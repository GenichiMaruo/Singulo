package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 物（1個の見本。同じアイテム・同じ付加情報で比べる）と数。注文の計算と、在庫の数え上げに使う。
 * alt があれば、見本と同じ物でなくても、alt に合う物（同じ種類の別の物。いろいろな木の板材など）を数える。
 */
public final class ItemCount {
    public final ItemStack item;
    public long count;
    /** 同じ種類として数える物（なければ null: 見本と同じ物だけ）。 */
    @Nullable
    public final Ingredient alt;
    /** 在庫の物: これがある資材口の、取り出しの優先度のいちばん高いもの（優先の登録がなければ 0）。 */
    public int takePriority;

    public ItemCount(ItemStack item, long count) {
        this(item, count, null);
    }

    public ItemCount(ItemStack item, long count, @Nullable Ingredient alt) {
        this.item = item.copyWithCount(1);
        this.count = count;
        this.alt = alt;
    }

    public boolean matches(ItemStack stack) {
        return alt != null ? !stack.isEmpty() && alt.test(stack) : ItemStack.isSameItemSameComponents(item, stack);
    }

    /** 一覧に足す（同じ物があればまとめる。同じ種類の数え方の物とは混ぜない）。 */
    public static void add(List<ItemCount> list, ItemStack stack, long count) {
        if (stack.isEmpty() || count == 0) {
            return;
        }
        for (ItemCount c : list) {
            if (c.alt == null && c.matches(stack)) {
                c.count += count;
                return;
            }
        }
        list.add(new ItemCount(stack, count));
    }

    /** 同じ種類として数える物を、一覧に足す（同じ見本・同じ数え方の物があればまとめる）。 */
    public static void addAlt(List<ItemCount> list, ItemStack stack, long count, Ingredient alt) {
        if (stack.isEmpty() || count == 0) {
            return;
        }
        String key = key(alt);
        for (ItemCount c : list) {
            if (c.alt != null && key.equals(key(c.alt)) && ItemStack.isSameItemSameComponents(c.item, stack)) {
                c.count += count;
                return;
            }
        }
        list.add(new ItemCount(stack, count, alt));
    }

    /** 在庫の一覧に足し、その物の取り出しの優先度を priority 以上にする。 */
    public static void addRanked(List<ItemCount> list, ItemStack stack, long count, int priority) {
        if (stack.isEmpty() || count == 0) {
            return;
        }
        add(list, stack, count);
        for (ItemCount c : list) {
            if (c.alt == null && c.matches(stack)) {
                c.takePriority = Math.max(c.takePriority, priority);
                return;
            }
        }
    }

    /** 寿命の短い順に並べるときの鍵: 触媒は残りの寿命、ほかの物は同じ（並びを変えない）。 */
    public static int lifeOrder(ItemStack stack) {
        return io.github.genichimaruo.singulo.item.UsesItem.isCatalyst(stack)
                ? io.github.genichimaruo.singulo.item.UsesHelper.remaining(stack) : 0;
    }

    /** 同じ数え方か比べるための文字（材料の中身を書いたもの）。 */
    public static String key(Ingredient alt) {
        return Ingredient.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, alt).result().map(Object::toString).orElse("");
    }

    /** 一覧の中の数（なければ 0）。 */
    public static long get(List<ItemCount> list, ItemStack stack) {
        for (ItemCount c : list) {
            if (c.alt == null && c.matches(stack)) {
                return c.count;
            }
        }
        return 0;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.put("item", item.save(registries));
        t.putLong("count", count);
        if (alt != null) {
            t.putString("alt", key(alt));
        }
        return t;
    }

    public static ItemCount load(CompoundTag t, HolderLookup.Provider registries) {
        Ingredient alt = t.contains("alt") ? parse(t.getString("alt")) : null;
        return new ItemCount(ItemStack.parseOptional(registries, t.getCompound("item")), t.getLong("count"), alt);
    }

    /** 文字から材料を読む（読めなければ null）。 */
    @Nullable
    public static Ingredient parse(String json) {
        if (json.isEmpty()) {
            return null;
        }
        try {
            return Ingredient.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json)).result()
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
