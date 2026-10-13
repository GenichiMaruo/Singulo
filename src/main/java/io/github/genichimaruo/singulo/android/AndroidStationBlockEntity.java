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
 * 電力を送ると、上に護衛のアンドロイドを1体印刷する。倒されたら、キットが残っていれば少しして印刷し直す。
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

    /** スロット: 設計メモリ・キット・強化（転送アップグレード）。 */
    public static final int SLOT_MEMORY = 0, SLOT_KIT = 1, SLOT_UPGRADE = 2, SLOT_REPAIR_STOCK = 3;
    public static final int SLOTS = 4;

    /** GUI に送る値。 */
    public static final int D_ENERGY = 0, D_CAPACITY = 1, D_MODE = 2, D_RETREAT = 3, D_STATUS = 4, D_HP = 5, D_CHARGE = 6,
            D_KIND = 7, D_DELAY = 8, COUNT = 9;
    /** 状態（GUI の表示）。 */
    public static final int S_NO_MEMORY = 0, S_NO_KIT = 1, S_NO_ENERGY = 2, S_PRINTING = 3, S_ACTIVE = 4, S_RETURNING = 5,
            S_CHARGING = 6, S_WAITING = 7, S_BLOCKED = 8, S_AWAY = 9, S_LOW_POWER = 10, S_ORPHAN = 11;
    /** 身寄りのないアンドロイドを探す範囲。 */
    public static final double ORPHAN_RANGE = 24;

    public static final int BUTTON_MODE_GUARD = 0, BUTTON_MODE_WANDER = 1, BUTTON_MODE_FOLLOW = 2, BUTTON_RETREAT_DOWN = 3,
            BUTTON_RETREAT_UP = 4, BUTTON_RECALL = 5, BUTTON_LINK = 6;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    /** 転送アップグレードで預かった持ち物（自己修理キット・装甲板。次に印刷するアンドロイドに持たせる）。 */
    private final net.minecraft.world.SimpleContainer kept = new net.minecraft.world.SimpleContainer(2);
    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_MEMORY -> memoryKind(stack) != null;
                case SLOT_KIT -> isKit(stack);
                case SLOT_UPGRADE -> isTransferUpgrade(stack);
                default -> RuinAndroid.isRepairKit(stack);
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_KIT || slot == SLOT_REPAIR_STOCK ? 64 : 1;
        }
    };

    @Nullable
    private UUID owner;
    @Nullable
    private UUID android;
    private int mode = RuinAndroid.MODE_GUARD;
    private int retreat = 30;
    private int delay;
    private int status;
    private int hp;
    private int charge;
    /** アンドロイドに付けた名前（空なら型の愛称）。印刷し直しても同じ名前にする。 */
    private String name = "";
    /** コンパレーターの出力（アンドロイドの体力。0 はいない）。 */
    private int signal;

    /** リンクできる、身寄りのないアンドロイド。 */
    @Nullable
    private UUID orphan;

    private final SyncedInts data = SyncedInts.server(COUNT, i -> switch (i) {
        case D_ENERGY -> energy.getEnergyStored();
        case D_CAPACITY -> energy.getMaxEnergyStored();
        case D_MODE -> mode;
        case D_RETREAT -> retreat;
        case D_STATUS -> status;
        case D_HP -> hp;
        case D_CHARGE -> charge;
        case D_KIND -> {
            AndroidKind k = memoryKind(items.getStackInSlot(SLOT_MEMORY));
            yield k == null ? -1 : k.ordinal();
        }
        case D_DELAY -> delay;
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

    /** 転送アップグレードを積んでいるか。 */
    public boolean hasTransfer() {
        return isTransferUpgrade(items.getStackInSlot(SLOT_UPGRADE));
    }

    /** アンドロイドから転送された持ち物を預かる（slot は持ち物の位置）。預かれなかった分を返す。 */
    public ItemStack keep(int slot, ItemStack stack) {
        ItemStack held = kept.getItem(slot);
        if (held.isEmpty()) {
            kept.setItem(slot, stack);
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

    /** 預かっている持ち物（GUI の表示用）。 */
    public net.minecraft.world.SimpleContainer kept() {
        return kept;
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
    }

    /** 上で充電するアンドロイドに電力を渡す。足りれば使って true。 */
    public boolean drawCharge(int fe) {
        return energy.consume(fe);
    }

    public String name() {
        return name;
    }

    /** 名前を覚える（アンドロイドの GUI から変えたとき）。 */
    public void rememberName(String name) {
        this.name = name;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 名前を変える（ステーションの GUI から）。今いるアンドロイドにも付ける。 */
    public void rename(String name) {
        if (android != null && level instanceof ServerLevel server && server.getEntity(android) instanceof RuinAndroid a) {
            a.rename(name);
        } else {
            rememberName(name.strip());
        }
    }

    /** このステーションが受け持つアンドロイドか。 */
    public boolean manages(UUID id) {
        return id.equals(android);
    }

    /** アンドロイドが体力を知らせる（毎秒）。 */
    public void reportHealth(UUID id, float fraction) {
        if (id.equals(android)) {
            setSignal(Math.max(1, (int) Math.ceil(15 * Math.min(1, fraction))));
        }
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
        if (id.equals(android)) {
            android = null;
            setSignal(0);
            delay = 10;
            setChanged();
        }
    }

    /** アンドロイドが倒れたとき（RuinAndroid.die から）。 */
    public void onAndroidDied(UUID id) {
        if (id.equals(android)) {
            android = null;
            setSignal(0);
            delay = REPRINT_DELAY;
            setChanged();
        }
    }

    // ------------------------------------------------------------------ 毎 tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, AndroidStationBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (android != null) {
            Entity e = level.getEntity(android);
            if (e instanceof RuinAndroid a && a.isAlive()) {
                hp = Math.round(100 * a.getHealth() / a.getMaxHealth());
                charge = Math.round(100 * a.chargeFraction());
                int s = a.state();
                status = !a.printed() ? S_PRINTING : s == RuinAndroid.STATE_RETREAT ? S_RETURNING
                        : s == RuinAndroid.STATE_LOW_POWER ? S_LOW_POWER : RuinAndroid.charging(s) ? S_CHARGING : S_ACTIVE;
                if (level.getGameTime() % 20 == 0) {
                    a.configure(mode, retreat);
                }
            } else if (e == null) {
                status = S_AWAY;                         // 読み込まれていない所にいる（追従で遠くへ行った）
            } else {
                android = null;
                delay = REPRINT_DELAY;
                setSignal(0);
                setChanged();
            }
            return;
        }
        setSignal(0);
        hp = 0;
        charge = 0;
        AndroidKind kind = memoryKind(items.getStackInSlot(SLOT_MEMORY));
        if (kind != null && level.getGameTime() % 20 == 0) {
            orphan = findOrphan(level, kind);
        }
        if (orphan != null) {
            // 身寄りのないアンドロイドがいる: リンクを待つ（新しく印刷しない）
            status = S_ORPHAN;
            return;
        }
        if (delay > 0) {
            delay--;
            status = S_WAITING;
            return;
        }
        if (kind == null) {
            status = S_NO_MEMORY;
        } else if (!isKit(items.getStackInSlot(SLOT_KIT))) {
            status = S_NO_KIT;
        } else if (energy.getEnergyStored() < PRINT_FE) {
            status = S_NO_ENERGY;
        } else if (!topClear(level)) {
            status = S_BLOCKED;
        } else if (level.getGameTime() % 10 == 0) {
            print(level, kind);
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

    private boolean topClear(ServerLevel level) {
        AABB box = new AABB(worldPosition.above()).expandTowards(0, 1, 0).deflate(0.05);
        return level.noCollision(box) && level.getEntitiesOfClass(Entity.class, box, e -> !e.isSpectator()).isEmpty();
    }

    private void print(ServerLevel level, AndroidKind kind) {
        RuinAndroid a = kind.type().create(level);
        if (a == null || !energy.consume(PRINT_FE)) {
            return;
        }
        items.extractItem(SLOT_KIT, 1, false);
        // 預かっていた持ち物を持たせ、自己修理キットをストックから補充する
        for (int i = 0; i < kept.getContainerSize(); i++) {
            if (!kept.getItem(i).isEmpty()) {
                a.inventory().setItem(i, kept.removeItemNoUpdate(i));
            }
        }
        refill(a);
        Vec3 top = Vec3.atBottomCenterOf(worldPosition.above());
        a.moveTo(top.x, top.y, top.z, level.random.nextFloat() * 360, 0);
        a.startPrinting();
        a.makeGuard(owner, worldPosition, mode, retreat);
        if (!name.isEmpty()) {
            a.setCustomName(Component.literal(name));
        }
        level.addFreshEntity(a);
        android = a.getUUID();
        status = S_PRINTING;
        setSignal(15);
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
            case BUTTON_RETREAT_DOWN -> retreat = Mth.clamp(retreat - 10, 0, 90);
            case BUTTON_RETREAT_UP -> retreat = Mth.clamp(retreat + 10, 0, 90);
            case BUTTON_RECALL -> {
                if (android != null && level instanceof ServerLevel server && server.getEntity(android) instanceof RuinAndroid a) {
                    a.recall();
                }
            }
            case BUTTON_LINK -> {
                if (android == null && orphan != null && level instanceof ServerLevel server
                        && server.getEntity(orphan) instanceof RuinAndroid a && a.orphan()) {
                    a.link(worldPosition, mode, retreat);
                    android = a.getUUID();
                    orphan = null;
                    setSignal(Math.max(1, (int) Math.ceil(15 * a.getHealth() / a.getMaxHealth())));
                    rememberName(a.hasCustomName() ? a.getCustomName().getString() : "");
                    server.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.6F);
                }
            }
            default -> {
                return;
            }
        }
        if (android != null && level instanceof ServerLevel server && server.getEntity(android) instanceof RuinAndroid a) {
            a.configure(mode, retreat);
        }
        setChanged();
    }

    @Override
    public void openMenu(ServerPlayer player) {
        if (owner == null) {
            setOwner(player.getUUID());
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
        if (android != null && level instanceof ServerLevel server && server.getEntity(android) instanceof RuinAndroid a) {
            a.loseStation();
        }
        for (int i = 0; i < items.getSlots(); i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
        }
        for (int i = 0; i < kept.getContainerSize(); i++) {
            Block.popResource(level, worldPosition, kept.getItem(i));
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
        if (android != null) {
            tag.putUUID("android", android);
        }
        tag.putInt("mode", mode);
        tag.putInt("retreat", retreat);
        tag.putInt("delay", delay);
        tag.putString("name", name);
        tag.putInt("signal", signal);
        tag.put("kept", kept.createTag(registries));
    }

    // 名前はクライアントにも送る（ステーションの GUI に出すため）
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("name", name);
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
        android = tag.hasUUID("android") ? tag.getUUID("android") : null;
        mode = Mth.clamp(tag.getInt("mode"), RuinAndroid.MODE_GUARD, RuinAndroid.MODE_FOLLOW);
        retreat = tag.contains("retreat") ? Mth.clamp(tag.getInt("retreat"), 0, 90) : 30;
        delay = tag.getInt("delay");
        name = tag.getString("name");
        signal = tag.getInt("signal");
        kept.fromTag(tag.getList("kept", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
    }
}
