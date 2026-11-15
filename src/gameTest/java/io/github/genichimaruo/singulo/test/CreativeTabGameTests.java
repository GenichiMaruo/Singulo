package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.HashSet;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class CreativeTabGameTests {
    @GameTest(template = "empty")
    public static void registeredCreativeItemsFollowGeneratedOrder(GameTestHelper helper) {
        var actual = SinguloItems.TAB_ORDER.stream().map(item -> item.getId().getPath()).toList();
        helper.assertTrue(new HashSet<>(actual).size() == actual.size(), "Duplicate creative item");
        helper.assertTrue(actual.stream().noneMatch(SinguloItems.HIDDEN::contains), "Hidden item leaked into tab");
        var expected = GeneratedContent.TAB_ORDER.stream().filter(id -> !SinguloItems.HIDDEN.contains(id)).toList();
        var missing = new HashSet<>(expected);
        missing.removeAll(actual);
        var unexpected = new HashSet<>(actual);
        unexpected.removeAll(expected);
        helper.assertTrue(actual.equals(expected), "Creative order mismatch; missing=" + missing + ", unexpected=" + unexpected);
        boolean eggs = false;
        for (String id : actual) {
            if (id.endsWith("_spawn_egg")) eggs = true;
            helper.assertTrue(!eggs || id.endsWith("_spawn_egg"), "Non-egg after spawn eggs: " + id);
        }
        helper.succeed();
    }
}
