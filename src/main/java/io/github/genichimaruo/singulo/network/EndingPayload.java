package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** サーバー → クライアント: 創世のエピローグを開く。archivist なら最後の記録者を倒した人だけの一節が加わる。 */
public record EndingPayload(boolean archivist) implements CustomPacketPayload {
    public static final Type<EndingPayload> TYPE = new Type<>(Singulo.id("ending"));
    public static final StreamCodec<ByteBuf, EndingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, EndingPayload::archivist, EndingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(EndingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.EndingScreen.open(payload.archivist()));
    }
}
