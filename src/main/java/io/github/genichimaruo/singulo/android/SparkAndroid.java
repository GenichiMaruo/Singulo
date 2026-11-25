package io.github.genichimaruo.singulo.android;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 放電型アンドロイド「エクレール」。濃い鋼灰色の髪に電光の黄色のインナーカラーと、レモン寄りの白黄の光。
 * 覚醒したエルゴ球の大蛇がときどき残す設計メモリから印刷できる隠し型。
 *
 * <p>両腕にコイルを巻いた放電籠手を着け、背中の腰に蓄電・発電の機械を背負う。機械と籠手はケーブルでつながっている。
 * 一体への火力はシエル・ルージュより低いが、中距離で敵から敵へ連鎖する放電で、まとめて相手にする。
 *
 * <ul>
 * <li>アーク: 指先から放電し、近くの敵へ最大 {@link #ARC_CHAIN} 体まで連鎖する（つなぐたびに弱まる。壁の向こうへは跳ばない）。</li>
 * <li>掌底放電: 近づいた敵に掌を当てて、強めの一撃で押し返す。</li>
 * <li>電場: 足元に電場を張る。中の敵に少しずつ当たり、動きを鈍らせる（張ったあとも戦い続けられる）。</li>
 * <li>大技「天雷」: 両腕を掲げて頭上に電荷を溜め、範囲の敵すべてへ枝分かれする放電を一斉に落とす。</li>
 * <li>給電: 近くにいる同じ持ち主のアンドロイドへ、自分の電力を少しずつ分ける（分けた分だけ自分が減る）。</li>
 * <li>連携: シエルのレールガンに電力を注ぎ足して溜めを縮め（電磁加速）、ルージュの紅一閃の刀に電荷を与える。</li>
 * </ul>
 *
 * 放電はブロックを壊さず、火を付けず、バニラの雷も使わない（クリーパーの帯電や豚の変化は起きない）。
 */
public class SparkAndroid extends RuinAndroid {
    public static final int ACCENT = 0xF4F25A;

    public static final int STATE_ARC = 100;
    public static final int STATE_PALM = 101;
    public static final int STATE_FIELD = 102;
    public static final int STATE_TENRAI_CHARGE = 103;
    public static final int STATE_TENRAI_STRIKE = 104;
    /** 待機中に、指先で小さな放電をもてあそぶ。 */
    public static final int STATE_PLAY = 105;

    /** アーク: 構えてから ARC_FIRE tick 目に放つ。 */
    public static final int ARC = 12, ARC_FIRE = 4;
    /** 掌底放電: PALM_HIT tick 目に当てる。 */
    public static final int PALM = 12, PALM_HIT = 4;
    /** 電場を張る仕草の長さと、張った電場が残る tick・当たる間隔。 */
    public static final int FIELD = 14, FIELD_PLANT = 7, FIELD_LIFE = 80, FIELD_PULSE = 10;
    /** 天雷の溜めと、落としてからの長さ（STRIKE_HIT tick 目に当たる）。 */
    public static final int TENRAI_CHARGE = 36, TENRAI_STRIKE = 18, STRIKE_HIT = 2;
    public static final int PLAY = 80;
    /** 放電の光が残る tick。 */
    public static final int FLASH = 5;

    public static final double ARC_RANGE = 8;
    /** 連鎖で次の相手へ跳べる距離と、つなげる数（最初の相手を含む）。 */
    public static final double ARC_HOP = 4.5;
    public static final int ARC_CHAIN = 3;
    public static final double PALM_REACH = 2.4;
    public static final double FIELD_RADIUS = 4;
    public static final double TENRAI_RADIUS = 10;
    public static final int TENRAI_TARGETS = 8;
    /** 天雷の電荷を溜める、頭上の高さ。 */
    public static final double TENRAI_HEIGHT = 4.2;

    static final float ARC_DAMAGE = 5, ARC_FALLOFF = 0.7F;
    static final float PALM_DAMAGE = 7;
    static final float FIELD_DAMAGE = 2;
    static final float TENRAI_DAMAGE = 14;
    /** 紅一閃の遅延斬撃に乗せる電荷（ダメージの上乗せ）と、電磁加速のレールガンのダメージ倍率・縮める溜め。 */
    public static final float BLADE_BONUS = 4, RAIL_BOOST = 1.25F;
    public static final int RAIL_BOOST_TICKS = 8;

    static final int COST_ARC = 50;
    static final int COST_PALM = 60;
    static final int COST_FIELD = 200;
    static final int COST_TENRAI = 400;
    static final int COST_LINK = 80;

    /** 給電: SUPPLY_INTERVAL tick ごとに、満タンの SUPPLY_SHARE を、相手が SUPPLY_BELOW を下回っていれば分ける。自分は SUPPLY_KEEP を残す。 */
    static final int SUPPLY_INTERVAL = 40;
    static final float SUPPLY_SHARE = 0.02F, SUPPLY_BELOW = 0.6F, SUPPLY_KEEP = 0.5F;
    public static final double SUPPLY_RANGE = 8;
    /** 連携（給電・電磁加速・刀への電荷）でつなぐ相手までの距離。 */
    public static final double LINK_RANGE = 10;

    /** つないでいる相手の種類（描画で、放電の線をどこへ引くか）。 */
    public static final int LINK_NONE = 0, LINK_SUPPLY = 1, LINK_RAIL = 2, LINK_BLADE = 3;

    /** 作業の速さ（珍しい型の強み）。 */
    public static final float WORK_PACE = 1.5F;
    private static final net.minecraft.resources.Identifier WORK_SPEED_ID = io.github.genichimaruo.singulo.Singulo.id("spark_work_speed");

    /** 位置は、整数の基準点からの差で送る。 */
    private static final EntityDataAccessor<BlockPos> FX_ORIGIN = SynchedEntityData.defineId(SparkAndroid.class, EntityDataSerializers.BLOCK_POS);
    /** アークの道筋（指先 → 1体目 → 2体目 …）と、天雷の落ちた先。 */
    private static final EntityDataAccessor<String> ARC_PATH = SynchedEntityData.defineId(SparkAndroid.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ARC_FLASH = SynchedEntityData.defineId(SparkAndroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> BOLTS = SynchedEntityData.defineId(SparkAndroid.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<org.joml.Vector3fc> FIELD_AT = SynchedEntityData.defineId(SparkAndroid.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> FIELD_LEFT = SynchedEntityData.defineId(SparkAndroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LINK_TARGET = SynchedEntityData.defineId(SparkAndroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LINK_KIND = SynchedEntityData.defineId(SparkAndroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LINK_FLASH = SynchedEntityData.defineId(SparkAndroid.class, EntityDataSerializers.INT);

    private int nextPlay = 20 * (10 + random.nextInt(12));
    private int nextSupply = SUPPLY_INTERVAL;
    /** 連携している相手（電磁加速のシエル、電荷を与えたルージュ）。 */
    private RuinAndroid linked;

    public SparkAndroid(EntityType<? extends SparkAndroid> type, Level level) {
        super(type, level);
    }

    /** 射撃型と刀型の間の頑丈さ。 */
    public static AttributeSupplier.Builder createAttributes() {
        return RuinAndroid.createAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.ARMOR, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FX_ORIGIN, BlockPos.ZERO);
        builder.define(ARC_PATH, "");
        builder.define(ARC_FLASH, 0);
        builder.define(BOLTS, "");
        builder.define(FIELD_AT, new Vector3f());
        builder.define(FIELD_LEFT, 0);
        builder.define(LINK_TARGET, -1);
        builder.define(LINK_KIND, LINK_NONE);
        builder.define(LINK_FLASH, 0);
    }

    @Override
    public AndroidKind kind() {
        return AndroidKind.SPARK;
    }

    /** 珍しい型の強み: バックパックを内蔵している（腰の機械が兼ねる）。 */
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
    public int workTicks(int ticks) {
        return Math.max(1, Math.round(ticks / WORK_PACE));
    }

    @Override
    public int accent() {
        return ACCENT;
    }

    @Override
    protected void registerCombat() {
        goalSelector.addGoal(2, new CombatGoal());
    }

    public static boolean combat(int s) {
        return s >= STATE_ARC && s <= STATE_TENRAI_STRIKE;
    }

    /** アーク・天雷・電場の光が届く範囲。 */
    @Override
    protected double moveReach() {
        double r = fieldLeft() > 0 ? FIELD_RADIUS + 1 : 0;
        int s = state();
        if (s == STATE_TENRAI_CHARGE || s == STATE_TENRAI_STRIKE) r = Math.max(r, TENRAI_RADIUS + 1);
        if (arcFlash() > 0 || s == STATE_ARC) r = Math.max(r, ARC_RANGE + ARC_HOP * (ARC_CHAIN - 1) + 1);
        if (linkKind() != LINK_NONE) r = Math.max(r, LINK_RANGE + 1);
        return r;
    }

    // ------------------------------------------------------------------ 光の位置（クライアントへ送る）

    private Vec3 fxOrigin() {
        return Vec3.atLowerCornerOf(entityData.get(FX_ORIGIN));
    }

    /** 基準点を今の位置へ動かす（ほかの光の位置も、同じ所を指すように直す）。 */
    private void rebase() {
        BlockPos o = blockPosition();
        if (o.equals(entityData.get(FX_ORIGIN))) return;
        List<Vec3> arc = arcPath(), bolts = boltTargets();
        Vec3 field = fieldCenter();
        entityData.set(FX_ORIGIN, o);
        entityData.set(ARC_PATH, pack(arc));
        entityData.set(BOLTS, pack(bolts));
        entityData.set(FIELD_AT, field.subtract(fxOrigin()).toVector3f());
    }

    private String pack(List<Vec3> points) {
        Vec3 origin = fxOrigin();
        var out = new StringBuilder();
        for (Vec3 point : points) {
            Vec3 p = point.subtract(origin);
            if (!out.isEmpty()) out.append(';');
            out.append((float)p.x).append(',').append((float)p.y).append(',').append((float)p.z);
        }
        return out.toString();
    }

    private List<Vec3> unpack(String encoded) {
        if (encoded.isEmpty()) return List.of();
        List<Vec3> out = new java.util.ArrayList<>();
        Vec3 origin = fxOrigin();
        for (String point : encoded.split(";")) {
            String[] xyz = point.split(",");
            out.add(origin.add(Float.parseFloat(xyz[0]), Float.parseFloat(xyz[1]), Float.parseFloat(xyz[2])));
        }
        return out;
    }

    public List<Vec3> arcPath() {
        return unpack(entityData.get(ARC_PATH));
    }

    public int arcFlash() {
        return entityData.get(ARC_FLASH);
    }

    /** 天雷が落ちた相手の体の中心。 */
    public List<Vec3> boltTargets() {
        return unpack(entityData.get(BOLTS));
    }

    public Vec3 fieldCenter() {
        return fxOrigin().add(new Vec3(entityData.get(FIELD_AT)));
    }

    /** 張った電場の残りの tick（0 は張っていない）。 */
    public int fieldLeft() {
        return entityData.get(FIELD_LEFT);
    }

    @javax.annotation.Nullable
    public Entity linkTarget() {
        int id = entityData.get(LINK_TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    public int linkKind() {
        return entityData.get(LINK_KIND);
    }

    public int linkFlash() {
        return entityData.get(LINK_FLASH);
    }

    private void setLink(@javax.annotation.Nullable Entity target, int kind, int flash) {
        entityData.set(LINK_TARGET, target == null ? -1 : target.getId());
        entityData.set(LINK_KIND, target == null ? LINK_NONE : kind);
        entityData.set(LINK_FLASH, target == null ? 0 : flash);
    }

    /** 天雷の溜めで体に浮き出る電光の筋の強さ（0〜1）。溜めで満ち、落としたあと引いていく。 */
    public float surge(float partialTick) {
        float t = stateTime(partialTick);
        return switch (state()) {
            case STATE_TENRAI_CHARGE -> Mth.clamp(t / TENRAI_CHARGE, 0, 1);
            case STATE_TENRAI_STRIKE -> 1 - Mth.clamp(t / TENRAI_STRIKE, 0, 1);
            default -> 0;
        };
    }

    /** 籠手のコイルと腰の機械の光の強さ（待機中は弱く、戦闘中と連携中は強い）。 */
    public float coilGlow(float partialTick) {
        float base = combat(state()) || linkKind() != LINK_NONE ? 1 : state() == STATE_PLAY ? 0.7F : 0.35F;
        return Math.max(base, surge(partialTick));
    }

    /** サーバーで放電経路と遮蔽を判定するための右手の位置。 */
    public Vec3 fingertip() {
        Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        return position().add(0, 1.05, 0).add(fwd.scale(0.55)).add(right.scale(0.2));
    }

    /** 腰の機械のおおよその位置（給電の放電はここから出る）。 */
    public Vec3 unit() {
        Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(0, 0.78, 0).subtract(fwd.scale(0.25));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        if (arcFlash() > 0) {
            for (Vec3 p : arcPath()) box = box.minmax(new AABB(p, p).inflate(0.5));
        }
        if (state() == STATE_TENRAI_CHARGE || state() == STATE_TENRAI_STRIKE) {
            if (state() == STATE_TENRAI_STRIKE) {
                for (Vec3 p : boltTargets()) box = box.minmax(new AABB(p, p).inflate(0.5));
            }
            box = box.minmax(new AABB(position(), position().add(0, TENRAI_HEIGHT + 1, 0)));
        }
        if (fieldLeft() > 0) {
            Vec3 center = fieldCenter();
            box = box.minmax(new AABB(center, center).inflate(FIELD_RADIUS + 0.5, 0.6, FIELD_RADIUS + 0.5));
        }
        Entity link = linkTarget();
        if (link != null && linkKind() != LINK_NONE) box = box.minmax(link.getBoundingBox());
        return box;
    }

    // ------------------------------------------------------------------ 技

    private boolean blocked(Level level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
    }

    private void sound(String name, float volume, float pitch) {
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("android." + name), volume, pitch);
    }

    /**
     * アーク: 指先から target へ放電し、そこから近くの相手へ跳び移る（最大 ARC_CHAIN 体。同じ相手には二度跳ばない。
     * 壁の向こうへは跳ばない）。当たった数を返す。壁で最初の相手に届かなければ放たない（0）。
     */
    public int arc(ServerLevel level, LivingEntity target) {
        if (!active() || !canHit(target)) return 0;
        Vec3 from = fingertip();
        Vec3 at = target.getBoundingBox().getCenter();
        if (from.distanceTo(at) > ARC_RANGE + 1 || blocked(level, from, at)) return 0;
        List<Vec3> path = new ArrayList<>();
        path.add(from);
        Set<LivingEntity> hit = new HashSet<>();
        LivingEntity current = target;
        float damage = ARC_DAMAGE;
        while (current != null && hit.size() < ARC_CHAIN) {
            hit.add(current);
            Vec3 c = current.getBoundingBox().getCenter();
            path.add(c);
            current.invulnerableTime = 0;
            current.hurt(damageSources().mobAttack(this), damage);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 8, 0.2, 0.3, 0.2, 0.25);
            damage *= ARC_FALLOFF;
            LivingEntity next = null;
            double best = ARC_HOP * ARC_HOP;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, current.getBoundingBox().inflate(ARC_HOP), this::canHit)) {
                if (hit.contains(e)) continue;
                double d = e.distanceToSqr(current);
                Vec3 ec = e.getBoundingBox().getCenter();
                if (d < best && !blocked(level, c, ec)) {
                    best = d;
                    next = e;
                }
            }
            current = next;
        }
        rebase();
        entityData.set(ARC_PATH, pack(path));
        entityData.set(ARC_FLASH, FLASH);
        sound("spark_arc", 1.6F, 0.95F + random.nextFloat() * 0.1F);
        return hit.size();
    }

    /** 掌底放電: 前の PALM_REACH 以内の相手に当てて押し返す。当たった数を返す。 */
    public int palm(ServerLevel level) {
        Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
        int hits = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(PALM_REACH), this::canHit)) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            if (to.lengthSqr() > PALM_REACH * PALM_REACH + 1 || to.normalize().dot(fwd) < 0.35 || !hasLineOfSight(e)) continue;
            e.invulnerableTime = 0;
            if (e.hurtServer(level, damageSources().mobAttack(this), PALM_DAMAGE)) {
                hits++;
                Vec3 push = to.lengthSqr() < 1e-4 ? fwd : to.normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.1, 0.35, push.z * 1.1));
                e.hurtMarked = true;
            }
            Vec3 c = e.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 14, 0.25, 0.3, 0.25, 0.4);
        }
        // 掌の閃光は描画中の電極に合わせる。命中位置の火花だけをサーバーから送る。
        sound("spark_palm", 1.2F, 1.0F);
        return hits;
    }

    /** 足元に電場を張る（それまでの電場は張り直す）。 */
    public void plantField() {
        rebase();
        entityData.set(FIELD_AT, position().subtract(fxOrigin()).toVector3f());
        entityData.set(FIELD_LEFT, FIELD_LIFE);
        sound("spark_field", 1.0F, 1.0F);
    }

    /** 電場の中の相手に当てる（FIELD_PULSE tick ごと）。当たった数を返す。 */
    int pulseField(ServerLevel level) {
        Vec3 c = fieldCenter();
        int hits = 0;
        AABB box = new AABB(c, c).inflate(FIELD_RADIUS, 2, FIELD_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            if (e.position().multiply(1, 0, 1).distanceToSqr(c.multiply(1, 0, 1)) > FIELD_RADIUS * FIELD_RADIUS) continue;
            e.invulnerableTime = 0;
            if (e.hurtServer(level, damageSources().mobAttack(this), FIELD_DAMAGE)) hits++;
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), this);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 0.2, e.getZ(), 5, 0.3, 0.1, 0.3, 0.2);
        }
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = FIELD_RADIUS * Math.sqrt(random.nextDouble());
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.1);
        }
        return hits;
    }

    /** 天雷: 範囲の相手（見えている相手だけ、近い順に最大 TENRAI_TARGETS 体）へ一斉に落とす。当たった数を返す。 */
    public int strike(ServerLevel level) {
        Vec3 sky = position().add(0, TENRAI_HEIGHT, 0);
        List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(TENRAI_RADIUS, 4, TENRAI_RADIUS),
                e -> canHit(e) && e.distanceToSqr(this) <= TENRAI_RADIUS * TENRAI_RADIUS && hasLineOfSight(e));
        foes.sort(java.util.Comparator.comparingDouble(this::distanceToSqr));
        List<Vec3> ends = new ArrayList<>();
        int hits = 0;
        for (LivingEntity e : foes.subList(0, Math.min(TENRAI_TARGETS, foes.size()))) {
            Vec3 c = e.getBoundingBox().getCenter();
            ends.add(c);
            e.invulnerableTime = 0;
            if (e.hurtServer(level, damageSources().mobAttack(this), TENRAI_DAMAGE)) hits++;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.3, 0.5, 0.3, 0.5);
            level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        rebase();
        entityData.set(BOLTS, pack(ends));
        level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), sky.x, sky.y, sky.z, 1, 0, 0, 0, 0);
        sound("spark_tenrai_strike", 5.0F, 1.0F);   // 雷鳴は遠くまで届かせる
        return hits;
    }

    // ------------------------------------------------------------------ 給電と連携

    /** 同じ持ち主の護衛のアンドロイド（自分を除く）。 */
    private List<RuinAndroid> allies(ServerLevel level, double range) {
        if (!isGuard() || owner() == null) return List.of();
        return level.getEntitiesOfClass(RuinAndroid.class, getBoundingBox().inflate(range),
                a -> a != this && a.isAlive() && a.isGuard() && owner().equals(a.owner()) && a.printed() && distanceToSqr(a) <= range * range);
    }

    /**
     * 給電: いちばん電力の少ない仲間へ、満タンの SUPPLY_SHARE を分ける（自分は SUPPLY_KEEP を残す。分けた分だけ自分が減り、
     * 相手の満タンを超える分は分けない）。分けた量を返す。
     */
    public int supply(ServerLevel level) {
        if (!active() || chargeFraction() <= SUPPLY_KEEP) return 0;
        RuinAndroid needy = null;
        for (RuinAndroid a : allies(level, SUPPLY_RANGE)) {
            if (a.chargeFraction() < SUPPLY_BELOW && hasLineOfSight(a) && (needy == null || a.chargeFraction() < needy.chargeFraction())) {
                needy = a;
            }
        }
        if (needy == null) return 0;
        int spare = charge() - (int) Math.ceil(maxCharge() * SUPPLY_KEEP);
        int amount = Math.min(Math.min((int) (maxCharge() * SUPPLY_SHARE), spare), needy.maxCharge() - needy.charge());
        if (amount <= 0) return 0;
        setCharge(charge() - amount);
        needy.setCharge(needy.charge() + amount);
        if (linkKind() == LINK_NONE || linkKind() == LINK_SUPPLY) setLink(needy, LINK_SUPPLY, 8);
        sparks(level, needy.getBoundingBox().getCenter(), 4, 0.25);
        sound("spark_supply", 0.4F, 1.3F);
        return amount;
    }

    /** 近くでレールガンを溜めているシエル（電磁加速）か、紅一閃を構えたルージュ（刀への電荷）に力を貸す。 */
    private void assist(ServerLevel level) {
        if (linked != null) {
            boolean keep = linked.isAlive() && distanceToSqr(linked) <= LINK_RANGE * LINK_RANGE
                    && (linked instanceof GunnerAndroid gun && gun.boostedBy(this) || linked instanceof KatanaAndroid k && k.sparkChargedBy(this));
            if (!keep) {
                linked = null;
                setLink(null, LINK_NONE, 0);
            } else {
                setLink(linked, linked instanceof GunnerAndroid ? LINK_RAIL : LINK_BLADE, 2);
            }
            return;
        }
        if (!active() || combat(state()) && state() != STATE_ARC || isWorking()) return;
        for (RuinAndroid a : allies(level, LINK_RANGE)) {
            if (!hasLineOfSight(a) || charge() < COST_LINK) continue;
            boolean joined = a instanceof GunnerAndroid gun && gun.acceptBoost(this)
                    || a instanceof KatanaAndroid k && k.acceptSparkCharge(this);
            if (joined && spend(COST_LINK)) {
                linked = a;
                setLink(a, a instanceof GunnerAndroid ? LINK_RAIL : LINK_BLADE, 2);
                sound("spark_link", 1.0F, 1.0F);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 毎 tick

    boolean idleForPlay() {
        return isAlive() && active() && !isNoAi() && (state() == STATE_IDLE || state() == STATE_PLAY) && (getTarget() == null || !getTarget().isAlive())
                && !isWorking() && !napping() && !life.busy() && gesture() == GESTURE_NONE && onGround() && !isPassenger()
                && !isInWater() && getNavigation().isDone() && position().distanceToSqr(xo, yo, zo) < 0.0001;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (arcFlash() > 0) entityData.set(ARC_FLASH, arcFlash() - 1);
        int left = fieldLeft();
        if (left > 0) {
            entityData.set(FIELD_LEFT, left - 1);
            if (left % FIELD_PULSE == 0 && active()) pulseField(level);
        }
        if (linkFlash() > 0) {
            entityData.set(LINK_FLASH, linkFlash() - 1);
            if (linkFlash() == 0 && linked == null) setLink(null, LINK_NONE, 0);
        }
        assist(level);
        if (--nextSupply <= 0) {
            nextSupply = SUPPLY_INTERVAL;
            supply(level);
        }
        // 待機中の放電遊び
        int s = state();
        if (s == STATE_PLAY) {
            if (!idleForPlay()) {
                setState(STATE_IDLE);
            } else {
                if (stateTicks % 20 == 6) sound("spark_play", 0.35F, 1.1F + random.nextFloat() * 0.3F);
                if (stateTicks >= PLAY) setState(STATE_IDLE);
            }
        } else if (s == STATE_IDLE && idleForPlay()) {
            if (tickCount >= nextPlay) {
                nextPlay = tickCount + PLAY + 20 * (30 + random.nextInt(30));
                setState(STATE_PLAY);
            }
        } else {
            nextPlay = Math.max(nextPlay, tickCount + 40);
        }
        // 作業中だけ、歩く速さを上げる
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        boolean working = isWorking();
        if (speed != null && working != speed.hasModifier(WORK_SPEED_ID)) {
            if (working) {
                speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(WORK_SPEED_ID, WORK_PACE - 1,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else {
                speed.removeModifier(WORK_SPEED_ID);
            }
        }
    }

    private final class CombatGoal extends Goal {
        private int nextArc = 10;
        private int nextPalm;
        private int nextField = 60;
        private int nextTenrai = 120;
        private boolean arcFired;

        CombatGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return active() && (combat(state()) || t != null && t.isAlive());
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            if (active() && combat(state())) setState(STATE_IDLE);
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel level) || !active()) return;
            LivingEntity target = getTarget();
            boolean hasTarget = target != null && target.isAlive();
            int s = state();
            switch (s) {
                case STATE_ARC -> {
                    getNavigation().stop();
                    if (hasTarget) face(target);
                    if (!arcFired && stateTicks >= ARC_FIRE) {
                        arcFired = true;
                        if (hasTarget) arc(level, target);
                    }
                    if (stateTicks >= ARC) {
                        setState(STATE_IDLE);
                        nextArc = 14 + random.nextInt(10);
                    }
                }
                case STATE_PALM -> {
                    getNavigation().stop();
                    if (hasTarget) face(target);
                    if (stateTicks == PALM_HIT) palm(level);
                    if (stateTicks >= PALM) {
                        setState(STATE_IDLE);
                        nextPalm = 40;
                        nextArc = Math.min(nextArc, 6);
                    }
                }
                case STATE_FIELD -> {
                    getNavigation().stop();
                    if (stateTicks == FIELD_PLANT) plantField();
                    if (stateTicks >= FIELD) {
                        setState(STATE_IDLE);
                        nextField = FIELD_LIFE + 60 + random.nextInt(40);
                    }
                }
                case STATE_TENRAI_CHARGE -> {
                    getNavigation().stop();
                    if (hasTarget) face(target);
                    if (stateTicks % 6 == 0) sparks(level, position().add(0, orbHeight(stateTicks), 0), 3, 0.35);
                    if (stateTicks >= TENRAI_CHARGE) {
                        entityData.set(BOLTS, "");
                        setState(STATE_TENRAI_STRIKE);
                    }
                }
                case STATE_TENRAI_STRIKE -> {
                    getNavigation().stop();
                    if (stateTicks == STRIKE_HIT) strike(level);
                    if (stateTicks >= TENRAI_STRIKE) {
                        setState(STATE_IDLE);
                        nextTenrai = 220 + random.nextInt(80);
                        nextArc = 10;
                    }
                }
                default -> {
                    if (hasTarget) approach(level, target);
                }
            }
        }

        private void face(LivingEntity target) {
            getLookControl().setLookAt(target, 60, 60);
            double dx = target.getX() - getX(), dz = target.getZ() - getZ();
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            float yaw = Mth.approachDegrees(yBodyRot, want, 30);
            setYRot(yaw);
            yBodyRot = yaw;
        }

        private void approach(ServerLevel level, LivingEntity target) {
            getLookControl().setLookAt(target, 30, 30);
            nextArc--;
            nextPalm--;
            nextField--;
            nextTenrai--;
            if (rapidCooldown()) {   // 連撃モジュール: 待ち時間 ×0.75
                nextArc--;
                nextPalm--;
                nextField--;
                nextTenrai--;
            }
            double dist = distanceTo(target);
            boolean seen = hasLineOfSight(target) && !blocked(level, fingertip(), target.getBoundingBox().getCenter());
            // 中距離（4〜7ブロック）を保つ
            if (dist > 7 || !seen) {
                if (stateTicks % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
            } else if (dist < 3.5 && nextPalm > 0) {
                if (stateTicks % 10 == 0 || getNavigation().isDone()) {
                    Vec3 away = position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(4);
                    getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.1);
                }
            } else {
                getNavigation().stop();
            }
            if (!seen) return;
            if (dist <= PALM_REACH && nextPalm <= 0 && spend(COST_PALM)) {
                start(STATE_PALM);
                return;
            }
            int around = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(TENRAI_RADIUS, 4, TENRAI_RADIUS),
                    e -> canHit(e) && e.distanceToSqr(SparkAndroid.this) <= TENRAI_RADIUS * TENRAI_RADIUS).size();
            if (nextTenrai <= 0 && dist <= TENRAI_RADIUS && (around >= 2 || target.getHealth() > TENRAI_DAMAGE) && spend(COST_TENRAI)) {
                start(STATE_TENRAI_CHARGE);
                sound("spark_tenrai_charge", 1.4F, 1.0F);
                return;
            }
            if (nextField <= 0 && fieldLeft() == 0 && dist <= FIELD_RADIUS + 2 && spend(COST_FIELD)) {
                start(STATE_FIELD);
                return;
            }
            if (nextArc <= 0 && dist <= ARC_RANGE && spend(COST_ARC)) {
                start(STATE_ARC);
            }
        }

        private void start(int state) {
            arcFired = false;
            getNavigation().stop();
            setState(state);
        }
    }

    /** 天雷の電荷と、その周りに出す火花の高さ。 */
    public static double orbHeight(float ticks) {
        return Mth.lerp(Mth.clamp(ticks / 20F, 0, 1), 2.1, TENRAI_HEIGHT);
    }

    /** テスト用: 技の待ち時間をなくす。 */
    public void readyAllMovesForTest() {
        for (var g : goalSelector.getAvailableGoals()) {
            if (g.getGoal() instanceof CombatGoal c) {
                c.nextArc = 0;
                c.nextPalm = 0;
                c.nextField = 0;
                c.nextTenrai = 0;
            }
        }
    }
}
