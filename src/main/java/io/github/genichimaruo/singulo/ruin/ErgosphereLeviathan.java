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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
 * エルゴ球の鯨（沈んだ炉心区画のボス、段階5）。止まったペンローズ炉の渦に棲みついた、炉殻の板を鱗のように連ねた長い体の鯨。
 * <ul>
 *   <li>頭と {@value #SEGMENTS} 節の体（{@link LeviathanSegment}）。体は頭の通った跡をなぞってついてくる。
 *       頭は普通に、胴は硬く（×{@value #BODY_FACTOR}）、尾の光る芯は弱い（×{@value #TAIL_FACTOR}）</li>
 *   <li>部屋の上空を、輪を描いて泳ぎ続ける。体に触れると傷つく</li>
 *   <li>泳いだ跡にはエルゴ球の帯が残り、その中では泳ぐ向きへ押し流される（流れに乗ると追いつきやすい）</li>
 *   <li>突進: 相手の上へ昇って身構え（頭が光る）、狙った場所を一直線に突き抜ける</li>
 *   <li>渦の吐息: 止まって口の渦を開き、前にいる相手を吸い寄せてから、渦の光線を吐く</li>
 *   <li>体力半分から: 速くなり、突進が続けて来ることがあり、鱗（炉殻の板）を剥がして降らせる</li>
 *   <li>出現: 床の炉心に渦が開き、頭が螺旋を描いて這い出し、体が一節ずつ引き出される</li>
 *   <li>倒されると: 身をよじり、鱗が尾から剥がれ落ち、螺旋を描いて炉心の渦へ沈む。最後に渦がつぶれて閃光を残す</li>
 * </ul>
 */
public class ErgosphereLeviathan extends RuinBoss {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE_TICKS = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> AIM = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<BlockPos> HOME = SynchedEntityData.defineId(ErgosphereLeviathan.class, EntityDataSerializers.BLOCK_POS);

    public static final int SWIM = 0, DIVE_WINDUP = 1, DIVE = 2, BREATH_CHARGE = 3, BREATH_FIRE = 4;

    /** 体の節の数（最後の1つが尾）。 */
    public static final int SEGMENTS = 12;
    /** 節と節の間（ブロック）。頭から最初の節までは HEAD_GAP。 */
    public static final float SPACING = 1.7F;
    public static final float HEAD_GAP = 1.9F;
    public static final float BODY_FACTOR = 0.6F;
    public static final float TAIL_FACTOR = 1.6F;
    /** 体力の既定値（設定 leviathanHealth）。 */
    public static final double HEALTH = 1600;
    public static final int EMERGE_TICKS = 140;
    /** 出現の演出で、渦が開いてから頭が這い出すまで。 */
    public static final int EMERGE_VORTEX = 40;
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
    static final int ATTACK_INTERVAL = 80;
    public static final float CONTACT_DAMAGE = 5, DIVE_DAMAGE = 14, BREATH_DAMAGE = 7;
    public static final double BREATH_RANGE = 26, BREATH_WIDTH = 1.6, BREATH_PULL_RANGE = 18;
    /** エルゴ球の帯が残る tick と、押し流す強さ・届く距離。 */
    public static final int WAKE_TICKS = 60;
    static final double WAKE_PUSH = 0.09, WAKE_REACH = 2.5;
    public static final int SHED_PLATES = 5;

    private final LeviathanSegment[] parts = new LeviathanSegment[SEGMENTS];
    /** 頭（の中心）の通った跡。新しいものが先頭。 */
    private final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();
    private Vec3 velocity = Vec3.ZERO;
    private double orbitAngle;
    private int attackCooldown = ATTACK_INTERVAL;
    private boolean diveNext = true;
    private boolean chained;
    @Nullable
    private Vec3 deathStart;

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
        this.xpReward = 250;
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
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.LEVIATHAN_HEALTH.get() : HEALTH;
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

    /** 体力が半分（覚醒した個体は 65%）を切ったか。 */
    public boolean enraged() {
        return getHealth() <= getMaxHealth() * enrageFraction();
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
        // 動きは自分で決める（泳ぐ・突進・出現・倒されたときの沈み方）
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

    /** 体のどこかに当たったとき（部位から呼ばれる）。 */
    public boolean hurtPart(LeviathanSegment part, DamageSource source, float amount) {
        return hurt(source, amount * part.damageFactor);
    }

    // ------------------------------------------------------------------ 出現

    @Override
    protected void onEmergeTick(ServerLevel level, int t) {
        if (home == null) {
            return;
        }
        Vec3 core = Vec3.atBottomCenterOf(home).add(0, 1.0, 0);
        if (t <= EMERGE_VORTEX) {
            // 炉心に渦が開く（頭はまだ炉心の中）
            setPos(core.x, core.y - 2.5, core.z);
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 2 + random.nextDouble() * 5;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x + Math.cos(a) * r, core.y + random.nextDouble(),
                        core.z + Math.sin(a) * r, 0, -Math.cos(a), -0.1, -Math.sin(a), 0.3);
            }
            if (t == 1) {
                level.playSound(null, home, SinguloSounds.get("ergosphere_leviathan.emerge"), SoundSource.HOSTILE, 3.0F, 0.8F);
            }
            return;
        }
        // 頭が螺旋を描いて這い出し、泳ぐ高さまで昇る（体は跡をなぞって炉心から引き出される）
        double k = (double) (t - EMERGE_VORTEX) / (EMERGE_TICKS - EMERGE_VORTEX);
        double ease = k * k * (3 - 2 * k);
        double a = ease * Math.PI * 2.5;
        double r = 1 + ORBIT * 0.75 * ease;
        Vec3 at = core.add(Math.cos(a) * r, SWIM_HEIGHT * ease - 1.5 * (1 - ease), Math.sin(a) * r);
        Vec3 before = headCenter();
        setPos(at.x, at.y - getBbHeight() / 2, at.z);
        face(at.subtract(before));
        if (t == EMERGE_VORTEX + 1) {
            level.sendParticles(ParticleTypes.SONIC_BOOM, core.x, core.y + 0.5, core.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION, core.x, core.y, core.z, 6, 1.5, 0.3, 1.5, 0);
            playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 4.0F, 0.9F);
        }
        if (t % 3 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
                    core.x, core.y, core.z, 8, 0.8, 0.2, 0.8, 0.2);
        }
    }

    @Override
    protected void onEmerged(ServerLevel level) {
        velocity = Vec3.directionFromRotation(getXRot(), getYRot()).scale(SWIM_SPEED);
        orbitAngle = home == null ? 0 : Math.atan2(getZ() - (home.getZ() + 0.5), getX() - (home.getX() + 0.5));
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
        switch (state()) {
            case DIVE_WINDUP -> tickDiveWindup(level, target, ticks);
            case DIVE -> tickDive(level, ticks);
            case BREATH_CHARGE -> tickBreathCharge(level, target, ticks);
            case BREATH_FIRE -> tickBreathFire(level, target, ticks);
            default -> tickSwim(level, target);
        }
        pushWake(level);
        contactDamage(level);
        if (awakened() && tickCount % 4 == 0) {
            Vec3 c = headCenter();
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 2, 0.6, 0.6, 0.6, 0.01);
        }
    }

    private Vec3 arenaCenter() {
        return Vec3.atBottomCenterOf(home).add(0, 1, 0);
    }

    private double swimSpeed() {
        double s = enraged() ? ENRAGED_SPEED : SWIM_SPEED;
        return awakened() ? s * 1.15 : s;
    }

    /** 部屋の上空を輪を描いて泳ぐ。間をおいて技を選ぶ。 */
    private void tickSwim(ServerLevel level, @Nullable LivingEntity target) {
        double speed = swimSpeed();
        orbitAngle += speed / ORBIT;
        Vec3 c = arenaCenter();
        Vec3 goal = c.add(Math.cos(orbitAngle) * ORBIT, SWIM_HEIGHT + Math.sin(tickCount * 0.03) * 4, Math.sin(orbitAngle) * ORBIT);
        steer(goal, speed, 0.08);
        if (target != null && --attackCooldown <= 0) {
            chooseAttack(level, target);
        }
    }

    private void chooseAttack(ServerLevel level, LivingEntity target) {
        attackCooldown = pace(ATTACK_INTERVAL + random.nextInt(30));
        chained = false;
        if (enraged() && random.nextFloat() < 0.3F) {
            shedScales(level);
            return;
        }
        if (diveNext) {
            setState(DIVE_WINDUP, pace(DIVE_WINDUP_TICKS));
            playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 3.0F, 1.1F);
        } else {
            setState(BREATH_CHARGE, pace(BREATH_CHARGE_TICKS));
            playSound(SinguloSounds.get("ergosphere_leviathan.breath_charge"), 3.0F, 0.8F);
        }
        diveNext = !diveNext;
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
            velocity = at.subtract(head).normalize().scale(DIVE_SPEED);
            setState(DIVE, DIVE_TICKS);
            playSound(SinguloSounds.get("ergosphere_leviathan.dive"), 3.0F, 0.9F);
        }
    }

    /** 突進: 狙った場所を一直線に突き抜ける。部屋の端か床に近づくか、狙いを大きく越えたら終わる。 */
    private void tickDive(ServerLevel level, int ticks) {
        Vec3 aim = aim();
        Vec3 head = headCenter();
        moveBy(velocity);
        Vec3 c = arenaCenter();
        boolean past = head.subtract(aim).dot(velocity) > 0 && head.distanceTo(aim) > 6;
        boolean edge = head.subtract(c).horizontalDistance() > ARENA - 6 || head.y < c.y + 1.5 || head.y > c.y + SWIM_HEIGHT + 12;
        if (tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, head.x, head.y, head.z, 3, 0.4, 0.4, 0.4, 0.02);
        }
        if (ticks <= 1 || past || edge) {
            if (edge && head.y < c.y + 1.5) {
                // 床をかすめて舞い上がる（床に土煙）
                level.sendParticles(ParticleTypes.EXPLOSION, head.x, c.y, head.z, 3, 1, 0.2, 1, 0);
                velocity = new Vec3(velocity.x, Math.abs(velocity.y) * 0.6 + 0.2, velocity.z);
            }
            LivingEntity target = getTarget();
            if (enraged() && !chained && target != null && challenger(target) && random.nextFloat() < (awakened() ? 0.7F : 0.5F)) {
                chained = true;
                setState(DIVE_WINDUP, pace(14));
                playSound(SinguloSounds.get("ergosphere_leviathan.roar"), 2.5F, 1.3F);
            } else {
                velocity = velocity.normalize().scale(swimSpeed());
                orbitAngle = Math.atan2(head.z - c.z, head.x - c.x);
                setState(SWIM, 0);
            }
        }
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
            setState(BREATH_FIRE, BREATH_FIRE_TICKS);
            playSound(SinguloSounds.get("ergosphere_leviathan.breath_fire"), 4.0F, 0.8F);
        }
    }

    /** 渦の光線を吐く。体力半分からは、相手を追って少しずつ向きを変える。 */
    private void tickBreathFire(ServerLevel level, @Nullable LivingEntity target, int ticks) {
        velocity = Vec3.ZERO;
        Vec3 mouth = mouth();
        Vec3 dir = aim().subtract(mouth).normalize();
        if ((enraged() || awakened()) && target != null) {
            Vec3 want = target.getBoundingBox().getCenter().subtract(mouth).normalize();
            dir = dir.scale(0.95).add(want.scale(0.05)).normalize();
        }
        face(dir);
        Vec3 to = mouth.add(dir.scale(BREATH_RANGE));
        HitResult hit = level.clip(new ClipContext(mouth, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
        setAim(end);
        if (ticks % 4 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(mouth, end).inflate(BREATH_WIDTH),
                    e -> e.isAlive() && e != this && !RuinGuards.isGuard(e))) {
                if (!e.getBoundingBox().inflate(BREATH_WIDTH / 2).clip(mouth, end).isEmpty()) {
                    e.hurt(damageSources().indirectMagic(this, this), BREATH_DAMAGE);
                }
            }
        }
        for (int i = 0; i <= 10; i++) {
            Vec3 q = mouth.lerp(end, i / 10.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, q.x, q.y, q.z, 1, 0.2, 0.2, 0.2, 0.05);
        }
        if (ticks <= 1) {
            velocity = dir.scale(swimSpeed());
            Vec3 c = arenaCenter();
            orbitAngle = Math.atan2(getZ() - c.z, getX() - c.x);
            setState(SWIM, 0);
        }
    }

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
        playSound(SinguloSounds.get("ergosphere_leviathan.shed"), 3.0F, 0.9F);
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
        double push = enraged() ? WAKE_PUSH * 1.35 : WAKE_PUSH;
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
        float damage = dive ? DIVE_DAMAGE : CONTACT_DAMAGE;
        List<AABB> boxes = new ArrayList<>();
        boxes.add(getBoundingBox().inflate(0.2));
        for (LeviathanSegment p : parts) {
            boxes.add(p.getBoundingBox().inflate(0.1));
        }
        for (Player p : challengers(level, ARENA + 6)) {
            for (AABB box : boxes) {
                if (box.intersects(p.getBoundingBox())) {
                    if (p.hurt(damageSources().mobAttack(this), damage) && dive && velocity.lengthSqr() > 1e-4) {
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

    // ------------------------------------------------------------------ 倒されたとき

    @Override
    public void die(DamageSource source) {
        super.die(source);
        setState(SWIM, 0);
        if (level() instanceof ServerLevel) {
            playSound(SinguloSounds.get("ergosphere_leviathan.death"), 4.0F, 0.9F);
        }
    }

    /**
     * 倒されたときの演出: しばらく身をよじり、鱗を尾から剥がして降らせながら、螺旋を描いて炉心の渦へ沈む。
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
            // 身をよじる（その場で頭を左右へ振る）
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
        // 鱗が尾から順に剥がれ落ちる
        int peel = SEGMENTS - 1 - (t - 4) / 7;
        if (t >= 4 && (t - 4) % 7 == 0 && peel >= 0 && peel < SEGMENTS) {
            LeviathanSegment p = parts[peel];
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, plate()), p.getX(), p.getY() + 1, p.getZ(),
                    30, 0.8, 0.8, 0.8, 0.15);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 8, 0.5, 0.5, 0.5, 0.05);
            level.playSound(null, p.blockPosition(), SinguloSounds.get("ergosphere_leviathan.shed"), SoundSource.HOSTILE, 1.5F, 1.2F);
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
            level.playSound(null, home, SinguloSounds.get("ergosphere_leviathan.implode"), SoundSource.HOSTILE, 4.0F, 0.7F);
        }
        if (t == DEATH_TICKS - 8) {
            // 渦がつぶれて弾ける（ダメージはない）
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        orbitAngle = tag.getDouble("orbit");
        if (home != null) {
            entityData.set(HOME, home);
        }
        trail.clear();
    }

    /** 跡の古い点を、帯の描画用に順に返す（新しいものから）。 */
    public Iterator<TrailPoint> trailIterator() {
        return trail.iterator();
    }
}
