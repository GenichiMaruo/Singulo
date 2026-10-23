package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.android.work.SurveyGlassesItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

/** Client-to-server adjustment of the survey glasses held in the selected hand. */
public record GlassesHeightPayload(boolean offHand, int height) {
    public static void encode(GlassesHeightPayload p, FriendlyByteBuf b) { b.writeBoolean(p.offHand()); b.writeVarInt(p.height()); }
    public static GlassesHeightPayload decode(FriendlyByteBuf b) { return new GlassesHeightPayload(b.readBoolean(), b.readVarInt()); }
    static void handle(GlassesHeightPayload p, NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            var stack = player.getItemInHand(p.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (stack.getItem() instanceof SurveyGlassesItem) SurveyGlassesItem.setHeight(stack, p.height());
        });
    }
}
