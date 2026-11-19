package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.monitor.FactoryMonitor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** サーバー → クライアント: 工場モニターのまとめ（0.21.0）。open なら画面を開き、そうでなければ開いている画面を新しくする。 */
public record MonitorPayload(FactoryMonitor.Snapshot snapshot, boolean open) implements CustomPacketPayload {
    public static final Type<MonitorPayload> TYPE = new Type<>(Singulo.id("factory_monitor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MonitorPayload> STREAM_CODEC = StreamCodec.composite(
            FactoryMonitor.Snapshot.CODEC, MonitorPayload::snapshot, ByteBufCodecs.BOOL, MonitorPayload::open, MonitorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(MonitorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.FactoryMonitorScreen.receive(payload.snapshot(), payload.open()));
    }
}
