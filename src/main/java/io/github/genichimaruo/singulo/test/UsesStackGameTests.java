package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class UsesStackGameTests {
    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)), count);
    }

    @GameTest(template="empty")
    public static void unusedOriginalsAndPartsStackButUsedOnesDoNot(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        for (String id : new String[]{"observation_log", "quantum_data_fragment", "culture_data", "control_unit",
                "cold_atom_trap", "time_crystal_seed", "anomaly_sample"}) {
            ItemStack fresh = item(id,64);
            h.assertTrue(fresh.getMaxStackSize()==64, "Unused item cannot stack: "+id);
            h.assertFalse(UsesHelper.canUse(fresh,1),"Whole stack must not be consumed as one original");
            ItemStack used = UsesHelper.consume(fresh.copyWithCount(1),1);
            h.assertTrue(used.getCount()==1 && used.getMaxStackSize()==1,"Used item can stack: "+id);
            ItemStack loaded = ItemStack.of(used.save(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(loaded.getMaxStackSize()==1 && UsesHelper.remaining(loaded)==UsesHelper.remaining(used),"Usage state lost after save: "+id);
            ItemStack exhausted = UsesHelper.consume(fresh.copyWithCount(1),UsesHelper.remaining(fresh));
            ItemStack repaired = UsesHelper.restore(exhausted,fresh.copyWithCount(1));
            h.assertTrue(repaired.getMaxStackSize()==64 && UsesHelper.repairsOf(repaired)>0,"Unused repaired item cannot stack: "+id);
            h.assertFalse(ItemStack.isSameItemSameTags(fresh,repaired),"Repair history merged with fresh items");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=500)
    public static void archiveTakesOneOriginalAndUsesOnlyThatOne(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        BlockPos pos=new BlockPos(2,1,2);
        h.setBlock(pos,SinguloBlocks.MACHINES.get(MachineType.ARCHIVE_TERMINAL).get());
        MachineBlockEntity archive=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, pos);
        ItemStack originals=item("observation_log",64);
        ItemStack simulated=archive.automationItems().insertItem(0,originals,true);
        h.assertTrue(simulated.getCount()==63 && archive.items().getStackInSlot(0).isEmpty(),"Simulation consumed originals");
        ItemStack rest=archive.automationItems().insertItem(0,originals,false);
        h.assertTrue(rest.getCount()==63 && originals.getCount()==64 && archive.items().getStackInSlot(0).getCount()==1,"Insertion failed to split one original");
        h.assertTrue(archive.automationItems().insertItem(0,rest,false).getCount()==63,"Input accepted another original into the same slot");
        archive.items().insertItem(1,item("blank_data_card",1),false);
        h.onEachTick(()->archive.energy().setEnergy(archive.energy().getMaxEnergyStored()));
        h.succeedWhen(()->{
            ItemStack output=archive.items().getStackInSlot(archive.type().outputSlot());
            h.assertTrue(output.is(item("data_card_observation_log",1).getItem()) && output.getCount()==1,"No copied data card");
            ItemStack active=archive.items().getStackInSlot(0);
            h.assertTrue(active.getCount()==1 && active.getMaxStackSize()==1 && UsesHelper.remaining(active)==15,"Used original lost or wrong uses");
            h.assertTrue(rest.getCount()==63 && rest.getMaxStackSize()==64 && UsesHelper.remaining(rest)==16,"Unused remainder changed");
        });
    }

    @GameTest(template="empty")
    public static void menuShiftClickInsertsOnlyOneOriginalAndNormalMaterialsStillStack(GameTestHelper h) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(h);
        BlockPos pos=new BlockPos(2,1,2);
        h.setBlock(pos,SinguloBlocks.MACHINES.get(MachineType.ARCHIVE_TERMINAL).get());
        MachineBlockEntity archive=io.github.genichimaruo.singulo.compat.Legacy.blockEntity(h, pos);
        var player=io.github.genichimaruo.singulo.compat.Legacy.mockServerPlayer(h);
        try {
            player.getInventory().setItem(9,item("quantum_data_fragment",64));
            MachineMenu menu=new MachineMenu(1,player.getInventory(),archive);
            h.assertTrue(menu.getSlot(0).getMaxStackSize(item("quantum_data_fragment",64))==1,"GUI slot accepts multiple originals");
            menu.quickMoveStack(player,archive.type().itemSlots());
            int total=0;
            for (int i=0;i<archive.type().inputSlots();i++) total+=archive.items().getStackInSlot(i).getCount();
            h.assertTrue(total==1 && player.getInventory().getItem(9).getCount()==63,"Shift-click scattered or lost originals");
            menu.quickMoveStack(player,archive.type().itemSlots());
            total=0;
            for (int i=0;i<archive.type().inputSlots();i++) {
                int count=archive.items().getStackInSlot(i).getCount();
                h.assertTrue(count<=1,"Repeated shift-click merged originals past the slot limit");
                total+=count;
            }
            h.assertTrue(total==2 && player.getInventory().getItem(9).getCount()==62,"Repeated shift-click lost originals");
            ItemStack rest=archive.items().insertItem(2,new ItemStack(Items.IRON_INGOT,64),false);
            h.assertTrue(rest.isEmpty() && archive.items().getStackInSlot(2).getCount()==64,"Ordinary ingredients stopped stacking");
        } finally {player.discard();}
        h.succeed();
    }
}
