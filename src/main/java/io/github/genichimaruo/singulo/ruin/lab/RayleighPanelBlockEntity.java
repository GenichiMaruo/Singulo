package io.github.genichimaruo.singulo.ruin.lab;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * レイリー散乱パネルのブロックエンティティ（描画は RayleighPanelRenderer、日差しは {@link RayleighSky}）。
 * 設計図から作ったものはパネル受電器から電力を受けて点く（GravityReceiverBlockEntity。1枚 20 FE/t、つながったパネル全体へ配る）。
 * 研究所の天井のものは研究所の中央動力炉で点く。
 */
public class RayleighPanelBlockEntity extends BlockEntity {
    public RayleighPanelBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.RAYLEIGH_PANEL.get(), pos, state);
    }

    @Override
    public void setBlockState(BlockState state) {
        boolean changed = getBlockState() != state;
        super.setBlockState(state);
        if (changed && level != null) RayleighSky.changed(level, worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            RayleighSky.register(level, worldPosition);
            // Existing saves can contain the old panel's BLOCK=15 data. Remove that obsolete
            // source as well as registering the new artificial sky; the light engine propagates it.
            level.getLightEngine().checkBlock(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) RayleighSky.unregister(level, worldPosition);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (level != null) RayleighSky.unregister(level, worldPosition);
        super.setRemoved();
    }


    /** Keep the sky surface visible when its block reaches the edge of the camera frustum. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1);
    }
}
