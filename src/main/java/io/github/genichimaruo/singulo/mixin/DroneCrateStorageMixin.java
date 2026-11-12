package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.20.1 container rules lack a stack-aware item hook. Preserve the packed-crate nesting guard. */
@Mixin(net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.class)
abstract class DroneCrateStorageMixin {
    @Inject(method = "canPlaceItemThroughFace", at = @At("HEAD"), cancellable = true)
    private void singulo$guard(int slot, ItemStack stack, net.minecraft.core.Direction side, CallbackInfoReturnable<Boolean> result) {
        if (!io.github.genichimaruo.singulo.item.SinguloBlockItem.mayNest(stack)) result.setReturnValue(false);
    }
}
