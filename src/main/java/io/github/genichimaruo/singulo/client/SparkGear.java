package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.android.SparkAndroid;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 放電型「エクレール」の装備と放電の描画。
 *
 * <ul>
 * <li>腰の機械: 背中の腰に背負う蓄電・発電の機械。白い外殻に暗い芯、中央にコイルの窓、左右に蓄電セル（内蔵の1本と、
 *     追加のバッテリーの数だけ灯る）と、ケーブルの差し込み口。</li>
 * <li>放電籠手: 両方の前腕に着ける白い籠手。コイルの帯が3本巻かれ、拳の先に電極が出ている。肘の後ろにケーブルの差し込み口。</li>
 * <li>ケーブル: 腰の機械の左右の口から、同じ側の籠手の肘へ。たるんで垂れ、戦闘中は電流の光の粒が籠手へ流れる。</li>
 * <li>放電（世界の座標）: アークの道筋、天雷の溜めと落雷、足元の電場、仲間への給電・連携の放電、待機中の指先の火花。</li>
 * </ul>
 *
 * 画素の単位（1/16 ブロック）で組み、体の部品の変形に合わせて描く。
 */
final class SparkGear {
    private static final int WHITE = 0xE6EAF0, LIGHT = 0xCDD3DE, DARK = 0x363C4C, CABLE = 0x2A2E38, COIL = 0xB8963A;
    /** 籠手のコイルの帯の高さ（前腕の画素。肘が 0、手首が 5）。 */
    private static final float[] BANDS = {1.5F, 2.5F, 3.5F};
    /** ケーブルの差し込み口: 腰の機械の左右の口（機械の画素）と、籠手の肘の後ろ（前腕の画素）。 */
    private static final float UNIT_PORT_X = 3.3F, UNIT_PORT_Y = 0.1F, UNIT_PORT_Z = 1.0F;
    private static final float ARM_PORT_Y = 1.0F;

    private SparkGear() {}

    // ------------------------------------------------------------------ 装備（体の座標）

    static void renderGear(AndroidModel m, PoseStack pose, MultiBufferSource buffers, int light, SparkAndroid spark, float partialTick, float age) {
        if (m.sparkUnit == null) return;
        float glow = spark.coilGlow(partialTick);
        float pulse = 0.75F + 0.25F * Mth.lerp(glow, Mth.sin(age * 0.25F), Mth.sin(age * 0.85F));
        int lit = 1 + spark.batteries();
        VertexConsumer body = buffers.getBuffer(RenderTypes.entityCutout(FxDraw.WHITE));

        // 腰の機械
        pose.pushPose();
        m.toSparkUnit(pose);
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        FxDraw.box(pose, body, -2.6F, -1.4F, 0.0F, 2.6F, 1.6F, 2.0F, light, DARK);
        FxDraw.box(pose, body, -2.85F, -1.75F, -0.1F, 2.85F, -1.1F, 2.25F, light, WHITE);          // 上の外殻
        FxDraw.box(pose, body, -2.85F, 1.15F, -0.1F, 2.85F, 1.8F, 2.25F, light, WHITE);            // 下の外殻
        FxDraw.box(pose, body, -1.25F, -1.05F, 1.95F, 1.25F, 1.1F, 2.15F, light, LIGHT);           // コイルの窓の枠
        for (int side = -1; side <= 1; side += 2) {
            FxDraw.box(pose, body, side < 0 ? -2.45F : 1.55F, -0.95F, 1.95F, side < 0 ? -1.55F : 2.45F, 0.95F, 2.2F, light, DARK);  // 蓄電セルの枠
            float px0 = side < 0 ? -3.35F : 2.55F, px1 = side < 0 ? -2.55F : 3.35F;
            FxDraw.box(pose, body, px0, -0.45F, 0.55F, px1, 0.65F, 1.45F, light, LIGHT);           // ケーブルの口
        }
        VertexConsumer fx = buffers.getBuffer(RenderTypes.lightning());
        int a = (int) (200 * glow * pulse);
        AndroidRenderer.glowBox(pose, fx, -1.0F, -0.85F, 2.12F, 1.0F, 0.9F, 2.25F, SparkAndroid.ACCENT, a);
        AndroidRenderer.glowBox(pose, fx, -0.45F, -0.45F, 2.2F, 0.45F, 0.5F, 2.3F, 0xFFFFFF, (int) (a * 0.8F));
        // 蓄電セル: 左に内蔵の1本、右に追加のバッテリーの数（最大3）。灯っていないセルは暗い
        for (int i = 0; i < 4; i++) {
            boolean left = i == 0;
            float x0 = left ? -2.3F : 1.7F + (i - 1) * 0.25F, x1 = left ? -1.7F : x0 + 0.18F;
            boolean on = i < lit;
            AndroidRenderer.glowBox(pose, fx, x0, -0.75F, 2.18F, x1, 0.75F, 2.26F, on ? SparkAndroid.ACCENT : 0x404040, on ? (int) (170 * pulse) : 40);
        }
        pose.popPose();

        // 両腕の放電籠手
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            pose.pushPose();
            if (right) m.toRightForearm(pose);
            else m.toLeftForearm(pose);
            pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
            float r = right ? 1.72F : 1.28F;
            body = buffers.getBuffer(RenderTypes.entityCutout(FxDraw.WHITE));
            FxDraw.box(pose, body, -r, 0.9F, -r, r, 4.55F, r, light, WHITE);
            for (float y : BANDS) {
                FxDraw.box(pose, body, -r - 0.15F, y, -r - 0.15F, r + 0.15F, y + 0.4F, r + 0.15F, light, COIL);
            }
            FxDraw.box(pose, body, -0.9F, 4.4F, -r - 0.25F, -0.4F, 5.4F, -r + 0.35F, light, DARK);       // 電極
            FxDraw.box(pose, body, 0.4F, 4.4F, -r - 0.25F, 0.9F, 5.4F, -r + 0.35F, light, DARK);
            FxDraw.box(pose, body, -0.55F, ARM_PORT_Y - 0.5F, r - 0.1F, 0.55F, ARM_PORT_Y + 0.5F, r + 0.5F, light, LIGHT);   // ケーブルの口
            fx = buffers.getBuffer(RenderTypes.lightning());
            for (int i = 0; i < BANDS.length; i++) {
                float wave = 0.6F + 0.4F * Mth.sin(age * 0.5F - i * 1.2F);
                float y = BANDS[i];
                AndroidRenderer.glowBox(pose, fx, -r - 0.2F, y + 0.1F, -r - 0.2F, r + 0.2F, y + 0.3F, r + 0.2F, SparkAndroid.ACCENT,
                        (int) (150 * glow * wave));
            }
            AndroidRenderer.glowBox(pose, fx, -0.8F, 5.2F, -r - 0.2F, -0.5F, 5.55F, -r + 0.3F, 0xFFFFFF, (int) (200 * glow * pulse));
            AndroidRenderer.glowBox(pose, fx, 0.5F, 5.2F, -r - 0.2F, 0.8F, 5.55F, -r + 0.3F, 0xFFFFFF, (int) (200 * glow * pulse));
            pose.popPose();
        }

        // ケーブル（腰の機械の口 → 同じ側の籠手の肘）
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            Vec3 from = point(p -> m.toSparkUnit(p), right ? -UNIT_PORT_X : UNIT_PORT_X, UNIT_PORT_Y, UNIT_PORT_Z);
            float r = right ? 1.72F : 1.28F;
            Vec3 to = point(p -> {
                if (right) m.toRightForearm(p);
                else m.toLeftForearm(p);
            }, 0, ARM_PORT_Y, r + 0.3F);
            // 体の座標は下が +Y、後ろが +Z。口から外へ少し出て、下へたるんでから肘へ上がる
            Vec3 out = new Vec3(right ? -0.06 : 0.06, 0.05, 0.08);
            Vec3 c1 = from.add(out.scale(2.2)).add(0, 0.12, 0);
            Vec3 c2 = to.add(0, 0.16, 0.12);
            tube(pose, buffers.getBuffer(RenderTypes.entityCutout(FxDraw.WHITE)), from, c1, c2, to, 0.032F, CABLE, light);
            if (glow > 0.5F) {
                // 電流の光の粒が、機械から籠手へ流れる
                fx = buffers.getBuffer(RenderTypes.lightning());
                for (int k = 0; k < 3; k++) {
                    float s = ((age * 0.06F + k / 3F) % 1F);
                    Vec3 p = bezier(from, c1, c2, to, s);
                    Vec3 q = bezier(from, c1, c2, to, Math.min(1, s + 0.05F));
                    FxDraw.beam(pose.last(), fx, p, q, 0.05F, SparkAndroid.ACCENT, (int) (200 * (glow - 0.5F) * 2));
                }
            }
        }
    }

    /** 変形 parts を積んだ所の、画素の座標 (x, y, z) の点（このレイヤーの座標で）。 */
    private static Vec3 point(java.util.function.Consumer<PoseStack> parts, float x, float y, float z) {
        PoseStack p = new PoseStack();
        parts.accept(p);
        Vector3f v = p.last().pose().transformPosition(new Vector3f(x / 16F, y / 16F, z / 16F));
        return new Vec3(v.x, v.y, v.z);
    }

    record Anchors(Vec3 right, Vec3 left, Vec3 unit) {}

    static Anchors anchors(AndroidModel m, org.joml.Matrix4f modelToEntity) {
        return new Anchors(entityPoint(modelToEntity, point(m::toRightForearm, 0, 5.5F, -1.72F)),
                entityPoint(modelToEntity, point(m::toLeftForearm, 0, 5.5F, -1.28F)),
                entityPoint(modelToEntity, point(m::toSparkUnit, 0, 0, 2.3F)));
    }

    private static Vec3 entityPoint(org.joml.Matrix4f frame, Vec3 point) {
        return new Vec3(frame.transformPosition(point.toVector3f()));
    }

    private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float s) {
        float u = 1 - s;
        return a.scale(u * u * u).add(b.scale(3 * u * u * s)).add(c.scale(3 * u * s * s)).add(d.scale(s * s * s));
    }

    /** a から d への三次曲線にそった、四角い断面の管（ケーブル）。 */
    private static void tube(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float radius, int rgb, int light) {
        PoseStack.Pose last = pose.last();
        int n = 12;
        Vec3 prev = a;
        Vec3[] previousCorners = null;
        Vec3 side = new Vec3(1, 0, 0);
        for (int i = 0; i <= n; i++) {
            float s = (float) i / n, u = 1 - s;
            Vec3 p = bezier(a, b, c, d, s);
            Vec3 dir = b.subtract(a).scale(u * u).add(c.subtract(b).scale(2 * u * s)).add(d.subtract(c).scale(s * s)).normalize();
            // 前の断面の向きを接線に沿って運び、隣り合う区間で同じ四隅を使う。
            side = side.subtract(dir.scale(dir.dot(side))).normalize();
            Vec3 s1 = side.scale(radius), s2 = dir.cross(side).scale(radius);
            Vec3[] corners = {s1.add(s2), s1.subtract(s2), s1.scale(-1).subtract(s2), s1.scale(-1).add(s2)};
            for (int k = 0; previousCorners != null && k < 4; k++) {
                Vec3 o0 = corners[k], o1 = corners[(k + 1) % 4];
                Vec3 p0 = prev.add(previousCorners[k]), p1 = prev.add(previousCorners[(k + 1) % 4]);
                Vec3 nrm = o0.add(o1).normalize();
                float nx = (float) nrm.x, ny = (float) nrm.y, nz = (float) nrm.z;
                FxDraw.vertex(vc, last, (float) p0.x, (float) p0.y, (float) p0.z, rgb, 255, light, nx, ny, nz);
                FxDraw.vertex(vc, last, (float) (p.x + o0.x), (float) (p.y + o0.y), (float) (p.z + o0.z), rgb, 255, light, nx, ny, nz);
                FxDraw.vertex(vc, last, (float) (p.x + o1.x), (float) (p.y + o1.y), (float) (p.z + o1.z), rgb, 255, light, nx, ny, nz);
                FxDraw.vertex(vc, last, (float) p1.x, (float) p1.y, (float) p1.z, rgb, 255, light, nx, ny, nz);
            }
            prev = p;
            previousCorners = corners;
        }
    }

    // ------------------------------------------------------------------ 放電（本体の足もとを原点にした世界の座標）

    static void renderFx(SparkAndroid spark, float partialTick, PoseStack pose, MultiBufferSource buffers, Quaternionf camera, Anchors anchors) {
        Vec3 base = spark.getPosition(partialTick);
        float age = spark.tickCount + partialTick;
        int frame = spark.tickCount;
        float yaw = Mth.rotLerp(partialTick, spark.yBodyRotO, spark.yBodyRot);
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        int state = spark.state();
        float t = spark.stateTime(partialTick);

        // アーク
        int flash = spark.arcFlash();
        if (flash > 0) {
            List<Vec3> path = spark.arcPath();
            float k = Mth.clamp((flash - partialTick) / SparkAndroid.FLASH, 0, 1);
            for (int i = 0; i + 1 < path.size(); i++) {
                Vec3 a = i == 0 && anchors != null ? anchors.right : path.get(i).subtract(base);
                Vec3 b = path.get(i + 1).subtract(base);
                bolt(pose, buffers, a, b, frame * 31 + i, 0.05F + 0.05F * k, (int) (240 * k), true);
            }
        }
        // 天雷の溜め: 頭上に電荷の玉が膨らみ、両手から細い放電が昇っていく
        if (state == SparkAndroid.STATE_TENRAI_CHARGE || state == SparkAndroid.STATE_TENRAI_STRIKE && t < SparkAndroid.STRIKE_HIT) {
            float charge = state == SparkAndroid.STATE_TENRAI_CHARGE ? t : SparkAndroid.TENRAI_CHARGE;
            float c = Mth.clamp(charge / SparkAndroid.TENRAI_CHARGE, 0, 1);
            Vec3 orb = new Vec3(0, SparkAndroid.orbHeight(charge), 0);
            VertexConsumer glow = GlowDepth.of(buffers, FxDraw.WHITE);
            pose.pushPose();
            pose.translate(orb.x, orb.y, orb.z);
            float flick = 0.85F + 0.15F * Mth.sin(age * 3.1F);
            FxDraw.billboard(pose, glow, camera, (0.4F + 1.2F * c) * flick, SparkAndroid.ACCENT, (int) (120 + 100 * c));
            FxDraw.billboard(pose, glow, camera, (0.15F + 0.5F * c) * flick, 0xFFFFFF, 235);
            pose.popPose();
            for (int side = -1; side <= 1; side += 2) {
                if (anchors == null) continue;
                Vec3 hand = side > 0 ? anchors.right : anchors.left;
                if ((frame + side) % 3 != 0) bolt(pose, buffers, hand, orb, frame * 7 + side, 0.03F + 0.03F * c, (int) (150 + 80 * c), false);
            }
            if (c > 0.4F && frame % 2 == 0) {
                // 溜まってくると、玉から周りへ小さな放電が散る
                double a = (frame * 2.39) % (Math.PI * 2);
                Vec3 tip = orb.add(Math.cos(a) * 1.2 * c, -0.3 + Math.sin(frame * 1.7) * 0.4, Math.sin(a) * 1.2 * c);
                bolt(pose, buffers, orb, tip, frame * 13, 0.025F, 180, false);
            }
        }
        // 天雷: 頭上の玉から、相手それぞれへ枝分かれした放電が落ちる
        float afterHit = t - SparkAndroid.STRIKE_HIT;
        if (state == SparkAndroid.STATE_TENRAI_STRIKE && afterHit >= 0 && afterHit < 10) {
            float k = 1 - afterHit / 10;
            Vec3 sky = new Vec3(0, SparkAndroid.TENRAI_HEIGHT, 0);
            List<Vec3> ends = spark.boltTargets();
            for (int i = 0; i < ends.size(); i++) {
                Vec3 end = ends.get(i).subtract(base);
                int seed = (frame / 2) * 17 + i * 101;
                bolt(pose, buffers, sky, end, seed, 0.08F + 0.1F * k, (int) (250 * k), true);
                // 枝: 途中から横へ短く
                Vec3 mid = sky.lerp(end, 0.45);
                Vec3 off = new Vec3(Math.sin(seed) * 0.9, -0.6, Math.cos(seed) * 0.9);
                bolt(pose, buffers, mid, mid.add(off), seed + 5, 0.04F * k + 0.02F, (int) (200 * k), false);
            }
            VertexConsumer glow = GlowDepth.of(buffers, FxDraw.WHITE);
            pose.pushPose();
            pose.translate(sky.x, sky.y, sky.z);
            FxDraw.billboard(pose, glow, camera, 1.6F * k + 0.3F, SparkAndroid.ACCENT, (int) (220 * k));
            FxDraw.billboard(pose, glow, camera, 0.8F * k + 0.1F, 0xFFFFFF, (int) (240 * k));
            pose.popPose();
        }
        // 電場: 縁をめぐる放電と、中で跳ねる小さな放電
        int left = spark.fieldLeft();
        if (left > 0) {
            Vec3 c = spark.fieldCenter().subtract(base).add(0, 0.08, 0);
            float fade = Mth.clamp(left / 12F, 0, 1);
            double r = SparkAndroid.FIELD_RADIUS;
            int segments = 16;
            int alpha = (int) (170 * fade);
            for (int i = 0; i < segments; i++) {
                if ((i + frame / 2) % 3 == 0) continue;     // ところどころ途切れて、めぐって見える
                double a0 = Math.PI * 2 * (i + age * 0.05) / segments, a1 = Math.PI * 2 * (i + 1 + age * 0.05) / segments;
                Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
                bolt(pose, buffers, p0, p1, frame * 3 + i, 0.035F, alpha, false);
            }
            for (int i = 0; i < 3; i++) {
                int seed = frame / 2 * 7 + i * 13;
                double a = (seed * 0.77) % (Math.PI * 2), d = r * (0.3 + 0.6 * ((seed * 0.37) % 1));
                Vec3 p = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                bolt(pose, buffers, p, p.add(Math.sin(seed) * 0.6, 0.35, Math.cos(seed) * 0.6), seed, 0.03F, alpha, false);
            }
        }
        // 給電・連携: 腰の機械から相手へ
        Entity link = spark.linkTarget();
        if (link != null && spark.linkKind() != SparkAndroid.LINK_NONE && frame % 3 != 2) {
            Vec3 from = anchors != null ? anchors.unit : spark.unit().subtract(spark.position());
            Vec3 to = link.getPosition(partialTick).add(0, link.getBbHeight() * 0.55, 0).subtract(base);
            boolean strong = spark.linkKind() != SparkAndroid.LINK_SUPPLY;
            bolt(pose, buffers, from, to, frame * 11, strong ? 0.04F : 0.025F, strong ? 210 : 150, false);
        }
        // 待機中の放電遊び: 顔の前に上げた右手の指先で、小さな火花がはじける
        if (anchors != null && state == SparkAndroid.STATE_PLAY && t > 8 && t < SparkAndroid.PLAY - 10 && frame % 4 != 3) {
            Vec3 hand = anchors.right;
            Vec3 tip = hand.add(right.scale(0.14)).add(0, 0.06 * Math.sin(age * 0.7), 0);
            bolt(pose, buffers, hand, tip, frame * 5, 0.02F, 220, false);
            VertexConsumer glow = GlowDepth.of(buffers, FxDraw.WHITE);
            pose.pushPose();
            pose.translate(tip.x, tip.y, tip.z);
            FxDraw.billboard(pose, glow, camera, 0.12F, SparkAndroid.ACCENT, 200);
            pose.popPose();
        }
        // 掌底: 突き出した掌の先で光がはじける
        if (anchors != null && state == SparkAndroid.STATE_PALM && t >= SparkAndroid.PALM_HIT && t < SparkAndroid.PALM_HIT + 4) {
            float k = 1 - (t - SparkAndroid.PALM_HIT) / 4;
            Vec3 palm = anchors.left;
            VertexConsumer glow = GlowDepth.of(buffers, FxDraw.WHITE);
            pose.pushPose();
            pose.translate(palm.x, palm.y, palm.z);
            FxDraw.billboard(pose, glow, camera, 0.9F * k + 0.2F, SparkAndroid.ACCENT, (int) (220 * k));
            FxDraw.billboard(pose, glow, camera, 0.4F * k + 0.1F, 0xFFFFFF, (int) (240 * k));
            pose.popPose();
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + frame;
                bolt(pose, buffers, palm, palm.add(fwd.scale(0.5)).add(Math.cos(a) * 0.5, Math.sin(a) * 0.5, 0), frame * 3 + i, 0.025F, (int) (200 * k), false);
            }
        }
    }

    /**
     * a から b へのジグザグの放電（白い芯と、型の色の光）。seed が同じなら同じ形（数 tick ごとに seed を変えて、形を揺らす）。
     * branch なら途中から短い枝を1本出す。
     */
    static void bolt(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, int seed, float width, int alpha, boolean branch) {
        if (alpha <= 0) return;
        Vec3 axis = b.subtract(a);
        double len = axis.length();
        if (len < 1e-3) return;
        Vec3 dir = axis.scale(1 / len);
        Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(up).normalize(), s2 = dir.cross(s1).normalize();
        int n = Mth.clamp((int) (len * 3), 3, 24);
        double amp = Math.min(0.35, len * 0.08);
        java.util.Random rnd = new java.util.Random(seed * 0x9E3779B97F4A7C15L);
        Vec3[] pts = new Vec3[n + 1];
        pts[0] = a;
        pts[n] = b;
        for (int i = 1; i < n; i++) {
            double taper = Math.sin(Math.PI * i / n);
            pts[i] = a.add(axis.scale((double) i / n))
                    .add(s1.scale((rnd.nextDouble() * 2 - 1) * amp * taper)).add(s2.scale((rnd.nextDouble() * 2 - 1) * amp * taper));
        }
        VertexConsumer glow = buffers.getBuffer(AndroidWeaponLight.GLOW);
        for (int i = 0; i < n; i++) {
            FxDraw.beam(pose.last(), glow, pts[i], pts[i + 1], width * 3.2F, SparkAndroid.ACCENT, (int) (alpha * 0.6F));
        }
        VertexConsumer core = buffers.getBuffer(AndroidWeaponLight.CORE);
        for (int i = 0; i < n; i++) {
            FxDraw.beam(pose.last(), core, pts[i], pts[i + 1], width, 0xFFFFF0, alpha);
        }
        if (branch && n > 4) {
            int at = n / 3 + rnd.nextInt(n / 3);
            Vec3 tip = pts[at].add(s1.scale((rnd.nextDouble() * 2 - 1) * len * 0.25)).add(s2.scale((rnd.nextDouble() * 2 - 1) * len * 0.25))
                    .add(dir.scale(len * 0.12));
            bolt(pose, buffers, pts[at], tip, seed + 77, width * 0.6F, (int) (alpha * 0.8F), false);
        }
    }
}
