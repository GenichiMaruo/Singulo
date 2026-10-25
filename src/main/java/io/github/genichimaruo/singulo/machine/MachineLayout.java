package io.github.genichimaruo.singulo.machine;

/**
 * 汎用加工装置のGUI配置。メニュー（スロット位置）と画面（描画）で同じ値を使う。
 * 左端の電力バーのあと、入力タンク → 入力スロット → 進捗矢印 → 出力スロット → 出力タンク の順に並べ、
 * 残りの幅の中央に寄せる。枠の幅は GuiFrame の3段階のどれか（インベントリは常に中央）。
 * 単極子アップグレードと面の設定は枠の外の右レールに置く。
 */
public final class MachineLayout {
    /** 面の設定やレシピ選択の板を、インベントリの見出しを隠さずに重ねられる高さ。 */
    public static final int HEIGHT = 184;
    public static final int TOP = 22;
    public static final int BAR_HEIGHT = 60;
    public static final int ENERGY_X = 8;
    public static final int TANK_WIDTH = 12;
    public static final int TANK_STEP = 16;
    public static final int ARROW_WIDTH = 22;
    /** 1段のときのスロットと矢印の高さ、2段のときの上の段の高さ。 */
    private static final int ROW_Y = 44;
    private static final int TWO_ROWS_Y = 35;

    public final int width;
    /** プレイヤーのインベントリの左端。 */
    public final int inventoryX;
    public final int[] fluidInX;
    public final int[] inputX;
    public final int[] inputY;
    public final int arrowX;
    public final int arrowY;
    public final int outputX;
    public final int[] outputY;
    public final int[] fluidOutX;
    /** 大量生産モードのボタン（触媒スロットと同じく、進捗矢印の下）と、その右の質量のバー。 */
    public final int modeX;
    public final int modeY;
    public final int massBarX;
    public final int massBarWidth;
    /** 作るものを選ぶボタン（出力スロットの右）。 */
    public final int selectX;
    public final int selectY;
    /** 触媒スロット（触媒を使う装置だけ）。進捗矢印の下。 */
    public final int catalystX;
    public final int catalystY;
    /** 単極子アップグレードのスロット（右レールの強化の段）。 */
    public final int upgradeX;
    public final int upgradeY;

    public MachineLayout(MachineType type) {
        int n = type.inputSlots();
        int cols = n <= 2 ? n : n <= 4 ? 2 : 3;
        int outs = type.outputSlots();
        int rowY = ROW_Y;

        int x = ENERGY_X + 14;
        int[] fluidIn = new int[type.fluidInputs()];
        for (int i = 0; i < fluidIn.length; i++) {
            fluidIn[i] = x;
            x += TANK_STEP;
        }
        int slotsStart = x + 4;
        int[] inX = new int[n];
        int[] inY = new int[n];
        for (int i = 0; i < n; i++) {
            inX[i] = slotsStart + 18 * (i % cols);
            inY[i] = n <= 2 ? rowY : TWO_ROWS_Y + 18 * (i / cols);
        }
        x = n == 0 ? x : slotsStart + 18 * cols;
        int arrow = x + 4;
        int output = arrow + ARROW_WIDTH + 6;
        x = type.hasOutput() ? output + 22 : arrow + ARROW_WIDTH + 6;
        int select = x + 2;
        if (type.selectsRecipe()) {
            x += 24;
        }
        int[] fluidOut = new int[type.fluidOutputs()];
        for (int i = 0; i < fluidOut.length; i++) {
            fluidOut[i] = x;
            x += TANK_STEP;
        }

        width = GuiFrame.width(x + 4);
        // タンクも含めた中身の左端と右端を、電力バーの右端から枠の右の余白までの真ん中に置く
        int left = fluidIn.length > 0 ? fluidIn[0] : n > 0 ? slotsStart : arrow;
        int right = fluidOut.length > 0 ? fluidOut[fluidOut.length - 1] + TANK_WIDTH
                : type.selectsRecipe() ? select + 18 : type.hasOutput() ? output + 16 : arrow + ARROW_WIDTH;
        int shift = Math.max(0, (ENERGY_X + 8 + width - 8 - left - right) / 2);
        fluidInX = shifted(fluidIn, shift);
        fluidOutX = shifted(fluidOut, shift);
        inputX = shifted(inX, shift);
        inputY = inY;
        arrowX = arrow + shift;
        outputX = output + shift;
        outputY = new int[outs];
        for (int i = 0; i < outs; i++) {
            outputY[i] = outs == 1 ? rowY : TWO_ROWS_Y + 18 * i;
        }
        arrowY = rowY;
        modeX = arrowX + 3;
        modeY = arrowY + 22;
        // 質量のバーはモードのボタンの右から出力スロットの右端まで（右の出力タンクにかからない）
        massBarX = modeX + 20;
        massBarWidth = Math.max(8, outputX + 16 - massBarX);
        selectX = select + shift;
        selectY = rowY;
        catalystX = arrowX + 3;
        catalystY = arrowY + 22;
        inventoryX = GuiFrame.inventoryX(width);
        upgradeX = GuiFrame.railSlotX(width);
        upgradeY = GuiFrame.railSlotY(GuiFrame.RAIL_UPGRADE);
    }

    private static int[] shifted(int[] xs, int shift) {
        int[] out = new int[xs.length];
        for (int i = 0; i < xs.length; i++) {
            out[i] = xs[i] + shift;
        }
        return out;
    }
}
