package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 番人の封印核。研究棟と封鎖培養施設のボス部屋の床に埋まっていて、壊せない。
 * <ul>
 *   <li>挑戦者が部屋に入ると、近くの封鎖された保管庫の遺構に応じた番人を起こす（研究棟は残響の番人、培養施設は重力の澱）</li>
 *   <li>番人が倒されると保管庫の封鎖を解く。中身が再生すると保管庫はまた封鎖され、次に入ったときに番人が戻ってくる</li>
 *   <li>部屋の仕掛け（残響投影器・封じ込め槽）は最初に覚えておき、番人を起こすたびに元の形へ直す</li>
 * </ul>
 */
public class GuardianCoreBlockEntity extends BlockEntity {
    /** 保管庫を探す広さ。 */
    public static final int VAULT_SEARCH_RADIUS = 16;
    /** 挑戦者に気づく広さ（核からの水平距離）と高さ。 */
    public static final int TRIGGER_RADIUS = 9;
    public static final int TRIGGER_HEIGHT = 8;
    /** 残響投影器を探す広さ。 */
    public static final int PROP_RADIUS = 16;
    /** 封じ込め槽（核の真上の円筒）の半径と高さ。 */
    static final double TANK_RADIUS = 2.6;
    static final int TANK_HEIGHT = 9;
    public static final int MESSAGE_RADIUS = 40;

    @Nullable
    private UUID boss;
    private boolean scanned;
    /** 残響投影器の位置。 */
    private final List<BlockPos> projectors = new ArrayList<>();
    /** 封じ込め槽のブロック（位置と、元の形）。 */
    private final List<BlockPos> tankPos = new ArrayList<>();
    private final List<BlockState> tankState = new ArrayList<>();
    /** 出現の演出で、槽をどこまで壊したか（0: 無傷、1: ひび、2: 砕けた）。 */
    private int tankStage;

    public GuardianCoreBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.GUARDIAN_CORE.get(), pos, state);
    }

    /** 遺構ID → 起こす番人。番人のいない遺構は null。 */
    @Nullable
    public static EntityType<? extends RuinBoss> bossFor(String ruin) {
        return switch (ruin) {
            case "research_building" -> SinguloEntities.ECHO_SENTINEL.get();
            case "culture_facility" -> SinguloEntities.GRAVITY_REMNANT.get();
            default -> null;
        };
    }

    public List<BlockPos> projectors() {
        return projectors;
    }

    public List<BlockPos> tank() {
        return tankPos;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GuardianCoreBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (!scanned) {
            scan(level);
        }
        RuinBoss active = activeBoss(level);
        if (active != null) {
            if (active instanceof GravityRemnant && active.emergeTicks() > 0) {
                shatterTank(level, active.emergeProgress(0));
            }
            return;
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        AABB room = new AABB(worldPosition).inflate(TRIGGER_RADIUS, 0, TRIGGER_RADIUS).expandTowards(0, TRIGGER_HEIGHT, 0);
        if (!level.getEntitiesOfClass(ServerPlayer.class, room, RuinBoss::challenger).isEmpty()) {
            awaken(level);
        }
    }

    /** 部屋の仕掛けを覚える（最初の1回だけ）。 */
    public void scan(ServerLevel level) {
        scanned = true;
        projectors.clear();
        tankPos.clear();
        tankState.clear();
        int r = PROP_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-r, -2, -r), worldPosition.offset(r, 10, r))) {
            if (level.getBlockState(p).is(SinguloBlocks.ECHO_PROJECTOR.get())) {
                projectors.add(p.immutable());
            }
        }
        int t = (int) Math.ceil(TANK_RADIUS);
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-t, 1, -t), worldPosition.offset(t, TANK_HEIGHT, t))) {
            double dx = p.getX() - worldPosition.getX();
            double dz = p.getZ() - worldPosition.getZ();
            BlockState s = level.getBlockState(p);
            if (dx * dx + dz * dz <= TANK_RADIUS * TANK_RADIUS && glassLike(s)) {
                tankPos.add(p.immutable());
                tankState.add(s);
            }
        }
        setChanged();
    }

    private static boolean glassLike(BlockState s) {
        return s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || s.is(SinguloBlocks.INTACT_RUIN_GLASS.get())
                || s.is(SinguloBlocks.RUIN_GLASS.get());
    }

    /** いちばん近い、番人に守られた保管庫。 */
    @Nullable
    public RuinCacheBlockEntity vault(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = VAULT_SEARCH_RADIUS;
        RuinCacheBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -4; dy <= 8; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < bestDist && level.getBlockEntity(p) instanceof RuinCacheBlockEntity cache && cache.guarded()
                            && bossFor(cache.ruin()) != null) {
                        best = cache;
                        bestDist = d;
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    public RuinBoss activeBoss(ServerLevel level) {
        if (boss == null) {
            return null;
        }
        Entity e = level.getEntity(boss);
        return e instanceof RuinBoss b && b.isAlive() ? b : null;
    }

    /** 番人を起こす（保管庫が封鎖されていて、まだ起きていないときだけ）。起こしたらそれを返す。 */
    @Nullable
    public RuinBoss awaken(ServerLevel level) {
        if (activeBoss(level) != null) {
            return null;
        }
        RuinCacheBlockEntity vault = vault(level);
        if (vault == null) {
            return null;
        }
        vault.refillIfDue(level, level.getGameTime());
        if (!vault.isSealed()) {
            return null;
        }
        EntityType<? extends RuinBoss> type = bossFor(vault.ruin());
        RuinBoss b = type == null ? null : type.create(level);
        if (b == null) {
            return null;
        }
        if (!scanned) {
            scan(level);
        }
        restoreProps(level);
        tankStage = 0;
        b.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0 + b.spawnHeight(), worldPosition.getZ() + 0.5, 0, 0);
        b.awaken(worldPosition);
        if (b instanceof EchoSentinel s) {
            s.setProjectors(projectors);
        }
        b.startEmerging();
        level.addFreshEntity(b);
        boss = b.getUUID();
        setChanged();
        level.playSound(null, worldPosition, io.github.genichimaruo.singulo.registry.SinguloSounds.get("guardian_core.awaken"), SoundSource.HOSTILE, 2.0F, 1.0F);
        broadcast(level, "gui.singulo.guardian.awakened." + typeName(type));
        return b;
    }

    /** 部屋の仕掛けを元の形へ直す。 */
    public void restoreProps(ServerLevel level) {
        BlockState projector = SinguloBlocks.ECHO_PROJECTOR.get().defaultBlockState();
        for (BlockPos p : projectors) {
            if (!level.getBlockState(p).is(projector.getBlock())) {
                level.setBlock(p, projector, Block.UPDATE_ALL);
            }
        }
        for (int i = 0; i < tankPos.size(); i++) {
            if (level.getBlockState(tankPos.get(i)) != tankState.get(i)) {
                level.setBlock(tankPos.get(i), tankState.get(i), Block.UPDATE_ALL);
            }
        }
    }

    /** 重力の澱の出現: 35% で槽のガラスにひびが走り、75% で砕け散る。 */
    private void shatterTank(ServerLevel level, float progress) {
        if (tankStage == 0 && progress >= 0.35F) {
            tankStage = 1;
            BlockState cracked = SinguloBlocks.RUIN_GLASS.get().defaultBlockState();
            for (BlockPos p : tankPos) {
                if (!level.getBlockState(p).isAir()) {
                    level.setBlock(p, cracked, Block.UPDATE_ALL);
                }
            }
            level.playSound(null, worldPosition.above(4), io.github.genichimaruo.singulo.registry.SinguloSounds.get("containment_tank.crack"), SoundSource.HOSTILE, 2.0F, 1.0F);
        } else if (tankStage == 1 && progress >= 0.75F) {
            tankStage = 2;
            for (BlockPos p : tankPos) {
                if (!level.getBlockState(p).isAir()) {
                    level.destroyBlock(p, false);
                }
            }
            level.playSound(null, worldPosition.above(4), io.github.genichimaruo.singulo.registry.SinguloSounds.get("containment_tank.shatter"), SoundSource.HOSTILE, 3.0F, 1.0F);
        }
    }

    void onBossDefeated(ServerLevel level) {
        EntityType<?> type = bossType(level);
        boss = null;
        setChanged();
        RuinCacheBlockEntity vault = vault(level);
        if (vault != null) {
            vault.setSealed(false);
            level.playSound(null, vault.getBlockPos(), io.github.genichimaruo.singulo.registry.SinguloSounds.get("vault.unseal"), SoundSource.BLOCKS, 1.5F, 1.0F);
        }
        broadcast(level, "gui.singulo.guardian.defeated." + typeName(type));
    }

    void onBossReset(ServerLevel level) {
        EntityType<?> type = bossType(level);
        boss = null;
        setChanged();
        restoreProps(level);
        broadcast(level, "gui.singulo.guardian.reset." + typeName(type));
    }

    @Nullable
    private EntityType<?> bossType(ServerLevel level) {
        RuinBoss b = activeBoss(level);
        if (b != null) {
            return b.getType();
        }
        RuinCacheBlockEntity vault = vault(level);
        return vault == null ? null : bossFor(vault.ruin());
    }

    private static String typeName(@Nullable EntityType<?> type) {
        if (type == null) {
            return "echo_sentinel";
        }
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key.getPath();
    }

    private void broadcast(ServerLevel level, String key) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(MESSAGE_RADIUS))) {
            p.displayClientMessage(Component.translatable(key), false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boss != null) {
            tag.putUUID("boss", boss);
        }
        tag.putBoolean("scanned", scanned);
        ListTag props = new ListTag();
        for (BlockPos p : projectors) {
            props.add(NbtUtils.writeBlockPos(p));
        }
        tag.put("projectors", props);
        ListTag tank = new ListTag();
        for (int i = 0; i < tankPos.size(); i++) {
            CompoundTag t = new CompoundTag();
            t.put("pos", NbtUtils.writeBlockPos(tankPos.get(i)));
            t.put("state", NbtUtils.writeBlockState(tankState.get(i)));
            tank.add(t);
        }
        tag.put("tank", tank);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boss = tag.hasUUID("boss") ? tag.getUUID("boss") : null;
        scanned = tag.getBoolean("scanned");
        projectors.clear();
        for (Tag t : tag.getList("projectors", Tag.TAG_INT_ARRAY)) {
            projectors.add(NbtUtils.readBlockPos(wrap(t), "p").orElse(BlockPos.ZERO));
        }
        tankPos.clear();
        tankState.clear();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag t : tag.getList("tank", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            BlockState s = NbtUtils.readBlockState(blocks, c.getCompound("state"));
            if (s.isAir()) {
                s = Blocks.GLASS.defaultBlockState();
            }
            tankPos.add(NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO));
            tankState.add(s);
        }
    }

    private static CompoundTag wrap(Tag t) {
        CompoundTag c = new CompoundTag();
        c.put("p", t);
        return c;
    }
}
