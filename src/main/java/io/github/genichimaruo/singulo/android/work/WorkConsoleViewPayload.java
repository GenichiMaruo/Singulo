package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.Singulo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;

/** サーバー → クライアント: 作業指令台の画面の様子（{@link WorkConsoleView}）。 */
public record WorkConsoleViewPayload(int containerId, CompoundTag view) {


    public static void encode(WorkConsoleViewPayload p, net.minecraft.network.FriendlyByteBuf b) { b.writeVarInt(p.containerId()); b.writeNbt(p.view()); }
    public static WorkConsoleViewPayload decode(net.minecraft.network.FriendlyByteBuf b) { return new WorkConsoleViewPayload(b.readVarInt(), java.util.Objects.requireNonNull(b.readNbt())); }

    public static void handle(WorkConsoleViewPayload payload, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().player.containerMenu instanceof WorkConsoleMenu menu && menu.containerId == payload.containerId()) {
                menu.receive(payload.view());
            }
        });
    }
}
