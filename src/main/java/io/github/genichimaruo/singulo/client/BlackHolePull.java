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
        if (player.isFallFlying()) {
            // 重力スリングショット: 滑空中は吸い込まれるかわりに、そばを通り抜けると加速する
            for (var rogue : io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity.clientLoaded(mc.level)) {
                slingshot(player, rogue.center(), rogue.horizonRadius(), rogue.pullScale());
            }
            for (PenroseReactorBlockEntity r : PenroseReactorBlockEntity.clientRunning(mc.level)) {
                slingshot(player, r.coreCenter(), r.horizonRadius(), 1.0);
            }
            return;
        }
        for (var rogue : io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity.clientLoaded(mc.level)) {
            Vec3 c = rogue.center();
            Vec3 v = PenroseReactorBlockEntity.pulledVelocity(player.getDeltaMovement(), player.getBoundingBox().getCenter(), c,
                    rogue.innerRadius(), rogue.pullScale());
            if (v != null) {
                player.setDeltaMovement(v);
            }
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

    /** スリングショットが効く距離（地平線からの倍率と、上限）。 */
    static final double SLINGSHOT_MIN = 2.5;
    static final double SLINGSHOT_MAX = 28;
    static final double SLINGSHOT_SPEED_CAP = 3.6;

    /**
     * 滑空中のプレイヤーが、ブラックホールのそばを通ると進む向きに加速し、軌道が少しだけ中心へ曲がる（近いほど強い）。
     * 加速している間は光の筋が後ろに残る。
     */
    private static void slingshot(LocalPlayer player, Vec3 c, double horizon, double scale) {
        Vec3 body = player.getBoundingBox().getCenter();
        Vec3 to = c.subtract(body);
        double d = to.length();
        if (d < horizon * SLINGSHOT_MIN || d > SLINGSHOT_MAX) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        double speed = v.length();
        if (speed < 0.2) {
            return;
        }
        double near = Math.min(1.0, 36.0 / (d * d));
        Vec3 boosted = v.add(v.scale(0.045 * scale * near / speed)).add(to.scale(0.012 * scale * near / d));
        if (boosted.length() > SLINGSHOT_SPEED_CAP) {
            boosted = boosted.normalize().scale(SLINGSHOT_SPEED_CAP);
        }
        player.setDeltaMovement(boosted);
        if (near > 0.15 && player.level().random.nextInt(2) == 0) {
            player.level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, body.x, body.y, body.z,
                    -v.x * 0.1, -v.y * 0.1, -v.z * 0.1);
        }
    }
}
