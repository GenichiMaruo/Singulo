package io.github.genichimaruo.singulo.cosmic;

import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public final class CosmicMenu extends AbstractContainerMenu {
    public static final int COUNT=14;
    /** 画面の枠（DeviceMenu と同じ大きさ。インベントリは枠の中央）とスロットの位置。CosmicScreen と同じ値。 */
    public static final int WIDTH=212, HEIGHT=206;
    public static final int SLOT_Y=72, SEED_X=25, BILLET_X=61, EXOTIC_X=81, OUTPUT_X=147;
    private final BlockPos pos;
    private final SyncedInts data;
    private final int machineSlots;
    private final CosmicChamberBlockEntity owner;
    /** Client and visual-fixture constructor; server menus must bind their inventory owner. */
    public CosmicMenu(int id,Inventory inventory,BlockPos pos,IItemHandler items,boolean spindle,SyncedInts data){
        this(id,inventory,pos,items,spindle,data,null);
    }
    CosmicMenu(int id,Inventory inventory,BlockPos pos,IItemHandler items,boolean spindle,SyncedInts data,CosmicChamberBlockEntity owner){
        super(SinguloMenus.COSMIC.get(),id);this.pos=pos;this.data=data;addDataSlots(data);
        this.owner=owner;
        machineSlots=spindle?4:0;
        if(spindle)for(int i=0;i<4;i++){
            final int index=i;
            addSlot(new SlotItemHandler(items,i,i==0?BILLET_X:i==1?EXOTIC_X:i==2?SEED_X:OUTPUT_X,SLOT_Y){
                @Override public boolean mayPlace(ItemStack stack){return index!=3&&(owner==null||stillValid(inventory.player))&&super.mayPlace(stack);}
                @Override public boolean mayPickup(Player player){return (owner==null||stillValid(player))&&super.mayPickup(player);}
            });
        }
        int left=io.github.genichimaruo.singulo.machine.GuiFrame.inventoryX(WIDTH);
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inventory,x+y*9+9,left+x*18,io.github.genichimaruo.singulo.machine.GuiFrame.inventoryY(HEIGHT)+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(inventory,x,left+x*18,io.github.genichimaruo.singulo.machine.GuiFrame.hotbarY(HEIGHT)));
    }
    public static CosmicMenu client(int id,Inventory inventory,FriendlyByteBuf buf){
        return new CosmicMenu(id,inventory,buf.readBlockPos(),new ItemStackHandler(4){
            @Override public int getSlotLimit(int slot){return slot==2?1:64;}
            @Override public boolean isItemValid(int slot,ItemStack stack){return CosmicChamberBlockEntity.acceptsSpindleItem(slot,stack);}
        },buf.readBoolean(),SyncedInts.client(COUNT));
    }
    public BlockPos pos(){return pos;}
    public int value(int i){return data.getInt(i);}
    public boolean ownedBy(CosmicChamberBlockEntity be){return owner==be;}
    @Override public boolean stillValid(Player player){
        return player.level().isClientSide || owner!=null&&owner.menuActive()&&owner.getLevel()==player.level()
                &&player.level().getBlockEntity(pos)==owner&&io.github.genichimaruo.singulo.multiblock.Blueprints.withinMenuReach(player,pos);
    }
    /** 画面のボタン: ポートの向きを切り替える（0 が左、1 が右）。 */
    @Override public boolean clickMenuButton(Player player,int id){
        if(owner==null||!stillValid(player)||id<0||id>1)return false;
        owner.cyclePort(id);return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(stillValid(player))super.clicked(slot,button,type,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player))return ItemStack.EMPTY;
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
