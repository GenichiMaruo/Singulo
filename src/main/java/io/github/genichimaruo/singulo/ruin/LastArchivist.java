package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import io.github.genichimaruo.singulo.registry.SinguloTriggers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 最後の記録者（隠しボス）。炉の暴走のとき時間の場で眠らされた旧文明の職員が、記録の結晶と、場を支えていた特異点と
 * 溶け合ったもの。象牙の衣と金の光輪・六枚の光の翼という神々しい姿で、頭は光を飲む事象の地平線、足元は降着の帳に溶けている。
 * <p>
 * 4つの断章から作る「終焉の記録」を、最終実験施設の封印コンソールに使うと目覚める（{@link SealConsoleBlockEntity#activateArchivist}）。
 * <ul>
 *   <li>啓示（体力 2/3 まで）: 裁きの光（光輪の放射が相手を狙い、狙いの線のあとで光の柱を放つ）と、
 *       天秤（相手の足元に光の陣を描き、少しあとで小さなブラックホールが落ちる）</li>
 *   <li>回想（1/3 まで）: これまでの番人の技を記録から読み上げて使う（残響の跳躍斬り・重力の反転・地平線の衝撃波）</li>
 *   <li>終焉（最後）: 光輪が黒く反転し、頭の特異点が広がって周りを引き寄せる。光が内へ集まったあと、
 *       新星の殻が外へ広がる（足元の目の中にいれば当たらない）</li>
 * </ul>
 * 倒されると、光輪が砕け、体が足元から金の塵になって特異点へ吸い込まれ、最後に一点へつぶれて光を残す。
 * 記録者の光輪と最後の記録が残る。
 */
public class LastArchivist extends RuinBoss {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOVE_TICKS = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLASH = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> JUDGE_COUNT = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> JUDGE_1 = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> JUDGE_2 = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> JUDGE_3 = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.VECTOR3);

    public static final int NONE = 0, JUDGMENT = 1, VERDICT = 2, RECALL_ECHO = 3, RECALL_GRAVITY = 4, RECALL_HORIZON = 5, NOVA = 6;

    /** 体力の既定値（設定 archivistHealth）。 */
    public static final double HEALTH = 2400;
    public static final int EMERGE_TICKS = 200, DEATH_TICKS = 200;
    public static final int ARENA = 18;
    /** 漂う高さ（コンソールの上面から）と、相手との間合い。 */
    public static final double HOVER = 2.5, KEEP_DISTANCE = 7;
    public static final float HOVER_F = (float) HOVER;
    /** 体の各部の高さ（足元から）。 */
    public static final double HEAD_Y = 2.78, HALO_Y = 2.85, CHEST_Y = 2.2;
    public static final float HEAD_Y_F = (float) HEAD_Y;

    public static final int JUDGE_WINDUP = 30, FLASH_TICKS = 8;
    public static final float JUDGE_DAMAGE = 9;
    public static final double JUDGE_RANGE = 32;
    public static final int VERDICT_WINDUP = 25;
    public static final int ECHO_WINDUP = 12;
    public static final float ECHO_DAMAGE = 12;
    public static final int GRAVITY_HOLD = 22;
    public static final int HORIZON_WINDUP = 16, HORIZON_SPREAD = 20;
    public static final double HORIZON_RADIUS = 18;
    public static final float HORIZON_DAMAGE = 10;
    public static final int NOVA_CHARGE = 50, NOVA_SPREAD = 20;
    public static final double NOVA_RADIUS = 22, NOVA_EYE = 3.5;
    public static final float NOVA_DAMAGE = 18;
    /** 終焉の引き寄せ。 */
    public static final double PULL_RADIUS = 20, CORE_RADIUS = 2.5;
    public static final float CORE_DAMAGE = 4;
    static final int[] GAP = {70, 55, 45};

    private int moveCooldown = 60;
    private int cycle;
    private int recall;
    private final List<Vec3> verdictMarks = new ArrayList<>();
    private final Set<UUID> ringHit = new HashSet<>();

    public LastArchivist(EntityType<? extends LastArchivist> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        this.xpReward = 1000;
        this.noPhysics = true;
        setNoGravity(true);
        setEffectiveHealth(ServerConfig.SPEC.isLoaded() ? ServerConfig.ARCHIVIST_HEALTH.get() : HEALTH);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH_CAP)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 1);
        builder.define(MOVE, NONE);
        builder.define(MOVE_TICKS, 0);
        builder.define(FLASH, 0);
        builder.define(JUDGE_COUNT, 0);
        builder.define(JUDGE_1, new Vector3f());
        builder.define(JUDGE_2, new Vector3f());
        builder.define(JUDGE_3, new Vector3f());
    }

    @Override
    public int emergeDuration() {
        return EMERGE_TICKS;
    }

    @Override
    public int deathDuration() {
        return DEATH_TICKS;
    }

    @Override
    public int arenaRadius() {
        return ARENA;
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public int move() {
        return entityData.get(MOVE);
    }

    public int moveTicks() {
        return entityData.get(MOVE_TICKS);
    }

    /** 裁きの光を放った光が残る tick。 */
    public int flash() {
        return entityData.get(FLASH);
    }

    /** 裁きの光の狙い（0〜3個）。 */
    public List<Vec3> judgePoints() {
        List<Vec3> out = new ArrayList<>();
        int n = entityData.get(JUDGE_COUNT);
        EntityDataAccessor<?>[] keys = {JUDGE_1, JUDGE_2, JUDGE_3};
        for (int i = 0; i < n && i < 3; i++) {
            @SuppressWarnings("unchecked")
            Vector3f v = entityData.get((EntityDataAccessor<Vector3f>) keys[i]);
            out.add(new Vec3(v.x, v.y, v.z));
        }
        return out;
    }

    /** 光輪の中心（裁きの光の出どころ）。 */
    public Vec3 haloCenter() {
        Vec3 back = Vec3.directionFromRotation(0, yBodyRot).scale(-0.45);
        return position().add(back).add(0, HALO_Y, 0);
    }

    public Vec3 chest() {
        return position().add(0, CHEST_Y, 0);
    }

    private void setMove(int move, int ticks) {
        entityData.set(MOVE, move);
        entityData.set(MOVE_TICKS, ticks);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        // 宙を漂う動きは自分で決める
    }

    @Override
    public int pace(int ticks) {
        return Math.max(4, Math.round(ticks * (phase() == 3 ? 0.8F : 1F)));
    }

    // ------------------------------------------------------------------ 出現

    /**
     * 出現の演出（{@value #EMERGE_TICKS} tick）: コンソールから金の光の柱が立ち、記録の文字が渦を巻いて集まる。
     * 頭の高さに黒い一点が開き、体が上から光で編まれ、翼が開き、光輪が灯る。最後に閃光と、床を走る衝撃波。
     */
    @Override
    protected void onEmergeTick(ServerLevel level, int t) {
        Vec3 c = position();
        if (t == 1) {
            playSound(SinguloSounds.get("last_archivist.emerge"), 4.0F, 0.8F);
        }
        if (t < 40) {
            for (int i = 0; i < 3; i++) {
                level.sendParticles(ParticleTypes.END_ROD, c.x + (random.nextDouble() - 0.5) * 0.4, c.y + random.nextDouble() * 10,
                        c.z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0.05, 0, 1);
            }
        }
        if (t < 150) {
            // 部屋じゅうから記録の文字が渦を巻いて集まる
            double r = 10 * (1 - t / 150.0) + 1;
            for (int i = 0; i < 6; i++) {
                double a = t * 0.15 + i * Math.PI / 3;
                level.sendParticles(ParticleTypes.ENCHANT, c.x + Math.cos(a) * r, c.y + 1 + (i % 3), c.z + Math.sin(a) * r,
                        0, -Math.cos(a), 0, -Math.sin(a), 0.6);
            }
        }
        if (t == 40) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + HEAD_Y, c.z, 80, 0.2, 0.2, 0.2, 0.6);
            level.playSound(null, blockPosition(), SinguloSounds.BLACK_HOLE_FORMATION.get(), SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        if (t == 20) {
            broadcastLine(level, "gui.singulo.archivist.emerge_1");
        }
        if (t == 150) {
            broadcastLine(level, "gui.singulo.archivist.emerge_2");
            playSound(SinguloSounds.get("last_archivist.phase"), 3.0F, 1.2F);
        }
        if (t == 185) {
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y + CHEST_Y, c.z, 2, 0.1, 0.1, 0.1, 0);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + CHEST_Y, c.z, 100, 0.3, 0.3, 0.3, 0.5);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10), e -> e != this && !RuinGuards.isGuard(e))) {
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) {
                    e.push(push.normalize().x * 1.0, 0.45, push.normalize().z * 1.0);
                    e.hurtMarked = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------ 戦闘

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isDeadOrDying() || home == null) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        updatePhase(level);
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !challenger(target))) {
            target = null;
        }
        drift(target);
        if (flash() > 0) {
            entityData.set(FLASH, flash() - 1);
        }
        int ticks = moveTicks();
        if (move() != NONE) {
            entityData.set(MOVE_TICKS, ticks - 1);
            tickMove(level, target, ticks - 1);
        } else if (target != null && --moveCooldown <= 0) {
            startMove(level, target);
        }
        if (phase() == 3) {
            tickEventHorizon(level);
        }
        if (tickCount % 5 == 0) {
            Vec3 h = haloCenter();
            level.sendParticles(phase() == 3 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD, h.x, h.y, h.z, 1, 0.8, 0.8, 0.3, 0.01);
        }
    }

    private void updatePhase(ServerLevel level) {
        float f = getHealth() / getMaxHealth();
        int phase = f > 2F / 3 ? 1 : f > 1F / 3 ? 2 : 3;
        if (phase <= phase()) {
            return;
        }
        entityData.set(PHASE, phase);
        bossBar.setColor(phase == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.WHITE);
        broadcastLine(level, phase == 2 ? "gui.singulo.archivist.phase_2" : "gui.singulo.archivist.phase_3");
        playSound(SinguloSounds.get("last_archivist.phase"), 4.0F, phase == 2 ? 1.0F : 0.7F);
        Vec3 c = chest();
        level.sendParticles(phase == 2 ? ParticleTypes.ENCHANT : ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 120, 1, 1, 1, 1);
        moveCooldown = 30;
    }

    /** コンソールの上を漂い、相手と間合いを取って回り込む。 */
    private void drift(LivingEntity target) {
        Vec3 h = Vec3.atBottomCenterOf(home).add(0, 1 + HOVER, 0);
        Vec3 goal = h;
        if (target != null) {
            Vec3 off = position().subtract(target.position()).multiply(1, 0, 1);
            off = off.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : off.normalize();
            off = off.yRot(0.01F * (phase() + 1));
            goal = target.position().add(off.scale(KEEP_DISTANCE)).add(0, HOVER, 0);
            Vec3 fromHome = goal.subtract(h).multiply(1, 0, 1);
            if (fromHome.length() > ARENA - 5) {
                goal = h.add(fromHome.normalize().scale(ARENA - 5)).add(0, goal.y - h.y, 0);
            }
            goal = new Vec3(goal.x, h.y + Math.sin(tickCount * 0.05) * 0.5, goal.z);
            getLookControl().setLookAt(target, 30, 30);
            float yaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90;
            setYRot(yaw);
            yBodyRot = Mth.approachDegrees(yBodyRot, yaw, 6);
            setYHeadRot(yaw);
        }
        Vec3 v = goal.subtract(position()).scale(0.05);
        if (v.length() > 0.14) {
            v = v.normalize().scale(0.14);
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
    }

    private void startMove(ServerLevel level, LivingEntity target) {
        moveCooldown = pace(GAP[phase() - 1] + random.nextInt(20));
        int kind;
        int step = cycle++;
        if (phase() == 1) {
            kind = step % 2 == 0 ? JUDGMENT : VERDICT;
        } else if (phase() == 2) {
            kind = switch (step % 4) {
                case 0 -> JUDGMENT;
                case 2 -> VERDICT;
                default -> nextRecall();
            };
        } else {
            kind = switch (step % 4) {
                case 0 -> NOVA;
                case 1 -> JUDGMENT;
                case 2 -> nextRecall();
                default -> VERDICT;
            };
        }
        switch (kind) {
            case JUDGMENT -> startJudgment(level, target);
            case VERDICT -> startVerdict(level, target);
            case RECALL_ECHO -> startRecallEcho(level, target);
            case RECALL_GRAVITY -> startRecallGravity(level);
            case RECALL_HORIZON -> {
                setMove(RECALL_HORIZON, HORIZON_WINDUP + HORIZON_SPREAD);
                ringHit.clear();
                playSound(SinguloSounds.get("last_archivist.recall"), 3.0F, 0.8F);
            }
            case NOVA -> {
                setMove(NOVA, NOVA_CHARGE + NOVA_SPREAD);
                ringHit.clear();
                playSound(SinguloSounds.get("last_archivist.nova_charge"), 4.0F, 0.6F);
            }
            default -> { }
        }
    }

    private int nextRecall() {
        int k = recall++ % 3;
        return k == 0 ? RECALL_ECHO : k == 1 ? RECALL_GRAVITY : RECALL_HORIZON;
    }

    private void tickMove(ServerLevel level, LivingEntity target, int left) {
        switch (move()) {
            case JUDGMENT -> {
                if (left <= 0) {
                    fireJudgment(level);
                }
            }
            case VERDICT -> tickVerdict(level, left);
            case RECALL_ECHO -> {
                if (left <= 0) {
                    echoStrike(level, target);
                }
            }
            case RECALL_GRAVITY -> {
                if (left <= 0) {
                    gravitySlam(level);
                }
            }
            case RECALL_HORIZON -> tickHorizonRing(level, left);
            case NOVA -> tickNova(level, left);
            default -> { }
        }
        if (left <= 0) {
            setMove(NONE, 0);
        }
    }

    // ---- 裁きの光

    /** 光輪の放射が相手を狙う（相手が1人なら、今の位置と、動いた先の左右）。狙いは始めたときに決まる。 */
    public void startJudgment(ServerLevel level, LivingEntity target) {
        List<Vec3> points = new ArrayList<>();
        List<Player> players = challengers(level);
        for (Player p : players) {
            if (points.size() < 3) {
                points.add(p.getBoundingBox().getCenter());
            }
        }
        if (points.size() == 1) {
            Vec3 at = points.get(0);
            Vec3 side = at.subtract(haloCenter()).cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
            points.add(at.add(side.scale(2.6)));
            points.add(at.add(side.scale(-2.6)));
        }
        if (points.isEmpty()) {
            points.add(target.getBoundingBox().getCenter());
        }
        EntityDataAccessor<?>[] keys = {JUDGE_1, JUDGE_2, JUDGE_3};
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            @SuppressWarnings("unchecked")
            EntityDataAccessor<Vector3f> key = (EntityDataAccessor<Vector3f>) keys[i];
            entityData.set(key, new Vector3f((float) p.x, (float) p.y, (float) p.z));
        }
        entityData.set(JUDGE_COUNT, points.size());
        setMove(JUDGMENT, pace(JUDGE_WINDUP));
        playSound(SinguloSounds.get("last_archivist.judgment_aim"), 3.0F, 1.0F);
    }

    private void fireJudgment(ServerLevel level) {
        Vec3 from = haloCenter();
        for (Vec3 at : judgePoints()) {
            Vec3 dir = at.subtract(from).normalize();
            Vec3 to = from.add(dir.scale(JUDGE_RANGE));
            HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.2),
                    e -> e.isAlive() && e != this && !RuinGuards.isGuard(e))) {
                if (!e.getBoundingBox().inflate(0.5).clip(from, end).isEmpty()) {
                    e.hurt(damageSources().indirectMagic(this, this), JUDGE_DAMAGE);
                }
            }
            for (int i = 0; i <= 20; i++) {
                Vec3 q = from.lerp(end, i / 20.0);
                level.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.02);
            }
            level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        }
        entityData.set(FLASH, FLASH_TICKS);
        playSound(SinguloSounds.get("last_archivist.judgment_fire"), 3.5F, 1.0F);
    }

    // ---- 天秤

    /** 相手の足元（とその周り）に光の陣を描く。少しあとで、そこへ小さなブラックホールが落ちる。 */
    public void startVerdict(ServerLevel level, LivingEntity target) {
        verdictMarks.clear();
        int n = phase() == 3 ? 5 : 3;
        List<Player> players = challengers(level);
        for (int i = 0; i < n; i++) {
            LivingEntity who = players.isEmpty() ? target : players.get(i % players.size());
            Vec3 at = who.position();
            if (i >= players.size()) {
                double a = random.nextDouble() * Math.PI * 2;
                at = at.add(Math.cos(a) * (3 + random.nextDouble() * 3), 0, Math.sin(a) * (3 + random.nextDouble() * 3));
            }
            verdictMarks.add(at);
        }
        setMove(VERDICT, pace(VERDICT_WINDUP));
        playSound(SinguloSounds.get("last_archivist.verdict"), 3.0F, 1.0F);
    }

    private void tickVerdict(ServerLevel level, int left) {
        for (Vec3 m : verdictMarks) {
            if (left % 3 == 0) {
                for (int k = 0; k < 10; k++) {
                    double a = k * Math.PI / 5 + left * 0.1;
                    level.sendParticles(ParticleTypes.END_ROD, m.x + Math.cos(a) * WardenSingularity.RADIUS, m.y + 0.1,
                            m.z + Math.sin(a) * WardenSingularity.RADIUS, 1, 0, 0.02, 0, 0);
                }
            }
            if (left <= 0) {
                level.addFreshEntity(new WardenSingularity(level, this, m.add(0, 1, 0)));
            }
        }
        if (left <= 0) {
            playSound(SinguloSounds.get("last_archivist.collapse"), 3.0F, 0.8F);
            verdictMarks.clear();
        }
    }

    // ---- 回想（これまでの番人の技）

    /** 残響の番人の技: 相手の背後へ跳び、光を集めてから斬る。 */
    private void startRecallEcho(ServerLevel level, LivingEntity target) {
        Vec3 look = target.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) {
            look = new Vec3(0, 0, 1);
        }
        Vec3 behind = target.position().subtract(look.normalize().scale(2.5));
        Vec3 before = position();
        setPos(behind.x, target.getY() + 0.3, behind.z);
        level.sendParticles(ParticleTypes.END_ROD, before.x, before.y + 1.5, before.z, 40, 0.4, 1, 0.4, 0.1);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 30, 0.4, 1, 0.4, 0.2);
        setMove(RECALL_ECHO, pace(ECHO_WINDUP));
        playSound(SinguloSounds.get("last_archivist.recall"), 3.0F, 1.3F);
        playSound(SinguloSounds.get("echo_sentinel.strike_windup"), 2.0F, 0.8F);
    }

    private void echoStrike(ServerLevel level, LivingEntity target) {
        playSound(SinguloSounds.get("echo_sentinel.strike"), 2.5F, 0.8F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.5, getZ(), 3, 1, 0.3, 1, 0);
        for (Player p : challengers(level)) {
            if (p.distanceTo(this) <= 4.5 && p.hurt(damageSources().mobAttack(this), ECHO_DAMAGE)) {
                Vec3 k = p.position().subtract(position()).multiply(1, 0, 1);
                if (k.lengthSqr() > 1e-4) {
                    p.knockback(0.8, -k.normalize().x, -k.normalize().z);
                }
            }
        }
    }

    /** 重力の澱の技: 周りの相手を浮かせ、少しあとで床へ叩きつける。 */
    private void startRecallGravity(ServerLevel level) {
        for (Player p : challengers(level)) {
            if (p.distanceTo(this) <= 16) {
                p.setDeltaMovement(p.getDeltaMovement().x * 0.3, 1.25, p.getDeltaMovement().z * 0.3);
                p.hurtMarked = true;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 0.5, p.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
            }
        }
        setMove(RECALL_GRAVITY, GRAVITY_HOLD);
        playSound(SinguloSounds.get("last_archivist.recall"), 3.0F, 0.9F);
        playSound(SinguloSounds.get("gravity_remnant.lift"), 2.5F, 0.8F);
    }

    private void gravitySlam(ServerLevel level) {
        for (Player p : challengers(level)) {
            if (p.distanceTo(this) <= 20 && !p.onGround()) {
                p.setDeltaMovement(p.getDeltaMovement().x, -3.0, p.getDeltaMovement().z);
                p.fallDistance = Math.max(p.fallDistance, 8);
                p.hurtMarked = true;
            }
        }
        playSound(SinguloSounds.get("gravity_remnant.slam"), 2.5F, 0.8F);
    }

    /** ホライズン・ウォーデンの技: 床を走る衝撃波の輪（地に足がついていると当たる。跳べばよけられる）。 */
    private void tickHorizonRing(ServerLevel level, int left) {
        if (left > HORIZON_SPREAD) {
            return;
        }
        if (left == HORIZON_SPREAD) {
            playSound(SinguloSounds.get("horizon_warden.shockwave"), 3.0F, 0.8F);
        }
        double r = horizonRadius(left);
        double floor = home.getY();
        Vec3 c = new Vec3(getX(), floor, getZ());
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI / 12;
            level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, floor + 0.2, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        for (Player p : challengers(level)) {
            double d = Math.hypot(p.getX() - c.x, p.getZ() - c.z);
            if (Math.abs(d - r) < 1.2 && p.onGround() && ringHit.add(p.getUUID())) {
                p.hurt(damageSources().mobAttack(this), HORIZON_DAMAGE);
                p.push(0, 0.4, 0);
                p.hurtMarked = true;
            }
        }
    }

    /** 衝撃波の輪の半径（残り tick から）。 */
    public static double horizonRadius(int left) {
        return HORIZON_RADIUS * (1 - Math.max(0, left) / (double) HORIZON_SPREAD);
    }

    // ---- 終焉

    /** 終焉: 頭の特異点が周りを引き寄せ、触れたものを潮汐で傷つける。 */
    private void tickEventHorizon(ServerLevel level) {
        Vec3 c = position().add(0, HEAD_Y, 0);
        for (Player p : challengers(level)) {
            Vec3 to = c.subtract(p.position().add(0, 1, 0));
            double d = to.length();
            if (d > PULL_RADIUS || d < 1e-3) {
                continue;
            }
            p.setDeltaMovement(p.getDeltaMovement().add(to.normalize().scale(0.02 * (1 - d / PULL_RADIUS))));
            p.hurtMarked = true;
            if (d <= CORE_RADIUS && tickCount % 10 == 0) {
                p.hurt(SinguloDamageTypes.tidal(level, this), CORE_DAMAGE);
            }
        }
    }

    /** 新星: 光が内へ集まり、そのあと殻が外へ広がる。足元の目（半径 NOVA_EYE）の中にいれば当たらない。 */
    private void tickNova(ServerLevel level, int left) {
        Vec3 c = chest();
        if (left > NOVA_SPREAD) {
            double r = 8 * (left - NOVA_SPREAD) / (double) NOVA_CHARGE + 0.5;
            for (int k = 0; k < 6; k++) {
                double a = random.nextDouble() * Math.PI * 2;
                double b = (random.nextDouble() - 0.5) * Math.PI;
                Vec3 d = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                level.sendParticles(ParticleTypes.END_ROD, c.x + d.x * r * 2, c.y + d.y * r * 2, c.z + d.z * r * 2, 0, -d.x, -d.y, -d.z, 0.5);
            }
            return;
        }
        if (left == NOVA_SPREAD) {
            playSound(SinguloSounds.get("last_archivist.nova"), 5.0F, 0.8F);
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0);
        }
        double r = novaRadius(left);
        for (Player p : challengers(level)) {
            double d = p.position().add(0, 1, 0).distanceTo(c);
            if (Math.abs(d - r) < 1.5 && ringHit.add(p.getUUID())) {
                p.hurt(damageSources().indirectMagic(this, this), NOVA_DAMAGE);
                Vec3 k = p.position().subtract(c).normalize();
                p.push(k.x, 0.5, k.z);
                p.hurtMarked = true;
            }
        }
    }

    /** 新星の殻の半径（残り tick から）。 */
    public static double novaRadius(int left) {
        return NOVA_EYE + (NOVA_RADIUS - NOVA_EYE) * (1 - Math.max(0, left) / (double) NOVA_SPREAD);
    }

    private List<Player> challengers(ServerLevel level) {
        Vec3 c = home == null ? position() : Vec3.atCenterOf(home);
        return level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(ARENA + 4), RuinBoss::challenger);
    }

    // ------------------------------------------------------------------ やめたとき・倒されたとき

    /** 挑戦者がいなくなったら、コンソールに知らせ、呼び出しに使った終焉の記録を返す。 */
    @Override
    protected void onAbandoned(ServerLevel level) {
        if (home != null) {
            if (level.getBlockEntity(home) instanceof SealConsoleBlockEntity console) {
                console.onArchivistReset(level);
            }
            net.minecraft.world.level.block.Block.popResource(level, home.above(), new ItemStack(SealConsoleBlockEntity.terminusRecord()));
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        setMove(NONE, 0);
        if (level() instanceof ServerLevel level) {
            playSound(SinguloSounds.get("last_archivist.death"), 4.0F, 0.8F);
            broadcastLine(level, "gui.singulo.archivist.death_1");
            if (home != null && level.getBlockEntity(home) instanceof SealConsoleBlockEntity console) {
                console.onArchivistDefeated(level);
            }
        }
    }

    /**
     * 倒されたときの演出（{@value #DEATH_TICKS} tick）: 光輪が砕けて散り、翼が畳まれて消える。体は足元から金の塵になって昇り、
     * 頭の特異点へ吸い込まれる。特異点はふくらんでから一点へつぶれ、最後に柔らかな閃光を残す。光輪と最後の記録が残る。
     */
    @Override
    protected void onDeathTick(ServerLevel level, int t) {
        Vec3 c = position();
        Vec3 head = c.add(0, HEAD_Y, 0);
        setDeltaMovement(Vec3.ZERO);
        if (t < 40 && t % 4 == 0) {
            Vec3 h = haloCenter();
            level.sendParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 10, 1.0, 1.0, 0.2, 0.3);
            level.sendParticles(ParticleTypes.END_ROD, h.x, h.y, h.z, 6, 1.0, 1.0, 0.2, 0.05);
        }
        if (t >= 40 && t < 120) {
            double y = (t - 40) / 80.0 * HEAD_Y;
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * 0.6, c.y + y, c.z + Math.sin(a) * 0.6, 0, 0, 0.08, 0, 1);
            }
            level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y + y + 1, c.z, 6, 0.6, 0.6, 0.6, 0.5);
        }
        if (t == 60) {
            broadcastLine(level, "gui.singulo.archivist.death_2");
        }
        if (t >= 120 && t < 170) {
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double b = (random.nextDouble() - 0.5) * Math.PI;
                double r = 3 + random.nextDouble() * 4;
                Vec3 d = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, head.x + d.x * r, head.y + d.y * r, head.z + d.z * r, 0, -d.x, -d.y, -d.z, 0.9);
            }
        }
        if (t == 172) {
            level.playSound(null, blockPosition(), SinguloSounds.get("last_archivist.death_flash"), SoundSource.HOSTILE, 4.0F, 1.0F);
        }
        if (t == 185) {
            level.sendParticles(ParticleTypes.FLASH, head.x, head.y, head.z, 3, 0.1, 0.1, 0.1, 0);
            level.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 160, 0.2, 0.2, 0.2, 0.45);
            level.sendParticles(ParticleTypes.ENCHANT, head.x, head.y, head.z, 120, 2, 2, 2, 1.5);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10), e -> e != this && !RuinGuards.isGuard(e))) {
                Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) {
                    e.push(push.normalize().x * 0.8, 0.3, push.normalize().z * 0.8);
                    e.hurtMarked = true;
                }
            }
            dropRewards(level);
        }
        if (t == 192) {
            broadcastLine(level, "gui.singulo.archivist.death_3");
        }
    }

    /** 報酬: 記録者の光輪、オーバークロック・チップ、H凝縮体。近くの挑戦者は最後の記録を読めるようになる。 */
    private void dropRewards(ServerLevel level) {
        for (String[] r : new String[][] {{"archivist_halo", "1"}, {"overclock_chip", "4"}, {"hawking_condensate", "3"}}) {
            Item item = BuiltInRegistries.ITEM.get(Singulo.id(r[0]));
            spawnAtLocation(new ItemStack(item, Integer.parseInt(r[1])), (float) HEAD_Y);
        }
        SinguloTriggers.milestoneNear(level, blockPosition(), ARENA + 16, "last_archivist");
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(ARENA + 16))) {
            AncientRecords.learn(p, "x01");
        }
    }

    private void broadcastLine(ServerLevel level, String key) {
        Component msg = Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.ITALIC);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(ARENA + 16))) {
            p.displayClientMessage(msg, false);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;                                  // 体から鳴り続ける音は、クライアントの BossSounds が鳴らす
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SinguloSounds.get("last_archivist.hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("phase", phase());
        tag.putInt("cycle", cycle);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, Math.max(1, tag.getInt("phase")));
        cycle = tag.getInt("cycle");
        int p = phase();
        bossBar.setColor(p == 1 ? BossEvent.BossBarColor.YELLOW : p == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.WHITE);
    }

    /** 召喚の位置（コンソールの上）。 */
    public static Vec3 spawnAt(BlockPos console) {
        return Vec3.atBottomCenterOf(console).add(0, 1 + HOVER, 0);
    }
}
