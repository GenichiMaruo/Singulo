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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

/** Ephemeral runtime state must not keep closed worlds or disconnected players alive. */
@EventBusSubscriber(modid = Singulo.MODID)
public final class RuntimeLifecycle {
    private RuntimeLifecycle() {}

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        io.github.genichimaruo.singulo.ruin.lab.RayleighSky.forgetLevel(level);
        if (level.isClientSide) return;
        io.github.genichimaruo.singulo.gravity.ZeroGravity.forgetLevel(level);
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
    public static void chunkUnloaded(net.minecraftforge.event.level.ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide) {
            io.github.genichimaruo.singulo.gravity.ZeroGravity.forgetChunk(level, event.getChunk().getPos());
        }
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
        io.github.genichimaruo.singulo.android.work.RecipeData.clearAlternatives();
        GravitonManipulatorItem.clearRuntime();
        MachineRecipeIndex.clear();
        RecipeSelection.clear();
        RecipeTweaks.invalidate();
    }

    @SubscribeEvent
    public static void tagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
        // 材料はタグの中身をあとから解決して覚える。再読み込みのときは古い定義を捨てる
        io.github.genichimaruo.singulo.android.work.RecipeData.clearAlternatives();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) BossCombatLog.onServerTick(event);
    }
}
