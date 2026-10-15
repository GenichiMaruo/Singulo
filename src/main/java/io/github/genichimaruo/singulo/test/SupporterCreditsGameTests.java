package io.github.genichimaruo.singulo.test;

import com.google.gson.JsonParseException;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.supporter.SupporterCredits;
import io.github.genichimaruo.singulo.supporter.SupporterCredits.Catalog;
import io.github.genichimaruo.singulo.supporter.SupporterCredits.Entry;
import io.github.genichimaruo.singulo.supporter.SupporterCredits.Tier;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Credit data is common code: a dedicated server must also be able to validate it. */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class SupporterCreditsGameTests {
    private SupporterCreditsGameTests() {}

    @GameTest(setupTicks = 20, template = "empty")
    public static void bundledRosterIsValid(GameTestHelper helper) throws Exception {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        try (var stream = SupporterCredits.class.getResourceAsStream("/assets/singulo/supporters.json")) {
            helper.assertTrue(stream != null, "Bundled supporter roster is missing");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Catalog roster = SupporterCredits.read(reader);
                helper.assertTrue(roster.entries().size() <= 100, "Bundled roster exceeds 100 people");
            }
        }
        helper.succeed();
    }

    @GameTest(setupTicks = 20, template = "empty")
    public static void allHundredNamesSurvivePaging(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            entries.add(new Entry("supporter-" + i, "名前 " + i, i % 2 == 0 ? Tier.STANDARD : Tier.RAINBOW));
        }
        Catalog roster = new Catalog(entries);
        // Handbook and ending capacities, including the narrowest window and incomplete final rows.
        for (int capacity : new int[]{1, 4, 6, 9, 12}) {
            List<Entry> displayed = new ArrayList<>();
            for (int page = 0; page < roster.pageCount(capacity); page++) {
                displayed.addAll(roster.page(page, capacity));
            }
            helper.assertTrue(displayed.equals(entries), "Names omitted, reordered or duplicated at capacity " + capacity);
            helper.assertTrue(roster.page(Integer.MAX_VALUE, capacity).isEmpty(), "Out-of-range page overflowed");
        }
        entries.clear();
        helper.assertTrue(roster.entries().size() == 100, "Caller can mutate roster");
        helper.succeed();
    }

    @GameTest(setupTicks = 20, template = "empty")
    public static void unicodeNamesAndBothTiersLoad(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        Catalog roster = SupporterCredits.read(new StringReader("""
                {"supporters":[
                  {"id":"one","name":"  日本語 e\\u0301  ","tier":"standard"},
                  {"id":"two","name":"%s","tier":"rainbow"}
                ]}
                """.formatted("🌈".repeat(64))));
        helper.assertTrue(roster.entries().get(0).name().equals("日本語 é"), "Name normalization failed");
        helper.assertTrue(roster.entries().get(1).name().codePointCount(0, roster.entries().get(1).name().length()) == 64,
                "Unicode code points were counted as UTF-16 units");
        helper.assertTrue(roster.entries().get(1).tier() == Tier.RAINBOW, "Rainbow tier was lost");
        helper.succeed();
    }

    @GameTest(setupTicks = 20, template = "empty")
    public static void invalidRostersAreRejected(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        for (String invalid : List.of("{}", "null", "[]", "{\"supporters\":[null]}",
                "{\"supporters\":[{\"id\":1,\"name\":\"name\",\"tier\":\"standard\"}]}",
                "{\"supporters\":[{\"id\":\"one\",\"name\":\"name\",\"tier\":\"gold\"}]}",
                "{\"supporters\":[{\"id\":\"one\",\"name\":\"§kname\",\"tier\":\"standard\"}]}")) {
            boolean rejected = false;
            try {
                SupporterCredits.read(new StringReader(invalid));
            } catch (JsonParseException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "Invalid roster accepted: " + invalid);
        }
        List<Entry> duplicate = List.of(new Entry("same", "One", Tier.STANDARD), new Entry("same", "Two", Tier.RAINBOW));
        rejectsCatalog(helper, duplicate);
        List<Entry> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(new Entry("person-" + i, "Name", Tier.STANDARD));
        }
        rejectsCatalog(helper, tooMany);
        helper.assertTrue(SupporterCredits.EMPTY.pageCount(4) == 0 && SupporterCredits.EMPTY.page(0, 4).isEmpty(),
                "Empty roster created a phantom supporter");
        helper.succeed();
    }

    private static void rejectsCatalog(GameTestHelper helper, List<Entry> entries) {
        boolean rejected = false;
        try {
            new Catalog(entries);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Duplicate id or over-capacity roster was accepted");
    }
}
