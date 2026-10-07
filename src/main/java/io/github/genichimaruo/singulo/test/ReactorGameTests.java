package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.multiblock.MultiblockPart;
import io.github.genichimaruo.singulo.multiblock.Structures;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * ペンローズ・リアクターの確認。引力帯が周りのテストに届かないよう、別の組（batch）にして順番に流す。
 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ReactorGameTests {
    private static final String HUGE = "huge";
    private static final String BATCH = "reactor";
    private static final BlockPos CENTER = new BlockPos(7, 7, 7);
    private static final BlockPos CONTROLLER = CENTER.below(Structures.CONTROLLER_BELOW_CENTER);

    private ReactorGameTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    private static PenroseReactorBlockEntity build(GameTestHelper helper) {
        for (Map.Entry<BlockPos, MultiblockPart.Role> e : Structures.reactorLayout(CENTER).entrySet()) {
            Block block = switch (e.getValue()) {
                case GYRO_DRIVE -> SinguloBlocks.GYRO_DRIVE.get();
                case EXTRACTION_PORT -> SinguloBlocks.EXTRACTION_PORT.get();
                default -> SinguloBlocks.REACTOR_SHELL.get();
            };
            helper.setBlock(e.getKey(), block);
        }
        helper.setBlock(CONTROLLER, SinguloBlocks.CORE_CONTROLLER.get());
        return helper.getBlockEntity(CONTROLLER);
    }

    @GameTest(template = HUGE, batch = BATCH)
    public static void accretionEfficiencyFollowsSpin(GameTestHelper helper) {
        double still = PenroseReactorBlockEntity.efficiency(0);
        double extremal = PenroseReactorBlockEntity.efficiency(1);
        helper.assertTrue(Math.abs(still - 0.0572) < 0.001, "無回転の降着効率は約5.7%: " + still);
        helper.assertTrue(Math.abs(extremal - 0.4226) < 0.001, "最大スピンの降着効率は約42.3%: " + extremal);
        helper.assertTrue(PenroseReactorBlockEntity.efficiency(0.9) > PenroseReactorBlockEntity.efficiency(0.5),
                "スピンが大きいほど効率が上がらない");
        helper.succeed();
    }

    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 100)
    public static void reactorFormsAndPortsForwardEnergy(GameTestHelper helper) {
        PenroseReactorBlockEntity r = build(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(r.isFormed(), "13×13×13 のリアクターとして形成されない");
            helper.assertTrue(r.state() == PenroseReactorBlockEntity.State.DORMANT, "形成後は停止中のはず");
            IEnergyStorage viaPort = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                    helper.absolutePos(CENTER.offset(4, 4, 0)), null);
            helper.assertTrue(viaPort != null, "抽出ポートからリアクターの電力につながらない");
        });
    }

    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 400)
    public static void ignitionNeeds50GFEWithinTenSeconds(GameTestHelper helper) {
        PenroseReactorBlockEntity r = build(helper);
        r.items().setStackInSlot(PenroseReactorBlockEntity.SLOT_SEED, new ItemStack(item("singularity_seed")));
        helper.runAtTickTime(3, () -> helper.assertTrue(r.ignite(), "点火を始められない"));
        // ホライズン・バス並みの 300 MFE/t を注ぐ（50 GFE / 10 秒 = 250 MFE/t が必要）
        helper.onEachTick(() -> r.energy().receiveEnergy(300_000_000, false));
        helper.succeedWhen(() -> {
            helper.assertTrue(r.state() == PenroseReactorBlockEntity.State.RUNNING, "点火して稼働しない");
            helper.assertTrue(Math.abs(r.mass() - PenroseReactorBlockEntity.START_MASS) < 1, "最初の炉心質量が500でない");
            helper.assertTrue(r.items().getStackInSlot(PenroseReactorBlockEntity.SLOT_SEED).isEmpty(), "特異点の種が残っている");
        });
    }

    /** ポートの外側の空いた面（リアクターの部品でない、中心から遠ざかる向き）。 */
    private static net.minecraft.core.Direction outward(BlockPos port) {
        java.util.Set<BlockPos> parts = Structures.reactorLayout(CENTER).keySet();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = port.relative(d);
            if (!parts.contains(n) && !parts.contains(n.relative(d)) && n.distSqr(CENTER) > port.distSqr(CENTER)) {
                return d;
            }
        }
        throw new IllegalStateException("ポートの外側に空きがない: " + port);
    }

    /** 組み立てる前に抽出ポートへ敷いたホライズン・バスからも、点火の電力が入ること（形成したら接続し直す）。 */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 200)
    public static void ignitionPowerThroughPortCableLaidBeforeForming(GameTestHelper helper) {
        BlockPos port = CENTER.offset(4, 4, 0);
        net.minecraft.core.Direction d = outward(port);
        helper.setBlock(port.relative(d), SinguloBlocks.HORIZON_BUS.get());
        helper.setBlock(port.relative(d, 2), SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        PenroseReactorBlockEntity r = build(helper);
        r.items().setStackInSlot(PenroseReactorBlockEntity.SLOT_SEED, new ItemStack(item("singularity_seed")));
        helper.runAtTickTime(5, () -> helper.assertTrue(r.ignite(), "点火を始められない"));
        helper.succeedWhen(() -> helper.assertTrue(r.state() == PenroseReactorBlockEntity.State.RUNNING,
                "ポートにつないだケーブルから点火の電力が入らない: " + r.state()));
    }

    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 400)
    public static void ignitionFailsWithoutEnoughPowerButKeepsSeed(GameTestHelper helper) {
        PenroseReactorBlockEntity r = build(helper);
        r.items().setStackInSlot(PenroseReactorBlockEntity.SLOT_SEED, new ItemStack(item("singularity_seed")));
        helper.runAtTickTime(3, () -> helper.assertTrue(r.ignite(), "点火を始められない"));
        // 100 MFE/t（トポロジカル導線）では10秒で 20 GFE にしかならない
        helper.onEachTick(() -> r.energy().receiveEnergy(100_000_000, false));
        helper.runAtTickTime(260, () -> {
            helper.assertTrue(r.state() == PenroseReactorBlockEntity.State.DORMANT, "電力が足りないのに点火した");
            helper.assertTrue(!r.items().getStackInSlot(PenroseReactorBlockEntity.SLOT_SEED).isEmpty(), "失敗で種が失われた");
            helper.succeed();
        });
    }

    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 200)
    public static void eddingtonLimitCapsFuel(GameTestHelper helper) {
        PenroseReactorBlockEntity r = build(helper);
        r.items().setStackInSlot(PenroseReactorBlockEntity.SLOT_FUEL, new ItemStack(item("mass_pellet"), 64));
        // 無回転（η 約5.7%、1個あたり約1.1 GFE）なら、5秒ぶん投入しても蓄電（40 GFE）は満ちない
        helper.runAtTickTime(3, () -> r.forceCore(2000, 0));
        helper.runAtTickTime(103, () -> {
            // 炉心質量2000 なら毎秒2個。5秒で約10個
            int used = 64 - r.items().getStackInSlot(PenroseReactorBlockEntity.SLOT_FUEL).getCount();
            helper.assertTrue(used >= 8 && used <= 12, "エディントン限界（毎秒2個）で投入されない: " + used);
            helper.assertTrue(r.buffer() > 0, "発電していない");
            helper.assertTrue(r.mass() > 2000, "投入した質量の一部が炉心に加わらない: " + r.mass());
            helper.succeed();
        });
    }

    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 400)
    public static void smallCoreEvaporatesAndBursts(GameTestHelper helper) {
        PenroseReactorBlockEntity r = build(helper);
        helper.runAtTickTime(3, () -> r.forceCore(104, 0));
        helper.succeedWhen(() -> {
            helper.assertTrue(r.state() == PenroseReactorBlockEntity.State.DORMANT && r.mass() == 0,
                    "質量100を割っても蒸発しきらない: " + r.mass());
            helper.assertBlockPresent(SinguloBlocks.REACTOR_SHELL.get(), CENTER.offset(5, 4, 0));
        });
    }

    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 6200)
    public static void smallCoreRadiatesHawkingCondensate(GameTestHelper helper) {
        PenroseReactorBlockEntity r = build(helper);
        r.items().setStackInSlot(PenroseReactorBlockEntity.SLOT_HAWKING, new ItemStack(item("hawking_collector")));
        helper.runAtTickTime(3, () -> r.forceCore(300, 0));
        helper.succeedWhen(() -> helper.assertTrue(
                r.items().getStackInSlot(PenroseReactorBlockEntity.SLOT_OUT).is(item("hawking_condensate")),
                "質量150〜400で5分たってもホーキング凝縮体ができない"));
    }

    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 1400)
    public static void ergosphereYieldsExoticMatterAndSpinsDown(GameTestHelper helper) {
        PenroseReactorBlockEntity r = build(helper);
        r.items().setStackInSlot(PenroseReactorBlockEntity.SLOT_ERGO, new ItemStack(item("ergosphere_ring")));
        helper.runAtTickTime(3, () -> r.forceCore(1000, 0.95));
        helper.succeedWhen(() -> {
            helper.assertTrue(r.items().getStackInSlot(PenroseReactorBlockEntity.SLOT_OUT).is(item("exotic_matter")),
                    "エルゴ抽出でエキゾチック物質ができない");
            helper.assertTrue(r.spin() < 0.95, "エルゴ抽出でスピンが減らない");
        });
    }

    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 100)
    public static void tidalZoneAndPull(GameTestHelper helper) {
        PenroseReactorBlockEntity r = build(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, CENTER.offset(2, 0, 0));
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        ItemEntity drop = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.COBBLESTONE));
        Vec3 far = Vec3.atCenterOf(helper.absolutePos(CENTER.offset(3, -3, 5)));   // リングにもポートにも重ならない位置
        drop.setPos(far);
        drop.setNoGravity(true);
        drop.setDeltaMovement(Vec3.ZERO);   // 生成時のランダムな初速を消す
        helper.getLevel().addFreshEntity(drop);
        helper.runAtTickTime(3, () -> r.forceCore(3000, 0.5));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(zombie.getHealth() < 20 || zombie.isDeadOrDying(), "潮汐帯でダメージを受けない");
            double before = far.distanceTo(Vec3.atCenterOf(helper.absolutePos(CENTER)));
            double after = drop.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(CENTER)));
            helper.assertTrue(drop.isRemoved() || after < before - 0.5, "引力帯でアイテムが引き寄せられない: " + before + " -> " + after + " v=" + drop.getDeltaMovement());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 6200)
    public static void encapsulatorMakesSingularityCore(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.SINGULARITY_ENCAPSULATOR).get());
        MachineBlockEntity m = helper.getBlockEntity(pos);
        helper.onEachTick(() -> m.energy().setEnergy(m.energy().getMaxEnergyStored()));
        m.items().setStackInSlot(0, new ItemStack(item("degenerate_matter_shell")));
        m.items().setStackInSlot(1, new ItemStack(item("hawking_condensate"), 4));
        m.items().setStackInSlot(2, new ItemStack(item("exotic_matter"), 2));
        m.items().setStackInSlot(3, new ItemStack(item("time_crystal_catalyst")));
        m.items().setStackInSlot(4, new ItemStack(Items.NETHER_STAR));
        m.items().setStackInSlot(5, new ItemStack(item("anomaly_sample")));
        helper.succeedWhen(() -> helper.assertTrue(
                m.items().getStackInSlot(m.type().outputSlot()).is(item("singularity_core")), "シンギュラリティ・コアができない"));
    }

    // ------------------------------------------------------------------ ダークマター

    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 120)
    public static void haloCollectorGathersDarkMatterAndCoreAcceptsIt(GameTestHelper helper) {
        PenroseReactorBlockEntity r = build(helper);
        BlockPos collectorPos = new BlockPos(1, 1, 1);
        BlockPos tankPos = new BlockPos(2, 1, 1);
        helper.setBlock(collectorPos, SinguloBlocks.HALO_COLLECTOR.get());
        helper.setBlock(tankPos, SinguloBlocks.CONTAINMENT_TANK.get());
        io.github.genichimaruo.singulo.darkmatter.HaloCollectorBlockEntity collector = helper.getBlockEntity(collectorPos);
        io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity tank = helper.getBlockEntity(tankPos);
        helper.onEachTick(() -> {
            collector.energy().setEnergy(collector.energy().getMaxEnergyStored());
            tank.energy().setEnergy(tank.energy().getMaxEnergyStored());
        });
        helper.runAtTickTime(2, () -> r.forceCore(2000, 0));
        helper.runAtTickTime(5, () -> {
            var input = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(CONTROLLER), null);
            helper.assertTrue(input != null, "炉心制御装置にダークマターを入れられない");
            double before = r.mass();
            int taken = input.fill(io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity.darkMatter(1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(taken == 1000 && Math.abs(r.mass() - before - 10) < 1e-6,
                    "ダークマター1000 mB で炉心質量が10増えない: " + taken + " " + (r.mass() - before));
            int water = input.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(water == 0, "水まで炉心に入る");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(collector.rate() == 20, "炉心質量2000で毎秒20 mB にならない: " + collector.rate());
            helper.assertTrue(tank.amount() >= 30, "隣の重力閉じ込めタンクにダークマターが届かない: " + tank.amount());
        });
    }
}
