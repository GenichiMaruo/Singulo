package io.github.genichimaruo.singulo.compat.jei;

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
 * 装置1種類のレシピの分類。左に材料（最大6、3列）と液体、真ん中に矢印、右に成果物と液体の成果物。
 * 下に処理時間・電力・段階を出す。触媒などの使用回数を減らす材料は、スロットの説明に「使用回数を N 使う」と出る。
 */
public class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {
    static final int W = 168;
    static final int H = 62;

    private final MachineType type;
    private final RecipeType<MachineRecipe> recipeType;
    private final IDrawable icon;
    private final IDrawable slot;

    public MachineRecipeCategory(IGuiHelper gui, MachineType type, RecipeType<MachineRecipe> recipeType) {
        this.type = type;
        this.recipeType = recipeType;
        this.icon = gui.createDrawableItemStack(new ItemStack(SinguloJeiPlugin.machineBlock(type)));
        this.slot = gui.getSlotDrawable();
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
        return W;
    }

    @Override
    public int getHeight() {
        return H;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe r, IFocusGroup focuses) {
        int i = 0;
        for (MachineRecipe.ItemInput in : r.ingredients()) {
            int x = 1 + (i % 3) * 18;
            int y = 1 + (i / 3) * 18;
            List<ItemStack> stacks = Arrays.stream(in.ingredient().getItems()).map(s -> s.copyWithCount(Math.max(1, in.count()))).toList();
            IRecipeSlotBuilder s = builder.addSlot(RecipeIngredientRole.INPUT, x, y).setBackground(slot, -1, -1)
                    .addIngredients(VanillaTypes.ITEM_STACK, stacks);
            if (in.uses() > 0) {
                int uses = in.uses();
                s.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei.singulo.uses", uses).withStyle(ChatFormatting.GOLD)));
            }
            i++;
        }
        r.restore().ifPresent(ing -> builder.addSlot(RecipeIngredientRole.INPUT, 1 + (r.ingredients().size() % 3) * 18,
                1 + (r.ingredients().size() / 3) * 18).setBackground(slot, -1, -1).addIngredients(ing));
        int fy = 1;
        for (MachineRecipe.FluidInput f : r.fluidIngredients()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 58, fy).setBackground(slot, -1, -1)
                    .addFluidStack(f.fluid(), f.amount()).setFluidRenderer(Math.max(1000, f.amount()), false, 16, 16);
            fy += 18;
        }
        if (!r.result().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 112, 10).setBackground(slot, -1, -1).addItemStack(r.result());
        }
        int oy = 1;
        for (FluidStack f : r.fluidResults()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 134, oy).setBackground(slot, -1, -1)
                    .addFluidStack(f.getFluid(), f.getAmount()).setFluidRenderer(Math.max(1000, f.getAmount()), false, 16, 16);
            oy += 18;
        }
    }

    @Override
    public void draw(MachineRecipe r, IRecipeSlotsView view, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        // 矢印
        int ax = 82;
        int ay = 14;
        g.fill(ax, ay + 3, ax + 14, ay + 6, 0xFF8B95A0);
        for (int k = 0; k < 5; k++) {
            g.fill(ax + 14 + k, ay - 1 + k, ax + 15 + k, ay + 10 - k, 0xFF8B95A0);
        }
        r.mass().ifPresent(m -> g.drawString(font, Component.translatable(m.metalOnly() ? "jei.singulo.metal_mass" : "jei.singulo.mass",
                String.format("%.0f", m.amount())), 1, 2, 0xFF3C4650, false));
        Component info = Component.translatable("jei.singulo.time_energy", String.format("%.1f", r.time() / 20.0), r.energy());
        g.drawString(font, info, 1, H - 10, 0xFF3C4650, false);
        Component stage = Component.translatable("gui.singulo.handbook.stage", r.stage());
        g.drawString(font, stage, W - font.width(stage) - 1, H - 10, 0xFF2A8FB8, false);
        if (r.minSize() > 0) {
            g.drawString(font, Component.translatable("jei.singulo.min_size", r.minSize()), 82, 30, 0xFFC08020, false);
        }
    }
}
