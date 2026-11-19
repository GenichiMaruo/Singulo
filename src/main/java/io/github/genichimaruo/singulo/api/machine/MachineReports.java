package io.github.genichimaruo.singulo.api.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.MachineReport;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;

/** Machine diagnostics shared by factory monitors, HUD integrations and android processing cards. */
public final class MachineReports {
    private MachineReports() {}

    /** Unsided capability. Providers may be queried on either side; report data is read on the server. */
    public static final BlockCapability<MachineReport.Source, Void> CAPABILITY =
            BlockCapability.createVoid(Singulo.id("machine_report"), MachineReport.Source.class);

    public static MachineReport.@Nullable Source source(@Nullable BlockEntity block) {
        if (block == null || block.isRemoved()) return null;
        if (block instanceof MachineReport.Source source) return source;
        var level = block.getLevel();
        return level == null ? null : level.getCapability(CAPABILITY, block.getBlockPos(), block.getBlockState(), block);
    }

    /** Read a report at the machine itself. The position must already be loaded. */
    public static Optional<MachineReport> read(ServerLevel level, BlockPos pos) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("MachineReports.read requires the server thread");
        if (!level.isLoaded(pos)) return Optional.empty();
        var provider = source(level.getBlockEntity(pos));
        return provider == null ? Optional.empty() : Optional.of(provider.report());
    }
}
