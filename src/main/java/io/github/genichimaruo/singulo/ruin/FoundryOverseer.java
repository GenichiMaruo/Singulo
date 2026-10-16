package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.android.AndroidKind;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 製造統括AI「ファウンドリ」（廃アンドロイド工場のボス）。中央管制ホールの中心に浮かぶ、大きな機械の頭脳。自分では動かず、工場の設備で戦う。
 * <ul>
 *   <li>製造ライン（{@link FactoryLineBlock}）が動いている間は、受けるダメージが小さい（1つにつき2割）。ラインは殴って壊せる</li>
 *   <li>ラインは、ときどき量産型のアンドロイド（弱い・何も落とさない）を印刷して送り出す（同時に {@value #MAX_MASS} 体まで）</li>
 *   <li>ロボットアーム: 床に赤い印が出て、少しの間は相手を追い、止まってから降りてきてつかみ、持ち上げて投げる</li>
 *   <li>プレス: 床に影の丸が出て、少しあとで上から叩きつける（ダメージと、少しの間動きが鈍る）</li>
 *   <li>コンベア: 床の帯（{@link FactoryConveyorBlock}）を動かして相手を流し、ときどき向きを変える（変える前に枠の灯が赤く光る）</li>
 *   <li>溶接レーザー: 床に扇形の印が出たあと、コアから床をなぞる光線がゆっくり回る。跳べば越えられる</li>
 *   <li>本気（体力半分）: 残っているラインから試作機を2体呼ぶ。アームが2本になり、プレスが広く、技の間が短い</li>
 * </ul>
 * 覚醒した個体（覚醒の製造核で呼ぶ攻略後の再戦）は、本気になったとき壊れた製造ラインをすべて1度だけ印刷し直す。
 */
public class FoundryOverseer extends RuinBoss {
    private static final EntityDataAccessor<Integer> LINES = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.INT);
    /** ロボットアーム（2本）: 残り tick と、狙う床の位置。 */
    private static final EntityDataAccessor<Integer> ARM0 = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> ARM0_AT = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> ARM1 = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> ARM1_AT = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.VECTOR3);
    /** プレス: 残り tick と、叩く床の位置。 */
    private static final EntityDataAccessor<Integer> PRESS = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> PRESS_AT = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.VECTOR3);
    /** 溶接レーザー: 残り tick、なぞり始めの向き（度）、回る向き（+1 / −1）。 */
    private static final EntityDataAccessor<Integer> LASER = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LASER_FROM = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LASER_SPIN = SynchedEntityData.defineId(FoundryOverseer.class, EntityDataSerializers.INT);

    public static final int EMERGE_TICKS = 80;
    public static final int DEATH_TICKS = 70;
    public static final float HEALTH = 380;
    public static final float HEALTH_NORMAL = 280;
    /** 動いている製造ラインの数（0〜4）ごとの、受けるダメージの倍率。 */
    public static final float[] LINE_FACTOR = {1.0F, 0.8F, 0.6F, 0.4F, 0.2F};
    /** 床から浮かぶ高さ（足の位置）。下から殴れる高さ。 */
    public static final double HOVER = 2.2;
    /** 技を当てる部屋の広さ（核から水平に、四角。足の中心がここまで）。AI中枢（31×31）の内側は核から14ブロック。 */
    public static final double ROOM_HALF = 13.5;

    public static final int ARM_INTERVAL = 100, ARM_INTERVAL_ENRAGED = 70;
    /** アームの狙い（印が出てからつかむまで）と、つかんだあと上がっていく間。狙いの最後の LOCK tick は印が止まる。 */
    public static final int ARM_AIM = 40, ARM_HOLD = 14, ARM_LOCK = 16;
    public static final double ARM_RADIUS = 1.7;
    public static final float ARM_DAMAGE = 9, ARM_DAMAGE_NORMAL = 7;
    public static final int PRESS_INTERVAL = 120, PRESS_INTERVAL_ENRAGED = 80;
    public static final int PRESS_WINDUP = 26, PRESS_FLASH = 14;
    public static final double PRESS_RADIUS = 2.3, PRESS_RADIUS_ENRAGED = 2.9;
    public static final float PRESS_DAMAGE = 11, PRESS_DAMAGE_NORMAL = 9;
    public static final int LASER_INTERVAL = 240, LASER_INTERVAL_ENRAGED = 170;
    public static final int LASER_AIM = 30, LASER_SWEEP = 60;
    /** レーザーがなぞる角度（度）と、太さ。床から LASER_CLEAR より高く跳んでいれば当たらない。届く長さは向きごとに壁まで（{@link #laserReach}）。 */
    public static final float LASER_ARC = 200;
    public static final double LASER_WIDTH = 0.8, LASER_CLEAR = 0.9;
    /** いちばん長く届く距離（部屋の隅まで）。 */
    public static final double LASER_REACH = (ROOM_HALF + 0.5) * Math.sqrt(2);
    public static final float LASER_DAMAGE = 8, LASER_DAMAGE_NORMAL = 6;
    /** コンベアが向きを変える間と、その前に警告する tick。 */
    public static final int CONVEYOR_PERIOD = 240, CONVEYOR_PERIOD_ENRAGED = 160, CONVEYOR_WARN = 40;
    public static final int PRINT_INTERVAL = 260, PRINT_INTERVAL_ENRAGED = 200;
    public static final int MAX_MASS = 4;

    private final List<BlockPos> lines = new ArrayList<>();
    private final List<BlockPos> conveyors = new ArrayList<>();
    private int armCooldown = 40, pressCooldown = 70, laserCooldown = 150, printCooldown = 120;
    private int conveyorClock;
    private boolean conveyorsOn;
    private boolean prototypesCalled;
    private boolean reprinted;
    /** 今のレーザーで、もう当てた相手（1回のなぞりで1度だけ）。 */
    private final Set<UUID> laserHit = new HashSet<>();
    /** 守る核がないとき（スポーンエッグ）に浮かぶ場所。 */
    @Nullable
    private Vec3 anchor;

    public FoundryOverseer(EntityType<? extends FoundryOverseer> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        this.xpReward = 600;
        setNoGravity(true);
        if (!level.isClientSide && !BossScaling.hardMode()) {
            setEffectiveHealth(HEALTH_NORMAL);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ARM_DAMAGE)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public static float health() {
        return BossScaling.hardMode() ? HEALTH : HEALTH_NORMAL;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LINES, 0);
        entityData.define(ARM0, 0);
        entityData.define(ARM0_AT, new Vector3f());
        entityData.define(ARM1, 0);
        entityData.define(ARM1_AT, new Vector3f());
        entityData.define(PRESS, 0);
        entityData.define(PRESS_AT, new Vector3f());
        entityData.define(LASER, 0);
        entityData.define(LASER_FROM, 0F);
        entityData.define(LASER_SPIN, 1);
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
    public double spawnHeight() {
        return HOVER;
    }

    @Override
    protected BossEvent.BossBarColor awakenedColor() {
        return BossEvent.BossBarColor.RED;
    }

    @Override
    protected String[][] awakenedRewards() {
        return new String[][] {{"overclock_chip", "1"}, {"precision_actuator", "3"}, {"android_kit", "2"}};
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
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(16);
    }

    // ------------------------------------------------------------------ 部屋の仕掛け

    /** 製造ラインの位置（封印核が渡す）。 */
    public void setLines(List<BlockPos> positions) {
        lines.clear();
        lines.addAll(positions);
        countLines();
    }

    /** 動かすコンベアの位置（封印核が渡す）。 */
    public void setConveyors(List<BlockPos> positions) {
        conveyors.clear();
        conveyors.addAll(positions);
    }

    /** 今も動いている製造ラインの数（描画にも使う）。 */
    public int lineCount() {
        return entityData.get(LINES);
    }

    private void countLines() {
        entityData.set(LINES, Math.min(intactLines().size(), LINE_FACTOR.length - 1));
    }

    public List<BlockPos> intactLines() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : lines) {
            if (level().getBlockState(p).is(SinguloBlocks.FACTORY_LINE.get())) {
                out.add(p);
            }
        }
        return out;
    }

    /** 体力が半分（覚醒した個体は 65%）を切ったか。 */
    public boolean enraged() {
        return getHealth() <= getMaxHealth() * enrageFraction();
    }

    /** 床の高さ（浮かぶ高さから求める。クライアントでも使える）。 */
    public double floorY() {
        return getY() - HOVER;
    }

    /** 浮かぶ場所（核の上）。 */
    public Vec3 anchor() {
        if (home != null) {
            return Vec3.atBottomCenterOf(home).add(0, 1.0 + HOVER, 0);
        }
        if (anchor == null) {
            anchor = position();
        }
        return anchor;
    }

    /** 技で狙う床の位置: 部屋の中に収め、高さは相手の足元（中枢の台地・見張り台のどちらでも）。床より下へは狙わない。 */
    public Vec3 aim(Vec3 p) {
        Vec3 c = clampToRoom(p);
        return new Vec3(c.x, Math.max(p.y, floorY() - 4), c.z);
    }

    /** 部屋の中に収めた床の位置。 */
    public Vec3 clampToRoom(Vec3 p) {
        Vec3 c = anchor();
        return new Vec3(Mth.clamp(p.x, c.x - ROOM_HALF, c.x + ROOM_HALF), floorY(), Mth.clamp(p.z, c.z - ROOM_HALF, c.z + ROOM_HALF));
    }

    public int armTicks(int arm) {
        return entityData.get(arm == 0 ? ARM0 : ARM1);
    }

    public Vec3 armTarget(int arm) {
        Vector3f v = entityData.get(arm == 0 ? ARM0_AT : ARM1_AT);
        return new Vec3(v.x, v.y, v.z);
    }

    public int pressTicks() {
        return entityData.get(PRESS);
    }

    public Vec3 pressTarget() {
        Vector3f v = entityData.get(PRESS_AT);
        return new Vec3(v.x, v.y, v.z);
    }

    public double pressRadius() {
        return enraged() ? PRESS_RADIUS_ENRAGED : PRESS_RADIUS;
    }

    public int laserTicks() {
        return entityData.get(LASER);
    }

    /** 今のレーザーの向き（度。なぞっている間だけ意味がある）。 */
    public float laserAngle(float partialTick) {
        int t = laserTicks();
        float k = t > LASER_SWEEP ? 0 : Mth.clamp(1 - (t - partialTick) / LASER_SWEEP, 0, 1);
        return laserFrom() + entityData.get(LASER_SPIN) * LASER_ARC * k;
    }

    /** 向き（度）ごとの、レーザーが届く長さ（核から四角い部屋の壁まで）。 */
    public static double laserReach(double deg) {
        double a = Math.toRadians(deg);
        return (ROOM_HALF + 0.5) / Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a)));
    }

    public float laserFrom() {
        return entityData.get(LASER_FROM);
    }

    public int laserSpin() {
        return entityData.get(LASER_SPIN);
    }

    // ------------------------------------------------------------------ 出現

    @Override
    protected void onEmergeTick(ServerLevel level, int t) {
        if (t == 1) {
            playSound(SinguloSounds.get("foundry.emerge"), 2.5F, 0.9F);
        }
        // 製造ラインから火花が走り、天井のケーブルに光が通る
        if (t % 6 == 0) {
            for (BlockPos p : intactLines()) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.15);
            }
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 2, 0.6, 0.6, 0.6, 0.05);
    }

    @Override
    protected void onEmerged(ServerLevel level) {
        level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.2, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 40, 0.8, 0.8, 0.8, 0.3);
        setConveyors(true);
    }

    // ------------------------------------------------------------------ 戦闘

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isDeadOrDying()) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        hover();
        if (tickCount % 10 == 0) {
            int before = lineCount();
            countLines();
            if (lineCount() < before) {
                level.playSound(null, blockPosition(), SinguloSounds.get("foundry.line_break"), SoundSource.HOSTILE, 2.0F, 1.0F);
            }
        }
        tickArm(level, 0);
        tickArm(level, 1);
        tickPress(level);
        tickLaser(level);
        tickConveyors(level);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || !targetInRoom(target)) {
            return;
        }
        turnTo(target);
        boolean enraged = enraged();
        if (enraged && !prototypesCalled) {
            callPrototypes(level);
        }
        if (--armCooldown <= 0 && armTicks(0) == 0) {
            armCooldown = pace(enraged ? ARM_INTERVAL_ENRAGED : ARM_INTERVAL);
            startArm(0, target);
        }
        if (enraged && armTicks(1) == 0 && armTicks(0) > 0 && armTicks(0) <= ARM_HOLD + ARM_AIM / 2) {
            // 本気: 2本目のアームは、1本目の狙いの途中から、別の相手（いなければ同じ相手）を狙う
            LivingEntity other = otherChallenger(level, target);
            startArm(1, other != null ? other : target);
        }
        // プレスとレーザーは同時には使わない
        pressCooldown--;
        laserCooldown--;
        if (pressTicks() == 0 && laserTicks() == 0) {
            if (laserCooldown <= 0) {
                laserCooldown = pace(enraged ? LASER_INTERVAL_ENRAGED : LASER_INTERVAL);
                pressCooldown = Math.max(pressCooldown, 30);
                startLaser(target);
            } else if (pressCooldown <= 0) {
                pressCooldown = pace(enraged ? PRESS_INTERVAL_ENRAGED : PRESS_INTERVAL);
                startPress(target);
            }
        }
        if (--printCooldown <= 0) {
            printCooldown = pace(enraged ? PRINT_INTERVAL_ENRAGED : PRINT_INTERVAL);
            printMass(level, target);
        }
    }

    /** 核の上に浮かんだまま動かない（押されても戻る）。 */
    private void hover() {
        Vec3 a = anchor();
        if (position().distanceToSqr(a) > 0.01) {
            setPos(a.x, a.y, a.z);
        }
        setDeltaMovement(Vec3.ZERO);
    }

    /** 単眼を相手へゆっくり向ける。 */
    private void turnTo(LivingEntity target) {
        float want = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90;
        float yaw = Mth.approachDegrees(getYRot(), want, 4);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    /** 相手が部屋の中にいるか（核がなければ、いつも中）。 */
    public boolean targetInRoom(LivingEntity target) {
        if (home == null) {
            return true;
        }
        Vec3 c = anchor();
        double dy = target.getY() - floorY();
        return Math.abs(target.getX() - c.x) <= ROOM_HALF + 2 && Math.abs(target.getZ() - c.z) <= ROOM_HALF + 2 && dy >= -1.5 && dy <= 12;
    }

    @Nullable
    private LivingEntity otherChallenger(ServerLevel level, LivingEntity not) {
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(anchor(), anchor()).inflate(ROOM_HALF + 2, 8, ROOM_HALF + 2),
                p -> challenger(p) && p != not)) {
            return p;
        }
        return null;
    }

    private DamageSource magic() {
        return BossScaling.hardMode() ? damageSources().indirectMagic(this, this) : damageSources().mobAttack(this);
    }

    private static Vector3f vec(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    // ------------------------------------------------------------------ ロボットアーム

    public void startArm(int arm, LivingEntity target) {
        if (armTicks(arm) > 0) {
            return;
        }
        entityData.set(arm == 0 ? ARM0 : ARM1, ARM_AIM + ARM_HOLD);
        entityData.set(arm == 0 ? ARM0_AT : ARM1_AT, vec(aim(target.position())));
        playSound(SinguloSounds.get("foundry.arm_aim"), 1.5F, 0.8F + arm * 0.2F);
    }

    private void tickArm(ServerLevel level, int arm) {
        int t = armTicks(arm);
        if (t <= 0) {
            return;
        }
        entityData.set(arm == 0 ? ARM0 : ARM1, t - 1);
        // 狙いの前半は相手を追い、最後の ARM_LOCK tick は止まる（よける間）
        LivingEntity target = getTarget();
        if (t - 1 > ARM_HOLD + ARM_LOCK && target != null && target.isAlive() && targetInRoom(target)) {
            Vec3 now = armTarget(arm);
            Vec3 want = aim(target.position());
            entityData.set(arm == 0 ? ARM0_AT : ARM1_AT, vec(now.lerp(want, 0.2)));
        }
        if (t - 1 != ARM_HOLD) {
            return;
        }
        Vec3 at = armTarget(arm);
        level.playSound(null, at.x, at.y, at.z, SinguloSounds.get("foundry.arm_grab"), SoundSource.HOSTILE, 1.8F, 0.9F);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.5, at.z, 12, 0.6, 0.3, 0.6, 0.2);
        Vec3 c = anchor();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(ARM_RADIUS, 2.5, ARM_RADIUS),
                e -> e.isAlive() && !RuinGuards.isGuard(e))) {
            if (e.position().subtract(at).horizontalDistance() > ARM_RADIUS) {
                continue;
            }
            if (hurtWith("arm", e, damageSources().mobAttack(this), BossScaling.hardMode() ? ARM_DAMAGE : ARM_DAMAGE_NORMAL)) {
                // 持ち上げて、部屋の内側（中枢の台地の上）へ投げる。縁の溶岩の溝へは落とさない
                Vec3 in = c.subtract(e.position()).multiply(1, 0, 1);
                in = in.lengthSqr() < 1e-3 ? Vec3.directionFromRotation(0, random.nextFloat() * 360) : in.normalize();
                e.setDeltaMovement(in.x * 0.9, 0.95, in.z * 0.9);
                e.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ プレス

    public void startPress(LivingEntity target) {
        entityData.set(PRESS, PRESS_WINDUP + PRESS_FLASH);
        Vec3 at = aim(target.position());
        entityData.set(PRESS_AT, vec(at));
        level().playSound(null, at.x, at.y, at.z, SinguloSounds.get("foundry.press_aim"), SoundSource.HOSTILE, 1.8F, 1.0F);
    }

    private void tickPress(ServerLevel level) {
        int t = pressTicks();
        if (t <= 0) {
            return;
        }
        entityData.set(PRESS, t - 1);
        if (t - 1 != PRESS_FLASH) {
            return;
        }
        Vec3 at = pressTarget();
        double r = pressRadius();
        level.playSound(null, at.x, at.y, at.z, SinguloSounds.get("foundry.press_slam"), SoundSource.HOSTILE, 2.2F, 0.7F);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, r * 0.3, 0.1, r * 0.3, 0);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 30, r * 0.5, 0.05, r * 0.5, 0.08);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 3, r),
                e -> e.isAlive() && !RuinGuards.isGuard(e))) {
            if (e.position().subtract(at).horizontalDistance() <= r
                    && hurtWith("press", e, damageSources().mobAttack(this), BossScaling.hardMode() ? PRESS_DAMAGE : PRESS_DAMAGE_NORMAL)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
            }
        }
    }

    // ------------------------------------------------------------------ 溶接レーザー

    public void startLaser(LivingEntity target) {
        Vec3 c = anchor();
        float toTarget = (float) (Mth.atan2(target.getZ() - c.z, target.getX() - c.x) * Mth.RAD_TO_DEG);
        int spin = random.nextBoolean() ? 1 : -1;
        // 相手の位置が、なぞる扇のまん中に来るように
        entityData.set(LASER_FROM, toTarget - spin * LASER_ARC / 2);
        entityData.set(LASER_SPIN, spin);
        entityData.set(LASER, LASER_AIM + LASER_SWEEP);
        laserHit.clear();
        playSound(SinguloSounds.get("foundry.laser_aim"), 2.0F, 1.0F);
    }

    private void tickLaser(ServerLevel level) {
        int t = laserTicks();
        if (t <= 0) {
            return;
        }
        entityData.set(LASER, t - 1);
        if (t - 1 > LASER_SWEEP) {
            return;
        }
        if (t - 1 == LASER_SWEEP) {
            playSound(SinguloSounds.get("foundry.laser_fire"), 2.0F, 0.8F);
        }
        float angle = laserAngle(1);
        Vec3 c = anchor();
        Vec3 dir = new Vec3(Math.cos(angle * Mth.DEG_TO_RAD), 0, Math.sin(angle * Mth.DEG_TO_RAD));
        double floor = floorY();
        if (t % 2 == 0) {
            double r = 1.5 + random.nextDouble() * (laserReach(angle) - 1.5);
            level.sendParticles(ParticleTypes.LAVA, c.x + dir.x * r, floor + 0.2, c.z + dir.z * r, 1, 0, 0, 0, 0);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(LASER_REACH, 0, LASER_REACH)
                .setMinY(floor - 1).setMaxY(floor + 3), e -> e.isAlive() && !RuinGuards.isGuard(e) && !laserHit.contains(e.getUUID()))) {
            Vec3 rel = e.position().subtract(c).multiply(1, 0, 1);
            double along = rel.dot(dir);
            double side = Math.abs(rel.x * dir.z - rel.z * dir.x);
            if (along > 0.5 && along <= laserReach(angle) && side <= LASER_WIDTH && e.getY() - floor < LASER_CLEAR
                    && hurtWith("laser", e, magic(), BossScaling.hardMode() ? LASER_DAMAGE : LASER_DAMAGE_NORMAL)) {
                laserHit.add(e.getUUID());
                e.setSecondsOnFire(2);
            }
        }
    }

    // ------------------------------------------------------------------ コンベア

    /** コンベアを動かす・止める（止めるときは警告の灯も消す）。 */
    public void setConveyors(boolean on) {
        conveyorsOn = on;
        conveyorClock = 0;
        for (BlockPos p : conveyors) {
            BlockState s = level().getBlockState(p);
            if (s.getBlock() instanceof FactoryConveyorBlock) {
                BlockState n = s.setValue(FactoryConveyorBlock.RUNNING, on).setValue(FactoryConveyorBlock.WARN, false);
                if (n != s) {
                    level().setBlock(p, n, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private void tickConveyors(ServerLevel level) {
        if (!conveyorsOn || conveyors.isEmpty()) {
            return;
        }
        int period = pace(enraged() ? CONVEYOR_PERIOD_ENRAGED : CONVEYOR_PERIOD);
        conveyorClock++;
        if (conveyorClock == period - CONVEYOR_WARN) {
            setAll(true, false);
            level.playSound(null, blockPosition(), SinguloSounds.get("foundry.conveyor_warn"), SoundSource.HOSTILE, 2.0F, 0.6F);
        } else if (conveyorClock >= period) {
            conveyorClock = 0;
            setAll(false, true);
            level.playSound(null, blockPosition(), SinguloSounds.get("foundry.conveyor_flip"), SoundSource.HOSTILE, 2.0F, 0.8F);
        }
    }

    private void setAll(boolean warn, boolean flip) {
        for (BlockPos p : conveyors) {
            BlockState s = level().getBlockState(p);
            if (s.getBlock() instanceof FactoryConveyorBlock) {
                BlockState n = s.setValue(FactoryConveyorBlock.WARN, warn);
                if (flip) {
                    n = n.setValue(FactoryConveyorBlock.FACING, s.getValue(FactoryConveyorBlock.FACING).getOpposite());
                }
                level().setBlock(p, n, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 今コンベアが向きを変える前の警告中か（テスト用）。 */
    public int conveyorClock() {
        return conveyorClock;
    }

    // ------------------------------------------------------------------ 製造ライン・試作機

    /** 量産型（弱い・何も落とさない）を、残っているラインのどれかから1体印刷する。 */
    public boolean printMass(ServerLevel level, @Nullable LivingEntity target) {
        List<BlockPos> live = intactLines();
        if (live.isEmpty() || massCount(level) >= MAX_MASS) {
            return false;
        }
        BlockPos line = live.get(random.nextInt(live.size()));
        AndroidKind[] kinds = AndroidKind.ruinKinds();             // 量産型は旧型（試作機の型は量産しない）
        return spawnAt(level, line, kinds[random.nextInt(kinds.length)], false, target) != null;
    }

    /** 本気になったとき: 試作機を2体呼ぶ（覚醒した個体は、壊れたラインを先に印刷し直す）。 */
    private void callPrototypes(ServerLevel level) {
        prototypesCalled = true;
        if (awakened() && !reprinted) {
            reprinted = true;
            for (BlockPos p : lines) {
                if (!level.getBlockState(p).is(SinguloBlocks.FACTORY_LINE.get())) {
                    level.setBlock(p, lineState(p), Block.UPDATE_ALL);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.2);
                }
            }
            countLines();
        }
        playSound(SinguloSounds.get("foundry.enrage"), 2.5F, 0.8F);
        List<BlockPos> live = intactLines();
        AndroidKind[] prototypes = AndroidKind.prototypes();          // 斧型「アルマ」とビット型「ノクス」の試作機
        for (int i = 0; i < prototypes.length; i++) {
            BlockPos from = live.isEmpty() ? null : live.get(i % live.size());
            spawnAt(level, from, prototypes[i], true, getTarget());
        }
    }

    /** ラインを直すときの形（封印核が覚えた向き。分からなければ核の方を向ける）。 */
    private BlockState lineState(BlockPos p) {
        GuardianCoreBlockEntity core = core((ServerLevel) level());
        BlockState s = core != null ? core.lineStateAt(p) : null;
        return s != null ? s : SinguloBlocks.FACTORY_LINE.get().defaultBlockState();
    }

    @Nullable
    private RuinAndroid spawnAt(ServerLevel level, @Nullable BlockPos line, AndroidKind kind, boolean prototype, @Nullable LivingEntity target) {
        RuinAndroid a = kind.type().create(level);
        if (a == null) {
            return null;
        }
        Vec3 at;
        if (line != null) {
            at = Vec3.atBottomCenterOf(FactoryLineBlock.front(level.getBlockState(line), line));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, line.getX() + 0.5, line.getY() + 0.7, line.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.15);
            level.playSound(null, line, SinguloSounds.get("foundry.print"), SoundSource.HOSTILE, 1.5F, prototype ? 0.7F : 1.0F);
        } else {
            at = clampToRoom(anchor().add(random.nextDouble() * 6 - 3, 0, random.nextDouble() * 6 - 3));
        }
        a.moveTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
        a.makeFactoryMade(prototype);
        if (home != null) {
            a.restrictTo(home, arenaRadius());
        }
        a.startPrinting();
        if (target != null) {
            a.setTarget(target);
        }
        level.addFreshEntity(a);
        return a;
    }

    /** 部屋にいる、工場で印刷されたアンドロイド（量産型と試作機）。 */
    public List<RuinAndroid> factoryMade(ServerLevel level) {
        return level.getEntitiesOfClass(RuinAndroid.class, new AABB(anchor(), anchor()).inflate(arenaRadius() + 4), RuinAndroid::factoryMade);
    }

    private int massCount(ServerLevel level) {
        int n = 0;
        for (RuinAndroid a : factoryMade(level)) {
            if (!a.prototype()) {
                n++;
            }
        }
        return n;
    }

    /** 動いている製造ラインが多いほど、受けるダメージが小さい。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (emergeTicks() > 0 || RuinGuards.friendlyFire(source)) {
            return super.hurt(source, amount);
        }
        boolean bypass = source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        int n = lineCount();
        float scaled = bypass ? amount : amount * LINE_FACTOR[Mth.clamp(n, 0, LINE_FACTOR.length - 1)];
        if (n > 0 && !bypass && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 4 + n * 3, 0.6, 0.6, 0.6, 0.3);
        }
        return super.hurt(source, scaled);
    }

    @Override
    public String combatState() {
        return super.combatState() + (enraged() ? " enraged" : "") + " lines=" + lineCount();
    }

    // ------------------------------------------------------------------ 倒されたとき・去るとき

    @Override
    public void die(DamageSource source) {
        super.die(source);
        entityData.set(ARM0, 0);
        entityData.set(ARM1, 0);
        entityData.set(PRESS, 0);
        entityData.set(LASER, 0);
        if (level() instanceof ServerLevel level) {
            setConveyors(false);
            for (RuinAndroid a : factoryMade(level)) {
                a.kill();
            }
            playSound(SinguloSounds.get("foundry.line_break"), 2.5F, 0.6F);
        }
    }

    /** ふつうの落とし物に加え、部屋にいる挑戦者それぞれへ試作機の設計メモリを1つ（まだ持っていない型を先に）。 */
    @Override
    protected void dropFromLootTable(DamageSource source, boolean recentlyHit) {
        super.dropFromLootTable(source, recentlyHit);
        if (isMinion() || !(level() instanceof ServerLevel level)) {
            return;
        }
        List<Player> players = rewardPlayers(level);
        if (players.isEmpty()) {
            AndroidKind[] all = AndroidKind.prototypes();
            spawnAtLocation(PrototypeMemories.stack(all[random.nextInt(all.length)]));
            return;
        }
        for (Player p : players) {
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                spawnAtLocation(PrototypeMemories.stack(PrototypeMemories.next(sp, random)));
            }
        }
    }

    /** 倒されたときの演出: 単眼がちらついて消え、火花と煙を噴きながらコアが沈み、最後に弾ける。 */
    @Override
    protected void onDeathTick(ServerLevel level, int t) {
        if (t % 3 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 8, 0.8, 0.8, 0.8, 0.25);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.6, getZ(), 2, 0.5, 0.3, 0.5, 0.02);
        }
        if (t == DEATH_TICKS * 9 / 10) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0, getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, blockPosition(), SinguloSounds.get("foundry.death"), SoundSource.HOSTILE, 3.0F, 0.8F);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            setConveyors(false);
            if (reason == RemovalReason.DISCARDED && level() instanceof ServerLevel level) {
                for (RuinAndroid a : factoryMade(level)) {
                    a.discard();                         // 挑戦者がいなくなって戻るとき: 印刷したアンドロイドも片付ける
                }
            }
        }
        super.remove(reason);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SinguloSounds.get("foundry.hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("lines", writeList(lines));
        tag.put("conveyors", writeList(conveyors));
        tag.putBoolean("conveyors_on", conveyorsOn);
        tag.putBoolean("prototypes", prototypesCalled);
        tag.putBoolean("reprinted", reprinted);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readList(tag.getList("lines", Tag.TAG_COMPOUND), lines);
        readList(tag.getList("conveyors", Tag.TAG_COMPOUND), conveyors);
        conveyorsOn = tag.getBoolean("conveyors_on");
        prototypesCalled = tag.getBoolean("prototypes");
        reprinted = tag.getBoolean("reprinted");
    }

    private static ListTag writeList(List<BlockPos> list) {
        ListTag out = new ListTag();
        for (BlockPos p : list) {
            out.add(NbtUtils.writeBlockPos(p));
        }
        return out;
    }

    private static void readList(ListTag in, List<BlockPos> out) {
        out.clear();
        for (Tag t : in) {
            CompoundTag c = new CompoundTag();
            c.put("p", t);
            io.github.genichimaruo.singulo.compat.Legacy.readBlockPos(c, "p").ifPresent(out::add);
        }
    }
}
