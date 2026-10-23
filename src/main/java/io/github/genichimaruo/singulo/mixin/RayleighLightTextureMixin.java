package io.github.genichimaruo.singulo.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run the same vanilla color calculation for the additional local-noon banks. */
@Mixin(LightTexture.class)
public abstract class RayleighLightTextureMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean updateLightTexture;
    @Unique private boolean singulo$noonPass, singulo$wasDirty;
    @Unique private final int[] singulo$noonColors = new int[256];

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;<init>(IIZ)V"), index = 1)
    private int singulo$height(int height) { return height * 32; }

    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void singulo$before(float partialTick, CallbackInfo ci) {
        if (!singulo$noonPass) singulo$wasDirty = updateLightTexture;
    }
    @Inject(method = "updateLightTexture", at = @At("RETURN"))
    private void singulo$after(float partialTick, CallbackInfo ci) {
        if (!singulo$noonPass && singulo$wasDirty && minecraft.level != null) {
            singulo$noonPass = true;
            try {
                updateLightTexture = true;
                ((LightTexture) (Object) this).updateLightTexture(partialTick);
            } finally { singulo$noonPass = false; }
        }
    }
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyDarken(F)F"))
    private float singulo$daylight(ClientLevel level, float tick) { return singulo$noonPass ? 1 : level.getSkyDarken(tick); }
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyFlashTime()I"))
    private int singulo$lightning(ClientLevel level) { return singulo$noonPass ? 0 : level.getSkyFlashTime(); }
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;forceBrightLightmap()Z"))
    private boolean singulo$ordinarySun(DimensionSpecialEffects effects) { return !singulo$noonPass && effects.forceBrightLightmap(); }
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LightTexture;getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F"))
    private float singulo$brightness(DimensionType dimension, int light) {
        if (!singulo$noonPass) return LightTexture.getBrightness(dimension, light);
        float f = light / 15.0F;
        return f / (4 - 3 * f); // Clear-noon Overworld ambient light is zero.
    }
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"))
    private void singulo$pixel(NativeImage image, int x, int y, int color) { image.setPixelRGBA(x, y + (singulo$noonPass ? 16 : 0), color); }
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void singulo$upload(DynamicTexture texture) {
        if (!singulo$noonPass) return;
        NativeImage image = texture.getPixels();
        // Rows 16..31 are temporary: copy before overwriting them with the combined banks.
        int[] noon = singulo$noonColors;
        for (int sky = 0; sky < 16; sky++) for (int block = 0; block < 16; block++) noon[sky * 16 + block] = image.getPixelRGBA(block, sky + 16);
        // A separate contiguous noon ramp lets terrain use exactly the same GPU interpolation
        // as vanilla. Entity shaders retain their direct texelFetch into the combined banks.
        for (int sky = 0; sky < 16; sky++) for (int block = 0; block < 16; block++) image.setPixelRGBA(block, 256 + sky, noon[sky * 16 + block]);
        for (int artificial = 1; artificial < 16; artificial++) for (int natural = 0; natural < 16; natural++) for (int block = 0; block < 16; block++) {
            int a = image.getPixelRGBA(block, natural), b = noon[artificial * 16 + block];
            int combined = 0xFF000000;
            for (int shift = 0; shift <= 16; shift += 8) combined |= Math.max(a >>> shift & 255, b >>> shift & 255) << shift;
            image.setPixelRGBA(block, artificial * 16 + natural, combined);
        }
        texture.upload();
    }
}
