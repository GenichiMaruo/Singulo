package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** SMESセル・SMESモジュールの画面（スロットなし、蓄電量と出し入れの速さだけ）。 */
public class SmesMenu extends AbstractContainerMenu {
    public static final int D_STORED = 0, D_CAPACITY = 1, D_IN = 2, D_OUT = 3, D_RATE = 4, COUNT = 5;

    private final BlockPos pos;
    private final SyncedInts data;

    public SmesMenu(int containerId, Inventory inventory, BlockPos pos, SyncedInts data) {
        super(SinguloMenus.SMES.get(), containerId);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
    }

    public static SmesMenu client(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new SmesMenu(containerId, inventory, buf.readBlockPos(), SyncedInts.client(COUNT));
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
                && player.level().getBlockEntity(pos) instanceof SmesCellBlockEntity;
    }
}
