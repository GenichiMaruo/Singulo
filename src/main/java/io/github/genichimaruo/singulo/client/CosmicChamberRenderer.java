package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 重力懸垂タンク・核パスタ積層蓄電庫・潮汐紡糸機の中身。座標は制御卓の真ん中の真下を原点に、+z が奥（CosmicLayouts の z がそのまま
 * z、x は中央からの距離、y は高さ）。
 * <ul>
 *   <li>タンク: 液体が冠から涙の形にぶら下がり、量で伸びて膨らむ。入ると下から滴が昇り、出すと先から滴が落ちる</li>
 *   <li>蓄電庫: 充電につれて上下の押し板が締まり、中の核パスタが粒 → 棒 → 板と形を変える（中性子星の地殻で起きる相転移）</li>
 *   <li>紡糸機: 本物のブラックホールの潮汐力で材料を細く引き伸ばし、絞りリングに通してスプールに巻く。巻いた分だけ太る</li>
 * </ul>
 * 画面全体を光らせることはしない。遠いときは粒の数や滴を間引く。
 */
public final class CosmicChamberRenderer implements BlockEntityRenderer<CosmicChamberBlockEntity> {
    private static final int FULL = LightTexture.FULL_BRIGHT;

    /** 画面だけが持つ、なめらかに動かすための状態。 */
    private static final class Visual {
        float lastTime = -1;
        /** なめらかにした量（0〜1）。 */
        float fill = -1;
        /** 液体が増えていれば正、減っていれば負（滴の向き）。 */
        float flow;
        float spin;
        float swirl = 1;
        float progress;
        /** 前に見たときに核があったか（まだ見ていなければ null）。 */
        Boolean lastCore;
        /** 核ができてからの秒数。 */
        float coreAge = 100;
    }

    private final Map<CosmicChamberBlockEntity, Visual> visuals = new WeakHashMap<>();

    public CosmicChamberRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public boolean shouldRenderOffScreen(CosmicChamberBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(CosmicChamberBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(16);
    }

    @Override
    public void render(CosmicChamberBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || be.status() != 0) {
            return;
        }
        float time = (be.getLevel().getGameTime() % 24000 + partial) / 20F;
        Visual v = visuals.computeIfAbsent(be, b -> new Visual());
        float dt = v.lastTime < 0 ? 0 : Mth.clamp(time - v.lastTime, 0, 0.25F);
        v.lastTime = time;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean near = camera.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) < 56 * 56;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-be.back().toYRot()));
        ports(be, pose, buffers, time);
        switch (be.kind()) {
            case CosmicChamberBlockEntity.RESERVOIR -> reservoir(be, v, pose, buffers, light, time, dt, near);
            case CosmicChamberBlockEntity.ACCUMULATOR -> accumulator(be, v, pose, buffers, light, time, dt, near);
            default -> spindle(be, v, pose, buffers, light, time, dt, near);
        }
        pose.popPose();
    }

    /** ポートの正面の印: 入力は水色の下向き、出力は琥珀の上向き、両方は白の横棒。光が向きへ流れる。 */
    private static void ports(CosmicChamberBlockEntity be, PoseStack pose, MultiBufferSource buffers, float time) {
        VertexConsumer glow = glow(buffers);
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? 1 : -1;
            int mode = be.portMode(i);
            int rgb = mode == CosmicChamberBlockEntity.PORT_INPUT ? 0x8FE8FF : mode == CosmicChamberBlockEntity.PORT_OUTPUT ? 0xFFC070 : 0xFFFFFF;
            float z = -0.52F;
            if (mode == CosmicChamberBlockEntity.PORT_BOTH) {
                FxDraw.box(pose, glow, x - 0.25F, 0.46F, z, x + 0.25F, 0.54F, z + 0.01F, FULL, rgb, 220);
                continue;
            }
            float dir = mode == CosmicChamberBlockEntity.PORT_INPUT ? -1 : 1;
            for (int k = 0; k < 2; k++) {
                float f = (time * 0.8F + k * 0.5F) % 1;
                float y = 0.5F + dir * (f - 0.5F) * 0.5F;
                int alpha = (int) (235 * Mth.sin(f * Mth.PI));
                Vec3 tip = new Vec3(x, y + dir * 0.09F, z);
                FxDraw.beam(pose.last(), glow, new Vec3(x - 0.16F, y - dir * 0.05F, z), tip, 0.05F, rgb, alpha);
                FxDraw.beam(pose.last(), glow, new Vec3(x + 0.16F, y - dir * 0.05F, z), tip, 0.05F, rgb, alpha);
            }
        }
    }

    /** ローカルの (0, y, z) のワールドの位置。 */
    private static Vec3 world(CosmicChamberBlockEntity be, double y, double z) {
        return Vec3.atBottomCenterOf(be.getBlockPos()).add(Vec3.atLowerCornerOf(be.back().getNormal()).scale(z)).add(0, y, 0);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float approach(float from, float to, float dt, float rate) {
        return from < 0 ? to : Mth.lerp(1 - (float) Math.exp(-dt * rate), from, to);
    }

    private static VertexConsumer glow(MultiBufferSource buffers) {
        return GlowDepth.of(buffers, FxDraw.WHITE);
    }

    private static VertexConsumer solid(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE));
    }

    /** 水平の輪。 */
    private static void hoop(PoseStack pose, VertexConsumer vc, float y, float z, float inner, float outer, int rgb, int alpha) {
        pose.pushPose();
        pose.translate(0, y, z);
        FxDraw.ring(pose.last(), vc, inner, outer, rgb, alpha, alpha / 3);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int rgb, int alpha,
                               int light, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    // ------------------------------------------------------------------ 重力懸垂タンク

    /** 液体の上端（冠の下面）と中心。 */
    private static final float CROWN_Y = 8;
    private static final float AXIS_Z = 3;

    /**
     * ぶら下がった滴の半径。s は上端 0 〜 先端 1。上は細い首で冠に下がり、7割ほどの所でいちばん膨らみ、先は丸く閉じる。
     * 表面をゆっくり波が下りていく。
     */
    private static float dropRadius(float s, float angle, float time, float radius, float wobble) {
        float neck = 0.5F + 0.5F * smooth(s / 0.7F);
        float tip = s <= 0.7F ? 1 : Mth.sqrt(Math.max(0, 1 - Mth.square((s - 0.7F) / 0.3F)));
        float wave = wobble * (Mth.sin(s * 11 - time * 1.7F + angle * 2) + 0.6F * Mth.sin(s * 5 + time * 0.8F - angle * 3));
        return Math.max(0, radius * neck * tip + wave * tip * radius);
    }

    private void reservoir(CosmicChamberBlockEntity be, Visual v, PoseStack pose, MultiBufferSource buffers, int light,
                           float time, float dt, boolean near) {
        FluidStack fluid = be.tank().getFluid();
        float target = fluid.getAmount() / (float) CosmicChamberBlockEntity.FLUID_CAPACITY;
        float before = v.fill;
        v.fill = approach(before, target, dt, 2.5F);
        if (before >= 0 && dt > 0) {
            float rate = (v.fill - before) / dt;
            v.flow = approach(v.flow, Mth.clamp(rate * 3000, -1, 1), dt, 2);
        }
        float fill = v.fill;
        // ごくわずかでも見えるよう、長さと太さは量の平方根ぎみに伸ばす
        float size = (float) Math.pow(fill, 0.55);
        float length = fluid.isEmpty() ? 0 : Mth.lerp(size, 0.5F, 6.4F);
        float radius = fluid.isEmpty() ? 0 : Mth.lerp(size, 0.32F, 2.05F);
        float tipY = CROWN_Y - length;
        int tint = fluid.isEmpty() ? 0x72DFEF : FluidDraw.tint(fluid.getFluid()) & 0xFFFFFF;
        boolean gas = !fluid.isEmpty() && FluidDraw.gaseous(fluid.getFluid());

        if (!fluid.isEmpty()) {
            TextureAtlasSprite sprite = FluidDraw.sprite(fluid.getFluid());
            // 表の面だけ描く（裏面を描くと、奥の面が手前の面に重なって見える）
            VertexConsumer liquid = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
            int fluidLight = LightTexture.pack(Math.max(fluid.getFluid().getFluidType().getLightLevel(), Math.max(11, LightTexture.block(light))),
                    LightTexture.sky(light));
            pendant(pose, liquid, sprite, length, radius, time, tint, gas ? 130 : 225, fluidLight, gas ? 0.07F : 0.028F,
                    near ? 24 : 12, near ? 18 : 8);
        }

        VertexConsumer glow = glow(buffers);
        // 支える重力場の輪: 液体のまわりをゆっくり下りていく
        int hoops = near ? 4 : 2;
        for (int i = 0; i < hoops; i++) {
            float k = (time * 0.09F + i / (float) hoops) % 1;
            float fade = Mth.sin(k * Mth.PI);
            float y = fluid.isEmpty() ? CROWN_Y - 0.4F - 6 * k : Mth.lerp(k, CROWN_Y - 0.2F, tipY - 0.2F);
            float r = fluid.isEmpty() ? 1.6F : dropRadius(k, 0, time, radius, 0) + 0.2F;
            hoop(pose, glow, y, AXIS_Z, r, r + 0.05F + 0.04F * fade, 0x8FE8FF, (int) ((fluid.isEmpty() ? 50 : 150) * fade));
        }
        // 冠の下の8つの発光点と、土台から昇る細い光の柱
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8 + Mth.PI / 8;
            float x = 2.05F * Mth.cos(a);
            float z = AXIS_Z + 2.05F * Mth.sin(a);
            int alpha = (int) (130 + 90 * Mth.sin(time * 1.3F + i));
            FxDraw.box(pose, glow, x - 0.07F, 7.86F, z - 0.07F, x + 0.07F, 7.99F, z + 0.07F, FULL, 0xBDF2FF, alpha);
        }
        float column = 0.03F + 0.015F * Mth.sin(time * 2.1F);
        float columnTop = fluid.isEmpty() ? CROWN_Y - 0.5F : tipY;
        FxDraw.beam(pose.last(), glow, new Vec3(0, 1, AXIS_Z), new Vec3(0, columnTop, AXIS_Z), column, 0x8FE8FF, fluid.isEmpty() ? 50 : 110);
        hoop(pose, glow, 1.02F, AXIS_Z, 0.1F, 0.55F + 0.1F * Mth.sin(time * 1.1F), 0x8FE8FF, 170);

        // 滴: 入っているときは土台から先端へ昇り、出しているときは先端から土台へ落ちる
        if (near && !fluid.isEmpty() && Math.abs(v.flow) > 0.08F) {
            VertexConsumer drops = glow(buffers);
            boolean rising = v.flow > 0;
            int count = 2 + (int) (Math.abs(v.flow) * 5);
            for (int i = 0; i < count; i++) {
                float seed = i * 2.399F;
                float k = (time * (rising ? 0.55F : 0.9F) + i / (float) count) % 1;
                float eased = rising ? k * k : k * k * k * 0.6F + k * 0.4F;
                float y = rising ? Mth.lerp(eased, 1.1F, tipY) : Mth.lerp(eased, tipY, 1.1F);
                float spread = 0.1F + 0.42F * (rising ? 1 - eased : eased);
                float x = spread * Mth.cos(seed + time * 0.4F);
                float z = AXIS_Z + spread * Mth.sin(seed + time * 0.4F);
                float s = 0.05F + 0.035F * Mth.sin(k * Mth.PI);
                pose.pushPose();
                pose.translate(x, y, z);
                FxDraw.octahedron(pose.last(), drops, s, s * 1.5F, tint, 235);
                pose.popPose();
                if (!rising && k > 0.96F) {
                    hoop(pose, drops, 1.04F, AXIS_Z, 0.3F, 0.3F + (k - 0.96F) * 12, tint, 170);
                }
            }
        }
    }

    /** ぶら下がった滴の表面。流体の絵を4マスごとに繰り返して貼る。 */
    private static void pendant(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite, float length, float radius, float time,
                                int tint, int alpha, int light, float wobble, int segments, int rings) {
        PoseStack.Pose p = pose.last();
        float u0 = sprite.getU0();
        float du = (sprite.getU1() - u0) / 4;
        float v0 = sprite.getV0();
        float dv = (sprite.getV1() - v0) / 4;
        // 先へいくほど少しふらつく（液体の重さで揺れる）
        float swayX = 0.05F * Mth.sin(time * 0.55F);
        float swayZ = 0.05F * Mth.cos(time * 0.43F);
        for (int j = 0; j < rings; j++) {
            float s0 = j / (float) rings;
            float s1 = (j + 1) / (float) rings;
            float y0 = CROWN_Y - length * s0;
            float y1 = CROWN_Y - length * s1;
            // 上は冠の影で暗く、先端は光を集めて明るい
            int top = FxDraw.lerpRgb(tint, 0xFFFFFF, 0.03F + 0.25F * s0 * s0);
            int bottom = FxDraw.lerpRgb(tint, 0xFFFFFF, 0.03F + 0.25F * s1 * s1);
            for (int i = 0; i < segments; i++) {
                float a0 = i * Mth.TWO_PI / segments;
                float a1 = (i + 1) * Mth.TWO_PI / segments;
                float r00 = dropRadius(s0, a0, time, radius, wobble);
                float r01 = dropRadius(s0, a1, time, radius, wobble);
                float r10 = dropRadius(s1, a0, time, radius, wobble);
                float r11 = dropRadius(s1, a1, time, radius, wobble);
                float o0 = s0 * s0;
                float o1 = s1 * s1;
                float us = u0 + du * (i % 4);
                float vs = v0 + dv * (j % 4);
                float c0 = Mth.cos(a0);
                float n0 = Mth.sin(a0);
                float c1 = Mth.cos(a1);
                float n1 = Mth.sin(a1);
                vertex(vc, p, swayX * o0 + c0 * r00, y0, AXIS_Z + swayZ * o0 + n0 * r00, us, vs, top, alpha, light, c0, 0, n0);
                vertex(vc, p, swayX * o0 + c1 * r01, y0, AXIS_Z + swayZ * o0 + n1 * r01, us + du, vs, top, alpha, light, c1, 0, n1);
                vertex(vc, p, swayX * o1 + c1 * r11, y1, AXIS_Z + swayZ * o1 + n1 * r11, us + du, vs + dv, bottom, alpha, light, c1, 0, n1);
                vertex(vc, p, swayX * o1 + c0 * r10, y1, AXIS_Z + swayZ * o1 + n0 * r10, us, vs + dv, bottom, alpha, light, c0, 0, n0);
            }
        }
    }

    // ------------------------------------------------------------------ 核パスタ積層蓄電庫

    /** 核パスタの色: 少ないときは冷たい紫、半ばでマゼンタ、満ちると白熱した金。 */
    private static int pastaColor(float fill, float shade) {
        int base = fill < 0.5F ? FxDraw.lerpRgb(0x6A4CFF, 0xE85AC0, fill * 2) : FxDraw.lerpRgb(0xE85AC0, 0xFFE0A0, (fill - 0.5F) * 2);
        return FxDraw.lerpRgb(base, 0x201030, shade);
    }

    private void accumulator(CosmicChamberBlockEntity be, Visual v, PoseStack pose, MultiBufferSource buffers, int light,
                             float time, float dt, boolean near) {
        float target = be.stored() / (float) be.capacity();
        v.fill = approach(v.fill, target, dt, 2);
        float fill = v.fill;
        float in = be.lastIn() / 2000F;
        float out = be.lastOut() / 2000F;
        float ease = smooth(fill);
        // 押し板: 下は y = 1〜、上は y = 8 から締まっていく。電力が出入りしている間はわずかに脈打つ
        float pulse = (in > 0 || out > 0) ? 0.02F * Mth.sin(time * 9) : 0;
        float bottom = Mth.lerp(ease, 1.2F, 2.7F) + pulse;
        float top = Mth.lerp(ease, 7.8F, 6.3F) - pulse;

        VertexConsumer solid = solid(buffers);
        int plate = 0x332E48;
        for (int side = 0; side < 2; side++) {
            float y0 = side == 0 ? bottom - 0.26F : top;
            float y1 = side == 0 ? bottom : top + 0.26F;
            FxDraw.box(pose, solid, -2.4F, y0, 0.6F, 2.4F, y1, 5.4F, light, plate);
            // 押し板を動かす4本のロッド
            for (int i = 0; i < 4; i++) {
                float x = i % 2 == 0 ? -1.5F : 1.5F;
                float z = i < 2 ? 1.5F : 4.5F;
                float rod0 = side == 0 ? 1 : top;
                float rod1 = side == 0 ? bottom : 8;
                FxDraw.box(pose, solid, x - 0.12F, rod0, z - 0.12F, x + 0.12F, rod1, z + 0.12F, light, 0x7E7A9E);
            }
        }

        // 核パスタは固体に見えるので、光ではなく深度を書く描き方で明るく描く（奥の水や雲や装置が透けないように）
        pasta(pose, solid, fill, bottom, top, time, near);
        VertexConsumer glow = glow(buffers);

        // 押し板の縁の光（量とともに明るくなる）
        int edge = pastaColor(fill, 0);
        for (int side = 0; side < 2; side++) {
            float y = side == 0 ? bottom + 0.01F : top - 0.03F;
            int alpha = (int) (90 + 150 * fill);
            FxDraw.box(pose, glow, -2.42F, y, 0.58F, 2.42F, y + 0.02F, 0.66F, FULL, edge, alpha);
            FxDraw.box(pose, glow, -2.42F, y, 5.34F, 2.42F, y + 0.02F, 5.42F, FULL, edge, alpha);
            FxDraw.box(pose, glow, -2.42F, y, 0.58F, -2.34F, y + 0.02F, 5.42F, FULL, edge, alpha);
            FxDraw.box(pose, glow, 2.34F, y, 0.58F, 2.42F, y + 0.02F, 5.42F, FULL, edge, alpha);
        }

        // 四隅の導体を走る光: 入る電力は上から下へ押し板へ、出る電力は下へ流れて制御卓へ向かう
        int bolts = near ? 3 : 1;
        for (int c = 0; c < 4; c++) {
            float x = c % 2 == 0 ? -3.0F : 3.0F;
            float z = c < 2 ? 0 : 6;
            for (int i = 0; i < bolts; i++) {
                if (in > 0) {
                    float k = (time * (0.7F + 1.2F * in) + i / (float) bolts + c * 0.17F) % 1;
                    float y = Mth.lerp(k, 8, 1);
                    FxDraw.box(pose, glow, x - 0.36F, y, z - 0.36F, x + 0.36F, y + 0.16F, z + 0.36F, FULL, 0x9CE8FF, (int) (220 * Mth.sin(k * Mth.PI)));
                }
                if (out > 0) {
                    float k = (time * (0.9F + 1.6F * out) + i / (float) bolts + c * 0.31F) % 1;
                    float y = Mth.lerp(k, 7, 0.5F);
                    FxDraw.box(pose, glow, x - 0.36F, y, z - 0.36F, x + 0.36F, y + 0.2F, z + 0.36F, FULL, 0xFFD890, (int) (235 * Mth.sin(k * Mth.PI)));
                }
            }
        }
        // 充電中: 導体から押し板へ小さな稲妻が飛ぶ
        if (near && in > 0) {
            int seed = (int) (time * 12);
            for (int a = 0; a < 2; a++) {
                java.util.Random r = new java.util.Random(seed * 31L + a * 7L + be.getBlockPos().asLong());
                float x = r.nextBoolean() ? -2.95F : 2.95F;
                float z = r.nextBoolean() ? 0.05F : 5.95F;
                float y = 2 + r.nextFloat() * 5;
                Vec3 from = new Vec3(x, y, z);
                Vec3 to = new Vec3(x * 0.8F, r.nextBoolean() ? bottom : top, 0.8F + r.nextFloat() * 4.4F);
                Vec3 mid = from.lerp(to, 0.5).add((r.nextFloat() - 0.5F) * 0.5F, (r.nextFloat() - 0.5F) * 0.5F, (r.nextFloat() - 0.5F) * 0.5F);
                FxDraw.beam(pose.last(), glow, from, mid, 0.03F, 0xBFEFFF, 210);
                FxDraw.beam(pose.last(), glow, mid, to, 0.03F, 0xBFEFFF, 210);
            }
        }
        // 詰まった核物質はまわりの景色をわずかに歪める
        if (fill > 0.05F) {
            GravitationalLensing.addDistortion(world(be, 4.5, 3), 0.1F + 0.2F * fill, 0.1F + 0.2F * fill, 2.9F);
        }
    }

    /**
     * 押し板の間の核パスタ。fill が増えると、格子に浮かぶ粒（ニョッキ）が横につながって棒（スパゲッティ）になり、
     * 棒が奥行きへ広がって板（ラザニア）になる。つながった所は1つの箱にまとめて、描く数を減らす。
     */
    private static void pasta(PoseStack pose, VertexConsumer vc, float fill, float bottom, float top, float time, boolean near) {
        final int layers = 5;
        final int cells = near ? 5 : 3;
        final float span = 2.1F;
        float pitch = span * 2 / cells;
        float rod = smooth((fill - 0.22F) / 0.26F);
        float sheet = smooth((fill - 0.58F) / 0.28F);
        float room = (top - bottom) / layers;
        float jitter = (1 - rod) * 0.09F;
        for (int l = 0; l < layers; l++) {
            float y = bottom + room * (l + 0.5F);
            float halfY = Mth.lerp(sheet, Mth.lerp(rod, 0.17F, 0.13F), Math.min(0.09F, room * 0.2F));
            // 1枚おきに暗くして、重なった層が読めるようにする
            int color = pastaColor(fill, 0.38F * (l % 2));
            int alpha = 235;
            if (sheet >= 1) {
                FxDraw.box(pose, vc, -span, y - halfY, 3 - span, span, y + halfY, 3 + span, FULL, color, alpha);
                continue;
            }
            for (int r = 0; r < cells; r++) {
                float z = 3 - span + pitch * (r + 0.5F);
                float halfZ = Mth.lerp(sheet, Mth.lerp(rod, 0.17F, 0.12F), pitch / 2);
                if (rod >= 1) {
                    FxDraw.box(pose, vc, -span, y - halfY, z - halfZ, span, y + halfY, z + halfZ, FULL, color, alpha);
                    continue;
                }
                for (int c = 0; c < cells; c++) {
                    float x = -span + pitch * (c + 0.5F);
                    float phase = (l * 3.1F + r * 1.7F + c * 2.3F);
                    float jx = jitter * Mth.sin(time * 1.1F + phase);
                    float jy = jitter * Mth.sin(time * 0.9F + phase * 1.3F);
                    float jz = jitter * Mth.cos(time * 1.3F + phase * 0.7F);
                    if (rod <= 0) {
                        pose.pushPose();
                        pose.translate(x + jx, y + jy, z + jz);
                        FxDraw.octahedron(pose.last(), vc, 0.2F, 0.2F, color, alpha);
                        pose.popPose();
                    } else {
                        float halfX = Mth.lerp(rod, 0.17F, pitch / 2);
                        FxDraw.box(pose, vc, x + jx - halfX, y + jy - halfY, z + jz - halfZ, x + jx + halfX, y + jy + halfY, z + jz + halfZ, FULL, color, alpha);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 潮汐紡糸機

    private static final float SPIN_Y = 4.5F;
    private static final float HOLE_Z = 3;
    private static final float SPOOL_Y = 3.5F;
    private static final float SPOOL_Z = 12;
    private static final float GUIDE_Z = 10.5F;
    private static final float BILLET_Z = 5.4F;
    private static final int THREAD = 0xFFEBC0;
    private static final int THREAD_HOT = 0xFFFFFF;
    /** ブラックホールの影の半径と降着円盤（胴の輪の内側に収める）。 */
    private static final float SHADOW = 0.62F;
    private static final float DISK_OUTER = 1.75F;
    private static final float REGION = 2.55F;
    /** 開く演出: 種が縮む秒数と、ブラックホールが広がる秒数。 */
    private static final float SEED_FALL = 1.3F;
    private static final float HOLE_OPEN = 1.1F;

    private void spindle(CosmicChamberBlockEntity be, Visual v, PoseStack pose, MultiBufferSource buffers, int light,
                         float time, float dt, boolean near) {
        boolean working = !be.reserved().isEmpty();
        float target = be.duration() > 0 ? be.progress() / (float) be.duration() : 0;
        float before = v.progress;
        // 巻き終わるとスプールは新しくなる（戻るときはなめらかにしない）
        v.progress = target < before ? target : approach(before, target, dt, 3);
        float k = v.progress;
        boolean moving = working && target > before + 1.0E-6F || working && be.duration() > 0 && be.progress() < be.duration() && be.stored() > 0;
        float speed = moving ? 2.4F + 1.2F * k : 0;
        v.spin += speed * dt;
        // 初めて見たときから核があれば、開く演出はしない（読み込むたびに開かないように）
        if (v.lastCore != null && be.core() && !v.lastCore) {
            v.coreAge = 0;
        }
        v.lastCore = be.core();
        v.coreAge += dt;

        if (be.core() && v.coreAge >= SEED_FALL) {
            float activity = working ? 1 : 0.35F;
            v.swirl = (v.swirl + dt * (0.5F + 1.6F * activity)) % (Mth.TWO_PI / 3);
            hole(be, v, pose, buffers, activity);
        }

        // スプール: 受け台の間で x 軸のまわりに回る。巻いた分だけ太る
        float wound = 0.5F + 0.95F * k;
        VertexConsumer solid = solid(buffers);
        spool(pose, solid, wound, v.spin, light, working);
        // 綾振りの案内: 糸を左右に振って均等に巻く。レールの上を動く
        float guide = moving || working ? 1.15F * tri(v.spin * 0.35F) : 0;
        FxDraw.box(pose, solid, -2.3F, 5.68F, GUIDE_Z - 0.08F, 2.3F, 5.84F, GUIDE_Z + 0.08F, light, 0x4E4A68);
        FxDraw.box(pose, solid, guide - 0.16F, 4.75F, GUIDE_Z - 0.12F, guide + 0.16F, 5.68F, GUIDE_Z + 0.12F, light, 0x8884A8);

        VertexConsumer glow = glow(buffers);
        Vec3 ring2 = new Vec3(0, SPIN_Y, 7);
        Vec3 ring1 = new Vec3(0, SPIN_Y, 9);
        Vec3 eye = new Vec3(guide, 4.7F, GUIDE_Z);
        Vec3 onSpool = new Vec3(guide, SPOOL_Y + wound, SPOOL_Z - 0.2F);
        // 絞りリングの内側の光: 糸が通っている間は強い
        int ringAlpha = working ? 200 : be.core() ? 70 : 25;
        ringGlow(pose, glow, 7, 0.82F, 1.38F, ringAlpha, time * 0.8F);
        ringGlow(pose, glow, 9, 0.2F, 0.5F, ringAlpha, -time * 1.3F);
        FxDraw.box(pose, glow, guide - 0.05F, 4.62F, GUIDE_Z - 0.05F, guide + 0.05F, 4.76F, GUIDE_Z + 0.05F, FULL, working ? THREAD_HOT : 0x8E8AAE, working ? 240 : 120);

        if (working) {
            // 入れ物を取り直すと前のものは閉じるので、材料を描いたら光の入れ物を取り直す
            billet(pose, solid(buffers), k, time);
            glow = glow(buffers);
            Vec3 start = new Vec3(0, SPIN_Y, BILLET_Z + 0.75F - 0.25F * k);
            // 潮汐で伸びた糸: 材料の側は太く、リングを通るたびに細くなる
            thread(pose, glow, start, ring2, 0.15F, 0.08F, time, moving);
            thread(pose, glow, ring2, ring1, 0.08F, 0.05F, time, moving);
            thread(pose, glow, ring1, eye, 0.05F, 0.04F, time, moving);
            thread(pose, glow, eye, onSpool, 0.04F, 0.04F, time, moving);
            // 材料の奥の端からブラックホールへ落ちていく筋（らせんで円盤に合流する）
            Vec3 back = new Vec3(0, SPIN_Y, BILLET_Z - 0.75F + 0.3F * k);
            int strands = near ? 3 : 1;
            for (int s = 0; s < strands; s++) {
                float a = time * 2.2F + s * Mth.TWO_PI / strands;
                Vec3 prev = back;
                for (int i = 1; i <= 6; i++) {
                    float f = i / 6F;
                    float r = 0.22F + 0.55F * Mth.sin(f * Mth.PI);
                    float ang = a + f * 5;
                    Vec3 next = new Vec3(r * Mth.cos(ang) * (1 - f * f * 0.6F), SPIN_Y + r * Mth.sin(ang) * (1 - f * f * 0.6F), Mth.lerp(f, (float) back.z, HOLE_Z + 0.2F));
                    FxDraw.beam(pose.last(), glow, prev, next, 0.07F * (1 - f * 0.6F), FxDraw.lerpRgb(THREAD, 0xB070FF, f), (int) (220 * (1 - f * 0.5F)));
                    prev = next;
                }
            }
        }
        if (be.core() && v.coreAge < SEED_FALL + HOLE_OPEN) {
            opening(pose, buffers, v.coreAge, time);
        }
    }

    /**
     * ブラックホールが開く演出（小さく、輪の中だけ）: 種が輪の中心へ漂いながら光を吸って縮み、一点につぶれた瞬間に衝撃の輪が走って、
     * 影と円盤が中心から広がる。画面全体は光らせない。
     */
    private static void opening(PoseStack pose, MultiBufferSource buffers, float age, float time) {
        VertexConsumer glow = glow(buffers);
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        if (age < SEED_FALL) {
            float a = age / SEED_FALL;
            float size = 0.3F * (1 - a * a * a);
            pose.pushPose();
            pose.translate(0, SPIN_Y, HOLE_Z);
            pose.mulPose(Axis.YP.rotation(time * (2 + 10 * a)));
            FxDraw.octahedron(pose.last(), glow, size, size * 1.4F, 0xD0B0FF, 245);
            pose.popPose();
            // 輪の内側から中心へ集まる光の筋
            for (int i = 0; i < 8; i++) {
                float ang = i * Mth.TWO_PI / 8 + time * 0.8F;
                float k = (a * 1.6F + i * 0.13F) % 1;
                float r = 2.3F * (1 - k);
                Vec3 at = new Vec3(r * Mth.cos(ang), SPIN_Y + r * Mth.sin(ang), HOLE_Z);
                Vec3 toward = new Vec3(0, SPIN_Y, HOLE_Z).subtract(at).normalize().scale(0.3F);
                FxDraw.beam(pose.last(), glow, at, at.add(toward), 0.05F, 0xC8B8FF, (int) (220 * a * Mth.sin(k * Mth.PI)));
            }
            hoopZ(pose, glow, 2.45F - 2.2F * a * a, 0.06F, 0xC8B8FF, (int) (120 + 110 * a));
        } else {
            float a = (age - SEED_FALL) / HOLE_OPEN;
            hoopZ(pose, glow, 0.4F + 2.0F * a, 0.1F + 0.12F * (1 - a), 0xF0E4FF, (int) (240 * (1 - a)));
            pose.pushPose();
            pose.translate(0, SPIN_Y, HOLE_Z);
            FxDraw.billboard(pose, glow, camera, 1.5F * (1 - a) * (1 - a), 0xF4ECFF, (int) (235 * (1 - a)));
            pose.popPose();
        }
    }

    /** 軸に垂直な輪（胴の位置）。 */
    private static void hoopZ(PoseStack pose, VertexConsumer vc, float radius, float width, int rgb, int alpha) {
        pose.pushPose();
        pose.translate(0, SPIN_Y, HOLE_Z);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        FxDraw.ring(pose.last(), vc, radius, radius + width, rgb, alpha, alpha / 4);
        pose.popPose();
    }

    /** -1〜1 を往復する三角波（綾振りの案内）。 */
    private static float tri(float x) {
        float p = x / Mth.TWO_PI;
        p -= Mth.floor(p);
        return p < 0.5F ? 4 * p - 1 : 3 - 4 * p;
    }

    /** 胴の中のブラックホール。円盤は軸に垂直で、手前からも奥からも見える。レンズを掛けられないときはワールドに描く。 */
    private static void hole(CosmicChamberBlockEntity be, Visual v, PoseStack pose, MultiBufferSource buffers, float activity) {
        float horizon = SHADOW / GravitationalLensing.SHADOW_PER_HORIZON;
        float radius = horizon * GravitationalLensing.EINSTEIN_PER_HORIZON;
        Vec3 axis = Vec3.atLowerCornerOf(be.back().getNormal());
        Vec3 center = world(be, SPIN_Y, HOLE_Z);
        float appear = smooth((v.coreAge - SEED_FALL) / HOLE_OPEN);
        if (GravitationalLensing.addBlackHole(center, radius * appear, 1, REGION, axis, SHADOW * 1.15F, DISK_OUTER, 0.45F,
                (0.5F + 1.1F * activity) * appear, Math.max(v.swirl, 1.0E-3F))) {
            return;
        }
        VertexConsumer solid = solid(buffers);
        pose.pushPose();
        pose.translate(0, SPIN_Y, HOLE_Z);
        FxDraw.sphere(pose.last(), solid, SHADOW * 0.75F * appear, 0x000000, FULL);
        VertexConsumer glow = glow(buffers);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        FxDraw.ring(pose.last(), glow, SHADOW * 1.15F, DISK_OUTER, 0xFFC890, (int) (160 + 80 * activity), 20);
        pose.popPose();
        GravitationalLensing.add(center, radius, 1, REGION);
    }

    private static void ringGlow(PoseStack pose, VertexConsumer vc, float z, float inner, float outer, int alpha, float angle) {
        pose.pushPose();
        pose.translate(0, SPIN_Y, z);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotation(angle));
        FxDraw.ring(pose.last(), vc, inner, outer, 0xC8B8FF, alpha / 3, alpha);
        pose.popPose();
    }

    /** 引き伸ばされている材料: 奥（ブラックホール側）も手前も細く伸び、進むほど全体が痩せる。 */
    private static void billet(PoseStack pose, VertexConsumer solid, float k, float time) {
        final int slices = 7;
        float half = 0.85F - 0.25F * k;
        float body = 0.48F * (1 - 0.8F * k) + 0.06F;
        for (int i = 0; i < slices; i++) {
            float s0 = i / (float) slices;
            float s1 = (i + 1) / (float) slices;
            float mid = (s0 + s1) / 2 * 2 - 1;
            float h = body * (1 - 0.85F * mid * mid);
            float shake = 0.012F * Mth.sin(time * 23 + i * 2);
            FxDraw.box(pose, solid, -h + shake, SPIN_Y - h, BILLET_Z - half + 2 * half * s0, h + shake, SPIN_Y + h, BILLET_Z - half + 2 * half * s1,
                    LightTexture.pack(15, 15), FxDraw.lerpRgb(0x4A4060, 0xF0B070, 0.25F + 0.75F * k));
        }
    }

    /** 糸の1区間。流れの向きへ小さな光の粒が走る。 */
    private static void thread(PoseStack pose, VertexConsumer vc, Vec3 from, Vec3 to, float w0, float w1, float time, boolean moving) {
        Vec3 mid = from.lerp(to, 0.5);
        FxDraw.beam(pose.last(), vc, from, mid, w0, THREAD, 235);
        FxDraw.beam(pose.last(), vc, mid, to, (w0 + w1) / 2, THREAD, 235);
        if (moving) {
            for (int i = 0; i < 3; i++) {
                float k = (time * 0.8F + i / 3F) % 1;
                Vec3 at = from.lerp(to, k);
                FxDraw.beam(pose.last(), vc, at, at.add(to.subtract(from).normalize().scale(0.14F)), w0 * 1.5F + 0.01F, THREAD_HOT, (int) (255 * Mth.sin(k * Mth.PI)));
            }
        }
    }

    /** スプール: 2枚のつばと、巻かれた糸の胴。縞を付けて回っているのが分かるようにする。 */
    private static void spool(PoseStack pose, VertexConsumer solid, float wound, float angle, int light, boolean working) {
        cylinder(pose, solid, -1.45F, -1.3F, 1.72F, angle, 0x6A668A, 0x8A86AC, light, 255);
        cylinder(pose, solid, 1.3F, 1.45F, 1.72F, angle, 0x6A668A, 0x8A86AC, light, 255);
        cylinder(pose, solid, -1.3F, 1.3F, 0.42F, angle, 0x2A2738, 0x35324A, light, 255);
        cylinder(pose, solid, -1.5F, 1.5F, 0.16F, 0, 0x7E7A9E, 0x7E7A9E, light, 255);
        if (working || wound > 0.5F) {
            int lit = LightTexture.pack(15, 15);
            cylinder(pose, solid, -1.28F, 1.28F, wound, angle, 0xE8C088, 0xC89C68, lit, 255);
        }
    }

    /** x 軸に沿った多角柱（両端にふた）。面ごとに色を交互にする。 */
    private static void cylinder(PoseStack pose, VertexConsumer vc, float x0, float x1, float radius, float angle, int rgb, int rgb2, int light, int alpha) {
        PoseStack.Pose p = pose.last();
        final int segments = 16;
        for (int i = 0; i < segments; i++) {
            float a0 = angle + i * Mth.TWO_PI / segments;
            float a1 = angle + (i + 1) * Mth.TWO_PI / segments;
            float y0 = SPOOL_Y + radius * Mth.cos(a0);
            float z0 = SPOOL_Z + radius * Mth.sin(a0);
            float y1 = SPOOL_Y + radius * Mth.cos(a1);
            float z1 = SPOOL_Z + radius * Mth.sin(a1);
            int c = i % 2 == 0 ? rgb : rgb2;
            float ny = Mth.cos((a0 + a1) / 2);
            float nz = Mth.sin((a0 + a1) / 2);
            vertex(vc, p, x0, y0, z0, 0.5F, 0.5F, c, alpha, light, 0, ny, nz);
            vertex(vc, p, x0, y1, z1, 0.5F, 0.5F, c, alpha, light, 0, ny, nz);
            vertex(vc, p, x1, y1, z1, 0.5F, 0.5F, c, alpha, light, 0, ny, nz);
            vertex(vc, p, x1, y0, z0, 0.5F, 0.5F, c, alpha, light, 0, ny, nz);
            // 両端のふた
            vertex(vc, p, x0, SPOOL_Y, SPOOL_Z, 0.5F, 0.5F, rgb, alpha, light, -1, 0, 0);
            vertex(vc, p, x0, y0, z0, 0.5F, 0.5F, rgb, alpha, light, -1, 0, 0);
            vertex(vc, p, x0, y1, z1, 0.5F, 0.5F, rgb, alpha, light, -1, 0, 0);
            vertex(vc, p, x0, SPOOL_Y, SPOOL_Z, 0.5F, 0.5F, rgb, alpha, light, -1, 0, 0);
            vertex(vc, p, x1, SPOOL_Y, SPOOL_Z, 0.5F, 0.5F, rgb, alpha, light, 1, 0, 0);
            vertex(vc, p, x1, y1, z1, 0.5F, 0.5F, rgb, alpha, light, 1, 0, 0);
            vertex(vc, p, x1, y0, z0, 0.5F, 0.5F, rgb, alpha, light, 1, 0, 0);
            vertex(vc, p, x1, SPOOL_Y, SPOOL_Z, 0.5F, 0.5F, rgb, alpha, light, 1, 0, 0);
        }
    }
}
