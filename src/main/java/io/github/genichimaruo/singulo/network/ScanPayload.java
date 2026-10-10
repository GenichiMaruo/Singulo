package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import java.util.List;
import net.minecraft.core.BlockPos;

/** サーバー → クライアント: ニュートリノ・スキャナーで見つけた鉱石と遺構のブロック。 */
public record ScanPayload(List<BlockPos> ores, List<BlockPos> ruins) {
    public static void encode(ScanPayload p, net.minecraft.network.FriendlyByteBuf buf) { buf.writeCollection(p.ores(), (b, pos) -> b.writeBlockPos(pos)); buf.writeCollection(p.ruins(), (b, pos) -> b.writeBlockPos(pos)); }
    public static ScanPayload decode(net.minecraft.network.FriendlyByteBuf buf) { return new ScanPayload(buf.readList(b -> b.readBlockPos()), buf.readList(b -> b.readBlockPos())); }

    static void handle(ScanPayload payload, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> io.github.genichimaruo.singulo.client.NeutrinoOverlay.show(payload.ores(), payload.ruins()));
    }
}
