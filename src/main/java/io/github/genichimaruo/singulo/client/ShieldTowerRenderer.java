package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * イベントホライズン・シールド発生塔の見た目: 放射冠の上に浮かんで回るリングと、守りが始まると上から降りてくる
 * 半透明の球（半径はシールドの半径、中心は塔の軸）。上から下へ包み込むように張られ、横から見た縁ほど濃く見え、コアのシールドは白く光る。
 * 守っている間は、胴の中心をエネルギーの脈が昇り（導波管から見える）、放射冠が脈に合わせて光る。
 */
public class ShieldTowerRenderer implements BlockEntityRenderer<ShieldTowerBlockEntity> {
    private static final ResourceLocation WHITE = Singulo.id("textures/misc/white.png");
    private static final int LAT = 16;
    private static final int LON = 48;
    /** 塔の軸の根元（基壇の上の段）からの、放射冠の高さ。 */
    private static final float CROWN_HEIGHT = 7.5F;

    public ShieldTowerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ShieldTowerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.getLevel() == null || !be.shownFormed()) {                // 塔ができるまでは何も描かない
            return;
        }
        float time = be.getLevel().getGameTime() + partialTick;
        boolean active = be.shownProtection() > 0;
        boolean core = be.shownProtection() >= 5;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        // ここから先は塔の軸の根元を原点にする
        net.minecraft.core.BlockPos axis = be.axis();
        pose.pushPose();
        pose.translate(axis.getX() - be.getBlockPos().getX(), axis.getY() - be.getBlockPos().getY(), axis.getZ() - be.getBlockPos().getZ());
        float deploy = be.deployTicks(partialTick);
        if (active) {
            renderEnergy(pose, vc, time, core, deploy);
        }
        renderCrown(be, pose, vc, time, active, core, deploy);

        float progress = be.domeProgress(partialTick);
        if (progress <= 0) {
            pose.popPose();
            return;
        }
        float r = be.shownRadius();
        int red = core ? 230 : 120;
        int green = core ? 240 : 210;
        int blue = 255;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // 降りきった直後: 赤道に衝撃波の輪が広がり、球全体が一度明るく光る
        float wave = deploy < ShieldTowerBlockEntity.DEPLOY_WAVE ? 0
                : Mth.clamp((deploy - ShieldTowerBlockEntity.DEPLOY_WAVE) / (ShieldTowerBlockEntity.DEPLOY_END - ShieldTowerBlockEntity.DEPLOY_WAVE), 0, 1);
        if (wave > 0 && wave < 1) {
            float wr = r * (1 + 0.25F * wave);
            FxDraw.ring(pose.last(), vc, wr, wr + 1.5F + 2 * (1 - wave), core ? 0xEAF4FF : 0x9ED8FF, (int) (200 * (1 - wave)), 0);
        }
        float flash = wave > 0 && wave < 1 ? (1 - wave) : 0;
        PoseStack.Pose last = pose.last();
        // 天頂から progress × 180° まで降ろして、塔のまわりを球で包む（地面の下まで）
        float maxTheta = Mth.PI * progress;
        for (int i = 0; i < LAT; i++) {
            float t0 = maxTheta * i / LAT;
            float t1 = maxTheta * (i + 1) / LAT;
            int a0 = Math.min(255, alpha(t0, time) + (int) (90 * flash));
            int a1 = Math.min(255, alpha(t1, time) + (int) (90 * flash));
            for (int j = 0; j < LON; j++) {
                float p0 = Mth.TWO_PI * j / LON;
                float p1 = Mth.TWO_PI * (j + 1) / LON;
                // 外側と内側の両方から見えるよう、両面に描く
                vertex(vc, last, r, t0, p0, red, green, blue, a0);
                vertex(vc, last, r, t1, p0, red, green, blue, a1);
                vertex(vc, last, r, t1, p1, red, green, blue, a1);
                vertex(vc, last, r, t0, p1, red, green, blue, a0);
                vertex(vc, last, r, t0, p1, red, green, blue, a0);
                vertex(vc, last, r, t1, p1, red, green, blue, a1);
                vertex(vc, last, r, t1, p0, red, green, blue, a1);
                vertex(vc, last, r, t0, p0, red, green, blue, a0);
            }
        }
        pose.popPose();
        pose.popPose();
    }

    /** 胴の中心を昇るエネルギーの脈（展開の充電のあいだは速く、太くなっていく）。 */
    private static void renderEnergy(PoseStack pose, VertexConsumer vc, float time, boolean core, float deploy) {
        int rgb = core ? 0xEAF4FF : 0x9ED8FF;
        float charge = deploy < 0 ? 1 : Mth.clamp(deploy / ShieldTowerBlockEntity.DEPLOY_BEAM, 0, 1);
        float rush = deploy >= 0 && deploy < ShieldTowerBlockEntity.DEPLOY_DOME ? 1 + 2 * charge : 1;
        var camera = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        FxDraw.beam(pose.last(), vc, new net.minecraft.world.phys.Vec3(0, 1, 0), new net.minecraft.world.phys.Vec3(0, CROWN_HEIGHT, 0),
                0.08F + 0.08F * charge * rush / 3, rgb, (int) (60 + 60 * charge));
        for (int i = 0; i < 4; i++) {
            float f = (time * 0.03F * rush + i / 4F) % 1F;
            float y = 1 + f * (CROWN_HEIGHT - 1);
            pose.pushPose();
            pose.translate(0, y, 0);
            FxDraw.billboard(pose, vc, camera, 0.4F + 0.3F * Mth.sin(f * Mth.PI), rgb, (int) (220 * Mth.sin(f * Mth.PI)));
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * 放射冠の上: 回る正八面体の結晶と、それを囲んで別々の軸で回る3本の光の輪（ジャイロ）、やわらかい光。
     * 展開のときは、輪が速さを増し（充電）、結晶から空へ光の柱が立ち、まぶしく光る。止まっている間は暗く、ゆっくり。
     */
    private static void renderCrown(ShieldTowerBlockEntity be, PoseStack pose, VertexConsumer vc, float time, boolean active,
                                    boolean core, float deploy) {
        int rgb = core ? 0xEAF4FF : 0x9ED8FF;
        var camera = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        float charge = deploy < 0 ? (active ? 1 : 0) : Mth.clamp(deploy / ShieldTowerBlockEntity.DEPLOY_BEAM, 0, 1);
        float spinUp = deploy >= 0 && deploy < ShieldTowerBlockEntity.DEPLOY_DOME ? 1 + 4 * charge : 1;
        float crystalY = CROWN_HEIGHT + 1.4F + Mth.sin(time * 0.05F) * 0.12F;
        pose.pushPose();
        pose.translate(0.5, crystalY, 0.5);
        // やわらかい光と結晶
        FxDraw.billboard(pose, vc, camera, (active ? 2.2F : 1.2F) + 1.2F * charge * (spinUp - 1) / 4, rgb, active ? 120 : 50);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * (active ? 2.5F : 0.6F) * spinUp));
        FxDraw.octahedron(pose.last(), vc, 0.32F, 0.55F, active ? 0xFFFFFF : 0x9AB8D0, active ? 230 : 140);
        pose.popPose();
        // 3本の光の輪
        FxDraw.gyroRings(pose, vc, 0.95F, 0.07F, time * (active ? 1.5F : 0.3F) * spinUp, rgb, active ? 200 : 90);
        // 展開: 結晶から空へ立つ光の柱と、まぶしい光
        if (deploy >= ShieldTowerBlockEntity.DEPLOY_BEAM && deploy < ShieldTowerBlockEntity.DEPLOY_DOME + 10) {
            float k = (deploy - ShieldTowerBlockEntity.DEPLOY_BEAM) / (ShieldTowerBlockEntity.DEPLOY_DOME + 10 - ShieldTowerBlockEntity.DEPLOY_BEAM);
            float height = be.shownRadius() * Math.min(1, k * 3);
            int a = (int) (230 * (1 - k));
            FxDraw.beam(pose.last(), vc, net.minecraft.world.phys.Vec3.ZERO, new net.minecraft.world.phys.Vec3(0, height, 0),
                    0.5F * (1 - k) + 0.1F, 0xFFFFFF, a);
            FxDraw.beam(pose.last(), vc, net.minecraft.world.phys.Vec3.ZERO, new net.minecraft.world.phys.Vec3(0, height, 0),
                    1.2F * (1 - k) + 0.2F, rgb, a / 2);
            FxDraw.billboard(pose, vc, camera, 6F * (1 - k) + 1, 0xFFFFFF, a);
            for (int i = 0; i < 4; i++) {
                FxDraw.flare(pose, vc, camera, 7F * (1 - k) + 2, 0.15F, i * 45 + time * 0.5F, rgb, a);
            }
        }
        pose.popPose();
        // 充電のあいだ、まわりから光の粒が結晶へ集まる
        if (deploy >= 0 && deploy < ShieldTowerBlockEntity.DEPLOY_BEAM && be.getLevel() != null && be.getLevel().random.nextInt(2) == 0) {
            var rnd = be.getLevel().random;
            net.minecraft.core.BlockPos ax = be.axis();
            double cx = ax.getX() + 0.5, cy = ax.getY() + crystalY, cz = ax.getZ() + 0.5;
            net.minecraft.world.phys.Vec3 d = new net.minecraft.world.phys.Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.5, rnd.nextGaussian())
                    .normalize().scale(3);
            be.getLevel().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, cx + d.x, cy + d.y, cz + d.z,
                    -d.x * 0.08, -d.y * 0.08, -d.z * 0.08);
        }
    }

    /** 縁ほど濃く、ゆっくり降りる明るい帯が流れる。 */
    private static int alpha(float theta, float time) {
        float rim = Mth.sin(theta);                                       // 赤道（横から見た縁）ほど濃い
        float band = Mth.sin(theta * 6 - time * 0.05F) * 0.5F + 0.5F;
        return (int) (12 + 40 * rim * rim + 14 * band);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, float r, float theta, float phi,
                               int red, int green, int blue, int alpha) {
        float x = Mth.sin(theta) * Mth.cos(phi);
        float y = Mth.cos(theta);
        float z = Mth.sin(theta) * Mth.sin(phi);
        vc.vertex(last.pose(), x * r, y * r, z * r).color(red, green, blue, alpha).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(last.normal(), x, y, z).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(ShieldTowerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    public AABB getRenderBoundingBox(ShieldTowerBlockEntity be) {
        return new AABB(be.axis()).inflate(Math.max(12, be.shownRadius()));
    }
}
