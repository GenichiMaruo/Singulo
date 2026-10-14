package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * アンドロイドに渡す1つの作業。作業指令台が作り、アンドロイドが進める。
 * <ul>
 * <li>CRAFT: 素材をまとめて取り（fetch）、batch の工程を順に、それぞれの作業場所で作る。素材を作る工程と使う工程を
 * 一度に受け持つので、途中で素材置き場へ戻らない。作り終えたら、完成品はそのまま納品先へ届ける。</li>
 * <li>DELIVER: 完成品を amount 個取り、納品先へ届ける。</li>
 * <li>DEPOSIT: 持っている物をすべて素材置き場へ戻す（前の作業の残りや、取り消したときの物）。</li>
 * <li>WAIT: 今できる作業がない（ほかの工程の終わりを待つ）。</li>
 * <li>EXIT: 注文が終わった・取り消された（持ち物を戻してから、作業をやめる）。</li>
 * </ul>
 * carried が true の作業は、素材（届ける物）をもう持っているので、取りに行かない（作った物で続けて作る・届ける）。
 */
public record WorkJob(Type type, int orderId, int jobId, long amount, List<ItemCount> fetch, List<Batch> batch, boolean carried) {
    public enum Type { CRAFT, DELIVER, DEPOSIT, WAIT, EXIT }

    /** 1つの工程を crafts 回（作業場所 location で）。 */
    public record Batch(int step, long crafts, BlockPos location, RecipeData recipe) {}

    public static WorkJob simple(Type type, int orderId) {
        return new WorkJob(type, orderId, -1, 0, List.of(), List.of(), false);
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
