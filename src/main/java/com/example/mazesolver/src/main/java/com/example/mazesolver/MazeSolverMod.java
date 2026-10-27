package com.example.mazesolver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = "mazesolver", name = "Maze Solver", version = "1.1.0", clientSideOnly = true)
public class MazeSolverMod {
    private final Minecraft mc = Minecraft.getMinecraft();
    private KeyBinding guiKey, toggleKey, goalKey;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        guiKey    = new KeyBinding("Open Maze Solver menu", Keyboard.KEY_RSHIFT, "Maze Solver");
        toggleKey = new KeyBinding("Start / Stop solver", Keyboard.KEY_M, "Maze Solver");
        goalKey   = new KeyBinding("Set goal (looked-at block)", Keyboard.KEY_G, "Maze Solver");
        ClientRegistry.registerKeyBinding(guiKey);
        ClientRegistry.registerKeyBinding(toggleKey);
        ClientRegistry.registerKeyBinding(goalKey);
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
        // always drain key presses so none pile up while a menu is open
        boolean gui = false, toggle = false, goal = false;
        while (guiKey.isPressed()) gui = true;
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

    /** Small HUD readout while solving. */
    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Post ev) {
        if (ev.type != RenderGameOverlayEvent.ElementType.TEXT) return;
        SolverController sc = SolverController.I;
        if (!sc.isRunning() || mc.currentScreen != null) return;
        String s = "\u00a7bMaze Solver\u00a7r  " + Math.min(sc.index, sc.path.size()) + "/" + sc.path.size();
        mc.fontRendererObj.drawStringWithShadow(s, 6, 6, 0xFFFFFF);
    }
}
