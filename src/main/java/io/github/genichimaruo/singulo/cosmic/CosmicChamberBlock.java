package io.github.genichimaruo.singulo.cosmic;

import io.github.genichimaruo.singulo.machine.SimpleMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** The controller is the sole owner of stored matter, energy and reserved work. */
public final class CosmicChamberBlock extends SimpleMachineBlock<CosmicChamberBlockEntity> {

    public CosmicChamberBlock(Properties p) {
        super(p, SinguloBlockEntities.COSMIC_CHAMBER, CosmicChamberBlockEntity::new, CosmicChamberBlockEntity::serverTick);
    }
    @Override
    public void onRemove(BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,BlockState replacement,boolean movedByPiston){
        if(!state.is(replacement.getBlock())&&!level.isClientSide&&level.getBlockEntity(pos) instanceof CosmicChamberBlockEntity be)
            be.retireForRemoval();
        super.onRemove(state,level,pos,replacement,movedByPiston);
    }
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack packed = new ItemStack(this);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CosmicChamberBlockEntity be) {
            BlockItem.setBlockEntityData(packed, be.getType(), be.saveWithoutMetadata());
            packed.getOrCreateTag().putBoolean("singulo:packed_device", true);
        }
        return List.of(packed);
    }
}
