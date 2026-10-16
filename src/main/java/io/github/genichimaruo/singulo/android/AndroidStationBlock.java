package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.machine.SimpleMachineBlock;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** アンドロイド組立ステーション。置いた人が持ち主になる（印刷した護衛のアンドロイドの持ち主）。 */
public class AndroidStationBlock extends SimpleMachineBlock<AndroidStationBlockEntity> {
    public AndroidStationBlock(Properties properties) {
        super(properties, SinguloBlockEntities.ANDROID_STATION, AndroidStationBlockEntity::new, AndroidStationBlockEntity::serverTick);
    }

    /** コンパレーターに、アンドロイドの体力（1〜15、いなければ 0）を出す。 */
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AndroidStationBlockEntity station ? station.signal() : 0;
    }

    /** 警報: 巡回中のアンドロイドが敵の群れ・ボスを見つけると、しばらくレッドストーン信号を出す。 */
    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return level.getBlockEntity(pos) instanceof AndroidStationBlockEntity station && station.alarming() ? 15 : 0;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof AndroidStationBlockEntity station) {
            station.setOwner(player.getUUID());
        }
    }
}
