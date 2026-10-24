package io.github.genichimaruo.singulo.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 画面の上に浮かぶ小さな窓（レシピの選択や面の設定）。上の帯をつかむと動かせ、右上の×で閉じる。
 * 窓の外の画面はそのまま触れるが、窓の中の操作は下の画面に届かない。
 * 位置は枠からのずれとして覚え、同じ種類の窓を開き直しても同じ場所に出る（ゲームを閉じるまで）。
 */
final class Popup {
    /** 上の帯（つかむところ）の高さ。 */
    static final int BAR = 11;
    private static final Map<String, int[]> OFFSETS = new HashMap<>();

    private final String key;
    boolean open;
    int x;
    int y;
    int w;
    int h;
    private boolean dragging;
    private double grabX;
    private double grabY;

    Popup(String key) {
        this.key = key;
    }

    /**
     * 大きさと位置を決める（毎フレーム呼ぶ）。初めて開くときは既定の位置（枠からのずれ）に出す。
     * 高さが変わったときは下端を動かさない。画面からはみ出さないようにする。
     */
    void layout(int left, int top, int width, int height, int defaultX, int defaultY, int screenW, int screenH) {
        int[] off = OFFSETS.computeIfAbsent(key, k -> new int[]{defaultX, defaultY, height});
        if (off[2] != height) {
            off[1] -= height - off[2];
            off[2] = height;
        }
        w = width;
        h = height;
        x = Math.max(2, Math.min(left + off[0], screenW - w - 2));
        y = Math.max(2, Math.min(top + off[1], screenH - h - 2));
        off[0] = x - left;
        off[1] = y - top;
    }

    /** 中身の上端（帯の下）。 */
    int bodyY() {
        return y + BAR + 1;
    }

    boolean contains(double mx, double my) {
        return open && Panel.inside(mx, my, x, y, w, h);
    }

    private int[] closeRect() {
        return new int[]{x + w - 11, y + 2, 9, 9};
    }

    /** 枠・帯（つまみの印）・閉じるボタン。 */
    void draw(GuiGraphics g, GuiSkin s, int mouseX, int mouseY) {
        Panel.sheet(g, s, x, y, w, h);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + BAR, s.plate(), s.plate2());
        g.fill(x + 1, y + BAR, x + w - 1, y + BAR + 1, s.seam());
        Panel.glowLine(g, s, x + 3, y + 1, w - 6);
        // つまみの印（点の列）
        int gx = x + w / 2 - 9;
        for (int i = 0; i < 6; i++) {
            g.fill(gx + i * 3, y + 4, gx + i * 3 + 1, y + 5, s.dim());
            g.fill(gx + i * 3 + 1, y + 7, gx + i * 3 + 2, y + 8, s.dim());
        }
        int[] c = closeRect();
        boolean hover = Panel.inside(mouseX, mouseY, c[0], c[1], c[2], c[3]);
        int d = Panel.button(g, s, c[0], c[1], c[2], c[3], hover ? Panel.Press.HOVER : Panel.Press.NORMAL);
        int col = hover ? GuiSkin.BAD : s.text();
        for (int i = 0; i < 5; i++) {
            g.fill(c[0] + 2 + i, c[1] + 2 + i + d, c[0] + 3 + i, c[1] + 3 + i + d, col);
            g.fill(c[0] + 6 - i, c[1] + 2 + i + d, c[0] + 7 - i, c[1] + 3 + i + d, col);
        }
    }

    boolean overClose(double mx, double my) {
        int[] c = closeRect();
        return open && Panel.inside(mx, my, c[0], c[1], c[2], c[3]);
    }

    /** 閉じるボタンと帯のクリック。扱ったら true（窓の中のほかの場所は呼び出し側が扱う）。 */
    boolean clickChrome(double mx, double my) {
        if (overClose(mx, my)) {
            open = false;
            return true;
        }
        if (open && Panel.inside(mx, my, x, y, w, BAR)) {
            dragging = true;
            grabX = mx - x;
            grabY = my - y;
            return true;
        }
        return false;
    }

    /** 帯をつかんで動かしている間は true。 */
    boolean drag(int left, int top, double mx, double my) {
        if (!dragging) {
            return false;
        }
        int[] off = OFFSETS.get(key);
        off[0] = (int) Math.round(mx - grabX) - left;
        off[1] = (int) Math.round(my - grabY) - top;
        return true;
    }

    boolean release() {
        boolean was = dragging;
        dragging = false;
        return was;
    }
}
