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

/** 触媒装置の画面。触媒スロットと（装置によって）燃料か許可証のスロット、状態表示。 */
public class CatalystDeviceMenu extends AbstractContainerMenu {
    /** 触媒スロット（右上）。状態はアイコンと値で出すので、画面は標準の高さ。 */
    public static final int SLOT_X = 130;
    public static final int HEIGHT = 166;
    public static final int INV_Y = GuiFrame.inventoryY(HEIGHT);
    public static final int HOTBAR_Y = GuiFrame.hotbarY(HEIGHT);
    public static final int SLOT_Y = 24;
    /** 2つ目のスロット（燃料・許可証）の位置（触媒スロットの右）。 */
    public static final int EXTRA_X = 150;
    /** 失活触媒の出口（触媒スロットの左）。 */
    public static final int SPENT_X = 110;

    private final BlockPos pos;
    private final CatalystDeviceBlockEntity.Kind kind;
    private final SyncedInts data;

    public CatalystDeviceMenu(int containerId, Inventory inventory, BlockPos pos, CatalystDeviceBlockEntity.Kind kind,
                              IItemHandler slot, IItemHandler spent, SyncedInts data) {
        super(SinguloMenus.CATALYST_DEVICE.get(), containerId);
        this.pos = pos;
        this.kind = kind;
        this.data = data;
        addSlot(new NotifyingItemSlot(slot, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CatalystHelper.tierOf(stack) > 0;
            }
        });
        if (kind.extraSlot() != null) {
            addSlot(new NotifyingItemSlot(slot, 1, EXTRA_X, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return kind.extraAccepts(stack);
                }
            });
        }
        // 失活触媒の出口（取り出すだけ）
        addSlot(new NotifyingItemSlot(spent, 0, SPENT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public static CatalystDeviceMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CatalystDeviceBlockEntity.Kind kind = CatalystDeviceBlockEntity.Kind.values()[buf.readVarInt()];
        return new CatalystDeviceMenu(containerId, inventory, pos, kind, new ItemStackHandler(kind.extraSlot() != null ? 2 : 1),
                new ItemStackHandler(1), SyncedInts.client(CatalystDeviceBlockEntity.COUNT));
    }

    public CatalystDeviceBlockEntity.Kind kind() {
        return kind;
    }

    public int value(int index) {
        return data.getInt(index);
    }

    public static final int BUTTON_POWER = 0, BUTTON_EJECT = 1;

    /** 失活触媒の出口のスロットの番号。 */
    public int spentIndex() {
        return kind.extraSlot() != null ? 2 : 1;
    }

    public BlockPos pos() { return pos; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_POWER && player.level().getBlockEntity(pos) instanceof CatalystDeviceBlockEntity device) {
            device.togglePower();
            return true;
        }
        if (id == BUTTON_EJECT && player.level().getBlockEntity(pos) instanceof CatalystDeviceBlockEntity device) {
            device.ejectLoaded(player);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int device = spentIndex() + 1;
        if (index < device) {
            if (!moveItemStackTo(stack, device, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, device, false)) {
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
        return io.github.genichimaruo.singulo.multiblock.Blueprints.withinMenuReach(player, pos)
                && player.level().getBlockEntity(pos) instanceof CatalystDeviceBlockEntity;
    }
}
