package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.ExoticCharge;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.github.genichimaruo.singulo.item.MetricDriveItem;
import io.github.genichimaruo.singulo.machine.AdvancedWorldlineAnchorBlockEntity;
import io.github.genichimaruo.singulo.machine.CatalystDeviceBlockEntity;
import io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity;
import io.github.genichimaruo.singulo.machine.TimeFields;
import io.github.genichimaruo.singulo.machine.TiplerCylinderBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Structures;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 段階5の特異点技術の確認。シールドとティプラーは範囲が広く周りのテストに効いてしまうので、それぞれ別の組（batch）にする。
 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class Stage5RomanceGameTests {
    private static final String EMPTY = "empty";
    private static final String TALL = "tall";
    private static final String HUGE = "huge";

    private Stage5RomanceGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    private static void keepPowered(GameTestHelper helper, CatalystDeviceBlockEntity be) {
        helper.onEachTick(() -> be.energy().setEnergy(be.energy().getMaxEnergyStored()));
    }

    /** コアと縮退炉外殻を並べる（相対座標のコア位置）。 */
    private static void buildCasing(GameTestHelper helper, BlockPos core, java.util.List<BlockPos> absoluteLayout) {
        for (BlockPos p : absoluteLayout) {
            helper.getLevel().setBlockAndUpdate(p, SinguloBlocks.DEGENERATE_CASING.get().defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ 時間の場

    @GameTest(template = EMPTY)
    public static void timeFieldsDilateAccelerateAndCancel(GameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos reactor = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos tipler = helper.absolutePos(new BlockPos(7, 1, 7));
        try {
            TimeFields.set(level, reactor, reactor, 10, false);
            helper.assertTrue(TimeFields.speed(level, at) == TimeFields.DILATION_SPEED
                    && TimeFields.wear(level, at) == TimeFields.DILATION_WEAR, "時間膨張ゾーンで処理×0.8・劣化×0.5 にならない");
            helper.assertTrue(TimeFields.speed(level, reactor) == 1.0, "場を張る装置自身にも効いている");
            TimeFields.set(level, tipler, tipler, 10, true);
            helper.assertTrue(TimeFields.speed(level, at) == 1.0 && TimeFields.wear(level, at) == 1.0,
                    "膨張と加速が重なっても打ち消し合わない");
            TimeFields.remove(level, reactor);
            helper.assertTrue(TimeFields.speed(level, at) == 2.0 && TimeFields.wear(level, at) == 2.0,
                    "ティプラーの範囲で処理×2・劣化×2 にならない");
            TimeFields.remove(level, tipler);
            helper.assertTrue(TimeFields.speed(level, at) == 1.0, "場を外しても元に戻らない");
        } finally {
            TimeFields.remove(level, reactor);
            TimeFields.remove(level, tipler);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ ティプラー・シリンダー

    @GameTest(template = TALL, batch = "tipler", timeoutTicks = 160)
    public static void tiplerCylinderDoublesFurnaceSpeed(GameTestHelper helper) {
        BlockPos core = new BlockPos(2, 1, 2);
        helper.setBlock(core, SinguloBlocks.TIPLER_CORE.get());
        buildCasing(helper, core, Structures.tiplerLayout(helper.absolutePos(core)));
        TiplerCylinderBlockEntity tipler = helper.getBlockEntity(core);
        keepPowered(helper, tipler);
        tipler.catalystSlot().insertItem(0, new ItemStack(item("time_crystal_catalyst")), false);
        tipler.fuel().insertItem(0, new ItemStack(item("exotic_matter"), 2), false);

        // 普通なら200 tick かかる精錬が、×2 なら100 tick ほどで終わる
        BlockPos furnacePos = new BlockPos(0, 1, 0);
        helper.setBlock(furnacePos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = helper.getBlockEntity(furnacePos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));

        // 範囲内の装置には恩恵の模様が出る
        BlockPos machinePos = new BlockPos(4, 1, 0);
        helper.setBlock(machinePos, SinguloBlocks.MACHINES.get(io.github.genichimaruo.singulo.machine.MachineType.COMPRESSOR).get());
        helper.runAtTickTime(30, () -> helper.assertTrue(helper.getBlockState(machinePos)
                .getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.BOOSTED), "ティプラーの範囲の装置に恩恵の模様が出ない"));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(tipler.status() == CatalystDeviceBlockEntity.Status.RUNNING, "動かない: " + tipler.status());
            helper.assertTrue(tipler.fuel().getStackInSlot(0).getCount() == 1, "エキゾチック物質を使っていない");
            helper.assertTrue(TimeFields.speed(helper.getLevel(), helper.absolutePos(furnacePos)) == 2.0, "範囲内が加速していない");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT), "まだ焼けていない");
            helper.assertTrue(helper.getTick() <= 150, "加速されていない（" + helper.getTick() + " tick）");
        });
    }

    @GameTest(template = TALL, batch = "tipler")
    public static void tiplerCylinderNeedsFullShape(GameTestHelper helper) {
        BlockPos core = new BlockPos(2, 1, 2);
        helper.setBlock(core, SinguloBlocks.TIPLER_CORE.get());
        java.util.List<BlockPos> layout = Structures.tiplerLayout(helper.absolutePos(core));
        buildCasing(helper, core, layout.subList(1, layout.size()));   // 1個足りない
        TiplerCylinderBlockEntity tipler = helper.getBlockEntity(core);
        keepPowered(helper, tipler);
        tipler.catalystSlot().insertItem(0, new ItemStack(item("time_crystal_catalyst")), false);
        tipler.fuel().insertItem(0, new ItemStack(item("exotic_matter")), false);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(tipler.status() == CatalystDeviceBlockEntity.Status.NOT_FORMED, "未完成なのに動く");
            helper.assertTrue(layout.size() == 36, "外殻の数がレシピ（36個）と合わない: " + layout.size());
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ イベントホライズン・シールド

    private static ShieldTowerBlockEntity buildShield(GameTestHelper helper, String catalyst) {
        BlockPos core = new BlockPos(2, 1, 2);
        helper.setBlock(core, SinguloBlocks.SHIELD_TOWER_CORE.get());
        buildCasing(helper, core, Structures.shieldTowerLayout(helper.absolutePos(core)));
        ShieldTowerBlockEntity shield = helper.getBlockEntity(core);
        keepPowered(helper, shield);
        shield.catalystSlot().insertItem(0, new ItemStack(item(catalyst)), false);
        return shield;
    }

    @GameTest(template = TALL, batch = "shield", timeoutTicks = 40)
    public static void shieldTowerStopsExplosionsAndGriefing(GameTestHelper helper) {
        ShieldTowerBlockEntity shield = buildShield(helper, "time_crystal_catalyst");
        BlockPos dirt = new BlockPos(0, 1, 0);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(Structures.shieldTowerLayout(BlockPos.ZERO).size() == 20, "外殻の数がレシピ（20個）と合わない");
            helper.assertTrue(shield.protectionLevel() == 4, "時間結晶触媒で守りが始まらない: " + shield.status());
            BlockPos at = helper.absolutePos(dirt.above());
            helper.getLevel().explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3.0F,
                    Level.ExplosionInteraction.TNT);
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
            helper.assertTrue(!EventHooks.canEntityGrief(helper.getLevel(), zombie), "範囲内のモブがブロックを荒らせる");
            helper.assertTrue(!ShieldTowerBlockEntity.shielded(helper.getLevel(), zombie.position(), 5),
                    "時間結晶触媒なのに湧き止めまで効いている");
            zombie.discard();
        });
        helper.runAtTickTime(10, () -> {
            helper.assertBlockPresent(Blocks.DIRT, dirt);
            helper.succeed();
        });
    }

    @GameTest(template = HUGE, batch = "shield_core", timeoutTicks = 60)
    public static void shieldTowerWithCoreRepelsHostiles(GameTestHelper helper) {
        ShieldTowerBlockEntity shield = buildShield(helper, "singularity_core");
        BlockPos zombiePos = new BlockPos(6, 1, 2);
        Zombie[] zombie = new Zombie[1];
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(shield.protectionLevel() == 5, "コアで完全版にならない: " + shield.status());
            zombie[0] = helper.spawn(EntityType.ZOMBIE, zombiePos);
        });
        helper.runAtTickTime(20, () -> {
            double d = zombie[0].position().distanceTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
            helper.assertTrue(d > 5, "敵対モブが押し返されない（距離 " + d + "）");
            zombie[0].discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ ワールドライン・アンカー（上位）

    @GameTest(template = EMPTY)
    public static void advancedAnchorScalesAndEmbedsCore(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.WORLDLINE_ANCHOR_ADVANCED.get());
        AdvancedWorldlineAnchorBlockEntity anchor = helper.getBlockEntity(pos);
        anchor.energy().setEnergy(anchor.energy().getMaxEnergyStored());
        anchor.catalystSlot().insertItem(0, new ItemStack(item("time_crystal_catalyst")), false);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(anchor.loadedRadius() == 3, "時間結晶触媒で半径3にならない: " + anchor.loadedRadius());
            anchor.embed(helper.getLevel());
            anchor.energy().setEnergy(0);
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(anchor.embedded() && anchor.loadedRadius() == AdvancedWorldlineAnchorBlockEntity.EMBEDDED_RADIUS,
                    "コアを埋め込んでも最大半径にならない: " + anchor.loadedRadius());
            helper.assertTrue(anchor.status() == CatalystDeviceBlockEntity.Status.RUNNING, "電力なしで止まった: " + anchor.status());
            helper.assertTrue(anchor.catalystSlot().getStackInSlot(0).isEmpty(), "埋め込み後も触媒が残っている");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ グラビトン・マニピュレーター

    @GameTest(template = EMPTY)
    public static void gravitonManipulatorTargetsAndCrush(GameTestHelper helper) {
        GravitonManipulatorItem manipulator = SinguloItems.GRAVITON_MANIPULATOR.get();
        var golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(1, 1, 1));
        helper.assertTrue(manipulator.affects(golem, GravityGauntletItem.Mode.LEVITATE), "HP100のゴーレムを持ち上げられない");
        helper.assertTrue(!GravityGauntletItem.canAffect(golem), "段階3のガントレットでゴーレムを操れてしまう");
        var enderman = helper.spawn(EntityType.ENDERMAN, new BlockPos(6, 1, 6));
        helper.assertTrue(!manipulator.affects(enderman, GravityGauntletItem.Mode.CRUSH), "重力耐性に圧壊が効く");
        var warden = helper.spawn(SinguloEntities.HORIZON_WARDEN.get(), new BlockPos(4, 1, 4));
        helper.assertTrue(!manipulator.affects(warden, GravityGauntletItem.Mode.LEVITATE), "ボスを持ち上げられてしまう");
        helper.assertTrue(manipulator.affects(warden, GravityGauntletItem.Mode.CRUSH), "ボスに圧壊のダメージが入らない");
        helper.assertTrue(GravitonManipulatorItem.crushDamage(warden) == GravitonManipulatorItem.BOSS_CRUSH_CAP,
                "ボスへの圧壊ダメージに上限がない: " + GravitonManipulatorItem.crushDamage(warden));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        helper.assertTrue(GravitonManipulatorItem.crushDamage(zombie) == 3.0F, "圧壊が「2＋最大HPの5%」でない");
        warden.discard();
        golem.discard();
        enderman.discard();
        zombie.discard();
        helper.assertTrue(GravityGauntletItem.energy(new ItemStack(manipulator)).getMaxEnergyStored() == GravitonManipulatorItem.CAPACITY,
                "マニピュレーターの電力容量が違う");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void gravitonManipulatorRepelsMobsAndProjectiles(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 1, 4);
        ArmorStand owner = helper.spawn(EntityType.ARMOR_STAND, center);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, center.east(2));
        zombie.setNoAi(true);
        Arrow arrow = helper.spawn(EntityType.ARROW, center.west(3).above());
        arrow.setDeltaMovement(1.5, 0, 0);                  // 中心に向かって飛んでくる
        Vec3 c = owner.position().add(0, owner.getBbHeight() / 2, 0);
        GravitonManipulatorItem.repelAround(helper.getLevel(), owner, c);
        helper.assertTrue(zombie.getDeltaMovement().x > 0.3, "モブが外へ押し出されない: " + zombie.getDeltaMovement());
        helper.assertTrue(arrow.getDeltaMovement().x < 0, "飛び道具がそれない: " + arrow.getDeltaMovement());
        zombie.discard();
        arrow.discard();
        owner.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ メトリック・ドライブ

    @GameTest(template = EMPTY)
    public static void metricDriveChangesGravityAndUsesExoticMatter(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.getAbilities().instabuild = false;
            ItemStack drive = new ItemStack(SinguloItems.METRIC_DRIVE.get());
            drive.set(SinguloComponents.GRAVITY_MODE.get(), MetricDriveItem.Mode.LOW_GRAVITY.ordinal());
            player.getInventory().setItem(0, drive);
            player.getInventory().setItem(1, new ItemStack(item("exotic_matter"), 2));
            double normal = player.getAttributeValue(Attributes.GRAVITY);
            MetricDriveItem.tick(player);
            ItemStack held = player.getInventory().getItem(0);
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.GRAVITY) - normal * 0.25) < 1e-9,
                    "低重力で重力が ×0.25 にならない: " + player.getAttributeValue(Attributes.GRAVITY));
            helper.assertTrue(player.getInventory().getItem(1).getCount() == 1, "エキゾチック物質を使っていない");
            helper.assertTrue(ExoticCharge.get(held) == MetricDriveItem.CHARGE_PER_MATTER - 1, "残量が減らない");

            held.set(SinguloComponents.GRAVITY_MODE.get(), MetricDriveItem.Mode.ZERO_G.ordinal());
            MetricDriveItem.tick(player);
            helper.assertTrue(player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) > 0, "無重力で飛べない");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.GRAVITY) - normal) < 1e-9, "低重力が残っている");

            held.set(SinguloComponents.GRAVITY_MODE.get(), MetricDriveItem.Mode.HIGH_GRAVITY.ordinal());
            MetricDriveItem.tick(player);
            helper.assertTrue(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1, "高重力でノックバック無効にならない");
            helper.assertTrue(player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) == 0, "飛行が残っている");

            held.set(SinguloComponents.GRAVITY_MODE.get(), MetricDriveItem.Mode.OFF.ordinal());
            MetricDriveItem.tick(player);
            helper.assertTrue(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0
                    && Math.abs(player.getAttributeValue(Attributes.GRAVITY) - normal) < 1e-9, "停止しても効果が残る");

            // 残量もエキゾチック物質もなければ効かない
            held.set(SinguloComponents.GRAVITY_MODE.get(), MetricDriveItem.Mode.LOW_GRAVITY.ordinal());
            held.set(SinguloComponents.EXOTIC_CHARGE.get(), 0);
            player.getInventory().setItem(1, ItemStack.EMPTY);
            MetricDriveItem.tick(player);
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.GRAVITY) - normal) < 1e-9,
                    "エキゾチック物質がないのに効く");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }
}
