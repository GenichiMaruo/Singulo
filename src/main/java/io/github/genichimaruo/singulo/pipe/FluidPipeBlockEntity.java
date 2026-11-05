package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 液体パイプ1本。液体は溜めない（中身がないので、壊しても液体は失われない）。受け渡しは {@link FluidPipeNetwork} がまとめて行う。 */
public class FluidPipeBlockEntity extends BlockEntity {
    @Nullable
    FluidPipeNetwork network;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.FLUID_PIPE.get(), pos, state);
    }

    /** 隣が変わった。口が増えた・消えたときだけネットワークを作り直す。 */
    void neighborChanged(BlockPos from) {
        if (level == null || network == null || !level.isLoaded(from)) {
            return;
        }
        boolean pipe = level.getBlockEntity(from) instanceof FluidPipeBlockEntity;
        if (pipe ? FluidPipeNetwork.of((FluidPipeBlockEntity) level.getBlockEntity(from)) != network
                : FluidPipeNetwork.mayBeEndpoint(level, from) != network.hasEndpoint(from)) {
            network.invalidate();
        }
    }

    /** ネットワークはサーバーのものだけ（一人プレイではクライアントのスレッドから触らない）。 */
    private void leaveNetwork() {
        if (level != null && !level.isClientSide) {
            invalidateNetwork();
            FluidPipeNetwork.forget(this);
        }
    }

    void invalidateNetwork() {
        if (network != null) {
            network.invalidate();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            FluidPipeNetwork.schedule(this);
            for (Direction d : Direction.values()) {
                BlockPos next = worldPosition.relative(d);
                if (level.isLoaded(next) && level.getBlockEntity(next) instanceof FluidPipeBlockEntity pipe) {
                    pipe.invalidateNetwork();
                }
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        leaveNetwork();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        leaveNetwork();
        super.setRemoved();
    }
}
