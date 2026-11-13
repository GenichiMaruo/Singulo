package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * SMESセル・SMESモジュールの画面: 蓄電量と出し入れの速さ、充電の枠（入れる枠 → 充電済みの物が出てくる枠）とインベントリ。
 */
public class SmesMenu extends AbstractContainerMenu {
    public static final int D_STORED = 0, D_CAPACITY = 1, D_IN = 2, D_OUT = 3, D_RATE = 4, COUNT = 5;
    public static final int WIDTH = 176, HEIGHT = 190;
    /** 充電の枠（入れる枠・出てくる枠）の位置。あいだに充電の進みの矢印。 */
    public static final int CHARGE_Y = 70, CHARGE_IN_X = 52, CHARGE_OUT_X = 108;

    private final BlockPos pos;
    private final SyncedInts data;

    public SmesMenu(int containerId, Inventory inventory, BlockPos pos, net.minecraftforge.items.IItemHandler charge, SyncedInts data) {
        super(SinguloMenus.SMES.get(), containerId);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
        addSlot(new net.minecraftforge.items.SlotItemHandler(charge, SmesCellBlockEntity.CHARGE_IN, CHARGE_IN_X, CHARGE_Y));
        addSlot(new net.minecraftforge.items.SlotItemHandler(charge, SmesCellBlockEntity.CHARGE_OUT, CHARGE_OUT_X, CHARGE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        int x = GuiFrame.inventoryX(WIDTH), y = GuiFrame.inventoryY(HEIGHT);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new net.minecraft.world.inventory.Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, col, x + col * 18, GuiFrame.hotbarY(HEIGHT)));
        }
    }

    public static SmesMenu client(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new SmesMenu(containerId, inventory, buf.readBlockPos(), new net.minecraftforge.items.ItemStackHandler(2) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == SmesCellBlockEntity.CHARGE_IN && io.github.genichimaruo.singulo.energy.ItemCharging.chargeable(stack);
            }
        }, SyncedInts.client(COUNT));
    }

    public int value(int index) {
        return data.getInt(index);
    }

    public BlockPos pos() { return pos; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), copy = stack.copy();
        boolean moved = index < 2 ? moveItemStackTo(stack, 2, slots.size(), true) : moveItemStackTo(stack, 0, 1, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return io.github.genichimaruo.singulo.multiblock.Blueprints.withinMenuReach(player, pos)
                && player.level().getBlockEntity(pos) instanceof SmesCellBlockEntity;
    }
}
