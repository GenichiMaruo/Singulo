package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;


/**
 * ワールドライン・アンカー（小、ティア2）。触媒を入れると周りのチャンクを常時ロードする。
 * 半径は触媒のティアで決まり（ミュオン触媒で1、ボース凝縮触媒以上で2）、電力はロードするチャンク数に比例する。
 * 置いた人ごとに、同時に動かせる数は設定 maxAnchorsPerPlayer まで（小・上位を合わせて数える。0 で無制限）。
 */
public class WorldlineAnchorBlockEntity extends CatalystDeviceBlockEntity {
    public static final int MACHINE_TIER = 2;
    public static final int MAX_RADIUS = 2;
    public static final int FE_PER_CHUNK = 20;

    private int loadedRadius = -1;
    /** 置いた人（設定 maxAnchorsPerPlayer の数え先）。旧版で置いたものや、人以外が置いたものは null（上限の対象外）。 */
    @javax.annotation.Nullable
    private java.util.UUID owner;

    /**
     * 持ち主ごとの、チャンクを読み込み中のアンカー（ディメンションと位置 → 動き出したゲーム時刻）。上限に達した持ち主の
     * 新しいアンカーは動き出さない。上限を下げたときは、動き出した時刻の遅いもの（同時なら位置の順）から止める。
     * 止める・壊す・触媒が切れると枠が空く。動き出した時刻は保存するので、再起動しても同じものが残る。
     */
    private static final java.util.Map<java.util.UUID, java.util.Map<String, Long>> ACTIVE = new java.util.HashMap<>();
    /** Drop runtime quota entries for a closed dimension; saved start times remain unchanged. */
    public static void forgetLevel(Level level) {
        String prefix = level.dimension().location() + "@";
        ACTIVE.entrySet().removeIf(e -> {
            e.getValue().keySet().removeIf(key -> key.startsWith(prefix));
            return e.getValue().isEmpty();
        });
    }

    /** 動き出したゲーム時刻（止まっていれば -1）。 */
    private long startedAt = -1;

    public void setOwner(java.util.UUID owner) {
        this.owner = owner;
        setChanged();
    }

    private String key() {
        return (level == null ? "?" : level.dimension().location().toString()) + "@" + worldPosition.asLong();
    }

    /** 1人が同時に動かせるアンカーの数。0 は無制限。 */
    public static int anchorLimit() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.MAX_ANCHORS_PER_PLAYER.get() : 8;
    }

    /** 持ち主が今動かしているアンカーの数。 */
    public static int activeCount(java.util.UUID owner) {
        java.util.Map<String, Long> m = ACTIVE.get(owner);
        return m == null ? 0 : m.size();
    }

    private void claim(boolean on) {
        if (on) {
            if (startedAt < 0) {
                startedAt = level != null ? level.getGameTime() : 0;
                setChanged();
            }
        } else if (startedAt >= 0) {
            startedAt = -1;
            setChanged();
        }
        if (owner == null) {
            return;
        }
        if (on) {
            ACTIVE.computeIfAbsent(owner, k -> new java.util.HashMap<>()).put(key(), startedAt);
        } else {
            unregister();
        }
    }

    /** 数え先から外すだけ（動き出した時刻は残す。チャンクの読み込み解除や終了のとき）。 */
    private void unregister() {
        java.util.Map<String, Long> m = owner == null ? null : ACTIVE.get(owner);
        if (m != null) {
            m.remove(key());
            if (m.isEmpty()) {
                ACTIVE.remove(owner);
            }
        }
    }

    /** 持ち主の動いているアンカーの中での順番（動き出した時刻順、同時なら位置の順。0 が最初）。 */
    private int rank(java.util.Map<String, Long> m) {
        String me = key();
        long mine = m.getOrDefault(me, startedAt);
        int r = 0;
        for (java.util.Map.Entry<String, Long> e : m.entrySet()) {
            if (e.getValue() < mine || e.getValue() == mine && e.getKey().compareTo(me) < 0) {
                r++;
            }
        }
        return r;
    }

    /**
     * 上限の判定。動いていないアンカーは、持ち主の枠が埋まっていれば動き出さない。動いているアンカーも、上限を下げて
     * 枠からあふれたら（後から動き出した順に）止まる。
     */
    @Override
    protected Status readiness(ServerLevel level) {
        int limit = anchorLimit();
        if (owner == null || limit <= 0) {
            return null;
        }
        java.util.Map<String, Long> m = ACTIVE.get(owner);
        boolean claimed = m != null && m.containsKey(key());
        if (!claimed) {
            return activeCount(owner) >= limit ? Status.LIMITED : null;
        }
        return m.size() > limit && rank(m) >= limit ? Status.LIMITED : null;
    }

    public WorldlineAnchorBlockEntity(BlockPos pos, BlockState state) {
        this(SinguloBlockEntities.WORLDLINE_ANCHOR.get(), pos, state);
    }

    protected WorldlineAnchorBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos,
                                         BlockState state) {
        super(type, pos, state, 50_000, 2_000, 0);
    }

    /** この装置で開ける最大半径。 */
    protected int maxRadius() {
        return MAX_RADIUS;
    }

    @Override
    public Kind kind() {
        return Kind.WORLDLINE_ANCHOR;
    }

    @Override
    protected int machineTier() {
        return MACHINE_TIER;
    }

    public int loadedRadius() {
        return loadedRadius;
    }

    /** この触媒で開ける半径（使えなければ -1）。半径はティア − 1。 */
    public int radiusFor(ItemStack catalyst) {
        if (!CatalystHelper.effect(machineTier(), catalyst).usable()) {
            return -1;
        }
        return Math.max(0, Math.min(cappedRadius(maxRadius()), CatalystHelper.tierOf(catalyst) - 1));
    }

    /** 設定 maxAnchorChunkRadius で頭打ちにする。 */
    protected static int cappedRadius(int radius) {
        return ServerConfig.SPEC.isLoaded() ? Math.min(radius, ServerConfig.MAX_ANCHOR_CHUNK_RADIUS.get()) : radius;
    }

    @Override
    protected int baseUsage(ItemStack catalyst) {
        int r = Math.max(0, radiusFor(catalyst));
        return FE_PER_CHUNK * (2 * r + 1) * (2 * r + 1);
    }

    @Override
    protected void apply(ServerLevel level, CatalystHelper.Effect effect, boolean active) {
        setRadius(level, active ? activeRadius() : -1);
    }

    /** 動いているときの半径。 */
    protected int activeRadius() {
        return radiusFor(catalyst());
    }

    @Override
    protected int displayValue() {
        return Math.max(0, loadedRadius);
    }

    private void setRadius(ServerLevel level, int radius) {
        if (radius == loadedRadius) {
            return;
        }
        forceChunks(level, loadedRadius, false);
        forceChunks(level, radius, true);
        loadedRadius = radius;
        claim(radius >= 0);
        setChanged();
    }

    private void forceChunks(ServerLevel level, int radius, boolean add) {
        if (radius < 0) {
            return;
        }
        ChunkPos center = new ChunkPos(worldPosition);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                net.minecraftforge.common.world.ForgeChunkManager.forceChunk(level, Singulo.MODID, worldPosition, center.x + dx, center.z + dz, add, true);
            }
        }
    }

    /** Remove this position's tickets and owner quota without dropping its catalyst. */
    public void prepareForRelocation() {
        if (level instanceof ServerLevel server) {
            forceChunks(server, loadedRadius, false);
            loadedRadius = -1;
        }
        claim(false);
    }

    @Override
    public void onBroken(Level level) {
        prepareForRelocation();
        super.onBroken(level);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 読み込み直したとき、動いていたアンカーは枠を取り直す（再起動しても数が合うように）
        if (level != null && !level.isClientSide && loadedRadius >= 0) {
            claim(true);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            unregister();
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("radius", loadedRadius);
        tag.putLong("started_at", startedAt);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loadedRadius = tag.contains("radius") ? tag.getInt("radius") : -1;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        startedAt = tag.contains("started_at") ? tag.getLong("started_at") : (loadedRadius >= 0 ? 0 : -1);
    }
}
