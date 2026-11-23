package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.BulkheadBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * 前室のあるボス部屋（最終実験施設のドーム・沈んだ炉心区画の球）の形。ボス部屋は耐圧殻で包まれ、隔壁だけが出入口になる。
 * 封印コンソール（ドーム）・封印核（球）の位置から、部屋の中か、隔壁はどこか、隔壁が閉じているかを調べる。
 * 実際の隔壁の位置と開閉状態を使うので、向きや再起動に依存しない。中継器がそろう前に入り込まれたときの扱いは
 * {@link IntrusionAlarm}。
 */
public final class PressureRoomSeal {
    private PressureRoomSeal() {}

    /** 封印コンソール・封印核から隔壁までの距離。 */
    static int reach(boolean dome) {
        return dome ? 19 : 23;
    }

    static int floor(BlockPos core, boolean dome) {
        return core.getY() - (dome ? 3 : 0);
    }

    /** core の真下の耐圧殻で、前室のあるボス部屋かを見分ける（古い版の遺構や、ほかの遺構の封印核では false）。 */
    static boolean pressureRoom(Level level, BlockPos core, boolean dome) {
        return level.getBlockState(core.below(dome ? 4 : 7)).is(SinguloBlocks.PRESSURE_HULL.get());
    }

    /** at（足もとの位置）がボス部屋の中か、部屋から隔壁までの通路の中か。 */
    static boolean inside(BlockPos core, boolean dome, Vec3 at) {
        double x = at.x - core.getX() - 0.5, z = at.z - core.getZ() - 0.5, y = at.y - floor(core, dome);
        int reach = reach(dome);
        if (Math.abs(x) > reach || Math.abs(z) > reach || y < 0 || y > (dome ? 19 : 27)) return false;
        double vertical = dome ? (y - 1) / 17 : (y - 10) / 16;
        double radius = dome ? 16 : 21;
        if ((x * x + z * z) / (radius * radius) + vertical * vertical <= 1) return true;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            double along = x * dir.getStepX() + z * dir.getStepZ();
            double across = x * dir.getStepZ() - z * dir.getStepX();
            if (along >= 0 && along < reach - 0.5 && Math.abs(across) < 1.5 && y < 5) return true;
        }
        return false;
    }

    /** pos がボス部屋の殻（ランプなど）を含めた範囲か（警報の灯を替える範囲）。 */
    static boolean shell(BlockPos core, boolean dome, BlockPos pos) {
        double x = pos.getX() - core.getX(), z = pos.getZ() - core.getZ(), y = pos.getY() - floor(core, dome);
        double vertical = dome ? (y - 1) / 18.5 : (y - 10) / 17.5;
        double radius = dome ? 17.5 : 22.5;
        return (x * x + z * z) / (radius * radius) + vertical * vertical <= 1 || inside(core, dome, Vec3.atBottomCenterOf(pos));
    }

    /** 隔壁の下の段の真ん中（四方から探す）。見つからなければ null。 */
    @Nullable
    static BlockPos gate(Level level, BlockPos core, boolean dome) {
        BlockPos base = core.atY(floor(core, dome) + 1);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos gate = base.relative(dir, reach(dome));
            if (level.getBlockState(gate).is(SinguloBlocks.BULKHEAD.get())) return gate;
        }
        return null;
    }

    static boolean closed(Level level, BlockPos gate) {
        var state = level.getBlockState(gate);
        return state.is(SinguloBlocks.BULKHEAD.get()) && !state.getValue(BulkheadBlock.OPEN);
    }

    /** 隔壁（幅3×高さ4）を開け閉めする。変わった数を返す。 */
    static int setGate(Level level, BlockPos gate, boolean open) {
        int changed = 0;
        for (BlockPos p : BlockPos.betweenClosed(gate.offset(-1, 0, -1), gate.offset(1, 3, 1))) {
            var state = level.getBlockState(p);
            if (state.is(SinguloBlocks.BULKHEAD.get()) && state.getValue(BulkheadBlock.OPEN) != open) {
                level.setBlock(p, state.setValue(BulkheadBlock.OPEN, open), Block.UPDATE_ALL);
                changed++;
            }
        }
        return changed;
    }

    /** at が閉じた隔壁の内側（ボス部屋か通路の中）なら、その隔壁の位置。そうでなければ null。 */
    @Nullable
    static BlockPos exit(Level level, BlockPos core, boolean dome, Vec3 at) {
        if (!pressureRoom(level, core, dome) || !inside(core, dome, at)) return null;
        BlockPos gate = gate(level, core, dome);
        return gate != null && closed(level, gate) ? gate : null;
    }
}
