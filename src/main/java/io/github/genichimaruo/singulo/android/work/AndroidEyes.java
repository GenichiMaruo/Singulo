package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * アンドロイドの装置の正面の顔: アンドロイドが使っている間（使ってから AWAKE tick）だけ目を開ける（LIT）。電力は使わない。
 * 保存はしない（読み込み直したら眠っている）。
 */
public final class AndroidEyes {
    public static final int AWAKE = 60;
    private long awakeUntil;

    /** 顔を持つ装置。 */
    public interface Holder {
        AndroidEyes eyes();
    }

    /** アンドロイドがその場所の装置を使った: 顔があれば、しばらく目を開ける。 */
    public static void wake(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof Holder holder) {
            holder.eyes().awakeUntil = level.getGameTime() + AWAKE;
            setLit(level, pos, level.getBlockState(pos), true);
        }
    }

    /** 毎 tick 呼ぶ: 使われなくなってしばらくしたら、目を閉じる。 */
    public void tick(Level level, BlockPos pos, BlockState state) {
        setLit(level, pos, state, level.getGameTime() < awakeUntil);
    }

    /** 正面の顔の目を開け閉めする（変わったときだけ書き換える）。 */
    public static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }
}
