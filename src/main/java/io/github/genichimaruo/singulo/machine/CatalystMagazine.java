package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.item.CreativeCatalystItem;
import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * 触媒の装填。触媒スロットは予備の置き場（同じ触媒を {@link #SUPPLY_LIMIT} 個まで積める）で、装置は1個を中に装填して使う。
 * <ul>
 *   <li>使い切ると失活触媒を出口へ出し、予備から次の1個をすぐ装填する（止まらない）</li>
 *   <li>使いかけの触媒を予備に入れると、装填中のものと同じ種類なら残りの使用回数を装填中のものへ移し、
 *       空になった殻（失活触媒）を出口へ出す。装填中のものが満タンの間は、減って空きができてから移す</li>
 *   <li>装填中のものと違う種類の触媒を予備に入れると、装填中のもの（使いかけ）を出口へ返して入れ替える</li>
 * </ul>
 * 出口（機械なら出力スロット、触媒装置なら失活触媒のスロット）がふさがっている間、返す物はここで待つ。
 */
public final class CatalystMagazine {
    /** 予備として積める数。 */
    public static final int SUPPLY_LIMIT = io.github.genichimaruo.singulo.item.UsesItem.CATALYST_STACK;

    private ItemStack loaded = ItemStack.EMPTY;
    /** 出口へ返す物（失活触媒・入れ替えで外した使いかけ）。出口が空くまで待つ。 */
    private final List<ItemStack> returns = new ArrayList<>();
    private final Runnable changed;

    public CatalystMagazine(Runnable changed) {
        this.changed = changed;
    }

    /** 装填中の触媒（なければ空）。 */
    public ItemStack loaded() {
        return loaded;
    }

    /** 装置が使う触媒: 装填中のもの。まだ装填していなければ予備のいちばん上（何が使えるかを見るため）。 */
    public ItemStack current(IItemHandlerModifiable supply, int slot) {
        if (!loaded.isEmpty()) {
            return loaded;
        }
        ItemStack s = supply.getStackInSlot(slot);
        return s.isEmpty() ? s : s.copyWithCount(1);       // 予備は何個積んであっても、使うのは1個
    }

    /**
     * レシピの照合に使う触媒: 装填中のものの残りに、予備の同じ触媒の残りを足した1個として見せる
     * （1回に装填中のものの残りより多く使うレシピでも、使い切ったあとは次の触媒から続けて使える）。
     */
    public ItemStack pooled(IItemHandlerModifiable supply, int slot) {
        ItemStack first = current(supply, slot);
        if (loaded.isEmpty() || first.getItem() instanceof CreativeCatalystItem) {
            return first;
        }
        ItemStack s = supply.getStackInSlot(slot);
        long total = UsesHelper.remaining(loaded);
        if (!s.isEmpty() && sameKind(loaded, s)) {
            total += (long) UsesHelper.remaining(s) * s.getCount();
        }
        ItemStack view = loaded.copy();
        int all = (int) Math.min(Integer.MAX_VALUE, total);
        view.set(SinguloComponents.USES.get(), new UsesData(0, all, UsesHelper.get(loaded).repairs()));
        return view;
    }

    /** 装填中の触媒の残りの割合（0〜1。なければ 0）。 */
    public float fill() {
        if (loaded.isEmpty() || loaded.getItem() instanceof CreativeCatalystItem) {
            return loaded.isEmpty() ? 0 : 1;
        }
        UsesData d = UsesHelper.get(loaded);
        return d.max() <= 0 ? 0 : (float) d.remaining() / d.max();
    }

    /**
     * 毎 tick: 待っている物を出口へ出し、予備との合流・入れ替え・装填をする。
     * exit は出口へ入れて、入りきらなかった残りを返す。
     */
    public void tick(IItemHandlerModifiable supply, int slot, UnaryOperator<ItemStack> exit) {
        flush(exit);
        ItemStack s = supply.getStackInSlot(slot);
        if (s.isEmpty() || CatalystHelper.tierOf(s) == 0) {
            return;
        }
        if (loaded.isEmpty()) {
            load(supply, slot);
            return;
        }
        if (!sameKind(loaded, s)) {
            // 違う種類の触媒が入った: 装填中のもの（使いかけ）を返して入れ替える
            returns.add(loaded);
            loaded = ItemStack.EMPTY;
            load(supply, slot);
            flush(exit);
            return;
        }
        if (s.getCount() == 1 && UsesHelper.get(s).used() > 0 && !(s.getItem() instanceof CreativeCatalystItem)) {
            merge(supply, slot, s);
            flush(exit);
        }
    }

    /**
     * amount 回ぶん使う。装填中のものを使い切ったら失活触媒を返す物へ回し、予備から次の1個を装填して、
     * 足りない分はその触媒から続けて使う。
     */
    public void consume(int amount, IItemHandlerModifiable supply, int slot) {
        while (amount > 0 && !loaded.isEmpty()) {
            int take = loaded.getItem() instanceof CreativeCatalystItem ? amount : Math.min(amount, UsesHelper.remaining(loaded));
            amount -= take;
            ItemStack after = UsesHelper.consume(loaded, take);
            if (!after.isEmpty() && CatalystHelper.tierOf(after) > 0 && UsesHelper.remaining(after) > 0) {
                loaded = after;
            } else {
                if (!after.isEmpty()) {
                    returns.add(after);
                }
                loaded = ItemStack.EMPTY;
                load(supply, slot);
            }
        }
        changed.run();
    }

    /** 装填中の触媒を取り出す（プレイヤーが取り出すとき）。予備から次は装填しない（次の tick で装填する）。 */
    public ItemStack eject() {
        ItemStack out = loaded;
        loaded = ItemStack.EMPTY;
        changed.run();
        return out;
    }

    /** 壊したとき落とす物（装填中のものと、出口を待っている物）。 */
    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>(returns);
        if (!loaded.isEmpty()) {
            out.add(loaded);
        }
        return out;
    }

    /** 出口を待っている物があるか。 */
    public boolean hasReturns() {
        return !returns.isEmpty();
    }

    private void load(IItemHandlerModifiable supply, int slot) {
        ItemStack s = supply.getStackInSlot(slot);
        if (s.isEmpty() || CatalystHelper.tierOf(s) == 0 || UsesHelper.remaining(s) <= 0) {
            return;
        }
        loaded = s.copyWithCount(1);
        ItemStack rest = s.copy();
        rest.shrink(1);
        supply.setStackInSlot(slot, rest);
        changed.run();
    }

    /** 予備の使いかけ s から、装填中のものの空きの分だけ使用回数を移す。空になった殻は返す物へ。 */
    private void merge(IItemHandlerModifiable supply, int slot, ItemStack s) {
        UsesData into = UsesHelper.get(loaded);
        UsesData from = UsesHelper.get(s);
        int moved = Math.min(from.remaining(), into.used());
        if (moved <= 0) {
            return;
        }
        ItemStack target = loaded.copy();
        target.set(SinguloComponents.USES.get(), new UsesData(into.used() - moved, into.max(), into.repairs()));
        loaded = target;
        ItemStack left = UsesHelper.consume(s, moved);
        if (!left.isEmpty() && CatalystHelper.tierOf(left) > 0 && UsesHelper.remaining(left) > 0) {
            supply.setStackInSlot(slot, left);
        } else {
            supply.setStackInSlot(slot, ItemStack.EMPTY);
            if (!left.isEmpty()) {
                returns.add(left);                        // 空の殻（失活触媒）
            }
        }
        changed.run();
    }

    private void flush(UnaryOperator<ItemStack> exit) {
        boolean moved = false;
        for (int i = 0; i < returns.size(); i++) {
            ItemStack rest = exit.apply(returns.get(i));
            if (rest.getCount() != returns.get(i).getCount()) {
                moved = true;
            }
            if (rest.isEmpty()) {
                returns.remove(i--);
            } else {
                returns.set(i, rest);
            }
        }
        if (moved) {
            changed.run();
        }
    }

    /** 同じ種類の触媒か（同じアイテムで、無限の触媒ならティアも同じ）。 */
    static boolean sameKind(ItemStack a, ItemStack b) {
        return a.is(b.getItem()) && CatalystHelper.tierOf(a) == CatalystHelper.tierOf(b);
    }

    /** 画面に送る、装填中の触媒のアイテムの番号（なければ -1）。 */
    public int loadedItemId() {
        return loaded.isEmpty() ? -1 : BuiltInRegistries.ITEM.getId(loaded.getItem());
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!loaded.isEmpty()) {
            tag.put("loaded", loaded.save(registries));
        }
        ListTag list = new ListTag();
        for (ItemStack r : returns) {
            list.add(r.save(registries));
        }
        tag.put("returns", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        loaded = tag.contains("loaded") ? ItemStack.parseOptional(registries, tag.getCompound("loaded")) : ItemStack.EMPTY;
        returns.clear();
        for (Tag t : tag.getList("returns", Tag.TAG_COMPOUND)) {
            ItemStack r = ItemStack.parseOptional(registries, (CompoundTag) t);
            if (!r.isEmpty()) {
                returns.add(r);
            }
        }
    }
}
