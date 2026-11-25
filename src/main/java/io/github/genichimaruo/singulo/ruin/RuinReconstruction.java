package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

public final class RuinReconstruction extends SavedData {
    static final List<String> FACILITIES = List.of("research_building", "culture_facility", "final_lab",
            "sunken_reactor", "android_lab");
    public static final int REVISION = 1;
    private static final long MAX_VOLUME = 4_000_000L;
    private final LongOpenHashSet used = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<CompoundTag> sealedRecovery = new Long2ObjectOpenHashMap<>();

    private static RuinReconstruction get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(RuinReconstruction::new, RuinReconstruction::load, null), "singulo_ruin_reconstruction");
    }

    private static RuinReconstruction load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new RuinReconstruction();
        for (long p : tag.getLongArray("used")) result.used.add(p);
        var recovery = tag.getList("sealed_recovery", Tag.TAG_COMPOUND);
        for (int i = 0; i < recovery.size(); i++) {
            var entry = recovery.getCompound(i);
            result.sealedRecovery.put(entry.getLong("anchor"), entry.getCompound("vault").copy());
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("used", used.toLongArray());
        if (!sealedRecovery.isEmpty()) {
            var recovery = new ListTag();
            for (var entry : sealedRecovery.long2ObjectEntrySet()) {
                var encoded = new CompoundTag();
                encoded.putLong("anchor", entry.getLongKey());
                encoded.put("vault", entry.getValue().copy());
                recovery.add(encoded);
            }
            tag.put("sealed_recovery", recovery);
        }
        return tag;
    }

    public record Plan(String facility, BlockPos anchor, BoundingBox bounds, BlockPos origin,
                       StructureTemplate template, StructurePlaceSettings settings,
                       StructureStart start, RuinCacheBlockEntity oldCache, boolean sealed) {
        public AABB area() {
            return AABB.encapsulatingFullBlocks(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                    new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        }
    }

    private static String currentTemplate(String facility) {
        return facility.equals("final_lab") || facility.equals("sunken_reactor") ? facility + "_antechamber" : facility;
    }

    public static Plan plan(ServerLevel level, BlockPos anchor) {
        return plan(level, anchor, false);
    }

    public static Plan plan(ServerLevel level, BlockPos anchor, boolean allowUncleared) {
        if (get(level).used.contains(anchor.asLong())) return null;
        var be = level.getBlockEntity(anchor);
        boolean console = be instanceof SealConsoleBlockEntity;
        if (!console && !(be instanceof GuardianCoreBlockEntity)) return null;
        if (be instanceof SealConsoleBlockEntity s && (s.bossAwake() || s.alarm().active())) return null;
        if (be instanceof GuardianCoreBlockEntity c && (c.activeBoss(level) != null || c.alarm().active())) return null;

        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String facility : FACILITIES) {
            if (console != facility.equals("final_lab")) continue;
            var structure = registry.get(Singulo.id(facility));
            if (structure == null) continue;
            var start = level.structureManager().getStructureWithPieceAt(anchor, structure);
            if (!start.isValid()) continue;

            StructureTemplate template = null;
            StructurePlaceSettings settings = null;
            BlockPos origin = null;
            BoundingBox bounds;
            BlockPos cachePos;
            if (facility.equals("android_lab")) {
                var room = start.getPieces().stream().filter(io.github.genichimaruo.singulo.ruin.lab.LabPieces.Room.class::isInstance)
                        .map(io.github.genichimaruo.singulo.ruin.lab.LabPieces.Room.class::cast)
                        .filter(r -> r.isBossAnchor(level, anchor)).findFirst().orElse(null);
                if (room == null) continue;
                bounds = start.getBoundingBox();
                cachePos = room.bossCache(level);
            } else {
                if (start.getPieces().isEmpty() || !(start.getPieces().getFirst() instanceof PoolElementStructurePiece piece)) continue;
                String currentName = currentTemplate(facility);
                template = level.getStructureManager().get(Singulo.id("ruins/" + currentName)).orElse(null);
                var old = level.getStructureManager().get(Singulo.id("ruins/" + facility)).orElse(null);
                if (template == null || old == null) continue;
                var mainBlock = console ? SinguloBlocks.SEAL_CONSOLE.get() : SinguloBlocks.GUARDIAN_CORE.get();
                settings = new StructurePlaceSettings().setRotation(piece.getRotation())
                        .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
                var originalAnchor = singleBlock(old, piece.getPosition(), settings, mainBlock);
                var originalCache = anchor.equals(originalAnchor) ? singleBlock(old, piece.getPosition(), settings, SinguloBlocks.RUIN_CACHE.get()) : null;
                if (originalCache == null && anchor.equals(singleBlock(template, piece.getPosition(), settings, mainBlock))) {
                    originalCache = singleBlock(template, piece.getPosition(), settings, SinguloBlocks.RUIN_CACHE.get());
                }
                if (originalCache == null) continue;
                cachePos = originalCache;
                var offset = singleBlock(template, BlockPos.ZERO, settings, mainBlock);
                if (offset == null) continue;
                origin = anchor.subtract(offset);
                bounds = template.getBoundingBox(settings, origin);
            }
            var startChunk = start.getChunkPos();
            if (cachePos == null || !bounds.isInside(cachePos) || !level.isLoaded(cachePos) || !validArea(level, bounds)
                    || level.getChunkSource().getChunkNow(startChunk.x, startChunk.z) == null) continue;
            if (!(level.getBlockEntity(cachePos) instanceof RuinCacheBlockEntity cache)
                    || cache.placedByPlayer() || (cache.isSealed() && !allowUncleared) || !facility.equals(cache.ruin())
                    || cache.reconstructionRevision() >= REVISION) continue;
            return new Plan(facility, anchor.immutable(), bounds, origin, template, settings, start, cache, cache.isSealed());
        }
        return null;
    }

    private static BlockPos singleBlock(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, Block block) {
        var blocks = template.filterBlocks(origin, settings, block);
        return blocks.size() == 1 ? blocks.getFirst().pos() : null;
    }

    private static boolean validArea(ServerLevel level, BoundingBox box) {
        if ((long) box.getXSpan() * box.getYSpan() * box.getZSpan() > MAX_VOLUME
                || box.minY() < level.getMinBuildHeight() || box.maxY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(new BlockPos(box.minX(), box.minY(), box.minZ()))
                || !level.getWorldBorder().isWithinBounds(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) return false;
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    public static boolean apply(ServerLevel level, Plan plan) {
        if (!validArea(level, plan.bounds) || plan.oldCache.isRemoved() || plan.oldCache.isSealed() != plan.sealed
                || plan.oldCache.reconstructionRevision() >= REVISION) return false;
        RuinReconstruction data = get(level);
        // Consume the per-world site before any block changes; a partial failure cannot be retried.
        if (!data.used.add(plan.anchor.asLong())) return false;
        data.setDirty();
        CompoundTag contents = plan.oldCache.saveWithoutMetadata(level.registryAccess());
        plan.oldCache.clearContent();
        boolean rebuilt = false;
        try {
            if (plan.template != null) {
                rebuilt = plan.template.placeInWorld(level, plan.origin, plan.origin, plan.settings,
                        RandomSource.create(plan.anchor.asLong()), Block.UPDATE_CLIENTS);
                if (rebuilt) replaceStructureStart(level, plan);
            } else {
                var b = plan.bounds;
                var r = RandomSource.create(plan.anchor.asLong());
                for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) {
                    for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++) {
                        ChunkPos cp = new ChunkPos(x, z);
                        var clip = new BoundingBox(cp.getMinBlockX(), b.minY(), cp.getMinBlockZ(),
                                cp.getMaxBlockX(), b.maxY(), cp.getMaxBlockZ());
                        plan.start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), r, clip, cp);
                    }
                }
                rebuilt = true;
            }
        } catch (RuntimeException failure) {
            rebuilt = false;
            Singulo.LOGGER.error("Partial facility reconstruction at {}; one-time reservation retained", plan.anchor, failure);
        }
        BlockPos cachePos = plan.template == null ? plan.oldCache.getBlockPos()
                : singleBlock(plan.template, plan.origin, plan.settings, SinguloBlocks.RUIN_CACHE.get());
        RuinCacheBlockEntity replacement = cachePos != null && level.getBlockEntity(cachePos) instanceof RuinCacheBlockEntity c
                ? c : !plan.oldCache.isRemoved() ? plan.oldCache : null;
        if (replacement != null) {
            var cache = replacement;
            contents.putInt("reconstruction_revision", REVISION);
            cache.loadWithComponents(contents, level.registryAccess());
            cache.setSealed(plan.sealed);
            cache.setChanged();
        } else if (plan.sealed) {
            // Do not expose a locked vault's contents if a pre-clear reconstruction fails.
            data.sealedRecovery.put(plan.anchor.asLong(), contents.copy());
            data.setDirty();
            Singulo.LOGGER.warn("Preserved sealed vault recovery data for facility at {} after partial reconstruction", plan.anchor);
            rebuilt = false;
        } else {
            // Never silently lose the previous vault inventory on a partial placement.
            var salvage = new RuinCacheBlockEntity(plan.anchor, SinguloBlocks.RUIN_CACHE.get().defaultBlockState());
            salvage.loadWithComponents(contents, level.registryAccess());
            for (int i = 0; i < salvage.getContainerSize(); i++) {
                var item = salvage.getItem(i);
                if (!item.isEmpty()) {
                    var at = plan.anchor.above().getCenter();
                    var drop = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, item);
                    drop.setDefaultPickUpDelay();
                    level.addFreshEntity(drop);
                }
            }
            rebuilt = false;
        }
        return rebuilt;
    }
    // Keep the original start chunk (so vanilla locate still finds it), but register the
    // actual new piece so compass, relay network and saved reference queries see its new bounds.
    private static void replaceStructureStart(ServerLevel level, Plan plan) {
        var structure = plan.start.getStructure();
        ChunkPos startChunk = plan.start.getChunkPos();
        var element = StructurePoolElement.single(Singulo.id("ruins/" + currentTemplate(plan.facility)).toString())
                .apply(StructureTemplatePool.Projection.RIGID);
        var piece = new PoolElementStructurePiece(level.getStructureManager(), element, plan.origin, 0,
                plan.settings.getRotation(), plan.bounds, LiquidSettings.APPLY_WATERLOGGING);
        var replacement = new StructureStart(structure, startChunk, plan.start.getReferences(),
                new PiecesContainer(List.of(piece)));
        var start = level.getChunkSource().getChunkNow(startChunk.x, startChunk.z);
        if (start == null) throw new IllegalStateException("Facility start no longer loaded");
        start.setStartForStructure(structure, replacement);
        start.setUnsaved(true);
        for (int x = plan.bounds.minX() >> 4; x <= plan.bounds.maxX() >> 4; x++) {
            for (int z = plan.bounds.minZ() >> 4; z <= plan.bounds.maxZ() >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) throw new IllegalStateException("Facility reference chunk no longer loaded");
                chunk.addReferenceForStructure(structure, startChunk.toLong());
                chunk.setUnsaved(true);
            }
        }
    }

}
