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

    /** 流体の見本（作業の自動化で、液体・ガスを物のように扱う目印）の、流体の種類と量。 */
    public static final Supplier<DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> FLUID_SAMPLE =
            REGISTER.registerComponentType("fluid_sample", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                    .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    /** 液体キャニスターの中身。 */
    public static final Supplier<DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> CANISTER_FLUID =
            REGISTER.registerComponentType("canister_fluid", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                    .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    /** 化学物質の見本（Mekanism の化学物質を、作業の自動化で物のように扱う目印）の、化学物質の ID と量。 */
    public static final Supplier<DataComponentType<io.github.genichimaruo.singulo.android.work.ChemicalSamples.Content>> CHEMICAL_SAMPLE =
            REGISTER.registerComponentType("chemical_sample", b -> b.persistent(io.github.genichimaruo.singulo.android.work.ChemicalSamples.Content.CODEC)
                    .networkSynchronized(io.github.genichimaruo.singulo.android.work.ChemicalSamples.Content.STREAM_CODEC));

    /** ホロ投影機で選んでいる大きさ（選択肢の番号）。 */
    public static final Supplier<DataComponentType<Integer>> HOLO_SIZE = REGISTER.registerComponentType("holo_size",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** 探索コンパスの調整段階（探せる遺構の数、1〜4）。 */
    public static final Supplier<DataComponentType<Integer>> COMPASS_LEVEL = REGISTER.registerComponentType("compass_level",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** ニュートリノ・スキャナーの感度の段階（1〜3）。 */
    public static final Supplier<DataComponentType<Integer>> SCANNER_TIER = REGISTER.registerComponentType("scanner_tier",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** 触媒安定化剤を使った触媒（もう使えない）。 */
    public static final Supplier<DataComponentType<Boolean>> STABILIZED = REGISTER.registerComponentType("stabilized",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** 無限の触媒のティア。 */
    public static final Supplier<DataComponentType<Integer>> CATALYST_TIER = REGISTER.registerComponentType("catalyst_tier",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private SinguloComponents() {}
}
