package io.github.genichimaruo.singulo.machine;

/**
 * 汎用加工装置のGUI配置。メニュー（スロット位置）と画面（描画）で同じ値を使う。
 * 左から 電力バー → 入力タンク → 入力スロット → 進捗矢印 → 出力スロット → 出力タンク の順に並べ、
 * 標準の幅176に収まらない装置は画面を横に広げる（プレイヤーのインベントリは中央に置く）。
 */
public final class MachineLayout {
    public static final int MIN_WIDTH = 176;
    public static final int HEIGHT = 166;
    public static final int TOP = 17;
    public static final int BAR_HEIGHT = 52;
    public static final int ENERGY_X = 8;
    public static final int TANK_WIDTH = 12;
    public static final int TANK_STEP = 16;
    public static final int ARROW_WIDTH = 22;
    public static final int ARROW_Y = 35;
    public static final int MODE_Y = 55;

    public final int width;
    /** プレイヤーのインベントリの左端。 */
    public final int inventoryX;
    public final int[] fluidInX;
    public final int[] inputX;
    public final int[] inputY;
    public final int arrowX;
    public final int outputX;
    public final int[] outputY;
    public final int[] fluidOutX;
    public final int modeX;
    /** 触媒スロット（触媒を使う装置だけ）。進捗矢印の下。 */
    public final int catalystX;
    public final int catalystY;
    /** 右端の列: 単極子アップグレードのスロットと、面の設定ボタン。 */
    public final int sideColumnX;
    public static final int UPGRADE_Y = 20;
    public static final int SIDES_Y = 44;
    /** 面の設定ボタンの大きさ。 */
    public static final int SIDES_SIZE = 10;
    public static final int SIDE_COLUMN = 24;

    public MachineLayout(MachineType type) {
        int x = ENERGY_X + 14;
        fluidInX = new int[type.fluidInputs()];
        for (int i = 0; i < fluidInX.length; i++) {
            fluidInX[i] = x;
            x += TANK_STEP;
        }
        int n = type.inputSlots();
        int cols = n <= 2 ? n : n <= 4 ? 2 : 3;
        inputX = new int[n];
        inputY = new int[n];
        int slotsStart = x + 4;
        for (int i = 0; i < n; i++) {
            inputX[i] = slotsStart + 18 * (i % cols);
            inputY[i] = n <= 2 ? 35 : 26 + 18 * (i / cols);
        }
        x = n == 0 ? x : slotsStart + 18 * cols;
        arrowX = x + 4;
        outputX = arrowX + ARROW_WIDTH + 6;
        int outs = type.outputSlots();
        outputY = new int[outs];
        for (int i = 0; i < outs; i++) {
            outputY[i] = outs == 1 ? 35 : 26 + 18 * i;
        }
        x = type.hasOutput() ? outputX + 22 : arrowX + ARROW_WIDTH + 6;
        fluidOutX = new int[type.fluidOutputs()];
        for (int i = 0; i < fluidOutX.length; i++) {
            fluidOutX[i] = x;
            x += TANK_STEP;
        }
        modeX = slotsStart;
        catalystX = arrowX + 3;
        catalystY = ARROW_Y + 20;
        int body = Math.max(MIN_WIDTH, x + 4);
        sideColumnX = body;
        width = body + SIDE_COLUMN;
        inventoryX = (body - MIN_WIDTH) / 2 + 8;
    }
}
