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
            () -> "2", "2"::equals, "2"::equals);
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
        CHANNEL.messageBuilder(GalaxyPayload.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GalaxyPayload::encode).decoder(GalaxyPayload::decode)
                .consumerMainThread((p, c) -> GalaxyPayload.handle(p, c.get())).add();
        CHANNEL.messageBuilder(EndingPayload.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EndingPayload::encode).decoder(EndingPayload::decode)
                .consumerMainThread((p, c) -> EndingPayload.handle(p, c.get())).add();
        CHANNEL.messageBuilder(RenameAndroidPayload.class, 7, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RenameAndroidPayload::encode).decoder(RenameAndroidPayload::decode)
                .consumerMainThread((p, c) -> RenameAndroidPayload.handle(p, c.get())).add();
    }
    public static void sendToServer(Object packet) { CHANNEL.sendToServer(packet); }
    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (player.connection != null && player.connection.connection.channel() != null)
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static void sendToAllPlayers(Object packet) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) for (ServerPlayer player : server.getPlayerList().getPlayers()) sendToPlayer(player, packet);
    }
    private SinguloNetwork() {}
}
