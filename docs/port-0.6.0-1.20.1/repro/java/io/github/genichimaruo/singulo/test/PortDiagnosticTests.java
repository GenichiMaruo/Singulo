package io.github.genichimaruo.singulo.test;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
@GameTestHolder("singulo_port_checks") @PrefixGameTestTemplate(false)
public class PortDiagnosticTests {
 @GameTest(template="empty",batch="boss_reward_log",timeoutTicks=40)
 public static void fatalSlamLogsPostDamageHealth(GameTestHelper h) { BossRewardGameTests.fatalSlamIsLoggedAsDeath(h); }
 @GameTest(template="empty",timeoutTicks=400) public static void transferInventory(GameTestHelper h) { AndroidGameTests.transferUpgradePassesInventory(h); }
 @GameTest(template="empty",timeoutTicks=40) public static void stepHeight(GameTestHelper h) { Port1201GameTests.stepHeightMatchesModernOneBlockHeight(h); }
 @GameTest(template="empty",batch="tank_water",timeoutTicks=160) public static void shatteredTankAcceptsFlowingWater(GameTestHelper h) {
  BossRoomGameTests.remnantShattersItsTank(h);
  h.setBlock(new BlockPos(7,4,4),Blocks.WATER);
  h.runAtTickTime(118,()->h.assertBlockPresent(Blocks.WATER,new BlockPos(6,4,4)));
 }
 @GameTest(template="huge",batch="reactor_ignition",timeoutTicks=420) public static void ignitionTiming(GameTestHelper h) { ReactorGameTests.ignitionNeeds50GFEWithinTenSeconds(h); }
}
