package io.github.genichimaruo.singulo.reactor;

import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 事象の地平線に触れたものを必ず消す。どんなに体力・耐性・無敵があっても助からない。
 * <ol>
 *   <li>専用のダメージ（singulo:event_horizon）を上限なしで与える。無敵・防具・耐性・効果・エンチャント・盾・
 *       不死のトーテムをすべて無視する（ダメージタイプのタグ）</li>
 *   <li>それでも生きていれば（ほかの MOD がダメージや死を取り消したなど）、体力を 0 にして死なせる</li>
 *   <li>それでも残っていれば、世界から取り除く</li>
 * </ol>
 * 生き物でないもの（落ちているアイテム・矢・トロッコなど）は飲み込まれて消える。
 * クリエイティブとスペクテイターのプレイヤーは対象にしない（呼び出し側で外す）。
 */
public final class EventHorizon {
    private EventHorizon() {}

    public static void consume(ServerLevel level, Entity entity) {
        if (entity.isRemoved()) {
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            if (!(entity instanceof Player)) {
                entity.discard();
            }
            return;
        }
        if (living.isDeadOrDying()) {
            return;
        }
        DamageSource source = SinguloDamageTypes.eventHorizon(level);
        living.invulnerableTime = 0;
        living.hurt(source, Float.MAX_VALUE);
        if (!living.isDeadOrDying()) {
            living.setHealth(0);
            living.die(source);
        }
        if (!living.isDeadOrDying() && !living.isRemoved()) {
            living.discard();
        }
    }
}
