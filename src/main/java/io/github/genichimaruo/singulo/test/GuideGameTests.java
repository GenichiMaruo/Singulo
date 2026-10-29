package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.ruin.AncientRecords;
import net.minecraft.advancements.Advancement;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 旧文明の記録と進捗の確認。 */
@GameTestHolder("singulo_progression")
@PrefixGameTestTemplate(false)
public final class GuideGameTests {
    private static final String EMPTY = "empty";

    private GuideGameTests() {}

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void decodedRecordsUnlockInOrder(GameTestHelper helper) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
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

    @GameTest(setupTicks = 20, template = EMPTY)
    public static void advancementsAreLoaded(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        var advancements = helper.getLevel().getServer().getAdvancements();
        for (String id : new String[]{"root", "tower", "ignite", "records"}) {
            Advancement h = advancements.getAdvancement(Singulo.id(id));
            helper.assertTrue(h != null, "進捗 " + id + " が読み込まれていない");
        }
        helper.succeed();
    }
    /** mock helper は isCreative() が常に true。生存者の条件には実プレイヤーを使う。 */
    private static ServerPlayer player(GameTestHelper helper, java.util.UUID id) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new com.mojang.authlib.GameProfile(id, "stage6-challenger"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        io.github.genichimaruo.singulo.test.harness.TestScope.ownPlayer(helper, player);
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4));
        player.teleportTo(helper.getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }

    private static boolean done(ServerPlayer player, String id) {
        var advancement = player.server.getAdvancements().getAdvancement(Singulo.id(id));
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** 親の未達成は発火条件にしない。協力討伐の通知は正しい実績だけを解除する。 */
    @GameTest(template = EMPTY)
    public static void milestonesMatchAndDoNotRequireParents(GameTestHelper helper) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
        try {
            io.github.genichimaruo.singulo.registry.SinguloTriggers.milestone(player, "defeat/foundry_overseer");
            helper.assertTrue(done(player, "foundry"), "親が未達成だと討伐実績を受け取れない");
            helper.assertFalse(done(player, "awakened_foundry"), "通常討伐で覚醒討伐も達成した");
            io.github.genichimaruo.singulo.registry.SinguloTriggers.milestone(player, "awakened_foundry_overseer");
            helper.assertTrue(done(player, "awakened_foundry"), "覚醒したファウンドリの条件がつながっていない");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    /** 2種類のメモリは同時に必要。旧型のメモリは、どれか1種類でよい。 */
    @GameTest(template = EMPTY)
    public static void memoryAdvancementsUseAnyAndAllCorrectly(GameTestHelper helper) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
        try {
            var inventory = player.getInventory();
            var axe = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("android_memory_axe")));
            var bit = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("android_memory_bit")));
            var blade = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id("android_memory_blade")));
            inventory.setItem(0, axe);
            net.minecraft.advancements.CriteriaTriggers.INVENTORY_CHANGED.trigger(player, inventory, axe);
            helper.assertFalse(done(player, "android_prototypes"), "メモリ1種類だけで両型を達成した");
            inventory.setItem(1, bit);
            net.minecraft.advancements.CriteriaTriggers.INVENTORY_CHANGED.trigger(player, inventory, bit);
            helper.assertTrue(done(player, "android_prototypes"), "メモリ2種類を持っても達成しない");
            helper.assertFalse(done(player, "android_memory"), "試作機メモリを旧型と扱った");
            inventory.setItem(2, blade);
            net.minecraft.advancements.CriteriaTriggers.INVENTORY_CHANGED.trigger(player, inventory, blade);
            helper.assertTrue(done(player, "android_memory"), "旧型1種類で達成しない");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    /** ログアウト中の注文完了を保存し、再起動後のログインで本人だけへ通知する。 */
    @GameTest(template = EMPTY)
    public static void offlineMilestonesSurviveSaveAndAreDeliveredOnce(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        var server = helper.getLevel().getServer();
        var owner = java.util.UUID.randomUUID();
        io.github.genichimaruo.singulo.registry.PendingMilestones.award(server, owner, "android_order_complete");
        var data = io.github.genichimaruo.singulo.registry.PendingMilestones.get(server);
        var saved = data.save(new net.minecraft.nbt.CompoundTag());
        helper.assertTrue(saved.getList("pending", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().anyMatch(t ->
                ((net.minecraft.nbt.CompoundTag) t).getUUID("owner").equals(owner)), "オフラインの注文者を保存していない");
        var restored = io.github.genichimaruo.singulo.registry.PendingMilestones.load(saved);
        ServerPlayer player = player(helper, owner);
        try {
            helper.assertTrue(done(player, "android_delivery"), "本当のログインイベントで保留通知が届かない");
            // ログインで元データから届いた履歴を外し、保存から復元したデータだけでも通知できることを確かめる。
            player.getAdvancements().revoke(server.getAdvancements().getAdvancement(Singulo.id("android_delivery")), "done");
            restored.deliver(player);
            helper.assertTrue(done(player, "android_delivery"), "保存・復元した完了通知が届かない");
            helper.assertFalse(done(player, "android_printed"), "注文だけで印刷まで達成した");
            helper.assertFalse(restored.save(new net.minecraft.nbt.CompoundTag())
                    .getList("pending", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().anyMatch(t ->
                            ((net.minecraft.nbt.CompoundTag) t).getUUID("owner").equals(owner)), "通知後にも本人の保留データが残った");
            helper.succeed();
        } finally {
            server.getPlayerList().remove(player);
        }
    }

    /** 本物の印刷完了と指令台の完了処理から、持ち主・注文者へ通知がつながる。 */
    @GameTest(template = EMPTY, batch = "progression_completion", timeoutTicks = 100)
    public static void printingAndDeliveryEmitOwnerMilestones(GameTestHelper helper) {
        ServerPlayer player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
        var bit = io.github.genichimaruo.singulo.registry.SinguloEntities.BIT_ANDROID.get().create(helper.getLevel());
        bit.setNoAi(true);
        bit.setNoGravity(true);
        bit.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4))));
        bit.makeGuard(player.getUUID(), helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 4)), 0, 0);
        bit.startPrinting();
        helper.getLevel().addFreshEntity(bit);
        var consolePos = new net.minecraft.core.BlockPos(1, 1, 1);
        helper.setBlock(consolePos, io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity console = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, consolePos);
        var order = new io.github.genichimaruo.singulo.android.work.WorkOrder(1, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS), 4, player.getUUID());
        console.orders().add(order);
        helper.runAtTickTime(10, () -> {
            helper.assertFalse(done(player, "android_printed") || done(player, "android_delivery"), "完了前に達成した");
            order.delivered = 4;
        });
        helper.runAtTickTime(75, () -> {
            try {
                io.github.genichimaruo.singulo.registry.PendingMilestones.get(player.server).deliver(player);
                helper.assertTrue(done(player, "android_printed"), "印刷完了から実績が発火しない");
                helper.assertTrue(done(player, "android_delivery"), "注文完了から実績が発火しない");
                helper.succeed();
            } finally {
                bit.discard();
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        });
    }

    /** ラストヒット以外の生存者にも与え、クリエイティブには与えない。 */
    @GameTest(template = EMPTY, batch = "progression_boss", timeoutTicks = 30)
    public static void normalBossCreditsSurvivingChallengers(GameTestHelper helper) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(helper);
        ServerPlayer first = player(helper, java.util.UUID.randomUUID());
        ServerPlayer second = player(helper, java.util.UUID.randomUUID());
        ServerPlayer creative = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
        var at = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4)));
        first.setPos(at); second.setPos(at); creative.setPos(at);
        first.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        second.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        creative.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var boss = io.github.genichimaruo.singulo.registry.SinguloEntities.FOUNDRY_OVERSEER.get().create(helper.getLevel());
        boss.setNoAi(true);
        boss.moveTo(at);
        helper.getLevel().addFreshEntity(boss);
        try {
            boss.setHealth(0);
            boss.die(boss.damageSources().generic());
            helper.assertTrue(done(first, "foundry") && done(second, "foundry"), "ラストヒット以外の挑戦者に実績が届かない");
            helper.assertFalse(done(creative, "foundry"), "クリエイティブにも討伐実績が届いた");
            helper.succeed();
        } finally {
            boss.discard();
            for (var p : new ServerPlayer[]{first, second, creative}) helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }

}
