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
        super.onBroken(level);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("radius", loadedRadius);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadedRadius = tag.contains("radius") ? tag.getInt("radius") : -1;
    }
}
