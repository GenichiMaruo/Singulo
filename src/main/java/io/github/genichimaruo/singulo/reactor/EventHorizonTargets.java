package io.github.genichimaruo.singulo.reactor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;

/** ブラックホールに引き寄せられ、飲み込まれるもの。 */
final class EventHorizonTargets {
    private EventHorizonTargets() {}

    /** 地平線の向こうから戻ってきた記録片の印（戻った時刻）。しばらくは引き寄せない。 */
    static final String RETURNED = "singulo_returned";
    static final long RETURN_GRACE = 600;

    static boolean isReturnedRecord(net.minecraft.world.entity.item.ItemEntity item) {
        return item.getPersistentData().contains(RETURNED);
    }

    /** クリエイティブ・スペクテイターのプレイヤーと、壁に掛けたもの（額縁・絵画）は除く。 */
    static boolean affected(Entity e) {
        if (e.isSpectator() || e.isRemoved()) {
            return false;
        }
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return false;
        }
        if (e instanceof net.minecraft.world.entity.item.ItemEntity item && isReturnedRecord(item)
                && e.level().getGameTime() - item.getPersistentData().getLong(RETURNED) < RETURN_GRACE) {
            return false;
        }
        return !(e instanceof HangingEntity);
    }
}
