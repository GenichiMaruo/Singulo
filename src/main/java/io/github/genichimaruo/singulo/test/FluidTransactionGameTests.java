package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.compat.TransactionalFluidTank;
import io.github.genichimaruo.singulo.compat.FluidResourceView;
import io.github.genichimaruo.singulo.compat.TankFluidAccess;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Native storage invariants; executed under the real NeoForge test-server bootstrap. */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class FluidTransactionGameTests {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    @GameTest(template="empty", timeoutTicks=20)
    public static void fluidRootAndNestedRollback(GameTestHelper h) {
        int[] changes={0};
        var tank=new TransactionalFluidTank(1000) { @Override protected void onContentsChanged(){changes[0]++;} };
        var water=FluidResource.of(Fluids.WATER);
        try(var root=Transaction.openRoot()) {
            check(tank.insert(0,water,700,root)==700,"native insert");
            check(changes[0]==0,"callback before commit");
            try(var nested=Transaction.open(root)){tank.extract(0,water,250,nested);}
            check(tank.getAmountAsLong(0)==700,"nested rollback");
            try(var nested=Transaction.open(root)){tank.extract(0,water,200,nested);nested.commit();}
            check(tank.getAmountAsLong(0)==500,"nested commit visible");
        }
        check(tank.getAmountAsLong(0)==0 && changes[0]==0,"outer rollback undoes nested commit");
        try(var root=Transaction.openRoot()){tank.insert(0,water,400,root);tank.insert(0,water,200,root);root.commit();}
        check(tank.getAmountAsLong(0)==600 && changes[0]==1,"single commit notification");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=20)
    public static void fluidSimulationPreservesComponents(GameTestHelper h) {
        var tank=new TransactionalFluidTank(1000);
        var stack=new FluidStack(Fluids.WATER,600);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("component-test"));
        var resource=FluidResource.of(stack);
        var legacy=new FluidResourceView(tank);
        check(legacy.fill(stack,IFluidHandler.FluidAction.SIMULATE)==600 && tank.isEmpty(),"simulated fill changed state");
        check(legacy.fill(stack,IFluidHandler.FluidAction.EXECUTE)==600,"committed fill");
        check(resource.matches(tank.getFluid()),"component preservation");
        check(legacy.fill(new FluidStack(Fluids.WATER,100),IFluidHandler.FluidAction.EXECUTE)==0,"component mixing");
        check(legacy.drain(200,IFluidHandler.FluidAction.SIMULATE).getAmount()==200 && tank.getFluidAmount()==600,"simulated drain");
        try(var root=Transaction.openRoot()){tank.extract(0,resource,600,root);}
        check(tank.getFluidAmount()==600 && resource.matches(tank.getFluid()),"rollback lost components");
        check(legacy.drain(200,IFluidHandler.FluidAction.EXECUTE).getAmount()==200 && tank.getFluidAmount()==400,"committed drain");
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=20)
    public static void fluidTransactionsRejectInvalidAndLegacyMutation(GameTestHelper h) {
        var tank=new TransactionalFluidTank(500, stack->stack.getFluid()==Fluids.WATER);
        var water=FluidResource.of(Fluids.WATER);
        try(var root=Transaction.openRoot()) {
            check(tank.insert(0,FluidResource.of(Fluids.LAVA),100,root)==0,"validator bypass");
            check(tank.insert(0,water,900,root)==500,"capacity overflow");
            boolean blocked=false;
            try{tank.fill(new FluidStack(Fluids.WATER,1),IFluidHandler.FluidAction.EXECUTE);}catch(IllegalStateException expected){blocked=true;}
            check(blocked,"legacy mutation accepted inside transaction");
        }
        check(tank.isEmpty(),"rollback after rejected legacy mutation");
        boolean negative=false;
        try(var root=Transaction.openRoot()){tank.insert(0,water,-1,root);}catch(IllegalArgumentException expected){negative=true;}
        check(negative,"negative insert accepted");
        h.succeed();
    }
}