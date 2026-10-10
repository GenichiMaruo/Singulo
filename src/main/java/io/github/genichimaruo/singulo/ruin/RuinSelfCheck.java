package io.github.genichimaruo.singulo.ruin;

import com.mojang.datafixers.util.Pair;
import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * 開発用の自己点検。-Dsingulo.ruinSelfCheck=true（./gradlew runServer -PruinSelfCheck=true）で起動すると、
 * 4種類の遺構をそれぞれ探し、その周りのチャンクを生成して保管庫が置かれたかを確かめ、結果をログに出して止まる。
 */
public final class RuinSelfCheck {
    static final String[] RUINS = {"observation_post", "research_building", "culture_facility", "final_lab"};
    static final int SEARCH_RADIUS_CHUNKS = 160;

    private RuinSelfCheck() {}

    public static boolean enabled() {
        return Boolean.getBoolean("singulo.ruinSelfCheck");
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        int ok = 0;
        for (String ruin : RUINS) {
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Singulo.id(ruin));
            Holder<Structure> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level, HolderSet.direct(holder), BlockPos.ZERO, SEARCH_RADIUS_CHUNKS, false);
            if (found == null) {
                Singulo.LOGGER.error("[ruin-check] {}: not found within {} chunks", ruin, SEARCH_RADIUS_CHUNKS);
                continue;
            }
            BlockPos pos = found.getFirst();
            BlockPos cachePos = findCache(level, pos, ruin);
            if (cachePos == null) {
                Singulo.LOGGER.error("[ruin-check] {}: located at {} but no cache was generated", ruin, pos);
            } else if ("final_lab".equals(ruin) && !finalLabReady(level, cachePos)) {
                Singulo.LOGGER.error("[ruin-check] {}: cache at {} is not sealed or has no seal console nearby", ruin, cachePos);
            } else if (GuardianCoreBlockEntity.bossFor(ruin) != null && !bossRoomReady(level, cachePos)) {
                Singulo.LOGGER.error("[ruin-check] {}: cache at {} is not sealed or has no guardian core nearby", ruin, cachePos);
            } else {
                ok++;
                Singulo.LOGGER.info("[ruin-check] {}: OK, located at {}, cache at {} ({} blocks from origin){}",
                        ruin, pos, cachePos, (int) Math.sqrt(cachePos.distSqr(BlockPos.ZERO)),
                        "final_lab".equals(ruin) ? ", vault sealed, seal console present"
                                : GuardianCoreBlockEntity.bossFor(ruin) != null ? ", vault sealed, guardian core present" : "");
            }
        }
        Singulo.LOGGER.info("[ruin-check] {}/{} ruins generated with a cache", ok, RUINS.length);
        server.halt(false);
    }

    /** 最終実験施設: 保管庫が封鎖された状態で生成され、近くに封印コンソールがあるか。 */
    private static boolean finalLabReady(ServerLevel level, BlockPos cachePos) {
        if (!(level.getBlockEntity(cachePos) instanceof RuinCacheBlockEntity cache) || !cache.isSealed()) {
            return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(cachePos.offset(-4, -2, -4), cachePos.offset(4, 2, 4))) {
            if (level.getBlockEntity(p) instanceof SealConsoleBlockEntity) {
                return true;
            }
        }
        return false;
    }

    /** ボス部屋: 保管庫が封鎖された状態で生成され、近くに番人の封印核があるか。 */
    private static boolean bossRoomReady(ServerLevel level, BlockPos cachePos) {
        if (!(level.getBlockEntity(cachePos) instanceof RuinCacheBlockEntity cache) || !cache.isSealed()) {
            return false;
        }
        int r = GuardianCoreBlockEntity.VAULT_SEARCH_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(cachePos.offset(-r, -8, -r), cachePos.offset(r, 4, r))) {
            if (level.getBlockEntity(p) instanceof GuardianCoreBlockEntity) {
                return true;
            }
        }
        return false;
    }

    /** 見つかった位置の周り（±3チャンク）を生成し、その遺構の保管庫を探す。 */
    private static BlockPos findCache(ServerLevel level, BlockPos center, String ruin) {
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof RuinCacheBlockEntity cache && ruin.equals(cache.ruin())) {
                        return be.getBlockPos();
                    }
                }
            }
        }
        return null;
    }
}
