package io.github.genichimaruo.singulo.android.work;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
        var t=new CompoundTag(); t.store("request", net.minecraft.core.UUIDUtil.CODEC, request); t.store("actor", net.minecraft.core.UUIDUtil.CODEC, actor); t.putInt("order",order);
        t.put("target",io.github.genichimaruo.singulo.compat.NbtCompat.save(target, registries)); t.putLong("count",count); t.putString("state",state.name()); t.putLong("delivered",delivered);
        if(reason!=null)t.putString("reason",reason);
        if(destination!=null)t.put("destination",io.github.genichimaruo.singulo.compat.NbtCompat.writeBlockPos(destination));
        if(materialSource!=null)t.put("material_source",io.github.genichimaruo.singulo.compat.NbtCompat.writeBlockPos(materialSource));
        var w=new ListTag(); for(UUID id:workers)w.add(io.github.genichimaruo.singulo.compat.NbtCompat.createUUID(id)); t.put("workers",w); return t;
    }
    @Nullable static ExternalOrderLedger load(CompoundTag t, HolderLookup.Provider registries) {
        if(t.contains("material_source") && io.github.genichimaruo.singulo.compat.NbtCompat.readBlockPos(t,"material_source").isEmpty()) return null;
        if(!t.read("request", net.minecraft.core.UUIDUtil.CODEC).isPresent()||!t.read("actor", net.minecraft.core.UUIDUtil.CODEC).isPresent()||t.getIntOr("order", 0)<1)return null;
        var item=io.github.genichimaruo.singulo.compat.NbtCompat.parseOptional(registries,t.getCompoundOrEmpty("target")); long count=t.getLongOr("count", 0L);
        if(item.isEmpty()||count<1||count>WorkConsoleBlockEntity.MAX_COUNT)return null;
        var workers=new ArrayList<UUID>();
        for(Tag w:t.getListOrEmpty("workers")) {
            try { workers.add(io.github.genichimaruo.singulo.compat.NbtCompat.loadUUID(w)); } catch(IllegalArgumentException ignored) { return null; }
        }
        var e=new ExternalOrderLedger(t.read("request", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(),t.read("actor", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(),t.getIntOr("order", 0),item,count,workers,io.github.genichimaruo.singulo.compat.NbtCompat.readBlockPos(t,"destination").orElse(null));
        e.materialSource=io.github.genichimaruo.singulo.compat.NbtCompat.readBlockPos(t,"material_source").orElse(null);
        try { e.state=AndroidOrders.State.valueOf(t.getStringOr("state", "")); }
        catch(IllegalArgumentException ex) { e.state=AndroidOrders.State.FAILED; e.reason="invalid_saved_state"; }
        if(e.state!=AndroidOrders.State.RUNNING&&!e.receipt().terminal()) { e.state=AndroidOrders.State.FAILED; e.reason="invalid_saved_state"; }
        e.delivered=Math.max(0,Math.min(count,t.getLongOr("delivered", 0L)));
        if(e.reason==null&&t.contains("reason"))e.reason=t.getStringOr("reason", ""); return e;
    }
}
