package io.github.genichimaruo.singulo.wormhole;

import io.github.genichimaruo.singulo.item.ExoticCharge;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * ワームホールの口（段階5）。同じ対の口どうしがつながり、近く（PORT_RANGE 以内）のワームホール・ポートを通して
 * エネルギー・アイテム・液体を直結する。喉（小さな黒い球）は枠だけの筐体の中に浮かんで見える。
 * <ul>
 *   <li>喉の大きさ: 3×3・5×5・7×7（大きさ1〜3、画面で目標を切り替える）。1 tick に通せるアイテム・エネルギー・液体と、
 *       働けるポートの数（PORTS_PER_SIZE、口に近い順）は大きさで決まる。7×7 では通せる量の上限がなくなる
 *       （エネルギーは 1 tick に int を超えて送ってもよい）</li>
 *   <li>維持: エキゾチック物質を使い続ける。3×3 で TICKS_PER_MATTER tick（15分）に1個、喉の一辺に比例して増え
 *       （5×5 で約9分、7×7 で約6.4分に1個）、向こう側が別のディメンションなら CROSS_DIMENSION_FACTOR 倍。初めて置いたときだけ、GRACE_TICKS（5分）は燃料なしで開いている</li>
 *   <li>切れると SHRINK_TICKS ごとに喉が1段縮み、0 になると閉じて休止する（筐体は残る）。
 *       エキゾチック物質を入れ直すと、また開く</li>
 *   <li>口は周りのエンティティをわずかに引き寄せる</li>
 * </ul>
 */
public class WormholeMouthBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener,
        AbstractMachineBlock.BreakListener {
    public static final int TICKS_PER_MATTER = 18_000;
    /** 向こう側が別のディメンションのときの維持費の倍率。 */
    public static final int CROSS_DIMENSION_FACTOR = 2;
    public static final int SHRINK_TICKS = 600;
    public static final int GROW_TICKS = 100;
    public static final int PORT_RANGE = 8;
    public static final int MAX_SIZE = 3;
    /** 初めて置いたときに、燃料なしで開いていられる時間（5分）。 */
    public static final int GRACE_TICKS = 6000;
    /** 大きさごとの1 tick の帯域（0 は閉じている）。UNLIMITED は上限なし（いちばん大きい喉）。 */
    public static final long UNLIMITED = Long.MAX_VALUE;
    public static final long[] ITEMS_PER_TICK = {0, 8, 32, UNLIMITED};
    public static final long[] ENERGY_PER_TICK = {0, 64_000_000L, 512_000_000L, UNLIMITED};
    public static final long[] FLUID_PER_TICK = {0, 1_000, 8_000, UNLIMITED};
    /** 大きさごとの、働けるポートの数（口に近い順）。 */
    public static final int[] PORTS_PER_SIZE = {0, 8, 32, 128};
    /** 口の中心（ブロックの底からの高さ）。喉は筐体の中に収まる。 */
    public static final double MOUTH_HEIGHT = 0.5;

    private static final Map<Level, Set<WormholeMouthBlockEntity>> LOADED = new WeakHashMap<>();

    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return ExoticCharge.isExoticMatter(stack);
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    @Nullable
    private WormholeData data;
    private int size = 1;
    private int target = 1;
    private double burn;
    /** 初めて置いたときの猶予の残り（tick）。 */
    private int grace;
    private int starve;
    private int grow;
    private boolean registered;
    private boolean crossDimension;
    private long budgetTick = -1;
    private long itemsLeft;
    private long energyLeft;
    private long fluidLeft;
    /** この口を使うポート（口に近い順）と、それを数えた tick。 */
    private List<WormholePortBlockEntity> ranked = List.of();
    private long rankTick = -1;

    public WormholeMouthBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.WORMHOLE_MOUTH.get(), pos, state);
    }

    @Nullable
    public WormholeData data() {
        return data;
    }

    public void setData(@Nullable WormholeData data) {
        this.data = data;
        setChanged();
    }

    public int size() {
        return size;
    }

    public int targetSize() {
        return target;
    }

    public void setTargetSize(int target) {
        this.target = Math.max(1, Math.min(MAX_SIZE, target));
        setChanged();
    }

    public ItemStackHandler fuel() {
        return fuel;
    }

    public IItemHandler automationItems() {
        return fuel;
    }

    /** 喉の面積（3×3 で 9）。 */
    public static int area(int size) {
        int side = 2 * size + 1;
        return side * side;
    }

    public Vec3 mouthCenter() {
        return Vec3.atCenterOf(worldPosition).add(0, MOUTH_HEIGHT - 0.5, 0);
    }

    // ------------------------------------------------------------------ 相手の口

    @Nullable
    public GlobalPos partnerPos() {
        if (data == null || !(level instanceof ServerLevel server)) {
            return null;
        }
        return WormholeNetwork.get(server.getServer()).partner(data.pair(), GlobalPos.of(level.dimension(), worldPosition));
    }

    /** 読み込まれている相手の口。 */
    @Nullable
    public WormholeMouthBlockEntity partner() {
        GlobalPos p = partnerPos();
        if (p == null || !(level instanceof ServerLevel server)) {
            return null;
        }
        ServerLevel other = server.getServer().getLevel(p.dimension());
        if (other == null || !other.isLoaded(p.pos())) {
            return null;
        }
        return other.getBlockEntity(p.pos()) instanceof WormholeMouthBlockEntity m && m.size > 0 ? m : null;
    }

    /** 開いていて相手とつながっているか。 */
    public boolean open() {
        return size > 0 && partner() != null;
    }

    /** この口の近くにある、読み込まれた口（ポートが使う）。 */
    @Nullable
    public static WormholeMouthBlockEntity near(Level level, BlockPos pos) {
        Set<WormholeMouthBlockEntity> set = LOADED.get(level);
        if (set == null) {
            return null;
        }
        WormholeMouthBlockEntity best = null;
        double bestSq = PORT_RANGE * PORT_RANGE + 1e-3;
        for (WormholeMouthBlockEntity m : set) {
            double d = m.worldPosition.distSqr(pos);
            if (!m.isRemoved() && m.size > 0 && d <= bestSq) {
                bestSq = d;
                best = m;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ ポートの順位（口に近い順に、上限の数まで働く）

    public int portLimit() {
        return PORTS_PER_SIZE[Math.max(0, Math.min(MAX_SIZE, size))];
    }

    /** この口を使うポート（いちばん近い口がこの口のもの）を、口に近い順に並べたもの。1 tick に1回数え直す。 */
    public List<WormholePortBlockEntity> rankedPorts() {
        long now = level == null ? 0 : level.getGameTime();
        if (now != rankTick && level != null) {
            rankTick = now;
            List<WormholePortBlockEntity> list = new ArrayList<>();
            for (WormholePortBlockEntity port : WormholePortBlockEntity.loaded(level)) {
                if (!port.isRemoved() && port.getBlockPos().distSqr(worldPosition) <= PORT_RANGE * PORT_RANGE
                        && near(level, port.getBlockPos()) == this) {
                    list.add(port);
                }
            }
            list.sort(java.util.Comparator.comparingDouble((WormholePortBlockEntity p) -> p.getBlockPos().distSqr(worldPosition))
                    .thenComparingLong(p -> p.getBlockPos().asLong()));
            ranked = list;
        }
        return ranked;
    }

    /** ポートの順位（1 から。この口を使っていなければ 0）。 */
    public int rankOf(WormholePortBlockEntity port) {
        return rankedPorts().indexOf(port) + 1;
    }

    /** ポートが働けるか（口に近い順で、上限の数に入っている）。 */
    public boolean portActive(WormholePortBlockEntity port) {
        int rank = rankOf(port);
        return rank > 0 && rank <= portLimit();
    }

    /** 働いているポートの数。 */
    public int activePorts() {
        return Math.min(rankedPorts().size(), portLimit());
    }

    // ------------------------------------------------------------------ 帯域（1 tick ごとに口ごとに数え直す）

    private void refreshBudget() {
        long now = level == null ? 0 : level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            int s = Math.max(0, Math.min(MAX_SIZE, size));
            itemsLeft = ITEMS_PER_TICK[s];
            energyLeft = ENERGY_PER_TICK[s];
            fluidLeft = FLUID_PER_TICK[s];
        }
    }

    private static long spend(long left, long amount) {
        return left == UNLIMITED ? UNLIMITED : Math.max(0, left - amount);
    }

    public int itemBudget() {
        refreshBudget();
        return (int) Math.min(Integer.MAX_VALUE, itemsLeft);
    }

    public void useItems(int amount) {
        refreshBudget();
        itemsLeft = spend(itemsLeft, amount);
    }

    public long energyBudget() {
        refreshBudget();
        return energyLeft;
    }

    public void useEnergy(long amount) {
        refreshBudget();
        energyLeft = spend(energyLeft, amount);
    }

    public int fluidBudget() {
        refreshBudget();
        return (int) Math.min(Integer.MAX_VALUE, fluidLeft);
    }

    public void useFluid(int amount) {
        refreshBudget();
        fluidLeft = spend(fluidLeft, amount);
    }

    /** 初めて置いたときの猶予の残り（tick）。 */
    public int grace() {
        return grace;
    }

    // ------------------------------------------------------------------ 毎tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, WormholeMouthBlockEntity be) {
        be.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (data == null) {
            return;
        }
        if (!registered) {
            WormholeNetwork.get(level.getServer()).register(data.pair(), GlobalPos.of(level.dimension(), pos));
            registered = true;
        }
        LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new IdentityHashMap<>())).add(this);
        if (level.getGameTime() % 20 == 0) {
            GlobalPos p = partnerPos();
            crossDimension = p != null && !p.dimension().equals(level.dimension());
        }
        upkeep(level);
        if (isRemoved()) {
            return;
        }
        if (size > 0 && level.getGameTime() % 5 == 0) {
            pull(level);
        }
        boolean lit = size > 0 && partner() != null;
        if (lit && level.getGameTime() % 20 == 0) {
            io.github.genichimaruo.singulo.registry.SinguloTriggers.milestoneNear(level, pos, 16, "wormhole_open");
        }
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** 1 tick に使う「3×3 換算の tick」。喉の一辺（3・5・7）に比例する。 */
    public double upkeepPerTick() {
        return (2 * size + 1) / 3.0 * (crossDimension ? CROSS_DIMENSION_FACTOR : 1);
    }

    private void upkeep(ServerLevel level) {
        boolean fed;
        if (grace > 0) {
            // 初めて置いたときの猶予（燃料を使わない）
            grace--;
            fed = true;
            if (grace % 20 == 0) {
                setChanged();
            }
        } else {
            burn -= upkeepPerTick();
            if (burn <= 0 && !fuel.extractItem(0, 1, false).isEmpty()) {
                burn += TICKS_PER_MATTER;
            }
            fed = burn > 0;
        }
        int before = size;
        if (!fed) {
            burn = 0;
            grow = 0;
            if (size > 0 && ++starve >= SHRINK_TICKS) {
                starve = 0;
                size--;
            }
        } else {
            starve = 0;
            if (size < target && ++grow >= GROW_TICKS) {
                grow = 0;
                size++;
            } else if (size > target) {
                size = target;
            }
        }
        if (size != before) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            if (size == 0) {
                close(level);
            }
        }
    }

    /** 喉が閉じて休止する（筐体は残り、エキゾチック物質を入れればまた開く）。 */
    public void close(ServerLevel level) {
        Vec3 c = mouthCenter();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 30, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
    }

    private void pull(ServerLevel level) {
        Vec3 c = mouthCenter();
        double radius = 2 + size;
        for (Entity e : level.getEntities((Entity) null, new AABB(c, c).inflate(radius),
                e -> !e.isSpectator() && !(e instanceof Player p && p.isCreative()))) {
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double d = to.length();
            if (d > 0.3 && d <= radius) {
                e.setDeltaMovement(e.getDeltaMovement().add(to.normalize().scale(0.03)));
                e.hurtMarked = true;
            }
        }
    }

    private void unregister(Level level) {
        if (data != null && level instanceof ServerLevel server) {
            WormholeNetwork.get(server.getServer()).unregister(data.pair(), GlobalPos.of(level.dimension(), worldPosition));
        }
        registered = false;
        Set<WormholeMouthBlockEntity> set = LOADED.get(level);
        if (set != null) {
            set.remove(this);
        }
    }

    @Override
    public void onBroken(Level level) {
        unregister(level);
        Block.popResource(level, worldPosition, fuel.getStackInSlot(0));
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            Set<WormholeMouthBlockEntity> set = LOADED.get(level);
            if (set != null) {
                set.remove(this);
            }
        }
        super.setRemoved();
    }

    // ------------------------------------------------------------------ プレイヤー

    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (ExoticCharge.isExoticMatter(stack)) {
            ItemStack rest = fuel.insertItem(0, stack.copy(), false);
            stack.setCount(rest.getCount());
            return true;
        }
        return false;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        io.github.genichimaruo.singulo.machine.DeviceMenu.open(player, this, io.github.genichimaruo.singulo.machine.DeviceMenu.Kind.WORMHOLE_MOUTH, fuel, i -> {
            GlobalPos p = partnerPos();
            return switch (i) {
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.SIZE -> size;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.TARGET -> target;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.PARTNER -> p == null ? 0 : partner() == null ? 1 : 2;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.PX -> p == null ? 0 : p.pos().getX();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.PY -> p == null ? 0 : p.pos().getY();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.PZ -> p == null ? 0 : p.pos().getZ();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.CROSS -> crossDimension ? 1 : 0;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.STARVE -> starve;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.TICKS_PER_MATTER -> upkeepPerTick() <= 0 ? 0 : (int) (TICKS_PER_MATTER / upkeepPerTick());
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.GRACE -> grace;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.PORTS -> rankedPorts().size();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.PORT_LIMIT -> portLimit();
                default -> 0;
            };
        }, (pl, id) -> {
            if (id == io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.BUTTON_SMALLER) {
                setTargetSize(Math.max(1, target - 1));
                return true;
            }
            if (id == io.github.genichimaruo.singulo.machine.DeviceMenu.Mouth.BUTTON_BIGGER) {
                setTargetSize(Math.min(MAX_SIZE, target + 1));
                return true;
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ 保存・同期

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (data != null) {
            components.set(SinguloComponents.WORMHOLE.get(), data);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        WormholeData placed = input.get(SinguloComponents.WORMHOLE.get());
        if (placed != null && !placed.placed()) {
            // 初めて置いた: 燃料なしでも5分は開いている
            grace = GRACE_TICKS;
            size = 1;
        } else if (placed != null) {
            // 置き直した口は閉じた状態から（エキゾチック物質を入れると開く）
            size = 0;
        }
        data = placed == null ? null : placed.asPlaced();
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("pair");
        tag.remove("created");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("size", size);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (data != null) {
            tag.putLong("pair", data.pair());
            tag.putLong("created", data.created());
        }
        tag.putInt("size", size);
        tag.putInt("target", target);
        tag.putDouble("burn", burn);
        tag.putInt("grace", grace);
        tag.putInt("starve", starve);
        tag.put("fuel", fuel.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("pair")) {
            data = new WormholeData(tag.getLong("pair"), tag.getLong("created"), true);
        }
        size = tag.contains("size") ? tag.getInt("size") : 1;
        if (tag.contains("target")) {
            target = tag.getInt("target");
        }
        burn = tag.getDouble("burn");
        grace = tag.getInt("grace");
        starve = tag.getInt("starve");
        if (tag.contains("fuel")) {
            fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        }
    }

    /** テスト用: 読み込まれている口の一覧。 */
    static List<WormholeMouthBlockEntity> loaded(Level level) {
        Set<WormholeMouthBlockEntity> set = LOADED.get(level);
        return set == null ? List.of() : new ArrayList<>(set);
    }
}
