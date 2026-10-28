package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 待機している装置が、なぜ動かないのかを調べる（画面の状態の表示とツールチップに使う）。
 * 材料が入っていれば、いちばん揃っているレシピを「作ろうとしているもの」とみなし、足りない材料・液体・大きさを挙げる。
 * サーバーと同じレシピの並び（{@link MachineBlockEntity#stationRecipes}）を使うので、クライアントでも同じ結果になる。
 */
public final class MachineDiagnosis {
    /**
     * 待機の理由。EMPTY = 何も入っていない、NO_MATCH = 入っている物で作れるものがない、MISSING = 材料が足りない、
     * TOO_SMALL = 大きさが足りない、AMBIGUOUS = 材料はあるが、ほかのレシピの途中と同じなので自動では作らない、
     * EXTRA = 材料はあるが、使わない物も入っているので自動では作らない（作るものを選べる装置だけ）。
     */
    public enum Kind { EMPTY, NO_MATCH, MISSING, TOO_SMALL, AMBIGUOUS, EXTRA }

    public record Result(Kind kind, List<Component> lines) {}

    private MachineDiagnosis() {}

    /**
     * @param inputs 照合に使うスロットの中身（入力 → 触媒。{@link MachineType#recipeSlot}）
     * @param tanks  タンクの中身（入力タンク → 出力タンク）
     */
    public static Result idle(Level level, MachineType type, List<ItemStack> inputs, List<FluidStack> tanks, int structureSize) {
        return idle(level, type, inputs, tanks, structureSize, null);
    }

    /** @param selected 指定した作るもの（{@link RecipeSelection}）。null なら自動 */
    public static Result idle(Level level, MachineType type, List<ItemStack> inputs, List<FluidStack> tanks, int structureSize,
                              @javax.annotation.Nullable RecipeHolder<MachineRecipe> selected) {
        boolean anything = inputs.stream().anyMatch(s -> !s.isEmpty());
        for (int i = 0; i < type.fluidInputs() && i < tanks.size(); i++) {
            anything |= !tanks.get(i).isEmpty();
        }
        if (!anything) {
            return new Result(Kind.EMPTY, List.of(Component.translatable("gui.singulo.reason.empty")));
        }
        if (type.selectsRecipe() && selected == null) {
            // 自動: 材料は揃っているのに、選び方の決まりで止めているものがあれば、それを理由にする
            for (RecipeHolder<MachineRecipe> holder : RecipeSelection.selectable(level, type)) {
                MachineRecipe.Plan plan = holder.value().plan(inputs);
                if (plan == null) {
                    continue;
                }
                Component name = holder.value().result().getHoverName();
                if (!RecipeSelection.usesEveryInput(type, inputs, plan)) {
                    return new Result(Kind.EXTRA, List.of(Component.translatable("gui.singulo.reason.extra", name),
                            Component.translatable("gui.singulo.reason.select_hint")));
                }
                if (RecipeSelection.ambiguous(level, type, holder)) {
                    return new Result(Kind.AMBIGUOUS, List.of(Component.translatable("gui.singulo.reason.ambiguous", name),
                            Component.translatable("gui.singulo.reason.select_hint")));
                }
            }
        }
        MachineRecipe best = selected == null ? null : selected.value();
        double bestScore = 0;
        for (RecipeHolder<MachineRecipe> holder : MachineBlockEntity.stationRecipes(level, type)) {
            MachineRecipe r = holder.value();
            if (r.isMassRecipe() || MachineBlockEntity.isFree(r) || selected != null) {
                continue;
            }
            int total = r.ingredients().size() + r.fluidIngredients().size();
            int matched = 0;
            for (MachineRecipe.ItemInput in : r.ingredients()) {
                if (inputs.stream().anyMatch(s -> !s.isEmpty() && in.ingredient().test(s))) {
                    matched++;
                }
            }
            for (MachineRecipe.FluidInput in : r.fluidIngredients()) {
                if (tanks.stream().anyMatch(t -> !t.isEmpty() && in.matches(t.copyWithAmount(Integer.MAX_VALUE)))) {
                    matched++;
                }
            }
            // 揃っている割合（同じなら材料の多い方）。材料が1つも合わないレシピは候補にしない
            double score = total == 0 ? 0 : matched / (double) total + matched * 1e-3;
            if (matched > 0 && score > bestScore) {
                best = r;
                bestScore = score;
            }
        }
        if (best == null) {
            return new Result(Kind.NO_MATCH, List.of(Component.translatable("gui.singulo.reason.no_match")));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.singulo.reason.making", best.result().isEmpty() && !best.fluidResults().isEmpty()
                ? best.fluidResults().get(0).getHoverName() : best.result().getHoverName()));
        Kind kind = Kind.MISSING;
        for (MachineRecipe.ItemInput in : best.ingredients()) {
            ItemStack[] options = in.ingredient().getItems();
            Component name = options.length > 0 ? options[0].getHoverName() : Component.literal("?");
            if (in.uses() > 0) {
                if (inputs.stream().noneMatch(s -> in.usesFrom(s) > 0)) {
                    lines.add(Component.translatable("gui.singulo.reason.need_uses", name, in.uses()));
                }
                continue;
            }
            int have = inputs.stream().filter(s -> in.ingredient().test(s)).mapToInt(ItemStack::getCount).sum();
            if (have < in.count()) {
                lines.add(Component.translatable("gui.singulo.reason.need_item", name, in.count() - have));
            }
        }
        for (MachineRecipe.FluidInput in : best.fluidIngredients()) {
            int have = tanks.stream().filter(t -> !t.isEmpty() && in.matches(t.copyWithAmount(Integer.MAX_VALUE)))
                    .mapToInt(FluidStack::getAmount).max().orElse(0);
            if (have < in.amount()) {
                lines.add(Component.translatable("gui.singulo.reason.need_fluid", in.fluid().getFluidType().getDescription(),
                        in.amount() - have, in.amount()));
            }
        }
        if (best.minSize() > 0 && structureSize < best.minSize()) {
            lines.add(Component.translatable("gui.singulo.reason.too_small", best.minSize(), structureSize));
            if (lines.size() == 2) {
                kind = Kind.TOO_SMALL;
            }
        }
        if (lines.size() == 1) {
            // 数は足りているのに照合に失敗した（同じスロットを2つの材料で取り合っているなど）
            lines.add(Component.translatable("gui.singulo.reason.arrange"));
        }
        return new Result(kind, lines);
    }
}
