package io.github.genichimaruo.singulo.monitor;

import io.github.genichimaruo.singulo.drone.DroneStationBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineReport;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 工場モニター（0.21.0。机のモニターを使うと開く）: まわりの装置の様子をまとめる。範囲は {@link #BASE_RANGE} ブロックで、
 * モニターのそば（{@link #RACK_REACH} ブロック以内）のサーバーラック1台ごとに {@link #RACK_RANGE} 広がる（{@link #MAX_RACKS} 台まで）。
 * <ul>
 * <li>電力: 範囲の装置が今作っている・使っている電力の合計と、蓄電の合計（{@link MachineReport} を出せる装置だけ）</li>
 * <li>止まっている装置: 手を入れたほうがよい装置（{@link MachineReport.State#stalled}）を近い順に {@link #MAX_STALLED} 台まで</li>
 * <li>ドローン: ステーションごとの運搬中・待機中の数と、困っていること</li>
 * </ul>
 * 読み込まれているチャンクの中だけを見る（チャンクを読み込まない）。
 */
public final class FactoryMonitor {
    public static final int BASE_RANGE = 16, RACK_RANGE = 8, MAX_RACKS = 4, RACK_REACH = 2, MAX_STALLED = 64;
    /** 画面を開いていられる、モニターからの距離（ブロック）。 */
    public static final double USE_DISTANCE = 8;

    private FactoryMonitor() {}

    /** 止まっている装置1台: 装置の物（名前と絵）・場所・状態・理由。 */
    public record Stalled(ItemStack icon, BlockPos pos, MachineReport.State state, Component reason) {
        static final StreamCodec<RegistryFriendlyByteBuf, Stalled> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Stalled::icon, BlockPos.STREAM_CODEC, Stalled::pos,
                ByteBufCodecs.VAR_INT.map(i -> MachineReport.State.values()[Math.floorMod(i, MachineReport.State.values().length)],
                        MachineReport.State::ordinal), Stalled::state,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Stalled::reason, Stalled::new);
    }

    /** ドローン・ステーション1台: 運搬中・待機中・困っている枠の数と、困っていることの1つ（なければ空）。 */
    public record Drones(BlockPos pos, int flying, int idle, int trouble, Component problem) {
        static final StreamCodec<RegistryFriendlyByteBuf, Drones> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Drones::pos, ByteBufCodecs.VAR_INT, Drones::flying, ByteBufCodecs.VAR_INT, Drones::idle,
                ByteBufCodecs.VAR_INT, Drones::trouble, ComponentSerialization.TRUSTED_STREAM_CODEC, Drones::problem, Drones::new);
    }

    /** まとめ（モニターの画面に送る）。 */
    public record Snapshot(BlockPos monitor, int range, int racks, int machines, int running, long produce, long consume, long stored,
                           long capacity, List<Stalled> stalled, List<Drones> drones) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            @Override
            public Snapshot decode(RegistryFriendlyByteBuf buf) {
                return new Snapshot(BlockPos.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                        Stalled.CODEC.apply(ByteBufCodecs.list(MAX_STALLED)).decode(buf),
                        Drones.CODEC.apply(ByteBufCodecs.list(256)).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Snapshot s) {
                BlockPos.STREAM_CODEC.encode(buf, s.monitor());
                buf.writeVarInt(s.range());
                buf.writeVarInt(s.racks());
                buf.writeVarInt(s.machines());
                buf.writeVarInt(s.running());
                buf.writeVarLong(s.produce());
                buf.writeVarLong(s.consume());
                buf.writeVarLong(s.stored());
                buf.writeVarLong(s.capacity());
                Stalled.CODEC.apply(ByteBufCodecs.list(MAX_STALLED)).encode(buf, s.stalled());
                Drones.CODEC.apply(ByteBufCodecs.list(256)).encode(buf, s.drones());
            }
        };
    }

    /** モニターのそばのサーバーラックの数（{@link #MAX_RACKS} まで）。 */
    public static int racks(ServerLevel level, BlockPos monitor) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(monitor.offset(-RACK_REACH, -RACK_REACH, -RACK_REACH),
                monitor.offset(RACK_REACH, RACK_REACH, RACK_REACH))) {
            if (level.getBlockState(p).is(SinguloBlocks.SERVER_RACK.get()) && ++n >= MAX_RACKS) {
                break;
            }
        }
        return n;
    }

    public static Snapshot scan(ServerLevel level, BlockPos monitor) {
        int racks = racks(level, monitor);
        int range = BASE_RANGE + racks * RACK_RANGE;
        int machines = 0, running = 0;
        long produce = 0, consume = 0, stored = 0, capacity = 0;
        List<Stalled> stalled = new ArrayList<>();
        List<Drones> drones = new ArrayList<>();
        for (int cx = (monitor.getX() - range) >> 4; cx <= (monitor.getX() + range) >> 4; cx++) {
            for (int cz = (monitor.getZ() - range) >> 4; cz <= (monitor.getZ() + range) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - monitor.getX()) > range || Math.abs(p.getY() - monitor.getY()) > range
                            || Math.abs(p.getZ() - monitor.getZ()) > range) {
                        continue;
                    }
                    if (be instanceof DroneStationBlockEntity station) {
                        drones.add(drones(station));
                        continue;
                    }
                    MachineReport r = report(level, be);
                    if (r != null) {
                        machines++;
                        if (r.state() == MachineReport.State.RUN) {
                            running++;
                        }
                        produce += r.produce();
                        consume += r.consume();
                        stored += r.energy();
                        capacity += r.capacity();
                        if (r.state().stalled()) {
                            stalled.add(new Stalled(new ItemStack(be.getBlockState().getBlock().asItem()), p.immutable(), r.state(), r.label()));
                        }
                    }
                }
            }
        }
        Comparator<BlockPos> near = Comparator.comparingDouble(p -> p.distSqr(monitor));
        stalled.sort((a, b) -> near.compare(a.pos(), b.pos()));
        if (stalled.size() > MAX_STALLED) {
            stalled = new ArrayList<>(stalled.subList(0, MAX_STALLED));
        }
        drones.sort((a, b) -> near.compare(a.pos(), b.pos()));
        return new Snapshot(monitor.immutable(), range, racks, machines, running, produce, consume, stored, capacity, stalled, drones);
    }

    /**
     * 1台の報告。報告を出す装置はその報告。ほかは、マルチブロックのコントローラー（組み上がっていなければ未形成）か、電力を持つ装置
     * （電力の量だけ）。マルチブロックの部品（コントローラーと二重に数える）・ケーブル・パイプ・クリエイティブの電源は数えない。
     */
    @javax.annotation.Nullable
    private static MachineReport report(ServerLevel level, BlockEntity be) {
        if (be instanceof MachineReport.Source source) {
            return source.report();
        }
        var block = be.getBlockState().getBlock();
        if (block instanceof io.github.genichimaruo.singulo.multiblock.MultiblockPart
                || be instanceof io.github.genichimaruo.singulo.cable.CableBlockEntity
                || be instanceof io.github.genichimaruo.singulo.pipe.FluidPipeBlockEntity
                || be instanceof io.github.genichimaruo.singulo.machine.CreativeEnergyBlockEntity) {
            return null;
        }
        BlockPos p = be.getBlockPos();
        if (io.github.genichimaruo.singulo.multiblock.Blueprints.kindOf(block) == null
                && level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, p, null) == null) {
            return null;
        }
        return MachineReport.of(level, p);
    }

    /** ステーションの枠の数え方:運搬中・待機中と、困っている枠（メモリ・キットがない、電力不足、上がふさがっている）。 */
    private static Drones drones(DroneStationBlockEntity station) {
        int flying = 0, idle = 0, trouble = 0;
        Component problem = Component.empty();
        for (int i = 0; i < station.units(); i++) {
            int s = station.status(i);
            switch (s) {
                case DroneStationBlockEntity.S_FLYING -> flying++;
                case DroneStationBlockEntity.S_IDLE, DroneStationBlockEntity.S_PRINTING -> idle++;
                case DroneStationBlockEntity.S_NO_MEMORY, DroneStationBlockEntity.S_NO_KIT, DroneStationBlockEntity.S_NO_ENERGY,
                     DroneStationBlockEntity.S_BLOCKED -> {
                    trouble++;
                    if (problem.getString().isEmpty()) {
                        problem = Component.translatable("gui.singulo.drone_station.row." + s);
                    }
                }
                default -> { }
            }
        }
        return new Drones(station.getBlockPos().immutable(), flying, idle, trouble, problem);
    }
}
