package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.drone.CargoDrone;
import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import io.github.genichimaruo.singulo.drone.DroneNetwork;
import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;
import io.github.genichimaruo.singulo.drone.DroneStationBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.ruin.GuardDockBlockEntity;
import io.github.genichimaruo.singulo.ruin.SecurityDrone;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ドローン輸送: 梱包・電力・射程・取り消し・受け渡し・見えない飛行・壊したときの保全・メモリの入手。 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class DroneGameTests {
    /** ドローンは高く飛んで、となりのテストの場所の上を通るので、ほかのテストと同時には動かさない。 */
    private static final String BATCH = "drones";
    private static final BlockPos STATION = new BlockPos(1, 1, 1), FROM = new BlockPos(1, 1, 7), TO = new BlockPos(7, 1, 7);

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Singulo.id(id));
    }

    /** テストごとの持ち主（並んで動くほかのテストのポートやステーションと網が混ざらないように）。 */
    private static UUID owner(GameTestHelper h) {
        return UUID.nameUUIDFromBytes(("drone-test" + h.absolutePos(BlockPos.ZERO)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static DronePortBlockEntity port(GameTestHelper h, BlockPos at) {
        h.setBlock(at, SinguloBlocks.DRONE_PORT.get());
        DronePortBlockEntity port = h.getBlockEntity(at);
        port.setOwner(owner(h));
        port.energy().setEnergy(DronePortBlockEntity.CAPACITY);
        DroneNetwork.get(h.getLevel()).putPort(h.absolutePos(at), owner(h), "");
        return port;
    }

    private static void load(DronePortBlockEntity port, int tier, ItemStack... cargo) {
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, new ItemStack(item(new String[]{"", "packing_crate",
                "battery_packing_crate", "large_battery_packing_crate"}[tier]), 2));
        for (int i = 0; i < cargo.length; i++) {
            port.items().setStackInSlot(DronePortBlockEntity.SLOT_CARGO + i, cargo[i]);
        }
    }

    private static int count(DronePortBlockEntity port, int from, int n, Item item) {
        int c = 0;
        for (int i = 0; i < n; i++) {
            ItemStack s = port.items().getStackInSlot(from + i);
            c += s.is(item) ? s.getCount() : 0;
        }
        return c;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void droneCrateRangeFollowsBatteryTier(GameTestHelper h) {
        h.assertTrue(DroneCrateItem.range(1) == 160 && DroneCrateItem.range(2) == 760 && DroneCrateItem.range(3) == 3160,
                "Crate ranges changed: " + DroneCrateItem.range(1) + "/" + DroneCrateItem.range(2) + "/" + DroneCrateItem.range(3));
        h.assertTrue(DroneCrateItem.cost(100) == 7_000 && DroneCrateItem.cost(DroneCrateItem.range(3)) <= DroneCrateItem.capacity(3),
                "Cost does not fit the battery");
        ItemStack packed = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.IRON_INGOT, 5)));
        h.assertTrue(DroneCrateItem.packed(packed) && packed.getMaxStackSize() == 1 && !DroneCrateItem.mayCarry(packed),
                "Packed crates must not stack or nest");
        DroneCrateItem.setContents(packed, List.of());
        h.assertTrue(ItemStack.isSameItemSameComponents(packed, new ItemStack(SinguloItems.PACKING_CRATE.get())),
                "An emptied crate does not stack with new ones");
        h.succeed();
    }

    /** テスト用のステーション（メモリ・キット・電力つき）。印刷が済むまで少し待つこと。 */
    private static DroneStationBlockEntity station(GameTestHelper h, BlockPos at, int kits) {
        h.setBlock(at, SinguloBlocks.DRONE_STATION.get());
        DroneStationBlockEntity station = h.getBlockEntity(at);
        station.setOwner(owner(h));
        station.energy().setEnergy(DroneStationBlockEntity.CAPACITY);
        station.items().setStackInSlot(DroneStationBlockEntity.SLOT_MEMORY, new ItemStack(item("drone_memory")));
        station.items().setStackInSlot(DroneStationBlockEntity.SLOT_KIT, new ItemStack(item("drone_kit"), kits));
        return station;
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void dronePortPacksCargoAndPaysByDistance(GameTestHelper h) {
        DronePortBlockEntity a = port(h, new BlockPos(1, 1, 1));
        port(h, new BlockPos(6, 1, 1));
        a.select(h.absolutePos(new BlockPos(6, 1, 1)));
        h.assertTrue(a.currentProblem() == DronePortBlockEntity.S_NO_CARGO, "Sent without cargo");
        load(a, 1, new ItemStack(Items.IRON_INGOT, 40), new ItemStack(Items.DIAMOND, 3));
        // 画面に出す理由は、中身が変わったらすぐ計り直す（古い「荷物なし」が残って発送できなくならない）
        h.assertTrue(a.currentProblem() == DronePortBlockEntity.S_NO_DRONE && !a.send(), "Stale reason or sent without any drone");
        station(h, new BlockPos(4, 1, 5), 1);
        h.runAtTickTime(100, () -> packAndCancel(h, a));
    }

    private static void packAndCancel(GameTestHelper h, DronePortBlockEntity a) {
        int before = a.energy().getEnergyStored();
        h.assertTrue(a.send(), "Could not send: " + a.status());
        h.assertTrue(a.energy().getEnergyStored() == before - DroneCrateItem.cost(5), "Wrong energy paid");
        h.assertTrue(count(a, DronePortBlockEntity.SLOT_CARGO, DronePortBlockEntity.CARGO, Items.IRON_INGOT) == 0
                && a.items().getStackInSlot(DronePortBlockEntity.SLOT_CRATES).getCount() == 1, "Cargo or crate not taken");
        var contents = DroneCrateItem.contents(a.staged());
        h.assertTrue(contents.get(0).getCount() == 40 && contents.get(1).is(Items.DIAMOND), "Packed contents differ");
        h.assertFalse(a.send(), "Sent twice while a crate was staged");
        // 取り消すと、荷物・箱・電力が元に戻る
        h.assertTrue(a.cancel(), "Cancel failed");
        h.assertTrue(a.energy().getEnergyStored() == before && a.staged().isEmpty()
                && count(a, DronePortBlockEntity.SLOT_CARGO, DronePortBlockEntity.CARGO, Items.IRON_INGOT) == 40
                && count(a, DronePortBlockEntity.SLOT_CARGO, DronePortBlockEntity.CARGO, Items.DIAMOND) == 3
                && a.items().getStackInSlot(DronePortBlockEntity.SLOT_CRATES).getCount() == 2, "Cancel did not restore everything");
        ours(h, new BlockPos(4, 1, 5)).forEach(CargoDrone::discard);
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void dronePortRejectsOutOfRangeAndForeignTargets(GameTestHelper h) {
        DronePortBlockEntity a = port(h, new BlockPos(1, 1, 1));
        BlockPos far = h.absolutePos(new BlockPos(1, 1, 1)).offset(400, 0, 0);
        DroneNetwork net = DroneNetwork.get(h.getLevel());
        net.putPort(far, owner(h), "far");
        load(a, 1, new ItemStack(Items.COBBLESTONE, 8));
        a.select(far);
        h.assertTrue(a.problem() == DronePortBlockEntity.S_OUT_OF_RANGE && !a.send(), "Basic crate reached 400 blocks");
        a.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.BATTERY_PACKING_CRATE.get()));
        h.assertTrue(a.problem() == DronePortBlockEntity.S_NO_DRONE && !a.send(), "Battery crate could not reach 400 blocks: " + a.problem());
        BlockPos foreign = far.offset(0, 0, 3);
        net.putPort(foreign, UUID.randomUUID(), "");
        a.select(foreign);
        h.assertTrue(a.problem() == DronePortBlockEntity.S_NO_DESTINATION, "Sent to another owner's port");
        net.removePort(far);
        net.removePort(foreign);
        h.succeed();
    }

    /** ステーションが印刷したドローンが、ポートからポートへ箱を運び、ステーションへ帰る。荷物は1つも増えも減りもしない。 */
    @GameTest(template = "large", batch = BATCH, timeoutTicks = 1200)
    public static void droneCarriesCrateBetweenPorts(GameTestHelper h) {
        DroneStationBlockEntity station = station(h, STATION, 2);
        DronePortBlockEntity a = port(h, FROM);
        DronePortBlockEntity b = port(h, TO);
        load(a, 1, new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.GOLD_INGOT, 7));
        a.select(h.absolutePos(TO));
        boolean[] sent = {false};
        h.succeedWhen(() -> {
            if (!sent[0]) {
                sent[0] = a.send();
                h.assertTrue(sent[0], "Waiting for the drone to be printed");
            }
            h.assertTrue(count(b, DronePortBlockEntity.SLOT_RECEIVE, DronePortBlockEntity.RECEIVE, Items.IRON_INGOT) == 64
                    && count(b, DronePortBlockEntity.SLOT_RECEIVE, DronePortBlockEntity.RECEIVE, Items.GOLD_INGOT) == 7, "Cargo not received yet: station "
                    + station.status(0) + " port " + a.status() + "/" + b.status() + " flight " + describe(h) + " drones "
                    + ours(h, STATION).stream()
                    .map(d -> d.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO))).toString()).toList());
            h.assertTrue(b.items().getStackInSlot(DronePortBlockEntity.SLOT_CRATES).is(SinguloItems.PACKING_CRATE.get()),
                    "Empty crate did not move to the crate slot");
            h.assertTrue(DroneNetwork.get(h.getLevel()).flightOf(h.absolutePos(STATION), 0) == null, "Drone has not returned");
            List<CargoDrone> drones = ours(h, STATION);
            h.assertTrue(drones.size() == 1 && drones.get(0).idle(), "Expected one idle drone, got " + drones.size());
            h.assertTrue(station.items().getStackInSlot(DroneStationBlockEntity.SLOT_KIT).getCount() == 1, "Printed more than one drone");
            drones.forEach(CargoDrone::discard);
        });
    }

    /** このテストのステーションのドローン（並んで動くほかのテストのものは数えない・消さない）。 */
    private static List<CargoDrone> ours(GameTestHelper h, BlockPos at) {
        BlockPos station = h.absolutePos(at);
        return h.getLevel().getEntitiesOfClass(CargoDrone.class, new AABB(station).inflate(60, 400, 60), d -> station.equals(d.station()));
    }

    private static String describe(GameTestHelper h) {
        DroneNetwork.Flight f = DroneNetwork.get(h.getLevel()).flightOf(h.absolutePos(STATION), 0);
        return f == null ? "none" : "stage " + f.stage + " virtual " + f.virtual + " crate " + f.crate;
    }

    /** 見えないまま行き先に着いた便は、行き先の上にドローンを出して降ろす（荷物は便の記録の側にある）。 */
    @GameTest(template = "large", batch = BATCH, timeoutTicks = 600)
    public static void virtualFlightMaterializesAndUnloads(GameTestHelper h) {
        DronePortBlockEntity b = port(h, TO);
        ServerLevel level = h.getLevel();
        DroneNetwork net = DroneNetwork.get(level);
        BlockPos station = h.absolutePos(STATION);
        DroneNetwork.Flight f = net.create(station, 0, null, h.absolutePos(FROM), h.absolutePos(TO));
        f.crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.EMERALD, 12)));
        f.stage = DroneNetwork.DELIVER;
        net.virtualize(f, Vec3.atCenterOf(h.absolutePos(FROM)), level.getGameTime() - 1000);
        h.succeedWhen(() -> {
            h.assertTrue(count(b, DronePortBlockEntity.SLOT_RECEIVE, DronePortBlockEntity.RECEIVE, Items.EMERALD) == 12, "Not delivered");
            h.assertTrue(f.stage == DroneNetwork.RETURN && f.crate.isEmpty(), "Flight not returning");
            net.remove(f);
            ours(h, STATION).forEach(CargoDrone::discard);
        });
    }

    /** 届け先のポートが壊されたら、便は発送元へ戻る。発送元もなければ、箱はその場に梱包済みのまま落ちる。 */
    @GameTest(template = "empty", batch = BATCH)
    public static void brokenDestinationReroutesOrDropsPackedCrate(GameTestHelper h) {
        port(h, new BlockPos(1, 1, 1));
        port(h, new BlockPos(6, 1, 1));
        ServerLevel level = h.getLevel();
        DroneNetwork net = DroneNetwork.get(level);
        BlockPos from = h.absolutePos(new BlockPos(1, 1, 1)), to = h.absolutePos(new BlockPos(6, 1, 1));
        DroneNetwork.Flight f = net.create(h.absolutePos(new BlockPos(3, 1, 5)), 0, null, from, to);
        f.stage = DroneNetwork.DELIVER;
        f.crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.REDSTONE, 30)));
        h.destroyBlock(new BlockPos(6, 1, 1));
        h.assertTrue(f.to.equals(from) && f.from.equals(to) && !f.crate.isEmpty(), "Not rerouted to the origin");
        // 戻す先のポートも壊す（元の届け先はもうない）: 箱は梱包済みのまま落ちる
        h.destroyBlock(new BlockPos(1, 1, 1));
        h.assertTrue(f.crate.isEmpty() && f.stage == DroneNetwork.RETURN, "Crate kept although nowhere to go");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(new BlockPos(0, 0, 0))).inflate(10),
                e -> DroneCrateItem.packed(e.getItem()));
        h.assertTrue(drops.size() == 1 && DroneCrateItem.contents(drops.get(0).getItem()).get(0).getCount() == 30,
                "Packed crate was not dropped intact");
        net.remove(f);
        drops.forEach(ItemEntity::discard);
        h.succeed();
    }

    /** 着荷の欄が受け取りきれないときは、残りを箱の中に残す（地面に落とさない）。 */
    @GameTest(template = "empty", batch = BATCH)
    public static void arrivedCrateKeepsOverflow(GameTestHelper h) {
        DronePortBlockEntity b = port(h, new BlockPos(2, 1, 2));
        for (int i = 0; i < DronePortBlockEntity.RECEIVE; i++) {
            b.items().setStackInSlot(DronePortBlockEntity.SLOT_RECEIVE + i, new ItemStack(Items.STONE, 60));
        }
        b.items().setStackInSlot(DronePortBlockEntity.SLOT_ARRIVED, DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()),
                List.of(new ItemStack(Items.STONE, 64))));
        h.runAtTickTime(20, () -> {
            ItemStack crate = b.items().getStackInSlot(DronePortBlockEntity.SLOT_ARRIVED);
            int left = DroneCrateItem.contents(crate).get(0).getCount();
            h.assertTrue(count(b, DronePortBlockEntity.SLOT_RECEIVE, DronePortBlockEntity.RECEIVE, Items.STONE) == 9 * 64 && left == 64 - 36,
                    "Overflow mishandled: left " + left);
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(10)).isEmpty(),
                    "Something was dropped");
            h.succeed();
        });
    }

    /** 自動排出: 全体スイッチを入れるまでは押し出さず、入れると受け取りの欄を隣のチェストへ移す。面が「無効」なら外から取り出せない。 */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 120)
    public static void dronePortAutoEjectsReceivedItems(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2), chestPos = at.east();
        DronePortBlockEntity port = port(h, at);
        h.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_RECEIVE, new ItemStack(Items.COPPER_INGOT, 20));
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_ARRIVED, new ItemStack(SinguloItems.PACKING_CRATE.get()));
        port.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.PACKING_CRATE.get(), 16));
        net.minecraft.world.Container chest = (net.minecraft.world.Container) h.getBlockEntity(chestPos);
        h.runAtTickTime(25, () -> {
            h.assertTrue(chest.isEmpty(), "Ejected while the master switch was off");
            port.setEjectEnabled(0, true);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(port.items().getStackInSlot(DronePortBlockEntity.SLOT_RECEIVE).isEmpty()
                    && port.items().getStackInSlot(DronePortBlockEntity.SLOT_ARRIVED).isEmpty(), "Not ejected");
            h.assertTrue(chest.countItem(Items.COPPER_INGOT) == 20 && chest.countItem(SinguloItems.PACKING_CRATE.get()) == 1,
                    "Chest contents differ");
            var facing = net.minecraft.core.Direction.NORTH;
            port.setSide(0, io.github.genichimaruo.singulo.machine.SideConfig.faceOf(facing, net.minecraft.core.Direction.DOWN),
                    io.github.genichimaruo.singulo.machine.SideConfig.NONE, false);
            port.items().setStackInSlot(DronePortBlockEntity.SLOT_RECEIVE + 1, new ItemStack(Items.STONE, 5));
            h.assertTrue(port.sidedItems(net.minecraft.core.Direction.DOWN).extractItem(DronePortBlockEntity.SLOT_RECEIVE + 1, 5, true).isEmpty(),
                    "Extracted through a disabled face");
            h.succeed();
        });
    }

    /** 空き箱専用の面: 荷物を「無効」・空き箱を「入力」にした面からは空き箱の欄にだけ入り、空き箱を「無効」にした面からは空き箱が入らない。 */
    @GameTest(template = "empty", batch = BATCH)
    public static void dronePortCrateOnlyFace(GameTestHelper h) {
        DronePortBlockEntity port = port(h, new BlockPos(2, 1, 2));
        var down = net.minecraft.core.Direction.DOWN;
        var up = net.minecraft.core.Direction.UP;
        var faceDown = io.github.genichimaruo.singulo.machine.SideConfig.faceOf(net.minecraft.core.Direction.NORTH, down);
        var faceUp = io.github.genichimaruo.singulo.machine.SideConfig.faceOf(net.minecraft.core.Direction.NORTH, up);
        port.setSide(DronePortBlockEntity.CHANNEL_ITEMS, faceDown, io.github.genichimaruo.singulo.machine.SideConfig.NONE, false);
        port.setSide(DronePortBlockEntity.CHANNEL_CRATES, faceUp, io.github.genichimaruo.singulo.machine.SideConfig.NONE, false);
        var below = port.sidedItems(down);
        h.assertTrue(below.insertItem(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.PACKING_CRATE.get(), 4), false).isEmpty(),
                "Crate-only face did not accept crates");
        h.assertTrue(below.insertItem(DronePortBlockEntity.SLOT_CARGO, new ItemStack(Items.COBBLESTONE, 8), false).getCount() == 8,
                "Crate-only face accepted cargo");
        var above = port.sidedItems(up);
        h.assertTrue(above.insertItem(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.PACKING_CRATE.get()), false).getCount() == 1,
                "Face without crate input accepted a crate");
        h.assertTrue(above.insertItem(DronePortBlockEntity.SLOT_CARGO, new ItemStack(Items.COBBLESTONE, 8), false).isEmpty(),
                "Cargo face refused cargo");
        h.assertTrue(port.items().getStackInSlot(DronePortBlockEntity.SLOT_CRATES).getCount() == 4, "Crate count differs");
        h.succeed();
    }

    /** 生産アップグレードは1枚につき1機増え、2枚で3機（3枚目は入らない）。 */
    @GameTest(template = "empty", batch = BATCH)
    public static void productionUpgradesAddOneDroneEach(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 1, 2), SinguloBlocks.DRONE_STATION.get());
        DroneStationBlockEntity station = h.getBlockEntity(new BlockPos(2, 1, 2));
        Item upgrade = item("android_production_upgrade");
        h.assertTrue(station.units() == 1, "Expected 1 drone without upgrades");
        station.items().setStackInSlot(DroneStationBlockEntity.SLOT_PRODUCTION, new ItemStack(upgrade, 1));
        h.assertTrue(station.units() == 2, "Expected 2 drones with one upgrade");
        station.items().setStackInSlot(DroneStationBlockEntity.SLOT_PRODUCTION, new ItemStack(upgrade, 2));
        h.assertTrue(station.units() == 3, "Expected 3 drones with two upgrades");
        ItemStack rest = station.items().insertItem(DroneStationBlockEntity.SLOT_PRODUCTION, new ItemStack(upgrade, 1), false);
        h.assertTrue(rest.getCount() == 1 && station.items().getStackInSlot(DroneStationBlockEntity.SLOT_PRODUCTION).getCount() == 2,
                "A third upgrade was accepted");
        h.succeed();
    }

    /** 届け終えたドローンは、ステーションへ戻らずに、待っている次のポートへそのまま向かう（同じ便の記録を使い続ける）。 */
    @GameTest(template = "large", batch = BATCH, timeoutTicks = 2400)
    public static void droneChainsToTheNextWaitingJob(GameTestHelper h) {
        station(h, STATION, 1);
        DronePortBlockEntity a = port(h, FROM);
        DronePortBlockEntity b = port(h, TO);
        BlockPos cPos = new BlockPos(7, 1, 1);
        DronePortBlockEntity c = port(h, cPos);
        load(a, 1, new ItemStack(Items.IRON_INGOT, 10));
        load(c, 1, new ItemStack(Items.LAPIS_LAZULI, 9));
        a.select(h.absolutePos(TO));
        c.select(h.absolutePos(FROM));
        DroneNetwork net = DroneNetwork.get(h.getLevel());
        long[] first = {0};
        boolean[] chained = {false, false};
        h.onEachTick(() -> {
            DroneNetwork.Flight f = net.flightOf(h.absolutePos(STATION), 0);
            if (f != null && first[0] == 0) {
                first[0] = f.id;
            }
            // 1便目を積み終えたら、2つ目のポートからも送る（ドローンは運搬中だが、手が空けば来るので送れる）
            if (!chained[1] && f != null && f.stage == DroneNetwork.DELIVER) {
                chained[1] = c.send();
            }
            if (f != null && f.stage == DroneNetwork.PICKUP && f.from.equals(h.absolutePos(cPos))) {
                chained[0] |= f.id == first[0];
            }
        });
        boolean[] sent = {false};
        h.succeedWhen(() -> {
            if (!sent[0]) {
                sent[0] = a.send();
                h.assertTrue(sent[0], "Waiting for the drone to be printed");
            }
            h.assertTrue(count(b, DronePortBlockEntity.SLOT_RECEIVE, DronePortBlockEntity.RECEIVE, Items.IRON_INGOT) == 10, "First job not done");
            h.assertTrue(count(a, DronePortBlockEntity.SLOT_RECEIVE, DronePortBlockEntity.RECEIVE, Items.LAPIS_LAZULI) == 9, "Second job not done");
            h.assertTrue(chained[0], "Drone did not go straight to the next job");
            ours(h, STATION).forEach(CargoDrone::discard);
        });
    }

    /** 解体: 待機中のドローンはステーションに降りて消え、キットが戻り、その枠は止まる。また印刷すると印刷し直す。 */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 600)
    public static void dismantledDroneReturnsItsKit(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 1, 3);
        DroneStationBlockEntity station = station(h, at, 2);
        int[] step = {0};
        h.succeedWhen(() -> {
            List<CargoDrone> drones = ours(h, at);
            if (step[0] == 0) {
                h.assertTrue(drones.size() == 1 && drones.get(0).idle(), "Not printed yet");
                h.assertTrue(station.items().getStackInSlot(DroneStationBlockEntity.SLOT_KIT).getCount() == 1, "Kit not used");
                station.press(DroneStationBlockEntity.BUTTON_DISMANTLE);
                step[0] = 1;
            }
            if (step[0] == 1) {
                h.assertTrue(drones.isEmpty() && station.status(0) == DroneStationBlockEntity.S_STOPPED, "Not dismantled yet");
                h.assertTrue(station.items().getStackInSlot(DroneStationBlockEntity.SLOT_KIT).getCount() == 2, "Kit not returned");
                station.press(DroneStationBlockEntity.BUTTON_RESUME);
                step[0] = 2;
            }
            h.assertTrue(drones.size() == 1 && station.items().getStackInSlot(DroneStationBlockEntity.SLOT_KIT).getCount() == 1,
                    "Not printed again");
            drones.forEach(CargoDrone::discard);
        });
    }

    /** 自動発送: しきい値（64個）に届くまでは送らず、届いて増えなくなったら、登録した行き先（手で選んだ行き先とは別）へ送る。 */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void dronePortAutoSendsAtThreshold(GameTestHelper h) {
        DronePortBlockEntity a = port(h, new BlockPos(1, 1, 1));
        port(h, new BlockPos(6, 1, 1));
        port(h, new BlockPos(6, 1, 6));
        station(h, new BlockPos(3, 1, 4), 1);
        a.select(h.absolutePos(new BlockPos(6, 1, 1)));
        a.registerAuto();
        a.select(h.absolutePos(new BlockPos(6, 1, 6)));
        a.cycleAuto();
        a.cycleAuto();
        h.assertTrue(DronePortBlockEntity.AUTO_THRESHOLDS[a.autoMode()] == 64, "Threshold is not 64");
        load(a, 1, new ItemStack(Items.REDSTONE, 32));
        h.runAtTickTime(160, () -> {
            h.assertTrue(a.staged().isEmpty(), "Sent below the threshold");
            a.items().setStackInSlot(DronePortBlockEntity.SLOT_CARGO + 1, new ItemStack(Items.REDSTONE, 32));
        });
        h.runAtTickTime(185, () -> h.assertTrue(a.staged().isEmpty(), "Sent before the cargo settled"));
        h.runAtTickTime(240, () -> {
            h.assertTrue(!a.staged().isEmpty() || !a.shipments().isEmpty(), "Did not auto-send");
            h.assertTrue(h.absolutePos(new BlockPos(6, 1, 1)).equals(a.stagedTo() != null ? a.stagedTo() : a.shipments().get(0).to),
                    "Sent to the manual destination instead of the registered one");
            ours(h, new BlockPos(3, 1, 4)).forEach(CargoDrone::discard);
            h.succeed();
        });
    }

    /** 警備機ドックは、出したドローンをプレイヤーが全部倒したときだけメモリを出す。 */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void guardDockDropsDroneMemoryOnlyForPlayerKills(GameTestHelper h) {
        BlockPos dockPos = new BlockPos(3, 1, 3);
        h.setBlock(dockPos, SinguloBlocks.RUIN_GUARD_DOCK.get());
        GuardDockBlockEntity dock = h.getBlockEntity(dockPos);
        dock.setMemoryChanceForTest(1);
        var player = TestScope.player(h);
        dock.launch(h.getLevel());
        List<SecurityDrone> wave = h.getLevel().getEntitiesOfClass(SecurityDrone.class, new AABB(h.absolutePos(dockPos)).inflate(4));
        h.assertTrue(wave.size() == dock.waveSize(), "Wave not launched");
        for (SecurityDrone d : wave) {
            d.hurt(h.getLevel().damageSources().playerAttack(player), 1000);
        }
        h.succeedWhen(() -> {
            List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(dockPos)).inflate(4),
                    e -> e.getItem().is(item("drone_memory")));
            h.assertTrue(drops.size() == 1, "Memory not dropped after a cleared wave");
            drops.forEach(ItemEntity::discard);
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void guardDockWithoutPlayerKillsDropsNoMemory(GameTestHelper h) {
        BlockPos dockPos = new BlockPos(3, 1, 3);
        h.setBlock(dockPos, SinguloBlocks.RUIN_GUARD_DOCK.get());
        GuardDockBlockEntity dock = h.getBlockEntity(dockPos);
        dock.setMemoryChanceForTest(1);
        TestScope.player(h);
        dock.launch(h.getLevel());
        h.getLevel().getEntitiesOfClass(SecurityDrone.class, new AABB(h.absolutePos(dockPos)).inflate(4)).forEach(SecurityDrone::discard);
        h.runAtTickTime(60, () -> {
            h.assertTrue(dock.aliveDrones() == 0, "Wave not cleared");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(dockPos)).inflate(4),
                    e -> e.getItem().is(item("drone_memory"))).isEmpty(), "Memory dropped without player kills");
            h.succeed();
        });
    }
}
