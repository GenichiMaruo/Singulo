package io.github.genichimaruo.singulo.ruin.lab;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.Singulo;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 研究所の飲み物の自動販売機（2ブロックの高さ。上の見本の棚が光る）。正面は左から、缶ソーダ・スポーツドリンク・エナジードリンクの3列。
 * エメラルドを持って正面を右クリックすると、押した列の飲み物が取り出し口から出てくる（値段は {@link #PRICES}）。
 * 研究所に置かれたものは、施設の電力が戻るまで止まっている（powered=false。{@link LabPower#restore} で動き出す）。
 * 自分で置いたものは、はじめから動いている。
 */
public class VendingMachineBlock extends AndroidCapsuleBlock {
    public static final MapCodec<VendingMachineBlock> CODEC = simpleCodec(VendingMachineBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** 正面の左から並ぶ飲み物と、その値段（エメラルド）。 */
    public static final String[] DRINKS = {"canned_soda", "sports_drink", "energy_drink"};
    public static final int[] PRICES = {1, 1, 2};

    public VendingMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** 正面を見たときの、左から何列目か（0〜2）。正面以外を押したときは -1。 */
    public static int column(BlockState state, BlockPos pos, Vec3 hit, Direction face) {
        Direction front = state.getValue(FACING);
        if (face != front) {
            return -1;
        }
        Direction right = front.getOpposite().getClockWise();         // 正面に向き合った人の右手
        double local = right.getAxis() == Direction.Axis.X ? hit.x - pos.getX() : hit.z - pos.getZ();
        double t = right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? local : 1 - local;
        return Math.min(2, Math.max(0, (int) (t * 3)));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!stack.is(Items.EMERALD)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            buy(state, level, pos, player, stack, hit);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable(state.getValue(POWERED) ? "lab.singulo.vending.how" : "lab.singulo.vending"), true);
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** エメラルドで、押した列の飲み物を買う。買えたら飲み物を返す（止まっている・足りない・正面以外なら null）。 */
    @Nullable
    public static ItemStack buy(BlockState state, Level level, BlockPos pos, Player player, ItemStack emeralds, BlockHitResult hit) {
        int col = column(state, pos, hit.getLocation(), hit.getDirection());
        String fail = !state.getValue(POWERED) ? "lab.singulo.vending" : col < 0 ? "lab.singulo.vending.how"
                : emeralds.getCount() < PRICES[col] ? "lab.singulo.vending.short" : null;
        if (fail != null) {
            player.displayClientMessage(col >= 0 && fail.endsWith("short") ? Component.translatable(fail, PRICES[col]) : Component.translatable(fail), true);
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.2F);
            return null;
        }
        emeralds.consume(PRICES[col], player);
        ItemStack drink = new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(DRINKS[col])));
        // 下半分の取り出し口から、正面へ出す
        BlockPos lower = state.getValue(HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? pos : pos.below();
        Block.popResourceFromFace(level, lower, state.getValue(FACING), drink.copy());
        level.playSound(null, lower, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.8F, 0.9F);
        level.playSound(null, lower, SoundEvents.CHAIN_FALL, SoundSource.BLOCKS, 0.5F, 1.4F);
        return drink;
    }
}
