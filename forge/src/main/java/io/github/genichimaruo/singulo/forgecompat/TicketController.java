package io.github.genichimaruo.singulo.forgecompat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.world.ForgeChunkManager;

/** Forge 版: NeoForge の TicketController と同じ形で使える、チャンクを読み込んだままにする札（中身は ForgeChunkManager）。 */
public final class TicketController {
    private final ResourceLocation id;
    private final ForgeChunkManager.LoadingValidationCallback callback;

    public TicketController(ResourceLocation id, ForgeChunkManager.LoadingValidationCallback callback) {
        this.id = id;
        this.callback = callback;
    }

    /** Mod の読み込み中に一度呼ぶ（読み込み時に、要らなくなった札を捨てる確認を登録する）。 */
    public void register() {
        ForgeChunkManager.setForcedChunkLoadingCallback(id.getNamespace(), callback);
    }

    public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
        return ForgeChunkManager.forceChunk(level, id.getNamespace(), owner, chunkX, chunkZ, add, ticking);
    }
}
