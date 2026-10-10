package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * エキゾチック物質を少しずつ使う道具の残量（tick）。残量がなくなると持ち物のエキゾチック物質を1個使って補充する。
 * クリエイティブでは減らない。
 */
public final class ExoticCharge {
    private ExoticCharge() {}

    public static int get(ItemStack stack) {
        Integer v = SinguloComponents.get(stack, SinguloComponents.EXOTIC_CHARGE.get());
        return v == null ? 0 : v;
    }

    public static boolean isExoticMatter(ItemStack stack) {
        return stack.is(BuiltInRegistries.ITEM.get(Singulo.id("exotic_matter")));
    }

    /**
     * 1 tick ぶん使う。残量がなければエキゾチック物質1個で perMatter tick ぶん補充する。使えたら true。
     */
    public static boolean draw(Player player, ItemStack stack, int perMatter) {
        return draw(player, stack, perMatter, false);
    }

    /** darkMatter が true なら、エキゾチック物質の代わりに重力閉じ込めタンク（アイテム）のダークマターも使える。 */
    public static boolean draw(Player player, ItemStack stack, int perMatter, boolean darkMatter) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        int charge = get(stack);
        if (charge <= 0) {
            if (!consumeMatter(player) && !(darkMatter && consumeDarkMatter(player))) {
                return false;
            }
            charge = perMatter;
        }
        SinguloComponents.set(stack, SinguloComponents.EXOTIC_CHARGE.get(), charge - 1);
        return true;
    }

    /** 残量があるか、持ち物にエキゾチック物質があるか。 */
    public static boolean available(Player player, ItemStack stack) {
        if (player.getAbilities().instabuild || get(stack) > 0) {
            return true;
        }
        for (ItemStack s : player.getInventory().items) {
            if (isExoticMatter(s)) {
                return true;
            }
        }
        return isExoticMatter(player.getOffhandItem());
    }

    /** エキゾチック物質1個の代わりになるダークマターの量（mB）。 */
    public static final int DARK_MATTER_PER_CHARGE = 250;

    private static boolean consumeDarkMatter(Player player) {
        for (ItemStack s : player.getInventory().items) {
            int amount = SinguloComponents.getOrDefault(s, SinguloComponents.DARK_MATTER.get(), 0);
            if (amount >= DARK_MATTER_PER_CHARGE && s.getCount() == 1) {
                int left = amount - DARK_MATTER_PER_CHARGE;
                if (left > 0) {
                    SinguloComponents.set(s, SinguloComponents.DARK_MATTER.get(), left);
                } else {
                    SinguloComponents.remove(s, SinguloComponents.DARK_MATTER.get());
                }
                return true;
            }
        }
        return false;
    }

    private static boolean consumeMatter(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (isExoticMatter(s)) {
                s.shrink(1);
                return true;
            }
        }
        if (isExoticMatter(player.getOffhandItem())) {
            player.getOffhandItem().shrink(1);
            return true;
        }
        return false;
    }
}
