package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** サーバー → クライアント: 解読した旧文明の記録の一覧。 */
public record RecordsPayload(List<String> decoded) implements CustomPacketPayload {
    public static final Type<RecordsPayload> TYPE = new Type<>(Singulo.id("records"));
    public static final StreamCodec<ByteBuf, RecordsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RecordsPayload::decoded, RecordsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(RecordsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.ClientRecords.set(payload.decoded()));
    }
}
