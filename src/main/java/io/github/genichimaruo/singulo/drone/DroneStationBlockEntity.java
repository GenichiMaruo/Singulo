package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.SyncedInts;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.List;
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
 * ドローン・ステーション。ドローン用クリスタルメモリ（減らない）とドローンキット（印刷ごとに1つ）を入れて電力を送ると、
 * 上にドローンを印刷する（生産アップグレードで3機まで）。ドローンは上空で待機し、半径 DISPATCH_RANGE の発着ポートに呼ばれて荷物を運ぶ。
 */
public class DroneStationBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener,
        AbstractMachineBlock.BreakListener, MenuProvider {
    public static final int CAPACITY = 200_000;
    public static final int MAX_RECEIVE = 4_000;
    public static final int PRINT_FE = 20_000;
    public static final int MAX_UNITS = 3;
    /** ポートに呼ばれる範囲（ブロック）。 */
    public static final int DISPATCH_RANGE = 64;
    /** 待機しているはずのドローンが見当たらないとき、印刷し直すまで（tick）。 */
    static final int LOST_TICKS = 100;

    public static final int SLOT_MEMORY = 0, SLOT_KIT = 1, SLOT_PRODUCTION = 2, SLOT_PERMIT = 3, SLOTS = 4;

    /** GUI に送る値: 電力・容量・受け持てる数・枠ごとの状態。 */
    public static final int D_ENERGY = 0, D_CAPACITY = 1, D_UNITS = 2, D_STATUS = 3, COUNT = D_STATUS + MAX_UNITS;
    /** 枠の状態: メモリなし・キットなし・電力不足・印刷中・待機中・運搬中・上がふさがっている・使わない枠・止めた枠・解体中。 */
    public static final int S_NO_MEMORY = 0, S_NO_KIT = 1, S_NO_ENERGY = 2, S_PRINTING = 3, S_IDLE = 4, S_FLYING = 5,
            S_BLOCKED = 6, S_UNUSED = 7, S_STOPPED = 8, S_DISMANTLING = 9;
    /** ボタン: 枠のドローンを解体して止める（BUTTON_DISMANTLE + 枠）、止めた枠をまた印刷する（BUTTON_RESUME + 枠）。 */
    public static final int BUTTON_DISMANTLE = 0, BUTTON_RESUME = 10;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_MEMORY -> isMemory(stack);
                case SLOT_KIT -> isKit(stack);
                case SLOT_PRODUCTION -> io.github.genichimaruo.singulo.android.AndroidStationBlockEntity.isProductionUpgrade(stack);
                default -> io.github.genichimaruo.singulo.android.AndroidStationBlockEntity.isPermit(stack);
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_KIT ? 64 : 1;
        }
    };

    @Nullable
    private UUID owner;
    private final UUID[] drones = new UUID[MAX_UNITS];
    private final int[] statuses = new int[MAX_UNITS];
    private final int[] missing = new int[MAX_UNITS];
    /** 解体して止めた枠（もう一度印刷を押すまで、印刷し直さない）。 */
    private final boolean[] stopped = new boolean[MAX_UNITS];

    private final SyncedInts data = SyncedInts.server(COUNT, i -> switch (i) {
        case D_ENERGY -> energy.getEnergyStored();
        case D_CAPACITY -> energy.getMaxEnergyStored();
        case D_UNITS -> units();
        default -> statuses[i - D_STATUS];
    });

    public DroneStationBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.DRONE_STATION.get(), pos, state);
    }

    public static boolean isMemory(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("drone_memory");
    }

    public static boolean isKit(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("drone_kit");
    }

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

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** 受け持てるドローンの数（生産アップグレードで3）。 */
    public int units() {
        return io.github.genichimaruo.singulo.android.AndroidStationBlockEntity.isProductionUpgrade(items.getStackInSlot(SLOT_PRODUCTION))
                ? MAX_UNITS : 1;
    }

    /** このステーションが受け持つドローンか。 */
    public boolean manages(UUID id, int unit) {
        return unit >= 0 && unit < MAX_UNITS && id.equals(drones[unit]);
    }

    public int status(int unit) {
        return statuses[unit];
    }

    /** 許可証に登録された人だけが使える（許可証がなければ、だれでも）。 */
    public boolean mayOperate(Player player) {
        List<UUID> members = io.github.genichimaruo.singulo.item.ShieldPermitItem.members(items.getStackInSlot(SLOT_PERMIT)).stream()
                .map(io.github.genichimaruo.singulo.item.ShieldPermitItem.Member::id).toList();
        return members.isEmpty() || owner == null || owner.equals(player.getUUID()) || members.contains(player.getUUID());
    }

    /** 枠ごとの待機位置（ステーションの上。2機目と3機目は左右の少し高い所）。 */
    public static Vec3 hover(BlockPos station, int unit) {
        double x = unit == 1 ? -1.0 : unit == 2 ? 1.0 : 0;
        double y = unit == 0 ? 1.6 : 2.1;
        return new Vec3(station.getX() + 0.5 + x, station.getY() + 1 + y, station.getZ() + 0.5);
    }

    // ------------------------------------------------------------------ 呼び出し

    /** 呼べるドローン: 待機中のドローン（ステーションの枠）か、荷物を届け終えて帰りかけのドローン（その便）。 */
    private record Candidate(@Nullable DroneStationBlockEntity station, int unit, @Nullable DroneNetwork.Flight returning, double distSqr) {}

    /** port に呼べるドローン（port に近い順）。読み込まれているものだけ。 */
    private static List<Candidate> candidates(ServerLevel level, BlockPos port, @Nullable UUID owner) {
        List<Candidate> found = new ArrayList<>();
        double range = (double) DISPATCH_RANGE * DISPATCH_RANGE;
        int c = (DISPATCH_RANGE >> 4) + 1;
        DroneNetwork net = DroneNetwork.get(level);
        for (int cx = (port.getX() >> 4) - c; cx <= (port.getX() >> 4) + c; cx++) {
            for (int cz = (port.getZ() >> 4) - c; cz <= (port.getZ() >> 4) + c; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof DroneStationBlockEntity s) || s.getBlockPos().distSqr(port) > range
                            || !java.util.Objects.equals(s.owner, owner)) {
                        continue;
                    }
                    for (int i = 0; i < MAX_UNITS; i++) {
                        if (s.drones[i] != null && net.flightOf(s.worldPosition, i) == null && !s.stopped[i]
                                && level.getEntity(s.drones[i]) instanceof CargoDrone d && d.isAlive() && d.idle()) {
                            found.add(new Candidate(s, i, null, d.position().distanceToSqr(Vec3.atCenterOf(port))));
                        }
                    }
                }
            }
        }
        // 届け終えて帰りかけのドローン（ステーションへ戻らずに、そのまま次の仕事へ）
        for (DroneNetwork.Flight f : net.flights()) {
            if (f.stage != DroneNetwork.RETURN || f.virtual || f.drone == null || f.station == null || !f.crate.isEmpty()
                    || !java.util.Objects.equals(f.owner, owner)) {
                continue;
            }
            if (level.getEntity(f.drone) instanceof CargoDrone d && d.isAlive() && !d.dismantling()) {
                double dist = d.position().distanceToSqr(Vec3.atCenterOf(port));
                if (dist <= range) {
                    found.add(new Candidate(null, f.unit, f, dist));
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(Candidate::distSqr));
        return found;
    }

    /**
     * port を受け持つドローンがいるか（いなければ発送できない）。今は運んでいても、手が空けば来るドローンも数える
     * （発送した箱は積み込み待ちになり、近くで荷物を届け終えたドローンがそのまま取りに来る）。
     */
    public static boolean available(ServerLevel level, BlockPos port, @Nullable UUID owner) {
        if (!candidates(level, port, owner).isEmpty()) {
            return true;
        }
        DroneNetwork net = DroneNetwork.get(level);
        double range = (double) DISPATCH_RANGE * DISPATCH_RANGE;
        int c = (DISPATCH_RANGE >> 4) + 1;
        for (int cx = (port.getX() >> 4) - c; cx <= (port.getX() >> 4) + c; cx++) {
            for (int cz = (port.getZ() >> 4) - c; cz <= (port.getZ() >> 4) + c; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof DroneStationBlockEntity s && s.getBlockPos().distSqr(port) <= range
                            && java.util.Objects.equals(s.owner, owner) && s.hasFleet(net)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 働けるドローンが1機でもいるか（待機中・運搬中。止めた枠と解体中は数えない）。 */
    private boolean hasFleet(DroneNetwork net) {
        for (int i = 0; i < MAX_UNITS; i++) {
            if (net.flightOf(worldPosition, i) != null || drones[i] != null && !stopped[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * port に近い、owner のドローン（待機中か、帰りかけ）を1機呼ぶ。呼べたら便の記録。
     */
    @Nullable
    public static DroneNetwork.Flight call(ServerLevel level, BlockPos port, @Nullable UUID owner, BlockPos to) {
        DroneNetwork net = DroneNetwork.get(level);
        for (Candidate c : candidates(level, port, owner)) {
            if (c.returning() != null) {
                net.retask(c.returning(), port, to);
                return c.returning();
            }
            DroneNetwork.Flight f = net.create(c.station().worldPosition, c.unit(), owner, port, to);
            f.drone = c.station().drones[c.unit()];
            c.station().statuses[c.unit()] = S_FLYING;
            return f;
        }
        return null;
    }

    // ------------------------------------------------------------------ 解体

    /** 枠のドローンを解体する（待機中だけ）。ドローンはステーションの上に降りて、印刷を逆にたどって消え、キットが戻る。 */
    public void dismantle(int unit) {
        if (unit < 0 || unit >= MAX_UNITS || !(level instanceof ServerLevel server) || drones[unit] == null
                || DroneNetwork.get(server).flightOf(worldPosition, unit) != null) {
            return;
        }
        if (server.getEntity(drones[unit]) instanceof CargoDrone d && d.idle()) {
            d.startDismantle();
            stopped[unit] = true;
            setChanged();
        }
    }

    /** 止めた枠をまた印刷する。 */
    public void resume(int unit) {
        if (unit >= 0 && unit < MAX_UNITS && stopped[unit]) {
            stopped[unit] = false;
            setChanged();
        }
    }

    /** 解体し終えた: キットを欄へ戻す（入りきらなければ、ステーションの上に出す）。 */
    public void onDismantled(UUID id, int unit) {
        if (!manages(id, unit) || level == null) {
            return;
        }
        drones[unit] = null;
        stopped[unit] = true;
        ItemStack kit = new ItemStack(BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("drone_kit")));
        ItemStack rest = items.insertItem(SLOT_KIT, kit, false);
        if (!rest.isEmpty()) {
            Block.popResource(level, worldPosition.above(), rest);
        }
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.8F);
        setChanged();
    }

    /** GUI のボタン。 */
    public void press(int id) {
        if (id >= BUTTON_RESUME && id < BUTTON_RESUME + MAX_UNITS) {
            resume(id - BUTTON_RESUME);
        } else if (id >= BUTTON_DISMANTLE && id < BUTTON_DISMANTLE + MAX_UNITS) {
            dismantle(id - BUTTON_DISMANTLE);
        }
    }

    // ------------------------------------------------------------------ 毎 tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneStationBlockEntity be) {
        io.github.genichimaruo.singulo.android.work.AndroidEyes.setLit(level, pos, state, be.energy.getEnergyStored() > 0);
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        DroneNetwork net = DroneNetwork.get(level);
        long now = level.getGameTime();
        boolean printing = false;
        for (int i = 0; i < MAX_UNITS; i++) {
            DroneNetwork.Flight f = net.flightOf(worldPosition, i);
            if (f != null) {
                if (f.stage == DroneNetwork.RETURN && f.arrived(now) && f.crate.isEmpty()) {
                    // 見えないまま帰ってきた: 空から待機位置へ降りてくる
                    net.remove(f);
                    CargoDrone d = CargoDrone.spawn(level, worldPosition, i, hover(worldPosition, i).add(0, 12, 0), false);
                    drones[i] = d == null ? null : d.getUUID();
                } else if (f.drone != null && !f.drone.equals(drones[i])) {
                    drones[i] = f.drone;                  // 読み込み直しで実体が変わった
                    setChanged();
                }
                statuses[i] = S_FLYING;
                missing[i] = 0;
                continue;
            }
            if (drones[i] != null) {
                Entity e = level.getEntity(drones[i]);
                if (e instanceof CargoDrone d && d.isAlive()) {
                    statuses[i] = d.dismantling() ? S_DISMANTLING : d.printed() ? S_IDLE : S_PRINTING;
                    missing[i] = 0;
                } else if (++missing[i] > LOST_TICKS && level.isPositionEntityTicking(BlockPos.containing(hover(worldPosition, i)))) {
                    drones[i] = null;                     // 待機位置が見えているのにいない: 印刷し直す
                    setChanged();
                }
                continue;
            }
            if (i >= units()) {
                statuses[i] = S_UNUSED;
                continue;
            }
            if (stopped[i]) {
                statuses[i] = S_STOPPED;
                continue;
            }
            if (!isMemory(items.getStackInSlot(SLOT_MEMORY))) {
                statuses[i] = S_NO_MEMORY;
            } else if (!isKit(items.getStackInSlot(SLOT_KIT))) {
                statuses[i] = S_NO_KIT;
            } else if (energy.getEnergyStored() < PRINT_FE) {
                statuses[i] = S_NO_ENERGY;
            } else if (printing || !topClear(level)) {
                statuses[i] = S_BLOCKED;
            } else if (now % 10 == 0) {
                print(level, i);
                printing = true;
            }
        }
    }

    private boolean topClear(ServerLevel level) {
        AABB box = new AABB(worldPosition.above()).deflate(0.05);
        return level.noCollision(box) && level.getEntitiesOfClass(Entity.class, box, e -> !e.isSpectator()).isEmpty();
    }

    private void print(ServerLevel level, int unit) {
        if (!energy.consume(PRINT_FE)) {
            return;
        }
        CargoDrone d = CargoDrone.spawn(level, worldPosition, unit, Vec3.atBottomCenterOf(worldPosition.above()), true);
        if (d == null) {
            energy.setEnergy(energy.getEnergyStored() + PRINT_FE);
            return;
        }
        items.extractItem(SLOT_KIT, 1, false);
        drones[unit] = d.getUUID();
        statuses[unit] = S_PRINTING;
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, 1.9F);
        setChanged();
    }

    // ------------------------------------------------------------------ GUI

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
        return new DroneStationMenu(id, inventory, worldPosition, items, data);
    }

    /**
     * 壊されたら: 中身を落とす。待機中のドローンは片付けてキットを1つずつ返す。飛んでいる便は荷物を届けてから消える。
     */
    @Override
    public void onBroken(Level level) {
        for (int i = 0; i < items.getSlots(); i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        DroneNetwork net = DroneNetwork.get(server);
        var kit = BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id("drone_kit"));
        for (int i = 0; i < MAX_UNITS; i++) {
            DroneNetwork.Flight f = net.flightOf(worldPosition, i);
            if (f != null) {
                if (f.stage == DroneNetwork.RETURN && f.crate.isEmpty()) {
                    net.remove(f);
                } else {
                    f.station = null;                     // 届け終えたら消える
                    net.changed();
                }
                continue;
            }
            if (drones[i] != null && server.getEntity(drones[i]) instanceof CargoDrone d) {
                d.discard();
                Block.popResource(level, worldPosition, new ItemStack(kit));
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
            if (drones[i] != null) {
                tag.putUUID("drone" + i, drones[i]);
            }
            tag.putBoolean("stopped" + i, stopped[i]);
        }
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
            drones[i] = tag.hasUUID("drone" + i) ? tag.getUUID("drone" + i) : null;
            stopped[i] = tag.getBoolean("stopped" + i);
        }
    }
}
