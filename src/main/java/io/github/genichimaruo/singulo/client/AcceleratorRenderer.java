package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.multiblock.AcceleratorControllerBlockEntity;
import io.github.genichimaruo.singulo.multiblock.AcceleratorRing;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** A textured cyan pulse travels around the assembled track only while processing. */
public class AcceleratorRenderer implements BlockEntityRenderer<AcceleratorControllerBlockEntity> {
    private static final ResourceLocation PULSE = Singulo.id("textures/misc/accelerator_pulse.png");
    private record Cached(long checked, AcceleratorRing ring) {}
    private final Map<AcceleratorControllerBlockEntity, Cached> rings = new WeakHashMap<>();

    public AcceleratorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(AcceleratorControllerBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var level = be.getLevel();
        if (level == null || !be.getBlockState().getValue(AbstractMachineBlock.LIT)) {
            rings.remove(be);
            return;
        }
        long tick = level.getGameTime();
        Cached cached = rings.get(be);
        if (cached == null || tick < cached.checked() || tick - cached.checked() >= 10) {
            cached = new Cached(tick, AcceleratorRing.find(level, be.getBlockPos()));
            rings.put(be, cached);
        }
        AcceleratorRing ring = cached.ring();
        if (ring == null) return;
        double head = ((tick % 40 + partialTick) / 40.0) * ring.perimeter();
        double tail = Math.min(6, ring.perimeter() * .2);
        int steps = (int) Math.ceil(tail / .06);
        var glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(PULSE));
        pose.pushPose();
        var offset = ring.minimum().subtract(be.getBlockPos());
        pose.translate(offset.getX(), offset.getY(), offset.getZ());
        // Thin surface overlays avoid hiding the casing, coil detail or retaining bands.
        ribbon(pose.last(), glow, ring, head, tail, steps, 1.004F, .15, 0x55DFFF, 160, false);
        ribbon(pose.last(), glow, ring, head, tail, steps, 1.006F, .055, 0xEDFFFF, 255, false);
        ribbon(pose.last(), glow, ring, head, tail, steps, -.004F, .15, 0x55DFFF, 160, true);
        ribbon(pose.last(), glow, ring, head, tail, steps, -.006F, .055, 0xEDFFFF, 255, true);
        pose.popPose();
    }

    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, AcceleratorRing ring,
            double head, double tail, int steps, float y, double width, int rgb, int alpha, boolean bottom) {
        for (int i = 0; i < steps; i++) {
            float u0 = (float) i / steps, u1 = (float) (i + 1) / steps;
            var a = ring.point(head - tail + u0 * tail);
            var b = ring.point(head - tail + u1 * tail);
            if (!bottom) {
                vertex(pose, buffer, a, -width, y, u0, 0, rgb, alpha, 1);
                vertex(pose, buffer, a, width, y, u0, 1, rgb, alpha, 1);
                vertex(pose, buffer, b, width, y, u1, 1, rgb, alpha, 1);
                vertex(pose, buffer, b, -width, y, u1, 0, rgb, alpha, 1);
            } else {
                vertex(pose, buffer, a, width, y, u0, 1, rgb, alpha, -1);
                vertex(pose, buffer, a, -width, y, u0, 0, rgb, alpha, -1);
                vertex(pose, buffer, b, -width, y, u1, 0, rgb, alpha, -1);
                vertex(pose, buffer, b, width, y, u1, 1, rgb, alpha, -1);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, AcceleratorRing.Point point,
            double width, float y, float u, float v, int rgb, int alpha, float normalY) {
        buffer.addVertex(pose, (float) (point.x() - point.tangentZ() * width), y,
                        (float) (point.z() + point.tangentX() * width))
                .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, normalY, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(AcceleratorControllerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(AcceleratorControllerBlockEntity be) {
        Cached cached = rings.get(be);
        if (cached != null && cached.ring() != null) {
            var ring = cached.ring();
            var p = ring.minimum();
            return new AABB(p.getX(), p.getY() - .01, p.getZ(),
                    p.getX() + ring.side(), p.getY() + 1.01, p.getZ() + ring.side());
        }
        return new AABB(be.getBlockPos()).inflate(33, 1, 33);
    }
}
