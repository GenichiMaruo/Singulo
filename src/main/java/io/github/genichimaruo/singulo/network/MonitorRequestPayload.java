package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.monitor.FactoryMonitor;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * クライアント → サーバー: 開いている工場モニターの画面を新しくしてほしい（0.21.0。画面が毎秒送る）。
 * モニターがまだあり、プレイヤーがそばにいるときだけ答える（離れた所の様子をのぞけないように）。
 */
public record MonitorRequestPayload(BlockPos monitor) {
    static void encode(MonitorRequestPayload p, FriendlyByteBuf b) {
        b.writeBlockPos(p.monitor());
    }

    static MonitorRequestPayload decode(FriendlyByteBuf b) {
        return new MonitorRequestPayload(b.readBlockPos());
    }

    static void handle(MonitorRequestPayload payload, NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                send(player, payload.monitor(), false);
            }
        });
    }

    /** モニターを使える所にいれば、まとめを送る（open なら画面を開かせる）。 */
    public static void send(ServerPlayer player, BlockPos monitor, boolean open) {
        var level = player.serverLevel();
        if (!level.isLoaded(monitor) || !level.getBlockState(monitor).is(SinguloBlocks.DESK_MONITOR.get())
                || player.distanceToSqr(monitor.getCenter()) > FactoryMonitor.USE_DISTANCE * FactoryMonitor.USE_DISTANCE) {
            return;
        }
        SinguloNetwork.sendToPlayer(player, new MonitorPayload(FactoryMonitor.scan(level, monitor), open));
    }
}
