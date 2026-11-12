package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.android.work.SurveyGlassesItem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * つながった液体パイプのひとまとまり。液体は溜めず、周期ごとに口どうしで直接受け渡す。パイプ自体は tick しない。
 * <p>
 * 電線（{@code EnergyNetwork}）と同じく、分かれ目のないパイプの並び（区間）は1つの制約にまとめる。毎周期の仕事は口と分かれ目の数で
 * 決まり、パイプの長さには効かない（つなぎ直したときに1回たどるだけ）。口から口への経路（分かれ目の数で最短）は初めて使うときに
 * 求めて覚え、運べる量は経路のいちばん細い区間・分かれ目の残りで決まる。同じ区間を通る受け渡しはその容量を分け合う。
 * <p>
 * 液体ごとに、出すだけの口 → 受けるだけの口 → 出し入れできる口（タンク）の順に配る。タンクからは受けるだけの口へしか
 * 出さないので、タンクどうしで行き来し続けない。どの面が出し入れできるかは装置の capability（面の設定）が決める。
 * <p>
 * 流れの見た目（パイプの中の液体と向き）は {@link #DISPLAY} tick ごとに、実際に流れた経路だけを広げて決め、変わったパイプにだけ送る。
 */
public final class FluidPipeNetwork {
    /** 受け渡しの周期（tick）。 */
    static final int INTERVAL = 5;
    /** 見た目をまとめて送る間隔（tick。INTERVAL の倍数）。 */
    static final int DISPLAY = 10;
    /** 見積もりに使う量（mB）。 */
    private static final int PROBE = FluidType.BUCKET_VOLUME;

    private static final Set<FluidPipeNetwork> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    /** まだネットワークに入っていないパイプ。読み込みやつなぎ替えのたびに作り直さず、次の tick にまとめて作る。 */
    private static final Set<FluidPipeBlockEntity> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());

    /** 分かれ目・口に面したパイプ（それ自体が1本ぶんの容量を持つ）。 */
    private static final class Node {
        final BlockPos pos;
        final long capacity;
        final List<Segment> edges = new ArrayList<>();

        Node(BlockPos pos, long capacity) {
            this.pos = pos;
            this.capacity = capacity;
        }
    }

    /** 2つの分かれ目の間の、分かれ目のないパイプの並び。容量は中のパイプのいちばん細いもの。 */
    private static final class Segment {
        final Node a, b;
        /** 両端の分かれ目を含む、a から b への並び（見た目を広げるときだけ使う）。 */
        final List<BlockPos> pipes;
        final long capacity;

        Segment(Node a, Node b, List<BlockPos> pipes, Map<BlockPos, Long> capacities) {
            this.a = a;
            this.b = b;
            this.pipes = List.copyOf(pipes);
            long c = Long.MAX_VALUE;
            for (int i = 1; i < pipes.size() - 1; i++) {
                c = Math.min(c, capacities.get(pipes.get(i)));
            }
            this.capacity = c;
        }

        Node other(Node n) {
            return n == a ? b : a;
        }

        int length() {
            return pipes.size() - 1;
        }
    }

    private record Link(BlockPos a, BlockPos b) {
        static Link of(BlockPos a, BlockPos b) {
            return a.asLong() < b.asLong() ? new Link(a, b) : new Link(b, a);
        }
    }

    private record Endpoint(BlockPos pos, Direction side, Node node) {}

    /** この周期で見えている口。 */
    private record Port(Endpoint endpoint, IFluidHandler handler, boolean drainable) {
        BlockPos pos() {
            return endpoint.pos;
        }
    }

    private record Path(List<Node> nodes, List<Segment> segments) {}

    private record Tree(Map<Node, Segment> parent) {}

    private record Step(Node node, int distance) {}

    private record RouteKey(Node from, Node to) {}

    private record SampleKey(Path path, BlockPos from, BlockPos to) {}

    private static final class Sample {
        long amount;
        FluidStack fluid = FluidStack.EMPTY;
        boolean mixed;

        void add(FluidStack f, long n) {
            amount = saturatedAdd(amount, n);
            if (fluid.isEmpty()) {
                fluid = f.copyWithAmount(1);
            } else if (!FluidStack.isSameFluidSameComponents(fluid, f)) {
                mixed = true;
            }
        }

        void add(Sample other) {
            if (!other.fluid.isEmpty()) {
                add(other.fluid, other.amount);
            }
            mixed |= other.mixed;
        }
    }

    /** 1本のパイプに見せる流れ。 */
    private static final class Flow {
        final Sample sample = new Sample();
        int in, out;
    }

    private static final class SegmentFlow {
        final Sample sample = new Sample();
        boolean forward, backward;
    }

    private final Level level;
    private final Map<BlockPos, FluidPipeBlockEntity> pipes;
    private final Map<BlockPos, Node> nodes;
    private final List<Endpoint> endpoints;
    private final Set<BlockPos> endpointPositions = new HashSet<>();
    private final Set<Segment> segments = Collections.newSetFromMap(new IdentityHashMap<>());
    private final AABB bounds;
    private final Map<Node, Tree> trees = new IdentityHashMap<>();
    private final Map<RouteKey, Path> paths = new HashMap<>();
    /** この周期に使った容量（分かれ目・区間ごと）。 */
    private final Map<Object, Long> used = new IdentityHashMap<>();
    private final Map<SampleKey, Sample> samples = new HashMap<>();
    /** 今、流れ・詰まりを見せているパイプ。 */
    private final Set<BlockPos> lit = new HashSet<>();
    private boolean valid = true;
    private int rotation;
    /** この見た目の間隔で、出せる液体があったか・実際に動いたか（詰まりの表示）。 */
    private boolean offered, moved;

    private FluidPipeNetwork(Level level, Map<BlockPos, FluidPipeBlockEntity> pipes, Map<BlockPos, Node> nodes,
                             List<Endpoint> endpoints) {
        this.level = level;
        this.pipes = pipes;
        this.nodes = nodes;
        this.endpoints = endpoints;
        nodes.values().forEach(n -> segments.addAll(n.edges));
        endpoints.forEach(e -> endpointPositions.add(e.pos));
        AABB b = null;
        for (var e : pipes.entrySet()) {
            b = b == null ? new AABB(e.getKey()) : b.minmax(new AABB(e.getKey()));
            // 前のネットワークが見せていた流れを引き継ぐ（次の見た目の更新で、流れていなければ消す）
            if (e.getValue().visualState() != 0) {
                lit.add(e.getKey());
            }
        }
        bounds = b == null ? new AABB(BlockPos.ZERO) : b;
    }

    static void schedule(FluidPipeBlockEntity pipe) {
        PENDING.add(pipe);
    }

    static void forget(FluidPipeBlockEntity pipe) {
        PENDING.remove(pipe);
    }

    /**
     * 液体パイプの先に口が置かれうるか。ブロックエンティティは面の設定で capability が変わるので今なくても含める。
     * 大釜のようにブロックエンティティなしで液体を扱うブロックと、水源も含む。capability は周期ごとに取り直す。
     */
    static boolean mayBeEndpoint(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) != null || isSource(level.getBlockState(pos))
                || level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null;
    }

    /** 汲める水源か（水・溶岩の源。流れている水や、水没していないブロックは含まない）。 */
    static boolean isSource(BlockState state) {
        return state.getFluidState().isSource() && state.getBlock() instanceof BucketPickup;
    }

    boolean hasEndpoint(BlockPos pos) {
        return endpointPositions.contains(pos);
    }

    void invalidate() {
        if (!valid) {
            return;
        }
        valid = false;
        ACTIVE.remove(this);
        // 液体はネットワークに溜めていないので、分かれても消えない。見た目は次のネットワークが引き継いで直す
        for (FluidPipeBlockEntity pipe : pipes.values()) {
            if (pipe.network == this) {
                pipe.network = null;
                if (!pipe.isRemoved()) {
                    PENDING.add(pipe);
                }
            }
        }
    }

    private static long perCycle(long perTick) {
        return perTick >= Long.MAX_VALUE / INTERVAL ? Long.MAX_VALUE : perTick * INTERVAL;
    }

    private static void build(Level level, FluidPipeBlockEntity start) {
        Map<BlockPos, FluidPipeBlockEntity> pipes = new HashMap<>();
        Map<BlockPos, Long> capacities = new HashMap<>();
        Map<BlockPos, List<BlockPos>> adjacent = new HashMap<>();
        Map<BlockPos, List<Map.Entry<BlockPos, Direction>>> devices = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start.getBlockPos());
        queue.add(start.getBlockPos());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            if (!(level.getBlockEntity(pos) instanceof FluidPipeBlockEntity be)
                    || !(level.getBlockState(pos).getBlock() instanceof FluidPipeBlock block)) {
                continue;
            }
            pipes.put(pos, be);
            capacities.put(pos, perCycle(block.capacity()));
            List<BlockPos> neighbors = new ArrayList<>(2);
            adjacent.put(pos, neighbors);
            for (Direction d : Direction.values()) {
                BlockPos next = pos.relative(d);
                // 読み込まれていない先は強制的に読み込まない（読み込まれたときに作り直す）
                if (!level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockEntity(next) instanceof FluidPipeBlockEntity
                        && level.getBlockState(next).getBlock() instanceof FluidPipeBlock) {
                    neighbors.add(next);
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                } else if (mayBeEndpoint(level, next)) {
                    devices.computeIfAbsent(pos, p -> new ArrayList<>()).add(Map.entry(next, d.getOpposite()));
                }
            }
        }
        // 分かれ目: 隣のパイプが2本でないもの、口に面したもの
        Map<BlockPos, Node> nodes = new HashMap<>();
        for (BlockPos pos : pipes.keySet()) {
            if (adjacent.get(pos).size() != 2 || devices.containsKey(pos)) {
                nodes.put(pos, new Node(pos, capacities.get(pos)));
            }
        }
        Set<Link> links = new HashSet<>();
        for (Node node : nodes.values()) {
            for (BlockPos neighbor : adjacent.get(node.pos)) {
                if (!links.add(Link.of(node.pos, neighbor))) {
                    continue;
                }
                List<BlockPos> run = new ArrayList<>();
                run.add(node.pos);
                BlockPos previous = node.pos, at = neighbor;
                while (!nodes.containsKey(at)) {
                    run.add(at);
                    List<BlockPos> next = adjacent.get(at);
                    BlockPos following = next.get(0).equals(previous) ? next.get(1) : next.get(0);
                    links.add(Link.of(at, following));
                    previous = at;
                    at = following;
                }
                run.add(at);
                Segment segment = new Segment(node, nodes.get(at), run, capacities);
                node.edges.add(segment);
                if (segment.b != node) {
                    segment.b.edges.add(segment);
                }
            }
        }
        List<Endpoint> endpoints = new ArrayList<>();
        devices.forEach((pos, ds) -> ds.forEach(d -> endpoints.add(new Endpoint(d.getKey(), d.getValue(), nodes.get(pos)))));
        endpoints.sort(Comparator.comparingLong((Endpoint e) -> e.pos.asLong()).thenComparingInt(e -> e.side.ordinal()));
        for (FluidPipeBlockEntity pipe : pipes.values()) {
            if (pipe.network != null) {
                pipe.network.invalidate();
            }
        }
        FluidPipeNetwork network = new FluidPipeNetwork(level, pipes, nodes, List.copyOf(endpoints));
        for (FluidPipeBlockEntity pipe : pipes.values()) {
            pipe.network = network;
            PENDING.remove(pipe);
        }
        ACTIVE.add(network);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (FluidPipeBlockEntity pipe : List.copyOf(PENDING)) {
            Level level = pipe.getLevel();
            if (level == null || pipe.isRemoved() || !level.isLoaded(pipe.getBlockPos())) {
                PENDING.remove(pipe);
            } else if (pipe.network == null) {
                build(level, pipe);
            } else {
                PENDING.remove(pipe);
            }
        }
        // 受け渡しで水源を汲むとつなぎ替えが起きることがあるので、写しで回す
        for (FluidPipeNetwork network : List.copyOf(ACTIVE)) {
            if (!network.valid || network.level.getServer() != event.getServer()) {
                continue;
            }
            long now = network.level.getGameTime();
            if (now % INTERVAL == 0) {
                network.transfer();
            }
            if (now % DISPLAY == 0 && network.valid) {
                network.publish();
            }
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        ACTIVE.removeIf(n -> n.level == event.getLevel());
        PENDING.removeIf(p -> p.getLevel() == event.getLevel());
    }

    // ------------------------------------------------------------------ 受け渡し

    private void transfer() {
        used.clear();
        List<Port> ports = new ArrayList<>();
        List<Endpoint> sources = new ArrayList<>();
        for (Endpoint e : endpoints) {
            if (!level.isLoaded(e.pos)) {
                continue;
            }
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, e.pos, e.side);
            if (handler != null) {
                ports.add(new Port(e, handler, !(level.getBlockEntity(e.pos) instanceof PipeInputOnly)));
            } else if (isSource(level.getBlockState(e.pos))) {
                sources.add(e);
            }
        }
        if (ports.isEmpty()) {
            return;
        }
        rotation++;
        List<FluidStack> fluids = offeredFluids(ports);
        offered |= !fluids.isEmpty() || !sources.isEmpty();
        for (FluidStack fluid : fluids) {
            moveFluid(fluid, ports);
        }
        for (Endpoint source : sources) {
            pump(source, ports);
        }
    }

    /** 口が出せる液体の種類（重複なし）。 */
    private static List<FluidStack> offeredFluids(List<Port> ports) {
        List<FluidStack> out = new ArrayList<>();
        for (Port port : ports) {
            if (!port.drainable) {
                continue;
            }
            for (int t = 0; t < port.handler.getTanks(); t++) {
                FluidStack inside = port.handler.getFluidInTank(t);
                if (!inside.isEmpty() && out.stream().noneMatch(f -> FluidStack.isSameFluidSameComponents(f, inside))
                        && !port.handler.drain(inside.copyWithAmount(PROBE), FluidAction.SIMULATE).isEmpty()) {
                    out.add(inside.copyWithAmount(1));
                }
            }
        }
        return out;
    }

    private void moveFluid(FluidStack fluid, List<Port> ports) {
        FluidStack probe = fluid.copyWithAmount(PROBE);
        List<Port> pureGivers = new ArrayList<>();
        List<Port> stores = new ArrayList<>();
        List<Port> takers = new ArrayList<>();
        for (Port port : ports) {
            boolean gives = port.drainable && !port.handler.drain(probe, FluidAction.SIMULATE).isEmpty();
            boolean takes = accepts(port.handler, fluid) && port.handler.fill(probe, FluidAction.SIMULATE) > 0;
            if (gives && takes && port.handler instanceof PipeSplitTanks) {
                pureGivers.add(port);                      // 送るタンクと受け取りのタンクが別: 出す口でも受ける口でもある
                takers.add(port);
            } else if (gives && takes) {
                stores.add(port);
            } else if (gives) {
                pureGivers.add(port);
            } else if (takes) {
                takers.add(port);
            }
        }
        rotate(takers);
        rotate(stores);
        for (Port giver : pureGivers) {
            long left = push(giver, fluid, Integer.MAX_VALUE, takers);
            push(giver, fluid, left, stores);
        }
        for (Port giver : stores) {
            push(giver, fluid, Integer.MAX_VALUE, takers);
        }
    }

    /** giver から receivers へ順に、最大 budget を移す（経路の容量の残りまで）。返り値は移しきれなかった量。 */
    private long push(Port giver, FluidStack fluid, long budget, List<Port> receivers) {
        long left = budget;
        for (Port receiver : receivers) {
            if (left <= 0) {
                break;
            }
            if (receiver.pos().equals(giver.pos())) {
                continue;
            }
            Path path = path(giver.endpoint.node, receiver.endpoint.node);
            long room = Math.min(left, available(path));
            if (room <= 0) {
                continue;
            }
            int want = receiver.handler.fill(fluid.copyWithAmount((int) Math.min(Integer.MAX_VALUE, room)), FluidAction.SIMULATE);
            if (want <= 0) {
                continue;
            }
            FluidStack taken = giver.handler.drain(fluid.copyWithAmount(want), FluidAction.EXECUTE);
            if (taken.isEmpty()) {
                break;
            }
            int filled = receiver.handler.fill(taken, FluidAction.EXECUTE);
            if (filled > 0) {
                account(path, filled);
                sample(path, giver.pos(), receiver.pos(), taken, filled);
            }
            left -= filled;
            if (filled < taken.getAmount()) {
                // 見積もりより入らなかった分は元へ戻す（消さない）
                giver.handler.fill(taken.copyWithAmount(taken.getAmount() - filled), FluidAction.EXECUTE);
            }
        }
        return left;
    }

    /** 隣の水源を汲む。受け手と経路に源1個ぶんの空きがあるときだけ（余った分を捨てないように）。 */
    private void pump(Endpoint source, List<Port> ports) {
        BlockState state = level.getBlockState(source.pos);
        if (!isSource(state)) {
            return;
        }
        FluidStack fluid = new FluidStack(state.getFluidState().getType(), FluidType.BUCKET_VOLUME);
        // 別の面やマルチブロックの別ポートでも、同じタンクの空きを公開することがある。
        // capability から共有先は判別できないので、1つの口へ1バケツ全部が入るときだけ汲む。
        List<Port> takers = new ArrayList<>(ports);
        rotate(takers);
        for (Port taker : takers) {
            Path path = path(source.node, taker.endpoint.node);
            if (!accepts(taker.handler, fluid) || available(path) < fluid.getAmount()
                    || taker.handler.fill(fluid, FluidAction.SIMULATE) < fluid.getAmount()) {
                continue;
            }
            if (((BucketPickup) state.getBlock()).pickupBlock(null, level, source.pos, state).isEmpty()) {
                return;
            }
            int filled = taker.handler.fill(fluid, FluidAction.EXECUTE);
            if (filled > 0) {
                account(path, filled);
                sample(path, source.pos, taker.pos(), fluid, filled);
            }
            return;
        }
    }

    private static boolean accepts(IFluidHandler handler, FluidStack fluid) {
        return !(handler instanceof PipeFluidFilter filter) || filter.pipeAccepts(fluid);
    }

    private void rotate(List<Port> list) {
        if (list.size() > 1) {
            Collections.rotate(list, rotation % list.size());
        }
    }

    // ------------------------------------------------------------------ 容量

    private long remaining(Object key, long capacity, @Nullable Map<Object, Long> extra) {
        if (capacity == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long u = used.getOrDefault(key, 0L) + (extra == null ? 0 : extra.getOrDefault(key, 0L));
        return Math.max(0, capacity - u);
    }

    private long available(Path path) {
        return available(path, null);
    }

    private long available(Path path, @Nullable Map<Object, Long> extra) {
        long a = Long.MAX_VALUE;
        for (Node n : path.nodes) {
            a = Math.min(a, remaining(n, n.capacity, extra));
        }
        for (Segment s : path.segments) {
            a = Math.min(a, remaining(s, s.capacity, extra));
        }
        return a;
    }

    private void account(Path path, long amount) {
        reserve(used, path, amount);
    }

    private static void reserve(Map<Object, Long> map, Path path, long amount) {
        for (Node n : path.nodes) {
            map.merge(n, amount, FluidPipeNetwork::saturatedAdd);
        }
        for (Segment s : path.segments) {
            map.merge(s, amount, FluidPipeNetwork::saturatedAdd);
        }
    }

    /** 最短（パイプの本数）の木。区間をまとめたグラフの上で、根ごとに1回だけ作る。 */
    private Tree tree(Node root) {
        return trees.computeIfAbsent(root, r -> {
            Map<Node, Integer> distance = new IdentityHashMap<>();
            Map<Node, Segment> parent = new IdentityHashMap<>();
            PriorityQueue<Step> wave = new PriorityQueue<>(Comparator.comparingInt(Step::distance)
                    .thenComparingLong(step -> step.node.pos.asLong()));
            distance.put(root, 0);
            wave.add(new Step(root, 0));
            while (!wave.isEmpty()) {
                Step step = wave.remove();
                if (step.distance != distance.get(step.node)) {
                    continue;
                }
                for (Segment edge : step.node.edges) {
                    Node next = edge.other(step.node);
                    int d = step.distance + edge.length();
                    if (d < distance.getOrDefault(next, Integer.MAX_VALUE)) {
                        distance.put(next, d);
                        parent.put(next, edge);
                        wave.add(new Step(next, d));
                    }
                }
            }
            return new Tree(parent);
        });
    }

    private Path path(Node from, Node to) {
        return paths.computeIfAbsent(new RouteKey(from, to), key -> {
            Map<Node, Segment> parent = tree(to).parent;
            List<Node> ns = new ArrayList<>();
            List<Segment> es = new ArrayList<>();
            Node at = from;
            ns.add(at);
            while (at != to) {
                Segment edge = parent.get(at);
                if (edge == null) {
                    throw new IllegalStateException("Disconnected pipe graph");
                }
                es.add(edge);
                at = edge.other(at);
                ns.add(at);
            }
            return new Path(List.copyOf(ns), List.copyOf(es));
        });
    }

    // ------------------------------------------------------------------ 見た目

    private void sample(Path path, BlockPos from, BlockPos to, FluidStack fluid, long amount) {
        moved = true;
        samples.computeIfAbsent(new SampleKey(path, from, to), k -> new Sample()).add(fluid, amount);
    }

    /** 測量メガネをかけた人が近くにいるか（詰まりを見せるのは、そのときだけ）。 */
    private boolean observed() {
        AABB around = bounds.inflate(24);
        for (var player : level.players()) {
            if (around.contains(player.position()) && SurveyGlassesItem.wearing(player)) {
                return true;
            }
        }
        return false;
    }

    /** 流れた経路だけを広げて、パイプに見せる流れを決める。変わらないパイプには何も送らない。 */
    private void publish() {
        Map<Node, Flow> nodeFlow = new IdentityHashMap<>();
        Map<Segment, SegmentFlow> segmentFlow = new IdentityHashMap<>();
        for (var e : samples.entrySet()) {
            SampleKey key = e.getKey();
            Sample s = e.getValue();
            Path path = key.path;
            for (int i = 0; i < path.nodes.size(); i++) {
                Node node = path.nodes.get(i);
                Flow f = nodeFlow.computeIfAbsent(node, k -> new Flow());
                f.sample.add(s);
                BlockPos previous = i == 0 ? key.from : neighbor(path.segments.get(i - 1), node);
                BlockPos next = i == path.segments.size() ? key.to : neighbor(path.segments.get(i), node);
                f.in |= bit(direction(node.pos, previous));
                f.out |= bit(direction(node.pos, next));
                if (i < path.segments.size()) {
                    Segment edge = path.segments.get(i);
                    SegmentFlow sf = segmentFlow.computeIfAbsent(edge, k -> new SegmentFlow());
                    sf.sample.add(s);
                    if (node == edge.a) {
                        sf.forward = true;
                    } else {
                        sf.backward = true;
                    }
                }
            }
        }
        Set<BlockPos> current = new HashSet<>();
        nodeFlow.forEach((node, f) -> show(node.pos, 1, f.in, f.out, f.sample, current));
        segmentFlow.forEach((edge, sf) -> {
            for (int i = 1; i < edge.pipes.size() - 1; i++) {
                BlockPos pos = edge.pipes.get(i);
                int toA = bit(direction(pos, edge.pipes.get(i - 1))), toB = bit(direction(pos, edge.pipes.get(i + 1)));
                int in = (sf.forward ? toA : 0) | (sf.backward ? toB : 0);
                int out = (sf.forward ? toB : 0) | (sf.backward ? toA : 0);
                show(pos, 1, in, out, sf.sample, current);
            }
        });
        boolean blocked = offered && !moved && observed();
        if (blocked) {
            Sample none = new Sample();
            for (BlockPos pos : pipes.keySet()) {
                if (!current.contains(pos)) {
                    show(pos, 2, 0, 0, none, current);
                }
            }
        }
        for (BlockPos old : lit) {
            if (!current.contains(old)) {
                FluidPipeBlockEntity be = pipes.get(old);
                if (be != null && !be.isRemoved() && level.isLoaded(old)) {
                    be.show(0, 0, 0, 0, FluidStack.EMPTY, false);
                }
            }
        }
        lit.clear();
        lit.addAll(current);
        samples.clear();
        offered = false;
        moved = false;
    }

    private void show(BlockPos pos, int state, int in, int out, Sample s, Set<BlockPos> current) {
        FluidPipeBlockEntity be = pipes.get(pos);
        if (be == null || be.isRemoved() || !level.isLoaded(pos)) {
            return;
        }
        int perTick = (int) Math.min(Integer.MAX_VALUE, s.amount / DISPLAY);
        be.show(state, in, out, s.amount > 0 ? Math.max(1, perTick) : 0, s.fluid, s.mixed);
        current.add(pos);
    }

    private static BlockPos neighbor(Segment edge, Node node) {
        return node == edge.a ? edge.pipes.get(1) : edge.pipes.get(edge.pipes.size() - 2);
    }

    private static int bit(@Nullable Direction d) {
        return d == null ? 0 : 1 << d.get3DDataValue();
    }

    @Nullable
    private static Direction direction(BlockPos from, BlockPos to) {
        return Direction.fromDelta(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    private static long saturatedAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    // ------------------------------------------------------------------ テスト用

    /** このネットワークのパイプの数。 */
    int pipeCount() {
        return pipes.size();
    }

    /** 区間の数（長い1本は1つにまとまる）。 */
    int segmentCount() {
        return segments.size();
    }

    /** 分かれ目の数。 */
    int junctionCount() {
        return nodes.size();
    }

    /** 覚えている経路の数。 */
    int cachedPathCount() {
        return paths.size();
    }

    /** テスト用: pipe のネットワークの {パイプの数, 区間の数, 分かれ目の数}（まだ作られていなければ null）。 */
    @Nullable
    public static int[] statsForTest(FluidPipeBlockEntity pipe) {
        FluidPipeNetwork n = pipe.network;
        return n == null || !n.valid ? null : new int[]{n.pipeCount(), n.segmentCount(), n.junctionCount()};
    }

    /** pipe が属するネットワーク（まだ作られていなければ null）。 */
    @Nullable
    static FluidPipeNetwork of(FluidPipeBlockEntity pipe) {
        return pipe.network;
    }
}
