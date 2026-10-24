package io.github.genichimaruo.singulo.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumRayleighShaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private static void singulo$sampleNoon(ResourceLocation name, CallbackInfoReturnable<String> ci) {
        if (!name.getNamespace().equals("sodium")) return;
        String shader = ci.getReturnValue();
        if (name.getPath().equals("include/chunk_vertex.glsl")) {
            shader = shader.replace("in uint a_LightCoord;", "in uvec2 a_LightCoord;")
                    .replace("(a_LightCoord &", "(a_LightCoord.x &")
                    .replace("(a_LightCoord >>", "(a_LightCoord.x >>");
            ci.setReturnValue(shader);
        } else if (name.getPath().equals("blocks/block_layer_opaque.vsh")) {
            String original = "v_Color = _vert_color * _sample_lightmap(u_LightTex, _vert_tex_light_coord);";
            String replacement = """
                    float singuloHeight = float(textureSize(u_LightTex, 0).y);
                    uint singuloSun;
                    #ifdef USE_VERTEX_COMPRESSION
                    singuloSun = uint(_vert_tex_light_coord.y) >> 8u;
                    #else
                    singuloSun = a_LightCoord.y;
                    #endif
                    ivec2 singuloNatural = ivec2(_vert_tex_light_coord.x, _vert_tex_light_coord.y & 255);
                    vec2 singuloUv = clamp(vec2(singuloNatural) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
                    vec4 singuloLight = texture(u_LightTex, singuloUv * vec2(1.0, 16.0 / singuloHeight));
                    if (singuloSun != 0u) {
                        vec4 noon = texture(u_LightTex, vec2(singuloUv.x,
                            (256.0 + clamp(float(singuloSun) / 16.0, 0.5, 15.5)) / singuloHeight));
                        singuloLight = max(singuloLight, noon);
                    }
                    v_Color = _vert_color * singuloLight;
                    """;
            ci.setReturnValue(shader.replace(original, replacement));
        }
    }
}
