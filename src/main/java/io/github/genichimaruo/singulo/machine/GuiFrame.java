package io.github.genichimaruo.singulo.machine;

/**
 * 全画面共通のGUIの骨格。メニュー（スロット位置）と画面（描画）で同じ値を使う。
 * 上から ヘッダー → 装置固有の部分 → 溝 → プレイヤーのインベントリ の4段で、共通の機能は枠の外の右レールに置く。
 * 右レールは枠の幅に含めないので、枠もインベントリも常に画面の中央に来る。
 */
public final class GuiFrame {
    /** 枠の幅は3段階（差はスロット2列ぶん）。どれも偶数なので、インベントリの左端が整数になる。 */
    private static final int[] WIDTHS = {176, 212, 248};
    /** ヘッダーの下の区切りを含めた高さ。装置固有の部分はここから始まる。 */
    public static final int HEADER = 19;
    /** ヘッダー右上の電源ボタン。 */
    public static final int POWER_Y = 4;
    public static final int POWER_SIZE = 11;
    /** 右レールのタブ。段の役割は固定（使わない段は詰めずに空ける）。 */
    public static final int RAIL_TOP = 4;
    public static final int RAIL_PITCH = 24;
    public static final int RAIL_TAB = 22;
    /** 枠の右端からはみ出す幅（開いているタブを含む）。 */
    public static final int RAIL_WIDTH = 24;
    public static final int RAIL_SETTINGS = 0;
    public static final int RAIL_UPGRADE = 1;
    public static final int RAIL_AUTOMATION = 2;
    public static final int RAIL_HELP = 3;
    public static final int RAIL_ROWS = 4;

    private GuiFrame() {}

    /** 中身が必要とする幅を、3段階のどれかに切り上げる。 */
    public static int width(int needed) {
        for (int w : WIDTHS) {
            if (needed <= w) {
                return w;
            }
        }
        return WIDTHS[WIDTHS.length - 1];
    }

    /** プレイヤーのインベントリの左端（スロットの内側）。枠の中央。 */
    public static int inventoryX(int width) {
        return (width - 162) / 2 + 1;
    }

    /** インベントリの上端。下端から測る。 */
    public static int inventoryY(int height) {
        return height - 82;
    }

    public static int hotbarY(int height) {
        return height - 24;
    }

    /** 「インベントリ」の見出しの行。 */
    public static int inventoryLabelY(int height) {
        return height - 93;
    }

    /** 装置とインベントリの間の溝。 */
    public static int grooveY(int height) {
        return height - 96;
    }

    /** 装置固有の部分の下端（この行は含まない）。 */
    public static int contentBottom(int height) {
        return height - 98;
    }

    public static int powerX(int width) {
        return width - 4 - POWER_SIZE;
    }

    public static int railX(int width) {
        return width - 1;
    }

    public static int railY(int row) {
        return RAIL_TOP + RAIL_PITCH * row;
    }

    /** レールの段に入れるスロット（強化の段）。 */
    public static int railSlotX(int width) {
        return railX(width) + 3;
    }

    public static int railSlotY(int row) {
        return railY(row) + 3;
    }
}
