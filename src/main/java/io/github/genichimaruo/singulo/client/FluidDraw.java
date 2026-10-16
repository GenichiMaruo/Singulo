package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;

/**
 * 流体そのものの見た目（静止したときの絵。ブロックの絵の束にあるので、水や溶岩のように流れるアニメーションもそのまま動く）を、
 * 流体の色（{@link IClientFluidTypeExtensions} の色）で染めて描く。ほかの mod の流体・流体として登録されたガスも、同じように描ける。
 */
public final class FluidDraw {
    private FluidDraw() {}

    /** 流体の静止した絵。 */
    public static TextureAtlasSprite sprite(Fluid fluid) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(new FluidStack(fluid, 1000)));
    }

    /** 流体の色（ARGB）。 */
    public static int tint(Fluid fluid) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor(new FluidStack(fluid, 1000));
    }

    /** 流体が空気より軽い（ガス）か（入れ物の上から溜まるように描く）。 */
    public static boolean gaseous(Fluid fluid) {
        return fluid.getFluidType().isLighterThanAir();
    }

    /** 画面の (x, y) から w×h を、流体の絵を 16×16 ずつ並べて埋める（はみ出す所は絵を切る）。 */
    public static void fill(GuiGraphics g, Fluid fluid, int x, int y, int w, int h) {
        fill(g, sprite(fluid), tint(fluid), x, y, w, h);
    }

    /** 化学物質（Mekanism）の絵と色で埋める（絵が分からなければ、白い絵を色で塗る）。 */
    public static void fillChemical(GuiGraphics g, String id, int x, int y, int w, int h) {
        fill(g, chemicalSprite(id), 0xFF000000 | io.github.genichimaruo.singulo.android.work.Chemicals.tint(id), x, y, w, h);
    }

    /** 化学物質の絵（ブロックの絵の束の中。なければ白い絵）。 */
    public static TextureAtlasSprite chemicalSprite(String id) {
        net.minecraft.resources.ResourceLocation icon = io.github.genichimaruo.singulo.android.work.Chemicals.icon(id);
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        return atlas.apply(icon == null ? new net.minecraft.resources.ResourceLocation("minecraft", "block/white_concrete") : icon);
    }

    /** 画面の (x, y) から w×h を、絵 sprite を 16×16 ずつ並べ、色 argb で染めて埋める。 */
    public static void fill(GuiGraphics g, TextureAtlasSprite sprite, int argb, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.flush();
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int ty = 0; ty < h; ty += 16) {
            int th = Math.min(16, h - ty);
            for (int tx = 0; tx < w; tx += 16) {
                int tw = Math.min(16, w - tx);
                float u0 = sprite.getU0(), u1 = sprite.getU(tw), v0 = sprite.getV0(), v1 = sprite.getV(th);
                float x0 = x + tx, x1 = x0 + tw, y0 = y + ty, y1 = y0 + th;
                b.vertex(m, x0, y1, 0).uv(u0, v1).color(argb).endVertex();
                b.vertex(m, x1, y1, 0).uv(u1, v1).color(argb).endVertex();
                b.vertex(m, x1, y0, 0).uv(u1, v0).color(argb).endVertex();
                b.vertex(m, x0, y0, 0).uv(u0, v0).color(argb).endVertex();
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.disableBlend();
    }

    /**
     * 立体の箱（x0..x1, y0..y1, z0..z1。ブロック単位）を、流体の絵で包む（1ブロック = 絵1枚の大きさで、面ごとに絵を切り出す）。
     * vc はブロックの絵の束（{@link InventoryMenu#BLOCK_ATLAS}）の半透明の描き方。
     */
    public static void box(PoseStack pose, VertexConsumer vc, Fluid fluid, float x0, float y0, float z0, float x1, float y1, float z1,
                           int light, int alpha) {
        box(pose, vc, sprite(fluid), tint(fluid), x0, y0, z0, x1, y1, z1, light, alpha);
    }

    /** 化学物質（Mekanism）の絵と色で包んだ箱。 */
    public static void chemicalBox(PoseStack pose, VertexConsumer vc, String id, float x0, float y0, float z0, float x1, float y1, float z1,
                                   int light, int alpha) {
        box(pose, vc, chemicalSprite(id), io.github.genichimaruo.singulo.android.work.Chemicals.tint(id), x0, y0, z0, x1, y1, z1, light, alpha);
    }

    /** 箱を、絵 s を色 argb で染めて包む。 */
    public static void box(PoseStack pose, VertexConsumer vc, TextureAtlasSprite s, int argb, float x0, float y0, float z0, float x1, float y1,
                           float z1, int light, int alpha) {
        int r = argb >> 16 & 0xFF, gr = argb >> 8 & 0xFF, bl = argb & 0xFF;
        PoseStack.Pose p = pose.last();
        float w = Math.abs(x1 - x0), h = Math.abs(y1 - y0), d = Math.abs(z1 - z0);
        // 面ごとに、面の大きさの分だけ絵を切り出す（上下は x と z、北南は x と y、西東は z と y）
        float uw = s.getU(Math.min(1, w) * 16), vd = s.getV(Math.min(1, d) * 16), vh = s.getV(Math.min(1, h) * 16), ud = s.getU(Math.min(1, d) * 16);
        float u0 = s.getU0(), v0 = s.getV0();
        face(vc, p, light, r, gr, bl, alpha, 0, -1, 0, x0, y0, z0, u0, v0, x1, y0, z0, uw, v0, x1, y0, z1, uw, vd, x0, y0, z1, u0, vd);
        face(vc, p, light, r, gr, bl, alpha, 0, 1, 0, x0, y1, z1, u0, vd, x1, y1, z1, uw, vd, x1, y1, z0, uw, v0, x0, y1, z0, u0, v0);
        face(vc, p, light, r, gr, bl, alpha, 0, 0, -1, x0, y1, z0, u0, v0, x1, y1, z0, uw, v0, x1, y0, z0, uw, vh, x0, y0, z0, u0, vh);
        face(vc, p, light, r, gr, bl, alpha, 0, 0, 1, x0, y0, z1, u0, vh, x1, y0, z1, uw, vh, x1, y1, z1, uw, v0, x0, y1, z1, u0, v0);
        face(vc, p, light, r, gr, bl, alpha, -1, 0, 0, x0, y0, z0, u0, vh, x0, y0, z1, ud, vh, x0, y1, z1, ud, v0, x0, y1, z0, u0, v0);
        face(vc, p, light, r, gr, bl, alpha, 1, 0, 0, x1, y1, z0, u0, v0, x1, y1, z1, ud, v0, x1, y0, z1, ud, vh, x1, y0, z0, u0, vh);
    }

    private static void face(VertexConsumer vc, PoseStack.Pose p, int light, int r, int g, int b, int a, float nx, float ny, float nz,
                             float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
        vertex(vc, p, ax, ay, az, au, av, light, r, g, b, a, nx, ny, nz);
        vertex(vc, p, bx, by, bz, bu, bv, light, r, g, b, a, nx, ny, nz);
        vertex(vc, p, cx, cy, cz, cu, cv, light, r, g, b, a, nx, ny, nz);
        vertex(vc, p, dx, dy, dz, du, dv, light, r, g, b, a, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light, int r, int g, int b,
                               int a, float nx, float ny, float nz) {
        vc.vertex(p.pose(), x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
    }
}
