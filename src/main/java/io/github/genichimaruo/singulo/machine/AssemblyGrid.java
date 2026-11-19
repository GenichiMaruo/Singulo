package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** A cell holds one kind of part. Only ordinary stacks leave the board or enter an ItemStack codec. */
abstract class AssemblyGrid extends ItemStackHandler {
    static final int REMOVAL_LIMIT = 64;
    static final ResourceLocation UPGRADE = Singulo.id("assembly_stock_upgrade");

    AssemblyGrid() { super(MachineRecipe.ASSEMBLY_CELLS); }

    boolean locksUpgrade() {
        for (int cell = 0; cell < getSlots(); cell++)
            if (getStackInSlot(cell).getCount() > REMOVAL_LIMIT) return true;
        return false;
    }

    static boolean isUpgrade(ItemStack stack) {
        return !stack.isEmpty() && stack.is(BuiltInRegistries.ITEM.get(UPGRADE));
    }

    static int upgradeLimit() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.STOCK_UPGRADE_LIMIT.get() : 1;
    }

    static int limit(ItemStack stack, ItemStack installed) {
        return limit(stack.getMaxStackSize(), installed);
    }

    static int limit(int normal, ItemStack installed) {
        if (normal <= 1 || !isUpgrade(installed)) return normal;
        int extra = ServerConfig.SPEC.isLoaded() ? ServerConfig.EXTRA_STACKS_PER_UPGRADE.get() : 3;
        long stacks = 1L + (long) extra * Math.min(upgradeLimit(), installed.getCount());
        return (int) Math.min(Integer.MAX_VALUE, normal * Math.min(Integer.MAX_VALUE, stacks));
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        return MachineStock.save(this, registries);
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        var saved = MachineStock.read(tag, registries);
        for (int i = 0; i < getSlots(); i++) setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY);
    }
}
