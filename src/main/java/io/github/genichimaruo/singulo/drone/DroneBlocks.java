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
