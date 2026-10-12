package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.generated.ServerConfig;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 遺構のボス部屋の番人（残響の番人・重力の澱）に共通の決まり。
 * <ul>
 *   <li>番人の封印核（{@link GuardianCoreBlockEntity}）が起こし、倒されると核に知らせて保管庫の封鎖を解く</li>
 *   <li>出現の演出のあいだは動かず、傷つかない。ボスバーは演出が終わってから出る</li>
 *   <li>部屋の周りに挑戦者がいなくなると、核へ戻る（設定 wardenResetOnLeave に従う）</li>
 *   <li>ほかの守り手とは互いに狙わず、互いの攻撃で傷つかない</li>
 * </ul>
 * 分身（{@link #isMinion()}）は、ボスバー・核への知らせ・ドロップを持たない。
 * <p>
 * 攻略後の再戦: 覚醒核を封印核に使うと「覚醒した」個体が現れる（{@link #makeAwakened()}）。体力×awakenedGuardianHealthMultiplier、
 * 与えるダメージ×awakenedGuardianDamageMultiplier、技の間×{@value #AWAKENED_PACE}、本気になる体力の割合が早まる。
 * 倒すと追加の報酬（{@link #awakenedRewards()}）が出る。やめて部屋を離れると覚醒核は封印核の上に返る。
 * <p>
 * 挑戦人数: 部屋にいる挑戦者が増えると、体力（割合を保ったまま）・技の速さ・報酬の数が増える（{@link BossScaling}）。
 */
public abstract class RuinBoss extends Monster {
    private static final EntityDataAccessor<Integer> EMERGE = SynchedEntityData.defineId(RuinBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AWAKENED = SynchedEntityData.defineId(RuinBoss.class, EntityDataSerializers.BOOLEAN);
    /** 部屋を守る広さ（核からのブロック数）。 */
    public static final int ARENA_RADIUS = 20;
    /** 挑戦者がいないのを何回（1秒ごと）続けて確かめたら戻るか。 */
    static final int RESET_CHECKS = 5;
    /** 覚醒した個体の技の間（通常の何倍か）。 */
    public static final float AWAKENED_PACE = 0.75F;
    /** 本気になる（技が増える）体力の割合。覚醒した個体は早い。 */
    public static final float ENRAGE = 0.5F, AWAKENED_ENRAGE = 0.65F;
    /** ゲームの決まりの最大体力の上限。 */
    static final double MAX_HEALTH_CAP = 1024;

    protected final ServerBossEvent bossBar;
    /** 起こした封印核の位置（守る場所の中心）。 */
    @Nullable
    protected BlockPos home;
    private int emptyChecks;
    /** 受けるダメージの倍率（最大体力の上限を超える硬さを、受けるダメージを減らして表す）。 */
    protected float damageScale = 1;
    /** 強さに数えている挑戦者の数（増えるだけで、減らない）。 */
    private int participants = 1;
    /** 無敵時間をプレイヤーごとに分ける（大勢で殴れば、人数ぶんの攻撃が通る）。 */
    private final HurtWindows hurtWindows = new HurtWindows();
    /** 報酬に数える挑戦者の数。戦いが始まったとき（出現の演出が終わったとき）の人数で決まり、途中で合流しても増えない。0 ならまだ決まっていない。 */
    private int rewardParticipants;
    /** ボスバーのもとの名前（人数を添える前）。null なら実体の名前。 */
    @Nullable
    private Component barName;

    protected RuinBoss(EntityType<? extends RuinBoss> type, Level level, BossEvent.BossBarColor color) {
        super(type, level);
        bossBar = new ServerBossEvent(getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        setPersistenceRequired();
    }

    /** 出現の演出の長さ（tick）。 */
    public abstract int emergeDuration();

    /** 核の上の、現れる高さ（ブロック）。 */
    public double spawnHeight() {
        return 0;
    }

    /** 分身か（ボスバー・核への知らせ・ドロップがない）。 */
    public boolean isMinion() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EMERGE, 0);
        builder.define(AWAKENED, false);
    }

    // ------------------------------------------------------------------ 覚醒（攻略後の再戦）

    /** 覚醒した個体か。 */
    public boolean awakened() {
        return entityData.get(AWAKENED);
    }

    /** 覚醒した番人の体力・与えるダメージの倍率（設定 awakenedGuardianHealthMultiplier・awakenedGuardianDamageMultiplier）。 */
    public static double awakenedHealthMultiplier() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.AWAKENED_GUARDIAN_HEALTH_MULTIPLIER.get() : 2.5;
    }

    public static double awakenedDamageMultiplier() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.AWAKENED_GUARDIAN_DAMAGE_MULTIPLIER.get() : 1.5;
    }

    /** 覚醒した個体にする（起こす前に呼ぶ）。体力を増やし、ボスバーの名前と色を変える。 */
    public void makeAwakened() {
        entityData.set(AWAKENED, true);
        double health = effectiveMaxHealth() * awakenedHealthMultiplier();
        setEffectiveHealth(health);
        xpReward = awakenedXp();
        applyAwakenedLook();
    }

    private void applyAwakenedLook() {
        if (!hasCustomName()) {
            setBarName(Component.translatable(getType().getDescriptionId() + ".awakened"));
        }
        bossBar.setColor(awakenedColor());
    }

    /** ボスバーの名前を変える（2人以上で挑んでいれば人数を添える）。 */
    protected void setBarName(Component name) {
        barName = name;
        bossBar.setName(BossScaling.barName(name, participants));
    }

    // ------------------------------------------------------------------ 挑戦人数

    /** 強さに数えている挑戦者の数。 */
    public int participants() {
        return participants;
    }

    /**
     * 挑戦者の数を増やす（減らす方向は無視する）。体力をその時の割合を保ったまま増やし、ボスバーに人数を添える。
     * 部屋の挑戦者を数えて呼ぶ（テストからも直接呼べる）。
     */
    public void setParticipants(int players) {
        int n = BossScaling.clamp(players);
        if (n <= participants) {
            return;
        }
        rescaleHealth(BossScaling.healthFactor(n) / BossScaling.healthFactor(participants));
        participants = n;
        setBarName(barName != null ? barName : getDisplayName());
    }

    /** 報酬に数える挑戦者の数（まだ決まっていなければ、今の人数で決める）。 */
    public int rewardParticipants() {
        if (rewardParticipants <= 0) {
            lockRewardParticipants();
        }
        return rewardParticipants;
    }

    /** 報酬に数える人数を、今の人数で決める（戦いが始まったとき。一度決めたら変わらない）。 */
    public void lockRewardParticipants() {
        if (rewardParticipants <= 0) {
            rewardParticipants = participants;
        }
    }

    /** 体力を factor 倍にする（今の体力の割合は保つ。最大体力の上限を超える分は受けるダメージで表す）。 */
    private void rescaleHealth(double factor) {
        float fraction = getHealth() / getMaxHealth();
        double effective = effectiveMaxHealth() * factor;
        double max = Math.min(effective, MAX_HEALTH_CAP);
        damageScale = (float) (max / effective);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        setHealth((float) (max * fraction));
    }

    /** 実際の硬さ（受けるダメージを減らした分を含めた体力）。 */
    public float effectiveMaxHealth() {
        return getMaxHealth() / damageScale;
    }

    /** 覚醒した個体のボスバーの色。 */
    protected BossEvent.BossBarColor awakenedColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    /** 覚醒した個体の経験値。 */
    protected int awakenedXp() {
        return xpReward * 3;
    }

    /** 覚醒した個体を倒したときの追加の報酬（アイテムID と個数）。通常のドロップに加えて出る。 */
    protected String[][] awakenedRewards() {
        return new String[0][];
    }

    /** 技の間（tick）。覚醒した個体と、大勢で挑まれたときは短い。 */
    protected int pace(int ticks) {
        float k = (awakened() ? AWAKENED_PACE : 1F) * BossScaling.paceFactor(participants);
        return k >= 1 ? ticks : Math.max(4, Math.round(ticks * k));
    }

    /** 本気になる体力の割合。 */
    protected float enrageFraction() {
        return awakened() ? AWAKENED_ENRAGE : ENRAGE;
    }

    /**
     * 覚醒した番人が与えるダメージを増やす（近接・飛び道具・技など、出どころがこの個体のものすべて）。
     * NeoForge のイベントで受け手の側から掛ける。
     */
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof RuinBoss b && b.awakened() && !RuinGuards.isGuard(event.getEntity())) {
            event.setAmount((float) (event.getAmount() * awakenedDamageMultiplier()));
        }
    }

    /** 出現の演出の残り tick。 */
    public int emergeTicks() {
        return entityData.get(EMERGE);
    }

    /** 出現の演出の進み（0〜1。出現済みなら 1）。 */
    public float emergeProgress(float partialTick) {
        int left = emergeTicks();
        return left <= 0 ? 1 : Math.min(1, 1 - (left - partialTick) / emergeDuration());
    }

    /** 出現の演出を始める（そのあいだは動かず、傷つかない）。 */
    public void startEmerging() {
        entityData.set(EMERGE, emergeDuration());
        setNoAi(true);
        bossBar.setVisible(false);
    }

    /** 出現の演出を飛ばす（テスト用）。 */
    public void skipEmerging() {
        entityData.set(EMERGE, 0);
        setNoAi(false);
        bossBar.setVisible(true);
    }

    /** 起こした核を覚え、その周りから離れないようにする。 */
    public void awaken(BlockPos core) {
        this.home = core;
        restrictTo(core, arenaRadius());
    }

    /** 守る広さ（核からのブロック数）。挑戦者がこの外（＋4）へ出ると戻る。 */
    public int arenaRadius() {
        return ARENA_RADIUS;
    }

    /**
     * 体力を決める（最大体力の上限 1024 を超える分は、受けるダメージを減らして同じ硬さにする）。
     * 生まれたときに呼ぶ。覚醒するときは、この硬さに倍率を掛ける。
     */
    protected void setEffectiveHealth(double health) {
        double max = Math.min(health, MAX_HEALTH_CAP);
        damageScale = (float) (max / health);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        setHealth((float) max);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    @Override
    public void tick() {
        super.tick();
        hurtWindows.tick();
        if (!level().isClientSide && emergeTicks() > 0) {
            ServerLevel level = (ServerLevel) level();
            int left = emergeTicks() - 1;
            entityData.set(EMERGE, left);
            setDeltaMovement(Vec3.ZERO);
            onEmergeTick(level, emergeDuration() - left);
            if (left <= 0) {
                setNoAi(false);
                bossBar.setVisible(!isMinion());
                onEmerged(level);
                if (!isMinion()) {
                    // 戦いの始まり: いま部屋にいる人数で強さと報酬の人数を決める（途中で合流しても報酬は増えない）
                    int players = countChallengers(level);
                    if (players > participants) {
                        setParticipants(players);
                    }
                    lockRewardParticipants();
                }
            }
        }
    }

    /** 出現の演出の t tick 目（1〜emergeDuration）。音や粒を出す。 */
    protected void onEmergeTick(ServerLevel level, int t) {}

    /** 出現の演出が終わったとき。 */
    protected void onEmerged(ServerLevel level) {}

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 20 == 0 && !isMinion()) {
            checkChallengers((ServerLevel) level());
        }
    }

    /** 守る場所の周りに挑戦者がいなくなったら、核へ戻る。 */
    private void checkChallengers(ServerLevel level) {
        if (home == null) {
            return;
        }
        int players = countChallengers(level);
        boolean present = players > 0;
        if (players > participants && emergeTicks() <= 0 && isAlive()) {
            setParticipants(players);
        }
        if (emergeTicks() <= 0) {
            lockRewardParticipants();
        }
        emptyChecks = present ? 0 : emptyChecks + 1;
        boolean reset = !ServerConfig.SPEC.isLoaded() || ServerConfig.WARDEN_RESET_ON_LEAVE.get();
        if (emptyChecks >= RESET_CHECKS && reset) {
            onAbandoned(level);
            discard();
        }
    }

    /** 部屋にいる挑戦者の数（強さと、戦いを続けるかに使う）。部屋の形が違うボスは数え方を変える。 */
    protected int countChallengers(ServerLevel level) {
        if (home == null) {
            return 0;
        }
        return level.getEntitiesOfClass(Player.class, new AABB(home).inflate(arenaRadius() + 4),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()).size();
    }

    /** 挑戦者がいなくなって戻るとき（このあと消える）。核に知らせ、覚醒戦なら呼び出しに使った覚醒核を返す。 */
    protected void onAbandoned(ServerLevel level) {
        GuardianCoreBlockEntity core = core(level);
        if (core != null) {
            core.onBossReset(level, awakened());
        }
        if (awakened() && home != null) {
            // 覚醒戦をやめたときは、呼び出しに使った覚醒核を返す（やり直しが重くなりすぎないように）
            net.minecraft.world.item.Item item = GuardianCoreBlockEntity.awakeningCoreFor(getType());
            if (item != null) {
                net.minecraft.world.level.block.Block.popResource(level, home.above(), new net.minecraft.world.item.ItemStack(item));
            }
        }
    }

    @Nullable
    protected GuardianCoreBlockEntity core(ServerLevel level) {
        return home != null && level.getBlockEntity(home) instanceof GuardianCoreBlockEntity c ? c : null;
    }

    /** 挑戦者（創造・観戦モードでない、生きたプレイヤー）。 */
    protected static boolean challenger(Entity e) {
        return e instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return RuinGuards.isGuard(other) || super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (emergeTicks() > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;            // 出現の演出のあいだは傷つかない
        }
        if (RuinGuards.friendlyFire(source)) {
            return false;
        }
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= damageScale;
        }
        return hurtWindows.hurt(this, source, amount, super::hurt, () -> lastHurt, v -> lastHurt = v);
    }

    /** source の攻撃が、いまその攻撃者の無敵時間（同じ強さ以下は通らない間）の中か。プレイヤーごとに分けて数える。 */
    protected boolean inHurtWindow(DamageSource source) {
        return hurtWindows.within(this, source);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && !isMinion()) {
            GuardianCoreBlockEntity core = core(level);
            if (core != null) {
                core.onBossDefeated(level);
            }
            if (awakened()) {
                dropAwakenedRewards(level);
            }
        }
    }

    private void dropAwakenedRewards(ServerLevel level) {
        for (int roll = 0; roll < BossScaling.lootRolls(rewardParticipants()); roll++) {
            for (String[] r : awakenedRewards()) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(io.github.genichimaruo.singulo.Singulo.id(r[0]));
                spawnAtLocation(new net.minecraft.world.item.ItemStack(item, Integer.parseInt(r[1])), 1.0F);
            }
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
        io.github.genichimaruo.singulo.registry.SinguloTriggers.milestoneNear(level, blockPosition(), arenaRadius() + 16, "awakened_" + id);
        Component msg = Component.translatable("gui.singulo.guardian.awakened_defeated");
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(arenaRadius() + 16))) {
            p.displayClientMessage(msg, false);
        }
    }

    /** 倒されたときの演出の長さ（tick）。そのあいだ体は残り、終わると消える。 */
    public int deathDuration() {
        return 20;
    }

    /** 倒されたときの演出の進み（0〜1）。倒されていなければ 0。 */
    public float deathProgress(float partialTick) {
        return deathTime <= 0 ? 0 : Math.min(1, (deathTime + partialTick) / deathDuration());
    }

    /** 倒されたときの演出の t tick 目（1〜deathDuration）。音や粒を出す。 */
    protected void onDeathTick(ServerLevel level, int t) {}

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel level && !isMinion()) {
            setDeltaMovement(Vec3.ZERO);
            onDeathTick(level, deathTime);
        }
        if (deathTime >= (isMinion() ? 20 : deathDuration()) && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    /** 落とす物（大勢で挑んだら人数ぶん）。分身は落とさない。 */
    @Override
    protected void dropFromLootTable(DamageSource source, boolean recentlyHit) {
        if (!isMinion()) {
            for (int roll = 0; roll < BossScaling.lootRolls(rewardParticipants()); roll++) {
                super.dropFromLootTable(source, recentlyHit);
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
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isMinion()) {
            bossBar.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("emerge", emergeTicks());
        tag.putBoolean("awakened", awakened());
        tag.putFloat("damage_scale", damageScale);
        tag.putInt("participants", participants);
        tag.putInt("reward_participants", rewardParticipants);
        if (home != null) {
            tag.put("home", NbtUtils.writeBlockPos(home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(EMERGE, tag.getInt("emerge"));
        entityData.set(AWAKENED, tag.getBoolean("awakened"));
        if (tag.contains("damage_scale")) {
            damageScale = BossScaling.validDamageScale(tag.getFloat("damage_scale"));
        }
        participants = Math.max(1, tag.getInt("participants"));
        // 以前の版で保存された戦い（報酬の人数を持たない）は、そのときの人数で決める
        rewardParticipants = tag.contains("reward_participants") ? tag.getInt("reward_participants")
                : emergeTicks() <= 0 ? participants : 0;
        home = NbtUtils.readBlockPos(tag, "home").orElse(null);
        if (home != null) {
            restrictTo(home, arenaRadius());
        }
        setBarName(getDisplayName());
        if (awakened()) {
            xpReward = awakenedXp();
            applyAwakenedLook();
        }
    }
}
