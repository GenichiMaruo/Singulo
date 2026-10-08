package io.github.genichimaruo.singulo.multiblock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * マルチブロックの形成演出: 正しく組み上がると、コントローラから近い順に部品の継ぎ目へ白い光が走り、起動音が鳴る。
 * 読み込み直したときの最初の判定では鳴らさない（firstCheck）。
 */
public final class FormationEffect {
    private record Spark(ServerLevel level, BlockPos pos, long at) {}

    private static final List<Spark> QUEUE = new ArrayList<>();
    private static final int MAX_SPARKS = 400;

    private FormationEffect() {}

    /** 形成の状態が変わったら呼ぶ。未形成から形成になったときだけ演出する。 */
    public static void onChange(ServerLevel level, BlockPos controller, boolean wasFormed, boolean nowFormed, boolean firstCheck,
                                int horizontal, int down, int up) {
        if (!wasFormed && nowFormed) {
            Blueprints.Kind kind = Blueprints.kindOf(level.getBlockState(controller).getBlock());
            if (kind != null) {
                io.github.genichimaruo.singulo.registry.SinguloTriggers.milestoneNear(level, controller, 32, "formed/" + kind.id());
            }
            if (!firstCheck) {
                play(level, controller, horizontal, down, up);
            }
        }
    }

    /** コントローラの周り（水平 horizontal、下 down、上 up の箱）の部品に光を走らせる。 */
    public static void play(ServerLevel level, BlockPos controller, int horizontal, int down, int up) {
        long now = level.getGameTime();
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(controller.offset(-horizontal, -down, -horizontal),
                controller.offset(horizontal, up, horizontal))) {
            if (n >= MAX_SPARKS) {
                break;
            }
            if (Structures.role(level.getBlockState(p)) != null || p.equals(controller)) {
                int dist = Math.max(Math.abs(p.getX() - controller.getX()),
                        Math.max(Math.abs(p.getY() - controller.getY()), Math.abs(p.getZ() - controller.getZ())));
                QUEUE.add(new Spark(level, p.immutable(), now + dist));
                n++;
            }
        }
        io.github.genichimaruo.singulo.registry.SinguloSounds.playAt(level, controller, "multiblock_formed", 1.5F, 1.0F);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) {
            return;
        }
        for (Iterator<Spark> it = QUEUE.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            if (s.level.getGameTime() >= s.at) {
                s.level.sendParticles(ParticleTypes.END_ROD, s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5,
                        4, 0.45, 0.45, 0.45, 0.01);
                it.remove();
            } else if (s.at - s.level.getGameTime() > 200) {
                it.remove();
            }
        }
    }
}
