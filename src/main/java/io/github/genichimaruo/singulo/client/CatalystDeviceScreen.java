package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.machine.CatalystDeviceBlockEntity;
import io.github.genichimaruo.singulo.machine.CatalystDeviceMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 触媒装置（アンカー・シールド塔・ティプラーシリンダー・スタビライザー・量子熱機関・縮退熱炉・探査ステーション）の画面。
 * 状態はヘッダーの記号、効果はアイコンと値で出す（説明は重ねると出る）。右上に触媒と、装置によって2つ目のスロット。
 */
public class CatalystDeviceScreen extends FramedScreen<CatalystDeviceMenu> {
    private static final int BAR_H = 44;
    private static final int STAT_X = 22;
    /** 触媒の効果（速度・消費）の列（スロットの下）。 */
    private static final int EFFECT_X = 112;

    public CatalystDeviceScreen(CatalystDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, CatalystDeviceMenu.HEIGHT);
    }

    private int value(int index) {
        return menu.value(index);
    }

    private CatalystDeviceBlockEntity.Status deviceStatus() {
        return CatalystDeviceBlockEntity.Status.values()[value(CatalystDeviceBlockEntity.D_STATUS)];
    }

    private boolean working() {
        var s = deviceStatus();
        return s == CatalystDeviceBlockEntity.Status.RUNNING || s == CatalystDeviceBlockEntity.Status.UNDERPOWERED;
    }

    @Override
    protected Boolean powered() {
        return value(CatalystDeviceBlockEntity.D_ENABLED) != 0;
    }

    @Override
    protected void togglePower() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CatalystDeviceMenu.BUTTON_POWER);
        }
    }

    @Override
    protected Panel.Status status() {
        return switch (deviceStatus()) {
            case RUNNING -> Panel.Status.RUN;
            case NO_POWER -> Panel.Status.NO_POWER;
            case NOT_FORMED -> Panel.Status.BROKEN;
            case OFF -> Panel.Status.OFF;
            case STANDBY -> Panel.Status.IDLE;
            default -> Panel.Status.WARN;
        };
    }

    @Override
    protected int statusColor() {
        return switch (deviceStatus()) {
            case RUNNING -> GuiSkin.OK;
            case NOT_FORMED -> GuiSkin.BAD;
            case OFF, STANDBY -> GuiSkin.IDLE;
            default -> GuiSkin.WARN;
        };
    }

    @Override
    protected Component statusText() {
        return switch (deviceStatus()) {
            case NO_CATALYST -> Component.translatable("gui.singulo.catalyst.none");
            case UNUSABLE -> Component.translatable("gui.singulo.catalyst.unusable");
            case NO_POWER -> Component.translatable("gui.singulo.status.no_power");
            case NOT_FORMED -> Component.translatable("gui.singulo.status.not_formed");
            case NO_FUEL -> Component.translatable("gui.singulo.device.no_fuel");
            case NO_TARGET -> Component.translatable("gui.singulo.probe.no_target");
            case OFF -> Component.translatable("gui.singulo.status.off");
            case STANDBY -> Component.translatable("gui.singulo.device.standby");
            case LIMITED -> Component.translatable("gui.singulo.anchor.limited",
                    io.github.genichimaruo.singulo.machine.WorldlineAnchorBlockEntity.anchorLimit());
            case UNDERPOWERED -> Component.translatable("gui.singulo.catalyst.underpowered");
            case RUNNING -> Component.translatable("gui.singulo.status.running");
        };
    }

    @Override
    protected List<Component> statusTooltip() {
        return deviceStatus() == CatalystDeviceBlockEntity.Status.STANDBY
                ? List.of(Component.translatable("gui.singulo.device.standby.hint").withStyle(s -> s.withColor(0xFF8A949E)))
                : List.of();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (renderFrameTooltips(g, mouseX, mouseY) || renderStatTooltips(g, mouseX, mouseY) || !menu.getCarried().isEmpty()) {
            return;
        }
        if (!menu.getSlot(0).hasItem() && Panel.inside(mouseX, mouseY, leftPos + CatalystDeviceMenu.SLOT_X, topPos + CatalystDeviceMenu.SLOT_Y, 16, 16)) {
            g.renderTooltip(font, Component.translatable("gui.singulo.catalyst.none"), mouseX, mouseY);
        } else if (menu.kind().extraSlot() != null && !menu.getSlot(1).hasItem()
                && Panel.inside(mouseX, mouseY, leftPos + CatalystDeviceMenu.EXTRA_X, topPos + CatalystDeviceMenu.SLOT_Y, 16, 16)) {
            g.renderTooltip(font, Component.translatable(menu.kind().extraSlot()), mouseX, mouseY);
        } else if (Panel.inside(mouseX, mouseY, leftPos + 8, topPos + centeredY(BAR_H + 2) + 1, 8, BAR_H)) {
            g.renderComponentTooltip(font, Panel.energyLines(value(CatalystDeviceBlockEntity.D_ENERGY),
                    value(CatalystDeviceBlockEntity.D_CAPACITY)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int capacity = Math.max(1, value(CatalystDeviceBlockEntity.D_CAPACITY));
        Panel.gauge(g, skin, leftPos + 8, topPos + centeredY(BAR_H + 2) + 1, 8, BAR_H, (double) value(CatalystDeviceBlockEntity.D_ENERGY) / capacity, Panel.GLOW);
        ghostSlot(g, leftPos + CatalystDeviceMenu.SLOT_X, topPos + CatalystDeviceMenu.SLOT_Y, menu.getSlot(0),
                new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("muon_catalyst"))));
        if (menu.kind().extraSlot() != null) {
            Panel.slot(g, skin, leftPos + CatalystDeviceMenu.EXTRA_X, topPos + CatalystDeviceMenu.SLOT_Y);
            if (!menu.getSlot(1).hasItem()) {
                Panel.ghost(g, leftPos + CatalystDeviceMenu.EXTRA_X, topPos + CatalystDeviceMenu.SLOT_Y, extraItem());
            }
        }
        if (working()) {
            drawStats(g);
        }
    }

    /** 動いているときの効果（アイコンと値）。 */
    private void drawStats(GuiGraphics g) {
        int v = value(CatalystDeviceBlockEntity.D_VALUE);
        int extra = value(CatalystDeviceBlockEntity.D_EXTRA);
        rows.clear();
        switch (menu.kind()) {
            case WORLDLINE_ANCHOR -> {
                row(g, Panel.Icon.RANGE, skin.dim(), String.valueOf(v), Component.translatable("gui.singulo.device.radius_chunks", v));
                if (extra > 0) {
                    row(g, Panel.Icon.STAR, 0xFFE0B040, "", Component.translatable("gui.singulo.anchor.core_embedded"));
                }
            }
            case SHIELD_TOWER -> {
                row(g, Panel.Icon.RANGE, skin.dim(), String.valueOf(v), Component.translatable("gui.singulo.device.radius_blocks", v));
                boolean full = extra >= 5;
                row(g, Panel.Icon.STAR, full ? 0xFFE0B040 : skin.dim(), full ? "5" : String.valueOf(extra),
                        Component.translatable(full ? "gui.singulo.shield.full" : "gui.singulo.shield.basic"));
                int members = io.github.genichimaruo.singulo.item.ShieldPermitItem.members(menu.getSlot(1).getItem()).size();
                row(g, Panel.Icon.PERSON, skin.dim(), members > 0 ? String.valueOf(members) : "-", members > 0
                        ? Component.translatable("gui.singulo.shield.permit_on", members) : Component.translatable("gui.singulo.shield.permit_off"));
            }
            case TIPLER_CYLINDER -> {
                row(g, Panel.Icon.RANGE, skin.dim(), String.valueOf(v), Component.translatable("gui.singulo.device.radius_blocks", v));
                row(g, Panel.Icon.INFO, skin.dim(), String.valueOf(extra), Component.translatable("gui.singulo.device.exotic", extra));
            }
            case INERTIAL_STABILIZER -> row(g, Panel.Icon.RANGE, skin.dim(), String.valueOf(v), Component.translatable("gui.singulo.device.radius_blocks", v));
            case QUANTUM_HEAT_ENGINE -> row(g, Panel.Icon.BOLT, Panel.GLOW, EnergyText.of(v) + " FE/t", Component.translatable("gui.singulo.device.output", EnergyText.of(v)));
            case DEGENERATE_FURNACE -> {
                row(g, Panel.Icon.BOLT, Panel.GLOW, EnergyText.of(v) + " FE/t", Component.translatable("gui.singulo.device.output", EnergyText.of(v)));
                row(g, Panel.Icon.INFO, skin.dim(), String.valueOf(extra), Component.translatable("gui.singulo.device.fuel", extra));
            }
            case PROBE_STATION -> {
                row(g, Panel.Icon.RANGE, skin.dim(), String.valueOf(extra), Component.translatable("gui.singulo.probe.targets", extra));
                row(g, Panel.Icon.HOURGLASS, skin.dim(), String.valueOf(v), Component.translatable("gui.singulo.probe.return", v));
            }
        }
        int usage = value(CatalystDeviceBlockEntity.D_USAGE);
        if (usage > 0) {
            row(g, Panel.Icon.BOLT, GuiSkin.WARN, EnergyText.of(usage) + " FE/t",
                    Component.translatable("gui.singulo.device.usage", EnergyText.of(usage)));
        }
        rowY = statsTop(rows.size());
        for (Row r : rows) {
            stat(g, STAT_X, rowY, r.icon(), r.color(), r.value(), skin.text(), List.of(r.tooltip()));
            rowY += STAT_PITCH;
        }
        // 触媒の効果（速度・消費の倍率）はスロットの下に
        double speed = value(CatalystDeviceBlockEntity.D_SPEED_X100) / 100.0;
        double wear = value(CatalystDeviceBlockEntity.D_WEAR_X100) / 100.0;
        Component effect = Component.translatable("gui.singulo.catalyst.effect", speed, wear);
        stat(g, EFFECT_X + 6, 46, Panel.Icon.SPEED, skin.dim(), "×" + trim(speed), skin.text(), List.of(effect));
        stat(g, EFFECT_X + 6, 57, Panel.Icon.HOURGLASS, skin.dim(), "×" + trim(wear), skin.text(), List.of(effect));
    }

    /** 効果の列の次の行の高さ。 */
    private int rowY;

    private record Row(Panel.Icon icon, int color, String value, Component tooltip) {}

    /** 左の列の行（数えてから上下中央に並べる）。 */
    private final List<Row> rows = new java.util.ArrayList<>();

    private void row(GuiGraphics g, Panel.Icon icon, int color, String value, Component tooltip) {
        rows.add(new Row(icon, color, value, tooltip));
    }

    private static String trim(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** 2つ目のスロットに入れる物。 */
    private String extraItem() {
        return switch (menu.kind()) {
            case TIPLER_CYLINDER -> "exotic_matter";
            case DEGENERATE_FURNACE -> "compressed_block_2";
            default -> "shield_permit";
        };
    }
}
