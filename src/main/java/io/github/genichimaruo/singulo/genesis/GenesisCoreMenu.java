package io.github.genichimaruo.singulo.genesis;

import io.github.genichimaruo.singulo.registry.SinguloMenus;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 創世炉の画面。様子（段階・進み・不安定度・受電・作る物）は核のブロックエンティティがクライアントへ同期しているので、
 * 画面はそれを直接読む。メニューが持つのは「出来上がり」スロット（取り出すだけ）と、投入の切り替えボタンだけ。
 */
public class GenesisCoreMenu extends AbstractContainerMenu {
    public static final int BUTTON_FEED = 0;
    /** 画面の大きさと、スロットの位置。 */
    public static final int WIDTH = 236, HEIGHT = 206;
    public static final int OUTPUT_X = 210, OUTPUT_Y = 50;
    /** 作る物の枠（見本を置く。持っている物は減らない）。 */
    public static final int TEMPLATE_SLOT = 1, TEMPLATE_X = 100, TEMPLATE_Y = 50;
    /** インベントリのスロットの始まり。 */
    static final int FIRST_INVENTORY = 2;
    public static final int INVENTORY_X = 38, INVENTORY_Y = 124;

    private final BlockPos pos;

    public GenesisCoreMenu(int containerId, Inventory inventory, BlockPos pos, Container output, Container template) {
        super(SinguloMenus.GENESIS_CORE.get(), containerId);
        this.pos = pos;
        Player player = inventory.player;
        addSlot(new Slot(output, 0, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            /** 完成（質量生成）のときだけ出す。それ以外の段階では描かず、触れない。 */
            @Override
            public boolean isActive() {
                return complete(player);
            }
        });
        addSlot(new Slot(template, 0, TEMPLATE_X, TEMPLATE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player p) {
                return false;
            }

            @Override
            public boolean isActive() {
                return complete(player);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    public static GenesisCoreMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new GenesisCoreMenu(containerId, inventory, buf.readBlockPos(), new SimpleContainer(1), new SimpleContainer(1));
    }

    public BlockPos pos() {
        return pos;
    }

    /** 画面が読む核（クライアントでは同期された写し）。 */
    @Nullable
    public GenesisCoreBlockEntity core(Player player) {
        return player.level().getBlockEntity(pos) instanceof GenesisCoreBlockEntity core ? core : null;
    }

    private boolean complete(Player player) {
        return core(player) instanceof GenesisCoreBlockEntity core && core.stage() == GenesisCoreBlockEntity.COMPLETE;
    }

    /** 作る物の枠: 物を持ってクリックするとそれを作る物に決め（持っている物は減らない）、素手でクリックすると外す。 */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType type, Player player) {
        if (slotId == TEMPLATE_SLOT) {
            if (player instanceof ServerPlayer sp && core(player) instanceof GenesisCoreBlockEntity core && complete(player)) {
                if (getCarried().isEmpty()) {
                    core.clearTemplate();
                } else {
                    core.chooseTemplate(sp, getCarried());
                }
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_FEED && player instanceof ServerPlayer sp && core(player) instanceof GenesisCoreBlockEntity core) {
            core.toggleFeed(sp);
            return true;
        }
        return false;
    }

    /** シフトクリック: 出来上がりはインベントリへ。インベントリの物は、作る物に決める（物は動かさない）。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index >= FIRST_INVENTORY) {
            if (slot.hasItem() && player instanceof ServerPlayer sp && core(player) instanceof GenesisCoreBlockEntity core && complete(player)) {
                core.chooseTemplate(sp, slot.getItem());
            }
            return ItemStack.EMPTY;
        }
        if (index != 0 || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, FIRST_INVENTORY, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return core(player) != null && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
