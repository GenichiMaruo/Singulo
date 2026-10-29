package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import io.github.genichimaruo.singulo.ruin.EchoSentinel;
import io.github.genichimaruo.singulo.ruin.ErgosphereLeviathan;
import io.github.genichimaruo.singulo.ruin.FoundryOverseer;
import io.github.genichimaruo.singulo.ruin.GravityRemnant;
import io.github.genichimaruo.singulo.ruin.HorizonWarden;
import io.github.genichimaruo.singulo.ruin.LastArchivist;
import io.github.genichimaruo.singulo.ruin.RuinBoss;
import io.github.genichimaruo.singulo.ruin.WardenSingularity;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
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
 *   <li>うなり: 出現の演出の始まりから、倒されて体が消えるまで、ボスごとの不気味な低いうなりを切れ目なくくり返す（分身は鳴らさない）</li>
 *   <li>気配: 生きている間ずっと、ボスごとの音を途切れずに鳴らし続ける（1つ鳴り終わるとすぐ次。バリエーションはランダム）。
 *       出現と倒されたときの演出のあいだは、演出の音に任せて止める</li>
 *   <li>重力の澱: 体力半分からの重力井戸のうなり（くり返し）</li>
 *   <li>ホライズン・ウォーデン: レーザーを照射している間の焼き切る音（くり返し）と、特異点が引き込んでいる間のうなり</li>
 *   <li>アンドロイド: スラスターを開いている間の噴射音（くり返し。ボスではないが、同じ仕組みで鳴らす）</li>
 * </ul>
 * 技が終わる・倒される・見えなくなると止まる。
 */
final class BossSounds {
    static final double RANGE = 48;
    /** 鳴らしている音（実体の番号と音の名前から作った番号 → 音）。 */
    private static final Map<Long, EntityLoop> PLAYING = new HashMap<>();

    private BossSounds() {}

    static void reset() {
        Minecraft mc = Minecraft.getInstance();
        for (var sound : PLAYING.values()) mc.getSoundManager().stop(sound);
        PLAYING.clear();
    }

    /**
     * ボスごとの声: 音の名前の頭（"&lt;頭&gt;.drone" と "&lt;頭&gt;.ambient"）、気配の音があるか、うなりと気配の大きさ。
     * うなりの大きさは 1 を超えると遠くまで届く（部屋の外からでも気配がわかるように）。分身の気配は本体より少し小さく。
     */
    private record Voice(String prefix, boolean ambient, float droneVolume, float presenceVolume) {}

    @Nullable
    private static Voice voice(Entity e) {
        return switch (e) {
            case EchoSentinel s -> new Voice("echo_sentinel", true, 1.8F, s.isMinion() ? 0.6F : 1.2F);
            case GravityRemnant g -> new Voice("gravity_remnant", true, 1.8F, 1.2F);
            case HorizonWarden w -> new Voice("horizon_warden", true, 2.2F, 1.4F);
            case ErgosphereLeviathan l -> new Voice("ergosphere_leviathan", true, 2.2F, 1.2F);
            case LastArchivist a -> new Voice("last_archivist", true, 1.8F, 1.2F);
            case FoundryOverseer f -> new Voice("foundry", false, 1.8F, 0F);
            default -> null;
        };
    }

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
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box,
                e -> e instanceof RuinBoss || e instanceof HorizonWarden || e instanceof WardenSingularity || e instanceof RuinAndroid)) {
            Voice v = voice(e);
            if (v != null) {
                if (!(e instanceof RuinBoss b && b.isMinion())) {
                    keep(mc, e, v.prefix() + ".drone", v.droneVolume(), true, x -> !x.isRemoved());
                }
                if (v.ambient()) {
                    keep(mc, e, v.prefix() + ".ambient", v.presenceVolume(), false, BossSounds::present);
                }
            }
            switch (e) {
                case GravityRemnant g when g.wellOpen() ->
                        keep(mc, g, "gravity_remnant.well_loop", 1.6F, true, x -> x instanceof GravityRemnant r && r.wellOpen() && !r.isDeadOrDying());
                // 特異点が引き込んでいる間の重いうなり
                case WardenSingularity s -> keep(mc, s, "gravity_remnant.well_loop", 1.4F, true, x -> !x.isRemoved());
                // スラスターを開いて飛んで・泳いでいる間の噴射音
                case RuinAndroid a when a.thrusterOpen(1) > 0.5F ->
                        keep(mc, a, "android.thruster", 0.5F, true, x -> x instanceof RuinAndroid r && r.thrusterOpen(1) > 0.3F && r.isAlive());
                case HorizonWarden w when w.laserTicks() > 0 ->
                        keep(mc, w, "horizon_warden.laser_loop", 1.8F, true, x -> x instanceof HorizonWarden h && h.laserTicks() > 0 && !h.isDeadOrDying());
                default -> {
                }
            }
        }
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
        long key = (long) e.getId() << 32 | (sound.hashCode() & 0xFFFFFFFFL);
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
