package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 置いたティーカップの色（染めていなければ白）。壊すとブロックのドロップ処理で、この色ごとアイテムに戻る。 */
public class TeacupBlockEntity extends BlockEntity {
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    private int color = DEFAULT_COLOR;

    public TeacupBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.TEACUP.get(), pos, state);
    }

    public int color() {
        return color;
    }

    public void setColor(int color) {
        this.color = color & 0xFFFFFF;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("color", color);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        color = tag.contains("color") ? tag.getInt("color") : DEFAULT_COLOR;
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("color", color);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
