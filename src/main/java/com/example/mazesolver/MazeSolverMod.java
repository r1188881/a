package com.example.mazesolver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@Mod(modid = "mazesolver", name = "Maze Solver", version = "1.2.0", clientSideOnly = true)
public class MazeSolverMod {
    public static KeyBinding guiKey, toggleKey, goalKey;
    private final Minecraft mc = Minecraft.getMinecraft();

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        guiKey    = new KeyBinding("Open Maze Solver menu", Keyboard.KEY_RSHIFT, "Maze Solver");
        toggleKey = new KeyBinding("Trail on / off", Keyboard.KEY_M, "Maze Solver");
        goalKey   = new KeyBinding("Set goal (looked-at block)", Keyboard.KEY_G, "Maze Solver");
        ClientRegistry.registerKeyBinding(guiKey);
        ClientRegistry.registerKeyBinding(toggleKey);
        ClientRegistry.registerKeyBinding(goalKey);
        // 1.8.9: tick events live on the FML bus, render/overlay events on the Forge bus
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        SolverController sc = SolverController.I;
        if (mc.thePlayer == null || mc.theWorld == null) {
            if (sc.isRunning()) sc.state = SolverController.State.IDLE;
            return;
        }

        boolean screenOpen = mc.currentScreen != null;
        boolean gui = false, toggle = false, goal = false;
        while (guiKey.isPressed()) gui = true;       // drain so presses never pile up
        while (toggleKey.isPressed()) toggle = true;
        while (goalKey.isPressed()) goal = true;

        if (!screenOpen) {
            if (gui) mc.displayGuiScreen(new MazeGui());
            if (toggle) sc.toggle();
            if (goal) {
                MovingObjectPosition hit = mc.objectMouseOver;
                if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
                    sc.setGoal(hit.getBlockPos());
            }
        }
        sc.tick();
    }

    /** Small HUD readout while the trail is on. */
    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Post ev) {
        if (ev.type != RenderGameOverlayEvent.ElementType.TEXT) return;
        SolverController sc = SolverController.I;
        if (!sc.isRunning() || mc.currentScreen != null) return;
        int left = Math.max(0, sc.path.size() - sc.index);
        String s = "\u00a7bMaze Solver\u00a7r  " + left + " blocks left" + (sc.partial ? " \u00a77(partial)" : "");
        mc.fontRendererObj.drawStringWithShadow(s, 6, 6, 0xFFFFFF);
    }

    /** Draws the trail (and a goal beacon) in the 3D world. */
    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent ev) {
        SolverController sc = SolverController.I;
        if (!sc.isRunning() || sc.path.isEmpty() || mc.thePlayer == null) return;

        RenderManager rm = mc.getRenderManager();
        double vx = rm.viewerPosX, vy = rm.viewerPosY, vz = rm.viewerPosZ;
        EntityPlayerSP p = mc.thePlayer;
        double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * ev.partialTicks;
        double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * ev.partialTicks;
        double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * ev.partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(3f);

        if (sc.throughWalls) {                 // faint pass that shows through walls
            GlStateManager.disableDepth();
            drawTrail(sc, px, py, pz, vx, vy, vz, 90);
            drawBeacon(sc, vx, vy, vz);
            GlStateManager.enableDepth();
        }
        drawTrail(sc, px, py, pz, vx, vy, vz, 255);   // solid pass, hidden behind walls

        GL11.glLineWidth(1f);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private void drawTrail(SolverController sc, double px, double py, double pz,
                           double vx, double vy, double vz, int alpha) {
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(px - vx, py + 0.05 - vy, pz - vz).color(0, 229, 255, alpha).endVertex();
        int end = Math.min(sc.path.size(), sc.index + 400);
        for (int i = sc.index; i < end; i++) {
            BlockPos b = sc.path.get(i);
            wr.pos(b.getX() + 0.5 - vx, b.getY() + 0.05 - vy, b.getZ() + 0.5 - vz).color(0, 229, 255, alpha).endVertex();
        }
        t.draw();
    }

    private void drawBeacon(SolverController sc, double vx, double vy, double vz) {
        if (sc.goal == null) return;
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        double x = sc.goal.getX() + 0.5 - vx, z = sc.goal.getZ() + 0.5 - vz;
        wr.pos(x, sc.goal.getY() + 1 - vy, z).color(255, 23, 68, 200).endVertex();
        wr.pos(x, sc.goal.getY() + 40 - vy, z).color(255, 23, 68, 0).endVertex();
        t.draw();
    }
}
