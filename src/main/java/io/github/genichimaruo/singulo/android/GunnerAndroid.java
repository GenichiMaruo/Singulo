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
    static final float SHOT_DAMAGE = 3F;
    static final float RAIL_DAMAGE = 14;
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

    static boolean combat(int s) {
        return s >= STATE_SHOT && s <= STATE_HOP;
    }

    /** レールガンを組み上げている・構えている間（背中の格納箱は空）。 */
    public static boolean railOut(int s) {
        return s >= STATE_RAIL_ASSEMBLE && s <= STATE_RAIL_STOW;
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
        setFx(from, from.add(dir.normalize().scale(RAIL_RANGE)), RAIL_FROM, RAIL_TO);
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
        Vec3 end = from.add(dir.scale(RAIL_RANGE));
        HitResult wall = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        setFx(from, end, RAIL_FROM, RAIL_TO);
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(RAIL_RADIUS), this::canHit)) {
            AABB box = e.getBoundingBox().inflate(RAIL_RADIUS);
            if ((box.contains(from) || box.clip(from, end).isPresent()) && hit.add(e)
                    && e.hurt(damageSources().mobAttack(this), RAIL_DAMAGE)) {
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
            // 敵がいなくなって、弾が減っていれば、手が空いたときにリロードしておく
            LivingEntity foe = getTarget();
            if (ammo < MAGAZINE && reloadTicks < 0 && state() == STATE_IDLE && active() && (foe == null || !foe.isAlive())
                    && gesture() == GESTURE_NONE) {
                startReload();
            }
            if (twirlWait > 0) {
                twirlWait--;
                LivingEntity target = getTarget();
                if (state() == STATE_IDLE && active() && (target == null || !target.isAlive())) {
                    twirlWait = 0;
                    gesture(GESTURE_TWIRL, null);
                    playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6F, 1.8F);
                }
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
        private int nextRail = 80;
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
            return active() && t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || combat(state()) && active();
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
            if (target == null || !target.isAlive()) {
                if (railOut(s)) {
                    setState(STATE_RAIL_STOW);
                } else if (combat(s) && stateTicks > 20) {
                    setState(STATE_IDLE);
                }
                return;
            }
            double dist = distanceTo(target);
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
                    hold(target);
                    if (stateTicks % 4 == 0) {
                        sparks(level, muzzle(true), 2, 0.25);
                    }
                    if (stateTicks >= RAIL_ASSEMBLE) {
                        aimRail(target);
                        setState(STATE_RAIL_CHARGE);
                        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("android.railgun_charge"), 0.9F, 1.0F);
                    }
                }
                case STATE_RAIL_CHARGE -> {
                    if (stateTicks < RAIL_CHARGE - RAIL_LOCK) {
                        hold(target);
                        aimRail(target);
                    } else {
                        getNavigation().stop();         // 狙いを固めた: もう相手を追わない（横へ避けられる）
                    }
                    // コイルが灯る音と、狙いが固まる音は、ためる音（android.railgun_charge）に入っている
                    if (stateTicks >= RAIL_CHARGE) {
                        setState(STATE_RAIL_FIRE);
                        fireRail(level);
                    }
                }
                case STATE_RAIL_FIRE -> {
                    getNavigation().stop();
                    if (stateTicks >= RAIL_FIRE) {
                        setState(STATE_RAIL_STOW);
                        playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8F, 1.5F);
                        nextRail = 160 + random.nextInt(80);
                        nextBurst = 30;
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
                default -> approach(level, target, dist);
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
                start(STATE_SHOT, SoundEvents.CROSSBOW_LOADING_END.value(), 1.6F);
            }
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
