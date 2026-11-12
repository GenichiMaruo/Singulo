package io.github.genichimaruo.singulo.drone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * ドローン輸送の網（ディメンションごと）。発着ポートの一覧（持ち主・名前。読み込まれていないポートも行き先に出すため）と、
 * 飛んでいる便の記録。荷物（梱包済みの箱）は、ポートに渡すまで便の記録が持つ。ドローンの実体は記録を見て動くだけで、
 * 読み込み範囲の外に出ると記録は「見えない飛行」になって時間で進む。
 */
public class DroneNetwork extends SavedData {
    static final String NAME = "singulo_drone_network";
    /** 巡航の速さ（ブロック/tick）。見えない飛行の到着時刻もこれで決める。 */
    public static final double SPEED = 0.6;

    /** 発着ポート（持ち主・名前・液体用か）。物のポートと液体のポートは、それぞれ同じ種類のポートだけを行き先にする。 */
    public record Port(@Nullable UUID owner, String name, boolean fluid) {}

    /** 便の段階: ポートへ荷物を取りに行く、荷物を届ける、ステーションへ帰る。 */
    public static final int PICKUP = 0, DELIVER = 1, RETURN = 2;

    /** 1便の記録。 */
    public static final class Flight {
        public final long id;
        /** 受け持つステーションと枠（ステーションが壊されたら null）。 */
        @Nullable
        public BlockPos station;
        public final int unit;
        /** 今この便を飛んでいるドローンの実体（読み込まれ直すたびに新しくなる）。 */
        @Nullable
        public UUID drone;
        public BlockPos from, to;
        /** 運んでいる梱包済みの箱（積み込む前と、降ろしたあとは空）。 */
        public ItemStack crate = ItemStack.EMPTY;
        public int stage;
        /** 見えない飛行か（実体がいない）。そのときの最後の位置と、着く時刻。 */
        public boolean virtual;
        public Vec3 last = Vec3.ZERO;
        public long arriveAt;
        /** 持ち主（帰りかけのドローンを、同じ持ち主のポートの次の仕事へ回すため）。 */
        @Nullable
        public UUID owner;
        /** 届ける区間の進み（0〜1）。見えている間はドローンが書き、見えない間は時刻から決める（見えなくなったときの進みと時刻）。 */
        public float progress;
        private float virtualFrom;
        private long virtualAt;
        /** ドローンがポートの真上で受け渡しをしているか、道が見つからず待っているか（保存しない）。 */
        public boolean docked, blocked;
        /** 壁を抜けて飛べるドローンか（受け持つステーションの位相透過アップグレード）。 */
        public boolean phase;
        /** 飛んでいるドローンの型（速さと、見えない飛行から出てくるときの姿）。 */
        public DroneKind kind = DroneKind.ROTOR;
        /**
         * ワームホール便の行き: 届ける先（to）はこちらの口で、口をくぐった先の次元（relayDim）と最終の行き先のポート（relayTo）。
         * ドローンは口に入り、向こうの口から出て届ける（{@link DroneWormhole}）。ふつうの便は null。
         */
        @Nullable
        public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> relayDim;
        @Nullable
        public BlockPos relayTo;
        /**
         * ワームホールをくぐって、ステーションのない次元に来ている便: ステーション（station）がある次元（homeDim）と、
         * 帰りにくぐるこちらの口（homeGate）。帰る段階の目的地はこの口になる。家の次元にいる便は null。
         */
        @Nullable
        public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> homeDim;
        @Nullable
        public BlockPos homeGate;

        Flight(long id, @Nullable BlockPos station, int unit) {
            this.id = id;
            this.station = station;
            this.unit = unit;
        }

        /** 今の段階の目的地（ポートの真上、またはステーション）。帰る先がなければ null。 */
        @Nullable
        public BlockPos target() {
            return switch (stage) {
                case PICKUP -> from;
                case DELIVER -> to;
                default -> homeDim != null ? homeGate : station;
            };
        }

        /** ワームホールをくぐって別の次元に来ているか（帰りは口を通る）。 */
        public boolean away() {
            return homeDim != null;
        }

        /** 届ける区間の進み（0〜1。届ける段階でなければ 0）。 */
        public float progress(long now) {
            if (stage != DELIVER) {
                return 0;
            }
            if (!virtual) {
                return docked ? 1 : progress;
            }
            if (arriveAt <= virtualAt) {
                return 1;
            }
            return Math.min(1, virtualFrom + (1 - virtualFrom) * (now - virtualAt) / (float) (arriveAt - virtualAt));
        }

        /** 見えない飛行で、もう目的地に着いているか。 */
        public boolean arrived(long now) {
            return virtual && now >= arriveAt;
        }
    }

    private final Map<Long, Port> ports = new LinkedHashMap<>();
    private final Map<Long, Flight> flights = new LinkedHashMap<>();
    /**
     * ワームホールをくぐって別の次元へ出ている、この次元のステーションの枠（枠の鍵 → 行った先の次元）。便の記録は行った先の網が持つ。
     * ステーションはこの印で、枠のドローンが飛んでいる（印刷し直さない）と分かる。帰ってくると消える。
     */
    private final Map<Long, ResourceKey<net.minecraft.world.level.Level>> away = new LinkedHashMap<>();

    /** station の unit のドローンが、ワームホールをくぐって dim へ出た。 */
    public void markAway(BlockPos station, int unit, ResourceKey<net.minecraft.world.level.Level> dim) {
        away.put(unitKey(station, unit), dim);
        setDirty();
    }

    /** station の unit のドローンが別の次元へ出ているか。 */
    public boolean isAway(BlockPos station, int unit) {
        return away.containsKey(unitKey(station, unit));
    }

    /** station の unit のドローンが出ている先の次元（出ていなければ null）。 */
    @Nullable
    public ResourceKey<net.minecraft.world.level.Level> awayDim(BlockPos station, int unit) {
        return away.get(unitKey(station, unit));
    }

    /** home の次元の station の unit から、この次元へ来ている便（なければ null）。 */
    @Nullable
    public Flight visitor(ResourceKey<net.minecraft.world.level.Level> home, BlockPos station, int unit) {
        for (Flight f : flights.values()) {
            if (home.equals(f.homeDim) && f.unit == unit && station.equals(f.station)) {
                return f;
            }
        }
        return null;
    }

    /** 別の次元へ出ていた印を消す（帰ってきた・ステーションが壊された）。印があったら true。 */
    public boolean clearAway(BlockPos station, int unit) {
        if (away.remove(unitKey(station, unit)) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * 別の次元から口をくぐって来た便を、この網の便として新しく載せる（番号はこの網で振り直す）。持ち主・型・壁抜け・荷物は写す。
     * 段階・場所・ワームホールの行き先は呼ぶ側で決める。
     */
    public Flight adopt(Flight src) {
        Flight f = new Flight(nextId++, src.station, src.unit);
        f.owner = src.owner;
        f.crate = src.crate;
        f.phase = src.phase;
        f.kind = src.kind;
        flights.put(f.id, f);
        forgetIndex();
        setDirty();
        return f;
    }
    /**
     * 便を探す手間を減らす控え（便を足す・消すたびに作り直す）: 便の一覧の写しと、ステーションの枠ごとの便。
     * ステーションが壊されて便の station が null になっても、控えから引いたあとで確かめるので、取り違えない。
     */
    @Nullable
    private List<Flight> flightList;
    @Nullable
    private Map<Long, Flight> byUnit;
    /**
     * 1 tick ごとに作り直す控え（便の行き先・段階は tick の途中でも変わるので、その tick の間だけ使う）: ポートごとの便
     * （そこで積む・そこへ届ける）と、持ち主ごとの帰りかけの便。ポートやステーションが毎 tick すべての便を回さずに済む。
     */
    private long tickIndexAt = Long.MIN_VALUE;
    @Nullable
    private Map<Long, List<Flight>> byPort;
    @Nullable
    private Map<UUID, List<Flight>> returningByOwner;
    /** 読み込まれているステーション（持ち主ごと。保存しない。ステーションが読み込まれる・外れるたびに出し入れする）。 */
    private final Map<UUID, Map<Long, DroneStationBlockEntity>> stations = new java.util.HashMap<>();
    private static final UUID NO_OWNER = new UUID(0, 0);
    private long nextId = 1;

    /** この網の次元（見えない飛行の道のりで、ワームホールの近道を調べるため。保存しない）。 */
    @Nullable
    private ServerLevel level;

    public static DroneNetwork get(ServerLevel level) {
        DroneNetwork net = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DroneNetwork::new, DroneNetwork::load, null), NAME);
        net.level = level;
        return net;
    }

    /** 見えない飛行の道のり（同じ次元のワームホールの近道を使うほうが近ければ、その道のり）。 */
    private double travel(Vec3 from, Vec3 to) {
        return level == null ? from.distanceTo(to) : DroneWormhole.distance(level, from, to);
    }

    // ------------------------------------------------------------------ ポート

    /** 物の発着ポートとして載せる（0.16.0 までの呼び方）。 */
    public void putPort(BlockPos pos, @Nullable UUID owner, String name) {
        putPort(pos, owner, name, false);
    }

    /** owner の物の発着ポート（0.16.0 までの呼び方）。 */
    public List<BlockPos> portsOf(@Nullable UUID owner, BlockPos from) {
        return portsOf(owner, from, false);
    }

    public void putPort(BlockPos pos, @Nullable UUID owner, String name, boolean fluid) {
        Port next = new Port(owner, name, fluid);
        if (!next.equals(ports.put(pos.asLong(), next))) {
            setDirty();
        }
    }

    public void removePort(BlockPos pos) {
        if (ports.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    @Nullable
    public Port port(BlockPos pos) {
        return ports.get(pos.asLong());
    }

    /** owner の発着ポートのうち、物か液体か（fluid）が同じもの（from 自身を除く。近い順）。 */
    public List<BlockPos> portsOf(@Nullable UUID owner, BlockPos from, boolean fluid) {
        List<BlockPos> out = new ArrayList<>();
        for (var e : ports.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            if (!p.equals(from) && e.getValue().fluid() == fluid && java.util.Objects.equals(owner, e.getValue().owner())) {
                out.add(p);
            }
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(from)));
        return out;
    }

    // ------------------------------------------------------------------ 便

    public Flight create(BlockPos station, int unit, @Nullable UUID owner, BlockPos from, BlockPos to) {
        Flight f = new Flight(nextId++, station, unit);
        f.owner = owner;
        f.from = from;
        f.to = to;
        f.stage = PICKUP;
        flights.put(f.id, f);
        forgetIndex();
        setDirty();
        return f;
    }

    @Nullable
    public Flight flight(long id) {
        return flights.get(id);
    }

    /** そのステーションの枠が飛んでいる便（なければ null）。 */
    @Nullable
    public Flight flightOf(BlockPos station, int unit) {
        if (byUnit == null) {
            Map<Long, Flight> index = new java.util.HashMap<>();
            for (Flight f : flights.values()) {
                if (f.station != null) {
                    index.put(unitKey(f.station, f.unit), f);
                }
            }
            byUnit = index;
        }
        Flight f = byUnit.get(unitKey(station, unit));
        return f != null && f.unit == unit && station.equals(f.station) ? f : null;
    }

    private static long unitKey(BlockPos station, int unit) {
        return station.asLong() * 31 + unit;
    }

    /** 便の一覧（便を足す・消すまで同じ写しを使い回す。回しながら便を消してもよい）。 */
    public List<Flight> flights() {
        if (flightList == null) {
            flightList = List.copyOf(flights.values());
        }
        return flightList;
    }

    private void forgetIndex() {
        flightList = null;
        byUnit = null;
        byPort = null;
        returningByOwner = null;
    }

    /** port で積む・port へ届ける便（その tick の控え。使う側で段階と場所を確かめること）。 */
    public List<Flight> flightsAt(BlockPos port, long now) {
        tickIndex(now);
        return byPort.getOrDefault(port.asLong(), List.of());
    }

    /** owner の帰りかけの便（その tick の控え。使う側で段階を確かめること）。 */
    public List<Flight> returning(@Nullable UUID owner, long now) {
        tickIndex(now);
        return returningByOwner.getOrDefault(owner == null ? NO_OWNER : owner, List.of());
    }

    private void tickIndex(long now) {
        if (byPort != null && returningByOwner != null && tickIndexAt == now) {
            return;
        }
        Map<Long, List<Flight>> ports = new java.util.HashMap<>();
        Map<UUID, List<Flight>> returning = new java.util.HashMap<>();
        for (Flight f : flights.values()) {
            if (f.from != null) {
                ports.computeIfAbsent(f.from.asLong(), k -> new ArrayList<>()).add(f);
            }
            if (f.to != null && !f.to.equals(f.from)) {
                ports.computeIfAbsent(f.to.asLong(), k -> new ArrayList<>()).add(f);
            }
            if (f.homeGate != null && !f.homeGate.equals(f.from) && !f.homeGate.equals(f.to)) {
                ports.computeIfAbsent(f.homeGate.asLong(), k -> new ArrayList<>()).add(f);   // 帰りにくぐる口
            }
            // 別の次元から来ている便は、こちらの仕事に回さない（家のステーションへ帰す）
            if (f.stage == RETURN && f.homeDim == null) {
                returning.computeIfAbsent(f.owner == null ? NO_OWNER : f.owner, k -> new ArrayList<>()).add(f);
            }
        }
        byPort = ports;
        returningByOwner = returning;
        tickIndexAt = now;
    }

    // ------------------------------------------------------------------ ステーション

    void addStation(DroneStationBlockEntity station) {
        stations.computeIfAbsent(ownerKey(station.owner()), k -> new java.util.HashMap<>()).put(station.getBlockPos().asLong(), station);
    }

    void removeStation(DroneStationBlockEntity station) {
        for (Map<Long, DroneStationBlockEntity> m : stations.values()) {
            m.remove(station.getBlockPos().asLong(), station);
        }
    }

    /** owner の、読み込まれているステーション。 */
    java.util.Collection<DroneStationBlockEntity> stationsOf(@Nullable UUID owner) {
        return stations.getOrDefault(ownerKey(owner), Map.of()).values();
    }

    private static UUID ownerKey(@Nullable UUID owner) {
        return owner == null ? NO_OWNER : owner;
    }

    public void remove(Flight f) {
        if (flights.remove(f.id) != null) {
            forgetIndex();
            setDirty();
        }
    }

    /** 段階を進める（記録を保存し直す）。 */
    public void changed() {
        setDirty();
    }

    /** 実体が読み込み範囲の外に出た: 見えない飛行にして、今の位置から目的地までの時間で着くことにする。 */
    public void virtualize(Flight f, Vec3 at, long now) {
        f.virtualFrom = f.stage == DELIVER ? f.progress : 0;
        f.virtualAt = now;
        f.virtual = true;
        f.drone = null;
        f.docked = false;
        f.last = at;
        BlockPos t = f.target();
        double d = t == null ? 0 : travel(at, Vec3.atCenterOf(t));
        f.arriveAt = now + (long) Math.ceil(d / (SPEED * f.kind.speed)) + 20;
        setDirty();
    }

    /** 見えない便を、at から今の目的地へ向け直す（進みは0から。着く時刻はドローンの型の速さで決める）。 */
    public void redirectVirtual(Flight f, Vec3 at, long now) {
        f.progress = 0;
        f.virtualFrom = 0;
        f.virtualAt = now;
        f.last = at;
        BlockPos t = f.target();
        double d = t == null ? 0 : travel(at, Vec3.atCenterOf(t));
        f.arriveAt = now + (long) Math.ceil(d / (SPEED * f.kind.speed)) + 20;
        setDirty();
    }

    /** 便を次の段階へ。帰る先のステーションがなくなっていれば、帰らずに記録を消す（ドローンの実体も消える）。 */
    public void advance(Flight f, int stage, long now) {
        if (stage == RETURN && f.station == null) {
            remove(f);
        } else if (f.virtual) {
            advanceVirtual(f, stage, now);
        } else {
            f.stage = stage;
            f.progress = 0;
            f.docked = false;
            setDirty();
        }
    }

    /** 帰りかけの便に、次の仕事（port から to へ）を任せ直す。 */
    public void retask(Flight f, BlockPos port, BlockPos to) {
        f.from = port;
        f.to = to;
        f.stage = PICKUP;
        f.progress = 0;
        f.docked = false;
        f.relayDim = null;                                // 前の便のワームホールの行き先を持ち越さない
        f.relayTo = null;
        setDirty();
    }

    /** 見えない飛行のまま次の段階へ（前の目的地から次の目的地までの時間で着く）。 */
    public void advanceVirtual(Flight f, int stage, long now) {
        BlockPos was = f.target();
        f.stage = stage;
        f.virtualFrom = 0;
        f.virtualAt = now;
        f.progress = 0;
        f.virtual = true;
        f.drone = null;
        if (was != null) {
            f.last = Vec3.atCenterOf(was);
        }
        BlockPos t = f.target();
        double d = t == null || was == null ? 0 : travel(Vec3.atCenterOf(was), Vec3.atCenterOf(t));
        f.arriveAt = now + (long) Math.ceil(d / (SPEED * f.kind.speed)) + 20;
        setDirty();
    }

    // ------------------------------------------------------------------ 保存

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var e : ports.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putLong("pos", e.getKey());
            if (e.getValue().owner() != null) {
                p.putUUID("owner", e.getValue().owner());
            }
            p.putString("name", e.getValue().name());
            p.putBoolean("fluid", e.getValue().fluid());
            list.add(p);
        }
        tag.put("ports", list);
        ListTag fl = new ListTag();
        for (Flight f : flights.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("id", f.id);
            if (f.station != null) {
                t.put("station", NbtUtils.writeBlockPos(f.station));
            }
            t.putInt("unit", f.unit);
            if (f.drone != null) {
                t.putUUID("drone", f.drone);
            }
            t.put("from", NbtUtils.writeBlockPos(f.from));
            t.put("to", NbtUtils.writeBlockPos(f.to));
            if (!f.crate.isEmpty()) {
                t.put("crate", f.crate.save(registries));
            }
            t.putInt("stage", f.stage);
            t.putBoolean("virtual", f.virtual);
            t.putDouble("x", f.last.x);
            t.putDouble("y", f.last.y);
            t.putDouble("z", f.last.z);
            t.putLong("arrive_at", f.arriveAt);
            if (f.owner != null) {
                t.putUUID("owner", f.owner);
            }
            t.putFloat("progress", f.progress);
            t.putBoolean("phase", f.phase);
            t.putInt("kind", f.kind.ordinal());
            t.putFloat("virtual_from", f.virtualFrom);
            t.putLong("virtual_at", f.virtualAt);
            if (f.relayDim != null && f.relayTo != null) {
                t.putString("relay_dim", f.relayDim.location().toString());
                t.put("relay_to", NbtUtils.writeBlockPos(f.relayTo));
            }
            if (f.homeDim != null && f.homeGate != null) {
                t.putString("home_dim", f.homeDim.location().toString());
                t.put("home_gate", NbtUtils.writeBlockPos(f.homeGate));
            }
            fl.add(t);
        }
        tag.put("flights", fl);
        ListTag al = new ListTag();
        for (var e : away.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("unit", e.getKey());
            t.putString("dim", e.getValue().location().toString());
            al.add(t);
        }
        tag.put("away", al);
        tag.putLong("next_id", nextId);
        return tag;
    }

    static DroneNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        DroneNetwork net = new DroneNetwork();
        for (Tag t : tag.getList("ports", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            net.ports.put(p.getLong("pos"), new Port(p.hasUUID("owner") ? p.getUUID("owner") : null, p.getString("name"), p.getBoolean("fluid")));
        }
        for (Tag t : tag.getList("flights", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Flight f = new Flight(c.getLong("id"), NbtUtils.readBlockPos(c, "station").orElse(null), c.getInt("unit"));
            f.drone = c.hasUUID("drone") ? c.getUUID("drone") : null;
            f.from = NbtUtils.readBlockPos(c, "from").orElse(BlockPos.ZERO);
            f.to = NbtUtils.readBlockPos(c, "to").orElse(BlockPos.ZERO);
            f.crate = c.contains("crate") ? ItemStack.parse(registries, c.getCompound("crate")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
            f.stage = c.getInt("stage");
            f.virtual = c.getBoolean("virtual");
            f.last = new Vec3(c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
            f.arriveAt = c.getLong("arrive_at");
            f.owner = c.hasUUID("owner") ? c.getUUID("owner") : null;
            f.progress = c.getFloat("progress");
            f.phase = c.getBoolean("phase");
            f.kind = DroneKind.byId(c.getInt("kind"));
            f.virtualFrom = c.getFloat("virtual_from");
            f.virtualAt = c.getLong("virtual_at");
            if (c.contains("relay_dim")) {
                var loc = ResourceLocation.tryParse(c.getString("relay_dim"));
                f.relayDim = loc == null ? null : ResourceKey.create(Registries.DIMENSION, loc);
                f.relayTo = NbtUtils.readBlockPos(c, "relay_to").orElse(null);
            }
            if (c.contains("home_dim")) {
                var loc = ResourceLocation.tryParse(c.getString("home_dim"));
                f.homeGate = NbtUtils.readBlockPos(c, "home_gate").orElse(null);
                f.homeDim = loc == null || f.homeGate == null ? null : ResourceKey.create(Registries.DIMENSION, loc);
            }
            net.flights.put(f.id, f);
            net.forgetIndex();
        }
        for (Tag t : tag.getList("away", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            var loc = ResourceLocation.tryParse(c.getString("dim"));
            if (loc != null) {
                net.away.put(c.getLong("unit"), ResourceKey.create(Registries.DIMENSION, loc));
            }
        }
        net.nextId = Math.max(tag.getLong("next_id"), 1);
        for (long id : net.flights.keySet()) {
            net.nextId = Math.max(net.nextId, id + 1);
        }
        return net;
    }
}
