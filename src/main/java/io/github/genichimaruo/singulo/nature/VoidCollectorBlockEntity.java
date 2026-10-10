package io.github.genichimaruo.singulo.nature;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 虚空捕集器。ジ・エンドの浮島の縁など、真下が奈落まで何もない所に置くと、虚空から湧く塵を集める。
 * ジ・エンドの外や、真下に1ブロックでも何かある所では動かない。MAX_TICKS tick（既定1分）に1個。
 */
public class VoidCollectorBlockEntity extends EnvironmentCollectorBlockEntity {
    public static final int MAX_TICKS = 1200;

    public VoidCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.VOID_COLLECTOR.get(), pos, state);
    }

    /** 真下が奈落まで空いているか（ジ・エンドだけ）。 */
    public static boolean overVoid(Level level, BlockPos pos) {
        if (level.dimension() != Level.END) {
            return false;
        }
        BlockPos.MutableBlockPos p = pos.below().mutable();
        while (p.getY() >= level.getMinBuildHeight()) {
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) {
                return false;
            }
            p.move(0, -1, 0);
        }
        return true;
    }

    @Override
    protected double rateAt(Level level, BlockPos pos) {
        return overVoid(level, pos) ? 1.0 : 0.0;
    }

    @Override
    protected String product() {
        return "void_dust";
    }

    @Override
    protected String collectSound() {
        return "void_collector.collect";
    }

    @Override
    protected int ticksAtMax() {
        return MAX_TICKS;
    }

    @Override
    protected String messageKey() {
        return "message.singulo.void_collector";
    }
}
