package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.machine.MachineReport;
import io.github.genichimaruo.singulo.monitor.FactoryMonitor;
import io.github.genichimaruo.singulo.network.MonitorRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 工場モニターの画面（0.21.0。机のモニターを使うと開く）。上に電力（発電・消費・蓄電）、中ほどに止まっている装置の一覧
 * （近い順。名前・理由・方角と距離。ホイールで送る）、下にドローンのまとめ。開いている間、毎秒サーバーに新しいまとめを頼む。
 */
public final class FactoryMonitorScreen extends Screen {
    private static final int W = 248, H = 200, ROW = 18, ROWS = 6;
    private static final int LIST_Y = 62, DRONE_Y = LIST_Y + ROWS * ROW + 6, DRONE_H = 16;
    private final GuiSkin skin = GuiSkin.STANDARD;
    private FactoryMonitor.Snapshot snapshot;
    private int scroll;
    private int ticks;

    private FactoryMonitorScreen(FactoryMonitor.Snapshot snapshot) {
        super(Component.translatable("gui.singulo.monitor.title"));
        this.snapshot = snapshot;
    }

    /** サーバーからまとめが届いた: open なら開き、そうでなければ同じモニターの画面を開いているときだけ新しくする。 */
    public static void receive(FactoryMonitor.Snapshot snapshot, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (open) {
            mc.setScreen(new FactoryMonitorScreen(snapshot));
        } else if (mc.screen instanceof FactoryMonitorScreen screen && screen.snapshot.monitor().equals(snapshot.monitor())) {
            screen.snapshot = snapshot;
            screen.scroll = Math.min(screen.scroll, screen.maxScroll());
        }
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int maxScroll() {
        return Math.max(0, snapshot.stalled().size() - ROWS);
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) {
            ClientPacketDistributor.sendToServer(new MonitorRequestPayload(snapshot.monitor()));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, maxScroll());
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        Panel.frame(g, skin, x, y, W, H);
        Panel.header(g, skin, x, y, W);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        FactoryMonitor.Snapshot s = snapshot;
        g.text(font, title, x + 8, y + 6, skin.text(), false);
        Component range = Component.translatable("gui.singulo.monitor.range", s.range());
        int rangeX = x + W - 8 - font.width(range);
        g.text(font, range, rangeX, y + 6, skin.dim(), false);
        List<Component> tip = null;
        if (Panel.inside(mouseX, mouseY, rangeX, y + 4, font.width(range), 10)) {
            tip = List.of(Component.translatable("gui.singulo.monitor.range.tip", s.racks(), FactoryMonitor.MAX_RACKS, FactoryMonitor.RACK_RANGE,
                    FactoryMonitor.RACK_REACH));
        }

        // 電力: 発電・消費・蓄電
        int bw = (W - 14 - 8) / 3;
        int by = y + 22;
        String[] heads = {"produce", "consume", "stored"};
        for (int i = 0; i < 3; i++) {
            int bx = x + 7 + i * (bw + 4);
            Panel.inset(g, skin, bx, by, bw, 30, skin.fill());
            g.text(font, Component.translatable("gui.singulo.monitor." + heads[i]), bx + 4, by + 4, 0xFFC8D0D8, false);
            String value = switch (i) {
                case 0 -> "+" + EnergyText.of(s.produce()) + " FE/t";
                case 1 -> "-" + EnergyText.of(s.consume()) + " FE/t";
                default -> EnergyText.of(s.stored()) + " FE";
            };
            int color = i == 0 ? GuiSkin.OK : i == 1 ? (s.consume() > s.produce() ? GuiSkin.WARN : 0xFFFFFFFF) : 0xFFFFFFFF;
            fitted(g, value, bx + 4, by + 16, bw - 8, color);
            if (i == 2 && s.capacity() > 0) {
                Panel.gauge(g, skin, bx + 3, by + 26, bw - 6, 2, (double) s.stored() / s.capacity(), GuiSkin.OK);
                if (Panel.inside(mouseX, mouseY, bx, by, bw, 30)) {
                    tip = Panel.energyLines(s.stored(), s.capacity());
                }
            }
        }

        // 止まっている装置
        int ly = y + LIST_Y;
        g.text(font, Component.translatable("gui.singulo.monitor.stalled", s.stalled().size()), x + 8, ly - 10, skin.text(), false);
        Component count = Component.translatable("gui.singulo.monitor.machines", s.machines(), s.running());
        g.text(font, count, x + W - 8 - font.width(count), ly - 10, skin.dim(), false);
        Panel.inset(g, skin, x + 7, ly, W - 14, ROWS * ROW + 2, skin.fill());
        if (s.stalled().isEmpty()) {
            Component none = Component.translatable("gui.singulo.monitor.none");
            g.text(font, none, x + W / 2 - font.width(none) / 2, ly + ROWS * ROW / 2 - 4, GuiSkin.OK, false);
        }
        for (int row = 0; row < ROWS && scroll + row < s.stalled().size(); row++) {
            FactoryMonitor.Stalled e = s.stalled().get(scroll + row);
            int ry = ly + 1 + row * ROW;
            if (row % 2 == 1) {
                g.fill(x + 8, ry, x + W - 8, ry + ROW, 0x18FFFFFF);
            }
            g.item(e.icon(), x + 9, ry + 1);
            Component where = where(s.monitor(), e.pos());
            int whereX = x + W - 10 - font.width(where);
            g.text(font, where, whereX, ry + 5, 0xFFA8B4C0, false);
            int textX = x + 28;
            int nameW = 78;
            String name = clip(e.icon().getHoverName().getString(), nameW);
            g.text(font, name, textX, ry + 5, 0xFFFFFFFF, false);
            int reasonX = textX + nameW + 4;
            String reason = clip(e.reason().getString(), whereX - 4 - reasonX);
            g.text(font, reason, reasonX, ry + 5, color(e.state()), false);
            if (Panel.inside(mouseX, mouseY, x + 8, ry, W - 16, ROW)) {
                tip = List.of(e.icon().getHoverName(), e.reason().copy().withStyle(st -> st.withColor(color(e.state()))),
                        Component.translatable("gui.singulo.monitor.at", e.pos().getX(), e.pos().getY(), e.pos().getZ())
                                .withStyle(st -> st.withColor(0xFF8A949E)));
            }
        }
        if (maxScroll() > 0) {
            int track = ROWS * ROW;
            int knob = Math.max(8, track * ROWS / s.stalled().size());
            int ky = ly + 1 + (track - knob) * scroll / maxScroll();
            g.fill(x + W - 9, ky, x + W - 8, ky + knob, 0xFF9DA8B2);
        }

        // ドローン
        int dy = y + DRONE_Y;
        var totals = s.droneTotals();
        // 1行の記号: ステーションの絵のあとに ⌂ステーション → 運搬 ○ 待機 ⚠ 困っている（説明はツールチップ）
        Panel.inset(g, skin, x + 7, dy, W - 14, DRONE_H, skin.fill());
        g.pose().pushMatrix();
        g.pose().translate(x + 10, dy + 2);
        g.pose().scale(0.75F, 0.75F);
        g.item(new net.minecraft.world.item.ItemStack(io.github.genichimaruo.singulo.registry.SinguloBlocks.DRONE_STATION.get()), 0, 0);
        g.pose().popMatrix();
        int tx = x + 26, ty = dy + (DRONE_H - 8) / 2;
        if (totals.stations() == 0) {
            g.text(font, Component.translatable("gui.singulo.monitor.no_drones"), tx, ty, skin.dim(), false);
        } else {
            String[] marks = {"⌂ " + totals.stations(), "→ " + totals.flying(), "○ " + totals.idle(), "⚠ " + totals.trouble()};
            int[] colors = {skin.text(), totals.flying() > 0 ? GuiSkin.OK : skin.dim(), skin.dim(), totals.trouble() > 0 ? GuiSkin.WARN : skin.dim()};
            for (int i = 0; i < marks.length; i++) {
                g.text(font, marks[i], tx, ty, colors[i], false);
                tx += font.width(marks[i]) + 14;
            }
        }
        if (!s.drones().isEmpty() && Panel.inside(mouseX, mouseY, x + 7, dy, W - 14, DRONE_H)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.singulo.monitor.drones", totals.stations(), totals.flying(), totals.idle(), totals.trouble()));
            for (FactoryMonitor.Drones d : s.drones()) {
                Component line = Component.translatable("gui.singulo.monitor.station", where(s.monitor(), d.pos()), d.flying(), d.idle());
                lines.add(d.problem().getString().isEmpty() ? line
                        : line.copy().append(" ").append(d.problem().copy().withStyle(st -> st.withColor(GuiSkin.WARN))));
            }
            tip = lines;
        }
        if (tip != null) {
            Panel.tooltip(g, font, tip, 220, mouseX, mouseY);
        }
    }

    /** 幅に収まらない文字（Shift で出す桁の多い正確な値）は、縮めて収める。縮めた分だけ下にずらし、行の下端をそろえる。 */
    private void fitted(GuiGraphicsExtractor g, String text, int x, int y, int width, int color) {
        int w = font.width(text);
        if (w <= width) {
            g.text(font, text, x, y, color, false);
            return;
        }
        float k = width / (float) w;
        g.pose().pushMatrix();
        g.pose().translate(x, y + font.lineHeight * (1 - k));
        g.pose().scale(k, k);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** 文字が幅に収まらなければ「…」で切る（全文はツールチップ）。 */
    private String clip(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    private static int color(MachineReport.State state) {
        return switch (state) {
            case BROKEN -> GuiSkin.BAD;
            case RUN -> GuiSkin.OK;
            case IDLE, OFF -> GuiSkin.IDLE;
            default -> GuiSkin.WARN;
        };
    }

    /** モニターから見た場所（距離と8方位、高さの差）。 */
    static Component where(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        Component dir;
        if (dist == 0) {
            dir = Component.translatable("gui.singulo.monitor.dir.here");
        } else {
            // 北は -z、東は +x。8方位に丸める
            double angle = Math.toDegrees(Math.atan2(dx, -dz));
            int octant = Math.floorMod((int) Math.round(angle / 45), 8);
            dir = Component.translatable("gui.singulo.monitor.dir." + new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[octant]);
        }
        Component out = Component.translatable("gui.singulo.monitor.where", dist, dir);
        if (Math.abs(dy) >= 2) {
            out = out.copy().append(Component.translatable(dy > 0 ? "gui.singulo.monitor.up" : "gui.singulo.monitor.down", Math.abs(dy)));
        }
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
