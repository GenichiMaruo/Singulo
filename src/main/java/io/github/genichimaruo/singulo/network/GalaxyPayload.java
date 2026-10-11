package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** サーバー → クライアント: 夜空の銀河（創世炉が生んだもの）。fresh なら最後の1つがゆっくり現れる。 */
public record GalaxyPayload(List<Long> seeds, boolean fresh) implements CustomPacketPayload {
    public static final Type<GalaxyPayload> TYPE = new Type<>(Singulo.id("galaxies"));
    public static final StreamCodec<ByteBuf, GalaxyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), GalaxyPayload::seeds,
            ByteBufCodecs.BOOL, GalaxyPayload::fresh, GalaxyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(GalaxyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.ClientGalaxies.set(payload.seeds(), payload.fresh()));
    }
}
