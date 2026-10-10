package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.compat.LegacyAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Runtime checks for physical attributes absent from vanilla 1.20.1. */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ForgeCompatibilityGameTests {
    @GameTest(template = "empty")
    public static void capabilitySurvivesInvalidationAndRevival(GameTestHelper helper) {
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, io.github.genichimaruo.singulo.registry.SinguloBlocks.CREATIVE_ENERGY_SOURCE.get());
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        var cap = net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY;
        var before = blockEntity.getCapability(cap);
        helper.assertTrue(before.isPresent(), "初期状態でエネルギーを接続できない");
        blockEntity.invalidateCaps();
        helper.assertFalse(before.isPresent(), "チャンクの無効化で古い接続が破棄されない");
        blockEntity.reviveCaps();
        helper.assertTrue(blockEntity.getCapability(cap).isPresent(), "再読み込み後にエネルギーを接続できない");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flightIsGrantedAndRevokedWithoutRemovingOtherFlight(GameTestHelper helper) {
        var player = TestBuild.mockPlayer(helper);
        try {
            player.setGameMode(GameType.SURVIVAL);
            var tick = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player);
            player.getAbilities().mayfly = true;
            LegacyAttributes.flight(tick);
            helper.assertTrue(player.getAbilities().mayfly, "別の機能で許可された飛行が解除された");
            player.getAbilities().mayfly = false;
            player.getAttribute(LegacyAttributes.FLIGHT.get()).setBaseValue(1);
            LegacyAttributes.flight(tick);
            helper.assertTrue(player.getAbilities().mayfly, "飛行属性で飛行が許可されない");
            player.getAbilities().flying = true;
            player.getAttribute(LegacyAttributes.FLIGHT.get()).setBaseValue(0);
            LegacyAttributes.flight(tick);
            helper.assertFalse(player.getAbilities().mayfly || player.getAbilities().flying, "飛行属性を外しても飛行が解除されない");
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void explosionResistanceSuppressesImpulse(GameTestHelper helper) {
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 3, 3));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setHealth(20);
        zombie.getAttribute(LegacyAttributes.EXPLOSION.get()).setBaseValue(1);
        zombie.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().explode(null, zombie.getX() - 1, zombie.getY(), zombie.getZ(), 1,
                false, Level.ExplosionInteraction.NONE);
        helper.assertTrue(zombie.getDeltaMovement().lengthSqr() > 0, "爆発の検査でノックバックが発生していない");
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(zombie.getDeltaMovement().lengthSqr() < 0.0001, "爆発ノックバック耐性が働いていない");
            zombie.discard();
            helper.succeed();
        });
    }

    private ForgeCompatibilityGameTests() {}
}
