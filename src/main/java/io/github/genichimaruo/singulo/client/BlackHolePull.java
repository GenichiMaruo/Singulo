package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 自分のプレイヤーへのブラックホールの引力（クライアントで毎tick）。
 * プレイヤーの動きはクライアントが決めるので、サーバーから速さを送るより、ここで同じ式の引力を足すほうがなめらかになる。
 * 事象の地平線での消滅はサーバーが判定する。
 */
final class BlackHolePull {
    private BlackHolePull() {}

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || player.isCreative() || player.isSpectator()
                || player.isPassenger()) {
            return;
        }
        for (PenroseReactorBlockEntity r : PenroseReactorBlockEntity.clientRunning(mc.level)) {
            Vec3 c = r.coreCenter();
            Vec3 v = PenroseReactorBlockEntity.pulledVelocity(player.getDeltaMovement(), player.getBoundingBox().getCenter(), c);
            if (v != null) {
                player.setDeltaMovement(v);
                if (player.getBoundingBox().getCenter().distanceTo(c) <= PenroseReactorBlockEntity.INNER_RADIUS) {
                    player.resetFallDistance();
                }
            }
        }
    }
}
