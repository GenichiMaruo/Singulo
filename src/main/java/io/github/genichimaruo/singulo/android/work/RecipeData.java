package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * レシピカードに記録したレシピ。
 * <ul>
 * <li>作業台モード（crafting）: 3×3 の並び（各1個）と、作業台のレシピでできる物。</li>
 * <li>加工モード（processing）: 入れる物（最大9種、数つき）と、出てくる物（主な物 output と、ほかに最大2つ extras）。
 * かまどや装置に、上から入れて下から取り出す。</li>
 * <li>手順モード（procedure、手順カード）: 投入・取り出し・採掘の手順（最大8つ、場所はカードの中）と、できる物。
 * 素材は投入する物。</li>
 * </ul>
 * 素材は置いたとおりの物（同じアイテム・同じ付加情報）で数える。
 * 手順カードも同じ記録を使う（{@link ProcedureCardItem}）。
 */
public record RecipeData(String mode, List<ItemStack> grid, ItemStack output, List<ItemStack> extras, List<ProcedureStep> steps) {
    public static final String MODE_CRAFTING = "crafting", MODE_PROCESSING = "processing", MODE_PROCEDURE = "procedure";
    public static final int MAX_STEPS = 8;
    private static final String KEY = "android_recipe";

    /** 作業台モードのレシピ。 */
    public RecipeData(List<ItemStack> grid, ItemStack output) {
        this(MODE_CRAFTING, grid, output, List.of(), List.of());
    }

    public RecipeData(String mode, List<ItemStack> grid, ItemStack output, List<ItemStack> extras) {
        this(mode, grid, output, extras, List.of());
    }

    /** 手順カードの記録。 */
    public static RecipeData procedure(List<ProcedureStep> steps, ItemStack output) {
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return new RecipeData(MODE_PROCEDURE, grid, output, List.of(), List.copyOf(steps));
    }

    public boolean processing() {
        return MODE_PROCESSING.equals(mode);
    }

    public boolean procedure() {
        return MODE_PROCEDURE.equals(mode);
    }

    /** 採掘の手順があるか（採掘アップグレードを積んだアンドロイドだけができる）。 */
    public boolean needsMining() {
        for (ProcedureStep s : steps) {
            if (s.kind() == ProcedureStep.MINE) {
                return true;
            }
        }
        return false;
    }

    /** 出てくる物（主な物と、ほかの物）。 */
    public List<ItemStack> outputs() {
        List<ItemStack> out = new ArrayList<>();
        out.add(output);
        for (ItemStack e : extras) {
            if (!e.isEmpty()) {
                out.add(e);
            }
        }
        return out;
    }

    /** カードの記録（空のカードなら null）。 */
    @Nullable
    public static RecipeData read(ItemStack card, HolderLookup.Provider registries) {
        CustomData data = card.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains(KEY)) {
            return null;
        }
        return load(tag.getCompound(KEY), registries);
    }

    /** カードに記録する。 */
    public void write(ItemStack card, HolderLookup.Provider registries) {
        CompoundTag r = save(registries);
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.put(KEY, r));
        card.set(DataComponents.MAX_STACK_SIZE, 1);           // 記録したカードは重ならない
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag r = new CompoundTag();
        r.putString("mode", mode);
        ListTag list = new ListTag();
        for (ItemStack s : grid) {
            list.add(s.isEmpty() ? new CompoundTag() : s.save(registries));
        }
        r.put("grid", list);
        r.put("output", output.save(registries));
        ListTag ex = new ListTag();
        for (ItemStack s : extras) {
            ex.add(s.isEmpty() ? new CompoundTag() : s.save(registries));
        }
        r.put("extras", ex);
        if (!steps.isEmpty()) {
            ListTag st = new ListTag();
            for (ProcedureStep s : steps) {
                st.add(s.save(registries));
            }
            r.put("steps", st);
        }
        return r;
    }

    @Nullable
    public static RecipeData load(CompoundTag r, HolderLookup.Provider registries) {
        List<ItemStack> grid = new ArrayList<>(9);
        ListTag list = r.getList("grid", Tag.TAG_COMPOUND);
        for (int i = 0; i < 9; i++) {
            CompoundTag t = i < list.size() ? list.getCompound(i) : new CompoundTag();
            grid.add(t.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, t));
        }
        ItemStack output = ItemStack.parseOptional(registries, r.getCompound("output"));
        List<ItemStack> extras = new ArrayList<>();
        for (Tag t : r.getList("extras", Tag.TAG_COMPOUND)) {
            ItemStack s = ((CompoundTag) t).isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, (CompoundTag) t);
            if (!s.isEmpty()) {
                extras.add(s);
            }
        }
        List<ProcedureStep> steps = new ArrayList<>();
        for (Tag t : r.getList("steps", Tag.TAG_COMPOUND)) {
            if (steps.size() < MAX_STEPS) {
                steps.add(ProcedureStep.load((CompoundTag) t, registries));
            }
        }
        String mode = r.contains("mode") ? r.getString("mode") : MODE_CRAFTING;
        return output.isEmpty() ? null : new RecipeData(mode, grid, output, extras, steps);
    }

    /** 記録を消す（新しいカードと同じに戻すので、未記録のカードとまた重なる）。 */
    public static void clear(ItemStack card) {
        CustomData.update(DataComponents.CUSTOM_DATA, card, tag -> tag.remove(KEY));
        CustomData data = card.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.isEmpty()) {
            card.remove(DataComponents.CUSTOM_DATA);
        }
        card.set(DataComponents.MAX_STACK_SIZE, card.getItem().getDefaultMaxStackSize());
    }

    /** 1回分の素材（同じ物はまとめる。加工モードは置いた数、作業台モードは1つずつ）。 */
    public List<ItemCount> inputs() {
        List<ItemCount> out = new ArrayList<>();
        if (procedure()) {
            // 手順カード: 投入する物
            for (ProcedureStep s : steps) {
                if (s.kind() == ProcedureStep.INSERT && !s.item().isEmpty()) {
                    ItemCount.add(out, s.item(), s.item().getCount());
                }
            }
            return out;
        }
        for (ItemStack s : grid) {
            if (!s.isEmpty()) {
                ItemCount.add(out, s, processing() ? s.getCount() : 1);
            }
        }
        return out;
    }

    /** 3×3 の並び（作業台に渡す形）。 */
    public NonNullList<ItemStack> gridList() {
        NonNullList<ItemStack> list = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            list.set(i, grid.get(i).copyWithCount(1));
        }
        return list;
    }
}
