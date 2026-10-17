package io.github.genichimaruo.singulo.forgecompat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Forge 版: NeoForge の FluidStack にあって Forge の FluidStack にない操作。 */
public final class FluidStacks {
    private FluidStacks() {}

    /** NeoForge と同じ形（{"id": 流体, "amount": 量}）の、レシピの流体の書き方。 */
    public static final Codec<FluidStack> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(FluidStack::getFluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidStack::getAmount)
    ).apply(i, FluidStack::new));

    /** 通信での流体（空・種類・量・タグ）。Forge 1.21.1 の FluidStack には通信の書き方がない。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, FluidStack stack) -> {
                buf.writeBoolean(!stack.isEmpty());
                if (!stack.isEmpty()) {
                    buf.writeResourceLocation(BuiltInRegistries.FLUID.getKey(stack.getFluid()));
                    buf.writeVarInt(stack.getAmount());
                    buf.writeNbt(stack.getTag());
                }
            },
            (RegistryFriendlyByteBuf buf) -> {
                if (!buf.readBoolean()) {
                    return FluidStack.EMPTY;
                }
                FluidStack stack = new FluidStack(BuiltInRegistries.FLUID.get(buf.readResourceLocation()), buf.readVarInt());
                net.minecraft.nbt.CompoundTag tag = buf.readNbt();
                if (tag != null) {
                    stack.setTag(tag);
                }
                return stack;
            });

    public static FluidStack copyWithAmount(FluidStack stack, int amount) {
        return stack.isEmpty() ? FluidStack.EMPTY : new FluidStack(stack, amount);
    }

    /** 置き換えの規則が ItemStack.copyWithAmount も通すので、そちらはそのまま。 */
    public static ItemStack copyWithAmount(ItemStack stack, int amount) {
        return stack.copyWithCount(amount);
    }

    public static boolean same(FluidStack a, FluidStack b) {
        return a.isFluidEqual(b);
    }

    /** アイテムの液体の機能（Forge 1.21.1 の FluidUtil.getFluidHandler(ItemStack) は常に空を返すので、自分で聞く）。 */
    public static java.util.Optional<net.minecraftforge.fluids.capability.IFluidHandlerItem> itemHandler(ItemStack stack) {
        return stack.isEmpty() ? java.util.Optional.empty()
                : stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).resolve();
    }

    /**
     * 手に持った入れ物（バケツなど）とブロックのあいだで液体を出し入れする（FluidUtil.interactWithFluidHandler の代わり）。
     * 先に入れ物の中身をブロックへ移し、できなければブロックから入れ物へ汲む。
     */
    public static boolean interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                   net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side) {
        ItemStack held = player.getItemInHand(hand);
        var block = Caps.find(level, Capabilities.FluidHandler.BLOCK, pos, side);
        var item = itemHandler(held.copyWithCount(1)).orElse(null);
        if (block == null || item == null) {
            return false;
        }
        var sim = net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        var exec = net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        boolean emptied = false;
        FluidStack inside = item.drain(Integer.MAX_VALUE, sim);
        if (!inside.isEmpty()) {
            int room = block.fill(inside, sim);
            FluidStack moved = room > 0 ? item.drain(new FluidStack(inside, room), sim) : FluidStack.EMPTY;
            if (!moved.isEmpty() && block.fill(moved, sim) == moved.getAmount()) {
                block.fill(item.drain(moved, exec), exec);
                emptied = true;
            }
        }
        if (!emptied) {
            FluidStack available = block.drain(Integer.MAX_VALUE, sim);
            int accepted = available.isEmpty() ? 0 : item.fill(available, sim);
            if (accepted <= 0) {
                return false;
            }
            FluidStack got = block.drain(new FluidStack(available, accepted), exec);
            item.fill(got, exec);
        }
        level.playSound(null, pos, emptied ? net.minecraft.sounds.SoundEvents.BUCKET_EMPTY : net.minecraft.sounds.SoundEvents.BUCKET_FILL,
                net.minecraft.sounds.SoundSource.BLOCKS, 1F, 1F);
        if (!player.getAbilities().instabuild) {
            ItemStack result = item.getContainer();
            held.shrink(1);
            if (held.isEmpty()) {
                player.setItemInHand(hand, result);
            } else if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
        return true;
    }
}
