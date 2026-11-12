package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * ホロ投影機の投影（クライアントだけ）。足りない位置には半分の大きさの見本ブロックと水色の枠、
 * 違うブロックがある位置と空けるべき位置には赤い枠を描く。離れるか、コントローラが壊れると消える。
 * <p>
 * 全部そろうと、コラプサー内張りを置ける設備（縮退圧縮炉・C空洞・極低温冷却塔）では、内張りの案内に切り替わる:
 * 内張りに替えられる外装板は脈打つ金色の枠、内張り済みの位置はうすい金色の枠で示し、枚数と加工の速さを画面の下に出す。
 * 内張りを置けない設備は、これまでどおり消える。
 */
public final class HologramRenderer {
    @Nullable
    private static BlockPos controller;
    @Nullable
    private static Blueprints.Kind kind;
    @Nullable
    private static Map<BlockPos, BlockState> layout;
    /** 替えられる外装板の位置（搬入出ポートに。内張りを受け付ける設備なら内張りにも）。形の決まっていない設備なら空。 */
    private static java.util.List<BlockPos> liningSlots = java.util.List.of();
    private static java.util.Set<BlockPos> panelSet = java.util.Set.of();
    /** 内張りを受け付ける設備か（金色で示す。そうでなければ搬入出ポートの位置として水色で示す）。 */
    private static boolean liningKind;
    private static long lastMessage;

    private HologramRenderer() {}

    /** 投影を切り替える。同じコントローラなら消し、resize なら大きさを変えて出し直す。 */
    public static void toggle(BlockPos pos, Blueprints.Kind k, Direction back, int size, boolean resize) {
        if (pos.equals(controller) && !resize) {
            clear();
            return;
        }
        controller = pos.immutable();
        kind = k;
        layout = Blueprints.layout(k, pos, back, size);
        var spec = io.github.genichimaruo.singulo.multiblock.Shapes.spec(k, size);
        liningSlots = spec == null ? java.util.List.of() : spec.panelSlots(controller, back);
        liningKind = spec != null && spec.acceptsLining();
        panelSet = new java.util.HashSet<>(liningSlots);
    }

    public static void clear() {
        controller = null;
        kind = null;
        layout = null;
        liningSlots = java.util.List.of();
        panelSet = java.util.Set.of();
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || layout == null || controller == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(controller)) > 96 * 96
                || kind == null || !level.getBlockState(controller).is(Blueprints.controllerBlock(kind))) {
            clear();
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int wrong = 0;
        float pulse = 0.55F + 0.25F * (float) Math.sin((level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.15);
        for (Map.Entry<BlockPos, BlockState> e : layout.entrySet()) {
            BlockPos p = e.getKey();
            BlockState want = e.getValue();
            BlockState have = level.getBlockState(p);
            if (Blueprints.matches(want, have, panelSet.contains(p), liningKind)) {
                continue;
            }
            wrong++;
            pose.pushPose();
            pose.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            if (want.isAir() || !have.isAir()) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(0.02, 0.02, 0.02, 0.98, 0.98, 0.98), 1.0F, 0.3F, 0.3F, 1.0F);
            } else {
                LevelRenderer.renderLineBox(pose, lines, new AABB(0.05, 0.05, 0.05, 0.95, 0.95, 0.95), 0.47F, 0.82F, 0.94F, pulse);
            }
            if (!want.isAir()) {
                pose.translate(0.25, 0.25, 0.25);
                pose.scale(0.5F, 0.5F, 0.5F);
                mc.getBlockRenderer().renderSingleBlock(want, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
        }
        if (wrong == 0 && !liningSlots.isEmpty()) {
            drawLinings(level, pose, cam, buffers, pulse);
            buffers.endBatch();
            return;
        }
        buffers.endBatch();
        if (wrong == 0) {
            mc.player.displayClientMessage(Component.translatable("holo.singulo.complete"), true);
            clear();
        }
    }

    /** 内張りの案内: 替えられる外装板は脈打つ金色の枠、内張り済みはうすい金色の枠。枚数と速さは1秒ごとに画面の下へ。 */
    private static void drawLinings(Level level, PoseStack pose, Vec3 cam, MultiBufferSource.BufferSource buffers, float pulse) {
        var lining = io.github.genichimaruo.singulo.registry.SinguloBlocks.COLLAPSAR_LINING.get();
        var port = io.github.genichimaruo.singulo.registry.SinguloBlocks.MULTIBLOCK_PORT.get();
        int lined = 0, open = 0;
        int rgb = liningKind ? 0xFFC850 : 0x70D8F0;
        java.util.List<BlockPos> openSlots = new java.util.ArrayList<>(), doneSlots = new java.util.ArrayList<>();
        for (BlockPos p : liningSlots) {
            BlockState have = level.getBlockState(p);
            if (have.is(lining)) {
                doneSlots.add(p);
            } else if (!have.is(port)) {
                openSlots.add(p);                             // 搬入出ポートの位置は、替えると口がなくなるので示さない
            }
        }
        lined = doneSlots.size();
        open = openSlots.size();
        // 光る箱（その場で描く半透明の発光。光をまとめて描く GlowDepth はブロックエンティティの段階で描き終わるので使わない）。
        // 替えられる外装板はブロックより一回り大きい箱が脈打ち、内張り済みはうすい金の箱。描き方ごとにまとめて描く
        VertexConsumer fill = buffers.getBuffer(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        for (BlockPos p : openSlots) {
            pose.pushPose();
            pose.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
            FxDraw.box(pose, fill, -0.06F, -0.06F, -0.06F, 1.06F, 1.06F, 1.06F, LightTexture.FULL_BRIGHT, rgb, (int) (70 + 90 * pulse));
            pose.popPose();
        }
        for (BlockPos p : doneSlots) {
            pose.pushPose();
            pose.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
            FxDraw.box(pose, fill, -0.03F, -0.03F, -0.03F, 1.03F, 1.03F, 1.03F, LightTexture.FULL_BRIGHT, 0xC8A050, 45);
            pose.popPose();
        }
        buffers.endBatch(RenderType.entityTranslucentEmissive(FxDraw.WHITE));
        // 外側の脈打つ枠
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        for (BlockPos p : openSlots) {
            pose.pushPose();
            pose.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
            LevelRenderer.renderLineBox(pose, lines, new AABB(-0.09, -0.09, -0.09, 1.09, 1.09, 1.09), r, g, b, Math.min(1F, pulse + 0.3F));
            pose.popPose();
        }
        long now = level.getGameTime();
        if (!liningKind) {
            if (now - lastMessage >= 20 || now < lastMessage) {
                lastMessage = now;
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("holo.singulo.ports", open), true);
            }
            return;
        }
        if (now - lastMessage >= 20 || now < lastMessage) {
            lastMessage = now;
            int max = io.github.genichimaruo.singulo.multiblock.CollapsarLining.MAX;
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("holo.singulo.lining", lined, max,
                    String.format("%.2f", io.github.genichimaruo.singulo.multiblock.CollapsarLining.factor(lined)), open), true);
        }
    }
}
