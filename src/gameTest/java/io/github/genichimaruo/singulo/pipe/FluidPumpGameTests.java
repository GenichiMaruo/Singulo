package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.cable.CableBlock;
import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class FluidPumpGameTests {
    private static final BlockPos PUMP = new BlockPos(3, 2, 3);

    private static FluidPumpBlockEntity pump(GameTestHelper h) {
        h.setBlock(PUMP, SinguloBlocks.FLUID_PUMP.get());
        return io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, PUMP);
    }

    private static FluidDronePortBlockEntity port(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, SinguloBlocks.FLUID_DRONE_PORT.get());
        return io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
    }

    private static FluidStack lava(int amount) { return new FluidStack(Fluids.LAVA, amount); }

    private static SmesCellBlockEntity cell(GameTestHelper h, BlockPos at, Direction front) {
        h.setBlock(at, SinguloBlocks.SMES_CELL.get().defaultBlockState().setValue(AbstractMachineBlock.FACING, front));
        return io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, at);
    }

    private static final class TankChest extends ChestBlockEntity {
        final FluidTank tank;
        TankChest(BlockPos pos, FluidTank tank) {
            super(pos, Blocks.CHEST.defaultBlockState());
            this.tank = tank;
        }
    }

    private static void registerTestTank() {
        var event = new io.github.genichimaruo.singulo.compat.RegisterCapabilitiesEvent();
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BlockEntityType.CHEST,
                (chest, face) -> chest instanceof TankChest test ? test.tank : null);
    }

    private static void attach(GameTestHelper h, BlockPos at, FluidTank tank) {
        h.setBlock(at, Blocks.CHEST);
        registerTestTank();
        h.getLevel().setBlockEntity(new TankChest(h.absolutePos(at), tank));
    }

    @GameTest(template = "empty")
    public static void pumpPlacementAndOutputControls(GameTestHelper h) {
        var pos = h.absolutePos(PUMP);
        var block = SinguloBlocks.FLUID_PUMP.get();
        for (Direction face : Direction.values()) {
            var context = new DirectionalPlaceContext(h.getLevel(), pos, face, new ItemStack(block), face);
            var state = block.getStateForPlacement(context);
            h.assertTrue(state.getValue(FluidPumpBlock.INTAKE) == face.getOpposite(), "Intake did not face clicked tank");
            h.setBlock(PUMP, state);
            for (Direction side : Direction.values()) {
                h.assertTrue((io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.FluidHandler.BLOCK, pos, side) != null)
                        == (side != face.getOpposite()), "Wrong outlet on " + side);
                h.assertTrue(io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.EnergyStorage.BLOCK, pos, side).canReceive(), "No FE on " + side);
            }
        }
        var pump = pump(h);
        port(h, PUMP.north()).receiveTank().fill(lava(500), FluidAction.EXECUTE);
        h.assertTrue(pump.energy().receiveEnergy(500, false) == 0, "Idle pump accepted a charge");
        var battery = cell(h, PUMP.south(), Direction.NORTH);
        battery.energy().setEnergy(500);
        var player = TestScope.player(h);
        player.setPos(Vec3.atCenterOf(pos));
        h.setBlock(PUMP.east(), SinguloBlocks.FLUID_PIPE.get());
        var connection = CableBlock.CONNECTIONS.get(Direction.WEST);
        var handle = pump.outlet(Direction.EAST);
        h.assertTrue(!pump.onButton(player, Direction.NORTH.get3DDataValue()), "Intake toggled");
        h.assertTrue(pump.onButton(player, Direction.EAST.get3DDataValue()) && !pump.open(Direction.EAST)
                && !h.getBlockState(PUMP.east()).getValue(connection) && handle.drain(500, FluidAction.EXECUTE).isEmpty(), "Disabled face still delivered");
        h.assertTrue(pump.onButton(player, Direction.EAST.get3DDataValue()) && pump.open(Direction.EAST)
                && h.getBlockState(PUMP.east()).getValue(connection), "Could not reopen face");
        h.assertTrue(handle.drain(500, FluidAction.SIMULATE).getAmount() == 500 && battery.energy().getEnergyStored() == 500, "Simulation charged FE");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pumpTransfersAvailableVolumeAndChargesOnlyDelivery(GameTestHelper h) {
        var source = port(h, PUMP.north()).receiveTank();
        var tank = port(h, PUMP.east()).sendTank();
        var pump = pump(h);
        source.fill(lava(110_000), FluidAction.EXECUTE);
        double oldCost = ServerConfig.PUMP_FE_PER_MB.get();
        try {
            ServerConfig.PUMP_FE_PER_MB.set(3.0);
            h.assertTrue(pump.energy().getMaxEnergyStored() == 0 && pump.energy().receiveEnergy(150_002, true) == 150_000
                    && source.getFluidAmount() == 110_000 && tank.isEmpty(), "Simulation moved fluid or quoted unusable FE");
            int spent = pump.energy().receiveEnergy(150_002, false);
            h.assertTrue(tank.getFluidAmount() == 50_000 && source.getFluidAmount() == 60_000 && spent == 150_000,
                    "Did not deliver the full affordable batch during the offer");
            int charged = pump.energy().receiveEnergy(300_000, false);
            h.assertTrue(tank.getFluidAmount() == tank.getCapacity() && charged == (tank.getCapacity() - 50_000) * 3,
                    "Charged for more than receiver capacity");
            h.assertTrue(pump.energy().receiveEnergy(Integer.MAX_VALUE, true) == 0
                    && pump.energy().receiveEnergy(Integer.MAX_VALUE, false) == 0 && pump.energy().getEnergyStored() == 0
                    && source.getFluidAmount() + tank.getFluidAmount() == 110_000, "Full output lost fluid or power");

            tank.drain(2000, FluidAction.EXECUTE);
            ServerConfig.PUMP_FE_PER_MB.set((double) Integer.MAX_VALUE);
            int before = tank.getFluidAmount();
            h.assertTrue(pump.energy().receiveEnergy(Integer.MAX_VALUE - 1, false) == 0, "Banked insufficient power");
            h.assertTrue(pump.energy().receiveEnergy(Integer.MAX_VALUE, false) == Integer.MAX_VALUE
                    && tank.getFluidAmount() == before + 1, "Maximum unit cost overflowed");
            h.assertTrue(pump.outlet(Direction.WEST).drain(5, FluidAction.EXECUTE).isEmpty(), "Unpowered extraction");
            ServerConfig.PUMP_FE_PER_MB.set(0.001);
            h.assertTrue(pump.energy().receiveEnergy(1, false) == 1 && tank.getFluidAmount() == before + 1001,
                    "Minimum unit cost did not deliver one bucket per FE");
        } finally {
            ServerConfig.PUMP_FE_PER_MB.set(oldCost);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fractionalPumpCostSurvivesPickupAndSmallTransfers(GameTestHelper h) {
        double oldPrice = ServerConfig.PUMP_FE_PER_MB.get();
        try {
            ServerConfig.PUMP_FE_PER_MB.set(0.1);
            var source = port(h, PUMP.north()).receiveTank();
            source.fill(lava(1001), FluidAction.EXECUTE);
            var pump = pump(h);
            var battery = cell(h, PUMP.south(), Direction.NORTH);
            battery.energy().setEnergy(100);
            var outlet = pump.outlet(Direction.WEST);
            for (int i = 0; i < 999; i++) {
                h.assertTrue(outlet.drain(1, FluidAction.SIMULATE).getAmount() == 1 && outlet.drain(1, FluidAction.EXECUTE).getAmount() == 1,
                        "Fractional billing stopped early at " + i);
            }
            h.assertTrue(battery.energy().getEnergyStored() == 0 && source.getFluidAmount() == 2, "Unexpected power after 999 separate deliveries");

            // At zero whole FE, the remaining 0.1 FE credit alone must survive harvesting.
            var pos = pump.getBlockPos();
            var drop = Block.getDrops(pump.getBlockState(), h.getLevel(), pos, pump).get(0);
            h.setBlock(PUMP, net.minecraft.world.level.block.Blocks.AIR);
            var restored = pump(h);
            net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(h.getLevel(), null, pos, drop);
            var sink = port(h, PUMP.east()).sendTank();
            FluidPumpBlockEntity.tick(h.getLevel(), pos, restored.getBlockState(), restored);
            h.assertTrue(sink.getFluidAmount() == 1 && source.getFluidAmount() == 1 && restored.energy().getEnergyStored() == 0,
                    "The saved credit must pay for exactly the thousandth mB");
            h.assertTrue(restored.outlet(Direction.WEST).drain(1, FluidAction.EXECUTE).isEmpty(), "An unpaid extra mB was delivered");
        } finally {
            ServerConfig.PUMP_FE_PER_MB.set(oldPrice);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pumpRetainsShortFillAcrossHarvest(GameTestHelper h) {
        var source = port(h, PUMP.north()).receiveTank();
        var pump = pump(h);
        source.fill(lava(5000), FluidAction.EXECUTE);
        var changesMind = new FluidTank(5000) {
            @Override public int fill(FluidStack offered, FluidAction action) {
                return super.fill(action.execute() ? io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(offered, Math.min(700, offered.getAmount())) : offered, action);
            }
        };
        attach(h, PUMP.east(), changesMind);
        h.assertTrue(pump.energy().receiveEnergy(500, true) == 124 && source.getFluidAmount() == 5000,
                "Simulation spent power or moved fluid");
        h.assertTrue(pump.energy().receiveEnergy(500, false) == 104 && source.isEmpty()
                && changesMind.getFluidAmount() == 700, "Billed for rejected fluid");
        var pos = pump.getBlockPos();
        var player = TestScope.player(h);
        player.setPos(Vec3.atCenterOf(pos));
        h.assertTrue(pump.onButton(player, Direction.EAST.get3DDataValue()), "Could not disable east");
        var drops = Block.getDrops(pump.getBlockState(), h.getLevel(), pos, pump);
        h.assertTrue(drops.size() == 1, "Missing pump drop");
        h.setBlock(PUMP, net.minecraft.world.level.block.Blocks.AIR);
        var restored = pump(h);
        net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(h.getLevel(), null, pos, drops.get(0));
        h.assertTrue(restored.outlet(Direction.EAST) == null, "Harvest lost face settings");
        var sink = port(h, PUMP.west()).sendTank();
        h.assertTrue(restored.energy().receiveEnergy(500, false) == 121 && sink.getFluidAmount() == 4300,
                "Harvest lost retained lava or charged twice");
        h.assertTrue(changesMind.getFluidAmount() + sink.getFluidAmount() == 5000, "Fluid not conserved");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pumpFillsMultipleOutletsAndHandlesMaxFluidAmount(GameTestHelper h) {
        var source = port(h, PUMP.north()).receiveTank();
        var left = port(h, PUMP.west()).sendTank();
        var right = port(h, PUMP.east()).sendTank();
        var pump = pump(h);
        source.fill(lava(112_345), FluidAction.EXECUTE);
        h.assertTrue(pump.energy().receiveEnergy(120_000, true) == 635 && source.getFluidAmount() == 112_345,
                "Simulation double counted the intake");
        int paid = pump.energy().receiveEnergy(120_000, false);
        h.assertTrue(left.getFluidAmount() + right.getFluidAmount() == 112_345 && source.isEmpty()
                && paid == 635, "Remaining volume did not spill to the other open face");

        h.setBlock(PUMP.east(), Blocks.AIR);
        h.setBlock(PUMP.west(), Blocks.AIR);
        source.setFluid(lava(Integer.MAX_VALUE));
        var destination = new FluidTank(Integer.MAX_VALUE);
        attach(h, PUMP.above(), destination);
        h.assertTrue(pump.energy().receiveEnergy(Integer.MAX_VALUE, true) == 10_226_011
                && pump.energy().receiveEnergy(Integer.MAX_VALUE, false) == 10_226_011
                && destination.getFluidAmount() == Integer.MAX_VALUE && source.isEmpty(), "Full-size transfer overflowed");

        // Creative sources can fulfill multiple int-sized drain calls during a single FE offer.
        var unlimited = new FluidTank(Integer.MAX_VALUE) {
            @Override public FluidStack drain(int amount, FluidAction action) { return super.drain(amount, FluidAction.SIMULATE); }
        };
        unlimited.setFluid(lava(Integer.MAX_VALUE));
        attach(h, PUMP.north(), unlimited);
        destination.setFluid(FluidStack.EMPTY);
        var second = new FluidTank(Integer.MAX_VALUE);
        attach(h, PUMP.west(), second);
        h.assertTrue(pump.energy().receiveEnergy(11_428_457, false) == 11_428_457
                && (long) destination.getFluidAmount() + second.getFluidAmount() == 2_400_000_112L,
                "Clipped the aggregate routed through two outlets to one FluidStack");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void horizonDuctDeliversTwoPointOneBillionMbEveryTick(GameTestHelper h) {
        int volume = 2_100_000_000;
        int cost = 10_000_000;
        int duration = 20;
        var source = new FluidTank(Integer.MAX_VALUE);
        var sink = new FluidTank(volume);
        attach(h, PUMP.north(), source);
        attach(h, PUMP.east(3), sink);
        pump(h);
        h.setBlock(PUMP.east(), SinguloBlocks.HORIZON_DUCT.get());
        h.setBlock(PUMP.east(2), SinguloBlocks.HORIZON_DUCT.get());
        BlockPos bus = PUMP.west();
        h.setBlock(bus, SinguloBlocks.HORIZON_BUS.get());
        var modules = new java.util.ArrayList<SmesCellBlockEntity>();
        for (Direction side : java.util.List.of(Direction.WEST, Direction.NORTH, Direction.SOUTH)) {
            BlockPos pos = bus.relative(side);
            h.setBlock(pos, SinguloBlocks.SMES_MODULE.get().defaultBlockState()
                    .setValue(AbstractMachineBlock.FACING, side.getOpposite()));
            SmesCellBlockEntity module = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
            modules.add(module);
        }
        Runnable supply = () -> {
            modules.get(0).energy().setEnergy(4_000_000);
            modules.get(1).energy().setEnergy(3_000_000);
            modules.get(2).energy().setEnergy(3_000_000);
        };
        h.runAtTickTime(4, () -> {
            supply.run();
            source.fill(lava(Integer.MAX_VALUE), FluidAction.EXECUTE);
        });
        for (int tick = 1; tick <= duration; tick++) {
            int elapsed = tick;
            h.runAtTickTime(4 + elapsed, () -> {
                long stored = modules.stream().mapToLong(module -> module.energy().getEnergyStored()).sum();
                h.assertTrue(source.getFluidAmount() == Integer.MAX_VALUE - volume && sink.getFluidAmount() == volume,
                        "Wrong per-tick intake or delivery at tick " + elapsed);
                h.assertTrue(stored == 0, "Wrong FE debit at tick " + elapsed);
                // Replenish the fixtures. The three modules, cable, pump and duct move the resources themselves.
                supply.run();
                source.fill(lava(volume), FluidAction.EXECUTE);
                if (elapsed < duration) sink.drain(volume, FluidAction.EXECUTE);
            });
        }
        h.runAtTickTime(5 + duration, () -> {
            long stored = modules.stream().mapToLong(module -> module.energy().getEnergyStored()).sum();
            h.assertTrue(source.getFluidAmount() == Integer.MAX_VALUE && stored == cost,
                    "A full output consumed fluid or power");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void pumpFeedsPipeWithinItsBottleneck(GameTestHelper h) {
        var source = port(h, PUMP.north()).receiveTank();
        var target = port(h, PUMP.east(2)).sendTank();
        var pump = pump(h);
        h.setBlock(PUMP.east(), SinguloBlocks.FLUID_PIPE.get());
        source.fill(lava(31_789), FluidAction.EXECUTE);
        int[] previous = {0};
        h.onEachTick(() -> {
            int charged = pump.energy().receiveEnergy(35_000, false);
            int now = target.getFluidAmount();
            int delivered = now - previous[0];
            long expected = delivered > 0 ? 100 + io.github.genichimaruo.singulo.compat.Legacy.ceilDiv((delivered - 1L) * 9_999_900L, 2_099_999_999L) : 0;
            h.assertTrue(delivered <= 10_000 && now + source.getFluidAmount() == 31_789
                    && charged == expected, "Pipe capacity or accounting mismatch");
            previous[0] = now;
        });
        h.succeedWhen(() -> h.assertTrue(target.getFluidAmount() == 31_789, "Fluid did not pass through pipe"));
    }
    @GameTest(template = "empty")
    public static void smallCableOfferFeedsMachineBeforeNearerPump(GameTestHelper h) {
        var pump = pump(h);
        var source = port(h, PUMP.north()).receiveTank();
        var sink = port(h, PUMP.east()).sendTank();
        source.fill(lava(1000), FluidAction.EXECUTE);
        h.setBlock(PUMP.west(), SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        h.setBlock(PUMP.west(2), SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        var machine = cell(h, PUMP.west(2).south(), Direction.SOUTH);
        int capacity = machine.energy().getMaxEnergyStored();
        machine.energy().setEnergy(capacity - 70);
        var wire = io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.EnergyStorage.BLOCK, h.absolutePos(PUMP.west()), Direction.UP);
        h.assertTrue(wire.receiveEnergy(100, true) == 70 && sink.isEmpty() && machine.energy().getEnergyStored() == capacity - 70,
                "Cable quote caused side effects");
        h.assertTrue(wire.receiveEnergy(100, false) == 70 && machine.energy().getEnergyStored() == capacity
                && sink.isEmpty() && pump.energy().getEnergyStored() == 0, "Pump displaced another load");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void adjacentThermoelectricGeneratorKeepsUnusedPower(GameTestHelper h) {
        var pump = pump(h);
        var source = port(h, PUMP.north()).receiveTank();
        h.setBlock(PUMP.above(), SinguloBlocks.THERMOELECTRIC_GENERATOR.get());
        ThermoelectricGeneratorBlockEntity generator = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, PUMP.above());
        var machine = cell(h, PUMP.above(2), Direction.SOUTH);
        int capacity = machine.energy().getMaxEnergyStored();
        machine.energy().setEnergy(capacity - 50);
        generator.energy().setEnergy(100);
        ThermoelectricGeneratorBlockEntity.serverTick(h.getLevel(), generator.getBlockPos(), generator.getBlockState(), generator);
        h.assertTrue(machine.energy().getEnergyStored() == capacity && generator.energy().getEnergyStored() == 50,
                "Idle pump drained the generator");
        var sink = port(h, PUMP.east()).sendTank();
        source.fill(lava(200), FluidAction.EXECUTE);
        machine.energy().setEnergy(capacity - 35);
        ThermoelectricGeneratorBlockEntity.serverTick(h.getLevel(), generator.getBlockPos(), generator.getBlockState(), generator);
        h.assertTrue(machine.energy().getEnergyStored() == capacity && sink.isEmpty()
                && generator.energy().getEnergyStored() == 15, "Directly attached pump took priority");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pumpSharesTickPriceAcrossPullPushAndReload(GameTestHelper h) {
        var pump = pump(h);
        var source = port(h, PUMP.north()).receiveTank();
        var sink = port(h, PUMP.east()).sendTank();
        var battery = cell(h, PUMP.south(), Direction.NORTH);
        battery.energy().setEnergy(104);
        source.fill(lava(1000), FluidAction.EXECUTE);
        h.assertTrue(pump.energy().receiveEnergy(99, false) == 0 && source.getFluidAmount() == 1000,
                "An offer below the starting price changed the intake");
        var outlet = pump.outlet(Direction.WEST);
        h.assertTrue(outlet.drain(1, FluidAction.SIMULATE).getAmount() == 1 && battery.energy().getEnergyStored() == 104,
                "A pull quote consumed FE");
        h.assertTrue(outlet.drain(1, FluidAction.EXECUTE).getAmount() == 1 && battery.energy().getEnergyStored() == 4,
                "The first mB must cost exactly 100 FE");
        h.assertTrue(outlet.drain(499, FluidAction.EXECUTE).getAmount() == 499 && battery.energy().getEnergyStored() == 1,
                "The second pull did not use the tick's cumulative price");

        var registries = h.getLevel().registryAccess();
        pump.load(pump.saveWithoutMetadata());
        h.assertTrue(pump.energy().receiveEnergy(1000, true) == 2 && sink.isEmpty()
                && pump.energy().receiveEnergy(2, false) == 2 && sink.getFluidAmount() == 500 && source.isEmpty(),
                "A reload, simulation or second power source charged another activation fee");
        h.runAfterDelay(1, () -> {
            source.fill(lava(1), FluidAction.EXECUTE);
            h.assertTrue(pump.energy().receiveEnergy(99, false) == 0 && pump.energy().receiveEnergy(100, false) == 100
                    && sink.getFluidAmount() == 501, "The next tick reused the previous activation fee");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void worldSourceRequiresOneWholeBucketOffer(GameTestHelper h) {
        var pump = pump(h);
        var sink = port(h, PUMP.east()).sendTank();
        h.setBlock(PUMP.north(), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
        for (int i = 0; i < 2; i++) {
            h.assertTrue(pump.energy().receiveEnergy(104, true) == 0 && pump.energy().receiveEnergy(104, false) == 0,
                    "Banked an offer too small to pick up a source");
        }
        h.assertTrue(h.getBlockState(PUMP.north()).getFluidState().isSource() && sink.isEmpty(), "Partial bucket extracted");
        h.assertTrue(pump.energy().receiveEnergy(105, true) == 105 && pump.energy().receiveEnergy(105, false) == 105
                && sink.getFluidAmount() == 1000 && h.getBlockState(PUMP.north()).getFluidState().isEmpty(),
                "A bucket-sized offer did not pick up exactly one source");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyChargeIsSpentOnceAndNeverRefilled(GameTestHelper h) {
        var pump = pump(h);
        var tag = pump.saveWithoutMetadata();
        tag.putInt("energy", 100);
        pump.load(tag);
        h.assertTrue(pump.energy().receiveEnergy(500, false) == 0 && pump.energy().getMaxEnergyStored() == 0,
                "Migration opened a charge buffer");
        var source = port(h, PUMP.north()).receiveTank();
        var sink = port(h, PUMP.east()).sendTank();
        source.fill(lava(1000), FluidAction.EXECUTE);
        FluidPumpBlockEntity.tick(h.getLevel(), pump.getBlockPos(), pump.getBlockState(), pump);
        h.assertTrue(sink.getFluidAmount() == 1 && !pump.saveWithoutMetadata().contains("energy"),
                "Legacy FE did not retire after paying for fluid");
        h.assertTrue(pump.energy().receiveEnergy(1000, false) == 5 && sink.getFluidAmount() == 1000
                && pump.energy().receiveEnergy(1000, false) == 0, "Fresh offers created a new reserve");
        h.succeed();
    }

}
