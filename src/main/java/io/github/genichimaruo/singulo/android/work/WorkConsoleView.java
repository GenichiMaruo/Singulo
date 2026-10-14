package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 作業指令台の画面に送る様子（NBT にまとめる）。注文できる物（在庫と、作れるか）、進行中の注文、つながり、
 * 使えるアンドロイド、最後の知らせ、計算の結果。
 */
public final class WorkConsoleView {
    /** 送る注文できる物の上限。 */
    static final int MAX_ENTRIES = 600;

    private WorkConsoleView() {}

    public static CompoundTag build(WorkConsoleBlockEntity c, ServerPlayer player, @Nullable CompoundTag plan) {
        HolderLookup.Provider reg = player.level().registryAccess();
        CompoundTag t = new CompoundTag();
        // 手順モードで並べた手順
        net.minecraft.nbt.ListTag proc = new net.minecraft.nbt.ListTag();
        for (ProcedureStep s : c.procSteps()) {
            proc.add(s.save(reg));
        }
        t.put("proc", proc);
        // 注文できる物: レシピの完成品（作れる）と、在庫
        List<ItemCount> stock = c.available();
        List<ItemCount> entries = new ArrayList<>();
        List<ItemStack> craftable = new ArrayList<>();
        for (RecipeRackBlockEntity.Entry e : c.recipes()) {
            ItemStack out = e.recipe().output();
            if (craftable.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, out))) {
                craftable.add(out.copyWithCount(1));
                entries.add(new ItemCount(out, ItemCount.get(stock, out)));
            }
        }
        List<ItemCount> rest = new ArrayList<>();
        for (ItemCount s : stock) {
            if (s.count > 0 && craftable.stream().noneMatch(x -> ItemStack.isSameItemSameComponents(x, s.item))) {
                rest.add(s);
            }
        }
        rest.sort(Comparator.comparing((ItemCount s) -> s.item.getHoverName().getString()));
        entries.addAll(rest);
        ListTag es = new ListTag();
        for (int i = 0; i < Math.min(MAX_ENTRIES, entries.size()); i++) {
            ItemCount e = entries.get(i);
            CompoundTag et = e.save(reg);
            et.putBoolean("craft", i < craftable.size());
            es.add(et);
        }
        t.put("entries", es);
        // 進行中の注文
        ListTag os = new ListTag();
        for (WorkOrder o : c.orders()) {
            CompoundTag ot = new CompoundTag();
            ot.putInt("id", o.id);
            ot.put("target", o.target.save(reg));
            ot.putLong("count", o.count);
            ot.putInt("progress", o.progress());
            ot.putInt("status", o.status);
            ot.putInt("workers", o.workers.size());
            // 進み具合の詳しい様子: 工程ごとの回数と、受け持っているアンドロイドの今していること
            ListTag st = new ListTag();
            for (WorkOrder.StepState s : o.steps) {
                CompoundTag x = new CompoundTag();
                x.put("item", s.recipe.output().save(reg));
                x.putLong("done", s.done);
                x.putLong("total", s.total);
                x.putLong("doing", s.inProgress);
                st.add(x);
            }
            ot.put("steps", st);
            ot.putLong("delivered", o.delivered);
            ot.putBoolean("to_stock", o.toStock);
            ListTag wt = new ListTag();
            for (java.util.UUID id : o.workers) {
                CompoundTag w = new CompoundTag();
                if (c.getLevel() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(id) instanceof RuinAndroid a && a.isAlive()) {
                    w.putString("name", Component.Serializer.toJson(a.nickname(), reg));
                    w.putInt("activity", a.workOrder() == o.id ? a.activity() : RuinAndroid.ACT_NONE);
                    w.putInt("accent", a.accent());
                } else {
                    w.putString("name", Component.Serializer.toJson(Component.translatable("gui.singulo.work_console.worker.away"), reg));
                    w.putInt("activity", -1);
                }
                wt.add(w);
            }
            ot.put("worker_list", wt);
            os.add(ot);
        }
        t.put("orders", os);
        // つながり
        ListTag ls = new ListTag();
        for (BlockPos p : c.links()) {
            CompoundTag lt = new CompoundTag();
            lt.putLong("pos", p.asLong());
            boolean ok = player.level().getBlockEntity(p) instanceof WorkLinkedBlockEntity b && c.getBlockPos().equals(b.console());
            lt.putInt("kind", ok ? ((WorkLinkedBlockEntity) player.level().getBlockEntity(p)).linkKind().ordinal() : -1);
            ls.add(lt);
        }
        t.put("links", ls);
        // 納品先（指令台そのものと、つながっている納品口）
        ListTag ds = new ListTag();
        for (BlockPos p : c.deliveryTargets()) {
            CompoundTag dt = new CompoundTag();
            dt.putLong("pos", p.asLong());
            Component name;
            if (p.equals(c.getBlockPos())) {
                name = Component.translatable("gui.singulo.work_console.delivery.console");
            } else {
                DeliveryPortBlockEntity port = c.deliveryPort(p);
                name = port != null && !port.name().isEmpty() ? Component.literal(port.name())
                        : Component.translatable("gui.singulo.work_console.delivery.port", p.getX(), p.getY(), p.getZ());
            }
            dt.putString("name", Component.Serializer.toJson(name, reg));
            ds.add(dt);
        }
        t.put("deliveries", ds);
        // 使えるアンドロイド
        ListTag as = new ListTag();
        for (RuinAndroid a : c.candidates(player)) {
            CompoundTag at = new CompoundTag();
            at.putUUID("id", a.getUUID());
            at.putString("name", Component.Serializer.toJson(a.nickname(), reg));
            at.putBoolean("busy", c.busy(a));
            at.putInt("slots", a.isWorking() ? a.carryCapacity() : a.freeCarry());
            at.putInt("accent", a.accent());
            at.putBoolean("miner", a.canMine());
            at.putBoolean("feller", a.canFell());
            as.add(at);
        }
        t.put("androids", as);
        // 登録した注文
        ListTag ss = new ListTag();
        List<ItemCount> inStock = c.standing().isEmpty() ? List.of() : c.stock();
        for (StandingOrder s : c.standing()) {
            CompoundTag st = new CompoundTag();
            st.putInt("id", s.id);
            st.put("item", s.target.save(reg));
            st.putLong("count", s.count);
            st.putInt("trigger", s.trigger);
            st.putInt("state", s.state);
            st.putLong("stock", ItemCount.get(inStock, s.target));
            if (s.reason != null) {
                st.putString("reason", Component.Serializer.toJson(s.reason, reg));
            }
            ss.add(st);
        }
        t.put("standing", ss);
        t.putBoolean("powered", c.powered());
        if (c.message() != null) {
            t.putString("message", Component.Serializer.toJson(c.message(), reg));
        }
        if (plan != null) {
            t.put("plan", plan);
        }
        return t;
    }

    /** 計算の結果（画面に出す）。 */
    public static CompoundTag plan(WorkConsoleBlockEntity c, WorkPlanner.Plan plan, HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        t.put("target", plan.target.save(reg));
        t.putLong("count", plan.count);
        t.putBoolean("ok", plan.ok());
        t.putBoolean("loop", plan.loop);
        long crafts = 0;
        boolean mining = false, felling = false;
        for (WorkPlanner.Step s : plan.steps) {
            crafts += s.crafts();
            mining |= s.entry().recipe().needsMining();
            felling |= s.entry().recipe().needsFelling();
        }
        t.putBoolean("mining", mining);
        t.putBoolean("felling", felling);              // 採掘の工程がある: 採掘アップグレードを積んだアンドロイドだけ選べる
        t.putInt("steps", plan.steps.size());
        t.putLong("crafts", crafts);
        List<ItemCount> stock = c.available();
        ListTag ms = new ListTag();
        for (ItemCount m : plan.materials()) {
            CompoundTag mt = m.save(reg);
            mt.putLong("have", ItemCount.get(stock, m.item));
            mt.putLong("missing", ItemCount.get(plan.missing, m.item));
            ms.add(mt);
        }
        t.put("materials", ms);
        // 作る物（中間の素材と完成品）: 作る数と、余り（使い切れずに素材置き場へ戻る数）
        List<ItemCount> made = new ArrayList<>();
        List<ItemCount> needed = new ArrayList<>();
        for (WorkPlanner.Step s : plan.steps) {
            ItemStack out = s.entry().recipe().output();
            ItemCount.add(made, out, (long) out.getCount() * s.crafts());
            ItemCount.add(needed, out, s.needed());
        }
        ListTag cs = new ListTag();
        for (ItemCount m : made) {
            CompoundTag ct = m.save(reg);
            ct.putLong("surplus", Math.max(0, m.count - ItemCount.get(needed, m.item)));
            cs.add(ct);
        }
        t.put("crafted", cs);
        return t;
    }
}
