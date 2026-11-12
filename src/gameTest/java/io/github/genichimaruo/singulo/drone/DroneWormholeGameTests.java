package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class DroneWormholeGameTests {
    @GameTest(template = "empty")
    public static void visitingDroneDoesNotReplaceLocalFlightAfterReload(GameTestHelper h) {
        var net = new DroneNetwork();
        BlockPos station = new BlockPos(2, 1, 2), gate = new BlockPos(5, 1, 5);
        var local = net.create(station, 0, null, station, gate);
        var visitor = net.adopt(local);
        visitor.homeDim = Level.NETHER;
        visitor.homeGate = gate;
        visitor.homeFrom = station;
        visitor.from = gate;
        visitor.to = station;
        visitor.crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.DIAMOND, 7)));
        h.assertTrue(net.flightOf(station, 0) == local, "Visiting drone replaced the local station's flight");
        var restored = DroneNetwork.load(net.save(new CompoundTag()));
        h.assertTrue(restored.flightOf(station, 0).id == local.id, "Reload assigned a visitor to a local station");
        var savedVisitor = restored.visitor(Level.NETHER, station, 0);
        h.assertTrue(savedVisitor != null && station.equals(savedVisitor.homeFrom)
                        && DroneCrateItem.contents(savedVisitor.crate).get(0).getCount() == 7,
                "Reload lost the visitor or its cargo");
        restored.remove(restored.flight(local.id));
        h.assertTrue(restored.flightOf(station, 0) == null && restored.flight(visitor.id) == savedVisitor,
                "Visitor was offered as the next local flight");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void localStationCannotClaimOrDetachVisitingDrone(GameTestHelper h) {
        BlockPos relative = new BlockPos(2, 1, 2), stationPos = h.absolutePos(relative);
        h.setBlock(relative, SinguloBlocks.DRONE_STATION.get());
        DroneStationBlockEntity station = (DroneStationBlockEntity) h.getBlockEntity(relative);
        var net = DroneNetwork.get(h.getLevel());
        var visitor = net.create(stationPos, 0, null, stationPos, stationPos);
        TestScope.of(h).defer(() -> net.remove(visitor));
        visitor.homeDim = Level.NETHER;
        visitor.homeGate = h.absolutePos(new BlockPos(5, 1, 5));
        visitor.stage = DroneNetwork.RETURN;
        visitor.virtual = true;
        visitor.arriveAt = h.getLevel().getGameTime() - 1;
        DroneStationBlockEntity.serverTick(h.getLevel(), stationPos, station.getBlockState(), station);
        h.assertTrue(net.flight(visitor.id) == visitor && visitor.virtual,
                "A local station consumed a returning visitor and spawned its own drone");
        visitor.stage = DroneNetwork.DELIVER;
        visitor.crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.DIAMOND, 7)));
        station.onBroken(h.getLevel());
        h.assertTrue(stationPos.equals(visitor.station) && net.flight(visitor.id) == visitor,
                "Breaking a local station detached a foreign station's delivery");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void missingRemoteEndpointsReturnVirtualCargoHome(GameTestHelper h) {
        var level = h.getLevel();
        var net = DroneNetwork.get(level);
        var home = DroneNetwork.get(level.getServer().getLevel(Level.NETHER));
        BlockPos station = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos source = h.absolutePos(new BlockPos(2, 1, 2));
        BlockPos missing = h.absolutePos(new BlockPos(3, 1, 3));
        BlockPos gate = h.absolutePos(new BlockPos(4, 1, 4));
        home.markAway(station, 0, level.dimension());
        var visitor = net.create(station, 0, null, gate, missing);
        visitor.homeDim = Level.NETHER;
        visitor.homeGate = gate;
        visitor.homeFrom = source;
        visitor.stage = DroneNetwork.DELIVER;
        visitor.virtual = true;
        visitor.arriveAt = level.getGameTime() - 1;
        visitor.crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.DIAMOND, 7)));
        ItemStack cargo = visitor.crate.copy();
        TestScope.of(h).defer(() -> {
            net.remove(visitor);
            home.clearAway(station, 0);
            var returned = home.flightOf(station, 0);
            if (returned != null) home.remove(returned);
        });
        h.assertTrue(level.shouldTickBlocksAt(missing), "Missing endpoint fixture is not ticking");
        DroneWormhole.watch(level, visitor);
        h.assertTrue(visitor.stage == DroneNetwork.RETURN && gate.equals(visitor.target())
                        && ItemStack.matches(cargo, visitor.crate),
                "Missing destination did not redirect the intact cargo to the return mouth");
        // The return mouth has also disappeared; no block entity will tick either endpoint.
        visitor.arriveAt = level.getGameTime() - 1;
        DroneWormhole.watch(level, visitor);
        var returned = home.flightOf(station, 0);
        h.assertTrue(net.flight(visitor.id) == null && !home.isAway(station, 0), "Remote flight or away marker was stranded");
        h.assertTrue(returned != null && returned.stage == DroneNetwork.DELIVER && source.equals(returned.target())
                        && ItemStack.matches(cargo, returned.crate),
                "Missing mouth did not preserve the cargo in a delivery to its source port");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingRemotePortReturnsCargoWithoutDroppingIt(GameTestHelper h) {
        BlockPos relative = new BlockPos(3, 1, 3), target = h.absolutePos(relative);
        h.setBlock(relative, SinguloBlocks.DRONE_PORT.get());
        DronePortBlockEntity port = (DronePortBlockEntity) h.getBlockEntity(relative);
        var net = DroneNetwork.get(h.getLevel());
        BlockPos gate = h.absolutePos(new BlockPos(5, 1, 5));
        var visitor = net.create(h.absolutePos(new BlockPos(1, 1, 1)), 0, null, gate, target);
        TestScope.of(h).defer(() -> net.remove(visitor));
        visitor.homeDim = Level.NETHER;
        visitor.homeGate = gate;
        visitor.homeFrom = h.absolutePos(new BlockPos(2, 1, 2));
        visitor.stage = DroneNetwork.DELIVER;
        visitor.crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.DIAMOND, 7)));
        ItemStack cargo = visitor.crate.copy();
        visitor.station = null; // The origin station was broken before the drone crossed dimensions.
        port.onBroken(h.getLevel());
        h.assertTrue(net.flight(visitor.id) == visitor && visitor.stage == DroneNetwork.RETURN
                        && gate.equals(visitor.target()) && ItemStack.matches(cargo, visitor.crate),
                "Breaking the remote port dropped or lost its inbound cargo");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void returningThroughMouthDeliversFailedCargoToSource(GameTestHelper h) {
        var level = h.getLevel();
        var homeLevel = level.getServer().getLevel(Level.NETHER);
        var net = DroneNetwork.get(level);
        var home = DroneNetwork.get(homeLevel);
        var scope = TestScope.of(h);
        BlockPos gate = h.absolutePos(new BlockPos(3, 1, 3));
        BlockPos homeGate = gate.atY(200), homeBeacon = homeGate.east();
        homeLevel.getChunkAt(homeGate);
        homeLevel.getChunkAt(homeBeacon);
        var beforeGate = homeLevel.getBlockState(homeGate);
        var beforeBeacon = homeLevel.getBlockState(homeBeacon);
        scope.defer(() -> {
            homeLevel.setBlockAndUpdate(homeGate, beforeGate);
            homeLevel.setBlockAndUpdate(homeBeacon, beforeBeacon);
        });
        h.setBlock(new BlockPos(3, 1, 3), SinguloBlocks.WORMHOLE_MOUTH.get());
        h.setBlock(new BlockPos(4, 1, 3), SinguloBlocks.WORMHOLE_DRONE_BEACON.get());
        homeLevel.setBlockAndUpdate(homeGate, SinguloBlocks.WORMHOLE_MOUTH.get().defaultBlockState());
        homeLevel.setBlockAndUpdate(homeBeacon, SinguloBlocks.WORMHOLE_DRONE_BEACON.get().defaultBlockState());
        var here = (io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity) level.getBlockEntity(gate);
        var there = (io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity) homeLevel.getBlockEntity(homeGate);
        long pair = level.random.nextLong();
        here.setData(new io.github.genichimaruo.singulo.wormhole.WormholeData(pair, 0));
        there.setData(new io.github.genichimaruo.singulo.wormhole.WormholeData(pair, 0));
        io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.serverTick(level, gate, here.getBlockState(), here);
        io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity.serverTick(homeLevel, homeGate, there.getBlockState(), there);
        BlockPos station = homeGate.west(), source = homeGate.south();
        var visitor = net.create(station, 0, null, gate, gate);
        visitor.homeDim = Level.NETHER;
        visitor.homeGate = gate;
        visitor.homeFrom = source;
        visitor.stage = DroneNetwork.RETURN;
        visitor.virtual = true;
        visitor.crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), List.of(new ItemStack(Items.DIAMOND, 7)));
        home.markAway(station, 0, level.dimension());
        ItemStack cargo = visitor.crate.copy();
        scope.defer(() -> {
            net.remove(visitor);
            home.clearAway(station, 0);
            var returned = home.flightOf(station, 0);
            if (returned != null) {
                home.remove(returned);
                if (returned.drone != null && homeLevel.getEntity(returned.drone) != null) homeLevel.getEntity(returned.drone).discard();
            }
        });
        h.assertTrue(DroneWormhole.cross(level, net, visitor, null), "Return crossing did not use the paired mouths");
        var returned = home.flightOf(station, 0);
        h.assertTrue(net.flight(visitor.id) == null && !home.isAway(station, 0), "Return crossing left a foreign record or away marker");
        h.assertTrue(returned != null && !returned.away() && returned.stage == DroneNetwork.DELIVER
                        && source.equals(returned.target()) && ItemStack.matches(cargo, returned.crate),
                "Return crossing retired the drone without delivering its intact cargo back to the source");
        h.succeed();
    }
}
