package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity;
import io.github.genichimaruo.singulo.android.work.RecipeRackMenu;
import io.github.genichimaruo.singulo.android.work.WorkLocationCardItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * アンドロイドレシピ棚の画面。左の列（作業位置カード）は、カードの代わりに記録したブロックの絵で見せる。
 * 左の列と右の8列の間に、行ごとの細い矢印を描く。空のスロットには入れるカードのグレーの見本。
 */
public class RecipeRackScreen extends FramedScreen<RecipeRackMenu> {
    public RecipeRackScreen(RecipeRackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, RecipeRackMenu.WIDTH, RecipeRackMenu.HEIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderFrameTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        drawFrame(g, mouseX, mouseY);
        ItemStack location = new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("work_location_card")));
        ItemStack recipe = new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("recipe_card")));
        for (int i = 0; i < RecipeRackBlockEntity.SLOTS; i++) {
            Slot s = menu.getSlot(i);
            ghostSlot(g, x + s.x, y + s.y, s, i % RecipeRackBlockEntity.COLS == 0 ? location : recipe);
        }
        for (int row = 0; row < RecipeRackBlockEntity.ROWS; row++) {
            int ry = y + RecipeRackMenu.GRID_Y + row * 18 + 7;
            // 行の矢印（作業位置 → レシピ）。彫り込み
            for (int pass = 0; pass < 2; pass++) {
                int c = pass == 0 ? skin.arrowHi() : skin.arrow();
                int oy = pass == 0 ? 1 : 0;
                g.fill(x + RecipeRackMenu.LOC_X + 19, ry + oy, x + RecipeRackMenu.CARD_X - 4, ry + 2 + oy, c);
                g.fill(x + RecipeRackMenu.CARD_X - 5, ry - 1 + oy, x + RecipeRackMenu.CARD_X - 3, ry + 3 + oy, c);
            }
        }
    }

    /** 作業位置カードは、記録したブロックの絵（右下に小さな印）で見せる。 */
    private void renderLocations(GuiGraphics g) {
        for (Slot slot : menu.slots) {
        ItemStack stack = slot.getItem();
        if (slot.index < RecipeRackBlockEntity.SLOTS && slot.index % RecipeRackBlockEntity.COLS == 0 && WorkLocationCardItem.isCard(stack)
                && WorkLocationCardItem.pos(stack) != null) {
            ItemStack block = new ItemStack(WorkLocationCardItem.block(stack));
            if (!block.isEmpty()) {
                g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF8B8F98);
                g.renderItem(block, slot.x, slot.y);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(slot.x + 11, slot.y + 11, slot.x + 16, slot.y + 16, 0xFF3A8FB0);
                g.fill(slot.x + 12, slot.y + 12, slot.x + 15, slot.y + 15, 0xFFBFEFFF);
                g.pose().popPose();
                continue;
            }
        }
        }
    }
}
