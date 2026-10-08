package io.github.genichimaruo.singulo.reactor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;

/** ブラックホールに引き寄せられ、飲み込まれるもの。 */
final class EventHorizonTargets {
    private EventHorizonTargets() {}

    /** クリエイティブ・スペクテイターのプレイヤーと、壁に掛けたもの（額縁・絵画）は除く。 */
    static boolean affected(Entity e) {
        if (e.isSpectator() || e.isRemoved()) {
            return false;
        }
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return false;
        }
        return !(e instanceof HangingEntity);
    }
}
