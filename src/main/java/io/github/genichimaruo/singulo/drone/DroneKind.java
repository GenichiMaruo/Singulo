package io.github.genichimaruo.singulo.drone;

import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * 輸送ドローンの型。ステーションに入れたメモリの型で印刷する。メモリは遺構の警備機ドックの一波を全部倒すと、ときどき出る
 * （上の型は上位の施設のドックからだけ: {@link #rollMemory}）。
 * <ul>
 *   <li>ローター型: 4つのローターで飛ぶ、ふつうの速さ。</li>
 *   <li>ジェット型: 後ろの2つの噴射口で飛ぶ。いちばん速い。</li>
 *   <li>反重力型: 浮かぶビットに囲まれた核が、反重力の場で浮く。牽引の光で箱を吊るす。</li>
 * </ul>
 */
public enum DroneKind {
    ROTOR("drone_memory", 1.0, "drone.rotor"),
    JET("drone_memory_jet", 1.6, "drone.jet"),
    GRAVITY("drone_memory_gravity", 1.25, "drone.hover");

    /** 設計メモリのアイテム ID。 */
    public final String memoryId;
    /** 速さの倍率（ローター型が1）。 */
    public final double speed;
    /** 飛んでいる間のくり返しの音。 */
    public final String sound;

    DroneKind(String memoryId, double speed, String sound) {
        this.memoryId = memoryId;
        this.speed = speed;
        this.sound = sound;
    }

    public static DroneKind byId(int id) {
        return values()[Math.floorMod(id, values().length)];
    }

    /** 設計メモリの型（ドローンのメモリでなければ null）。 */
    @Nullable
    public static DroneKind ofMemory(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        net.minecraft.resources.ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (DroneKind k : values()) {
            if (id.equals(io.github.genichimaruo.singulo.Singulo.id(k.memoryId))) {
                return k;
            }
        }
        return null;
    }

    /**
     * 警備機ドックの一波を全部倒したとき、メモリが出る確率（ドックの段階 1〜3）。上位の施設はドックが多く波も大きいので、少し下げる。
     */
    public static float memoryChance(int tier) {
        return tier >= 3 ? 0.25F : tier == 2 ? 0.35F : 0.4F;
    }

    /**
     * 出たメモリの型（ドックの段階 1〜3）。段階1（地表観測拠点）はローター型だけ。段階2（研究棟）は 35% でジェット型。
     * 段階3（封鎖培養施設・地下アンドロイド研究所）は 20% で反重力型、40% でジェット型、残りはローター型。
     */
    public static DroneKind rollMemory(int tier, RandomSource random) {
        float x = random.nextFloat();
        if (tier >= 3) {
            return x < 0.2F ? GRAVITY : x < 0.6F ? JET : ROTOR;
        }
        return tier == 2 && x < 0.35F ? JET : ROTOR;
    }
}
