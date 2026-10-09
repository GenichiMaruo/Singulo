package io.github.genichimaruo.singulo.multiblock;

import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 搬入出口。形成したコントローラが自分の位置を書き込み、壊れると消す（保存はせず、読み込み後の判定で付け直す）。
 */
public class PortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controller;

    public PortBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.PORT.get(), pos, state);
    }

    protected PortBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void link(@Nullable BlockPos controller) {
        if (!java.util.Objects.equals(this.controller, controller)) {
            this.controller = controller;
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
                // 先に敷いてあったケーブルにつなぎ直させる
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                // クライアントにも知らせる（右クリックでコントローラーの画面を開くため）
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        if (controller != null) {
            tag.putLong("controller", controller.asLong());
        }
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 同期のときだけ入っている（保存はしない。読み込み後の形の判定で付け直す）
        if (level != null && level.isClientSide) {
            controller = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
        }
    }

    /** つながっているコントローラの位置。未形成なら null。 */
    @Nullable
    public BlockPos controllerPos() {
        return controller;
    }

    /** コントローラの能力をそのまま返す（未形成なら null）。 */
    @Nullable
    public <T> T delegate(net.neoforged.neoforge.capabilities.BlockCapability<T, net.minecraft.core.Direction> capability) {
        if (controller == null || level == null || controller.equals(worldPosition)) {
            return null;
        }
        if (level.getBlockEntity(controller) instanceof MachineBlockEntity machine && machine.structureSize() <= 0) {
            return null;
        }
        return level.getCapability(capability, controller, null);
    }
}
