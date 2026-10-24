package io.github.genichimaruo.singulo.client;

/**
 * GUIの配色。骨格・座標・部品の形は全画面で共通にし、色だけをスキンで切り替える。
 * 光は左上から当たる: 凸（押せるもの）は上と左が明るく、凹（入れ物）は上と左が暗い。
 */
record GuiSkin(
        int outline, int hi, int face, int face2, int lo,
        int plate, int plate2, int seam, int seamHi, int glow, int glow2,
        int dark, int fill, int shade, int slotHi,
        int text, int dim,
        int button, int button2, int buttonLo,
        int accent, int accent2, int accentHi, int accentLo, int onAccent,
        int chip, int arrow, int arrowHi, int tab, int tab2, int tabHi,
        int corner, boolean stars) {

    /** 白い外装（加工装置・発電機・物流）。 */
    static final GuiSkin STANDARD = new GuiSkin(
            0xFF56626C, 0xFFFFFFFF, 0xFFEBEFF2, 0xFFDCE2E7, 0xFFB1BAC3,
            0xFFF6F8F9, 0xFFE5EAEE, 0xFFC4CAD0, 0xFFFFFFFF, 0xFF78D2F0, 0xFF78D2F0,
            0xFF2B333A, 0xFF5A646E, 0xFF4E5862, 0xFFFFFFFF,
            0xFF33404A, 0xFF6E7A85,
            0xFFF3F6F8, 0xFFD8DEE3, 0xFF9DA8B2,
            0xFF4AAED2, 0xFF2E88AA, 0xFFB0E6F8, 0xFF1E6886, 0xFFFFFFFF,
            0xFF2A3137, 0xFFC3CAD1, 0xFFFFFFFF, 0xFFD2D8DD, 0xFFC5CCD2, 0xFFE9EDF0,
            1, false);

    /** 終盤の装置（観測装置・創世炉の核・ワームホール・ペンローズ）。発光ラインはシアンからマゼンタ、地に星。 */
    static final GuiSkin COSMIC = new GuiSkin(
            0xFF04050B, 0xFF3B3577, 0xFF141A34, 0xFF0B0F21, 0xFF05070E,
            0xFF1C2244, 0xFF121732, 0xFF05070E, 0xFF252B55, 0xFF7FE3FF, 0xFFE46CFF,
            0xFF010207, 0xFF151B31, 0xFF0F1427, 0xFF363070,
            0xFFD8E4FF, 0xFF8D96BC,
            0xFF2A3060, 0xFF1A1F40, 0xFF080A18,
            0xFF7B5CFF, 0xFF5A3FD6, 0xFFC2B4FF, 0xFF34248F, 0xFFFFFFFF,
            0xFF03040A, 0xFF05070E, 0xFF2B3366, 0xFF0E1228, 0xFF0A0D1E, 0xFF252B55,
            1, true);

    /** アンドロイド: 個体のアクセント1色から作る。角だけ2px落として少し柔らかく。 */
    static GuiSkin android(int accent) {
        int a = 0xFF000000 | accent;
        return new GuiSkin(
                mix(0xFF3A414C, a, 0.28F), 0xFFFFFFFF, mix(0xFFF3F5F7, a, 0.06F), mix(0xFFE4E8EC, a, 0.10F), mix(0xFFB4BCC5, a, 0.18F),
                mix(0xFFFFFFFF, a, 0.30F), mix(0xFFFFFFFF, a, 0.45F), mix(0xFFC4CAD0, a, 0.25F), 0xFFFFFFFF, a, a,
                mix(0xFF2B333A, a, 0.12F), mix(0xFF5A646E, a, 0.12F), mix(0xFF4E5862, a, 0.12F), 0xFFFFFFFF,
                mix(0xFF2E3640, a, 0.22F), mix(0xFF6E7A85, a, 0.20F),
                0xFFFAFBFC, mix(0xFFDDE2E6, a, 0.12F), mix(0xFF9DA8B2, a, 0.25F),
                a, mix(a, 0xFF000000, 0.15F), mix(a, 0xFFFFFFFF, 0.55F), mix(a, 0xFF000000, 0.35F),
                luminance(a) > 0.55F ? mix(a, 0xFF000000, 0.72F) : 0xFFFFFFFF,
                mix(0xFF2A3137, a, 0.15F), mix(0xFFC3CAD1, a, 0.15F), 0xFFFFFFFF,
                mix(0xFFD2D8DD, a, 0.12F), mix(0xFFC5CCD2, a, 0.16F), mix(0xFFE9EDF0, a, 0.12F),
                2, false);
    }

    /** 状態の色（スキンに関係なく共通）。 */
    static final int OK = 0xFF5BD07E;
    static final int IDLE = 0xFFA3AEB8;
    static final int WARN = 0xFFF0B450;
    static final int BAD = 0xFFF06A6A;

    static int mix(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static float luminance(int c) {
        return (0.2126F * ((c >> 16) & 255) + 0.7152F * ((c >> 8) & 255) + 0.0722F * (c & 255)) / 255F;
    }
}
