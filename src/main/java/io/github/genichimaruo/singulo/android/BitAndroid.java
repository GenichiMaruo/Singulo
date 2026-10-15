package io.github.genichimaruo.singulo.android;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * ビット型アンドロイド「ノクス」。紺の髪に菫と水色の差し色。物静かで観察好き。
 * 背中と腰に小さなビット（ファンネルのような子機）を6基しまっていて、戦いになると体から離れて追従して見え、レーザーと突進斬撃で戦う（別エンティティではない）。
 *
 * <ul>
 * <li>展開: 戦い始めに、ビットが4基（体力が半分を切ると6基）体から離れ、体の後ろに扇のように浮かぶ。戦いが終わると戻る。</li>
 * <li>ビットの射撃: 浮かんだビットが順に、相手へ細いレーザーを撃つ（壁の向こうへは撃たない）。</li>
 * <li>短剣: 近づかれたら光る短剣で2回斬り、そのまま後ろへ跳んで間合いを取る。</li>
 * <li>ふだんは相手から少し離れて（6〜10ブロック）戦う。</li>
 * </ul>
 */
public class BitAndroid extends RuinAndroid {
    public static final int ACCENT = 0xB080FF;
    /** ビットのレーザーの芯の色（水色）。 */
    public static final int BEAM = 0x7FE8FF;

    public static final int STATE_DEPLOY = 50;
    public static final int STATE_STAB = 51;
    public static final int STATE_BACKSTEP = 52;

    public static final int DEPLOY = 12;
    public static final int STAB = 12;
    public static final int BACKSTEP = 10;

    /** しまっているビットの数と、展開するビットの数（ふだん / 体力が半分を切ったら）。 */
    public static final int BITS = 6, BITS_OUT = 4, BITS_OUT_ENRAGED = 6;
    public static final int FIRE_INTERVAL = 16, FIRE_INTERVAL_ENRAGED = 11;
    public static final double FIRE_RANGE = 18;
    public static final float BIT_DAMAGE = 3;
    public static final float STAB_DAMAGE = 4;
    /** レーザーの光が残る tick。 */
    public static final int FLASH = 5;
    /** 予備動作4tick、往路8tick、復路12tick。光線の3回に1回を置き換える（追加発射ではない）。 */
    public static final int SLASH_WINDUP = 4, SLASH_OUT = 8, SLASH_RETURN = 12;
    public static final int SLASH_DURATION = SLASH_WINDUP + SLASH_OUT + SLASH_RETURN;
    public static final double SLASH_RANGE = 12;
    public static final float SLASH_DAMAGE = 4.5F;
    private static final int COST_SLASH = 36;

    private static final EntityDataAccessor<BlockPos> SLASH_ORIGIN = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> SHOT_ORIGIN = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> SLASH_BIT = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SLASH_START = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3f> SLASH_FROM = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> SLASH_AT = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> SHOT_FROM = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.VECTOR3);

    // 本体の一部。別エンティティ・当たり判定・独立した保存データは作らない。
    // ワールド座標を保持するので、本体の旋回や歩行に即座に貼り付かない。
    private final Vec3[] followers = new Vec3[BITS];
    private final Vec3[] oldFollowers = new Vec3[BITS];
    private float formationYaw;
    private boolean formationReady;
    private final Set<Integer> slashHits = new HashSet<>();

    static final int COST_DEPLOY = 120;
    static final int COST_SHOT = 18;
    static final int COST_STAB = 90;

    /** 浮かんでいるビットの数（0 ならしまっている）。 */
    private static final EntityDataAccessor<Integer> DEPLOYED = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.INT);
    /** 最後に撃ったビットの番号・光の残り・撃った先。 */
    private static final EntityDataAccessor<Integer> SHOT_BIT = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHOT_FLASH = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> SHOT_AT = SynchedEntityData.defineId(BitAndroid.class, EntityDataSerializers.VECTOR3);

    public BitAndroid(EntityType<? extends BitAndroid> type, Level level) {
        super(type, level);
    }

    /** ほかの型より少し脆く、足が速い。 */
    public static AttributeSupplier.Builder createAttributes() {
        return RuinAndroid.createAttributes()
                .add(Attributes.MAX_HEALTH, 26)
                .add(Attributes.ARMOR, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.32);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DEPLOYED, 0);
        builder.define(SLASH_ORIGIN, BlockPos.ZERO);
        builder.define(SHOT_ORIGIN, BlockPos.ZERO);
        builder.define(SLASH_BIT, -1);
        builder.define(SLASH_START, 0L);
        builder.define(SLASH_FROM, new Vector3f());
        builder.define(SLASH_AT, new Vector3f());
        builder.define(SHOT_FROM, new Vector3f());
        builder.define(SHOT_BIT, 0);
        builder.define(SHOT_FLASH, 0);
        builder.define(SHOT_AT, new Vector3f());
    }

    @Override
    public AndroidKind kind() {
        return AndroidKind.BIT;
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
        return s >= STATE_DEPLOY && s <= STATE_BACKSTEP;
    }

    /** 浮かんでいるビットの数。 */
    public int deployed() {
        return entityData.get(DEPLOYED);
    }

    public int shotBit() {
        return entityData.get(SHOT_BIT);
    }

    public int shotFlash() {
        return entityData.get(SHOT_FLASH);
    }

    public Vec3 shotAt() {
        Vector3f v = entityData.get(SHOT_AT);
        return Vec3.atLowerCornerOf(entityData.get(SHOT_ORIGIN)).add(new Vec3(v));
    }

    /**
     * ビット i（n 基のうち）の、体からの位置（ブロック。体の向き yaw と時間 age で決まる。サーバーとクライアントで同じ式）。
     * 体の後ろ上に、扇のように並んで、ゆっくり上下する。
     */
    public static Vec3 bitOffset(int i, int n, float yaw, float age) {
        double spread = n <= 1 ? 0 : (i - (n - 1) / 2.0) * (150.0 / Math.max(1, n - 1));
        double a = Math.toRadians(yaw + 180 + spread);
        double r = 1.15 + 0.15 * Math.sin(age * 0.07 + i);
        double y = 1.75 + 0.3 * Math.cos(Math.toRadians(spread) * 1.4) + 0.12 * Math.sin(age * 0.11 + i * 1.7);
        return new Vec3(-Math.sin(a) * r, y, Math.cos(a) * r);
    }

    /** ビットを浮かべる・しまう。 */
    public void setDeployed(int n) {
        if (deployed() != n) {
            entityData.set(DEPLOYED, n);
            playSound(n > 0 ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, 0.7F, 1.8F);
        }
    }

    public Vec3 shotFrom() {
        return Vec3.atLowerCornerOf(entityData.get(SHOT_ORIGIN)).add(new Vec3(entityData.get(SHOT_FROM)));
    }

    public int slashBit() {
        return entityData.get(SLASH_BIT);
    }

    private Vec3 slashFrom() {
        return Vec3.atLowerCornerOf(entityData.get(SLASH_ORIGIN)).add(new Vec3(entityData.get(SLASH_FROM)));
    }

    public Vec3 slashAim() {
        return Vec3.atLowerCornerOf(entityData.get(SLASH_ORIGIN)).add(new Vec3(entityData.get(SLASH_AT)));
    }

    public float slashTime(float partialTick) {
        return level().getGameTime() - entityData.get(SLASH_START) + partialTick;
    }

    public boolean bladeOut(int i, float partialTick) {
        return i == slashBit() && slashTime(partialTick) < SLASH_WINDUP + SLASH_OUT;
    }

    /** 展開位置へ追いつくまで少し遅れる。瞬間移動時は長い残像を残さない。 */
    private void tickFormation() {
        if (!formationReady) {
            formationYaw = yBodyRot;
            formationReady = true;
        }
        formationYaw = Mth.rotLerp(0.14F, formationYaw, yBodyRot);
        for (int i = 0; i < BITS; i++) {
            Vec3 goal = position().add(bitOffset(i, Math.max(BITS_OUT, deployed()), formationYaw, tickCount));
            if (followers[i] == null || followers[i].distanceToSqr(position()) > 64) {
                followers[i] = goal;
            }
            oldFollowers[i] = followers[i];
            followers[i] = followers[i].lerp(goal, 0.24);
        }
    }

    /** 本体の相対座標に戻すのは描画の直前だけ。突進は同期した発射位置と狙いへ向かう。 */
    public Vec3 bitPosition(int i, float partialTick) {
        Vec3 follow = followers[i] == null ? position().add(bitOffset(i, Math.max(BITS_OUT, deployed()), yBodyRot, tickCount))
                : oldFollowers[i].lerp(followers[i], partialTick);
        if (i != slashBit()) {
            return follow;
        }
        Vec3 from = slashFrom();
        Vec3 to = slashAim();
        float t = slashTime(partialTick);
        if (t < SLASH_WINDUP) {
            return from;
        }
        if (t < SLASH_WINDUP + SLASH_OUT) {
            return from.lerp(to, Mth.clamp((t - SLASH_WINDUP) / SLASH_OUT, 0, 1));
        }
        float back = Mth.clamp((t - SLASH_WINDUP - SLASH_OUT) / SLASH_RETURN, 0, 1);
        return to.lerp(follow, back * back * (3 - 2 * back));
    }

    /** 描画上の先端方向。突進中だけ狙った場所を向き、帰りは編隊へ戻る。 */
    public Vec3 bitDirection(int i, float partialTick) {
        if (i == slashBit()) {
            Vec3 from = slashFrom();
            Vec3 to = slashAim();
            if (slashTime(partialTick) < SLASH_WINDUP + SLASH_OUT) {
                return to.subtract(from).normalize();
            }
            return (followers[i] == null ? position() : followers[i]).subtract(bitPosition(i, partialTick)).normalize();
        }
        return Vec3.directionFromRotation(-25, formationYaw);
    }

    /** 狙いは発射時に固定。移動で避けられ、壁の向こうへ突き抜けない。 */
    public boolean slash(ServerLevel level, int i, LivingEntity target) {
        if (!active() || !canHit(target) || i < 0 || i >= deployed() || slashBit() >= 0) {
            return false;
        }
        Vec3 from = bitPosition(i, 1);
        Vec3 to = target.getBoundingBox().getCenter();
        if (from.distanceTo(to) > SLASH_RANGE || blocked(level, from, to)) {
            return false;
        }
        // 大きいワールド座標をfloatに詰めると小数が失われる。整数の基準点と近距離の差分を同期する。
        entityData.set(SLASH_ORIGIN, blockPosition());
        Vec3 origin = Vec3.atLowerCornerOf(blockPosition());
        entityData.set(SLASH_FROM, from.subtract(origin).toVector3f());
        entityData.set(SLASH_AT, to.subtract(origin).toVector3f());
        entityData.set(SLASH_START, level.getGameTime());
        entityData.set(SLASH_BIT, i);
        slashHits.clear();
        playSound(SoundEvents.BEACON_POWER_SELECT, 0.55F, 1.6F);
        return true;
    }

    private boolean blocked(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
    }

    private void tickSlash(ServerLevel level) {
        if (slashBit() < 0) {
            return;
        }
        float t = slashTime(0);
        if (!active() || !isAlive() || deployed() <= slashBit() || t >= SLASH_DURATION) {
            entityData.set(SLASH_BIT, -1);
            slashHits.clear();
            return;
        }
        if (t <= SLASH_WINDUP || t > SLASH_WINDUP + SLASH_OUT) {
            return;
        }
        Vec3 from = slashFrom();
        Vec3 aim = slashAim();
        Vec3 a = from.lerp(aim, (t - SLASH_WINDUP - 1) / SLASH_OUT);
        Vec3 b = from.lerp(aim, (t - SLASH_WINDUP) / SLASH_OUT);
        // 毎tick全経路を確認。突進中に置かれた壁も通り抜けない。
        HitResult wall = level.clip(new ClipContext(from, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            entityData.set(SLASH_AT, wall.getLocation().subtract(Vec3.atLowerCornerOf(entityData.get(SLASH_ORIGIN))).toVector3f());
            entityData.set(SLASH_START, level.getGameTime() - SLASH_WINDUP - SLASH_OUT);
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(0.35), this::canHit)) {
            AABB box = e.getBoundingBox().inflate(0.25);
            if (!slashHits.contains(e.getId()) && (box.contains(a) || box.clip(a, b).isPresent())
                    && !blocked(level, a, e.getBoundingBox().getCenter())) {
                slashHits.add(e.getId());
                if (e.hurt(damageSources().mobAttack(this), SLASH_DAMAGE)) {
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.8F);
                    sparks(level, e.getBoundingBox().getCenter(), 5, 0.15);
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        tickFormation();
        if (level() instanceof ServerLevel level) {
            if (shotFlash() > 0) {
                entityData.set(SHOT_FLASH, shotFlash() - 1);
            }
            tickSlash(level);
        }
    }

    /** ビット i から相手へレーザーを撃つ（壁があれば撃たない）。撃てたか。 */
    public boolean fire(ServerLevel level, int i, LivingEntity target) {
        if (!active() || i < 0 || i >= deployed() || i == slashBit() || !canHit(target)) {
            return false;
        }
        Vec3 from = bitPosition(i, 1);
        Vec3 to = target.getBoundingBox().getCenter();
        if (from.distanceTo(to) > FIRE_RANGE) {
            return false;
        }
        HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            return false;
        }
        if (canHit(target)) {
            target.hurt(damageSources().mobAttack(this), BIT_DAMAGE);
        }
        entityData.set(SHOT_ORIGIN, blockPosition());
        Vec3 origin = Vec3.atLowerCornerOf(blockPosition());
        entityData.set(SHOT_FROM, from.subtract(origin).toVector3f());
        entityData.set(SHOT_BIT, i);
        entityData.set(SHOT_AT, to.subtract(origin).toVector3f());
        entityData.set(SHOT_FLASH, FLASH);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.9F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 4, 0.1, 0.1, 0.1, 0.1);
        return true;
    }

    private final class CombatGoal extends Goal {
        private int nextMove = 20;
        private int fireCooldown = FIRE_INTERVAL;
        private int shots;
        private int idle;

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
            return canUse() || (combat(state()) || deployed() > 0) && active();
        }

        @Override
        public void stop() {
            if (active() && combat(state())) {
                setState(STATE_IDLE);
            }
            setDeployed(0);
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
            if (target == null || !target.isAlive()) {
                // 相手がいなくなったら、少し待ってビットを戻す
                if (++idle > 40) {
                    setDeployed(0);
                    if (combat(s)) {
                        setState(STATE_IDLE);
                    }
                }
                return;
            }
            idle = 0;
            double dist = distanceTo(target);
            int want = getHealth() <= getMaxHealth() / 2 ? BITS_OUT_ENRAGED : BITS_OUT;
            if (deployed() > 0 && deployed() != want) {
                setDeployed(want);
            }
            // 浮かんだビットの射撃（ほかの動きの最中も撃つ）
            if (deployed() > 0 && --fireCooldown <= 0) {
                fireCooldown = deployed() >= BITS_OUT_ENRAGED ? FIRE_INTERVAL_ENRAGED : FIRE_INTERVAL;
                if (hasLineOfSight(target)) {
                    int i = shots++ % deployed();
                    if (shots % 3 == 0 && slashBit() < 0 && bitPosition(i, 1).distanceTo(target.getBoundingBox().getCenter()) <= SLASH_RANGE) {
                        if (spend(COST_SLASH) && slash(level, i, target)) {
                            // 命中後の通常の無敵時間が明けてから次の光線を撃つ。
                            fireCooldown = Math.max(fireCooldown + 8, SLASH_WINDUP + SLASH_OUT + 10);
                        }
                    } else if (i != slashBit() && spend(COST_SHOT)) {
                        fire(level, i, target);
                    }
                }
            }
            switch (s) {
                case STATE_DEPLOY -> {
                    hold(target);
                    if (stateTicks == DEPLOY / 2) {
                        setDeployed(want);
                        sparks(level, position().add(0, 1.4, 0), 12, 0.6);
                    }
                    if (stateTicks >= DEPLOY) {
                        finish(10);
                    }
                }
                case STATE_STAB -> {
                    hold(target);
                    if (stateTicks == 3 || stateTicks == 8) {
                        sweep(2.6, 100, STAB_DAMAGE, 0.25);
                        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.8F);
                    }
                    if (stateTicks >= STAB) {
                        // 斬ったら、後ろへ跳んで間合いを取る
                        Vec3 back = position().subtract(target.position()).multiply(1, 0, 1);
                        back = back.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, yBodyRot + 180) : back.normalize();
                        setDeltaMovement(back.x * 0.9, 0.38, back.z * 0.9);
                        hasImpulse = true;
                        setState(STATE_BACKSTEP);
                        playSound(SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.6F, 1.9F);
                    }
                }
                case STATE_BACKSTEP -> {
                    getLookControl().setLookAt(target, 60, 60);
                    if (stateTicks >= BACKSTEP && onGround()) {
                        finish(25);
                    }
                }
                default -> approach(target, dist);
            }
        }

        private void finish(int pause) {
            setState(STATE_IDLE);
            nextMove = pause;
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

        /** 6〜10ブロックの間合いを保つ。近づかれたら短剣、ビットがしまってあれば展開する。 */
        private void approach(LivingEntity target, double dist) {
            getLookControl().setLookAt(target, 30, 30);
            nextMove--;
            boolean seen = hasLineOfSight(target);
            if (dist > 10 || !seen) {
                if (stateTicks % 8 == 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(target, 1.0);
                }
            } else if (dist < 5 && deployed() > 0) {
                Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
                if (away.lengthSqr() > 1e-4 && (stateTicks % 10 == 0 || getNavigation().isDone())) {
                    Vec3 to = position().add(away.normalize().scale(4));
                    getNavigation().moveTo(to.x, to.y, to.z, 1.05);
                }
            } else {
                getNavigation().stop();
            }
            if (deployed() == 0 && seen && dist < FIRE_RANGE && spend(COST_DEPLOY)) {
                start(STATE_DEPLOY, SoundEvents.BEACON_ACTIVATE, 1.7F);
                return;
            }
            if (nextMove <= 0 && dist < 2.8 && seen && spend(COST_STAB)) {
                start(STATE_STAB, SoundEvents.PLAYER_ATTACK_WEAK, 1.5F);
            }
        }

        private void start(int s, net.minecraft.sounds.SoundEvent sound, float pitch) {
            if (active()) {
                setState(s);
                playSound(sound, 0.9F, pitch);
            }
        }
    }
}
