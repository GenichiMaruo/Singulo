package io.github.genichimaruo.singulo.compat.jei;

import io.github.genichimaruo.singulo.forge.JetForgeBlockEntity;

import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.item.UsesItem;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * 装置1種類のレシピの分類。装置の画面と同じ並び（左の材料グリッド → 矢印 → 右の成果物）で、
 * 材料が2段になる装置では矢印と成果物を2段の真ん中に、出力が2枠の装置では2枠を矢印の上下に置く。
 * 触媒スロットを持つ装置では、触媒は装置の画面と同じく専用の枠（矢印の下）に出す。
 * 電力容量アップグレードが要るレシピでは、装置の画面の右レールと同じく、右端の別枠のタブに出す。
 * 下に処理時間・電力・段階を出す。触媒などの使用回数を減らす材料は、スロットの説明に「使用回数を N 使う」と出る。
 */
public class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {
    /** 下の文字（処理時間・電力と段階）が収まる幅に、右レールのタブの分を足した幅。 */
    private static final int MIN_WIDTH = 160;
    private static final int INPUT_COLUMNS = 3;
    /** グリッドの右端から矢印、矢印から出力、出力から出力の液体までの間隔。 */
    private static final int ARROW_GAP = 4;
    private static final int OUTPUT_STEP = 26;
    private static final int FLUID_OUTPUT_STEP = 22;
    /** 右レールの縁の線と、そこから出るタブ（装置の画面の右レールと同じ22×22、少し下げる）。 */
    private static final int RAIL_TAB_Y = 4;
    private static final int RAIL_TAB = 22;
    private static final int RAIL_COLOR = 0xFF8B95A0;
    /** 触媒の枠の色（装置の画面の触媒スロットと同じ琥珀色）。 */
    private static final int AMBER = 0xFFF0B450;

    /**
     * 枠の並びを決める、装置の形（汎用加工装置は MachineType から。降着ジェット鍛造炉のような専用の装置は自分で作る）。
     * energyCapacity を超える電力のレシピには、容量アップグレードの案内を出す。
     */
    record Station(int inputSlots, int fluidInputs, int outputSlots, int fluidOutputs, boolean hasCatalystSlot, long energyCapacity,
                   net.minecraft.world.level.block.Block block) {
        static Station of(MachineType t) {
            return new Station(t.inputSlots(), t.fluidInputs(), t.outputSlots(), t.fluidOutputs(), t.hasCatalystSlot(), t.energyCapacity(),
                    SinguloJeiPlugin.machineBlock(t));
        }

        boolean hasOutput() {
            return outputSlots > 0;
        }
    }

    private final Station type;
    private final RecipeType<MachineRecipe> recipeType;
    private final IDrawable icon;
    private final IDrawable slot;
    /** アイテムと液体の材料を同じグリッドに左から並べる。グリッドから成果物までを、右レールより左の中央に寄せる。 */
    private final int columns;
    private final int itemX;
    private final int arrowX;
    private final int outputX;
    private final int fluidOutputX;
    private final int width, height, arrowY, railX;

    public MachineRecipeCategory(IGuiHelper gui, MachineType type, RecipeType<MachineRecipe> recipeType) {
        this(gui, Station.of(type), recipeType);
    }

    MachineRecipeCategory(IGuiHelper gui, Station type, RecipeType<MachineRecipe> recipeType) {
        this.type = type;
        this.recipeType = recipeType;
        this.icon = gui.createDrawableItemStack(new ItemStack(type.block()));
        this.slot = gui.getSlotDrawable();
        boolean assembly = type.block() == SinguloJeiPlugin.machineBlock(MachineType.PRECISION_ASSEMBLER);
        this.columns = assembly ? 5 : Math.max(1, Math.min(INPUT_COLUMNS, type.inputSlots() + type.fluidInputs()));
        width = assembly ? 182 : MIN_WIDTH;
        height = assembly ? 106 : 62;
        arrowY = assembly ? 37 : 10;
        railX = width - 23;
        int arrow = (assembly ? 5 : columns) * 18 + ARROW_GAP;
        int output = arrow + OUTPUT_STEP;
        int fluidOutput = type.hasOutput() ? output + FLUID_OUTPUT_STEP : output;
        int right = type.fluidOutputs() > 0 ? fluidOutput + 16 : type.hasOutput() ? output + 16 : arrow + 22;
        int left = (railX - 1 - right) / 2;
        this.itemX = left;
        this.arrowX = left + arrow;
        this.outputX = left + output;
        this.fluidOutputX = left + fluidOutput;
    }

    @Override
    public RecipeType<MachineRecipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(type.block().getDescriptionId());
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    /**
     * 材料の見せ方。触媒を寿命で使う材料は、使う分だけ残った触媒として見せる（アイテムの寿命のバーが、新品のうち使う割合になる）。
     */
    private static ItemStack shown(ItemStack s, MachineRecipe.ItemInput in, int count) {
        ItemStack out = s.copyWithCount(count);
        if (in.uses() > 0 && UsesItem.isCatalyst(out)) {
            int max = UsesHelper.get(out).max();
            if (in.uses() < max) {
                io.github.genichimaruo.singulo.registry.SinguloComponents.set(out, SinguloComponents.USES.get(), new UsesData(max - in.uses(), max, 0));
            }
        }
        return out;
    }

    /** 装置で触媒スロットに入れる材料か（触媒スロットを持つ装置の、触媒の材料）。 */
    private boolean inCatalystSlot(MachineRecipe.ItemInput in) {
        return type.hasCatalystSlot() && Arrays.stream(in.ingredient().getItems()).anyMatch(s -> CatalystHelper.tierOf(s) > 0);
    }

    /** 材料のグリッドが2段以上になる装置か（装置の画面で入力スロットが2段の装置）。 */
    private boolean twoRows() {
        return type.inputSlots() + type.fluidInputs() > INPUT_COLUMNS;
    }

    /** 材料のグリッドの上端。2段なら矢印が1段目と2段目の真ん中に来る高さ。 */
    private int inputY() {
        return twoRows() ? arrowY - 9 : arrowY;
    }

    /** 最初の出力の高さ。出力が2枠の装置では、2枠の真ん中が矢印の高さになる。 */
    private int outputY() {
        return type.outputSlots() >= 2 ? arrowY - 9 : arrowY;
    }

    /** 設定の倍率をかけた、レシピ1回に要る電力。 */
    private int demand(MachineRecipe r) {
        return io.github.genichimaruo.singulo.recipe.RecipeNumbers.energy(r.energy());
    }

    /** 装置の電力容量を超えるので、電力容量アップグレードが要るか。 */
    private boolean needsUpgrade(MachineRecipe r) {
        return demand(r) > type.energyCapacity();
    }

    /** 触媒スロットに入れる材料があるか。 */
    private boolean hasCatalyst(MachineRecipe r) {
        return r.fitsAssemblyBoard() ? r.ingredients().stream().anyMatch(r::assemblyCatalyst)
                : r.ingredients().stream().anyMatch(this::inCatalystSlot);
    }

    private int boardX(int cell) { return itemX + MachineRecipe.assemblySquare(cell) % 5 * 18; }
    private int boardY(int cell) { return 2 + MachineRecipe.assemblySquare(cell) / 5 * 18; }

    /** A recipe card records the displayed variant once even when several assembly cells use that ingredient. */
    static String ingredientName(int index) { return "ingredient_" + index; }

    private IRecipeSlotBuilder ingredient(IRecipeLayoutBuilder builder, MachineRecipe.ItemInput in, int count, int x, int y) {
        List<ItemStack> stacks = Arrays.stream(in.ingredient().getItems()).map(s -> shown(s, in, count > 99 ? 1 : count)).toList();
        IRecipeSlotBuilder slotBuilder = builder.addSlot(RecipeIngredientRole.INPUT, x, y).setBackground(slot, -1, -1)
                .addIngredients(VanillaTypes.ITEM_STACK, stacks);
        counted(slotBuilder, count);
        if (in.uses() > 0) {
            boolean catalyst = !stacks.isEmpty() && UsesItem.isCatalyst(stacks.get(0));
            slotBuilder.addRichTooltipCallback((view, tooltip) -> {
                tooltip.add((catalyst ? Component.translatable("jei.singulo.lifetime", UsesItem.lifetime(in.uses()))
                        : Component.translatable("jei.singulo.uses", in.uses())).withStyle(ChatFormatting.GOLD));
                if (in.tier() > 0) {
                    tooltip.add(Component.translatable("jei.singulo.higher_tier").withStyle(ChatFormatting.GRAY));
                }
            });
        }
        return slotBuilder;
    }

    private static void counted(IRecipeSlotBuilder builder, int count) {
        if (count <= 99) return;
        builder.setOverlay(new IDrawable() {
            @Override public int getWidth() { return 16; }
            @Override public int getHeight() { return 16; }
            @Override public void draw(GuiGraphics graphics, int x, int y) {
                io.github.genichimaruo.singulo.client.StockCount.draw(graphics, x, y, count);
            }
        }, 0, 0);
        builder.addRichTooltipCallback((view, tooltip) ->
                tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "× %,d", count))));
    }

    private void output(IRecipeLayoutBuilder builder, int x, int y, ItemStack stack) {
        var target = builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).setBackground(slot, -1, -1)
                .addItemStack(stack.getCount() > 99 ? stack.copyWithCount(1) : stack);
        counted(target, stack.getCount());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe r, IFocusGroup focuses) {
        boolean board = r.fitsAssemblyBoard();
        int columns = this.columns;
        int fluidOutputX = r.result().isEmpty() && r.byproduct().isEmpty() ? outputX : this.fluidOutputX;
        int itemY = inputY();
        int outputY = outputY();
        int i = 0;
        for (int index = 0; index < r.ingredients().size(); index++) {
            MachineRecipe.ItemInput in = r.ingredients().get(index);
            if (board && !r.assemblyCatalyst(in)) {
                boolean first = true;
                for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
                    if (r.boardIngredientIndex(cell) != index) continue;
                    IRecipeSlotBuilder part = ingredient(builder, in, 1, boardX(cell), boardY(cell));
                    if (first) part.setSlotName(ingredientName(index));
                    first = false;
                }
            } else if (board || inCatalystSlot(in)) {
                ingredient(builder, in, Math.max(1, in.count()), arrowX + 3, arrowY + 22).setSlotName(ingredientName(index));
            } else {
                ingredient(builder, in, Math.max(1, in.count()), itemX + (i % columns) * 18, itemY + (i / columns) * 18);
                i++;
            }
        }
        if (r.restore().isPresent()) {
            int restoreX = itemX + (i % columns) * 18, restoreY = itemY + (i / columns) * 18;
            if (board) {
                for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
                    if (r.boardIngredientIndex(cell) == MachineRecipe.BOARD_RESTORE) {
                        restoreX = boardX(cell);
                        restoreY = boardY(cell);
                        break;
                    }
                }
            }
            builder.addSlot(RecipeIngredientRole.INPUT, restoreX, restoreY).setSlotName("restore")
                    .setBackground(slot, -1, -1).addIngredients(r.restore().get());
        }
        int fluidIndex = i + (r.restore().isPresent() ? 1 : 0);
        for (MachineRecipe.FluidInput f : r.fluidIngredients()) {
            builder.addSlot(RecipeIngredientRole.INPUT, itemX + (fluidIndex % columns) * 18,
                    itemY + (fluidIndex / columns) * 18).setBackground(slot, -1, -1)
                    .addFluidStack(f.fluid(), f.amount()).setFluidRenderer(Math.max(1000, f.amount()), false, 16, 16);
            fluidIndex++;
        }
        if (!r.result().isEmpty()) {
            output(builder, outputX, outputY, r.result());
        }
        if (needsUpgrade(r)) {
            int shownDemand = demand(r);
            int required = io.github.genichimaruo.singulo.machine.CapacityUpgrades.requiredTier(shownDemand);
            var upgrade = builder.addSlot(RecipeIngredientRole.CATALYST, railX + 3, RAIL_TAB_Y + 3)
                    .setBackground(slot, -1, -1);
            // Mk3 でも足りないときは、どの容量アップグレードも並べない（説明だけ出す）
            for (int tier = Math.max(required, 1); required > 0 && tier <= 3; tier++) {
                upgrade.addItemStack(io.github.genichimaruo.singulo.machine.CapacityUpgrades.item(tier));
            }
            upgrade.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                    io.github.genichimaruo.singulo.client.MachineScreen.capacityReason(shownDemand)));
        }
        if (!r.byproduct().isEmpty()) {
            output(builder, outputX, outputY + 18, r.byproduct());
        }
        int oy = arrowY;
        for (FluidStack f : r.fluidResults()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, fluidOutputX, oy).setBackground(slot, -1, -1)
                    .addFluidStack(f.getFluid(), f.getAmount()).setFluidRenderer(Math.max(1000, f.getAmount()), false, 16, 16);
            oy += 18;
        }
    }

    @Override
    public void draw(MachineRecipe r, IRecipeSlotsView view, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        // 矢印
        int ax = arrowX;
        int ay = arrowY;
        if (r.fitsAssemblyBoard()) {
            for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
                if (r.boardIngredientIndex(cell) == MachineRecipe.BOARD_EMPTY) slot.draw(g, boardX(cell) - 1, boardY(cell) - 1);
            }
        }
        // 装置の画面と同じ形（22×16、先がとがる）
        g.fill(ax, ay + 6, ax + 16, ay + 10, 0xFF8B95A0);
        for (int k = 0; k < 8; k++) {
            g.fill(ax + 14 + k, ay + k, ax + 15 + k, ay + 16 - k, 0xFF8B95A0);
        }
        if (hasCatalyst(r)) {
            g.renderOutline(arrowX + 1, arrowY + 20, 20, 20, AMBER);
        }
        if (needsUpgrade(r)) {
            // 装置の画面の右レール：枠の縁の線から、少し下げたタブが右に出る
            g.fill(railX - 1, 0, railX, height - 13, RAIL_COLOR);
            int tx = railX - 1, ty = RAIL_TAB_Y, tw = RAIL_TAB;
            g.fill(tx + 1, ty + 1, tx + tw - 1, ty + RAIL_TAB - 1, 0x20000000);
            g.fill(tx, ty, tx + tw, ty + 1, RAIL_COLOR);
            g.fill(tx, ty + RAIL_TAB - 1, tx + tw, ty + RAIL_TAB, RAIL_COLOR);
            g.fill(tx + tw - 1, ty + 1, tx + tw, ty + RAIL_TAB - 1, RAIL_COLOR);
            g.fill(tx + 2, ty + RAIL_TAB, tx + tw + 1, ty + RAIL_TAB + 1, 0x40000000);
            g.fill(tx + tw, ty + 2, tx + tw + 1, ty + RAIL_TAB, 0x40000000);
        }
        r.mass().ifPresent(m -> g.drawString(font, Component.translatable(m.metalOnly() ? "jei.singulo.metal_mass" : "jei.singulo.mass",
                io.github.genichimaruo.singulo.energy.EnergyText.shortForm(m.amount())), itemX, arrowY + 22, 0xFF3C4650, false));
        long ticks = JetForgeBlockEntity.STATION.equals(r.station()) ? JetForgeBlockEntity.recipeTicks(r.time()) : r.time();
        Component info = Component.translatable("jei.singulo.time_energy", ticks < 20_000 ? String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0)
                : io.github.genichimaruo.singulo.energy.EnergyText.shortForm(ticks / 20.0),
                io.github.genichimaruo.singulo.energy.EnergyText.shortForm(demand(r)));
        Component stage = Component.translatable("gui.singulo.handbook.stage", r.stage());
        int infoWidth = width - font.width(stage) - 7;
        float scale = Math.min(1F, infoWidth / (float) Math.max(1, font.width(info)));
        g.pose().pushPose();
        g.pose().translate(1, height - 10, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, info, 0, 0, 0xFF3C4650, false);
        g.pose().popPose();
        g.drawString(font, stage, width - font.width(stage) - 1, height - 10, 0xFF2A8FB8, false);
        if (r.minSize() > 0) {
            g.drawString(font, Component.translatable("jei.singulo.min_size", r.minSize()), arrowX, arrowY + 22, 0xFFC08020, false);
        }
    }
}
