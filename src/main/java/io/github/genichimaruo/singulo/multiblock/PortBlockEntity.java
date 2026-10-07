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

    public void link(@Nullable BlockPos controller) {
        if (!java.util.Objects.equals(this.controller, controller)) {
            this.controller = controller;
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
                // 先に敷いてあったケーブルにつなぎ直させる
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
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
