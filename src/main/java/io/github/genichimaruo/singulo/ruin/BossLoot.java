package io.github.genichimaruo.singulo.ruin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * ボスの落とす物と経験値を、倒れた場所ではなく決まった安全な場所（核やコンソールの上）に出す。
 * 宙に浮いたまま倒れたボスの落とし物が、床の穴や隙間へ落ちて失われないように。落とし物は時間で消えず、
 * ブラックホール（マイクロブラックホール・Pリアクター）にも飲み込まれない。
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
        item.setExtendedLifetime();
        item.getPersistentData().putBoolean(io.github.genichimaruo.singulo.reactor.EventHorizonTargets.PROTECTED, true);
        if (boss.captureDrops() != null) {
            boss.captureDrops().add(item);
        } else {
            boss.level().addFreshEntity(item);
        }
        return item;
    }

    /** 経験値を安全な場所に出す。 */
    static void experience(ServerLevel level, Vec3 at, int amount) {
        if (amount > 0) {
            ExperienceOrb.award(level, at, amount);
        }
    }
}
