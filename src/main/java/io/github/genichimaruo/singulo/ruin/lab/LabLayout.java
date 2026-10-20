package io.github.genichimaruo.singulo.ruin.lab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 地下アンドロイド研究所の部屋と廊下の並び。種から毎回同じ並びが決まる。
 * <ul>
 *   <li>部屋の大きさはまちまち（型紙の大きさ）。格子には並べず、廊下（断面 3×3、途中で1回曲がることもある）でつなぐ</li>
 *   <li>エントランス（主立坑の真下）から始めて、置いた部屋の空いている辺から廊下を伸ばし、その先に次の部屋を置く。
 *       部屋どうし・廊下どうしは1マス以上離す（間は耐圧殻で埋まり、どこも二重の壁になる）</li>
 *   <li>大きな部屋には、小部屋（倉庫・ロッカー室・観察室）を壁に直接くっつけることがある</li>
 *   <li>中央動力炉は最後に置き（行き止まり）、その先に短い廊下と隔壁を挟んでボス部屋（AI中枢）。動力炉の横には変電室</li>
 * </ul>
 */
public final class LabLayout {
    public static final String BOSS = "boss_hall", REACTOR = "reactor", ENTRANCE = "entrance";
    public static final String[] REQUIRED = {"control", "loading_dock"};
    public static final String[] OPTIONAL = {"research_lab", "meeting", "archive", "office", "server_room", "clean_room", "prototype",
            "mass_factory", "dorm", "body_storage", "test_ground", "bio_parts", "waste", "medical", "cafeteria", "private_rooms"};
    public static final String[] ANNEXES = {"annex_storage", "annex_locker", "annex_observation"};
    public static final String SUBSTATION = "annex_substation";
    /** 選ばれる部屋の数。 */
    public static final int CHOSEN = 9;
    /** エントランスの中心から、施設が広がってよい広さ（構造物の部品は始まりのチャンクから8チャンク以内に収める）。 */
    public static final int REACH = 56;
    /** 大きな部屋（どちらかの辺がこれ以上）には、小部屋がつくことがある。 */
    static final int LARGE = 17;

    /** 置いた部屋（box は床から天井まで）。 */
    public record Placed(String name, BoundingBox box, Rotation rotation) {}

    /** 出入口: 壁の真ん中（床の高さ）、外向き、隔壁か、耐圧殻の壁か（ボス部屋の側。枠や灯を付けない）。 */
    public record Door(BlockPos wall, Direction out, boolean bulkhead, boolean hullWall) {}

    /** 廊下: 床の高さの中心線の折れ点（はじめと終わりは、部屋の壁のすぐ外）。 */
    public record Corridor(List<BlockPos> points) {}

    public final List<Placed> rooms = new ArrayList<>();
    public final List<Corridor> corridors = new ArrayList<>();
    public final List<Door> doors = new ArrayList<>();
    /** 部屋どうしのつながり（rooms の番号の組。廊下・直付けのどちらも）。 */
    public final List<int[]> links = new ArrayList<>();
    final int floor, cx, cz;
    private final Function<String, Vec3i> sizes;
    private final RandomSource r;
    /** 廊下の当たり判定の箱（壁ごと）。 */
    private final List<BoundingBox> corridorBoxes = new ArrayList<>();
    /** 廊下を伸ばせる辺（部屋の番号と向き）。 */
    private final List<Object[]> open = new ArrayList<>();

    private LabLayout(long seed, Function<String, Vec3i> sizes, int cx, int cz, int floor) {
        this.r = RandomSource.create(seed);
        this.sizes = sizes;
        this.cx = cx;
        this.cz = cz;
        this.floor = floor;
    }

    /**
     * 並びを作る。sizes は型紙の名前 → 大きさ（向きを変える前）。(cx, cz) がエントランスの中心（主立坑）。
     * うまく置けない種なら、種をずらして作り直す。
     */
    public static LabLayout create(long seed, Function<String, Vec3i> sizes, int cx, int cz, int floor) {
        for (int k = 0; k < 64; k++) {
            LabLayout l = new LabLayout(seed + k * 7919L, sizes, cx, cz, floor);
            if (l.build()) {
                return l;
            }
        }
        throw new IllegalStateException("lab layout failed for seed " + seed);
    }

    public Placed entrance() {
        return rooms.get(0);
    }

    // ------------------------------------------------------------------ 組み立て

    private boolean build() {
        Vec3i e = sizes.apply(ENTRANCE);
        rooms.add(new Placed(ENTRANCE, new BoundingBox(cx - e.getX() / 2, floor, cz - e.getZ() / 2,
                cx + e.getX() / 2, floor + e.getY() - 1, cz + e.getZ() / 2), Rotation.NONE));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            open.add(new Object[] {0, d});
        }
        List<String> chosen = new ArrayList<>(List.of(OPTIONAL));
        Collections.shuffle(chosen, new java.util.Random(r.nextLong()));
        List<String> order = new ArrayList<>(List.of(REQUIRED));
        order.addAll(chosen.subList(0, CHOSEN));
        Collections.shuffle(order, new java.util.Random(r.nextLong()));
        for (String name : order) {
            int idx = viaCorridor(name, true);
            if (idx < 0) {
                if (List.of(REQUIRED).contains(name)) {
                    return false;
                }
                continue;
            }
            if (large(rooms.get(idx)) && r.nextFloat() < 0.6F) {
                annex(idx, ANNEXES[r.nextInt(ANNEXES.length)]);
            }
        }
        // 中央動力炉（行き止まり）と、その先のボス部屋。動力炉の横に変電室
        for (int attempt = 0; attempt < 12; attempt++) {
            int reactor = viaCorridor(REACTOR, false);
            if (reactor < 0) {
                return false;
            }
            if (boss(reactor)) {
                if (annex(reactor, SUBSTATION)) {
                    return true;
                }
                undoLast(false);                     // 変電室がつかない: ボス部屋を取り消して、動力炉から置き直す
            }
            undoLast(true);
        }
        return false;
    }

    private static boolean large(Placed p) {
        return Math.max(p.box.getXSpan(), p.box.getZSpan()) >= LARGE;
    }

    /** 向きを変えた大きさ（横幅 x、奥行き z）。 */
    private Vec3i size(String name, Rotation rot) {
        Vec3i s = sizes.apply(name);
        return rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90 ? new Vec3i(s.getZ(), s.getY(), s.getX()) : s;
    }

    /** 部屋の、ある辺の壁の真ん中（床の高さ）。 */
    public static BlockPos wallCenter(BoundingBox b, Direction d) {
        int mx = (b.minX() + b.maxX()) / 2, mz = (b.minZ() + b.maxZ()) / 2;
        return switch (d) {
            case NORTH -> new BlockPos(mx, b.minY(), b.minZ());
            case SOUTH -> new BlockPos(mx, b.minY(), b.maxZ());
            case WEST -> new BlockPos(b.minX(), b.minY(), mz);
            default -> new BlockPos(b.maxX(), b.minY(), mz);
        };
    }

    /** 壁の外のある点 at に、辺 facing を向けて部屋を置いたときの箱（at は部屋の壁の真ん中のすぐ外）。 */
    private BoundingBox boxAt(BlockPos at, Direction facing, Vec3i s) {
        // facing は「部屋から at へ向かう」向き。部屋は at から facing の反対へ広がる
        Direction into = facing.getOpposite();
        int w = s.getX(), d = s.getZ(), h = s.getY();
        int minX, minZ;
        switch (into) {
            case EAST -> { minX = at.getX() + 1; minZ = at.getZ() - d / 2; }
            case WEST -> { minX = at.getX() - w; minZ = at.getZ() - d / 2; }
            case SOUTH -> { minX = at.getX() - w / 2; minZ = at.getZ() + 1; }
            default -> { minX = at.getX() - w / 2; minZ = at.getZ() - d; }
        }
        return new BoundingBox(minX, floor, minZ, minX + w - 1, floor + h - 1, minZ + d - 1);
    }

    /** 廊下の線分（a〜b、軸にそった）の、壁ごとの箱。 */
    static BoundingBox segmentBox(BlockPos a, BlockPos b, int floor) {
        boolean alongX = a.getZ() == b.getZ();
        int lx = alongX ? 0 : 2, lz = alongX ? 2 : 0;
        return new BoundingBox(Math.min(a.getX(), b.getX()) - lx, floor, Math.min(a.getZ(), b.getZ()) - lz,
                Math.max(a.getX(), b.getX()) + lx, floor + 4, Math.max(a.getZ(), b.getZ()) + lz);
    }

    private boolean withinReach(BoundingBox b) {
        return b.minX() >= cx - REACH && b.maxX() <= cx + REACH && b.minZ() >= cz - REACH && b.maxZ() <= cz + REACH;
    }

    /** ほかの部屋・廊下と、1マスの隙間を空けて重ならないか（except の部屋は除く）。 */
    private boolean free(BoundingBox b, int exceptRoom, @Nullable List<BoundingBox> exceptCorridor) {
        if (!withinReach(b)) {
            return false;
        }
        BoundingBox grown = b.inflatedBy(1);
        for (int i = 0; i < rooms.size(); i++) {
            if (i != exceptRoom && grown.intersects(rooms.get(i).box)) {
                return false;
            }
        }
        for (BoundingBox c : corridorBoxes) {
            if ((exceptCorridor == null || !exceptCorridor.contains(c)) && grown.intersects(c)) {
                return false;
            }
        }
        return true;
    }

    /** 空いている辺から廊下を伸ばして、その先に部屋を置く。置けたら部屋の番号、だめなら -1。 */
    private int viaCorridor(String name, boolean keepOpen) {
        for (int attempt = 0; attempt < 80 && !open.isEmpty(); attempt++) {
            Object[] slot = open.get(r.nextInt(open.size()));
            int from = (int) slot[0];
            Direction d = (Direction) slot[1];
            BlockPos start = wallCenter(rooms.get(from).box, d).relative(d);
            List<BlockPos> pts = new ArrayList<>();
            pts.add(start);
            BlockPos p1 = start.relative(d, 4 + r.nextInt(9));
            pts.add(p1);
            Direction end = d;
            if (r.nextFloat() < 0.45F) {
                end = r.nextBoolean() ? d.getClockWise() : d.getCounterClockWise();
                pts.add(p1.relative(end, 4 + r.nextInt(7)));
            }
            Rotation rot = Rotation.values()[r.nextInt(4)];
            BoundingBox box = boxAt(pts.get(pts.size() - 1), end.getOpposite(), size(name, rot));
            List<BoundingBox> segs = new ArrayList<>();
            for (int i = 0; i + 1 < pts.size(); i++) {
                segs.add(segmentBox(pts.get(i), pts.get(i + 1), floor));
            }
            boolean ok = free(box, -1, null);
            for (int i = 0; ok && i < segs.size(); i++) {
                ok = free(segs.get(i), from, null) && (i == segs.size() - 1 || !segs.get(i).inflatedBy(1).intersects(box));
            }
            if (!ok) {
                continue;
            }
            rooms.add(new Placed(name, box, rot));
            int idx = rooms.size() - 1;
            corridorBoxes.addAll(segs);
            corridors.add(new Corridor(pts));
            open.remove(slot);
            doors.add(new Door(wallCenter(rooms.get(from).box, d), d, false, false));
            doors.add(new Door(wallCenter(box, end.getOpposite()), end.getOpposite(), false, false));
            links.add(new int[] {from, idx});
            if (keepOpen) {
                for (Direction o : Direction.Plane.HORIZONTAL) {
                    if (o != end.getOpposite()) {
                        open.add(new Object[] {idx, o});
                    }
                }
            }
            return idx;
        }
        return -1;
    }

    /** 最後に廊下で置いた部屋（動力炉・ボス部屋）と、その廊下・出入口・つながりを取り消す。 */
    private void undoLast(boolean reopen) {
        rooms.remove(rooms.size() - 1);
        Corridor c = corridors.remove(corridors.size() - 1);
        for (int i = 0; i < c.points().size() - 1; i++) {
            corridorBoxes.remove(corridorBoxes.size() - 1);
        }
        doors.remove(doors.size() - 1);
        Door first = doors.remove(doors.size() - 1);
        int[] link = links.remove(links.size() - 1);
        if (reopen) {
            open.add(new Object[] {link[0], first.out()});
        }
    }

    /** 動力炉の空いている辺から、短いまっすぐな廊下と隔壁を挟んでボス部屋を置く。 */
    private boolean boss(int reactor) {
        Placed rp = rooms.get(reactor);
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Collections.shuffle(sides, new java.util.Random(r.nextLong()));
        for (Direction d : sides) {
            if (doors.stream().anyMatch(o -> o.wall().equals(wallCenter(rp.box, d)))) {
                continue;
            }
            BlockPos start = wallCenter(rp.box, d).relative(d);
            BlockPos end = start.relative(d, 2 + r.nextInt(3));
            // ボス部屋はほかの部屋より深く置く（台地が出入口と同じ高さ、溶岩の溝がその下）
            BoundingBox box = boxAt(end, d.getOpposite(), sizes.apply(BOSS)).moved(0, -LabStructure.BOSS_DEPTH, 0);
            BoundingBox seg = segmentBox(start, end, floor);
            if (!free(box, -1, null) || !free(seg, reactor, null)) {
                continue;
            }
            rooms.add(new Placed(BOSS, box, Rotation.NONE));
            corridorBoxes.add(seg);
            corridors.add(new Corridor(List.of(start, end)));
            doors.add(new Door(wallCenter(rp.box, d), d, false, false));
            doors.add(new Door(wallCenter(box, d.getOpposite()), d.getOpposite(), true, true));
            links.add(new int[] {reactor, rooms.size() - 1});
            return true;
        }
        return false;
    }

    /** 大きな部屋の空いている辺に、小部屋を直接くっつける（北の辺を部屋へ向ける）。つけられたか。 */
    private boolean annex(int host, String name) {
        Placed hp = rooms.get(host);
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Collections.shuffle(sides, new java.util.Random(r.nextLong()));
        for (Direction d : sides) {
            BlockPos wall = wallCenter(hp.box, d);
            if (doors.stream().anyMatch(o -> o.wall().equals(wall))) {
                continue;
            }
            // 小部屋の北（型紙の -z）が、大きな部屋のほう（d の反対）を向く向き
            Rotation rot = switch (d) {
                case SOUTH -> Rotation.NONE;
                case WEST -> Rotation.CLOCKWISE_90;
                case NORTH -> Rotation.CLOCKWISE_180;
                default -> Rotation.COUNTERCLOCKWISE_90;
            };
            BoundingBox box = boxAt(wall, d.getOpposite(), size(name, rot));
            if (!free(box, host, null)) {
                continue;
            }
            rooms.add(new Placed(name, box, rot));
            open.removeIf(o -> (int) o[0] == host && o[1] == d);
            doors.add(new Door(wall, d, false, false));
            doors.add(new Door(wallCenter(box, d.getOpposite()), d.getOpposite(), false, false));
            links.add(new int[] {host, rooms.size() - 1});
            return true;
        }
        return false;
    }

    /** 全体を包む箱（部屋と廊下のまわりに、耐圧殻の厚さ hull）。 */
    public BoundingBox bounds(int hull) {
        BoundingBox b = rooms.get(0).box;
        for (Placed p : rooms) {
            b = encapsulate(b, p.box);
        }
        for (BoundingBox c : corridorBoxes) {
            b = encapsulate(b, c);
        }
        return new BoundingBox(b.minX() - hull, b.minY() - hull, b.minZ() - hull, b.maxX() + hull, b.maxY() + hull, b.maxZ() + hull);
    }

    private static BoundingBox encapsulate(BoundingBox a, BoundingBox b) {
        return new BoundingBox(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()),
                Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
    }

    /** 廊下の当たり判定の箱（耐圧殻で埋めない所）。 */
    public List<BoundingBox> corridorBoxes() {
        return corridorBoxes;
    }
}
