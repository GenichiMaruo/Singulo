package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.data.ThermalData;
import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 熱電発電機（ゼーベック効果）。隣の最も熱いブロックと最も冷たいブロックの温度差 ΔT から
 * P = thermoelectricCoefficient × ΔT FE/t を出す。
 * ΔT が低温源の「維持できる温度差」を超えると、超えた分に比例した速さで低温源が溶けて劣化する。
 */
public class ThermoelectricGeneratorBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener,
        GeneratorInfo {
    /** 維持できる温度差を 1 K 超えたまま、低温源が1段階劣化するまでの tick 数。100 K 超過なら約1分。 */
    static final double MELT_THRESHOLD = 120_000;
    static final int SCAN_INTERVAL = 10;
    static final int PUSH_PER_TICK = 2_000;

    public static final int D_HOT = 0, D_COLD = 1, D_RATE_X100 = 2, D_ENERGY = 3, D_CAPACITY = 4, D_TOLERANCE = 5,
            D_MELTING = 6, D_FLUID = 7, COUNT = 8;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(20_000, 0, PUSH_PER_TICK, this::setChanged);
    private int hot;
    private int cold;
    private int tolerance;
    @Nullable
    private BlockPos coldPos;
    private double rate;
    private double fraction;
    private double meltProgress;

    public ThermoelectricGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.THERMOELECTRIC_GENERATOR.get(), pos, state);
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ThermoelectricGeneratorBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % SCAN_INTERVAL == 0) {
            scan(level, pos);
        }
        boolean active = rate > 0;
        if (active) {
            fraction += rate;
            int whole = (int) fraction;
            fraction -= whole;
            energy.generate(whole);
            melt(level);
        }
        pushEnergy(level, pos);
        if (state.getValue(AbstractMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    private void scan(Level level, BlockPos pos) {
        int bestHot = 0;
        int bestCold = Integer.MAX_VALUE;
        int bestTolerance = 0;
        BlockPos bestColdPos = null;
        for (Direction dir : Direction.values()) {
            BlockPos p = pos.relative(dir);
            BlockState s = level.getBlockState(p);
            ThermalData.Hot h = ThermalData.INSTANCE.hot(s);
            if (h != null && h.temperature() > bestHot) {
                bestHot = h.temperature();
            }
            ThermalData.Cold c = ThermalData.INSTANCE.cold(s);
            if (c != null && c.temperature() < bestCold) {
                bestCold = c.temperature();
                bestTolerance = c.tolerance();
                bestColdPos = p;
            }
        }
        if (bestColdPos == null || !bestColdPos.equals(coldPos)) {
            meltProgress = 0;
        }
        hot = bestHot;
        cold = bestColdPos == null ? 0 : bestCold;
        tolerance = bestTolerance;
        coldPos = bestColdPos;
        int delta = deltaT();
        rate = delta > 0 ? delta * ServerConfig.THERMOELECTRIC_COEFFICIENT.get() * ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.get() : 0;
    }

    private int deltaT() {
        return hot > 0 && coldPos != null ? Math.max(0, hot - cold) : 0;
    }

    private void melt(Level level) {
        int excess = deltaT() - tolerance;
        if (coldPos == null || excess <= 0) {
            return;
        }
        // ネザーでは氷系が速く溶ける
        meltProgress += excess * (level.dimensionType().ultraWarm() ? 2.0 : 1.0);
        if (meltProgress < MELT_THRESHOLD) {
            return;
        }
        meltProgress = 0;
        ThermalData.Cold c = ThermalData.INSTANCE.cold(level.getBlockState(coldPos));
        if (c != null) {
            Block next = c.becomes();
            if (next == Blocks.WATER && level.dimensionType().ultraWarm()) {
                next = Blocks.AIR;
            }
            level.setBlock(coldPos, next.defaultBlockState(), Block.UPDATE_ALL);
        }
        scan(level, worldPosition);
    }

    private void pushEnergy(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) {
                return;
            }
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target != null && target.canReceive()) {
                int sent = target.receiveEnergy(Math.min(energy.getEnergyStored(), PUSH_PER_TICK), false);
                if (sent > 0) {
                    energy.consume(sent);
                }
            }
        }
    }

    // ------------------------------------------------------------------ GUI・保存

    @Override
    public SyncedInts syncData() {
        return SyncedInts.server(COUNT, i -> switch (i) {
            case D_HOT -> hot;
            case D_COLD -> cold;
            case D_RATE_X100 -> (int) Math.round(rate * 100);
            case D_ENERGY -> energy.getEnergyStored();
            case D_CAPACITY -> energy.getMaxEnergyStored();
            case D_TOLERANCE -> tolerance;
            case D_MELTING -> deltaT() > tolerance && coldPos != null ? 1 : 0;
            case D_FLUID -> -1;
            default -> 0;
        });
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ThermoelectricGeneratorMenu(containerId, worldPosition, syncData());
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.openMenu(this, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putDouble("melt", meltProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("energy"));
        meltProgress = tag.getDouble("melt");
    }
}
