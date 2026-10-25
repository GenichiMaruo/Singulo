package io.github.genichimaruo.singulo.energy;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * 電力（FE・FE/t）の数を文字にする。ふだんは K・M・G・T を付けて3桁に縮め、
 * 詳しく見たいとき（クライアントで Shift を押している間）は桁区切りの正確な値にする。
 */
public final class EnergyText {
    /** 正確な値を出すか。クライアントの起動時に「Shift を押しているか」を入れる。サーバーでは常に縮める。 */
    public static BooleanSupplier detail = () -> false;
    private static final String[] UNITS = {"K", "M", "G", "T", "P"};

    private EnergyText() {}

    public static String of(long fe) {
        return detail.getAsBoolean() ? String.format(Locale.ROOT, "%,d", fe) : shortForm(fe);
    }

    public static String of(double fe) {
        if (!detail.getAsBoolean()) {
            return shortForm(fe);
        }
        return fe == Math.rint(fe) ? String.format(Locale.ROOT, "%,d", (long) fe) : String.format(Locale.ROOT, "%,.1f", fe);
    }

    /** ツールチップに添える「Shift: 正確な値」（暗い文字）。 */
    public static net.minecraft.network.chat.Component hint() {
        return net.minecraft.network.chat.Component.translatable("gui.singulo.shift_detail").withStyle(net.minecraft.ChatFormatting.DARK_GRAY);
    }

    /** 縮めて見せているか（ツールチップに「Shift: 正確な値」を添えるかどうか）。 */
    public static boolean abbreviated(double fe) {
        return Math.abs(fe) >= 1000 && !detail.getAsBoolean();
    }

    /** 3桁に縮める（999 → 999、12345 → 12.3K、4500000 → 4.5M）。 */
    public static String shortForm(double fe) {
        double a = Math.abs(fe);
        if (a < 1000) {
            return fe == Math.rint(fe) ? String.valueOf((long) fe) : String.format(Locale.ROOT, "%.1f", fe);
        }
        int unit = -1;
        while (a >= 999.5 && unit < UNITS.length - 1) {
            a /= 1000;
            unit++;
        }
        String number = String.format(Locale.ROOT, a < 9.995 ? "%.2f" : a < 99.95 ? "%.1f" : "%.0f", a);
        if (number.contains(".")) {
            number = number.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return (fe < 0 ? "-" : "") + number + UNITS[unit];
    }
}
