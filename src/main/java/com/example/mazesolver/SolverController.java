package com.example.mazesolver;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Solver state + logic. In "trail" mode the mod never moves the player: it plans a route and
 * the world renderer draws it; this class just tracks how far along it the player is.
 */
public final class SolverController {
    public static final SolverController I = new SolverController();

    public enum State { IDLE, RUNNING, DONE, FAILED }

    public static final int C_UNLOADED = 0xFF111111, C_WALL = 0xFF3B3B3B, C_FLOOR = 0xFF8A8A8A, C_PIT = 0xFF5A2A2A;

    private static final int SCAN_RADIUS = 128, MAX_GRID = 170;

    private final Minecraft mc = Minecraft.getMinecraft();

    public BlockPos goal, startPos;
    public List<BlockPos> path = new ArrayList<BlockPos>();
    public int index;
    public boolean partial;           // route only leads toward the goal (far / unloaded chunks)
    public boolean throughWalls = true;
    public State state = State.IDLE;
    public String status = "Idle";

    public int[] grid = new int[0];
    public int gx, gz, gw, gh;

    private int tick, lastReplan;
    private boolean lastFound;

    private SolverController() {}

    public boolean isRunning() { return state == State.RUNNING; }

    // ---------- actions ----------

    public void setGoal(BlockPos pos) {
        goal = pos;
        path = new ArrayList<BlockPos>();
        index = 0;
        partial = false;
        state = State.IDLE;
        status = "Goal set";
        buildPreview();
        chat("Goal set to " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    public boolean autoFindGoal() {
        BlockPos found = findExitBlock();
        if (found == null) { status = "No exit block found"; chat(status); return false; }
        setGoal(found);
        return true;
    }

    /** Plan the route and show it in the preview map only (no in-world trail). */
    public void preview() {
        if (isRunning()) return;
        if (goal == null && !autoFindGoal()) { state = State.FAILED; return; }
        boolean ok = plan();
        state = State.IDLE;
        if (!ok) { state = State.FAILED; status = "No path found"; }
        else status = partial ? "Partial route: " + path.size() + " steps" : "Route found: " + path.size() + " steps";
    }

    public void start() {
        if (goal == null && !autoFindGoal()) {
            state = State.FAILED;
            chat("No goal. Look at the exit and press G, or place an emerald/gold/diamond block.");
            return;
        }
        plan();
        if (path.isEmpty()) {
            if (lastFound) { state = State.DONE; status = "You're already at the exit!"; }
            else { state = State.FAILED; status = "No path found"; }
            chat(status);
            return;
        }
        state = State.RUNNING;
        status = partial ? "Partial trail - keep going" : "Follow the trail!";
        chat(partial ? "Partial trail (" + path.size() + " steps) - the goal is far or not loaded yet; it extends as you move."
                     : "Trail on: " + path.size() + " steps. Follow the cyan line.");
    }

    public void stop(State newState, String message) {
        state = newState;
        status = message;
        chat(message);
    }

    public void toggle() {
        if (isRunning()) stop(State.IDLE, "Trail off.");
        else start();
    }

    // ---------- planning ----------

    private BlockPos playerBlock() {
        EntityPlayerSP p = mc.thePlayer;
        return new BlockPos(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY + 0.01), MathHelper.floor_double(p.posZ));
    }

    private boolean plan() {
        startPos = playerBlock();
        PathFinder.Result r = PathFinder.find(mc.theWorld, startPos, goal);
        path = r.path;
        partial = r.partial;
        lastFound = r.found;
        index = 0;
        lastReplan = tick;
        buildPreview();
        return r.found || !r.path.isEmpty();
    }

    private void replan() {
        plan();
        if (path.isEmpty()) {
            if (lastFound) stop(State.DONE, "Reached the exit!");
            else stop(State.FAILED, "Lost the route - no path from here.");
            return;
        }
        status = partial ? "Partial trail - keep going" : "Follow the trail!";
    }

    private boolean isExitBlock(Block b) {
        return b == Blocks.emerald_block || b == Blocks.gold_block || b == Blocks.diamond_block;
    }

    private BlockPos findExitBlock() {
        if (mc.thePlayer == null) return null;
        BlockPos o = playerBlock(), best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++)
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                if (!mc.theWorld.isBlockLoaded(new BlockPos(o.getX() + dx, o.getY(), o.getZ() + dz))) continue;
                for (int dy = -16; dy <= 16; dy++) {
                    BlockPos p = new BlockPos(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    if (isExitBlock(mc.theWorld.getBlockState(p).getBlock())) {
                        double d = p.distanceSq(o);
                        if (d < bestD) { bestD = d; best = p; }
                    }
                }
            }
        return best;
    }

    /** Snapshot walls/floors around the route into a colour grid for the GUI map. */
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

    // ---------- per-tick progress tracking (no movement!) ----------

    public void tick() {
        if (state != State.RUNNING) return;
        EntityPlayerSP p = mc.thePlayer;
        tick++;

        // advance the trail when the player walks along it (look a few nodes ahead for shortcuts)
        int limit = Math.min(path.size(), index + 10);
        for (int i = limit - 1; i >= index; i--) {
            BlockPos b = path.get(i);
            double dx = b.getX() + 0.5 - p.posX, dz = b.getZ() + 0.5 - p.posZ, dy = b.getY() - p.posY;
            if (dx * dx + dz * dz < 1.25 * 1.25 && Math.abs(dy) < 2.0) { index = i + 1; break; }
        }

        if (index >= path.size()) {
            if (!partial) { stop(State.DONE, "You reached the exit!"); return; }
            if (tick - lastReplan >= 40) replan();   // end of a partial trail: extend it
            return;
        }

        if (tick - lastReplan >= 40) {
            BlockPos n = path.get(index);
            double dx = n.getX() + 0.5 - p.posX, dz = n.getZ() + 0.5 - p.posZ, dy = n.getY() - p.posY;
            boolean offTrail = dx * dx + dz * dz > 36 || Math.abs(dy) > 4;
            if (offTrail || (partial && tick - lastReplan >= 100)) replan();
        }
    }

    private void chat(String s) {
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("\u00a7b[MazeSolver]\u00a7r " + s));
    }
}
