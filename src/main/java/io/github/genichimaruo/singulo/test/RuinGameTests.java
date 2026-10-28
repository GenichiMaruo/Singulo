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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
        RuinCacheBlockEntity cache = helper.getBlockEntity(POS);
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
        GuardDockBlockEntity dock = helper.getBlockEntity(POS);
        for (int tier = 1; tier <= 3; tier++) {
            dock.setTier(tier);
            dock.launch(helper.getLevel());
            List<SecurityDrone> drones = helper.getLevel().getEntitiesOfClass(SecurityDrone.class,
                    new AABB(helper.absolutePos(POS)).inflate(4));
            helper.assertTrue(drones.size() == (tier == 1 ? 2 : 3), "ドック1個の出撃数が2〜3機ではない: " + drones.size());
            for (SecurityDrone d : drones) {
                helper.assertTrue(d.tier() == tier && d.getMaxHealth() == (tier == 1 ? 6 : tier == 2 ? 14 : 24), "段階と体力が不一致");
                helper.assertTrue(d.isNoGravity(), "ドローンが飛んでいない");
                d.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void droneBoltsAreReducedByArmor(GameTestHelper helper) throws Exception {
        var drone = helper.spawn(SinguloEntities.SECURITY_DRONE.get(), POS.above());
        var bare = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, POS.east(2));
        var armored = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, POS.west(2));
        armored.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(20);
        armored.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        bare.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        drone.setTier(3);
        var shoot = SecurityDrone.class.getDeclaredMethod("shoot", net.minecraft.world.entity.LivingEntity.class);
        shoot.setAccessible(true); shoot.invoke(drone, bare);
        var bolts = helper.getLevel().getEntitiesOfClass(io.github.genichimaruo.singulo.ruin.HorizonBolt.class,
                drone.getBoundingBox().inflate(3), bolt -> bolt.getOwner() == drone);
        helper.assertTrue(bolts.size() == 1 && bolts.getFirst().armored(), "実際のドローン光弾が防具を無視する");
        var hit = io.github.genichimaruo.singulo.ruin.HorizonBolt.class.getDeclaredMethod("onHitEntity", net.minecraft.world.phys.EntityHitResult.class);
        hit.setAccessible(true);
        var movement = new net.minecraft.world.phys.Vec3(0.1, 0.2, -0.15);
        bare.setDeltaMovement(movement); armored.setDeltaMovement(movement);
        float beforeBare = bare.getHealth(), beforeArmored = armored.getHealth();
        hit.invoke(bolts.getFirst(), new net.minecraft.world.phys.EntityHitResult(bare));
        hit.invoke(bolts.getFirst(), new net.minecraft.world.phys.EntityHitResult(armored));
        helper.assertTrue(beforeBare - bare.getHealth() > 0 && beforeArmored - armored.getHealth() < (beforeBare - bare.getHealth()) / 2,
                "防具がドローン光弾を十分に軽減しない");
        helper.assertTrue(bare.getDeltaMovement().equals(movement) && armored.getDeltaMovement().equals(movement), "ドローン光弾が移動速度を変えた");
        bolts.forEach(net.minecraft.world.entity.Entity::discard); drone.discard(); bare.discard(); armored.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "drone_altitude")
    public static void healthyDroneDescendsAndCannotShootFromHighAbove(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos floor = helper.absolutePos(POS).offset(0, 64, 256);
        var forced = new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x = (floor.getX() - 12) >> 4; x <= (floor.getX() + 12) >> 4; x++) {
            for (int z = (floor.getZ() - 12) >> 4; z <= (floor.getZ() + 12) >> 4; z++) {
                var chunk = new net.minecraft.world.level.ChunkPos(x, z);
                if (!level.getForcedChunks().contains(chunk.toLong())) { level.setChunkForced(x, z, true); forced.add(chunk); }
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-12, 0, -12), floor.offset(12, 0, 12))) {
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        var drone = helper.spawn(SinguloEntities.SECURITY_DRONE.get(), new BlockPos(3, 12, 3));
        // Other simultaneous fixtures create players. Keep this altitude test's
        // manually assigned target instead of letting player acquisition replace it.
        var selectors = net.minecraft.world.entity.Mob.class.getDeclaredField("targetSelector");
        selectors.setAccessible(true);
        ((net.minecraft.world.entity.ai.goal.GoalSelector) selectors.get(drone)).removeAllGoals(goal -> true);
        var target = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, POS);
        target.moveTo(floor.above().getCenter());
        drone.moveTo(floor.above(12).getCenter());
        target.setNoAi(true); target.setNoGravity(true);
        Runnable cleanup = () -> {
            drone.discard(); target.discard();
            for (var chunk : forced) level.setChunkForced(chunk.x, chunk.z, false);
        };
        helper.runAtTickTime(590, cleanup);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000); drone.setTarget(target);
        var shoot = SecurityDrone.class.getDeclaredMethod("shoot", net.minecraft.world.entity.LivingEntity.class);
        shoot.setAccessible(true); shoot.invoke(drone, target);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(io.github.genichimaruo.singulo.ruin.HorizonBolt.class,
                drone.getBoundingBox().inflate(3), bolt -> bolt.getOwner() == drone).isEmpty(), "高空から射撃できてしまう");
        // Count actual entity ticks: accelerated GameTests can run ahead of chunk ticket promotion.
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.tickCount >= 120, "Waiting for the drone's entity-ticking chunk");
            helper.assertFalse(drone.crippled(), "高度検査中に壊れかけになった");
            helper.assertTrue(drone.getY() <= target.getY() + 2.8, "健康なドローンが高空に留まる: " + (drone.getY() - target.getY()));
            cleanup.run();
        });
    }

    @GameTest(template = EMPTY)
    public static void droneRamAndPulsePreserveVictimMovement(GameTestHelper helper) throws Exception {
        var drone = helper.spawn(SinguloEntities.SECURITY_DRONE.get(), POS.above()); drone.setTier(3);
        var target = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, POS.east());
        var movement = new net.minecraft.world.phys.Vec3(0.13, 0.21, -0.17);
        target.setDeltaMovement(movement); drone.setTarget(target);
        var setState = SecurityDrone.class.getDeclaredMethod("setState", int.class); setState.setAccessible(true);
        setState.invoke(drone, SecurityDrone.STATE_RAM);
        Class<?> goalType = java.util.Arrays.stream(SecurityDrone.class.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("CombatGoal")).findFirst().orElseThrow();
        var constructor = goalType.getDeclaredConstructor(SecurityDrone.class); constructor.setAccessible(true);
        var tick = goalType.getDeclaredMethod("tick"); tick.setAccessible(true);
        float before = target.getHealth(); tick.invoke(constructor.newInstance(drone));
        helper.assertTrue(target.getHealth() < before && target.getDeltaMovement().equals(movement), "突進でダメージがないか、吹き飛ばされた");
        target.invulnerableTime = 0;
        var pulse = SecurityDrone.class.getDeclaredMethod("pulse", net.minecraft.server.level.ServerLevel.class); pulse.setAccessible(true);
        before = target.getHealth(); pulse.invoke(drone, helper.getLevel());
        helper.assertTrue(target.getHealth() < before && target.getDeltaMovement().equals(movement), "パルスでダメージがないか、吹き飛ばされた");
        helper.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "パルスの鈍足まで失われた");
        target.invulnerableTime = 0;
        target.hurt(helper.getLevel().damageSources().mobAttack(target), 1);
        helper.assertFalse(target.getDeltaMovement().equals(movement), "他の攻撃のノックバックまで無効化された");
        drone.discard(); target.discard(); helper.succeed();
    }

    /** 体力が半分を切ったドローンは壊れかけになり、地面から1.5ブロックより上へは飛べない（高い所にいれば沈む）。 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void crippledDroneFliesLow(GameTestHelper helper) {
        SecurityDrone drone = helper.spawn(io.github.genichimaruo.singulo.registry.SinguloEntities.SECURITY_DRONE.get(), new BlockPos(3, 6, 3));
        drone.setTier(1);
        helper.assertFalse(drone.crippled(), "無傷のドローンが壊れかけになっている");
        drone.setHealth(drone.getMaxHealth() * 0.4F);
        helper.assertTrue(drone.crippled(), "体力4割のドローンが壊れかけにならない");
        double floor = helper.absolutePos(new BlockPos(3, 1, 3)).getY();
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(drone.getY() <= floor + 1.5 + 0.3, "壊れかけのドローンが地面近くまで下りない: 床 " + floor + " / " + drone.getY());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void droneDropsNothing(GameTestHelper helper) {
        SecurityDrone drone = helper.spawn(SinguloEntities.SECURITY_DRONE.get(), POS.above());
        drone.setTier(3);
        helper.assertTrue(drone.getMaxHealth() == 24, "段階3のドローンはHP24");
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
        // サーバー側の翻訳に頼らないよう、翻訳キーで確かめる
        String northeast = translationKeys(GravitationalWaveDetectorBlockEntity.describe(origin, new BlockPos(400, 64, -400)));
        helper.assertTrue(northeast.contains("direction.singulo.northeast"), "北東にならない: " + northeast);
        helper.assertTrue(northeast.contains("gui.singulo.detector.band.2"), "距離帯が 512〜1024 にならない: " + northeast);
        String south = translationKeys(GravitationalWaveDetectorBlockEntity.describe(origin, new BlockPos(0, 64, 100)));
        helper.assertTrue(south.contains("direction.singulo.south"), "南にならない: " + south);
        helper.succeed();
    }

    /** 文に含まれる翻訳キー（引数の中も）を空白区切りで並べる。 */
    private static String translationKeys(net.minecraft.network.chat.Component c) {
        StringBuilder out = new StringBuilder();
        if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            out.append(t.getKey()).append(' ');
            for (Object arg : t.getArgs()) {
                if (arg instanceof net.minecraft.network.chat.Component a) {
                    out.append(translationKeys(a));
                }
            }
        }
        c.getSiblings().forEach(s -> out.append(translationKeys(s)));
        return out.toString();
    }

    @GameTest(template = EMPTY)
    public static void ruinTemplatesHaveCacheAndGuards(GameTestHelper helper) {
        // 地下アンドロイド研究所は部屋の型紙を並べるので、LabGameTests で確かめる
        for (String ruin : List.of("observation_post", "research_building", "culture_facility", "final_lab", "sunken_reactor")) {
            Optional<StructureTemplate> template = helper.getLevel().getStructureManager().get(Singulo.id("ruins/" + ruin));
            helper.assertTrue(template.isPresent(), "構造物ファイルがない: " + ruin);
            List<StructureTemplate.StructureBlockInfo> caches = template.get().filterBlocks(BlockPos.ZERO,
                    new StructurePlaceSettings(), SinguloBlocks.RUIN_CACHE.get());
            helper.assertTrue(caches.size() == 1 && caches.get(0).nbt() != null
                    && ruin.equals(caches.get(0).nbt().getString("ruin")), "保管庫が1つ・遺構IDつきで入っていない: " + ruin);
            // 最終実験施設は警備機の代わりに守護機（封印コンソール）。研究棟・培養施設はボス部屋の封印核も持つ
            // 沈んだ炉心区画は、部屋全体が大蛇の泳ぐボス部屋なので警備機を置かない
            boolean finalLab = ruin.equals("final_lab");
            helper.assertTrue(ruin.equals("sunken_reactor") || !template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
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
        RuinCacheBlockEntity cache = helper.getBlockEntity(pos);
        cache.setRuin("observation_post");
        var player = helper.makeMockServerPlayerInLevel();
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
