package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.nature.KeraunosTowerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * ケラウノス放電塔: 塔の上に浮かぶ放電球。
 * <ul>
 *   <li>待機中: 小さく脈打つ青白い球と、ゆっくり回る3本の輪</li>
 *   <li>ためている間: 輪が速く回りながら縮み、球がふくらんで明るくなる。コイルの頭から球へ細い放電が何本も走る</li>
 *   <li>撃った瞬間: 球から避雷針の先へ、ぎざぎざに折れた太い雷が走り、枝分かれする（2 tick ごとに形が変わる）</li>
 * </ul>
 */
public class KeraunosTowerRenderer implements BlockEntityRenderer<KeraunosTowerBlockEntity> {
    private static final int CORE = 0xEAF6FF;
    private static final int GLOW = 0x78C8FF;
    private static final int ARC = 0xB4E4FF;

    public KeraunosTowerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(KeraunosTowerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        long now = be.getLevel().getGameTime();
        float time = now + partialTick;
        float charge = be.chargeStart() >= 0 ? Mth.clamp((time - be.chargeStart()) / KeraunosTowerBlockEntity.CHARGE_TICKS, 0, 1) : 0;
        float sinceBolt = time - be.boltAt();
        boolean bolting = sinceBolt >= 0 && sinceBolt < KeraunosTowerBlockEntity.BOLT_TICKS;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Vec3 orb = new Vec3(0.5, KeraunosTowerBlockEntity.ORB_HEIGHT, 0.5);

        pose.pushPose();
        pose.translate(orb.x, orb.y, orb.z);
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.25F);
        float size = 0.28F + 0.05F * pulse + 0.35F * charge + (bolting ? 0.4F * (1 - sinceBolt / KeraunosTowerBlockEntity.BOLT_TICKS) : 0);
        FxDraw.billboard(pose, vc, camera, size * 2.2F, GLOW, (int) (90 + 120 * charge));
        FxDraw.billboard(pose, vc, camera, size, CORE, 230);
        float spin = time * (2 + 28 * charge);
        FxDraw.gyroRings(pose, vc, 0.32F - 0.12F * charge, 0.03F, spin, GLOW, (int) (120 + 120 * charge));
        pose.popPose();

        // ためている間: コイルの頭（塔の四隅の上）から球へ細い放電
        if (charge > 0) {
            RandomSource rnd = RandomSource.create(now / 2 * 31 + be.getBlockPos().asLong());
            int arcs = 1 + (int) (charge * 4);
            for (int i = 0; i < arcs; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                Vec3 from = new Vec3(0.5 + Math.cos(a) * 0.38, 0.95, 0.5 + Math.sin(a) * 0.38);
                bolt(pose.last(), vc, from, orb, 4, 0.06, 0.012F, ARC, 200, rnd);
            }
        }
        // 撃った瞬間: 避雷針の先への雷（枝分かれつき）
        BlockPos target = be.target();
        if (bolting && target != null) {
            BlockPos origin = be.getBlockPos();
            Vec3 tip = new Vec3(target.getX() - origin.getX() + 0.5, target.getY() - origin.getY() + 0.9, target.getZ() - origin.getZ() + 0.5);
            RandomSource rnd = RandomSource.create(now / 2 * 17 + target.asLong());
            int alpha = (int) (255 * (1 - sinceBolt / KeraunosTowerBlockEntity.BOLT_TICKS * 0.7F));
            int segments = Math.max(6, (int) (tip.distanceTo(orb) * 1.6));
            Vec3[] main = bolt(pose.last(), vc, orb, tip, segments, 0.35, 0.18F, GLOW, alpha / 2, rnd);
            trace(pose.last(), vc, main, 0.07F, CORE, alpha);
            for (int b = 0; b < 3; b++) {
                Vec3 start = main[1 + rnd.nextInt(Math.max(1, main.length - 2))];
                Vec3 end = start.add((rnd.nextDouble() - 0.5) * 2.4, -rnd.nextDouble() * 1.6, (rnd.nextDouble() - 0.5) * 2.4);
                bolt(pose.last(), vc, start, end, 4, 0.25, 0.05F, ARC, alpha / 2, rnd);
            }
        }
    }

    /** from から to へ、途中を wobble ずつずらしたぎざぎざの雷を描き、折れ点を返す。 */
    private static Vec3[] bolt(PoseStack.Pose last, VertexConsumer vc, Vec3 from, Vec3 to, int segments, double wobble, float width,
                               int rgb, int alpha, RandomSource rnd) {
        Vec3[] pts = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            Vec3 p = from.lerp(to, t);
            if (i > 0 && i < segments) {
                double w = wobble * Math.sin(Math.PI * t);
                p = p.add((rnd.nextDouble() - 0.5) * 2 * w, (rnd.nextDouble() - 0.5) * 2 * w, (rnd.nextDouble() - 0.5) * 2 * w);
            }
            pts[i] = p;
        }
        for (int i = 0; i < segments; i++) {
            FxDraw.beam(last, vc, pts[i], pts[i + 1], width, rgb, alpha);
        }
        return pts;
    }

    /** 決まった折れ点（同じ雷の芯）をなぞる。 */
    private static void trace(PoseStack.Pose last, VertexConsumer vc, Vec3[] path, float width, int rgb, int alpha) {
        for (int i = 0; i + 1 < path.length; i++) {
            FxDraw.beam(last, vc, path[i], path[i + 1], width, rgb, alpha);
        }
    }

    public AABB getRenderBoundingBox(KeraunosTowerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(KeraunosTowerBlockEntity.RANGE + 1);
    }

    @Override
    public boolean shouldRenderOffScreen(KeraunosTowerBlockEntity be) {
        return true;
    }
}
