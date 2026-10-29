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
 * <p>
 * 優先の登録（{@link #ENTRIES} 件）: アイテムごとに優先順位（1がいちばん先）と、取り出し・返却のどちらに効くかを決める。
 * 登録した物は、順位の高い資材口から先に取り出し、先に返す（いっぱいなら次の場所へ）。登録していない物は、これまでどおり。
 */
public class SupplyPortBlockEntity extends WorkLinkedBlockEntity {
    public static final int ENTRIES = 8;
    public static final int MAX_RANK = 9;
    /** 優先が効く向き。 */
    public static final int MODE_BOTH = 0, MODE_TAKE = 1, MODE_RETURN = 2, MODES = 3;

    /** 登録したアイテム（1個ずつ。中身は持たない見本）。 */
    private final net.minecraft.world.SimpleContainer entries = new net.minecraft.world.SimpleContainer(ENTRIES) {
        @Override
        public void setChanged() {
            super.setChanged();
            SupplyPortBlockEntity.this.entryChanged();
        }
    };
    private final int[] ranks = new int[ENTRIES];
    private final int[] modes = new int[ENTRIES];
    /** 画面へ送る値: 0〜7 は順位、8〜15 は向き。 */
    private final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int index) {
            return index < ENTRIES ? ranks[index] : modes[index - ENTRIES];
        }

        @Override
        public void set(int index, int value) {
            if (index < ENTRIES) {
                ranks[index] = value;
            } else {
                modes[index - ENTRIES] = value;
            }
        }

        @Override
        public int getCount() {
            return ENTRIES * 2;
        }
    };

    public SupplyPortBlockEntity(BlockPos pos, BlockState state) {
        super(SinguloBlockEntities.ANDROID_SUPPLY_PORT.get(), pos, state);
        java.util.Arrays.fill(ranks, 1);
    }

    public net.minecraft.world.Container entries() {
        return entries;
    }

    public net.minecraft.world.inventory.ContainerData entryData() {
        return data;
    }

    /** 登録を変えた: 空いた欄の順位と向きは初めに戻す。 */
    private void entryChanged() {
        for (int i = 0; i < ENTRIES; i++) {
            if (entries.getItem(i).isEmpty()) {
                ranks[i] = 1;
                modes[i] = MODE_BOTH;
            }
        }
        setChanged();
    }

    /** i 番の登録の順位を delta だけ変える（1〜{@link #MAX_RANK}）。 */
    public void shiftRank(int i, int delta) {
        if (i >= 0 && i < ENTRIES && !entries.getItem(i).isEmpty()) {
            ranks[i] = Math.max(1, Math.min(MAX_RANK, ranks[i] + delta));
            setChanged();
        }
    }

    /** i 番の登録の向きを次へ（両方 → 取り出し → 返却）。 */
    public void cycleMode(int i) {
        if (i >= 0 && i < ENTRIES && !entries.getItem(i).isEmpty()) {
            modes[i] = (modes[i] + 1) % MODES;
            setChanged();
        }
    }

    /**
     * item の優先度（大きいほど先）。登録した順位 1〜9 を 9〜1 にする。take は取り出し、そうでなければ返却。
     * 登録していなければ（その向きに効く登録がなければ） 0。
     */
    public int priority(net.minecraft.world.item.ItemStack item, boolean take) {
        int best = 0;
        for (int i = 0; i < ENTRIES; i++) {
            net.minecraft.world.item.ItemStack e = entries.getItem(i);
            if (e.isEmpty() || !net.minecraft.world.item.ItemStack.isSameItem(e, item)
                    || modes[i] == (take ? MODE_RETURN : MODE_TAKE)) {
                continue;
            }
            best = Math.max(best, MAX_RANK + 1 - ranks[i]);
        }
        return best;
    }

    @Override
    protected boolean openScreen(ServerPlayer player) {
        if (!mayOperate(player)) {
            player.displayClientMessage(Component.translatable("gui.singulo.work_console.locked"), true);
            return true;
        }
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new SupplyPortMenu(id, inv, worldPosition, entries, data),
                Component.translatable(getBlockState().getBlock().getDescriptionId())), buf -> buf.writeBlockPos(worldPosition));
        return true;
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < ENTRIES; i++) {
            net.minecraft.world.item.ItemStack e = entries.getItem(i);
            if (e.isEmpty()) {
                continue;
            }
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            t.putInt("slot", i);
            t.put("item", e.save(new net.minecraft.nbt.CompoundTag()));
            t.putInt("rank", ranks[i]);
            t.putInt("mode", modes[i]);
            list.add(t);
        }
        if (!list.isEmpty()) {
            tag.put("priorities", list);
        }
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        for (int i = 0; i < ENTRIES; i++) {
            entries.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
        net.minecraft.nbt.ListTag list = tag.getList("priorities", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int k = 0; k < list.size(); k++) {
            net.minecraft.nbt.CompoundTag t = list.getCompound(k);
            int i = t.getInt("slot");
            if (i < 0 || i >= ENTRIES) {
                continue;
            }
            entries.setItem(i, net.minecraft.world.item.ItemStack.of(t.getCompound("item")));
            ranks[i] = Math.max(1, Math.min(MAX_RANK, t.getInt("rank")));
            modes[i] = Math.floorMod(t.getInt("mode"), MODES);
        }
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
            if (!level.isLoaded(p)) {
                continue;
            }
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
        if (box.isEmpty()) {
            return;
        }
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
        var owner = level.isLoaded(from) ? level.getBlockEntity(from) : null;
        AdjacentItemStores cache = owner instanceof WorkConsoleBlockEntity console ? console.adjacentItems
                : owner instanceof WorkLinkedBlockEntity linked ? linked.adjacentItems : null;
        for (Direction d : Direction.values()) {
            BlockPos p = from.relative(d);
            if (!level.isLoaded(p)) {
                continue;
            }
            if (level.getBlockEntity(p) instanceof WorkLinkedBlockEntity || level.getBlockEntity(p) instanceof WorkConsoleBlockEntity) {
                continue;
            }
            IItemHandler h = cache == null ? Capabilities.get(level, Capabilities.ItemHandler.BLOCK, p, d.getOpposite()) : cache.get(d);
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
