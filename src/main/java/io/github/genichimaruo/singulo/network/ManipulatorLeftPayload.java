package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** クライアント → サーバー: グラビトン・マニピュレーターを持って左クリックを押した・離した。 */
public record ManipulatorLeftPayload(boolean down) implements CustomPacketPayload {
    public static final Type<ManipulatorLeftPayload> TYPE = new Type<>(Singulo.id("manipulator_left"));
    public static final StreamCodec<ByteBuf, ManipulatorLeftPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(ManipulatorLeftPayload::new, ManipulatorLeftPayload::down);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ManipulatorLeftPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GravitonManipulatorItem.setLeftDown(player, p.down());
            }
        });
    }
}
