package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.BulkheadBlock;
import io.github.genichimaruo.singulo.ruin.lab.LabLayout;
import io.github.genichimaruo.singulo.ruin.lab.LabPieces;
import io.github.genichimaruo.singulo.ruin.lab.LabPower;
import io.github.genichimaruo.singulo.ruin.lab.LabPoweredBlock;
import io.github.genichimaruo.singulo.ruin.lab.LabStructure;
import io.github.genichimaruo.singulo.ruin.lab.LabTerminalBlock;
import io.github.genichimaruo.singulo.ruin.lab.PressureHullBlock;
import io.github.genichimaruo.singulo.ruin.lab.ReactorConsoleBlockEntity;
import io.github.genichimaruo.singulo.ruin.lab.SlidingDoorBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 地下アンドロイド研究所: 部屋の並び・部品・型紙・電力・耐圧殻。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class LabGameTests {
    private static final String EMPTY = "empty";

    private LabGameTests() {}

    // ------------------------------------------------------------------ 部屋の並び

    private static LabLayout layout(GameTestHelper helper, long seed) {
        var templates = helper.getLevel().getStructureManager();
        return LabLayout.create(seed, name -> templates.getOrCreate(LabPieces.Room.id(name)).getSize(), 1000, 2000, -30);
    }

    /**
     * どの種でも: 必ずある部屋がそろい、同じ部屋は2つなく、部屋どうし・廊下と部屋は重ならない（1マス以上空く）。
     * すべての部屋へエントランスから行け、ボス部屋は動力炉とだけ、隔壁の出入口1つでつながる。小部屋は大きな部屋にだけつく。
     */
    @GameTest(template = EMPTY)
    public static void layoutConnectsEveryRoom(GameTestHelper helper) {
        for (long seed = 0; seed < 120; seed++) {
            LabLayout l = layout(helper, seed);
            List<String> names = l.rooms.stream().map(LabLayout.Placed::name).toList();
            for (String need : List.of(LabLayout.ENTRANCE, LabLayout.REACTOR, LabLayout.BOSS, LabLayout.SUBSTATION, "control", "loading_dock", LabLayout.FREIGHT_LIFT)) {
                helper.assertTrue(names.contains(need), "種 " + seed + ": " + need + " がない");
            }
            helper.assertTrue(names.stream().distinct().count() == names.size() || names.stream().filter(n -> n.startsWith("annex_")).count() > 1,
                    "種 " + seed + ": 同じ部屋が2つある: " + names);
            helper.assertTrue(names.size() >= 12, "種 " + seed + ": 部屋が少ない: " + names.size());
            for (int i = 0; i < l.rooms.size(); i++) {
                BoundingBox bi = l.rooms.get(i).box();
                helper.assertTrue(Math.abs(bi.getCenter().getX() - 1000) <= LabLayout.REACH && Math.abs(bi.getCenter().getZ() - 2000) <= LabLayout.REACH,
                        "種 " + seed + ": 遠すぎる部屋");
                for (int j = i + 1; j < l.rooms.size(); j++) {
                    helper.assertFalse(bi.intersects(l.rooms.get(j).box()), "種 " + seed + ": 部屋が重なった: " + names.get(i) + " / " + names.get(j));
                }
                for (BoundingBox c : l.corridorBoxes()) {
                    BoundingBox inner = new BoundingBox(c.minX() + 1, c.minY() + 1, c.minZ() + 1, c.maxX() - 1, c.maxY() - 1, c.maxZ() - 1);
                    helper.assertFalse(inner.intersects(bi), "種 " + seed + ": 廊下が部屋の中を通った: " + names.get(i));
                }
            }
            // 屋外の代わりの部屋はどちらか必ずある
            helper.assertTrue(java.util.Arrays.stream(LabLayout.OUTDOOR).anyMatch(names::contains), "種 " + seed + ": 日光浴室も屋外試験場もない");
            // 動力炉は型紙の北を入口へ、ボス部屋は型紙の南（操作卓の背後）へ
            LabLayout.Placed rp = l.rooms.get(names.indexOf(LabLayout.REACTOR));
            net.minecraft.core.BlockPos southWall = LabLayout.wallCenter(rp.box(), rp.rotation().rotate(net.minecraft.core.Direction.SOUTH));
            net.minecraft.core.BlockPos northWall = LabLayout.wallCenter(rp.box(), rp.rotation().rotate(net.minecraft.core.Direction.NORTH));
            helper.assertTrue(l.doors.stream().anyMatch(o -> o.wall().equals(southWall) && !o.bulkhead()), "種 " + seed + ": ボス部屋への出口が動力炉の操作卓の背後にない");
            helper.assertTrue(l.doors.stream().anyMatch(o -> o.wall().equals(northWall)), "種 " + seed + ": 動力炉の入口が型紙の北にない");
            long bulkheads = l.doors.stream().filter(LabLayout.Door::bulkhead).count();
            helper.assertTrue(bulkheads == 1, "種 " + seed + ": 隔壁が " + bulkheads);
            int boss = names.indexOf(LabLayout.BOSS), reactor = names.indexOf(LabLayout.REACTOR);
            List<int[]> bossLinks = l.links.stream().filter(k -> k[0] == boss || k[1] == boss).toList();
            helper.assertTrue(bossLinks.size() == 1 && (bossLinks.get(0)[0] == reactor || bossLinks.get(0)[1] == reactor),
                    "種 " + seed + ": ボス部屋が動力炉の先にない");
            for (int[] k : l.links) {
                String other = names.get(k[1]);
                if (other.startsWith("annex_")) {
                    BoundingBox host = l.rooms.get(k[0]).box();
                    helper.assertTrue(Math.max(host.getXSpan(), host.getZSpan()) >= 17, "種 " + seed + ": 小部屋が小さな部屋についた");
                }
            }
            boolean[] seen = new boolean[l.rooms.size()];
            ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(0));
            seen[0] = true;
            while (!queue.isEmpty()) {
                int c = queue.poll();
                for (int[] k : l.links) {
                    int n = k[0] == c ? k[1] : k[1] == c ? k[0] : -1;
                    if (n >= 0 && !seen[n]) {
                        seen[n] = true;
                        queue.add(n);
                    }
                }
            }
            for (int i = 0; i < seen.length; i++) {
                helper.assertTrue(seen[i], "種 " + seed + ": " + names.get(i) + " へ行けない");
            }
        }
        helper.succeed();
    }

    /** 部品: 耐圧殻がいちばん先、主立坑がいちばん後。部屋の部品の数は並びの部屋の数、廊下の部品は廊下の数。 */
    @GameTest(template = EMPTY)
    public static void piecesFollowTheLayout(GameTestHelper helper) {
        var templates = helper.getLevel().getStructureManager();
        for (long seed = 0; seed < 20; seed++) {
            StructurePiecesBuilder b = new StructurePiecesBuilder();
            LabStructure.addPieces(b, templates, seed, 1000, 2000, -30, 70);
            List<StructurePiece> pieces = b.build().pieces();
            LabLayout l = layout(helper, seed);
            helper.assertTrue(pieces.stream().filter(p -> p instanceof LabPieces.AccessShaft).count() == 1, "搬入出立坑がないか重複している");
            var freight = LabStructure.accessRoom(l, 1000, 2000);
            var shaft = pieces.stream().filter(p -> p instanceof LabPieces.AccessShaft).findFirst().orElseThrow().getBoundingBox();
            helper.assertTrue(shaft.getCenter().getX() == freight.box().getCenter().getX()
                    && shaft.getCenter().getZ() == freight.box().getCenter().getZ(), "搬入出立坑が専用室につながっていない");
            helper.assertTrue(pieces.get(0) instanceof LabPieces.Hull, "耐圧殻がいちばん先でない");
            helper.assertTrue(pieces.get(pieces.size() - 1) instanceof LabPieces.Shaft, "主立坑がいちばん後でない");
            helper.assertTrue(pieces.stream().filter(p -> p instanceof LabPieces.Room).count() == l.rooms.size(), "部屋の部品の数が違う");
            helper.assertTrue(pieces.stream().filter(p -> p instanceof LabPieces.Corridor).count() == l.corridors.size(), "廊下の部品の数が違う");
            for (StructurePiece p : pieces) {
                if (p instanceof LabPieces.Room r) {
                    LabLayout.Placed placed = l.rooms.stream().filter(x -> x.name().equals(r.name()) && x.box().minX() == r.getBoundingBox().minX()
                            && x.box().minZ() == r.getBoundingBox().minZ()).findFirst().orElseThrow();
                    helper.assertTrue(r.getBoundingBox().getXSpan() == placed.box().getXSpan() && r.getBoundingBox().getZSpan() == placed.box().getZSpan(),
                            "種 " + seed + ": " + r.name() + " の型紙が並びの大きさと合わない");
                }
            }
            helper.assertTrue(pieces.get(0).getBoundingBox().isInside(new BlockPos(1000, -29, 2000)), "主立坑の下が施設の中でない");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 型紙

    private static Optional<StructureTemplate> template(GameTestHelper helper, String name) {
        return helper.getLevel().getStructureManager().get(Singulo.id("lab/" + name));
    }

    private static int count(StructureTemplate t, Block block) {
        return t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
    }

    /** 型紙: すべての部屋があり、幅・奥行きは奇数。ボス部屋に保管庫・封印核・製造ライン4つ、動力炉に制御盤と炉心と導管、エントランスに端末。 */
    @GameTest(template = EMPTY)
    public static void templatesHaveTheirFixtures(GameTestHelper helper) {
        List<String> all = new ArrayList<>(List.of(LabLayout.ENTRANCE, LabLayout.REACTOR, LabLayout.BOSS, LabLayout.SUBSTATION));
        all.addAll(List.of(LabLayout.REQUIRED));
        all.addAll(List.of(LabLayout.OPTIONAL));
        all.addAll(List.of(LabLayout.ANNEXES));
        java.util.Set<String> sizes = new java.util.HashSet<>();
        for (String name : all) {
            Optional<StructureTemplate> t = template(helper, name);
            helper.assertTrue(t.isPresent(), "型紙がない: " + name);
            var size = t.get().getSize();
            helper.assertTrue(size.getX() % 2 == 1 && size.getZ() % 2 == 1, "型紙の幅・奥行きが奇数でない: " + name);
            sizes.add(size.getX() + "x" + size.getY() + "x" + size.getZ());
            if (!name.equals(LabLayout.BOSS)) {
                helper.assertTrue(count(t.get(), SinguloBlocks.EMERGENCY_LIGHT.get()) >= 2, "非常灯がない: " + name);
            }
        }
        helper.assertTrue(sizes.size() >= 10, "部屋の大きさの種類が少ない: " + sizes);
        StructureTemplate boss = template(helper, LabLayout.BOSS).orElseThrow();
        var caches = boss.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), SinguloBlocks.RUIN_CACHE.get());
        helper.assertTrue(caches.size() == 1 && caches.get(0).nbt() != null && "android_lab".equals(caches.get(0).nbt().getString("ruin")),
                "ボス部屋に研究所の保管庫が1つない");
        helper.assertTrue(count(boss, SinguloBlocks.GUARDIAN_CORE.get()) == 1, "ボス部屋に封印核がない");
        helper.assertTrue(boss.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(Singulo.id("sealed_container_3"))).size() == 1, "ボス部屋に封印コンテナがない");
        helper.assertTrue(count(boss, SinguloBlocks.FACTORY_LINE.get()) == 4, "ボス部屋の製造ラインが4つでない");
        helper.assertTrue(count(boss, SinguloBlocks.PRESSURE_HULL.get()) > 1000, "ボス部屋の壁が耐圧殻でない");
        StructureTemplate reactor = template(helper, LabLayout.REACTOR).orElseThrow();
        helper.assertTrue(count(reactor, SinguloBlocks.REACTOR_CONSOLE.get()) == 1, "動力炉の部屋に制御盤がない");
        helper.assertTrue(count(reactor, SinguloBlocks.REACTOR_CORE.get()) >= 9 && count(reactor, SinguloBlocks.POWER_CONDUIT.get()) >= 20,
                "動力炉の部屋の炉心・導管が少ない");
        helper.assertTrue(count(template(helper, LabLayout.ENTRANCE).orElseThrow(), SinguloBlocks.LAB_TERMINAL.get()) == 1, "エントランスに端末がない");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 電力

    /** 動力炉を起動すると: 施設灯が光り、非常灯が消え、隔壁が開いて通れるようになり、制御盤も動いている形になる。範囲の外は変わらない。 */
    @GameTest(template = EMPTY, timeoutTicks = ReactorConsoleBlockEntity.STARTUP + 40)
    public static void reactorRestoresPower(GameTestHelper helper) {
        BlockPos console = new BlockPos(1, 1, 1), lamp = new BlockPos(3, 3, 3), alarm = new BlockPos(4, 3, 3), door = new BlockPos(5, 1, 3);
        helper.setBlock(console, SinguloBlocks.REACTOR_CONSOLE.get());
        helper.setBlock(lamp, SinguloBlocks.FACILITY_LIGHT.get());
        helper.setBlock(alarm, SinguloBlocks.EMERGENCY_LIGHT.get());
        helper.setBlock(door, SinguloBlocks.BULKHEAD.get());
        BlockPos outside = new BlockPos(6, 1, 6);
        helper.setBlock(outside, SinguloBlocks.FACILITY_LIGHT.get());
        ReactorConsoleBlockEntity be = helper.getBlockEntity(console);
        be.setFacility(BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(5, 4, 5))));
        helper.assertTrue(helper.getLevel().getLightEmission(helper.absolutePos(alarm)) > 0, "電力がないのに非常灯が消えている");
        helper.assertTrue(helper.getLevel().getLightEmission(helper.absolutePos(lamp)) == 0, "電力がないのに施設灯が光っている");
        be.start(helper.getLevel(), null);
        helper.assertTrue(be.starting(), "起動が始まらない");
        helper.runAtTickTime(ReactorConsoleBlockEntity.STARTUP + 5, () -> {
            helper.assertTrue(be.powered(), "起動しても電力が戻らない");
            helper.assertTrue(helper.getBlockState(lamp).getValue(LabPoweredBlock.POWERED)
                    && helper.getLevel().getLightEmission(helper.absolutePos(lamp)) == 15, "施設灯が光らない");
            helper.assertTrue(helper.getLevel().getLightEmission(helper.absolutePos(alarm)) == 0, "電力が戻っても非常灯が消えない");
            helper.assertTrue(helper.getBlockState(door).getValue(BulkheadBlock.OPEN)
                    && helper.getBlockState(door).getCollisionShape(helper.getLevel(), helper.absolutePos(door)).isEmpty(), "隔壁が開かない");
            helper.assertFalse(helper.getBlockState(outside).getValue(LabPoweredBlock.POWERED), "施設の外まで電力が戻った");
            helper.succeed();
        });
    }

    /** 部品を、テストの場所の絶対座標の範囲（チャンクの区切りの代わりに全体）で描く。 */
    private static void draw(GameTestHelper helper, net.minecraft.world.level.levelgen.structure.StructurePiece piece) {
        var level = helper.getLevel();
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                piece.getBoundingBox(), new net.minecraft.world.level.ChunkPos(piece.getBoundingBox().getCenter()), piece.getBoundingBox().getCenter());
    }

    /** 耐圧殻は部屋の箱から2マス以内だけ（部屋の中と、3マス以上離れた所は描かない）。 */
    @GameTest(template = "huge")
    public static void hullWrapsRoomsTwoThick(GameTestHelper helper) {
        BlockPos o = helper.absolutePos(new BlockPos(2, 1, 2));
        var room = new net.minecraft.world.level.levelgen.structure.BoundingBox(o.getX() + 6, o.getY() + 4, o.getZ() + 6,
                o.getX() + 10, o.getY() + 8, o.getZ() + 10);
        var facility = new net.minecraft.world.level.levelgen.structure.BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 16, o.getY() + 14, o.getZ() + 16);
        draw(helper, new LabPieces.Hull(facility, List.of(room)));
        var level = helper.getLevel();
        java.util.function.Function<BlockPos, Boolean> sealed = p -> level.getBlockState(p).is(SinguloBlocks.PRESSURE_HULL.get())
                && level.getBlockState(p).getValue(PressureHullBlock.SEALED);
        BlockPos c = new BlockPos(o.getX() + 8, o.getY() + 6, o.getZ() + 8);
        helper.assertTrue(!level.getBlockState(c).is(SinguloBlocks.PRESSURE_HULL.get()), "部屋の中に耐圧殻がある");
        helper.assertTrue(sealed.apply(c.east(3)) && sealed.apply(c.east(4)), "部屋の外の1〜2マスが封じられた耐圧殻でない");
        helper.assertTrue(sealed.apply(c.above(3)) && sealed.apply(c.above(4)), "部屋の上の1〜2マスが封じられた耐圧殻でない");
        helper.assertTrue(!level.getBlockState(c.east(5)).is(SinguloBlocks.PRESSURE_HULL.get()), "部屋から3マス離れた所にも耐圧殻がある");
        helper.assertTrue(!level.getBlockState(new BlockPos(o.getX() + 1, o.getY() + 1, o.getZ() + 1)).is(SinguloBlocks.PRESSURE_HULL.get()),
                "施設の範囲の隅（部屋から遠い所）まで耐圧殻で埋まる");
        helper.succeed();
    }

    /**
     * 搬入出の立坑: 部屋の天井と地表の1段下は封じられた溶接の蓋、間は空洞で壁はパネル。封じが解けると、ダイヤのツルハシで掘れる
     * （鉄のツルハシでは掘っても落ちない）。
     */
    @GameTest(template = "huge")
    public static void accessShaftIsWeldedAtBothEnds(GameTestHelper helper) {
        BlockPos o = helper.absolutePos(new BlockPos(6, 1, 6));
        var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(o.getX() - 2, o.getY(), o.getZ() - 2,
                o.getX() + 2, o.getY() + 14, o.getZ() + 2);
        draw(helper, new LabPieces.AccessShaft(box));
        var level = helper.getLevel();
        BlockPos bottom = o, top = o.above(13), mid = o.above(7);
        for (BlockPos p : new BlockPos[]{bottom, top}) {
            helper.assertTrue(level.getBlockState(p).is(SinguloBlocks.WELDED_HATCH.get()) && level.getBlockState(p).getValue(PressureHullBlock.SEALED),
                    "立坑の端が封じられた溶接の蓋でない: " + p);
        }
        helper.assertTrue(level.getBlockState(mid).isAir() && level.getBlockState(o.above(14)).isAir(), "立坑の中か地表の口が空洞でない");
        helper.assertTrue(level.getBlockState(mid.east(2)).is(SinguloBlocks.LAB_PANEL.get()), "立坑の壁が研究所のパネルでない");
        helper.assertTrue(level.getBlockState(o.above(1).east(2)).is(SinguloBlocks.PRESSURE_HULL.get()), "耐圧殻の高さの壁が耐圧殻でない");
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
        helper.assertTrue(level.getBlockState(top).getDestroyProgress(player, level, top) == 0, "封じられた蓋が掘れる");
        io.github.genichimaruo.singulo.ruin.lab.LabPower.unseal(level, box);
        helper.assertTrue(level.getBlockState(top).getDestroyProgress(player, level, top) > 0 && player.hasCorrectToolForDrops(level.getBlockState(top)),
                "封じが解けた蓋がダイヤのツルハシで掘れない");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        helper.assertFalse(player.hasCorrectToolForDrops(level.getBlockState(top)), "鉄のツルハシでも蓋が掘れる");
        helper.succeed();
    }

    /** 地表の建物: 床・昇降路の扉の枠・入場ゲートの柱・受付のカウンター・出入口・屋根。 */
    @GameTest(template = "huge")
    public static void entranceBuildingStandsOverTheShaft(GameTestHelper helper) {
        BlockPos o = helper.absolutePos(new BlockPos(8, 2, 6));
        draw(helper, new LabPieces.Entrance(o.getX(), o.getZ(), o.getY(), 7));
        var level = helper.getLevel();
        java.util.function.BiFunction<Integer, Integer, java.util.function.IntFunction<net.minecraft.world.level.block.state.BlockState>> at =
                (dx, dz) -> h -> level.getBlockState(o.offset(dx, h, dz));
        helper.assertTrue(at.apply(0, 6).apply(0).is(SinguloBlocks.LAB_TILES.get()), "ホールの床が研究所の床タイルでない");
        helper.assertTrue(at.apply(0, 0).apply(0).isAir(), "昇降路の口がふさがっている");
        helper.assertTrue(at.apply(2, 3).apply(1).is(SinguloBlocks.LAB_FRAME.get()) && at.apply(0, 3).apply(1).isAir(),
                "エレベーターの扉の枠と口がない");
        helper.assertTrue(at.apply(-1, 6).apply(1).is(SinguloBlocks.LAB_FRAME.get()), "入場ゲートの柱がない");
        helper.assertTrue(at.apply(-3, 8).apply(1).is(SinguloBlocks.LAB_PANEL_LINED.get()), "受付のカウンターがない");
        helper.assertTrue(at.apply(0, 9).apply(1).isAir() && at.apply(0, 9).apply(2).isAir(), "出入口が開いていない");
        helper.assertTrue(at.apply(0, 6).apply(5).is(SinguloBlocks.LAB_PANEL.get()) || at.apply(0, 6).apply(5).is(SinguloBlocks.LAB_PANEL_LINED.get()),
                "屋根がない");
        helper.succeed();
    }

    /** 封じられた耐圧殻は掘れず（壊す速さ 0）、封じが解けると、黒曜石よりは遅いが掘れる。 */
    @GameTest(template = EMPTY)
    public static void sealedHullCannotBeMined(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, SinguloBlocks.PRESSURE_HULL.get().defaultBlockState().setValue(PressureHullBlock.SEALED, true));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE));
        BlockPos abs = helper.absolutePos(at);
        helper.assertTrue(helper.getBlockState(at).getDestroyProgress(player, helper.getLevel(), abs) == 0, "封じられた耐圧殻が掘れる");
        helper.setBlock(at, SinguloBlocks.PRESSURE_HULL.get().defaultBlockState().setValue(PressureHullBlock.SEALED, false));
        float hull = helper.getBlockState(at).getDestroyProgress(player, helper.getLevel(), abs);
        helper.setBlock(at, net.minecraft.world.level.block.Blocks.OBSIDIAN);
        float obsidian = helper.getBlockState(at).getDestroyProgress(player, helper.getLevel(), abs);
        helper.assertTrue(hull > 0 && hull < obsidian, "封じが解けた耐圧殻の硬さが違う: " + hull + " / 黒曜石 " + obsidian);
        helper.succeed();
    }

    /**
     * 研究所は地下の飾り（スカルクの塊・スカルクヴェイン）より後、木や草より前に置く。先に置くと部屋の中にスカルクヴェインが這う
     * （耐圧殻で囲まれた中へは、あとから外のスカルクは広がれない）。
     */
    @GameTest(template = EMPTY)
    public static void labIsPlacedAfterSculk(GameTestHelper helper) {
        var lab = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(Singulo.id("android_lab"));
        helper.assertTrue(lab != null, "研究所の構造物がない");
        var step = lab.step();
        helper.assertTrue(step.ordinal() > net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal()
                && step.ordinal() < net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal(),
                "研究所を置く段階がスカルクの後・木の前でない: " + step);
        helper.succeed();
    }

    /**
     * 収納の中身: どの表も、開けたときに何種類も入っていて（20回引いた平均で5種類以上の束）、バニラの品を含む。
     * 部屋に合った表を使う（食堂は厨房、個室は私物、医務室は手当ての品、個室のロッカーの樽は私物）。
     */
    @GameTest(template = EMPTY)
    public static void labContainersHoldFittingLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        for (String name : List.of("lab_supplies", "lab_research", "lab_office", "lab_quarters", "lab_locker", "lab_kitchen", "lab_medical")) {
            var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.LOOT_TABLE, Singulo.id("chests/" + name)));
            int stacks = 0;
            boolean vanilla = false;
            for (int i = 0; i < 20; i++) {
                var items = table.getRandomItems(new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO))
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST));
                stacks += items.size();
                vanilla |= items.stream().anyMatch(s -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("minecraft"));
            }
            int min = name.equals("lab_locker") ? 2 : 5;
            helper.assertTrue(stacks >= min * 20, name + " の中身が少ない: 平均 " + stacks / 20F);
            helper.assertTrue(vanilla, name + " にバニラの品が入らない");
        }
        for (var room : List.of(java.util.Map.entry("cafeteria", "lab_kitchen"), java.util.Map.entry("private_rooms", "lab_quarters"),
                java.util.Map.entry("dorm", "lab_quarters"), java.util.Map.entry("medical", "lab_medical"), java.util.Map.entry("office", "lab_office"))) {
            StructureTemplate t = template(helper, room.getKey()).orElseThrow();
            var chests = t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), net.minecraft.world.level.block.Blocks.CHEST);
            helper.assertFalse(chests.isEmpty(), room.getKey() + " にチェストがない");
            for (var info : chests) {
                helper.assertTrue(info.nbt() != null && info.nbt().getString("LootTable").equals(Singulo.MODID + ":chests/" + room.getValue()),
                        room.getKey() + " のチェストの中身が " + room.getValue() + " でない");
            }
        }
        for (var info : template(helper, "private_rooms").orElseThrow().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                net.minecraft.world.level.block.Blocks.BARREL)) {
            helper.assertTrue(info.nbt() != null && info.nbt().getString("LootTable").endsWith("chests/lab_locker"), "個室のロッカーが空");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 自販機・コーヒーメーカー

    private static net.minecraft.world.item.Item item(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    /**
     * 自販機: 研究所のもの（止まっている）は売らない。施設の電力が戻ると、エメラルドで押した列の飲み物が出る
     * （左からソーダ1・スポーツドリンク1・エナジードリンク2。足りないと買えない。正面以外を押しても買えない）。
     */
    @GameTest(template = EMPTY)
    public static void vendingMachineSellsForEmeralds(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos at = new BlockPos(2, 1, 2);
        var lower = SinguloBlocks.VENDING_MACHINE.get().defaultBlockState().setValue(io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.POWERED, false);
        helper.setBlock(at, lower);
        helper.setBlock(at.above(), lower.setValue(io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        BlockPos abs = helper.absolutePos(at);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var emeralds = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 3);
        // 北を向いた正面に向き合うと、右手は西（x が小さい側）。左の列は x の大きい側
        java.util.function.BiFunction<Double, net.minecraft.core.Direction, BlockHitResult> press = (dx, face) ->
                new BlockHitResult(new net.minecraft.world.phys.Vec3(abs.getX() + dx, abs.getY() + 0.5, abs.getZ()), face, abs, false);
        java.util.function.Supplier<net.minecraft.world.level.block.state.BlockState> state = () -> level.getBlockState(abs);
        helper.assertTrue(io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.buy(state.get(), level, abs, player, emeralds, press.apply(0.9, net.minecraft.core.Direction.NORTH)) == null
                && emeralds.getCount() == 3, "電力のない自販機で買えた");
        LabPower.restore(level, new BoundingBox(abs.getX(), abs.getY(), abs.getZ(), abs.getX(), abs.getY() + 1, abs.getZ()));
        helper.assertTrue(state.get().getValue(io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.POWERED) && level.getBlockState(abs.above()).getValue(io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.POWERED), "電力が戻っても動かない");
        helper.assertTrue(io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.buy(state.get(), level, abs, player, emeralds, press.apply(0.5, net.minecraft.core.Direction.EAST)) == null,
                "正面以外を押して買えた");
        var soda = io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.buy(state.get(), level, abs, player, emeralds, press.apply(0.9, net.minecraft.core.Direction.NORTH));
        helper.assertTrue(soda != null && soda.is(item("canned_soda")) && emeralds.getCount() == 2, "左の列でソーダが1エメラルドで買えない");
        var energy = io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.buy(state.get(), level, abs, player, emeralds, press.apply(0.1, net.minecraft.core.Direction.NORTH));
        helper.assertTrue(energy != null && energy.is(item("energy_drink")) && emeralds.isEmpty(), "右の列でエナジードリンクが2エメラルドで買えない");
        var one = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 1);
        helper.assertTrue(io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.buy(state.get(), level, abs, player, one, press.apply(0.1, net.minecraft.core.Direction.NORTH)) == null
                && one.getCount() == 1, "エメラルドが足りないのに買えた");
        var sports = io.github.genichimaruo.singulo.ruin.lab.VendingMachineBlock.buy(state.get(), level, abs, player, one, press.apply(0.5, net.minecraft.core.Direction.NORTH));
        helper.assertTrue(sports != null && sports.is(item("sports_drink")), "真ん中の列でスポーツドリンクが買えない");
        helper.succeed();
    }

    /** コーヒーメーカー: カカオ豆で淹れ（少しして3杯分）、ガラス瓶でくむとコーヒーになり、杯数が減る。空のときは瓶でくめない。 */
    @GameTest(template = EMPTY, timeoutTicks = io.github.genichimaruo.singulo.ruin.lab.CoffeeMakerBlock.BREW_TIME + 40)
    public static void coffeeMakerBrewsAndFillsBottles(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, SinguloBlocks.COFFEE_MAKER.get().defaultBlockState());
        BlockPos abs = helper.absolutePos(at);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var hit = new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), net.minecraft.core.Direction.NORTH, abs, false);
        java.util.function.Consumer<net.minecraft.world.item.ItemStack> use = stack -> {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            level.getBlockState(abs).useItemOn(stack, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        };
        var bottle = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 4);
        use.accept(bottle);
        helper.assertTrue(bottle.getCount() == 4, "空のポットから瓶でくめた");
        var beans = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COCOA_BEANS, 2);
        use.accept(beans);
        helper.assertTrue(beans.getCount() == 1 && helper.getBlockState(at).getValue(io.github.genichimaruo.singulo.ruin.lab.CoffeeMakerBlock.BREWING), "カカオ豆で淹れはじめない");
        helper.runAtTickTime(io.github.genichimaruo.singulo.ruin.lab.CoffeeMakerBlock.BREW_TIME + 5, () -> {
            var s = helper.getBlockState(at);
            helper.assertTrue(!s.getValue(io.github.genichimaruo.singulo.ruin.lab.CoffeeMakerBlock.BREWING) && s.getValue(io.github.genichimaruo.singulo.ruin.lab.CoffeeMakerBlock.CUPS) == 3, "淹れ上がって3杯分にならない");
            use.accept(bottle);
            helper.assertTrue(bottle.getCount() == 3 && player.getInventory().countItem(item("coffee")) == 1
                    && helper.getBlockState(at).getValue(io.github.genichimaruo.singulo.ruin.lab.CoffeeMakerBlock.CUPS) == 2, "瓶でコーヒーがくめない");
            helper.succeed();
        });
    }

    /** 飲み物: 飲むと効果がつく（エナジードリンクは採掘速度上昇 II）。コーヒーは眠気を覚まし（最後に眠ってからの時間が 0）、瓶が残る。 */
    @GameTest(template = EMPTY)
    public static void drinksGiveTheirEffects(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);           // クリエイティブでは飲んでも減らない（瓶も残らない）
        new net.minecraft.world.item.ItemStack(item("energy_drink")).finishUsingItem(level, player);
        var haste = player.getEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED);
        helper.assertTrue(haste != null && haste.getAmplifier() == 1, "エナジードリンクで採掘速度上昇 II がつかない");
        new net.minecraft.world.item.ItemStack(item("canned_soda")).finishUsingItem(level, player);
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED), "ソーダで移動速度上昇がつかない");
        new net.minecraft.world.item.ItemStack(item("sports_drink")).finishUsingItem(level, player);
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "スポーツドリンクで再生がつかない");
        var rest = net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST);
        player.getStats().setValue(player, rest, 100000);
        var left = new net.minecraft.world.item.ItemStack(item("coffee")).finishUsingItem(level, player);
        helper.assertTrue(player.getStats().getValue(rest) == 0, "コーヒーで眠気が覚めない");
        helper.assertTrue(left.is(net.minecraft.world.item.Items.GLASS_BOTTLE), "コーヒーを飲んでも瓶が残らない: " + left);
        helper.succeed();
    }

    // ------------------------------------------------------------------ スライドドア

    /** 下半分と上半分のスライドドアを置く（扉の面は north）。 */
    private static void placeDoor(GameTestHelper helper, BlockPos at, Block door) {
        var state = door.defaultBlockState().setValue(SlidingDoorBlock.FACING, net.minecraft.core.Direction.NORTH);
        helper.setBlock(at, state);
        helper.setBlock(at.above(), state.setValue(SlidingDoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }

    /** ドアの真ん中を通れるか（閉じていると扉に当たり、開いていると左右の枠だけが残る）。 */
    private static boolean passable(GameTestHelper helper, BlockPos at) {
        var shape = helper.getBlockState(at).getCollisionShape(helper.getLevel(), helper.absolutePos(at));
        return !net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape,
                net.minecraft.world.phys.shapes.Shapes.box(0.25, 0, 0.25, 0.75, 1, 0.75), net.minecraft.world.phys.shapes.BooleanOp.AND);
    }

    private static boolean open(GameTestHelper helper, BlockPos at) {
        return helper.getBlockState(at).getValue(SlidingDoorBlock.OPEN);
    }

    /** 右クリックで上下とも開いて通れるようになり、誰もいなければ少しして自分で閉まる。 */
    @GameTest(template = EMPTY, timeoutTicks = SlidingDoorBlock.CLOSE_DELAY + 40)
    public static void slidingDoorClosesByItself(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        placeDoor(helper, at, SinguloBlocks.LAB_DOOR.get());
        helper.assertFalse(passable(helper, at) || passable(helper, at.above()), "閉じたドアを通れる");
        helper.useBlock(at.above(), helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
        helper.assertTrue(open(helper, at) && open(helper, at.above()), "右クリックで上下とも開かない");
        helper.assertTrue(passable(helper, at) && passable(helper, at.above()), "開いたドアを通れない");
        helper.runAtTickTime(SlidingDoorBlock.CLOSE_DELAY + 10, () -> {
            helper.assertFalse(open(helper, at) || open(helper, at.above()), "誰もいないのに閉まらない");
            helper.succeed();
        });
    }

    /** ドアの中に誰かいる間と、レッドストーンの信号が入っている間は閉まらない。 */
    @GameTest(template = EMPTY, timeoutTicks = SlidingDoorBlock.CLOSE_DELAY * 2 + 60)
    public static void slidingDoorWaitsForPassersAndRedstone(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2), wired = new BlockPos(5, 1, 2);
        placeDoor(helper, at, SinguloBlocks.WHITE_SLIDING_DOOR.get());
        placeDoor(helper, wired, SinguloBlocks.WHITE_SLIDING_DOOR.get());
        var stand = helper.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, at);
        helper.useBlock(at, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
        helper.setBlock(wired.west(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        helper.assertTrue(open(helper, wired) && helper.getBlockState(wired.above()).getValue(SlidingDoorBlock.POWERED), "信号で開かない");
        helper.runAtTickTime(SlidingDoorBlock.CLOSE_DELAY + 20, () -> {
            helper.assertTrue(open(helper, at), "中に人がいるのに閉まった");
            helper.assertTrue(open(helper, wired), "信号が入っているのに閉まった");
            stand.discard();
            helper.setBlock(wired.west(), net.minecraft.world.level.block.Blocks.AIR);
        });
        helper.runAtTickTime(SlidingDoorBlock.CLOSE_DELAY * 2 + 40, () -> {
            helper.assertFalse(open(helper, at), "人がいなくなっても閉まらない");
            helper.assertFalse(open(helper, wired), "信号が切れても閉まらない");
            helper.succeed();
        });
    }

    /** 研究所のドアはレシピがなく、似せたホワイトスライドドアは作業台で作れる。どちらも下半分だけが落とす（壊すと1つ）。 */
    @GameTest(template = EMPTY)
    public static void onlyTheWhiteSlidingDoorIsCraftable(GameTestHelper helper) {
        var level = helper.getLevel();
        java.util.function.Predicate<net.minecraft.world.item.Item> craftable = item -> level.getRecipeManager().getRecipes().stream()
                .anyMatch(r -> r.value().getResultItem(level.registryAccess()).is(item));
        helper.assertFalse(craftable.test(SinguloBlocks.LAB_DOOR.get().asItem()), "研究所のドアにレシピがある");
        helper.assertTrue(craftable.test(SinguloBlocks.WHITE_SLIDING_DOOR.get().asItem()), "ホワイトスライドドアにレシピがない");
        BlockPos at = new BlockPos(2, 1, 2);
        placeDoor(helper, at, SinguloBlocks.LAB_DOOR.get());
        level.destroyBlock(helper.absolutePos(at.above()), true);
        helper.assertTrue(helper.getBlockState(at).isAir() && helper.getBlockState(at.above()).isAir(), "上を壊しても下が残る");
        helper.succeed();
    }

    /** 個室区画には個室のドアが8つ（16ブロック）、食堂には厨房のドアが1つ。どのドアも前後が空いている（家具でふさがれていない）。 */
    @GameTest(template = EMPTY)
    public static void privateRoomsAndKitchenHaveDoors(GameTestHelper helper) {
        for (var room : List.of(java.util.Map.entry("private_rooms", 16), java.util.Map.entry("cafeteria", 2))) {
            StructureTemplate t = template(helper, room.getKey()).orElseThrow();
            helper.assertTrue(count(t, SinguloBlocks.LAB_DOOR.get()) == room.getValue(),
                    room.getKey() + " のドアの数が違う: " + count(t, SinguloBlocks.LAB_DOOR.get()));
            java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> all = new java.util.HashMap<>();
            for (var info : t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), net.minecraft.world.level.block.Blocks.AIR)) {
                all.put(info.pos(), info.state());
            }
            for (var info : t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), SinguloBlocks.LAB_DOOR.get())) {
                var facing = info.state().getValue(SlidingDoorBlock.FACING);
                for (BlockPos side : List.of(info.pos().relative(facing), info.pos().relative(facing.getOpposite()))) {
                    helper.assertTrue(all.containsKey(side), room.getKey() + " のドアの前後がふさがっている: " + side);
                }
            }
        }
        helper.succeed();
    }

    /** 記録端末は、番号ごとに題と本文の記録を持つ（番号は記録の数で回る）。 */
    @GameTest(template = EMPTY)
    public static void terminalsHoldRecords(GameTestHelper helper) {
        for (int i = 0; i < LabTerminalBlock.LOGS + 2; i++) {
            var lines = LabTerminalBlock.lines(i);
            helper.assertTrue(lines.length == 2, "記録の行が2つでない");
            String key = ((net.minecraft.network.chat.contents.TranslatableContents) lines[1].getContents()).getKey();
            helper.assertTrue(key.equals("lab.singulo.log." + (i % LabTerminalBlock.LOGS)), "記録の番号が違う: " + key);
        }
        helper.succeed();
    }
    @GameTest(template = EMPTY)
    public static void bothShaftsRemainPresentOnSlopingTerrain(GameTestHelper helper) {
        var templates = helper.getLevel().getStructureManager();
        for (int height : new int[]{-20, 0, 20, 45, 70}) {
            int floor = LabStructure.floorForShafts(-30, 70, height, 11);
            var builder = new StructurePiecesBuilder();
            LabStructure.addPieces(builder, templates, 17, 1000, 2000, floor, 70, (x,z) -> height);
            var pieces = builder.build().pieces();
            helper.assertTrue(pieces.stream().filter(p -> p instanceof LabPieces.Shaft).count()==1, "主立坑がない");
            helper.assertTrue(pieces.stream().filter(p -> p instanceof LabPieces.AccessShaft).count()==1, "斜面で搬入出立坑が省略された");
        }
        helper.succeed();
    }

}
