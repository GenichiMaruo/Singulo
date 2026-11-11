package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 液体パイプ1本。液体は溜めない（中身がないので、壊しても液体は失われない）。受け渡しは {@link FluidPipeNetwork} がまとめて行う。
 * <p>
 * 見た目のための流れ（状態・入ってきた面・出ていった面・量・液体）だけを持つ。ネットワークが {@link FluidPipeNetwork#DISPLAY} tick ごとに
 * 決め、変わったときだけクライアントへ送る（量は桁の段階が変わったときだけ）。描くのは FluidPipeRenderer と測量メガネ。
 */
public class FluidPipeBlockEntity extends BlockEntity {
    /** 状態: 流れていない・流れている・詰まっている（出せる液体があるのに受け手がない。測量メガネをかけた人が近いときだけ）。 */
    public static final int IDLE = 0, FLOWING = 1, BLOCKED = 2;

    private static final Set<FluidPipeBlockEntity> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    public static List<FluidPipeBlockEntity> clientPipes() {
        return List.copyOf(CLIENT);
    }

    private int visualState, inMask, outMask, visualAmount;
    private boolean mixed;
    private FluidStack visualFluid = FluidStack.EMPTY;

    @Nullable
    FluidPipeNetwork network;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.FLUID_PIPE.get(), pos, state);
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

    /** 流れている量（mB/t、見た目の間隔の平均）。 */
    public int visualAmount() {
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
    private static int bucket(int amount) {
        return amount <= 0 ? -1 : 31 - Integer.numberOfLeadingZeros(amount);
    }

    /** ネットワークが見せる流れを決める。変わったときだけ送る。 */
    void show(int state, int in, int out, int amount, FluidStack fluid, boolean mixed) {
        boolean changed = visualState != state || inMask != in || outMask != out || this.mixed != mixed
                || bucket(visualAmount) != bucket(amount) || !FluidStack.isSameFluidSameComponents(visualFluid, fluid);
        if (!changed) {
            return;
        }
        visualState = state;
        inMask = in;
        outMask = out;
        visualAmount = amount;
        visualFluid = fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(1);
        this.mixed = mixed;
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("state", visualState);
        tag.putInt("in", inMask);
        tag.putInt("out", outMask);
        tag.putInt("amount", visualAmount);
        tag.putBoolean("mixed", mixed);
        if (!visualFluid.isEmpty()) {
            tag.put("fluid", visualFluid.save(registries));
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 保存データには何も書かない（見た目はクライアントへの更新でだけ届く。読み込み直したら流れていない状態から始める）
        visualState = tag.getInt("state");
        inMask = tag.getInt("in");
        outMask = tag.getInt("out");
        visualAmount = tag.getInt("amount");
        mixed = tag.getBoolean("mixed");
        visualFluid = tag.contains("fluid") ? FluidStack.parseOptional(registries, tag.getCompound("fluid")) : FluidStack.EMPTY;
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
