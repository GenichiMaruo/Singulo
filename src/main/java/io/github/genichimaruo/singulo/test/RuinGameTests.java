package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.ruin.GravitationalWaveDetectorBlockEntity;
import io.github.genichimaruo.singulo.ruin.GuardDockBlockEntity;
import io.github.genichimaruo.singulo.ruin.GuardianCoreBlockEntity;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlock;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity;
import io.github.genichimaruo.singulo.ruin.SecurityDrone;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 遺構（保管庫の中身と再生、警備機、重力波検出器、構造物ファイル）の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RuinGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos POS = new BlockPos(2, 1, 2);
    private static final long DAY = RuinCacheBlockEntity.TICKS_PER_DAY;

    private RuinGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    private static int count(RuinCacheBlockEntity cache, Item item) {
        int n = 0;
        for (int i = 0; i < cache.getContainerSize(); i++) {
            if (cache.getItem(i).is(item)) {
                n += cache.getItem(i).getCount();
            }
        }
        return n;
    }

    private static RuinCacheBlockEntity cache(GameTestHelper helper, String ruin) {
        helper.setBlock(POS, SinguloBlocks.RUIN_CACHE.get());
        RuinCacheBlockEntity cache = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, POS);
        cache.setRuin(ruin);
        return cache;
    }

    @GameTest(template = EMPTY)
    public static void observationPostCacheHoldsOneExpedition(GameTestHelper helper) {
        RuinCacheBlockEntity cache = cache(helper, "observation_post");
        cache.refillIfDue(helper.getLevel(), 1000);
        int logs = count(cache, item("observation_log"));
        int units = count(cache, item("degraded_control_unit"));
        helper.assertTrue(logs >= 3 && logs <= 5, "観測ログは1回の遠征で3〜5個: " + logs);
        helper.assertTrue(units >= 6 && units <= 10, "劣化した制御ユニットは6〜10個: " + units);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cacheRegeneratesAfterSevenDays(GameTestHelper helper) {
        RuinCacheBlockEntity cache = cache(helper, "observation_post");
        long t = 1000;
        cache.refillIfDue(helper.getLevel(), t);
        cache.clearContent();
        cache.refillIfDue(helper.getLevel(), t);                 // 空になったのに気づいた時刻を記録
        cache.refillIfDue(helper.getLevel(), t + 7 * DAY - 1);
        helper.assertTrue(cache.isEmpty(), "7日たつ前に再生した");
        cache.refillIfDue(helper.getLevel(), t + 7 * DAY);
        helper.assertTrue(count(cache, item("observation_log")) >= 3, "7日たっても再生しない");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void dormantSeedOnlyOnFirstFill(GameTestHelper helper) {
        RuinCacheBlockEntity cache = cache(helper, "final_lab");
        cache.refillIfDue(helper.getLevel(), 0);
        helper.assertTrue(count(cache, item("dormant_singularity_seed")) == 1, "初回に休眠した特異点の種が1個入らない");
        helper.assertTrue(count(cache, item("degraded_anomaly_sample")) >= 4, "アノマリー・サンプルが4〜6個入らない");
        cache.clearContent();
        cache.refillIfDue(helper.getLevel(), 0);
        cache.refillIfDue(helper.getLevel(), 14 * DAY);
        helper.assertTrue(count(cache, item("dormant_singularity_seed")) == 0, "再生で種がもう一度入った");
        helper.assertTrue(count(cache, item("degraded_anomaly_sample")) >= 4, "14日で再生しない");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void guardDockLaunchesTieredDrones(GameTestHelper helper) {
        helper.setBlock(POS, SinguloBlocks.RUIN_GUARD_DOCK.get());
        GuardDockBlockEntity dock = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, POS);
        dock.setTier(2);
        dock.launch(helper.getLevel());
        List<SecurityDrone> drones = helper.getLevel().getEntitiesOfClass(SecurityDrone.class,
                new AABB(helper.absolutePos(POS)).inflate(4));
        helper.assertTrue(drones.size() == 3, "段階2のドックは3機出撃する: " + drones.size());
        for (SecurityDrone d : drones) {
            helper.assertTrue(d.tier() == 2 && d.getMaxHealth() == 24, "段階2のドローンはHP24");
            helper.assertTrue(d.isNoGravity(), "ドローンが飛んでいない");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void droneDropsNothing(GameTestHelper helper) {
        SecurityDrone drone = helper.spawn(SinguloEntities.SECURITY_DRONE.get(), POS.above());
        drone.setTier(3);
        helper.assertTrue(drone.getMaxHealth() == 40, "段階3のドローンはHP40");
        drone.kill();
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(helper.absolutePos(POS)).inflate(4)).isEmpty(), "ドローンが何か落とした");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void detectorDescribesDirectionAndBand(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 64, 0);
        String northeast = GravitationalWaveDetectorBlockEntity.describe(origin, new BlockPos(400, 64, -400)).getString();
        helper.assertTrue(northeast.contains("北東") || northeast.contains("northeast"), "北東にならない: " + northeast);
        helper.assertTrue(northeast.contains("512") , "距離帯が 512〜1024 にならない: " + northeast);
        String south = GravitationalWaveDetectorBlockEntity.describe(origin, new BlockPos(0, 64, 100)).getString();
        helper.assertTrue(south.contains("南") || south.contains("south"), "南にならない: " + south);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void ruinTemplatesHaveCacheAndGuards(GameTestHelper helper) {
        for (String ruin : List.of("observation_post", "research_building", "culture_facility", "final_lab")) {
            Optional<StructureTemplate> template = helper.getLevel().getStructureManager().get(Singulo.id("ruins/" + ruin));
            helper.assertTrue(template.isPresent(), "構造物ファイルがない: " + ruin);
            List<StructureTemplate.StructureBlockInfo> caches = template.get().filterBlocks(BlockPos.ZERO,
                    new StructurePlaceSettings(), SinguloBlocks.RUIN_CACHE.get());
            helper.assertTrue(caches.size() == 1 && caches.get(0).nbt() != null
                    && ruin.equals(caches.get(0).nbt().getString("ruin")), "保管庫が1つ・遺構IDつきで入っていない: " + ruin);
            // 最終実験施設は警備機の代わりに守護機（封印コンソール）。研究棟・培養施設はボス部屋の封印核も持つ
            boolean finalLab = ruin.equals("final_lab");
            helper.assertTrue(!template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                    finalLab ? SinguloBlocks.SEAL_CONSOLE.get() : SinguloBlocks.RUIN_GUARD_DOCK.get()).isEmpty(),
                    (finalLab ? "封印コンソールがない: " : "警備機ドックがない: ") + ruin);
            boolean bossRoom = GuardianCoreBlockEntity.bossFor(ruin) != null;
            List<StructureTemplate.StructureBlockInfo> cores = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                    SinguloBlocks.GUARDIAN_CORE.get());
            helper.assertTrue(cores.size() == (bossRoom ? 1 : 0), "ボス部屋の封印核の数がおかしい: " + ruin + " " + cores.size());
            if (bossRoom) {
                helper.assertTrue(cores.get(0).pos().distManhattan(caches.get(0).pos()) <= GuardianCoreBlockEntity.VAULT_SEARCH_RADIUS * 2,
                        "保管庫が封印核から遠い: " + ruin);
            }
            int projectors = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                    SinguloBlocks.ECHO_PROJECTOR.get()).size();
            helper.assertTrue(projectors == (ruin.equals("research_building") ? 4 : 0), "残響投影器の数がおかしい: " + ruin + " " + projectors);
            helper.assertTrue(caches.get(0).state().getValue(RuinCacheBlock.SEALED) == RuinCacheBlockEntity.GUARDED.contains(ruin),
                    "番人のいる遺構の保管庫だけが封鎖されていない: " + ruin);
        }
        helper.succeed();
    }

    /** /singulo ruin と同じ置き方で、遺構を丸ごと建てられる（保管庫に遺構IDが入る）。 */
    @GameTest(template = EMPTY)
    public static void ruinCommandPlacesRuin(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        net.minecraft.core.Vec3i size = io.github.genichimaruo.singulo.command.SinguloCommands.placeRuin(
                helper.getLevel(), "observation_post", center);
        helper.assertTrue(size != null, "遺構の構造物が見つからない");
        boolean found = false;
        BlockPos origin = center.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            if (helper.getLevel().getBlockEntity(p) instanceof RuinCacheBlockEntity cache && "observation_post".equals(cache.ruin())) {
                found = true;
            }
        }
        helper.assertTrue(found, "建てた遺構に保管庫がない");
        helper.succeed();
    }

    /** 探索コンパスは、攻略した遺構の保管庫で調整するまで、次の遺構を探せない。 */
    @GameTest(template = EMPTY)
    public static void compassUnlocksNextRuinOnlyAfterClearing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.RUIN_CACHE.get());
        RuinCacheBlockEntity cache = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, pos);
        cache.setRuin("observation_post");
        var player = TestBuild.mockPlayer(helper);
        player.setShiftKeyDown(true);
        net.minecraft.world.item.ItemStack compass = new net.minecraft.world.item.ItemStack(
                io.github.genichimaruo.singulo.registry.SinguloItems.EXPLORER_COMPASS.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, compass);
        helper.assertTrue(io.github.genichimaruo.singulo.item.ExplorerCompassItem.level(compass) == 1, "はじめから次の遺構を探せる");
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(pos)),
                net.minecraft.core.Direction.UP, helper.absolutePos(pos), false);
        compass.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(io.github.genichimaruo.singulo.item.ExplorerCompassItem.level(compass) == 1, "攻略していないのに調整できた");
        io.github.genichimaruo.singulo.ruin.RuinDiscovery.record(player, "observation_post", helper.absolutePos(pos));
        compass.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(io.github.genichimaruo.singulo.item.ExplorerCompassItem.level(compass) == 2, "攻略したのに調整できない");
        helper.assertTrue(io.github.genichimaruo.singulo.item.ExplorerCompassItem.TARGETS[
                io.github.genichimaruo.singulo.item.ExplorerCompassItem.target(compass)].equals("research_building"),
                "調整したら次の遺構を探す向きにならない");
        helper.succeed();
    }
}
