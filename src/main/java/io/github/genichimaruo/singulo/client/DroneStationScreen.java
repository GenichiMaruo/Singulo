package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.drone.DroneStationBlockEntity;
import io.github.genichimaruo.singulo.drone.DroneStationMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * ドローン・ステーションの画面（アンドロイドのスキンを橙で）。左にメモリ・キット・生産アップグレード・許可証の見本つきスロット、
 * 右に3機ぶんの様子（受け持てない枠は暗く）、右端に蓄電のゲージ。
 */
public class DroneStationScreen extends FramedScreen<DroneStationMenu> {
    /** ドローンの光の色（ステーションの顔・ドローンの灯と同じ橙）。 */
    public static final int ACCENT = 0xF0A040;
    /** 行の右端のボタン（解体・また印刷する）の大きさ。 */
    private static final int ROW_BUTTON = 12;
    /** 解体のボタンを1回押した枠と時刻（3秒以内にもう一度押すと解体する）。 */
    private int armedUnit = -1;
    private long armedAt;

    public DroneStationScreen(DroneStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, DroneStationMenu.WIDTH, DroneStationMenu.HEIGHT);
        skin = GuiSkin.android(ACCENT);
    }

    private static ItemStack ghost(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(id)));
    }

    private int unitStatus(int unit) {
        return menu.value(DroneStationBlockEntity.D_STATUS + unit);
    }

    @Override
    protected Panel.Status status() {
        int best = Panel.Status.IDLE.ordinal();
        for (int i = 0; i < menu.value(DroneStationBlockEntity.D_UNITS); i++) {
            int s = unitStatus(i);
            if (s == DroneStationBlockEntity.S_FLYING || s == DroneStationBlockEntity.S_PRINTING) {
                return Panel.Status.RUN;
            }
            if (s == DroneStationBlockEntity.S_NO_ENERGY) {
                best = Panel.Status.NO_POWER.ordinal();
            } else if (s <= DroneStationBlockEntity.S_NO_KIT && best != Panel.Status.NO_POWER.ordinal()) {
                best = Panel.Status.WARN.ordinal();
            }
        }
        return Panel.Status.values()[best];
    }

    @Override
    protected int statusColor() {
        return switch (status()) {
            case RUN -> GuiSkin.OK;
            case WARN, NO_POWER -> GuiSkin.WARN;
            default -> GuiSkin.IDLE;
        };
    }

    @Override
    protected Component statusText() {
        return Component.translatable("gui.singulo.drone_station.status." + unitStatus(0));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!renderFrameTooltips(g, mouseX, mouseY) && !renderStatTooltips(g, mouseX, mouseY)) {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int x = leftPos;
        int y = topPos;
        String[] ghosts = {"drone_memory", "drone_kit", "android_production_upgrade", "shield_permit"};
        String[] tips = {"gui.singulo.drone_station.memory", "gui.singulo.drone_station.kit", "gui.singulo.drone_station.production",
                "gui.singulo.drone_station.permit"};
        for (int i = 0; i < DroneStationBlockEntity.SLOTS; i++) {
            var s = menu.getSlot(i);
            ghostSlot(g, x + s.x, y + s.y, s, ghost(ghosts[i]));
            if (!s.hasItem()) {
                area(s.x, s.y, 16, 16, List.of(Component.translatable(tips[i])));
            }
        }
        // 3機ぶんの様子: 小さなドローンの印と、状態の短い言葉
        int units = menu.value(DroneStationBlockEntity.D_UNITS);
        for (int i = 0; i < DroneStationBlockEntity.MAX_UNITS; i++) {
            int ry = y + DroneStationMenu.UNIT_Y + i * DroneStationMenu.UNIT_ROW;
            int rx = x + DroneStationMenu.UNIT_X;
            Panel.well(g, skin, rx, ry, DroneStationMenu.UNIT_W, DroneStationMenu.UNIT_ROW - 2);
            int s = unitStatus(i);
            boolean used = i < units || s == DroneStationBlockEntity.S_FLYING || s == DroneStationBlockEntity.S_DISMANTLING;
            int c = !used || s == DroneStationBlockEntity.S_STOPPED ? skin.dim() : s == DroneStationBlockEntity.S_IDLE || s == DroneStationBlockEntity.S_FLYING
                    ? 0xFF000000 | ACCENT : s == DroneStationBlockEntity.S_PRINTING ? GuiSkin.OK : GuiSkin.WARN;
            droneGlyph(g, rx + 3, ry + 3, c);
            Component text = Component.translatable(used ? "gui.singulo.drone_station.status." + s : "gui.singulo.drone_station.status.7");
            AndroidStationScreen.fit(g, font, text, rx + 16, ry + 3, DroneStationMenu.UNIT_W - 19 - ROW_BUTTON, used ? skin.text() : skin.dim());
            int action = action(i);
            if (action != 0) {
                int[] b = rowButton(i);
                boolean hover = Panel.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
                boolean armed = armed(i);
                int d = Panel.button(g, skin, b[0], b[1], b[2], b[3], armed ? Panel.Press.ON : hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
                if (action < 0) {
                    // 解体: ×の印（押した直後は赤く光って、もう一度押すのを待つ）
                    int col = armed ? skin.onAccent() : hover ? GuiSkin.BAD : skin.text();
                    for (int k = 0; k < 6; k++) {
                        g.fill(b[0] + 3 + k, b[1] + 3 + k + d, b[0] + 4 + k, b[1] + 4 + k + d, col);
                        g.fill(b[0] + 8 - k, b[1] + 3 + k + d, b[0] + 9 - k, b[1] + 4 + k + d, col);
                    }
                    area(b[0] - leftPos, b[1] - topPos, b[2], b[3], List.of(Component.translatable(armed
                            ? "gui.singulo.drone_station.dismantle.confirm" : "gui.singulo.drone_station.dismantle")));
                } else {
                    Panel.plusIcon(g, b[0] + 3, b[1] + 3 + d, hover ? skin.accent() : skin.text());
                    area(b[0] - leftPos, b[1] - topPos, b[2], b[3], List.of(Component.translatable("gui.singulo.drone_station.resume")));
                }
            }
            if (!used) {
                area(DroneStationMenu.UNIT_X, DroneStationMenu.UNIT_Y + i * DroneStationMenu.UNIT_ROW, DroneStationMenu.UNIT_W,
                        DroneStationMenu.UNIT_ROW - 2, List.of(Component.translatable("gui.singulo.drone_station.more_units")));
            }
        }
        int cap = Math.max(1, menu.value(DroneStationBlockEntity.D_CAPACITY));
        Panel.gauge(g, skin, x + DroneStationMenu.ENERGY_X, y + DroneStationMenu.ENERGY_Y, 8, DroneStationMenu.ENERGY_H,
                (double) menu.value(DroneStationBlockEntity.D_ENERGY) / cap, Panel.GLOW);
        area(DroneStationMenu.ENERGY_X, DroneStationMenu.ENERGY_Y, 8, DroneStationMenu.ENERGY_H,
                Panel.energyLines(menu.value(DroneStationBlockEntity.D_ENERGY), cap));
    }

    /** 行のボタン: -1 は解体（待機中）、1 はまた印刷する（止めた枠）、0 はなし。 */
    private int action(int unit) {
        int s = unitStatus(unit);
        return s == DroneStationBlockEntity.S_IDLE ? -1 : s == DroneStationBlockEntity.S_STOPPED ? 1 : 0;
    }

    private int[] rowButton(int unit) {
        return new int[]{leftPos + DroneStationMenu.UNIT_X + DroneStationMenu.UNIT_W - ROW_BUTTON - 1,
                topPos + DroneStationMenu.UNIT_Y + unit * DroneStationMenu.UNIT_ROW + 1, ROW_BUTTON, ROW_BUTTON};
    }

    private boolean armed(int unit) {
        return armedUnit == unit && net.minecraft.Util.getMillis() - armedAt < 3000;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickFrame(mouseX, mouseY, button)) {
            return true;
        }
        for (int i = 0; i < DroneStationBlockEntity.MAX_UNITS; i++) {
            int action = action(i);
            int[] b = rowButton(i);
            if (action == 0 || !Panel.inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                continue;
            }
            if (action > 0) {
                press(DroneStationBlockEntity.BUTTON_RESUME + i);
            } else if (armed(i)) {
                press(DroneStationBlockEntity.BUTTON_DISMANTLE + i);
                armedUnit = -1;
            } else {
                armedUnit = i;
                armedAt = net.minecraft.Util.getMillis();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** 小さなドローンの印（8×7）: 4つのローターと胴。 */
    static void droneGlyph(GuiGraphics g, int x, int y, int c) {
        Panel.sprite(g, x, y, new String[]{"xx....xx", "xx....xx", "..xxxx..", "..xxxx..", "xx....xx", "xx....xx", "........"}, c);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawLabels(g);
    }
}
