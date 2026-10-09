package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.multiblock.CoolingTowerControllerBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 極低温冷却塔: 稼働中は中心の熱交換コアのまわりを冷たい青い光が包み、霜の帯がゆっくり昇っていく（くびれの窓から見える）。
 * てっぺんの格子からは冷えた蒸気が白く立ちのぼり、ふくらみながら風に流れる。
 */
public class CoolingTowerRenderer implements BlockEntityRenderer<CoolingTowerControllerBlockEntity> {
    public CoolingTowerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CoolingTowerControllerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.getBlockState().getValue(AbstractMachineBlock.LIT)) {
            return;
        }
        Shapes.Found shape = ShapeCache.get(level, be.getBlockPos(), Blueprints.Kind.COOLING_TOWER);
        if (shape == null) {
            return;
        }
        int h = shape.size();
        BlockPos bottom = shape.pos(be.getBlockPos(), 2, 1, 2);
        Vec3 base = Vec3.atLowerCornerOf(bottom.subtract(be.getBlockPos())).add(0.5, 0, 0.5);
        float time = level.getGameTime() + partialTick;
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        float top = h - 2;                                                // コアの列の高さ（y=1〜h−2）
        pose.pushPose();
        pose.translate(base.x, base.y, base.z);
        // コアを包む青い光（コアより少し外側の薄い殻）
        float breathe = 0.5F + 0.5F * Mth.sin(time * 0.05F);
        FxDraw.box(pose, glow, -0.53F, 0, -0.53F, 0.53F, top, 0.53F, LightTexture.FULL_BRIGHT, 0x7FD8FF, (int) (40 + 30 * breathe));
        // 昇っていく霜の帯
        for (int i = 0; i < 3; i++) {
            float y = ((time * 0.04F + i / 3F) % 1F) * top;
            float a = Mth.sin((y / top) * Mth.PI);
            FxDraw.box(pose, glow, -0.6F, y, -0.6F, 0.6F, y + 0.18F, 0.6F, LightTexture.FULL_BRIGHT, 0xDAF6FF, (int) (110 * a));
        }
        // てっぺんの上に立つ蒸気（ふくらみながら昇る、やわらかい白）
        for (int i = 0; i < 6; i++) {
            float f = ((time * 0.012F + i / 6F) % 1F);
            float drift = f * f * 1.6F;
            pose.pushPose();
            pose.translate(drift * 0.6F, h - 1 + 0.6F + f * 4.5F, drift * 0.35F);
            FxDraw.billboard(pose, glow, camera, 1.2F + f * 2.6F, 0xEEF6FA, (int) (70 * Mth.sin(f * Mth.PI)));
            pose.popPose();
        }
        pose.popPose();
        // 格子のすき間から細かい冷気の粒
        if (level.random.nextInt(3) == 0) {
            Vec3 t = Vec3.atCenterOf(shape.pos(be.getBlockPos(), 2, h - 1, 2));
            level.addParticle(ParticleTypes.SNOWFLAKE, t.x + (level.random.nextDouble() - 0.5) * 2.4, t.y + 0.6,
                    t.z + (level.random.nextDouble() - 0.5) * 2.4, 0, 0.05, 0);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(CoolingTowerControllerBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(CoolingTowerControllerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(6, 3, 6).expandTowards(0, Shapes.TOWER_MAX_HEIGHT + 6, 0);
    }
}
