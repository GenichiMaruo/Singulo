package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.StaffTipTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class StaffHandProjectionMixin {
    @Shadow @Final private Projection hudProjection;
    @Inject(method="renderItemInHand",at=@At("HEAD"))
    private void singulo$beginHands(CameraRenderState camera, float tick, Matrix4fc view, CallbackInfo ci) {
        StaffTipTracker.beginHands(hudProjection.getMatrix(new Matrix4f()));
    }
    @Inject(method="renderItemInHand",at=@At("RETURN"))
    private void singulo$endHands(CameraRenderState camera, float tick, Matrix4fc view, CallbackInfo ci) {
        StaffTipTracker.endHands();
    }
}
