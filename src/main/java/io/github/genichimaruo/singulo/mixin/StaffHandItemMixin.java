package io.github.genichimaruo.singulo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.genichimaruo.singulo.client.StaffTipTracker;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class StaffHandItemMixin {
    @Inject(method="renderItem",at=@At("HEAD"))
    private void singulo$beginItem(LivingEntity entity, ItemStack stack, ItemDisplayContext display, PoseStack pose,
                                   SubmitNodeCollector collector, int light, CallbackInfo ci) {
        StaffTipTracker.beginItem(stack,display);
    }
    @Inject(method="renderItem",at=@At("RETURN"))
    private void singulo$endItem(LivingEntity entity, ItemStack stack, ItemDisplayContext display, PoseStack pose,
                                 SubmitNodeCollector collector, int light, CallbackInfo ci) { StaffTipTracker.endItem(); }
}
