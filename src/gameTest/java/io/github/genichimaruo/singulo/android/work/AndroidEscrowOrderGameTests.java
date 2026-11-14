package io.github.genichimaruo.singulo.android.work;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import io.github.genichimaruo.singulo.api.android.AndroidEscrows;
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
public final class AndroidEscrowOrderGameTests {
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
    private static AndroidEscrows.Submission offer(GameTestHelper h,WorkConsoleBlockEntity c,UUID id,int logs) {
        return AndroidEscrows.submit(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.OAK_PLANKS),4,
                List.of(new ItemStack(Items.OAK_LOG,logs)),null);
    }
    private static RuinAndroid worker(GameTestHelper h,WorkConsoleBlockEntity c,int order) {
        var ids=c.order(order).workers;
        return h.getLevel().getEntitiesOfClass(RuinAndroid.class,new net.minecraft.world.phys.AABB(c.getBlockPos()).inflate(16))
                .stream().filter(a->ids.contains(a.getUUID())).findFirst().orElseThrow();
    }
    private static long count(List<ItemStack> stacks,net.minecraft.world.item.Item item) {
        return stacks.stream().filter(s->s.is(item)).mapToLong(ItemStack::getCount).sum();
    }
    @GameTest(template="empty")
    public static void escrowNeverUsesSharedStockAndRejectionTransfersNothing(GameTestHelper h) {
        var c=setup(h,false); var id=UUID.randomUUID(); var offered=new ItemStack(Items.DIRT,1);
        var denied=AndroidEscrows.submit(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.OAK_PLANKS),4,List.of(offered),null);
        h.assertTrue(!denied.acceptedNow() && denied.receipt().state()==State.REJECTED,"Shared logs covered wrong offer");
        h.assertTrue(offered.getCount()==1 && c.orders().isEmpty() && AndroidEscrows.snapshot(h.getLevel(),c.owner(),id).phase()==AndroidEscrows.Phase.UNKNOWN,"Rejected offer mutated ownership");
        // A crafting chain (logs -> planks -> sticks) is planned only from the offer: 1 log cannot make 64 sticks,
        // and the 32 shared logs must not make up the difference.
        var shortChain=AndroidEscrows.submit(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.STICK),64,
                List.of(new ItemStack(Items.OAK_LOG)),null);
        h.assertTrue(!shortChain.acceptedNow() && shortChain.receipt().state()==State.REJECTED
                && AndroidEscrows.snapshot(h.getLevel(),c.owner(),id).phase()==AndroidEscrows.Phase.UNKNOWN,"Insufficient chain offer took ownership");
        var accepted=offer(h,c,id,1); h.assertTrue(accepted.acceptedNow(),"Valid offer rejected");
        var snapshot=AndroidEscrows.snapshot(h.getLevel(),c.owner(),id);
        h.assertTrue(count(snapshot.materials(),Items.OAK_LOG)==1,"Escrow did not own exact offer");
        h.assertTrue(AndroidEscrows.claim(h.getLevel(),c.owner(),id,false,0,1,false).isEmpty(),"Live escrow material refunded"); h.succeed();
    }
    @GameTest(template="empty")
    public static void replayIsIdempotentAndChangedOfferIsRejected(GameTestHelper h) {
        var c=setup(h,false); var id=UUID.randomUUID(); var first=offer(h,c,id,1);
        h.assertTrue(first.acceptedNow(),"No acceptance"); var repeat=offer(h,c,id,1);
        h.assertTrue(!repeat.acceptedNow() && repeat.receipt().orderId()==first.receipt().orderId() && c.orders().size()==1,"Replay transferred twice");
        var changed=offer(h,c,id,2);
        h.assertTrue(!changed.acceptedNow() && changed.receipt().state()==State.REJECTED,"Changed offer accepted");
        var changedWorker=AndroidEscrows.submit(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.OAK_PLANKS),4,
                List.of(new ItemStack(Items.OAK_LOG)),worker(h,c,first.receipt().orderId()).getUUID());
        h.assertTrue(!changedWorker.acceptedNow() && changedWorker.receipt().state()==State.REJECTED,"Changed worker selector accepted");
        h.assertTrue(count(AndroidEscrows.snapshot(h.getLevel(),c.owner(),id).materials(),Items.OAK_LOG)==1,"Replay changed custody"); h.succeed();
    }
    @GameTest(template="empty")
    public static void twoEqualOutputsHaveSeparateWorkersAndCustody(GameTestHelper h) {
        var c=setup(h,false); var extra=SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        extra.makeGuard(c.owner(),h.absolutePos(new BlockPos(2,1,4)),RuinAndroid.MODE_GUARD,30);
        extra.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,3)))); extra.setNoAi(true); h.getLevel().addFreshEntity(extra);
        var one=UUID.randomUUID();var two=UUID.randomUUID();var a=offer(h,c,one,1);var b=offer(h,c,two,2);
        h.assertTrue(a.acceptedNow() && b.acceptedNow(),"Second independent order rejected");
        h.assertTrue(!worker(h,c,a.receipt().orderId()).getUUID().equals(worker(h,c,b.receipt().orderId()).getUUID()),"Orders shared worker");
        h.assertTrue(count(AndroidEscrows.snapshot(h.getLevel(),c.owner(),one).materials(),Items.OAK_LOG)==1
                && count(AndroidEscrows.snapshot(h.getLevel(),c.owner(),two).materials(),Items.OAK_LOG)==2,"Orders shared custody"); h.succeed();
    }
    @GameTest(template="empty")
    public static void cancelledFetchedCargoReturnsOnlyActualItemsAndStaleLeaseCannotFetch(GameTestHelper h) {
        var c=setup(h,false);var id=UUID.randomUUID();var accepted=offer(h,c,id,1);h.assertTrue(accepted.acceptedNow(),"No acceptance");
        var a=worker(h,c,accepted.receipt().orderId());var job=c.requestJob(a);var route=c.planPickup(job,a.blockPosition());
        h.assertTrue(route!=null && !route.isEmpty() && c.fetchAt(a,job,route.getFirst()),"Private fetch failed");
        h.assertTrue(count(AndroidEscrows.snapshot(h.getLevel(),c.owner(),id).materials(),Items.OAK_LOG)==0,"Extraction left duplicate escrow stock");
        AndroidEscrows.cancel(h.getLevel(),c.owner(),id);
        h.assertTrue(a.recoverEscrowCargo(),"Actual cargo recovery failed");
        var recovered=AndroidEscrows.snapshot(h.getLevel(),c.owner(),id);
        h.assertTrue(!recovered.workerPending() && count(recovered.materials(),Items.OAK_LOG)==1 && a.carryEmpty(),"Cancellation duplicated or lost materials");
        h.assertTrue(!c.fetchAt(a,job,route.getFirst()),"Stale lease extracted again");
        h.assertTrue(AndroidEscrows.claim(h.getLevel(),c.owner(),id,false,0,64,false).getCount()==1,"Refund missing");
        h.assertTrue(AndroidEscrows.claim(h.getLevel(),c.owner(),id,false,0,64,false).isEmpty(),"Refund duplicated"); h.succeed();
    }
    @GameTest(template="empty")
    public static void consoleReplacementDoesNotOwnOldEscrow(GameTestHelper h) {
        var c=setup(h,false);var owner=c.owner();var id=UUID.randomUUID();var accepted=offer(h,c,id,1);h.assertTrue(accepted.acceptedNow(),"No acceptance");
        var a=worker(h,c,accepted.receipt().orderId());var job=c.requestJob(a);var route=c.planPickup(job,a.blockPosition());
        h.assertTrue(c.fetchAt(a,job,route.getFirst()),"No material fetch");
        h.setBlock(CONSOLE,net.minecraft.world.level.block.Blocks.AIR);h.setBlock(CONSOLE,SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        WorkConsoleBlockEntity replacement=h.getBlockEntity(CONSOLE); replacement.setOwner(owner);
        h.assertTrue(!replacement.escrowConsoleIdentity().equals(c.escrowConsoleIdentity()),"Replacement inherited identity");
        h.assertTrue(a.recoverEscrowCargo(),"Removed console stranded cargo");
        h.assertTrue(count(AndroidEscrows.snapshot(h.getLevel(),owner,id).materials(),Items.OAK_LOG)==1 && replacement.orders().isEmpty(),"Replacement stole old custody"); h.succeed();
    }
    @GameTest(template="empty")
    public static void consoleAndWorkerBindingSurviveSaveRestore(GameTestHelper h) {
        var c=setup(h,false);var id=UUID.randomUUID();var accepted=offer(h,c,id,1);h.assertTrue(accepted.acceptedNow(),"No acceptance");
        var a=worker(h,c,accepted.receipt().orderId());var identity=c.escrowConsoleIdentity();
        var consoleTag=c.saveWithoutMetadata(h.getLevel().registryAccess());var workTag=a.saveWorkState();
        c.loadWithComponents(consoleTag,h.getLevel().registryAccess());a.restoreWorkState(workTag);
        h.assertTrue(identity.equals(c.escrowConsoleIdentity()) && id.equals(a.escrowRequest()) && c.owner().equals(a.escrowOwner()),"Escrow binding lost");
        h.assertTrue(!offer(h,c,id,1).acceptedNow() && c.orders().size()==1,"Restore allowed duplicate transfer"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=2400)
    public static void actualWorkerCraftsPrivateProductsAndOwnerReclaimsExactlyOnce(GameTestHelper h) {
        var c=setup(h,true);var id=UUID.randomUUID();var accepted=offer(h,c,id,1);h.assertTrue(accepted.acceptedNow(),"No acceptance");
        h.succeedWhen(()->{
            var snap=AndroidEscrows.snapshot(h.getLevel(),c.owner(),id);
            h.assertTrue(snap.phase()==AndroidEscrows.Phase.COMPLETED && !snap.workerPending(),"Not completed with worker returned: "+snap.phase());
            h.assertTrue(count(snap.products(),Items.OAK_PLANKS)==4 && c.outbox().isEmpty(),"Output escaped private custody");
            h.assertTrue(AndroidEscrows.claim(h.getLevel(),UUID.randomUUID(),id,true,0,64,false).isEmpty(),"Foreign actor claimed output");
            h.assertTrue(AndroidEscrows.claim(h.getLevel(),c.owner(),id,true,0,64,true).getCount()==4,"Simulation wrong");
            h.assertTrue(AndroidEscrows.claim(h.getLevel(),c.owner(),id,true,0,64,false).getCount()==4,"Claim wrong");
            h.assertTrue(AndroidEscrows.claim(h.getLevel(),c.owner(),id,true,0,64,false).isEmpty(),"Claim duplicated");
            h.assertTrue(AndroidEscrows.acknowledge(h.getLevel(),c.owner(),id),"Empty terminal escrow not acknowledged");
        });
    }
    /** Logs -> planks -> sticks in one private request: intermediates stay in that request's custody, never shared stock. */
    @GameTest(template="empty",timeoutTicks=2400)
    public static void actualWorkerCraftsPrivateChainInOneRequest(GameTestHelper h) {
        var c=setup(h,true);var id=UUID.randomUUID();
        net.minecraft.world.level.block.entity.ChestBlockEntity shared=h.getBlockEntity(CHEST);
        var accepted=AndroidEscrows.submit(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.STICK),8,
                List.of(new ItemStack(Items.OAK_LOG,1)),null);
        h.assertTrue(accepted.acceptedNow(),"Chain request rejected: "+accepted.receipt().reason());
        h.assertTrue(c.orders().size()==1 && c.orders().getFirst().steps.size()==2,"One order with two steps expected");
        h.succeedWhen(()->{
            var snap=AndroidEscrows.snapshot(h.getLevel(),c.owner(),id);
            h.assertTrue(snap.phase()==AndroidEscrows.Phase.COMPLETED && !snap.workerPending(),"Not completed: "+snap.phase());
            h.assertTrue(count(snap.products(),Items.STICK)==8,"Sticks: "+snap.products());
            h.assertTrue(snap.materials().stream().allMatch(ItemStack::isEmpty),
                    "The whole log was used through the chain: "+snap.materials());
            h.assertTrue(shared.getItem(0).getCount()==32 && c.outbox().isEmpty(),"Shared stock or outbox touched");
        });
    }
    @GameTest(template="empty")
    public static void workerEntitySaveRetainsRequestOwnerAndOriginalDimension(GameTestHelper h) {
        var c=setup(h,false);var id=UUID.randomUUID();var accepted=offer(h,c,id,1);h.assertTrue(accepted.acceptedNow(),"No acceptance");
        var a=worker(h,c,accepted.receipt().orderId());
        var work=a.saveWorkState(); var tag=a.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        h.assertTrue(id.equals(tag.getUUID("escrow_request")) && c.owner().equals(tag.getUUID("escrow_owner")),"Independent entity binding absent");
        h.assertTrue(h.getLevel().dimension().location().toString().equals(tag.getString("escrow_dimension"))
                && tag.getString("escrow_dimension").equals(work.getString("escrow_dimension")),"Original dimension not saved in both formats");
        var restored=SinguloEntities.BLADE_ANDROID.get().create(h.getLevel()); restored.load(tag);
        h.assertTrue(id.equals(restored.escrowRequest()) && c.owner().equals(restored.escrowOwner()) && restored.escrowLevel()==h.getLevel(),"Entity restore lost custody dimension");
        h.assertTrue(c.getBlockPos().equals(restored.workConsole()) && restored.workOrder()==accepted.receipt().orderId(),"Entity restore lost working assignment"); h.succeed();
    }
    @GameTest(template="empty")
    public static void workerDeathRecoversPrivateCargoWithoutDroppingIt(GameTestHelper h) {
        var c=setup(h,false);var id=UUID.randomUUID();var accepted=offer(h,c,id,1);h.assertTrue(accepted.acceptedNow(),"No acceptance");
        var a=worker(h,c,accepted.receipt().orderId());var job=c.requestJob(a);var route=c.planPickup(job,a.blockPosition());
        h.assertTrue(route!=null && c.fetchAt(a,job,route.getFirst()),"Private fetch failed");
        a.hurt(h.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(!a.isAlive(),"Worker did not die");
        var snap=AndroidEscrows.snapshot(h.getLevel(),c.owner(),id);
        h.assertTrue(!snap.workerPending() && count(snap.materials(),Items.OAK_LOG)==1,"Death failed to recover actual cargo");
        var dropped=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(c.getBlockPos()).inflate(16));
        h.assertTrue(dropped.stream().noneMatch(e->e.getItem().is(Items.OAK_LOG)),"Escrow log both recovered and dropped");
        h.assertTrue(AndroidEscrows.claim(h.getLevel(),c.owner(),id,false,0,64,false).getCount()==1
                && AndroidEscrows.claim(h.getLevel(),c.owner(),id,false,0,64,false).isEmpty(),"Death refund not exactly once"); h.succeed();
    }
    @GameTest(template="empty")
    public static void unknownOwnerDeathPreservesCargoOnlyInForensicQuarantine(GameTestHelper h) {
        var c=setup(h,false);var id=UUID.randomUUID();var accepted=offer(h,c,id,1);h.assertTrue(accepted.acceptedNow(),"No acceptance");
        var original=worker(h,c,accepted.receipt().orderId());var job=c.requestJob(original);var route=c.planPickup(job,original.blockPosition());
        h.assertTrue(route!=null && c.fetchAt(original,job,route.getFirst()),"Private fetch failed");
        var tag=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());tag.remove("escrow_owner");
        tag.getCompound("work_state").remove("escrow_owner");
        // Move, not copy, the actual cargo into a restored corrupt-owner entity.
        for(int slot:original.carrySlots()) original.inventory().setItem(slot,ItemStack.EMPTY);
        original.discard();
        var restored=SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());restored.load(tag);h.getLevel().addFreshEntity(restored);
        h.assertTrue(restored.escrowOwner()==null && id.equals(restored.escrowRequest()),"Corrupt owner fixture unexpectedly inferred owner");
        restored.hurt(h.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(!restored.isAlive(),"Worker did not die");
        var saved=AndroidEscrowData.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
        var salvage=saved.getList("recovery_salvage_v1",net.minecraft.nbt.Tag.TAG_COMPOUND);long recovered=0;
        for(var raw:salvage) {
            var entry=(net.minecraft.nbt.CompoundTag)raw;
            if(!entry.hasUUID("request") || !id.equals(entry.getUUID("request"))) continue;
            h.assertTrue(!entry.contains("owner"),"Forensic recovery invented owner");
            for(var stackTag:entry.getList("actual_cargo",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var stack=ItemStack.parseOptional(h.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)stackTag);
                if(stack.is(Items.OAK_LOG)) recovered+=stack.getCount();
            }
        }
        h.assertTrue(recovered==1,"Unknown-owner actual cargo not retained exactly once");
        h.assertTrue(AndroidEscrows.claim(h.getLevel(),c.owner(),id,false,0,64,false).isEmpty(),"Unknown-owner quarantine became a normal refund");
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(c.getBlockPos()).inflate(16))
                .stream().noneMatch(e->e.getItem().is(Items.OAK_LOG)),"Quarantined material also dropped");h.succeed();
    }
    /** Fault injection is confined to this test block entity; production has no testing hook. */
    private static final class FailingEscrowConsole extends WorkConsoleBlockEntity {
        private UUID armedRequest;
        private boolean injected;
        private FailingEscrowConsole(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { super(pos,state); }
        @Override public void setChanged() {
            if (!injected && armedRequest != null && getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
                var entry=AndroidEscrowData.get(server).find(armedRequest);
                if(entry!=null && entry.state==AndroidEscrowData.State.RUNNING) {
                    injected=true;
                    throw new IllegalStateException("Expected GameTest fault after escrow custody binding");
                }
            }
            super.setChanged();
        }
    }
    @GameTest(template="empty")
    public static void failureAfterCustodyBindingReturnsAcceptedRecoverableFailure(GameTestHelper h) {
        var original=setup(h,false);var owner=original.owner();var id=UUID.randomUUID();
        var saved=original.saveWithoutMetadata(h.getLevel().registryAccess());
        var failing=new FailingEscrowConsole(original.getBlockPos(),original.getBlockState());
        failing.loadWithComponents(saved,h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(failing);
        failing.armedRequest=id;
        var offered=new ItemStack(Items.OAK_LOG);
        var result=AndroidEscrows.submit(h.getLevel(),failing.getBlockPos(),owner,id,new ItemStack(Items.OAK_PLANKS),4,List.of(offered),null);
        h.assertTrue(failing.injected,"Fault was not reached after custody binding");
        h.assertTrue(result.acceptedNow() && result.receipt().state()==State.FAILED,"Bound offer returned ambiguous rejection");
        h.assertTrue(failing.orders().isEmpty(),"Partial order survived failure");
        var snap=AndroidEscrows.snapshot(h.getLevel(),owner,id);
        h.assertTrue(snap.phase()==AndroidEscrows.Phase.RECOVERABLE && !snap.workerPending()
                && count(snap.materials(),Items.OAK_LOG)==1 && snap.products().isEmpty(),"Failed custody not safely recoverable");
        var replay=offer(h,failing,id,1);
        h.assertTrue(!replay.acceptedNow() && replay.receipt().state()==State.FAILED,"Failed acceptance replay transferred again");
        h.assertTrue(AndroidEscrows.claim(h.getLevel(),owner,id,false,0,64,false).getCount()==1,"Failed start lost offered material");
        h.assertTrue(AndroidEscrows.claim(h.getLevel(),owner,id,false,0,64,false).isEmpty(),"Failed start refunded twice");
        h.assertTrue(AndroidEscrows.acknowledge(h.getLevel(),owner,id),"Recovered failure could not be acknowledged");
        h.succeed();
    }
}
