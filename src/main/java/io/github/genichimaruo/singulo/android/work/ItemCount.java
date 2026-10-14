package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** 物（1個の見本。同じアイテム・同じ付加情報で比べる）と数。注文の計算と、在庫の数え上げに使う。 */
public final class ItemCount {
    public final ItemStack item;
    public long count;

    public ItemCount(ItemStack item, long count) {
        this.item = item.copyWithCount(1);
        this.count = count;
    }

    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(item, stack);
    }

    /** 一覧に足す（同じ物があればまとめる）。 */
    public static void add(List<ItemCount> list, ItemStack stack, long count) {
        if (stack.isEmpty() || count == 0) {
            return;
        }
        for (ItemCount c : list) {
            if (c.matches(stack)) {
                c.count += count;
                return;
            }
        }
        list.add(new ItemCount(stack, count));
    }

    /** 一覧の中の数（なければ 0）。 */
    public static long get(List<ItemCount> list, ItemStack stack) {
        for (ItemCount c : list) {
            if (c.matches(stack)) {
                return c.count;
            }
        }
        return 0;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.put("item", item.save(registries));
        t.putLong("count", count);
        return t;
    }

    public static ItemCount load(CompoundTag t, HolderLookup.Provider registries) {
        return new ItemCount(ItemStack.parseOptional(registries, t.getCompound("item")), t.getLong("count"));
    }
}
