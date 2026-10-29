package io.github.genichimaruo.singulo.compat.jei;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

/**
 * JEI との連携。装置ごとのレシピの分類（入力・液体・質量・触媒の使用回数・時間・電力）と、
 * レシピのないアイテム（遺構の回収物など）の説明ページを出す。
 */
@JeiPlugin
public class SinguloJeiPlugin implements IModPlugin {
    static final Map<MachineType, RecipeType<MachineRecipe>> TYPES = new EnumMap<>(MachineType.class);
    static final Map<MachineRecipe, ResourceLocation> RECIPE_IDS = new java.util.IdentityHashMap<>();

    static {
        for (MachineType t : MachineType.values()) {
            TYPES.put(t, RecipeType.create(Singulo.MODID, t.id(), MachineRecipe.class));
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return Singulo.id("jei");
    }

    /** 装置のブロック（1マスの装置かマルチブロックのコントローラ）。 */
    static Block machineBlock(MachineType t) {
        return t.isMultiblock() ? SinguloBlocks.CONTROLLERS.get(t).get() : SinguloBlocks.MACHINES.get(t).get();
    }

    /** 枠の外の右レールに JEI の一覧が重ならないようにする。 */
    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(io.github.genichimaruo.singulo.client.FramedScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<io.github.genichimaruo.singulo.client.FramedScreen<?>>() {
                    @Override
                    public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(
                            io.github.genichimaruo.singulo.client.FramedScreen<?> screen) {
                        return screen.extraAreas();
                    }
                });
        // 装置の画面の進捗の矢印をクリックすると、その装置で作れるものを JEI で出す
        registration.addGuiContainerHandler(io.github.genichimaruo.singulo.client.MachineScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<io.github.genichimaruo.singulo.client.MachineScreen>() {
                    @Override
                    public java.util.Collection<mezz.jei.api.gui.handlers.IGuiClickableArea> getGuiClickableAreas(
                            io.github.genichimaruo.singulo.client.MachineScreen screen, double mouseX, double mouseY) {
                        if (screen.popupBlocksGuiInput(mouseX, mouseY)) {
                            return List.of();
                        }
                        net.minecraft.client.renderer.Rect2i a = screen.arrowArea();
                        return List.of(mezz.jei.api.gui.handlers.IGuiClickableArea.createBasic(a.getX(), a.getY(),
                                a.getWidth(), a.getHeight(), TYPES.get(screen.machineType())));
                    }
                });
    }

    /** 機械には実物の素材を転送し、作業指令台にはレシピの見本を記録する。 */
    @Override
    public void registerRecipeTransferHandlers(mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new WorkConsoleTransferHandler(registration.getTransferHelper()));
        for (MachineType type : MachineType.values()) {
            registration.addRecipeTransferHandler(new MachineTransferHandler(type, registration.getTransferHelper()), TYPES.get(type));
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (MachineType t : MachineType.values()) {
            registration.addRecipeCategories(new MachineRecipeCategory(registration.getJeiHelpers().getGuiHelper(), t, TYPES.get(t)));
        }
        registration.addRecipeCategories(new MultiblockInfoCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RECIPE_IDS.clear();
        // マルチブロック装置の組み立てと動かし方
        List<MultiblockInfoCategory.Info> infos = new ArrayList<>();
        for (io.github.genichimaruo.singulo.multiblock.Blueprints.Kind k : io.github.genichimaruo.singulo.multiblock.Blueprints.Kind.values()) {
            infos.add(MultiblockInfoCategory.Info.of(k));
        }
        registration.addRecipes(MultiblockInfoCategory.TYPE, infos);
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Map<MachineType, List<MachineRecipe>> byType = new EnumMap<>(MachineType.class);
        // 装置と同じく、サーバー設定（プリセット・殻の材料・育成時間など）を当てはめたレシピを見せる
        for (RecipeHolder<MachineRecipe> h : io.github.genichimaruo.singulo.recipe.RecipeTweaks.adjustAll(
                Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get()))) {
            RECIPE_IDS.put(h.value(), h.id());
            for (MachineType t : MachineType.values()) {
                if (t.id().equals(h.value().station())) {
                    byType.computeIfAbsent(t, k -> new ArrayList<>()).add(h.value());
                }
            }
        }
        byType.forEach((t, list) -> registration.addRecipes(TYPES.get(t), list));
        // JEI のレシピで作れないアイテムには説明と入手方法（遺構の回収物・粒子加速器の副産物・Pリアクターの運転で生まれる物・
        // 専用の処理で作る物・クリエイティブ専用など）
        var registries = Minecraft.getInstance().level.registryAccess();
        java.util.Set<Item> made = new java.util.HashSet<>();
        for (RecipeHolder<?> h : Minecraft.getInstance().level.getRecipeManager().getRecipes()) {
            ItemStack result = h.value().getResultItem(registries);
            if (!result.isEmpty()) {
                made.add(result.getItem());
            }
        }
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!Singulo.MODID.equals(id.getNamespace())) {
                continue;
            }
            String howto = "howto.singulo." + id.getPath();
            String desc = "desc.singulo." + id.getPath();
            if (I18n.exists(desc) && (!I18n.exists(howto) || !made.contains(item)
                    || I18n.get(howto).startsWith(I18n.get("jei.singulo.found_prefix")))) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(desc));
                if (I18n.exists(howto)) {
                    lines.add(Component.translatable(howto));
                }
                registration.addItemStackInfo(new ItemStack(item), lines.toArray(new Component[0]));
            }
        }
    }

    /** 隠しボスの報酬（残光の翼）と、作業の内部の目印（流体・化学物質の見本）、まだ表に出さないアイテムは、JEI の一覧に出さない。 */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        JeiSearch.runtime = runtime;
        List<ItemStack> hidden = new ArrayList<>(List.of(new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.AFTERGLOW_WINGS.get()),
                new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.FLUID_SAMPLE.get()),
                new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.CHEMICAL_SAMPLE.get())));
        for (String id : io.github.genichimaruo.singulo.registry.SinguloItems.HIDDEN) {
            hidden.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id(id))));
        }
        runtime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, hidden);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiSearch.runtime = null;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineType t : MachineType.values()) {
            registration.addRecipeCatalyst(new ItemStack(machineBlock(t)), TYPES.get(t));
        }
        registration.addRecipeCatalyst(new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.HOLO_PROJECTOR.get()), MultiblockInfoCategory.TYPE);
    }
}
