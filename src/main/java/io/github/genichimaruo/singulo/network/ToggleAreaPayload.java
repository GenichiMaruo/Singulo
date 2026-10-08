package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** クライアント → サーバー: 手に持った重力操作道具の範囲（1体・円錐）を切り替える。 */
public record ToggleAreaPayload() implements CustomPacketPayload {
    public static final ToggleAreaPayload INSTANCE = new ToggleAreaPayload();
    public static final Type<ToggleAreaPayload> TYPE = new Type<>(Singulo.id("toggle_area"));
    public static final StreamCodec<ByteBuf, ToggleAreaPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ToggleAreaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof GravityGauntletItem g && g.supportsCone()) {
                    g.toggleCone(player, stack);
                    return;
                }
            }
        });
    }
}
