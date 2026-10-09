package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.machine.SideConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 面の設定の、ドラッグで回せる立方体（装置を北向きに置いたときの形。正面が手前）。
 * 見えている面を奥から順に、設定の色で描く。マウスの下の面を求めるのにも使う。
 */
final class SideCube {
    /** 回転（度）。左右と上下。 */
    float yaw = -35;
    float pitch = 25;

    record Quad(SideConfig.Face face, float[] xs, float[] ys, float depth, float light) {
        boolean contains(double px, double py) {
            // 凸な四角形の中か（辺の外積の符号がそろっているか）
            boolean pos = false, neg = false;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                double cross = (xs[j] - xs[i]) * (py - ys[i]) - (ys[j] - ys[i]) * (px - xs[i]);
                pos |= cross > 0;
                neg |= cross < 0;
            }
            return !(pos && neg);
        }

        float cx() {
            return (xs[0] + xs[1] + xs[2] + xs[3]) / 4;
        }

        float cy() {
            return (ys[0] + ys[1] + ys[2] + ys[3]) / 4;
        }
    }

    void rotate(double dx, double dy) {
        yaw += (float) dx * 1.2F;
        pitch = Mth.clamp(pitch + (float) dy * 1.2F, -85, 85);
    }

    /** 画面に見えている面（奥から手前の順）。cx, cy は中心、half は半辺の長さ（画素）。 */
    List<Quad> visible(float cx, float cy, float half) {
        Matrix4f m = new Matrix4f().rotateX(pitch * Mth.DEG_TO_RAD).rotateY(yaw * Mth.DEG_TO_RAD);
        List<Quad> out = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            Vector3f n = m.transformDirection(new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ()));
            if (n.z <= 0.02F) {
                continue;                                              // 向こうを向いている面
            }
            float[][] corners = corners(dir);
            float[] xs = new float[4];
            float[] ys = new float[4];
            float depth = 0;
            for (int i = 0; i < 4; i++) {
                Vector3f p = m.transformPosition(new Vector3f(corners[i][0], corners[i][1], corners[i][2]));
                xs[i] = cx + p.x * half;
                ys[i] = cy - p.y * half;
                depth += p.z;
            }
            // 正面は北（-Z）。装置の向きを北としたときの、設定上の面
            SideConfig.Face face = SideConfig.faceOf(Direction.NORTH, dir);
            out.add(new Quad(face, xs, ys, depth / 4, 0.6F + 0.4F * n.z));
        }
        out.sort(Comparator.comparingDouble(Quad::depth));
        return out;
    }

    /** マウスの下のいちばん手前の面。なければ null。 */
    @Nullable
    SideConfig.Face pick(List<Quad> quads, double mx, double my) {
        SideConfig.Face hit = null;
        for (Quad q : quads) {
            if (q.contains(mx, my)) {
                hit = q.face();                                        // 奥から順なので、最後に当たったものが手前
            }
        }
        return hit;
    }

    /** 面を色で塗り、縁と、面の頭文字・自動排出の印を描く。 */
    void draw(GuiGraphics g, Font font, List<Quad> quads, ToIntFunction<SideConfig.Face> color, java.util.function.Predicate<SideConfig.Face> eject,
              java.util.function.Function<SideConfig.Face, String> label, @Nullable SideConfig.Face hovered) {
        var vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = g.pose().last().pose();
        for (Quad q : quads) {
            int c = color.applyAsInt(q.face());
            float shade = q.light() * (q.face() == hovered ? 1.15F : 1F);
            int r = Math.min(255, (int) (((c >> 16) & 0xFF) * shade));
            int gr = Math.min(255, (int) (((c >> 8) & 0xFF) * shade));
            int b = Math.min(255, (int) ((c & 0xFF) * shade));
            // 裏表のどちらから描いても見えるよう、両方の巻き順で描く
            for (int i : new int[]{0, 3, 2, 1, 0, 1, 2, 3}) {
                vc.addVertex(pose, q.xs()[i], q.ys()[i], 0).setColor(r, gr, b, 255);
            }
        }
        g.flush();
        for (Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                line(g, q.xs()[i], q.ys()[i], q.xs()[j], q.ys()[j], q.face() == hovered ? 0xFFFFFFFF : 0xFF39424A);
            }
            String s = label.apply(q.face());
            g.drawString(font, s, (int) (q.cx() - font.width(s) / 2F), (int) (q.cy() - 4), 0xFFFFFFFF, true);
            if (eject.test(q.face())) {
                g.fill((int) q.cx() + 3, (int) q.cy() - 6, (int) q.cx() + 6, (int) q.cy() - 3, 0xFFFFFFFF);
            }
        }
    }

    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
        int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int k = 0; k <= steps; k++) {
            float t = steps == 0 ? 0 : k / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * t);
            int y = Math.round(y0 + (y1 - y0) * t);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** 面の4つの角（立方体は -1〜1）。 */
    private static float[][] corners(Direction dir) {
        int ax = dir.getStepX(), ay = dir.getStepY(), az = dir.getStepZ();
        float[][] out = new float[4][];
        if (ax != 0) {
            out = new float[][]{{ax, -1, -1}, {ax, 1, -1}, {ax, 1, 1}, {ax, -1, 1}};
        } else if (ay != 0) {
            out = new float[][]{{-1, ay, -1}, {1, ay, -1}, {1, ay, 1}, {-1, ay, 1}};
        } else {
            out = new float[][]{{-1, -1, az}, {1, -1, az}, {1, 1, az}, {-1, 1, az}};
        }
        return out;
    }
}
