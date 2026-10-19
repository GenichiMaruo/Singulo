package io.github.genichimaruo.singulo.machine;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * 水素燃料電池（段階1）。水素と酸素を反応させて電力と水（水蒸気として出す）を作る、電解槽の逆の装置。
 * <ul>
 * <li>水素1 mB で {@link #fePerMb()}（電解槽で水素1 mB を作る電力 {@link #ELECTROLYSIS_FE_PER_MB} の {@link #EFFICIENCY}）。
 *     電解槽と組んで回しても電力は増えない（電解槽の水素は、単極子などで速くしても1 mBあたりの電力が変わらない）。</li>
 * <li>水素2 mB に酸素1 mB を使う（水の H₂O と同じ割合）。出力は最大 {@link #MAX_OUTPUT} FE/t（設定の発電倍率がかかる）。</li>
 * <li>蓄電がほぼいっぱいの間は待機して、水素も酸素も減らさない。水素と酸素は、パイプでも液体キャニスターでも入る。</li>
 * </ul>
 * 画面は持たず、素手で右クリックすると状態を表示する。
 */
public class HydrogenFuelCellBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    /** 電解槽が水素1 mB を作る電力（水200 mB → 水素200 mB に 80 FE/t × 20 tick）。 */
    public static final double ELECTROLYSIS_FE_PER_MB = 8.0;
    public static final double EFFICIENCY = 0.6;
    public static final int MAX_OUTPUT = 240;
    public static final int HYDROGEN_TANK = 16_000;
    public static final int OXYGEN_TANK = 8_000;
    static final int PUSH_PER_TICK = 2_000;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(50_000, 0, PUSH_PER_TICK, this::setChanged);
    private final FluidTank hydrogen = tank(HYDROGEN_TANK, "hydrogen");
    private final FluidTank oxygen = tank(OXYGEN_TANK, "oxygen");
    /** 外からの出し入れ: 水素と酸素を、それぞれのタンクへ入れる（取り出しはしない）。 */
    private final IFluidHandler fluids = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return (tank == 0 ? hydrogen : oxygen).getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return (tank == 0 ? hydrogen : oxygen).getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return (tank == 0 ? hydrogen : oxygen).isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (hydrogen.isFluidValid(resource)) {
                return hydrogen.fill(resource, action);
            }
            return oxygen.isFluidValid(resource) ? oxygen.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };
    /** 水素の端数（1 tick に1 mB 未満しか使わないときの持ち越し）。 */
    private double carry;
    private double rate;
    private boolean standby;

    public HydrogenFuelCellBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.HYDROGEN_FUEL_CELL.get(), pos, state);
    }

    private FluidTank tank(int capacity, String fluid) {
        return new FluidTank(capacity, f -> f.getFluid().isSame(SinguloFluids.get(fluid))) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
    }

    /** 水素1 mB から取り出す電力（加工装置の電力倍率に合わせる。電解槽の電力も同じ倍率で増減するため）。 */
    public static double fePerMb() {
        double multiplier = ServerConfig.SPEC.isLoaded() ? ServerConfig.MACHINE_ENERGY_MULTIPLIER.get() : 1.0;
        return ELECTROLYSIS_FE_PER_MB * multiplier * EFFICIENCY;
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public IFluidHandler fluids() {
        return fluids;
    }

    public FluidTank hydrogen() {
        return hydrogen;
    }

    public FluidTank oxygen() {
        return oxygen;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HydrogenFuelCellBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        rate = 0;
        double fill = (double) energy.getEnergyStored() / energy.getMaxEnergyStored();
        if (standby ? fill < 0.75 : fill >= 0.98) {
            standby = !standby;
        }
        double perMb = fePerMb();
        double output = MAX_OUTPUT * (ServerConfig.SPEC.isLoaded() ? ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.get() : 1.0);
        if (!standby && perMb > 0) {
            // 出力に見合う水素を使う（水素2 mB に酸素1 mB）。足りない分だけ出力を下げる
            double want = output / perMb;
            // Only whole 2H:1O pairs can be withdrawn. An odd last mB must stay in the tank,
            // rather than being credited as a second mB that drain() never supplied.
            double available = Math.min(hydrogen.getFluidAmount() / 2, oxygen.getFluidAmount()) * 2.0 + carry;
            double use = Math.min(want, available);
            if (use > 0) {
                carry -= use;
                int whole = 0;
                while (carry < 0) {
                    carry += 2;
                    whole += 2;
                }
                if (whole > 0) {
                    hydrogen.drain(whole, IFluidHandler.FluidAction.EXECUTE);
                    oxygen.drain(whole / 2, IFluidHandler.FluidAction.EXECUTE);
                }
                rate = use * perMb;
                energy.generate((int) Math.floor(rate));
            }
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
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    /** 今の出力（FE/t）。 */
    public double rate() {
        return rate;
    }

    /** 素手で右クリック: 状態を表示する。 */
    @Override
    public void openMenu(ServerPlayer player) {
        Component status = standby ? Component.translatable("message.singulo.fuel_cell.standby")
                : Component.translatable("gui.singulo.energy_rate", String.format("%.1f", rate));
        player.displayClientMessage(Component.translatable("message.singulo.fuel_cell.status",
                hydrogen.getFluidAmount(), HYDROGEN_TANK, oxygen.getFluidAmount(), OXYGEN_TANK,
                energy.getEnergyStored(), energy.getMaxEnergyStored(), status), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("hydrogen", hydrogen.writeToNBT(new CompoundTag()));
        tag.put("oxygen", oxygen.writeToNBT(new CompoundTag()));
        tag.putDouble("carry", carry);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setEnergy(tag.getInt("energy"));
        hydrogen.readFromNBT(tag.getCompound("hydrogen"));
        oxygen.readFromNBT(tag.getCompound("oxygen"));
        carry = tag.getDouble("carry");
    }
}
