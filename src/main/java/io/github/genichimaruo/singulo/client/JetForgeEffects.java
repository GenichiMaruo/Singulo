package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.forge.JetForgeBlockEntity;
import io.github.genichimaruo.singulo.forge.JetForgeClientIndex;
import io.github.genichimaruo.singulo.forge.JetForgeLayout;
import io.github.genichimaruo.singulo.generated.ClientConfig;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * 降着ジェット鍛造炉の、画面と音の演出（クライアント）。
 * <ul>
 *   <li>閃光: 仕上げの瞬間に画面を白く光らせる（設定 jetForgeFlash で弱める・切る。近いほど強い）。ジェットが収束リングを
 *       突き抜けるたびにも軽く光る。光が出るのは一瞬だけで、照射中は画面を白くしない</li>
 *   <li>揺れ: 収束リングの突き抜けで短く、照射中は近くで細かく、仕上げの衝撃波で大きく揺れる（設定 jetForgeShake）</li>
 *   <li>火花: 収束リングの突き抜けで、リングの縁から放射状に飛ぶ</li>
 *   <li>音: 炉心のうなり（待機中も）・ジェットの轟音（点火から仕上げまで、ジェットの強さに合わせて）・ビームのうなり（収束と照射のあいだ）をくり返し鳴らす。段階の切り替わりの音はサーバーが鳴らす</li>
 *   <li>粒: 照射中の火花、冷却中の湯気（粒子の設定で減らす）</li>
 * </ul>
 */
final class JetForgeEffects {
    private static final double SOUND_RANGE = 64;
    private static final double SHAKE_RANGE = 40;
    private static final Map<BlockPos, Loop> HUMS = new HashMap<>();
    private static final Map<BlockPos, Loop> BEAMS = new HashMap<>();
    private static final Map<BlockPos, Loop> JETS = new HashMap<>();
    /** 3つの収束リングの放電（リングごと。キーは位置 × 4 + リングの番号）。 */
    private static final Map<Long, Loop> ARCS = new HashMap<>();
    /** くり返す音の種類（ARC + i は i 番目の収束リングの放電）。 */
    static final int HUM = 0, BEAM = 1, JET = 2, ARC = 3;
    private static float flash;
    private static float shake;
    /** 突き抜けの火花を最後に散らした時刻（キーは位置 × 4 + リングの番号。同じ突き抜けで2回散らさない）。 */
    private static final Map<Long, Long> BURSTS = new HashMap<>();

    private JetForgeEffects() {}

    static float flashSetting() {
        return ClientConfig.SPEC.isLoaded() ? ClientConfig.JET_FORGE_FLASH.get().floatValue() : 1;
    }

    static float shakeSetting() {
        return ClientConfig.SPEC.isLoaded() ? ClientConfig.JET_FORGE_SHAKE.get().floatValue() : 1;
    }

    static void onClientTick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            HUMS.clear();
            BEAMS.clear();
            JETS.clear();
            ARCS.clear();
            BURSTS.clear();
            flash = 0;
            shake = 0;
            return;
        }
        float flashNow = 0;
        float shakeNow = 0;
        Vec3 eye = mc.player.getEyePosition();
        for (JetForgeBlockEntity be : JetForgeClientIndex.loaded()) {
            Direction back = be.back();
            if (be.getLevel() != mc.level || !be.formed() || back == null) {
                continue;
            }
            BlockPos pos = be.getBlockPos();
            Vec3 core = JetForgeLayout.core(pos, back);
            Vec3 target = JetForgeLayout.target(pos, back);
            double d = Math.min(eye.distanceTo(core), eye.distanceTo(target));
            JetForgeTimeline tl = JetForgeTimeline.of(be, 0);
            if (tl.finish() >= 0) {
                float near = (float) Mth.clamp(1 - eye.distanceTo(target) / 64, 0, 1);
                flashNow = Math.max(flashNow, near * Math.max(0, 1 - tl.finish() / 14F));
                shakeNow = Math.max(shakeNow, (float) Mth.clamp(1 - d / SHAKE_RANGE, 0, 1) * Math.max(0, 1 - tl.finish() / 24F) * 1.4F);
            } else if (tl.phase() == JetForgeBlockEntity.IRRADIATE && !tl.stalled()) {
                shakeNow = Math.max(shakeNow, (float) Mth.clamp(1 - d / 18, 0, 1) * (0.08F + 0.22F * tl.shatter()));
            } else if (tl.phase() == JetForgeBlockEntity.IGNITE && tl.t() < 25) {
                shakeNow = Math.max(shakeNow, (float) Mth.clamp(1 - d / 24, 0, 1) * 0.25F * (1 - tl.t() / 25F));
            } else if (tl.phase() == JetForgeBlockEntity.FOCUS && !tl.stalled()) {
                // ジェットが収束リングを突き抜けた瞬間: 軽い閃光と揺れ、リングから火花（後ろのリングほど強い）
                for (int i = 0; i < 3; i++) {
                    float age = JetForgeRenderer.punchAge(tl, i);
                    if (age < 0 || age >= 10) {
                        continue;
                    }
                    Vec3 ring = JetForgeLayout.at(pos, back, JetForgeLayout.LENS_X[i], JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
                    double dr = eye.distanceTo(ring);
                    float k = (1 - age / 10F) * (0.8F + 0.2F * i);
                    flashNow = Math.max(flashNow, (float) Mth.clamp(1 - dr / 48, 0, 1) * 0.3F * k);
                    shakeNow = Math.max(shakeNow, (float) Mth.clamp(1 - dr / 32, 0, 1) * 0.65F * k);
                    long key = pos.asLong() * 4 + i;
                    long now = mc.level.getGameTime();
                    Long last = BURSTS.get(key);
                    if (age < 4 && dr < 96 && (last == null || now - last > 40 || now < last)) {
                        BURSTS.put(key, now);
                        burst(mc, ring, JetForgeLayout.axis(back), JetForgeLayout.LENS_R[i], i);
                    }
                }
            }
            if (d < SOUND_RANGE) {
                keepLoop(mc, HUMS, pos, be, HUM, core);
                keepLoop(mc, BEAMS, pos, be, BEAM, core.add(JetForgeLayout.axis(back).scale(12)));
                // ジェットの轟音は炉心から（両極へ噴き出す所）
                keepLoop(mc, JETS, pos, be, JET, core);
                // ジリジリした放電は、3つの収束リングからそれぞれ
                for (int i = 0; i < 3; i++) {
                    Vec3 ring = JetForgeLayout.at(pos, back, JetForgeLayout.LENS_X[i], JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
                    keepArc(mc, pos, be, i, ring);
                }
            }
            particles(mc, be, tl, back, d);
        }
        flash = Math.max(flashNow, flash * 0.6F);
        shake = Math.max(shakeNow, shake * 0.8F);
        HUMS.values().removeIf(l -> l.isStopped());
        BEAMS.values().removeIf(l -> l.isStopped());
        JETS.values().removeIf(l -> l.isStopped());
        ARCS.values().removeIf(l -> l.isStopped());
    }

    /** 突き抜けの火花: リングの縁から外へ放射状に飛ぶ光の粒と、ジェットの向きへ噴き出す粒（粒子の設定で減る）。 */
    private static void burst(Minecraft mc, Vec3 c, Vec3 axis, float radius, int ring) {
        RandomSource rnd = mc.level.random;
        Vec3 a = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 e1 = axis.cross(a).normalize();
        Vec3 e2 = axis.cross(e1).normalize();
        int count = 48 + 16 * ring;
        for (int k = 0; k < count; k++) {
            float ang = rnd.nextFloat() * Mth.TWO_PI;
            Vec3 dir = e1.scale(Mth.cos(ang)).add(e2.scale(Mth.sin(ang)));
            Vec3 p = c.add(dir.scale(radius * (0.6 + 0.4 * rnd.nextDouble())));
            double speed = 0.35 + 0.65 * rnd.nextDouble();
            Vec3 v = dir.scale(speed).add(axis.scale(0.25 + 0.5 * rnd.nextDouble()));
            mc.level.addParticle(k % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        for (int k = 0; k < 16; k++) {
            Vec3 v = axis.scale(0.8 + 0.8 * rnd.nextDouble()).add(e1.scale(rnd.nextGaussian() * 0.08)).add(e2.scale(rnd.nextGaussian() * 0.08));
            mc.level.addParticle(ParticleTypes.FIREWORK, c.x, c.y, c.z, v.x, v.y, v.z);
        }
    }

    private static void keepArc(Minecraft mc, BlockPos pos, JetForgeBlockEntity be, int ring, Vec3 at) {
        long key = pos.asLong() * 4 + ring;
        Loop l = ARCS.get(key);
        if (l != null && !l.isStopped() && mc.getSoundManager().isActive(l)) {
            return;
        }
        if (Loop.volumeFor(be, ARC + ring) <= 0.01F) {
            return;
        }
        Loop loop = new Loop(be, ARC + ring, at);
        ARCS.put(key, loop);
        mc.getSoundManager().play(loop);
    }

    private static void keepLoop(Minecraft mc, Map<BlockPos, Loop> loops, BlockPos pos, JetForgeBlockEntity be, int beam, Vec3 at) {
        Loop l = loops.get(pos);
        if (l != null && !l.isStopped() && mc.getSoundManager().isActive(l)) {
            return;
        }
        if (Loop.volumeFor(be, beam) <= 0.01F) {
            return;
        }
        Loop loop = new Loop(be, beam, at);
        loops.put(pos.immutable(), loop);
        mc.getSoundManager().play(loop);
    }

    private static void particles(Minecraft mc, JetForgeBlockEntity be, JetForgeTimeline tl, Direction back, double d) {
        if (d > 48 || mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL || mc.isPaused()) {
            return;
        }
        int scale = mc.options.particles().get() == net.minecraft.client.ParticleStatus.DECREASED ? 1 : 2;
        RandomSource rnd = mc.level.random;
        Vec3 axis = JetForgeLayout.axis(back);
        Vec3 target = JetForgeLayout.target(be.getBlockPos(), back);
        if (tl.phase() == JetForgeBlockEntity.IRRADIATE && !tl.stalled()) {
            // 当たった所から跳ね返る火花
            Vec3 hit = target.subtract(axis.scale(0.5));
            for (int i = 0; i < scale * (1 + (int) (3 * tl.shatter())); i++) {
                Vec3 v = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).scale(0.18).subtract(axis.scale(0.12));
                mc.level.addParticle(rnd.nextInt(3) == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, hit.x, hit.y, hit.z, v.x, v.y, v.z);
            }
        }
        if (tl.phase() == JetForgeBlockEntity.COOL && tl.cool() < 0.7F && rnd.nextFloat() < 0.5F * scale) {
            // 冷やすときの湯気（照射室の中央と、排熱側の杯）
            Vec3 c = rnd.nextBoolean() ? target : JetForgeLayout.at(be.getBlockPos(), back, 1.5, JetForgeLayout.AXIS_Y, JetForgeLayout.AXIS_Z);
            mc.level.addParticle(ParticleTypes.CLOUD, c.x + rnd.nextGaussian() * 0.4, c.y + rnd.nextGaussian() * 0.4,
                    c.z + rnd.nextGaussian() * 0.4, 0, 0.06 + rnd.nextFloat() * 0.05, 0);
        }
    }

    /** 画面の揺れ（向きを少しずらす）。 */
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float s = shake * shakeSetting();
        if (s <= 0.002F) {
            return;
        }
        double t = event.getPartialTick() + (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime());
        float k = s * 1.6F;
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 1.9) * Math.sin(t * 0.7)) * k);
        event.setPitch(event.getPitch() + (float) (Math.sin(t * 2.3 + 1.0) * Math.cos(t * 0.9)) * k);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 1.3 + 2.0) * k * 0.6F);
    }

    /** 画面の閃光（仕上げの一瞬だけ）。 */
    static void onRenderGui(RenderGuiEvent.Pre event) {
        float a = flash * flashSetting();
        if (a <= 0.004F) {
            return;
        }
        var g = event.getGuiGraphics();
        int alpha = (int) (Math.min(0.9F, a) * 255);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24 | 0xF4F8FF);
    }

    /** くり返す音（炉心のうなり・ビームのうなり・ジェットの轟音）。音の大きさは段階に合わせて毎 tick 変わり、止まると消える。 */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final JetForgeBlockEntity be;
        private final int beam;

        Loop(JetForgeBlockEntity be, int beam, Vec3 at) {
            super(SinguloSounds.get(beam == BEAM ? "jet_forge.beam" : beam == JET ? "jet_forge.jet" : beam >= ARC ? "jet_forge.arc"
                    : "black_hole.ambient"), SoundSource.BLOCKS,
                    SoundInstance.createUnseededRandom());
            this.be = be;
            this.beam = beam;
            this.looping = true;
            this.delay = 0;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.volume = 0.01F;
            this.relative = false;
        }

        /**
         * ジェットとビームは長い線の音源: 毎 tick、線の上でいちばん聞き手に近い点から鳴らす（どこにいても、近くの噴流から
         * 聞こえる）。ジェットは排熱側の杯から加工側の先端まで（先端は収束が進むほど遠くへ伸びる）、ビームは最初の収束リングから素材まで。
         */
        private void follow(JetForgeTimeline tl) {
            if (beam == HUM || beam >= ARC || be.back() == null) {
                return;                                      // 炉心のうなりと放電は、決まった所から
            }
            Vec3 core = be.core();
            Vec3 axis = JetForgeLayout.axis(be.back());
            float front;
            float rear;
            if (beam == JET) {
                float wide = 4 + 9 * tl.jet();
                front = Mth.lerp(JetForgeTimeline.ease(tl.focus() * 1.15F), wide, JetForgeLayout.TARGET_X - JetForgeLayout.CORE_X);
                rear = -Math.min(JetForgeLayout.CORE_X - 1.5F, 3 + 10 * tl.jet());
            } else {
                front = JetForgeLayout.TARGET_X - JetForgeLayout.CORE_X;
                rear = JetForgeLayout.LENS_X[0] - JetForgeLayout.CORE_X;
            }
            Vec3 a = core.add(axis.scale(rear));
            Vec3 b = core.add(axis.scale(front));
            Vec3 ear = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 ab = b.subtract(a);
            double t = Mth.clamp(ear.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr()), 0, 1);
            Vec3 at = a.add(ab.scale(t));
            x = at.x;
            y = at.y;
            z = at.z;
        }

        static float volumeFor(JetForgeBlockEntity be, int beam) {
            if (!be.formed() || !be.shownCore()) {
                return 0;
            }
            JetForgeTimeline tl = JetForgeTimeline.of(be, 0);
            if (beam == HUM) {
                return 0.35F + 0.5F * tl.power();
            }
            if (beam >= ARC) {
                // そのリングが入っている間（入った瞬間から鳴り、仕上げで消える）。止まっている間は弱く
                float on = tl.ring(beam - ARC);
                return tl.stalled() ? on * 0.35F : on * 1.1F;
            }
            if (beam == JET) {
                // ジェットの強さそのまま。照射中がいちばん強く、止まっている間は弱くちらつく
                float jet = tl.jet() * (tl.phase() == JetForgeBlockEntity.IRRADIATE ? 1 : 0.85F);
                return tl.stalled() ? jet * 0.3F : jet * 1.4F;
            }
            float on = tl.phase() == JetForgeBlockEntity.IRRADIATE ? 1
                    : tl.phase() == JetForgeBlockEntity.FOCUS ? 0.3F + 0.7F * tl.focus()
                    : tl.phase() == JetForgeBlockEntity.IGNITE ? 0.3F * tl.jet() : 0;
            return tl.stalled() ? on * 0.3F : on * 1.6F;
        }

        @Override
        public void tick() {
            if (be.isRemoved() || be.getLevel() != Minecraft.getInstance().level) {
                stop();
                return;
            }
            float want = volumeFor(be, beam);
            volume += (want - volume) * 0.2F;
            if (want <= 0.01F && volume < 0.02F) {
                stop();
                return;
            }
            JetForgeTimeline tl = JetForgeTimeline.of(be, 0);
            follow(tl);
            pitch = beam >= ARC ? 0.92F + 0.1F * (beam - ARC) + 0.04F * Mth.sin(be.clientTicks() * 0.53F + beam)
                    : beam == BEAM ? 0.85F + 0.3F * tl.irr()
                    : beam == JET ? 0.8F + 0.12F * tl.focus() + 0.08F * tl.irr() + 0.03F * Mth.sin(be.clientTicks() * 0.37F)
                    : 0.7F + 0.4F * tl.power();
        }
    }
}
