package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.registry.SinguloMenus;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 作業指令台の画面。スロット: 0 許可証（つながりのページ）、1〜9 記録の見本（物は減らない）、10 作る物（表示だけ）、
 * 11 記録したカード、12 未記録のカードのストック、13〜14 加工モードの、ほかに出てくる物、15〜16 手順モードの作業位置カード、
 * 17 手順モードの物、18 加工モードの燃料（記録のページ。メニューのスロット番号 = 記録の欄の番号 + 1）、19〜45 納品箱、
 * そのあとプレイヤーの持ち物。
 * 注文できる物や進み具合は、サーバーから {@link WorkConsoleViewPayload} で届く。ボタン: 0 記録、1 見本を消す、2 カードのレシピを消す、
 * 3 モードの切り替え、4 手順の種類の切り替え、5 手順を足す、{@link #BUTTON_REMOVE_STEP} から 手順を消す、
 * {@link #BUTTON_ADJUST} から 数の増減（スロットごとに4つ: +1、-1、+10、-10）。
 */
public class WorkConsoleMenu extends AbstractContainerMenu {
    /**
     * 幅はインベントリに合わせた 176。高さは注文の一覧の行数（設定 workOrderRows）で変わり、画面がインベントリを動かす
     * （ここの HEIGHT とインベントリの位置は既定の5行のとき）。
     */
    public static final int WIDTH = 176, HEIGHT = 228;
    /** 枠を画面の箱から内側に描く量（0）。 */
    public static final int FRAME_INSET = 0;
    /** 枠の右にはみ出すタブ: ページ7つと、その下の許可証の段。 */
    public static final int TAB_PITCH = 23, PAGE_TABS = 7;
    public static final int PERMIT_X = FRAME_INSET + io.github.genichimaruo.singulo.machine.GuiFrame.railSlotX(WIDTH - 2 * FRAME_INSET),
            PERMIT_Y = io.github.genichimaruo.singulo.machine.GuiFrame.RAIL_TOP + PAGE_TABS * TAB_PITCH + 3;
    /**
     * レシピ登録の窓の配置（窓の中身の左上からの位置。スロットは画面が窓と一緒に動かす）。
     * 上の段: 左から 燃料（加工）・見本 3×3・矢印・作る物（とほかに出てくる物）。右端の列に モード・記録・見本消去のボタン。
     * 下の段: 左にモードごとのボタン（作業台は「同じ種類」、加工は3つの面）、右端にカードのストック・記録したカード・レシピ消去。
     * 手順モードは、左に手順の一覧、その右に作業位置カード2枚・物・作る物と、種類・足す・数のボタン。囲みの線どうしは重ねない。
     */
    public static final int RECORD_BODY_H = 88;
    public static final int GRID_X = 32, GRID_Y = 6, RESULT_X = 122, RESULT_Y = 24;
    public static final int BUTTON_X = 190, BUTTON_W = 48, BUTTON_H = 14;
    public static final int MODE_X = BUTTON_X, MODE_Y = 6, ENCODE_X = BUTTON_X, ENCODE_Y = 24, CLEAR_X = BUTTON_X, CLEAR_Y = 42;
    /** カードのストック・記録したカードと、その右のレシピ消去（ごみ箱のアイコンだけの小さなボタン、ICON_W 幅）。 */
    public static final int STOCK_X = 190, STOCK_Y = 66, CARD_X = 210, CARD_Y = 66, ICON_W = 10, CLEAR_CARD_X = 228, CLEAR_CARD_Y = 67;
    /** 加工モードの燃料の欄（見本の左）。 */
    public static final int FUEL_X = 8, FUEL_Y = 24;
    /** 加工モードの、ほかに出てくる物の欄（作る物の右に2つ）。 */
    public static final int EXTRA_X = 146, EXTRA_Y = 24;
    /** 手順モード: 左に並べた手順の一覧、右に作業位置カード2枚と物の欄、その下に種類と「足す」のボタン、数のボタン、作る物。 */
    public static final int STEPS_X = 8, STEPS_Y = 6, STEPS_W = 110, STEP_H = 9;
    public static final int LOC_A_X = 124, LOC_B_X = 144, LOC_Y = 6, PITEM_X = 167, PITEM_Y = 6;
    public static final int KIND_X = 124, KIND_W = 28, ADD_X = 154, ADD_W = 28, SMALL_Y = 30, SMALL_H = 12;
    /** 手順モードでは、作る物の欄をここへ動かす（画面がスロットを動かす）。 */
    public static final int RESULT_PROC_X = 167, RESULT_PROC_Y = 64;
    public static final int INV_X = io.github.genichimaruo.singulo.machine.GuiFrame.inventoryX(WIDTH),
            INV_Y = io.github.genichimaruo.singulo.machine.GuiFrame.inventoryY(HEIGHT),
            HOTBAR_Y = io.github.genichimaruo.singulo.machine.GuiFrame.hotbarY(HEIGHT);
    /** 納品箱のページ: 9×3。 */
    public static final int OUTBOX_X = INV_X, OUTBOX_Y = 35;
    public static final int SLOT_PERMIT = 0, SLOT_GRID = 1, SLOT_RESULT = 10, SLOT_CARD = 11, SLOT_STOCK = 12, SLOT_EXTRA = 13,
            SLOT_LOC_A = 15, SLOT_LOC_B = 16, SLOT_PITEM = 17, SLOT_FUEL = 18, SLOT_OUTBOX = 19,
            SLOT_ROUTE = SLOT_OUTBOX + WorkConsoleBlockEntity.OUTBOX_SLOTS, OWN = SLOT_ROUTE + PathRule.SLOTS;
    /** 経路のページ: 4行（経由地点・対象範囲・進入禁止ゾーン2つ）。 */
    public static final int ROUTE_X = 24, ROUTE_Y = 37, ROUTE_ROW = 19;
    public static final int[] ROUTE_COL_X = {ROUTE_X, ROUTE_X + 38, ROUTE_X + 90, ROUTE_X + 108};
    public static final int BUTTON_ENCODE = 0, BUTTON_CLEAR = 1, BUTTON_CLEAR_CARD = 2, BUTTON_MODE = 3, BUTTON_KIND = 4, BUTTON_ADD = 5,
            BUTTON_KIND_BACK = 6, BUTTON_REMOVE_STEP = 10, BUTTON_OPTION = 20, BUTTON_FACE = 30, BUTTON_FACE_BACK = 33, BUTTON_VARIANTS = 36,
            BUTTON_FUEL_FILL = 37, BUTTON_VARIANT_SLOT = 40, BUTTON_ADJUST = 100;
    /** 加工モードの、燃料を持ってくる量のボタン（燃料の欄の下）。 */
    public static final int FUEL_MODE_X = 6, FUEL_MODE_Y = 46, FUEL_MODE_W = 22, FUEL_MODE_H = 11;
    /** 作業台モードの「同じ種類の別の物も使う」をまとめて切り替えるアイコンのボタン（下の段の左）。 */
    public static final int VARIANTS_X = 32, VARIANTS_Y = 69, VARIANTS_W = 16, VARIANTS_H = 12;
    /** 加工モードの面のボタン（下の段に3つ: 入れる面・取り出す面・燃料の面。アイコンと面の名前）。 */
    public static final int FACE_X = 32, FACE_Y = 68, FACE_W = 48, FACE_H = 14, FACE_GAP = 2;
    /** 手順の数の上限・レバーの扱いのボタン（種類のボタンの下）。BUTTON_OPTION から +1、-1、+10、-10。 */
    public static final int OPTION_X = 124, OPTION_Y = 46, OPTION_W = 58, OPTION_H = 12;

    private final BlockPos pos;
    private final Container encoder;
    private final Player player;
    /** 記録のモード（0: 作業台、1: 加工）。 */
    private final net.minecraft.world.inventory.ContainerData mode;
    /** 画面（クライアント）が決める、スロットを見せるか（レシピ登録の窓・納品箱・経路のページ）。 */
    private boolean showEncoder;
    private boolean showOutbox;
    private boolean showRoutes;
    /** サーバーから届いた様子（クライアント）。 */
    @Nullable
    private CompoundTag view;
    private int viewVersion;

    public WorkConsoleMenu(int id, Inventory inventory, BlockPos pos, IItemHandler permit, Container encoder, Container outbox,
                           IItemHandler routes, net.minecraft.world.inventory.ContainerData mode) {
        super(SinguloMenus.ANDROID_WORK_CONSOLE.get(), id);
        this.pos = pos;
        this.encoder = encoder;
        this.player = inventory.player;
        this.mode = mode;
        addDataSlots(mode);
        addSlot(new SlotItemHandler(permit, 0, PERMIT_X, PERMIT_Y));      // 枠の外のタブにあるので、どのページでも触れる
        for (int i = 0; i < WorkConsoleBlockEntity.GRID; i++) {
            addSlot(new EncoderSlot(encoder, i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18, false) {
                @Override
                public boolean isActive() {
                    return super.isActive() && mode() != WorkConsoleBlockEntity.MODE_PROCEDURE;
                }
            });
        }
        addSlot(new EncoderSlot(encoder, WorkConsoleBlockEntity.RESULT, RESULT_X, RESULT_Y, false));
        addSlot(new EncoderSlot(encoder, WorkConsoleBlockEntity.CARD, CARD_X, CARD_Y, true));
        addSlot(new EncoderSlot(encoder, WorkConsoleBlockEntity.STOCK, STOCK_X, STOCK_Y, true) {
            /** ストックは、未記録のカードだけ1スタック。 */
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RecipeCardItem.isCard(stack) && RecipeData.read(stack, player().level().registryAccess()) == null;
            }

            @Override
            public int getMaxStackSize() {
                return 64;
            }
        });
        for (int i = 0; i < WorkConsoleBlockEntity.EXTRAS; i++) {
            addSlot(new EncoderSlot(encoder, WorkConsoleBlockEntity.EXTRA + i, EXTRA_X + i * 18, EXTRA_Y, false) {
                @Override
                public boolean isActive() {
                    return super.isActive() && processing();
                }
            });
        }
        for (int i = 0; i < 2; i++) {
            addSlot(new EncoderSlot(encoder, WorkConsoleBlockEntity.LOC_A + i, i == 0 ? LOC_A_X : LOC_B_X, LOC_Y, true) {
                @Override
                public boolean isActive() {
                    return super.isActive() && mode() == WorkConsoleBlockEntity.MODE_PROCEDURE;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return WorkLocationCardItem.isCard(stack);
                }
            });
        }
        addSlot(new EncoderSlot(encoder, WorkConsoleBlockEntity.PITEM, PITEM_X, PITEM_Y, false) {
            @Override
            public boolean isActive() {
                return super.isActive() && mode() == WorkConsoleBlockEntity.MODE_PROCEDURE;
            }
        });
        addSlot(new EncoderSlot(encoder, WorkConsoleBlockEntity.FUEL, FUEL_X, FUEL_Y, false) {
            @Override
            public boolean isActive() {
                return super.isActive() && processing();
            }
        });
        for (int i = 0; i < WorkConsoleBlockEntity.OUTBOX_SLOTS; i++) {
            addSlot(new io.github.genichimaruo.singulo.compat.LegacyConstructors.MenuSlot(outbox, i, OUTBOX_X + (i % 9) * 18, OUTBOX_Y + (i / 9) * 18) {
                @Override
                public boolean isActive() {
                    return showOutbox;
                }
            });
        }
        for (int i = 0; i < PathRule.SLOTS; i++) {
            int col = i % PathRule.COLS;
            addSlot(new SlotItemHandler(routes, i, ROUTE_COL_X[col], ROUTE_Y + (i / PathRule.COLS) * ROUTE_ROW) {
                @Override
                public boolean isActive() {
                    return showRoutes;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return PathRule.fits(col, stack);
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
    }

    /** 記録のページのスロット。見本と作る物は、取り出しも入れることもできない（見本はクリックで置く）。 */
    private class EncoderSlot extends Slot {
        private final boolean card;

        EncoderSlot(Container container, int index, int x, int y, boolean card) {
            super(container, index, x, y);
            this.card = card;
        }

        @Override
        public boolean isActive() {
            return showEncoder;
        }

        @Override
        public boolean mayPickup(Player player) {
            return card;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return card && RecipeCardItem.isCard(stack);
        }

        @Override
        public int getMaxStackSize() {
            return card ? 1 : 64;
        }
    }

    private Player player() {
        return player;
    }

    public static WorkConsoleMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        return new WorkConsoleMenu(id, inventory, buf.readBlockPos(), new ItemStackHandler(1),
                new SimpleContainer(WorkConsoleBlockEntity.ENCODER_SLOTS), new SimpleContainer(WorkConsoleBlockEntity.OUTBOX_SLOTS),
                new ItemStackHandler(PathRule.SLOTS), new net.minecraft.world.inventory.SimpleContainerData(5));
    }

    /** 記録のモード（{@link WorkConsoleBlockEntity#MODE_CRAFTING} など）。 */
    public int mode() {
        return mode.get(0);
    }

    /** 記録のモードが加工か。 */
    public boolean processing() {
        return mode() == WorkConsoleBlockEntity.MODE_PROCESSING;
    }

    /** 手順モードで、次に足す手順の種類。 */
    public int stepKind() {
        return mode.get(1);
    }

    /** 作業台モードで、同じ種類の別の物も使う見本の欄（ビット i が欄 i）。 */
    public int variantMask() {
        return mode.get(4);
    }

    /** 見本の欄 i で、同じ種類の別の物も使うか。 */
    public boolean variant(int i) {
        return (variantMask() >> i & 1) != 0;
    }

    /** 加工モードで記録する面（{@link RecipeData#faces}）。 */
    public int faces() {
        return mode.get(3);
    }

    /** 手順モードで、次に足す手順の数の上限・レバーの扱い。 */
    public int stepOption() {
        return mode.get(2);
    }

    /** 見本の欄（物は減らない。見本、作る物、ほかに出てくる物、手順の物）。 */
    public boolean isGhost(int slotId) {
        return slotId >= SLOT_GRID && slotId <= SLOT_RESULT || slotId >= SLOT_EXTRA && slotId < SLOT_LOC_A || slotId == SLOT_PITEM
                || slotId == SLOT_FUEL;
    }

    private static int encoderIndex(int slotId) {
        return slotId - 1;
    }

    public BlockPos pos() {
        return pos;
    }

    public void setVisibility(boolean encoder, boolean outbox, boolean routes) {
        this.showEncoder = encoder;
        this.showOutbox = outbox;
        this.showRoutes = routes;
    }

    /** 様子を受け取る（クライアント）。 */
    void receive(CompoundTag view) {
        this.view = view;
        viewVersion++;
    }

    @Nullable
    public CompoundTag view() {
        return view;
    }

    /** 様子が届くたびに増える（画面が作り直すため）。 */
    public int viewVersion() {
        return viewVersion;
    }

    /**
     * 見本の欄のクリック: 持っている物の写しを置く（何も持っていなければ消す）。作業台モードは1個、加工モードは持っている数
     * （右クリックなら1個）。加工モードでは、作る物・ほかに出てくる物の欄にも置ける。
     */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        boolean grid = slotId >= SLOT_GRID && slotId < SLOT_GRID + WorkConsoleBlockEntity.GRID;
        if (grid || isGhost(slotId)) {
            boolean counted = mode() != WorkConsoleBlockEntity.MODE_CRAFTING;
            if ((grid || counted) && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                ItemStack carried = getCarried();
                int count = counted && button == 0 ? carried.getCount() : 1;
                ItemStack fluid = counted ? FluidSamples.fromContainer(carried) : ItemStack.EMPTY;
                encoder.setItem(encoderIndex(slotId), carried.isEmpty() ? ItemStack.EMPTY
                        : FluidSamples.liquid(fluid) ? fluid : carried.copyWithCount(count));
                if (!player.level().isClientSide && player.level().getBlockEntity(pos) instanceof WorkConsoleBlockEntity c) {
                    c.updateResult();
                }
                broadcastChanges();
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof WorkConsoleBlockEntity c) || !c.mayOperate(player)) {
            return false;
        }
        if (id == BUTTON_ENCODE) {
            c.encode();
            return true;
        }
        if (id == BUTTON_CLEAR) {
            c.clearGrid();
            return true;
        }
        if (id == BUTTON_CLEAR_CARD) {
            c.clearCard();
            return true;
        }
        if (id == BUTTON_MODE) {
            c.cycleMode();
            return true;
        }
        if (id == BUTTON_KIND || id == BUTTON_KIND_BACK) {
            c.cycleStepKind(id == BUTTON_KIND_BACK);
            return true;
        }
        if (id == BUTTON_VARIANTS) {
            c.toggleVariants();
            return true;
        }
        if (id >= BUTTON_VARIANT_SLOT && id < BUTTON_VARIANT_SLOT + WorkConsoleBlockEntity.GRID) {
            c.toggleVariantSlot(id - BUTTON_VARIANT_SLOT);
            return true;
        }
        if (id == BUTTON_FUEL_FILL) {
            c.toggleFuelFill();
            return true;
        }
        if (id >= BUTTON_FACE && id < BUTTON_FACE + 6) {
            c.cycleFace((id - BUTTON_FACE) % 3, id >= BUTTON_FACE_BACK);
            return true;
        }
        if (id >= BUTTON_OPTION && id < BUTTON_OPTION + 4) {
            c.adjustStepOption(new int[] {1, -1, 10, -10}[id - BUTTON_OPTION]);
            return true;
        }
        if (id == BUTTON_ADD || id >= BUTTON_REMOVE_STEP && id < BUTTON_REMOVE_STEP + RecipeData.MAX_STEPS) {
            if (id == BUTTON_ADD) {
                net.minecraft.network.chat.Component why = c.addStep();
                if (why != null) {
                    player.displayClientMessage(why, true);
                }
            } else {
                c.removeStep(id - BUTTON_REMOVE_STEP);
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                c.sendView(sp, containerId, null);
            }
            return true;
        }
        if (id >= BUTTON_ADJUST) {
            int slotId = (id - BUTTON_ADJUST) / 4;
            int kind = (id - BUTTON_ADJUST) % 4;
            if (isGhost(slotId)) {
                c.adjustGhost(encoderIndex(slotId), new int[] {1, -1, 10, -10}[kind]);
                return true;
            }
        }
        return false;
    }

    /** 見本を置き換える（JEI の自動配置。サーバー）。 */
    public void setGrid(Player player, List<ItemStack> grid) {
        if (player.level().getBlockEntity(pos) instanceof WorkConsoleBlockEntity c) {
            c.setGrid(grid);
            broadcastChanges();
        }
    }

    /** 加工モードの見本を置き換える（JEI の自動配置。サーバー）。 */
    public void setProcess(Player player, List<ItemStack> inputs, List<ItemStack> outputs) {
        if (player.level().getBlockEntity(pos) instanceof WorkConsoleBlockEntity c) {
            c.setProcess(inputs, outputs);
            broadcastChanges();
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
        if (index == SLOT_PERMIT || index == SLOT_CARD || index == SLOT_STOCK || index == SLOT_LOC_A || index == SLOT_LOC_B
                || index >= SLOT_OUTBOX && index < OWN) {     // 納品箱・経路の欄から持ち物へ
            if (!moveItemStackTo(stack, OWN, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= OWN) {
            if (RecipeCardItem.isCard(stack)) {
                // 未記録のカードはストックへ、記録したカードは記録したカードの欄へ
                boolean blank = RecipeData.read(stack, player.level().registryAccess()) == null;
                if (!moveItemStackTo(stack, blank ? SLOT_STOCK : SLOT_CARD, blank ? SLOT_STOCK + 1 : SLOT_CARD + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (showRoutes && (WorkLocationCardItem.isCard(stack) || AreaCardItem.isCard(stack))) {
                if (!moveItemStackTo(stack, SLOT_ROUTE, OWN, false)) {        // 経路のページ: 空いている合う欄へ
                    return ItemStack.EMPTY;
                }
            } else if (WorkLocationCardItem.isCard(stack) && mode() == WorkConsoleBlockEntity.MODE_PROCEDURE) {
                if (!moveItemStackTo(stack, SLOT_LOC_A, SLOT_LOC_B + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, SLOT_PERMIT, SLOT_PERMIT + 1, false)) {
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
                && player.level().getBlockEntity(pos) instanceof WorkConsoleBlockEntity c && c.mayOperate(player);
    }
}
