package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.machine.SideConfig;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * 液体のドローン発着ポート。物の発着ポートと同じ扉・昇降台・ドローンの受け渡しで、荷物の代わりに液体を送る。
 * 送るタンクの液体を空の液体カプセルに詰めて（1便に最大 {@link FluidCapsuleItem#CAPACITY} mB、1種類）、
 * 同じ持ち主の液体のポートへ送る。届いたカプセルは受け取りのタンクへ注ぎ、空になったカプセルは空き箱の欄へ戻す。
 * <p>
 * パイプや装置は面の設定で出し入れする: 「入力」の面から送るタンクへ入れ、「出力」の面から受け取りのタンクを取り出す。
 * 自動排出がオンの「出力」の面へは、受け取りのタンクの液体を押し出す。手のバケツでも出し入れできる。
 * 荷物の正は、物のポートと同じく便の記録が持つ（カプセルに詰めた液体は、届くまで便の記録の中にある）。
 */
public class FluidDronePortBlockEntity extends DronePortBlockEntity {
    public static final int SEND_CAPACITY = FluidCapsuleItem.CAPACITY;
    public static final int RECEIVE_CAPACITY = 2 * FluidCapsuleItem.CAPACITY;
    /** 自動発送のしきい値（AUTO_THRESHOLDS の番号ごと、mB）: しない・1バケツ・16・32バケツ・満杯（カプセル1つぶん）。 */
    private static final int[] AUTO_FLUID = {0, 1_000, 16_000, 32_000, SEND_CAPACITY};

    private final FluidTank send = new FluidTank(SEND_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            cargoChanged();
        }
    };
    private final FluidTank receive = new FluidTank(RECEIVE_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler[] fluidViews = new IFluidHandler[Direction.values().length];
    private final IFluidHandler unsidedFluids = new SidedTanks(null);

    public FluidDronePortBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.FLUID_DRONE_PORT.get(), pos, state);
    }

    /** 自動発送のしきい値（mB）。 */
    public static int autoThreshold(int mode) {
        return AUTO_FLUID[Math.floorMod(mode, AUTO_FLUID.length)];
    }

    public FluidTank sendTank() {
        return send;
    }

    public FluidTank receiveTank() {
        return receive;
    }

    @Override
    public boolean fluidPort() {
        return true;
    }

    @Override
    protected boolean acceptsCrate(ItemStack stack) {
        return DroneCrateItem.isFluid(stack) && DroneCrateItem.tier(stack) > 0 && !DroneCrateItem.packed(stack);
    }

    @Override
    protected boolean acceptsCargo(ItemStack stack) {
        return false;
    }

    @Override
    protected boolean hasCargo() {
        return !send.isEmpty();
    }

    @Override
    protected ItemStack packCargo(ItemStack crate) {
        FluidStack fluid = send.drain(FluidCapsuleItem.CAPACITY, IFluidHandler.FluidAction.EXECUTE);
        return FluidCapsuleItem.pack(crate, fluid);
    }

    @Override
    protected boolean restoreCargo(ItemStack packed, boolean simulate) {
        FluidStack fluid = FluidCapsuleItem.fluid(packed);
        if (fluid.isEmpty()) {
            return true;
        }
        if (send.fill(fluid, IFluidHandler.FluidAction.SIMULATE) < fluid.getAmount()) {
            return false;
        }
        if (!simulate) {
            send.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        }
        return true;
    }

    @Override
    protected void unpackOne(int slot, ItemStack crate) {
        FluidStack fluid = FluidCapsuleItem.fluid(crate);
        int filled = receive.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            ItemStack next = crate.copy();
            FluidCapsuleItem.setFluid(next, io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(fluid, fluid.getAmount() - filled));
            items().setStackInSlot(slot, next);
        }
    }

    @Override
    protected boolean autoReady(int mode) {
        int threshold = autoThreshold(mode);
        return threshold > 0 && send.getFluidAmount() >= threshold;
    }

    @Override
    protected int fluidData(int index) {
        return switch (index) {
            case D_SEND_FLUID -> id(send.getFluid());
            case D_SEND_AMOUNT -> send.getFluidAmount();
            case D_RECV_FLUID -> id(receive.getFluid());
            case D_RECV_AMOUNT -> receive.getFluidAmount();
            default -> 0;
        };
    }

    private static int id(FluidStack fluid) {
        return fluid.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(fluid.getFluid()) + 1;
    }

    @Override
    protected void ejectExtra(ServerLevel level, Direction dir) {
        if (receive.isEmpty()) {
            return;
        }
        IFluidHandler dest = Capabilities.get(level, Capabilities.FluidHandler.BLOCK, getBlockPos().relative(dir), dir.getOpposite());
        if (dest != null) {
            FluidUtil.tryFluidTransfer(dest, receive, Integer.MAX_VALUE, true);
        }
    }

    // ------------------------------------------------------------------ 液体の口

    /**
     * 面から見たタンク: 「入力」の面からは送るタンクへ入れられ、「出力」の面からは受け取りのタンクを取り出せる。
     * side が null（手のバケツなど、面を問わない出し入れ）なら両方。
     */
    public IFluidHandler sidedFluids(@Nullable Direction side) {
        if (side == null) {
            return unsidedFluids;
        }
        // Forge caches LazyOptionals by handler identity. A lookup must not invalidate an existing view.
        if (fluidViews[side.ordinal()] == null) {
            fluidViews[side.ordinal()] = new SidedTanks(side);
        }
        return fluidViews[side.ordinal()];
    }

    /** 面から見たタンク（送るタンクと受け取りのタンクは別なので、パイプからは「タンク」と見なさない）。 */
    private final class SidedTanks implements IFluidHandler, io.github.genichimaruo.singulo.pipe.PipeSplitTanks {
        @Nullable private final Direction side;

        SidedTanks(@Nullable Direction side) {
            this.side = side;
        }

        private boolean mayInsert() {
            return side == null || sides().canInsert(SideConfig.faceOf(facing(), side));
        }

        private boolean mayExtract() {
            return side == null || sides().canExtract(SideConfig.faceOf(facing(), side));
        }

        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? send.getFluid() : receive.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? SEND_CAPACITY : RECEIVE_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && mayInsert();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return mayInsert() ? send.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return mayExtract() ? receive.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return mayExtract() ? receive.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }

    private Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING)
                ? s.getValue(io.github.genichimaruo.singulo.machine.AbstractMachineBlock.FACING) : Direction.NORTH;
    }

    // ------------------------------------------------------------------ アイテムとの受け渡し（壊してもタンクの中身を失わない）

    public void collectComponents(ItemStack stack) {
        if (!send.isEmpty()) io.github.genichimaruo.singulo.registry.SinguloComponents.set(stack,
                io.github.genichimaruo.singulo.registry.SinguloComponents.PORT_SEND_FLUID.get(), send.getFluid().copy());
        if (!receive.isEmpty()) io.github.genichimaruo.singulo.registry.SinguloComponents.set(stack,
                io.github.genichimaruo.singulo.registry.SinguloComponents.PORT_RECEIVE_FLUID.get(), receive.getFluid().copy());
    }

    public void applyComponentsFromItemStack(ItemStack stack) {
        FluidStack s = io.github.genichimaruo.singulo.registry.SinguloComponents.getOrDefault(stack,
                io.github.genichimaruo.singulo.registry.SinguloComponents.PORT_SEND_FLUID.get(), FluidStack.EMPTY);
        FluidStack r = io.github.genichimaruo.singulo.registry.SinguloComponents.getOrDefault(stack,
                io.github.genichimaruo.singulo.registry.SinguloComponents.PORT_RECEIVE_FLUID.get(), FluidStack.EMPTY);
        send.setFluid(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(s, Math.min(SEND_CAPACITY, s.getAmount())));
        receive.setFluid(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(r, Math.min(RECEIVE_CAPACITY, r.getAmount())));
    }

    // ------------------------------------------------------------------ 保存

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("send_tank", send.writeToNBT(new CompoundTag()));
        tag.put("receive_tank", receive.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("send_tank")) {
            send.readFromNBT(tag.getCompound("send_tank"));
        }
        if (tag.contains("receive_tank")) {
            receive.readFromNBT(tag.getCompound("receive_tank"));
        }
    }
}
