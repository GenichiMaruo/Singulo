package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.machine.CatalystDeviceBlockEntity;
import io.github.genichimaruo.singulo.machine.CatalystDeviceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 触媒装置（アンカー・スタビライザー・量子熱機関）の画面。 */
public class CatalystDeviceScreen extends AbstractContainerScreen<CatalystDeviceMenu> {
    public CatalystDeviceScreen(CatalystDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (Panel.inside(mouseX, mouseY, leftPos + 8, topPos + 17, 8, 52)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.energy",
                    menu.value(CatalystDeviceBlockEntity.D_ENERGY), menu.value(CatalystDeviceBlockEntity.D_CAPACITY)),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Panel.background(g, leftPos, topPos, imageWidth, imageHeight);
        int capacity = Math.max(1, menu.value(CatalystDeviceBlockEntity.D_CAPACITY));
        Panel.verticalBar(g, leftPos + 8, topPos + 17, 8, 52,
                (double) menu.value(CatalystDeviceBlockEntity.D_ENERGY) / capacity, Panel.GLOW);
        Panel.slot(g, leftPos + CatalystDeviceMenu.SLOT_X, topPos + CatalystDeviceMenu.SLOT_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Panel.slot(g, leftPos + 8 + col * 18, topPos + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            Panel.slot(g, leftPos + 8 + col * 18, topPos + 142);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, Panel.TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
        Component label = Component.translatable("gui.singulo.catalyst.slot");
        g.drawString(font, label, CatalystDeviceMenu.SLOT_X - 4 - font.width(label), CatalystDeviceMenu.SLOT_Y + 4,
                Panel.TEXT, false);

        int y = 44;
        for (Component line : lines()) {
            g.drawString(font, line, 22, y, Panel.TEXT, false);
            y += 10;
        }
    }

    private List<Component> lines() {
        List<Component> out = new ArrayList<>();
        CatalystDeviceBlockEntity.Status status =
                CatalystDeviceBlockEntity.Status.values()[menu.value(CatalystDeviceBlockEntity.D_STATUS)];
        switch (status) {
            case NO_CATALYST -> out.add(Component.translatable("gui.singulo.catalyst.none"));
            case UNUSABLE -> out.add(Component.translatable("gui.singulo.catalyst.unusable"));
            case NO_POWER -> out.add(Component.translatable("gui.singulo.status.no_power"));
            case NOT_FORMED -> out.add(Component.translatable("gui.singulo.status.not_formed"));
            case NO_FUEL -> out.add(Component.translatable("gui.singulo.device.no_fuel"));
            case NO_TARGET -> out.add(Component.translatable("gui.singulo.probe.no_target"));
            default -> {
                int value = menu.value(CatalystDeviceBlockEntity.D_VALUE);
                switch (menu.kind()) {
                    case WORLDLINE_ANCHOR -> {
                        out.add(Component.translatable("gui.singulo.device.radius_chunks", value));
                        if (menu.value(CatalystDeviceBlockEntity.D_EXTRA) > 0) {
                            out.add(Component.translatable("gui.singulo.anchor.core_embedded"));
                        }
                    }
                    case SHIELD_TOWER -> {
                        out.add(Component.translatable("gui.singulo.device.radius_blocks", value));
                        out.add(Component.translatable(menu.value(CatalystDeviceBlockEntity.D_EXTRA) >= 5
                                ? "gui.singulo.shield.full" : "gui.singulo.shield.basic"));
                    }
                    case TIPLER_CYLINDER -> {
                        out.add(Component.translatable("gui.singulo.device.radius_blocks", value));
                        out.add(Component.translatable("gui.singulo.device.exotic", menu.value(CatalystDeviceBlockEntity.D_EXTRA)));
                    }
                    case INERTIAL_STABILIZER -> out.add(Component.translatable("gui.singulo.device.radius_blocks", value));
                    case QUANTUM_HEAT_ENGINE -> out.add(Component.translatable("gui.singulo.device.output", value));
                    case DEGENERATE_FURNACE -> {
                        out.add(Component.translatable("gui.singulo.device.output", value));
                        out.add(Component.translatable("gui.singulo.device.fuel", menu.value(CatalystDeviceBlockEntity.D_EXTRA)));
                    }
                    case PROBE_STATION -> {
                        out.add(Component.translatable("gui.singulo.probe.targets", menu.value(CatalystDeviceBlockEntity.D_EXTRA)));
                        out.add(Component.translatable("gui.singulo.probe.return", value));
                    }
                }
                int usage = menu.value(CatalystDeviceBlockEntity.D_USAGE);
                if (usage > 0) {
                    out.add(Component.translatable("gui.singulo.device.usage", usage));
                }
                out.add(Component.translatable("gui.singulo.catalyst.effect",
                        menu.value(CatalystDeviceBlockEntity.D_SPEED_X100) / 100.0,
                        menu.value(CatalystDeviceBlockEntity.D_WEAR_X100) / 100.0));
                if (status == CatalystDeviceBlockEntity.Status.UNDERPOWERED) {
                    out.add(Component.translatable("gui.singulo.catalyst.underpowered"));
                }
            }
        }
        return out;
    }
}
