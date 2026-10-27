package com.example.mazesolver;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

public class MazeGui extends GuiScreen {
    private static final int PANEL = 176;
    private static final int C_PATH = 0xFF00E5FF, C_WALKED = 0xFF00C853, C_GOAL = 0xFFFF1744,
            C_START = 0xFFFFD600, C_PLAYER = 0xFFFFFFFF;

    private final SolverController sc = SolverController.I;
    private BlockPos lookedAt;
    private boolean settingsView;
    private KeyBinding listening;          // key currently being rebound
    private int left, top;

    private GuiButton toggleBtn, previewBtn, goalBtn, autoBtn;           // main view
    private GuiButton kGuiBtn, kToggleBtn, kGoalBtn, wallsBtn;           // settings view

    @Override
    public void initGui() {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (lookedAt == null && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
            lookedAt = hit.getBlockPos();

        left = width / 2 - 150;
        top = height / 2 - 118;
        buttonList.clear();
        toggleBtn = previewBtn = goalBtn = autoBtn = kGuiBtn = kToggleBtn = kGoalBtn = wallsBtn = null;

        if (!settingsView) {
            buttonList.add(toggleBtn  = new GuiButton(0, left, top + 30,  116, 20, ""));
            buttonList.add(previewBtn = new GuiButton(1, left, top + 54,  116, 20, "Preview Route"));
            buttonList.add(goalBtn    = new GuiButton(2, left, top + 78,  116, 20, "Set Goal: Looked-At"));
            buttonList.add(autoBtn    = new GuiButton(3, left, top + 102, 116, 20, "Auto-Find Exit"));
            buttonList.add(new GuiButton(5, left, top + 126, 116, 20, "Settings / Keybinds"));
            buttonList.add(new GuiButton(4, left, top + 150, 116, 20, "Close"));
        } else {
            buttonList.add(kGuiBtn    = new GuiButton(10, left, top + 30,  116, 20, ""));
            buttonList.add(kToggleBtn = new GuiButton(11, left, top + 54,  116, 20, ""));
            buttonList.add(kGoalBtn   = new GuiButton(12, left, top + 78,  116, 20, ""));
            buttonList.add(wallsBtn   = new GuiButton(13, left, top + 102, 116, 20, ""));
            buttonList.add(new GuiButton(14, left, top + 126, 116, 20, "Reset Keybinds"));
            buttonList.add(new GuiButton(15, left, top + 150, 116, 20, "Back"));
        }
        refreshButtons();
    }

    private String keyLabel(String name, KeyBinding kb) {
        String key = listening == kb ? EnumChatFormatting.YELLOW + "> press key <"
                                     : GameSettings.getKeyDisplayString(kb.getKeyCode());
        return name + ": " + key;
    }

    private void refreshButtons() {
        if (toggleBtn != null) {
            toggleBtn.displayString = "Trail: " + (sc.isRunning()
                    ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF");
            previewBtn.enabled = !sc.isRunning();
            goalBtn.enabled = lookedAt != null && !sc.isRunning();
            autoBtn.enabled = !sc.isRunning();
        }
        if (kGuiBtn != null) {
            kGuiBtn.displayString    = keyLabel("Menu", MazeSolverMod.guiKey);
            kToggleBtn.displayString = keyLabel("Trail", MazeSolverMod.toggleKey);
            kGoalBtn.displayString   = keyLabel("Goal", MazeSolverMod.goalKey);
            wallsBtn.displayString   = "Through walls: " + (sc.throughWalls
                    ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF");
        }
    }

    private void applyKeys() {
        KeyBinding.resetKeyBindingArrayAndHash();
        mc.gameSettings.saveOptions();
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        switch (b.id) {
            case 0:  sc.toggle(); break;
            case 1:  sc.preview(); break;
            case 2:  if (lookedAt != null) sc.setGoal(lookedAt); break;
            case 3:  sc.autoFindGoal(); break;
            case 4:  mc.displayGuiScreen(null); return;
            case 5:  settingsView = true; listening = null; initGui(); return;
            case 10: listening = MazeSolverMod.guiKey; break;
            case 11: listening = MazeSolverMod.toggleKey; break;
            case 12: listening = MazeSolverMod.goalKey; break;
            case 13: sc.throughWalls = !sc.throughWalls; break;
            case 14:
                MazeSolverMod.guiKey.setKeyCode(MazeSolverMod.guiKey.getKeyCodeDefault());
                MazeSolverMod.toggleKey.setKeyCode(MazeSolverMod.toggleKey.getKeyCodeDefault());
                MazeSolverMod.goalKey.setKeyCode(MazeSolverMod.goalKey.getKeyCodeDefault());
                applyKeys();
                listening = null;
                break;
            case 15: settingsView = false; listening = null; initGui(); return;
        }
        refreshButtons();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (listening != null) {
            if (keyCode != Keyboard.KEY_ESCAPE) {   // Esc just cancels
                listening.setKeyCode(keyCode);
                applyKeys();
            }
            listening = null;
            refreshButtons();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() { refreshButtons(); }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();

        int px = left + 124, py = top + 22;
        drawRect(left - 10, top - 10, px + PANEL + 10, top + 232, 0xCC0B0B12);
        drawRect(left - 10, top - 10, px + PANEL + 10, top - 9, 0xFF00E5FF);

        drawString(fontRendererObj, EnumChatFormatting.BOLD + (settingsView ? "Maze Solver - Settings" : "Maze Solver"), left, top + 2, 0xFFFFFF);
        drawString(fontRendererObj, "Esc to close", left, top + 14, 0x888888);

        super.drawScreen(mx, my, pt);

        int ty = top + 178;
        if (!settingsView) {
            drawString(fontRendererObj, "Status: " + stateColor() + sc.status, left, ty, 0xFFFFFF);
            String g = sc.goal == null ? "none" : sc.goal.getX() + ", " + sc.goal.getY() + ", " + sc.goal.getZ();
            drawString(fontRendererObj, "Goal: \u00a77" + g, left, ty + 11, 0xFFFFFF);
            int total = sc.path.size();
            drawString(fontRendererObj, "Steps: \u00a77" + Math.min(sc.index, total) + " / " + total
                    + (sc.partial ? " (partial)" : ""), left, ty + 22, 0xFFFFFF);
            if (total > 0) {
                int bw = 116, filled = bw * Math.min(sc.index, total) / total;
                drawRect(left, ty + 35, left + bw, ty + 40, 0xFF222222);
                drawRect(left, ty + 35, left + filled, ty + 40, C_WALKED);
            }
        } else {
            drawString(fontRendererObj, "Click a key button, then", left, ty, 0xAAAAAA);
            drawString(fontRendererObj, "press the new key.", left, ty + 11, 0xAAAAAA);
            drawString(fontRendererObj, "Esc cancels. Saved to options.", left, ty + 22, 0x777777);
        }

        drawPreview(px, py);

        int ly = py + PANEL + 5;
        legend(px, ly, C_PATH, "Route");
        legend(px + 46, ly, C_WALKED, "Done");
        legend(px + 88, ly, C_GOAL, "Goal");
        legend(px + 128, ly, C_PLAYER, "You");
    }

    private String stateColor() {
        switch (sc.state) {
            case RUNNING: return EnumChatFormatting.GREEN.toString();
            case DONE:    return EnumChatFormatting.AQUA.toString();
            case FAILED:  return EnumChatFormatting.RED.toString();
            default:      return EnumChatFormatting.GRAY.toString();
        }
    }

    private void legend(int x, int y, int color, String label) {
        drawRect(x, y + 1, x + 6, y + 7, color);
        fontRendererObj.drawString(label, x + 9, y, 0xAAAAAA);
    }

    // ---------- top-down preview ----------

    private void drawPreview(int px, int py) {
        drawRect(px - 2, py - 2, px + PANEL + 2, py + PANEL + 2, 0xFF000000);
        drawRect(px, py, px + PANEL, py + PANEL, SolverController.C_UNLOADED);

        if (sc.gw == 0 || sc.gh == 0) {
            drawCenteredString(fontRendererObj, "No preview yet", px + PANEL / 2, py + PANEL / 2 - 4, 0x777777);
            return;
        }

        int cell = Math.max(1, Math.min(10, Math.min(PANEL / sc.gw, PANEL / sc.gh)));
        int ox = px + (PANEL - sc.gw * cell) / 2;
        int oy = py + (PANEL - sc.gh * cell) / 2;

        for (int z = 0; z < sc.gh; z++) {
            int x = 0;
            while (x < sc.gw) {
                int c = sc.grid[z * sc.gw + x], x2 = x + 1;
                while (x2 < sc.gw && sc.grid[z * sc.gw + x2] == c) x2++;
                drawRect(ox + x * cell, oy + z * cell, ox + x2 * cell, oy + (z + 1) * cell, c);
                x = x2;
            }
        }

        List<BlockPos> path = sc.path;
        for (int i = 0; i < path.size(); i++)
            cell(ox, oy, cell, path.get(i), i < sc.index ? C_WALKED : C_PATH, 0);
        if (sc.startPos != null) cell(ox, oy, cell, sc.startPos, C_START, 0);
        if (sc.goal != null) cell(ox, oy, cell, sc.goal, C_GOAL, 1);

        if (mc.thePlayer != null && (System.currentTimeMillis() / 350) % 2 == 0) {
            int cx = MathHelper.floor_double(mc.thePlayer.posX), cz = MathHelper.floor_double(mc.thePlayer.posZ);
            cell(ox, oy, cell, new BlockPos(cx, 0, cz), C_PLAYER, 1);
        }

        fontRendererObj.drawString("N", px + PANEL - 8, py + 2, 0xFFFFFF);
    }

    private void cell(int ox, int oy, int cell, BlockPos p, int color, int grow) {
        int cx = p.getX() - sc.gx, cz = p.getZ() - sc.gz;
        if (cx < 0 || cz < 0 || cx >= sc.gw || cz >= sc.gh) return;
        int size = Math.max(cell, 2);
        int x = ox + cx * cell - (size - cell) / 2 - grow, y = oy + cz * cell - (size - cell) / 2 - grow;
        drawRect(x, y, x + size + grow * 2, y + size + grow * 2, color);
    }
}
