package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.InertialStabilizerBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.QuantumHeatEngineBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 段階3（量子）の装置の動作確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class Stage3GameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    private Stage3GameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    /** 装置を置き、テストの間ずっと電力を満たしておく（電源の代わり）。 */
    private static MachineBlockEntity poweredMachine(GameTestHelper helper, MachineType type) {
        helper.setBlock(POS, SinguloBlocks.MACHINES.get(type).get());
        MachineBlockEntity be = helper.getBlockEntity(POS);
        helper.onEachTick(() -> be.energy().setEnergy(be.energy().getMaxEnergyStored()));
        return be;
    }

    private static void fill(MachineBlockEntity be, String fluid, int amount) {
        be.automationFluids().fill(new FluidStack(SinguloFluids.get(fluid), amount), IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = EMPTY, timeoutTicks = 700)
    public static void synthesizerMakesEntangledPairs(GameTestHelper helper) {
        MachineBlockEntity m = poweredMachine(helper, MachineType.ENTANGLEMENT_SYNTHESIZER);
        m.items().setStackInSlot(0, new ItemStack(Items.QUARTZ, 2));
        m.items().setStackInSlot(1, new ItemStack(Items.AMETHYST_SHARD, 2));
        m.items().setStackInSlot(2, new ItemStack(Items.DIAMOND));
        m.items().setStackInSlot(3, new ItemStack(item("superconducting_wire")));
        m.items().setStackInSlot(4, new ItemStack(item("data_card_quantum_fragment")));
        fill(m, "liquid_nitrogen", 1000);
        helper.succeedWhen(() -> {
            ItemStack out = m.items().getStackInSlot(m.type().outputSlot());
            helper.assertTrue(out.is(item("entangled_element")) && out.getCount() == 2, "量子もつれ素子が2個1組で出ない");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void laserCoolerMakesColdAtoms(GameTestHelper helper) {
        MachineBlockEntity m = poweredMachine(helper, MachineType.LASER_COOLER);
        m.items().setStackInSlot(0, new ItemStack(item("stardust")));
        m.items().setStackInSlot(1, new ItemStack(Items.QUARTZ));
        m.items().setStackInSlot(2, new ItemStack(item("cold_atom_trap")));
        fill(m, "liquid_helium", 1000);
        helper.succeedWhen(() -> {
            ItemStack out = m.items().getStackInSlot(m.type().outputSlot());
            helper.assertTrue(out.is(item("cold_atoms")) && out.getCount() == 2, "冷却原子が2個出ない");
            helper.assertTrue(UsesHelper.remaining(m.items().getStackInSlot(2)) == 31, "冷却原子トラップの使用回数が1減らない");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 900)
    public static void laserCoolerMakesBoseCatalystFromSpentMuon(GameTestHelper helper) {
        MachineBlockEntity m = poweredMachine(helper, MachineType.LASER_COOLER);
        m.items().setStackInSlot(0, new ItemStack(item("cold_atoms"), 4));
        m.items().setStackInSlot(1, new ItemStack(item("superconducting_wire"), 2));
        m.items().setStackInSlot(2, new ItemStack(item("spent_muon_catalyst")));
        fill(m, "liquid_nitrogen", 1000);
        fill(m, "liquid_helium", 1000);
        helper.succeedWhen(() -> helper.assertTrue(
                m.items().getStackInSlot(m.type().outputSlot()).is(item("bose_condensate_catalyst")),
                "失活したミュオン触媒からボース凝縮触媒ができない"));
    }

    @GameTest(template = EMPTY, timeoutTicks = 2500)
    public static void echoResonatorCopiesShardKeepingTemplate(GameTestHelper helper) {
        MachineBlockEntity m = poweredMachine(helper, MachineType.ECHO_RESONATOR);
        m.items().setStackInSlot(0, new ItemStack(Items.ECHO_SHARD));
        m.items().setStackInSlot(1, new ItemStack(Items.SCULK, 16));
        m.items().setStackInSlot(2, new ItemStack(Items.AMETHYST_SHARD, 4));
        m.items().setStackInSlot(3, new ItemStack(item("entangled_element")));
        helper.succeedWhen(() -> {
            helper.assertTrue(m.items().getStackInSlot(m.type().outputSlot()).is(Items.ECHO_SHARD), "残響の欠片が複製されない");
            ItemStack template = m.items().getStackInSlot(0);
            helper.assertTrue(template.is(Items.ECHO_SHARD) && UsesHelper.remaining(template) == 15,
                    "型の欠片が消費されず使用回数だけ減る（16→15）");
        });
    }

    @GameTest(template = EMPTY)
    public static void quantumHeatEngineOutputDependsOnCatalystTier(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(5, 1, 5);
        helper.setBlock(a, SinguloBlocks.QUANTUM_HEAT_ENGINE.get());
        helper.setBlock(b, SinguloBlocks.QUANTUM_HEAT_ENGINE.get());
        QuantumHeatEngineBlockEntity bose = helper.getBlockEntity(a);
        QuantumHeatEngineBlockEntity muon = helper.getBlockEntity(b);
        bose.catalystSlot().insertItem(0, new ItemStack(item("bose_condensate_catalyst")), false);
        muon.catalystSlot().insertItem(0, new ItemStack(item("muon_catalyst")), false);
        helper.runAtTickTime(10, () -> {
            int full = bose.energy().getEnergyStored();
            int half = muon.energy().getEnergyStored();
            helper.assertTrue(full >= 200_000 * 9 && full <= 200_000 * 11, "ボース凝縮触媒で 200 kFE/t にならない: " + full);
            helper.assertTrue(Math.abs(half * 2 - full) <= 200_000, "1段下のミュオン触媒で出力が半分にならない: " + half);
            helper.succeed();
        });
    }

    /** 中央の土の真上で小さな爆発を起こす。隣のテストに届かない大きさにしてある。 */
    private static void blastDirt(GameTestHelper helper, boolean powered) {
        BlockPos stabilizerPos = new BlockPos(1, 1, 1);
        helper.setBlock(stabilizerPos, SinguloBlocks.INERTIAL_STABILIZER.get());
        InertialStabilizerBlockEntity stabilizer = helper.getBlockEntity(stabilizerPos);
        if (powered) {
            stabilizer.energy().setEnergy(stabilizer.energy().getMaxEnergyStored());
        }
        stabilizer.catalystSlot().insertItem(0, new ItemStack(item("bose_condensate_catalyst")), false);
        BlockPos dirt = new BlockPos(4, 1, 4);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.runAtTickTime(5, () -> {
            BlockPos at = helper.absolutePos(dirt.above());
            helper.getLevel().explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 2.0F,
                    Level.ExplosionInteraction.TNT);
        });
        helper.runAtTickTime(10, () -> {
            if (powered) {
                helper.assertBlockPresent(Blocks.DIRT, dirt);
            } else {
                helper.assertBlockNotPresent(Blocks.DIRT, dirt);
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void inertialStabilizerStopsExplosionDamage(GameTestHelper helper) {
        blastDirt(helper, true);
    }

    /** 対照: 電力のないスタビライザーは守らない（同じ爆発で土が壊れることの確認）。 */
    @GameTest(template = EMPTY)
    public static void unpoweredStabilizerDoesNotProtect(GameTestHelper helper) {
        blastDirt(helper, false);
    }

    @GameTest(template = EMPTY)
    public static void gauntletTargetsAndCharging(GameTestHelper helper) {
        helper.assertTrue(GravityGauntletItem.canAffect(helper.spawn(EntityType.ZOMBIE, POS)), "ゾンビ（HP20）を操れない");
        helper.assertTrue(!GravityGauntletItem.canAffect(helper.spawn(EntityType.IRON_GOLEM, POS.east(2))),
                "アイアンゴーレム（HP100）を操れてしまう");
        helper.assertTrue(!GravityGauntletItem.canAffect(helper.spawn(EntityType.ENDERMAN, POS.west())),
                "重力耐性のエンダーマンを操れてしまう");
        ItemStack gauntlet = new ItemStack(SinguloItems.INERTIAL_CONTROL_GAUNTLET.get());
        IEnergyStorage energy = GravityGauntletItem.energy(gauntlet);
        helper.assertTrue(energy.receiveEnergy(50_000, false) == 50_000, "充電できない");
        helper.assertTrue(GravityGauntletItem.energy(gauntlet).getEnergyStored() == 50_000, "電力がアイテムに残らない");
        helper.succeed();
    }
}
