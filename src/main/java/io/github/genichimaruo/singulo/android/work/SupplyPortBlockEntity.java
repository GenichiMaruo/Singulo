package io.github.genichimaruo.singulo.android.work;

import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * アンドロイド資材口。隣（6方向）のストレージ（チェストなど）を、作業の素材置き場としてつなぐ。
 * アンドロイドはここへ来て、素材を取り、できた物や副産物を戻す。作業指令台も資材口を兼ねるので、
 * 素材置き場を増やしたい・離れた所にも置きたいときに使う。
 */
public class SupplyPortBlockEntity extends WorkLinkedBlockEntity {
    public SupplyPortBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_SUPPLY_PORT.get(), pos, state);
    }

    @Override
    public WorkConsoleBlockEntity.LinkKind linkKind() {
        return WorkConsoleBlockEntity.LinkKind.SUPPLY;
    }

    /**
     * 隣のストレージを集める。seen は数えたストレージの場所（2つの資材口に挟まれたチェストや、大きいチェストの片割れを二重に数えないため）。
     */
    public void storages(Level level, Set<BlockPos> seen, List<IItemHandler> out) {
        collect(level, worldPosition, seen, out);
    }

    /** from の隣の、流体を入れておけるブロック（タンクなど。作業のブロックは除く）。 */
    static void collectFluids(Level level, BlockPos from, Set<BlockPos> seen,
                              List<net.minecraftforge.fluids.capability.IFluidHandler> out) {
        for (Direction d : Direction.values()) {
            BlockPos p = from.relative(d);
            if (level.getBlockEntity(p) instanceof WorkLinkedBlockEntity || level.getBlockEntity(p) instanceof WorkConsoleBlockEntity) {
                continue;
            }
            var h = io.github.genichimaruo.singulo.compat.Capabilities.get(level, Capabilities.FluidHandler.BLOCK, p, d.getOpposite());
            if (h != null && h.getTanks() > 0 && seen.add(p)) {
                out.add(h);
            }
        }
    }

    /** box の中身を、pos の隣のストレージへ移す（作業のブロックは除く）。1回で1スタックずつ。 */
    static void pushOut(Level level, BlockPos pos, net.minecraft.world.Container box) {
        List<IItemHandler> targets = new java.util.ArrayList<>();
        collect(level, pos, new java.util.HashSet<>(), targets);
        if (targets.isEmpty()) {
            return;
        }
        for (int i = 0; i < box.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack s = box.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            net.minecraft.world.item.ItemStack rest = s.copy();
            for (IItemHandler h : targets) {
                rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(h, rest, false);
                if (rest.isEmpty()) {
                    break;
                }
            }
            if (rest.getCount() != s.getCount()) {
                box.setItem(i, rest);
            }
        }
    }

    /** from の隣（6方向）のストレージを集める（作業指令台・資材口・納品口・レシピ棚は除く）。 */
    static void collect(Level level, BlockPos from, Set<BlockPos> seen, List<IItemHandler> out) {
        for (Direction d : Direction.values()) {
            BlockPos p = from.relative(d);
            if (level.getBlockEntity(p) instanceof WorkLinkedBlockEntity || level.getBlockEntity(p) instanceof WorkConsoleBlockEntity) {
                continue;
            }
            IItemHandler h = io.github.genichimaruo.singulo.compat.Capabilities.get(level, Capabilities.ItemHandler.BLOCK, p, d.getOpposite());
            if (h == null) {
                continue;
            }
            BlockPos key = p;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                // 大きいチェストは、2つの片割れで同じ中身なので、片方の場所で数える
                BlockPos other = p.relative(ChestBlock.getConnectedDirection(s));
                key = other.asLong() < p.asLong() ? other : p;
            }
            if (seen.add(key)) {
                out.add(h);
            }
        }
    }

    /** つながっている指令台に加えて、つながっているストレージの数も出す。 */
    @Override
    protected void showLink(ServerPlayer player) {
        BlockPos c = console();
        if (c == null) {
            super.showLink(player);
            return;
        }
        List<IItemHandler> list = new java.util.ArrayList<>();
        storages(player.level(), new java.util.HashSet<>(), list);
        player.displayClientMessage(Component.translatable("message.singulo.work_link.info", c.getX(), c.getY(), c.getZ())
                .append(Component.translatable("message.singulo.supply_port.storages", list.size())), true);
    }
}
