package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;

@net.minecraftforge.gametest.GameTestHolder("singulo_cable")
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class CableCapacityGameTests {
    private static IEnergyStorage port(GameTestHelper h, BlockPos pos) {
        var cap=io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.EnergyStorage.BLOCK,h.absolutePos(pos),Direction.NORTH);
        h.assertTrue(cap!=null,"Cable energy port missing");
        return cap;
    }
    private static SmesCellBlockEntity sink(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos,SinguloBlocks.SMES_MODULE.get().defaultBlockState()
                .setValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING,Direction.SOUTH));
        return io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
    }
    private static void superCable(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos,SinguloBlocks.SUPERCONDUCTING_CABLE.get());
    }

    @GameTest(template="empty")
    public static void unusedCopperBranchDoesNotThrottleSuperconductingRoute(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var entry=new BlockPos(2,1,2);
        superCable(h,entry);
        superCable(h,entry.east());
        superCable(h,entry.east(2));
        h.setBlock(entry.south(),SinguloBlocks.COPPER_WIRE.get());
        var sink=sink(h,entry.east(3));
        var cap=port(h,entry);
        h.assertTrue(cap.getMaxEnergyStored()==1_000_000,"Port reports a remote branch's capacity");
        h.assertTrue(cap.receiveEnergy(100_000,true)==100_000 && sink.energy().getEnergyStored()==0,"Simulation throttled by unused copper or changed storage");
        h.assertTrue(cap.receiveEnergy(100_000,false)==100_000 && sink.energy().getEnergyStored()==100_000,"Unused copper branch throttled main route");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void activeCopperBranchIsLimitedWithoutThrottlingMainRoute(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var entry=new BlockPos(2,1,1);
        superCable(h,entry);
        superCable(h,entry.east());
        superCable(h,entry.east(2));
        var main=sink(h,entry.east(3));
        main.energy().setEnergy(main.energy().getMaxEnergyStored()-50_000);
        h.setBlock(entry.east().south(),SinguloBlocks.COPPER_WIRE.get());
        h.setBlock(entry.east().south(2),SinguloBlocks.COPPER_WIRE.get());
        var branch=sink(h,entry.east().south(3));
        var cap=port(h,entry);
        int simulated=cap.receiveEnergy(100_000,true);
        h.assertTrue(main.energy().getEnergyStored()==main.energy().getMaxEnergyStored()-50_000 && branch.energy().getEnergyStored()==0,"Simulation delivered energy");
        int paid=cap.receiveEnergy(100_000,false);
        h.assertTrue(paid==simulated && paid>2_000,"Simulation and real branch budgets disagree");
        h.assertTrue(main.energy().getEnergyStored()==main.energy().getMaxEnergyStored(),"Copper branch throttled superconducting destination");
        h.assertTrue(branch.energy().getEnergyStored()>0 && branch.energy().getEnergyStored()<=2_000,"Copper branch exceeded its rating");
        h.assertTrue(50_000L+branch.energy().getEnergyStored()<=paid,"Cable created energy");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=20)
    public static void sharedCableBudgetIncludesAllSourcesAndResetsNextTick(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var a=new BlockPos(2,1,2);
        var b=new BlockPos(2,1,4);
        superCable(h,a);
        superCable(h,b);
        h.setBlock(new BlockPos(2,1,3),SinguloBlocks.COPPER_WIRE.get());
        superCable(h,new BlockPos(3,1,3));
        var sink=sink(h,new BlockPos(4,1,3));
        var ca=port(h,a);
        var cb=port(h,b);
        h.assertTrue(ca.receiveEnergy(100_000,true)==2_000 && cb.receiveEnergy(100_000,true)==2_000,"Simulation reserved real capacity");
        h.assertTrue(ca.receiveEnergy(100_000,false)==2_000,"Series copper did not cap first source");
        int stored=sink.energy().getEnergyStored();
        h.assertTrue(cb.receiveEnergy(100_000,true)==0 && cb.receiveEnergy(100_000,false)==0
                && sink.energy().getEnergyStored()==stored,"Second source exceeded shared cable's per-tick capacity");
        h.runAtTickTime(2,()->{
            h.assertTrue(cb.receiveEnergy(100_000,false)==2_000,"Cable budget did not reset next tick");
            h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void simulationSharesTrunkBudgetBetweenDestinations(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var entry=new BlockPos(2,1,2);
        superCable(h,entry);
        h.setBlock(entry.east(),SinguloBlocks.COPPER_WIRE.get());
        superCable(h,entry.east(2));
        var a=sink(h,entry.east(3));
        var b=sink(h,entry.east(2).south());
        var cap=port(h,entry);
        h.assertTrue(cap.receiveEnergy(100_000,true)==2_000,"Simulation reused shared trunk budget for each destination");
        h.assertTrue(cap.receiveEnergy(100_000,false)==2_000,"Real delivery exceeded shared trunk budget");
        int received=a.energy().getEnergyStored()+b.energy().getEnergyStored();
        h.assertTrue(received>0 && received<=2_000,"Shared trunk duplicated energy");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void disjointRoutesDoNotShareOneNetworkWideBudget(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var left=new BlockPos(2,1,2);
        var right=new BlockPos(4,1,2);
        superCable(h,left);
        h.setBlock(new BlockPos(3,1,2),SinguloBlocks.COPPER_WIRE.get());
        superCable(h,right);
        var a=sink(h,left.west());
        var b=sink(h,right.east());
        b.energy().setEnergy(b.energy().getMaxEnergyStored());
        int first=port(h,left).receiveEnergy(100_000,false);
        h.assertTrue(first>=50_000 && a.energy().getEnergyStored()==first,"Local left route throttled by remote cable");
        a.energy().setEnergy(a.energy().getMaxEnergyStored());
        b.energy().setEnergy(0);
        int second=port(h,right).receiveEnergy(100_000,false);
        h.assertTrue(second>=50_000 && b.energy().getEnergyStored()==second,"Disjoint right route consumed left route's budget");
        h.succeed();
    }

    @GameTest(template="tall",timeoutTicks=100)
    public static void coolingTowerReceivesFullSuperconductingPowerBesideCopperBranch(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var controller=new BlockPos(2,1,2);
        TestBuild.build(h,Blueprints.Kind.COOLING_TOWER,controller,Direction.SOUTH,7);
        var output=TestBuild.port(h,Blueprints.Kind.COOLING_TOWER,controller,Direction.SOUTH,7,1,1,0);
        var cable=output.north();
        h.getLevel().setBlockAndUpdate(cable,SinguloBlocks.SUPERCONDUCTING_CABLE.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(cable.north(),SinguloBlocks.COPPER_WIRE.get().defaultBlockState());
        MachineBlockEntity tower=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, controller);
        h.startSequence().thenWaitUntil(()->h.assertTrue(tower.structureSize()==7,"Cooling tower not formed"))
                .thenExecute(()->{
                    tower.energy().setEnergy(0);
                    var cap=io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.EnergyStorage.BLOCK,cable,Direction.WEST);
                    h.assertTrue(cap!=null,"Tower supply cable missing");
                    int expected=tower.energy().receiveEnergy(20_000,true);
                    h.assertTrue(expected>2_000,"Tower must accept more than copper capacity for this regression");
                    int paid=cap.receiveEnergy(20_000,false);
                    h.assertTrue(paid==expected && tower.energy().getEnergyStored()==expected,
                            "Copper branch restricted cooling tower supply: "+paid+" / "+expected);
                }).thenSucceed();
    }
}
