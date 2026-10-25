package io.github.genichimaruo.singulo.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * GUIの部品を描く（テクスチャ画像を使わない）。光は左上から、段差は1pxまで。
 * 押せるもの（ボタン・タブ）は凸、入れ物（スロット・ゲージ・表示窓）は凹、文字と地は平。
 */
final class Panel {
    static final int BACKGROUND = 0xFFE9EDF0;
    static final int SEAM = 0xFFC4CAD0;
    static final int SHADE = 0xFFD6DBE0;
    static final int WELL = 0xFF39424A;
    static final int WELL_EDGE = 0xFF8B95A0;
    static final int TEXT = 0xFF3C4650;
    static final int GLOW = 0xFF78D2F0;
    static final int AMBER = 0xFFF0B450;
    static final int RED = 0xFFE86060;

    private static final int SHADOW = 0x66000000;
    private static final int SHADOW_SOFT = 0x2E000000;

    enum Press { NORMAL, HOVER, PRESSED, ON, DISABLED }

    private Panel() {}

    // ------------------------------------------------------------------ 骨格

    /** 枠: 右下に2段の落ち影、角を欠いた輪郭、上が少し明るい面、左上の明るい線と右下の暗い線。 */
    static void frame(GuiGraphics g, GuiSkin s, int x, int y, int w, int h) {
        g.fill(x + 2, y + h, x + w + 1, y + h + 1, SHADOW);
        g.fill(x + w, y + 2, x + w + 1, y + h, SHADOW);
        g.fill(x + 3, y + h + 1, x + w + 1, y + h + 2, SHADOW_SOFT);
        g.fill(x + w + 1, y + 3, x + w + 2, y + h + 1, SHADOW_SOFT);
        int c = s.corner();
        g.fill(x + c, y, x + w - c, y + 1, s.outline());
        g.fill(x + c, y + h - 1, x + w - c, y + h, s.outline());
        g.fill(x, y + c, x + 1, y + h - c, s.outline());
        g.fill(x + w - 1, y + c, x + w, y + h - c, s.outline());
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, s.face(), s.face2());
        g.fill(x + 1, y + 1, x + w - 1, y + 2, s.hi());
        g.fill(x + 1, y + 1, x + 2, y + h - 1, s.hi());
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, s.lo());
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, s.lo());
        if (c == 2) {
            g.fill(x + 1, y + 1, x + 2, y + 2, s.outline());
            g.fill(x + w - 2, y + 1, x + w - 1, y + 2, s.outline());
            g.fill(x + 1, y + h - 2, x + 2, y + h - 1, s.outline());
            g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, s.outline());
        }
        if (s.stars()) {
            stars(g, x, y, w, h);
        }
    }

    /** 宇宙スキンの地の星（位置は枠の大きさで決まり、明るさだけゆっくり瞬く）。 */
    private static void stars(GuiGraphics g, int x, int y, int w, int h) {
        int top = 20;
        int span = Math.max(10, h - 120);
        float t = (net.minecraft.Util.getMillis() % 100_000L) / 1000F;
        int seed = 7;
        for (int i = 0; i < 26; i++) {
            seed = (seed * 9301 + 49297) % 233280;
            int sx = 4 + seed % (w - 8);
            seed = (seed * 9301 + 49297) % 233280;
            int sy = top + seed % span;
            float tw = 0.5F + 0.5F * net.minecraft.util.Mth.sin(t * 1.3F + i * 2.1F);
            int alpha = (int) ((i % 3 == 0 ? 120 : 50) + 60 * tw);
            g.fill(x + sx, y + sy, x + sx + 1, y + sy + 1, alpha << 24 | 0xCFE0FF);
        }
    }

    /** ヘッダーの板（装置名・状態・電源を載せる）と、その下の彫り込み。上端の発光ラインは今までの印として残す。 */
    static void header(GuiGraphics g, GuiSkin s, int x, int y, int w) {
        g.fillGradient(x + 3, y + 3, x + w - 3, y + 16, s.plate(), s.plate2());
        g.fill(x + 3, y + 16, x + w - 3, y + 17, s.seam());
        g.fill(x + 3, y + 17, x + w - 3, y + 18, s.seamHi());
        glowLine(g, s, x + 5, y + 3, w - 10);
    }

    static void glowLine(GuiGraphics g, GuiSkin s, int x, int y, int w) {
        if (s.glow() == s.glow2()) {
            g.fill(x, y, x + w, y + 1, s.glow());
            return;
        }
        for (int i = 0; i < w; i++) {
            g.fill(x + i, y, x + i + 1, y + 1, GuiSkin.mix(s.glow(), s.glow2(), (float) i / Math.max(1, w - 1)));
        }
    }

    /** 区切りの溝（暗い1pxと明るい1px）。 */
    static void groove(GuiGraphics g, GuiSkin s, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, s.seam());
        g.fill(x, y + 1, x + w, y + 2, s.seamHi());
    }

    /** 浮いた板（設定やレシピ選択など、装置の部分にかぶせる画面）。 */
    static void sheet(GuiGraphics g, GuiSkin s, int x, int y, int w, int h) {
        g.fill(x + 1, y + h, x + w + 1, y + h + 1, SHADOW);
        g.fill(x + w, y + 1, x + w + 1, y + h, SHADOW);
        g.fill(x, y, x + w, y + h, s.outline());
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, s.face(), s.face2());
        g.fill(x + 1, y + 1, x + w - 1, y + 2, s.hi());
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, s.lo());
    }

    // ------------------------------------------------------------------ 凹

    /** 凹んだ入れ物。x, y, w, h は縁を含む外側。 */
    static void inset(GuiGraphics g, GuiSkin s, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + 1, s.dark());
        g.fill(x, y, x + 1, y + h, s.dark());
        g.fill(x + 1, y + h - 1, x + w, y + h, s.slotHi());
        g.fill(x + w - 1, y + 1, x + w, y + h, s.slotHi());
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    static void slot(GuiGraphics g, GuiSkin s, int x, int y) {
        inset(g, s, x - 1, y - 1, 18, 18, s.fill());
        g.fill(x, y, x + 16, y + 1, s.shade());
        g.fill(x, y, x + 1, y + 16, s.shade());
    }

    /** 中身（x, y, w, h）の周りに1pxの縁をつけた凹み。 */
    static void well(GuiGraphics g, GuiSkin s, int x, int y, int w, int h) {
        inset(g, s, x - 1, y - 1, w + 2, h + 2, s.fill());
    }

    /** 充填部をガラス越しに見せる（左と上を明るく、右を暗く）。 */
    static void glass(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + 1, 0x8CFFFFFF);
        g.fill(x, y + 1, x + 1, y + h, 0x59FFFFFF);
        if (w > 2) {
            g.fill(x + w - 1, y + 1, x + w, y + h, 0x40000000);
        }
    }

    /** 下から fraction の割合だけ塗る縦のゲージ（上が明るいグラデ、ガラス、右に目盛り）。 */
    static void gauge(GuiGraphics g, GuiSkin s, int x, int y, int w, int h, double fraction, int color) {
        well(g, s, x, y, w, h);
        int filled = (int) Math.round(h * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fillGradient(x, y + h - filled, x + w, y + h, GuiSkin.mix(color, 0xFFFFFFFF, 0.35F), GuiSkin.mix(color, 0xFF000000, 0.2F));
            glass(g, x, y + h - filled, w, filled);
        }
        for (int i = 1; i < 4; i++) {
            int ty = y + Math.round(h * i / 4F);
            g.fill(x + w - 2, ty, x + w, ty + 1, 0x73FFFFFF);
        }
    }

    /** 彫り込んだ進捗の矢印（下に明るい線）。進んだ分だけ発光色で埋める。 */
    static void arrow(GuiGraphics g, GuiSkin s, int x, int y, double fraction) {
        arrowShape(g, x, y + 1, s.arrowHi(), 22);
        arrowShape(g, x, y, s.arrow(), 22);
        int filled = (int) Math.round(22 * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            arrowShape(g, x, y, s.glow(), filled);
        }
    }

    private static void arrowShape(GuiGraphics g, int x, int y, int color, int limit) {
        g.fill(x, y + 6, x + Math.min(limit, 16), y + 10, color);
        for (int i = 0; i < 8 && 14 + i < limit; i++) {
            g.fill(x + 14 + i, y + i, x + 15 + i, y + 16 - i, color);
        }
    }

    /** 状態の記号（文字の代わり。詳しい説明はツールチップ）。 */
    enum Status { RUN, IDLE, WARN, NO_POWER, FULL, BROKEN, OFF }

    static final int CHIP_WIDTH = 19;

    /** 状態の表示窓（LEDと記号）。right は右端、返り値は左端。 */
    static int chip(GuiGraphics g, GuiSkin s, int right, int y, Status status, int color) {
        int left = right - CHIP_WIDTH;
        inset(g, s, left, y, CHIP_WIDTH, 11, s.chip());
        g.fill(left + 3, y + 4, left + 6, y + 7, color);
        g.fill(left + 3, y + 4, left + 4, y + 5, 0xFFFFFFFF);
        statusGlyph(g, left + 9, y + 2, status, color);
        return left;
    }

    /** 7×7 の状態の記号。 */
    static void statusGlyph(GuiGraphics g, int x, int y, Status status, int c) {
        switch (status) {
            case RUN -> {
                for (int i = 0; i < 4; i++) {
                    g.fill(x + 1 + i, y + i, x + 2 + i, y + 7 - i, c);
                }
            }
            case IDLE -> {
                g.fill(x, y + 5, x + 1, y + 6, c);
                g.fill(x + 3, y + 5, x + 4, y + 6, c);
                g.fill(x + 6, y + 5, x + 7, y + 6, c);
            }
            case WARN -> {
                g.fill(x + 3, y, x + 4, y + 4, c);
                g.fill(x + 3, y + 5, x + 4, y + 7, c);
            }
            case NO_POWER -> boltIcon(g, x, y - 1, c);
            case FULL -> {
                g.renderOutline(x, y, 7, 7, c);
                g.fill(x + 2, y + 2, x + 5, y + 5, c);
            }
            case BROKEN -> {
                for (int i = 0; i < 7; i++) {
                    g.fill(x + i, y + i, x + i + 1, y + i + 1, c);
                    g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, c);
                }
            }
            case OFF -> {
                g.fill(x + 1, y, x + 3, y + 7, c);
                g.fill(x + 4, y, x + 6, y + 7, c);
            }
        }
    }

    // ------------------------------------------------------------------ 凸

    /** ボタン。押している間は明暗が逆になり、返り値（中身を下げる量）が1になる。 */
    static int button(GuiGraphics g, GuiSkin s, int x, int y, int w, int h, Press state) {
        int edge = state == Press.HOVER ? s.accent() : state == Press.DISABLED ? s.lo() : s.outline();
        g.fill(x + 1, y, x + w - 1, y + 1, edge);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, edge);
        g.fill(x, y + 1, x + 1, y + h - 1, edge);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, edge);
        int ix = x + 1;
        int iy = y + 1;
        int iw = w - 2;
        int ih = h - 2;
        switch (state) {
            case DISABLED -> g.fill(ix, iy, ix + iw, iy + ih, s.button2());
            case PRESSED -> {
                g.fill(ix, iy, ix + iw, iy + ih, s.button2());
                g.fill(ix, iy, ix + iw, iy + 1, s.buttonLo());
                g.fill(ix, iy, ix + 1, iy + ih, s.buttonLo());
                return 1;
            }
            case ON -> {
                g.fillGradient(ix, iy, ix + iw, iy + ih, s.accent(), s.accent2());
                g.fill(ix, iy, ix + iw, iy + 1, s.accentHi());
                g.fill(ix, iy, ix + 1, iy + ih - 1, s.accentHi());
                g.fill(ix, iy + ih - 1, ix + iw, iy + ih, s.accentLo());
                g.fill(ix + iw - 1, iy + 1, ix + iw, iy + ih, s.accentLo());
            }
            default -> {
                g.fillGradient(ix, iy, ix + iw, iy + ih, state == Press.HOVER ? s.hi() : s.button(), s.button2());
                g.fill(ix, iy, ix + iw, iy + 1, s.hi());
                g.fill(ix, iy, ix + 1, iy + ih - 1, s.hi());
                g.fill(ix, iy + ih - 1, ix + iw, iy + ih, s.buttonLo());
                g.fill(ix + iw - 1, iy + 1, ix + iw, iy + ih, s.buttonLo());
            }
        }
        return 0;
    }

    /** 文字のボタン。選択中の文字色はスキンが決める（明るいアクセントでは濃い色）。 */
    static void labelButton(GuiGraphics g, Font font, GuiSkin s, int x, int y, int w, int h, Component label, Press state) {
        int d = button(g, s, x, y, w, h, state);
        int color = state == Press.ON ? s.onAccent() : state == Press.DISABLED ? s.dim() : s.text();
        AndroidStationScreen.fitCentered(g, font, label, x + 2, y + (h - 8) / 2 + d, w - 4, color);
    }

    /** 電源ボタン。印の色は稼働中が緑、停止中が赤（スキンに関係なく共通）。 */
    static void power(GuiGraphics g, GuiSkin s, int x, int y, int size, boolean on, boolean hover) {
        int d = button(g, s, x, y, size, size, hover ? Press.HOVER : Press.NORMAL);
        int c = on ? 0xFF3DBE66 : 0xFFD85050;
        int gx = x + (size - 7) / 2;
        int gy = y + (size - 7) / 2 + d;
        g.fill(gx + 3, gy, gx + 4, gy + 4, c);
        g.fill(gx + 1, gy + 1, gx + 2, gy + 2, c);
        g.fill(gx + 5, gy + 1, gx + 6, gy + 2, c);
        g.fill(gx, gy + 2, gx + 1, gy + 5, c);
        g.fill(gx + 6, gy + 2, gx + 7, gy + 5, c);
        g.fill(gx + 1, gy + 5, gx + 2, gy + 6, c);
        g.fill(gx + 5, gy + 5, gx + 6, gy + 6, c);
        g.fill(gx + 2, gy + 6, gx + 5, gy + 7, c);
    }

    /**
     * 右レールのタブ。connected のタブは枠と同じ面の色で枠の縁を上書きし、枠から引き出したように見せる。
     * frameY, frameH は枠の上端と高さ（面のグラデーションを枠と合わせる）。
     */
    static void railTab(GuiGraphics g, GuiSkin s, int x, int y, boolean connected, boolean hover, int frameY, int frameH) {
        int tw = connected ? 23 : 21;
        int h = 22;
        g.fill(x + 2, y + h, x + tw + 1, y + h + 1, SHADOW);
        g.fill(x + tw, y + 2, x + tw + 1, y + h, SHADOW);
        g.fill(x, y, x + tw - 1, y + 1, hover ? s.accent() : s.outline());
        g.fill(x, y + h - 1, x + tw - 1, y + h, hover ? s.accent() : s.outline());
        g.fill(x + tw - 1, y + 1, x + tw, y + h - 1, hover ? s.accent() : s.outline());
        if (connected) {
            float top = (float) (y + 1 - frameY) / frameH;
            float bottom = (float) (y + h - 1 - frameY) / frameH;
            g.fillGradient(x - 1, y + 1, x + tw - 1, y + h - 1, GuiSkin.mix(s.face(), s.face2(), top), GuiSkin.mix(s.face(), s.face2(), bottom));
            g.fill(x - 1, y + 1, x + tw - 1, y + 2, s.hi());
        } else {
            g.fillGradient(x + 1, y + 1, x + tw - 1, y + h - 1, hover ? s.tabHi() : s.tab(), s.tab2());
            g.fill(x + 1, y + 1, x + tw - 1, y + 2, s.tabHi());
        }
        g.fill(x, y + h - 2, x + tw - 1, y + h - 1, s.lo());
        g.fill(x + tw - 2, y + 2, x + tw - 1, y + h - 2, s.lo());
    }

    // ------------------------------------------------------------------ アイコン

    /** 面の設定のアイコン（上・左・右の3面が色分けされた立方体、16×16）。 */
    static void sidesIcon(GuiGraphics g, int x, int y) {
        int top = 0xFF5AA9E6;
        int left = 0xFFF0A040;
        int right = 0xFF58C27A;
        for (int r = 0; r < 4; r++) {
            g.fill(x + 7 - 2 * r, y + 2 + r, x + 9 + 2 * r, y + 3 + r, top);
            g.fill(x + 1 + 2 * r, y + 6 + r, x + 15 - 2 * r, y + 7 + r, top);
        }
        g.fill(x + 1, y + 7, x + 8, y + 13, left);
        g.fill(x + 1, y + 13, x + 8, y + 14, 0xFFC07A28);
        g.fill(x + 8, y + 7, x + 15, y + 13, right);
        g.fill(x + 8, y + 13, x + 15, y + 14, 0xFF3E9A5C);
        g.fill(x + 7, y + 6, x + 9, y + 14, 0xFFFFFFFF);
    }

    /** 文字列の点絵（x の位置を塗る）。 */
    static void sprite(GuiGraphics g, int x, int y, String[] rows, int c) {
        for (int j = 0; j < rows.length; j++) {
            for (int i = 0; i < rows[j].length(); i++) {
                if (rows[j].charAt(i) == 'x') {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, c);
                }
            }
        }
    }

    private static final String[] AUTO = {
            "...xxxx..", "x.x....x.", "xx......x", "xxxx....x", "........x",
            "x........", "x....xxxx", "x......xx", ".x....x.x", "..xxxx..."};
    private static final String[] BOX = {
            "xxxxxxxxx", "x.......x", "xxxxxxxxx", "x.......x", "x..xxx..x", "x.......x", "xxxxxxxxx"};
    private static final String[] DROP = {
            "...x...", "..xxx..", ".xxxxx.", "xxxxxxx", "xxxxxxx", "xxxxxxx", ".xxxxx.", "..xxx.."};
    private static final String[] EJECT = {
            "xx.......", "x.....x..", "x.....xx.", "x.....xxx", "x.xxxxxxxx", "x.....xxx", "x.....xx.", "x.....x..", "xx......."};
    private static final String[] PLUS = {"..x..", "..x..", "xxxxx", "..x..", "..x.."};
    private static final String[] MINUS = {"xxxxx"};
    private static final String[] STRUCTURE = {"xxxxxxx", "x..x..x", "x..x..x", "xxxxxxx", "x..x..x", "x..x..x", "xxxxxxx"};

    /** 値の行のアイコン（7×8 まで）。 */
    enum Icon { FLAME, SNOW, BOLT, DROP, PERCENT, HOURGLASS, IN, OUT, RANGE, SPEED, INFO, STAR, PERSON, BOX, ORB }

    private static final String[] FLAME = {
            "...x...", "..xx...", "..xxx..", ".xxxx.x", ".xxxxxx", "xxx.xxx", "xx...xx", ".xxxxx."};
    private static final String[] SNOW = {
            "x..x..x", ".x.x.x.", "..xxx..", "xxxxxxx", "..xxx..", ".x.x.x.", "x..x..x"};
    private static final String[] PERCENT = {
            "xx...x.", "xx..x..", "...x...", "..x....", ".x..xx.", "x...xx."};
    private static final String[] HOURGLASS = {
            "xxxxxxx", ".x...x.", "..x.x..", "...x...", "..x.x..", ".x...x.", "xxxxxxx"};
    private static final String[] IN = {
            "...x...", "...x...", ".x.x.x.", "..xxx..", "...x...", "x.....x", "xxxxxxx"};
    private static final String[] OUT = {
            "...x...", "..xxx..", ".x.x.x.", "...x...", "...x...", "x.....x", "xxxxxxx"};

    private static final String[] RANGE = {
            "..xxx..", ".x...x.", "x..x..x", "x.xxx.x", "x..x..x", ".x...x.", "..xxx.."};
    private static final String[] SPEED = {"x..x...", ".x..x..", "..x..x.", ".x..x..", "x..x..."};
    private static final String[] INFO = {
            ".xxxxx.", "x..x..x", "x.....x", "x..x..x", "x..x..x", "x..x..x", ".xxxxx."};
    private static final String[] STAR = {
            "...x...", "..xxx..", "xxxxxxx", ".xxxxx.", "..xxx..", ".xx.xx.", "x.....x"};
    private static final String[] ORB = {"..xxx..", ".xxxxx.", "xx.xxxx", "xxxxxxx", "xxxxxxx", ".xxxxx.", "..xxx.."};
    private static final String[] PERSON = {"..xxx..", "..xxx..", "...x...", ".xxxxx.", "x.xxx.x", "..x.x..", "..x.x.."};

    static void icon(GuiGraphics g, int x, int y, Icon icon, int c) {
        switch (icon) {
            case FLAME -> sprite(g, x, y, FLAME, c);
            case SNOW -> sprite(g, x, y, SNOW, c);
            case BOLT -> boltIcon(g, x, y, c);
            case DROP -> sprite(g, x, y, DROP, c);
            case PERCENT -> sprite(g, x, y + 1, PERCENT, c);
            case HOURGLASS -> sprite(g, x, y, HOURGLASS, c);
            case IN -> sprite(g, x, y, IN, c);
            case OUT -> sprite(g, x, y, OUT, c);
            case RANGE -> sprite(g, x, y, RANGE, c);
            case SPEED -> sprite(g, x, y + 1, SPEED, c);
            case INFO -> sprite(g, x, y, INFO, c);
            case STAR -> sprite(g, x, y, STAR, c);
            case PERSON -> sprite(g, x, y, PERSON, c);
            case BOX -> sprite(g, x - 1, y, BOX, c);
            case ORB -> sprite(g, x, y, ORB, c);
        }
    }

    /** 彫り込んだ下向きの矢印（入口から出口へ、9×9）。 */
    static void downArrow(GuiGraphics g, GuiSkin s, int x, int y) {
        for (int pass = 0; pass < 2; pass++) {
            int c = pass == 0 ? s.arrowHi() : s.arrow();
            int oy = pass == 0 ? 1 : 0;
            g.fill(x + 3, y + oy, x + 6, y + 5 + oy, c);
            for (int i = 0; i < 5; i++) {
                g.fill(x + i, y + 4 + i + oy, x + 9 - i, y + 5 + i + oy, c);
            }
        }
    }

    /** 自動（ぐるりと回る矢印、9×10）。 */
    static void autoIcon(GuiGraphics g, int x, int y, int c) {
        sprite(g, x, y, AUTO, c);
    }

    /** アイテム（箱、9×7）。 */
    static void boxIcon(GuiGraphics g, int x, int y, int c) {
        sprite(g, x, y, BOX, c);
    }

    /** 液体（しずく、7×8）。入れるタンクは下向き、出すタンクは上向きの白い矢印。 */
    static void dropIcon(GuiGraphics g, int x, int y, int c, boolean input) {
        sprite(g, x, y, DROP, c);
        int w = 0xFFFFFFFF;
        g.fill(x + 3, y + 3, x + 4, y + 6, w);
        int head = input ? y + 5 : y + 3;
        g.fill(x + 2, head, x + 5, head + 1, w);
    }

    /** 自動排出（壁から外へ出る矢印、10×9）。 */
    static void ejectIcon(GuiGraphics g, int x, int y, int c) {
        sprite(g, x, y, EJECT, c);
    }

    static void plusIcon(GuiGraphics g, int x, int y, int c) {
        sprite(g, x, y, PLUS, c);
    }

    static void minusIcon(GuiGraphics g, int x, int y, int c) {
        sprite(g, x, y + 2, MINUS, c);
    }

    /** マルチブロックの大きさ（格子、7×7）。 */
    static void structureIcon(GuiGraphics g, int x, int y, int c) {
        sprite(g, x, y, STRUCTURE, c);
    }

    private static final String[] GEAR = {
            "....xxx....", ".xx.xxx.xx.", ".xxxxxxxxx.", "..xxxxxxx..", "xxxxx.xxxxx", "xxxx...xxxx", "xxxxx.xxxxx",
            "..xxxxxxx..", ".xxxxxxxxx.", ".xx.xxx.xx.", "....xxx...."};

    /** 歯車（設定、11×11）。 */
    static void gearIcon(GuiGraphics g, int x, int y, int c) {
        sprite(g, x, y, GEAR, c);
    }

    /** 稲妻の印（電力だけで作るスイッチ）。 */
    static void boltIcon(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 4, y, x + 6, y + 2, c);
        g.fill(x + 3, y + 2, x + 5, y + 4, c);
        g.fill(x + 2, y + 4, x + 6, y + 5, c);
        g.fill(x + 4, y + 5, x + 6, y + 6, c);
        g.fill(x + 3, y + 6, x + 5, y + 8, c);
    }

    // ------------------------------------------------------------------ まだ骨格に移していない画面用（標準スキン）

    /** 画面の地（枠と、上の発光ライン）。 */
    static void background(GuiGraphics g, int x, int y, int w, int h) {
        frame(g, GuiSkin.STANDARD, x, y, w, h);
        glowLine(g, GuiSkin.STANDARD, x + 4, y + 2, w - 8);
    }

    /** 空のスロットに、入れる物のグレーの影を描く（textures/gui/ghost/<id>.png。データ生成で作る）。 */
    static void ghost(GuiGraphics g, int x, int y, String id) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(io.github.genichimaruo.singulo.Singulo.id("textures/gui/ghost/" + id + ".png"), x, y, 0, 0, 16, 16, 16, 16);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    static void slot(GuiGraphics g, int x, int y) {
        slot(g, GuiSkin.STANDARD, x, y);
    }

    static void well(GuiGraphics g, int x, int y, int w, int h) {
        well(g, GuiSkin.STANDARD, x, y, w, h);
    }

    static void verticalBar(GuiGraphics g, int x, int y, int w, int h, double fraction, int color) {
        gauge(g, GuiSkin.STANDARD, x, y, w, h, fraction, color);
    }

    static void arrow(GuiGraphics g, int x, int y, double fraction) {
        arrow(g, GuiSkin.STANDARD, x, y, fraction);
    }

    /** 電源スイッチ（10×10）。 */
    static void powerButton(GuiGraphics g, int x, int y, boolean on, boolean hover) {
        power(g, GuiSkin.STANDARD, x, y, 10, on, hover);
    }

    /** 蓄電のツールチップ（K・M・G で縮め、縮めたときは「Shift: 正確な値」を添える）。 */
    static java.util.List<Component> energyLines(long stored, long capacity) {
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.translatable("gui.singulo.energy", io.github.genichimaruo.singulo.energy.EnergyText.of(stored),
                io.github.genichimaruo.singulo.energy.EnergyText.of(capacity)));
        if (io.github.genichimaruo.singulo.energy.EnergyText.abbreviated(capacity)) {
            lines.add(io.github.genichimaruo.singulo.energy.EnergyText.hint());
        }
        return lines;
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    /** Keep wrapped explanatory tooltips, including their border, inside small windows. */
    static void tooltip(GuiGraphics g, net.minecraft.client.gui.Font font, java.util.List<net.minecraft.network.chat.Component> text,
                        int maxWidth, int mouseX, int mouseY) {
        var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (var line : text) lines.addAll(font.split(line, Math.min(maxWidth, g.guiWidth() - 8)));
        g.renderTooltip(font, lines, (screenWidth, screenHeight, x, y, width, height) -> {
            var preferred = net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE
                    .positionTooltip(screenWidth, screenHeight, x, y, width, height);
            return new org.joml.Vector2i(Math.max(4, Math.min(preferred.x(), screenWidth - width - 4)),
                    Math.max(4, Math.min(preferred.y(), screenHeight - height - 4)));
        }, mouseX, mouseY);
    }

}
