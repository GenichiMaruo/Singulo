package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.android.program.Library;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * プログラムを書く画面の手引き（0.24.0）: 関数の引数の名前と分類、メソッド、場所・物を受け取る引数、ひな形。
 * 関数の説明は {@code program.singulo.fn.<名前>}、ひな形の名前と説明は {@code gui.singulo.program.snippet.<id>.title / .desc}。
 */
final class ProgramHelp {
    enum Category { ACTION, QUERY, VALUE }

    record Fn(String name, List<String> params, Category category) {
        String signature() {
            return name + "(" + String.join(", ", params) + ")";
        }
    }

    record Snippet(String id, String code) {}

    static final Map<String, Fn> FUNCTIONS = new LinkedHashMap<>();
    /** 値のメソッド（{@code .} のあとの候補とヒント）。 */
    static final Map<String, Fn> METHODS = new LinkedHashMap<>();
    static final Set<String> KEYWORDS = Set.of("if", "elif", "else", "while", "for", "in", "def", "return", "break", "continue",
            "pass", "and", "or", "not", "True", "False", "None", "global", "is");
    /** 最初の引数に場所を受け取る関数。 */
    static final Set<String> PLACE_FIRST = Set.of("go", "take", "put", "use", "mine", "count_at", "block", "redstone", "point", "mobs");
    /** モブの属性（{@code .} のあとの候補）。 */
    static final List<String> MOB_ATTRIBUTES = List.of("name", "type", "health", "max_health", "alive", "hostile", "baby", "named",
            "pos", "x", "y", "z");
    /** 知らせ（receive() の結果）の属性。 */
    static final List<String> MESSAGE_ATTRIBUTES = List.of("topic", "data", "sender");
    /** 絞り込みの言葉（mobs() の3番目、matches() の2番目の引数）。 */
    static final List<String> MOB_KINDS = List.of("hostile", "animal", "all");

    /** モブの絞り込みを受け取る引数か。 */
    static boolean filterArgument(String fn, int index) {
        return fn.equals("mobs") && index == 2 || fn.equals("matches") && index == 1;
    }

    static {
        fn(Category.ACTION, "go", "p");
        fn(Category.ACTION, "take", "p", "item", "n");
        fn(Category.ACTION, "put", "p", "item", "n");
        fn(Category.ACTION, "use", "p");
        fn(Category.ACTION, "lever", "p", "on");
        fn(Category.ACTION, "mine", "p");
        fn(Category.ACTION, "wait", "seconds");
        fn(Category.ACTION, "home");
        fn(Category.ACTION, "attack", "mob", "p", "r");
        fn(Category.ACTION, "auto_fight", "on");
        fn(Category.QUERY, "say", "...");
        fn(Category.QUERY, "count", "item");
        fn(Category.QUERY, "count_at", "p", "item");
        fn(Category.QUERY, "free_slots");
        fn(Category.QUERY, "block", "p");
        fn(Category.QUERY, "redstone", "p");
        fn(Category.QUERY, "here");
        fn(Category.QUERY, "station");
        fn(Category.QUERY, "point", "name");
        fn(Category.QUERY, "points");
        fn(Category.QUERY, "is_day");
        fn(Category.QUERY, "time");
        fn(Category.QUERY, "charge");
        fn(Category.QUERY, "health");
        fn(Category.QUERY, "mobs", "p", "r", "filter");
        fn(Category.QUERY, "matches", "mob", "filter");
        fn(Category.QUERY, "distance", "a", "b");
        fn(Category.QUERY, "send", "topic", "data", "r");
        fn(Category.QUERY, "receive", "topic");
        fn(Category.VALUE, "pos", "x", "y", "z");
        fn(Category.VALUE, "range", "start", "stop", "step");
        fn(Category.VALUE, "len", "x");
        fn(Category.VALUE, "str", "x");
        fn(Category.VALUE, "int", "x");
        fn(Category.VALUE, "float", "x");
        fn(Category.VALUE, "bool", "x");
        fn(Category.VALUE, "abs", "x");
        fn(Category.VALUE, "min", "...");
        fn(Category.VALUE, "max", "...");
        fn(Category.VALUE, "round", "x", "digits");
        fn(Category.VALUE, "randint", "a", "b");
        fn(Category.VALUE, "list", "x");
        fn(Category.VALUE, "sorted", "x");
        fn(Category.VALUE, "sum", "x");
        method("append", "x");
        method("pop", "i");
        method("insert", "i", "x");
        method("remove", "x");
        method("index", "x");
        method("clear");
        method("upper");
        method("lower");
        method("strip");
        method("startswith", "s");
        method("endswith", "s");
        method("replace", "old", "new");
        method("split", "sep");
        method("join", "items");
        method("offset", "dx", "dy", "dz");
        for (String d : new String[] {"up", "down", "north", "south", "east", "west"}) {
            method(d, "n");
        }
        if (!FUNCTIONS.keySet().equals(Library.FUNCTIONS.keySet().stream().filter(n -> !n.equals("print"))
                .collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalStateException("ProgramHelp and Library disagree");
        }
    }

    /** ひな形（場所は p1〜p3。書いてから、ステーションの場所に合わせて名前を変える）。 */
    static final List<Snippet> SNIPPETS = List.of(
            new Snippet("carry", """
                    while True:
                        if count_at("p1") > 0:
                            take("p1")
                            put("p2")
                        wait(5)
                    """),
            new Snippet("furnace", """
                    while True:
                        take("p1", "raw_iron", 16)
                        put("p2", "raw_iron")
                        take("p2", "iron_ingot")
                        put("p3", "iron_ingot")
                        wait(10)
                    """),
            new Snippet("dig", """
                    top = point("p1")
                    for dy in range(5):
                        if not mine(top.down(dy)):
                            say("stopped at", dy)
                            break
                    home()
                    put("p2")
                    """),
            new Snippet("day", """
                    while True:
                        if is_day():
                            go("p1")
                            wait(10)
                        else:
                            home()
                            wait(30)
                    """),
            new Snippet("guard", """
                    post = point("p1")
                    auto_fight(False)
                    while True:
                        go(post.up())
                        for m in mobs(post, 5):
                            attack(m, post, 5)
                        wait(0.5)
                    """),
            new Snippet("team", """
                    post = point("p1")
                    auto_fight(False)
                    while True:
                        go(post.up())
                        enemies = mobs(post, 8, "hostile")
                        if enemies:
                            send("enemy", enemies[0], 32)
                            attack(enemies[0], post, 12)
                        else:
                            msg = receive("enemy")
                            if msg and msg.data.alive:
                                attack(msg.data, msg.pos, 12)
                        wait(0.5)
                    """),
            new Snippet("alarm", """
                    while True:
                        if mobs("p1", 10, "hostile"):
                            lever("p2", True)
                        else:
                            lever("p2", False)
                        wait(1)
                    """),
            new Snippet("signal", """
                    while True:
                        if redstone("p1") > 0:
                            use("p2")
                            wait(2)
                        wait(0.5)
                    """),
            new Snippet("function", """
                    def move(item, n):
                        got = take("p1", item, n)
                        put("p2", item)
                        return got

                    say("moved", move("cobblestone", 64))
                    """));

    private ProgramHelp() {}

    private static void fn(Category category, String name, String... params) {
        FUNCTIONS.put(name, new Fn(name, List.of(params), category));
    }

    private static void method(String name, String... params) {
        METHODS.put(name, new Fn(name, List.of(params), Category.VALUE));
    }

    /** 場所を受け取る引数か。 */
    static boolean placeArgument(String fn, int index) {
        return index == 0 && PLACE_FIRST.contains(fn) || fn.equals("attack") && index == 1 || fn.equals("distance");
    }

    /** 物を受け取る引数か。 */
    static boolean itemArgument(String fn, int index) {
        return switch (fn) {
            case "take", "put", "count_at" -> index == 1;
            case "count" -> index == 0;
            default -> false;
        };
    }

    /** 分類ごとの関数（一覧の並び）。 */
    static List<Fn> of(Category category) {
        List<Fn> out = new ArrayList<>();
        for (Fn f : FUNCTIONS.values()) {
            if (f.category() == category) {
                out.add(f);
            }
        }
        return out;
    }
}
