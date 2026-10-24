package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** アンドロイドレシピ棚の画面。6行 × [作業位置カード1枚 | レシピカード8枚]。 */
public class RecipeRackMenu extends AbstractContainerMenu {
    public static final int WIDTH = 212, HEIGHT = 230;
    /** 左の列（作業位置カード）と、右の8列（レシピカード）の間を少しあける。全体を枠の中央に置く。 */
    public static final int LOC_X = 21, CARD_X = 47, GRID_Y = 22;
    public static final int INV_X = io.github.genichimaruo.singulo.machine.GuiFrame.inventoryX(WIDTH),
            INV_Y = io.github.genichimaruo.singulo.machine.GuiFrame.inventoryY(HEIGHT),
            HOTBAR_Y = io.github.genichimaruo.singulo.machine.GuiFrame.hotbarY(HEIGHT);

    private final BlockPos pos;

    public RecipeRackMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items) {
        super(SinguloMenus.ANDROID_RECIPE_RACK.get(), id);
        this.pos = pos;
        for (int row = 0; row < RecipeRackBlockEntity.ROWS; row++) {
            for (int col = 0; col < RecipeRackBlockEntity.COLS; col++) {
                int x = col == 0 ? LOC_X : CARD_X + (col - 1) * 18;
                boolean loc = col == 0;
                addSlot(new SlotItemHandler(items, row * RecipeRackBlockEntity.COLS + col, x, GRID_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return loc ? WorkLocationCardItem.isCard(stack) : RecipeCardItem.isCard(stack);
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public static RecipeRackMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new RecipeRackMenu(id, inventory, buf.readBlockPos(), new ItemStackHandler(RecipeRackBlockEntity.SLOTS));
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int own = RecipeRackBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, own, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return io.github.genichimaruo.singulo.multiblock.Blueprints.withinMenuReach(player, pos)
                && player.level().getBlockEntity(pos) instanceof RecipeRackBlockEntity rack && rack.mayOperate(player);
    }
}
