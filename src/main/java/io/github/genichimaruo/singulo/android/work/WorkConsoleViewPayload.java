package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.Singulo;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** サーバー → クライアント: 作業指令台の画面の様子（{@link WorkConsoleView}）。 */
public record WorkConsoleViewPayload(int containerId, CompoundTag view) implements CustomPacketPayload {
    public static final Type<WorkConsoleViewPayload> TYPE = new Type<>(Singulo.id("work_console_view"));
    public static final StreamCodec<ByteBuf, WorkConsoleViewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WorkConsoleViewPayload::containerId,
            ByteBufCodecs.COMPOUND_TAG, WorkConsoleViewPayload::view,
            WorkConsoleViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WorkConsoleViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof WorkConsoleMenu menu && menu.containerId == payload.containerId()) {
                menu.receive(payload.view());
            }
        });
    }
}
