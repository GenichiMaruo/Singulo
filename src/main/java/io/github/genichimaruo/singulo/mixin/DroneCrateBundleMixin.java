package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.20.1 container rules lack a stack-aware item hook. Preserve the packed-crate nesting guard. */
@Mixin(net.minecraft.world.item.BundleItem.class)
abstract class DroneCrateBundleMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private static void singulo$guard(ItemStack bundle, ItemStack stack, CallbackInfoReturnable<Integer> result) {
        if (DroneCrateItem.packed(stack)) result.setReturnValue(0);
    }
}
