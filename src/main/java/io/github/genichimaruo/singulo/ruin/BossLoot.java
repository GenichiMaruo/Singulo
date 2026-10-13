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

    /** 経験値を受け取れる挑戦者か（ボスの人数に数える条件と同じ。生きていて、クリエイティブ・観戦者でない）。 */
    static boolean recipient(net.minecraft.world.entity.player.Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    /**
     * 経験値の合計 total を、部屋にいる挑戦者で等しく分けて直接与える（オーブではないので、時間で消えず、ブラックホールにも
     * 吸われない）。割り切れない分は先の人から1ずつ足すので、合計はちょうど total（人数が増えても増えない）。
     * 修繕の付いた傷んだ装備があれば、オーブを拾ったときと同じように先に直す。
     * 部屋に誰もいなければ、合計 total を安全な場所へオーブで出す（ブラックホールに吸われず、通常より長く残る）。
     */
    static void experience(ServerLevel level, Vec3 at, int total, java.util.List<? extends net.minecraft.world.entity.player.Player> players) {
        if (total <= 0) {
            return;
        }
        if (players.isEmpty()) {
            orbs(level, at, total);
            return;
        }
        int share = total / players.size();
        int extra = total % players.size();
        for (int i = 0; i < players.size(); i++) {
            net.minecraft.world.entity.player.Player p = players.get(i);
            int xp = share + (i < extra ? 1 : 0);
            if (xp > 0) {
                xp = p instanceof net.minecraft.server.level.ServerPlayer sp ? repair(sp, xp) : xp;
                if (xp > 0) {
                    p.giveExperiencePoints(xp);
                }
            }
        }
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 修繕の付いた傷んだ装備を経験値で直し、残りを返す（経験値オーブを拾ったときと同じ換算）。 */
    static int repair(net.minecraft.server.level.ServerPlayer player, int xp) {
        while (xp > 0) {
            var found = net.minecraft.world.item.enchantment.EnchantmentHelper.getRandomItemWith(
                    net.minecraft.world.item.enchantment.EnchantmentEffectComponents.REPAIR_WITH_XP, player, ItemStack::isDamaged);
            if (found.isEmpty()) {
                return xp;
            }
            ItemStack stack = found.get().itemStack();
            int durability = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDurabilityToRepairFromXp(player.serverLevel(), stack, xp);
            int fixed = Math.min(durability, stack.getDamageValue());
            if (fixed <= 0) {
                return xp;
            }
            stack.setDamageValue(stack.getDamageValue() - fixed);
            xp -= (int) ((long) fixed * xp / durability);
        }
        return 0;
    }

    /** 誰もいないときのオーブ。ブラックホールに吸われず、約30分残る（ふつうは5分）。 */
    private static void orbs(ServerLevel level, Vec3 at, int total) {
        while (total > 0) {
            int value = ExperienceOrb.getExperienceValue(total);
            total -= value;
            ExperienceOrb orb = new ExperienceOrb(level, at.x, at.y, at.z, value);
            net.minecraft.nbt.CompoundTag tag = orb.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            tag.putShort("Age", Short.MIN_VALUE);
            orb.load(tag);
            orb.getPersistentData().putBoolean(io.github.genichimaruo.singulo.reactor.EventHorizonTargets.PROTECTED, true);
            level.addFreshEntity(orb);
        }
    }
}
