package io.github.genichimaruo.singulo.compat.jei;

import io.github.genichimaruo.singulo.item.CatalystHelper;
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
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 装置1種類のレシピの分類。左に材料（最大6、3列）と液体、真ん中に矢印、右に成果物と液体の成果物。
 * 触媒スロットを持つ装置では、触媒は装置の画面と同じく専用の枠（矢印の下）に出す。
 * 下に処理時間・電力・段階を出す。触媒などの使用回数を減らす材料は、スロットの説明に「使用回数を N 使う」と出る。
 */
public class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {
    static final int H = 62;
    /** 下の文字（処理時間・電力と段階）が収まる幅。 */
    private static final int MIN_WIDTH = 130;
    /** 矢印と、その下の触媒の枠の高さ。 */
    private static final int ARROW_Y = 10;
    private static final int CATALYST_Y = 30;
    /** 列どうしの間（材料と矢印、矢印と成果物など）。 */
    private static final int GAP = 6;
    /** 触媒の枠の色（装置の画面の触媒スロットと同じ琥珀色）。 */
    private static final int AMBER = 0xFFF0B450;

    private final MachineType type;
    private final RecipeType<MachineRecipe> recipeType;
    private final IDrawable icon;
    private final IDrawable slot;
    /**
     * 列の位置。装置の枠の数に合わせて詰める（材料の枠が少ない装置や液体のない装置に、空いた列を残さない）。
     * 中身が MIN_WIDTH より狭ければ、まん中に寄せる。
     */
    private final int itemX;
    private final int itemColumns;
    private final int fluidX;
    private final int arrowX;
    private final int outputX;
    private final int fluidOutputX;
    private final int width;

    public MachineRecipeCategory(IGuiHelper gui, MachineType type, RecipeType<MachineRecipe> recipeType) {
        this.type = type;
        this.recipeType = recipeType;
        this.icon = gui.createDrawableItemStack(new ItemStack(SinguloJeiPlugin.machineBlock(type)));
        this.slot = gui.getSlotDrawable();
        itemColumns = Math.min(3, type.inputSlots());
        int x = itemColumns * 18;
        int fluid = x + (itemColumns > 0 ? GAP - 2 : 0);
        if (type.fluidInputs() > 0) {
            x = fluid + 18;
        }
        int arrow = x + GAP;
        x = arrow + 22;
        int output = x + GAP;
        if (type.outputSlots() > 0) {
            x = output + 18;
        }
        int fluidOutput = x + (type.outputSlots() > 0 ? GAP - 2 : GAP);
        if (type.fluidOutputs() > 0) {
            x = fluidOutput + 18;
        }
        width = Math.max(MIN_WIDTH, x + 2);
        int shift = (width - x) / 2 + 1;
        itemX = shift;
        fluidX = fluid + shift;
        arrowX = arrow + shift;
        outputX = output + shift;
        fluidOutputX = fluidOutput + shift;
    }

    @Override
    public RecipeType<MachineRecipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(SinguloJeiPlugin.machineBlock(type).getDescriptionId());
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
        return H;
    }

    /** 材料の列の数（材料の枠のない装置でも、修復の対象などを並べられるよう1列は取る）。 */
    private int itemColumns() {
        return Math.max(1, itemColumns);
    }

    /** 装置で触媒スロットに入れる材料か（触媒スロットを持つ装置の、触媒の材料）。 */
    private boolean inCatalystSlot(MachineRecipe.ItemInput in) {
        return type.hasCatalystSlot() && Arrays.stream(in.ingredient().getItems()).anyMatch(s -> CatalystHelper.tierOf(s) > 0);
    }

    /** 触媒スロットに入れる材料があるか。 */
    private boolean hasCatalyst(MachineRecipe r) {
        return r.ingredients().stream().anyMatch(this::inCatalystSlot);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe r, IFocusGroup focuses) {
        int i = 0;
        for (MachineRecipe.ItemInput in : r.ingredients()) {
            int x;
            int y;
            if (inCatalystSlot(in)) {
                x = arrowX + 3;
                y = CATALYST_Y;
            } else {
                x = itemX + (i % itemColumns()) * 18;
                y = 1 + (i / itemColumns()) * 18;
                i++;
            }
            List<ItemStack> stacks = Arrays.stream(in.ingredient().getItems()).map(s -> s.copyWithCount(Math.max(1, in.count()))).toList();
            IRecipeSlotBuilder s = builder.addSlot(RecipeIngredientRole.INPUT, x, y).setBackground(slot, -1, -1)
                    .addIngredients(VanillaTypes.ITEM_STACK, stacks);
            if (in.uses() > 0) {
                int uses = in.uses();
                boolean higher = in.tier() > 0;
                s.addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.singulo.uses", uses).withStyle(ChatFormatting.GOLD));
                    if (higher) {
                        tooltip.add(Component.translatable("jei.singulo.higher_tier").withStyle(ChatFormatting.GRAY));
                    }
                });
            }
        }
        int restoreAt = i;
        r.restore().ifPresent(ing -> builder.addSlot(RecipeIngredientRole.INPUT, itemX + (restoreAt % itemColumns()) * 18,
                1 + (restoreAt / itemColumns()) * 18).setBackground(slot, -1, -1).addIngredients(ing));
        int fy = 1;
        for (MachineRecipe.FluidInput f : r.fluidIngredients()) {
            builder.addSlot(RecipeIngredientRole.INPUT, fluidX, fy).setBackground(slot, -1, -1)
                    .addFluidStack(f.fluid(), f.amount()).setFluidRenderer(Math.max(1000, f.amount()), false, 16, 16);
            fy += 18;
        }
        if (!r.result().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, outputX, 10).setBackground(slot, -1, -1).addItemStack(r.result());
        }
        if (!r.byproduct().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, outputX, 30).setBackground(slot, -1, -1).addItemStack(r.byproduct());
        }
        int oy = 1;
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
        int ay = ARROW_Y;
        // 装置の画面と同じ形（22×16、先がとがる）
        g.fill(ax, ay + 6, ax + 16, ay + 10, 0xFF8B95A0);
        for (int k = 0; k < 8; k++) {
            g.fill(ax + 14 + k, ay + k, ax + 15 + k, ay + 16 - k, 0xFF8B95A0);
        }
        if (hasCatalyst(r)) {
            g.renderOutline(arrowX + 1, CATALYST_Y - 2, 20, 20, AMBER);
        }
        r.mass().ifPresent(m -> g.drawString(font, Component.translatable(m.metalOnly() ? "jei.singulo.metal_mass" : "jei.singulo.mass",
                String.format("%.0f", m.amount())), itemX, 2, 0xFF3C4650, false));
        Component info = Component.translatable("jei.singulo.time_energy", String.format("%.1f", r.time() / 20.0), r.energy());
        g.drawString(font, info, 1, H - 10, 0xFF3C4650, false);
        Component stage = Component.translatable("gui.singulo.handbook.stage", r.stage());
        g.drawString(font, stage, width - font.width(stage) - 1, H - 10, 0xFF2A8FB8, false);
        if (r.minSize() > 0) {
            g.drawString(font, Component.translatable("jei.singulo.min_size", r.minSize()), arrowX, 30, 0xFFC08020, false);
        }
    }
}
