package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.machine.SimpleMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** ドローン輸送のブロック。どちらも置いた人が持ち主になる（ドローンの網は持ち主ごと）。 */
public final class DroneBlocks {
    private DroneBlocks() {}

    /** ドローン・ステーション。 */
    public static class Station extends SimpleMachineBlock<DroneStationBlockEntity> {
        public Station(Properties properties) {
            super(properties, SinguloBlockEntities.DRONE_STATION, DroneStationBlockEntity::new, DroneStationBlockEntity::serverTick);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof DroneStationBlockEntity station) {
                station.setOwner(player.getUUID());
            }
        }
    }

    /** 液体のドローン発着ポート。コンパレーターに、受け取りのタンクの埋まり具合を出す。 */
    public static class FluidPort extends SimpleMachineBlock<FluidDronePortBlockEntity> {
        public FluidPort(Properties properties) {
            super(properties, SinguloBlockEntities.FLUID_DRONE_PORT, FluidDronePortBlockEntity::new, DronePortBlockEntity::serverTick);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof FluidDronePortBlockEntity port) {
                port.setOwner(player.getUUID());
            }
        }

        @Override
        protected boolean hasAnalogOutputSignal(BlockState state) {
            return true;
        }

        /** 壊して中身ごと持ち出したポート: タンクの中身を出す（置き直すと戻る）。 */
        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                    java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
            super.appendHoverText(stack, context, lines, flag);
            var send = stack.get(io.github.genichimaruo.singulo.registry.SinguloComponents.PORT_SEND_FLUID.get());
            var receive = stack.get(io.github.genichimaruo.singulo.registry.SinguloComponents.PORT_RECEIVE_FLUID.get());
            for (var e : new Object[][]{{"send", send}, {"receive", receive}}) {
                if (e[1] instanceof net.neoforged.neoforge.fluids.SimpleFluidContent c && !c.isEmpty()) {
                    var f = c.copy();
                    lines.add(net.minecraft.network.chat.Component.translatable("tooltip.singulo.fluid_drone_port." + e[0], f.getHoverName(),
                            String.format("%,d", f.getAmount())).withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }
        }

        @Override
        protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
            if (!(level.getBlockEntity(pos) instanceof FluidDronePortBlockEntity port) || port.receiveTank().isEmpty()) {
                return 0;
            }
            return 1 + Math.round(14F * port.receiveTank().getFluidAmount() / FluidDronePortBlockEntity.RECEIVE_CAPACITY);
        }
    }

    /** ドローン発着ポート。コンパレーターに、受け取りの欄の埋まり具合を出す。 */
    public static class Port extends SimpleMachineBlock<DronePortBlockEntity> {
        public Port(Properties properties) {
            super(properties, SinguloBlockEntities.DRONE_PORT, DronePortBlockEntity::new, DronePortBlockEntity::serverTick);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof DronePortBlockEntity port) {
                port.setOwner(player.getUUID());
            }
        }

        @Override
        protected boolean hasAnalogOutputSignal(BlockState state) {
            return true;
        }

        @Override
        protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
            if (!(level.getBlockEntity(pos) instanceof DronePortBlockEntity port)) {
                return 0;
            }
            float sum = 0;
            for (int i = 0; i < DronePortBlockEntity.RECEIVE; i++) {
                ItemStack s = port.items().getStackInSlot(DronePortBlockEntity.SLOT_RECEIVE + i);
                sum += s.isEmpty() ? 0 : s.getCount() / (float) s.getMaxStackSize();
            }
            return sum <= 0 ? 0 : 1 + Math.round(14 * sum / DronePortBlockEntity.RECEIVE);
        }
    }
}
