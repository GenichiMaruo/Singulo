package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * 液体そのものの絵（液体の静止テクスチャと色）で箱を描く。液体パイプの中の流れと、液体カプセルの中身に使う。
 * 面の UV は面の大きさに合わせて切り出す（1ドットを1ドットのまま。引き伸ばさない）。
 */
final class FluidLook {
    private FluidLook() {}

    /** 液体の見た目: 絵・色（不透明度を含む）・光（光る液体は最大の明るさ）。 */
    record Look(TextureAtlasSprite sprite, int argb, boolean glows) {
        int light(int light) {
            return glows ? LightTexture.FULL_BRIGHT : light;
        }

        int rgb() {
            return argb & 0xFFFFFF;
        }

        int alpha() {
            return argb >>> 24;
        }
    }

    static Look look(FluidStack fluid) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
        int tint = ext.getTintColor(fluid);
        int alpha = tint >>> 24;
        if (alpha == 0) {
            alpha = 255;
        }
        // 気体はうすく（中が透けて見える）
        if (fluid.getFluid().getFluidType().isLighterThanAir()) {
            alpha = Math.min(alpha, 120);
        } else {
            alpha = Math.max(alpha, 200);
        }
        return new Look(sprite, (alpha << 24) | (tint & 0xFFFFFF), fluid.getFluid().getFluidType().getLightLevel(fluid) > 0);
    }

    static VertexConsumer buffer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
    }

    /**
     * 入れ物（ガラスの殻）の中の液体の描き込み先。殻の半透明（{@code Sheets.translucentItemSheet}）は共有の描き込み先なので、
     * 液体も別の共有の描き込み先に入れる。共有の描き込み先は、別の共有の種類を求めた時点でそれまでの分を描くため、殻を求めた
     * 瞬間に液体が先に描かれ、ガラスの奥に必ず見える（{@link #buffer} の種類は固定の描き込み先で、殻より後に描かれて隠れる）。
     * 面を落とさない描き方で、裏の面も奥から順に重なる。
     */
    static VertexConsumer insideBuffer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
    }

    /**
     * 箱（ブロック単位）。mask は描く面（Direction の 3D 番号のビット。0x3F で全部）。rgb・alpha は液体の色に掛ける。
     */
    static void box(PoseStack pose, VertexConsumer vc, Look look, int rgb, int alpha, int light, int mask,
                    float x0, float y0, float z0, float x1, float y1, float z1) {
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite s = look.sprite();
        int lum = look.light(light);
        // DOWN, UP, NORTH, SOUTH, WEST, EAST の順（Direction.get3DDataValue）。
        // 頂点は面の外から見て反時計回り（裏を落とす描き方で、外から見える向き）。上と下は以前逆回りで、上からは上面が消えて下面が見えた
        if ((mask & 1) != 0) quad(vc, p, s, rgb, alpha, lum, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, x1 - x0, z1 - z0);
        if ((mask & 2) != 0) quad(vc, p, s, rgb, alpha, lum, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, x1 - x0, z1 - z0);
        if ((mask & 4) != 0) quad(vc, p, s, rgb, alpha, lum, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1 - x0, y1 - y0);
        if ((mask & 8) != 0) quad(vc, p, s, rgb, alpha, lum, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x1 - x0, y1 - y0);
        if ((mask & 16) != 0) quad(vc, p, s, rgb, alpha, lum, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, z1 - z0, y1 - y0);
        if ((mask & 32) != 0) quad(vc, p, s, rgb, alpha, lum, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, z1 - z0, y1 - y0);
    }

    /** 4頂点（左下・右下・右上・左上の順）。UV は絵の左上から、面の幅 w・高さ h（ブロック）ぶん。 */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int rgb, int alpha, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float w, float h) {
        float u0 = s.getU0(), v0 = s.getV0();
        float du = (s.getU1() - u0) * Math.min(1, w), dv = (s.getV1() - v0) * Math.min(1, h);
        vertex(vc, p, ax, ay, az, u0, v0 + dv, rgb, alpha, light, nx, ny, nz);
        vertex(vc, p, bx, by, bz, u0 + du, v0 + dv, rgb, alpha, light, nx, ny, nz);
        vertex(vc, p, cx, cy, cz, u0 + du, v0, rgb, alpha, light, nx, ny, nz);
        vertex(vc, p, dx, dy, dz, u0, v0, rgb, alpha, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
                               int rgb, int alpha, int light, float nx, float ny, float nz) {
        vc.vertex(p.pose(), x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
    }

    /** 2つの色を掛け合わせる（液体の色 × 明るさの色）。 */
    static int multiply(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return (r << 16) | (g << 8) | bl;
    }
}
