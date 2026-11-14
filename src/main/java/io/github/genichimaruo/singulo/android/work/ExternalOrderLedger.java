package io.github.genichimaruo.singulo.android.work;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
/** Console-local persistent receipt, not an inventory or independent scheduler. */
final class ExternalOrderLedger {
    final UUID request, actor;
    final int order;
    final ItemStack target;
    final long count;
    final List<UUID> workers;
    @Nullable final BlockPos destination;
    @Nullable BlockPos materialSource;
    AndroidOrders.State state = AndroidOrders.State.RUNNING;
    long delivered;
    @Nullable String reason;
    ExternalOrderLedger(UUID request, UUID actor, int order, ItemStack target, long count, List<UUID> workers, @Nullable BlockPos destination) {
        this.request=request; this.actor=actor; this.order=order; this.target=target.copyWithCount(1); this.count=count;
        this.workers=workers.stream().distinct().sorted().toList(); this.destination=destination==null?null:destination.immutable();
    }
    boolean matches(ItemStack item, long amount, List<UUID> selected, @Nullable BlockPos dest) {
        return count==amount && ItemStack.isSameItemSameComponents(target,item)
                && workers.equals(selected.stream().distinct().sorted().toList()) && Objects.equals(destination,dest);
    }
    boolean matches(ItemStack item, long amount, List<UUID> selected, @Nullable BlockPos dest, @Nullable BlockPos source) {
        return matches(item, amount, selected, dest) && Objects.equals(materialSource, source);
    }
    AndroidOrders.Receipt receipt() { return new AndroidOrders.Receipt(request,state,order,count,delivered,reason); }
    CompoundTag save(HolderLookup.Provider registries) {
        var t=new CompoundTag(); t.putUUID("request",request); t.putUUID("actor",actor); t.putInt("order",order);
        t.put("target",target.save(registries)); t.putLong("count",count); t.putString("state",state.name()); t.putLong("delivered",delivered);
        if(reason!=null)t.putString("reason",reason);
        if(destination!=null)t.put("destination",NbtUtils.writeBlockPos(destination));
        if(materialSource!=null)t.put("material_source",NbtUtils.writeBlockPos(materialSource));
        var w=new ListTag(); for(UUID id:workers)w.add(NbtUtils.createUUID(id)); t.put("workers",w); return t;
    }
    @Nullable static ExternalOrderLedger load(CompoundTag t, HolderLookup.Provider registries) {
        if(t.contains("material_source") && NbtUtils.readBlockPos(t,"material_source").isEmpty()) return null;
        if(!t.hasUUID("request")||!t.hasUUID("actor")||t.getInt("order")<1)return null;
        var item=ItemStack.parseOptional(registries,t.getCompound("target")); long count=t.getLong("count");
        if(item.isEmpty()||count<1||count>WorkConsoleBlockEntity.MAX_COUNT)return null;
        var workers=new ArrayList<UUID>();
        for(Tag w:t.getList("workers",Tag.TAG_INT_ARRAY)) {
            try { workers.add(NbtUtils.loadUUID(w)); } catch(IllegalArgumentException ignored) { return null; }
        }
        var e=new ExternalOrderLedger(t.getUUID("request"),t.getUUID("actor"),t.getInt("order"),item,count,workers,NbtUtils.readBlockPos(t,"destination").orElse(null));
        e.materialSource=NbtUtils.readBlockPos(t,"material_source").orElse(null);
        try { e.state=AndroidOrders.State.valueOf(t.getString("state")); }
        catch(IllegalArgumentException ex) { e.state=AndroidOrders.State.FAILED; e.reason="invalid_saved_state"; }
        if(e.state!=AndroidOrders.State.RUNNING&&!e.receipt().terminal()) { e.state=AndroidOrders.State.FAILED; e.reason="invalid_saved_state"; }
        e.delivered=Math.max(0,Math.min(count,t.getLong("delivered")));
        if(e.reason==null&&t.contains("reason"))e.reason=t.getString("reason"); return e;
    }
}
