package io.github.genichimaruo.singulo.android.work;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
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
        var raw=tag.getList("records",Tag.TAG_COMPOUND).getCompound(0);raw.putString("worker","broken");
        var bad=AndroidEscrowData.load(tag,h.getLevel().registryAccess());h.assertTrue(bad.find(id)==null&&bad.isKnown(id),"Malformed record usable");
        h.assertTrue(bad.save(new CompoundTag(),h.getLevel().registryAccess()).getList("records",Tag.TAG_COMPOUND).getCompound(0).equals(raw),"Quarantine changed raw record");h.succeed();
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
        h.assertTrue(again.getString("records").equals("original corrupt payload")&&again.getList("recovery_salvage_extra_v1",Tag.TAG_COMPOUND).size()==1,"Lost original or duplicated salvage");
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
        var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());var list=saved.getList("records",Tag.TAG_COMPOUND);
        CompoundTag duplicate=list.getCompound(0).copy();duplicate.remove("owner");list.add(duplicate);
        var loaded=AndroidEscrowData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(loaded.isKnown(id)&&loaded.find(id)==null&&loaded.claim(owner,id,true,0,64).isEmpty(),"Conflicting malformed duplicate left original usable");
        h.assertTrue(loaded.save(new CompoundTag(),h.getLevel().registryAccess()).getList("records",Tag.TAG_COMPOUND).size()==2,"Conflict discarded custody record");h.succeed();
    }

    @GameTest(template="empty") public static void unknownOwnerCargoAndWorkerFingerprintPersist(GameTestHelper h){
        var d=AndroidEscrowData.load(new CompoundTag(),h.getLevel().registryAccess());var id=UUID.randomUUID();
        h.assertTrue(d.quarantineCargo(id,null,UUID.randomUUID(),List.of(new ItemStack(Items.DIAMOND,2))),"Unknown owner cargo discarded");
        var salvage=d.save(new CompoundTag(),h.getLevel().registryAccess()).getList("recovery_salvage_extra_v1",Tag.TAG_COMPOUND).getCompound(0);
        h.assertTrue(!salvage.contains("owner")&&salvage.getList("actual_cargo",Tag.TAG_COMPOUND).size()==1,"Invented owner or lost cargo");
        var normal=new AndroidEscrowData();var e=normal.create(id,UUID.randomUUID(),BlockPos.ZERO,UUID.randomUUID(),new ItemStack(Items.STICK),4,List.of(new ItemStack(Items.OAK_PLANKS,2)));
        e.requestedWorker=UUID.randomUUID();var saved=normal.save(new CompoundTag(),h.getLevel().registryAccess());
        h.assertTrue(e.requestedWorker.equals(AndroidEscrowData.load(saved,h.getLevel().registryAccess()).find(id).requestedWorker),"Requested worker lost");
        saved.getList("records",Tag.TAG_COMPOUND).getCompound(0).putString("requested_worker","invalid");
        h.assertTrue(AndroidEscrowData.load(saved,h.getLevel().registryAccess()).find(id)==null,"Invalid requested worker accepted");h.succeed();
    }}
