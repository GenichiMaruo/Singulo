package io.github.genichimaruo.singulo.nature;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * 深海圧力収集器。真上に続く水の深さ（水圧）で、鉱物を押し固めた高圧結晶を少しずつ作る。
 * 水深 MIN_DEPTH 未満では動かず、MAX_DEPTH 以上で最大（MAX_TICKS tick に1個、既定1分）。浅い海や陸では取れない。
 */
public class DeepSeaCollectorBlockEntity extends EnvironmentCollectorBlockEntity {
    public static final int MIN_DEPTH = 16;
    public static final int MAX_DEPTH = 48;
    public static final int MAX_TICKS = 1200;

    public DeepSeaCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.DEEP_SEA_COLLECTOR.get(), pos, state);
    }

    /** 真上に続く水の深さ（ブロック）。 */
    public static int depth(Level level, BlockPos pos) {
        int h = 0;
        BlockPos.MutableBlockPos p = pos.above().mutable();
        while (h < MAX_DEPTH && level.isLoaded(p) && level.getFluidState(p).is(Fluids.WATER)) {
            h++;
            p.move(0, 1, 0);
        }
        return h;
    }

    @Override
    protected double rateAt(Level level, BlockPos pos) {
        int h = depth(level, pos);
        return h < MIN_DEPTH ? 0 : Math.min(1.0, h / (double) MAX_DEPTH);
    }

    @Override
    protected String product() {
        return "pressure_crystal";
    }

    @Override
    protected String collectSound() {
        return "deep_sea_collector.collect";
    }

    @Override
    protected int ticksAtMax() {
        return MAX_TICKS;
    }

    @Override
    protected String messageKey() {
        return "message.singulo.deep_sea_collector";
    }
}
