package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 置いたティーカップの色（染めていなければ白）。壊すとルートテーブルの copy_components で、この色ごとアイテムに戻る。 */
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

    /** アイテムの染めた色を受け取る（置いたとき）。 */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        DyedItemColor dyed = input.get(DataComponents.DYED_COLOR);
        color = dyed != null ? dyed.rgb() & 0xFFFFFF : DEFAULT_COLOR;
    }

    /** 壊したときに、アイテムへ色を写す。 */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (color != DEFAULT_COLOR) {
            builder.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("color", color);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        color = tag.contains("color") ? tag.getInt("color") : DEFAULT_COLOR;
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("color", color);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
