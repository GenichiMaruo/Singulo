package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.machine.GeneratorSlotsMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 入口と出口のスロットを持つ発電機の画面（極低温タービン・ミュオン触媒核融合炉）。
 * 左に蓄電のゲージ、まん中に状態（アイコンと値。説明は重ねると出る）、右に入口と出口（あいだに矢印）。
 */
public class GeneratorSlotsScreen extends FramedScreen<GeneratorSlotsMenu> {
    private static final int BLUE = 0xFF5AA9E6, HOT = 0xFFE07040, PURPLE = 0xFF9A6AE0;
    private static final int BAR_H = 44;
    private static final int STAT_X = 22;

    public GeneratorSlotsScreen(GeneratorSlotsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GeneratorSlotsMenu.WIDTH, GeneratorSlotsMenu.HEIGHT);
    }

    /** 蓄電のゲージ（縁を含めて上下中央）。 */
    private int barY() {
        return centeredY(BAR_H + 2) + 1;
    }

    private boolean turbine() {
        return menu.kind() == GeneratorSlotsMenu.KIND_TURBINE;
    }

    @Override
    protected Panel.Status status() {
        if (turbine() ? menu.value(GeneratorSlotsMenu.D_HOT) <= 0 : menu.value(GeneratorSlotsMenu.D_EXTRA) < 0) {
            return Panel.Status.WARN;
        }
        if (menu.value(GeneratorSlotsMenu.D_STANDBY) != 0) {
            return Panel.Status.IDLE;
        }
        return menu.value(GeneratorSlotsMenu.D_RATE_X100) > 0 ? Panel.Status.RUN : Panel.Status.IDLE;
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
        if (status() == Panel.Status.WARN) {
            return Component.translatable(turbine() ? "gui.singulo.generator_slots.no_heat" : "gui.singulo.generator_slots.no_catalyst");
        }
        if (menu.value(GeneratorSlotsMenu.D_STANDBY) != 0) {
            return Component.translatable("gui.singulo.generator_slots.standby");
        }
        return Component.translatable(status() == Panel.Status.RUN ? "gui.singulo.status.running" : "gui.singulo.status.idle");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (renderFrameTooltips(g, mouseX, mouseY) || renderStatTooltips(g, mouseX, mouseY)) {
            return;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + 8, topPos + barY(), 8, BAR_H)) {
            Panel.tooltip(g, font, Panel.energyLines(menu.value(GeneratorSlotsMenu.D_ENERGY), menu.value(GeneratorSlotsMenu.D_CAPACITY)),
                    160, mouseX, mouseY);
        }
        var in = menu.getSlot(0);
        if (!in.hasItem() && menu.getCarried().isEmpty()
                && Panel.inside(mouseX, mouseY, leftPos + in.x, topPos + in.y, 16, 16)) {
            Panel.tooltip(g, font, List.of(Component.translatable(turbine() ? "gui.singulo.generator_slots.turbine.in"
                    : "gui.singulo.generator_slots.fusion.in")), 160, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int capacity = Math.max(1, menu.value(GeneratorSlotsMenu.D_CAPACITY));
        Panel.gauge(g, skin, leftPos + 8, topPos + barY(), 8, BAR_H, (double) menu.value(GeneratorSlotsMenu.D_ENERGY) / capacity, Panel.GLOW);
        var in = menu.getSlot(0);
        ghostSlot(g, leftPos + in.x, topPos + in.y, in,
                new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(turbine() ? "coolant_cartridge" : "muon_catalyst"))));
        var out = menu.getSlot(1);
        Panel.slot(g, skin, leftPos + out.x, topPos + out.y);
        Panel.downArrow(g, skin, leftPos + GeneratorSlotsMenu.IN_X + 4, topPos + GeneratorSlotsMenu.IN_Y + 18);
        drawStats(g);
    }

    /** 状態をアイコンと値で並べる（説明は重ねると出る）。 */
    private void drawStats(GuiGraphics g) {
        int fluid = menu.value(GeneratorSlotsMenu.D_FLUID);
        int cap = Math.max(1, menu.value(GeneratorSlotsMenu.D_FLUID_CAP));
        double rate = menu.value(GeneratorSlotsMenu.D_RATE_X100) / 100.0;
        int y = statsTop(turbine() ? 4 : 3);
        if (turbine()) {
            int hot = menu.value(GeneratorSlotsMenu.D_HOT);
            stat(g, STAT_X, y, Panel.Icon.FLAME, hot <= 0 ? GuiSkin.WARN : HOT, hot <= 0 ? "-" : hot + "K", skin.text(),
                    List.of(hot <= 0 ? Component.translatable("gui.singulo.generator_slots.no_heat")
                            : Component.translatable("gui.singulo.thermo.hot", hot)));
            int efficiency = menu.value(GeneratorSlotsMenu.D_EXTRA);
            stat(g, STAT_X, y + 11, Panel.Icon.PERCENT, skin.dim(), efficiency + "%", skin.text(),
                    List.of(Component.translatable("gui.singulo.generator_slots.efficiency", efficiency)));
            statBar(g, STAT_X, y + 22, Panel.Icon.DROP, BLUE, 60, (double) fluid / cap, BLUE,
                    List.of(Component.translatable("gui.singulo.generator_slots.nitrogen", fluid, cap)));
        } else {
            int left = menu.value(GeneratorSlotsMenu.D_EXTRA);
            stat(g, STAT_X, y, Panel.Icon.HOURGLASS, left < 0 ? GuiSkin.WARN : PURPLE, left < 0 ? "-" : left / 10 + "%", skin.text(),
                    List.of(left < 0 ? Component.translatable("gui.singulo.generator_slots.no_catalyst")
                            : Component.translatable("gui.singulo.generator_slots.catalyst", left / 10)));
            statBar(g, STAT_X, y + 11, Panel.Icon.DROP, BLUE, 60, (double) fluid / cap, BLUE,
                    List.of(Component.translatable("gui.singulo.generator_slots.water", fluid, cap)));
        }
        // タービンは3行の下、核融合炉は2行の下
        stat(g, STAT_X, y + (turbine() ? 33 : 22), Panel.Icon.BOLT, Panel.GLOW, EnergyText.of(rate) + " FE/t", skin.text(),
                List.of(Component.translatable("gui.singulo.energy_rate", EnergyText.of(rate))));
    }
}
