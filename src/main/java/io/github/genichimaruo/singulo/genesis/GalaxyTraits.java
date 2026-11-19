package io.github.genichimaruo.singulo.genesis;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 創世炉が生む銀河の性質（0.21.0）。生まれる前（揺籃・種火・安定化）の核に素材を使うと決まる。1つの int に詰める:
 * 下の3ビットが形、次の5ビットが腕の色（0 は種まかせ、1〜16 は染料の色+1）、その上の1ビットが大きな銀河。
 * 0 はそれまでの版と同じ（形は渦巻、色は種まかせ）なので、以前に生まれた銀河はそのまま見える。
 * <ul>
 * <li>染料: 腕の色</li>
 * <li>ブレイズロッド: 棒渦巻（中心の棒の両端から腕）・スライムボール: 楕円（腕のない星の集まり）・
 * エンダーアイ: 環（輪のような銀河）・マグマクリーム: 不規則（星の生まれる塊）</li>
 * <li>ネザースター: 大きな銀河（夜空で 1.5 倍）</li>
 * </ul>
 */
public final class GalaxyTraits {
    public static final int SPIRAL = 0, BARRED = 1, ELLIPTICAL = 2, RING = 3, IRREGULAR = 4;
    static final String[] FORMS = {"spiral", "barred", "elliptical", "ring", "irregular"};
    private static final int FORM_MASK = 7, COLOR_SHIFT = 3, COLOR_MASK = 31 << COLOR_SHIFT, GRAND = 1 << 8;

    private GalaxyTraits() {}

    public static int form(int traits) {
        int f = traits & FORM_MASK;
        return f < FORMS.length ? f : SPIRAL;
    }

    /** 腕の色の染料（なければ null）。 */
    @org.jspecify.annotations.Nullable
    public static DyeColor dye(int traits) {
        int c = ((traits & COLOR_MASK) >> COLOR_SHIFT) - 1;
        return c >= 0 && c < 16 ? DyeColor.byId(c) : null;
    }

    public static boolean grand(int traits) {
        return (traits & GRAND) != 0;
    }

    /** 素材 stack を使ったあとの性質（素材でなければ -1。もう同じ性質なら traits のまま）。 */
    public static int apply(int traits, ItemStack stack) {
        var dye = DyeColor.getColor(stack);
        if (dye != null) {
            return traits & ~COLOR_MASK | (dye.getId() + 1) << COLOR_SHIFT;
        }
        int form = stack.is(Items.BLAZE_ROD) ? BARRED : stack.is(Items.SLIME_BALL) ? ELLIPTICAL : stack.is(Items.ENDER_EYE) ? RING
                : stack.is(Items.MAGMA_CREAM) ? IRREGULAR : -1;
        if (form >= 0) {
            return traits & ~FORM_MASK | form;
        }
        if (stack.is(Items.NETHER_STAR)) {
            return traits | GRAND;
        }
        return -1;
    }

    /** 性質の説明（「棒渦巻・赤・大きな銀河」）。 */
    public static Component describe(int traits) {
        List<Component> parts = new ArrayList<>();
        parts.add(Component.translatable("galaxy.singulo.form." + FORMS[form(traits)]));
        DyeColor dye = dye(traits);
        parts.add(dye == null ? Component.translatable("galaxy.singulo.color.random")
                : Component.translatable("color.minecraft." + dye.getName()));
        if (grand(traits)) {
            parts.add(Component.translatable("galaxy.singulo.grand"));
        }
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(Component.translatable("galaxy.singulo.separator"));
            }
            out.append(parts.get(i));
        }
        return out;
    }
}
