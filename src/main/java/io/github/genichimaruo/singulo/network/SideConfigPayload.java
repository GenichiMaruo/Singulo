package io.github.genichimaruo.singulo.network;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * クライアント → サーバー: 装置の面の設定を変える（channel 0 はアイテム、1 以降はタンクごと（1 + タンクの番号）。
 * value は下位2ビットがモード、4 が自動排出）。
 * face が MASTER_FACE のときは自動排出の全体スイッチ（value の4がオン）。
 */
public record SideConfigPayload(BlockPos pos, int channel, int face, int value) {
    public static final int MASTER_FACE = 6;
    public static void encode(SideConfigPayload p, net.minecraft.network.FriendlyByteBuf buf) { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.channel()); buf.writeVarInt(p.face()); buf.writeVarInt(p.value()); }
    public static SideConfigPayload decode(net.minecraft.network.FriendlyByteBuf buf) { return new SideConfigPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()); }

    static void handle(SideConfigPayload p, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (p.face < 0 || p.face > MASTER_FACE
                    || player.distanceToSqr(p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5) > 64
                    || !(player.level().getBlockEntity(p.pos) instanceof MachineBlockEntity m) || !m.validChannel(p.channel)) {
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
