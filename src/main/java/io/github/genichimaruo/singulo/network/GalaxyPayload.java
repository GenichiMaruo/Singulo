package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import java.util.List;

/** サーバー → クライアント: 夜空の銀河（創世炉が生んだもの）。fresh なら最後の1つがゆっくり現れる。 */
public record GalaxyPayload(List<Long> seeds, boolean fresh) {
    public static void encode(GalaxyPayload p, net.minecraft.network.FriendlyByteBuf b) {
        b.writeCollection(p.seeds(), (buf, seed) -> buf.writeVarLong(seed)); b.writeBoolean(p.fresh());
    }
    public static GalaxyPayload decode(net.minecraft.network.FriendlyByteBuf b) {
        return new GalaxyPayload(b.readList(buf -> buf.readVarLong()), b.readBoolean());
    }

    static void handle(GalaxyPayload payload, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.ClientGalaxies.set(payload.seeds(), payload.fresh()));
    }
}
