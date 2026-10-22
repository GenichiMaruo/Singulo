package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.RayleighLighting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use the lighter's existing context, which reset() clears; retain no additional world reference. */
@Mixin(QuadLighter.class)
public abstract class RayleighQuadLightMixin {
    @Shadow private BlockAndTintGetter level;
    @Shadow private BlockPos pos;
    @Shadow @Final private int[] lightmap;
    @Shadow @Final private float[][] positions;
    @Shadow @Final private byte[][] normals;
    @Inject(method = "computeLightingForQuad([IZ)V", at = @At("RETURN"))
    private void singulo$localNoon(int[] vertices, boolean shade, CallbackInfo ci) {
        double x = 0, y = 0, z = 0;
        for (int i = 0; i < 4; i++) { x += positions[i][0]; y += positions[i][1]; z += positions[i][2]; }
        BlockPos sample = BlockPos.containing(pos.getX() + x / 4 + normals[0][0] / 127000.0,
                pos.getY() + y / 4 + normals[0][1] / 127000.0, pos.getZ() + z / 4 + normals[0][2] / 127000.0);
        for (int i = 0; i < 4; i++) lightmap[i] = RayleighLighting.tag(level, sample, lightmap[i]);
    }
}
