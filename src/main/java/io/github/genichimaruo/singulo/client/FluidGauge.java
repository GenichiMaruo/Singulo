package io.github.genichimaruo.singulo.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * 画面のタンクの中身を、動きのある絵で描く。
 * 液体は水面がゆれ、明るさの帯がゆっくり流れ、泡が下から上がる。ガスは境目のない半透明の渦がゆらぎ、粒が漂いながら昇る。
 * 色はその液体の色（IClientFluidTypeExtensions の tint）。密度が負の液体をガスとして描く。
 */
final class FluidGauge {
    private FluidGauge() {}

    static void draw(GuiGraphics g, int x, int y, int w, int h, double fraction, Fluid fluid) {
        Panel.well(g, x, y, w, h);
        int filled = (int) Math.round(h * Mth.clamp(fraction, 0, 1));
        if (filled <= 0) {
            return;
        }
        int rgb = IClientFluidTypeExtensions.of(fluid).getTintColor() & 0xFFFFFF;
        float t = (Util.getMillis() % 3_600_000L) / 1000F;
        // タンクごとに少しずらす（同じ液体のタンクが同じ動きにならないように）
        float seed = (x * 0.37F + y * 0.11F) % 10;
        if (fluid.getFluidType().getDensity() < 0) {
            gas(g, x, y, w, h, filled, rgb, t + seed);
        } else {
            liquid(g, x, y, w, h, filled, rgb, t + seed);
        }
    }

    private static void liquid(GuiGraphics g, int x, int y, int w, int h, int filled, int rgb, float t) {
        int bottom = y + h;
        int top = bottom - filled;
        // 明るさの帯（下から上へゆっくり流れる）
        for (int row = top; row < bottom; row++) {
            float band = 0.94F + 0.06F * Mth.sin((bottom - row) * 0.45F - t * 2.2F);
            g.fill(x, row, x + w, row + 1, 0xFF000000 | shade(rgb, band));
        }
        // 水面のゆれ: 列ごとに1ドット上下する明るい線
        if (filled >= 3) {
            for (int col = 0; col < w; col++) {
                int lift = Math.round(Mth.sin(col * 0.9F + t * 4.0F) * 0.8F);
                int sy = Math.max(y, top + lift);
                g.fill(x + col, sy, x + col + 1, sy + 1, 0xFF000000 | shade(rgb, 1.25F));
                if (lift > 0) {
                    // 水面が下がった列は、上のドットを空ける
                    g.fill(x + col, top, x + col + 1, sy, Panel.WELL);
                }
            }
        }
        // 泡: 下から上がって水面で消える
        if (filled >= 6) {
            for (int i = 0; i < 3; i++) {
                float speed = 7 + i * 3.1F;
                float rise = (t * speed + i * 17.3F) % (filled - 2);
                int bx = x + 1 + (int) ((i * 5.7F + Mth.sin(t * 1.3F + i) * 1.2F + w) % Math.max(1, w - 2));
                int by = bottom - 1 - (int) rise;
                g.fill(bx, by, bx + 1, by + 1, 0xB0000000 | shade(rgb, 1.6F));
            }
        }
    }

    private static void gas(GuiGraphics g, int x, int y, int w, int h, int filled, int rgb, float t) {
        int bottom = y + h;
        int top = bottom - filled;
        // 渦: 位置と時間でゆらぐ明るさ。上の数ドットは薄くして、境目をぼかす
        for (int row = top; row < bottom; row++) {
            float edge = Math.min(1F, (row - top + 1) / 4F);
            for (int col = 0; col < w; col++) {
                float swirl = Mth.sin(col * 0.7F + Mth.sin(row * 0.25F + t * 0.9F) * 2.2F + t * 1.4F)
                        * Mth.cos(row * 0.18F - t * 1.1F);
                float bright = 0.82F + 0.18F * swirl;
                int alpha = (int) ((150 + 50 * swirl) * edge);
                g.fill(x + col, row, x + col + 1, row + 1, (Mth.clamp(alpha, 0, 255) << 24) | shade(rgb, bright));
            }
        }
        // 漂いながら昇る粒
        for (int i = 0; i < 4; i++) {
            float speed = 4 + i * 1.7F;
            float rise = (t * speed + i * 11.9F) % filled;
            int px = x + (int) ((w / 2F + Mth.sin(t * 0.8F + i * 2.1F) * (w / 2F - 1) + w) % w);
            int py = bottom - 1 - (int) rise;
            g.fill(px, py, px + 1, py + 1, 0x90000000 | shade(rgb, 1.5F));
        }
    }

    /** 色の明るさを f 倍する（白に向かって飽和する）。 */
    private static int shade(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF;
        int gr = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        if (f <= 1) {
            return ((int) (r * f) << 16) | ((int) (gr * f) << 8) | (int) (b * f);
        }
        float k = Math.min(1, f - 1);
        return ((int) (r + (255 - r) * k) << 16) | ((int) (gr + (255 - gr) * k) << 8) | (int) (b + (255 - b) * k);
    }
}
