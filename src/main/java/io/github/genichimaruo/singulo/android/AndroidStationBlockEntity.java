package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * アンドロイド組立ステーション。設計メモリ（型を決める。減らない）とアンドロイドキット（印刷するたびに1つ使う）を入れ、
 * 電力を送ると、上に護衛のアンドロイドを1体（生産アップグレードを積めば2体まで）印刷する。上で充電できるのは1体ずつ。倒されたら、キットが残っていれば少しして印刷し直す。
 * 戻ってきたアンドロイドは、ここで電力を使って修理・充電する。GUI で動き方と、戻ってくる体力の割合を決める。
 */
public class AndroidStationBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener,
        AbstractMachineBlock.BreakListener, MenuProvider {
    public static final int CAPACITY = 400_000;
    public static final int MAX_RECEIVE = 4_000;
    /** 1体を印刷する電力。 */
    public static final int PRINT_FE = 40_000;
    /** 上で充電するときの 1 tick の電力（満充電 AndroidChargerItem.PER_ANDROID を 5秒で）。 */
    public static final int FE_PER_CHARGE_TICK = AndroidChargerItem.PER_ANDROID / 100;
    /** 倒されてから印刷し直すまで。 */
    public static final int REPRINT_DELAY = 100;

    /** スロット: 設計メモリ・キット・強化（転送アップグレード）・自己修理キットの補充・シールド許可証・生産アップグレード・巡回路（作業位置カード8枚）。 */
    public static final int SLOT_MEMORY = 0, SLOT_KIT = 1, SLOT_UPGRADE = 2, SLOT_REPAIR_STOCK = 3, SLOT_PERMIT = 4, SLOT_PRODUCTION = 5,
            SLOT_ROUTE = 6, ROUTE_SLOTS = 8;
    /** 経路の決まり（{@link io.github.genichimaruo.singulo.android.work.PathRule}）の欄（巡回の移動に効く）。 */
    public static final int SLOT_RULES = SLOT_ROUTE + ROUTE_SLOTS;
    /** 工場式印刷ユニット（印刷の時間と電力が半分、修理が2倍速）。古い保存と番号がずれないよう、最後に置く。 */
    public static final int SLOT_PRINT_UNIT = SLOT_RULES + io.github.genichimaruo.singulo.android.work.PathRule.SLOTS;
    public static final int SLOTS = SLOT_PRINT_UNIT + 1;
    /** 巡回の各地点で立ち止まる時間の選び方（秒）。 */
    public static final int[] DWELL_SECONDS = {0, 3, 5, 10, 20};
    /** 警報のレッドストーン信号を出す時間。 */
    public static final int ALARM_TICKS = 100;
    /** 受け持てるアンドロイドの数（生産アップグレードで2）。 */
    public static final int MAX_UNITS = 2;

    /** GUI に送る値。 */
    public static final int D_ENERGY = 0, D_CAPACITY = 1, D_MODE = 2, D_RETREAT = 3, D_STATUS = 4, D_HP = 5, D_CHARGE = 6,
            D_KIND = 7, D_DELAY = 8, D_ESCORT = 9, D_LEADER = 10,
            D_SELECTED = 11, D_UNITS = 12, D_PATROL = 13, D_DWELL = 14, COUNT = 15;
    /** D_PATROL のビット: 往復する、夜だけ巡回する、警報中。 */
    public static final int PATROL_BACK_AND_FORTH = 1, PATROL_NIGHT = 2, PATROL_ALARM = 4;
    /** 画面に出すモードの番号: 追従（ついていくだけ）。 */
    public static final int MODE_FOLLOW_ONLY = 9;
    /** 状態（GUI の表示）。 */
    public static final int S_NO_MEMORY = 0, S_NO_KIT = 1, S_NO_ENERGY = 2, S_PRINTING = 3, S_ACTIVE = 4, S_RETURNING = 5,
            S_CHARGING = 6, S_WAITING = 7, S_BLOCKED = 8, S_AWAY = 9, S_LOW_POWER = 10, S_ORPHAN = 11;
    /** 身寄りのないアンドロイドを探す範囲。 */
    public static final double ORPHAN_RANGE = 24;

    public static final int BUTTON_MODE_GUARD = 0, BUTTON_MODE_WANDER = 1, BUTTON_MODE_FOLLOW = 2, BUTTON_RETREAT_DOWN = 3,
            BUTTON_RETREAT_UP = 4, BUTTON_RECALL = 5, BUTTON_LINK = 6, BUTTON_FOLLOW_ONLY = 7, BUTTON_ESCORT = 8,
            BUTTON_LEADER = 9;
    /** 画面で見るアンドロイドを選ぶ（BUTTON_SELECT + 番号）。 */
    public static final int BUTTON_SELECT = 10;
    /** 巡回: 動き方を巡回に、周回 ⇔ 往復、夜だけ、立ち止まる時間。 */
    public static final int BUTTON_MODE_PATROL = 12, BUTTON_ROUTE_ORDER = 13, BUTTON_NIGHT = 14, BUTTON_DWELL = 15;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    /** 転送アップグレードで預かった持ち物（アンドロイドごと。次にその枠で印刷するアンドロイドに持たせる）。 */
    private final net.minecraft.world.SimpleContainer[] kept = {new net.minecraft.world.SimpleContainer(RuinAndroid.INVENTORY_SIZE),
            new net.minecraft.world.SimpleContainer(RuinAndroid.INVENTORY_SIZE)};
    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_PRINT_UNIT) {
                return isPrintUnit(stack);
            }
            return switch (slot) {
                case SLOT_MEMORY -> memoryKind(stack) != null;
                case SLOT_KIT -> isKit(stack);
                case SLOT_UPGRADE -> isTransferUpgrade(stack);
                case SLOT_PERMIT -> isPermit(stack);
                case SLOT_PRODUCTION -> isProductionUpgrade(stack);
                case SLOT_REPAIR_STOCK -> RuinAndroid.isRepairKit(stack);
                default -> slot >= SLOT_RULES
                        ? io.github.genichimaruo.singulo.android.work.PathRule.fits((slot - SLOT_RULES) % io.github.genichimaruo.singulo.android.work.PathRule.COLS, stack)
                        : io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.isCard(stack);   // 巡回路
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_KIT || slot == SLOT_REPAIR_STOCK ? 64 : 1;
        }
    };

    @Nullable
    private UUID owner;
    /** 受け持つアンドロイド（枠ごと。いなければ null）。 */
    private final UUID[] androids = new UUID[MAX_UNITS];
    private int mode = RuinAndroid.MODE_GUARD;
    private int retreat = 30;
    /** 追従中に戦うか（false なら、ついていくだけ）。 */
    private boolean escort = true;
    /** 追従する人（許可証に登録された人。null は持ち主）。 */
    @Nullable
    private UUID leader;
    private final int[] delays = new int[MAX_UNITS];
    private final int[] statuses = new int[MAX_UNITS];
    private final int[] hps = new int[MAX_UNITS];
    private final int[] charges = new int[MAX_UNITS];
    /** 枠ごとの体力の信号（1〜15。いなければ 0）。 */
    private final int[] signals = new int[MAX_UNITS];
    /** アンドロイドに付けた名前（枠ごと。空なら型の愛称）。印刷し直しても同じ名前にする。 */
    private final String[] names = {"", ""};
    /** 画面で見ているアンドロイドの枠。 */
    private int selected;
    /** コンパレーターの出力（アンドロイドの体力の低いほう。0 はいない）。 */
    private int signal;
    /** 巡回: 往復するか（でなければ、最後まで行ったら最初へ）、夜だけ巡回するか、各地点で立ち止まる時間（{@link #DWELL_SECONDS} の番号）。 */
    private boolean backAndForth, nightOnly;
    private int dwell = 2;
    /** 警報のレッドストーン信号を出し終える時刻（出していなければ 0）。 */
    private long alarmUntil;

    /** リンクできる、身寄りのないアンドロイド。 */
    @Nullable
    private UUID orphan;

    private final SyncedInts data = SyncedInts.server(COUNT, i -> switch (i) {
        case D_ENERGY -> energy.getEnergyStored();
        case D_CAPACITY -> energy.getMaxEnergyStored();
        case D_MODE -> mode;
        case D_RETREAT -> retreat;
        case D_STATUS -> statuses[selected];
        case D_HP -> hps[selected];
        case D_CHARGE -> charges[selected];
        case D_KIND -> {
            AndroidKind k = memoryKind(items.getStackInSlot(SLOT_MEMORY));
            yield k == null ? -1 : k.ordinal();
        }
        case D_DELAY -> delays[selected];
        case D_SELECTED -> selected;
        case D_UNITS -> units();
        case D_ESCORT -> escort ? 1 : 0;
        case D_LEADER -> leaderIndex();
        case D_PATROL -> (backAndForth ? PATROL_BACK_AND_FORTH : 0) | (nightOnly ? PATROL_NIGHT : 0) | (alarming() ? PATROL_ALARM : 0);
        case D_DWELL -> DWELL_SECONDS[dwell];
        default -> 0;
    });

    public AndroidStationBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_STATION.get(), pos, state);
    }

    // ------------------------------------------------------------------ 中身

    /** 設計メモリの型（設計メモリでなければ null）。 */
    @Nullable
    public static AndroidKind memoryKind(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (AndroidKind k : AndroidKind.values()) {
            if (path.equals(k.memoryId())) {
                return k;
            }
        }
        return null;
    }

    public static boolean isTransferUpgrade(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("android_transfer_upgrade");
    }

    public static boolean isProductionUpgrade(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("android_production_upgrade");
    }

    public static boolean isPrintUnit(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("android_factory_print_unit");
    }

    /** 工場式印刷ユニットを積んでいるか。 */
    public boolean hasPrintUnit() {
        return isPrintUnit(items.getStackInSlot(SLOT_PRINT_UNIT));
    }

    /** 1体の印刷にかかる電力（工場式印刷ユニットで半分）。 */
    public int printFe() {
        return hasPrintUnit() ? PRINT_FE / 2 : PRINT_FE;
    }

    /** 倒されてから印刷し直すまで（工場式印刷ユニットで半分）。 */
    public int reprintDelay() {
        return hasPrintUnit() ? REPRINT_DELAY / 2 : REPRINT_DELAY;
    }

    /** 受け持てるアンドロイドの数（生産アップグレードを積んでいれば2）。 */
    public int units() {
        return isProductionUpgrade(items.getStackInSlot(SLOT_PRODUCTION)) ? MAX_UNITS : 1;
    }

    /** このアンドロイドの枠（受け持っていなければ -1）。 */
    private int unitOf(UUID id) {
        for (int i = 0; i < MAX_UNITS; i++) {
            if (id.equals(androids[i])) {
                return i;
            }
        }
        return -1;
    }

    /** 転送アップグレードを積んでいるか。 */
    public boolean hasTransfer() {
        return isTransferUpgrade(items.getStackInSlot(SLOT_UPGRADE));
    }

    /** アンドロイドから転送された持ち物を預かる（slot は持ち物の位置）。預かれなかった分を返す。 */
    public ItemStack keep(UUID id, int slot, ItemStack stack) {
        int unit = unitOf(id);
        if (unit < 0 || slot >= RuinAndroid.INVENTORY_SIZE) {
            return stack;
        }
        net.minecraft.world.SimpleContainer box = kept[unit];
        ItemStack held = box.getItem(slot);
        if (held.isEmpty()) {
            box.setItem(slot, stack);
            setChanged();
            return ItemStack.EMPTY;
        }
        if (ItemStack.isSameItemSameComponents(held, stack)) {
            int move = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
            held.grow(move);
            stack.shrink(move);
            setChanged();
        }
        return stack;
    }

    /** 転送アップグレードで預かった作業（枠ごと。次に印刷するアンドロイドが同じ注文の作業を続ける）。 */
    private final BlockPos[] keptConsole = new BlockPos[MAX_UNITS];
    private final int[] keptOrder = new int[MAX_UNITS];
    private final CompoundTag[] keptWork = new CompoundTag[MAX_UNITS];

    /** 倒れたアンドロイドの作業を預かる。 */
    public void keepWork(UUID id, @Nullable BlockPos console, int order, CompoundTag state) {
        int unit = unitOf(id);
        if (unit >= 0) {
            keptConsole[unit] = console;
            keptOrder[unit] = order;
            keptWork[unit] = state.copy();
            setChanged();
        }
    }

    /** 預かっている持ち物（枠ごと）。 */
    public net.minecraft.world.SimpleContainer kept(int unit) {
        return kept[unit];
    }

    public static boolean isPermit(ItemStack stack) {
        return stack.getItem() instanceof io.github.genichimaruo.singulo.item.ShieldPermitItem;
    }

    /** 許可証に登録された人（許可証がなければ空）。 */
    public java.util.List<UUID> members() {
        return io.github.genichimaruo.singulo.item.ShieldPermitItem.members(items.getStackInSlot(SLOT_PERMIT)).stream()
                .map(io.github.genichimaruo.singulo.item.ShieldPermitItem.Member::id).toList();
    }

    /**
     * この人がステーションを使えるか。許可証にだれかが登録されていれば、持ち主と登録された人だけ。なければだれでも。
     */
    public boolean mayOperate(Player player) {
        java.util.List<UUID> members = members();
        return members.isEmpty() || owner == null || owner.equals(player.getUUID()) || members.contains(player.getUUID());
    }

    /** 追従する人に選べる、持ち主のほかの登録者（許可証の順）。 */
    public static java.util.List<io.github.genichimaruo.singulo.item.ShieldPermitItem.Member> leaders(ItemStack permit, @Nullable UUID owner) {
        return io.github.genichimaruo.singulo.item.ShieldPermitItem.members(permit).stream().filter(m -> !m.id().equals(owner)).toList();
    }

    /** 追従する人の番号（0 は持ち主、1〜 は leaders の順）。選んだ人が許可証から外れていれば持ち主。 */
    private int leaderIndex() {
        if (leader == null) {
            return 0;
        }
        var list = leaders(items.getStackInSlot(SLOT_PERMIT), owner);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(leader)) {
                return i + 1;
            }
        }
        return 0;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public boolean escort() {
        return escort;
    }

    /** 追従中に戦うか（アンドロイドの画面で切り替えたとき）。 */
    public void setEscort(boolean escort) {
        if (this.escort != escort) {
            this.escort = escort;
            setChanged();
        }
    }

    /** 巡回路: 巡回路の欄の作業位置カードの場所（左から順。この世界のものだけ）。 */
    public java.util.List<BlockPos> route() {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int i = 0; i < ROUTE_SLOTS; i++) {
            ItemStack card = items.getStackInSlot(SLOT_ROUTE + i);
            BlockPos p = io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.pos(card);
            if (p != null && level != null && io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.sameDimension(card, level)) {
                out.add(p);
            }
        }
        return out;
    }

    /** 警報: しばらくレッドストーン信号を出す（巡回中のアンドロイドが、敵の群れ・ボスを見つけた）。 */
    public void alarm() {
        if (level == null) {
            return;
        }
        boolean was = alarming();
        alarmUntil = level.getGameTime() + ALARM_TICKS;
        if (!was) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
        }
        setChanged();
    }

    /** 警報のレッドストーン信号を出しているか。 */
    public boolean alarming() {
        return alarmUntil != 0 && level != null && level.getGameTime() < alarmUntil;
    }

    /** アンドロイドに今の設定（動き方・戻る体力・追従中に戦うか・許可証・追従する人・巡回路）を渡す。 */
    private void sync(RuinAndroid a) {
        a.configure(mode, retreat);
        a.setPatrol(route(), backAndForth, nightOnly, DWELL_SECONDS[dwell] * 20);
        a.setPatrolRules(level == null ? java.util.List.of() : io.github.genichimaruo.singulo.android.work.PathRule.read(items, SLOT_RULES, level));
        if (!a.escortPending()) {
            a.setEscort(escort);
        }
        a.setMembers(members());
        a.setLeader(leaderIndex() > 0 ? leader : null);
    }

    public static boolean isKit(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("android_kit");
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public ItemStackHandler items() {
        return items;
    }

    /** 外（ホッパー・パイプ）から見た中身: 入れられるが、取り出せない（設計メモリなどが吸い出されないように）。 */
    public net.neoforged.neoforge.items.IItemHandler automationItems() {
        return new net.neoforged.neoforge.items.IItemHandler() {
            @Override
            public int getSlots() {
                return items.getSlots();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return items.getStackInSlot(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return items.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return items.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return items.isItemValid(slot, stack);
            }
        };
    }

    /** ストックから、アンドロイドの自己修理キットを1スタックまで補充する。 */
    public void refill(RuinAndroid android) {
        ItemStack stock = items.getStackInSlot(SLOT_REPAIR_STOCK);
        if (stock.isEmpty()) {
            return;
        }
        ItemStack carried = android.inventory().getItem(RuinAndroid.SLOT_REPAIR);
        if (carried.isEmpty()) {
            android.inventory().setItem(RuinAndroid.SLOT_REPAIR, items.extractItem(SLOT_REPAIR_STOCK, stock.getMaxStackSize(), false));
        } else if (ItemStack.isSameItemSameComponents(carried, stock) && carried.getCount() < carried.getMaxStackSize()) {
            ItemStack moved = items.extractItem(SLOT_REPAIR_STOCK, carried.getMaxStackSize() - carried.getCount(), false);
            carried.grow(moved.getCount());
            android.inventory().setChanged();
        }
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);   // 持ち主はクライアントにも送る
        }
    }

    /** 上で充電するアンドロイドに電力を渡す。足りれば使って true。 */
    public boolean drawCharge(int fe) {
        return energy.consume(fe);
    }

    /** 枠の名前（空なら型の愛称）。 */
    public String name(int unit) {
        return names[unit];
    }

    /** 名前を覚える（アンドロイドの GUI から変えたとき）。 */
    public void rememberName(UUID id, String name) {
        int unit = unitOf(id);
        if (unit < 0) {
            return;
        }
        names[unit] = name;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 名前を変える（ステーションの GUI から。見ている枠）。今いるアンドロイドにも付ける。 */
    public void rename(String name) {
        UUID id = androids[selected];
        if (id != null && level instanceof ServerLevel server && server.getEntity(id) instanceof RuinAndroid a) {
            a.rename(name);
        } else {
            names[selected] = name.strip();
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /** このステーションが受け持つアンドロイドか。 */
    public boolean manages(UUID id) {
        return unitOf(id) >= 0;
    }

    /** アンドロイドが体力を知らせる（毎秒）。 */
    public void reportHealth(UUID id, float fraction) {
        int unit = unitOf(id);
        if (unit >= 0) {
            signals[unit] = Math.max(1, (int) Math.ceil(15 * Math.min(1, fraction)));
            updateSignal();
        }
    }

    /** コンパレーターの出力を、受け持つアンドロイドの体力の低いほうにする（いなければ 0）。 */
    private void updateSignal() {
        int value = 0;
        for (int i = 0; i < MAX_UNITS; i++) {
            if (androids[i] != null) {
                value = value == 0 ? signals[i] : Math.min(value, signals[i]);
            }
        }
        setSignal(value);
    }

    /** コンパレーターの出力（0〜15）。 */
    public int signal() {
        return signal;
    }

    private void setSignal(int value) {
        if (value != signal) {
            signal = value;
            setChanged();
            if (level != null) {
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    /** 解体されたとき: すぐに印刷し直す（キットと電力があれば）。 */
    public void onAndroidDismantled(UUID id) {
        release(id, 10);
    }

    /** アンドロイドが倒れたとき（RuinAndroid.die から）。 */
    public void onAndroidDied(UUID id) {
        release(id, reprintDelay());
    }

    /** アンドロイドの枠を空ける（delay tick ののち、キットがあれば印刷し直す）。 */
    private void release(UUID id, int delay) {
        int unit = unitOf(id);
        if (unit >= 0) {
            androids[unit] = null;
            signals[unit] = 0;
            delays[unit] = delay;
            updateSignal();
            setChanged();
        }
    }

    // ------------------------------------------------------------------ 毎 tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, AndroidStationBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (alarmUntil != 0 && level.getGameTime() >= alarmUntil) {
            alarmUntil = 0;                          // 警報を止める
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        if (selected >= units() && androids[selected] == null) {
            selected = 0;
        }
        AndroidKind kind = memoryKind(items.getStackInSlot(SLOT_MEMORY));
        boolean empty = false;
        for (int i = 0; i < units(); i++) {
            empty |= androids[i] == null;
        }
        if (!empty) {
            orphan = null;
        } else if (kind != null && level.getGameTime() % 20 == 0) {
            orphan = findOrphan(level, kind);
        }
        boolean printing = false;
        for (int i = 0; i < MAX_UNITS; i++) {
            if (androids[i] != null) {
                Entity e = level.getEntity(androids[i]);
                if (e instanceof RuinAndroid a && a.isAlive()) {
                    hps[i] = Math.round(100 * a.getHealth() / a.getMaxHealth());
                    charges[i] = Math.round(100 * a.chargeFraction());
                    int s = a.state();
                    statuses[i] = !a.printed() ? S_PRINTING : s == RuinAndroid.STATE_RETREAT ? S_RETURNING
                            : s == RuinAndroid.STATE_LOW_POWER ? S_LOW_POWER : RuinAndroid.charging(s) ? S_CHARGING : S_ACTIVE;
                    if (level.getGameTime() % 20 == 0) {
                        sync(a);
                    }
                } else if (e == null) {
                    statuses[i] = S_AWAY;                // 読み込まれていない所にいる（追従で遠くへ行った）
                } else {
                    release(androids[i], reprintDelay());
                }
                continue;
            }
            hps[i] = 0;
            charges[i] = 0;
            if (i >= units()) {
                continue;                                // 生産アップグレードがないと、2体目は印刷しない
            }
            if (orphan != null) {
                // 身寄りのないアンドロイドがいる: リンクを待つ（新しく印刷しない）
                statuses[i] = S_ORPHAN;
            } else if (delays[i] > 0) {
                delays[i]--;
                statuses[i] = S_WAITING;
            } else if (kind == null) {
                statuses[i] = S_NO_MEMORY;
            } else if (!isKit(items.getStackInSlot(SLOT_KIT))) {
                statuses[i] = S_NO_KIT;
            } else if (energy.getEnergyStored() < printFe()) {
                statuses[i] = S_NO_ENERGY;
            } else if (printing || !topClear(level)) {
                statuses[i] = S_BLOCKED;                 // 上で印刷中・充電中のアンドロイドがどくのを待つ
            } else if (level.getGameTime() % 10 == 0) {
                print(level, kind, i);
                printing = true;
            }
        }
    }

    /** 近くの、同じ型で、ステーションを失った護衛（持ち主が同じか、持ち主がいない）。 */
    @Nullable
    private UUID findOrphan(ServerLevel level, AndroidKind kind) {
        for (RuinAndroid a : level.getEntitiesOfClass(RuinAndroid.class, new AABB(worldPosition).inflate(ORPHAN_RANGE),
                a -> a.isAlive() && a.orphan() && a.kind() == kind && (owner == null || owner.equals(a.owner())))) {
            return a.getUUID();
        }
        return null;
    }

    /** 印刷したいのに、上にだれかいて待っている（上で待機しているアンドロイドは、どく）。 */
    public boolean wantsTop() {
        for (int i = 0; i < units(); i++) {
            if (androids[i] == null && statuses[i] == S_BLOCKED) {
                return true;
            }
        }
        return false;
    }

    private boolean topClear(ServerLevel level) {
        AABB box = new AABB(worldPosition.above()).expandTowards(0, 1, 0).deflate(0.05);
        return level.noCollision(box) && level.getEntitiesOfClass(Entity.class, box, e -> !e.isSpectator()).isEmpty();
    }

    private void print(ServerLevel level, AndroidKind kind, int unit) {
        RuinAndroid a = kind.type().create(level);
        if (a == null || !energy.consume(printFe())) {
            return;
        }
        items.extractItem(SLOT_KIT, 1, false);
        // 預かっていた持ち物を持たせ、自己修理キットをストックから補充する
        net.minecraft.world.SimpleContainer box = kept[unit];
        for (int i = 0; i < box.getContainerSize(); i++) {
            if (!box.getItem(i).isEmpty()) {
                a.inventory().setItem(i, box.removeItemNoUpdate(i));
            }
        }
        refill(a);
        a.setCharge(a.maxCharge());
        if (keptConsole[unit] != null) {
            a.assignWork(keptConsole[unit], keptOrder[unit]);
            if (keptWork[unit] != null) {
                a.restoreWorkState(keptWork[unit]);
            }
            keptConsole[unit] = null;
            keptWork[unit] = null;
        }
        Vec3 top = Vec3.atBottomCenterOf(worldPosition.above());
        a.moveTo(top.x, top.y, top.z, level.random.nextFloat() * 360, 0);
        a.startPrinting();
        if (hasPrintUnit()) {
            a.skipPrinting(RuinAndroid.PRINT_TICKS / 2);           // 工場式印刷ユニット: 半分の時間で印刷し終える
        }
        a.makeGuard(owner, worldPosition, mode, retreat);
        sync(a);
        if (!names[unit].isEmpty()) {
            a.setCustomName(Component.literal(names[unit]));
        }
        level.addFreshEntity(a);
        androids[unit] = a.getUUID();
        statuses[unit] = S_PRINTING;
        signals[unit] = 15;
        updateSignal();
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
        setChanged();
    }

    // ------------------------------------------------------------------ GUI

    public int mode() {
        return mode;
    }

    public int retreat() {
        return retreat;
    }

    /** GUI のボタン。 */
    public void press(int id) {
        switch (id) {
            case BUTTON_MODE_GUARD, BUTTON_MODE_WANDER, BUTTON_MODE_FOLLOW -> mode = id;
            case BUTTON_MODE_PATROL -> mode = RuinAndroid.MODE_PATROL;
            case BUTTON_ROUTE_ORDER -> backAndForth = !backAndForth;
            case BUTTON_NIGHT -> nightOnly = !nightOnly;
            case BUTTON_DWELL -> dwell = (dwell + 1) % DWELL_SECONDS.length;
            case BUTTON_RETREAT_DOWN -> retreat = Mth.clamp(retreat - 10, 0, 90);
            case BUTTON_RETREAT_UP -> retreat = Mth.clamp(retreat + 10, 0, 90);
            case BUTTON_FOLLOW_ONLY -> escort = false;
            case BUTTON_ESCORT -> escort = true;
            case BUTTON_LEADER -> {
                // 持ち主 → 登録した人（順に）→ 持ち主 …
                var list = leaders(items.getStackInSlot(SLOT_PERMIT), owner);
                int next = (leaderIndex() + 1) % (list.size() + 1);
                leader = next == 0 ? null : list.get(next - 1).id();
            }
            case BUTTON_RECALL -> {
                if (androids[selected] != null && level instanceof ServerLevel server
                        && server.getEntity(androids[selected]) instanceof RuinAndroid a) {
                    a.recall();
                }
            }
            case BUTTON_LINK -> {
                // 見ている枠が空いていればそこへ、埋まっていれば空いている枠へつなぐ
                int unit = selected < units() && androids[selected] == null ? selected : -1;
                for (int i = 0; unit < 0 && i < units(); i++) {
                    if (androids[i] == null) {
                        unit = i;
                    }
                }
                if (unit >= 0 && orphan != null && level instanceof ServerLevel server
                        && server.getEntity(orphan) instanceof RuinAndroid a && a.orphan()) {
                    a.link(worldPosition, mode, retreat);
                    sync(a);
                    androids[unit] = a.getUUID();
                    orphan = null;
                    signals[unit] = Math.max(1, (int) Math.ceil(15 * a.getHealth() / a.getMaxHealth()));
                    updateSignal();
                    rememberName(a.getUUID(), a.hasCustomName() ? a.getCustomName().getString() : "");
                    selected = unit;
                    server.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.6F);
                }
            }
            case BUTTON_SELECT, BUTTON_SELECT + 1 -> {
                int unit = id - BUTTON_SELECT;
                if (unit < units() || androids[unit] != null) {
                    selected = unit;
                }
                return;
            }
            default -> {
                return;
            }
        }
        if (level instanceof ServerLevel server) {
            for (UUID unit : androids) {
                if (unit != null && server.getEntity(unit) instanceof RuinAndroid a) {
                    sync(a);
                }
            }
        }
        setChanged();
    }

    @Override
    public void openMenu(ServerPlayer player) {
        if (owner == null) {
            setOwner(player.getUUID());
        }
        if (!mayOperate(player)) {
            player.displayClientMessage(Component.translatable("gui.singulo.android_station.locked"), true);
            return;
        }
        player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AndroidStationMenu(id, inventory, worldPosition, items, data);
    }

    /** 壊されたら、中身を落とし、アンドロイドはステーションを失う（その場に残る）。 */
    @Override
    public void onBroken(Level level) {
        // 読み込まれているアンドロイドには、すぐ知らせる（同じ場所に置き直しても、新しいステーションでリンクできるように）
        if (level instanceof ServerLevel server) {
            for (UUID unit : androids) {
                if (unit != null && server.getEntity(unit) instanceof RuinAndroid a) {
                    a.loseStation();
                }
            }
        }
        for (int i = 0; i < items.getSlots(); i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
        }
        for (net.minecraft.world.SimpleContainer box : kept) {
            for (int i = 0; i < box.getContainerSize(); i++) {
                Block.popResource(level, worldPosition, box.getItem(i));
            }
        }
    }

    // ------------------------------------------------------------------ 保存

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("items", items.serializeNBT(registries));
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        for (int i = 0; i < MAX_UNITS; i++) {
            if (androids[i] != null) {
                tag.putUUID("android" + i, androids[i]);
            }
            tag.putInt("delay" + i, delays[i]);
            tag.putString("name" + i, names[i]);
            tag.putInt("signal" + i, signals[i]);
            net.minecraft.core.NonNullList<ItemStack> list = net.minecraft.core.NonNullList.withSize(RuinAndroid.INVENTORY_SIZE, ItemStack.EMPTY);
            for (int s = 0; s < RuinAndroid.INVENTORY_SIZE; s++) {
                list.set(s, kept[i].getItem(s));
            }
            tag.put("kept" + i, net.minecraft.world.ContainerHelper.saveAllItems(new CompoundTag(), list, registries));
        }
        tag.putInt("selected", selected);
        for (int i = 0; i < MAX_UNITS; i++) {
            if (keptConsole[i] != null) {
                tag.put("kept_console" + i, net.minecraft.nbt.NbtUtils.writeBlockPos(keptConsole[i]));
                tag.putInt("kept_order" + i, keptOrder[i]);
                if (keptWork[i] != null) {
                    tag.put("kept_work" + i, keptWork[i]);
                }
            }
        }
        tag.putInt("mode", mode);
        tag.putInt("retreat", retreat);
        tag.putBoolean("escort", escort);
        if (leader != null) {
            tag.putUUID("leader", leader);
        }
        tag.putInt("signal", signal);
        tag.putBoolean("back_and_forth", backAndForth);
        tag.putBoolean("night_only", nightOnly);
        tag.putInt("dwell", dwell);
    }

    // 名前と持ち主はクライアントにも送る（ステーションの GUI に出すため。追従する人の候補から持ち主を除く）
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        for (int i = 0; i < MAX_UNITS; i++) {
            tag.putString("name" + i, names[i]);
        }
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("energy"));
        CompoundTag inv = tag.getCompound("items");
        inv.putInt("Size", SLOTS);
        items.deserializeNBT(registries, inv);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        for (int i = 0; i < MAX_UNITS; i++) {
            androids[i] = tag.hasUUID("android" + i) ? tag.getUUID("android" + i) : null;
            delays[i] = tag.getInt("delay" + i);
            names[i] = tag.getString("name" + i);
            signals[i] = tag.getInt("signal" + i);
            kept[i].clearContent();
            if (tag.contains("kept" + i)) {
                net.minecraft.core.NonNullList<ItemStack> list = net.minecraft.core.NonNullList.withSize(RuinAndroid.INVENTORY_SIZE, ItemStack.EMPTY);
                net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("kept" + i), list, registries);
                for (int s = 0; s < RuinAndroid.INVENTORY_SIZE; s++) {
                    kept[i].setItem(s, list.get(s));
                }
            }
        }
        // 0.6.0 までの保存（1体だけ）は、1つ目の枠に入れる
        if (tag.hasUUID("android")) {
            androids[0] = tag.getUUID("android");
        }
        if (tag.contains("name")) {
            names[0] = tag.getString("name");
        }
        if (tag.contains("delay")) {
            delays[0] = tag.getInt("delay");
        }
        for (net.minecraft.nbt.Tag t : tag.getList("kept", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(stack -> kept[0].setItem(RuinAndroid.isArmorPlate(stack) ? RuinAndroid.SLOT_PLATE
                    : RuinAndroid.SLOT_REPAIR, stack));
        }
        selected = Mth.clamp(tag.getInt("selected"), 0, MAX_UNITS - 1);
        for (int i = 0; i < MAX_UNITS; i++) {
            keptConsole[i] = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "kept_console" + i).orElse(null);
            keptOrder[i] = tag.getInt("kept_order" + i);
            keptWork[i] = tag.contains("kept_work" + i) ? tag.getCompound("kept_work" + i).copy() : null;
        }
        mode = Mth.clamp(tag.getInt("mode"), RuinAndroid.MODE_GUARD, RuinAndroid.MODE_PATROL);
        retreat = tag.contains("retreat") ? Mth.clamp(tag.getInt("retreat"), 0, 90) : 30;
        escort = !tag.contains("escort") || tag.getBoolean("escort");
        leader = tag.hasUUID("leader") ? tag.getUUID("leader") : null;
        signal = tag.getInt("signal");
        backAndForth = tag.getBoolean("back_and_forth");
        nightOnly = tag.getBoolean("night_only");
        dwell = tag.contains("dwell") ? Mth.clamp(tag.getInt("dwell"), 0, DWELL_SECONDS.length - 1) : 2;
    }
}
