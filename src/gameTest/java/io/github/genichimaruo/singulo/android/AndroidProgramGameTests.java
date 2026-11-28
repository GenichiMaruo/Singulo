package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 動き方「プログラム」（0.24.0）: 入れ物の間で運ぶ・名前の場所・範囲の外・掘る・守られた入れ物・ステーションの場所。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AndroidProgramGameTests {
    private static final String EMPTY = "empty";

    private AndroidProgramGameTests() {}

    private static RuinAndroid android(GameTestHelper h, BlockPos at, String source, List<AndroidProgram.Point> points) {
        RuinAndroid a = SinguloEntities.BLADE_ANDROID.get().create(h.getLevel());
        a.moveTo(Vec3.atBottomCenterOf(h.absolutePos(at)), 0, 0);
        h.getLevel().addFreshEntity(a);
        a.makeGuard(java.util.UUID.randomUUID(), h.absolutePos(at), RuinAndroid.MODE_PROGRAM, 0);
        a.setCharge(a.maxCharge());
        a.setProgram(source, points);
        return a;
    }

    private static ChestBlockEntity chest(GameTestHelper h, BlockPos at) {
        h.setBlock(at, Blocks.CHEST);
        return h.getBlockEntity(at);
    }

    private static int count(ChestBlockEntity c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }

    /** 名前の場所の入れ物から決めた数だけ取り、別の入れ物へ入れて終わる。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void programCarriesItemsBetweenNamedChests(GameTestHelper h) {
        ChestBlockEntity from = chest(h, new BlockPos(3, 1, 2));
        ChestBlockEntity to = chest(h, new BlockPos(1, 1, 2));
        from.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        RuinAndroid a = android(h, new BlockPos(2, 1, 2), """
                # 鉄を6つ運ぶ
                n = take("source", "iron_ingot", 6)
                if n == 6:
                    put("p2")
                """, List.of(new AndroidProgram.Point("source", h.absolutePos(new BlockPos(3, 1, 2))),
                new AndroidProgram.Point("box", h.absolutePos(new BlockPos(1, 1, 2)))));
        h.succeedWhen(() -> {
            h.assertTrue(a.programStatus() == AndroidProgram.P_DONE, "終わらない: " + a.programStatus() + " " + a.programMessage());
            h.assertTrue(count(to, Items.IRON_INGOT) == 6 && count(from, Items.IRON_INGOT) == 4,
                    "運んだ数が違う: " + count(from, Items.IRON_INGOT) + " / " + count(to, Items.IRON_INGOT));
            a.discard();
        });
    }

    /** ステーションから遠すぎる場所・ない場所の名前は、その行の誤りで止まる。 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void programStopsOnFarOrUnknownPlaces(GameTestHelper h) {
        BlockPos far = h.absolutePos(new BlockPos(2, 1, 2)).offset(200, 0, 0);
        RuinAndroid a = android(h, new BlockPos(2, 1, 2),
                "say('start')\ngo(pos(" + far.getX() + ", " + far.getY() + ", " + far.getZ() + "))\n", List.of());
        RuinAndroid b = android(h, new BlockPos(4, 1, 4), "x = 1\n\nuse('lever')\n", List.of());
        h.succeedWhen(() -> {
            h.assertTrue(a.programStatus() == AndroidProgram.P_ERROR && a.programLine() == 2 && a.programMessage().contains("too_far"),
                    "遠い場所で止まらない: " + a.programStatus() + " " + a.programLine() + " " + a.programMessage());
            h.assertTrue(b.programStatus() == AndroidProgram.P_ERROR && b.programLine() == 3 && b.programMessage().contains("no_point"),
                    "ない場所で止まらない: " + b.programStatus() + " " + b.programLine() + " " + b.programMessage());
            a.discard();
            b.discard();
        });
    }

    /** 掘るには採掘アップグレードが要る。積めば、隣のブロックを掘って持ち物に入れる。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void programMinesWithTheUpgrade(GameTestHelper h) {
        BlockPos stone = new BlockPos(3, 1, 2);
        h.setBlock(stone, Blocks.STONE);
        String source = "ok = mine(pos(" + h.absolutePos(stone).getX() + ", " + h.absolutePos(stone).getY() + ", " + h.absolutePos(stone).getZ()
                + "))\nsay(ok, count('cobblestone'))\n";
        RuinAndroid without = android(h, new BlockPos(2, 1, 2), source, List.of());
        h.runAtTickTime(10, () -> {
            h.assertTrue(without.programStatus() == AndroidProgram.P_ERROR && without.programMessage().contains("needs_mining_upgrade"),
                    "採掘アップグレードなしで掘ろうとした: " + without.programMessage());
            h.assertBlockPresent(Blocks.STONE, stone);
            without.inventory().setItem(RuinAndroid.SLOT_MINING, new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id("android_mining_upgrade"))));
            without.restartProgram();
        });
        h.runAtTickTime(12, () -> h.succeedWhen(() -> {
            h.assertBlockNotPresent(Blocks.STONE, stone);
            h.assertTrue(without.programStatus() == AndroidProgram.P_DONE && without.programMessage().equals("s\u0001True 1"),
                    "掘った結果が違う: " + without.programStatus() + " " + without.programMessage());
            without.discard();
        }));
    }

    /** ほかの mod の守り（右クリックの取り消し）がある入れ物からは取らない。 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void programRespectsContainerProtection(GameTestHelper h) {
        ChestBlockEntity from = chest(h, new BlockPos(3, 1, 2));
        from.setItem(0, new ItemStack(Items.DIAMOND, 5));
        BlockPos guarded = h.absolutePos(new BlockPos(3, 1, 2));
        Consumer<PlayerInteractEvent.RightClickBlock> deny = e -> {
            if (e.getPos().equals(guarded)) {
                e.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(deny);
        RuinAndroid a = android(h, new BlockPos(2, 1, 2), "say(take('chest'))\n",
                List.of(new AndroidProgram.Point("chest", guarded)));
        h.succeedWhen(() -> {
            h.assertTrue(a.programStatus() == AndroidProgram.P_DONE, "終わらない: " + a.programStatus());
            NeoForge.EVENT_BUS.unregister(deny);
            h.assertTrue(a.programMessage().equals("s\u00010") && count(from, Items.DIAMOND) == 5, "守られた入れ物から取った: " + a.programMessage());
            a.discard();
        });
    }

    /** ステーションの場所: 巡回路の欄の作業位置カード。金床の名前があればその名前、なければ p と欄の番号。 */
    @GameTest(template = EMPTY)
    public static void stationNamesProgramPointsFromCards(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2);
        h.setBlock(at, SinguloBlocks.ANDROID_STATION.get());
        AndroidStationBlockEntity station = h.getBlockEntity(at);
        ItemStack named = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(named, h.getLevel(), h.absolutePos(new BlockPos(4, 1, 2)), Blocks.CHEST);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(" furnace "));
        ItemStack plain = new ItemStack(SinguloItems.WORK_LOCATION_CARD.get());
        io.github.genichimaruo.singulo.android.work.WorkLocationCardItem.record(plain, h.getLevel(), h.absolutePos(new BlockPos(0, 1, 2)), Blocks.CHEST);
        var items = station.items();
        items.setStackInSlot(AndroidStationBlockEntity.SLOT_ROUTE, named);
        items.setStackInSlot(AndroidStationBlockEntity.SLOT_ROUTE + 2, plain);
        List<AndroidProgram.Point> points = station.points();
        h.assertTrue(points.size() == 2 && points.get(0).name().equals("furnace") && points.get(1).name().equals("p3")
                        && points.get(0).pos().equals(h.absolutePos(new BlockPos(4, 1, 2))),
                "場所の名前が違う: " + points);
        ItemStack card = new ItemStack(SinguloItems.PROGRAM_CARD.get());
        h.assertTrue(station.items().isItemValid(AndroidStationBlockEntity.SLOT_PROGRAM, card), "プログラムカードを入れられない");
        h.succeed();
    }
}
