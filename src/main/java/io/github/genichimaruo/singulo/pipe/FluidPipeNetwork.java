package io.github.genichimaruo.singulo.pipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * つながった液体パイプのひとまとまり。液体は溜めず、周期ごとに口どうしで直接受け渡す。パイプ自体は tick しないので、
 * 長さは毎周期の仕事に効かない（つなぎ直したときに1回たどるだけ）。
 * <p>
 * 液体ごとに、出すだけの口 → 受けるだけの口 → 出し入れできる口（タンク）の順に配る。タンクからは受けるだけの口へしか
 * 出さないので、タンクどうしで行き来し続けない。どの面が出し入れできるかは装置の capability（面の設定）が決める。
 */
public final class FluidPipeNetwork {
    /** 受け渡しの周期（tick）。 */
    static final int INTERVAL = 5;
    /** 1つの口が1周期に出せる量（mB）。 */
    static final int PER_PUSH = 4_000;

    private static final Set<FluidPipeNetwork> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    /** まだネットワークに入っていないパイプ。読み込みやつなぎ替えのたびに作り直さず、次の tick にまとめて作る。 */
    private static final Set<FluidPipeBlockEntity> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());

    private record Endpoint(BlockPos pos, Direction side) {}
    /** このパイプの周期で見えている口。 */
    private record Port(BlockPos pos, Direction side, IFluidHandler handler, boolean drainable) {}

    private final Level level;
    private final Set<FluidPipeBlockEntity> pipes;
    private final List<Endpoint> endpoints;
    private boolean valid = true;
    private int rotation;
    private final FluidPipeObservation observation;
    private boolean visualMoved;

    private FluidPipeNetwork(Level level, Set<FluidPipeBlockEntity> pipes, List<Endpoint> endpoints) {
        this.level = level;
        this.pipes = pipes;
        this.endpoints = endpoints;
        observation=new FluidPipeObservation(level,pipes);
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
                || Capabilities.get(level, Capabilities.FluidHandler.BLOCK, pos, null) != null;
    }

    /** 汲める水源か（水・溶岩の源。流れている水や、水没していないブロックは含まない）。 */
    static boolean isSource(BlockState state) {
        return state.getFluidState().isSource() && state.getBlock() instanceof BucketPickup;
    }

    boolean hasEndpoint(BlockPos pos) {
        for (Endpoint e : endpoints) {
            if (e.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    void invalidate() {
        if (!valid) {
            return;
        }
        valid = false;
        ACTIVE.remove(this);
        for (FluidPipeBlockEntity pipe : pipes) {
            pipe.network = null;
            if (!pipe.isRemoved()) {
                PENDING.add(pipe);
            }
        }
    }

    private static void build(Level level, FluidPipeBlockEntity start) {
        Set<FluidPipeBlockEntity> pipes = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Endpoint> endpoints = new ArrayList<>();
        ArrayDeque<FluidPipeBlockEntity> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start.getBlockPos());
        queue.add(start);
        while (!queue.isEmpty()) {
            FluidPipeBlockEntity pipe = queue.remove();
            pipes.add(pipe);
            for (Direction d : Direction.values()) {
                BlockPos next = pipe.getBlockPos().relative(d);
                // 読み込まれていない先は強制的に読み込まない（読み込まれたときに作り直す）
                if (!level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockEntity(next) instanceof FluidPipeBlockEntity other) {
                    if (seen.add(next)) {
                        queue.add(other);
                    }
                } else if (mayBeEndpoint(level, next)) {
                    endpoints.add(new Endpoint(next, d.getOpposite()));
                }
            }
        }
        FluidPipeNetwork network = new FluidPipeNetwork(level, pipes, endpoints);
        for (FluidPipeBlockEntity pipe : pipes) {
            if (pipe.network != null) {
                pipe.network.invalidate();
            }
        }
        for (FluidPipeBlockEntity pipe : pipes) {
            pipe.network = network;
            PENDING.remove(pipe);
        }
        ACTIVE.add(network);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
            if (network.valid && network.level.getServer() == event.getServer()
                    && network.level.getGameTime() % INTERVAL == 0) {
                network.transfer();
            }
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        ACTIVE.removeIf(n -> n.level == event.getLevel());
        PENDING.removeIf(p -> p.getLevel() == event.getLevel());
    }

    private void transfer() {
        observation.begin();visualMoved=false;
        List<Port> ports = new ArrayList<>();
        List<BlockPos> sources = new ArrayList<>();
        for (Endpoint e : endpoints) {
            if (!level.isLoaded(e.pos)) {
                continue;
            }
            IFluidHandler handler = Capabilities.get(level, Capabilities.FluidHandler.BLOCK, e.pos, e.side);
            if (handler != null) {
                ports.add(new Port(e.pos, e.side, handler, !(level.getBlockEntity(e.pos) instanceof PipeInputOnly)));
            } else if (isSource(level.getBlockState(e.pos))) {
                sources.add(e.pos);
            }
        }
        rotation++;
        List<FluidStack> offered=offeredFluids(ports);
        for (FluidStack fluid : offered) {
            moveFluid(fluid, ports);
        }
        for (BlockPos source : sources) {
            pump(source, ports);
        }
        observation.end(!offered.isEmpty()||!sources.isEmpty(),visualMoved);
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
                if (!inside.isEmpty() && out.stream().noneMatch(f -> io.github.genichimaruo.singulo.compat.Legacy.sameFluid(f, inside))
                        && !port.handler.drain(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(inside,PER_PUSH), FluidAction.SIMULATE).isEmpty()) {
                    out.add(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(inside,1));
                }
            }
        }
        return out;
    }

    private void moveFluid(FluidStack fluid, List<Port> ports) {
        FluidStack probe = io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(fluid,PER_PUSH);
        List<Port> pureGivers = new ArrayList<>();
        List<Port> stores = new ArrayList<>();
        List<Port> takers = new ArrayList<>();
        for (Port port : ports) {
            boolean gives = port.drainable && !port.handler.drain(probe, FluidAction.SIMULATE).isEmpty();
            boolean takes = accepts(port.handler, fluid) && port.handler.fill(probe, FluidAction.SIMULATE) > 0;
            if (gives && takes) {
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
            int left = push(giver, fluid, PER_PUSH, takers);
            push(giver, fluid, left, stores);
        }
        for (Port giver : stores) {
            push(giver, fluid, PER_PUSH, takers);
        }
    }

    /** giver から receivers へ順に、最大 budget を移す。返り値は移しきれなかった量。 */
    private int push(Port giver, FluidStack fluid, int budget, List<Port> receivers) {
        int left = budget;
        for (Port receiver : receivers) {
            if (left <= 0) {
                break;
            }
            if (receiver.pos.equals(giver.pos)) {
                continue;
            }
            int room = receiver.handler.fill(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(fluid,left), FluidAction.SIMULATE);
            if (room <= 0) {
                continue;
            }
            FluidStack taken = giver.handler.drain(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(fluid,room), FluidAction.EXECUTE);
            if (taken.isEmpty()) {
                break;
            }
            int filled = receiver.handler.fill(taken, FluidAction.EXECUTE);
            if(filled>0){observation.moved(giver.pos,giver.pos.relative(giver.side),receiver.pos.relative(receiver.side),receiver.pos,taken,filled);visualMoved=true;}
            left -= filled;
            if (filled < taken.getAmount()) {
                // 見積もりより入らなかった分は元へ戻す（消さない）
                giver.handler.fill(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(taken,taken.getAmount() - filled), FluidAction.EXECUTE);
            }
        }
        return left;
    }

    /** 隣の水源を汲む。受け手に源1個ぶんの空きがあるときだけ（余った分を捨てないように）。 */
    private void pump(BlockPos pos, List<Port> ports) {
        BlockState state = level.getBlockState(pos);
        if (!isSource(state)) {
            return;
        }
        FluidStack fluid = new FluidStack(state.getFluidState().getType(), FluidType.BUCKET_VOLUME);
        List<Port> takers = new ArrayList<>();
        int room = 0;
        for (Port port : ports) {
            int r = accepts(port.handler, fluid) ? port.handler.fill(fluid, FluidAction.SIMULATE) : 0;
            if (r > 0) {
                takers.add(port);
                room += r;
            }
        }
        if (room < fluid.getAmount()
                || ((BucketPickup) state.getBlock()).pickupBlock(level, pos, state).isEmpty()) {
            return;
        }
        rotate(takers);
        int left = fluid.getAmount();
        for (Port taker : takers) {
            if (left > 0) {
                int filled=taker.handler.fill(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(fluid,left), FluidAction.EXECUTE);
                if(filled>0){observation.moved(pos,taker.pos,fluid,filled);visualMoved=true;}
                left -= filled;
            }
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

    /** テスト用: このネットワークのパイプの数。 */
    int pipeCount() {
        return pipes.size();
    }

    /** テスト用: pipe が属するネットワーク（まだ作られていなければ null）。 */
    @Nullable
    static FluidPipeNetwork of(FluidPipeBlockEntity pipe) {
        return pipe.network;
    }
}
