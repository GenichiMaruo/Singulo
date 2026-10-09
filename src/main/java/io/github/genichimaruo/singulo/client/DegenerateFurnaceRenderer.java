package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.DegenerateFurnaceBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.multiblock.Shapes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 縮退熱炉の中: 上下のピストンの押し込み棒が中心の燃料を挟んで打ち込み、押しつぶされた燃料が白熱する。
 * 稼働中は STROKE_TICKS ごとに1回、ゆっくり離れてから一気に打ち込み、そのたびに炉心が閃き、熱の輪が広がり、火花が散る。
 * 四隅の熱交換管には熱が流れ上がる。中は観察窓から見える。止まっている間は押し込み棒が離れたまま、炉心は暗い。
 */
public class DegenerateFurnaceRenderer implements BlockEntityRenderer<DegenerateFurnaceBlockEntity> {
    private static final ResourceLocation WHITE = Singulo.id("textures/misc/white.png");
    private static final ResourceLocation RAM = Singulo.id("textures/block/degenerate_furnace_piston.png");
    private static final ResourceLocation ROD = Singulo.id("textures/block/degenerate_furnace_frame.png");
    /** 炉の部屋の中心から、上下のピストンの内側の面までの距離。 */
    private static final float WALL = 2.5F;
    /** 押し込み棒の先が離れているとき・打ち込んだときの、中心からの距離。 */
    private static final float OPEN = 1.55F;
    private static final float SHUT = 0.40F;
    private static final float HOLD_TICKS = 10F;

    /** 炉ごとの、最後に火花を散らした往復の番号（同じ打ち込みで何度も散らさないように）。 */
    private static final Map<BlockPos, Long> LAST_SLAM = new HashMap<>();

    public DegenerateFurnaceRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(DegenerateFurnaceBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        Shapes.Found shape = ShapeCache.get(level, be.getBlockPos(), Blueprints.Kind.DEGENERATE_FURNACE);
        if (shape == null) {
            return;                                                // 形ができていない
        }
        BlockPos center = shape.pos(be.getBlockPos(), 2, 3, 2);
        Vec3 offset = Vec3.atLowerCornerOf(center.subtract(be.getBlockPos())).add(0.5, 0.5, 0.5);
        boolean running = be.getBlockState().getValue(AbstractMachineBlock.LIT);
        float time = level.getGameTime() + partialTick;
        float phase = running ? time % DegenerateFurnaceBlockEntity.STROKE_TICKS : DegenerateFurnaceBlockEntity.STROKE_TICKS - 1;
        float gap = gap(phase);
        float flash = running ? flash(phase) : 0F;
        float heat = running ? 0.45F + 0.55F * flash : 0F;

        if (running) {
            sparks(level, be.getBlockPos(), Vec3.atCenterOf(center), time);
        }

        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        // 押し込み棒（上下）。炉心の明かりで照らされる
        int block = Math.max(LightTexture.block(LevelRenderer.getLightColor(level, center)), (int) (3 + 12 * heat));
        int lit = LightTexture.pack(Math.min(15, block), LightTexture.sky(LevelRenderer.getLightColor(level, center)));
        float shake = flash > 0.6F ? (float) Math.sin(time * 9.0) * 0.015F * flash : 0F;
        VertexConsumer rod = buffers.getBuffer(RenderType.entitySolid(ROD));
        FxDraw.box(pose, rod, -0.2F, gap + 0.3F, -0.2F, 0.2F, WALL, 0.2F, lit, 0xFFFFFF);
        FxDraw.box(pose, rod, -0.2F, -WALL, -0.2F, 0.2F, -gap - 0.3F, 0.2F, lit, 0xFFFFFF);
        VertexConsumer ram = buffers.getBuffer(RenderType.entitySolid(RAM));
        int hot = FxDraw.lerpRgb(0xFFFFFF, 0xFFB070, heat * 0.7F);
        FxDraw.box(pose, ram, -0.45F + shake, gap, -0.45F, 0.45F + shake, gap + 0.32F, 0.45F, lit, hot);
        FxDraw.box(pose, ram, -0.45F - shake, -gap - 0.32F, -0.45F, 0.45F - shake, -gap, 0.45F, lit, hot);

        if (running) {
            VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
            var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
            // 押し込み棒の先の、赤熱した面
            int face = FxDraw.lerpRgb(0xFF5A1E, 0xFFE6B0, flash);
            FxDraw.box(pose, glow, -0.43F, gap - 0.01F, -0.43F, 0.43F, gap + 0.02F, 0.43F, LightTexture.FULL_BRIGHT, face, (int) (90 + 150 * heat));
            FxDraw.box(pose, glow, -0.43F, -gap - 0.02F, -0.43F, 0.43F, -gap + 0.01F, 0.43F, LightTexture.FULL_BRIGHT, face, (int) (90 + 150 * heat));
            // 炉心: 挟まれて平たくつぶれる燃料の塊と、そのまわりの光
            float squash = 0.25F * flash;
            pose.pushPose();
            pose.scale(1 + squash, Math.min(1 - squash * 1.2F, gap / 0.42F), 1 + squash);
            FxDraw.sphere(pose.last(), glow, 0.3F, FxDraw.lerpRgb(0xFF7A2A, 0xFFF6E0, 0.4F + 0.6F * flash), LightTexture.FULL_BRIGHT);
            pose.popPose();
            float breathe = 1 + 0.06F * Mth.sin(time * 0.35F);
            FxDraw.billboard(pose, glow, camera, (1.3F + 1.4F * flash) * breathe, 0xFF8A3A, (int) (70 + 120 * flash));
            FxDraw.billboard(pose, glow, camera, (0.6F + 0.7F * flash) * breathe, 0xFFF0D0, (int) (120 + 135 * flash));
            // 打ち込みの瞬間に、水平に広がる熱の輪
            float since = phase - DegenerateFurnaceBlockEntity.SLAM_TICK;
            if (since >= 0 && since < 14) {
                float k = since / 14F;
                float r = 0.4F + 1.5F * (float) Math.sqrt(k);
                int a = (int) (200 * (1 - k));
                pose.pushPose();
                FxDraw.ring(pose.last(), glow, r, r + 0.12F + 0.25F * (1 - k), 0xFFC080, a, 0);
                pose.popPose();
            }
            // 四隅の熱交換管: 内側の角を熱が流れ上がる
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    float x = sx * 0.53F, z = sz * 0.53F;
                    FxDraw.beam(pose.last(), glow, new Vec3(x, -WALL, z), new Vec3(x, WALL, z), 0.05F, 0xFF7030, (int) (60 + 90 * heat));
                    for (int i = 0; i < 2; i++) {
                        float s = ((time * 0.02F + i * 0.5F + (sx + 2) * 0.13F + (sz + 2) * 0.29F) % 1F);
                        pose.pushPose();
                        pose.translate(x, -WALL + s * 2 * WALL, z);
                        FxDraw.billboard(pose, glow, camera, 0.18F, 0xFFC890, (int) (200 * Mth.sin(s * Mth.PI)));
                        pose.popPose();
                    }
                }
            }
        }
        pose.popPose();
    }

    /** 往復の中の位置 phase（tick）での、押し込み棒の先の中心からの距離。 */
    static float gap(float phase) {
        float slam = DegenerateFurnaceBlockEntity.SLAM_TICK;
        if (phase < slam) {
            float k = phase / slam;
            return OPEN - (OPEN - SHUT) * k * k;                    // だんだん速く打ち込む
        }
        if (phase < slam + HOLD_TICKS) {
            return SHUT;
        }
        float k = (phase - slam - HOLD_TICKS) / (DegenerateFurnaceBlockEntity.STROKE_TICKS - slam - HOLD_TICKS);
        k = k * k * (3 - 2 * k);
        return SHUT + (OPEN - SHUT) * k;                           // ゆっくり離れる
    }

    /** 打ち込みの閃き（0〜1）。打ち込む直前に高まり、打ち込んだ瞬間がいちばん明るく、すっと引く。 */
    static float flash(float phase) {
        float slam = DegenerateFurnaceBlockEntity.SLAM_TICK;
        if (phase < slam) {
            float k = phase / slam;
            return 0.35F * k * k;
        }
        return (float) Math.exp(-(phase - slam) / 7.0);
    }

    /** 打ち込んだ瞬間に、炉の中で火花を散らし、天面から熱気を吹き出す。 */
    private static void sparks(Level level, BlockPos controller, Vec3 c, float time) {
        long cycle = (long) (time / DegenerateFurnaceBlockEntity.STROKE_TICKS);
        float phase = time % DegenerateFurnaceBlockEntity.STROKE_TICKS;
        if (phase < DegenerateFurnaceBlockEntity.SLAM_TICK || phase > DegenerateFurnaceBlockEntity.SLAM_TICK + 4) {
            return;
        }
        Long last = LAST_SLAM.put(controller.immutable(), cycle);
        if (last != null && last == cycle) {
            return;
        }
        var rnd = level.random;
        for (int i = 0; i < 14; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double v = 0.06 + rnd.nextDouble() * 0.12;
            level.addParticle(ParticleTypes.SMALL_FLAME, c.x, c.y + (rnd.nextDouble() - 0.5) * 0.2, c.z,
                    Math.cos(a) * v, (rnd.nextDouble() - 0.3) * 0.08, Math.sin(a) * v);
        }
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.LAVA, c.x, c.y, c.z, 0, 0, 0);
        }
        // 天面のピストンのわきから熱気
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x + (rnd.nextDouble() - 0.5) * 1.6, c.y + 3.6,
                    c.z + (rnd.nextDouble() - 0.5) * 1.6, 0, 0.04 + rnd.nextDouble() * 0.03, 0);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(DegenerateFurnaceBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(DegenerateFurnaceBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(5, 7, 5);
    }
}
