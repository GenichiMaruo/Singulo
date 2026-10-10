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
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/**
 * ブラックホール（稼働中の炉心と野良ブラックホール）が出すうなり（くり返し）。炉心の位置から鳴り、離れるほど小さくなる（32ブロックまで）。
 * 炉心が重いほど少し大きく低い音になる。止まったり読み込みが外れたりすると消える。
 */
final class BlackHoleAmbience {
    private static final Map<BlockPos, Loop> PLAYING = new HashMap<>();

    private BlackHoleAmbience() {}

    /** うなりを鳴らすもの（稼働中の炉心と、野良ブラックホール）。 */
    private interface Source {
        Vec3 center();

        double mass();

        boolean alive();
    }

    static void onClientTick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PLAYING.clear();
            return;
        }
        PLAYING.values().removeIf(Loop::isStopped);
        for (PenroseReactorBlockEntity r : PenroseReactorBlockEntity.clientRunning(mc.level)) {
            start(mc, r.getBlockPos(), new Source() {
                public Vec3 center() {
                    return r.coreCenter();
                }

                public double mass() {
                    return r.mass();
                }

                public boolean alive() {
                    return !r.isRemoved() && r.state() == PenroseReactorBlockEntity.State.RUNNING;
                }
            });
        }
        for (var hole : io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity.clientLoaded(mc.level)) {
            start(mc, hole.getBlockPos(), new Source() {
                public Vec3 center() {
                    return hole.center();
                }

                public double mass() {
                    return hole.mass();
                }

                public boolean alive() {
                    return !hole.isRemoved();
                }
            });
        }
    }

    private static void start(Minecraft mc, BlockPos pos, Source source) {
        if (!PLAYING.containsKey(pos)) {
            Loop loop = new Loop(source);
            PLAYING.put(pos.immutable(), loop);
            mc.getSoundManager().play(loop);
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final Source source;

        Loop(Source source) {
            super(SinguloSounds.BLACK_HOLE_AMBIENT.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.source = source;
            this.looping = true;
            this.delay = 0;
            this.attenuation = Attenuation.LINEAR;
            update();
        }

        private void update() {
            Vec3 c = source.center();
            this.x = c.x;
            this.y = c.y;
            this.z = c.z;
            double heavy = Math.min(1.5, source.mass() / PenroseReactorBlockEntity.MAX_MASS);
            this.volume = (float) (1.6 + 0.8 * heavy);
            this.pitch = (float) Math.max(0.6, 1.0 - 0.2 * heavy);
        }

        @Override
        public void tick() {
            if (!source.alive()) {
                stop();
                return;
            }
            update();
        }
    }
}
