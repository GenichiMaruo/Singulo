package io.github.genichimaruo.singulo.android.work;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoint;
import io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoints;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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
public final class AndroidEndpointApiGameTests {
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
    private static final BlockPos ENDPOINT = new BlockPos(6,1,0);
    private static final class Endpoint extends ChestBlockEntity implements AndroidWorkEndpoint {
        final UUID identity = UUID.randomUUID();
        UUID owner;
        boolean extractOnly;
        final ItemStackHandler supply = new ItemStackHandler(2) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return extractOnly ? stack : super.insertItem(slot,stack,simulate);
            }
        };
        final ItemStackHandler delivery = new ItemStackHandler(1);
        Endpoint(BlockPos pos, UUID owner) { super(pos, Blocks.CHEST.defaultBlockState()); this.owner=owner; }
        public UUID endpointId() { return identity; }
        public UUID endpointOwner() { return owner; }
        public IItemHandler supplyItems() { return supply; }
        public IItemHandler deliveryItems() { return delivery; }
    }
    private static Endpoint endpoint(GameTestHelper h, UUID owner) {
        h.setBlock(ENDPOINT, Blocks.CHEST);
        var e=new Endpoint(h.absolutePos(ENDPOINT), owner);
        h.getLevel().setBlockEntity(e); return e;
    }
    private static AndroidWorkEndpoints.Result link(GameTestHelper h, WorkConsoleBlockEntity c) {
        return AndroidWorkEndpoints.link(h.getLevel(),c.getBlockPos(),c.owner(),h.absolutePos(ENDPOINT));
    }
    private static AndroidOrders.Receipt submit(GameTestHelper h, WorkConsoleBlockEntity c, UUID id) {
        return AndroidOrders.submit(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.STICK),8,List.of(),h.absolutePos(ENDPOINT));
    }
    @GameTest(template="empty")
    public static void endpointLinksRequireOwnerRangeAndLoadedConsole(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner());
        h.assertTrue(AndroidWorkEndpoints.link(h.getLevel(),c.getBlockPos(),UUID.randomUUID(),e.getBlockPos())==AndroidWorkEndpoints.Result.REJECTED,"Foreign actor linked");
        e.owner=UUID.randomUUID();
        h.assertTrue(link(h,c)==AndroidWorkEndpoints.Result.REJECTED,"Foreign endpoint linked");
        e.owner=c.owner();
        h.assertTrue(link(h,c)==AndroidWorkEndpoints.Result.LINKED,"Valid link rejected");
        h.assertTrue(AndroidWorkEndpoints.link(h.getLevel(),c.getBlockPos(),c.owner(),c.getBlockPos().offset(64,0,0))==AndroidWorkEndpoints.Result.REJECTED,"Out of range accepted");
        h.assertTrue(AndroidWorkEndpoints.link(h.getLevel(),c.getBlockPos().above(),c.owner(),e.getBlockPos())==AndroidWorkEndpoints.Result.UNAVAILABLE,"Missing console accepted");
        h.assertTrue(AndroidWorkEndpoints.unlink(h.getLevel(),c.getBlockPos(),UUID.randomUUID(),e.getBlockPos())==AndroidWorkEndpoints.Result.REJECTED,"Foreign unlink accepted");
        h.assertTrue(AndroidWorkEndpoints.unlink(h.getLevel(),c.getBlockPos(),c.owner(),e.getBlockPos())==AndroidWorkEndpoints.Result.UNLINKED && c.workEndpoint(e.getBlockPos())==null,"Unlink failed"); h.succeed();
    }
    @GameTest(template="empty")
    public static void linkPersistsAndReplacementCannotInheritSupply(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner()); e.supply.setStackInSlot(0,new ItemStack(Items.DIAMOND,5));
        h.assertTrue(link(h,c)==AndroidWorkEndpoints.Result.LINKED,"Link rejected");
        var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); c.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(c.workEndpoint(e.getBlockPos())==e && c.storages().contains(e.supply),"Link did not persist");
        var replacement=endpoint(h,c.owner()); replacement.supply.setStackInSlot(0,new ItemStack(Items.DIAMOND,7));
        h.assertTrue(c.workEndpoint(replacement.getBlockPos())==null && !c.storages().contains(replacement.supply),"Replacement inherited access");
        h.assertTrue(c.storagesAt(replacement.getBlockPos(),new java.util.HashSet<>()).isEmpty(),"Invalid endpoint fell back to adjacent inventory"); h.succeed();
    }
    @GameTest(template="empty")
    public static void endpointOrderPinsIdentityAndReplaysWithoutDuplication(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner()); link(h,c); var id=UUID.randomUUID(); var receipt=submit(h,c,id);
        h.assertTrue(receipt.state()==State.RUNNING,"Endpoint order rejected: "+receipt.reason());
        h.assertTrue(submit(h,c,id).orderId()==receipt.orderId() && c.orders().size()==1,"Replay duplicated order");
        h.assertTrue(AndroidWorkEndpoints.unlink(h.getLevel(),c.getBlockPos(),c.owner(),e.getBlockPos())==AndroidWorkEndpoints.Result.BUSY,"Active endpoint unlinked");
        var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); c.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(e.identity.equals(c.orders().getFirst().deliveryEndpointId),"Destination identity lost on reload");
        endpoint(h,c.owner());
        h.assertTrue(c.workEndpoint(e.getBlockPos())==null,"Replacement inherited active destination");
        h.assertTrue(submit(h,c,UUID.randomUUID()).state()==State.REJECTED,"New order accepted replaced endpoint");
        h.assertTrue(submit(h,c,id).orderId()==receipt.orderId() && c.orders().size()==1,"Replay restarted missing endpoint work"); h.succeed();
    }
    @GameTest(template="empty")
    public static void endpointApiRejectsOffThreadCalls(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner());
        boolean rejected=java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try { AndroidWorkEndpoints.link(h.getLevel(),c.getBlockPos(),c.owner(),e.getBlockPos()); return false; }
            catch(IllegalStateException expected) { return true; }
        }).join();
        h.assertTrue(rejected,"Off-thread endpoint call accepted"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=2400)
    public static void actualEndpointCraftWaitsForSpaceAndDeliversExactlyOnce(GameTestHelper h) {
        var c=setup(h,true); ChestBlockEntity shared=h.getBlockEntity(CHEST); shared.clearContent();
        var e=endpoint(h,c.owner()); e.supply.setStackInSlot(0,new ItemStack(Items.OAK_LOG,32));
        e.delivery.setStackInSlot(0,new ItemStack(Items.STICK,60)); link(h,c);
        var id=UUID.randomUUID(); var first=submit(h,c,id);
        h.assertTrue(first.state()==State.RUNNING,"Remote supply/delivery order rejected: "+first.reason());
        boolean[] partial={false};
        h.succeedWhen(()->{
            var receipt=AndroidOrders.status(h.getLevel(),c.getBlockPos(),c.owner(),id);
            if(!partial[0] && e.delivery.getStackInSlot(0).getCount()==64) {
                h.assertTrue(receipt.state()==State.RUNNING && receipt.delivered()==4,"Partial delivery counted as complete");
                e.delivery.extractItem(0,64,false); partial[0]=true;
            }
            h.assertTrue(partial[0] && receipt.state()==State.COMPLETED,"Not completed after partial delivery");
            h.assertTrue(receipt.delivered()==8 && e.delivery.getStackInSlot(0).getCount()==4,"Wrong destination output amount");
            h.assertTrue(submit(h,c,id).state()==State.COMPLETED && c.orders().isEmpty(),"Replay restarted completed delivery");
        });
    }
    @GameTest(template="empty")
    public static void restrictedSourceNeverUsesSharedStockAndRejectsMultistage(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner()); link(h,c);
        var denied=AndroidOrders.submitFromEndpoint(h.getLevel(),c.getBlockPos(),c.owner(),UUID.randomUUID(),new ItemStack(Items.OAK_PLANKS),4,List.of(),null,e.getBlockPos());
        h.assertTrue(denied.state()==State.REJECTED && c.orders().isEmpty(),"Shared logs satisfied empty restricted source");
        e.supply.setStackInSlot(0,new ItemStack(Items.OAK_LOG,8));
        var multistage=AndroidOrders.submitFromEndpoint(h.getLevel(),c.getBlockPos(),c.owner(),UUID.randomUUID(),new ItemStack(Items.STICK),4,List.of(),null,e.getBlockPos());
        h.assertTrue(multistage.state()==State.REJECTED && c.orders().isEmpty(),"Unsupported multistage accepted");
        var accepted=AndroidOrders.submitFromEndpoint(h.getLevel(),c.getBlockPos(),c.owner(),UUID.randomUUID(),new ItemStack(Items.OAK_PLANKS),4,List.of(),null,e.getBlockPos());
        h.assertTrue(accepted.state()==State.RUNNING,"Single crafting rejected: "+accepted.reason());
        h.assertTrue(c.orders().getFirst().held.stream().allMatch(v->v.point.equals(e.getBlockPos())),"Reservation escaped source"); h.succeed();
    }
    @GameTest(template="empty")
    public static void restrictedSourcePersistsAndIsPartOfRequestFingerprint(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner()); e.supply.setStackInSlot(0,new ItemStack(Items.OAK_LOG,8)); link(h,c);
        var id=UUID.randomUUID();
        var first=AndroidOrders.submitFromEndpoint(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.OAK_PLANKS),4,List.of(),null,e.getBlockPos());
        h.assertTrue(first.state()==State.RUNNING,"Restricted order rejected");
        var tag=c.saveWithoutMetadata(h.getLevel().registryAccess()); c.loadWithComponents(tag,h.getLevel().registryAccess());
        var o=c.orders().getFirst();
        h.assertTrue(e.getBlockPos().equals(o.materialSource) && e.identity.equals(o.materialEndpointId),"Source binding lost on reload");
        h.assertTrue(AndroidOrders.submit(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.OAK_PLANKS),4,List.of(),null).state()==State.REJECTED,"Unrestricted replay accepted");
        h.assertTrue(AndroidOrders.submitFromEndpoint(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.OAK_PLANKS),4,List.of(),null,e.getBlockPos().above()).state()==State.REJECTED,"Different source replay accepted");
        h.assertTrue(AndroidOrders.submitFromEndpoint(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.OAK_PLANKS),4,List.of(),null,e.getBlockPos()).orderId()==first.orderId() && c.orders().size()==1,"Same source replay duplicated");
        var replacement=endpoint(h,c.owner()); replacement.supply.setStackInSlot(0,new ItemStack(Items.OAK_LOG,8));
        var worker=h.getLevel().getEntitiesOfClass(RuinAndroid.class,new net.minecraft.world.phys.AABB(c.getBlockPos()).inflate(16)).stream()
                .filter(a->o.workers.contains(a.getUUID())).findFirst().orElseThrow();
        h.assertTrue(!c.fetchAt(worker,WorkJob.simple(WorkJob.Type.CRAFT,o.id),new WorkConsoleBlockEntity.Pickup(e.getBlockPos(),List.of(new ItemCount(new ItemStack(Items.OAK_LOG),1)))),"Replaced source was fetched");
        h.assertTrue(replacement.supply.getStackInSlot(0).getCount()==8 && worker.carryEmpty(),"Failed source check moved materials");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void malformedSourceAndDestinationNeverBecomeUnrestricted(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner()); e.supply.setStackInSlot(0,new ItemStack(Items.OAK_LOG,8)); link(h,c);
        var id=UUID.randomUUID();
        var first=AndroidOrders.submitFromEndpoint(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.OAK_PLANKS),4,List.of(),e.getBlockPos(),e.getBlockPos());
        h.assertTrue(first.state()==State.RUNNING,"Fixture rejected");
        var registries=h.getLevel().registryAccess(); var saved=c.orders().getFirst().save(registries);
        h.assertTrue(WorkOrder.load(saved,registries)!=null,"Valid order did not load");
        for(String key:List.of("material_source","material_endpoint_id","delivery_endpoint_id","destination")) {
            var malformed=saved.copy(); malformed.putString(key,"invalid");
            h.assertTrue(WorkOrder.load(malformed,registries)==null,"Malformed "+key+" silently accepted");
        }
        for(String key:List.of("material_source","material_endpoint_id","destination")) {
            var missing=saved.copy(); missing.remove(key);
            h.assertTrue(WorkOrder.load(missing,registries)==null,"Missing "+key+" silently accepted");
        }
        var receipt=new ExternalOrderLedger(id,c.owner(),first.orderId(),new ItemStack(Items.OAK_PLANKS),4,List.of(),e.getBlockPos());
        receipt.materialSource=e.getBlockPos(); var receiptTag=receipt.save(registries);
        h.assertTrue(ExternalOrderLedger.load(receiptTag,registries)!=null,"Valid receipt did not load");
        receiptTag.putString("material_source","invalid");
        h.assertTrue(ExternalOrderLedger.load(receiptTag,registries)==null,"Malformed receipt source became unrestricted"); h.succeed();
    }
    @GameTest(template="empty")
    public static void fullCarryDoesNotExtractFromNonReturnableEndpoint(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner()); e.supply.setStackInSlot(0,new ItemStack(Items.OAK_LOG,8)); e.extractOnly=true; link(h,c);
        var first=AndroidOrders.submitFromEndpoint(h.getLevel(),c.getBlockPos(),c.owner(),UUID.randomUUID(),new ItemStack(Items.OAK_PLANKS),4,List.of(),null,e.getBlockPos());
        h.assertTrue(first.state()==State.RUNNING,"Fixture rejected"); var o=c.orders().getFirst();
        var worker=h.getLevel().getEntitiesOfClass(RuinAndroid.class,new net.minecraft.world.phys.AABB(c.getBlockPos()).inflate(16)).stream()
                .filter(a->o.workers.contains(a.getUUID())).findFirst().orElseThrow();
        for(int slot:worker.carrySlots()) worker.inventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        var held=o.held(e.getBlockPos(),new ItemStack(Items.OAK_LOG));
        h.assertTrue(!c.fetchAt(worker,WorkJob.simple(WorkJob.Type.CRAFT,o.id),new WorkConsoleBlockEntity.Pickup(e.getBlockPos(),List.of(new ItemCount(new ItemStack(Items.OAK_LOG),1)))),"Full carry accepted extraction");
        h.assertTrue(e.supply.getStackInSlot(0).getCount()==8 && o.held(e.getBlockPos(),new ItemStack(Items.OAK_LOG))==held,"Failed fetch consumed material or reservation");
        for(int slot:worker.carrySlots()) h.assertTrue(worker.inventory().getItem(slot).is(Items.COBBLESTONE) && worker.inventory().getItem(slot).getCount()==64,"Failed fetch changed worker inventory");
        h.succeed();
    }
}