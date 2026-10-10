package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** クライアント → サーバー: 手に持った重力操作道具の範囲（1体・円錐）を切り替える。 */
public record ToggleAreaPayload() {
    public static final ToggleAreaPayload INSTANCE = new ToggleAreaPayload();
    public static void encode(ToggleAreaPayload p, net.minecraft.network.FriendlyByteBuf buf) {  }
    public static ToggleAreaPayload decode(net.minecraft.network.FriendlyByteBuf buf) { return INSTANCE; }

    static void handle(ToggleAreaPayload payload, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            Player player = context.getSender();
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
