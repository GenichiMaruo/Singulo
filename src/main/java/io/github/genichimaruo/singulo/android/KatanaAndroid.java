package io.github.genichimaruo.singulo.android;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 刀型アンドロイド「ルージュ」。黒髪に紅のインナーカラーと、紅の光。物静かで、抜けば速い。
 * 覚醒した残響の番人がときどき残す設計メモリから印刷できる隠し型。
 *
 * <ul>
 * <li>刀は2本。ふだん使いの刀（打刀）を左の腰に差し、紅一閃のための紅い刀を背中に背負う（柄は左肩の上）。</li>
 * <li>敵を見つけると、右手で腰の刀を抜く。片付けて少したつと腰の鞘へ納める。</li>
 * <li>袈裟斬りと、続けての返し斬り。間合いの外からは、踏み込み斬りで一気に詰める。</li>
 * <li>大技「紅一閃」: 右手の刀を低く後ろへ流し、左手で背中の紅い刀の柄を握って居合の構えを取ると、体の継ぎ目に紅の光の筋が
 *     胸から手足へ浮き出ていく。溜め切ると、背中の刀を抜きざまに二刀を交差させて相手を突き抜けて駆け抜け（通り道の相手をまとめて斬る）、
 *     背を向けたまま二刀で血振りをすると、斬った相手に遅れて深い斬撃が走る。最後の敵を倒したときは、紅刀を掲げてから背中の鞘へ納める。</li>
 * </ul>
 */
public class KatanaAndroid extends RuinAndroid {
    public static final int ACCENT = 0xFF3A54;

    /** 抜刀・納刀（右手で、左の腰の鞘から）。 */
    public static final int STATE_DRAW = 70;
    public static final int STATE_SHEATHE = 71;
    /** 袈裟斬り（右上から左下へ）と、続けての返し斬り（左下から右上へ）。 */
    public static final int STATE_CUT = 72;
    public static final int STATE_RETURN_CUT = 73;
    /** 踏み込み斬り（間合いの外から一気に詰める）。 */
    public static final int STATE_STEP = 74;
    /** 紅一閃（二刀）: 居合の構え・駆け抜け・血振りと遅延斬撃。 */
    public static final int STATE_IAI_STANCE = 75;
    public static final int STATE_IAI_FLASH = 76;
    public static final int STATE_IAI_ZANSHIN = 77;

    /** 最後の敵を倒したあとの刀礼。右の打刀を下げ、左手で背中の紅刀を半分抜いて礼をする。 */
    public static final int STATE_VICTORY = 78;
    public static final int VICTORY_GRIP = 6, VICTORY_RAISE = 13, VICTORY_RETURN = 23, VICTORY_SEAT = 32, VICTORY = 40;
    /** 紅一閃で最後の敵を倒した。抜いたままの紅刀で礼を取り、切っ先を鯉口に合わせて納める。 */
    public static final int STATE_IAI_VICTORY = 79;
    public static final int IAI_VICTORY_RETURN = 24, IAI_VICTORY_GUIDE = 30, IAI_VICTORY_SEAT = 39, IAI_VICTORY = 46;
    /** 待機中に背中の箱から布を取り、腰の打刀を拭いて両方を元に戻す。 */
    public static final int STATE_MAINTENANCE = 80;
    private GunnerAndroid inspectionCiel;
    private GunnerAndroid crossCiel;

    private java.util.List<GunnerAndroid> nearbyGunners(ServerLevel level, int range) {
        if (!isGuard() || owner() == null) return java.util.List.of();
        var allies = level.getEntitiesOfClass(GunnerAndroid.class, getBoundingBox().inflate(range),
                gun -> gun.isGuard() && owner().equals(gun.owner()) && distanceToSqr(gun) <= range * range && hasLineOfSight(gun));
        allies.sort(java.util.Comparator.comparingDouble(gun -> distanceToSqr(gun)));
        return allies;
    }
    public static final int MAINTENANCE_CLOTH = 12, MAINTENANCE_DRAW = 18, MAINTENANCE_CLEAR = 26, MAINTENANCE_WIPE = 34;
    public static final int MAINTENANCE_RETURN = 62, MAINTENANCE_GUIDE = 68, MAINTENANCE_SEAT = 76, MAINTENANCE_STOW = 86, MAINTENANCE = 96;

    public static final int DRAW = 10;
    /** 抜刀で、刀が鞘から抜けきる tick（ここから手に持って描く）。 */
    public static final int DRAW_CLEAR = 5;
    public static final int SHEATHE = 12;
    /** 納刀で、刀が鞘に収まる tick（ここから鞘に描く）。 */
    public static final int SHEATHE_SEAT = 8;
    public static final int CUT = 8;
    public static final int RETURN_CUT = 8;
    public static final int STEP = 11;
    /** 構えの長さ: 体に光が満ちていく。最後の IAI_LOCK tick は向きを変えない（避ける間）。 */
    public static final int IAI_STANCE = 34;
    public static final int IAI_LOCK = 8;
    /** 駆け抜けの tick（IAI_DASH tick で通り抜け、残りは踏みとどまる）。 */
    public static final int IAI_FLASH = 6;
    public static final int IAI_DASH = 3;
    /** 残心: 紅刀を手にしたまま斬撃を解放し、その結果を見てから納刀か勝利の礼へ移る。 */
    public static final int IAI_ZANSHIN = 36;
    public static final int IAI_RELEASE = 24, IAI_SHEATHE_GUIDE = 27, IAI_SHEATHE_SEAT = 33;

    /** 駆け抜ける距離の上限（相手の少し先まで。壁の手前で止まる）と、通り道の当たりの太さ（半径）。 */
    public static final double IAI_RANGE = 16;
    private static final double IAI_START_RANGE = 14;
    public static final double IAI_RADIUS = 0.9;
    /** 相手の向こうへ抜ける距離。 */
    static final double IAI_OVERRUN = 2.2;
    public static final double CUT_REACH = 3.3;
    public static final double STEP_REACH = 3.2;
    private static final double STEP_START_RANGE = 10;
    private static final double STEP_STOP_DISTANCE = 2;
    private static final int STEP_TRAVEL_TICKS = 3;

    static final float CUT_DAMAGE = 6;
    static final float RETURN_DAMAGE = 5;
    static final float STEP_DAMAGE = 7;
    /** 駆け抜けたときの浅い傷と、残心で解放する深い斬撃。 */
    static final float IAI_PASS_DAMAGE = 4;
    static final float IAI_DAMAGE = 16;

    static final int COST_CUT = 60;
    static final int COST_STEP = 150;
    static final int COST_IAI = 360;

    /** 抜いたまま敵がいなくなってから、この tick たつと納める。 */
    static final int SHEATHE_DELAY = 50;

    /** 腰の刀を右手に抜いているか（クライアントでも見える。描画で、刀を手に持つか腰の鞘に納めるかを決める）。 */
    private static final EntityDataAccessor<Boolean> DRAWN = SynchedEntityData.defineId(KatanaAndroid.class, EntityDataSerializers.BOOLEAN);
    /** 遅延斬撃の結果、通常の残心で納刀することが確定した。受信するまでクライアントは紅刀を手に保つ。 */
    private static final EntityDataAccessor<Boolean> IAI_RETURNING = SynchedEntityData.defineId(KatanaAndroid.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> MAINTENANCE_START = SynchedEntityData.defineId(KatanaAndroid.class, EntityDataSerializers.LONG);
    /** 紅一閃の通り道（駆け抜け始めの点と、抜けた先の点）。位置は、整数の基準点からの差で送る。 */
    private static final EntityDataAccessor<BlockPos> FX_ORIGIN = SynchedEntityData.defineId(KatanaAndroid.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> PATH_FROM = SynchedEntityData.defineId(KatanaAndroid.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> PATH_TO = SynchedEntityData.defineId(KatanaAndroid.class, EntityDataSerializers.VECTOR3);

    /** 駆け抜けで斬った相手（残心で遅れて斬撃が走る）。 */
    private final List<Integer> marked = new ArrayList<>();
    /** 抜いたまま敵がいない tick。 */
    private int calm;
    private int nextMaintenance = 20 * (12 + random.nextInt(13));
    private boolean victoryPending;
    private boolean iaiKilledOpponent;
    private int clientIaiReturnStart;
    private int clientLastState;
    private int clientReturnState;
    private int clientReturnStart;
    private float clientReturnFrom;
    private int clientMaintenanceExitStart;
    private float clientMaintenanceExitTime = -1;

    public KatanaAndroid(EntityType<? extends KatanaAndroid> type, Level level) {
        super(type, level);
    }

    /** 射撃型より少し頑丈で、足が速い。 */
    public static AttributeSupplier.Builder createAttributes() {
        return RuinAndroid.createAttributes()
                .add(Attributes.MAX_HEALTH, 34)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    /** 珍しい型の強み: 作業の速さ（作る・掘る・収穫・家畜の世話と、作業中に歩く速さの倍率）。 */
    public static final float WORK_PACE = 1.5F;
    private static final java.util.UUID WORK_SPEED_ID = java.util.UUID.nameUUIDFromBytes("singulo:katana_work_speed".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Override
    public int workTicks(int ticks) {
        return Math.max(1, Math.round(ticks / WORK_PACE));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DRAWN, false);
        entityData.define(IAI_RETURNING, false);
        entityData.define(MAINTENANCE_START, -1L);
        entityData.define(FX_ORIGIN, BlockPos.ZERO);
        entityData.define(PATH_FROM, new Vector3f());
        entityData.define(PATH_TO, new Vector3f());
    }

    @Override
    public AndroidKind kind() {
        return AndroidKind.KATANA;
    }

    /** 珍しい型の強み: バックパックを内蔵している（運べる欄が最初から多い）。 */
    @Override
    public boolean builtInPack() {
        return true;
    }

    /** 珍しい型の強み: バッテリーを1つ内蔵している（充電の満タンが1.5倍）。 */
    @Override
    public int builtInBatteries() {
        return 1;
    }

    @Override
    public int accent() {
        return ACCENT;
    }

    @Override
    protected void registerCombat() {
        goalSelector.addGoal(2, new CombatGoal());
    }

    /** 紅一閃の通り道と、斬撃の光が届く範囲。 */
    @Override
    protected double moveReach() {
        int s = state();
        if (s == STATE_IAI_VICTORY) {
            return 1.25; // 頭上に掲げた紅刀の切っ先
        }
        return s == STATE_IAI_FLASH || s == STATE_IAI_ZANSHIN ? IAI_RANGE + 2 : combat(s) ? STEP_REACH + 1 : 0;
    }

    static boolean combat(int s) {
        return s >= STATE_DRAW && s <= STATE_IAI_ZANSHIN;
    }

    @Override
    boolean canHit(LivingEntity target) {
        if (!super.canHit(target)) return false;
        return switch (state()) {
            case STATE_CUT, STATE_RETURN_CUT, STATE_STEP -> hasLineOfSight(target);
            default -> true;
        };
    }

    /** 腰の刀を右手に持って描くか（抜刀の途中で鞘から抜けたあと〜納刀で鞘に収まる前。紅一閃の間はずっと右手に）。 */
    public boolean bladeInHand(float partialTick) {
        int s = state();
        float t = stateTime(partialTick);
        return switch (s) {
            case STATE_DRAW -> t >= DRAW_CLEAR;
            case STATE_SHEATHE -> t < SHEATHE_SEAT;
            case STATE_MAINTENANCE -> t >= MAINTENANCE_CLEAR && t < MAINTENANCE_SEAT;
            case STATE_IAI_STANCE, STATE_IAI_FLASH, STATE_IAI_ZANSHIN -> true;
            default -> entityData.get(DRAWN) && !weaponStowed();
        };
    }

    /** 紅一閃で抜いた刀は、通常の残心または勝利の礼で鍔が着くまで左手にある。 */
    public boolean greatBladeInHand(float partialTick) {
        float t = level().isClientSide ? stateTime(partialTick) : stateTicks + partialTick;
        return switch (state()) {
            case STATE_IAI_FLASH -> true;
            case STATE_IAI_ZANSHIN -> t < IAI_SHEATHE_SEAT;
            case STATE_IAI_VICTORY -> t < IAI_VICTORY_SEAT;
            default -> false;
        };
    }

    public boolean drawn() {
        return entityData.get(DRAWN);
    }

    /** 紅刀と左手の時計。礼の途中で接敵したら、右の打刀で戦いながら残りの納刀を4 tickで終える。 */
    public float victoryTime(float partialTick) {
        return saluteTime(STATE_VICTORY, VICTORY, 4, partialTick);
    }

    /** 紅一閃の礼。途中で接敵したときは、抜き切った紅刀を8 tickで背中に戻す。 */
    public float iaiVictoryTime(float partialTick) {
        return saluteTime(STATE_IAI_VICTORY, IAI_VICTORY, 8, partialTick);
    }

    public float maintenanceTime(float partialTick) {
        return state() == STATE_MAINTENANCE && isAlive() ? stateTime(partialTick) : -1;
    }

    /** 中断直前の姿勢から左手の布を箱に戻す。右手は同じ時に戦闘へ復帰できる。 */
    public float maintenanceExitTime(float partialTick) {
        return maintenanceExitProgress(partialTick) >= 0 ? clientMaintenanceExitTime : -1;
    }

    public float maintenanceExitProgress(float partialTick) {
        int s = state();
        float elapsed = tickCount - clientMaintenanceExitStart + partialTick;
        if (clientMaintenanceExitTime < 0 || elapsed >= 5 || !active() || !isAlive() || isWorking()
                || !(s == STATE_IDLE || s == STATE_DRAW || s == STATE_CUT || s == STATE_RETURN_CUT || s == STATE_STEP)) return -1;
        return elapsed / 5;
    }

    private float saluteTime(int motion, int duration, int returnTicks, float partialTick) {
        int s = state();
        if (s == motion) {
            return isAlive() ? stateTime(partialTick) : -1;
        }
        float elapsed = tickCount - clientReturnStart + partialTick;
        if (clientReturnState != motion || clientReturnFrom >= duration || elapsed >= returnTicks || !isAlive() || !drawn() || isWorking()
                || !(s == STATE_IDLE || s == STATE_CUT || s == STATE_RETURN_CUT || s == STATE_STEP)) {
            return -1;
        }
        return Mth.lerp(elapsed / returnTicks, clientReturnFrom, duration);
    }

    @Override
    public float stateTime(float partialTick) {
        float time = super.stateTime(partialTick);
        if (state() == STATE_MAINTENANCE) {
            if (!level().isClientSide) return stateTicks + partialTick;
            return clientMaintenanceClock(partialTick);
        }
        if (level().isClientSide && state() == STATE_IAI_ZANSHIN) {
            // 勝敗が届く前に遅延斬撃の光（t >= IAI_RELEASE）を出したり、刀を戻したりしない。
            float permitted = entityData.get(IAI_RETURNING)
                    ? IAI_RELEASE + (tickCount - clientIaiReturnStart) + partialTick
                    : Math.nextDown((float) IAI_RELEASE);
            return Math.min(time, permitted);
        }
        return time;
    }

    private float clientMaintenanceClock(float partialTick) {
        // 途中から見始めても、刀と布は現在の手順から表示する。
        long start = entityData.get(MAINTENANCE_START);
        return start < 0 ? super.stateTime(partialTick) : Mth.clamp(level().getGameTime() - start + partialTick, 0, MAINTENANCE);
    }

    protected void setIaiReturning(boolean returning) {
        entityData.set(IAI_RETURNING, returning);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        // Read the old clock before RuinAndroid resets it for the incoming state.
        if (level().isClientSide && IAI_RETURNING.equals(key) && entityData.get(IAI_RETURNING)) {
            clientIaiReturnStart = tickCount;
        }
        int s = state();
        if (level().isClientSide && s != clientLastState) {
            if (clientLastState == STATE_MAINTENANCE) {
                float t = clientMaintenanceClock(0);
                clientMaintenanceExitTime = t < MAINTENANCE_STOW ? t : -1;
                clientMaintenanceExitStart = tickCount;
            }
            if (saluting(clientLastState)) {
                float t = stateTime(0);
                if (clientLastState == STATE_IAI_VICTORY) {
                    clientReturnFrom = t;
                } else if (t < VICTORY_GRIP) {
                    clientReturnFrom = Mth.lerp(t / VICTORY_GRIP, VICTORY, VICTORY_SEAT);
                } else if (t < VICTORY_RAISE) {
                    clientReturnFrom = Mth.lerp((t - VICTORY_GRIP) / (VICTORY_RAISE - VICTORY_GRIP), VICTORY_SEAT, VICTORY_RETURN);
                } else {
                    clientReturnFrom = Mth.clamp(t, VICTORY_RETURN, VICTORY);
                }
                clientReturnState = clientLastState;
                clientReturnStart = tickCount;
            }
            clientLastState = s;
        }
        super.onSyncedDataUpdated(key);
    }

    private void setDrawn(boolean drawn) {
        entityData.set(DRAWN, drawn);
    }

    /**
     * 体に浮き出る紅の光の筋の強さ（0〜1）: 居合の構えで背中の柄を握ってから満ちていき、駆け抜けの間は最大、
     * 遅延斬撃を解放すると引いていく。
     */
    public float surge(float partialTick) {
        float t = stateTime(partialTick);
        return switch (state()) {
            case STATE_IAI_STANCE -> Mth.clamp((t - 3) / (IAI_STANCE - 3), 0, 1);
            case STATE_IAI_FLASH -> 1;
            case STATE_IAI_ZANSHIN -> 1 - Mth.clamp((t - IAI_RELEASE) / 12, 0, 1);
            case STATE_IAI_VICTORY -> 1 - Mth.clamp(t / 12, 0, 1);
            default -> 0;
        };
    }

    private Vec3 fxOrigin() {
        return Vec3.atLowerCornerOf(entityData.get(FX_ORIGIN));
    }

    /** 紅一閃の通り道の始めの点。 */
    public Vec3 pathFrom() {
        return fxOrigin().add(new Vec3(entityData.get(PATH_FROM)));
    }

    /** 紅一閃の通り道の終わりの点（抜けた先）。 */
    public Vec3 pathTo() {
        return fxOrigin().add(new Vec3(entityData.get(PATH_TO)));
    }

    private void setPath(Vec3 from, Vec3 to) {
        BlockPos o = BlockPos.containing(from);
        entityData.set(FX_ORIGIN, o);
        Vec3 base = Vec3.atLowerCornerOf(o);
        entityData.set(PATH_FROM, from.subtract(base).toVector3f());
        entityData.set(PATH_TO, to.subtract(base).toVector3f());
    }

    /**
     * 駆け抜ける先を決める: 今の向きのまま、相手の少し先まで（最大 IAI_RANGE）。足元と胸の高さで壁に当たれば、その手前で止まる。
     * 決めた通り道を返す（始めの点・終わりの点は足元の高さ）。
     */
    public Vec3 planDash(ServerLevel level, LivingEntity target) {
        Vec3 from = position();
        Vec3 dir = Vec3.directionFromRotation(0, yBodyRot);
        double want = Math.min(IAI_RANGE, Math.max(3, from.multiply(1, 0, 1).distanceTo(target.position().multiply(1, 0, 1)) + IAI_OVERRUN));
        double length = want;
        for (double h : new double[]{0.3, 1.2}) {
            Vec3 a = from.add(0, h, 0);
            HitResult wall = level.clip(new ClipContext(a, a.add(dir.scale(want + 0.4)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (wall.getType() != HitResult.Type.MISS) {
                length = Math.min(length, Math.max(0, wall.getLocation().distanceTo(a) - 0.5));
            }
        }
        Vec3 to = from.add(dir.scale(length));
        setPath(from, to);
        return to;
    }

    /**
     * 通り道（from から to）に触れた相手を斬る（浅い傷）。斬った相手は覚えておき、残心で深い斬撃を受ける。斬った数を返す。
     */
    public int cutAlong(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 a = from.add(0, 0.9, 0), b = to.add(0, 0.9, 0);
        int hits = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(IAI_RADIUS + 0.6), this::canHit)) {
            AABB box = e.getBoundingBox().inflate(IAI_RADIUS);
            if ((box.contains(a) || box.clip(a, b).isPresent()) && !marked.contains(e.getId())) {
                marked.add(e.getId());
                e.invulnerableTime = 0;
                e.hurt(damageSources().mobAttack(this), IAI_PASS_DAMAGE);
                hits++;
            }
        }
        return hits;
    }

    /** 駆け抜けで斬った相手に、遅れて深い斬撃が走る。斬撃が入った数を返す。 */
    public int releaseCuts(ServerLevel level) {
        int hits = 0;
        for (int id : marked) {
            if (level.getEntity(id) instanceof LivingEntity e && e.isAlive() && e.distanceToSqr(this) < 24 * 24) {
                e.invulnerableTime = 0;
                if (e.hurt(damageSources().mobAttack(this), IAI_DAMAGE)) {
                    hits++;
                }
                Vec3 c = e.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0);
                level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.35, 0.5, 0.35, 0.4);
                sparks(level, c, 16, 0.45);
            }
        }
        marked.clear();
        return hits;
    }

    /** 覚えている、斬った相手の数（テスト用）。 */
    public int markedCount() {
        return marked.size();
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity killed) {
        int s = state();
        if (killed != this && drawn()) {
            if (s == STATE_IAI_FLASH || s == STATE_IAI_ZANSHIN) {
                iaiKilledOpponent = true;
            } else if (s == STATE_CUT || s == STATE_RETURN_CUT || s == STATE_STEP) {
                victoryPending = true;
            }
        }
        return super.killedEntity(level, killed);
    }

    private static boolean saluting(int state) {
        return state == STATE_VICTORY || state == STATE_IAI_VICTORY;
    }

    /** The target selector can take a few ticks to acquire another foe after a kill. */
    private boolean anotherOpponent(ServerLevel level) {
        double range = getAttributeValue(Attributes.FOLLOW_RANGE);
        TargetingConditions targets = TargetingConditions.forCombat().range(range)
                .selector(e -> isGuard() ? guardTargets(e) : e instanceof Player);
        return level.getNearestEntity(LivingEntity.class, targets, this, getX(), getY(), getZ(),
                getBoundingBox().inflate(range, 4, range)) != null;
    }

    private void tickMaintenance(ServerLevel level, boolean threat) {
        int s = state();
        if (inspectionCiel != null && (s != STATE_MAINTENANCE || !inspectionCiel.idleForInspection()
                || inspectionCiel.gesture() != GunnerAndroid.GESTURE_INSPECT || distanceToSqr(inspectionCiel) > 4 * 4)) {
            endInspection(false);
        }
        boolean free = (s == STATE_IDLE || s == STATE_MAINTENANCE) && !threat && !drawn() && isAlive() && !isNoAi()
                && active() && !isWorking() && !napping() && (gesture() == GESTURE_NONE || inspectionCiel != null) && !life.busy()
                && onGround() && !isPassenger() && !isInWater() && !isInLava() && hurtTime == 0
                && getNavigation().isDone() && position().distanceToSqr(xo, yo, zo) < 0.0001;
        if (!free) {
            nextMaintenance = Math.max(nextMaintenance, tickCount + 40);
            if (s == STATE_MAINTENANCE) endMaintenance(threat);
        } else if (s == STATE_MAINTENANCE) {
            if (stateTicks == MAINTENANCE_DRAW) sound("katana_draw", 0.25F, 0.9F);
            if (stateTicks == MAINTENANCE_SEAT - 8) sound("katana_sheathe", 0.25F, 1);
            if (stateTicks >= MAINTENANCE) endMaintenance(false);
        } else if (tickCount >= nextMaintenance) {
            nextMaintenance = tickCount + MAINTENANCE + 20 * (35 + random.nextInt(26));
            entityData.set(MAINTENANCE_START, level.getGameTime());
            setState(STATE_MAINTENANCE);
            if (home() != null && home().closerToCenterThan(position(), 12)) {
                for (var gun : nearbyGunners(level, 4)) {
                    if (gun.gesture() != GESTURE_NONE || !gun.idleForInspection()) continue;
                    inspectionCiel = gun;
                    gesture(GunnerAndroid.GESTURE_INSPECT, null, MAINTENANCE + 3);
                    gun.gesture(GunnerAndroid.GESTURE_INSPECT, this, MAINTENANCE + 3);
                    break;
                }
            }
        }
    }

    private void endMaintenance(boolean threat) {
        endInspection(!threat && stateTicks >= MAINTENANCE);
        // 抜き切った打刀で迎撃する場合だけ、手入れ中の刀をそのまま戦闘用に引き継ぐ。
        if (threat && active() && !isWorking() && stateTicks >= MAINTENANCE_CLEAR && stateTicks < MAINTENANCE_GUIDE) {
            setDrawn(true);
        }
        setState(STATE_IDLE);
    }

    private void endInspection(boolean completed) {
        if (inspectionCiel == null) return;
        var gun = inspectionCiel;
        inspectionCiel = null;
        if (gun.gesture() == GunnerAndroid.GESTURE_INSPECT) gun.gesture(completed ? GESTURE_NOD : GESTURE_NONE, completed ? this : null);
        if (gesture() == GunnerAndroid.GESTURE_INSPECT) gesture(completed ? GESTURE_NOD : GESTURE_NONE, completed ? gun : null);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            int s = state();
            LivingEntity foe = getTarget();
            if (s != STATE_IAI_STANCE) crossCiel = null;
            boolean threat = foe != null && foe.isAlive();
            if (victoryPending) {
                if (threat || !isAlive() || !drawn() || !active() || isWorking() || (s != STATE_IDLE && !combat(s))) {
                    victoryPending = false;
                } else if (s == STATE_IDLE) {
                    victoryPending = false;
                    if (!anotherOpponent(level)) {
                        getNavigation().stop();
                        setState(STATE_VICTORY);
                        s = STATE_VICTORY;
                    }
                }
            }
            if (saluting(s)) {
                calm = 0;
                int duration = s == STATE_IAI_VICTORY ? IAI_VICTORY : VICTORY;
                int seat = s == STATE_IAI_VICTORY ? IAI_VICTORY_SEAT : VICTORY_SEAT;
                if (threat || !isAlive() || !active() || isWorking() || stateTicks >= duration) {
                    setState(STATE_IDLE);
                } else if (stateTicks == seat - 8) {
                    sound("katana_sheathe", 0.4F, 1.0F); // 音源の鍔鳴りは8 tick後
                }
            } else if (drawn() && !threat && !combat(s)) {
                if (s != STATE_IDLE || !active()) {
                    setDrawn(false);                     // 作業・充電に入った: 黙って納めておく（描画は鞘に）
                    calm = 0;
                } else if (++calm >= SHEATHE_DELAY && gesture() == GESTURE_NONE) {
                    setState(STATE_SHEATHE);
                    sound("katana_sheathe", 0.6F, 1.0F);
                    calm = 0;
                }
            } else {
                calm = 0;
            }
            if (s == STATE_SHEATHE) {
                if (stateTicks == SHEATHE_SEAT) {
                    setDrawn(false);
                }
                if (stateTicks >= SHEATHE) {
                    setState(STATE_IDLE);
                }
            }
            tickMaintenance(level, threat);
            // 作業中だけ、歩く速さを上げる
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            boolean working = isWorking();
            if (speed != null && working != (speed.getModifier(WORK_SPEED_ID) != null)) {
                if (working) {
                    speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(WORK_SPEED_ID, "singulo work speed", WORK_PACE - 1,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
                } else {
                    speed.removeModifier(WORK_SPEED_ID);
                }
            }
        }
    }

    /**
     * 描画するかを決める箱: 紅一閃の通り道の光が出ている間は、その両端まで広げる（本体が画面の外でも、画面に入っている光は描く）。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        int s = state();
        if (s == STATE_IAI_FLASH || s == STATE_IAI_ZANSHIN || s == STATE_IAI_VICTORY && stateTime(0) < 10) {
            box = box.minmax(new AABB(pathFrom(), pathTo()).inflate(1.5));
        }
        return box;
    }

    private final class CombatGoal extends Goal {
        private int nextMove = 20;
        private int nextIai = 90;
        private int cutCooldown;
        /** 袈裟斬りのあと、返し斬りへつなぐか。 */
        private boolean combo;
        private Vec3 dashTo = Vec3.ZERO;

        CombatGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return active() && (saluting(state()) || t != null && t.isAlive());
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || combat(state()) && state() != STATE_SHEATHE && active();
        }

        @Override
        public void stop() {
            int s = state();
            if (active() && (saluting(s) || combat(s) && s != STATE_SHEATHE)) {
                if ((s == STATE_IAI_ZANSHIN || s == STATE_IAI_FLASH) && level() instanceof ServerLevel level) {
                    // 駆け抜けの途中で止められた: 斬った相手の斬撃は、ここで走らせる（背中の刀は描画で鞘に戻る）
                    releaseCuts(level);
                }
                setState(STATE_IDLE);
            }
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level) || !active()) {
                return;
            }
            LivingEntity target = getTarget();
            int s = state();
            if (s == STATE_MAINTENANCE && target != null && target.isAlive()) {
                endMaintenance(true);
                s = STATE_IDLE;
            }
            if (saluting(s)) {
                getNavigation().stop();
                if (target == null || !target.isAlive()) {
                    return;
                }
                setState(STATE_IDLE);
                s = STATE_IDLE;
            }
            // 敵が消えても抜き切る。納刀中に次の敵が現れたら、手を離してから改めて抜く。
            if (s == STATE_DRAW) {
                if (target != null && target.isAlive()) {
                    getLookControl().setLookAt(target, 30, 30);
                }
                if (stateTicks == DRAW_CLEAR) {
                    setDrawn(true);
                }
                if (stateTicks >= DRAW) {
                    setState(STATE_IDLE);
                    nextMove = Math.min(nextMove, 6);
                }
                return;
            }
            if (s == STATE_SHEATHE) {
                return;
            }
            // 紅一閃は、相手がいなくなっても終わりまで（駆け抜けの途中で止まらない。斬った相手に斬撃が走る）
            if (s == STATE_IAI_FLASH || s == STATE_IAI_ZANSHIN) {
                iai(level, s);
                return;
            }
            if (target == null || !target.isAlive()) {
                if (combat(s) && stateTicks > 12) {
                    setState(STATE_IDLE);             // 構えの途中なら、背中の刀は抜かずに終える
                }
                return;
            }
            double dist = distanceTo(target);
            switch (s) {
                case STATE_CUT -> {
                    hold(target);
                    if (stateTicks == 3) {
                        sweep(CUT_REACH, 140, CUT_DAMAGE, 0.35);
                        sound("katana_swing", 0.9F, 1.0F);
                    }
                    if (stateTicks >= CUT) {
                        if (combo && distanceTo(target) < CUT_REACH + 0.3 && spend(COST_CUT)) {
                            combo = false;
                            setState(STATE_RETURN_CUT);
                        } else {
                            setState(STATE_IDLE);
                            cutCooldown = 14;
                        }
                    }
                }
                case STATE_RETURN_CUT -> {
                    hold(target);
                    if (stateTicks == 3) {
                        sweep(CUT_REACH, 150, RETURN_DAMAGE, 0.5);
                        sound("katana_swing", 0.9F, 1.15F);
                    }
                    if (stateTicks >= RETURN_CUT) {
                        setState(STATE_IDLE);
                        cutCooldown = 20;
                    }
                }
                case STATE_STEP -> {
                    getNavigation().stop();
                    if (stateTicks <= STEP_TRAVEL_TICKS) {
                        advanceDash(STEP_TRAVEL_TICKS - stateTicks + 1);
                    }
                    if (stateTicks == STEP_TRAVEL_TICKS + 1) {
                        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
                        sweep(STEP_REACH, 110, STEP_DAMAGE, 0.6);
                        Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
                        sparks(level, position().add(forward.scale(1.5)).add(0, 1.0, 0), 8, 0.3);
                    }
                    if (stateTicks >= STEP) {
                        finish(30 + random.nextInt(20));
                    }
                }
                case STATE_IAI_STANCE -> {
                    if ((stateTicks == 1 || stateTicks % 5 == 0) && stateTicks < IAI_STANCE - IAI_LOCK
                            && (crossCiel == null || !crossCiel.covering(KatanaAndroid.this))) {
                        crossCiel = null;
                        for (var gun : nearbyGunners(level, 24)) {
                            if (gun.prepareCross(level, KatanaAndroid.this, target)) {
                                crossCiel = gun;
                                break;
                            }
                        }
                    }
                    if (stateTicks < IAI_STANCE - IAI_LOCK) {
                        hold(target);
                    } else {
                        getNavigation().stop();         // 向きを固めた: もう相手を追わない（横へ避けられる）
                    }
                    if (stateTicks > 4 && stateTicks % 3 == 0) {
                        sparks(level, position().add(0, 0.2 + random.nextDouble() * 1.3, 0), 2, 0.35);
                    }
                    if (stateTicks >= IAI_STANCE) {
                        dashTo = planDash(level, target);
                        iaiKilledOpponent = false;
                        setIaiReturning(false);
                        setState(STATE_IAI_FLASH);
                        sound("katana_iai_flash", 1.2F, 1.0F);
                    }
                }
                default -> approach(level, target, dist);
            }
        }

        /** 駆け抜けと残心（相手がいなくても進める）。 */
        private void iai(ServerLevel level, int s) {
            getNavigation().stop();
            if (s == STATE_IAI_FLASH) {
                Vec3 from = pathFrom();
                if (stateTicks <= IAI_DASH) {
                    Vec3 v = advanceDash(IAI_DASH - stateTicks + 1);
                    double step = v.length();
                    Vec3 direction = v.normalize();
                    for (double d = 0; d < step; d += 1.2) {
                        Vec3 at = position().add(direction.scale(d)).add(0, 0.9, 0);
                        sparks(level, at, 2, 0.15);
                    }
                } else {
                    setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
                }
                if (stateTicks == IAI_DASH + 1) {
                    setPath(from, position());
                    cutAlong(level, from, position());
                }
                if (stateTicks >= IAI_FLASH) {
                    setState(STATE_IAI_ZANSHIN);
                }
                return;
            }
            // 残心
            if (stateTicks == 4) {
                sound("katana_swing", 0.5F, 1.35F);      // 二刀で血振り
            }
            if (stateTicks == IAI_RELEASE) {
                sound("katana_iai_release", 1.3F, 1.0F);
                releaseCuts(level);
                LivingEntity foe = getTarget();
                if (iaiKilledOpponent && drawn() && !isWorking() && (foe == null || !foe.isAlive()) && !anotherOpponent(level)) {
                    finishIai(STATE_IAI_VICTORY);
                    return;
                }
                setIaiReturning(true);
            }
            if (stateTicks == IAI_SHEATHE_SEAT - 8) {
                sound("katana_sheathe", 0.4F, 1.0F);
            }
            if (stateTicks >= IAI_ZANSHIN) {
                finishIai(STATE_IDLE);
            }
        }

        private void finishIai(int nextState) {
            crossCiel = null;
            nextIai = 220 + random.nextInt(80);
            nextMove = 16;
            setState(nextState);
        }

        private void finish(int pause) {
            setState(STATE_IDLE);
            nextMove = pause;
        }

        /** 始動時に決めた終点へ残り時間で踏み込む。実際の移動には通常のブロック衝突が働く。 */
        private Vec3 advanceDash(int ticksLeft) {
            Vec3 step = dashTo.subtract(position()).multiply(1, 0, 1).scale(1.0 / ticksLeft);
            setDeltaMovement(step.x, Math.min(0, getDeltaMovement().y), step.z);
            hasImpulse = true;
            return step;
        }

        private void hold(LivingEntity target) {
            getNavigation().stop();
            getLookControl().setLookAt(target, 60, 60);
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }

        private void approach(ServerLevel level, LivingEntity target, double dist) {
            getLookControl().setLookAt(target, 30, 30);
            nextMove--;
            nextIai--;
            cutCooldown--;
            boolean seen = hasLineOfSight(target);
            if (dist > 2.4 || !seen) {
                if (stateTicks % 8 == 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(target, 1.0);
                }
            } else {
                getNavigation().stop();
            }
            if (!seen) {
                return;
            }
            if (!drawn()) {
                // まだ納めている: 間合いに入る前に抜く
                if (dist <= IAI_RANGE) {
                    setState(STATE_DRAW);
                    sound("katana_draw", 0.8F, 1.0F);
                }
                return;
            }
            if (nextIai <= 0 && nextIai > -60 && dist < 3) {
                // 紅一閃の用意ができた: 斬り合いをやめて、居合の間合いまで下がる（3秒下がれなければ、斬り合いに戻る）
                if (stateTicks % 6 == 0 || getNavigation().isDone()) {
                    Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, yBodyRot + 180) : away.normalize();
                    getNavigation().moveTo(getX() + away.x * 4, getY(), getZ() + away.z * 4, 1.2);
                }
                return;
            }
            if (nextIai <= 0 && dist <= IAI_START_RANGE && spend(COST_IAI)) {
                start(STATE_IAI_STANCE, "katana_iai_charge", 1.0F);
                return;
            }
            if (nextMove <= 0 && dist >= 3.5 && dist <= STEP_START_RANGE && spend(COST_STEP)) {
                Vec3 toward = target.position().subtract(position()).multiply(1, 0, 1);
                dashTo = position().add(toward.normalize().scale(Math.max(0, toward.length() - STEP_STOP_DISTANCE)));
                hold(target);
                start(STATE_STEP, "katana_step", 1.0F);
                return;
            }
            if (dist <= CUT_REACH - 0.2 && cutCooldown <= 0 && spend(COST_CUT)) {
                combo = random.nextFloat() < 0.6F;
                start(STATE_CUT, null, 1.0F);
            }
        }

        private void start(int s, String sound, float pitch) {
            if (active()) {
                setState(s);
                if (sound != null) {
                    sound(sound, 0.9F, pitch);
                }
            }
        }
    }

    /** 刀型の効果音（android.katana_*）を鳴らす。 */
    private void sound(String name, float volume, float pitch) {
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("android." + name), volume, pitch);
    }

    /** テスト用: 刀を抜いた状態にする。 */
    public void drawForTest() {
        setDrawn(true);
    }

    /** 斬った相手か（テスト用）。 */
    public boolean markedForTest(Entity e) {
        return marked.contains(e.getId());
    }
}
