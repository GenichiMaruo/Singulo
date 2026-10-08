package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ホロ投影機（段階1）。マルチブロックのコントローラに使うと、正しい形を半透明のガイドで投影する（もう一度使うと消える）。
 * <ul>
 *   <li>足りないブロックは小さな見本と水色の枠、違うブロックや空けるべき場所のブロックは赤い枠で示す</li>
 *   <li>サーバーは必要なブロックの数と足りない数をチャットに出す</li>
 *   <li>大きさを選べる装置（冷却塔・加速器）は、スニークして使うと大きさが切り替わる</li>
 * </ul>
 * 形はコントローラの正面の反対側（置いたときに見ていた向き）へ伸びる。
 */
public class HoloProjectorItem extends SinguloItem {
    public HoloProjectorItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    /** 今選んでいる大きさ（種類ごとの選択肢の番号）。 */
    public static int sizeIndex(ItemStack stack) {
        return stack.getOrDefault(SinguloComponents.HOLO_SIZE.get(), 0);
    }

    public static int size(ItemStack stack, Blueprints.Kind kind) {
        int[] sizes = kind.sizes();
        return sizes[Math.floorMod(sizeIndex(stack), sizes.length)];
    }

    /** コントローラの向きから、形の伸びる向きを決める。 */
    public static Direction backOf(BlockState state) {
        return state.hasProperty(AbstractMachineBlock.FACING) ? state.getValue(AbstractMachineBlock.FACING).getOpposite()
                : Direction.NORTH;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Blueprints.Kind kind = Blueprints.kindOf(state.getBlock());
        Player player = context.getPlayer();
        if (kind == null) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("holo.singulo.not_controller"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        ItemStack stack = context.getItemInHand();
        boolean resize = player != null && player.isShiftKeyDown();
        if (resize) {
            stack.set(SinguloComponents.HOLO_SIZE.get(), sizeIndex(stack) + 1);
        }
        int size = size(stack, kind);
        Direction back = backOf(state);
        if (level.isClientSide) {
            io.github.genichimaruo.singulo.client.HologramRenderer.toggle(pos, kind, back, size, resize);
        } else if (player != null) {
            report(level, player, kind, pos, back, size);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 必要なブロックと足りない数をチャットに出す。 */
    public static void report(Level level, Player player, Blueprints.Kind kind, BlockPos pos, Direction back, int size) {
        Map<Block, int[]> counts = new LinkedHashMap<>();   // [必要, 足りない]
        int blocking = 0;
        for (Map.Entry<BlockPos, BlockState> e : Blueprints.layout(kind, pos, back, size).entrySet()) {
            BlockState want = e.getValue();
            BlockState have = level.getBlockState(e.getKey());
            if (want.isAir()) {
                if (!have.isAir()) {
                    blocking++;
                }
                continue;
            }
            int[] c = counts.computeIfAbsent(want.getBlock(), b -> new int[2]);
            c[0]++;
            if (!Blueprints.matches(want, have)) {
                c[1]++;
            }
        }
        player.displayClientMessage(Component.translatable("holo.singulo.header",
                Component.translatable("multiblock.singulo." + kind.id()), size).withStyle(ChatFormatting.AQUA), false);
        int missing = 0;
        for (Map.Entry<Block, int[]> e : counts.entrySet()) {
            int[] c = e.getValue();
            missing += c[1];
            player.displayClientMessage(Component.translatable("holo.singulo.line", e.getKey().getName(), c[0], c[1])
                    .withStyle(c[1] == 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }
        if (blocking > 0) {
            player.displayClientMessage(Component.translatable("holo.singulo.blocking", blocking).withStyle(ChatFormatting.RED), false);
        }
        if (missing == 0 && blocking == 0) {
            player.displayClientMessage(Component.translatable("holo.singulo.complete").withStyle(ChatFormatting.GREEN), false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.singulo.holo_projector.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
