package io.github.genichimaruo.singulo.ruin.lab;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloWorldgen;
import io.github.genichimaruo.singulo.ruin.GuardianCoreBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** 地下アンドロイド研究所の部品（{@link LabStructure}）。耐圧殻 → 部屋 → 廊下 → 出入口 → 主立坑の順に描く。 */
public final class LabPieces {
    private LabPieces() {}

    private static final int SET = 2;

    private static BlockState hull(boolean sealed) {
        return SinguloBlocks.PRESSURE_HULL.get().defaultBlockState().setValue(PressureHullBlock.SEALED, sealed);
    }

    private static ListTag boxes(List<BoundingBox> list) {
        ListTag out = new ListTag();
        for (BoundingBox b : list) {
            out.add(new IntArrayTag(LabPower.write(b)));
        }
        return out;
    }

    private static List<BoundingBox> boxes(ListTag in) {
        List<BoundingBox> out = new ArrayList<>();
        for (Tag t : in) {
            out.add(LabPower.read(((IntArrayTag) t).getAsIntArray()));
        }
        return out;
    }

    // ------------------------------------------------------------------ 耐圧殻

    /** 施設全体を包む耐圧殻（部屋と廊下の中は描かない。部屋・廊下の壁と合わせて二重になる）。ボスを倒すまで封じられている。 */
    public static class Hull extends StructurePiece {
        private final List<BoundingBox> rooms;

        public Hull(BoundingBox box, List<BoundingBox> rooms) {
            super(SinguloWorldgen.LAB_HULL.get(), 0, box);
            this.rooms = rooms;
        }

        public Hull(StructurePieceSerializationContext context, CompoundTag tag) {
            super(SinguloWorldgen.LAB_HULL.get(), tag);
            this.rooms = boxes(tag.getList("rooms", Tag.TAG_INT_ARRAY));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.put("rooms", boxes(rooms));
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
            BlockState sealed = hull(true);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = Math.max(chunk.minX(), boundingBox.minX()); x <= Math.min(chunk.maxX(), boundingBox.maxX()); x++) {
                for (int z = Math.max(chunk.minZ(), boundingBox.minZ()); z <= Math.min(chunk.maxZ(), boundingBox.maxZ()); z++) {
                    for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                        p.set(x, y, z);
                        if (!inRoom(p)) {
                            level.setBlock(p, sealed, SET);
                        }
                    }
                }
            }
        }

        private boolean inRoom(BlockPos p) {
            for (BoundingBox b : rooms) {
                if (b.isInside(p)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ 部屋

    /** 部屋の型紙を、向きを変えて置く（向きを変えた型紙の角が min に来る）。動力炉の制御盤とボス部屋の封印核には、施設の範囲を書き込む。 */
    public static class Room extends StructurePiece {
        private final String name;
        private final BlockPos pos;
        private final Rotation rotation;
        private final BoundingBox facility;

        public Room(StructureTemplateManager templates, String name, BlockPos min, Rotation rotation, BoundingBox facility) {
            super(SinguloWorldgen.LAB_ROOM.get(), 0, place(templates, name, min, rotation));
            this.name = name;
            this.rotation = rotation;
            this.facility = facility;
            this.pos = origin(templates, name, min, rotation);
        }

        public Room(StructurePieceSerializationContext context, CompoundTag tag) {
            super(SinguloWorldgen.LAB_ROOM.get(), tag);
            this.name = tag.getString("name");
            this.pos = new BlockPos(tag.getInt("px"), tag.getInt("py"), tag.getInt("pz"));
            this.rotation = Rotation.values()[tag.getInt("rot") & 3];
            this.facility = LabPower.read(tag.getIntArray("facility"));
        }

        public static ResourceLocation id(String name) {
            return Singulo.id("lab/" + name);
        }

        /** 向きを変えた型紙の角が、区画の角に来る置き場所。 */
        private static BlockPos origin(StructureTemplateManager templates, String name, BlockPos cellMin, Rotation rotation) {
            StructureTemplate t = templates.getOrCreate(id(name));
            BoundingBox b = t.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), BlockPos.ZERO);
            return cellMin.offset(-b.minX(), -b.minY(), -b.minZ());
        }

        private static BoundingBox place(StructureTemplateManager templates, String name, BlockPos min, Rotation r) {
            return templates.getOrCreate(id(name)).getBoundingBox(new StructurePlaceSettings().setRotation(r), origin(templates, name, min, r));
        }

        public String name() {
            return name;
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putString("name", name);
            tag.putInt("px", pos.getX());
            tag.putInt("py", pos.getY());
            tag.putInt("pz", pos.getZ());
            tag.putInt("rot", rotation.ordinal());
            tag.putIntArray("facility", LabPower.write(facility));
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
            StructureTemplate t = level.getLevel().getStructureManager().getOrCreate(id(name));
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setBoundingBox(chunk)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
            t.placeInWorld(level, pos, pos, settings, random, SET);
            if (!name.equals(LabLayout.REACTOR) && !name.equals(LabLayout.BOSS)) {
                return;
            }
            for (BlockPos p : BlockPos.betweenClosed(Math.max(chunk.minX(), boundingBox.minX()), boundingBox.minY(),
                    Math.max(chunk.minZ(), boundingBox.minZ()), Math.min(chunk.maxX(), boundingBox.maxX()), boundingBox.maxY(),
                    Math.min(chunk.maxZ(), boundingBox.maxZ()))) {
                // ボス部屋の溶岩の滝: 型紙で置いただけでは流れ出さないので、流れを始めさせる
                if (p.getY() > boundingBox.minY() + 4 && level.getFluidState(p).isSource()
                        && level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)) {
                    level.scheduleTick(p.immutable(), net.minecraft.world.level.material.Fluids.LAVA, 1);
                }
                var be = level.getBlockEntity(p);
                if (be instanceof ReactorConsoleBlockEntity console) {
                    console.setFacility(facility);
                } else if (be instanceof GuardianCoreBlockEntity core) {
                    core.setFacility(facility);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 廊下

    /**
     * 廊下（中は幅3・高さ3）。床はタイル、壁は帯入りのパネル、天井に施設灯（4マスおき）、壁に非常灯（8マスおき、左右交互）。
     * 折れ点ごとの線分を、まず全部の壁を描いてから中をくり抜く（曲がり角もつながる）。両端は部屋の壁（出入口は Doors が開ける）。
     */
    public static class Corridor extends StructurePiece {
        private final List<BlockPos> points;
        private final int floor;

        public Corridor(List<BlockPos> points, int floor) {
            super(SinguloWorldgen.LAB_CORRIDOR.get(), 0, bounds(points, floor));
            this.points = points;
            this.floor = floor;
        }

        public Corridor(StructurePieceSerializationContext context, CompoundTag tag) {
            super(SinguloWorldgen.LAB_CORRIDOR.get(), tag);
            this.floor = tag.getInt("floor");
            this.points = new ArrayList<>();
            for (Tag t : tag.getList("points", Tag.TAG_INT_ARRAY)) {
                int[] p = ((IntArrayTag) t).getAsIntArray();
                points.add(new BlockPos(p[0], p[1], p[2]));
            }
        }

        private static BoundingBox bounds(List<BlockPos> points, int floor) {
            BoundingBox b = LabLayout.segmentBox(points.get(0), points.get(1), floor);
            for (int i = 1; i + 1 < points.size(); i++) {
                BoundingBox s = LabLayout.segmentBox(points.get(i), points.get(i + 1), floor);
                b = new BoundingBox(Math.min(b.minX(), s.minX()), b.minY(), Math.min(b.minZ(), s.minZ()), Math.max(b.maxX(), s.maxX()),
                        b.maxY(), Math.max(b.maxZ(), s.maxZ()));
            }
            return b;
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("floor", floor);
            ListTag list = new ListTag();
            for (BlockPos p : points) {
                list.add(new IntArrayTag(new int[] {p.getX(), p.getY(), p.getZ()}));
            }
            tag.put("points", list);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
            BlockState tiles = SinguloBlocks.LAB_TILES.get().defaultBlockState();
            BlockState panel = SinguloBlocks.LAB_PANEL.get().defaultBlockState();
            BlockState lined = SinguloBlocks.LAB_PANEL_LINED.get().defaultBlockState();
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState light = SinguloBlocks.FACILITY_LIGHT.get().defaultBlockState();
            BlockState alarm = SinguloBlocks.EMERGENCY_LIGHT.get().defaultBlockState();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int pass = 0; pass < 3; pass++) {
                int step = 0;
                for (int i = 0; i + 1 < points.size(); i++) {
                    BlockPos a = points.get(i), b = points.get(i + 1);
                    Direction along = Direction.getNearest(Integer.signum(b.getX() - a.getX()), 0, Integer.signum(b.getZ() - a.getZ()));
                    Direction side = along.getClockWise();
                    int len = a.distManhattan(b);
                    for (int t = 0; t <= len; t++, step++) {
                        BlockPos c = a.relative(along, t);
                        for (int l = -2; l <= 2; l++) {
                            for (int dy = 0; dy <= 4; dy++) {
                                p.set(c.getX() + side.getStepX() * l, floor + dy, c.getZ() + side.getStepZ() * l);
                                if (!chunk.isInside(p)) {
                                    continue;
                                }
                                boolean inside = Math.abs(l) <= 1 && dy >= 1 && dy <= 3;
                                if (pass == 0 && !inside) {
                                    level.setBlock(p, dy == 0 ? tiles : dy == 4 ? panel : dy == 1 ? lined : panel, SET);
                                } else if (pass == 1 && inside) {
                                    level.setBlock(p, air, SET);
                                } else if (pass == 2) {
                                    if (dy == 4 && l == 0 && step % 4 == 2) {
                                        level.setBlock(p, light, SET);
                                    } else if (dy == 3 && l == (step / 8 % 2 == 0 ? 2 : -2) && step % 8 == 5 && t > 0 && t < len) {
                                        level.setBlock(p, alarm, SET);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 出入口

    /**
     * 出入口。部屋の壁に幅3・高さ3の口を開け、まわりを構造材の枠にし、上に非常灯を付ける。
     * ボス部屋への口は隔壁でふさぐ（ボス部屋の壁は耐圧殻のまま。壊せる枠や灯を付けない）。
     */
    public static class Doors extends StructurePiece {
        /** 壁1枚ぶん: {x, z, 口の伸びる向き（0: x、1: z）, 隔壁か, ボス部屋の壁か}。 */
        private final List<int[]> walls;
        private final int floor;

        public Doors(List<int[]> walls, int floor) {
            super(SinguloWorldgen.LAB_DOOR.get(), 0, bounds(walls, floor));
            this.walls = walls;
            this.floor = floor;
        }

        public Doors(StructurePieceSerializationContext context, CompoundTag tag) {
            super(SinguloWorldgen.LAB_DOOR.get(), tag);
            this.floor = tag.getInt("floor");
            this.walls = new ArrayList<>();
            for (Tag t : tag.getList("walls", Tag.TAG_INT_ARRAY)) {
                walls.add(((IntArrayTag) t).getAsIntArray());
            }
        }

        private static BoundingBox bounds(List<int[]> walls, int floor) {
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int[] w : walls) {
                minX = Math.min(minX, w[0] - 2);
                maxX = Math.max(maxX, w[0] + 2);
                minZ = Math.min(minZ, w[1] - 2);
                maxZ = Math.max(maxZ, w[1] + 2);
            }
            return new BoundingBox(minX, floor, minZ, maxX, floor + 5, maxZ);
        }

        /** 部屋の並びから、出入口の壁を求める（廊下の両端は1枚ずつ、小部屋の直付けは2枚）。 */
        public static List<int[]> walls(LabLayout layout) {
            List<int[]> out = new ArrayList<>();
            for (LabLayout.Door d : layout.doors) {
                int axis = d.out().getAxis() == Direction.Axis.Z ? 0 : 1;
                out.add(new int[] {d.wall().getX(), d.wall().getZ(), axis, d.bulkhead() ? 1 : 0, d.hullWall() ? 1 : 0});
            }
            return out;
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("floor", floor);
            ListTag list = new ListTag();
            for (int[] w : walls) {
                list.add(new IntArrayTag(w));
            }
            tag.put("walls", list);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState frame = SinguloBlocks.LAB_FRAME.get().defaultBlockState();
            BlockState light = SinguloBlocks.EMERGENCY_LIGHT.get().defaultBlockState();
            BlockState bulkhead = SinguloBlocks.BULKHEAD.get().defaultBlockState();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int[] w : walls) {
                boolean bulk = w[3] == 1, bossWall = w[4] == 1;
                for (int t = -2; t <= 2; t++) {
                    for (int dy = 1; dy <= 4; dy++) {
                        int x = w[0] + (w[2] == 0 ? t : 0);
                        int z = w[1] + (w[2] == 1 ? t : 0);
                        p.set(x, floor + dy, z);
                        if (!chunk.isInside(p)) {
                            continue;
                        }
                        if (Math.abs(t) <= 1 && dy <= 3) {
                            level.setBlock(p, bulk ? bulkhead : air, SET);
                        } else if (!bossWall) {
                            level.setBlock(p, dy == 4 && t == 0 ? light : frame, SET);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 主立坑

    /**
     * 崩れた主立坑。エントランスの天井から地表まで、5×5 の縦穴。壁は耐圧殻（施設の殻の高さでは封じられたまま）と、崩れた構造材・岩。
     * 北の壁に、ところどころ途切れたはしご。エントランスの床には瓦礫の山と、天井まで届く足場（はしごの下）。地表の口のまわりには瓦礫。
     */
    public static class Shaft extends StructurePiece {
        private final int sx, sz, floor, surface;
        private final long seed;

        public Shaft(int sx, int sz, int floor, int surface, long seed) {
            super(SinguloWorldgen.LAB_SHAFT.get(), 0, new BoundingBox(sx - 4, floor + 1, sz - 4, sx + 4, surface + 3, sz + 4));
            this.sx = sx;
            this.sz = sz;
            this.floor = floor;
            this.surface = surface;
            this.seed = seed;
        }

        public Shaft(StructurePieceSerializationContext context, CompoundTag tag) {
            super(SinguloWorldgen.LAB_SHAFT.get(), tag);
            this.sx = tag.getInt("sx");
            this.sz = tag.getInt("sz");
            this.floor = tag.getInt("floor");
            this.surface = tag.getInt("surface");
            this.seed = tag.getLong("seed");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("sx", sx);
            tag.putInt("sz", sz);
            tag.putInt("floor", floor);
            tag.putInt("surface", surface);
            tag.putLong("seed", seed);
        }

        /** 場所ごとに決まった 0〜1 の値（チャンクの境目をまたいでも同じ）。 */
        private float noise(int x, int y, int z) {
            long h = seed ^ (x * 73428767L) ^ (y * 912931L) ^ (z * 438289L);
            return RandomSource.create(h).nextFloat();
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
            BlockState scaffold = Blocks.SCAFFOLDING.defaultBlockState();
            BlockState frame = SinguloBlocks.LAB_FRAME.get().defaultBlockState();
            BlockState[] rubble = {Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
                    SinguloBlocks.LAB_PANEL.get().defaultBlockState(), frame};
            BlockState[] lining = {hull(false), hull(false), frame, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.TUFF.defaultBlockState()};
            int ceiling = floor + LabStructure.ENTRANCE_H - 1;
            int hullTop = floor + LabStructure.HULL_TOP;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = sx - 4; x <= sx + 4; x++) {
                for (int z = sz - 4; z <= sz + 4; z++) {
                    int dx = x - sx, dz = z - sz;
                    int r = Math.max(Math.abs(dx), Math.abs(dz));
                    boolean ladderColumn = dx == 0 && dz == -2;
                    for (int y = floor + 1; y <= surface + 3; y++) {
                        p.set(x, y, z);
                        if (!chunk.isInside(p)) {
                            continue;
                        }
                        float n = noise(x, y, z);
                        if (y < ceiling) {
                            // エントランスの中: 瓦礫の山と、はしごの下の足場
                            if (r > 2) {
                                continue;
                            }
                            if (ladderColumn) {
                                level.setBlock(p, scaffold, SET);
                            } else if (y == floor + 1 && n < 0.55F || y == floor + 2 && n < 0.18F && r <= 1) {
                                level.setBlock(p, rubble[(int) (n * 97) % rubble.length], SET);
                            }
                            continue;
                        }
                        if (r <= 2) {
                            if (y > surface + 2) {
                                continue;
                            }
                            if (ladderColumn) {
                                boolean gap = y > ceiling + 3 && y < surface - 3 && n < 0.07F;
                                level.setBlock(p, y == ceiling ? scaffold : gap ? air : ladder, SET);
                            } else if (r == 2 && y > ceiling + 2 && y < surface - 2 && n < 0.04F && Math.abs(dx) + Math.abs(dz) > 2
                                    && !(dz == -2 && Math.abs(dx) <= 1)) {
                                level.setBlock(p, rubble[0], SET);              // 壁から突き出た崩れた岩
                            } else {
                                level.setBlock(p, air, SET);
                            }
                        } else if (r == 3 && y <= surface) {
                            // 立坑の壁（施設の殻の高さは封じられた耐圧殻、上は崩れた内張り）
                            boolean anchor = dx == 0 && dz == -3;
                            level.setBlock(p, y <= hullTop ? hull(true) : anchor ? hull(false) : lining[(int) (n * 131) % lining.length], SET);
                        } else if (r == 4 && y == surface && n < 0.6F) {
                            level.setBlock(p, rubble[(int) (n * 53) % 2], SET);  // 地表の口のまわりの瓦礫
                        } else if (r == 4 && Math.abs(dx) == 4 && Math.abs(dz) == 4 && y > surface && y <= surface + 2) {
                            level.setBlock(p, frame, SET);                     // 折れた柵の柱
                        }
                    }
                }
            }
        }
    }
}
