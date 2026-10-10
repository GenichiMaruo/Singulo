package io.github.genichimaruo.singulo.client;

import net.minecraft.core.BlockPos;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.multiblock.Structures;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 炉心の描画: 光を返さない完全な黒の球（事象の地平線）と、回転する降着円盤。
 * 円盤は観測者に近づいてくる側が明るい（ドップラー・ビーミング）。大きさは炉心質量に比例し、回転の速さはスピンに比例する。
 * 画面空間の重力レンズは GravitationalLensing が掛ける。
 */
public class PenroseReactorRenderer implements BlockEntityRenderer<PenroseReactorBlockEntity> {
    private static final ResourceLocation WHITE = Singulo.id("textures/misc/white.png");
    private static final int SPHERE_LAT = 12;
    private static final int SPHERE_LON = 16;
    private static final int DISK_SEGMENTS = 64;

    public PenroseReactorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PenroseReactorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        PenroseReactorBlockEntity.State state = be.state();
        float now = be.getLevel().getGameTime() + partialTick;
        Long flash = trackFormation(be, state);
        if (state == PenroseReactorBlockEntity.State.IGNITING) {
            renderIgnition(be, now, pose, buffers);
        }
        if (flash != null) {
            renderFormationFlash(be, now - flash, pose, buffers);
        }
        if (state != PenroseReactorBlockEntity.State.RUNNING) {
            return;
        }
        float horizon = (float) be.horizonRadius();
        float time = be.getLevel().getGameTime() + partialTick;
        Vec3 center = Vec3.atCenterOf(be.getBlockPos().above(Structures.CONTROLLER_BELOW_CENTER));
        pose.pushPose();
        pose.translate(0.5, 0.5 + Structures.CONTROLLER_BELOW_CENTER, 0.5);
        renderHole(pose, buffers, horizon, (float) be.spin(), time, center, Structures.REACTOR_RADIUS - 0.5F);
        renderPellets(be, pose, buffers, time, horizon);
        pose.popPose();
        if (be.collapsing()) {
            renderCollapse(be, pose, buffers, time, center);
        }
    }

    /**
     * ブラックホール本体: 黒い地平線、傾いて回る降着円盤、画面の重力レンズ。pose は中心に合わせておく。
     * region は景色を歪める範囲の半径（リアクターの中ではリングの内側だけ）。
     */
    static void renderHole(PoseStack pose, MultiBufferSource buffers, float horizon, float spin, float time, Vec3 worldCenter,
                           float region) {
        pose.pushPose();
        drawSphere(pose, buffers.getBuffer(RenderType.entitySolid(WHITE)), horizon);
        // 降着円盤: 少し傾け、スピンに比例した速さで回す
        pose.mulPose(Axis.XP.rotationDegrees(12));
        float angle = time * (0.5F + 4.0F * spin);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 toCamera = camera.subtract(worldCenter).normalize();
        drawDisk(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), horizon * 1.6F, horizon * 3.0F,
                Math.toRadians(angle), toCamera, spin);
        pose.popPose();
        // 画面の重力レンズ（アインシュタイン半径は地平線の2.2倍。黒い中心はシェーダー側で地平線の大きさに合わせる）
        GravitationalLensing.add(worldCenter, horizon * GravitationalLensing.EINSTEIN_PER_HORIZON, 1.0F, region);
    }

    /**
     * 飲み込まれていくペレット: 12のポートのどれかから、渦を巻きながら中心へ落ちていく光の粒。地平線に近づくほど
     * 速く、細長く引き伸ばされ、赤くなっていき、最後に地平線の縁で小さく光って消える。
     */
    private static void renderPellets(PenroseReactorBlockEntity be, PoseStack pose, MultiBufferSource buffers, float time, float horizon) {
        var flights = be.clientPellets();
        if (flights.isEmpty()) {
            return;
        }
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        double c = Structures.REACTOR_RADIUS * Math.sqrt(0.5) - 0.6;
        for (PenroseReactorBlockEntity.PelletFlight f : flights) {
            float k = (time - f.start()) / PenroseReactorBlockEntity.PELLET_FLIGHT_TICKS;
            if (k < 0 || k > 1) {
                continue;
            }
            Vec3 from = portPoint(f.port(), c);
            Vec3 now = pelletPos(from, k, f.twist(), horizon);
            Vec3 before = pelletPos(from, Math.max(0, k - 0.06F - 0.12F * k * k), f.twist(), horizon);
            int rgb = lerpRgb(0xFFD9A0, 0xFF5030, k * k);
            int a = (int) (230 * Math.min(1, (1 - k) * 4));
            FxDraw.beam(pose.last(), glow, before, now, 0.12F * (1 - 0.6F * k), rgb, a / 2);
            pose.pushPose();
            pose.translate(now.x, now.y, now.z);
            FxDraw.billboard(pose, glow, camera, 0.35F * (1 - 0.5F * k), 0xFFFFFF, a);
            FxDraw.billboard(pose, glow, camera, 0.7F * (1 - 0.4F * k), rgb, a / 2);
            pose.popPose();
            if (k > 0.85F) {
                // 地平線の縁での最後の光
                float f2 = (k - 0.85F) / 0.15F;
                Vec3 edge = now.normalize().scale(horizon * 1.05);
                pose.pushPose();
                pose.translate(edge.x, edge.y, edge.z);
                FxDraw.billboard(pose, glow, camera, 0.5F + 0.6F * f2, 0xFFE0C0, (int) (200 * (1 - f2)));
                pose.popPose();
            }
        }
    }

    private static Vec3 portPoint(int i, double c) {
        int sx = (i & 1) == 0 ? -1 : 1;
        int sy = (i & 2) == 0 ? -1 : 1;
        return switch (i / 4) {
            case 0 -> new Vec3(sx * c, sy * c, 0);
            case 1 -> new Vec3(0, sx * c, sy * c);
            default -> new Vec3(sx * c, 0, sy * c);
        };
    }

    /** 渦を巻いて中心へ落ちる道筋（k は 0〜1）。 */
    private static Vec3 pelletPos(Vec3 from, float k, float twist, float horizon) {
        double r0 = from.length();
        double r = horizon + (r0 - horizon) * (1 - k * k);
        double turn = twist * Math.PI * 1.5 * k * k;
        Vec3 dir = from.normalize();
        Vec3 axis = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(axis).normalize();
        Vec3 d = dir.scale(Math.cos(turn)).add(side.scale(Math.sin(turn)));
        return d.scale(r);
    }

    /**
     * 炉の崩壊: リングや周りのブロックが、近いものから順に中心へ吸い込まれていく。回りながら縮み、地平線に消える。
     * サーバーがブロックを消す瞬間から、ここで同じブロックが飛んでいくのを描く。
     */
    private static void renderCollapse(PenroseReactorBlockEntity be, PoseStack pose, MultiBufferSource buffers, float time, Vec3 center) {
        var blocks = be.collapseBlocks();
        int n = blocks.size();
        float t = time - be.collapseStart();
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        BlockPos origin = be.getBlockPos();
        int fly = 24;
        for (int i = 0; i < n; i++) {
            int d = PenroseReactorBlockEntity.collapseDelay(i, n);
            float k = (t - d) / fly;
            if (k < 0 || k >= 1) {
                continue;
            }
            BlockPos p = blocks.get(i);
            Vec3 start = Vec3.atCenterOf(p);
            float e = k * k;
            Vec3 at = start.add(center.subtract(start).scale(e));
            float scale = 1 - 0.9F * e;
            pose.pushPose();
            pose.translate(at.x - origin.getX(), at.y - origin.getY(), at.z - origin.getZ());
            pose.mulPose(Axis.YP.rotationDegrees(k * 540 + i * 37));
            pose.mulPose(Axis.XP.rotationDegrees(k * 360 + i * 11));
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, -0.5, -0.5);
            dispatcher.renderSingleBlock(be.collapseState(i), pose, buffers, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        var level = be.getLevel();
        if (level != null && level.random.nextInt(2) == 0 && n > 0) {
            BlockPos p = blocks.get(level.random.nextInt(n));
            Vec3 v = center.subtract(Vec3.atCenterOf(p)).scale(0.06);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                    v.x, v.y, v.z);
        }
    }

    // ------------------------------------------------------------------ 点火と、ブラックホールができる瞬間

    /** 炉心ごとの、前に見た状態と、ブラックホールができた時刻（クライアント）。 */
    private static final java.util.Map<net.minecraft.core.BlockPos, PenroseReactorBlockEntity.State> LAST_STATE = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.core.BlockPos, Long> FORMED_AT = new java.util.HashMap<>();
    private static final int FLASH_TICKS = 40;

    /** 点火から稼働に変わった瞬間を見つけ、光と粒を出す。光っている間はその時刻を返す。 */
    @javax.annotation.Nullable
    private static Long trackFormation(PenroseReactorBlockEntity be, PenroseReactorBlockEntity.State state) {
        net.minecraft.core.BlockPos pos = be.getBlockPos();
        long now = be.getLevel().getGameTime();
        PenroseReactorBlockEntity.State last = LAST_STATE.put(pos.immutable(), state);
        if (last == PenroseReactorBlockEntity.State.IGNITING && state == PenroseReactorBlockEntity.State.RUNNING) {
            FORMED_AT.put(pos.immutable(), now);
            Vec3 c = be.coreCenter();
            var level = be.getLevel();
            var rnd = level.random;
            for (int i = 0; i < 120; i++) {
                Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize().scale(0.4 + rnd.nextDouble() * 0.6);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, c.x, c.y, c.z, d.x, d.y, d.z);
            }
            for (int i = 0; i < 80; i++) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, c.x + rnd.nextGaussian() * 2,
                        c.y + rnd.nextGaussian() * 2, c.z + rnd.nextGaussian() * 2, 0, 0, 0);
            }
        }
        Long at = FORMED_AT.get(pos);
        if (at != null && now - at > FLASH_TICKS) {
            FORMED_AT.remove(pos);
            return null;
        }
        return at;
    }

    /**
     * 点火中（16秒、点火の音の終わりで炉心ができる）。3つの段階で高まっていく:
     * <ol>
     * <li>目覚め（0〜6秒）: ジャイロのリングをかすかな光がゆっくり巡り始め、抽出ポートが1つずつ灯る。</li>
     * <li>注入（6〜13.6秒、点火の音）: 光の筋が速さを増し、12のポートから中心の種へ光が流れ込み、種が脈打ちながら膨らむ。
     *     景色が歪み始める。電力が満ちていないうちは流れ込む光がちらつく。</li>
     * <li>崩壊（最後の2.4秒）: リングの光が中心へ縮み、3平面の光の輪が絞り込まれ、粒が渦を巻いて吸い込まれる。
     *     種が激しく明滅し、最後に黒い地平線が生まれかける。</li>
     * </ol>
     */
    private static void renderIgnition(PenroseReactorBlockEntity be, float t, PoseStack pose, MultiBufferSource buffers) {
        float p = be.ignitionProgress();
        float charge = be.ignitionCharge();
        float wake = Mth.clamp(p / 0.375F, 0, 1);                          // 目覚め
        float inject = Mth.clamp((p - 0.375F) / (0.85F - 0.375F), 0, 1);   // 注入
        float collapse = Mth.clamp((p - 0.85F) / 0.15F, 0, 1);            // 崩壊
        float power = 0.25F * wake + 0.75F * inject;
        Vec3 center = be.coreCenter();
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        int beamRgb = lerpRgb(0x64DCFF, 0xFFFFFF, power + collapse);
        // 電力が満ちていないうちは、流れ込む光がちらつく
        float flicker = charge >= 1F ? 1F : 0.55F + 0.45F * Mth.sin(t * 2.3F) * Mth.sin(t * 0.7F);
        pose.pushPose();
        pose.translate(0.5, 0.5 + Structures.CONTROLLER_BELOW_CENTER, 0.5);

        // ジャイロのリングを駆ける光の筋（3平面）。崩壊では半径が中心へ縮む
        float radius = Structures.REACTOR_RADIUS * (1 - 0.82F * collapse * collapse);
        float spin = t * (1.5F + 30 * power * power + 90 * collapse);
        for (int plane = 0; plane < 3; plane++) {
            pose.pushPose();
            if (plane == 1) {
                pose.mulPose(Axis.XP.rotationDegrees(90));
            } else if (plane == 2) {
                pose.mulPose(Axis.ZP.rotationDegrees(90));
            }
            pose.mulPose(Axis.YP.rotationDegrees(spin + plane * 40));
            int a = (int) Math.min(255, 40 + 120 * wake + 95 * inject);
            float width = 0.35F + 0.4F * power + 0.5F * collapse;
            float arc = 25 + 35 * power + 60 * collapse;
            for (int k = 0; k < 2; k++) {
                FxDraw.crescent(pose.last(), glow, -arc, arc, radius, width, beamRgb, a);
                pose.mulPose(Axis.YP.rotationDegrees(180));
            }
            // 崩壊: 3平面に絞り込まれる光の輪
            if (collapse > 0) {
                FxDraw.ring(pose.last(), glow, radius * 0.92F, radius * 0.92F + 0.25F, 0xFFFFFF, (int) (220 * collapse), 0);
            }
            pose.popPose();
        }

        // 12か所の抽出ポート: 目覚めで1つずつ灯り、注入で中心へ光が流れ込む
        double c = Structures.REACTOR_RADIUS * Math.sqrt(0.5);
        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                Vec3[] ports = {new Vec3(sx * c, sy * c, 0), new Vec3(0, sx * c, sy * c), new Vec3(sx * c, 0, sy * c)};
                for (Vec3 from : ports) {
                    float lit = Mth.clamp(wake * 12 - i, 0, 1);
                    if (lit > 0) {
                        pose.pushPose();
                        pose.translate(from.x, from.y, from.z);
                        FxDraw.billboard(pose, glow, camera, 0.5F + 0.4F * power, beamRgb, (int) (180 * lit));
                        pose.popPose();
                    }
                    if (inject > 0) {
                        Vec3 to = from.scale(collapse * 0.8);                // 崩壊では光の根元も中心へ寄る
                        FxDraw.beam(pose.last(), glow, from.scale(1 - 0.82 * collapse * collapse), to.scale(0),
                                0.03F + 0.14F * inject + 0.1F * collapse, beamRgb, (int) ((50 + 190 * inject) * flicker));
                        for (int k = 0; k < 2; k++) {
                            float s = ((t * (0.03F + 0.08F * inject + 0.2F * collapse) + i * 0.137F + k * 0.5F) % 1F);
                            Vec3 dot = from.scale((1 - s) * (1 - 0.82 * collapse * collapse));
                            pose.pushPose();
                            pose.translate(dot.x, dot.y, dot.z);
                            FxDraw.billboard(pose, glow, camera, 0.18F + 0.3F * inject, 0xFFFFFF, (int) ((120 + 120 * inject) * flicker));
                            pose.popPose();
                        }
                    }
                    i++;
                }
            }
        }

        // 中心の種: 何重にも重なった光。芯・にじむ光・回る光の筋・種を囲むジャイロの輪・周りを巡る火花
        float pulse = 1 + (0.12F + 0.25F * collapse) * Mth.sin(t * (0.3F + 1.4F * power + 4F * collapse));
        float seed = (0.5F + 3.0F * power) * (1 - 0.55F * collapse) * pulse;
        FxDraw.billboard(pose, glow, camera, seed * 3.2F, 0x3A70FF, (int) (30 + 60 * power));
        FxDraw.billboard(pose, glow, camera, seed * 1.8F, 0x64DCFF, (int) (60 + 110 * power));
        FxDraw.billboard(pose, glow, camera, seed, 0xFFFFFF, (int) (160 + 95 * Math.max(power, collapse)));
        // 光の筋（長い4本はゆっくり、短い4本は逆向きに速く回る）
        float streak = seed * (2.2F + 2.5F * collapse);
        for (int k = 0; k < 4; k++) {
            FxDraw.flare(pose, glow, camera, streak, 0.06F + 0.1F * power, t * 0.6F + k * 45, 0xDDF4FF, (int) (90 + 140 * power));
            FxDraw.flare(pose, glow, camera, streak * 0.55F, 0.05F + 0.06F * power, -t * 1.7F + k * 45 + 22.5F, 0x9EDCFF,
                    (int) (60 + 120 * power));
        }
        // 種を囲むジャイロの輪（注入から現れ、崩壊で種へ絞り込まれる）
        if (inject > 0) {
            float ringR = (0.6F + 1.6F * inject) * (1 - 0.7F * collapse);
            FxDraw.gyroRings(pose, glow, ringR, 0.05F + 0.06F * inject, t * (1 + 4 * collapse), 0xB8ECFF, (int) (120 * inject + 100 * collapse));
        }
        // 種の周りを巡る火花
        for (int k = 0; k < 10; k++) {
            float ang = t * (0.08F + 0.02F * k) + k * 2.4F;
            float tilt = k * 0.7F;
            float orbit = (0.9F + 0.25F * (k % 3)) * seed * 0.8F;
            pose.pushPose();
            pose.translate(Mth.cos(ang) * orbit, Mth.sin(ang) * orbit * Mth.sin(tilt), Mth.sin(ang) * orbit * Mth.cos(tilt));
            FxDraw.billboard(pose, glow, camera, 0.12F + 0.1F * power, 0xFFFFFF, (int) (160 * Math.max(wake, power)));
            pose.popPose();
        }
        if (collapse > 0.55F) {
            // 黒い地平線が生まれかける
            float k = (collapse - 0.55F) / 0.45F;
            FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(WHITE)), 0.15F + 0.45F * k * k, 0x000000,
                    net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        }
        pose.popPose();

        // 崩壊: 渦を巻いて吸い込まれる粒
        var level = be.getLevel();
        if (level != null && (inject > 0.5F || collapse > 0)) {
            var rnd = level.random;
            int n = collapse > 0 ? 6 : 1;
            for (int k = 0; k < n; k++) {
                if (rnd.nextFloat() > 0.5F + 0.5F * collapse) {
                    continue;
                }
                Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.3, rnd.nextGaussian()).normalize();
                double r = 3 + rnd.nextDouble() * 3;
                Vec3 at = center.add(d.scale(r));
                Vec3 tangent = d.cross(new Vec3(0, 1, 0)).normalize().scale(0.25);
                Vec3 v = d.scale(-0.35 - 0.3 * collapse).add(tangent);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y, at.z, v.x, v.y, v.z);
            }
        }
        GravitationalLensing.addDistortion(center, 0.1F + 0.6F * power * power + 1.2F * collapse, 0.3F + 0.5F * power + 0.3F * collapse,
                Structures.REACTOR_RADIUS - 0.5F);
    }

    /** ブラックホールができた瞬間: まぶしい光が縮み、3平面に衝撃波の輪が広がる。 */
    private static void renderFormationFlash(PenroseReactorBlockEntity be, float age, PoseStack pose, MultiBufferSource buffers) {
        float k = Mth.clamp(age / FLASH_TICKS, 0, 1);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.pushPose();
        pose.translate(0.5, 0.5 + Structures.CONTROLLER_BELOW_CENTER, 0.5);
        FxDraw.billboard(pose, glow, camera, 9 * (1 - k) + 0.5F, 0xFFFFFF, (int) (255 * (1 - k)));
        FxDraw.billboard(pose, glow, camera, 14 * (1 - k * 0.6F), 0x9BE6FF, (int) (140 * (1 - k)));
        float r = 1 + 15 * (float) Math.sqrt(k);
        int a = (int) (230 * (1 - k));
        for (int plane = 0; plane < 3; plane++) {
            pose.pushPose();
            if (plane == 1) {
                pose.mulPose(Axis.XP.rotationDegrees(90));
            } else if (plane == 2) {
                pose.mulPose(Axis.ZP.rotationDegrees(90));
            }
            FxDraw.ring(pose.last(), glow, r, r + 0.5F + 1.5F * (1 - k), 0xFFFFFF, a, a / 4);
            FxDraw.ring(pose.last(), glow, r * 0.7F, r * 0.7F + 0.3F, 0x9BE6FF, a / 2, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0, 1);
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    static void drawSphere(PoseStack pose, VertexConsumer vc, float r) {
        PoseStack.Pose last = pose.last();
        for (int i = 0; i < SPHERE_LAT; i++) {
            float t0 = Mth.PI * i / SPHERE_LAT - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / SPHERE_LAT - Mth.HALF_PI;
            for (int j = 0; j < SPHERE_LON; j++) {
                float p0 = Mth.TWO_PI * j / SPHERE_LON;
                float p1 = Mth.TWO_PI * (j + 1) / SPHERE_LON;
                sphereVertex(vc, last, r, t0, p0);
                sphereVertex(vc, last, r, t1, p0);
                sphereVertex(vc, last, r, t1, p1);
                sphereVertex(vc, last, r, t0, p1);
            }
        }
    }

    private static void sphereVertex(VertexConsumer vc, PoseStack.Pose last, float r, float theta, float phi) {
        float x = Mth.cos(theta) * Mth.cos(phi);
        float y = Mth.sin(theta);
        float z = Mth.cos(theta) * Mth.sin(phi);
        vc.vertex(last.pose(), x * r, y * r, z * r).color(0, 0, 0, 255).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0).normal(last.normal(), x, y, z).endVertex();
    }

    /**
     * 円盤を両面で描く。各区間の明るさ = 基本の明るさ + 視線方向への速度成分（ドップラー・ビーミング）。
     * diskAngle は円盤の回転角（ワールドに対する向きを求めるのに使う）。
     */
    private static void drawDisk(PoseStack pose, VertexConsumer vc, float inner, float outer, double diskAngle,
                                 Vec3 toCamera, float spin) {
        PoseStack.Pose last = pose.last();
        for (int s = 0; s < DISK_SEGMENTS; s++) {
            float a0 = Mth.TWO_PI * s / DISK_SEGMENTS;
            float a1 = Mth.TWO_PI * (s + 1) / DISK_SEGMENTS;
            float mid = (a0 + a1) / 2;
            // 回転方向の接線（ワールドの向きにおおよそ戻す）
            double world = mid - diskAngle;
            double vx = -Math.sin(world);
            double vz = Math.cos(world);
            double doppler = vx * toCamera.x + vz * toCamera.z;
            float bright = (float) Mth.clamp(0.55 + 0.45 * doppler * (0.4 + 0.6 * spin), 0.15, 1.0);
            int ri = (int) (255 * bright);
            int gi = (int) (200 * bright + 40);
            int bi = (int) (150 * bright + 90);
            int ro = (int) (180 * bright);
            int go = (int) (110 * bright);
            int bo = (int) (90 * bright + 40);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            // 上面
            diskVertex(vc, last, c0 * inner, s0 * inner, ri, gi, bi, 230, 1);
            diskVertex(vc, last, c0 * outer, s0 * outer, ro, go, bo, 0, 1);
            diskVertex(vc, last, c1 * outer, s1 * outer, ro, go, bo, 0, 1);
            diskVertex(vc, last, c1 * inner, s1 * inner, ri, gi, bi, 230, 1);
            // 下面
            diskVertex(vc, last, c1 * inner, s1 * inner, ri, gi, bi, 230, -1);
            diskVertex(vc, last, c1 * outer, s1 * outer, ro, go, bo, 0, -1);
            diskVertex(vc, last, c0 * outer, s0 * outer, ro, go, bo, 0, -1);
            diskVertex(vc, last, c0 * inner, s0 * inner, ri, gi, bi, 230, -1);
        }
    }

    private static void diskVertex(VertexConsumer vc, PoseStack.Pose last, float x, float z, int r, int g, int b, int a,
                                   float ny) {
        vc.vertex(last.pose(), x, 0, z).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(last.normal(), 0, ny, 0).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(PenroseReactorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    public AABB getRenderBoundingBox(PenroseReactorBlockEntity be) {
        return new AABB(be.getBlockPos().above(Structures.CONTROLLER_BELOW_CENTER)).inflate(Structures.REACTOR_RADIUS + 8); // 重力レンズは構造の外まで届く
    }
}
