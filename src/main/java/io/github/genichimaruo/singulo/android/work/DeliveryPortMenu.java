package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** アンドロイド納品口の画面。27スロットの箱と、名前（作業指令台で納品先を選ぶときに出る。名前をクリックで変える）。 */
public class DeliveryPortMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 184;
    public static final int BOX_X = 8, BOX_Y = 24, INV_X = io.github.genichimaruo.singulo.machine.GuiFrame.inventoryX(WIDTH),
            INV_Y = io.github.genichimaruo.singulo.machine.GuiFrame.inventoryY(HEIGHT),
            HOTBAR_Y = io.github.genichimaruo.singulo.machine.GuiFrame.hotbarY(HEIGHT);

    private final BlockPos pos;

    public DeliveryPortMenu(int id, Inventory inventory, BlockPos pos, Container box) {
        super(SinguloMenus.ANDROID_DELIVERY_PORT.get(), id);
        this.pos = pos;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(box, col + row * 9, BOX_X + col * 18, BOX_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public static DeliveryPortMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        return new DeliveryPortMenu(id, inventory, buf.readBlockPos(), new SimpleContainer(DeliveryPortBlockEntity.SLOTS));
    }

    public BlockPos pos() {
        return pos;
    }

    /** 名前を付け直す（サーバー。RenameAndroidPayload から）。 */
    public void rename(Player player, String name) {
        if (player.level().getBlockEntity(pos) instanceof DeliveryPortBlockEntity port && port.mayOperate(player)) {
            port.setName(name);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int own = DeliveryPortBlockEntity.SLOTS;
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
                && player.level().getBlockEntity(pos) instanceof DeliveryPortBlockEntity port && port.mayOperate(player);
    }
}
