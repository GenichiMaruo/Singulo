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
    /** 経路の決まり（{@link PathRule}）: 4行 × 経由地点・対象範囲・進入禁止ゾーン2つ。 */
    private final ItemStackHandler routes = new ItemStackHandler(PathRule.SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return PathRule.fits(slot % PathRule.COLS, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
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
     * 14〜15 は手順モードの作業位置カード（場所と、採掘の範囲のもう1点）、16 は手順モードの物（見本）、
     * 17 は加工モードの燃料（見本。切れたら取りに行く燃料と、1回に持ってくる数）。
     */
    public static final int GRID = 9, RESULT = 9, CARD = 10, STOCK = 11, EXTRA = 12, EXTRAS = 2, LOC_A = 14, LOC_B = 15, PITEM = 16,
            FUEL = 17, ENCODER_SLOTS = 18;
    public static final int MODE_CRAFTING = 0, MODE_PROCESSING = 1, MODE_PROCEDURE = 2, MODES = 3;
    /** 記録のモードと、手順モードで次に足す手順の種類。 */
    private int mode;
    private int stepKind;
    /** 次に足す手順の数の上限（繁殖・処理）か、レバーの扱い（操作）。 */
    private int stepOption;
    /** 加工モードで記録する、入れる面・取り出す面・燃料の面（{@link RecipeData#faces}）。 */
    private int faces = RecipeData.DEFAULT_FACES;
    /** 作業台モードで、同じ種類の別の物も使うレシピとして記録するか。 */
    private boolean variants;
    /** 手順モードで並べた手順。 */
    private final List<ProcedureStep> procSteps = new ArrayList<>();
    private final net.minecraft.world.inventory.ContainerData modeData = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? mode : index == 1 ? stepKind : index == 2 ? stepOption : index == 3 ? faces : variants ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                mode = value;
            } else if (index == 1) {
                stepKind = value;
            } else if (index == 2) {
                stepOption = value;
            } else if (index == 3) {
                faces = value;
            } else {
                variants = value != 0;
            }
        }

        @Override
        public int getCount() {
            return 5;
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
    /** 渡している作業。途中の消費と再割当てが重ならないよう保存する。 */
    private final List<WorkJob.Lease> leases = new ArrayList<>();
    /** 登録した注文。 */
    private final List<StandingOrder> standing = new ArrayList<>();
    private int nextStandingId = 1;
    public static final int MAX_STANDING = 16;
    /** レッドストーン信号が入っているか。進行中の注文があるか（コンパレーターの出力）。 */
    private boolean powered;
    private boolean outputOn;
    private int nextJobId = 1;
    private boolean restoredLeases;
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

    public ItemStackHandler routes() {
        return routes;
    }

    /** 経路の決まり（作業のアンドロイドが従う）。 */
    public List<PathRule> pathRules() {
        return level == null ? List.of() : PathRule.read(routes, 0, level);
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
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : supplyPoints()) {
            LiquidStores.at(level, p, seen).addTo(out);       // 液体・化学物質（Mekanism）も、量で数える
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

    /**
     * このアンドロイドが、ほかの指令台の作業中か。この指令台の注文なら、いくつでも受け持てる（今の注文に今できることがなければ、
     * ほかの注文の作業をする）。
     */
    public boolean busy(RuinAndroid a) {
        return a.workConsole() != null && !worldPosition.equals(a.workConsole());
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
        boolean felling = plan.steps.stream().anyMatch(s -> s.entry().recipe().needsFelling());
        List<RuinAndroid> chosen = new ArrayList<>();
        for (RuinAndroid a : candidates(player)) {
            if ((androids.isEmpty() || androids.contains(a.getUUID())) && !busy(a)
                    && (!androids.isEmpty() || (!mining || a.canMine()) && (!felling || a.canFell()))) {
                chosen.add(a);
            }
        }
        if (chosen.isEmpty()) {
            return Component.translatable("gui.singulo.work_console.error.no_android");
        }
        WorkOrder order = WorkOrder.create(nextOrderId++, plan, player.getUUID());
        order.toStock = toStock || FluidSamples.liquid(target);
        order.destination = destination != null && !destination.equals(worldPosition) && deliveryPort(destination) != null ? destination : null;
        // どの工程も、選んだアンドロイドのだれかが一度に運べるか
        for (WorkOrder.StepState s : order.steps) {
            boolean fits = false;
            for (RuinAndroid a : chosen) {
                fits |= maxCrafts(s.recipe, a.isWorking() ? a.carryCapacity() : a.freeCarry(), 1) >= 1 && tankLimit(a, s.recipe) >= 1;
            }
            if (!fits) {
                return Component.translatable("gui.singulo.work_console.error.capacity", s.recipe.output().getHoverName());
            }
            if (!s.recipe.processing() && !s.recipe.procedure() && !isWorkbench(s.block)) {
                return Component.translatable("gui.singulo.work_console.error.not_workbench", s.recipe.output().getHoverName());
            }
            if (s.recipe.processing() && !isMachine(s.location, s.recipe)) {
                return Component.translatable("gui.singulo.work_console.error.not_machine", s.recipe.output().getHoverName());
            }
            if (s.recipe.processing()) {
                ItemStack refused = MachineWatch.refusedInput(level, s.location, s.recipe);
                if (!refused.isEmpty()) {
                    return Component.translatable("gui.singulo.work_console.error.wrong_face", s.recipe.output().getHoverName(),
                            refused.getHoverName(), faceName(s.recipe.inputFaceKey()));
                }
            }
            if (s.recipe.needsMining() && chosen.stream().noneMatch(RuinAndroid::canMine)) {
                return Component.translatable("gui.singulo.work_console.error.no_miner", s.recipe.output().getHoverName());
            }
            if (s.recipe.needsFelling() && chosen.stream().noneMatch(RuinAndroid::canFell)) {
                return Component.translatable("gui.singulo.work_console.error.no_feller", s.recipe.output().getHoverName());
            }
        }
        reserve(order, plan);
        orders.add(order);
        for (RuinAndroid a : chosen) {
            order.workers.add(a.getUUID());
            if (order(a.workOrder()) == null || !worldPosition.equals(a.workConsole())) {
                a.assignWork(worldPosition, order.id);     // 今の注文の作業中なら、それを続けてから（手が空いたら）こちらへ
            }
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
            Player actor = actorFor(level, s.registrant);
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
    private boolean isMachine(BlockPos pos, RecipeData recipe) {
        return level != null && MachineWatch.handlesInput(level, pos, recipe) && MachineWatch.handlesOutput(level, pos, recipe);
    }

    /** その面から、物か流体を出し入れできるか。 */
    private boolean handles(BlockPos pos, net.minecraft.core.Direction face) {
        return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, face) != null
                || LiquidStores.handles(level, pos, face);
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
            stacks += stacks(recipe.output(), (long) recipe.outputCount() * done);
            peak = Math.max(peak, stacks);
        }
        // ほかに出てくる物（副産物、採掘で出るほかの物）の分も空けておく
        int extra = recipe.extras().size() + (recipe.needsMining() ? 2 : 0);
        return peak + extra + (crafts > 256 ? 1 : 0);      // 間引いて数えたときは、1つ余裕を見る
    }

    private static int stacks(ItemStack item, long count) {
        if (FluidSamples.liquid(item)) {
            return 0;                                  // 液体・化学物質は持ち物ではなく、タンクに入る
        }
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

    /**
     * 1回分の液体・化学物質（入れる物・出てくる物）を、アンドロイドのタンクで何回分運べるか（どちらもなければ上限なし）。
     * 液体は液体のタンク、化学物質は化学物質のタンク（Mekanism）に、それぞれ2種類まで。
     */
    static long tankLimit(RuinAndroid a, RecipeData recipe) {
        List<ItemCount> fluids = new ArrayList<>(), chems = new ArrayList<>();
        for (ItemCount in : recipe.inputs()) {
            if (FluidSamples.liquid(in.item)) {
                ItemCount.add(ChemicalSamples.is(in.item) ? chems : fluids, in.item, in.count);
            }
        }
        for (ItemStack out : recipe.outputs()) {
            if (FluidSamples.liquid(out)) {
                ItemCount.add(ChemicalSamples.is(out) ? chems : fluids, FluidSamples.key(out), FluidSamples.amount(out));
            }
        }
        if (fluids.isEmpty() && chems.isEmpty()) {
            return Long.MAX_VALUE;
        }
        int fluidCap = a.fluidCapacity(), chemCap = a.chemCapacity();
        if (!fluids.isEmpty() && (fluidCap <= 0 || fluids.size() > RuinAndroid.TANKS)
                || !chems.isEmpty() && (chemCap <= 0 || chems.size() > RuinAndroid.TANKS)) {
            return 0;
        }
        long k = Long.MAX_VALUE;
        for (ItemCount f : fluids) {
            k = Math.min(k, fluidCap / Math.max(1, f.count));
        }
        for (ItemCount c : chems) {
            k = Math.min(k, chemCap / Math.max(1, c.count));
        }
        return k;
    }

    /** このアンドロイドにできる工程か（採掘の手順は、採掘アップグレードが要る）。 */
    private static boolean canDo(RuinAndroid a, RecipeData recipe) {
        return (!recipe.needsMining() || a.canMine()) && (!recipe.needsFelling() || a.canFell()) && tankLimit(a, recipe) > 0;
    }

    /** 次の作業をもらう。 */
    public WorkJob requestJob(RuinAndroid a) {
        return requestJob(a, false);
    }

    /**
     * 次の作業をもらう。今の注文に今できることがなければ、このアンドロイドが受け持つほかの注文の作業をもらう（注文を移る）。
     * carrying なら、持ち物を持ったまま次を決める（素材を取りに行く作業だけ。最初の受け渡し場所で持ち物を戻してから取る）。
     */
    public WorkJob requestJob(RuinAndroid a, boolean carrying) {
        WorkOrder o = order(a.workOrder());
        if (o == null) {
            if (!carrying && !a.carryEmpty()) {
                return WorkJob.simple(WorkJob.Type.DEPOSIT, a.workOrder());     // 終わった注文の残りを戻してから
            }
            o = nextOrder(a);
            if (o == null) {
                return WorkJob.simple(WorkJob.Type.EXIT, a.workOrder());
            }
            a.switchOrder(o.id);
        }
        o.workers.add(a.getUUID());                     // 印刷し直されたアンドロイドも、続きを受け持つ
        if (!carrying && !a.carryEmpty()) {
            return WorkJob.simple(WorkJob.Type.DEPOSIT, o.id);
        }
        WorkJob job = jobIn(o, a, carrying);
        if (job.type() != WorkJob.Type.WAIT) {
            return job;
        }
        // この注文に今できることがない（装置の加工を待っているなど）: 受け持っているほかの注文の作業をする
        for (WorkOrder other : orders) {
            if (other != o && other.workers.contains(a.getUUID())) {
                WorkJob j = jobIn(other, a, carrying);
                if (j.type() != WorkJob.Type.WAIT) {
                    a.switchOrder(other.id);
                    return j;
                }
            }
        }
        return job;
    }

    /** このアンドロイドが受け持つ、まだ終わっていない注文（なければ null）。 */
    @Nullable
    private WorkOrder nextOrder(RuinAndroid a) {
        for (WorkOrder o : orders) {
            if (o.workers.contains(a.getUUID())) {
                return o;
            }
        }
        return null;
    }

    /** 注文 o の中で、次の作業を決める（なければ WAIT）。 */
    private WorkJob jobIn(WorkOrder o, RuinAndroid a, boolean carrying) {
        long now = level.getGameTime();
        // 加工を待っている装置で、できた物（取りに行ってよい物）があれば、先に取り出す（装置を空けて、あとの工程を進める）
        if (!carrying) {
            for (int i = 0; i < o.steps.size(); i++) {
                WorkOrder.StepState s = o.steps.get(i);
                if (s.parked <= 0) {
                    continue;
                }
                if (s.parkedOut <= 0) {
                    s.done += s.parked;                 // 取り出す物が残っていない
                    s.parked = 0;
                    setChanged();
                    continue;
                }
                if (s.collectReady && !locationBusy(s.location, s) && canDo(a, s.recipe)) {
                    s.collectReady = false;
                    return lease(new WorkJob(WorkJob.Type.COLLECT, o.id, nextJobId++, s.parkedOut, List.of(),
                            List.of(new WorkJob.Batch(i, s.parked, s.location, s.recipe)), true), a);
                }
            }
        }
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
        // 1工程ずつ: できる工程のうち、作業場所が一番近いもの
        boolean tooSmall = false;
        int best = -1;
        long bestK = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < o.steps.size(); i++) {
            WorkOrder.StepState s = o.steps.get(i);
            if (s.remaining() <= 0 || !o.ready(s) || locationBusy(s.location, s) || !canDo(a, s.recipe) || fuelBlocked(s)) {
                continue;
            }
            long k = Math.min(maxCrafts(s.recipe, slots, s.remaining()), tankLimit(a, s.recipe));
            if (k <= 0) {
                tooSmall = true;
                continue;
            }
            double d = a.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.location));
            if (d < bestDist) {
                best = i;
                bestK = k;
                bestDist = d;
            }
        }
        if (best >= 0) {
            int i = best;
            long k = bestK;
            WorkOrder.StepState s = o.steps.get(i);
            List<ItemCount> fetch = new ArrayList<>();
            for (ItemCount in : s.recipe.inputs()) {
                if (in.alt != null) {
                    ItemCount.addAlt(fetch, in.item, in.count * k, in.alt);
                } else {
                    ItemCount.add(fetch, in.item, in.count * k);
                }
            }
            s.inProgress += k;
            return lease(new WorkJob(WorkJob.Type.CRAFT, o.id, nextJobId++, 0, fetch,
                    List.of(new WorkJob.Batch(i, k, s.location, s.recipe)), false), a);
        }
        if (o.stepsFinished() && !carrying) {
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
                long amount = FluidSamples.liquid(o.target) ? Math.min(left, a.liquidSpace(o.target))
                        : Math.min(left, (long) slots * o.target.getMaxStackSize());
                if (amount <= 0) {
                    return WorkJob.simple(WorkJob.Type.WAIT, o.id);          // タンクがない・いっぱい
                }
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
            if (s.remaining() <= 0 || !o.ready(s) || locationBusy(s.location, s) || !canDo(a, s.recipe)) {
                continue;
            }
            // 持ち物の素材で何回作れるか（素材の要らない手順（採掘だけなど）は、空いている場所に収まるだけ）
            long k = s.remaining();
            for (ItemCount in : s.recipe.inputs()) {
                k = Math.min(k, carried(a, in) / in.count);
            }
            if (s.recipe.inputs().isEmpty()) {
                k = maxCrafts(s.recipe, a.freeCarry(), k);
            }
            if (k <= 0) {
                continue;
            }
            List<ItemCount> use = new ArrayList<>();
            for (ItemCount in : s.recipe.inputs()) {
                if (in.alt != null) {
                    ItemCount.addAlt(use, in.item, in.count * k, in.alt);
                } else {
                    ItemCount.add(use, in.item, in.count * k);
                }
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
            if (s.inProgress > 0 || locationBusy(s.location, s) || !canDo(a, s.recipe) || tankLimit(a, s.recipe) != Long.MAX_VALUE
                    || s.recipe.variants()) {
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
            ItemCount.add(inv, b.recipe().outputKey(), (long) b.recipe().outputCount() * b.crafts());
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
            ItemCount.add(during, b.recipe().outputKey(), (long) b.recipe().outputCount() * b.crafts());
            for (ItemCount in : b.recipe().inputs()) {
                if (in.item.hasCraftingRemainingItem()) {
                    ItemCount.add(during, in.item.getCraftingRemainingItem(), in.count * b.crafts());
                }
            }
            peak = Math.max(peak, stacks(during));
            for (ItemCount in : b.recipe().inputs()) {
                ItemCount.add(carry, in.item, -in.count * b.crafts());
            }
            ItemCount.add(carry, b.recipe().outputKey(), (long) b.recipe().outputCount() * b.crafts());
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
    /** 持っている、need に合う物の数（同じ種類の別の物も数える）。 */
    private static long carried(RuinAndroid a, ItemCount need) {
        if (need.alt == null) {
            return carried(a, need.item);
        }
        long n = 0;
        for (int slot : a.carrySlots()) {
            if (need.matches(a.inventory().getItem(slot))) {
                n += a.inventory().getItem(slot).getCount();
            }
        }
        return n;
    }

    private static long carried(RuinAndroid a, ItemStack like) {
        if (FluidSamples.liquid(like)) {
            return a.liquidAmount(like);
        }
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
        setChanged();
        WorkOrder o = order(job.orderId());
        if (o != null && o.status == WorkOrder.WAIT_CAPACITY) {
            o.status = WorkOrder.RUNNING;
        }
        return job;
    }

    /**
     * 作業場所が使われているか: だれかが作業している（取り出しに行っている）か、ほかの工程（self 以外）が加工を待っている
     * （装置に別の素材を入れると混ざるので）。
     */
    private boolean locationBusy(BlockPos pos, @Nullable WorkOrder.StepState self) {
        for (WorkOrder o : orders) {
            for (WorkOrder.StepState s : o.steps) {
                if (s != self && s.parked > 0 && pos.equals(s.location)) {
                    return true;
                }
            }
        }
        for (WorkJob.Lease l : leases) {
            if (l.job.type() == WorkJob.Type.CRAFT || l.job.type() == WorkJob.Type.COLLECT) {
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
        if (!l.worker.equals(a.getUUID())) {
            l.worker = a.getUUID();
            setChanged();
        }
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

    /** 受け渡し場所 point の隣の、流体の入れ物（タンクなど）。 */
    public List<net.neoforged.neoforge.fluids.capability.IFluidHandler> fluidStoresAt(BlockPos point, Set<BlockPos> seen) {
        List<net.neoforged.neoforge.fluids.capability.IFluidHandler> out = new ArrayList<>();
        SupplyPortBlockEntity.collectFluids(level, point, seen, out);
        return out;
    }

    /** 流体の入れ物にある、この流体の量（mB）。 */
    private static long fluidIn(List<net.neoforged.neoforge.fluids.capability.IFluidHandler> stores, net.neoforged.neoforge.fluids.FluidStack like) {
        long n = 0;
        for (var h : stores) {
            for (int t = 0; t < h.getTanks(); t++) {
                if (net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(h.getFluidInTank(t), like)) {
                    n += h.getFluidInTank(t).getAmount();
                }
            }
        }
        return n;
    }

    /** 受け渡し場所 point にある、この物（流体の見本なら流体）の数。 */
    private long countAt(BlockPos point, ItemStack item, List<IItemHandler> stores) {
        if (FluidSamples.liquid(item)) {
            return LiquidStores.at(level, point).amount(item);
        }
        long here = 0;
        for (IItemHandler h : stores) {
            for (int s = 0; s < h.getSlots(); s++) {
                if (ItemStack.isSameItemSameComponents(h.getStackInSlot(s), item)) {
                    here += h.getStackInSlot(s).getCount();
                }
            }
        }
        return here;
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
            if (c.alt != null) {
                ItemCount.addAlt(left, c.item, c.count, c.alt);
            } else {
                ItemCount.add(left, c.item, c.count);
            }
        }
        List<Pickup> out = new ArrayList<>();
        for (BlockPos p : points) {
            List<ItemCount> here = new ArrayList<>();
            for (ItemCount need : left) {
                if (need.alt != null) {
                    // 同じ種類の別の物: ここに予約した、合う物それぞれから
                    for (WorkOrder.Holding h : o.held) {
                        if (need.count <= 0) {
                            break;
                        }
                        if (h.point.equals(p) && need.matches(h.item)) {
                            long take = Math.min(need.count, h.count - ItemCount.get(here, h.item));
                            if (take > 0) {
                                need.count -= take;
                                ItemCount.add(here, h.item, take);
                            }
                        }
                    }
                    continue;
                }
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
        // 回る順: 今いる所から一番近い場所、そこから一番近い場所…（行ったり来たりしない）
        List<Pickup> route = new ArrayList<>();
        BlockPos at = from;
        while (!out.isEmpty()) {
            Pickup next = out.get(0);
            for (Pickup p : out) {
                if (p.point().distSqr(at) < next.point().distSqr(at)) {
                    next = p;
                }
            }
            out.remove(next);
            route.add(next);
            at = next.point();
        }
        return route;
    }

    /**
     * 注文のときに、使う在庫を受け渡し場所ごとに予約する（その時そこにあって、ほかの注文が予約していない物だけ）。
     * 同じストレージ（2つの場所に挟まれた箱など）は、先に数えた場所の物として扱う。
     */
    private void reserve(WorkOrder order, WorkPlanner.Plan plan) {
        reserveItems(order, plan.fromStock);
    }

    /** need を、受け渡し場所の在庫（ほかの注文の予約を除く）から予約する。予約しきれなかった物を返す。 */
    private List<ItemCount> reserveItems(WorkOrder order, List<ItemCount> need) {
        List<ItemCount> short_ = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> points = supplyPoints();
        List<List<IItemHandler>> stores = new ArrayList<>();
        for (BlockPos p : points) {
            stores.add(storagesAt(p, seen));
        }
        for (ItemCount c : need) {
            long left = c.count;
            if (c.alt != null) {
                // 同じ種類の別の物も使う: 受け渡し場所ごとに、合う物そのものを予約する
                for (int i = 0; i < points.size() && left > 0; i++) {
                    for (ItemCount have : itemsAt(stores.get(i), c)) {
                        long here = have.count;
                        for (WorkOrder other : orders) {
                            here -= other.held(points.get(i), have.item);
                        }
                        long take = Math.min(left, Math.max(0, here));
                        order.hold(points.get(i), have.item, take);
                        left -= take;
                        if (left <= 0) {
                            break;
                        }
                    }
                }
                if (left > 0) {
                    short_.add(new ItemCount(c.item, left, c.alt));
                }
                continue;
            }
            for (int i = 0; i < points.size() && left > 0; i++) {
                long here = countAt(points.get(i), c.item, stores.get(i));
                for (WorkOrder other : orders) {
                    here -= other.held(points.get(i), c.item);
                }
                long take = Math.min(left, Math.max(0, here));
                order.hold(points.get(i), c.item, take);
                left -= take;
            }
            if (left > 0) {
                ItemCount.add(short_, c.item, left);
            }
        }
        return short_;
    }

    /** ストレージにある、need に合う物（物ごとの数）。 */
    private static List<ItemCount> itemsAt(List<IItemHandler> stores, ItemCount need) {
        List<ItemCount> out = new ArrayList<>();
        for (IItemHandler h : stores) {
            for (int s = 0; s < h.getSlots(); s++) {
                ItemStack st = h.getStackInSlot(s);
                if (!st.isEmpty() && need.matches(st)) {
                    ItemCount.add(out, st, st.getCount());
                }
            }
        }
        return out;
    }

    /** 燃料 fuel の、ほかの注文が予約していない在庫がある受け渡し場所のうち、from に一番近い所（なければ null）。 */
    @Nullable
    public BlockPos fuelPoint(ItemStack fuel, BlockPos from) {
        BlockPos best = null;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : supplyPoints()) {
            if (loose(p, fuel, storagesAt(p, seen)) > 0 && (best == null || p.distSqr(from) < best.distSqr(from))) {
                best = p;
            }
        }
        return best;
    }

    /** 受け渡し場所 point にある、どの注文も予約していない item の数。 */
    private long loose(BlockPos point, ItemStack item, List<IItemHandler> stores) {
        long here = 0;
        for (IItemHandler h : stores) {
            for (int s = 0; s < h.getSlots(); s++) {
                if (ItemStack.isSameItemSameComponents(h.getStackInSlot(s), item)) {
                    here += h.getStackInSlot(s).getCount();
                }
            }
        }
        for (WorkOrder o : orders) {
            here -= o.held(point, item);
        }
        return Math.max(0, here);
    }

    /** 受け渡し場所 point から、予約されていない燃料を最大 count 個、持ち物へ取る。取った数。 */
    public int takeFuel(RuinAndroid a, BlockPos point, ItemStack fuel, int count) {
        List<IItemHandler> stores = storagesAt(point, new HashSet<>());
        int want = (int) Math.min(count, loose(point, fuel, stores));
        int took = 0;
        for (IItemHandler h : stores) {
            for (int s = 0; s < h.getSlots() && took < want; s++) {
                if (!ItemStack.isSameItemSameComponents(h.getStackInSlot(s), fuel)) {
                    continue;
                }
                ItemStack got = h.extractItem(s, want - took, false);
                ItemStack rest = a.addToCarry(got);
                took += got.getCount() - rest.getCount();
                if (!rest.isEmpty()) {
                    ItemHandlerHelper.insertItemStacked(h, rest, false);        // 持ち物がいっぱい: 戻す
                    return took;
                }
            }
        }
        return took;
    }

    /**
     * 作った数が足りなかった（確率で出る物）: 足りない数を作る回数を工程に足し、その素材を在庫から予約する（なければ、そろうまで待つ）。
     * 目当ての数が集まるまで、作業を続ける。
     */
    public void reportShort(WorkJob job, int batchIndex, long missingOutputs) {
        WorkOrder o = order(job.orderId());
        if (o == null || batchIndex < 0 || batchIndex >= job.batch().size() || missingOutputs <= 0) {
            return;
        }
        WorkJob.Batch b = job.batch().get(batchIndex);
        if (b.step() >= o.steps.size()) {
            return;
        }
        WorkOrder.StepState s = o.steps.get(b.step());
        int per = s.recipe.outputCount();
        long extra = (missingOutputs + per - 1) / per;
        s.total += extra;
        List<ItemCount> need = new ArrayList<>(o.pendingReserve);
        for (ItemCount in : s.recipe.inputs()) {
            if (in.alt != null) {
                ItemCount.addAlt(need, in.item, in.count * extra, in.alt);
            } else {
                ItemCount.add(need, in.item, in.count * extra);
            }
        }
        o.pendingReserve.clear();
        o.pendingReserve.addAll(reserveItems(o, need));
        if (!o.pendingReserve.isEmpty()) {
            o.status = WorkOrder.WAIT_ITEMS;
        }
        setChanged();
    }

    /**
     * 装置に素材を入れ終えたアンドロイドが、できるのを待たずにほかの作業へ行く。今の工程（batchIndex）は「加工を待っている」
     * （まだ取り出していない主な物 outputsLeft 個）にし、前の工程は終えたことに、あとの工程はまだやっていないことにする。
     * できたら、指令台が取り出しの作業を出す（だれが行ってもよい）。
     */
    public void park(WorkJob job, int batchIndex, long outputsLeft) {
        park(job, batchIndex, -1, outputsLeft);
    }

    /**
     * 今の工程のうち parkedCrafts 回（-1 なら全部）だけを「加工を待っている」にし、残りの回数はまだやっていないことにする
     * （燃料を作るあいだに離れるとき、まだ入れていない分）。outputsLeft が 0 なら、取り出す物は残っていない。
     */
    public void park(WorkJob job, int batchIndex, long parkedCrafts, long outputsLeft) {
        WorkJob.Lease l = lease(job.jobId());
        if (l == null) {
            return;
        }
        leases.remove(l);
        WorkOrder o = order(job.orderId());
        if (o == null) {
            return;
        }
        for (int i = 0; i < job.batch().size(); i++) {
            WorkJob.Batch b = job.batch().get(i);
            if (b.step() >= o.steps.size()) {
                continue;
            }
            WorkOrder.StepState s = o.steps.get(b.step());
            if (job.type() == WorkJob.Type.COLLECT) {
                if (i == batchIndex) {
                    s.parkedOut = Math.max(0, outputsLeft);   // 取り出しの途中で、また待つことにした
                }
            } else {
                s.inProgress = Math.max(0, s.inProgress - b.crafts());
                if (i < batchIndex) {
                    s.done += b.crafts();
                } else if (i == batchIndex) {
                    s.parked += parkedCrafts < 0 ? b.crafts() : Math.min(parkedCrafts, b.crafts());
                    s.parkedOut += Math.max(0, outputsLeft);
                }
            }
            if (i == batchIndex) {
                s.collectReady = false;
                s.watchSig = Long.MIN_VALUE;
                s.watchChanged = level.getGameTime();
            }
        }
        if (o.pendingReserve.isEmpty()) {
            o.status = WorkOrder.RUNNING;
        }
        setChanged();
    }

    /**
     * 燃料が素材置き場になく、作る注文を出したもの（かまどなどが、燃料切れで止まっていて、その燃料を作る注文が進んでいる）。
     * 作れて素材置き場に入るまで、この装置に素材を運ばない（行っても止まったままなので）。
     */
    private boolean fuelBlocked(WorkOrder.StepState s) {
        ItemStack fuel = s.recipe.fuel();
        return !fuel.isEmpty() && s.recipe.processing() && MachineWatch.outOfFuel(level, s.location, s.recipe) && fuelPoint(fuel, s.location) == null
                && makingFuel(fuel);
    }

    /** この燃料を作る注文が進んでいるか。 */
    private boolean makingFuel(ItemStack fuel) {
        for (WorkOrder o : orders) {
            if (ItemStack.isSameItemSameComponents(o.target, FluidSamples.key(fuel))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 燃料が素材置き場にないとき、レシピで作れるなら、燃料を count 個作って素材置き場へ入れる注文を出す（注文 job と同じ注文者・
     * アンドロイドで）。もう作っているなら、それを待つ。出した・待つなら true。作れない（レシピがない・素材が足りない・
     * 燃料を作るのにその燃料が要る・燃料を作る注文そのものが燃料切れ）なら false。
     */
    public boolean orderFuel(WorkJob job, ItemStack fuel, int count) {
        WorkOrder parent = order(job.orderId());
        if (parent == null || count <= 0 || fuel.isEmpty()) {
            return false;
        }
        if (makingFuel(fuel)) {
            return !ItemStack.isSameItemSameComponents(parent.target, FluidSamples.key(fuel));
        }
        WorkPlanner.Plan plan = planToStock(fuel, count);
        if (!plan.ok() || plan.steps.isEmpty()) {
            return false;
        }
        for (WorkPlanner.Step s : plan.steps) {
            if (s.entry().recipe().processing() && ItemStack.isSameItem(s.entry().recipe().fuel(), fuel)) {
                return false;                       // 木炭を焼くのに木炭が要る、のように、始められない
            }
        }
        Component why = start(actorFor(level, parent.orderer), fuel, count, List.copyOf(parent.workers), null, true);
        if (why != null) {
            return false;
        }
        message = Component.translatable("gui.singulo.work_console.fuel_ordered", fuel.getHoverName(), count);
        setChanged();
        return true;
    }

    /** 自動で注文するときの「だれとして」（登録した人・注文した人。いなければ持ち主）。 */
    private Player actorFor(Level level, @Nullable UUID who) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get((ServerLevel) level, new com.mojang.authlib.GameProfile(
                who != null ? who : owner != null ? owner : UUID.nameUUIDFromBytes(new byte[0]), "[Singulo]"));
    }

    /**
     * 装置の加工を待つあいだに、このアンドロイドにできるほかの作業があるか（なければ、装置のそばで待つほうが早い）。
     * job の今の工程（batchIndex）のほかに、受け持つ注文のできる工程・取りに行ける装置・届ける物があれば true。
     */
    public boolean hasOtherWork(RuinAndroid a, WorkJob job, int batchIndex) {
        int current = batchIndex < job.batch().size() ? job.batch().get(batchIndex).step() : -1;
        long now = level.getGameTime();
        for (WorkOrder o : orders) {
            if (o.id != job.orderId() && !o.workers.contains(a.getUUID())) {
                continue;
            }
            for (int i = 0; i < o.steps.size(); i++) {
                WorkOrder.StepState s = o.steps.get(i);
                if (o.id == job.orderId() && i == current) {
                    continue;
                }
                if (s.parked > 0 && s.collectReady && !locationBusy(s.location, s)) {
                    return true;
                }
                boolean waiting = o.status == WorkOrder.WAIT_ITEMS && now < o.retryAt;
                if (!waiting && s.remaining() > 0 && o.ready(s) && !locationBusy(s.location, s) && canDo(a, s.recipe) && !fuelBlocked(s)) {
                    return true;
                }
            }
            if (o.id != job.orderId() && o.stepsFinished() && o.count - o.delivered - o.deliverInProgress > 0) {
                return true;
            }
        }
        return false;
    }

    /** 作業の途中まで（upTo の工程まで）を終えたことにし、そのあとの工程は、まだやっていないことにする。 */
    public void completeUpTo(WorkJob job, int upTo) {
        WorkJob.Lease l = lease(job.jobId());
        if (l == null) {
            return;
        }
        leases.remove(l);
        WorkOrder o = order(job.orderId());
        if (o == null) {
            return;
        }
        for (int i = 0; i < job.batch().size(); i++) {
            WorkJob.Batch b = job.batch().get(i);
            if (b.step() < o.steps.size()) {
                WorkOrder.StepState s = o.steps.get(b.step());
                if (job.type() == WorkJob.Type.COLLECT) {
                    if (i <= upTo) {
                        collected(s, b.crafts(), job.amount());
                    }
                    continue;
                }
                s.inProgress = Math.max(0, s.inProgress - b.crafts());
                if (i <= upTo) {
                    s.done += b.crafts();
                }
            }
        }
        if (o.pendingReserve.isEmpty()) {
            o.status = WorkOrder.RUNNING;
        }
        finishIfDone(o);
        setChanged();
    }

    /** 受け渡し場所で、そこにある物を持ち物へ取り出す（全部そろわなければ取らない）。 */
    public boolean fetchAt(RuinAndroid a, WorkJob job, Pickup pickup) {
        List<IItemHandler> stores = storagesAt(pickup.point(), new HashSet<>());
        LiquidStores liquids = LiquidStores.at(level, pickup.point());
        for (ItemCount need : pickup.items()) {
            if (countAt(pickup.point(), need.item, stores) < need.count) {
                return false;
            }
            if (FluidSamples.liquid(need.item) && a.liquidSpace(need.item) < need.count) {
                return false;                          // タンクに入りきらない
            }
        }
        WorkOrder o = order(job.orderId());
        for (ItemCount need : pickup.items()) {
            if (FluidSamples.liquid(need.item)) {
                // 液体・化学物質: 入れ物から抜いて、タンクへ（入りきる分だけ抜く）
                long room = Math.min(need.count, a.fillLiquid(need.item, need.count, true));
                long got = liquids.drain(need.item, room, false);
                long in = a.fillLiquid(need.item, got, false);
                if (in < got) {
                    liquids.fill(need.item, got - in, false);         // 入りきらなかった分は戻す（ふつうは起きない）
                }
                if (o != null) {
                    o.release(pickup.point(), need.item, in);
                }
                continue;
            }
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
        // タンクの液体・化学物質も、隣の入れ物へ戻す
        LiquidStores liquids = LiquidStores.at(level, point);
        for (var e : a.liquids()) {
            long moved = liquids.fill(e.getKey(), e.getValue(), false);
            if (moved > 0) {
                a.drainLiquid(e.getKey(), moved, false);
                if (o != null) {
                    o.hold(point, e.getKey(), moved);
                }
            }
            all &= moved >= e.getValue();
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
        WorkJob.Lease active = lease(job.jobId());
        if (active == null) {
            return false;
        }
        BlockPos at = destination(job);
        DeliveryPortBlockEntity port = deliveryPort(at);
        WorkOrder o = order(job.orderId());
        if (FluidSamples.liquid(job.fetch().get(0).item)) {
            return deliverFluid(a, job, at, o, active);
        }
        IItemHandler box = o != null && o.toStock ? stockHandler(at) : port != null ? port.handler() : outboxHandler;
        boolean all = true;
        long moved = 0;
        long limit = active.remainingDelivery;
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
        active.remainingDelivery = Math.max(0, active.remainingDelivery - moved);
        all = active.remainingDelivery == 0;
        if (o != null) {
            o.delivered += moved;
            o.deliverInProgress = Math.max(0, o.deliverInProgress - moved);
            o.status = all ? WorkOrder.RUNNING : WorkOrder.WAIT_SPACE;
        }
        setChanged();
        return all;
    }

    /** 液体・化学物質の完成品を、届け先の隣の入れ物へ入れる。全部入れば true。 */
    private boolean deliverFluid(RuinAndroid a, WorkJob job, BlockPos at, @Nullable WorkOrder o, WorkJob.Lease active) {
        ItemStack like = job.fetch().get(0).item;
        long want = Math.min(active.remainingDelivery, a.liquidAmount(like));
        long moved = LiquidStores.at(level, at).fill(like, want, false);
        a.drainLiquid(like, moved, false);
        active.remainingDelivery = Math.max(0, active.remainingDelivery - moved);
        boolean all = active.remainingDelivery == 0;
        if (o != null) {
            o.delivered += moved;
            o.deliverInProgress = Math.max(0, o.deliverInProgress - moved);
            o.status = all ? WorkOrder.RUNNING : WorkOrder.WAIT_SPACE;
        }
        setChanged();
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
        } else if (job.type() == WorkJob.Type.COLLECT) {
            for (WorkJob.Batch b : job.batch()) {
                if (b.step() < o.steps.size()) {
                    collected(o.steps.get(b.step()), b.crafts(), job.amount());
                }
            }
        }
        o.status = WorkOrder.RUNNING;
        finishIfDone(o);
        setChanged();
    }

    /** 加工を待っていた crafts 回分を取り出し終えた。 */
    private static void collected(WorkOrder.StepState s, long crafts, long outputs) {
        long n = Math.min(crafts, s.parked);
        s.parked -= n;
        s.done += n;
        s.parkedOut = s.parked > 0 ? Math.max(0, s.parkedOut - outputs) : 0;
    }

    /** 作業ができなかった。素材が足りなければ少し待ってやり直す。たどり着けなければ注文を取り消す。 */
    public void fail(WorkJob job, Failure why) {
        WorkJob.Lease l = lease(job.jobId());
        if (l != null) {
            leases.remove(l);
            release(l.job, l.remainingDelivery);
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
            case DEFERRED -> { }
            case WRONG_FACE -> {
                RecipeData r = job.batch().stream().map(WorkJob.Batch::recipe).filter(RecipeData::processing).findFirst().orElse(null);
                cancel(o.id, r == null ? null : Component.translatable("gui.singulo.work_console.cancelled.wrong_face",
                        r.output().getHoverName(), faceName(r.inputFaceKey())));
            }
        }
        setChanged();
    }

    /** DEFERRED: 今はやめて、あとでやり直す（ほかの人に回してよい）。WRONG_FACE: 装置が、記録した入れる面から素材を受け付けない。 */
    public enum Failure { MISSING, UNREACHABLE, BAD_RECIPE, STALLED, DEFERRED, WRONG_FACE }

    /** 装置の面の名前（上・下・北…・自動）。 */
    private static Component faceName(String key) {
        return Component.translatable("gui.singulo.recipe_encoder.face." + key);
    }

    /** 取り上げた作業の分を、まだやっていないことにする。 */
    private void release(WorkJob job, long remainingDelivery) {
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
            o.deliverInProgress = Math.max(0, o.deliverInProgress - remainingDelivery);
        }
    }

    private void finishIfDone(WorkOrder o) {
        if (o.finished()) {
            orders.remove(o);
            if (o.orderer != null && level instanceof ServerLevel server) {
                io.github.genichimaruo.singulo.registry.PendingMilestones.award(server.getServer(), o.orderer, "android_order_complete");
            }
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
            for (int i = 0; i < merged.size(); i++) {
                ItemStack m = merged.get(i);
                if (ItemStack.isSameItemSameComponents(FluidSamples.key(m), FluidSamples.key(s))) {
                    merged.set(i, FluidSamples.withAmount(m, (int) (FluidSamples.amount(m) + FluidSamples.amount(s))));
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(FluidSamples.liquid(s) ? s.copy() : s.copyWithCount(Math.min(64, s.getCount())));
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
        if (FluidSamples.liquid(s)) {
            int amount = (int) FluidSamples.amount(s);
            encoder.setItem(index, FluidSamples.withAmount(s, Math.max(1, Math.min(1_000_000, amount + delta * 100))));
        } else if (!s.isEmpty()) {
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
            RecipeData.processing(grid, result.copy(), extras, encoder.getItem(FUEL), faces).write(card, level.registryAccess());
        } else if (variants) {
            RecipeData.crafting(grid, result.copy(), alternatives(grid)).write(card, level.registryAccess());
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
    public void cycleStepKind(boolean back) {
        stepKind = Math.floorMod(stepKind + (back ? -1 : 1), ProcedureStep.KINDS);
        stepOption = ProcedureStep.defaultOption(stepKind);
    }

    /**
     * 手順を足す: 場所は作業位置カード A（採掘なら B があれば、A と B で囲った範囲）、物は見本の欄（採掘で空なら、カードの完成品）。
     * 足せなければ、その理由。
     */
    @Nullable
    /** 同じ種類の別の物も使うかを切り替える（作業台モード）。 */
    public void toggleVariants() {
        variants = !variants;
        setChanged();
    }

    /** 加工モードの燃料を持ってくる量を切り替える（燃料の欄の数ずつ ⇔ 装置に入るだけ）。 */
    public void toggleFuelFill() {
        faces ^= RecipeData.FUEL_FILL;
        setChanged();
    }

    /**
     * 見本の欄ごとの、使ってよい物（作業台のレシピの材料のうち、その欄の物が合い、ほかの物も合うもの）。合う材料がなければ空。
     * 棒のレシピなら、板材の欄はどの木の板材でもよい、となる。
     */
    private List<String> alternatives(List<ItemStack> grid) {
        List<String> out = new ArrayList<>();
        List<net.minecraft.world.item.crafting.Ingredient> ings = List.of();
        if (level instanceof ServerLevel server) {
            net.minecraft.core.NonNullList<ItemStack> g = net.minecraft.core.NonNullList.withSize(GRID, ItemStack.EMPTY);
            for (int i = 0; i < GRID; i++) {
                g.set(i, grid.get(i).copyWithCount(1));
            }
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, g);
            ings = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, server)
                    .map(h -> (List<net.minecraft.world.item.crafting.Ingredient>) h.value().getIngredients()).orElse(List.of());
        }
        for (ItemStack s : grid) {
            String alt = "";
            if (!s.isEmpty()) {
                for (var ing : ings) {
                    if (!ing.isEmpty() && ing.test(s) && ing.getItems().length > 1) {
                        alt = ItemCount.key(ing);
                        break;
                    }
                }
            }
            out.add(alt);
        }
        return out;
    }

    /** 加工モードの面を切り替える（which: 0 入れる面、1 取り出す面、2 燃料の面。どれも「自動」がある）。 */
    public void cycleFace(int which, boolean back) {
        int shift = which * 3;
        int cur = faces >> shift & 7;
        int n = 7;                                           // 0〜5 と 自動（7）
        int idx = cur == RecipeData.FACE_AUTO ? 6 : cur;
        idx = Math.floorMod(idx + (back ? -1 : 1), n);
        int value = idx == 6 ? RecipeData.FACE_AUTO : idx;
        faces = faces & ~(7 << shift) | value << shift;
        setChanged();
    }

    /** 次に足す手順の数の上限（1〜64）・レバーの扱い（切り替え・オン・オフ）を変える。 */
    public void adjustStepOption(int delta) {
        if (stepKind == ProcedureStep.USE) {
            stepOption = Math.floorMod(stepOption + Integer.signum(delta), ProcedureStep.USE_MODES);
        } else if (ProcedureStep.hasOption(stepKind)) {
            stepOption = Math.max(1, Math.min(64, stepOption + delta));
        }
    }

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
        if (ProcedureStep.needsItem(stepKind) && item.isEmpty()) {
            return Component.translatable("gui.singulo.recipe_encoder.step.no_item");
        }
        BlockPos corner = null;
        if (ProcedureStep.usesArea(stepKind)) {
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
        int option = ProcedureStep.hasOption(stepKind) ? stepOption : ProcedureStep.defaultOption(stepKind);
        procSteps.add(new ProcedureStep(stepKind, pos, corner, stepKind == ProcedureStep.USE ? ItemStack.EMPTY : item, option));
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
        if (restoredLeases) {
            for (WorkJob.Lease lease : leases) {
                lease.lastBeat = now;           // 指令台と個体のチャンクの読み込み順に猶予を設ける
            }
            restoredLeases = false;
        }
        // 消費済みの作業は、未読込の個体から取り上げて再実行しない。
        List<Integer> abandoned = new ArrayList<>();
        for (Iterator<WorkJob.Lease> it = leases.iterator(); it.hasNext(); ) {
            WorkJob.Lease l = it.next();
            // 未読込の個体は途中で再割当てしない。復帰・転送後の引継ぎか、画面からの取消を待つ。
            boolean workerLoaded = level.getEntity(l.worker) != null;
            if (order(l.job.orderId()) == null) {
                it.remove();
            } else if (workerLoaded && now - l.lastBeat > LEASE_TIMEOUT) {
                it.remove();
                abandoned.add(l.job.orderId());
            }
        }
        for (int id : abandoned) {
            cancel(id, null);
        }
        for (WorkOrder o : new ArrayList<>(orders)) {
            if (!o.pendingReserve.isEmpty()) {
                List<ItemCount> need = new ArrayList<>(o.pendingReserve);
                o.pendingReserve.clear();
                o.pendingReserve.addAll(reserveItems(o, need));
                o.status = o.pendingReserve.isEmpty() ? WorkOrder.RUNNING : WorkOrder.WAIT_ITEMS;
            }
            // 加工を待っている装置を見て、取り出しに行ってよいかを決める
            for (WorkOrder.StepState s : o.steps) {
                if (s.parked > 0) {
                    MachineWatch.watch(level, s, now, s.recipe.fuel().isEmpty() || fuelPoint(s.recipe.fuel(), s.location) != null);
                }
            }
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
        return new WorkConsoleMenu(id, inventory, worldPosition, permit, encoder, outbox, routes, modeData);
    }

    @Override
    public void onBroken(Level level) {
        Block.popResource(level, worldPosition, permit.getStackInSlot(0));
        for (int i = 0; i < routes.getSlots(); i++) {
            Block.popResource(level, worldPosition, routes.getStackInSlot(i));
        }
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
        tag.put("routes", routes.serializeNBT(registries));
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
        tag.putInt("next_job", nextJobId);
        ListTag jobs = new ListTag();
        for (WorkJob.Lease lease : leases) {
            CompoundTag job = new CompoundTag();
            job.put("job", lease.job.save(registries));
            job.putUUID("worker", lease.worker);
            job.putLong("last_beat", lease.lastBeat);
            job.putLong("remaining_delivery", lease.remainingDelivery);
            jobs.add(job);
        }
        tag.put("leases", jobs);
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
        tag.putInt("step_option", stepOption);
        tag.putInt("faces", faces);
        tag.putBoolean("variants", variants);
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
        CompoundTag rt = tag.getCompound("routes");
        rt.putInt("Size", PathRule.SLOTS);
        routes.deserializeNBT(registries, rt);
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
        nextJobId = Math.max(1, tag.getInt("next_job"));
        leases.clear();
        restoredLeases = true;
        for (Tag element : tag.getList("leases", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) element;
            WorkJob job = WorkJob.load(t.getCompound("job"), registries);
            WorkOrder order = job == null ? null : order(job.orderId());
            if (job == null || order == null || !t.hasUUID("worker")) {
                continue;
            }
            var lease = new WorkJob.Lease(job, t.getUUID("worker"), t.getLong("last_beat"));
            lease.remainingDelivery = Math.max(0, Math.min(job.amount(), t.getLong("remaining_delivery")));
            leases.add(lease);
            nextJobId = Math.max(nextJobId, job.jobId() + 1);
            if (job.type() == WorkJob.Type.CRAFT) {
                for (WorkJob.Batch b : job.batch()) {
                    if (b.step() < order.steps.size()) {
                        order.steps.get(b.step()).inProgress += b.crafts();
                    }
                }
            } else if (job.type() == WorkJob.Type.DELIVER) {
                order.deliverInProgress += lease.remainingDelivery;
            }
        }
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
        stepOption = tag.contains("step_option") ? tag.getInt("step_option") : ProcedureStep.defaultOption(stepKind);
        faces = tag.contains("faces") ? tag.getInt("faces") : RecipeData.DEFAULT_FACES;
        variants = tag.getBoolean("variants");
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
