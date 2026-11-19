package io.github.genichimaruo.singulo.ruin;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/** 遺構の守り手（ホライズン・ウォーデン・ボス部屋の番人・警備ドローン・遺構のアンドロイド。プレイヤーの護衛のアンドロイドは除く）。互いに狙わず、互いの攻撃で傷つかない。 */
public final class RuinGuards {
    private RuinGuards() {}

    static boolean canSummon(ServerLevel level, @Nullable ServerPlayer player) {
        if (level.getDifficulty() != Difficulty.PEACEFUL) return true;
        if (player != null) {
            player.displayClientMessage(Component.translatable("gui.singulo.guardian.peaceful"), true);
        }
        return false;
    }

    public static boolean isGuard(Entity e) {
        return e instanceof HorizonWarden || e instanceof SecurityDrone || e instanceof RuinBoss
                || e instanceof io.github.genichimaruo.singulo.android.RuinAndroid a && !a.isGuard();
    }

    /** 守り手どうしの攻撃（光弾・技・跳ね返した飛び道具も含む）か。 */
    static boolean friendlyFire(DamageSource source) {
        Entity by = source.getEntity();
        return by != null && isGuard(by);
    }
}
