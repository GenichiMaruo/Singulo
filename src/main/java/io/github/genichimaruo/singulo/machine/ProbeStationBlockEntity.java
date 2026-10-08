package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity;
import io.github.genichimaruo.singulo.ruin.RuinDiscovery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;

/**
 * 自動探査機ステーション（ティア4）。発見済みの遺構（段階の N−2 ルールで飛べるもの）へ探査機を送り、回収物を持ち帰る。
 * 1回の出撃は MISSION_TICKS（ゲーム内半日）。持ち帰る量は手動遠征の probeYieldMultiplier 倍（既定0.5）で、
 * 同じ遺構には中身が再生するまで（7日）行かない。同ティアの触媒を消費する。
 * 遺構の登録は、発見したプレイヤーがこのステーションを開いたときに行う。持ち帰った物はホッパーなどで取り出す。
 */
public class ProbeStationBlockEntity extends CatalystDeviceBlockEntity {
    public static final int MACHINE_TIER = 4;
    public static final int MISSION_TICKS = 12_000;
    public static final int FE_PER_TICK = 2_000;

    /** 登録した遺構と、次に行ける時刻。 */
    private record Target(String ruin, BlockPos pos, long nextVisit) {}

    private final ItemStackHandler output = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    private final IItemHandler automation = new CombinedInvWrapper(slot, new RangedWrapper(output, 0, 27) {
        @Override
        public ItemStack insertItem(int s, ItemStack stack, boolean simulate) {
            return stack;
        }
    });
    private final List<Target> targets = new ArrayList<>();
    private int progress;

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
        for (RuinDiscovery.Discovered d : RuinDiscovery.discovered(player)) {
            if (!RuinDiscovery.automatable(d.ruin(), MACHINE_TIER)) {
                continue;
            }
            boolean known = targets.stream().anyMatch(t -> t.pos().equals(d.pos()));
            if (!known) {
                targets.add(new Target(d.ruin(), d.pos(), 0));
                added++;
            }
        }
        if (added > 0) {
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
        return MACHINE_TIER;
    }

    @Override
    protected int baseUsage(ItemStack catalyst) {
        return FE_PER_TICK;
    }

    @Override
    protected Status readiness(ServerLevel level) {
        return targets.isEmpty() ? Status.NO_TARGET : null;
    }

    @Override
    protected void apply(ServerLevel level, CatalystHelper.Effect effect, boolean active) {
        if (!active) {
            return;
        }
        if (progress == 0) {
            io.github.genichimaruo.singulo.registry.SinguloSounds.playAt(level, worldPosition, "probe_station_launch", 1.2F, 1.0F);
        }
        progress += Math.max(1, (int) Math.round(effect.speed()));
        if (progress >= MISSION_TICKS) {
            progress = 0;
            completeMission(level, level.getGameTime());
            io.github.genichimaruo.singulo.registry.SinguloSounds.playAt(level, worldPosition, "probe_station_return", 1.2F, 1.0F);
        }
    }

    /** 再生の済んだ遺構を回り、持ち帰った物を出力に入れる。入り切らない分は捨てる。 */
    public void completeMission(ServerLevel level, long now) {
        double yield = ServerConfig.SPEC.isLoaded() ? ServerConfig.PROBE_YIELD_MULTIPLIER.get() : 0.5;
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            if (now < t.nextVisit()) {
                continue;
            }
            // 重ならないアイテム（観測ログなど）は1個ずつのスタックで来るので、種類ごとに合計してから倍率を掛ける
            java.util.Map<net.minecraft.world.item.Item, ItemStack> totals = new java.util.LinkedHashMap<>();
            for (ItemStack stack : RuinCacheBlockEntity.rollLoot(level, t.ruin(), t.pos())) {
                totals.merge(stack.getItem(), stack.copy(), (a, b) -> a.copyWithCount(a.getCount() + b.getCount()));
            }
            for (ItemStack total : totals.values()) {
                int count = (int) Math.floor(total.getCount() * yield + level.random.nextDouble());
                for (int n = 0; n < count; ) {
                    int chunk = Math.min(count - n, total.getMaxStackSize());
                    ItemStack rest = total.copyWithCount(chunk);
                    for (int s = 0; s < output.getSlots() && !rest.isEmpty(); s++) {
                        rest = output.insertItem(s, rest, false);
                    }
                    n += chunk;
                }
            }
            long regen = RuinCacheBlockEntity.regenTicks(t.ruin());
            targets.set(i, new Target(t.ruin(), t.pos(), regen > 0 ? now + regen : Long.MAX_VALUE));
        }
        setChanged();
    }

    @Override
    protected int displayValue() {
        return (MISSION_TICKS - progress) / 20;
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
        for (int i = 0; i < output.getSlots(); i++) {
            Block.popResource(level, worldPosition, output.getStackInSlot(i));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("output", output.serializeNBT(registries));
        tag.putInt("progress", progress);
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        output.deserializeNBT(registries, tag.getCompound("output"));
        progress = tag.getInt("progress");
        targets.clear();
        for (Tag t : tag.getList("targets", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            NbtUtils.readBlockPos(c, "pos").ifPresent(p -> targets.add(new Target(c.getString("ruin"), p, c.getLong("next"))));
        }
    }
}
