package io.github.genichimaruo.singulo.ruin.lab;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** スライドドアの扉の位置（クライアントだけで動かす。0 閉 〜 1 開）。読み込んだときは、今の開け閉めの位置から始める。 */
public class SlidingDoorBlockEntity extends BlockEntity {
    /** 1 tick に動く分（約5 tick で開ききる）。 */
    static final float SPEED = 0.2F;
    private float slide, previous;
    private boolean started;

    public SlidingDoorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.SLIDING_DOOR.get(), pos, state);
    }

    void clientTick(BlockState state) {
        float target = state.getValue(SlidingDoorBlock.OPEN) ? 1 : 0;
        if (!started) {
            slide = previous = target;
            started = true;
        }
        previous = slide;
        slide = slide < target ? Math.min(target, slide + SPEED) : Math.max(target, slide - SPEED);
    }

    /** 扉の開き（なめらかに動き出し、なめらかに止まる）。 */
    public float slide(float partialTick) {
        if (!started) {
            return getBlockState().getValue(SlidingDoorBlock.OPEN) ? 1 : 0;
        }
        float v = Mth.lerp(partialTick, previous, slide);
        return v * v * (3 - 2 * v);
    }
}
