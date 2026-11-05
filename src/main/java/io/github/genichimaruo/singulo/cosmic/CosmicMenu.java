package io.github.genichimaruo.singulo.cosmic;

import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class CosmicMenu extends AbstractContainerMenu {
    public static final int COUNT=12;
    private final BlockPos pos;
    private final SyncedInts data;
    private final int machineSlots;
    public CosmicMenu(int id,Inventory inventory,BlockPos pos,IItemHandler items,boolean spindle,SyncedInts data){
        super(SinguloMenus.COSMIC.get(),id);this.pos=pos;this.data=data;addDataSlots(data);
        machineSlots=spindle?4:0;
        if(spindle)for(int i=0;i<4;i++){
            final int index=i;
            addSlot(new SlotItemHandler(items,i,i==3?154:30+i*28,92){
                @Override public boolean mayPlace(ItemStack stack){return index!=3&&super.mayPlace(stack);}
            });
        }
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inventory,x+y*9+9,26+x*18,128+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(inventory,x,26+x*18,186));
    }
    public static CosmicMenu client(int id,Inventory inventory,RegistryFriendlyByteBuf buf){
        return new CosmicMenu(id,inventory,buf.readBlockPos(),new ItemStackHandler(4){
            @Override public int getSlotLimit(int slot){return slot==2?1:64;}
            @Override public boolean isItemValid(int slot,ItemStack stack){return CosmicChamberBlockEntity.acceptsSpindleItem(slot,stack);}
        },buf.readBoolean(),SyncedInts.client(COUNT));
    }
    public BlockPos pos(){return pos;}
    public int value(int i){return data.getInt(i);}
    @Override public boolean stillValid(Player player){return player.level().getBlockEntity(pos) instanceof CosmicChamberBlockEntity&&io.github.genichimaruo.singulo.multiblock.Blueprints.withinMenuReach(player,pos);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();
        boolean moved=index<machineSlots?moveItemStackTo(stack,machineSlots,slots.size(),true):machineSlots>0&&moveItemStackTo(stack,0,3,false);
        if(!moved && index>=machineSlots){
            int hotbar=machineSlots+27;
            moved=index<hotbar?moveItemStackTo(stack,hotbar,slots.size(),false):moveItemStackTo(stack,machineSlots,hotbar,false);
        }
        if(!moved)return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        slot.onTake(player,stack);return copy;
    }
}
