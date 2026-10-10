package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import net.minecraft.server.level.ServerPlayer;

/** クライアント → サーバー: グラビトン・マニピュレーターを持って左クリック（right = false）か右クリックを押した・離した。 */
public record ManipulatorInputPayload(boolean right, boolean down) {
    public static void encode(ManipulatorInputPayload p, net.minecraft.network.FriendlyByteBuf buf) { buf.writeBoolean(p.right()); buf.writeBoolean(p.down()); }
    public static ManipulatorInputPayload decode(net.minecraft.network.FriendlyByteBuf buf) { return new ManipulatorInputPayload(buf.readBoolean(), buf.readBoolean()); }

    static void handle(ManipulatorInputPayload p, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                GravitonManipulatorItem.setInput(player, p.right(), p.down());
            }
        });
    }
}
