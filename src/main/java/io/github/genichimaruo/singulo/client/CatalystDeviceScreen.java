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
        imageHeight = CatalystDeviceMenu.HEIGHT;
        inventoryLabelY = CatalystDeviceMenu.INV_Y - 11;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (menu.kind().extraSlot() != null && !menu.getSlot(1).hasItem() && menu.getCarried().isEmpty()
                && Panel.inside(mouseX, mouseY, leftPos + CatalystDeviceMenu.EXTRA_X, topPos + CatalystDeviceMenu.SLOT_Y, 16, 16)) {
            g.renderTooltip(font, net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    io.github.genichimaruo.singulo.Singulo.id(extraItem())).getDescription(), mouseX, mouseY);
        }
        if (Panel.inside(mouseX, mouseY, leftPos + POWER_X, topPos + POWER_Y, 10, 10)) {
            g.renderTooltip(font, Component.translatable(powered() ? "gui.singulo.power.on" : "gui.singulo.power.off"),
                    mouseX, mouseY);
        }
        if (Panel.inside(mouseX, mouseY, leftPos + 8, topPos + 17, 8, 52)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.energy",
                    menu.value(CatalystDeviceBlockEntity.D_ENERGY), menu.value(CatalystDeviceBlockEntity.D_CAPACITY)),
                    mouseX, mouseY);
        }
    }

    /** 電源スイッチ（題名の行の右端）。 */
    private static final int POWER_X = 158;
    private static final int POWER_Y = 5;

    private boolean powered() {
        return menu.value(CatalystDeviceBlockEntity.D_ENABLED) != 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Panel.inside(mouseX, mouseY, leftPos + POWER_X, topPos + POWER_Y, 10, 10) && minecraft != null
                && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CatalystDeviceMenu.BUTTON_POWER);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Panel.background(g, leftPos, topPos, imageWidth, imageHeight);
        Panel.powerButton(g, leftPos + POWER_X, topPos + POWER_Y, powered(),
                Panel.inside(mouseX, mouseY, leftPos + POWER_X, topPos + POWER_Y, 10, 10));
        int capacity = Math.max(1, menu.value(CatalystDeviceBlockEntity.D_CAPACITY));
        Panel.verticalBar(g, leftPos + 8, topPos + 17, 8, 52,
                (double) menu.value(CatalystDeviceBlockEntity.D_ENERGY) / capacity, Panel.GLOW);
        Panel.slot(g, leftPos + CatalystDeviceMenu.SLOT_X, topPos + CatalystDeviceMenu.SLOT_Y);
        if (menu.kind().extraSlot() != null) {
            Panel.slot(g, leftPos + CatalystDeviceMenu.EXTRA_X, topPos + CatalystDeviceMenu.SLOT_Y);
            if (!menu.getSlot(1).hasItem()) {
                Panel.ghost(g, leftPos + CatalystDeviceMenu.EXTRA_X, topPos + CatalystDeviceMenu.SLOT_Y, extraItem());
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Panel.slot(g, leftPos + 8 + col * 18, topPos + CatalystDeviceMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            Panel.slot(g, leftPos + 8 + col * 18, topPos + CatalystDeviceMenu.HOTBAR_Y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, Panel.TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
        Component label = Component.translatable("gui.singulo.catalyst.slot");
        g.drawString(font, label, CatalystDeviceMenu.SLOT_X - 4 - font.width(label), CatalystDeviceMenu.SLOT_Y + 4,
                Panel.TEXT, false);
        if (menu.kind().extraSlot() != null) {
            Component extra = Component.translatable(menu.kind().extraSlot());
            g.drawString(font, extra, CatalystDeviceMenu.EXTRA_X - 4 - font.width(extra), CatalystDeviceMenu.SLOT_Y + 4,
                    Panel.TEXT, false);
        }

        int y = 44;
        for (Component line : lines()) {
            for (var seq : font.split(line, imageWidth - 22 - 8)) {
                if (y > CatalystDeviceMenu.INV_Y - 14) {
                    return;
                }
                g.drawString(font, seq, 22, y, Panel.TEXT, false);
                y += 10;
            }
        }
    }

    /** 2つ目のスロットに入れる物。 */
    private String extraItem() {
        return switch (menu.kind()) {
            case TIPLER_CYLINDER -> "exotic_matter";
            case DEGENERATE_FURNACE -> "compressed_block_2";
            default -> "shield_permit";
        };
    }

    /** 許可証の状態（入っていれば登録された人数）。 */
    private Component permitLine() {
        var stack = menu.getSlot(1).getItem();
        int n = io.github.genichimaruo.singulo.item.ShieldPermitItem.members(stack).size();
        return n > 0 ? Component.translatable("gui.singulo.shield.permit_on", n)
                : Component.translatable("gui.singulo.shield.permit_off");
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
            case OFF -> out.add(Component.translatable("gui.singulo.status.off"));
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
                        out.add(permitLine());
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
