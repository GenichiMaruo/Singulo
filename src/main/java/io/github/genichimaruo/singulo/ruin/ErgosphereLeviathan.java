package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Vector3f;

/**
 * エルゴ球の大蛇（沈んだ炉心区画のボス、段階5）。止まったペンローズ炉の渦に棲みついた、長い体の大蛇。
 * 頭と {@value #SEGMENTS} 節の体（{@link LeviathanSegment}）からなり、体は頭の通った跡をなぞってついてくる。
 * 体力の 2/3・1/3 で姿を変える（そのあいだは傷つかない）。
 * <ol>
 *   <li><b>装甲</b>: 炉殻の板を鱗のように連ねた体。頭は普通に、胴は硬く（×{@value #BODY_FACTOR}）、尾の光る芯は弱い
 *       （×{@value #TAIL_FACTOR}）。突進・渦の吐息・尾の薙ぎ払い</li>
 *   <li><b>骨格</b>（脱皮）: 鱗をすべて弾き飛ばし、光の背骨と肋の輪だけになる。胴も効く（×{@value #BODY_FACTOR_2}）。
 *       続けての突進・とぐろ（部屋の中で輪になって回り、まわりを押し流しながら光の欠片を撃つ）・噛みつき・光の欠片の雨</li>
 *   <li><b>龍</b>（再臨）: 炉心の渦へ潜り、黒い体に金の降着の翼と光の角を持つ龍となって噴き上がる。
 *       相対論的ジェット（頭の両角から回る2本の光線）・降着の嵐（上空を回りながら小さなブラックホールを降らせる）・
 *       突進の着地に特異点を残す・相手を追う吐息</li>
 * </ol>
 * <ul>
 *   <li>泳いだ跡にはエルゴ球の帯が残り、その中では泳ぐ向きへ押し流される（流れに乗ると追いつきやすい）</li>
 *   <li>出現: 床の炉心に渦が開き、小さな蛇が這い出して頼りなく泳ぐ。直後、真下から巨大な顎が噴き上がってそれを丸呑みにし、
 *       そのまま螺旋を描いて昇り、体を一節ずつ引き出す</li>
 *   <li>倒されると: 身をよじり、体が尾から崩れ、螺旋を描いて炉心の渦へ沈む。最後に渦がつぶれて閃光を残す</li>
 * </ul>
 */
public class ErgosphereLeviathan extends RuinBoss {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE_TICKS = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> AIM = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<BlockPos> HOME = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> JET_YAW = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.FLOAT);
    /** ジェットの下向きの角度（度）。光線が床に当たる輪が、ちょうど相手の居る距離を通るように決める。 */
    private static final EntityDataAccessor<Float> JET_PITCH = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.FLOAT);
    /** 重力の裂け目の、いまの線の向き（ラジアン。見た目にも使う）。 */
    private static final EntityDataAccessor<Float> RIFT_ANGLE = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.FLOAT);

    public static final int SWIM = 0, DIVE_WINDUP = 1, DIVE = 2, BREATH_CHARGE = 3, BREATH_FIRE = 4, TAIL_WHIP = 5, MOLT = 6,
            COIL = 7, BITE = 8, SUBMERGE = 9, ASCEND = 10, JET = 11, STORM = 12, REST = 13, REIGNITE = 14, QUAKE = 15, RIFT = 16, TIDE = 17;
    /**
     * 「重力の裂け目」: 床に平行な3本の光の線（RIFT_SPACING おき）を引き、RIFT_WARN tick 後に線に沿って空間を裂く。
     * 線の上（幅 RIFT_WIDTH）にいると当たる。向きを変えて RIFT_VOLLEYS 回続く。
     */
    public static final int RIFT_WARN = 24, RIFT_GAP = 10, RIFT_VOLLEYS = 3;
    public static final double RIFT_SPACING = 6, RIFT_WIDTH = 1.6;
    public static final float RIFT_DAMAGE = 14, RIFT_DAMAGE_NORMAL = 12;
    /**
     * 「引き潮」: 炉心の渦が開き、TIDE_TICKS のあいだ部屋じゅうを中心へ引き込む。炉心から TIDE_CORE_RADIUS 以内では
     * 潮汐ダメージを受け続ける（流れに逆らって外へ走る）。
     */
    public static final int TIDE_TICKS = 100;
    public static final double TIDE_PULL = 0.045, TIDE_CORE_RADIUS = 5;
    public static final float TIDE_DAMAGE = 4, TIDE_DAMAGE_NORMAL = 3;
    /**
     * 必殺技「炉心の再点火」（骨格・龍）: 炉心に巻きついて REIGNITE_CHARGE tick 溜め、炉心から部屋じゅうへ衝撃を放つ。
     * 炉心から見て封じ込め柱（か宙の破片）の陰にいれば当たらない。それ以外はほぼ倒れるほどの潮汐ダメージを受けて吹き飛ぶ。
     * 溜めているあいだ大蛇は炉心の近くで低く巻いているので、危険を承知で殴ることもできる。
     */
    public static final int REIGNITE_CHARGE = 120, REIGNITE_TICKS = 140;
    public static final float REIGNITE_DAMAGE = 60, REIGNITE_DAMAGE_NORMAL = 40;
    /** 次の必殺技までの間（tick）。 */
    static final int REIGNITE_INTERVAL = 900;
    /**
     * 「地殻の波」: 高く昇って身を丸め（QUAKE_RISE tick）、部屋の中心へ叩きつける。床を伝って衝撃の輪が2つ広がり、
     * 輪が来たときに床に立っていると当たる（跳べばよけられる）。
     */
    public static final int QUAKE_RISE = 34, QUAKE_TICKS = 34 + 70;
    public static final double QUAKE_WAVE_SPEED = 0.55;
    public static final int QUAKE_SECOND_WAVE = 20;
    public static final float QUAKE_DAMAGE = 14, QUAKE_DAMAGE_NORMAL = 10;
    /**
     * 突進で床をかすめたあと、床の近くでとぐろを巻いて止まる時間（tick）。頭と体に剣が届く隙。
     * 龍は、飛び去るときにその場所へ特異点を残す（降りてきた所に出すと、近寄れなくなるので）。
     */
    public static final int REST_TICKS = 60;

    /** 体の節の数（最後の1つが尾）。 */
    public static final int SEGMENTS = 12;
    /** 節と節の間（ブロック）。頭から最初の節までは HEAD_GAP。 */
    public static final float SPACING = 1.7F;
    public static final float HEAD_GAP = 1.9F;
    public static final float BODY_FACTOR = 0.6F, BODY_FACTOR_2 = 0.9F;
    public static final float TAIL_FACTOR = 1.6F;
    /** 体力の既定値（設定 leviathanHealth）。 */
    public static final double HEALTH = 1600;
    /** 通常の強さの体力の倍率（leviathanHealth に掛ける。ハードモードは1倍）。 */
    public static final double NORMAL_HEALTH_FACTOR = 0.625;

    /** いまの設定での体力の倍率（ハードモードは1倍）。 */
    public static double healthFactor() {
        return BossScaling.hardMode() ? 1.0 : NORMAL_HEALTH_FACTOR;
    }

    // ---- 出現: 渦が開く → 小さな蛇が飛び出し、渦は閉じる → 小さな蛇が漂う → 急に渦が開き、大蛇が噴き上がって丸呑み → 螺旋を描いて昇る
    public static final int EMERGE_TICKS = 230;
    /** 小さな蛇が飛び出す tick と、渦が閉じ終える tick。 */
    public static final int EMERGE_VORTEX = 30, RIFT_CLOSE = 42;
    /** 小さな蛇が炉心の上に戻って止まる tick、渦が急に開く tick、大蛇が噴き上がる tick、飲み込み終える tick。 */
    public static final int HATCH_SETTLE = 118, RIFT_SNAP = 122, SWALLOW = 125, SWALLOW_END = 135;

    public static final int DEATH_TICKS = 120;
    /** 倒されたとき、身をよじってから沈み始めるまで・沈み終えるまで。 */
    public static final int DEATH_WRITHE = 20, DEATH_SINK = 100;
    public static final int ARENA = 24;
    /** 泳ぐ輪の半径と、床からの高さ。 */
    static final double ORBIT = 11;
    static final double SWIM_HEIGHT = 10;
    static final double SWIM_SPEED = 0.32, ENRAGED_SPEED = 0.42;
    static final double DIVE_SPEED = 1.15;
    public static final int DIVE_WINDUP_TICKS = 36, DIVE_TICKS = 40;
    public static final int BREATH_CHARGE_TICKS = 40, BREATH_FIRE_TICKS = 24;
    public static final int WHIP_TICKS = 18, WHIP_STRIKE = 8;
    public static final int MOLT_TICKS = 80, MOLT_BURST = 40;
    public static final int COIL_TICKS = 110;
    public static final int BITE_TICKS = 22, BITE_WINDUP = 9;
    public static final int SUBMERGE_TICKS = 50, ASCEND_TICKS = 80, ASCEND_BURST = 34;
    public static final int JET_TICKS = 90, JET_WINDUP = 22;
    public static final int STORM_TICKS = 100;
    static final int ATTACK_INTERVAL = 80;
    public static final float CONTACT_DAMAGE = 5, DIVE_DAMAGE = 14, BREATH_DAMAGE = 14, WHIP_DAMAGE = 9, BITE_DAMAGE = 12,
            JET_DAMAGE = 16, SHARD_DAMAGE = 5, MOLT_DAMAGE = 8;
    /** 通常の強さの光線（吐息・ジェット）。よけたくなる痛さに。 */
    public static final float BREATH_DAMAGE_NORMAL = 12, JET_DAMAGE_NORMAL = 14;
    public static final double BREATH_RANGE = 26, BREATH_WIDTH = 1.6, BREATH_PULL_RANGE = 18, JET_RANGE = 24, WHIP_RANGE = 5.5;
    public static final double COIL_RADIUS = 7, COIL_REACH = 15;
    /** エルゴ球の帯が残る tick と、押し流す強さ・届く距離。 */
    public static final int WAKE_TICKS = 60;
    static final double WAKE_PUSH = 0.09, WAKE_REACH = 2.5;
    public static final int SHED_PLATES = 5;

    private final LeviathanSegment[] parts = new LeviathanSegment[SEGMENTS];
    /** 頭（の中心）の通った跡。新しいものが先頭。 */
    private final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();
    /** 突進のあとに止まっている場所。 */
    @Nullable
    private Vec3 restAt;
    private Vec3 velocity = Vec3.ZERO;
    private double orbitAngle;
    private int attackCooldown = ATTACK_INTERVAL;
    private int whipCooldown;
    private int cycle;
    private boolean chained;
    /** 次の必殺技までの残り（骨格・龍のときだけ減る）。 */
    private int reigniteTimer = REIGNITE_INTERVAL / 2;
    /** 必殺技の目印にする、封じ込め柱の場所（溜め始めに探す）。 */
    private final List<Vec3> pillarMarks = new ArrayList<>();
    /** 地殻の波: 叩きつけた場所と、それぞれの輪に当たった相手。 */
    @Nullable
    private Vec3 quakeAt;
    private final java.util.Set<java.util.UUID> quakeHit1 = new java.util.HashSet<>();
    private final java.util.Set<java.util.UUID> quakeHit2 = new java.util.HashSet<>();
    /** 重力の裂け目: いまの線の向き（ラジアン）と、何回目か。 */
    private double riftAngle;
    private int riftVolley;
    /** 降着の嵐で、これから小さなブラックホールが落ちる場所（落ちるまでの tick）。 */
    private final List<Vec3> stormMarks = new ArrayList<>();
    private final List<Integer> stormTimers = new ArrayList<>();
    @Nullable
    private Vec3 deathStart;
    /** とどめ（/kill など無敵を無視するダメージ）のときは、姿を変える前でも倒れる。 */
    private boolean finishing;
    /** セーブから読み込んでいる最中（体力をそのまま戻す）。 */
    private boolean loading;

    /** 跡の1点: 位置・その時の泳ぐ向き・記録した tick。 */
    public record TrailPoint(Vec3 pos, Vec3 dir, int tick) {}

    public ErgosphereLeviathan(EntityType<? extends ErgosphereLeviathan> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        for (int i = 0; i < SEGMENTS; i++) {
            boolean tail = i == SEGMENTS - 1;
            parts[i] = new LeviathanSegment(this, i, 2 * radius(i), tail ? TAIL_FACTOR : BODY_FACTOR);
        }
        // 部位の番号は本体の番号の続きにする（エンダードラゴンと同じ。クライアントとサーバーで番号がそろう）
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        this.xpReward = 2000;
        this.noPhysics = true;
        this.noCulling = true;
        setNoGravity(true);
        orbitAngle = random.nextDouble() * Math.PI * 2;
        setEffectiveHealth(configuredHealth());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH_CAP)
                .add(Attributes.ATTACK_DAMAGE, CONTACT_DAMAGE)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    static double configuredHealth() {
        return (ServerConfig.SPEC.isLoaded() ? ServerConfig.LEVIATHAN_HEALTH.get() : HEALTH)
                * (BossScaling.hardMode() ? 1.0 : NORMAL_HEALTH_FACTOR);
    }

    /** i 番目の節の太さ（半径）。首が太く、尾へ細くなる。 */
    public static float radius(int i) {
        return 1.15F - 0.62F * i / (SEGMENTS - 1);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < SEGMENTS; i++) {
            if (parts[i] != null) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    public LeviathanSegment[] segments() {
        return parts;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SWIM);
        builder.define(STATE_TICKS, 0);
        builder.define(AIM, new Vector3f());
        builder.define(HOME, BlockPos.ZERO);
        builder.define(FORM, 1);
        builder.define(JET_YAW, 0F);
        builder.define(JET_PITCH, 32F);
        builder.define(RIFT_ANGLE, 0F);
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
    public void awaken(BlockPos core) {
        super.awaken(core);
        entityData.set(HOME, core);
    }

    /** 炉心（封印核）の位置。クライアントにも送る（出現と倒されたときの渦を描くため）。 */
    public BlockPos homePos() {
        return entityData.get(HOME);
    }

    public int state() {
        return entityData.get(STATE);
    }

    /** 今の技の残り tick。 */
    public int stateTicks() {
        return entityData.get(STATE_TICKS);
    }

    @Override
    public String combatState() {
        return super.combatState() + " form=" + form();
    }

    /** 姿（1: 装甲、2: 骨格、3: 龍）。 */
    public int form() {
        return entityData.get(FORM);
    }

    /** 相対論的ジェットの向き（度。頭の上で回る）。 */
    public float jetYaw() {
        return entityData.get(JET_YAW);
    }

    /** 重力の裂け目の、いまの線の向き（ラジアン）。 */
    public float riftAngle() {
        return entityData.get(RIFT_ANGLE);
    }

    public float jetPitch() {
        return entityData.get(JET_PITCH);
    }

    public Vec3 aim() {
        Vector3f v = entityData.get(AIM);
        return new Vec3(v.x, v.y, v.z);
    }

    private void setState(int state, int ticks) {
        entityData.set(STATE, state);
        entityData.set(STATE_TICKS, ticks);
    }

    private void setAim(Vec3 at) {
        entityData.set(AIM, new Vector3f((float) at.x, (float) at.y, (float) at.z));
    }

    /** 姿を変えている最中か（傷つかない）。 */
    public boolean transforming() {
        int s = state();
        return s == MOLT || s == SUBMERGE || s == ASCEND;
    }

    /** 体力が半分（覚醒した個体は 65%）を切ったか（その姿の中で、技が増え速くなる目安）。 */
    public boolean enraged() {
        return form() >= 2 || getHealth() <= getMaxHealth() * enrageFraction();
    }

    /** 頭の中心（実体の位置は足元なので、その1ブロック上）。 */
    public Vec3 headCenter() {
        return position().add(0, getBbHeight() / 2, 0);
    }

    /** 口の位置（頭の中心から前へ）。 */
    public Vec3 mouth() {
        return headCenter().add(Vec3.directionFromRotation(getXRot(), getYRot()).scale(1.1));
    }

    public List<TrailPoint> trail() {
        return new ArrayList<>(trail);
    }

    /** 跡を、帯の描画用に新しいものから順に返す。 */
    public Iterator<TrailPoint> trailIterator() {
        return trail.iterator();
    }

    @Override
    protected BossEvent.BossBarColor awakenedColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected String[][] awakenedRewards() {
        return new String[][] {{"overclock_chip", "2"}, {"jet_condensate", "4"}, {"exotic_matter", "6"}};
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
        // 動きは自分で決める（泳ぐ・突進・出現・姿を変える・倒されたときの沈み方）
    }

    // ------------------------------------------------------------------ 体（跡と節）

    @Override
    public void tick() {
        super.tick();
        recordTrail();
        placeSegments();
    }

    private void recordTrail() {
        Vec3 head = headCenter();
        TrailPoint last = trail.peekFirst();
        if (last == null) {
            seedTrail(head);
            return;
        }
        if (last.pos().distanceToSqr(head) > 0.01) {
            Vec3 dir = head.subtract(last.pos()).normalize();
            trail.addFirst(new TrailPoint(head, dir, tickCount));
            while (trail.size() > 400) {
                trail.removeLast();
            }
        }
    }

    /** 跡がまだないとき: 出現の途中なら炉心の下へ、そうでなければ後ろへまっすぐ伸ばしておく。 */
    private void seedTrail(Vec3 head) {
        Vec3 back = emergeTicks() > 0 ? new Vec3(0, -1, 0)
                : Vec3.directionFromRotation(getXRot(), getYRot()).scale(-1);
        double length = HEAD_GAP + SPACING * SEGMENTS + 2;
        for (double d = length; d >= 0; d -= 0.5) {
            trail.addFirst(new TrailPoint(head.add(back.scale(d)), back.scale(-1), tickCount));
        }
    }

    /** 跡の上で、頭から道のり distance の位置。跡が足りなければ最後の向きのまま延ばす。 */
    public Vec3 alongTrail(double distance) {
        Vec3 prev = null;
        double walked = 0;
        Vec3 lastDir = new Vec3(0, -1, 0);
        for (TrailPoint p : trail) {
            if (prev != null) {
                double step = prev.distanceTo(p.pos());
                if (walked + step >= distance && step > 1e-6) {
                    return prev.lerp(p.pos(), (distance - walked) / step);
                }
                walked += step;
                lastDir = p.pos().subtract(prev).normalize();
            }
            prev = p.pos();
        }
        return prev == null ? headCenter() : prev.add(lastDir.scale(distance - walked));
    }

    private void placeSegments() {
        for (int i = 0; i < SEGMENTS; i++) {
            LeviathanSegment part = parts[i];
            Vec3 c = alongTrail(HEAD_GAP + SPACING * i);
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
            part.xOld = part.getX();
            part.yOld = part.getY();
            part.zOld = part.getZ();
            part.setPos(c.x, c.y - radius(i), c.z);
        }
    }

    /** 体のどこかに当たったとき（部位から呼ばれる）。骨格からは胴も効く。 */
    public boolean hurtPart(LeviathanSegment part, DamageSource source, float amount) {
        float factor = part.index == SEGMENTS - 1 ? TAIL_FACTOR : form() >= 2 ? BODY_FACTOR_2 : BODY_FACTOR;
        return hurt(source, amount * factor);
    }

    /** 姿を変えている最中は傷つかない。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean bypass = source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (transforming() && !bypass) {
            return false;
        }
        finishing = bypass;
        try {
            return super.hurt(source, amount);
        } finally {
            finishing = false;
        }
    }

    /** 体力は、今の姿の境目より下へは減らない（境目で姿を変える）。とどめは別。 */
    @Override
    public void setHealth(float health) {
        boolean guard = !finishing && !loading && !level().isClientSide && emergeTicks() <= 0 && form() < 3;
        if (guard && health > 0) {
            float floor = getMaxHealth() * (form() == 1 ? 2F / 3 : 1F / 3) - 0.01F;
            health = Math.max(health, Math.min(getHealth(), floor));
        } else if (guard && !isDeadOrDying()) {
            health = getMaxHealth() * (form() == 1 ? 2F / 3 : 1F / 3) - 0.01F;
        }
        super.setHealth(health);
    }

    // ------------------------------------------------------------------ 出現

    /** 出現の演出の小さな蛇の位置（炉心の上。t は出現の tick）。クライアントの描画と同じ式。 */
    public static Vec3 hatchlingPos(Vec3 core, float t) {
        float k = Mth.clamp((t - EMERGE_VORTEX) / (HATCH_SETTLE - EMERGE_VORTEX), 0, 1);
        float rise = Mth.clamp(k / 0.1F, 0, 1);              // 渦から勢いよく飛び出す
        float r = 3.0F * Mth.clamp(k / 0.25F, 0, 1) * (1 - Mth.clamp((k - 0.75F) / 0.25F, 0, 1));
        float a = k * Mth.TWO_PI * 2;
        float y = -0.5F + 2.7F * rise + 0.3F * Mth.sin(t * 0.3F);
        return core.add(Mth.cos(a) * r, y, Mth.sin(a) * r);
    }

    @Override
    protected void onEmergeTick(ServerLevel level, int t) {
        if (home == null) {
            return;
        }
        Vec3 core = Vec3.atBottomCenterOf(home).add(0, 1.0, 0);
        emergeBar(t);
        if (t < SWALLOW) {
            // 大蛇の頭はまだ炉心の下。渦が開いて小さな蛇が飛び出し、渦は閉じる。小さな蛇はしばらく漂う
            setPos(core.x, core.y - 4, core.z);
            if (t <= EMERGE_VORTEX) {
                for (int i = 0; i < 4; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double r = 2 + random.nextDouble() * 5;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x + Math.cos(a) * r, core.y + random.nextDouble(),
                            core.z + Math.sin(a) * r, 0, -Math.cos(a), -0.1, -Math.sin(a), 0.3);
                }
            }
            if (t == 1) {
                level.playSound(null, home, SinguloSounds.get("ergosphere_leviathan.emerge"), SoundSource.HOSTILE, 2.0F, 1.0F);
            }
            if (t == EMERGE_VORTEX) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y + 0.5, core.z, 30, 0.4, 0.4, 0.4, 0.4);
                level.playSound(null, home, SinguloSounds.get("ergosphere_leviathan.hatchling"), SoundSource.HOSTILE, 1.5F, 1.0F);
            }
            if (t > EMERGE_VORTEX && t < HATCH_SETTLE && (t - EMERGE_VORTEX) % 16 == 0) {
                Vec3 h = hatchlingPos(core, t);
                level.playSound(null, h.x, h.y, h.z, SinguloSounds.get("ergosphere_leviathan.hatchling"), SoundSource.HOSTILE, 1.0F,
                        1.05F + 0.15F * random.nextFloat());
            }
            if (t == RIFT_SNAP) {
                // 前ぶれなく渦が開き、床が裂ける
                level.playSound(null, home, SinguloSounds.get("ergosphere_leviathan.burst"), SoundSource.HOSTILE, 4.0F, 1.0F);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
                        core.x, core.y - 0.5, core.z, 40, 2.5, 0.1, 2.5, 0.35);
            }
            return;
        }
        if (t < SWALLOW_END) {
            // 真下から顎がすごい勢いで噴き上がり、小さな蛇を丸呑みにする
            double k = (double) (t - SWALLOW) / (SWALLOW_END - SWALLOW);
            Vec3 at = core.add(0, -2 + 7 * Math.sqrt(k), 0);
            setPos(at.x, at.y - getBbHeight() / 2, at.z);
            setXRot(-80);
            if (t == SWALLOW) {
                level.sendParticles(ParticleTypes.SONIC_BOOM, core.x, core.y + 0.5, core.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.EXPLOSION, core.x, core.y, core.z, 8, 2, 0.3, 2, 0);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
                        core.x, core.y, core.z, 80, 2.5, 0.5, 2.5, 0.5);
                playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 4.0F, 0.7F);
            }
            if (t == SWALLOW + 4) {
                playSound(SinguloSounds.get("ergosphere_leviathan.swallow"), 3.0F, 1.0F);
            }
            return;
        }
        if (t == SWALLOW_END) {
            playSound(SinguloSounds.get("ergosphere_leviathan.rise"), 4.0F, 1.0F);
        }
        // 螺旋を描いて泳ぐ高さまで昇る（体は跡をなぞって炉心から引き出される）
        double k = (double) (t - SWALLOW_END) / (EMERGE_TICKS - SWALLOW_END);
        double ease = k * k * (3 - 2 * k);
        double a = ease * Math.PI * 2.5;
        double r = 0.5 + ORBIT * 0.75 * ease;
        Vec3 at = core.add(Math.cos(a) * r, 5 + (SWIM_HEIGHT - 5) * ease, Math.sin(a) * r);
        Vec3 before = headCenter();
        setPos(at.x, at.y - getBbHeight() / 2, at.z);
        face(at.subtract(before));
        if (t % 3 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
                    core.x, core.y, core.z, 8, 0.8, 0.2, 0.8, 0.2);
        }
    }

    /**
     * 出現の間のボスバー: 小さな蛇が飛び出すと「エルゴ球の蛇」として現れ、呑まれる顎の動きに合わせて削り取られる。
     * 呑まれたあとは本来の名前に替わり、昇り終えるまでに満ちていく。
     */
    /** 呑まれるときの、顎の動きに合わせたボスバーの残り（呑み込み始めの次の tick から 1 tick ずつ）。 */
    private static final float[] SWALLOW_BITES = {0.62F, 0.28F, 0.28F, 0.08F, 0.08F, 0F};

    private void emergeBar(int t) {
        if (t == EMERGE_VORTEX) {
            bossBar.setName(Component.translatable("entity.singulo.ergosphere_leviathan.hatchling"));
            bossBar.setProgress(1);
            bossBar.setVisible(true);
        } else if (t > SWALLOW && t <= SWALLOW + SWALLOW_BITES.length) {
            bossBar.setProgress(SWALLOW_BITES[t - SWALLOW - 1]);      // ひと噛みごとに大きく削れる
        } else if (t == SWALLOW_END) {
            refreshBarName();
            bossBar.setProgress(0);
        } else if (t > SWALLOW_END) {
            bossBar.setProgress(Math.min(1, (float) (t - SWALLOW_END) / (EMERGE_TICKS - SWALLOW_END - 10)));
        }
    }

    @Override
    protected void onEmerged(ServerLevel level) {
        velocity = Vec3.directionFromRotation(getXRot(), getYRot()).scale(SWIM_SPEED);
        orbitAngle = home == null ? 0 : Math.atan2(getZ() - (home.getZ() + 0.5), getX() - (home.getX() + 0.5));
        playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 4.0F, 0.9F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12), e -> e != this && !RuinGuards.isGuard(e))) {
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
            if (push.lengthSqr() > 1e-4) {
                e.push(push.normalize().x * 0.9, 0.4, push.normalize().z * 0.9);
                e.hurtMarked = true;
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
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !challenger(target))) {
            target = null;
        }
        int ticks = stateTicks();
        if (ticks > 0) {
            entityData.set(STATE_TICKS, ticks - 1);
        }
        if (!transforming() && form() < 3 && getHealth() <= getMaxHealth() * (form() == 1 ? 2F / 3 : 1F / 3)) {
            startTransform(level);
            return;                 // 姿を変えるのは次の tick から
        }
        switch (state()) {
            case DIVE_WINDUP -> tickDiveWindup(level, target, ticks);
            case DIVE -> tickDive(level, ticks);
            case BREATH_CHARGE -> tickBreathCharge(level, target, ticks);
            case BREATH_FIRE -> tickBreathFire(level, target, ticks);
            case TAIL_WHIP -> tickTailWhip(level, ticks);
            case MOLT -> tickMolt(level, ticks);
            case COIL -> tickCoil(level, ticks);
            case BITE -> tickBite(level, target, ticks);
            case SUBMERGE -> tickSubmerge(level, ticks);
            case ASCEND -> tickAscend(level, ticks);
            case JET -> tickJet(level, target, ticks);
            case STORM -> tickStorm(level, ticks);
            case REST -> tickRest(level, ticks);
            case REIGNITE -> tickReignite(level, ticks);
            case QUAKE -> tickQuake(level, ticks);
            case RIFT -> tickRift(level, ticks);
            case TIDE -> tickTide(level, ticks);
            default -> tickSwim(level, target);
        }
        tickStormMarks(level);
        if (!transforming()) {
            pushWake(level);
            contactDamage(level);
        }
        if (awakened() && tickCount % 4 == 0) {
            Vec3 c = headCenter();
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 2, 0.6, 0.6, 0.6, 0.01);
        }
    }

    private Vec3 arenaCenter() {
        return Vec3.atBottomCenterOf(home).add(0, 1, 0);
    }

    private double swimSpeed() {
        double s = form() == 3 ? ENRAGED_SPEED * 1.15 : enraged() ? ENRAGED_SPEED : SWIM_SPEED;
        return awakened() ? s * 1.15 : s;
    }

    /** 部屋の上空を輪を描いて泳ぐ。間をおいて技を選ぶ。尾のそばに相手がいれば薙ぎ払う。 */
    private void tickSwim(ServerLevel level, @Nullable LivingEntity target) {
        double speed = swimSpeed();
        orbitAngle += speed / ORBIT;
        Vec3 c = arenaCenter();
        double height = form() == 3 ? SWIM_HEIGHT + 2 : SWIM_HEIGHT;
        Vec3 goal = c.add(Math.cos(orbitAngle) * ORBIT, height + Math.sin(tickCount * 0.03) * 4, Math.sin(orbitAngle) * ORBIT);
        steer(goal, speed, 0.08);
        if (--whipCooldown <= 0 && form() == 1 && playerNearTail(level)) {
            whipCooldown = pace(70);
            setState(TAIL_WHIP, WHIP_TICKS);
            playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 2.0F, 1.6F);
            return;
        }
        if (form() >= 2 && --reigniteTimer <= 0 && target != null) {
            startReignite(level);
            return;
        }
        if (target != null && --attackCooldown <= 0) {
            chooseAttack(level, target);
        }
    }

    private void chooseAttack(ServerLevel level, LivingEntity target) {
        attackCooldown = pace((form() == 3 ? 60 : form() == 2 ? 70 : ATTACK_INTERVAL) + random.nextInt(30));
        chained = false;
        int step = cycle++;
        double dist = target.position().distanceTo(headCenter());
        switch (form()) {
            case 1 -> {
                switch (step % 6) {
                    case 0, 3 -> startDive();
                    case 1 -> startBreath();
                    case 2 -> startQuake(level);
                    case 4 -> startRift(level);
                    default -> {
                        if (enraged()) {
                            shedScales(level);
                        } else {
                            startBreath();
                        }
                    }
                }
            }
            case 2 -> {
                if (dist < 9 && step % 2 == 1) {
                    setState(BITE, BITE_TICKS);
                    playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 2.5F, 1.4F);
                    return;
                }
                switch (step % 7) {
                    case 0 -> startDive();
                    case 1 -> {
                        setState(COIL, pace(COIL_TICKS));
                        playSound(SinguloSounds.get("ergosphere_leviathan.coil"), 3.0F, 1.0F);
                    }
                    case 2 -> shedShards(level);
                    case 3 -> startQuake(level);
                    case 4 -> startRift(level);
                    case 5 -> startTide(level);
                    default -> startBreath();
                }
            }
            default -> {
                switch (step % 7) {
                    case 4 -> startQuake(level);
                    case 5 -> startRift(level);
                    case 6 -> startTide(level);
                    case 0 -> {
                        setState(JET, JET_TICKS);
                        entityData.set(JET_YAW, getYRot());
                        playSound(SinguloSounds.get("ergosphere_leviathan.jet_charge"), 4.0F, 1.0F);
                    }
                    case 1 -> startDive();
                    case 2 -> {
                        setState(STORM, STORM_TICKS);
                        playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 4.0F, 0.6F);
                    }
                    default -> startBreath();
                }
            }
        }
    }

    private void startDive() {
        setState(DIVE_WINDUP, pace(DIVE_WINDUP_TICKS));
        playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 3.0F, 1.1F);
    }

    private void startBreath() {
        setState(BREATH_CHARGE, pace(BREATH_CHARGE_TICKS));
        playSound(SinguloSounds.get("ergosphere_leviathan.breath_charge"), 3.0F, 1.0F);
    }

    /** 突進の身構え: 相手の上の、部屋の外側寄りへ昇る。終わると狙いを決めて突進する。 */
    private void tickDiveWindup(ServerLevel level, @Nullable LivingEntity target, int ticks) {
        if (target == null) {
            setState(SWIM, 0);
            return;
        }
        Vec3 c = arenaCenter();
        Vec3 out = target.position().subtract(c).multiply(1, 0, 1);
        out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
        Vec3 perch = target.position().add(out.scale(-6)).add(0, 8, 0);
        steer(perch, swimSpeed() * 1.1, 0.14);
        Vec3 head = headCenter();
        level.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 2, 0.5, 0.5, 0.5, 0.02);
        if (ticks <= 1) {
            Vec3 at = target.getBoundingBox().getCenter();
            setAim(at);
            velocity = at.subtract(head).normalize().scale(form() == 3 ? DIVE_SPEED * 1.15 : DIVE_SPEED);
            setState(DIVE, DIVE_TICKS);
            playSound(SinguloSounds.get("ergosphere_leviathan.dive"), 3.0F, 1.0F);
        }
    }

    /** 突進: 狙った場所を一直線に突き抜ける。部屋の端か床に近づくか、狙いを大きく越えたら終わる。龍は着地点に特異点を残す。 */
    private void tickDive(ServerLevel level, int ticks) {
        Vec3 aim = aim();
        Vec3 head = headCenter();
        moveBy(velocity);
        Vec3 c = arenaCenter();
        boolean past = head.subtract(aim).dot(velocity) > 0 && head.distanceTo(aim) > 6;
        boolean floor = head.y < c.y + 1.5;
        boolean edge = head.subtract(c).horizontalDistance() > ARENA - 6 || floor || head.y > c.y + SWIM_HEIGHT + 12;
        if (tickCount % 2 == 0) {
            level.sendParticles(form() == 3 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.CLOUD, head.x, head.y, head.z, 3, 0.4, 0.4, 0.4, 0.02);
        }
        if (ticks <= 1 || past || edge) {
            if (floor) {
                // 床をかすめて舞い上がる（床に土煙）
                level.sendParticles(ParticleTypes.EXPLOSION, head.x, c.y, head.z, 3, 1, 0.2, 1, 0);
                velocity = new Vec3(velocity.x, Math.abs(velocity.y) * 0.6 + 0.2, velocity.z);
            }
            LivingEntity target = getTarget();
            float chain = form() >= 2 ? 0.7F : 0.4F;
            if (enraged() && !chained && target != null && challenger(target) && random.nextFloat() < (awakened() ? chain + 0.15F : chain)) {
                chained = true;
                setState(DIVE_WINDUP, pace(14));
                playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 2.5F, 1.3F);
            } else if (floor) {
                // 床をかすめたら、そこでしばらくとぐろを巻いて止まる（反撃の隙）
                restAt = new Vec3(head.x, c.y, head.z);
                velocity = velocity.multiply(1, 0, 1).scale(0.3);
                setState(REST, REST_TICKS);
            } else {
                backToSwim();
            }
        }
    }

    // ---- 必殺技「炉心の再点火」

    private void startReignite(ServerLevel level) {
        reigniteTimer = pace(REIGNITE_INTERVAL);
        setState(REIGNITE, REIGNITE_TICKS);
        findPillars(level);
        showTitle(level, "gui.singulo.leviathan.reignite", "gui.singulo.leviathan.reignite_hint");
        playSound(SinguloSounds.get("ergosphere_leviathan.jet_charge"), 5.0F, 0.6F);
    }

    /** 炉心のまわり（半径11〜15）の、床から立つ柱を探して目印にする（向きを変えて置かれた遺構でも合うように）。 */
    private void findPillars(ServerLevel level) {
        pillarMarks.clear();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int y = home.getY() + 6;
        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -15; dz <= 15; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 11 || d > 15) {
                    continue;
                }
                p.set(home.getX() + dx, y, home.getZ() + dz);
                if (!level.getBlockState(p).isAir() && !level.getBlockState(p.below(2)).isAir()) {
                    pillarMarks.add(Vec3.atCenterOf(p));
                }
            }
        }
    }

    /** 溜めて、炉心から部屋じゅうへ衝撃を放つ。柱の陰（炉心から見て遮られた所）にいる相手には当たらない。 */
    private void tickReignite(ServerLevel level, int ticks) {
        int t = REIGNITE_TICKS - ticks;
        Vec3 core = Vec3.atBottomCenterOf(home).add(0, 2.0, 0);
        if (t < REIGNITE_CHARGE) {
            // 炉心のまわりを低く巻く
            orbitAngle += 0.09;
            steer(core.add(Math.cos(orbitAngle) * 4, 1.0, Math.sin(orbitAngle) * 4), 0.3, 0.2);
            float k = t / (float) REIGNITE_CHARGE;
            // 炉心から昇る光の柱（溜まるほど太く高く）
            for (int i = 0; i < 4 + (int) (k * 8); i++) {
                double h = random.nextDouble() * (6 + k * 14);
                level.sendParticles(ParticleTypes.END_ROD, core.x + (random.nextDouble() - 0.5) * (0.6 + k),
                        core.y - 1 + h, core.z + (random.nextDouble() - 0.5) * (0.6 + k), 1, 0, 0.15, 0, 0.02);
            }
            // 部屋の縁から炉心へ縮んでいく光の輪
            double ring = (ARENA - 2) * (1 - (t % 30) / 30.0);
            for (int i = 0; i < 40; i++) {
                double a = Math.PI * 2 * i / 40;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, core.x + Math.cos(a) * ring, core.y - 1.6, core.z + Math.sin(a) * ring,
                        1, 0, 0, 0, 0);
            }
            if (t % 2 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y, core.z, 20, 3 + k * 4, 2, 3 + k * 4, 0.4);
                // 柱は高く明るく光って、隠れる場所を示す
                for (Vec3 m : pillarMarks) {
                    level.sendParticles(ParticleTypes.GLOW, m.x, m.y, m.z, 2, 0.35, 3.5, 0.35, 0);
                    level.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 4, m.z, 1, 0.2, 2.5, 0.2, 0);
                }
            }
            if (t % 20 == 0) {
                level.playSound(null, core.x, core.y, core.z, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
                        net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.5F + k);
            }
            if (t == REIGNITE_CHARGE - 40) {
                playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 5.0F, 0.7F);
                level.playSound(null, core.x, core.y, core.z, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_CHARGE,
                        net.minecraft.sounds.SoundSource.HOSTILE, 5.0F, 0.6F);
            }
            return;
        }
        if (t == REIGNITE_CHARGE) {
            level.sendParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 8, 2, 2, 2, 0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 4, 2, 1, 2, 0);
            level.sendParticles(ParticleTypes.END_ROD, core.x, core.y, core.z, 500, 0.3, 0.3, 0.3, 1.8);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, core.x, core.y, core.z, 200, 0.5, 0.5, 0.5, 1.2);
            // 部屋いっぱいに広がる衝撃の輪
            for (int r = 3; r <= ARENA; r += 3) {
                int n = r * 3;
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    level.sendParticles(r % 6 == 0 ? ParticleTypes.SONIC_BOOM : ParticleTypes.EXPLOSION,
                            core.x + Math.cos(a) * r, core.y - 1, core.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            playSound(SinguloSounds.get("ergosphere_leviathan.jet"), 6.0F, 0.5F);
            level.playSound(null, core.x, core.y, core.z, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                    net.minecraft.sounds.SoundSource.HOSTILE, 6.0F, 0.5F);
            level.playSound(null, core.x, core.y, core.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 6.0F, 0.4F);
            float damage = BossScaling.hardMode() ? REIGNITE_DAMAGE : REIGNITE_DAMAGE_NORMAL;
            for (Player p : challengers(level, ARENA + 4)) {
                Vec3 eye = p.getEyePosition();
                HitResult hit = level.clip(new ClipContext(core, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(eye) > 0.6) {
                    continue;                                   // 柱の陰
                }
                hurtWith("reignite", p, io.github.genichimaruo.singulo.registry.SinguloDamageTypes.tidal(level, this), damage);
                Vec3 away = p.position().subtract(core).multiply(1, 0, 1);
                Vec3 dir = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                p.setDeltaMovement(dir.x * 1.6, 0.7, dir.z * 1.6);
                p.hurtMarked = true;
            }
        }
        if (ticks <= 1) {
            pillarMarks.clear();
            backToSwim();
        }
    }

    // ---- 地殻の波

    private void startQuake(ServerLevel level) {
        setState(QUAKE, QUAKE_TICKS);
        quakeAt = null;
        quakeHit1.clear();
        quakeHit2.clear();
        actionBar(level, "gui.singulo.leviathan.quake");
        playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 4.0F, 0.8F);
    }

    /** 高く昇って身を丸め、中心へ叩きつける。床を伝って衝撃の輪が2つ広がる（床に立っていると当たる）。 */
    private void tickQuake(ServerLevel level, int ticks) {
        int t = QUAKE_TICKS - ticks;
        Vec3 c = arenaCenter();
        if (t < QUAKE_RISE) {
            steer(c.add(0, SWIM_HEIGHT + 8, 0), swimSpeed() * 1.6, 0.25);
            Vec3 h = headCenter();
            level.sendParticles(ParticleTypes.END_ROD, h.x, h.y, h.z, 6, 0.8, 0.8, 0.8, 0.05);
            if (t > QUAKE_RISE - 12) {
                // 身を丸めて力を溜める（頭のまわりに光が集まる）
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, h.x, h.y, h.z, 30, 3, 3, 3, 0.6);
            }
            return;
        }
        if (quakeAt == null) {
            // 叩きつける
            steer(c.add(0, 1.2, 0), 1.8, 0.6);
            if (headCenter().y > c.y + 2.5 && t < QUAKE_RISE + 12) {
                return;
            }
            quakeAt = new Vec3(headCenter().x, c.y, headCenter().z);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, quakeAt.x, quakeAt.y + 0.5, quakeAt.z, 3, 1.5, 0.3, 1.5, 0);
            level.sendParticles(ParticleTypes.FLASH, quakeAt.x, quakeAt.y + 1, quakeAt.z, 2, 0.5, 0.5, 0.5, 0);
            level.sendParticles(floorDust(level, quakeAt), quakeAt.x, quakeAt.y + 0.5, quakeAt.z, 300, 2.5, 0.5, 2.5, 0.6);
            level.sendParticles(ParticleTypes.SONIC_BOOM, quakeAt.x, quakeAt.y + 1, quakeAt.z, 1, 0, 0, 0, 0);
            playSound(SinguloSounds.get("ergosphere_leviathan.dive"), 5.0F, 0.6F);
            level.playSound(null, quakeAt.x, quakeAt.y, quakeAt.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 6.0F, 0.5F);
            velocity = Vec3.ZERO;
            restAt = quakeAt;
            entityData.set(STATE_TICKS, 70);               // ここから輪が広がりきるまで
            return;
        }
        int since = 70 - ticks;
        orbitAngle += 0.08;
        steer(quakeAt.add(Math.cos(orbitAngle) * 3, 1.2, Math.sin(orbitAngle) * 3), 0.18, 0.2);
        wave(level, since * QUAKE_WAVE_SPEED, quakeHit1);
        if (since >= QUAKE_SECOND_WAVE) {
            wave(level, (since - QUAKE_SECOND_WAVE) * QUAKE_WAVE_SPEED, quakeHit2);
        }
        if (ticks <= 1) {
            quakeAt = null;
            restAt = null;
            backToSwim();
        }
    }

    /** 半径 r の衝撃の輪: 見た目を出し、輪の上で床に立っている相手に当てる（1つの輪で1度まで）。 */
    private void wave(ServerLevel level, double r, java.util.Set<java.util.UUID> hit) {
        if (r <= 0.5 || r > ARENA) {
            return;
        }
        // 床の破片が吹き上がる壁として見せる
        int n = Math.min(72, (int) (r * 4));
        net.minecraft.core.particles.ParticleOptions dust = floorDust(level, quakeAt);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = quakeAt.x + Math.cos(a) * r;
            double z = quakeAt.z + Math.sin(a) * r;
            level.sendParticles(dust, x, quakeAt.y + 0.4, z, 3, 0.15, 0.4, 0.15, 0.25);
            level.sendParticles(ParticleTypes.CLOUD, x, quakeAt.y + 0.3, z, 1, 0, 0.15, 0, 0.02);
            if (i % 6 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, quakeAt.y + 0.8, z, 2, 0.1, 0.4, 0.1, 0.1);
            }
        }
        if ((int) (r / QUAKE_WAVE_SPEED) % 8 == 0) {
            level.playSound(null, quakeAt.x, quakeAt.y, quakeAt.z, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                    net.minecraft.sounds.SoundSource.HOSTILE, 2.5F, 0.6F + (float) (r / ARENA));
        }
        float damage = BossScaling.hardMode() ? QUAKE_DAMAGE : QUAKE_DAMAGE_NORMAL;
        for (Player p : challengers(level, ARENA + 4)) {
            double d = Math.hypot(p.getX() - quakeAt.x, p.getZ() - quakeAt.z);
            if (Math.abs(d - r) > 0.9 || !p.onGround() || p.getY() > quakeAt.y + 2.5 || !hit.add(p.getUUID())) {
                continue;
            }
            hurtWith("quake", p, io.github.genichimaruo.singulo.registry.SinguloDamageTypes.tidal(level, this), damage);
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.6, 0));
            p.hurtMarked = true;
        }
    }

    // ---- 重力の裂け目

    private void startRift(ServerLevel level) {
        riftVolley = 0;
        riftAngle = random.nextDouble() * Math.PI;
        entityData.set(RIFT_ANGLE, (float) riftAngle);
        setState(RIFT, RIFT_VOLLEYS * (RIFT_WARN + RIFT_GAP));
        actionBar(level, "gui.singulo.leviathan.rift");
        playSound(SinguloSounds.get("ergosphere_leviathan.breath_charge"), 4.0F, 1.4F);
    }

    /** 床に光の線を引き、少しあとで線に沿って空間を裂く。向きを変えて繰り返す。 */
    private void tickRift(ServerLevel level, int ticks) {
        Vec3 c = arenaCenter();
        orbitAngle += swimSpeed() / ORBIT;
        steer(c.add(Math.cos(orbitAngle) * ORBIT, SWIM_HEIGHT + 4, Math.sin(orbitAngle) * ORBIT), swimSpeed(), 0.08);
        int length = RIFT_WARN + RIFT_GAP;
        int t = RIFT_VOLLEYS * length - ticks;
        int inVolley = t % length;
        if (inVolley == 0) {
            entityData.set(RIFT_ANGLE, (float) riftAngle);
        }
        Vec3 along = new Vec3(Math.cos(riftAngle), 0, Math.sin(riftAngle));
        Vec3 across = new Vec3(-along.z, 0, along.x);
        if (inVolley < RIFT_WARN) {
            // 点滅しながら太くなっていく予告の線（幅の両端に2列）
            boolean on = inVolley % 4 < 2 || inVolley > RIFT_WARN - 8;
            if (on) {
                for (int k = -1; k <= 1; k++) {
                    Vec3 mid = c.add(across.scale(k * RIFT_SPACING));
                    for (double s = -RIFT_REACH; s <= RIFT_REACH; s += 1.2) {
                        for (int edge = -1; edge <= 1; edge += 2) {
                            Vec3 q = mid.add(along.scale(s)).add(across.scale(edge * RIFT_WIDTH * 0.8));
                            level.sendParticles(inVolley > RIFT_WARN - 8 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                                    q.x, c.y + 0.15, q.z, 1, 0, 0, 0, 0);
                        }
                    }
                }
            }
            if (inVolley == RIFT_WARN - 10) {
                level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE,
                        net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 1.6F);
            }
            return;
        }
        if (inVolley == RIFT_WARN) {
            float damage = BossScaling.hardMode() ? RIFT_DAMAGE : RIFT_DAMAGE_NORMAL;
            // 線に沿って黒い光の壁が立ち上がる
            for (int k = -1; k <= 1; k++) {
                Vec3 mid = c.add(across.scale(k * RIFT_SPACING));
                for (double s = -RIFT_REACH; s <= RIFT_REACH; s += 1.5) {
                    Vec3 q = mid.add(along.scale(s));
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, q.x, c.y + 2.5, q.z, 10, 0.25, 2.2, 0.25, 0.4);
                    level.sendParticles(ParticleTypes.SQUID_INK, q.x, c.y + 1.5, q.z, 2, 0.15, 1.2, 0.15, 0.02);
                    level.sendParticles(ParticleTypes.END_ROD, q.x, c.y + 0.2, q.z, 1, 0.2, 0.05, 0.2, 0.08);
                }
                Vec3 end1 = mid.add(along.scale(RIFT_REACH));
                Vec3 end2 = mid.add(along.scale(-RIFT_REACH));
                level.sendParticles(ParticleTypes.SONIC_BOOM, end1.x, c.y + 1.5, end1.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.SONIC_BOOM, end2.x, c.y + 1.5, end2.z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                    net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 1.5F);
            for (Player p : challengers(level, ARENA + 4)) {
                if (riftHits(c, riftAngle, p.position())) {
                    hurtWith("rift", p, damageSources().indirectMagic(this, this), damage);
                }
            }
            playSound(SinguloSounds.get("ergosphere_leviathan.jet"), 3.0F, 1.6F);
            riftVolley++;
            // 次は向きを60度変える
            riftAngle += Math.PI / 3;
        }
        if (ticks <= 1) {
            backToSwim();
        }
    }

    /** 重力の裂け目の、予告の線の長さ（中心から片側）。描画と当たりで同じ長さを使う。 */
    public static final double RIFT_REACH = ARENA - 4;

    /**
     * pos が、向き angle の裂け目（中心 c、平行な3本）に当たるか。予告の線の上（幅 RIFT_WIDTH、長さ ±RIFT_REACH）で、
     * 床から高さ4ブロックまで。
     */
    public static boolean riftHits(Vec3 c, double angle, Vec3 pos) {
        Vec3 along = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        Vec3 across = new Vec3(-along.z, 0, along.x);
        Vec3 off = pos.subtract(c);
        double side = off.x * across.x + off.z * across.z;
        double length = off.x * along.x + off.z * along.z;
        double nearest = Math.round(side / RIFT_SPACING);
        return Math.abs(nearest) <= 1 && Math.abs(side - nearest * RIFT_SPACING) <= RIFT_WIDTH && Math.abs(length) <= RIFT_REACH
                && pos.y >= c.y - 1 && pos.y < c.y + 4;
    }

    // ---- 引き潮

    private void startTide(ServerLevel level) {
        setState(TIDE, TIDE_TICKS);
        actionBar(level, "gui.singulo.leviathan.tide");
        playSound(SinguloSounds.get("ergosphere_leviathan.storm"), 4.0F, 0.6F);
    }

    /** 炉心の渦が開き、部屋じゅうを中心へ引き込む。炉心の近くでは潮汐ダメージ。 */
    private void tickTide(ServerLevel level, int ticks) {
        Vec3 c = arenaCenter();
        orbitAngle += swimSpeed() * 1.3 / ORBIT;
        steer(c.add(Math.cos(orbitAngle) * (ORBIT - 2), SWIM_HEIGHT - 3, Math.sin(orbitAngle) * (ORBIT - 2)), swimSpeed() * 1.3, 0.1);
        float damage = BossScaling.hardMode() ? TIDE_DAMAGE : TIDE_DAMAGE_NORMAL;
        for (Player p : challengers(level, ARENA + 4)) {
            Vec3 to = c.subtract(p.position()).multiply(1, 0, 1);
            double d = to.length();
            if (d > 0.5) {
                p.setDeltaMovement(p.getDeltaMovement().add(to.normalize().scale(TIDE_PULL)));
                p.hurtMarked = true;
            }
            if (d <= TIDE_CORE_RADIUS && ticks % 10 == 0) {
                hurtWith("tide", p, io.github.genichimaruo.singulo.registry.SinguloDamageTypes.tidal(level, this), damage);
            }
        }
        int t = TIDE_TICKS - ticks;
        // 床の渦巻き: 4本の腕が回りながら中心へ流れ込む
        double spin = t * 0.12;
        for (int arm = 0; arm < 4; arm++) {
            for (double r = 2; r < ARENA - 2; r += 1.4) {
                double a = spin + arm * Math.PI / 2 + r * 0.22;
                double x = c.x + Math.cos(a) * r;
                double z = c.z + Math.sin(a) * r;
                level.sendParticles(r < TIDE_CORE_RADIUS ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.REVERSE_PORTAL, x, c.y + 0.2, z,
                        0, -Math.cos(a) * 0.6, 0, -Math.sin(a) * 0.6, 0.5);
            }
        }
        // 炉心の上の渦の柱
        if (ticks % 2 == 0) {
            for (int i = 0; i < 14; i++) {
                double a = spin * 2 + i * Math.PI * 2 / 14;
                double h = (i * 0.9 + t * 0.3) % 12;
                double rr = TIDE_CORE_RADIUS * (1 - h / 14);
                level.sendParticles(ParticleTypes.PORTAL, c.x + Math.cos(a) * rr, c.y + h, c.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.5, c.z, 6, TIDE_CORE_RADIUS * 0.4, 0.2, TIDE_CORE_RADIUS * 0.4, 0.01);
        }
        if (t % 20 == 0) {
            level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.CONDUIT_AMBIENT,
                    net.minecraft.sounds.SoundSource.HOSTILE, 5.0F, 0.5F);
            level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,
                    net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.6F);
        }
        if (ticks <= 1) {
            backToSwim();
        }
    }

    /** その場所の床のブロックの破片（地殻の波で吹き上がる）。 */
    private static net.minecraft.core.particles.ParticleOptions floorDust(ServerLevel level, Vec3 at) {
        BlockState floor = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (floor.isAir()) {
            floor = plate();
        }
        return new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, floor);
    }

    /** 挑戦者の画面に大きな文字（題と、隠れ方の手がかり）を出す。 */
    private void showTitle(ServerLevel level, String title, String hint) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(arenaCenter(), arenaCenter()).inflate(ARENA + 16))) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 70, 15));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable(hint)));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable(title)));
        }
    }

    /** 挑戦者のホットバーの上に文字を出す。 */
    private void actionBar(ServerLevel level, String key) {
        Component msg = Component.translatable(key);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(arenaCenter(), arenaCenter()).inflate(ARENA + 16))) {
            p.displayClientMessage(msg, true);
        }
    }

    /** 突進のあとの隙: 床の近くで小さく輪を描いて止まる。龍は飛び去るときに、その場所へ特異点を残す。 */
    private void tickRest(ServerLevel level, int ticks) {
        Vec3 at = restAt != null ? restAt : arenaCenter();
        orbitAngle += 0.08;
        Vec3 goal = at.add(Math.cos(orbitAngle) * 3, 1.2, Math.sin(orbitAngle) * 3);
        steer(goal, 0.18, 0.2);
        if (tickCount % 6 == 0) {
            Vec3 h = headCenter();
            level.sendParticles(ParticleTypes.CLOUD, h.x, at.y + 0.2, h.z, 2, 0.6, 0.1, 0.6, 0.01);
        }
        if (ticks <= 1) {
            if (form() == 3) {
                level.addFreshEntity(new WardenSingularity(level, this, at.add(0, 1, 0)));
            }
            playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 2.5F, 1.2F);
            restAt = null;
            backToSwim();
        }
    }

    private void backToSwim() {
        Vec3 c = arenaCenter();
        velocity = velocity.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0).scale(swimSpeed()) : velocity.normalize().scale(swimSpeed());
        orbitAngle = Math.atan2(getZ() - c.z, getX() - c.x);
        setState(SWIM, 0);
    }

    /** 渦の吐息のため: 止まって相手へ向き、口の渦で前にいる相手を吸い寄せる。 */
    private void tickBreathCharge(ServerLevel level, @Nullable LivingEntity target, int ticks) {
        if (target == null) {
            setState(SWIM, 0);
            return;
        }
        velocity = velocity.scale(0.85);
        steer(target.getEyePosition(), Math.max(0.03, velocity.length()), 0.25);
        Vec3 mouth = mouth();
        Vec3 forward = Vec3.directionFromRotation(getXRot(), getYRot());
        for (Player p : challengers(level, BREATH_PULL_RANGE)) {
            Vec3 to = mouth.subtract(p.position().add(0, 1, 0));
            double d = to.length();
            if (d < 1e-3 || d > BREATH_PULL_RANGE || forward.dot(to.scale(-1 / d)) < 0.55) {
                continue;
            }
            p.setDeltaMovement(p.getDeltaMovement().add(to.normalize().scale(0.055 * (1 - d / BREATH_PULL_RANGE))));
            p.hurtMarked = true;
        }
        for (int i = 0; i < 3; i++) {
            Vec3 from = mouth.add(forward.scale(4 + random.nextDouble() * 8)).add((random.nextDouble() - 0.5) * 6,
                    (random.nextDouble() - 0.5) * 6, (random.nextDouble() - 0.5) * 6);
            Vec3 v = mouth.subtract(from).scale(0.08);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1);
        }
        if (ticks <= 1) {
            setAim(target.getBoundingBox().getCenter());
            setState(BREATH_FIRE, BREATH_FIRE_TICKS + (form() == 3 ? 12 : 0));
            playSound(SinguloSounds.get("ergosphere_leviathan.breath_fire"), 4.0F, 1.0F);
        }
    }

    /** 渦の光線を吐く。体力半分から（と龍）は、相手を追って向きを変える。 */
    private void tickBreathFire(ServerLevel level, @Nullable LivingEntity target, int ticks) {
        velocity = Vec3.ZERO;
        Vec3 mouth = mouth();
        Vec3 dir = aim().subtract(mouth).normalize();
        if ((enraged() || awakened()) && target != null) {
            Vec3 want = target.getBoundingBox().getCenter().subtract(mouth).normalize();
            double follow = form() == 3 ? 0.09 : 0.05;
            dir = dir.scale(1 - follow).add(want.scale(follow)).normalize();
        }
        face(dir);
        Vec3 end = fireRay(level, mouth, dir, BREATH_RANGE, BREATH_WIDTH, BossScaling.hardMode() ? BREATH_DAMAGE : BREATH_DAMAGE_NORMAL,
                ticks % 4 == 0);
        setAim(end);
        for (int i = 0; i <= 10; i++) {
            Vec3 q = mouth.lerp(end, i / 10.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, q.x, q.y, q.z, 1, 0.2, 0.2, 0.2, 0.05);
        }
        if (ticks <= 1) {
            velocity = dir.scale(swimSpeed());
            backToSwim();
        }
    }

    /** from から dir へ光線を通し、当たった相手を傷つける（damageNow のとき）。光線の終わりを返す。 */
    private Vec3 fireRay(ServerLevel level, Vec3 from, Vec3 dir, double range, double width, float damage, boolean damageNow) {
        Vec3 to = from.add(dir.scale(range));
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
        if (damageNow) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(width),
                    e -> e.isAlive() && e != this && !RuinGuards.isGuard(e))) {
                if (!e.getBoundingBox().inflate(width / 2).clip(from, end).isEmpty()) {
                    hurtWith("ray", e, damageSources().indirectMagic(this, this), damage);
                }
            }
        }
        return end;
    }

    // ---- 装甲: 尾の薙ぎ払い

    private boolean playerNearTail(ServerLevel level) {
        for (Player p : challengers(level, ARENA + 4)) {
            for (int i = SEGMENTS - 3; i < SEGMENTS; i++) {
                if (p.distanceTo(parts[i]) < WHIP_RANGE) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 尾の薙ぎ払い: 尾が光って身構え、少しあとで尾のまわりを大きく払う。 */
    private void tickTailWhip(ServerLevel level, int ticks) {
        steer(arenaCenter().add(Math.cos(orbitAngle) * ORBIT, SWIM_HEIGHT, Math.sin(orbitAngle) * ORBIT), swimSpeed() * 0.5, 0.05);
        LeviathanSegment tail = parts[SEGMENTS - 1];
        if (ticks > WHIP_STRIKE) {
            level.sendParticles(ParticleTypes.END_ROD, tail.getX(), tail.getY() + 0.5, tail.getZ(), 2, 0.3, 0.3, 0.3, 0.02);
            return;
        }
        if (ticks == WHIP_STRIKE) {
            playSound(SinguloSounds.get("ergosphere_leviathan.whip"), 3.0F, 1.0F);
            for (Player p : challengers(level, ARENA + 4)) {
                for (int i = SEGMENTS - 3; i < SEGMENTS; i++) {
                    if (p.distanceTo(parts[i]) < WHIP_RANGE) {
                        if (hurtWith("tail_whip", p, damageSources().mobAttack(this), WHIP_DAMAGE)) {
                            Vec3 k = p.position().subtract(parts[i].position()).multiply(1, 0, 1);
                            k = k.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : k.normalize();
                            p.push(k.x * 1.6, 0.6, k.z * 1.6);
                            p.hurtMarked = true;
                        }
                        break;
                    }
                }
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, tail.getX(), tail.getY() + 0.5, tail.getZ(), 6, 2, 0.5, 2, 0);
        }
        if (ticks <= 1) {
            setState(SWIM, 0);
        }
    }

    // ---- 姿を変える

    private void startTransform(ServerLevel level) {
        stormMarks.clear();
        stormTimers.clear();
        if (form() == 1) {
            setState(MOLT, MOLT_TICKS);
            playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 4.0F, 0.6F);
            broadcastLine(level, "gui.singulo.leviathan.molt");
        } else {
            setState(SUBMERGE, SUBMERGE_TICKS);
            playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 4.0F, 0.5F);
            broadcastLine(level, "gui.singulo.leviathan.submerge");
        }
    }

    /** 脱皮: 部屋の真上へ昇って身を震わせ、鱗をすべて弾き飛ばす（当たると痛い）。光の骨格が現れる。 */
    private void tickMolt(ServerLevel level, int ticks) {
        Vec3 c = arenaCenter();
        if (ticks > MOLT_BURST) {
            steer(c.add(Math.cos(orbitAngle) * 5, SWIM_HEIGHT + 3, Math.sin(orbitAngle) * 5), 0.18, 0.12);
            orbitAngle += 0.06;
            if (ticks % 4 == 0) {
                LeviathanSegment p = parts[random.nextInt(SEGMENTS)];
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, plate()), p.getX(), p.getY() + 1, p.getZ(),
                        10, 0.6, 0.6, 0.6, 0.1);
            }
            return;
        }
        velocity = velocity.scale(0.8);
        moveBy(velocity);
        if (ticks == MOLT_BURST) {
            entityData.set(FORM, 2);
            BlockState plate = plate();
            for (LeviathanSegment p : parts) {
                for (int k = 0; k < 2; k++) {
                    GravityDebris d = new GravityDebris(level, this, plate);
                    d.setPos(p.getX(), p.getY() + p.getBbHeight() / 2, p.getZ());
                    double a = random.nextDouble() * Math.PI * 2;
                    d.shoot(Math.cos(a), 0.2 + random.nextDouble() * 0.6, Math.sin(a), 0.8F + random.nextFloat() * 0.5F, 4.0F);
                    level.addFreshEntity(d);
                }
                level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 12, 0.5, 0.5, 0.5, 0.3);
            }
            playSound(SinguloSounds.get("ergosphere_leviathan.molt"), 5.0F, 1.0F);
            for (Player p : challengers(level, 12)) {
                if (p.distanceTo(this) < 10) {
                    hurtWith("molt", p, damageSources().mobAttack(this), MOLT_DAMAGE);
                }
            }
        }
        if (ticks <= 1) {
            attackCooldown = 40;
            backToSwim();
        }
    }

    /** 再臨の前半: 炉心の渦へ頭から潜る（体も渦へ吸い込まれる）。 */
    private void tickSubmerge(ServerLevel level, int ticks) {
        Vec3 c = arenaCenter();
        Vec3 head = headCenter();
        Vec3 goal = c.add(0, -6, 0);
        Vec3 to = goal.subtract(head);
        double speed = Math.min(0.9, 0.3 + (SUBMERGE_TICKS - ticks) * 0.02);
        velocity = to.lengthSqr() < 1e-4 ? Vec3.ZERO : to.normalize().scale(Math.min(speed, to.length()));
        moveBy(velocity);
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 3 + random.nextDouble() * 6;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * r, c.y + random.nextDouble() * 2, c.z + Math.sin(a) * r,
                    0, -Math.cos(a), -0.1, -Math.sin(a), 0.5);
        }
        if (ticks <= 1) {
            setPos(c.x, c.y - 8, c.z);
            setState(ASCEND, ASCEND_TICKS);
            level.playSound(null, home, SinguloSounds.get("ergosphere_leviathan.rumble"), SoundSource.HOSTILE, 4.0F, 1.0F);
        }
    }

    /** 再臨の後半: 静けさのあと、黒い龍となって炉心から真上へ噴き上がる。 */
    private void tickAscend(ServerLevel level, int ticks) {
        Vec3 c = arenaCenter();
        int t = ASCEND_TICKS - ticks;
        if (t < ASCEND_BURST) {
            setPos(c.x, c.y - 8, c.z);
            if (t % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.2, c.z, 6, 4, 0.1, 4, 0.05);
            }
            return;
        }
        if (t == ASCEND_BURST) {
            entityData.set(FORM, 3);
            setBarName(Component.translatable(wyrmName()));
            bossBar.setColor(BossEvent.BossBarColor.RED);
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 3, 0.2, 0.2, 0.2, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
            playSound(SinguloSounds.get("ergosphere_leviathan.ascend"), 5.0F, 1.0F);
            broadcastLine(level, "gui.singulo.leviathan.ascend");
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(14), e -> e != this && !RuinGuards.isGuard(e))) {
                Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) {
                    e.push(push.normalize().x * 1.4, 0.7, push.normalize().z * 1.4);
                    e.hurtMarked = true;
                }
            }
        }
        double k = Math.min(1, (t - ASCEND_BURST) / 20.0);
        Vec3 at = c.add(0, -6 + (SWIM_HEIGHT + 8) * Math.sqrt(k), 0);
        Vec3 before = headCenter();
        setPos(at.x, at.y - getBbHeight() / 2, at.z);
        face(at.subtract(before).lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : at.subtract(before));
        if (ticks <= 1) {
            velocity = new Vec3(0.3, 0, 0);
            attackCooldown = 30;
            backToSwim();
        }
    }

    // ---- 骨格: とぐろ・噛みつき・光の欠片

    /** とぐろ: 部屋の中央で輪になって速く回る。まわりの相手は回る向きへ押し流され、内へ少し引かれる。節から光の欠片を撃つ。 */
    private void tickCoil(ServerLevel level, int ticks) {
        Vec3 c = arenaCenter();
        orbitAngle += 0.11;
        Vec3 goal = c.add(Math.cos(orbitAngle) * COIL_RADIUS, 4 + Math.sin(tickCount * 0.1), Math.sin(orbitAngle) * COIL_RADIUS);
        steer(goal, 0.75, 0.35);
        for (Player p : challengers(level, COIL_REACH + 2)) {
            Vec3 off = p.position().subtract(c).multiply(1, 0, 1);
            double d = off.length();
            if (d > COIL_REACH || d < 1e-3) {
                continue;
            }
            Vec3 tangent = new Vec3(-off.z, 0, off.x).normalize();
            p.setDeltaMovement(p.getDeltaMovement().add(tangent.scale(0.07)).add(off.normalize().scale(-0.025)));
            p.hurtMarked = true;
        }
        if (ticks % pace(10) == 0) {
            List<Player> players = challengers(level, ARENA + 4);
            if (!players.isEmpty()) {
                fireShard(level, parts[random.nextInt(SEGMENTS)], players.get(random.nextInt(players.size())));
            }
        }
        if (ticks <= 1) {
            backToSwim();
        }
    }

    /** 噛みつき: 顎を開いて身構え、相手へ飛びかかる。顎に捕まると傷つき、引き寄せられる。 */
    private void tickBite(ServerLevel level, @Nullable LivingEntity target, int ticks) {
        if (target == null) {
            backToSwim();
            return;
        }
        int t = BITE_TICKS - ticks;
        if (t < BITE_WINDUP) {
            velocity = velocity.scale(0.7);
            steer(target.getEyePosition(), Math.max(0.05, velocity.length()), 0.4);
            if (t == BITE_WINDUP - 1) {
                setAim(target.getBoundingBox().getCenter());
                velocity = aim().subtract(headCenter()).normalize().scale(0.95);
            }
            return;
        }
        moveBy(velocity);
        Vec3 mouth = mouth();
        for (Player p : challengers(level, ARENA + 4)) {
            if (p.getBoundingBox().inflate(1.2).contains(mouth) && hurtWith("bite", p, damageSources().mobAttack(this), BITE_DAMAGE)) {
                p.setDeltaMovement(velocity.scale(0.8));
                p.hurtMarked = true;
                playSound(SinguloSounds.get("ergosphere_leviathan.swallow"), 2.5F, 1.2F);
                ticks = 1;
            }
        }
        if (ticks <= 1) {
            backToSwim();
        }
    }

    /** 光の欠片の雨: 体のあちこちから、挑戦者へ光の欠片を撃つ。 */
    public int shedShards(ServerLevel level) {
        List<Player> targets = challengers(level, ARENA + 4);
        if (targets.isEmpty()) {
            return 0;
        }
        int n = 6 + (awakened() ? 2 : 0);
        for (int i = 0; i < n; i++) {
            fireShard(level, parts[random.nextInt(SEGMENTS)], targets.get(random.nextInt(targets.size())));
        }
        playSound(SinguloSounds.get("ergosphere_leviathan.shed"), 3.0F, 1.2F);
        return n;
    }

    private void fireShard(ServerLevel level, LeviathanSegment from, Player p) {
        HorizonBolt bolt = new HorizonBolt(level, this).style(0x9FEFFF, 0.6F, SHARD_DAMAGE);
        Vec3 at = from.position().add(0, from.getBbHeight() / 2, 0);
        bolt.setPos(at);
        Vec3 d = p.getEyePosition().subtract(at).normalize();
        bolt.shoot(d.x, d.y + 0.05, d.z, 0.9F, 3.0F);
        level.addFreshEntity(bolt);
    }

    // ---- 龍: 相対論的ジェット・降着の嵐

    /** 相対論的ジェット: 部屋の上空で止まり、頭の両角から2本の光線を伸ばして、ゆっくり回しながら薙ぐ。 */
    private void tickJet(ServerLevel level, @Nullable LivingEntity target, int ticks) {
        Vec3 c = arenaCenter();
        steer(c.add(0, SWIM_HEIGHT + 2, 0), 0.25, 0.2);
        velocity = velocity.scale(0.7);
        int t = JET_TICKS - ticks;
        if (t < JET_WINDUP) {
            if (target != null) {
                Vec3 to = target.position().subtract(headCenter());
                entityData.set(JET_YAW, (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG) - 60);
                // 光線が床に当たる輪が、相手の居る距離を通る角度
                double horizontal = Math.max(1, to.horizontalDistance());
                float pitch = (float) (Mth.atan2(-to.y + 1.0, horizontal) * Mth.RAD_TO_DEG);
                entityData.set(JET_PITCH, Mth.clamp(pitch, 12F, 75F));
            }
            return;
        }
        if (t == JET_WINDUP) {
            playSound(SinguloSounds.get("ergosphere_leviathan.jet"), 5.0F, 1.0F);
        }
        float speed = awakened() ? 3.4F : 2.6F;
        entityData.set(JET_YAW, jetYaw() + speed);
        for (Vec3 dir : jetDirections(jetYaw(), jetPitch())) {
            Vec3 end = fireRay(level, headCenter(), dir, JET_RANGE + 12, 1.4, BossScaling.hardMode() ? JET_DAMAGE : JET_DAMAGE_NORMAL, true);
            if (ticks % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 3, 0.2, 0.2, 0.2, 0.05);
            }
        }
        if (ticks <= 1) {
            backToSwim();
        }
    }

    /** 2本のジェットの向き（yaw の向きと、その反対。pitch だけ斜め下へ）。 */
    public static Vec3[] jetDirections(float yaw, float pitch) {
        Vec3 a = Vec3.directionFromRotation(pitch, yaw);
        Vec3 b = Vec3.directionFromRotation(pitch, yaw + 180);
        return new Vec3[] {a, b};
    }

    /** 降着の嵐: 高く昇って速く回りながら、挑戦者の足元へ小さなブラックホールを降らせる（光の柱が前触れ）。 */
    private void tickStorm(ServerLevel level, int ticks) {
        orbitAngle += 0.05;
        Vec3 c = arenaCenter();
        steer(c.add(Math.cos(orbitAngle) * (ORBIT + 2), SWIM_HEIGHT + 6, Math.sin(orbitAngle) * (ORBIT + 2)), swimSpeed() * 1.4, 0.12);
        if (ticks % pace(14) == 0) {
            for (Player p : challengers(level, ARENA + 4)) {
                stormMarks.add(p.position());
                stormTimers.add(18);
            }
            playSound(SinguloSounds.get("ergosphere_leviathan.storm"), 2.5F, 1.0F);
        }
        if (ticks <= 1) {
            backToSwim();
        }
    }

    private void tickStormMarks(ServerLevel level) {
        for (int i = stormMarks.size() - 1; i >= 0; i--) {
            Vec3 m = stormMarks.get(i);
            int left = stormTimers.get(i) - 1;
            if (left % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 0.5 + random.nextDouble() * 6, m.z, 2, 0.1, 0.6, 0.1, 0.0);
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3 + left * 0.2;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x + Math.cos(a) * WardenSingularity.RADIUS, m.y + 0.1,
                            m.z + Math.sin(a) * WardenSingularity.RADIUS, 1, 0, 0, 0, 0);
                }
            }
            if (left <= 0) {
                level.addFreshEntity(new WardenSingularity(level, this, m.add(0, 1, 0)));
                stormMarks.remove(i);
                stormTimers.remove(i);
            } else {
                stormTimers.set(i, left);
            }
        }
    }

    // ---- 共通

    /** 鱗（炉殻の板）を剥がして、挑戦者へ降らせる。 */
    public int shedScales(ServerLevel level) {
        List<Player> targets = challengers(level, ARENA + 4);
        if (targets.isEmpty()) {
            return 0;
        }
        BlockState plate = plate();
        int n = SHED_PLATES + (awakened() ? 2 : 0);
        for (int i = 0; i < n; i++) {
            LeviathanSegment from = parts[random.nextInt(SEGMENTS - 1)];
            Player p = targets.get(random.nextInt(targets.size()));
            GravityDebris debris = new GravityDebris(level, this, plate);
            debris.setPos(from.getX(), from.getY() + from.getBbHeight() / 2, from.getZ());
            Vec3 d = p.getEyePosition().subtract(debris.position()).normalize().add(0, 0.1, 0);
            debris.shoot(d.x, d.y, d.z, 0.9F, 6.0F);
            level.addFreshEntity(debris);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, plate), from.getX(), from.getY() + 1, from.getZ(),
                    10, 0.5, 0.5, 0.5, 0.1);
        }
        playSound(SinguloSounds.get("ergosphere_leviathan.shed"), 3.0F, 1.0F);
        return n;
    }

    /** 鱗の見た目（炉殻ブロック）。 */
    public static BlockState plate() {
        return BuiltInRegistries.BLOCK.get(Singulo.id("reactor_shell")).defaultBlockState();
    }

    private List<Player> challengers(ServerLevel level, double range) {
        return level.getEntitiesOfClass(Player.class, new AABB(arenaCenter(), arenaCenter()).inflate(range), RuinBoss::challenger);
    }

    /** エルゴ球の帯: 少し前に泳いだ跡の近くにいる相手を、泳いだ向きへ押し流す。 */
    private void pushWake(ServerLevel level) {
        if (tickCount % 2 != 0) {
            return;
        }
        double push = WAKE_PUSH * (form() == 3 ? 1.6 : enraged() ? 1.35 : 1);
        for (Player p : challengers(level, ARENA + 4)) {
            Vec3 at = p.position().add(0, 1, 0);
            TrailPoint best = null;
            double bestD = WAKE_REACH * WAKE_REACH;
            int n = 0;
            for (TrailPoint q : trail) {
                if (n++ % 3 != 0) {
                    continue;
                }
                int age = tickCount - q.tick();
                if (age < 6) {
                    continue;
                }
                if (age > WAKE_TICKS) {
                    break;
                }
                double d = q.pos().distanceToSqr(at);
                if (d < bestD) {
                    bestD = d;
                    best = q;
                }
            }
            if (best != null) {
                p.setDeltaMovement(p.getDeltaMovement().add(best.dir().scale(push)));
                p.hurtMarked = true;
            }
        }
    }

    /** 体に触れた相手を傷つける（突進中は強く、はね飛ばす）。 */
    private void contactDamage(ServerLevel level) {
        boolean dive = state() == DIVE;
        float damage = dive ? DIVE_DAMAGE : form() == 3 ? CONTACT_DAMAGE + 3 : CONTACT_DAMAGE;
        List<AABB> boxes = new ArrayList<>();
        boxes.add(getBoundingBox().inflate(0.2));
        for (LeviathanSegment p : parts) {
            boxes.add(p.getBoundingBox().inflate(0.1));
        }
        for (Player p : challengers(level, ARENA + 6)) {
            for (AABB box : boxes) {
                if (box.intersects(p.getBoundingBox())) {
                    if (hurtWith(dive ? "dive" : "body", p, damageSources().mobAttack(this), damage) && dive && velocity.lengthSqr() > 1e-4) {
                        Vec3 k = velocity.normalize();
                        p.push(k.x * 1.2, 0.5, k.z * 1.2);
                        p.hurtMarked = true;
                    }
                    break;
                }
            }
        }
    }

    /** 向きを少しずつ goal へ変えながら進む。部屋の外へ出そうなら中心へ戻る。 */
    private void steer(Vec3 goal, double speed, double turn) {
        Vec3 head = headCenter();
        Vec3 c = arenaCenter();
        if (head.subtract(c).horizontalDistance() > ARENA - 5 || head.y < c.y + 2) {
            goal = c.add(0, SWIM_HEIGHT, 0);
            turn = Math.max(turn, 0.15);
        }
        Vec3 to = goal.subtract(head);
        Vec3 cur = velocity.lengthSqr() < 1e-6 ? (to.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : to.normalize()) : velocity.normalize();
        Vec3 want = to.lengthSqr() < 1e-6 ? cur : to.normalize();
        Vec3 dir = cur.scale(1 - turn).add(want.scale(turn));
        dir = dir.lengthSqr() < 1e-6 ? cur : dir.normalize();
        velocity = dir.scale(speed);
        moveBy(velocity);
    }

    private void moveBy(Vec3 v) {
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        face(v);
    }

    /** 体の向きを dir に合わせる。 */
    private void face(Vec3 dir) {
        if (dir.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 d = dir.normalize();
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Math.asin(Mth.clamp(d.y, -1, 1)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        setXRot(pitch);
    }

    private void broadcastLine(ServerLevel level, String key) {
        Component msg = Component.translatable(key);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(arenaCenter(), arenaCenter()).inflate(ARENA + 16))) {
            p.displayClientMessage(msg, false);
        }
    }

    /** 龍の姿のボスバーの名前。 */
    private String wyrmName() {
        return "entity.singulo.ergosphere_leviathan.wyrm" + (awakened() ? ".awakened" : "");
    }

    // ------------------------------------------------------------------ 倒されたとき

    @Override
    public void die(DamageSource source) {
        super.die(source);
        setState(SWIM, 0);
        stormMarks.clear();
        stormTimers.clear();
        if (level() instanceof ServerLevel) {
            playSound(SinguloSounds.get("ergosphere_leviathan.death"), 4.0F, 1.0F);
        }
    }

    /**
     * 倒されたときの演出: しばらく身をよじり、体を尾から崩しながら、螺旋を描いて炉心の渦へ沈む。
     * 体は跡をなぞって渦へ吸い込まれ、最後に渦がつぶれて閃光と衝撃波を残す。
     */
    @Override
    protected void onDeathTick(ServerLevel level, int t) {
        Vec3 head = headCenter();
        if (t == 1) {
            deathStart = head;
        }
        if (home == null || deathStart == null) {
            return;
        }
        Vec3 core = arenaCenter();
        if (t <= DEATH_WRITHE) {
            Vec3 wob = new Vec3(Math.sin(t * 1.3) * 0.5, Math.cos(t * 0.9) * 0.3, Math.cos(t * 1.3) * 0.5);
            Vec3 at = deathStart.add(wob);
            setPos(at.x, at.y - getBbHeight() / 2, at.z);
            face(at.subtract(head));
        } else if (t <= DEATH_SINK) {
            double k = (double) (t - DEATH_WRITHE) / (DEATH_SINK - DEATH_WRITHE);
            Vec3 off = deathStart.subtract(core);
            double a0 = Math.atan2(off.z, off.x);
            double r0 = off.horizontalDistance();
            double a = a0 + k * Math.PI * 3;
            double r = r0 * Math.pow(1 - k, 1.2);
            double y = core.y - 3 + (off.y + 3) * Math.pow(1 - k, 1.5);
            Vec3 at = new Vec3(core.x + Math.cos(a) * r, y, core.z + Math.sin(a) * r);
            setPos(at.x, at.y - getBbHeight() / 2, at.z);
            face(at.subtract(head));
        }
        // 体が尾から順に崩れる
        int peel = SEGMENTS - 1 - (t - 4) / 7;
        if (t >= 4 && (t - 4) % 7 == 0 && peel >= 0 && peel < SEGMENTS) {
            LeviathanSegment p = parts[peel];
            if (form() == 1) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, plate()), p.getX(), p.getY() + 1, p.getZ(),
                        30, 0.8, 0.8, 0.8, 0.15);
            }
            level.sendParticles(form() == 3 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(),
                    10, 0.5, 0.5, 0.5, 0.05);
            level.playSound(null, p.blockPosition(), SinguloSounds.get("ergosphere_leviathan.shed"), SoundSource.HOSTILE, 1.5F, 1.1F);
        }
        if (t > DEATH_WRITHE && t % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 3 + random.nextDouble() * 6;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x + Math.cos(a) * r, core.y + random.nextDouble() * 2,
                        core.z + Math.sin(a) * r, 0, -Math.cos(a), -0.05, -Math.sin(a), 0.4);
            }
        }
        if (t == DEATH_TICKS - 14) {
            level.playSound(null, home, SinguloSounds.get("ergosphere_leviathan.implode"), SoundSource.HOSTILE, 4.0F, 1.0F);
        }
        if (t == DEATH_TICKS - 8) {
            level.sendParticles(ParticleTypes.FLASH, core.x, core.y + 1, core.z, 3, 0.1, 0.1, 0.1, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, core.x, core.y + 1, core.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y + 1, core.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, core.x, core.y + 1, core.z, 120, 0.2, 0.2, 0.2, 0.7);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(core, core).inflate(14),
                    e -> e != this && !RuinGuards.isGuard(e))) {
                Vec3 push = e.position().subtract(core).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) {
                    e.push(push.normalize().x * 1.3, 0.6, push.normalize().z * 1.3);
                    e.hurtMarked = true;
                }
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;                                  // 体から鳴り続ける音は、クライアントの BossSounds が鳴らす
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SinguloSounds.get("ergosphere_leviathan.hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("orbit", orbitAngle);
        tag.putInt("form", form());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        loading = true;
        try {
            super.readAdditionalSaveData(tag);
        } finally {
            loading = false;
        }
        orbitAngle = tag.getDouble("orbit");
        entityData.set(FORM, Mth.clamp(tag.getInt("form"), 1, 3));
        if (home != null) {
            entityData.set(HOME, home);
        }
        if (form() == 3 && !hasCustomName()) {
            setBarName(Component.translatable(wyrmName()));
            bossBar.setColor(BossEvent.BossBarColor.RED);
        }
        trail.clear();
    }
}
