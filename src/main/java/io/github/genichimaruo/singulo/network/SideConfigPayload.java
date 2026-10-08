package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.SideConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * クライアント → サーバー: 装置の面の設定を変える（channel 0 はアイテム、1 は液体。value は下位2ビットがモード、4 が自動排出）。
 * face が MASTER_FACE のときは自動排出の全体スイッチ（value の4がオン）。
 */
public record SideConfigPayload(BlockPos pos, int channel, int face, int value) implements CustomPacketPayload {
    public static final int MASTER_FACE = 6;
    public static final Type<SideConfigPayload> TYPE = new Type<>(Singulo.id("side_config"));
    public static final StreamCodec<ByteBuf, SideConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SideConfigPayload::pos,
            ByteBufCodecs.VAR_INT, SideConfigPayload::channel,
            ByteBufCodecs.VAR_INT, SideConfigPayload::face,
            ByteBufCodecs.VAR_INT, SideConfigPayload::value,
            SideConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SideConfigPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (p.face < 0 || p.face > MASTER_FACE || p.channel < 0 || p.channel > 1
                    || player.distanceToSqr(p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5) > 64
                    || !(player.level().getBlockEntity(p.pos) instanceof MachineBlockEntity m)) {
                return;
            }
            if (p.face == MASTER_FACE) {
                m.setEjectEnabled(p.channel, (p.value & 4) != 0);
            } else {
                m.setSide(p.channel, SideConfig.Face.values()[p.face], p.value & 3, (p.value & 4) != 0);
            }
        });
    }
}
