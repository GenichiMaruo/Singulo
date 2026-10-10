package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.data.ThermalData;
import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * 極低温タービン。液体窒素（77 K）を隣の高温源で一気に気化させ、その膨張でタービンを回す。
 * 出力は高温源の温度に比例し、溶岩（1300 K）で cryoTurbineOutput（既定 5 kFE/t）。
 * 液体窒素を毎tick 2 mB 使う（冷却塔1基で約2.5基を回せる）。
 */
public class CryogenicTurbineBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener,
        GeneratorInfo {
    static final int COOLANT_TEMPERATURE = 77;
    static final int REFERENCE_HOT = 1300;
    static final int COOLANT_PER_TICK = 2;
    static final int PUSH_PER_TICK = 20_000;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(200_000, 0, PUSH_PER_TICK, this::setChanged);
    private final FluidTank tank = new FluidTank(MachineType.TANK_CAPACITY,
            f -> f.getFluid().isSame(SinguloFluids.get("liquid_nitrogen"))) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private int hot;
    private double rate;

    public CryogenicTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.CRYOGENIC_TURBINE.get(), pos, state);
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public IFluidHandler tank() {
        return tank;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CryogenicTurbineBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 10 == 0) {
            hot = 0;
            for (Direction dir : Direction.values()) {
                ThermalData.Hot h = ThermalData.INSTANCE.hot(level.getBlockState(pos.relative(dir)));
                if (h != null) {
                    hot = Math.max(hot, h.temperature());
                }
            }
        }
        rate = 0;
        if (hot > COOLANT_TEMPERATURE && tank.getFluidAmount() >= COOLANT_PER_TICK) {
            double f = Math.min(1.0, (hot - COOLANT_TEMPERATURE) / (double) (REFERENCE_HOT - COOLANT_TEMPERATURE));
            rate = ServerConfig.CRYO_TURBINE_OUTPUT.get() * f * ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.get();
            tank.drain(COOLANT_PER_TICK, IFluidHandler.FluidAction.EXECUTE);
            energy.generate((int) rate);
        }
        for (Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) {
                break;
            }
            IEnergyStorage target = Capabilities.get(level, Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target != null && target.canReceive()) {
                int sent = target.receiveEnergy(Math.min(energy.getEnergyStored(), PUSH_PER_TICK), false);
                if (sent > 0) {
                    energy.consume(sent);
                }
            }
        }
        boolean active = rate > 0;
        if (state.getValue(AbstractMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    @Override
    public SyncedInts syncData() {
        return SyncedInts.server(ThermoelectricGeneratorBlockEntity.COUNT, i -> switch (i) {
            case ThermoelectricGeneratorBlockEntity.D_HOT -> hot;
            case ThermoelectricGeneratorBlockEntity.D_COLD -> tank.isEmpty() ? 0 : COOLANT_TEMPERATURE;
            case ThermoelectricGeneratorBlockEntity.D_RATE_X100 -> (int) Math.round(rate * 100);
            case ThermoelectricGeneratorBlockEntity.D_ENERGY -> energy.getEnergyStored();
            case ThermoelectricGeneratorBlockEntity.D_CAPACITY -> energy.getMaxEnergyStored();
            case ThermoelectricGeneratorBlockEntity.D_FLUID -> tank.getFluidAmount();
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
        net.minecraftforge.network.NetworkHooks.openScreen(player, this, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("tank", tank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setEnergy(tag.getInt("energy"));
        tank.readFromNBT(tag.getCompound("tank"));
    }
}
