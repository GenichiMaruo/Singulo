package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.registry.SinguloSounds;
import io.github.genichimaruo.singulo.ruin.EchoSentinel;
import io.github.genichimaruo.singulo.ruin.GravityRemnant;
import io.github.genichimaruo.singulo.ruin.HorizonWarden;
import io.github.genichimaruo.singulo.ruin.RuinBoss;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * ボスの体から鳴る音。
 * <ul>
 *   <li>気配: 生きている間ずっと、ボスごとの音を途切れずに鳴らし続ける（1つ鳴り終わるとすぐ次。バリエーションはランダム）。
 *       出現と倒されたときの演出のあいだは、演出の音に任せて止める</li>
 *   <li>重力の澱: 体力半分からの重力井戸のうなり（くり返し）</li>
 *   <li>ホライズン・ウォーデン: レーザーを照射している間の焼き切る音（くり返し）</li>
 * </ul>
 * 技が終わる・倒される・見えなくなると止まる。
 */
final class BossSounds {
    static final double RANGE = 48;
    /** 鳴らしている音（実体の番号と音の名前 → 音）。 */
    private static final Map<String, EntityLoop> PLAYING = new HashMap<>();

    private BossSounds() {}

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PLAYING.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        PLAYING.values().removeIf(loop -> loop.isStopped() || !mc.getSoundManager().isActive(loop));
        AABB box = mc.player.getBoundingBox().inflate(RANGE);
        for (LivingEntity boss : mc.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof RuinBoss || e instanceof HorizonWarden)) {
            String presence = presenceSound(boss);
            if (presence != null) {
                keep(mc, boss, presence, presenceVolume(boss), false, BossSounds::present);
            }
        }
        for (GravityRemnant r : mc.level.getEntitiesOfClass(GravityRemnant.class, box, GravityRemnant::wellOpen)) {
            keep(mc, r, "gravity_remnant.well_loop", 1.6F, true, e -> e instanceof GravityRemnant g && g.wellOpen() && !g.isDeadOrDying());
        }
        for (HorizonWarden w : mc.level.getEntitiesOfClass(HorizonWarden.class, box, w -> w.laserTicks() > 0)) {
            keep(mc, w, "horizon_warden.laser_loop", 1.8F, true, e -> e instanceof HorizonWarden h && h.laserTicks() > 0 && !h.isDeadOrDying());
        }
    }

    /** ボスごとの気配の音。 */
    private static String presenceSound(Entity e) {
        if (e instanceof EchoSentinel) {
            return "echo_sentinel.ambient";
        }
        if (e instanceof GravityRemnant) {
            return "gravity_remnant.ambient";
        }
        if (e instanceof HorizonWarden) {
            return "horizon_warden.ambient";
        }
        return null;
    }

    /** 分身の気配は本体より少し小さく（数が増えても騒がしくなりすぎないように）。 */
    private static float presenceVolume(Entity e) {
        if (e instanceof EchoSentinel s && s.isMinion()) {
            return 0.6F;
        }
        return e instanceof HorizonWarden ? 1.4F : 1.2F;
    }

    /** 気配を鳴らすとき: 生きていて、出現と倒されたときの演出のあいだでない。 */
    private static boolean present(Entity e) {
        if (!(e instanceof LivingEntity l) || l.isDeadOrDying() || e.isRemoved()) {
            return false;
        }
        if (e instanceof RuinBoss b) {
            return b.emergeTicks() <= 0;
        }
        return !(e instanceof HorizonWarden w) || w.emergeTicks() <= 0;
    }

    private static void keep(Minecraft mc, Entity e, String sound, float volume, boolean loop, Predicate<Entity> on) {
        String key = e.getId() + "/" + sound;
        if (on.test(e) && !PLAYING.containsKey(key)) {
            EntityLoop s = new EntityLoop(e, sound, volume, loop, on);
            PLAYING.put(key, s);
            mc.getSoundManager().play(s);
        }
    }

    /** 実体についていく音。くり返しのものは、条件が外れたら少しずつ小さくなって止まる。 */
    private static final class EntityLoop extends AbstractTickableSoundInstance {
        private final Entity entity;
        private final Predicate<Entity> on;
        private final float full;

        EntityLoop(Entity entity, String sound, float volume, boolean loop, Predicate<Entity> on) {
            super(SinguloSounds.get(sound), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.entity = entity;
            this.on = on;
            this.full = volume;
            this.looping = loop;
            this.delay = 0;
            this.volume = volume;
            follow();
        }

        private void follow() {
            this.x = entity.getX();
            this.y = entity.getY() + entity.getBbHeight() / 2;
            this.z = entity.getZ();
        }

        @Override
        public void tick() {
            if (entity.isRemoved()) {
                stop();
                return;
            }
            follow();
            if (!on.test(entity)) {
                volume -= full / 6;                      // 0.3秒で消える
                if (volume <= 0.02F) {
                    stop();
                }
            } else {
                volume = full;
            }
        }
    }
}
