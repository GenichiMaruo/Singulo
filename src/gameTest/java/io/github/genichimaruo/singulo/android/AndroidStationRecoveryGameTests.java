package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AndroidStationRecoveryGameTests {
    private static final BlockPos STATION = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 360)
    public static void guardTransfersEquipmentWithMobLootDisabled(GameTestHelper helper) {
        var level = helper.getLevel();
        var station = station(helper);
        helper.startSequence().thenWaitUntil(() -> printedGuard(helper, station)).thenExecute(() -> {
            var guard = printedGuard(helper, station);
            // 以前のワールドではステーションの座標だけが保存されている。
            CompoundTag legacy = io.github.genichimaruo.singulo.test.harness.TestNbt.save(guard);
            legacy.remove("home_dimension");
            io.github.genichimaruo.singulo.test.harness.TestNbt.load(guard, legacy);
            guard.inventory().setItem(RuinAndroid.SLOT_REPAIR, item("android_repair_kit", 5));
            boolean previous = level.getGameRules().get(GameRules.MOB_DROPS);
            try {
                level.getGameRules().set(GameRules.MOB_DROPS, false, level.getServer());
                guard.kill(level);
            } finally {
                level.getGameRules().set(GameRules.MOB_DROPS, previous, level.getServer());
            }
        }).thenWaitUntil(() -> printedGuard(helper, station)).thenExecute(() -> {
            var replacement = printedGuard(helper, station);
            assertRepairKits(helper, replacement);
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(station.getBlockPos()).inflate(6),
                    drop -> drop.getItem().is(item("android_repair_kit", 1).getItem())).isEmpty(), "転送した装備が地面にも落ちた");
            replacement.discard();
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 360)
    public static void guardReprintsAtHomeAfterNetherDeath(GameTestHelper helper) {
        var station = station(helper);
        helper.startSequence().thenWaitUntil(() -> printedGuard(helper, station)).thenExecute(() -> {
            var guard = printedGuard(helper, station);
            var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            BlockPos destination = station.getBlockPos().atY(190);
            nether.getChunkAt(destination);
            var traveller = (RuinAndroid) guard.teleport(new TeleportTransition(nether, Vec3.atCenterOf(destination),
                    Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            helper.assertTrue(traveller != null && traveller.level() == nether, "護衛がネザーに到着しない");
            try {
                CompoundTag saved = io.github.genichimaruo.singulo.test.harness.TestNbt.save(traveller);
                helper.assertTrue(helper.getLevel().dimension().identifier().toString().equals(saved.getStringOr("home_dimension", "")), "元の次元が保存されない");
                saved.remove("home_dimension");
                CompoundTag pending = saved.copy();
                BlockPos unavailable = station.getBlockPos().above(3);
                pending.put("home", io.github.genichimaruo.singulo.compat.NbtCompat.writeBlockPos(unavailable));
                io.github.genichimaruo.singulo.test.harness.TestNbt.load(traveller, pending);
                traveller.setNoAi(true);
                CompoundTag unresolved = io.github.genichimaruo.singulo.test.harness.TestNbt.save(traveller);
                helper.assertTrue(!unresolved.contains("home_dimension") && io.github.genichimaruo.singulo.compat.NbtCompat.readBlockPos(unresolved, "home").orElseThrow().equals(unavailable),
                        "ステーション未確認の旧リンクが失われたか、移動先の次元に確定された");
                io.github.genichimaruo.singulo.test.harness.TestNbt.load(traveller, unresolved);
                helper.assertTrue(!traveller.orphan() && traveller.home() == null, "再読込で保留中のリンクが失われた");
                io.github.genichimaruo.singulo.test.harness.TestNbt.load(traveller, saved);
                traveller.setNoAi(true);
                traveller.tickCount = 20;
                traveller.tick();
                helper.assertTrue(!traveller.orphan() && traveller.home() == null, "別次元の同じ座標を自分のステーションとして扱った");
                traveller.inventory().setItem(RuinAndroid.SLOT_REPAIR, item("android_repair_kit", 5));
                traveller.kill(nether);
                helper.assertTrue(!station.manages(traveller.getUUID()), "死亡した護衛が元のステーションに登録されたまま");
            } finally {
                traveller.discard();
            }
        }).thenWaitUntil(() -> printedGuard(helper, station)).thenExecute(() -> {
            var replacement = printedGuard(helper, station);
            assertRepairKits(helper, replacement);
            helper.assertTrue(station.items().getStackInSlot(AndroidStationBlockEntity.SLOT_KIT).isEmpty(), "再印刷に予備キットを使わなかった");
            replacement.discard();
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void legacyGuardLinksToNewStationAfterHomeRemoval(GameTestHelper helper) {
        var old = station(helper);
        RuinAndroid[] guards = new RuinAndroid[1];
        helper.startSequence().thenWaitUntil(() -> printedGuard(helper, old)).thenExecute(() -> {
            var guard = printedGuard(helper, old);
            guards[0] = guard;
            CompoundTag legacy = io.github.genichimaruo.singulo.test.harness.TestNbt.save(guard);
            legacy.remove("home_dimension");
            helper.setBlock(STATION, Blocks.AIR);
            helper.setBlock(STATION, SinguloBlocks.ANDROID_STATION.get());
            AndroidStationBlockEntity replacement = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(helper, STATION);
            helper.assertTrue(old.isRemoved() && old.getBlockPos().equals(replacement.getBlockPos()), "元のステーションが同じ座標で交換されなかった");
            replacement.setOwner(guard.owner());
            replacement.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY, item(AndroidKind.BLADE.memoryId(), 1));
            io.github.genichimaruo.singulo.test.harness.TestNbt.load(guard, legacy);
            helper.assertTrue(!guard.orphan() && !replacement.manages(guard.getUUID()), "確認前にリンクが確定した");
        }).thenWaitUntil(() -> {
            var guard = guards[0];
            AndroidStationBlockEntity replacement = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(helper, STATION);
            helper.assertTrue(guard.orphan(), "撤去済みの旧ステーションにリンクしたまま");
            replacement.press(AndroidStationBlockEntity.BUTTON_LINK);
            helper.assertTrue(replacement.manages(guard.getUUID()) && replacement.getBlockPos().equals(guard.home()), "生きている護衛を再リンクできない");
            guard.discard();
        }).thenSucceed();
    }

    private static AndroidStationBlockEntity station(GameTestHelper helper) {
        helper.setBlock(STATION, SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity station = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(helper, STATION);
        station.setOwner(UUID.randomUUID());
        station.energy().setEnergy(AndroidStationBlockEntity.CAPACITY);
        station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_MEMORY, item(AndroidKind.BLADE.memoryId(), 1));
        station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_KIT, item("android_kit", 2));
        station.items().setStackInSlot(AndroidStationBlockEntity.SLOT_UPGRADE, item("android_transfer_upgrade", 1));
        return station;
    }

    private static RuinAndroid printedGuard(GameTestHelper helper, AndroidStationBlockEntity station) {
        var guards = helper.getLevel().getEntitiesOfClass(RuinAndroid.class, new AABB(station.getBlockPos()).inflate(8),
                guard -> guard.isAlive() && station.manages(guard.getUUID()));
        helper.assertTrue(guards.size() == 1, "ステーションに護衛が1体いない");
        var guard = guards.getFirst();
        guard.setNoAi(true);
        guard.setNoGravity(true);
        helper.assertTrue(guard.printed(), "護衛の印刷中");
        return guard;
    }

    private static void assertRepairKits(GameTestHelper helper, RuinAndroid guard) {
        var kits = guard.inventory().getItem(RuinAndroid.SLOT_REPAIR);
        helper.assertTrue(kits.is(item("android_repair_kit", 1).getItem()) && kits.getCount() == 5, "後継機に自己修理キット5個が渡らない: " + kits);
    }

    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Singulo.id(id)), count);
    }
}
