package io.github.genichimaruo.singulo.forgecompat;

import net.minecraft.world.entity.player.Player;

/** Forge 版: NeoForge の IPayloadContext と同じ形で使える、受け取った通信の状況（送り手と、メインスレッドでの実行）。 */
public interface IPayloadContext {
    /** サーバーでは送ってきたプレイヤー、クライアントでは自分。 */
    Player player();

    void enqueueWork(Runnable work);
}
