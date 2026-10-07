package io.github.genichimaruo.singulo.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 1マスの装置の共通部分。正面の向きと、稼働中の発光（lit）、上位装置の恩恵（boosted）を持つ。
 * boosted はティプラー・シリンダーの時間加速か、イベントホライズン・シールドの守りの中にあるとき true になり、
 * 外装にエネルギーの流れの模様が浮かび上がる。
 */
public abstract class AbstractMachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty BOOSTED = BooleanProperty.create("boosted");

    protected AbstractMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(BOOSTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, BOOSTED);
    }

    /** その場所の装置が上位装置の恩恵を受けているか。 */
    public static boolean boostedAt(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ShieldTowerBlockEntity) {
            return false;
        }
        return TimeFields.state(level, pos) == 1
                || ShieldTowerBlockEntity.shielded(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), 4);
    }

    /** 恩恵の有無をブロックの状態に反映する。 */
    public static void updateBoost(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.hasProperty(BOOSTED)) {
            return;
        }
        boolean b = boostedAt(level, pos);
        if (s.getValue(BOOSTED) != b) {
            level.setBlock(pos, s.setValue(BOOSTED, b), Block.UPDATE_CLIENTS);
        }
    }

    /** サーバーの毎tickの処理に、20 tick ごとの恩恵の確認を足す（場所ごとにずらす）。 */
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
            withBoost(net.minecraft.world.level.block.entity.BlockEntityTicker<T> inner) {
        return (l, p, s, be) -> {
            inner.tick(l, p, s, be);
            if ((l.getGameTime() + p.hashCode()) % 20 == 0) {
                updateBoost(l, p);
            }
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // マルチブロックのコントローラーは、完成するまで画面を開かない（未完成と知らせる）
        var kind = io.github.genichimaruo.singulo.multiblock.Blueprints.kindOf(state.getBlock());
        if (kind != null && io.github.genichimaruo.singulo.multiblock.Blueprints.formedSize(level, pos, kind) <= 0) {
            if (!level.isClientSide && player.getMainHandItem().isEmpty()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.singulo.multiblock.not_formed",
                        net.minecraft.network.chat.Component.translatable(state.getBlock().getDescriptionId())), true);
            }
            return player.getMainHandItem().isEmpty() ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MenuOpener opener) {
            opener.openMenu(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 手に持ったアイテムで右クリックしたとき、ブロックエンティティが受け取れば使う（触媒を入れるなど）。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // クライアントは素通しし、使うかどうかはサーバーが決める（使わなければ通常どおりGUIを開く）
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MenuOpener opener && opener.useItem(serverPlayer, stack, hand)) {
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BreakListener listener) {
                listener.onBroken(level);
            } else if (be instanceof MachineBlockEntity machine) {
                machine.dropContents(level, pos);
            } else if (be instanceof io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity reactor) {
                reactor.dropContents(level);
            } else if (be instanceof CatalystDeviceBlockEntity device) {
                device.onBroken(level);
            } else if (be instanceof CosmicMuonCollectorBlockEntity collector) {
                Block.popResource(level, pos, collector.output().getStackInSlot(0));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** ブロックが壊されたとき（チャンクの読み込み解除では呼ばれない）に後始末をするブロックエンティティ。 */
    public interface BreakListener {
        void onBroken(Level level);
    }

    /** GUIを持つ（または右クリックで状態を表示する）ブロックエンティティ。 */
    public interface MenuOpener {
        void openMenu(ServerPlayer player);

        /** 手に持ったアイテムを使う。使ったら true。 */
        default boolean useItem(ServerPlayer player, ItemStack stack, InteractionHand hand) {
            return false;
        }
    }
}
