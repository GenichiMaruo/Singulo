package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;

/** サーバー → クライアント: 創世のエピローグを開く。archivist なら最後の記録者を倒した人だけの一節が加わる。 */
public record EndingPayload(boolean archivist) {
    public static void encode(EndingPayload p, net.minecraft.network.FriendlyByteBuf b) { b.writeBoolean(p.archivist()); }
    public static EndingPayload decode(net.minecraft.network.FriendlyByteBuf b) { return new EndingPayload(b.readBoolean()); }

    static void handle(EndingPayload payload, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.EndingScreen.open(payload.archivist()));
    }
}
