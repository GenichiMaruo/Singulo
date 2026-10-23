package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RelocationGameTests {
    private static UseOnContext context(net.minecraft.world.entity.player.Player p, BlockPos pos) {
        return new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }

    @GameTest(template="empty")
    public static void wrenchPreservesContentsFluidsEnergyAndSidesOnPlacement(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        BlockPos a=new BlockPos(2,1,2), b=new BlockPos(5,1,2);
        h.setBlock(a,SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity original=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, a);
        original.items().setStackInSlot(0,new ItemStack(Items.COAL,13));
        original.items().setStackInSlot(original.type().outputSlot(),new ItemStack(Items.IRON_INGOT,7));
        original.energy().receiveEnergy(1234,false);
        original.automationFluids().fill(new FluidStack(SinguloFluids.ALL.get("oxygen").source().get(),750),IFluidHandler.FluidAction.EXECUTE);
        original.setSide(0,SideConfig.Face.TOP,SideConfig.NONE,false);
        original.setEjectEnabled(0,true);
        int sides=original.itemSides().packed();
        var player=io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(h);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var viewer=io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(h);
        viewer.setPos(Vec3.atCenterOf(h.absolutePos(a)));
        viewer.containerMenu = new MachineMenu(1, viewer.getInventory(), original);
        try {
            var wrench=new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
            player.setShiftKeyDown(true);
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(a)));
            h.assertTrue(h.getBlockState(a).isAir(),"Machine remained after packing");
            h.assertTrue(viewer.containerMenu == viewer.inventoryMenu,"Other player's stale machine menu stayed open");
            ItemStack packed=ItemStack.EMPTY;
            for(int i=0;i<player.getInventory().getContainerSize();i++) {
                var candidate=player.getInventory().getItem(i);
                if(candidate.is(SinguloBlocks.MACHINES.get(MachineType.KILN).get().asItem())) packed=candidate;
            }
            h.assertFalse(packed.isEmpty(),"No packed machine delivered");
            h.assertTrue(packed.getMaxStackSize()==1,"Packed devices must not stack");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(h.absolutePos(a)).inflate(1)).isEmpty(),"Packing also dropped loose contents");
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND,packed);
            ((BlockItem)packed.getItem()).place(new BlockPlaceContext(context(player,h.absolutePos(b))));
            MachineBlockEntity restored=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, b);
            h.assertTrue(restored!=null,"Packed machine could not be placed");
            h.assertTrue(restored.items().getStackInSlot(0).getCount()==13 && restored.items().getStackInSlot(restored.type().outputSlot()).getCount()==7,"Inventory not preserved");
            h.assertTrue(restored.energy().getEnergyStored()==1234,"Energy not preserved");
            h.assertTrue(restored.automationFluids().getFluidInTank(0).getAmount()==750,"Fluid not preserved");
            h.assertTrue(restored.itemSides().packed()==sides,"Side settings not preserved");
        } finally {player.discard(); viewer.discard();}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void wrenchRequiresSneakingAndIgnoresOtherInventories(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        BlockPos a=new BlockPos(2,1,2), chest=new BlockPos(4,1,2);
        h.setBlock(a,SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        h.setBlock(chest,net.minecraft.world.level.block.Blocks.CHEST);
        var player=io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(h);
        try {
            var wrench=new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(a)));
            h.assertTrue(io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, a)!=null,"Ordinary click packed a device");
            player.setShiftKeyDown(true);
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(chest)));
            h.assertTrue(io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, chest)!=null,"Wrench packed an unrelated inventory");
        } finally {player.discard();}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void wrenchRetainsGeneratorAndCatalystDeviceState(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        var blocks=java.util.List.of(SinguloBlocks.THERMOELECTRIC_GENERATOR, SinguloBlocks.SMES_CELL, SinguloBlocks.SMES_MODULE,
                SinguloBlocks.CRYOGENIC_TURBINE, SinguloBlocks.HYDROGEN_FUEL_CELL, SinguloBlocks.MUON_FUSION_REACTOR,
                SinguloBlocks.COSMIC_MUON_COLLECTOR, SinguloBlocks.QUANTUM_HEAT_ENGINE, SinguloBlocks.INERTIAL_STABILIZER,
                SinguloBlocks.PROBE_STATION, SinguloBlocks.DEGENERATE_FURNACE_CONTROLLER,
                SinguloBlocks.SHIELD_TOWER_CORE, SinguloBlocks.TIPLER_CORE);
        BlockPos a=new BlockPos(2,1,2), b=new BlockPos(5,1,2);
        var player=io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(h);
        try {
            for(var block:blocks) {
                player.getInventory().clearContent();
                h.setBlock(a,block.get());
                var before=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, a);
                var tag=before.saveWithoutMetadata();
                // Every audited generator/storage/catalyst device persists this buffer.
                if(tag.contains("energy")) tag.putInt("energy",1234);
                before.load(tag);
                var expected=before.saveWithoutMetadata();
                var wrench=new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
                player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
                player.setShiftKeyDown(true);
                wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(a)));
                ItemStack packed=ItemStack.EMPTY;
                for(int n=0;n<player.getInventory().getContainerSize();n++) {
                    var stack=player.getInventory().getItem(n);
                    if(stack.is(block.get().asItem())) packed=stack;
                }
                h.assertFalse(packed.isEmpty(),"Device not supported: "+block.getId());
                player.setShiftKeyDown(false);
                player.setItemInHand(InteractionHand.MAIN_HAND,packed);
                ((BlockItem)packed.getItem()).place(new BlockPlaceContext(context(player,h.absolutePos(b))));
                var after=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, b);
                h.assertTrue(after!=null && expected.equals(after.saveWithoutMetadata()),"Device state changed on relocation: "+block.getId());
                // Clear the empty test fixture without running its production loot path.
                h.getLevel().removeBlockEntity(h.absolutePos(b));
                h.setBlock(b,net.minecraft.world.level.block.Blocks.AIR);
            }
        } finally {player.discard();}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void wrenchDropsOnePackedDeviceWhenInventoryIsFull(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        BlockPos pos=new BlockPos(2,1,2);
        h.setBlock(pos,SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity machine=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, pos);
        machine.items().setStackInSlot(0,new ItemStack(Items.COAL,13));
        var player=io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(h);
        try {
            player.setPos(Vec3.atCenterOf(h.absolutePos(pos)));
            for(int n=0;n<36;n++) player.getInventory().setItem(n,new ItemStack(Items.DIRT,64));
            var wrench=new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
            player.setShiftKeyDown(true);
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(pos)));
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(h.absolutePos(pos)).inflate(3));
            h.assertTrue(drops.size()==1 && drops.get(0).getItem().is(SinguloBlocks.MACHINES.get(MachineType.KILN).get().asItem())
                    && drops.get(0).getItem().getOrCreateTag().contains("BlockEntityTag", 10),"Full inventory lost contents or produced extra loose drops: "+drops.stream().map(e->e.getItem().toString()).toList());
        } finally {player.discard();}
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=60)
    public static void relocatingAnchorReleasesOldTicketsAndRestartsAtNewPosition(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        BlockPos a=new BlockPos(2,1,2), b=new BlockPos(5,1,2);
        h.setBlock(a,SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get());
        WorldlineAnchorBlockEntity original=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, a);
        var owner=java.util.UUID.randomUUID();
        original.setOwner(owner);
        original.energy().setEnergy(original.energy().getMaxEnergyStored());
        original.catalystSlot().insertItem(0,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst"))),false);
        h.runAtTickTime(10,()->{
            h.assertTrue(original.loadedRadius()>=0,"Anchor did not start before packing");
            var player=io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(h);
            try {
                var wrench=new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
                player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
                player.setShiftKeyDown(true);
                wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(a)));
                h.assertTrue(original.loadedRadius()==-1 && WorldlineAnchorBlockEntity.activeCount(owner)==0,"Old anchor retained ticket radius or owner quota");
                ItemStack packed=ItemStack.EMPTY;
                for(int n=0;n<36;n++) if(player.getInventory().getItem(n).is(SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get().asItem())) packed=player.getInventory().getItem(n);
                h.assertFalse(packed.isEmpty(),"Anchor was not packed");
                player.setShiftKeyDown(false);
                player.setItemInHand(InteractionHand.MAIN_HAND,packed);
                ((BlockItem)packed.getItem()).place(new BlockPlaceContext(context(player,h.absolutePos(b))));
                WorldlineAnchorBlockEntity restored=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, b);
                h.assertTrue(restored!=null && !restored.catalystSlot().getStackInSlot(0).isEmpty(),"Anchor lost its catalyst");
            } finally {player.discard();}
        });
        h.runAtTickTime(30,()->{
            WorldlineAnchorBlockEntity restored=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, b);
            h.assertTrue(restored.loadedRadius()>=0,"Relocated anchor did not create new position tickets");
            restored.prepareForRelocation();
            h.succeed();
        });
    }

    /** ケーブル撤去モード: 使うと1本、スニークするとつながった同じケーブルをまとめて回収する。ほかの種類のケーブルは残す。 */
    @GameTest(template="empty")
    public static void wrenchCableModeTakesBackCables(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        var copper=SinguloBlocks.COPPER_WIRE.get();
        for(int x=1;x<=5;x++) h.setBlock(new BlockPos(x,1,2),copper);
        h.setBlock(new BlockPos(6,1,2),SinguloBlocks.SUPERCONDUCTING_CABLE.get());
        h.setBlock(new BlockPos(1,1,4),copper);
        var player=io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(h);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        try {
            var wrench=new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
            // スニークして空中で使うとモードが変わる
            player.setShiftKeyDown(true);
            wrench.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
            h.assertTrue(io.github.genichimaruo.singulo.item.RelocationWrenchItem.mode(wrench)==1,"Mode did not switch to cable removal");
            h.assertTrue(wrench.getDestroySpeed(copper.defaultBlockState())>=1000,"Cable mode does not break cables instantly");
            // 1本だけ
            player.setShiftKeyDown(false);
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(new BlockPos(1,1,4))));
            h.assertTrue(h.getBlockState(new BlockPos(1,1,4)).isAir(),"Single cable not removed");
            // つながった同じ種類をまとめて
            player.setShiftKeyDown(true);
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(new BlockPos(3,1,2))));
            for(int x=1;x<=5;x++) h.assertTrue(h.getBlockState(new BlockPos(x,1,2)).isAir(),"Connected copper wire left at x="+x);
            h.assertTrue(h.getBlockState(new BlockPos(6,1,2)).is(SinguloBlocks.SUPERCONDUCTING_CABLE.get()),"Other cable kind was removed");
            int got=0;
            for(int i=0;i<player.getInventory().getContainerSize();i++) {
                var st=player.getInventory().getItem(i);
                if(st.is(copper.asItem())) got+=st.getCount();
            }
            h.assertTrue(got==6,"Expected 6 copper wires back, got "+got);
            // 移設モードに戻すと、ケーブルには何もしない
            wrench.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
            h.assertTrue(io.github.genichimaruo.singulo.item.RelocationWrenchItem.mode(wrench)==0,"Mode did not switch back");
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(new BlockPos(6,1,2))));
            h.assertFalse(h.getBlockState(new BlockPos(6,1,2)).isAir(),"Relocation mode removed a cable");
        } finally {player.discard();}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void wrenchRespectsCanceledBreaksForEachBlock(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        BlockPos machine = new BlockPos(2, 1, 2);
        h.setBlock(machine, SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity kiln = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, machine);
        kiln.items().setStackInSlot(0, new ItemStack(Items.COAL, 13));
        for (int x = 1; x <= 3; x++) h.setBlock(new BlockPos(x, 1, 4), SinguloBlocks.COPPER_WIRE.get());
        var player = io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(h);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> protect = event -> {
            if (event.getPlayer() == player && (event.getPos().equals(h.absolutePos(machine))
                    || event.getPos().equals(h.absolutePos(new BlockPos(2, 1, 4))))) event.setCanceled(true);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(protect);
        try {
            var wrench = new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            player.setShiftKeyDown(true);
            wrench.getItem().onItemUseFirst(wrench, context(player, h.absolutePos(machine)));
            h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(machine)) == kiln && kiln.items().getStackInSlot(0).getCount() == 13,
                    "Canceled machine break was packed anyway");
            io.github.genichimaruo.singulo.item.RelocationWrenchItem.setMode(wrench, 1);
            wrench.getItem().onItemUseFirst(wrench, context(player, h.absolutePos(new BlockPos(1, 1, 4))));
            h.assertTrue(h.getBlockState(new BlockPos(2, 1, 4)).is(SinguloBlocks.COPPER_WIRE.get()),
                    "Bulk removal bypassed a canceled cable break");
            h.assertTrue(h.getBlockState(new BlockPos(1, 1, 4)).isAir() && h.getBlockState(new BlockPos(3, 1, 4)).isAir(),
                    "Unprotected cables were not removed");
            h.assertTrue(player.getInventory().countItem(SinguloBlocks.COPPER_WIRE.get().asItem()) == 2,
                    "Canceled removal returned extra cable items");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(protect);
            player.discard();
        }
        h.succeed();
    }
}
