package io.github.genichimaruo.singulo.wormhole;

import io.github.genichimaruo.singulo.item.ExoticCharge;
import io.github.genichimaruo.singulo.machine.SyncedInts;
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

/** ワームホール固定化装置の画面。左右の2つの喉（口を入れる）と、エキゾチック物質・口の筐体。 */
public class WormholeStabilizerMenu extends AbstractContainerMenu {
    public static final int D_PROGRESS_0 = 0, D_PROGRESS_1 = 1, D_ENERGY = 2, D_CAPACITY = 3, COUNT = 4;
    public static final int WIDTH = 200;
    public static final int HEIGHT = 206;
    /** スロットの位置（menu と screen で共有）: 左の喉・右の喉・燃料・筐体。 */
    public static final int[][] SLOT_POS = {{38, 46}, {146, 46}, {92, 88}, {116, 88}};
    public static final int INV_X = 20;
    public static final int INV_Y = 124;
    public static final int HOTBAR_Y = 182;

    private final BlockPos pos;
    private final SyncedInts data;

    public WormholeStabilizerMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, SyncedInts data,
                                  @Nullable WormholeStabilizerBlockEntity stabilizer) {
        super(SinguloMenus.WORMHOLE_STABILIZER.get(), containerId);
        this.pos = pos;
        this.data = data;
        for (int i = 0; i < WormholeStabilizerBlockEntity.SLOTS; i++) {
            final int index = i;
            addSlot(new SlotItemHandler(items, i, SLOT_POS[i][0], SLOT_POS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return index == WormholeStabilizerBlockEntity.SLOT_FUEL ? ExoticCharge.isExoticMatter(stack)
                            : index == WormholeStabilizerBlockEntity.SLOT_CASING ? WormholeStabilizerBlockEntity.isCasing(stack)
                            : stack.getItem() instanceof UnstableMouthItem;
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

    public static WormholeStabilizerMenu client(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new WormholeStabilizerMenu(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(WormholeStabilizerBlockEntity.SLOTS),
                SyncedInts.client(COUNT), null);
    }

    public int value(int index) {
        return data.getInt(index);
    }

    /** 喉 i の固定化の進み（0〜1）。 */
    public float progress(int i) {
        return value(i == 0 ? D_PROGRESS_0 : D_PROGRESS_1) / (float) WormholeStabilizerBlockEntity.STABILIZE_TICKS;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machine = WormholeStabilizerBlockEntity.SLOTS;
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (ExoticCharge.isExoticMatter(stack)) {
            if (!moveItemStackTo(stack, WormholeStabilizerBlockEntity.SLOT_FUEL, WormholeStabilizerBlockEntity.SLOT_FUEL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (WormholeStabilizerBlockEntity.isCasing(stack)) {
            if (!moveItemStackTo(stack, WormholeStabilizerBlockEntity.SLOT_CASING, WormholeStabilizerBlockEntity.SLOT_CASING + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof UnstableMouthItem) {
            if (!moveItemStackTo(stack, 0, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
                && player.level().getBlockEntity(pos) instanceof WormholeStabilizerBlockEntity;
    }
}
