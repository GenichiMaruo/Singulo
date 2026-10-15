package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidMenu;
import io.github.genichimaruo.singulo.android.AndroidStationMenu;
import net.minecraft.world.entity.player.Player;

/**
 * クライアント → サーバー: 開いている画面（アンドロイドの GUI か組立ステーションの GUI）のアンドロイドに名前を付ける。
 * 空なら型の愛称に戻す。開いている画面の相手にしか効かない（ほかのアンドロイドの名前は変えられない）。
 */
public record RenameAndroidPayload(int containerId, String name) {
    public static final int MAX_LENGTH = 32;
    public static void encode(RenameAndroidPayload p, net.minecraft.network.FriendlyByteBuf b) { b.writeVarInt(p.containerId()); b.writeUtf(p.name(), MAX_LENGTH * 4); }
    public static RenameAndroidPayload decode(net.minecraft.network.FriendlyByteBuf b) { return new RenameAndroidPayload(b.readVarInt(), b.readUtf(MAX_LENGTH * 4)); }

    static void handle(RenameAndroidPayload payload, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player == null) return;
            if (player.containerMenu.containerId != payload.containerId()) {
                return;
            }
            if (player.containerMenu instanceof AndroidMenu menu && menu.stillValid(player)) {
                menu.rename(player, payload.name());
            } else if (player.containerMenu instanceof AndroidStationMenu menu && menu.stillValid(player)) {
                menu.rename(player, payload.name());
            } else if (player.containerMenu instanceof io.github.genichimaruo.singulo.android.work.DeliveryPortMenu menu
                    && menu.stillValid(player)) {
                menu.rename(player, payload.name());
            }
        });
    }
}
