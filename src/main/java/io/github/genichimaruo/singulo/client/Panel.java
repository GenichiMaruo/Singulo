package io.github.genichimaruo.singulo.client;

import net.minecraft.client.gui.GuiGraphics;

/** 白い外装の配色でGUIの部品を描く（テクスチャ画像を使わない）。 */
final class Panel {
    static final int BACKGROUND = 0xFFE9EDF0;
    static final int SEAM = 0xFFC4CAD0;
    static final int SHADE = 0xFFD6DBE0;
    static final int WELL = 0xFF39424A;
    static final int WELL_EDGE = 0xFF8B95A0;
    static final int TEXT = 0xFF3C4650;
    static final int GLOW = 0xFF78D2F0;
    static final int AMBER = 0xFFF0B450;
    static final int RED = 0xFFE86060;

    private Panel() {}

    static void background(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SEAM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BACKGROUND);
        g.fill(x + 3, y + h / 2, x + w - 3, y + h / 2 + 1, SHADE);
        // 発光ライン
        g.fill(x + 4, y + 2, x + w - 4, y + 3, GLOW);
    }

    /** 空のスロットに、入れる物のグレーの影を描く（textures/gui/ghost/<id>.png。データ生成で作る）。 */
    static void ghost(GuiGraphics g, int x, int y, String id) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(io.github.genichimaruo.singulo.Singulo.id("textures/gui/ghost/" + id + ".png"), x, y, 0, 0, 16, 16, 16, 16);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, WELL_EDGE);
        g.fill(x, y, x + 16, y + 16, WELL);
    }

    static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, WELL_EDGE);
        g.fill(x, y, x + w, y + h, WELL);
    }

    /** 下から fraction の割合だけ塗る縦バー。 */
    static void verticalBar(GuiGraphics g, int x, int y, int w, int h, double fraction, int color) {
        well(g, x, y, w, h);
        int filled = (int) Math.round(h * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x, y + h - filled, x + w, y + h, color);
        }
    }

    static void arrow(GuiGraphics g, int x, int y, double fraction) {
        int w = 22;
        g.fill(x, y + 6, x + w - 6, y + 10, SEAM);
        for (int i = 0; i < 8; i++) {
            g.fill(x + w - 8 + i, y + i, x + w - 7 + i, y + 16 - i, SEAM);
        }
        int filled = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x, y + 6, x + Math.min(filled, w - 6), y + 10, GLOW);
            for (int i = 0; i < 8 && w - 8 + i < filled; i++) {
                g.fill(x + w - 8 + i, y + i, x + w - 7 + i, y + 16 - i, GLOW);
            }
        }
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
