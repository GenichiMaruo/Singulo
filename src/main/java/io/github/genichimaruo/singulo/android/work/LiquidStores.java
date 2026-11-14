package io.github.genichimaruo.singulo.android.work;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 受け渡し場所（指令台・資材口）の隣の、液体・化学物質の入れ物（タンクなど）。液体は流体の見本で、化学物質（Mekanism）は化学物質の見本で
 * 指して、量を数える・抜く・入れる。作業のブロックそのものは入れ物に数えない。同じ入れ物を2つの受け渡し場所から数えないよう、seen で除く。
 */
public final class LiquidStores {
    private final List<IFluidHandler> fluids = new ArrayList<>();
    private final List<Chemicals.Store> chemicals = new ArrayList<>();
    /**
     * 素材置き場として数えるか（{@link #at}）。そのときは、抜ける量だけを数える（装置の入れるだけのタンクのように、中身は見えても
     * 抜けない物を在庫にしない）。装置の面（{@link #machine}）は、まだ残っている素材も数えたいので、見えている量のまま。
     */
    private boolean storage;

    private LiquidStores() {}

    /** point の隣の入れ物（seen にある場所は除き、見つけた場所を足す）。 */
    public static LiquidStores at(Level level, BlockPos point, Set<BlockPos> seen) {
        LiquidStores out = new LiquidStores();
        out.storage = true;
        // The addon contract exposes item handlers only, never neighboring fluid/chemical stores.
        if (level.isLoaded(point) && level.getBlockEntity(point) instanceof io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoint)
            return out;
        Set<BlockPos> chemSeen = new HashSet<>(seen);
        SupplyPortBlockEntity.collectFluids(level, point, seen, out.fluids);
        if (Chemicals.present()) {
            for (Direction d : Direction.values()) {
                BlockPos p = point.relative(d);
                if (level.getBlockEntity(p) instanceof WorkLinkedBlockEntity || level.getBlockEntity(p) instanceof WorkConsoleBlockEntity) {
                    continue;
                }
                Chemicals.Store s = anySide(level, p);
                if (s != null && chemSeen.add(p)) {
                    out.chemicals.add(s);
                    seen.add(p);
                }
            }
        }
        return out;
    }

    /**
     * 素材置き場の化学物質の入れ物（Mekanism のケミカルタンクなど）を、面を問わず1つの入れ物として扱う。Mekanism の装置は面ごとに
     * 入れる・出すの設定があるので（ケミカルタンクの既定は、前だけが出口）、数えるのは最初に見つかった面で（同じ中身を何度も数えない）、
     * 出し入れはできる面を順に試す。数えるのは、どこかの面から取り出せる量だけ（入れるだけの欄の中身を在庫にしない）。
     */
    @javax.annotation.Nullable
    private static Chemicals.Store anySide(Level level, BlockPos p) {
        java.util.List<Chemicals.Store> sides = new ArrayList<>();
        for (Direction d : Direction.values()) {
            Chemicals.Store s = Chemicals.store(level, p, d);
            if (s != null) {
                sides.add(s);
            }
        }
        if (sides.isEmpty()) {
            return null;
        }
        return new Chemicals.Store() {
            @Override
            public long amount(String id) {
                long seen = sides.get(0).amount(id);
                return seen <= 0 ? 0 : extract(id, seen, true);
            }

            @Override
            public long insert(String id, long amount, boolean simulate) {
                long put = 0;
                for (Chemicals.Store s : sides) {
                    if (put >= amount) {
                        break;
                    }
                    long n = s.insert(id, amount - put, simulate);
                    put += n;
                    if (simulate && n > 0) {
                        break;                         // 試すだけ: 同じ入れ物を別の面から二重に数えない
                    }
                }
                return put;
            }

            @Override
            public long extract(String id, long amount, boolean simulate) {
                long got = 0;
                for (Chemicals.Store s : sides) {
                    if (got >= amount) {
                        break;
                    }
                    long n = s.extract(id, amount - got, simulate);
                    got += n;
                    if (simulate && n > 0) {
                        break;
                    }
                }
                return got;
            }

            @Override
            public Map<String, Long> contents() {
                Map<String, Long> out = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Long> e : sides.get(0).contents().entrySet()) {
                    long n = extract(e.getKey(), e.getValue(), true);
                    if (n > 0) {
                        out.put(e.getKey(), n);
                    }
                }
                return out;
            }
        };
    }

    public static LiquidStores at(Level level, BlockPos point) {
        return at(level, point, new HashSet<>());
    }

    /** 見本の液体・化学物質の量。 */
    public long amount(ItemStack sample) {
        if (ChemicalSamples.is(sample)) {
            String id = ChemicalSamples.id(sample);
            long n = 0;
            for (Chemicals.Store s : chemicals) {
                n += s.amount(id);
            }
            return n;
        }
        FluidStack like = FluidSamples.fluid(sample);
        long n = 0;
        for (IFluidHandler h : fluids) {
            for (FluidAmount f : contents(h)) {
                if (FluidStack.isSameFluidSameComponents(f.type(), like)) {
                    n += f.amount();
                }
            }
        }
        return n;
    }

    /** 入れ物の中身（流体の種類ごと）。素材置き場なら、抜ける量だけ。 */
    private record FluidAmount(FluidStack type, long amount) {}

    private List<FluidAmount> contents(IFluidHandler h) {
        List<FluidAmount> out = new ArrayList<>();
        for (int t = 0; t < h.getTanks(); t++) {
            FluidStack f = h.getFluidInTank(t);
            if (f.isEmpty()) continue;
            int index = -1;
            for (int i = 0; i < out.size(); i++) {
                if (FluidStack.isSameFluidSameComponents(out.get(i).type(), f)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                out.add(new FluidAmount(f.copyWithAmount(1), f.getAmount()));
            } else {
                FluidAmount previous = out.get(index);
                out.set(index, new FluidAmount(previous.type(), previous.amount() + f.getAmount()));
            }
        }
        if (storage) {
            // A single handler's drain API is int-sized. Do not repeat simulations: each
            // would see the same stock and could incorrectly count inaccessible tanks.
            out.replaceAll(f -> new FluidAmount(f.type(), h.drain(
                    f.type().copyWithAmount((int) Math.min(Integer.MAX_VALUE, f.amount())),
                    IFluidHandler.FluidAction.SIMULATE).getAmount()));
            out.removeIf(f -> f.amount() <= 0);
        }
        return out;
    }
    /** 抜く（最大 amount）。抜けた量。 */
    public long drain(ItemStack sample, long amount, boolean simulate) {
        long got = 0;
        if (ChemicalSamples.is(sample)) {
            String id = ChemicalSamples.id(sample);
            for (Chemicals.Store s : chemicals) {
                if (got >= amount) {
                    break;
                }
                got += s.extract(id, amount - got, simulate);
            }
            return got;
        }
        FluidStack like = FluidSamples.fluid(sample);
        var action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        for (IFluidHandler h : fluids) {
            if (got >= amount) {
                break;
            }
            got += h.drain(like.copyWithAmount((int) Math.min(Integer.MAX_VALUE, amount - got)), action).getAmount();
        }
        return got;
    }

    /** 入れる（最大 amount）。入った量。 */
    public long fill(ItemStack sample, long amount, boolean simulate) {
        long put = 0;
        if (ChemicalSamples.is(sample)) {
            String id = ChemicalSamples.id(sample);
            for (Chemicals.Store s : chemicals) {
                if (put >= amount) {
                    break;
                }
                put += s.insert(id, amount - put, simulate);
            }
            return put;
        }
        FluidStack like = FluidSamples.fluid(sample);
        var action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        for (IFluidHandler h : fluids) {
            if (put >= amount) {
                break;
            }
            put += h.fill(like.copyWithAmount((int) Math.min(Integer.MAX_VALUE, amount - put)), action);
        }
        return put;
    }

    /** 中身をすべて、数え方の目印（量 1 の見本）と量で足す。 */
    public void addTo(List<ItemCount> out) {
        for (IFluidHandler h : fluids) {
            for (FluidAmount f : contents(h)) {
                ItemCount.add(out, FluidSamples.of(f.type().copyWithAmount(1)), f.amount());
            }
        }
        for (Chemicals.Store s : chemicals) {
            for (Map.Entry<String, Long> e : s.contents().entrySet()) {
                ItemCount.add(out, ChemicalSamples.of(e.getKey(), 1), e.getValue());
            }
        }
    }

    /** 装置の1つの面の、液体・化学物質の出入口（なければ空の入れ物）。 */
    public static LiquidStores machine(Level level, BlockPos pos, Direction face) {
        LiquidStores out = new LiquidStores();
        IFluidHandler f = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        if (f != null) {
            out.fluids.add(f);
        }
        Chemicals.Store c = Chemicals.store(level, pos, face);
        if (c != null) {
            out.chemicals.add(c);
        }
        return out;
    }

    /** 機械の面から、液体・化学物質を出し入れできるか（化学物質は Mekanism があるときだけ）。 */
    public static boolean handles(Level level, BlockPos pos, Direction face) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face) != null || Chemicals.store(level, pos, face) != null;
    }
}
