package io.github.genichimaruo.singulo.android.program;

import io.github.genichimaruo.singulo.android.program.Values.PList;
import io.github.genichimaruo.singulo.android.program.Values.Pos;
import io.github.genichimaruo.singulo.android.program.Values.Range;
import java.util.ArrayList;
import java.util.List;

/** 値の計算だけをする関数（len・range など）と、値のメソッド・属性。アンドロイドには触れない。 */
final class Builtins {
    private Builtins() {}

    static Object call(String name, List<Object> args, java.util.Random random) {
        return switch (name) {
            case "len" -> Values.length(args.get(0));
            case "range" -> range(args);
            case "str" -> args.isEmpty() ? "" : Values.checkString(Values.str(args.get(0)));
            case "int" -> args.isEmpty() ? 0L : toInt(args.get(0));
            case "float" -> args.isEmpty() ? 0.0 : toFloat(args.get(0));
            case "bool" -> !args.isEmpty() && Values.truthy(args.get(0));
            case "abs" -> {
                Object v = args.get(0);
                Long l = Values.asLong(v);
                if (l != null) {
                    yield Math.absExact(l);
                }
                yield Math.abs(Values.asDouble(v));
            }
            case "min", "max" -> extreme(name.equals("max"), args);
            case "round" -> round(args);
            case "randint" -> {
                long lo = Values.integer(args.get(0)), hi = Values.integer(args.get(1));
                if (hi < lo) {
                    throw new ProgramError("bad_value", "randint");
                }
                yield lo + (long) (random.nextDouble() * (hi - lo + 1));
            }
            case "pos" -> new Pos(Values.clampInt(Values.integer(args.get(0))), Values.clampInt(Values.integer(args.get(1))),
                    Values.clampInt(Values.integer(args.get(2))));
            case "list" -> args.isEmpty() ? new PList(List.of()) : new PList(elements(args.get(0)));
            case "sorted" -> {
                List<Object> items = new ArrayList<>(elements(args.get(0)));
                items.sort((a, b) -> Values.truthy(Values.binary(Values.LT, a, b)) ? -1 : Values.truthy(Values.binary(Values.LT, b, a)) ? 1 : 0);
                yield new PList(items);
            }
            case "sum" -> {
                Object total = 0L;
                for (Object o : elements(args.get(0))) {
                    total = Values.binary(Values.ADD, total, o);
                }
                yield total;
            }
            default -> throw new ProgramError("unknown_function", name);
        };
    }

    private static Range range(List<Object> args) {
        long start = 0, stop, step = 1;
        if (args.size() == 1) {
            stop = Values.integer(args.get(0));
        } else {
            start = Values.integer(args.get(0));
            stop = Values.integer(args.get(1));
            if (args.size() == 3) {
                step = Values.integer(args.get(2));
            }
        }
        if (step == 0) {
            throw new ProgramError("bad_value", "range");
        }
        return new Range(start, stop, step);
    }

    private static long toInt(Object v) {
        if (v instanceof Double d) {
            if (Double.isNaN(d) || Double.isInfinite(d) || Math.abs(d) > 9e18) {
                throw new ProgramError("convert", Values.repr(v), "int");
            }
            return (long) (double) d;
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim().replace("_", ""));
            } catch (NumberFormatException e) {
                throw new ProgramError("convert", Values.repr(v), "int");
            }
        }
        Long l = Values.asLong(v);
        if (l == null) {
            throw new ProgramError("convert", Values.repr(v), "int");
        }
        return l;
    }

    private static double toFloat(Object v) {
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                throw new ProgramError("convert", Values.repr(v), "float");
            }
        }
        if (!Values.isNumber(v)) {
            throw new ProgramError("convert", Values.repr(v), "float");
        }
        return Values.asDouble(v);
    }

    private static Object extreme(boolean max, List<Object> args) {
        List<Object> items = args.size() == 1 ? elements(args.get(0)) : args;
        if (items.isEmpty()) {
            throw new ProgramError("bad_value", max ? "max" : "min");
        }
        Object best = items.get(0);
        for (Object o : items) {
            boolean better = Values.truthy(Values.binary(max ? Values.GT : Values.LT, o, best));
            if (better) {
                best = o;
            }
        }
        return best;
    }

    private static Object round(List<Object> args) {
        double v = Values.asDouble(args.get(0));
        if (args.size() == 1) {
            if (Values.asLong(args.get(0)) != null) {
                return Values.asLong(args.get(0));
            }
            return (long) Math.rint(v);
        }
        long digits = Values.integer(args.get(1));
        double k = Math.pow(10, Math.max(-15, Math.min(15, digits)));
        return Math.rint(v * k) / k;
    }

    /** リスト・範囲・文字列の中身。 */
    static List<Object> elements(Object v) {
        if (v instanceof PList l) {
            return l.items;
        }
        if (v instanceof Range r) {
            long n = r.size();
            if (n > Values.MAX_LIST) {
                throw new ProgramError("too_big");
            }
            List<Object> out = new ArrayList<>();
            for (long i = 0; i < n; i++) {
                out.add(r.get(i));
            }
            return out;
        }
        if (v instanceof String s) {
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < s.length(); i++) {
                out.add(String.valueOf(s.charAt(i)));
            }
            return out;
        }
        throw new ProgramError("not_iterable", Values.typeName(v));
    }

    // ------------------------------------------------------------------ メソッド・属性

    static Object attribute(Object v, String name) {
        if (v instanceof Values.Message m) {
            switch (name) {
                case "topic" -> {
                    return m.topic();
                }
                case "data" -> {
                    return m.data();
                }
                case "pos" -> {
                    return m.pos();
                }
                case "sender" -> {
                    return m.sender();
                }
                default -> {
                }
            }
        }
        if (v instanceof Pos p) {
            switch (name) {
                case "x" -> {
                    return (long) p.x();
                }
                case "y" -> {
                    return (long) p.y();
                }
                case "z" -> {
                    return (long) p.z();
                }
                default -> {
                }
            }
        }
        throw new ProgramError("no_attribute", Values.typeName(v), name);
    }

    static Object method(Object v, String name, List<Object> args) {
        if (v instanceof PList l) {
            switch (name) {
                case "append" -> {
                    arity(name, args, 1, 1);
                    l.add(args.get(0));
                    return null;
                }
                case "pop" -> {
                    arity(name, args, 0, 1);
                    if (l.items.isEmpty()) {
                        throw new ProgramError("empty_list");
                    }
                    long i = args.isEmpty() ? -1 : Values.integer(args.get(0));
                    int n = l.items.size();
                    long at = i < 0 ? i + n : i;
                    if (at < 0 || at >= n) {
                        throw new ProgramError("index_range", i, n);
                    }
                    return l.items.remove((int) at);
                }
                case "insert" -> {
                    arity(name, args, 2, 2);
                    int n = l.items.size();
                    long i = Values.integer(args.get(0));
                    long at = Math.max(0, Math.min(n, i < 0 ? i + n : i));
                    if (n >= Values.MAX_LIST) {
                        throw new ProgramError("too_big");
                    }
                    l.items.add((int) at, args.get(1));
                    return null;
                }
                case "remove" -> {
                    arity(name, args, 1, 1);
                    for (int i = 0; i < l.items.size(); i++) {
                        if (Values.eq(l.items.get(i), args.get(0))) {
                            l.items.remove(i);
                            return null;
                        }
                    }
                    throw new ProgramError("not_in_list", Values.repr(args.get(0)));
                }
                case "index" -> {
                    arity(name, args, 1, 1);
                    for (int i = 0; i < l.items.size(); i++) {
                        if (Values.eq(l.items.get(i), args.get(0))) {
                            return (long) i;
                        }
                    }
                    throw new ProgramError("not_in_list", Values.repr(args.get(0)));
                }
                case "clear" -> {
                    arity(name, args, 0, 0);
                    l.items.clear();
                    return null;
                }
                default -> {
                }
            }
        }
        if (v instanceof String s) {
            switch (name) {
                case "upper" -> {
                    arity(name, args, 0, 0);
                    return s.toUpperCase(java.util.Locale.ROOT);
                }
                case "lower" -> {
                    arity(name, args, 0, 0);
                    return s.toLowerCase(java.util.Locale.ROOT);
                }
                case "strip" -> {
                    arity(name, args, 0, 0);
                    return s.strip();
                }
                case "startswith", "endswith" -> {
                    arity(name, args, 1, 1);
                    String x = text(args.get(0));
                    return name.equals("startswith") ? s.startsWith(x) : s.endsWith(x);
                }
                case "replace" -> {
                    arity(name, args, 2, 2);
                    return Values.checkString(s.replace(text(args.get(0)), text(args.get(1))));
                }
                case "split" -> {
                    arity(name, args, 0, 1);
                    String[] parts = args.isEmpty() ? s.strip().split("\\s+") : s.split(java.util.regex.Pattern.quote(text(args.get(0))), -1);
                    List<Object> out = new ArrayList<>();
                    for (String part : parts) {
                        if (!args.isEmpty() || !part.isEmpty()) {
                            out.add(part);
                        }
                    }
                    return new PList(out);
                }
                case "join" -> {
                    arity(name, args, 1, 1);
                    StringBuilder b = new StringBuilder();
                    List<Object> parts = elements(args.get(0));
                    for (int i = 0; i < parts.size(); i++) {
                        if (i > 0) {
                            b.append(s);
                        }
                        b.append(text(parts.get(i)));
                        Values.checkString(b.toString());
                    }
                    return b.toString();
                }
                default -> {
                }
            }
        }
        if (v instanceof Pos p) {
            switch (name) {
                case "offset" -> {
                    arity(name, args, 3, 3);
                    return p.offset(Values.integer(args.get(0)), Values.integer(args.get(1)), Values.integer(args.get(2)));
                }
                case "up", "down", "north", "south", "east", "west" -> {
                    arity(name, args, 0, 1);
                    long n = args.isEmpty() ? 1 : Values.integer(args.get(0));
                    return switch (name) {
                        case "up" -> p.offset(0, n, 0);
                        case "down" -> p.offset(0, -n, 0);
                        case "north" -> p.offset(0, 0, -n);
                        case "south" -> p.offset(0, 0, n);
                        case "east" -> p.offset(n, 0, 0);
                        default -> p.offset(-n, 0, 0);
                    };
                }
                default -> {
                }
            }
        }
        throw new ProgramError("no_method", Values.typeName(v), name);
    }

    private static String text(Object v) {
        if (!(v instanceof String s)) {
            throw new ProgramError("not_text", Values.typeName(v));
        }
        return s;
    }

    private static void arity(String name, List<Object> args, int min, int max) {
        if (args.size() < min || args.size() > max) {
            throw new ProgramError("arg_count", name, min == max ? String.valueOf(min) : min + "-" + max, args.size());
        }
    }
}
