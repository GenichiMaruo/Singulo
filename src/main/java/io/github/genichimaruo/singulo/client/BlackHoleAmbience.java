package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 稼働中のリアクターのブラックホールが出すうなり（くり返し）。炉心の位置から鳴り、離れるほど小さくなる（32ブロックまで）。
 * 炉心が重いほど少し大きく低い音になる。止まったり読み込みが外れたりすると消える。
 */
final class BlackHoleAmbience {
    private static final Map<BlockPos, Loop> PLAYING = new HashMap<>();

    private BlackHoleAmbience() {}

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PLAYING.clear();
            return;
        }
        PLAYING.values().removeIf(Loop::isStopped);
        for (PenroseReactorBlockEntity r : PenroseReactorBlockEntity.clientRunning(mc.level)) {
            if (!PLAYING.containsKey(r.getBlockPos())) {
                Loop loop = new Loop(r);
                PLAYING.put(r.getBlockPos(), loop);
                mc.getSoundManager().play(loop);
            }
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final PenroseReactorBlockEntity reactor;

        Loop(PenroseReactorBlockEntity reactor) {
            super(SinguloSounds.BLACK_HOLE_AMBIENT.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.reactor = reactor;
            this.looping = true;
            this.delay = 0;
            this.attenuation = Attenuation.LINEAR;
            update();
        }

        private void update() {
            Vec3 c = reactor.coreCenter();
            this.x = c.x;
            this.y = c.y;
            this.z = c.z;
            double heavy = Math.min(1.0, reactor.mass() / PenroseReactorBlockEntity.MAX_MASS);
            this.volume = (float) (1.6 + 0.8 * heavy);
            this.pitch = (float) (1.0 - 0.2 * heavy);
        }

        @Override
        public void tick() {
            if (reactor.isRemoved() || reactor.state() != PenroseReactorBlockEntity.State.RUNNING) {
                stop();
                return;
            }
            update();
        }
    }
}
