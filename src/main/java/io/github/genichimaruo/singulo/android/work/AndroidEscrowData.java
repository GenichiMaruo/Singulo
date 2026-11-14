package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Per-dimension physical custody. Internal trusted server-thread API, not a capability.
 * Entity and SavedData writes are not crash-atomic. Invalid saved records are retained verbatim.
 */
public final class AndroidEscrowData extends SavedData {
    public enum State { PREPARING, RUNNING, RETURNING, RECOVERABLE, COMPLETED, QUARANTINED }
    public static final int MAX_RECORDS = 64, MAX_OFFER_STACKS = 128;
    public static final class Entry {
        public final UUID request, owner, consoleIdentity;
        public final BlockPos console;
        private final ItemStack target;
        public final long count;
        private final List<ItemStack> originalOffer, inputs = new ArrayList<>(), products = new ArrayList<>();
        public UUID worker;
        public UUID requestedWorker;
        public int orderId = -1;
        public State state = State.PREPARING;
        public AndroidOrders.State outcome = AndroidOrders.State.RUNNING;
        public long delivered;
        private Entry(UUID request, UUID owner, BlockPos console, UUID identity, ItemStack target, long count, List<ItemStack> offer) {
            this.request=request; this.owner=owner; this.console=console.immutable(); this.consoleIdentity=identity;
            this.target=target.copyWithCount(1); this.count=count; this.originalOffer=copy(offer);
        }
        public ItemStack target() { return target.copy(); }
        public List<ItemStack> originalOffer() { return copy(originalOffer); }
    }
    private final Map<UUID,Entry> records = new LinkedHashMap<>();
    private final List<Tag> quarantined = new ArrayList<>();
    private final Set<UUID> blocked = new HashSet<>();
    private final ListTag salvage = new ListTag();
    private HolderLookup.Provider registriesForRecovery;
    private boolean invalidRoot;
    private CompoundTag rawRoot;
    public static AndroidEscrowData get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Escrow requires server thread");
        var data = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(AndroidEscrowData::new, AndroidEscrowData::load, null), "singulo_android_escrow_v1");
        data.registriesForRecovery=level.registryAccess(); return data;
    }
    private static List<ItemStack> copy(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::copy).toList(); }
    public List<Entry> entries() { return List.copyOf(records.values()); }
    public Entry find(UUID request) { return records.get(request); }
    public boolean isKnown(UUID request) { return invalidRoot || records.containsKey(request) || blocked.contains(request); }
    public boolean quarantined(UUID request) { return invalidRoot || blocked.contains(request); }
    /** Emergency custody only, never claimable. Caller clears actual cargo only after true.
     * This is not an idempotent retry endpoint: each call transfers the supplied real cargo.
     */
    public boolean quarantineCargo(UUID request, UUID owner, UUID worker, List<ItemStack> actualCargo) {
        if(request==null || worker==null || registriesForRecovery==null || actualCargo==null)return false;
        for(ItemStack s:actualCargo)if(s==null)return false;
        CompoundTag t=new CompoundTag();t.putUUID("request",request);if(owner!=null)t.putUUID("owner",owner);t.putUUID("worker",worker);
        t.put("actual_cargo",stacks(actualCargo,registriesForRecovery));salvage.add(t);setDirty();return true;
    }
    public List<ItemStack> inputs(Entry e) { return valid(e) ? copy(e.inputs) : List.of(); }
    public List<ItemStack> products(Entry e) { return valid(e) ? copy(e.products) : List.of(); }
    private boolean valid(Entry e) { return e != null && !invalidRoot && records.get(e.request)==e && !blocked.contains(e.request) && e.state!=State.QUARANTINED; }
    public Entry create(UUID request, UUID owner, BlockPos console, UUID identity, ItemStack target, long count, List<ItemStack> offer) {
        Objects.requireNonNull(request); Objects.requireNonNull(owner); Objects.requireNonNull(console); Objects.requireNonNull(identity);
        if (invalidRoot || records.containsKey(request) || blocked.contains(request) || records.size()+quarantined.size()>=MAX_RECORDS
                || target==null || target.isEmpty() || FluidSamples.liquid(target) || count<1 || count>WorkConsoleBlockEntity.MAX_COUNT
                || offer==null || offer.isEmpty() || offer.size()>MAX_OFFER_STACKS) return null;
        for (ItemStack s:offer) if(s==null || s.isEmpty() || ChemicalSamples.is(s) || s.getCount()>s.getMaxStackSize()
                || FluidSamples.is(s) && FluidSamples.amount(s)<1) return null;
        Entry e=new Entry(request,owner,console,identity,target,count,offer); e.inputs.addAll(copy(offer));
        records.put(request,e); setDirty(); return e;
    }
    public boolean matches(Entry e, UUID actor, ItemStack target, long count, List<ItemStack> offer, BlockPos console, UUID identity) {
        if(!valid(e) || !e.owner.equals(actor) || !e.console.equals(console) || !e.consoleIdentity.equals(identity)
                || e.count!=count || !ItemStack.isSameItemSameComponents(e.target,target) || offer.size()!=e.originalOffer.size()) return false;
        for(int i=0;i<offer.size();i++) if(!ItemStack.matches(offer.get(i),e.originalOffer.get(i))) return false;
        return true;
    }
    public boolean discardPreparing(UUID request) {
        Entry e=find(request); if(!valid(e) || e.state!=State.PREPARING || e.worker!=null) return false;
        records.remove(request); setDirty(); return true;
    }
    public void bind(Entry e, int orderId, UUID worker) {
        if(!valid(e) || e.state!=State.PREPARING || orderId<1 || worker==null) throw new IllegalStateException("Invalid escrow binding");
        e.orderId=orderId; e.worker=worker; e.state=State.RUNNING; setDirty();
    }
    private static boolean terminal(Entry e) { return e.outcome==AndroidOrders.State.COMPLETED || e.outcome==AndroidOrders.State.CANCELLED || e.outcome==AndroidOrders.State.FAILED; }
    public void markTerminal(Entry e, AndroidOrders.State outcome, long delivered) {
        if(!valid(e)) return;
        if(outcome!=AndroidOrders.State.COMPLETED && outcome!=AndroidOrders.State.CANCELLED && outcome!=AndroidOrders.State.FAILED) throw new IllegalArgumentException("Nonterminal outcome");
        if(terminal(e)) return;
        e.outcome=outcome; e.delivered=Math.max(0,delivered);
        e.state=e.worker!=null ? State.RETURNING : outcome==AndroidOrders.State.COMPLETED ? State.COMPLETED : State.RECOVERABLE; setDirty();
    }
    public void workerReturned(Entry e, UUID worker) {
        if(!valid(e) || !Objects.equals(e.worker,worker) || worker==null) return;
        e.worker=null;
        if(terminal(e)) e.state=e.outcome==AndroidOrders.State.COMPLETED ? State.COMPLETED : State.RECOVERABLE;
        setDirty();
    }
    /** Caller must remove exactly the accepted amount from actual cargo. No fixed-capacity loss. */
    public ItemStack returnCargo(Entry e, ItemStack stack) {
        if(!valid(e)) return stack.copy(); append(e.inputs,stack); setDirty(); return ItemStack.EMPTY;
    }
    private static void append(List<ItemStack> inventory, ItemStack offered) {
        if(FluidSamples.is(offered)) { appendFluid(inventory, FluidSamples.fluid(offered)); return; }
        ItemStack rest=offered.copy();
        for(ItemStack held:inventory) if(!held.isEmpty() && ItemStack.isSameItemSameComponents(held,rest)) {
            int n=Math.min(rest.getCount(),Math.max(0,held.getMaxStackSize()-held.getCount())); held.grow(n); rest.shrink(n); if(rest.isEmpty())return;
        }
        for(int i=0;i<inventory.size() && !rest.isEmpty();i++) if(inventory.get(i).isEmpty()) {
            int n=Math.min(rest.getCount(),rest.getMaxStackSize()); inventory.set(i,rest.copyWithCount(n)); rest.shrink(n);
        }
        while(!rest.isEmpty()) { int n=Math.min(rest.getCount(),rest.getMaxStackSize()); inventory.add(rest.copyWithCount(n)); rest.shrink(n); }
    }
    public ItemStack claim(UUID owner, UUID request, boolean products, int slot, int amount) {
        Entry e=find(request);
        if(!valid(e) || !e.owner.equals(owner) || (!products && (!terminal(e) || e.worker!=null))) return ItemStack.EMPTY;
        return extract(products?e.products:e.inputs,slot,amount,false);
    }
    public boolean acknowledge(UUID owner, UUID request) {
        Entry e=find(request); if(!valid(e) || !e.owner.equals(owner) || !terminal(e) || e.worker!=null
                || e.inputs.stream().anyMatch(s->!s.isEmpty()) || e.products.stream().anyMatch(s->!s.isEmpty())) return false;
        records.remove(request); setDirty(); return true;
    }
    /** Fluids are kept as one sample per fluid; its amount (mB) grows and shrinks. */
    private static void appendFluid(List<ItemStack> inventory, FluidStack fluid) {
        if(fluid.isEmpty()) return;
        for(int i=0;i<inventory.size();i++){ var f=FluidSamples.fluid(inventory.get(i));
            if(!f.isEmpty() && FluidStack.isSameFluidSameComponents(f,fluid)){ inventory.set(i,FluidSamples.of(f.copyWithAmount(f.getAmount()+fluid.getAmount()))); return; } }
        for(int i=0;i<inventory.size();i++) if(inventory.get(i).isEmpty()){ inventory.set(i,FluidSamples.of(fluid.copy())); return; }
        inventory.add(FluidSamples.of(fluid.copy()));
    }
    private ItemStack extract(List<ItemStack> inv,int slot,int amount,boolean simulate) {
        if(slot<0 || slot>=inv.size() || amount<=0)return ItemStack.EMPTY;
        ItemStack held=inv.get(slot); if(held.isEmpty())return ItemStack.EMPTY;
        if(FluidSamples.is(held)) {   // amount = mB
            var f=FluidSamples.fluid(held); int take=Math.min(amount,f.getAmount());
            if(!simulate){ inv.set(slot,take>=f.getAmount()?ItemStack.EMPTY:FluidSamples.of(f.copyWithAmount(f.getAmount()-take))); setDirty(); }
            return FluidSamples.of(f.copyWithAmount(take));
        }
        ItemStack result=held.copyWithCount(Math.min(amount,held.getCount()));
        if(!simulate){held.shrink(result.getCount());setDirty();} return result;
    }
    /**
     * Fluids of a request's custody (inputs, or products) as a fluid handler: workers drain inputs into their tanks
     * and return leftovers; nothing else can reach it.
     */
    public IFluidHandler fluidHandler(Entry e, boolean products) {
        return new IFluidHandler() {
            private List<ItemStack> inv(){return products?e.products:e.inputs;}
            private List<Integer> tanks(){List<Integer> out=new ArrayList<>(); if(!valid(e))return out;
                for(int i=0;i<inv().size();i++) if(FluidSamples.is(inv().get(i)))out.add(i); return out;}
            public int getTanks(){return tanks().size()+1;}
            public FluidStack getFluidInTank(int tank){var t=tanks(); return tank<t.size()?FluidSamples.fluid(inv().get(t.get(tank))):FluidStack.EMPTY;}
            public int getTankCapacity(int tank){return Integer.MAX_VALUE;}
            public boolean isFluidValid(int tank,FluidStack stack){return valid(e);}
            public int fill(FluidStack resource,FluidAction action){
                if(!valid(e) || resource.isEmpty())return 0;
                if(action.execute()){appendFluid(inv(),resource.copy());setDirty();}
                return resource.getAmount();
            }
            public FluidStack drain(FluidStack resource,FluidAction action){
                if(!valid(e) || resource.isEmpty() || !products && e.state!=State.RUNNING)return FluidStack.EMPTY;
                for(int i:tanks()){var f=FluidSamples.fluid(inv().get(i));
                    if(FluidStack.isSameFluidSameComponents(f,resource)){
                        int take=Math.min(resource.getAmount(),f.getAmount());
                        if(action.execute()){inv().set(i,take>=f.getAmount()?ItemStack.EMPTY:FluidSamples.of(f.copyWithAmount(f.getAmount()-take)));setDirty();}
                        return f.copyWithAmount(take);}}
                return FluidStack.EMPTY;
            }
            public FluidStack drain(int maxDrain,FluidAction action){
                for(int i:tanks()){var f=FluidSamples.fluid(inv().get(i)); if(!f.isEmpty())return drain(f.copyWithAmount(maxDrain),action);}
                return FluidStack.EMPTY;
            }
        };
    }
    public IItemHandler inputHandler(Entry e) { return handler(e,false); }
    public IItemHandler productHandler(Entry e) { return handler(e,true); }
    private IItemHandler handler(Entry e,boolean product) {
        return new IItemHandler() {
            private List<ItemStack> inv(){return product?e.products:e.inputs;}
            public int getSlots(){return valid(e)?inv().size()+(product?1:0):0;}
            public ItemStack getStackInSlot(int slot){return valid(e)&&slot>=0&&slot<inv().size()?inv().get(slot).copy():ItemStack.EMPTY;}
            public int getSlotLimit(int slot){return 64;}
            public boolean isItemValid(int slot,ItemStack s){return product && valid(e) && slot>=0 && slot<getSlots();}
            public ItemStack insertItem(int slot,ItemStack s,boolean simulate){
                if(!isItemValid(slot,s))return s.copy();
                if(s.isEmpty())return ItemStack.EMPTY;
                ItemStack held=slot<e.products.size()?e.products.get(slot):ItemStack.EMPTY;
                if(!held.isEmpty()&&!ItemStack.isSameItemSameComponents(held,s))return s.copy();
                int accepted=Math.min(s.getCount(),Math.max(0,Math.min(64,s.getMaxStackSize())-held.getCount()));
                if(!simulate&&accepted>0){
                    if(slot==e.products.size())e.products.add(s.copyWithCount(accepted));
                    else if(held.isEmpty())e.products.set(slot,s.copyWithCount(accepted));else held.grow(accepted);
                    setDirty();
                }
                return s.copyWithCount(s.getCount()-accepted);
            }
            public ItemStack extractItem(int slot,int amount,boolean simulate){
                return !product && valid(e) && e.state==State.RUNNING ? extract(e.inputs,slot,amount,simulate):ItemStack.EMPTY;
            }
        };
    }
    private static ListTag stacks(List<ItemStack> inv,HolderLookup.Provider registries) {
        ListTag out=new ListTag(); for(ItemStack s:inv)out.add(s.isEmpty()?new CompoundTag():s.save(registries));return out;
    }
    private static List<ItemStack> readStacks(CompoundTag t,String key,HolderLookup.Provider registries,boolean allowEmpty) {
        if(!t.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Missing inventory");
        ListTag list=t.getList(key,Tag.TAG_COMPOUND);
        if(list.size()!=((ListTag)t.get(key)).size())throw new IllegalArgumentException("Invalid inventory tags");
        List<ItemStack> out=new ArrayList<>();
        for(Tag raw:list){CompoundTag st=(CompoundTag)raw; ItemStack s=st.isEmpty()?ItemStack.EMPTY:ItemStack.parseOptional(registries,st);
            if((s.isEmpty() && (!allowEmpty || !st.isEmpty())) || !s.isEmpty() && (s.getCount()<1 || s.getCount()>s.getMaxStackSize()))throw new IllegalArgumentException("Invalid stack");out.add(s);}
        return out;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        if(invalidRoot){CompoundTag out=rawRoot.copy();out.put("recovery_salvage_extra_v1",salvage.copy());return out;}
        tag.put("recovery_salvage_v1",salvage.copy());
        tag.putInt("schema",1); ListTag list=new ListTag();
        for(Entry e:records.values()){
            CompoundTag t=new CompoundTag();t.putUUID("request",e.request);t.putUUID("owner",e.owner);t.putUUID("identity",e.consoleIdentity);
            t.put("console",NbtUtils.writeBlockPos(e.console));t.put("target",e.target.save(registries));t.putLong("count",e.count);
            t.putInt("order",e.orderId);t.putString("state",e.state.name());if(e.worker!=null)t.putUUID("worker",e.worker);
            if(e.requestedWorker!=null)t.putUUID("requested_worker",e.requestedWorker);
            if(e.outcome!=null)t.putString("outcome",e.outcome.name());t.putLong("delivered",e.delivered);
            t.put("offer",stacks(e.originalOffer,registries));t.put("inputs",stacks(e.inputs,registries));t.put("products",stacks(e.products,registries));list.add(t);
        }
        for(Tag raw:quarantined)list.add(raw.copy());tag.put("records",list);return tag;
    }
    public static AndroidEscrowData load(CompoundTag tag,HolderLookup.Provider registries) {
        AndroidEscrowData data=new AndroidEscrowData();data.registriesForRecovery=registries;
        if(tag.contains("recovery_salvage_extra_v1",Tag.TAG_LIST)) for(Tag raw:tag.getList("recovery_salvage_extra_v1",Tag.TAG_COMPOUND))data.salvage.add(raw.copy());

        if(tag.getInt("schema")!=1 || !tag.contains("records",Tag.TAG_LIST) || ((ListTag)tag.get("records")).getElementType()!=Tag.TAG_COMPOUND && !((ListTag)tag.get("records")).isEmpty()) {
            data.invalidRoot=true;data.rawRoot=tag.copy();return data;
        }
        if(tag.contains("recovery_salvage_v1",Tag.TAG_LIST)) for(Tag raw:tag.getList("recovery_salvage_v1",Tag.TAG_COMPOUND))data.salvage.add(raw.copy());
        for(Tag raw:tag.getList("records",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)raw; UUID id=t.hasUUID("request")?t.getUUID("request"):null;
            try {
                if(id==null || data.blocked.contains(id) || !t.hasUUID("owner") || !t.hasUUID("identity"))throw new IllegalArgumentException();
                if(data.records.containsKey(id)){
                    // Preserve both conflicting records and disable access, rather than pick a winner.
                    Entry old=data.records.remove(id); CompoundTag previous=new CompoundTag();
                    AndroidEscrowData temp=new AndroidEscrowData();temp.records.put(id,old);previous=(CompoundTag)temp.save(new CompoundTag(),registries).getList("records",Tag.TAG_COMPOUND).get(0);
                    data.quarantined.add(previous);throw new IllegalArgumentException();
                }
                BlockPos pos=NbtUtils.readBlockPos(t,"console").orElseThrow();ItemStack target=ItemStack.parseOptional(registries,t.getCompound("target"));
                long count=t.getLong("count");List<ItemStack> offer=readStacks(t,"offer",registries,false);
                if(target.isEmpty() || FluidSamples.liquid(target) || count<1 || count>WorkConsoleBlockEntity.MAX_COUNT || offer.isEmpty() || offer.size()>MAX_OFFER_STACKS || offer.stream().anyMatch(ChemicalSamples::is))throw new IllegalArgumentException();
                Entry e=new Entry(id,t.getUUID("owner"),pos,t.getUUID("identity"),target,count,offer);
                e.inputs.addAll(readStacks(t,"inputs",registries,true));e.products.addAll(readStacks(t,"products",registries,true));
                e.state=State.valueOf(t.getString("state"));e.orderId=t.getInt("order");
                if(t.contains("worker")&&!t.hasUUID("worker"))throw new IllegalArgumentException();e.worker=t.hasUUID("worker")?t.getUUID("worker"):null;
                if(t.contains("requested_worker")&&!t.hasUUID("requested_worker"))throw new IllegalArgumentException();
                e.requestedWorker=t.hasUUID("requested_worker")?t.getUUID("requested_worker"):null;
                if(t.contains("outcome"))e.outcome=AndroidOrders.State.valueOf(t.getString("outcome"));e.delivered=t.getLong("delivered");
                if(e.state==State.QUARANTINED || e.delivered<0 || e.outcome!=AndroidOrders.State.RUNNING&&!terminal(e)
                        || e.state==State.RUNNING&&(e.orderId<1 || e.worker==null || terminal(e))
                        || e.state==State.RETURNING&&(!terminal(e)||e.worker==null)
                        || (e.state==State.RECOVERABLE||e.state==State.COMPLETED)&&(!terminal(e)||e.worker!=null)
                        || e.state==State.COMPLETED&&e.outcome!=AndroidOrders.State.COMPLETED
                        || e.state==State.RECOVERABLE&&e.outcome==AndroidOrders.State.COMPLETED
                        || e.state==State.PREPARING&&(e.worker!=null||terminal(e)))throw new IllegalArgumentException();
                data.records.put(id,e);
                        }catch(RuntimeException ex){
                if(id!=null && data.records.containsKey(id)) {
                    Entry prior=data.records.remove(id);AndroidEscrowData temporary=new AndroidEscrowData();temporary.records.put(id,prior);
                    data.quarantined.add(temporary.save(new CompoundTag(),registries).getList("records",Tag.TAG_COMPOUND).get(0).copy());
                }
                data.quarantined.add(raw.copy());if(id!=null)data.blocked.add(id);
            }
        }
        return data;
    }
}
