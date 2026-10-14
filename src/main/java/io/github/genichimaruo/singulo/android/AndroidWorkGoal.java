package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.android.work.ItemCount;
import io.github.genichimaruo.singulo.android.work.RecipeData;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;
import io.github.genichimaruo.singulo.android.work.WorkJob;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
    /** 採掘: 掘れる物がないまま待つ限度、手の届く距離、1つ掘るごとに使う充電。 */
    private static final int MINE_IDLE_TICKS = 20 * 30, MINE_COST = 6;
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
    private int stalled;
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
    private int dropIndex;
    private boolean dropReached;

    AndroidWorkGoal(RuinAndroid android) {
        this.android = android;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return android.isGuard() && android.workConsole() != null && android.active() && android.printed();
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
        phase = job.carried() ? 1 : 0;
        returning = false;
        pickups = null;
        dropPoints = null;
        travel = 0;
        crafted = 0;
        craftTimer = 0;
        batchIndex = 0;
        inserted = 0;
        collected = 0;
        stalled = 0;
        resetProcedure();
    }

    private void resetProcedure() {
        procIndex = 0;
        progress = 0;
        stopMining();
        mineIdle = 0;
        mineSkip.clear();
    }

    @Override
    public void tick() {
        if (!(android.level() instanceof ServerLevel level)) {
            return;
        }
        WorkConsoleBlockEntity c = console(level);
        if (c == null) {
            return;
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
            case CRAFT -> craft(level, c, job);
            case DELIVER -> deliver(c, job);
        }
    }

    /** 作業の今の段階を、画面と頭の上のアイコンに出す形にする。 */
    private int activityFor(WorkJob job) {
        return switch (job.type()) {
            case WAIT -> RuinAndroid.ACT_WAIT;
            case DEPOSIT, EXIT -> RuinAndroid.ACT_RETURN;
            case DELIVER -> phase == 0 ? RuinAndroid.ACT_FETCH : RuinAndroid.ACT_DELIVER;
            case CRAFT -> {
                if (phase == 0) {
                    yield RuinAndroid.ACT_FETCH;
                }
                if (batchIndex >= job.batch().size() || job.batch().get(batchIndex).recipe() == null) {
                    yield RuinAndroid.ACT_CRAFT;
                }
                RecipeData r = job.batch().get(batchIndex).recipe();
                if (r.procedure()) {
                    boolean mine = procIndex < r.steps().size()
                            && r.steps().get(procIndex).kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.MINE;
                    yield mine ? RuinAndroid.ACT_MINE : RuinAndroid.ACT_PROCESS;
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
            dropPoints.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(android.blockPosition())));
            dropIndex = 0;
            dropReached = false;
        }
        if (dropIndex >= dropPoints.size()) {
            boolean reached = dropReached;
            dropPoints = null;
            if (!reached) {
                return null;
            }
            wait = WAIT_TICKS;
            return false;
        }
        BlockPos point = dropPoints.get(dropIndex);
        Boolean at = go(point, HANDOVER_TICKS);
        if (at == null) {
            dropIndex++;
        } else if (at) {
            dropReached = true;
            if (c.depositAt(android, job, point)) {
                dropPoints = null;
                return true;
            }
            dropIndex++;                       // ここはいっぱい: 次の場所へ
        }
        return false;
    }

    private void finishDeposit(WorkJob job) {
        android.workJob = null;
        if (job.type() == WorkJob.Type.EXIT) {
            android.clearWork();
        }
    }

    /** 作業のあと: 持っている物で続けられればそのまま続け、なければ持ち物を資材口へ戻しに行く。 */
    private void afterJob(WorkConsoleBlockEntity c, WorkJob done) {
        WorkJob next = c.chainJob(android, done.orderId());
        if (next != null) {
            begin(next);
            return;
        }
        android.workJob = null;
        returning = !android.carryEmpty();
        travel = 0;
    }

    /** 作業のあと、持ち物を資材の受け渡し場所へ戻す。 */
    private void returnItems(WorkConsoleBlockEntity c) {
        Boolean done = dropAll(c, WorkJob.simple(WorkJob.Type.DEPOSIT, android.workOrder()));
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
        if (step.recipe() != null && step.recipe().processing()) {
            process(level, c, job, step);
            return;
        }
        boolean fast = android.level().getBlockState(at).is(io.github.genichimaruo.singulo.registry.SinguloBlocks.ANDROID_WORKBENCH.get());
        if (++craftTimer < (fast ? io.github.genichimaruo.singulo.android.work.AndroidWorkbenchBlockEntity.CRAFT_TICKS : CRAFT_TICKS)) {
            return;
        }
        craftTimer = 0;
        if (!craftOnce(level, step.recipe())) {
            android.setState(RuinAndroid.STATE_IDLE);
            fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
            return;
        }
        android.playSound(SoundEvents.VILLAGER_WORK_TOOLSMITH, 0.6F, 1.4F);
        android.sparks(level, Vec3.atCenterOf(at).add(0, 0.6, 0), 4, 0.25);
        crafted++;
        if (!android.spend(CRAFT_COST)) {
            return;                            // 充電切れ: 充電してから続きを作る
        }
        if (crafted >= step.crafts()) {
            // この工程を作り終えた: 次の工程（次の作業場所）へ。全部終われば、納品か片付けへ
            // （作る姿勢は、歩き出すまで続ける。ここで立ち姿に戻すと、続けて作るときに頭が一瞬ガクッと動くので）
            crafted = 0;
            batchIndex++;
        }
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
        net.neoforged.neoforge.items.IItemHandler in = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, at, Direction.UP);
        net.neoforged.neoforge.items.IItemHandler out = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, at, Direction.DOWN);
        if (in == null || out == null) {
            fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
            return;
        }
        boolean progress = false;
        while (inserted < step.crafts() && insertOnce(in, r)) {
            inserted++;
            progress = true;
            android.spend(CRAFT_COST);
        }
        long want = step.crafts() * Math.max(1, r.output().getCount());
        for (int i = 0; i < out.getSlots(); i++) {
            ItemStack s = out.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            int take = 0;
            if (ItemStack.isSameItemSameComponents(s, r.output())) {
                take = (int) Math.min(s.getCount(), want - collected);     // ほかの人の分までは取らない
            } else {
                for (ItemStack e : r.extras()) {
                    if (ItemStack.isSameItemSameComponents(s, e)) {
                        take = s.getCount();
                    }
                }
            }
            if (take <= 0) {
                continue;
            }
            ItemStack got = out.extractItem(i, take, false);
            if (got.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItemSameComponents(got, r.output())) {
                collected += got.getCount();
            }
            drop(android.addToCarry(got));
            progress = true;
        }
        if (progress) {
            stalled = 0;
            android.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.2F);
        } else if ((stalled += PROCESS_INTERVAL) >= STALL_TICKS) {
            fail(c, job, WorkConsoleBlockEntity.Failure.STALLED);
            return;
        }
        if (collected >= want) {
            inserted = 0;
            collected = 0;
            stalled = 0;
            batchIndex++;
        }
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
        long want = Math.max(1, what.getCount()) * step.crafts();
        if (progress >= want) {
            procIndex++;
            progress = 0;
            stalled = 0;
            stopMining();
            mineIdle = 0;
            mineSkip.clear();
            return;
        }
        if (ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.MINE) {
            mine(level, c, job, ps, what);
            return;
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
        net.neoforged.neoforge.items.IItemHandler h = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                ps.pos(), insert ? Direction.UP : Direction.DOWN);
        if (h == null) {
            fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
            return;
        }
        long moved = insert ? insertFromCarry(h, what, want - progress) : extractToCarry(h, what, want - progress);
        progress += moved;
        if (moved > 0) {
            stalled = 0;
            android.playSound(SoundEvents.ITEM_PICKUP, 0.4F, insert ? 0.9F : 1.2F);
        } else if ((stalled += PROCESS_INTERVAL) >= STALL_TICKS) {
            fail(c, job, WorkConsoleBlockEntity.Failure.STALLED);
        }
    }

    /** 持ち物から what を最大 limit 個、装置へ入れる。入れた数。 */
    private long insertFromCarry(net.neoforged.neoforge.items.IItemHandler h, ItemStack what, long limit) {
        long moved = 0;
        for (int slot : android.carrySlots()) {
            ItemStack s = android.inventory().getItem(slot);
            if (moved >= limit || s.isEmpty() || !ItemStack.isSameItemSameComponents(s, what)) {
                continue;
            }
            int give = (int) Math.min(s.getCount(), limit - moved);
            ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(h, s.copyWithCount(give), false);
            int in = give - rest.getCount();
            moved += in;
            android.inventory().setItem(slot, s.copyWithCount(s.getCount() - in));
        }
        return moved;
    }

    /** 装置から what を最大 limit 個、持ち物へ取り出す。取り出した数。 */
    private long extractToCarry(net.neoforged.neoforge.items.IItemHandler h, ItemStack what, long limit) {
        long moved = 0;
        for (int i = 0; i < h.getSlots() && moved < limit; i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, what)) {
                continue;
            }
            ItemStack got = h.extractItem(i, (int) Math.min(s.getCount(), limit - moved), false);
            moved += got.getCount();
            drop(android.addToCarry(got));
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
        ItemStack tool = miningTool();
        if (mineTarget != null && !mineable(level, mineTarget, tool, c)) {
            stopMining();
        }
        if (mineTarget == null) {
            mineTarget = findMineTarget(level, ps, tool, c);
            mineTimer = 0;
            travel = 0;
            if (mineTarget == null) {
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
            mineNeed = breakTicks(level, mineTarget, state, tool);
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
                } else if (travel % 20 == 1 || android.getNavigation().isDone()) {
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
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, target,
                state, as)).isCanceled()) {
            mineSkip.add(target);
            stopMining();
            return;
        }
        java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(state, level, target, level.getBlockEntity(target),
                android, tool);
        level.destroyBlock(target, false, android);
        for (ItemStack d : drops) {
            if (ItemStack.isSameItemSameComponents(d, what)) {
                progress += d.getCount();
            }
            drop(android.addToCarry(d));
        }
        stopMining();
        android.spend(MINE_COST);
    }

    private void stopMining() {
        if (mineTarget != null && android.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(android.getId(), mineTarget, -1);
        }
        mineTarget = null;
        mineTimer = 0;
        mineSettled = false;
    }

    /** 採掘に使う道具: ダイヤのツルハシに、採掘アップグレードのエンチャントを付けたもの。 */
    private ItemStack miningTool() {
        ItemStack tool = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        ItemStack upgrade = android.inventory().getItem(RuinAndroid.SLOT_MINING);
        tool.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                upgrade.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
        return tool;
    }

    /** ブロックを掘る時間（tick）。プレイヤーがその道具で地面に立って掘るのと同じ（効率強化も効く）。 */
    private static int breakTicks(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        float speed = tool.getDestroySpeed(state);
        if (speed > 1) {
            var lookup = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            int eff = lookup.get(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY)
                    .map(e -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(e, tool)).orElse(0);
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
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) {
            return false;
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
        BlockPos best = null;
        double bestDist = 0;
        for (int y = box.maxY(); y >= box.minY() && best == null; y--) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.isLoaded(p) || !mineable(level, p, tool, c)) {
                        continue;
                    }
                    double d = p.distSqr(android.blockPosition());
                    if (best == null || d < bestDist) {
                        best = p;
                        bestDist = d;
                    }
                }
            }
        }
        return best;
    }

    /** 掘ってよいかを確かめるときの「だれとして」: 注文した人（いなければ指令台の持ち主）の代わりのプレイヤー。 */
    private net.minecraft.world.entity.player.Player actor(ServerLevel level, WorkConsoleBlockEntity c, @Nullable WorkJob job) {
        java.util.UUID id = c.actorFor(android.workOrder());
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                id == null ? java.util.UUID.nameUUIDFromBytes("singulo:android".getBytes(java.nio.charset.StandardCharsets.UTF_8)) : id,
                "[Singulo Android]"));
    }

    /** 1回分の素材を、全部入るときだけ入れる。 */
    private boolean insertOnce(net.neoforged.neoforge.items.IItemHandler in, RecipeData r) {
        java.util.List<ItemCount> inputs = r.inputs();
        for (ItemCount need : inputs) {
            int have = 0;
            for (int slot : android.carrySlots()) {
                if (need.matches(android.inventory().getItem(slot))) {
                    have += android.inventory().getItem(slot).getCount();
                }
            }
            if (have < need.count) {
                return false;
            }
            ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(in, need.item.copyWithCount((int) need.count), true);
            if (!rest.isEmpty()) {
                return false;
            }
        }
        for (ItemCount need : inputs) {
            android.takeFromCarry(need.item, (int) need.count);
            ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(in, need.item.copyWithCount((int) need.count), false);
            drop(android.addToCarry(rest));
        }
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
                c.complete(job);
                afterJob(c, job);
            } else {
                wait = WAIT_TICKS;             // 納品口がいっぱい: 空くのを待つ
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
            if (c.fetchAt(android, job, p)) {
                pickupIndex++;
            } else {
                fail(c, job, WorkConsoleBlockEntity.Failure.MISSING);   // 取りに来るまでに減った: 少し待ってやり直す
            }
        }
    }

    private void fail(WorkConsoleBlockEntity c, WorkJob job, WorkConsoleBlockEntity.Failure why) {
        pickups = null;
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
        if (d.horizontalDistanceSqr() < CENTER * CENTER && Math.abs(d.y) < 0.6 && android.onGround()) {
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
        if (travel % 20 == 1 || android.getNavigation().isDone()) {
            android.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
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
    private boolean craftOnce(ServerLevel level, @Nullable RecipeData recipe) {
        if (recipe == null) {
            return false;
        }
        NonNullList<ItemStack> grid = recipe.gridList();
        CraftingInput input = CraftingInput.of(3, 3, grid);
        java.util.Optional<RecipeHolder<CraftingRecipe>> match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) {
            return false;
        }
        for (ItemCount in : recipe.inputs()) {
            int have = 0;
            for (int slot : android.carrySlots()) {
                if (in.matches(android.inventory().getItem(slot))) {
                    have += android.inventory().getItem(slot).getCount();
                }
            }
            if (have < in.count) {
                return false;
            }
        }
        ItemStack result = match.get().value().assemble(input, level.registryAccess());
        NonNullList<ItemStack> remaining = match.get().value().getRemainingItems(input);
        for (ItemCount in : recipe.inputs()) {
            android.takeFromCarry(in.item, (int) in.count);
        }
        drop(android.addToCarry(result));
        for (ItemStack r : remaining) {
            if (!r.isEmpty()) {
                drop(android.addToCarry(r));
            }
        }
        return true;
    }

    /** 持ち物に入りきらなかった物は、足元に落とす（ふつうは起きない）。 */
    private void drop(ItemStack rest) {
        if (!rest.isEmpty()) {
            android.spawnAtLocation(rest);
        }
    }
}
