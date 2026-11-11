package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * ワームホール便: ドローンで別の次元のポートへ荷物を送る。両方の口のそばにワームホール航路標識を置いた、開いているワームホールだけを使う。
 * <ol>
 *   <li>発送元のポートは、こちらの口を行き先にして近くのドローンを呼ぶ（便の relayDim・relayTo に最終の行き先）。</li>
 *   <li>ドローンが口に着くと、荷物を向こうの口のリレー（向こうの次元の {@link DroneNetwork.Relay}）に渡して帰る。</li>
 *   <li>向こうの口は、そばのステーションのドローンを呼ぶ。ドローンが口で荷物を受け取り、行き先のポートへ届ける。</li>
 * </ol>
 * 荷物の正は、便の記録 → 向こうの口のリレー → 向こうの便の記録 → 行き先のポートの順に、いつも1か所にある。
 * 見えない飛行で口に着いたときも、口が読み込まれていれば同じように受け渡す。向こうの口が別の次元へつなぎ直されていたら、
 * 荷物は発送元のポートへ戻す。口を壊すと、そこで待っていた荷物は梱包済みの箱のまま落とす。
 */
public final class WormholeRelay {
    private WormholeRelay() {}

    /** 別の次元の行き先: 行き先のポート、その次元、こちらの口・向こうの口、道のり（こちらのポート → 口、向こうの口 → 行き先）、名前。 */
    public record Route(BlockPos to, ResourceKey<Level> dim, BlockPos mouthHere, BlockPos mouthThere, int distance, String name) {}

    /** from（このポート）から、ワームホールを通って行ける同じ持ち主・同じ種類（物か液体か）のポート（近い順）。 */
    public static List<Route> routes(ServerLevel level, @Nullable UUID owner, BlockPos from, boolean fluid) {
        Map<String, Route> best = new LinkedHashMap<>();
        for (WormholeMouthBlockEntity here : WormholeMouthBlockEntity.loaded(level)) {
            if (here.isRemoved() || !here.droneGate()) {
                continue;
            }
            WormholeMouthBlockEntity there = here.partner();
            if (there == null || !there.droneGate() || !(there.getLevel() instanceof ServerLevel other)
                    || other.dimension().equals(level.dimension())) {
                continue;
            }
            DroneNetwork net = DroneNetwork.get(other);
            double leg = Math.sqrt(from.distSqr(here.getBlockPos()));
            for (BlockPos to : net.portsOf(owner, there.getBlockPos(), fluid)) {
                int d = (int) Math.ceil(leg + Math.sqrt(there.getBlockPos().distSqr(to)));
                String key = other.dimension().location() + "|" + to.asLong();
                Route old = best.get(key);
                if (old == null || d < old.distance()) {
                    DroneNetwork.Port info = net.port(to);
                    best.put(key, new Route(to.immutable(), other.dimension(), here.getBlockPos().immutable(),
                            there.getBlockPos().immutable(), d, info == null ? "" : info.name()));
                }
            }
        }
        List<Route> out = new ArrayList<>(best.values());
        out.sort(java.util.Comparator.comparingInt(Route::distance));
        return out;
    }

    /** dim の to への、いちばん近い道（なければ null）。 */
    @Nullable
    public static Route route(ServerLevel level, @Nullable UUID owner, BlockPos from, ResourceKey<Level> dim, BlockPos to, boolean fluid) {
        for (Route r : routes(level, owner, from, fluid)) {
            if (r.dim().equals(dim) && r.to().equals(to)) {
                return r;
            }
        }
        return null;
    }

    /** 向こうの口のそばに、荷物を受け取りに来られるドローンがいるか。 */
    public static boolean dronesThere(ServerLevel level, Route route, @Nullable UUID owner) {
        ServerLevel other = level.getServer().getLevel(route.dim());
        return other != null && DroneStationBlockEntity.available(other, route.mouthThere(), owner);
    }

    // ------------------------------------------------------------------ 口での受け渡し

    /**
     * こちらの口に着いた便（届ける段階・relayTo あり）: 荷物を向こうの口のリレーに渡し、便は帰りの段階へ。
     * 口が閉じている・標識がない・向こうが読み込まれていないときは false（ドローンは口の上で待つ）。
     */
    public static boolean handOff(ServerLevel level, DroneNetwork net, DroneNetwork.Flight f) {
        if (!(level.getBlockEntity(f.to) instanceof WormholeMouthBlockEntity here) || !here.droneGate() || f.relayTo == null) {
            return false;
        }
        WormholeMouthBlockEntity there = here.partner();
        if (there == null || !(there.getLevel() instanceof ServerLevel other) || !there.droneGate()) {
            return false;
        }
        if (!other.dimension().equals(f.relayDim)) {
            // 口が別の次元へつなぎ直された: 荷物を発送元のポートへ戻す
            BlockPos home = f.from;
            f.from = f.to;
            f.to = home;
            f.relayDim = null;
            f.relayTo = null;
            net.changed();
            return true;
        }
        DroneNetwork.get(other).addRelay(there.getBlockPos(), f.crate, f.relayTo, f.owner);
        f.crate = ItemStack.EMPTY;
        f.relayDim = null;
        f.relayTo = null;
        net.advance(f, DroneNetwork.RETURN, level.getGameTime());
        level.playSound(null, here.getBlockPos(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 1.4F);
        other.playSound(null, there.getBlockPos(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 1.2F);
        return true;
    }

    /** 向こうの口に荷物を取りに来た便: リレーの荷物を積んで、届ける段階へ。リレーがなければ false。 */
    public static boolean pickUp(ServerLevel level, DroneNetwork net, DroneNetwork.Flight f) {
        DroneNetwork.Relay r = net.relayOfFlight(f.id);
        if (r == null || !r.mouth.equals(f.from)) {
            return false;
        }
        f.crate = r.crate;
        net.removeRelay(r);
        net.advance(f, DroneNetwork.DELIVER, level.getGameTime());
        level.playSound(null, r.mouth, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.5F, 1.6F);
        return true;
    }

    /** この位置がワームホールの口か（ドローンが受け渡しをする口）。 */
    public static boolean isMouth(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WormholeMouthBlockEntity;
    }

    /**
     * 口ごとに10 tick ごと: 見えないまま口に着いた便の受け渡しと、待っている荷物のためにドローンを呼ぶこと。
     */
    public static void tickMouth(ServerLevel level, WormholeMouthBlockEntity mouth) {
        DroneNetwork net = DroneNetwork.get(level);
        BlockPos pos = mouth.getBlockPos();
        long now = level.getGameTime();
        for (DroneNetwork.Flight f : List.copyOf(net.flightsAt(pos, now))) {
            if (!f.arrived(now)) {
                continue;
            }
            boolean deliver = f.stage == DroneNetwork.DELIVER && f.relayTo != null && pos.equals(f.to);
            boolean pickup = f.stage == DroneNetwork.PICKUP && pos.equals(f.from) && net.relayOfFlight(f.id) != null;
            if (!deliver && !pickup) {
                continue;
            }
            // 見える所なら、ポートと同じくドローンを出して口の上で受け渡させる
            if (level.isPositionEntityTicking(pos.above()) && CargoDrone.materialize(level, net, f)) {
                continue;
            }
            if (deliver) {
                handOff(level, net, f);
            } else {
                pickUp(level, net, f);
            }
        }
        for (DroneNetwork.Relay r : net.relaysAt(pos)) {
            DroneNetwork.Flight f = r.flight == 0 ? null : net.flight(r.flight);
            if (f == null || f.stage != DroneNetwork.PICKUP || !pos.equals(f.from)) {
                if (r.flight != 0) {
                    r.flight = 0;
                    net.changed();
                }
                if (now % 20 == 0) {
                    DroneNetwork.Flight called = DroneStationBlockEntity.call(level, pos, r.owner, r.to);
                    if (called != null) {
                        r.flight = called.id;
                        net.changed();
                    }
                }
            }
        }
    }

    /** 口が壊れた: そこで待っていた荷物は、梱包済みの箱のまま落とす。 */
    public static void mouthRemoved(ServerLevel level, BlockPos pos) {
        DroneNetwork net = DroneNetwork.get(level);
        for (DroneNetwork.Relay r : net.relaysAt(pos)) {
            Block.popResource(level, pos, r.crate);
            net.removeRelay(r);
        }
    }
}
