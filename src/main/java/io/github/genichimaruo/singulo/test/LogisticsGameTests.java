package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class LogisticsGameTests {
    @GameTest(template="empty", timeoutTicks=100)
    public static void outputDefaultsOffAndMasterStartsAdjacentTransfer(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        BlockPos from = new BlockPos(2,1,2), to = from.east();
        h.setBlock(from, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        h.setBlock(to, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity a=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, from), b=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, to);
        a.items().setStackInSlot(a.type().outputSlot(), new ItemStack(Items.IRON_INGOT, 7));
        h.assertFalse(a.itemSides().ejectEnabled(), "New machine auto-eject defaults on");
        h.runAtTickTime(20, () -> {
            h.assertTrue(a.items().getStackInSlot(a.type().outputSlot()).getCount()==7 && b.items().getStackInSlot(0).isEmpty(), "Auto-eject moved items while off");
            // Exercise old saved settings with no individual eject faces selected.
            for (var f:SideConfig.Face.values()) a.setSide(0,f,SideConfig.BOTH,false);
            a.setEjectEnabled(0,true);
        });
        h.runAtTickTime(40, () -> {
            h.assertTrue(a.items().getStackInSlot(a.type().outputSlot()).isEmpty(), "Master on did not move output");
            h.assertTrue(b.items().getStackInSlot(0).is(Items.IRON_INGOT) && b.items().getStackInSlot(0).getCount()==7, "Adjacent machine lost input");
            h.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void autoEjectRespectsBlockedDestinationFace(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        BlockPos from=new BlockPos(2,1,2),to=from.east();
        h.setBlock(from,SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        h.setBlock(to,SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity a=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, from), b=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, to);
        a.items().setStackInSlot(a.type().outputSlot(),new ItemStack(Items.IRON_INGOT,5));
        a.setEjectEnabled(0,true);
        var face=SideConfig.faceOf(Direction.NORTH,Direction.WEST);
        b.setSide(0,face,SideConfig.NONE,false);
        h.runAtTickTime(20,()->{
            h.assertTrue(a.items().getStackInSlot(a.type().outputSlot()).getCount()==5,"Disabled destination face accepted output");
            b.setSide(0,face,SideConfig.INPUT,false);
        });
        h.runAtTickTime(40,()->{
            h.assertTrue(a.items().getStackInSlot(a.type().outputSlot()).isEmpty() && b.items().getStackInSlot(0).getCount()==5,"Re-enabled destination did not accept output");
            h.succeed();
        });
    }

    @GameTest(template="huge", timeoutTicks=130)
    public static void differentMultiblocksCanTransferThroughAdjacentPorts(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        var kind=io.github.genichimaruo.singulo.multiblock.Blueprints.Kind.COOLING_TOWER;
        BlockPos ca=new BlockPos(2,1,1),cb=ca.east(5);
        TestBuild.build(h,kind,ca,Direction.SOUTH,7);
        TestBuild.build(h,kind,cb,Direction.SOUTH,7);
        var pa=TestBuild.port(h,kind,ca,Direction.SOUTH,7,0,1,2);
        var pb=TestBuild.port(h,kind,cb,Direction.SOUTH,7,4,1,2);
        h.assertTrue(pa.distManhattan(pb)==1,"Test ports must share a face");
        MachineBlockEntity a=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, ca),b=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, cb);
        a.items().setStackInSlot(a.type().outputSlot(),new ItemStack(Items.IRON_INGOT,9));
        h.succeedWhen(()->{
            h.assertTrue(a.structureSize()==7 && b.structureSize()==7,"Both structures must be formed");
            h.assertTrue(a.items().getStackInSlot(a.type().outputSlot()).isEmpty() && b.items().getStackInSlot(0).getCount()==9,"Adjacent ports of different structures did not transfer items");
        });
    }
    private LogisticsGameTests() {}
}
