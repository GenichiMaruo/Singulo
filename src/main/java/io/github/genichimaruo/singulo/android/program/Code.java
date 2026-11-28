package io.github.genichimaruo.singulo.android.program;

import java.util.Map;
import java.util.Set;

/**
 * 訳したプログラム（命令の並び）。命令 i は op[i] と、引数 a[i]・b[i]、文の行 line[i]。
 * 定数と名前は表に置き、命令は番号で指す。自分で定めた関数（def）は functions に。
 */
final class Code {
    static final int CONST = 0, LOAD = 1, STORE = 2, POP = 3, BIN = 4, NEG = 5, NOT = 6, JUMP = 7, JF = 8, JF_KEEP = 9,
            JT_KEEP = 10, CALL = 11, METHOD = 12, ATTR = 13, INDEX = 14, SET_INDEX = 15, DUP2 = 16, LIST = 17, LEN = 18,
            RET = 19, HALT = 20, PLUS = 21;

    record Fn(String name, int entry, String[] params, Set<String> globals, int line) {}

    final int[] op, a, b, line;
    final Object[] consts;
    final String[] names;
    final Map<String, Fn> functions;

    Code(int[] op, int[] a, int[] b, int[] line, Object[] consts, String[] names, Map<String, Fn> functions) {
        this.op = op;
        this.a = a;
        this.b = b;
        this.line = line;
        this.consts = consts;
        this.names = names;
        this.functions = functions;
    }

    int size() {
        return op.length;
    }
}
