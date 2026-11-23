package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import io.github.genichimaruo.singulo.ruin.lab.BulkheadBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 遺構の中継器（最終実験施設・沈んだ炉心区画の前室）。施設の中継器をすべて起動すると、ボス部屋の前の隔壁
 * （{@link io.github.genichimaruo.singulo.ruin.lab.BulkheadBlock}）が開く。起動した中継器は戻らないので、攻略後はボス部屋へ直行できる。
 * <p>
 * 種類（構造物の NBT の mode）:
 * <ul>
 *   <li>plain: 近くに遺構の守り手（警備ドローン・遺構のアンドロイド）が残っていなければ、触れると起動する</li>
 *   <li>guarded: 挑戦者が近づくと中ボス（guardian: containment_hauler / hull_repairer）が中継器の上に現れる。倒すと起動できる</li>
 *   <li>extract: 上の渦の穴へ落とした物を呑み込み、重さに応じたエネルギーをためる。{@value #EXTRACT_GOAL} たまると自分で起動する</li>
 *   <li>drain: 起動すると、つながった水（浸水した冷却系統）を抜く</li>
 * </ul>
 */
public class RuinRelayBlockEntity extends io.github.genichimaruo.singulo.compat.LegacyBlockEntity {
    public static final String PLAIN = "plain", GUARDED = "guarded", EXTRACT = "extract", DRAIN = "drain";
    /** 抽出の中継器が起動するのに要るエネルギー（ブロック1個 = 4、ほかのアイテム1個 = 1、落とした敵 = 最大体力×2）。 */
    public static final int EXTRACT_GOAL = 512;
    /** 起動を妨げる守り手を探す距離。 */
    public static final double HOSTILE_RADIUS = 10;
    /** 中ボスを起こす距離と、中ボスがいなくなったと見なすまでの確かめ（1秒ごと）の回数。 */
    public static final double WAKE_RADIUS = 11;
    static final int LOST_CHECKS = 30;
    /** 渦の穴の広さ（中継器の上、高さ {@value #PIT_HEIGHT}、中心から {@value #PIT_RADIUS} ブロック）。 */
    public static final double PIT_RADIUS = 2.5, PIT_HEIGHT = 9;
    /** 抜く水の上限（ブロック）。 */
    static final int DRAIN_LIMIT = 24_000;
    /** 構造物の外（テストや、コマンドで建てたとき）に、同じ施設と見なす広さ。 */
    static final int FALLBACK_RADIUS = 64;
    static final List<ResourceKey<Structure>> FACILITIES = List.of(
            ResourceKey.create(Registries.STRUCTURE, Singulo.id("final_lab")),
            ResourceKey.create(Registries.STRUCTURE, Singulo.id("sunken_reactor")));

    private String mode = PLAIN;
    private String guardian = "";
    private boolean active;
    private boolean guardDefeated;
    @Nullable
    private UUID guardId;
    private int lostChecks;
    private int charge;
    /** 隔壁を開け終えたか（起動済みの中継器が、開け直しをやめる）。 */
    private boolean bulkheadsOpen;
    private boolean draining;
    private final List<BlockPos> stationOffsets = new ArrayList<>();
    @Nullable private BlockPos doorOffset;

    private BlockPos wiredPosition(BlockPos offset) {
        var state = getBlockState();
        Direction forward = state.getValue(RuinRelayBlock.FORWARD);
        Direction right = state.getValue(RuinRelayBlock.MIRRORED) ? forward.getClockWise() : forward.getCounterClockWise();
        return worldPosition.relative(right, offset.getX()).relative(forward, offset.getZ()).above(offset.getY());
    }

    public RuinRelayBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.RUIN_RELAY.get(), pos, state);
    }

    public String mode() {
        return mode;
    }

    public boolean active() {
        return active;
    }

    public int charge() {
        return charge;
    }

    public boolean guardDefeated() {
        return guardDefeated;
    }

    @Nullable
    public UUID guardId() {
        return guardId;
    }

    /** テストや /singulo ruin 以外で置いたときの設定。 */
    public void configure(String mode, String guardian) {
        this.mode = mode;
        this.guardian = guardian == null ? "" : guardian;
        setChanged();
    }

    // ------------------------------------------------------------------ 触れる

    public void use(ServerLevel level, Player player) {
        int[] count = count(level);
        if (active) {
            if (doorOffset != null && count[0] == count[1] && !bulkheadsOpen) openBulkheads(level);
            player.sendOverlayMessage(Component.translatable("gui.singulo.relay.already", count[0], count[1]));
            return;
        }
        if (EXTRACT.equals(mode)) {
            player.sendOverlayMessage(Component.translatable("gui.singulo.relay.extract", charge, EXTRACT_GOAL));
            return;
        }
        if (GUARDED.equals(mode) && !guardDefeated) {
            if (guardAlive(level) == null && !player.isCreative() && !player.isSpectator()) {
                summon(level, player instanceof ServerPlayer sp ? sp : null);
            }
            player.sendOverlayMessage(Component.translatable("gui.singulo.relay.boss"));
            return;
        }
        if (hostilesNear(level)) {
            player.sendOverlayMessage(Component.translatable("gui.singulo.relay.guarded"));
            return;
        }
        activate(level);
    }

    /** 近くに、まだ動いている遺構の守り手（警備ドローン・遺構のアンドロイド・中ボス）がいるか。 */
    public boolean hostilesNear(ServerLevel level) {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(HOSTILE_RADIUS),
                e -> e.isAlive() && RuinGuards.isGuard(e)).isEmpty();
    }

    /** 起動する。水を抜く中継器は水を抜き、施設の中継器がそろえば隔壁を開く。 */
    public void activate(ServerLevel level) {
        if (active) {
            return;
        }
        active = true;
        setChanged();
        BlockState state = getBlockState();
        if (state.hasProperty(RuinRelayBlock.ACTIVE)) {
            level.setBlock(worldPosition, state.setValue(RuinRelayBlock.ACTIVE, true), Block.UPDATE_ALL);
        }
        Vec3 c = Vec3.atCenterOf(worldPosition);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.6, c.z, 30, 0.3, 0.6, 0.3, 0.08);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.4F, 1.2F);
        if (DRAIN.equals(mode) && drainStep(level) > 0) {
            draining = true;                         // 残りは上の段から少しずつ抜ける（serverTick）
            level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 2.0F, 0.6F);
            tell(level, Component.translatable("gui.singulo.relay.drained"));
        }
        int[] count = count(level);
        tell(level, Component.translatable("gui.singulo.relay.activated", count[0], count[1]));
        if (count[0] >= count[1]) {
            if (openBulkheads(level) > 0) {
                tell(level, Component.translatable("gui.singulo.relay.open"));
            }
        }
    }

    private void tell(ServerLevel level, Component message) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(48))) {
            p.sendSystemMessage(message);
        }
    }

    // ------------------------------------------------------------------ 施設

    /** この中継器が属する施設の範囲（遺構の構造物。構造物の外なら中継器の周り）。 */
    public BoundingBox facility(ServerLevel level) {
        BoundingBox inStructure = structureAt(level, worldPosition);
        if (inStructure != null) {
            return inStructure;
        }
        return new BoundingBox(worldPosition.getX() - FALLBACK_RADIUS, worldPosition.getY() - 24, worldPosition.getZ() - FALLBACK_RADIUS,
                worldPosition.getX() + FALLBACK_RADIUS, worldPosition.getY() + 24, worldPosition.getZ() + FALLBACK_RADIUS);
    }

    /** pos を含む、前室のある遺構（生成された構造物）の範囲。構造物の外なら null。 */
    @Nullable
    static BoundingBox structureAt(ServerLevel level, BlockPos pos) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (ResourceKey<Structure> key : FACILITIES) {
            Structure structure = registry.get(key).map(net.minecraft.core.Holder::value).orElse(null);
            if (structure == null) {
                continue;
            }
            StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
            if (start.isValid()) {
                return start.getBoundingBox();
            }
        }
        return null;
    }

    /**
     * pos（ボス部屋の封印コンソール・封印核）が前室のある遺構の中にあり、まだ起動していない中継器があれば
     * [起動した数, 中継器の数]。そうでなければ（構造物の外・前室のない古い遺構・すべて起動済み）null。
     */
    @Nullable
    public static int[] facilityLock(ServerLevel level, BlockPos pos) {
        BoundingBox box = structureAt(level, pos);
        boolean dome = level.getBlockState(pos).is(SinguloBlocks.SEAL_CONSOLE.get());
        boolean closedDoor = PressureRoomSeal.exit(level, pos, dome, Vec3.atCenterOf(pos)) != null;
        if (box == null) {
            return closedDoor ? new int[]{0, dome ? 3 : 4} : null;
        }
        int on = 0, total = 0;
        for (RuinRelayBlockEntity r : relaysIn(level, box)) {
            total++;
            if (r.active) {
                on++;
            }
        }
        if (closedDoor) return new int[]{on, dome ? 3 : 4};
        return total == 0 || on >= total ? null : new int[] {on, total};
    }

    /**
     * core（封印コンソール・封印核）の部屋の隔壁につながった中継器が、すべて起動しているか。隔壁につながった中継器が
     * 見つからなければ false。
     */
    public static boolean relaysComplete(ServerLevel level, BlockPos core) {
        boolean dome = level.getBlockState(core).is(SinguloBlocks.SEAL_CONSOLE.get());
        BlockPos gate = PressureRoomSeal.gate(level, core, dome);
        if (gate == null) {
            return false;
        }
        BoundingBox box = structureAt(level, core);
        if (box == null) {
            box = new BoundingBox(core.getX() - 80, core.getY() - 32, core.getZ() - 80, core.getX() + 80, core.getY() + 32, core.getZ() + 80);
        }
        for (RuinRelayBlockEntity r : relaysIn(level, box)) {
            if (r.doorOffset != null && r.wiredPosition(r.doorOffset).equals(gate)) {
                int[] count = r.count(level);
                return count[0] >= count[1];
            }
        }
        return false;
    }

    /** 前室の中継器がそろっていなければ、近くの挑戦者（player を含む）に知らせて true（ボスを起こさない）。 */
    public static boolean refuseWhileLocked(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        int[] lock = facilityLock(level, pos);
        if (lock == null) {
            return false;
        }
        Component msg = Component.translatable("gui.singulo.relay.locked", lock[0], lock[1]);
        if (player != null) {
            player.sendOverlayMessage(msg);
        }
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(24))) {
            if (p != player) {
                p.sendOverlayMessage(msg);
            }
        }
        return true;
    }

    /** 施設の中継器（読み込まれているチャンクだけ。未読み込みのチャンクは読み込まない）。 */
    public List<RuinRelayBlockEntity> relays(ServerLevel level) {
        return relaysIn(level, facility(level));
    }

    static List<RuinRelayBlockEntity> relaysIn(ServerLevel level, BoundingBox box) {
        List<RuinRelayBlockEntity> out = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof RuinRelayBlockEntity r && box.isInside(be.getBlockPos())) {
                        out.add(r);
                    }
                }
            }
        }
        return out;
    }

    /** 起動した中継器の数と、施設の中継器の数。 */
    public int[] count(ServerLevel level) {
        if (doorOffset != null) {
            int on = 0;
            BlockPos door = wiredPosition(doorOffset);
            for (BlockPos offset : stationOffsets) {
                if (level.getBlockEntity(wiredPosition(offset)) instanceof RuinRelayBlockEntity peer && peer.active
                        && peer.doorOffset != null && peer.wiredPosition(peer.doorOffset).equals(door)) on++;
            }
            return new int[]{on, Math.max(1, stationOffsets.size())};
        }
        List<RuinRelayBlockEntity> all = relays(level);
        int on = 0;
        for (RuinRelayBlockEntity r : all) {
            if (r.active) {
                on++;
            }
        }
        return new int[] {on, Math.max(1, all.size())};
    }

    /** 施設の隔壁をすべて開く。開いた数を返す。 */
    public int openBulkheads(ServerLevel level) {
        if (doorOffset != null) {
            int[] progress = count(level);
            if (progress[0] < progress[1]) return 0;
            Direction across = getBlockState().getValue(RuinRelayBlock.FORWARD).getClockWise();
            BlockPos base = wiredPosition(doorOffset);
            int opened = 0;
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y < 4; y++) {
                    BlockPos p = base.relative(across, x).above(y);
                    BlockState s = level.getBlockState(p);
                    if (s.is(SinguloBlocks.BULKHEAD.get()) && s.getValue(BulkheadBlock.WIRED) && !s.getValue(BulkheadBlock.OPEN)) {
                        level.setBlock(p, s.setValue(BulkheadBlock.OPEN, true), Block.UPDATE_ALL);
                        opened++;
                    }
                }
            }
            bulkheadsOpen = true;
            setChanged();
            if (opened > 0) level.playSound(null, base, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2, 0.5F);
            return opened;
        }
        BoundingBox box = facility(level);
        Block bulkhead = SinguloBlocks.BULKHEAD.get();
        int opened = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(bulkhead))) {
                        continue;
                    }
                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState s = section.getBlockState(x, y, z);
                                if (!s.is(bulkhead) || s.getValue(BulkheadBlock.WIRED) || s.getValue(BulkheadBlock.OPEN)) {
                                    continue;
                                }
                                p.set((cx << 4) + x, baseY + y, (cz << 4) + z);
                                if (box.isInside(p)) {
                                    level.setBlock(p, s.setValue(io.github.genichimaruo.singulo.ruin.lab.BulkheadBlock.OPEN, true), Block.UPDATE_ALL);
                                    opened++;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (opened > 0) {
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, 0.5F);
        }
        return opened;
    }

    /** 水を1段抜く間（tick）。 */
    public static final int DRAIN_INTERVAL = 30;

    /** 排水の途中か（上の段から {@value #DRAIN_INTERVAL} tick ごとに1段ずつ抜けていく）。 */
    public boolean draining() {
        return draining;
    }

    /**
     * つながった水（中継器のまわりから、施設の範囲の中だけ）のうち、いちばん上の段だけを抜く。抜いた数を返す（0 なら抜き終わり）。
     * はしごや中継器のような水没したブロックは、水だけ抜く。
     */
    public int drainStep(ServerLevel level) {
        BoundingBox box = facility(level);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> water = new ArrayList<>();
        queue.add(worldPosition);                   // 水没した中継器そのもの
        for (Direction d : Direction.values()) {
            queue.add(worldPosition.relative(d));
        }
        queue.add(worldPosition.above(2));
        int top = Integer.MIN_VALUE;
        while (!queue.isEmpty() && water.size() < DRAIN_LIMIT) {
            BlockPos p = queue.poll();
            if (!seen.add(p) || !box.isInside(p) || !level.isLoaded(p) || !wet(level.getBlockState(p))) {
                continue;
            }
            water.add(p);
            top = Math.max(top, p.getY());
            for (Direction d : Direction.values()) {
                queue.add(p.relative(d));
            }
        }
        int drained = 0;
        for (BlockPos p : water) {
            if (p.getY() != top) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(p, s.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS);
            } else {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            drained++;
        }
        return drained;
    }

    private static boolean wet(BlockState s) {
        if (s.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return s.getValue(BlockStateProperties.WATERLOGGED);
        }
        return s.is(Blocks.WATER) || s.getFluidState().is(FluidTags.WATER) && s.canBeReplaced();
    }

    /** つながった水をすべて一度に抜く（段ごとに {@link #drainStep} をくり返す）。抜いた数を返す。 */
    public int drain(ServerLevel level) {
        int total = 0;
        for (int step; (step = drainStep(level)) > 0; ) {
            total += step;
        }
        draining = false;
        setChanged();
        return total;
    }

    // ------------------------------------------------------------------ 動き

    public static void serverTick(Level level, BlockPos pos, BlockState state, RuinRelayBlockEntity be) {
        if (be.draining && level.getGameTime() % DRAIN_INTERVAL == 0) {
            ServerLevel server = (ServerLevel) level;
            if (be.drainStep(server) > 0) {
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.6F, 0.5F + level.getRandom().nextFloat() * 0.2F);
            } else {
                be.draining = false;
                level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.4F, 0.5F);
            }
            be.setChanged();
        }
        if (be.active) {
            // 最後の中継器を起動したとき、隔壁のチャンクが読み込まれていなかったときのために、ときどき開け直す
            if (be.doorOffset == null && !be.bulkheadsOpen && level.getGameTime() % 100 == Math.floorMod(pos.asLong(), 100)) {
                ServerLevel server = (ServerLevel) level;
                int[] count = be.count(server);
                if (count[0] >= count[1]) {
                    be.openBulkheads(server);
                    be.bulkheadsOpen = true;
                    be.setChanged();
                }
            }
            return;
        }
        ServerLevel server = (ServerLevel) level;
        if (EXTRACT.equals(be.mode) && level.getGameTime() % 5 == 0) {
            be.tickExtract(server);
        }
        if (GUARDED.equals(be.mode) && !be.guardDefeated && level.getGameTime() % 20 == 0) {
            be.tickGuard(server);
        }
    }

    /** 渦の穴に落ちた物を呑み込み、エネルギーをためる。 */
    public void tickExtract(ServerLevel level) {
        AABB pit = pit();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        int gained = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, pit, ItemEntity::isAlive)) {
            ItemStack stack = item.getItem();
            gained += weight(stack);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, item.getX(), item.getY() + 0.2, item.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            item.discard();
        }
        AABB bottom = new AABB(worldPosition).inflate(PIT_RADIUS, 0, PIT_RADIUS).expandTowards(0, 2, 0);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, bottom, m -> m.isAlive() && swallowable(m))) {
            gained += Math.round(mob.getMaxHealth() * 2);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
            mob.kill(level);
        }
        if (level.getGameTime() % 10 == 0) {
            // 穴の底で回る渦
            for (int i = 0; i < 6; i++) {
                double a = level.getRandom().nextDouble() * Math.PI * 2;
                double r = 0.6 + level.getRandom().nextDouble() * PIT_RADIUS;
                level.sendParticles(ParticleTypes.PORTAL, c.x + Math.cos(a) * r, c.y + 0.8, c.z + Math.sin(a) * r,
                        0, -Math.sin(a), 0.2, Math.cos(a), 0.5);
            }
        }
        if (gained <= 0) {
            return;
        }
        charge = Math.min(EXTRACT_GOAL, charge + gained);
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8F,
                0.6F + 1.0F * charge / EXTRACT_GOAL);
        Component progress = Component.translatable("gui.singulo.relay.extract", charge, EXTRACT_GOAL);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(16))) {
            p.sendOverlayMessage(progress);
        }
        if (charge >= EXTRACT_GOAL) {
            activate(level);
        }
    }

    /** 渦の穴の範囲（中継器の上）。 */
    public AABB pit() {
        return new AABB(worldPosition).inflate(PIT_RADIUS, 0, PIT_RADIUS).expandTowards(0, PIT_HEIGHT, 0);
    }

    /** 落とした物の重さ（エネルギー）。ブロックは 4、ほかは 1（1個あたり）。 */
    public static int weight(ItemStack stack) {
        return stack.getCount() * (stack.getItem() instanceof BlockItem ? 4 : 1);
    }

    /** 渦が呑み込める敵（プレイヤー・ボス・中ボス・護衛のアンドロイドは呑み込まない）。 */
    static boolean swallowable(Mob mob) {
        if (mob instanceof RuinBoss || mob instanceof HorizonWarden || mob.isPersistenceRequired() && mob.hasCustomName()) {
            return false;
        }
        if (mob instanceof io.github.genichimaruo.singulo.android.RuinAndroid a) {
            return !a.isGuard();
        }
        return mob instanceof net.minecraft.world.entity.monster.Enemy;
    }

    /** 挑戦者が近づいたら中ボスを起こし、いなくなったら（倒されずに消えたら）また起こせるようにする。 */
    public void tickGuard(ServerLevel level) {
        Entity guard = guardAlive(level);
        if (guard == null && guardId != null) {
            // チャンクの読み込みが遅れているだけのこともあるので、しばらく待ってから忘れる
            if (++lostChecks >= LOST_CHECKS) {
                guardId = null;
                lostChecks = 0;
                setChanged();
            }
            return;
        }
        lostChecks = 0;
        if (guard != null || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        Player player = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                WAKE_RADIUS, p -> !p.isSpectator() && !((Player) p).isCreative());
        if (player != null) {
            summon(level, player instanceof ServerPlayer sp ? sp : null);
        }
    }

    @Nullable
    public Entity guardAlive(ServerLevel level) {
        if (guardId == null) {
            return null;
        }
        Entity e = level.getEntity(guardId);
        return e != null && e.isAlive() ? e : null;
    }

    /** 中ボスを中継器の上に起こす。起こしたら true。 */
    public boolean summon(ServerLevel level, @Nullable ServerPlayer player) {
        if (!RuinGuards.canSummon(level, player) || guardAlive(level) != null) {
            return false;
        }
        Mob guard = switch (guardian) {
            case "containment_hauler" -> SinguloEntities.CONTAINMENT_HAULER.get().create(level, net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            case "hull_repairer" -> SinguloEntities.HULL_REPAIRER.get().create(level, net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            default -> null;
        };
        if (guard == null) {
            return false;
        }
        guard.snapTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        if (guard instanceof RuinBoss boss) {
            boss.awaken(worldPosition);
            boss.startEmerging();
        }
        level.addFreshEntity(guard);
        guardId = guard.getUUID();
        lostChecks = 0;
        setChanged();
        tell(level, Component.translatable("gui.singulo.relay.awakened." + guardian));
        return true;
    }

    /** 中ボスが倒された（中ボスから呼ぶ）。 */
    public void onGuardDefeated(ServerLevel level) {
        guardDefeated = true;
        guardId = null;
        setChanged();
        tell(level, Component.translatable("gui.singulo.relay.defeated"));
    }

    /** 挑戦者がいなくなって中ボスが戻った（中ボスから呼ぶ）。 */
    public void onGuardReset(ServerLevel level) {
        guardId = null;
        setChanged();
    }

    /** pos の中継器に中ボスの結果を知らせる。 */
    public static void notifyGuard(ServerLevel level, @Nullable BlockPos pos, boolean defeated) {
        if (pos != null && level.getBlockEntity(pos) instanceof RuinRelayBlockEntity relay) {
            if (defeated) {
                relay.onGuardDefeated(level);
            } else {
                relay.onGuardReset(level);
            }
        }
    }

    // ------------------------------------------------------------------ 保存

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("mode", mode);
        if (!guardian.isEmpty()) {
            tag.putString("guardian", guardian);
        }
        tag.putBoolean("active", active);
        tag.putBoolean("guard_defeated", guardDefeated);
        if (guardId != null) {
            tag.store("guard", net.minecraft.core.UUIDUtil.CODEC, guardId);
        }
        tag.putInt("charge", charge);
        tag.putBoolean("bulkheads_open", bulkheadsOpen);
        tag.putBoolean("draining", draining);
        if (doorOffset != null) {
            tag.put("door", writeOffset(doorOffset));
            ListTag peers = new ListTag();
            for (BlockPos offset : stationOffsets) peers.add(writeOffset(offset));
            tag.put("stations", peers);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = tag.getStringOr("mode", PLAIN);
        guardian = tag.getStringOr("guardian", "");
        active = tag.getBooleanOr("active", false);
        guardDefeated = tag.getBooleanOr("guard_defeated", false);
        guardId = tag.read("guard", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        charge = Math.max(0, Math.min(EXTRACT_GOAL, tag.getIntOr("charge", 0)));
        bulkheadsOpen = tag.getBooleanOr("bulkheads_open", false);
        draining = tag.getBooleanOr("draining", false);
        ListTag door = tag.getListOrEmpty("door");
        doorOffset = door.size() == 3 ? readOffset(door) : null;
        stationOffsets.clear();
        ListTag peers = tag.getListOrEmpty("stations");
        for (int i = 0; i < peers.size(); i++) {
            ListTag entry = peers.getListOrEmpty(i);
            if (entry.size() == 3) stationOffsets.add(readOffset(entry));
        }
    }

    private static BlockPos readOffset(ListTag v) {
        return new BlockPos(v.getIntOr(0, 0), v.getIntOr(1, 0), v.getIntOr(2, 0));
    }

    private static ListTag writeOffset(BlockPos pos) {
        ListTag v = new ListTag();
        v.add(IntTag.valueOf(pos.getX()));
        v.add(IntTag.valueOf(pos.getY()));
        v.add(IntTag.valueOf(pos.getZ()));
        return v;
    }
}
