package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 1つの注文。工程（レシピと回数）と、最後に納品口へ届ける数、進み具合、割り当てたアンドロイド、予約した在庫を持つ。
 */
public final class WorkOrder {
    /** 状態（画面の表示）。 */
    public static final int RUNNING = 0, WAIT_ITEMS = 1, WAIT_CAPACITY = 2, WAIT_SPACE = 3;

    public final int id;
    public final ItemStack target;
    public final long count;
    @Nullable
    public final UUID orderer;
    public final List<StepState> steps = new ArrayList<>();
    public long delivered;
    public long deliverInProgress;
    /**
     * この注文が使ってよい物（予約）: 受け渡し場所ごとの物と数。注文したときにそこにあった物と、この注文が作って戻した物だけ。
     * あとから増えた物（ほかの人が入れた物・ほかの注文の物）は使わない。
     */
    public final List<Holding> held = new ArrayList<>();
    /** 割り当てたアンドロイド（印刷し直されたアンドロイドも、続きを受け持つと加わる）。 */
    public final Set<UUID> workers = new HashSet<>();
    public int status = RUNNING;
    /** 素材が足りず待っているとき、次に試す時刻。 */
    public long retryAt;
    /** 納品先（作業指令台か、納品口）。 */
    @Nullable
    public BlockPos destination;
    /** できた物を、納品先ではなく素材置き場へ入れる（登録した注文の「在庫を保つ」）。 */
    public boolean toStock;

    public WorkOrder(int id, ItemStack target, long count, @Nullable UUID orderer) {
        this.id = id;
        this.target = target.copyWithCount(1);
        this.count = count;
        this.orderer = orderer;
    }

    /** 受け渡し場所 point に予約した物。 */
    public static final class Holding {
        public final BlockPos point;
        public final ItemStack item;
        public long count;

        Holding(BlockPos point, ItemStack item, long count) {
            this.point = point.immutable();
            this.item = item.copyWithCount(1);
            this.count = count;
        }
    }

    /** 予約を足す。 */
    public void hold(BlockPos point, ItemStack item, long count) {
        if (count <= 0 || item.isEmpty()) {
            return;
        }
        for (Holding h : held) {
            if (h.point.equals(point) && ItemStack.isSameItemSameComponents(h.item, item)) {
                h.count += count;
                return;
            }
        }
        held.add(new Holding(point, item, count));
    }

    /** 予約を使う（取り出した分）。 */
    public void release(BlockPos point, ItemStack item, long count) {
        for (Holding h : held) {
            if (h.point.equals(point) && ItemStack.isSameItemSameComponents(h.item, item)) {
                h.count = Math.max(0, h.count - count);
            }
        }
        held.removeIf(h -> h.count <= 0);
    }

    /** 受け渡し場所 point に予約した、この物の数（point が null なら全部の場所の合計）。 */
    public long held(@Nullable BlockPos point, ItemStack item) {
        long n = 0;
        for (Holding h : held) {
            if ((point == null || h.point.equals(point)) && ItemStack.isSameItemSameComponents(h.item, item)) {
                n += h.count;
            }
        }
        return n;
    }

    /** 1つの工程の進み具合。 */
    public static final class StepState {
        public final RecipeData recipe;
        public final BlockPos location;
        public final Block block;
        public final long total;
        public long done;
        public long inProgress;
        /** この工程の素材を作る、前の工程の番号。 */
        public final List<Integer> after = new ArrayList<>();

        StepState(RecipeData recipe, BlockPos location, Block block, long total) {
            this.recipe = recipe;
            this.location = location;
            this.block = block;
            this.total = total;
        }

        public long remaining() {
            return total - done - inProgress;
        }

        public boolean finished() {
            return done >= total;
        }
    }

    /** 計算した工程から注文を作る。 */
    public static WorkOrder create(int id, WorkPlanner.Plan plan, @Nullable UUID orderer) {
        WorkOrder o = new WorkOrder(id, plan.target, plan.count, orderer);
        for (WorkPlanner.Step s : plan.steps) {
            o.steps.add(new StepState(s.entry().recipe(), s.entry().location(),
                    s.entry().block() == null ? Blocks.AIR : s.entry().block(), s.crafts()));
        }
        o.link();
        return o;
    }

    /** 工程の前後関係（素材を作る工程が終わってから、その素材を使う工程を始める）。 */
    private void link() {
        for (int i = 0; i < steps.size(); i++) {
            StepState s = steps.get(i);
            s.after.clear();
            for (int j = 0; j < i; j++) {
                for (ItemCount in : s.recipe.inputs()) {
                    if (ItemStack.isSameItemSameComponents(in.item, steps.get(j).recipe.output())) {
                        s.after.add(j);
                        break;
                    }
                }
            }
        }
    }

    public boolean ready(StepState s) {
        for (int j : s.after) {
            if (!steps.get(j).finished()) {
                return false;
            }
        }
        return true;
    }

    public boolean stepsFinished() {
        for (StepState s : steps) {
            if (!s.finished()) {
                return false;
            }
        }
        return true;
    }

    public boolean finished() {
        return stepsFinished() && delivered >= count;
    }

    /** 素材置き場に予約している物の数（ほかの注文の計算から引く）。 */
    public long outstanding(ItemStack item) {
        return held(null, item);
    }

    /** 進み具合（0〜1000）: 工程の回数と、届けた数で数える。 */
    public int progress() {
        long all = count, done = Math.min(delivered, count);
        for (StepState s : steps) {
            all += s.total;
            done += Math.min(s.done, s.total);
        }
        return all <= 0 ? 1000 : (int) (1000 * done / all);
    }

    // ------------------------------------------------------------------ 保存

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.put("target", target.save(registries));
        t.putLong("count", count);
        if (orderer != null) {
            t.putUUID("orderer", orderer);
        }
        ListTag list = new ListTag();
        for (StepState s : steps) {
            CompoundTag st = new CompoundTag();
            st.put("recipe", s.recipe.save(registries));
            st.put("location", NbtUtils.writeBlockPos(s.location));
            st.putString("block", BuiltInRegistries.BLOCK.getKey(s.block).toString());
            st.putLong("total", s.total);
            st.putLong("done", s.done);
            list.add(st);
        }
        t.put("steps", list);
        t.putLong("delivered", delivered);
        if (destination != null) {
            t.put("destination", NbtUtils.writeBlockPos(destination));
        }
        t.putBoolean("to_stock", toStock);
        ListTag hs = new ListTag();
        for (Holding h : held) {
            CompoundTag ht = new CompoundTag();
            ht.put("point", NbtUtils.writeBlockPos(h.point));
            ht.put("item", h.item.save(registries));
            ht.putLong("count", h.count);
            hs.add(ht);
        }
        t.put("held", hs);
        ListTag ws = new ListTag();
        for (UUID w : workers) {
            ws.add(NbtUtils.createUUID(w));
        }
        t.put("workers", ws);
        return t;
    }

    @Nullable
    public static WorkOrder load(CompoundTag t, HolderLookup.Provider registries) {
        ItemStack target = ItemStack.parseOptional(registries, t.getCompound("target"));
        if (target.isEmpty()) {
            return null;
        }
        WorkOrder o = new WorkOrder(t.getInt("id"), target, t.getLong("count"), t.hasUUID("orderer") ? t.getUUID("orderer") : null);
        for (Tag tag : t.getList("steps", Tag.TAG_COMPOUND)) {
            CompoundTag st = (CompoundTag) tag;
            RecipeData r = RecipeData.load(st.getCompound("recipe"), registries);
            if (r == null) {
                return null;
            }
            ResourceLocation bid = ResourceLocation.tryParse(st.getString("block"));
            StepState s = new StepState(r, NbtUtils.readBlockPos(st, "location").orElse(BlockPos.ZERO),
                    bid == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(bid), st.getLong("total"));
            s.done = st.getLong("done");
            o.steps.add(s);
        }
        o.delivered = t.getLong("delivered");
        o.destination = NbtUtils.readBlockPos(t, "destination").orElse(null);
        o.toStock = t.getBoolean("to_stock");
        for (Tag tag : t.getList("held", Tag.TAG_COMPOUND)) {
            CompoundTag ht = (CompoundTag) tag;
            ItemStack item = ItemStack.parseOptional(registries, ht.getCompound("item"));
            NbtUtils.readBlockPos(ht, "point").ifPresent(p -> o.hold(p, item, ht.getLong("count")));
        }
        for (Tag w : t.getList("workers", Tag.TAG_INT_ARRAY)) {
            o.workers.add(NbtUtils.loadUUID(w));
        }
        o.link();
        return o;
    }

    static ListTag saveCounts(List<ItemCount> list, HolderLookup.Provider registries) {
        ListTag out = new ListTag();
        for (ItemCount c : list) {
            out.add(c.save(registries));
        }
        return out;
    }

    static void loadCounts(ListTag tags, List<ItemCount> out, HolderLookup.Provider registries) {
        for (Tag t : tags) {
            ItemCount c = ItemCount.load((CompoundTag) t, registries);
            if (!c.item.isEmpty()) {
                out.add(c);
            }
        }
    }
}
