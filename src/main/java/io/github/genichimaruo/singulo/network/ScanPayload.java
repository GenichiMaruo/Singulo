package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** サーバー → クライアント: ニュートリノ・スキャナーで見つけた鉱石と遺構のブロック。 */
public record ScanPayload(List<BlockPos> ores, List<BlockPos> ruins) implements CustomPacketPayload {
    public static final Type<ScanPayload> TYPE = new Type<>(Singulo.id("scan"));
    public static final StreamCodec<ByteBuf, ScanPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ScanPayload::ores,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ScanPayload::ruins,
            ScanPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ScanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.NeutrinoOverlay.show(payload.ores(), payload.ruins()));
    }
}
