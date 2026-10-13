package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 護衛のアンドロイドの画面。自己修理キット（1スタック）と装甲板（1枚）のスロット、体力・充電・動き方の表示。 */
public class AndroidMenu extends AbstractContainerMenu {
    public static final int WIDTH = 210;
    public static final int HEIGHT = 216;
    /** 修理・装甲のスロットと解体のボタンを、同じ間隔（22）で並べる。 */
    public static final int REPAIR_X = 96, REPAIR_Y = 102;
    public static final int PLATE_X = 134, PLATE_Y = 102;
    public static final int DISMANTLE_X = 172, DISMANTLE_Y = 104;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = 134;
    public static final int HOTBAR_Y = 192;

    public static final int D_HP = 0, D_MAX_HP = 1, D_CHARGE = 2, D_MODE = 3, D_STATE = 4, D_ARMOR = 5, D_ALERT = 6, COUNT = 7;
    public static final int BUTTON_DISMANTLE = 0;

    @Nullable
    private final RuinAndroid android;
    private final SyncedInts data;

    public AndroidMenu(int containerId, Inventory inventory, RuinAndroid android) {
        this(containerId, inventory, android, android.inventory(), SyncedInts.server(COUNT, i -> switch (i) {
            case D_HP -> Math.round(android.getHealth() * 10);
            case D_MAX_HP -> Math.round(android.getMaxHealth() * 10);
            case D_CHARGE -> Math.round(100 * android.chargeFraction());
            case D_MODE -> android.mode();
            case D_STATE -> android.state();
            case D_ARMOR -> android.getArmorValue();
            case D_ALERT -> android.alert() ? 1 : 0;
            default -> 0;
        }));
    }

    private AndroidMenu(int containerId, Inventory inventory, @Nullable RuinAndroid android, Container items, SyncedInts data) {
        super(SinguloMenus.ANDROID.get(), containerId);
        this.android = android;
        this.data = data;
        addSlot(new Slot(items, RuinAndroid.SLOT_REPAIR, REPAIR_X, REPAIR_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RuinAndroid.isRepairKit(stack);
            }
        });
        addSlot(new Slot(items, RuinAndroid.SLOT_PLATE, PLATE_X, PLATE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RuinAndroid.isArmorPlate(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
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

    public static AndroidMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        RuinAndroid android = inventory.player.level().getEntity(id) instanceof RuinAndroid a ? a : null;
        return new AndroidMenu(containerId, inventory, android, new SimpleContainer(2), SyncedInts.client(COUNT));
    }

    /** 画面に描くアンドロイド（クライアント。見つからなければ null）。 */
    @Nullable
    public RuinAndroid android() {
        return android;
    }

    public int value(int index) {
        return data.getInt(index);
    }

    /** 名前を付け直す（サーバー。RenameAndroidPayload から）。 */
    public void rename(String name) {
        if (android != null) {
            android.rename(name);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_DISMANTLE && android != null) {
            android.dismantle();
            player.closeContainer();
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
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 2, false)) {
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
        return android == null || android.isAlive() && android.distanceToSqr(player) < 8 * 8;
    }
}
