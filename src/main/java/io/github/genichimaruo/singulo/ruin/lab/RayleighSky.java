package io.github.genichimaruo.singulo.ruin.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * レイリー散乱パネルの「日差し」: 点いているパネルの真下（光を通すブロックだけをはさんで）にあるものは、真昼の日が当たっている扱い。
 * 日照センサーはずっと真昼の値（ふつうは15、反転なら0）を出す（mixin.DaylightDetectorBlockMixin）。
 */
public final class RayleighSky {
    /** 上に何ブロック先まで探すか。 */
    public static final int REACH = 32;

    private RayleighSky() {}

    /** pos の真上に、光を通すブロックだけをはさんで、点いているレイリー散乱パネルがあるか。 */
    public static boolean litAbove(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < REACH; i++) {
            p.move(0, 1, 0);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof RayleighPanelBlock) {
                return s.getValue(RayleighPanelBlock.LIT);
            }
            if (s.canOcclude() && s.isSolidRender(level, p)) {
                return false;
            }
        }
        return false;
    }

    /** 真昼の日照センサーの値（反転していれば0）。 */
    public static int noonSignal(boolean inverted) {
        return inverted ? 0 : 15;
    }
}
