package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * SMESセル（超伝導磁気エネルギー貯蔵）。10 MFE を蓄える。正面から出し、ほかの5面から受け取る。
 * 右クリックで画面を開き、蓄電量と1 tick あたりの出し入れを見られる。
 * 画面の充電の枠: 「充電する物を入れる枠」の物を蓄えた電力で充電し（出し入れの速さまで。正面から出す分と合わせる）、
 * 満タンになったら「充電済みの物が出てくる枠」へ移す。ホッパーなどからも、入れる枠へ入れ、出てくる枠から取り出せる。
 * 壊すと枠の物を落とす。
 */
public class SmesCellBlockEntity extends BlockEntity implements AbstractMachineBlock.MenuOpener,
        net.minecraft.world.MenuProvider, AbstractMachineBlock.BreakListener {
    /** 充電の枠: 0 は入れる枠、1 は充電済みの物が出てくる枠。 */
    public static final int CHARGE_IN = 0, CHARGE_OUT = 1;
    public static final int CAPACITY = 10_000_000;
    public static final int RATE = 100_000;

    private final SinguloEnergyStorage energy;
    private final int rate;
    private final IEnergyStorage input = new Side(true);
    private final IEnergyStorage output = new Side(false);
    /** この tick に受け取った量と、直前の tick に受け取った量・送り出した量（画面用）。 */
    private int inThisTick;
    private int lastIn;
    private int lastOut;
    private int externallyExtracted;
    private final net.neoforged.neoforge.items.ItemStackHandler charge = new net.neoforged.neoforge.items.ItemStackHandler(2) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return slot == CHARGE_IN && io.github.genichimaruo.singulo.energy.ItemCharging.chargeable(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** ホッパーなど用: 入れる枠へ入れるだけ、出てくる枠から取り出すだけ。 */
    private final net.neoforged.neoforge.items.IItemHandler chargeAutomation = new net.neoforged.neoforge.items.IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            return charge.getStackInSlot(slot);
        }

        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            return slot == CHARGE_IN ? charge.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == CHARGE_OUT ? charge.extractItem(slot, amount, simulate) : net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return charge.isItemValid(slot, stack);
        }
    };

    public SmesCellBlockEntity(BlockPos pos, BlockState state) {
        this(SinguloBlockEntities.SMES_CELL.get(), pos, state, CAPACITY, RATE);
    }

    protected SmesCellBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos,
                                  BlockState state, int capacity, int rate) {
        super(type, pos, state);
        this.energy = new SinguloEnergyStorage(capacity, rate, rate, this::setChanged);
        this.rate = rate;
    }

    /** SMESモジュール（段階4）。2 GFE を蓄え、100 MFE/t で出し入れする。 */
    public static class Module extends SmesCellBlockEntity {
        public static final int MODULE_CAPACITY = 2_000_000_000;
        public static final int MODULE_RATE = 100_000_000;

        public Module(BlockPos pos, BlockState state) {
            super(SinguloBlockEntities.SMES_MODULE.get(), pos, state, MODULE_CAPACITY, MODULE_RATE);
        }
    }

    public SinguloEnergyStorage energy() {
        return energy;
    }

    /** 充電の枠（画面用）。 */
    public net.neoforged.neoforge.items.ItemStackHandler chargeSlots() {
        return charge;
    }

    /** 充電の枠（ホッパーなど用。入れる枠へ入れ、出てくる枠から取り出す）。 */
    public net.neoforged.neoforge.items.IItemHandler chargeAutomation() {
        return chargeAutomation;
    }

    @Override
    public void onBroken(Level level) {
        for (int i = 0; i < charge.getSlots(); i++) {
            Block.popResource(level, worldPosition, charge.getStackInSlot(i));
        }
    }

    @Nullable
    public IEnergyStorage energyFor(@Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return side == getBlockState().getValue(AbstractMachineBlock.FACING) ? output : input;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmesCellBlockEntity be) {
        Direction front = state.getValue(AbstractMachineBlock.FACING);
        boolean active = be.externallyExtracted > 0;
        be.lastIn = be.inThisTick;
        be.inThisTick = 0;
        be.lastOut = be.externallyExtracted;
        be.externallyExtracted = 0;
        if (be.energy.getEnergyStored() > 0) {
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(front), front.getOpposite());
            if (target != null && target.canReceive()) {
                int sent = target.receiveEnergy(Math.min(be.energy.getEnergyStored(), be.rate), false);
                if (sent > 0) {
                    be.energy.consume(sent);
                    be.lastOut += sent;
                    active = true;
                }
            }
        }
        // 充電の枠の物を充電する（出し入れの速さのうち、正面から出した残りまで）
        long used = io.github.genichimaruo.singulo.energy.ItemCharging.tick(be.charge, CHARGE_IN, CHARGE_OUT,
                Math.min(be.energy.getEnergyStored(), Math.max(0, be.rate - be.lastOut)));
        if (used > 0) {
            be.energy.consume((int) used);
            be.lastOut += (int) used;
            active = true;
        }
        if (state.getValue(AbstractMachineBlock.LIT) != active && level.getGameTime() % 10 == 0) {
            level.setBlock(pos, state.setValue(AbstractMachineBlock.LIT, active), Block.UPDATE_ALL);
        }
    }

    @Override
    public void openMenu(ServerPlayer player) {
        player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv,
                                                                          net.minecraft.world.entity.player.Player player) {
        return new SmesMenu(id, inv, worldPosition, charge, SyncedInts.server(SmesMenu.COUNT, i -> switch (i) {
            case SmesMenu.D_STORED -> energy.getEnergyStored();
            case SmesMenu.D_CAPACITY -> energy.getMaxEnergyStored();
            case SmesMenu.D_IN -> lastIn;
            case SmesMenu.D_OUT -> lastOut;
            case SmesMenu.D_RATE -> rate;
            default -> 0;
        }));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("charge", charge.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("energy"));
        if (tag.contains("charge")) {
            charge.deserializeNBT(registries, tag.getCompound("charge"));
        }
    }

    /** 受け取り専用・出力専用の口。 */
    private final class Side implements IEnergyStorage {
        private final boolean receive;

        Side(boolean receive) {
            this.receive = receive;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            if (!receive) {
                return 0;
            }
            int got = energy.receiveEnergy(amount, simulate);
            if (!simulate) {
                inThisTick += got;
            }
            return got;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            int sent = receive ? 0 : energy.extractEnergy(amount, simulate);
            if (!simulate) externallyExtracted += sent;
            return sent;
        }

        @Override
        public int getEnergyStored() {
            return energy.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return energy.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return !receive;
        }

        @Override
        public boolean canReceive() {
            return receive;
        }
    }
}
