package io.github.genichimaruo.singulo.gravity;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Receiver state must be rebuilt from saved blocks, never from transient bookkeeping alone. */
@GameTestHolder("singulo_panel_review")
@PrefixGameTestTemplate(false)
public final class PanelReceiverRegressionGameTests {
    private static final BlockPos RECEIVER = new BlockPos(1, 1, 1);
    private static final BlockPos PANEL = new BlockPos(2, 1, 1);

    private static GravityReceiverBlockEntity receiver(GameTestHelper helper) {
        helper.setBlock(RECEIVER, SinguloBlocks.GRAVITY_PANEL_RECEIVER.get());
        return io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RECEIVER);
    }

    private static void tick(GameTestHelper helper, GravityReceiverBlockEntity receiver) {
        receiver.requestScan();
        GravityReceiverBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(RECEIVER), helper.getBlockState(RECEIVER), receiver);
    }

    @GameTest(template = "empty")
    public static void savedPoweredPanelsStopWithoutEnergy(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.setBlock(PANEL, SinguloBlocks.LOW_GRAVITY_PANEL.get().defaultBlockState().setValue(GravityPanelBlock.POWERED, true));
        GravityReceiverBlockEntity receiver = receiver(helper);
        tick(helper, receiver);
        helper.assertFalse(helper.getBlockState(PANEL).getValue(GravityPanelBlock.POWERED), "A saved powered panel works without power after reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedRayleighPanelsStopWithoutEnergy(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.setBlock(PANEL, SinguloBlocks.RAYLEIGH_PANEL.get().defaultBlockState().setValue(RayleighPanelBlock.LIT, true));
        GravityReceiverBlockEntity receiver = receiver(helper);
        tick(helper, receiver);
        helper.assertFalse(helper.getBlockState(PANEL).getValue(RayleighPanelBlock.LIT), "A saved sky panel supplies sunlight without power after reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unloadingReceiverStopsItsLoadedPanels(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.setBlock(PANEL, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        GravityReceiverBlockEntity receiver = receiver(helper);
        tick(helper, receiver);
        receiver.energy().receiveLong(ZeroGravity.BASE_COST, false);
        tick(helper, receiver);
        BlockPos p = helper.absolutePos(PANEL).above();
        helper.assertTrue(ZeroGravity.inZone(helper.getLevel(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5), "Fixture was not powered");
        receiver.onChunkUnloaded();
        helper.assertFalse(helper.getBlockState(PANEL).getValue(GravityPanelBlock.POWERED), "An unloaded receiver leaves loaded panels powered");
        helper.assertFalse(ZeroGravity.inZone(helper.getLevel(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5), "An unloaded receiver leaves a weightless zone behind");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void impactGeneratorReservesActualPanelDemand(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.setBlock(PANEL, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        BlockPos control = PANEL.south();
        helper.setBlock(control, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        ((ZeroGravityControllerBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, control)).setHeight(8);
        helper.setBlock(RECEIVER, SinguloBlocks.IMPACT_GENERATOR.get());
        ImpactGeneratorBlockEntity receiver = io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, RECEIVER);
        receiver.absorb(20);
        tick(helper, receiver);
        long expected = receiver.stored() - ZeroGravity.costPerBlock(8) * ImpactGeneratorBlockEntity.RESERVE_TICKS;
        helper.assertTrue(receiver.surplus() == expected, "Impact generator must reserve the actual zero-gravity cost: " + receiver.surplus() + " vs " + expected);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fastLandingInsideWeightlessZoneDoesNotHurt(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.setBlock(PANEL, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        BlockPos control = PANEL.south();
        helper.setBlock(control, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        ((ZeroGravityControllerBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, control)).setHeight(1);
        GravityReceiverBlockEntity receiver = receiver(helper);
        tick(helper, receiver);
        receiver.energy().receiveLong(ZeroGravity.costPerBlock(1), false);
        tick(helper, receiver);
        var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, PANEL.above(5));
        try {
            float health = zombie.getHealth();
            zombie.fallDistance = 30;
            zombie.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(0, -8, 0));
            helper.assertTrue(zombie.onGround(), "Fixture did not land");
            helper.assertTrue(zombie.getHealth() == health, "Crossing a thin weightless zone and landing in one tick still causes fall damage");
        } finally {
            zombie.discard();
        }
        BlockPos outside = PANEL.east(3);
        helper.setBlock(outside, net.minecraft.world.level.block.Blocks.STONE);
        var controlZombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, outside.above(5));
        try {
            float health = controlZombie.getHealth();
            controlZombie.fallDistance = 30;
            controlZombie.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(0, -8, 0));
            helper.assertTrue(controlZombie.getHealth() < health, "Ordinary falls outside the powered zone must still cause damage");
        } finally {
            controlZombie.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saturatedDemandDoesNotCreateFreePower(GameTestHelper helper) {
        io.github.genichimaruo.singulo.compat.Legacy.isolateFixture(helper);
        helper.setBlock(PANEL, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        BlockPos second = PANEL.east();
        helper.setBlock(second, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        BlockPos control = PANEL.south();
        helper.setBlock(control, SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get());
        ((ZeroGravityControllerBlockEntity) io.github.genichimaruo.singulo.compat.Legacy.blockEntity(helper, control)).setHeight(10_000);
        GravityReceiverBlockEntity receiver = receiver(helper);
        tick(helper, receiver);
        receiver.energy().receiveLong(Long.MAX_VALUE, false);
        tick(helper, receiver);
        helper.assertTrue(receiver.activePanels() == 1, "One full buffer must power only one maximum-cost panel");
        receiver.energy().receiveLong(Long.MAX_VALUE, false);
        // Shrink the group to let the old fast path cache allOn, then restore the second expensive panel.
        helper.setBlock(second, net.minecraft.world.level.block.Blocks.AIR);
        tick(helper, receiver);
        helper.setBlock(second, SinguloBlocks.ZERO_GRAVITY_BLOCK.get());
        receiver.energy().receiveLong(Long.MAX_VALUE, false);
        tick(helper, receiver);
        helper.assertTrue(receiver.activePanels() == 1, "Saturated demand must not activate two panels for the price of one");
        helper.succeed();
    }
}
