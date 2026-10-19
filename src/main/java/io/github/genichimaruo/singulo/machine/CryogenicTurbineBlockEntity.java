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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 極低温タービン（液体窒素の蓄電）。極低温冷却塔が液体窒素に詰めた電力を、隣の高温源で一気に気化させた膨張で取り戻す。
 * 取り戻せるのは詰めた電力の {@link #EFFICIENCY}（溶岩 1300 K の横で。温度が低いほど減る）までなので、冷却塔とつないで回しても
 * 電力は増えない（液体窒素は電力を貯めて運ぶためのもの。第2段階の発電はミュオン触媒核融合炉）。
 * 出力は cryoTurbineOutput（既定 4.5 kFE/t）まで。液体窒素はそれに見合う量だけ使う。蓄電がほぼいっぱいの間は待機して、窒素を減らさない。
 * 液体窒素は、パイプのほか、冷媒カートリッジを入口に入れても（空のカートリッジは出口へ）、カートリッジで右クリックしても入る。
 */
public class CryogenicTurbineBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener,
        AbstractMachineBlock.BreakListener, GeneratorSlotsMenu.Owner {
    static final int COOLANT_TEMPERATURE = 77;
    static final int REFERENCE_HOT = 1300;
    /** 液体窒素 1 mB に詰まっている電力（冷却塔の液体窒素のレシピ: 100 mB に 25,000 FE）。加工装置の消費電力倍率も掛ける。 */
    static final double NITROGEN_FE_PER_MB = 250;
    /** 詰めた電力のうち、取り戻せる割合（溶岩の横で）。1 未満なので、冷却塔とつないでも電力は増えない。 */
    static final double EFFICIENCY = 0.9;
    static final int PUSH_PER_TICK = 20_000;
    static final int TANK = 32_000;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(200_000, 0, PUSH_PER_TICK, this::setChanged);
    private final FluidTank tank = new FluidTank(TANK, f -> f.getFluid().isSame(SinguloFluids.get("liquid_nitrogen"))) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    /** 入口（冷媒カートリッジ）と出口（空のカートリッジ）。 */
    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 ? CoolantCartridges.isFilled(stack) : CoolantCartridges.isEmptyCartridge(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** ホッパーなど: 入口へ入れて、出口から取り出す。 */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == 0 ? items.insertItem(0, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 1 ? items.extractItem(1, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && items.isItemValid(0, stack);
        }
    };
    private int hot;
    private double rate;
    private boolean standby;
    /** 0.10.0 より前（発電機だったころ）に置かれたタービン。最初に画面を開いた人に、蓄電装置に変わったことを一度だけ知らせる。 */
    private boolean legacyNotice;

    public CryogenicTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.CRYOGENIC_TURBINE.get(), pos, state);
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    public IFluidHandler tank() {
        return tank;
    }

    public IItemHandler automationItems() {
        return automation;
    }

    public ItemStackHandler items() {
        return items;
    }

    /** 今の高温源での、液体窒素 1 mB から取り戻せる電力（FE。高温源がなければ 0）。 */
    double fePerMb() {
        if (hot <= COOLANT_TEMPERATURE) {
            return 0;
        }
        double f = Math.min(1.0, (hot - COOLANT_TEMPERATURE) / (double) (REFERENCE_HOT - COOLANT_TEMPERATURE));
        return NITROGEN_FE_PER_MB * ServerConfig.MACHINE_ENERGY_MULTIPLIER.get() * EFFICIENCY * f;
    }

    /** 詰めた電力のうち、今の高温源で取り戻せる割合（%）。 */
    int efficiencyPercent() {
        if (hot <= COOLANT_TEMPERATURE) {
            return 0;
        }
        return (int) Math.round(100 * EFFICIENCY * Math.min(1.0, (hot - COOLANT_TEMPERATURE) / (double) (REFERENCE_HOT - COOLANT_TEMPERATURE)));
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
        // 入口の冷媒カートリッジを、タンクに空ける（空のカートリッジは出口へ）
        ItemStack in = items.getStackInSlot(0), out = items.getStackInSlot(1);
        if (CoolantCartridges.isFilled(in) && tank.getSpace() >= CoolantCartridges.AMOUNT
                && (out.isEmpty() || CoolantCartridges.isEmptyCartridge(out) && out.getCount() < out.getMaxStackSize())) {
            tank.fill(new FluidStack(SinguloFluids.get("liquid_nitrogen"), CoolantCartridges.AMOUNT), IFluidHandler.FluidAction.EXECUTE);
            items.extractItem(0, 1, false);
            items.insertItem(1, new ItemStack(CoolantCartridges.empty()), false);
        }
        rate = 0;
        // 蓄電がいっぱいなら待機（窒素を捨てない）。75%を割ったら再開する
        double fill = (double) energy.getEnergyStored() / energy.getMaxEnergyStored();
        if (standby ? fill < 0.75 : fill >= 0.98) {
            standby = !standby;
        }
        double perMb = fePerMb();
        if (!standby && perMb > 0 && tank.getFluidAmount() > 0) {
            // 出力の上限まで、液体窒素を使う（1 mB 単位）
            int use = (int) Math.min(tank.getFluidAmount(), Math.max(1, Math.floor(ServerConfig.CRYO_TURBINE_OUTPUT.get() / perMb)));
            tank.drain(use, IFluidHandler.FluidAction.EXECUTE);
            rate = use * perMb;
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
    public SyncedInts slotsData() {
        return SyncedInts.server(GeneratorSlotsMenu.COUNT, i -> switch (i) {
            case GeneratorSlotsMenu.D_HOT -> hot;
            case GeneratorSlotsMenu.D_RATE_X100 -> (int) Math.round(rate * 100);
            case GeneratorSlotsMenu.D_ENERGY -> energy.getEnergyStored();
            case GeneratorSlotsMenu.D_CAPACITY -> energy.getMaxEnergyStored();
            case GeneratorSlotsMenu.D_FLUID -> tank.getFluidAmount();
            case GeneratorSlotsMenu.D_FLUID_CAP -> tank.getCapacity();
            case GeneratorSlotsMenu.D_EXTRA -> efficiencyPercent();
            case GeneratorSlotsMenu.D_STANDBY -> standby ? 1 : 0;
            default -> 0;
        });
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GeneratorSlotsMenu(containerId, inventory, worldPosition, GeneratorSlotsMenu.KIND_TURBINE, items, slotsData());
    }

    @Override
    public void openMenu(ServerPlayer player) {
        noticeIfLegacy(player);
        net.minecraftforge.network.NetworkHooks.openScreen(player, this, buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeVarInt(GeneratorSlotsMenu.KIND_TURBINE);
        });
    }

    /** 0.10.0 より前のタービンなら、蓄電装置に変わったことを一度だけ知らせる。 */
    public void noticeIfLegacy(ServerPlayer player) {
        if (legacyNotice) {
            legacyNotice = false;
            setChanged();
            player.sendSystemMessage(Component.translatable("message.singulo.cryogenic_turbine.now_storage")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
    }

    @Override
    public void onBroken(Level level) {
        for (int i = 0; i < items.getSlots(); i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("tank", tank.writeToNBT(new CompoundTag()));
        tag.put("items", items.serializeNBT());
        if (legacyNotice) {
            tag.putBoolean("legacy_notice", true);
        }
    }

    /** 0.10.0 より前に置かれたタービンか（まだ知らせていない）。 */
    public boolean legacyNotice() {
        return legacyNotice;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setEnergy(tag.getInt("energy"));
        tank.readFromNBT(tag.getCompound("tank"));
        if (tag.contains("items")) {
            items.deserializeNBT(tag.getCompound("items"));
        }
        // 前の版はスロットを持たなかった（タンクだけを書いていた）
        legacyNotice = tag.getBoolean("legacy_notice") || tag.contains("tank") && !tag.contains("items");
    }
}
