package io.github.genichimaruo.singulo.drone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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

    /** 発着ポート（持ち主・名前）。 */
    public record Port(@Nullable UUID owner, String name) {}

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
        /** ドローンがポートの真上で受け渡しをしているか（保存しない）。 */
        public boolean docked;

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
                default -> station;
            };
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
    private long nextId = 1;

    public static DroneNetwork get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DroneNetwork::new, DroneNetwork::load, null), NAME);
    }

    // ------------------------------------------------------------------ ポート

    public void putPort(BlockPos pos, @Nullable UUID owner, String name) {
        Port next = new Port(owner, name);
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

    /** owner の発着ポート（from 自身を除く。近い順）。 */
    public List<BlockPos> portsOf(@Nullable UUID owner, BlockPos from) {
        List<BlockPos> out = new ArrayList<>();
        for (var e : ports.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            if (!p.equals(from) && java.util.Objects.equals(owner, e.getValue().owner())) {
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
        for (Flight f : flights.values()) {
            if (f.unit == unit && station.equals(f.station)) {
                return f;
            }
        }
        return null;
    }

    public List<Flight> flights() {
        return List.copyOf(flights.values());
    }

    public void remove(Flight f) {
        if (flights.remove(f.id) != null) {
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
        double d = t == null ? 0 : Math.sqrt(at.distanceToSqr(Vec3.atCenterOf(t)));
        f.arriveAt = now + (long) Math.ceil(d / SPEED) + 20;
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
        double d = t == null || was == null ? 0 : Math.sqrt(was.distSqr(t));
        f.arriveAt = now + (long) Math.ceil(d / SPEED) + 20;
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
            t.putFloat("virtual_from", f.virtualFrom);
            t.putLong("virtual_at", f.virtualAt);
            fl.add(t);
        }
        tag.put("flights", fl);
        tag.putLong("next_id", nextId);
        return tag;
    }

    static DroneNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        DroneNetwork net = new DroneNetwork();
        for (Tag t : tag.getList("ports", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            net.ports.put(p.getLong("pos"), new Port(p.hasUUID("owner") ? p.getUUID("owner") : null, p.getString("name")));
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
            f.virtualFrom = c.getFloat("virtual_from");
            f.virtualAt = c.getLong("virtual_at");
            net.flights.put(f.id, f);
        }
        net.nextId = Math.max(tag.getLong("next_id"), 1);
        for (long id : net.flights.keySet()) {
            net.nextId = Math.max(net.nextId, id + 1);
        }
        return net;
    }
}
