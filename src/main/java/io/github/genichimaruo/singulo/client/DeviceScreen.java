package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.machine.DeviceMenu;
import io.github.genichimaruo.singulo.machine.DeviceMenu.Kind;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * スロットの少ない装置の画面（DeviceMenu）。ワームホール系は宇宙のスキン、ほかは標準のスキンで描く。
 * 状態はヘッダーの記号、数はアイコンと値で出し、説明の文は重ねると出す。
 * 中身は幅200で設計してあり、DeviceMenu.CONTENT_X だけ右へずらして枠の中央に置く。
 */
public class DeviceScreen extends FramedScreen<DeviceMenu> {
    private static final int CYAN = 0xFF64DCFF;
    private static final int MAGENTA = 0xFFE070FF;
    private static final int DARK_MATTER = 0xFF7A3CC8;
    private static final int OX = DeviceMenu.CONTENT_X;

    private final Kind kind;

    public DeviceScreen(DeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, DeviceMenu.WIDTH, DeviceMenu.HEIGHT);
        this.kind = menu.kind();
        skin = kind.space ? GuiSkin.COSMIC : GuiSkin.STANDARD;
    }

    private int v(int i) {
        return menu.value(i);
    }

    private float time(float partialTick) {
        return (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.singulo.device." + key, args);
    }

    /** 帯域の上限（上限なしは ∞）。 */
    private static String limit(long n) {
        return n == io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.UNLIMITED ? "∞" : EnergyText.of(n);
    }

    /** 中身の座標（幅200の設計）から画面の座標へ。 */
    private int cx(int x) {
        return leftPos + OX + x;
    }

    private int cy(int y) {
        return topPos + y;
    }

    // ------------------------------------------------------------------ 状態（ヘッダーの記号）

    private record State(Panel.Status icon, int color, Component text, List<Component> more) {}

    private State state() {
        return switch (kind) {
            case WORMHOLE_GENERATOR -> {
                boolean formed = v(DeviceMenu.Gen.FORMED) != 0;
                int progress = v(DeviceMenu.Gen.PROGRESS);
                boolean hasOutput = menu.slots.get(0).hasItem() || menu.slots.get(1).hasItem();
                yield !formed ? new State(Panel.Status.BROKEN, GuiSkin.BAD, tr("not_formed"), List.of())
                        : hasOutput ? new State(Panel.Status.RUN, GuiSkin.OK, tr("generator.done"), List.of())
                        : progress > 0 ? new State(Panel.Status.RUN, GuiSkin.OK, tr("generator.working", progress / 20, 10), List.of())
                        : new State(Panel.Status.IDLE, GuiSkin.IDLE, tr("generator.idle"), List.of());
            }
            case WORMHOLE_MOUTH -> {
                int partner = v(DeviceMenu.Mouth.PARTNER);
                Object[] at = {v(DeviceMenu.Mouth.PX), v(DeviceMenu.Mouth.PY), v(DeviceMenu.Mouth.PZ)};
                if (v(DeviceMenu.Mouth.SIZE) <= 0) {
                    yield new State(Panel.Status.WARN, GuiSkin.WARN, tr("mouth.dormant"), List.of());
                }
                yield partner == 0 ? new State(Panel.Status.WARN, GuiSkin.WARN, tr("mouth.no_partner"), List.of())
                        : partner == 1 ? new State(Panel.Status.IDLE, GuiSkin.IDLE, tr("mouth.unloaded", at), List.of())
                        : new State(Panel.Status.RUN, GuiSkin.OK, tr("mouth.linked", at), List.of());
            }
            case WORMHOLE_PORT -> {
                int s = v(DeviceMenu.Port.STATE);
                yield switch (s) {
                    case DeviceMenu.Port.STATE_NO_MOUTH -> new State(Panel.Status.WARN, GuiSkin.WARN, tr("port.no_mouth"), List.of());
                    case DeviceMenu.Port.STATE_NO_PARTNER -> new State(Panel.Status.WARN, GuiSkin.WARN, tr("port.no_partner"), List.of());
                    case DeviceMenu.Port.STATE_OVER_LIMIT -> new State(Panel.Status.OFF, GuiSkin.WARN, tr("port.inactive"), List.of());
                    default -> new State(Panel.Status.RUN, GuiSkin.OK, tr("port.linked"), List.of());
                };
            }
            case CONTAINMENT_TANK -> v(DeviceMenu.Tank.CONTAINED) != 0
                    ? new State(Panel.Status.RUN, GuiSkin.OK, tr("tank.contained"), List.of())
                    : new State(Panel.Status.WARN, GuiSkin.WARN, tr("tank.leaking"), List.of());
            case HALO_COLLECTOR -> v(DeviceMenu.Halo.CORE) != 0
                    ? new State(Panel.Status.RUN, GuiSkin.OK, tr("halo.core", v(DeviceMenu.Halo.CORE_MASS)), List.of())
                    : new State(Panel.Status.WARN, GuiSkin.WARN,
                    tr("halo.no_core", io.github.genichimaruo.singulo.darkmatter.HaloCollectorBlockEntity.RANGE), List.of());
            case MUON_COLLECTOR -> v(DeviceMenu.Muon.SKY) != 0
                    ? new State(Panel.Status.RUN, GuiSkin.OK, tr("muon.rate", v(DeviceMenu.Muon.RATE)), List.of())
                    : new State(Panel.Status.WARN, GuiSkin.WARN, tr("muon.no_sky"), List.of());
            case DETECTOR -> {
                if (v(DeviceMenu.Detector.OBSERVED) == 0) {
                    yield new State(Panel.Status.IDLE, GuiSkin.IDLE, tr("detector.not_yet"), List.of());
                }
                if (v(DeviceMenu.Detector.FOUND) == 0) {
                    yield new State(Panel.Status.WARN, GuiSkin.WARN, Component.translatable("gui.singulo.detector.none"), List.of());
                }
                yield new State(Panel.Status.RUN, GuiSkin.OK, direction(),
                        List.of(Component.translatable("gui.singulo.detector.band." + v(DeviceMenu.Detector.BAND))));
            }
        };
    }

    private Component direction() {
        String[] dirs = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        return Component.translatable("direction.singulo." + dirs[v(DeviceMenu.Detector.DIRECTION) & 7]);
    }

    @Override
    protected Panel.Status status() {
        return state().icon();
    }

    @Override
    protected int statusColor() {
        return state().color();
    }

    @Override
    protected Component statusText() {
        return state().text();
    }

    @Override
    protected List<Component> statusTooltip() {
        return state().more();
    }

    // ------------------------------------------------------------------ 共通

    /** 右の蓄電のゲージ（タンク・ハロー・検出器）。 */
    private static final int BAR_X = 182, BAR_Y = 20, BAR_W = 6, BAR_H = 84;

    private boolean hasEnergyBar() {
        return kind == Kind.CONTAINMENT_TANK || kind == Kind.HALO_COLLECTOR || kind == Kind.DETECTOR;
    }

    private int[] energy() {
        return switch (kind) {
            case CONTAINMENT_TANK -> new int[]{v(DeviceMenu.Tank.ENERGY), v(DeviceMenu.Tank.ENERGY_MAX)};
            case HALO_COLLECTOR -> new int[]{v(DeviceMenu.Halo.ENERGY), v(DeviceMenu.Halo.ENERGY_MAX)};
            default -> new int[]{v(DeviceMenu.Detector.ENERGY), v(DeviceMenu.Detector.ENERGY_MAX)};
        };
    }

    /** 中身の座標の範囲に重ねたときの説明。 */
    private void hint(int x, int y, int w, int h, Component... lines) {
        area(OX + x, y, w, h, List.of(lines));
    }

    /** アイコンと値（中身の座標）。 */
    private void value(GuiGraphics g, int x, int y, Panel.Icon icon, int iconColor, String text, int color, Component... tooltip) {
        stat(g, OX + x, y, icon, iconColor, text, color, List.of(tooltip));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (renderFrameTooltips(g, mouseX, mouseY) || renderStatTooltips(g, mouseX, mouseY)) {
            return;
        }
        if (hasEnergyBar() && Panel.inside(mouseX, mouseY, cx(BAR_X), cy(BAR_Y), BAR_W, BAR_H)) {
            int[] e = energy();
            g.renderComponentTooltip(font, Panel.energyLines(e[0], e[1]), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        float t = time(partialTick);
        switch (kind) {
            case WORMHOLE_GENERATOR -> drawGenerator(g, t);
            case WORMHOLE_MOUTH -> drawMouth(g, t, mouseX, mouseY);
            case WORMHOLE_PORT -> drawPort(g, t, mouseX, mouseY);
            case CONTAINMENT_TANK -> drawTank(g, t);
            case HALO_COLLECTOR -> drawHalo(g, t);
            case MUON_COLLECTOR -> drawMuon(g, t);
            case DETECTOR -> drawDetector(g, t, mouseX, mouseY);
        }
        if (hasEnergyBar()) {
            int[] e = energy();
            Panel.gauge(g, skin, cx(BAR_X), cy(BAR_Y), BAR_W, BAR_H, e[0] / (double) Math.max(1, e[1]), Panel.GLOW);
        }
    }

    // ------------------------------------------------------------------ 絵の部品

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
        return GuiSkin.mix(a, b, Mth.clamp(k, 0, 1));
    }

    /** 横のゲージ（左から fraction だけ満ちる。色は from から to へ）。 */
    private void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int from, int to) {
        Panel.well(g, skin, x, y, w, h);
        int f = Math.round(w * Mth.clamp(fraction, 0, 1));
        for (int i = 0; i < f; i++) {
            g.fill(x + i, y, x + i + 1, y + h, lerp(from, to, (float) i / Math.max(1, w - 1)));
        }
        Panel.glass(g, x, y, f, h);
    }

    // ------------------------------------------------------------------ ワームホール生成器

    private void drawGenerator(GuiGraphics g, float t) {
        float p = v(DeviceMenu.Gen.PROGRESS) / (float) io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity.GENERATE_TICKS;
        boolean running = p > 0;
        vortex(g, cx(100), cy(46), 20, t, running ? 0.05F + 0.25F * p : 0.02F, running ? 0.5F + 0.5F * p : 0.3F);
        ring(g, cx(100), cy(46), 30, p, lerp(CYAN, 0xFFFFFFFF, p), 0xFF262C48, 40);
        // 受けている電力（必要な量に対して）
        float in = Math.min(1F, v(DeviceMenu.Gen.RECEIVED) / (float) Math.max(1, v(DeviceMenu.Gen.REQUIRED)));
        bar(g, cx(20), cy(76), 160, 4, in, MAGENTA, in >= 1 ? GuiSkin.OK : CYAN);
        hint(19, 74, 162, 8, tr("generator.input", EnergyText.of(v(DeviceMenu.Gen.RECEIVED)), EnergyText.of(v(DeviceMenu.Gen.REQUIRED))));
        int[][] at = DeviceMenu.slotPos(kind);
        for (int i = 0; i < 2; i++) {
            Slot s = menu.slots.get(i);
            Panel.slot(g, skin, leftPos + at[i][0], topPos + at[i][1]);
            g.renderOutline(leftPos + at[i][0] - 2, topPos + at[i][1] - 2, 20, 20, i == 0 ? CYAN : MAGENTA);
            int remain = v(i == 0 ? DeviceMenu.Gen.REMAIN_0 : DeviceMenu.Gen.REMAIN_1);
            if (s.hasItem() && remain > 0) {
                stat(g, at[i][0] - 2, at[i][1] + 20, Panel.Icon.HOURGLASS, skin.dim(), remain / 20 + "s",
                        remain < 400 ? GuiSkin.WARN : skin.text(), List.of(tr("generator.expires", remain / 20)));
            }
        }
    }

    // ------------------------------------------------------------------ ワームホールの口

    private static final int MOUTH_BTN_Y = 22;
    private static final int MOUTH_MINUS_X = 132;
    private static final int MOUTH_PLUS_X = 174;
    private static final int BTN = 16;

    private void drawMouth(GuiGraphics g, float t, int mouseX, int mouseY) {
        int size = v(DeviceMenu.Mouth.SIZE);
        int target = v(DeviceMenu.Mouth.TARGET);
        // 喉の大きさを入れ子の輪で（今の大きさは明るく、目標は暗く）
        for (int s = 1; s <= 3; s++) {
            int r = 10 + s * 9;
            int col = s <= size ? lerp(CYAN, MAGENTA, s / 3F) : (s <= target ? 0xFF4A4F7A : 0xFF1C2038);
            ring(g, cx(56), cy(52), r, 1, col, col, 18 + s * 8);
        }
        vortex(g, cx(56), cy(52), 6 + size * 5, t, 0.04F + 0.03F * size, size > 0 ? 0.9F : 0.3F);
        iconButton(g, cx(MOUTH_MINUS_X), cy(MOUTH_BTN_Y), false, mouseX, mouseY, target > 1);
        iconButton(g, cx(MOUTH_PLUS_X), cy(MOUTH_BTN_Y), true, mouseX, mouseY, target < 3);
        int side = 2 * size + 1;
        int goal = 2 * target + 1;
        // 目標の大きさ（ボタンの間）
        String goalText = goal + "×" + goal;
        g.drawString(font, goalText, cx(MOUTH_MINUS_X + BTN + (MOUTH_PLUS_X - MOUTH_MINUS_X - BTN - font.width(goalText)) / 2),
                cy(MOUTH_BTN_Y + 4), skin.text(), false);
        hint(MOUTH_MINUS_X, MOUTH_BTN_Y, MOUTH_PLUS_X + BTN - MOUTH_MINUS_X, BTN, tr("mouth.size", side, side, goal, goal));
        int s = Math.max(0, Math.min(3, size));
        int ports = v(DeviceMenu.Mouth.PORTS);
        int portLimit = v(DeviceMenu.Mouth.PORT_LIMIT);
        int y = 48;
        value(g, 100, y, Panel.Icon.RANGE, CYAN, side + "×" + side, skin.text(), tr("mouth.size", side, side, goal, goal));
        value(g, 100, y + STAT_PITCH, Panel.Icon.BOLT, Panel.GLOW,
                limit(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.ENERGY_PER_TICK[s]) + " FE/t", skin.text(),
                tr("mouth.bandwidth", limit(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.ENERGY_PER_TICK[s]),
                        limit(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.ITEMS_PER_TICK[s]),
                        limit(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.FLUID_PER_TICK[s])));
        value(g, 100, y + STAT_PITCH * 2, Panel.Icon.INFO, ports > portLimit ? GuiSkin.WARN : skin.dim(),
                Math.min(ports, portLimit) + "/" + portLimit, ports > portLimit ? GuiSkin.WARN : skin.text(),
                tr("mouth.ports", Math.min(ports, portLimit), portLimit, ports));
        if (v(DeviceMenu.Mouth.CROSS) != 0) {
            value(g, 100, y + STAT_PITCH * 3, Panel.Icon.STAR, MAGENTA, "", skin.text(), tr("mouth.cross"));
        }
        // 燃料のスロットと、もつ時間（猶予・燃料切れ）
        int[] f = DeviceMenu.slotPos(kind)[0];
        Panel.slot(g, skin, leftPos + f[0], topPos + f[1]);
        g.renderOutline(leftPos + f[0] - 2, topPos + f[1] - 2, 20, 20, MAGENTA);
        int tpm = v(DeviceMenu.Mouth.TICKS_PER_MATTER);
        stat(g, f[0] + 22, f[1] + 4, Panel.Icon.HOURGLASS, skin.dim(), tpm > 0 ? String.format("%.1fm", tpm / 1200.0) : "-", skin.text(),
                List.of(tpm > 0 ? tr("mouth.upkeep", String.format("%.1f", tpm / 1200.0)) : tr("mouth.upkeep_none")));
        int grace = v(DeviceMenu.Mouth.GRACE);
        if (grace > 0) {
            stat(g, f[0] + 22, f[1] + 4 + STAT_PITCH, Panel.Icon.STAR, GuiSkin.OK, grace / 20 + "s", GuiSkin.OK,
                    List.of(tr("mouth.grace", grace / 20)));
        } else if (v(DeviceMenu.Mouth.STARVE) > 0) {
            int left = (io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.SHRINK_TICKS - v(DeviceMenu.Mouth.STARVE)) / 20;
            stat(g, f[0] + 22, f[1] + 4 + STAT_PITCH, Panel.Icon.HOURGLASS, GuiSkin.WARN, left + "s", GuiSkin.WARN,
                    List.of(tr("mouth.starving", left)));
        }
    }

    /** アイコンのボタン（16×16、−か＋）。 */
    private void iconButton(GuiGraphics g, int bx, int by, boolean plus, int mouseX, int mouseY, boolean enabled) {
        boolean hover = enabled && Panel.inside(mouseX, mouseY, bx, by, BTN, BTN);
        int d = Panel.button(g, skin, bx, by, BTN, BTN, !enabled ? Panel.Press.DISABLED : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
        int c = enabled ? skin.text() : skin.dim();
        if (plus) {
            Panel.plusIcon(g, bx + 6, by + 6 + d, c);
        } else {
            Panel.minusIcon(g, bx + 6, by + 6 + d, c);
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
    private void roundButton(GuiGraphics g, int bx, int by, boolean on, boolean hover) {
        int edge = hover ? 0xFFFFFFFF : on ? CYAN : skin.hi();
        int fill = on ? CYAN : skin.button();
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

    private void drawPort(GuiGraphics g, float t, int mouseX, int mouseY) {
        int state = v(DeviceMenu.Port.STATE);
        vortex(g, cx(40), cy(56), 18, t, state == 2 ? 0.06F : 0.015F, state == 2 ? 0.9F : 0.3F);
        if (state == DeviceMenu.Port.STATE_OVER_LIMIT) {
            // 止まっている印（渦に斜線）
            for (int k = -12; k <= 12; k++) {
                g.fill(cx(40 + k), cy(56 - k), cx(42 + k), cy(58 - k), GuiSkin.BAD);
            }
        }
        if (state == 2) {
            for (int px = 62; px < 180; px++) {
                float k = ((cx(px)) * 0.35F - t * 0.6F) % 4;
                if ((k < 0 ? k + 4 : k) < 2) {
                    g.fill(cx(px), cy(76), cx(px) + 1, cy(77), CYAN);
                }
            }
        }
        if (state != DeviceMenu.Port.STATE_NO_MOUTH) {
            int side = 2 * v(DeviceMenu.Port.SIZE) + 1;
            int rank = v(DeviceMenu.Port.RANK);
            int lim = v(DeviceMenu.Port.LIMIT);
            value(g, 80, 30, Panel.Icon.RANGE, CYAN, side + "×" + side, skin.text(), tr("port.throat", side, side));
            value(g, 80, 30 + STAT_PITCH, Panel.Icon.INFO, rank > lim ? GuiSkin.WARN : skin.dim(), rank + "/" + lim,
                    rank > lim ? GuiSkin.WARN : skin.text(), tr("port.rank", rank, lim));
        }
        if (state == DeviceMenu.Port.STATE_LINKED) {
            Component targets = tr("port.targets", v(DeviceMenu.Port.ENERGY), v(DeviceMenu.Port.ITEMS), v(DeviceMenu.Port.FLUIDS));
            value(g, 130, 30, Panel.Icon.BOLT, Panel.GLOW, String.valueOf(v(DeviceMenu.Port.ENERGY)), skin.text(), targets);
            value(g, 130, 30 + STAT_PITCH, Panel.Icon.BOX, skin.dim(), String.valueOf(v(DeviceMenu.Port.ITEMS)), skin.text(), targets);
            value(g, 130, 30 + STAT_PITCH * 2, Panel.Icon.DROP, CYAN, String.valueOf(v(DeviceMenu.Port.FLUIDS)), skin.text(), targets);
        }
        // 番号の丸と、その右に今の番号
        int channel = v(DeviceMenu.Port.CHANNEL);
        for (int i = 0; i < DeviceMenu.Port.BITS; i++) {
            int bx = cx(bitX(i));
            boolean on = (channel >> bitOf(i) & 1) != 0;
            roundButton(g, bx, cy(BIT_Y), on, Panel.inside(mouseX, mouseY, bx, cy(BIT_Y), BIT_SIZE, BIT_SIZE));
        }
        String number = "#" + channel;
        g.drawString(font, number, cx(bitX(DeviceMenu.Port.BITS)), cy(BIT_Y + 2), skin.text(), false);
        hint(BIT_X, BIT_Y, bitX(DeviceMenu.Port.BITS) + font.width(number) - BIT_X, BIT_SIZE,
                tr("port.channel", channel), tr("port.channel_hint").copy().withStyle(st -> st.withColor(0xFF8A949E)));
    }

    // ------------------------------------------------------------------ 重力閉じ込めタンク

    private void drawTank(GuiGraphics g, float t) {
        int amount = v(DeviceMenu.Tank.AMOUNT);
        int cap = Math.max(1, v(DeviceMenu.Tank.CAPACITY));
        // 大きな縦のタンク: ゆっくり渦を巻くダークマター
        int tx = cx(20);
        int ty = cy(20);
        int tw = 40;
        int th = 84;
        Panel.well(g, skin, tx, ty, tw, th);
        int filled = Math.round(th * Math.min(1F, amount / (float) cap));
        for (int yy = 0; yy < filled; yy++) {
            for (int xx = 0; xx < tw; xx += 2) {
                float n = Mth.sin(xx * 0.3F + (yy + t * 0.5F) * 0.25F) * Mth.cos(yy * 0.2F - t * 0.05F);
                int c = lerp(DARK_MATTER, 0xFF1A0838, 0.5F + 0.5F * n);
                g.fill(tx + xx, ty + th - 1 - yy, tx + xx + 2, ty + th - yy, c);
            }
        }
        Panel.glass(g, tx, ty + th - filled, tw, filled);
        boolean contained = v(DeviceMenu.Tank.CONTAINED) != 0;
        // 場の輪（閉じ込めている間は光る）
        for (int i = 0; i < 4; i++) {
            int ry = ty + 10 + i * 20;
            g.fill(tx - 4, ry, tx + tw + 4, ry + 1, contained ? lerp(Panel.GLOW, 0xFFFFFFFF, 0.5F + 0.5F * Mth.sin(t * 0.2F + i))
                    : 0xFF9A5050);
        }
        hint(18, 18, tw + 4, th + 4, tr("tank.label"), Component.literal(String.format("%,d / %,d mB", amount, cap)));
        int y = statsTop(2);
        value(g, 76, y, Panel.Icon.DROP, DARK_MATTER, EnergyText.shortForm(amount) + " / " + EnergyText.shortForm(cap) + " mB", skin.text(),
                tr("tank.label"), Component.literal(String.format("%,d / %,d mB", amount, cap)));
        value(g, 76, y + STAT_PITCH, Panel.Icon.BOLT, GuiSkin.WARN,
                EnergyText.of(io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity.FE_PER_TICK) + " FE/t", skin.text(),
                tr("tank.upkeep", EnergyText.of(io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity.FE_PER_TICK)));
    }

    // ------------------------------------------------------------------ ハロー捕集器

    private void drawHalo(GuiGraphics g, float t) {
        boolean core = v(DeviceMenu.Halo.CORE) != 0;
        int x0 = cx(40);
        int y0 = cy(56);
        // 炉心へ流れ込む暗黒物質の粒
        for (int i = 0; i < 40; i++) {
            float ang = i * 2.4F;
            float r = 30 - ((t * (core ? 0.6F : 0.1F) + i * 3.7F) % 26);
            int px = x0 + Math.round(Mth.cos(ang) * r);
            int py = y0 + Math.round(Mth.sin(ang) * r);
            g.fill(px, py, px + 1, py + 1, core ? DARK_MATTER : 0xFF8890A0);
        }
        for (int dy = -4; dy <= 4; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, 20 - dy * dy)));
            g.fill(x0 - half, y0 + dy, x0 + half, y0 + dy + 1, core ? 0xFF000000 : 0xFF50565E);
        }
        int amount = v(DeviceMenu.Halo.AMOUNT);
        int buffer = Math.max(1, v(DeviceMenu.Halo.BUFFER));
        int y = statsTop(2);
        value(g, 80, y, Panel.Icon.SPEED, DARK_MATTER, v(DeviceMenu.Halo.RATE) + " mB/s", skin.text(), tr("halo.rate", v(DeviceMenu.Halo.RATE)));
        statBar(g, OX + 80, y + STAT_PITCH, Panel.Icon.DROP, DARK_MATTER, 80, amount / (double) buffer, DARK_MATTER,
                List.of(tr("halo.tank", String.format("%,d", amount), String.format("%,d", buffer)),
                        tr("halo.leak").copy().withStyle(st -> st.withColor(0xFF8A949E))));
    }

    // ------------------------------------------------------------------ 宇宙線ミュオン収集器

    private void drawMuon(GuiGraphics g, float t) {
        // 高さの目盛り（Y=100 で0、Y=200 で最大）と、今の高さの印
        int bx = cx(24);
        int by = cy(20);
        int bh = 84;
        Panel.well(g, skin, bx, by, 8, bh);
        g.fillGradient(bx, by, bx + 8, by + bh, 0xFF78D2F0, skin.fill());
        int yy = v(DeviceMenu.Muon.Y);
        float k = Mth.clamp((yy - 100) / 100F, 0, 1);
        int my = by + bh - Math.round(bh * k);
        g.fill(bx - 4, my, bx + 12, my + 1, Panel.AMBER);
        hint(20, 18, 16, bh + 4, tr("muon.altitude", yy), tr("muon.hint").copy().withStyle(st -> st.withColor(0xFF8A949E)));
        // 降ってくる宇宙線
        boolean sky = v(DeviceMenu.Muon.SKY) != 0;
        if (sky) {
            for (int i = 0; i < 12; i++) {
                int px = cx(60 + (i * 37) % 50);
                int py = cy(18 + (int) ((t * (1.5F + i % 3) + i * 13) % 80));
                g.fill(px, py, px + 1, py + 3, 0x9978D2F0);
            }
        }
        int y = statsTop(sky ? 2 : 1);
        value(g, 44, y, Panel.Icon.OUT, Panel.AMBER, "Y" + yy, skin.text(), tr("muon.altitude", yy),
                tr("muon.hint").copy().withStyle(st -> st.withColor(0xFF8A949E)));
        if (sky) {
            value(g, 44, y + STAT_PITCH, Panel.Icon.SPEED, Panel.GLOW, String.valueOf(v(DeviceMenu.Muon.RATE)), skin.text(),
                    tr("muon.rate", v(DeviceMenu.Muon.RATE)));
        }
        Panel.arrow(g, skin, cx(120), cy(60), v(DeviceMenu.Muon.PROGRESS) / 100.0);
        int[] s = DeviceMenu.slotPos(kind)[0];
        Panel.slot(g, skin, leftPos + s[0], topPos + s[1]);
    }

    // ------------------------------------------------------------------ 重力波検出器

    private static final int OBSERVE_X = 120;
    private static final int OBSERVE_Y = 84;
    private static final int OBSERVE_W = 40;
    private static final int OBSERVE_H = 18;

    private void drawDetector(GuiGraphics g, float t, int mouseX, int mouseY) {
        int x0 = cx(52);
        int y0 = cy(60);
        // 方位の盤（北が上）
        for (int r : new int[]{30, 20}) {
            ring(g, x0, y0, r, 1, skin.seam(), skin.seam(), 48);
        }
        for (int i = 0; i < 8; i++) {
            float ang = -Mth.HALF_PI + i * Mth.TWO_PI / 8;
            int px = x0 + Math.round(Mth.cos(ang) * 34);
            int py = y0 + Math.round(Mth.sin(ang) * 34 * 0.9F);
            g.fill(px - 1, py - 1, px + 1, py + 1, i == 0 ? Panel.RED : skin.lo());
        }
        boolean found = v(DeviceMenu.Detector.OBSERVED) != 0 && v(DeviceMenu.Detector.FOUND) != 0;
        // 観測した向きへの針（脈打つ）
        if (found) {
            float ang = -Mth.HALF_PI + v(DeviceMenu.Detector.DIRECTION) * Mth.TWO_PI / 8;
            float pulse = 0.7F + 0.3F * Mth.sin(t * 0.3F);
            for (int s = 0; s < 28; s++) {
                int px = x0 + Math.round(Mth.cos(ang) * s);
                int py = y0 + Math.round(Mth.sin(ang) * s * 0.9F);
                g.fill(px - 1, py - 1, px + 1, py + 1, lerp(Panel.GLOW, 0xFFFFFFFF, pulse * s / 28F));
            }
            hint(16, 24, 72, 72, direction(), Component.translatable("gui.singulo.detector.band." + v(DeviceMenu.Detector.BAND)));
            value(g, 100, statsTop(1) - 16, Panel.Icon.INFO, Panel.GLOW, String.valueOf(v(DeviceMenu.Detector.BAND)), skin.text(),
                    Component.translatable("gui.singulo.detector.band." + v(DeviceMenu.Detector.BAND)));
        }
        g.fill(x0 - 2, y0 - 2, x0 + 2, y0 + 2, skin.fill());
        // 観測のボタン（電力が足りないと押せない）
        boolean can = v(DeviceMenu.Detector.ENERGY) >= v(DeviceMenu.Detector.COST);
        boolean hover = can && Panel.inside(mouseX, mouseY, cx(OBSERVE_X), cy(OBSERVE_Y), OBSERVE_W, OBSERVE_H);
        int d = Panel.button(g, skin, cx(OBSERVE_X), cy(OBSERVE_Y), OBSERVE_W, OBSERVE_H,
                !can ? Panel.Press.DISABLED : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.icon(g, cx(OBSERVE_X + OBSERVE_W / 2 - 3), cy(OBSERVE_Y + 5) + d, Panel.Icon.RANGE, can ? skin.text() : skin.dim());
        hint(OBSERVE_X, OBSERVE_Y, OBSERVE_W, OBSERVE_H, tr("detector.observe"),
                tr("detector.cost", String.format("%,d", v(DeviceMenu.Detector.COST))).copy().withStyle(st -> st.withColor(0xFF8A949E)));
    }

    // ------------------------------------------------------------------ ボタン

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickFrame(mouseX, mouseY, button)) {
            return true;
        }
        if (minecraft != null && minecraft.gameMode != null) {
            int id = -1;
            if (kind == Kind.WORMHOLE_MOUTH) {
                if (Panel.inside(mouseX, mouseY, cx(MOUTH_MINUS_X), cy(MOUTH_BTN_Y), BTN, BTN)) {
                    id = DeviceMenu.Mouth.BUTTON_SMALLER;
                } else if (Panel.inside(mouseX, mouseY, cx(MOUTH_PLUS_X), cy(MOUTH_BTN_Y), BTN, BTN)) {
                    id = DeviceMenu.Mouth.BUTTON_BIGGER;
                }
            } else if (kind == Kind.WORMHOLE_PORT) {
                for (int i = 0; i < DeviceMenu.Port.BITS; i++) {
                    if (Panel.inside(mouseX, mouseY, cx(bitX(i)), cy(BIT_Y), BIT_SIZE, BIT_SIZE)) {
                        id = DeviceMenu.Port.BUTTON_BIT + bitOf(i);
                    }
                }
            } else if (kind == Kind.DETECTOR
                    && Panel.inside(mouseX, mouseY, cx(OBSERVE_X), cy(OBSERVE_Y), OBSERVE_W, OBSERVE_H)) {
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
