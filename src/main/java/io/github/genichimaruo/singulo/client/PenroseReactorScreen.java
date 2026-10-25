package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.reactor.PenroseReactorMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * ペンローズ・リアクターの画面（宇宙のスキン）。左に種・燃料・抽出装置、右に副産物、まん中に炉の様子（アイコンと値）。
 * 状態と運転のモードはヘッダーの記号、点火・スピン目標・回収運転はアイコンのボタン（説明は重ねると出る）。
 * 中身は幅200で設計してあり、PenroseReactorMenu.CONTENT_X だけ右へずらして枠の中央に置く。
 */
public class PenroseReactorScreen extends FramedScreen<PenroseReactorMenu> {
    private static final int OX = PenroseReactorMenu.CONTENT_X;
    private static final int STAT_X = 56;
    private static final int BUTTON_Y = 96;
    private static final int BUTTON_H = 16;
    private static final int IGNITE_X = 56, IGNITE_W = 22;
    private static final int SPIN_X = 82, SPIN_W = 48;
    private static final int HARVEST_X = 134, HARVEST_W = 22;
    private static final int INTERVAL_Y = 116;

    /** 入力スロットに入れる物（空のときにグレーの影を描く）。 */
    private static final String[] SLOT_ITEMS = {"singularity_seed", "mass_pellet", "jet_collector", "hawking_collector", "ergosphere_ring"};
    private net.minecraft.client.gui.components.EditBox interval;

    public PenroseReactorScreen(PenroseReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PenroseReactorMenu.WIDTH, PenroseReactorMenu.HEIGHT);
        skin = GuiSkin.COSMIC;
    }

    private int v(int index) {
        return menu.value(index);
    }

    private PenroseReactorBlockEntity.State state() {
        return PenroseReactorBlockEntity.State.values()[v(PenroseReactorBlockEntity.D_STATE)];
    }

    private PenroseReactorBlockEntity.Mode mode() {
        return PenroseReactorBlockEntity.Mode.values()[v(PenroseReactorBlockEntity.D_MODE)];
    }

    private boolean harvestOn() {
        return v(PenroseReactorBlockEntity.D_HARVEST) != 0;
    }

    private double target() {
        return PenroseReactorBlockEntity.SPIN_TARGETS[v(PenroseReactorBlockEntity.D_TARGET)];
    }

    @Override
    protected void init() {
        super.init();
        interval = new net.minecraft.client.gui.components.EditBox(font, leftPos + OX + STAT_X + 12, topPos + INTERVAL_Y, 32, 12,
                Component.translatable("gui.singulo.reactor.interval"));
        interval.setMaxLength(4);
        interval.setFilter(t -> t.isEmpty() || t.chars().allMatch(Character::isDigit));
        interval.setValue(Integer.toString(v(PenroseReactorBlockEntity.D_INTERVAL)));
        interval.setResponder(t -> {
            if (!t.isEmpty() && minecraft != null && minecraft.gameMode != null) {
                int n = Math.min(PenroseReactorBlockEntity.MAX_FEED_INTERVAL, Integer.parseInt(t));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PenroseReactorMenu.BUTTON_INTERVAL + n);
            }
        });
        addRenderableWidget(interval);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // 入力していない間は、装置の値に合わせる
        if (interval != null && !interval.isFocused()) {
            String n = Integer.toString(v(PenroseReactorBlockEntity.D_INTERVAL));
            if (!n.equals(interval.getValue())) {
                interval.setValue(n);
            }
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        // 入力中は E などで画面が閉じないように
        if (interval != null && interval.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            return interval.keyPressed(key, scan, modifiers) || interval.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ------------------------------------------------------------------ ヘッダー（状態・モード）

    @Override
    protected Panel.Status status() {
        return switch (state()) {
            case UNFORMED -> Panel.Status.BROKEN;
            case DORMANT -> Panel.Status.OFF;
            case IGNITING -> Panel.Status.RUN;
            case RUNNING -> mode() == PenroseReactorBlockEntity.Mode.DANGER ? Panel.Status.WARN
                    : mode() == PenroseReactorBlockEntity.Mode.STANDBY ? Panel.Status.IDLE : Panel.Status.RUN;
        };
    }

    @Override
    protected int statusColor() {
        return switch (status()) {
            case BROKEN -> GuiSkin.BAD;
            case WARN -> GuiSkin.WARN;
            case OFF, IDLE -> GuiSkin.IDLE;
            default -> GuiSkin.OK;
        };
    }

    @Override
    protected Component statusText() {
        return switch (state()) {
            case IGNITING -> Component.translatable("gui.singulo.reactor.state.igniting", v(PenroseReactorBlockEntity.D_IGNITION_PCT),
                    v(PenroseReactorBlockEntity.D_IGNITION_SECS));
            case RUNNING -> Component.translatable("gui.singulo.reactor.mode." + mode().name().toLowerCase());
            default -> Component.translatable("gui.singulo.reactor.state." + state().name().toLowerCase());
        };
    }

    @Override
    protected List<Component> statusTooltip() {
        List<Component> lines = new ArrayList<>();
        switch (state()) {
            case DORMANT -> lines.add(ignitionNeed());
            case IGNITING -> {
                // 受電帯域と、この速さで届く量（細いケーブルが混じると足りないことが分かる）
                lines.add(Component.translatable("gui.singulo.reactor.ignition.band",
                        String.format("%,d", v(PenroseReactorBlockEntity.D_IGNITION_BAND_MFE)), String.format("%,d", needPerTickMfe())));
                lines.add(Component.translatable("gui.singulo.reactor.ignition.reach",
                        String.format("%.1f", v(PenroseReactorBlockEntity.D_IGNITION_STORED_GFE10) / 10.0), String.format("%.1f", ignitionGfe())));
            }
            default -> { }
        }
        return lines;
    }

    private Component ignitionNeed() {
        return Component.translatable("gui.singulo.reactor.ignition.need",
                String.format("%.1f", ignitionGfe()), ignitionWindow(), String.format("%,d", needPerTickMfe()));
    }

    // ------------------------------------------------------------------ 描画

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (renderFrameTooltips(g, mouseX, mouseY) || renderStatTooltips(g, mouseX, mouseY)) {
            return;
        }
        if (menu.getCarried().isEmpty()) {
            for (int i = 0; i < PenroseReactorMenu.SLOT_POS.length; i++) {
                int[] p = PenroseReactorMenu.SLOT_POS[i];
                if (!menu.getSlot(i).hasItem() && Panel.inside(mouseX, mouseY, leftPos + p[0], topPos + p[1], 16, 16)) {
                    int k = Math.min(i, PenroseReactorBlockEntity.SLOT_OUT);
                    g.renderTooltip(font, Component.translatable("gui.singulo.reactor.slot." + k), mouseX, mouseY);
                    return;
                }
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        for (int i = 0; i < PenroseReactorMenu.SLOT_POS.length; i++) {
            int[] p = PenroseReactorMenu.SLOT_POS[i];
            Panel.slot(g, skin, leftPos + p[0], topPos + p[1]);
            if (i < SLOT_ITEMS.length && !menu.getSlot(i).hasItem()) {
                Panel.ghost(g, leftPos + p[0], topPos + p[1], SLOT_ITEMS[i]);
            }
        }
        drawStats(g);
        drawButtons(g, mouseX, mouseY);
        // 投入間隔（アイコンと入力欄と単位。説明は重ねると出る）
        stat(g, OX + STAT_X, INTERVAL_Y + 2, Panel.Icon.HOURGLASS, skin.dim(), "", skin.text(),
                List.of(Component.translatable("gui.singulo.reactor.interval"),
                        Component.translatable("gui.singulo.reactor.interval.hint").withStyle(st -> st.withColor(0xFF8A949E))));
        g.drawString(font, "tick", leftPos + OX + STAT_X + 48, topPos + INTERVAL_Y + 2, skin.dim(), false);
    }

    /** 炉の様子（アイコンと値。説明は重ねると出る）。 */
    private void drawStats(GuiGraphics g) {
        int x = OX + STAT_X;
        int y = 24;
        switch (state()) {
            case IGNITING -> {
                int pct = v(PenroseReactorBlockEntity.D_IGNITION_PCT);
                statBar(g, x, y, Panel.Icon.FLAME, 0xFFFF9A4A, 70, pct / 100.0, 0xFFFF9A4A, statusTooltip());
                g.drawString(font, pct + "% " + v(PenroseReactorBlockEntity.D_IGNITION_SECS) + "s", leftPos + x + 11 + 74, topPos + y,
                        skin.text(), false);
                y += STAT_PITCH;
            }
            case RUNNING -> {
                String mass = String.format("%.1f", v(PenroseReactorBlockEntity.D_MASS_X10) / 10.0);
                stat(g, x, y, Panel.Icon.ORB, 0xFFB0A0FF, mass, skin.text(), List.of(Component.translatable("gui.singulo.reactor.mass", mass)));
                y += STAT_PITCH;
                String spin = String.format("%.3f", v(PenroseReactorBlockEntity.D_SPIN_X1000) / 1000.0);
                stat(g, x, y, Panel.Icon.SPEED, 0xFF64DCFF, spin + " → " + target(), skin.text(),
                        List.of(Component.translatable("gui.singulo.reactor.spin", spin, target())));
                y += STAT_PITCH;
                String eta = String.format("%.1f", v(PenroseReactorBlockEntity.D_ETA_X10000) / 100.0);
                stat(g, x, y, Panel.Icon.PERCENT, skin.dim(), eta + "%", skin.text(), List.of(Component.translatable("gui.singulo.reactor.eta", eta)));
                y += STAT_PITCH;
                stat(g, x, y, Panel.Icon.BOLT, Panel.GLOW, EnergyText.of(menu.output()) + " FE/t", skin.text(),
                        List.of(Component.translatable("gui.singulo.reactor.output", EnergyText.of(menu.output()))));
                y += STAT_PITCH;
            }
            default -> {
                stat(g, x, y, Panel.Icon.SPEED, 0xFF64DCFF, "- → " + target(), skin.text(),
                        List.of(Component.translatable("gui.singulo.reactor.spin", "-", target())));
                y += STAT_PITCH;
            }
        }
        String buffer = String.format("%.2f", v(PenroseReactorBlockEntity.D_BUFFER_MFE) / 1000.0);
        stat(g, x, y, Panel.Icon.BOLT, skin.dim(), EnergyText.of(v(PenroseReactorBlockEntity.D_BUFFER_MFE) * 1_000_000L) + " FE", skin.text(),
                List.of(Component.translatable("gui.singulo.reactor.buffer", buffer)));
    }

    /** 点火・スピン目標・回収運転（アイコンのボタン）。 */
    private void drawButtons(GuiGraphics g, int mouseX, int mouseY) {
        int bx = leftPos + OX;
        int by = topPos + BUTTON_Y;
        int d = Panel.button(g, skin, bx + IGNITE_X, by, IGNITE_W, BUTTON_H, hover(mouseX, mouseY, IGNITE_X, IGNITE_W) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.icon(g, bx + IGNITE_X + 8, by + 4 + d, Panel.Icon.FLAME, 0xFFFF9A4A);
        area(OX + IGNITE_X, BUTTON_Y, IGNITE_W, BUTTON_H, List.of(Component.translatable("gui.singulo.reactor.ignite"),
                ignitionNeed().copy().withStyle(st -> st.withColor(0xFF8A949E))));
        d = Panel.button(g, skin, bx + SPIN_X, by, SPIN_W, BUTTON_H, hover(mouseX, mouseY, SPIN_X, SPIN_W) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.icon(g, bx + SPIN_X + 5, by + 4 + d, Panel.Icon.SPEED, 0xFF64DCFF);
        g.drawString(font, String.valueOf(target()), bx + SPIN_X + 15, by + 4 + d, skin.text(), false);
        area(OX + SPIN_X, BUTTON_Y, SPIN_W, BUTTON_H, List.of(Component.translatable("gui.singulo.reactor.spin_target"),
                Component.translatable("gui.singulo.reactor.spin", "-", target()).withStyle(st -> st.withColor(0xFF8A949E))));
        boolean on = harvestOn();
        d = Panel.button(g, skin, bx + HARVEST_X, by, HARVEST_W, BUTTON_H, on ? Panel.Press.ON
                : hover(mouseX, mouseY, HARVEST_X, HARVEST_W) ? Panel.Press.HOVER : Panel.Press.NORMAL);
        Panel.icon(g, bx + HARVEST_X + 8, by + 4 + d, Panel.Icon.BOX, on ? skin.onAccent() : skin.text());
        area(OX + HARVEST_X, BUTTON_Y, HARVEST_W, BUTTON_H, List.of(
                Component.translatable(on ? "gui.singulo.reactor.harvest.on" : "gui.singulo.reactor.harvest.off"),
                Component.translatable("gui.singulo.reactor.harvest.hint").withStyle(st -> st.withColor(0xFF8A949E))));
    }

    private boolean hover(int mouseX, int mouseY, int x, int w) {
        return Panel.inside(mouseX, mouseY, leftPos + OX + x, topPos + BUTTON_Y, w, BUTTON_H);
    }

    /** 点火エネルギー（GFE）と制限時間（秒）。サーバー設定（同期済み）とプリセットから。 */
    private static double ignitionGfe() {
        long base = io.github.genichimaruo.singulo.generated.ServerConfig.SPEC.isLoaded()
                ? io.github.genichimaruo.singulo.generated.ServerConfig.IGNITION_ENERGY.get() : 50_000_000_000L;
        return base * io.github.genichimaruo.singulo.data.Difficulty.ignitionEnergy() / 1e9;
    }

    private static int ignitionWindow() {
        return io.github.genichimaruo.singulo.generated.ServerConfig.SPEC.isLoaded()
                ? io.github.genichimaruo.singulo.generated.ServerConfig.IGNITION_WINDOW_SECONDS.get() : 10;
    }

    /** 点火に要る平均の受電（MFE/t）。 */
    private static long needPerTickMfe() {
        return Math.round(ignitionGfe() * 1000 / (ignitionWindow() * 20.0));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickFrame(mouseX, mouseY, button)) {
            return true;
        }
        if (minecraft != null && minecraft.gameMode != null) {
            int id = hover((int) mouseX, (int) mouseY, IGNITE_X, IGNITE_W) ? PenroseReactorMenu.BUTTON_IGNITE
                    : hover((int) mouseX, (int) mouseY, SPIN_X, SPIN_W) ? PenroseReactorMenu.BUTTON_SPIN
                    : hover((int) mouseX, (int) mouseY, HARVEST_X, HARVEST_W) ? PenroseReactorMenu.BUTTON_HARVEST : -1;
            if (id >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
