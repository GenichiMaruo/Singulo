package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * 液体パイプ1本。液体は溜めない（中身がないので、壊しても液体は失われない）。受け渡しは {@link FluidPipeNetwork} がまとめて行う。
 * <p>
 * 見た目のための流れ（状態・入ってきた面・出ていった面・量・液体）だけを持つ。ネットワークが {@link FluidPipeNetwork#DISPLAY} tick ごとに
 * 決め、変わったときだけクライアントへ送る（量は桁の段階が変わったときと、測量メガネで見ているときに更新）。描くのは FluidPipeRenderer と測量メガネ。
 */
public class FluidPipeBlockEntity extends BlockEntity implements io.github.genichimaruo.singulo.cable.ConduitCover.Holder {
    /** 状態: 流れていない・流れている・詰まっている（出せる液体があるのに受け手がない。測量メガネをかけた人が近いときだけ）。 */
    public static final int IDLE = 0, FLOWING = 1, BLOCKED = 2;

    private static final Set<FluidPipeBlockEntity> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    public static List<FluidPipeBlockEntity> clientPipes() {
        return List.copyOf(CLIENT);
    }

    private int visualState, inMask, outMask;
    private long visualAmount, lastSentAmount;
    private boolean mixed;
    private FluidStack visualFluid = FluidStack.EMPTY;

    @Nullable
    FluidPipeNetwork network;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.FLUID_PIPE.get(), pos, state);
    }

    boolean injectionPoint;

    public IFluidHandler inlet(@Nullable Direction face) {
        return new Inlet(face);
    }

    final class Inlet extends EmptyFluidHandler {
        @Nullable private final Direction face;

        Inlet(@Nullable Direction face) { this.face = face; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? Integer.MAX_VALUE : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack fluid) { return tank == 0 && !fluid.isEmpty(); }
        @Override public int fill(FluidStack fluid, FluidAction action) { return route(fluid, action, null); }

        int route(FluidStack fluid, FluidAction action, @Nullable FluidPumpBlockEntity.Outlet pump) {
            if (level == null || level.isClientSide || isRemoved() || fluid.isEmpty()
                    || !level.isLoaded(worldPosition) || level.getBlockEntity(worldPosition) != FluidPipeBlockEntity.this) return 0;
            if (!injectionPoint) {
                injectionPoint = true;
                if (network != null && !network.hasNode(worldPosition)) invalidateNetwork();
            }
            if (network == null) FluidPipeNetwork.build(level, FluidPipeBlockEntity.this);
            return network == null ? 0 : network.route(worldPosition, face, fluid, action, pump);
        }
    }

    public int visualState() {
        return visualState;
    }

    public int inMask() {
        return inMask;
    }

    public int outMask() {
        return outMask;
    }

    /** Kept for callers using the original int-sized display. */
    public int visualAmount() {
        return (int) Math.min(Integer.MAX_VALUE, visualAmount);
    }

    /** 流れている量（mB/t、見た目の間隔の平均）。 */
    public long flowRate() {
        return visualAmount;
    }

    public boolean mixed() {
        return mixed;
    }

    public FluidStack visualFluid() {
        return visualFluid;
    }

    /** 測量メガネに見せる値があるか。 */
    public boolean fresh() {
        return visualState != IDLE;
    }

    /** 面 d から液体が入ってきているか。 */
    public boolean flowsIn(Direction d) {
        return visualState == FLOWING && (inMask & (1 << d.get3DDataValue())) != 0;
    }

    /** 面 d へ液体が出ていっているか。 */
    public boolean flowsOut(Direction d) {
        return visualState == FLOWING && (outMask & (1 << d.get3DDataValue())) != 0;
    }

    /** 量の段階（2倍ごと）。これが変わったときだけ送り直す。 */
    private static int bucket(long amount) {
        return amount <= 0 ? -1 : 63 - Long.numberOfLeadingZeros(amount);
    }

    /** ネットワークが見せる流れを決める。変わったときだけ送る。 */
    void show(int state, int in, int out, long amount, FluidStack fluid, boolean mixed) {
        boolean changed = visualState != state || inMask != in || outMask != out || this.mixed != mixed
                || bucket(visualAmount) != bucket(amount) || !io.github.genichimaruo.singulo.compat.Legacy.sameFluid(visualFluid, fluid);
        visualAmount = amount;
        if (!changed) {
            return;
        }
        visualState = state;
        inMask = in;
        outMask = out;
        visualAmount = amount;
        visualFluid = fluid.isEmpty() ? FluidStack.EMPTY : io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(fluid,1);
        this.mixed = mixed;
        syncVisual();
    }

    void syncSurveyRate() {
        if (lastSentAmount != visualAmount) syncVisual();
    }

    private void syncVisual() {
        lastSentAmount = visualAmount;
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("state", visualState);
        tag.putInt("in", inMask);
        tag.putInt("out", outMask);
        tag.putLong("amount", visualAmount);
        tag.putBoolean("mixed", mixed);
        if (!visualFluid.isEmpty()) {
            tag.put("fluid", visualFluid.writeToNBT(new CompoundTag()));
        }
        io.github.genichimaruo.singulo.cable.ConduitCover.save(tag, coverState);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 流れの見た目は保存しない（クライアントへの更新でだけ届く。読み込み直したら流れていない状態から始める）。保存するのはカバーだけ
        visualState = tag.getInt("state");
        inMask = tag.getInt("in");
        outMask = tag.getInt("out");
        visualAmount = tag.getLong("amount");
        mixed = tag.getBoolean("mixed");
        visualFluid = tag.contains("fluid") ? FluidStack.loadFluidStackFromNBT(tag.getCompound("fluid")) : FluidStack.EMPTY;
        loadCover(tag);
    }

    // ------------------------------------------------------------------ カバー（{@link io.github.genichimaruo.singulo.cable.ConduitCover}）

    /** カバーにはめ込んだブロック（向きを含む状態。なければ null）。 */
    @Nullable
    private BlockState coverState;

    @Override
    @Nullable
    public BlockState coverState() {
        return coverState;
    }

    @Override
    public void setCoverState(@Nullable BlockState state) {
        coverState = state;
        setChanged();
        io.github.genichimaruo.singulo.cable.ConduitCover.updateLight(level, worldPosition, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        io.github.genichimaruo.singulo.cable.ConduitCover.save(tag, coverState);
    }

    /** 読んだカバーが変わっていれば、クライアントでは見た目を描き直す。 */
    private void loadCover(CompoundTag tag) {
        BlockState before = coverState;
        coverState = io.github.genichimaruo.singulo.cable.ConduitCover.load(tag);
        if (before != coverState && level != null && level.isClientSide) {
            io.github.genichimaruo.singulo.cable.ConduitCover.updateLight(level, worldPosition, coverState);
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public net.minecraftforge.client.model.data.ModelData getModelData() {
        return coverState == null ? net.minecraftforge.client.model.data.ModelData.EMPTY
                : net.minecraftforge.client.model.data.ModelData.builder().with(io.github.genichimaruo.singulo.cable.ConduitCover.MODEL_STATE, coverState).build();
    }


    /** 隣が変わった。口が増えた・消えたときだけネットワークを作り直す。 */
    void neighborChanged(BlockPos from) {
        if (level == null || network == null || !level.isLoaded(from)) {
            return;
        }
        boolean pipe = level.getBlockEntity(from) instanceof FluidPipeBlockEntity;
        if (pipe ? FluidPipeNetwork.of((FluidPipeBlockEntity) level.getBlockEntity(from)) != network
                : FluidPipeNetwork.mayBeEndpoint(level, from, FluidPipeNetwork.direction(from, worldPosition)) != network.hasEndpoint(from)) {
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
        // 光るブロックの明るさを書き直す（0.23.0 より前にはめ込んだ物も光るように）
        if (coverState != null) {
            io.github.genichimaruo.singulo.cable.ConduitCover.updateLight(level, worldPosition, coverState);
        }
        if (level != null && level.isClientSide) {
            CLIENT.add(this);
        }
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
        if (level != null && level.isClientSide) {
            CLIENT.remove(this);
        }
        leaveNetwork();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            CLIENT.remove(this);
        }
        leaveNetwork();
        super.setRemoved();
    }
}
