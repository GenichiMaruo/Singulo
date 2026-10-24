package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.energy.EnergyText;
import io.github.genichimaruo.singulo.machine.SmesMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * SMESの画面: 大きな蓄電バーと、1 tick あたりの受け取り・送り出しの量（アイコンと値。説明は重ねると出る）。
 * 正面から出し、ほかの5面から受け取る。
 */
public class SmesScreen extends FramedScreen<SmesMenu> {
    private static final int BAR_X = 8, BAR_H = 16;

    public SmesScreen(SmesMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 78);
    }

    @Override
    protected boolean hasInventory() {
        return false;
    }

    /** 蓄電バーの上端（バーから下の2行までを上下中央に）。 */
    private int barY() {
        return centeredY(BAR_H + 2 + 18 + 8) + 1;
    }

    private List<Component> hint() {
        return List.of(Component.translatable("gui.singulo.smes.hint", EnergyText.of(menu.value(SmesMenu.D_RATE)))
                .withStyle(s -> s.withColor(0xFF8A949E)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (renderFrameTooltips(g, mouseX, mouseY) || renderStatTooltips(g, mouseX, mouseY)) {
            return;
        }
        if (Panel.inside(mouseX, mouseY, leftPos + BAR_X, topPos + barY(), imageWidth - 16, BAR_H)) {
            List<Component> lines = new ArrayList<>(Panel.energyLines(menu.value(SmesMenu.D_STORED), menu.value(SmesMenu.D_CAPACITY)));
            lines.addAll(hint());
            Panel.tooltip(g, font, lines, 200, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        clearStats();
        drawFrame(g, mouseX, mouseY);
        int stored = menu.value(SmesMenu.D_STORED);
        int cap = Math.max(1, menu.value(SmesMenu.D_CAPACITY));
        // 横長の蓄電バー（1割ごとの目盛りつき）
        int bx = leftPos + BAR_X;
        int by = topPos + barY();
        int bw = imageWidth - 16;
        Panel.well(g, skin, bx, by, bw, BAR_H);
        int filled = (int) Math.round(bw * Math.min(1.0, (double) stored / cap));
        if (filled > 0) {
            g.fillGradient(bx, by, bx + filled, by + BAR_H, GuiSkin.mix(Panel.GLOW, 0xFFFFFFFF, 0.35F), GuiSkin.mix(Panel.GLOW, 0xFF000000, 0.2F));
            Panel.glass(g, bx, by, filled, BAR_H);
        }
        for (int i = 1; i < 10; i++) {
            g.fill(bx + bw * i / 10, by + BAR_H - 3, bx + bw * i / 10 + 1, by + BAR_H, 0x73FFFFFF);
        }
        String amount = EnergyText.of(stored) + " / " + EnergyText.of(cap) + " FE";
        g.drawString(font, amount, bx, by + BAR_H + 5, skin.text(), false);
        String pct = String.format("%.1f%%", 100.0 * stored / cap);
        g.drawString(font, pct, bx + bw - font.width(pct), by + BAR_H + 5, 0xFF2A8FB8, false);
        int y = by + BAR_H + 18 - topPos;
        stat(g, BAR_X, y, Panel.Icon.IN, 0xFF3C9A5A, EnergyText.of(menu.value(SmesMenu.D_IN)) + " FE/t", skin.text(),
                List.of(Component.translatable("gui.singulo.smes.in", EnergyText.of(menu.value(SmesMenu.D_IN)))));
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("gui.singulo.smes.out", EnergyText.of(menu.value(SmesMenu.D_OUT))));
        out.addAll(hint());
        stat(g, imageWidth / 2, y, Panel.Icon.OUT, 0xFFC08020, EnergyText.of(menu.value(SmesMenu.D_OUT)) + " FE/t", skin.text(), out);
    }
}
