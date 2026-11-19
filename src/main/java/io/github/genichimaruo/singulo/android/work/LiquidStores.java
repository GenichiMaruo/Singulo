package io.github.genichimaruo.singulo.android.work;

import com.google.common.math.LongMath;
import io.github.genichimaruo.singulo.api.android.AndroidChemicalStorage;
import io.github.genichimaruo.singulo.api.android.AndroidSharedStorage;
import io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
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
    private final List<AndroidChemicalStorage> chemicals = new ArrayList<>();
    /**
     * 素材置き場として数えるか（{@link #at}）。そのときは、抜ける量だけを数える（装置の入れるだけのタンクのように、中身は見えても
     * 抜けない物を在庫にしない）。装置の面（{@link #machine}）は、まだ残っている素材も数えたいので、見えている量のまま。
     */
    private boolean storage;

    private LiquidStores() {}

    /** One fluid handler as a store (private escrow custody). Counts what can be drained. */
    public static LiquidStores of(IFluidHandler handler) {
        LiquidStores out = new LiquidStores();
        out.storage = true;
        out.fluids.add(handler);
        return out;
    }

    static LiquidStores endpoint(AndroidWorkEndpoint endpoint, boolean delivery) {
        LiquidStores out = new LiquidStores();
        out.storage = true;
        IFluidHandler fluid = delivery ? endpoint.deliveryFluids() : endpoint.supplyFluids();
        AndroidChemicalStorage chemical = delivery ? endpoint.deliveryChemicals() : endpoint.supplyChemicals();
        if (fluid != null) out.fluids.add(fluid);
        if (chemical != null) out.chemicals.add(chemical);
        return out;
    }

    /** point の隣の入れ物（seen にある場所は除き、見つけた場所を足す）。 */
    public static LiquidStores at(Level level, BlockPos point, Set<BlockPos> seen) {
        LiquidStores out = new LiquidStores();
        out.storage = true;
        Set<IFluidHandler> handlers = Collections.newSetFromMap(new IdentityHashMap<>());
        adjacent(level, point, (pos, store) -> {
            if (seen.add(pos)) {
                for (IFluidHandler fluid : store.fluids) if (handlers.add(fluid)) out.fluids.add(fluid);
                out.chemicals.addAll(store.chemicals);
            }
        });
        return out;
    }

    private static void adjacent(Level level, BlockPos point, BiConsumer<BlockPos, LiquidStores> visit) {
        // Linked endpoints are resolved by the console, including ownership and placed identity.
        if (!level.isLoaded(point) || level.getBlockEntity(point) instanceof io.github.genichimaruo.singulo.api.android.AndroidWorkEndpoint) return;
        for (Direction d : Direction.values()) {
            BlockPos pos = point.relative(d);
            if (!level.isLoaded(pos)) continue;
            var be = level.getBlockEntity(pos);
            if (be instanceof WorkLinkedBlockEntity || be instanceof WorkConsoleBlockEntity) continue;
            IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, d.getOpposite());
            Chemicals.Store chemicals = Chemicals.present() ? anySide(level, pos) : null;
            boolean hasFluid = fluids != null && fluids.getTanks() > 0;
            if (!hasFluid && chemicals == null) continue;
            LiquidStores store = new LiquidStores();
            store.storage = true;
            if (hasFluid) store.fluids.add(fluids);
            if (chemicals != null) store.chemicals.add(chemicals);
            visit.accept(pos, store);
        }
    }

    /** An extractable material and the supply point that can actually drain it. */
    record Supply(BlockPos key, BlockPos point, ItemCount material, LiquidStores store) {}

    private static void visitSupply(WorkConsoleBlockEntity console, BlockPos point, BiConsumer<BlockPos, LiquidStores> visit) {
        if (console.hasEndpointLink(point)) {
            var endpoint = console.workEndpoint(point);
            if (endpoint != null) visit.accept(point, endpoint(endpoint, false));
        } else adjacent(console.getLevel(), point, visit);
    }

    private BlockPos storageKey(ItemStack sample, BlockPos pos) {
        Object handler = ChemicalSamples.is(sample) ? chemicals.getFirst() : fluids.getFirst();
        return handler instanceof AndroidSharedStorage shared ? SupplyPortBlockEntity.sharedSeenKey(shared.sharedStorageKey()) : pos;
    }

    static boolean canFillAt(WorkConsoleBlockEntity console, BlockPos point, ItemStack sample) {
        boolean[] fits = {false};
        visitSupply(console, point, (pos, store) -> {
            if (!fits[0]) fits[0] = store.fill(sample, 1, true) > 0;
        });
        return fits[0];
    }

    static long fillAt(WorkConsoleBlockEntity console, BlockPos point, ItemStack sample, long amount, BiConsumer<BlockPos, Long> deposited) {
        long[] moved = {0};
        visitSupply(console, point, (pos, store) -> {
            if (moved[0] == amount) return;
            long accepted = store.fill(sample, amount - moved[0], false);
            if (accepted > 0) {
                moved[0] += accepted;
                deposited.accept(store.storageKey(sample, pos), accepted);
            }
        });
        return moved[0];
    }

    static Map<BlockPos, List<Supply>> supplies(WorkConsoleBlockEntity console, List<BlockPos> points) {
        Map<BlockPos, List<Supply>> tanks = new LinkedHashMap<>();
        Map<Object, BlockPos> shared = new IdentityHashMap<>();
        for (BlockPos point : points) {
            visitSupply(console, point, (pos, store) -> {
                List<ItemCount> available = new ArrayList<>();
                store.addTo(available);
                for (ItemCount item : available) {
                    Object handler = FluidSamples.is(item.item) ? store.fluids.getFirst() : store.chemicals.getFirst();
                    BlockPos key = shared.computeIfAbsent(handler, h -> store.storageKey(item.item, pos));
                    List<Supply> faces = tanks.computeIfAbsent(key, ignored -> new ArrayList<>());
                    // Two faces can expose the same tank with different drain permissions.
                    // For each fluid use its most accessible face; ties keep the first point.
                    Supply candidate = new Supply(key, point, item, store);
                    int i = 0;
                    while (i < faces.size() && !faces.get(i).material.matches(item.item)) i++;
                    if (i == faces.size()) faces.add(candidate);
                    else if (faces.get(i).material.count < item.count) faces.set(i, candidate);
                }
            });
        }
        Map<BlockPos, List<Supply>> byPoint = new LinkedHashMap<>();
        for (var faces : tanks.values()) {
            for (Supply supply : faces) byPoint.computeIfAbsent(supply.point, ignored -> new ArrayList<>()).add(supply);
        }
        return byPoint;
    }

    static long available(List<Supply> supplies, ItemStack sample) {
        long amount = 0;
        for (Supply supply : supplies) if (supply.material.matches(sample)) amount = LongMath.saturatedAdd(amount, supply.material.count);
        return amount;
    }

    static long take(List<Supply> supplies, ItemStack sample, long amount) {
        long taken = 0;
        for (Supply supply : supplies) {
            if (taken >= amount) break;
            if (supply.material.matches(sample)) taken += supply.store.drain(sample, amount - taken, false);
        }
        return taken;
    }

    boolean hasMultipleSources(ItemStack sample) {
        return ChemicalSamples.is(sample) ? chemicals.size() > 1 : FluidSamples.is(sample) && fluids.size() > 1;
    }

    /**
     * 素材置き場の化学物質の入れ物（Mekanism のケミカルタンクなど）を、面を問わず1つの入れ物として扱う。Mekanism の装置は面ごとに
     * 入れる・出すの設定があり、面によって見えるタンクも異なる。化学物質ごとに各面から抜ける量の最大値を取り、
     * 同じタンクを複数の面から見ても二重に数えない。
     */
    @javax.annotation.Nullable
    private static Chemicals.Store anySide(Level level, BlockPos p) {
        List<Chemicals.Store> sides = new ArrayList<>();
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
                return extract(id, Long.MAX_VALUE, true);
            }

            @Override
            public long insert(String id, long amount, boolean simulate) {
                long put = 0;
                for (var side : sides) {
                    if (put >= amount) break;
                    put = simulate ? Math.max(put, side.insert(id, amount, true))
                            : put + side.insert(id, amount - put, false);
                }
                return put;
            }

            @Override
            public long extract(String id, long amount, boolean simulate) {
                long got = 0;
                for (var side : sides) {
                    if (got >= amount) break;
                    got = simulate ? Math.max(got, side.extract(id, amount, true))
                            : got + side.extract(id, amount - got, false);
                }
                return got;
            }

            @Override
            public Map<String, Long> contents() {
                Map<String, Long> out = new LinkedHashMap<>();
                for (var side : sides) {
                    side.contents().forEach((id, seen) -> {
                        long best = out.getOrDefault(id, 0L);
                        if (seen > best) {
                            long accessible = side.extract(id, seen, true);
                            if (accessible > best) out.put(id, accessible);
                        }
                    });
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
            for (AndroidChemicalStorage s : chemicals) {
                n = LongMath.saturatedAdd(n, s.amount(id));
            }
            return n;
        }
        FluidStack like = FluidSamples.fluid(sample);
        long n = 0;
        for (IFluidHandler h : fluids) {
            for (FluidAmount f : contents(h)) {
                if (FluidStack.isSameFluidSameComponents(f.type(), like)) {
                    n = LongMath.saturatedAdd(n, f.amount());
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
            for (AndroidChemicalStorage s : chemicals) {
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
            for (AndroidChemicalStorage s : chemicals) {
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
        for (AndroidChemicalStorage s : chemicals) {
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
