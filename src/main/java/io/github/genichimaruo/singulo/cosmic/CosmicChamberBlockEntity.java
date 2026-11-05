package io.github.genichimaruo.singulo.cosmic;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Finite storage and a reserved-work spindle, with no runtime chunk loading. */
public final class CosmicChamberBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener, MenuProvider {
    public static final int RESERVOIR=0, ACCUMULATOR=1, SPINDLE=2;
    public static final int FLUID_CAPACITY=16_000_000;
    public static final long PASTA_CAPACITY=8_000_000_000_000L, PASTA_RATE=2_000_000_000L;
    public static final long SPINDLE_CAPACITY=20_000_000_000L, SPINDLE_RATE=200_000_000L, CORE_COST=10_000_000_000L;
    private long stored, pendingOutput, budgetTick=Long.MIN_VALUE, tickIn, tickOut;
    private int lastIn, lastOut, progress, duration, cost, status=1;
    private boolean core;
    private boolean retired;
    private ItemStack reserved=ItemStack.EMPTY;
    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override public int getSlotLimit(int slot) { return slot==2?1:64; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return kind()==SPINDLE && acceptsSpindleItem(slot,stack);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot==3 ? stack : super.insertItem(slot,stack,simulate);
        }
    };
    private final FluidTank tank = new FluidTank(FLUID_CAPACITY) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private final Energy input=new Energy(true,false), output=new Energy(false,true), both=new Energy(true,true);
    private final Fluids fluidInput=new Fluids(true,false), fluidOutput=new Fluids(false,true), fluidBoth=new Fluids(true,true);
    private final IItemHandler automation=new IItemHandler() {
        public int getSlots(){return 4;}
        public ItemStack getStackInSlot(int slot){return items.getStackInSlot(slot);}
        public int getSlotLimit(int slot){return slot==2?1:64;}
        public boolean isItemValid(int slot,ItemStack stack){return items.isItemValid(slot,stack);}
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return ready()?items.insertItem(slot,stack,simulate):stack;}
        public ItemStack extractItem(int slot,int count,boolean simulate){return slot==3&&ready()?items.extractItem(slot,count,simulate):ItemStack.EMPTY;}
    };

    public CosmicChamberBlockEntity(BlockPos pos, BlockState state) { super(SinguloBlockEntities.COSMIC_CHAMBER.get(),pos,state); }
    public static boolean acceptsSpindleItem(int slot,ItemStack stack){
        return slot>=0&&slot<3&&stack.is(BuiltInRegistries.ITEM.get(Singulo.id(
                slot==0?"collapsar_billet":slot==1?"exotic_matter":"singularity_seed")));
    }
    public int kind() {
        String id=BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath();
        return id.equals("suspension_reservoir")?RESERVOIR:id.equals("pasta_accumulator")?ACCUMULATOR:SPINDLE;
    }
    public Direction back(){return getBlockState().getValue(AbstractMachineBlock.FACING).getOpposite();}
    public long stored(){return stored;}
    public long capacity(){return kind()==ACCUMULATOR?PASTA_CAPACITY:SPINDLE_CAPACITY;}
    public long rate(){return kind()==ACCUMULATOR?PASTA_RATE:SPINDLE_RATE;}
    public FluidTank tank(){return tank;}
    public ItemStackHandler items(){return items;}
    public boolean menuActive(){return !retired&&!isRemoved();}
    /** Retire access before close callbacks run; never serialize this runtime-only state. */
    public void retireForRemoval(){
        if(retired)return;
        retired=true;
        if(level instanceof ServerLevel server)for(var player:java.util.List.copyOf(server.players()))
            if(player.containerMenu instanceof CosmicMenu menu&&menu.ownedBy(this))player.closeContainer();
    }
    public int status(){return status;}
    public boolean core(){return core;}
    public int progress(){return progress;}
    public int duration(){return duration;}
    public int lastIn(){return lastIn;}
    public int lastOut(){return lastOut;}
    public ItemStack reserved(){return reserved;}
    public int tankCapacity(){return kind()==RESERVOIR?FLUID_CAPACITY:16_000;}
    public LongEnergyStorage energyFor(Direction side){return side==null?both:kind()==ACCUMULATOR&&side==back().getOpposite()?output:input;}
    public IFluidHandler fluidFor(Direction side){return side==null?fluidBoth:kind()==RESERVOIR&&side==back().getOpposite()?fluidOutput:fluidInput;}
    public IItemHandler automation(){return automation;}

    /** Every external mutation checks the loaded structure, including changes in this tick. */
    public boolean ready() {
        if(level==null || level.isClientSide || isRemoved())return false;
        var shape=CosmicLayouts.of(kind());
        for(int x=0;x<shape.width();x++) for(int z=0;z<shape.depth();z++)
            if(!level.isLoaded(shape.pos(worldPosition,back(),x,0,z))){status=2;return false;}
        boolean valid=shape.matches(level,worldPosition,back(),getBlockState().getBlock(),new ArrayList<>());
        status=valid?0:1;
        return valid;
    }
    private void budgets() {
        long now=level==null?0:level.getGameTime();
        if(now!=budgetTick){budgetTick=now;lastIn=(int)(tickIn/1_000_000);lastOut=(int)(tickOut/1_000_000);tickIn=0;tickOut=0;}
    }
    public static void serverTick(Level level,BlockPos pos,BlockState state,CosmicChamberBlockEntity be) {
        be.budgets();
        if(be.ready()) {
            if(be.kind()==ACCUMULATOR)be.pushPower();
            else if(be.kind()==SPINDLE)be.process((ServerLevel)level);
        }
        if(level.getGameTime()%20==0){be.setChanged();level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}
    }
    private void pushPower() {
        Direction front=back().getOpposite();BlockPos next=worldPosition.relative(front);
        if(stored<=0 || !level.isLoaded(next))return;
        IEnergyStorage target=level.getCapability(Capabilities.EnergyStorage.BLOCK,next,front.getOpposite());
        if(target==null || !target.canReceive())return;
        sendPower(target);
    }
    private void sendPower(IEnergyStorage target) {
        budgets();
        long offered=Math.min(stored,Math.max(0,rate()-tickOut));
        if(offered<=0)return;
        stored-=offered;tickOut+=offered;pendingOutput+=offered;
        long paid=0;
        try {paid=Math.max(0,Math.min(offered,LongEnergyStorage.receive(target,offered,false)));}
        finally {
            // Unaccepted power still owns its capacity while the foreign handler runs.
            // Re-entrant input cannot occupy that room and then overflow on refund.
            stored+=offered-paid;tickOut-=offered-paid;pendingOutput-=offered;
        }
        if(paid>0)setChanged();
    }
    private void process(ServerLevel server) {
        if(!core) {
            if(stored<CORE_COST || !items.getStackInSlot(2).is(BuiltInRegistries.ITEM.get(Singulo.id("singularity_seed"))))return;
            stored-=CORE_COST;items.extractItem(2,1,false);core=true;setChanged();
        }
        if(!reserved.isEmpty()) {
            if(progress<duration && stored>=cost){stored-=cost;progress++;setChanged();}
            if(progress>=duration && fits(reserved)){
                ItemStack out=items.getStackInSlot(3);
                if(out.isEmpty())items.setStackInSlot(3,reserved.copy());else {out.grow(reserved.getCount());items.setStackInSlot(3,out);}
                reserved=ItemStack.EMPTY;progress=0;duration=0;setChanged();
            }
            return;
        }
        if(server.getGameTime()%10!=0)return;
        for(var holder:io.github.genichimaruo.singulo.recipe.RecipeTweaks.adjustAll(server.getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get()))){
            MachineRecipe r=holder.value();
            // This chamber has one ordinary output and no catalyst, mass or fluid-output slots.
            // Refuse incompatible data-pack recipes before reserving any resources.
            if(!r.station().equals("tidal_spindle") || r.mass().isPresent() || r.restore().isPresent()
                    || !r.byproduct().isEmpty() || !r.fluidResults().isEmpty() || r.energy()<0
                    || r.ingredients().stream().anyMatch(in->in.count()<=0 || in.uses()>0 || in.tier()>0)
                    || r.fluidIngredients().stream().anyMatch(in->in.amount()<=0))continue;
            int[] left={items.getStackInSlot(0).getCount(),items.getStackInSlot(1).getCount()},take=new int[2];boolean valid=true;
            for(var in:r.ingredients()){
                int need=in.count();
                for(int i=0;i<2&&need>0;i++)if(in.ingredient().test(items.getStackInSlot(i))){int n=Math.min(left[i],need);left[i]-=n;take[i]+=n;need-=n;}
                if(need>0)valid=false;
            }
            long coolant=0;
            for(var in:r.fluidIngredients()){if(!in.matches(tank.getFluid()))valid=false;coolant+=in.amount();}
            if(!valid || coolant>tank.getFluidAmount() || coolant<0 || stored<r.energy() || !fits(r.result()))continue;
            for(int i=0;i<2;i++)items.extractItem(i,take[i],false);
            tank.drain((int)coolant,IFluidHandler.FluidAction.EXECUTE);
            reserved=r.result().copy();progress=0;duration=Math.max(1,r.time());cost=Math.max(0,r.energy());setChanged();return;
        }
    }
    private boolean fits(ItemStack product){ItemStack out=items.getStackInSlot(3);return !product.isEmpty() && product.getCount()<=product.getMaxStackSize() && (out.isEmpty() || ItemStack.isSameItemSameComponents(out,product) && out.getCount()+product.getCount()<=out.getMaxStackSize());}

    private final class Energy implements LongEnergyStorage {
        private final boolean receive,extract;
        Energy(boolean receive,boolean extract){this.receive=receive;this.extract=extract;}
        public long receiveLong(long amount,boolean simulate){
            if(!receive || amount<=0 || kind()==RESERVOIR || !ready())return 0;budgets();
            long n=Math.min(amount,Math.min(Math.max(0,capacity()-stored-pendingOutput),Math.max(0,rate()-tickIn)));
            if(!simulate&&n>0){stored+=n;tickIn+=n;setChanged();}return n;
        }
        public long extractLong(long amount,boolean simulate){
            if(!extract || kind()!=ACCUMULATOR || amount<=0 || !ready())return 0;budgets();
            long n=Math.min(amount,Math.min(stored,Math.max(0,rate()-tickOut)));
            if(!simulate&&n>0){stored-=n;tickOut+=n;setChanged();}return n;
        }
        public int receiveEnergy(int n,boolean simulate){return(int)receiveLong(n,simulate);}
        public int extractEnergy(int n,boolean simulate){return(int)extractLong(n,simulate);}
        public int getEnergyStored(){return(int)Math.min(Integer.MAX_VALUE,stored);}
        public int getMaxEnergyStored(){return(int)Math.min(Integer.MAX_VALUE,capacity());}
        public boolean canReceive(){return receive&&kind()!=RESERVOIR;}
        public boolean canExtract(){return extract&&kind()==ACCUMULATOR;}
    }
    private final class Fluids implements IFluidHandler {
        private final boolean fill,drain;
        Fluids(boolean fill,boolean drain){this.fill=fill;this.drain=drain;}
        public int getTanks(){return kind()==ACCUMULATOR?0:1;}
        public FluidStack getFluidInTank(int n){return n==0?tank.getFluid().copy():FluidStack.EMPTY;}
        public int getTankCapacity(int n){return n==0?tankCapacity():0;}
        public boolean isFluidValid(int n,FluidStack f){return n==0&&kind()!=ACCUMULATOR&&(kind()==RESERVOIR||f.getFluid()==io.github.genichimaruo.singulo.registry.SinguloFluids.get("liquid_helium"));}
        public int fill(FluidStack f,FluidAction action){
            if(!fill||!isFluidValid(0,f)||!ready())return 0;
            int room=Math.max(0,tankCapacity()-tank.getFluidAmount());return tank.fill(f.copyWithAmount(Math.min(f.getAmount(),room)),action);
        }
        public FluidStack drain(FluidStack f,FluidAction a){return drain&&kind()==RESERVOIR&&ready()?tank.drain(f,a):FluidStack.EMPTY;}
        public FluidStack drain(int n,FluidAction a){return drain&&kind()==RESERVOIR&&ready()?tank.drain(n,a):FluidStack.EMPTY;}
    }
    @Override public void openMenu(ServerPlayer player){player.openMenu(this,b->{b.writeBlockPos(worldPosition);b.writeBoolean(kind()==SPINDLE);});}
    @Override public Component getDisplayName(){return Component.translatable(getBlockState().getBlock().getDescriptionId());}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){
        return new CosmicMenu(id,inv,worldPosition,items,kind()==SPINDLE,SyncedInts.server(CosmicMenu.COUNT,i->switch(i){
            case 0->kind();case 1->status;case 2->(int)(stored/1_000_000);case 3->(int)(capacity()/1_000_000);
            case 4->lastIn;case 5->lastOut;case 6->tank.getFluidAmount();case 7->tankCapacity();case 8->progress;case 9->duration;case 10->core?1:0;
            case 11->tank.isEmpty()?0:BuiltInRegistries.FLUID.getId(tank.getFluid().getFluid())+1;default->0;
        }),this);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.put("items",items.serializeNBT(registries));write(tag,registries);
    }
    private void write(CompoundTag tag,HolderLookup.Provider registries){
        tag.putLong("stored",stored);tag.put("tank",tank.writeToNBT(registries,new CompoundTag()));
        tag.putInt("progress",progress);tag.putInt("duration",duration);tag.putInt("cost",cost);tag.putBoolean("core",core);tag.putInt("status",status);
        tag.putInt("in",lastIn);tag.putInt("out",lastOut);if(!reserved.isEmpty())tag.put("reserved",reserved.save(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("items"))items.deserializeNBT(registries,tag.getCompound("items"));
        stored=Math.max(0,Math.min(capacity(),tag.getLong("stored")));tank.readFromNBT(registries,tag.getCompound("tank"));
        if(tank.getFluidAmount()>tankCapacity())tank.setFluid(tank.getFluid().copyWithAmount(tankCapacity()));
        duration=Math.max(0,tag.getInt("duration"));progress=Math.max(0,Math.min(duration,tag.getInt("progress")));cost=Math.max(0,tag.getInt("cost"));core=tag.getBoolean("core");
        status=tag.getInt("status");lastIn=tag.getInt("in");lastOut=tag.getInt("out");
        reserved=tag.contains("reserved")?ItemStack.parseOptional(registries,tag.getCompound("reserved")):ItemStack.EMPTY;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();write(tag,registries);return tag;}
    @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
