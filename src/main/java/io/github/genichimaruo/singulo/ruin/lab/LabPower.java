package io.github.genichimaruo.singulo.ruin.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 地下アンドロイド研究所の電力。施設の範囲（耐圧殻で包んだ箱）の中の仕掛けを、まとめて切り替える。
 * <ul>
 *   <li>{@link #restore}: 中央動力炉を起動したとき。施設灯・炉心・サーバーラック・制御盤・自販機が動き出し、非常灯が消え、隔壁が開く</li>
 *   <li>{@link #unseal}: 研究所のボスを倒したとき。耐圧殻の封じが解け、掘れるようになる</li>
 * </ul>
 */
public final class LabPower {
    private LabPower() {}

    /** 施設の電力を戻す。切り替えたブロックの数を返す。 */
    public static int restore(ServerLevel level, BoundingBox box) {
        return restore(level, box, false);
    }

    /** 起動の途中: 炉心と動力導管だけを先に光らせる（施設の明かりはまだ）。 */
    public static int ignite(ServerLevel level, BoundingBox box) {
        return restore(level, box, true);
    }

    private static int restore(ServerLevel level, BoundingBox box, boolean coreOnly) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (coreOnly && !s.is(io.github.genichimaruo.singulo.registry.SinguloBlocks.REACTOR_CORE.get())
                    && !s.is(io.github.genichimaruo.singulo.registry.SinguloBlocks.POWER_CONDUIT.get())) {
                continue;
            }
            BlockState t = s;
            if (s.getBlock() instanceof LabPoweredBlock || s.getBlock() instanceof LabFacingBlock || s.getBlock() instanceof ReactorConsoleBlock) {
                t = s.setValue(LabPoweredBlock.POWERED, true);
            } else if (s.getBlock() instanceof VendingMachineBlock) {
                t = s.setValue(VendingMachineBlock.POWERED, true);          // 自販機が動き出す（エメラルドで買える）
            } else if (s.getBlock() instanceof BulkheadBlock) {
                t = s.setValue(BulkheadBlock.OPEN, true);
            }
            if (t != s) {
                level.setBlock(p, t, Block.UPDATE_CLIENTS);
                n++;
            }
        }
        return n;
    }

    /** 耐圧殻の封じを解く（掘れるようになる）。解いたブロックの数を返す。 */
    public static int unseal(ServerLevel level, BoundingBox box) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof PressureHullBlock && s.getValue(PressureHullBlock.SEALED)) {
                level.setBlock(p, s.setValue(PressureHullBlock.SEALED, false), Block.UPDATE_CLIENTS);
                n++;
            }
        }
        return n;
    }

    /** 箱を NBT に（int 6つ）。 */
    public static int[] write(BoundingBox box) {
        return new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
    }

    public static BoundingBox read(int[] a) {
        return new BoundingBox(a[0], a[1], a[2], a[3], a[4], a[5]);
    }
}
