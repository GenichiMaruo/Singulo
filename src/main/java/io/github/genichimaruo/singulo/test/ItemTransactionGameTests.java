package io.github.genichimaruo.singulo.test;
import io.github.genichimaruo.singulo.compat.TransactionalItemStackHandler;
import io.github.genichimaruo.singulo.compat.ItemTransferView;
import io.github.genichimaruo.singulo.compat.ItemResourceView;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class ItemTransactionGameTests {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 @GameTest(template="empty",timeoutTicks=20)
 public static void itemNestedRollbackAndCallbacks(GameTestHelper h){
  int[] changed={0};
  var storage=new TransactionalItemStackHandler(2){protected void onContentsChanged(int i){changed[0]++;}};
  var stone=ItemResource.of(new ItemStack(Items.STONE));
  try(var root=Transaction.openRoot()){
   check(storage.insert(0,stone,40,root)==40,"insert");
   try(var nested=Transaction.open(root)){storage.extract(0,stone,10,nested);}
   check(storage.getAmountAsLong(0)==40,"nested rollback");
   try(var nested=Transaction.open(root)){storage.insert(1,stone,12,nested);nested.commit();}
   check(changed[0]==0,"early callback");
   boolean blocked=false;try{storage.setStackInSlot(0,ItemStack.EMPTY);}catch(IllegalStateException expected){blocked=true;}
   check(blocked,"legacy mutation must not bypass journal");
  }
  check(storage.getAmountAsLong(0)==0 && storage.getAmountAsLong(1)==0 && changed[0]==0,"outer rollback");
  try(var root=Transaction.openRoot()){storage.insert(0,stone,40,root);storage.insert(0,stone,10,root);root.commit();}
  check(storage.getAmountAsLong(0)==50 && changed[0]==1,"root callback once");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=20)
 public static void itemPolicyAndComponents(GameTestHelper h){
  var storage=new TransactionalItemStackHandler(2);
  var stack=new ItemStack(Items.STONE,12);stack.set(DataComponents.CUSTOM_NAME,Component.literal("component-test"));
  var resource=ItemResource.of(stack);var plain=ItemResource.of(new ItemStack(Items.STONE));
  var view=new ItemTransferView(storage,(slot,r)->slot==0,(slot,r)->slot==1);
  var legacy=new ItemResourceView(storage);
  check(legacy.insertItem(0,stack,true).isEmpty() && storage.getAmountAsLong(0)==0,"simulation");
  check(legacy.insertItem(0,stack,false).isEmpty(),"commit");
  try(var root=Transaction.openRoot()){
   check(view.insert(0,plain,1,root)==0,"component mixing");
   check(view.insert(1,resource,1,root)==0,"output insertion");
   check(view.extract(0,resource,1,root)==0,"input extraction");
   check(storage.extract(0,resource,5,root)==5,"extract");
  }
  check(storage.getAmountAsLong(0)==12 && resource.matches(storage.getStackInSlot(0)),"rollback component loss");h.succeed();
 }
}
