package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * 触媒の規則（設計書「設計の柱」2・3）。
 * <ul>
 *   <li>装置ティアNに上位の触媒: 1段ごとに速度+25%・消費−20%（2段まで）</li>
 *   <li>同じティア: 標準</li>
 *   <li>1段下: 速度×lowerTierSpeed、消費×lowerTierConsumption</li>
 *   <li>2段以上下: 使えない</li>
 *   <li>残量が減るほど必要電力が増え（×1.0〜×1.6）、要求電力の underpowerThreshold 未満なら消費×underpowerConsumption</li>
 * </ul>
 */
public final class CatalystHelper {
    private CatalystHelper() {}

    /** 触媒の効き方。usable が false なら装置は動かない。 */
    public record Effect(boolean usable, double speed, double consumption) {
        public static final Effect NONE = new Effect(false, 0, 0);
    }

    /** 触媒のティア（段階と同じ）。触媒でなければ 0。 */
    public static int tierOf(ItemStack stack) {
        if (!(stack.getItem() instanceof UsesItem uses) || !uses.isCatalyst()) {
            return 0;
        }
        if (stack.getItem() instanceof CreativeCatalystItem) {
            return CreativeCatalystItem.tier(stack);
        }
        return switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()) {
            case "muon_catalyst" -> 2;
            case "bose_condensate_catalyst" -> 3;
            case "time_crystal_catalyst" -> 4;
            case "singularity_core" -> 5;
            default -> 0;
        };
    }

    public static Effect effect(int machineTier, ItemStack catalyst) {
        int tier = tierOf(catalyst);
        if (tier == 0 || UsesHelper.remaining(catalyst) <= 0) {
            return Effect.NONE;
        }
        int diff = tier - machineTier;
        if (diff >= 1) {
            int steps = Math.min(diff, 2);
            return new Effect(true, 1 + 0.25 * steps, 1 - 0.2 * steps);
        }
        if (diff == 0) {
            return new Effect(true, 1, 1);
        }
        if (diff == -1) {
            return new Effect(true, lowerTierSpeed(), lowerTierConsumption());
        }
        return Effect.NONE;
    }

    /** 残量による必要電力の倍率。100〜51%で×1.0〜1.25、50〜11%で×1.25〜1.6、10%以下で×1.6。 */
    public static double powerMultiplier(ItemStack catalyst) {
        UsesData data = UsesHelper.get(catalyst);
        if (data.max() <= 0) {
            return 1;
        }
        double r = (double) data.remaining() / data.max();
        if (r > 0.5) {
            return 1.0 + 0.25 * (1.0 - r) / 0.5;
        }
        if (r > 0.1) {
            return 1.25 + 0.35 * (0.5 - r) / 0.4;
        }
        return 1.6;
    }

    /** 実際に得た電力の割合から、不完全反応による消費倍率を返す。 */
    public static double underpowerFactor(double suppliedFraction) {
        double threshold = ServerConfig.SPEC.isLoaded() ? ServerConfig.UNDERPOWER_THRESHOLD.get() : 0.8;
        double factor = ServerConfig.SPEC.isLoaded() ? ServerConfig.UNDERPOWER_CONSUMPTION.get() : 1.5;
        return suppliedFraction < threshold ? factor : 1;
    }

    private static double lowerTierSpeed() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.LOWER_TIER_SPEED.get() : 0.5;
    }

    private static double lowerTierConsumption() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.LOWER_TIER_CONSUMPTION.get() : 2.0;
    }
}
