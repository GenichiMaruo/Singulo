package io.github.genichimaruo.singulo.cable;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;

@net.minecraftforge.gametest.GameTestHolder("singulo_cable")
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class CableNetworkGameTests {
    private record Setup(SmesCellBlockEntity far, SmesCellBlockEntity near, SmesCellBlockEntity sink,
                         IEnergyStorage farPort, IEnergyStorage nearPort) {}
    private static SmesCellBlockEntity storage(GameTestHelper h, BlockPos pos, Direction front) {
        h.setBlock(pos,SinguloBlocks.SMES_MODULE.get().defaultBlockState().setValue(AbstractMachineBlock.FACING,front));
        return io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
    }
    private static IEnergyStorage port(GameTestHelper h, BlockPos pos, Direction side) {
        var cap=io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.EnergyStorage.BLOCK,h.absolutePos(pos),side);
        h.assertTrue(cap!=null,"Missing cable capability");
        return cap;
    }
    private static Setup setup(GameTestHelper h, boolean copper) {
        var far=storage(h,new BlockPos(1,1,2),Direction.EAST);
        far.energy().setEnergy(100_000);
        for (int x=2;x<=6;x++) h.setBlock(new BlockPos(x,1,2),SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        BlockPos nearPos=new BlockPos(5,1,copper?5:3);
        var near=storage(h,nearPos,Direction.NORTH);
        near.energy().setEnergy(copper?100_000:4_000);
        if (copper) {
            h.setBlock(new BlockPos(5,1,3),SinguloBlocks.COPPER_WIRE.get());
            h.setBlock(new BlockPos(5,1,4),SinguloBlocks.COPPER_WIRE.get());
        }
        var sink=storage(h,new BlockPos(7,1,2),Direction.SOUTH);
        sink.energy().setEnergy(sink.energy().getMaxEnergyStored()-6_000);
        return new Setup(far,near,sink,port(h,new BlockPos(2,1,2),Direction.WEST),
                port(h,new BlockPos(5,1,copper?4:2),Direction.SOUTH));
    }
    @GameTest(template="empty")
    public static void nearbySourcePaysFirstEvenWhenDistantSourcePushesFirst(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        Setup s=setup(h,false);
        int before=s.sink.energy().getEnergyStored();
        h.assertTrue(s.farPort.receiveEnergy(100_000,true)==2_000,"Simulation did not reserve nearest source first");
        h.assertTrue(s.farPort.receiveEnergy(100_000,true)==2_000 && s.near.energy().getEnergyStored()==4_000
                && s.sink.energy().getEnergyStored()==before,"Simulation modified energy or budgets");
        int paid=s.farPort.receiveEnergy(100_000,false);
        h.assertTrue(paid==2_000 && s.near.energy().getEnergyStored()==0,"Far source displaced available nearby power");
        s.far.energy().consume(paid);
        h.assertTrue(s.far.energy().getEnergyStored()==98_000 && s.sink.energy().getEnergyStored()==before+6_000,
                "Source spending and delivered energy disagree");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void sourcePriorityDoesNotDependOnWhichSourceTicksFirst(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        Setup s=setup(h,false);
        int paid=s.nearPort.receiveEnergy(4_000,false);
        h.assertTrue(paid==4_000,"Nearby offered power was not used first");
        s.near.energy().consume(paid);
        h.assertTrue(s.far.energy().getEnergyStored()==98_000,"Distant source did not fill nearby source's deficit");
        h.assertTrue(s.farPort.receiveEnergy(98_000,false)==0,"Satisfied receiver was charged twice in one tick");
        h.assertTrue(s.sink.energy().getEnergyStored()==s.sink.energy().getMaxEnergyStored(),"Receiver not fully supplied");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void farSourceSupplementsNearestRouteBottleneckAndLoss(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        Setup s=setup(h,true);
        int before=s.sink.energy().getEnergyStored();
        int expected=s.farPort.receiveEnergy(100_000,true);
        int paid=s.farPort.receiveEnergy(100_000,false);
        h.assertTrue(expected==paid && paid==4_002,"Far source did not cover copper capacity and route loss: "+paid);
        s.far.energy().consume(paid);
        h.assertTrue(s.near.energy().getEnergyStored()==98_000 && s.sink.energy().getEnergyStored()==before+6_000,
                "Nearest route exceeded copper capacity or failed to fill receiver");
        h.assertTrue(100_000-s.far.energy().getEnergyStored()+100_000-s.near.energy().getEnergyStored()==6_002,
                "Loss accounting created or lost unrelated energy");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void equalDistanceSourcesUseStablePositionOrder(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var a=storage(h,new BlockPos(2,1,1),Direction.SOUTH);
        var b=storage(h,new BlockPos(4,1,1),Direction.SOUTH);
        for(int x=2;x<=4;x++) h.setBlock(new BlockPos(x,1,2),SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        var sink=storage(h,new BlockPos(3,1,3),Direction.SOUTH);
        a.energy().setEnergy(5_000); b.energy().setEnergy(5_000);
        sink.energy().setEnergy(sink.energy().getMaxEnergyStored()-3_000);
        int paid=port(h,new BlockPos(4,1,2),Direction.NORTH).receiveEnergy(5_000,false);
        h.assertTrue(paid==0 && a.energy().getEnergyStored()==2_000 && b.energy().getEnergyStored()==5_000,
                "Equal-distance ordering depended on caller or hash iteration order");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void depletedNearbySourceDoesNotReserveUnavailablePower(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        Setup s=setup(h,false);
        s.near.energy().setEnergy(0);
        int paid=s.farPort.receiveEnergy(100_000,false);
        h.assertTrue(paid==6_000 && s.sink.energy().getEnergyStored()==s.sink.energy().getMaxEnergyStored(),
                "Unavailable nearby source blocked distant backup");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void cuttingCableClearsLitLinesImmediatelyAndStopsDelivery(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var source=storage(h,new BlockPos(1,1,2),Direction.EAST);
        source.energy().setEnergy(100_000_000);
        for(int x=2;x<=5;x++) h.setBlock(new BlockPos(x,1,2),SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        var sink=storage(h,new BlockPos(6,1,2),Direction.SOUTH);
        CableBlockEntity a=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(2,1,2)), b=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(5,1,2));
        var removedPort=port(h,new BlockPos(3,1,2),Direction.NORTH);
        int[] afterCut={0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(a.flow()>0 && b.flow()>0,"Cable display not active"))
                .thenExecute(()->{
                    h.setBlock(new BlockPos(3,1,2),Blocks.AIR);
                    h.assertTrue(a.flow()==0 && b.flow()==0,"Cut waited for periodic visual refresh");
                    h.assertTrue(!removedPort.canReceive() && removedPort.receiveEnergy(1,false)==0,
                            "Cached capability kept accepting power after removal");
                    afterCut[0]=sink.energy().getEnergyStored();
                }).thenIdle(5).thenExecute(()->h.assertTrue(sink.energy().getEnergyStored()==afterCut[0],
                        "Power still crossed cut cable")).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void machineLightChangesKeepCachedNetworkAndCableDisplay(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var source=storage(h,new BlockPos(1,1,2),Direction.EAST);
        source.energy().setEnergy(100_000_000);
        for(int x=2;x<=4;x++) h.setBlock(new BlockPos(x,1,2),SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        storage(h,new BlockPos(5,1,2),Direction.SOUTH);
        CableBlockEntity cable=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(2,1,2));
        h.startSequence().thenWaitUntil(()->h.assertTrue(cable.flow()>0,"Cable display not active"))
                .thenExecute(()->{
                    EnergyNetwork before=cable.network;
                    var pos=h.absolutePos(new BlockPos(1,1,2));
                    h.getLevel().setBlockAndUpdate(pos,h.getLevel().getBlockState(pos).cycle(AbstractMachineBlock.LIT));
                    h.assertTrue(cable.network==before && before.isValid() && cable.flow()>0,
                            "Source light change rebuilt topology or made cable flicker");
                }).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void localBreakDisplayClearsBothSidesWithoutWaitingForPacket(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var source=storage(h,new BlockPos(1,1,2),Direction.EAST);
        source.energy().setEnergy(100_000_000);
        for(int x=2;x<=5;x++) h.setBlock(new BlockPos(x,1,2),SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        storage(h,new BlockPos(6,1,2),Direction.SOUTH);
        CableBlockEntity a=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(2,1,2)), b=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(5,1,2));
        h.startSequence().thenWaitUntil(()->h.assertTrue(a.flow()>0 && b.flow()>0,"Cable display not active"))
                .thenExecute(()->{
                    CableBlockEntity.clearConnectedDisplay(h.getLevel(),h.absolutePos(new BlockPos(3,1,2)));
                    h.assertTrue(a.flow()==0 && b.flow()==0 && !a.flowsOut(Direction.EAST),
                            "Local break display waited for server update");
                }).thenSucceed();
    }
    @GameTest(template="empty")
    public static void directInteriorEntryKeepsExistingTickBudgetWhenSplittingSegment(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        for(int x=1;x<=5;x++) h.setBlock(new BlockPos(x,1,2),SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        var sink=storage(h,new BlockPos(6,1,2),Direction.SOUTH);
        var first=port(h,new BlockPos(1,1,2),Direction.DOWN);
        h.assertTrue(first.receiveEnergy(900_000,false)==900_000,"Initial route not supplied");
        CableBlockEntity cable=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(1,1,2));
        EnergyNetwork before=cable.network;
        var interior=port(h,new BlockPos(2,1,2),Direction.DOWN);
        h.assertTrue(interior.receiveEnergy(200_000,true)==100_000,"Interior simulation reset shared budget");
        h.assertTrue(interior.receiveEnergy(200_000,false)==100_000,"Interior entry bypassed shared capacity");
        h.assertTrue(first.receiveEnergy(1,false)==0 && port(h,new BlockPos(3,1,2),Direction.DOWN).receiveEnergy(1,false)==0,
                "Changing entry rebuilt the network and reset capacity");
        h.assertTrue(cable.network==before && before.isValid() && before.segmentCount()==3
                && sink.energy().getEnergyStored()==1_000_000,"Dynamic entry changed network identity or created energy");
        h.succeed();
    }
    @GameTest(template="empty",batch="long_cable_network",timeoutTicks=100)
    public static void longStraightRunUsesOneSegmentAndNoPerCableTicker(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        for(int y=1;y<=256;y++) h.setBlock(new BlockPos(2,y,2),SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        var sink=storage(h,new BlockPos(3,256,2),Direction.SOUTH);
        CableBlockEntity cable=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, new BlockPos(2,1,2));
        var cap=port(h,new BlockPos(2,1,2),Direction.DOWN);
        for(int i=0;i<100;i++) h.assertTrue(cap.receiveEnergy(1_000,false)==1_000,"Long route stopped supplying");
        EnergyNetwork n=cable.network;
        h.assertTrue(n.junctionCount()==2 && n.segmentCount()==1 && n.accountedConstraints()==3 && n.cachedPathCount()==1,
                "Transfer state grew with the 256 individual cables");
        h.assertTrue(sink.energy().getEnergyStored()==100_000,"Long network lost power");
        h.assertTrue(((CableBlock)cable.getBlockState().getBlock()).getTicker(h.getLevel(),cable.getBlockState(),SinguloBlockEntities.CABLE.get())==null,
                "Individual cables still have server tickers");
        for(int y=1;y<=256;y++) h.setBlock(new BlockPos(2,y,2),Blocks.AIR);
        h.setBlock(new BlockPos(3,256,2),Blocks.AIR);
        h.succeed();
    }
}
