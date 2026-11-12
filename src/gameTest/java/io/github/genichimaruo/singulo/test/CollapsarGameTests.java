package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import io.github.genichimaruo.singulo.drone.DroneNetwork;
import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;
import io.github.genichimaruo.singulo.drone.DroneWormhole;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import io.github.genichimaruo.singulo.multiblock.CollapsarLining;
import io.github.genichimaruo.singulo.multiblock.Shapes;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.storage.HorizonVaultBlockEntity;
import io.github.genichimaruo.singulo.wormhole.WormholeData;
import io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 0.17.0: コラプサー材の使い道（内張り・事象の地平線倉庫・ワームホール便）。 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class CollapsarGameTests {
    // ------------------------------------------------------------------ コラプサー内張り

    @GameTest(template = "empty")
    public static void liningOnlyFitsProcessingMultiblocks(GameTestHelper h) {
        for (Blueprints.Kind kind : new Blueprints.Kind[]{Blueprints.Kind.COOLING_TOWER, Blueprints.Kind.DEGENERATE_COMPACTOR, Blueprints.Kind.CASIMIR_CAVITY}) {
            h.assertTrue(Shapes.acceptsLining(kind), kind + " should accept lining");
        }
        for (Blueprints.Kind kind : new Blueprints.Kind[]{Blueprints.Kind.TIPLER_CYLINDER, Blueprints.Kind.SHIELD_TOWER, Blueprints.Kind.WORMHOLE_GENERATOR,
                Blueprints.Kind.DEGENERATE_FURNACE, Blueprints.Kind.JET_FORGE, Blueprints.Kind.SUSPENSION_RESERVOIR}) {
            h.assertFalse(Shapes.acceptsLining(kind), kind + " should not accept lining");
        }
        h.assertTrue(CollapsarLining.factor(0) == 1.0 && Math.abs(CollapsarLining.factor(10) - 1.5) < 1e-9
                && CollapsarLining.factor(40) == 2.0, "Lining factor changed");
        // 投影と報告: 外装板の位置のポート・内張りは正しいブロック（完成した形が「違うブロック」にならない）
        var casing = SinguloBlocks.COOLING_TOWER_CASING.get().defaultBlockState();
        h.assertTrue(Blueprints.matches(casing, SinguloBlocks.MULTIBLOCK_PORT.get().defaultBlockState(), true, false), "Port on casing rejected");
        h.assertTrue(Blueprints.matches(casing, SinguloBlocks.COLLAPSAR_LINING.get().defaultBlockState(), true, true), "Lining on casing rejected");
        h.assertFalse(Blueprints.matches(casing, SinguloBlocks.COLLAPSAR_LINING.get().defaultBlockState(), true, false),
                "Lining accepted where lining is not allowed");
        h.assertFalse(Blueprints.matches(casing, SinguloBlocks.MULTIBLOCK_PORT.get().defaultBlockState(), false, true), "Port accepted off casing");
        h.succeed();
    }

    /** 冷却塔の外装板3か所を内張りに替えても形は保たれ、制御装置が3枚と数える。置ける位置は外装板の位置だけ。 */
    @GameTest(template = "tall", timeoutTicks = 200)
    public static void liningKeepsTheTowerFormedAndIsCounted(GameTestHelper h) {
        BlockPos c = new BlockPos(2, 1, 2);
        TestBuild.build(h, Blueprints.Kind.COOLING_TOWER, c, Direction.SOUTH, 7);
        MachineBlockEntity tower = h.getBlockEntity(c);
        List<BlockPos> lined = new ArrayList<>();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(tower.structureSize() == 7, "Tower did not form"))
                .thenExecute(() -> {
                    var found = Shapes.find(Blueprints.Kind.COOLING_TOWER, h.getLevel(), h.absolutePos(c));
                    h.assertTrue(found != null, "Tower not found");
                    List<BlockPos> slots = found.spec().liningSlots(h.absolutePos(c), found.back());
                    h.assertTrue(slots.size() > 20, "Too few lining slots: " + slots.size());
                    for (BlockPos p : slots) {
                        if (lined.size() < 3 && h.getLevel().getBlockState(p).is(SinguloBlocks.COOLING_TOWER_CASING.get())) {
                            h.getLevel().setBlockAndUpdate(p, SinguloBlocks.COLLAPSAR_LINING.get().defaultBlockState());
                            lined.add(p);
                        }
                    }
                    h.assertTrue(lined.size() == 3, "Could not line three casing blocks");
                })
                .thenWaitUntil(() -> h.assertTrue(tower.structureSize() == 7 && tower.liningCount() == 3,
                        "Lined tower: size " + tower.structureSize() + " linings " + tower.liningCount()))
                .thenExecute(() -> {
                    // 内張りを外装板でも内張りでもないブロック（石）に替えると、形は崩れる
                    h.getLevel().setBlockAndUpdate(lined.get(0), Blocks.STONE.defaultBlockState());
                })
                .thenWaitUntil(() -> h.assertTrue(tower.structureSize() == 0 && tower.liningCount() == 0, "Broken tower still counted"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ 事象の地平線倉庫

    @GameTest(template = "empty")
    public static void vaultEmptyHandExtractsThroughPlayerInteraction(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2);
        h.setBlock(at, SinguloBlocks.HORIZON_VAULT.get());
        HorizonVaultBlockEntity vault = h.getBlockEntity(at);
        vault.insert(new ItemStack(Items.COBBLESTONE, 100), false);
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        try {
            BlockPos absolute = h.absolutePos(at);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            h.assertTrue(vault.count() == 36 && player.getInventory().countItem(Items.COBBLESTONE) == 64,
                    "Empty-hand interaction did not extract one stack");
            player.getInventory().selected = 1;
            player.setShiftKeyDown(true);
            player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            h.assertTrue(vault.count() == 35 && player.getInventory().countItem(Items.COBBLESTONE) == 65,
                    "Sneaking with an empty hand did not extract one item");
            player.setShiftKeyDown(false);
            player.getInventory().selected = 2;
            ItemStack held = new ItemStack(Items.COBBLESTONE, 12);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
            player.gameMode.useItemOn(player, h.getLevel(), held, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            h.assertTrue(vault.count() == 47 && player.getMainHandItem().isEmpty(), "Held-stack deposit failed");
            player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            h.assertTrue(vault.count() == 112 && player.getInventory().countItem(Items.COBBLESTONE) == 0,
                    "Second click with the now-empty hand did not deposit the rest of the inventory");
        } finally {
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vaultStoresOneItemAndRejectsTheRest(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2);
        h.setBlock(at, SinguloBlocks.HORIZON_VAULT.get());
        HorizonVaultBlockEntity vault = h.getBlockEntity(at);
        var handler = vault.items();
        ItemStack packed = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.IRON_INGOT)));
        h.assertFalse(vault.accepts(packed), "An empty vault accepted a packed crate");
        ItemStack capsule = io.github.genichimaruo.singulo.drone.FluidCapsuleItem.pack(new ItemStack(SinguloItems.FLUID_CAPSULE.get()),
                new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1_000));
        h.assertFalse(vault.accepts(capsule), "An empty vault accepted a packed capsule");
        h.assertTrue(handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).isEmpty(), "Cobblestone not inserted");
        h.assertTrue(handler.insertItem(0, new ItemStack(Items.DIRT, 5), false).getCount() == 5, "A second kind of item was accepted");
        h.assertFalse(vault.accepts(packed), "A packed crate was accepted");
        for (int i = 0; i < 100; i++) {
            handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
        }
        h.assertTrue(vault.count() == 64 * 101, "Count is " + vault.count());
        ItemStack out = handler.extractItem(0, 1000, false);
        h.assertTrue(out.is(Items.COBBLESTONE) && out.getCount() == 64 && vault.count() == 64 * 100, "Extract one stack failed");
        // 壊すと中身ごとアイテムになり、置き直すと戻る
        h.getLevel().destroyBlock(h.absolutePos(at), true);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(at)).inflate(2),
                e -> e.getItem().is(SinguloBlocks.HORIZON_VAULT.get().asItem()));
        h.assertTrue(drops.size() == 1, "Expected one vault item, got " + drops.size());
        ItemStack item = drops.get(0).getItem();
        Long count = item.get(SinguloComponents.VAULT_COUNT.get());
        h.assertTrue(count != null && count == 64 * 100, "Vault item lost its count: " + count);
        h.assertFalse(vault.accepts(item), "A filled vault could go inside a vault");
        h.assertFalse(item.canFitInsideContainerItems(), "A filled vault could go inside a container item");
        h.assertTrue(new ItemStack(SinguloBlocks.HORIZON_VAULT.get()).canFitInsideContainerItems(), "An empty vault should fit inside container items");
        drops.forEach(ItemEntity::discard);
        h.setBlock(at, SinguloBlocks.HORIZON_VAULT.get());
        HorizonVaultBlockEntity placed = h.getBlockEntity(at);
        placed.applyComponentsFromItemStack(item);
        h.assertTrue(placed.count() == 64 * 100 && placed.type().is(Items.COBBLESTONE), "Placing did not restore the contents");
        placed.insert(new ItemStack(Items.COBBLESTONE, 1), false);
        h.assertTrue(placed.count() == 64 * 100 + 1, "Single insert failed");
        // 10億個を超えてもしまえる（上限は long。上限に近いと入る分だけ受け取り、残りは返す）
        ItemStack big = item.copy();
        big.set(SinguloComponents.VAULT_COUNT.get(), 5_000_000_000L);
        placed.applyComponentsFromItemStack(big);
        h.assertTrue(placed.count() == 5_000_000_000L, "Large count lost: " + placed.count());
        big.set(SinguloComponents.VAULT_COUNT.get(), Long.MAX_VALUE - 10);
        placed.applyComponentsFromItemStack(big);
        ItemStack rest = placed.insert(new ItemStack(Items.COBBLESTONE, 64), false);
        h.assertTrue(placed.count() == Long.MAX_VALUE && rest.getCount() == 54, "Overflow handling failed: " + placed.count() + " / " + rest.getCount());
        h.succeed();
    }

    // ------------------------------------------------------------------ ワームホール便

    private static WormholeMouthBlockEntity openMouth(ServerLevel level, BlockPos pos, long pair) {
        level.setBlockAndUpdate(pos, SinguloBlocks.WORMHOLE_MOUTH.get().defaultBlockState().setValue(AbstractMachineBlock.FACING, Direction.SOUTH));
        WormholeMouthBlockEntity be = (WormholeMouthBlockEntity) level.getBlockEntity(pos);
        be.setData(new WormholeData(pair, 0));
        var tag = be.saveWithoutMetadata(level.registryAccess());
        tag.putInt("grace", 6000);
        tag.putInt("size", 1);
        be.loadWithComponents(tag, level.registryAccess());
        return be;
    }

    /** 網の中から、station の unit の便を探す（口をくぐると番号が振り直されるため）。 */
    private static DroneNetwork.Flight flightOf(DroneNetwork net, BlockPos station) {
        for (DroneNetwork.Flight f : net.flights()) {
            if (station.equals(f.station) && f.unit == 0) {
                return f;
            }
        }
        return null;
    }

    /** くぐったあとに出てきたドローンの実体を片付ける（テストの外へ飛んでいかないように）。 */
    private static void discardDrone(ServerLevel level, DroneNetwork.Flight f) {
        if (f != null && f.drone != null && level.getEntity(f.drone) != null) {
            level.getEntity(f.drone).discard();
        }
    }

    /**
     * ネザーとのワームホールの両方の口のそばに航路標識を置くと、発着ポートの行き先にネザーのポートが並ぶ。こちらの口に着いた便は
     * 口をくぐってネザーの網の便になり（ステーションには「出ている」印）、届け終えると向こうの口をくぐって家の網の便に戻る。
     * 口を壊すと、行きの便は発送元へ戻り、帰りの口を失った便は口を通らずに家へ帰る。
     */
    @GameTest(template = "forge", timeoutTicks = 100)
    public static void wormholeDroneCrossesToAnotherDimensionAndBack(GameTestHelper h) {
        ServerLevel here = h.getLevel();
        ServerLevel nether = here.getServer().getLevel(Level.NETHER);
        UUID owner = UUID.nameUUIDFromBytes(("relay-test" + h.absolutePos(BlockPos.ZERO)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long pair = here.random.nextLong();
        BlockPos portA = new BlockPos(2, 1, 2), mouthARel = new BlockPos(10, 1, 2);
        BlockPos mouthB = h.absolutePos(new BlockPos(20, 1, 5)).atY(200), portB = mouthB.offset(6, 0, 0);
        var scope = io.github.genichimaruo.singulo.test.harness.TestScope.of(h);
        var ticket = net.minecraft.server.level.TicketType.create("singulo_relay_fixture",
                java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
        var previous = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var chunks = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for (BlockPos p : BlockPos.betweenClosed(mouthB.offset(-4, 0, -4), portB.offset(4, 0, 4))) {
            chunks.add(new net.minecraft.world.level.ChunkPos(p));
        }
        for (var chunk : chunks) {
            nether.getChunkSource().addRegionTicket(ticket, chunk, 0, chunk);
            nether.getChunk(chunk.x, chunk.z);
            scope.defer(() -> nether.getChunkSource().removeRegionTicket(ticket, chunk, 0, chunk));
        }
        scope.defer(() -> previous.forEach(nether::setBlockAndUpdate));
        BlockPos beaconB = mouthB.offset(0, 0, 2);
        for (BlockPos p : List.of(mouthB, beaconB)) {
            previous.put(p.immutable(), nether.getBlockState(p));
        }
        DroneNetwork netA = DroneNetwork.get(here), netB = DroneNetwork.get(nether);
        scope.defer(() -> {
            netA.removePort(h.absolutePos(portA));
            netB.removePort(portB);
        });
        // こちら: ポート・口・標識。向こう: 口・標識・ポート（網に載せるだけ）
        h.setBlock(portA, SinguloBlocks.DRONE_PORT.get());
        DronePortBlockEntity port = h.getBlockEntity(portA);
        port.setOwner(owner);
        netA.putPort(h.absolutePos(portA), owner, "", false);
        WormholeMouthBlockEntity a = openMouth(here, h.absolutePos(mouthARel), pair);
        h.setBlock(mouthARel.offset(0, 0, 2), SinguloBlocks.WORMHOLE_DRONE_BEACON.get());
        WormholeMouthBlockEntity b = openMouth(nether, mouthB, pair);
        nether.setBlockAndUpdate(beaconB, SinguloBlocks.WORMHOLE_DRONE_BEACON.get().defaultBlockState());
        netB.putPort(portB, owner, "far", false);
        h.runAtTickTime(5, () -> {
            WormholeMouthBlockEntity.serverTick(here, a.getBlockPos(), a.getBlockState(), a);
            WormholeMouthBlockEntity.serverTick(nether, mouthB, b.getBlockState(), b);
            h.assertTrue(a.partner() == b && b.partner() == a, "Cross-dimensional pairing failed");
            h.assertTrue(a.droneGate() && b.droneGate(), "Beacons were not detected");
            var routes = DroneWormhole.routes(here, owner, h.absolutePos(portA), false);
            h.assertTrue(routes.size() == 1 && routes.get(0).to().equals(portB) && routes.get(0).dim().equals(Level.NETHER),
                    "Route to the nether port missing: " + routes);
            int expected = (int) Math.ceil(Math.sqrt(h.absolutePos(portA).distSqr(a.getBlockPos())) + Math.sqrt(mouthB.distSqr(portB)));
            h.assertTrue(routes.get(0).distance() == expected, "Route distance " + routes.get(0).distance() + " != " + expected);
            h.assertTrue(DroneWormhole.routes(here, owner, h.absolutePos(portA), true).isEmpty(), "Item port listed for fluid sending");
            // 行き先に選ぶと、行き先は認められる（こちらにステーションがないので「呼べるドローンがいない」。向こうには要らない）
            port.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.BATTERY_PACKING_CRATE.get()));
            port.items().setStackInSlot(DronePortBlockEntity.SLOT_CARGO, new ItemStack(Items.DIAMOND, 3));
            port.energy().setEnergy(DronePortBlockEntity.CAPACITY);
            port.select(portB, Level.NETHER);
            h.assertTrue(port.problem() == DronePortBlockEntity.S_NO_DRONE, "Remote destination not accepted: " + port.problem());

            // 行き: こちらの口に着いた便は口をくぐり、ネザーの網の便として行き先へ届ける段階になる
            BlockPos station = h.absolutePos(new BlockPos(1, 1, 6));
            scope.defer(() -> {
                for (DroneNetwork net : List.of(netA, netB)) {
                    DroneNetwork.Flight left = flightOf(net, station);
                    while (left != null) {
                        net.remove(left);
                        left = flightOf(net, station);
                    }
                }
                netA.clearAway(station, 0);
            });
            ItemStack crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.DIAMOND, 3)));
            DroneNetwork.Flight out = netA.create(station, 0, owner, h.absolutePos(portA), a.getBlockPos());
            netA.advance(out, DroneNetwork.DELIVER, here.getGameTime());
            out.crate = crate.copy();
            out.relayDim = Level.NETHER;
            out.relayTo = portB;
            h.assertTrue(DroneWormhole.crossing(out) && out.target().equals(a.getBlockPos()), "Outbound flight does not head for the mouth");
            h.assertTrue(DroneWormhole.cross(here, netA, out, null), "Crossing refused");
            DroneNetwork.Flight far = flightOf(netB, station);
            h.assertTrue(netA.flight(out.id) == null && flightOf(netA, station) == null && netA.isAway(station, 0),
                    "The home network kept the flight or lost the away mark");
            h.assertTrue(far != null && far.stage == DroneNetwork.DELIVER && portB.equals(far.to) && ItemStack.matches(far.crate, crate)
                    && here.dimension().equals(far.homeDim) && mouthB.equals(far.homeGate), "Far flight not set up: " + far);
            discardDrone(nether, far);

            // 届け終えたら、帰りは向こうの口が目的地。くぐると家の網の便に戻り、ステーションへ帰る
            far.crate = ItemStack.EMPTY;
            netB.advance(far, DroneNetwork.RETURN, nether.getGameTime());
            h.assertTrue(DroneWormhole.crossing(far) && mouthB.equals(far.target()), "Return does not head for the far mouth");
            h.assertTrue(DroneWormhole.cross(nether, netB, far, null), "Crossing home refused");
            DroneNetwork.Flight back = flightOf(netA, station);
            h.assertTrue(flightOf(netB, station) == null && !netA.isAway(station, 0), "Far network kept the flight or the away mark stayed");
            h.assertTrue(back != null && back.stage == DroneNetwork.RETURN && !back.away() && station.equals(back.target()),
                    "Home flight not restored: " + back);
            discardDrone(here, back);
            netA.remove(back);

            // 口を壊す: 行きの便は発送元のポートへ戻る
            DroneNetwork.Flight going = netA.create(station, 0, owner, h.absolutePos(portA), a.getBlockPos());
            netA.advance(going, DroneNetwork.DELIVER, here.getGameTime());
            going.crate = crate.copy();
            going.relayDim = Level.NETHER;
            going.relayTo = portB;
            DroneWormhole.mouthRemoved(here, a.getBlockPos());
            h.assertTrue(h.absolutePos(portA).equals(going.to) && going.relayTo == null && ItemStack.matches(going.crate, crate),
                    "Outbound flight was not turned back to its port");
            netA.remove(going);

            // 帰りの口を壊す: 別の次元に来ていた便は、口を通らずに家のステーションへ帰る
            DroneNetwork.Flight stranded = netB.create(station, 0, owner, mouthB, portB);
            stranded.homeDim = here.dimension();
            stranded.homeGate = mouthB;
            netB.advance(stranded, DroneNetwork.RETURN, nether.getGameTime());
            netA.markAway(station, 0, Level.NETHER);
            DroneWormhole.mouthRemoved(nether, mouthB);
            DroneNetwork.Flight rescued = flightOf(netA, station);
            h.assertTrue(flightOf(netB, station) == null && !netA.isAway(station, 0) && rescued != null
                    && rescued.stage == DroneNetwork.RETURN && rescued.virtual, "Stranded drone was not sent home");
            h.succeed();
        });
    }

    /**
     * 向こうの口が読み込まれていなくても（ほかの次元はプレイヤーがいなければふつう読み込まれていない）、行き先の一覧に並び、
     * 口に着いた便がくぐろうとすると、ネザーポータルと同じく向こうの口のまわりが読み込まれる。
     */
    @GameTest(template = "forge", timeoutTicks = 400)
    public static void wormholeDroneLoadsTheUnloadedFarMouth(GameTestHelper h) {
        ServerLevel here = h.getLevel();
        ServerLevel nether = here.getServer().getLevel(Level.NETHER);
        UUID owner = UUID.nameUUIDFromBytes(("far-test" + h.absolutePos(BlockPos.ZERO)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long pair = here.random.nextLong();
        BlockPos base = h.absolutePos(BlockPos.ZERO);
        // 向こうの口は、読み込まれていない遠くの場所に「置かれている」ことにする（網に場所だけ載せる）
        BlockPos far = new BlockPos(Math.floorMod(base.getX(), 4096) + 200_000, 64, Math.floorMod(base.getZ(), 4096) + 200_000);
        BlockPos farPort = far.offset(6, 0, 0);
        var scope = io.github.genichimaruo.singulo.test.harness.TestScope.of(h);
        var wormholes = io.github.genichimaruo.singulo.wormhole.WormholeNetwork.get(here.getServer());
        wormholes.register(pair, net.minecraft.core.GlobalPos.of(Level.NETHER, far));
        DroneNetwork netA = DroneNetwork.get(here), netB = DroneNetwork.get(nether);
        netB.putPort(farPort, owner, "far", false);
        BlockPos station = h.absolutePos(new BlockPos(1, 1, 6));
        scope.defer(() -> {
            wormholes.unregister(pair, net.minecraft.core.GlobalPos.of(Level.NETHER, far));
            netB.removePort(farPort);
            DroneNetwork.Flight left = flightOf(netA, station);
            if (left != null) {
                netA.remove(left);
            }
        });
        WormholeMouthBlockEntity a = openMouth(here, h.absolutePos(new BlockPos(10, 1, 2)), pair);
        h.setBlock(new BlockPos(10, 1, 4), SinguloBlocks.WORMHOLE_DRONE_BEACON.get());
        h.runAtTickTime(5, () -> {
            WormholeMouthBlockEntity.serverTick(here, a.getBlockPos(), a.getBlockState(), a);
            h.assertFalse(nether.isLoaded(far), "Fixture expects the far mouth to be unloaded");
            h.assertTrue(a.partner() == null && a.droneGate(), "An unloaded far mouth should still accept drones");
            var routes = DroneWormhole.routes(here, owner, h.absolutePos(new BlockPos(2, 1, 2)), false);
            h.assertTrue(routes.size() == 1 && routes.get(0).to().equals(farPort) && routes.get(0).mouthThere().equals(far),
                    "Far port missing while its mouth is unloaded: " + routes);
            DroneNetwork.Flight out = netA.create(station, 0, owner, h.absolutePos(new BlockPos(2, 1, 2)), a.getBlockPos());
            netA.advance(out, DroneNetwork.DELIVER, here.getGameTime());
            out.relayDim = Level.NETHER;
            out.relayTo = farPort;
            h.assertFalse(DroneWormhole.cross(here, netA, out, null), "Crossed without the far mouth");
        });
        h.succeedWhen(() -> h.assertTrue(nether.isLoaded(far), "The far mouth was not loaded"));
    }

    /**
     * 同じ次元のワームホールでも、両方の口のそばに航路標識があり、くぐるほうが近ければドローンは近道に使う。道のり（射程と電力）も
     * 近道で数える。標識がなければ使わない。わずかな差では寄り道しない。
     */
    @GameTest(template = "forge", timeoutTicks = 60)
    public static void wormholeShortcutInTheSameDimension(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        long pair = level.random.nextLong();
        WormholeMouthBlockEntity a = openMouth(level, h.absolutePos(new BlockPos(5, 1, 5)), pair);
        WormholeMouthBlockEntity b = openMouth(level, h.absolutePos(new BlockPos(20, 1, 5)), pair);
        h.runAtTickTime(5, () -> {
            h.assertTrue(a.partner() == b && b.partner() == a, "Pair fixture not linked");
            var from = DroneWormhole.gate(a.getBlockPos()).add(0, 0, 1);
            var to = DroneWormhole.gate(b.getBlockPos()).add(0, 0, 1);
            h.assertTrue(DroneWormhole.shortcut(level, from, to) == null, "Used a wormhole without beacons");
            h.setBlock(new BlockPos(5, 1, 8), SinguloBlocks.WORMHOLE_DRONE_BEACON.get());
            h.setBlock(new BlockPos(20, 1, 8), SinguloBlocks.WORMHOLE_DRONE_BEACON.get());
        });
        h.runAtTickTime(50, () -> {                       // 標識の有無は40 tick 覚えるので、覚え直してから
            var from = DroneWormhole.gate(a.getBlockPos()).add(0, 0, 1);
            var to = DroneWormhole.gate(b.getBlockPos()).add(0, 0, 1);
            h.assertTrue(a.getBlockPos().equals(DroneWormhole.shortcut(level, from, to)), "Shorter wormhole not used");
            h.assertTrue(Math.abs(DroneWormhole.distance(level, from, to) - 2) < 1e-6, "Shortcut distance: " + DroneWormhole.distance(level, from, to));
            h.assertTrue(DroneWormhole.shortcut(level, from, from.add(3, 0, 0)) == null, "Detoured for a short hop");
            h.assertTrue(DroneWormhole.distance(level, a.getBlockPos().above(2), b.getBlockPos().above(2)) < 15, "Port distance ignores the shortcut");
            h.succeed();
        });
    }

    /** 完成済みのコントローラに投影機を使うと、ブロックの右クリック（画面を開く）より先に投影機が受け持ち、画面は開かない。 */
    @GameTest(template = "tall", timeoutTicks = 200)
    public static void projectorOnFormedControllerDoesNotOpenTheScreen(GameTestHelper h) {
        BlockPos c = new BlockPos(2, 1, 2);
        TestBuild.build(h, Blueprints.Kind.COOLING_TOWER, c, Direction.SOUTH, 7);
        MachineBlockEntity tower = h.getBlockEntity(c);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(tower.structureSize() == 7, "Tower did not form"))
                .thenExecute(() -> {
                    var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
                    ItemStack projector = new ItemStack(SinguloItems.HOLO_PROJECTOR.get());
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, projector);
                    BlockPos at = h.absolutePos(c);
                    var context = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), Direction.NORTH, at, false));
                    var result = projector.getItem().onItemUseFirst(projector, context);
                    h.assertTrue(result.consumesAction(), "Projector did not take the click first: " + result);
                    h.assertTrue(player.containerMenu == player.inventoryMenu, "A machine screen opened");
                    // 装置でないブロックは、ふつうの右クリックに任せる
                    BlockPos stone = h.absolutePos(new BlockPos(0, 1, 0));
                    h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
                    var other = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(stone), Direction.UP, stone, false));
                    h.assertTrue(projector.getItem().onItemUseFirst(projector, other) == net.minecraft.world.InteractionResult.PASS,
                            "Projector swallowed a click on a plain block");
                    player.discard();
                })
                .thenSucceed();
    }
}
