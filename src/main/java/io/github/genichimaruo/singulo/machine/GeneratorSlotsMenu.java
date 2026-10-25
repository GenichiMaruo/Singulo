package io.github.genichimaruo.singulo.machine;

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

/**
 * 入口と出口のスロットを持つ発電機の画面（極低温タービン: 冷媒カートリッジ → 空のカートリッジ、
 * ミュオン触媒核融合炉: ミュオン触媒 → 失活したミュオン触媒）。左に蓄電の目盛り、まん中に状態、右に入口と出口。
 */
public class GeneratorSlotsMenu extends AbstractContainerMenu {
    public static final int KIND_TURBINE = 0, KIND_FUSION = 1;
    /** 同期する値: 高温源の温度（K）・出力（×100）・蓄電・蓄電の上限・液体・液体の上限・種類ごとの値（効率% / 触媒の残り‰）・待機中か。 */
    public static final int D_HOT = 0, D_RATE_X100 = 1, D_ENERGY = 2, D_CAPACITY = 3, D_FLUID = 4, D_FLUID_CAP = 5, D_EXTRA = 6,
            D_STANDBY = 7, COUNT = 8;
    public static final int WIDTH = 176, HEIGHT = 166;
    public static final int IN_X = 134, IN_Y = 21, OUT_X = 134, OUT_Y = 51;
    public static final int INV_Y = 84;

    private final BlockPos pos;
    private final int kind;
    private final SyncedInts data;

    public GeneratorSlotsMenu(int containerId, Inventory inventory, BlockPos pos, int kind, IItemHandler items, SyncedInts data) {
        super(SinguloMenus.GENERATOR_SLOTS.get(), containerId);
        this.pos = pos;
        this.kind = kind;
        this.data = data;
        addSlot(new NotifyingItemSlot(items, 0, IN_X, IN_Y));
        addSlot(new NotifyingItemSlot(items, 1, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;                          // 出口: 取り出すだけ
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    public static GeneratorSlotsMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int kind = buf.readVarInt();
        return new GeneratorSlotsMenu(containerId, inventory, pos, kind, new ItemStackHandler(2), SyncedInts.client(COUNT));
    }

    public int kind() {
        return kind;
    }

    public int value(int index) {
        return data.getInt(index);
    }

    public BlockPos pos() { return pos; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!slots.get(0).mayPlace(stack) || !moveItemStackTo(stack, 0, 1, false)) {
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
                && player.level().getBlockEntity(pos) instanceof Owner;
    }

    /** この画面を開くブロックエンティティ。 */
    public interface Owner {
        SyncedInts slotsData();
    }
}
