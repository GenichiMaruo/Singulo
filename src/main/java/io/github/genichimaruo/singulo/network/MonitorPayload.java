package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.monitor.FactoryMonitor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** サーバー → クライアント: 工場モニターのまとめ（0.21.0）。open なら画面を開き、そうでなければ開いている画面を新しくする。 */
public record MonitorPayload(FactoryMonitor.Snapshot snapshot, boolean open) {
    static void encode(MonitorPayload p, FriendlyByteBuf b) {
        FactoryMonitor.Snapshot.encode(p.snapshot(), b);
        b.writeBoolean(p.open());
    }

    static MonitorPayload decode(FriendlyByteBuf b) {
        return new MonitorPayload(FactoryMonitor.Snapshot.decode(b), b.readBoolean());
    }

    static void handle(MonitorPayload payload, NetworkEvent.Context context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.FactoryMonitorScreen.receive(payload.snapshot(), payload.open()));
    }
}
