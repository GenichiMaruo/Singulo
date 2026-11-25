package io.github.genichimaruo.singulo.android;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
 * 射撃型アンドロイド「シエル」。薄い青の髪に、深い青のひと房と淡い水緑の光。几帳面で、狙いを外さない。
 * まだクリエイティブでだけ出す（遺構にも研究所にも出ない）。
 *
 * <ul>
 * <li>小型銃: 右手の片手持ちの小型銃で、相手へ光弾を3発ずつ撃つ（壁の向こうへは撃たない）。</li>
 * <li>組み立て式レールガン: 背中の格納箱から部品が飛び出して手元で組み上がり、狙いの光線を見せながら力を溜めて、
 *     まっすぐ貫く光線を撃つ（並んだ相手をまとめて貫く。壁で止まる）。狙いは撃つ少し前に固まるので、横へ避けられる。
 *     撃ち終わると、部品は背中へ戻っていく。</li>
 * <li>近づかれたら、後ろへ跳んで間合いを取る。ふだんは相手から離れて（7〜12ブロック）戦う。</li>
 * </ul>
 */
public class GunnerAndroid extends RuinAndroid {
    /** 銃声の音量（届く距離は 16 × 音量 ブロック）: 小型銃は 64、レールガンは 96 ブロック先まで聞こえる。 */
    static final float PISTOL_SOUND_VOLUME = 4.0F, RAIL_SOUND_VOLUME = 6.0F;

    public static final int ACCENT = 0x6EF0D2;

    public static final int STATE_SHOT = 60;
    public static final int STATE_RAIL_ASSEMBLE = 61;
    public static final int STATE_RAIL_CHARGE = 62;
    public static final int STATE_RAIL_FIRE = 63;
    public static final int STATE_RAIL_STOW = 64;
    public static final int STATE_HOP = 65;

    /** 仕草: 3連射のあとのリロード（エネルギーカートリッジの交換）と、相手を倒したあとの小型銃の一回し。 */
    public static final int GESTURE_RELOAD = 20, GESTURE_TWIRL = 21;
    public static final int GESTURE_INSPECT = 22;
    private KatanaAndroid crossPartner;
    private int crossTargetId;
    /** 電磁加速で電力を注ぎ足している放電型（溜めが短くなり、光線が強く、電光の色になる）。 */
    private SparkAndroid booster;
    private static final EntityDataAccessor<Boolean> BOOSTED = SynchedEntityData.defineId(GunnerAndroid.class, EntityDataSerializers.BOOLEAN);

    /** 放電型の電磁加速を受けているか（クライアントでも見える）。 */
    public boolean boosted() {
        return entityData.get(BOOSTED);
    }

    /** 今のレールガンの溜めの長さ（電磁加速を受けていれば短い）。 */
    public int railCharge() {
        return boosted() ? RAIL_CHARGE - SparkAndroid.RAIL_BOOST_TICKS : RAIL_CHARGE;
    }

    boolean boostedBy(SparkAndroid spark) {
        return booster == spark && boosted();
    }

    /**
     * 放電型の電磁加速を受け入れる（レールガンを組み上げている間か、溜め始めて間もない間だけ）。
     * ルージュとの交差照準は発射の時を合わせているので、その間は受けない。
     */
    boolean acceptBoost(SparkAndroid spark) {
        int s = state();
        if (booster != null || crossPartner != null || !isAlive() || !active() || getTarget() == null
                || !(s == STATE_RAIL_ASSEMBLE || s == STATE_RAIL_CHARGE && stateTicks < RAIL_CHARGE - RAIL_LOCK - SparkAndroid.RAIL_BOOST_TICKS)) {
            return false;
        }
        booster = spark;
        entityData.set(BOOSTED, true);
        return true;
    }
    private int nextRail = 80;

    boolean idleForInspection() {
        return isAlive() && active() && !isNoAi() && state() == STATE_IDLE && getTarget() == null
                && !isWorking() && !napping() && !life.busy() && onGround() && !isPassenger()
                && getNavigation().isDone() && getDeltaMovement().horizontalDistanceSqr() < 0.0025;
    }

    boolean prepareCross(ServerLevel level, KatanaAndroid rouge, LivingEntity foe) {
        if (crossPartner != null || !isAlive() || !active() || isNoAi() || getTarget() != foe
                || !hasLineOfSight(foe) || blocked(level, muzzle(true), foe.getBoundingBox().getCenter())) return false;
        Vec3 rail = foe.position().subtract(position()).multiply(1, 0, 1).normalize();
        Vec3 dash = foe.position().subtract(rouge.position()).multiply(1, 0, 1).normalize();
        if (Math.abs(rail.cross(dash).y) < 0.55) return false;
        int s = state();
        if (s == STATE_IDLE) {
            double range = distanceTo(foe);
            if (rouge.stateTicks > 2 || nextRail > 0 || reloadTicks >= 0 || range < 5 || range >= RAIL_RANGE * 0.8
                    || isWorking() || !spend(COST_RAIL)) return false;
            aimRail(foe);
            setState(STATE_RAIL_ASSEMBLE);
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("android.railgun_assemble"), 0.8F, 1);
        } else if (s == STATE_RAIL_ASSEMBLE) {
            if (RAIL_ASSEMBLE - stateTicks + RAIL_CHARGE - RAIL_LOCK > KatanaAndroid.IAI_STANCE - rouge.stateTicks) return false;
        } else if (s != STATE_RAIL_CHARGE || RAIL_CHARGE - RAIL_LOCK - stateTicks > KatanaAndroid.IAI_STANCE - rouge.stateTicks) return false;
        crossPartner = rouge;
        crossTargetId = foe.getId();
        return true;
    }

    boolean covering(KatanaAndroid rouge) {
        return crossPartner == rouge && isAlive() && active() && rouge.isAlive() && level() == rouge.level()
                && distanceToSqr(rouge) <= 32 * 32 && (state() == STATE_RAIL_ASSEMBLE || state() == STATE_RAIL_CHARGE);
    }
    /**
     * リロード（エネルギーカートリッジの交換）の流れ: 空のカートリッジが握りの底から抜け落ち（RELOAD_EJECT から）、
     * 左手で新しいカートリッジを運んで、RELOAD_INSERT に押し込み、RELOAD_DONE に握りのエネルギーが満ちる（仕草の tick）。
     */
    public static final int RELOAD_EJECT = 2, RELOAD_INSERT = 13, RELOAD_DONE = 20;
    /** 倒したあと、この tick 以内に手が空けば（構えを解けば）、小型銃を一回す。 */
    static final int TWIRL_WINDOW = 60;

    /** 小型銃の3連射: 構えてから 6・11・16 tick 目以降、相手の方を向けていれば撃つ（向き終わるまでは待つ）。 */
    public static final int SHOT = 21;
    public static final int[] SHOT_TICKS = {6, 11, 16};
    /** 構えている間に体を相手へ回す速さ（度/tick）と、撃ってよい向きのずれ（度）。 */
    static final float TURN = 24, AIMED = 12;
    public static final int RAIL_ASSEMBLE = 16;
    public static final int RAIL_CHARGE = 26;
    /** 溜めの終わりのこの tick 数だけ、狙いを動かさない（避ける間）。 */
    public static final int RAIL_LOCK = 9;
    /** 溜めの間、3つのコイルが銃床側から順に灯る tick。 */
    public static final int[] RAIL_STAGES = {6, 12, 19};
    public static final int RAIL_FIRE = 12;
    public static final int RAIL_STOW = 12;
    public static final int HOP = 10;

    public static final double SHOT_RANGE = 18;
    public static final double RAIL_RANGE = 32;
    /** レールガンの光線の太さ（当たり判定の半径）。 */
    public static final double RAIL_RADIUS = 0.55;
    static final float SHOT_DAMAGE = 4F;
    static final float RAIL_DAMAGE = 20F;
    /** 光弾・光線の光が残る tick。 */
    public static final int FLASH = 4;

    static final int COST_BURST = 45;
    static final int COST_RAIL = 320;
    static final int COST_HOP = 60;

    /** 位置は、整数の基準点からの差で送る（遠くても小数の精度を落とさない）。 */
    private static final EntityDataAccessor<BlockPos> FX_ORIGIN = SynchedEntityData.defineId(GunnerAndroid.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> SHOT_FROM = SynchedEntityData.defineId(GunnerAndroid.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> SHOT_AT = SynchedEntityData.defineId(GunnerAndroid.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> SHOT_FLASH = SynchedEntityData.defineId(GunnerAndroid.class, EntityDataSerializers.INT);
    /** レールガンの銃口と、狙い（溜めの間）・光線の先（撃った後）。 */
    private static final EntityDataAccessor<Vector3f> RAIL_FROM = SynchedEntityData.defineId(GunnerAndroid.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> RAIL_TO = SynchedEntityData.defineId(GunnerAndroid.class, EntityDataSerializers.VECTOR3);
    /** 構えている間の狙いの点（相手の体の中心）。描画で、銃身をここへぴったり向ける。 */
    private static final EntityDataAccessor<Vector3f> AIM = SynchedEntityData.defineId(GunnerAndroid.class, EntityDataSerializers.VECTOR3);
    private int clientPistolState = STATE_IDLE;
    private float clientHolsterFrom;
    private float clientPistolAimFrom;

    public GunnerAndroid(EntityType<? extends GunnerAndroid> type, Level level) {
        super(type, level);
    }

    /** ほかの型より少し脆い。遠くの敵まで見つける（護衛の範囲が広い）。 */
    public static AttributeSupplier.Builder createAttributes() {
        return RuinAndroid.createAttributes()
                .add(Attributes.MAX_HEALTH, 28)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    /** 珍しい型の強み: 作業の速さ（作る・掘る・収穫・家畜の世話と、作業中に歩く速さの倍率）。 */
    public static final float WORK_PACE = 1.5F;
    private static final net.minecraft.resources.ResourceLocation WORK_SPEED_ID = io.github.genichimaruo.singulo.Singulo.id("gunner_work_speed");

    /** 珍しい型の強み: 遠距離の護衛（敵を探す範囲がほかの型の2倍）。 */
    @Override
    public double guardRange() {
        return GUARD_RANGE * 2;
    }

    /** 珍しい型の強み: 作業が速い（かかる時間が 1/1.5）。 */
    @Override
    public int workTicks(int ticks) {
        return Math.max(1, Math.round(ticks / WORK_PACE));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FX_ORIGIN, BlockPos.ZERO);
        builder.define(SHOT_FROM, new Vector3f());
        builder.define(SHOT_AT, new Vector3f());
        builder.define(SHOT_FLASH, 0);
        builder.define(RAIL_FROM, new Vector3f());
        builder.define(RAIL_TO, new Vector3f());
        builder.define(AIM, new Vector3f());
        builder.define(BOOSTED, false);
    }

    @Override
    public AndroidKind kind() {
        return AndroidKind.GUNNER;
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

    public static boolean combat(int s) {
        return s >= STATE_SHOT && s <= STATE_HOP;
    }

    /** レールガンを組み上げている・構えている間（背中の格納箱は空）。 */
    public static boolean railOut(int s) {
        return s >= STATE_RAIL_ASSEMBLE && s <= STATE_RAIL_STOW;
    }

    /** The sidearm stays on the hip while the hands are occupied. The rail's last pieces return at tick 7. */
    private static float holsterAt(int state, float t, float from) {
        boolean hip = railOut(state) || state == STATE_CRAFTING || state == STATE_MINING || lifeState(state)
                || state == STATE_LOW_POWER || charging(state) || state == STATE_SOLAR_FOLD;
        if (state == STATE_RAIL_STOW && t >= 7) {
            t -= 7;
            from = 1;
            hip = false;
        }
        float u = Mth.clamp(t / 5, 0, 1);
        return Mth.lerp(u * u * (3 - 2 * u), from, hip ? 1 : 0);
    }

    public float pistolHolster(float partialTick) {
        return holsterAt(state(), stateTime(partialTick), clientHolsterFrom);
    }

    private static float pistolAimAt(int state, float t, float from) {
        boolean aiming = state == STATE_SHOT;
        float u = Mth.clamp(t / (aiming ? 4 : 5), 0, 1);
        return Mth.lerp(u * u * (3 - 2 * u), from, aiming ? 1 : 0);
    }

    public float pistolAim(float partialTick) {
        return pistolAimAt(state(), stateTime(partialTick), clientPistolAimFrom);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (level().isClientSide && state() != clientPistolState) {
            float elapsed = super.stateTime(0);
            clientHolsterFrom = holsterAt(clientPistolState, elapsed, clientHolsterFrom);
            clientPistolAimFrom = pistolAimAt(clientPistolState, elapsed, clientPistolAimFrom);
            clientPistolState = state();
        }
        super.onSyncedDataUpdated(key);
    }

    private Vec3 fxOrigin() {
        return Vec3.atLowerCornerOf(entityData.get(FX_ORIGIN));
    }

    public Vec3 shotFrom() {
        return fxOrigin().add(new Vec3(entityData.get(SHOT_FROM)));
    }

    public Vec3 shotAt() {
        return fxOrigin().add(new Vec3(entityData.get(SHOT_AT)));
    }

    public int shotFlash() {
        return entityData.get(SHOT_FLASH);
    }

    public Vec3 railFrom() {
        return fxOrigin().add(new Vec3(entityData.get(RAIL_FROM)));
    }

    public Vec3 railTo() {
        return fxOrigin().add(new Vec3(entityData.get(RAIL_TO)));
    }

    /** 構えている間の狙いの点。 */
    public Vec3 aim() {
        return fxOrigin().add(new Vec3(entityData.get(AIM)));
    }

    /** 狙いの点を at にする（構えている間、毎 tick）。 */
    public void aimAt(Vec3 at) {
        rebase();
        entityData.set(AIM, at.subtract(fxOrigin()).toVector3f());
    }

    /** 銃口のおおよその位置（体の右前・胸の高さ。描画もこの位置から光を出す）。 */
    public Vec3 muzzle(boolean rail) {
        Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        return position().add(0, rail ? 1.02 : 1.08, 0).add(fwd.scale(rail ? 0.95 : 0.6)).add(right.scale(rail ? 0.12 : 0.22));
    }

    /** 基準点を今の位置へ動かす（ほかの光・狙いの位置も、同じ所を指すように直す）。 */
    private void rebase() {
        BlockPos o = blockPosition();
        if (!o.equals(entityData.get(FX_ORIGIN))) {
            Vec3 sf = shotFrom(), sa = shotAt(), rf = railFrom(), rt = railTo(), am = aim();
            entityData.set(FX_ORIGIN, o);
            Vec3 base = Vec3.atLowerCornerOf(o);
            entityData.set(SHOT_FROM, sf.subtract(base).toVector3f());
            entityData.set(SHOT_AT, sa.subtract(base).toVector3f());
            entityData.set(RAIL_FROM, rf.subtract(base).toVector3f());
            entityData.set(RAIL_TO, rt.subtract(base).toVector3f());
            entityData.set(AIM, am.subtract(base).toVector3f());
        }
    }

    private void setFx(Vec3 from, Vec3 to, EntityDataAccessor<Vector3f> a, EntityDataAccessor<Vector3f> b) {
        rebase();
        Vec3 base = fxOrigin();
        entityData.set(a, from.subtract(base).toVector3f());
        entityData.set(b, to.subtract(base).toVector3f());
    }

    private boolean blocked(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
    }

    /** 小型銃で相手を撃つ（壁があれば撃たない）。撃てたか。 */
    public boolean shoot(ServerLevel level, LivingEntity target) {
        if (!active() || !canHit(target)) {
            return false;
        }
        Vec3 from = muzzle(false);
        Vec3 to = target.getBoundingBox().getCenter();
        if (from.distanceTo(to) > SHOT_RANGE || blocked(level, from, to)) {
            return false;
        }
        // 3連射は 5 tick おきなので、受けたあとの無敵時間（10 tick）に2発目・3発目が消されないよう、光弾の前に外す
        // （当てたあとは、ふつうに無敵時間が付く）
        target.invulnerableTime = 0;
        target.hurt(damageSources().mobAttack(this), SHOT_DAMAGE);
        setFx(from, to, SHOT_FROM, SHOT_AT);
        entityData.set(SHOT_FLASH, FLASH);
        // 銃声は遠くまで届かせる（音の届く距離は 16 × 音量 ブロック。1 を超えると近くでは最大の大きさで、遠くへ行くほど小さくなる）
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("android.gunner_shot"), PISTOL_SOUND_VOLUME, 0.95F + random.nextFloat() * 0.1F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 4, 0.1, 0.1, 0.1, 0.1);
        return true;
    }

    /** レールガンの狙いを target へ向ける（溜めの間。撃つ少し前からは動かさない）。 */
    public void aimRail(LivingEntity target) {
        Vec3 from = muzzle(true);
        Vec3 dir = target.getBoundingBox().getCenter().subtract(from);
        if (dir.lengthSqr() < 1e-4) {
            return;
        }
        setFx(from, railEnd(from, dir.normalize()), RAIL_FROM, RAIL_TO);
    }

    private Vec3 railEnd(Vec3 from, Vec3 direction) {
        Vec3 end = from.add(direction.scale(RAIL_RANGE));
        HitResult wall = level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();
    }

    /**
     * レールガンを撃つ: 狙いの向きへまっすぐ、最初の壁まで（最大 RAIL_RANGE）。光線に触れた相手をまとめて貫く。当たった数を返す。
     * 撃った反動で、少し後ろへ下がる。
     */
    public int fireRail(ServerLevel level) {
        Vec3 from = muzzle(true);
        Vec3 dir = railTo().subtract(railFrom());
        if (dir.lengthSqr() < 1e-4) {
            dir = Vec3.directionFromRotation(getXRot(), yBodyRot);
        }
        dir = dir.normalize();
        Vec3 end = railEnd(from, dir);
        setFx(from, end, RAIL_FROM, RAIL_TO);
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(RAIL_RADIUS), this::canHit)) {
            AABB box = e.getBoundingBox().inflate(RAIL_RADIUS);
            if (!(box.contains(from) || box.clip(from, end).isPresent()) || !hit.add(e)) continue;
            if (crossPartner != null && e.getId() == crossTargetId) e.invulnerableTime = 0;
            if (e.hurt(damageSources().mobAttack(this), boosted() ? RAIL_DAMAGE * SparkAndroid.RAIL_BOOST : RAIL_DAMAGE)) {
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.8, 0.25, dir.z * 0.8));
                e.hurtMarked = true;
            }
        }
        // 光線にそって火花、着弾点で小さな爆ぜ（ブロックは壊さない）
        double len = from.distanceTo(end);
        for (double d = 1; d < len; d += 1.5) {
            Vec3 at = from.add(dir.scale(d));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.08, 0.08, 0.08, 0.05);
        }
        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        sparks(level, end, 12, 0.4);
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("android.railgun_fire"), RAIL_SOUND_VOLUME, 1.0F);   // レールガンの轟音は、さらに遠くまで
        setDeltaMovement(getDeltaMovement().add(-dir.x * 0.35, 0.05, -dir.z * 0.35));
        hasImpulse = true;
        return hit.size();
    }

    /**
     * 描画するかを決める箱: 光弾の跡・狙いの光線・レールガンの光線が出ている間は、その両端まで広げる
     * （本体が画面の外にいても、画面に入っている光線は描く）。当たり判定は変えない。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        if (shotFlash() > 0) {
            box = box.minmax(new AABB(shotFrom(), shotAt()));
        }
        int s = state();
        if (s == STATE_RAIL_CHARGE || s == STATE_RAIL_FIRE) {
            box = box.minmax(new AABB(railFrom(), railTo()));
        }
        return box;
    }

    /** リロードの仕草を始めてからの tick（-1 はリロードしていない）。カートリッジを押し込む音・満ちた音を鳴らす。 */
    private int reloadTicks = -1;
    /** カートリッジ1本で撃てる数と、今の残り（撃てた数だけ減り、リロードで満ちる。障害物で撃てなかった分は減らない）。 */
    static final int MAGAZINE = 3;
    private int ammo = MAGAZINE;

    /** リロードの仕草を始める（空のカートリッジを抜く）。終わると弾が満ちる（途中で構えたら、やり直し）。 */
    private void startReload() {
        gesture(GESTURE_RELOAD, null);
        reloadTicks = 0;
        playSound(SoundEvents.CROSSBOW_LOADING_START.value(), 0.5F, 1.8F);
    }

    /** 今の弾の残り。 */
    public int ammo() {
        return ammo;
    }
    /** 相手を倒してから、小型銃を一回すのを待つ残りの tick（0 は待っていない）。 */
    private int twirlWait;

    /** 相手を倒した: 手が空いたら小型銃を一回す。 */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity killed) {
        if (killed != this) {
            twirlWait = TWIRL_WINDOW;
        }
        return super.killedEntity(level, killed);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) {
            if (state() != STATE_RAIL_ASSEMBLE && state() != STATE_RAIL_CHARGE) crossPartner = null;
            if (booster != null && (!railOut(state()) || state() == STATE_RAIL_STOW || !booster.isAlive())) {
                booster = null;
                entityData.set(BOOSTED, false);
            }
            int motion = gesture();
            if (motion == GESTURE_INSPECT && !idleForInspection()) gesture(GESTURE_NONE, null);
            if (motion != GESTURE_NONE && (combat(state()) || (motion == GESTURE_RELOAD || motion == GESTURE_TWIRL)
                    && (state() != STATE_IDLE || isWorking() || napping()))) {
                gesture(GESTURE_NONE, null);
            }
            if (shotFlash() > 0) {
                entityData.set(SHOT_FLASH, shotFlash() - 1);
            }
            if (reloadTicks >= 0) {
                if (gesture() != GESTURE_RELOAD || state() != STATE_IDLE) {
                    reloadTicks = -1;               // 次の構えが始まった: 途中でやめる
                } else if (++reloadTicks == RELOAD_INSERT) {
                    playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.5F, 1.9F);                 // カチッと押し込む
                } else if (reloadTicks == RELOAD_DONE) {
                    playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.5F, 2.0F);       // エネルギーが満ちた
                    reloadTicks = -1;
                    ammo = MAGAZINE;
                }
            }
            LivingEntity foe = getTarget();
            boolean freeHands = state() == STATE_IDLE && active() && !isWorking() && !napping() && !life.busy()
                    && (foe == null || !foe.isAlive()) && gesture() == GESTURE_NONE;
            if (twirlWait > 0) {
                twirlWait--;
                if (freeHands) {
                    twirlWait = 0;
                    gesture(GESTURE_TWIRL, null);
                    playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6F, 1.8F);
                }
            } else if (freeHands && ammo < MAGAZINE && reloadTicks < 0) {
                startReload();
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
    }

    private final class CombatGoal extends Goal {
        private int nextBurst = 20;
        private int hopCooldown;
        /** 今の連射で撃った数と、次に撃てる tick（撃てたら5 tick あける。障害物で撃てなければ、向けたまま毎 tick 試す）。 */
        private int shots;
        private int nextShot;

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
            if (active() && combat(state())) {
                // 組み上げたレールガンは、しまってから戻る
                setState(railOut(state()) && state() != STATE_RAIL_STOW ? STATE_RAIL_STOW : STATE_IDLE);
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
            if (s == STATE_RAIL_STOW) {
                getNavigation().stop();
                if (stateTicks >= RAIL_STOW) {
                    setState(STATE_IDLE);
                }
                return;
            }
            // Finish moving parts, recoil and landing even if the shot killed the last target.
            boolean hasTarget = target != null && target.isAlive();
            if (!hasTarget && s != STATE_RAIL_ASSEMBLE && s != STATE_RAIL_FIRE && s != STATE_HOP) {
                if (s == STATE_RAIL_CHARGE) {
                    stowRail();
                } else if (s == STATE_SHOT && shotFlash() == 0) {
                    setState(STATE_IDLE);
                }
                return;
            }
            switch (s) {
                case STATE_SHOT -> {
                    boolean aimed = hold(target);
                    // 撃てたときだけ弾が減る（間に障害物があれば撃たずに、向けたまま次の tick も試す）
                    if (ammo > 0 && stateTicks >= nextShot && aimed && shoot(level, target)) {
                        ammo--;
                        shots++;
                        nextShot = stateTicks + SHOT_TICKS[1] - SHOT_TICKS[0];
                    }
                    // 撃ち切ったら少し構えを残して下ろし、リロードする。撃てないまま長引いたら、弾は残したまま下ろす
                    boolean empty = ammo == 0 && stateTicks >= nextShot;
                    if (empty || stateTicks >= SHOT + 20) {
                        setState(STATE_IDLE);
                        nextBurst = 26 + random.nextInt(16);
                        if (ammo == 0) {
                            startReload();
                        }
                    }
                }
                case STATE_RAIL_ASSEMBLE -> {
                    getNavigation().stop();
                    if (hasTarget) {
                        hold(target);
                        aimRail(target);
                    }
                    if (stateTicks % 4 == 0) {
                        sparks(level, muzzle(true), 2, 0.25);
                    }
                    if (stateTicks >= RAIL_ASSEMBLE) {
                        if (!hasTarget) {
                            stowRail();
                            return;
                        }
                        setState(STATE_RAIL_CHARGE);
                        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("android.railgun_charge"), 0.9F, 1.0F);
                    }
                }
                case STATE_RAIL_CHARGE -> {
                    if (stateTicks < railCharge() - RAIL_LOCK) {
                        hold(target);
                        aimRail(target);
                    } else {
                        getNavigation().stop();         // 狙いを固めた: もう相手を追わない（横へ避けられる）
                    }
                    // コイルが灯る音と、狙いが固まる音は、ためる音（android.railgun_charge）に入っている
                    if (stateTicks >= railCharge()) {
                        if (crossPartner != null) {
                            KatanaAndroid partner = crossPartner;
                            int phase = partner.state();
                            if (!covering(partner) || target.getId() != crossTargetId || partner.getTarget() != target || !partner.active()
                                    || stateTicks > RAIL_CHARGE + 90
                                    || phase < KatanaAndroid.STATE_IAI_STANCE || phase > KatanaAndroid.STATE_IAI_ZANSHIN) {
                                stowRail();
                                return;
                            }
                            if (phase != KatanaAndroid.STATE_IAI_ZANSHIN || partner.stateTicks < KatanaAndroid.IAI_SHEATHE_SEAT) return;
                            Vec3 from = muzzle(true);
                            AABB partnerBox = partner.getBoundingBox().inflate(RAIL_RADIUS);
                            if (partnerBox.contains(from) || partnerBox.clip(from, railTo()).isPresent()) {
                                stowRail();
                                return;
                            }
                        }
                        // ためている間に相手が物陰へ隠れた: 撃たずに諦めて、しまう
                        if (hasLineOfSight(target) && !blocked(level, muzzle(true), target.getBoundingBox().getCenter())) {
                            setState(STATE_RAIL_FIRE);
                            fireRail(level);
                            if (crossPartner != null) {
                                Vec3 a = crossPartner.pathFrom(), b = crossPartner.pathTo();
                                int steps = Mth.clamp((int) (a.distanceTo(b) * 2), 1, 32);
                                for (int i = 0; i <= steps; i++) {
                                    crossPartner.sparks(level, a.lerp(b, (double) i / steps).add(0, 0.9, 0), 1, 0.025);
                                }
                            }
                        } else {
                            stowRail();
                        }
                    }
                }
                case STATE_RAIL_FIRE -> {
                    getNavigation().stop();
                    if (stateTicks >= RAIL_FIRE) {
                        stowRail();
                    }
                }
                case STATE_HOP -> {
                    // 着地したら終わり（水の中や無重力では着地しないので、時間で終える）
                    if (stateTicks >= HOP && (onGround() || floating()) || stateTicks >= HOP * 4) {
                        setState(STATE_IDLE);
                        hopCooldown = 50;
                        nextBurst = Math.min(nextBurst, 6);
                    }
                }
                default -> approach(level, target, distanceTo(target));
            }
        }

        /** 足を止めて、体を相手の方へ少しずつ回す（一気には向かない）。相手の方を向けたか。 */
        private boolean hold(LivingEntity target) {
            getNavigation().stop();
            getLookControl().setLookAt(target, 60, 60);
            aimAt(target.getBoundingBox().getCenter());
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            float yaw = Mth.approachDegrees(yBodyRot, want, TURN);
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = Mth.approachDegrees(yHeadRot, want, TURN * 1.5F);
            return Math.abs(Mth.wrapDegrees(want - yaw)) < AIMED;
        }

        private void approach(ServerLevel level, LivingEntity target, double dist) {
            getLookControl().setLookAt(target, 30, 30);
            nextBurst--;
            nextRail--;
            hopCooldown--;
            if (rapidCooldown()) {   // 連撃モジュール: 待ち時間 ×0.75
                nextBurst--;
                nextRail--;
                hopCooldown--;
            }
            // 銃口から相手まで、間に障害物がないか（目では見えていても、柵などで撃てないなら撃たずに、撃てる場所へ動く）
            boolean seen = hasLineOfSight(target) && !blocked(level, muzzle(false), target.getBoundingBox().getCenter());
            if (dist < 3.2 && hopCooldown <= 0 && onGround() && !floating() && spend(COST_HOP)) {
                // 近すぎる: 相手から離れる向きへ跳ぶ
                Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, yBodyRot + 180) : away.normalize();
                setDeltaMovement(away.x * 0.75, 0.42, away.z * 0.75);
                hasImpulse = true;
                setState(STATE_HOP);
                playSound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.6F, 1.9F);
                return;
            }
            if (dist > 12 || !seen) {
                if (stateTicks % 8 == 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(target, 1.0);
                }
            } else if (dist < 6.5) {
                // 間合いを取る: 相手から離れる向きの少し先へ下がる
                if (stateTicks % 10 == 0 || getNavigation().isDone()) {
                    Vec3 away = position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(4);
                    getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.1);
                }
            } else {
                getNavigation().stop();
            }
            if (!seen) {
                return;
            }
            if (nextRail <= 0 && dist >= 5 && dist < RAIL_RANGE * 0.8 && spend(COST_RAIL)) {
                aimRail(target);
                start(STATE_RAIL_ASSEMBLE, io.github.genichimaruo.singulo.registry.SinguloSounds.get("android.railgun_assemble"), 1.0F);
                return;
            }
            if (ammo == 0) {
                if (reloadTicks < 0) {
                    startReload();                         // 弾がない（リロードが途中で止まった）: 先にリロードする
                }
                return;
            }
            if (nextBurst <= 0 && dist < SHOT_RANGE && reloadTicks < 0 && spend(COST_BURST)) {
                aimAt(target.getBoundingBox().getCenter());
                start(STATE_SHOT, SoundEvents.CROSSBOW_LOADING_END.value(), 1.6F);
            }
        }

        /** レールガンをしまい、次に組み上げるまで間をあける。 */
        private void stowRail() {
            crossPartner = null;
            setState(STATE_RAIL_STOW);
            playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8F, 1.5F);
            nextRail = 160 + random.nextInt(80);
            nextBurst = 30;
        }

        private void start(int s, net.minecraft.sounds.SoundEvent sound, float pitch) {
            if (active()) {
                shots = 0;
                nextShot = SHOT_TICKS[0];
                setState(s);
                playSound(sound, 0.8F, pitch);
            }
        }
    }
}
