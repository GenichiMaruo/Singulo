package io.github.genichimaruo.singulo.reactor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;

/** ブラックホールに引き寄せられ、飲み込まれるもの（実体と、崩されるブロック）。 */
public final class EventHorizonTargets {
    private EventHorizonTargets() {}

    /**
     * ブラックホールが崩して飲み込めるブロックか（炉心の吸い込み・崩壊、小さなブラックホールで共通の決まり）。
     * 空気・壊せないブロック・中身を持つブロック（チェストなど。中身を失わないように）は崩さない。
     * fluids が false なら、液体（水に浸かったブロックを含む）も崩さない。シールドタワーの守りは、使う側で確かめる。
     */
    public static boolean edibleBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                      net.minecraft.world.level.block.state.BlockState state, boolean fluids) {
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0 && level.getBlockEntity(pos) == null
                && (fluids || state.getFluidState().isEmpty());
    }

    /** ボスの落とし物（アイテム・経験値オーブ）の印。ブラックホールに引き寄せられず、飲み込まれない（倒した報酬が失われないように）。 */
    public static final String PROTECTED = "singulo_boss_loot";

    /** 地平線の向こうから戻ってきたクリスタルメモリの印（戻った時刻）。しばらくは引き寄せない。 */
    static final String RETURNED = "singulo_returned";
    static final long RETURN_GRACE = 600;

    static boolean isReturnedRecord(net.minecraft.world.entity.item.ItemEntity item) {
        return item.getPersistentData().contains(RETURNED);
    }

    /** クリエイティブ・スペクテイターのプレイヤーと、壁に掛けたもの（額縁・絵画）は除く。 */
    static boolean affected(Entity e) {
        if (e.isSpectator() || e.isRemoved() || e instanceof InfallingBlock) {
            return false;                                  // 崩れて落ちていくブロックは、自分で炉心へ落ちる
        }
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return false;
        }
        if ((e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb)
                && e.getPersistentData().getBoolean(PROTECTED)) {
            return false;
        }
        if (e instanceof net.minecraft.world.entity.item.ItemEntity item && isReturnedRecord(item)
                && e.level().getGameTime() - item.getPersistentData().getLong(RETURNED) < RETURN_GRACE) {
            return false;
        }
        return !(e instanceof HangingEntity);
    }
}
