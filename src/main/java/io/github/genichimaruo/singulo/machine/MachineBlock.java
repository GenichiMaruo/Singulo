package io.github.genichimaruo.singulo.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;

/** 汎用加工装置（焼成炉・圧縮機・電解槽・アーカイブ端末）。 */
public class MachineBlock extends AbstractMachineBlock {

    private final MachineType type;

    public MachineBlock(Properties properties, MachineType type) {
        super(properties);
        this.type = type;
    }

    public MachineType type() {
        return type;
    }


    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type == MachineType.TIME_CRYSTAL_INCUBATOR ? new TimeCrystalIncubatorBlockEntity(pos, state)
                : new MachineBlockEntity(pos, state);
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

    /** バケツなどの液体容器で右クリックすると、タンクに直接出し入れする。 */
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (type.tanks() > 0 && FluidUtil.getFluidHandler(stack).isPresent()
                && FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
