package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.android.work.SurveyGlassesItem;
import io.github.genichimaruo.singulo.pipe.FluidPipeBlockEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 測量メガネで見る液体パイプ: 流れている向きへ、液体の色の山形の光がパイプの中を走る。止まっているパイプは琥珀の輪で脈打つ。
 * 見ているパイプの量と種類は、画面の中央の近くに小さな札で出す。
 */
final class PipeSurveyRenderer {
    private static final int BLOCKED = 0xFFB450;
    private static List<FluidPipeBlockEntity> cached = List.of();
    private static long refreshed = Long.MIN_VALUE;
    private static Object world;

    private PipeSurveyRenderer() {}

    static void clear(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() == world) {
            cached = List.of();
            world = null;
            refreshed = Long.MIN_VALUE;
        }
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !SurveyGlassesItem.wearing(mc.player)) {
            return;
        }
        long now = mc.level.getGameTime();
        if (world != mc.level || refreshed == Long.MIN_VALUE || now - refreshed >= 10) {
            world = mc.level;
            refreshed = now;
            cached = FluidPipeBlockEntity.clientPipes().stream()
                    .filter(p -> p.getLevel() == mc.level && !p.isRemoved())
                    .filter(p -> p.getBlockPos().distToCenterSqr(mc.player.position()) <= 24 * 24)
                    .sorted(Comparator.comparingDouble(p -> p.getBlockPos().distToCenterSqr(mc.player.position())))
                    .limit(128).toList();
        }
        float time = (now % 24000 + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20F;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(FxDraw.WHITE);
        VertexConsumer vc = buffers.getBuffer(type);
        for (FluidPipeBlockEntity pipe : cached) {
            if (pipe.isRemoved() || !pipe.fresh()) {
                continue;
            }
            Vec3 c = Vec3.atCenterOf(pipe.getBlockPos());
            if (pipe.visualState() == 2) {
                blocked(pose, vc, c, time);
            } else if (pipe.visualState() == 1) {
                int rgb = pipe.mixed() || pipe.visualFluid().isEmpty() ? 0x78E6FF : tint(pipe);
                for (Direction d : Direction.values()) {
                    if ((pipe.outMask() & (1 << d.get3DDataValue())) != 0) {
                        chevrons(pose, vc, c, d, false, time, rgb);
                    }
                    if ((pipe.inMask() & (1 << d.get3DDataValue())) != 0) {
                        chevrons(pose, vc, c, d, true, time, rgb);
                    }
                }
            }
        }
        pose.popPose();
        buffers.endBatch(type);
    }

    /** 液体の色を、暗い液体でも見えるよう少し明るくする。 */
    private static int tint(FluidPipeBlockEntity pipe) {
        return FxDraw.lerpRgb(FluidDraw.tint(pipe.visualFluid().getFluid()) & 0xFFFFFF, 0xFFFFFF, 0.25F);
    }

    /** パイプの中心と面の間を、流れの向きへ走る山形の光。 */
    private static void chevrons(PoseStack pose, VertexConsumer vc, Vec3 center, Direction side, boolean incoming, float time, int rgb) {
        Vec3 d = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 along = incoming ? d.scale(-1) : d;
        Vec3 up = side.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 across = along.cross(up).normalize();
        for (int i = 0; i < 2; i++) {
            float k = (time * 1.4F + i * 0.5F) % 1;
            // 流れてくる側から出ていく側へ（入口は面から中心へ、出口は中心から面へ）
            float at = incoming ? 0.5F - 0.5F * k : 0.5F * k;
            Vec3 tip = center.add(d.scale(at));
            int alpha = (int) (235 * Mth.sin(k * Mth.PI));
            for (Vec3 arm : new Vec3[]{up, across}) {
                FxDraw.beam(pose.last(), vc, tip.subtract(along.scale(0.11)).add(arm.scale(0.17)), tip, 0.035F, rgb, alpha);
                FxDraw.beam(pose.last(), vc, tip.subtract(along.scale(0.11)).subtract(arm.scale(0.17)), tip, 0.035F, rgb, alpha);
            }
        }
    }

    /** 止まっているパイプ: 中心のまわりで脈打つ琥珀の輪。 */
    private static void blocked(PoseStack pose, VertexConsumer vc, Vec3 c, float time) {
        pose.pushPose();
        pose.translate(c.x, c.y, c.z);
        float pulse = 0.5F + 0.5F * Mth.sin(time * 4);
        float r = 0.34F + 0.05F * pulse;
        FxDraw.ring(pose.last(), vc, r, r + 0.05F, BLOCKED, (int) (120 + 120 * pulse), 40);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        FxDraw.ring(pose.last(), vc, r, r + 0.05F, BLOCKED, (int) (120 + 120 * pulse), 40);
        pose.popPose();
    }

    static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null || !SurveyGlassesItem.wearing(mc.player)
                || !(mc.hitResult instanceof BlockHitResult hit)
                || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof FluidPipeBlockEntity pipe)) {
            return;
        }
        boolean flowing = pipe.fresh() && pipe.visualState() == 1;
        boolean stopped = pipe.fresh() && pipe.visualState() == 2;
        Component text = !pipe.fresh() ? Component.translatable("gui.singulo.pipe.stale")
                : flowing ? Component.translatable("gui.singulo.pipe.flow", pipe.visualAmount())
                : Component.translatable("gui.singulo.pipe." + (stopped ? "blocked" : "idle"));
        Component detail = flowing ? (pipe.mixed() ? Component.translatable("gui.singulo.pipe.mixed") : pipe.visualFluid().getHoverName()) : null;
        var g = event.getGuiGraphics();
        var font = mc.font;
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 14;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 10;
        int textX = x + (flowing && !pipe.mixed() && !pipe.visualFluid().isEmpty() ? 16 : 0);
        int width = Math.max(40, Math.min(220, mc.getWindow().getGuiScaledWidth() - textX - 10));
        int textHeight = font.split(text, width).size() * font.lineHeight;
        int height = Math.max(textHeight + (detail == null ? 0 : font.split(detail, width).size() * font.lineHeight + 2), flowing ? 13 : 0);
        int used = Math.min(width, Math.max(font.width(text), detail == null ? 0 : font.width(detail)));
        GuiSkin skin = GuiSkin.COSMIC;
        Panel.sheet(g, skin, x - 4, y - 4, textX - x + used + 8, height + 8);
        g.fill(x - 4, y - 4, x - 2, y + height + 4, stopped ? 0xFF000000 | BLOCKED : skin.glow());
        if (flowing && !pipe.mixed() && !pipe.visualFluid().isEmpty()) {
            FluidDraw.fill(g, pipe.visualFluid().getFluid(), x, y, 12, 12);
        }
        g.drawWordWrap(font, text, textX, y, width, skin.text());
        if (detail != null) {
            g.drawWordWrap(font, detail, textX, y + textHeight + 2, width, skin.dim());
        }
    }
}
