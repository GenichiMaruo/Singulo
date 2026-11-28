package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.android.program.Library;
import io.github.genichimaruo.singulo.android.program.Program;
import io.github.genichimaruo.singulo.android.program.ProgramError;
import io.github.genichimaruo.singulo.android.program.Values;
import io.github.genichimaruo.singulo.android.program.Vm;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * 動き方「プログラム」（0.24.0）: ステーションのプログラムカードの文の通りに動く。
 * <ul>
 * <li>場所はステーションの巡回路の欄の作業位置カード（金床で付けた名前か、p1〜p8）か、{@code pos(x, y, z)}。
 * ステーションから {@link #RANGE} ブロック以内、同じ次元だけ。読み込まれていないチャンクには触れない（動きは失敗、調べると空）。</li>
 * <li>1 tick に動かすのは {@link #BUDGET} 手まで（重いプログラムでもサーバーを止めない）。</li>
 * <li>取る・入れる・使う・掘るは、ステーションの持ち主として、ほかの mod の守り（右クリック・ブロックを壊す）とシールドの許可証を確かめる。
 * 持ち物からあふれる分は取らない・掘らない（足元に落とさない）。</li>
 * <li>戦い・充電・修理の間は止まり、終わると続きから動く。ワールドを開き直したとき、カードや場所が変わったときは最初から動く。</li>
 * <li>attack(m, p, r) は、相手を狙いにして、いつもの戦い方（型ごとの Goal）で戦わせる。そのあいだプログラムの Goal は止まり、
 * {@link #watch} が毎 tick、相手が倒れたか・範囲（p から r ブロック）を出たか・{@link #ATTACK_TICKS} たったかを見て、終わらせる。
 * auto_fight(False) にすると、自分で敵を探す・打たれて仕返しするのをやめ、attack の相手とだけ戦う（充電・修理はいつもどおり）。</li>
 * </ul>
 */
final class AndroidProgram implements Vm.Host {
    /** 1 tick に動かす手数。 */
    static final int BUDGET = 400;
    /** 動ける範囲（ステーションからの横の距離と、高さの差）。 */
    static final double RANGE = 64;
    /** たどり着けずにあきらめるまで（tick）と、手の届く距離。 */
    static final int GIVE_UP_TICKS = 20 * 20;
    static final double REACH = 2.4, MINE_REACH = 4.5;
    static final int MINE_COST = 6, USE_COST = 1;
    /** mobs() の範囲の上限と、1回の attack の長さの上限（tick）。 */
    static final double MAX_SCAN = 16;
    static final int ATTACK_TICKS = 20 * 60;
    /** say の連発の上限（たまる数と、1つ戻るまでの tick）。 */
    static final int SAY_BURST = 8, SAY_REFILL = 5;
    /** 画面に出す状態。 */
    static final int P_NONE = 0, P_RUNNING = 1, P_WAITING = 2, P_DONE = 3, P_ERROR = 4, P_PAUSED = 5;

    /** 名前の付いた場所（巡回路の欄の作業位置カード）。 */
    record Point(String name, BlockPos pos) {}

    private final RuinAndroid android;
    private String source = "";
    private List<Point> points = List.of();
    @Nullable
    private Vm vm;
    @Nullable
    private ProgramError compileError;
    /** 進めている動き（なければ null）とその引数、かかっている時間。 */
    @Nullable
    private String action;
    private List<Object> actionArgs = List.of();
    private int timer, travel, face;
    @Nullable
    private BlockPos goalBlock, stand;
    private int mineNeed;
    private int sayTokens = SAY_BURST, sayRefill;
    /** 最後に say した言葉（画面に出す）と、最近の言葉（書く画面の「出力」に出す。新しいものが後ろ）。 */
    private String lastSaid = "";
    static final int LOG_SIZE = 8;
    /** 知らせ: 届く距離（省くと16、64まで）・ためておける数（あふれたら古いものから捨てる）・入れ子のリストの深さ。 */
    static final double SEND_RANGE = 16, MAX_SEND_RANGE = 64;
    static final int INBOX_SIZE = 16, MESSAGE_DEPTH = 4;
    private final java.util.ArrayDeque<Values.Message> inbox = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> log = new java.util.ArrayDeque<>();
    private final Map<String, Predicate<ItemStack>> itemCache = new HashMap<>();
    /** 自分で敵を探して戦うか（auto_fight）。 */
    private boolean autoFight = true;
    /** attack の相手と範囲（中心と半径。半径が負なら範囲なし）、たった時間、結果（まだなら null）。 */
    @Nullable
    private net.minecraft.world.entity.LivingEntity attackTarget;
    @Nullable
    private BlockPos zoneCenter;
    private double zoneRadius = -1;
    private int attackTicks;
    @Nullable
    private Boolean attackResult;

    AndroidProgram(RuinAndroid android) {
        this.android = android;
    }

    // ------------------------------------------------------------------ 設定・状態

    /** ステーションのプログラムと場所を受け取る（変わったら最初から動かす）。 */
    void configure(String source, List<Point> points) {
        if (source.equals(this.source) && points.equals(this.points) && (vm != null || compileError != null || source.isBlank())) {
            return;
        }
        this.source = source;
        this.points = List.copyOf(points);
        restart();
    }

    /** 最初から動かし直す。 */
    void restart() {
        cancelAction();
        vm = null;
        compileError = null;
        lastSaid = "";
        log.clear();
        itemCache.clear();
        filterCache.clear();
        inbox.clear();
        autoFight = true;
        if (source.isBlank()) {
            return;
        }
        try {
            vm = Program.compile(source).start(android.getUUID().getLeastSignificantBits() ^ android.level().getGameTime());
        } catch (ProgramError e) {
            compileError = e;
        }
    }

    boolean runnable() {
        return vm != null && (vm.state() == Vm.State.RUNNING || vm.state() == Vm.State.WAITING);
    }

    int status(boolean paused) {
        if (compileError != null || vm != null && vm.state() == Vm.State.ERROR) {
            return P_ERROR;
        }
        if (vm == null) {
            return P_NONE;
        }
        if (vm.state() == Vm.State.DONE) {
            return P_DONE;
        }
        if (paused) {
            return P_PAUSED;
        }
        return "wait".equals(action) ? P_WAITING : P_RUNNING;
    }

    int line() {
        if (compileError != null) {
            return compileError.line();
        }
        if (vm == null) {
            return 0;
        }
        return vm.state() == Vm.State.ERROR ? vm.error().line() : vm.line();
    }

    /** 画面に出す一言: 誤りなら「e」＋キーと引数、そうでなければ最後に say した言葉（区切りは \u0001）。 */
    String message() {
        ProgramError e = compileError != null ? compileError : vm != null && vm.state() == Vm.State.ERROR ? vm.error() : null;
        if (e != null) {
            return "e\u0001" + e.key() + (e.args().length == 0 ? "" : "\u0001" + String.join("\u0001", e.args()));
        }
        return lastSaid.isEmpty() ? "" : "s\u0001" + lastSaid;
    }

    /** 最近の言葉（ で区切る）。 */
    String log() {
        return String.join("", log);
    }

    /** {@link #message} を画面の文に戻す。 */
    static Component describeMessage(String message) {
        if (message.isEmpty()) {
            return Component.empty();
        }
        String[] parts = message.split("\u0001", -1);
        if (parts[0].equals("e") && parts.length >= 2) {
            Object[] args = java.util.Arrays.copyOfRange(parts, 2, parts.length);
            return Component.translatable("program.singulo.error." + parts[1], args);
        }
        return Component.literal(parts.length >= 2 ? parts[1] : "");
    }

    // ------------------------------------------------------------------ 動かす

    void tick(ServerLevel level) {
        if (vm == null) {
            return;
        }
        if (sayTokens < SAY_BURST && ++sayRefill >= SAY_REFILL) {
            sayRefill = 0;
            sayTokens++;
        }
        if (action != null) {
            Object result;
            try {
                result = step(level);
            } catch (ProgramError e) {
                cancelAction();
                vm.fail(e);
                report(level);
                return;
            }
            if (result == Vm.PENDING) {
                return;
            }
            cancelAction();
            vm.resume(result);
        }
        Vm.State before = vm.state();
        vm.run(this, BUDGET);
        if (vm.state() == Vm.State.ERROR && before != Vm.State.ERROR) {
            report(level);
        }
    }

    /** 誤りで止まったことを持ち主に知らせる。 */
    private void report(ServerLevel level) {
        if (vm == null || vm.error() == null) {
            return;
        }
        ServerPlayer owner = ownerPlayer(level);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("program.singulo.stopped", android.getDisplayName(), vm.error().describe())
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    @Nullable
    private ServerPlayer ownerPlayer(ServerLevel level) {
        java.util.UUID id = android.owner();
        return id == null ? null : level.getServer().getPlayerList().getPlayer(id);
    }

    /** 戦い・充電で止めたとき: 歩くのをやめる（動きは続きからやり直す）。 */
    void pause() {
        android.getNavigation().stop();
        stopMining();
        travel = 0;
        face = 0;
        goalBlock = null;
        stand = null;
        if (android.state() == RuinAndroid.STATE_MINING) {
            android.setState(RuinAndroid.STATE_IDLE);
        }
    }

    private void cancelAction() {
        if (attackTarget != null && android.getTarget() == attackTarget) {
            android.setTarget(null);
        }
        attackTarget = null;
        attackResult = null;
        zoneCenter = null;
        zoneRadius = -1;
        pause();
        action = null;
        actionArgs = List.of();
        timer = 0;
    }

    @Override
    public Object call(String name, List<Object> args) {
        ServerLevel level = (ServerLevel) android.level();
        Library.Sig sig = Library.FUNCTIONS.get(name);
        if (sig != null && sig.action()) {
            // 動きは、始める前に引数を確かめてから、次の tick から進める
            switch (name) {
                case "go", "use", "mine" -> target(args.get(0));
                case "lever" -> target(args.get(0));
                case "take", "put" -> {
                    target(args.get(0));
                    if (args.size() >= 2) {
                        item(args.get(1));
                    }
                    if (args.size() >= 3 && args.get(2) != null) {
                        Values.integer(args.get(2));
                    }
                }
                case "wait" -> {
                    double s = number(args.get(0));
                    timer = (int) Math.max(1, Math.min(20 * 3600, Math.round(s * 20)));
                }
                case "attack" -> {
                    mob(args.get(0));
                    if (args.size() == 2) {
                        throw new ProgramError("arg_count", "attack", "1, 3", 2);
                    }
                    if (args.size() == 3) {
                        target(args.get(1));
                        radius(args.get(2));
                    }
                }
                default -> {
                }
            }
            action = name;
            actionArgs = List.copyOf(args);
            travel = 0;
            face = 0;
            return Vm.PENDING;
        }
        return switch (name) {
            case "say", "print" -> {
                say(level, args);
                yield null;
            }
            case "count" -> (long) countCarry(args.isEmpty() ? null : item(args.get(0)));
            case "count_at" -> {
                BlockPos p = target(args.get(0));
                IItemHandler h = level.isLoaded(p) ? handler(level, p, null) : null;
                yield (long) (h == null ? 0 : count(h, args.size() < 2 ? null : item(args.get(1))));
            }
            case "free_slots" -> {
                long n = 0;
                for (int slot : android.carrySlots()) {
                    if (android.inventory().getItem(slot).isEmpty()) {
                        n++;
                    }
                }
                yield n;
            }
            case "block" -> {
                BlockPos p = target(args.get(0));
                yield level.isLoaded(p) ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString() : null;
            }
            case "redstone" -> {
                BlockPos p = target(args.get(0));
                yield (long) (level.isLoaded(p) ? level.getBestNeighborSignal(p) : 0);
            }
            case "here" -> Values.Pos.of(android.blockPosition());
            case "station" -> Values.Pos.of(home());
            case "point" -> Values.Pos.of(target(args.get(0)));
            case "points" -> {
                List<Object> names = new ArrayList<>();
                for (Point pt : points) {
                    names.add(pt.name());
                }
                yield new Values.PList(names);
            }
            case "is_day" -> level.isDay();
            case "time" -> level.getDayTime() % 24000;
            case "charge" -> (long) Math.round(100 * android.chargeFraction());
            case "health" -> (long) Math.round(100 * android.getHealth() / android.getMaxHealth());
            case "mobs" -> mobs(level, target(args.get(0)), args.size() >= 2 ? radius(args.get(1)) : 5,
                    args.size() >= 3 ? filter(args.get(2)) : null);
            case "matches" -> {
                net.minecraft.world.entity.LivingEntity e = resolve(level, args.get(0));
                Predicate<net.minecraft.world.entity.LivingEntity> f = filter(args.get(1));
                yield e != null && (f == null || f.test(e));
            }
            case "distance" -> where(level, args.get(0)).distanceTo(where(level, args.get(1)));
            case "send" -> send(level, args);
            case "receive" -> {
                String topic = args.isEmpty() || args.get(0) == null ? null : text(args.get(0));
                Values.Message found = null;
                for (var it = inbox.iterator(); it.hasNext(); ) {
                    Values.Message m = it.next();
                    if (topic == null || m.topic().equals(topic)) {
                        found = m;
                        it.remove();
                        break;
                    }
                }
                yield found;
            }
            case "auto_fight" -> {
                autoFight = Values.truthy(args.get(0));
                if (!autoFight && android.getTarget() != null && android.getTarget() != attackTarget) {
                    android.setTarget(null);             // 今の戦いもやめる
                }
                yield null;
            }
            default -> throw new ProgramError("unknown_function", name);
        };
    }

    /** 進めている動きを1 tick 進める。終わったら結果、まだなら {@link Vm#PENDING}。 */
    private Object step(ServerLevel level) {
        String a = action;
        if (a == null) {
            return null;
        }
        switch (a) {
            case "wait" -> {
                android.getNavigation().stop();
                return --timer <= 0 ? null : Vm.PENDING;
            }
            case "home" -> {
                BlockPos h = home();
                Boolean arrived = walkTo(level, h.above(), true);
                return arrived == null ? Boolean.FALSE : arrived ? Boolean.TRUE : Vm.PENDING;
            }
            case "go" -> {
                BlockPos p = target(actionArgs.get(0));
                if (!level.isLoaded(p)) {
                    return Boolean.FALSE;
                }
                Boolean arrived = standable(level, p) ? walkTo(level, p, false) : approach(level, p, REACH);
                return arrived == null ? Boolean.FALSE : arrived ? Boolean.TRUE : Vm.PENDING;
            }
            case "take", "put" -> {
                BlockPos p = target(actionArgs.get(0));
                if (!level.isLoaded(p)) {
                    return 0L;
                }
                Boolean arrived = approach(level, p, REACH);
                if (arrived == null) {
                    return 0L;
                }
                if (!arrived) {
                    return Vm.PENDING;
                }
                Predicate<ItemStack> what = actionArgs.size() >= 2 ? item(actionArgs.get(1)) : null;
                long limit = actionArgs.size() >= 3 && actionArgs.get(2) != null ? Math.max(0, Values.integer(actionArgs.get(2))) : Long.MAX_VALUE;
                if (!mayInteract(level, p)) {
                    android.setStall(RuinAndroid.STALL_PROTECTED);
                    return 0L;
                }
                android.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return a.equals("take") ? (Object) take(level, p, what, limit) : (Object) put(level, p, what, limit);
            }
            case "use" -> {
                BlockPos p = target(actionArgs.get(0));
                if (!level.isLoaded(p)) {
                    return Boolean.FALSE;
                }
                Boolean arrived = approach(level, p, REACH);
                if (arrived == null) {
                    return Boolean.FALSE;
                }
                if (!arrived) {
                    return Vm.PENDING;
                }
                return use(level, p);
            }
            case "mine" -> {
                return mine(level, target(actionArgs.get(0)));
            }
            case "lever" -> {
                BlockPos p = target(actionArgs.get(0));
                if (!level.isLoaded(p)) {
                    return Boolean.FALSE;
                }
                boolean on = Values.truthy(actionArgs.get(1));
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof net.minecraft.world.level.block.LeverBlock
                        && s.getValue(net.minecraft.world.level.block.LeverBlock.POWERED) == on) {
                    return Boolean.TRUE;                 // もうその向き: 歩いて行かない
                }
                Boolean arrived = approach(level, p, REACH);
                if (arrived == null) {
                    return Boolean.FALSE;
                }
                if (!arrived) {
                    return Vm.PENDING;
                }
                return lever(level, p, on);
            }
            case "attack" -> {
                return attack(level);
            }
            default -> {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ 引数

    private BlockPos home() {
        BlockPos h = android.home();
        if (h == null) {
            throw new ProgramError("no_station");
        }
        return h;
    }

    /** 場所の引数（場所・場所の名前・場所の番号）を、範囲を確かめてブロックの場所に。 */
    private BlockPos target(Object v) {
        BlockPos p;
        if (v instanceof Values.Pos pos) {
            p = pos.block();
        } else if (v instanceof String name) {
            p = null;
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i).name().equals(name) || name.equals("p" + (i + 1))) {
                    p = points.get(i).pos();
                    break;
                }
            }
            if (p == null) {
                throw new ProgramError("no_point", name);
            }
        } else if (v instanceof Long index) {
            if (index < 1 || index > points.size()) {
                throw new ProgramError("no_point", index);
            }
            p = points.get((int) (index - 1)).pos();
        } else {
            throw new ProgramError("not_position", Values.typeName(v));
        }
        BlockPos h = home();
        if (Math.abs(p.getX() - h.getX()) > RANGE || Math.abs(p.getZ() - h.getZ()) > RANGE || Math.abs(p.getY() - h.getY()) > RANGE) {
            throw new ProgramError("too_far", Values.repr(Values.Pos.of(p)), (int) RANGE);
        }
        return p;
    }

    private static double number(Object v) {
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Double d) {
            return d;
        }
        throw new ProgramError("not_number", Values.typeName(v));
    }

    /** 物の引数: "minecraft:coal"・"coal"（minecraft: を省ける）・"#c:ingots"（タグ）・None（何でも）。 */
    @Nullable
    private Predicate<ItemStack> item(@Nullable Object v) {
        if (v == null) {
            return null;
        }
        if (!(v instanceof String s)) {
            throw new ProgramError("not_text", Values.typeName(v));
        }
        Predicate<ItemStack> cached = itemCache.get(s);
        if (cached != null) {
            return cached;
        }
        Predicate<ItemStack> p;
        if (s.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(s.substring(1));
            if (id == null) {
                throw new ProgramError("bad_item", s);
            }
            TagKey<Item> tag = TagKey.create(net.minecraft.core.registries.Registries.ITEM, id);
            p = stack -> stack.is(tag);
        } else {
            ResourceLocation id = ResourceLocation.tryParse(s.contains(":") ? s : "minecraft:" + s);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                throw new ProgramError("bad_item", s);
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            p = stack -> stack.is(item);
        }
        if (itemCache.size() < 64) {
            itemCache.put(s, p);
        }
        return p;
    }

    private static boolean matches(@Nullable Predicate<ItemStack> what, ItemStack stack) {
        return !stack.isEmpty() && (what == null || what.test(stack));
    }

    // ------------------------------------------------------------------ 歩く

    /** 立てる場所か（そこに空きがあり、足元がある）。 */
    private boolean standable(ServerLevel level, BlockPos p) {
        return !AndroidPathing.blocks(level, p, android) && !AndroidPathing.blocks(level, p.above(), android)
                && AndroidPathing.blocks(level, p.below(), android);
    }

    /** その場所に立つ。着いたら true、歩いている間 false、たどり着けなければ null。 */
    @Nullable
    private Boolean walkTo(ServerLevel level, BlockPos p, boolean near) {
        Vec3 goal = Vec3.atBottomCenterOf(p);
        Vec3 d = goal.subtract(android.position());
        // go() はそのブロックの真ん中まで（0.7 だと隣のブロックに立ったまま縁で着いたことになった）
        double arrive = near ? 1.6 : 0.3;
        if (d.horizontalDistanceSqr() < arrive * arrive && Math.abs(d.y) < 1.2 && (near || android.blockPosition().equals(p))) {
            android.getNavigation().stop();
            return true;
        }
        if (++travel > GIVE_UP_TICKS) {
            android.setStall(RuinAndroid.STALL_UNREACHABLE);
            return null;
        }
        // 経路探しは1ブロック手前で「着いた」とみなして止まるので、最後のひと足は自分で真ん中へ寄る
        if (d.horizontalDistanceSqr() < 2.5 * 2.5 && Math.abs(d.y) < 1.2 && android.getNavigation().isDone()) {
            android.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 1.0);
            return false;
        }
        if (android.moveVia(goal, 1.0, android.patrolRules(), travel % 20 == 1)) {
            travel = 0;
        }
        return false;
    }

    /** ブロックの目の前へ行き、そちらを向く。着いて向いたら true。 */
    @Nullable
    private Boolean approach(ServerLevel level, BlockPos target, double reach) {
        Vec3 c = Vec3.atCenterOf(target);
        Vec3 d = c.subtract(android.position());
        boolean close = d.horizontalDistanceSqr() < reach * reach && Math.abs(d.y) < 3;
        if (close && (stand == null || android.getNavigation().isDone()
                || Vec3.atBottomCenterOf(stand).distanceToSqr(android.position()) < 0.5)) {
            android.getNavigation().stop();
            android.getLookControl().setLookAt(c.x, c.y, c.z, 90, 90);
            float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90;
            android.setYRot(yaw);
            android.setYBodyRot(yaw);
            android.setYHeadRot(yaw);
            io.github.genichimaruo.singulo.android.work.AndroidEyes.wake(level, target);
            return ++face >= 3;
        }
        face = 0;
        if (!target.equals(goalBlock)) {
            goalBlock = target.immutable();
            stand = standSpot(level, target);
        }
        if (++travel > GIVE_UP_TICKS) {
            android.setStall(RuinAndroid.STALL_UNREACHABLE);
            return null;
        }
        Vec3 to = stand != null ? Vec3.atBottomCenterOf(stand) : c;
        if (android.moveVia(to, 1.0, android.patrolRules(), travel % 20 == 1)) {
            travel = 0;
        }
        return false;
    }

    /** ブロックの横の、立てて歩いて行ける場所（一番近いもの）。なければ null。 */
    @Nullable
    private BlockPos standSpot(ServerLevel level, BlockPos target) {
        List<BlockPos> spots = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos p = target.relative(dir).above(dy);
                if (standable(level, p)) {
                    spots.add(p.immutable());
                    break;
                }
            }
        }
        if (standable(level, target.above())) {
            spots.add(target.above());
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
        return spots.isEmpty() ? null : spots.get(0);
    }

    // ------------------------------------------------------------------ 持ち物・入れ物

    @Nullable
    private static IItemHandler handler(ServerLevel level, BlockPos p, @Nullable Direction side) {
        IItemHandler h = side == null ? null : level.getCapability(Capabilities.ItemHandler.BLOCK, p, side);
        return h != null ? h : level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
    }

    private static int count(IItemHandler h, @Nullable Predicate<ItemStack> what) {
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (matches(what, s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private int countCarry(@Nullable Predicate<ItemStack> what) {
        int n = 0;
        for (int slot : android.carrySlots()) {
            ItemStack s = android.inventory().getItem(slot);
            if (matches(what, s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** 持ち物に入る数（同じ物の空きと、空いたスロット）。 */
    private int room(ItemStack like) {
        int room = 0;
        for (int slot : android.carrySlots()) {
            ItemStack held = android.inventory().getItem(slot);
            if (held.isEmpty()) {
                room += like.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(held, like)) {
                room += Math.max(0, held.getMaxStackSize() - held.getCount());
            }
        }
        return room;
    }

    /** 入れ物から持ち物へ取る（下の面から。持ち物に入る分だけ）。取った数。 */
    private long take(ServerLevel level, BlockPos p, @Nullable Predicate<ItemStack> what, long limit) {
        IItemHandler h = handler(level, p, Direction.DOWN);
        if (h == null) {
            return 0;
        }
        android.compactCarry();
        long moved = 0;
        for (int i = 0; i < h.getSlots() && moved < limit; i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!matches(what, s)) {
                continue;
            }
            int want = (int) Math.min(Math.min(s.getCount(), limit - moved), room(s));
            if (want <= 0) {
                continue;
            }
            ItemStack got = h.extractItem(i, want, false);
            if (got.isEmpty()) {
                continue;
            }
            ItemStack rest = android.addToCarry(got);
            moved += got.getCount() - rest.getCount();
            if (!rest.isEmpty()) {
                // 入りきらなかった分は入れ物へ戻す（戻せなければ、どこかへ入れる。落とさない）
                ItemStack back = h.insertItem(i, rest, false);
                if (!back.isEmpty()) {
                    back = ItemHandlerHelper.insertItemStacked(h, back, false);
                }
                if (!back.isEmpty()) {
                    android.spawnAtLocation(back);
                }
            }
        }
        if (moved == 0 && count(h, what) > 0) {
            android.setStall(RuinAndroid.STALL_FULL);
        }
        return moved;
    }

    /** 持ち物から入れ物へ入れる（上の面から）。入れた数。 */
    private long put(ServerLevel level, BlockPos p, @Nullable Predicate<ItemStack> what, long limit) {
        IItemHandler h = handler(level, p, Direction.UP);
        if (h == null) {
            return 0;
        }
        long moved = 0;
        for (int slot : android.carrySlots()) {
            ItemStack s = android.inventory().getItem(slot);
            if (moved >= limit || !matches(what, s)) {
                continue;
            }
            int give = (int) Math.min(s.getCount(), limit - moved);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(h, s.copyWithCount(give), false);
            int in = give - rest.getCount();
            moved += in;
            android.inventory().setItem(slot, s.copyWithCount(s.getCount() - in));
        }
        android.compactCarry();
        return moved;
    }

    // ------------------------------------------------------------------ 使う・掘る

    /** だれとして（持ち主の代わりのプレイヤー）。 */
    private net.minecraft.world.entity.player.Player actor(ServerLevel level) {
        java.util.UUID id = android.owner();
        var as = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                id == null ? java.util.UUID.nameUUIDFromBytes("singulo:android".getBytes(java.nio.charset.StandardCharsets.UTF_8)) : id,
                "[Singulo Android]"));
        as.moveTo(android.getX(), android.getY(), android.getZ(), android.getYRot(), android.getXRot());
        return as;
    }

    /** そのブロックを開いて・使ってよいか（シールドの許可証と、ほかの mod の守り）。 */
    private boolean mayInteract(ServerLevel level, BlockPos p) {
        var as = actor(level);
        if (io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.locked(level, p, as)) {
            return false;
        }
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false);
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                as, net.minecraft.world.InteractionHand.MAIN_HAND, p, hit);
        return !net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled()
                && event.getUseBlock() != net.neoforged.neoforge.common.util.TriState.FALSE;
    }

    private Boolean use(ServerLevel level, BlockPos p) {
        if (!mayInteract(level, p)) {
            android.setStall(RuinAndroid.STALL_PROTECTED);
            return false;
        }
        if (!android.spend(USE_COST)) {
            return false;
        }
        BlockState s = level.getBlockState(p);
        var as = actor(level);
        android.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (s.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock button) {
            if (!s.getValue(net.minecraft.world.level.block.ButtonBlock.POWERED)) {
                button.press(s, level, p, as);
                level.playSound(null, p, SoundEvents.STONE_BUTTON_CLICK_ON, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1);
            }
            return true;
        }
        if (s.getBlock() instanceof net.minecraft.world.level.block.LeverBlock lever) {
            lever.pull(s, level, p, as);
            return true;
        }
        var result = s.useWithoutItem(level, as, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
        return result.consumesAction();
    }

    /** レバーを決めた向きにする（もうそうなら、触らずに True）。 */
    private Boolean lever(ServerLevel level, BlockPos p, boolean on) {
        BlockState s = level.getBlockState(p);
        if (!(s.getBlock() instanceof net.minecraft.world.level.block.LeverBlock lever)) {
            throw new ProgramError("not_lever", BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        }
        if (s.getValue(net.minecraft.world.level.block.LeverBlock.POWERED) == on) {
            return true;
        }
        if (!mayInteract(level, p)) {
            android.setStall(RuinAndroid.STALL_PROTECTED);
            return false;
        }
        if (!android.spend(USE_COST)) {
            return false;
        }
        android.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        lever.pull(s, level, p, actor(level));
        return true;
    }

    private void stopMining() {
        if (goalBlock != null && "mine".equals(action) && android.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(android.getId(), goalBlock, -1);
        }
        mineNeed = 0;
    }

    private ItemStack miningTool() {
        ItemStack tool = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        ItemStack upgrade = android.tool(RuinAndroid::isMiningUpgrade);
        tool.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                upgrade.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
        return tool;
    }

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

    /** 掘ってよいブロックか（空気・液体・壊せない物・入れ物・封じた物・芽生えたアメジスト・道具で掘れない物は掘らない）。 */
    private boolean mineable(ServerLevel level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null
                || io.github.genichimaruo.singulo.ruin.lab.PressureHullBlock.isSealed(state)
                || state.is(net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST)
                || pos.equals(android.blockPosition().below())) {
            return false;
        }
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            return false;
        }
        return !io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.isActiveCrown(level, pos)
                && !io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity.locked(level, pos, actor(level));
    }

    /** 1つのブロックを掘る。掘れたら true、掘れない・たどり着けない・持ち物に入らなければ false。 */
    private Object mine(ServerLevel level, BlockPos target) {
        if (!android.canMine()) {
            throw new ProgramError("needs_mining_upgrade");
        }
        if (!level.isLoaded(target)) {
            return Boolean.FALSE;
        }
        ItemStack tool = miningTool();
        if (!mineable(level, target, tool)) {
            if (!level.getBlockState(target).isAir()) {
                android.setStall(RuinAndroid.STALL_PROTECTED);
            }
            return Boolean.FALSE;
        }
        if (android.charge() < MINE_COST) {
            android.spend(MINE_COST);                    // 充電へ戻る（戻ってきたら続きから掘る）
            return Vm.PENDING;
        }
        Boolean arrived = approach(level, target, MINE_REACH);
        if (arrived == null) {
            return Boolean.FALSE;
        }
        if (!arrived) {
            if (android.state() == RuinAndroid.STATE_MINING) {
                android.setState(RuinAndroid.STATE_IDLE);
            }
            return Vm.PENDING;
        }
        BlockState state = level.getBlockState(target);
        if (mineNeed <= 0) {
            mineNeed = android.workTicks(breakTicks(level, target, state, tool));
            timer = 0;
        }
        android.setTool(RuinAndroid.TOOL_PICKAXE);
        if (android.state() != RuinAndroid.STATE_MINING) {
            android.setState(RuinAndroid.STATE_MINING);
        }
        timer++;
        level.destroyBlockProgress(android.getId(), target, Math.min(9, timer * 10 / Math.max(1, mineNeed)));
        if (timer % 5 == 0) {
            level.playSound(null, target, state.getSoundType(level, target, android).getHitSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                    0.5F, 0.8F);
        }
        if (timer < mineNeed) {
            return Vm.PENDING;
        }
        level.destroyBlockProgress(android.getId(), target, -1);
        mineNeed = 0;
        timer = 0;
        android.setState(RuinAndroid.STATE_IDLE);
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, target,
                state, actor(level))).isCanceled()) {
            android.setStall(RuinAndroid.STALL_PROTECTED);
            return Boolean.FALSE;
        }
        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(state, level, target, null, android, tool);
        if (!fits(drops)) {
            android.setStall(RuinAndroid.STALL_FULL);   // 壊すと持ち物からあふれる: 壊さない
            return Boolean.FALSE;
        }
        level.destroyBlock(target, false, android);
        for (ItemStack d : drops) {
            if (!d.isEmpty()) {
                ItemStack rest = android.addToCarry(d);
                if (!rest.isEmpty()) {
                    android.spawnAtLocation(rest);       // fits で確かめたので、ここには来ない
                }
            }
        }
        android.spend(MINE_COST);
        return Boolean.TRUE;
    }

    /** 落ちる物がみな持ち物に入るか。 */
    private boolean fits(List<ItemStack> drops) {
        android.compactCarry();
        List<ItemStack> sim = new ArrayList<>();
        for (int slot : android.carrySlots()) {
            sim.add(android.inventory().getItem(slot).copy());
        }
        for (ItemStack d : drops) {
            int left = d.getCount();
            for (ItemStack held : sim) {
                if (left > 0 && !held.isEmpty() && ItemStack.isSameItemSameComponents(held, d)) {
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

    // ------------------------------------------------------------------ モブ・戦う

    private static double radius(Object v) {
        double r = number(v);
        if (r < 0 || r > MAX_SCAN) {
            throw new ProgramError("bad_radius", Values.repr(v), (int) MAX_SCAN);
        }
        return r;
    }

    /** 絞り込みの覚え（同じ文字列・リストを毎回読み直さない）。 */
    private final Map<String, Predicate<net.minecraft.world.entity.LivingEntity>> filterCache = new HashMap<>();

    /**
     * モブの絞り込み: 文字列か、文字列のリスト。言葉は "hostile"（敵対モブ）・"animal"（動物）・"all"、モブの ID（"zombie"・
     * "minecraft:skeleton"）、タグ（"#minecraft:undead"）。頭に ! を付けると除く。除かないものが1つでもあれば、どれかに合うものだけ。
     * None なら絞らない。
     */
    @Nullable
    private Predicate<net.minecraft.world.entity.LivingEntity> filter(@Nullable Object v) {
        if (v == null) {
            return null;
        }
        List<Object> terms = v instanceof Values.PList l ? l.items() : List.of(v);
        String key = Values.repr(v);
        Predicate<net.minecraft.world.entity.LivingEntity> cached = filterCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<Predicate<net.minecraft.world.entity.LivingEntity>> include = new ArrayList<>(), exclude = new ArrayList<>();
        for (Object t : terms) {
            if (!(t instanceof String s) || s.isBlank() || s.equals("!")) {
                throw new ProgramError("bad_filter", Values.repr(t));
            }
            boolean not = s.startsWith("!");
            (not ? exclude : include).add(term(not ? s.substring(1).strip() : s.strip()));
        }
        Predicate<net.minecraft.world.entity.LivingEntity> f = e -> (include.isEmpty() || include.stream().anyMatch(p -> p.test(e)))
                && exclude.stream().noneMatch(p -> p.test(e));
        if (filterCache.size() < 32) {
            filterCache.put(key, f);
        }
        return f;
    }

    private static Predicate<net.minecraft.world.entity.LivingEntity> term(String s) {
        switch (s) {
            case "all" -> {
                return e -> true;
            }
            case "hostile" -> {
                return e -> e instanceof net.minecraft.world.entity.monster.Enemy;
            }
            case "animal" -> {
                return e -> e instanceof net.minecraft.world.entity.animal.Animal;
            }
            default -> {
            }
        }
        if (s.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(s.substring(1).contains(":") ? s.substring(1) : "minecraft:" + s.substring(1));
            if (id == null) {
                throw new ProgramError("bad_filter", "'" + s + "'");
            }
            TagKey<net.minecraft.world.entity.EntityType<?>> tag = TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, id);
            if (BuiltInRegistries.ENTITY_TYPE.getTag(tag).isEmpty()) {
                throw new ProgramError("bad_filter", "'" + s + "'");
            }
            return e -> e.getType().is(tag);
        }
        ResourceLocation id = ResourceLocation.tryParse(s.contains(":") ? s : "minecraft:" + s);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            throw new ProgramError("bad_filter", "'" + s + "'");
        }
        net.minecraft.world.entity.EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return e -> e.getType() == type;
    }

    /** 場所かモブの位置（場所はブロックの中心、モブは足元）。 */
    private Vec3 where(ServerLevel level, Object v) {
        if (v instanceof Values.Mob) {
            net.minecraft.world.entity.LivingEntity e = resolve(level, v);
            if (e == null) {
                throw new ProgramError("mob_gone");
            }
            return e.position();
        }
        return Vec3.atCenterOf(target(v));
    }

    private static Values.Mob mob(Object v) {
        if (!(v instanceof Values.Mob m)) {
            throw new ProgramError("not_mob", Values.typeName(v));
        }
        return m;
    }

    /** モブの値が指す生き物（いなければ null）。 */
    @Nullable
    private static net.minecraft.world.entity.LivingEntity resolve(ServerLevel level, Object v) {
        return level.getEntity(mob(v).uuid()) instanceof net.minecraft.world.entity.LivingEntity e && e.isAlive() ? e : null;
    }

    /** 範囲の中か（ブロックの中心から、足元まで r + 0.5 以内）。 */
    private static boolean within(net.minecraft.world.entity.Entity e, BlockPos center, double r) {
        return e.position().distanceToSqr(Vec3.atCenterOf(center)) <= (r + 0.5) * (r + 0.5);
    }

    /** 戦ってよいモブか（自分・護衛のアンドロイド・持ち主と登録者の飼っている動物・ボス（対番人モジュールがなければ）・プレイヤーは除く）。 */
    private boolean fair(net.minecraft.world.entity.LivingEntity e) {
        if (e == android || !(e instanceof net.minecraft.world.entity.Mob) || !e.isAlive()) {
            return false;
        }
        if (e instanceof RuinAndroid a && a.isGuard()) {
            return false;
        }
        if (e instanceof net.minecraft.world.entity.OwnableEntity pet && pet.getOwnerUUID() != null
                && (pet.getOwnerUUID().equals(android.owner()) || android.memberIdsForProgram().contains(pet.getOwnerUUID()))) {
            return false;
        }
        return !RuinAndroid.isBoss(e) || android.bossHunter();
    }

    private Values.PList mobs(ServerLevel level, BlockPos center, double r, @Nullable Predicate<net.minecraft.world.entity.LivingEntity> filter) {
        var box = new net.minecraft.world.phys.AABB(center).inflate(r + 1);
        List<net.minecraft.world.entity.LivingEntity> found = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                e -> fair(e) && within(e, center, r) && (filter == null || filter.test(e)));
        Vec3 c = Vec3.atCenterOf(center);
        found.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(c)));
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < Math.min(found.size(), 64); i++) {
            var e = found.get(i);
            out.add(new Values.Mob(e.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()));
        }
        return new Values.PList(out);
    }

    @Override
    public Object attribute(Object target, String name) {
        ServerLevel level = (ServerLevel) android.level();
        Values.Mob m = mob(target);
        net.minecraft.world.entity.LivingEntity e = level.getEntity(m.uuid()) instanceof net.minecraft.world.entity.LivingEntity l ? l : null;
        boolean alive = e != null && e.isAlive();
        if (name.equals("alive")) {
            return alive;
        }
        if (name.equals("type")) {
            return m.type();
        }
        if (!alive) {
            throw new ProgramError("mob_gone");
        }
        return switch (name) {
            case "name" -> e.getName().getString();
            case "health" -> (double) e.getHealth();
            case "max_health" -> (double) e.getMaxHealth();
            case "hostile" -> e instanceof net.minecraft.world.entity.monster.Enemy;
            case "baby" -> e.isBaby();
            case "named" -> e.hasCustomName();
            case "pos" -> Values.Pos.of(e.blockPosition());
            case "x" -> (long) e.blockPosition().getX();
            case "y" -> (long) e.blockPosition().getY();
            case "z" -> (long) e.blockPosition().getZ();
            default -> throw new ProgramError("no_attribute", "mob", name);
        };
    }

    /** attack の相手か（狙いを外さない・技を当ててよい）。 */
    boolean isAttackTarget(@Nullable net.minecraft.world.entity.Entity e) {
        return e != null && e == attackTarget && "attack".equals(action);
    }

    boolean autoFight() {
        return autoFight;
    }

    /** attack を始める（相手を狙いにする）か、終わっていれば結果を返す。 */
    private Object attack(ServerLevel level) {
        if (attackResult != null) {
            return attackResult;
        }
        if (attackTarget == null) {
            net.minecraft.world.entity.LivingEntity e = resolve(level, actionArgs.get(0));
            if (e == null) {
                return Boolean.FALSE;                    // もういない
            }
            if (!fair(e)) {
                throw new ProgramError("cannot_attack", mob(actionArgs.get(0)).type());
            }
            if (actionArgs.size() == 3) {
                zoneCenter = target(actionArgs.get(1));
                zoneRadius = radius(actionArgs.get(2));
                if (!within(e, zoneCenter, zoneRadius)) {
                    return Boolean.FALSE;                // はじめから範囲の外
                }
            }
            attackTarget = e;
            attackTicks = 0;
            android.setTarget(e);                        // 次の tick から、型ごとの戦い方で戦う（このあいだ、プログラムの Goal は止まる）
        }
        return Vm.PENDING;
    }

    /**
     * 毎 tick（プログラムの Goal が止まっていても）: attack の相手が倒れたか・いなくなったか・範囲を出たか・長すぎるかを見て、
     * 終わったら狙いを外し、結果を覚える（プログラムの Goal が戻ったときに渡す）。
     */
    void watch() {
        if (!"attack".equals(action) || attackTarget == null || attackResult != null) {
            return;
        }
        net.minecraft.world.entity.LivingEntity t = attackTarget;
        Boolean done = null;
        if (!t.isAlive() || t.isRemoved()) {
            done = t.isDeadOrDying() || t.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED;
        } else if (zoneCenter != null && !within(t, zoneCenter, zoneRadius)) {
            done = false;
        } else if (++attackTicks > ATTACK_TICKS || t.level() != android.level()) {
            done = false;
        }
        if (done != null) {
            attackResult = done;
            if (android.getTarget() == t) {
                android.setTarget(null);
            }
            return;
        }
        if (android.getTarget() == null && android.active()) {
            android.setTarget(t);                        // ほかの事（自分を守る戦い・充電）のあとも、同じ相手を狙い続ける
        }
    }

    // ------------------------------------------------------------------ 知らせ（アンドロイドどうし）

    private static String text(Object v) {
        if (!(v instanceof String s)) {
            throw new ProgramError("not_text", Values.typeName(v));
        }
        return s;
    }

    /**
     * send(topic, data, r): r ブロック以内の、同じ持ち主（か登録者どうし）のプログラムで動くアンドロイドへ知らせる。
     * 届いた数を返す。data は値をそのまま写す（リストは写しを送るので、あとで変えても相手のものは変わらない）。
     */
    private long send(ServerLevel level, List<Object> args) {
        String topic = text(args.get(0));
        if (topic.isEmpty() || topic.length() > 32) {
            throw new ProgramError("bad_topic", Values.repr(topic));
        }
        Object data = args.size() >= 2 ? copy(args.get(1), 0) : null;
        double r = args.size() >= 3 ? Math.min(MAX_SEND_RANGE, number(args.get(2))) : SEND_RANGE;
        if (!(r > 0)) {
            throw new ProgramError("bad_value", "send");
        }
        String sender = android.getName().getString();
        Values.Message m = new Values.Message(topic, data, Values.Pos.of(android.blockPosition()), sender);
        long n = 0;
        for (RuinAndroid other : level.getEntitiesOfClass(RuinAndroid.class, android.getBoundingBox().inflate(r),
                o -> o != android && o.isAlive() && o.distanceTo(android) <= r)) {
            if (other.isGuard() && other.mode() == RuinAndroid.MODE_PROGRAM && other.program != null && teammate(other)) {
                other.program.deliver(m);
                n++;
            }
        }
        return n;
    }

    /** 同じ持ち主か、どちらかが相手の持ち主を登録しているか。 */
    private boolean teammate(RuinAndroid other) {
        java.util.UUID mine = android.owner(), theirs = other.owner();
        if (mine == null || theirs == null) {
            return false;
        }
        return mine.equals(theirs) || android.memberIdsForProgram().contains(theirs) || other.memberIdsForProgram().contains(mine);
    }

    void deliver(Values.Message m) {
        inbox.addLast(m);
        while (inbox.size() > INBOX_SIZE) {
            inbox.removeFirst();
        }
    }

    /** 送れる値の写し（数・文字・真偽・None・場所・モブ・それらのリスト）。 */
    private static Object copy(Object v, int depth) {
        if (v == null || v instanceof Boolean || v instanceof Long || v instanceof Double || v instanceof String
                || v instanceof Values.Pos || v instanceof Values.Mob) {
            return v;
        }
        if (v instanceof Values.PList l && depth < MESSAGE_DEPTH) {
            List<Object> out = new ArrayList<>();
            for (Object o : l.items()) {
                out.add(copy(o, depth + 1));
            }
            return new Values.PList(out);
        }
        throw new ProgramError("cannot_send", Values.typeName(v));
    }

    // ------------------------------------------------------------------ 話す

    private void say(ServerLevel level, List<Object> args) {
        StringBuilder b = new StringBuilder();
        for (Object o : args) {
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append(Values.str(o));
            if (b.length() > 256) {
                b.setLength(256);
                break;
            }
        }
        String text = b.toString();
        lastSaid = text.length() > 64 ? text.substring(0, 64) : text;
        log.addLast(text.length() > 120 ? text.substring(0, 120) : text);
        while (log.size() > LOG_SIZE) {
            log.removeFirst();
        }
        if (sayTokens <= 0) {
            return;                                      // 連発しすぎ: 持ち主には送らない（画面の一言は変える）
        }
        sayTokens--;
        ServerPlayer owner = ownerPlayer(level);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("program.singulo.say", android.getDisplayName(), text));
        }
    }

    // ------------------------------------------------------------------ 動き（Goal）

    /** プログラムを動かす Goal（作業の Goal と同じ優先度）。戦い・充電・修理のあいだは止まる。 */
    final class ProgramGoal extends Goal {
        ProgramGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return android.isGuard() && android.mode() == RuinAndroid.MODE_PROGRAM && android.printed() && android.active()
                    && android.getTarget() == null && runnable();
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
            pause();
        }

        @Override
        public void tick() {
            if (android.level() instanceof ServerLevel level) {
                AndroidProgram.this.tick(level);
            }
        }
    }
}
