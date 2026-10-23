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
import net.minecraft.world.item.UseAnim;
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
            "canned_soda", () -> drink(2, MobEffects.MOVEMENT_SPEED, 20 * 120, 0, false),
            "sports_drink", () -> drink(2, MobEffects.REGENERATION, 20 * 15, 0, false),
            "energy_drink", () -> drink(1, MobEffects.DIG_SPEED, 20 * 120, 1, false),
            "coffee", () -> drink(1, MobEffects.DIG_SPEED, 20 * 180, 0, true));

    private final boolean wakesUp;

    public DrinkItem(Properties properties, int stage, String id) {
        super(properties.food(DRINKS.get(id).get()).stacksTo(16), stage, false);
        this.wakesUp = id.equals("coffee");
    }

    private static FoodProperties drink(int nutrition, MobEffect effect, int ticks, int amplifier, boolean bottle) {
        FoodProperties.Builder b = new FoodProperties.Builder().nutrition(nutrition).saturationMod(0.2F).alwaysEat()
                .effect(() -> new MobEffectInstance(effect, ticks, amplifier), 1.0F);
        if (bottle) {
            // The glass bottle is returned in finishUsingItem on 1.20.1.
        }
        return b.build();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    /** コーヒーは眠気を覚ます（最後に眠ってからの時間を戻す。ファントムが出なくなる）。 */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (wakesUp && entity instanceof ServerPlayer player) {
            player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        }
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (wakesUp && entity instanceof net.minecraft.world.entity.player.Player player && !player.getAbilities().instabuild) {
            if (result.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
        }
        return result;
    }

    /** 効果の一覧（説明とテスト用）。 */
    public static List<MobEffectInstance> effects(String id) {
        return DRINKS.get(id).get().getEffects().stream().map(e -> e.getFirst()).toList();
    }
}
