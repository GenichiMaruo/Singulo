package io.github.genichimaruo.singulo.ruin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * ボスの無敵時間をプレイヤーごとに分ける。
 * <p>
 * バニラでは、攻撃を受けると 20 tick の無敵時間が始まり、前半の 10 tick は同じ強さ以下の攻撃を受け付けない。
 * これは全員で共有なので、大勢で殴っても約 0.5 秒に1撃しか通らず、人数で体力を増やすと協力するほど長期戦になる。
 * ここでは、プレイヤーの攻撃についてだけ、そのプレイヤーが最後に当てた時刻と強さで無敵時間を判定する
 * （ひとりひとりにはバニラと同じ間隔がかかり、他の人の攻撃には邪魔されない）。プレイヤー以外の攻撃は今までどおり。
 */
final class HurtWindows {
    /** 同じ強さ以下の攻撃を受け付けない間（バニラの無敵時間 20 tick の前半）。 */
    static final int WINDOW = 10;
    static final int FULL = WINDOW * 2;

    private record Hit(long start, float amount) {}

    private final Map<UUID, Hit> hits = new HashMap<>();
    /** ボス自身の時計（ボスの tick ごとに進む。バニラの無敵時間もボスの tick で減るのと同じ）。 */
    private long clock;

    /** ボスの tick ごとに呼ぶ。 */
    void tick() {
        clock++;
    }

    /** source がいま、そのプレイヤーの「受け付けない間」の中か（プレイヤー以外は共有の無敵時間で判定）。 */
    boolean within(LivingEntity self, DamageSource source) {
        if (source.getEntity() instanceof Player p && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Hit h = hits.get(p.getUUID());
            return h != null && clock - h.start() < WINDOW;
        }
        return self.invulnerableTime > WINDOW;
    }

    /**
     * プレイヤーの攻撃なら、共有の無敵時間をそのプレイヤーのものに入れ替えてから受ける（superHurt はバニラの処理）。
     * lastHurt / setLastHurt は LivingEntity の「直前に受けた強さ」（protected なので呼び出し側から渡す）。
     */
    boolean hurt(LivingEntity self, DamageSource source, float amount, BiPredicate<DamageSource, Float> superHurt,
                 Supplier<Float> lastHurt, Consumer<Float> setLastHurt) {
        if (!(source.getEntity() instanceof Player p) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return superHurt.test(source, amount);
        }
        long now = clock;
        Hit h = hits.get(p.getUUID());
        int sharedInvulnerable = self.invulnerableTime;
        float sharedLast = lastHurt.get();
        if (h != null && now - h.start() < FULL) {
            self.invulnerableTime = (int) (FULL - (now - h.start()));
            setLastHurt.accept(h.amount());
        } else {
            self.invulnerableTime = 0;
        }
        boolean hurt = superHurt.test(source, amount);
        if (hurt) {
            // 受け付けない間に強い攻撃で上書きしたときは、間の始まりはそのまま（バニラと同じ）
            long start = h != null && now - h.start() < WINDOW ? h.start() : now;
            hits.put(p.getUUID(), new Hit(start, lastHurt.get()));
        } else {
            self.invulnerableTime = sharedInvulnerable;
            setLastHurt.accept(sharedLast);
        }
        if (hits.size() > 16) {
            hits.values().removeIf(x -> now - x.start() >= FULL);
        }
        return hurt;
    }
}
