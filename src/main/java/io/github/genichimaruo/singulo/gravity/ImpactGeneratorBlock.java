package io.github.genichimaruo.singulo.gravity;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 衝撃発電パネル。上に落ちても落下ダメージを受けず、落ちた高さに応じて発電する（{@link ImpactGeneratorBlockEntity}）。
 * ためた電力は、接している重力パネルの集まりへ配る。
 */
public class ImpactGeneratorBlock extends GravityReceiverBlock {
    public static final MapCodec<ImpactGeneratorBlock> CODEC = simpleCodec(ImpactGeneratorBlock::new);

    public ImpactGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ImpactGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SinguloBlockEntities.IMPACT_GENERATOR.get(),
                GravityReceiverBlockEntity::serverTick);
    }

    /** 落ちてきた: 落下ダメージを与えず（標準の処理を呼ばない）、高さに応じて発電する。 */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity
                && level.getBlockEntity(pos) instanceof ImpactGeneratorBlockEntity be && be.absorb(fallDistance) > 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5,
                    Math.min(24, 4 + (int) fallDistance * 2), 0.3, 0.02, 0.3, 0.1);
            level.playSound(null, pos, io.github.genichimaruo.singulo.registry.SinguloSounds.get("impact_generator.absorb"), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        entity.resetFallDistance();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ImpactGeneratorBlockEntity be) {
            player.displayClientMessage(Component.translatable("message.singulo.impact_generator.status",
                    be.stored(), ImpactGeneratorBlockEntity.CAPACITY, be.activePanels(), be.panels()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
