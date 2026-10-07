package io.github.genichimaruo.singulo.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 使用回数の状態。
 *
 * @param used    使った回数（触媒は経過tick）
 * @param max     今の最大使用回数。修復のたびに repairWear 倍に縮む
 * @param repairs 修復・再復元した回数
 */
public record UsesData(int used, int max, int repairs) {
    public static final Codec<UsesData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("used").forGetter(UsesData::used),
            Codec.INT.fieldOf("max").forGetter(UsesData::max),
            Codec.INT.optionalFieldOf("repairs", 0).forGetter(UsesData::repairs)
    ).apply(i, UsesData::new));

    public static final StreamCodec<ByteBuf, UsesData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UsesData::used,
            ByteBufCodecs.VAR_INT, UsesData::max,
            ByteBufCodecs.VAR_INT, UsesData::repairs,
            UsesData::new);

    public int remaining() {
        return Math.max(0, max - used);
    }
}
