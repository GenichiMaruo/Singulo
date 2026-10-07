package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** 触媒装置の画面。触媒スロット1つと状態表示。 */
public class CatalystDeviceMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 80;
    public static final int SLOT_Y = 22;

    private final BlockPos pos;
    private final CatalystDeviceBlockEntity.Kind kind;
    private final SyncedInts data;

    public CatalystDeviceMenu(int containerId, Inventory inventory, BlockPos pos, CatalystDeviceBlockEntity.Kind kind,
                              IItemHandler slot, SyncedInts data) {
        super(SinguloMenus.CATALYST_DEVICE.get(), containerId);
        this.pos = pos;
        this.kind = kind;
        this.data = data;
        addSlot(new SlotItemHandler(slot, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CatalystHelper.tierOf(stack) > 0;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    public static CatalystDeviceMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CatalystDeviceBlockEntity.Kind kind = CatalystDeviceBlockEntity.Kind.values()[buf.readVarInt()];
        return new CatalystDeviceMenu(containerId, inventory, pos, kind, new ItemStackHandler(1),
                SyncedInts.client(CatalystDeviceBlockEntity.COUNT));
    }

    public CatalystDeviceBlockEntity.Kind kind() {
        return kind;
    }

    public int value(int index) {
        return data.getInt(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                && player.level().getBlockEntity(pos) instanceof CatalystDeviceBlockEntity;
    }
}
