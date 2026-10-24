package io.github.genichimaruo.singulo.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumRayleighShaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private static void singulo$sampleNoon(ResourceLocation name, CallbackInfoReturnable<String> ci) {
        if (!name.getNamespace().equals("sodium") || !name.getPath().equals("blocks/block_layer_opaque.vsh")) return;
        String original = "v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);";
        String replacement = """
                float singuloHeight = float(textureSize(u_LightTex, 0).y);
                vec4 singuloLight = texture(u_LightTex, _vert_tex_light_coord * vec2(1.0, 16.0 / singuloHeight));
                uint singuloSun = (a_Position.x >> 30u) | ((a_Position.y >> 30u) << 2u) | ((a_LightAndData.z >> 4u) << 4u);
                if (singuloSun != 0u) {
                    vec4 noon = texture(u_LightTex, vec2(_vert_tex_light_coord.x,
                        (256.0 + clamp(float(singuloSun) / 16.0, 0.5, 15.5)) / singuloHeight));
                    singuloLight = max(singuloLight, noon);
                }
                v_Color = _vert_color * singuloLight;
                """;
        ci.setReturnValue(ci.getReturnValue().replace(original, replacement));
    }
}
