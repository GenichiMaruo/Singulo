package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.wormhole.WormholeTransit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Forge 47 has no cancellable general entity pre-tick event. */
@Mixin(ServerLevel.class)
public abstract class WormholeProjectileTickMixin {
    @Redirect(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void singulo$projectilePassage(Entity entity) {
        // Skip only the entity tick: ServerLevel must still pop its profiler section afterwards.
        if (!WormholeTransit.onProjectileTick(entity)) entity.tick();
    }
}
