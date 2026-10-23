package io.github.genichimaruo.singulo.cable;

import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * 導線1本。電力は溜めず、受け取った分をネットワーク経由でその場で配る。
 * 実際に電力が通っているあいだは、通った面（入ってきた面と出ていった面）と量をクライアントへ送る（光り方に使う）。
 * 通電表示はネットワークがまとめて更新する。切断は周期を待たず、クライアント側でも直ちに消す。
 */
public class CableBlockEntity extends BlockEntity {
    static final int WINDOW = 10;

    @Nullable
    EnergyNetwork network;

    /** クライアントに見せている通電のようす（in・out は面のビット、dist は入口から数えたケーブルの数）。 */
    private long shownFlow;
    private int shownIn;
    private int shownOut;
    private int shownDist;
    @Nullable private BlockPos predictedCut;
    private long shownGeneration, predictedGeneration;
    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.CABLE.get(), pos, state);
    }

    /** Rendering state only; all electrical accounting and scheduling belong to the network. */
    void showFlow(@Nullable EnergyNetwork.Flow flow) {
        long amount = flow == null ? 0 : flow.amount;
        int in = flow == null ? 0 : flow.in, out = flow == null ? 0 : flow.out;
        int dist = flow == null ? 0 : flow.dist;
        long generation = flow == null ? shownGeneration : flow.generation;
        if (generation != shownGeneration || in != shownIn || out != shownOut || dist != shownDist || bucket(amount) != bucket(shownFlow)) {
            shownGeneration = generation;
            show(amount,in,out,dist);
        }
    }

    /** 光の強さの段階（量の桁、細かめに）。これが変わったときだけ送り直す。 */
    private static int bucket(long flow) {
        return flow <= 0 ? -1 : (int) (Math.log(flow) / Math.log(2) * 2);
    }

    private void show(long flow, int in, int out, int dist) {
        if (flow == shownFlow && in == shownIn && out == shownOut && dist == shownDist) {
            return;
        }
        shownFlow = flow;
        shownIn = in;
        shownOut = out;
        shownDist = dist;
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ クライアント（描画用）

    /** 通っている量（FE/t、通っていなければ0）。 */
    public long flow() {
        if (predictedCut != null && level != null && level.isClientSide) {
            // A rejected/predicted break may be rolled back without fresh packets for every other cable.
            if (level.getBlockState(predictedCut).getBlock() instanceof CableBlock) predictedCut = null;
            else return 0;
        }
        return shownFlow;
    }

    /** 面 d から電力が入ってきているか。 */
    public boolean flowsIn(Direction d) {
        return flow() > 0 && (shownIn & (1 << d.get3DDataValue())) != 0;
    }

    /** 面 d へ電力が出ていっているか。 */
    public boolean flowsOut(Direction d) {
        return flow() > 0 && (shownOut & (1 << d.get3DDataValue())) != 0;
    }

    /** 入口から数えたケーブルの数（波の位置合わせ）。 */
    public int distance() {
        return shownDist;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Flow", shownFlow);
        tag.putLong("FlowGeneration", shownGeneration);
        tag.putInt("In", shownIn);
        tag.putInt("Out", shownOut);
        tag.putInt("Dist", shownDist);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        long generation = tag.getLong("FlowGeneration");
        if (tag.getLong("Flow") == 0 || generation != predictedGeneration) predictedCut = null;
        shownGeneration = generation;
        shownFlow = tag.getLong("Flow");
        shownIn = tag.getInt("In");
        shownOut = tag.getInt("Out");
        shownDist = tag.getInt("Dist");
    }

    // ------------------------------------------------------------------ ネットワーク

    public void invalidateNetwork() {
        if (network != null) {
            network.invalidate();
        }
    }

    /** Client prediction can remove a cable before the server's immediate zero-flow packet arrives. */
    static void clearConnectedDisplay(Level level, BlockPos removed) {
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(removed); seen.add(removed);
        for (Direction d : Direction.values()) {
            BlockPos p = removed.relative(d);
            if (seen.add(p)) queue.add(p);
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.remove();
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof CableBlockEntity cable)) continue;
            if (level.isClientSide) {
                if (cable.shownFlow > 0) {
                    cable.predictedCut = removed;
                    cable.predictedGeneration = cable.shownGeneration;
                }
            } else cable.showFlow(null);
            for (Direction d : Direction.values()) {
                BlockPos next = p.relative(d);
                if (seen.add(next)) queue.add(next);
            }
        }
    }

    void invalidateForNeighbor(BlockPos pos) {
        if (network != null && network.topologyChanged(pos)) network.invalidate();
    }

    EnergyNetwork network() {
        if (network == null || !network.isValid()) {
            if (network != null) network.invalidate();
            EnergyNetwork.build(level, worldPosition);
        }
        network.ensureEntry(worldPosition);
        return network;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            invalidateNetwork();
            for (Direction direction : Direction.values()) {
                BlockPos next = worldPosition.relative(direction);
                if (level.isLoaded(next) && level.getBlockEntity(next) instanceof CableBlockEntity cable) {
                    cable.invalidateNetwork();
                }
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        invalidateNetwork();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        // Client chunk replacement does not invoke Block.onRemove; BE removal is the local hook.
        // Unloading an unchanged cable chunk must not flood-fill the component once per cable.
        if (level != null && level.isClientSide && level.isLoaded(worldPosition)
                && level.getBlockState(worldPosition).getBlock() != getBlockState().getBlock()) {
            clearConnectedDisplay(level,worldPosition);
        }
        invalidateNetwork();
        super.setRemoved();
    }

    @Nullable
    public IEnergyStorage energyFor(@Nullable Direction side) {
        if (level == null || level.isClientSide) {
            return null;
        }
        return new Port(side);
    }

    /** 導線の口。受け取った電力を、入ってきた口以外の受け手へ流す。 */
    private final class Port implements LongEnergyStorage {
        @Nullable
        private final Direction side;

        Port(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public long receiveLong(long amount, boolean simulate) {
            if (level == null || isRemoved() || !level.isLoaded(worldPosition)
                    || level.getBlockEntity(worldPosition) != CableBlockEntity.this) return 0;
            BlockPos source = side == null ? null : worldPosition.relative(side);
            return network().distribute(worldPosition, source, amount, simulate);
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return (int) receiveLong(amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return getBlockState().getBlock() instanceof CableBlock cable
                    ? (int)Math.min(Integer.MAX_VALUE,cable.capacity()) : 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return level != null && !isRemoved();
        }
    }
}
