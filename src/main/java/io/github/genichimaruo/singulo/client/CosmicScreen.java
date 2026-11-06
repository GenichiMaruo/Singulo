package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import io.github.genichimaruo.singulo.cosmic.CosmicMenu;
import io.github.genichimaruo.singulo.energy.EnergyText;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 宇宙現象を封じ込める設備の画面（宇宙のスキン）。文の代わりに、装置の中で起きていることをそのまま小さく描く:
 * タンクはぶら下がった滴、蓄電庫は押し板の間で形を変える核パスタ、紡糸機はブラックホールからスプールまでの糸。
 * 数は「アイコンと値」で出し、説明は重ねると出す。
 */
public final class CosmicScreen extends FramedScreen<CosmicMenu> {
    private static final int WELL_X = 14;
    private static final int WELL_Y = 24;
    private static final int WELL_W = 84;
    private static final int WELL_H = 78;
    private static final int STAT_X = 108;
    private static final int GRAVITY = 0xFF8FE8FF;
    private static final int THREAD = 0xFFFFEBC0;
    private static final int BAR_X = CosmicMenu.WIDTH - 22;
    private static final int BAR_Y = 24;
    private static final int BAR_H = 78;

    public CosmicScreen(CosmicMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CosmicMenu.WIDTH, CosmicMenu.HEIGHT);
        skin = GuiSkin.COSMIC;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.singulo.cosmic." + key, args);
    }

    private int kind() {
        return menu.value(0);
    }

    private boolean active() {
        return switch (kind()) {
            case CosmicChamberBlockEntity.RESERVOIR -> menu.value(6) > 0;
            case CosmicChamberBlockEntity.ACCUMULATOR -> menu.value(2) > 0;
            default -> menu.value(10) != 0;
        };
    }

    private float time(float partial) {
        return ((minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() % 24000 : 0) + partial) / 20F;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    @Override
    protected Panel.Status status() {
        return switch (menu.value(1)) {
            case 0 -> active() ? Panel.Status.RUN : Panel.Status.IDLE;
            case 2 -> Panel.Status.IDLE;
            default -> Panel.Status.BROKEN;
        };
    }

    @Override
    protected int statusColor() {
        return switch (menu.value(1)) {
            case 0 -> active() ? GuiSkin.OK : GuiSkin.IDLE;
            case 2 -> GuiSkin.IDLE;
            default -> GuiSkin.BAD;
        };
    }

    @Override
    protected Component statusText() {
        return tr("state." + menu.value(1));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        float t = time(partial);
        switch (kind()) {
            case CosmicChamberBlockEntity.RESERVOIR -> drawReservoir(g, t);
            case CosmicChamberBlockEntity.ACCUMULATOR -> drawAccumulator(g, t);
            default -> drawSpindle(g, t);
        }
        drawPorts(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            for (int i = 0; i < 2; i++) {
                if (Panel.inside(mouseX, mouseY, leftPos + portX(i), topPos + portY(), PORT_SIZE, PORT_SIZE)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ 搬入出ポート

    private static final int PORT_SIZE = 14;

    private int portX(int index) {
        int center = kind() == CosmicChamberBlockEntity.RESERVOIR || kind() == CosmicChamberBlockEntity.ACCUMULATOR ? STAT_X + 26 : 164;
        return center + (index == 0 ? -PORT_SIZE - 9 : 9);
    }

    private int portY() {
        return kind() == CosmicChamberBlockEntity.RESERVOIR || kind() == CosmicChamberBlockEntity.ACCUMULATOR ? 82 : CosmicMenu.SLOT_Y + 20;
    }

    /** 制御卓の左右のポートのボタン。押すと 入力 → 出力 → 両方 と切り替わる。 */
    private void drawPorts(GuiGraphics g, int mouseX, int mouseY) {
        int y = topPos + portY();
        int cx = leftPos + (portX(0) + PORT_SIZE + portX(1)) / 2;
        g.fill(cx - 4, y + 3, cx + 4, y + 11, 0xFF3A3650);
        g.fill(cx - 2, y + 5, cx + 2, y + 9, GRAVITY);
        for (int i = 0; i < 2; i++) {
            int x = leftPos + portX(i);
            int mode = menu.value(12 + i);
            boolean hover = Panel.inside(mouseX, mouseY, x, y, PORT_SIZE, PORT_SIZE);
            int d = Panel.button(g, skin, x, y, PORT_SIZE, PORT_SIZE, hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
            Panel.Icon icon = mode == CosmicChamberBlockEntity.PORT_INPUT ? Panel.Icon.IN : mode == CosmicChamberBlockEntity.PORT_OUTPUT ? Panel.Icon.OUT : Panel.Icon.IN_OUT;
            int color = mode == CosmicChamberBlockEntity.PORT_INPUT ? GRAVITY : mode == CosmicChamberBlockEntity.PORT_OUTPUT ? 0xFFF0B450 : 0xFFFFFFFF;
            Panel.icon(g, x + 3, y + 3 + d, icon, color);
            area(portX(i), portY(), PORT_SIZE, PORT_SIZE, List.of(tr("port." + (i == 0 ? "left" : "right"), tr("port.mode." + mode)), tr("port.click")));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
        if (!renderFrameTooltips(g, mouseX, mouseY)) {
            renderStatTooltips(g, mouseX, mouseY);
        }
    }

    private void well(GuiGraphics g, int x, int y, int w, int h) {
        Panel.well(g, skin, leftPos + x, topPos + y, w, h);
        g.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, 0xFF060814);
    }

    private void energyBar(GuiGraphics g, double fraction, List<Component> tooltip) {
        Panel.gauge(g, skin, leftPos + BAR_X, topPos + BAR_Y, 6, BAR_H, fraction, Panel.GLOW);
        area(BAR_X - 1, BAR_Y - 1, 8, BAR_H + 2, tooltip);
    }

    private static String number(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private String fit(Component text, int width) {
        String s = text.getString();
        return font.width(s) <= width ? s : font.plainSubstrByWidth(s, width - font.width("…")) + "…";
    }

    // ------------------------------------------------------------------ 重力懸垂タンク

    private void drawReservoir(GuiGraphics g, float t) {
        int amount = menu.value(6);
        int capacity = Math.max(1, menu.value(7));
        int id = menu.value(11);
        Fluid fluid = amount > 0 && id > 0 ? BuiltInRegistries.FLUID.byId(id - 1) : null;
        Component name = fluid == null ? tr("empty") : new FluidStack(fluid, 1).getHoverName();
        Component amountText = tr("amount", number(amount), number(capacity));

        well(g, WELL_X, WELL_Y, WELL_W + 8, WELL_H);
        int cx = leftPos + WELL_X + (WELL_W + 8) / 2;
        int top = topPos + WELL_Y + 7;
        int floor = topPos + WELL_Y + WELL_H - 4;
        // 冠・支柱・土台
        g.fill(cx - 30, top - 5, cx + 30, top, 0xFF3A3650);
        g.fill(cx - 30, top - 3, cx + 30, top - 2, GRAVITY);
        g.fill(cx - 36, floor, cx + 36, floor + 2, 0xFF3A3650);
        for (int side = -1; side <= 1; side += 2) {
            g.fill(cx + side * 30, top, cx + side * 30 + 1, floor, 0xFF55516F);
        }
        // ぶら下がった滴（ワールドの形と同じ。量の平方根ぎみに伸びて膨らむ）
        float size = (float) Math.pow(amount / (double) capacity, 0.55);
        if (fluid != null) {
            int length = Math.max(4, Math.round(Mth.lerp(size, 8, floor - top - 3)));
            float radius = Mth.lerp(size, 4, 23);
            for (int row = 0; row < length; row++) {
                float s = row / (float) length;
                float neck = 0.5F + 0.5F * smooth(s / 0.7F);
                float tip = s <= 0.7F ? 1 : Mth.sqrt(Math.max(0, 1 - Mth.square((s - 0.7F) / 0.3F)));
                float wave = 0.03F * Mth.sin(s * 11 - t * 1.7F);
                int half = Math.max(1, Math.round(radius * (neck + wave) * tip));
                FluidDraw.fill(g, fluid, cx - half, top + row, half * 2, 1);
            }
        }
        // ゆっくり下りる重力場の輪
        for (int i = 0; i < 3; i++) {
            float k = (t * 0.09F + i / 3F) % 1;
            int y = top + Math.round(k * (floor - top - 4));
            int half = 28;
            int alpha = (int) (110 * Mth.sin(k * Mth.PI));
            g.fill(cx - half, y, cx + half, y + 1, (alpha << 24) | (GRAVITY & 0xFFFFFF));
        }
        area(WELL_X, WELL_Y, WELL_W + 8, WELL_H, List.of(name, amountText));

        int y = WELL_Y + 2;
        stat(g, STAT_X, y, Panel.Icon.DROP, GRAVITY, fit(name, CosmicMenu.WIDTH - STAT_X - 18), skin.text(), List.of(name));
        y += STAT_PITCH + 2;
        statBar(g, STAT_X, y, Panel.Icon.BOX, GRAVITY, 68, amount / (double) capacity, GRAVITY, List.of(amountText));
        y += STAT_PITCH + 2;
        stat(g, STAT_X, y, Panel.Icon.PERCENT, skin.dim(), String.format(Locale.ROOT, "%.1f%%", 100.0 * amount / capacity), skin.text(), List.of(amountText));
    }

    // ------------------------------------------------------------------ 核パスタ積層蓄電庫

    private static int pastaColor(float fill) {
        int base = fill < 0.5F ? GuiSkin.mix(0xFF6A4CFF, 0xFFE85AC0, fill * 2) : GuiSkin.mix(0xFFE85AC0, 0xFFFFE0A0, (fill - 0.5F) * 2);
        return base | 0xFF000000;
    }

    private void drawAccumulator(GuiGraphics g, float t) {
        long stored = menu.value(2) * 1_000_000L;
        long capacity = Math.max(1, menu.value(3)) * 1_000_000L;
        long in = menu.value(4) * 1_000_000L;
        long out = menu.value(5) * 1_000_000L;
        float fill = (float) (stored / (double) capacity);
        float ease = smooth(fill);
        Component storedText = Component.literal(EnergyText.of(stored) + " / " + EnergyText.of(capacity));
        int phase = fill < 0.35F ? 0 : fill < 0.72F ? 1 : 2;

        well(g, WELL_X, WELL_Y, WELL_W + 8, WELL_H);
        int cx = leftPos + WELL_X + (WELL_W + 8) / 2;
        int y0 = topPos + WELL_Y;
        int pressTop = y0 + Math.round(Mth.lerp(ease, 7, 21));
        int pressBottom = y0 + WELL_H - Math.round(Mth.lerp(ease, 7, 21));
        // 導体と押し板
        for (int side = -1; side <= 1; side += 2) {
            g.fill(cx + side * 38 - 2, y0 + 2, cx + side * 38 + 2, y0 + WELL_H - 2, 0xFFB98A4E);
            for (int y = y0 + 4; y < y0 + WELL_H - 2; y += 3) {
                g.fill(cx + side * 38 - 2, y, cx + side * 38 + 2, y + 1, 0xFF6A4830);
            }
            int rod = side * 24;
            g.fill(cx + rod, y0 + 2, cx + rod + 1, pressTop, 0xFF7E7A9E);
            g.fill(cx + rod, pressBottom, cx + rod + 1, y0 + WELL_H - 2, 0xFF7E7A9E);
        }
        g.fill(cx - 34, pressTop - 3, cx + 34, pressTop, 0xFF3A3650);
        g.fill(cx - 34, pressBottom, cx + 34, pressBottom + 3, 0xFF3A3650);
        int color = pastaColor(fill);
        g.fill(cx - 34, pressTop - 1, cx + 34, pressTop, color);
        g.fill(cx - 34, pressBottom, cx + 34, pressBottom + 1, color);
        // 核パスタ: 粒 → 棒 → 板。ワールドと同じ変わり方
        float rod = smooth((fill - 0.22F) / 0.26F);
        float sheet = smooth((fill - 0.58F) / 0.28F);
        final int layers = 5;
        final int cells = 6;
        float room = (pressBottom - pressTop) / (float) layers;
        float pitch = 60F / cells;
        for (int l = 0; l < layers; l++) {
            int y = pressTop + Math.round(room * (l + 0.5F));
            int shade = l % 2 == 0 ? color : GuiSkin.mix(color, 0xFF201030, 0.25F);
            int half = Math.max(1, Math.round(Mth.lerp(sheet, 2, Math.min(2, room * 0.3F))));
            for (int c = 0; c < cells; c++) {
                float x = cx - 30 + pitch * (c + 0.5F);
                float jig = (1 - rod) * 1.5F * Mth.sin(t * 1.2F + l * 3.1F + c * 2.3F);
                int w = Math.round(Mth.lerp(rod, 2, pitch / 2 + 0.5F));
                int dy = Math.round((1 - rod) * 1.5F * Mth.cos(t * 1.0F + l * 1.7F + c));
                g.fill(Math.round(x + jig) - w, y + dy - half, Math.round(x + jig) + w, y + dy + half, shade);
            }
        }
        // 流れの光: 入る電力は導体を下り、出る電力は導体を下りて外へ
        for (int i = 0; i < 3; i++) {
            float k = (t * 0.9F + i / 3F) % 1;
            int y = y0 + 2 + Math.round(k * (WELL_H - 6));
            if (in > 0) {
                g.fill(cx - 40, y, cx - 36, y + 2, 0xFFBFEFFF);
                g.fill(cx + 36, y, cx + 40, y + 2, 0xFFBFEFFF);
            }
            if (out > 0) {
                g.fill(cx - 40, y, cx - 36, y + 2, 0xFFFFD890);
                g.fill(cx + 36, y, cx + 40, y + 2, 0xFFFFD890);
            }
        }
        area(WELL_X, WELL_Y, WELL_W + 8, WELL_H, List.of(tr("phase." + phase), storedText));

        int y = WELL_Y + 2;
        stat(g, STAT_X, y, Panel.Icon.BOLT, Panel.GLOW, EnergyText.of(stored), skin.text(), List.of(storedText));
        y += STAT_PITCH + 2;
        stat(g, STAT_X, y, Panel.Icon.IN, 0xFFBFEFFF, EnergyText.of(in) + "/t", skin.text(), List.of(tr("rate_in", EnergyText.of(in))));
        y += STAT_PITCH + 2;
        stat(g, STAT_X, y, Panel.Icon.OUT, 0xFFFFD890, EnergyText.of(out) + "/t", skin.text(), List.of(tr("rate_out", EnergyText.of(out))));
        energyBar(g, stored / (double) capacity, List.of(storedText));
    }

    // ------------------------------------------------------------------ 潮汐紡糸機

    private void drawSpindle(GuiGraphics g, float t) {
        boolean core = menu.value(10) != 0;
        int duration = menu.value(9);
        float k = duration > 0 ? menu.value(8) / (float) duration : 0;
        boolean working = duration > 0;
        long stored = menu.value(2) * 1_000_000L;
        long capacity = Math.max(1, menu.value(3)) * 1_000_000L;
        int helium = menu.value(6);
        int heliumMax = Math.max(1, menu.value(7));
        Component storedText = Component.literal(EnergyText.of(stored) + " / " + EnergyText.of(capacity));

        int wx = CosmicMenu.WIDTH / 2 - 88;
        int ww = 170;
        int wy = 24;
        int wh = 42;
        well(g, wx, wy, ww, wh);
        int ax = leftPos + wx;
        int ay = topPos + wy + wh / 2;
        int holeX = ax + 22;
        int spoolX = ax + ww - 24;
        // ブラックホールの胴: 影と、まわりを回る円盤
        drum(g, holeX, ay, core, t, working);
        // 絞りリング
        for (int i = 0; i < 2; i++) {
            int x = ax + 66 + i * 28;
            int half = i == 0 ? 11 : 5;
            g.fill(x, ay - half - 3, x + 3, ay - half + 2, 0xFF55516F);
            g.fill(x, ay + half - 1, x + 3, ay + half + 4, 0xFF55516F);
        }
        // 糸: 進んだ分だけ明るい。光の粒が流れる
        if (working) {
            int from = holeX + 15;
            int to = spoolX - 4;
            int thick = ay;
            for (int x = from; x < to; x++) {
                float f = (x - from) / (float) (to - from);
                boolean hot = ((x + (int) (t * 40)) % 12) < 3;
                g.fill(x, thick, x + 1, thick + (f < 0.3F ? 2 : 1), hot ? 0xFFFFFFFF : THREAD);
            }
            // 引き伸ばされている材料
            int half = Math.max(1, Math.round(6 * (1 - 0.8F * k)));
            g.fill(from - 1, ay - half + 1, from + 5, ay + half + 1, 0xFF3A3248);
        }
        spool(g, spoolX, ay, k, t, working);
        area(wx, wy, ww, wh, List.of(core ? tr("core.on") : tr("seed")));

        // 材料 → 進み具合 → 完成品
        int slotY = CosmicMenu.SLOT_Y;
        slotHint(g, CosmicMenu.SEED_X, slotY, 2, "singularity_seed", tr("slot.seed"));
        slotHint(g, CosmicMenu.BILLET_X, slotY, 0, "collapsar_billet", tr("slot.billet"));
        slotHint(g, CosmicMenu.EXOTIC_X, slotY, 1, "exotic_matter", tr("slot.exotic"));
        slotHint(g, CosmicMenu.OUTPUT_X, slotY, 3, "tidal_filament", tr("slot.output"));
        Panel.arrow(g, skin, leftPos + CosmicMenu.EXOTIC_X + 26, topPos + slotY, k);
        statBar(g, wx + 2, slotY + 22, Panel.Icon.DROP, 0xFF7FB8E8, 62, helium / (double) heliumMax, 0xFF7FB8E8,
                List.of(tr("helium", number(helium), number(heliumMax))));
        stat(g, wx + 86, slotY + 22, Panel.Icon.BOLT, Panel.GLOW, EnergyText.of(stored), skin.text(), List.of(storedText));
        energyBar(g, stored / (double) capacity, List.of(storedText));
    }

    private void slotHint(GuiGraphics g, int x, int y, int slot, String ghost, Component hint) {
        Panel.slot(g, skin, leftPos + x, topPos + y);
        if (menu.slots.get(slot).getItem().isEmpty()) {
            g.renderFakeItem(new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(ghost))), leftPos + x, topPos + y);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, 0xB0060814);
            g.pose().popPose();
            area(x, y, 16, 16, List.of(hint));
        }
    }

    private static void drum(GuiGraphics g, int x, int y, boolean core, float t, boolean working) {
        // 胴の輪
        for (int a = 0; a < 40; a++) {
            float ang = a * Mth.TWO_PI / 40;
            int px = x + Math.round(Mth.cos(ang) * 17);
            int py = y + Math.round(Mth.sin(ang) * 17);
            g.fill(px, py, px + 2, py + 2, 0xFF55516F);
        }
        if (!core) {
            g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF3A3650);
            return;
        }
        // 降着円盤
        int disk = working ? 0xFFFFC890 : 0xFF8A6A58;
        for (int r = 7; r <= 13; r += 2) {
            int dots = 10 + r;
            for (int a = 0; a < dots; a++) {
                float ang = a * Mth.TWO_PI / dots + t * (20F / r);
                int px = x + Math.round(Mth.cos(ang) * r);
                int py = y + Math.round(Mth.sin(ang) * r);
                if (a % 3 != 0) {
                    g.fill(px, py, px + 1, py + 1, GuiSkin.mix(disk, 0xFF201030, (r - 7) / 8F));
                }
            }
        }
        // 影
        for (int dy = -5; dy <= 5; dy++) {
            int half = Math.round(Mth.sqrt(26 - dy * dy));
            g.fill(x - half, y + dy, x + half + 1, y + dy + 1, 0xFF000000);
        }
    }

    private static void spool(GuiGraphics g, int x, int y, float k, float t, boolean working) {
        int outer = 16;
        int wound = 5 + Math.round(9 * k);
        for (int dy = -outer; dy <= outer; dy++) {
            int half = Math.round(Mth.sqrt(outer * outer - dy * dy));
            g.fill(x - half, y + dy, x + half + 1, y + dy + 1, 0xFF3A3650);
        }
        for (int dy = -wound; dy <= wound; dy++) {
            int half = Math.round(Mth.sqrt(wound * wound - dy * dy));
            g.fill(x - half, y + dy, x + half + 1, y + dy + 1, working || k > 0 ? 0xFFC89C68 : 0xFF2A2738);
        }
        // 回っているのが分かる筋
        float spin = working ? t * 2.4F : 0;
        for (int i = 0; i < 4; i++) {
            float ang = spin + i * Mth.HALF_PI;
            for (int r = 3; r <= outer - 1; r++) {
                int px = x + Math.round(Mth.cos(ang) * r);
                int py = y + Math.round(Mth.sin(ang) * r);
                g.fill(px, py, px + 1, py + 1, r <= wound ? 0xFFFFEBC0 : 0xFF7E7A9E);
            }
        }
        g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF7E7A9E);
    }
}
