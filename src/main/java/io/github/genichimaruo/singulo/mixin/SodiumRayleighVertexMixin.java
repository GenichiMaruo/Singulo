package io.github.genichimaruo.singulo.mixin;

import me.jellysquid.mods.sodium.client.gl.attribute.*;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.*;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Compact vertices already retain all 32 light bits. Give uncompressed vertices a separate sunlight word. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.VanillaLikeChunkVertex", remap = false)
public abstract class SodiumRayleighVertexMixin {
    @Unique private static final GlVertexFormat<ChunkMeshAttribute> SINGULO_FORMAT = GlVertexFormat.builder(ChunkMeshAttribute.class, 32)
            .addElement(ChunkMeshAttribute.POSITION_MATERIAL_MESH, 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement(ChunkMeshAttribute.COLOR_SHADE, 12, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
            .addElement(ChunkMeshAttribute.BLOCK_TEXTURE, 16, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .addElement(ChunkMeshAttribute.LIGHT_TEXTURE, 24, GlVertexAttributeFormat.UNSIGNED_INT, 2, false, true).build();

    @Inject(method = "getVertexFormat", at = @At("RETURN"), cancellable = true)
    private void singulo$format(CallbackInfoReturnable<GlVertexFormat<ChunkMeshAttribute>> ci) { ci.setReturnValue(SINGULO_FORMAT); }

    @Inject(method = "getEncoder", at = @At("RETURN"), cancellable = true)
    private void singulo$carrySunlight(CallbackInfoReturnable<ChunkVertexEncoder> ci) {
        ChunkVertexEncoder original = ci.getReturnValue();
        ci.setReturnValue((ptr, material, vertex, section) -> {
            original.write(ptr, material, vertex, section);
            MemoryUtil.memPutInt(ptr + 28, vertex.light >>> 24);
            return ptr + 32;
        });
    }
}
