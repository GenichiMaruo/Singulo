package io.github.genichimaruo.singulo.android.work;

import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * アンドロイドに渡す1つの作業。作業指令台が作り、アンドロイドが進める。
 * <ul>
 * <li>CRAFT: 素材をまとめて取り（fetch）、batch の工程を順に、それぞれの作業場所で作る。素材を作る工程と使う工程を
 * 一度に受け持つので、途中で素材置き場へ戻らない。作り終えたら、完成品はそのまま納品先へ届ける。</li>
 * <li>DELIVER: 完成品を amount 個取り、納品先へ届ける。</li>
 * <li>COLLECT: 加工を待っている装置（batch の1工程。素材はもう入れてある）から、できた物を amount 個取り出す。</li>
 * <li>DEPOSIT: 持っている物をすべて素材置き場へ戻す（前の作業の残りや、取り消したときの物）。</li>
 * <li>WAIT: 今できる作業がない（ほかの工程の終わりを待つ）。</li>
 * <li>EXIT: 注文が終わった・取り消された（持ち物を戻してから、作業をやめる）。</li>
 * </ul>
 * carried が true の作業は、素材（届ける物）をもう持っているので、取りに行かない（作った物で続けて作る・届ける）。
 */
public record WorkJob(Type type, int orderId, int jobId, long amount, List<ItemCount> fetch, List<Batch> batch, boolean carried) {
    public enum Type { CRAFT, DELIVER, DEPOSIT, WAIT, EXIT, COLLECT }

    /** 1つの工程を crafts 回（作業場所 location で）。 */
    public record Batch(int step, long crafts, BlockPos location, RecipeData recipe) {}

    public static WorkJob simple(Type type, int orderId) {
        return new WorkJob(type, orderId, -1, 0, List.of(), List.of(), false);
    }

    /** 消費済み素材をもう一度要求しないよう、作業そのものも保存する。 */
    public net.minecraft.nbt.CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("type", type.name());
        tag.putInt("order", orderId);
        tag.putInt("job", jobId);
        tag.putLong("amount", amount);
        tag.putBoolean("carried", carried);
        tag.put("fetch", WorkOrder.saveCounts(fetch, registries));
        var batches = new net.minecraft.nbt.ListTag();
        for (Batch b : batch) {
            var t = new net.minecraft.nbt.CompoundTag();
            t.putInt("step", b.step());
            t.putLong("crafts", b.crafts());
            t.putLong("location", b.location().asLong());
            t.put("recipe", b.recipe().save(registries));
            batches.add(t);
        }
        tag.put("batch", batches);
        return tag;
    }

    @javax.annotation.Nullable
    public static WorkJob load(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (!tag.contains("type")) {
            return null;
        }
        Type type;
        try {
            type = Type.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        var fetch = new java.util.ArrayList<ItemCount>();
        WorkOrder.loadCounts(tag.getList("fetch", net.minecraft.nbt.Tag.TAG_COMPOUND), fetch, registries);
        var batch = new java.util.ArrayList<Batch>();
        for (var element : tag.getList("batch", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var t = (net.minecraft.nbt.CompoundTag) element;
            var recipe = RecipeData.load(t.getCompound("recipe"), registries);
            if (recipe == null || t.getInt("step") < 0 || t.getLong("crafts") <= 0) {
                return null;
            }
            batch.add(new Batch(t.getInt("step"), t.getLong("crafts"), BlockPos.of(t.getLong("location")), recipe));
        }
        return new WorkJob(type, tag.getInt("order"), tag.getInt("job"), Math.max(0, tag.getLong("amount")),
                fetch, batch, tag.getBoolean("carried"));
    }

    /** 作業指令台が覚えておく、渡した作業（だれが、いつまで）。 */
    public static final class Lease {
        public final WorkJob job;
        public UUID worker;
        public long lastBeat;
        /** 分割して納品するときの未納品数。 */
        public long remainingDelivery;

        Lease(WorkJob job, UUID worker, long now) {
            this.job = job;
            this.worker = worker;
            this.lastBeat = now;
            this.remainingDelivery = job.amount();
        }
    }
}
