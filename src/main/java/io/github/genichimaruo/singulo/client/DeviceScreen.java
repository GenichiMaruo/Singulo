package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.machine.DeviceMenu;
import io.github.genichimaruo.singulo.machine.DeviceMenu.Kind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * スロットの少ない装置の画面（DeviceMenu）。ワームホール系は暗い宇宙の配色、ほかは白い外装の配色で描く。
 */
public class DeviceScreen extends AbstractContainerScreen<DeviceMenu> {
    // 宇宙の配色（ワームホール系。固定化装置の画面とそろえる）
    private static final int S_BG = 0xFF090C17;
    private static final int S_BG2 = 0xFF0E1324;
    private static final int S_FRAME = 0xFF2B2350;
    private static final int S_FRAME_HI = 0xFF5A4BA0;
    private static final int S_TEXT = 0xFFD8E4FF;
    private static final int S_DIM = 0xFF7F8AB0;
    private static final int CYAN = 0xFF64DCFF;
    private static final int MAGENTA = 0xFFE070FF;
    private static final int GOOD = 0xFF7CF0A0;
    private static final int WARN = 0xFFFF7070;
    private static final int S_SLOT = 0xFF05070E;
    private static final int DARK_MATTER = 0xFF7A3CC8;

    private final Kind kind;

    public DeviceScreen(DeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.kind = menu.kind();
        imageWidth = DeviceMenu.WIDTH;
        imageHeight = DeviceMenu.HEIGHT;
        inventoryLabelX = DeviceMenu.INV_X;
        inventoryLabelY = DeviceMenu.INV_Y - 11;
        titleLabelX = 10;
    }

    private int v(int i) {
        return menu.value(i);
    }

    private float time(float partialTick) {
        return (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick;
    }

    private int text() {
        return kind.space ? S_TEXT : Panel.TEXT;
    }

    private int dim() {
        return kind.space ? S_DIM : 0xFF7A8490;
    }

    // ------------------------------------------------------------------ 共通

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (kind == Kind.WORMHOLE_PORT && Panel.inside(mouseX, mouseY, leftPos + BIT_X, topPos + BIT_Y,
                bitX(DeviceMenu.Port.BITS) - BIT_GAP - BIT_X, BIT_SIZE)) {
            g.renderTooltip(font, font.split(tr("port.channel_hint"), 180), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        float t = time(partialTick);
        if (kind.space) {
            spaceBackground(g, x, y, t);
        } else {
            Panel.background(g, x, y, imageWidth, imageHeight);
            g.fill(x + 8, y + 112, x + imageWidth - 8, y + 113, Panel.SEAM);
        }
        switch (kind) {
            case WORMHOLE_GENERATOR -> drawGenerator(g, x, y, t);
            case WORMHOLE_MOUTH -> drawMouth(g, x, y, t, mouseX, mouseY);
            case WORMHOLE_PORT -> {
                drawPort(g, x, y, t);
                drawChannel(g, x, y, mouseX, mouseY);
            }
            case CONTAINMENT_TANK -> drawTank(g, x, y, t);
            case HALO_COLLECTOR -> drawHalo(g, x, y, t);
            case MUON_COLLECTOR -> drawMuon(g, x, y, t);
            case DETECTOR -> drawDetector(g, x, y, t, mouseX, mouseY);
        }
        for (Slot s : menu.slots) {
            if (s.index >= kind.slots) {
                if (kind.space) {
                    spaceSlot(g, x + s.x, y + s.y, 0xFF2E3658);
                } else {
                    Panel.slot(g, x + s.x, y + s.y);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY + 2, text(), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, dim(), false);
        switch (kind) {
            case WORMHOLE_GENERATOR -> labelsGenerator(g);
            case WORMHOLE_MOUTH -> labelsMouth(g);
            case WORMHOLE_PORT -> labelsPort(g);
            case CONTAINMENT_TANK -> labelsTank(g);
            case HALO_COLLECTOR -> labelsHalo(g);
            case MUON_COLLECTOR -> labelsMuon(g);
            case DETECTOR -> labelsDetector(g);
        }
    }

    private void line(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font, c, x, y, color, false);
    }

    /** 文を幅 maxWidth に収まるよう折り返して描き、次の行の y を返す。 */
    private int para(GuiGraphics g, Component c, int x, int y, int maxWidth, int color) {
        for (var seq : font.split(c, maxWidth)) {
            g.drawString(font, seq, x, y, color, false);
            y += 10;
        }
        return y;
    }

    /** 右の電力バーの手前まで（白い画面の右側の文の幅）。 */
    private static int widthTo(int x) {
        return 178 - x;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.singulo.device." + key, args);
    }

    private static String num(long n) {
        return String.format("%,d", n);
    }

    /** 帯域の上限（上限なしは ∞）。 */
    private static String limit(long n) {
        return n == io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.UNLIMITED ? "∞" : power(n);
    }

    /** 大きな電力（FE/t）を読みやすく（k・M・G）。 */
    private static String power(long fe) {
        if (fe >= 1_000_000_000L) {
            return String.format("%.2f G", fe / 1e9);
        }
        if (fe >= 1_000_000L) {
            return String.format("%.1f M", fe / 1e6);
        }
        if (fe >= 1_000L) {
            return String.format("%.1f k", fe / 1e3);
        }
        return String.valueOf(fe);
    }

    // ------------------------------------------------------------------ 宇宙の配色の部品

    private void spaceBackground(GuiGraphics g, int x, int y, float t) {
        int w = imageWidth;
        int h = imageHeight;
        g.fill(x, y, x + w, y + h, S_FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, S_FRAME_HI);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, S_BG);
        g.fillGradient(x + 2, y + 2, x + w - 2, y + 112, S_BG2, S_BG);
        for (int i = 0; i < w - 12; i++) {
            float k = 0.5F + 0.5F * Mth.sin(i * 0.05F - t * 0.06F);
            g.fill(x + 6 + i, y + 3, x + 7 + i, y + 4, lerp(CYAN, MAGENTA, k));
        }
        long seed = 1469598103934665603L + kind.ordinal();
        for (int i = 0; i < 60; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sx = (int) Math.floorMod(seed >>> 20, (long) (w - 8)) + 4;
            int sy = (int) Math.floorMod(seed >>> 40, 100L) + 8;
            float tw = 0.5F + 0.5F * Mth.sin(t * 0.08F + i * 1.7F);
            g.fill(x + sx, y + sy, x + sx + 1, y + sy + 1, ((int) (50 + 140 * tw) << 24) | 0xCFE0FF);
        }
        g.fill(x + 8, y + 112, x + w - 8, y + 113, S_FRAME_HI);
    }

    private static void spaceSlot(GuiGraphics g, int x, int y, int edge) {
        g.fill(x - 1, y - 1, x + 17, y + 17, edge);
        g.fill(x, y, x + 16, y + 16, S_SLOT);
    }

    /** 渦（ワームホール）。r は大きさ、speed は回る速さ、bright は明るさ（0〜1）。 */
    private static void vortex(GuiGraphics g, int cx, int cy, float r, float t, float speed, float bright) {
        int arms = 4;
        int steps = (int) (r * 1.8F);
        for (int a = 0; a < arms; a++) {
            for (int k = 0; k < steps; k++) {
                float rr = 3 + k * (r - 3) / steps;
                float ang = a * Mth.TWO_PI / arms + k * (8F / steps) + t * speed;
                int px = cx + Math.round(Mth.cos(ang) * rr);
                int py = cy + Math.round(Mth.sin(ang) * rr * 0.82F);
                float fade = 1 - (float) k / steps;
                int col = lerp(MAGENTA, CYAN, a % 2 == 0 ? fade : 1 - fade);
                int alpha = (int) (255 * bright * (0.25F + 0.75F * fade));
                g.fill(px, py, px + (k < steps / 4 ? 2 : 1), py + (k < steps / 4 ? 2 : 1), (alpha << 24) | (col & 0xFFFFFF));
            }
        }
        for (int dy = -3; dy <= 3; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, 12 - dy * dy * 1.4)));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0xFF000000);
        }
    }

    /** 円い進み具合（点の輪）。 */
    private static void ring(GuiGraphics g, int cx, int cy, int r, float fraction, int on, int off, int dots) {
        int lit = Math.round(fraction * dots);
        for (int i = 0; i < dots; i++) {
            float ang = -Mth.HALF_PI + i * Mth.TWO_PI / dots;
            int px = cx + Math.round(Mth.cos(ang) * r) - 1;
            int py = cy + Math.round(Mth.sin(ang) * r * 0.9F) - 1;
            g.fill(px, py, px + 2, py + 2, i < lit ? on : off);
        }
    }

    private static int lerp(int a, int b, float k) {
        k = Mth.clamp(k, 0, 1);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * k) << 16) | ((int) (ag + (bg - ag) * k) << 8) | (int) (ab + (bb - ab) * k);
    }

    /** 横のゲージ（左から fraction だけ満ちる）。 */
    private void gauge(GuiGraphics g, int x, int y, int w, int h, float fraction, int from, int to) {
        int edge = kind.space ? S_FRAME_HI : Panel.WELL_EDGE;
        int bg = kind.space ? S_SLOT : Panel.WELL;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, edge);
        g.fill(x, y, x + w, y + h, bg);
        int f = Math.round(w * Mth.clamp(fraction, 0, 1));
        if (f > 0) {
            for (int i = 0; i < f; i++) {
                g.fill(x + i, y, x + i + 1, y + h, lerp(from, to, (float) i / Math.max(1, w - 1)));
            }
        }
    }

    // ------------------------------------------------------------------ ワームホール生成器

    private void drawGenerator(GuiGraphics g, int x, int y, float t) {
        float p = v(DeviceMenu.Gen.PROGRESS) / (float) io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity.GENERATE_TICKS;
        boolean running = p > 0;
        int cx = x + 100;
        int cy = y + 48;
        vortex(g, cx, cy, 20, t, running ? 0.05F + 0.25F * p : 0.02F, running ? 0.5F + 0.5F * p : 0.3F);
        ring(g, cx, cy, 30, p, lerp(CYAN, 0xFFFFFFFF, p), 0xFF262C48, 40);
        float in = Math.min(1F, v(DeviceMenu.Gen.RECEIVED) / (float) Math.max(1, v(DeviceMenu.Gen.REQUIRED)));
        gauge(g, x + 20, y + 76, 160, 4, in, MAGENTA, in >= 1 ? GOOD : CYAN);
        int[][] at = DeviceMenu.slotPos(kind);
        for (int i = 0; i < 2; i++) {
            spaceSlot(g, x + at[i][0], y + at[i][1], i == 0 ? CYAN : MAGENTA);
        }
    }

    private void labelsGenerator(GuiGraphics g) {
        boolean formed = v(DeviceMenu.Gen.FORMED) != 0;
        int progress = v(DeviceMenu.Gen.PROGRESS);
        boolean hasOutput = menu.slots.get(0).hasItem() || menu.slots.get(1).hasItem();
        Component status = !formed ? tr("not_formed")
                : hasOutput ? tr("generator.done")
                : progress > 0 ? tr("generator.working", progress / 20, 10)
                : tr("generator.idle");
        int color = !formed ? WARN : progress > 0 || hasOutput ? GOOD : S_DIM;
        line(g, status, 100 - font.width(status) / 2, 16, color);
        Component in = tr("generator.input", power(v(DeviceMenu.Gen.RECEIVED)), power(v(DeviceMenu.Gen.REQUIRED)));
        line(g, in, 20, 66, S_DIM);
        int[][] at = DeviceMenu.slotPos(kind);
        for (int i = 0; i < 2; i++) {
            int remain = v(i == 0 ? DeviceMenu.Gen.REMAIN_0 : DeviceMenu.Gen.REMAIN_1);
            if (menu.slots.get(i).hasItem() && remain > 0) {
                Component c = tr("generator.expires", remain / 20);
                line(g, c, at[i][0] + 8 - font.width(c) / 2, at[i][1] + 19, remain < 400 ? WARN : S_TEXT);
            }
        }
    }

    // ------------------------------------------------------------------ ワームホールの口

    private static final int MOUTH_BTN_Y = 22;
    private static final int MOUTH_MINUS_X = 140;
    private static final int MOUTH_PLUS_X = 170;
    private static final int BTN = 16;

    private void drawMouth(GuiGraphics g, int x, int y, float t, int mouseX, int mouseY) {
        int size = v(DeviceMenu.Mouth.SIZE);
        int target = v(DeviceMenu.Mouth.TARGET);
        int cx = x + 56;
        int cy = y + 52;
        // 喉の大きさを入れ子の輪で（今の大きさは明るく、目標は点線）
        for (int s = 1; s <= 3; s++) {
            int r = 10 + s * 9;
            int col = s <= size ? lerp(CYAN, MAGENTA, s / 3F) : (s <= target ? 0xFF4A4F7A : 0xFF1C2038);
            ring(g, cx, cy, r, 1, col, col, 18 + s * 8);
        }
        vortex(g, cx, cy, 6 + size * 5, t, 0.04F + 0.03F * size, size > 0 ? 0.9F : 0.3F);
        button(g, x + MOUTH_MINUS_X, y + MOUTH_BTN_Y, "-", mouseX, mouseY, target > 1);
        button(g, x + MOUTH_PLUS_X, y + MOUTH_BTN_Y, "+", mouseX, mouseY, target < 3);
        spaceSlot(g, x + DeviceMenu.slotPos(kind)[0][0], y + DeviceMenu.slotPos(kind)[0][1], MAGENTA);
    }

    private void button(GuiGraphics g, int bx, int by, String label, int mouseX, int mouseY, boolean enabled) {
        boolean hover = enabled && Panel.inside(mouseX, mouseY, bx, by, BTN, BTN);
        g.fill(bx, by, bx + BTN, by + BTN, enabled ? (hover ? CYAN : S_FRAME_HI) : 0xFF20243C);
        g.fill(bx + 1, by + 1, bx + BTN - 1, by + BTN - 1, enabled ? 0xFF161B33 : 0xFF0E1124);
        g.drawString(font, label, bx + (BTN - font.width(label)) / 2 + 1, by + 4, enabled ? 0xFFFFFFFF : 0xFF50566E, false);
    }

    private void labelsMouth(GuiGraphics g) {
        int size = v(DeviceMenu.Mouth.SIZE);
        int target = v(DeviceMenu.Mouth.TARGET);
        int side = 2 * size + 1;
        int x = 96;
        int w = 194 - x;
        int y = para(g, tr("mouth.size", side, side, 2 * target + 1, 2 * target + 1), x, 52, w, S_TEXT);
        int partner = v(DeviceMenu.Mouth.PARTNER);
        Component link = partner == 0 ? tr("mouth.no_partner") : partner == 1 ? tr("mouth.unloaded",
                v(DeviceMenu.Mouth.PX), v(DeviceMenu.Mouth.PY), v(DeviceMenu.Mouth.PZ))
                : tr("mouth.linked", v(DeviceMenu.Mouth.PX), v(DeviceMenu.Mouth.PY), v(DeviceMenu.Mouth.PZ));
        y = para(g, link, x, y + 2, w, partner == 2 ? GOOD : partner == 1 ? 0xFFFFC060 : WARN);
        if (v(DeviceMenu.Mouth.CROSS) != 0) {
            y = para(g, tr("mouth.cross"), x, y, w, MAGENTA);
        }
        int s = Math.max(0, Math.min(3, size));
        y = para(g, tr("mouth.bandwidth", limit(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.ENERGY_PER_TICK[s]),
                limit(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.ITEMS_PER_TICK[s]),
                limit(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.FLUID_PER_TICK[s])), x, y + 2, w, S_DIM);
        int ports = v(DeviceMenu.Mouth.PORTS);
        int portLimit = v(DeviceMenu.Mouth.PORT_LIMIT);
        y = para(g, tr("mouth.ports", Math.min(ports, portLimit), portLimit, ports), x, y, w, ports > portLimit ? WARN : S_DIM);
        if (size <= 0) {
            para(g, tr("mouth.dormant"), x, y + 2, w, WARN);
        }
        int tpm = v(DeviceMenu.Mouth.TICKS_PER_MATTER);
        Component upkeep = tpm > 0 ? tr("mouth.upkeep", String.format("%.1f", tpm / 1200.0)) : tr("mouth.upkeep_none");
        int[] f = DeviceMenu.slotPos(kind)[0];
        int fy = para(g, upkeep, f[0] + 22, f[1], 70, S_DIM);
        int grace = v(DeviceMenu.Mouth.GRACE);
        if (grace > 0) {
            para(g, tr("mouth.grace", grace / 20), f[0] + 22, fy, 70, GOOD);
        } else if (v(DeviceMenu.Mouth.STARVE) > 0) {
            int left = (io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.SHRINK_TICKS - v(DeviceMenu.Mouth.STARVE)) / 20;
            para(g, tr("mouth.starving", left), f[0] + 22, fy, 70, WARN);
        }
    }

    // ------------------------------------------------------------------ ワームホール・ポート

    /** ポート番号の7つの丸（左が 64、右が 1。押すとオンオフが切り替わり、オンの丸の和が番号）。 */
    private static final int BIT_Y = 94;
    private static final int BIT_X = 10;
    private static final int BIT_SIZE = 12;
    private static final int BIT_GAP = 6;

    /** 丸 i（左から）の x。左の丸ほど上のビット。 */
    private static int bitX(int i) {
        return BIT_X + i * (BIT_SIZE + BIT_GAP);
    }

    private static int bitOf(int i) {
        return DeviceMenu.Port.BITS - 1 - i;
    }

    /** 丸いボタン（12×12）。オンなら光る。 */
    private static void roundButton(GuiGraphics g, int bx, int by, boolean on, boolean hover) {
        int edge = hover ? 0xFFFFFFFF : on ? CYAN : S_FRAME_HI;
        int fill = on ? CYAN : 0xFF161B33;
        int[] rows = {4, 2, 1, 1, 0, 0, 0, 0, 1, 1, 2, 4};
        for (int r = 0; r < BIT_SIZE; r++) {
            int a = rows[r];
            g.fill(bx + a, by + r, bx + BIT_SIZE - a, by + r + 1, edge);
            if (r > 0 && r < BIT_SIZE - 1) {
                int b = Math.max(a, 1) + (r == 1 || r == BIT_SIZE - 2 ? 1 : 0);
                g.fill(bx + b, by + r, bx + BIT_SIZE - b, by + r + 1, fill);
            }
        }
        if (on) {
            g.fill(bx + 4, by + 3, bx + 6, by + 5, 0xFFFFFFFF);
        }
    }

    private void drawChannel(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int channel = v(DeviceMenu.Port.CHANNEL);
        for (int i = 0; i < DeviceMenu.Port.BITS; i++) {
            int bx = x + bitX(i);
            boolean on = (channel >> bitOf(i) & 1) != 0;
            roundButton(g, bx, y + BIT_Y, on, Panel.inside(mouseX, mouseY, bx, y + BIT_Y, BIT_SIZE, BIT_SIZE));
        }
    }

    private void drawPort(GuiGraphics g, int x, int y, float t) {
        int state = v(DeviceMenu.Port.STATE);
        vortex(g, x + 40, y + 56, 18, t, state == 2 ? 0.06F : 0.015F, state == 2 ? 0.9F : 0.3F);
        if (state == DeviceMenu.Port.STATE_OVER_LIMIT) {
            // 止まっている印（渦に斜線）
            for (int k = -12; k <= 12; k++) {
                g.fill(x + 40 + k, y + 56 - k, x + 42 + k, y + 58 - k, WARN);
            }
        }
        if (state == 2) {
            for (int px = x + 62; px < x + 180; px++) {
                float k = (px * 0.35F - t * 0.6F) % 4;
                if ((k < 0 ? k + 4 : k) < 2) {
                    g.fill(px, y + 56, px + 1, y + 57, CYAN);
                }
            }
        }
    }

    private void labelsPort(GuiGraphics g) {
        int state = v(DeviceMenu.Port.STATE);
        int x = 72;
        int w = 194 - x;
        Component s = switch (state) {
            case DeviceMenu.Port.STATE_NO_MOUTH -> tr("port.no_mouth");
            case DeviceMenu.Port.STATE_NO_PARTNER -> tr("port.no_partner");
            case DeviceMenu.Port.STATE_OVER_LIMIT -> tr("port.inactive");
            default -> tr("port.linked");
        };
        int y = para(g, s, x, 20, w, state == DeviceMenu.Port.STATE_LINKED ? GOOD : WARN);
        if (state != DeviceMenu.Port.STATE_NO_MOUTH) {
            int side = 2 * v(DeviceMenu.Port.SIZE) + 1;
            y = para(g, tr("port.throat", side, side), x, y + 2, w, S_TEXT);
            int rank = v(DeviceMenu.Port.RANK);
            int limit = v(DeviceMenu.Port.LIMIT);
            y = para(g, tr("port.rank", rank, limit), x, y, w, rank > limit ? WARN : S_DIM);
        }
        if (state == DeviceMenu.Port.STATE_LINKED) {
            para(g, tr("port.targets", v(DeviceMenu.Port.ENERGY), v(DeviceMenu.Port.ITEMS), v(DeviceMenu.Port.FLUIDS)), x, y + 2, w, S_TEXT);
        }
        line(g, tr("port.channel", v(DeviceMenu.Port.CHANNEL)), BIT_X, BIT_Y - 11, S_TEXT);
    }

    // ------------------------------------------------------------------ 重力閉じ込めタンク

    private void drawTank(GuiGraphics g, int x, int y, float t) {
        int amount = v(DeviceMenu.Tank.AMOUNT);
        int cap = Math.max(1, v(DeviceMenu.Tank.CAPACITY));
        // 大きな縦のタンク: ゆっくり渦を巻くダークマター
        int tx = x + 20;
        int ty = y + 20;
        int tw = 40;
        int th = 84;
        g.fill(tx - 2, ty - 2, tx + tw + 2, ty + th + 2, Panel.WELL_EDGE);
        g.fill(tx, ty, tx + tw, ty + th, Panel.WELL);
        int filled = Math.round(th * Math.min(1F, amount / (float) cap));
        for (int yy = 0; yy < filled; yy++) {
            for (int xx = 0; xx < tw; xx += 2) {
                float n = Mth.sin(xx * 0.3F + (yy + t * 0.5F) * 0.25F) * Mth.cos(yy * 0.2F - t * 0.05F);
                int c = lerp(DARK_MATTER, 0xFF1A0838, 0.5F + 0.5F * n);
                g.fill(tx + xx, ty + th - 1 - yy, tx + xx + 2, ty + th - yy, c);
            }
        }
        boolean contained = v(DeviceMenu.Tank.CONTAINED) != 0;
        // 場の輪（閉じ込めている間は光る）
        for (int i = 0; i < 4; i++) {
            int ry = ty + 10 + i * 20;
            g.fill(tx - 4, ry, tx + tw + 4, ry + 1, contained ? lerp(Panel.GLOW, 0xFFFFFFFF, 0.5F + 0.5F * Mth.sin(t * 0.2F + i))
                    : 0xFF9A5050);
        }
        Panel.verticalBar(g, x + 182, y + 20, 6, 84, v(DeviceMenu.Tank.ENERGY) / (double) Math.max(1, v(DeviceMenu.Tank.ENERGY_MAX)),
                Panel.GLOW);
    }

    private void labelsTank(GuiGraphics g) {
        int x = 72;
        int w = widthTo(x);
        int y = para(g, tr("tank.label"), x, 22, w, 0xFF7A8490);
        y = para(g, Component.literal(num(v(DeviceMenu.Tank.AMOUNT)) + " / " + num(v(DeviceMenu.Tank.CAPACITY)) + " mB"), x, y, w, Panel.TEXT);
        boolean contained = v(DeviceMenu.Tank.CONTAINED) != 0;
        y = para(g, contained ? tr("tank.contained") : tr("tank.leaking"), x, y + 4, w, contained ? 0xFF2E9A5A : Panel.RED);
        y = para(g, tr("tank.upkeep", num(io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity.FE_PER_TICK)), x, y + 4, w,
                0xFF7A8490);
        para(g, tr("energy_short", power(v(DeviceMenu.Tank.ENERGY)), power(v(DeviceMenu.Tank.ENERGY_MAX))), x, y, w, 0xFF7A8490);
    }

    // ------------------------------------------------------------------ ハロー捕集器

    private void drawHalo(GuiGraphics g, int x, int y, float t) {
        boolean core = v(DeviceMenu.Halo.CORE) != 0;
        int cx = x + 40;
        int cy = y + 56;
        // 炉心へ流れ込む暗黒物質の粒
        for (int i = 0; i < 40; i++) {
            float ang = i * 2.4F;
            float r = 30 - ((t * (core ? 0.6F : 0.1F) + i * 3.7F) % 26);
            int px = cx + Math.round(Mth.cos(ang) * r);
            int py = cy + Math.round(Mth.sin(ang) * r);
            g.fill(px, py, px + 1, py + 1, core ? DARK_MATTER : 0xFF8890A0);
        }
        for (int dy = -4; dy <= 4; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, 20 - dy * dy)));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, core ? 0xFF000000 : 0xFF50565E);
        }
        gauge(g, x + 76, y + 76, 100, 6, v(DeviceMenu.Halo.AMOUNT) / (float) Math.max(1, v(DeviceMenu.Halo.BUFFER)),
                0xFF4A2080, DARK_MATTER);
        Panel.verticalBar(g, x + 182, y + 20, 6, 84, v(DeviceMenu.Halo.ENERGY) / (double) Math.max(1, v(DeviceMenu.Halo.ENERGY_MAX)),
                Panel.GLOW);
    }

    private void labelsHalo(GuiGraphics g) {
        int x = 76;
        int w = widthTo(x);
        boolean core = v(DeviceMenu.Halo.CORE) != 0;
        int y = para(g, core ? tr("halo.core", num(v(DeviceMenu.Halo.CORE_MASS))) : tr("halo.no_core",
                io.github.genichimaruo.singulo.darkmatter.HaloCollectorBlockEntity.RANGE), x, 20, w, core ? 0xFF2E9A5A : Panel.RED);
        y = para(g, tr("halo.rate", v(DeviceMenu.Halo.RATE)), x, y + 2, w, Panel.TEXT);
        para(g, tr("halo.tank", num(v(DeviceMenu.Halo.AMOUNT)), num(v(DeviceMenu.Halo.BUFFER))), x, Math.max(y + 2, 64), w, 0xFF7A8490);
        para(g, tr("halo.leak"), x, 88, w, 0xFF7A8490);
    }

    // ------------------------------------------------------------------ 宇宙線ミュオン収集器

    private void drawMuon(GuiGraphics g, int x, int y, float t) {
        // 高さの目盛り（Y=100 で0、Y=200 で最大）と、今の高さの印
        int bx = x + 24;
        int by = y + 20;
        int bh = 84;
        g.fill(bx - 1, by - 1, bx + 9, by + bh + 1, Panel.WELL_EDGE);
        g.fillGradient(bx, by, bx + 8, by + bh, 0xFF78D2F0, 0xFF39424A);
        int yy = v(DeviceMenu.Muon.Y);
        float k = Mth.clamp((yy - 100) / 100F, 0, 1);
        int my = by + bh - Math.round(bh * k);
        g.fill(bx - 4, my, bx + 12, my + 1, Panel.AMBER);
        // 降ってくる宇宙線
        boolean sky = v(DeviceMenu.Muon.SKY) != 0;
        if (sky) {
            for (int i = 0; i < 12; i++) {
                int px = x + 60 + (i * 37) % 80;
                int py = y + 18 + (int) ((t * (1.5F + i % 3) + i * 13) % 80);
                g.fill(px, py, px + 1, py + 3, 0x9978D2F0);
            }
        }
        Panel.arrow(g, x + 120, y + 60, v(DeviceMenu.Muon.PROGRESS) / 100.0);
        Panel.slot(g, x + DeviceMenu.slotPos(kind)[0][0], y + DeviceMenu.slotPos(kind)[0][1]);
    }

    private void labelsMuon(GuiGraphics g) {
        int x = 44;
        int w = 194 - x;
        boolean sky = v(DeviceMenu.Muon.SKY) != 0;
        int y = para(g, tr("muon.altitude", v(DeviceMenu.Muon.Y)), x, 20, w, Panel.TEXT);
        para(g, sky ? tr("muon.rate", v(DeviceMenu.Muon.RATE)) : tr("muon.no_sky"), x, y + 2, w, sky ? Panel.TEXT : Panel.RED);
        para(g, tr("muon.hint"), x, 92, 194 - x, 0xFF7A8490);
    }

    // ------------------------------------------------------------------ 重力波検出器

    private static final int OBSERVE_X = 100;
    private static final int OBSERVE_Y = 86;
    private static final int OBSERVE_W = 72;
    private static final int OBSERVE_H = 16;

    private void drawDetector(GuiGraphics g, int x, int y, float t, int mouseX, int mouseY) {
        int cx = x + 52;
        int cy = y + 60;
        // 方位の盤（北が上）
        for (int r : new int[]{30, 20}) {
            ring(g, cx, cy, r, 1, Panel.SEAM, Panel.SEAM, 48);
        }
        for (int i = 0; i < 8; i++) {
            float ang = -Mth.HALF_PI + i * Mth.TWO_PI / 8;
            int px = cx + Math.round(Mth.cos(ang) * 34);
            int py = cy + Math.round(Mth.sin(ang) * 34 * 0.9F);
            g.fill(px - 1, py - 1, px + 1, py + 1, i == 0 ? Panel.RED : Panel.WELL_EDGE);
        }
        // 観測した向きへの針（脈打つ）
        if (v(DeviceMenu.Detector.OBSERVED) != 0 && v(DeviceMenu.Detector.FOUND) != 0) {
            float ang = -Mth.HALF_PI + v(DeviceMenu.Detector.DIRECTION) * Mth.TWO_PI / 8;
            float pulse = 0.7F + 0.3F * Mth.sin(t * 0.3F);
            for (int s = 0; s < 28; s++) {
                int px = cx + Math.round(Mth.cos(ang) * s);
                int py = cy + Math.round(Mth.sin(ang) * s * 0.9F);
                g.fill(px - 1, py - 1, px + 1, py + 1, lerp(Panel.GLOW, 0xFFFFFFFF, pulse * s / 28F));
            }
        }
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, Panel.WELL);
        boolean hover = Panel.inside(mouseX, mouseY, x + OBSERVE_X, y + OBSERVE_Y, OBSERVE_W, OBSERVE_H);
        boolean can = v(DeviceMenu.Detector.ENERGY) >= v(DeviceMenu.Detector.COST);
        g.fill(x + OBSERVE_X, y + OBSERVE_Y, x + OBSERVE_X + OBSERVE_W, y + OBSERVE_Y + OBSERVE_H,
                can ? (hover ? Panel.GLOW : Panel.WELL_EDGE) : Panel.SEAM);
        Panel.verticalBar(g, x + 182, y + 20, 6, 84,
                v(DeviceMenu.Detector.ENERGY) / (double) Math.max(1, v(DeviceMenu.Detector.ENERGY_MAX)), Panel.GLOW);
    }

    private void labelsDetector(GuiGraphics g) {
        int x = 100;
        int w = widthTo(x);
        Component b = tr("detector.observe");
        line(g, b, OBSERVE_X + (OBSERVE_W - font.width(b)) / 2, OBSERVE_Y + 4, 0xFFFFFFFF);
        if (v(DeviceMenu.Detector.OBSERVED) == 0) {
            para(g, tr("detector.not_yet"), x, 22, w, 0xFF7A8490);
        } else if (v(DeviceMenu.Detector.FOUND) == 0) {
            para(g, Component.translatable("gui.singulo.detector.none"), x, 22, w, Panel.RED);
        } else {
            String[] dirs = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
            int y = para(g, Component.translatable("direction.singulo." + dirs[v(DeviceMenu.Detector.DIRECTION) & 7]), x, 22, w, Panel.TEXT);
            para(g, Component.translatable("gui.singulo.detector.band." + v(DeviceMenu.Detector.BAND)), x, y + 2, w, Panel.TEXT);
        }
        para(g, tr("detector.cost", num(v(DeviceMenu.Detector.COST))), x, 64, w, 0xFF7A8490);
    }

    // ------------------------------------------------------------------ ボタン

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            int id = -1;
            if (kind == Kind.WORMHOLE_MOUTH) {
                if (Panel.inside(mouseX, mouseY, leftPos + MOUTH_MINUS_X, topPos + MOUTH_BTN_Y, BTN, BTN)) {
                    id = DeviceMenu.Mouth.BUTTON_SMALLER;
                } else if (Panel.inside(mouseX, mouseY, leftPos + MOUTH_PLUS_X, topPos + MOUTH_BTN_Y, BTN, BTN)) {
                    id = DeviceMenu.Mouth.BUTTON_BIGGER;
                }
            } else if (kind == Kind.WORMHOLE_PORT) {
                for (int i = 0; i < DeviceMenu.Port.BITS; i++) {
                    if (Panel.inside(mouseX, mouseY, leftPos + bitX(i), topPos + BIT_Y, BIT_SIZE, BIT_SIZE)) {
                        id = DeviceMenu.Port.BUTTON_BIT + bitOf(i);
                    }
                }
            } else if (kind == Kind.DETECTOR
                    && Panel.inside(mouseX, mouseY, leftPos + OBSERVE_X, topPos + OBSERVE_Y, OBSERVE_W, OBSERVE_H)) {
                id = DeviceMenu.Detector.BUTTON_OBSERVE;
            }
            if (id >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
