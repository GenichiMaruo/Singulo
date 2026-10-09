package io.github.genichimaruo.singulo.reactor;

import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import javax.annotation.Nullable;
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

/** ペンローズ・リアクターの画面。種・燃料・抽出装置3種・副産物4つ。ボタンは「点火」と「スピン目標」。 */
public class PenroseReactorMenu extends AbstractContainerMenu {
    public static final int BUTTON_IGNITE = 0, BUTTON_SPIN = 1;
    /** 投入間隔の設定は BUTTON_INTERVAL + 間隔（tick）で送る。 */
    public static final int BUTTON_INTERVAL = 1000;
    public static final int WIDTH = 200;
    public static final int HEIGHT = 228;
    /** 持ち物の位置。 */
    public static final int INV_X = 20;
    public static final int INV_Y = 146;
    public static final int HOTBAR_Y = 204;
    /** スロットの位置（menu と screen で共有）。 */
    public static final int[][] SLOT_POS = {
            {10, 22}, {10, 46},                 // 種・燃料
            {32, 22}, {32, 46}, {32, 70},       // ジェット・ホーキング・エルゴ
            {176, 22}, {176, 42}, {176, 62}, {176, 82}, // 副産物
    };

    private final BlockPos pos;
    private final SyncedInts data;
    @Nullable
    private final PenroseReactorBlockEntity reactor;

    public PenroseReactorMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, SyncedInts data,
                              @Nullable PenroseReactorBlockEntity reactor) {
        super(SinguloMenus.PENROSE_REACTOR.get(), containerId);
        this.pos = pos;
        this.data = data;
        this.reactor = reactor;
        for (int i = 0; i < PenroseReactorBlockEntity.SLOTS; i++) {
            boolean out = i >= PenroseReactorBlockEntity.SLOT_OUT;
            final int index = i;
            addSlot(new SlotItemHandler(items, i, SLOT_POS[i][0], SLOT_POS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !out && (reactor == null || reactor.items().isItemValid(index, stack));
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

    public static PenroseReactorMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new PenroseReactorMenu(containerId, inventory, buf.readBlockPos(),
                new ItemStackHandler(PenroseReactorBlockEntity.SLOTS), SyncedInts.client(PenroseReactorBlockEntity.COUNT), null);
    }

    public int value(int index) {
        return data.getInt(index);
    }

    /** 直前の tick の出力（FE/t）。 */
    public long output() {
        return ((long) value(PenroseReactorBlockEntity.D_OUT_HI) << 32) | (value(PenroseReactorBlockEntity.D_OUT_LO) & 0xFFFFFFFFL);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (reactor == null) {
            return false;
        }
        if (id == BUTTON_IGNITE) {
            return reactor.ignite();
        }
        if (id == BUTTON_SPIN) {
            reactor.cycleSpinTarget();
            return true;
        }
        if (id >= BUTTON_INTERVAL && id <= BUTTON_INTERVAL + PenroseReactorBlockEntity.MAX_FEED_INTERVAL) {
            reactor.setFeedInterval(id - BUTTON_INTERVAL);
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
        int machine = PenroseReactorBlockEntity.SLOTS;
        if (index < machine) {
            if (!moveItemStackTo(stack, machine, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, PenroseReactorBlockEntity.SLOT_OUT, false)) {
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
                && player.level().getBlockEntity(pos) instanceof PenroseReactorBlockEntity;
    }
}
