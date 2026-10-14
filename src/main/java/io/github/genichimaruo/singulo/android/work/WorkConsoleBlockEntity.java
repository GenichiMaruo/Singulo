package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.item.ShieldPermitItem;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * アンドロイド作業指令台。作業位置カードでつないだ資材口・納品口・レシピ棚をまとめ、注文を受けて、アンドロイドに作業を割り当てる。
 * <ul>
 * <li>注文できる物: レシピ棚に並べたレシピの完成品と、資材口のストレージにある物（工程なしで納品口へ運ぶだけ）。</li>
 * <li>注文のときに、在庫だけで作れるかを計算する（足りなければ注文できない）。使う在庫は予約し、ほかの注文には使わせない。</li>
 * <li>作業は工程ごとに、資材口から素材を取る → 作業場所で作る → できた物を資材口へ戻す、の形で渡す。
 * 最後に、完成品を資材口から納品口へ届ける。</li>
 * <li>許可証が入っていれば、持ち主と登録した人だけが画面を開ける（つなぐ・注文する）。</li>
 * <li>指令台そのものも資材口を兼ねる（隣のストレージも素材置き場）。レシピカードへの記録も、指令台の画面でできる。</li>
 * </ul>
 */
public class WorkConsoleBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener, AbstractMachineBlock.BreakListener,
        MenuProvider {
    /** つなげるブロックと、注文に使えるアンドロイドの、指令台からの距離。 */
    public static final double LINK_RANGE = 64;
    public static final double WORKER_RANGE = 64;
    /** 作業の知らせ（毎秒）がこれだけ途切れたら、作業を取り上げて、ほかのアンドロイドに回す。 */
    static final int LEASE_TIMEOUT = 300;
    /** 素材が足りなかったとき、次に試すまで。 */
    static final int RETRY_DELAY = 100;
    /** 1回の注文の数の上限。 */
    public static final int MAX_COUNT = 4096;

    public enum LinkKind { SUPPLY, DELIVERY, RACK }

    @Nullable
    private UUID owner;
    private final ItemStackHandler permit = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof ShieldPermitItem;
        }
    };
    /**
     * レシピの記録: 0〜8 は見本の欄（物は減らない）、9 は作る物（作業台モードは表示だけ）、10 は記録したカード、
     * 11 は未記録のカードのストック（1スタック）、12〜13 は加工モードの、ほかに出てくる物（副産物）、
     * 14〜15 は手順モードの作業位置カード（場所と、採掘の範囲のもう1点）、16 は手順モードの物（見本）。
     */
    public static final int GRID = 9, RESULT = 9, CARD = 10, STOCK = 11, EXTRA = 12, EXTRAS = 2, LOC_A = 14, LOC_B = 15, PITEM = 16,
            ENCODER_SLOTS = 17;
    public static final int MODE_CRAFTING = 0, MODE_PROCESSING = 1, MODE_PROCEDURE = 2, MODES = 3;
    /** 記録のモードと、手順モードで次に足す手順の種類。 */
    private int mode;
    private int stepKind;
    /** 手順モードで並べた手順。 */
    private final List<ProcedureStep> procSteps = new ArrayList<>();
    private final net.minecraft.world.inventory.ContainerData modeData = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? mode : stepKind;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                mode = value;
            } else {
                stepKind = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };
    private final net.minecraft.world.SimpleContainer encoder = new net.minecraft.world.SimpleContainer(ENCODER_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            WorkConsoleBlockEntity.this.setChanged();
        }
    };
    /** 納品箱（指令台そのものも納品口を兼ねる。ホッパーなどで取り出せる）。 */
    public static final int OUTBOX_SLOTS = 27;
    private final net.minecraft.world.SimpleContainer outbox = new net.minecraft.world.SimpleContainer(OUTBOX_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            WorkConsoleBlockEntity.this.setChanged();
        }
    };
    private final IItemHandler outboxHandler = new net.neoforged.neoforge.items.wrapper.InvWrapper(outbox);
    private final Set<BlockPos> links = new LinkedHashSet<>();
    private final List<WorkOrder> orders = new ArrayList<>();
    private int nextOrderId = 1;
    /** 渡している作業（保存しない。読み込み直したら、アンドロイドが作業を取り直す）。 */
    private final List<WorkJob.Lease> leases = new ArrayList<>();
    /** 登録した注文。 */
    private final List<StandingOrder> standing = new ArrayList<>();
    private int nextStandingId = 1;
    public static final int MAX_STANDING = 16;
    /** レッドストーン信号が入っているか。進行中の注文があるか（コンパレーターの出力）。 */
    private boolean powered;
    private boolean outputOn;
    private int nextJobId = 1;
    /** 最後の知らせ（画面に出す。取り消しの理由など）。 */
    @Nullable
    private Component message;

    public WorkConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_WORK_CONSOLE.get(), pos, state);
    }

    // ------------------------------------------------------------------ 持ち主・許可証

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public ItemStackHandler permit() {
        return permit;
    }

    public net.minecraft.world.SimpleContainer outbox() {
        return outbox;
    }

    /** 納品箱（ホッパーなどから見た中身）。 */
    public IItemHandler outboxHandler() {
        return outboxHandler;
    }

    /** 許可証にだれかが登録されていれば、持ち主と登録した人だけ。なければだれでも。 */
    public boolean mayOperate(Player player) {
        List<ShieldPermitItem.Member> members = ShieldPermitItem.members(permit.getStackInSlot(0));
        if (members.isEmpty() || owner == null || owner.equals(player.getUUID())) {
            return true;
        }
        for (ShieldPermitItem.Member m : members) {
            if (m.id().equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public Component message() {
        return message;
    }

    // ------------------------------------------------------------------ つながり

    void addLink(BlockPos pos) {
        links.add(pos.immutable());
        setChanged();
    }

    void removeLink(BlockPos pos) {
        if (links.remove(pos)) {
            setChanged();
        }
    }

    /** 外す（画面のボタン）。 */
    public void unlink(BlockPos pos) {
        if (level != null && level.getBlockEntity(pos) instanceof WorkLinkedBlockEntity b && worldPosition.equals(b.console())) {
            b.setConsole(null);
        }
        removeLink(pos);
    }

    /** つながっているブロック（向こうもこの指令台につながっているものだけ。読み込まれていないものは除く）。 */
    public <T extends WorkLinkedBlockEntity> List<T> linked(Class<T> type) {
        List<T> out = new ArrayList<>();
        if (level == null) {
            return out;
        }
        for (BlockPos p : links) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof WorkLinkedBlockEntity b && type.isInstance(b)
                    && worldPosition.equals(b.console())) {
                out.add(type.cast(b));
            }
        }
        return out;
    }

    /** つながっている場所の一覧（画面用。種類はブロックで見る）。 */
    public List<BlockPos> links() {
        return new ArrayList<>(links);
    }

    // ------------------------------------------------------------------ 在庫・レシピ

    /** 素材置き場: 指令台と資材口の隣のストレージ。 */
    public List<IItemHandler> storages() {
        List<IItemHandler> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        SupplyPortBlockEntity.collect(level, worldPosition, seen, out);
        for (SupplyPortBlockEntity port : linked(SupplyPortBlockEntity.class)) {
            port.storages(level, seen, out);
        }
        return out;
    }

    /** 素材置き場の中身の合計。 */
    public List<ItemCount> stock() {
        List<ItemCount> out = new ArrayList<>();
        for (IItemHandler h : storages()) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                ItemCount.add(out, s, s.getCount());
            }
        }
        return out;
    }

    /** ほかの注文が予約した分を引いた在庫。 */
    public List<ItemCount> available() {
        List<ItemCount> out = stock();
        for (ItemCount c : out) {
            for (WorkOrder o : orders) {
                c.count -= o.outstanding(c.item);
            }
            c.count = Math.max(0, c.count);
        }
        return out;
    }

    public List<RecipeRackBlockEntity.Entry> recipes() {
        List<RecipeRackBlockEntity.Entry> out = new ArrayList<>();
        for (RecipeRackBlockEntity rack : linked(RecipeRackBlockEntity.class)) {
            rack.entries(level, out);
        }
        return out;
    }

    public WorkPlanner.Plan plan(ItemStack target, long count) {
        return WorkPlanner.plan(target, Math.max(1, Math.min(MAX_COUNT, count)), recipes(), available());
    }

    /**
     * 作って増やす計算（在庫を保つ注文）: 完成品そのものの在庫は使わない（使うと、作らずに在庫を予約するだけで終わり、在庫が増えない）。
     */
    private WorkPlanner.Plan planToStock(ItemStack target, long count) {
        List<ItemCount> stock = available();
        stock.removeIf(c -> c.matches(target));
        return WorkPlanner.plan(target, Math.max(1, Math.min(MAX_COUNT, count)), recipes(), stock);
    }

    // ------------------------------------------------------------------ アンドロイド

    /** 注文に使えるアンドロイド（範囲内・作業オン・この人が操作できる・印刷が済んでいる護衛）。 */
    public List<RuinAndroid> candidates(Player player) {
        if (level == null) {
            return List.of();
        }
        return level.getEntitiesOfClass(RuinAndroid.class, new AABB(worldPosition).inflate(WORKER_RANGE),
                a -> a.isAlive() && a.isGuard() && a.printed() && a.workEnabled() && a.mayOperate(player));
    }

    /** このアンドロイドが、ほかの注文の作業中か。 */
    public boolean busy(RuinAndroid a) {
        if (a.workConsole() == null) {
            return false;
        }
        // ほかの指令台の作業中か、この指令台のまだ終わっていない注文の作業中
        return !worldPosition.equals(a.workConsole()) || order(a.workOrder()) != null;
    }

    // ------------------------------------------------------------------ 注文

    public List<WorkOrder> orders() {
        return orders;
    }

    @Nullable
    public WorkOrder order(int id) {
        for (WorkOrder o : orders) {
            if (o.id == id) {
                return o;
            }
        }
        return null;
    }

    /** 注文する。できなければ理由を返す（できたら null）。 */
    @Nullable
    public Component start(ServerPlayer player, ItemStack target, long count, List<UUID> androids, @Nullable BlockPos destination) {
        return start(player, target, count, androids, destination, false);
    }

    /**
     * 注文する（toStock なら、できた物を素材置き場へ入れる）。androids が空なら、選べる全員。できなければ理由を返す（できたら null）。
     */
    @Nullable
    private Component start(Player player, ItemStack target, long count, List<UUID> androids, @Nullable BlockPos destination,
                            boolean toStock) {
        if (!mayOperate(player)) {
            return Component.translatable("gui.singulo.work_console.locked");
        }
        WorkPlanner.Plan plan = toStock ? planToStock(target, count) : plan(target, count);
        if (!plan.ok()) {
            return Component.translatable(plan.loop ? "gui.singulo.work_console.error.loop" : "gui.singulo.work_console.error.missing");
        }
        if (toStock && plan.steps.isEmpty()) {
            return Component.translatable("gui.singulo.work_console.error.missing");   // 作る工程がない（レシピがない）
        }
        boolean mining = plan.steps.stream().anyMatch(s -> s.entry().recipe().needsMining());
        List<RuinAndroid> chosen = new ArrayList<>();
        for (RuinAndroid a : candidates(player)) {
            if ((androids.isEmpty() || androids.contains(a.getUUID())) && !busy(a) && (!androids.isEmpty() || !mining || a.canMine())) {
                chosen.add(a);
            }
        }
        if (chosen.isEmpty()) {
            return Component.translatable("gui.singulo.work_console.error.no_android");
        }
        WorkOrder order = WorkOrder.create(nextOrderId++, plan, player.getUUID());
        order.toStock = toStock;
        order.destination = destination != null && !destination.equals(worldPosition) && deliveryPort(destination) != null ? destination : null;
        // どの工程も、選んだアンドロイドのだれかが一度に運べるか
        for (WorkOrder.StepState s : order.steps) {
            boolean fits = false;
            for (RuinAndroid a : chosen) {
                fits |= maxCrafts(s.recipe, a.freeCarry(), 1) >= 1;
            }
            if (!fits) {
                return Component.translatable("gui.singulo.work_console.error.capacity", s.recipe.output().getHoverName());
            }
            if (!s.recipe.processing() && !s.recipe.procedure() && !isWorkbench(s.block)) {
                return Component.translatable("gui.singulo.work_console.error.not_workbench", s.recipe.output().getHoverName());
            }
            if (s.recipe.processing() && !isMachine(s.location)) {
                return Component.translatable("gui.singulo.work_console.error.not_machine", s.recipe.output().getHoverName());
            }
            if (s.recipe.needsMining() && chosen.stream().noneMatch(RuinAndroid::canMine)) {
                return Component.translatable("gui.singulo.work_console.error.no_miner", s.recipe.output().getHoverName());
            }
        }
        reserve(order, plan);
        orders.add(order);
        for (RuinAndroid a : chosen) {
            order.workers.add(a.getUUID());
            a.assignWork(worldPosition, order.id);
        }
        message = null;
        setChanged();
        return null;
    }

    // ------------------------------------------------------------------ 登録した注文

    public List<StandingOrder> standing() {
        return standing;
    }

    public boolean powered() {
        return powered;
    }

    /** 注文を登録する。できなければ理由を返す。 */
    @Nullable
    public Component register(ServerPlayer player, ItemStack target, long count, int trigger, @Nullable BlockPos destination,
                              List<UUID> androids) {
        if (!mayOperate(player)) {
            return Component.translatable("gui.singulo.work_console.locked");
        }
        if (standing.size() >= MAX_STANDING) {
            return Component.translatable("gui.singulo.work_console.standing.full", MAX_STANDING);
        }
        BlockPos dest = destination != null && !destination.equals(worldPosition) && deliveryPort(destination) != null ? destination : null;
        standing.add(new StandingOrder(nextStandingId++, target, Math.max(1, Math.min(MAX_COUNT, count)), trigger, dest, androids,
                player.getUUID()));
        message = Component.translatable("gui.singulo.work_console.standing.added", target.getHoverName());
        setChanged();
        return null;
    }

    public void unregister(int id) {
        standing.removeIf(s -> s.id == id);
        setChanged();
    }

    /** 隣の信号が変わった。立ち上がりで「信号で1回」の登録に知らせる。 */
    public void onSignal(boolean now) {
        if (now && !powered) {
            for (StandingOrder s : standing) {
                if (s.trigger == StandingOrder.PULSE && s.activeOrder < 0) {
                    s.pending = true;               // 進行中に来た信号は数えない
                }
            }
        }
        if (powered != now) {
            powered = now;
            setChanged();
        }
    }

    /** 登録した注文のきっかけを調べて、要れば注文する（毎秒）。 */
    private void runStanding(ServerLevel level) {
        for (StandingOrder s : standing) {
            if (s.activeOrder >= 0) {
                if (order(s.activeOrder) != null) {
                    s.state = StandingOrder.RUNNING;
                    continue;
                }
                s.activeOrder = -1;
            }
            long want;
            if (s.trigger == StandingOrder.PULSE) {
                want = s.pending ? s.count : 0;
            } else if (s.trigger == StandingOrder.KEEP_POWERED && !powered) {
                s.state = StandingOrder.NO_SIGNAL;
                s.reason = null;
                continue;
            } else {
                want = Math.max(0, s.count - ItemCount.get(stock(), s.target));
            }
            if (want <= 0) {
                s.state = StandingOrder.IDLE;
                s.reason = null;
                continue;
            }
            Player actor = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                    s.registrant != null ? s.registrant : owner != null ? owner : UUID.nameUUIDFromBytes(new byte[0]), "[Singulo]"));
            int id = nextOrderId;
            Component why = start(actor, s.target, want, s.androids, s.destination, s.toStock());
            if (why == null) {
                s.activeOrder = id;
                s.pending = false;
                s.state = StandingOrder.RUNNING;
                s.reason = null;
                setChanged();
            } else {
                s.state = StandingOrder.WAITING;   // 素材・アンドロイドがそろうのを待つ（そろえば次の確認で始める）
                s.reason = why;
            }
        }
    }

    /** 注文を取り消す。作業中のアンドロイドは、持っている物を資材口へ戻してから、作業をやめる。 */
    public void cancel(int id, @Nullable Component reason) {
        WorkOrder o = order(id);
        if (o == null) {
            return;
        }
        orders.remove(o);
        leases.removeIf(l -> l.job.orderId() == id);
        message = reason != null ? reason : Component.translatable("gui.singulo.work_console.cancelled", o.target.getHoverName());
        setChanged();
    }

    /**
     * 一度に作れる回数: 持ち物の空き slots に収まるだけ（上限 limit。足りない分を残さないよう、収まるなら全部）。
     * 作るたびに素材は減り、できた物が増えるので、作っている途中で一番場所を取るときで数える。
     */
    /** 加工モードのレシピを作れる場所（上から入れて、下から取り出せる）。 */
    private boolean isMachine(BlockPos pos) {
        return level != null
                && level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, net.minecraft.core.Direction.UP) != null
                && level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, net.minecraft.core.Direction.DOWN) != null;
    }

    /** 作業台のレシピを作れるブロック（バニラの作業台か、アンドロイド作業台）。 */
    public static boolean isWorkbench(net.minecraft.world.level.block.Block block) {
        return block instanceof net.minecraft.world.level.block.CraftingTableBlock
                || block == io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_WORKBENCH.get();
    }

    static long maxCrafts(RecipeData recipe, int slots, long limit) {
        long lo = 0, hi = limit;
        while (lo < hi) {                                     // 収まる回数は、回数を増やすほど収まりにくくなるので二分探索
            long mid = (lo + hi + 1) / 2;
            if (peakStacks(recipe, mid) <= slots) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** crafts 回作るあいだに、持ち物が一番場所を取るときのスタック数（素材の残り + できた物 + 残る物）。 */
    static int peakStacks(RecipeData recipe, long crafts) {
        int peak = 0;
        long step = Math.max(1, crafts / 256);
        for (long done = 0; done <= crafts; done = done == crafts ? crafts + 1 : Math.min(crafts, done + step)) {
            int stacks = 0;
            for (ItemCount in : recipe.inputs()) {
                stacks += stacks(in.item, in.count * (crafts - done));
                if (in.item.hasCraftingRemainingItem()) {
                    stacks += stacks(in.item.getCraftingRemainingItem(), in.count * done);   // バケツなど、作ると残る物
                }
            }
            stacks += stacks(recipe.output(), (long) recipe.output().getCount() * done);
            peak = Math.max(peak, stacks);
        }
        // ほかに出てくる物（副産物、採掘で出るほかの物）の分も空けておく
        int extra = recipe.extras().size() + (recipe.needsMining() ? 2 : 0);
        return peak + extra + (crafts > 256 ? 1 : 0);      // 間引いて数えたときは、1つ余裕を見る
    }

    private static int stacks(ItemStack item, long count) {
        int max = Math.max(1, item.getMaxStackSize());
        return (int) ((count + max - 1) / max);
    }

    // ------------------------------------------------------------------ 作業の受け渡し（アンドロイドから）

    /** 注文の作業で、ブロックを壊してよいか確かめるときの「だれとして」（注文した人。いなければ持ち主）。 */
    @Nullable
    public java.util.UUID actorFor(int orderId) {
        WorkOrder o = order(orderId);
        return o != null && o.orderer != null ? o.orderer : owner;
    }

    /** このアンドロイドにできる工程か（採掘の手順は、採掘アップグレードが要る）。 */
    private static boolean canDo(RuinAndroid a, RecipeData recipe) {
        return !recipe.needsMining() || a.canMine();
    }

    /** 次の作業をもらう。 */
    public WorkJob requestJob(RuinAndroid a) {
        WorkOrder o = order(a.workOrder());
        if (o == null) {
            return WorkJob.simple(WorkJob.Type.EXIT, a.workOrder());
        }
        o.workers.add(a.getUUID());                     // 印刷し直されたアンドロイドも、続きを受け持つ
        if (!a.carryEmpty()) {
            return WorkJob.simple(WorkJob.Type.DEPOSIT, o.id);
        }
        long now = level.getGameTime();
        if (o.status == WorkOrder.WAIT_ITEMS && now < o.retryAt) {
            return WorkJob.simple(WorkJob.Type.WAIT, o.id);
        }
        int slots = a.carryCapacity();
        // まず、残りの工程をまとめて1つの作業にする（素材を一度に取り、素材を作る工程から順に作って、そのまま納品へ）。
        // 一度に運べなければ、運べるだけの量に減らす
        WorkJob batch = batchJob(o, slots, a);
        if (batch != null) {
            return lease(batch, a);
        }
        boolean tooSmall = false;
        for (int i = 0; i < o.steps.size(); i++) {
            WorkOrder.StepState s = o.steps.get(i);
            if (s.remaining() <= 0 || !o.ready(s) || locationBusy(s.location) || !canDo(a, s.recipe)) {
                continue;
            }
            long k = maxCrafts(s.recipe, slots, s.remaining());
            if (k <= 0) {
                tooSmall = true;
                continue;
            }
            List<ItemCount> fetch = new ArrayList<>();
            for (ItemCount in : s.recipe.inputs()) {
                ItemCount.add(fetch, in.item, in.count * k);
            }
            s.inProgress += k;
            return lease(new WorkJob(WorkJob.Type.CRAFT, o.id, nextJobId++, 0, fetch,
                    List.of(new WorkJob.Batch(i, k, s.location, s.recipe)), false), a);
        }
        if (o.stepsFinished()) {
            long left = o.count - o.delivered - o.deliverInProgress;
            if (o.toStock && left > 0) {
                // 素材置き場へ入れる注文: もう素材置き場にある完成品は、運ばずに届けたことにする
                long have = Math.min(left, o.held(null, o.target));
                o.delivered += have;
                left -= have;
                if (left <= 0) {
                    finishIfDone(o);
                    return WorkJob.simple(WorkJob.Type.WAIT, o.id);
                }
            }
            if (left > 0) {
                long amount = Math.min(left, (long) slots * o.target.getMaxStackSize());
                o.deliverInProgress += amount;
                List<ItemCount> fetch = new ArrayList<>();
                ItemCount.add(fetch, o.target, amount);
                return lease(new WorkJob(WorkJob.Type.DELIVER, o.id, nextJobId++, amount, fetch, List.of(), false), a);
            }
        }
        if (tooSmall && o.status == WorkOrder.RUNNING) {
            o.status = WorkOrder.WAIT_CAPACITY;
        }
        return WorkJob.simple(WorkJob.Type.WAIT, o.id);
    }

    /**
     * 作り終えたあと、持っている物だけで続けられる作業（次の工程を作る・完成品を届ける）。なければ null（資材口へ戻す）。
     * 作った物をいちいち素材置き場へ戻さずに、そのまま次へ進むため。
     */
    @Nullable
    public WorkJob chainJob(RuinAndroid a, int orderId) {
        WorkOrder o = order(orderId);
        if (o == null) {
            return null;
        }
        for (int i = 0; i < o.steps.size(); i++) {
            WorkOrder.StepState s = o.steps.get(i);
            if (s.remaining() <= 0 || !o.ready(s) || locationBusy(s.location) || !canDo(a, s.recipe)) {
                continue;
            }
            // 持ち物の素材で何回作れるか（素材の要らない手順（採掘だけなど）は、空いている場所に収まるだけ）
            long k = s.remaining();
            for (ItemCount in : s.recipe.inputs()) {
                k = Math.min(k, carried(a, in.item) / in.count);
            }
            if (s.recipe.inputs().isEmpty()) {
                k = maxCrafts(s.recipe, a.freeCarry(), k);
            }
            if (k <= 0) {
                continue;
            }
            List<ItemCount> use = new ArrayList<>();
            for (ItemCount in : s.recipe.inputs()) {
                ItemCount.add(use, in.item, in.count * k);
            }
            s.inProgress += k;
            return lease(new WorkJob(WorkJob.Type.CRAFT, o.id, nextJobId++, 0, use, List.of(new WorkJob.Batch(i, k, s.location, s.recipe)), true), a);
        }
        if (o.stepsFinished()) {
            long left = o.count - o.delivered - o.deliverInProgress;
            long have = carried(a, o.target);
            if (left > 0 && have > 0) {
                long amount = Math.min(left, have);
                o.deliverInProgress += amount;
                List<ItemCount> use = new ArrayList<>();
                ItemCount.add(use, o.target, amount);
                return lease(new WorkJob(WorkJob.Type.DELIVER, o.id, nextJobId++, amount, use, List.of(), true), a);
            }
        }
        return null;
    }

    /**
     * 残りの工程をまとめた作業: 工程を上から（素材を作る工程が先）順に、残りの回数を割合 f で減らして並べ、素材の取り出しを
     * 一度で済ませる。作った物は次の工程の素材に使い、足りない分だけ素材置き場（この注文の予約）から取る。
     * 一度に運べて、予約で足りる、一番大きな f を選ぶ。まとめられなければ null。
     */
    @Nullable
    private WorkJob batchJob(WorkOrder o, int slots, RuinAndroid a) {
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < o.steps.size(); i++) {
            WorkOrder.StepState s = o.steps.get(i);
            if (s.remaining() <= 0) {
                continue;
            }
            if (s.inProgress > 0 || locationBusy(s.location) || !canDo(a, s.recipe)) {
                return null;                          // ほかのアンドロイドが作業中の工程、このアンドロイドにできない工程がある: 1工程ずつに分ける
            }
            for (int j : s.after) {
                if (!o.steps.get(j).finished() && !open.contains(j)) {
                    return null;
                }
            }
            open.add(i);
        }
        if (open.size() < 2) {
            return null;                              // 1工程だけなら、ふつうの作業で十分
        }
        // 割合 f を二分探索（1 から下げる）
        double lo = 0, hi = 1;
        List<WorkJob.Batch> best = null;
        List<ItemCount> bestFetch = null;
        for (int iter = 0; iter < 14; iter++) {
            double f = iter == 0 ? 1 : (lo + hi) / 2;
            List<WorkJob.Batch> batch = new ArrayList<>();
            for (int i : open) {
                WorkOrder.StepState s = o.steps.get(i);
                long k = Math.max(1, Math.min(s.remaining(), (long) Math.ceil(s.remaining() * f)));
                batch.add(new WorkJob.Batch(i, k, s.location, s.recipe));
            }
            List<ItemCount> fetch = simulate(o, batch, slots);
            if (fetch != null) {
                best = batch;
                bestFetch = fetch;
                if (iter == 0) {
                    break;
                }
                lo = f;
            } else {
                hi = f;
            }
        }
        if (best == null) {
            return null;
        }
        for (WorkJob.Batch b : best) {
            o.steps.get(b.step()).inProgress += b.crafts();
        }
        return new WorkJob(WorkJob.Type.CRAFT, o.id, nextJobId++, 0, bestFetch, best, false);
    }

    /**
     * まとめた作業を、持ち物の中で順に作ってみる。最初に一度で取る物の一覧を返す（運べない・予約で足りなければ null）。
     * 持ち物の場所は、取った直後と、各工程を作り終えた時（作っている途中は、素材とできた物が両方あるとして多めに）で数える。
     */
    @Nullable
    private List<ItemCount> simulate(WorkOrder o, List<WorkJob.Batch> batch, int slots) {
        List<ItemCount> inv = new ArrayList<>();
        List<ItemCount> fetch = new ArrayList<>();
        for (WorkJob.Batch b : batch) {
            for (ItemCount in : b.recipe().inputs()) {
                long need = in.count * b.crafts();
                long have = ItemCount.get(inv, in.item);
                long use = Math.min(have, need);
                ItemCount.add(inv, in.item, -use);
                ItemCount.add(fetch, in.item, need - use);
            }
            ItemCount.add(inv, b.recipe().output(), (long) b.recipe().output().getCount() * b.crafts());
        }
        for (ItemCount c : fetch) {
            if (c.count > 0 && o.held(null, c.item) < c.count) {
                return null;                          // 予約した在庫で足りない
            }
        }
        fetch.removeIf(c -> c.count <= 0);
        // 持ち物の場所: 順に作りながら、一番多いときで数える
        List<ItemCount> carry = new ArrayList<>();
        for (ItemCount c : fetch) {
            ItemCount.add(carry, c.item, c.count);
        }
        int peak = stacks(carry);
        for (WorkJob.Batch b : batch) {
            List<ItemCount> during = new ArrayList<>();
            for (ItemCount c : carry) {
                ItemCount.add(during, c.item, c.count);
            }
            ItemCount.add(during, b.recipe().output(), (long) b.recipe().output().getCount() * b.crafts());
            for (ItemCount in : b.recipe().inputs()) {
                if (in.item.hasCraftingRemainingItem()) {
                    ItemCount.add(during, in.item.getCraftingRemainingItem(), in.count * b.crafts());
                }
            }
            peak = Math.max(peak, stacks(during));
            for (ItemCount in : b.recipe().inputs()) {
                ItemCount.add(carry, in.item, -in.count * b.crafts());
            }
            ItemCount.add(carry, b.recipe().output(), (long) b.recipe().output().getCount() * b.crafts());
        }
        return peak <= slots ? fetch : null;
    }

    private static int stacks(List<ItemCount> list) {
        int n = 0;
        for (ItemCount c : list) {
            if (c.count > 0) {
                n += stacks(c.item, c.count);
            }
        }
        return n;
    }

    /** 持ち物の中の、見本と同じ物の数。 */
    private static long carried(RuinAndroid a, ItemStack like) {
        long n = 0;
        for (int slot : a.carrySlots()) {
            if (ItemStack.isSameItemSameComponents(a.inventory().getItem(slot), like)) {
                n += a.inventory().getItem(slot).getCount();
            }
        }
        return n;
    }

    private WorkJob lease(WorkJob job, RuinAndroid a) {
        leases.add(new WorkJob.Lease(job, a.getUUID(), level.getGameTime()));
        WorkOrder o = order(job.orderId());
        if (o != null && o.status == WorkOrder.WAIT_CAPACITY) {
            o.status = WorkOrder.RUNNING;
        }
        return job;
    }

    private boolean locationBusy(BlockPos pos) {
        for (WorkJob.Lease l : leases) {
            if (l.job.type() == WorkJob.Type.CRAFT) {
                for (WorkJob.Batch b : l.job.batch()) {
                    if (pos.equals(b.location())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    private WorkJob.Lease lease(int jobId) {
        for (WorkJob.Lease l : leases) {
            if (l.job.jobId() == jobId) {
                return l;
            }
        }
        return null;
    }

    /** 作業中だと知らせる（毎秒）。作業がもう取り消されていれば false。 */
    public boolean heartbeat(RuinAndroid a, WorkJob job) {
        WorkJob.Lease l = lease(job.jobId());
        if (l == null || order(job.orderId()) == null) {
            return false;
        }
        l.worker = a.getUUID();
        l.lastBeat = level.getGameTime();
        return true;
    }

    // ------------------------------------------------------------------ 資材の受け渡し場所（指令台と資材口）

    /** 資材の受け渡し場所: 指令台と、つながっている資材口。 */
    public List<BlockPos> supplyPoints() {
        List<BlockPos> out = new ArrayList<>();
        out.add(worldPosition);
        for (SupplyPortBlockEntity port : linked(SupplyPortBlockEntity.class)) {
            out.add(port.getBlockPos());
        }
        return out;
    }

    /** 受け渡し場所の隣のストレージ。 */
    public List<IItemHandler> storagesAt(BlockPos point, Set<BlockPos> seen) {
        List<IItemHandler> out = new ArrayList<>();
        SupplyPortBlockEntity.collect(level, point, seen, out);
        return out;
    }

    /** 1つの受け渡し場所で取る物。 */
    public record Pickup(BlockPos point, List<ItemCount> items) {}

    /**
     * 作業の素材を、実際に入っている受け渡し場所ごとに分けて、取りに回る順（from から近い順）に並べる。全部そろわなければ null。
     */
    @Nullable
    public List<Pickup> planPickup(WorkJob job, BlockPos from) {
        WorkOrder o = order(job.orderId());
        if (o == null) {
            return null;
        }
        // 予約した物（注文したときにあった物と、この注文が作って戻した物）だけを、近い場所から順に取る
        List<BlockPos> points = new ArrayList<>();
        for (WorkOrder.Holding h : o.held) {
            if (!points.contains(h.point)) {
                points.add(h.point);
            }
        }
        points.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(from)));
        List<ItemCount> left = new ArrayList<>();
        for (ItemCount c : job.fetch()) {
            ItemCount.add(left, c.item, c.count);
        }
        List<Pickup> out = new ArrayList<>();
        for (BlockPos p : points) {
            List<ItemCount> here = new ArrayList<>();
            for (ItemCount need : left) {
                long take = Math.min(need.count, o.held(p, need.item));
                if (take > 0) {
                    need.count -= take;
                    ItemCount.add(here, need.item, take);
                }
            }
            if (!here.isEmpty()) {
                out.add(new Pickup(p, here));
            }
        }
        for (ItemCount need : left) {
            if (need.count > 0) {
                return null;
            }
        }
        return out;
    }

    /**
     * 注文のときに、使う在庫を受け渡し場所ごとに予約する（その時そこにあって、ほかの注文が予約していない物だけ）。
     * 同じストレージ（2つの場所に挟まれた箱など）は、先に数えた場所の物として扱う。
     */
    private void reserve(WorkOrder order, WorkPlanner.Plan plan) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> points = supplyPoints();
        List<List<IItemHandler>> stores = new ArrayList<>();
        for (BlockPos p : points) {
            stores.add(storagesAt(p, seen));
        }
        for (ItemCount c : plan.fromStock) {
            long left = c.count;
            for (int i = 0; i < points.size() && left > 0; i++) {
                long here = 0;
                for (IItemHandler h : stores.get(i)) {
                    for (int s = 0; s < h.getSlots(); s++) {
                        if (c.matches(h.getStackInSlot(s))) {
                            here += h.getStackInSlot(s).getCount();
                        }
                    }
                }
                for (WorkOrder other : orders) {
                    here -= other.held(points.get(i), c.item);
                }
                long take = Math.min(left, Math.max(0, here));
                order.hold(points.get(i), c.item, take);
                left -= take;
            }
        }
    }

    /** 受け渡し場所で、そこにある物を持ち物へ取り出す（全部そろわなければ取らない）。 */
    public boolean fetchAt(RuinAndroid a, WorkJob job, Pickup pickup) {
        List<IItemHandler> stores = storagesAt(pickup.point(), new HashSet<>());
        for (ItemCount need : pickup.items()) {
            long have = 0;
            for (IItemHandler h : stores) {
                for (int i = 0; i < h.getSlots(); i++) {
                    if (need.matches(h.getStackInSlot(i))) {
                        have += h.getStackInSlot(i).getCount();
                    }
                }
            }
            if (have < need.count) {
                return false;
            }
        }
        WorkOrder o = order(job.orderId());
        for (ItemCount need : pickup.items()) {
            long left = need.count;
            for (IItemHandler h : stores) {
                for (int i = 0; i < h.getSlots() && left > 0; i++) {
                    if (need.matches(h.getStackInSlot(i))) {
                        ItemStack got = h.extractItem(i, (int) Math.min(left, h.getStackInSlot(i).getMaxStackSize()), false);
                        left -= got.getCount();
                        ItemStack rest = a.addToCarry(got);
                        if (!rest.isEmpty()) {
                            ItemHandlerHelper.insertItemStacked(h, rest, false);      // 入りきらなければ戻す（ふつうは起きない）
                            left += rest.getCount();
                        }
                    }
                }
            }
            if (o != null) {
                o.release(pickup.point(), need.item, need.count - left);      // 取り出した分の予約を使う
            }
        }
        if (o != null && o.status == WorkOrder.WAIT_ITEMS) {
            o.status = WorkOrder.RUNNING;
        }
        setChanged();
        return true;
    }

    /** 持ち物を、受け渡し場所 point の隣のストレージへ戻す。全部戻せたら true（入りきらなければ残る。ほかの場所へ回る）。 */
    public boolean depositAt(RuinAndroid a, @Nullable WorkJob job, BlockPos point) {
        WorkOrder o = job == null ? null : order(job.orderId());
        List<IItemHandler> stores = storagesAt(point, new HashSet<>());
        boolean all = true;
        for (int slot : a.carrySlots()) {
            ItemStack s = a.inventory().getItem(slot);
            if (s.isEmpty()) {
                continue;
            }
            ItemStack rest = s.copy();
            for (IItemHandler h : stores) {
                rest = ItemHandlerHelper.insertItemStacked(h, rest, false);
                if (rest.isEmpty()) {
                    break;
                }
            }
            if (o != null) {
                o.hold(point, s, s.getCount() - rest.getCount());              // この注文が戻した物は、この注文が使ってよい
            }
            a.inventory().setItem(slot, rest);
            all &= rest.isEmpty();
        }
        if (!all && o != null) {
            o.status = WorkOrder.WAIT_SPACE;
        } else if (o != null && o.status == WorkOrder.WAIT_SPACE) {
            o.status = WorkOrder.RUNNING;
        }
        setChanged();
        return all;
    }

    /** 注文の納品先（納品口がなくなっていれば、指令台）。 */
    public BlockPos destination(WorkJob job) {
        WorkOrder o = order(job.orderId());
        if (o != null && o.toStock) {
            // 素材置き場へ入れる: ストレージのある受け渡し場所（指令台を先に）
            for (BlockPos p : supplyPoints()) {
                if (!storagesAt(p, new HashSet<>()).isEmpty()) {
                    return p;
                }
            }
            return worldPosition;
        }
        return o != null && o.destination != null && deliveryPort(o.destination) != null ? o.destination : worldPosition;
    }

    /** つながっている納品口（なければ null）。 */
    @Nullable
    public DeliveryPortBlockEntity deliveryPort(BlockPos pos) {
        for (DeliveryPortBlockEntity port : linked(DeliveryPortBlockEntity.class)) {
            if (port.getBlockPos().equals(pos)) {
                return port;
            }
        }
        return null;
    }

    /** 完成品を納品先（指令台の納品箱か、納品口）へ届ける。全部入れば true。 */
    public boolean deliver(RuinAndroid a, WorkJob job) {
        BlockPos at = destination(job);
        DeliveryPortBlockEntity port = deliveryPort(at);
        WorkOrder o = order(job.orderId());
        IItemHandler box = o != null && o.toStock ? stockHandler(at) : port != null ? port.handler() : outboxHandler;
        boolean all = true;
        long moved = 0;
        long limit = job.amount();
        for (int slot : a.carrySlots()) {
            ItemStack s = a.inventory().getItem(slot);
            if (s.isEmpty() || limit <= moved || !ItemStack.isSameItemSameComponents(s, job.fetch().get(0).item)) {
                continue;
            }
            int give = (int) Math.min(s.getCount(), limit - moved);
            int notIn = ItemHandlerHelper.insertItemStacked(box, s.copyWithCount(give), false).getCount();
            moved += give - notIn;
            a.inventory().setItem(slot, s.copyWithCount(s.getCount() - give + notIn));
            all &= notIn == 0;
        }
        if (o != null) {
            o.delivered += moved;
            o.deliverInProgress = Math.max(0, o.deliverInProgress - moved);
            o.status = all ? WorkOrder.RUNNING : WorkOrder.WAIT_SPACE;
        }
        return all;
    }

    /** 受け渡し場所の隣のストレージ全部を、1つの入れ口として扱う（素材置き場へ入れる注文の納品。入れるだけ）。 */
    private IItemHandler stockHandler(BlockPos point) {
        List<IItemHandler> stores = storagesAt(point, new HashSet<>());
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 1;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                ItemStack rest = stack;
                for (IItemHandler h : stores) {
                    rest = ItemHandlerHelper.insertItemStacked(h, rest, simulate);
                    if (rest.isEmpty()) {
                        break;
                    }
                }
                return rest;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return true;
            }
        };
    }

    /** 作業が終わった。 */
    public void complete(WorkJob job) {
        WorkJob.Lease l = lease(job.jobId());
        if (l == null) {
            return;
        }
        leases.remove(l);
        WorkOrder o = order(job.orderId());
        if (o == null) {
            return;
        }
        if (job.type() == WorkJob.Type.CRAFT) {
            for (WorkJob.Batch b : job.batch()) {
                if (b.step() < o.steps.size()) {
                    WorkOrder.StepState s = o.steps.get(b.step());
                    s.inProgress = Math.max(0, s.inProgress - b.crafts());
                    s.done += b.crafts();
                }
            }
        }
        o.status = WorkOrder.RUNNING;
        finishIfDone(o);
        setChanged();
    }

    /** 作業ができなかった。素材が足りなければ少し待ってやり直す。たどり着けなければ注文を取り消す。 */
    public void fail(WorkJob job, Failure why) {
        WorkJob.Lease l = lease(job.jobId());
        if (l != null) {
            leases.remove(l);
            release(l.job);
        }
        WorkOrder o = order(job.orderId());
        if (o == null) {
            return;
        }
        switch (why) {
            case MISSING -> {
                o.status = WorkOrder.WAIT_ITEMS;
                o.retryAt = level.getGameTime() + RETRY_DELAY;
            }
            case UNREACHABLE -> cancel(o.id, Component.translatable("gui.singulo.work_console.cancelled.unreachable", o.target.getHoverName()));
            case BAD_RECIPE -> cancel(o.id, Component.translatable("gui.singulo.work_console.cancelled.recipe", o.target.getHoverName()));
            case STALLED -> cancel(o.id, Component.translatable("gui.singulo.work_console.cancelled.stalled", o.target.getHoverName()));
        }
        setChanged();
    }

    public enum Failure { MISSING, UNREACHABLE, BAD_RECIPE, STALLED }

    /** 取り上げた作業の分を、まだやっていないことにする。 */
    private void release(WorkJob job) {
        WorkOrder o = order(job.orderId());
        if (o == null) {
            return;
        }
        if (job.type() == WorkJob.Type.CRAFT) {
            for (WorkJob.Batch b : job.batch()) {
                if (b.step() < o.steps.size()) {
                    WorkOrder.StepState s = o.steps.get(b.step());
                    s.inProgress = Math.max(0, s.inProgress - b.crafts());
                }
            }
        } else if (job.type() == WorkJob.Type.DELIVER) {
            o.deliverInProgress = Math.max(0, o.deliverInProgress - job.amount());
        }
    }

    private void finishIfDone(WorkOrder o) {
        if (o.finished()) {
            orders.remove(o);
            message = Component.translatable("gui.singulo.work_console.done", o.target.getHoverName(), o.count);
            if (level != null) {
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.6F);
            }
        }
    }

    /** 一番近い資材の受け渡し場所（指令台か、資材口）。 */
    public BlockPos nearestSupply(BlockPos from) {
        BlockPos best = worldPosition;
        for (SupplyPortBlockEntity port : linked(SupplyPortBlockEntity.class)) {
            if (port.getBlockPos().distSqr(from) < best.distSqr(from)) {
                best = port.getBlockPos();
            }
        }
        return best;
    }

    /** 納品先に選べる場所: 指令台そのものと、つながっている納品口。 */
    public List<BlockPos> deliveryTargets() {
        List<BlockPos> out = new ArrayList<>();
        out.add(worldPosition);
        for (DeliveryPortBlockEntity port : linked(DeliveryPortBlockEntity.class)) {
            out.add(port.getBlockPos());
        }
        return out;
    }

    // ------------------------------------------------------------------ レシピの記録

    public net.minecraft.world.SimpleContainer encoder() {
        return encoder;
    }

    /** 見本の並びから、作る物を出す（作業台のレシピに合わなければ空）。加工モードでは、作る物は自分で置くので何もしない。 */
    public void updateResult() {
        if (!(level instanceof ServerLevel server) || mode != MODE_CRAFTING) {
            return;
        }
        net.minecraft.core.NonNullList<ItemStack> grid = net.minecraft.core.NonNullList.withSize(GRID, ItemStack.EMPTY);
        for (int i = 0; i < GRID; i++) {
            grid.set(i, encoder.getItem(i).copyWithCount(1));
        }
        net.minecraft.world.item.crafting.CraftingInput input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid);
        ItemStack result = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, server)
                .map(r -> r.value().assemble(input, server.registryAccess())).orElse(ItemStack.EMPTY);
        encoder.setItem(RESULT, result);
    }

    public int mode() {
        return mode;
    }

    /** 記録のモードを、作業台 → 加工 → 手順 の順に切り替える。 */
    public void cycleMode() {
        setMode((mode + 1) % MODES);
    }

    /** 記録のモードを変える。作る物の欄は空にし、作業台モードに戻すときは見本を1個ずつに戻す。 */
    public void setMode(int value) {
        if (mode == value) {
            return;
        }
        boolean toCrafting = value == MODE_CRAFTING;
        mode = value;
        encoder.setItem(RESULT, ItemStack.EMPTY);
        for (int i = 0; i < EXTRAS; i++) {
            encoder.setItem(EXTRA + i, ItemStack.EMPTY);
        }
        if (toCrafting) {
            for (int i = 0; i < GRID; i++) {
                ItemStack s = encoder.getItem(i);
                if (!s.isEmpty()) {
                    encoder.setItem(i, s.copyWithCount(1));
                }
            }
        }
        updateResult();
        setChanged();
    }

    /** 加工モードの見本を置き換える（JEI の自動配置）。同じ物はまとめる（数つき）。出てくる物は、主な物とほかに2つまで。 */
    public void setProcess(List<ItemStack> inputs, List<ItemStack> outputs) {
        mode = MODE_PROCESSING;
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack s : inputs) {
            if (s.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (ItemStack m : merged) {
                if (ItemStack.isSameItemSameComponents(m, s)) {
                    m.setCount(Math.min(64, m.getCount() + s.getCount()));
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(s.copyWithCount(Math.min(64, s.getCount())));
            }
        }
        for (int i = 0; i < GRID; i++) {
            encoder.setItem(i, i < merged.size() ? merged.get(i) : ItemStack.EMPTY);
        }
        List<ItemStack> outs = outputs.stream().filter(s -> !s.isEmpty()).toList();
        encoder.setItem(RESULT, outs.isEmpty() ? ItemStack.EMPTY : outs.get(0).copy());
        for (int i = 0; i < EXTRAS; i++) {
            encoder.setItem(EXTRA + i, i + 1 < outs.size() ? outs.get(i + 1).copy() : ItemStack.EMPTY);
        }
        setChanged();
    }

    /** 加工モードの見本・出てくる物の数を変える（1〜64）。 */
    public void adjustGhost(int index, int delta) {
        if (mode == MODE_CRAFTING || index < 0 || index >= ENCODER_SLOTS || index == CARD || index == STOCK || index == LOC_A
                || index == LOC_B) {
            return;
        }
        ItemStack s = encoder.getItem(index);
        if (!s.isEmpty()) {
            encoder.setItem(index, s.copyWithCount(Math.max(1, Math.min(64, s.getCount() + delta))));
        }
    }

    public net.minecraft.world.inventory.ContainerData modeData() {
        return modeData;
    }

    /** 見本を置き換える（JEI の自動配置。作業台モードにする）。 */
    public void setGrid(List<ItemStack> grid) {
        mode = MODE_CRAFTING;
        for (int i = 0; i < EXTRAS; i++) {
            encoder.setItem(EXTRA + i, ItemStack.EMPTY);
        }
        for (int i = 0; i < GRID; i++) {
            encoder.setItem(i, i < grid.size() ? grid.get(i).copyWithCount(1) : ItemStack.EMPTY);
        }
        updateResult();
    }

    /**
     * 記録する。記録したカードの欄にカードがあればそれに（上書き）、なければストックから1枚取って記録し、記録したカードの欄に置く。
     * できたら true。
     */
    public boolean encode() {
        ItemStack result = encoder.getItem(RESULT);
        if (result.isEmpty() || level == null) {
            return false;
        }
        if (mode == MODE_PROCEDURE && procSteps.isEmpty()) {
            return false;
        }
        if (mode == MODE_PROCESSING) {
            boolean any = false;
            for (int i = 0; i < GRID; i++) {
                any |= !encoder.getItem(i).isEmpty();
            }
            if (!any) {
                return false;
            }
        }
        ItemStack card = encoder.getItem(CARD);
        if (card.isEmpty()) {
            ItemStack stock = encoder.getItem(STOCK);
            if (!RecipeCardItem.isCard(stock) || stock.isEmpty() || ProcedureCardItem.isProcedureCard(stock) != (mode == MODE_PROCEDURE)) {
                return false;
            }
            card = stock.split(1);
            encoder.setItem(STOCK, stock);
            encoder.setItem(CARD, card);
        }
        if (!RecipeCardItem.isCard(card) || ProcedureCardItem.isProcedureCard(card) != (mode == MODE_PROCEDURE)) {
            return false;                       // 手順は手順カードに、レシピはレシピカードに
        }
        if (mode == MODE_PROCEDURE) {
            RecipeData.procedure(procSteps, result.copy()).write(card, level.registryAccess());
            encoder.setChanged();
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.4F);
            return true;
        }
        boolean processing = mode == MODE_PROCESSING;
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < GRID; i++) {
            grid.add(processing ? encoder.getItem(i).copy() : encoder.getItem(i).copyWithCount(1));
        }
        if (processing) {
            List<ItemStack> extras = new ArrayList<>();
            for (int i = 0; i < EXTRAS; i++) {
                if (!encoder.getItem(EXTRA + i).isEmpty()) {
                    extras.add(encoder.getItem(EXTRA + i).copy());
                }
            }
            new RecipeData(RecipeData.MODE_PROCESSING, grid, result.copy(), extras).write(card, level.registryAccess());
        } else {
            new RecipeData(grid, result.copy()).write(card, level.registryAccess());
        }
        encoder.setChanged();
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.4F);
        return true;
    }

    /** 見本の欄を空にする（手順モードでは、並べた手順を消す）。 */
    public void clearGrid() {
        if (mode == MODE_PROCEDURE) {
            procSteps.clear();
            encoder.setItem(PITEM, ItemStack.EMPTY);
            encoder.setItem(RESULT, ItemStack.EMPTY);
            setChanged();
            return;
        }
        int keep = mode;
        setGrid(List.of());
        if (keep != MODE_CRAFTING) {
            mode = keep;
            encoder.setItem(RESULT, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ 手順モード

    public List<ProcedureStep> procSteps() {
        return procSteps;
    }

    /** 次に足す手順の種類を切り替える（投入 → 取り出し → 採掘）。 */
    public void cycleStepKind() {
        stepKind = (stepKind + 1) % ProcedureStep.KINDS;
    }

    /**
     * 手順を足す: 場所は作業位置カード A（採掘なら B があれば、A と B で囲った範囲）、物は見本の欄（採掘で空なら、カードの完成品）。
     * 足せなければ、その理由。
     */
    @Nullable
    public Component addStep() {
        if (procSteps.size() >= RecipeData.MAX_STEPS) {
            return Component.translatable("gui.singulo.recipe_encoder.step.full");
        }
        ItemStack a = encoder.getItem(LOC_A);
        BlockPos pos = WorkLocationCardItem.pos(a);
        if (pos == null || level == null || !WorkLocationCardItem.sameDimension(a, level)) {
            return Component.translatable("gui.singulo.recipe_encoder.step.no_place");
        }
        ItemStack item = encoder.getItem(PITEM).copy();
        if (stepKind != ProcedureStep.MINE && item.isEmpty()) {
            return Component.translatable("gui.singulo.recipe_encoder.step.no_item");
        }
        BlockPos corner = null;
        if (stepKind == ProcedureStep.MINE) {
            ItemStack b = encoder.getItem(LOC_B);
            BlockPos pb = WorkLocationCardItem.pos(b);
            if (pb != null && WorkLocationCardItem.sameDimension(b, level)) {
                if (Math.abs(pb.getX() - pos.getX()) >= MINE_SPAN || Math.abs(pb.getY() - pos.getY()) >= MINE_SPAN
                        || Math.abs(pb.getZ() - pos.getZ()) >= MINE_SPAN) {
                    return Component.translatable("gui.singulo.recipe_encoder.step.too_big", MINE_SPAN);
                }
                corner = pb;
            }
        }
        procSteps.add(new ProcedureStep(stepKind, pos, corner, item));
        setChanged();
        return null;
    }

    /** 採掘の範囲の、1辺の最大（ブロック）。 */
    public static final int MINE_SPAN = 16;

    public void removeStep(int index) {
        if (index >= 0 && index < procSteps.size()) {
            procSteps.remove(index);
            setChanged();
        }
    }

    /** 記録したカードの欄のカードの、レシピを消す。 */
    public void clearCard() {
        ItemStack card = encoder.getItem(CARD);
        if (RecipeCardItem.isCard(card)) {
            RecipeData.clear(card);
            encoder.setChanged();
        }
    }

    // ------------------------------------------------------------------ 毎 tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, WorkConsoleBlockEntity be) {
        if (level.getGameTime() % 20 == 0) {
            be.tick((ServerLevel) level);
        }
        if ((level.getGameTime() + 5) % 10 == 0) {
            SupplyPortBlockEntity.pushOut(level, pos, be.outbox);     // 納品箱の中身を、隣の箱へ出す
        }
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        // 知らせが途切れた作業（倒れた・読み込まれていない）を取り上げる
        for (Iterator<WorkJob.Lease> it = leases.iterator(); it.hasNext(); ) {
            WorkJob.Lease l = it.next();
            if (now - l.lastBeat > LEASE_TIMEOUT || order(l.job.orderId()) == null) {
                it.remove();
                release(l.job);
            }
        }
        for (WorkOrder o : new ArrayList<>(orders)) {
            finishIfDone(o);
        }
        runStanding(level);
        // コンパレーター: 進行中の注文があれば 15
        boolean on = !orders.isEmpty();
        if (on != outputOn) {
            outputOn = on;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        // 画面を開いている人に、様子を送る
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof WorkConsoleMenu menu && worldPosition.equals(menu.pos())) {
                sendView(p, menu.containerId, null);
            }
        }
    }

    /** 画面に様子を送る（plan は計算の結果。なければ null）。 */
    public void sendView(ServerPlayer player, int containerId, @Nullable CompoundTag plan) {
        PacketDistributor.sendToPlayer(player, new WorkConsoleViewPayload(containerId, WorkConsoleView.build(this, player, plan)));
    }

    // ------------------------------------------------------------------ 画面

    @Override
    public void openMenu(ServerPlayer player) {
        if (owner == null) {
            setOwner(player.getUUID());
        }
        if (!mayOperate(player)) {
            player.displayClientMessage(Component.translatable("gui.singulo.work_console.locked"), true);
            return;
        }
        player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
        sendView(player, player.containerMenu.containerId, null);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WorkConsoleMenu(id, inventory, worldPosition, permit, encoder, outbox, modeData);
    }

    @Override
    public void onBroken(Level level) {
        Block.popResource(level, worldPosition, permit.getStackInSlot(0));
        Block.popResource(level, worldPosition, encoder.getItem(CARD));
        Block.popResource(level, worldPosition, encoder.getItem(STOCK));
        Block.popResource(level, worldPosition, encoder.getItem(LOC_A));
        Block.popResource(level, worldPosition, encoder.getItem(LOC_B));
        for (int i = 0; i < OUTBOX_SLOTS; i++) {
            Block.popResource(level, worldPosition, outbox.getItem(i));
        }
        for (BlockPos p : links) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof WorkLinkedBlockEntity b && worldPosition.equals(b.console())) {
                b.setConsole(null);
            }
        }
    }

    // ------------------------------------------------------------------ 保存

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.put("permit", permit.serializeNBT(registries));
        ListTag ls = new ListTag();
        for (BlockPos p : links) {
            ls.add(NbtUtils.writeBlockPos(p));
        }
        tag.put("links", ls);
        ListTag os = new ListTag();
        for (WorkOrder o : orders) {
            os.add(o.save(registries));
        }
        tag.put("orders", os);
        tag.putInt("next_order", nextOrderId);
        ListTag ss = new ListTag();
        for (StandingOrder s : standing) {
            ss.add(s.save(registries));
        }
        tag.put("standing", ss);
        tag.putInt("next_standing", nextStandingId);
        tag.putBoolean("powered", powered);
        net.minecraft.core.NonNullList<ItemStack> enc = net.minecraft.core.NonNullList.withSize(ENCODER_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < ENCODER_SLOTS; i++) {
            enc.set(i, encoder.getItem(i));
        }
        tag.put("encoder", net.minecraft.world.ContainerHelper.saveAllItems(new CompoundTag(), enc, registries));
        tag.putInt("encoder_mode", mode);
        tag.putInt("step_kind", stepKind);
        ListTag ps = new ListTag();
        for (ProcedureStep s : procSteps) {
            ps.add(s.save(registries));
        }
        tag.put("proc_steps", ps);
        net.minecraft.core.NonNullList<ItemStack> box = net.minecraft.core.NonNullList.withSize(OUTBOX_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < OUTBOX_SLOTS; i++) {
            box.set(i, outbox.getItem(i));
        }
        tag.put("outbox", net.minecraft.world.ContainerHelper.saveAllItems(new CompoundTag(), box, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        if (tag.contains("permit")) {
            permit.deserializeNBT(registries, tag.getCompound("permit"));
        }
        links.clear();
        for (Tag t : tag.getList("links", Tag.TAG_INT_ARRAY)) {
            NbtUtils.readBlockPos(wrap(t), "p").ifPresent(links::add);
        }
        orders.clear();
        for (Tag t : tag.getList("orders", Tag.TAG_COMPOUND)) {
            WorkOrder o = WorkOrder.load((CompoundTag) t, registries);
            if (o != null) {
                orders.add(o);
            }
        }
        nextOrderId = Math.max(1, tag.getInt("next_order"));
        standing.clear();
        for (Tag t : tag.getList("standing", Tag.TAG_COMPOUND)) {
            StandingOrder s = StandingOrder.load((CompoundTag) t, registries);
            if (s != null) {
                standing.add(s);
            }
        }
        nextStandingId = Math.max(1, tag.getInt("next_standing"));
        powered = tag.getBoolean("powered");
        net.minecraft.core.NonNullList<ItemStack> enc = net.minecraft.core.NonNullList.withSize(ENCODER_SLOTS, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("encoder"), enc, registries);
        mode = Math.floorMod(tag.getInt("encoder_mode"), MODES);
        stepKind = Math.floorMod(tag.getInt("step_kind"), ProcedureStep.KINDS);
        procSteps.clear();
        for (Tag t : tag.getList("proc_steps", Tag.TAG_COMPOUND)) {
            if (procSteps.size() < RecipeData.MAX_STEPS) {
                procSteps.add(ProcedureStep.load((CompoundTag) t, registries));
            }
        }
        for (int i = 0; i < ENCODER_SLOTS; i++) {
            encoder.setItem(i, enc.get(i));
        }
        net.minecraft.core.NonNullList<ItemStack> box = net.minecraft.core.NonNullList.withSize(OUTBOX_SLOTS, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("outbox"), box, registries);
        for (int i = 0; i < OUTBOX_SLOTS; i++) {
            outbox.setItem(i, box.get(i));
        }
    }

    private static CompoundTag wrap(Tag t) {
        CompoundTag c = new CompoundTag();
        c.put("p", t);
        return c;
    }
}
