package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class SpentItemGameTests {
    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)), count);
    }
    private static MachineBlockEntity archive(GameTestHelper h) {
        BlockPos pos = new BlockPos(2,2,2);
        h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.ARCHIVE_TERMINAL).get());
        MachineBlockEntity be = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
        ItemStack log = item("observation_log",1);
        be.items().setStackInSlot(0, UsesHelper.consume(log, UsesHelper.remaining(log)-1));
        be.items().setStackInSlot(1, item("blank_data_card",1));
        h.onEachTick(() -> be.energy().setEnergy(be.energy().getMaxEnergyStored()));
        return be;
    }
    private static void finished(GameTestHelper h, MachineBlockEntity be) {
        h.assertTrue(be.items().getStackInSlot(be.type().outputSlot()).is(item("data_card_observation_log",1).getItem()), "Final copy has not finished");
        h.assertTrue(be.items().getStackInSlot(0).is(item("observation_log",1).getItem())
                && UsesHelper.remaining(be.items().getStackInSlot(0))==0, "Exhausted original lost");
    }

    @GameTest(template="empty", timeoutTicks=350)
    public static void pipesCanPullSpentOriginalWhileProductBlocksOutput(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var be = archive(h);
        var cap = io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.ItemHandler.BLOCK, be.getBlockPos(), Direction.DOWN);
        h.assertTrue(cap != null && cap.extractItem(0,1,false).isEmpty(), "Automation removed a still usable original");
        h.startSequence().thenWaitUntil(() -> finished(h,be)).thenIdle(20).thenExecute(() -> {
            var simulated = cap.extractItem(0,1,true);
            h.assertTrue(!simulated.isEmpty() && UsesHelper.remaining(simulated)==0, "Pipe simulation cannot see exhausted original");
            h.assertTrue(!be.items().getStackInSlot(0).isEmpty(), "Simulation removed original");
            h.assertTrue(UsesHelper.get(cap.extractItem(0,1,false)).used()==16, "Pipe extraction lost usage data");
            h.assertTrue(cap.extractItem(0,1,false).isEmpty(), "Spent original duplicated");
            be.items().setStackInSlot(0,item("observation_log",1));
            h.assertTrue(cap.extractItem(0,1,false).isEmpty(), "Return flag leaked onto replacement original");
        }).thenSucceed();
    }

    @GameTest(template="empty", timeoutTicks=350)
    public static void pendingOriginalSurvivesSaveAndMovesAfterOutputIsTaken(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var be = archive(h);
        h.startSequence().thenWaitUntil(() -> finished(h,be)).thenExecute(() -> {
            var registries = h.getLevel().registryAccess();
            be.load(be.saveWithoutMetadata());
            h.assertTrue(!be.automationItems().extractItem(0,1,true).isEmpty(), "Return flag lost on reload");
            be.automationItems().extractItem(be.type().outputSlot(),64,false);
        }).thenIdle(2).thenExecute(() -> {
            ItemStack out = be.items().getStackInSlot(be.type().outputSlot());
            h.assertTrue(be.items().getStackInSlot(0).isEmpty() && out.is(item("observation_log",1).getItem())
                    && UsesHelper.get(out).used()==16 && UsesHelper.canRestore(out), "Spent original did not reach output intact");
        }).thenSucceed();
    }

    @GameTest(template="empty", timeoutTicks=350)
    public static void hopperCollectsProductAndSpentOriginalWithAutoEjectOff(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var be = archive(h);
        BlockPos below = new BlockPos(2,1,2);
        h.setBlock(below,Blocks.HOPPER);
        h.setBlock(new BlockPos(1,1,2),Blocks.REDSTONE_BLOCK);
        HopperBlockEntity hopper = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, below);
        h.startSequence().thenWaitUntil(() -> finished(h,be)).thenExecute(() -> {
            h.assertFalse(be.itemSides().ejectEnabled(), "Test requires auto-eject off");
            h.setBlock(new BlockPos(1,1,2),Blocks.AIR);
        }).thenWaitUntil(() -> {
            int logs=0, cards=0;
            for (int i=0; i<hopper.getContainerSize(); i++) {
                ItemStack s=hopper.getItem(i);
                if (s.is(item("observation_log",1).getItem())) { logs+=s.getCount(); h.assertTrue(UsesHelper.get(s).used()==16,"Hopper lost usage data"); }
                if (s.is(item("data_card_observation_log",1).getItem())) cards+=s.getCount();
            }
            h.assertTrue(logs==1 && cards==1 && be.items().getStackInSlot(0).isEmpty()
                    && be.items().getStackInSlot(be.type().outputSlot()).isEmpty(),"Hopper has not collected both outputs exactly once");
        }).thenSucceed();
    }

    @GameTest(template="empty", timeoutTicks=350)
    public static void automaticEjectionMovesBothOutputsOnlyAfterEnabled(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var be = archive(h);
        BlockPos target = new BlockPos(3,2,2);
        h.setBlock(target,Blocks.CHEST);
        var chest = io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.ItemHandler.BLOCK,h.absolutePos(target),Direction.WEST);
        h.startSequence().thenWaitUntil(() -> finished(h,be)).thenIdle(20).thenExecute(() -> {
            finished(h,be);
            be.setEjectEnabled(0,true);
        }).thenWaitUntil(() -> {
            int logs=0,cards=0;
            for (int i=0;i<chest.getSlots();i++) {
                var s=chest.getStackInSlot(i);
                if(s.is(item("observation_log",1).getItem())) logs+=s.getCount();
                if(s.is(item("data_card_observation_log",1).getItem())) cards+=s.getCount();
            }
            h.assertTrue(logs==1 && cards==1 && be.items().getStackInSlot(0).isEmpty(),"Auto-eject did not move both outputs exactly once");
        }).thenSucceed();
    }

    @GameTest(template="tall", timeoutTicks=750)
    public static void controlUnitReturnsThroughPortWithRepairHistory(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var kind=Blueprints.Kind.COOLING_TOWER;
        var pos=new BlockPos(2,1,0);
        TestBuild.build(h,kind,pos,Direction.SOUTH,7);
        var port=TestBuild.port(h,kind,pos,Direction.SOUTH,7,1,1,0);
        MachineBlockEntity be=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
        var unit=UsesHelper.restore(UsesHelper.consume(item("control_unit",1),16),item("control_unit",1));
        be.items().setStackInSlot(0,new ItemStack(Items.COPPER_INGOT,8));
        be.items().setStackInSlot(1,new ItemStack(Items.REDSTONE,4));
        be.items().setStackInSlot(2,item("steel_plate",2));
        be.items().setStackInSlot(3,item("ceramic_substrate",2));
        be.items().setStackInSlot(4,UsesHelper.consume(unit,UsesHelper.remaining(unit)-1));
        h.onEachTick(() -> be.energy().setEnergy(be.energy().getMaxEnergyStored()));
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(be.items().getStackInSlot(be.type().outputSlot()).getCount()==4,"Final coils not produced");
            h.assertTrue(be.items().getStackInSlot(4).is(item("degraded_control_unit",1).getItem()),"No degraded control unit");
        }).thenExecute(() -> {
            var cap=io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.ItemHandler.BLOCK,port,Direction.NORTH);
            h.assertTrue(cap!=null,"Port capability missing");
            var worn=cap.extractItem(4,1,false);
            h.assertTrue(worn.is(item("degraded_control_unit",1).getItem()) && UsesHelper.repairsOf(worn)==2,"Port lost worn part or repair history");
            h.assertTrue(cap.extractItem(4,1,false).isEmpty(),"Port duplicated worn part");
        }).thenSucceed();
    }

    @GameTest(template="empty", timeoutTicks=500)
    public static void insertedRepairInputStaysUntilRestored(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        BlockPos pos=new BlockPos(2,1,2);
        h.setBlock(pos,SinguloBlocks.MACHINES.get(MachineType.ARCHIVE_TERMINAL).get());
        MachineBlockEntity be=io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
        be.items().setStackInSlot(0,UsesHelper.consume(item("observation_log",1),16));
        h.startSequence().thenIdle(20).thenExecute(() -> {
            h.assertTrue(!be.items().getStackInSlot(0).isEmpty() && be.items().getStackInSlot(be.type().outputSlot()).isEmpty(),"Repair ingredient auto-ejected");
            h.assertTrue(be.automationItems().extractItem(0,1,true).isEmpty(),"Pipe stole repair ingredient");
            be.items().setStackInSlot(1,new ItemStack(Items.REDSTONE,4));
            be.items().setStackInSlot(2,new ItemStack(Items.GLOWSTONE_DUST,2));
            be.items().setStackInSlot(3,item("ceramic_substrate",1));
        }).thenWaitUntil(() -> {
            var restored=be.items().getStackInSlot(be.type().outputSlot());
            h.assertTrue(restored.is(item("observation_log",1).getItem()) && UsesHelper.remaining(restored)==12
                    && UsesHelper.repairsOf(restored)==1 && be.items().getStackInSlot(0).isEmpty(),"Repair no longer works");
        }).thenSucceed();
        h.onEachTick(() -> be.energy().setEnergy(be.energy().getMaxEnergyStored()));
    }
}
