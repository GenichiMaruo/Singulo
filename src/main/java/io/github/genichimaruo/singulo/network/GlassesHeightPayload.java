package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.work.SurveyGlassesItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** クライアント → サーバー: 手に持った測量メガネを掛ける高さ（ピクセル）を決める。 */
public record GlassesHeightPayload(boolean offHand, int height) implements CustomPacketPayload {
    public static final Type<GlassesHeightPayload> TYPE = new Type<>(Singulo.id("glasses_height"));
    public static final StreamCodec<ByteBuf, GlassesHeightPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GlassesHeightPayload::offHand,
            ByteBufCodecs.VAR_INT, GlassesHeightPayload::height,
            GlassesHeightPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(GlassesHeightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = context.player().getItemInHand(payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (stack.getItem() instanceof SurveyGlassesItem) {
                SurveyGlassesItem.setHeight(stack, payload.height());
            }
        });
    }
}
