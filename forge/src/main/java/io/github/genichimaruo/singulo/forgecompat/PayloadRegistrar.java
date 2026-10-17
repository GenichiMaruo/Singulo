package io.github.genichimaruo.singulo.forgecompat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Forge 版: NeoForge の PayloadRegistrar と同じ書き方で、サーバー行き・クライアント行きの通信を並べる。 */
public final class PayloadRegistrar {
    @FunctionalInterface
    public interface Handler<T> {
        void handle(T payload, IPayloadContext context);
    }

    record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                               StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {}

    final List<Entry<?>> toServer = new ArrayList<>();
    final List<Entry<?>> toClient = new ArrayList<>();

    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
        toServer.add(new Entry<>(type, codec, handler));
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> handler) {
        toClient.add(new Entry<>(type, codec, handler));
        return this;
    }
}
