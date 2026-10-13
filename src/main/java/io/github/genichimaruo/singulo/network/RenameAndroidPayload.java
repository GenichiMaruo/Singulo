package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.AndroidMenu;
import io.github.genichimaruo.singulo.android.AndroidStationMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * クライアント → サーバー: 開いている画面（アンドロイドの GUI か組立ステーションの GUI）のアンドロイドに名前を付ける。
 * 空なら型の愛称に戻す。開いている画面の相手にしか効かない（ほかのアンドロイドの名前は変えられない）。
 */
public record RenameAndroidPayload(int containerId, String name) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 32;
    public static final Type<RenameAndroidPayload> TYPE = new Type<>(Singulo.id("rename_android"));
    public static final StreamCodec<ByteBuf, RenameAndroidPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RenameAndroidPayload::containerId,
            ByteBufCodecs.stringUtf8(MAX_LENGTH * 4), RenameAndroidPayload::name,
            RenameAndroidPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(RenameAndroidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu.containerId != payload.containerId()) {
                return;
            }
            if (player.containerMenu instanceof AndroidMenu menu && menu.stillValid(player)) {
                menu.rename(player, payload.name());
            } else if (player.containerMenu instanceof AndroidStationMenu menu && menu.stillValid(player)) {
                menu.rename(player, payload.name());
            }
        });
    }
}
