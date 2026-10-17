package io.github.genichimaruo.singulo.forgecompat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

/** Forge 版: NeoForge の DeferredBlock と同じ形で使える、登録したブロックへの参照。 */
public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {
    public DeferredBlock(RegistryObject<T> object) {
        super(object);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }

    public ItemStack toStack() {
        return new ItemStack(get());
    }
}
