package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * ワームホール便: ドローンがワームホールの口をそのままくぐり、別の次元のポートへ荷物を届けて、同じ口を通って自分のステーションへ帰る。
 * 両方の口のそばにワームホール航路標識を置いた、開いているワームホールだけを使う。向こう側にステーションは要らない。
 * <ol>
 *   <li>発送元のポートは、こちらの口を行き先にして近くのドローンを呼ぶ（便の relayDim・relayTo に最終の行き先）。</li>
 *   <li>ドローンが口に入ると、便の記録は向こうの次元の網へ移り（{@link DroneNetwork#adopt}）、ドローンは向こうの口から出て行き先へ届ける。
 *       家のステーションの網には「別の次元へ出ている」印が残る（ステーションは印刷し直さない）。</li>
 *   <li>届け終えたドローンは、向こうの口（{@link DroneNetwork.Flight#homeGate}）へ戻ってくぐり、家の網の便に戻ってステーションへ帰る。</li>
 * </ol>
 * 便の記録と荷物はいつもどちらか一方の網にだけある。見えない飛行で口に着いたときも、口が読み込まれていれば同じようにくぐる。
 * 口が閉じている・標識がない・向こうが読み込まれていないときは、口の上で待つ。行きで口が別の次元へつなぎ直されていたら、荷物を発送元へ戻す。
 */
public final class DroneWormhole {
    private DroneWormhole() {}

    /** ドローンがくぐる高さ（口のブロックの上面から）。 */
    static final double GATE_LIFT = 0.6;

    /** 別の次元の行き先: 行き先のポート、その次元、こちらの口・向こうの口、道のり（こちらのポート → 口、向こうの口 → 行き先）、名前。 */
    public record Route(BlockPos to, ResourceKey<Level> dim, BlockPos mouthHere, BlockPos mouthThere, int distance, String name) {}

    /** from（このポート）から、ワームホールを通って行ける同じ持ち主・同じ種類（物か液体か）のポート（近い順）。 */
    public static List<Route> routes(ServerLevel level, @Nullable UUID owner, BlockPos from, boolean fluid) {
        Map<String, Route> best = new LinkedHashMap<>();
        for (WormholeMouthBlockEntity here : WormholeMouthBlockEntity.loaded(level)) {
            if (here.isRemoved() || !here.droneGate()) {
                continue;
            }
            // 向こうの口は読み込まれていなくてもよい（ほかの次元は、プレイヤーがいなければふつう読み込まれていない）。
            // 読み込まれていれば標識を確かめ、いなければ、くぐるときに読み込ませて確かめる
            net.minecraft.core.GlobalPos far = here.partnerPos();
            ServerLevel other = far == null ? null : level.getServer().getLevel(far.dimension());
            if (other == null || other.dimension().equals(level.dimension())) {
                continue;
            }
            WormholeMouthBlockEntity there = here.partner();
            if (there != null && !there.droneGate()) {
                continue;
            }
            BlockPos mouthThere = far.pos();
            DroneNetwork net = DroneNetwork.get(other);
            double leg = Math.sqrt(from.distSqr(here.getBlockPos()));
            for (BlockPos to : net.portsOf(owner, mouthThere, fluid)) {
                int d = (int) Math.ceil(leg + Math.sqrt(mouthThere.distSqr(to)));
                String key = other.dimension().location() + "|" + to.asLong();
                Route old = best.get(key);
                if (old == null || d < old.distance()) {
                    DroneNetwork.Port info = net.port(to);
                    best.put(key, new Route(to.immutable(), other.dimension(), here.getBlockPos().immutable(),
                            mouthThere.immutable(), d, info == null ? "" : info.name()));
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

    // ------------------------------------------------------------------ 同じ次元の近道

    /** 近道に使うのは、口をくぐるほうがこれ（ブロック）より近いときだけ（わずかな差で寄り道しない）。 */
    static final double SHORTCUT_MARGIN = 8;

    /**
     * 同じ次元の中で from から to へ飛ぶとき、ワームホールをくぐるほうが近ければ入る口（なければ null）。両方の口が同じ次元にあって
     * 読み込まれていて、両方のそばに航路標識があるワームホールだけを使う。
     */
    @Nullable
    public static BlockPos shortcut(ServerLevel level, Vec3 from, Vec3 to) {
        double best = from.distanceTo(to) - SHORTCUT_MARGIN;
        BlockPos pick = null;
        for (WormholeMouthBlockEntity here : WormholeMouthBlockEntity.loaded(level)) {
            WormholeMouthBlockEntity there = here.isRemoved() ? null : here.partner();
            if (there == null || there.getLevel() != level || !here.droneGate() || !there.droneGate()) {
                continue;
            }
            double d = from.distanceTo(gate(here.getBlockPos())) + gate(there.getBlockPos()).distanceTo(to);
            if (d < best) {
                best = d;
                pick = here.getBlockPos();
            }
        }
        return pick;
    }

    /** 同じ次元の中で from から to までの道のり（ワームホールの近道を使うほうが近ければ、その道のり）。 */
    public static double distance(ServerLevel level, Vec3 from, Vec3 to) {
        BlockPos via = shortcut(level, from, to);
        if (via == null || !(level.getBlockEntity(via) instanceof WormholeMouthBlockEntity here) || here.partner() == null) {
            return from.distanceTo(to);
        }
        return from.distanceTo(gate(via)) + gate(here.partner().getBlockPos()).distanceTo(to);
    }

    /** 同じ次元の中で、ポート from からポート to までの道のり（射程と電力に使う。切り上げ）。 */
    public static int distance(ServerLevel level, BlockPos from, BlockPos to) {
        return (int) Math.ceil(distance(level, Vec3.atCenterOf(from), Vec3.atCenterOf(to)));
    }

    /** 近道の口 via から、向こうの口の出口（同じ次元。使えなくなっていれば null）。 */
    @Nullable
    static Vec3 shortcutExit(ServerLevel level, BlockPos via) {
        if (!(level.getBlockEntity(via) instanceof WormholeMouthBlockEntity here) || !here.droneGate()) {
            return null;
        }
        WormholeMouthBlockEntity there = here.partner();
        return there == null || there.getLevel() != level || !there.droneGate() ? null : gate(there.getBlockPos());
    }

    /** 近道の口をくぐった光と音（入った口と出た口）。 */
    static void shortcutEffects(ServerLevel level, BlockPos via, Vec3 exit) {
        Vec3 g = gate(via);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, g.x, g.y, g.z, 24, 0.25, 0.25, 0.25, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, exit.x, exit.y, exit.z, 24, 0.25, 0.25, 0.25, 0.05);
        level.playSound(null, via, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 1.4F);
        level.playSound(null, BlockPos.containing(exit), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    // ------------------------------------------------------------------ 口をくぐる

    /** この便が、今の段階の目的地の口をくぐる便か（行きの届ける段階か、別の次元から帰る段階）。 */
    public static boolean crossing(DroneNetwork.Flight f) {
        return f.stage == DroneNetwork.DELIVER && f.relayTo != null && !f.away()
                || f.stage == DroneNetwork.RETURN && f.away();
    }

    /** ドローンが口をくぐる位置（口のブロックの真上）。 */
    public static Vec3 gate(BlockPos mouth) {
        return new Vec3(mouth.getX() + 0.5, mouth.getY() + 1.0 + GATE_LIFT, mouth.getZ() + 0.5);
    }

    /**
     * 口に着いた便が口をくぐる。くぐれたら true（便の記録は向こうの網へ移り、drone の実体は消えて向こうの口から出てくる）。
     * 口が閉じている・標識がない・向こうが読み込まれていないときは false（ドローンは口の上で待つ）。
     * 行きで口が別の次元へつなぎ直されていたら、荷物を発送元のポートへ戻して true。
     */
    public static boolean cross(ServerLevel level, DroneNetwork net, DroneNetwork.Flight f, @Nullable CargoDrone drone) {
        BlockPos at = f.target();
        if (!crossing(f) || at == null || !(level.getBlockEntity(at) instanceof WormholeMouthBlockEntity here) || !here.droneGate()) {
            return false;
        }
        WormholeMouthBlockEntity there = here.partner();
        if (there == null) {
            // 向こうの口が読み込まれていない: ネザーポータルと同じく、向こうを少しの間読み込ませて、読み込めたらくぐる
            net.minecraft.core.GlobalPos far = here.partnerPos();
            ServerLevel farLevel = far == null ? null : level.getServer().getLevel(far.dimension());
            if (farLevel != null) {
                wake(farLevel, far.pos());
            }
            return false;
        }
        if (!(there.getLevel() instanceof ServerLevel other) || !there.droneGate()) {
            return false;
        }
        long now = level.getGameTime();
        if (!f.away()) {
            if (!other.dimension().equals(f.relayDim)) {
                // 口が別の次元へつなぎ直された: 荷物を発送元のポートへ戻す
                turnBack(net, f, now);
                return true;
            }
            DroneNetwork far = DroneNetwork.get(other);
            DroneNetwork.Flight g = far.adopt(f);
            g.homeDim = level.dimension();
            g.homeGate = there.getBlockPos().immutable();
            g.from = there.getBlockPos().immutable();
            g.to = f.relayTo;
            g.stage = DroneNetwork.DELIVER;
            net.remove(f);
            if (f.station != null) {
                net.markAway(f.station, f.unit, other.dimension());
            }
            leave(level, here.getBlockPos(), drone);
            emerge(other, far, g, there.getBlockPos(), level.dimension());
            return true;
        }
        if (!other.dimension().equals(f.homeDim)) {
            return false;                                 // 口が家とは別の次元へつなぎ直された: つながり直すまで待つ
        }
        DroneNetwork home = DroneNetwork.get(other);
        // 家のステーションがなくなっていれば（印が消えている）、ドローンはここで役目を終える
        boolean welcome = f.station != null && home.clearAway(f.station, f.unit);
        net.remove(f);
        leave(level, here.getBlockPos(), drone);
        if (!welcome) {
            if (!f.crate.isEmpty()) {
                Block.popResource(level, here.getBlockPos().above(), f.crate);
            }
            return true;
        }
        DroneNetwork.Flight g = home.adopt(f);
        g.from = there.getBlockPos().immutable();
        g.to = there.getBlockPos().immutable();
        g.stage = DroneNetwork.RETURN;
        emerge(other, home, g, there.getBlockPos(), null);
        return true;
    }

    /** 行きの便を発送元のポートへ戻す（ワームホールの行き先を忘れる）。 */
    static void turnBack(DroneNetwork net, DroneNetwork.Flight f, long now) {
        BlockPos home = f.from;
        f.from = f.to;
        f.to = home;
        f.relayDim = null;
        f.relayTo = null;
        if (f.virtual) {
            net.redirectVirtual(f, gate(f.from), now);
        }
        net.changed();
    }

    /** こちらの口に入る: 光と音を出し、ドローンの実体を消す（便の記録はもう移したので、見えない飛行にはならない）。 */
    private static void leave(ServerLevel level, BlockPos mouth, @Nullable CargoDrone drone) {
        Vec3 g = gate(mouth);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, g.x, g.y, g.z, 24, 0.25, 0.25, 0.25, 0.05);
        level.playSound(null, mouth, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 1.4F);
        if (drone != null) {
            drone.discard();
        }
    }

    /** 向こうの口から出る: 見える所ならドローンの実体を出し、見えない所なら見えない飛行で次の目的地へ向かう。 */
    private static void emerge(ServerLevel level, DroneNetwork net, DroneNetwork.Flight g, BlockPos mouth, @Nullable ResourceKey<Level> home) {
        Vec3 at = gate(mouth);
        long now = level.getGameTime();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 24, 0.25, 0.25, 0.25, 0.05);
        level.playSound(null, mouth, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 1.2F);
        g.last = at;
        if (level.isPositionEntityTicking(mouth.above()) && CargoDrone.emerge(level, net, g, at, home)) {
            return;
        }
        g.virtual = true;
        g.drone = null;
        net.redirectVirtual(g, at, now);
    }

    /** ワームホール便のために、口や行き先のまわりを少しの間読み込ませる札（ネザーポータルの札と同じ広さと長さ）。 */
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
            net.minecraft.server.level.TicketType.create("singulo_wormhole_drone",
                    java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 300);

    /** pos のまわり（ブロックエンティティが動く広さ）を、300 tick の間読み込ませる。続けて呼ぶと延びる。 */
    public static void wake(ServerLevel level, BlockPos pos) {
        var chunk = new net.minecraft.world.level.ChunkPos(pos);
        level.getChunkSource().addRegionTicket(TICKET, chunk, 3, chunk);
    }

    /**
     * ステーションが受け持つ便の見張り（ステーションが20 tick ごとに呼ぶ）: 見えない飛行で着いたのに、着いた先が動いていない
     * （ほかの次元はプレイヤーがいなければ読み込まれていない）便のために、そこを読み込ませる。口をくぐる便と、
     * 別の次元へ出ている便（行き先のポート・帰りの口）だけ。同じ次元のふつうの便は、これまでどおり読み込まれるまで待つ。
     */
    static void watch(ServerLevel level, DroneNetwork.Flight f) {
        if (!f.virtual || !f.arrived(level.getGameTime()) || !(crossing(f) || f.away())) {
            return;
        }
        BlockPos t = f.target();
        if (t != null && !level.shouldTickBlocksAt(t)) {
            wake(level, t);
        }
    }

    /** この位置がワームホールの口か。 */
    public static boolean isMouth(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WormholeMouthBlockEntity;
    }

    /** 口ごとに10 tick ごと: 見えないまま口に着いた便をくぐらせる（見える所なら、ドローンを出して口の上でくぐらせる）。 */
    public static void tickMouth(ServerLevel level, WormholeMouthBlockEntity mouth) {
        DroneNetwork net = DroneNetwork.get(level);
        BlockPos pos = mouth.getBlockPos();
        long now = level.getGameTime();
        for (DroneNetwork.Flight f : List.copyOf(net.flightsAt(pos, now))) {
            if (!f.arrived(now) || !crossing(f) || !pos.equals(f.target())) {
                continue;
            }
            if (level.isPositionEntityTicking(pos.above()) && CargoDrone.materialize(level, net, f)) {
                continue;
            }
            cross(level, net, f, null);
        }
    }

    /**
     * 帰りにくぐる口がなくなった便を、口を通らずに家のステーションへ帰す（見えない飛行で、ステーションの上から降りてくる）。
     * 運んでいた箱があれば at に落とす。家のステーションがもうなければ、そのまま消える。
     */
    static void sendHome(ServerLevel level, DroneNetwork net, DroneNetwork.Flight f, BlockPos at) {
        if (!f.crate.isEmpty()) {
            Block.popResource(level, at, f.crate);
            f.crate = ItemStack.EMPTY;
        }
        net.remove(f);
        ServerLevel home = f.homeDim == null ? null : level.getServer().getLevel(f.homeDim);
        if (home == null || f.station == null) {
            return;
        }
        DroneNetwork homeNet = DroneNetwork.get(home);
        if (!homeNet.clearAway(f.station, f.unit)) {
            return;
        }
        DroneNetwork.Flight g = homeNet.adopt(f);
        g.from = f.station;
        g.to = f.station;
        g.stage = DroneNetwork.RETURN;
        g.virtual = true;
        homeNet.redirectVirtual(g, Vec3.atCenterOf(f.station).add(0, 16, 0), home.getGameTime());
    }

    /**
     * 口が壊された: 行きでこの口へ向かっていた便は発送元のポートへ戻す（発送元もなければ、梱包済みの箱のままここに落として帰す）。
     * 別の次元から来て、帰りにこの口をくぐるはずだった便は、口を通らずに家のステーションへ帰す。
     */
    public static void mouthRemoved(ServerLevel level, BlockPos pos) {
        DroneNetwork net = DroneNetwork.get(level);
        long now = level.getGameTime();
        for (DroneNetwork.Flight f : List.copyOf(net.flights())) {
            if (f.stage == DroneNetwork.DELIVER && f.relayTo != null && !f.away() && pos.equals(f.to)) {
                if (net.port(f.from) != null && !f.from.equals(pos)) {
                    turnBack(net, f, now);
                } else {
                    Block.popResource(level, pos, f.crate);
                    f.crate = ItemStack.EMPTY;
                    f.relayDim = null;
                    f.relayTo = null;
                    net.advance(f, DroneNetwork.RETURN, now);
                }
            } else if (f.away() && pos.equals(f.homeGate)) {
                sendHome(level, net, f, pos);
            }
        }
    }
}
