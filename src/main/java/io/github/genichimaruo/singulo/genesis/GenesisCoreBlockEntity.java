package io.github.genichimaruo.singulo.genesis;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity;
import io.github.genichimaruo.singulo.machine.TimeFields;
import io.github.genichimaruo.singulo.network.EndingPayload;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloDamageTypes;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloSounds;
import io.github.genichimaruo.singulo.registry.SinguloTriggers;
import io.github.genichimaruo.singulo.ruin.AncientRecords;
import io.github.genichimaruo.singulo.ruin.EchoSentinel;
import io.github.genichimaruo.singulo.ruin.WardenSingularity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 創世炉の核。まわりに創世炉の枠（8）と創世炉の柱（4本×3段）を組むと形ができる（{@link #FRAMES}・{@link #PYLONS}）。
 * 旧文明は「すべての質量をエネルギーに」変えようとして滅んだ。創世炉は逆に、エネルギーから宇宙（質量）を生む。
 * <ol>
 *   <li><b>揺籃</b>: 形ができたら、原初の種を核に使う</li>
 *   <li><b>種火</b>: genesisSeedEnergy（既定 1.5 TFE）を注ぐ。1 tick に受け取るのは genesisMaxInput（既定 1 GFE）まで</li>
 *   <li><b>安定化</b>: 維持の電力（genesisUpkeep、既定 100 MFE/t）を受けながら {@value #STABILIZE_TICKS} tick 進める。
 *       ときどき暴走の兆し（警報）が来る。そのあいだは<b>投入を止める</b>（素手で核を使って開く画面のスイッチか、核に赤石信号を入れる）と収まり、
 *       止めないと不安定度が跳ね上がる。不安定度が 100% になると崩落し、進みが戻る。重力の漏れ・記録の残響・引力の波も起きる。
 *       シールドの中なら暴走と事件が弱まり、Tシリンダーの加速の場の中なら速く進む</li>
 *   <li><b>誕生</b>: {@value #BIRTH_TICKS} tick の演出（重力崩壊・静寂・閃光・膨張・銀河）。夜空に自分の銀河が昇る</li>
 *   <li><b>完成</b>: 質量生成。手に持った物を核に使うと、それを作る物に決める（{@link MassSynthesis} の表にある物だけ）。
 *       電力を注ぐと作り、画面の出来上がりスロットから受け取れる（下からホッパーなどでも取り出せる）</li>
 * </ol>
 */
public class GenesisCoreBlockEntity extends BlockEntity {
    public static final int UNFORMED = 0, CRADLE = 1, KINDLING = 2, STABILIZING = 3, BIRTH = 4, COMPLETE = 5;

    /** 枠の位置（核と同じ高さ、八角形）。 */
    public static final BlockPos[] FRAMES = {new BlockPos(3, 0, 0), new BlockPos(-3, 0, 0), new BlockPos(0, 0, 3), new BlockPos(0, 0, -3),
            new BlockPos(2, 0, 2), new BlockPos(2, 0, -2), new BlockPos(-2, 0, 2), new BlockPos(-2, 0, -2)};
    /** 柱の位置（斜めの枠の上に3段）。 */
    public static final BlockPos[] PYLONS;
    static {
        PYLONS = new BlockPos[12];
        int i = 0;
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                for (int y = 1; y <= 3; y++) {
                    PYLONS[i++] = new BlockPos(dx, y, dz);
                }
            }
        }
    }
    /** 宇宙が生まれる場所（核の上）。 */
    public static final double LENS_Y = 5.2;

    public static final int STABILIZE_TICKS = 4800;
    public static final int BIRTH_TICKS = 600;
    /** 誕生の演出の節目: 光が集まって崩れ落ちる・静寂・閃光・膨張・銀河が落ち着く・夜空へ・エピローグ。 */
    public static final int BIRTH_COLLAPSE = 100, BIRTH_BANG = 120, BIRTH_EXPAND = 400, BIRTH_SKY = 420, BIRTH_ENDING = 520;
    /** 暴走の兆し: 警報の長さと、暴走している長さ。 */
    public static final int SURGE_WARN = 80, SURGE_ACTIVE = 200;
    static final int CHECK_INTERVAL = 40;
    static final int SYNC_INTERVAL = 10;

    private int stage = UNFORMED;
    private boolean formed;
    private long nextCheck;
    /** 受けた電力の蓄え（FE）。 */
    private long buffer;
    private long receivedThisTick;
    private long receivedTick = -1;
    /** 直近の受電の速さ（FE/t、ならした値）。 */
    private double intake;
    /** 種火に注いだ電力（FE）。 */
    private double charge;
    /** 安定化の進み（tick）。 */
    private double progress;
    /** 不安定度（0〜100）。 */
    private float instability;
    private int surgeTicks;
    private int nextSurge = 700;
    private int nextEvent = 500;
    private int slamTicks;
    private boolean manualStop;
    private boolean feeding = true;
    private int birthTicks;
    private long galaxySeed;
    /** 質量生成: 作る物（1個）・注いだ電力・出来上がった物。 */
    private ItemStack template = ItemStack.EMPTY;
    private double synthProgress;
    private ItemStack output = ItemStack.EMPTY;
    private boolean dirtySync;
    private int syncTimer;
    private int clientTicks;

    private final Energy energy = new Energy();
    private final Output outputHandler = new Output();

    public GenesisCoreBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.GENESIS_CORE.get(), pos, state);
    }

    // ------------------------------------------------------------------ 設定

    public static long seedEnergy() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.GENESIS_SEED_ENERGY.get() : 1_500_000_000_000L;
    }

    public static long maxInput() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.GENESIS_MAX_INPUT.get() : 1_000_000_000L;
    }

    public static long upkeep() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.GENESIS_UPKEEP.get() : 100_000_000L;
    }

    static boolean endingScreen() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.GENESIS_ENDING_SCREEN.get();
    }

    // ------------------------------------------------------------------ 読み取り（描画にも使う）

    public int stage() {
        return stage;
    }

    public boolean formed() {
        return formed;
    }

    public float chargeFraction() {
        return (float) Math.min(1, charge / Math.max(1, seedEnergy()));
    }

    public float progressFraction() {
        return (float) Math.min(1, progress / STABILIZE_TICKS);
    }

    public float instability() {
        return instability;
    }

    /** 暴走の兆しの残り tick（警報 → 暴走）。0 なら静か。 */
    public int surgeTicks() {
        return surgeTicks;
    }

    /** 暴走している最中か（警報の後）。 */
    public boolean surging() {
        return surgeTicks > 0 && surgeTicks <= SURGE_ACTIVE;
    }

    public boolean feeding() {
        return feeding;
    }

    public int birthTicks() {
        return birthTicks;
    }

    public long galaxySeed() {
        return galaxySeed;
    }

    public ItemStack template() {
        return template;
    }

    public ItemStack output() {
        return output;
    }

    public double intake() {
        return intake;
    }

    public float synthFraction() {
        MassSynthesis.Entry e = MassSynthesis.INSTANCE.entry(template);
        return e == null ? 0 : (float) Math.min(1, synthProgress / MassSynthesis.cost(e));
    }

    public Vec3 lens() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, LENS_Y, 0);
    }

    public LongEnergyStorage energy() {
        return energy;
    }

    public IItemHandler items() {
        return outputHandler;
    }

    // ------------------------------------------------------------------ 形

    /** 形ができているか（枠8と柱12）。 */
    public static boolean checkShape(Level level, BlockPos core) {
        Block frame = SinguloBlocks.SIMPLE.get("genesis_frame").get();
        Block pylon = SinguloBlocks.SIMPLE.get("genesis_pylon").get();
        for (BlockPos p : FRAMES) {
            if (!level.getBlockState(core.offset(p)).is(frame)) {
                return false;
            }
        }
        for (BlockPos p : PYLONS) {
            if (!level.getBlockState(core.offset(p)).is(pylon)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 毎 tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, GenesisCoreBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    /** 描き方のための時計（誕生の演出は同期を待たず、毎 tick 進める）。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, GenesisCoreBlockEntity be) {
        be.clientTicks++;
        if (be.stage == BIRTH && be.birthTicks < BIRTH_TICKS) {
            be.birthTicks++;
        }
    }

    /** 描き方の時計（クライアントだけ）。 */
    public int clientTicks() {
        return clientTicks;
    }

    private void tick(ServerLevel level) {
        if (level.getGameTime() >= nextCheck) {
            nextCheck = level.getGameTime() + CHECK_INTERVAL;
            boolean now = checkShape(level, worldPosition);
            if (now != formed) {
                formed = now;
                if (formed && stage == UNFORMED) {
                    stage = CRADLE;
                    level.playSound(null, worldPosition, SinguloSounds.get("genesis.form"), SoundSource.BLOCKS, 2.0F, 1.0F);
                    tellNear(level, "gui.singulo.genesis.formed", 12);
                    SinguloTriggers.milestoneNear(level, worldPosition, 16, "genesis_formed");
                }
                markSync();
            }
        }
        feeding = !manualStop && !level.hasNeighborSignal(worldPosition);
        double movedThisTick = 0;
        switch (stage) {
            case KINDLING -> movedThisTick = tickKindling(level);
            case STABILIZING -> movedThisTick = tickStabilizing(level);
            case BIRTH -> tickBirth(level);
            case COMPLETE -> movedThisTick = tickSynthesis(level);
            default -> { }
        }
        intake = intake * 0.9 + movedThisTick * 0.1;
        if (slamTicks > 0 && --slamTicks == 0) {
            slamNearby(level);
        }
        if (level.getGameTime() % 10 == 0 && (stage == KINDLING || stage == STABILIZING || stage == COMPLETE && !template.isEmpty())) {
            showStatus(level);
        }
        if (stage >= KINDLING && level.getGameTime() % 80 == 0) {
            level.playSound(null, worldPosition.above(5), SinguloSounds.get("genesis.ambient"), SoundSource.BLOCKS, 1.5F,
                    stage == STABILIZING ? 0.8F : 1.0F);
        }
        if (dirtySync || ++syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            dirtySync = false;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void markSync() {
        dirtySync = true;
    }

    /** 種火: 蓄えをそのまま注ぐ。 */
    private double tickKindling(ServerLevel level) {
        if (!formed || !feeding) {
            return 0;
        }
        long moved = buffer;
        buffer = 0;
        charge += moved;
        if (charge >= seedEnergy()) {
            charge = seedEnergy();
            stage = STABILIZING;
            instability = 0;
            nextSurge = 600;
            level.playSound(null, worldPosition.above(5), SinguloSounds.get("genesis.ignite"), SoundSource.BLOCKS, 3.0F, 0.9F);
            tellNear(level, "gui.singulo.genesis.kindled", 48);
            markSync();
        }
        return moved;
    }

    /** 安定化: 維持の電力を受けて進む。暴走の兆し・事件・崩落。 */
    private double tickStabilizing(ServerLevel level) {
        if (!formed) {
            return 0;
        }
        boolean shield = ShieldTowerBlockEntity.shielded(level, lens(), 1);
        long need = upkeep();
        long used = Math.min(buffer, need);
        buffer -= used;
        boolean powered = used >= need;
        // 暴走の兆し
        if (surgeTicks > 0) {
            surgeTicks--;
            if (surgeTicks <= SURGE_ACTIVE) {
                instability += feeding ? (shield ? 0.18F : 0.3F) : -0.25F;
                if (surgeTicks % 20 == 0) {
                    level.playSound(null, worldPosition.above(5), SinguloSounds.get("genesis.surge"), SoundSource.BLOCKS, 2.5F, feeding ? 1.2F : 0.8F);
                }
            }
            if (surgeTicks == 0) {
                tellNear(level, "gui.singulo.genesis.surge_end", 48);
                markSync();
            }
        } else {
            instability += feeding ? 0.008F : -0.05F;
            if (--nextSurge <= 0) {
                surgeTicks = SURGE_WARN + SURGE_ACTIVE;
                nextSurge = 600 + level.random.nextInt(300);
                level.playSound(null, worldPosition.above(5), SinguloSounds.get("genesis.alarm"), SoundSource.BLOCKS, 3.0F, 1.0F);
                tellNear(level, "gui.singulo.genesis.surge", 48);
                markSync();
            }
        }
        instability = Math.max(0, Math.min(100, instability));
        if (instability >= 100) {
            collapse(level);
            return used;
        }
        // 進み（投入していて、維持の電力が足りているときだけ）
        if (feeding && powered) {
            progress += TimeFields.state(level, worldPosition.above(5)) > 0 ? 1.5 : 1.0;
        }
        if (--nextEvent <= 0) {
            nextEvent = (shield ? 900 : 600) + level.random.nextInt(300);
            randomEvent(level);
        }
        if (progress >= STABILIZE_TICKS) {
            progress = STABILIZE_TICKS;
            stage = BIRTH;
            birthTicks = 0;
            surgeTicks = 0;
            galaxySeed = worldPosition.asLong() * 31 + level.getGameTime();
            markSync();
        }
        return used;
    }

    /** 崩落: 不安定度が 100% になると、生まれかけの宇宙がつぶれかけ、進みが戻る（地形は壊さない）。 */
    private void collapse(ServerLevel level) {
        progress = Math.max(0, progress - STABILIZE_TICKS * 0.25);
        instability = 40;
        surgeTicks = 0;
        nextSurge = 500;
        Vec3 c = lens();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 200, 3, 3, 3, 1.2);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0);
        level.playSound(null, worldPosition.above(5), SinguloSounds.get("genesis.collapse"), SoundSource.BLOCKS, 4.0F, 0.6F);
        for (Player p : players(level, 16)) {
            p.hurt(SinguloDamageTypes.tidal(level, null), 8);
            Vec3 push = p.position().subtract(c).multiply(1, 0, 1);
            if (push.lengthSqr() > 1e-4) {
                p.push(push.normalize().x * 1.4, 0.6, push.normalize().z * 1.4);
                p.hurtMarked = true;
            }
        }
        for (int i = 0; i < 3; i++) {
            spawnLeak(level);
        }
        tellNear(level, "gui.singulo.genesis.collapse", 48);
        markSync();
    }

    /** 事件: 重力の漏れ・記録の残響・引力の波のどれか。 */
    private void randomEvent(ServerLevel level) {
        List<Player> players = players(level, 24);
        if (players.isEmpty()) {
            return;
        }
        switch (level.random.nextInt(3)) {
            case 0 -> {
                spawnLeak(level);
                spawnLeak(level);
                tellNear(level, "gui.singulo.genesis.event.leak", 48);
            }
            case 1 -> {
                for (int i = 0; i < 3; i++) {
                    Player target = players.get(level.random.nextInt(players.size()));
                    EchoSentinel echo = SinguloEntities.ECHO_SENTINEL.get().create(level);
                    if (echo == null) {
                        continue;
                    }
                    echo.makeMinion();
                    double a = level.random.nextDouble() * Math.PI * 2;
                    echo.moveTo(target.getX() + Math.cos(a) * 6, target.getY() + 2, target.getZ() + Math.sin(a) * 6, 0, 0);
                    echo.setTarget(target);
                    level.addFreshEntity(echo);
                    level.sendParticles(ParticleTypes.FLASH, echo.getX(), echo.getY() + 1.2, echo.getZ(), 1, 0, 0, 0, 0);
                }
                level.playSound(null, worldPosition.above(5), SinguloSounds.get("echo_sentinel.summon"), SoundSource.HOSTILE, 2.5F, 0.8F);
                tellNear(level, "gui.singulo.genesis.event.echoes", 48);
            }
            default -> {
                for (Player p : players) {
                    p.setDeltaMovement(p.getDeltaMovement().x * 0.3, 1.1, p.getDeltaMovement().z * 0.3);
                    p.hurtMarked = true;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 0.5, p.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
                }
                slamTicks = 20;
                level.playSound(null, worldPosition.above(5), SinguloSounds.get("gravity_remnant.lift"), SoundSource.HOSTILE, 2.5F, 0.8F);
                tellNear(level, "gui.singulo.genesis.event.wave", 48);
            }
        }
    }

    private void spawnLeak(ServerLevel level) {
        double a = level.random.nextDouble() * Math.PI * 2;
        double r = 4 + level.random.nextDouble() * 4;
        Vec3 at = Vec3.atBottomCenterOf(worldPosition).add(Math.cos(a) * r, 1.2, Math.sin(a) * r);
        level.addFreshEntity(new WardenSingularity(level, null, at));
    }

    private void slamNearby(ServerLevel level) {
        for (Player p : players(level, 26)) {
            if (!p.onGround()) {
                p.setDeltaMovement(p.getDeltaMovement().x, -2.6, p.getDeltaMovement().z);
                p.fallDistance = Math.max(p.fallDistance, 6);
                p.hurtMarked = true;
            }
        }
        level.playSound(null, worldPosition.above(5), SinguloSounds.get("gravity_remnant.slam"), SoundSource.HOSTILE, 2.5F, 0.8F);
    }

    /**
     * 誕生の演出（サーバー側: 音・粒・言葉・夜空・記録・エピローグ）。光の描き方は {@code GenesisCoreRenderer}。
     * 光が集まって一点に崩れ落ち（〜{@value #BIRTH_COLLAPSE}）、静寂のあと閃光（{@value #BIRTH_BANG}）、宇宙が膨らんで
     * 銀河が生まれ（〜{@value #BIRTH_EXPAND}）、夜空へ昇る（{@value #BIRTH_SKY}）。
     */
    private void tickBirth(ServerLevel level) {
        int t = ++birthTicks;
        Vec3 c = lens();
        if (t == 1) {
            level.playSound(null, worldPosition.above(5), SinguloSounds.get("genesis.birth_collapse"), SoundSource.BLOCKS, 4.0F, 0.7F);
            tellNear(level, "gui.singulo.genesis.birth_1", 64);
            for (ServerPlayer p : serverPlayers(level, 48)) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BIRTH_BANG + 20, 0, false, false));
            }
        }
        if (t < BIRTH_COLLAPSE) {
            for (int i = 0; i < 8; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double b = (level.random.nextDouble() - 0.5) * Math.PI;
                double r = 6 + level.random.nextDouble() * 10;
                Vec3 d = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                level.sendParticles(ParticleTypes.END_ROD, c.x + d.x * r, c.y + d.y * r, c.z + d.z * r, 0, -d.x, -d.y, -d.z, 0.8);
            }
        }
        if (t == BIRTH_BANG) {
            for (ServerPlayer p : serverPlayers(level, 96)) {
                // 見ている人の目の前で閃光（画面いっぱいの光）
                Vec3 eye = p.getEyePosition().add(p.getLookAngle().scale(1.5));
                level.sendParticles(p, ParticleTypes.FLASH, true, eye.x, eye.y, eye.z, 4, 0.2, 0.2, 0.2, 0);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 0.5, 0.5, 0.5, 0);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 400, 0.2, 0.2, 0.2, 1.2);
            level.playSound(null, worldPosition.above(5), SinguloSounds.get("genesis.big_bang"), SoundSource.BLOCKS, 6.0F, 0.8F);
            for (Player p : players(level, 24)) {
                Vec3 push = p.position().subtract(c).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) {
                    p.push(push.normalize().x * 0.9, 0.4, push.normalize().z * 0.9);
                    p.hurtMarked = true;
                }
            }
        }
        if (t > BIRTH_BANG && t < BIRTH_EXPAND && t % 2 == 0) {
            // 渦の腕に沿って星がこぼれる
            double k = (t - BIRTH_BANG) / (double) (BIRTH_EXPAND - BIRTH_BANG);
            for (int arm = 0; arm < 3; arm++) {
                double a = t * 0.08 + arm * Math.PI * 2 / 3 + k * 4;
                double r = 1 + 7 * k;
                level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + (level.random.nextDouble() - 0.5) * 0.5,
                        c.z + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
        if (t == 200) {
            tellNear(level, "gui.singulo.genesis.birth_2", 64);
        }
        if (t == 300) {
            tellNear(level, "gui.singulo.genesis.birth_3", 64);
        }
        if (t == BIRTH_SKY) {
            GenesisGalaxies.add(level.getServer(), galaxySeed);
            level.playSound(null, worldPosition.above(5), SinguloSounds.get("genesis.sky"), SoundSource.BLOCKS, 3.0F, 1.0F);
            tellNear(level, "gui.singulo.genesis.birth_sky", 64);
            SinguloTriggers.milestoneNear(level, worldPosition, 64, "genesis_birth");
            for (ServerPlayer p : serverPlayers(level, 64)) {
                AncientRecords.learn(p, "x02");
                if (AncientRecords.decoded(p).contains("x01")) {
                    AncientRecords.learn(p, "x03");
                }
            }
        }
        if (t == BIRTH_ENDING && endingScreen()) {
            for (ServerPlayer p : serverPlayers(level, 64)) {
                if (p.connection != null && p.connection.hasChannel(EndingPayload.TYPE)) {
                    PacketDistributor.sendToPlayer(p, new EndingPayload(AncientRecords.decoded(p).contains("x01")));
                }
            }
        }
        if (t >= BIRTH_TICKS) {
            stage = COMPLETE;
            tellNear(level, "gui.singulo.genesis.complete", 64);
            markSync();
        }
    }

    /** 質量生成: 蓄えを注ぎ、作る物の電力に届いたら1個できる。 */
    private double tickSynthesis(ServerLevel level) {
        MassSynthesis.Entry e = MassSynthesis.INSTANCE.entry(template);
        if (!formed || !feeding || e == null) {
            return 0;
        }
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, template) || output.getCount() >= output.getMaxStackSize())) {
            return 0;
        }
        long moved = buffer;
        buffer = 0;
        synthProgress += moved;
        double cost = MassSynthesis.cost(e);
        if (synthProgress >= cost) {
            synthProgress -= cost;
            if (output.isEmpty()) {
                output = template.copyWithCount(1);
            } else {
                output.grow(1);
            }
            Vec3 c = lens();
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 2, c.z, 12 + e.tier() * 10, 0.2, 0.2, 0.2, 0.05 + e.tier() * 0.05);
            level.playSound(null, worldPosition.above(6), SinguloSounds.get("genesis.synth"), SoundSource.BLOCKS, 1.0F + e.tier() * 0.3F,
                    1.4F - e.tier() * 0.15F);
            if (e.tier() >= MassSynthesis.MAX_TIER) {
                SinguloTriggers.milestoneNear(level, worldPosition, 16, "synth_legendary");
            }
            markSync();
        }
        return moved;
    }

    // ------------------------------------------------------------------ 使う

    /** 手に持った物を使ったとき。true なら使い終わった（物を減らすかどうかはここで決める）。 */
    public boolean useItem(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ServerLevel level = (ServerLevel) getLevel();
        if (stage == CRADLE && stack.is(primordialSeed())) {
            if (!formed) {
                tell(player, "gui.singulo.genesis.unformed");
                return true;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            stage = KINDLING;
            charge = 0;
            level.playSound(null, worldPosition.above(5), SinguloSounds.get("genesis.seed"), SoundSource.BLOCKS, 2.5F, 1.0F);
            tellNear(level, "gui.singulo.genesis.seeded", 32);
            markSync();
            return true;
        }
        if (stage == COMPLETE) {
            chooseTemplate(player, stack);
            return true;
        }
        return false;
    }

    /** 作る物を決める（核に使う・画面の枠に置く）。表にない物は断る。持っている物は減らない。 */
    public boolean chooseTemplate(ServerPlayer player, ItemStack stack) {
        if (stage != COMPLETE || stack.isEmpty()) {
            return false;
        }
        MassSynthesis.Entry e = MassSynthesis.INSTANCE.entry(stack);
        if (e == null) {
            tell(player, "gui.singulo.genesis.not_synthesizable");
            return false;
        }
        // 作るのはその物の素の形（エンチャント・名前・損傷・中身などは写さない）。見本の付加データまで電力で複製しないように
        ItemStack plain = new ItemStack(stack.getItem());
        if (!ItemStack.isSameItemSameComponents(template, plain)) {
            template = plain;
            synthProgress = 0;
        }
        player.displayClientMessage(Component.translatable("gui.singulo.genesis.template", stack.getHoverName(),
                formatFe(MassSynthesis.cost(e)), e.tier()), true);
        markSync();
        return true;
    }

    /** 作る物を外す（画面の枠を素手でクリック）。 */
    public void clearTemplate() {
        if (!template.isEmpty()) {
            template = ItemStack.EMPTY;
            synthProgress = 0;
            markSync();
        }
    }

    /** 画面の「作る物」の枠。見せるだけで、出し入れはメニューのクリック処理で扱う。 */
    public net.minecraft.world.Container templateContainer() {
        return new net.minecraft.world.SimpleContainer(1) {
            @Override
            public ItemStack getItem(int slot) {
                return template;
            }

            @Override
            public void setItem(int slot, ItemStack stack) {
            }
        };
    }

    // ------------------------------------------------------------------ 宇宙の記憶（壊しても残る）

    /** 宇宙を生んだ炉か（壊すと、その記憶を核のアイテムに宿す）。 */
    public boolean remembersUniverse() {
        return stage == COMPLETE;
    }

    /** 壊したときに落とす核: 完成した炉なら、宇宙の記憶（段階・銀河・作る物）を宿して光る。置き直すとそのまま質量生成を続けられる。 */
    public ItemStack memoryItem(ItemStack drop) {
        if (!remembersUniverse()) {
            return drop;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("stage", COMPLETE);
        tag.putLong("galaxy", galaxySeed);
        if (!template.isEmpty() && level != null) {
            tag.put("template", template.save(level.registryAccess()));
        }
        net.minecraft.world.item.BlockItem.setBlockEntityData(drop, SinguloBlockEntities.GENESIS_CORE.get(), tag);
        drop.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return drop;
    }

    /** 宇宙の記憶を宿した核のアイテムか（説明に使う）。 */
    public static boolean remembersUniverse(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        return data != null && data.copyTag().getInt("stage") == COMPLETE;
    }

    /** クライアント側の見込み（腕を振るかどうか）。 */
    public boolean wouldUseItem(ItemStack stack) {
        return !stack.isEmpty() && (stage == CRADLE && stack.is(primordialSeed()) || stage == COMPLETE);
    }

    /** 素手で使ったとき: 創世炉の画面を開く（段階・進み・不安定度・投入の切り替え・出来上がった物の受け取り）。 */
    public void useEmpty(ServerPlayer player) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, p) -> new GenesisCoreMenu(id, inventory, worldPosition, outputContainer(), templateContainer()),
                Component.translatable("multiblock.singulo.genesis_engine")), buf -> buf.writeBlockPos(worldPosition));
    }

    /** 投入を止められる段階か（種火・安定化・質量生成）。 */
    public boolean canToggleFeed() {
        return stage == KINDLING || stage == STABILIZING || stage == COMPLETE;
    }

    /** 投入を止める／再開する（画面のスイッチ）。赤石信号が入っている間は、再開しても止まったまま。 */
    public void toggleFeed(ServerPlayer player) {
        if (!canToggleFeed()) {
            return;
        }
        ServerLevel level = (ServerLevel) getLevel();
        manualStop = !manualStop;
        feeding = !manualStop && !level.hasNeighborSignal(worldPosition);
        level.playSound(null, worldPosition, SinguloSounds.get("genesis.lever"), SoundSource.BLOCKS, 1.2F, manualStop ? 0.7F : 1.2F);
        tell(player, manualStop ? "gui.singulo.genesis.stopped" : "gui.singulo.genesis.resumed");
        markSync();
    }

    /** 手で投入を止めているか（false で feeding も false なら、赤石信号で止まっている）。 */
    public boolean manualStop() {
        return manualStop;
    }

    /** 画面の「出来上がり」スロット。取り出すだけで、入れることはできない。 */
    public net.minecraft.world.Container outputContainer() {
        return new net.minecraft.world.Container() {
            @Override
            public int getContainerSize() {
                return 1;
            }

            @Override
            public boolean isEmpty() {
                return output.isEmpty();
            }

            @Override
            public ItemStack getItem(int slot) {
                return output;
            }

            @Override
            public ItemStack removeItem(int slot, int amount) {
                return outputHandler.extractItem(0, amount, false);
            }

            @Override
            public ItemStack removeItemNoUpdate(int slot) {
                return outputHandler.extractItem(0, output.getCount(), false);
            }

            @Override
            public void setItem(int slot, ItemStack stack) {
                output = stack.isEmpty() ? ItemStack.EMPTY : stack;
                markSync();
            }

            @Override
            public void setChanged() {
                markSync();
            }

            @Override
            public boolean stillValid(Player player) {
                return !isRemoved() && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64;
            }

            @Override
            public void clearContent() {
                output = ItemStack.EMPTY;
                markSync();
            }
        };
    }

    /** 近くの人の画面下に、今の様子を出す。 */
    private void showStatus(ServerLevel level) {
        Component msg = switch (stage) {
            case KINDLING -> Component.translatable("gui.singulo.genesis.bar.kindling", percent(chargeFraction()), formatFe(intake));
            case STABILIZING -> surgeTicks > 0
                    ? Component.translatable(feeding ? "gui.singulo.genesis.bar.surge" : "gui.singulo.genesis.bar.surge_held",
                    percent(instability / 100F)).withStyle(feeding ? ChatFormatting.RED : ChatFormatting.AQUA)
                    : Component.translatable("gui.singulo.genesis.bar.stabilizing", percent(progressFraction()), percent(instability / 100F));
            default -> Component.translatable("gui.singulo.genesis.bar.synth", template.getHoverName(), percent(synthFraction()),
                    output.getCount());
        };
        if (!feeding && !(stage == STABILIZING && surgeTicks > 0)) {
            msg = Component.translatable("gui.singulo.genesis.bar.paused", msg);
        }
        for (ServerPlayer p : serverPlayers(level, 14)) {
            p.displayClientMessage(msg, true);
        }
    }

    // ------------------------------------------------------------------ 道具

    public static Item primordialSeed() {
        return BuiltInRegistries.ITEM.get(Singulo.id("primordial_seed"));
    }

    static String percent(float f) {
        return String.format("%.0f%%", f * 100);
    }

    /** FE を読みやすく（k/M/G/T）。 */
    public static String formatFe(double fe) {
        String[] units = {"", "k", "M", "G", "T", "P"};
        int u = 0;
        while (fe >= 1000 && u < units.length - 1) {
            fe /= 1000;
            u++;
        }
        return String.format(fe >= 100 ? "%.0f %sFE" : "%.1f %sFE", fe, units[u]);
    }

    private List<Player> players(ServerLevel level, double r) {
        return level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(r),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
    }

    private List<ServerPlayer> serverPlayers(ServerLevel level, double r) {
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(r));
    }

    private void tellNear(ServerLevel level, String key, double r) {
        Component msg = Component.translatable(key).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : serverPlayers(level, r)) {
            p.displayClientMessage(msg, false);
        }
    }

    private static void tell(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    // ------------------------------------------------------------------ 電力と取り出し口

    /** 電力の口。種火・安定化・質量生成のときだけ、genesisMaxInput まで受け取る（投入を止めている間は受け取らない）。 */
    private final class Energy implements LongEnergyStorage {
        /** 蓄えの大きさ（1 tick の上限の4倍。とても大きな設定でも long をあふれない）。 */
        private long capacity() {
            long max = maxInput();
            return max > Long.MAX_VALUE / 4 ? Long.MAX_VALUE : max * 4;
        }

        private boolean accepting() {
            return formed && feeding && (stage == KINDLING || stage == STABILIZING || stage == COMPLETE);
        }

        @Override
        public long receiveLong(long amount, boolean simulate) {
            if (!accepting() || amount <= 0 || level == null) {
                return 0;
            }
            long now = level.getGameTime();
            if (now != receivedTick) {
                receivedTick = now;
                receivedThisTick = 0;
            }
            long room = Math.min(maxInput() - receivedThisTick, capacity() - buffer);
            long accepted = Math.max(0, Math.min(amount, room));
            if (!simulate && accepted > 0) {
                buffer += accepted;
                receivedThisTick += accepted;
            }
            return accepted;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return (int) receiveLong(amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, buffer);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, capacity());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return accepting();
        }
    }

    /** 出来上がった物の取り出し口（取り出すだけ）。 */
    private final class Output implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return output;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (output.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack out = output.copyWithCount(Math.min(amount, output.getCount()));
            if (!simulate) {
                output.shrink(out.getCount());
                if (output.isEmpty()) {
                    output = ItemStack.EMPTY;
                }
                markSync();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 保存と同期

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stage", stage);
        tag.putBoolean("formed", formed);
        tag.putLong("buffer", buffer);
        tag.putDouble("intake", intake);
        tag.putDouble("charge", charge);
        tag.putDouble("progress", progress);
        tag.putFloat("instability", instability);
        tag.putInt("surge", surgeTicks);
        tag.putInt("next_surge", nextSurge);
        tag.putInt("next_event", nextEvent);
        tag.putBoolean("manual_stop", manualStop);
        tag.putBoolean("feeding", feeding);
        tag.putInt("birth", birthTicks);
        tag.putLong("galaxy", galaxySeed);
        tag.putDouble("synth", synthProgress);
        if (!template.isEmpty()) {
            tag.put("template", template.save(registries));
        }
        if (!output.isEmpty()) {
            tag.put("output", output.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stage = tag.getInt("stage");
        formed = tag.getBoolean("formed");
        buffer = tag.getLong("buffer");
        intake = tag.getDouble("intake");
        charge = tag.getDouble("charge");
        progress = tag.getDouble("progress");
        instability = tag.getFloat("instability");
        surgeTicks = tag.getInt("surge");
        nextSurge = tag.contains("next_surge") ? tag.getInt("next_surge") : 700;
        nextEvent = tag.contains("next_event") ? tag.getInt("next_event") : 500;
        manualStop = tag.getBoolean("manual_stop");
        feeding = !tag.contains("feeding") || tag.getBoolean("feeding");
        birthTicks = tag.getInt("birth");
        galaxySeed = tag.getLong("galaxy");
        synthProgress = tag.getDouble("synth");
        template = tag.contains("template") ? ItemStack.parseOptional(registries, tag.getCompound("template")) : ItemStack.EMPTY;
        output = tag.contains("output") ? ItemStack.parseOptional(registries, tag.getCompound("output")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
