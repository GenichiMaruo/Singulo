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
import net.minecraft.world.level.block.state.BlockState;
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
    /** 裁きの光の狙い（挑戦者ひとりにつき1本。x, y, z を並べた float のビット列）。 */
    private static final EntityDataAccessor<net.minecraft.nbt.CompoundTag> JUDGE_POINTS = SynchedEntityData.defineId(LastArchivist.class,
            EntityDataSerializers.COMPOUND_TAG);
    /** 裁きの光の狙いの数の上限（大人数でも重くなりすぎないように）。 */
    public static final int JUDGE_MAX = 16;
    /** 部屋の中心（封印コンソール）。部屋全体の演出をクライアントで描くのに使う。 */
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> HOME = SynchedEntityData.defineId(LastArchivist.class,
            EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** 力場: 張ってからの tick（正）、消え始めてからの tick（負）、0 なら張っていない。 */
    private static final EntityDataAccessor<Integer> SHIELD = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    /** 力場に誰かがぶつかった光が残る tick。 */
    private static final EntityDataAccessor<Integer> SHIELD_HIT = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    /** 重力潮汐の残り tick（TIDE_LOW より多い間は浮上、そのあと低重力）。 */
    private static final EntityDataAccessor<Integer> TIDE = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    /** ブラックホール爆弾を吸い込んで回復している残り tick（演出用）。 */
    private static final EntityDataAccessor<Integer> ABSORB = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    /** 光輪の断罪: それぞれの輪が高いか（ビット）。翼の審判: 翼の向きの始まり（ラジアン）。どちらも見た目にも使う。 */
    private static final EntityDataAccessor<Integer> HALO_PATTERN = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WING_ANGLE = SynchedEntityData.defineId(LastArchivist.class, EntityDataSerializers.FLOAT);

    public static final int NONE = 0, JUDGMENT = 1, VERDICT = 2, RECALL_ECHO = 3, RECALL_GRAVITY = 4, RECALL_HORIZON = 5, NOVA = 6, GRAVITY_TIDE = 7, HALO_SWEEP = 8, WINGS = 9;
    /**
     * 光輪の断罪（追憶・終焉）: 光輪が水平の輪になって部屋の縁まで広がる。HALO_RINGS 個が HALO_GAP おきに続き、
     * 低い輪（床から HALO_LOW）は跳んで越え、高い輪（床から HALO_HIGH）はしゃがんでくぐる。
     */
    public static final int HALO_WINDUP = 24, HALO_GAP = 18, HALO_RINGS = 3;
    public static final double HALO_SPEED = 0.5, HALO_LOW = 0.45, HALO_HIGH = 1.62;
    public static final float HALO_DAMAGE = 12, HALO_DAMAGE_NORMAL = 9;
    /**
     * 必殺技「翼の審判」（終焉）: 部屋の中央へ昇り、光の翼6枚を広げてゆっくり回す。床は光に満ち、翼の真下だけが影になる。
     * WINGS_CHARGE tick 後、影（翼の向きから ±WINGS_SHADOW 度）の外にいる相手へ光が降り注ぐ。
     */
    public static final int WINGS_CHARGE = 140, WINGS_TICKS = 170, WINGS_INTERVAL = 800;
    public static final float WINGS_TURN_DEG = 0.55F, WINGS_SHADOW = 11F;
    public static final float WINGS_DAMAGE = 40, WINGS_DAMAGE_NORMAL = 30;
    /** 裁きの光は、撃つこの tick 前まで相手を追い、そこで狙いを決める（音で分かる）。 */
    public static final int JUDGE_LOCK = 8;

    /** 体力の既定値（設定 archivistHealth）。 */
    public static final double HEALTH = 2400;
    /** 通常の強さの体力の倍率（archivistHealth に掛ける。ハードモードは1倍）。 */
    public static final double NORMAL_HEALTH_FACTOR = 0.85;

    /** いまの設定での体力の倍率（ハードモードは1倍）。 */
    public static double healthFactor() {
        return BossScaling.hardMode() ? 1.0 : NORMAL_HEALTH_FACTOR;
    }
    public static final int EMERGE_TICKS = 200, DEATH_TICKS = 200;
    public static final int ARENA = 18;
    /** 漂う高さ（コンソールの上面から）と、相手との間合い。 */
    /**
     * 漂う高さ（相手の足元か床から。足元の目の中へ入れて、剣が届く高さ）と、相手との間合い。
     * 重力潮汐のあいだは KEEP_DISTANCE_TIDE まで近づく（浮いていても殴れるように）。
     */
    public static final double HOVER = 1.4, KEEP_DISTANCE = 7, KEEP_DISTANCE_TIDE = 2.5;
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
    /** ボス部屋を包む力場の半径（部屋の中心＝コンソールの2ブロック下から）。最終実験施設のドームの内側に収まる。 */
    public static final double SHIELD_RADIUS = 15.0;
    /** 力場が張られる・消えるまでの tick。 */
    public static final int SHIELD_RISE = 40;
    /** 重力潮汐: 浮上の tick（終焉では長い）、そのあとの低重力の tick、終わってからも落下ダメージを受けない tick。 */
    public static final int TIDE_LIFT = 40, TIDE_LIFT_FINAL = 55, TIDE_LOW = 120, TIDE_GRACE = 50;
    /** 低重力のときの重力の倍率。 */
    public static final double TIDE_GRAVITY = 0.15;
    static final net.minecraft.resources.ResourceLocation TIDE_GRAVITY_ID = Singulo.id("archivist_tide_gravity");
    static final net.minecraft.resources.ResourceLocation TIDE_FALL_ID = Singulo.id("archivist_tide_fall");
    /** 守る床（コンソールの3ブロック下の段）の半径。最終実験施設の床の円に合わせる。 */
    public static final double FLOOR_RADIUS = 15.4;
    /** ブラックホール爆弾を吸い込んだとき回復する、最大体力の割合。 */
    public static final float BOMB_HEAL = 0.12F;
    /** 吸い込んで回復する演出の tick。 */
    public static final int ABSORB_TICKS = 50;
    /** 裁きの光が着いた所を中心に、まわりも焼く半径。 */
    public static final double JUDGE_SPLASH = 2.2;

    private int moveCooldown = 60;
    private int cycle;
    private int recall;
    private final List<Vec3> verdictMarks = new ArrayList<>();
    private final Set<UUID> ringHit = new HashSet<>();
    /** 力場を張っているか（保存する。読み込み直したら張り直す）。 */
    private boolean shieldUp;
    /** 力場の中に閉じ込めたプレイヤー（保存する。読み込み直しても、中にいた人は中のまま）。 */
    private final Set<UUID> insiders = new HashSet<>();
    /** 中にいた人を保存していない古いデータを読んだとき: 最初の一度だけ、力場の中にいる人をみな中の人とみなす。 */
    private boolean adoptInsiders;
    /**
     * 古いデータから続く戦い（中にいた人がわからない）。この戦いのあいだは、力場の中でログインしてきた人
     * （ログインしたばかりの人）も中の人とみなす。保存する（戻ってくる人が別々でも、何度読み込み直しても）。
     */
    private boolean legacyInsiders;
    /** ログインしたばかりとみなす tick（旧データの移行のときだけ使う）。 */
    public static final int LOGIN_GRACE = 100;
    private int bumpSoundCooldown;
    private int tideGrace;
    /** 翼の審判までの残り（終焉のときだけ減る）。 */
    private int wingsTimer = 200;
    /** 裁きの光を続けて撃ったか。 */
    private boolean judgeRepeated;
    /** 光輪の断罪: それぞれの輪に当たった相手。 */
    private final List<Set<UUID>> haloHit = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
    /** 重力潮汐で重力を変えたプレイヤー（どこへ行っても、潮汐が終われば必ず元に戻す）。 */
    private final Set<UUID> tideAffected = new HashSet<>();
    /** 床の欠けた所に置いたホログラムの床と、そこにもとあったもの（水・草など。消すときに戻す）。 */
    private final java.util.Map<BlockPos, BlockState> hologram = new java.util.LinkedHashMap<>();

    public LastArchivist(EntityType<? extends LastArchivist> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        this.xpReward = 5000;
        this.noPhysics = true;
        setNoGravity(true);
        setEffectiveHealth((ServerConfig.SPEC.isLoaded() ? ServerConfig.ARCHIVIST_HEALTH.get() : HEALTH)
                * (BossScaling.hardMode() ? 1.0 : NORMAL_HEALTH_FACTOR));
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
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, 1);
        entityData.define(MOVE, NONE);
        entityData.define(MOVE_TICKS, 0);
        entityData.define(FLASH, 0);
        entityData.define(JUDGE_POINTS, new net.minecraft.nbt.CompoundTag());
        entityData.define(HOME, java.util.Optional.empty());
        entityData.define(SHIELD, 0);
        entityData.define(SHIELD_HIT, 0);
        entityData.define(TIDE, 0);
        entityData.define(ABSORB, 0);
        entityData.define(HALO_PATTERN, 0);
        entityData.define(WING_ANGLE, 0F);
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

    @Override
    public String combatState() {
        return super.combatState() + " phase=" + phase();
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

    /** 光輪の断罪: i 番目の輪が高い輪か。 */
    public boolean haloHigh(int i) {
        return (entityData.get(HALO_PATTERN) >> i & 1) != 0;
    }

    /** 翼の審判: いまの翼の向き（ラジアン。elapsed は始まってからの tick）。 */
    public double wingAngle(float elapsed) {
        return entityData.get(WING_ANGLE) + Math.toRadians(WINGS_TURN_DEG * elapsed);
    }

    /** 床の上面の高さ（部屋の演出の基準）。 */
    public static double arenaFloor(BlockPos console) {
        return floorY(console) + 1;
    }

    /** 裁きの光を放った光が残る tick。 */
    public int flash() {
        return entityData.get(FLASH);
    }

    /** 裁きの光の狙い（挑戦者ひとりにつき1つ。1人のときは、その人と左右）。 */
    public List<Vec3> judgePoints() {
        List<Vec3> out = new ArrayList<>();
        int[] v = entityData.get(JUDGE_POINTS).getIntArray("p");
        for (int i = 0; i + 2 < v.length; i += 3) {
            out.add(new Vec3(Float.intBitsToFloat(v[i]), Float.intBitsToFloat(v[i + 1]), Float.intBitsToFloat(v[i + 2])));
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
    public void awaken(BlockPos core) {
        super.awaken(core);
        entityData.set(HOME, java.util.Optional.of(core));
    }

    /** 部屋の中心（封印コンソール）。クライアントでも分かる。 */
    public java.util.Optional<BlockPos> arenaHome() {
        return entityData.get(HOME);
    }

    /** 力場の中心（部屋の床の中央あたり）。 */
    public static Vec3 shieldCenter(BlockPos console) {
        return Vec3.atCenterOf(console).subtract(0, 2, 0);
    }

    public int shieldTicks() {
        return entityData.get(SHIELD);
    }

    public int shieldHit() {
        return entityData.get(SHIELD_HIT);
    }

    public int tideTicks() {
        return entityData.get(TIDE);
    }

    public int absorbTicks() {
        return entityData.get(ABSORB);
    }

    /** 守る床の高さ（コンソールの3ブロック下）。 */
    public static int floorY(BlockPos console) {
        return console.getY() - 3;
    }

    /** 重力潮汐で、いま浮き上がっている最中か（残り tick から）。 */
    public static boolean tideLifting(int left) {
        return left > TIDE_LOW;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && !isRemoved()) {
            tickShield(level);
            tickTide(level);
            captureBombs(level);
            if (absorbTicks() > 0) {
                entityData.set(ABSORB, absorbTicks() - 1);
            }
        }
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
        return Math.max(4, Math.round(ticks * (phase() == 3 ? 0.8F : 1F) * BossScaling.paceFactor(participants())));
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
            playSound(SinguloSounds.get("last_archivist.emerge"), 4.0F, 1.0F);
            raiseShield(level);
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
            playSound(SinguloSounds.get("last_archivist.phase"), 3.0F, 1.0F);
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
        if (phase() == 3 && move() != WINGS && wingsTimer > 0) {
            wingsTimer--;
        }
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

    /**
     * 相手と間合いを取って回り込む。高さは相手の足元（床より下へは行かない）から HOVER 上で、下の壇やコンソールには
     * めり込まない。重力潮汐のあいだは近づく。
     */
    private void drift(LivingEntity target) {
        Vec3 h = Vec3.atBottomCenterOf(home).add(0, 1 + HOVER, 0);
        Vec3 goal = h;
        if (move() == WINGS) {
            goal = shieldCenter(home).add(0, 6, 0);
        } else if (target != null) {
            Vec3 off = position().subtract(target.position()).multiply(1, 0, 1);
            off = off.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : off.normalize();
            off = off.yRot(0.01F * (phase() + 1));
            boolean tide = tideTicks() > 0 || tideGrace > 0;
            goal = target.position().add(off.scale(tide ? KEEP_DISTANCE_TIDE : KEEP_DISTANCE));
            Vec3 fromHome = goal.subtract(h).multiply(1, 0, 1);
            if (fromHome.length() > ARENA - 5) {
                goal = h.add(fromHome.normalize().scale(ARENA - 5)).add(0, goal.y - h.y, 0);
            }
            double base = Math.max(target.getY(), floorY(home) + 1);
            double y = Math.max(base, groundBelow(goal.x, goal.z, base + 3)) + HOVER + Math.sin(tickCount * 0.05) * 0.3;
            goal = new Vec3(goal.x, y, goal.z);
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
            kind = switch (step % 6) {
                case 0 -> JUDGMENT;
                case 1 -> HALO_SWEEP;
                case 2 -> GRAVITY_TIDE;
                case 3 -> VERDICT;
                default -> nextRecall();
            };
        } else if (wingsTimer <= 0) {
            kind = WINGS;
        } else {
            kind = switch (step % 6) {
                case 0 -> NOVA;
                case 1 -> JUDGMENT;
                case 2 -> GRAVITY_TIDE;
                case 3 -> nextRecall();
                case 4 -> HALO_SWEEP;
                default -> VERDICT;
            };
        }
        if (kind == GRAVITY_TIDE && tideTicks() > 0) {
            kind = JUDGMENT;
        }
        switch (kind) {
            case JUDGMENT -> startJudgment(level, target);
            case VERDICT -> startVerdict(level, target);
            case RECALL_ECHO -> startRecallEcho(level, target);
            case RECALL_GRAVITY -> startRecallGravity(level);
            case RECALL_HORIZON -> {
                setMove(RECALL_HORIZON, HORIZON_WINDUP + HORIZON_SPREAD);
                ringHit.clear();
                playSound(SinguloSounds.get("last_archivist.recall"), 3.0F, 1.0F);
            }
            case NOVA -> {
                setMove(NOVA, NOVA_CHARGE + NOVA_SPREAD);
                ringHit.clear();
                playSound(SinguloSounds.get("last_archivist.nova_charge"), 4.0F, 1.0F);
            }
            case GRAVITY_TIDE -> {
                // 部屋全体の重力をゆがめる。ゆがめている間も、ほかの技は続く
                startTide(level);
                moveCooldown = pace(30);
            }
            case HALO_SWEEP -> startHaloSweep(level);
            case WINGS -> startWings(level);
            default -> { }
        }
    }

    // ---- 光輪の断罪

    private void startHaloSweep(ServerLevel level) {
        int pattern = 0;
        for (int i = 0; i < HALO_RINGS; i++) {
            if (random.nextBoolean()) {
                pattern |= 1 << i;
            }
        }
        // 3つとも同じ高さにはしない
        if (pattern == 0 || pattern == (1 << HALO_RINGS) - 1) {
            pattern ^= 1 << random.nextInt(HALO_RINGS);
        }
        entityData.set(HALO_PATTERN, pattern);
        for (int i = 0; i < HALO_RINGS; i++) {
            haloHit.get(i).clear();
        }
        setMove(HALO_SWEEP, haloTicks());
        actionBar(level, "gui.singulo.archivist.halo");
        playSound(SinguloSounds.get("last_archivist.recall"), 4.0F, 1.4F);
    }

    /** 光輪の断罪の長さ（最後の輪が部屋の縁に届くまで）。 */
    public static int haloTicks() {
        return HALO_WINDUP + (HALO_RINGS - 1) * HALO_GAP + (int) Math.ceil((SHIELD_RADIUS + 1) / HALO_SPEED);
    }

    /** i 番目の輪の半径（まだ出ていなければ負）。elapsed は始まってからの tick。 */
    public static double haloRadius(int i, float elapsed) {
        return (elapsed - HALO_WINDUP - i * HALO_GAP) * HALO_SPEED;
    }

    private void tickHaloSweep(ServerLevel level, int left) {
        if (home == null) {
            return;
        }
        float elapsed = haloTicks() - left;
        Vec3 c = shieldCenter(home);
        double floor = arenaFloor(home);
        float damage = BossScaling.hardMode() ? HALO_DAMAGE : HALO_DAMAGE_NORMAL;
        for (int i = 0; i < HALO_RINGS; i++) {
            double r = haloRadius(i, elapsed);
            if (r < 0 || r > SHIELD_RADIUS + 1) {
                continue;
            }
            if (Math.abs(r - Math.round(r)) < HALO_SPEED / 2 && (int) Math.round(r) % 4 == 0) {
                playSound(SinguloSounds.get("last_archivist.judgment_fire"), 1.5F, 1.6F + i * 0.1F);
            }
            double y = floor + (haloHigh(i) ? HALO_HIGH : HALO_LOW);
            for (Player p : challengers(level)) {
                double d = Math.hypot(p.getX() - c.x, p.getZ() - c.z);
                net.minecraft.world.phys.AABB box = p.getBoundingBox();
                if (Math.abs(d - r) > 0.7 || y < box.minY || y > box.maxY || !haloHit.get(i).add(p.getUUID())) {
                    continue;
                }
                hurtWith("halo_sweep", p, SinguloDamageTypes.tidal(level, this), damage);
                Vec3 k = p.position().subtract(c).multiply(1, 0, 1);
                if (k.lengthSqr() > 1e-4) {
                    k = k.normalize();
                    p.push(k.x * 0.8, 0.3, k.z * 0.8);
                    p.hurtMarked = true;
                }
            }
        }
    }

    // ---- 翼の審判

    private void startWings(ServerLevel level) {
        wingsTimer = pace(WINGS_INTERVAL);
        entityData.set(WING_ANGLE, (float) (random.nextDouble() * Math.PI * 2));
        setMove(WINGS, WINGS_TICKS);
        showTitle(level, "gui.singulo.archivist.wings", "gui.singulo.archivist.wings_hint");
        playSound(SinguloSounds.get("last_archivist.phase"), 5.0F, 0.6F);
    }

    /** 相手が翼の影（翼の向きから ±WINGS_SHADOW 度）の中にいるか。 */
    private boolean inWingShadow(Player p, Vec3 c, float elapsed) {
        double a = Math.atan2(p.getZ() - c.z, p.getX() - c.x);
        double base = wingAngle(elapsed);
        for (int w = 0; w < 6; w++) {
            double diff = Mth.wrapDegrees(Math.toDegrees(a - base) - w * 60.0);
            if (Math.abs(diff) <= WINGS_SHADOW) {
                return true;
            }
        }
        return Math.hypot(p.getX() - c.x, p.getZ() - c.z) < 1.5;     // 真下も影
    }

    private void tickWings(ServerLevel level, int left) {
        if (home == null) {
            return;
        }
        float elapsed = WINGS_TICKS - left;
        Vec3 c = shieldCenter(home);
        if (elapsed % 20 == 0 && elapsed < WINGS_CHARGE) {
            playSound(SinguloSounds.get("last_archivist.judgment_aim"), 4.0F, 0.5F + elapsed / WINGS_CHARGE);
        }
        if (elapsed == WINGS_CHARGE - 30) {
            playSound(SinguloSounds.get("last_archivist.nova_charge"), 5.0F, 0.8F);
        }
        if (elapsed == WINGS_CHARGE) {
            playSound(SinguloSounds.get("last_archivist.nova"), 6.0F, 0.7F);
            level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                    net.minecraft.sounds.SoundSource.HOSTILE, 5.0F, 0.6F);
            float damage = BossScaling.hardMode() ? WINGS_DAMAGE : WINGS_DAMAGE_NORMAL;
            for (Player p : challengers(level)) {
                if (inWingShadow(p, c, elapsed)) {
                    continue;
                }
                hurtWith("wings", p, SinguloDamageTypes.tidal(level, this), damage);
                p.push(0, 0.6, 0);
                p.hurtMarked = true;
            }
        }
    }

    /** 挑戦者の画面に大きな文字（題と、よけ方の手がかり）を出す。 */
    private void showTitle(ServerLevel level, String title, String hint) {
        for (Player p : challengers(level)) {
            if (p instanceof ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 80, 15));
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable(hint)));
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable(title)));
            }
        }
    }

    /** 挑戦者のホットバーの上に文字を出す。 */
    private void actionBar(ServerLevel level, String key) {
        Component msg = Component.translatable(key);
        for (Player p : challengers(level)) {
            p.displayClientMessage(msg, true);
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
            case HALO_SWEEP -> tickHaloSweep(level, left);
            case WINGS -> tickWings(level, left);
            default -> { }
        }
        if (move() == JUDGMENT && left > JUDGE_LOCK) {
            // 撃つ直前まで相手を追う
            aimJudgment(level, target);
        } else if (move() == JUDGMENT && left == JUDGE_LOCK) {
            playSound(SinguloSounds.get("last_archivist.judgment_aim"), 3.0F, 1.8F);
        }
        if (left <= 0) {
            if (move() == JUDGMENT && phase() >= 2 && !judgeRepeated) {
                // 追憶からは2度続けて撃つ
                judgeRepeated = true;
                setMove(JUDGMENT, pace(JUDGE_WINDUP * 2 / 3));
                playSound(SinguloSounds.get("last_archivist.judgment_aim"), 3.0F, 1.2F);
            } else {
                judgeRepeated = false;
                setMove(NONE, 0);
            }
        }
    }

    // ---- 裁きの光

    /** 光輪の放射が相手を狙う（相手が1人なら、今の位置と、動いた先の左右）。狙いは始めたときに決まる。 */
    public void startJudgment(ServerLevel level, LivingEntity target) {
        aimJudgment(level, target);
        setMove(JUDGMENT, pace(JUDGE_WINDUP));
        playSound(SinguloSounds.get("last_archivist.judgment_aim"), 3.0F, 1.0F);
    }

    /** 裁きの光の狙いを、いまの挑戦者の位置に合わせる（全員に1本ずつ。相手が1人なら、その左右も）。 */
    private void aimJudgment(ServerLevel level, @javax.annotation.Nullable LivingEntity target) {
        List<Vec3> points = new ArrayList<>();
        List<Player> players = challengers(level);
        for (Player p : players) {
            if (points.size() < JUDGE_MAX) {
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
            if (target == null) {
                return;
            }
            points.add(target.getBoundingBox().getCenter());
        }
        int[] v = new int[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            v[i * 3] = Float.floatToIntBits((float) p.x);
            v[i * 3 + 1] = Float.floatToIntBits((float) p.y);
            v[i * 3 + 2] = Float.floatToIntBits((float) p.z);
        }
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putIntArray("p", v);
        entityData.set(JUDGE_POINTS, tag);
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
                    hurtWith("judgment", e, damageSources().indirectMagic(this, this), JUDGE_DAMAGE);
                }
            }
            for (int i = 0; i <= 20; i++) {
                Vec3 q = from.lerp(end, i / 20.0);
                level.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.02);
            }
            level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
            // 着いた所: 光が弾けて、まわりも焼く
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(JUDGE_SPLASH),
                    e -> e.isAlive() && e != this && !RuinGuards.isGuard(e) && e.distanceToSqr(end) <= JUDGE_SPLASH * JUDGE_SPLASH)) {
                hurtWith("judgment_splash", e, damageSources().indirectMagic(this, this), JUDGE_DAMAGE * 0.6F);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 2, 0.4, 0.2, 0.4, 0);
            level.sendParticles(ParticleTypes.END_ROD, end.x, end.y + 0.2, end.z, 40, 0.2, 0.1, 0.2, 0.35);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y + 0.2, end.z, 30, 1.2, 0.1, 1.2, 0.3);
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
            if (left % 2 == 0) {
                double top = m.y + 2 + 10 * Math.max(0, left) / Math.max(1.0, VERDICT_WINDUP);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x, top, m.z, 4, 0.25, 0.4, 0.25, 0.02);
            }
            if (left <= 0) {
                level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.5, m.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.SQUID_INK, m.x, m.y + 0.5, m.z, 20, 1.0, 0.2, 1.0, 0.05);
            }
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
            playSound(SinguloSounds.get("last_archivist.collapse"), 3.0F, 1.0F);
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
        playSound(SinguloSounds.get("last_archivist.recall"), 3.0F, 1.3F);                 // 残響の番人の記録（高め）
        playSound(SinguloSounds.get("echo_sentinel.strike_windup"), 2.0F, 0.8F);
    }

    private void echoStrike(ServerLevel level, LivingEntity target) {
        playSound(SinguloSounds.get("echo_sentinel.strike"), 2.5F, 0.8F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.5, getZ(), 8, 2.0, 0.5, 2.0, 0);
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI / 12;
            level.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * 3.5, getY() + 1.4, getZ() + Math.sin(a) * 3.5,
                    0, Math.cos(a), 0.05, Math.sin(a), 0.4);
        }
        for (Player p : challengers(level)) {
            if (p.distanceTo(this) <= 4.5 && hurtWith("echo_strike", p, damageSources().mobAttack(this), ECHO_DAMAGE)) {
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
        playSound(SinguloSounds.get("last_archivist.recall"), 3.0F, 0.9F);                 // 重力の澱の記録（低め）
        playSound(SinguloSounds.get("gravity_remnant.lift"), 2.5F, 0.8F);
    }

    private void gravitySlam(ServerLevel level) {
        for (Player p : challengers(level)) {
            if (p.distanceTo(this) <= 20 && !p.onGround()) {
                p.setDeltaMovement(p.getDeltaMovement().x, -3.0, p.getDeltaMovement().z);
                p.fallDistance = Math.max(p.fallDistance, 8);
                BossCombatLog.slammed(p, this, "gravity_slam_fall");
                p.hurtMarked = true;
            }
        }
        playSound(SinguloSounds.get("gravity_remnant.slam"), 2.5F, 0.8F);
        if (home != null) {
            Vec3 f = shieldCenter(home);
            for (int k = 0; k < 32; k++) {
                double a = k * Math.PI / 16;
                level.sendParticles(ParticleTypes.SQUID_INK, f.x + Math.cos(a) * 6, f.y, f.z + Math.sin(a) * 6, 2, 0.4, 0.1, 0.4, 0.05);
            }
        }
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
                hurtWith("horizon_ring", p, damageSources().mobAttack(this), HORIZON_DAMAGE);
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
                hurtWith("event_horizon", p, SinguloDamageTypes.tidal(level, this), CORE_DAMAGE);
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
            playSound(SinguloSounds.get("last_archivist.nova"), 5.0F, 1.0F);
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 200, 0.3, 0.3, 0.3, 0.9);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        double r = novaRadius(left);
        for (Player p : challengers(level)) {
            double d = p.position().add(0, 1, 0).distanceTo(c);
            if (Math.abs(d - r) < 1.5 && ringHit.add(p.getUUID())) {
                hurtWith("nova", p, damageSources().indirectMagic(this, this), NOVA_DAMAGE);
                Vec3 k = p.position().subtract(c).normalize();
                p.push(k.x, 0.5, k.z);
                p.hurtMarked = true;
            }
        }
    }

    // ---- ボス部屋の力場

    /** 部屋を力場で閉ざす（出現の最初）。中にいるプレイヤーは倒すまで出られず、外からは入れない。 */
    private void raiseShield(ServerLevel level) {
        if (home == null) {
            return;
        }
        shieldUp = true;
        Vec3 c = shieldCenter(home);
        ArenaShield.raise(getUUID(), level, c, SHIELD_RADIUS, floorY(home), FLOOR_RADIUS);
        fillFloor(level);
        insiders.clear();
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(SHIELD_RADIUS))) {
            if (!ArenaShield.exempt(p) && p.position().add(0, p.getBbHeight() / 2, 0).distanceTo(c) < SHIELD_RADIUS) {
                insiders.add(p.getUUID());
            }
        }
        entityData.set(SHIELD, 1);
        level.playSound(null, home, SinguloSounds.get("guardian_core.awaken"), SoundSource.HOSTILE, 3.0F, 0.7F);
        broadcastLine(level, "gui.singulo.archivist.shield_up");
    }

    /** 力場を消す（倒されたとき・挑戦者がいなくなったとき・いなくなったとき）。 */
    private void lowerShield(boolean announce) {
        if (!shieldUp) {
            return;
        }
        shieldUp = false;
        ArenaShield.lower(getUUID());
        insiders.clear();
        legacyInsiders = false;
        entityData.set(SHIELD, -1);
        if (announce && level() instanceof ServerLevel level && home != null) {
            level.playSound(null, home, SinguloSounds.get("vault.unseal"), SoundSource.HOSTILE, 3.0F, 0.6F);
            broadcastLine(level, "gui.singulo.archivist.shield_down");
        }
    }

    private void tickShield(ServerLevel level) {
        int s = shieldTicks();
        if (shieldUp && home != null) {
            if (!ArenaShield.active(getUUID())) {
                ArenaShield.raise(getUUID(), level, shieldCenter(home), SHIELD_RADIUS, floorY(home), FLOOR_RADIUS);      // 読み込み直したとき
            }
            if (s < SHIELD_RISE + 2) {
                entityData.set(SHIELD, Math.max(1, s + 1));
            }
            holdShield(level);
            if (tickCount % 20 == 0) {
                fillFloor(level);
            }
        } else if (s < 0) {
            entityData.set(SHIELD, s <= -SHIELD_RISE ? 0 : s - 1);
        }
        if (shieldHit() > 0) {
            entityData.set(SHIELD_HIT, shieldHit() - 1);
        }
        if (bumpSoundCooldown > 0) {
            bumpSoundCooldown--;
        }
    }

    /** 力場の内と外を分ける: 中から出ようとするプレイヤーは押し戻し、外から入ろうとするプレイヤーは押し返す。 */
    private void holdShield(ServerLevel level) {
        Vec3 c = shieldCenter(home);
        double r = SHIELD_RADIUS;
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r + 4))) {
            if (ArenaShield.exempt(p) || !p.isAlive()) {
                continue;
            }
            Vec3 body = p.position().add(0, p.getBbHeight() / 2, 0);
            Vec3 off = body.subtract(c);
            double d = off.length();
            if (d < 1e-3) {
                continue;
            }
            Vec3 n = off.scale(1 / d);
            boolean in = insiders.contains(p.getUUID());
            boolean adopt = d < r && (adoptInsiders || legacyInsiders && p.tickCount < LOGIN_GRACE);
            if (!in && (d < r - 1.5 || adopt)) {
                insiders.add(p.getUUID());              // 中で目を覚ました（読み込み直しなど）
                in = true;
            }
            if (in && d > r - 0.8) {
                if (d > r) {
                    Vec3 back = c.add(n.scale(r - 1.0)).subtract(0, p.getBbHeight() / 2, 0);
                    p.teleportTo(back.x, back.y, back.z);
                }
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.subtract(n.scale(Math.max(0, v.dot(n)) + 0.3)));
                p.hurtMarked = true;
                bump(level, c.add(n.scale(r)));
            } else if (!in && d < r + 0.8) {
                if (d < r) {
                    Vec3 out = c.add(n.scale(r + 1.0)).subtract(0, p.getBbHeight() / 2, 0);
                    p.teleportTo(out.x, out.y, out.z);
                }
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.add(n.scale(Math.max(0, -v.dot(n)) + 0.3)));
                p.hurtMarked = true;
                bump(level, c.add(n.scale(r)));
            }
        }
        adoptInsiders = false;
    }

    private void bump(ServerLevel level, Vec3 at) {
        entityData.set(SHIELD_HIT, 10);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.08);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.2);
        if (bumpSoundCooldown <= 0) {
            bumpSoundCooldown = 10;
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.2F, 1.6F);
        }
    }

    // ---- 床

    /** ボス部屋の床の欠けた所（空気・水・草など置き換えられるもの）を、ホログラムの床で埋める。 */
    private void fillFloor(ServerLevel level) {
        if (home == null) {
            return;
        }
        BlockState holo = io.github.genichimaruo.singulo.registry.SinguloBlocks.ARENA_HOLOGRAM.get().defaultBlockState();
        int r = (int) Math.ceil(FLOOR_RADIUS);
        boolean placed = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > FLOOR_RADIUS * FLOOR_RADIUS) {
                    continue;
                }
                BlockPos pos = home.offset(dx, -3, dz);
                BlockState state = level.getBlockState(pos);
                if (!state.is(holo.getBlock()) && (state.isAir() || state.canBeReplaced())) {
                    hologram.putIfAbsent(pos.immutable(), state);
                    level.setBlock(pos, holo, net.minecraft.world.level.block.Block.UPDATE_ALL);
                    placed = true;
                    if (random.nextInt(3) == 0) {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                                3, 0.3, 0.02, 0.3, 0.05);
                    }
                }
            }
        }
        if (placed) {
            level.playSound(null, home, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5F, 0.6F);
        }
    }

    /** ホログラムの床を消し、もとあったもの（水・草など）に戻す（戦いが終わったとき）。いまもホログラムの所だけ戻す。 */
    private void clearFloor(ServerLevel level) {
        net.minecraft.world.level.block.Block holo = io.github.genichimaruo.singulo.registry.SinguloBlocks.ARENA_HOLOGRAM.get();
        for (var e : hologram.entrySet()) {
            BlockPos pos = e.getKey();
            if (level.getBlockState(pos).is(holo)) {
                level.setBlock(pos, e.getValue(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.05);
            }
        }
        hologram.clear();
    }

    // ---- ブラックホール爆弾を吸い込む

    /** 部屋の中に投げ込まれたブラックホール爆弾を捕らえる（着弾させず、頭の特異点へ吸い込む）。 */
    private void captureBombs(ServerLevel level) {
        if (home == null || isDeadOrDying()) {
            return;
        }
        Vec3 c = shieldCenter(home);
        for (io.github.genichimaruo.singulo.reactor.BlackHoleBomb bomb : level.getEntitiesOfClass(
                io.github.genichimaruo.singulo.reactor.BlackHoleBomb.class, new AABB(c, c).inflate(SHIELD_RADIUS + 6), b -> !b.captured())) {
            bomb.capture(this);
            level.playSound(null, bomb.blockPosition(), SinguloSounds.BLACK_HOLE_FORMATION.get(), SoundSource.HOSTILE, 1.5F, 1.9F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, bomb.getX(), bomb.getY(), bomb.getZ(), 30, 0.2, 0.2, 0.2, 0.4);
        }
    }

    /** 捕らえた爆弾を頭の特異点が飲み込み、体力が戻る（言葉ではなく、光と音で分かるように）。 */
    public void absorbBomb(ServerLevel level, io.github.genichimaruo.singulo.reactor.BlackHoleBomb bomb) {
        heal(getMaxHealth() * BOMB_HEAL);
        entityData.set(ABSORB, ABSORB_TICKS);
        Vec3 head = position().add(0, HEAD_Y, 0);
        Vec3 chest = chest();
        level.sendParticles(ParticleTypes.FLASH, head.x, head.y, head.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, head.x, head.y, head.z, 60, 0.1, 0.1, 0.1, 0.6);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, chest.x, chest.y, chest.z, 120, 0.6, 1.0, 0.6, 0.6);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, chest.x, chest.y, chest.z, 40, 0.9, 1.4, 0.9, 0.1);
        level.playSound(null, blockPosition(), SinguloSounds.BLACK_HOLE_FORMATION.get(), SoundSource.HOSTILE, 3.0F, 1.6F);
        level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 2.0F, 0.8F);
        level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5F, 0.7F);
    }

    /** (x, z) の真下の、足場の上面の高さ（from から床までを調べる。なければ床）。 */
    private double groundBelow(double x, double z, double from) {
        int floor = floorY(home) + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = (int) Math.floor(from); y >= floor; y--) {
            p.set(Mth.floor(x), y, Mth.floor(z));
            if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) {
                return y + 1;
            }
        }
        return floor;
    }

    // ---- 重力潮汐（部屋全体の重力のゆがみ）

    /** 部屋じゅうの相手を宙へ浮かせ、そのあとしばらく低重力にする（終焉では高く、長く浮かせる）。 */
    public void startTide(ServerLevel level) {
        int lift = phase() == 3 ? TIDE_LIFT_FINAL : TIDE_LIFT;
        entityData.set(TIDE, lift + TIDE_LOW);
        playSound(SinguloSounds.get("gravity_remnant.lift"), 4.0F, 0.6F);
        playSound(SinguloSounds.get("last_archivist.recall"), 3.0F, 0.7F);
        broadcastLine(level, phase() == 3 ? "gui.singulo.archivist.tide_final" : "gui.singulo.archivist.tide");
    }

    private void tickTide(ServerLevel level) {
        int left = tideTicks();
        if (left > 0) {
            entityData.set(TIDE, left - 1);
            tideGrace = TIDE_GRACE;
            boolean lifting = tideLifting(left);
            double rise = phase() == 3 ? 0.2 : 0.14;
            for (Player p : arenaPlayers(level)) {
                if (lifting) {
                    Vec3 v = p.getDeltaMovement();
                    // 浮かせるのは上向きだけ（横には動ける）
                    p.setDeltaMovement(v.x, Mth.lerp(0.25, v.y, rise), v.z);
                    p.hurtMarked = true;
                }
                p.fallDistance = 0;
                applyTide(p, !lifting, true);
            }
            releaseLeavers(level);
            if (home != null && left % 2 == 0) {
                // 床の塵と瓦礫が浮き上がる
                Vec3 f = shieldCenter(home);
                for (int i = 0; i < 6; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double r = random.nextDouble() * (SHIELD_RADIUS - 1);
                    level.sendParticles(lifting ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, f.x + Math.cos(a) * r, f.y,
                            f.z + Math.sin(a) * r, 0, 0, 1, 0, lifting ? 0.25 : 0.08);
                }
            }
            if (left == TIDE_LOW + 1) {
                playSound(SinguloSounds.get("last_archivist.verdict"), 3.0F, 0.6F);
            }
        } else if (tideGrace > 0) {
            tideGrace--;
            if (tideGrace > 0) {
                for (Player p : arenaPlayers(level)) {
                    applyTide(p, false, true);
                }
                releaseLeavers(level);
            } else {
                releaseAllTide(level);
            }
        }
    }

    /** 部屋から出た人の重力を元に戻す（潮汐を受けたまま外へ出ても、低重力・落下無効を持ち出さない）。 */
    private void releaseLeavers(ServerLevel level) {
        if (tideAffected.isEmpty()) {
            return;
        }
        Set<UUID> inArena = new HashSet<>();
        for (Player p : arenaPlayers(level)) {
            inArena.add(p.getUUID());
        }
        tideAffected.removeIf(id -> {
            if (inArena.contains(id)) {
                return false;
            }
            Player p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                setTideModifiers(p, false, false);
            }
            return true;
        });
    }

    /** 重力を変えたすべての人を、どこにいても元に戻す。 */
    private void releaseAllTide(ServerLevel level) {
        for (UUID id : tideAffected) {
            Player p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                setTideModifiers(p, false, false);
            }
        }
        tideAffected.clear();
    }

    /** 重力潮汐を止め、プレイヤーの重力を元に戻す。 */
    private void clearTide(ServerLevel level) {
        entityData.set(TIDE, 0);
        tideGrace = 0;
        releaseAllTide(level);
        Vec3 c = home == null ? position() : shieldCenter(home);
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(SHIELD_RADIUS + 16))) {
            setTideModifiers(p, false, false);
        }
    }

    /** 力場の中のプレイヤー（張っている間は中に閉じ込めた人だけ。張っていなければ部屋の広さの中）。 */
    private List<Player> arenaPlayers(ServerLevel level) {
        Vec3 c = home == null ? position() : shieldCenter(home);
        double r = SHIELD_RADIUS + 2;
        return level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r),
                p -> !ArenaShield.exempt(p) && p.isAlive() && p.position().distanceToSqr(c) <= r * r
                        && (!shieldUp || insiders.contains(p.getUUID())));
    }

    /** 最後の記録者は、力場の中の全員が倒れたら、すぐ（5秒で）戦いを終える（ほかの番人のような猶予はない）。 */
    @Override
    protected int resetChecks() {
        return 5;
    }

    /**
     * 強さと、戦いを続けるかに数える挑戦者。力場を張っている間は、中に閉じ込めた人だけを数える
     * （外で待つ人は強さにも報酬にも入らず、中の全員が倒れれば戦いは終わる）。
     */
    @Override
    protected int countChallengers(ServerLevel level) {
        if (!shieldUp || home == null) {
            return super.countChallengers(level);
        }
        Vec3 c = shieldCenter(home);
        double r = SHIELD_RADIUS + 1;
        int n = 0;
        for (UUID id : insiders) {
            if (level.getEntity(id) instanceof Player p && challenger(p) && p.position().distanceToSqr(c) <= r * r) {
                n++;
            }
        }
        return n;
    }

    /** 潮汐の効果を付け、付けた人を覚える（あとで、どこにいても必ず外せるように）。効果を付けるときはいつもこれを使う。 */
    private void applyTide(Player p, boolean low, boolean safe) {
        setTideModifiers(p, low, safe);
        if (low || safe) {
            tideAffected.add(p.getUUID());
        }
    }

    private static void setTideModifiers(Player p, boolean low, boolean safe) {
        setModifier(p.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get()), TIDE_GRAVITY_ID, low, TIDE_GRAVITY - 1);
        setModifier(p.getAttribute(io.github.genichimaruo.singulo.compat.LegacyAttributes.FALL.get()), TIDE_FALL_ID, safe, -1);
    }

    private static void setModifier(@javax.annotation.Nullable net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,
                                    net.minecraft.resources.ResourceLocation id, boolean on, double amount) {
        if (attribute == null) {
            return;
        }
        boolean has = attribute.getModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(id)) != null;
        if (on && !has) {
            attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(id), id.toString(), amount,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!on && has) {
            attribute.removeModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(id));
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // どんな形でいなくなっても、力場と重力のゆがみは残さない（チャンクの読み込み直しなら、戻ったときに張り直す）
        ArenaShield.lower(getUUID());
        if (level() instanceof ServerLevel level) {
            clearTide(level);
            if (reason.shouldDestroy()) {
                clearFloor(level);                     // 倒されて演出が終わったとき・消されたとき（チャンクの読み込み直しでは残す）
            }
        }
        super.remove(reason);
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
        lowerShield(false);
        clearTide(level);
        clearFloor(level);
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
        lowerShield(true);
        if (level() instanceof ServerLevel level) {
            clearTide(level);
            playSound(SinguloSounds.get("last_archivist.death"), 4.0F, 1.0F);
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

    /** 報酬: 記録者の光輪、残光の翼、オーバークロック・チップ、H凝縮体。近くの挑戦者は最後の記録を読めるようになる。 */
    private void dropRewards(ServerLevel level) {
        // 大勢で挑んだら、光輪と翼も人数ぶん（それぞれが持ち帰れるように）
        for (int roll = 0; roll < BossScaling.lootRolls(rewardParticipants()); roll++) {
            for (String[] r : new String[][] {{"archivist_halo", "1"}, {"afterglow_wings", "1"}, {"overclock_chip", "4"}, {"hawking_condensate", "3"}}) {
                Item item = BuiltInRegistries.ITEM.get(Singulo.id(r[0]));
                spawnAtLocation(new ItemStack(item, Integer.parseInt(r[1])), (float) HEAD_Y);
            }
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
        tag.putBoolean("shield", shieldUp);
        net.minecraft.nbt.ListTag floor = new net.minecraft.nbt.ListTag();
        hologram.forEach((pos, was) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("pos", pos.asLong());
            e.put("was", net.minecraft.nbt.NbtUtils.writeBlockState(was));
            floor.add(e);
        });
        tag.put("floor", floor);
        net.minecraft.nbt.ListTag inside = new net.minecraft.nbt.ListTag();
        for (UUID id : insiders) {
            inside.add(net.minecraft.nbt.NbtUtils.createUUID(id));
        }
        tag.put("insiders", inside);
        tag.putBoolean("legacy_insiders", legacyInsiders);
        tag.putInt("tide", tideTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, Math.max(1, tag.getInt("phase")));
        cycle = tag.getInt("cycle");
        if (home != null) {
            entityData.set(HOME, java.util.Optional.of(home));
        }
        shieldUp = tag.getBoolean("shield");
        hologram.clear();
        var blocks = level().holderLookup(net.minecraft.core.registries.Registries.BLOCK);
        for (net.minecraft.nbt.Tag t : tag.getList("floor", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            hologram.put(BlockPos.of(e.getLong("pos")), net.minecraft.nbt.NbtUtils.readBlockState(blocks, e.getCompound("was")));
        }
        for (long l : tag.getLongArray("hologram")) {
            // 以前の版の保存（もとのものを覚えていない）: 空気に戻す
            hologram.putIfAbsent(BlockPos.of(l), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        insiders.clear();
        if (tag.contains("insiders")) {
            for (net.minecraft.nbt.Tag t : tag.getList("insiders", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                insiders.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
            }
        }
        adoptInsiders = tag.getBoolean("shield") && !tag.contains("insiders");
        legacyInsiders = adoptInsiders || tag.getBoolean("legacy_insiders");
        entityData.set(SHIELD, shieldUp ? SHIELD_RISE + 2 : 0);
        entityData.set(TIDE, tag.getInt("tide"));
        int p = phase();
        bossBar.setColor(p == 1 ? BossEvent.BossBarColor.YELLOW : p == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.WHITE);
    }

    /** 召喚の位置（コンソールの上）。 */
    public static Vec3 spawnAt(BlockPos console) {
        return Vec3.atBottomCenterOf(console).add(0, 1 + HOVER, 0);
    }
}
