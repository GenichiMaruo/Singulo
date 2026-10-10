package io.github.genichimaruo.singulo.multiblock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.genichimaruo.singulo.machine.MachineBlock;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** マルチブロックのコントローラ。加工の仕組みは汎用加工装置と同じで、形と大きさの判定を足したもの。 */
public class MultiblockControllerBlock extends MachineBlock {

    public MultiblockControllerBlock(Properties properties, MachineType type) {
        super(properties, type);
    }


    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (type()) {
            case CRYOGENIC_COOLING_TOWER -> new CoolingTowerControllerBlockEntity(pos, state);
            case PARTICLE_ACCELERATOR -> new AcceleratorControllerBlockEntity(pos, state);
            case DEGENERATE_COMPACTOR -> new FixedShapeControllerBlockEntity.Compactor(pos, state);
            case CASIMIR_CAVITY -> new FixedShapeControllerBlockEntity.Cavity(pos, state);
            default -> throw new IllegalStateException("Not a multiblock: " + type());
        };
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        return level.isClientSide ? null : withBoost((l, p, s, be) -> {
            if (be instanceof MachineBlockEntity machine) {
                MachineBlockEntity.serverTick(l, p, s, machine);
            }
        });
    }
}
