package io.github.genichimaruo.singulo.cable;

import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A network owns the topology, transfer budgets and display samples. Degree-two cable runs are
 * collapsed into segments: recurring transfer work scales with junctions/endpoints, not wire length.
 * Topology/path searches happen only after invalidation or the first request for an endpoint pair.
 */
public final class EnergyNetwork {
    static final double MIN_EFFICIENCY = 0.5;
    private static final java.util.concurrent.atomic.AtomicLong GENERATIONS = new java.util.concurrent.atomic.AtomicLong();
    private final long generation = GENERATIONS.incrementAndGet();
    private static final Set<EnergyNetwork> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());

    static final class Flow {
        long generation;
        long amount;
        int dist = Integer.MAX_VALUE;
        int in;
        int out;
    }

    /** A shared capacity constraint, either a junction cable or the interior of a cable run. */
    private static final class Budget {
        final long capacity;
        Budget(long capacity) { this.capacity = capacity; }
    }
    private static final class Node {
        final BlockPos pos;
        final Budget budget;
        final double loss;
        final List<Segment> edges = new ArrayList<>();
        Node(BlockPos pos, CableBlock cable) {
            this.pos = pos;
            this.budget = new Budget(cable.capacity());
            this.loss = cable.lossPerBlock();
        }
    }
    private static final class Segment {
        final Node a, b;
        /** Includes both end nodes, in a-to-b order. Used only when publishing the visual samples. */
        final List<BlockPos> cables;
        final Budget budget;
        final double loss;
        Segment(Node a, Node b, List<BlockPos> cables, Map<BlockPos, CableBlock> blocks) {
            this.a = a; this.b = b; this.cables = List.copyOf(cables);
            long capacity = Long.MAX_VALUE;
            double loss = 0;
            for (int i = 1; i < cables.size() - 1; i++) {
                var cable = blocks.get(cables.get(i));
                capacity = Math.min(capacity, cable.capacity());
                loss += cable.lossPerBlock();
            }
            this.budget = new Budget(capacity);
            this.loss = loss;
        }
        Node other(Node n) { return n == a ? b : a; }
        int length() { return cables.size() - 1; }
    }
    private record Link(BlockPos a, BlockPos b) {
        static Link of(BlockPos a, BlockPos b) {
            return a.asLong() < b.asLong() ? new Link(a,b) : new Link(b,a);
        }
    }
    private record Endpoint(BlockPos pos, Direction side, Node node) {}
    private record Path(List<Node> nodes, List<Segment> segments, int length, double efficiency) {}
    private record Candidate(Endpoint endpoint, int distance) {}
    private record Tree(Map<Node,Segment> parent, Map<Node,Integer> distance) {}
    private record Step(Node node, int distance) {}
    private record RouteKey(Node from, Node to) {}
    private record SampleKey(Path path, @Nullable BlockPos source, Endpoint target) {}
    private static final class Sample { double amount; }
    private static final class SegmentSample {
        double amount;
        boolean forward, backward;
        int fromA = Integer.MAX_VALUE, fromB = Integer.MAX_VALUE;
    }

    /** Per-tick accounting; simulation copies only this small endpoint/segment state, never cables. */
    private static final class State {
        final Map<Budget, Long> used = new IdentityHashMap<>();
        final Map<BlockPos, Long> sourceLimits = new HashMap<>(), paid = new HashMap<>();
        final Map<BlockPos, Long> receiverLimits = new HashMap<>(), received = new HashMap<>();
        State() {}
        State(State other) {
            used.putAll(other.used); sourceLimits.putAll(other.sourceLimits); paid.putAll(other.paid);
            receiverLimits.putAll(other.receiverLimits); received.putAll(other.received);
        }
    }

    private final Level level;
    private final Map<BlockPos, CableBlockEntity> cables;
    private final Map<BlockPos, CableBlock> cableTypes;
    private final Set<BlockPos> devicePositions = new HashSet<>();
    private final Map<BlockPos, Node> nodes;
    private final List<Endpoint> endpoints;
    private final Map<RouteKey, Path> paths = new HashMap<>();
    private final Map<Node, Tree> trees = new IdentityHashMap<>();
    private final Map<Endpoint, List<Candidate>> preferences = new HashMap<>();
    private final Map<Node, List<Endpoint>> targetOrder = new IdentityHashMap<>();
    private final Map<SampleKey, Sample> samples = new HashMap<>();
    private final Set<BlockPos> lit = new HashSet<>();
    private boolean valid = true;
    private long tick = -1, windowStart = -1, nextVisual;
    private State state = new State();
    /** Counts are exposed to GameTests to verify that a long run stays a single constraint. */
    private final Set<Segment> segments = Collections.newSetFromMap(new IdentityHashMap<>());

    private EnergyNetwork(Level level, Map<BlockPos, CableBlockEntity> cables, Map<BlockPos, Node> nodes,
                          List<Endpoint> endpoints, Map<BlockPos,CableBlock> cableTypes) {
        this.level = level; this.cables = cables; this.nodes = nodes;
        this.endpoints = endpoints; this.cableTypes = cableTypes;
        nodes.values().forEach(n -> segments.addAll(n.edges));
        endpoints.forEach(e -> devicePositions.add(e.pos));
    }

    static void build(Level level, BlockPos start) {
        Map<BlockPos, CableBlockEntity> cables = new HashMap<>();
        Map<BlockPos, CableBlock> blocks = new HashMap<>();
        Map<BlockPos, List<BlockPos>> adjacent = new HashMap<>();
        Map<BlockPos, List<Map.Entry<BlockPos,Direction>>> devices = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start); queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            if (!(level.getBlockEntity(pos) instanceof CableBlockEntity be)
                    || !(level.getBlockState(pos).getBlock() instanceof CableBlock cable)) continue;
            cables.put(pos,be); blocks.put(pos,cable);
            List<BlockPos> neighbors = new ArrayList<>();
            adjacent.put(pos,neighbors);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!level.isLoaded(next)) continue;
                if (level.getBlockEntity(next) instanceof CableBlockEntity) {
                    neighbors.add(next);
                    if (seen.add(next)) queue.add(next);
                } else if (level.getBlockEntity(next) != null
                        || level.getCapability(Capabilities.EnergyStorage.BLOCK,next,dir.getOpposite()) != null) {
                    // Keep potential endpoints: a port can acquire its capability after forming.
                    devices.computeIfAbsent(pos,p -> new ArrayList<>()).add(Map.entry(next,dir.getOpposite()));
                }
            }
        }
        Map<BlockPos,Node> nodes = new HashMap<>();
        for (BlockPos pos : cables.keySet()) {
            if (pos.equals(start) || adjacent.get(pos).size() != 2 || devices.containsKey(pos)) {
                nodes.put(pos,new Node(pos,blocks.get(pos)));
            }
        }
        Set<Link> links = new HashSet<>();
        for (Node node : nodes.values()) {
            for (BlockPos neighbor : adjacent.get(node.pos)) {
                if (!links.add(Link.of(node.pos,neighbor))) continue;
                List<BlockPos> run = new ArrayList<>();
                run.add(node.pos);
                BlockPos previous = node.pos, at = neighbor;
                while (!nodes.containsKey(at)) {
                    run.add(at);
                    List<BlockPos> next = adjacent.get(at);
                    BlockPos following = next.get(0).equals(previous) ? next.get(1) : next.get(0);
                    links.add(Link.of(at,following));
                    previous = at; at = following;
                }
                run.add(at);
                Segment segment = new Segment(node,nodes.get(at),run,blocks);
                node.edges.add(segment); segment.b.edges.add(segment);
            }
        }
        List<Endpoint> endpoints = new ArrayList<>();
        devices.forEach((pos, ds) -> ds.forEach(d -> endpoints.add(new Endpoint(d.getKey(),d.getValue(),nodes.get(pos)))));
        endpoints.sort(Comparator.comparingLong((Endpoint e) -> e.pos.asLong()).thenComparingInt(e -> e.side.ordinal()));
        EnergyNetwork network = new EnergyNetwork(level,cables,nodes,List.copyOf(endpoints),blocks);
        cables.values().forEach(be -> be.network = network);
    }

    boolean isValid() { return valid; }
    void invalidate() {
        if (!valid) return;
        valid = false;
        // Cut the light immediately, rather than leaving the last ten-tick sample visible.
        clearVisuals();
        // Release unloaded cable BEs even when the rest of an idle network stays loaded.
        for (CableBlockEntity cable : cables.values()) if (cable.network == this) cable.network = null;
        // No energy is buffered in the network, so splitting/removing it cannot lose stored energy.
    }
    boolean topologyChanged(BlockPos pos) {
        if (!level.isLoaded(pos)) return true;
        if (cables.containsKey(pos)) {
            return level.getBlockEntity(pos) != cables.get(pos) || level.getBlockState(pos).getBlock() != cableTypes.get(pos);
        }
        if (level.getBlockEntity(pos) instanceof CableBlockEntity) return true;
        boolean endpoint = level.getBlockEntity(pos) != null;
        if (!endpoint) for (Direction d : Direction.values()) {
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,d) != null) { endpoint = true; break; }
        }
        return endpoint != devicePositions.contains(pos);
    }
    long capacity(BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CableBlock cable ? cable.capacity() : 0;
    }
    /** A capability may be called directly on a run interior, without a physical adjacent device. */
    void ensureEntry(BlockPos pos) {
        if (nodes.containsKey(pos) || !valid || !cableTypes.containsKey(pos)) return;
        roll();
        Segment old = null;
        int at = -1;
        for (Segment edge : segments) {
            int i = edge.cables.indexOf(pos);
            if (i > 0 && i < edge.cables.size()-1) { old = edge; at = i; break; }
        }
        if (old == null) return;
        // Flush samples expressed in the old graph; energy remains entirely in the source/receiver.
        if (!samples.isEmpty()) publish();
        Node entry = new Node(pos,cableTypes.get(pos));
        Segment first = new Segment(old.a,entry,old.cables.subList(0,at+1),cableTypes);
        Segment second = new Segment(entry,old.b,old.cables.subList(at,old.cables.size()),cableTypes);
        old.a.edges.remove(old); old.b.edges.remove(old);
        old.a.edges.add(first); entry.edges.add(first); entry.edges.add(second); old.b.edges.add(second);
        nodes.put(pos,entry); segments.remove(old); segments.add(first); segments.add(second);
        long used = state.used.getOrDefault(old.budget,0L);
        state.used.remove(old.budget);
        if (used > 0) {
            state.used.put(first.budget,used); state.used.put(entry.budget,used); state.used.put(second.budget,used);
        }
        paths.clear(); trees.clear(); preferences.clear(); targetOrder.clear();
    }
    int segmentCount() { return segments.size(); }
    int junctionCount() { return nodes.size(); }
    int cachedPathCount() { return paths.size(); }
    int accountedConstraints() { return state.used.size(); }

    private void roll() {
        long now = level.getGameTime();
        if (now != tick) { state = new State(); tick = now; }
    }
    private IEnergyStorage capability(Endpoint e) {
        return level.isLoaded(e.pos) ? level.getCapability(Capabilities.EnergyStorage.BLOCK,e.pos,e.side) : null;
    }
    private static long extract(IEnergyStorage source, long amount, boolean simulate) {
        return source instanceof LongEnergyStorage big ? big.extractLong(amount,simulate)
                : source.extractEnergy((int)Math.min(Integer.MAX_VALUE,amount),simulate);
    }
    private long sourceLeft(State s, Endpoint e, IEnergyStorage cap, long offer) {
        long limit = s.sourceLimits.computeIfAbsent(e.pos,k -> cap != null && cap.canExtract() ? extract(cap,Long.MAX_VALUE,true) : Long.MAX_VALUE);
        if (offer > limit) { limit = offer; s.sourceLimits.put(e.pos,limit); }
        long available = Math.max(0,limit - s.paid.getOrDefault(e.pos,0L));
        return offer == 0 && cap != null && cap.canExtract() ? Math.min(available,extract(cap,Long.MAX_VALUE,true)) : available;
    }
    private long receiverLeft(State s, Endpoint e, IEnergyStorage cap) {
        long limit = LongEnergyStorage.receive(cap,Long.MAX_VALUE,true);
        limit = Math.max(limit,s.receiverLimits.getOrDefault(e.pos,0L));
        s.receiverLimits.put(e.pos,limit);
        return Math.max(0,limit-s.received.getOrDefault(e.pos,0L));
    }
    private static long remaining(State s, Budget b) { return b.capacity-s.used.getOrDefault(b,0L); }
    private static long available(State s, Path path) {
        long available = Long.MAX_VALUE;
        for (Node n : path.nodes) available = Math.min(available,remaining(s,n.budget));
        for (Segment edge : path.segments) available = Math.min(available,remaining(s,edge.budget));
        return available;
    }
    private static void account(State s, Path path, Endpoint source, Endpoint target, long paid, long received) {
        for (Node n : path.nodes) s.used.merge(n.budget,paid,Long::sum);
        for (Segment edge : path.segments) s.used.merge(edge.budget,paid,Long::sum);
        s.paid.merge(source.pos,paid,Long::sum); s.received.merge(target.pos,received,Long::sum);
    }

    /** Immediate FE compatibility, with receiver-side nearest-source priority independent of push order. */
    long distribute(BlockPos entry, @Nullable BlockPos source, long amount, boolean simulate) {
        if (amount <= 0 || !valid) return 0;
        Node node = nodes.get(entry);
        if (node == null) return 0;
        roll();
        State s = simulate ? new State(state) : state;
        Direction face = source == null ? Direction.DOWN : direction(source,entry);
        if (face == null) face = Direction.DOWN;
        Endpoint caller = new Endpoint(source == null ? entry : source,face,node);
        IEnergyStorage callerCap = source == null ? null : capability(caller);
        long callerLeft = Math.min(amount,sourceLeft(s,caller,callerCap,amount));
        long callerPaid = 0;
        List<Endpoint> targets = targetOrder.computeIfAbsent(node,n -> {
            List<Endpoint> ordered = new ArrayList<>(endpoints);
            ordered.sort(Comparator.comparingInt((Endpoint e) -> tree(n).distance.get(e.node))
                    .thenComparingLong(e -> e.pos.asLong()).thenComparingInt(e -> e.side.ordinal()));
            return List.copyOf(ordered);
        });
        Map<Endpoint,IEnergyStorage> caps = new HashMap<>();
        for (Endpoint target : targets) {
            if (target.pos.equals(caller.pos)) continue;
            IEnergyStorage receiver = caps.computeIfAbsent(target,this::capability);
            if (receiver == null || !receiver.canReceive() || receiverLeft(s,target,receiver) <= 0) continue;
            List<Candidate> candidates = preferences.computeIfAbsent(target,t -> {
                List<Candidate> choices = new ArrayList<>();
                for (Endpoint e : endpoints) if (!e.pos.equals(t.pos)) choices.add(new Candidate(e,tree(t.node).distance.get(e.node)));
                choices.sort(Comparator.comparingInt(Candidate::distance)
                        .thenComparingLong(c -> c.endpoint.pos.asLong()).thenComparingInt(c -> c.endpoint.side.ordinal()));
                return List.copyOf(choices);
            });
            Path callerPath = path(node,target.node);
            boolean offered = false;
            for (int i = 0; i <= candidates.size(); i++) {
                Candidate candidate = i == candidates.size() ? null : candidates.get(i);
                // Insert the offered FE into the same deterministic order, including synthetic FE callers.
                boolean before = candidate == null || compare(caller,callerPath,candidate) <= 0;
                if (!offered && before) {
                    offered = true;
                    long paid = transfer(s,caller,callerCap,target,receiver,callerPath,callerLeft,simulate,true);
                    callerLeft -= paid; callerPaid += paid;
                }
                if (receiverLeft(s,target,receiver) <= 0) break;
                if (candidate == null || candidate.endpoint.pos.equals(caller.pos)) continue;
                IEnergyStorage supplier = caps.computeIfAbsent(candidate.endpoint,this::capability);
                if (supplier == null || !supplier.canExtract()) continue;
                transfer(s,candidate.endpoint,supplier,target,receiver,path(candidate.endpoint.node,target.node),
                        sourceLeft(s,candidate.endpoint,supplier,0),simulate,false);
            }
        }
        return callerPaid;
    }

    private static int compare(Endpoint caller, Path path, Candidate other) {
        int order = Integer.compare(path.length,other.distance);
        if (order == 0) order = Long.compare(caller.pos.asLong(),other.endpoint.pos.asLong());
        return order != 0 ? order : Integer.compare(caller.side.ordinal(),other.endpoint.side.ordinal());
    }
    private long transfer(State s, Endpoint source, IEnergyStorage supplier, Endpoint target, IEnergyStorage receiver,
                          Path path, long offer, boolean simulate, boolean offered) {
        long budget = Math.min(offer,available(s,path));
        if (budget <= 0) return 0;
        long wanted = Math.min(receiverLeft(s,target,receiver),(long)(budget*path.efficiency));
        long received = LongEnergyStorage.receive(receiver,wanted,true);
        long paid = Math.min(budget,(long)Math.ceil(received/path.efficiency));
        if (paid <= 0) return 0;
        if (!simulate) {
            if (!offered) {
                paid = extract(supplier,paid,false);
                received = Math.min(received,(long)(paid*path.efficiency));
            }
            received = LongEnergyStorage.receive(receiver,received,false);
            if (offered) paid = Math.min(paid,(long)Math.ceil(received/path.efficiency));
        }
        account(s,path,source,target,paid,received);
        if (!simulate && paid > 0) {
            Sample sample = samples.computeIfAbsent(new SampleKey(path,source.pos,target),k -> new Sample());
            sample.amount += paid;
            if (windowStart < 0) windowStart = level.getGameTime();
            ACTIVE.add(this);
        }
        return paid;
    }

    /** Shortest cable-hop trees are built once on the compressed graph, never once per wire/tick. */
    private Tree tree(Node root) {
        return trees.computeIfAbsent(root,r -> {
            Map<Node,Integer> distance = new IdentityHashMap<>();
            Map<Node,Segment> parent = new IdentityHashMap<>();
            PriorityQueue<Step> wave = new PriorityQueue<>(Comparator.comparingInt(Step::distance)
                    .thenComparingLong(step -> step.node.pos.asLong()));
            distance.put(root,0); wave.add(new Step(root,0));
            while (!wave.isEmpty()) {
                Step step = wave.remove();
                if (step.distance != distance.get(step.node)) continue;
                for (Segment edge : step.node.edges) {
                    Node next = edge.other(step.node);
                    int d = step.distance+edge.length();
                    if (d < distance.getOrDefault(next,Integer.MAX_VALUE)) {
                        distance.put(next,d); parent.put(next,edge); wave.add(new Step(next,d));
                    }
                }
            }
            return new Tree(parent,distance);
        });
    }
    private Path path(Node from, Node to) {
        return paths.computeIfAbsent(new RouteKey(from,to),key -> {
            Map<Node,Segment> parent = tree(to).parent;
            List<Node> ns = new ArrayList<>(); List<Segment> es = new ArrayList<>();
            Node at = from; int length = 0; double loss = 0;
            ns.add(at);
            while (at != to) {
                Segment edge = parent.get(at);
                if (edge == null) throw new IllegalStateException("Disconnected cable graph");
                es.add(edge); length += edge.length(); loss += edge.loss;
                at = edge.other(at); ns.add(at); loss += at.loss;
            }
            return new Path(List.copyOf(ns),List.copyOf(es),length,Math.max(MIN_EFFICIENCY,1-loss));
        });
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (Iterator<EnergyNetwork> it = ACTIVE.iterator(); it.hasNext();) {
            EnergyNetwork n = it.next();
            if (!n.valid) { n.clearVisuals(); it.remove(); continue; }
            if (n.level.getServer() != event.getServer()) continue;
            if (n.level.getGameTime() >= n.nextVisual) {
                n.publish();
                n.nextVisual = n.level.getGameTime()+CableBlockEntity.WINDOW;
                if (n.lit.isEmpty() && n.samples.isEmpty()) it.remove();
            }
        }
    }
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        ACTIVE.removeIf(n -> n.level == event.getLevel());
    }
    private void clearVisuals() {
        for (BlockPos pos : lit) {
            CableBlockEntity be = cables.get(pos);
            if (!be.isRemoved() && level.isLoaded(pos)) be.showFlow(null);
        }
        lit.clear(); samples.clear();
    }
    /** Expand only the active display segments, once per window; electrical work never walks a run. */
    private void publish() {
        long now = level.getGameTime();
        double ticks = windowStart < 0 ? CableBlockEntity.WINDOW : Math.max(1,now-windowStart+1);
        Map<Node,Flow> nodeFlow = new IdentityHashMap<>();
        Map<Segment,SegmentSample> segmentFlow = new IdentityHashMap<>();
        for (var e : samples.entrySet()) {
            SampleKey sample = e.getKey(); Path path = sample.path;
            long amount = (long)Math.max(1,e.getValue().amount/ticks);
            int distance = 0;
            for (int i = 0; i < path.nodes.size(); i++) {
                Node node = path.nodes.get(i);
                Flow f = nodeFlow.computeIfAbsent(node,k -> new Flow());
                f.amount = saturatedAdd(f.amount,amount); f.dist = Math.min(f.dist,distance);
                BlockPos previous = i == 0 ? sample.source : neighbor(path.segments.get(i-1),node);
                BlockPos next = i == path.segments.size() ? sample.target.pos : neighbor(path.segments.get(i),node);
                f.in |= bit(previous == null ? null : direction(node.pos,previous));
                f.out |= bit(direction(node.pos,next));
                if (i < path.segments.size()) {
                    Segment edge = path.segments.get(i);
                    SegmentSample flow = segmentFlow.computeIfAbsent(edge,k -> new SegmentSample());
                    flow.amount += amount;
                    if (node == edge.a) { flow.forward = true; flow.fromA = Math.min(flow.fromA,distance); }
                    else { flow.backward = true; flow.fromB = Math.min(flow.fromB,distance); }
                    distance += edge.length();
                }
            }
        }
        Set<BlockPos> current = new HashSet<>();
        nodeFlow.forEach((node,flow) -> show(node.pos,flow,current));
        segmentFlow.forEach((edge,sample) -> {
            for (int i = 1; i < edge.cables.size()-1; i++) {
                BlockPos pos = edge.cables.get(i);
                Flow f = new Flow(); f.amount = (long)sample.amount;
                Direction toA = direction(pos,edge.cables.get(i-1)), toB = direction(pos,edge.cables.get(i+1));
                if (sample.forward) { f.in |= bit(toA); f.out |= bit(toB); f.dist = Math.min(f.dist,sample.fromA+i); }
                if (sample.backward) { f.in |= bit(toB); f.out |= bit(toA); f.dist = Math.min(f.dist,sample.fromB+edge.length()-i); }
                show(pos,f,current);
            }
        });
        for (BlockPos old : lit) if (!current.contains(old)) {
            CableBlockEntity be = cables.get(old);
            if (!be.isRemoved() && level.isLoaded(old)) be.showFlow(null);
        }
        lit.clear(); lit.addAll(current); samples.clear(); windowStart = now+1;
    }
    private static BlockPos neighbor(Segment edge, Node node) {
        return node == edge.a ? edge.cables.get(1) : edge.cables.get(edge.cables.size()-2);
    }
    private void show(BlockPos pos, Flow flow, Set<BlockPos> current) {
        flow.generation = generation;
        CableBlockEntity be = cables.get(pos);
        if (be != null && !be.isRemoved() && level.isLoaded(pos)) { be.showFlow(flow); current.add(pos); }
    }
    private static long saturatedAdd(long a, long b) { return Long.MAX_VALUE-a < b ? Long.MAX_VALUE : a+b; }
    private static int bit(@Nullable Direction d) { return d == null ? 0 : 1 << d.get3DDataValue(); }
    @Nullable private static Direction direction(BlockPos from, BlockPos to) {
        return Direction.fromDelta(to.getX()-from.getX(),to.getY()-from.getY(),to.getZ()-from.getZ());
    }
}
