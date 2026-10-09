package io.github.genichimaruo.singulo.multiblock;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * コントローラがつないでいる搬入出ポートの一覧。形を確かめるたびに付け直し、コントローラが壊されたら外す。
 * チャンクの読み込み解除のときは外さない（隣のチャンクのポートに触ると、解除中のチャンクを読み込み直してしまう）。
 */
public final class PortLinks {
    private List<BlockPos> ports = List.of();
    private boolean chunkUnloading;

    /** 見つかったポートにつなぎ直す（形成できていなければ空の一覧を渡す）。変わったら true。 */
    public boolean update(Level level, BlockPos controller, List<BlockPos> found) {
        if (found.equals(ports)) {
            return false;
        }
        link(level, ports, null);
        link(level, found, controller);
        ports = List.copyOf(found);
        return true;
    }

    public List<BlockPos> ports() {
        return ports;
    }

    /** BlockEntity.onChunkUnloaded から呼ぶ。 */
    public void onChunkUnloaded() {
        chunkUnloading = true;
    }

    /** BlockEntity.setRemoved から呼ぶ。 */
    public void onRemoved(@Nullable Level level) {
        if (level != null && !chunkUnloading) {
            link(level, ports, null);
        }
    }

    private static void link(Level level, List<BlockPos> ports, @Nullable BlockPos controller) {
        for (BlockPos p : ports) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof PortBlockEntity port) {
                port.link(controller);
            }
        }
    }
}
