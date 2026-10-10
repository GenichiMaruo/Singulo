package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * ワームホール固定化装置の専用画面。深い宇宙の暗い盤に星がまたたき、真ん中でワームホールの渦が回る（動いている間は速く明るく）。
 * 左右の喉（水色・赤紫）は、固定化が進むほど周りの光点が灯る。下は燃料（エキゾチック物質）と口の筐体、右は電力。
 */
public class WormholeStabilizerScreen extends AbstractContainerScreen<WormholeStabilizerMenu> {
    private static final int BG = 0xFF090C17;
    private static final int BG2 = 0xFF0E1324;
    private static final int FRAME = 0xFF2B2350;
    private static final int FRAME_HI = 0xFF5A4BA0;
    private static final int TEXT = 0xFFD8E4FF;
    private static final int DIM = 0xFF7F8AB0;
    private static final int CYAN = 0xFF64DCFF;
    private static final int MAGENTA = 0xFFE070FF;
    private static final int SLOT_BG = 0xFF05070E;
    private static final int[] CHAMBER = {CYAN, MAGENTA};
    private static final int CX = 100;
    private static final int CY = 54;
    private static final int BAR_X = 184;
    private static final int BAR_Y = 20;
    private static final int BAR_H = 84;

    public WormholeStabilizerScreen(WormholeStabilizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WormholeStabilizerMenu.WIDTH;
        imageHeight = WormholeStabilizerMenu.HEIGHT;
        inventoryLabelX = WormholeStabilizerMenu.INV_X;
        inventoryLabelY = WormholeStabilizerMenu.INV_Y - 11;
        titleLabelX = 10;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (Panel.inside(mouseX, mouseY, leftPos + BAR_X - 1, topPos + BAR_Y - 1, 8, BAR_H + 2)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.energy",
                    String.format("%,d", menu.value(WormholeStabilizerMenu.D_ENERGY)),
                    String.format("%,d", menu.value(WormholeStabilizerMenu.D_CAPACITY))), mouseX, mouseY);
        }
    }

    private boolean working() {
        return menu.progress(0) > 0 || menu.progress(1) > 0;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;
        float t = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick;
        // 盤: 二重の縁と、上下で少し色の違う深い宇宙
        g.fill(x, y, x + w, y + h, FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FRAME_HI);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BG);
        g.fillGradient(x + 2, y + 2, x + w - 2, y + 112, BG2, BG);
        // 上の光の線（水色と赤紫がゆっくり入れ替わる）
        for (int i = 0; i < w - 12; i++) {
            float k = 0.5F + 0.5F * Mth.sin(i * 0.05F - t * 0.06F);
            g.fill(x + 6 + i, y + 3, x + 7 + i, y + 4, lerpColor(CYAN, MAGENTA, k));
        }
        stars(g, x, y, w, 112, t);
        // 機械の区画と、持ち物の区画を分ける線
        g.fill(x + 8, y + 112, x + w - 8, y + 113, FRAME_HI);
        wormhole(g, x + CX, y + CY, t);
        conduits(g, x, y, t);
        for (int i = 0; i < 2; i++) {
            chamber(g, x + WormholeStabilizerMenu.SLOT_POS[i][0], y + WormholeStabilizerMenu.SLOT_POS[i][1],
                    menu.progress(i), CHAMBER[i], t);
        }
        int[] fuel = WormholeStabilizerMenu.SLOT_POS[2];
        slot(g, x + fuel[0], y + fuel[1], 0xFF8A70D0);
        int[] casing = WormholeStabilizerMenu.SLOT_POS[3];
        slot(g, x + casing[0], y + casing[1], 0xFFB8C4E0);
        energyBar(g, x + BAR_X, y + BAR_Y, t);
        for (Slot s : menu.slots) {
            if (s.index >= io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity.SLOTS) {
                slot(g, x + s.x, y + s.y, 0xFF2E3658);
            }
        }
    }

    /** またたく星（位置は決まった乱数）。 */
    private static void stars(GuiGraphics g, int x, int y, int w, int h, float t) {
        long seed = 1469598103934665603L;
        for (int i = 0; i < 70; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sx = (int) Math.floorMod(seed >>> 20, (long) (w - 8)) + 4;
            int sy = (int) Math.floorMod(seed >>> 40, (long) (h - 10)) + 6;
            float tw = 0.5F + 0.5F * Mth.sin(t * 0.08F + i * 1.7F);
            int a = (int) (60 + 150 * tw);
            g.fill(x + sx, y + sy, x + sx + 1, y + sy + 1, (a << 24) | 0xCFE0FF);
        }
    }

    /** 真ん中のワームホール: 黒い喉と、回る渦の腕（動いている間は速く明るい）。 */
    private void wormhole(GuiGraphics g, int cx, int cy, float t) {
        boolean on = working();
        float speed = on ? 0.12F : 0.025F;
        int arms = 4;
        for (int a = 0; a < arms; a++) {
            for (int k = 0; k < 46; k++) {
                float r = 4 + k * 0.52F;
                float ang = a * Mth.TWO_PI / arms + k * 0.2F + t * speed;
                int px = cx + Math.round(Mth.cos(ang) * r);
                int py = cy + Math.round(Mth.sin(ang) * r * 0.82F);
                float fade = 1 - k / 46F;
                int col = lerpColor(MAGENTA, CYAN, (a % 2 == 0 ? fade : 1 - fade));
                int alpha = (int) ((on ? 230 : 140) * (0.25F + 0.75F * fade));
                int size = k < 12 ? 2 : 1;
                g.fill(px, py, px + size, py + size, (alpha << 24) | (col & 0xFFFFFF));
            }
        }
        // 縁の光の輪
        for (int i = 0; i < 48; i++) {
            float ang = i * Mth.TWO_PI / 48 - t * speed * 0.5F;
            int px = cx + Math.round(Mth.cos(ang) * 6);
            int py = cy + Math.round(Mth.sin(ang) * 6 * 0.82F);
            g.fill(px, py, px + 1, py + 1, on ? 0xFFFFFFFF : 0xFFB8C4FF);
        }
        // 黒い喉
        for (int dy = -4; dy <= 4; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, 22 - dy * dy * 1.4)));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0xFF000000);
        }
    }

    /** 左右の喉から真ん中へ流れる光（動いている喉だけ）。 */
    private void conduits(GuiGraphics g, int x, int y, float t) {
        for (int i = 0; i < 2; i++) {
            if (menu.progress(i) <= 0) {
                continue;
            }
            int sx = x + WormholeStabilizerMenu.SLOT_POS[i][0] + 8;
            int ex = x + CX + (i == 0 ? -10 : 10);
            int yy = y + CY;
            int from = Math.min(sx, ex);
            int to = Math.max(sx, ex);
            for (int px = from; px < to; px++) {
                float k = (px * 0.35F + (i == 0 ? -t : t) * 0.6F) % 4;
                if (k < 0) {
                    k += 4;
                }
                if (k < 2) {
                    g.fill(px, yy, px + 1, yy + 1, CHAMBER[i]);
                }
            }
        }
    }

    /** 喉: 光る縁のスロットと、固定化の進みに合わせて灯る16個の光点。 */
    private static void chamber(GuiGraphics g, int sx, int sy, float progress, int color, float t) {
        int cx = sx + 8;
        int cy = sy + 8;
        int lit = Math.round(progress * 16);
        for (int i = 0; i < 16; i++) {
            float ang = -Mth.HALF_PI + i * Mth.TWO_PI / 16;
            int px = cx + Math.round(Mth.cos(ang) * 15) - 1;
            int py = cy + Math.round(Mth.sin(ang) * 15) - 1;
            boolean on = i < lit;
            int c = on ? color : 0xFF262C48;
            if (on && i == lit - 1) {
                float pulse = 0.5F + 0.5F * Mth.sin(t * 0.5F);
                c = lerpColor(color, 0xFFFFFFFF, pulse);
            }
            g.fill(px, py, px + 2, py + 2, c);
        }
        slot(g, sx, sy, color);
    }

    private static void slot(GuiGraphics g, int x, int y, int edge) {
        g.fill(x - 1, y - 1, x + 17, y + 17, edge);
        g.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    /** 電力: 下から満ちる縦の光（赤紫→水色）。 */
    private void energyBar(GuiGraphics g, int x, int y, float t) {
        g.fill(x - 1, y - 1, x + 7, y + BAR_H + 1, FRAME_HI);
        g.fill(x, y, x + 6, y + BAR_H, SLOT_BG);
        int cap = Math.max(1, menu.value(WormholeStabilizerMenu.D_CAPACITY));
        int filled = Math.round(BAR_H * Math.min(1F, menu.value(WormholeStabilizerMenu.D_ENERGY) / (float) cap));
        if (filled > 0) {
            g.fillGradient(x, y + BAR_H - filled, x + 6, y + BAR_H, CYAN, MAGENTA);
            int glint = (int) ((t * 1.5F) % Math.max(1, filled));
            g.fill(x, y + BAR_H - glint - 1, x + 6, y + BAR_H - glint, 0x90FFFFFF);
        }
    }

    private static int lerpColor(int a, int b, float k) {
        k = Mth.clamp(k, 0, 1);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * k) << 16) | ((int) (ag + (bg - ag) * k) << 8) | (int) (ab + (bb - ab) * k);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY + 2, TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
        for (int i = 0; i < 2; i++) {
            int[] p = WormholeStabilizerMenu.SLOT_POS[i];
            float pr = menu.progress(i);
            boolean hasMouth = menu.slots.get(i).hasItem();
            Component s;
            if (pr > 0) {
                s = Component.translatable("gui.singulo.stabilizer.working", Math.round(pr * 100));
            } else if (hasMouth && menu.slots.get(i).getItem().getItem() instanceof io.github.genichimaruo.singulo.wormhole.UnstableMouthItem) {
                s = Component.translatable("gui.singulo.stabilizer.waiting");
            } else if (hasMouth) {
                s = Component.translatable("gui.singulo.stabilizer.done");
            } else {
                s = Component.translatable("gui.singulo.stabilizer.empty");
            }
            int cx = p[0] + 8;
            g.drawString(font, s, cx - font.width(s) / 2, p[1] + 24, pr > 0 ? CHAMBER[i] : DIM, false);
        }
        int[] f = WormholeStabilizerMenu.SLOT_POS[2];
        Component fuel = Component.translatable("gui.singulo.stabilizer.fuel");
        g.drawString(font, fuel, f[0] - 6 - font.width(fuel), f[1] + 4, DIM, false);
        int[] c = WormholeStabilizerMenu.SLOT_POS[3];
        g.drawString(font, Component.translatable("gui.singulo.stabilizer.casing"), c[0] + 22, c[1] + 4, DIM, false);
    }
}
