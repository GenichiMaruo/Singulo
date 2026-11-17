package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

/** ボスは悪い効果を受けず、負傷と即時回復のどちらのポーションでも回復する。ボスのスポーンエッグはコマンド専用。 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class BossPotionGameTests {
    private static final List<Supplier<? extends EntityType<? extends LivingEntity>>> BOSSES = List.of(
            SinguloEntities.HORIZON_WARDEN, SinguloEntities.ECHO_SENTINEL, SinguloEntities.GRAVITY_REMNANT,
            SinguloEntities.ERGOSPHERE_LEVIATHAN, SinguloEntities.LAST_ARCHIVIST, SinguloEntities.FOUNDRY_OVERSEER);

    @GameTest(template = "empty")
    public static void bossesIgnoreHarmfulEffectsAndHealFromBothInstantPotions(GameTestHelper helper) {
        for (var type : BOSSES) {
            LivingEntity boss = type.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            String name = BuiltInRegistries.ENTITY_TYPE.getKey(type.get()).getPath();
            helper.assertTrue(boss != null, "Boss was not created: " + name);
            for (var harmful : List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.SLOWNESS, MobEffects.WEAKNESS,
                    MobEffects.BLINDNESS, MobEffects.LEVITATION)) {
                helper.assertFalse(boss.addEffect(new MobEffectInstance(harmful, 200)), name + " accepted " + harmful.getRegisteredName());
            }
            helper.assertTrue(boss.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200)), name + " refused a beneficial effect");

            // 投げたポーション・残留ポーション（即時の効き目）
            for (var instant : List.of(MobEffects.INSTANT_DAMAGE, MobEffects.INSTANT_HEALTH)) {
                boss.setHealth(boss.getMaxHealth() / 2);
                float before = boss.getHealth();
                instant.value().applyInstantenousEffect(helper.getLevel(), null, null, boss, 1, 1.0);
                helper.assertTrue(boss.getHealth() == before + 8, name + " was not healed 8 by " + instant.getRegisteredName()
                        + ": " + before + " -> " + boss.getHealth());
            }
            // 矢・コマンドで付けた効果（負傷は付けられ、効き目で回復する）
            boss.setHealth(boss.getMaxHealth() / 2);
            float before = boss.getHealth();
            helper.assertTrue(boss.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1)), name + " refused instant damage from arrows");
            MobEffects.INSTANT_DAMAGE.value().applyEffectTick(helper.getLevel(), boss, 0);
            helper.assertTrue(boss.getHealth() == before + 4, name + " was not healed by an instant damage tick");
            boss.discard();
        }
        for (String egg : List.of("horizon_warden_spawn_egg", "echo_sentinel_spawn_egg", "gravity_remnant_spawn_egg",
                "ergosphere_leviathan_spawn_egg", "last_archivist_spawn_egg", "foundry_overseer_spawn_egg")) {
            helper.assertTrue(SinguloItems.HIDDEN.contains(egg), "Boss egg is not command-only: " + egg);
        }
        helper.succeed();
    }
}
