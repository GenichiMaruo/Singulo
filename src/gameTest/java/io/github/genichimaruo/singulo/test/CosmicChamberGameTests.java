package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity;
import io.github.genichimaruo.singulo.cosmic.CosmicLayouts;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class CosmicChamberGameTests {
    private static final BlockPos C=new BlockPos(15,1,16);
    private static CosmicChamberBlockEntity build(GameTestHelper h,Blueprints.Kind kind){
        TestBuild.build(h,kind,C,Direction.NORTH,7);
        CosmicChamberBlockEntity be=h.getBlockEntity(C);
        h.assertTrue(be.ready(),"Authored blueprint must form");return be;
    }
    private static ItemStack item(String id,int count){return new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)),count);}
    private static void stored(GameTestHelper h,CosmicChamberBlockEntity be,long amount,boolean core){
        var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());tag.putLong("stored",amount);tag.putBoolean("core",core);
        be.loadWithComponents(tag,h.getLevel().registryAccess());
    }
    @GameTest(template="empty")
    public static void cosmicClientMenuPredictsSpindleSlotsBeforeBlockEntityArrives(GameTestHelper h){
        var player=io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            buf.writeBlockPos(BlockPos.ZERO);buf.writeBoolean(true);
            var menu=io.github.genichimaruo.singulo.cosmic.CosmicMenu.client(1,player.getInventory(),buf);
            h.assertTrue(menu.slots.size()==40,"Menu layout depends on a client block-entity packet arriving first");
            h.assertTrue(menu.getSlot(2).getMaxStackSize()==1&&menu.getSlot(2).mayPlace(item("singularity_seed",1)),"Client core slot differs from server");
            h.assertTrue(menu.getSlot(0).mayPlace(item("collapsar_billet",1))&&!menu.getSlot(0).mayPlace(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),"Client predicts invalid ingredient placement");
            h.assertFalse(menu.getSlot(3).mayPlace(item("tidal_filament",1)),"Client accepts output insertion");h.succeed();
        }finally{buf.release();}
    }
    @GameTest(template="forge")
    public static void cosmicControllerDropPacksLongEnergyExactlyOnce(GameTestHelper h){
        var be=build(h,Blueprints.Kind.PASTA_ACCUMULATOR);stored(h,be,7_123_456_789_012L,false);
        var drops=net.minecraft.world.level.block.Block.getDrops(be.getBlockState(),h.getLevel(),be.getBlockPos(),be,null,new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE));
        h.assertTrue(drops.size()==1&&drops.get(0).getCount()==1&&drops.get(0).getMaxStackSize()==1,"Controller not a single packed item");
        var data=drops.get(0).get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        h.assertTrue(data!=null&&data.copyTag().getLong("stored")==7_123_456_789_012L,"Drop truncated stored energy");
        h.succeed();
    }
    @GameTest(template="forge")
    public static void cosmicWrenchClosesOtherViewerAndPreservesReservedWork(GameTestHelper h){
        var be=build(h,Blueprints.Kind.TIDAL_SPINDLE);stored(h,be,12_345_678_901L,true);
        be.items().setStackInSlot(0,item("collapsar_billet",7));be.fluidFor(null).fill(new FluidStack(SinguloFluids.get("liquid_helium"),1234),FluidAction.EXECUTE);
        var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());tag.putInt("progress",123);tag.putInt("duration",1200);tag.putInt("cost",20_000_000);tag.put("reserved",item("tidal_filament",4).save(h.getLevel().registryAccess()));be.loadWithComponents(tag,h.getLevel().registryAccess());
        var player=io.github.genichimaruo.singulo.test.harness.TestScope.player(h);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var viewer=io.github.genichimaruo.singulo.test.harness.TestScope.player(h);viewer.containerMenu=be.createMenu(1,viewer.getInventory(),viewer);
        var wrench=new ItemStack(io.github.genichimaruo.singulo.registry.SinguloItems.RELOCATION_WRENCH.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,wrench);player.setShiftKeyDown(true);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()),Direction.UP,be.getBlockPos(),false);
        wrench.getItem().onItemUseFirst(wrench,new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        h.assertTrue(h.getBlockState(C).isAir()&&viewer.containerMenu==viewer.inventoryMenu,"Packing left controller or stale menu");
        ItemStack packed=ItemStack.EMPTY;for(int i=0;i<player.getInventory().getContainerSize();i++){var s=player.getInventory().getItem(i);if(s.is(be.getBlockState().getBlock().asItem()))packed=s;}
        h.assertTrue(!packed.isEmpty(),"Packed controller missing");player.setShiftKeyDown(false);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,packed);
        ((net.minecraft.world.item.BlockItem)packed.getItem()).place(new net.minecraft.world.item.context.BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)));
        CosmicChamberBlockEntity copy=h.getBlockEntity(C);
        h.assertTrue(copy!=null&&copy.stored()==12_345_678_901L&&copy.core()&&copy.progress()==123&&copy.reserved().getCount()==4&&copy.items().getStackInSlot(0).getCount()==7&&copy.tank().getFluidAmount()==1234,"Packed work did not survive placement");
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1)).isEmpty(),"Packing also dropped loose contents");h.succeed();
    }
    @GameTest(template="forge")
    public static void cosmicReservoirSimulatesAndRejectsMixing(GameTestHelper h){
        var be=build(h,Blueprints.Kind.SUSPENSION_RESERVOIR);var in=be.fluidFor(Direction.NORTH);var out=be.fluidFor(Direction.SOUTH);
        h.assertTrue(h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,be.getBlockPos(),null)==null,"Fluid reservoir advertises an energy port");
        var water=new FluidStack(Fluids.WATER,Integer.MAX_VALUE);
        h.assertTrue(in.fill(water,FluidAction.SIMULATE)==16_000_000&&be.tank().isEmpty(),"Simulation changed reservoir");
        h.assertTrue(in.fill(water,FluidAction.EXECUTE)==16_000_000,"Finite capacity incorrect");
        h.assertTrue(out.fill(water,FluidAction.EXECUTE)==0&&in.drain(1000,FluidAction.EXECUTE).isEmpty(),"Directional IO bypass");
        h.assertTrue(out.drain(1234,FluidAction.SIMULATE).getAmount()==1234&&be.tank().getFluidAmount()==16_000_000,"Drain simulation mutated");
        out.drain(1234,FluidAction.EXECUTE);
        h.assertTrue(in.fill(new FluidStack(Fluids.LAVA,1000),FluidAction.EXECUTE)==0,"Mixed incompatible fluids");
        h.assertTrue(be.tank().getFluidAmount()==15_998_766,"Fluid conservation");
        var player=io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        var menu=(io.github.genichimaruo.singulo.cosmic.CosmicMenu)be.createMenu(1,player.getInventory(),player);
        h.assertTrue(menu.value(6)==15_998_766&&menu.value(11)==BuiltInRegistries.FLUID.getId(Fluids.WATER)+1,"Menu omits live fluid identity while amount updates");h.succeed();
    }
    @GameTest(template="forge")
    public static void cosmicBrokenStructureStopsImmediatelyAndKeepsContents(GameTestHelper h){
        var be=build(h,Blueprints.Kind.SUSPENSION_RESERVOIR);
        be.fluidFor(null).fill(new FluidStack(Fluids.WATER,7000),FluidAction.EXECUTE);
        var part=CosmicLayouts.RESERVOIR.pos(be.getBlockPos(),Direction.NORTH,0,6,0);
        var state=h.getLevel().getBlockState(part);h.getLevel().setBlockAndUpdate(part,Blocks.AIR.defaultBlockState());
        h.assertTrue(be.fluidFor(null).drain(7000,FluidAction.EXECUTE).isEmpty(),"Broken structure still drains in same tick");
        h.assertTrue(be.fluidFor(null).fill(new FluidStack(Fluids.WATER,100),FluidAction.EXECUTE)==0,"Broken structure accepts input");
        h.getLevel().setBlockAndUpdate(part,state);
        h.assertTrue(be.fluidFor(null).drain(7000,FluidAction.EXECUTE).getAmount()==7000,"Repair lost contents");h.succeed();
    }
    @GameTest(template="forge")
    public static void cosmicAccumulatorSharesTickBudgetWithoutGeneratingPower(GameTestHelper h){
        var be=build(h,Blueprints.Kind.PASTA_ACCUMULATOR);var input=be.energyFor(Direction.NORTH);var output=be.energyFor(Direction.SOUTH);
        h.assertTrue(h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,be.getBlockPos(),null)==null,"Accumulator advertises a fluid port");
        h.assertTrue(output.extractLong(Long.MAX_VALUE,false)==0,"Empty accumulator generated power");
        h.assertTrue(input.receiveLong(Long.MAX_VALUE,true)==2_000_000_000L&&be.stored()==0,"Simulation mutated energy");
        h.assertTrue(input.receiveLong(Long.MAX_VALUE,false)==2_000_000_000L,"Rate incorrect");
        h.assertTrue(be.energyFor(Direction.UP).receiveLong(1,false)==0,"Input budget is per face");
        h.assertTrue(input.extractLong(1,false)==0&&output.receiveLong(1,false)==0,"Directional energy bypass");
        h.assertTrue(output.extractLong(Long.MAX_VALUE,false)==2_000_000_000L&&be.stored()==0,"Energy not conserved");
        h.assertTrue(input.receiveLong(-1,false)==0&&output.extractLong(-1,false)==0,"Negative operation");h.succeed();
    }
    @GameTest(template="forge")
    public static void cosmicAccumulatorPersistsLongCapacity(GameTestHelper h){
        var be=build(h,Blueprints.Kind.PASTA_ACCUMULATOR);stored(h,be,8_000_000_000_000L,false);
        var copy=(CosmicChamberBlockEntity)BlockEntity.loadStatic(be.getBlockPos(),be.getBlockState(),be.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.stored()==8_000_000_000_000L,"Long energy truncated");
        h.assertTrue(be.energyFor(null).receiveLong(Long.MAX_VALUE,false)==0,"Finite capacity overflow");h.succeed();
    }
    @GameTest(template="forge")
    public static void cosmicPushReservationCannotOverfillOnReentrantReceive(GameTestHelper h){
        var be=build(h,Blueprints.Kind.PASTA_ACCUMULATOR);stored(h,be,be.capacity()-1_000_000_000L,false);long[] injected={0};
        var receiver=new net.neoforged.neoforge.energy.IEnergyStorage(){
            public int receiveEnergy(int amount,boolean simulate){if(!simulate)injected[0]+=be.energyFor(null).receiveLong(amount,false);return 0;}
            public int extractEnergy(int amount,boolean simulate){return 0;}
            public int getEnergyStored(){return 0;}public int getMaxEnergyStored(){return Integer.MAX_VALUE;}
            public boolean canExtract(){return false;}public boolean canReceive(){return true;}
        };
        try{
            // Inject a foreign capability that re-enters the public input during delivery.
            var send=CosmicChamberBlockEntity.class.getDeclaredMethod("sendPower",net.neoforged.neoforge.energy.IEnergyStorage.class);send.setAccessible(true);send.invoke(be,receiver);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        h.assertTrue(injected[0]==1_000_000_000L&&be.stored()==be.capacity(),"Refund and re-entrant input exceeded finite capacity");h.succeed();
    }
    @GameTest(template="forge",timeoutTicks=100)
    public static void cosmicSpindleCoreConsumesSeedAndStartupOnce(GameTestHelper h){
        var be=build(h,Blueprints.Kind.TIDAL_SPINDLE);stored(h,be,10_000_000_000L,false);be.items().setStackInSlot(2,item("singularity_seed",2));
        h.runAtTickTime(10,()->{
            h.assertTrue(be.core()&&be.stored()==0&&be.items().getStackInSlot(2).getCount()==1,"Startup cost/seed not exact");
            stored(h,be,10_000_000_000L,true);
        });
        h.runAtTickTime(30,()->{h.assertTrue(be.stored()==10_000_000_000L&&be.items().getStackInSlot(2).getCount()==1,"Core charged twice after load");h.succeed();});
    }
    @GameTest(template="forge",timeoutTicks=1500)
    public static void cosmicSpindleReservedWorkSurvivesReloadPowerPauseAndFullOutput(GameTestHelper h){
        var be=build(h,Blueprints.Kind.TIDAL_SPINDLE);stored(h,be,1_000_000_000L,true);
        be.items().setStackInSlot(0,item("collapsar_billet",1));be.items().setStackInSlot(1,item("exotic_matter",1));
        be.fluidFor(null).fill(new FluidStack(SinguloFluids.get("liquid_helium"),500),FluidAction.EXECUTE);
        int[] paused={0};boolean[] power={false};
        h.onEachTick(()->{if(power[0])be.energyFor(null).receiveLong(Long.MAX_VALUE,false);});
        h.runAtTickTime(20,()->{
            h.assertTrue(be.reserved().getCount()==4&&be.items().getStackInSlot(0).isEmpty()&&be.items().getStackInSlot(1).isEmpty()&&be.tank().isEmpty(),"Resources not reserved exactly once");
            stored(h,be,0,true);paused[0]=be.progress();
        });
        h.runAtTickTime(50,()->{h.assertTrue(be.progress()==paused[0]&&be.reserved().getCount()==4,"Unpowered saved work advanced/lost");be.items().setStackInSlot(3,item("tidal_filament",64));power[0]=true;});
        h.runAtTickTime(1300,()->{
            h.assertTrue(be.progress()==be.duration()&&be.reserved().getCount()==4&&be.items().getStackInSlot(3).getCount()==64,"Full output lost reserved product");
            be.items().setStackInSlot(3,ItemStack.EMPTY);
        });
        h.runAtTickTime(1320,()->{h.assertTrue(be.reserved().isEmpty()&&be.items().getStackInSlot(3).getCount()==4,"Completion duplicated or lost product");h.succeed();});
    }
    @GameTest(template="forge",timeoutTicks=50)
    public static void cosmicSpindleFullOutputDoesNotConsumeInputs(GameTestHelper h){
        var be=build(h,Blueprints.Kind.TIDAL_SPINDLE);stored(h,be,1_000_000_000L,true);
        be.items().setStackInSlot(0,item("collapsar_billet",1));be.items().setStackInSlot(1,item("exotic_matter",1));be.items().setStackInSlot(3,item("tidal_filament",64));
        be.fluidFor(null).fill(new FluidStack(SinguloFluids.get("liquid_helium"),500),FluidAction.EXECUTE);
        h.runAtTickTime(20,()->{h.assertTrue(be.reserved().isEmpty()&&be.items().getStackInSlot(0).getCount()==1&&be.tank().getFluidAmount()==500&&be.stored()==1_000_000_000L,"Blocked start consumed inputs");h.succeed();});
    }
}
