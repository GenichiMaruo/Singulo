package io.github.genichimaruo.singulo.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse each projectile's collision filter before allowing a wormhole to bypass its normal tick. */
@Mixin(Projectile.class)
public interface ProjectileAccessor {
    @Invoker("canHitEntity")
    boolean singulo$canHitEntity(Entity entity);
}
