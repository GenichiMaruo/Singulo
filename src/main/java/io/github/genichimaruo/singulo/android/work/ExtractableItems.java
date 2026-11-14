package io.github.genichimaruo.singulo.android.work;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 素材置き場の入れ物を、取り出せる物だけが見えるようにしたもの。取り出せない欄（フードプリンターのカートリッジ、装置の入れる欄など、
 * 窓口が中身を見せても取り出しは断る欄）は空に見える。在庫の数・取りに行く場所は、どれも getStackInSlot で数えるので、
 * 取り出せない物を在庫と思って取りに行き、取れずに止まることがない。
 * <p>
 * 取り出せるかは、その欄から1個取る試し（simulate）で見る。数は欄の数のまま（1回の取り出しの上限で減らさない。大きな欄のある入れ物を少なく数えない）。
 * 入れる・取り出すは、もとの入れ物にそのまま渡す。
 */
final class ExtractableItems implements IItemHandler {
    private final IItemHandler handler;

    private ExtractableItems(IItemHandler handler) {
        this.handler = handler;
    }

    static IItemHandler of(IItemHandler handler) {
        return handler == null || handler instanceof ExtractableItems ? handler : new ExtractableItems(handler);
    }

    /** 包む前の入れ物（包んでいなければそのもの）。 */
    static IItemHandler source(IItemHandler handler) {
        return handler instanceof ExtractableItems e ? e.handler : handler;
    }

    @Override
    public int getSlots() {
        return handler.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        ItemStack stack = handler.getStackInSlot(slot);
        return stack.isEmpty() || handler.extractItem(slot, 1, true).isEmpty() ? ItemStack.EMPTY : stack;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return handler.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return handler.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return handler.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return handler.isItemValid(slot, stack);
    }
}
