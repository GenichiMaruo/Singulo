package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** プログラムを書く画面の手助け（0.24.0）: カーソルの所の読み取り・候補・ステーションにない場所の名前。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ProgramAssistGameTests {
    private ProgramAssistGameTests() {}

    @GameTest(template = "empty")
    public static void programAssistReadsTheCursorContext(GameTestHelper h) {
        var c = ProgramAssist.context("    n = take(\"fur", 17);
        h.assertTrue(c.inString() && c.prefix().equals("fur") && c.call() != null && c.call().name().equals("take") && c.call().argument() == 0,
                "場所の引数の文字列を読めない: " + c);
        c = ProgramAssist.context("put(\"box\", \"iron", 16);
        h.assertTrue(c.inString() && c.call().name().equals("put") && c.call().argument() == 1, "2番目の引数を読めない: " + c);
        c = ProgramAssist.context("x = items.app", 13);
        h.assertTrue(!c.inString() && c.afterDot() && c.prefix().equals("app"), "メソッドの語を読めない: " + c);
        c = ProgramAssist.context("say(len(items), ", 16);
        h.assertTrue(c.call().name().equals("say") && c.call().argument() == 1, "入れ子の呼び出しを抜けたあとを読めない: " + c);
        c = ProgramAssist.context("# take(\"a", 9);
        h.assertTrue(c.inComment(), "コメントの中を読めない: " + c);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void programAssistSuggestsWordsPlacesAndItems(GameTestHelper h) {
        String source = "def move(item, n):\n    return n\ncounter = 0\nfor slot in range(3):\n    pass\n";
        var words = ProgramAssist.candidates(ProgramAssist.context("cou", 3), source, List.of(), false);
        h.assertTrue(words.size() >= 2 && words.get(0).label().equals("counter") && words.stream().anyMatch(w -> w.label().equals("count_at")),
                "変数と関数の候補が違う: " + words.stream().map(ProgramAssist.Candidate::label).toList());
        var defs = ProgramAssist.candidates(ProgramAssist.context("mo", 2), source, List.of(), false);
        h.assertTrue(defs.stream().anyMatch(w -> w.label().equals("move") && w.kind() == ProgramAssist.Kind.FUNCTION), "自分の関数が候補にない");
        var places = ProgramAssist.candidates(ProgramAssist.context("go(\"f", 5), source, List.of("furnace", "chest", "p1"), false);
        h.assertTrue(places.size() == 1 && places.get(0).insert().equals("furnace"), "場所の名前の候補が違う: " + places);
        var items = ProgramAssist.candidates(ProgramAssist.context("take(\"p1\", \"iron_ing", 20), source, List.of(), false);
        h.assertTrue(!items.isEmpty() && items.get(0).insert().equals("iron_ingot") && items.get(0).item() != null, "物の ID の候補が違う: " + items);
        h.assertTrue(ProgramAssist.candidates(ProgramAssist.context("whil", 4), source, List.of(), false).get(0).label().equals("while"),
                "キーワードの候補がない");
        h.assertTrue(ProgramAssist.candidates(ProgramAssist.context("x = ", 4), source, List.of(), false).isEmpty(), "語を打っていないのに候補が出る");
        String typing = "mobs(post, 5, [\"hostile\", \"!zomb";
        var filters = ProgramAssist.candidates(ProgramAssist.context(typing, typing.length()), source, List.of(), false);
        h.assertTrue(!filters.isEmpty() && filters.get(0).insert().equals("!zombie") && filters.get(0).item() != null,
                "絞り込みの候補が違う: " + filters.stream().map(ProgramAssist.Candidate::insert).toList());
        var kinds = ProgramAssist.candidates(ProgramAssist.context("matches(m, \"host", 16), source, List.of(), false);
        h.assertTrue(!kinds.isEmpty() && kinds.get(0).insert().equals("hostile"), "絞り込みの言葉の候補がない: " + kinds);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void programAssistFlagsUnknownPlaces(GameTestHelper h) {
        var bad = ProgramAssist.unknownPlaces("go(\"chest\")\ntake('furnace', 'coal')\n# go(\"nowhere\")\nsay('go(\"x\")')\n",
                List.of("chest", "p1"));
        h.assertTrue(bad.size() == 1 && (int) bad.get(0)[0] == 2 && bad.get(0)[1].equals("furnace"),
                "ない場所の名前の行が違う: " + bad.stream().map(java.util.Arrays::toString).toList());
        h.succeed();
    }
}
