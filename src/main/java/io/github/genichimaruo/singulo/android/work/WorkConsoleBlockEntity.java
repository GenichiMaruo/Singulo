package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.api.android.AndroidOrders;
import io.github.genichimaruo.singulo.data.MassValues;
import io.github.genichimaruo.singulo.item.ShieldPermitItem;
import io.github.genichimaruo.singulo.item.UsesHelper;
import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoint;
import io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoints;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
public class WorkConsoleBlockEntity extends io.github.genichimaruo.singulo.compat.LegacyBlockEntity implements AbstractMachineBlock.MenuOpener, AbstractMachineBlock.BreakListener,
        MenuProvider, AndroidEyes.Holder {
    final AdjacentItemStores adjacentItems = new AdjacentItemStores(this);

    @Override
    public void setRemoved() {
        adjacentItems.clear();
        super.setRemoved();
    }
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        adjacentItems.clear();
    }

    /** 正面の顔（アンドロイドが使っている間は目を開ける）。 */
    protected final AndroidEyes eyes = new AndroidEyes();

    @Override
    public AndroidEyes eyes() {
        return eyes;
    }

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
    /** 作業台モードで、同じ種類の別の物も使う見本の欄（ビット i が欄 i）。 */
    private int variantMask;
    public static final int ALL_VARIANTS = (1 << GRID) - 1;
    /** 手順モードで並べた手順。 */
    private final List<ProcedureStep> procSteps = new ArrayList<>();
    private final net.minecraft.world.inventory.ContainerData modeData = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? mode : index == 1 ? stepKind : index == 2 ? stepOption : index == 3 ? faces : variantMask;
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
                variantMask = value & ALL_VARIANTS;
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };
    private final List<RecipeData.Tool> encoderTools = new ArrayList<>();
    private boolean encoderGuaranteedExtras;
    private final net.minecraft.world.SimpleContainer encoder = new net.minecraft.world.SimpleContainer(ENCODER_SLOTS) {
        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot <= RESULT || slot >= EXTRA && slot < EXTRA + EXTRAS) encoderGuaranteedExtras = false;
            if (slot == RESULT) encoderTools.clear();
            if (slot < GRID) encoderTools.removeIf(t -> t.slot() == slot);
            super.setItem(slot, stack);
        }

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
    private UUID escrowConsoleIdentity = UUID.randomUUID();
    public UUID escrowConsoleIdentity() { return escrowConsoleIdentity; }
    private AndroidEscrowData.@Nullable Entry escrowEntry(WorkOrder order) {
        if (order == null || order.escrowRequest == null || !(level instanceof ServerLevel server)) return null;
        var e = AndroidEscrowData.get(server).find(order.escrowRequest);
        return e != null && e.consoleIdentity.equals(escrowConsoleIdentity) && e.console.equals(worldPosition)
                && java.util.Objects.equals(e.owner, order.orderer) ? e : null;
    }
    public boolean escrowReturning(RuinAndroid a) {
        if (a.escrowRequest() == null || !(level instanceof ServerLevel server)) return false;
        var e = AndroidEscrowData.get(server).find(a.escrowRequest());
        return e == null || e.state != AndroidEscrowData.State.RUNNING || !e.consoleIdentity.equals(escrowConsoleIdentity)
                || !java.util.Objects.equals(e.worker, a.getUUID()) || !java.util.Objects.equals(e.owner, owner()) || order(e.orderId) == null;
    }
    public List<BlockPos> returnPoints(RuinAndroid a, WorkJob job) {
        return a.escrowRequest() == null ? supplyPoints() : new ArrayList<>(List.of(worldPosition));
    }
    public io.github.genichimaruo.singulo.api.android.AndroidEscrows.Submission submitEscrowed(
            UUID actor, UUID request, ItemStack target, long count, List<ItemStack> offered, @Nullable UUID worker) {
        var denied = new io.github.genichimaruo.singulo.api.android.AndroidEscrows.Submission(false,
                AndroidOrders.result(request, AndroidOrders.State.REJECTED, "escrow_request_rejected"));
        if (!externalAllowed(actor) || !(level instanceof ServerLevel server) || target.isEmpty()
                || count < 1 || count > MAX_COUNT || offered.stream().anyMatch(java.util.Objects::isNull)) return denied;
        var data = AndroidEscrowData.get(server); var previous = data.find(request);
        if (data.isKnown(request)) {
            if (previous == null || !java.util.Objects.equals(previous.requestedWorker, worker)
                    || !data.matches(previous, actor, target, count, offered, worldPosition, escrowConsoleIdentity)) return denied;
            return new io.github.genichimaruo.singulo.api.android.AndroidEscrows.Submission(false,
                    new AndroidOrders.Receipt(request, previous.outcome, previous.orderId, previous.count, previous.delivered, null));
        }
        if (externalOrders.containsKey(request) || externalOrders.size() >= AndroidOrders.MAX_RECEIPTS) return denied;
        var entry = data.create(request, actor, worldPosition, escrowConsoleIdentity, target, count, offered);
        if (entry == null) return denied;
        entry.requestedWorker = worker;
        data.setDirty();
        List<ItemCount> supplied = new ArrayList<>();
        for (ItemStack s : offered) ItemCount.add(supplied, FluidSamples.liquid(s) ? FluidSamples.key(s) : s, FluidSamples.amount(s));
        int id = nextOrderId;
        Component why;
        try {
            why = start(actorFor(level, actor), target.copy(), count, worker == null ? List.of() : List.of(worker), null,
                    false, false, true, supplied, null, request);
        } catch (RuntimeException failure) {
            // Before binding, no ownership transferred and the caller keeps its offer.
            if (data.discardPreparing(request)) throw failure;
            // Binding commits custody. Report acceptance with a recoverable failure rather
            // than throw an ambiguous rejection while retaining the offered physical items.
            // start only reserves metadata/assigns workers; it never runs pickup or crafting.
            data.markTerminal(entry, AndroidOrders.State.FAILED, entry.delivered);
            orders.removeIf(o -> o.id == entry.orderId && request.equals(o.escrowRequest));
            leases.removeIf(l -> l.job.orderId() == entry.orderId);
            UUID boundWorker = entry.worker;
            try {
                var entity = boundWorker == null ? null : server.getEntity(boundWorker);
                if (entity instanceof RuinAndroid android && request.equals(android.escrowRequest())) {
                    android.clearWork(); // Return any actual private cargo before clearing binding.
                } else {
                    if (entity instanceof RuinAndroid android && worldPosition.equals(android.workConsole())
                            && android.workOrder() == entry.orderId && android.escrowRequest() == null) android.clearWork();
                    data.workerReturned(entry, boundWorker);
                }
            } catch (RuntimeException cleanupFailure) {
                // Keep RETURNING custody if cleanup itself fails; never discard retained items.
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            io.github.genichimaruo.singulo.Singulo.LOGGER.error("Escrow start failed after custody acceptance for {}", request, failure);
            var failed = new ExternalOrderLedger(request, actor, entry.orderId, target, count,
                    worker == null ? List.of() : List.of(worker), null);
            failed.state = AndroidOrders.State.FAILED;
            failed.reason = "escrow_start_failed_recover_materials";
            externalOrders.put(request, failed); setChanged();
            return new io.github.genichimaruo.singulo.api.android.AndroidEscrows.Submission(true, failed.receipt());
        }
        if (why != null) {
            data.discardPreparing(request);
            return new io.github.genichimaruo.singulo.api.android.AndroidEscrows.Submission(false,
                    AndroidOrders.result(request, AndroidOrders.State.REJECTED, why.getString()));
        }
        var receipt = new ExternalOrderLedger(request, actor, id, target, count, worker == null ? List.of() : List.of(worker), null);
        externalOrders.put(request, receipt); setChanged();
        return new io.github.genichimaruo.singulo.api.android.AndroidEscrows.Submission(true, receipt.receipt());
    }
    private record EndpointLink(UUID identity, UUID owner, @Nullable Direction face) { }
    private final java.util.Map<BlockPos, EndpointLink> endpointLinks = new java.util.LinkedHashMap<>();

    public AndroidWorkEndpoints.Result linkExternalEndpoint(UUID actor, BlockPos pos) {
        return linkExternalEndpoint(actor, pos, null);
    }
    public AndroidWorkEndpoints.Result linkExternalEndpoint(UUID actor, BlockPos pos, @Nullable Direction face) {
        if (!externalAllowed(actor) || pos.equals(worldPosition) || !pos.closerThan(worldPosition, LINK_RANGE))
            return AndroidWorkEndpoints.Result.REJECTED;
        if (!level.isLoaded(pos)) return AndroidWorkEndpoints.Result.UNAVAILABLE;
        var e = endpointAt(pos, face);
        if (e == null || e.endpointId() == null
                || !actor.equals(e.endpointOwner())) return AndroidWorkEndpoints.Result.REJECTED;
        EndpointLink wanted = new EndpointLink(e.endpointId(), actor, face);
        if (wanted.equals(endpointLinks.get(pos))) return AndroidWorkEndpoints.Result.LINKED;
        if (!orders.isEmpty()) return AndroidWorkEndpoints.Result.BUSY;
        if (endpointLinks.size() >= 64 && !endpointLinks.containsKey(pos)) return AndroidWorkEndpoints.Result.REJECTED;
        endpointLinks.put(pos.immutable(), wanted); setChanged();
        return AndroidWorkEndpoints.Result.LINKED;
    }
    public AndroidWorkEndpoints.Result unlinkExternalEndpoint(UUID actor, BlockPos pos) {
        if (!externalAllowed(actor)) return AndroidWorkEndpoints.Result.REJECTED;
        if (!orders.isEmpty()) return AndroidWorkEndpoints.Result.BUSY;
        endpointLinks.remove(pos); setChanged(); return AndroidWorkEndpoints.Result.UNLINKED;
    }
    @Nullable public AndroidWorkEndpoint workEndpoint(BlockPos pos) {
        var link = endpointLinks.get(pos);
        if (link == null || !java.util.Objects.equals(owner(), link.owner()) || level == null || !level.isLoaded(pos)) return null;
        var e = endpointAt(pos, link.face());
        return e != null && link.identity().equals(e.endpointId()) && link.owner().equals(e.endpointOwner()) ? e : null;
    }
    @Nullable private AndroidWorkEndpoint endpointAt(BlockPos pos, @Nullable Direction face) {
        var be = level.getBlockEntity(pos);
        if (be != null && be.isRemoved()) return null;
        if (face == null && be instanceof AndroidWorkEndpoint endpoint) return endpoint;
        return level.getCapability(AndroidWorkEndpoint.CAPABILITY, pos, face);
    }
    boolean hasEndpointLink(BlockPos pos) { return endpointLinks.containsKey(pos); }

    private boolean validDelivery(BlockPos pos, ItemStack target) {
        var e = workEndpoint(pos);
        if (e == null) return deliveryPort(pos) != null;
        if (ChemicalSamples.is(target)) return e.deliveryChemicals() != null;
        return FluidSamples.is(target) ? e.deliveryFluids() != null : e.deliveryItems() != null;
    }
    private final List<WorkOrder> orders = new ArrayList<>();
    private int nextOrderId = 1;
    private final java.util.Map<UUID, ExternalOrderLedger> externalOrders = new java.util.LinkedHashMap<>();
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
    private boolean restoreSupplyClaims;
    private boolean migrateSupplyClaims;
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

    /** 許可証にだれかが登録されていれば、持ち主と登録した人と、持ち主と同じチームの人だけ。なければだれでも。 */
    public boolean mayOperate(Player player) {
        List<ShieldPermitItem.Member> members = ShieldPermitItem.members(permit.getStackInSlot(0));
        if (members.isEmpty() || owner == null || owner.equals(player.getUUID()) || io.github.genichimaruo.singulo.item.TeamSharing.sameTeam(level, owner, player)) {
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
        if (endpointLinks.containsKey(pos)) {
            if (orders.isEmpty()) { endpointLinks.remove(pos); setChanged(); }
            return;
        }
        if (level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WorkLinkedBlockEntity b && worldPosition.equals(b.console())) {
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
        var out = new ArrayList<>(links); out.addAll(endpointLinks.keySet()); return out;
    }

    public boolean hasWorkshopLink(BlockPos pos) { return links.contains(pos); }

    // ------------------------------------------------------------------ 在庫・レシピ

    /** 素材置き場: 指令台と資材口の隣のストレージ。 */
    public List<IItemHandler> storages() {
        List<IItemHandler> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        SupplyPortBlockEntity.collect(level, worldPosition, seen, out);
        for (SupplyPortBlockEntity port : linked(SupplyPortBlockEntity.class)) {
            port.storages(level, seen, out);
        }
        for (BlockPos p : endpointLinks.keySet()) {
            var e = workEndpoint(p);
            if (e != null && e.supplyItems() != null) out.add(e.supplyItems());
        }
        return out;
    }

    /** 素材置き場の中身の合計（物ごとに、それがある資材口の取り出しの優先度も覚える）。 */
    public List<ItemCount> stock() {
        List<ItemCount> out = new ArrayList<>();
        List<BlockPos> points = supplyPoints();
        for (var source : ItemSupplyStores.sources(this, points)) {
            IItemHandler h = source.handler();
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty() || io.github.genichimaruo.singulo.wormhole.UnstableMouthItem.expired(s, level)) continue;
                ItemCount.addRanked(out, s, s.getCount(), priority(source.point(), s, true));
            }
        }
        for (var sources : LiquidStores.supplies(this, points).values()) {
            for (var source : sources) ItemCount.add(out, source.material().item, source.material().count);
        }
        return out;
    }

    /** ほかの注文が予約した分を引いた在庫。 */
    public List<ItemCount> available() {
        return available(stock());
    }

    private List<ItemCount> available(List<ItemCount> stock) {
        syncSupplyClaims();
        var foreign = foreignClaims(null, stock);
        var owned = new java.util.HashMap<WorkPlanner.Key, Long>();
        for (var order : orders) if (order.escrowRequest == null)
            for (var h : order.held) owned.merge(WorkPlanner.Key.of(h.item), h.count, Long::sum);
        List<ItemCount> out = new ArrayList<>();
        for (ItemCount actual : stock) {
            ItemCount c = new ItemCount(actual.item, actual.count);
            c.takePriority = actual.takePriority;
            var key = WorkPlanner.Key.of(c.item);
            c.count -= foreign.getOrDefault(key, 0L);
            if (WormholeOrders.pattern(c.item)) {
                for (var o : orders) if (o.escrowRequest == null) c.count -= o.outstanding(c.item);
            } else c.count -= owned.getOrDefault(key, 0L);
            c.count = Math.max(0, c.count);
            out.add(c);
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

    /** その場所でそのレシピを作っている: レシピが並んでいる棚の顔が目を開ける（場所と完成品で見分ける）。 */
    public void wakeRacks(BlockPos location, RecipeData recipe) {
        List<RecipeRackBlockEntity.Entry> entries = new ArrayList<>();
        for (RecipeRackBlockEntity rack : linked(RecipeRackBlockEntity.class)) {
            entries.clear();
            rack.entries(level, entries);
            for (RecipeRackBlockEntity.Entry e : entries) {
                if (e.location().equals(location) && (ItemStack.isSameItemSameComponents(e.recipe().output(), recipe.output())
                        || e.recipe().extras().stream().anyMatch(extra -> ItemStack.isSameItemSameComponents(extra, recipe.output())))) {
                    AndroidEyes.wake(level, rack.getBlockPos());
                    break;
                }
            }
        }
    }

    public WorkPlanner.Plan plan(ItemStack target, long count) {
        return plan(target, count, false);
    }

    /** 計算する。fresh なら、完成品そのものの在庫は使わず、count 個を新しく作る（{@link #plan(ItemStack, long, boolean)}）。 */
    public WorkPlanner.Plan plan(ItemStack target, long count, boolean fresh) {
        return plan(target, count, fresh, stock());
    }

    /**
     * 作って増やす計算（在庫を保つ注文と、「新しく作る」注文）: 完成品そのものの在庫は使わない
     * （使うと、作らずに在庫を予約するだけで終わり、新しく増えない。例: 鉄の斧が3本あって、あと2本ほしいとき）。
     */
    private WorkPlanner.Plan plan(ItemStack target, long count, boolean fresh, List<ItemCount> actual) {
        List<ItemCount> stock = available(actual);
        if (fresh) stock.removeIf(c -> WormholeOrders.matches(target, c.item));
        return WorkPlanner.plan(target, Math.max(1, Math.min(MAX_COUNT, count)), recipes(), stock, level);
    }

    private record WorkshopPlan(WorkPlanner.Plan plan, @Nullable RuinAndroid tankWorker) {}

    public WorkPlanner.Plan preview(Player player, ItemStack target, long count, boolean fresh) {
        return workshopPlan(player, List.of(), target, count, fresh, stock()).plan();
    }

    /** Material preview for a fresh public order, including the selected workers' tanks. */
    public WorkPlanner.Plan previewExternalOrder(UUID actor, List<UUID> workers, ItemStack target, long count) {
        return workshopPlan(actorFor(level, actor), workers, target, count, true, stock()).plan();
    }

    /** Try each idle worker with its own tanks. Materials on different workers are never pooled. */
    private WorkshopPlan workshopPlan(Player player, List<UUID> ids, ItemStack target, long count, boolean fresh, List<ItemCount> actual) {
        var shared = plan(target, count, fresh, actual);
        var ordinary = new WorkshopPlan(shared, null);
        if (shared.ok() || shared.limited || FluidSamples.liquid(target)) return ordinary;
        var missingFluids = new ArrayList<ItemStack>();
        for (ItemCount missing : shared.missing) {
            var sample = FluidSamples.is(missing.item) ? missing.item : BucketCrafting.fluid(missing.item);
            if (!sample.isEmpty()) missingFluids.add(FluidSamples.key(sample));
        }
        if (missingFluids.isEmpty()) {
            // Making a filled bucket may itself have failed for lack of an empty bucket, for example.
            for (var step : shared.steps) {
                if (RecipeData.MODE_CRAFTING.equals(step.entry().recipe().mode())) {
                    for (var slot : step.entry().recipe().grid()) {
                        var fluid = BucketCrafting.fluid(slot);
                        if (!fluid.isEmpty()) missingFluids.add(FluidSamples.key(fluid));
                    }
                }
            }
        }
        if (missingFluids.isEmpty()) return ordinary;
        var recipes = recipes();
        for (RuinAndroid worker : candidates(player)) {
            if (worker.workConsole() != null || worker.escrowRequest() != null || !ids.isEmpty() && !ids.contains(worker.getUUID())
                    || missingFluids.stream().noneMatch(f -> worker.liquidAmount(f) > 0)) continue;
            var stock = available(actual);
            if (fresh) stock.removeIf(c -> WormholeOrders.matches(target, c.item));
            for (var held : worker.liquids()) {
                if (FluidSamples.is(held.getKey())) ItemCount.add(stock, held.getKey(), held.getValue());
            }
            var combined = WorkPlanner.plan(target, shared.count, recipes, stock, level);
            if (combined.ok() && combined.steps.stream().allMatch(s -> canExecuteStep(worker, s.entry().recipe()))) {
                for (ItemCount in : combined.fromStock) {
                    if (FluidSamples.is(in.item)) {
                        long onboard = Math.min(in.count, worker.liquidAmount(in.item));
                        ItemCount.add(combined.fromWorker, in.item, onboard);
                        in.count -= onboard;
                    }
                }
                combined.fromStock.removeIf(in -> in.count <= 0);
                return new WorkshopPlan(combined, worker);
            }
        }
        return ordinary;
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
        return a.workConsole() != null && (!worldPosition.equals(a.workConsole()) || a.usesPersonalTanks());
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

    /** Implementation behind AndroidOrders; callers should use that facade. */
    public AndroidOrders.Receipt submitExternalOrder(UUID actor, UUID id, ItemStack target, long count,
                                                     List<UUID> workers, @Nullable BlockPos destination) {
        return submitExternalOrder(actor, id, target, count, workers, destination, null);
    }
    public AndroidOrders.Receipt submitExternalOrder(UUID actor, UUID id, ItemStack target, long count,
                                                     List<UUID> workers, @Nullable BlockPos destination, @Nullable BlockPos source) {
        if (!externalAllowed(actor)) return AndroidOrders.result(id, AndroidOrders.State.REJECTED, "not_console_owner");
        if (target.isEmpty() || FluidSamples.liquid(target) && (source != null || FluidSamples.amount(target) <= 0)
                || count < 1 || count > MAX_COUNT || workers.stream().anyMatch(java.util.Objects::isNull))
            return AndroidOrders.result(id, AndroidOrders.State.REJECTED, "invalid_item_count_or_workers");
        target = FluidSamples.key(target);
        if (target.isEmpty()) return AndroidOrders.result(id, AndroidOrders.State.REJECTED, "invalid_sample");
        var previous = externalOrders.get(id);
        if (previous != null) {
            if (!previous.actor.equals(actor) || !previous.matches(target, count, workers, destination, source))
                return AndroidOrders.result(id, AndroidOrders.State.REJECTED, "request_id_conflict");
            return externalOrderStatus(actor, id);
        }
        if (externalOrders.size() >= AndroidOrders.MAX_RECEIPTS)
            return AndroidOrders.result(id, AndroidOrders.State.REJECTED, "receipt_limit_acknowledge_terminal_orders");
        if (destination != null && (destination.equals(worldPosition) || !validDelivery(destination, target)))
            return AndroidOrders.result(id, AndroidOrders.State.REJECTED, "invalid_delivery_port");
        var endpoint = source == null ? null : workEndpoint(source);
        if (source != null && (endpoint == null || endpoint.supplyItems() == null))
            return AndroidOrders.result(id, AndroidOrders.State.REJECTED, "source_endpoint_unavailable");
        int orderId = nextOrderId;
        Component why = start(actorFor(level, actor), target.copy(), count, List.copyOf(workers), destination,
                false, workers.isEmpty(), true, source == null ? null : itemsAt(storagesAt(source, new HashSet<>())), source);
        if (why != null) return AndroidOrders.result(id, AndroidOrders.State.REJECTED, why.getString());
        var entry = new ExternalOrderLedger(id, actor, orderId, target, count, workers, destination);
        entry.materialSource = source == null ? null : source.immutable();
        externalOrders.put(id, entry);
        setChanged();
        return entry.receipt();
    }
    /** Read-only view; missing active orders are reported failed without changing persisted state. */
    public AndroidOrders.ConsoleSnapshot externalOrderSnapshot(UUID actor) {
        if (!externalAllowed(actor)) return new AndroidOrders.ConsoleSnapshot(AndroidOrders.SnapshotState.REJECTED,
                List.of(), 0, 0, AndroidOrders.MAX_RECEIPTS, 0, MAX_COUNT);
        List<AndroidOrders.Receipt> receipts = new ArrayList<>();
        int activeCount = 0, historyCount = 0;
        for (var entry : externalOrders.values()) {
            if (!entry.actor.equals(actor)) continue;
            var receipt = entry.receipt();
            if (entry.state == AndroidOrders.State.RUNNING) {
                var active = order(entry.order);
                receipt = new AndroidOrders.Receipt(entry.request,
                        active == null ? AndroidOrders.State.FAILED : AndroidOrders.State.RUNNING,
                        entry.order, entry.count, active == null ? entry.delivered : active.delivered,
                        active == null ? "order_missing_not_completed" : entry.reason);
            }
            receipts.add(receipt);
            if (receipt.state() == AndroidOrders.State.RUNNING) activeCount++;
            else if (receipt.terminal()) historyCount++;
        }
        return new AndroidOrders.ConsoleSnapshot(AndroidOrders.SnapshotState.AVAILABLE, receipts, activeCount,
                historyCount, AndroidOrders.MAX_RECEIPTS, AndroidOrders.MAX_RECEIPTS - externalOrders.size(), MAX_COUNT);
    }
    /**
     * Restricted orders only run plain crafting. Machine capabilities do not isolate pre-existing output or
     * retain request ownership after insertion, cancellation, worker loss or reload. Even a currently empty
     * furnace cannot provide that contract. Recipe discovery and ordinary processing orders remain available.
     */
    private static boolean restrictedStep(RecipeData r, boolean escrow) {
        if (!RecipeData.MODE_CRAFTING.equals(r.mode()) || !r.fuel().isEmpty()) return false;
        for (ItemStack out : r.outputs()) if (ChemicalSamples.is(out) || !escrow && FluidSamples.liquid(out)) return false;
        for (ItemCount in : r.inputs()) {
            if (unsupportedMaterial(in.item, escrow)) return false;
            if (in.alt != null) for (ItemStack a : in.alt.items().map(holder -> new ItemStack(holder)).toList()) if (unsupportedMaterial(a, escrow)) return false;
        }
        return true;
    }
    private static boolean unsupportedMaterial(ItemStack s, boolean escrow) {
        return io.github.genichimaruo.singulo.item.UsesItem.isCatalyst(s) || ChemicalSamples.is(s) || !escrow && FluidSamples.liquid(s);
    }
    private boolean externalAllowed(UUID actor) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || isRemoved()) return false;
        return owner != null && owner.equals(actor) && mayOperate(actorFor(level, actor));
    }
    public AndroidOrders.Receipt externalOrderStatus(UUID actor, UUID id) {
        if (!externalAllowed(actor)) return AndroidOrders.result(id, AndroidOrders.State.REJECTED, "not_console_owner");
        var entry = externalOrders.get(id);
        if (entry == null) return AndroidOrders.result(id, AndroidOrders.State.UNKNOWN, "unknown_request");
        if (!entry.actor.equals(actor)) return AndroidOrders.result(id, AndroidOrders.State.REJECTED, "not_request_owner");
        if (entry.state == AndroidOrders.State.RUNNING) {
            var active = order(entry.order);
            if (active == null) { entry.state = AndroidOrders.State.FAILED; entry.reason = "order_missing_not_completed"; setChanged(); }
            else entry.delivered = active.delivered;
        }
        return entry.receipt();
    }
    public AndroidOrders.Receipt cancelExternalOrder(UUID actor, UUID id) {
        var status = externalOrderStatus(actor, id);
        if (status.state() == AndroidOrders.State.RUNNING) cancel(status.orderId(), null);
        return status.state() == AndroidOrders.State.RUNNING ? externalOrderStatus(actor, id) : status;
    }
    public boolean acknowledgeExternalOrder(UUID actor, UUID id) {
        if (level instanceof ServerLevel server && AndroidEscrowData.get(server).isKnown(id)) return false;
        var status = externalOrderStatus(actor, id);
        if (!status.terminal()) return false;
        externalOrders.remove(id); setChanged(); return true;
    }
    private void finishExternalOrder(WorkOrder order, AndroidOrders.State state, @Nullable Component reason) {
        var escrow = escrowEntry(order);
        if (escrow != null) AndroidEscrowData.get((ServerLevel) level).markTerminal(escrow, state, order.delivered);
        for (var entry : externalOrders.values()) {
            if (entry.order == order.id && entry.state == AndroidOrders.State.RUNNING) {
                entry.state = state; entry.delivered = order.delivered;
                entry.reason = reason == null ? null : reason.getString(); setChanged();
            }
        }
    }

    /** 注文する。できなければ理由を返す（できたら null）。 */
    @Nullable
    public Component start(ServerPlayer player, ItemStack target, long count, List<UUID> androids, @Nullable BlockPos destination) {
        return start(player, target, count, androids, false, destination);
    }

    /**
     * 注文する。auto なら、androids は画面で選べた候補（だれも選ばなかった）で、そのうち作業量に見合う人数だけを招集する。
     * できなければ理由を返す（できたら null）。
     */
    @Nullable
    public Component start(ServerPlayer player, ItemStack target, long count, List<UUID> androids, boolean auto, @Nullable BlockPos destination) {
        return start(player, target, count, androids, auto, destination, false);
    }

    /** 注文する。fresh なら、完成品の在庫は使わず count 個を新しく作って届ける。できなければ理由を返す（できたら null）。 */
    @Nullable
    public Component start(ServerPlayer player, ItemStack target, long count, List<UUID> androids, boolean auto, @Nullable BlockPos destination,
                           boolean fresh) {
        Component why = start(player, target, count, androids, destination, false, auto, fresh);
        if (why == null && !orders.isEmpty()) {
            orders.get(orders.size() - 1).notify = true;     // 画面から今すぐ出した注文: できたら・止まったら知らせる
        }
        return why;
    }

    /** 素材を待ったまま、これだけ経ったら注文した人へ知らせる（tick）。 */
    static final int STALL_NOTIFY_TICKS = 20 * 60;

    /** 注文した人がいれば（同じサーバーにいれば）、チャットで知らせる。 */
    private void tell(WorkOrder o, Component what) {
        if (!o.notify || o.orderer == null || !(level instanceof ServerLevel server)) {
            return;
        }
        ServerPlayer p = server.getServer().getPlayerList().getPlayer(o.orderer);
        if (p != null) {
            p.sendSystemMessage(Component.translatable("gui.singulo.work_console.notify", what));
        }
    }

    /**
     * 注文する（toStock なら、できた物を素材置き場へ入れる）。androids が空なら、選べる全員。できなければ理由を返す（できたら null）。
     */
    @Nullable
    private Component start(Player player, ItemStack target, long count, List<UUID> androids, @Nullable BlockPos destination,
                            boolean toStock) {
        return start(player, target, count, androids, destination, toStock, false, false);
    }

    @Nullable
    private Component start(Player player, ItemStack target, long count, List<UUID> androids, @Nullable BlockPos destination,
                            boolean toStock, boolean auto, boolean fresh) {
        return start(player, target, count, androids, destination, toStock, auto, fresh, null);
    }

    @Nullable
    private Component start(Player player, ItemStack target, long count, List<UUID> androids, @Nullable BlockPos destination,
                            boolean toStock, boolean auto, boolean fresh, @Nullable List<ItemCount> stock) {
        return start(player, target, count, androids, destination, toStock, auto, fresh, stock, null);
    }
    @Nullable
    private Component start(Player player, ItemStack target, long count, List<UUID> androids, @Nullable BlockPos destination,
                            boolean toStock, boolean auto, boolean fresh, @Nullable List<ItemCount> stock, @Nullable BlockPos source) {
        return start(player, target, count, androids, destination, toStock, auto, fresh, stock, source, null);
    }
    @Nullable
    private Component start(Player player, ItemStack target, long count, List<UUID> androids, @Nullable BlockPos destination,
                            boolean toStock, boolean auto, boolean fresh, @Nullable List<ItemCount> stock,
                            @Nullable BlockPos source, @Nullable UUID escrowRequest) {
        if (!mayOperate(player)) {
            return Component.translatable("gui.singulo.work_console.locked");
        }
        WorkPlanner.Plan plan;
        RuinAndroid tankWorker = null;
        if (escrowRequest != null) {
            List<ItemCount> privateStock = new ArrayList<>();
            for (ItemCount actual : stock) if (!actual.matches(target)) privateStock.add(new ItemCount(actual.item, actual.count));
            plan = WorkPlanner.plan(target, count, recipes(), privateStock, level);
        } else if (source == null) {
            var workshop = workshopPlan(player, androids, target, count, toStock || fresh, stock == null ? stock() : stock);
            plan = workshop.plan();
            tankWorker = workshop.tankWorker();
        } else {
            syncSupplyClaims();
            var foreign = foreignClaims(List.of(source), stock);
            List<ItemCount> scoped = new ArrayList<>();
            for (ItemCount actual : stock) {
                long available = actual.count - foreign.getOrDefault(WorkPlanner.Key.of(actual.item), 0L);
                for (WorkOrder other : orders) available -= other.held(source, actual.item);
                if (available > 0 && !actual.matches(target)) scoped.add(new ItemCount(actual.item, available));
            }
            plan = WorkPlanner.plan(target, count, recipes(), scoped);
        }
        if (!plan.ok()) {
            return Component.translatable(plan.failureKey());
        }
        // Endpoint source restriction: exactly one plain crafting step. Private escrow: plain crafting chains.
        // Reject every machine step before custody binding: an output baseline is not an ownership protocol.
        if (source != null && (plan.steps.size() != 1 || plan.steps.stream().anyMatch(s -> !restrictedStep(s.entry().recipe(), false))))
            return Component.literal("source_restriction_requires_single_plain_crafting_step");
        if (escrowRequest != null && (plan.steps.isEmpty() || plan.steps.stream().anyMatch(s -> !restrictedStep(s.entry().recipe(), true))))
            return Component.literal("escrow_requires_plain_crafting_steps_without_fuel_or_catalysts");
        if ((toStock || fresh) && plan.steps.isEmpty()) {
            return Component.translatable("gui.singulo.work_console.error.missing");   // 作る工程がない（レシピがない）
        }
        boolean mining = plan.steps.stream().anyMatch(s -> s.entry().recipe().needsMining());
        boolean felling = plan.steps.stream().anyMatch(s -> s.entry().recipe().needsFelling());
        List<RuinAndroid> chosen = new ArrayList<>();
        for (RuinAndroid a : candidates(player)) {
            if ((tankWorker == null || a == tankWorker) && (androids.isEmpty() || androids.contains(a.getUUID())) && !busy(a) && a.escrowRequest() == null
                    && (escrowRequest == null || a.carryEmpty() && a.workConsole() == null)) {
                chosen.add(a);
            }
        }
        if (escrowRequest != null && chosen.size() > 1) chosen.subList(1, chosen.size()).clear();
        if (chosen.isEmpty()) {
            return Component.translatable("gui.singulo.work_console.error.no_android");
        }
        WorkOrder order = WorkOrder.create(nextOrderId++, plan, player.getUUID());
        order.escrowRequest = escrowRequest;
        order.materialSource = source == null ? null : source.immutable();
        order.materialEndpointId = source == null ? null : workEndpoint(source).endpointId();
        order.destination = destination != null && !destination.equals(worldPosition) && validDelivery(destination, target) ? destination.immutable() : null;
        order.toStock = toStock || FluidSamples.liquid(target) && order.destination == null;
        if (order.destination != null && workEndpoint(order.destination) != null)
            order.deliveryEndpointId = workEndpoint(order.destination).endpointId();
        // どの工程も、選んだアンドロイドのだれかが一度に運べるか
        for (WorkOrder.StepState s : order.steps) {
            boolean fits = false;
            for (RuinAndroid a : chosen) {
                fits |= canExecuteStep(a, s.recipe);
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
            if (s.recipe.needsFishing() && chosen.stream().noneMatch(RuinAndroid::canFish)) {
                return Component.translatable("gui.singulo.work_console.error.no_fisher", s.recipe.output().getHoverName());
            }
            if (s.recipe.needsFarming() && chosen.stream().noneMatch(RuinAndroid::canFarm)) {
                return Component.translatable("gui.singulo.work_console.error.no_farmer", s.recipe.output().getHoverName());
            }
        }
        if (escrowRequest == null && (androids.isEmpty() || auto)) {
            chosen = recruit(order, chosen);   // 選ばなかったとき: 作業量に見合う人数だけ招集する
        }
        if (escrowRequest != null) {
            var data = AndroidEscrowData.get((ServerLevel) level);
            data.bind(data.find(escrowRequest), order.id, chosen.get(0).getUUID());
        }
        // A real bucket travels as an item. An item-only order never needs to unload the owner's tanks.
        boolean retainIdleTanks = tankWorker == null && source == null && escrowRequest == null
                && chosen.stream().anyMatch(a -> a.workConsole() == null && !a.tanksEmpty())
                && !FluidSamples.liquid(target) && order.steps.stream().noneMatch(s -> usesTanks(s.recipe));
        if (!reserveItems(order, plan.fromStock).isEmpty() && escrowRequest == null) {
            discardClaims(order);
            return Component.translatable("gui.singulo.work_console.error.missing");
        }
        orders.add(order);
        for (RuinAndroid a : chosen) {
            order.workers.add(a.getUUID());
            if (order(a.workOrder()) == null || !worldPosition.equals(a.workConsole())) {
                if (a == tankWorker || retainIdleTanks && a.workConsole() == null && !a.tanksEmpty()) a.retainTanksForWork();
                a.assignWork(worldPosition, order.id);     // 今の注文の作業中なら、それを続けてから（手が空いたら）こちらへ
                if (escrowRequest != null) a.bindEscrow(escrowRequest, player.getUUID());
            }
        }
        message = null;
        setChanged();
        return null;
    }

    /**
     * 注文に招集する人数を、作業量に見合うだけにして選ぶ（アンドロイドを指定しなかった注文）。
     * 同じ作業場所では同時に1体しか作業できないので、同時に進められる作業場所の数（前の工程の材料待ちを考え、同じ段の工程の場所の数の最大）と、
     * 一度に運べる量で割った往復の数の、少ないほうまで。手の空いている・近いアンドロイドから選び、採掘・伐採が要るなら、それができる個体を必ず入れる。
     */
    private List<RuinAndroid> recruit(WorkOrder order, List<RuinAndroid> eligible) {
        int needed = workersNeeded(order, eligible.stream().mapToInt(RuinAndroid::carryCapacity).max().orElse(1));
        List<RuinAndroid> sorted = new ArrayList<>(eligible);
        sorted.sort(java.util.Comparator.comparing((RuinAndroid a) -> order(a.workOrder()) != null && worldPosition.equals(a.workConsole()))
                .thenComparingDouble(a -> a.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition))));
        List<RuinAndroid> picked = new ArrayList<>(sorted.subList(0, Math.min(needed, sorted.size())));
        // Every step must still have a worker with all of its required skills,
        // item slots and tanks after trimming the candidate list.
        for (WorkOrder.StepState step : order.steps) {
            ensure(picked, sorted, true, a -> canExecuteStep(a, step.recipe));
        }
        // Replace an incapable nearby worker instead of retaining extra idle crew.
        for (int i = 0; i < picked.size() && picked.size() > needed;) {
            RuinAndroid candidate = picked.get(i);
            boolean covered = order.steps.stream().allMatch(step -> picked.stream()
                    .anyMatch(a -> a != candidate && canExecuteStep(a, step.recipe)));
            if (covered) picked.remove(i); else i++;
        }
        return picked;
    }

    private static boolean canExecuteStep(RuinAndroid a, RecipeData recipe) {
        return canDo(a, recipe)
                && maxCrafts(recipe, a.isWorking() ? a.carryCapacity() : a.freeCarry(), 1) >= 1;
    }

    /** need なら、picked に ability を持つ個体が1体はいるようにする（いなければ、候補のうち一番よいものを足す）。 */
    private static void ensure(List<RuinAndroid> picked, List<RuinAndroid> sorted, boolean need,
                               java.util.function.Predicate<RuinAndroid> ability) {
        if (need && picked.stream().noneMatch(ability)) {
            sorted.stream().filter(ability).findFirst().ifPresent(picked::add);
        }
    }

    /** 注文に役立つ人数（1以上）。slots は、招集できる個体のうち一番多く運べる個体の欄の数。 */
    static int workersNeeded(WorkOrder order, int slots) {
        if (order.steps.isEmpty()) {
            return 1;                                       // 素材置き場から届けるだけ
        }
        int n = order.steps.size();
        int[] depth = new int[n];
        java.util.Map<Integer, java.util.Set<BlockPos>> places = new java.util.HashMap<>();
        long trips = 0;
        for (int i = 0; i < n; i++) {
            WorkOrder.StepState s = order.steps.get(i);
            depth[i] = depth(order, i, depth);
            places.computeIfAbsent(depth[i], d -> new java.util.HashSet<>()).add(s.location);
            long perTrip = Math.max(1, maxCrafts(s.recipe, slots, Math.max(1, s.total)));
            trips += (Math.max(1, s.total) + perTrip - 1) / perTrip;
        }
        int width = places.values().stream().mapToInt(java.util.Set::size).max().orElse(1);
        return (int) Math.max(1, Math.min(width, trips));
    }

    /** 工程 i の段（材料を作る前の工程がなければ 0。前の工程の段の最大 + 1）。 */
    private static int depth(WorkOrder order, int i, int[] memo) {
        int d = 0;
        for (int j : order.steps.get(i).after) {
            if (j != i && j >= 0 && j < i) {
                d = Math.max(d, memo[j] + 1);
            }
        }
        return d;
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
        // ここでは在庫を予約するだけ。実際の在庫は1回だけ数えて使い回すが、予約済みの分は注文ごとに引き直す
        // （同時に出す注文が、同じ物を二重に当てにしないように）
        List<ItemCount> stock = null;
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
                if (stock == null) {
                    stock = stock();
                }
                long have = WormholeOrders.pattern(s.target) ? WormholeOrders.inStock(level, stock, s.target, s.count)
                        : ItemCount.get(stock, s.target);
                want = Math.max(0, s.count - have);
            }
            if (want <= 0) {
                s.state = StandingOrder.IDLE;
                s.reason = null;
                continue;
            }
            Player actor = actorFor(level, s.registrant);
            int id = nextOrderId;
            if (stock == null) {
                stock = stock();
            }
            Component why = start(actor, s.target, want, s.androids, s.destination, s.toStock(), s.androids.isEmpty(), false, stock);
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
        finishExternalOrder(o, reason == null ? AndroidOrders.State.CANCELLED : AndroidOrders.State.FAILED, reason);
        discardClaims(o);
        orders.remove(o);
        leases.removeIf(l -> l.job.orderId() == id);
        message = reason != null ? reason : Component.translatable("gui.singulo.work_console.cancelled", o.target.getHoverName());
        if (reason != null) {
            tell(o, reason);                           // 止まって取り消された（画面から取り消したときは知らせない）
        }
        setChanged();
    }

    /**
     * 一度に作れる回数: 持ち物の空き slots に収まるだけ（上限 limit。足りない分を残さないよう、収まるなら全部）。
     * 作るたびに素材は減り、できた物が増えるので、作っている途中で一番場所を取るときで数える。
     */
    /** 加工モードのレシピを作れる場所（上から入れて、下から取り出せる）。 */
    private boolean isMachine(BlockPos pos, RecipeData recipe) {
        return level != null && level.isLoaded(pos) && MachineWatch.handlesInput(level, pos, recipe) && MachineWatch.handlesOutput(level, pos, recipe);
    }

    /** その面から、物か流体を出し入れできるか。 */
    private boolean handles(BlockPos pos, net.minecraft.core.Direction face) {
        return io.github.genichimaruo.singulo.compat.ItemResourceView.findAndroid(level, pos, face) != null
                || LiquidStores.handles(level, pos, face);
    }

    /** 作業台のレシピを作れるブロック（バニラの作業台か、アンドロイド作業台）。 */
    public static boolean isWorkbench(net.minecraft.world.level.block.Block block) {
        return block instanceof net.minecraft.world.level.block.CraftingTableBlock || AndroidWorkbenchBlockEntity.tier(block) > 0;
    }

    static long maxCrafts(RecipeData recipe, int slots, long limit) {
        return maxCrafts(recipe, slots, limit, List.of());
    }

    /**
     * 持ち物の枠 slots に、作っている間ずっと収まる一番多い回数（limit まで）。枠は全部使う（決まった数を空けておかない）。
     * carried: 今持っている物（この工程の素材も含む）。k 回作るとき、素材に使う分を除いた残りは、作っている間ずっと持っている。
     */
    static long maxCrafts(RecipeData recipe, int slots, long limit, List<ItemCount> carried) {
        long lo = 0, hi = limit;
        // できた物は、ほかの持ち物を数える前から収まっていなければならない。待っている作業者ごとに、
        // 収まるはずのない何千回もの試算をしないよう、先に上限を絞る
        for (ItemStack output : recipe.outputs()) {
            if (!output.isEmpty() && !FluidSamples.liquid(output)) {
                hi = Math.min(hi, (long) slots * Math.max(1, output.getMaxStackSize()) / Math.max(1, output.getCount()));
            }
        }
        while (lo < hi) {                                     // 収まる回数は、回数を増やすほど収まりにくくなるので二分探索
            long mid = (lo + hi + 1) / 2;
            if (peakStacks(recipe, mid, rest(carried, recipe, mid)) <= slots) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** 作業台で作る回数: 素材は作業者が持ち運ぶが、増えていくできた物は作業台の預かり箱にも置ける。 */
    private long workbenchCrafts(WorkOrder.StepState step, int slots) {
        RecipeData recipe = step.recipe;
        if (level != null && MachineWatch.missingTool(level, step.location, recipe) != null) slots = Math.max(0, slots - 1);
        if (recipe.processing() || recipe.procedure() || recipe.variants() || level == null || !level.isLoaded(step.location)
                || !(level.getBlockEntity(step.location) instanceof AndroidWorkbenchBlockEntity bench)) {
            return maxCrafts(recipe, slots, step.remaining());
        }
        int free = 0;
        for (int i = 0; i < bench.buffer().getSlots(); i++) {
            if (bench.buffer().getStackInSlot(i).isEmpty()) {
                free++;
            }
        }
        long lo = 0, hi = maxCrafts(recipe, slots + free, step.remaining());
        List<ItemCount> inputs = recipe.inputs();
        while (lo < hi) {
            long mid = lo + (hi - lo + 1) / 2;
            int needed = 0;
            for (ItemCount input : inputs) {
                needed += stacks(input.item, input.count * mid);
            }
            if (needed <= slots) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** carried から、crafts 回分の素材を除いた残り（作っている間ずっと持っている物）。 */
    private static List<ItemCount> rest(List<ItemCount> carried, RecipeData recipe, long crafts) {
        List<ItemCount> rest = new ArrayList<>();
        for (ItemCount c : carried) {
            ItemCount.add(rest, c.item, c.count);
        }
        for (ItemCount in : recipe.inputs()) {
            ItemCount.add(rest, in.item, -Math.min(ItemCount.get(rest, in.item), in.count * crafts));
        }
        return rest;
    }

    static int peakStacks(RecipeData recipe, long crafts) {
        return peakStacks(recipe, crafts, List.of());
    }

    /**
     * crafts 回を1回ずつ作っていくあいだ、持ち物が一番場所を取るときのスタック数。作るたびに、素材の残り・できた物・
     * 残る物（バケツなど）・ほかに出てくる物（記録した数ずつ）がどう変わるかを数える。base はずっと持っている物
     * （同じ物は同じスタックにまとまる）。採掘・伐採・収穫で一緒に出る物は前もって分からないので、掘る前に収まるかを確かめる
     * （AndroidWorkGoal）。
     */
    static int peakStacks(RecipeData recipe, long crafts, List<ItemCount> base) {
        int peak = 0;
        List<ItemCount> inputs = recipe.inputs();
        long step = Math.max(1, crafts / 256);
        for (long done = 0; done <= crafts; done = done == crafts ? crafts + 1 : Math.min(crafts, done + step)) {
            List<ItemCount> now = new ArrayList<>();
            for (ItemCount c : base) {
                ItemCount.add(now, c.item, c.count);
            }
            for (ItemCount in : inputs) {
                ItemCount.add(now, in.item, in.count * (crafts - done));
                if (in.item.getItem().getCraftingRemainder(in.item) != null) {
                    ItemCount.add(now, in.item.getItem().getCraftingRemainder(in.item).create(), in.count * done);   // バケツなど、作ると残る物
                }
            }
            ItemCount.add(now, recipe.output(), (long) recipe.outputCount() * done);
            for (ItemStack e : recipe.extras()) {
                if (!e.isEmpty()) {
                    ItemCount.add(now, e, (long) e.getCount() * done);           // 副産物: 記録した数ずつ
                }
            }
            peak = Math.max(peak, stacks(now));
        }
        return peak + (crafts > 256 ? 1 : 0);              // 間引いて数えたときは、1つ余裕を見る
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
    public java.util.@Nullable UUID actorFor(int orderId) {
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
        // Processing unloads its inputs before collecting products, so the same tanks can serve both trips.
        long inputLimit = Long.MAX_VALUE;
        if (recipe.processing()) {
            inputLimit = tankLimit(a, fluids, chems);
            fluids.clear();
            chems.clear();
        }
        for (ItemStack out : recipe.outputs()) {
            if (FluidSamples.liquid(out)) {
                ItemCount.add(ChemicalSamples.is(out) ? chems : fluids, FluidSamples.key(out), FluidSamples.amount(out));
            }
        }
        return Math.min(inputLimit, tankLimit(a, fluids, chems));
    }

    private static boolean usesTanks(RecipeData recipe) {
        return recipe.inputs().stream().anyMatch(in -> FluidSamples.liquid(in.item))
                || recipe.outputs().stream().anyMatch(FluidSamples::liquid)
                || recipe.steps().stream().anyMatch(step -> FluidSamples.liquid(step.item()));
    }

    private static long tankLimit(RuinAndroid a, List<ItemCount> fluids, List<ItemCount> chems) {
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
        return (!recipe.needsMining() || a.canMine()) && (!recipe.needsFelling() || a.canFell())
                && (!recipe.needsFishing() || a.canFish()) && (!recipe.needsFarming() || a.canFarm()) && tankLimit(a, recipe) > 0;
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
        if (a.escrowRequest() != null && (escrowReturning(a) || o == null || !a.escrowRequest().equals(o.escrowRequest)))
            return WorkJob.simple(WorkJob.Type.EXIT, a.workOrder());
        if (o != null && o.escrowRequest != null && !o.escrowRequest.equals(a.escrowRequest()))
            return WorkJob.simple(WorkJob.Type.EXIT, a.workOrder());
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
        BusyLocations busy = busyLocations();
        WorkJob job = jobIn(o, a, carrying, busy);
        if (job.type() != WorkJob.Type.WAIT) {
            return job;
        }
        // この注文に今できることがない（装置の加工を待っているなど）: 受け持っているほかの注文の作業をする
        for (WorkOrder other : orders) {
            if (a.escrowRequest() == null && other.escrowRequest == null && other != o && other.workers.contains(a.getUUID())) {
                WorkJob j = jobIn(other, a, carrying, busy);
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
            if (o.escrowRequest == null && o.workers.contains(a.getUUID())) {
                return o;
            }
        }
        return null;
    }

    /** 注文 o の中で、次の作業を決める（なければ WAIT）。 */
    private WorkJob jobIn(WorkOrder o, RuinAndroid a, boolean carrying, BusyLocations busy) {
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
                if (s.collectReady && !locationBusy(busy, s.location, s) && canDo(a, s.recipe) && !fuelBlocked(s)) {
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
        WorkJob batch = batchJob(o, slots, a, busy);
        if (batch != null) {
            return lease(batch, a);
        }
        // 1工程ずつ: できる工程のうち、作業場所が一番近いもの
        boolean tooSmall = false;
        int best = -1;
        long bestK = 0;
        double bestDist = Double.MAX_VALUE;
        List<Integer> ready = o.readySteps();
        for (int index = 0; index < (ready == null ? o.steps.size() : ready.size()); index++) {
            int i = ready == null ? index : ready.get(index);
            WorkOrder.StepState s = o.steps.get(i);
            if (s.remaining() <= 0 || (ready == null && !o.ready(s)) || locationBusy(busy, s.location, s) || !canDo(a, s.recipe) || fuelBlocked(s)) {
                continue;
            }
            // Private escrow work cannot spill products into the workbench buffer, so the whole trip
            // (materials and products) must fit in the carry; otherwise the worker refetches forever.
            long k = Math.min(o.escrowRequest != null ? maxCrafts(s.recipe, slots, s.remaining())
                    : workbenchCrafts(s, slots), tankLimit(a, s.recipe));
            k = Math.min(k, MachineWatch.toolCraftLimit(level, s.location, s.recipe));
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
                    long amount = in.count * k;
                    if (a.usesPersonalTanks() && FluidSamples.is(in.item)) amount -= Math.min(amount, a.personalLiquid(in.item));
                    ItemCount.add(fetch, in.item, amount);
                }
            }
            s.inProgress += k;
            return lease(new WorkJob(WorkJob.Type.CRAFT, o.id, nextJobId++, 0, fetch,
                    List.of(new WorkJob.Batch(i, k, s.location, s.recipe)), false), a);
        }
        if (canDeliver(o) && !carrying) {
            long left = o.count - o.delivered - o.deliverInProgress;
            if (!o.stepsFinished()) {
                // 作っている間は、倉庫に予約済みの完成品だけを届ける
                // 残りは、ほかの作業者がまだ作っているかもしれない
                left = Math.min(left, Math.max(0, o.held(null, o.target) - o.deliverInProgress));
            }
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
        BusyLocations busy = busyLocations();
        WorkOrder o = order(orderId);
        if (o == null) {
            return null;
        }
        if (o.escrowRequest != null && (!o.escrowRequest.equals(a.escrowRequest()) || escrowReturning(a))) return null;
        List<Integer> ready = o.readySteps();
        for (int index = 0; index < (ready == null ? o.steps.size() : ready.size()); index++) {
            int i = ready == null ? index : ready.get(index);
            WorkOrder.StepState s = o.steps.get(i);
            if (s.remaining() <= 0 || (ready == null && !o.ready(s)) || locationBusy(busy, s.location, s) || !canDo(a, s.recipe) || fuelBlocked(s)) {
                continue;
            }
            // 持ち物の素材で何回作れるか（素材の要らない手順（採掘だけなど）は、空いている場所に収まるだけ）
            long k = Math.min(s.remaining(), MachineWatch.toolCraftLimit(level, s.location, s.recipe));
            for (ItemCount in : s.recipe.inputs()) {
                k = Math.min(k, carried(a, in) / in.count);
            }
            // できた物とほかに出てくる物が、今持っている物と一緒に収まる回数まで（持ち物があふれて足元に落ちないように）
            int toolSlot = MachineWatch.missingTool(level, s.location, s.recipe) == null ? 0 : 1;
            k = maxCrafts(s.recipe, Math.max(0, a.carryCapacity() - toolSlot), k, carriedList(a));
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
        if (canDeliver(o)) {
            long left = o.count - o.delivered - o.deliverInProgress;
            if (left > 0 && WormholeOrders.pattern(o.target)) {
                var chosen = new ArrayList<ItemCount>();
                WorkPlanner.take(carriedList(a), chosen, o.target, left);
                if (chosen.isEmpty()) return null;
                var pair = chosen.getFirst();
                o.deliverInProgress += pair.count;
                return lease(new WorkJob(WorkJob.Type.DELIVER, o.id, nextJobId++, pair.count, List.of(pair), List.of(), true), a);
            }
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
     * 作り終えた分は、まだ終わっていないほかの工程がその物を使わない限り、早めに届ける
     * （でないと、4096個の注文では、届け始める前に4096個分の置き場が要る）。
     */
    private static boolean canDeliver(WorkOrder order) {
        for (WorkOrder.StepState step : order.steps) {
            if (!step.finished()) for (ItemCount input : step.recipe.inputs()) {
                if (input.matches(order.target)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 残りの工程をまとめた作業: 工程を上から（素材を作る工程が先）順に、残りの回数を割合 f で減らして並べ、素材の取り出しを
     * 一度で済ませる。作った物は次の工程の素材に使い、足りない分だけ素材置き場（この注文の予約）から取る。
     * 一度に運べて、予約で足りる、一番大きな f を選ぶ。まとめられなければ null。
     */
    @Nullable
    private WorkJob batchJob(WorkOrder o, int slots, RuinAndroid a, BusyLocations busy) {
        List<Integer> open = new ArrayList<>();
        java.util.BitSet planned = new java.util.BitSet(o.steps.size());
        for (int i = 0; i < o.steps.size(); i++) {
            WorkOrder.StepState s = o.steps.get(i);
            if (s.remaining() <= 0) {
                continue;
            }
            if (s.inProgress > 0 || !s.recipe.tools().isEmpty() || locationBusy(busy, s.location, s) || !canDo(a, s.recipe) || tankLimit(a, s.recipe) != Long.MAX_VALUE) {
                return null;                          // ほかのアンドロイドが作業中の工程、このアンドロイドにできない工程がある: 1工程ずつに分ける
            }
            for (int j : s.after) {
                if (!o.steps.get(j).finished() && !planned.get(j)) {
                    return null;
                }
            }
            open.add(i);
            planned.set(i);
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
                if (in.alt != null) {
                    // 同じ種類の別の物も使う素材: 前の工程で作った合う物から使い、足りない分を（合う物ならどれでも）取りに行く
                    long use = 0;
                    for (ItemCount c : inv) {
                        if (c.count > 0 && use < need && in.matches(c.item)) {
                            long take = Math.min(c.count, need - use);
                            c.count -= take;
                            use += take;
                        }
                    }
                    if (need > use) {
                        ItemCount.addAlt(fetch, in.item, need - use, in.alt);
                    }
                    continue;
                }
                long have = ItemCount.get(inv, in.item);
                long use = Math.min(have, need);
                ItemCount.add(inv, in.item, -use);
                ItemCount.add(fetch, in.item, need - use);
            }
            ItemCount.add(inv, b.recipe().outputKey(), (long) b.recipe().outputCount() * b.crafts());
        }
        // 同じ種類の別の物は、取ってくる物が何種類かに分かれることがある: 分かれる分だけ、持ち物の枠を多めに見る
        int mixed = 0;
        for (ItemCount c : fetch) {
            if (c.count <= 0) {
                continue;
            }
            if (c.alt == null) {
                if (o.held(null, c.item) < c.count) {
                    return null;                      // 予約した在庫で足りない
                }
                continue;
            }
            long held = 0;
            List<ItemStack> kinds = new ArrayList<>();
            for (WorkOrder.Holding h : o.held) {
                if (c.matches(h.item)) {
                    held += h.count;
                    if (kinds.stream().noneMatch(k -> ItemStack.isSameItemSameComponents(k, h.item))) {
                        kinds.add(h.item);
                    }
                }
            }
            if (held < c.count) {
                return null;
            }
            mixed += Math.max(0, kinds.size() - 1);       // k 種類に分かれると、スタックは多くて k - 1 増える
        }
        fetch.removeIf(c -> c.count <= 0);
        // 持ち物の場所: 順に作りながら、一番多いときで数える
        List<ItemCount> carry = new ArrayList<>();
        for (ItemCount c : fetch) {
            ItemCount.add(carry, c.item, c.count);
        }
        int peak = stacks(carry);
        for (WorkJob.Batch b : batch) {
            // この工程を1回ずつ作るあいだ: ほかの工程の物はずっと持っていて、この工程の素材・できた物・残る物・副産物が入れ替わる
            peak = Math.max(peak, peakStacks(b.recipe(), b.crafts(), rest(carry, b.recipe(), b.crafts())));
            for (ItemCount in : b.recipe().inputs()) {
                ItemCount.add(carry, in.item, -in.count * b.crafts());
                if (in.item.getItem().getCraftingRemainder(in.item) != null) {
                    ItemCount.add(carry, in.item.getItem().getCraftingRemainder(in.item).create(), in.count * b.crafts());     // 残る物は、このあとも持っている
                }
            }
            ItemCount.add(carry, b.recipe().outputKey(), (long) b.recipe().outputCount() * b.crafts());
            for (ItemStack e : b.recipe().extras()) {
                if (!e.isEmpty()) {
                    ItemCount.add(carry, e, (long) e.getCount() * b.crafts());
                }
            }
        }
        return peak + mixed <= slots ? fetch : null;
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

    /** 作業で運ぶ持ち物（作業のスロットにある物すべて）。 */
    private static List<ItemCount> carriedList(RuinAndroid a) {
        List<ItemCount> out = new ArrayList<>();
        for (int slot : a.carrySlots()) {
            ItemStack s = a.inventory().getItem(slot);
            if (!s.isEmpty() && !FluidSamples.liquid(s)) {
                ItemCount.add(out, s, s.getCount());
            }
        }
        return out;
    }

    private static long carried(RuinAndroid a, ItemStack like) {
        if (FluidSamples.liquid(like)) {
            return a.liquidAmount(like);
        }
        long n = 0;
        for (int slot : a.carrySlots()) {
            if (WormholeOrders.matches(like, a.inventory().getItem(slot))) {
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
    private record BusyLocations(java.util.Map<BlockPos, Integer> parked, Set<BlockPos> leased) {}

    /** 作業を決めるたびに1回だけ数える（tick ごとには覚えない。次の作業者は新しい受け持ちを見る）。 */
    private BusyLocations busyLocations() {
        var parked = new java.util.HashMap<BlockPos, Integer>();
        Set<BlockPos> leased = new HashSet<>();
        for (WorkOrder order : orders) for (WorkOrder.StepState step : order.steps) {
            if (step.parked > 0) {
                parked.merge(step.location, 1, Integer::sum);
            }
        }
        for (WorkJob.Lease lease : leases) {
            if (lease.job.type() == WorkJob.Type.CRAFT || lease.job.type() == WorkJob.Type.COLLECT) {
                for (WorkJob.Batch batch : lease.job.batch()) {
                    leased.add(batch.location());
                }
            }
        }
        return new BusyLocations(parked, leased);
    }

    private static boolean locationBusy(BusyLocations busy, BlockPos pos, WorkOrder.@Nullable StepState self) {
        int own = self != null && self.parked > 0 && pos.equals(self.location) ? 1 : 0;
        return busy.leased.contains(pos) || busy.parked.getOrDefault(pos, 0) > own;
    }

    private WorkJob.@Nullable Lease lease(int jobId) {
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
        for (BlockPos p : endpointLinks.keySet()) {
            var e = workEndpoint(p);
            if (e != null && (e.supplyItems() != null || e.supplyFluids() != null || e.supplyChemicals() != null)) out.add(p);
        }
        return out;
    }

    /** 受け渡し場所 point での item の優先度（資材口の優先の登録。take は取り出し、そうでなければ返却。登録がなければ 0）。 */
    public int priority(BlockPos point, ItemStack item, boolean take) {
        return level != null && level.getBlockEntity(point) instanceof SupplyPortBlockEntity port ? port.priority(item, take) : 0;
    }

    /** 受け渡し場所の番号を、item を取り出す順に並べる（優先度の高い順。同じなら元の順: 指令台、資材口）。 */
    private List<Integer> takeOrder(List<BlockPos> points, ItemStack item) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            order.add(i);
        }
        order.sort(java.util.Comparator.comparingInt(i -> -priority(points.get(i), item, true)));
        return order;
    }

    /** item を戻す場所として、point が candidates のどこよりも優先度が低くないか。 */
    private boolean returnsHere(BlockPos point, ItemStack item, java.util.Collection<BlockPos> candidates) {
        int here = priority(point, item, false);
        for (BlockPos p : candidates) {
            if (priority(p, item, false) > here) {
                return false;
            }
        }
        return true;
    }

    /**
     * 持ち物を戻しに行く次の場所: candidates のうち、持ち物のどれかをいちばん優先して受け取る場所（優先の登録がなければ、
     * いちばん近い場所）。同じ優先度なら from に近い方。
     */
    public BlockPos nextReturnPoint(RuinAndroid a, List<BlockPos> candidates, BlockPos from) {
        BlockPos pick = null;
        int pickScore = -1;
        for (BlockPos p : candidates) {
            int score = 0;
            for (int slot : a.carrySlots()) {
                ItemStack s = a.inventory().getItem(slot);
                if (!s.isEmpty()) {
                    score = Math.max(score, priority(p, s, false));
                }
            }
            if (score > pickScore || score == pickScore && p.distSqr(from) < pick.distSqr(from)) {
                pick = p;
                pickScore = score;
            }
        }
        return pick;
    }

    /** 持ち物のどれかが、candidates のうちで point に戻したい物か（戻しきれずに残ったなら、そこはいっぱい）。 */
    public boolean returnsAnyHere(RuinAndroid a, BlockPos point, java.util.Collection<BlockPos> candidates) {
        for (int slot : a.carrySlots()) {
            ItemStack s = a.inventory().getItem(slot);
            if (!s.isEmpty() && returnsHere(point, s, candidates)) {
                return true;
            }
        }
        return false;
    }

    /** 受け渡し場所の隣のストレージ。アンドロイド作業台なら、その預かり箱（作った物があふれたときに入れた所）。 */
    public List<IItemHandler> storagesAt(BlockPos point, Set<BlockPos> seen) {
        List<IItemHandler> out = new ArrayList<>();
        visitItemStoresAt(point, (key, handler) -> {
            if (seen.add(key)) out.add(handler);
        });
        return out;
    }

    void visitItemStoresAt(BlockPos point, java.util.function.BiConsumer<BlockPos, IItemHandler> visit) {
        if (endpointLinks.containsKey(point)) {
            var e = workEndpoint(point);
            IItemHandler handler = e == null ? null : e.supplyItems();
            if (handler != null) {
                BlockPos key = handler instanceof io.github.genichimaruo.singulo.api.android.AndroidSharedStorage shared
                        ? SupplyPortBlockEntity.sharedSeenKey(shared.sharedStorageKey()) : point;
                visit.accept(key, handler);
            }
            return;
        }
        if (!level.isLoaded(point)) return;
        if (level.getBlockEntity(point) instanceof AndroidWorkbenchBlockEntity bench) {
            visit.accept(point, bench.buffer());
            return;
        }
        SupplyPortBlockEntity.visitItemStores(level, point, visit);
    }

    /** 受け渡し場所 point の隣の、流体の入れ物（タンクなど）。 */
    public List<net.neoforged.neoforge.fluids.capability.IFluidHandler> fluidStoresAt(BlockPos point, Set<BlockPos> seen) {
        List<net.neoforged.neoforge.fluids.capability.IFluidHandler> out = new ArrayList<>();
        if (endpointLinks.containsKey(point)) {
            var e = workEndpoint(point);
            var fluid = e == null ? null : e.supplyFluids();
            if (fluid != null && seen.add(fluid instanceof io.github.genichimaruo.singulo.api.android.AndroidSharedStorage shared
                    ? SupplyPortBlockEntity.sharedSeenKey(shared.sharedStorageKey()) : point)) out.add(fluid);
            return out;
        }
        SupplyPortBlockEntity.collectFluids(level, point, seen, out);
        return out;
    }

    private long countItems(ItemStack item, List<IItemHandler> stores) {
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
            } else if (WormholeOrders.pattern(c.item)) {
                // Select the physical links before splitting the trip by distance. Two ports can each hold one half.
                var pairs = new java.util.LinkedHashMap<WorkPlanner.Key, ItemCount>();
                for (var held : o.held) {
                    if (WormholeOrders.matches(c.item, held.item)) {
                        pairs.computeIfAbsent(WorkPlanner.Key.of(held.item), key -> new ItemCount(held.item, 0)).count += held.count;
                    }
                }
                if (WorkPlanner.take(new ArrayList<>(pairs.values()), left, c.item, c.count) > 0) return null;
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
                            long take = Math.min(need.count, o.held(p, h.item) - ItemCount.get(here, h.item));
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

    private java.util.Map<WorkPlanner.Key, Long> foreignClaims(@Nullable List<BlockPos> points, List<ItemCount> stock) {
        if (stock.isEmpty() || !(level instanceof ServerLevel server) || !SupplyReservations.get(server).hasForeign(server, worldPosition))
            return java.util.Map.of();
        return supplies(points == null ? supplyPoints() : points, stock).foreignClaims(worldPosition);
    }

    private WorkshopSupplies supplies(List<BlockPos> points, List<ItemCount> need) {
        return new WorkshopSupplies(this, points, need.stream().anyMatch(c -> !FluidSamples.liquid(c.item)),
                need.stream().anyMatch(c -> FluidSamples.liquid(c.item)));
    }

    private void holdSupply(WorkOrder order, BlockPos point, BlockPos key, ItemStack item, long amount) {
        if (amount <= 0) return;
        order.hold(point, key, item, amount);
        ServerLevel server = (ServerLevel)level;
        SupplyReservations.get(server).change(server, worldPosition, point, key, item, amount);
    }

    private void releaseSupply(WorkOrder order, BlockPos point, BlockPos key, ItemStack item, long amount) {
        long bound = order.consume(point, key, false, item, amount);
        ServerLevel server = (ServerLevel)level;
        if (bound > 0) SupplyReservations.get(server).change(server, worldPosition, point, key, item, -bound);
        if (bound < amount) order.consume(point, null, false, item, amount - bound);
    }

    private void releaseReplacedSupply(WorkOrder order, BlockPos point, ItemStack item, long amount) {
        for (var h : new ArrayList<>(order.held)) {
            if (!point.equals(h.point) || !ItemStack.isSameItemSameComponents(h.item, item)) continue;
            long used = Math.min(amount, h.count);
            if (h.store == null) order.consume(point, null, false, item, used);
            else releaseSupply(order, point, h.store, item, used);
            amount -= used;
            if (amount == 0) break;
        }
    }

    private void discardClaims(WorkOrder order) {
        if (!(level instanceof ServerLevel server)) return;
        for (var h : order.held) if (h.store != null)
            SupplyReservations.get(server).change(server, worldPosition, h.point, h.store, h.item, -h.count);
    }

    private void syncSupplyClaims() {
        if (!restoreSupplyClaims || !(level instanceof ServerLevel server)) return;
        restoreSupplyClaims = false;
        var claims = SupplyReservations.get(server);
        claims.clear(server, worldPosition);
        for (var o : orders) for (var h : o.held) if (h.store != null)
            claims.change(server, worldPosition, h.point, h.store, h.item, h.count);
        migrateLegacyClaims();
    }

    private void migrateLegacyClaims() {
        // An old save may load the console before its supply chunk becomes accessible.
        migrateSupplyClaims = false;
        var byPoint = new java.util.HashMap<BlockPos, WorkshopSupplies>();
        for (var o : orders) {
            if (o.escrowRequest != null) continue;
            for (var h : new ArrayList<>(o.held)) {
                if (h.store != null) continue;
                var wanted = new ItemCount(h.item, h.count);
                var found = byPoint.computeIfAbsent(h.point, point -> new WorkshopSupplies(this, List.of(point), true, true));
                long left = claimAt(o, wanted, h.count, found, h.point);
                migrateSupplyClaims |= left > 0;
                if (left < h.count) {
                    o.consume(h.point, null, false, h.item, h.count - left);
                    setChanged();
                }
            }
        }
    }

    /** need を、受け渡し場所の在庫（ほかの注文の予約を除く）から予約する。予約しきれなかった物を返す。 */
    private List<ItemCount> reserveItems(WorkOrder order, List<ItemCount> need) {
        if (order.escrowRequest != null) {
            List<ItemCount> missing = new ArrayList<>();
            var e = escrowEntry(order);
            List<IItemHandler> stores = e == null ? List.of() : List.of(AndroidEscrowData.get((ServerLevel) level).inputHandler(e));
            for (ItemCount needItem : need) {
                long left = needItem.count;
                for (ItemCount have : itemsAt(stores, needItem)) {
                    long n = Math.min(left, Math.max(0, have.count - order.held(worldPosition, have.item)));
                    order.hold(worldPosition, have.item, n); left -= n;
                }
                if (left > 0) missing.add(new ItemCount(needItem.item, left, needItem.alt));
            }
            return missing;
        }
        if (need.isEmpty()) return List.of();
        syncSupplyClaims();
        List<BlockPos> points = order.materialSource == null ? supplyPoints()
                : validMaterialSource(order) ? List.of(order.materialSource) : List.of();
        var supplies = supplies(points, need);
        List<ItemCount> missing = new ArrayList<>();
        for (ItemCount c : need) {
            long left = c.count;
            for (int index : takeOrder(points, c.item)) {
                left = claimAt(order, c, left, supplies, points.get(index));
                if (left == 0) break;
            }
            if (left > 0) missing.add(new ItemCount(c.item, left, c.alt));
        }
        return missing;
    }

    private long claimAt(WorkOrder order, ItemCount need, long left, WorkshopSupplies supplies, BlockPos point) {
        var candidates = supplies.matching(point, need);
        if (need.alt != null) candidates = candidates.stream()
                .sorted(java.util.Comparator.comparingInt(s -> ItemCount.lifeOrder(s.stock().item))).toList();
        for (var source : candidates) {
            if (!need.matches(source.stock().item)) continue;
            long take = Math.min(left, supplies.free(source));
            holdSupply(order, point, source.material().store(), source.stock().item, take);
            left -= take;
            if (left == 0) break;
        }
        return left;
    }

    /** ストレージにある、need に合う物（物ごとの数）。 */
    private static List<ItemCount> itemsAt(List<IItemHandler> stores, ItemCount need) {
        List<ItemCount> out = new ArrayList<>();
        for (IItemHandler h : stores) {
            for (int s = 0; s < h.getSlots(); s++) {
                ItemStack st = h.getStackInSlot(s);
                if (FluidSamples.liquid(need.item)) {   // escrow custody keeps fluids as samples (amount in mB)
                    if (FluidSamples.is(st) && FluidSamples.is(need.item) && net.neoforged.neoforge.fluids.FluidStack
                            .isSameFluidSameComponents(FluidSamples.fluid(st), FluidSamples.fluid(need.item)))
                        ItemCount.add(out, FluidSamples.key(st), FluidSamples.amount(st));
                    continue;
                }
                if (!st.isEmpty() && need.matches(st)) {
                    ItemCount.add(out, st, st.getCount());
                }
            }
        }
        return out;
    }
    private static List<ItemCount> itemsAt(List<IItemHandler> stores) {
        List<ItemCount> out = new ArrayList<>();
        for (IItemHandler h : stores) for (int s = 0; s < h.getSlots(); s++) {
            ItemStack item = h.getStackInSlot(s); ItemCount.add(out, item, item.getCount());
        }
        return out;
    }
    private boolean validMaterialSource(WorkOrder order) {
        var e = order.materialSource == null ? null : workEndpoint(order.materialSource);
        return e != null && e.endpointId().equals(order.materialEndpointId) && e.supplyItems() != null;
    }

    /**
     * 燃料 fuel の、ほかの注文が予約していない在庫がある受け渡し場所のうち、取り出しの優先度がいちばん高く、
     * 同じなら from に一番近い所（なければ null）。
     */
    @Nullable
    public BlockPos fuelPoint(ItemStack fuel, BlockPos from) {
        BlockPos best = null;
        int bestPriority = -1;
        var points = supplyPoints();
        syncSupplyClaims();
        var stock = new WorkshopSupplies(this, points, true, false);
        for (BlockPos point : points) {
            if (stock.at(point).stream().noneMatch(source -> source.stock().matches(fuel) && stock.free(source) > 0)) continue;
            int priority = priority(point, fuel, true);
            if (best == null || priority > bestPriority || priority == bestPriority && point.distSqr(from) < best.distSqr(from)) {
                best = point;
                bestPriority = priority;
            }
        }
        return best;
    }

    /** Pick an obtainable tool with its real components, including restored and partially used tools. */
    public ItemStack toolSupply(RecipeData.Tool tool, ItemStack sample) {
        ItemStack best = ItemStack.EMPTY;
        var points = supplyPoints();
        syncSupplyClaims();
        var stock = new WorkshopSupplies(this, points, true, false);
        for (var point : points) for (var source : stock.at(point)) {
            ItemStack candidate = source.stock().item;
            if (tool.accepts(sample, candidate) && stock.free(source) > 0
                    && (best.isEmpty() || UsesHelper.remaining(candidate) < UsesHelper.remaining(best)))
                best = candidate;
        }
        if (!best.isEmpty()) return best;
        for (var entry : recipes()) {
            ItemStack product = entry.recipe().output();
            if (ItemStack.isSameItem(product, sample) && tool.accepts(sample, product)) return product.copyWithCount(1);
        }
        return sample;
    }

    /** Take only fuel that is free in the particular inventory being drained. */
    public int takeFuel(RuinAndroid a, BlockPos point, ItemStack fuel, int count) {
        syncSupplyClaims();
        a.compactCarry();
        int room = 0;
        for (int slot : a.carrySlots()) {
            ItemStack stack = a.inventory().getItem(slot);
            if (stack.isEmpty()) room += fuel.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(stack, fuel)) room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
        }
        count = Math.min(room, count);
        if (count <= 0) return 0;
        var stock = new WorkshopSupplies(this, List.of(point), true, false);
        int took = 0;
        for (var source : stock.at(point)) {
            if (!source.stock().matches(fuel)) continue;
            int want = (int)Math.min(count - took, stock.free(source));
            if (want <= 0) continue;
            long moved = source.take(a, want);
            took += (int)moved;
            if (moved < want || took == count) break;
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
        if (!s.awaitingTool.isEmpty()) {
            if (makingFuel(s.awaitingTool) && fuelPoint(s.awaitingTool, s.location) == null
                    && MachineWatch.missingTool(level, s.location, s.recipe) != null) return true;
            s.awaitingTool = ItemStack.EMPTY;
        }
        ItemStack fuel = s.recipe.fuel();
        return !fuel.isEmpty() && s.recipe.processing() && MachineWatch.outOfFuel(level, s.location, s.recipe) && fuelPoint(fuel, s.location) == null
                && makingFuel(fuel);
    }

    public void awaitTool(WorkJob job, int step, ItemStack item) {
        WorkOrder parent = order(job.orderId());
        if (parent != null && step >= 0 && step < parent.steps.size()) {
            parent.steps.get(step).awaitingTool = item.copyWithCount(1);
            setChanged();
        }
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
    public boolean orderFuel(WorkJob job, ItemStack fuel, int count, boolean tool) {
        WorkOrder parent = order(job.orderId());
        if (parent == null || count <= 0 || fuel.isEmpty()) {
            return false;
        }
        if (makingFuel(fuel)) {
            return !ItemStack.isSameItemSameComponents(parent.target, FluidSamples.key(fuel));
        }
        WorkPlanner.Plan plan = plan(fuel, count, true);
        if (!plan.ok() || plan.steps.isEmpty()) {
            return false;
        }
        for (WorkPlanner.Step s : plan.steps) {
            var recipe = s.entry().recipe();
            if (recipe.processing() && (ItemStack.isSameItem(recipe.fuel(), fuel)
                    || recipe.tools().stream().anyMatch(t -> t.accepts(recipe.grid().get(t.slot()), fuel)))) {
                return false;                       // 木炭を焼くのに木炭が要る、のように、始められない
            }
        }
        Component why = start(actorFor(level, parent.orderer), fuel, count, List.copyOf(parent.workers), null, true);
        if (why != null) {
            return false;
        }
        message = Component.translatable(tool ? "gui.singulo.work_console.tool_ordered" : "gui.singulo.work_console.fuel_ordered",
                fuel.getHoverName(), count);
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
        BusyLocations busy = busyLocations();
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
                if (s.parked > 0 && s.collectReady && !locationBusy(busy, s.location, s)) {
                    return true;
                }
                boolean waiting = o.status == WorkOrder.WAIT_ITEMS && now < o.retryAt;
                if (!waiting && s.remaining() > 0 && o.ready(s) && !locationBusy(busy, s.location, s) && canDo(a, s.recipe) && !fuelBlocked(s)) {
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

    /** 置き場がいっぱいになったとき、作り終えた分はやり直さずに、作る作業の受け持ちを手放す。 */
    public void completePartialCraft(WorkJob job, int current, long crafts) {
        if (lease(job.jobId()) == null) {
            return;
        }
        WorkOrder order = order(job.orderId());
        if (order != null && current >= 0 && current < job.batch().size()) {
            WorkJob.Batch batch = job.batch().get(current);
            if (batch.step() >= 0 && batch.step() < order.steps.size()) {
                order.steps.get(batch.step()).done += Math.max(0, Math.min(batch.crafts(), crafts));
            }
        }
        completeUpTo(job, current - 1);
    }

    /** 受け渡し場所で、そこにある物を持ち物へ取り出す（全部そろわなければ取らない）。 */
    public boolean fetchAt(RuinAndroid a, WorkJob job, Pickup pickup) {
        WorkOrder restricted = order(job.orderId());
        if (a.escrowRequest() != null && (restricted == null || !a.escrowRequest().equals(restricted.escrowRequest))) return false;
        AndroidEscrowData.Entry escrow = escrowEntry(restricted);
        if (restricted != null && restricted.escrowRequest != null) {
            var active = lease(job.jobId());
            if (escrow == null || escrow.state != AndroidEscrowData.State.RUNNING || active == null
                    || !a.getUUID().equals(active.worker) || !a.getUUID().equals(escrow.worker)
                    || !restricted.escrowRequest.equals(a.escrowRequest()) || !worldPosition.equals(pickup.point())) return false;
            for (ItemCount need : pickup.items()) if (need.count > restricted.held(worldPosition, need.item)) return false;
        }
        if (restricted != null && restricted.materialSource != null && !validMaterialSource(restricted)) return false;
        // Check the complete item pickup before extraction. Addon supplies can be read-only on
        // insertion; relying on returning an overflow to them can otherwise discard resources.
        a.compactCarry();
        List<ItemStack> simulatedCarry = new ArrayList<>();
        for (int slot : a.carrySlots()) simulatedCarry.add(a.inventory().getItem(slot).copy());
        for (ItemCount need : pickup.items()) {
            if (io.github.genichimaruo.singulo.wormhole.UnstableMouthItem.expired(need.item, level)) {
                if (restricted != null) cancel(restricted.id, Component.translatable("gui.singulo.wormhole.mouth_collapsed"));
                return false;
            }
            if (FluidSamples.liquid(need.item)) continue;
            long remaining = need.count;
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int i = 0; i < simulatedCarry.size() && remaining > 0; i++) {
                    ItemStack held = simulatedCarry.get(i);
                    if (pass == 0 && !held.isEmpty() && ItemStack.isSameItemSameComponents(held, need.item)) {
                        int move = (int) Math.min(remaining, Math.max(0, held.getMaxStackSize() - held.getCount()));
                        held.grow(move); remaining -= move;
                    } else if (pass == 1 && held.isEmpty()) {
                        int move = (int) Math.min(remaining, need.item.getMaxStackSize());
                        simulatedCarry.set(i, need.item.copyWithCount(move)); remaining -= move;
                    }
                }
            }
            if (remaining > 0) return false;
        }
        if (!pickupTanksFit(a, pickup.items())) return false;
        if (escrow == null) return fetchShared(a, restricted, pickup);
        var data = AndroidEscrowData.get((ServerLevel)level);
        List<IItemHandler> stores = List.of(data.inputHandler(escrow));
        LiquidStores liquids = LiquidStores.of(data.fluidHandler(escrow, false));
        for (ItemCount need : pickup.items()) {
            long have = FluidSamples.liquid(need.item) ? liquids.amount(need.item) : countItems(need.item, stores);
            if (have < need.count) {
                WorkOrder short_ = order(job.orderId());
                if (short_ != null) {
                    // 画面の「足りない素材」に出す（同じ物は数を置き換える）
                    short_.shortage.removeIf(c -> c.matches(need.item));
                    short_.shortage.add(new ItemCount(need.item, need.count - have));
                }
                return false;
            }
            if (FluidSamples.liquid(need.item) && a.liquidSpace(need.item) < need.count) {
                return false;                          // タンクに入りきらない
            }
        }
        WorkOrder o = order(job.orderId());
        if (o != null) {
            o.shortage.clear();                        // 取れた: 足りなかった物は、もう足りている
        }
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
                    // 1つの欄にスタックより多く入っている入れ物（QIO・ME など、種類ごとに1欄）もあるので、
                    // 足りるか、その欄が空になるか、持ち物がいっぱいになるまで、同じ欄から続けて取る
                    while (left > 0 && need.matches(h.getStackInSlot(i))) {
                        ItemStack got = h.extractItem(i, (int) Math.min(left, h.getStackInSlot(i).getMaxStackSize()), false);
                        if (got.isEmpty()) {
                            break;
                        }
                        left -= got.getCount();
                        ItemStack rest = a.addToCarry(got);
                        if (!rest.isEmpty()) {
                            ItemHandlerHelper.insertItemStacked(h, rest, false);      // 入りきらなければ戻す（ふつうは起きない）
                            left += rest.getCount();
                            break;
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

    private static boolean pickupTanksFit(RuinAndroid worker, List<ItemCount> need) {
        if (need.stream().noneMatch(c -> FluidSamples.liquid(c.item))) return true;
        int fluidCap = worker.fluidCapacity(), chemCap = worker.chemCapacity();
        long emptyFluids = RuinAndroid.TANKS - worker.tankFluids().size();
        long emptyChems = chemCap == 0 ? 0 : worker.chemSpace("") / chemCap;
        long newFluids = 0, newChems = 0;
        for (var item : need) {
            if (!FluidSamples.liquid(item.item)) continue;
            boolean chemical = ChemicalSamples.is(item.item);
            int capacity = chemical ? chemCap : fluidCap;
            long room = worker.liquidSpace(item.item);
            if (capacity <= 0 || room < item.count) return false;
            long matchingRoom = room - (chemical ? emptyChems : emptyFluids) * capacity;
            long newTanks = (Math.max(0, item.count - matchingRoom) + capacity - 1) / capacity;
            if (chemical) newChems += newTanks;
            else newFluids += newTanks;
        }
        return newChems <= emptyChems && newFluids <= emptyFluids;
    }

    private record Draw(WorkshopSupplies.Source source, long count, boolean replacement) {}

    private boolean fetchShared(RuinAndroid worker, @Nullable WorkOrder order, Pickup pickup) {
        syncSupplyClaims();
        var supplies = supplies(List.of(pickup.point()), pickup.items());
        var draws = new ArrayList<Draw>();
        var remaining = new java.util.HashMap<WorkshopSupplies.Material, Long>();
        if (order != null) for (var h : order.held) {
            if (h.store != null && h.point.equals(pickup.point()))
                remaining.merge(new WorkshopSupplies.Material(h.store, WorkPlanner.Key.of(h.item)), h.count, Long::sum);
        }
        for (var need : pickup.items()) {
            long left = need.count;
            var available = supplies.matching(pickup.point(), need);
            var planned = new java.util.IdentityHashMap<WorkshopSupplies.Source, Long>();
            for (var source : available) {
                long mine = remaining.getOrDefault(source.material(), 0L);
                long bound = Math.min(left, Math.min(source.stock().count, mine));
                if (bound == 0) continue;
                draws.add(new Draw(source, bound, false));
                planned.put(source, bound);
                remaining.put(source.material(), mine - bound);
                left -= bound;
                if (left == 0) break;
            }
            // A player can move the materials to another chest at the same pickup point. Use only free
            // replacement stock and retire the original reservation after the worker actually takes it.
            long replacement = order == null ? left : Math.min(left, order.held(pickup.point(), need.item) - (need.count - left));
            for (var source : available) {
                if (replacement <= 0) break;
                long space = source.stock().count - planned.getOrDefault(source, 0L);
                long free = Math.min(replacement, Math.min(space, supplies.free(source)));
                if (free <= 0) continue;
                draws.add(new Draw(source, free, true));
                replacement -= free;
                left -= free;
            }
            if (left > 0) {
                if (order != null) {
                    order.shortage.removeIf(c -> c.matches(need.item));
                    order.shortage.add(new ItemCount(need.item, left));
                }
                return false;
            }
        }
        for (var draw : draws) {
            var source = draw.source;
            long moved = source.take(worker, draw.count);
            if (order != null) {
                if (draw.replacement) releaseReplacedSupply(order, pickup.point(), source.stock().item, moved);
                else releaseSupply(order, pickup.point(), source.material().store(), source.stock().item, moved);
            }
            if (moved < draw.count) {
                setChanged();
                return false;
            }
        }
        if (order != null) {
            order.shortage.clear();
            if (order.status == WorkOrder.WAIT_ITEMS) order.status = WorkOrder.RUNNING;
        }
        setChanged();
        return true;
    }

    /**
     * アンドロイド作業台の預かり箱へ、作った物を count 個入れた（持ち物に入りきらなかった）: この注文の素材置き場の物として予約する。
     * あとの工程や納品では、ほかの受け渡し場所と同じように、ここへ取りに来る。
     */
    public void holdBuffered(WorkJob job, BlockPos bench, ItemStack item, long count) {
        WorkOrder o = order(job.orderId());
        if (o != null && count > 0) {
            holdSupply(o, bench, bench, item, count);
            setChanged();
        }
    }

    /** 預かり箱から、この注文が count 個取り出した（作る前に、足りない素材を持ち物へ戻した）。 */
    public void releaseBuffered(WorkJob job, BlockPos bench, ItemStack item, long count) {
        WorkOrder o = order(job.orderId());
        if (o != null && count > 0) {
            releaseSupply(o, bench, bench, item, count);
            setChanged();
        }
    }

    /** この注文が預かり箱 bench に予約している、item の数。 */
    public long buffered(WorkJob job, BlockPos bench, ItemStack item) {
        WorkOrder o = order(job.orderId());
        return o == null ? 0 : o.held(bench, item);
    }

    /** 持ち物を、受け渡し場所 point の隣のストレージへ戻す。全部戻せたら true（入りきらなければ残る。ほかの場所へ回る）。 */
    public boolean depositAt(RuinAndroid a, @Nullable WorkJob job, BlockPos point) {
        return depositAt(a, job, point, null);
    }

    /**
     * 持ち物を point へ戻す。candidates があれば、そのうちのほかの場所の方が返却の優先度が高い物は戻さずに持っておく
     * （その物の分は false になる。あとでその場所へ戻しに行く）。
     */
    public boolean depositAt(RuinAndroid a, @Nullable WorkJob job, BlockPos point, java.util.@Nullable Collection<BlockPos> candidates) {
        WorkOrder o = job == null ? null : order(job.orderId());
        if (a.escrowRequest() != null) {
            var data = AndroidEscrowData.get((ServerLevel) level); var e = data.find(a.escrowRequest());
            if (e == null || !a.getUUID().equals(e.worker) || !e.consoleIdentity.equals(escrowConsoleIdentity)
                    || !worldPosition.equals(point)) return false;
            boolean allReturned = true;
            for (int slot : a.carrySlots()) {
                ItemStack stack = a.inventory().getItem(slot);
                ItemStack rest = data.returnCargo(e, stack);
                if (o != null && a.escrowRequest().equals(o.escrowRequest)) o.hold(worldPosition, stack, stack.getCount() - rest.getCount());
                a.inventory().setItem(slot, rest); allReturned &= rest.isEmpty();
            }
            // fluids carried in tanks go back into the same request's custody
            var fluids = LiquidStores.of(data.fluidHandler(e, false));
            for (var held : new ArrayList<>(a.liquids())) {
                if (!FluidSamples.is(held.getKey())) { allReturned = false; continue; }
                long moved = fluids.fill(held.getKey(), held.getValue(), false);
                if (moved > 0) {
                    a.drainLiquid(held.getKey(), moved, false);
                    if (o != null && a.escrowRequest().equals(o.escrowRequest)) o.hold(worldPosition, FluidSamples.key(held.getKey()), moved);
                }
                allReturned &= moved >= held.getValue();
            }
            setChanged(); return allReturned;
        }
        syncSupplyClaims();
        var stores = ItemSupplyStores.sources(this, List.of(point));
        boolean all = true;
        boolean kept = false;
        for (int slot : a.carrySlots()) {
            ItemStack s = a.inventory().getItem(slot);
            if (s.isEmpty()) {
                continue;
            }
            if (candidates != null && !returnsHere(point, s, candidates)) {
                kept = true;
                continue;
            }
            ItemStack rest = s.copy();
            for (var store : stores) {
                int before = rest.getCount();
                rest = ItemHandlerHelper.insertItemStacked(store.handler(), rest, false);
                if (o != null) holdSupply(o, point, store.key(), s, before - rest.getCount());
                if (rest.isEmpty()) break;
            }
            a.inventory().setItem(slot, rest);
            all &= rest.isEmpty();
        }
        // タンクの液体・化学物質も、隣の入れ物へ戻す
        for (var e : a.liquids()) {
            long returning = e.getValue() - (a.usesPersonalTanks() ? a.personalLiquid(e.getKey()) : 0);
            if (returning <= 0) continue;
            long moved = LiquidStores.fillAt(this, point, e.getKey(), returning, (store, amount) -> {
                if (o != null) holdSupply(o, point, store, e.getKey(), amount);
            });
            if (moved > 0) a.drainLiquid(e.getKey(), moved, false);
            all &= moved >= returning;
        }
        if (!all && o != null) {
            o.status = WorkOrder.WAIT_SPACE;
        } else if (o != null && o.status == WorkOrder.WAIT_SPACE) {
            o.status = WorkOrder.RUNNING;
        }
        setChanged();
        return all && !kept;
    }

    /** 注文の納品先（納品口がなくなっていれば、指令台）。 */
    public BlockPos destination(WorkJob job) {
        WorkOrder o = order(job.orderId());
        if (o != null && o.toStock) {
            // 素材置き場へ入れる: ストレージのある受け渡し場所（返却の優先度の高い場所、同じなら指令台を先に）
            BlockPos best = null;
            int bestPriority = -1;
            for (BlockPos p : supplyPoints()) {
                int priority = priority(p, o.target, false);
                if (priority > bestPriority && (FluidSamples.liquid(o.target)
                        ? LiquidStores.canFillAt(this, p, o.target) : !storagesAt(p, new HashSet<>()).isEmpty())) {
                    best = p;
                    bestPriority = priority;
                }
            }
            return best != null ? best : worldPosition;
        }
        // Never silently redirect an endpoint order after unloading, replacement or permission change.
        if (o != null && o.deliveryEndpointId != null && o.destination != null) return o.destination;
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
        if (o != null && o.escrowRequest != null) {
            var e = escrowEntry(o);
            if (e == null || e.state != AndroidEscrowData.State.RUNNING || !a.getUUID().equals(e.worker)
                    || !a.getUUID().equals(active.worker) || !o.escrowRequest.equals(a.escrowRequest())) return false;
            box = AndroidEscrowData.get((ServerLevel) level).productHandler(e);
        }
        if (o != null && o.deliveryEndpointId != null) {
            var endpoint = workEndpoint(at);
            if (endpoint == null || !o.deliveryEndpointId.equals(endpoint.endpointId()) || endpoint.deliveryItems() == null) {
                o.status = WorkOrder.WAIT_SPACE;
                return false;
            }
            box = endpoint.deliveryItems();
        }
        boolean all = true;
        long moved = 0;
        long limit = active.remainingDelivery;
        for (int slot : a.carrySlots()) {
            ItemStack s = a.inventory().getItem(slot);
            if (s.isEmpty() || limit <= moved || !WormholeOrders.matches(job.fetch().get(0).item, s)) {
                continue;
            }
            int give = (int) Math.min(s.getCount(), limit - moved);
            int notIn = ItemHandlerHelper.insertItemStacked(box, s.copyWithCount(give), false).getCount();
            moved += give - notIn;
            a.inventory().setItem(slot, s.copyWithCount(s.getCount() - give + notIn));
            all &= notIn == 0;
        }
        a.compactCarry();
        active.remainingDelivery = Math.max(0, active.remainingDelivery - moved);
        all = active.remainingDelivery == 0;
        if (o != null) {
            o.delivered += moved;
            var escrow = escrowEntry(o);
            if (escrow != null) { escrow.delivered = o.delivered; AndroidEscrowData.get((ServerLevel) level).setDirty(); }
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
        long moved;
        if (o != null && o.deliveryEndpointId != null) {
            var endpoint = workEndpoint(at);
            if (endpoint == null || !o.deliveryEndpointId.equals(endpoint.endpointId())) {
                o.status = WorkOrder.WAIT_SPACE;
                return false;
            }
            moved = LiquidStores.endpoint(endpoint, true).fill(like, want, false);
        } else if (o != null && o.toStock) {
            moved = LiquidStores.fillAt(this, at, like, want, (pos, amount) -> {});
        } else {
            moved = LiquidStores.at(level, at).fill(like, want, false);
        }
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
            finishExternalOrder(o, AndroidOrders.State.COMPLETED, null);
            discardClaims(o);
            orders.remove(o);
            if (o.orderer != null && level instanceof ServerLevel server) {
                io.github.genichimaruo.singulo.registry.PendingMilestones.award(server.getServer(), o.orderer, "android_order_complete");
            }
            message = Component.translatable("gui.singulo.work_console.done", o.target.getHoverName(), o.count);
            tell(o, message);
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
        ItemStack result = server.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, server)
                .map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
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

    /** Use the selected ingredient alternatives and the server recipe's actual counts, components and fluids. */
    public void setMachineProcess(MachineRecipe recipe, List<ItemStack> displayed) {
        List<ItemStack> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        if (recipe.mass().isPresent()) {
            var mass = recipe.mass().get();
            var feed = new ItemStack(mass.metalOnly() ? net.minecraft.world.item.Items.IRON_BLOCK : net.minecraft.world.item.Items.COBBLESTONE);
            double unit = MassValues.INSTANCE.massOf(feed);
            int count = (int) Math.ceil(mass.amount() / unit);
            if (unit <= 0 || count < 1 || count > 64 || mass.metalOnly() && !MassValues.isMetal(feed)) return;
            inputs.add(feed.copyWithCount(count));
        }
        int slot = 0;
        for (var in : recipe.ingredients()) {
            ItemStack shown = slot < displayed.size() ? displayed.get(slot) : ItemStack.EMPTY;
            slot++;
            var alternatives = in.ingredient().items().map(ItemStack::new).toArray(ItemStack[]::new);
            if (alternatives.length == 0) return;
            ItemStack actual = java.util.Arrays.stream(alternatives).filter(s -> ItemStack.isSameItem(s, shown))
                    .findFirst().orElse(alternatives[0]);
            inputs.add(actual.copyWithCount(Math.max(1, in.count())));
        }
        ItemStack result = recipe.result();
        if (recipe.restore().isPresent()) {
            var alternatives = recipe.restore().get().items().map(ItemStack::new).toArray(ItemStack[]::new);
            if (alternatives.length == 0) return;
            var restore = slot < displayed.size() && recipe.restore().get().test(displayed.get(slot))
                    ? displayed.get(slot).copyWithCount(1) : alternatives[0].copyWithCount(1);
            if (!UsesHelper.canRestore(restore)) restore = UsesHelper.consume(restore, UsesHelper.remaining(restore));
            if (!UsesHelper.canRestore(restore)) return;
            inputs.add(restore);
            result = UsesHelper.restore(restore, result);
        }
        for (var in : recipe.fluidIngredients()) inputs.add(FluidSamples.of(new net.neoforged.neoforge.fluids.FluidStack(in.fluid(), in.amount())));
        if (!result.isEmpty()) outputs.add(result);
        if (!recipe.byproduct().isEmpty()) outputs.add(recipe.byproduct());
        for (var out : recipe.fluidResults()) outputs.add(FluidSamples.of(out));
        setProcess(inputs, outputs);
        encoderGuaranteedExtras = outputs.size() > 1;
        for (var in : recipe.ingredients()) {
            if (in.uses() == 0) continue;
            for (int i = 0; i < GRID; i++) {
                if (in.ingredient().test(encoder.getItem(i))) {
                    encoderTools.add(new RecipeData.Tool(i, in.uses(), in.tier()));
                    break;
                }
            }
        }
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
        if (mode == MODE_PROCEDURE && (procSteps.isEmpty() || !procedureLocationsValid())) {
            return false;
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
            new RecipeData(RecipeData.MODE_PROCESSING, grid, result.copy(), extras, List.of(), encoder.getItem(FUEL).copy(),
                    faces, List.of(), 0, List.copyOf(encoderTools), encoderGuaranteedExtras).write(card, level.registryAccess());
        } else if (variantMask != 0) {
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

    boolean procedureLocationsValid() {
        return level != null && procSteps.stream().allMatch(s -> s.isIn(level));
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
    /** 同じ種類の別の物も使うかを、全部の欄でまとめて切り替える（作業台モード。全部オンなら全部オフ、そうでなければ全部オン）。 */
    public void toggleVariants() {
        variantMask = variantMask == ALL_VARIANTS ? 0 : ALL_VARIANTS;
        setChanged();
    }

    /** 見本の欄 i だけ、同じ種類の別の物も使うかを切り替える（作業台モード）。 */
    public void toggleVariantSlot(int i) {
        if (i >= 0 && i < GRID) {
            variantMask ^= 1 << i;
            setChanged();
        }
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
        net.minecraft.world.item.crafting.CraftingRecipe matched = null;
        if (level instanceof ServerLevel server) {
            net.minecraft.core.NonNullList<ItemStack> g = net.minecraft.core.NonNullList.withSize(GRID, ItemStack.EMPTY);
            for (int i = 0; i < GRID; i++) {
                g.set(i, grid.get(i).copyWithCount(1));
            }
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, g);
            matched = server.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, server)
                    .map(h -> h.value()).orElse(null);
        }
        var assigned = alternativeSlots(matched, grid);
        for (int i = 0; i < grid.size(); i++) {
            ItemStack s = grid.get(i);
            String alt = "";
            if (!s.isEmpty() && (variantMask >> i & 1) != 0) {
                var ing = assigned.get(i);
                if (ing != null && !ing.isEmpty() && ing.test(s) && ing.items().limit(2).count() > 1
                        && validAlternatives(matched, grid, i, ing)) alt = ItemCount.key(ing);
            }
            out.add(alt);
        }
        return out;
    }

    /** Assign each physical slot once, including the chosen shaped orientation. */
    private static List<net.minecraft.world.item.crafting.Ingredient> alternativeSlots(
            net.minecraft.world.item.crafting.CraftingRecipe recipe, List<ItemStack> grid) {
        net.minecraft.world.item.crafting.Ingredient empty = null;
        List<net.minecraft.world.item.crafting.Ingredient> out = new ArrayList<>(java.util.Collections.nCopies(grid.size(), empty));
        if (recipe instanceof io.github.genichimaruo.singulo.recipe.SmesUpgradeRecipe upgrade) recipe = upgrade.shapedRecipe();
        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            int left = 3, top = 3;
            for (int i = 0; i < grid.size(); i++) if (!grid.get(i).isEmpty()) {
                left = Math.min(left, i % 3); top = Math.min(top, i / 3);
            }
            for (boolean mirror : new boolean[]{false, true}) {
                boolean matches = true;
                for (int y = 0; y < shaped.getHeight(); y++) for (int x = 0; x < shaped.getWidth(); x++) {
                    int index = (top + y) * 3 + left + x;
                    var ingredient = shaped.getIngredients().get(y * shaped.getWidth() + (mirror ? shaped.getWidth() - 1 - x : x));
                    if (index >= grid.size() || !net.minecraft.world.item.crafting.Ingredient.testOptionalIngredient(ingredient, grid.get(index))) matches = false;
                    else out.set(index, ingredient.orElse(null));
                }
                if (matches) return out;
                java.util.Collections.fill(out, empty);
            }
        } else if (recipe instanceof net.minecraft.world.item.crafting.ShapelessRecipe) {
            List<Integer> slots = new ArrayList<>();
            List<ItemStack> inputs = new ArrayList<>();
            for (int i = 0; i < grid.size(); i++) if (!grid.get(i).isEmpty()) { slots.add(i); inputs.add(grid.get(i)); }
            int[] mapping = net.neoforged.neoforge.common.util.RecipeMatcher.findMatches(inputs, recipe.placementInfo().ingredients());
            if (mapping != null) for (int i = 0; i < mapping.length; i++) out.set(slots.get(i), recipe.placementInfo().ingredients().get(mapping[i]));
        }
        return out;
    }

    /** Overlapping ingredients must not broaden a different shaped slot. */
    private boolean validAlternatives(net.minecraft.world.item.crafting.CraftingRecipe recipe,
                                      List<ItemStack> grid, int slot,
                                      net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (recipe == null || level == null) return false;
        List<ItemStack> probe = new ArrayList<>(grid);
        for (ItemStack candidate : ingredient.items().map(holder -> new ItemStack(holder)).toList()) {
            probe.set(slot, candidate.copyWithCount(1));
            if (!recipe.matches(net.minecraft.world.item.crafting.CraftingInput.of(3, 3, probe), level)) return false;
        }
        return true;
    }

    /**
     * 加工モードの面を切り替える（which: 0 入れる面、1 取り出す面、2 燃料の面）。
     * 自動 → 上 → 下 → 正面 → 背面 → 左 → 右 → 北 → 南 → 西 → 東 → 自動 の順（back なら逆）。
     */
    public void cycleFace(int which, boolean back) {
        int choice = Math.floorMod(RecipeData.faceChoice(faces, which) + (back ? -1 : 1), RecipeData.FACE_CHOICES);
        faces = RecipeData.withFaceChoice(faces, which, choice);
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
            if (pb != null) {
                if (!WorkLocationCardItem.sameDimension(b, level)) {
                    return Component.translatable("gui.singulo.recipe_encoder.step.no_place");
                }
                if (Math.abs(pb.getX() - pos.getX()) >= MINE_SPAN || Math.abs(pb.getY() - pos.getY()) >= MINE_SPAN
                        || Math.abs(pb.getZ() - pos.getZ()) >= MINE_SPAN) {
                    return Component.translatable("gui.singulo.recipe_encoder.step.too_big", MINE_SPAN);
                }
                corner = pb;
            }
        }
        int option = ProcedureStep.hasOption(stepKind) ? stepOption : ProcedureStep.defaultOption(stepKind);
        procSteps.add(new ProcedureStep(stepKind, pos, corner, stepKind == ProcedureStep.USE ? ItemStack.EMPTY : item, option).recordedIn(level));
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
        be.syncSupplyClaims();
        be.eyes.tick(level, pos, state);
        long phase = Math.floorMod(pos.hashCode(), 20);
        if ((level.getGameTime() + phase) % 20 == 0) {
            be.tick((ServerLevel) level);
        }
        if ((level.getGameTime() + phase + 5) % 10 == 0) {
            SupplyPortBlockEntity.pushOut(level, pos, be.outbox);     // 納品箱の中身を、隣の箱へ出す
        }
    }

    private void tick(ServerLevel level) {
        if (migrateSupplyClaims) migrateLegacyClaims();
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
            // 素材を待ったまま長く経ったら、何が足りないかを注文した人へ知らせる（1回だけ）
            if (o.status == WorkOrder.WAIT_ITEMS) {
                if (o.waitingSince < 0) {
                    o.waitingSince = now;
                } else if (!o.stallNotified && now - o.waitingSince >= STALL_NOTIFY_TICKS) {
                    o.stallNotified = true;
                    List<ItemCount> missing = o.missing();
                    tell(o, missing.isEmpty()
                            ? Component.translatable("gui.singulo.work_console.notify.waiting", o.target.getHoverName())
                            : Component.translatable(missing.size() > 1 ? "gui.singulo.work_console.notify.missing.more" : "gui.singulo.work_console.notify.missing",
                            o.target.getHoverName(),
                            missing.get(0).item.getHoverName(), missing.get(0).count, missing.size() - 1));
                }
            } else {
                o.waitingSince = -1;
                o.stallNotified = false;
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
        CompoundTag common = null;
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof WorkConsoleMenu menu && worldPosition.equals(menu.pos()) && mayOperate(p)) {
                if (common == null) {
                    common = WorkConsoleView.common(this);
                }
                sendSnapshot(p, menu.containerId, WorkConsoleView.forPlayer(this, p, null, common), false);
            }
        }
    }

    /** 画面に様子を送る（plan は計算の結果。なければ null）。 */
    public void sendView(ServerPlayer player, int containerId, @Nullable CompoundTag plan) {
        sendSnapshot(player, containerId, WorkConsoleView.build(this, player, plan), true);
    }

    private void sendSnapshot(ServerPlayer player, int containerId, CompoundTag snapshot, boolean force) {
        if (player.containerMenu instanceof WorkConsoleMenu menu && menu.containerId == containerId && (menu.shouldSend(snapshot) || force)) {
            PacketDistributor.sendToPlayer(player, new WorkConsoleViewPayload(containerId, snapshot));
        }
    }

    // ------------------------------------------------------------------ 画面

    @Override
    public void openMenu(ServerPlayer player) {
        if (owner == null) {
            setOwner(player.getUUID());
        }
        if (!mayOperate(player)) {
            player.sendOverlayMessage(Component.translatable("gui.singulo.work_console.locked"));
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
        if (level instanceof ServerLevel server) {
            SupplyReservations.get(server).clear(server, worldPosition);
            for (WorkOrder o : new ArrayList<>(orders)) if (o.escrowRequest != null) cancel(o.id, null);
            for (var e : AndroidEscrowData.get(server).entries())
                if (e.consoleIdentity.equals(escrowConsoleIdentity) && e.state == AndroidEscrowData.State.RUNNING)
                    AndroidEscrowData.get(server).markTerminal(e, AndroidOrders.State.CANCELLED, e.delivered);
        }
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
        tag.store("escrow_console_identity", net.minecraft.core.UUIDUtil.CODEC, escrowConsoleIdentity);
        if (owner != null) {
            tag.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
        }
        tag.put("permit", io.github.genichimaruo.singulo.compat.NbtCompat.serialize(permit, registries));
        tag.put("routes", io.github.genichimaruo.singulo.compat.NbtCompat.serialize(routes, registries));
        ListTag ls = new ListTag();
        for (BlockPos p : links) {
            ls.add(io.github.genichimaruo.singulo.compat.NbtCompat.writeBlockPos(p));
        }
        tag.put("links", ls);
        ListTag endpoints = new ListTag();
        endpointLinks.forEach((p, link) -> {
            CompoundTag e = new CompoundTag(); e.putLong("pos", p.asLong());
            e.store("identity", net.minecraft.core.UUIDUtil.CODEC, link.identity()); e.store("owner", net.minecraft.core.UUIDUtil.CODEC, link.owner());
            if (link.face() != null) e.putString("face", link.face().getSerializedName());
            endpoints.add(e);
        });
        tag.put("work_endpoints_v1", endpoints);
        ListTag os = new ListTag();
        for (WorkOrder o : orders) {
            os.add(o.save(registries));
        }
        tag.put("orders", os);
        ListTag receipts = new ListTag();
        for (var entry : externalOrders.values()) receipts.add(entry.save(registries));
        tag.put("external_orders_v1", receipts);
        tag.putInt("next_order", nextOrderId);
        tag.putInt("next_job", nextJobId);
        ListTag jobs = new ListTag();
        for (WorkJob.Lease lease : leases) {
            CompoundTag job = new CompoundTag();
            job.put("job", lease.job.save(registries));
            job.store("worker", net.minecraft.core.UUIDUtil.CODEC, lease.worker);
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
        tag.put("encoder", io.github.genichimaruo.singulo.compat.NbtCompat.saveAllItems(new CompoundTag(), enc, registries));
        tag.putInt("encoder_mode", mode);
        tag.putInt("step_kind", stepKind);
        tag.putInt("step_option", stepOption);
        tag.putInt("faces", faces);
        tag.putInt("variant_mask", variantMask);
        tag.putIntArray("machine_tools", RecipeData.saveTools(encoderTools));
        if (encoderGuaranteedExtras) tag.putBoolean("guaranteed_extras", true);
        ListTag ps = new ListTag();
        for (ProcedureStep s : procSteps) {
            ps.add(s.save(registries));
        }
        tag.put("proc_steps", ps);
        net.minecraft.core.NonNullList<ItemStack> box = net.minecraft.core.NonNullList.withSize(OUTBOX_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < OUTBOX_SLOTS; i++) {
            box.set(i, outbox.getItem(i));
        }
        tag.put("outbox", io.github.genichimaruo.singulo.compat.NbtCompat.saveAllItems(new CompoundTag(), box, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        escrowConsoleIdentity = tag.read("escrow_console_identity", net.minecraft.core.UUIDUtil.CODEC).isPresent() ? tag.read("escrow_console_identity", net.minecraft.core.UUIDUtil.CODEC).orElseThrow() : UUID.randomUUID();
        owner = tag.read("owner", net.minecraft.core.UUIDUtil.CODEC).isPresent() ? tag.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElseThrow() : null;
        if (tag.contains("permit")) {
            io.github.genichimaruo.singulo.compat.NbtCompat.deserialize(permit, registries, tag.getCompoundOrEmpty("permit"));
        }
        CompoundTag rt = tag.getCompoundOrEmpty("routes");
        rt.putInt("Size", PathRule.SLOTS);
        io.github.genichimaruo.singulo.compat.NbtCompat.deserialize(routes, registries, rt);
        links.clear();
        endpointLinks.clear();
        for (Tag entry : tag.getListOrEmpty("work_endpoints_v1")) {
            CompoundTag e = (CompoundTag) entry;
            BlockPos p = BlockPos.of(e.getLongOr("pos", 0L));
            Direction face = Direction.byName(e.getStringOr("face", ""));
            if (e.contains("face") && face == null) continue;
            if (endpointLinks.size() < 64 && e.read("identity", net.minecraft.core.UUIDUtil.CODEC).isPresent() && e.read("owner", net.minecraft.core.UUIDUtil.CODEC).isPresent()
                    && !p.equals(worldPosition) && p.closerThan(worldPosition, LINK_RANGE))
                endpointLinks.putIfAbsent(p, new EndpointLink(e.read("identity", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), e.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), face));
        }
        for (Tag t : tag.getListOrEmpty("links")) {
            io.github.genichimaruo.singulo.compat.NbtCompat.readBlockPos(wrap(t), "p").ifPresent(links::add);
        }
        restoreSupplyClaims = true;
        orders.clear();
        for (Tag t : tag.getListOrEmpty("orders")) {
            WorkOrder o = WorkOrder.load((CompoundTag) t, registries);
            if (o != null) {
                orders.add(o);
            }
        }
        externalOrders.clear();
        for (Tag t : tag.getListOrEmpty("external_orders_v1")) {
            var entry = ExternalOrderLedger.load((CompoundTag) t, registries);
            if (entry != null && externalOrders.size() < AndroidOrders.MAX_RECEIPTS) externalOrders.putIfAbsent(entry.request, entry);
        }
        nextOrderId = Math.max(1, tag.getIntOr("next_order", 0));
        nextJobId = Math.max(1, tag.getIntOr("next_job", 0));
        leases.clear();
        restoredLeases = true;
        for (Tag element : tag.getListOrEmpty("leases")) {
            CompoundTag t = (CompoundTag) element;
            WorkJob job = WorkJob.load(t.getCompoundOrEmpty("job"), registries);
            WorkOrder order = job == null ? null : order(job.orderId());
            if (job == null || order == null || !t.read("worker", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
                continue;
            }
            var lease = new WorkJob.Lease(job, t.read("worker", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), t.getLongOr("last_beat", 0L));
            lease.remainingDelivery = Math.max(0, Math.min(job.amount(), t.getLongOr("remaining_delivery", 0L)));
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
        for (Tag t : tag.getListOrEmpty("standing")) {
            StandingOrder s = StandingOrder.load((CompoundTag) t, registries);
            if (s != null) {
                standing.add(s);
            }
        }
        nextStandingId = Math.max(1, tag.getIntOr("next_standing", 0));
        powered = tag.getBooleanOr("powered", false);
        net.minecraft.core.NonNullList<ItemStack> enc = net.minecraft.core.NonNullList.withSize(ENCODER_SLOTS, ItemStack.EMPTY);
        io.github.genichimaruo.singulo.compat.NbtCompat.loadAllItems(tag.getCompoundOrEmpty("encoder"), enc, registries);
        mode = Math.floorMod(tag.getIntOr("encoder_mode", 0), MODES);
        stepKind = Math.floorMod(tag.getIntOr("step_kind", 0), ProcedureStep.KINDS);
        stepOption = tag.contains("step_option") ? tag.getIntOr("step_option", 0) : ProcedureStep.defaultOption(stepKind);
        faces = tag.contains("faces") ? tag.getIntOr("faces", 0) : RecipeData.DEFAULT_FACES;
        // 0.11 までは全部の欄でまとめた切り替えだった
        variantMask = tag.contains("variant_mask") ? tag.getIntOr("variant_mask", 0) & ALL_VARIANTS
                : tag.getBooleanOr("variants", false) ? ALL_VARIANTS : 0;
        procSteps.clear();
        for (Tag t : tag.getListOrEmpty("proc_steps")) {
            if (procSteps.size() < RecipeData.MAX_STEPS) {
                procSteps.add(ProcedureStep.load((CompoundTag) t, registries));
            }
        }
        for (int i = 0; i < ENCODER_SLOTS; i++) {
            encoder.setItem(i, i == FUEL ? RecipeData.fuelItem(enc.get(i)) : enc.get(i));
        }
        encoderTools.addAll(RecipeData.readTools(tag.getIntArray("machine_tools").orElseGet(() -> new int[0])));
        encoderGuaranteedExtras = tag.getBooleanOr("guaranteed_extras", false);
        net.minecraft.core.NonNullList<ItemStack> box = net.minecraft.core.NonNullList.withSize(OUTBOX_SLOTS, ItemStack.EMPTY);
        io.github.genichimaruo.singulo.compat.NbtCompat.loadAllItems(tag.getCompoundOrEmpty("outbox"), box, registries);
        for (int i = 0; i < OUTBOX_SLOTS; i++) {
            outbox.setItem(i, box.get(i));
        }
    }

    private static CompoundTag wrap(Tag t) {
        CompoundTag c = new CompoundTag();
        c.put("p", t);
        return c;
    }

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> transferItems() {
        return net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(outbox);
    }
}
