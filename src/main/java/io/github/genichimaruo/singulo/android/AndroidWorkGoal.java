package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.android.work.FluidSamples;
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
        fuelTrip = null;
        resetProcedure();
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
        net.neoforged.neoforge.items.IItemHandler in = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, at,
                r.inputFace());
        net.neoforged.neoforge.items.IItemHandler out = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, at,
                r.outputFace());
        net.neoforged.neoforge.fluids.capability.IFluidHandler fin = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, at, r.inputFace());
        net.neoforged.neoforge.fluids.capability.IFluidHandler fout = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, at, r.outputFace());
        if (in == null && fin == null || out == null && fout == null) {
            fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
            return;
        }
        // 燃料が切れていれば、燃料を足す（持っていなければ、取りに行く）。記録した燃料がなければ、取りに行かない
        if (!r.fuel().isEmpty() && needsFuel(level, at, r) && refuel(level, c, at, r)) {
            return;
        }
        boolean progress = false;
        while (inserted < step.crafts() && insertOnce(in, fin, r)) {
            inserted++;
            progress = true;
            android.spend(CRAFT_COST);
        }
        long want = step.crafts() * r.outputCount();
        // 流体の出てくる物: 装置の流体の出口から、タンクへ（主な物は要る分だけ、ほかの物は全部）
        if (fout != null) {
            for (ItemStack o : r.outputs()) {
                if (!FluidSamples.is(o)) {
                    continue;
                }
                boolean main = o == r.output();
                var like = FluidSamples.fluid(o);
                int limit = (int) Math.min(main ? want - collected : Integer.MAX_VALUE, android.tankSpace(like));
                if (limit <= 0) {
                    continue;
                }
                var got = fout.drain(like.copyWithAmount(limit), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if (!got.isEmpty()) {
                    android.fillTanks(got, false);
                    if (main) {
                        collected += got.getAmount();
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
        } else if ((stalled += PROCESS_INTERVAL) >= SHORT_TICKS && inserted >= step.crafts() && collected < want
                && (in == null || !holdsAny(in, r.inputs())) && (fin == null || !holdsFluid(fin, r.inputs()))) {
            // 入れた素材を使い切ったのに、できた物が足りない（確率で出る物）: 足りない分の回数を足して、集まるまで作り直す
            endShort(c, job, want - collected);
            return;
        } else if (stalled >= STALL_TICKS) {
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

    /** 燃料を取りに行く受け渡し場所（行っていなければ null）。 */
    @Nullable
    private BlockPos fuelTrip;

    /** 燃料を入れる面: 記録した面か、自動なら、装置から見てアンドロイドのいる横の面（かまどなら燃料の欄）。 */
    private Direction fuelSide(BlockPos machine, RecipeData r) {
        if (r.fuelFace() != null) {
            return r.fuelFace();
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
        net.neoforged.neoforge.items.IItemHandler side = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                machine, fuelSide(machine, r));
        return stalled >= 100 && side != null && side.getSlots() > 0;
    }

    /** 燃料を足す。持っていなければ取りに行き始めて true（このあと取りに行く）。足した・足せないときは false。 */
    private boolean refuel(ServerLevel level, WorkConsoleBlockEntity c, BlockPos machine, RecipeData r) {
        ItemStack fuel = r.fuel();
        if (carriedCount(fuel.getItem()) > 0) {
            net.neoforged.neoforge.items.IItemHandler side = level.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, machine, fuelSide(machine, r));
            if (side != null && insertFromCarry(side, fuel, carriedCount(fuel.getItem())) > 0) {
                stalled = 0;
                android.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 0.8F);
            }
            return false;
        }
        fuelTrip = c.fuelPoint(fuel, android.blockPosition());
        travel = 0;
        return fuelTrip != null;                // 在庫にない: そのまま待つ（加工が止まったままなら、やがて取り消す）
    }

    /** 燃料を取りに行って、受け渡し場所から（予約されていない物を）取る。取ったら装置へ戻る。 */
    private void fuelRun(WorkConsoleBlockEntity c, ItemStack fuel) {
        android.setActivity(RuinAndroid.ACT_FETCH);
        Boolean at = go(fuelTrip, HANDOVER_TICKS);
        if (at == null) {
            fuelTrip = null;
        } else if (at) {
            c.takeFuel(android, fuelTrip, fuel, Math.max(1, fuel.getCount()));
            fuelTrip = null;
            stalled = 0;
            travel = 0;
        }
    }

    /** 素材を使い切ったあと、できた物を待つ時間。過ぎても足りなければ、確率で出なかったとみなす。 */
    private static final int SHORT_TICKS = 20 * 30;

    /** 装置（入れる面）に、まだこれらの素材が残っているか（まだ加工している）。 */
    private static boolean holdsAny(net.neoforged.neoforge.items.IItemHandler h, java.util.List<ItemCount> inputs) {
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
        } else if (ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.BREED || ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.USE) {
            want = 1;                          // 1回通りやったら終わり（終わったら progress を 1 にする）
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
                breed(level, c, job, ps);
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
        if (FluidSamples.is(what)) {
            net.neoforged.neoforge.fluids.capability.IFluidHandler fh = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, ps.pos(), insert ? Direction.UP : Direction.DOWN);
            if (fh == null) {
                fail(c, job, WorkConsoleBlockEntity.Failure.BAD_RECIPE);
                return;
            }
            var like = FluidSamples.fluid(what);
            int limit = (int) Math.min(want - progress, Integer.MAX_VALUE);
            if (insert) {
                var f = android.drainTanks(like, limit, true);
                int put = fh.fill(f, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                android.drainTanks(like, put, false);
                moved = put;
            } else {
                var got = fh.drain(like.copyWithAmount(Math.min(limit, android.tankSpace(like))), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                android.fillTanks(got, false);
                moved = got.getAmount();
            }
        } else {
            net.neoforged.neoforge.items.IItemHandler h = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
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
            net.neoforged.neoforge.items.IItemHandler h = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    s.pos(), Direction.UP);
            if (h != null && holdsAny(h, java.util.List.of(new ItemCount(s.item(), 1)))) {
                return false;
            }
        }
        return true;
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
        BlockPos best = null;
        double bestDist = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!android.level().isLoaded(p) || mineSkip.contains(p) || !ok.test(p)) {
                continue;
            }
            double d = p.distSqr(android.blockPosition());
            if (best == null || d < bestDist) {
                best = p.immutable();
                bestDist = d;
            }
        }
        return best;
    }

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
        if (travel % 20 == 1 || android.getNavigation().isDone()) {
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
        if (travel % 10 == 1 || android.getNavigation().isDone()) {
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
        if (mineIdle % 100 == 0) {
            growing = android.level() instanceof ServerLevel level && growing(level, ps);
        }
        if (growing) {
            android.setActivity(RuinAndroid.ACT_WAIT);
        }
        if (++mineIdle >= (growing ? GROW_WAIT_TICKS : limit)) {
            fail(c, job, WorkConsoleBlockEntity.Failure.STALLED);
        }
    }

    /** 育つのを待つ限度（20分）と、今、育つのを待っているか。 */
    private static final int GROW_WAIT_TICKS = 20 * 60 * 20;
    private boolean growing;

    /** 範囲に、まだ育つ物があるか。 */
    private static boolean growing(ServerLevel level, io.github.genichimaruo.singulo.android.work.ProcedureStep ps) {
        net.minecraft.world.level.levelgen.structure.BoundingBox box = ps.area();
        switch (ps.kind()) {
            case io.github.genichimaruo.singulo.android.work.ProcedureStep.HARVEST, io.github.genichimaruo.singulo.android.work.ProcedureStep.FELL -> {
                for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                    if (level.isLoaded(p) && grows(level, p, ps.kind() == io.github.genichimaruo.singulo.android.work.ProcedureStep.FELL)) {
                        return true;
                    }
                }
                return false;
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

    /** 手に入れた物を持ち物に入れ、目当ての物の数を数える。 */
    private void collect(java.util.List<ItemStack> drops, ItemStack what) {
        for (ItemStack d : drops) {
            if (d.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItemSameComponents(d, what)) {
                progress += d.getCount();
            }
            drop(android.addToCarry(d));
        }
    }

    /** ほかの mod の守りも効くよう、注文した人として、そのブロックを壊してよいか確かめる。 */
    private boolean mayBreak(ServerLevel level, WorkConsoleBlockEntity c, BlockPos p) {
        if (io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.isActiveCrown(level, p)) {
            return false;
        }
        net.minecraft.world.entity.player.Player as = actor(level, c, null);
        return !net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, p,
                level.getBlockState(p), as)).isCanceled();
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
        if (mineTarget != null && !harvestable(level, mineTarget)) {
            stopMining();
        }
        if (mineTarget == null) {
            mineTarget = nearestInArea(ps.area(), p -> harvestable(level, p));
            travel = 0;
            if (mineTarget == null) {
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
        if (++mineTimer < HARVEST_TICKS) {
            return;
        }
        stopMining();
        if (!mayBreak(level, c, target)) {
            mineSkip.add(target);
            return;
        }
        collect(harvestBlock(level, target), what);
        android.spend(CRAFT_COST);
    }

    /** 収穫する: 作物は種を1つ使って植え直し、スイートベリーは実だけ摘み、ほかは壊して取る。手に入った物を返す。 */
    private java.util.List<ItemStack> harvestBlock(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        net.minecraft.world.level.block.Block b = s.getBlock();
        if (b instanceof net.minecraft.world.level.block.SweetBerryBushBlock) {
            int age = s.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE);
            int n = (age >= 3 ? 2 : 1) + level.random.nextInt(2);
            level.setBlock(p, s.setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 1), 2);
            level.playSound(null, p, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
            return new java.util.ArrayList<>(java.util.List.of(new ItemStack(net.minecraft.world.item.Items.SWEET_BERRIES, n)));
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
            level.levelEvent(2001, p, net.minecraft.world.level.block.Block.getId(s));     // 壊れる粒と音
            level.setBlock(p, replant, 2);
            return drops;
        }
        level.destroyBlock(p, false, android);
        return drops;
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
        tool.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                upgrade.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
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
        ItemStack tool = fellingTool();
        if (mineTarget != null && !level.getBlockState(mineTarget).is(net.minecraft.tags.BlockTags.LOGS)) {
            stopMining();
        }
        if (mineTarget == null) {
            // 一番下の段の原木（同じ段では近い順）を根元にする
            net.minecraft.world.level.levelgen.structure.BoundingBox box = ps.area();
            for (int y = box.minY(); y <= box.maxY() && mineTarget == null; y++) {
                mineTarget = nearestInArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(box.minX(), y, box.minZ(), box.maxX(), y,
                        box.maxZ()), p -> level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS)
                        && !level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.LOGS));
            }
            travel = 0;
            if (mineTarget == null) {
                idleNear(c, job, ps, FARM_IDLE_TICKS);
                return;
            }
            int total = 0;
            for (BlockPos p : tree(level, mineTarget)) {
                BlockState s = level.getBlockState(p);
                total += s.is(net.minecraft.tags.BlockTags.LOGS) ? breakTicks(level, p, s, tool) : 0;
            }
            mineNeed = Math.max(20, Math.min(200, total / 2));
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
            mineSkip.add(base);
            return;
        }
        // 木を丸ごと切る（守られたブロックは残す）
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        net.minecraft.world.entity.player.Player as = actor(level, c, null);
        for (BlockPos p : tree(level, base)) {
            if (io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.locked(level, p, as)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            drops.addAll(net.minecraft.world.level.block.Block.getDrops(s, level, p, level.getBlockEntity(p), android, tool));
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
        android.spend(MINE_COST * 4);
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
            if (ItemStack.isSameItemSameComponents(s, what)) {
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
        if (++craftTimer < RANCH_TICKS) {
            return;
        }
        craftTimer = 0;
        ranchTarget = null;
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
    private void breed(ServerLevel level, WorkConsoleBlockEntity c, WorkJob job, io.github.genichimaruo.singulo.android.work.ProcedureStep ps) {
        ItemStack feed = ps.item();
        if (feed.isEmpty() || carriedCount(feed.getItem()) <= 0) {
            progress = 1;
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
                progress = 1;                  // もう与える相手がいない: 終わり
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
        if (++craftTimer < RANCH_TICKS) {
            return;
        }
        craftTimer = 0;
        ranchTarget = null;
        if (android.takeFromCarry(feed, 1)) {
            target.setInLove(actor(level, c, null));          // ハートが出る
            android.spend(CRAFT_COST);
        }
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
        android.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (s.getBlock() instanceof net.minecraft.world.level.block.LeverBlock lever) {
            boolean on = s.getValue(net.minecraft.world.level.block.LeverBlock.POWERED);
            boolean flip = ps.option() == io.github.genichimaruo.singulo.android.work.ProcedureStep.USE_TOGGLE || ps.option() == io.github.genichimaruo.singulo.android.work.ProcedureStep.USE_ON && !on
                    || ps.option() == io.github.genichimaruo.singulo.android.work.ProcedureStep.USE_OFF && on;
            if (flip) {
                lever.pull(s, level, p, as);
            }
        } else if (s.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock button) {
            if (!s.getValue(net.minecraft.world.level.block.ButtonBlock.POWERED)) {
                button.press(s, level, p, as);
                level.playSound(null, p, SoundEvents.STONE_BUTTON_CLICK_ON, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1);
            }
        } else {
            as.moveTo(android.getX(), android.getY(), android.getZ(), android.getYRot(), android.getXRot());
            s.useWithoutItem(level, as, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
        }
        progress = 1;
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
    private boolean insertOnce(@Nullable net.neoforged.neoforge.items.IItemHandler in, @Nullable net.neoforged.neoforge.fluids.capability.IFluidHandler fin, RecipeData r) {
        java.util.List<ItemCount> inputs = r.inputs();
        for (ItemCount need : inputs) {
            if (FluidSamples.is(need.item)) {
                // 流体: タンクにあって、装置の流体の入口に全部入るか
                var f = FluidSamples.fluid(need.item).copyWithAmount((int) need.count);
                if (fin == null || android.tankAmount(f) < need.count || fin.fill(f, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE) < need.count) {
                    return false;
                }
                continue;
            }
            if (in == null) {
                return false;
            }
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
            if (FluidSamples.is(need.item)) {
                var f = android.drainTanks(FluidSamples.fluid(need.item), (int) need.count, false);
                int put = fin.fill(f, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if (put < f.getAmount()) {
                    android.fillTanks(f.copyWithAmount(f.getAmount() - put), false);     // 入りきらなかった分は戻す
                }
                continue;
            }
            android.takeFromCarry(need.item, (int) need.count);
            ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(in, need.item.copyWithCount((int) need.count), false);
            drop(android.addToCarry(rest));
        }
        return true;
    }

    /** 装置（流体の入口）に、まだ入れた流体が残っているか（まだ加工している）。 */
    private static boolean holdsFluid(net.neoforged.neoforge.fluids.capability.IFluidHandler h, java.util.List<ItemCount> inputs) {
        for (ItemCount in : inputs) {
            if (!FluidSamples.is(in.item)) {
                continue;
            }
            var like = FluidSamples.fluid(in.item);
            for (int t = 0; t < h.getTanks(); t++) {
                if (net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(h.getFluidInTank(t), like)) {
                    return true;
                }
            }
        }
        return false;
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
        if (recipe.variants()) {
            return craftVariants(level, recipe);
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

    /**
     * 同じ種類の別の物も使うレシピを1回作る: 見本の欄ごとに、持ち物から合う物を1つずつ選んで並べ、作業台のレシピで作る。
     * 選んだ物を減らし、できた物と残る物を持ち物に入れる。並べられない・レシピが合わないときは false（何も減らさない）。
     */
    private boolean craftVariants(ServerLevel level, RecipeData recipe) {
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
                boolean ok = alt != null ? !s.isEmpty() && alt.test(s) : ItemStack.isSameItemSameComponents(s, want);
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
        java.util.Optional<RecipeHolder<CraftingRecipe>> match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) {
            return false;
        }
        ItemStack result = match.get().value().assemble(input, level.registryAccess());
        NonNullList<ItemStack> remaining = match.get().value().getRemainingItems(input);
        for (int j = 0; j < carry.length; j++) {
            if (used[j] > 0) {
                ItemStack s = android.inventory().getItem(carry[j]);
                android.inventory().setItem(carry[j], s.copyWithCount(s.getCount() - used[j]));
            }
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
