package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.machine.GuiFrame;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 携帯型アンドロイド印刷機の画面（0.21.0）。組ごとに1行（メモリ・キット、少しあけて自己修理キットのストックと装甲板）。
 * Mk1 は1行、Mk2 は3行。行の左の印は次に印刷する組で、押すとその組を次に印刷する（Mk2）。右に印刷ボタン、下に充電残量。
 * 枠の中身は、変わるたびに手に持った印刷機へ書き戻す。開いている間、手に持った印刷機は動かせない。
 */
public class PortablePrinterMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, BASE_HEIGHT = 168, ROW = 20;
    /** 行の左の「次に印刷する組」の印（押せる）、枠の位置。 */
    public static final int SELECT_X = 6, SELECT_W = 10, MEMORY_X = 20, KIT_X = 40, REPAIR_X = 64, PLATE_X = 84, WEAPON_X = 104, SLOT_Y = 26;
    /** ボタンの番号: 印刷、組を選ぶ（BUTTON_SELECT + 組）。 */
    public static final int BUTTON_PRINT = 0, BUTTON_SELECT = 1;
    /** 画面に送る値: 電力・容量・次に印刷する組。 */
    public static final int D_ENERGY = 0, D_CAPACITY = 1, D_NEXT = 2, COUNT = 3;

    private final Player player;
    private final InteractionHand hand;
    private final ItemStack openedPrinter;
    private final int units;
    private final io.github.genichimaruo.singulo.compat.ObservableContainer contents;
    private final ContainerData data;
    private ItemContainerContents shownContents;

    public PortablePrinterMenu(int id, Inventory inventory, InteractionHand hand) {
        super(SinguloMenus.PORTABLE_PRINTER.get(), id);
        this.player = inventory.player;
        this.hand = hand;
        this.openedPrinter = player.getItemInHand(hand);
        this.units = Math.max(1, PortableAndroidPrinterItem.units(openedPrinter));
        int slots = units * (PortableAndroidPrinterItem.PER_UNIT + 1);
        this.contents = new io.github.genichimaruo.singulo.compat.ObservableContainer(slots);
        refreshContents();
        if (!player.level().isClientSide()) {
            contents.addListener(container -> save());
        }
        this.data = player.level().isClientSide() ? new SimpleContainerData(COUNT) : new ContainerData() {
            @Override
            public int get(int index) {
                ItemStack s = printer();
                return switch (index) {
                    case D_ENERGY -> PortableAndroidPrinterItem.energy(s).getEnergyStored();
                    case D_CAPACITY -> PortableAndroidPrinterItem.capacity(s);
                    default -> Math.floorMod(s.getOrDefault(SinguloComponents.PRINTER_NEXT.get(), 0), units);
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return COUNT;
            }
        };
        for (int u = 0; u < units; u++) {
            int y = rowY(u);
            addSlot(new Slot(contents, PortableAndroidPrinterItem.slot(u, PortableAndroidPrinterItem.SLOT_MEMORY), MEMORY_X, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return AndroidStationBlockEntity.memoryKind(stack) != null;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
            addSlot(new Slot(contents, PortableAndroidPrinterItem.slot(u, PortableAndroidPrinterItem.SLOT_KIT), KIT_X, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return AndroidStationBlockEntity.isKit(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return PortableAndroidPrinterItem.MAX_KITS;
                }
            });
            addSlot(new Slot(contents, PortableAndroidPrinterItem.slot(u, PortableAndroidPrinterItem.SLOT_REPAIR), REPAIR_X, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return RuinAndroid.isRepairKit(stack);
                }
            });
            addSlot(new Slot(contents, PortableAndroidPrinterItem.slot(u, PortableAndroidPrinterItem.SLOT_PLATE), PLATE_X, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return RuinAndroid.isArmorPlate(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        // 武装モジュールの枠は、中身の並びのうしろ（組の枠のあと）。画面では各行の装甲板の右
        for (int u = 0; u < units; u++) {
            addSlot(new Slot(contents, PortableAndroidPrinterItem.weaponSlot(units, u), WEAPON_X, rowY(u)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return RuinAndroid.isWeaponModule(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        int height = height(units);
        int invX = GuiFrame.inventoryX(WIDTH), invY = GuiFrame.inventoryY(height), hotbarY = GuiFrame.hotbarY(height);
        int locked = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, invX + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            boolean held = col == locked;
            addSlot(new Slot(inventory, col, invX + col * 18, hotbarY) {
                @Override
                public boolean mayPickup(Player p) {
                    return !held;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !held;
                }
            });
        }
        addDataSlots(data);
    }

    public static PortablePrinterMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new PortablePrinterMenu(id, inventory, buf.readEnum(InteractionHand.class));
    }

    /** 画面の高さ（行が増えた分だけ高くする）。 */
    public static int height(int units) {
        return BASE_HEIGHT + (units - 1) * ROW;
    }

    public static int rowY(int unit) {
        return SLOT_Y + unit * ROW;
    }

    /** 充電残量のゲージの高さの位置。 */
    public static int barY(int units) {
        return SLOT_Y + units * ROW + 8;
    }

    public int units() {
        return units;
    }

    public InteractionHand hand() {
        return hand;
    }

    /** 手に持った印刷機（持っていなければ空）。 */
    public ItemStack printer() {
        ItemStack held = player.getItemInHand(hand);
        if (!(openedPrinter.getItem() instanceof PortableAndroidPrinterItem) || !held.is(openedPrinter.getItem())) {
            return ItemStack.EMPTY;
        }
        // サーバーでは開いた現物にだけ書く。クライアントの持ち物は同期で新しいスタックに置き換わる。
        return held == openedPrinter || player.level().isClientSide() ? held : ItemStack.EMPTY;
    }

    private void refreshContents() {
        ItemContainerContents stored = openedPrinter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (shownContents != stored) {
            stored.copyInto(contents.getItems());
            shownContents = stored;
        }
    }

    private void save() {
        if (!printer().isEmpty()) {
            shownContents = ItemContainerContents.fromItems(contents.getItems());
            openedPrinter.set(DataComponents.CONTAINER, shownContents);
        }
    }

    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide()) {
            refreshContents();
        }
        super.broadcastChanges();
    }

    public int energy() {
        return data.get(D_ENERGY);
    }

    public int capacity() {
        return Math.max(1, data.get(D_CAPACITY));
    }

    /** 次に印刷する組。 */
    public int next() {
        return Math.floorMod(data.get(D_NEXT), units);
    }

    public ItemStack item(int unit, int slot) {
        return contents.getItem(PortableAndroidPrinterItem.slot(unit, slot));
    }

    /** 印刷ボタン: 画面を閉じ、目の前の地面に置いて印刷する。組を選ぶボタン: その組を次に印刷する。 */
    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (!(p instanceof ServerPlayer sp)) {
            return false;
        }
        ItemStack printer = printer();
        if (printer.isEmpty()) {
            return false;
        }
        if (id >= BUTTON_SELECT && id < BUTTON_SELECT + units) {
            printer.set(SinguloComponents.PRINTER_NEXT.get(), id - BUTTON_SELECT);
            return true;
        }
        if (id != BUTTON_PRINT) {
            return false;
        }
        String problem = PortableAndroidPrinterItem.problem(printer);
        if (problem != null) {
            sp.sendOverlayMessage(Component.translatable(problem));
            return false;
        }
        sp.closeContainer();
        PortableAndroidPrinterItem.deploy(sp, hand, PortableAndroidPrinterItem.frontSpot(sp));
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player p) {
        int heldSlot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : p.getInventory().getSelectedSlot();
        if (!stillValid(p) || type == ContainerInput.SWAP && button == heldSlot) {
            return;
        }
        if (!player.level().isClientSide()) {
            refreshContents();
        }
        super.clicked(slotId, button, type, p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (!stillValid(p)) {
            return ItemStack.EMPTY;
        }
        if (!player.level().isClientSide()) {
            refreshContents();
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(p)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int own = contents.getContainerSize();
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 入れられる枠へ、上の行から順に（同じ物が入っている枠には重ねる）
            boolean weapon = RuinAndroid.isWeaponModule(stack);
            int kind = weapon ? 0 : AndroidStationBlockEntity.memoryKind(stack) != null ? PortableAndroidPrinterItem.SLOT_MEMORY
                    : AndroidStationBlockEntity.isKit(stack) ? PortableAndroidPrinterItem.SLOT_KIT
                    : RuinAndroid.isArmorPlate(stack) ? PortableAndroidPrinterItem.SLOT_PLATE
                    : RuinAndroid.isRepairKit(stack) ? PortableAndroidPrinterItem.SLOT_REPAIR : -1;
            if (kind < 0) {
                return ItemStack.EMPTY;
            }
            boolean moved = false;
            for (int u = 0; u < units && !stack.isEmpty(); u++) {
                int at = weapon ? PortableAndroidPrinterItem.weaponSlot(units, u) : PortableAndroidPrinterItem.slot(u, kind);
                moved |= moveItemStackTo(stack, at, at + 1, false);
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
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return p == player && !printer().isEmpty();
    }
}
