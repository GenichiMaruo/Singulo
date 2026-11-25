package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.registry.SinguloWorldgen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

/**
 * 地上の遺構（singulo:graded）。中の構造物（structure。地表観測拠点の jigsaw）を置いたあと、周りの地面をならす。
 * <p>
 * 地形の調整（beard_thin）は、型紙の箱の下を盛り、上を削るが、斜面では削りきれない。型紙の中で何も置かないマス
 * （八角形の建物の四隅や、すき間）には元の地面が残り、建物に土が盛られたように見える。そこで、置いたあとに、
 * 箱の中は床の1段下より上、箱の外は箱から離れるほど1段ずつ高くなる斜面より上にある自然の地面（土・石・砂など）を取り除く。
 * 取り除いた列の上が土になったら、元の表面（草ブロックなど）に戻す。型紙のブロックや水には触れない。
 * <p>
 * 地形の調整があると、構造物は箱から12マス先のチャンクまで置かれる（{@link Structure#adjustBoundingBox}）ので、
 * ならす範囲（margin）は12マスまで。
 */
public class GradedStructure extends Structure {
    public static final MapCodec<GradedStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Structure.DIRECT_CODEC.fieldOf("structure").forGetter(GradedStructure::inner),
            Codec.intRange(0, 12).optionalFieldOf("margin", 12).forGetter(GradedStructure::margin)
    ).apply(i, GradedStructure::new));

    private final Structure inner;
    private final int margin;

    public GradedStructure(StructureSettings settings, Structure inner, int margin) {
        super(settings);
        this.inner = inner;
        this.margin = margin;
    }

    public Structure inner() {
        return inner;
    }

    public int margin() {
        return margin;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return inner.findValidGenerationPoint(context);
    }

    @Override
    public void afterPlace(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                           BoundingBox chunkBox, ChunkPos chunkPos, PiecesContainer pieces) {
        if (pieces.isEmpty()) {
            return;
        }
        BoundingBox box = pieces.calculateBoundingBox();
        int floor = pieces.pieces().get(0).getBoundingBox().minY();
        gradeAround(level, box, floor, margin, chunkBox, Heightmap.Types.WORLD_SURFACE_WG);
    }

    /**
     * box の周り margin マスまでのうち area に入る列をならす。floor は建物の床の高さ（型紙のいちばん下の段）。
     * heightmap は、列のいちばん上の地面を探しはじめる高さ。
     */
    public static void gradeAround(WorldGenLevel level, BoundingBox box, int floor, int margin, BoundingBox area,
                                   Heightmap.Types heightmap) {
        int x0 = Math.max(area.minX(), box.minX() - margin), x1 = Math.min(area.maxX(), box.maxX() + margin);
        int z0 = Math.max(area.minZ(), box.minZ() - margin), z1 = Math.min(area.maxZ(), box.maxZ() + margin);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = Math.max(0, Math.max(box.minX() - x, x - box.maxX()));
                int dz = Math.max(0, Math.max(box.minZ() - z, z - box.maxZ()));
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > margin) {
                    continue;
                }
                grade(level, pos, x, z, allowedTop(floor, d), level.getHeight(heightmap, x, z));
            }
        }
    }

    /** 箱から水平に d 離れた列で、地面のいちばん上に許す高さ。箱のすぐ外の1列までは床の1段下、その先は1マスごとに1段。 */
    static int allowedTop(int floor, double d) {
        return floor - 1 + Math.max(0, (int) Math.ceil(d) - 1);
    }

    /** (x, z) の列で、surface（いちばん上のブロックの1つ上）から下へ、top より上の自然の地面を取り除き、上に出た土を元の表面に戻す。 */
    private static void grade(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, int surface) {
        if (surface <= top + 1) {
            return;
        }
        Block cover = null;
        for (int y = surface - 1; y > top; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!isTerrain(state)) {
                continue;
            }
            if (cover == null && (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || (state.is(BlockTags.DIRT) && !state.is(Blocks.DIRT)))) {
                cover = state.getBlock();
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        if (cover != null && level.getBlockState(pos.set(x, top, z)).is(Blocks.DIRT)
                && level.getBlockState(pos.set(x, top + 1, z)).isAir()) {
            level.setBlock(pos.set(x, top, z), cover.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 地形として生まれた地面か（土・石・砂・砂利・テラコッタ・雪など。水などの液体は除く）。 */
    static boolean isTerrain(BlockState state) {
        return state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES) && state.getFluidState().isEmpty();
    }

    @Override
    public StructureType<?> type() {
        return SinguloWorldgen.GRADED.get();
    }
}
