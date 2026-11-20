package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class CreativeFluidTrashGameTests {
    private static final BlockPos CENTER = new BlockPos(3, 2, 3);

    @GameTest(template = "empty")
    public static void trashAcceptsAllFluidsFromEveryFace(GameTestHelper h) {
        h.setBlock(CENTER, SinguloBlocks.CREATIVE_FLUID_TRASH.get());
        var pos = h.absolutePos(CENTER);
        var nitrogen = SinguloFluids.ALL.get("liquid_nitrogen").source().get();
        for (Direction face : Direction.values()) {
            var sink = io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.FluidHandler.BLOCK, pos, face);
            h.assertTrue(sink != null && sink.getTanks() == 1, "Missing trash inlet on " + face);
            for (var fluid : new FluidStack[]{new FluidStack(nitrogen, Integer.MAX_VALUE), new FluidStack(Fluids.LAVA, 1000)}) {
                h.assertTrue(sink.fill(fluid, FluidAction.SIMULATE) == fluid.getAmount()
                        && sink.fill(fluid, FluidAction.EXECUTE) == fluid.getAmount(), "Trash rejected fluid");
                h.assertTrue(sink.getFluidInTank(0).isEmpty() && sink.drain(fluid, FluidAction.EXECUTE).isEmpty()
                        && sink.drain(Integer.MAX_VALUE, FluidAction.EXECUTE).isEmpty(), "Discarded fluid could be recovered");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void trashItemDisposesFluidInContainerFillingSlots(GameTestHelper h) {
        var stack = new ItemStack(SinguloBlocks.CREATIVE_FLUID_TRASH.get());
        stack.setHoverName(Component.literal("Test bin"));
        var source = new FluidTank(64_000);
        source.fill(new FluidStack(Fluids.WATER, 54_321), FluidAction.EXECUTE);
        var simulated = FluidUtil.tryFillContainer(stack, source, Integer.MAX_VALUE, null, false);
        h.assertTrue(simulated.isSuccess() && source.getFluidAmount() == 54_321, "Simulation consumed fluid");
        var filled = FluidUtil.tryFillContainer(stack, source, Integer.MAX_VALUE, null, true);
        h.assertTrue(filled.isSuccess() && ItemStack.matches(stack, filled.getResult()) && source.isEmpty(), "Lost item or left fluid behind");
        var handler = filled.getResult().getCapability(Capabilities.FluidHandler.ITEM).orElse(null);
        h.assertTrue(handler != null && handler.getFluidInTank(0).isEmpty()
                && handler.drain(Integer.MAX_VALUE, FluidAction.EXECUTE).isEmpty(), "Item retained discarded water");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void trashPlacementPreservesTankAndReturnsEmptyBucket(GameTestHelper h) {
        h.setBlock(CENTER, SinguloBlocks.FLUID_DRONE_PORT.get());
        FluidDronePortBlockEntity port = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, CENTER);
        port.receiveTank().fill(new FluidStack(Fluids.LAVA, 5432), FluidAction.EXECUTE);
        var player = TestScope.player(h);
        player.setGameMode(GameType.SURVIVAL);
        var pos = h.absolutePos(CENTER);
        player.setPos(Vec3.atCenterOf(pos.east(3)));
        var hand = InteractionHand.MAIN_HAND;
        var stack = new ItemStack(SinguloBlocks.CREATIVE_FLUID_TRASH.get());
        player.setItemInHand(hand, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0.5, 0, 0), Direction.EAST, pos, false);
        player.gameMode.useItemOn(player, h.getLevel(), stack, hand, hit);
        h.assertTrue(h.getLevel().getBlockState(pos.east()).is(SinguloBlocks.CREATIVE_FLUID_TRASH.get())
                && port.receiveTank().getFluidAmount() == 5432, "Placement on tank disposed of its contents");
        player.setItemInHand(hand, new ItemStack(Items.LAVA_BUCKET));
        var binHit = new BlockHitResult(Vec3.atCenterOf(pos.east()).add(0.5, 0, 0), Direction.EAST, pos.east(), false);
        player.gameMode.useItemOn(player, h.getLevel(), player.getItemInHand(hand), hand, binHit);
        h.assertTrue(player.getItemInHand(hand).is(Items.BUCKET) && h.getLevel().getFluidState(pos.east(2)).isEmpty(),
                "Pouring did not return an empty bucket or placed lava in world");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pumpAndPipeDisposeIntoCreativeTrash(GameTestHelper h) {
        h.setBlock(CENTER.north(), SinguloBlocks.FLUID_DRONE_PORT.get());
        FluidDronePortBlockEntity source = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, CENTER.north());
        source.receiveTank().fill(new FluidStack(Fluids.LAVA, 23_456), FluidAction.EXECUTE);
        h.setBlock(CENTER, SinguloBlocks.FLUID_PUMP.get());
        FluidPumpBlockEntity pump = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, CENTER);
        int[] spent = {0};
        h.setBlock(CENTER.east(), SinguloBlocks.FLUID_PIPE.get());
        h.setBlock(CENTER.east(2), SinguloBlocks.CREATIVE_FLUID_TRASH.get());
        h.onEachTick(() -> spent[0] += pump.energy().receiveEnergy(25_000 - spent[0], false));
        h.succeedWhen(() -> h.assertTrue(source.receiveTank().isEmpty() && spent[0] == 413,
                "Pump did not transfer and charge for exactly the disposed volume"));
    }
}
