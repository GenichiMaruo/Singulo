package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * 液体カプセル（3段階）。液体のドローン発着ポートで液体を詰めて送る、液体用の梱包。段階とバッテリー（射程）は梱包箱と同じ。
 * 空のカプセルは重なり、詰めると1個だけの梱包済みになる。中身は1種類の液体か気体で、最大 {@link #CAPACITY} mB。
 * <p>
 * 見た目は立体の耐圧容器（両端の鏡板・ガラスの胴・保護の枠と脚・上の弁）で、胴の中に液体がたまって見える（FluidCapsuleRenderer）。
 * 梱包済みのカプセルを液体のタンクなどに使うと、入るだけ注ぐ。
 */
public class FluidCapsuleItem extends DroneCrateItem {
    public static final int CAPACITY = 64_000;

    public FluidCapsuleItem(int tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return io.github.genichimaruo.singulo.client.FluidCapsuleRenderer.get();
            }
        });
    }

    /** 中身（空なら EMPTY）。 */
    public static FluidStack fluid(ItemStack stack) {
        return SinguloComponents.getOrDefault(stack, SinguloComponents.CAPSULE_FLUID.get(), FluidStack.EMPTY).copy();
    }

    /** 空のカプセル1個に液体を詰めたもの。 */
    public static ItemStack pack(ItemStack capsule, FluidStack fluid) {
        ItemStack out = capsule.copyWithCount(1);
        setFluid(out, fluid);
        return out;
    }

    /** 中身を入れ替える。空になったら、ほかの空のカプセルと重なるように部品を外す。 */
    public static void setFluid(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty()) {
            SinguloComponents.remove(stack, SinguloComponents.CAPSULE_FLUID.get());
            return;
        }
        SinguloComponents.set(stack, SinguloComponents.CAPSULE_FLUID.get(), io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(fluid, Math.min(CAPACITY, fluid.getAmount())));
    }

    /** 梱包済みのカプセルの中身は、手に持って開けられない（タンクなどに使って注ぐ）。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /** タンクの画面を開くブロック操作より先に注ぐ（スニークを要求しない）。 */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    /** 液体を受けるブロック（タンク・装置）に使うと、入るだけ注ぐ。残りはカプセルに残る。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        FluidStack inside = fluid(stack);
        if (inside.isEmpty()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        IFluidHandler target = Capabilities.get(level, Capabilities.FluidHandler.BLOCK, context.getClickedPos(), context.getClickedFace());
        if (target == null || target.fill(inside, IFluidHandler.FluidAction.SIMULATE) <= 0) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            int filled = target.fill(inside, IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) {
                ItemStack next = stack.copyWithCount(1);
                setFluid(next, io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(inside, inside.getAmount() - filled));
                if (context.getPlayer() != null) {
                    context.getPlayer().setItemInHand(context.getHand(), next);
                }
                level.playSound(null, context.getClickedPos(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 0.9F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.singulo.drone_crate.range", range(tier(stack))).withStyle(ChatFormatting.AQUA));
        FluidStack inside = fluid(stack);
        if (inside.isEmpty()) {
            lines.add(Component.translatable("tooltip.singulo.fluid_capsule.empty", CAPACITY).withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(Component.translatable("tooltip.singulo.fluid_capsule.contents", inside.getDisplayName(), inside.getAmount(), CAPACITY)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.singulo.fluid_capsule.pour").withStyle(ChatFormatting.DARK_GRAY));
    }
}
