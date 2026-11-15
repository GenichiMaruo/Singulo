package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.compat.ItemResourceView;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Regression cases for the one-way adapter; never use this adapter to advertise a legacy transaction. */
@GameTestHolder("singulo_scaling")
@PrefixGameTestTemplate(false)
public final class ItemResourceViewGameTests {
    @GameTest(template = "empty")
    public static void simulationRollsBackAndRealOperationsCommit(GameTestHelper h) {
        var nativeStore = new TestStore();
        var legacy = new ItemResourceView(nativeStore);
        var input = new ItemStack(Items.DIAMOND, 12);
        h.assertTrue(legacy.insertItem(0, input, true).getCount() == 4, "Partial simulation result");
        h.assertTrue(nativeStore.count == 0 && input.getCount() == 12, "Simulation mutated state or argument");
        h.assertTrue(legacy.insertItem(0, input, false).getCount() == 4 && nativeStore.count == 8, "Insert must commit exactly once");
        var shown = legacy.getStackInSlot(0);
        shown.setCount(1);
        h.assertTrue(nativeStore.count == 8, "Returned stack aliases storage");
        h.assertTrue(legacy.extractItem(0, 3, true).getCount() == 3 && nativeStore.count == 8, "Extract simulation did not roll back");
        h.assertTrue(legacy.extractItem(0, 3, false).getCount() == 3 && nativeStore.count == 5, "Extract did not commit");
        h.assertTrue(legacy.extractItem(0, 0, false).isEmpty() && nativeStore.count == 5, "Zero extraction changed state");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void existingTransactionCannotBeSilentlyCommitted(GameTestHelper h) {
        var nativeStore = new TestStore();
        var legacy = new ItemResourceView(nativeStore);
        boolean refused = false;
        try (var outer = Transaction.openRoot()) {
            try { legacy.insertItem(0, new ItemStack(Items.DIAMOND), false); }
            catch (IllegalStateException expected) { refused = true; }
        }
        h.assertTrue(refused && nativeStore.count == 0, "Legacy operation bypassed outer transaction ownership");
        h.succeed();
    }

    private static final class TestStore extends SnapshotJournal<Integer> implements ResourceHandler<ItemResource> {
        int count;
        @Override public int size() { return 1; }
        @Override public ItemResource getResource(int index) { return count == 0 ? ItemResource.EMPTY : ItemResource.of(Items.DIAMOND); }
        @Override public long getAmountAsLong(int index) { return count; }
        @Override public long getCapacityAsLong(int index, ItemResource resource) { return 8; }
        @Override public boolean isValid(int index, ItemResource resource) { return resource.is(Items.DIAMOND); }
        @Override public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            if (!isValid(index, resource)) return 0;
            updateSnapshots(tx);
            int accepted = Math.min(amount, 8 - count); count += accepted; return accepted;
        }
        @Override public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            if (!resource.is(Items.DIAMOND)) return 0;
            updateSnapshots(tx);
            int taken = Math.min(amount, count); count -= taken; return taken;
        }
        @Override protected Integer createSnapshot() { return count; }
        @Override protected void revertToSnapshot(Integer state) { count = state; }
    }
}
