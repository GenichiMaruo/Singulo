package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * ワームホール固定化装置の専用画面（宇宙のスキン）。真ん中でワームホールの渦が回る（動いている間は速く明るく）。
 * 左右の喉（水色・赤紫）は、固定化が進むほど周りの光点が灯る。下は燃料（エキゾチック物質）と口の筐体、右は電力。
 * 空のスロットには入れる物のグレーの見本を出し、喉の様子は数と記号で出す（説明は重ねると出る）。
 */
public class WormholeStabilizerScreen extends FramedScreen<WormholeStabilizerMenu> {
    private static final int CYAN = 0xFF64DCFF;
    private static final int MAGENTA = 0xFFE070FF;
    private static final int[] CHAMBER = {CYAN, MAGENTA};
    private static final int OX = WormholeStabilizerMenu.CONTENT_X;
    private static final int CX = 100;
    private static final int CY = 54;
    private static final int BAR_X = 184;
    private static final int BAR_Y = 20;
    private static final int BAR_H = 84;

    public WormholeStabilizerScreen(WormholeStabilizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WormholeStabilizerMenu.WIDTH, WormholeStabilizerMenu.HEIGHT);
        skin = GuiSkin.COSMIC;
    }

    private boolean working() {
        return menu.progress(0) > 0 || menu.progress(1) > 0;
    }

    /** 喉 i の様子: 0 空き、1 燃料・電力待ち、2 固定化中、3 完了。 */
    private int chamberState(int i) {
        if (menu.progress(i) > 0) {
            return 2;
        }
        if (!menu.slots.get(i).hasItem()) {
            return 0;
        }
        return menu.slots.get(i).getItem().getItem() instanceof io.github.genichimaruo.singulo.wormhole.UnstableMouthItem ? 1 : 3;
    }

    @Override
    protected Panel.Status status() {
        if (working()) {
            return Panel.Status.RUN;
        }
        return chamberState(0) == 1 || chamberState(1) == 1 ? Panel.Status.WARN : Panel.Status.IDLE;
    }

    @Override
    protected int statusColor() {
        return switch (status()) {
            case RUN -> GuiSkin.OK;
            case WARN -> GuiSkin.WARN;
            default -> GuiSkin.IDLE;
        };
    }

    @Override
    protected Component statusText() {
        if (working()) {
            return Component.translatable("gui.singulo.stabilizer.working",
                    Math.round(Math.max(menu.progress(0), menu.progress(1)) * 100));
        }
        return Component.translatable(status() == Panel.Status.WARN ? "gui.singulo.stabilizer.waiting" : "gui.singulo.status.idle");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (renderFrameTooltips(g, mouseX, mouseY) || renderStatTooltips(g, mouseX, mouseY) || !menu.getCarried().isEmpty()) {
            return;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + OX + BAR_X - 1, topPos + BAR_Y - 1, 8, BAR_H + 2)) {
            g.renderComponentTooltip(font, Panel.energyLines(menu.value(WormholeStabilizerMenu.D_ENERGY),
                    menu.value(WormholeStabilizerMenu.D_CAPACITY)), mouseX, mouseY);
            return;
        }
        String[] empty = {"gui.singulo.stabilizer.empty", "gui.singulo.stabilizer.empty", "gui.singulo.stabilizer.fuel", "gui.singulo.stabilizer.casing"};
        for (int i = 0; i < 4; i++) {
            int[] p = WormholeStabilizerMenu.SLOT_POS[i];
            if (!menu.slots.get(i).hasItem() && Panel.inside(mouseX, mouseY, leftPos + p[0], topPos + p[1], 16, 16)) {
                g.renderTooltip(font, Component.translatable(empty[i]), mouseX, mouseY);
                return;
            }
        }
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(id)));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int x = leftPos + OX;
        int y = topPos;
        float t = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick;
        wormhole(g, x + CX, y + CY, t);
        conduits(g, x, y, t);
        for (int i = 0; i < 2; i++) {
            int[] p = WormholeStabilizerMenu.SLOT_POS[i];
            chamber(g, leftPos + p[0], topPos + p[1], menu.progress(i), CHAMBER[i], t);
            ghostSlot(g, leftPos + p[0], topPos + p[1], menu.slots.get(i), item("unstable_wormhole_mouth"));
            g.renderOutline(leftPos + p[0] - 2, topPos + p[1] - 2, 20, 20, CHAMBER[i]);
            chamberStat(g, i, p);
        }
        int[] fuel = WormholeStabilizerMenu.SLOT_POS[2];
        ghostSlot(g, leftPos + fuel[0], topPos + fuel[1], menu.slots.get(2), item("exotic_matter"));
        int[] casing = WormholeStabilizerMenu.SLOT_POS[3];
        ghostSlot(g, leftPos + casing[0], topPos + casing[1], menu.slots.get(3), item("wormhole_mouth_casing"));
        energyBar(g, x + BAR_X, y + BAR_Y, t);
    }

    /** 喉の下: 固定化中は％、待ちは「…」、完了は星（空きは見本だけで何も出さない）。 */
    private void chamberStat(GuiGraphics g, int i, int[] p) {
        int state = chamberState(i);
        int sx = p[0] + 2;
        int sy = p[1] + 24;
        switch (state) {
            case 2 -> {
                int pct = Math.round(menu.progress(i) * 100);
                stat(g, sx - 4, sy, Panel.Icon.HOURGLASS, CHAMBER[i], pct + "%", CHAMBER[i],
                        List.of(Component.translatable("gui.singulo.stabilizer.working", pct)));
            }
            case 1 -> {
                Panel.statusGlyph(g, leftPos + sx + 2, topPos + sy, Panel.Status.IDLE, GuiSkin.WARN);
                area(sx + 1, sy - 1, 9, 9, List.of(Component.translatable("gui.singulo.stabilizer.waiting")));
            }
            case 3 -> {
                Panel.icon(g, leftPos + sx + 2, topPos + sy, Panel.Icon.STAR, GuiSkin.OK);
                area(sx + 1, sy - 1, 9, 9, List.of(Component.translatable("gui.singulo.stabilizer.done")));
            }
            default -> {
            }
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
                int col = GuiSkin.mix(MAGENTA, CYAN, (a % 2 == 0 ? fade : 1 - fade));
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

    /** 左右の喉から真ん中へ流れる光（動いている喉だけ）。x は中身の左端。 */
    private void conduits(GuiGraphics g, int x, int y, float t) {
        for (int i = 0; i < 2; i++) {
            if (menu.progress(i) <= 0) {
                continue;
            }
            int sx = leftPos + WormholeStabilizerMenu.SLOT_POS[i][0] + 8;
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

    /** 喉の周りの、固定化の進みに合わせて灯る16個の光点。 */
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
                c = GuiSkin.mix(color, 0xFFFFFFFF, pulse);
            }
            g.fill(px, py, px + 2, py + 2, c);
        }
    }

    /** 電力: 下から満ちる縦の光（赤紫→水色）。 */
    private void energyBar(GuiGraphics g, int x, int y, float t) {
        Panel.well(g, skin, x, y, 6, BAR_H);
        int cap = Math.max(1, menu.value(WormholeStabilizerMenu.D_CAPACITY));
        int filled = Math.round(BAR_H * Math.min(1F, menu.value(WormholeStabilizerMenu.D_ENERGY) / (float) cap));
        if (filled > 0) {
            g.fillGradient(x, y + BAR_H - filled, x + 6, y + BAR_H, CYAN, MAGENTA);
            Panel.glass(g, x, y + BAR_H - filled, 6, filled);
            int glint = (int) ((t * 1.5F) % Math.max(1, filled));
            g.fill(x, y + BAR_H - glint - 1, x + 6, y + BAR_H - glint, 0x90FFFFFF);
        }
    }
}
