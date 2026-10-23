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
 * <p>同じ集まりに付いたコントロールボックスは、どれも同じ高さを持つ（1つで変えると、ほかのボックスも同じ高さになる。新しく置いたボックスは、
 * すでにあるボックスの高さに合わせる）。壊すと高さはアイテムに残り、置き直すとその高さに戻る（壊して置き直すだけで、
 * 電力の安い高さ5に戻ることはない）。
 */
public class ZeroGravityControllerBlockEntity extends BlockEntity {
    private int height = ZeroGravity.DEFAULT_HEIGHT;

    public ZeroGravityControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ZERO_GRAVITY_CONTROLLER.get(), pos, state);
    }

    public int height() {
        return height;
    }

    /** 高さを変え、同じ集まりのほかのボックスにも同じ高さを写して、集まりの受電器に調べ直しを頼む。 */
    public void setHeight(int h) {
        int next = Math.max(1, h);
        height = next;
        setChanged();
        if (level != null) {
            for (ZeroGravityControllerBlockEntity other : groupControllers(level, worldPosition)) {
                if (other != this && other.height != next) {
                    other.height = next;
                    other.setChanged();
                }
            }
            notifyReceivers(level, worldPosition);
        }
    }

    /** 置いたとき: 同じ集まりにほかのボックスがあれば、その高さに合わせる（なければアイテムに残っていた高さのまま）。 */
    void adoptGroupHeight() {
        if (level == null) {
            return;
        }
        for (ZeroGravityControllerBlockEntity other : groupControllers(level, worldPosition)) {
            if (other != this) {
                height = other.height;
                setChanged();
                return;
            }
        }
    }

    /** from（ボックスの位置）に接する無重力ブロックの集まりに付いた、すべてのボックス（from 自身を含む）。 */
    static java.util.List<ZeroGravityControllerBlockEntity> groupControllers(Level level, BlockPos from) {
        java.util.List<ZeroGravityControllerBlockEntity> out = new java.util.ArrayList<>();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue queue = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
        queue.enqueue(from.asLong());
        seen.add(from.asLong());
        if (level.getBlockEntity(from) instanceof ZeroGravityControllerBlockEntity self) {
            out.add(self);
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && seen.size() < GravityReceiverBlockEntity.MAX_PANELS) {
            p.set(queue.dequeueLong());
            boolean fromController = p.asLong() == from.asLong();
            var current = level.getBlockState(p);
            boolean fromZero = current.getBlock() instanceof GravityPanelBlock g && g.kind() == GravityPanelBlock.Kind.ZERO;
            for (Direction d : Direction.values()) {
                n.setWithOffset(p, d);
                long key = n.asLong();
                if (seen.contains(key) || !level.isLoaded(n)) {
                    continue;
                }
                var state = level.getBlockState(n);
                // Match the receiver's power-group traversal, including mixed panel bridges.
                // A controller itself still has to touch a zero-gravity block.
                if (GravityReceiverBlockEntity.isPanel(state) && (!fromController
                        || state.getBlock() instanceof GravityPanelBlock g && g.kind() == GravityPanelBlock.Kind.ZERO)) {
                    seen.add(key);
                    queue.enqueue(key);
                } else if (fromZero && level.getBlockEntity(n) instanceof ZeroGravityControllerBlockEntity c) {
                    seen.add(key);
                    out.add(c);                                       // ボックスからは先へたどらない（別の集まりをつながない）
                }
            }
        }
        return out;
    }

    /** 壊したときのアイテムに高さを残す（置き直すと、ブロックのデータとして戻る）。 */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        CompoundTag tag = new CompoundTag();
        tag.putInt("height", height);
        net.minecraft.world.level.block.entity.BlockEntity.addEntityType(tag, getType());
        components.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(tag));
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("height", height);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        height = tag.contains("height") ? Math.max(1, tag.getInt("height")) : ZeroGravity.DEFAULT_HEIGHT;
    }
}
