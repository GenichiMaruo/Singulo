package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorBlockEntity;
import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ThermoelectricGeneratorScreen extends AbstractContainerScreen<ThermoelectricGeneratorMenu> {
    public ThermoelectricGeneratorScreen(ThermoelectricGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 88;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (Panel.inside(mouseX, mouseY, leftPos + 8, topPos + 17, 8, 62)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.energy",
                    menu.value(ThermoelectricGeneratorBlockEntity.D_ENERGY),
                    menu.value(ThermoelectricGeneratorBlockEntity.D_CAPACITY)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Panel.background(g, leftPos, topPos, imageWidth, imageHeight);
        int energy = menu.value(ThermoelectricGeneratorBlockEntity.D_ENERGY);
        int capacity = Math.max(1, menu.value(ThermoelectricGeneratorBlockEntity.D_CAPACITY));
        Panel.verticalBar(g, leftPos + 8, topPos + 17, 8, 62, (double) energy / capacity, Panel.GLOW);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, Panel.TEXT, false);
        int hot = menu.value(ThermoelectricGeneratorBlockEntity.D_HOT);
        int cold = menu.value(ThermoelectricGeneratorBlockEntity.D_COLD);
        int x = 24;
        int y = 20;
        if (hot <= 0 || cold <= 0) {
            g.drawString(font, Component.translatable("gui.singulo.thermo.none"), x, y, Panel.TEXT, false);
            return;
        }
        g.drawString(font, Component.translatable("gui.singulo.thermo.hot", hot), x, y, 0xFFC06030, false);
        g.drawString(font, Component.translatable("gui.singulo.thermo.cold", cold), x, y + 11, 0xFF3A88C0, false);
        g.drawString(font, Component.translatable("gui.singulo.thermo.delta", Math.max(0, hot - cold)), x, y + 22,
                Panel.TEXT, false);
        double rate = menu.value(ThermoelectricGeneratorBlockEntity.D_RATE_X100) / 100.0;
        g.drawString(font, Component.translatable("gui.singulo.energy_rate", String.format("%.1f", rate)), x, y + 33,
                Panel.TEXT, false);
        int coolant = menu.value(ThermoelectricGeneratorBlockEntity.D_FLUID);
        if (coolant >= 0) {
            g.drawString(font, Component.translatable("gui.singulo.thermo.coolant", coolant), x, y + 46, 0xFF3A88C0, false);
        }
        if (menu.value(ThermoelectricGeneratorBlockEntity.D_MELTING) == 1) {
            g.drawString(font, Component.translatable("gui.singulo.thermo.melting",
                    menu.value(ThermoelectricGeneratorBlockEntity.D_TOLERANCE)), x, y + 46, 0xFFC08020, false);
        }
    }
}
