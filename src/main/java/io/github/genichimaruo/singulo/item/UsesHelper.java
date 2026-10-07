package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 使用回数つきアイテムの規則（設計書「遺構回収物」「復元と修復」）。
 * <ul>
 *   <li>データ系の原本は使い切っても残り、修復すると使用回数が戻る</li>
 *   <li>部品系の復元品は使い切ると劣化品に戻り、再び復元できる</li>
 *   <li>修復・再復元のたびに最大使用回数が repairWear 倍になり、maxRepairs 回まで</li>
 *   <li>触媒と型の残響の欠片は使い切ると消える</li>
 * </ul>
 */
public final class UsesHelper {
    private UsesHelper() {}

    public static boolean hasUses(ItemStack stack) {
        return stack.getItem() instanceof UsesItem || stack.is(Items.ECHO_SHARD);
    }

    /** 新品の最大使用回数（設定の倍率込み）。 */
    public static int baseMax(ItemStack stack) {
        if (stack.getItem() instanceof CreativeCatalystItem) {
            return 1_000_000;
        }
        if (stack.getItem() instanceof UsesItem uses) {
            if (uses.isCatalyst()) {
                return Math.max(1, (int) Math.round(catalystMinutes(uses) * 1200 * catalystLifetimeMultiplier()));
            }
            return Math.max(1, (int) Math.round(uses.defaultMax() * usesMultiplier()));
        }
        if (stack.is(Items.ECHO_SHARD)) {
            return configLoaded() ? ServerConfig.TEMPLATE_USES.get() : 16;
        }
        return 0;
    }

    public static UsesData get(ItemStack stack) {
        UsesData data = stack.get(SinguloComponents.USES.get());
        return data != null ? data : new UsesData(0, baseMax(stack), 0);
    }

    public static int remaining(ItemStack stack) {
        return hasUses(stack) ? get(stack).remaining() : 0;
    }

    public static boolean canUse(ItemStack stack, int amount) {
        return hasUses(stack) && stack.getCount() == 1 && remaining(stack) >= amount;
    }

    /** amount 回ぶん使い、置き換え後のスタック（1個）を返す。 */
    public static ItemStack consume(ItemStack stack, int amount) {
        if (stack.getItem() instanceof CreativeCatalystItem) {
            return stack.copyWithCount(1);                  // 無限の触媒は減らない
        }
        UsesData data = get(stack);
        UsesData next = new UsesData(data.used() + amount, data.max(), data.repairs());
        if (next.remaining() > 0) {
            ItemStack copy = stack.copyWithCount(1);
            copy.set(SinguloComponents.USES.get(), next);
            return copy;
        }
        return exhausted(stack, next);
    }

    private static ItemStack exhausted(ItemStack stack, UsesData data) {
        if (!(stack.getItem() instanceof UsesItem uses)) {
            return ItemStack.EMPTY;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (uses.isCatalyst()) {
            // 触媒は失活触媒になる
            String spent = GeneratedContent.SPENT_FORM.get(id);
            return spent == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(spent)));
        }
        String worn = GeneratedContent.WORN_FORM.get(id);
        if (worn != null) {
            // 復元品は劣化品に戻る。次の復元は修復1回ぶんとして数える
            Item wornItem = BuiltInRegistries.ITEM.get(Singulo.id(worn));
            ItemStack out = new ItemStack(wornItem);
            out.set(SinguloComponents.USES.get(), new UsesData(0, 0, data.repairs() + 1));
            return out;
        }
        // データ系の原本は使い切った状態で残る
        ItemStack copy = stack.copyWithCount(1);
        copy.set(SinguloComponents.USES.get(), data);
        return copy;
    }

    /** この入力を修復・復元できるか。 */
    public static boolean canRestore(ItemStack input) {
        if (input.isEmpty()) {
            return false;
        }
        int repairs = repairsOf(input);
        if (input.getItem() instanceof UsesItem) {
            // 原本の修復は、使い切ったときだけ
            return remaining(input) == 0 && repairs < maxRepairs();
        }
        // 劣化品の復元（遺構から拾った直後は repairs = 0）
        return repairs <= maxRepairs();
    }

    /** 修復・復元の成果物。result は復元後のアイテム（原本の修復なら同じアイテム）。 */
    public static ItemStack restore(ItemStack input, ItemStack result) {
        int repairs = input.getItem() instanceof UsesItem ? repairsOf(input) + 1 : repairsOf(input);
        ItemStack out = result.copyWithCount(1);
        int max = Math.max(1, (int) Math.floor(baseMax(out) * Math.pow(repairWear(), repairs)));
        out.set(SinguloComponents.USES.get(), new UsesData(0, max, repairs));
        return out;
    }

    public static int repairsOf(ItemStack stack) {
        UsesData data = stack.get(SinguloComponents.USES.get());
        return data == null ? 0 : data.repairs();
    }

    // ---- 設定（サーバー設定を読んでいないクライアントでは既定値を使う）

    static boolean configLoaded() {
        return ServerConfig.SPEC.isLoaded();
    }

    public static int maxRepairs() {
        return configLoaded() ? ServerConfig.MAX_REPAIRS.get() : 3;
    }

    static double repairWear() {
        return configLoaded() ? ServerConfig.REPAIR_WEAR.get() : 0.75;
    }

    static double usesMultiplier() {
        return configLoaded() ? ServerConfig.USES_MULTIPLIER.get() : 1.0;
    }

    static double catalystLifetimeMultiplier() {
        return configLoaded() ? ServerConfig.LIFETIME_MULTIPLIER.get() : 1.0;
    }

    static double catalystMinutes(UsesItem item) {
        if (!configLoaded()) {
            return item.defaultMax() / 1200.0;
        }
        return switch (BuiltInRegistries.ITEM.getKey(item).getPath()) {
            case "muon_catalyst" -> ServerConfig.MUON_LIFETIME_MINUTES.get();
            case "bose_condensate_catalyst" -> ServerConfig.BOSE_LIFETIME_MINUTES.get();
            case "time_crystal_catalyst" -> ServerConfig.TIME_CRYSTAL_LIFETIME_MINUTES.get();
            case "singularity_core" -> ServerConfig.SINGULARITY_LIFETIME_MINUTES.get();
            default -> item.defaultMax() / 1200.0;
        };
    }
}
