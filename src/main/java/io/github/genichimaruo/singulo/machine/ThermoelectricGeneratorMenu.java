package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** 熱電発電機の状態表示だけのメニュー（スロットなし）。 */
public class ThermoelectricGeneratorMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final SyncedInts data;

    public ThermoelectricGeneratorMenu(int containerId, BlockPos pos, SyncedInts data) {
        super(SinguloMenus.THERMOELECTRIC_GENERATOR.get(), containerId);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
    }

    public static ThermoelectricGeneratorMenu client(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new ThermoelectricGeneratorMenu(containerId, buf.readBlockPos(),
                SyncedInts.client(ThermoelectricGeneratorBlockEntity.COUNT));
    }

    public int value(int index) {
        return data.getInt(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return io.github.genichimaruo.singulo.multiblock.Blueprints.withinMenuReach(player, pos)
                && player.level().getBlockEntity(pos) instanceof GeneratorInfo;
    }
}
