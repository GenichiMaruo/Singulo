package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * アンドロイドが落ちている物を拾い、預ける（0.21.0）: 拾った物は近くのチェストへ預ける。預け先を「預けない」にすると持ったまま。
 * プレイヤーが投げた物は拾わない。アンドロイドは8ブロック先まで拾いに行くので、隣のテストの物を拾わないよう、テストごとに別の組で流す。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidCollectGameTests {
    private static final int WAIT = 260;

    private static RuinAndroid guard(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // 床（0〜8）を低い壁で囲み、ぶらついて場所の外へ出ないようにする
        for (int x = -1; x <= 9; x++) {
            for (int z = -1; z <= 9; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                if (x == -1 || x == 9 || z == -1 || z == 9) {
                    h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                }
            }
        }
        RuinAndroid a = SinguloEntities.BLADE_ANDROID.get().create(level);
        BlockPos at = h.absolutePos(new BlockPos(4, 1, 4));
        a.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        a.makeGuard(null, at, RuinAndroid.MODE_GUARD, 30);
        level.addFreshEntity(a);
        return a;
    }

    private static ItemEntity drop(GameTestHelper h, BlockPos rel, ItemStack stack) {
        BlockPos at = h.absolutePos(rel);
        ItemEntity e = new ItemEntity(h.getLevel(), at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, stack);
        e.setNoPickUpDelay();
        e.setDeltaMovement(0, 0, 0);
        h.getLevel().addFreshEntity(e);
        return e;
    }

    @GameTest(template = "empty", timeoutTicks = WAIT + 40, batch = "android_collect_store")
    public static void androidsCollectDropsAndStoreThemInAChest(GameTestHelper h) {
        RuinAndroid a = guard(h);
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);   // 離れた所に人がいる（いつもの遊び方と同じ）
        BlockPos far = h.absolutePos(new BlockPos(7, 1, 0));
        player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        h.setBlock(new BlockPos(6, 1, 6), Blocks.CHEST);
        drop(h, new BlockPos(4, 1, 2), new ItemStack(Items.ROTTEN_FLESH, 5));
        drop(h, new BlockPos(2, 1, 4), new ItemStack(Items.BONE, 3));
        h.runAfterDelay(WAIT, () -> {
            ChestBlockEntity chest = h.getBlockEntity(new BlockPos(6, 1, 6));
            int flesh = 0, bone = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                flesh += s.is(Items.ROTTEN_FLESH) ? s.getCount() : 0;
                bone += s.is(Items.BONE) ? s.getCount() : 0;
            }
            h.assertTrue(flesh == 5 && bone == 3, "The drops were not stored in the chest: flesh " + flesh + ", bone " + bone);
            a.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = WAIT + 40, batch = "android_collect_keep")
    public static void androidsKeepPickupsWhenStoringIsOffAndSkipThrownItems(GameTestHelper h) {
        RuinAndroid a = guard(h);
        a.cycleStoreMode();
        a.cycleStoreMode();
        h.assertTrue(a.storeMode() == RuinAndroid.STORE_OFF, "Storing is not off");
        h.setBlock(new BlockPos(6, 1, 6), Blocks.CHEST);
        drop(h, new BlockPos(4, 1, 2), new ItemStack(Items.ROTTEN_FLESH, 5));
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        BlockPos far = h.absolutePos(new BlockPos(7, 1, 0));
        player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        ItemEntity thrown = drop(h, new BlockPos(2, 1, 4), new ItemStack(Items.DIAMOND));
        thrown.setThrower(player);
        h.runAfterDelay(WAIT, () -> {
            int carried = 0;
            for (int i = 0; i < a.inventory().getContainerSize(); i++) {
                ItemStack s = a.inventory().getItem(i);
                carried += s.is(Items.ROTTEN_FLESH) ? s.getCount() : 0;
                h.assertTrue(!s.is(Items.DIAMOND), "The android picked up an item thrown by a player");
            }
            h.assertTrue(carried == 5, "The android did not keep its pickups: " + carried);
            h.assertTrue(thrown.isAlive(), "The thrown item is gone");
            ChestBlockEntity chest = h.getBlockEntity(new BlockPos(6, 1, 6));
            h.assertTrue(chest.isEmpty(), "Items were stored although storing is off");
            a.discard();
            h.succeed();
        });
    }
}
