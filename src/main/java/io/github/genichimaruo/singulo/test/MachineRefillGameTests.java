package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

@net.neoforged.neoforge.gametest.GameTestHolder(Singulo.MODID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class MachineRefillGameTests {
    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)),count);
    }
    private static MachineBlockEntity machine(GameTestHelper h, BlockPos pos, MachineType type) {
        h.setBlock(pos,SinguloBlocks.MACHINES.get(type).get());
        return h.getBlockEntity(pos);
    }
    private static void takeOutput(GameTestHelper h, MachineMenu menu, MachineBlockEntity machine, Player player) {
        menu.clicked(machine.type().outputSlot(),0,ClickType.PICKUP,player);
        h.assertTrue(!menu.getCarried().isEmpty() && machine.items().getStackInSlot(machine.type().outputSlot()).isEmpty(),"Could not take finished output");
        player.getInventory().add(menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
    }
    private static void refillCompressor(GameTestHelper h, boolean shift) {
        MachineBlockEntity machine=machine(h,new BlockPos(2,1,2),MachineType.COMPRESSOR);
        machine.items().setStackInSlot(0,new ItemStack(Items.CHARCOAL,4));
        var player=io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        MachineMenu menu=new MachineMenu(1,player.getInventory(),machine);
        h.onEachTick(()->machine.energy().setEnergy(machine.energy().getMaxEnergyStored()));
        h.startSequence()
                .thenWaitUntil(()->h.assertTrue(machine.items().getStackInSlot(machine.type().outputSlot()).getCount()==2
                        && machine.items().getStackInSlot(0).getCount()==1 && machine.status()==MachineBlockEntity.Status.IDLE,"First batch has not stopped with one charcoal left"))
                .thenExecute(()->takeOutput(h,menu,machine,player))
                .thenIdle(3)
                .thenExecute(()->{
                    if (shift) {
                        player.getInventory().setItem(9,new ItemStack(Items.CHARCOAL,2));
                        menu.quickMoveStack(player,machine.type().itemSlots());
                    } else {
                        menu.setCarried(new ItemStack(Items.CHARCOAL,2));
                        menu.clicked(0,0,ClickType.PICKUP,player);
                    }
                    h.assertTrue(machine.items().getStackInSlot(0).getCount()==3,"Refill did not merge into existing charcoal");
                })
                .thenWaitUntil(()->h.assertTrue(machine.items().getStackInSlot(machine.type().outputSlot()).getCount()==2
                        && machine.items().getStackInSlot(0).isEmpty(),"Compressor stayed idle after refilling existing material"))
                .thenExecute(player::discard).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void compressorResumesAfterOutputTakenAndClickRefill(GameTestHelper h) { refillCompressor(h,false); }
    @GameTest(template="empty",timeoutTicks=400)
    public static void compressorResumesAfterOutputTakenAndShiftRefill(GameTestHelper h) { refillCompressor(h,true); }

    @GameTest(template="empty",timeoutTicks=800)
    public static void kilnAndCatalyticReactorAlsoResumeAfterPartialStackRefill(GameTestHelper h) {
        var kiln=machine(h,new BlockPos(2,1,2),MachineType.KILN);
        var reactor=machine(h,new BlockPos(5,1,2),MachineType.CATALYTIC_REACTOR);
        var machines=List.of(kiln,reactor);
        var player=io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        var menus=List.of(new MachineMenu(1,player.getInventory(),kiln),new MachineMenu(2,player.getInventory(),reactor));
        kiln.items().setStackInSlot(0,item("nihonium",3));
        reactor.items().setStackInSlot(0,item("spent_muon_catalyst",5));
        h.onEachTick(()->machines.forEach(m->m.energy().setEnergy(m.energy().getMaxEnergyStored())));
        h.startSequence()
                .thenWaitUntil(()->{
                    for(var m:machines) h.assertTrue(!m.items().getStackInSlot(m.type().outputSlot()).isEmpty()
                            && m.items().getStackInSlot(0).getCount()==1 && m.status()==MachineBlockEntity.Status.IDLE,"First batch did not finish: "+m.type());
                })
                .thenExecute(()->{for(int i=0;i<2;i++) takeOutput(h,menus.get(i),machines.get(i),player);})
                .thenIdle(3)
                .thenExecute(()->{
                    player.getInventory().setItem(9,item("nihonium",1));
                    menus.get(0).quickMoveStack(player,kiln.type().itemSlots());
                    player.getInventory().setItem(9,item("spent_muon_catalyst",3));
                    menus.get(1).quickMoveStack(player,reactor.type().itemSlots());
                })
                .thenWaitUntil(()->{
                    for(var m:machines) h.assertTrue(!m.items().getStackInSlot(m.type().outputSlot()).isEmpty()
                            && m.items().getStackInSlot(0).isEmpty(),"Machine did not resume after refill: "+m.type());
                }).thenExecute(player::discard).thenSucceed();
    }
    private static void checkMenuMerge(GameTestHelper h, Player player, String name, int size, int target, ItemStack fuel,
            java.util.function.Function<net.neoforged.neoforge.items.IItemHandler, net.minecraft.world.inventory.AbstractContainerMenu> factory) {
        checkMenuMerge(h, player, name, size, size, target, fuel, factory);
    }

    /** firstPlayerSlot: メニューの中で、プレイヤーの持ち物が始まる番号（装置のスロットのほかに出口などがあればその分あと）。 */
    private static void checkMenuMerge(GameTestHelper h, Player player, String name, int size, int firstPlayerSlot, int target, ItemStack fuel,
            java.util.function.Function<net.neoforged.neoforge.items.IItemHandler, net.minecraft.world.inventory.AbstractContainerMenu> factory) {
        class Inventory extends net.neoforged.neoforge.items.ItemStackHandler {
            int changes;
            Inventory() { super(size); }
            @Override protected void onContentsChanged(int slot) { changes++; }
        }
        Inventory inventory=new Inventory();
        inventory.setStackInSlot(target,fuel.copyWithCount(1));
        player.getInventory().clearContent();
        player.getInventory().setItem(9,fuel.copyWithCount(2));
        var menu=factory.apply(inventory);
        int before=inventory.changes;
        menu.quickMoveStack(player,firstPlayerSlot);
        h.assertTrue(inventory.getStackInSlot(target).getCount()==3,"Fuel did not merge: "+name);
        h.assertTrue(inventory.changes>before,"GUI did not notify the inventory: "+name);
    }

    @GameTest(template="empty")
    public static void otherDeviceMenusNotifyInventoryWhenShiftMergingFuel(GameTestHelper h) {
        var player=io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        try {
            var pos=h.absolutePos(new BlockPos(2,1,2));
            checkMenuMerge(h,player,"generator",2,0,new ItemStack(Items.CHARCOAL),
                    inv->new GeneratorSlotsMenu(1,player.getInventory(),pos,0,inv,SyncedInts.client(GeneratorSlotsMenu.COUNT)));
            checkMenuMerge(h,player,"catalyst device",2,3,1,item("compressed_block_2",1),
                    inv->new CatalystDeviceMenu(1,player.getInventory(),pos,CatalystDeviceBlockEntity.Kind.DEGENERATE_FURNACE,inv,new net.neoforged.neoforge.items.ItemStackHandler(1),SyncedInts.client(CatalystDeviceBlockEntity.COUNT)));
            checkMenuMerge(h,player,"wormhole mouth",1,0,item("exotic_matter",1),
                    inv->new DeviceMenu(1,player.getInventory(),DeviceMenu.Kind.WORMHOLE_MOUTH,pos,inv,SyncedInts.client(DeviceMenu.Mouth.COUNT),null));
            checkMenuMerge(h,player,"reactor",io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.SLOTS,1,new ItemStack(Items.COBBLESTONE),
                    inv->new io.github.genichimaruo.singulo.reactor.PenroseReactorMenu(1,player.getInventory(),pos,inv,
                            SyncedInts.client(io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity.COUNT),null));
            checkMenuMerge(h,player,"wormhole stabilizer",io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity.SLOTS,
                    io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity.SLOT_FUEL,item("exotic_matter",1),
                    inv->new io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu(1,player.getInventory(),pos,inv,
                            SyncedInts.client(io.github.genichimaruo.singulo.wormhole.WormholeStabilizerMenu.COUNT),null));
        } finally {player.discard();}
        h.succeed();
    }

}
