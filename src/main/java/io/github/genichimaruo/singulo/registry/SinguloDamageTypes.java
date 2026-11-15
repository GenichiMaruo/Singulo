package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import org.jspecify.annotations.Nullable;
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

    /** 降着ジェット鍛造炉のジェットとビーム。防具を無視する（タグ minecraft:bypasses_armor）。 */
    public static final ResourceKey<DamageType> JET_BEAM = ResourceKey.create(Registries.DAMAGE_TYPE, Singulo.id("jet_beam"));

    private SinguloDamageTypes() {}

    public static DamageSource jetBeam(Level level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(JET_BEAM));
    }

    public static DamageSource eventHorizon(Level level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(EVENT_HORIZON));
    }

    public static DamageSource tidal(Level level, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(TIDAL), attacker);
    }
}
