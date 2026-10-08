package io.github.genichimaruo.singulo.compat.jei;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * マルチブロック装置の説明ページ。コントローラーかその部品を調べると出る。
 * 上に必要な部品と個数（いちばん小さい形で数える）、下に起動のしかた・燃料・消費・出力などの説明。
 */
public class MultiblockInfoCategory implements IRecipeCategory<MultiblockInfoCategory.Info> {
    public static final RecipeType<Info> TYPE = RecipeType.create(Singulo.MODID, "multiblock", Info.class);
    static final int W = 168;
    static final int H = 168;
    /** 部品スロットの段数（1段9個）。 */
    private static final int PART_ROWS = 2;

    /** 1種類のマルチブロックの情報。parts は部品と個数（コントローラーを含む）。 */
    public record Info(Blueprints.Kind kind, List<ItemStack> parts) {
        public static Info of(Blueprints.Kind kind) {
            List<ItemStack> parts = new ArrayList<>();
            Blueprints.partCounts(kind).forEach((block, n) -> {
                if (block.asItem() != net.minecraft.world.item.Items.AIR) {
                    parts.add(new ItemStack(block, n));
                }
            });
            return new Info(kind, parts);
        }
    }

    private final IDrawable icon;
    private final IDrawable slot;

    public MultiblockInfoCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(Blueprints.controllerBlock(Blueprints.Kind.PENROSE_REACTOR)));
        this.slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<Info> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.singulo.multiblock");
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
    public void setRecipe(IRecipeLayoutBuilder builder, Info info, IFocusGroup focuses) {
        int i = 0;
        for (ItemStack part : info.parts()) {
            if (i >= 9 * PART_ROWS) {
                break;
            }
            int x = 1 + (i % 9) * 18;
            int y = 12 + (i / 9) * 18;
            // コントローラーは「作る対象」、ほかは部品として材料扱い（どちらを調べてもこのページが出る）
            builder.addSlot(i == 0 ? RecipeIngredientRole.CATALYST : RecipeIngredientRole.INPUT, x, y)
                    .setBackground(slot, -1, -1).addIngredient(VanillaTypes.ITEM_STACK, part);
            i++;
        }
    }

    @Override
    public void draw(Info info, IRecipeSlotsView view, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        Component name = Component.translatable(Blueprints.controllerBlock(info.kind()).getDescriptionId());
        g.drawString(font, name, 1, 1, 0xFF2A8FB8, false);
        int y = 12 + PART_ROWS * 18 + 3;
        String key = "jei.singulo.mb." + info.kind().id();
        if (!I18n.exists(key)) {
            return;
        }
        for (String line : I18n.get(key).split("\n")) {
            boolean head = line.startsWith("■");
            for (FormattedCharSequence seq : font.split(Component.literal(line), W - 2)) {
                if (y > H - 9) {
                    return;
                }
                g.drawString(font, seq, 1, y, head ? 0xFFB06A10 : 0xFF3C4650, false);
                y += 9;
            }
        }
    }
}
