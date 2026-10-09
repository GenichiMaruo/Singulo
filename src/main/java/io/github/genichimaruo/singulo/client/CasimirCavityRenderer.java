package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.multiblock.FixedShapeControllerBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * C空洞の中（向かい合う2枚の鏡の間の真空）: 鏡の間に立つ定在波の薄い膜が、ゆっくり明滅しながら重なり合う。
 * その中で、仮想粒子の対がふっと生まれては離れ、また寄って消える（真空のゆらぎ）。鏡と鏡を細い光の筋がつなぐ。
 * 止まっている間は何も見えない（ただの真空）。
 */
public class CasimirCavityRenderer implements BlockEntityRenderer<FixedShapeControllerBlockEntity.Cavity> {
    /** 部屋の中心から鏡までの距離。 */
    private static final float MIRROR = 1.5F;
    private static final int PAIRS = 14;
    private static final float PAIR_LIFE = 24F;

    public CasimirCavityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FixedShapeControllerBlockEntity.Cavity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.getBlockState().getValue(AbstractMachineBlock.LIT)) {
            return;
        }
        Shapes.Found shape = ShapeCache.get(level, be.getBlockPos(), Blueprints.Kind.CASIMIR_CAVITY);
        if (shape == null) {
            return;
        }
        BlockPos center = shape.pos(be.getBlockPos(), 2, 2, 2);
        Vec3 offset = Vec3.atLowerCornerOf(center.subtract(be.getBlockPos())).add(0.5, 0.5, 0.5);
        float time = level.getGameTime() + partialTick;
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);

        // 定在波の膜: 鏡の間の4枚。腹と節がゆっくり入れ替わる
        for (int n = 1; n <= 4; n++) {
            float y = -MIRROR + 2 * MIRROR * n / 5F;
            float a = 0.5F + 0.5F * Mth.sin(time * 0.06F + n * 1.7F);
            sheet(pose, glow, y + 0.03F * Mth.sin(time * 0.1F + n), 1.45F, 0x8CE6FF, (int) (12 + 34 * a));
        }
        // 鏡と鏡をつなぐ細い光の筋（四隅寄りで、ゆっくり明滅）
        for (int k = 0; k < 4; k++) {
            float ang = k * Mth.HALF_PI + Mth.PI / 4;
            float x = Mth.cos(ang) * 0.95F, z = Mth.sin(ang) * 0.95F;
            float a = 0.5F + 0.5F * Mth.sin(time * 0.09F + k * 2.1F);
            FxDraw.beam(pose.last(), glow, new Vec3(x, -MIRROR, z), new Vec3(x, MIRROR, z), 0.03F, 0xBFF4FF, (int) (30 + 70 * a));
        }
        // 仮想粒子の対: 生まれて離れ、また寄って消える
        for (int i = 0; i < PAIRS; i++) {
            float t = time + i * (PAIR_LIFE / PAIRS);
            long gen = (long) Math.floor(t / PAIR_LIFE);
            float f = (t - gen * PAIR_LIFE) / PAIR_LIFE;
            long seed = gen * 7919L + i * 104729L + be.getBlockPos().asLong();
            float px = (hash(seed) - 0.5F) * 2.4F;
            float py = (hash(seed + 1) - 0.5F) * 2.4F;
            float pz = (hash(seed + 2) - 0.5F) * 2.4F;
            float dir = hash(seed + 3) * Mth.TWO_PI;
            float sep = 0.22F * Mth.sin(f * Mth.PI);
            float size = 0.14F * Mth.sin(f * Mth.PI) + 0.02F;
            int alpha = (int) (230 * Mth.sin(f * Mth.PI));
            for (int s = -1; s <= 1; s += 2) {
                pose.pushPose();
                pose.translate(px + s * sep * Mth.cos(dir), py, pz + s * sep * Mth.sin(dir));
                FxDraw.billboard(pose, glow, camera, size, s < 0 ? 0xE6FCFF : 0x9EDCFF, alpha);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    private static float hash(long seed) {
        long x = seed * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 32;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    /** 水平な薄い膜（両面）。 */
    private static void sheet(PoseStack pose, VertexConsumer vc, float y, float half, int rgb, int alpha) {
        FxDraw.box(pose, vc, -half, y, -half, half, y + 0.001F, half, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, rgb, alpha);
    }

    @Override
    public boolean shouldRenderOffScreen(FixedShapeControllerBlockEntity.Cavity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(FixedShapeControllerBlockEntity.Cavity be) {
        return new AABB(be.getBlockPos()).inflate(5, 5, 5);
    }
}
