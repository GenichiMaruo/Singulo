package io.github.genichimaruo.singulo.android;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 巡回警備（動き方「巡回」）。ステーションで決めた巡回路（作業位置カードの場所、8つまで）を順に回り（最後まで行ったら最初へ。
 * 往復も選べる）、各地点で少し立ち止まって見回す。夜だけ巡回する、も選べる（昼はステーションのそばで待つ）。
 * <ul>
 * <li>戦い: 巡回路の近くの敵と戦い（{@link RuinAndroid} の戦い）、片付けたら抜けた所から続ける。</li>
 * <li>仲間を呼ぶ: 戦いになったら、同じステーションの手の空いた護衛を呼ぶ（来た仲間は、片付いたら元の持ち場へ戻る）。</li>
 * <li>警報: 敵の群れ（{@link #ALARM_COUNT} 体以上）かボスを見つけたら、ステーションからレッドストーン信号を出し、持ち主に知らせる。</li>
 * <li>記録: 倒した相手と警報を、新しい順に {@link #LOG_SIZE} 件残す（アンドロイドの画面の「記録」のページ）。
 * 久しぶりにそばに来た持ち主へ手を振るとき、留守のあいだのことを一言知らせる。</li>
 * </ul>
 */
public final class AndroidPatrol {
    static final int LOG_SIZE = 16;
    /** 記録の種類: 倒した、敵の群れを見つけて警報、ボスを見つけて警報。 */
    public static final int KIND_KILL = 0, KIND_ALARM = 1, KIND_BOSS = 2;
    /** この数より多く敵が集まっていたら警報。同じアンドロイドは、警報のあと {@link #ALARM_COOLDOWN} 出さない。 */
    static final int ALARM_COUNT = 5, ALARM_COOLDOWN = 20 * 60;
    static final double ALARM_RANGE = 16, BOSS_RANGE = 24;
    /** 仲間を呼ぶ範囲。 */
    static final double CALL_RANGE = 48;
    /** 地点に着いたとみなす距離（横）と、たどり着けずにあきらめて次の地点へ行くまで。 */
    private static final double ARRIVE = 1.6;
    private static final int STUCK_TICKS = 20 * 30;
    /** 同じ相手を倒した記録をまとめる時間と距離。 */
    private static final int MERGE_TICKS = 20 * 60;
    private static final double MERGE_RANGE = 16;

    /** 記録の1件（gameTime はまとめるため、dayTime は画面に出す日と時刻）。 */
    public record Entry(long gameTime, long dayTime, int kind, String what, int count, BlockPos pos) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("g", gameTime);
            t.putLong("d", dayTime);
            t.putInt("k", kind);
            t.putString("w", what);
            t.putInt("c", count);
            t.putLong("p", pos.asLong());
            return t;
        }

        static Entry load(CompoundTag t) {
            return new Entry(t.getLong("g"), t.getLong("d"), t.getInt("k"), t.getString("w"), Math.max(1, t.getInt("c")), BlockPos.of(t.getLong("p")));
        }

        /** 画面に出す文（日と時刻を除く）。 */
        public Component describe() {
            Component name = EntityType.byString(what).map(EntityType::getDescription).orElse(Component.literal(what));
            return switch (kind) {
                case KIND_KILL -> Component.translatable("gui.singulo.android.log.kill", name, count);
                case KIND_ALARM -> Component.translatable("gui.singulo.android.log.alarm", name, count);
                default -> Component.translatable("gui.singulo.android.log.boss", name);
            };
        }

        /** 日と時刻（何日目・何時何分）。 */
        public Component when() {
            long day = dayTime / 24000 + 1;
            int minutes = (int) ((dayTime % 24000 + 6000) % 24000 * 60 / 1000);
            return Component.translatable("gui.singulo.android.log.when", day, String.format("%02d:%02d", minutes / 60, minutes % 60));
        }
    }

    private final RuinAndroid android;
    /** ステーションから受け取った巡回路と設定（ステーションが読み込まれていなくても続けられるよう、覚えておく）。 */
    private final List<BlockPos> route = new ArrayList<>();
    private boolean backAndForth, nightOnly;
    private int dwell = 100;
    /** 今向かっている地点の番号（-1 はまだ決めていない）と、往復の向き、立ち止まっている残り、向かってからの時間。 */
    private int index = -1, dir = 1, pause, travel;
    private long alarmAt = -ALARM_COOLDOWN;
    private final List<Entry> log = new ArrayList<>();
    /** 持ち主にまだ知らせていない記録の数。 */
    private int unreported;

    AndroidPatrol(RuinAndroid android) {
        this.android = android;
    }

    /** ステーションの設定を受け取る（巡回路が変わったら、一番近い地点から回り直す）。 */
    void configure(List<BlockPos> route, boolean backAndForth, boolean nightOnly, int dwellTicks) {
        if (!this.route.equals(route)) {
            this.route.clear();
            this.route.addAll(route);
            index = -1;
            pause = 0;
        }
        this.backAndForth = backAndForth;
        this.nightOnly = nightOnly;
        this.dwell = Math.max(0, dwellTicks);
    }

    /** 今、巡回しているか（巡回路があり、夜だけの設定なら夜）。 */
    boolean active(ServerLevel level) {
        return !route.isEmpty() && (!nightOnly || level.isNight());
    }

    /** 巡回の動き（毎 tick。戦っていないとき）。巡回していなければ false（ステーションのそばで待つ）。 */
    boolean tick(ServerLevel level) {
        if (!active(level)) {
            return false;
        }
        if (index < 0 || index >= route.size()) {
            index = startIndex();
            dir = 1;
        }
        BlockPos p = route.get(index);
        Vec3 stand = Vec3.atBottomCenterOf(p.above());
        if (pause > 0) {
            // 立ち止まって見回す
            android.getNavigation().stop();
            if (pause % 30 == 0) {
                double a = android.getRandom().nextDouble() * Math.PI * 2;
                android.getLookControl().setLookAt(android.getX() + Math.cos(a) * 6, android.getEyeY(), android.getZ() + Math.sin(a) * 6);
            }
            if (--pause == 0) {
                advance();
            }
            return true;
        }
        Vec3 d = stand.subtract(android.position());
        if (d.horizontalDistance() < ARRIVE && Math.abs(d.y) < 3) {
            android.getNavigation().stop();
            pause = Math.max(1, dwell);
            travel = 0;
            return true;
        }
        if (++travel > STUCK_TICKS) {
            advance();                              // たどり着けない: 次の地点へ
            return true;
        }
        // 経路の決まり（ステーションの「経路」）に従って向かう。経由地点を通ったら、たどり着けない時間を数え直す
        if (android.moveVia(stand, 0.9, android.patrolRules(), android.tickCount % 20 == 0)) {
            travel = 0;
        }
        return true;
    }

    /** 最初に向かう地点: 一番近い地点（同じ巡回路を2体で回るときは、片方は反対側から）。 */
    private int startIndex() {
        int best = 0;
        for (int i = 1; i < route.size(); i++) {
            if (route.get(i).distSqr(android.blockPosition()) < route.get(best).distSqr(android.blockPosition())) {
                best = i;
            }
        }
        return route.size() >= 2 && (android.getUUID().hashCode() & 1) == 1 ? (best + route.size() / 2) % route.size() : best;
    }

    private void advance() {
        travel = 0;
        if (route.size() <= 1) {
            return;
        }
        if (backAndForth) {
            if (index + dir < 0 || index + dir >= route.size()) {
                dir = -dir;
            }
            index += dir;
        } else {
            index = (index + 1) % route.size();
        }
    }

    /** 戦いを始めた: 同じステーションの手の空いた護衛を呼ぶ。 */
    void engage(ServerLevel level, LivingEntity target) {
        BlockPos home = android.home();
        if (home == null) {
            return;
        }
        boolean called = false;
        for (RuinAndroid a : level.getEntitiesOfClass(RuinAndroid.class, android.getBoundingBox().inflate(CALL_RANGE),
                a -> a != android && a.isAlive() && a.isGuard() && home.equals(a.home()) && a.printed() && a.active()
                        && a.getTarget() == null && !a.isWorking() && a.state() == RuinAndroid.STATE_IDLE && a.mode() != RuinAndroid.MODE_FOLLOW)) {
            a.assist(target);
            called = true;
        }
        if (called) {
            android.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8F, 1.6F);
        }
    }

    /** 毎秒（巡回中）: 敵の群れ・ボスを見つけたら、警報を出す。 */
    void scan(ServerLevel level) {
        if (level.getGameTime() - alarmAt < ALARM_COOLDOWN) {
            return;
        }
        LivingEntity boss = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, android.getBoundingBox().inflate(BOSS_RANGE),
                e -> e.isAlive() && RuinAndroid.isBoss(e))) {
            boss = e;
        }
        if (boss != null) {
            alarm(level, KIND_BOSS, boss.getType(), 1, boss.blockPosition());
            return;
        }
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, android.getBoundingBox().inflate(ALARM_RANGE),
                e -> e.isAlive() && android.hostile(e));
        if (enemies.size() >= ALARM_COUNT) {
            // 一番多い種類の名前で知らせる
            java.util.Map<EntityType<?>, Integer> counts = new java.util.HashMap<>();
            for (LivingEntity e : enemies) {
                counts.merge(e.getType(), 1, Integer::sum);
            }
            EntityType<?> most = java.util.Collections.max(counts.entrySet(), java.util.Map.Entry.comparingByValue()).getKey();
            alarm(level, KIND_ALARM, most, enemies.size(), android.blockPosition());
        }
    }

    /** 警報: ステーションからレッドストーン信号を出し、持ち主に知らせて、記録に残す。 */
    private void alarm(ServerLevel level, int kind, EntityType<?> what, int count, BlockPos at) {
        alarmAt = level.getGameTime();
        BlockPos home = android.home();
        if (home != null && level.isLoaded(home) && level.getBlockEntity(home) instanceof AndroidStationBlockEntity station) {
            station.alarm();
        }
        Component name = what.getDescription();
        Component text = kind == KIND_BOSS
                ? Component.translatable("message.singulo.android.alarm.boss", android.nickname(), name, at.getX(), at.getY(), at.getZ())
                : Component.translatable("message.singulo.android.alarm", android.nickname(), name, count, at.getX(), at.getY(), at.getZ());
        if (android.owner() != null && level.getServer().getPlayerList().getPlayer(android.owner()) instanceof ServerPlayer p) {
            p.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
            p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.0F, 0.7F);
        }
        android.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.7F);
        add(level, new Entry(level.getGameTime(), level.getDayTime(), kind, BuiltInRegistries.ENTITY_TYPE.getKey(what).toString(), count, at));
    }

    /** 倒した: 記録に残す（少し前に近くで同じ相手を倒していれば、まとめる）。 */
    void onKill(ServerLevel level, LivingEntity killed) {
        String what = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()).toString();
        BlockPos at = killed.blockPosition();
        if (!log.isEmpty()) {
            Entry last = log.get(0);
            if (unreported > 0 && last.kind == KIND_KILL && last.what.equals(what) && level.getGameTime() - last.gameTime < MERGE_TICKS
                    && last.pos.closerThan(at, MERGE_RANGE)) {
                log.set(0, new Entry(level.getGameTime(), level.getDayTime(), KIND_KILL, what, last.count + 1, last.pos));
                android.syncPatrolLog();
                return;
            }
        }
        add(level, new Entry(level.getGameTime(), level.getDayTime(), KIND_KILL, what, 1, at));
    }

    private void add(ServerLevel level, Entry e) {
        log.add(0, e);
        while (log.size() > LOG_SIZE) {
            log.remove(log.size() - 1);
        }
        unreported = Math.min(log.size(), unreported + 1);
        android.syncPatrolLog();
    }

    /**
     * 久しぶりにそばに来た持ち主への一言（留守のあいだに倒した数・警報の回数）。知らせることがなければ null。知らせたら、知らせ済みにする。
     */
    @Nullable
    Component report() {
        if (unreported <= 0) {
            return null;
        }
        int kills = 0, alarms = 0, n = 0;
        for (Entry e : log) {
            if (n >= unreported) {
                break;
            }
            n++;
            if (e.kind == KIND_KILL) {
                kills += e.count;
            } else {
                alarms++;
            }
        }
        unreported = 0;
        return alarms > 0
                ? Component.translatable("message.singulo.android.patrol_report.alarm", android.nickname(), kills, alarms)
                : Component.translatable("message.singulo.android.patrol_report", android.nickname(), kills);
    }

    /** 記録（クライアントへ送る形）。 */
    CompoundTag logTag() {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry e : log) {
            list.add(e.save());
        }
        t.put("log", list);
        return t;
    }

    /** 送られてきた記録を読む（クライアント）。 */
    public static List<Entry> readLog(CompoundTag t) {
        List<Entry> out = new ArrayList<>();
        for (Tag e : t.getList("log", Tag.TAG_COMPOUND)) {
            out.add(Entry.load((CompoundTag) e));
        }
        return out;
    }

    CompoundTag save() {
        CompoundTag t = logTag();
        ListTag r = new ListTag();
        for (BlockPos p : route) {
            r.add(NbtUtils.writeBlockPos(p));
        }
        t.put("route", r);
        t.putBoolean("back_and_forth", backAndForth);
        t.putBoolean("night_only", nightOnly);
        t.putInt("dwell", dwell);
        t.putInt("index", index);
        t.putInt("dir", dir);
        t.putInt("unreported", unreported);
        return t;
    }

    void load(CompoundTag t) {
        route.clear();
        for (Tag p : t.getList("route", Tag.TAG_INT_ARRAY)) {
            route.add(NbtUtils.readBlockPos(wrap(p), "p").orElse(BlockPos.ZERO));
        }
        backAndForth = t.getBoolean("back_and_forth");
        nightOnly = t.getBoolean("night_only");
        dwell = t.contains("dwell") ? t.getInt("dwell") : 100;
        index = t.contains("index") ? t.getInt("index") : -1;
        dir = t.getInt("dir") == -1 ? -1 : 1;
        unreported = t.getInt("unreported");
        log.clear();
        log.addAll(readLog(t));
    }

    private static CompoundTag wrap(Tag p) {
        CompoundTag c = new CompoundTag();
        c.put("p", p);
        return c;
    }
}
