package io.github.genichimaruo.singulo.data;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.multiblock.FormationEffect;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.recipe.RecipeTweaks;
import io.github.genichimaruo.singulo.ruin.ArenaShield;
import io.github.genichimaruo.singulo.ruin.BossCombatLog;
import io.github.genichimaruo.singulo.wormhole.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Ephemeral runtime state must not keep closed worlds or disconnected players alive. */
@EventBusSubscriber(modid = Singulo.MODID)
public final class RuntimeLifecycle {
    private RuntimeLifecycle() {}

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        InertialStabilizerBlockEntity.forgetLevel(level);
        ShieldTowerBlockEntity.forgetLevel(level);
        PenroseReactorBlockEntity.forgetLevel(level);
        WormholeMouthBlockEntity.forgetLevel(level);
        WormholePortBlockEntity.forgetLevel(level);
        WorldlineAnchorBlockEntity.forgetLevel(level);
        TimeFields.forgetLevel(level);
        FormationEffect.forgetLevel(level);
        ArenaShield.forgetLevel(level);
        BossCombatLog.forgetLevel(level);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) { forget(event); }
    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) { forget(event); }
    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) { forget(event); }
    private static void forget(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) GravitonManipulatorItem.forgetPlayer(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        GravitonManipulatorItem.clearRuntime();
        MachineRecipeIndex.clear();
        RecipeSelection.clear();
        RecipeTweaks.invalidate();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) { BossCombatLog.onServerTick(event); }
}
