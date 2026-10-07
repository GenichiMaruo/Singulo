package io.github.genichimaruo.singulo.wormhole;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * ワームホールの口の対（pair）と、生まれた時刻（ゲーム時間）。同じ pair を持つ2つの口がつながる。
 */
public record WormholeData(long pair, long created) {
    public static final Codec<WormholeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("pair").forGetter(WormholeData::pair),
            Codec.LONG.fieldOf("created").forGetter(WormholeData::created)
    ).apply(i, WormholeData::new));

    public static final StreamCodec<ByteBuf, WormholeData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, WormholeData::pair,
            ByteBufCodecs.VAR_LONG, WormholeData::created,
            WormholeData::new);
}
