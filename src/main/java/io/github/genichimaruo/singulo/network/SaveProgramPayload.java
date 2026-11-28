package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.program.Program;
import io.github.genichimaruo.singulo.android.program.ProgramCardItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * クライアント → サーバー: 書いた文をプログラムカードに保存する（0.24.0）。containerId が -1 なら手に持ったカード、
 * そうでなければ、その番号の組立ステーションの画面を開いているときの、ステーションのプログラムの欄のカード。
 * 書き方の誤りがあっても保存する（カードの説明とステーションに誤りが出て、アンドロイドは動かない）。
 */
public record SaveProgramPayload(int containerId, boolean offHand, String source) implements CustomPacketPayload {
    public static final Type<SaveProgramPayload> TYPE = new Type<>(Singulo.id("save_program"));
    public static final StreamCodec<ByteBuf, SaveProgramPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SaveProgramPayload::containerId,
            ByteBufCodecs.BOOL, SaveProgramPayload::offHand,
            ByteBufCodecs.stringUtf8(Program.MAX_SOURCE), SaveProgramPayload::source,
            SaveProgramPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SaveProgramPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.source().length() > Program.MAX_SOURCE) {
                return;
            }
            var player = context.player();
            if (payload.containerId() >= 0) {
                if (player.containerMenu.containerId == payload.containerId()
                        && player.containerMenu instanceof io.github.genichimaruo.singulo.android.AndroidStationMenu menu && menu.stillValid(player)) {
                    menu.saveProgram(player, payload.source());
                }
                return;
            }
            ItemStack stack = context.player().getItemInHand(payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (ProgramCardItem.isCard(stack) && payload.source().length() <= Program.MAX_SOURCE) {
                ProgramCardItem.setSource(stack, payload.source());
            }
        });
    }
}
