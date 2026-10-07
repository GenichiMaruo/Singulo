package io.github.genichimaruo.singulo.registry;

import com.mojang.serialization.Codec;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.UsesData;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SinguloComponents {
    public static final DeferredRegister.DataComponents REGISTER = DeferredRegister.createDataComponents(Singulo.MODID);

    /** 使用回数（遺構回収物・復元品・触媒・型の欠片）。 */
    public static final Supplier<DataComponentType<UsesData>> USES = REGISTER.registerComponentType("uses",
            b -> b.persistent(UsesData.CODEC).networkSynchronized(UsesData.STREAM_CODEC));

    /** 道具に蓄えた電力（FE）。 */
    public static final Supplier<DataComponentType<Integer>> ENERGY = REGISTER.registerComponentType("energy",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** 3種類以上の元ブロックから作った圧縮ブロックLv1（混成ボーナスの対象）。 */
    public static final Supplier<DataComponentType<Boolean>> MIXED_SOURCE = REGISTER.registerComponentType("mixed_source",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** 重力操作道具のモード（GravityMode の番号）。 */
    public static final Supplier<DataComponentType<Integer>> GRAVITY_MODE = REGISTER.registerComponentType("gravity_mode",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** エキゾチック物質を使う道具の残量（tick）。 */
    public static final Supplier<DataComponentType<Integer>> EXOTIC_CHARGE = REGISTER.registerComponentType("exotic_charge",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** 重力閉じ込めタンクに入っているダークマター（mB）。 */
    public static final Supplier<DataComponentType<Integer>> DARK_MATTER = REGISTER.registerComponentType("dark_matter",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** ワームホールの口の対と生まれた時刻。 */
    public static final Supplier<DataComponentType<io.github.genichimaruo.singulo.wormhole.WormholeData>> WORMHOLE =
            REGISTER.registerComponentType("wormhole", b -> b.persistent(io.github.genichimaruo.singulo.wormhole.WormholeData.CODEC)
                    .networkSynchronized(io.github.genichimaruo.singulo.wormhole.WormholeData.STREAM_CODEC));

    /** 重力操作道具の範囲（true なら前方の円錐、false なら視線上の1体）。 */
    public static final Supplier<DataComponentType<Boolean>> CONE = REGISTER.registerComponentType("cone",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** ホロ投影機で選んでいる大きさ（選択肢の番号）。 */
    public static final Supplier<DataComponentType<Integer>> HOLO_SIZE = REGISTER.registerComponentType("holo_size",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** 無限の触媒のティア。 */
    public static final Supplier<DataComponentType<Integer>> CATALYST_TIER = REGISTER.registerComponentType("catalyst_tier",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private SinguloComponents() {}
}
