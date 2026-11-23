package io.github.genichimaruo.singulo.android.work;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoint;
import io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoints;
import io.github.genichimaruo.singulo.api.android.AndroidWorkshops;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraft.world.level.material.Fluids;
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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidEndpointApiGameTests {
    private static void registerEndpointFace() {
        var event = new io.github.genichimaruo.singulo.compat.RegisterCapabilitiesEvent();
        event.registerBlockEntity(AndroidWorkEndpoint.CAPABILITY, net.minecraft.world.level.block.entity.BlockEntityType.CHEST,
                (chest, face) -> face == Direction.UP && chest instanceof Endpoint endpoint ? endpoint : null);
    }
    private static final BlockPos CONSOLE=new BlockPos(0,1,0), SUPPLY=new BlockPos(1,1,0), CHEST=new BlockPos(1,1,1),
            DELIVERY=new BlockPos(3,1,0), RACK=new BlockPos(4,1,0), TABLE=new BlockPos(4,1,3);
    private static Item item(String name) { return BuiltInRegistries.ITEM.get(new ResourceLocation("singulo",name)); }
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
        net.minecraft.world.level.block.entity.ChestBlockEntity chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, CHEST);
        chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG, logs));
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
        ItemStack link = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(link, level, helper.absolutePos(CONSOLE),
                SinguloBlocks.ANDROID_WORK_CONSOLE.get());
        for (BlockPos p : List.of(SUPPLY, DELIVERY, RACK)) {
            io.github.genichimaruo.singulo.android.work.WorkLinkedBlockEntity b = (io.github.genichimaruo.singulo.android.work.WorkLinkedBlockEntity) io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, p);
            b.useItem(player, link, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        ItemStack table = new ItemStack(item("work_location_card"));
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(table, level, helper.absolutePos(TABLE),
                net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
        io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity rack = (io.github.genichimaruo.singulo.android.work.RecipeRackBlockEntity) io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, RACK);
        java.util.List<ItemStack> planks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        planks.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_LOG));
        java.util.List<ItemStack> sticks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        sticks.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        sticks.set(3, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
        rack.items().setStackInSlot(0, table);
        rack.items().setStackInSlot(1, recipeCard(helper, planks, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4)));
        rack.items().setStackInSlot(2, recipeCard(helper, sticks, new ItemStack(net.minecraft.world.item.Items.STICK, 4)));
        return (WorkConsoleBlockEntity) io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(helper, CONSOLE);
    }

    private static WorkConsoleBlockEntity setup(GameTestHelper h,boolean moving) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
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
        FluidTank inputTank, outputTank;
        Endpoint(BlockPos pos, UUID owner) { super(pos, Blocks.CHEST.defaultBlockState()); this.owner=owner; }
        public UUID endpointId() { return identity; }
        public UUID endpointOwner() { return owner; }
        public IItemHandler supplyItems() { return supply; }
        public IItemHandler deliveryItems() { return delivery; }
        public IFluidHandler supplyFluids() { return inputTank; }
        public IFluidHandler deliveryFluids() { return outputTank; }
    }
    private static Endpoint endpoint(GameTestHelper h, UUID owner) {
        h.setBlock(ENDPOINT, Blocks.CHEST);
        registerEndpointFace();
        var e=new Endpoint(h.absolutePos(ENDPOINT), owner);
        h.getLevel().setBlockEntity(e); return e;
    }
    private static AndroidWorkEndpoints.Result link(GameTestHelper h, WorkConsoleBlockEntity c) {
        return AndroidWorkEndpoints.link(h.getLevel(),c.getBlockPos(),c.owner(),h.absolutePos(ENDPOINT));
    }
    private static AndroidOrders.Receipt submit(GameTestHelper h, WorkConsoleBlockEntity c, UUID id) {
        return AndroidOrders.submit(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.STICK),8,List.of(),h.absolutePos(ENDPOINT));
    }

    @GameTest(template="empty", timeoutTicks=1800)
    public static void workshopOrderRunsAProcedureWithoutPatternInputs(GameTestHelper h) {
        var console = setup(h, true);
        var endpoint = endpoint(h, console.owner());
        link(h, console);
        var source = new BlockPos(5, 1, 3);
        h.setBlock(source, Blocks.BARREL);
        net.minecraft.world.level.block.entity.BarrelBlockEntity barrel = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, source);
        var target = new ItemStack(Items.AMETHYST_SHARD, 3);
        barrel.setItem(0, target.copy());
        var take = new ProcedureStep(ProcedureStep.EXTRACT, barrel.getBlockPos(), null, target).recordedIn(h.getLevel());
        var card = new ItemStack(item("recipe_card"));
        RecipeData.procedure(List.of(take), target).write(card, h.getLevel().registryAccess());
        RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, RACK);
        rack.items().setStackInSlot(3, card);
        var id = UUID.randomUUID();
        var receipt = AndroidOrders.submit(h.getLevel(), console.getBlockPos(), console.owner(), id, target, 3, List.of(), endpoint.getBlockPos());
        h.assertTrue(receipt.state() == State.RUNNING, "Zero-input procedure rejected: " + receipt.reason());
        h.succeedWhen(() -> {
            var status = AndroidOrders.status(h.getLevel(), console.getBlockPos(), console.owner(), id);
            h.assertTrue(status.state() == State.COMPLETED && status.delivered() == 3,
                    "Procedure did not deliver: " + status.state() + " (" + status.reason() + "), delivered=" + status.delivered());
            h.assertTrue(barrel.getItem(0).isEmpty() && ItemStack.matches(target, endpoint.delivery.getStackInSlot(0)),
                    "Delivered amount did not match the items taken by the procedure");
        });
    }

    @GameTest(template="empty",timeoutTicks=1800)
    public static void workshopOrderElectrolyzesEndpointWaterAndDeliversMeasuredGas(GameTestHelper h) {
        var console = setup(h, true);
        var level = h.getLevel();
        var endpoint = endpoint(h, console.owner());
        endpoint.inputTank = new FluidTank(1000);
        endpoint.outputTank = new FluidTank(125);
        endpoint.inputTank.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
        link(h, console);
        var worker = console.candidates(level.getServer().getPlayerList().getPlayer(console.owner())).get(0);
        worker.inventory().setItem(RuinAndroid.SLOT_TANK, new ItemStack(item("android_tank_upgrade")));
        var machinePos = new BlockPos(5, 1, 3);
        var block = SinguloBlocks.MACHINES.get(io.github.genichimaruo.singulo.machine.MachineType.ELECTROLYZER).get();
        h.setBlock(machinePos, block);
        io.github.genichimaruo.singulo.machine.MachineBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, machinePos);
        machine.energy().setEnergy(10000);
        var location = new ItemStack(item("work_location_card"));
        WorkLocationCardItem.record(location, level, machine.getBlockPos(), block);
        var water = FluidSamples.of(new FluidStack(Fluids.WATER, 200));
        var hydrogen = FluidSamples.of(new FluidStack(io.github.genichimaruo.singulo.registry.SinguloFluids.get("hydrogen"), 200));
        var card = new ItemStack(item("recipe_card"));
        RecipeData.processing(List.of(water), hydrogen, List.of(), ItemStack.EMPTY).write(card, level.registryAccess());
        RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, RACK);
        rack.items().setStackInSlot(RecipeRackBlockEntity.COLS, location);
        rack.items().setStackInSlot(RecipeRackBlockEntity.COLS + 1, card);
        var id = UUID.randomUUID();
        var plan = AndroidWorkshops.preview(level, console.getBlockPos(), console.owner(), hydrogen, 200, List.of(worker.getUUID()));
        h.assertTrue(plan.state() == AndroidWorkshops.PlanState.MATERIALS_READY && plan.fromStock().size() == 1
                && plan.fromStock().get(0).amount() == 200 && FluidSamples.amount(plan.fromStock().get(0).sample()) == 1
                && endpoint.inputTank.getFluidAmount() == 250, "Preview did not preserve mB units or changed the input tank");
        var receipt = AndroidOrders.submit(level, console.getBlockPos(), console.owner(), id, hydrogen, 200, List.of(worker.getUUID()), endpoint.getBlockPos());
        h.assertTrue(receipt.state() == State.RUNNING, "Fluid processing order rejected: " + receipt.reason());
        boolean[] partial = {false};
        h.succeedWhen(() -> {
            var status = AndroidOrders.status(level, console.getBlockPos(), console.owner(), id);
            if (!partial[0] && endpoint.outputTank.getFluidAmount() == 125) {
                h.assertTrue(status.state() == State.RUNNING && status.delivered() == 125, "Partial fluid delivery completed early");
                endpoint.outputTank.drain(125, IFluidHandler.FluidAction.EXECUTE);
                partial[0] = true;
            }
            h.assertTrue(status.state() == State.COMPLETED, "Order pending: " + worker.stall() + ", received " + status.delivered());
            h.assertTrue(partial[0] && status.delivered() == 200 && endpoint.inputTank.getFluidAmount() == 50
                    && endpoint.outputTank.getFluidAmount() == 75
                    && io.github.genichimaruo.singulo.compat.Legacy.sameFluid(endpoint.outputTank.getFluid(), FluidSamples.fluid(hydrogen)),
                    "Fluid type or mB balance changed");
            worker.discard();
        });
    }

    @GameTest(template="empty")
    public static void addonWorkshopDiscoveryAndPreviewRespectOwnershipAndReservations(GameTestHelper h) {
        var c = setup(h, false);
        var level = h.getLevel();
        var pos = c.getBlockPos();
        var port = h.absolutePos(SUPPLY);
        var owner = c.owner();
        var other = UUID.randomUUID();
        var card = new ItemStack(item("work_location_card"));
        h.assertTrue(AndroidWorkshops.isLocationCard(card) && AndroidWorkshops.readLocationCard(card).isEmpty(), "Blank card has a target");
        WorkLocationCardItem.record(card, level, port, SinguloBlocks.ANDROID_SUPPLY_PORT.get());
        var globalPos = AndroidWorkshops.readLocationCard(card).orElseThrow();
        h.assertTrue(globalPos.pos().equals(port) && globalPos.dimension().equals(level.dimension()), "Card lost its dimension or position");
        h.assertTrue(AndroidWorkshops.resolve(level, port, owner).orElseThrow().equals(new AndroidWorkshops.Connection(pos, AndroidWorkshops.Role.SUPPLY))
                && AndroidWorkshops.resolve(level, pos, owner).orElseThrow().role() == AndroidWorkshops.Role.CONSOLE
                && AndroidWorkshops.resolve(level, port, other).isEmpty(), "Incorrect workshop ownership or port role");
        var sticks = new ItemStack(Items.STICK);
        var plan = AndroidWorkshops.preview(level, pos, owner, sticks, 8, List.of());
        h.assertTrue(plan.state() == AndroidWorkshops.PlanState.MATERIALS_READY && plan.steps().size() == 2
                && plan.fromStock().size() == 1 && plan.fromStock().get(0).amount() == 1
                && c.orders().isEmpty() && AndroidOrders.snapshot(level, pos, owner).receipts().isEmpty(), "Preview reserved materials or created an order");
        var logs = AndroidWorkshops.stock(level, pos, owner).available().get(0);
        logs.sample().shrink(1);
        h.assertTrue(logs.sample().is(Items.OAK_LOG) && logs.amount() == 32, "Mutable stock snapshot");
        h.assertTrue(AndroidOrders.submit(level, pos, owner, UUID.randomUUID(), sticks, 8, List.of(), null).state() == State.RUNNING
                && AndroidWorkshops.stock(level, pos, owner).available().get(0).amount() == 31, "Reserved logs still available");
        var missing = AndroidWorkshops.preview(level, pos, owner, new ItemStack(Items.DIAMOND), 5, List.of());
        h.assertTrue(missing.state() == AndroidWorkshops.PlanState.MISSING_MATERIALS && missing.missing().get(0).amount() == 5
                && AndroidWorkshops.preview(level, pos, owner, sticks, 0, List.of()).state() == AndroidWorkshops.PlanState.INVALID_REQUEST,
                "Missing or invalid request reported as ready");
        h.assertTrue(AndroidWorkshops.preview(level, pos, other, sticks, 8, List.of()).state() == AndroidWorkshops.PlanState.REJECTED
                && AndroidWorkshops.stock(level, pos, other).available().isEmpty(), "Foreign caller learned workshop stock");
        c.removeLink(port);
        h.assertTrue(AndroidWorkshops.resolve(level, port, owner).isEmpty(), "One-sided link resolved");
        var unloaded = pos.offset(40000, 0, 40000);
        h.assertTrue(!level.isLoaded(unloaded) && AndroidWorkshops.resolve(level, unloaded, owner).isEmpty()
                && AndroidWorkshops.stock(level, unloaded, owner).state() == AndroidOrders.SnapshotState.UNAVAILABLE
                && !level.isLoaded(unloaded), "Discovery loaded a distant chunk");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void fluidNetworkEndpointsShareStockAndRejectReplacements(GameTestHelper h) {
        var console = setup(h, false);
        class NetworkTank extends FluidTank implements io.github.genichimaruo.singulo.api.android.AndroidSharedStorage {
            NetworkTank() { super(1000); setFluid(new FluidStack(Fluids.WATER, 700)); }
            public long sharedStorageKey() { return 32L; }
        }
        var first = endpoint(h, console.owner());
        first.inputTank = new NetworkTank();
        link(h, console);
        var pos = ENDPOINT.south();
        h.setBlock(pos, Blocks.CHEST);
        var second = new Endpoint(h.absolutePos(pos), console.owner());
        h.getLevel().setBlockEntity(second);
        second.inputTank = new NetworkTank();
        AndroidWorkEndpoints.link(h.getLevel(), console.getBlockPos(), console.owner(), second.getBlockPos());
        var water = FluidSamples.of(new FluidStack(Fluids.WATER, 1));
        h.assertTrue(ItemCount.get(console.stock(), water) == 700, "Two views of one ME fluid network doubled its stock");
        first.owner = UUID.randomUUID();
        h.assertTrue(ItemCount.get(console.stock(), water) == 700, "A revoked link hid the remaining authorized view");
        var replacement = new Endpoint(second.getBlockPos(), console.owner());
        replacement.inputTank = new NetworkTank();
        h.getLevel().setBlockEntity(replacement);
        h.assertTrue(ItemCount.get(console.stock(), water) == 0, "A replacement endpoint inherited access to the fluid supply");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void endpointLinksRequireOwnerRangeAndLoadedConsole(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner());
        h.assertTrue(AndroidWorkEndpoints.link(h.getLevel(), c.getBlockPos(), c.owner(), e.getBlockPos(), Direction.DOWN)
                == AndroidWorkEndpoints.Result.REJECTED, "A different multipart face granted access");
        h.assertTrue(AndroidWorkEndpoints.link(h.getLevel(), c.getBlockPos(), c.owner(), e.getBlockPos(), Direction.UP)
                == AndroidWorkEndpoints.Result.LINKED, "Face-specific capability was not linked");
        var saved = c.saveWithoutMetadata();
        h.assertTrue(saved.getList("work_endpoints_v1", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getString("face").equals("up"),
                "Missing endpoint face in saved link");
        c.load(saved);
        h.assertTrue(c.workEndpoint(e.getBlockPos()) == e, "Sided endpoint did not survive reload");
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
        var tag=c.saveWithoutMetadata(); c.load(tag);
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
        var tag=c.saveWithoutMetadata(); c.load(tag);
        h.assertTrue(e.identity.equals(c.orders().get(0).deliveryEndpointId),"Destination identity lost on reload");
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
        var c=setup(h,true); ChestBlockEntity shared=(ChestBlockEntity) io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, CHEST); shared.clearContent();
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
        h.assertTrue(c.orders().get(0).held.stream().allMatch(v->v.point.equals(e.getBlockPos())),"Reservation escaped source"); h.succeed();
    }

    @GameTest(template="empty")
    public static void itemOnlySourceDoesNotUseSharedLava(GameTestHelper h) {
        var console = setup(h, false);
        var endpoint = endpoint(h, console.owner());
        link(h, console);
        var grid = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
        grid.set(0, new ItemStack(Items.LAVA_BUCKET));
        grid.set(1, new ItemStack(Items.OBSIDIAN));
        var target = new ItemStack(Items.CRYING_OBSIDIAN);
        RecipeRackBlockEntity rack = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, RACK);
        rack.items().setStackInSlot(3, recipeCard(h, grid, target));
        h.setBlock(SUPPLY.east(), SinguloBlocks.FLUID_DRONE_PORT.get());
        io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity tank = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, SUPPLY.east());
        tank.receiveTank().setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000));
        endpoint.supply.setStackInSlot(0, new ItemStack(Items.OBSIDIAN));
        var denied = AndroidOrders.submitFromEndpoint(h.getLevel(), console.getBlockPos(), console.owner(), UUID.randomUUID(),
                target, 1, List.of(), null, endpoint.getBlockPos());
        h.assertTrue(denied.state() == State.REJECTED && console.orders().isEmpty(), "Restricted source used shared lava");
        endpoint.supply.setStackInSlot(1, new ItemStack(Items.LAVA_BUCKET));
        var accepted = AndroidOrders.submitFromEndpoint(h.getLevel(), console.getBlockPos(), console.owner(), UUID.randomUUID(),
                target, 1, List.of(), null, endpoint.getBlockPos());
        h.assertTrue(accepted.state() == State.RUNNING && console.orders().get(0).steps.get(0).recipe.tankBuckets() == 0
                && tank.receiveTank().getFluidAmount() == 1000, "Restricted source did not reserve its physical bucket");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void restrictedSourcePersistsAndIsPartOfRequestFingerprint(GameTestHelper h) {
        var c=setup(h,false); var e=endpoint(h,c.owner()); e.supply.setStackInSlot(0,new ItemStack(Items.OAK_LOG,8)); link(h,c);
        var id=UUID.randomUUID();
        var first=AndroidOrders.submitFromEndpoint(h.getLevel(),c.getBlockPos(),c.owner(),id,new ItemStack(Items.OAK_PLANKS),4,List.of(),null,e.getBlockPos());
        h.assertTrue(first.state()==State.RUNNING,"Restricted order rejected");
        var tag=c.saveWithoutMetadata(); c.load(tag);
        var o=c.orders().get(0);
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
        var registries=h.getLevel().registryAccess(); var saved=c.orders().get(0).save(registries);
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
        h.assertTrue(first.state()==State.RUNNING,"Fixture rejected"); var o=c.orders().get(0);
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
