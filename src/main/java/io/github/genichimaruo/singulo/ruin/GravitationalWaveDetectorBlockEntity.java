package io.github.genichimaruo.singulo.ruin;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 重力波検出器（段階3）。最終実験施設（重力異常点）が出す重力波をとらえ、おおよその方角と距離帯を示す。
 * 1回の観測に OBSERVATION_COST FE 使う。正確な位置まではわからない。
 */
public class GravitationalWaveDetectorBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    public static final int OBSERVATION_COST = 50_000;
    public static final int SEARCH_RADIUS_CHUNKS = 160;
    public static final TagKey<Structure> GRAVITY_ANOMALIES =
            TagKey.create(Registries.STRUCTURE, Singulo.id("gravity_anomalies"));
    /** 距離帯の境目（ブロック）。 */
    static final int[] BANDS = {256, 512, 1024, 2048};

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(200_000, 10_000, 0, this::setChanged);

    public GravitationalWaveDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.GRAVITATIONAL_WAVE_DETECTOR.get(), pos, state);
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    /** いちばん近い重力異常点。見つからなければ null。 */
    @Nullable
    public BlockPos locate(ServerLevel level) {
        return level.findNearestMapStructure(GRAVITY_ANOMALIES, worldPosition, SEARCH_RADIUS_CHUNKS, false);
    }

    /** 観測結果の文。 */
    public static Component describe(BlockPos from, @Nullable BlockPos target) {
        if (target == null) {
            return Component.translatable("gui.singulo.detector.none");
        }
        int dx = target.getX() - from.getX();
        int dz = target.getZ() - from.getZ();
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        return Component.translatable("gui.singulo.detector.result",
                Component.translatable("direction.singulo." + direction(dx, dz)),
                Component.translatable("gui.singulo.detector.band." + band(distance)));
    }

    /** 8方位（north, northeast, …）。マイクラでは北が -Z。 */
    static String direction(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return new String[]{"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"}[sector];
    }

    static int band(double distance) {
        for (int i = 0; i < BANDS.length; i++) {
            if (distance < BANDS[i]) {
                return i;
            }
        }
        return BANDS.length;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        if (!energy.consume(OBSERVATION_COST)) {
            player.displayClientMessage(Component.translatable("gui.singulo.detector.no_power", OBSERVATION_COST), true);
            return;
        }
        Level level = player.level();
        player.displayClientMessage(describe(worldPosition, locate((ServerLevel) level)), false);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("energy"));
    }
}
