package io.github.genichimaruo.singulo.darkmatter;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 重力閉じ込めタンク（段階5）。内部に重力場を張ってダークマターを閉じ込める、ダークマターを保管できる唯一の容器。
 * 中身があると場の維持に FE_PER_TICK を使い、電力が切れると毎秒1%（最低1 mB）ずつ漏れていく。
 * 壊すと中身ごとアイテムになり（タンクごと運ぶ）、置き直すと戻る。アイテムの間は漏れない。
 */
public class ContainmentTankBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener {
    public static final int CAPACITY = 64_000;
    public static final int FE_PER_TICK = 500;
    static final int INTERVAL = 20;

    private final FluidTank tank = new FluidTank(CAPACITY, ContainmentTankBlockEntity::isDarkMatter) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(100_000, 20_000, 0, this::setChanged);
    private boolean contained = true;

    public ContainmentTankBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.CONTAINMENT_TANK.get(), pos, state);
    }

    public static boolean isDarkMatter(FluidStack stack) {
        return stack.is(SinguloFluids.get("dark_matter"));
    }

    public static FluidStack darkMatter(int amount) {
        return new FluidStack(SinguloFluids.get("dark_matter"), amount);
    }

    public FluidTank tank() {
        return tank;
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public int amount() {
        return tank.getFluidAmount();
    }

    /** 場が張れているか（中身がないか、電力が足りている）。 */
    public boolean contained() {
        return contained;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ContainmentTankBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0) {
            return;
        }
        be.contained = be.amount() == 0 || be.energy.consume(FE_PER_TICK * INTERVAL);
        if (!be.contained) {
            be.tank.drain(Math.max(1, be.amount() / 100), FluidTank.FluidAction.EXECUTE);
        }
        boolean lit = be.amount() > 0 && be.contained;
        if (state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        io.github.genichimaruo.singulo.machine.DeviceMenu.open(player, this, io.github.genichimaruo.singulo.machine.DeviceMenu.Kind.CONTAINMENT_TANK, new net.neoforged.neoforge.items.ItemStackHandler(0), i -> switch (i) {
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Tank.AMOUNT -> amount();
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Tank.CAPACITY -> CAPACITY;
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Tank.CONTAINED -> contained ? 1 : 0;
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Tank.ENERGY -> energy.getEnergyStored();
            case io.github.genichimaruo.singulo.machine.DeviceMenu.Tank.ENERGY_MAX -> energy.getMaxEnergyStored();
            default -> 0;
        }, (p, id) -> false);
    }

    // ------------------------------------------------------------------ アイテムとの受け渡し（タンクごと運ぶ）

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (amount() > 0) {
            components.set(SinguloComponents.DARK_MATTER.get(), amount());
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        int amount = input.getOrDefault(SinguloComponents.DARK_MATTER.get(), 0);
        tank.setFluid(amount > 0 ? darkMatter(Math.min(CAPACITY, amount)) : FluidStack.EMPTY);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("tank");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("tank"));
        energy.setEnergy(tag.getInt("energy"));
    }
}
