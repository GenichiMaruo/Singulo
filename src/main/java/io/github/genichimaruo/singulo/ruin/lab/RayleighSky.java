package io.github.genichimaruo.singulo.ruin.lab;

import it.unimi.dsi.fastutil.longs.*;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;

/** Local, clear-noon skylight. Values never retain their Level and never load chunks. */
public final class RayleighSky {
    private static final Map<Level, Field> FIELDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Direction[] DIRECTIONS = Direction.values();
    private RayleighSky() {}

    /** Published immutable field for renderer workers. Does not access chunks or a light engine. */
    public static LightSnapshot snapshot(Level level) {
        Field field = FIELDS.get(level);
        return field == null ? LightSnapshot.EMPTY : field.snapshot;
    }

    public static final class LightSnapshot {
        private static final LightSnapshot EMPTY = new LightSnapshot(new Long2ByteOpenHashMap());
        private final Long2ByteOpenHashMap light;
        private final LongSet nearbySections = new LongOpenHashSet();

        private LightSnapshot(Long2ByteOpenHashMap light) {
            this.light = light;
            LongSet sections = new LongOpenHashSet();
            for (long pos : light.keySet()) sections.add(SectionPos.blockToSection(pos));
            for (long section : sections) {
                for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                    nearbySections.add(SectionPos.asLong(SectionPos.x(section) + x, SectionPos.y(section) + y, SectionPos.z(section) + z));
                }
            }
        }

        public int light(int x, int y, int z) { return light.get(BlockPos.asLong(x, y, z)); }
        public boolean near(BlockPos pos) { return nearbySections.contains(SectionPos.asLong(pos)); }
    }

    public static void register(Level level, BlockPos pos) {
        Field field = FIELDS.computeIfAbsent(level, ignored -> new Field());
        synchronized (field) {
            if (level.isClientSide) field.ownerThread = Thread.currentThread();
            if (field.panels.add(pos.asLong())) { index(field, pos, 1); field.dirty = true; }
        }
    }
    public static void unregister(Level level, BlockPos pos) {
        Field field = FIELDS.get(level);
        if (field != null) synchronized (field) {
            if (field.panels.remove(pos.asLong())) { index(field, pos, -1); field.dirty = true; }
        }
    }
    public static void forgetLevel(Level level) { FIELDS.remove(level); }

    /** Called by the light engine, including opacity, shape and LIT changes. Batch edits share one rebuild. */
    public static void changed(Level level, BlockPos pos) {
        Field field = FIELDS.get(level);
        if (field == null) return;
        synchronized (field) {
            if (field.affectedChunks.containsKey(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) field.dirty = true;
        }
    }

    private static void index(Field field, BlockPos panel, int delta) {
        for (int x = (panel.getX() - 15) >> 4; x <= (panel.getX() + 15) >> 4; x++)
            for (int z = (panel.getZ() - 15) >> 4; z <= (panel.getZ() + 15) >> 4; z++) {
                long key = ChunkPos.asLong(x, z);
                int count = field.affectedChunks.get(key) + delta;
                if (count == 0) field.affectedChunks.remove(key); else field.affectedChunks.put(key, count);
            }
    }

    /** Chunk borders can open/close diffusion paths even when the source chunk stays loaded. */
    public static void chunkChanged(Level level, ChunkPos pos) {
        Field field = FIELDS.get(level);
        if (field != null) synchronized (field) {
            if (field.affectedChunks.containsKey(pos.toLong())) field.dirty = true;
        }
    }

    public static int light(Level level, BlockPos pos) {
        Field field = FIELDS.get(level);
        if (field == null) return 0;
        // Render/light workers only read a completed immutable snapshot. Never make a server
        // light worker wait for main-thread chunk access while holding the field lock.
        if (field.dirty && (level.isClientSide ? field.ownerThread == Thread.currentThread()
                : level.getServer() != null && level.getServer().isSameThread())) {
            synchronized (field) { refresh(level, field); }
        }
        return field.light.get(pos.asLong());
    }

    public static boolean sunlit(Level level, BlockPos pos) { return light(level, pos) == 15; }

    /** Changed sections are consumed on the client thread; no client classes are referenced on the server. */
    public static LongSet takeChangedSections(Level level) {
        Field field = FIELDS.get(level);
        if (field == null) return LongSets.EMPTY_SET;
        synchronized (field) {
            refresh(level, field);
            LongSet changed = field.changedSections;
            field.changedSections = new LongOpenHashSet();
            return changed;
        }
    }

    private static void refresh(Level level, Field field) {
        if (!field.dirty || field.building) return;
        field.building = true;
        try {
            Long2ByteOpenHashMap light = new Long2ByteOpenHashMap();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            // Vanilla skylight seeds every unobstructed cell in each source column at 15.
            // Only subsequent diffusion loses at least one level per step, including downward steps.
            for (long panel : field.panels) {
                pos.set(panel);
                if (!level.hasChunkAt(pos) || !RayleighPanelBlock.lit(level, pos)) continue;
                BlockState previous = null;
                for (int y = pos.getY() - 1; y >= level.getMinBuildHeight(); y--) {
                    pos.setY(y);
                    BlockState state = level.getBlockState(pos);
                    if (state.getLightBlock(level, pos) != 0 || occludes(level, pos.above(), previous, pos, state, Direction.DOWN)) break;
                    if (light.put(pos.asLong(), (byte) 15) != 15) queue.enqueue(pos.asLong());
                    previous = state;
                }
            }
            BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
            while (!queue.isEmpty()) {
                long packed = queue.dequeueLong();
                int strength = light.get(packed);
                if (strength <= 1) continue;
                pos.set(packed);
                BlockState state = level.getBlockState(pos);
                for (Direction direction : DIRECTIONS) {
                    next.setWithOffset(pos, direction);
                    if (level.isOutsideBuildHeight(next) || !level.hasChunkAt(next)) continue;
                    long key = next.asLong();
                    if (light.get(key) >= strength - 1) continue;
                    BlockState neighbor = level.getBlockState(next);
                    int candidate = strength - Math.max(1, neighbor.getLightBlock(level, next));
                    if (candidate > light.get(key) && !occludes(level, pos, state, next, neighbor, direction)) {
                        light.put(key, (byte) candidate);
                        queue.enqueue(key);
                    }
                }
            }
            if (level.isClientSide) {
                for (var entry : field.light.long2ByteEntrySet()) {
                    if (entry.getByteValue() != light.get(entry.getLongKey())) field.changedSections.add(SectionPos.blockToSection(entry.getLongKey()));
                }
                for (var entry : light.long2ByteEntrySet()) {
                    if (entry.getByteValue() != field.light.get(entry.getLongKey())) field.changedSections.add(SectionPos.blockToSection(entry.getLongKey()));
                }
            }
            field.light = light;
            field.snapshot = new LightSnapshot(light);
            field.dirty = false;
        } finally { field.building = false; }
    }

    private static boolean occludes(Level level, BlockPos from, BlockState source, BlockPos to, BlockState target, Direction direction) {
        // The panel emits through its underside, regardless of its own solid model.
        return Shapes.faceShapeOccludes(source == null ? Shapes.empty() : LightEngine.getOcclusionShape(level, from, source, direction),
                LightEngine.getOcclusionShape(level, to, target, direction.getOpposite()));
    }

    private static final class Field {
        final LongSet panels = new LongOpenHashSet();
        final Long2IntOpenHashMap affectedChunks = new Long2IntOpenHashMap();
        Thread ownerThread;
        volatile Long2ByteOpenHashMap light = new Long2ByteOpenHashMap();
        volatile LightSnapshot snapshot = LightSnapshot.EMPTY;
        LongSet changedSections = new LongOpenHashSet();
        volatile boolean dirty = true;
        boolean building;
    }
}
