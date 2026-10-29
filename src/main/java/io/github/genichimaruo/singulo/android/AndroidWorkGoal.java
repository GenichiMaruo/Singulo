package io.github.genichimaruo.singulo.android;

import net.minecraft.nbt.CompoundTag;
import io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity;
import io.github.genichimaruo.singulo.android.work.FluidSamples;
import io.github.genichimaruo.singulo.android.work.ItemCount;
import io.github.genichimaruo.singulo.android.work.RecipeData;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import io.github.genichimaruo.singulo.android.work.PathRetry;
import io.github.genichimaruo.singulo.android.work.WorkAreaSearch;
import io.github.genichimaruo.singulo.android.work.WorkJob;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import io.github.genichimaruo.singulo.compat.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 作業（自動化）: 作業指令台から作業をもらって進める。
 * <ul>
 * <li>CRAFT: 資材口へ行き素材を取る → 作業場所の目の前へ行き作る（1回 {@link #CRAFT_TICKS}）。</li>
 * <li>DELIVER: 資材口へ行き完成品を取る → 納品口へ届ける。</li>
 * <li>作り終えた・届け終えたら、持っている物だけで続けられる作業（次の工程・完成品の納品）があれば、資材口へ戻らずにそのまま続ける。
 * なければ、持ち物を資材口へ戻す。</li>
 * <li>加工モードのレシピ: かまどや装置に、上から素材を入れ、下からできた物を取り出す（できるまでそばで待つ）。
 * 主な物のほかに出てきた物（副産物）も持ち帰り、資材口へ戻す。{@link #STALL_TICKS} のあいだ何も進まなければ、注文を取り消す。</li>
 * <li>手順カード: 手順を上から順に行う（投入・取り出し・採掘。数は作る回数分）。採掘は、範囲のブロックを上から掘り、
 * 目当ての物がそろうまで続ける（掘れる物がない状態が {@link #MINE_IDLE_TICKS} 続けば、注文を取り消す）。</li>
 * <li>DEPOSIT / EXIT: 持ち物を資材口へ戻す（EXIT はそのあと作業をやめる）。</li>
 * </ul>
 * ブロックの所では、隣の立てる場所（ブロックの目の前）まで歩いて、ブロックの方を向く。資材口・納品口では少し立ち止まってから受け渡す。
 * どうしてもたどり着けなければ（{@link #GIVE_UP_TICKS}）、作業指令台に知らせて注文を取り消してもらう。充電が切れたら、充電してから続ける。
 */
final class AndroidWorkGoal extends Goal {
    /** 作業台で1回作る時間（tick）。 */
    static final int CRAFT_TICKS = 40;
    /** 加工モード: 装置に入れる・取り出すのを試す間隔（tick）と、何も進まないときにあきらめるまで。 */
    private static final int PROCESS_INTERVAL = 10, STALL_TICKS = 20 * 120;
    /**
     * 装置の加工を待たずに離れる: 入れ終えて {@link #LEAVE_TICKS} 待ち、残りが {@link #LEAVE_ETA_TICKS} 以上かかりそうなとき
     * （残り時間が分からない装置は、{@link #LEAVE_UNKNOWN_TICKS} 待ってもできなければ）。
     */
    private static final int LEAVE_TICKS = 20, LEAVE_ETA_TICKS = 20 * 15, LEAVE_UNKNOWN_TICKS = 20 * 10;
    /** 採掘: 掘れる物がないまま待つ限度、手の届く距離、1つ掘るごとに使う充電。 */
    private static final int MINE_IDLE_TICKS = 20 * 30, MINE_COST = 6;
    /** 伐採の充電: 基本料金＋原木1本ごと（0.8.x までは木の大きさによらず24。大きな木ほど高くした）。 */
    static final int FELL_BASE_COST = 12, FELL_COST_PER_LOG = 2;
    private static final double MINE_REACH = 4.0, MINE_NEAR = 2.0;
    /** 1回作るごとに使う充電。 */
    static final int CRAFT_COST = 10;
    /** 立てる場所が見つからないときの、着いたとみなす距離（ブロックの中心から、横）。 */
    static final double REACH = 2.4;
    /** 目的地へ向かって、これだけ経っても着かなければあきらめる。 */
    static final int GIVE_UP_TICKS = 600;
    /** 次の作業を聞きに行くまで（何もないとき）。 */
    static final int WAIT_TICKS = 40;
    /** 資材口・納品口の前で立ち止まる時間。 */
    static final int HANDOVER_TICKS = 10;
    /** 目の前の地面の中央とみなす距離と、向いてから作業に移るまで。 */
    static final double CENTER = 0.3;
    static final int FACE_TICKS = 3;

    private final RuinAndroid android;
    /** 今の作業の段階（0: 取りに行く、1: 作業場所・納品口へ）。 */
    private int phase;
    /** 持ち物を資材口へ戻しに行くところ（作業のあと）。 */
    private boolean returning;
    private int travel;
    private int wait;
    private long crafted;
    /** 加工モード: 装置に入れた回数、取り出した主な物の数、何も進まなかった時間。 */
    private long inserted, collected;
    @Nullable
    private java.util.List<ItemCount> processingPending;
    private boolean processingPaid, processingMoved;
    private int stalled;
    /** 加工モード: 素材を入れ終えてから、できるのを待っている時間（長くかかりそうなら、ほかの作業へ行く）。 */
    private int machineWait;
    /** 次の作業の最初の受け渡し場所で、前の作業の残り（注文 leftoverOrder の物）を戻してから取る。 */
    private boolean depositFirst;
    private int leftoverOrder = -1;
    /** 手順カード: 今の手順の番号と、その手順で入れた・取り出した・集めた数。 */
    private int procIndex;
    private long progress;
    /** 採掘: 掘っているブロックと、掘った時間・かかる時間、掘る物がない時間、たどり着けなかったブロック。 */
    @Nullable
    private BlockPos mineTarget;
    private int mineTimer, mineNeed, mineIdle;
    /** 掘るブロックのそばに着いた（これ以上は近づかない）。 */
    private boolean mineSettled;
    private final java.util.Set<BlockPos> mineSkip = new java.util.HashSet<>();
    /** まとめた作業の、今作っている工程の番号。 */
    private int batchIndex;
    private int craftTimer;
    /** 上位のアンドロイド作業台で、まだ作っていない分のたまった時間（1回分に満たない端数を次の音へ持ち越す）。 */
    private int craftBank;
    /** 向かっているブロックと、その目の前の立つ場所。着いてからの時間。 */
    @Nullable
    private BlockPos goalBlock;
    @Nullable
    private BlockPos standAt;
    private int arrived;
    /** 素材を取りに回る受け渡し場所（実際に入っている所）と、次に行く番号。 */
    @Nullable
    private java.util.List<WorkConsoleBlockEntity.Pickup> pickups;
    private int pickupIndex;
    /** 持ち物を戻しに回る受け渡し場所（近い順。いっぱいなら次へ）と、次に行く番号、たどり着けた所があるか。 */
    @Nullable
    private java.util.List<BlockPos> dropPoints;
    /** 今戻しに行っている場所（dropPoints のうち。着くか、たどり着けなければ外して選び直す）。 */
    @Nullable
    private BlockPos dropTarget;
    private boolean dropReached;

    AndroidWorkGoal(RuinAndroid android) {
        this.android = android;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** 製作・投入・手順・素材受取の途中を、アンドロイドと一緒に保存する。 */
    net.minecraft.nbt.CompoundTag saveWork() {
        var t = new net.minecraft.nbt.CompoundTag();
        var registries = android.level().registryAccess();
        if (android.workJob != null) {
            t.put("job", android.workJob.save(registries));
        }
        t.putInt("phase", phase);
        t.putBoolean("returning", returning);
        t.putLong("crafted", crafted);
        t.putLong("inserted", inserted);
        t.putLong("collected", collected);
        if (processingPending != null) {
            var inputs = new net.minecraft.nbt.ListTag();
            for (ItemCount input : processingPending) {
                inputs.add(input.save(registries));
            }
            t.put("processing_pending", inputs);
            t.putBoolean("processing_paid", processingPaid);
        }
        t.putInt("batch", batchIndex);
        t.putInt("procedure", procIndex);
        t.putLong("progress", progress);
        t.putBoolean("deposit_first", depositFirst);
        t.putInt("leftover_order", leftoverOrder);
        if (pickups != null) {
            var remaining = new net.minecraft.nbt.ListTag();
            for (int i = pickupIndex; i < pickups.size(); i++) {
                var pickup = pickups.get(i);
                var pt = new net.minecraft.nbt.CompoundTag();
                pt.putLong("point", pickup.point().asLong());
                var items = new net.minecraft.nbt.ListTag();
                for (ItemCount count : pickup.items()) {
                    items.add(count.save(registries));
                }
                pt.put("items", items);
                remaining.add(pt);
            }
            t.put("pickups", remaining);
        }
        return t;
    }

    /** 移動経路は探し直すが、消費・製作・受取済みの数は巻き戻さない。 */
    void loadWork(net.minecraft.nbt.CompoundTag t) {
        android.workJob = WorkJob.load(t.getCompound("job"), android.level().registryAccess());
        phase = t.getInt("phase");
        returning = t.getBoolean("returning");
        crafted = t.getLong("crafted");
        inserted = t.getLong("inserted");
        collected = t.getLong("collected");
        processingPending = null;
        processingPaid = t.getBoolean("processing_paid");
        processingMoved = false;
        if (t.contains("processing_pending")) {
            processingPending = new java.util.ArrayList<>();
            for (var input : t.getList("processing_pending", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                processingPending.add(ItemCount.load((net.minecraft.nbt.CompoundTag) input, android.level().registryAccess()));
            }
        }
        batchIndex = t.getInt("batch");
        procIndex = t.getInt("procedure");
        progress = t.getLong("progress");
        depositFirst = t.getBoolean("deposit_first");
        leftoverOrder = t.contains("leftover_order") ? t.getInt("leftover_order") : android.workOrder();
        pickups = null;
        pickupIndex = 0;
        if (t.contains("pickups")) {
            pickups = new java.util.ArrayList<>();
            for (var element : t.getList("pickups", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                var pt = (net.minecraft.nbt.CompoundTag) element;
                var items = new java.util.ArrayList<ItemCount>();
                for (var item : pt.getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    var count = ItemCount.load((net.minecraft.nbt.CompoundTag) item, android.level().registryAccess());
                    if (count != null) {
                        items.add(count);
                    }
                }
                pickups.add(new WorkConsoleBlockEntity.Pickup(BlockPos.of(pt.getLong("point")), items));
            }
        }
        travel = wait = craftTimer = craftBank = stalled = mineIdle = machineWait = 0;
        stopMining();
        mineSkip.clear();
        ranchSkip.clear();
        ranchTarget = null;
        goalBlock = standAt = fuelTrip = null;
        pathRetry.reset();
        dropPoints = null;
    }

    @Override
    public boolean canUse() {
        // 襲われて戦っている間は止める（作業の進みは残る。片付けたら、止めた所から続ける）
        return android.isGuard() && android.workConsole() != null && android.active() && android.printed() && android.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        if (android.state() == RuinAndroid.STATE_CRAFTING || android.state() == RuinAndroid.STATE_MINING) {
            android.setState(RuinAndroid.STATE_IDLE);
        }
        stopMining();
        android.getNavigation().stop();
        goalBlock = null;
    }

    @Nullable
    private WorkConsoleBlockEntity console(ServerLevel level) {
        BlockPos p = android.workConsole();
        if (p == null || !level.isLoaded(p)) {
            return null;
        }
        if (level.getBlockEntity(p) instanceof WorkConsoleBlockEntity c) {
            return c;
        }
        android.clearWork();                  // 作業指令台が壊された
        return null;
    }

    /** 新しい作業を始める（持っている物で続ける作業なら、取りに行く段階を飛ばす）。 */
    private void begin(WorkJob job) {
        android.workJob = job;
        pathRetry.reset();
        phase = job.carried() ? 1 : 0;
        returning = false;
        pickups = null;
        dropPoints = null;
        travel = 0;
        crafted = 0;
        craftTimer = 0;
        craftBank = 0;
        batchIndex = 0;
        inserted = 0;
        collected = 0;
        processingPending = null;
        processingPaid = processingMoved = false;
        stalled = 0;
        machineWait = 0;
        depositFirst = false;
        fuelTrip = null;
        resetProcedure();
        if (job.type() == WorkJob.Type.COLLECT && !job.batch().isEmpty()) {
            inserted = job.batch().get(0).crafts();          // 素材はもう入れてある: 取り出すだけ
        }
    }

    private void resetProcedure() {
        procIndex = 0;
        progress = 0;
        stopMining();
        mineIdle = 0;
        mineSkip.clear();
        ranchTarget = null;
        ranchSkip.clear();
    }

    @Override
    public void tick() {
        if (!(android.level() instanceof ServerLevel level)) {
            return;
        }
        // 充電切れ・充電中・襲われているときは何もしない。目的の見直しは1tickおきなので、充電切れになった次のtickにも
        // 作業が呼ばれ、作る姿勢に戻して「動ける」状態にしてしまい、充電に行かずに切れと起動を繰り返していた
        if (!canUse()) {
            if (android.state() == RuinAndroid.STATE_CRAFTING || android.state() == RuinAndroid.STATE_MINING) {
                android.setState(RuinAndroid.STATE_IDLE);
            }
            return;
        }
        WorkConsoleBlockEntity c = console(level);
        if (c == null) {
            return;
        }
        if (android.tickCount % 20 == 5) {
            android.setWorkRules(c.pathRules());       // 作業指令台の「経路」
        }
        // 取消・再割当てを毎tick確認し、残った古い作業で素材を消費しない。
        if (android.workJob != null && android.workJob.jobId() >= 0 && !c.heartbeat(android, android.workJob)) {
            android.workJob = null;
            returning = false;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (returning) {
            android.setActivity(RuinAndroid.ACT_RETURN);
            returnItems(c);
            return;
        }
        WorkJob job = android.workJob;
        if (job == null) {
            // 作業の頼みは、作業者ごとに決まった tick にだけ出す（大勢の作業者の頼みが同じ tick に重ならないように）。
            // 預かりの更新や取り消しの確かめは、この上で毎 tick 続ける
            if (Math.floorMod(level.getGameTime(), REQUEST_PHASES) != Math.floorMod(android.getUUID().hashCode(), REQUEST_PHASES)) {
                return;
            }
            begin(c.requestJob(android));
            job = android.workJob;
        }
        android.setActivity(activityFor(job));
        switch (job.type()) {
            case WAIT -> {
                if (android.state() == RuinAndroid.STATE_CRAFTING) {
                    android.setState(RuinAndroid.STATE_IDLE);
                }
                moveNear(c.nearestSupply(android.blockPosition()));
                android.workJob = null;
                wait = WAIT_TICKS;
            }
            case DEPOSIT, EXIT -> deposit(c, job);
            case CRAFT, COLLECT -> craft(level, c, job);
            case DELIVER -> deliver(c, job);
        }
    }

    /** 作業の今の段階を、画面と頭の上のアイコンに出す形にする。 */
    private int activityFor(WorkJob job) {
        return switch (job.type()) {
            case WAIT -> RuinAndroid.ACT_WAIT;
            case DEPOSIT, EXIT -> RuinAndroid.ACT_RETURN;
            case DELIVER -> phase == 0 ? RuinAndroid.ACT_FETCH : RuinAndroid.ACT_DELIVER;
            case CRAFT, COLLECT -> {
                if (phase == 0) {
                    yield RuinAndroid.ACT_FETCH;
                }
                if (batchIndex >= job.batch().size() || job.batch().get(batchIndex).recipe() == null) {
                    yield RuinAndroid.ACT_CRAFT;
                }
                RecipeData r = job.batch().get(batchIndex).recipe();
                if (r.procedure()) {
                    int kind = procIndex < r.steps().size() ? r.steps().get(procIndex).kind() : -1;
                    yield switch (kind) {
                        case io.github.genichimaruo.singulo.android.work.ProcedureStep.MINE -> RuinAndroid.ACT_MINE;
                        case io.github.genichimaruo.singulo.android.work.ProcedureStep.HARVEST -> RuinAndroid.ACT_FARM;
                        case io.github.genichimaruo.singulo.android.work.ProcedureStep.FELL -> RuinAndroid.ACT_FELL;
                        case io.github.genichimaruo.singulo.android.work.ProcedureStep.BREED, io.github.genichimaruo.singulo.android.work.ProcedureStep.SHEAR, io.github.genichimaruo.singulo.android.work.ProcedureStep.MILK, io.github.genichimaruo.singulo.android.work.ProcedureStep.CULL -> RuinAndroid.ACT_RANCH;
                        case io.github.genichimaruo.singulo.android.work.ProcedureStep.USE -> RuinAndroid.ACT_USE;
                        default -> RuinAndroid.ACT_PROCESS;
                    };
                }
                yield r.processing() ? RuinAndroid.ACT_PROCESS : RuinAndroid.ACT_CRAFT;
            }
        };
    }

    /** 持ち物を資材の受け渡し場所へ戻す。EXIT なら、そのあと作業をやめる。 */
    private void deposit(WorkConsoleBlockEntity c, WorkJob job) {
        Boolean done = dropAll(c, job);
        if (done == null || done) {
            finishDeposit(job);                // 戻し終えた（たどり着ける所がなければ、あきらめて持ったまま）
        }
    }

    /**
     * 持ち物を、近い受け渡し場所から順に戻す（いっぱいなら次の場所へ）。全部戻したら true、続けているあいだは false、
     * どこにもたどり着けなければ null。どこもいっぱいなら、少し待って近い順にやり直す。
     */
    @Nullable
    private Boolean dropAll(WorkConsoleBlockEntity c, WorkJob job) {
        if (android.carryEmpty()) {
            dropPoints = null;
            return true;
        }
        if (dropPoints == null) {
            dropPoints = c.supplyPoints();
            dropTarget = null;
            dropReached = false;
        }
        if (dropPoints.isEmpty()) {
            boolean reached = dropReached;
            dropPoints = null;
            if (!reached) {
                return null;
            }
            wait = WAIT_TICKS;
            return false;
        }
        if (dropTarget == null) {
            dropTarget = c.nextReturnPoint(android, dropPoints, android.blockPosition());
        }
        BlockPos point = dropTarget;
        Boolean at = go(point, HANDOVER_TICKS);
        if (at == null) {
            dropPoints.remove(point);
            dropTarget = null;
        } else if (at) {
            dropReached = true;
            // 返却の優先の登録があれば、ほかの場所の方が優先する物は持ったまま次へ
            if (c.depositAt(android, job, point, dropPoints)) {
                dropPoints = null;
                return true;
            }
            if (c.returnsAnyHere(android, point, dropPoints)) {
                android.setStall(RuinAndroid.STALL_FULL);     // ここに戻したい物が入りきらない: いっぱい
            }
            dropPoints.remove(point);
            dropTarget = null;
        }
        return false;
    }

    private void finishDeposit(WorkJob job) {
        android.workJob = null;
        if (job.type() == WorkJob.Type.EXIT) {
            android.clearWork();
        }
    }

    /**
     * 作業のあと: 持っている物で続けられればそのまま続ける。なければ次の作業を決めて、素材を取りに行く最初の受け渡し場所で
     * 持ち物を戻してから取る（戻しに行くだけの道を省く）。取りに行く作業がなければ、持ち物を資材口へ戻しに行く。
     */
    private void afterJob(WorkConsoleBlockEntity c, WorkJob done) {
        WorkJob next = c.chainJob(android, done.orderId());
        if (next != null) {
            begin(next);
            return;
        }
        android.workJob = null;
        travel = 0;
        leftoverOrder = done.orderId();
        returning = false;
        if (android.carryEmpty()) {
            return;
        }
        next = c.requestJob(android, true);
        if ((next.type() == WorkJob.Type.CRAFT || next.type() == WorkJob.Type.DELIVER) && !next.carried() && !next.fetch().isEmpty()) {
            begin(next);
            depositFirst = true;
            return;
        }
        // requestJob は見積もりではなく貸出。今回は着手しないので、予約を必ず返す。
        if (next.jobId() >= 0) {
            c.fail(next, WorkConsoleBlockEntity.Failure.DEFERRED);
        }
        returning = true;
    }

    /** 作業のあと、持ち物を資材の受け渡し場所へ戻す。 */
    private void returnItems(WorkConsoleBlockEntity c) {
        Boolean done = dropAll(c, WorkJob.simple(WorkJob.Type.DEPOSIT, leftoverOrder));
        if (done == null || done) {
            returning = false;                 // 戻せない所しかなければ、次に作業をもらうときにもう一度戻しに行く
        }
    }

    private void craft(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job) {
        if (phase == 0) {
            fetch(c, job);
            return;
        }
        if (batchIndex >= job.batch().size()) {
            c.complete(job);
            afterJob(c, job);
            return;
        }
        WorkJob.Batch step = job.batch().get(batchIndex);
        if (fuelTrip != null && step.recipe() != null) {
            fuelRun(c, step.recipe().fuel());              // 燃料を取りに行っている
            return;
        }
        if (step.recipe() != null && step.recipe().procedure()) {
            procedure(level, c, job, step);
            return;
        }
        BlockPos at = step.location();
        Boolean arrived = go(at, 0);
        if (arrived == null) {
            fail(c, job, WorkConsoleBlockEntity.Failure.UNREACHABLE);
            return;
        }
        if (!arrived) {
            return;
        }
        Vec3 look = Vec3.atCenterOf(at);
        android.getLookControl().setLookAt(look.x, look.y, look.z, 90, 90);
        if (android.state() != RuinAndroid.STATE_CRAFTING) {
            android.setState(RuinAndroid.STATE_CRAFTING);
        }
        if (step.recipe() != null && level.getGameTime() % 20 == 0) {
            c.wakeRacks(at, step.recipe());        // 使っているレシピの棚も、目を開ける
        }
        if (step.recipe() != null && step.recipe().processing()) {
            process(level, c, job, step);
            return;
        }
        // 1回あたりの時間（per）と、音と火花の間隔（beat）。上位のアンドロイド作業台は、1回の音でまとめて何回分も作る
        // （作る速さは per のまま。たまった時間を craftBank に持ち越すので、割り切れない分もずれない）
        net.minecraft.world.level.block.Block block = android.level().getBlockState(at).getBlock();
        int bench = io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity.craftTicks(block);
        int per = Math.max(1, android.workTicks(bench > 0 ? bench : CRAFT_TICKS));
        int beat = bench > 0 ? Math.max(per, android.workTicks(io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity.beatTicks(block)))
                : per;
        if (++craftTimer < beat) {
            return;
        }
        craftBank += craftTimer;
        craftTimer = 0;
        // 1回あたりの充電の減り（上位のアンドロイド作業台ほど少ない）
        int cost = bench > 0 ? io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity.craftCost(block) : CRAFT_COST;
        if (android.charge() < cost) {
            android.spend(cost);
            return;
        }
        long n = Math.min(Math.min(craftBank / per, step.crafts() - crafted), Math.max(1, android.charge() / cost));
        craftBank -= craftBank / per * per;
        int made = 0;
        while (made < n && craftOnce(level, c, job, at, step.recipe())) {
            made++;
        }
        if (made == 0 && craftStorageFull) {
            pauseCraft(c, job);
            return;
        }
        if (made == 0) {
            android.setState(RuinAndroid.STATE_IDLE);
            fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
            return;
        }
        android.playSound(SoundEvents.VILLAGER_WORK_TOOLSMITH, 0.6F, 1.4F);
        android.sparks(level, Vec3.atCenterOf(at).add(0, 0.6, 0), 4, 0.25);
        crafted += made;
        android.spend(cost * made);
        if (craftStorageFull) {
            pauseCraft(c, job);
            return;
        }
        // 製作済みの回数は、充電切れでも下で確定する
        if (crafted >= step.crafts()) {
            // この工程を作り終えた: 次の工程（次の作業場所）へ。全部終われば、納品か片付けへ
            // （作る姿勢は、歩き出すまで続ける。ここで立ち姿に戻すと、続けて作るときに頭が一瞬ガクッと動くので）
            crafted = 0;
            batchIndex++;
        }
    }

    /** 作業の頼みを出す tick を、作業者ごとに分ける数。 */
    private static final int REQUEST_PHASES = 8;
    /** 同じ行き先への経路探しを間引く。 */
    private final PathRetry pathRetry = new PathRetry();

    private boolean craftStorageFull;

    private void pauseCraft(WorkConsoleBlockEntity console, WorkJob job) {
        console.completePartialCraft(job, batchIndex, crafted);
        crafted = 0;
        android.setStall(RuinAndroid.STALL_FULL);
        afterJob(console, job);
    }

    /**
     * 加工モードの1工程: 装置の上の面へ素材を1回分ずつ入れ（入るだけ）、下の面から、できた物を取り出す。
     * 主な物が作る回数分そろえば、次の工程へ。副産物（記録した、ほかに出てくる物）も取り出して持つ。
     */
    private void process(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, WorkJob.Batch step) {
        if (++craftTimer < PROCESS_INTERVAL) {
            return;
        }
        craftTimer = 0;
        RecipeData r = step.recipe();
        BlockPos at = step.location();
        // 取り出す面: 記録した面か、自動なら、今できた物を取り出せる面（まだなければ null: 取り出さない）
        Direction outFace = io.github.genichimaruo.singulo.android.work.MachineWatch.outputFace(level, at, r);
        net.minecraftforge.items.IItemHandler out = outFace == null ? null
                : io.github.genichimaruo.singulo.compat.Capabilities.get(level, io.github.genichimaruo.singulo.compat.Capabilities.ItemHandler.BLOCK, at, outFace);
        if (!io.github.genichimaruo.singulo.android.work.MachineWatch.handlesInput(level, at, r)
                || !io.github.genichimaruo.singulo.android.work.MachineWatch.handlesOutput(level, at, r)) {
            fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
            return;
        }
        // 燃料が切れていれば、燃料を足す（持っていなければ、取りに行く）。記録した燃料がなければ、取りに行かない
        fuelMissing = false;
        if (!r.fuel().isEmpty() && needsFuel(level, at, r) && refuel(level, c, at, r)) {
            return;
        }
        // 燃料が素材置き場になく、作れるなら、作る注文を出して（作っていれば）待つ。待つあいだは取り消さない。
        // ほかにできる作業（燃料を作る作業も）があれば、入れた分は装置に任せてそちらへ
        if (fuelMissing && processingPending == null && c.orderFuel(job, r.fuel(), fuelWant)) {
            stalled = 0;
            if (c.hasOtherWork(android, job, batchIndex)) {
                leaveForFuel(c, job, step, r);
                return;
            }
        }
        // 作るものを選べる装置（精密組立台など）: 材料の一部が別のレシピと同じでも、記録したレシピの物を作らせる
        if (level.getBlockEntity(at) instanceof io.github.genichimaruo.singulo.machine.MachineBlockEntity machine) {
            machine.selectFor(r.output(), r.inputs().stream().map(ic -> ic.item).toList());
        }
        boolean progress = false;
        while (inserted < step.crafts()) {
            if (!processingPaid && android.charge() < CRAFT_COST) {
                android.spend(CRAFT_COST);
                break;
            }
            boolean complete = insertOnce(level, at, r);
            progress |= processingMoved;
            if (!complete) {
                if (!processingMoved && !io.github.genichimaruo.singulo.android.work.MachineWatch.refusedInput(level, at, r).isEmpty()) {
                    // 記録した入れる面から入らない（かまどの下の面など）: いつまで待っても入らないので、理由を出して取り消す
                    android.setState(RuinAndroid.STATE_IDLE);
                    fail(c, job, WorkConsoleBlockEntity.Failure.WRONG_FACE);
                    return;
                }
                break;
            }
            inserted++;
            progress = true;
            if (!android.active()) {
                break;
            }
        }
        long want = job.type() == WorkJob.Type.COLLECT ? job.amount() : step.crafts() * r.outputCount();
        // 液体・化学物質の出てくる物: 装置の出口から、タンクへ（主な物は要る分だけ、ほかの物は全部）
        if (outFace != null) {
            io.github.genichimaruo.singulo.android.work.LiquidStores liquids = io.github.genichimaruo.singulo.android.work.LiquidStores.machine(level, at, outFace);
            for (ItemStack o : r.outputs()) {
                if (!FluidSamples.liquid(o)) {
                    continue;
                }
                boolean main = o == r.output();
                long limit = Math.min(main ? want - collected : Long.MAX_VALUE, android.liquidSpace(o));
                if (limit <= 0) {
                    continue;
                }
                long got = liquids.drain(o, limit, false);
                if (got > 0) {
                    long in = android.fillLiquid(o, got, false);
                    if (in < got) {
                        liquids.fill(o, got - in, false);     // 入りきらなかった分は戻す
                    }
                    if (main) {
                        collected += in;
                    }
                    progress = true;
                }
            }
        }
        for (int i = 0; out != null && i < out.getSlots(); i++) {
            ItemStack s = out.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            int take = 0;
            if (ItemStack.isSameItemSameTags(s, r.output())) {
                take = (int) Math.min(s.getCount(), want - collected);     // ほかの人の分までは取らない
            } else if (io.github.genichimaruo.singulo.item.UsesHelper.isSpentCatalyst(s)) {
                // 使い切った触媒: 記録していなくても持ち帰る（出口に残すと、次にできた物の出口がふさがる）
                take = s.getCount();
            } else {
                for (ItemStack e : r.extras()) {
                    if (ItemStack.isSameItemSameTags(s, e)) {
                        take = s.getCount();
                    }
                }
            }
            if (take <= 0) {
                continue;
            }
            boolean main = ItemStack.isSameItemSameTags(s, r.output());
            take = Math.min(take, outputCapacity(s.copyWithCount(take), main));
            if (take <= 0) {
                continue;
            }
            ItemStack got = out.extractItem(i, take, false);
            if (got.isEmpty()) {
                continue;
            }
            int kept = keepOutput(got, main);
            if (main) {
                collected += kept;
            }
            progress = true;
        }
        if (inserted >= step.crafts() && collected < want) {
            // 入れ終えて、できるのを待っている: 長くかかりそうで、ほかにできる作業があれば、待たずにそちらへ
            // （できたら、指令台が取り出しの作業を出す）
            machineWait += PROCESS_INTERVAL;
            if (mayLeave(level, at) && c.hasOtherWork(android, job, batchIndex)) {
                c.park(job, batchIndex, want - collected);
                android.gesture(RuinAndroid.GESTURE_NOD, null);
                inserted = 0;
                collected = 0;
                stalled = 0;
                machineWait = 0;
                afterJob(c, job);
                return;
            }
        }
        if (progress) {
            stalled = 0;
            android.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.2F);
        } else if ((stalled += PROCESS_INTERVAL) >= SHORT_TICKS && inserted >= step.crafts() && collected < want
                && io.github.genichimaruo.singulo.android.work.MachineWatch.held(level, at, r) == 0) {
            // 入れた素材を使い切ったのに、できた物が足りない（確率で出る物）: 足りない分の回数を足して、集まるまで作り直す
            endShort(c, job, want - collected);
            return;
        } else if (stalled >= STALL_TICKS) {
            fail(c, job, WorkConsoleBlockEntity.Failure.STALLED);
            return;
        }
        if (collected >= want && inserted >= step.crafts()) {
            inserted = 0;
            collected = 0;
            stalled = 0;
            machineWait = 0;
            batchIndex++;
        }
    }

    /** 装置の加工を待たずに離れてよいか: 残り時間の見込みが長い（分からない装置は、しばらく待ってもできなければ）。 */
    private boolean mayLeave(ServerLevel level, BlockPos machine) {
        int eta = io.github.genichimaruo.singulo.android.work.MachineWatch.eta(level, machine);
        return eta < 0 ? machineWait >= LEAVE_UNKNOWN_TICKS : machineWait >= LEAVE_TICKS && eta >= LEAVE_ETA_TICKS;
    }

    /** 燃料を取りに行く受け渡し場所（行っていなければ null）と、持ってくる数。 */
    @Nullable
    private BlockPos fuelTrip;
    private int fuelWant;
    /** 燃料を足そうとしたが、素材置き場になかった（この tick の加工で）。 */
    private boolean fuelMissing;

    /** 装置の燃料の面に、あと何個燃料が入るか。 */
    private int fuelRoom(ServerLevel level, BlockPos machine, RecipeData r) {
        net.minecraftforge.items.IItemHandler side = io.github.genichimaruo.singulo.compat.Capabilities.get(level, io.github.genichimaruo.singulo.compat.Capabilities.ItemHandler.BLOCK,
                machine, fuelSide(machine, r));
        if (side == null) {
            return 0;
        }
        ItemStack full = r.fuel().copyWithCount(r.fuel().getMaxStackSize());
        int room = 0;
        for (int i = 0; i < side.getSlots(); i++) {
            room += full.getCount() - side.insertItem(i, full, true).getCount();
        }
        return room;
    }

    /** 燃料を入れる面: 記録した面か、自動なら、装置から見てアンドロイドのいる横の面（かまどなら燃料の欄）。 */
    private Direction fuelSide(BlockPos machine, RecipeData r) {
        Direction set = r.fuelFace(android.level().getBlockState(machine));
        if (set != null) {
            return set;
        }
        Vec3 d = android.position().subtract(Vec3.atCenterOf(machine));
        Direction dir = Direction.getNearest(d.x, 0, d.z);
        return dir.getAxis().isHorizontal() ? dir : Direction.NORTH;
    }

    /** 装置の燃料が切れて、止まっているか（かまどは火が消えて燃料の欄が空で、焼く物が残っている）。 */
    private boolean needsFuel(ServerLevel level, BlockPos machine, RecipeData r) {
        if (level.getBlockEntity(machine) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity f) {
            BlockState s = level.getBlockState(machine);
            boolean lit = s.hasProperty(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)
                    && s.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT);
            return !lit && f.getItem(1).isEmpty() && !f.getItem(0).isEmpty();
        }
        // ほかの装置: 5秒進まず、横の面に燃料が入っていない
        net.minecraftforge.items.IItemHandler side = io.github.genichimaruo.singulo.compat.Capabilities.get(level, io.github.genichimaruo.singulo.compat.Capabilities.ItemHandler.BLOCK,
                machine, fuelSide(machine, r));
        return stalled >= 100 && side != null && side.getSlots() > 0;
    }

    /** 燃料を足す。持っていなければ取りに行き始めて true（このあと取りに行く）。足した・足せないときは false。 */
    private boolean refuel(ServerLevel level, WorkConsoleBlockEntity c, BlockPos machine, RecipeData r) {
        ItemStack fuel = r.fuel();
        if (carriedCount(fuel.getItem()) > 0) {
            net.minecraftforge.items.IItemHandler side = io.github.genichimaruo.singulo.compat.Capabilities.get(level,
                    io.github.genichimaruo.singulo.compat.Capabilities.ItemHandler.BLOCK, machine, fuelSide(machine, r));
            if (side != null && insertFromCarry(side, fuel, carriedCount(fuel.getItem())) > 0) {
                stalled = 0;
                android.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 0.8F);
            }
            return false;
        }
        // 持ってくる数: 装置に入るだけ（満タン）か、燃料の欄の数ずつ。どちらも、入りきらない分は持ってこない
        int room = fuelRoom(level, machine, r);
        fuelWant = r.fuelFill() ? room : Math.min(Math.max(1, fuel.getCount()), room);
        if (fuelWant <= 0) {
            return false;                       // 燃料の欄がいっぱい（ほかの理由で止まっている）
        }
        fuelTrip = c.fuelPoint(fuel, android.blockPosition());
        travel = 0;
        fuelMissing = fuelTrip == null;         // 在庫にない: 作れるなら作る注文を出す（作れなければ、止まったままなら、やがて取り消す）
        return fuelTrip != null;
    }

    /**
     * 燃料を作るあいだ、装置を離れる: 入れた回数分は「加工を待っている」にして（燃料ができたら、指令台が取り出し・燃料足しの作業を出す）、
     * 残りの素材は資材口へ戻す。何も入れていなければ、作業をあとに回す。
     */
    private void leaveForFuel(WorkConsoleBlockEntity c, WorkJob job, WorkJob.Batch step, RecipeData r) {
        if (job.type() == WorkJob.Type.COLLECT) {
            c.park(job, batchIndex, job.amount() - collected);
        } else if (inserted > 0) {
            c.park(job, batchIndex, inserted, Math.max(0, inserted * r.outputCount() - collected));
        } else {
            android.setState(RuinAndroid.STATE_IDLE);
            fail(c, job, WorkConsoleBlockEntity.Failure.DEFERRED);
            return;
        }
        android.gesture(RuinAndroid.GESTURE_NOD, null);
        inserted = 0;
        collected = 0;
        stalled = 0;
        machineWait = 0;
        afterJob(c, job);
    }

    /** 燃料を取りに行って、受け渡し場所から（予約されていない物を）取る。取ったら装置へ戻る。 */
    private void fuelRun(WorkConsoleBlockEntity c, ItemStack fuel) {
        android.setActivity(RuinAndroid.ACT_FETCH);
        Boolean at = go(fuelTrip, HANDOVER_TICKS);
        if (at == null) {
            fuelTrip = null;
        } else if (at) {
            c.takeFuel(android, fuelTrip, fuel, Math.max(1, fuelWant));
            fuelTrip = null;
            stalled = 0;
            travel = 0;
        }
    }

    /** 素材を使い切ったあと、できた物を待つ時間。過ぎても足りなければ、確率で出なかったとみなす。 */
    private static final int SHORT_TICKS = 20 * 30;

    /** 装置（入れる面）に、まだこれらの素材が残っているか（まだ加工している）。 */
    private static boolean holdsAny(net.minecraftforge.items.IItemHandler h, java.util.List<ItemCount> inputs) {
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            for (ItemCount in : inputs) {
                if (!s.isEmpty() && in.matches(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 今の工程が、目当ての数に missing だけ足りずに終わった: 指令台に知らせて（回数を足してもらい）、ここまでで作業を区切る。 */
    private void endShort(WorkConsoleBlockEntity c, WorkJob job, long missing) {
        c.reportShort(job, batchIndex, missing);
        c.completeUpTo(job, batchIndex);
        inserted = 0;
        collected = 0;
        stalled = 0;
        resetProcedure();
        afterJob(c, job);
    }

    // ------------------------------------------------------------------ 手順カード

    /** 手順カードの工程: 手順を上から順に行う。全部終われば次の工程へ。 */
    private void procedure(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, WorkJob.Batch step) {
        RecipeData r = step.recipe();
        if (procIndex >= r.steps().size()) {
            resetProcedure();
            batchIndex++;
            return;
        }
        io.github.genichimaruo.singulo.android.work.ProcedureStep ps = r.steps().get(procIndex);
        ItemStack what = ps.item().isEmpty() ? r.output() : ps.item();
        long want = Math.max(1, FluidSamples.amount(what)) * step.crafts();
        if (ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.MILK) {
            // 搾乳: 牛乳入りバケツを集める（数は物の欄、空ならできる物の数）
            want = Math.max(1, ps.item().isEmpty() ? r.outputCount() : ps.item().getCount()) * step.crafts();
            what = new ItemStack(net.minecraft.world.item.Items.MILK_BUCKET);
        } else if (ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.USE) {
            want = step.crafts();              // 1回の製作につき1回操作する
        }
        if (progress >= want) {
            procIndex++;
            progress = 0;
            stalled = 0;
            stopMining();
            mineIdle = 0;
            mineSkip.clear();
            ranchTarget = null;
            ranchSkip.clear();
            craftTimer = 0;
            return;
        }
        switch (ps.kind()) {
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.MINE -> {
                mine(level, c, job, ps, what);
                return;
            }
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.HARVEST -> {
                harvest(level, c, job, ps, what);
                return;
            }
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.FELL -> {
                fell(level, c, job, ps, what);
                return;
            }
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.BREED -> {
                breed(level, c, job, ps, want);
                return;
            }
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.SHEAR, io.github.genichimaruo.singulo.android.work.ProcedureStep.MILK, io.github.genichimaruo.singulo.android.work.ProcedureStep.CULL -> {
                ranch(level, c, job, ps, what);
                return;
            }
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.USE -> {
                use(level, c, job, ps);
                return;
            }
            default -> {
            }
        }
        Boolean arrived = go(ps.pos(), 0);
        if (arrived == null) {
            fail(c, job, WorkConsoleBlockEntity.Failure.UNREACHABLE);
            return;
        }
        if (!arrived) {
            return;
        }
        if (android.state() != RuinAndroid.STATE_CRAFTING) {
            android.setState(RuinAndroid.STATE_CRAFTING);
        }
        if (++craftTimer < PROCESS_INTERVAL) {
            return;
        }
        craftTimer = 0;
        boolean insert = ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.INSERT;
        long moved;
        if (FluidSamples.liquid(what)) {
            Direction face = insert ? Direction.UP : Direction.DOWN;
            if (!io.github.genichimaruo.singulo.android.work.LiquidStores.handles(level, ps.pos(), face)) {
                fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
                return;
            }
            io.github.genichimaruo.singulo.android.work.LiquidStores liquids = io.github.genichimaruo.singulo.android.work.LiquidStores.machine(level, ps.pos(), face);
            long limit = want - progress;
            if (insert) {
                long can = android.drainLiquid(what, limit, true);
                long put = liquids.fill(what, can, false);
                android.drainLiquid(what, put, false);
                moved = put;
            } else {
                long got = liquids.drain(what, Math.min(limit, android.liquidSpace(what)), false);
                long in = android.fillLiquid(what, got, false);
                if (in < got) {
                    liquids.fill(what, got - in, false);
                }
                moved = in;
            }
        } else {
            net.minecraftforge.items.IItemHandler h = io.github.genichimaruo.singulo.compat.Capabilities.get(level, io.github.genichimaruo.singulo.compat.Capabilities.ItemHandler.BLOCK,
                    ps.pos(), insert ? Direction.UP : Direction.DOWN);
            if (h == null) {
                fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
                return;
            }
            moved = insert ? insertFromCarry(h, what, want - progress) : extractToCarry(h, what, want - progress);
        }
        progress += moved;
        if (moved > 0) {
            stalled = 0;
            android.playSound(SoundEvents.ITEM_PICKUP, 0.4F, insert ? 0.9F : 1.2F);
        } else if ((stalled += PROCESS_INTERVAL) >= SHORT_TICKS && !insert && insertsUsedUp(level, r)) {
            // 投入した素材はもう使われたのに、取り出す物が足りない（確率で出る物）: 足りない分の回数を足して、集まるまで繰り返す
            long per = Math.max(1, what.getCount());
            long missingRuns = (want - progress + per - 1) / per;
            endShort(c, job, missingRuns * r.outputCount());
        } else if (stalled >= STALL_TICKS) {
            fail(c, job, WorkConsoleBlockEntity.Failure.STALLED);
        }
    }

    /** 手順カードで投入した物が、投入先の装置にもう残っていないか。 */
    private static boolean insertsUsedUp(ServerLevel level, RecipeData r) {
        for (io.github.genichimaruo.singulo.android.work.ProcedureStep s : r.steps()) {
            if (s.kind() != io.github.genichimaruo.singulo.android.work.ProcedureStep.INSERT || s.item().isEmpty()) {
                continue;
            }
            net.minecraftforge.items.IItemHandler h = io.github.genichimaruo.singulo.compat.Capabilities.get(level, io.github.genichimaruo.singulo.compat.Capabilities.ItemHandler.BLOCK,
                    s.pos(), Direction.UP);
            if (h != null && holdsAny(h, java.util.List.of(new ItemCount(s.item(), 1)))) {
                return false;
            }
        }
        return true;
    }

    /** 持ち物から what を最大 limit 個、装置へ入れる。入れた数。 */
    private long insertFromCarry(net.minecraftforge.items.IItemHandler h, ItemStack what, long limit) {
        long moved = 0;
        for (int slot : android.carrySlots()) {
            ItemStack s = android.inventory().getItem(slot);
            if (moved >= limit || s.isEmpty() || !ItemStack.isSameItemSameTags(s, what)) {
                continue;
            }
            int give = (int) Math.min(s.getCount(), limit - moved);
            ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(h, s.copyWithCount(give), false);
            int in = give - rest.getCount();
            moved += in;
            android.inventory().setItem(slot, s.copyWithCount(s.getCount() - in));
        }
        android.compactCarry();
        return moved;
    }

    /** 持ち物から need に合う物を最大 limit 個、装置へ入れる（触媒は寿命の短いものから）。入れた数。 */
    private long insertFromCarry(net.minecraftforge.items.IItemHandler h, ItemCount need, long limit) {
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        for (int slot : android.carrySlots()) {
            if (need.matches(android.inventory().getItem(slot))) {
                slots.add(slot);
            }
        }
        slots.sort(java.util.Comparator.comparingInt(slot -> ItemCount.lifeOrder(android.inventory().getItem(slot))));
        long moved = 0;
        for (int slot : slots) {
            ItemStack s = android.inventory().getItem(slot);
            if (moved >= limit) {
                break;
            }
            int give = (int) Math.min(s.getCount(), limit - moved);
            ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(h, s.copyWithCount(give), false);
            int in = give - rest.getCount();
            moved += in;
            android.inventory().setItem(slot, s.copyWithCount(s.getCount() - in));
        }
        android.compactCarry();
        return moved;
    }

    /** 装置から what を最大 limit 個、持ち物へ取り出す。取り出した数。 */
    private long extractToCarry(net.minecraftforge.items.IItemHandler h, ItemStack what, long limit) {
        long moved = 0;
        for (int i = 0; i < h.getSlots() && moved < limit; i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, what)) {
                continue;
            }
            int take = (int) Math.min(s.getCount(), limit - moved);
            take = Math.min(take, outputCapacity(s.copyWithCount(take), true));
            if (take <= 0) {
                continue;
            }
            ItemStack got = h.extractItem(i, take, false);
            moved += keepOutput(got, true);
        }
        return moved;
    }

    /**
     * 採掘の手順: 範囲のブロックを上の段から（同じ段では近い順に）掘り、出てきた物を持つ。目当ての物の数を数える。
     * 掘れるのは、ダイヤのツルハシで掘れる物（採掘アップグレードのエンチャントが効く）。壊せないブロック・液体・
     * 入れ物などのブロック・シールドの許可証で守られた所は掘らない。
     */
    private void mine(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, io.github.genichimaruo.singulo.android.work.ProcedureStep ps,
                      ItemStack what) {
        if (!android.canMine()) {
            fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
            return;
        }
        if (!canPay(MINE_COST)) {
            return;
        }
        ItemStack tool = miningTool();
        if (mineTarget != null && !mineable(level, mineTarget, tool, c)) {
            stopMining();
        }
        if (mineTarget == null) {
            mineTarget = findMineTarget(level, ps, tool, c);
            mineTimer = 0;
            travel = 0;
            if (mineTarget == null) {
                if (areaSearch.pending()) {
                    return;                               // 広い範囲を探している途中（次の tick に続きを探す）
                }
                // 掘る物がない（丸石製造機なら、戻るのを待つ）。範囲のそばで待つ
                if (android.state() == RuinAndroid.STATE_MINING) {
                    android.setState(RuinAndroid.STATE_IDLE);
                }
                if (android.position().distanceToSqr(Vec3.atCenterOf(ps.pos())) > (MINE_REACH + 1) * (MINE_REACH + 1)) {
                    moveNear(ps.pos());
                }
                if (++mineIdle >= MINE_IDLE_TICKS) {
                    fail(c, job, WorkConsoleBlockEntity.Failure.STALLED);
                }
                return;
            }
            BlockState state = level.getBlockState(mineTarget);
            mineNeed = android.workTicks(breakTicks(level, mineTarget, state, tool));
        }
        mineIdle = 0;
        BlockPos target = mineTarget;
        Vec3 center = Vec3.atCenterOf(target);
        double d2 = android.getEyePosition().distanceToSqr(center);
        if (!mineSettled && d2 > MINE_NEAR * MINE_NEAR) {
            // できるだけ近く（ブロックの隣）まで歩く。これ以上近づけなければ、手が届く所から掘る。届かなければ、そのブロックは飛ばす
            boolean reach = d2 <= MINE_REACH * MINE_REACH;
            if (travel > 0 && android.getNavigation().isDone() && reach) {
                mineSettled = true;
            } else {
                if (android.state() == RuinAndroid.STATE_MINING) {
                    android.setState(RuinAndroid.STATE_IDLE);
                }
                if (++travel > GIVE_UP_TICKS) {
                    if (reach) {
                        mineSettled = true;
                    } else {
                        mineSkip.add(target);
                        stopMining();
                        return;
                    }
                } else if (pathRetry.allow(target, android.level().getGameTime(), android.getNavigation().isDone() ? 10 : 20)) {
                    net.minecraft.world.level.pathfinder.Path path = android.getNavigation().createPath(target, 1);   // ブロックの隣まで
                    if (path != null && android.getNavigation().moveTo(path, 1.0)) {
                        return;
                    }
                    if (reach) {
                        mineSettled = true;            // 道がない: 届くなら、ここから掘る
                    }
                }
                if (!mineSettled) {
                    return;
                }
            }
        }
        mineSettled = true;
        travel = 0;
        android.getNavigation().stop();
        android.getLookControl().setLookAt(center.x, center.y, center.z, 90, 90);
        android.setYRot(yawTo(target));
        android.setYBodyRot(android.getYRot());
        android.setTool(RuinAndroid.TOOL_PICKAXE);
        if (android.state() != RuinAndroid.STATE_MINING) {
            android.setState(RuinAndroid.STATE_MINING);
        }
        mineTimer++;
        level.destroyBlockProgress(android.getId(), target, Math.min(9, mineTimer * 10 / Math.max(1, mineNeed)));
        if (mineTimer % 5 == 0) {
            BlockState state = level.getBlockState(target);
            level.playSound(null, target, state.getSoundType(level, target, android).getHitSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                    0.5F, 0.8F);
        }
        if (mineTimer < mineNeed) {
            return;
        }
        // 掘り終えた: ほかの mod の守りも効くよう、注文した人として壊してよいか確かめる
        BlockState state = level.getBlockState(target);
        net.minecraft.world.entity.player.Player as = actor(level, c, job);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, target,
                state, as))) {
            android.setStall(RuinAndroid.STALL_PROTECTED);
            mineSkip.add(target);
            stopMining();
            return;
        }
        java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(state, level, target, level.getBlockEntity(target),
                android, tool);
        if (!fitsInCarry(drops)) {
            stopMining();
            endFull(c, job, what);                   // 壊すと持ち物からあふれる: 壊さずに、ここまでの分で置きに戻る
            return;
        }
        level.destroyBlock(target, false, android);
        collect(drops, what);
        stopMining();
        android.spend(MINE_COST);
    }

    // ------------------------------------------------------------------ 農作業・操作

    /** 収穫・伐採・牧場で、することがない状態が続いたらあきらめるまで（牧場は毛が生えるのを待つので長め）。 */
    private static final int FARM_IDLE_TICKS = 20 * 30, RANCH_IDLE_TICKS = 20 * 120;
    /** 1つ収穫する・動物の世話をする時間。 */
    private static final int HARVEST_TICKS = 8, RANCH_TICKS = 12;
    /** 牧場で世話をしている動物と、たどり着けなかった動物。 */
    @Nullable
    private java.util.UUID ranchTarget;
    private final java.util.Set<java.util.UUID> ranchSkip = new java.util.HashSet<>();

    /** 範囲の中で、条件に合う一番近いブロック（なければ null）。 */
    @Nullable
    private BlockPos nearestInArea(net.minecraft.world.level.levelgen.structure.BoundingBox box, java.util.function.Predicate<BlockPos> ok) {
        return areaSearch.find(box, WorkAreaSearch.Order.NEAREST, android.blockPosition(), android.level().getGameTime(),
                p -> android.level().isLoaded(p) && !mineSkip.contains(p) && ok.test(p));
    }

    /** 作業範囲の中の探しもの（採掘・伐採の場所と、育ちきった作物）。何 tick かに分けて探す。 */
    private final WorkAreaSearch areaSearch = new WorkAreaSearch();
    private final WorkAreaSearch growthSearch = new WorkAreaSearch();

    /**
     * ブロックのそば（手が届く所）まで近づく。着いたら true、歩いているあいだ false、たどり着けなければ null。
     * できるだけ隣まで寄り、これ以上近づけなければ、手が届く所から作業する。
     */
    @Nullable
    private Boolean reachBlock(BlockPos target) {
        Vec3 center = Vec3.atCenterOf(target);
        double d2 = android.getEyePosition().distanceToSqr(center);
        if (mineSettled || d2 <= MINE_NEAR * MINE_NEAR) {
            mineSettled = true;
            travel = 0;
            android.getNavigation().stop();
            return true;
        }
        boolean reach = d2 <= MINE_REACH * MINE_REACH;
        if (travel > 0 && android.getNavigation().isDone() && reach) {
            mineSettled = true;
            return true;
        }
        idlePose();
        if (++travel > GIVE_UP_TICKS) {
            travel = 0;
            if (reach) {
                mineSettled = true;
                return true;
            }
            return null;
        }
        if (pathRetry.allow(target, android.level().getGameTime(), android.getNavigation().isDone() ? 10 : 20)) {
            net.minecraft.world.level.pathfinder.Path path = android.getNavigation().createPath(target, 1);
            if (path != null && android.getNavigation().moveTo(path, 1.0)) {
                return false;
            }
            if (reach) {
                mineSettled = true;
                return true;
            }
        }
        return false;
    }

    /** 動物のそばまで寄る。着いたら true、歩いているあいだ false、たどり着けなければ null。 */
    @Nullable
    private Boolean reachEntity(net.minecraft.world.entity.Entity target) {
        if (android.distanceToSqr(target) <= 2.3 * 2.3) {
            travel = 0;
            android.getNavigation().stop();
            android.getLookControl().setLookAt(target, 60, 60);
            return true;
        }
        idlePose();
        if (++travel > GIVE_UP_TICKS) {
            travel = 0;
            return null;
        }
        if (pathRetry.allow(target.blockPosition(), android.level().getGameTime(), 10)) {
            android.getNavigation().moveTo(target, 1.0);
        }
        return false;
    }

    /** 歩き出すときは、作業の姿勢をやめる。 */
    private void idlePose() {
        if (android.state() == RuinAndroid.STATE_MINING || android.state() == RuinAndroid.STATE_CRAFTING) {
            android.setState(RuinAndroid.STATE_IDLE);
        }
    }

    /** することがない: 範囲のそばで待つ。limit 続いたら、注文を取り消す。 */
    private void idleNear(WorkConsoleBlockEntity c, WorkJob job, io.github.genichimaruo.singulo.android.work.ProcedureStep ps, int limit) {
        idlePose();
        if (android.position().distanceToSqr(Vec3.atCenterOf(ps.pos())) > (MINE_REACH + 2) * (MINE_REACH + 2)) {
            moveNear(ps.pos());
        }
        // まだ育つ物（若い作物・苗木・毛を刈った羊・子ども・アメジストの芽）があれば、育つのを待つ（長く待つ）
        if (mineIdle % 100 == 0 || growthSearch.pending()) {
            growing = android.level() instanceof ServerLevel level && growing(level, ps);
        }
        if (growing) {
            android.setActivity(RuinAndroid.ACT_WAIT);
        } else {
            android.setStall(RuinAndroid.STALL_NO_TARGET);   // 範囲に、今できる作業の対象がない
        }
        if (++mineIdle >= (growing ? GROW_WAIT_TICKS : limit)) {
            fail(c, job, WorkConsoleBlockEntity.Failure.STALLED);
        }
    }

    /** 育つのを待つ限度（20分）と、今、育つのを待っているか。 */
    private static final int GROW_WAIT_TICKS = 20 * 60 * 20;
    private boolean growing;

    /** 範囲に、まだ育つ物があるか。 */
    private boolean growing(ServerLevel level, io.github.genichimaruo.singulo.android.work.ProcedureStep ps) {
        net.minecraft.world.level.levelgen.structure.BoundingBox box = ps.area();
        switch (ps.kind()) {
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.HARVEST, io.github.genichimaruo.singulo.android.work.ProcedureStep.FELL -> {
                BlockPos found = growthSearch.find(box, WorkAreaSearch.Order.ANY, android.blockPosition(), level.getGameTime(),
                        p -> level.isLoaded(p) && grows(level, p, ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.FELL));
                return found != null || growthSearch.pending();

            }
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.SHEAR, io.github.genichimaruo.singulo.android.work.ProcedureStep.CULL -> {
                return !level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, pen(ps),
                        a -> a.isAlive() && (a.isBaby() || ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.SHEAR && a instanceof net.minecraft.world.entity.animal.Sheep s
                                && s.isSheared())).isEmpty();
            }
            default -> {
                return false;
            }
        }
    }

    /** そのうち収穫できる（伐採なら、苗木）。 */
    private static boolean grows(ServerLevel level, BlockPos p, boolean trees) {
        BlockState s = level.getBlockState(p);
        net.minecraft.world.level.block.Block b = s.getBlock();
        if (trees) {
            return s.is(net.minecraft.tags.BlockTags.SAPLINGS);
        }
        if (b instanceof net.minecraft.world.level.block.CropBlock crop) {
            return !crop.isMaxAge(s);
        }
        if (b instanceof net.minecraft.world.level.block.NetherWartBlock) {
            return s.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) < 3;
        }
        if (b instanceof net.minecraft.world.level.block.CocoaBlock) {
            return s.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) < 2;
        }
        if (b instanceof net.minecraft.world.level.block.SweetBerryBushBlock) {
            return s.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) < 2;
        }
        if (b instanceof net.minecraft.world.level.block.SugarCaneBlock || b instanceof net.minecraft.world.level.block.CactusBlock
                || b instanceof net.minecraft.world.level.block.BambooStalkBlock) {
            return !level.getBlockState(p.below()).is(b);              // 根元: 伸びる
        }
        return b instanceof net.minecraft.world.level.block.StemBlock || s.is(net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST);
    }

    /** 注文の完成品を副産物より優先する。持ち主の私物や工程で使う材料は捨てない。 */
    private int outputCapacity(ItemStack what, boolean priority) {
        int room = carryRoom(what);
        if (priority && room < what.getCount()) {
            for (int slot : android.carrySlots()) {
                ItemStack held = android.inventory().getItem(slot);
                if (held.isEmpty() || ItemStack.isSameItemSameTags(held, what) || neededForWork(held)) {
                    continue;
                }
                android.inventory().setItem(slot, ItemStack.EMPTY);
                drop(held);
                room = carryRoom(what);
                if (room >= what.getCount()) {
                    break;
                }
            }
        }
        return room;
    }

    private int carryRoom(ItemStack what) {
        int room = 0;
        for (int slot : android.carrySlots()) {
            ItemStack held = android.inventory().getItem(slot);
            if (held.isEmpty()) {
                room += what.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(held, what)) {
                room += Math.max(0, held.getMaxStackSize() - held.getCount());
            }
        }
        return room;
    }

    private boolean neededForWork(ItemStack held) {
        WorkJob job = android.workJob;
        if (job == null) {
            return true; // 作業の内容が分からないときは持ち物を守る
        }
        for (ItemCount in : job.fetch()) {
            if (ItemStack.isSameItemSameTags(held, in.item)) {
                return true;
            }
        }
        for (WorkJob.Batch batch : job.batch()) {
            RecipeData recipe = batch.recipe();
            if (ItemStack.isSameItemSameTags(held, recipe.output())
                    || ItemStack.isSameItemSameTags(held, recipe.fuel())) {
                return true;
            }
            for (ItemCount in : recipe.inputs()) {
                if (ItemStack.isSameItemSameTags(held, in.item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int keepOutput(ItemStack stack, boolean priority) {
        outputCapacity(stack, priority);
        ItemStack rest = android.addToCarry(stack);
        int kept = stack.getCount() - rest.getCount();
        drop(rest);
        return kept;
    }

    /** 手に入れて実際に持てた物だけを数える。 */
    private void collect(java.util.List<ItemStack> drops, ItemStack what) {
        for (ItemStack d : drops) {
            if (d.isEmpty()) {
                continue;
            }
            boolean main = ItemStack.isSameItemSameTags(d, what);
            int kept = keepOutput(d, main);
            if (main) {
                progress += kept;
            }
        }
    }

    /** 今切っている木の、伐採の充電（根元を決めたときに原木を数える）。 */
    private int fellCost = FELL_BASE_COST;

    private boolean canPay(int cost) {
        if (android.charge() >= cost) {
            return true;
        }
        android.spend(cost);                    // 作業前に充電へ戻り、最後の1回を無料にしない
        return false;
    }

    /** ほかの mod の守りも効くよう、注文した人として、そのブロックを壊してよいか確かめる。 */
    private boolean mayBreak(ServerLevel level, WorkConsoleBlockEntity c, BlockPos p) {
        if (io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.isActiveCrown(level, p)) {
            return false;
        }
        net.minecraft.world.entity.player.Player as = actor(level, c, null);
        return !net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, p,
                level.getBlockState(p), as));
    }

    // ---------------- 収穫

    /** 収穫できるブロックか（育ちきった作物・ネザーウォート・カカオ・スイートベリー、サトウキビなどの一番上、カボチャ・スイカ）。 */
    private static boolean harvestable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        net.minecraft.world.level.block.Block b = s.getBlock();
        if (b instanceof net.minecraft.world.level.block.CropBlock crop) {
            return crop.isMaxAge(s);
        }
        if (b instanceof net.minecraft.world.level.block.NetherWartBlock) {
            return s.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) >= 3;
        }
        if (b instanceof net.minecraft.world.level.block.CocoaBlock) {
            return s.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) >= 2;
        }
        if (b instanceof net.minecraft.world.level.block.SweetBerryBushBlock) {
            return s.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) >= 2;
        }
        if (b instanceof net.minecraft.world.level.block.SugarCaneBlock || b instanceof net.minecraft.world.level.block.CactusBlock
                || b instanceof net.minecraft.world.level.block.BambooStalkBlock) {
            // 根元は残す: 下が同じで、上が違う（一番上）のものから切る
            return level.getBlockState(p.below()).is(b) && !level.getBlockState(p.above()).is(b);
        }
        return s.is(net.minecraft.world.level.block.Blocks.PUMPKIN) || s.is(net.minecraft.world.level.block.Blocks.MELON)
                || s.is(net.minecraft.world.level.block.Blocks.AMETHYST_CLUSTER);
    }

    private void harvest(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, io.github.genichimaruo.singulo.android.work.ProcedureStep ps,
                         ItemStack what) {
        if (!canPay(CRAFT_COST)) {
            return;
        }
        if (mineTarget != null && !harvestable(level, mineTarget)) {
            stopMining();
        }
        if (mineTarget == null) {
            mineTarget = nearestInArea(ps.area(), p -> harvestable(level, p));
            travel = 0;
            if (mineTarget == null) {
                if (areaSearch.pending()) {
                    return;                               // 広い範囲を探している途中（次の tick に続きを探す）
                }
                idleNear(c, job, ps, FARM_IDLE_TICKS);
                return;
            }
        }
        mineIdle = 0;
        BlockPos target = mineTarget;
        Boolean at = reachBlock(target);
        if (at == null) {
            mineSkip.add(target);
            stopMining();
            return;
        }
        if (!at) {
            return;
        }
        Vec3 look = Vec3.atCenterOf(target);
        android.getLookControl().setLookAt(look.x, look.y, look.z, 90, 90);
        if (android.state() != RuinAndroid.STATE_CRAFTING) {
            android.setState(RuinAndroid.STATE_CRAFTING);
        }
        if (++mineTimer < android.workTicks(HARVEST_TICKS)) {
            return;
        }
        stopMining();
        if (!mayBreak(level, c, target)) {
            android.setStall(RuinAndroid.STALL_PROTECTED);   // 守られていて壊せない
            mineSkip.add(target);
            return;
        }
        Harvest h = harvestBlock(level, target);
        if (!fitsInCarry(h.drops())) {
            endFull(c, job, what);                   // 取ると持ち物からあふれる: 取らずに、ここまでの分で置きに戻る
            return;
        }
        h.apply().run();
        collect(h.drops(), what);
        android.spend(CRAFT_COST);
    }

    /** 収穫で手に入る物と、実際に取る（ブロックを変える）こと。手に入る物を先に決めて、持ち物に収まるときだけ取る。 */
    private record Harvest(java.util.List<ItemStack> drops, Runnable apply) {}

    /** 収穫する: 作物は種を1つ使って植え直し、スイートベリーは実だけ摘み、ほかは壊して取る。 */
    private Harvest harvestBlock(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        net.minecraft.world.level.block.Block b = s.getBlock();
        if (b instanceof net.minecraft.world.level.block.SweetBerryBushBlock) {
            int age = s.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE);
            int n = (age >= 3 ? 2 : 1) + level.random.nextInt(2);
            return new Harvest(new java.util.ArrayList<>(java.util.List.of(new ItemStack(net.minecraft.world.item.Items.SWEET_BERRIES, n))), () -> {
                level.setBlock(p, s.setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 1), 2);
                level.playSound(null, p, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
            });
        }
        // アメジストの塊はツルハシで取る（欠片が多く出る。採掘アップグレードがあれば、そのエンチャントも効く）
        ItemStack tool = s.is(net.minecraft.world.level.block.Blocks.AMETHYST_CLUSTER)
                ? android.canMine() ? miningTool() : new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE) : ItemStack.EMPTY;
        java.util.List<ItemStack> drops = new java.util.ArrayList<>(
                net.minecraft.world.level.block.Block.getDrops(s, level, p, level.getBlockEntity(p), android, tool));
        BlockState replant = null;
        if (b instanceof net.minecraft.world.level.block.CropBlock crop) {
            replant = crop.getStateForAge(0);
        } else if (b instanceof net.minecraft.world.level.block.NetherWartBlock) {
            replant = s.setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 0);
        } else if (b instanceof net.minecraft.world.level.block.CocoaBlock) {
            replant = s.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 0);
        }
        if (replant != null && takeSeed(drops, b.asItem())) {
            BlockState again = replant;
            return new Harvest(drops, () -> {
                level.levelEvent(2001, p, net.minecraft.world.level.block.Block.getId(s));     // 壊れる粒と音
                level.setBlock(p, again, 2);
            });
        }
        return new Harvest(drops, () -> level.destroyBlock(p, false, android));
    }

    /** 手に入った物から、植え直す種を1つ取る。取れたら true。 */
    private static boolean takeSeed(java.util.List<ItemStack> drops, net.minecraft.world.item.Item seed) {
        for (ItemStack d : drops) {
            if (d.is(seed) && !d.isEmpty()) {
                d.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ---------------- 伐採

    /** 伐採の道具: ダイヤの斧に、伐採アップグレードのエンチャントを付けたもの。 */
    private ItemStack fellingTool() {
        ItemStack tool = new ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE);
        ItemStack upgrade = android.inventory().getItem(RuinAndroid.SLOT_FELLING);
        net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(upgrade), tool);
        return tool;
    }

    /** 木: base からつながった原木（斜めも）と、そのまわりの自然の葉。 */
    private static java.util.List<BlockPos> tree(ServerLevel level, BlockPos base) {
        java.util.List<BlockPos> logs = new java.util.ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(base);
        seen.add(base);
        while (!queue.isEmpty() && logs.size() < 256) {
            BlockPos p = queue.poll();
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (Math.abs(n.getX() - base.getX()) <= 8 && Math.abs(n.getZ() - base.getZ()) <= 8 && n.getY() - base.getY() <= 32
                                && seen.add(n) && level.getBlockState(n).is(net.minecraft.tags.BlockTags.LOGS)) {
                            queue.add(n);
                        }
                    }
                }
            }
        }
        // 葉: 原木から（面でつながって）5つまで
        java.util.List<BlockPos> out = new java.util.ArrayList<>(logs);
        java.util.Map<BlockPos, Integer> depth = new java.util.HashMap<>();
        java.util.ArrayDeque<BlockPos> leaves = new java.util.ArrayDeque<>();
        for (BlockPos p : logs) {
            depth.put(p, 0);
            leaves.add(p);
        }
        while (!leaves.isEmpty() && out.size() < 768) {
            BlockPos p = leaves.poll();
            int d = depth.get(p);
            if (d >= 5) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (depth.containsKey(n)) {
                    continue;
                }
                BlockState s = level.getBlockState(n);
                if (s.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock
                        && !s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) {
                    depth.put(n, d + 1);
                    leaves.add(n);
                    out.add(n);
                }
            }
        }
        return out;
    }

    private void fell(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, io.github.genichimaruo.singulo.android.work.ProcedureStep ps,
                      ItemStack what) {
        if (!android.canFell()) {
            fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
            return;
        }
        if (!canPay(FELL_BASE_COST)) {
            return;
        }
        ItemStack tool = fellingTool();
        if (mineTarget != null && !level.getBlockState(mineTarget).is(net.minecraft.tags.BlockTags.LOGS)) {
            stopMining();
        }
        if (mineTarget == null) {
            // 一番下の段の原木（同じ段では近い順）を根元にする
            net.minecraft.world.level.levelgen.structure.BoundingBox box = ps.area();
            mineTarget = areaSearch.find(box, WorkAreaSearch.Order.BOTTOM_UP, android.blockPosition(), level.getGameTime(),
                    p -> level.isLoaded(p) && !mineSkip.contains(p) && level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS)
                            && !level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.LOGS));
            travel = 0;
            if (mineTarget == null) {
                if (areaSearch.pending()) {
                    return;                               // 広い範囲を探している途中（次の tick に続きを探す）
                }
                idleNear(c, job, ps, FARM_IDLE_TICKS);
                return;
            }
            int total = 0, logs = 0;
            for (BlockPos p : tree(level, mineTarget)) {
                BlockState s = level.getBlockState(p);
                if (s.is(net.minecraft.tags.BlockTags.LOGS)) {
                    total += breakTicks(level, p, s, tool);
                    logs++;
                }
            }
            mineNeed = Math.max(20, Math.min(200, total / 2));
            fellCost = fellCost(logs);
        }
        if (!canPay(fellCost)) {
            return;
        }
        mineIdle = 0;
        BlockPos base = mineTarget;
        Boolean at = reachBlock(base);
        if (at == null) {
            mineSkip.add(base);
            stopMining();
            return;
        }
        if (!at) {
            return;
        }
        Vec3 look = Vec3.atCenterOf(base);
        android.getLookControl().setLookAt(look.x, look.y, look.z, 90, 90);
        android.setYRot(yawTo(base));
        android.setYBodyRot(android.getYRot());
        android.setTool(RuinAndroid.TOOL_AXE);
        if (android.state() != RuinAndroid.STATE_MINING) {
            android.setState(RuinAndroid.STATE_MINING);
        }
        mineTimer++;
        level.destroyBlockProgress(android.getId(), base, Math.min(9, mineTimer * 10 / Math.max(1, mineNeed)));
        if (mineTimer % 6 == 0) {
            level.playSound(null, base, SoundEvents.WOOD_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 0.9F);
        }
        if (mineTimer < mineNeed) {
            return;
        }
        stopMining();
        if (!mayBreak(level, c, base)) {
            android.setStall(RuinAndroid.STALL_PROTECTED);   // 守られていて壊せない
            mineSkip.add(base);
            return;
        }
        // 木を丸ごと切る（守られたブロックは残す）
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        java.util.List<BlockPos> cut = new java.util.ArrayList<>();
        net.minecraft.world.entity.player.Player as = actor(level, c, null);
        for (BlockPos p : tree(level, base)) {
            if (io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.locked(level, p, as)
                    || !p.equals(base) && !mayBreak(level, c, p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            drops.addAll(net.minecraft.world.level.block.Block.getDrops(s, level, p, level.getBlockEntity(p), android, tool));
            cut.add(p);
        }
        if (!fitsInCarry(drops)) {
            endFull(c, job, what);                   // 切ると持ち物からあふれる: 切らずに、ここまでの分で置きに戻る
            return;
        }
        for (BlockPos p : cut) {
            level.removeBlock(p, false);
        }
        level.levelEvent(2001, base, net.minecraft.world.level.block.Block.getId(net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()));
        level.playSound(null, base, SoundEvents.WOOD_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1, 0.8F);
        // 苗木を植え直す
        for (ItemStack d : drops) {
            if (!d.isEmpty() && d.getItem() instanceof net.minecraft.world.item.BlockItem bi
                    && bi.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.SAPLINGS)
                    && level.getBlockState(base).isAir() && bi.getBlock().defaultBlockState().canSurvive(level, base)) {
                level.setBlock(base, bi.getBlock().defaultBlockState(), 3);
                d.shrink(1);
                break;
            }
        }
        collect(drops, what);
        android.spend(fellCost);
    }

    /** 伐採1本ぶんの充電（原木の数で決まる）。 */
    static int fellCost(int logs) {
        return FELL_BASE_COST + FELL_COST_PER_LOG * Math.max(1, logs);
    }

    // ---------------- 牧場

    /** 範囲の空間（動物を探す。上に少し広げる）。 */
    private static net.minecraft.world.phys.AABB pen(io.github.genichimaruo.singulo.android.work.ProcedureStep ps) {
        net.minecraft.world.level.levelgen.structure.BoundingBox b = ps.area();
        return new net.minecraft.world.phys.AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 3, b.maxZ() + 1);
    }

    /** 世話をしてよい大人の動物か（名前付き・つながれている・飼われている動物は扱わない）。 */
    private static boolean tendable(net.minecraft.world.entity.animal.Animal a) {
        return a.isAlive() && !a.isBaby() && !a.hasCustomName() && !a.isLeashed()
                && !(a instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame());
    }

    /** 範囲の、落ちている物を拾う（近い物から。目当ての物は数える）。 */
    private void pickUp(ServerLevel level, net.minecraft.world.phys.AABB area, ItemStack what) {
        for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                area.inflate(2), e -> e.isAlive() && e.distanceToSqr(android) < 4 * 4)) {
            ItemStack s = e.getItem().copy();
            ItemStack rest = android.addToCarry(s);
            int took = s.getCount() - rest.getCount();
            if (took <= 0) {
                continue;
            }
            if (ItemStack.isSameItemSameTags(s, what)) {
                progress += took;
            }
            if (rest.isEmpty()) {
                e.discard();
            } else {
                e.setItem(rest);
            }
            android.playSound(SoundEvents.ITEM_PICKUP, 0.3F, 1.4F);
        }
    }

    /** 毛刈り・搾乳・処理: 範囲の動物を1頭ずつ世話して、目当ての物がそろうまで続ける。 */
    private void ranch(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, io.github.genichimaruo.singulo.android.work.ProcedureStep ps,
                       ItemStack what) {
        if (!canPay(CRAFT_COST)) {
            return;
        }
        net.minecraft.world.phys.AABB area = pen(ps);
        pickUp(level, area, what);
        net.minecraft.world.entity.animal.Animal target = ranchTarget != null
                && level.getEntity(ranchTarget) instanceof net.minecraft.world.entity.animal.Animal a && a.isAlive() ? a : null;
        if (target != null && !wants(level, ps, target, area)) {
            target = null;
        }
        if (target == null) {
            ranchTarget = null;
            craftTimer = 0;
            travel = 0;
            double best = 0;
            for (net.minecraft.world.entity.animal.Animal a : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, area,
                    a -> !ranchSkip.contains(a.getUUID()) && wants(level, ps, a, area))) {
                double d = a.distanceToSqr(android);
                if (target == null || d < best) {
                    target = a;
                    best = d;
                }
            }
            if (target == null) {
                idleNear(c, job, ps, RANCH_IDLE_TICKS);
                return;
            }
            ranchTarget = target.getUUID();
        }
        mineIdle = 0;
        Boolean at = reachEntity(target);
        if (at == null) {
            ranchSkip.add(target.getUUID());
            ranchTarget = null;
            return;
        }
        if (!at) {
            return;
        }
        if (android.state() != RuinAndroid.STATE_CRAFTING) {
            android.setState(RuinAndroid.STATE_CRAFTING);
        }
        if (++craftTimer < android.workTicks(RANCH_TICKS)) {
            return;
        }
        craftTimer = 0;
        ranchTarget = null;
        if (!mayTend(level, c, target, ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.CULL)) {
            ranchSkip.add(target.getUUID());
            return;
        }
        switch (ps.kind()) {
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.SHEAR -> {
                if (target instanceof net.minecraft.world.entity.animal.Sheep sheep) {
                    sheep.shear(net.minecraft.sounds.SoundSource.PLAYERS);        // 毛は足元に落ちる（拾う）
                }
            }
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.MILK -> {
                if (android.takeFromCarry(new ItemStack(net.minecraft.world.item.Items.BUCKET), 1)) {
                    level.playSound(null, target, SoundEvents.COW_MILK, net.minecraft.sounds.SoundSource.NEUTRAL, 1, 1);
                    collect(new java.util.ArrayList<>(java.util.List.of(new ItemStack(net.minecraft.world.item.Items.MILK_BUCKET))), what);
                }
            }
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.CULL -> {
                android.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                target.hurt(level.damageSources().mobAttack(android), Float.MAX_VALUE);     // 落ちた物は拾う
            }
            default -> {
            }
        }
        android.spend(CRAFT_COST);
    }

    /** この動物に、この手順ですることがあるか。 */
    private boolean wants(ServerLevel level, io.github.genichimaruo.singulo.android.work.ProcedureStep ps, net.minecraft.world.entity.animal.Animal a,
                          net.minecraft.world.phys.AABB area) {
        if (!tendable(a)) {
            return false;
        }
        return switch (ps.kind()) {
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.SHEAR -> a instanceof net.minecraft.world.entity.animal.Sheep sheep && sheep.readyForShearing();
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.MILK -> a instanceof net.minecraft.world.entity.animal.Cow
                    && carriedCount(net.minecraft.world.item.Items.BUCKET) > 0;
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.CULL -> level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, area,
                    o -> o.getType() == a.getType() && tendable(o)).size() > ps.option();
            default -> false;
        };
    }

    private int carriedCount(net.minecraft.world.item.Item item) {
        int n = 0;
        for (int slot : android.carrySlots()) {
            ItemStack s = android.inventory().getItem(slot);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /**
     * 繁殖: 持ってきたえさを、ペアになれる大人に与える（同じ種類の数が上限に届くまで）。与えられる相手がいなくなるか、えさがなくなったら終わり。
     */
    private void breed(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, io.github.genichimaruo.singulo.android.work.ProcedureStep ps,
                       long want) {
        ItemStack feed = ps.item();
        if (feed.isEmpty() || carriedCount(feed.getItem()) <= 0) {
            progress = want;
            return;
        }
        net.minecraft.world.phys.AABB area = pen(ps);
        net.minecraft.world.entity.animal.Animal target = ranchTarget != null
                && level.getEntity(ranchTarget) instanceof net.minecraft.world.entity.animal.Animal a && a.isAlive() ? a : null;
        if (target == null || !breedable(level, target, feed, area, ps.option())) {
            ranchTarget = null;
            craftTimer = 0;
            travel = 0;
            target = null;
            double best = 0;
            for (net.minecraft.world.entity.animal.Animal a : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, area,
                    a -> !ranchSkip.contains(a.getUUID()) && breedable(level, a, feed, area, ps.option()))) {
                double d = a.distanceToSqr(android);
                if (target == null || d < best) {
                    target = a;
                    best = d;
                }
            }
            if (target == null) {
                progress = want;               // もう与える相手がいない: 終わり
                return;
            }
            ranchTarget = target.getUUID();
        }
        Boolean at = reachEntity(target);
        if (at == null) {
            ranchSkip.add(target.getUUID());
            ranchTarget = null;
            return;
        }
        if (!at) {
            return;
        }
        if (android.state() != RuinAndroid.STATE_CRAFTING) {
            android.setState(RuinAndroid.STATE_CRAFTING);
        }
        if (++craftTimer < android.workTicks(RANCH_TICKS)) {
            return;
        }
        craftTimer = 0;
        ranchTarget = null;
        if (!mayTend(level, c, target, false)) {
            ranchSkip.add(target.getUUID());
            return;
        }
        if (!canPay(CRAFT_COST)) {
            return;
        }
        if (android.takeFromCarry(feed, 1)) {
            progress++;
            target.setInLove(actor(level, c, null));          // ハートが出る
            android.spend(CRAFT_COST);
        }
    }

    /** 牧場の操作も、注文者としての保護判定を通す。 */
    private boolean mayTend(ServerLevel level, WorkConsoleBlockEntity c, net.minecraft.world.entity.animal.Animal target, boolean attack) {
        var as = actor(level, c, null);
        as.moveTo(android.getX(), android.getY(), android.getZ(), android.getYRot(), android.getXRot());
        if (io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.locked(level, target.blockPosition(), as)) {
            return false;
        }
        if (attack) {
            return !net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.minecraftforge.event.entity.player.AttackEntityEvent(as, target));
        }
        return !net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(
                        as, net.minecraft.world.InteractionHand.MAIN_HAND, target));
    }

    /** えさを与えてよいか: 大人で恋をしていない、えさを食べる、同じ種類の数が上限より少なく、相手（ほかに与えられる大人か、恋をしている大人）がいる。 */
    private static boolean breedable(ServerLevel level, net.minecraft.world.entity.animal.Animal a, ItemStack feed, net.minecraft.world.phys.AABB area,
                                     int limit) {
        if (!tendable(a) || a.getAge() != 0 || a.isInLove() || !a.canFallInLove() || !a.isFood(feed)) {
            return false;
        }
        java.util.List<net.minecraft.world.entity.animal.Animal> same = level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,
                area, o -> o.getType() == a.getType() && o.isAlive());
        if (same.size() >= limit) {
            return false;                      // 子どもも数える（生まれる分で上限を超えないように）
        }
        for (net.minecraft.world.entity.animal.Animal o : same) {
            if (o != a && !o.isBaby() && (o.isInLove() || o.getAge() == 0 && o.canFallInLove())) {
                return true;
            }
        }
        return false;
    }

    // ---------------- 操作

    /** 操作: ブロックの前へ行って使う（レバーは、オン・オフ・切り替えを選べる）。 */
    private void use(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, io.github.genichimaruo.singulo.android.work.ProcedureStep ps) {
        Boolean arrived = go(ps.pos(), FACE_TICKS);
        if (arrived == null) {
            fail(c, job, WorkConsoleBlockEntity.Failure.UNREACHABLE);
            return;
        }
        if (!arrived) {
            return;
        }
        BlockPos p = ps.pos();
        BlockState s = level.getBlockState(p);
        net.minecraft.world.entity.player.Player as = actor(level, c, null);
        as.moveTo(android.getX(), android.getY(), android.getZ(), android.getYRot(), android.getXRot());
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false);
        var event = new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                as, net.minecraft.world.InteractionHand.MAIN_HAND, p, hit);
        if (io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.locked(level, p, as)
                || net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)
                || event.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY) {
            fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
            return;
        }
        android.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (s.getBlock() instanceof net.minecraft.world.level.block.LeverBlock lever) {
            boolean on = s.getValue(net.minecraft.world.level.block.LeverBlock.POWERED);
            boolean flip = ps.option() == io.github.genichimaruo.singulo.android.work.ProcedureStep.USE_TOGGLE || ps.option() == io.github.genichimaruo.singulo.android.work.ProcedureStep.USE_ON && !on
                    || ps.option() == io.github.genichimaruo.singulo.android.work.ProcedureStep.USE_OFF && on;
            if (flip) {
                lever.pull(s, level, p);
            }
        } else if (s.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock button) {
            if (!s.getValue(net.minecraft.world.level.block.ButtonBlock.POWERED)) {
                button.press(s, level, p);
                level.playSound(null, p, SoundEvents.STONE_BUTTON_CLICK_ON, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1);
            }
        } else {
            as.moveTo(android.getX(), android.getY(), android.getZ(), android.getYRot(), android.getXRot());
            s.use(level, as, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
        }
        progress++;
    }

    private void stopMining() {
        if (mineTarget != null && android.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(android.getId(), mineTarget, -1);
        }
        mineTarget = null;
        areaSearch.reset();
        growthSearch.reset();
        mineTimer = 0;
        mineSettled = false;
    }

    /** 採掘に使う道具: ダイヤのツルハシに、採掘アップグレードのエンチャントを付けたもの。 */
    private ItemStack miningTool() {
        ItemStack tool = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        ItemStack upgrade = android.inventory().getItem(RuinAndroid.SLOT_MINING);
        net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(upgrade), tool);
        return tool;
    }

    /** ブロックを掘る時間（tick）。プレイヤーがその道具で地面に立って掘るのと同じ（効率強化も効く）。 */
    private static int breakTicks(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        float speed = tool.getDestroySpeed(state);
        if (speed > 1) {
            int eff = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY, tool);
            if (eff > 0) {
                speed += eff * eff + 1;
            }
        }
        boolean right = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        return Math.max(2, (int) Math.ceil(hardness * (right ? 30 : 100) / speed));
    }

    /** 掘ってよいブロックか。 */
    private boolean mineable(ServerLevel level, BlockPos pos, ItemStack tool, WorkConsoleBlockEntity c) {
        if (mineSkip.contains(pos) || pos.equals(android.blockPosition().below())) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null
                || state.is(net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST)) {
            return false;                      // 芽生えたアメジストは壊さない（壊すと何も出ず、二度と育たない）
        }
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            return false;                      // ダイヤのツルハシで掘れない（ネザライトより硬い物など）
        }
        return !io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.isActiveCrown(level, pos)
                && !io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.locked(level, pos, actor(level, c, null));
    }

    /** 次に掘るブロック: 上の段から、同じ段では近い順。 */
    @Nullable
    private BlockPos findMineTarget(ServerLevel level, io.github.genichimaruo.singulo.android.work.ProcedureStep ps, ItemStack tool,
                                    WorkConsoleBlockEntity c) {
        net.minecraft.world.level.levelgen.structure.BoundingBox box = ps.area();
        return areaSearch.find(box, WorkAreaSearch.Order.TOP_DOWN, android.blockPosition(), level.getGameTime(),
                p -> level.isLoaded(p) && mineable(level, p, tool, c));
    }

    /** 掘ってよいかを確かめるときの「だれとして」: 注文した人（いなければ指令台の持ち主）の代わりのプレイヤー。 */
    private net.minecraft.world.entity.player.Player actor(ServerLevel level, WorkConsoleBlockEntity c, @Nullable WorkJob job) {
        java.util.UUID id = c.actorFor(android.workOrder());
        return net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                id == null ? java.util.UUID.nameUUIDFromBytes("singulo:android".getBytes(java.nio.charset.StandardCharsets.UTF_8)) : id,
                "[Singulo Android]"));
    }

    /**
     * 1回分を順に投入する。異なる材料のSIMULATEを同じ空き枠に対して行っても、
     * 全種類が同時に入る保証にはならない。実際に入った分を覚え、残りだけを次回投入する。
     */
    private boolean insertOnce(ServerLevel level, BlockPos at, RecipeData r) {
        // 入れる面: 記録した面か、自動なら、今この素材を受け付ける面（上・横・下の順）
        return insertOnce(item -> {
            Direction face = io.github.genichimaruo.singulo.android.work.MachineWatch.inputFace(level, at, r, item);
            return face == null ? null : io.github.genichimaruo.singulo.compat.Capabilities.get(level, io.github.genichimaruo.singulo.compat.Capabilities.ItemHandler.BLOCK, at, face);
        }, item -> {
            Direction face = io.github.genichimaruo.singulo.android.work.MachineWatch.inputFace(level, at, r, item);
            return face == null ? null : io.github.genichimaruo.singulo.android.work.LiquidStores.machine(level, at, face);
        }, r);
    }

    /** 素材ごとに入れる先（items: 物の入れ口、liquids: 液体・化学物質の入れ口。なければ null）を決めて、1回分を順に投入する。 */
    private boolean insertOnce(java.util.function.Function<ItemStack, net.minecraftforge.items.IItemHandler> items,
                               java.util.function.Function<ItemStack, io.github.genichimaruo.singulo.android.work.LiquidStores> liquids,
                               RecipeData r) {
        processingMoved = false;
        if (processingPending == null) {
            java.util.List<ItemCount> inputs = r.inputs();
            for (ItemCount need : inputs) {
                if (FluidSamples.liquid(need.item)) {
                    if (android.liquidAmount(need.item) < need.count) {
                        return false;
                    }
                } else {
                    long have = 0;
                    for (int slot : android.carrySlots()) {
                        if (need.matches(android.inventory().getItem(slot))) {
                            have += android.inventory().getItem(slot).getCount();
                        }
                    }
                    if (have < need.count) {
                        return false;
                    }
                }
            }
            processingPending = new java.util.ArrayList<>();
            for (ItemCount input : inputs) {
                processingPending.add(new ItemCount(input.item, input.count, input.alt));
            }
            processingPaid = false;
        }
        for (ItemCount need : processingPending) {
            if (need.count <= 0) {
                continue;
            }
            long moved = 0;
            boolean liquid = FluidSamples.liquid(need.item);
            io.github.genichimaruo.singulo.android.work.LiquidStores fin = liquid ? liquids.apply(need.item) : null;
            net.minecraftforge.items.IItemHandler in = liquid ? null : items.apply(need.item);
            if (liquid && fin != null) {
                long available = android.drainLiquid(need.item, need.count, true);
                if (available > 0) {
                    long put = fin.fill(need.item, available, false);
                    android.drainLiquid(need.item, put, false);
                    moved = put;
                }
            } else if (in != null && !liquid) {
                moved = insertFromCarry(in, need, need.count);
            }
            if (moved > 0) {
                if (!processingPaid) {
                    android.spend(CRAFT_COST);
                    processingPaid = true;
                }
                need.count -= moved;
                processingMoved = true;
            }
        }
        for (ItemCount need : processingPending) {
            if (need.count > 0) {
                return false;
            }
        }
        if (!processingPaid) {
            android.spend(CRAFT_COST);          // 素材のない加工でも1回分は使う
        }
        processingPending = null;
        processingPaid = false;
        return true;
    }

    private void deliver(WorkConsoleBlockEntity c, WorkJob job) {
        if (phase == 0) {
            fetch(c, job);
            return;
        }
        Boolean at = go(c.destination(job), HANDOVER_TICKS);
        if (at == null) {
            fail(c, job, WorkConsoleBlockEntity.Failure.UNREACHABLE);
        } else if (at) {
            if (c.deliver(android, job)) {
                android.gesture(RuinAndroid.GESTURE_NOD, null);            // 届けた: 小さくうなずく
                c.complete(job);
                afterJob(c, job);
            } else {
                wait = WAIT_TICKS;             // 納品口がいっぱい: 空くのを待つ
                android.setStall(RuinAndroid.STALL_FULL);
            }
        }
    }

    /**
     * 作業の素材（届ける物）を、実際に入っている受け渡し場所を順に回って取る。全部そろって持ち物に集めてから、次（作る・届ける）へ進む。
     */
    private void fetch(WorkConsoleBlockEntity c, WorkJob job) {
        if (pickups == null) {
            pickups = c.planPickup(job, android.blockPosition());
            pickupIndex = 0;
            if (pickups == null) {
                fail(c, job, WorkConsoleBlockEntity.Failure.MISSING);
                return;
            }
        }
        if (pickupIndex >= pickups.size()) {
            pickups = null;
            phase = 1;
            travel = 0;
            return;
        }
        WorkConsoleBlockEntity.Pickup p = pickups.get(pickupIndex);
        Boolean at = go(p.point(), HANDOVER_TICKS);
        if (at == null) {
            fail(c, job, WorkConsoleBlockEntity.Failure.UNREACHABLE);
        } else if (at) {
            if (depositFirst) {
                // 前の作業の残りを、ここで戻してから取る。戻しきれなければ、この作業はあとにして、ほかの場所へ戻しに行く
                depositFirst = false;
                if (!c.depositAt(android, WorkJob.simple(WorkJob.Type.DEPOSIT, leftoverOrder), p.point(), c.supplyPoints())) {
                    fail(c, job, WorkConsoleBlockEntity.Failure.DEFERRED);
                    returning = true;
                    return;
                }
            }
            if (c.fetchAt(android, job, p)) {
                pickupIndex++;
            } else {
                fail(c, job, WorkConsoleBlockEntity.Failure.MISSING);   // 取りに来るまでに減った: 少し待ってやり直す
            }
        }
    }

    private void fail(WorkConsoleBlockEntity c, WorkJob job, WorkConsoleBlockEntity.Failure why) {
        pickups = null;
        switch (why) {
            case UNREACHABLE -> android.setStall(RuinAndroid.STALL_UNREACHABLE);
            case MISSING -> android.setStall(RuinAndroid.STALL_MISSING);
            default -> { }
        }
        c.fail(job, why);
        android.workJob = null;
        wait = why == WorkConsoleBlockEntity.Failure.MISSING ? WAIT_TICKS : 0;
    }

    /**
     * ブロックの目の前の地面の中央へ歩く。中央に立ってブロックの方を向いたのを確かめてから（pause tick 立ち止まって）true。
     * 向かっている間は false、あきらめたら null。立てる場所がなければ、なるべく近くまで行けば着いたとみなす。
     * <p>道のりは経路探しで進み、目の前の1マスに入ったら、まっすぐ中央へ寄る（経路探しは手前で止まりがちなので）。
     */
    @Nullable
    private Boolean go(BlockPos target, int pause) {
        if (!target.equals(goalBlock)) {
            goalBlock = target.immutable();
            standAt = standSpot(target);
            arrived = 0;
            travel = 0;
        }
        int need = Math.max(FACE_TICKS, pause);
        if (standAt == null) {
            Vec3 toBlock = Vec3.atCenterOf(target).subtract(android.position());
            if (toBlock.horizontalDistanceSqr() < REACH * REACH && Math.abs(toBlock.y) < 3) {
                return face(target, need);
            }
            arrived = 0;
            return walk(Vec3.atCenterOf(target));
        }
        Vec3 goal = Vec3.atBottomCenterOf(standAt);
        Vec3 d = goal.subtract(android.position());
        if (d.horizontalDistanceSqr() < CENTER * CENTER && Math.abs(d.y) < 0.6 && (android.onGround() || android.floating())) {
            // 中央に着いた: ぴったり中央に立ち、ブロックの方を向く
            android.setPos(goal.x, android.getY(), goal.z);
            return face(target, need);
        }
        arrived = 0;
        if (android.state() == RuinAndroid.STATE_CRAFTING || android.state() == RuinAndroid.STATE_MINING) {
            android.setState(RuinAndroid.STATE_IDLE);      // 作り終えて歩き出す: 作る姿勢をやめる
        }
        if (d.horizontalDistanceSqr() < 1.6 * 1.6 && Math.abs(d.y) < 1.2) {
            // 目の前の1マスのそば: 経路探しをやめて、まっすぐ中央へ寄る
            android.getNavigation().stop();
            android.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 0.7);
            return ++travel > GIVE_UP_TICKS ? giveUp() : Boolean.FALSE;
        }
        return walk(goal);
    }

    /** 止まって、ブロックの方を向く（体も頭もすぐ向け、上下も合わせる）。need tick そうしていたら true。 */
    private Boolean face(BlockPos target, int need) {
        io.github.genichimaruo.singulo.android.work.AndroidEyes.wake(android.level(), target);   // 使う装置が目を開ける
        android.getNavigation().stop();
        android.setDeltaMovement(android.getDeltaMovement().multiply(0, 1, 0));
        Vec3 look = Vec3.atCenterOf(target);
        android.getLookControl().setLookAt(look.x, look.y, look.z, 90, 90);
        android.setYRot(yawTo(target));
        android.setYBodyRot(android.getYRot());
        android.setYHeadRot(android.getYRot());
        travel = 0;
        if (arrived < need) {
            arrived++;
            return false;
        }
        return true;
    }

    /** 経路探しで向かう。 */
    @Nullable
    private Boolean walk(Vec3 goal) {
        if (android.state() == RuinAndroid.STATE_CRAFTING || android.state() == RuinAndroid.STATE_MINING) {
            android.setState(RuinAndroid.STATE_IDLE);
        }
        if (++travel > GIVE_UP_TICKS) {
            return giveUp();
        }
        // 経路の決まり（作業指令台の「経路」）に従って向かう。経由地点を通ったら、あきらめるまでの時間を数え直す
        if (android.moveVia(goal, 1.0, android.workRules(), travel % 20 == 1)) {
            travel = 0;
        }
        return false;
    }

    @Nullable
    private Boolean giveUp() {
        travel = 0;
        goalBlock = null;
        return null;
    }

    private float yawTo(BlockPos target) {
        Vec3 d = Vec3.atCenterOf(target).subtract(android.position());
        return (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90;
    }

    /**
     * ブロックの目の前の立てる場所: 横の4方向のうち、空いていて足元があり、歩いて行ける所で、アンドロイドに一番近い所。なければ null。
     */
    @Nullable
    private BlockPos standSpot(BlockPos target) {
        var level = android.level();
        java.util.List<BlockPos> spots = new java.util.ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos p = target.relative(dir).above(dy);
                boolean free = level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                        && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
                boolean floor = !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
                if (free && floor) {
                    spots.add(p.immutable());
                    break;
                }
            }
        }
        spots.sort(java.util.Comparator.comparingDouble(p -> Vec3.atBottomCenterOf(p).distanceToSqr(android.position())));
        for (BlockPos p : spots) {
            if (android.blockPosition().equals(p)) {
                return p;
            }
            var path = android.getNavigation().createPath(p, 0);
            if (path != null && path.canReach()) {
                return p;
            }
        }
        // 経路が見つからなくても、一番近い立てる場所を目指す（近づけば、まっすぐ寄れることが多い）
        return spots.isEmpty() ? null : spots.get(0);
    }

    private void moveNear(BlockPos target) {
        Vec3 c = Vec3.atCenterOf(target);
        if (c.distanceToSqr(android.position()) > 4 * 4 && android.getNavigation().isDone()) {
            android.getNavigation().moveTo(c.x, c.y, c.z, 0.9);
        }
    }

    /**
     * 作業台で1回作る: 持ち物から素材を取り、作業台のレシピで作って、できた物と残る物（バケツなど）を持ち物に入れる。
     * 素材が足りない・レシピが合わないときは false（何も減らさない）。
     */
    private boolean craftOnce(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, BlockPos at, @Nullable RecipeData recipe) {
        craftStorageFull = false;
        if (recipe == null) {
            return false;
        }
        unbuffer(level, c, job, at, recipe);
        if (recipe.variants()) {
            return craftVariants(level, c, job, at, recipe);
        }
        NonNullList<ItemStack> grid = recipe.gridList();
        CraftingInput input = CraftingInput.of(3, 3, grid);
        java.util.Optional<CraftingRecipe> match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) {
            return false;
        }
        int[] carry = android.carrySlots();
        int[] used = new int[carry.length];
        for (ItemCount in : recipe.inputs()) {
            long left = in.count;
            for (int i = 0; i < carry.length && left > 0; i++) {
                ItemStack held = android.inventory().getItem(carry[i]);
                if (in.matches(held)) {
                    int take = (int) Math.min(left, held.getCount() - used[i]);
                    used[i] += take;
                    left -= take;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        ItemStack result = match.get().assemble(input, level.registryAccess());
        NonNullList<ItemStack> remaining = match.get().getRemainingItems(input);
        return commitCraft(level, c, job, at, used, result, remaining);
    }

    /**
     * 素材を使い、できた物と残る物（バケツなど）をすべて持ち物へ入れてみて、どれも落とさずに収まるときだけ確定する。
     * 持ち物に入りきらない分は、作業台の預かり箱へ入れる。収まらなければ何も変えずに false（{@link #craftStorageFull}）。
     */
    private boolean commitCraft(ServerLevel level, WorkConsoleBlockEntity console, WorkJob job, BlockPos at,
                                int[] used, ItemStack result, NonNullList<ItemStack> remaining) {
        int[] slots = android.carrySlots();
        var carried = new ItemStackHandler(slots.length);
        for (int i = 0; i < slots.length; i++) {
            ItemStack before = android.inventory().getItem(slots[i]);
            carried.setStackInSlot(i, before.copyWithCount(before.getCount() - used[i]));
        }
        var bench = level.getBlockEntity(at) instanceof AndroidWorkbenchBlockEntity b ? b : null;
        ItemStackHandler stored = null;
        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(result);
        outputs.addAll(remaining);
        for (ItemStack output : outputs) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(carried, output.copy(), false);
            if (!rest.isEmpty() && bench != null) {
                // たいていは持ち物に収まる。あふれたときだけ、作業台の預かり箱の写しを作って入れてみる
                if (stored == null) {
                    stored = new ItemStackHandler(bench.buffer().getSlots());
                    for (int i = 0; i < stored.getSlots(); i++) {
                        stored.setStackInSlot(i, bench.buffer().getStackInSlot(i).copy());
                    }
                }
                rest = ItemHandlerHelper.insertItemStacked(stored, rest, false);
            }
            if (!rest.isEmpty()) {
                craftStorageFull = true;
                return false;
            }
        }
        for (int i = 0; i < slots.length; i++) {
            android.inventory().setItem(slots[i], carried.getStackInSlot(i));
        }
        for (int i = 0; stored != null && i < stored.getSlots(); i++) {
            ItemStack before = bench.buffer().getStackInSlot(i);
            ItemStack after = stored.getStackInSlot(i);
            int added = after.getCount() - before.getCount();
            if (added > 0) {
                bench.buffer().setStackInSlot(i, after);
                console.holdBuffered(job, at, after, added);
            }
        }
        return true;
    }

    /**
     * 作る前に、足りない素材がこの作業台の預かり箱にあれば（前に作った物があふれて入れた分）、持ち物へ戻す。
     * この注文が預けた分だけを、持ち物に入るだけ取る。
     */
    private void unbuffer(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, BlockPos at, RecipeData recipe) {
        if (!(level.getBlockEntity(at) instanceof io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity bench) || bench.bufferEmpty()) {
            return;
        }
        var buffer = bench.buffer();
        for (ItemCount in : recipe.inputs()) {
            long have = 0;
            for (int slot : android.carrySlots()) {
                if (in.matches(android.inventory().getItem(slot))) {
                    have += android.inventory().getItem(slot).getCount();
                }
            }
            long missing = in.count - have;
            for (int i = 0; i < buffer.getSlots() && missing > 0; i++) {
                ItemStack s = buffer.getStackInSlot(i);
                if (s.isEmpty() || !in.matches(s)) {
                    continue;
                }
                long mine = c.buffered(job, at, s);
                int take = (int) Math.min(Math.min(missing, mine), s.getCount());
                if (take <= 0) {
                    continue;
                }
                ItemStack got = buffer.extractItem(i, take, false);
                ItemStack back = android.addToCarry(got);
                if (!back.isEmpty()) {
                    buffer.insertItem(i, back, false);              // 持ち物に入りきらない分は戻す
                }
                int moved = got.getCount() - back.getCount();
                c.releaseBuffered(job, at, got, moved);
                missing -= moved;
                if (moved < take) {
                    break;
                }
            }
        }
    }

    /**
     * 同じ種類の別の物も使うレシピを1回作る: 見本の欄ごとに、持ち物から合う物を1つずつ選んで並べ、作業台のレシピで作る。
     * 選んだ物を減らし、できた物と残る物を持ち物に入れる。並べられない・レシピが合わないときは false（何も減らさない）。
     */
    private boolean craftVariants(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, BlockPos at, RecipeData recipe) {
        int[] carry = android.carrySlots();
        int[] used = new int[carry.length];
        int[] from = new int[9];
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            from[i] = -1;
            ItemStack want = recipe.grid().get(i);
            if (want.isEmpty()) {
                continue;
            }
            net.minecraft.world.item.crafting.Ingredient alt = recipe.alt(i);
            for (int j = 0; j < carry.length; j++) {
                ItemStack s = android.inventory().getItem(carry[j]);
                boolean ok = alt != null ? !s.isEmpty() && alt.test(s) : ItemStack.isSameItemSameTags(s, want);
                if (ok && s.getCount() - used[j] > 0) {
                    used[j]++;
                    from[i] = j;
                    grid.set(i, s.copyWithCount(1));
                    break;
                }
            }
            if (from[i] < 0) {
                return false;
            }
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        java.util.Optional<CraftingRecipe> match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) {
            return false;
        }
        ItemStack result = match.get().assemble(input, level.registryAccess());
        NonNullList<ItemStack> remaining = match.get().getRemainingItems(input);
        return commitCraft(level, c, job, at, used, result, remaining);
    }

    /**
     * drops を全部、今の持ち物（作業のスロット）に入れられるか。持ち物へ入れるときと同じ順で数える（同じ物のスタックの空きへ足してから、
     * 空いているスロットへ）。
     */
    private boolean fitsInCarry(java.util.List<ItemStack> drops) {
        android.compactCarry();
        java.util.List<ItemStack> sim = new java.util.ArrayList<>();
        for (int slot : android.carrySlots()) {
            sim.add(android.inventory().getItem(slot).copy());
        }
        for (ItemStack d : drops) {
            if (d.isEmpty()) {
                continue;
            }
            int left = d.getCount();
            for (ItemStack held : sim) {
                if (left > 0 && !held.isEmpty() && ItemStack.isSameItemSameTags(held, d)) {
                    int move = Math.min(left, held.getMaxStackSize() - held.getCount());
                    held.grow(move);
                    left -= move;
                }
            }
            for (int i = 0; i < sim.size() && left > 0; i++) {
                if (sim.get(i).isEmpty()) {
                    int move = Math.min(left, d.getMaxStackSize());
                    sim.set(i, d.copyWithCount(move));
                    left -= move;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 掘る・切る・取ると持ち物からあふれると分かった（一緒に出る物は前もって分からない）: 足元に落とさず、ここまでの分で作業を終える。
     * 手順の最後の工程で集めていた分は、足りない分を注文に戻して、持っている物を置きに行く（残りは、あとでまた集める）。
     * 途中の工程や、まだ何も集めていなければ、この作業をあとに回して持ち物を置きに戻る。
     */
    private void endFull(WorkConsoleBlockEntity c, WorkJob job, ItemStack what) {
        WorkJob.Batch step = batchIndex < job.batch().size() ? job.batch().get(batchIndex) : null;
        long want = step == null ? 0 : Math.max(1, FluidSamples.amount(what)) * step.crafts();
        boolean last = step != null && procIndex == step.recipe().steps().size() - 1
                && ItemStack.isSameItemSameTags(what, step.recipe().output());
        if (last && progress > 0 && progress < want) {
            endShort(c, job, want - progress);
        } else {
            fail(c, job, WorkConsoleBlockEntity.Failure.DEFERRED);
            returning = true;
        }
    }

    /** 持ち物に入りきらなかった物は、足元に落とす（ふつうは起きない）。 */
    private void drop(ItemStack rest) {
        if (!rest.isEmpty()) {
            android.spawnAtLocation(rest);
        }
    }
}
