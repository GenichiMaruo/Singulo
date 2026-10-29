package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineMenu;
import io.github.genichimaruo.singulo.machine.MachineRecipeTransfer;
import io.github.genichimaruo.singulo.machine.RecipeSelection;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.recipe.RecipeTweaks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Only the recipe ID is trusted as a lookup key; the server computes all item movements. */
public record MachineRecipeTransferPayload(int containerId, ResourceLocation recipeId, boolean maxTransfer)
        implements CustomPacketPayload {
    public static final Type<MachineRecipeTransferPayload> TYPE = new Type<>(Singulo.id("machine_recipe_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineRecipeTransferPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MachineRecipeTransferPayload::containerId,
            ResourceLocation.STREAM_CODEC, MachineRecipeTransferPayload::recipeId,
            ByteBufCodecs.BOOL, MachineRecipeTransferPayload::maxTransfer,
            MachineRecipeTransferPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    static void handle(MachineRecipeTransferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player.containerMenu instanceof MachineMenu menu) || menu.containerId != payload.containerId()
                    || !menu.stillValid(player)) return;
            if (!(player.level().getBlockEntity(menu.pos()) instanceof io.github.genichimaruo.singulo.machine.MachineBlockEntity machine)
                    || machine.type() != menu.type()) return;
            var holder = player.level().getRecipeManager().byKey(payload.recipeId()).orElse(null);
            if (holder == null || !(holder.value() instanceof MachineRecipe original)) return;
            MachineRecipe recipe = RecipeTweaks.adjust(payload.recipeId(), original);
            if (recipe == null) return;
            var plan = MachineRecipeTransfer.plan(menu, recipe, payload.maxTransfer());
            if (plan == null) return;
            if (menu.type().selectsRecipe()) {
                int index = RecipeSelection.indexOf(player.level(), menu.type(), payload.recipeId());
                if (index < 0) return;
                menu.clickMenuButton(player, MachineMenu.BUTTON_SELECT + index);
            }
            MachineRecipeTransfer.apply(menu, plan);
        });
    }
}
