package io.github.genichimaruo.singulo.client;

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
        if (be.state() != PenroseReactorBlockEntity.State.RUNNING || be.getLevel() == null) {
            return;
        }
        double mass = be.mass();
        float horizon = (float) be.horizonRadius();
        float time = be.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0.5 + Structures.CONTROLLER_BELOW_CENTER, 0.5);
        drawSphere(pose, buffers.getBuffer(RenderType.entitySolid(WHITE)), horizon);

        // 降着円盤: 少し傾け、スピンに比例した速さで回す
        pose.mulPose(Axis.XP.rotationDegrees(12));
        float angle = time * (float) (0.5 + 4.0 * be.spin());
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = Vec3.atCenterOf(be.getBlockPos().above(Structures.CONTROLLER_BELOW_CENTER));
        Vec3 toCamera = camera.subtract(center).normalize();
        drawDisk(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), horizon * 1.6F, horizon * 3.0F,
                Math.toRadians(angle), toCamera, (float) be.spin());
        pose.popPose();
        // 画面の重力レンズ（アインシュタイン半径は地平線の2.2倍。黒い中心はシェーダー側で地平線の大きさに合わせる）
        // 歪むのはリアクターの内側の空洞（リングの内側）だけ
        GravitationalLensing.add(center, horizon * GravitationalLensing.EINSTEIN_PER_HORIZON, 1.0F,
                Structures.REACTOR_RADIUS - 0.5F);
    }

    private static void drawSphere(PoseStack pose, VertexConsumer vc, float r) {
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
        vc.addVertex(last, x * r, y * r, z * r).setColor(0, 0, 0, 255).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0).setNormal(last, x, y, z);
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
        vc.addVertex(last, x, 0, z).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0, ny, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(PenroseReactorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(PenroseReactorBlockEntity be) {
        return new AABB(be.getBlockPos().above(Structures.CONTROLLER_BELOW_CENTER)).inflate(Structures.REACTOR_RADIUS + 8); // 重力レンズは構造の外まで届く
    }
}
