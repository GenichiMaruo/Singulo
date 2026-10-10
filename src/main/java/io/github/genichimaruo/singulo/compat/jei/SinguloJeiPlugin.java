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

import net.minecraft.world.level.block.Block;

/**
 * JEI との連携。装置ごとのレシピの分類（入力・液体・質量・触媒の使用回数・時間・電力）と、
 * レシピのないアイテム（遺構の回収物など）の説明ページを出す。
 */
@JeiPlugin
public class SinguloJeiPlugin implements IModPlugin {
    static final Map<MachineType, RecipeType<MachineRecipe>> TYPES = new EnumMap<>(MachineType.class);

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

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (MachineType t : MachineType.values()) {
            registration.addRecipeCategories(new MachineRecipeCategory(registration.getJeiHelpers().getGuiHelper(), t, TYPES.get(t)));
        }
        registration.addRecipeCategories(new MultiblockInfoCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
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
        for (MachineRecipe h : Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get())) {
            for (MachineType t : MachineType.values()) {
                if (t.id().equals(h.value().station())) {
                    byType.computeIfAbsent(t, k -> new ArrayList<>()).add(h.value());
                }
            }
        }
        byType.forEach((t, list) -> registration.addRecipes(TYPES.get(t), list));
        // レシピのないアイテムには説明（遺構の回収物・粒子加速器の副産物・クリエイティブ専用など）
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!Singulo.MODID.equals(id.getNamespace())) {
                continue;
            }
            String howto = "howto.singulo." + id.getPath();
            String desc = "desc.singulo." + id.getPath();
            if (I18n.exists(desc) && (!I18n.exists(howto) || I18n.get(howto).startsWith(I18n.get("jei.singulo.found_prefix")))) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(desc));
                if (I18n.exists(howto)) {
                    lines.add(Component.translatable(howto));
                }
                registration.addItemStackInfo(new ItemStack(item), lines.toArray(new Component[0]));
            }
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineType t : MachineType.values()) {
            registration.addRecipeCatalyst(new ItemStack(machineBlock(t)), TYPES.get(t));
        }
        registration.addRecipeCatalyst(new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.HOLO_PROJECTOR.get()), MultiblockInfoCategory.TYPE);
    }
}
