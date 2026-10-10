package io.github.genichimaruo.singulo.darkmatter;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * ハロー捕集器（段階5）。ペンローズ・リアクターの炉心から RANGE ブロック以内に置くと、炉心の重力で集まった
 * 暗黒物質（WIMP）を集める。量は炉心質量100ごとに毎秒1 mB（質量5000で毎秒50 mB）、電力は FE_PER_TICK。
 * <p>
 * ダークマターは重力以外ほとんど相互作用しないので、捕集器の小さなタンクからは毎秒2%ずつ漏れる。
 * 毎秒、隣の液体の入れ物（重力閉じ込めタンクや、炉心制御装置・抽出ポート）へ押し出す。
 */
public class HaloCollectorBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    public static final int RANGE = 16;
    public static final int FE_PER_TICK = 2000;
    public static final int BUFFER = 4000;
    static final int INTERVAL = 20;

    private final FluidTank tank = new FluidTank(BUFFER, ContainmentTankBlockEntity::isDarkMatter) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    /** 外からは取り出すだけ。 */
    private final IFluidHandler output = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int t) {
            return BUFFER;
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return tank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return tank.drain(maxDrain, action);
        }
    };
    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(200_000, 20_000, 0, this::setChanged);
    private double carry;
    private int rate;

    public HaloCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.HALO_COLLECTOR.get(), pos, state);
    }

    public FluidTank tank() {
        return tank;
    }

    public IFluidHandler automationFluids() {
        return output;
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    /** 今の収集速度（mB/秒）。 */
    public int rate() {
        return rate;
    }

    public static int rateFor(double coreMass) {
        return (int) (coreMass / 100);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HaloCollectorBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0) {
            return;
        }
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        if (tank.getFluidAmount() > 0) {
            tank.drain(Math.max(1, tank.getFluidAmount() / 50), IFluidHandler.FluidAction.EXECUTE);
        }
        PenroseReactorBlockEntity reactor = PenroseReactorBlockEntity.nearestRunning(level, pos, RANGE);
        rate = 0;
        if (reactor != null && energy.consume(FE_PER_TICK * INTERVAL)) {
            rate = rateFor(reactor.mass());
            io.github.genichimaruo.singulo.registry.SinguloTriggers.milestoneNear(level, pos, 16, "dark_matter");
            tank.fill(ContainmentTankBlockEntity.darkMatter(rate), IFluidHandler.FluidAction.EXECUTE);
        }
        pushOut(level, pos);
        boolean lit = rate > 0;
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    private void pushOut(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (tank.isEmpty()) {
                return;
            }
            IFluidHandler target = Capabilities.get(level, Capabilities.FluidHandler.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target != null) {
                int moved = target.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
                if (moved > 0) {
                    tank.drain(moved, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        io.github.genichimaruo.singulo.machine.DeviceMenu.open(player, this, io.github.genichimaruo.singulo.machine.DeviceMenu.Kind.HALO_COLLECTOR, new net.minecraftforge.items.ItemStackHandler(0), i -> {
            PenroseReactorBlockEntity core = level == null ? null : PenroseReactorBlockEntity.nearestRunning(level, worldPosition, RANGE);
            return switch (i) {
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Halo.RATE -> rate;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Halo.AMOUNT -> tank.getFluidAmount();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Halo.BUFFER -> BUFFER;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Halo.ENERGY -> energy.getEnergyStored();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Halo.ENERGY_MAX -> energy.getMaxEnergyStored();
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Halo.CORE -> core == null ? 0 : 1;
                case io.github.genichimaruo.singulo.machine.DeviceMenu.Halo.CORE_MASS -> core == null ? 0 : (int) core.mass();
                default -> 0;
            };
        }, (p, id) -> false);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("tank", tank.writeToNBT(new CompoundTag()));
        tag.putInt("energy", energy.getEnergyStored());
        tag.putDouble("carry", carry);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound("tank"));
        energy.setEnergy(tag.getInt("energy"));
        carry = tag.getDouble("carry");
    }
}
