package io.github.genichimaruo.singulo.test;

import com.mojang.authlib.GameProfile;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.ruin.AnomalyEmitterBlock;
import io.github.genichimaruo.singulo.ruin.AnomalyEmitterBlockEntity;
import io.github.genichimaruo.singulo.ruin.ContainmentHauler;
import io.github.genichimaruo.singulo.ruin.DragBrakeBlock;
import io.github.genichimaruo.singulo.ruin.FrameDragFloorBlock;
import io.github.genichimaruo.singulo.ruin.HullRepairer;
import io.github.genichimaruo.singulo.ruin.RuinRelayBlock;
import io.github.genichimaruo.singulo.ruin.RuinRelayBlockEntity;
import io.github.genichimaruo.singulo.ruin.SecurityDrone;
import io.github.genichimaruo.singulo.ruin.lab.BulkheadBlock;
import io.github.genichimaruo.singulo.ruin.lab.PressureHullBlock;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 最終実験施設・沈んだ炉心区画の前室: 中継器と隔壁、中ボス、抽出・排水、異常場、回転流の床と制動器、構造物の中身。
 * 中継器は施設（構造物の外では周り64ブロック）の中継器を数えるので、中継器を置く検査はそれぞれ専用の batch で走らせる。
 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AntechamberGameTests {
    private static final String EMPTY = "empty";

    private static ServerPlayer survivor(GameTestHelper helper, String name) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        TestScope.ownPlayer(helper, player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** 中継器を置く（検査が終わったら取り除く。残すと、あとの batch の検査が施設の中継器として数えてしまう）。 */
    private static RuinRelayBlockEntity relay(GameTestHelper helper, BlockPos pos, String mode, String guardian) {
        helper.setBlock(pos, SinguloBlocks.RUIN_RELAY.get());
        TestScope.of(helper).defer(() -> helper.setBlock(pos, Blocks.AIR));
        var relay = (RuinRelayBlockEntity) helper.getBlockEntity(pos);
        relay.configure(mode, guardian);
        return relay;
    }

    /** すべての中継器を起動すると隔壁が開く。近くに警備が残っている間は起動できない。起動した中継器は戻らない。 */
    @GameTest(template = EMPTY, batch = "antechamber_relays")
    public static void relaysOpenTheBulkheadOnlyWhenAllAreRunning(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(6, 1, 1);
        var first = relay(helper, a, RuinRelayBlockEntity.PLAIN, null);
        var second = relay(helper, b, RuinRelayBlockEntity.PLAIN, null);
        for (int x = 3; x <= 4; x++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(new BlockPos(x, y, 6), SinguloBlocks.BULKHEAD.get());
            }
        }
        var player = survivor(helper, "RelayRunner");
        // 警備ドローンが近くにいる間は起動できない
        SecurityDrone drone = helper.spawn(SinguloEntities.SECURITY_DRONE.get(), new BlockPos(1, 3, 2));
        drone.setNoAi(true);
        first.use(level, player);
        helper.assertTrue(!first.active() && first.hostilesNear(level), "警備が残っているのに中継器が起動した");
        drone.discard();
        first.use(level, player);
        helper.assertTrue(first.active() && helper.getBlockState(a).getValue(RuinRelayBlock.ACTIVE), "中継器が起動しない");
        helper.assertTrue(first.count(level)[0] == 1 && first.count(level)[1] == 2, "中継器の数え方が違う: "
                + first.count(level)[0] + "/" + first.count(level)[1]);
        helper.assertTrue(!helper.getBlockState(new BlockPos(3, 1, 6)).getValue(BulkheadBlock.OPEN), "中継器がそろう前に隔壁が開いた");
        second.use(level, player);
        for (int x = 3; x <= 4; x++) {
            for (int y = 1; y <= 2; y++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 6)).getValue(BulkheadBlock.OPEN), "中継器がそろっても隔壁が開かない");
            }
        }
        // 構造物の外（コマンドで建てた遺構・古い遺構）では、ボスの封印は中継器で止まらない
        helper.assertTrue(RuinRelayBlockEntity.facilityLock(level, helper.absolutePos(a)) == null, "構造物の外でボスの封印が止まった");
        checkMirroredWiring(helper);
        helper.succeed();
    }

    private static void checkMirroredWiring(GameTestHelper helper) {
        var level = helper.getLevel();
        var state = SinguloBlocks.RUIN_RELAY.get().defaultBlockState()
                .mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK)
                .rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        BlockPos first = new BlockPos(6, 1, 6), second = new BlockPos(6, 1, 3), door = new BlockPos(2, 1, 4);
        helper.setBlock(door, SinguloBlocks.BULKHEAD.get().defaultBlockState().setValue(BulkheadBlock.WIRED, true));
        helper.setBlock(first, state);
        var relay = (RuinRelayBlockEntity) helper.getBlockEntity(first);
        wire(relay, "{stations:[[0,0,0],[3,0,0]],door:[2,0,4]}");
        relay.activate(level);
        helper.assertTrue(relay.count(level)[1] == 2 && !helper.getBlockState(door).getValue(BulkheadBlock.OPEN),
                "未配置の回線を無視して隔壁が開いた");
        helper.setBlock(second, state);
        var peer = (RuinRelayBlockEntity) helper.getBlockEntity(second);
        wire(peer, "{stations:[[-3,0,0],[0,0,0]],door:[-1,0,4]}");
        peer.activate(level);
        helper.assertTrue(helper.getBlockState(door).getValue(BulkheadBlock.OPEN), "鏡像・回転した施設の隔壁を開けない");
    }

    private static void wire(RuinRelayBlockEntity relay, String wiring) {
        try {
            relay.load(net.minecraft.nbt.TagParser.parseTag(wiring));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError(e);
        }
    }

    /** 格納庫の中継器: 近づくと封じ込め搬送機が起き、倒すまで起動できない。 */
    @GameTest(template = EMPTY, batch = "antechamber_hauler", timeoutTicks = 200)
    public static void containmentHaulerGuardsTheHangarRelay(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 3);
        var relay = relay(helper, pos, RuinRelayBlockEntity.GUARDED, "containment_hauler");
        var player = survivor(helper, "HangarVisitor");
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 1, 6))));
        relay.tickGuard(level);
        Entity guard = relay.guardAlive(level);
        helper.assertTrue(guard instanceof ContainmentHauler, "挑戦者が近づいても搬送機が起きない");
        ContainmentHauler hauler = (ContainmentHauler) guard;
        helper.assertTrue(hauler.home() != null && hauler.home().equals(helper.absolutePos(pos)), "搬送機が中継器を守る場所にしていない");
        relay.use(level, player);
        helper.assertTrue(!relay.active(), "守り手が残っているのに中継器が起動した");
        hauler.skipEmerging();
        hauler.kill();
        helper.assertTrue(relay.guardDefeated() && relay.guardAlive(level) == null, "搬送機を倒しても中継器に知らせが来ない");
        relay.use(level, player);
        helper.assertTrue(relay.active(), "搬送機を倒したあとに中継器が起動しない");
        helper.succeed();
    }

    /** 搬送機の突進: 壁にぶつかると止まり、止まっている間は受けるダメージが増える。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void haulerStallsWhenItRamsAWall(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
            }
        }
        ContainmentHauler rammer = helper.spawn(SinguloEntities.CONTAINMENT_HAULER.get(), new BlockPos(3, 1, 2));
        ContainmentHauler idle = helper.spawn(SinguloEntities.CONTAINMENT_HAULER.get(), new BlockPos(3, 4, 2));
        idle.setNoAi(true);
        idle.setNoGravity(true);
        for (ContainmentHauler h : List.of(rammer, idle)) {
            h.getAttribute(Attributes.ARMOR).setBaseValue(0);          // 防具の減り方はダメージの大きさで変わるので、倍率だけを見る
        }
        rammer.charge(new Vec3(0, 0, 1));
        helper.assertTrue(rammer.state() == ContainmentHauler.STATE_RAM, "突進を始めない");
        helper.succeedWhen(() -> {
            helper.assertTrue(rammer.state() == ContainmentHauler.STATE_STUNNED, "壁にぶつかっても止まらない: " + rammer.state());
            var source = helper.getLevel().damageSources().generic();
            rammer.invulnerableTime = 0;
            idle.invulnerableTime = 0;
            float before = rammer.getHealth(), idleBefore = idle.getHealth();
            rammer.hurt(source, 10);
            idle.hurt(source, 10);
            float ratio = (before - rammer.getHealth()) / (idleBefore - idle.getHealth());
            helper.assertTrue(Math.abs(ratio - ContainmentHauler.STUNNED_DAMAGE) < 0.05, "止まっている間のダメージの倍率が違う: " + ratio);
        });
    }

    /** 搬送機の踏み鳴らし: 床を伝う衝撃の輪は、床にいる者を打ち、宙にいる者は打たない。 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void haulerStompRingSparesThoseInTheAir(GameTestHelper helper) {
        ContainmentHauler hauler = helper.spawn(SinguloEntities.CONTAINMENT_HAULER.get(), new BlockPos(1, 1, 1));
        Pig grounded = helper.spawn(EntityType.PIG, new BlockPos(5, 1, 1));
        Pig airborne = helper.spawn(EntityType.PIG, new BlockPos(1, 3, 5));
        // 床にいる方は AI を残す（AI のない生き物は落ちず、床に着かない）。歩いても輪の届く広さの中にいる
        airborne.setNoAi(true);
        airborne.setNoGravity(true);
        hauler.startStomp();
        helper.succeedWhen(() -> {
            helper.assertTrue(hauler.rings() == 0 && hauler.state() == ContainmentHauler.STATE_IDLE, "衝撃の輪がまだ広がっている");
            helper.assertTrue(grounded.getHealth() < grounded.getMaxHealth(), "床にいる相手に衝撃の輪が当たらない");
            helper.assertTrue(airborne.getHealth() == airborne.getMaxHealth(), "宙にいる相手に衝撃の輪が当たった");
        });
    }

    /** 整備場の中継器: 炉殻修理機が修理ドローンを呼び、倒されるとドローンも止まって中継器に知らせる。 */
    @GameTest(template = EMPTY, batch = "antechamber_repairer")
    public static void hullRepairerRecallsDronesAndUnlocksTheRelay(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 3);
        var relay = relay(helper, pos, RuinRelayBlockEntity.GUARDED, "hull_repairer");
        helper.assertTrue(relay.summon(level, null), "炉殻修理機を起こせない");
        HullRepairer repairer = (HullRepairer) relay.guardAlive(level);
        repairer.skipEmerging();
        repairer.callDrones(level);
        List<UUID> drones = repairer.drones();
        helper.assertTrue(drones.size() == 2, "修理ドローンが2機来ない: " + drones.size());
        repairer.kill();
        for (UUID id : drones) {
            Entity e = level.getEntity(id);
            helper.assertTrue(e == null || !e.isAlive(), "倒されたあとも修理ドローンが残っている");
        }
        helper.assertTrue(relay.guardDefeated(), "修理機を倒しても中継器に知らせが来ない");
        helper.succeed();
    }

    /** 抽出塔: 渦の穴に落とした物と敵を、重さに応じたエネルギーに替え、たまると起動する。 */
    @GameTest(template = EMPTY, batch = "antechamber_extract")
    public static void extractionRelayChargesFromWhatFallsIn(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 3);
        var relay = relay(helper, pos, RuinRelayBlockEntity.EXTRACT, null);
        Vec3 above = Vec3.atBottomCenterOf(helper.absolutePos(pos)).add(0, 2, 0);
        ItemEntity blocks = new ItemEntity(level, above.x, above.y, above.z, new ItemStack(Items.COBBLESTONE, 64));
        level.addFreshEntity(blocks);
        relay.tickExtract(level);
        helper.assertTrue(blocks.isRemoved() && relay.charge() == 256, "ブロックの重さが4でない: " + relay.charge());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        zombie.setNoAi(true);
        relay.tickExtract(level);
        helper.assertTrue(!zombie.isAlive() && relay.charge() == 256 + Math.round(zombie.getMaxHealth() * 2),
                "穴に落ちた敵がエネルギーにならない: " + relay.charge());
        ItemEntity sticks = new ItemEntity(level, above.x, above.y, above.z, new ItemStack(Items.STICK, 32));
        level.addFreshEntity(sticks);
        relay.tickExtract(level);
        helper.assertTrue(!relay.active(), "ためきる前に起動した");
        level.addFreshEntity(new ItemEntity(level, above.x, above.y, above.z, new ItemStack(Items.COBBLESTONE, 64)));
        relay.tickExtract(level);
        helper.assertTrue(relay.active() && relay.charge() == RuinRelayBlockEntity.EXTRACT_GOAL, "ためきっても起動しない: " + relay.charge());
        // 起動したあとは呑み込まない（穴に物を落としても失われない）
        ItemEntity kept = new ItemEntity(level, above.x, above.y, above.z, new ItemStack(Items.DIAMOND));
        level.addFreshEntity(kept);
        RuinRelayBlockEntity.serverTick(level, relay.getBlockPos(), relay.getBlockState(), relay);
        helper.assertTrue(!kept.isRemoved(), "起動したあとも物を呑み込んだ");
        kept.discard();
        helper.succeed();
    }

    /**
     * 排水弁の中継器: 起動すると、つながった水だけを上の段から1段ずつ抜く（はしごと水没した中継器は水だけ抜けて残る）。
     */
    @GameTest(template = EMPTY, batch = "antechamber_drain", timeoutTicks = 200)
    public static void drainRelayEmptiesOnlyConnectedWater(GameTestHelper helper) {
        var level = helper.getLevel();
        // 床（y=0）も石で閉じる（水は1段ずつ抜けるので、その間に床へ流れ出すと、離れた水とつながってしまう）
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                for (int y = 0; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        BlockPos pos = new BlockPos(2, 1, 2);
        var relay = relay(helper, pos, RuinRelayBlockEntity.DRAIN, null);
        helper.setBlock(pos, helper.getBlockState(pos).setValue(RuinRelayBlock.WATERLOGGED, true));
        relay = (RuinRelayBlockEntity) helper.getBlockEntity(pos);
        BlockPos[] connected = {new BlockPos(3, 1, 1), new BlockPos(3, 1, 2), new BlockPos(3, 1, 3), new BlockPos(4, 1, 2), new BlockPos(3, 2, 2)};
        for (BlockPos p : connected) {
            helper.setBlock(p, Blocks.WATER);
        }
        BlockPos ladder = new BlockPos(2, 1, 1);
        helper.setBlock(ladder, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST)
                .setValue(BlockStateProperties.WATERLOGGED, true));
        BlockPos isolated = new BlockPos(6, 1, 6);
        helper.setBlock(isolated, Blocks.WATER);
        relay.activate(level);
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 2)).isAir(), "いちばん上の段の水が抜けない");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 2)).is(Blocks.WATER) && relay.draining(), "水が一度に全部抜けた");
        var drained = relay;
        helper.succeedWhen(() -> {
            helper.assertTrue(!drained.draining(), "排水が終わらない");
            for (BlockPos p : connected) {
                helper.assertTrue(helper.getBlockState(p).isAir(), "つながった水が残っている: " + p);
            }
            BlockState l = helper.getBlockState(ladder);
            helper.assertTrue(l.is(Blocks.LADDER) && !l.getValue(BlockStateProperties.WATERLOGGED), "はしごが水浸しのまま、または消えた");
            BlockState r = helper.getBlockState(pos);
            helper.assertTrue(r.is(SinguloBlocks.RUIN_RELAY.get()) && !r.getValue(RuinRelayBlock.WATERLOGGED), "中継器が水没したまま、または消えた");
            helper.assertTrue(helper.getBlockState(isolated).is(Blocks.WATER), "つながっていない水まで抜けた");
        });
    }

    /** 異常場: 浮く場は浮遊、重い場は鈍足、遅い場は鈍足と採掘速度低下、速い場は移動速度上昇。場の上の生き物に効く。 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void anomalyFieldsAffectWhatIsAbove(GameTestHelper helper) {
        Pig probe = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        probe.setNoAi(true);
        AnomalyEmitterBlockEntity.apply(probe, AnomalyEmitterBlock.Field.SLOW);
        helper.assertTrue(probe.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && probe.hasEffect(MobEffects.DIG_SLOWDOWN), "遅い場が効かない");
        probe.removeAllEffects();
        AnomalyEmitterBlockEntity.apply(probe, AnomalyEmitterBlock.Field.FAST);
        helper.assertTrue(probe.hasEffect(MobEffects.MOVEMENT_SPEED) && !probe.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "速い場が効かない");
        probe.removeAllEffects();
        AnomalyEmitterBlockEntity.apply(probe, AnomalyEmitterBlock.Field.HEAVY);
        helper.assertTrue(probe.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "重い場が効かない");
        probe.discard();
        // 置いた発生器: 真上の豚が浮く
        BlockPos emitter = new BlockPos(4, 1, 4);
        helper.setBlock(emitter, SinguloBlocks.ANOMALY_EMITTER.get().defaultBlockState()
                .setValue(AnomalyEmitterBlock.FIELD, AnomalyEmitterBlock.Field.LIFT));
        Pig lifted = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
        lifted.setNoAi(true);
        helper.succeedWhen(() -> helper.assertTrue(lifted.hasEffect(MobEffects.LEVITATION), "浮く場の上の豚が浮かない"));
    }

    /** 制動器は、同じ区画の回転流の床だけを止める。一度下ろすと戻らない。 */
    @GameTest(template = EMPTY)
    public static void brakeStopsOnlyItsOwnSegment(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockState floor = SinguloBlocks.FRAME_DRAG_FLOOR.get().defaultBlockState().setValue(FrameDragFloorBlock.FACING, Direction.EAST);
        for (int x = 1; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), floor.setValue(FrameDragFloorBlock.SEGMENT, 0));
            helper.setBlock(new BlockPos(x, 1, 5), floor.setValue(FrameDragFloorBlock.SEGMENT, 1));
        }
        BlockPos brake = new BlockPos(0, 2, 3);
        helper.setBlock(brake, SinguloBlocks.DRAG_BRAKE.get().defaultBlockState().setValue(DragBrakeBlock.SEGMENT, 0));
        var player = survivor(helper, "BrakePuller");
        BlockPos abs = helper.absolutePos(brake);
        helper.getBlockState(brake).use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        helper.assertTrue(helper.getBlockState(brake).getValue(DragBrakeBlock.ENGAGED), "制動器が下りない");
        for (int x = 1; x <= 6; x++) {
            helper.assertTrue(!helper.getBlockState(new BlockPos(x, 1, 2)).getValue(FrameDragFloorBlock.ACTIVE), "同じ区画の床が止まらない");
            helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, 5)).getValue(FrameDragFloorBlock.ACTIVE), "別の区画の床まで止まった");
        }
        helper.succeed();
    }

    /**
     * 中継器がそろう前に最終実験施設のドームへ入り込むと、警報が鳴る: 灯が赤い警告灯に変わり、報酬のない体力20倍の守護機
     * （排除モード）が現れ、隔壁が開く。倒しても何も落とさず保管庫も開かない。挑戦者が外へ出ると隔壁が閉じ、灯が戻り、
     * 壊れていた耐圧殻と耐圧ガラスが作り直される。
     * 実際の構造物を、ほかの検査から離れた上空に置いて確かめる。
     */
    @GameTest(template = EMPTY, batch = "antechamber_intrusion", timeoutTicks = 400)
    public static void intrudersRaiseTheAlarmInsteadOfBeingTeleported(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var template = level.getStructureManager().get(Singulo.id("ruins/final_lab")).orElseThrow();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(90);
        var settings = new StructurePlaceSettings().setIgnoreEntities(true);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        var size = template.getSize();
        TestScope.of(helper).defer(() -> {
            AABB box = new AABB(origin, origin.offset(size.getX() + 1, size.getY() + 1, size.getZ() + 1));
            level.getEntities((Entity) null, box, e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
            for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
                if (!level.getBlockState(p).isAir()) {
                    level.removeBlockEntity(p);
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        });
        BlockPos console = origin.offset(template.filterBlocks(BlockPos.ZERO, settings, SinguloBlocks.SEAL_CONSOLE.get()).get(0).pos());
        var be = (io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity) level.getBlockEntity(console);
        BlockPos gate = console.below(2).south(19);
        helper.assertTrue(level.getBlockState(gate).is(SinguloBlocks.BULKHEAD.get()) && !level.getBlockState(gate).getValue(BulkheadBlock.OPEN),
                "隔壁が閉じていない");
        // 壁を破ったことにする（耐圧殻と耐圧ガラスを1つずつ取り除く）
        BlockPos brokenHull = null, brokenGlass = null;
        for (BlockPos p : BlockPos.betweenClosed(console.offset(-20, -4, -20), console.offset(20, 0, 20))) {
            if (brokenHull == null && level.getBlockState(p).is(SinguloBlocks.PRESSURE_HULL.get()) && !p.equals(console.below(4))) {
                brokenHull = p.immutable();
            } else if (brokenGlass == null && level.getBlockState(p).is(SinguloBlocks.PRESSURE_GLASS.get())) {
                brokenGlass = p.immutable();
            }
        }
        helper.assertTrue(brokenHull != null && brokenGlass != null, "耐圧殻・耐圧ガラスが見つからない");
        level.setBlock(brokenHull, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(brokenGlass, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        var player = survivor(helper, "Intruder");
        player.teleportTo(level, console.getX() + 0.5, console.getY() - 2, console.getZ() - 8.5, 0, 0);
        be.alarm().check(level, console, be);
        helper.assertTrue(be.alarm().active() && level.getBlockState(gate).getValue(BulkheadBlock.OPEN), "侵入しても警報が鳴らない・隔壁が開かない");
        helper.assertTrue(player.blockPosition().closerThan(console, 12), "侵入した挑戦者が動かされた");
        var guards = level.getEntitiesOfClass(io.github.genichimaruo.singulo.ruin.HorizonWarden.class, new AABB(console).inflate(20));
        helper.assertTrue(guards.size() == 1 && guards.get(0).intruderGuard(), "排除モードの守護機が現れない");
        var normal = SinguloEntities.HORIZON_WARDEN.get().create(level);
        float ratio = guards.get(0).effectiveMaxHealth() / normal.effectiveMaxHealth();
        helper.assertTrue(Math.abs(ratio - io.github.genichimaruo.singulo.ruin.IntrusionAlarm.HEALTH_MULTIPLIER) < 0.01F,
                "排除モードの体力が20倍でない: " + ratio);
        int red = 0, white = 0;
        for (BlockPos p : BlockPos.betweenClosed(console.offset(-17, -4, -17), console.offset(17, 18, 17))) {
            if (level.getBlockState(p).is(SinguloBlocks.RUIN_LAMP_CRIMSON.get())) red++;
            else if (level.getBlockState(p).is(SinguloBlocks.RUIN_LAMP.get()) && p.distSqr(console) < 15 * 15) white++;
        }
        helper.assertTrue(red > 20 && white == 0, "ドームの灯が赤い警告灯に変わらない: 赤 " + red + " / 白 " + white);
        // 倒しても何も落とさず、保管庫の封鎖も解けない
        var guard = guards.get(0);
        guard.setInvulnerable(false);
        guard.die(level.damageSources().playerAttack(player));
        guard.discard();
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(console).inflate(24)).isEmpty(),
                "排除モードの守護機が物を落とした");
        helper.assertTrue(be.vault(level) != null && be.vault(level).isSealed(), "排除モードの守護機を倒すと保管庫が開いた");
        // 外へ出ると隔壁が閉じ、灯が戻る
        player.teleportTo(level, gate.getX() + 0.5, gate.getY(), gate.getZ() + 6.5, 0, 0);
        be.alarm().check(level, console, be);
        helper.assertTrue(!be.alarm().active() && !level.getBlockState(gate).getValue(BulkheadBlock.OPEN), "外へ出ても隔壁が閉じない");
        int restored = 0;
        for (BlockPos p : BlockPos.betweenClosed(console.offset(-17, -4, -17), console.offset(17, 18, 17))) {
            if (level.getBlockState(p).is(SinguloBlocks.RUIN_LAMP_CRIMSON.get())) {
                helper.fail("警告灯が戻らない: " + p);
            }
            if (level.getBlockState(p).is(SinguloBlocks.RUIN_LAMP.get())) restored++;
        }
        helper.assertTrue(restored >= red, "灯が元に戻らない");
        helper.assertTrue(level.getBlockState(brokenHull).is(SinguloBlocks.PRESSURE_HULL.get())
                && level.getBlockState(brokenHull).getValue(PressureHullBlock.SEALED), "壊れた耐圧殻が作り直されない");
        helper.assertTrue(level.getBlockState(brokenGlass).is(SinguloBlocks.PRESSURE_GLASS.get()), "壊れた耐圧ガラスが作り直されない");
        helper.succeed();
    }

    /** 耐圧ガラス: 封じられている間は掘れず、爆発にも耐え、遺構の敵にも壊されない。封じが解けると掘れる。 */
    @GameTest(template = EMPTY)
    public static void sealedPressureGlassHoldsLikeTheHull(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        var sealed = SinguloBlocks.PRESSURE_GLASS.get().defaultBlockState().setValue(PressureHullBlock.SEALED, true);
        helper.setBlock(at, sealed);
        var player = helper.makeMockPlayer();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
        BlockPos abs = helper.absolutePos(at);
        helper.assertTrue(helper.getBlockState(at).getDestroyProgress(player, helper.getLevel(), abs) == 0, "封じられた耐圧ガラスが掘れる");
        helper.assertTrue(PressureHullBlock.isSealed(helper.getBlockState(at)), "耐圧ガラスが封じられた耐圧殻として扱われない");
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 4));
        helper.assertTrue(!helper.getBlockState(at).canEntityDestroy(helper.getLevel(), abs, zombie), "敵が封じられた耐圧ガラスを壊せる");
        helper.assertTrue(SinguloBlocks.PRESSURE_GLASS.get().getExplosionResistance() >= 3_600_000F, "耐圧ガラスが爆発で壊れる");
        helper.setBlock(at, sealed.setValue(PressureHullBlock.SEALED, false));
        helper.assertTrue(helper.getBlockState(at).getDestroyProgress(player, helper.getLevel(), abs) > 0, "封じが解けた耐圧ガラスが掘れない");
        zombie.discard();
        helper.succeed();
    }

    /** 構造物: 最終実験施設は3つ、沈んだ炉心区画は4つの中継器（種類つき）と、ボス部屋の前の閉じた隔壁を持つ。 */
    @GameTest(template = EMPTY)
    public static void antechamberTemplatesHoldRelaysAndBulkheads(GameTestHelper helper) {
        var manager = helper.getLevel().getStructureManager();
        Map<String, Map<String, Integer>> expected = Map.of(
                "final_lab", Map.of("plain", 2, "guarded:containment_hauler", 1),
                "sunken_reactor", Map.of("plain", 1, "drain", 1, "extract", 1, "guarded:hull_repairer", 1));
        for (var entry : expected.entrySet()) {
            Optional<StructureTemplate> template = manager.get(Singulo.id("ruins/" + entry.getKey()));
            helper.assertTrue(template.isPresent(), "構造物がない: " + entry.getKey());
            Map<String, Integer> modes = new HashMap<>();
            for (var info : template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), SinguloBlocks.RUIN_RELAY.get())) {
                helper.assertTrue(info.nbt() != null && !info.state().getValue(RuinRelayBlock.ACTIVE), "中継器の設定がない、または起動済み");
                String mode = info.nbt().getString("mode");
                String guardian = info.nbt().getString("guardian");
                modes.merge(guardian.isEmpty() ? mode : mode + ":" + guardian, 1, Integer::sum);
            }
            helper.assertTrue(modes.equals(entry.getValue()), "中継器の種類が違う: " + entry.getKey() + " " + modes);
            var bulkheads = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), SinguloBlocks.BULKHEAD.get());
            helper.assertTrue(bulkheads.size() == 12 && bulkheads.stream().noneMatch(i -> i.state().getValue(BulkheadBlock.OPEN)),
                    "隔壁（3×4、閉じている）がない: " + entry.getKey() + " " + bulkheads.size());
            var glass = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), SinguloBlocks.PRESSURE_GLASS.get());
            helper.assertTrue(glass.size() > 600 && glass.stream().allMatch(i -> i.state().getValue(PressureHullBlock.SEALED)),
                    "ボス部屋を囲む観測窓（封じられた耐圧ガラス）がない: " + entry.getKey() + " " + glass.size());
        }
        helper.succeed();
    }
}
