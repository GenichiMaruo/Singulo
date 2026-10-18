package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
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

/** アンドロイド組立ステーションの画面。設計メモリ・キットなどと許可証のスロット、アンドロイドの様子、動き方の設定。 */
public class AndroidStationMenu extends AbstractContainerMenu {
    public static final int WIDTH = 254;
    public static final int HEIGHT = 240;
    /** 左の列（中心 x=26）。強化の段だけは、転送と生産のアップグレードを横に2つ並べる。 */
    public static final int MEMORY_X = 18, MEMORY_Y = 30;
    /** キットの段は、キットと工場式印刷ユニットを横に2つ並べる。 */
    public static final int KIT_X = 8, KIT_Y = 56;
    public static final int PRINT_UNIT_X = 28, PRINT_UNIT_Y = 56;
    public static final int UPGRADE_X = 8, UPGRADE_Y = 82;
    public static final int PRODUCTION_X = 28, PRODUCTION_Y = 82;
    public static final int STOCK_X = 18, STOCK_Y = 108;
    public static final int PERMIT_X = 18, PERMIT_Y = 134;
    /** 巡回路の欄（動き方が巡回のとき、追従の設定の段に8つ並べる）。 */
    public static final int ROUTE_X = 61, ROUTE_Y = 111;
    /** 経路のページ（題の右の「経路」で切り替える）: 4行（経由地点・対象範囲・進入禁止ゾーン2つ）。 */
    public static final int RULE_Y = 40, RULE_ROW = 19;
    public static final int[] RULE_COL_X = {66, 106, 146, 164};
    /** 持ち物は横幅の中央に。 */
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = 160;
    public static final int HOTBAR_Y = 218;

    private final BlockPos pos;
    private final SyncedInts data;
    /** 経路のページを見ているか（クライアント）。 */
    private boolean ruleView;

    public AndroidStationMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, SyncedInts data) {
        super(SinguloMenus.ANDROID_STATION.get(), containerId);
        this.pos = pos;
        this.data = data;
        addSlot(new SlotItemHandler(items, AndroidStationBlockEntity.SLOT_MEMORY, MEMORY_X, MEMORY_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AndroidStationBlockEntity.memoryKind(stack) != null;
            }
        });
        addSlot(new SlotItemHandler(items, AndroidStationBlockEntity.SLOT_KIT, KIT_X, KIT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AndroidStationBlockEntity.isKit(stack);
            }
        });
        addSlot(new SlotItemHandler(items, AndroidStationBlockEntity.SLOT_UPGRADE, UPGRADE_X, UPGRADE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AndroidStationBlockEntity.isTransferUpgrade(stack);
            }
        });
        addSlot(new SlotItemHandler(items, AndroidStationBlockEntity.SLOT_REPAIR_STOCK, STOCK_X, STOCK_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RuinAndroid.isRepairKit(stack);
            }
        });
        addSlot(new SlotItemHandler(items, AndroidStationBlockEntity.SLOT_PERMIT, PERMIT_X, PERMIT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AndroidStationBlockEntity.isPermit(stack);
            }
        });
        addSlot(new SlotItemHandler(items, AndroidStationBlockEntity.SLOT_PRODUCTION, PRODUCTION_X, PRODUCTION_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AndroidStationBlockEntity.isProductionUpgrade(stack);
            }
        });
        for (int i = 0; i < AndroidStationBlockEntity.ROUTE_SLOTS; i++) {
            addSlot(new SlotItemHandler(items, AndroidStationBlockEntity.SLOT_ROUTE + i, ROUTE_X + i * 18, ROUTE_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.isCard(stack);
                }

                @Override
                public boolean isActive() {
                    return patrolling() && !ruleView;
                }
            });
        }
        for (int i = 0; i < io.github.genichimaruo.singulo.android.work.PathRule.SLOTS; i++) {
            int col = i % io.github.genichimaruo.singulo.android.work.PathRule.COLS;
            addSlot(new SlotItemHandler(items, AndroidStationBlockEntity.SLOT_RULES + i, RULE_COL_X[col],
                    RULE_Y + (i / io.github.genichimaruo.singulo.android.work.PathRule.COLS) * RULE_ROW) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return io.github.genichimaruo.singulo.android.work.PathRule.fits(col, stack);
                }

                @Override
                public boolean isActive() {
                    return ruleView;
                }
            });
        }
        addSlot(new SlotItemHandler(items, AndroidStationBlockEntity.SLOT_PRINT_UNIT, PRINT_UNIT_X, PRINT_UNIT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AndroidStationBlockEntity.isPrintUnit(stack);
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

    public static AndroidStationMenu client(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new AndroidStationMenu(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(AndroidStationBlockEntity.SLOTS),
                SyncedInts.client(AndroidStationBlockEntity.COUNT));
    }

    public int value(int index) {
        return data.getInt(index);
    }

    /** 経路のページを見ているか。 */
    public boolean ruleView() {
        return ruleView;
    }

    public void setRuleView(boolean ruleView) {
        this.ruleView = ruleView;
    }

    /** 動き方が巡回か（巡回路の欄を出す）。 */
    public boolean patrolling() {
        return data.getInt(AndroidStationBlockEntity.D_MODE) == RuinAndroid.MODE_PATROL;
    }

    /** ステーションの場所（クライアントで、名前を読むため）。 */
    public BlockPos pos() {
        return pos;
    }

    /** 名前を付け直す（サーバー。RenameAndroidPayload から）。 */
    public void rename(Player player, String name) {
        if (player.level().getBlockEntity(pos) instanceof AndroidStationBlockEntity station && station.mayOperate(player)) {
            station.rename(name);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().getBlockEntity(pos) instanceof AndroidStationBlockEntity station && station.mayOperate(player)) {
            if (id == AndroidStationBlockEntity.BUTTON_RECALL_ALL) {
                // 近くのステーション（この人が操作できるもの）のアンドロイドを、みな呼び戻す
                int n = 0;
                for (AndroidStationBlockEntity s : AndroidStationBlockEntity.nearby(player.level(), pos, AndroidStationBlockEntity.RECALL_ALL_RANGE, player)) {
                    n += s.recallAll();
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.singulo.android_station.recall_all.done", n), true);
                return true;
            }
            station.press(id);
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
        int own = AndroidStationBlockEntity.SLOTS;
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
                && player.level().getBlockEntity(pos) instanceof AndroidStationBlockEntity station && station.mayOperate(player);
    }
}
