package io.github.genichimaruo.singulo.forgecompat;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.network.SinguloNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

/**
 * Forge 版の通信: {@link SinguloNetwork#register} の登録内容から、Forge の通信路（ChannelBuilder の payloadChannel）を作る。
 * 送るのは {@link io.github.genichimaruo.singulo.forgecompat.PacketDistributor}（NeoForge と同じ名前の送り口）から。
 */
public final class Net {
    private Net() {}

    private static Channel<CustomPacketPayload> channel;

    /** Mod の読み込み中に一度だけ呼ぶ。 */
    public static void init() {
        RegisterPayloadHandlersEvent event = new RegisterPayloadHandlersEvent();
        SinguloNetwork.register(event);
        PayloadRegistrar r = event.registrar();
        var play = ChannelBuilder.named(Singulo.id("main")).networkProtocolVersion(1).payloadChannel().play();
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow = play.serverbound();
        for (var e : r.toServer) {
            flow = add(flow, e);
        }
        flow = flow.clientbound();
        for (var e : r.toClient) {
            flow = add(flow, e);
        }
        channel = flow.build();
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> add(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow, PayloadRegistrar.Entry<T> e) {
        return flow.add(e.type(), (StreamCodec<RegistryFriendlyByteBuf, T>) e.codec(),
                (payload, ctx) -> {
                    e.handler().handle(payload, context(ctx));
                    ctx.setPacketHandled(true);
                });
    }

    private static IPayloadContext context(CustomPayloadEvent.Context ctx) {
        return new IPayloadContext() {
            @Override
            public Player player() {
                ServerPlayer sender = ctx.getSender();
                return sender != null ? sender : ClientAccess.player();
            }

            @Override
            public void enqueueWork(Runnable work) {
                ctx.enqueueWork(work);
            }
        };
    }

    public static boolean hasChannel(ServerGamePacketListenerImpl connection, CustomPacketPayload.Type<?> type) {
        return channel != null && channel.isRemotePresent(connection.getConnection());
    }

    static void sendToServer(CustomPacketPayload payload) {
        channel.send(payload, PacketDistributor.SERVER.noArg());
    }

    static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        channel.send(payload, PacketDistributor.PLAYER.with(player));
    }

    static void sendToAllPlayers(CustomPacketPayload payload) {
        channel.send(payload, PacketDistributor.ALL.noArg());
    }

    /** クライアントだけにあるクラスに触る所（サーバーでは読み込まれない）。 */
    private static final class ClientAccess {
        static Player player() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
    }
}
