package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.machine.GuiFrame;
import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.registry.SinguloMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * ドローン発着ポートの画面。右レールのタブ（面の設定の下の3段）で3つのページを切り替える（タブはクライアントだけの状態）。
 * <ul>
 *   <li>荷物: 左に空き箱と荷物（3×3）、真ん中に蓄電、右に着荷箱（縦に3つ）→ 受け取り（3×3）。</li>
 *   <li>発送: 行き先の一覧（同じ持ち主のポート。近い順。自動発送の行き先には印）と、その下の1段に発送・取り消し・自動発送のしきい値・登録のボタン。</li>
 *   <li>輸送中: 出る便・来る便の段階と進み（SHIP_ROWS まで）。</li>
 * </ul>
 * 行き先の一覧は開いたときに作り、選んでいる行き先の番号と、輸送中の便を同期する。
 */
public class DronePortMenu extends AbstractContainerMenu {
    public static final int WIDTH = 212, HEIGHT = 218;
    /** ページ: 荷物・発送・輸送中（枠の中は、ヘッダーのすぐ下からページに使う）。 */
    public static final int PAGE_CARGO = 0, PAGE_SEND = 1, PAGE_SHIPS = 2, PAGES = 3;
    /** ページの中身の上端。 */
    public static final int PAGE_Y = 22;
    /**
     * 荷物のページのスロットの上端。3段のスロット（枠を含めて高さ54）を、ヘッダーの下から溝の上までの上下中央に置く
     * （FramedScreen.centeredY と同じ決め方。スロットの枠はスロットの1ドット上から）。
     */
    public static final int CARGO_PAGE_Y = GuiFrame.HEADER - 1 + (GuiFrame.grooveY(HEIGHT) - (GuiFrame.HEADER - 1) - 3 * 18 + 1) / 2 + 1;
    public static final int CRATE_X = 8, CRATE_Y = CARGO_PAGE_Y;
    public static final int CARGO_X = 30, CARGO_Y = CARGO_PAGE_Y;
    public static final int ENERGY_X = 97, ENERGY_Y = CARGO_PAGE_Y, ENERGY_H = 52;
    /** 着荷箱（縦に3つ）と受け取り（3×3）。間に開梱の向きの矢印を、どちらの枠からも離して置く。 */
    public static final int ARRIVED_X = 116, ARRIVED_Y = CARGO_PAGE_Y, ARROW_X = 138, RECEIVE_X = 150, RECEIVE_Y = CARGO_PAGE_Y;
    /** 発送のページ: 行き先の一覧（6行）と、その下の1段のボタン（発送・取り消し・自動発送のしきい値・登録。間は2ドット）。 */
    public static final int LIST_X = 8, LIST_Y = PAGE_Y, LIST_W = WIDTH - 16, ROW_H = 13, ROWS = 6;
    public static final int BUTTON_Y = LIST_Y + ROWS * ROW_H + 3, BUTTON_H = 14, BUTTON_W = 48, AUTO_W = 58, REGISTER_W = 36, BUTTON_GAP = 2;
    /** 輸送中のページ: 便の欄（6行）。 */
    public static final int SHIP_X = 8, SHIP_Y = PAGE_Y, SHIP_W = WIDTH - 16, SHIP_ROW = 13, SHIP_ROWS = 6;
    /** 便の段階: ドローン待ち・ドローンが向かっている・積み込み中・飛行中・荷降ろし中。向き: 出る・来る。 */
    public static final int PHASE_WAITING = 1, PHASE_COMING = 2, PHASE_LOADING = 3, PHASE_FLYING = 4, PHASE_UNLOADING = 5, PHASE_NO_ROUTE = 6;
    public static final int DIR_OUT = 1, DIR_IN = 2;
    private static final int SHIP_INTS = 3;
    public static final int INV_X = GuiFrame.inventoryX(WIDTH), INV_Y = GuiFrame.inventoryY(HEIGHT), HOTBAR_Y = GuiFrame.hotbarY(HEIGHT);

    private final BlockPos pos;
    private final SyncedInts data;
    private final List<BlockPos> targets;
    private final List<String> names;
    /** 行き先の次元（"" はこの次元。ほかはワームホール便）と、道のり（ブロック）。 */
    private final List<String> dims;
    private final List<Integer> distances;
    /** 液体のポートの画面か（荷物と受け取りの欄の代わりに、送るタンクと受け取りのタンクを出す）。 */
    private final boolean fluid;
    /** 見ているページ（クライアント）。荷物のページ以外では、ポートのスロットを出さない。 */
    private int page = PAGE_CARGO;
    /** 選んでいる行き先の一覧での番号（-1 はなし）。サーバーではポートから、クライアントでは同期された値。 */
    private final DataSlot selected;
    /** 自動発送の行き先の一覧での番号（-1 は未登録か、一覧にない）。 */
    private final DataSlot autoTarget;
    /** 輸送中の便（行ごとに 段階と向きと箱の段階・相手の一覧での番号・進みの千分率）。 */
    private final SyncedInts ships;
    private long shipsAt = -1;
    private int[] shipSnapshot = new int[SHIP_ROWS * SHIP_INTS];

    public DronePortMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, SyncedInts data, List<BlockPos> targets,
                         List<String> names, boolean fluid) {
        this(id, inventory, pos, items, data, targets, names, java.util.Collections.nCopies(targets.size(), ""),
                targets.stream().map(t -> (int) Math.ceil(Math.sqrt(t.distSqr(pos)))).toList(), fluid);
    }

    public DronePortMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, SyncedInts data, List<BlockPos> targets,
                         List<String> names, List<String> dims, List<Integer> distances, boolean fluid) {
        super(SinguloMenus.DRONE_PORT.get(), id);
        this.fluid = fluid;
        this.dims = dims;
        this.distances = distances;
        this.pos = pos;
        this.data = data;
        this.targets = targets;
        this.names = names;
        addSlot(new PageSlot(items, DronePortBlockEntity.SLOT_CRATES, CRATE_X, CRATE_Y, true));
        for (int i = 0; i < DronePortBlockEntity.CARGO; i++) {
            addSlot(new PageSlot(items, DronePortBlockEntity.SLOT_CARGO + i, CARGO_X + (i % 3) * 18, CARGO_Y + (i / 3) * 18, true));
        }
        for (int i = 0; i < DronePortBlockEntity.ARRIVED; i++) {
            addSlot(new PageSlot(items, DronePortBlockEntity.SLOT_ARRIVED + i, ARRIVED_X, ARRIVED_Y + i * 18, false));
        }
        for (int i = 0; i < DronePortBlockEntity.RECEIVE; i++) {
            addSlot(new PageSlot(items, DronePortBlockEntity.SLOT_RECEIVE + i, RECEIVE_X + (i % 3) * 18, RECEIVE_Y + (i / 3) * 18, false));
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
        DataSlot server = new DataSlot() {
            private int value = -1;

            @Override
            public int get() {
                if (inventory.player.level().isClientSide) {
                    return value;
                }
                return inventory.player.level().getBlockEntity(pos) instanceof DronePortBlockEntity port
                        ? indexOf(port.destination(), port.destinationDim()) : -1;
            }

            @Override
            public void set(int v) {
                value = v;
            }
        };
        selected = addDataSlot(server);
        autoTarget = addDataSlot(new DataSlot() {
            private int value = -1;

            @Override
            public int get() {
                if (inventory.player.level().isClientSide) {
                    return value;
                }
                return inventory.player.level().getBlockEntity(pos) instanceof DronePortBlockEntity port
                        ? indexOf(port.autoTo(), port.autoDim()) : -1;
            }

            @Override
            public void set(int v) {
                value = v;
            }
        });
        ships = inventory.player.level().isClientSide ? SyncedInts.client(SHIP_ROWS * SHIP_INTS)
                : SyncedInts.server(SHIP_ROWS * SHIP_INTS, i -> shipSnapshot(inventory.player.level())[i]);
        addDataSlots(ships);
    }

    /** サーバー: 輸送中の便の行（tick ごとに1回だけ作る）。 */
    private int[] shipSnapshot(net.minecraft.world.level.Level level) {
        long now = level.getGameTime();
        if (now == shipsAt) {
            return shipSnapshot;
        }
        shipsAt = now;
        int[] out = new int[SHIP_ROWS * SHIP_INTS];
        if (level.getBlockEntity(pos) instanceof DronePortBlockEntity port) {
            int row = 0;
            BlockPos waiting = port.stagedTo();
            if (waiting != null && port.status() == DronePortBlockEntity.S_WAITING) {
                put(out, row++, PHASE_WAITING, DIR_OUT, DroneCrateItem.tier(port.staged()), targets.indexOf(waiting), 0);
            }
            for (DroneNetwork.Flight f : port.shipments()) {
                if (row >= SHIP_ROWS) {
                    break;
                }
                boolean out1 = pos.equals(f.from);
                int phase = f.blocked && !f.virtual ? PHASE_NO_ROUTE
                        : f.stage == DroneNetwork.PICKUP ? (port.handling(f) ? PHASE_LOADING : PHASE_COMING)
                        : f.docked || port.handling(f) ? PHASE_UNLOADING : PHASE_FLYING;
                int tier = f.stage == DroneNetwork.PICKUP ? DroneCrateItem.tier(port.staged()) : DroneCrateItem.tier(f.crate);
                put(out, row++, phase, out1 ? DIR_OUT : DIR_IN, tier, targets.indexOf(out1 ? f.to : f.from),
                        Math.round(1000 * (phase == PHASE_UNLOADING ? 1 : f.progress(now))));
            }
        }
        shipSnapshot = out;
        return out;
    }

    private static void put(int[] out, int row, int phase, int dir, int tier, int partner, int permille) {
        out[row * SHIP_INTS] = phase | dir << 4 | tier << 6;
        out[row * SHIP_INTS + 1] = partner;
        out[row * SHIP_INTS + 2] = permille;
    }

    /** 輸送中の便の行（クライアント）。phase が 0 なら空き。 */
    public record Ship(int phase, int dir, int tier, int partner, int permille) {}

    public Ship ship(int row) {
        int a = ships.getInt(row * SHIP_INTS);
        return new Ship(a & 15, a >> 4 & 3, a >> 6 & 3, ships.getInt(row * SHIP_INTS + 1), ships.getInt(row * SHIP_INTS + 2));
    }

    public static DronePortMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean fluid = buf.readBoolean();
        int n = buf.readVarInt();
        List<BlockPos> targets = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> dims = new ArrayList<>();
        List<Integer> distances = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            targets.add(buf.readBlockPos());
            names.add(buf.readUtf());
            dims.add(buf.readUtf());
            distances.add(buf.readVarInt());
        }
        return new DronePortMenu(id, inventory, pos, new ItemStackHandler(DronePortBlockEntity.SLOTS),
                SyncedInts.client(DronePortBlockEntity.COUNT), targets, names, dims, distances, fluid);
    }

    /** ポートのスロット（荷物のページでだけ出す。着荷箱と受け取りへは入れられない）。 */
    private final class PageSlot extends SlotItemHandler {
        private final boolean input;

        PageSlot(IItemHandler items, int index, int x, int y, boolean input) {
            super(items, index, x, y);
            this.input = input;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return input && super.mayPlace(stack);
        }

        @Override
        public boolean isActive() {
            // 液体のポートでは、荷物と受け取りの欄は使わない（タンクを出す）
            return page == PAGE_CARGO && !(fluid && (getSlotIndex() >= DronePortBlockEntity.SLOT_CARGO && getSlotIndex() < DronePortBlockEntity.SLOT_ARRIVED
                    || getSlotIndex() >= DronePortBlockEntity.SLOT_RECEIVE));
        }
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public boolean fluid() {
        return fluid;
    }

    public BlockPos pos() {
        return pos;
    }

    public int value(int index) {
        return data.getInt(index);
    }

    public List<BlockPos> targets() {
        return targets;
    }

    public List<String> names() {
        return names;
    }

    /** 行き先の次元の名前（"" はこの次元）。 */
    public List<String> dims() {
        return dims;
    }

    public List<Integer> distances() {
        return distances;
    }

    /** 一覧の index の次元（この次元なら null）。 */
    @javax.annotation.Nullable
    public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey(int index) {
        String d = index >= 0 && index < dims.size() ? dims.get(index) : "";
        var loc = d.isEmpty() ? null : net.minecraft.resources.ResourceLocation.tryParse(d);
        return loc == null ? null : net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc);
    }

    /** 行き先（位置と次元）の一覧の番号（なければ -1）。 */
    public int indexOf(@javax.annotation.Nullable BlockPos to,
                       @javax.annotation.Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        if (to == null) {
            return -1;
        }
        String d = dim == null ? "" : dim.location().toString();
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).equals(to) && dims.get(i).equals(d)) {
                return i;
            }
        }
        return -1;
    }

    public int selected() {
        return selected.get();
    }

    public int autoTarget() {
        return autoTarget.get();
    }

    /** 名前を付け直す（サーバー。RenameAndroidPayload から）。 */
    public void rename(Player player, String name) {
        if (player.level().getBlockEntity(pos) instanceof DronePortBlockEntity port && port.mayOperate(player)) {
            port.setName(name);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof DronePortBlockEntity port) || !port.mayOperate(player)) {
            return false;
        }
        if (id == DronePortBlockEntity.BUTTON_SEND) {
            port.send();
            return true;
        }
        if (id == DronePortBlockEntity.BUTTON_CANCEL) {
            port.cancel();
            return true;
        }
        if (id == DronePortBlockEntity.BUTTON_AUTO) {
            port.cycleAuto();
            return true;
        }
        if (id == DronePortBlockEntity.BUTTON_AUTO_REGISTER) {
            port.registerAuto();
            return true;
        }
        int index = id - DronePortBlockEntity.BUTTON_SELECT;
        if (index >= 0 && index < targets.size()) {
            port.select(targets.get(index), dimKey(index));
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int own = DronePortBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (DroneCrateItem.tier(stack) > 0 && !DroneCrateItem.packed(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false) && (fluid || !moveItemStackTo(stack, 1, 1 + DronePortBlockEntity.CARGO, false))) {
                return ItemStack.EMPTY;
            }
        } else if (fluid || !moveItemStackTo(stack, 1, 1 + DronePortBlockEntity.CARGO, false)) {
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
                && player.level().getBlockEntity(pos) instanceof DronePortBlockEntity port && port.mayOperate(player);
    }
}
