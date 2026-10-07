package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class SinguloDamageTypes {
    /** 潮汐力。防具を無視する（タグ minecraft:bypasses_armor）。 */
    public static final ResourceKey<DamageType> TIDAL = ResourceKey.create(Registries.DAMAGE_TYPE, Singulo.id("tidal"));

    private SinguloDamageTypes() {}

    public static DamageSource tidal(Level level, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TIDAL), attacker);
    }
}
