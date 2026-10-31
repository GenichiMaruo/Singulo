package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.machine.GuiFrame;
import io.github.genichimaruo.singulo.machine.SyncedInts;
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

/** ドローン・ステーションの画面。左にメモリ・キット・生産アップグレード・許可証、右に3機ぶんの様子と電力。 */
public class DroneStationMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 170;
    public static final int MEMORY_X = 8, MEMORY_Y = 24, KIT_X = 8, KIT_Y = 46;
    public static final int PRODUCTION_X = 30, PRODUCTION_Y = 24, PERMIT_X = 30, PERMIT_Y = 46;
    /** 3機ぶんの様子（行）と電力のゲージ。 */
    public static final int UNIT_X = 56, UNIT_Y = 22, UNIT_ROW = 16, UNIT_W = 98;
    public static final int ENERGY_X = 160, ENERGY_Y = 22, ENERGY_H = 46;
    public static final int INV_X = GuiFrame.inventoryX(WIDTH), INV_Y = GuiFrame.inventoryY(HEIGHT), HOTBAR_Y = GuiFrame.hotbarY(HEIGHT);

    private final BlockPos pos;
    private final SyncedInts data;

    public DroneStationMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, SyncedInts data) {
        super(SinguloMenus.DRONE_STATION.get(), id);
        this.pos = pos;
        this.data = data;
        addSlot(new SlotItemHandler(items, DroneStationBlockEntity.SLOT_MEMORY, MEMORY_X, MEMORY_Y));
        addSlot(new SlotItemHandler(items, DroneStationBlockEntity.SLOT_KIT, KIT_X, KIT_Y));
        addSlot(new SlotItemHandler(items, DroneStationBlockEntity.SLOT_PRODUCTION, PRODUCTION_X, PRODUCTION_Y));
        addSlot(new SlotItemHandler(items, DroneStationBlockEntity.SLOT_PERMIT, PERMIT_X, PERMIT_Y));
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

    public static DroneStationMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new DroneStationMenu(id, inventory, buf.readBlockPos(), new ItemStackHandler(DroneStationBlockEntity.SLOTS),
                SyncedInts.client(DroneStationBlockEntity.COUNT));
    }

    public BlockPos pos() {
        return pos;
    }

    public int value(int index) {
        return data.getInt(index);
    }

    /** 枠のボタン（解体・また印刷する）。 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= DroneStationBlockEntity.BUTTON_RESUME + DroneStationBlockEntity.MAX_UNITS
                || !(player.level().getBlockEntity(pos) instanceof DroneStationBlockEntity station) || !station.mayOperate(player)) {
            return false;
        }
        station.press(id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int own = DroneStationBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, own, false)) {
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
                && player.level().getBlockEntity(pos) instanceof DroneStationBlockEntity station && station.mayOperate(player);
    }
}
