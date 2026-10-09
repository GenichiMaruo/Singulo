package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.CatalystStabilizerItem;
import io.github.genichimaruo.singulo.item.DimensionalPocketItem;
import io.github.genichimaruo.singulo.item.UsesData;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.ruin.SealedContainerBlockEntity;
import io.github.genichimaruo.singulo.ruin.SealedContainerBlockEntity.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 封印コンテナと鍵、コンテナから出る道具（オーバークロック・チップ、触媒安定化剤、次元ポケット、重力ブーツ）。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class SealedGameTests {
    private static final String EMPTY = "empty";

    private SealedGameTests() {}

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    /** 遺構のコンテナは鍵がないと開かず、段階の鍵で解錠の演出のあとに開いて、中身が入っている。 */
    @GameTest(template = EMPTY, timeoutTicks = SealedContainerBlockEntity.UNSEAL_TICKS + 40)
    public static void sealedContainerOpensWithKey(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.SEALED_CONTAINERS.get(0).get());
        SealedContainerBlockEntity box = helper.getBlockEntity(pos);
        box.sealWithLoot();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        box.interact(player, ItemStack.EMPTY);
        box.interact(player, item("quantum_key"));
        helper.assertTrue(box.phase() == Phase.SEALED, "鍵なし・違う鍵で開いた");
        ItemStack key = item("magnetic_key");
        box.interact(player, key);
        helper.assertTrue(box.phase() == Phase.UNSEALING && key.isEmpty(), "正しい鍵で解錠が始まらない（鍵も減らない）");
        helper.succeedWhen(() -> {
            helper.assertTrue(box.phase() == Phase.OPEN, "解錠が終わらない");
            helper.assertFalse(box.isEmpty(), "中身が入っていない");
        });
    }

    /** 封印中に壊すと、中身ごと失われる（何も落ちない）。 */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void breakingSealedContainerLosesContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.SEALED_CONTAINERS.get(1).get());
        SealedContainerBlockEntity box = helper.getBlockEntity(pos);
        box.setItem(0, new ItemStack(Items.DIAMOND, 10));
        box.sealWithLoot();
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(3)).isEmpty(),
                    "封印中に壊したのに何か落ちた");
            helper.succeed();
        });
    }

    /** 自分で置いたコンテナは開いていて、鍵で封印でき、また鍵で開けると中身が残っている。 */
    @GameTest(template = EMPTY, timeoutTicks = SealedContainerBlockEntity.SEAL_TICKS + SealedContainerBlockEntity.UNSEAL_TICKS + 40)
    public static void craftedContainerCanBeSealedAndReopened(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.SEALED_CONTAINERS.get(0).get());
        SealedContainerBlockEntity box = helper.getBlockEntity(pos);
        helper.assertTrue(box.phase() == Phase.OPEN, "置いたばかりなのに封印されている");
        box.setItem(3, new ItemStack(Items.EMERALD, 5));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setShiftKeyDown(true);
        box.interact(player, item("magnetic_key"));
        helper.assertTrue(box.phase() == Phase.SEALING, "鍵を持ってスニークしても封印されない");
        helper.runAtTickTime(SealedContainerBlockEntity.SEAL_TICKS + 3, () -> {
            helper.assertTrue(box.phase() == Phase.SEALED, "封印が終わらない");
            helper.assertFalse(box.canTakeItem(box, 3, box.getItem(3)), "封印中に取り出せる");
            player.setShiftKeyDown(false);
            box.interact(player, item("magnetic_key"));
        });
        helper.runAtTickTime(SealedContainerBlockEntity.SEAL_TICKS + SealedContainerBlockEntity.UNSEAL_TICKS + 8, () -> {
            helper.assertTrue(box.phase() == Phase.OPEN, "また開かない");
            helper.assertTrue(box.getItem(3).is(Items.EMERALD) && box.getItem(3).getCount() == 5, "中身が残っていない");
            helper.succeed();
        });
    }

    /** オーバークロック・チップを挿した電解槽は、同じ時間でより多く水素を作る（電力は十分にある）。 */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void overclockChipSpeedsUpMachine(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(3, 1, 1);
        helper.setBlock(new BlockPos(2, 1, 1), SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        for (BlockPos p : new BlockPos[]{a, b}) {
            helper.setBlock(p, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
            MachineBlockEntity m = helper.getBlockEntity(p);
            m.energy().setEnergy(m.energy().getMaxEnergyStored());
            m.automationFluids().fill(new FluidStack(Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE);
        }
        MachineBlockEntity fast = helper.getBlockEntity(b);
        helper.assertTrue(fast.slotAccepts(fast.type().upgradeSlot(), item("overclock_chip")), "アップグレード枠に入らない");
        fast.items().setStackInSlot(fast.type().upgradeSlot(), item("overclock_chip"));
        helper.runAtTickTime(80, () -> {
            MachineBlockEntity slow = helper.getBlockEntity(a);
            int h1 = hydrogen(slow);
            int h2 = hydrogen(fast);
            helper.assertTrue(h2 > h1, "オーバークロックで速くならない: " + h1 + " / " + h2);
            helper.succeed();
        });
    }

    private static int hydrogen(MachineBlockEntity m) {
        return m.automationFluids().drain(new FluidStack(SinguloFluids.get("hydrogen"), 100000), IFluidHandler.FluidAction.SIMULATE).getAmount();
    }

    /** 触媒安定化剤は、触媒の残りの寿命を2倍にし、印をつける。 */
    @GameTest(template = EMPTY)
    public static void stabilizerDoublesCatalystLife(GameTestHelper helper) {
        ItemStack catalyst = item("muon_catalyst");
        catalyst.set(SinguloComponents.USES.get(), new UsesData(100, 1000, 0));
        CatalystStabilizerItem.stabilize(catalyst);
        UsesData d = catalyst.get(SinguloComponents.USES.get());
        helper.assertTrue(d.remaining() == 1800 && d.max() == 2000, "残りが2倍にならない: " + d);
        helper.assertTrue(CatalystStabilizerItem.stabilized(catalyst), "安定化の印がない");
        helper.succeed();
    }

    /** 次元ポケットの中身はプレイヤーに付いていて、開き直しても残る。 */
    @GameTest(template = EMPTY)
    public static void dimensionalPocketKeepsContents(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        DimensionalPocketItem.Pocket first = new DimensionalPocketItem.Pocket(player);
        first.setItem(5, new ItemStack(Items.GOLD_INGOT, 7));
        first.setChanged();
        DimensionalPocketItem.Pocket again = new DimensionalPocketItem.Pocket(player);
        helper.assertTrue(again.getItem(5).is(Items.GOLD_INGOT) && again.getItem(5).getCount() == 7, "ポケットの中身が残らない");
        helper.succeed();
    }

    /** 重力ブーツを履いていると落下ダメージを受けない。 */
    @GameTest(template = EMPTY)
    public static void gravityBootsPreventFallDamage(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(SinguloItems.GRAVITY_BOOTS.get()));
        float before = player.getHealth();
        player.causeFallDamage(30, 1.0F, player.damageSources().fall());
        helper.assertTrue(player.getHealth() == before, "重力ブーツで落下ダメージを受けた");
        helper.succeed();
    }
}
