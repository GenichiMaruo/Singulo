package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.ruin.AncientRecords;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 旧文明の記録と進捗の確認。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class GuideGameTests {
    private static final String EMPTY = "empty";

    private GuideGameTests() {}

    @GameTest(template = EMPTY)
    public static void decodedRecordsUnlockInOrder(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.getAbilities().instabuild = false;
            ItemStack rec = new ItemStack(SinguloItems.DECODED_RECORD.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, rec);
            rec.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            rec.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(AncientRecords.decoded(player).equals(GeneratedContent.RECORDS.subList(0, 2)),
                    "記録が順番に増えない: " + AncientRecords.decoded(player));
            helper.assertTrue(rec.isEmpty(), "読んだ記録が消えない");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    @GameTest(template = EMPTY)
    public static void advancementsAreLoaded(GameTestHelper helper) {
        var advancements = helper.getLevel().getServer().getAdvancements();
        for (String id : new String[]{"root", "tower", "ignite", "records"}) {
            AdvancementHolder h = advancements.get(Singulo.id(id));
            helper.assertTrue(h != null, "進捗 " + id + " が読み込まれていない");
        }
        helper.succeed();
    }
}
