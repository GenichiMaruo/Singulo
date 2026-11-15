package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.work.FluidSamples;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/** 流体の見本の、右下の数: 量（mB）を縮めて書く（1000、10K など）。 */
final class FluidSampleDecorator implements IItemDecorator {
    static final FluidSampleDecorator INSTANCE = new FluidSampleDecorator();

    @Override
    public boolean render(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y) {
        long amount = FluidSamples.amount(stack);
        if (amount <= 0) {
            return false;
        }
        String text = WorkConsoleScreen.compact(amount);
        float k = Math.min(0.6F, 17F / font.width(text));
        g.pose().pushMatrix();
        g.pose().translate(x + 17 - font.width(text) * k, y + 17 - font.lineHeight * k);
        g.pose().scale(k, k);
        g.text(font, text, 0, 0, 0xFF9FE4FF, true);
        g.pose().popMatrix();
        return false;
    }
}
