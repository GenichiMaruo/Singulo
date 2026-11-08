package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 液体パイプ1本。液体は溜めない（中身がないので、壊しても液体は失われない）。受け渡しは {@link FluidPipeNetwork} がまとめて行う。 */
public class FluidPipeBlockEntity extends BlockEntity {
    private static final java.util.Set<FluidPipeBlockEntity> CLIENT = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static java.util.List<FluidPipeBlockEntity> clientPipes(){return java.util.List.copyOf(CLIENT);}
    private int visualState,inMask,outMask,visualAmount;
    private boolean mixed;
    private net.minecraftforge.fluids.FluidStack visualFluid=net.minecraftforge.fluids.FluidStack.EMPTY;
    private long published=Long.MIN_VALUE,observed=Long.MIN_VALUE;
    public int visualState(){return visualState;}
    public int inMask(){return inMask;}
    public int outMask(){return outMask;}
    public int visualAmount(){return visualAmount;}
    public boolean mixed(){return mixed;}
    public net.minecraftforge.fluids.FluidStack visualFluid(){return visualFluid;}
    public boolean fresh(){return level!=null&&observed!=Long.MIN_VALUE&&level.getGameTime()-observed<=40;}
    boolean publish(int state,int in,int out,int amount,net.minecraftforge.fluids.FluidStack fluid,boolean mixed){
        boolean changed=visualState!=state||inMask!=in||outMask!=out||visualAmount!=amount||this.mixed!=mixed
                ||!io.github.genichimaruo.singulo.compat.Legacy.sameFluid(visualFluid,fluid);
        visualState=state;inMask=in;outMask=out;visualAmount=amount;visualFluid=fluid;this.mixed=mixed;
        long now=level.getGameTime();if(published!=Long.MIN_VALUE&&now-published<(changed?10:20))return false;
        published=now;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);return true;
    }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(){
        var tag=new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("observed",published!=Long.MIN_VALUE&&level!=null&&level.getGameTime()-published<=40);
        tag.putInt("state",visualState);tag.putInt("in",inMask);tag.putInt("out",outMask);tag.putInt("amount",visualAmount);tag.putBoolean("mixed",mixed);
        if(!visualFluid.isEmpty())tag.put("fluid",visualFluid.writeToNBT(new net.minecraft.nbt.CompoundTag()));return tag;
    }
    @Override public void load(net.minecraft.nbt.CompoundTag tag){
        super.load(tag);visualState=tag.getInt("state");inMask=tag.getInt("in");outMask=tag.getInt("out");visualAmount=tag.getInt("amount");mixed=tag.getBoolean("mixed");
        visualFluid=tag.contains("fluid")?net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(tag.getCompound("fluid")):net.minecraftforge.fluids.FluidStack.EMPTY;
        observed=tag.getBoolean("observed")&&level!=null?level.getGameTime():Long.MIN_VALUE;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
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
        if(level!=null&&level.isClientSide)CLIENT.add(this);
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
        if(level!=null&&level.isClientSide)CLIENT.remove(this);
        leaveNetwork();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if(level!=null&&level.isClientSide)CLIENT.remove(this);
        leaveNetwork();
        super.setRemoved();
    }
}
