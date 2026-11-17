package io.github.genichimaruo.singulo.android.work;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import io.github.genichimaruo.singulo.api.android.AndroidEscrows;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraft.world.level.material.Fluids;
@GameTestHolder("singulo") @PrefixGameTestTemplate(false)
public final class AndroidEscrowStorageGameTests {
    @GameTest(template="empty") public static void physicalCustodyStableSlotsAndOwnerRefund(GameTestHelper h){
        var d=new AndroidEscrowData();var id=UUID.randomUUID();var owner=UUID.randomUUID();var worker=UUID.randomUUID();
        var offer=new ItemStack(Items.OAK_LOG,8);var e=d.create(id,owner,BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.OAK_PLANKS),32,List.of(offer));
        offer.setCount(1);h.assertTrue(d.inputs(e).get(0).getCount()==8,"Offer alias");
        d.bind(e,1,worker);var extracted=d.inputHandler(e).extractItem(0,8,false);
        h.assertTrue(extracted.getCount()==8&&d.inputs(e).size()==1&&d.inputs(e).get(0).isEmpty(),"Not physical or slot shifted");
        d.returnCargo(e,extracted);d.markTerminal(e,AndroidOrders.State.CANCELLED,0);
        h.assertTrue(d.claim(owner,id,false,0,64).isEmpty(),"Refund before worker return");
        d.workerReturned(e,worker);h.assertTrue(d.claim(UUID.randomUUID(),id,false,0,64).isEmpty(),"Foreign refund");
        h.assertTrue(d.claim(owner,id,false,0,64).getCount()==8&&d.acknowledge(owner,id),"Refund/ack failed");h.succeed();
    }
    @GameTest(template="empty") public static void escrowRoundtripAndMalformedRecordQuarantine(GameTestHelper h){
        var d=new AndroidEscrowData();var id=UUID.randomUUID();var owner=UUID.randomUUID();
        var e=d.create(id,owner,BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.STICK),4,List.of(new ItemStack(Items.OAK_PLANKS,2)));
        d.bind(e,1,UUID.randomUUID());var tag=d.save(new CompoundTag(),h.getLevel().registryAccess());
        var loaded=AndroidEscrowData.load(tag,h.getLevel().registryAccess());h.assertTrue(loaded.inputs(loaded.find(id)).get(0).getCount()==2,"Restore lost stacks");
        var raw=tag.getListOrEmpty("records").getCompoundOrEmpty(0);raw.putString("worker","broken");
        var bad=AndroidEscrowData.load(tag,h.getLevel().registryAccess());h.assertTrue(bad.find(id)==null&&bad.isKnown(id),"Malformed record usable");
        h.assertTrue(bad.save(new CompoundTag(),h.getLevel().registryAccess()).getListOrEmpty("records").getCompoundOrEmpty(0).equals(raw),"Quarantine changed raw record");h.succeed();
    }
    @GameTest(template="empty") public static void productsRespectSlotsAndSimulation(GameTestHelper h){
        var d=new AndroidEscrowData();var id=UUID.randomUUID();var owner=UUID.randomUUID();
        var e=d.create(id,owner,BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.STICK),4,List.of(new ItemStack(Items.OAK_PLANKS,2)));
        var handler=d.productHandler(e);h.assertTrue(handler.insertItem(0,new ItemStack(Items.STICK,70),true).getCount()==6&&d.products(e).isEmpty(),"Simulation mutated or exceeded slot");
        h.assertTrue(handler.insertItem(0,new ItemStack(Items.STICK,70),false).getCount()==6,"Incorrect remainder");
        h.assertTrue(d.claim(owner,id,true,0,64).getCount()==64&&d.claim(owner,id,true,0,64).isEmpty(),"Duplicate product claim");h.succeed();
    }
    @GameTest(template="empty") public static void emergencyQuarantineRetainsCargoAcrossRepeatedSaves(GameTestHelper h){
        CompoundTag broken=new CompoundTag();broken.putInt("schema",99);broken.putString("records","original corrupt payload");
        var d=AndroidEscrowData.load(broken,h.getLevel().registryAccess());var id=UUID.randomUUID();var owner=UUID.randomUUID();
        h.assertTrue(d.quarantineCargo(id,owner,UUID.randomUUID(),List.of(new ItemStack(Items.DIAMOND,7))),"Emergency custody rejected");
        var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());
        var again=AndroidEscrowData.load(saved,h.getLevel().registryAccess()).save(new CompoundTag(),h.getLevel().registryAccess());
        h.assertTrue(again.getStringOr("records", "").equals("original corrupt payload")&&again.getListOrEmpty("recovery_salvage_extra_v1").size()==1,"Lost original or duplicated salvage");
        h.assertTrue(d.claim(owner,id,true,0,64).isEmpty(),"Quarantine became claimable");h.succeed();
    }
    @GameTest(template="empty") public static void returnCargoGrowsWithoutDiscardAndStaleHandlersStop(GameTestHelper h){
        var d=new AndroidEscrowData();var id=UUID.randomUUID();var owner=UUID.randomUUID();var worker=UUID.randomUUID();
        var e=d.create(id,owner,BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.STICK),4,List.of(new ItemStack(Items.OAK_PLANKS,64)));
        d.bind(e,1,worker);h.assertTrue(d.returnCargo(e,new ItemStack(Items.DIAMOND,128)).isEmpty(),"Return capped or dropped");
        h.assertTrue(d.inputs(e).stream().mapToInt(ItemStack::getCount).sum()==192,"Return conservation");
        d.markTerminal(e,AndroidOrders.State.FAILED,0);d.workerReturned(e,worker);
        var handler=d.productHandler(e);for(int i=0;i<d.inputs(e).size();i++)d.claim(owner,id,false,i,64);
        h.assertTrue(d.acknowledge(owner,id)&&handler.insertItem(0,new ItemStack(Items.DIAMOND),false).getCount()==1,"Stale handler accepted items");h.succeed();
    }

    @GameTest(template="empty") public static void duplicateMalformedRecordBlocksOriginalCustody(GameTestHelper h){
        var d=new AndroidEscrowData();var id=UUID.randomUUID();var owner=UUID.randomUUID();
        d.create(id,owner,BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.STICK),4,List.of(new ItemStack(Items.OAK_PLANKS,2)));
        var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());var list=saved.getListOrEmpty("records");
        CompoundTag duplicate=list.getCompoundOrEmpty(0).copy();duplicate.remove("owner");list.add(duplicate);
        var loaded=AndroidEscrowData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(loaded.isKnown(id)&&loaded.find(id)==null&&loaded.claim(owner,id,true,0,64).isEmpty(),"Conflicting malformed duplicate left original usable");
        h.assertTrue(loaded.save(new CompoundTag(),h.getLevel().registryAccess()).getListOrEmpty("records").size()==2,"Conflict discarded custody record");h.succeed();
    }

    @GameTest(template="empty") public static void unknownOwnerCargoAndWorkerFingerprintPersist(GameTestHelper h){
        var d=AndroidEscrowData.load(new CompoundTag(),h.getLevel().registryAccess());var id=UUID.randomUUID();
        h.assertTrue(d.quarantineCargo(id,null,UUID.randomUUID(),List.of(new ItemStack(Items.DIAMOND,2))),"Unknown owner cargo discarded");
        var salvage=d.save(new CompoundTag(),h.getLevel().registryAccess()).getListOrEmpty("recovery_salvage_extra_v1").getCompoundOrEmpty(0);
        h.assertTrue(!salvage.contains("owner")&&salvage.getListOrEmpty("actual_cargo").size()==1,"Invented owner or lost cargo");
        var normal=new AndroidEscrowData();var e=normal.create(id,UUID.randomUUID(),BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.STICK),4,List.of(new ItemStack(Items.OAK_PLANKS,2)));
        e.requestedWorker=UUID.randomUUID();var saved=normal.save(new CompoundTag(),h.getLevel().registryAccess());
        h.assertTrue(e.requestedWorker.equals(AndroidEscrowData.load(saved,h.getLevel().registryAccess()).find(id).requestedWorker),"Requested worker lost");
        saved.getListOrEmpty("records").getCompoundOrEmpty(0).putString("requested_worker","invalid");
        h.assertTrue(AndroidEscrowData.load(saved,h.getLevel().registryAccess()).find(id)==null,"Invalid requested worker accepted");h.succeed();
    }

    @GameTest(template="empty") public static void fluidClaimsSimulateTheSameMilliBucketsAsExecution(GameTestHelper h){
        var d=AndroidEscrowData.get(h.getLevel());var id=UUID.randomUUID();var owner=UUID.randomUUID();var worker=UUID.randomUUID();
        var e=d.create(id,owner,BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.STICK),4,
                List.of(FluidSamples.of(new FluidStack(Fluids.WATER,1000))));
        d.bind(e,1,worker);d.fluidHandler(e,true).fill(new FluidStack(Fluids.WATER,750),FluidAction.EXECUTE);
        h.assertTrue(AndroidEscrows.claim(h.getLevel(),owner,id,false,0,100,true).isEmpty(),"Live material simulation bypassed custody");
        for(boolean products:List.of(true,false)){
            if(!products){d.markTerminal(e,AndroidOrders.State.CANCELLED,0);d.workerReturned(e,worker);}
            int before=products?750:1000;
            var simulated=AndroidEscrows.claim(h.getLevel(),owner,id,products,0,100,true);
            h.assertTrue(FluidSamples.amount(simulated)==100,"Fluid simulation used stack count instead of mB");
            h.assertTrue(FluidSamples.amount((products?d.products(e):d.inputs(e)).get(0))==before,"Simulation changed custody");
            h.assertTrue(AndroidEscrows.claim(h.getLevel(),UUID.randomUUID(),id,products,0,100,true).isEmpty(),"Foreign simulation leaked custody");
            var actual=AndroidEscrows.claim(h.getLevel(),owner,id,products,0,100,false);
            h.assertTrue(ItemStack.matches(simulated,actual),"Simulation and execution differ");
            h.assertTrue(FluidSamples.amount((products?d.products(e):d.inputs(e)).get(0))==before-100,"Partial claim lost fluid");
            h.assertTrue(FluidSamples.amount(AndroidEscrows.claim(h.getLevel(),owner,id,products,0,Integer.MAX_VALUE,true))==before-100,"Oversized simulation wrong");
            h.assertTrue(FluidSamples.amount(AndroidEscrows.claim(h.getLevel(),owner,id,products,0,Integer.MAX_VALUE,false))==before-100,"Remainder claim wrong");
            h.assertTrue(AndroidEscrows.claim(h.getLevel(),owner,id,products,0,100,false).isEmpty(),"Fluid claimed twice");
        }
        h.assertTrue(AndroidEscrows.acknowledge(h.getLevel(),owner,id),"Empty fluid custody not acknowledged");h.succeed();
    }

    @GameTest(template="empty") public static void fluidCustodySplitsOverflowAndPreservesItAcrossSave(GameTestHelper h){
        var d=new AndroidEscrowData();var id=UUID.randomUUID();var owner=UUID.randomUUID();var worker=UUID.randomUUID();
        var e=d.create(id,owner,BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.STICK),4,
                List.of(FluidSamples.of(new FluidStack(Fluids.WATER,Integer.MAX_VALUE-100))));
        d.bind(e,1,worker);
        h.assertTrue(d.returnCargo(e,FluidSamples.of(new FluidStack(Fluids.WATER,200))).isEmpty(),"Fluid return rejected");
        long expected=2L*Integer.MAX_VALUE+100;
        for(boolean products:List.of(false,true)){
            var handler=d.fluidHandler(e,products);
            if(products){handler.fill(new FluidStack(Fluids.WATER,Integer.MAX_VALUE),FluidAction.EXECUTE);handler.fill(new FluidStack(Fluids.WATER,100),FluidAction.EXECUTE);}
            long before=(products?d.products(e):d.inputs(e)).stream().mapToLong(FluidSamples::amount).sum();
            h.assertTrue(before==(long)Integer.MAX_VALUE+100,"Initial overflow lost fluid");
            h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,Integer.MAX_VALUE),FluidAction.SIMULATE)==Integer.MAX_VALUE,"Simulation rejected capacity");
            h.assertTrue((products?d.products(e):d.inputs(e)).stream().mapToLong(FluidSamples::amount).sum()==before,"Simulated fill changed custody");
            h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,Integer.MAX_VALUE),FluidAction.EXECUTE)==Integer.MAX_VALUE,"Fill silently dropped excess");
            h.assertTrue((products?d.products(e):d.inputs(e)).stream().mapToLong(FluidSamples::amount).sum()==expected,"Int overflow changed total");
        }
        d=AndroidEscrowData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());e=d.find(id);
        d.markTerminal(e,AndroidOrders.State.CANCELLED,0);d.workerReturned(e,worker);
        for(boolean products:List.of(false,true)){
            int slots=(products?d.products(e):d.inputs(e)).size();long claimed=0;
            for(int i=0;i<slots;i++)claimed+=FluidSamples.amount(d.claim(owner,id,products,i,Integer.MAX_VALUE));
            h.assertTrue(claimed==expected,"Save/claim lost overflow: "+claimed);
        }
        h.assertTrue(d.acknowledge(owner,id),"Overflow left unclaimable cargo");h.succeed();
    }

    @GameTest(template="empty") public static void fluidDrainsAggregateMatchingSlotsWithoutChangingSimulation(GameTestHelper h){
        var d=new AndroidEscrowData();var id=UUID.randomUUID();var owner=UUID.randomUUID();
        var e=d.create(id,owner,BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.STICK),4,List.of(
                FluidSamples.of(new FluidStack(Fluids.WATER,500)),FluidSamples.of(new FluidStack(Fluids.LAVA,400)),
                FluidSamples.of(new FluidStack(Fluids.WATER,500))));
        d.bind(e,1,UUID.randomUUID());var handler=d.fluidHandler(e,false);
        h.assertTrue(handler.drain(new FluidStack(Fluids.WATER,800),FluidAction.SIMULATE).getAmount()==800,"Simulation only saw first matching slot");
        h.assertTrue(d.inputs(e).stream().mapToLong(FluidSamples::amount).sum()==1400,"Drain simulation mutated custody");
        h.assertTrue(handler.drain(new FluidStack(Fluids.WATER,800),FluidAction.EXECUTE).getAmount()==800,"Execution only drained first matching slot");
        var remaining=d.inputs(e);
        h.assertTrue(remaining.size()==3 && remaining.get(0).isEmpty() && FluidSamples.amount(remaining.get(1))==400
                && FluidSamples.amount(remaining.get(2))==200,"Drain shifted slots, touched other fluid or lost remainder");
        h.assertTrue(handler.drain(new FluidStack(Fluids.WATER,500),FluidAction.EXECUTE).getAmount()==200,"Final remainder wrong");
        h.assertTrue(handler.drain(new FluidStack(Fluids.WATER,500),FluidAction.EXECUTE).isEmpty(),"Fluid drained twice");h.succeed();
    }
}
