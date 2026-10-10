package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * ホライズン・ウォーデン。最終実験施設の守護機で、封印コンソールに触れると起動する（自然には湧かない）。
 * 技はフェーズごとの候補から順に選ぶ（同じ技を続けない）。どの技も、満タンのプレイヤーを一撃では倒さない強さ。
 * <ul>
 *   <li>フェーズ1（HP 2/3 以上）: 白い外装（防御+10）。近接攻撃、扇状の3連光弾、薙ぎ払い（大きな三日月と星）。
 *       離れたり隠れたりすると背後へ跳ぶ</li>
 *   <li>フェーズ2: 外装が剥がれて破片が周りを回る。重力攻撃（引き寄せる／浮かせて叩き落とす）、破片からの追尾光弾、
 *       胸からのレーザー（ゆっくり追ってくる）、重力の手（持ち上げて振り回し、叩き落とす）、衝撃波（跳べばよけられる）</li>
 *   <li>フェーズ3（HP 1/3 未満）: 宙に浮く。小型の特異点、事象の地平線ビーム（ためて狙いを固定してから撃つ。防具を無視）</li>
 * </ul>
 * 挑戦者が死ぬか施設から離れると、全回復して封印に戻る（wardenResetOnLeave）。倒すと保管庫の力場が消える。
 */
public class HorizonWarden extends Monster {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.INT);
    /** ビームのため（残り tick。0 ならためていない）。 */
    private static final EntityDataAccessor<Integer> BEAM_CHARGE = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.INT);
    /** ビームを撃った光（残り tick）。 */
    private static final EntityDataAccessor<Integer> BEAM_FLASH = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.INT);
    /** ビームの終点（ワールド座標）。 */
    private static final EntityDataAccessor<Vector3f> BEAM_END = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.VECTOR3);
    /** 薙ぎ払い（残り tick。SWEEP_STRIKE より大きい間は振りかぶり、そのあとが振り抜き）。 */
    private static final EntityDataAccessor<Integer> SWEEP = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.INT);
    /** 胸のレーザー（残り tick）と、その終点。 */
    private static final EntityDataAccessor<Integer> LASER = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> LASER_END = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.VECTOR3);
    /** 重力の手（残り tick）と、つかんでいる相手の ID（-1 ならいない）。 */
    private static final EntityDataAccessor<Integer> GRIP = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GRIP_TARGET = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.INT);
    /** 出現の演出の残り tick（0 なら出現済み）。 */
    private static final EntityDataAccessor<Integer> EMERGE = SynchedEntityData.defineId(HorizonWarden.class, EntityDataSerializers.INT);
    /** 出現の演出の長さ: 特異点が開き（〜30%）、体が組み上がり（〜75%）、目覚めの衝撃波（〜100%）。 */
    public static final int EMERGE_TICKS = 120;
    public static final int DEATH_TICKS = 100;
    /** 体力の既定値（設定 wardenHealth）。 */
    public static final double HEALTH = 1200;
    /** ゲームの決まりの最大体力の上限。 */
    static final double MAX_HEALTH_CAP = 1024;
    /** フェーズごとの、技のあいだの間の倍率（フェーズが進むほど短い）。 */
    static final float[] PACE = {0.7F, 0.5F, 0.35F};
    /** フェーズごとの、技と技のあいだの最短の間（tick）。 */
    static final int[] MIN_GAP = {22, 12, 7};

    public static final double ARMOR_PHASE_1 = 10;
    public static final int ARENA_RADIUS = 24;
    /** 挑戦者がいない判定（20 tick ごと）がこの回数続くと封印に戻る。 */
    static final int RESET_CHECKS = 5;
    static final int BOLT_INTERVAL = 40;
    /** 扇状に撃つ光弾の数と、隣どうしの角度。 */
    public static final int VOLLEY = 3;
    static final float VOLLEY_SPREAD = 12;
    static final int GRAVITY_INTERVAL = 60;
    /** 浮かせてから叩き落とすまでの tick。 */
    static final int SLAM_DELAY = 14;
    /** 衝撃波: 広がる速さ（1 tick あたりのブロック）、最大の半径、ダメージ。 */
    static final double SHOCK_SPEED = 0.8;
    public static final double SHOCK_RADIUS = 7;
    public static final float SHOCK_DAMAGE = 14;
    /** 事象の地平線ビーム: ためる tick、狙いを固定する（撃つまでの残り）tick、射程、ダメージ（防具を無視）。 */
    public static final int BEAM_CHARGE_TICKS = 40;
    public static final int BEAM_LOCK_TICKS = 10;
    static final double BEAM_RANGE = 32;
    public static final float BEAM_DAMAGE = 18;
    /** 薙ぎ払い: 全体の tick、振り抜きの tick、届く距離、前方の角度（度）、ダメージ。 */
    public static final int SWEEP_TICKS = 20;
    public static final int SWEEP_STRIKE = 8;
    public static final double SWEEP_RANGE = 6;
    public static final double SWEEP_ARC = 200;
    public static final float SWEEP_DAMAGE = 15;
    /** 胸のレーザー: 続く tick、向きを変える速さ、射程、ダメージと間隔。 */
    public static final int LASER_TICKS = 60;
    static final double LASER_TURN = 0.07;
    static final double LASER_RANGE = 24;
    public static final float LASER_DAMAGE = 3;
    static final int LASER_HIT_INTERVAL = 5;
    /** 重力の手: 前触れの tick、持ち上げている tick、ダメージと間隔。 */
    public static final int GRIP_WARN = 15;
    public static final int GRIP_HOLD = 40;
    public static final float GRIP_DAMAGE = 2;
    static final int GRIP_HIT_INTERVAL = 20;
    /** 叩き落とすときの下向きの速さと、着地で落下ダメージになる高さ（ブロック）。 */
    public static final double GRIP_SLAM_SPEED = 3.6;
    public static final float GRIP_SLAM_FALL = 10;
    /** 背後へ跳ぶ: 離れている距離、見えない tick、間隔。 */
    static final double BLINK_DISTANCE = 16;
    static final int BLINK_UNSEEN_TICKS = 60;
    static final int BLINK_INTERVAL = 160;
    /** 外装の破片の数（フェーズ2以降、周りを回る）。 */
    public static final int SHARDS = 4;
    /** フェーズ2以降: 飛び道具を跳ね返す。見張る距離、跳ね返した速さの倍率（最低の速さ）、矢のダメージの倍率。 */
    static final double REFLECT_RANGE = 3.0;
    public static final double REFLECT_SPEED = 1.5;
    static final double REFLECT_MIN_SPEED = 1.6;
    /** ブラックホール爆弾を見張る範囲と、打ち返したあと開いてもよい自分からの距離（小型ブラックホールの引力が届かない所）。 */
    static final double BOMB_WATCH_RANGE = 16;
    public static final double BOMB_SAFE_DISTANCE = 13;
    static final double BOMB_RETURN_SPEED = 1.9;
    public static final double REFLECT_ARROW_DAMAGE = 1.6;

    /** 技。 */
    enum Move { VOLLEY, SWEEP, GRAVITY, SHARDS, LASER, GRIP, SHOCKWAVE, SINGULARITY, BEAM }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable
    private BlockPos home;
    @Nullable
    private BlockPos console;
    private int specialCooldown = BOLT_INTERVAL;
    /** 受けるダメージの倍率（体力が上限を超える設定のとき 1 より小さい）。 */
    private float damageScale = (float) (MAX_HEALTH_CAP / HEALTH);
    private int blinkCooldown = BLINK_INTERVAL;
    private int unseenTicks;
    private boolean liftNext;
    @Nullable
    private Move lastMove;
    private int emptyChecks;
    /** 叩き落とす相手と、その時刻。 */
    @Nullable
    private LivingEntity slamTarget;
    private int slamAt;
    /** 広がっている衝撃波（中心・始めた時刻・当たった相手）。 */
    @Nullable
    private Vec3 shockCenter;
    private int shockStart;
    private final Set<Integer> shockHit = new HashSet<>();
    /** ビームとレーザーの向き。 */
    private Vec3 beamDir = new Vec3(0, 0, 1);
    private Vec3 laserDir = new Vec3(0, 0, 1);

    public HorizonWarden(EntityType<? extends HorizonWarden> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, Math.min(HEALTH, MAX_HEALTH_CAP))
                .add(Attributes.ATTACK_DAMAGE, 16)
                .add(Attributes.ARMOR, ARMOR_PHASE_1)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(), 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, 1);
        entityData.define(BEAM_CHARGE, 0);
        entityData.define(BEAM_FLASH, 0);
        entityData.define(BEAM_END, new Vector3f());
        entityData.define(SWEEP, 0);
        entityData.define(LASER, 0);
        entityData.define(LASER_END, new Vector3f());
        entityData.define(GRIP, 0);
        entityData.define(GRIP_TARGET, -1);
        entityData.define(EMERGE, 0);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    /** 出現の演出の残り tick。 */
    public int emergeTicks() {
        return entityData.get(EMERGE);
    }

    /** 出現の演出の進み（0〜1。出現済みなら 1）。 */
    public float emergeProgress(float partialTick) {
        int left = emergeTicks();
        return left <= 0 ? 1 : Math.min(1, 1 - (left - partialTick) / EMERGE_TICKS);
    }

    /** 出現の演出を始める（そのあいだは動かず、傷つかない）。 */
    public void startEmerging() {
        entityData.set(EMERGE, EMERGE_TICKS);
        setNoAi(true);
        setNoGravity(true);
        bossBar.setVisible(false);
    }

    /** 出現の演出を飛ばす（テスト用）。 */
    public void skipEmerging() {
        entityData.set(EMERGE, 0);
        setNoAi(false);
        setNoGravity(false);
        bossBar.setVisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && emergeTicks() > 0) {
            tickEmerge((ServerLevel) level());
        }
    }

    /** 出現の演出（サーバー側: 音・粒・終わりの衝撃波）。 */
    private void tickEmerge(ServerLevel level) {
        int left = emergeTicks() - 1;
        int t = EMERGE_TICKS - left;
        entityData.set(EMERGE, left);
        setDeltaMovement(Vec3.ZERO);
        Vec3 core = position().add(0, 2.2, 0);
        if (t < EMERGE_TICKS * 3 / 10) {
            // 特異点が開く: まわりから光の粒が吸い込まれていく
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 2.5 + random.nextDouble() * 2;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, core.x + Math.cos(a) * r,
                        core.y + (random.nextDouble() - 0.5) * 2, core.z + Math.sin(a) * r, 0, -Math.cos(a), 0, -Math.sin(a), 0.3);
            }
        } else if (t < EMERGE_TICKS * 3 / 4) {
            // 体が組み上がる: 足元から火花が昇る
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + (t % 20) * 0.2, getZ(),
                    4, 1.2, 0.1, 1.2, 0.05);
        }
        if (t == 1) {
            level.playSound(null, blockPosition(), io.github.genichimaruo.singulo.registry.SinguloSounds.get("horizon_warden.emerge"), SoundSource.HOSTILE, 3.0F, 1.0F);
        }
        if (left <= 0) {
            // 目覚め: 衝撃波で近くの者を押し返し、戦いが始まる
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 12, 3, 0.2, 3, 0);
            level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.6F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), e -> e != this)) {
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) {
                    e.push(push.normalize().x * 1.2, 0.5, push.normalize().z * 1.2);
                    e.hurtMarked = true;
                }
            }
            setNoAi(false);
            setNoGravity(false);
            bossBar.setVisible(true);
        }
    }

    /** ビームをためている残り tick（0 ならためていない）。 */
    public int beamCharge() {
        return entityData.get(BEAM_CHARGE);
    }

    /** ビームを撃った直後の光の残り tick。 */
    public int beamFlash() {
        return entityData.get(BEAM_FLASH);
    }

    public Vec3 beamEnd() {
        return toVec(entityData.get(BEAM_END));
    }

    /** 薙ぎ払いの残り tick（0 ならしていない）。 */
    public int sweepTicks() {
        return entityData.get(SWEEP);
    }

    /** 胸のレーザーの残り tick と終点。 */
    public int laserTicks() {
        return entityData.get(LASER);
    }

    public Vec3 laserEnd() {
        return toVec(entityData.get(LASER_END));
    }

    /** 重力の手の残り tick と、つかんでいる相手（前触れの間も含む）。 */
    public int gripTicks() {
        return entityData.get(GRIP);
    }

    /** 重力の手で持ち上げている最中か（前触れが終わったあと）。 */
    public boolean gripHolding() {
        int g = gripTicks();
        return g > 0 && g <= GRIP_HOLD;
    }

    @Nullable
    public Entity gripTarget() {
        int id = entityData.get(GRIP_TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    private static Vec3 toVec(Vector3f v) {
        return new Vec3(v.x, v.y, v.z);
    }

    private static Vector3f toVector(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    /** 胸の炉心（ビームとレーザーが出るところ）。 */
    public Vec3 corePosition() {
        double hover = phase() == 3 ? 0.55 : 0;
        return position().add(0, 2.2 + hover, 0).add(Vec3.directionFromRotation(0, yBodyRot).scale(0.5));
    }

    /** 重力の手（右手）の先。 */
    public Vec3 handPosition() {
        Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return corePosition().add(0, 0.9, 0).add(right.scale(0.9)).add(forward.scale(0.4));
    }

    /** 起動するときに呼ぶ。守る場所と、倒されたとき知らせるコンソール。体力は設定の wardenHealth。 */
    public void awaken(BlockPos home, BlockPos console) {
        this.home = home;
        this.console = console;
        double health = ServerConfig.SPEC.isLoaded() ? ServerConfig.WARDEN_HEALTH.get() : HEALTH;
        // 最大体力はゲームの決まりで 1024 までなので、それを超える分は受けるダメージを減らして同じ硬さにする
        double max = Math.min(health, MAX_HEALTH_CAP);
        damageScale = (float) (max / health);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        setHealth((float) max);
        restrictTo(home, ARENA_RADIUS);
    }

    /** 実際の硬さ（受けるダメージを減らした分を含めた体力。設定の wardenHealth と同じ）。 */
    public float effectiveMaxHealth() {
        return getMaxHealth() / damageScale;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ 戦闘

    /** 大技の最中（ほかの技を始めない・歩かない）。 */
    private boolean busy() {
        return beamCharge() > 0 || laserTicks() > 0 || gripTicks() > 0 || sweepTicks() > 0;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        updatePhase(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && !busy()) {
            if (--specialCooldown <= 0) {
                chooseMove(level, target);
            }
            tickBlink(level, target);
        }
        if (busy()) {
            getNavigation().stop();
        }
        deflectBombs(level);
        if (phase() >= 2) {
            reflectProjectiles(level);
        }
        tickSweep(level, target);
        tickLaser(level, target);
        tickGrip(level);
        tickBeam(level, target);
        tickSlam(level);
        tickShockwave(level);
        int flash = beamFlash();
        if (flash > 0) {
            entityData.set(BEAM_FLASH, flash - 1);
        }
        if (tickCount % 20 == 0) {
            checkChallengers(level);
        }
    }

    /** フェーズと距離で使える技から、さっきと違うものを選ぶ。 */
    private void chooseMove(ServerLevel level, LivingEntity target) {
        double d = distanceTo(target);
        boolean near = d < 5.5;
        boolean seen = hasLineOfSight(target);
        List<Move> moves = new ArrayList<>();
        if (near) {
            moves.add(Move.SWEEP);
        }
        if (phase() == 1) {
            if (!near && seen) {
                moves.add(Move.VOLLEY);
            }
        } else {
            moves.add(Move.GRAVITY);
            moves.add(Move.SHARDS);
            moves.add(Move.LASER);
            moves.add(Move.BEAM);
            if (seen) {
                moves.add(Move.GRIP);
                moves.add(Move.LASER);
            }
            if (near && onGround()) {
                moves.add(Move.SHOCKWAVE);
            }
        }
        if (phase() == 3) {
            moves.add(Move.SINGULARITY);
            moves.add(Move.BEAM);
            moves.add(Move.SHARDS);
        }
        if (moves.stream().anyMatch(m -> m != lastMove)) {
            moves.removeIf(m -> m == lastMove);
        }
        if (moves.isEmpty()) {
            specialCooldown = 10;
            return;
        }
        Move move = moves.get(random.nextInt(moves.size()));
        lastMove = move;
        switch (move) {
            case VOLLEY -> shootBolt(target);
            case SWEEP -> startSweep();
            case GRAVITY -> gravityAttack(level, target);
            case SHARDS -> shardBarrage(target);
            case LASER -> startLaser(target);
            case GRIP -> startGrip(target);
            case SHOCKWAVE -> startShockwave(level);
            case SINGULARITY -> deploySingularity(target.position().add(0, 1, 0));
            case BEAM -> startBeam(target);
        }
        // 技のあいだの間（フェーズが進むほど短い）
        specialCooldown = Math.max(specialCooldown, MIN_GAP[Mth.clamp(phase(), 1, 3) - 1]);
    }

    /** 技のあいだの間を、今のフェーズに合わせて縮める。 */
    int pace(int ticks) {
        return Math.max(4, Math.round(ticks * PACE[Mth.clamp(phase(), 1, 3) - 1]));
    }

    /** フェーズ2から: ビームとレーザーが壁を抜け、防具と盾を無視する（手加減なし）。 */
    public boolean piercing() {
        return phase() >= 2;
    }

    private void updatePhase(ServerLevel level) {
        float f = getHealth() / getMaxHealth();
        int phase = f > 2.0F / 3 ? 1 : f > 1.0F / 3 ? 2 : 3;
        if (phase == phase()) {
            return;
        }
        entityData.set(PHASE, phase);
        getAttribute(Attributes.ARMOR).setBaseValue(phase == 1 ? ARMOR_PHASE_1 : 0);
        bossBar.setColor(phase == 1 ? BossEvent.BossBarColor.WHITE : phase == 2 ? BossEvent.BossBarColor.BLUE
                : BossEvent.BossBarColor.RED);
        if (phase == 2) {
            // 外装が剥がれ、破片が周りを回り始める
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.5, getZ(), 4, 0.6, 0.8, 0.6, 0);
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.8, getZ(), 40, 0.8, 1.0, 0.8, 0.15);
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("horizon_warden.phase"), 3.0F, 1.0F);
        } else if (phase == 3) {
            // 炉心が赤く燃え、宙に浮く
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2, getZ(), 80, 1.0, 1.2, 1.0, 0.3);
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("horizon_warden.phase"), 3.0F, 0.85F);
        }
        specialCooldown = pace(GRAVITY_INTERVAL / 2);
    }

    /** フェーズ3: その場所に小型の特異点を展開する（{@link WardenSingularity}）。 */
    public void deploySingularity(Vec3 at) {
        level().addFreshEntity(new WardenSingularity(level(), this, at));
        specialCooldown = pace(40);
        level().playSound(null, at.x, at.y, at.z, io.github.genichimaruo.singulo.registry.SinguloSounds.BLACK_HOLE_FORMATION.get(),
                getSoundSource(), 1.6F, 1.4F);
    }

    /** 光弾を扇状に3発撃つ。近すぎるか見えないときは撃たない。撃ったら true。 */
    public boolean shootBolt(LivingEntity target) {
        specialCooldown = pace(BOLT_INTERVAL);
        if (distanceToSqr(target) < 16 || !hasLineOfSight(target)) {
            return false;
        }
        Vec3 from = new Vec3(getX(), getEyeY() - 0.3, getZ());
        Vec3 dir = target.getEyePosition().subtract(from).normalize();
        for (int i = 0; i < VOLLEY; i++) {
            float yaw = (i - (VOLLEY - 1) / 2.0F) * VOLLEY_SPREAD * Mth.DEG_TO_RAD;
            Vec3 d = dir.yRot(yaw);
            HorizonBolt bolt = new HorizonBolt(level(), this);
            bolt.setPos(from);
            bolt.shoot(d.x, d.y, d.z, 1.4F, 0.5F);
            level().addFreshEntity(bolt);
        }
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.6F);
        swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** フェーズ2以降: 周りを回る外装の破片から、追尾する光弾を撃つ。 */
    public void shardBarrage(LivingEntity target) {
        specialCooldown = pace(GRAVITY_INTERVAL);
        double base = tickCount * 0.08;
        int rgb = phase() == 3 ? 0xFF5A5A : 0x78D2F0;
        for (int i = 0; i < SHARDS; i++) {
            double a = base + i * Math.PI * 2 / SHARDS;
            Vec3 from = position().add(Math.cos(a) * 1.8, getBbHeight() * 0.55, Math.sin(a) * 1.8);
            HorizonBolt bolt = new HorizonBolt(level(), this).homing(target).style(rgb, 0.8F, HorizonBolt.DAMAGE * 0.8F).piercing(piercing());
            bolt.setPos(from);
            Vec3 d = from.subtract(position().add(0, getBbHeight() * 0.55, 0)).normalize().add(0, 0.4, 0);
            bolt.shoot(d.x, d.y, d.z, 0.7F, 0);
            level().addFreshEntity(bolt);
        }
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.2F);
    }

    /** 引き寄せと、浮かせて叩き落とすのを交互に。速度を直接与える。 */
    public void gravityAttack(ServerLevel level, LivingEntity target) {
        specialCooldown = pace(GRAVITY_INTERVAL);
        if (distanceToSqr(target) > 20 * 20) {
            return;
        }
        if (liftNext) {
            target.setDeltaMovement(target.getDeltaMovement().x, 1.4, target.getDeltaMovement().z);
            slamTarget = target;
            slamAt = tickCount + SLAM_DELAY;
        } else {
            Vec3 pull = position().subtract(target.position()).normalize().scale(1.2).add(0, 0.3, 0);
            target.setDeltaMovement(pull);
        }
        target.hurtMarked = true;
        liftNext = !liftNext;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1, target.getZ(), 30, 0.5, 1, 0.5, 0.05);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.4F);
    }

    /** 浮かせた相手を、少しあとで地面へ叩きつける。 */
    private void tickSlam(ServerLevel level) {
        if (slamTarget == null || tickCount < slamAt) {
            return;
        }
        LivingEntity t = slamTarget;
        slamTarget = null;
        if (!t.isAlive() || t.onGround()) {
            return;
        }
        t.setDeltaMovement(t.getDeltaMovement().x * 0.2, -2.4, t.getDeltaMovement().z * 0.2);
        t.hurtMarked = true;
        level.sendParticles(ParticleTypes.SONIC_BOOM, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
        playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 1.5F, 0.6F);
    }

    // ------------------------------------------------------------------ 薙ぎ払い

    /** 刃の腕を大きく振りかぶってから、前方を薙ぎ払う。振り抜く瞬間に大きな三日月と星が散る。 */
    public void startSweep() {
        entityData.set(SWEEP, SWEEP_TICKS);
        specialCooldown = pace(30);
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("horizon_warden.sweep"), 2.0F, 1.0F);
    }

    private void tickSweep(ServerLevel level, @Nullable LivingEntity target) {
        int s = sweepTicks();
        if (s <= 0) {
            return;
        }
        if (s > SWEEP_STRIKE && target != null) {
            getLookControl().setLookAt(target, 30, 30);
            setYBodyRot(getYHeadRot());
        }
        entityData.set(SWEEP, s - 1);
        if (s - 1 == SWEEP_STRIKE) {
            sweepStrike(level);
        }
    }

    /** 振り抜き: 前方の扇（SWEEP_ARC 度、SWEEP_RANGE ブロック）の中の相手に当たる。 */
    private void sweepStrike(ServerLevel level) {
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.5F);
        Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 c = position();
        double cos = Math.cos(Math.toRadians(SWEEP_ARC / 2));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SWEEP_RANGE, 2, SWEEP_RANGE),
                e -> e != this && e.isAlive() && !RuinGuards.isGuard(e))) {
            Vec3 to = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            double d = to.length();
            if (d > SWEEP_RANGE + e.getBbWidth() / 2 || (d > 0.5 && to.normalize().dot(forward) < cos)) {
                continue;
            }
            e.hurt(damageSources().mobAttack(this), SWEEP_DAMAGE);
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            e.setDeltaMovement(to.normalize().scale(0.8).add(side.scale(-0.6)).add(0, 0.45, 0));
            e.hurtMarked = true;
        }
        // 三日月に沿ってきらきらした星を散らす
        double yaw = Math.atan2(forward.z, forward.x);
        double y = getY() + 2.0 + (phase() == 3 ? 0.55 : 0);
        for (int i = 0; i <= 28; i++) {
            double a = yaw + Math.toRadians(-SWEEP_ARC / 2 + SWEEP_ARC * i / 28.0);
            double r = 3.6 + random.nextDouble() * 1.6;
            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            level.sendParticles(ParticleTypes.WAX_OFF, x, y + random.nextGaussian() * 0.25, z, 1, 0.1, 0.1, 0.1, 0.6);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.15, 0.15, 0.15, 0.2);
            }
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.05, 0.05, 0.05, 0.08);
            }
        }
    }

    // ------------------------------------------------------------------ 胸のレーザー

    /** フェーズ2以降: 胸の炉心から細いレーザーを出し続け、相手をゆっくり追う（走れば振り切れる）。 */
    public void startLaser(LivingEntity target) {
        entityData.set(LASER, LASER_TICKS);
        laserDir = target.getBoundingBox().getCenter().subtract(corePosition()).normalize()
                .yRot((random.nextBoolean() ? 1 : -1) * 25 * Mth.DEG_TO_RAD);
        specialCooldown = pace(30);
        playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 1.5F);
    }

    private void tickLaser(ServerLevel level, @Nullable LivingEntity target) {
        int l = laserTicks();
        if (l <= 0) {
            return;
        }
        Vec3 from = corePosition();
        if (target != null && target.isAlive()) {
            Vec3 want = target.getBoundingBox().getCenter().subtract(from).normalize();
            laserDir = laserDir.lerp(want, LASER_TURN).normalize();
        }
        Vec3 to = from.add(laserDir.scale(LASER_RANGE));
        Vec3 end = piercing() ? to : clip(level, from, to);
        entityData.set(LASER_END, toVector(end));
        getLookControl().setLookAt(end);
        if (l % LASER_HIT_INTERVAL == 0) {
            AABB box = new AABB(from, end).inflate(0.6);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !RuinGuards.isGuard(e))) {
                if (!e.getBoundingBox().inflate(0.2).clip(from, end).isEmpty()) {
                    e.hurt(piercing() ? SinguloDamageTypes.tidal(level, this) : damageSources().indirectMagic(this, this), LASER_DAMAGE);
                }
            }
        }
        if (l % 2 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 3, 0.1, 0.1, 0.1, 0.15);
            level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 1, 0.05, 0.05, 0.05, 0.01);
        }
        entityData.set(LASER, l - 1);
    }

    // ------------------------------------------------------------------ 重力の手

    /** フェーズ2以降: 相手の周りに重力の渦を集め（前触れ）、持ち上げて振り回し、最後に叩き落とす。 */
    public void startGrip(LivingEntity target) {
        entityData.set(GRIP, GRIP_WARN + GRIP_HOLD);
        entityData.set(GRIP_TARGET, target.getId());
        specialCooldown = pace(30);
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("horizon_warden.grip"), 2.5F, 1.0F);
    }

    private void tickGrip(ServerLevel level) {
        int g = gripTicks();
        if (g <= 0) {
            return;
        }
        Entity e = gripTarget();
        if (!(e instanceof LivingEntity t) || !t.isAlive()) {
            endGrip();
            return;
        }
        if (g > GRIP_HOLD) {
            // 前触れ: 相手の周りに紫の渦
            double a = g * 0.6;
            for (int i = 0; i < 3; i++) {
                double ang = a + i * Math.PI * 2 / 3;
                level.sendParticles(ParticleTypes.WITCH, t.getX() + Math.cos(ang) * 1.2, t.getY() + 0.2 + (GRIP_WARN + GRIP_HOLD - g) * 0.1,
                        t.getZ() + Math.sin(ang) * 1.2, 1, 0, 0, 0, 0);
            }
        } else {
            // 持ち上げて、目の前の空中で振り回す
            Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            double swing = Math.sin(g * 0.25) * 2.0;
            Vec3 hold = corePosition().add(forward.scale(4)).add(0, 3.4, 0).add(side.scale(swing));
            Vec3 v = hold.subtract(t.getBoundingBox().getCenter()).scale(0.35);
            if (v.lengthSqr() > 1.5 * 1.5) {
                v = v.normalize().scale(1.5);
            }
            t.setDeltaMovement(v);
            t.hurtMarked = true;
            t.fallDistance = 0;
            getLookControl().setLookAt(t);
            if (g % GRIP_HIT_INTERVAL == 0) {
                t.hurt(SinguloDamageTypes.tidal(level, this), GRIP_DAMAGE);
            }
            if (g % 3 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
            }
        }
        entityData.set(GRIP, g - 1);
        if (g - 1 == 0) {
            // 最後に強く叩き落とす。落ちた高さに GRIP_SLAM_FALL を足して、着地で大きな落下ダメージになる
            t.setDeltaMovement(t.getDeltaMovement().x * 0.3, -GRIP_SLAM_SPEED, t.getDeltaMovement().z * 0.3);
            t.fallDistance = GRIP_SLAM_FALL;
            t.hurtMarked = true;
            level.sendParticles(ParticleTypes.SONIC_BOOM, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
            playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 1.5F, 0.5F);
            endGrip();
        }
    }

    private void endGrip() {
        entityData.set(GRIP, 0);
        entityData.set(GRIP_TARGET, -1);
    }

    // ------------------------------------------------------------------ 衝撃波

    /** フェーズ2以降: 足元から衝撃波を広げる。地面にいる相手に当たる（跳べばよけられる）。 */
    public void startShockwave(ServerLevel level) {
        shockCenter = position();
        shockStart = tickCount;
        shockHit.clear();
        specialCooldown = pace(30);
        swing(InteractionHand.MAIN_HAND);
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("horizon_warden.shockwave"), 3.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 2, 0.3, 0, 0.3, 0);
    }

    private void tickShockwave(ServerLevel level) {
        if (shockCenter == null) {
            return;
        }
        double r = (tickCount - shockStart) * SHOCK_SPEED;
        if (r > SHOCK_RADIUS) {
            shockCenter = null;
            return;
        }
        Vec3 c = shockCenter;
        int n = Math.max(8, (int) (r * 8));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.01);
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0.02);
            }
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 1, 2, r + 1),
                e -> e != this && e.isAlive() && !RuinGuards.isGuard(e) && !shockHit.contains(e.getId()))) {
            double d = Math.sqrt(Mth.square(e.getX() - c.x) + Mth.square(e.getZ() - c.z));
            // 輪の縁にいて、地面に足がついている相手だけ（跳べばよけられる）
            if (Math.abs(d - r) > 0.9 || !e.onGround() || e.getY() > c.y + 1.5) {
                continue;
            }
            shockHit.add(e.getId());
            e.hurt(damageSources().mobAttack(this), SHOCK_DAMAGE);
            Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z).normalize().scale(1.1).add(0, 0.55, 0);
            e.setDeltaMovement(out);
            e.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ 事象の地平線ビーム

    /** フェーズ3: 事象の地平線ビームをため始める。 */
    public void startBeam(LivingEntity target) {
        entityData.set(BEAM_CHARGE, BEAM_CHARGE_TICKS);
        beamDir = target.getEyePosition().subtract(corePosition()).normalize();
        updateBeamEnd();
        specialCooldown = pace(30);
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("horizon_warden.beam_charge"), 3.0F, 1.0F);
    }

    private void tickBeam(ServerLevel level, @Nullable LivingEntity target) {
        int charge = beamCharge();
        if (charge <= 0) {
            return;
        }
        // 撃つ直前（BEAM_LOCK_TICKS）までは相手を追い、そのあとは向きを固定する（よけるならここ）
        if (charge > BEAM_LOCK_TICKS && target != null && target.isAlive()) {
            beamDir = target.getEyePosition().subtract(corePosition()).normalize();
            getLookControl().setLookAt(target);
        }
        updateBeamEnd();
        Vec3 from = corePosition();
        Vec3 end = beamEnd();
        if (charge % 3 == 0) {
            // ためている間は、狙いの線に沿って細かい光が集まる
            for (int i = 1; i < 12; i++) {
                Vec3 p = from.lerp(end, i / 12.0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 3, 0.4, 0.4, 0.4, 0.15);
        entityData.set(BEAM_CHARGE, charge - 1);
        if (charge - 1 == 0) {
            fireBeam(level, from, end);
        }
    }

    private void updateBeamEnd() {
        Vec3 from = corePosition();
        Vec3 to = from.add(beamDir.scale(BEAM_RANGE));
        Vec3 end = piercing() ? to : clip(level(), from, to);
        entityData.set(BEAM_END, toVector(end));
    }

    private Vec3 clip(Level level, Vec3 from, Vec3 to) {
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    /** ビームを撃つ: 線上にいる相手に、防具を無視するダメージと吹き飛ばし（フェーズ2からは壁の向こうまで届く）。 */
    private void fireBeam(ServerLevel level, Vec3 from, Vec3 end) {
        entityData.set(BEAM_FLASH, 8);
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("horizon_warden.beam_fire"), 3.5F, 1.0F);
        Vec3 dir = end.subtract(from).normalize();
        AABB box = new AABB(from, end).inflate(1.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !RuinGuards.isGuard(e))) {
            if (e.getBoundingBox().inflate(0.4).clip(from, end).isEmpty()) {
                continue;
            }
            e.hurt(SinguloDamageTypes.tidal(level, this), BEAM_DAMAGE);
            e.setDeltaMovement(dir.scale(1.4).add(0, 0.4, 0));
            e.hurtMarked = true;
        }
        for (int i = 0; i <= 24; i++) {
            Vec3 p = from.lerp(end, i / 24.0);
            level.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 2, 0.2, 0.2, 0.2, 0);
    }

    // ------------------------------------------------------------------ 飛び道具を跳ね返す（フェーズ2以降）

    @Override
    public boolean isAlliedTo(Entity other) {
        return RuinGuards.isGuard(other) || super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile && level() instanceof ServerLevel server) {
            if (projectile instanceof io.github.genichimaruo.singulo.reactor.BlackHoleBomb bomb && !bomb.deflectedBy(this)) {
                deflectBomb(server, bomb); return false;
            }
            if (phase() >= 2 && !ownProjectile(projectile)) { reflect(server, projectile); return false; }
        }
        if (emergeTicks() > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;            // 出現の演出のあいだは傷つかない
        }
        if (RuinGuards.friendlyFire(source)) {
            return false;
        }
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= damageScale;
        }
        return super.hurt(source, amount);
    }

    /** 跳ね返さない飛び道具（自分やドローンの光弾、すでに自分が跳ね返したもの）。 */
    private boolean ownProjectile(net.minecraft.world.entity.projectile.Projectile p) {
        Entity owner = p.getOwner();
        return owner == this || owner instanceof HorizonWarden || owner instanceof SecurityDrone;
    }

    /** 近づいてくる飛び道具を見つけて、撃った相手へ打ち返す。 */
    private void reflectProjectiles(ServerLevel level) {
        for (net.minecraft.world.entity.projectile.Projectile p : level.getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class, getBoundingBox().inflate(REFLECT_RANGE),
                p -> p.isAlive() && !ownProjectile(p))) {
            Vec3 toMe = getBoundingBox().getCenter().subtract(p.position());
            if (p.getDeltaMovement().lengthSqr() < 1e-4 || p.getDeltaMovement().dot(toMe) <= 0) {
                continue;            // こちらへ向かっていない（刺さった矢など）
            }
            reflect(level, p);
        }
    }

    /** 飛び道具を、撃った相手の方へ、より強くして打ち返す（撃った相手がいなければ来た方へ）。 */
    private void reflect(ServerLevel level, net.minecraft.world.entity.projectile.Projectile p) {
        Entity shooter = p.getOwner();
        { net.minecraft.world.entity.projectile.Projectile proj = p;
            Vec3 v = proj.getDeltaMovement();
            double speed = Math.max(REFLECT_MIN_SPEED, v.length() * REFLECT_SPEED);
            Vec3 dir = shooter != null && shooter.isAlive()
                    ? shooter.getEyePosition().subtract(proj.position()).normalize()
                    : v.normalize().scale(-1);
            proj.setDeltaMovement(dir.scale(speed));
            double horizontal = dir.horizontalDistance();
            proj.setYRot((float) (Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG));
            proj.setXRot((float) (Mth.atan2(dir.y, horizontal) * Mth.RAD_TO_DEG));
            proj.yRotO = proj.getYRot();
            proj.xRotO = proj.getXRot();
            proj.hasImpulse = true;
            proj.hurtMarked = true;
            if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
                arrow.setBaseDamage(arrow.getBaseDamage() * REFLECT_ARROW_DAMAGE);
                arrow.setCritArrow(true);
            }
            if (proj instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile hurting) {
                hurting.xPower = dir.x * 0.1; hurting.yPower = dir.y * 0.1; hurting.zPower = dir.z * 0.1;
            }
        }
        p.setOwner(this);
        swing(InteractionHand.MAIN_HAND);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY(), p.getZ(), 8, 0.15, 0.15, 0.15, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 4, 0.1, 0.1, 0.1, 0.15);
        playSound(SoundEvents.SHIELD_BLOCK, 1.5F, 0.8F);
    }

    // ------------------------------------------------------------------ ブラックホール爆弾を打ち返す（どのフェーズでも）

    /**
     * 近づいてくるブラックホール爆弾を、届く前に打ち返す。開いても自分が引き込まれない所（BOMB_SAFE_DISTANCE より遠く）へ飛ばす。
     * 投げた相手が十分に遠ければその相手へ、近ければ相手の向こうへ高く放る。すでに開いたブラックホールには何もできない。
     */
    private void deflectBombs(ServerLevel level) {
        for (io.github.genichimaruo.singulo.reactor.BlackHoleBomb bomb : level.getEntitiesOfClass(
                io.github.genichimaruo.singulo.reactor.BlackHoleBomb.class, getBoundingBox().inflate(BOMB_WATCH_RANGE),
                b -> b.isAlive() && !b.deflectedBy(this))) {
            Vec3 toMe = getBoundingBox().getCenter().subtract(bomb.position());
            if (bomb.getDeltaMovement().dot(toMe) <= 0 && toMe.lengthSqr() > BOMB_SAFE_DISTANCE * BOMB_SAFE_DISTANCE) {
                continue;            // 離れていく爆弾は放っておく
            }
            deflectBomb(level, bomb);
        }
    }

    private void deflectBomb(ServerLevel level, io.github.genichimaruo.singulo.reactor.BlackHoleBomb bomb) {
        Entity shooter = bomb.getOwner();
        Vec3 c = getBoundingBox().getCenter();
        Vec3 v;
        if (shooter != null && shooter.isAlive() && shooter.distanceTo(this) > BOMB_SAFE_DISTANCE + 2) {
            v = shooter.getEyePosition().subtract(bomb.position()).normalize().scale(BOMB_RETURN_SPEED).add(0, 0.15, 0);
        } else {
            Vec3 away = new Vec3(bomb.getX() - c.x, 0, bomb.getZ() - c.z);
            if (away.lengthSqr() < 1e-4) {
                away = Vec3.directionFromRotation(0, yBodyRot);
            }
            v = away.normalize().scale(BOMB_RETURN_SPEED).add(0, 0.6, 0);
        }
        Vec3 dir = v;
        { net.minecraft.world.entity.projectile.Projectile proj = bomb;
            proj.setDeltaMovement(dir);
            proj.hasImpulse = true;
            proj.hurtMarked = true;
        }
        bomb.setOwner(this);
        bomb.markDeflected(this);
        swing(InteractionHand.MAIN_HAND);
        level.sendParticles(ParticleTypes.SONIC_BOOM, bomb.getX(), bomb.getY(), bomb.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, bomb.getX(), bomb.getY(), bomb.getZ(), 12, 0.2, 0.2, 0.2, 0.25);
        playSound(SoundEvents.SHIELD_BLOCK, 2.0F, 0.6F);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.5F);
    }

    /** 速すぎて見張りをすり抜けた飛び道具も、当たる瞬間に打ち返す（フェーズ2以降。ブラックホール爆弾はいつでも）。 */
    // ------------------------------------------------------------------ 移動・封印

    /** 離れたまま、または隠れたままの相手の背後へ跳ぶ。 */
    private void tickBlink(ServerLevel level, LivingEntity target) {
        unseenTicks = hasLineOfSight(target) ? 0 : unseenTicks + 1;
        if (--blinkCooldown > 0) {
            return;
        }
        if (distanceToSqr(target) < BLINK_DISTANCE * BLINK_DISTANCE && unseenTicks < BLINK_UNSEEN_TICKS) {
            return;
        }
        blinkCooldown = pace(BLINK_INTERVAL);
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
        Vec3 before = position();
        if (!randomTeleport(behind.x, target.getY(), behind.z, false)) {
            return;
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, before.x, before.y + 1.5, before.z, 60, 0.6, 1.2, 0.6, 0.2);
        level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.5, getZ(), 60, 0.6, 1.2, 0.6, 0.6);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.5F);
        unseenTicks = 0;
    }

    /** 守る場所の周りに挑戦者がいなくなったら、封印に戻る。 */
    private void checkChallengers(ServerLevel level) {
        if (home == null) {
            return;
        }
        boolean present = !level.getEntitiesOfClass(Player.class, new AABB(home).inflate(ARENA_RADIUS),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()).isEmpty();
        emptyChecks = present ? 0 : emptyChecks + 1;
        boolean reset = !ServerConfig.SPEC.isLoaded() || ServerConfig.WARDEN_RESET_ON_LEAVE.get();
        if (emptyChecks >= RESET_CHECKS && reset) {
            if (console != null && level.getBlockEntity(console) instanceof SealConsoleBlockEntity c) {
                c.onWardenReset(level);
            }
            discard();
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (busy()) {
            return false;
        }
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
        }
        return hit;
    }

    // ------------------------------------------------------------------ 倒されたとき・耐性

    @Override
    public void die(DamageSource source) {
        super.die(source);
        endGrip();
        entityData.set(BEAM_CHARGE, 0);
        entityData.set(LASER, 0);
        entityData.set(SWEEP, 0);
        if (level() instanceof ServerLevel level) {
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("horizon_warden.death"), 3.5F, 1.0F);
            if (console != null && level.getBlockEntity(console) instanceof SealConsoleBlockEntity c) {
                c.onWardenDefeated(level);
            }
        }
    }

    /** 倒されたときの演出の進み（0〜1）。倒されていなければ 0。 */
    public float deathProgress(float partialTick) {
        return deathTime <= 0 ? 0 : Math.min(1, (deathTime + partialTick) / DEATH_TICKS);
    }

    /**
     * 倒されたときの演出（{@value #DEATH_TICKS} tick）: 外装が火花と爆ぜる音を立てて剥がれ落ち、膝をつく。
     * 胸の炉心の特異点がふくらんで周りの光を吸い込み、最後に一点へつぶれて閃光と衝撃波を残す。
     */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel level) {
            setDeltaMovement(Vec3.ZERO);
            deathTick(level, deathTime);
        }
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    private void deathTick(ServerLevel level, int t) {
        Vec3 core = position().add(0, 2.0 - 0.6 * Math.min(1, t / 30.0), 0);
        if (t < 55) {
            // 外装が剥がれ、火花が散る
            if (t % 9 == 1) {
                level.sendParticles(ParticleTypes.EXPLOSION, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 1 + random.nextDouble() * 2,
                        getZ() + (random.nextDouble() - 0.5) * 1.6, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 3, 0.6, 1.0, 0.6, 0.2);
        } else if (t < DEATH_TICKS - 10) {
            // 特異点が胸からふくらみ、周りの光を吸い込む
            for (int i = 0; i < 5; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double b = (random.nextDouble() - 0.5) * Math.PI;
                double r = 3 + random.nextDouble() * 3;
                Vec3 d = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x + d.x * r, core.y + d.y * r, core.z + d.z * r,
                        0, -d.x, -d.y, -d.z, 0.8);
            }
        } else if (t == DEATH_TICKS - 10) {
            level.sendParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 3, 0.2, 0.2, 0.2, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, core.x, core.y, core.z, 120, 0.2, 0.2, 0.2, 0.7);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10), e -> e != this)) {
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) {
                    e.push(push.normalize().x * 1.3, 0.5, push.normalize().z * 1.3);
                    e.hurtMarked = true;
                }
            }
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;                                  // 体から鳴り続ける音は、クライアントの BossSounds が鳴らす
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("phase", phase());
        tag.putFloat("damage_scale", damageScale);
        tag.putInt("emerge", emergeTicks());
        if (home != null) {
            tag.put("home", NbtUtils.writeBlockPos(home));
        }
        if (console != null) {
            tag.put("console", NbtUtils.writeBlockPos(console));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, Math.max(1, tag.getInt("phase")));
        if (tag.contains("damage_scale")) {
            damageScale = Math.max(0.001F, tag.getFloat("damage_scale"));
        }
        entityData.set(EMERGE, tag.getInt("emerge"));
        home = io.github.genichimaruo.singulo.compat.Legacy.readBlockPos(tag, "home").orElse(null);
        console = io.github.genichimaruo.singulo.compat.Legacy.readBlockPos(tag, "console").orElse(null);
        if (home != null) {
            restrictTo(home, ARENA_RADIUS);
        }
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
    }
}
