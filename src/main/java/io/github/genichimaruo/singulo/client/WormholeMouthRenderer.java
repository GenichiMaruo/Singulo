package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ワームホールの口: 枠だけの筐体の中心に浮かぶ小さな黒い球（喉）と、その周りを回る薄紫の光の輪。
 * 喉の大きさに比例して大きくなるが、いちばん大きくても輪まで筐体の内側（枠の2ドットの内側）に収まる。
 * 重力レンズを小さく掛けて、周りの景色をゆがめる。
 */
public class WormholeMouthRenderer implements BlockEntityRenderer<WormholeMouthBlockEntity> {
    private static final ResourceLocation WHITE = Singulo.id("textures/misc/white.png");
    private static final int LAT = 10;
    private static final int LON = 16;

    public WormholeMouthRenderer(BlockEntityRendererProvider.Context context) {}

    public static float throatRadius(int size) {
        return 0.08F + 0.02F * size;
    }

    @Override
    public void render(WormholeMouthBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        int size = be.size();
        if (size <= 0 || be.getLevel() == null) {
            return;
        }
        if(be.traversable())seam(be,partialTick,pose,buffers);
        float r = throatRadius(size);
        float time = be.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, WormholeMouthBlockEntity.MOUTH_HEIGHT, 0.5);
        sphere(pose.last(), buffers.getBuffer(RenderType.entitySolid(WHITE)), r);
        pose.mulPose(Axis.YP.rotationDegrees(time * 4));
        pose.mulPose(Axis.XP.rotationDegrees(20));
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        FxDraw.ring(pose.last(), glow, r * 1.15F, r * 2.0F, 0xC8C8FF, 230, 230);
        pose.popPose();
        Vec3 c = Vec3.atBottomCenterOf(be.getBlockPos()).add(0, WormholeMouthBlockEntity.MOUTH_HEIGHT, 0);
        GravitationalLensing.add(c, r * GravitationalLensing.EINSTEIN_PER_HORIZON, 0.6F);
    }

    private static void seam(WormholeMouthBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers){
        pose.pushPose();pose.translate(0.5,2.5,0.5);
        float yaw=be.getBlockState().getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING).toYRot();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        VertexConsumer glow=buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        int colour=be.passageState()==2?0x8ADEEF:0xDAAF6E;
        FxDraw.box(pose,glow,-1.34F,-1.44F,-0.012F,1.34F,1.44F,0.012F,LightTexture.FULL_BRIGHT,0x07121F,195);
        for(int side=0;side<2;side++){
            float x=side==0?-1.4F:1.34F,y=side==0?-1.5F:1.44F;
            FxDraw.box(pose,glow,x,-1.5F,-0.025F,x+0.06F,1.5F,0.025F,LightTexture.FULL_BRIGHT,colour,210);
            FxDraw.box(pose,glow,-1.4F,y,-0.025F,1.4F,y+0.06F,0.025F,LightTexture.FULL_BRIGHT,colour,210);
        }
        float time=(be.getLevel().getGameTime()%24000+partial)/20F;
        for(int i=0;i<16;i++){
            float y=-1.4F+(i+time*0.7F)%16/16*2.8F;
            FxDraw.box(pose,glow,-1.56F,y,-0.035F,-1.22F,y+0.025F,0.035F,LightTexture.FULL_BRIGHT,colour,170);
            FxDraw.box(pose,glow,1.22F,-y,-0.035F,1.56F,-y+0.025F,0.035F,LightTexture.FULL_BRIGHT,colour,170);
        }
        pose.popPose();
    }

    private static void sphere(PoseStack.Pose last, VertexConsumer vc, float r) {
        for (int i = 0; i < LAT; i++) {
            float t0 = Mth.PI * i / LAT - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / LAT - Mth.HALF_PI;
            for (int j = 0; j < LON; j++) {
                float p0 = Mth.TWO_PI * j / LON;
                float p1 = Mth.TWO_PI * (j + 1) / LON;
                v(vc, last, r, t0, p0);
                v(vc, last, r, t1, p0);
                v(vc, last, r, t1, p1);
                v(vc, last, r, t0, p1);
            }
        }
    }

    private static void v(VertexConsumer vc, PoseStack.Pose last, float r, float theta, float phi) {
        float x = Mth.cos(theta) * Mth.cos(phi);
        float y = Mth.sin(theta);
        float z = Mth.cos(theta) * Mth.sin(phi);
        vc.addVertex(last, x * r, y * r, z * r).setColor(0, 0, 0, 255).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0).setNormal(last, x, y, z);
    }

    @Override
    public AABB getRenderBoundingBox(WormholeMouthBlockEntity be) {
        // 口が画面の外に出ても、周りの重力レンズが画面にかかっている間は描く
        return new AABB(be.getBlockPos()).inflate(8);
    }

    @Override
    public boolean shouldRenderOffScreen(WormholeMouthBlockEntity be) {
        return true;
    }
}
