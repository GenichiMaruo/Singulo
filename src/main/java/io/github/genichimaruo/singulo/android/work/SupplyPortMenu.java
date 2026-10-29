package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.machine.GuiFrame;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * アンドロイド資材口の画面。優先の登録8件（2列×4行）: 見本の欄（持っている物でクリックすると登録、何も持たずにクリックで外す）、
 * 向き（両方・取り出し・返却）、優先順位（−/+、1がいちばん先）。
 */
public class SupplyPortMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 206;
    public static final int ENTRY_X = 8, ENTRY_Y = 24, COLUMN_W = 80, ROW_H = 20;
    public static final int INV_X = GuiFrame.inventoryX(WIDTH), INV_Y = GuiFrame.inventoryY(HEIGHT), HOTBAR_Y = GuiFrame.hotbarY(HEIGHT);
    /** ボタンの番号: 登録 i の (i × 3 + 動き)。 */
    public static final int RANK_DOWN = 0, RANK_UP = 1, MODE = 2;

    private final BlockPos pos;
    private final Container entries;
    private final ContainerData data;

    public SupplyPortMenu(int id, Inventory inventory, BlockPos pos, Container entries, ContainerData data) {
        super(SinguloMenus.ANDROID_SUPPLY_PORT.get(), id);
        this.pos = pos;
        this.entries = entries;
        this.data = data;
        for (int i = 0; i < SupplyPortBlockEntity.ENTRIES; i++) {
            addSlot(new Slot(entries, i, entryX(i), entryY(i)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public static SupplyPortMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        return new SupplyPortMenu(id, inventory, buf.readBlockPos(), new SimpleContainer(SupplyPortBlockEntity.ENTRIES),
                new SimpleContainerData(SupplyPortBlockEntity.ENTRIES * 2));
    }

    public static int entryX(int i) {
        return ENTRY_X + (i % 2) * COLUMN_W;
    }

    public static int entryY(int i) {
        return ENTRY_Y + (i / 2) * ROW_H;
    }

    public BlockPos pos() {
        return pos;
    }

    public ItemStack entry(int i) {
        return entries.getItem(i);
    }

    public int rank(int i) {
        return data.get(i);
    }

    public int mode(int i) {
        return data.get(SupplyPortBlockEntity.ENTRIES + i);
    }

    /** 見本の欄: 持っている物を1個の見本として登録する（何も持っていなければ外す）。持っている物は減らない。 */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < SupplyPortBlockEntity.ENTRIES) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                ItemStack carried = getCarried();
                entries.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != entries && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= SupplyPortBlockEntity.ENTRIES * 3
                || !(player.level().getBlockEntity(pos) instanceof SupplyPortBlockEntity port) || !port.mayOperate(player)) {
            return false;
        }
        int i = id / 3;
        switch (id % 3) {
            case RANK_DOWN -> port.shiftRank(i, -1);
            case RANK_UP -> port.shiftRank(i, 1);
            default -> port.cycleMode(i);
        }
        return true;
    }

    /** シフトクリック: 持ち物の物を、空いた欄に見本として登録する（もう登録してあれば何もしない）。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int own = SupplyPortBlockEntity.ENTRIES;
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        if (index < own) {
            entries.setItem(index, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < own; i++) {
            if (ItemStack.isSameItem(entries.getItem(i), stack)) {
                return ItemStack.EMPTY;
            }
        }
        for (int i = 0; i < own; i++) {
            if (entries.getItem(i).isEmpty()) {
                entries.setItem(i, stack.copyWithCount(1));
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return io.github.genichimaruo.singulo.multiblock.Blueprints.withinMenuReach(player, pos)
                && player.level().getBlockEntity(pos) instanceof SupplyPortBlockEntity port && port.mayOperate(player);
    }
}
