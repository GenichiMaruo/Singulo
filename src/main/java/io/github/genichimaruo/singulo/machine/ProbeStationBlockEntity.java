package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity;
import io.github.genichimaruo.singulo.ruin.RuinDiscovery;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.RangedWrapper;

/**
 * 自動探査機ステーション（ティア4）。発見済みの遺構（段階の N−2 ルールで飛べるもの）へ探査機を送り、回収物を持ち帰る。
 * 1回の出撃は MISSION_TICKS（5分）で1か所。持ち帰る量は手動遠征の probeYieldMultiplier 倍（既定0.5）で、
 * 同じ遺構には中身が再生するまで（7日）行かない。同ティアの触媒を消費する。
 * 遺構の登録は、発見したプレイヤーがこのステーションを開いたときに行う。持ち帰った物はホッパーなどで取り出す。
 */
public class ProbeStationBlockEntity extends CatalystDeviceBlockEntity {
    public static final int MACHINE_TIER = 4;
    public static final int MISSION_TICKS = 6_000;
    public static final int FE_PER_TICK = 2_000;

    /** 登録した遺構と、次に行ける時刻。 */
    private record Target(String ruin, BlockPos pos, long nextVisit) {}

    private final ItemStackHandler output = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int s) {
            flushNeeded = true;
            setChanged();
        }
    };
    private final IItemHandler automation = new CombinedInvWrapper(slot, new RangedWrapper(output, 0, 27) {
        @Override
        public ItemStack insertItem(int s, ItemStack stack, boolean simulate) {
            return stack;
        }
    }, spentOut());
    private final List<Target> targets = new ArrayList<>();
    private double progress;
    // Only one destination's loot may be in flight. Backpressure bounds the queue independently of target count.
    private final ArrayDeque<ItemStack> pending = new ArrayDeque<>();
    private boolean flushNeeded = true;
    private boolean outputFull;
    private int nextTarget = -1;
    private int cachedTier = -1;
    private int cachedOffset = -1;

    public ProbeStationBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.PROBE_STATION.get(), pos, state, 100_000, 10_000, 0);
    }

    public ItemStackHandler output() {
        return output;
    }

    public int targets() {
        return targets.size();
    }

    /** プレイヤーが発見した遺構のうち、このステーションが飛べるものを登録する。登録した数を返す。 */
    public int registerFrom(ServerPlayer player) {
        int added = 0;
        var known = new HashSet<BlockPos>();
        for (Target target : targets) known.add(target.pos());
        for (RuinDiscovery.Discovered d : RuinDiscovery.discovered(player)) {
            if (!RuinDiscovery.automatable(d.ruin(), MACHINE_TIER + 1)) {
                continue;
            }
            if (known.add(d.pos())) {
                targets.add(new Target(d.ruin(), d.pos().immutable(), 0));
                added++;
            }
        }
        if (added > 0) {
            cachedTier = -1;
            setChanged();
        }
        return added;
    }

    @Override
    public Kind kind() {
        return Kind.PROBE_STATION;
    }

    @Override
    protected int machineTier() {
        return CatalystHelper.tierOf(catalyst()) >= 5 ? 5 : MACHINE_TIER;
    }

    @Override
    protected int baseUsage(ItemStack catalyst) {
        return FE_PER_TICK;
    }

    @Override
    protected Status readiness(ServerLevel level) {
        flushPending();
        if (!pending.isEmpty() || outputFull) return Status.OUTPUT_FULL;
        int tier = machineTier();
        if (cachedTier != tier || cachedOffset != automationOffset()) selectNext(tier);
        if (targets.isEmpty()) return Status.NO_TARGET;
        if (nextTarget < 0) return Status.NEEDS_UPGRADE;
        return level.getGameTime() < targets.get(nextTarget).nextVisit() ? Status.WAITING : null;
    }

    @Override
    protected void apply(ServerLevel level, CatalystHelper.Effect effect, boolean active) {
        if (!active) {
            return;
        }
        if (progress == 0) {
            io.github.genichimaruo.singulo.registry.SinguloSounds.playAt(level, worldPosition, "probe_station_launch", 1.2F, 1.0F);
        }
        progress += effect.speed();
        if (progress >= MISSION_TICKS) {
            progress = 0;
            completeMission(level, level.getGameTime(), cachedTier);
            io.github.genichimaruo.singulo.registry.SinguloSounds.playAt(level, worldPosition, "probe_station_return", 1.2F, 1.0F);
        }
    }

    /** Complete at most one eligible visit; keep all overflow until automation makes room. */
    public void completeMission(ServerLevel level, long now) {
        completeMission(level, now, machineTier());
    }

    private void completeMission(ServerLevel level, long now, int tier) {
        flushPending();
        if (!pending.isEmpty()) return;
        if (cachedTier != tier || cachedOffset != automationOffset()) selectNext(tier);
        if (nextTarget < 0) return;
        Target target = targets.get(nextTarget);
        if (now < target.nextVisit()) return;
        double yield = ServerConfig.SPEC.isLoaded() ? ServerConfig.PROBE_YIELD_MULTIPLIER.get() : 0.5;
        var totals = new ArrayList<ItemStack>();
        for (ItemStack stack : RuinCacheBlockEntity.rollLoot(level, target.ruin(), target.pos())) {
            ItemStack same = totals.stream().filter(s -> ItemStack.isSameItemSameTags(s, stack)).findFirst().orElse(null);
            if (same == null) totals.add(stack.copy()); else same.grow(stack.getCount());
        }
        for (ItemStack total : totals) {
            int count = (int) Math.floor(total.getCount() * yield + level.random.nextDouble());
            while (count > 0) {
                int chunk = Math.min(count, total.getMaxStackSize());
                pending.add(total.copyWithCount(chunk));
                count -= chunk;
            }
        }
        long regen = RuinCacheBlockEntity.regenTicks(target.ruin());
        targets.set(nextTarget, new Target(target.ruin(), target.pos(), regen > 0 ? now + regen : Long.MAX_VALUE));
        selectNext(tier);
        flushNeeded = true;
        flushPending();
        setChanged();
    }

    private static int automationOffset() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.PROBE_AUTOMATION_OFFSET.get() : 2;
    }

    private void selectNext(int tier) {
        nextTarget = -1;
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            if (!RuinDiscovery.automatable(target.ruin(), tier)) continue;
            if (nextTarget < 0 || target.nextVisit() < targets.get(nextTarget).nextVisit()) nextTarget = i;
        }
        cachedTier = tier;
        cachedOffset = automationOffset();
    }

    private void flushPending() {
        if (!flushNeeded) return;
        while (!pending.isEmpty()) {
            ItemStack rest = pending.peek();
            for (int slot = 0; slot < output.getSlots() && !rest.isEmpty(); slot++)
                rest = output.insertItem(slot, rest, false);
            if (!rest.isEmpty()) {
                pending.remove();
                pending.addFirst(rest);
                break;
            }
            pending.remove();
        }
        outputFull = true;
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack stack = output.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), output.getSlotLimit(slot))) {
                outputFull = false;
                break;
            }
        }
        flushNeeded = false;
    }

    @Override
    protected int displayValue() {
        return (int) Math.ceil(Math.max(0, (MISSION_TICKS - progress) / 20));
    }

    @Override
    protected int extraValue() {
        return targets.size();
    }

    @Override
    public IItemHandler automationItems() {
        return automation;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        registerFrom(player);
        super.openMenu(player);
    }

    @Override
    public void onBroken(Level level) {
        super.onBroken(level);
        for (ItemStack stack : pending) Block.popResource(level, worldPosition, stack);
        pending.clear();
        for (int i = 0; i < output.getSlots(); i++) {
            Block.popResource(level, worldPosition, output.getStackInSlot(i));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("output", output.serializeNBT());
        tag.putDouble("progress", progress);
        ListTag cargo = new ListTag();
        for (ItemStack stack : pending) cargo.add(stack.save(new CompoundTag()));
        tag.put("pending", cargo);
        ListTag list = new ListTag();
        for (Target t : targets) {
            CompoundTag c = new CompoundTag();
            c.putString("ruin", t.ruin());
            c.put("pos", NbtUtils.writeBlockPos(t.pos()));
            c.putLong("next", t.nextVisit());
            list.add(c);
        }
        tag.put("targets", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        output.deserializeNBT(tag.getCompound("output"));
        progress = net.minecraft.util.Mth.clamp(tag.getDouble("progress"), 0, MISSION_TICKS - 1);
        pending.clear();
        for (Tag entry : tag.getList("pending", Tag.TAG_COMPOUND))
            { ItemStack stack = ItemStack.of((CompoundTag) entry); if (!stack.isEmpty()) pending.add(stack); }
        flushNeeded = true;
        cachedTier = -1;
        targets.clear();
        for (Tag t : tag.getList("targets", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            io.github.genichimaruo.singulo.compat.Legacy.readBlockPos(c, "pos").ifPresent(p -> targets.add(new Target(c.getString("ruin"), p, c.getLong("next"))));
        }
    }
}
