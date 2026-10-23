package io.github.genichimaruo.singulo.ruin.lab;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * コーヒーメーカー。カカオ豆を1つ入れると、少しして3杯分のコーヒーが淹れ上がる（淹れている間は湯気が立つ）。
 * ポットにコーヒーがあるとき、ガラス瓶で右クリックするとコーヒーを1杯くめる。研究所のものは、はじめから3杯分入っている。
 */
public class CoffeeMakerBlock extends LabDecorBlock {
    public static final MapCodec<CoffeeMakerBlock> CODEC = simpleCodec(p -> new CoffeeMakerBlock(p));
    /** ポットに残っている杯数と、淹れている途中か。 */
    public static final IntegerProperty CUPS = IntegerProperty.create("cups", 0, 3);
    public static final BooleanProperty BREWING = BooleanProperty.create("brewing");
    /** 淹れ上がるまで（tick）。 */
    public static final int BREW_TIME = 100;

    public CoffeeMakerBlock(Properties properties) {
        super(properties, Block.box(4, 0, 4, 12, 13, 13), null, null);
        registerDefaultState(defaultBlockState().setValue(CUPS, 0).setValue(BREWING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CUPS, BREWING);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.is(Items.COCOA_BEANS) && !state.getValue(BREWING) && state.getValue(CUPS) == 0) {
            if (!level.isClientSide) {
                stack.consume(1, player);
                level.setBlock(pos, state.setValue(BREWING, true), Block.UPDATE_ALL);
                level.scheduleTick(pos, this, BREW_TIME);
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Items.GLASS_BOTTLE) && state.getValue(CUPS) > 0) {
            if (!level.isClientSide) {
                stack.consume(1, player);
                ItemStack coffee = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("coffee")));
                if (!player.getInventory().add(coffee)) {
                    player.drop(coffee, false);
                }
                level.setBlock(pos, state.setValue(CUPS, state.getValue(CUPS) - 1), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 0.9F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 素手などで押すと、今の様子を一言で出す（空・淹れている・残りの杯数）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int cups = state.getValue(CUPS);
            player.displayClientMessage(state.getValue(BREWING) ? Component.translatable("lab.singulo.coffee.brewing")
                    : cups > 0 ? Component.translatable("lab.singulo.coffee.ready", cups) : Component.translatable("lab.singulo.coffee.empty"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(BREWING)) {
            level.setBlock(pos, state.setValue(BREWING, false).setValue(CUPS, 3), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5F, 1.4F);
        }
    }

    /** 淹れている間は湯気、淹れたてのポットからもときどき湯気が立つ。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(BREWING) || state.getValue(CUPS) > 0 && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2, pos.getY() + 0.85,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2, 0, 0.02, 0);
        }
    }
}
