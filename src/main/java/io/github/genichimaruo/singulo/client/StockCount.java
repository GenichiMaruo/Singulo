package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.energy.EnergyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Right-align the quantity within the item icon, including wide translated fonts. */
public final class StockCount {
    private StockCount() {}

    public static void draw(GuiGraphics graphics, int x, int y, int count) {
        var font = Minecraft.getInstance().font;
        String label = EnergyText.shortForm(count);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 16, y + 16, 200);
        float scale = Math.min(0.75F, 16F / Math.max(1, font.width(label)));
        pose.scale(scale, scale, 1);
        graphics.drawString(font, label, -font.width(label), -font.lineHeight, 0xFFFFFFFF, true);
        pose.popPose();
    }
}
