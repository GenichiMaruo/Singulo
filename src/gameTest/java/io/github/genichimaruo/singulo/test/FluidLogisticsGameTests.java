package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.drone.CargoDrone;
import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import io.github.genichimaruo.singulo.drone.DroneNetwork;
import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;
import io.github.genichimaruo.singulo.drone.DroneStationBlockEntity;
import io.github.genichimaruo.singulo.drone.FluidCapsuleItem;
import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.pipe.FluidPipeBlock;
import io.github.genichimaruo.singulo.pipe.FluidPipeBlockEntity;
import io.github.genichimaruo.singulo.pipe.FluidPipeNetwork;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 0.17.0: 液体パイプの段階（区間のまとめ・いちばん細いところの制限）、液体カプセル、液体のドローン発着ポート。 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class FluidLogisticsGameTests {
    /** ドローンは高く飛んで、となりのテストの場所の上を通るので、ドローンのテストと同じ組で動かす。 */
    private static final String DRONES = "drones";
    private static final BlockPos STATION = new BlockPos(1, 1, 1), FROM = new BlockPos(1, 1, 7), TO = new BlockPos(7, 1, 7);

    private static UUID owner(GameTestHelper h) {
        return UUID.nameUUIDFromBytes(("fluid-drone-test" + h.absolutePos(BlockPos.ZERO)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void pipe(GameTestHelper h, BlockPos at, Block block) {
        h.setBlock(at, block);
    }

    private static FluidDronePortBlockEntity fluidPort(GameTestHelper h, BlockPos at) {
        h.setBlock(at, SinguloBlocks.FLUID_DRONE_PORT.get());
        FluidDronePortBlockEntity port = h.getBlockEntity(at);
        port.setOwner(owner(h));
        port.energy().setEnergy(DronePortBlockEntity.CAPACITY);
        DroneNetwork.get(h.getLevel()).putPort(h.absolutePos(at), owner(h), "", true);
        return port;
    }

    @GameTest(template = "empty")
    public static void pipeTiersCarryMoreAndConnectToEachOther(GameTestHelper h) {
        FluidPipeBlock[] tiers = {SinguloBlocks.FLUID_PIPE.get(), SinguloBlocks.VACUUM_JACKETED_PIPE.get(),
                SinguloBlocks.SUPERFLUID_CONDUIT.get(), SinguloBlocks.HORIZON_DUCT.get()};
        for (int i = 1; i < tiers.length; i++) {
            h.assertTrue(tiers[i].capacity() > tiers[i - 1].capacity() && tiers[i].profile().tube >= tiers[i - 1].profile().tube,
                    "Tier " + i + " is not larger than the one below");
        }
        h.assertTrue(tiers[3].capacity() == Long.MAX_VALUE, "Horizon Duct should be unlimited");
        // 段階の違うパイプをつなぐと、1つのネットワークになる
        for (int i = 0; i < tiers.length; i++) {
            pipe(h, new BlockPos(2 + i, 1, 2), tiers[i]);
        }
        h.succeedWhen(() -> {
            int[] stats = FluidPipeNetwork.statsForTest(h.getBlockEntity(new BlockPos(2, 1, 2)));
            h.assertTrue(stats != null && stats[0] == 4, "Mixed tiers did not join one network: " + java.util.Arrays.toString(stats));
        });
    }

    /** 曲がり角を含む長い1本は、両端の分かれ目2つと区間1つにまとまる（毎周期の仕事がパイプの長さで増えない）。 */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void longPipeRunCollapsesIntoOneStretch(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(0, 1, 6);
        h.setBlock(a, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        h.setBlock(b, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity giver = h.getBlockEntity(a), taker = h.getBlockEntity(b);
        for (int x = 2; x <= 6; x++) {
            pipe(h, new BlockPos(x, 1, 1), SinguloBlocks.FLUID_PIPE.get());
        }
        for (int z = 2; z <= 6; z++) {
            pipe(h, new BlockPos(6, 1, z), SinguloBlocks.VACUUM_JACKETED_PIPE.get());
        }
        for (int x = 5; x >= 1; x--) {
            pipe(h, new BlockPos(x, 1, 6), SinguloBlocks.FLUID_PIPE.get());
        }
        giver.forceOutputTankForTest(new FluidStack(Fluids.WATER, 2_000));
        h.succeedWhen(() -> {
            int[] stats = FluidPipeNetwork.statsForTest(h.getBlockEntity(new BlockPos(6, 1, 3)));
            h.assertTrue(stats != null && stats[0] == 15 && stats[1] == 1 && stats[2] == 2,
                    "Expected 15 pipes in 1 stretch between 2 junctions: " + java.util.Arrays.toString(stats));
            h.assertTrue(taker.automationFluids().getFluidInTank(0).getAmount()
                    + taker.automationFluids().getFluidInTank(1).getAmount() >= 2_000, "Water did not reach the end");
        });
    }

    /**
     * いちばん細いパイプが1周期の量を決める（真空断熱パイプの列に液体パイプが1本あると、5 tick に 10,000 mB まで）。
     * 全部が真空断熱パイプなら、64,000 mB が1周期で移る。液体の総量は変わらない。
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void narrowestPipeLimitsTheFlow(GameTestHelper h) {
        FluidDronePortBlockEntity[] from = new FluidDronePortBlockEntity[2], to = new FluidDronePortBlockEntity[2];
        for (int lane = 0; lane < 2; lane++) {
            int z = 1 + lane * 4;
            from[lane] = fluidPort(h, new BlockPos(1, 1, z));
            to[lane] = fluidPort(h, new BlockPos(6, 1, z));
            for (int x = 2; x <= 5; x++) {
                pipe(h, new BlockPos(x, 1, z), lane == 0 && x == 4 ? SinguloBlocks.FLUID_PIPE.get() : SinguloBlocks.VACUUM_JACKETED_PIPE.get());
            }
            from[lane].receiveTank().fill(new FluidStack(Fluids.WATER, 64_000), IFluidHandler.FluidAction.EXECUTE);
        }
        int[] last = {0, 0};
        int[] biggest = {0, 0};
        h.onEachTick(() -> {
            for (int lane = 0; lane < 2; lane++) {
                int now = to[lane].sendTank().getFluidAmount();
                biggest[lane] = Math.max(biggest[lane], now - last[lane]);
                last[lane] = now;
                h.assertTrue(now + from[lane].receiveTank().getFluidAmount() == 64_000, "Fluid was created or lost in lane " + lane);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(last[0] == 64_000 && last[1] == 64_000, "Not all fluid arrived: " + last[0] + " / " + last[1]);
            h.assertTrue(biggest[0] <= 10_000, "A copper pipe let " + biggest[0] + " mB through in one tick");
            h.assertTrue(biggest[1] == 64_000, "Vacuum-jacketed pipes moved only " + biggest[1] + " mB at once");
        });
    }

    @GameTest(template = "empty")
    public static void fluidCapsulesStackOnlyWhenEmpty(GameTestHelper h) {
        ItemStack empty = new ItemStack(SinguloItems.FLUID_CAPSULE.get());
        h.assertTrue(DroneCrateItem.isFluid(empty) && DroneCrateItem.tier(empty) == 1 && !DroneCrateItem.packed(empty), "Empty capsule misread");
        ItemStack full = FluidCapsuleItem.pack(empty, new FluidStack(Fluids.LAVA, 100_000));
        h.assertTrue(DroneCrateItem.packed(full) && full.getMaxStackSize() == 1 && !full.canFitInsideContainerItems()
                && !DroneCrateItem.mayCarry(full), "A filled capsule must not stack or nest");
        h.assertTrue(FluidCapsuleItem.fluid(full).getAmount() == FluidCapsuleItem.CAPACITY, "Capsule overfilled");
        FluidCapsuleItem.setFluid(full, FluidStack.EMPTY);
        h.assertTrue(ItemStack.isSameItemSameComponents(full, new ItemStack(SinguloItems.FLUID_CAPSULE.get())), "Emptied capsule does not stack");
        h.assertTrue(DroneCrateItem.displayTier(new ItemStack(SinguloItems.LARGE_BATTERY_FLUID_CAPSULE.get())) == 6
                && DroneCrateItem.range(3) == 3160, "Capsule tiers do not follow crate tiers");
        h.succeed();
    }

    /** 液体のポートは液体のポートだけを行き先にし、空き箱の欄には液体カプセルだけを受ける。取り消すと液体・カプセル・電力が戻る。 */
    @GameTest(template = "empty", batch = DRONES)
    public static void fluidPortPacksCancelsAndKeepsToFluidPorts(GameTestHelper h) {
        FluidDronePortBlockEntity a = fluidPort(h, new BlockPos(1, 1, 1));
        fluidPort(h, new BlockPos(5, 1, 1));
        h.setBlock(new BlockPos(1, 1, 5), SinguloBlocks.DRONE_PORT.get());
        DroneNetwork net = DroneNetwork.get(h.getLevel());
        net.putPort(h.absolutePos(new BlockPos(1, 1, 5)), owner(h), "");
        List<BlockPos> targets = net.portsOf(owner(h), h.absolutePos(new BlockPos(1, 1, 1)), true);
        h.assertTrue(targets.equals(List.of(h.absolutePos(new BlockPos(5, 1, 1)))), "Fluid port lists item ports: " + targets);
        a.select(h.absolutePos(new BlockPos(1, 1, 5)));
        a.sendTank().fill(new FluidStack(Fluids.WATER, 30_000), IFluidHandler.FluidAction.EXECUTE);
        a.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.FLUID_CAPSULE.get(), 2));
        h.assertTrue(a.problem() == DronePortBlockEntity.S_NO_DESTINATION, "Fluid port accepted an item port as destination");
        h.assertFalse(a.items().isItemValid(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.PACKING_CRATE.get())),
                "Fluid port accepts packing crates");
        h.assertFalse(a.items().isItemValid(DronePortBlockEntity.SLOT_CARGO, new ItemStack(Items.IRON_INGOT)),
                "Fluid port accepts item cargo");
        int before = a.energy().getEnergyStored();
        a.stageForTest(h.absolutePos(new BlockPos(5, 1, 1)), 0);
        h.assertTrue(a.sendTank().isEmpty() && FluidCapsuleItem.fluid(a.staged()).getAmount() == 30_000
                && a.items().getStackInSlot(DronePortBlockEntity.SLOT_CRATES).getCount() == 1, "Packing did not move the fluid into a capsule");
        h.assertTrue(a.cancel(), "Cancel failed");
        h.assertTrue(a.sendTank().getFluidAmount() == 30_000 && a.staged().isEmpty() && a.energy().getEnergyStored() == before
                && a.items().getStackInSlot(DronePortBlockEntity.SLOT_CRATES).getCount() == 2, "Cancel did not restore fluid, capsule and power");
        h.succeed();
    }

    /** ステーションのドローンが、液体のポートからポートへカプセルを運ぶ。届いた液体は受け取りのタンクへ、空のカプセルは空き箱の欄へ。 */
    @GameTest(template = "large", batch = DRONES, timeoutTicks = 1200)
    public static void droneCarriesFluidCapsuleBetweenPorts(GameTestHelper h) {
        h.setBlock(STATION, SinguloBlocks.DRONE_STATION.get());
        DroneStationBlockEntity station = h.getBlockEntity(STATION);
        station.setOwner(owner(h));
        station.energy().setEnergy(DroneStationBlockEntity.CAPACITY);
        station.items().setStackInSlot(DroneStationBlockEntity.SLOT_MEMORY, new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("drone_memory"))));
        station.items().setStackInSlot(DroneStationBlockEntity.SLOT_KIT, new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("drone_kit"))));
        FluidDronePortBlockEntity a = fluidPort(h, FROM);
        FluidDronePortBlockEntity b = fluidPort(h, TO);
        a.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.FLUID_CAPSULE.get()));
        a.sendTank().fill(new FluidStack(Fluids.LAVA, 40_000), IFluidHandler.FluidAction.EXECUTE);
        a.select(h.absolutePos(TO));
        boolean[] sent = {false};
        h.succeedWhen(() -> {
            if (!sent[0]) {
                sent[0] = a.send();
                h.assertTrue(sent[0], "Waiting for the drone to be printed: " + a.status());
            }
            h.assertTrue(b.receiveTank().getFluid().getFluid() == Fluids.LAVA && b.receiveTank().getFluidAmount() == 40_000,
                    "Fluid not received yet: " + b.receiveTank().getFluidAmount() + " status " + a.status() + "/" + b.status());
            ItemStack back = b.items().getStackInSlot(DronePortBlockEntity.SLOT_CRATES);
            h.assertTrue(back.is(SinguloItems.FLUID_CAPSULE.get()) && !DroneCrateItem.packed(back), "Empty capsule did not move to the crate slot");
            h.assertTrue(DroneNetwork.get(h.getLevel()).flightOf(h.absolutePos(STATION), 0) == null, "Drone has not returned");
            BlockPos s = h.absolutePos(STATION);
            h.getLevel().getEntitiesOfClass(CargoDrone.class, new AABB(s).inflate(60, 400, 60), d -> s.equals(d.station()))
                    .forEach(CargoDrone::discard);
        });
    }

    /** パイプは液体の流れを、流れている間だけ見せる（流れた面と液体）。 */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void flowingPipeShowsItsFluid(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(5, 1, 1), mid = new BlockPos(3, 1, 1);
        h.setBlock(a, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        h.setBlock(b, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity giver = h.getBlockEntity(a);
        for (int x = 2; x <= 4; x++) {
            pipe(h, new BlockPos(x, 1, 1), SinguloBlocks.SUPERFLUID_CONDUIT.get());
        }
        h.onEachTick(() -> giver.forceOutputTankForTest(new FluidStack(Fluids.WATER, 500)));
        h.succeedWhen(() -> {
            FluidPipeBlockEntity pipe = h.getBlockEntity(mid);
            h.assertTrue(pipe.visualState() == FluidPipeBlockEntity.FLOWING && pipe.visualFluid().getFluid() == Fluids.WATER
                    && pipe.flowsIn(net.minecraft.core.Direction.WEST) && pipe.flowsOut(net.minecraft.core.Direction.EAST),
                    "Flow not shown: state " + pipe.visualState() + " fluid " + pipe.visualFluid());
        });
    }

    /** 液体のポートを壊すと、2つのタンクの中身をアイテムに写して落とし、置き直すと戻る（液体を失わない）。 */
    @GameTest(template = "empty")
    public static void brokenFluidPortKeepsItsTanks(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2);
        FluidDronePortBlockEntity port = fluidPort(h, at);
        port.sendTank().fill(new FluidStack(Fluids.WATER, 12_000), IFluidHandler.FluidAction.EXECUTE);
        port.receiveTank().fill(new FluidStack(Fluids.LAVA, 90_000), IFluidHandler.FluidAction.EXECUTE);
        h.getLevel().destroyBlock(h.absolutePos(at), true);
        var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(h.absolutePos(at)).inflate(2),
                e -> e.getItem().is(SinguloBlocks.FLUID_DRONE_PORT.get().asItem()));
        h.assertTrue(drops.size() == 1, "Expected one dropped port, got " + drops.size());
        ItemStack item = drops.get(0).getItem();
        var send = item.get(io.github.genichimaruo.singulo.registry.SinguloComponents.PORT_SEND_FLUID.get());
        var receive = item.get(io.github.genichimaruo.singulo.registry.SinguloComponents.PORT_RECEIVE_FLUID.get());
        h.assertTrue(send != null && send.copy().getAmount() == 12_000 && receive != null && receive.copy().getAmount() == 90_000
                && receive.copy().getFluid() == Fluids.LAVA, "Tank contents were not copied to the item");
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        h.setBlock(at, SinguloBlocks.FLUID_DRONE_PORT.get());
        FluidDronePortBlockEntity placed = h.getBlockEntity(at);
        placed.applyComponentsFromItemStack(item);
        h.assertTrue(placed.sendTank().getFluidAmount() == 12_000 && placed.receiveTank().getFluidAmount() == 90_000,
                "Placing the port did not restore its tanks");
        h.succeed();
    }

    /** 撤去モードの移設レンチは、液体パイプのつながった一続き（同じ段階）をまとめて回収する。 */
    @GameTest(template = "empty")
    public static void wrenchTakesBackAPipeRun(GameTestHelper h) {
        for (int x = 1; x <= 5; x++) {
            pipe(h, new BlockPos(x, 1, 2), x == 5 ? SinguloBlocks.FLUID_PIPE.get() : SinguloBlocks.VACUUM_JACKETED_PIPE.get());
        }
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        try {
            var wrench = new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
            io.github.genichimaruo.singulo.item.RelocationWrenchItem.setMode(wrench, 1);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wrench);
            player.setShiftKeyDown(true);
            BlockPos start = h.absolutePos(new BlockPos(1, 1, 2));
            wrench.getItem().onItemUseFirst(wrench, new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(start), net.minecraft.core.Direction.UP, start, false)));
            for (int x = 1; x <= 4; x++) {
                h.assertTrue(h.getBlockState(new BlockPos(x, 1, 2)).isAir(), "Pipe at " + x + " was not taken back");
            }
            h.assertTrue(h.getBlockState(new BlockPos(5, 1, 2)).is(SinguloBlocks.FLUID_PIPE.get()), "A pipe of another tier was taken too");
            h.assertTrue(player.getInventory().countItem(SinguloBlocks.VACUUM_JACKETED_PIPE.get().asItem()) == 4, "Pipes were not returned");
        } finally {
            player.discard();
        }
        h.succeed();
    }
}
