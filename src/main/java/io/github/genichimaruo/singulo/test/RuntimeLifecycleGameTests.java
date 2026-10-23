package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.data.RuntimeLifecycle;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.multiblock.FormationEffect;
import io.github.genichimaruo.singulo.registry.*;
import io.github.genichimaruo.singulo.ruin.*;
import io.github.genichimaruo.singulo.wormhole.WormholePortBlockEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RuntimeLifecycleGameTests {
    private static Map<?, ?> map(Class<?> type, String field) {
        try {
            var f = type.getDeclaredField(field); f.setAccessible(true);
            return (Map<?, ?>) f.get(null);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @GameTest(template = "empty", batch = "lifecycle_cleanup")
    public static void closingLevelReleasesLiveDeviceRegistryAndFields(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(Level.NETHER);
        var pos = h.absolutePos(new BlockPos(2, 1, 2)).atY(200);
        var port = new WormholePortBlockEntity(pos, SinguloBlocks.WORMHOLE_PORT.get().defaultBlockState());
        port.setLevel(level); port.onLoad();
        TimeFields.set(level, pos, pos, 16, true);
        UUID owner = UUID.randomUUID();
        ArenaShield.raise(owner, level, Vec3.atCenterOf(pos), 16, pos.getY(), 16);
        try {
            h.assertTrue(map(WormholePortBlockEntity.class, "LOADED").containsKey(level), "Port was not registered");
            h.assertTrue(TimeFields.state(level, pos.east()) == 1, "Time field fixture missing");
            RuntimeLifecycle.unload(new LevelEvent.Unload(level));
            h.assertFalse(map(WormholePortBlockEntity.class, "LOADED").containsKey(level), "Closing level keeps live BE -> Level references");
            h.assertTrue(TimeFields.state(level, pos.east()) == 0 && !ArenaShield.active(owner), "Closed level left fields active");
        } finally { port.setRemoved(); TimeFields.remove(level, pos); ArenaShield.lower(owner); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "lifecycle_cleanup")
    public static void closingLevelDiscardsPendingFormationEffects(GameTestHelper h) {
        var level = h.getLevel().getServer().getLevel(Level.NETHER);
        var pos = h.absolutePos(new BlockPos(2, 1, 2)).atY(200);
        FormationEffect.play(level, pos, 0, 0, 0);
        try {
            var field = FormationEffect.class.getDeclaredField("QUEUE"); field.setAccessible(true);
            var queue = (List<?>) field.get(null);
            int before = queue.size();
            h.assertTrue(before > 0, "No pending particle fixture");
            RuntimeLifecycle.unload(new LevelEvent.Unload(level));
            h.assertTrue(queue.size() == before - 1, "Pending particle retains a closed level");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        finally { FormationEffect.forgetLevel(level); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "lifecycle_cleanup")
    public static void logoutAndDimensionChangeClearHeldInput(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SinguloItems.GRAVITON_MANIPULATOR.get()));
        try {
            GravitonManipulatorItem.setInput(player, true, true);
            h.assertTrue(map(GravitonManipulatorItem.class, "CHARGING").containsKey(player.getUUID()), "Charge fixture missing");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            h.assertFalse(map(GravitonManipulatorItem.class, "CHARGING").containsKey(player.getUUID()), "Logout kept charge input");
            GravitonManipulatorItem.setInput(player, true, true);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
            h.assertFalse(map(GravitonManipulatorItem.class, "CHARGING").containsKey(player.getUUID()), "Dimension change kept old charge input");
        } finally { GravitonManipulatorItem.forgetPlayer(player); player.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "lifecycle_cleanup")
    public static void expiredCombatAttributionReleasesBossReference(GameTestHelper h) {
        boolean old = ServerConfig.BOSS_COMBAT_LOG.get();
        var player = h.makeMockServerPlayerInLevel();
        var boss = h.spawn(SinguloEntities.ECHO_SENTINEL.get(), new BlockPos(4, 1, 4));
        try {
            ServerConfig.BOSS_COMBAT_LOG.set(true);
            BossCombatLog.slammed(player, boss, "test");
            h.assertTrue(map(BossCombatLog.class, "SLAMS").containsKey(player.getUUID()), "Missing attribution fixture");
            // The world remains open: expiry must work even if the victim never takes fall damage.
            var field = BossCombatLog.class.getDeclaredField("SLAMS"); field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<UUID, Object> slams = (Map<UUID, Object>) field.get(null);
            Object entry = slams.get(player.getUUID());
            var ctor = entry.getClass().getDeclaredConstructors()[0]; ctor.setAccessible(true);
            slams.put(player.getUUID(), ctor.newInstance(boss, "test", h.getLevel().getGameTime() - 1));
            BossCombatLog.onServerTick(new ServerTickEvent.Post(() -> true, h.getLevel().getServer()));
            h.assertFalse(slams.containsKey(player.getUUID()), "Expired attribution retained the boss without fall damage");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        finally { ServerConfig.BOSS_COMBAT_LOG.set(old); player.discard(); boss.discard(); }
        h.succeed();
    }
}
