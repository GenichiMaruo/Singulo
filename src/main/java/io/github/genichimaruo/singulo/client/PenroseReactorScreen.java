package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.reactor.PenroseReactorMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PenroseReactorScreen extends AbstractContainerScreen<PenroseReactorMenu> {
    private static final int TEXT_X = 56;
    private static final int BUTTON_Y = 104;
    private static final int BUTTON_H = 14;
    private static final int IGNITE_X = 56, IGNITE_W = 40;
    private static final int SPIN_X = 98, SPIN_W = 54;
    private static final int HARVEST_X = 154, HARVEST_W = 42;

    public PenroseReactorScreen(PenroseReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PenroseReactorMenu.WIDTH;
        imageHeight = PenroseReactorMenu.HEIGHT;
        inventoryLabelX = PenroseReactorMenu.INV_X;
        inventoryLabelY = PenroseReactorMenu.INV_Y - 11;
    }

    /** 入力スロットに入れる物（空のときにグレーの影を描く）。 */
    private static final String[] SLOT_ITEMS = {"singularity_seed", "mass_pellet", "jet_collector", "hawking_collector", "ergosphere_ring"};
    private static final int INTERVAL_Y = 124;
    private net.minecraft.client.gui.components.EditBox interval;

    @Override
    protected void init() {
        super.init();
        interval = new net.minecraft.client.gui.components.EditBox(font, leftPos + 112, topPos + INTERVAL_Y - 2, 40, 12,
                Component.translatable("gui.singulo.reactor.interval"));
        interval.setMaxLength(4);
        interval.setFilter(t -> t.isEmpty() || t.chars().allMatch(Character::isDigit));
        interval.setValue(Integer.toString(menu.value(PenroseReactorBlockEntity.D_INTERVAL)));
        interval.setResponder(t -> {
            if (!t.isEmpty() && minecraft != null && minecraft.gameMode != null) {
                int v = Math.min(PenroseReactorBlockEntity.MAX_FEED_INTERVAL, Integer.parseInt(t));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PenroseReactorMenu.BUTTON_INTERVAL + v);
            }
        });
        addRenderableWidget(interval);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // 入力していない間は、装置の値に合わせる
        if (interval != null && !interval.isFocused()) {
            String v = Integer.toString(menu.value(PenroseReactorBlockEntity.D_INTERVAL));
            if (!v.equals(interval.getValue())) {
                interval.setValue(v);
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
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
        if (Panel.inside(mouseX, mouseY, leftPos + HARVEST_X, topPos + BUTTON_Y, HARVEST_W, BUTTON_H)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable(harvestOn() ? "gui.singulo.reactor.harvest.on" : "gui.singulo.reactor.harvest.off"),
                    Component.translatable("gui.singulo.reactor.harvest.hint")), mouseX, mouseY);
            return;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + TEXT_X, topPos + INTERVAL_Y - 2, 100, 12)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.reactor.interval.hint"), mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Panel.background(g, leftPos, topPos, imageWidth, imageHeight);
        for (int i = 0; i < PenroseReactorMenu.SLOT_POS.length; i++) {
            int[] p = PenroseReactorMenu.SLOT_POS[i];
            Panel.slot(g, leftPos + p[0], topPos + p[1]);
            if (i < SLOT_ITEMS.length && !menu.getSlot(i).hasItem()) {
                Panel.ghost(g, leftPos + p[0], topPos + p[1], SLOT_ITEMS[i]);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Panel.slot(g, leftPos + PenroseReactorMenu.INV_X + col * 18, topPos + PenroseReactorMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            Panel.slot(g, leftPos + PenroseReactorMenu.INV_X + col * 18, topPos + PenroseReactorMenu.HOTBAR_Y);
        }
        button(g, IGNITE_X, IGNITE_W, false, mouseX, mouseY);
        button(g, SPIN_X, SPIN_W, false, mouseX, mouseY);
        button(g, HARVEST_X, HARVEST_W, harvestOn(), mouseX, mouseY);
    }

    private boolean harvestOn() {
        return menu.value(PenroseReactorBlockEntity.D_HARVEST) != 0;
    }

    /** ボタンの地。lit なら入っている印（回収運転のオン）に、縁を光らせる。 */
    private void button(GuiGraphics g, int x, int w, boolean lit, int mouseX, int mouseY) {
        boolean hover = Panel.inside(mouseX, mouseY, leftPos + x, topPos + BUTTON_Y, w, BUTTON_H);
        if (lit) {
            g.fill(leftPos + x - 1, topPos + BUTTON_Y - 1, leftPos + x + w + 1, topPos + BUTTON_Y + BUTTON_H + 1, Panel.GLOW);
        }
        g.fill(leftPos + x, topPos + BUTTON_Y, leftPos + x + w, topPos + BUTTON_Y + BUTTON_H,
                hover ? Panel.GLOW : Panel.WELL_EDGE);
    }

    private void label(GuiGraphics g, Component text, int x, int w) {
        int width = font.width(text);
        if (width > w - 2) {
            // 狭いボタンでは文字を縮めて収める
            float s = (w - 2) / (float) width;
            g.pose().pushPose();
            g.pose().translate(x + 1, BUTTON_Y + 3 + (8 - 8 * s) / 2, 0);
            g.pose().scale(s, s, 1);
            g.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
            g.pose().popPose();
        } else {
            g.drawString(font, text, x + (w - width) / 2, BUTTON_Y + 3, 0xFFFFFFFF, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, Panel.TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
        int y = 22;
        for (Component line : lines()) {
            g.drawString(font, line, TEXT_X, y, Panel.TEXT, false);
            y += 11;
        }
        label(g, Component.translatable("gui.singulo.reactor.ignite"), IGNITE_X, IGNITE_W);
        label(g, Component.translatable("gui.singulo.reactor.spin_target"), SPIN_X, SPIN_W);
        label(g, Component.translatable("gui.singulo.reactor.harvest"), HARVEST_X, HARVEST_W);
        g.drawString(font, Component.translatable("gui.singulo.reactor.interval"), TEXT_X, INTERVAL_Y, Panel.TEXT, false);
        g.drawString(font, Component.literal("tick"), 156, INTERVAL_Y, Panel.TEXT, false);
    }

    private List<Component> lines() {
        List<Component> out = new ArrayList<>();
        PenroseReactorBlockEntity.State state =
                PenroseReactorBlockEntity.State.values()[menu.value(PenroseReactorBlockEntity.D_STATE)];
        switch (state) {
            case IGNITING -> {
                out.add(Component.translatable("gui.singulo.reactor.state.igniting",
                        menu.value(PenroseReactorBlockEntity.D_IGNITION_PCT), menu.value(PenroseReactorBlockEntity.D_IGNITION_SECS)));
                // 受電帯域と、この速さで届く量（細いケーブルが混じると足りないことが分かる）
                out.add(Component.translatable("gui.singulo.reactor.ignition.band",
                        String.format("%,d", menu.value(PenroseReactorBlockEntity.D_IGNITION_BAND_MFE)), String.format("%,d", needPerTickMfe())));
                out.add(Component.translatable("gui.singulo.reactor.ignition.reach",
                        String.format("%.1f", menu.value(PenroseReactorBlockEntity.D_IGNITION_STORED_GFE10) / 10.0),
                        String.format("%.1f", ignitionGfe())));
            }
            case DORMANT -> {
                out.add(Component.translatable("gui.singulo.reactor.state.dormant"));
                out.add(Component.translatable("gui.singulo.reactor.ignition.need",
                        String.format("%.1f", ignitionGfe()), ignitionWindow(), String.format("%,d", needPerTickMfe())));
            }
            default -> out.add(Component.translatable("gui.singulo.reactor.state." + state.name().toLowerCase()));
        }
        double target = PenroseReactorBlockEntity.SPIN_TARGETS[menu.value(PenroseReactorBlockEntity.D_TARGET)];
        if (state == PenroseReactorBlockEntity.State.RUNNING) {
            PenroseReactorBlockEntity.Mode mode =
                    PenroseReactorBlockEntity.Mode.values()[menu.value(PenroseReactorBlockEntity.D_MODE)];
            out.add(Component.translatable("gui.singulo.reactor.mode." + mode.name().toLowerCase()));
            out.add(Component.translatable("gui.singulo.reactor.mass",
                    String.format("%.1f", menu.value(PenroseReactorBlockEntity.D_MASS_X10) / 10.0)));
            out.add(Component.translatable("gui.singulo.reactor.spin",
                    String.format("%.3f", menu.value(PenroseReactorBlockEntity.D_SPIN_X1000) / 1000.0), target));
            out.add(Component.translatable("gui.singulo.reactor.eta",
                    String.format("%.1f", menu.value(PenroseReactorBlockEntity.D_ETA_X10000) / 100.0)));
            out.add(Component.translatable("gui.singulo.reactor.output", io.github.genichimaruo.singulo.energy.EnergyText.of(menu.output())));
        } else {
            out.add(Component.translatable("gui.singulo.reactor.spin", "-", target));
        }
        out.add(Component.translatable("gui.singulo.reactor.buffer",
                String.format("%.2f", menu.value(PenroseReactorBlockEntity.D_BUFFER_MFE) / 1000.0)));
        return out;
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
        if (minecraft != null && minecraft.gameMode != null) {
            if (Panel.inside(mouseX, mouseY, leftPos + IGNITE_X, topPos + BUTTON_Y, IGNITE_W, BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PenroseReactorMenu.BUTTON_IGNITE);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, leftPos + SPIN_X, topPos + BUTTON_Y, SPIN_W, BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PenroseReactorMenu.BUTTON_SPIN);
                return true;
            }
            if (Panel.inside(mouseX, mouseY, leftPos + HARVEST_X, topPos + BUTTON_Y, HARVEST_W, BUTTON_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PenroseReactorMenu.BUTTON_HARVEST);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
