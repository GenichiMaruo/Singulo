package io.github.genichimaruo.singulo.android.program;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * プログラムで呼べる関数の一覧と、受け取る引数の数（訳すときに確かめる）。
 * host が true のものはアンドロイドが行う（{@link Vm.Host}）。action が true のものは時間がかかり、終わるまでプログラムが待つ。
 */
public final class Library {
    /** max が -1 なら、いくつでも。 */
    public record Sig(int min, int max, boolean host, boolean action) {
        boolean accepts(int n) {
            return n >= min && (max < 0 || n <= max);
        }
    }

    public static final Map<String, Sig> FUNCTIONS = new LinkedHashMap<>();

    static {
        // アンドロイドの動き（時間がかかる）
        action("go", 1, 1);
        action("take", 1, 3);
        action("put", 1, 3);
        action("use", 1, 1);
        action("mine", 1, 1);
        action("wait", 1, 1);
        action("home", 0, 0);
        action("attack", 1, 3);
        action("lever", 2, 2);
        // アンドロイドと世界を調べる（すぐ終わる）
        host("say", 0, -1);
        host("print", 0, -1);
        host("count", 0, 1);
        host("count_at", 1, 2);
        host("free_slots", 0, 0);
        host("block", 1, 1);
        host("redstone", 1, 1);
        host("here", 0, 0);
        host("station", 0, 0);
        host("point", 1, 1);
        host("points", 0, 0);
        host("is_day", 0, 0);
        host("time", 0, 0);
        host("charge", 0, 0);
        host("health", 0, 0);
        host("mobs", 1, 3);
        host("matches", 2, 2);
        host("distance", 2, 2);
        host("auto_fight", 1, 1);
        host("send", 1, 3);
        host("receive", 0, 1);
        // 値の計算
        pure("len", 1, 1);
        pure("range", 1, 3);
        pure("str", 0, 1);
        pure("int", 0, 1);
        pure("float", 0, 1);
        pure("bool", 0, 1);
        pure("abs", 1, 1);
        pure("min", 1, -1);
        pure("max", 1, -1);
        pure("round", 1, 2);
        pure("randint", 2, 2);
        pure("pos", 3, 3);
        pure("list", 0, 1);
        pure("sorted", 1, 1);
        pure("sum", 1, 1);
    }

    private Library() {}

    private static void action(String name, int min, int max) {
        FUNCTIONS.put(name, new Sig(min, max, true, true));
    }

    private static void host(String name, int min, int max) {
        FUNCTIONS.put(name, new Sig(min, max, true, false));
    }

    private static void pure(String name, int min, int max) {
        FUNCTIONS.put(name, new Sig(min, max, false, false));
    }
}
