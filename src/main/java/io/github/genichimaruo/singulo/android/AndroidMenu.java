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

/**
 * 護衛のアンドロイドの画面。2ページある。
 * <ul>
 * <li>様子: 体力・充電・動き方の表示と、自己修理キット（1スタック）・装甲板（1枚）のスロット。</li>
 * <li>装備と持ち物: アップグレード（バッテリー2個まで・バックパック・採掘）、作業のオン／オフ、
 * 持ち物（5スロット）とバックパックの中（縦4×横6。バックパックを積んでいるときだけ）。</li>
 * </ul>
 * ページは画面（クライアント）だけが切り替え、見えないページのスロットは隠す。
 */
public class AndroidMenu extends AbstractContainerMenu {
    public static final int WIDTH = 210;
    public static final int HEIGHT = 216;
    /** 修理・装甲のスロットと解体のボタンを、同じ間隔（22）で並べる。 */
    public static final int REPAIR_X = 96, REPAIR_Y = 102;
    public static final int PLATE_X = 134, PLATE_Y = 102;
    public static final int DISMANTLE_X = 172, DISMANTLE_Y = 104;
    /** 装備と持ち物のページ: 左にアップグレードの列、中ほどにバックパック（6×4）、右に持ち物の列（5）。 */
    public static final int UPGRADE_X = 22, BATTERY_Y = 32, BACKPACK_Y = 60, MINING_Y = 88;
    /** 道具の枠（採掘・伐採）と、収納の枠（バックパック・タンク）は、左右に並べる。 */
    public static final int MINING_X = 12, FELLING_X = 32, BACKPACK_X = 12, TANK_X = 32;
    public static final int PACK_X = 58, PACK_Y = 32;
    public static final int CARRY_X = 184, CARRY_Y = 32;
    public static final int WORK_X = 8, WORK_Y = 108, WORK_W = 44, WORK_H = 14;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = 134;
    public static final int HOTBAR_Y = 192;

    public static final int PAGE_STATUS = 0, PAGE_GEAR = 1, PAGE_DECOR = 2, PAGE_LOG = 3, PAGES = 4;
    /** 装飾のページ: 姿の右に、頭・背中/腰・手の枠を縦に並べる。 */
    public static final int DECOR_X = 96, DECOR_Y = 34, DECOR_STEP = 28;

    public static final int D_HP = 0, D_MAX_HP = 1, D_CHARGE = 2, D_MODE = 3, D_STATE = 4, D_ARMOR = 5, D_ALERT = 6, D_WORK = 7,
            COUNT = 8;
    public static final int BUTTON_DISMANTLE = 0, BUTTON_ESCORT = 1, BUTTON_WORK = 2;
    /** タンク i に、カーソルの入れ物を使う（BUTTON_TANK_USE + i）・中身を捨てる（BUTTON_TANK_CLEAR + i）。 */
    public static final int BUTTON_TANK_USE = 10, BUTTON_TANK_CLEAR = 20;
    /** 化学物質のタンク i に、カーソルの入れ物を使う・中身を捨てる。 */
    public static final int BUTTON_CHEM_USE = 30, BUTTON_CHEM_CLEAR = 40;

    @Nullable
    private final RuinAndroid android;
    private final Container items;
    private final SyncedInts data;
    /** 見ているページ（クライアント）。 */
    private int page = PAGE_STATUS;

    public AndroidMenu(int containerId, Inventory inventory, RuinAndroid android) {
        this(containerId, inventory, android, android.inventory(), SyncedInts.server(COUNT, i -> switch (i) {
            case D_HP -> Math.round(android.getHealth() * 10);
            case D_MAX_HP -> Math.round(android.getMaxHealth() * 10);
            case D_CHARGE -> Math.round(100 * android.chargeFraction());
            case D_MODE -> android.mode() == RuinAndroid.MODE_FOLLOW && !android.escort() ? AndroidStationBlockEntity.MODE_FOLLOW_ONLY
                    : android.mode();
            case D_STATE -> android.state();
            case D_ARMOR -> android.getArmorValue();
            case D_ALERT -> android.alert() ? 1 : 0;
            case D_WORK -> android.workEnabled() ? 1 : 0;
            default -> 0;
        }));
    }

    private AndroidMenu(int containerId, Inventory inventory, @Nullable RuinAndroid android, Container items, SyncedInts data) {
        super(SinguloMenus.ANDROID.get(), containerId);
        this.android = android;
        this.items = items;
        this.data = data;
        addSlot(new PageSlot(items, RuinAndroid.SLOT_REPAIR, REPAIR_X, REPAIR_Y, PAGE_STATUS, 64) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RuinAndroid.isRepairKit(stack);
            }
        });
        addSlot(new PageSlot(items, RuinAndroid.SLOT_PLATE, PLATE_X, PLATE_Y, PAGE_STATUS, 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RuinAndroid.isArmorPlate(stack);
            }
        });
        addSlot(new PageSlot(items, RuinAndroid.SLOT_BATTERY, UPGRADE_X, BATTERY_Y, PAGE_GEAR, RuinAndroid.MAX_BATTERIES) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !working() && RuinAndroid.isBatteryUpgrade(stack);
            }
        });
        addSlot(new PageSlot(items, RuinAndroid.SLOT_BACKPACK, BACKPACK_X, BACKPACK_Y, PAGE_GEAR, 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !working() && RuinAndroid.isBackpackUpgrade(stack);
            }

            /** 中に物が入っている間は、バックパックを外せない。 */
            @Override
            public boolean mayPickup(Player player) {
                return !working() && packEmpty();
            }
        });
        addSlot(new PageSlot(items, RuinAndroid.SLOT_MINING, MINING_X, MINING_Y, PAGE_GEAR, 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !working() && RuinAndroid.isMiningUpgrade(stack);
            }
        });
        for (int i = 0; i < RuinAndroid.CARRY_SLOTS; i++) {
            addSlot(new PageSlot(items, RuinAndroid.CARRY_START + i, CARRY_X, CARRY_Y + i * 18, PAGE_GEAR, 64));
        }
        for (int i = 0; i < RuinAndroid.PACK_SLOTS; i++) {
            addSlot(new PageSlot(items, RuinAndroid.PACK_START + i, PACK_X + (i % 6) * 18, PACK_Y + (i / 6) * 18, PAGE_GEAR, 64) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !working() && hasBackpack();
                }
            });
        }
        addSlot(new PageSlot(items, RuinAndroid.SLOT_FELLING, FELLING_X, MINING_Y, PAGE_GEAR, 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !working() && RuinAndroid.isFellingUpgrade(stack);
            }
        });
        addSlot(new PageSlot(items, RuinAndroid.SLOT_TANK, TANK_X, BACKPACK_Y, PAGE_GEAR, 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Mekanism がなければ、化学物質だけのタンク（ガスタンク）は何もできないので積まない
                return !working() && RuinAndroid.isTankUpgrade(stack)
                        && (RuinAndroid.fluidCapacity(stack) > 0 || RuinAndroid.chemCapacity(stack) > 0);
            }

            /** 中に流体が残っている間は、タンクを外せない。 */
            @Override
            public boolean mayPickup(Player player) {
                return !working() && (android == null || android.tanksEmpty());
            }
        });
        for (int i = 0; i < RuinAndroid.DECOR_SLOTS; i++) {
            int part = i;
            addSlot(new PageSlot(items, RuinAndroid.SLOT_DECOR + i, DECOR_X, DECOR_Y + i * DECOR_STEP, PAGE_DECOR, 1) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return AndroidAccessoryItem.isAccessory(stack, part);
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

    /** ページごとに出し入れするスロット（見えないページのものは隠す）。 */
    private class PageSlot extends Slot {
        private final int slotPage;
        private final int limit;

        PageSlot(Container container, int index, int x, int y, int page, int limit) {
            super(container, index, x, y);
            this.slotPage = page;
            this.limit = limit;
        }

        @Override
        public boolean isActive() {
            return page == slotPage;
        }

        @Override
        public int getMaxStackSize() {
            return limit;
        }

        /** 作業中は、アップグレード・持ち物・バックパックの中を出し入れできない（修理キットと装甲板はできる）。 */
        private boolean locked() {
            return slotPage == PAGE_GEAR && android != null && android.isWorking();
        }

        @Override
        public boolean mayPickup(Player player) {
            return !locked() && super.mayPickup(player);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !locked() && super.mayPlace(stack);
        }
    }

    public static AndroidMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        RuinAndroid android = inventory.player.level().getEntity(id) instanceof RuinAndroid a ? a : null;
        return new AndroidMenu(containerId, inventory, android, new SimpleContainer(RuinAndroid.INVENTORY_SIZE), SyncedInts.client(COUNT));
    }

    /** 画面に描くアンドロイド（クライアント。見つからなければ null）。 */
    @Nullable
    public RuinAndroid android() {
        return android;
    }

    public int value(int index) {
        return data.getInt(index);
    }

    public int page() {
        return page;
    }

    /** 見るページを変える（クライアント）。 */
    public void setPage(int page) {
        this.page = page;
    }

    /** バックパックアップグレードを積んでいるか（スロットの中身で見るので、クライアントでも分かる）。 */
    public boolean hasBackpack() {
        return RuinAndroid.isBackpackUpgrade(items.getItem(RuinAndroid.SLOT_BACKPACK));
    }

    /** 作業中か（持ち物を出し入れできない）。 */
    public boolean working() {
        return android != null && android.isWorking();
    }

    /** バックパックの中が空か。 */
    public boolean packEmpty() {
        for (int i = 0; i < RuinAndroid.PACK_SLOTS; i++) {
            if (!items.getItem(RuinAndroid.PACK_START + i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 名前を付け直す（サーバー。RenameAndroidPayload から）。操作できる人だけ。 */
    public void rename(Player player, String name) {
        if (android != null && android.mayOperate(player)) {
            android.rename(name);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (android == null || !android.mayOperate(player)) {
            return false;
        }
        if (id == BUTTON_DISMANTLE) {
            android.dismantle();
            player.closeContainer();
            return true;
        }
        if (id == BUTTON_ESCORT && android.mode() == RuinAndroid.MODE_FOLLOW) {
            android.chooseEscort(!android.escort());
            return true;
        }
        if (id == BUTTON_WORK) {
            android.setWorkEnabled(!android.workEnabled());
            return true;
        }
        if (id >= BUTTON_TANK_USE && id < BUTTON_TANK_USE + RuinAndroid.TANKS) {
            return android.useTankContainer(player, id - BUTTON_TANK_USE);
        }
        if (id >= BUTTON_TANK_CLEAR && id < BUTTON_TANK_CLEAR + RuinAndroid.TANKS) {
            return android.clearTank(id - BUTTON_TANK_CLEAR);
        }
        if (id >= BUTTON_CHEM_USE && id < BUTTON_CHEM_USE + RuinAndroid.TANKS) {
            return android.useChemContainer(player, id - BUTTON_CHEM_USE);
        }
        if (id >= BUTTON_CHEM_CLEAR && id < BUTTON_CHEM_CLEAR + RuinAndroid.TANKS) {
            return android.clearChem(id - BUTTON_CHEM_CLEAR);
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
        int own = RuinAndroid.INVENTORY_SIZE;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(RuinAndroid.isFellingUpgrade(stack) && moveItemStackTo(stack, RuinAndroid.SLOT_FELLING, RuinAndroid.SLOT_FELLING + 1, false))
                && !(RuinAndroid.isTankUpgrade(stack) && moveItemStackTo(stack, RuinAndroid.SLOT_TANK, RuinAndroid.SLOT_TANK + 1, false))
                && !(stack.getItem() instanceof AndroidAccessoryItem acc
                        && moveItemStackTo(stack, RuinAndroid.SLOT_DECOR + acc.part(), RuinAndroid.SLOT_DECOR + acc.part() + 1, false))
                && !moveItemStackTo(stack, 0, RuinAndroid.CARRY_START, false)          // 修理キット・装甲板・アップグレードの枠
                && !moveItemStackTo(stack, RuinAndroid.CARRY_START, RuinAndroid.SLOT_FELLING, false)) {     // 持ち物、バックパックの中
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
        // 許可証から外された人の画面は、開いたままでも閉じる（持ち物を動かしたり、名前を変えたりできないように）
        return android == null || android.isAlive() && android.distanceToSqr(player) < 8 * 8 && android.mayOperate(player);
    }
}
