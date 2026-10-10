package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import java.util.List;

/** サーバー → クライアント: 解読した旧文明の記録の一覧。 */
public record RecordsPayload(List<String> decoded) {
    public static void encode(RecordsPayload p, net.minecraft.network.FriendlyByteBuf buf) { buf.writeCollection(p.decoded(), (b, s) -> b.writeUtf(s)); }
    public static RecordsPayload decode(net.minecraft.network.FriendlyByteBuf buf) { return new RecordsPayload(buf.readList(b -> b.readUtf())); }

    static void handle(RecordsPayload payload, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.ClientRecords.set(payload.decoded()));
    }
}
