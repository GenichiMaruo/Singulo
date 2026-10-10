package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.ruin.GravityRemnant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 重力の澱: 光を飲む黒い核と、その縁の菫色の光・傾いた降着の輪、周りを回る瓦礫の殻（遺構の建材の塊）。
 * <ul>
 *   <li>核の周りの空間がゆがんで見える（重力レンズ）。体力半分からの重力井戸では、ゆがみと渦の円盤が大きくなる</li>
 *   <li>浮かせて叩きつける前は、核が脈打ち、床へ向かう光の輪が広がる</li>
 *   <li>殻を作り直すときは、遠くから瓦礫が渦を巻いて集まる</li>
 *   <li>出現: 槽の中に核が生まれて膨らみ、槽が砕けると瓦礫が飛んできて殻になる</li>
 * </ul>
 */
public class GravityRemnantRenderer extends EntityRenderer<GravityRemnant> {
    static final int RIM = 0x9A6CFF;
    static final int HOT = 0xE0C8FF;

    public GravityRemnantRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GravityRemnant entity) {
        return FxDraw.WHITE;
    }

    @Override
    public void render(GravityRemnant e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        float emerge = e.emergeProgress(partialTick);
        float death = e.deathProgress(partialTick);
        // 核の大きさ: 出現の35%から膨らみ、倒されると脈打ってから一点へつぶれる
        float grow = emerge < 1 ? Mth.clamp((emerge - 0.1F) / 0.65F, 0, 1) : 1;
        float flipPulse = e.flipTicks() > 0 ? 0.15F * Mth.sin(t * 1.2F) : 0;
        float r = (0.72F + flipPulse + 0.03F * Mth.sin(t * 0.2F)) * grow * collapse(death, t);
        float cy = e.getBbHeight() / 2;
        Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));

        Vec3 world = e.getPosition(partialTick).add(0, cy, 0);
        if (death > 0 && death < FLASH_AT) {
            // つぶれていくほど、ゆがみが強く広がる
            float k = Mth.clamp((death - COLLAPSE_AT) / (FLASH_AT - COLLAPSE_AT), 0, 1);
            GravitationalLensing.add(world, 1.8F + 2.5F * k, 0.6F + 0.6F * k);
        } else if (r > 0.02F) {
            GravitationalLensing.add(world, e.wellOpen() ? 3.2F : 1.6F * grow, e.wellOpen() ? 0.9F : 0.5F);
        }
        pose.pushPose();
        pose.translate(0, cy, 0);
        if (r > 0.02F) {
            FxDraw.sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE)), r, 0x000000, 0);
            // 別の描き方の入れ物を取ると前の入れ物は閉じられるので、光の入れ物は取り直す
            glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
            FxDraw.billboard(pose, glow, camera, r * 3.4F, RIM, (int) (150 * grow));
            FxDraw.billboard(pose, glow, camera, r * 2.3F, HOT, (int) (90 * grow));
            // 傾いた降着の輪
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(22));
            pose.mulPose(Axis.YP.rotationDegrees(t * 6));
            FxDraw.ring(pose.last(), glow, r * 1.25F, r * 2.0F, HOT, (int) (200 * grow), 0);
            FxDraw.ring(pose.last(), glow, r * 2.0F, r * 2.6F, RIM, (int) (110 * grow), 0);
            pose.popPose();
            FxDraw.gyroRings(pose, glow, r * 1.9F, 0.03F, t * 2, RIM, (int) (90 * grow));
        }
        if (death > 0) {
            deathFx(pose, glow, camera, death, t, cy);
        }
        // 重力井戸: 大きく回る渦の円盤
        if (e.wellOpen() && death == 0) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-t * 3));
            for (int k = 0; k < 3; k++) {
                float rr = (float) GravityRemnant.WELL_RADIUS * (0.35F + 0.22F * k);
                FxDraw.ring(pose.last(), glow, rr - 0.08F, rr, RIM, 70 - k * 15, 0);
            }
            pose.popPose();
        }
        // 叩きつけの前触れ: 床へ向かって広がる輪
        int flip = e.flipTicks();
        if (flip > 0) {
            float k = 1 - (flip - partialTick) / GravityRemnant.FLIP_HOLD;
            pose.pushPose();
            pose.translate(0, -cy - 1.4 + 0.05, 0);
            float rr = 1 + k * (float) GravityRemnant.FLIP_RADIUS;
            FxDraw.ring(pose.last(), glow, rr - 0.3F, rr, HOT, (int) (220 * (1 - k * 0.5F)), 0);
            pose.popPose();
        }
        pose.popPose();

        // 瓦礫の殻
        int shell = e.shell();
        float gather = e.gatherTicks() > 0 ? (e.gatherTicks() - partialTick) / GravityRemnant.GATHER_TICKS : 0;
        float arrive = emerge < 1 ? Mth.clamp((emerge - 0.75F) / 0.25F, 0, 1) : 1;
        int pieces = death > 0 ? Math.max(shell, e.deathShell()) : e.gatherTicks() > 0 ? GravityRemnant.SHELL_PIECES : shell;
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        // 倒されたとき: 殻は回るのをやめて床へ崩れ落ち、最後は小さくなって消える
        float fallTicks = e.deathTime > 0 ? e.deathTime + partialTick : 0;
        double frozen = e.deathTime > 0 ? (e.tickCount - e.deathTime) * 0.06 : t * 0.06;
        for (int i = 0; i < pieces && death < 0.97F; i++) {
            double a = frozen + i * Math.PI * 2 / GravityRemnant.SHELL_PIECES;
            float orbit = 1.6F + 4.5F * gather + 6F * (1 - arrive) + fallTicks * 0.03F;
            float tilt = Mth.sin((float) a * 2 + i) * 0.5F;
            if (emerge < 0.75F) {
                break;
            }
            float y = cy + tilt - 2.5F * (1 - arrive);
            if (fallTicks > 0) {
                y = Math.max(-2.3F, y - 0.03F * fallTicks * fallTicks);
            }
            float spin = fallTicks > 0 ? Math.min(fallTicks, 14) : t;
            pose.pushPose();
            pose.translate(Math.cos(a) * orbit, y, Math.sin(a) * orbit);
            pose.mulPose(Axis.YP.rotationDegrees(spin * (3 + i)));
            pose.mulPose(Axis.XP.rotationDegrees(spin * (2 + i * 0.7F)));
            float s = (0.42F + 0.08F * (i % 3)) * (death > 0.8F ? Math.max(0, 1 - (death - 0.8F) / 0.17F) : 1);
            pose.scale(s, s, s);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(GravityRemnant.rubble(i), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** 倒されたときの核の大きさの倍率: 脈打ちながら膨らみ、COLLAPSE_AT からつぶれ、FLASH_AT で消える。 */
    static final float COLLAPSE_AT = 0.625F;
    static final float FLASH_AT = 0.925F;

    static float collapse(float death, float t) {
        if (death <= 0) {
            return 1;
        }
        if (death < COLLAPSE_AT) {
            float speed = 0.4F + death * 2.2F;
            return 1 + 0.12F * death / COLLAPSE_AT + 0.18F * (death / COLLAPSE_AT) * Mth.sin(t * speed);
        }
        if (death < FLASH_AT) {
            float k = (death - COLLAPSE_AT) / (FLASH_AT - COLLAPSE_AT);
            return 1.2F * (1 - k) * (1 - k) + 0.05F;
        }
        return 0;
    }

    /** 倒されたときの光: つぶれる間は縁の光が強まって吸い込まれ、最後に閃光と床を走る衝撃波の輪。 */
    private static void deathFx(PoseStack pose, VertexConsumer glow, Quaternionf camera, float death, float t, float cy) {
        if (death < FLASH_AT) {
            float k = Mth.clamp((death - COLLAPSE_AT) / (FLASH_AT - COLLAPSE_AT), 0, 1);
            if (k > 0) {
                FxDraw.billboard(pose, glow, camera, 3.5F * (1 - k) + 0.4F, HOT, (int) (120 + 120 * k));
                FxDraw.gyroRings(pose, glow, 2.6F * (1 - k) + 0.2F, 0.05F, t * (8 + 30 * k), RIM, 200);
            }
            return;
        }
        float k = (death - FLASH_AT) / (1 - FLASH_AT);
        FxDraw.billboard(pose, glow, camera, 1 + 7 * k, 0xFFFFFF, (int) (255 * (1 - k)));
        FxDraw.billboard(pose, glow, camera, 2 + 10 * k, RIM, (int) (200 * (1 - k)));
        pose.pushPose();
        pose.translate(0, -cy - 2.4F, 0);
        float rr = 1 + 12 * k;
        FxDraw.ring(pose.last(), glow, rr - 0.6F, rr, HOT, (int) (230 * (1 - k)), 0);
        pose.popPose();
    }
}
