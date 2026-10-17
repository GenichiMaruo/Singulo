package io.github.genichimaruo.singulo.forgecompat;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Forge 版: NeoForge の PacketDistributor と同じ名前で使える、通信の送り口（中身は {@link Net}）。 */
public final class PacketDistributor {
    private PacketDistributor() {}

    public static void sendToServer(CustomPacketPayload payload) {
        Net.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        Net.sendToPlayer(player, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        Net.sendToAllPlayers(payload);
    }
}
