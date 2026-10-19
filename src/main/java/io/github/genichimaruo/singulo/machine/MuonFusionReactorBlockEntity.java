package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * ミュオン触媒核融合炉（第2段階の発電機）。水から取り出した重水素を、ミュオン（電子の約200倍重い粒子）が原子核どうしを
 * 近づけることで常温のまま融合させる。ミュオンはいつか原子核にくっついて働かなくなるので、ミュオン触媒は使ううちに失活する。
 * <ul>
 * <li>ミュオン触媒の使用回数を毎tick {@link #USES_PER_TICK} 回ずつ使う（新品なら寿命20分の半分の10分。使いかけは残りの分だけ。
 *     触媒の寿命の設定にも連動する）。使い切ると、失活したミュオン触媒が出口に出る（触媒反応器でミュオン束に戻せる）。</li>
 * <li>水を {@link #WATER_INTERVAL} tick ごとに 1 mB 使う（タンク1杯で約27分。バケツで右クリックしても、パイプでも入る）。</li>
 * <li>出力は fusionReactorOutput（既定 4 kFE/t）。蓄電がほぼいっぱいの間は待機して、触媒も水も減らさない。</li>
 * </ul>
 * 電力の元は使い捨てる触媒（銅・レッドストーン・セラミック・観測ログとミュオン）なので、電力だけを回して増やすことはできない。
 */
public class MuonFusionReactorBlockEntity extends BlockEntity implements MenuProvider, AbstractMachineBlock.MenuOpener,
        AbstractMachineBlock.BreakListener, GeneratorSlotsMenu.Owner {
    /** 触媒の使用回数を1 tickに使う回数（核融合炉は触媒を2倍の速さで消耗する）。 */
    public static final int USES_PER_TICK = 2;
    /** 水を 1 mB 使う間隔（tick）。 */
    public static final int WATER_INTERVAL = 4;
    static final int PUSH_PER_TICK = 20_000;

    private final SinguloEnergyStorage energy = new SinguloEnergyStorage(400_000, 0, PUSH_PER_TICK, this::setChanged);
    private final FluidTank tank = new FluidTank(MachineType.TANK_CAPACITY, f -> f.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    /** 入口（ミュオン触媒）と出口（失活したミュオン触媒）。 */
    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(slot == 0 ? catalyst() : spent());
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
    /** 炉の中で働いている触媒の残り（tick。0 なら入っていない）と、入れたときの長さ（残りの割合の表示用）。 */
    private int burn;
    private int burnTotal;
    private double rate;
    private boolean standby;

    public MuonFusionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.MUON_FUSION_REACTOR.get(), pos, state);
    }

    static Item catalyst() {
        return BuiltInRegistries.ITEM.get(Singulo.id("muon_catalyst"));
    }

    static Item spent() {
        return BuiltInRegistries.ITEM.get(Singulo.id("spent_muon_catalyst"));
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

    /** 働いている触媒の残り（tick）。 */
    public int burn() {
        return burn;
    }

    /** テスト用: 働いている触媒の残りを決め打ちにする。 */
    public void setBurnForTest(int ticks) {
        burn = ticks;
        burnTotal = Math.max(burnTotal, ticks);
    }

    /** 触媒1個が炉の中で燃える長さ（tick）。残りの使用回数を {@link #USES_PER_TICK} で割った分（端数は切り上げ）。 */
    public static int burnTicks(ItemStack catalyst) {
        int remaining = io.github.genichimaruo.singulo.item.UsesHelper.remaining(catalyst);
        return Math.max(1, (remaining + USES_PER_TICK - 1) / USES_PER_TICK);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MuonFusionReactorBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        rate = 0;
        double fill = (double) energy.getEnergyStored() / energy.getMaxEnergyStored();
        if (standby ? fill < 0.75 : fill >= 0.98) {
            standby = !standby;
        }
        if (!standby && burn <= 0 && items.getStackInSlot(0).is(catalyst()) && tank.getFluidAmount() >= 1
                && items.insertItem(1, new ItemStack(spent()), true).isEmpty()) {
            // 新しい触媒を炉へ入れる（失活したら出口へ出すので、出口が空いているときだけ）。残りの使用回数の分だけ燃える
            ItemStack fuel = items.extractItem(0, 1, false);
            burn = burnTicks(fuel);
            burnTotal = burn;
        }
        if (!standby && burn > 0 && tank.getFluidAmount() >= 1) {
            if (burn % WATER_INTERVAL == 0) {
                tank.drain(1, IFluidHandler.FluidAction.EXECUTE);
            }
            rate = ServerConfig.FUSION_REACTOR_OUTPUT.get() * ServerConfig.GENERATOR_OUTPUT_MULTIPLIER.get();
            energy.generate((int) rate);
            if (--burn == 0) {
                items.insertItem(1, new ItemStack(spent()), false);     // 触媒が失活した
            }
        }
        for (Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) {
                break;
            }
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
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
            case GeneratorSlotsMenu.D_RATE_X100 -> (int) Math.round(rate * 100);
            case GeneratorSlotsMenu.D_ENERGY -> energy.getEnergyStored();
            case GeneratorSlotsMenu.D_CAPACITY -> energy.getMaxEnergyStored();
            case GeneratorSlotsMenu.D_FLUID -> tank.getFluidAmount();
            case GeneratorSlotsMenu.D_FLUID_CAP -> tank.getCapacity();
            case GeneratorSlotsMenu.D_EXTRA -> burn > 0 ? (int) Math.ceil(1000.0 * burn / Math.max(1, burnTotal)) : -1;
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
        return new GeneratorSlotsMenu(containerId, inventory, worldPosition, GeneratorSlotsMenu.KIND_FUSION, items, slotsData());
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.openMenu(this, buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeVarInt(GeneratorSlotsMenu.KIND_FUSION);
        });
    }

    @Override
    public void onBroken(Level level) {
        for (int i = 0; i < items.getSlots(); i++) {
            Block.popResource(level, worldPosition, items.getStackInSlot(i));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("burn", burn);
        tag.putInt("burn_total", burnTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("energy"));
        tank.readFromNBT(registries, tag.getCompound("tank"));
        if (tag.contains("items")) {
            items.deserializeNBT(registries, tag.getCompound("items"));
        }
        burn = tag.getInt("burn");
        burnTotal = tag.contains("burn_total") ? tag.getInt("burn_total") : Math.max(burn, 12_000);
    }
}
