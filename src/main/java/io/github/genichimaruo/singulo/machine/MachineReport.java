package io.github.genichimaruo.singulo.machine;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 装置の様子の報告（0.21.0）: 状態と、止まっていればその理由、進み具合、電力、触媒の残り、マルチブロックの足りない部品。
 * サーバーで作り（{@link #of}）、NBT で送る。見ている装置の様子を出す連携（Jade・WTHIT）と、工場モニターが同じものを使う。
 *
 * @param state    状態（画面の状態の記号と同じ分け方）
 * @param label    状態の短い言葉（画面のヘッダーと同じ）
 * @param details  理由の続き（足りない材料など）
 * @param progress 進み具合（maxProgress が 0 なら出さない）
 * @param energy   蓄電（capacity が 0 なら電力を持たない）
 * @param consume  今使っている電力（FE/t）
 * @param produce  今作っている電力（FE/t）
 * @param catalyst 装填中の触媒（なければ空）と、その残りの割合 catalystFill（0〜1）
 * @param missing  マルチブロックの足りない部品（ブロックの物と数）。blocking は設計図の空気の位置をふさぐブロックの数
 */
public record MachineReport(State state, Component label, List<Component> details, int progress, int maxProgress,
                            long energy, long capacity, int consume, int produce, ItemStack catalyst, float catalystFill,
                            List<ItemStack> missing, int blocking) {
    /** 状態（画面の状態の記号 Panel.Status と同じ並び）。 */
    public enum State {
        RUN(ChatFormatting.GREEN), IDLE(ChatFormatting.GRAY), WARN(ChatFormatting.YELLOW), NO_POWER(ChatFormatting.GOLD),
        FULL(ChatFormatting.YELLOW), BROKEN(ChatFormatting.RED), OFF(ChatFormatting.DARK_GRAY);

        public final ChatFormatting color;

        State(ChatFormatting color) {
            this.color = color;
        }

        /** 止まっていて、手を入れたほうがよい状態か（工場モニターの一覧に出す）。待機・電源を切った装置は入れない。 */
        public boolean stalled() {
            return this == WARN || this == NO_POWER || this == FULL || this == BROKEN;
        }
    }

    /** 報告を出せるブロックエンティティ。 */
    public interface Source {
        MachineReport report();
    }

    /** 足りない部品を一覧に出す種類の数（多いときは「ほか」とまとめる）。 */
    public static final int MISSING_LINES = 5;

    public static MachineReport simple(State state, Component label, long energy, long capacity) {
        return new MachineReport(state, label, List.of(), 0, 0, energy, capacity, 0, 0, ItemStack.EMPTY, 0, List.of(), 0);
    }

    /** 組み上がっていないマルチブロック: 足りない部品を付け、状態は（装置がまだ気づいていなくても）未形成にする。 */
    public MachineReport withMissing(List<ItemStack> missing, int blocking) {
        return new MachineReport(State.BROKEN, Component.translatable("gui.singulo.status.not_formed"), List.of(), 0, 0, energy, capacity,
                0, 0, catalyst, catalystFill, missing, blocking);
    }

    // ------------------------------------------------------------------ 作る

    /**
     * pos の装置の報告（サーバーで呼ぶ）。マルチブロックの部品なら、そのコントローラーの報告。組み上がっていないマルチブロックの
     * コントローラーには、足りない部品を付ける。報告のない装置でも電力を持っていれば電力だけを出す。何もなければ null。
     */
    @Nullable
    public static MachineReport of(Level level, BlockPos pos) {
        BlockPos at = pos;
        BlockState state = level.getBlockState(pos);
        if (io.github.genichimaruo.singulo.multiblock.Blueprints.kindOf(state.getBlock()) == null) {
            BlockPos controller = io.github.genichimaruo.singulo.multiblock.Blueprints.controllerOf(level, pos);
            if (controller != null) {
                at = controller;
                state = level.getBlockState(at);
            }
        }
        BlockEntity be = level.getBlockEntity(at);
        MachineReport report = null;
        if (be instanceof Source source) {
            report = source.report();
        } else {
            var storage = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, at, null);
            if (storage != null && storage.getMaxEnergyStored() > 0) {
                report = simple(State.IDLE, Component.empty(), storage.getEnergyStored(), storage.getMaxEnergyStored());
            }
        }
        var kind = io.github.genichimaruo.singulo.multiblock.Blueprints.kindOf(state.getBlock());
        if (kind != null && io.github.genichimaruo.singulo.multiblock.Blueprints.formedSize(level, at, kind) == 0) {
            int[] blocking = new int[1];
            List<ItemStack> missing = shortfall(level, kind, at, state, blocking);
            if (report == null) {
                report = simple(State.BROKEN, Component.translatable("gui.singulo.status.not_formed"), 0, 0);
            }
            report = report.withMissing(missing, blocking[0]);
        }
        return report;
    }

    /**
     * 組み上げるのに足りない部品（足りない数の多い順）。大きさを選べるマルチブロックは、足りない数がいちばん少ない大きさで数える。
     * blocking[0] に、空けておく所をふさぐブロックの数。
     */
    public static List<ItemStack> shortfall(Level level, io.github.genichimaruo.singulo.multiblock.Blueprints.Kind kind, BlockPos at,
                                            BlockState controller, int[] blocking) {
        Direction back = io.github.genichimaruo.singulo.item.HoloProjectorItem.backOf(controller);
        List<ItemStack> best = null;
        int bestCount = Integer.MAX_VALUE, bestBlocking = 0;
        for (int size : kind.sizes()) {
            var found = io.github.genichimaruo.singulo.item.HoloProjectorItem.shortfall(level, kind, at, back, size);
            int block = found.blocking();
            List<ItemStack> list = new ArrayList<>();
            int total = 0;
            for (var e : found.counts().entrySet()) {
                if (e.getValue()[1] > 0) {
                    list.add(new ItemStack(e.getKey(), e.getValue()[1]));
                    total += e.getValue()[1];
                }
            }
            if (total + block < bestCount) {
                bestCount = total + block;
                best = list;
                bestBlocking = block;
            }
        }
        blocking[0] = bestBlocking;
        best = best == null ? new ArrayList<>() : best;
        best.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
        return best;
    }

    // ------------------------------------------------------------------ 出す

    /**
     * 報告を頼む値打ちのあるブロックか（見るたびにサーバーへ頼むので、装置とマルチブロックの部品・コントローラーだけに絞る）。
     */
    public static boolean reports(BlockState state, @Nullable BlockEntity be) {
        return be instanceof Source || state.getBlock() instanceof io.github.genichimaruo.singulo.multiblock.MultiblockPart
                || io.github.genichimaruo.singulo.multiblock.Blueprints.kindOf(state.getBlock()) != null;
    }

    /** 様子を出す行。withEnergy が false なら蓄電の量を出さない（Jade・WTHIT は自分で出す）。 */
    public List<Component> lines(boolean withEnergy) {
        List<Component> out = new ArrayList<>();
        if (!label.getString().isEmpty()) {
            out.add(Component.literal("● ").withStyle(state.color).append(label.copy().withStyle(state.color)));
        }
        for (Component d : details) {
            out.add(Component.literal("  ").append(d.copy().withStyle(ChatFormatting.GRAY)));
        }
        if (maxProgress > 0 && (progress > 0 || state == State.RUN)) {
            out.add(Component.translatable("report.singulo.progress", Math.min(100, progress * 100 / maxProgress)));
        }
        if (withEnergy && capacity > 0) {
            Component line = Component.translatable("report.singulo.energy", io.github.genichimaruo.singulo.energy.EnergyText.of(energy),
                    io.github.genichimaruo.singulo.energy.EnergyText.of(capacity));
            out.add(line);
        }
        if (consume > 0) {
            out.add(Component.translatable("report.singulo.consume", io.github.genichimaruo.singulo.energy.EnergyText.of(consume)));
        }
        if (produce > 0) {
            out.add(Component.translatable("report.singulo.produce", io.github.genichimaruo.singulo.energy.EnergyText.of(produce)));
        }
        if (!catalyst.isEmpty()) {
            out.add(Component.translatable("report.singulo.catalyst", catalyst.getHoverName(), Math.round(catalystFill * 100)));
        }
        if (!missing.isEmpty() || blocking > 0) {
            out.add(Component.translatable("report.singulo.missing").withStyle(ChatFormatting.RED));
            for (int i = 0; i < missing.size() && i < MISSING_LINES; i++) {
                ItemStack s = missing.get(i);
                out.add(Component.translatable("report.singulo.missing.line", s.getHoverName(), s.getCount()).withStyle(ChatFormatting.GRAY));
            }
            if (missing.size() > MISSING_LINES) {
                int rest = 0;
                for (int i = MISSING_LINES; i < missing.size(); i++) {
                    rest += missing.get(i).getCount();
                }
                out.add(Component.translatable("report.singulo.missing.more", missing.size() - MISSING_LINES, rest).withStyle(ChatFormatting.GRAY));
            }
            if (blocking > 0) {
                out.add(Component.translatable("report.singulo.blocking", blocking).withStyle(ChatFormatting.GRAY));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 送る

    public CompoundTag save(HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag t = new CompoundTag();
        t.putByte("state", (byte) state.ordinal());
        t.put("label", ComponentSerialization.CODEC.encodeStart(ops, label).getOrThrow());
        ListTag d = new ListTag();
        for (Component c : details) {
            CompoundTag line = new CompoundTag();           // 文字だけの行と飾りのある行で NBT の型が違うので、包んで並べる
            line.put("c", ComponentSerialization.CODEC.encodeStart(ops, c).getOrThrow());
            d.add(line);
        }
        t.put("details", d);
        t.putInt("progress", progress);
        t.putInt("max", maxProgress);
        t.putLong("energy", energy);
        t.putLong("capacity", capacity);
        t.putInt("consume", consume);
        t.putInt("produce", produce);
        if (!catalyst.isEmpty()) {
            t.put("catalyst", catalyst.save(registries));
            t.putFloat("fill", catalystFill);
        }
        ListTag m = new ListTag();
        for (ItemStack s : missing) {
            CompoundTag e = new CompoundTag();
            e.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            e.putInt("count", s.getCount());
            m.add(e);
        }
        t.put("missing", m);
        t.putInt("blocking", blocking);
        return t;
    }

    @Nullable
    public static MachineReport load(CompoundTag t, HolderLookup.Provider registries) {
        if (!t.contains("state")) {
            return null;
        }
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        State state = State.values()[Math.floorMod(t.getByte("state"), State.values().length)];
        Component label = ComponentSerialization.CODEC.parse(ops, t.get("label")).result().orElse(Component.empty());
        List<Component> details = new ArrayList<>();
        for (Tag e : t.getList("details", Tag.TAG_COMPOUND)) {
            ComponentSerialization.CODEC.parse(ops, ((CompoundTag) e).get("c")).result().ifPresent(details::add);
        }
        ItemStack catalyst = t.contains("catalyst") ? ItemStack.parseOptional(registries, t.getCompound("catalyst")) : ItemStack.EMPTY;
        List<ItemStack> missing = new ArrayList<>();
        for (Tag e : t.getList("missing", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(c.getString("id")));
            missing.add(new ItemStack(item, c.getInt("count")));
        }
        return new MachineReport(state, label, details, t.getInt("progress"), t.getInt("max"), t.getLong("energy"), t.getLong("capacity"),
                t.getInt("consume"), t.getInt("produce"), catalyst, t.getFloat("fill"), missing, t.getInt("blocking"));
    }
}
