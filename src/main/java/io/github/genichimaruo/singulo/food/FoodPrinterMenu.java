package io.github.genichimaruo.singulo.food;

import io.github.genichimaruo.singulo.machine.GuiFrame;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * フードプリンターの画面（ほかの装置と同じ骨格。幅212）。左端に蓄電、上からタンパク質・炭水化物・甘味の行（その種類の食材の枠 →
 * カートリッジ → 縦長のペーストの目盛り）、印刷室の窓（選んだ食品が下から積み上がる）→ 矢印 → 印刷した食品の枠。
 * 出口の上に、作るものを選ぶボタン（選ぶ窓を開く）。電源と状態はヘッダーの右。
 * 選ぶ・電源は、ボタンの番号でサーバーへ送る（{@link #clickMenuButton}）。
 */
public class FoodPrinterMenu extends AbstractContainerMenu {
    public static final int WIDTH = 212, HEIGHT = 184;
    /** 蓄電の目盛り（ほかの装置と同じ左端）。 */
    public static final int ENERGY_X = 8, ENERGY_Y = 22, ENERGY_W = 8, ENERGY_H = 60;
    /** 行（上からタンパク質・炭水化物・甘味）: 食材の枠 → 小さな矢印 → カートリッジ → 縦長のペーストの目盛り。 */
    public static final int ROW_Y = 24, ROW_STEP = 18;
    public static final int INGREDIENT_X = 30, FLOW_X = 53, CART_X = 64, PASTE_X = 83, PASTE_W = 4;
    /** 印刷室の窓（選んだ食品の絵を、進んだ分だけ下から見せる）。 */
    public static final int BED_X = 100, BED_Y = 23, BED_W = 36, BED_H = 54;
    public static final int OUT_X = 174, OUT_Y = 51;
    /** 作るものを選ぶボタン（スロットの位置。まわりに20×20のボタン）。 */
    public static final int SELECT_X = 174, SELECT_Y = 26;
    /** ボタンの番号: 食品の番号（0〜）、自動、電源。 */
    public static final int BUTTON_AUTO = 100, BUTTON_POWER = 101;
    public static final int INV_X = GuiFrame.inventoryX(WIDTH), INV_Y = GuiFrame.inventoryY(HEIGHT), HOTBAR_Y = GuiFrame.hotbarY(HEIGHT);

    private final BlockPos pos;
    private final ContainerData data;

    public FoodPrinterMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(SinguloMenus.FOOD_PRINTER.get(), id);
        this.pos = pos;
        this.data = data;
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            addSlot(new SlotItemHandler(items, FoodPrinterBlockEntity.INGREDIENT + k, INGREDIENT_X, ROW_Y + k * ROW_STEP));
        }
        for (int k = 0; k < FoodPaste.KINDS; k++) {
            addSlot(new SlotItemHandler(items, FoodPrinterBlockEntity.CARTRIDGE + k, CART_X, ROW_Y + k * ROW_STEP));
        }
        addSlot(new SlotItemHandler(items, FoodPrinterBlockEntity.OUTPUT, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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

    public static FoodPrinterMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        ItemStackHandler items = new ItemStackHandler(FoodPrinterBlockEntity.SLOTS) {
            @Override
            public int getSlotLimit(int slot) {
                return FoodPrinterBlockEntity.isCartridge(slot) ? 1 : 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return FoodPrinterBlockEntity.accepts(slot, stack);
            }
        };
        return new FoodPrinterMenu(id, inventory, buf.readBlockPos(), items, new SimpleContainerData(FoodPrinterBlockEntity.DATA));
    }

    public BlockPos pos() {
        return pos;
    }

    public int value(int i) {
        return data.get(i);
    }

    /** 種類 kind のカートリッジ（空の枠なら空）。 */
    public ItemStack cartridge(int kind) {
        return getSlot(FoodPrinterBlockEntity.CARTRIDGE + kind).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof FoodPrinterBlockEntity printer)) {
            return false;
        }
        if (id == BUTTON_POWER) {
            printer.togglePower();
        } else if (id == BUTTON_AUTO || PrintedFood.byIndex(id) != null) {
            printer.select(id == BUTTON_AUTO ? FoodPrinterBlockEntity.AUTO : id);
        } else {
            return false;
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
        int own = FoodPrinterBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            int kind = FoodPaste.kindOf(stack);
            if (kind >= 0) {
                // 食材はその種類の枠へ
                moved = moveItemStackTo(stack, FoodPrinterBlockEntity.INGREDIENT + kind, FoodPrinterBlockEntity.INGREDIENT + kind + 1, false);
            } else {
                // カートリッジは同じ種類の枠へ。空のものは空いている枠へ上から
                for (int k = 0; k < FoodPaste.KINDS && !moved; k++) {
                    int i = FoodPrinterBlockEntity.CARTRIDGE + k;
                    if (!slots.get(i).hasItem() && FoodPrinterBlockEntity.accepts(i, stack)) {
                        moved = moveItemStackTo(stack, i, i + 1, false);
                    }
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
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
        return player.level().getBlockEntity(pos) instanceof FoodPrinterBlockEntity printer && printer.stillValid(player);
    }
}
