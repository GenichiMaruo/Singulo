package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.drone.DroneCrateItem;
import io.github.genichimaruo.singulo.drone.DroneNetwork;
import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;
import io.github.genichimaruo.singulo.drone.WormholeRelay;
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
    public static void vaultStoresOneItemAndRejectsTheRest(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2);
        h.setBlock(at, SinguloBlocks.HORIZON_VAULT.get());
        HorizonVaultBlockEntity vault = h.getBlockEntity(at);
        var handler = vault.items();
        h.assertTrue(handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).isEmpty(), "Cobblestone not inserted");
        h.assertTrue(handler.insertItem(0, new ItemStack(Items.DIRT, 5), false).getCount() == 5, "A second kind of item was accepted");
        ItemStack packed = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.IRON_INGOT)));
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

    /**
     * ネザーとのワームホールの両方の口のそばに航路標識を置くと、発着ポートの行き先にネザーのポートが並ぶ。こちらの口に着いた便は
     * 荷物を向こうの口のリレーに渡して帰り、向こうの便がリレーから受け取って届ける段階へ進む。口を壊すと、待っていた荷物は落ちる。
     */
    @GameTest(template = "forge", timeoutTicks = 100)
    public static void wormholeRelayCarriesCargoAcrossDimensions(GameTestHelper h) {
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
            var routes = WormholeRelay.routes(here, owner, h.absolutePos(portA), false);
            h.assertTrue(routes.size() == 1 && routes.get(0).to().equals(portB) && routes.get(0).dim().equals(Level.NETHER),
                    "Route to the nether port missing: " + routes);
            int expected = (int) Math.ceil(Math.sqrt(h.absolutePos(portA).distSqr(a.getBlockPos())) + Math.sqrt(mouthB.distSqr(portB)));
            h.assertTrue(routes.get(0).distance() == expected, "Route distance " + routes.get(0).distance() + " != " + expected);
            h.assertTrue(WormholeRelay.routes(here, owner, h.absolutePos(portA), true).isEmpty(), "Item port listed for fluid sending");
            // 行き先に選ぶと、行き先は認められる（ステーションがないので「呼べるドローンがいない」）
            port.items().setStackInSlot(DronePortBlockEntity.SLOT_CRATES, new ItemStack(SinguloItems.BATTERY_PACKING_CRATE.get()));
            port.items().setStackInSlot(DronePortBlockEntity.SLOT_CARGO, new ItemStack(Items.DIAMOND, 3));
            port.energy().setEnergy(DronePortBlockEntity.CAPACITY);
            port.select(portB, Level.NETHER);
            h.assertTrue(port.problem() == DronePortBlockEntity.S_NO_DRONE, "Remote destination not accepted: " + port.problem());

            // 1本目の便: こちらの口に着いたら、荷物を向こうのリレーに渡して帰る
            ItemStack crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.DIAMOND, 3)));
            DroneNetwork.Flight leg1 = netA.create(h.absolutePos(new BlockPos(1, 1, 6)), 0, owner, h.absolutePos(portA), a.getBlockPos());
            netA.advance(leg1, DroneNetwork.DELIVER, here.getGameTime());
            leg1.crate = crate.copy();
            leg1.relayDim = Level.NETHER;
            leg1.relayTo = portB;
            h.assertTrue(WormholeRelay.handOff(here, netA, leg1), "Hand-off refused");
            h.assertTrue(leg1.crate.isEmpty() && leg1.stage == DroneNetwork.RETURN, "Leg 1 kept the crate or did not return");
            List<DroneNetwork.Relay> waiting = netB.relaysAt(mouthB);
            h.assertTrue(waiting.size() == 1 && waiting.get(0).to.equals(portB) && ItemStack.matches(waiting.get(0).crate, crate),
                    "Relay not waiting at the far mouth");
            netA.remove(leg1);

            // 2本目の便: 向こうの口でリレーから受け取り、届ける段階へ
            DroneNetwork.Flight leg2 = netB.create(mouthB.offset(3, 0, 3), 0, owner, mouthB, portB);
            waiting.get(0).flight = leg2.id;
            h.assertTrue(WormholeRelay.pickUp(nether, netB, leg2), "Pick-up refused");
            h.assertTrue(ItemStack.matches(leg2.crate, crate) && leg2.stage == DroneNetwork.DELIVER && netB.relaysAt(mouthB).isEmpty(),
                    "Leg 2 did not take the crate");
            netB.remove(leg2);

            // 口を壊すと、待っていた荷物は梱包済みの箱のまま落ちる
            netB.addRelay(mouthB, crate.copy(), portB, owner);
            WormholeRelay.mouthRemoved(nether, mouthB);
            var dropped = nether.getEntitiesOfClass(ItemEntity.class, new AABB(mouthB).inflate(2), e -> DroneCrateItem.packed(e.getItem()));
            h.assertTrue(netB.relaysAt(mouthB).isEmpty() && dropped.size() == 1, "Waiting crate was not dropped: relays " + netB.relaysAt(mouthB).size() + " crates " + dropped.size() + " items " + nether.getEntitiesOfClass(ItemEntity.class, new AABB(mouthB).inflate(4)).size());
            dropped.forEach(ItemEntity::discard);
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
