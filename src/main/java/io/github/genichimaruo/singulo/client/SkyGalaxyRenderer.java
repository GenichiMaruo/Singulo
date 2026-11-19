package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 創世炉が生んだ銀河を、オーバーワールドの夜空に描く。空のすぐ後（地形より前）に、星と同じ回り方で描くので、
 * 地形や雲の向こうに見え、夜とともに昇って沈む。昼と雨のあいだは見えない。
 */
final class SkyGalaxyRenderer {
    private SkyGalaxyRenderer() {}

    private static final net.minecraft.util.context.ContextKey<java.util.List<SnapshotBlockRenderer.Batch>> KEY = new net.minecraft.util.context.ContextKey<>(io.github.genichimaruo.singulo.Singulo.id("sky_galaxies"));
    static void extract(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent event) {
        event.getRenderState().setRenderData(KEY,java.util.List.of());
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        List<Long> seeds = ClientGalaxies.seeds();
        if (seeds.isEmpty()) {
            return;
        }
        float pt = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float night = Mth.clamp(event.getRenderState().skyRenderState.starBrightness * 2.2F, 0, 1) * (1 - level.getRainLevel(pt));
        if (night <= 0.01F || mc.gameRenderer.getMainCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) {
            return;
        }
        // 空の霧の中で薄れすぎない距離（地形はこの後に上から描かれるので、近くても隠れる）
        float dist = Math.min(30F, mc.options.getEffectiveRenderDistance()*16F * 0.3F);
        Quaternionf sky = new Quaternionf().rotationY(-Mth.HALF_PI).rotateX(event.getRenderState().skyRenderState.starAngle);
        Quaternionf toLocal = new Quaternionf(sky).conjugate();
        Quaternionf camRot = mc.gameRenderer.getMainCamera().rotation();
        Quaternionf localCam = new Quaternionf(toLocal).mul(camRot);
        Vector3f right = localCam.transform(new Vector3f(1, 0, 0));
        Vector3f up = localCam.transform(new Vector3f(0, 1, 0));
        float time = level.getGameTime() + pt;

        PoseStack pose = new PoseStack();
        pose.mulPose(sky);
        var buffers = new SnapshotBlockRenderer.Capture();
        RenderType type = net.minecraft.client.renderer.rendertype.RenderTypes.eyes(FxDraw.WHITE);
        VertexConsumer vc = buffers.getBuffer(type);
        for (int i = 0; i < seeds.size(); i++) {
            long seed = seeds.get(i);
            int traits = ClientGalaxies.traits(i);
            Random rnd = new Random(seed ^ 0x51D3A1L);
            // 真夜中に頭の上あたりへ来る向き（星の球の -Y のまわり）
            Vec3 dir = new Vec3((rnd.nextFloat() - 0.5) * 1.3, -1, (rnd.nextFloat() - 0.5) * 1.1).normalize();
            float appear = ClientGalaxies.appear(seed);
            float radius = dist * (0.16F + rnd.nextFloat() * 0.06F) * (0.5F + 0.5F * appear)
                    * (io.github.genichimaruo.singulo.genesis.GalaxyTraits.grand(traits) ? 1.5F : 1F);   // ネザースターの大きな銀河
            GalaxyDraw.Shape shape = GalaxyDraw.shape(seed, traits);
            // 円盤はこちらを向き、種ごとに傾く
            Quaternionf tilt = new Quaternionf().rotationTo(0, 1, 0, (float) -dir.x, (float) -dir.y, (float) -dir.z)
                    .rotateX(shape.tilt() * Mth.DEG_TO_RAD).rotateY(rnd.nextFloat() * Mth.TWO_PI);
            float bright = night * appear;
            pose.pushPose();
            pose.translate(dir.x * dist, dir.y * dist, dir.z * dist);
            GalaxyDraw.glow(pose, vc, localCam, tilt, seed, traits, radius, bright * 0.8F, 0, true);
            GalaxyDraw.draw(pose.last(), vc, right, up, tilt, seed, traits, radius, time * 0.0004F, bright, time, 0, true);
            pose.popPose();
        }
        event.getRenderState().setRenderData(KEY,buffers.finish());
    }
    static void onRenderStage(RenderLevelStageEvent.AfterSky event) {
        var batches=event.getLevelRenderState().getRenderData(KEY);
        if(batches==null||batches.isEmpty())return;
        var pose=new PoseStack();pose.mulPose(event.getModelViewMatrix());
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        for(var batch:batches) {
            var out=buffers.getBuffer(batch.type());
            for(var v:batch.vertices())out.addVertex(pose.last(),v.x(),v.y(),v.z()).setColor(v.color()).setUv(v.u(),v.v()).setOverlay(v.overlay()).setLight(v.light()).setNormal(pose.last(),v.nx(),v.ny(),v.nz()).setLineWidth(v.width());
            buffers.endBatch(batch.type());
        }
    }

}
