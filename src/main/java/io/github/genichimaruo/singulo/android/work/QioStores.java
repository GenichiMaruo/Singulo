package io.github.genichimaruo.singulo.android.work;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

/**
 * Mekanism の QIO を、作業のブロック（作業指令台・資材口・納品口など）の隣のストレージとして使う窓口。
 * リンク済みの QIO ダッシュボードの裏が作業のブロックに接していれば、その周波数の中身をチェストと同じように数え、出し入れする。
 * 中身は {@code compat.mekanism.MekanismQio} が受け持つ（Mekanism がなければ何もしない。ここより外では Mekanism の型を使わない）。
 */
public final class QioStores {
    private QioStores() {}

    /** 中身を受け持つもの。 */
    public interface Access {
        /**
         * pos が、裏を toDashboard の反対側（作業のブロックの側）に向けたリンク済みの QIO ダッシュボードなら、その周波数の中身。
         * toDashboard は作業のブロックからダッシュボードへの向き。違えば null。
         */
        @Nullable
        IItemHandler dashboard(Level level, BlockPos pos, Direction toDashboard);

        /** dashboard が返した入れ物の通し番号（同じ周波数なら同じ番号）。 */
        int id(IItemHandler handler);
    }

    private static final Access ACCESS = load();

    private static Access load() {
        if (net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
            try {
                return (Access) Class.forName("io.github.genichimaruo.singulo.compat.mekanism.MekanismQio").getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError e) {
                io.github.genichimaruo.singulo.Singulo.LOGGER.warn("QIO の連携を読み込めなかった", e);
            }
        }
        return null;
    }

    /**
     * 数えたストレージの印（場所の集まりに入れて、同じ周波数を二重に数えないため）。実際の場所とは決して重ならない
     * （x と y が int の最小値の場所）。
     */
    public static BlockPos seenKey(IItemHandler handler) {
        return new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, ACCESS == null ? 0 : ACCESS.id(handler));
    }

    /** {@link Access#dashboard}（Mekanism がなければ null）。 */
    @Nullable
    public static IItemHandler dashboard(Level level, BlockPos pos, Direction toDashboard) {
        return ACCESS == null ? null : ACCESS.dashboard(level, pos, toDashboard);
    }
}
