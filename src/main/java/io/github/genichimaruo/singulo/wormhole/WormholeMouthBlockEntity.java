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
 * エネルギー・アイテム・液体を直結する。口はブロックの上に浮かんで見える。
 * <ul>
 *   <li>喉の大きさ: 3×3・5×5・7×7（大きさ1〜3、空の手で右クリックして目標を切り替える）。帯域は大きさで決まる</li>
 *   <li>維持: エキゾチック物質を使い続ける。3×3 で TICKS_PER_MATTER tick に1個、面積に比例して増え、
 *       向こう側が別のディメンションなら3倍</li>
 *   <li>切れると SHRINK_TICKS ごとに喉が1段縮み、0 になると口は崩壊して消える（アイテムは残らない）</li>
 *   <li>口は周りのエンティティをわずかに引き寄せる</li>
 * </ul>
 */
public class WormholeMouthBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener,
        AbstractMachineBlock.BreakListener {
    public static final int TICKS_PER_MATTER = 6000;
    public static final int SHRINK_TICKS = 600;
    public static final int GROW_TICKS = 100;
    public static final int PORT_RANGE = 8;
    public static final int MAX_SIZE = 3;
    /** 大きさごとの1 tick の帯域（0 は閉じている）。 */
    public static final int[] ENERGY_PER_TICK = {0, 64_000_000, 512_000_000, Integer.MAX_VALUE};
    public static final int[] ITEMS_PER_TICK = {0, 8, 32, 128};
    public static final int[] FLUID_PER_TICK = {0, 1_000, 8_000, 64_000};
    /** 口の中心（ブロックからの高さ）。 */
    public static final double MOUTH_HEIGHT = 1.5;

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
    private int starve;
    private int grow;
    private boolean registered;
    private boolean crossDimension;
    private long budgetTick = -1;
    private long energyLeft;
    private int itemsLeft;
    private int fluidLeft;

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

    // ------------------------------------------------------------------ 帯域（1 tick ごとに口ごとに数え直す）

    private void refreshBudget() {
        long now = level == null ? 0 : level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            energyLeft = ENERGY_PER_TICK[size];
            itemsLeft = ITEMS_PER_TICK[size];
            fluidLeft = FLUID_PER_TICK[size];
        }
    }

    public int energyBudget() {
        refreshBudget();
        return (int) Math.min(Integer.MAX_VALUE, energyLeft);
    }

    public void useEnergy(int amount) {
        refreshBudget();
        energyLeft -= amount;
    }

    public int itemBudget() {
        refreshBudget();
        return itemsLeft;
    }

    public void useItems(int amount) {
        refreshBudget();
        itemsLeft -= amount;
    }

    public int fluidBudget() {
        refreshBudget();
        return fluidLeft;
    }

    public void useFluid(int amount) {
        refreshBudget();
        fluidLeft -= amount;
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

    /** 1 tick に使う「3×3 換算の tick」。 */
    public double upkeepPerTick() {
        return area(size) / 9.0 * (crossDimension ? 3 : 1);
    }

    private void upkeep(ServerLevel level) {
        burn -= upkeepPerTick();
        if (burn <= 0) {
            if (!fuel.extractItem(0, 1, false).isEmpty()) {
                burn += TICKS_PER_MATTER;
            }
        }
        int before = size;
        if (burn <= 0) {
            burn = 0;
            grow = 0;
            if (++starve >= SHRINK_TICKS) {
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
        }
        if (size <= 0) {
            collapse(level);
        }
    }

    /** 喉が閉じて口が消える。 */
    public void collapse(ServerLevel level) {
        Vec3 c = mouthCenter();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 60, 0.5, 0.5, 0.5, 0.2);
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
        unregister(level);
        level.removeBlock(worldPosition, false);
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
        data = input.get(SinguloComponents.WORMHOLE.get());
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
        tag.putInt("starve", starve);
        tag.put("fuel", fuel.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("pair")) {
            data = new WormholeData(tag.getLong("pair"), tag.getLong("created"));
        }
        size = tag.contains("size") ? tag.getInt("size") : 1;
        if (tag.contains("target")) {
            target = tag.getInt("target");
        }
        burn = tag.getDouble("burn");
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
