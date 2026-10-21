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
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RelocationGameTests {
    private static UseOnContext context(net.minecraft.world.entity.player.Player p, BlockPos pos) {
        return new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }

    @GameTest(template="empty")
    public static void wrenchPreservesContentsFluidsEnergyAndSidesOnPlacement(GameTestHelper h) {
        BlockPos a=new BlockPos(2,1,2), b=new BlockPos(5,1,2);
        h.setBlock(a,SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity original=h.getBlockEntity(a);
        original.items().setStackInSlot(0,new ItemStack(Items.COAL,13));
        original.items().setStackInSlot(original.type().outputSlot(),new ItemStack(Items.IRON_INGOT,7));
        original.energy().receiveEnergy(1234,false);
        original.automationFluids().fill(new FluidStack(SinguloFluids.ALL.get("oxygen").source().get(),750),IFluidHandler.FluidAction.EXECUTE);
        original.setSide(0,SideConfig.Face.TOP,SideConfig.NONE,false);
        original.setEjectEnabled(0,true);
        int sides=original.itemSides().packed();
        var player=h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var viewer=h.makeMockServerPlayerInLevel();
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
            MachineBlockEntity restored=h.getBlockEntity(b);
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
        BlockPos a=new BlockPos(2,1,2), chest=new BlockPos(4,1,2);
        h.setBlock(a,SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        h.setBlock(chest,net.minecraft.world.level.block.Blocks.CHEST);
        var player=h.makeMockServerPlayerInLevel();
        try {
            var wrench=new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(a)));
            h.assertTrue(h.getBlockEntity(a)!=null,"Ordinary click packed a device");
            player.setShiftKeyDown(true);
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(chest)));
            h.assertTrue(h.getBlockEntity(chest)!=null,"Wrench packed an unrelated inventory");
        } finally {player.discard();}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void wrenchRetainsGeneratorAndCatalystDeviceState(GameTestHelper h) {
        var blocks=java.util.List.of(SinguloBlocks.THERMOELECTRIC_GENERATOR, SinguloBlocks.SMES_CELL, SinguloBlocks.SMES_MODULE,
                SinguloBlocks.CRYOGENIC_TURBINE, SinguloBlocks.HYDROGEN_FUEL_CELL, SinguloBlocks.MUON_FUSION_REACTOR,
                SinguloBlocks.COSMIC_MUON_COLLECTOR, SinguloBlocks.QUANTUM_HEAT_ENGINE, SinguloBlocks.INERTIAL_STABILIZER,
                SinguloBlocks.PROBE_STATION, SinguloBlocks.DEGENERATE_FURNACE_CONTROLLER,
                SinguloBlocks.SHIELD_TOWER_CORE, SinguloBlocks.TIPLER_CORE);
        BlockPos a=new BlockPos(2,1,2), b=new BlockPos(5,1,2);
        var player=h.makeMockServerPlayerInLevel();
        try {
            for(var block:blocks) {
                player.getInventory().clearContent();
                h.setBlock(a,block.get());
                var before=h.getBlockEntity(a);
                var tag=before.saveCustomOnly(h.getLevel().registryAccess());
                // Every audited generator/storage/catalyst device persists this buffer.
                if(tag.contains("energy")) tag.putInt("energy",1234);
                before.loadWithComponents(tag,h.getLevel().registryAccess());
                var expected=before.saveCustomOnly(h.getLevel().registryAccess());
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
                var after=h.getBlockEntity(b);
                h.assertTrue(after!=null && expected.equals(after.saveCustomOnly(h.getLevel().registryAccess())),"Device state changed on relocation: "+block.getId());
                // Clear the empty test fixture without running its production loot path.
                h.getLevel().removeBlockEntity(h.absolutePos(b));
                h.setBlock(b,net.minecraft.world.level.block.Blocks.AIR);
            }
        } finally {player.discard();}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void wrenchDropsOnePackedDeviceWhenInventoryIsFull(GameTestHelper h) {
        BlockPos pos=new BlockPos(2,1,2);
        h.setBlock(pos,SinguloBlocks.MACHINES.get(MachineType.KILN).get());
        MachineBlockEntity machine=h.getBlockEntity(pos);
        machine.items().setStackInSlot(0,new ItemStack(Items.COAL,13));
        var player=h.makeMockServerPlayerInLevel();
        try {
            player.setPos(Vec3.atCenterOf(h.absolutePos(pos)));
            for(int n=0;n<36;n++) player.getInventory().setItem(n,new ItemStack(Items.DIRT,64));
            var wrench=new ItemStack(SinguloItems.RELOCATION_WRENCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
            player.setShiftKeyDown(true);
            wrench.getItem().onItemUseFirst(wrench,context(player,h.absolutePos(pos)));
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(h.absolutePos(pos)).inflate(3));
            h.assertTrue(drops.size()==1 && drops.getFirst().getItem().is(SinguloBlocks.MACHINES.get(MachineType.KILN).get().asItem())
                    && drops.getFirst().getItem().has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA),"Full inventory lost contents or produced extra loose drops: "+drops.stream().map(e->e.getItem().toString()).toList());
        } finally {player.discard();}
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=60)
    public static void relocatingAnchorReleasesOldTicketsAndRestartsAtNewPosition(GameTestHelper h) {
        BlockPos a=new BlockPos(2,1,2), b=new BlockPos(5,1,2);
        h.setBlock(a,SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get());
        WorldlineAnchorBlockEntity original=h.getBlockEntity(a);
        var owner=java.util.UUID.randomUUID();
        original.setOwner(owner);
        original.energy().setEnergy(original.energy().getMaxEnergyStored());
        original.catalystSlot().insertItem(0,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst"))),false);
        h.runAtTickTime(10,()->{
            h.assertTrue(original.loadedRadius()>=0,"Anchor did not start before packing");
            var player=h.makeMockServerPlayerInLevel();
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
                WorldlineAnchorBlockEntity restored=h.getBlockEntity(b);
                h.assertTrue(restored!=null && !restored.catalystSlot().getStackInSlot(0).isEmpty(),"Anchor lost its catalyst");
            } finally {player.discard();}
        });
        h.runAtTickTime(30,()->{
            WorldlineAnchorBlockEntity restored=h.getBlockEntity(b);
            h.assertTrue(restored.loadedRadius()>=0,"Relocated anchor did not create new position tickets");
            restored.prepareForRelocation();
            h.succeed();
        });
    }

}
