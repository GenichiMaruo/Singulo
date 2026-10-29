package io.github.genichimaruo.singulo.compat.jei;

import io.github.genichimaruo.singulo.machine.MachineMenu;
import io.github.genichimaruo.singulo.machine.MachineRecipeTransfer;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.network.MachineRecipeTransferPayload;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.PacketDistributor;

final class MachineTransferHandler implements IRecipeTransferHandler<MachineMenu, MachineRecipe> {
    private final MachineType type;
    private final IRecipeTransferHandlerHelper helper;

    MachineTransferHandler(MachineType type, IRecipeTransferHandlerHelper helper) {
        this.type = type;
        this.helper = helper;
    }

    @Override
    public Class<? extends MachineMenu> getContainerClass() { return MachineMenu.class; }

    @Override
    public Optional<MenuType<MachineMenu>> getMenuType() { return Optional.of(SinguloMenus.MACHINE.get()); }

    @Override
    public RecipeType<MachineRecipe> getRecipeType() { return SinguloJeiPlugin.TYPES.get(type); }

    @Override
    public IRecipeTransferError transferRecipe(IRecipeTransferContext<MachineRecipe, MachineMenu> context, boolean doTransfer) {
        var error = transferRecipe(context.getContainer(), context.getRecipe(), context.getRecipeSlots(),
                context.getPlayer(), context.isMaxTransfer(), doTransfer);
        if (doTransfer) context.completeRecipeTransfer(error == null ? RecipeTransferResult.SUCCESS : RecipeTransferResult.REJECTED);
        return error;
    }

    @Override
    @SuppressWarnings("removal")
    public IRecipeTransferError transferRecipe(MachineMenu menu, MachineRecipe recipe, IRecipeSlotsView slots,
                                               Player player, boolean maxTransfer, boolean doTransfer) {
        var id = SinguloJeiPlugin.RECIPE_IDS.get(recipe);
        if (menu.type() != type || id == null || recipe.mass().isPresent()
                || (recipe.ingredients().isEmpty() && recipe.restore().isEmpty())) return helper.createInternalError();
        if (MachineRecipeTransfer.plan(menu, recipe, maxTransfer) == null) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.missing")
                    .append(" / ").append(Component.translatable("jei.tooltip.error.recipe.transfer.inventory.full")));
        }
        if (doTransfer) PacketDistributor.sendToServer(new MachineRecipeTransferPayload(menu.containerId, id, maxTransfer));
        return null;
    }
}
