package io.github.genichimaruo.singulo.compat.jei;

import javax.annotation.Nullable;
import mezz.jei.api.runtime.IJeiRuntime;

/** JEI の検索欄の文字の読み書き（作業指令台の注文タブの検索欄と連携するため）。JEI を入れているときだけ呼ぶ。 */
public final class JeiSearch {
    @Nullable
    static volatile IJeiRuntime runtime;

    private JeiSearch() {}

    /** JEI の検索欄の文字（JEI がまだ使えなければ null）。 */
    @Nullable
    public static String get() {
        IJeiRuntime r = runtime;
        return r == null ? null : r.getIngredientFilter().getFilterText();
    }

    /** JEI の検索欄を text にする（同じなら何もしない）。 */
    public static void set(String text) {
        IJeiRuntime r = runtime;
        if (r != null && !text.equals(r.getIngredientFilter().getFilterText())) {
            r.getIngredientFilter().setFilterText(text);
        }
    }
}
