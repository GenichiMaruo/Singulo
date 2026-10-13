package io.github.genichimaruo.singulo.ruin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * ボスの落とす物を、倒れた場所ではなく決まった安全な場所（核やコンソールの上）に出し、経験値は挑戦者へ直接与える。
 * 宙に浮いたまま倒れたボスの落とし物が、床の穴や隙間へ落ちて失われないように。落とし物は時間で消えず
 * （無期限。ほかのアイテムとは結合しないので、印も失われない）、ブラックホール（マイクロブラックホール・Pリアクター）にも
 * 飲み込まれない。
 */
final class BossLoot {
    private BossLoot() {}

    /** 落とし物を1つ、安全な場所に出す（死んだときの取りまとめ中なら、そこへ加える）。 */
    static ItemEntity drop(LivingEntity boss, Vec3 at, ItemStack stack) {
        if (stack.isEmpty() || boss.level().isClientSide) {
            return null;
        }
        ItemEntity item = new ItemEntity(boss.level(), at.x, at.y, at.z, stack, 0, 0.15, 0);
        item.setDefaultPickUpDelay();
        item.setUnlimitedLifetime();
        item.getPersistentData().putBoolean(io.github.genichimaruo.singulo.reactor.EventHorizonTargets.PROTECTED, true);
        if (boss.captureDrops() != null) {
            boss.captureDrops().add(item);
        } else {
            boss.level().addFreshEntity(item);
        }
        return item;
    }

    /**
     * 経験値を、部屋にいる挑戦者それぞれへ直接 perPlayer ずつ与える（オーブではないので、時間で消えず、ブラックホールにも
     * 吸われない）。部屋に誰もいなければ、合計 total を安全な場所へオーブで出す。
     */
    static void experience(ServerLevel level, Vec3 at, int total, int perPlayer, java.util.List<? extends net.minecraft.world.entity.player.Player> players) {
        if (total <= 0) {
            return;
        }
        if (players.isEmpty()) {
            ExperienceOrb.award(level, at, total);
            return;
        }
        for (net.minecraft.world.entity.player.Player p : players) {
            p.giveExperiencePoints(perPlayer);
        }
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
