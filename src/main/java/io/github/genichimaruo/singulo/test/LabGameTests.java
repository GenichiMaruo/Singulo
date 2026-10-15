package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.BulkheadBlock;
import io.github.genichimaruo.singulo.ruin.lab.LabLayout;
import io.github.genichimaruo.singulo.ruin.lab.LabPieces;
import io.github.genichimaruo.singulo.ruin.lab.LabPoweredBlock;
import io.github.genichimaruo.singulo.ruin.lab.LabStructure;
import io.github.genichimaruo.singulo.ruin.lab.LabTerminalBlock;
import io.github.genichimaruo.singulo.ruin.lab.PressureHullBlock;
import io.github.genichimaruo.singulo.ruin.lab.ReactorConsoleBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
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
            for (String need : List.of(LabLayout.ENTRANCE, LabLayout.REACTOR, LabLayout.BOSS, LabLayout.SUBSTATION, "control", "loading_dock")) {
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
            helper.assertTrue(pieces.get(0) instanceof LabPieces.Hull, "耐圧殻がいちばん先でない");
            helper.assertTrue(pieces.get(pieces.size() - 1) instanceof LabPieces.Shaft, "主立坑がいちばん後でない");
            helper.assertTrue(pieces.stream().filter(p -> p instanceof LabPieces.Room).count() == l.rooms.size(), "部屋の部品の数が違う");
            helper.assertTrue(pieces.stream().filter(p -> p instanceof LabPieces.Corridor).count() == l.corridors.size(), "廊下の部品の数が違う");
            for (StructurePiece p : pieces) {
                if (p instanceof LabPieces.Room r) {
                    LabLayout.Placed placed = l.rooms.stream().filter(x -> x.name().equals(r.name())).findFirst().orElseThrow();
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
}
