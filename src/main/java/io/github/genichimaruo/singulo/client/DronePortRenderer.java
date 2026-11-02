package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * ドローン発着ポートの動く部分: 天井の2枚の扉、昇降台（と下の柱）、台に載った梱包箱、扉のまわりの誘導灯。
 * <p>
 * 開くとき: 2枚の扉が真ん中の合わせ目から、外側の縁を軸に上へ跳ね上がり（0〜ANIM_HINGE）、立ちきってから壁ぎわを下へ引っ込む
 * （〜ANIM_OPEN）。台が箱を天井の高さまで持ち上げ（〜ANIM_RAISED）、ANIM_HANDOFF で箱がドローンへ移り、台が下がる（〜ANIM_LOWERED）。
 * 閉じるときは逆に、扉が壁ぎわからせり上がってから、真ん中へ倒れて閉じる（〜ANIM_TICKS）。荷降ろしは、空の台が上がり、
 * ANIM_HANDOFF で箱が台に載って下がる。時刻は DronePortBlockEntity の ANIM_* と同じ。
 */
public class DronePortRenderer implements BlockEntityRenderer<DronePortBlockEntity> {
    private static final ResourceLocation HATCH = Singulo.id("textures/block/drone_port_hatch.png");
    private static final ResourceLocation PLATFORM = Singulo.id("textures/block/drone_port_platform.png");
    /** 開口部（ドット）と、台のいちばん下・いちばん上の天板の高さ。 */
    static final float HOLE0 = 2, HOLE1 = 14, FLOOR = 4, TOP = 16;
    /** 扉の幅（ドット）と、立てた扉を引っ込める深さ。 */
    static final float LEAF = 6, SINK = 7;
    /** 閉じるときに、扉が壁ぎわからせり上がり終える時刻。 */
    static final int RISEN = DronePortBlockEntity.ANIM_LOWERED + (DronePortBlockEntity.ANIM_OPEN - DronePortBlockEntity.ANIM_HINGE);

    private final ItemRenderer items;

    public DronePortRenderer(BlockEntityRendererProvider.Context context) {
        items = context.getItemRenderer();
    }

    private static float ease(float v) {
        v = Mth.clamp(v, 0, 1);
        return v * v * (3 - 2 * v);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(DronePortBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        // 中は暗いので、上の明るさで描く
        int lit = be.getLevel() == null ? light : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        int anim = be.anim();
        float t = anim == DronePortBlockEntity.ANIM_NONE ? 0 : be.animTime(partialTick);
        // 扉の跳ね上がり（0 閉 〜 1 立った）と、引っ込み（0 〜 1）
        float hinge;
        float sink;
        if (anim == DronePortBlockEntity.ANIM_NONE) {
            hinge = 0;
            sink = 0;
        } else if (t < DronePortBlockEntity.ANIM_LOWERED) {
            hinge = ease(t / DronePortBlockEntity.ANIM_HINGE);
            sink = ease((t - DronePortBlockEntity.ANIM_HINGE) / (DronePortBlockEntity.ANIM_OPEN - DronePortBlockEntity.ANIM_HINGE));
        } else {
            sink = 1 - ease((t - DronePortBlockEntity.ANIM_LOWERED) / (RISEN - DronePortBlockEntity.ANIM_LOWERED));
            hinge = 1 - ease((t - RISEN) / (DronePortBlockEntity.ANIM_TICKS - RISEN));
        }
        float rise = anim == DronePortBlockEntity.ANIM_NONE ? 0
                : t < DronePortBlockEntity.ANIM_HANDOFF
                ? ease((t - DronePortBlockEntity.ANIM_OPEN) / (DronePortBlockEntity.ANIM_RAISED - DronePortBlockEntity.ANIM_OPEN))
                : 1 - ease((t - DronePortBlockEntity.ANIM_HANDOFF) / (DronePortBlockEntity.ANIM_LOWERED - DronePortBlockEntity.ANIM_HANDOFF));
        // 扉の向きは置いた装置の向きに合わせる（正面から見て左右の縁を軸に開く）
        pose.pushPose();
        var state = be.getBlockState();
        if (state.hasProperty(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING)) {
            float yaw = 180 - state.getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING).toYRot();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            pose.translate(-0.5, 0, -0.5);
        }
        drawDoors(pose, buffers, hinge, sink, lit);
        pose.popPose();
        float top = FLOOR + (TOP - FLOOR) * rise;
        if (hinge > 0) {
            drawPlatform(pose, buffers, top, lit);
        }
        boolean crateOnPlatform = anim == DronePortBlockEntity.ANIM_LOAD ? t < DronePortBlockEntity.ANIM_HANDOFF
                : anim == DronePortBlockEntity.ANIM_UNLOAD && t >= DronePortBlockEntity.ANIM_HANDOFF;
        if (crateOnPlatform) {
            ItemStack crate = CargoDroneRenderer.crate(be.animTier());
            if (!crate.isEmpty()) {
                pose.pushPose();
                pose.translate(0.5, top / 16F + CargoDroneRenderer.CRATE_HALF, 0.5);
                items.renderStatic(crate, ItemDisplayContext.NONE, lit, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
                pose.popPose();
            }
        }
        if (anim != DronePortBlockEntity.ANIM_NONE) {
            drawBeacons(pose, buffers, t);
        }
    }

    /**
     * 2枚の扉（各 6×12 ドット、厚さ1）。外側の縁（西の扉は x=2、東の扉は x=14）を軸に、hinge で 0〜90度 上へ跳ね上がり、
     * sink で立ったまま SINK ドット沈む（壁ぎわに沿って開口部の中へ）。壁の面と重ならないよう、軸を少し内側に寄せる。
     */
    private static void drawDoors(PoseStack pose, MultiBufferSource buffers, float hinge, float sink, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(HATCH));
        float down = sink * SINK / 16;
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            float axis = (side < 0 ? HOLE0 + 0.05F : HOLE1 - 0.05F) / 16;
            pose.translate(axis, TOP / 16 - down, 0);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-side * 90 * hinge));
            // 閉じた位置で、軸から真ん中へ向かって伸びる板（上面が天井の高さ）
            float x0 = side < 0 ? 0 : -(LEAF - 0.05F) / 16;
            float x1 = side < 0 ? (LEAF - 0.05F) / 16 : 0;
            float px0 = side < 0 ? HOLE0 : 8, px1 = side < 0 ? 8 : HOLE1;
            FxDraw.pixelBox(pose, vc, x0, -1 / 16F, HOLE0 / 16, x1, 0, HOLE1 / 16, px0, 15, HOLE0, px1, 16, HOLE1, light, 0xFFFFFF);
            pose.popPose();
        }
    }
    /** 昇降台（立てた扉の内側に収まる天板）と、その下の伸びる柱。 */
    private static void drawPlatform(PoseStack pose, MultiBufferSource buffers, float top, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(PLATFORM));
        float a = 3.2F, b = 12.8F;
        FxDraw.pixelBox(pose, vc, a / 16, (top - 1) / 16, a / 16, b / 16, top / 16, b / 16, a, 15, a, b, 16, b, light, 0xFFFFFF);
        if (top - 1 > 3) {
            FxDraw.pixelBox(pose, vc, 6.5F / 16, 3 / 16F, 6.5F / 16, 9.5F / 16, (top - 1) / 16, 9.5F / 16, 6.5F, 3, 6.5F, 9.5F,
                    top - 1, 9.5F, light, 0xB0B0B0);
        }
    }

    /** 動いている間、開口部の四隅の誘導灯が順に点滅する。 */
    private static void drawBeacons(PoseStack pose, MultiBufferSource buffers, float t) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        float[][] corners = {{0.5F, 0.5F}, {14.5F, 0.5F}, {14.5F, 14.5F}, {0.5F, 14.5F}};
        for (int k = 0; k < 4; k++) {
            boolean on = ((int) (t / 3) + k) % 4 < 2;
            float x = corners[k][0] / 16, z = corners[k][1] / 16;
            FxDraw.box(pose, vc, x, 16.01F / 16, z, x + 1 / 16F, 16.4F / 16, z + 1 / 16F, FxDraw.FULL_BRIGHT,
                    DroneStationScreen.ACCENT, on ? 240 : 70);
        }
    }
}
