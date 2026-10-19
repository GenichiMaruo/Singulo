package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.machine.GeneratorSlotsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 入口と出口のスロットを持つ発電機の画面（極低温タービン・ミュオン触媒核融合炉）。
 * 左に蓄電の目盛り、まん中に状態（熱源・効率・液体・触媒の残り・出力）、右に入口と出口（あいだに矢印）。
 */
public class GeneratorSlotsScreen extends AbstractContainerScreen<GeneratorSlotsMenu> {
    private static final int BLUE = 0xFF3A88C0, HOT = 0xFFC06030, WARN = 0xFFC08020;
    /** 蓄電の目盛りの高さ（インベントリの見出しより上で終わる）。 */
    private static final int BAR_H = GeneratorSlotsMenu.INV_Y - 11 - 17 - 3;

    public GeneratorSlotsScreen(GeneratorSlotsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GeneratorSlotsMenu.WIDTH;
        imageHeight = GeneratorSlotsMenu.HEIGHT;
        inventoryLabelY = GeneratorSlotsMenu.INV_Y - 11;
    }

    private boolean turbine() {
        return menu.kind() == GeneratorSlotsMenu.KIND_TURBINE;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (Panel.inside(mouseX, mouseY, leftPos + 8, topPos + 17, 8, BAR_H)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.energy",
                    menu.value(GeneratorSlotsMenu.D_ENERGY), menu.value(GeneratorSlotsMenu.D_CAPACITY)), mouseX, mouseY);
        }
        var in = menu.getSlot(0);
        if (!in.hasItem() && menu.getCarried().isEmpty()
                && Panel.inside(mouseX, mouseY, leftPos + in.x, topPos + in.y, 16, 16)) {
            g.renderTooltip(font, font.split(Component.translatable(turbine() ? "gui.singulo.generator_slots.turbine.in"
                    : "gui.singulo.generator_slots.fusion.in"), 160), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Panel.background(g, leftPos, topPos, imageWidth, imageHeight);
        int capacity = Math.max(1, menu.value(GeneratorSlotsMenu.D_CAPACITY));
        Panel.verticalBar(g, leftPos + 8, topPos + 17, 8, BAR_H, (double) menu.value(GeneratorSlotsMenu.D_ENERGY) / capacity, Panel.GLOW);
        for (int i = 0; i < 2; i++) {
            var s = menu.getSlot(i);
            Panel.slot(g, leftPos + s.x, topPos + s.y);
        }
        // 入口から出口への矢印（下向き）
        int ax = leftPos + GeneratorSlotsMenu.IN_X + 7, ay = topPos + GeneratorSlotsMenu.IN_Y + 20;
        g.fill(ax, ay, ax + 2, ay + 8, Panel.SEAM);
        g.fill(ax - 2, ay + 6, ax + 4, ay + 8, Panel.SEAM);
        g.fill(ax - 1, ay + 8, ax + 3, ay + 9, Panel.SEAM);
        for (int col = 0; col < 9; col++) {
            for (int row = 0; row < 4; row++) {
                Panel.slot(g, leftPos + 8 + col * 18, topPos + GeneratorSlotsMenu.INV_Y + row * 18 + (row == 3 ? 4 : 0));
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        AndroidStationScreen.fit(g, font, title, titleLabelX, titleLabelY, imageWidth - titleLabelX - 8, Panel.TEXT);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
        int x = 22, y = 18, w = GeneratorSlotsMenu.IN_X - x - 4;
        int fluid = menu.value(GeneratorSlotsMenu.D_FLUID), cap = menu.value(GeneratorSlotsMenu.D_FLUID_CAP);
        double rate = menu.value(GeneratorSlotsMenu.D_RATE_X100) / 100.0;
        if (turbine()) {
            int hot = menu.value(GeneratorSlotsMenu.D_HOT);
            AndroidStationScreen.fit(g, font, hot <= 0 ? Component.translatable("gui.singulo.generator_slots.no_heat")
                    : Component.translatable("gui.singulo.thermo.hot", hot), x, y, w, hot <= 0 ? WARN : HOT);
            AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.generator_slots.efficiency", menu.value(GeneratorSlotsMenu.D_EXTRA)),
                    x, y + 11, w, Panel.TEXT);
            AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.generator_slots.nitrogen", fluid, cap), x, y + 22, w, BLUE);
        } else {
            int left = menu.value(GeneratorSlotsMenu.D_EXTRA);
            AndroidStationScreen.fit(g, font, left < 0 ? Component.translatable("gui.singulo.generator_slots.no_catalyst")
                    : Component.translatable("gui.singulo.generator_slots.catalyst", left / 10), x, y, w, left < 0 ? WARN : 0xFF8A5AD0);
            AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.generator_slots.water", fluid, cap), x, y + 11, w, BLUE);
        }
        AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.energy_rate", String.format("%.1f", rate)), x, y + 33, w, Panel.TEXT);
        if (menu.value(GeneratorSlotsMenu.D_STANDBY) != 0) {
            AndroidStationScreen.fit(g, font, Component.translatable("gui.singulo.generator_slots.standby"), x, y + 44, w, 0xFF8A949E);
        }
    }
}
