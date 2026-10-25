package io.github.genichimaruo.singulo.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * アンドロイドの顔のアイコン（11×11）。実際のテクスチャの顔（8×8）をデフォルメしたもの:
 * まっすぐで長めの前髪、上まぶた・白目と瞳・瞳の下の発光色の目、頬の赤み、横に流れる髪。
 * 髪と瞳の色は型ごとにテクスチャ（textures/entity/<型>_android.png の顔）から取った値。テクスチャを描き直したら合わせる。
 */
final class AndroidFace {
    private static final String[] FACE = {
            "..ooooooo..", ".ohbLbbbho.", "ohbbbbbbbho", "ohbbbbbbbho", "ohhshhhshho", "ohllsssllho",
            "ohwissswiho", "oheessseeho", "ohrsssssrho", "ohhSsssShho", "oho.ccc.oho"};

    /** 髪・前髪・縁（いちばん暗い髪）・瞳・瞳の下の発光色。 */
    private record Colors(int hair, int bangs, int edge, int iris, int glow) {}

    private AndroidFace() {}

    /** 型のアクセント色から、その型の顔の色を選ぶ（知らない色はルミナの顔をアクセントで染める）。 */
    private static Colors colors(int accent) {
        return switch (accent & 0xFFFFFF) {
            case 0x78E1FF -> new Colors(0xD0D7E5, 0xEEF1F7, 0xB2BCD0, 0x2880B6, 0x6ED6FA);   // ルミナ（光刃）
            case 0xFF6AC4 -> new Colors(0x201E2A, 0x2C2A38, 0x14131C, 0x962878, 0xFA7AD2);   // ヴェルナ（鞭）
            case 0xFF6E40 -> new Colors(0x954B31, 0xBA6646, 0x703624, 0x96341E, 0xFF9060);   // アルマ（斧）
            case 0xB080FF -> new Colors(0x242A4C, 0x323A62, 0x181C36, 0x543EB0, 0xCCB0FF);   // ノクス（ビット）
            case 0x6EF0D2 -> new Colors(0xA8CAE8, 0xCCE4F6, 0x84A8D0, 0x1E768C, 0x78ECDE);   // シエル（射撃）
            case 0xFFC45A -> new Colors(0xC0C6D4, 0xE2E6EE, 0xA0A6B6, 0xA86C1C, 0xFFD470);   // ソレイユ（光剣）
            default -> new Colors(0xD0D7E5, 0xEEF1F7, 0xB2BCD0, GuiSkin.mix(0xFF000000 | accent, 0xFF000000, 0.4F), accent);
        };
    }

    static void draw(GuiGraphics g, int x, int y, int accent) {
        Colors c = colors(accent);
        for (int j = 0; j < FACE.length; j++) {
            String row = FACE[j];
            for (int i = 0; i < row.length(); i++) {
                int color = switch (row.charAt(i)) {
                    case 'o' -> GuiSkin.mix(0xFF000000 | c.edge(), 0xFF2A2F3A, 0.35F);
                    case 'h' -> c.hair();
                    case 'b' -> c.bangs();
                    case 'L' -> GuiSkin.mix(0xFF000000 | c.bangs(), 0xFFFFFFFF, 0.55F);
                    case 's' -> 0xFFECE4;
                    case 'S' -> 0xEED6CE;
                    case 'c' -> 0xD9B3A8;
                    case 'l' -> c.edge();
                    case 'w' -> 0xFCFDFF;
                    case 'i' -> c.iris();
                    case 'e' -> c.glow();
                    case 'r' -> 0xF6BAC2;
                    default -> -1;
                };
                if (color != -1) {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, 0xFF000000 | color);
                }
            }
        }
    }
}
