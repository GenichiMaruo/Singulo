package io.github.genichimaruo.singulo.api.android;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity;

/** Owner-authorized same-dimension endpoint links. Does not load chunks. */
public final class AndroidWorkEndpoints {
    private AndroidWorkEndpoints() { }
    public enum Result { LINKED, UNLINKED, REJECTED, UNAVAILABLE, BUSY }
    public static Result link(ServerLevel level, BlockPos console, UUID actor, BlockPos endpoint) {
        var c = console(level, console, actor, endpoint);
        return c == null ? Result.UNAVAILABLE : c.linkExternalEndpoint(actor, endpoint);
    }
    public static Result unlink(ServerLevel level, BlockPos console, UUID actor, BlockPos endpoint) {
        var c = console(level, console, actor, endpoint);
        return c == null ? Result.UNAVAILABLE : c.unlinkExternalEndpoint(actor, endpoint);
    }
    private static WorkConsoleBlockEntity console(ServerLevel level, BlockPos pos, UUID actor, BlockPos endpoint) {
        Objects.requireNonNull(level); Objects.requireNonNull(pos); Objects.requireNonNull(actor); Objects.requireNonNull(endpoint);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("AndroidWorkEndpoints requires the server thread");
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WorkConsoleBlockEntity c && !c.isRemoved() ? c : null;
    }
}
