package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.android.AndroidMenu;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.BlockPos;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

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
        RuinAndroid a = SinguloEntities.BLADE_ANDROID.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        BlockPos at = h.absolutePos(new BlockPos(4, 1, 4));
        a.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
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
        a.inventory().setItem(RuinAndroid.CARRY_START, new ItemStack(Items.ROTTEN_FLESH, 13));
        a.inventory().setItem(RuinAndroid.CARRY_START + 1, new ItemStack(Items.DIAMOND));
        drop(h, new BlockPos(4, 1, 2), new ItemStack(Items.ROTTEN_FLESH, 5));
        drop(h, new BlockPos(2, 1, 4), new ItemStack(Items.BONE, 3));
        h.runAfterDelay(WAIT, () -> {
            ChestBlockEntity chest = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, new BlockPos(6, 1, 6));
            int flesh = 0, bone = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                flesh += s.is(Items.ROTTEN_FLESH) ? s.getCount() : 0;
                bone += s.is(Items.BONE) ? s.getCount() : 0;
            }
            h.assertTrue(flesh == 5 && bone == 3, "The drops were not stored in the chest: flesh " + flesh + ", bone " + bone);
            h.assertTrue(a.inventory().countItem(Items.ROTTEN_FLESH) == 13 && a.inventory().countItem(Items.DIAMOND) == 1,
                    "Automatic storage took cargo the owner had given the guard");
            a.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 420, batch = "android_collect_reload")
    public static void androidAutoStashKeepsEditedCargoAfterReload(GameTestHelper h) {
        RuinAndroid guard = guard(h);
        guard.cycleStoreMode();
        guard.cycleStoreMode(); // Keep pickups until the inventory has been edited.
        int fleshSlot = RuinAndroid.CARRY_START, boneSlot = fleshSlot + 1;
        guard.inventory().setItem(fleshSlot, new ItemStack(Items.ROTTEN_FLESH, 10));
        guard.inventory().setItem(boneSlot, new ItemStack(Items.BONE));
        int firstStone = boneSlot + 1;
        guard.inventory().setItem(firstStone, new ItemStack(Items.STONE, 64));
        BlockPos start = new BlockPos(4, 1, 4);
        drop(h, start, new ItemStack(Items.ROTTEN_FLESH, 5));
        drop(h, start, new ItemStack(Items.BONE, 3));
        drop(h, start, new ItemStack(Items.STONE, 10));
        BlockPos box = new BlockPos(5, 1, 4);
        h.setBlock(box, Blocks.CHEST);
        ChestBlockEntity chest = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, box);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.STONE, 64));
        chest.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 63));
        RuinAndroid reloaded = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(guard.inventory().countItem(Items.ROTTEN_FLESH) == 15
                        && guard.inventory().countItem(Items.BONE) == 4 && guard.inventory().countItem(Items.STONE) == 74,
                        "Guard has not collected all three drops"))
                .thenExecute(() -> {
                    guard.inventory().removeItem(fleshSlot, 2);
                    var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
                    AndroidMenu menu = new AndroidMenu(1, player.getInventory(), guard);
                    menu.setPage(AndroidMenu.PAGE_GEAR);
                    menu.setCarried(new ItemStack(Items.ROTTEN_FLESH, 8));
                    menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ContainerInput.QUICK_CRAFT, player);
                    menu.clicked(fleshSlot, AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT, player);
                    menu.clicked(RuinAndroid.INVENTORY_SIZE, AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT, player);
                    menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ContainerInput.QUICK_CRAFT, player);
                    h.assertTrue(guard.inventory().countItem(Items.ROTTEN_FLESH) == 17, "Drag did not add four personal items to the pickup stack");
                    guard.inventory().setItem(boneSlot, new ItemStack(Items.BONE, 7)); // All seven are personal.
                    guard.inventory().removeItem(firstStone, 4);
                    guard.inventory().setItem(firstStone + 2, new ItemStack(Items.STONE, 20));
                    // 60 personal stones, then 10 picked up, then 20 personal. Sorting merges both ways.
                    guard.sortCarry();
                    guard.cycleStoreMode();
                    CompoundTag tag = new CompoundTag();
                    io.github.genichimaruo.singulo.test.harness.TestNbt.save(guard, tag);
                    guard.discard();
                    io.github.genichimaruo.singulo.test.harness.TestNbt.load(reloaded, tag);
                    reloaded.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(start)));
                    h.getLevel().addFreshEntity(reloaded);
                })
                .thenWaitUntil(() -> h.assertTrue(chest.countItem(Items.ROTTEN_FLESH) == 64, "The reloaded guard did not deposit its pickup"))
                .thenExecute(() -> {
                    h.assertTrue(reloaded.inventory().countItem(Items.ROTTEN_FLESH) == 16, "A partial deposit removed too much cargo");
                    chest.clearContent();
                    reloaded.cycleStoreMode();
                })
                .thenWaitUntil(() -> h.assertTrue(chest.countItem(Items.ROTTEN_FLESH) == 2, "The remaining two pickups were not deposited"))
                .thenExecute(() -> {
                    h.assertTrue(chest.countItem(Items.STONE) == 10 && reloaded.inventory().countItem(Items.STONE) == 80,
                            "Compaction reassigned personal stone to a previously split pickup stack");
                    h.assertTrue(reloaded.inventory().countItem(Items.ROTTEN_FLESH) == 14 && reloaded.inventory().countItem(Items.BONE) == 7,
                            "Sorting, reloading or a slot replacement reclassified personal items as pickups");
                    h.assertTrue(reloaded.requestStash() == null, "Could not manually deposit personal cargo");
                })
                .thenWaitUntil(() -> h.assertTrue(chest.countItem(Items.ROTTEN_FLESH) == 16 && chest.countItem(Items.BONE) == 7,
                        "Manual storage did not include all remaining cargo"))
                .thenExecute(reloaded::discard)
                .thenSucceed();
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
            ChestBlockEntity chest = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, new BlockPos(6, 1, 6));
            h.assertTrue(chest.isEmpty(), "Items were stored although storing is off");
            a.discard();
            h.succeed();
        });
    }
}
