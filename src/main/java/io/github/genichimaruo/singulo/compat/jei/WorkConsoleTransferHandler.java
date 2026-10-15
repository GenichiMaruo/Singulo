package io.github.genichimaruo.singulo.compat.jei;

import io.github.genichimaruo.singulo.android.work.WorkConsoleActionPayload;
import io.github.genichimaruo.singulo.android.work.WorkConsoleMenu;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import io.github.genichimaruo.singulo.network.SinguloNetwork;

/**
 * JEI のレシピの「+」: 作業指令台の記録のページの見本の欄へ、レシピを置く（物は減らない）。
 * 作業台のレシピは作業台モードの並びで、ほかのレシピ（かまどや装置）は加工モードで、入れる物（数つき）と出てくる物を置く。
 * 置いたら、画面を記録のページに切り替える。
 */
final class WorkConsoleTransferHandler implements IUniversalRecipeTransferHandler<WorkConsoleMenu> {
    private final mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper helper;

    WorkConsoleTransferHandler(mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends WorkConsoleMenu> getContainerClass() {
        return WorkConsoleMenu.class;
    }

    @Override
    public Optional<MenuType<WorkConsoleMenu>> getMenuType() {
        return Optional.of(SinguloMenus.ANDROID_WORK_CONSOLE.get());
    }

    /** 欄の物（流体なら、流体の見本）。 */
    private static Optional<ItemStack> sample(IRecipeSlotView view) {
        Optional<ItemStack> item = view.getDisplayedItemStack();
        if (item.isPresent()) {
            return item;
        }
        return view.getDisplayedIngredient(mezz.jei.api.forge.ForgeTypes.FLUID_STACK)
                .map(io.github.genichimaruo.singulo.android.work.FluidSamples::of).filter(s -> !s.isEmpty());
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(WorkConsoleMenu menu, Object recipe, IRecipeSlotsView slots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        boolean crafting = recipe instanceof CraftingRecipe;
        if (!crafting) {
            List<ItemStack> inputs = new ArrayList<>(), outputs = new ArrayList<>();
            for (IRecipeSlotView view : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
                sample(view).ifPresent(inputs::add);
            }
            for (IRecipeSlotView view : slots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
                sample(view).ifPresent(outputs::add);
            }
            if (inputs.isEmpty() || outputs.isEmpty()) {
                return helper.createInternalError();   // 物のないレシピ（液体だけなど）は扱えない: ボタンを出さない
            }
            if (doTransfer) {
                SinguloNetwork.sendToServer(new WorkConsoleActionPayload(menu.containerId,
                        WorkConsoleActionPayload.process(inputs, outputs, player.level().registryAccess())));
                if (Minecraft.getInstance().screen instanceof io.github.genichimaruo.singulo.client.WorkConsoleScreen screen) {
                    screen.showRecordTab();
                }
            }
            return null;
        }
        if (!doTransfer) {
            return null;
        }
        List<ItemStack> grid = new ArrayList<>();
        for (IRecipeSlotView view : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            grid.add(view.getDisplayedItemStack().orElse(ItemStack.EMPTY));
        }
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        SinguloNetwork.sendToServer(new WorkConsoleActionPayload(menu.containerId,
                WorkConsoleActionPayload.grid(grid.subList(0, 9), player.level().registryAccess())));
        if (Minecraft.getInstance().screen instanceof io.github.genichimaruo.singulo.client.WorkConsoleScreen screen) {
            screen.showRecordTab();
        }
        return null;
    }
}
