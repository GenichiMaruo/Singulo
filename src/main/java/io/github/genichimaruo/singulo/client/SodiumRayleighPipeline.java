package io.github.genichimaruo.singulo.client;

import me.jellysquid.mods.sodium.client.model.light.*;
import me.jellysquid.mods.sodium.client.model.light.data.*;
import me.jellysquid.mods.sodium.client.model.light.flat.FlatLightPipeline;
import me.jellysquid.mods.sodium.client.model.light.smooth.SmoothLightPipeline;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Run Sodium's own AO/interpolation for both sky contributions, with the same block geometry. */
public final class SodiumRayleighPipeline implements LightPipeline {
    private final LightDataAccess source;
    private final LightMode mode;
    private final LightPipeline natural;
    private final QuadLightData noon = new QuadLightData();
    private LightPipeline artificial;
    private long epoch = Long.MIN_VALUE;

    public SodiumRayleighPipeline(LightDataAccess source, LightMode mode, LightPipeline natural) {
        this.source = source;
        this.mode = mode;
        this.natural = natural;
    }

    @Override
    public void calculate(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cull, Direction face, boolean shade) {
        natural.calculate(quad, pos, out, cull, face, shade);
        if (!(source.getWorld() instanceof SodiumRayleighView view)) return;
        var snapshot = view.singulo$sunlight();
        if (epoch != view.singulo$lightEpoch()) {
            artificial = null; // release a completed job's snapshot even when the next section is unlit
            epoch = view.singulo$lightEpoch();
        }
        if (snapshot == null || !snapshot.near(pos) || LightDataAccess.unpackEM(source.get(pos))) return;
        if (artificial == null) {
            LightDataAccess cache = new LightDataAccess() {
                { world = source.getWorld(); }
                @Override public int get(int x, int y, int z) {
                    int word = source.get(x, y, z);
                    int sun = unpackFO(word) && unpackLU(word) == 0 ? 0 : snapshot.light(x, y, z);
                    // Emissivity is already handled by the natural pass; it must not
                    // invent artificial skylight in an otherwise unlit neighboring cell.
                    return (word & ~(0xF0 | packEM(true))) | packSL(sun);
                }
            };
            artificial = mode == LightMode.SMOOTH ? new SmoothLightPipeline(cache) : new FlatLightPipeline(cache);
        }
        artificial.calculate(quad, pos, noon, cull, face, shade);
        // Keep the complete interpolated 8-bit coordinate, not just a 4-bit light level.
        for (int i = 0; i < 4; i++) out.lm[i] = (out.lm[i] & 0x00FFFFFF) | ((noon.lm[i] >>> 16) & 255) << 24;
    }
    @Override public void reset() {
        natural.reset();
        if (artificial != null) artificial.reset();
    }
}
