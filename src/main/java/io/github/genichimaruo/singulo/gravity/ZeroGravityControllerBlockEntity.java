package io.github.genichimaruo.singulo.gravity;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 無重力コントロールボックス。接している無重力ブロックの集まり全体の、無重力の高さを決める（1ブロック以上、上限なし）。
 * 高さを上げるほど、無重力ブロック1つの電力が高さ1ブロックごとに ×{@value ZeroGravity#GROWTH} で増える（{@link ZeroGravity#costPerBlock}）。
 */
public class ZeroGravityControllerBlockEntity extends BlockEntity {
    private int height = ZeroGravity.DEFAULT_HEIGHT;

    public ZeroGravityControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ZERO_GRAVITY_CONTROLLER.get(), pos, state);
    }

    public int height() {
        return height;
    }

    /** 高さを変え、集まりの受電器に調べ直しを頼む。 */
    public void setHeight(int h) {
        int next = Math.max(1, h);
        if (next == height) {
            return;
        }
        height = next;
        setChanged();
        if (level != null) {
            notifyReceivers(level, worldPosition);
        }
    }

    /** 接している無重力ブロックの集まりにつながった受電器へ、調べ直しを頼む（集まりをたどる。大きくても1回だけ）。 */
    static void notifyReceivers(Level level, BlockPos from) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue queue = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
        queue.enqueue(from.asLong());
        seen.add(from.asLong());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && seen.size() < GravityReceiverBlockEntity.MAX_PANELS) {
            p.set(queue.dequeueLong());
            for (Direction d : Direction.values()) {
                n.setWithOffset(p, d);
                long key = n.asLong();
                if (seen.contains(key) || !level.isLoaded(n)) {
                    continue;
                }
                if (level.getBlockEntity(n) instanceof GravityReceiverBlockEntity r) {
                    r.requestScan();
                    seen.add(key);
                } else if (GravityReceiverBlockEntity.isPanel(level.getBlockState(n))) {
                    seen.add(key);
                    queue.enqueue(key);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("height", height);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        height = tag.contains("height") ? Math.max(1, tag.getInt("height")) : ZeroGravity.DEFAULT_HEIGHT;
    }
}
