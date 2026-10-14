package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.work.RecipeData;
import io.github.genichimaruo.singulo.android.work.WorkLocationCardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/**
 * 作業位置カードとレシピカードの、持ち物の中での見え方: シフトを押している間、カードの上に中身（記録したブロック・作る物）を重ねて描く。
 */
final class WorkCardDecorator implements IItemDecorator {
    static final WorkCardDecorator LOCATION = new WorkCardDecorator(true);
    static final WorkCardDecorator RECIPE = new WorkCardDecorator(false);

    private final boolean location;

    private WorkCardDecorator(boolean location) {
        this.location = location;
    }

    @Override
    public boolean render(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        if (!Screen.hasShiftDown()) {
            return false;
        }
        ItemStack shown = ItemStack.EMPTY;
        if (location) {
            if (WorkLocationCardItem.pos(stack) != null) {
                shown = new ItemStack(WorkLocationCardItem.block(stack));
            }
        } else if (Minecraft.getInstance().level != null) {
            RecipeData data = RecipeData.read(stack, Minecraft.getInstance().level.registryAccess());
            if (data != null) {
                shown = data.output();
            }
        }
        if (shown.isEmpty()) {
            return false;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 160);
        g.fill(x, y, x + 16, y + 16, 0xC0E9EDF0);           // カードの絵を薄く隠して、中身を見せる
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        g.renderItem(shown, x, y);
        g.pose().popPose();
        return true;
    }
}
