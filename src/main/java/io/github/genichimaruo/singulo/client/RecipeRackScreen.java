package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity;
import io.github.genichimaruo.singulo.android.work.RecipeRackMenu;
import io.github.genichimaruo.singulo.android.work.WorkLocationCardItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * アンドロイドレシピ棚の画面。左の列（作業位置カード）は、カードの代わりに記録したブロックの絵で見せる。
 * 左の列と右の8列の間に、行ごとの細い矢印を描く。
 */
public class RecipeRackScreen extends AbstractContainerScreen<RecipeRackMenu> {
    public RecipeRackScreen(RecipeRackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = RecipeRackMenu.WIDTH;
        imageHeight = RecipeRackMenu.HEIGHT;
        inventoryLabelX = RecipeRackMenu.INV_X;
        inventoryLabelY = RecipeRackMenu.INV_Y - 11;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Panel.background(g, x, y, imageWidth, imageHeight);
        for (Slot s : menu.slots) {
            Panel.slot(g, x + s.x, y + s.y);
        }
        for (int row = 0; row < RecipeRackBlockEntity.ROWS; row++) {
            int ry = y + RecipeRackMenu.GRID_Y + row * 18 + 7;
            // 行の矢印（作業位置 → レシピ）
            g.fill(x + RecipeRackMenu.LOC_X + 19, ry, x + RecipeRackMenu.CARD_X - 4, ry + 2, Panel.SEAM);
            g.fill(x + RecipeRackMenu.CARD_X - 5, ry - 1, x + RecipeRackMenu.CARD_X - 3, ry + 3, Panel.SEAM);
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

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        renderLocations(g);
        AndroidStationScreen.fit(g, font, title, 8, 6, imageWidth - 16, Panel.TEXT);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panel.TEXT, false);
    }
}
