package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Versioned Forge 1.20.1 channel; handlers execute on the game thread. */
public final class SinguloNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Singulo.id("main"),
            () -> "1", "1"::equals, "1"::equals);
    public static void register() {
        CHANNEL.messageBuilder(ToggleAreaPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleAreaPayload::encode).decoder(ToggleAreaPayload::decode)
                .consumerMainThread((p, c) -> ToggleAreaPayload.handle(p, c.get())).add();
        CHANNEL.messageBuilder(RecordsPayload.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RecordsPayload::encode).decoder(RecordsPayload::decode)
                .consumerMainThread((p, c) -> RecordsPayload.handle(p, c.get())).add();
        CHANNEL.messageBuilder(ScanPayload.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScanPayload::encode).decoder(ScanPayload::decode)
                .consumerMainThread((p, c) -> ScanPayload.handle(p, c.get())).add();
        CHANNEL.messageBuilder(SideConfigPayload.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SideConfigPayload::encode).decoder(SideConfigPayload::decode)
                .consumerMainThread((p, c) -> SideConfigPayload.handle(p, c.get())).add();
        CHANNEL.messageBuilder(ManipulatorInputPayload.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ManipulatorInputPayload::encode).decoder(ManipulatorInputPayload::decode)
                .consumerMainThread((p, c) -> ManipulatorInputPayload.handle(p, c.get())).add();
    }
    public static void sendToServer(Object packet) { CHANNEL.sendToServer(packet); }
    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (player.connection != null && player.connection.connection.channel() != null)
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    private SinguloNetwork() {}
}
