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
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 導線1本。電力は溜めず、受け取った分をネットワーク経由でその場で配る。
 * 実際に電力が通っているあいだは、通った面（入ってきた面と出ていった面）と量をクライアントへ送る（光り方に使う）。
 * 新しく通り始めた道はすぐに、量の変化は {@link #WINDOW} tick ごとにまとめて送る。
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
    /** 集計中の WINDOW tick 分。 */
    private long windowFlow;
    private int windowIn;
    private int windowOut;
    private int windowDist = Integer.MAX_VALUE;
    private int windowTicks;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.CABLE.get(), pos, state);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, CableBlockEntity be) {
        EnergyNetwork.Flow f = be.network().lastFlow(pos);
        if (f != null) {
            be.windowFlow += f.amount;
            be.windowIn |= f.in;
            be.windowOut |= f.out;
            be.windowDist = Math.min(be.windowDist, f.dist);
            // 新しい道はすぐに光らせる
            if ((f.in & ~be.shownIn) != 0 || (f.out & ~be.shownOut) != 0) {
                be.show(be.shownFlow == 0 ? f.amount : be.shownFlow, be.shownIn | f.in, be.shownOut | f.out,
                        be.shownFlow == 0 ? f.dist : Math.min(be.shownDist, f.dist));
            }
        }
        if (++be.windowTicks >= WINDOW) {
            long avg = be.windowFlow / WINDOW;
            if (be.windowFlow == 0) {
                be.show(0, 0, 0, 0);
            } else if (be.windowIn != be.shownIn || be.windowOut != be.shownOut || be.windowDist != be.shownDist
                    || bucket(avg) != bucket(be.shownFlow)) {
                be.show(Math.max(1, avg), be.windowIn, be.windowOut, be.windowDist);
            }
            be.windowFlow = 0;
            be.windowIn = 0;
            be.windowOut = 0;
            be.windowDist = Integer.MAX_VALUE;
            be.windowTicks = 0;
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
        return shownFlow;
    }

    /** 面 d から電力が入ってきているか。 */
    public boolean flowsIn(Direction d) {
        return (shownIn & (1 << d.get3DDataValue())) != 0;
    }

    /** 面 d へ電力が出ていっているか。 */
    public boolean flowsOut(Direction d) {
        return (shownOut & (1 << d.get3DDataValue())) != 0;
    }

    /** 入口から数えたケーブルの数（波の位置合わせ）。 */
    public int distance() {
        return shownDist;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Flow", shownFlow);
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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

    EnergyNetwork network() {
        if (network == null || !network.isValid()) {
            EnergyNetwork.build(level, worldPosition);
        }
        return network;
    }

    @Override
    public void setRemoved() {
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
            return (int) Math.min(Integer.MAX_VALUE, network().capacity());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
