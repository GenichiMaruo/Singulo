package io.github.genichimaruo.singulo.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex", remap = false)
public abstract class SodiumRayleighVertexMixin {
    @Inject(method = "getEncoder", at = @At("RETURN"), cancellable = true)
    private void singulo$carrySunlight(CallbackInfoReturnable<ChunkVertexEncoder> ci) {
        ChunkVertexEncoder original = ci.getReturnValue();
        ci.setReturnValue((ptr, material, vertices, section) -> {
            long end = original.write(ptr, material, vertices, section);
            // Preserve all eight interpolated bits: four spare position bits and four
            // unused material bits. Stride, positions, normal light and material flags stay intact.
            for (int i = 0; i < 4; i++) {
                int sun = vertices[i].light >>> 24;
                long at = ptr + i * 20L;
                MemoryUtil.memPutInt(at, (MemoryUtil.memGetInt(at) & 0x3FFFFFFF) | (sun & 3) << 30);
                MemoryUtil.memPutInt(at + 4, (MemoryUtil.memGetInt(at + 4) & 0x3FFFFFFF) | ((sun >>> 2) & 3) << 30);
                MemoryUtil.memPutInt(at + 16, (MemoryUtil.memGetInt(at + 16) & ~0x00F00000) | (sun >>> 4) << 20);
            }
            return end;
        });
    }
}
