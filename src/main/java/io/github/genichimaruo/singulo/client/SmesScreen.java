package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.machine.SmesMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** SMESの画面: 大きな蓄電バーと、1 tick あたりの受け取り・送り出しの量。正面から出し、ほかの5面から受け取る。 */
public class SmesScreen extends AbstractContainerScreen<SmesMenu> {
    public SmesScreen(SmesMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 116;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Panel.background(g, x, y, imageWidth, imageHeight);
        int stored = menu.value(SmesMenu.D_STORED);
        int cap = Math.max(1, menu.value(SmesMenu.D_CAPACITY));
        // 横長の蓄電バー（区切り線つき）
        int bx = x + 10;
        int by = y + 22;
        int bw = imageWidth - 20;
        Panel.well(g, bx, by, bw, 18);
        int filled = (int) Math.round(bw * Math.min(1.0, (double) stored / cap));
        g.fill(bx, by, bx + filled, by + 18, Panel.GLOW);
        g.fill(bx, by, bx + filled, by + 2, 0xFFB8ECFF);
        for (int i = 1; i < 10; i++) {
            g.fill(bx + bw * i / 10, by, bx + bw * i / 10 + 1, by + 18, 0x6039424A);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 7, Panel.TEXT, false);
        int stored = menu.value(SmesMenu.D_STORED);
        int cap = Math.max(1, menu.value(SmesMenu.D_CAPACITY));
        String pct = String.format("%.1f%%", 100.0 * stored / cap);
        g.drawString(font, compact(stored) + " / " + compact(cap) + " FE", 10, 46, Panel.TEXT, false);
        g.drawString(font, pct, imageWidth - 10 - font.width(pct), 46, 0xFF2A8FB8, false);
        g.drawString(font, Component.translatable("gui.singulo.smes.in", compact(menu.value(SmesMenu.D_IN))), 10, 60, 0xFF3C9A5A, false);
        g.drawString(font, Component.translatable("gui.singulo.smes.out", compact(menu.value(SmesMenu.D_OUT))), 10, 72, 0xFFC08020, false);
        int y = 86;
        for (var seq : font.split(Component.translatable("gui.singulo.smes.hint", compact(menu.value(SmesMenu.D_RATE))), imageWidth - 20)) {
            g.drawString(font, seq, 10, y, 0xFF8A949E, false);
            y += 10;
        }
    }

    /** 大きな数を短く（k・M・G）。 */
    private static String compact(long n) {
        if (n >= 1_000_000_000L) {
            return String.format("%.2fG", n / 1e9);
        }
        if (n >= 1_000_000L) {
            return String.format("%.1fM", n / 1e6);
        }
        if (n >= 10_000L) {
            return String.format("%.1fk", n / 1e3);
        }
        return String.valueOf(n);
    }
}
