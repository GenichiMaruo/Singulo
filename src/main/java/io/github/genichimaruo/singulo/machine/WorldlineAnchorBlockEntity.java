package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.item.CatalystHelper;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * ワールドライン・アンカー（小、ティア2）。触媒を入れると周りのチャンクを常時ロードする。
 * 半径は触媒のティアで決まり（ミュオン触媒で1、ボース凝縮触媒以上で2）、電力はロードするチャンク数に比例する。
 * 置いた人ごとに、同時に動かせる数は設定 maxAnchorsPerPlayer まで（小・上位を合わせて数える。0 で無制限）。
 */
public class WorldlineAnchorBlockEntity extends CatalystDeviceBlockEntity {
    public static final int MACHINE_TIER = 2;
    public static final int MAX_RADIUS = 2;
    public static final int FE_PER_CHUNK = 20;

    public static final TicketController TICKETS = new TicketController(Singulo.id("worldline_anchor"),
            (level, helper) -> helper.getBlockTickets().keySet().forEach(pos -> {
                // 読み込み時、アンカーが無くなった場所のチケットを捨てる
                if (!(level.getBlockEntity(pos) instanceof WorldlineAnchorBlockEntity)) {
                    helper.removeAllTickets(pos);
                }
            }));

    private int loadedRadius = -1;
    /** 置いた人（設定 maxAnchorsPerPlayer の数え先）。旧版で置いたものや、人以外が置いたものは null（上限の対象外）。 */
    @javax.annotation.Nullable
    private java.util.UUID owner;

    /**
     * 持ち主ごとの、チャンクを読み込み中のアンカー（ディメンションと位置）。上限に達した持ち主の新しいアンカーは
     * 動き出さない（先に動き出したものが優先。止める・壊す・触媒が切れると枠が空く）。
     */
    private static final java.util.Map<java.util.UUID, java.util.Set<String>> ACTIVE = new java.util.HashMap<>();

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
        java.util.Set<String> set = ACTIVE.get(owner);
        return set == null ? 0 : set.size();
    }

    private void claim(boolean on) {
        if (owner == null) {
            return;
        }
        if (on) {
            ACTIVE.computeIfAbsent(owner, k -> new java.util.HashSet<>()).add(key());
        } else {
            java.util.Set<String> set = ACTIVE.get(owner);
            if (set != null) {
                set.remove(key());
                if (set.isEmpty()) {
                    ACTIVE.remove(owner);
                }
            }
        }
    }

    @Override
    protected Status readiness(ServerLevel level) {
        int limit = anchorLimit();
        if (owner != null && limit > 0 && loadedRadius < 0 && activeCount(owner) >= limit) {
            java.util.Set<String> set = ACTIVE.get(owner);
            if (set == null || !set.contains(key())) {
                return Status.LIMITED;
            }
        }
        return null;
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
        return radiusFor(slot.getStackInSlot(0));
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
                TICKETS.forceChunk(level, worldPosition, center.x + dx, center.z + dz, add, true);
            }
        }
    }

    @Override
    public void onBroken(Level level) {
        if (level instanceof ServerLevel server) {
            forceChunks(server, loadedRadius, false);
            loadedRadius = -1;
        }
        claim(false);
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
            claim(false);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("radius", loadedRadius);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadedRadius = tag.contains("radius") ? tag.getInt("radius") : -1;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }
}
