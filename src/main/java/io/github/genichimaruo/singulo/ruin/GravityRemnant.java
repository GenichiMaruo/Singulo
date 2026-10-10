package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 重力の澱（封鎖培養施設のボス）。培養施設で生まれた重力の失敗作が、封じ込め槽を破って出てくる。
 * <ul>
 *   <li>歪んだ黒い核と、その周りを回る瓦礫の殻。殻がある間は、1撃ごとに殻が1つ剥がれる代わりに受けるダメージが小さい</li>
 *   <li>殻の瓦礫を撃ち出す。剥がれきると、しばらくして床から瓦礫を引き寄せて殻を作り直す</li>
 *   <li>周りの相手を浮かせてから、強い重力で床へ叩きつける（落下ダメージ。重力ブーツや衝撃発電パネルで防げる）</li>
 *   <li>体力が半分を切ると、核のまわりに小さな重力井戸ができ、近くの相手を引き寄せる。核に触れると潮汐で傷つく</li>
 *   <li>倒されると、殻が崩れ落ち、核が鼓動を速めて脈打ったあと、一点へ縮んで閃光と衝撃波を残して消える</li>
 * </ul>
 */
public class GravityRemnant extends RuinBoss {
    private static final EntityDataAccessor<Integer> SHELL = SynchedEntityData.defineId(GravityRemnant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIP = SynchedEntityData.defineId(GravityRemnant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GATHER = SynchedEntityData.defineId(GravityRemnant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DEATH_SHELL = SynchedEntityData.defineId(GravityRemnant.class, EntityDataSerializers.INT);

    public static final int EMERGE_TICKS = 110;
    public static final int DEATH_TICKS = 80;
    public static final float HEALTH = 450;
    public static final int SHELL_PIECES = 8;
    /** 殻がある間に受けるダメージの倍率。 */
    public static final float SHELL_FACTOR = 0.3F;
    static final int DEBRIS_INTERVAL = 70;
    /** 殻が剥がれきってから作り直すまで（引き寄せている時間を含む）。 */
    static final int REGROW_DELAY = 160;
    public static final int GATHER_TICKS = 40;
    static final int FLIP_INTERVAL = 140;
    /** 浮かせてから叩きつけるまでの tick。 */
    public static final int FLIP_HOLD = 22;
    public static final double FLIP_RADIUS = 14;
    public static final double LIFT_SPEED = 1.25;
    public static final double SLAM_SPEED = 3.0;
    public static final float SLAM_FALL = 8;
    /** 重力井戸（体力半分から）。 */
    public static final double WELL_RADIUS = 9;
    static final double WELL_PULL = 0.07;
    public static final double CORE_RADIUS = 2.2;
    public static final float CORE_DAMAGE = 3;
    static final double HOVER = 2.5;
    /** 殻・瓦礫の見た目に使う建材。 */
    static final int RUBBLE_KINDS = 6;

    private int debrisCooldown = DEBRIS_INTERVAL;
    private int flipCooldown = FLIP_INTERVAL / 2;
    private int bareTicks;

    public GravityRemnant(EntityType<? extends GravityRemnant> type, Level level) {
        super(type, level, BossEvent.BossBarColor.PURPLE);
        this.xpReward = 120;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SHELL, SHELL_PIECES);
        entityData.define(FLIP, 0);
        entityData.define(GATHER, 0);
        entityData.define(DEATH_SHELL, 0);
    }

    @Override
    public int emergeDuration() {
        return EMERGE_TICKS;
    }

    @Override
    public double spawnHeight() {
        return 3.0;
    }

    /** 残っている殻の瓦礫の数。 */
    public int shell() {
        return entityData.get(SHELL);
    }

    public void setShell(int n) {
        entityData.set(SHELL, Math.max(0, Math.min(SHELL_PIECES, n)));
    }

    /** 浮かせてから叩きつけるまでの残り tick（0 ならしていない）。 */
    public int flipTicks() {
        return entityData.get(FLIP);
    }

    /** 床から瓦礫を引き寄せている残り tick。 */
    public int gatherTicks() {
        return entityData.get(GATHER);
    }

    /** 重力井戸ができているか（体力が半分以下）。 */
    public boolean wellOpen() {
        return getHealth() <= getMaxHealth() / 2 && emergeTicks() <= 0;
    }

    /** 殻の i 番目の瓦礫の見た目。 */
    public static BlockState rubble(int i) {
        return switch (Math.floorMod(i * 7 + 3, RUBBLE_KINDS)) {
            case 0 -> SinguloBlocks.RUIN_PANEL.get().defaultBlockState();
            case 1 -> SinguloBlocks.CRACKED_RUIN_PANEL.get().defaultBlockState();
            case 2 -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            case 3 -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case 4 -> SinguloBlocks.MOSSY_RUIN_PANEL.get().defaultBlockState();
            default -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 20));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ 出現（槽を壊すのは封印核）

    @Override
    protected void onEmergeTick(ServerLevel level, int t) {
        Vec3 c = position().add(0, 0.6, 0);
        float e = (float) t / EMERGE_TICKS;
        if (e < 0.75F) {
            // 槽の中で、まわりの塵が渦を巻いて吸い込まれていく
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 1.5 + random.nextDouble() * 1.5;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * r, c.y + (random.nextDouble() - 0.5) * 3,
                        c.z + Math.sin(a) * r, 0, -Math.cos(a), 0, -Math.sin(a), 0.2);
            }
        } else {
            // 槽が砕け、床の瓦礫が浮き上がって殻になる
            level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 3, 1.5, 1.5, 1.5, 0.02);
        }
        if (t == 1) {
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("gravity_remnant.emerge"), 2.5F, 1.0F);
        }
    }

    @Override
    protected void onEmerged(ServerLevel level) {
        level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 0.6, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() - 1.5, getZ(), 10, 3, 0.3, 3, 0);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.5F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), e -> e != this)) {
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
            if (push.lengthSqr() > 1e-4) {
                e.push(push.normalize().x * 1.1, 0.4, push.normalize().z * 1.1);
                e.hurtMarked = true;
            }
        }
        setShell(SHELL_PIECES);
    }

    // ------------------------------------------------------------------ 戦闘

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isDeadOrDying()) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        LivingEntity target = getTarget();
        drift(target);
        tickFlip(level);
        tickShell(level);
        if (wellOpen()) {
            tickWell(level);
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        if (--debrisCooldown <= 0) {
            launchDebris(target);
        }
        if (--flipCooldown <= 0 && flipTicks() == 0) {
            startFlip(level);
        }
    }

    /** 核の上あたりをゆっくり漂い、相手のほうへ少し寄る。 */
    private void drift(@Nullable LivingEntity target) {
        if (home == null) {
            return;
        }
        Vec3 h = Vec3.atBottomCenterOf(home).add(0, 1 + HOVER, 0);
        Vec3 goal = h;
        if (target != null && target.isAlive()) {
            Vec3 toward = target.position().subtract(h).multiply(1, 0, 1);
            double reach = Math.min(toward.length() - 5, ARENA_RADIUS / 2.0);
            if (reach > 0) {
                goal = h.add(toward.normalize().scale(reach));
            }
            goal = goal.add(0, Math.sin(tickCount * 0.05) * 0.6, 0);
        }
        Vec3 v = goal.subtract(position()).scale(0.03);
        if (v.length() > 0.15) {
            v = v.normalize().scale(0.15);
        }
        setDeltaMovement(getDeltaMovement().scale(0.7).add(v.scale(0.3)));
    }

    /** 殻の瓦礫を撃ち出す（体力半分からは2つ）。 */
    public int launchDebris(LivingEntity target) {
        debrisCooldown = DEBRIS_INTERVAL + random.nextInt(20);
        int n = Math.min(shell(), wellOpen() ? 2 : 1);
        for (int i = 0; i < n; i++) {
            int piece = shell() - 1;
            setShell(piece);
            double a = tickCount * 0.06 + piece * Math.PI * 2 / SHELL_PIECES;
            Vec3 from = position().add(Math.cos(a) * 1.6, 0.6, Math.sin(a) * 1.6);
            GravityDebris debris = new GravityDebris(level(), this, rubble(piece));
            debris.setPos(from);
            Vec3 d = target.getEyePosition().subtract(from).normalize().add(0, 0.12, 0);
            debris.shoot(d.x, d.y, d.z, 1.1F, 2.0F);
            level().addFreshEntity(debris);
        }
        if (n > 0) {
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("gravity_remnant.debris_launch"), 1.5F, 1.0F);
        }
        return n;
    }

    /** 殻が剥がれきったら、しばらくして床から瓦礫を引き寄せて作り直す。 */
    private void tickShell(ServerLevel level) {
        int gather = gatherTicks();
        if (gather > 0) {
            entityData.set(GATHER, gather - 1);
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 3 + random.nextDouble() * 4;
                BlockState s = rubble(random.nextInt(RUBBLE_KINDS));
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, s),
                        getX() + Math.cos(a) * r, getY() - HOVER, getZ() + Math.sin(a) * r, 2, 0.2, 0.1, 0.2, 0.05);
            }
            if (gather - 1 == 0) {
                setShell(SHELL_PIECES);
                bareTicks = 0;
            }
            return;
        }
        if (shell() == 0 && ++bareTicks >= REGROW_DELAY - GATHER_TICKS) {
            entityData.set(GATHER, GATHER_TICKS);
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("gravity_remnant.shell_regrow"), 2.0F, 1.0F);
        }
    }

    /** 周りの相手を浮かせる。少しあとで床へ叩きつける。 */
    public void startFlip(ServerLevel level) {
        flipCooldown = FLIP_INTERVAL + random.nextInt(40);
        entityData.set(FLIP, FLIP_HOLD);
        for (LivingEntity e : flipTargets(level)) {
            e.setDeltaMovement(e.getDeltaMovement().x * 0.3, LIFT_SPEED, e.getDeltaMovement().z * 0.3);
            e.hurtMarked = true;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 0.5, e.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
        }
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("gravity_remnant.lift"), 2.0F, 1.0F);
    }

    private void tickFlip(ServerLevel level) {
        int flip = flipTicks();
        if (flip <= 0) {
            return;
        }
        entityData.set(FLIP, flip - 1);
        if (flip - 1 > 0) {
            return;
        }
        for (LivingEntity e : flipTargets(level)) {
            if (e.onGround()) {
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().x, -SLAM_SPEED, e.getDeltaMovement().z);
            e.fallDistance = Math.max(e.fallDistance, SLAM_FALL);
            e.hurtMarked = true;
            level.sendParticles(ParticleTypes.SQUID_INK, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        }
        playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("gravity_remnant.slam"), 2.0F, 1.0F);
    }

    private java.util.List<LivingEntity> flipTargets(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(FLIP_RADIUS),
                e -> e != this && !RuinGuards.isGuard(e) && e.distanceTo(this) <= FLIP_RADIUS
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
    }

    /** 重力井戸: 近くの相手を核へ引き寄せ、核に触れた相手を潮汐で傷つける。 */
    private void tickWell(ServerLevel level) {
        Vec3 c = position().add(0, getBbHeight() / 2, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(WELL_RADIUS),
                e -> e != this && !RuinGuards.isGuard(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double d = to.length();
            if (d > WELL_RADIUS || d < 1e-3) {
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().add(to.normalize().scale(WELL_PULL * (1 - d / WELL_RADIUS))));
            e.hurtMarked = true;
            if (d <= CORE_RADIUS && tickCount % 10 == 0) {
                e.hurt(SinguloDamageTypes.tidal(level, this), CORE_DAMAGE);
            }
        }
        if (tickCount % 4 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * WELL_RADIUS * 0.8, c.y + (random.nextDouble() - 0.5) * 2,
                    c.z + Math.sin(a) * WELL_RADIUS * 0.8, 0, -Math.cos(a), 0, -Math.sin(a), 0.4);
        }
    }

    /** 殻がある間は受けるダメージが小さく、1撃ごとに殻が1つ剥がれる。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (emergeTicks() > 0 || RuinGuards.friendlyFire(source) || isInvulnerableTo(source)) {
            return super.hurt(source, amount);
        }
        boolean bypass = source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (shell() > 0 && !bypass && amount > 0 && invulnerableTime <= 10) {
            int piece = shell() - 1;
            setShell(piece);
            if (level() instanceof ServerLevel level) {
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, rubble(piece)),
                        getX(), getY() + 0.6, getZ(), 20, 0.6, 0.6, 0.6, 0.1);
            }
            return super.hurt(source, amount * SHELL_FACTOR);
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        entityData.set(FLIP, 0);
        entityData.set(GATHER, 0);
        if (level() instanceof ServerLevel) {
            playSound(io.github.genichimaruo.singulo.registry.SinguloSounds.get("gravity_remnant.death_collapse"), 2.5F, 1.0F);
        }
    }

    @Override
    public int deathDuration() {
        return DEATH_TICKS;
    }

    /** 倒されたとき、殻が崩れ落ち始める時点の殻の数（描画用）。 */
    public int deathShell() {
        return entityData.get(DEATH_SHELL);
    }

    /**
     * 倒されたときの演出: 殻の瓦礫が床へ崩れ落ち、むき出しの核が鼓動を速めながら不安定に脈打つ。
     * やがて周りの光と塵を吸い込みながら一点へ縮み、最後に閃光と衝撃波を残して消える。
     */
    @Override
    protected void onDeathTick(ServerLevel level, int t) {
        Vec3 c = position().add(0, getBbHeight() / 2, 0);
        if (t == 1) {
            entityData.set(DEATH_SHELL, Math.max(shell(), 3));
        }
        if (t == 14) {
            // 瓦礫が床に落ちる
            for (int i = 0; i < SHELL_PIECES; i++) {
                double a = i * Math.PI * 2 / SHELL_PIECES;
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, rubble(i)),
                        c.x + Math.cos(a) * 1.6, getY() - HOVER + 0.2, c.z + Math.sin(a) * 1.6, 10, 0.3, 0.1, 0.3, 0.1);
            }
        }
        int collapse = DEATH_TICKS * 5 / 8;
        if (t < collapse) {
            // 不安定な鼓動（だんだん速く）
            int beat = Math.max(4, 16 - t / 4);
            if (t % beat == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 30, 0.2, 0.2, 0.2, 0.6);
            }
        } else if (t < DEATH_TICKS - 6) {
            // 吸い込みながら縮む
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double b = (random.nextDouble() - 0.5) * Math.PI;
                double r = 4 + random.nextDouble() * 3;
                Vec3 d = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + d.x * r, c.y + d.y * r, c.z + d.z * r, 0, -d.x, -d.y, -d.z, 0.9);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), e -> e != this && !RuinGuards.isGuard(e))) {
                Vec3 to = c.subtract(e.position());
                if (to.lengthSqr() > 1) {
                    e.setDeltaMovement(e.getDeltaMovement().add(to.normalize().scale(0.05)));
                    e.hurtMarked = true;
                }
            }
            if (t == DEATH_TICKS - 8) {
                // 爆縮の音は頭に一瞬の静けさがあるので、少し早めに鳴らす
                level.playSound(null, blockPosition(), io.github.genichimaruo.singulo.registry.SinguloSounds.get("gravity_remnant.death_implode"), SoundSource.HOSTILE, 3.0F, 1.0F);
            }
        } else if (t == DEATH_TICKS - 6) {
            // 一点につぶれて弾ける（ダメージはない）
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.1, 0.1, 0.1, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 0.1, 0.1, 0.1, 0.6);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), e -> e != this && !RuinGuards.isGuard(e))) {
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) {
                    e.push(push.normalize().x * 1.2, 0.5, push.normalize().z * 1.2);
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
        return io.github.genichimaruo.singulo.registry.SinguloSounds.get("gravity_remnant.hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("shell", shell());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("shell")) {
            setShell(tag.getInt("shell"));
        }
    }
}
