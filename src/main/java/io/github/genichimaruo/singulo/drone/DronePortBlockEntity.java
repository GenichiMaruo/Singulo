package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import io.github.genichimaruo.singulo.machine.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * ドローン発着ポート。空き箱と荷物を入れ、行き先（同じ持ち主のポート）を選んで「発送」すると、荷物を梱包して電力を払い、
 * 近くのステーションから待機中のドローンを呼ぶ。ドローンが真上に来ると、天井の扉が開いて昇降台が箱を持ち上げ、ドローンが掴む。
 * 届いた箱は着荷箱の欄に入り、受け取りの欄へ開梱される。空になった箱は空き箱の欄へ戻る。
 * <p>
 * 積み込み・荷降ろしの時間はポートが進める（ドローンは真上で止まって待つ）。時刻は ANIM_* と同じで、描くのは DronePortRenderer。
 */
public class DronePortBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener, AbstractMachineBlock.BreakListener,
        MenuProvider, SideConfig.Holder, io.github.genichimaruo.singulo.android.work.AndroidStorageView {
    public static final int CAPACITY = 400_000;
    public static final int MAX_RECEIVE = 8_000;

    /** スロット: 空き箱・荷物9・着荷箱3・受け取り9。 */
    public static final int SLOT_CRATES = 0, SLOT_CARGO = 1, CARGO = 9, SLOT_ARRIVED = SLOT_CARGO + CARGO, ARRIVED = 3,
            SLOT_RECEIVE = SLOT_ARRIVED + ARRIVED, RECEIVE = 9, SLOTS = SLOT_RECEIVE + RECEIVE;

    /**
     * 扉と昇降台の動き（tick）: 扉が真ん中から上へ開き（〜ANIM_HINGE）、開ききってから下へ引っ込む（〜ANIM_OPEN）。台が上がりきる・受け渡し・
     * 台が下がりきる。閉じるときは逆に、扉がせり上がって（〜ANIM_LOWERED + 6）から倒れて閉じる（〜ANIM_TICKS）。
     */
    public static final int ANIM_HINGE = 8, ANIM_OPEN = 14, ANIM_RAISED = 28, ANIM_HANDOFF = 36, ANIM_LOWERED = 44, ANIM_TICKS = 58;
    public static final int ANIM_NONE = 0, ANIM_LOAD = 1, ANIM_UNLOAD = 2;
    /**
     * 受け渡しを待つドローンの、天井より上の余裕（ブロック）。扉が跳ね上がる範囲（約6ドット）より上に吊るした箱を置き、
     * 扉が引っ込んでから台と一緒に下りて受け渡し、扉が閉じる前にまた上がる（dockLift）。
     */
    public static final double WAIT_LIFT = 0.5;

    /** GUI に送る値。 */
    public static final int D_ENERGY = 0, D_CAPACITY = 1, D_STATUS = 2, D_COST = 3, D_PROBLEM = 4, D_SIDES = 5, D_AUTO = 6, D_CRATE_SIDES = 7;
    /** 液体のポートだけ: 送るタンク・受け取りのタンクの液体（登録番号 + 1。空なら 0）と量（mB）。 */
    public static final int D_SEND_FLUID = 8, D_SEND_AMOUNT = 9, D_RECV_FLUID = 10, D_RECV_AMOUNT = 11, COUNT = 12;
    /** 面の設定の種類: 荷物（入出力と自動排出）と、空き箱の搬入出（空き箱の欄だけ）。 */
    public static final int CHANNEL_ITEMS = 0, CHANNEL_CRATES = 1;
    /**
     * 状態: 待機・ドローン待ち・ドローンが向かっている・積み込み中・荷降ろし中・電力不足・空き箱なし・射程外・行き先なし・荷物なし・
     * 近くに呼べるドローンがいない。
     */
    public static final int S_IDLE = 0, S_WAITING = 1, S_COMING = 2, S_LOADING = 3, S_UNLOADING = 4, S_NO_ENERGY = 5,
            S_NO_CRATE = 6, S_OUT_OF_RANGE = 7, S_NO_DESTINATION = 8, S_NO_CARGO = 9, S_NO_DRONE = 10;
    /** 発送できない理由を計り直す間隔（呼べるドローンを探すのは重いので）。 */
    static final int PROBLEM_TICKS = 10;
    /** 積み込み待ちの間、ドローンを呼び直す間隔（tick）。 */
    static final int CALL_TICKS = 20;
    /** ボタン: 発送、取り消し、行き先を選ぶ（BUTTON_SELECT + 一覧の番号）。 */
    public static final int BUTTON_SEND = 0, BUTTON_CANCEL = 1, BUTTON_AUTO = 2, BUTTON_AUTO_REGISTER = 3, BUTTON_SELECT = 100;
    /**
     * 自動発送のしきい値（荷物の欄の合計の個数）。0 はしない、AUTO_FULL は9欄すべてが満杯のとき。
     * 荷物が最後に増えてから AUTO_SETTLE tick 待ってから送る（ホッパーで少しずつ入る途中で送らないように）。
     */
    public static final int AUTO_FULL = -1;
    public static final int[] AUTO_THRESHOLDS = {0, 1, 64, 256, AUTO_FULL};
    static final int AUTO_SETTLE = 40;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            problemStale = true;
            if (slot >= SLOT_CARGO && slot < SLOT_ARRIVED && level != null) {
                cargoChangedAt = level.getGameTime();
            }
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_CRATES) {
                return acceptsCrate(stack);
            }
            if (slot < SLOT_ARRIVED) {
                return acceptsCargo(stack);
            }
            return true;                                   // 着荷箱・受け取りへはポート自身が入れる（外からは入れられない）
        }
    };

    @Nullable
    private UUID owner;
    private String name = "";
    /** 選んでいる行き先と、その次元（null はこの次元。別の次元はワームホール便 {@link WormholeRelay}）。 */
    @Nullable
    private BlockPos destination;
    @Nullable
    private net.minecraft.resources.ResourceKey<Level> destinationDim, stagedDim, autoDim;
    /** 積み込み待ちの梱包済みの箱と、その行き先・払った電力・受け持つ便（まだなら 0）。 */
    private ItemStack staged = ItemStack.EMPTY;
    @Nullable
    private BlockPos stagedTo;
    private int stagedCost;
    private long flightId;
    /** 扉と昇降台の動き（ANIM_*）、始めた時刻、台に載る箱の段階、相手の便、受け渡しを済ませたか。 */
    private int anim;
    private long animStart;
    private int animTier;
    /** 台に載る箱そのもの（液体カプセルの中身の色を描くため。描画用の写し）。 */
    private ItemStack animCrate = ItemStack.EMPTY;
    private long animFlight;
    private boolean handed;
    /** 面ごとの搬入出と自動排出（受け取りの欄と、空き箱の欄に入りきらなかった空の箱を押し出す）。 */
    private final SideConfig sides = new SideConfig();
    /** 面ごとの空き箱の搬入（既定はどの面からも入れられる。荷物の面を「無効」にして、ここを「入力」にすると、空き箱専用の面になる）。 */
    private final SideConfig crateSides = new SideConfig();

    {
        for (SideConfig.Face f : SideConfig.Face.values()) {
            crateSides.set(f, SideConfig.INPUT, false);
        }
    }
    /** 発送できない理由（PROBLEM_TICKS ごとに計り直す）。 */
    private int cachedProblem = S_IDLE;
    private long problemAt;
    /** 計り直しが要る（行き先や中身が変わった）。 */
    private boolean problemStale = true;
    /**
     * 受け持つドローンがいるか（周りのステーションを調べるのは重いので、PROBLEM_TICKS ごとにだけ調べ直す。
     * 中身が変わって理由を計り直すときも、これは使い回す）。
     */
    private boolean dronesOk, dronesKnown;
    private long dronesAt;
    /** 自動発送: しきい値（AUTO_THRESHOLDS の番号）と、登録した行き先。荷物が最後に変わった時刻。 */
    private int autoMode;
    @Nullable
    private BlockPos autoTo;
    private long cargoChangedAt;

    private final SyncedInts data = SyncedInts.server(COUNT, i -> switch (i) {
        case D_ENERGY -> energy.getEnergyStored();
        case D_CAPACITY -> energy.getMaxEnergyStored();
        case D_STATUS -> status();
        case D_COST -> destination == null ? 0 : Math.max(0, DroneCrateItem.cost(Math.max(0, distanceTo(destination, destinationDim))));
        case D_PROBLEM -> currentProblem();
        case D_SIDES -> sides.packed();
        case D_AUTO -> autoMode;
        case D_CRATE_SIDES -> crateSides.packed();
        default -> fluidData(i);
    });

    public DronePortBlockEntity(BlockPos pos, BlockState state) {
        this(SinguloBlockEntities.DRONE_PORT.get(), pos, state);
    }

    protected DronePortBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------ 物と液体で変わるところ（液体のポートが上書きする）

    /** 液体のポートか（行き先は同じ種類のポートだけ）。 */
    public boolean fluidPort() {
        return false;
    }

    /** 空き箱の欄に入れられる箱（物のポートは梱包箱、液体のポートは液体カプセル）。 */
    protected boolean acceptsCrate(ItemStack stack) {
        return DroneCrateItem.tier(stack) > 0 && !DroneCrateItem.packed(stack) && !DroneCrateItem.isFluid(stack);
    }

    /** 荷物の欄に入れられる物。 */
    protected boolean acceptsCargo(ItemStack stack) {
        return DroneCrateItem.mayCarry(stack);
    }

    /** 送る荷物があるか。 */
    protected boolean hasCargo() {
        for (int i = 0; i < CARGO; i++) {
            if (!items.getStackInSlot(SLOT_CARGO + i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 荷物を空き箱1つ（crate）に詰めて、梱包済みの箱を返す（荷物はここで取り出す）。 */
    protected ItemStack packCargo(ItemStack crate) {
        List<ItemStack> cargo = new ArrayList<>();
        for (int i = 0; i < CARGO; i++) {
            cargo.add(items.getStackInSlot(SLOT_CARGO + i).copy());
            items.setStackInSlot(SLOT_CARGO + i, ItemStack.EMPTY);
        }
        return DroneCrateItem.pack(crate, cargo);
    }

    /**
     * 取り消し: 梱包済みの箱の中身を荷物の欄へ戻せるか（simulate）、戻す。全部が入るときだけ戻す。
     * 戻す先は空の欄だけ（途中で入れ直されていても混ぜない）。
     */
    protected boolean restoreCargo(ItemStack packed, boolean simulate) {
        NonNullList<ItemStack> contents = DroneCrateItem.contents(packed);
        int free = 0;
        for (int i = 0; i < CARGO; i++) {
            free += items.getStackInSlot(SLOT_CARGO + i).isEmpty() ? 1 : 0;
        }
        long need = contents.stream().filter(s -> !s.isEmpty()).count();
        if (free < need) {
            return false;
        }
        if (!simulate) {
            int slot = SLOT_CARGO;
            for (ItemStack s : contents) {
                if (s.isEmpty()) {
                    continue;
                }
                while (!items.getStackInSlot(slot).isEmpty()) {
                    slot++;
                }
                items.setStackInSlot(slot, s);
            }
        }
        return true;
    }

    /** 着荷箱の欄の梱包済みの箱1つ（slot）を、入る分だけ開ける。 */
    protected void unpackOne(int slot, ItemStack crate) {
        NonNullList<ItemStack> contents = DroneCrateItem.contents(crate);
        boolean moved = false;
        for (int i = 0; i < contents.size(); i++) {
            ItemStack s = contents.get(i);
            for (int r = 0; r < RECEIVE && !s.isEmpty(); r++) {
                ItemStack before = s;
                s = items.insertItem(SLOT_RECEIVE + r, s, false);
                moved |= s.getCount() != before.getCount();
            }
            contents.set(i, s);
        }
        if (moved) {
            ItemStack next = crate.copy();
            DroneCrateItem.setContents(next, contents);
            items.setStackInSlot(slot, next);
        }
    }

    /** 自動発送のしきい値までたまっているか（AUTO_THRESHOLDS の番号 mode）。 */
    protected boolean autoReady(int mode) {
        int threshold = AUTO_THRESHOLDS[mode];
        if (threshold == 0) {
            return false;
        }
        int total = 0;
        boolean full = true;
        for (int i = 0; i < CARGO; i++) {
            ItemStack s = items.getStackInSlot(SLOT_CARGO + i);
            total += s.getCount();
            full &= !s.isEmpty() && s.getCount() >= s.getMaxStackSize();
        }
        return threshold == AUTO_FULL ? full : total >= threshold;
    }

    /** GUI に送る液体の値（物のポートは 0）。 */
    protected int fluidData(int index) {
        return 0;
    }

    /** 自動排出の面ごとに、物のほかに押し出すもの（液体のポートは受け取りのタンクの液体）。 */
    protected void ejectExtra(ServerLevel level, Direction dir) {}

    // ------------------------------------------------------------------ 中身

    public ItemStackHandler items() {
        return items;
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public ItemStack staged() {
        return staged;
    }

    @Nullable
    public BlockPos destination() {
        return destination;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        changedVisible();
    }

    public void setName(String name) {
        this.name = name.strip();
        changedVisible();
    }

    /** ほかの人も使えるか: 持ち主がいなければだれでも、いれば持ち主だけ（網は持ち主ごと）。 */
    @Override
    public boolean mayConfigure(Player player) {
        return mayOperate(player);
    }

    public boolean mayOperate(Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    /** 外（ホッパー・パイプ）から見た中身: 空き箱と荷物へ入れられ、着荷箱と受け取りから取り出せる。 */
    /** ホッパーやパイプは毎 tick 問い合わせるので、窓口は作ったものを使い回す（面ごとの窓口は、回されたら作り直す）。 */
    @Nullable
    private IItemHandler automationView;
    private final IItemHandler[] sidedViews = new IItemHandler[6];

    public IItemHandler automationItems() {
        if (automationView == null) {
            automationView = createAutomationItems();
        }
        return automationView;
    }

    private IItemHandler createAutomationItems() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return SLOTS;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return items.getStackInSlot(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return slot < SLOT_ARRIVED ? items.insertItem(slot, stack, simulate) : stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot >= SLOT_ARRIVED ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return items.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot < SLOT_ARRIVED && items.isItemValid(slot, stack);
            }
        };
    }

    // ------------------------------------------------------------------ 状態

    /** 画面に出す状態。 */
    public int status() {
        if (anim == ANIM_LOAD) {
            return S_LOADING;
        }
        if (anim == ANIM_UNLOAD) {
            return S_UNLOADING;
        }
        if (!staged.isEmpty()) {
            return flightId != 0 ? S_COMING : S_WAITING;
        }
        return currentProblem();
    }

    /** 発送できない理由（少し前に計ったもの）。 */
    public int currentProblem() {
        long now = level == null ? 0 : level.getGameTime();
        if (problemStale || now - problemAt >= PROBLEM_TICKS || now < problemAt) {
            cachedProblem = problem();
            problemAt = now;
            problemStale = false;
        }
        return cachedProblem;
    }

    public int anim() {
        return anim;
    }

    /** 動きを始めてからの時間（クライアントの描画用）。 */
    public float animTime(float partialTick) {
        return level == null ? 0 : level.getGameTime() - animStart + partialTick;
    }

    public int animTier() {
        return animTier;
    }

    /** 台に載っている（載る）箱（クライアントの描画用）。 */
    public ItemStack animCrate() {
        return animCrate;
    }

    // ------------------------------------------------------------------ 発送

    /** 行き先を選ぶ（一覧の番号。一覧は DronePortMenu が開いたときに作る）。 */
    public void select(@Nullable BlockPos to) {
        select(to, null);
    }

    /** 行き先を選ぶ（dim が null かこの次元ならふつうの便、別の次元ならワームホール便）。 */
    public void select(@Nullable BlockPos to, @Nullable net.minecraft.resources.ResourceKey<Level> dim) {
        destination = to;
        destinationDim = dim == null || level != null && dim.equals(level.dimension()) ? null : dim;
        problemStale = true;
        setChanged();
    }

    /** 今の行き先と箱で発送できない理由（できるなら S_IDLE）。 */
    public int problem() {
        return problem(destination, destinationDim);
    }

    @Nullable
    public net.minecraft.resources.ResourceKey<Level> destinationDim() {
        return destinationDim;
    }

    /** to までの道のり（別の次元はワームホール経由。行けなければ -1）。 */
    public int distanceTo(BlockPos to, @Nullable net.minecraft.resources.ResourceKey<Level> dim) {
        if (dim == null) {
            return (int) Math.ceil(Math.sqrt(to.distSqr(worldPosition)));
        }
        if (!(level instanceof ServerLevel server)) {
            return -1;
        }
        WormholeRelay.Route r = WormholeRelay.route(server, owner, worldPosition, dim, to, fluidPort());
        return r == null ? -1 : r.distance();
    }

    /** dim の to へ発送できない理由（できるなら S_IDLE）。 */
    public int problem(@Nullable BlockPos to, @Nullable net.minecraft.resources.ResourceKey<Level> dim) {
        if (dim == null) {
            return problem(to);
        }
        if (!(level instanceof ServerLevel server) || to == null) {
            return S_IDLE;
        }
        WormholeRelay.Route route = WormholeRelay.route(server, owner, worldPosition, dim, to, fluidPort());
        if (route == null) {
            return S_NO_DESTINATION;
        }
        if (!hasCargo()) {
            return S_NO_CARGO;
        }
        int tier = DroneCrateItem.tier(items.getStackInSlot(SLOT_CRATES));
        if (tier == 0) {
            return S_NO_CRATE;
        }
        if (route.distance() > DroneCrateItem.range(tier)) {
            return S_OUT_OF_RANGE;
        }
        if (energy.getEnergyStored() < DroneCrateItem.cost(route.distance())) {
            return S_NO_ENERGY;
        }
        if (!dronesAvailable(server, false) || !WormholeRelay.dronesThere(server, route, owner)) {
            return S_NO_DRONE;
        }
        return S_IDLE;
    }

    /** to へ発送できない理由（できるなら S_IDLE）。 */
    public int problem(@Nullable BlockPos to) {
        if (!(level instanceof ServerLevel server)) {
            return S_IDLE;
        }
        DroneNetwork.Port target = to == null ? null : DroneNetwork.get(server).port(to);
        if (target == null || !java.util.Objects.equals(target.owner(), owner) || to.equals(worldPosition)
                || target.fluid() != fluidPort()) {
            return S_NO_DESTINATION;
        }
        if (!hasCargo()) {
            return S_NO_CARGO;
        }
        int tier = DroneCrateItem.tier(items.getStackInSlot(SLOT_CRATES));
        if (tier == 0) {
            return S_NO_CRATE;
        }
        double distance = Math.sqrt(to.distSqr(worldPosition));
        if (distance > DroneCrateItem.range(tier)) {
            return S_OUT_OF_RANGE;
        }
        if (energy.getEnergyStored() < DroneCrateItem.cost(distance)) {
            return S_NO_ENERGY;
        }
        if (!dronesAvailable(server, false)) {
            return S_NO_DRONE;
        }
        return S_IDLE;
    }

    /** 受け持つドローンがいるか（fresh でなければ、PROBLEM_TICKS 以内に調べた結果を使う）。 */
    private boolean dronesAvailable(ServerLevel server, boolean fresh) {
        long now = server.getGameTime();
        if (fresh || !dronesKnown || now - dronesAt >= PROBLEM_TICKS || now < dronesAt) {
            dronesOk = DroneStationBlockEntity.available(server, worldPosition, owner);
            dronesAt = now;
            dronesKnown = true;
        }
        return dronesOk;
    }

    /** テスト用: 今の荷物を to 行きに梱包して積み込み待ちにし、便 flight を受け持たせる（ドローンの呼び出しを省く）。 */
    public void stageForTest(BlockPos to, long flight) {
        if (pack(to, null)) {
            flightId = flight;
        }
    }

    /** 発送: 荷物と空き箱1つを梱包し、電力を払って、積み込み待ちにする。できたら true。 */
    public boolean send() {
        if (!staged.isEmpty()) {
            return false;
        }
        if (level instanceof ServerLevel server) {
            dronesAvailable(server, true);                // 押したときは、ドローンの有無を調べ直す
        }
        cachedProblem = problem();
        problemAt = level == null ? 0 : level.getGameTime();
        return cachedProblem == S_IDLE && pack(destination, destinationDim);
    }

    /** 荷物と空き箱1つを to 行きに梱包する（発送できるのは確かめてある）。 */
    private boolean pack(BlockPos to, @Nullable net.minecraft.resources.ResourceKey<Level> dim) {
        int distance = distanceTo(to, dim);
        if (distance < 0) {
            return false;
        }
        int cost = DroneCrateItem.cost(distance);
        ItemStack crate = items.extractItem(SLOT_CRATES, 1, false);
        energy.consume(cost);
        staged = packCargo(crate);
        stagedTo = to;
        stagedDim = dim;
        stagedCost = cost;
        flightId = 0;
        if (level != null) {
            level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone_port.pack"), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        changedVisible();
        return true;
    }

    // ------------------------------------------------------------------ 自動発送

    public int autoMode() {
        return autoMode;
    }

    @Nullable
    public BlockPos autoTo() {
        return autoTo;
    }

    @Nullable
    public net.minecraft.resources.ResourceKey<Level> autoDim() {
        return autoDim;
    }

    /** しきい値を次へ（しない → 1個 → 64個 → 256個 → 満杯 → しない）。 */
    public void cycleAuto() {
        autoMode = (autoMode + 1) % AUTO_THRESHOLDS.length;
        setChanged();
    }

    /** 一覧で選んでいる行き先を、自動発送の行き先にする。 */
    public void registerAuto() {
        if (destination != null) {
            autoTo = destination;
            autoDim = destinationDim;
            setChanged();
        }
    }

    /** 荷物がしきい値までたまっているか。 */
    private boolean autoReady() {
        return autoReady(autoMode);
    }

    /** 荷物が最後に変わった時刻を今にする（液体のポートのタンクが変わったとき）。 */
    protected void cargoChanged() {
        problemStale = true;
        if (level != null) {
            cargoChangedAt = level.getGameTime();
        }
        setChanged();
    }

    /** 自動発送: たまっていて、少し待っても増えなければ（満杯なら待たずに）、登録した行き先へ送る。 */
    private void tickAuto(long now) {
        if (autoTo == null || !staged.isEmpty() || !autoReady()) {
            return;
        }
        boolean full = AUTO_THRESHOLDS[autoMode] == AUTO_FULL;
        if (!full && now - cargoChangedAt < AUTO_SETTLE) {
            return;
        }
        if (problem(autoTo, autoDim) == S_IDLE) {
            pack(autoTo, autoDim);
            problemStale = true;
        }
    }

    /**
     * 取り消し: まだ積み込んでいなければ、荷物と箱を元の欄へ戻し、払った電力を返す。ドローンが向かっていれば帰す。
     * 全部が元の欄に入りきらないときは取り消さない。
     */
    public boolean cancel() {
        if (staged.isEmpty() || anim == ANIM_LOAD || !(level instanceof ServerLevel server)) {
            return false;
        }
        ItemStack crate = emptied(staged);
        // 入りきるか先に確かめる
        if (!restoreCargo(staged, true) || !items.insertItem(SLOT_CRATES, crate, true).isEmpty()) {
            return false;
        }
        restoreCargo(staged, false);
        items.insertItem(SLOT_CRATES, crate, false);
        // 払った電力をそのまま戻す（蓄電が満杯近くでも捨てない。容量を超えた分を使い切るまで受電しない）
        energy.restoreEnergy(energy.getEnergyStored() + stagedCost);
        DroneNetwork net = DroneNetwork.get(server);
        DroneNetwork.Flight f = flightId == 0 ? null : net.flight(flightId);
        if (f != null && f.stage == DroneNetwork.PICKUP) {
            goHome(net, f, server.getGameTime());
        }
        staged = ItemStack.EMPTY;
        stagedTo = null;
        stagedDim = null;
        stagedCost = 0;
        flightId = 0;
        changedVisible();
        return true;
    }

    /** 梱包済みの箱から中身を除いた、空の箱。 */
    static ItemStack emptied(ItemStack packed) {
        ItemStack crate = packed.copy();
        if (DroneCrateItem.isFluid(crate)) {
            FluidCapsuleItem.setFluid(crate, net.neoforged.neoforge.fluids.FluidStack.EMPTY);
        } else {
            DroneCrateItem.setContents(crate, List.of());
        }
        return crate;
    }

    /** 便を帰りの段階にする。 */
    private static void goHome(DroneNetwork net, DroneNetwork.Flight f, long now) {
        net.advance(f, DroneNetwork.RETURN, now);
    }

    // ------------------------------------------------------------------ ドローンとの受け渡し

    /**
     * 真上に来たドローンが呼ぶ: 積み込み・荷降ろしを始める（または、その便の分を進めている）なら true。
     * ほかの便の受け渡し中や、着荷箱の欄に空きがないときは false（ドローンは真上で待つ）。
     */
    public boolean dock(DroneNetwork.Flight f) {
        if (anim != ANIM_NONE) {
            return animFlight == f.id;
        }
        if (f.stage == DroneNetwork.PICKUP && worldPosition.equals(f.from) && flightId == f.id && !staged.isEmpty()) {
            begin(ANIM_LOAD, f, staged);
            return true;
        }
        if (f.stage == DroneNetwork.DELIVER && worldPosition.equals(f.to) && !f.crate.isEmpty() && freeArrival() >= 0) {
            begin(ANIM_UNLOAD, f, f.crate);
            return true;
        }
        return false;
    }

    private void begin(int kind, DroneNetwork.Flight f, ItemStack crate) {
        anim = kind;
        animStart = level.getGameTime();
        animTier = DroneCrateItem.displayTier(crate);
        // 中身の色を描く液体カプセルだけ写す（梱包箱は段階の絵で描くので、中身をクライアントへ送らない）
        animCrate = DroneCrateItem.isFluid(crate) ? crate.copy() : ItemStack.EMPTY;
        animFlight = f.id;
        handed = false;
        level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone_port.hatch_open"), SoundSource.BLOCKS, 0.8F, 1.0F);
        changedVisible();
    }

    /** 着荷箱の空いている欄（なければ -1）。 */
    private int freeArrival() {
        for (int i = 0; i < ARRIVED; i++) {
            if (items.getStackInSlot(SLOT_ARRIVED + i).isEmpty()) {
                return SLOT_ARRIVED + i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ 毎 tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, DronePortBlockEntity be) {
        // 電力がたまっている間は、正面の単眼が開く
        io.github.genichimaruo.singulo.android.work.AndroidEyes.setLit(level, pos, state, be.energy.getEnergyStored() > 0);
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        DroneNetwork net = DroneNetwork.get(level);
        long now = level.getGameTime();
        if (now % 40 == 0) {
            net.putPort(worldPosition, owner, name, fluidPort());        // 網に載せておく（置き直し・読み込み直しのとき）
        }
        tickAnim(level, net, now);
        tickOutgoing(level, net, now);
        if (now % 10 == 0) {
            tickIncoming(level, net, now);
        }
        if (now % 5 == 0) {
            unpack();
        }
        if (now % 10 == 0 && (sides.ejectEnabled() || crateSides.ejectEnabled())) {
            autoEject(level);
        }
        if (now % 20 == 0) {
            tickAuto(now);
        }
    }

    // ------------------------------------------------------------------ 面の設定と自動排出

    public SideConfig sides() {
        return sides;
    }

    @Override
    public boolean validChannel(int channel) {
        return channel == CHANNEL_ITEMS || channel == CHANNEL_CRATES;
    }

    /** 自動排出の全体スイッチ（加工装置と同じく、どの面も排出しない設定なら、出せる面をみな排出にする）。 */
    @Override
    public void setEjectEnabled(int channel, boolean on) {
        if (!validChannel(channel)) return;
        SideConfig config = channel == CHANNEL_CRATES ? crateSides : sides;
        if (on && java.util.Arrays.stream(SideConfig.Face.values()).noneMatch(f -> config.canExtract(f) && config.eject(f))) {
            for (SideConfig.Face face : SideConfig.Face.values()) {
                if (config.canExtract(face)) config.set(face, config.mode(face), true);
            }
        }
        config.setEjectEnabled(on);
        setChanged();
    }

    @Override
    public void setSide(int channel, SideConfig.Face face, int mode, boolean eject) {
        if (channel == CHANNEL_CRATES) {
            crateSides.set(face, mode, eject);
        } else {
            sides.set(face, mode, eject);
        }
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(AbstractMachineBlock.FACING) ? s.getValue(AbstractMachineBlock.FACING) : Direction.NORTH;
    }

    /** 面から見た中身: 「入力」の面からは空き箱と荷物へ入れられ、「出力」の面からは受け取りと着荷箱の欄から取り出せる。 */
    public IItemHandler sidedItems(@Nullable Direction side) {
        if (side == null) {
            return automationItems();
        }
        refreshViews();
        if (sidedViews[side.ordinal()] == null) {
            sidedViews[side.ordinal()] = createSidedItems(side);
        }
        return sidedViews[side.ordinal()];
    }

    /** 向きが変わっていたら、面ごとの窓口を作り直す（面の設定は向きからの相対で決まる）。 */
    private void refreshViews() {
        if (viewsFacing != facing()) {
            java.util.Arrays.fill(sidedViews, null);
            java.util.Arrays.fill(androidViews, null);
            viewsFacing = facing();
        }
    }

    private IItemHandler createSidedItems(Direction side) {
        SideConfig.Face face = SideConfig.faceOf(facing(), side);
        IItemHandler all = automationItems();
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return SLOTS;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return items.getStackInSlot(slot);
            }

            /** 空き箱の欄へは「空き箱の搬入」の面から、荷物の欄へは荷物の「入力」の面から。 */
            private boolean mayInsert(int slot) {
                return slot == SLOT_CRATES ? crateSides.canInsert(face) : sides.canInsert(face);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return mayInsert(slot) ? all.insertItem(slot, stack, simulate) : stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot == SLOT_CRATES) {
                    return crateSides.canExtract(face) ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
                }
                return sides.canExtract(face) ? all.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return items.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return mayInsert(slot) && all.isItemValid(slot, stack);
            }
        };
    }

    /** Android stock must contain only removable goods; cargo and sealed crates are not raw materials. */
    private final IItemHandler[] androidViews = new IItemHandler[7];
    /** 面の窓口を作ったときの向き（回されたら作り直す。面の設定は向きからの相対で決まる）。 */
    @Nullable
    private Direction viewsFacing;

    @Override
    public IItemHandler androidItems(@Nullable Direction side) {
        int index = side == null ? 6 : side.ordinal();
        refreshViews();
        if (androidViews[index] != null) return androidViews[index];
        IItemHandler delegate = sidedItems(side);
        return androidViews[index] = new IItemHandler() {
            @Override public int getSlots() { return delegate.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return extractItem(slot, Integer.MAX_VALUE, true); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return delegate.insertItem(slot, stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot >= SLOT_ARRIVED && slot < SLOT_RECEIVE && DroneCrateItem.packed(items.getStackInSlot(slot)))
                    return ItemStack.EMPTY;
                return delegate.extractItem(slot, amount, simulate);
            }
            @Override public int getSlotLimit(int slot) { return delegate.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return delegate.isItemValid(slot, stack); }
        };
    }

    /** 自動排出: 「出力」を含み自動排出がオンの面の隣へ、受け取りの欄と、着荷箱の欄に残った空の箱を押し出す。 */
    private void autoEject(ServerLevel level) {
        Direction facing = facing();
        for (SideConfig.Face f : SideConfig.Face.values()) {
            boolean cargo = sides.ejectEnabled() && sides.eject(f) && sides.canExtract(f);
            boolean crates = crateSides.ejectEnabled() && crateSides.eject(f) && crateSides.canExtract(f);
            if (!cargo && !crates) {
                continue;
            }
            Direction dir = SideConfig.directionOf(facing, f);
            if (cargo) {
                ejectExtra(level, dir);
            }
            IItemHandler dest = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    worldPosition.relative(dir), dir.getOpposite());
            if (dest == null) {
                continue;
            }
            if (crates) eject(dest, SLOT_CRATES);
            if (!cargo) continue;
            for (int i = 0; i < RECEIVE; i++) {
                eject(dest, SLOT_RECEIVE + i);
            }
            for (int i = 0; i < ARRIVED; i++) {
                if (!DroneCrateItem.packed(items.getStackInSlot(SLOT_ARRIVED + i))) {
                    eject(dest, SLOT_ARRIVED + i);
                }
            }
        }
    }

    private void eject(IItemHandler dest, int slot) {
        SideConfig.pushOut(items, slot, dest);
    }

    /** 画面に出す便: このポートから出る便と、ここへ来る便（荷物を積む前・積んだあと）。 */
    public List<DroneNetwork.Flight> shipments() {
        if (!(level instanceof ServerLevel server)) {
            return List.of();
        }
        List<DroneNetwork.Flight> out = new ArrayList<>();
        for (DroneNetwork.Flight f : DroneNetwork.get(server).flightsAt(worldPosition, server.getGameTime())) {
            boolean mine = f.stage == DroneNetwork.PICKUP && worldPosition.equals(f.from) && f.id == flightId
                    || f.stage == DroneNetwork.DELIVER && (worldPosition.equals(f.from) || worldPosition.equals(f.to));
            if (mine) {
                out.add(f);
            }
        }
        return out;
    }

    /** 積み込み待ちの箱の行き先（なければ null）。 */
    @Nullable
    public BlockPos stagedTo() {
        return staged.isEmpty() ? null : stagedTo;
    }

    /** 受け渡し中のドローンの、天井より上の高さ（WAIT_LIFT 〜 0）。台が上がる間に下り、箱を渡したら台が下がる間に上がる。 */
    public double dockLift() {
        float t = level == null ? 0 : level.getGameTime() - animStart;
        float k;
        if (t < ANIM_OPEN) {
            k = 1;
        } else if (t < ANIM_RAISED) {
            k = 1 - ease((t - ANIM_OPEN) / (ANIM_RAISED - ANIM_OPEN));
        } else if (t < ANIM_HANDOFF) {
            k = 0;
        } else if (t < ANIM_LOWERED) {
            k = ease((t - ANIM_HANDOFF) / (ANIM_LOWERED - ANIM_HANDOFF));
        } else {
            k = 1;
        }
        return WAIT_LIFT * k;
    }

    private static float ease(float v) {
        v = Math.max(0, Math.min(1, v));
        return v * v * (3 - 2 * v);
    }

    /** その便の受け渡しをこのポートで今しているか。 */
    public boolean handling(DroneNetwork.Flight f) {
        return anim != ANIM_NONE && animFlight == f.id;
    }

    private void tickAnim(ServerLevel level, DroneNetwork net, long now) {
        if (anim == ANIM_NONE) {
            return;
        }
        long t = now - animStart;
        DroneNetwork.Flight f = net.flight(animFlight);
        if (f == null && !handed) {
            anim = ANIM_NONE;                           // 便がなくなった（取り消し）: 扉を閉じる
            changedVisible();
            return;
        }
        if (t == ANIM_OPEN || t == ANIM_HANDOFF + 1) {
            level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone_port.lift"), SoundSource.BLOCKS, 0.6F, t == ANIM_OPEN ? 1.1F : 0.9F);
        }
        if (t == ANIM_LOWERED) {
            level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone_port.hatch_close"), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        if (!handed && t >= ANIM_HANDOFF) {
            handed = true;
            if (anim == ANIM_LOAD) {
                f.crate = staged;
                staged = ItemStack.EMPTY;
                stagedTo = null;
                stagedDim = null;
                stagedCost = 0;
                flightId = 0;
            } else {
                int slot = freeArrival();
                items.setStackInSlot(slot, f.crate);   // 始めるときに空きを確かめた（外からは入れられない）
                f.crate = ItemStack.EMPTY;
            }
            net.changed();
            level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone_port.clamp"), SoundSource.BLOCKS, 0.9F, anim == ANIM_LOAD ? 1.0F : 0.85F);
            changedVisible();
        }
        if (t >= ANIM_TICKS) {
            int next = anim == ANIM_LOAD ? DroneNetwork.DELIVER : DroneNetwork.RETURN;
            anim = ANIM_NONE;
            if (f != null) {
                net.advance(f, next, now);
            }
            if (next == DroneNetwork.RETURN) {
                level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("drone_port.arrive"), SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            changedVisible();
        }
    }

    /** 積み込み待ち: ドローンを呼ぶ。ドローンが見えないまま着いたら（読み込みの外）、そのまま積み込んだことにする。 */
    private void tickOutgoing(ServerLevel level, DroneNetwork net, long now) {
        if (staged.isEmpty()) {
            return;
        }
        DroneNetwork.Flight f = flightId == 0 ? null : net.flight(flightId);
        if (stagedDim == null && stagedTo != null && net.port(stagedTo) == null && anim != ANIM_LOAD) {
            // 行き先のポートが壊された: 積む前なら取り消して荷物と電力を戻す。入りきらなければ、ドローンを帰して箱をここに
            // 置いておく（取消を押せば、空けたあとで戻せる。積んで、ない行き先へ飛ばすことはしない）
            if (!cancel()) {
                if (f != null && f.stage == DroneNetwork.PICKUP && worldPosition.equals(f.from)) {
                    goHome(net, f, now);
                }
                if (flightId != 0) {
                    flightId = 0;
                    changedVisible();
                }
            }
            return;
        }
        if (f == null || f.stage != DroneNetwork.PICKUP || !worldPosition.equals(f.from)) {
            if (flightId != 0) {
                flightId = 0;
                changedVisible();
            }
            // ドローンを呼ぶのは周りのステーションを調べるので重い。全機が運んでいる間も続くので、1秒に1回にする
            if (now % CALL_TICKS == 0 && stagedTo != null) {
                if (stagedDim != null) {
                    // ワームホール便: こちらの口へ届ける便にして、最終の行き先を便に持たせる（道がなければ待つ）
                    WormholeRelay.Route route = WormholeRelay.route(level, owner, worldPosition, stagedDim, stagedTo, fluidPort());
                    f = route == null ? null : DroneStationBlockEntity.call(level, worldPosition, owner, route.mouthHere());
                    if (f != null) {
                        f.relayDim = stagedDim;
                        f.relayTo = stagedTo;
                        net.changed();
                    }
                } else {
                    f = DroneStationBlockEntity.call(level, worldPosition, owner, stagedTo);
                }
                if (f != null) {
                    flightId = f.id;
                    changedVisible();
                }
            }
            return;
        }
        if (f.arrived(now) && anim == ANIM_NONE) {
            // 見えないまま着いた: 見える所なら、届けるときと同じくドローンを出して、扉を開けて積ませる
            if (level.isPositionEntityTicking(worldPosition.above()) && CargoDrone.materialize(level, net, f)) {
                return;
            }
            f.crate = staged;
            staged = ItemStack.EMPTY;
            stagedTo = null;
            stagedDim = null;
            stagedCost = 0;
            flightId = 0;
            net.advance(f, DroneNetwork.DELIVER, now);
            changedVisible();
        }
    }

    /** 見えないまま着いた便: 見える所ならドローンを出して降ろさせ、見えない所ならそのまま受け取る。 */
    private void tickIncoming(ServerLevel level, DroneNetwork net, long now) {
        for (DroneNetwork.Flight f : net.flightsAt(worldPosition, now)) {
            if (f.stage != DroneNetwork.DELIVER || !worldPosition.equals(f.to) || !f.arrived(now)) {
                continue;
            }
            if (level.isPositionEntityTicking(worldPosition.above()) && CargoDrone.materialize(level, net, f)) {
                continue;
            }
            int slot = freeArrival();
            if (slot < 0 || anim != ANIM_NONE) {
                return;
            }
            items.setStackInSlot(slot, f.crate);
            f.crate = ItemStack.EMPTY;
            net.advance(f, DroneNetwork.RETURN, now);
        }
    }

    /** 着荷箱を受け取りの欄へ開梱する（入る分だけ）。空になった箱は空き箱の欄へ移す。 */
    private void unpack() {
        for (int a = 0; a < ARRIVED; a++) {
            ItemStack crate = items.getStackInSlot(SLOT_ARRIVED + a);
            if (!DroneCrateItem.packed(crate)) {
                if (!crate.isEmpty() && DroneCrateItem.tier(crate) > 0) {
                    ItemStack rest = items.insertItem(SLOT_CRATES, crate, false);
                    // 空き箱の欄に入りきらない（別の段階の箱・満杯）ときは、着荷箱の欄の同じ空の箱に重ねて、次の荷物の欄を空ける
                    for (int b = 0; b < a && !rest.isEmpty(); b++) {
                        ItemStack other = items.getStackInSlot(SLOT_ARRIVED + b);
                        if (!other.isEmpty() && !DroneCrateItem.packed(other)) {
                            rest = items.insertItem(SLOT_ARRIVED + b, rest, false);
                        }
                    }
                    items.setStackInSlot(SLOT_ARRIVED + a, rest);
                }
                continue;
            }
            unpackOne(SLOT_ARRIVED + a, crate);
        }
    }

    // ------------------------------------------------------------------ GUI

    @Override
    public void openMenu(ServerPlayer player) {
        if (owner == null) {
            setOwner(player.getUUID());
        }
        if (!mayOperate(player)) {
            player.displayClientMessage(Component.translatable("gui.singulo.drone_port.locked"), true);
            return;
        }
        List<Target> targets = menuTargets();
        player.openMenu(this, buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeBoolean(fluidPort());
            buf.writeVarInt(targets.size());
            for (Target t : targets) {
                buf.writeBlockPos(t.pos());
                buf.writeUtf(t.name());
                buf.writeUtf(t.dim());
                buf.writeVarInt(t.distance());
            }
        });
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        List<Target> targets = menuTargets();
        return new DronePortMenu(id, inventory, worldPosition, items, data, targets.stream().map(Target::pos).toList(),
                targets.stream().map(Target::name).toList(), targets.stream().map(Target::dim).toList(),
                targets.stream().map(Target::distance).toList(), fluidPort());
    }

    /** 画面の行き先の1行: 位置・次元（"" はこの次元）・名前・道のり。 */
    private record Target(BlockPos pos, String dim, String name, int distance) {}

    /** 画面の行き先: この次元の同じ種類のポート（近い順）と、ワームホールの先のポート（近い順）。 */
    private List<Target> menuTargets() {
        List<Target> out = new ArrayList<>();
        if (!(level instanceof ServerLevel server)) {
            return out;
        }
        DroneNetwork net = DroneNetwork.get(server);
        for (BlockPos p : net.portsOf(owner, worldPosition, fluidPort())) {
            DroneNetwork.Port info = net.port(p);
            out.add(new Target(p, "", info == null ? "" : info.name(), (int) Math.ceil(Math.sqrt(p.distSqr(worldPosition)))));
        }
        for (WormholeRelay.Route r : WormholeRelay.routes(server, owner, worldPosition, fluidPort())) {
            out.add(new Target(r.to(), r.dim().location().toString(), r.name(), r.distance()));
        }
        return out;
    }

    /** 壊されたら: 中身を落とす。積み込み待ちは梱包済みの箱のまま落とし、ここへ向かう便は発送元へ戻す。 */
    @Override
    public void onBroken(Level level) {
        for (int i = 0; i < items.getSlots(); i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
        }
        Block.popResource(level, worldPosition, staged);
        staged = ItemStack.EMPTY;
        if (level instanceof ServerLevel server) {
            DroneNetwork net = DroneNetwork.get(server);
            net.removePort(worldPosition);
            long now = server.getGameTime();
            // 受け渡しのあとなら、その便は次の段階へ（積んだ箱は便に、降ろした箱は上で落とした）。前なら箱は元の側に残っている
            DroneNetwork.Flight moving = anim == ANIM_NONE ? null : net.flight(animFlight);
            if (moving != null && handed) {
                net.advance(moving, anim == ANIM_LOAD ? DroneNetwork.DELIVER : DroneNetwork.RETURN, now);
            }
            anim = ANIM_NONE;
            for (DroneNetwork.Flight f : net.flights()) {
                if (f.stage == DroneNetwork.PICKUP && (worldPosition.equals(f.from) || worldPosition.equals(f.to))) {
                    // ここで積む便も、ここへ届けるために取りに行く便も帰す（発送元の箱は、発送元が取り消して荷物を戻す）
                    goHome(net, f, now);
                } else if (f.stage == DroneNetwork.DELIVER && worldPosition.equals(f.to)) {
                    if (net.port(f.from) != null && !f.from.equals(worldPosition)) {
                        BlockPos back = f.from;          // 発送元へ戻す
                        f.from = f.to;
                        f.to = back;
                        if (f.virtual) {
                            net.redirectVirtual(f, Vec3.atCenterOf(f.from), now);
                        }
                        net.changed();
                    } else {
                        Block.popResource(level, worldPosition, f.crate);   // 戻す先もない: 梱包済みの箱のまま、ここに落とす
                        f.crate = ItemStack.EMPTY;
                        goHome(net, f, now);
                    }
                }
            }
        }
    }

    private void changedVisible() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            if (level instanceof ServerLevel server) {
                DroneNetwork.get(server).putPort(worldPosition, owner, name, fluidPort());
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            DroneNetwork.get(server).putPort(worldPosition, owner, name, fluidPort());
        }
    }

    // ------------------------------------------------------------------ 保存

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("items", items.serializeNBT(registries));
        if (!staged.isEmpty()) {
            tag.put("staged", staged.save(registries));
        }
        if (stagedTo != null) {
            tag.put("staged_to", NbtUtils.writeBlockPos(stagedTo));
        }
        tag.putInt("staged_cost", stagedCost);
        tag.putLong("flight", flightId);
        if (destination != null) {
            tag.put("destination", NbtUtils.writeBlockPos(destination));
        }
        tag.putInt("sides", sides.packed());
        tag.putInt("crate_sides", crateSides.packed());
        tag.putInt("auto", autoMode);
        putDim(tag, "destination_dim", destinationDim);
        putDim(tag, "staged_dim", stagedDim);
        putDim(tag, "auto_dim", autoDim);
        if (autoTo != null) {
            tag.put("auto_to", NbtUtils.writeBlockPos(autoTo));
        }
        saveVisible(tag, registries);
    }

    private static void putDim(CompoundTag tag, String key, @Nullable net.minecraft.resources.ResourceKey<Level> dim) {
        if (dim != null) {
            tag.putString(key, dim.location().toString());
        }
    }

    @Nullable
    private static net.minecraft.resources.ResourceKey<Level> readDim(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        var loc = net.minecraft.resources.ResourceLocation.tryParse(tag.getString(key));
        return loc == null ? null : net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc);
    }

    /** クライアントにも送るもの（名前・持ち主・扉の動き）。 */
    private void saveVisible(CompoundTag tag, HolderLookup.Provider registries) {
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putString("name", name);
        tag.putInt("anim", anim);
        tag.putLong("anim_start", animStart);
        tag.putInt("anim_tier", animTier);
        if (!animCrate.isEmpty()) {
            tag.put("anim_crate", animCrate.save(registries));
        }
        tag.putLong("anim_flight", animFlight);
        tag.putBoolean("handed", handed);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveVisible(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) {
            energy.restoreEnergy(tag.getInt("energy"));
            CompoundTag inv = tag.getCompound("items");
            inv.putInt("Size", SLOTS);
            items.deserializeNBT(registries, inv);
            staged = tag.contains("staged") ? ItemStack.parse(registries, tag.getCompound("staged")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
            stagedTo = NbtUtils.readBlockPos(tag, "staged_to").orElse(null);
            stagedCost = tag.getInt("staged_cost");
            flightId = tag.getLong("flight");
            destination = NbtUtils.readBlockPos(tag, "destination").orElse(null);
            autoMode = Math.floorMod(tag.getInt("auto"), AUTO_THRESHOLDS.length);
            autoTo = NbtUtils.readBlockPos(tag, "auto_to").orElse(null);
            destinationDim = readDim(tag, "destination_dim");
            stagedDim = readDim(tag, "staged_dim");
            autoDim = readDim(tag, "auto_dim");
            if (tag.contains("crate_sides")) {
                crateSides.setPacked(tag.getInt("crate_sides"));
            }
            if (tag.contains("sides")) {
                sides.setPacked(tag.getInt("sides"));
            }
        }
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        name = tag.getString("name");
        anim = tag.getInt("anim");
        animStart = tag.getLong("anim_start");
        animTier = tag.getInt("anim_tier");
        animCrate = tag.contains("anim_crate") ? ItemStack.parse(registries, tag.getCompound("anim_crate")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        animFlight = tag.getLong("anim_flight");
        handed = tag.getBoolean("handed");
    }
}
