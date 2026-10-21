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
 * （{@link LabLayout}）、部屋と廊下を厚さ2の耐圧殻で包み、出入口を開け、エントランスの真上から地表まで崩れた主立坑を通す。
 * 主立坑の上の地表には、研究所の入口だった白い小さな建物（受付・入場ゲート・エレベーターホール）が残る。
 * 専用の貨物昇降室の天井からは、地表まで搬入出の立坑（内のり3×3）が必ず通り、部屋の手前と地表の1段下で溶接されて
 * ふさがれている（ボスを倒すと掘れる）。
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
        LabLayout initial = LabLayout.create(seed, name -> templates.getOrCreate(LabPieces.Room.id(name)).getSize(), sx, sz, floor);
        var freight = accessRoom(initial, sx, sz);
        int fx = freight.box().getCenter().getX(), fz = freight.box().getCenter().getZ();
        int mouth = context.chunkGenerator().getFirstOccupiedHeight(fx, fz, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        int freightGround = context.chunkGenerator().getFirstOccupiedHeight(fx, fz, Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());
        if (mouth != freightGround) return Optional.empty();
        // Lower the whole facility together on slopes, preserving room/corridor connections.
        int adjustedFloor = floorForShafts(floor, surface, mouth, freight.box().getYSpan());
        if (adjustedFloor < floorMin || adjustedFloor - BOSS_DEPTH - HULL <= context.heightAccessor().getMinBuildHeight()) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(new BlockPos(sx, adjustedFloor, sz),
                builder -> addPieces(builder, templates, seed, sx, sz, adjustedFloor, surface,
                        (x, z) -> context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                                context.heightAccessor(), context.randomState()))));
    }

    /** 地表の高さがどこも主立坑と同じとみなして並べる（テスト用）。 */
    public static void addPieces(StructurePiecesBuilder builder, StructureTemplateManager templates, long seed, int sx, int sz, int floor,
                                 int surface) {
        addPieces(builder, templates, seed, sx, sz, floor, surface, (x, z) -> surface);
    }

    /** Highest facility floor that leaves adequate depth for both shafts. */
    public static int floorForShafts(int requested, int mainSurface, int freightSurface, int freightHeight) {
        return Math.min(requested, Math.min(mainSurface - MIN_SHAFT,
                freightSurface - (freightHeight - 1) - HULL - LabPieces.AccessShaft.MIN_DEPTH));
    }

    /** The mandatory cargo lift room is the only destination of the supply shaft. */
    public static LabLayout.Placed accessRoom(LabLayout layout, int sx, int sz) {
        return layout.rooms.stream().filter(p -> p.name().equals(LabLayout.FREIGHT_LIFT)).findFirst().orElseThrow();
    }

    /**
     * 部屋の並びを決め、部品（耐圧殻・部屋・廊下・出入口・搬入出の立坑・地表の建物・主立坑）をこの順に並べる。
     * surfaceAt は地表の高さ（搬入出の立坑の口を決める）。
     */
    public static void addPieces(StructurePiecesBuilder builder, StructureTemplateManager templates, long seed, int sx, int sz, int floor,
                                 int surface, java.util.function.IntBinaryOperator surfaceAt) {
        LabLayout layout = LabLayout.create(seed, name -> templates.getOrCreate(LabPieces.Room.id(name)).getSize(), sx, sz, floor);
        BoundingBox facility = layout.bounds(HULL);
        List<BoundingBox> hollow = new ArrayList<>();
        for (LabLayout.Placed p : layout.rooms) {
            hollow.add(p.box());
        }
        hollow.addAll(layout.corridorBoxes());
        builder.addPiece(new LabPieces.Hull(facility, hollow));
        // Every accepted facility has a main shaft and a shaft over its dedicated logistics room.
        LabLayout.Placed room = accessRoom(layout, sx, sz);
        int cx = room.box().getCenter().getX(), cz = room.box().getCenter().getZ();
        int top = room.box().maxY(), mouth = surfaceAt.applyAsInt(cx, cz);
        if (surface < floor + MIN_SHAFT || mouth < top + HULL + LabPieces.AccessShaft.MIN_DEPTH) {
            throw new IllegalArgumentException("Lab requires sufficient terrain depth for both shafts");
        }
        BoundingBox access = new BoundingBox(cx - 2, top, cz - 2, cx + 2, mouth, cz + 2);
        for (LabLayout.Placed p : layout.rooms) {
            builder.addPiece(new LabPieces.Room(templates, p.name(), new BlockPos(p.box().minX(), p.box().minY(), p.box().minZ()), p.rotation(),
                    facility, access));
        }
        for (LabLayout.Corridor c : layout.corridors) {
            builder.addPiece(new LabPieces.Corridor(c.points(), floor));
        }
        builder.addPiece(new LabPieces.Doors(LabPieces.Doors.walls(layout), floor));
        builder.addPiece(new LabPieces.AccessShaft(access));
        builder.addPiece(new LabPieces.Entrance(sx, sz, surface, seed));
        builder.addPiece(new LabPieces.Shaft(sx, sz, floor, surface, seed));
    }

    @Override
    public StructureType<?> type() {
        return SinguloWorldgen.ANDROID_LAB.get();
    }
}
