package com.example.mazesolver;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

import java.io.IOException;
import java.util.List;

public class MazeGui extends GuiScreen {
    private static final int PANEL = 176;
    private static final int C_PATH = 0xFF00E5FF, C_WALKED = 0xFF00C853, C_GOAL = 0xFFFF1744,
            C_START = 0xFFFFD600, C_PLAYER = 0xFFFFFFFF;

    private final SolverController sc = SolverController.I;
    private BlockPos lookedAt;
    private GuiButton toggleBtn, previewBtn, goalBtn, autoBtn, closeBtn;
    private int left, top;

    @Override
    public void initGui() {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (lookedAt == null && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
            lookedAt = hit.getBlockPos();

        left = width / 2 - 150;
        top = height / 2 - 105;
        buttonList.clear();
        toggleBtn  = new GuiButton(0, left, top + 30,  110, 20, "");
        previewBtn = new GuiButton(1, left, top + 54,  110, 20, "Preview Route");
        goalBtn    = new GuiButton(2, left, top + 78,  110, 20, "Set Goal: Looked-At");
        autoBtn    = new GuiButton(3, left, top + 102, 110, 20, "Auto-Find Exit");
        closeBtn   = new GuiButton(4, left, top + 126, 110, 20, "Close");
        buttonList.add(toggleBtn); buttonList.add(previewBtn); buttonList.add(goalBtn);
        buttonList.add(autoBtn);   buttonList.add(closeBtn);
        refreshButtons();
    }

    private void refreshButtons() {
        toggleBtn.displayString = "Solver: " + (sc.isRunning()
                ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF");
        previewBtn.enabled = !sc.isRunning();
        goalBtn.enabled = lookedAt != null && !sc.isRunning();
        autoBtn.enabled = !sc.isRunning();
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        switch (b.id) {
            case 0: sc.toggle(); break;
            case 1: sc.preview(); break;
            case 2: if (lookedAt != null) sc.setGoal(lookedAt); break;
            case 3: sc.autoFindGoal(); break;
            case 4: mc.displayGuiScreen(null); break;
        }
        refreshButtons();
    }

    @Override
    public void updateScreen() { refreshButtons(); }

    @Override
    public boolean doesGuiPauseGame() { return false; } // keep walking while the menu is open

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();

        int px = left + 122, py = top + 22;
        // window frame
        drawRect(left - 10, top - 10, px + PANEL + 10, top + 222, 0xCC0B0B12);
        drawRect(left - 10, top - 10, px + PANEL + 10, top - 9, 0xFF00E5FF);

        drawString(fontRendererObj, EnumChatFormatting.BOLD + "Maze Solver", left, top + 2, 0xFFFFFF);
        drawString(fontRendererObj, "RShift to open, Esc to close", left, top + 14, 0x888888);

        super.drawScreen(mx, my, pt);

        // info text under buttons
        int ty = top + 154;
        drawString(fontRendererObj, "Status: " + stateColor() + sc.status, left, ty, 0xFFFFFF);
        String g = sc.goal == null ? "none" : sc.goal.getX() + ", " + sc.goal.getY() + ", " + sc.goal.getZ();
        drawString(fontRendererObj, "Goal: \u00a77" + g, left, ty + 11, 0xFFFFFF);
        int total = sc.path.size();
        drawString(fontRendererObj, "Steps: \u00a77" + Math.min(sc.index, total) + " / " + total, left, ty + 22, 0xFFFFFF);
        if (total > 0) {
            int bw = 110, filled = bw * Math.min(sc.index, total) / total;
            drawRect(left, ty + 35, left + bw, ty + 40, 0xFF222222);
            drawRect(left, ty + 35, left + filled, ty + 40, C_WALKED);
        }

        drawPreview(px, py);

        // legend
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

        // base map, merged into horizontal runs of equal colour
        for (int z = 0; z < sc.gh; z++) {
            int x = 0;
            while (x < sc.gw) {
                int c = sc.grid[z * sc.gw + x], x2 = x + 1;
                while (x2 < sc.gw && sc.grid[z * sc.gw + x2] == c) x2++;
                drawRect(ox + x * cell, oy + z * cell, ox + x2 * cell, oy + (z + 1) * cell, c);
                x = x2;
            }
        }

        // route: walked part green, remaining cyan
        List<BlockPos> path = sc.path;
        for (int i = 0; i < path.size(); i++)
            cell(ox, oy, cell, path.get(i), i < sc.index ? C_WALKED : C_PATH, 0);
        if (sc.startPos != null) cell(ox, oy, cell, sc.startPos, C_START, 0);
        if (sc.goal != null) cell(ox, oy, cell, sc.goal, C_GOAL, 1);

        // player marker (blinks)
        if (mc.thePlayer != null && (System.currentTimeMillis() / 350) % 2 == 0) {
            int cx = MathHelper.floor_double(mc.thePlayer.posX), cz = MathHelper.floor_double(mc.thePlayer.posZ);
            cell(ox, oy, cell, new BlockPos(cx, 0, cz), C_PLAYER, 1);
        }

        fontRendererObj.drawString("N", px + PANEL - 8, py + 2, 0xFFFFFF);
    }

    /** Draws one world column as a cell; grow makes the marker a little bigger so it stays visible. */
    private void cell(int ox, int oy, int cell, BlockPos p, int color, int grow) {
        int cx = p.getX() - sc.gx, cz = p.getZ() - sc.gz;
        if (cx < 0 || cz < 0 || cx >= sc.gw || cz >= sc.gh) return;
        int size = Math.max(cell, 2);
        int x = ox + cx * cell - (size - cell) / 2 - grow, y = oy + cz * cell - (size - cell) / 2 - grow;
        drawRect(x, y, x + size + grow * 2, y + size + grow * 2, color);
    }
}
