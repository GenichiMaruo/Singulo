package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** マニピュレーターの投げる強さと、JEI に出すマルチブロックの部品表。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ManipulatorGameTests {
    private static final String EMPTY = "empty";

    private ManipulatorGameTests() {}

    /** ためるほど強く、ためきったら頭打ち。少し上向きに投げる。 */
    @GameTest(template = EMPTY)
    public static void manipulatorFlingScalesWithCharge(GameTestHelper helper) {
        Vec3 look = new Vec3(1, 0, 0);
        double weak = GravitonManipulatorItem.flingVelocity(look, 0).x;
        double strong = GravitonManipulatorItem.flingVelocity(look, GravitonManipulatorItem.MAX_CHARGE).x;
        double over = GravitonManipulatorItem.flingVelocity(look, GravitonManipulatorItem.MAX_CHARGE * 5).x;
        helper.assertTrue(strong > weak * 3, "ためても強くならない: " + weak + " → " + strong);
        helper.assertTrue(over == strong, "ためすぎで強くなりすぎる");
        helper.assertTrue(GravitonManipulatorItem.flingVelocity(look, 10).y > 0, "上向きの成分がない");
        helper.succeed();
    }

    /** どのマルチブロックも部品表にコントローラー（1個）と部品があること。大きさもログに出す。 */
    @GameTest(template = EMPTY)
    public static void multiblockPartLists(GameTestHelper helper) {
        for (Blueprints.Kind kind : Blueprints.Kind.values()) {
            Map<Block, Integer> parts = Blueprints.partCounts(kind);
            helper.assertTrue(parts.get(Blueprints.controllerBlock(kind)) == 1, kind.id() + " のコントローラーが1個でない");
            helper.assertTrue(parts.size() >= 2, kind.id() + " の部品がない");
            int minX = 99, minY = 99, minZ = 99, maxX = -99, maxY = -99, maxZ = -99;
            for (Map.Entry<BlockPos, BlockState> e : Blueprints.layout(kind, BlockPos.ZERO, Direction.NORTH, kind.defaultSize()).entrySet()) {
                if (e.getValue().isAir()) {
                    continue;
                }
                BlockPos p = e.getKey();
                minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
                minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            }
            Singulo.LOGGER.info("[mb] {} {}x{}x{} parts={}", kind.id(), maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, parts.size());
        }
        helper.succeed();
    }

    /** 右クリックでためて離すと、持ち上げたモブが前へ飛んでいき、1秒は持ち上げられない。 */
    @GameTest(template = "huge", timeoutTicks = 140)
    public static void manipulatorChargeAndThrow(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            net.minecraft.core.BlockPos base = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 1));
            player.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0F, 0F);   // 南（+Z）を向く
            var item = io.github.genichimaruo.singulo.registry.SinguloItems.GRAVITON_MANIPULATOR.get();
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            io.github.genichimaruo.singulo.item.GravityGauntletItem.energy(stack).receiveEnergy(Integer.MAX_VALUE, false);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            net.minecraft.world.item.ItemStack tank = new net.minecraft.world.item.ItemStack(
                    io.github.genichimaruo.singulo.registry.SinguloBlocks.CONTAINMENT_TANK.get());
            tank.set(io.github.genichimaruo.singulo.registry.SinguloComponents.DARK_MATTER.get(), 4000);
            player.getInventory().setItem(5, tank);
            var zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new net.minecraft.core.BlockPos(2, 1, 5));
            double[] startZ = new double[1];
            helper.onEachTick(() -> GravitonManipulatorItem.onPlayerTick(
                    new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player)));
            // 左クリックで持ち上げ、押したまま右クリックでためる（実際の操作と同じ）
            helper.runAtTickTime(2, () -> GravitonManipulatorItem.setInput(player, false, true));
            helper.runAtTickTime(6, () -> GravitonManipulatorItem.setInput(player, true, true));
            helper.runAtTickTime(40, () -> {
                helper.assertTrue(zombie.getY() > base.getY() + 0.5, "ためている間に持ち上がらない: y=" + zombie.getY());
                startZ[0] = zombie.getZ();
                GravitonManipulatorItem.setInput(player, true, false);
            });
            String[] after = new String[1];
            helper.runAtTickTime(41, () -> after[0] = "直後の速さ " + zombie.getDeltaMovement() + " 位置 " + zombie.position());
            helper.runAtTickTime(50, () -> helper.assertTrue(player.getCooldowns().isOnCooldown(item), "投げたあとにクールタイムがない"));
            // 左クリックは押したまま。クールタイムが明けても掴み直して引き戻さない
            helper.runAtTickTime(75, () -> {
                helper.assertTrue(zombie.getZ() > startZ[0] + 3, "離しても飛んでいかない: " + startZ[0] + " → " + zombie.getZ() + " / " + after[0]);
                zombie.discard();
                helper.succeed();
            });
        } finally {
            // プレイヤーはテストの終わりまで残す（runAtTickTime の後で外す）
            helper.runAtTickTime(85, () -> helper.getLevel().getServer().getPlayerList().remove(player));
        }
    }

    /** 円錐（G キー）で持ち上げていても、ためて離すと投げられる。 */
    @GameTest(template = "huge", timeoutTicks = 140)
    public static void manipulatorThrowInConeMode(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            net.minecraft.core.BlockPos base = helper.absolutePos(new net.minecraft.core.BlockPos(7, 1, 1));
            player.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0F, 0F);
            var item = io.github.genichimaruo.singulo.registry.SinguloItems.GRAVITON_MANIPULATOR.get();
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            io.github.genichimaruo.singulo.item.GravityGauntletItem.energy(stack).receiveEnergy(Integer.MAX_VALUE, false);
            item.toggleCone(player, stack);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            net.minecraft.world.item.ItemStack tank = new net.minecraft.world.item.ItemStack(
                    io.github.genichimaruo.singulo.registry.SinguloBlocks.CONTAINMENT_TANK.get());
            tank.set(io.github.genichimaruo.singulo.registry.SinguloComponents.DARK_MATTER.get(), 4000);
            player.getInventory().setItem(5, tank);
            var zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new net.minecraft.core.BlockPos(7, 1, 5));
            double[] startZ = new double[1];
            helper.onEachTick(() -> GravitonManipulatorItem.onPlayerTick(
                    new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player)));
            helper.runAtTickTime(2, () -> GravitonManipulatorItem.setInput(player, false, true));
            helper.runAtTickTime(6, () -> GravitonManipulatorItem.setInput(player, true, true));
            helper.runAtTickTime(40, () -> {
                helper.assertTrue(zombie.getY() > base.getY() + 0.5, "円錐で持ち上がらない: y=" + zombie.getY());
                startZ[0] = zombie.getZ();
                GravitonManipulatorItem.setInput(player, true, false);
            });
            helper.runAtTickTime(55, () -> {
                helper.assertTrue(zombie.getZ() > startZ[0] + 3, "円錐で持ち上げたものを投げられない: " + startZ[0] + " → " + zombie.getZ());
                zombie.discard();
                helper.succeed();
            });
        } finally {
            helper.runAtTickTime(65, () -> helper.getLevel().getServer().getPlayerList().remove(player));
        }
    }
}
