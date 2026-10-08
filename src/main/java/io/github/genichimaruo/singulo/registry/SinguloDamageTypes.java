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

    /** 事象の地平線。無敵・防具・耐性・効果・エンチャント・盾・不死のトーテムをすべて無視する（タグ）。 */
    public static final ResourceKey<DamageType> EVENT_HORIZON = ResourceKey.create(Registries.DAMAGE_TYPE, Singulo.id("event_horizon"));

    private SinguloDamageTypes() {}

    public static DamageSource eventHorizon(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(EVENT_HORIZON));
    }

    public static DamageSource tidal(Level level, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TIDAL), attacker);
    }
}
