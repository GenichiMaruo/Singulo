package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.monitor.FactoryMonitor;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * クライアント → サーバー: 開いている工場モニターの画面を新しくしてほしい（0.21.0。画面が毎秒送る）。
 * モニターがまだあり、プレイヤーがそばにいるときだけ答える（離れた所の様子をのぞけないように）。
 */
public record MonitorRequestPayload(BlockPos monitor) implements CustomPacketPayload {
    public static final Type<MonitorRequestPayload> TYPE = new Type<>(Singulo.id("factory_monitor_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MonitorRequestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MonitorRequestPayload::monitor, MonitorRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(MonitorRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
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
        PacketDistributor.sendToPlayer(player, new MonitorPayload(FactoryMonitor.scan(level, monitor), open));
    }
}
