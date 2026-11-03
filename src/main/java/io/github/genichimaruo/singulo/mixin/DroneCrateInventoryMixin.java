package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply the same nesting rule to Forge's unsided shulker inventory wrapper. */
@Mixin(value = InvWrapper.class, remap = false)
abstract class DroneCrateInventoryMixin {
    private boolean singulo$blocked(ItemStack stack) {
        return ((InvWrapper)(Object)this).getInv() instanceof ShulkerBoxBlockEntity && DroneCrateItem.packed(stack);
    }
    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void singulo$valid(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (singulo$blocked(stack)) result.setReturnValue(false);
    }
    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void singulo$insert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> result) {
        if (singulo$blocked(stack)) result.setReturnValue(stack);
    }
}
