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
    public enum Kind { EMPTY, NO_MATCH, MISSING, TOO_SMALL, AMBIGUOUS, EXTRA, MISPLACED }

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
        boolean board = type == MachineType.PRECISION_ASSEMBLER && inputs.size() == MachineRecipe.ASSEMBLY_CELLS + 1;
        var materials = board ? inputs.subList(0, MachineRecipe.ASSEMBLY_CELLS) : inputs;
        boolean anything = materials.stream().anyMatch(s -> !s.isEmpty());
        for (int i = 0; i < type.fluidInputs() && i < tanks.size(); i++) {
            anything |= !tanks.get(i).isEmpty();
        }
        if (!anything) {
            return new Result(Kind.EMPTY, List.of(Component.translatable("gui.singulo.reason.empty")));
        }
        if (type.selectsRecipe() && selected == null && !board) {
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
        boolean bestAligned = false;
        int boardCount = board ? inputs.subList(0, MachineRecipe.ASSEMBLY_CELLS).stream().mapToInt(ItemStack::getCount).sum() : 0;
        MachineRecipe misplaced = null;
        int nearest = -1;
        for (RecipeHolder<MachineRecipe> holder : MachineBlockEntity.stationRecipes(level, type)) {
            MachineRecipe r = holder.value();
            if (r.isMassRecipe() || MachineBlockEntity.isFree(r) || selected != null || board && !r.fitsAssemblyBoard()) {
                continue;
            }
            int total = r.ingredients().size() + r.fluidIngredients().size();
            int matched = 0;
            int requiredParts = 0;
            for (MachineRecipe.ItemInput in : r.ingredients()) {
                if (in.uses() == 0) requiredParts += in.count();
                if (board && in.uses() > 0 ? in.usesFrom(inputs.get(MachineRecipe.ASSEMBLY_CELLS)) > 0
                        : inputs.stream().anyMatch(s -> !s.isEmpty() && in.ingredient().test(s))) {
                    matched++;
                }
            }
            boolean fluidsReady = true;
            for (MachineRecipe.FluidInput in : r.fluidIngredients()) {
                long have = availableFluid(tanks, in);
                if (have > 0) matched++;
                if (have < in.amount()) fluidsReady = false;
            }
            boolean aligned = board && requiredParts == boardCount && r.matchesAssemblyMaterials(inputs);
            if (board && r.hasAssemblyShape() && fluidsReady && matched == total && requiredParts == boardCount
                    && (r.minSize() <= 0 || r.minSize() <= structureSize)) {
                // A valid diagram can briefly be visible before the running/no-power status reaches the screen.
                if (r.assemblyPlan(inputs) != null) return new Result(Kind.EMPTY, List.of());
                var unordered = r.plan(inputs);
                if (unordered != null && unordered.consume()[MachineRecipe.ASSEMBLY_CELLS] == 0) {
                    int correct = 0;
                    for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
                        var part = r.assemblyInput(cell);
                        if (part != null && part.ingredient().test(inputs.get(cell))) correct++;
                    }
                    if (correct > nearest) {
                        nearest = correct;
                        misplaced = r;
                    }
                }
            }
            // 揃っている割合（同じなら材料の多い方）。材料が1つも合わないレシピは候補にしない
            double score = total == 0 ? 0 : matched / (double) total + matched * 1e-3;
            if ((aligned && !bestAligned) || (aligned == bestAligned && matched > 0 && score > bestScore)) {
                best = r;
                bestScore = score;
                bestAligned = aligned;
            }
        }
        if (misplaced != null && !bestAligned) {
            return new Result(Kind.MISPLACED, List.of(Component.translatable("gui.singulo.reason.making", misplaced.result().getHoverName()),
                    Component.translatable("gui.singulo.assembly.board_hint")));
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
                    boolean catalyst = options.length > 0 && io.github.genichimaruo.singulo.item.UsesItem.isCatalyst(options[0]);
                    lines.add(catalyst ? Component.translatable("gui.singulo.reason.need_lifetime", name,
                            io.github.genichimaruo.singulo.item.UsesItem.lifetime(in.uses()))
                            : Component.translatable("gui.singulo.reason.need_uses", name, in.uses()));
                }
                continue;
            }
            int have = inputs.stream().filter(s -> in.ingredient().test(s)).mapToInt(ItemStack::getCount).sum();
            if (have < in.count()) {
                lines.add(Component.translatable("gui.singulo.reason.need_item", name, in.count() - have));
            }
        }
        for (MachineRecipe.FluidInput in : best.fluidIngredients()) {
            long have = availableFluid(tanks, in);
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

    private static long availableFluid(List<FluidStack> tanks, MachineRecipe.FluidInput in) {
        return tanks.stream().filter(t -> !t.isEmpty() && t.getFluid().isSame(in.fluid()))
                .mapToLong(FluidStack::getAmount).sum();
    }
}
