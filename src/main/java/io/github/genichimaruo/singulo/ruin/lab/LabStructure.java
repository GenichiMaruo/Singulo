package io.github.genichimaruo.singulo.ruin.lab;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.registry.SinguloWorldgen;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * 地下アンドロイド研究所。大きさのちがう部屋の型紙（data/singulo/structure/lab/*.nbt）を、狭い廊下でつないでランダムに並べ
 * （{@link LabLayout}）、全体を耐圧殻で包み、出入口を開け、エントランスの真上から地表まで崩れた主立坑を通す。
 * 主立坑はチャンクの真ん中に立つ（構造物の置き方の locate_offset が [8, 0, 8] なので、探索コンパスは主立坑を指す）。
 * 水の上には作らない（主立坑から水が流れ込まないように）。
 */
public class LabStructure extends Structure {
    public static final MapCodec<LabStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i),
            Codec.INT.fieldOf("floor_min").forGetter(s -> s.floorMin),
            Codec.INT.fieldOf("floor_max").forGetter(s -> s.floorMax)).apply(i, LabStructure::new));
    /** ボス部屋の大きさ（31×15×31）とエントランスの高さ。 */
    public static final int BOSS_SIZE = 31, BOSS_H = 19, ENTRANCE_H = 9;
    /** ボス部屋をほかの部屋より深く置く分（台地が出入口と同じ高さになり、溶岩の溝がその下に来る）。 */
    public static final int BOSS_DEPTH = 4;
    /** 耐圧殻の厚さと、施設の天井の上の高さ（ボス部屋の天井 + 2）。 */
    public static final int HULL = 2, HULL_TOP = BOSS_H - BOSS_DEPTH + 1;
    /** 地表がこれより低いと作らない（主立坑が短すぎる）。 */
    static final int MIN_SHAFT = 24;

    final int floorMin, floorMax;

    public LabStructure(StructureSettings settings, int floorMin, int floorMax) {
        super(settings);
        this.floorMin = floorMin;
        this.floorMax = floorMax;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos cp = context.chunkPos();
        int sx = cp.getMinBlockX() + 8;
        int sz = cp.getMinBlockZ() + 8;
        int surface = context.chunkGenerator().getFirstOccupiedHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        int ground = context.chunkGenerator().getFirstOccupiedHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());
        if (surface != ground) {
            return Optional.empty();                  // 水の上
        }
        RandomSource r = context.random();
        int floor = floorMin + r.nextInt(Math.max(1, floorMax - floorMin + 1));
        if (surface < floor + MIN_SHAFT) {
            return Optional.empty();
        }
        long seed = r.nextLong();
        StructureTemplateManager templates = context.structureTemplateManager();
        return Optional.of(new GenerationStub(new BlockPos(sx, floor, sz),
                builder -> addPieces(builder, templates, seed, sx, sz, floor, surface)));
    }

    /** 部屋の並びを決め、部品（耐圧殻・部屋・廊下・出入口・主立坑）をこの順に並べる。 */
    public static void addPieces(StructurePiecesBuilder builder, StructureTemplateManager templates, long seed, int sx, int sz, int floor,
                                 int surface) {
        LabLayout layout = LabLayout.create(seed, name -> templates.getOrCreate(LabPieces.Room.id(name)).getSize(), sx, sz, floor);
        BoundingBox facility = layout.bounds(HULL);
        List<BoundingBox> hollow = new ArrayList<>();
        for (LabLayout.Placed p : layout.rooms) {
            hollow.add(p.box());
        }
        hollow.addAll(layout.corridorBoxes());
        builder.addPiece(new LabPieces.Hull(facility, hollow));
        for (LabLayout.Placed p : layout.rooms) {
            builder.addPiece(new LabPieces.Room(templates, p.name(), new BlockPos(p.box().minX(), p.box().minY(), p.box().minZ()), p.rotation(), facility));
        }
        for (LabLayout.Corridor c : layout.corridors) {
            builder.addPiece(new LabPieces.Corridor(c.points(), floor));
        }
        builder.addPiece(new LabPieces.Doors(LabPieces.Doors.walls(layout), floor));
        builder.addPiece(new LabPieces.Shaft(sx, sz, floor, surface, seed));
    }

    @Override
    public StructureType<?> type() {
        return SinguloWorldgen.ANDROID_LAB.get();
    }
}
