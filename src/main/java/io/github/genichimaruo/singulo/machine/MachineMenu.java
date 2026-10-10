package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.registry.SinguloMenus;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public class MachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_CYCLE_MODE = 0, BUTTON_POWER = 1, BUTTON_MAKE_FREE = 2;

    private final MachineType type;
    private final BlockPos pos;
    private final SyncedInts data;
    @Nullable
    private final MachineBlockEntity machine;
    public final MachineLayout layout;

    public MachineMenu(int containerId, Inventory inventory, MachineBlockEntity machine) {
        this(containerId, inventory, machine.type(), machine.getBlockPos(), machine.items(), machine.syncData(), machine);
    }

    public static MachineMenu client(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        MachineType type = MachineType.values()[buf.readVarInt()];
        int synced = MachineBlockEntity.syncedCount(type);
        return new MachineMenu(containerId, inventory, type, pos, new ItemStackHandler(type.itemSlots()),
                SyncedInts.client(synced), null);
    }

    private MachineMenu(int containerId, Inventory inventory, MachineType type, BlockPos pos, IItemHandler handler,
                        SyncedInts data, @Nullable MachineBlockEntity machine) {
        super(SinguloMenus.MACHINE.get(), containerId);
        this.type = type;
        this.pos = pos;
        this.data = data;
        this.machine = machine;
        this.layout = new MachineLayout(type);

        for (int i = 0; i < type.inputSlots(); i++) {
            addSlot(new SlotItemHandler(handler, i, layout.inputX[i], layout.inputY[i]));
        }
        for (int i = 0; i < type.outputSlots(); i++) {
            addSlot(new SlotItemHandler(handler, type.outputSlot() + i, layout.outputX, layout.outputY[i]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        if (type.hasCatalystSlot()) {
            addSlot(new SlotItemHandler(handler, type.catalystSlot(), layout.catalystX, layout.catalystY) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return MachineBlockEntity.isCatalyst(stack);
                }
            });
        }
        addSlot(new SlotItemHandler(handler, type.upgradeSlot(), layout.sideColumnX + 4, MachineLayout.UPGRADE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return MachineBlockEntity.isUpgrade(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, layout.inventoryX + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, layout.inventoryX + col * 18, 142));
        }
        addDataSlots(data);
    }

    public MachineType type() {
        return type;
    }

    public BlockPos pos() {
        return pos;
    }

    public int value(int index) {
        return data.getInt(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) {
            return false;
        }
        switch (id) {
            case BUTTON_CYCLE_MODE -> machine.cycleMode();
            case BUTTON_POWER -> machine.togglePower();
            case BUTTON_MAKE_FREE -> machine.toggleMakeFree();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = type.itemSlots();
        int upgradeIndex = machineSlots - 1;                 // メニュー上の並び: 入力・出力・（触媒）・アップグレード
        int catalystIndex = type.hasCatalystSlot() ? machineSlots - 2 : -1;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (MachineBlockEntity.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, upgradeIndex, upgradeIndex + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (catalystIndex >= 0 && MachineBlockEntity.isCatalyst(stack)) {
            if (!moveItemStackTo(stack, catalystIndex, catalystIndex + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, type.inputSlots(), false)) {
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
                && player.level().getBlockEntity(pos) instanceof MachineBlockEntity;
    }
}
