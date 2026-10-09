package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.item.GravityBootsItem;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 重力ブーツの壁蹴り（クライアント）。空中で壁に触れているときにジャンプを押し直すと、壁から離れる向きへ跳ぶ。
 * 着地するまでに GravityBootsItem.WALL_JUMPS 回まで。プレイヤーの動きはクライアントが決めるので、ここで速度を変えればよい
 * （落下ダメージはサーバー側の GravityBootsItem.onFall で消す）。
 */
final class GravityBootsClient {
    private static boolean jumpWasDown;
    private static int jumpsLeft;
    private static int cooldown;

    private GravityBootsClient() {}

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused()) {
            return;
        }
        boolean down = mc.options.keyJump.isDown();
        boolean pressed = down && !jumpWasDown;
        jumpWasDown = down;
        if (cooldown > 0) {
            cooldown--;
        }
        if (!GravityBootsItem.wearing(player) || player.getAbilities().flying) {
            return;
        }
        if (player.onGround()) {
            jumpsLeft = GravityBootsItem.WALL_JUMPS;
            return;
        }
        if (!pressed || cooldown > 0 || jumpsLeft <= 0 || !player.horizontalCollision) {
            return;
        }
        // 壁から離れる向き: 見ている向きの反対（水平）
        Vec3 look = player.getLookAngle();
        Vec3 away = new Vec3(-look.x, 0, -look.z);
        away = away.lengthSqr() < 1e-4 ? Vec3.ZERO : away.normalize().scale(0.45);
        player.setDeltaMovement(away.x, 0.62, away.z);
        player.fallDistance = 0;
        jumpsLeft--;
        cooldown = 6;
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SinguloSounds.get("gravity_boots.wall_jump"),
                SoundSource.PLAYERS, 0.8F, 1.0F + (GravityBootsItem.WALL_JUMPS - jumpsLeft) * 0.1F, false);
        for (int i = 0; i < 6; i++) {
            player.level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, player.getX(), player.getY() + 0.1,
                    player.getZ(), (player.getRandom().nextDouble() - 0.5) * 0.2, 0.02, (player.getRandom().nextDouble() - 0.5) * 0.2);
        }
    }
}
