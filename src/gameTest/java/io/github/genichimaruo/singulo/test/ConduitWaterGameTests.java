package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ConduitWaterGameTests {
    private static void placeUnderwater(GameTestHelper h, BlockPos at, Block block, Player player) {
        h.setBlock(at.below(), Blocks.STONE);
        h.setBlock(at, Blocks.WATER);
        var support = h.absolutePos(at.below());
        var hit = new BlockHitResult(support.getCenter().relative(Direction.UP, 0.5), Direction.UP, support, false);
        var stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        h.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "Underwater placement failed: " + block);
        h.assertTrue(h.getBlockState(at).is(block) && h.getBlockState(at).getFluidState().isSourceOfType(Fluids.WATER),
                "Placement displaced the water source: " + block);
    }

    @GameTest(template = "empty")
    public static void everyConduitKeepsWaterThroughBucketsAndRemoval(GameTestHelper h) {
        var level = h.getLevel();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var at = new BlockPos(3, 2, 3);
        var pos = h.absolutePos(at);
        for (Block block : List.of(SinguloBlocks.COPPER_WIRE.get(), SinguloBlocks.SUPERCONDUCTING_CABLE.get(),
                SinguloBlocks.TOPOLOGICAL_WIRE.get(), SinguloBlocks.HORIZON_BUS.get(), SinguloBlocks.FLUID_PIPE.get(),
                SinguloBlocks.VACUUM_JACKETED_PIPE.get(), SinguloBlocks.SUPERFLUID_CONDUIT.get(), SinguloBlocks.HORIZON_DUCT.get(),
                SinguloBlocks.COVER_FRAME.get())) {
            h.assertTrue(block.defaultBlockState().getFluidState().isEmpty(), "Default state contained water: " + block);
            placeUnderwater(h, at, block, player);
            var wet = h.getBlockState(at);
            var water = (SimpleWaterloggedBlock) block;
            h.assertTrue(!wet.propagatesSkylightDown() && wet.getLightDampening() == 1, "Incorrect underwater lighting");
            h.assertTrue(water.pickupBlock(player, level, pos, wet).is(Items.WATER_BUCKET)
                    && h.getBlockState(at).is(block) && h.getBlockState(at).getFluidState().isEmpty(), "Bucket did not drain only the water");
            h.assertTrue(water.placeLiquid(level, pos, h.getBlockState(at), Fluids.WATER.getSource(false)), "Water bucket did not refill the block");
            h.setBlock(at.east(), Blocks.STONE);
            h.assertTrue(h.getBlockState(at).getFluidState().isSourceOfType(Fluids.WATER), "Neighbour update lost water");
            h.setBlock(at.east(), Blocks.AIR);
            level.destroyBlock(pos, false);
            h.assertTrue(h.getBlockState(at).is(Blocks.WATER) && level.getFluidState(pos).isSource(), "Removal left an air pocket");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void underwaterCablePowersPumpAndPipeCarriesOnlyLava(GameTestHelper h) {
        BlockPos pump = new BlockPos(4, 2, 4);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(pump, SinguloBlocks.FLUID_PUMP.get());
        h.setBlock(pump.north(), SinguloBlocks.FLUID_DRONE_PORT.get());
        h.setBlock(pump.east(3), SinguloBlocks.FLUID_DRONE_PORT.get());
        h.setBlock(pump.west(3), SinguloBlocks.SMES_MODULE.get().defaultBlockState().setValue(AbstractMachineBlock.FACING, Direction.EAST));
        SmesCellBlockEntity battery = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, pump.west(3));
        FluidDronePortBlockEntity source = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, pump.north()), sink = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, pump.east(3));
        var lava = new FluidStack(Fluids.LAVA, 1000);
        source.receiveTank().fill(lava, FluidAction.EXECUTE);
        placeUnderwater(h, pump.west(2), SinguloBlocks.HORIZON_BUS.get(), player);
        placeUnderwater(h, pump.west(), SinguloBlocks.SUPERCONDUCTING_CABLE.get(), player);
        placeUnderwater(h, pump.east(), SinguloBlocks.FLUID_PIPE.get(), player);
        placeUnderwater(h, pump.east(2), SinguloBlocks.VACUUM_JACKETED_PIPE.get(), player);
        h.runAfterDelay(10, () -> {
            h.assertTrue(sink.sendTank().isEmpty() && source.receiveTank().getFluidAmount() == 1000, "Submerged pipes extracted fluid with no power");
            battery.energy().setEnergy(5000);
            h.succeedWhen(() -> {
                h.assertTrue(FluidStack.matches(sink.sendTank().getFluid(), lava) && source.receiveTank().isEmpty(),
                        "The underwater line did not deliver exactly one bucket of lava");
                for (var at : List.of(pump.west(2), pump.west(), pump.east(), pump.east(2))) {
                    h.assertTrue(h.getBlockState(at).getFluidState().isSourceOfType(Fluids.WATER), "Transport consumed the surrounding water");
                }
            });
        });
    }
}
