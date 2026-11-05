package io.github.genichimaruo.singulo.cosmic;

import io.github.genichimaruo.singulo.machine.SimpleMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** The controller is the sole owner of stored matter, energy and reserved work. */
public final class CosmicChamberBlock extends SimpleMachineBlock<CosmicChamberBlockEntity> {
    @Override protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec(){return simpleCodec(CosmicChamberBlock::new);}
    public CosmicChamberBlock(Properties p) {
        super(p, SinguloBlockEntities.COSMIC_CHAMBER, CosmicChamberBlockEntity::new, CosmicChamberBlockEntity::serverTick);
    }
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack packed = new ItemStack(this);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CosmicChamberBlockEntity be) {
            BlockItem.setBlockEntityData(packed, be.getType(), be.saveCustomOnly(params.getLevel().registryAccess()));
            packed.set(DataComponents.MAX_STACK_SIZE, 1);
        }
        return List.of(packed);
    }
}
