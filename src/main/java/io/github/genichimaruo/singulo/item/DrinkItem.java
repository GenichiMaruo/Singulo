package io.github.genichimaruo.singulo.item;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * 飲み物（研究所の自動販売機の缶と、コーヒーメーカーのコーヒー）。いつでも飲め、少しの満腹度と短い効果がある。
 * <ul>
 *   <li>缶ソーダ: 移動速度上昇 I（2分）</li>
 *   <li>スポーツドリンク: 再生 I（15秒）</li>
 *   <li>エナジードリンク: 採掘速度上昇 II（2分）</li>
 *   <li>コーヒー: 採掘速度上昇 I（3分）と眠気覚まし（ファントムが出るまでの時間が戻る）。飲むとガラス瓶が残る</li>
 * </ul>
 */
public class DrinkItem extends SinguloItem {
    /** 飲み物のID → 中身。 */
    public static final Map<String, Supplier<FoodProperties>> DRINKS = Map.of(
            "canned_soda", () -> drink(2, MobEffects.SPEED, 20 * 120, 0, false),
            "sports_drink", () -> drink(2, MobEffects.REGENERATION, 20 * 15, 0, false),
            "energy_drink", () -> drink(1, MobEffects.HASTE, 20 * 120, 1, false),
            "coffee", () -> drink(1, MobEffects.HASTE, 20 * 180, 0, true));

    private final boolean wakesUp;

    public DrinkItem(Properties properties, int stage, String id) {
        super(properties(properties, id), stage, false);
        this.wakesUp = id.equals("coffee");
    }

    private static FoodProperties drink(int nutrition, Holder<MobEffect> effect, int ticks, int amplifier, boolean bottle) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(0.2F).alwaysEdible().build();
    }

    private static Properties properties(Properties properties, String id) {
        var consumable = net.minecraft.world.item.component.Consumables.defaultDrink()
                .onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(effects(id), 1.0F)).build();
        properties.food(DRINKS.get(id).get(), consumable).stacksTo(16);
        if (id.equals("coffee")) properties.usingConvertsTo(Items.GLASS_BOTTLE);
        return properties;
    }

    /** コーヒーは眠気を覚ます（最後に眠ってからの時間を戻す。ファントムが出なくなる）。 */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (wakesUp && entity instanceof ServerPlayer player) {
            player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        }
        return super.finishUsingItem(stack, level, entity);
    }

    /** 効果の一覧（説明とテスト用）。 */
    public static List<MobEffectInstance> effects(String id) {
        return List.of(switch (id) {
            case "canned_soda" -> new MobEffectInstance(MobEffects.SPEED, 20 * 120, 0);
            case "sports_drink" -> new MobEffectInstance(MobEffects.REGENERATION, 20 * 15, 0);
            case "energy_drink" -> new MobEffectInstance(MobEffects.HASTE, 20 * 120, 1);
            case "coffee" -> new MobEffectInstance(MobEffects.HASTE, 20 * 180, 0);
            default -> throw new IllegalArgumentException("Unknown drink: " + id);
        });
    }
}
