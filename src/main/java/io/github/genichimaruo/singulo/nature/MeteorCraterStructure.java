package io.github.genichimaruo.singulo.nature;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.registry.SinguloWorldgen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * 隕石クレーター。型紙を置くのではなく、置く場所の地形の高さを1列ずつ読んで、すり鉢・縁・飛び散った土を地面に描き込む
 * （MeteorCraterPiece）。水の上と、急な斜面には作らない。
 */
public class MeteorCraterStructure extends Structure {
    public static final com.mojang.serialization.Codec<MeteorCraterStructure> CODEC = simpleCodec(MeteorCraterStructure::new);
    /** 中心の周りで調べる距離と、許す高さの差（これより急な斜面には作らない）。 */
    static final int SLOPE_REACH = 7;
    static final int MAX_SLOPE = 5;

    public MeteorCraterStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos cp = context.chunkPos();
        int x = cp.getMinBlockX() + context.random().nextInt(16);
        int z = cp.getMinBlockZ() + context.random().nextInt(16);
        ChunkGenerator gen = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        RandomState state = context.randomState();
        int ground = dryGround(gen, x, z, height, state);
        if (ground == Integer.MIN_VALUE) {
            return Optional.empty();
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int sx = x + (int) Math.round(Math.cos(a) * SLOPE_REACH);
            int sz = z + (int) Math.round(Math.sin(a) * SLOPE_REACH);
            int g = dryGround(gen, sx, sz, height, state);
            if (g == Integer.MIN_VALUE || Math.abs(g - ground) > MAX_SLOPE) {
                return Optional.empty();
            }
        }
        long seed = context.random().nextLong();
        BlockPos center = new BlockPos(x, ground, z);
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new MeteorCraterPiece(center, seed))));
    }

    /** 地面のいちばん上のブロックの高さ。上に水があれば Integer.MIN_VALUE。 */
    private static int dryGround(ChunkGenerator gen, int x, int z, LevelHeightAccessor height, RandomState state) {
        int surface = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, state);
        int floor = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, state);
        return surface == floor ? floor : Integer.MIN_VALUE;
    }

    @Override
    public StructureType<?> type() {
        return SinguloWorldgen.METEOR_CRATER.get();
    }
}
