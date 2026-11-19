package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.registry.SinguloMenus;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class MachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_CYCLE_MODE = 0, BUTTON_POWER = 1, BUTTON_MAKE_FREE = 2;
    /** 作るものの指定を外す。BUTTON_SELECT + n は {@link RecipeSelection#selectable} の n 番を指定する。 */
    public static final int BUTTON_CLEAR_SELECTION = 3, BUTTON_SELECT = 100;
    /** 装填中の触媒を取り出す。 */
    public static final int BUTTON_EJECT_CATALYST = 4;

    private final MachineType type;
    private final BlockPos pos;
    private final SyncedInts data;
    @Nullable
    private final MachineBlockEntity machine;
    @Nullable private Set<Slot> assemblyDrag;
    private int assemblyDragType;
    public final MachineLayout layout;

    public MachineMenu(int containerId, Inventory inventory, MachineBlockEntity machine) {
        this(containerId, inventory, machine.type(), machine.getBlockPos(), machine.items(),
                machine.type() == MachineType.PRECISION_ASSEMBLER ? machine.assemblyGrid() : null, machine.syncData(), machine);
    }

    public static MachineMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        MachineType type = MachineType.values()[buf.readVarInt()];
        int synced = MachineBlockEntity.syncedCount(type);
        return new MachineMenu(containerId, inventory, type, pos, new ItemStackHandler(type.itemSlots()),
                type == MachineType.PRECISION_ASSEMBLER ? new ItemStackHandler(MachineRecipe.ASSEMBLY_CELLS) : null, SyncedInts.client(synced), null);
    }

    private MachineMenu(int containerId, Inventory inventory, MachineType type, BlockPos pos, ItemStackHandler handler,
                        @Nullable ItemStackHandler assemblyGrid, SyncedInts data, @Nullable MachineBlockEntity machine) {
        super(SinguloMenus.MACHINE.get(), containerId);
        this.type = type;
        this.pos = pos;
        this.data = data;
        this.machine = machine;
        this.layout = new MachineLayout(type);
        addDataSlots(data);

        for (int i = 0; i < type.inputSlots(); i++) {
            addSlot(new StockSlot(handler, i, layout.inputX[i], layout.inputY[i], false));
        }
        for (int i = 0; i < type.outputSlots(); i++) {
            addSlot(new StockSlot(handler, type.outputSlot() + i, layout.outputX, layout.outputY[i], false));
        }
        if (type.hasCatalystSlot()) {
            addSlot(new NotifyingItemSlot(handler, type.catalystSlot(), layout.catalystX, layout.catalystY) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return MachineBlockEntity.isCatalyst(stack);
                }
            });
        }
        addSlot(new NotifyingItemSlot(handler, type.upgradeSlot(), layout.upgradeX, layout.upgradeY) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return MachineBlockEntity.isUpgrade(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new NotifyingItemSlot(handler, type.capacitySlot(), layout.capacityX, layout.capacityY) {
            @Override
            public boolean mayPlace(ItemStack stack) { return CapacityUpgrades.tier(stack) > 0; }
            @Override
            public int getMaxStackSize() { return 1; }
        });
        if (assemblyGrid != null) {
            addSlot(new NotifyingItemSlot(handler, type.spentCatalystSlot(), layout.spentX, layout.spentY) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
            addSlot(new StockSlot(handler, type.assemblyUpgradeSlot(), layout.upgradeX, GuiFrame.railSlotY(GuiFrame.RAIL_HELP), false) {
                @Override
                public boolean mayPlace(ItemStack stack) { return AssemblyGrid.isUpgrade(stack); }
                @Override
                public boolean mayPickup(Player player) {
                    if (machine == null) {
                        for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
                            Slot slot = getSlot(assemblySlot(cell));
                            if (slot.getItem().getCount() + assemblyExcess(slot) > AssemblyGrid.REMOVAL_LIMIT) return false;
                        }
                    }
                    return super.mayPickup(player);
                }
                @Override
                public int getMaxStackSize() { return AssemblyGrid.upgradeLimit(); }
                @Override
                public int getMaxStackSize(ItemStack stack) { return Math.min(stack.getMaxStackSize(), getMaxStackSize()); }
            });
            for (int cell = 0; cell < MachineRecipe.ASSEMBLY_CELLS; cell++) {
                addSlot(new StockSlot(assemblyGrid, cell, MachineLayout.assemblyX(cell), MachineLayout.assemblyY(cell), true));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, layout.inventoryX + col * 18, GuiFrame.inventoryY(layout.height) + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, layout.inventoryX + col * 18, GuiFrame.hotbarY(layout.height)));
        }
    }

    public MachineType type() {
        return type;
    }

    public int assemblySlot(int cell) {
        if (type != MachineType.PRECISION_ASSEMBLER || cell < 0 || cell >= MachineRecipe.ASSEMBLY_CELLS) {
            throw new IndexOutOfBoundsException("No assembly cell " + cell);
        }
        return type.itemSlots() + cell;
    }

    public int playerSlotStart() {
        return type.itemSlots() + (type == MachineType.PRECISION_ASSEMBLER ? MachineRecipe.ASSEMBLY_CELLS : 0);
    }

    /**
     * How many of {@code stack} the machine slot holds, by the same rules as the machine's item handler. Unlike
     * SlotItemHandler#getMaxStackSize(ItemStack), it never empties and refills the slot to find out.
     */
    public int stackLimit(int slot, ItemStack stack) {
        if (getSlot(slot) instanceof StockSlot stock) {
            if (!stock.board && slot == type.assemblyUpgradeSlot()) return AssemblyGrid.upgradeLimit();
            if (!stock.board) return MachineStock.limit(stack, slot < type.inputSlots());
            return AssemblyGrid.limit(stack, transferStack(type.assemblyUpgradeSlot()));
        }
        int limit = Math.min(getSlot(slot).getMaxStackSize(), stack.getMaxStackSize());
        if (slot == type.catalystSlot() && type.hasCatalystSlot()) return Math.min(CatalystMagazine.SUPPLY_LIMIT, limit);
        return slot < type.inputSlots() && io.github.genichimaruo.singulo.item.UsesHelper.hasUses(stack) ? 1 : limit;
    }

    /** Vanilla moves one legal stack. The reserve is synchronized as a full 32-bit quantity. */
    private class StockSlot extends SlotItemHandler {
        private final ItemStackHandler grid;
        private final SyncedInts excess;

        private final boolean board;

        StockSlot(ItemStackHandler grid, int cell, int x, int y, boolean board) {
            super(grid, cell, x, y);
            this.board = board;
            this.grid = grid;
            excess = machine == null ? SyncedInts.client(1) : SyncedInts.server(1, ignored -> {
                ItemStack stack = grid.getStackInSlot(cell);
                return Math.max(0, stack.getCount() - stack.getMaxStackSize());
            });
            addDataSlots(excess);
        }

        @Override
        public ItemStack getItem() { return MachineStock.visible(grid.getStackInSlot(index)); }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return !board && index < type.inputSlots() && io.github.genichimaruo.singulo.item.UsesHelper.hasUses(stack) ? 1 : stack.getMaxStackSize();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return (board ? !MachineBlockEntity.isCatalyst(stack) : index < type.inputSlots()) && super.mayPlace(stack);
        }

        void setTotal(ItemStack stack) {
            grid.setStackInSlot(index, machine == null ? MachineStock.visible(stack) : stack);
        }

        @Override
        public void setChanged() {
            // Vanilla also edits ordinary stacks in place.
            grid.setStackInSlot(index, grid.getStackInSlot(index));
        }

        @Override
        public void setByPlayer(ItemStack replacement, ItemStack previous) {
            int reserved = excess.getInt(0);
            if (machine != null && reserved > 0) {
                ItemStack part = replacement.isEmpty() ? previous : replacement;
                setTotal(part.copyWithCount((int) Math.min(Integer.MAX_VALUE, (long) reserved + replacement.getCount())));
            } else {
                super.setByPlayer(replacement, previous);
            }
        }

        @Override
        public ItemStack safeInsert(ItemStack stack, int increment) {
            if (!mayPlace(stack)) return stack;
            ItemStack visible = getItem();
            if (!visible.isEmpty() && !ItemStack.isSameItemSameComponents(visible, stack)) return stack;
            int count = Math.clamp((long) visible.getCount() + excess.getInt(0), 0, Integer.MAX_VALUE);
            int added = Math.min(Math.min(stack.getCount(), increment), stackLimit(board ? assemblySlot(index) : index, stack) - count);
            if (added > 0) {
                setTotal(stack.copyWithCount(count + added));
                stack.shrink(added);
            }
            return stack;
        }
    }

    public int assemblyExcess(Slot slot) {
        return slot instanceof StockSlot cell ? cell.excess.getInt(0) : 0;
    }

    public ItemStack transferStack(int slot) {
        Slot target = getSlot(slot);
        ItemStack visible = target.getItem();
        return visible.copyWithCount(Math.clamp((long) visible.getCount() + assemblyExcess(target), 0, Integer.MAX_VALUE));
    }

    void setTransferStack(int slot, ItemStack stack) {
        if (getSlot(slot) instanceof StockSlot cell) cell.setTotal(stack);
        else getSlot(slot).set(stack);
    }

    @Override
    public void clicked(int slot, int button, ClickType click, Player player) {
        if (click == ClickType.QUICK_CRAFT && handleAssemblyDrag(slot, button, player)) return;
        if (click != ClickType.QUICK_CRAFT) assemblyDrag = null;
        if (slot >= 0 && slot < slots.size() && assemblyExcess(getSlot(slot)) > 0) {
            // A swap can hand out one stack. A different item cannot replace the parts still in reserve.
            ItemStack incoming = switch (click) {
                case PICKUP -> getCarried();
                case SWAP -> button >= 0 && button < 9 || button == 40 ? player.getInventory().getItem(button) : ItemStack.EMPTY;
                default -> ItemStack.EMPTY;
            };
            if (!incoming.isEmpty() && !ItemStack.isSameItemSameComponents(getSlot(slot).getItem(), incoming)) return;
        }
        super.clicked(slot, button, click, player);
    }

    /** Vanilla drag clips each destination at the item's ordinary stack size. Distribute through safeInsert
     * so a partially stocked board can receive its share alongside ordinary inventory slots. */
    private boolean handleAssemblyDrag(int index, int button, Player player) {
        int stage = getQuickcraftHeader(button), mode = getQuickcraftType(button);
        if (stage == 0) {
            assemblyDrag = null;
            if (type != MachineType.PRECISION_ASSEMBLER || mode > 1 || getCarried().isEmpty()
                    || !AssemblyGrid.isUpgrade(getSlot(type.assemblyUpgradeSlot()).getItem())) return false;
            assemblyDrag = new LinkedHashSet<>();
            assemblyDragType = mode;
            return true;
        }
        if (assemblyDrag == null) return false;
        ItemStack held = getCarried();
        if (mode == assemblyDragType && !held.isEmpty()) {
            if (stage == 1 && index >= 0 && index < slots.size()) {
                Slot target = getSlot(index);
                if (held.getCount() > assemblyDrag.size() && canItemQuickReplace(target, held, true)
                        && target.mayPlace(held) && canDragTo(target)) assemblyDrag.add(target);
                return true;
            }
            if (stage == 2 && assemblyDrag.size() == 1) {
                int target = assemblyDrag.iterator().next().index;
                assemblyDrag = null;
                clicked(target, mode, ClickType.PICKUP, player);
                return true;
            }
            if (stage == 2 && !assemblyDrag.isEmpty()) {
                int share = getQuickCraftPlaceCount(assemblyDrag, mode, held);
                for (Slot target : assemblyDrag) target.safeInsert(held, share);
            }
        }
        assemblyDrag = null;
        return true;
    }

    public BlockPos pos() {
        return pos;
    }

    public double massValue(boolean target) {
        int index = MachineBlockEntity.assemblyPreviewIndex(type) + (target ? 2 : 0);
        return data.getDouble(index);
    }

    public int value(int index) {
        return data.getInt(index);
    }

    /** Active catalyst is stored outside the editable reserve slot. Never move it during JEI transfer. */
    public ItemStack loadedCatalyst() {
        if (machine != null) return machine.magazine().loaded().copy();
        int item = value(MachineBlockEntity.D_LOADED_ITEM);
        if (!type.hasCatalystSlot() || item < 0) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(item));
        int remaining = Math.max(0, value(MachineBlockEntity.D_LOADED_USES));
        int max = Math.max(io.github.genichimaruo.singulo.item.UsesHelper.baseMax(stack), remaining);
        stack.set(io.github.genichimaruo.singulo.registry.SinguloComponents.USES.get(),
                new io.github.genichimaruo.singulo.item.UsesData(max - remaining, max, 0));
        return stack;
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
            case BUTTON_CLEAR_SELECTION -> {
                if (!type.selectsRecipe()) return false;
                machine.selectRecipe(-1);
            }
            case BUTTON_EJECT_CATALYST -> machine.ejectLoaded(player);
            default -> {
                if (id < BUTTON_SELECT || !type.selectsRecipe()) {
                    return false;
                }
                machine.selectRecipe(id - BUTTON_SELECT);
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = playerSlotStart();
        int inputStart = type == MachineType.PRECISION_ASSEMBLER ? assemblySlot(0) : 0;
        int inputEnd = type == MachineType.PRECISION_ASSEMBLER ? machineSlots : type.inputSlots();
        int upgradeIndex = type.upgradeSlot();
        int catalystIndex = type.catalystSlot();
        if (slot instanceof StockSlot) {
            ItemStack moving = original.copy();
            if (!moveItemStackTo(moving, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
            slot.remove(original.getCount() - moving.getCount());
            return original;
        } else if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (type == MachineType.PRECISION_ASSEMBLER && AssemblyGrid.isUpgrade(stack)) {
            int target = type.assemblyUpgradeSlot();
            int before = stack.getCount();
            getSlot(target).safeInsert(stack);
            if (stack.getCount() == before) return ItemStack.EMPTY;
        } else if (CapacityUpgrades.tier(stack) > 0) {
            // 容量の専用枠が空いていれば装着し、埋まっていれば材料の欄へ（Mk1・Mk2 は上の段階の材料にもなる）
            if (!moveItemStackTo(stack, type.capacitySlot(), type.capacitySlot() + 1, false)
                    && !moveToInputs(stack, inputStart, inputEnd)) {
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
        } else if (io.github.genichimaruo.singulo.item.UsesHelper.hasUses(stack)) {
            // A single template per shift-click, rather than filling every input with originals.
            boolean moved = false;
            for (int i = inputStart; i < inputEnd && !moved; i++) {
                moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) return ItemStack.EMPTY;
        } else if (!moveToInputs(stack, inputStart, inputEnd)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    private boolean moveToInputs(ItemStack stack, int start, int end) {
        int before = stack.getCount();
        for (int i = start; i < end && !stack.isEmpty(); i++) {
            if (getSlot(i).hasItem()) getSlot(i).safeInsert(stack);
        }
        if (!stack.isEmpty()) moveItemStackTo(stack, start, end, false);
        return stack.getCount() < before;
    }

    @Override
    public boolean stillValid(Player player) {
        return io.github.genichimaruo.singulo.multiblock.Blueprints.withinMenuReach(player, pos)
                && player.level().getBlockEntity(pos) instanceof MachineBlockEntity;
    }
}
