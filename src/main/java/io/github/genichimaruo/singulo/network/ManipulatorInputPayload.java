package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** クライアント → サーバー: グラビトン・マニピュレーターを持って左クリック（right = false）か右クリックを押した・離した。 */
public record ManipulatorInputPayload(boolean right, boolean down) implements CustomPacketPayload {
    public static final Type<ManipulatorInputPayload> TYPE = new Type<>(Singulo.id("manipulator_input"));
    public static final StreamCodec<ByteBuf, ManipulatorInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ManipulatorInputPayload::right,
            ByteBufCodecs.BOOL, ManipulatorInputPayload::down,
            ManipulatorInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ManipulatorInputPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GravitonManipulatorItem.setInput(player, p.right(), p.down());
            }
        });
    }
}
