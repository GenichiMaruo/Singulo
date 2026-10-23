package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.gravity.ZeroGravity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/**
 * 無重力の中の動き（手元のプレイヤーだけ。動きはクライアントで計算される）。ジャンプのキーで上へ、スニークで下へ進み、
 * 上下の勢いは少しずつ弱まる（放っておくと、その高さで漂う）。
 */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class ZeroGravityClient {
    /** 1 tick に足す上下の速さと、上下の勢いの残り方。 */
    private static final double PUSH = 0.04, DAMPING = 0.9;

    private ZeroGravityClient() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.isPaused() || player.getAbilities().flying || !ZeroGravity.weightless(player)) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        double vy = v.y * DAMPING;
        if (mc.options.keyJump.isDown()) {
            vy += PUSH;
        }
        if (mc.options.keyShift.isDown()) {
            vy -= PUSH;
        }
        player.setDeltaMovement(v.x, vy, v.z);
    }
}
