package com.example.mazesolver;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** All solver state + logic. The GUI and HUD only read from / call into this. */
public final class SolverController {
    public static final SolverController I = new SolverController();

    public enum State { IDLE, RUNNING, DONE, FAILED }

    // preview colours (ARGB)
    public static final int C_UNLOADED = 0xFF111111, C_WALL = 0xFF3B3B3B, C_FLOOR = 0xFF8A8A8A, C_PIT = 0xFF5A2A2A;

    private static final int SCAN_RADIUS = 48, MAX_REPATHS = 5, MAX_GRID = 160;

    private final Minecraft mc = Minecraft.getMinecraft();

    public BlockPos goal, startPos;
    public List<BlockPos> path = new ArrayList<BlockPos>();
    public int index;
    public State state = State.IDLE;
    public String status = "Idle";

    // top-down preview snapshot: gw x gh cells starting at world (gx, gz)
    public int[] grid = new int[0];
    public int gx, gz, gw, gh;

    private int stuckTicks, repaths, tick;
    private double lastX, lastY, lastZ;

    private SolverController() {}

    public boolean isRunning() { return state == State.RUNNING; }

    // ---------- public actions ----------

    public void setGoal(BlockPos pos) {
        goal = pos;
        path = new ArrayList<BlockPos>();
        index = 0;
        state = State.IDLE;
        status = "Goal set";
        buildPreview();
        chat("Goal set to " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    public boolean autoFindGoal() {
        BlockPos found = findExitBlock();
        if (found == null) { status = "No exit block found"; return false; }
        setGoal(found);
        return true;
    }

    /** Plan the route and show it in the preview, without walking. */
    public void preview() {
        if (isRunning()) return;
        if (goal == null && !autoFindGoal()) { state = State.FAILED; return; }
        if (plan()) { state = State.IDLE; status = "Route found: " + path.size() + " steps"; }
        else { state = State.FAILED; status = "No path found"; }
    }

    public void start() {
        if (goal == null && !autoFindGoal()) {
            state = State.FAILED;
            chat("No goal. Look at the exit and press G, or place an emerald/gold/diamond block.");
            return;
        }
        repaths = 0;
        if (!plan()) { state = State.FAILED; status = "No path found"; chat(status); return; }
        state = State.RUNNING;
        status = "Solving...";
        chat("Solving... " + path.size() + " steps.");
    }

    public void stop(State newState, String message) {
        state = newState;
        status = message;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        chat(message);
    }

    public void toggle() {
        if (isRunning()) stop(State.IDLE, "Stopped.");
        else start();
    }

    // ---------- planning ----------

    private BlockPos playerBlock() {
        EntityPlayerSP p = mc.thePlayer;
        return new BlockPos(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY + 0.01), MathHelper.floor_double(p.posZ));
    }

    private boolean plan() {
        startPos = playerBlock();
        path = PathFinder.find(mc.theWorld, startPos, goal);
        index = 0;
        stuckTicks = 0;
        buildPreview();
        return !path.isEmpty();
    }

    private boolean isExitBlock(Block b) {
        return b == Blocks.emerald_block || b == Blocks.gold_block || b == Blocks.diamond_block;
    }

    private BlockPos findExitBlock() {
        if (mc.thePlayer == null) return null;
        BlockPos o = playerBlock(), best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++)
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++)
                for (int dy = -16; dy <= 16; dy++) {
                    BlockPos p = new BlockPos(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    if (!mc.theWorld.isBlockLoaded(p)) continue;
                    if (isExitBlock(mc.theWorld.getBlockState(p).getBlock())) {
                        double d = p.distanceSq(o);
                        if (d < bestD) { bestD = d; best = p; }
                    }
                }
        return best;
    }

    /** Snapshot walls/floors around the route into a small colour grid for the GUI. */
    private void buildPreview() {
        World w = mc.theWorld;
        BlockPos s = startPos != null ? startPos : playerBlock();
        int minX = s.getX(), maxX = minX, minZ = s.getZ(), maxZ = minZ;
        for (BlockPos p : path) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        int pad = path.isEmpty() ? 12 : 3;
        minX -= pad; maxX += pad; minZ -= pad; maxZ += pad;
        if (maxX - minX + 1 > MAX_GRID) { minX = s.getX() - MAX_GRID / 2; maxX = minX + MAX_GRID - 1; }
        if (maxZ - minZ + 1 > MAX_GRID) { minZ = s.getZ() - MAX_GRID / 2; maxZ = minZ + MAX_GRID - 1; }

        gx = minX; gz = minZ; gw = maxX - minX + 1; gh = maxZ - minZ + 1;
        grid = new int[gw * gh];
        int refY = s.getY();
        for (int z = 0; z < gh; z++)
            for (int x = 0; x < gw; x++) {
                BlockPos a = new BlockPos(gx + x, refY, gz + z);
                int c;
                if (!w.isBlockLoaded(a)) c = C_UNLOADED;
                else if (!PathFinder.free(w, a) || !PathFinder.free(w, a.up())) c = C_WALL;
                else if (!PathFinder.solidFloor(w, a)) c = C_PIT;
                else c = C_FLOOR;
                grid[z * gw + x] = c;
            }
    }

    // ---------- per-tick walking ----------

    public void tick() {
        if (state != State.RUNNING) return;
        EntityPlayerSP p = mc.thePlayer;
        tick++;

        if (index >= path.size()) { stop(State.DONE, "Reached the exit!"); return; }

        BlockPos target = path.get(index);
        double dx = target.getX() + 0.5 - p.posX;
        double dz = target.getZ() + 0.5 - p.posZ;
        double dy = target.getY() - p.posY;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        if (horiz < 0.25 && Math.abs(dy) < 1.2) { index++; stuckTicks = 0; return; }

        float wanted = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float diff = MathHelper.wrapAngleTo180_float(wanted - p.rotationYaw);
        p.rotationYaw += MathHelper.clamp_float(diff, -35f, 35f);

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), Math.abs(diff) < 50f);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), dy > 0.5 && p.onGround);

        double moved = sq(p.posX - lastX) + sq(p.posY - lastY) + sq(p.posZ - lastZ);
        if (moved < 0.0004) stuckTicks++; else stuckTicks = 0;
        lastX = p.posX; lastY = p.posY; lastZ = p.posZ;
        if (stuckTicks > 30) {
            if (++repaths > MAX_REPATHS || !plan()) stop(State.FAILED, "Got stuck - stopping.");
            return;
        }

        if (tick % 10 == 0 && mc.currentScreen == null) {
            for (int i = index; i < path.size(); i++) {
                BlockPos b = path.get(i);
                mc.theWorld.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, b.getX() + 0.5, b.getY() + 0.3, b.getZ() + 0.5, 0, 0, 0);
            }
        }
    }

    private static double sq(double v) { return v * v; }

    private void chat(String s) {
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("\u00a7b[MazeSolver]\u00a7r " + s));
    }
}
