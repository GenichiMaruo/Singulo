package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.multiblock.FixedShapeControllerBlockEntity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 縮退圧縮炉の中: 天面のラムが底のアンビルの上の塊へ打ち下ろす。稼働中は STROKE ごとに1回、ゆっくり上がってから一気に落ち、
 * 打った瞬間に塊が紫に閃き、衝撃の輪が床を走り、火花が散る。塊は打たれるたびに少しずつ縮んで暗く締まっていく。
 * 止まっている間はラムが上がったまま。中は観察窓から見える。
 */
public class DegenerateCompactorRenderer implements BlockEntityRenderer<FixedShapeControllerBlockEntity.Compactor> {
    private static final ResourceLocation RAM = Singulo.id("textures/block/degenerate_compactor_ram.png");
    private static final ResourceLocation ROD = Singulo.id("textures/block/degenerate_compactor_frame.png");
    private static final ResourceLocation ANVIL = Singulo.id("textures/block/degenerate_compactor_anvil.png");
    private static final float STROKE = 40F;
    private static final float SLAM = 6F;
    /** 部屋の中心から天井・床までの距離。 */
    private static final float WALL = 1.5F;
    /** ラムの先の高さ（上がっているとき・塊を打ったとき）。 */
    private static final float UP = 1.2F;
    private static final Map<BlockPos, Long> LAST_SLAM = new HashMap<>();

    public DegenerateCompactorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FixedShapeControllerBlockEntity.Compactor be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        Shapes.Found shape = ShapeCache.get(level, be.getBlockPos(), Blueprints.Kind.DEGENERATE_COMPACTOR);
        if (shape == null) {
            return;
        }
        BlockPos center = shape.pos(be.getBlockPos(), 2, 2, 2);
        Vec3 offset = Vec3.atLowerCornerOf(center.subtract(be.getBlockPos())).add(0.5, 0.5, 0.5);
        boolean running = be.getBlockState().getValue(AbstractMachineBlock.LIT);
        float time = level.getGameTime() + partialTick;
        float phase = running ? time % STROKE : STROKE - 1;
        // 塊の上面（アンビルの上に乗った塊）
        float squeeze = running ? 0.06F * Mth.sin(time * 0.01F) : 0F;
        float lumpTop = -WALL + 0.5F - squeeze;
        float tip = ramTip(phase, lumpTop);
        float flash = running ? flash(phase) : 0F;

        int sky = LightTexture.sky(LevelRenderer.getLightColor(level, center));
        int lit = LightTexture.pack(Math.min(15, (int) (4 + 11 * Math.max(flash, running ? 0.35F : 0F))), sky);

        if (running) {
            sparks(level, be.getBlockPos(), Vec3.atCenterOf(center).add(0, lumpTop, 0), time);
        }
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        // ラム（棒と頭）
        VertexConsumer rod = buffers.getBuffer(RenderType.entitySolid(ROD));
        FxDraw.box(pose, rod, -0.18F, tip + 0.35F, -0.18F, 0.18F, WALL, 0.18F, lit, 0xFFFFFF);
        VertexConsumer ram = buffers.getBuffer(RenderType.entitySolid(RAM));
        FxDraw.box(pose, ram, -0.42F, tip, -0.42F, 0.42F, tip + 0.35F, 0.42F, lit, 0xFFFFFF);
        // アンビルの上の塊（打たれて締まっていく縮退物質）
        VertexConsumer anvil = buffers.getBuffer(RenderType.entitySolid(ANVIL));
        float half = 0.26F + (running ? 0.02F * (1 - flash) : 0.04F);
        int lump = FxDraw.lerpRgb(0x8A80A8, 0xE8D8FF, flash);
        FxDraw.box(pose, anvil, -half, -WALL, -half, half, lumpTop, half, lit, lump);
        if (running) {
            VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
            var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
            // 打った瞬間の閃きと、床を走る衝撃の輪
            pose.pushPose();
            pose.translate(0, lumpTop, 0);
            FxDraw.billboard(pose, glow, camera, 0.5F + 1.6F * flash, 0xB070FF, (int) (40 + 190 * flash));
            FxDraw.billboard(pose, glow, camera, 0.25F + 0.6F * flash, 0xF0E0FF, (int) (60 + 195 * flash));
            pose.popPose();
            float since = phase - SLAM;
            if (since >= 0 && since < 12) {
                float k = since / 12F;
                float r = 0.3F + 1.1F * (float) Math.sqrt(k);
                pose.pushPose();
                pose.translate(0, -WALL + 0.02F, 0);
                FxDraw.ring(pose.last(), glow, r, r + 0.1F + 0.2F * (1 - k), 0xC890FF, (int) (220 * (1 - k)), 0);
                pose.popPose();
            }
            // 冷却口へ抜ける、ほのかな紫の熱
            float breathe = 0.5F + 0.5F * Mth.sin(time * 0.15F);
            FxDraw.beam(pose.last(), glow, new Vec3(0, lumpTop, 0), new Vec3(0, tip, 0), 0.08F + 0.1F * flash, 0xC8A0FF,
                    (int) (30 + 60 * breathe + 120 * flash));
        }
        pose.popPose();
    }

    /** 往復の中の位置 phase でのラムの先の高さ。 */
    static float ramTip(float phase, float lumpTop) {
        if (phase < SLAM) {
            float k = phase / SLAM;
            return UP - (UP - lumpTop) * k * k;                    // だんだん速く落ちる
        }
        if (phase < SLAM + 6) {
            return lumpTop;
        }
        float k = (phase - SLAM - 6) / (STROKE - SLAM - 6);
        k = k * k * (3 - 2 * k);
        return lumpTop + (UP - lumpTop) * k;                       // ゆっくり上がる
    }

    static float flash(float phase) {
        return phase < SLAM ? 0F : (float) Math.exp(-(phase - SLAM) / 5.0);
    }

    private static void sparks(Level level, BlockPos controller, Vec3 at, float time) {
        long cycle = (long) (time / STROKE);
        float phase = time % STROKE;
        if (phase < SLAM || phase > SLAM + 4) {
            return;
        }
        Long last = LAST_SLAM.put(controller.immutable(), cycle);
        if (last != null && last == cycle) {
            return;
        }
        var rnd = level.random;
        for (int i = 0; i < 12; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double v = 0.05 + rnd.nextDouble() * 0.1;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.05, at.z, Math.cos(a) * v, 0.04 + rnd.nextDouble() * 0.06,
                    Math.sin(a) * v);
        }
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, at.x + (rnd.nextDouble() - 0.5) * 0.6, at.y + 0.1,
                    at.z + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(FixedShapeControllerBlockEntity.Compactor be) {
        return true;
    }

    public AABB getRenderBoundingBox(FixedShapeControllerBlockEntity.Compactor be) {
        return new AABB(be.getBlockPos()).inflate(5, 5, 5);
    }
}
