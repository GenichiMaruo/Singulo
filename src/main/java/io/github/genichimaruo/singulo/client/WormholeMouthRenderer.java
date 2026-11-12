package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity;
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
 * ワームホールの口: 枠だけの筐体の中心に浮かぶ小さな黒い球（喉）と、その周りを回る薄紫の光の輪。
 * 喉の大きさに比例して大きくなるが、いちばん大きくても輪まで筐体の内側（枠の2ドットの内側）に収まる。
 * 重力レンズを小さく掛けて、周りの景色をゆがめる。
 */
public class WormholeMouthRenderer implements BlockEntityRenderer<WormholeMouthBlockEntity> {
    private static final ResourceLocation WHITE = Singulo.id("textures/misc/white.png");
    private static final int LAT = 10;
    private static final int LON = 16;

    public WormholeMouthRenderer(BlockEntityRendererProvider.Context context) {}

    public static float throatRadius(int size) {
        return 0.08F + 0.02F * size;
    }

    @Override
    public void render(WormholeMouthBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        int size = be.size();
        if (size <= 0 || be.getLevel() == null) {
            return;
        }
        if(be.traversable())seam(be,partialTick,pose,buffers);
        float r = throatRadius(size);
        float time = be.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, WormholeMouthBlockEntity.MOUTH_HEIGHT, 0.5);
        sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(WHITE)), r);
        pose.mulPose(Axis.YP.rotationDegrees(time * 4));
        pose.mulPose(Axis.XP.rotationDegrees(20));
        VertexConsumer glow = GlowDepth.of(buffers, WHITE);
        FxDraw.ring(pose.last(), glow, r * 1.15F, r * 2.0F, 0xC8C8FF, 230, 230);
        pose.popPose();
        Vec3 c = Vec3.atBottomCenterOf(be.getBlockPos()).add(0, WormholeMouthBlockEntity.MOUTH_HEIGHT, 0);
        GravitationalLensing.add(c, r * GravitationalLensing.EINSTEIN_PER_HORIZON, 0.6F);
    }

    private static void membraneVertex(VertexConsumer vc, PoseStack pose, float x, float y, int alpha) {
        vc.addVertex(pose.last(), x, y, 0).setColor(0x0A, 0x06, 0x18, alpha).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
    }

    /**
     * 通行用の縫合口: 空間の裂け目を光る潮汐繊維で縫ったもの。片側だけで閉じている間は糸が裂け目を綴じていて、
     * 両端がつながると糸が左右へ引かれて開き、向こう側へ歩いて通れる。大きさは通行の判定（WormholeTransit）と同じ。
     */
    /** 縫い糸の太さ（ブロック）。膜（z=0）から表面が半分ずつ離れる。 */
    private static final float THREAD_WIDTH = 0.06F;

    private static void seam(WormholeMouthBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.translate(0.5, 2.5, 0.5);
        float yaw = be.getBlockState().getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING).toYRot();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        float time = (be.getLevel().getGameTime() % 24000 + partial) / 20F;
        boolean open = be.passageState() == 2;
        // 開いているときは四角い口が幅いっぱいに、閉じているときは細い線になる
        float halfX = open ? 1.34F : 0.14F + 0.03F * Mth.sin(time * 1.4F);
        final float halfY = 1.42F;
        final int rows = 16;
        int thread = open ? 0xFFEBC0 : 0xDAAF6E;
        int rim = open ? 0xA070FF : 0x7A5030;
        float reach = open ? 0.34F : 0.46F;
        // 糸・縁・星は不透明の固体で描く。半透明の細長い面は、斜めから見ると前後の順が毎フレーム入れ替わってちらつくため。
        // 固体なら奥行きは画素ごとに決まる。半透明で描くのは、あとの暗い膜だけ
        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(WHITE));
        float[] widths = new float[rows];
        for (int i = 0; i < rows; i++) {
            float y0 = -halfY + 2 * halfY * i / rows;
            float y1 = -halfY + 2 * halfY * (i + 1) / rows;
            float mid = (y0 + y1) / 2 / halfY;
            // 上下の端だけ少しすぼまる。縁は糸に引かれて波打つ
            float taper = 1 - 0.14F * mid * mid * mid * mid;
            float w = halfX * taper + 0.04F * Mth.sin(y0 * 5 + time * 0.8F);
            widths[i] = w;
            for (int side = -1; side <= 1; side += 2) {
                float x0 = side > 0 ? w - 0.09F : -w;
                float x1 = side > 0 ? w : -w + 0.09F;
                FxDraw.box(pose, solid, x0, y0, -0.04F, x1, y1, 0.04F, LightTexture.FULL_BRIGHT, rim);
            }
            // 縫い目: 閉じているときは裂け目を横切って綴じ、開いているときは縁をまたいで外へ張る。
            // 四角い棒で描く（板を十字に組むと、1枚が膜と同じ面に入ってちらつくため）
            if (open) {
                for (int side = -1; side <= 1; side += 2) {
                    FxDraw.rod(pose.last(), solid, new Vec3(side * (w - reach), y0 + 0.02F, 0), new Vec3(side * (w + reach), y1 - 0.02F, 0), THREAD_WIDTH, thread, LightTexture.FULL_BRIGHT);
                }
            } else {
                float side = i % 2 == 0 ? 1 : -1;
                FxDraw.rod(pose.last(), solid, new Vec3(-side * (w + reach), y0, 0), new Vec3(side * (w + reach), y1, 0), THREAD_WIDTH, thread, LightTexture.FULL_BRIGHT);
            }
        }
        if (open) {
            // 上下の縁
            for (int side = -1; side <= 1; side += 2) {
                float y = side * (halfY + 0.02F);
                FxDraw.box(pose, solid, -1.2F, y - 0.03F, -0.04F, 1.2F, y + 0.03F, 0.04F, LightTexture.FULL_BRIGHT, rim);
            }
            // 向こう側の星（ゆっくり色が移ろう。膜の中に浮かぶ）
            for (int i = 0; i < 36; i++) {
                float sx = Mth.frac(i * 0.7549F + 0.13F) * 2 - 1;
                float sy = Mth.frac(i * 0.5698F + 0.37F + time * 0.015F) * 2 - 1;
                float twinkle = 0.5F + 0.5F * Mth.sin(time * 0.7F + i * 1.7F);
                float x = sx * 1.0F;
                float y = sy * 1.28F;
                float size = 0.018F + 0.02F * Mth.frac(i * 0.27F);
                int star = FxDraw.lerpRgb(0x5A5A90, i % 3 == 0 ? 0xE0C8FF : 0xB8C8FF, twinkle);
                FxDraw.box(pose, solid, x - size, y - size, -size, x + size, y + size, size, LightTexture.FULL_BRIGHT, star);
            }
        }
        // 向こう側が透ける暗い膜
        VertexConsumer glow = GlowDepth.of(buffers, WHITE);
        for (int i = 0; i < rows; i++) {
            float y0 = -halfY + 2 * halfY * i / rows;
            float y1 = -halfY + 2 * halfY * (i + 1) / rows;
            // 厚みのない1枚の面（箱にすると、段の境目の面が重なって濃い横線になる）
            float w0 = widths[i];
            float w1 = widths[Math.min(i + 1, rows - 1)];
            int alpha = open ? 205 : 240;
            membraneVertex(glow, pose, -w0, y0, alpha);
            membraneVertex(glow, pose, w0, y0, alpha);
            membraneVertex(glow, pose, w1, y1, alpha);
            membraneVertex(glow, pose, -w1, y1, alpha);
        }
        pose.popPose();
    }

    private static void sphere(PoseStack.Pose last, VertexConsumer vc, float r) {
        for (int i = 0; i < LAT; i++) {
            float t0 = Mth.PI * i / LAT - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / LAT - Mth.HALF_PI;
            for (int j = 0; j < LON; j++) {
                float p0 = Mth.TWO_PI * j / LON;
                float p1 = Mth.TWO_PI * (j + 1) / LON;
                v(vc, last, r, t0, p0);
                v(vc, last, r, t1, p0);
                v(vc, last, r, t1, p1);
                v(vc, last, r, t0, p1);
            }
        }
    }

    private static void v(VertexConsumer vc, PoseStack.Pose last, float r, float theta, float phi) {
        float x = Mth.cos(theta) * Mth.cos(phi);
        float y = Mth.sin(theta);
        float z = Mth.cos(theta) * Mth.sin(phi);
        vc.addVertex(last, x * r, y * r, z * r).setColor(0, 0, 0, 255).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0).setNormal(last, x, y, z);
    }

    @Override
    public AABB getRenderBoundingBox(WormholeMouthBlockEntity be) {
        // 口が画面の外に出ても、周りの重力レンズが画面にかかっている間は描く
        return new AABB(be.getBlockPos()).inflate(8);
    }

    @Override
    public boolean shouldRenderOffScreen(WormholeMouthBlockEntity be) {
        return true;
    }
}
