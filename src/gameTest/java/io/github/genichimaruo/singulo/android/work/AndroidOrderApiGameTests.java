package io.github.genichimaruo.singulo.android.work;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import io.github.genichimaruo.singulo.api.android.AndroidOrders.State;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidOrderApiGameTests {
    private static final BlockPos CONSOLE=new BlockPos(0,1,0), SUPPLY=new BlockPos(1,1,0), CHEST=new BlockPos(1,1,1),
            DELIVERY=new BlockPos(3,1,0), RACK=new BlockPos(4,1,0), TABLE=new BlockPos(4,1,3);
    private static Item item(String name) { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("singulo",name)); }
    private static ItemStack recipeCard(GameTestHelper h,List<ItemStack> grid,ItemStack output) {
        var card=new ItemStack(item("recipe_card")); new RecipeData(grid,output).write(card,h.getLevel().registryAccess()); return card;
    }
    private static WorkConsoleBlockEntity workshop(GameTestHelper helper, int logs) {
        var level = helper.getLevel();
        helper.setBlock(CONSOLE, SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        helper.setBlock(SUPPLY, SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        helper.setBlock(CHEST, net.minecraft.world.level.block.Blocks.CHEST);
        helper.setBlock(DELIVERY, SinguloBlocks.ANDROID_DELIVERY_PORT.get());
        helper.setBlock(RACK, SinguloBlocks.ANDROID_RECIPE_RACK.get());
        helper.setBlock(TABLE, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG, logs));
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
        ItemStack link = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(link, level, helper.absolutePos(CONSOLE),
                SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        for (BlockPos p : List.of(SUPPLY, DELIVERY, RACK)) {
            io.github.genichimaruo.singulo.android.work.WorkLinkedBlockEntity b = helper.getBlockEntity(p);
            b.useItem(player, link, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        ItemStack table = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(table, level, helper.absolutePos(TABLE),
                net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = helper.getBlockEntity(RACK);
        java.util.List<ItemStack> planks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        planks.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG));
        java.util.List<ItemStack> sticks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        sticks.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        sticks.set(3, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        rack.items().setStackInSlot(0, table);
        rack.items().setStackInSlot(1, recipeCard(helper, planks, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4)));
        rack.items().setStackInSlot(2, recipeCard(helper, sticks, new ItemStack(net.minecraft.world.item.Items.STICK, 4)));
        return helper.getBlockEntity(CONSOLE);
    }

    private static WorkConsoleBlockEntity setup(GameTestHelper h,boolean moving) {
        var c=workshop(h,32); c.setOwner(TestScope.player(h).getUUID());
        var a=SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        a.makeGuard(c.owner(),h.absolutePos(new BlockPos(2,1,4)),RuinAndroid.MODE_GUARD,30);
        a.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,2))));
        a.setNoAi(!moving); h.getLevel().addFreshEntity(a); return c;
    }
    private static AndroidOrders.Receipt submit(GameTestHelper h, UUID id,long count) {
        return AndroidOrders.submit(h.getLevel(),h.absolutePos(CONSOLE),((WorkConsoleBlockEntity)h.getBlockEntity(CONSOLE)).owner(),id,
                new ItemStack(Items.STICK),count,List.of(),h.absolutePos(DELIVERY));
    }
    private static AndroidOrders.Receipt status(GameTestHelper h,UUID id) {
        return AndroidOrders.status(h.getLevel(),h.absolutePos(CONSOLE),((WorkConsoleBlockEntity)h.getBlockEntity(CONSOLE)).owner(),id);
    }
    @GameTest(template="empty")
    public static void ownerCountsAndDestinationsAreChecked(GameTestHelper h) {
        var c=setup(h,false); var id=UUID.randomUUID();
        var denied=AndroidOrders.submit(h.getLevel(),c.getBlockPos(),UUID.randomUUID(),id,new ItemStack(Items.STICK),8,List.of(),null);
        h.assertTrue(denied.state()==State.REJECTED,"Non-owner submitted order");
        h.assertTrue(submit(h,id,0).state()==State.REJECTED && submit(h,id,4097).state()==State.REJECTED,"Count was silently clamped");
        h.assertTrue(AndroidOrders.submit(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.STICK),8,List.of(),c.getBlockPos().above()).state()==State.REJECTED,"Invalid port fell back silently");
        h.assertTrue(c.orders().isEmpty(),"Rejected request created work"); h.succeed();
    }
    @GameTest(template="empty")
    public static void replayCreatesExactlyOneOrderAndRejectsConflicts(GameTestHelper h) {
        var c=setup(h,false); var id=UUID.randomUUID(); var first=submit(h,id,8);
        h.assertTrue(first.state()==State.RUNNING,"Fixture rejected: "+first.reason());
        var replay=submit(h,id,8); h.assertTrue(replay.orderId()==first.orderId() && c.orders().size()==1,"Duplicate order");
        h.assertTrue(submit(h,id,4).state()==State.REJECTED && c.orders().size()==1,"Changed request ID accepted");
        h.assertTrue(!AndroidOrders.acknowledge(h.getLevel(),c.getBlockPos(),c.owner(),id),"Running receipt acknowledged"); h.succeed();
    }
    @GameTest(template="empty")
    public static void cancelIsExplicitPersistentAndAcknowledged(GameTestHelper h) {
        var c=setup(h,false); var id=UUID.randomUUID(); h.assertTrue(submit(h,id,8).state()==State.RUNNING,"No order");
        h.assertTrue(AndroidOrders.cancel(h.getLevel(),c.getBlockPos(),UUID.randomUUID(),id).state()==State.REJECTED,"Non-owner cancelled");
        var cancelled=AndroidOrders.cancel(h.getLevel(),c.getBlockPos(),c.owner(),id);
        h.assertTrue(cancelled.state()==State.CANCELLED && c.orders().isEmpty(),"Cancellation not tracked");
        var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); c.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(status(h,id).state()==State.CANCELLED && submit(h,id,8).state()==State.CANCELLED,"Reload/replay restarted cancelled job");
        h.assertTrue(!AndroidOrders.acknowledge(h.getLevel(),c.getBlockPos(),UUID.randomUUID(),id),"Non-owner acknowledged");
        h.assertTrue(AndroidOrders.acknowledge(h.getLevel(),c.getBlockPos(),c.owner(),id) && status(h,id).state()==State.UNKNOWN,"Receipt acknowledgement failed"); h.succeed();
    }
    @GameTest(template="empty")
    public static void runningReceiptSurvivesReloadWithoutDuplicatingWork(GameTestHelper h) {
        var c=setup(h,false); var id=UUID.randomUUID(); var first=submit(h,id,8);
        h.assertTrue(first.state()==State.RUNNING,"No order");
        var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); c.loadWithComponents(tag,h.getLevel().registryAccess());
        var again=submit(h,id,8); h.assertTrue(again.state()==State.RUNNING && again.orderId()==first.orderId() && c.orders().size()==1,"Reload duplicated or lost order"); h.succeed();
    }
    @GameTest(template="empty")
    public static void missingOrderNeverMeansCompleted(GameTestHelper h) {
        var c=setup(h,false); var id=UUID.randomUUID(); submit(h,id,8);
        var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); tag.put("orders",new ListTag()); c.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(status(h,id).state()==State.FAILED,"Missing order reported success");
        h.assertTrue(status(h,UUID.randomUUID()).state()==State.UNKNOWN,"Unknown ID reported success");
        h.assertTrue(AndroidOrders.status(h.getLevel(),c.getBlockPos().above(),c.owner(),id).state()==State.UNAVAILABLE,"Missing console reported success"); h.succeed();
    }
    @GameTest(template="empty")
    public static void terminalHistoryIsBoundedAndNotSilentlyEvicted(GameTestHelper h) {
        var c=setup(h,false); var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); var list=new ListTag(); UUID oldest=null;
        for(int i=0;i<AndroidOrders.MAX_RECEIPTS;i++) {
            var id=UUID.randomUUID(); if(i==0)oldest=id;
            var e=new ExternalOrderLedger(id,c.owner(),i+1,new ItemStack(Items.STICK),8,List.of(),h.absolutePos(DELIVERY));
            e.state=State.CANCELLED; list.add(e.save(h.getLevel().registryAccess()));
        }
        tag.put("external_orders_v1",list); c.loadWithComponents(tag,h.getLevel().registryAccess());
        var rejected=submit(h,UUID.randomUUID(),8); h.assertTrue(rejected.state()==State.REJECTED && rejected.reason().startsWith("receipt_limit"),"History cap not enforced");
        h.assertTrue(status(h,oldest).state()==State.CANCELLED,"Old receipt silently evicted");
        h.assertTrue(AndroidOrders.acknowledge(h.getLevel(),c.getBlockPos(),c.owner(),oldest),"Cannot clear terminal receipt");
        h.assertTrue(submit(h,UUID.randomUUID(),8).state()==State.RUNNING,"Acknowledgement did not free capacity"); h.succeed();
    }
    @GameTest(template="empty")
    public static void apiRejectsOffThreadCallsBeforeWorldAccess(GameTestHelper h) {
        var c=setup(h,false); var owner=c.owner(); var pos=c.getBlockPos();
        boolean rejected=java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try { AndroidOrders.status(h.getLevel(),pos,owner,UUID.randomUUID()); return false; }
            catch(IllegalStateException expected) { return true; }
        }).join();
        h.assertTrue(rejected,"Off-thread API call accepted"); h.succeed();
    }
    @GameTest(template="empty")
    public static void snapshotIsOwnerOnlyAndDetached(GameTestHelper h) {
        var c=setup(h,false); var id=UUID.randomUUID(); submit(h,id,8);
        var snap=AndroidOrders.snapshot(h.getLevel(),c.getBlockPos(),c.owner());
        h.assertTrue(snap.state()==AndroidOrders.SnapshotState.AVAILABLE && snap.activeCount()==1 && snap.historyCount()==0,"Wrong active snapshot");
        h.assertTrue(snap.receiptCapacity()==256 && snap.remainingReceiptCapacity()==255 && snap.maxRequestCount()==4096,"Wrong limits");
        boolean immutable=false; try { snap.receipts().clear(); } catch(UnsupportedOperationException expected) { immutable=true; }
        h.assertTrue(immutable,"Mutable receipt list");
        AndroidOrders.cancel(h.getLevel(),c.getBlockPos(),c.owner(),id);
        var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); c.loadWithComponents(tag,h.getLevel().registryAccess());
        var after=AndroidOrders.snapshot(h.getLevel(),c.getBlockPos(),c.owner());
        h.assertTrue(after.activeCount()==0 && after.historyCount()==1 && after.receipts().getFirst().state()==State.CANCELLED,"History lost on reload");
        h.assertTrue(snap.receipts().getFirst().state()==State.RUNNING,"Snapshot changed after cancellation");
        var denied=AndroidOrders.snapshot(h.getLevel(),c.getBlockPos(),UUID.randomUUID());
        h.assertTrue(denied.state()==AndroidOrders.SnapshotState.REJECTED && denied.receipts().isEmpty()
                && denied.activeCount()==0 && denied.historyCount()==0 && denied.remainingReceiptCapacity()==0,"Non-owner information leak");
        var source=new java.util.ArrayList<>(after.receipts());
        var copy=new AndroidOrders.ConsoleSnapshot(after.state(),source,0,1,256,255,4096); source.clear();
        h.assertTrue(copy.receipts().size()==1,"Constructor retained mutable list"); h.succeed();
    }
    @GameTest(template="empty")
    public static void snapshotCapacityIncludesOtherOwnersWithoutExposingReceipts(GameTestHelper h) {
        var c=setup(h,false); var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); var list=new ListTag();
        for(int i=0;i<AndroidOrders.MAX_RECEIPTS;i++) {
            var e=new ExternalOrderLedger(UUID.randomUUID(),i==0?c.owner():UUID.randomUUID(),i+1,new ItemStack(Items.STICK),8,List.of(),null);
            e.state=State.CANCELLED; list.add(e.save(h.getLevel().registryAccess()));
        }
        tag.put("external_orders_v1",list); c.loadWithComponents(tag,h.getLevel().registryAccess());
        var snap=AndroidOrders.snapshot(h.getLevel(),c.getBlockPos(),c.owner());
        h.assertTrue(snap.receipts().size()==1 && snap.historyCount()==1 && snap.remainingReceiptCapacity()==0,"Foreign receipt leak or false capacity");
        AndroidOrders.acknowledge(h.getLevel(),c.getBlockPos(),c.owner(),snap.receipts().getFirst().requestId());
        h.assertTrue(AndroidOrders.snapshot(h.getLevel(),c.getBlockPos(),c.owner()).remainingReceiptCapacity()==1,"Capacity not freed"); h.succeed();
    }
    @GameTest(template="empty")
    public static void snapshotMissingOrderIsReadOnlyFailure(GameTestHelper h) {
        var c=setup(h,false); var id=UUID.randomUUID(); submit(h,id,8);
        var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); tag.put("orders",new ListTag()); c.loadWithComponents(tag,h.getLevel().registryAccess());
        var before=c.saveWithoutMetadata(h.getLevel().registryAccess());
        var snap=AndroidOrders.snapshot(h.getLevel(),c.getBlockPos(),c.owner());
        h.assertTrue(snap.activeCount()==0 && snap.historyCount()==1 && snap.receipts().getFirst().state()==State.FAILED,"Missing order is not failure");
        h.assertTrue(before.equals(c.saveWithoutMetadata(h.getLevel().registryAccess())),"Snapshot mutated persistent state"); h.succeed();
    }
    @GameTest(template="empty")
    public static void snapshotUnavailableAndThreadConstraints(GameTestHelper h) {
        var c=setup(h,false); var owner=c.owner(); var pos=c.getBlockPos();
        var missing=AndroidOrders.snapshot(h.getLevel(),pos.above(),owner);
        h.assertTrue(missing.state()==AndroidOrders.SnapshotState.UNAVAILABLE && missing.receipts().isEmpty() && missing.remainingReceiptCapacity()==0,"Missing console not unavailable");
        var far=new BlockPos(28000000,64,28000000);
        h.assertTrue(!h.getLevel().hasChunk(far.getX()>>4,far.getZ()>>4),"Fixture chunk already loaded");
        h.assertTrue(AndroidOrders.snapshot(h.getLevel(),far,owner).state()==AndroidOrders.SnapshotState.UNAVAILABLE,"Unloaded console not unavailable");
        h.assertTrue(!h.getLevel().hasChunk(far.getX()>>4,far.getZ()>>4),"Snapshot loaded chunk");
        boolean rejected=java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try { AndroidOrders.snapshot(h.getLevel(),pos,owner); return false; }
            catch(IllegalStateException expected) { return true; }
        }).join();
        h.assertTrue(rejected,"Off-thread snapshot accepted"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=2400)
    public static void actualCraftAndDeliveryProducesPersistentCompletion(GameTestHelper h) {
        var c=setup(h,true); var id=UUID.randomUUID();
        h.assertTrue(submit(h,id,8).state()==State.RUNNING,"Order rejected");
        h.succeedWhen(()->{
            var receipt=status(h,id); h.assertTrue(receipt.state()==State.COMPLETED,"Not completed: "+receipt.state());
            h.assertTrue(receipt.delivered()==8 && c.orders().isEmpty(),"Completion before delivery");
            DeliveryPortBlockEntity port=h.getBlockEntity(DELIVERY); int count=0;
            for(int i=0;i<port.handler().getSlots();i++)if(port.handler().getStackInSlot(i).is(Items.STICK))count+=port.handler().getStackInSlot(i).getCount();
            h.assertTrue(count==8,"Wrong physical output count");
            var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); c.loadWithComponents(tag,h.getLevel().registryAccess());
            h.assertTrue(status(h,id).state()==State.COMPLETED && submit(h,id,8).state()==State.COMPLETED,"Terminal receipt lost");
        });
    }
}
