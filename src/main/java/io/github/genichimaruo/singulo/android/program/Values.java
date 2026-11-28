package io.github.genichimaruo.singulo.android.program;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * プログラムの値と、その計算（Python に合わせる）。値は次のどれか:
 * None（null）・真偽（Boolean）・整数（Long）・小数（Double）・文字列（String）・リスト（{@link PList}）・場所（{@link Pos}）・
 * 範囲（{@link Range}。range() の結果）・モブ（{@link Mob}。mobs() の結果。属性はアンドロイドの側が世界から読む）。
 */
public final class Values {
    /** リストの長さと文字列の長さの上限（使いすぎを防ぐ）。 */
    public static final int MAX_LIST = 1024, MAX_STRING = 4096;

    private Values() {}

    /** リスト（中身を書き換えられる）。 */
    public static final class PList {
        final ArrayList<Object> items;

        public PList(List<Object> items) {
            if (items.size() > MAX_LIST) {
                throw new ProgramError("too_big");
            }
            this.items = new ArrayList<>(items);
        }

        public List<Object> items() {
            return java.util.Collections.unmodifiableList(items);
        }

        void add(Object v) {
            if (items.size() >= MAX_LIST) {
                throw new ProgramError("too_big");
            }
            items.add(v);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PList other) || other.items.size() != items.size()) {
                return false;
            }
            for (int i = 0; i < items.size(); i++) {
                if (!eq(items.get(i), other.items.get(i))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return items.size();
        }
    }

    /** ブロックの場所。 */
    public record Pos(int x, int y, int z) {
        public net.minecraft.core.BlockPos block() {
            return new net.minecraft.core.BlockPos(x, y, z);
        }

        public static Pos of(net.minecraft.core.BlockPos p) {
            return new Pos(p.getX(), p.getY(), p.getZ());
        }

        Pos offset(long dx, long dy, long dz) {
            return new Pos(clampInt(x + dx), clampInt(y + dy), clampInt(z + dz));
        }
    }

    /** モブ（世界の生き物を指す。UUID で探し直す）。 */
    public record Mob(java.util.UUID uuid, String type) {}

    /** ほかのアンドロイドからの知らせ（receive() の結果）。topic・data・pos（送ったアンドロイドがいた場所）・sender（その名前）。 */
    public record Message(String topic, Object data, Pos pos, String sender) {}

    /** range(start, stop, step)。 */
    public record Range(long start, long stop, long step) {
        long size() {
            if (step > 0) {
                return stop <= start ? 0 : (stop - start + step - 1) / step;
            }
            return stop >= start ? 0 : (start - stop - step - 1) / -step;
        }

        long get(long i) {
            return start + i * step;
        }
    }

    static int clampInt(long v) {
        if (v > 30_000_000 || v < -30_000_000) {
            throw new ProgramError("overflow");
        }
        return (int) v;
    }

    // ------------------------------------------------------------------ 種類・真偽・文字列

    public static String typeName(Object v) {
        if (v == null) {
            return "None";
        }
        if (v instanceof Boolean) {
            return "bool";
        }
        if (v instanceof Long) {
            return "int";
        }
        if (v instanceof Double) {
            return "float";
        }
        if (v instanceof String) {
            return "str";
        }
        if (v instanceof PList) {
            return "list";
        }
        if (v instanceof Pos) {
            return "pos";
        }
        if (v instanceof Range) {
            return "range";
        }
        if (v instanceof Mob) {
            return "mob";
        }
        if (v instanceof Message) {
            return "message";
        }
        return "?";
    }

    public static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Long l) {
            return l != 0;
        }
        if (v instanceof Double d) {
            return d != 0;
        }
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        if (v instanceof PList l) {
            return !l.items.isEmpty();
        }
        if (v instanceof Range r) {
            return r.size() > 0;
        }
        return true;
    }

    /** str(v)。 */
    public static String str(Object v) {
        if (v instanceof String s) {
            return s;
        }
        return repr(v);
    }

    /** 値の書き表し（リストの中の文字列は引用符つき）。 */
    public static String repr(Object v) {
        if (v == null) {
            return "None";
        }
        if (v instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (v instanceof Double d) {
            return floatString(d);
        }
        if (v instanceof String s) {
            return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'";
        }
        if (v instanceof PList l) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < l.items.size(); i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append(repr(l.items.get(i)));
                if (b.length() > MAX_STRING) {
                    b.append("...");
                    break;
                }
            }
            return b.append(']').toString();
        }
        if (v instanceof Pos p) {
            return "pos(" + p.x + ", " + p.y + ", " + p.z + ")";
        }
        if (v instanceof Range r) {
            return r.step == 1 ? "range(" + r.start + ", " + r.stop + ")" : "range(" + r.start + ", " + r.stop + ", " + r.step + ")";
        }
        if (v instanceof Mob m) {
            return "<mob " + m.type() + ">";
        }
        if (v instanceof Message m) {
            return "<message " + m.topic() + ">";
        }
        return String.valueOf(v);
    }

    static String floatString(double d) {
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        double a = Math.abs(d);
        if (a != 0 && (a >= 1e16 || a < 1e-4)) {
            return Double.toString(d).replace("E", "e");
        }
        String s = new BigDecimal(Double.toString(d)).toPlainString();
        return s.contains(".") ? s : s + ".0";
    }

    // ------------------------------------------------------------------ 数

    static boolean isNumber(Object v) {
        return v instanceof Long || v instanceof Double || v instanceof Boolean;
    }

    /** 整数として（真偽は 0/1）。整数でなければ null。 */
    static Long asLong(Object v) {
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Boolean b) {
            return b ? 1L : 0L;
        }
        return null;
    }

    static double asDouble(Object v) {
        if (v instanceof Double d) {
            return d;
        }
        Long l = asLong(v);
        if (l == null) {
            throw new ProgramError("not_number", typeName(v));
        }
        return l;
    }

    /** 整数の引数（小数・ほかの種類なら誤り）。 */
    public static long integer(Object v) {
        Long l = asLong(v);
        if (l == null) {
            throw new ProgramError("not_integer", typeName(v));
        }
        return l;
    }

    // ------------------------------------------------------------------ 計算

    static final int ADD = 0, SUB = 1, MUL = 2, DIV = 3, FLOORDIV = 4, MOD = 5, POW = 6,
            EQ = 7, NE = 8, LT = 9, LE = 10, GT = 11, GE = 12, IN = 13, NOT_IN = 14;
    static final String[] SYMBOLS = {"+", "-", "*", "/", "//", "%", "**", "==", "!=", "<", "<=", ">", ">=", "in", "not in"};

    static Object binary(int op, Object a, Object b) {
        try {
            return switch (op) {
                case EQ -> eq(a, b);
                case NE -> !eq(a, b);
                case LT -> compare(op, a, b) < 0;
                case LE -> compare(op, a, b) <= 0;
                case GT -> compare(op, a, b) > 0;
                case GE -> compare(op, a, b) >= 0;
                case IN -> contains(b, a);
                case NOT_IN -> !contains(b, a);
                default -> arithmetic(op, a, b);
            };
        } catch (ArithmeticException e) {
            throw new ProgramError("overflow");
        }
    }

    private static Object arithmetic(int op, Object a, Object b) {
        if (op == ADD) {
            if (a instanceof String x && b instanceof String y) {
                return checkString(x + y);
            }
            if (a instanceof PList x && b instanceof PList y) {
                List<Object> all = new ArrayList<>(x.items);
                all.addAll(y.items);
                return new PList(all);
            }
        }
        if (op == MUL) {
            if (a instanceof String s && asLong(b) != null) {
                return repeat(s, asLong(b));
            }
            if (b instanceof String s && asLong(a) != null) {
                return repeat(s, asLong(a));
            }
            if (a instanceof PList l && asLong(b) != null) {
                return repeat(l, asLong(b));
            }
            if (b instanceof PList l && asLong(a) != null) {
                return repeat(l, asLong(a));
            }
        }
        if (!isNumber(a) || !isNumber(b)) {
            throw new ProgramError("bad_operands", SYMBOLS[op], typeName(a), typeName(b));
        }
        Long x = asLong(a), y = asLong(b);
        if (x != null && y != null) {
            long l = x, r = y;
            return switch (op) {
                case ADD -> Math.addExact(l, r);
                case SUB -> Math.subtractExact(l, r);
                case MUL -> Math.multiplyExact(l, r);
                case DIV -> {
                    if (r == 0) {
                        throw new ProgramError("div_zero");
                    }
                    yield (double) l / r;
                }
                case FLOORDIV -> {
                    if (r == 0) {
                        throw new ProgramError("div_zero");
                    }
                    yield Math.floorDiv(l, r);
                }
                case MOD -> {
                    if (r == 0) {
                        throw new ProgramError("div_zero");
                    }
                    yield Math.floorMod(l, r);
                }
                default -> r >= 0 ? (Object) powExact(l, r) : (Object) Math.pow(l, r);
            };
        }
        double l = asDouble(a), r = asDouble(b);
        return switch (op) {
            case ADD -> l + r;
            case SUB -> l - r;
            case MUL -> l * r;
            case DIV -> {
                if (r == 0) {
                    throw new ProgramError("div_zero");
                }
                yield l / r;
            }
            case FLOORDIV -> {
                if (r == 0) {
                    throw new ProgramError("div_zero");
                }
                yield Math.floor(l / r);
            }
            case MOD -> {
                if (r == 0) {
                    throw new ProgramError("div_zero");
                }
                yield l - r * Math.floor(l / r);
            }
            default -> Math.pow(l, r);
        };
    }

    private static long powExact(long base, long exp) {
        long result = 1;
        for (long i = 0; i < exp; i++) {
            result = Math.multiplyExact(result, base);
            if (result == 0 || result == 1 && base == 1) {
                break;
            }
        }
        return result;
    }

    private static String repeat(String s, long n) {
        if (n <= 0 || s.isEmpty()) {
            return "";
        }
        if (s.length() * n > MAX_STRING) {
            throw new ProgramError("too_big");
        }
        return s.repeat((int) n);
    }

    private static PList repeat(PList l, long n) {
        if (n <= 0 || l.items.isEmpty()) {
            return new PList(List.of());
        }
        if (l.items.size() * n > MAX_LIST) {
            throw new ProgramError("too_big");
        }
        List<Object> all = new ArrayList<>();
        for (long i = 0; i < n; i++) {
            all.addAll(l.items);
        }
        return new PList(all);
    }

    static String checkString(String s) {
        if (s.length() > MAX_STRING) {
            throw new ProgramError("too_big");
        }
        return s;
    }

    static Object negate(Object v) {
        Long l = asLong(v);
        if (l != null) {
            return Math.negateExact(l);
        }
        if (v instanceof Double d) {
            return -d;
        }
        throw new ProgramError("bad_operand", "-", typeName(v));
    }

    public static boolean eq(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (isNumber(a) && isNumber(b)) {
            Long x = asLong(a), y = asLong(b);
            if (x != null && y != null) {
                return x.longValue() == y.longValue();
            }
            return asDouble(a) == asDouble(b);
        }
        return a.equals(b);
    }

    private static int compare(int op, Object a, Object b) {
        if (isNumber(a) && isNumber(b)) {
            Long x = asLong(a), y = asLong(b);
            if (x != null && y != null) {
                return Long.compare(x, y);
            }
            return Double.compare(asDouble(a), asDouble(b));
        }
        if (a instanceof String x && b instanceof String y) {
            return x.compareTo(y);
        }
        throw new ProgramError("bad_operands", SYMBOLS[op], typeName(a), typeName(b));
    }

    private static boolean contains(Object container, Object item) {
        if (container instanceof String s) {
            if (!(item instanceof String i)) {
                throw new ProgramError("bad_operands", "in", typeName(item), "str");
            }
            return s.contains(i);
        }
        if (container instanceof PList l) {
            for (Object o : l.items) {
                if (eq(o, item)) {
                    return true;
                }
            }
            return false;
        }
        if (container instanceof Range r) {
            Long v = asLong(item);
            if (v == null) {
                return false;
            }
            long n = r.size();
            if (n == 0 || (v - r.start) % r.step != 0) {
                return false;
            }
            long i = (v - r.start) / r.step;
            return i >= 0 && i < n;
        }
        throw new ProgramError("not_iterable", typeName(container));
    }

    // ------------------------------------------------------------------ 長さ・添え字

    static long length(Object v) {
        if (v instanceof PList l) {
            return l.items.size();
        }
        if (v instanceof String s) {
            return s.length();
        }
        if (v instanceof Range r) {
            return r.size();
        }
        throw new ProgramError("no_length", typeName(v));
    }

    static Object index(Object v, Object index) {
        long n = length(v);
        Long i = asLong(index);
        if (i == null) {
            throw new ProgramError("bad_index", typeName(index));
        }
        long at = i < 0 ? i + n : i;
        if (at < 0 || at >= n) {
            throw new ProgramError("index_range", i, n);
        }
        if (v instanceof PList l) {
            return l.items.get((int) at);
        }
        if (v instanceof String s) {
            return String.valueOf(s.charAt((int) at));
        }
        return ((Range) v).get(at);
    }

    static void setIndex(Object v, Object index, Object value) {
        if (!(v instanceof PList l)) {
            throw new ProgramError("not_assignable", typeName(v));
        }
        Long i = asLong(index);
        if (i == null) {
            throw new ProgramError("bad_index", typeName(index));
        }
        int n = l.items.size();
        long at = i < 0 ? i + n : i;
        if (at < 0 || at >= n) {
            throw new ProgramError("index_range", i, n);
        }
        l.items.set((int) at, value);
    }
}
