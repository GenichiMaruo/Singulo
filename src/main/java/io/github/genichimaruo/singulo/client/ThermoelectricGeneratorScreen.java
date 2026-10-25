package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorBlockEntity;
import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 熱電発電機の画面: 蓄電のゲージと、高温側・低温側の温度、出力、冷媒（アイコンと値。説明は重ねると出る）。 */
public class ThermoelectricGeneratorScreen extends FramedScreen<ThermoelectricGeneratorMenu> {
    private static final int BAR_H = 52;
    private static final int STAT_X = 22;

    public ThermoelectricGeneratorScreen(ThermoelectricGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 78);
    }

    @Override
    protected boolean hasInventory() {
        return false;
    }

    /** 蓄電のゲージ（縁を含めて上下中央）。 */
    private int barY() {
        return centeredY(BAR_H + 2) + 1;
    }

    private int value(int index) {
        return menu.value(index);
    }

    private boolean noSource() {
        return value(ThermoelectricGeneratorBlockEntity.D_HOT) <= 0 || value(ThermoelectricGeneratorBlockEntity.D_COLD) <= 0;
    }

    private boolean melting() {
        return value(ThermoelectricGeneratorBlockEntity.D_MELTING) == 1;
    }

    @Override
    protected Panel.Status status() {
        if (noSource() || melting()) {
            return Panel.Status.WARN;
        }
        return value(ThermoelectricGeneratorBlockEntity.D_RATE_X100) > 0 ? Panel.Status.RUN : Panel.Status.IDLE;
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
        if (noSource()) {
            return Component.translatable("gui.singulo.thermo.none");
        }
        if (melting()) {
            return Component.translatable("gui.singulo.thermo.melting", value(ThermoelectricGeneratorBlockEntity.D_TOLERANCE));
        }
        return Component.translatable(status() == Panel.Status.RUN ? "gui.singulo.status.running" : "gui.singulo.status.idle");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (renderFrameTooltips(g, mouseX, mouseY) || renderStatTooltips(g, mouseX, mouseY)) {
            return;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + 8, topPos + barY(), 8, BAR_H)) {
            g.renderComponentTooltip(font, Panel.energyLines(value(ThermoelectricGeneratorBlockEntity.D_ENERGY),
                    value(ThermoelectricGeneratorBlockEntity.D_CAPACITY)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int capacity = Math.max(1, value(ThermoelectricGeneratorBlockEntity.D_CAPACITY));
        Panel.gauge(g, skin, leftPos + 8, topPos + barY(), 8, BAR_H, (double) value(ThermoelectricGeneratorBlockEntity.D_ENERGY) / capacity,
                Panel.GLOW);
        int hot = value(ThermoelectricGeneratorBlockEntity.D_HOT);
        int cold = value(ThermoelectricGeneratorBlockEntity.D_COLD);
        boolean none = noSource();
        int coolant = value(ThermoelectricGeneratorBlockEntity.D_FLUID);
        int y = statsTop(coolant >= 0 ? 4 : 3);
        stat(g, STAT_X, y, Panel.Icon.FLAME, hot <= 0 ? GuiSkin.WARN : 0xFFE07040, hot <= 0 ? "-" : hot + "K", skin.text(),
                List.of(hot <= 0 ? Component.translatable("gui.singulo.thermo.none") : Component.translatable("gui.singulo.thermo.hot", hot)));
        stat(g, STAT_X, y + 11, Panel.Icon.SNOW, cold <= 0 ? GuiSkin.WARN : 0xFF5AA9E6, cold <= 0 ? "-" : cold + "K", skin.text(),
                none ? List.of(Component.translatable("gui.singulo.thermo.none"))
                        : List.of(Component.translatable("gui.singulo.thermo.cold", cold),
                        Component.translatable("gui.singulo.thermo.delta", Math.max(0, hot - cold))));
        double rate = value(ThermoelectricGeneratorBlockEntity.D_RATE_X100) / 100.0;
        stat(g, STAT_X, y + 22, Panel.Icon.BOLT, Panel.GLOW, EnergyText.of(rate) + " FE/t", skin.text(),
                List.of(Component.translatable("gui.singulo.energy_rate", EnergyText.of(rate))));
        if (coolant >= 0) {
            stat(g, STAT_X, y + 33, Panel.Icon.DROP, 0xFF5AA9E6, coolant + " mB", skin.text(),
                    List.of(Component.translatable("gui.singulo.thermo.coolant", coolant)));
        }
    }
}
