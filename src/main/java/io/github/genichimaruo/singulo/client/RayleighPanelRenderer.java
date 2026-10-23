package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlock;
import io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * レイリー散乱パネルの投影面（下の面）。空の色は、見ている人の目から面の各点への向き（どれだけ上を見上げているか）で決める。
 * 真上を見上げると深い青、浅く見ると地平線に近い淡い青。色は場所ではなく向きで決まるので、並べたパネルの境目でも色がそろい、
 * 1枚の広い空に見える（雲・太陽・月・星は描かない）。消えている間は同じ決め方で、星のない真夜中の暗い色。
 *
 * <p>縁（1ドット）はブロックのモデルの下の面の絵。同じ種類のパネルが隣にある辺は、投影面を縁の上まで広げて縁を隠す。
 */
public class RayleighPanelRenderer implements BlockEntityRenderer<RayleighPanelBlockEntity> {
    /** 真昼の空（真上・地平線の近く）と、真夜中（真上・地平線の近く）の色。アイコンの絵（tools/lab_blocks.py）と同じ色。 */
    static final int NOON_ZENITH = 0x3F7FE4, NOON_HORIZON = 0xBCD9F8, NIGHT_ZENITH = 0x03050B, NIGHT_HORIZON = 0x0C1426;
    /** 縁の色（研究所のもの・作ったもの）。モデルの下の面の絵の縁と同じ色。 */
    static final int LAB_FRAME = 0xD6DCE4, MADE_FRAME = 0x4C525E;
    /** 縁の太さ（ブロック）と、面から少し下げる量（モデルの面と重ねない）。 */
    private static final float FRAME = 1 / 16F, DROP = 0.0015F;
    /** 1辺の分け方（色を頂点ごとに決めるので、細かく分けるほど滑らか）。 */
    private static final int GRID = 4;

    public RayleighPanelRenderer(BlockEntityRendererProvider.Context context) {}

    /**
     * 目から見た向き dir（正規化済み）の空の色。頂点の法線は上向きにする（下向きだと、エンティティの陰影で下の面が暗く沈み、
     * 真昼の空に見えない）。
     */
    static int skyColor(Vec3 dir, boolean lit) {
        // 見上げる角度の sin（0 = 水平、1 = 真上）。下を向いた面なので、見えるときは正
        float up = (float) Mth.clamp(dir.y, 0, 1);
        float t = (float) Math.pow(Mth.clamp((up - 0.05F) / 0.95F, 0, 1), 0.65);
        return lit ? FxDraw.lerpRgb(NOON_HORIZON, NOON_ZENITH, t) : FxDraw.lerpRgb(NIGHT_HORIZON, NIGHT_ZENITH, t);
    }

    @Override
    public void render(RayleighPanelBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof RayleighPanelBlock panel)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (eye.y >= pos.getY()) {
            return;                                                         // 下の面は、下からしか見えない
        }
        boolean lit = be.getBlockState().getValue(RayleighPanelBlock.LIT);
        // 隣にパネルがある辺は縁まで広げる
        float x0 = panel.connects(be.getLevel(), pos, Direction.WEST) ? 0 : FRAME;
        float x1 = panel.connects(be.getLevel(), pos, Direction.EAST) ? 1 : 1 - FRAME;
        float z0 = panel.connects(be.getLevel(), pos, Direction.NORTH) ? 0 : FRAME;
        float z1 = panel.connects(be.getLevel(), pos, Direction.SOUTH) ? 1 : 1 - FRAME;
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(FxDraw.WHITE));
        PoseStack.Pose last = pose.last();
        // 点いている間は自分で光る（周りの暗さに関係なく真昼の色）。消えていれば周りの明るさのまま
        int packed = lit ? FxDraw.FULL_BRIGHT : light;
        float y = -DROP;
        for (int i = 0; i < GRID; i++) {
            for (int k = 0; k < GRID; k++) {
                float ax = Mth.lerp(i / (float) GRID, x0, x1), bx = Mth.lerp((i + 1) / (float) GRID, x0, x1);
                float az = Mth.lerp(k / (float) GRID, z0, z1), bz = Mth.lerp((k + 1) / (float) GRID, z0, z1);
                // 下から見て表になる向き（法線は下）
                vertex(vc, last, pos, eye, ax, y, az, lit, packed);
                vertex(vc, last, pos, eye, bx, y, az, lit, packed);
                vertex(vc, last, pos, eye, bx, y, bz, lit, packed);
                vertex(vc, last, pos, eye, ax, y, bz, lit, packed);
            }
        }
        // 角: 2つの隣はあるのに斜めの隣がない所（L字の内側の角）は、縁の角（1ドット）を描き足す
        corner(be, panel, last, vc, pos, eye, lit, packed, Direction.WEST, Direction.NORTH, 0, 0);
        corner(be, panel, last, vc, pos, eye, lit, packed, Direction.EAST, Direction.NORTH, 1 - FRAME, 0);
        corner(be, panel, last, vc, pos, eye, lit, packed, Direction.WEST, Direction.SOUTH, 0, 1 - FRAME);
        corner(be, panel, last, vc, pos, eye, lit, packed, Direction.EAST, Direction.SOUTH, 1 - FRAME, 1 - FRAME);
    }

    /** 縁の角の1ドット: 投影面は辺ごとに縁まで広げるので、2辺とも広げると角まで空になる。斜めの隣がなければ、そこに縁を戻す。 */
    private static void corner(RayleighPanelBlockEntity be, RayleighPanelBlock panel, PoseStack.Pose last, VertexConsumer vc, BlockPos pos,
                               Vec3 eye, boolean lit, int packed, Direction a, Direction b, float cx, float cz) {
        var level = be.getLevel();
        if (level == null || !panel.connects(level, pos, a) || !panel.connects(level, pos, b)
                || level.getBlockState(pos.relative(a).relative(b)).is(panel)) {
            return;
        }
        int rgb = panel.lab() ? LAB_FRAME : MADE_FRAME;
        int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos.below());
        float y = -DROP * 2;
        for (float[] v : new float[][] {{cx, cz}, {cx + FRAME, cz}, {cx + FRAME, cz + FRAME}, {cx, cz + FRAME}}) {
            vc.vertex(last.pose(), v[0], y, v[1]).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255).uv(0.5F, 0.5F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(last.normal(), 0, -1, 0).endVertex();
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, BlockPos pos, Vec3 eye, float x, float y, float z, boolean lit,
                               int light) {
        Vec3 dir = new Vec3(pos.getX() + x - eye.x, pos.getY() + y - eye.y, pos.getZ() + z - eye.z).normalize();
        int rgb = skyColor(dir, lit);
        vc.vertex(last.pose(), x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(last.normal(), 0, 1, 0).endVertex();
    }
}
