package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.drone.*;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class DroneRegressionGameTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void cargoSurvivesVirtualArrivalAfterStationBreak(GameTestHelper h) {
        var level = h.getLevel();
        var stationPos = new BlockPos(1, 1, 1);
        var targetPos = new BlockPos(3, 1, 3);
        h.setBlock(stationPos, SinguloBlocks.DRONE_STATION.get());
        h.setBlock(targetPos, SinguloBlocks.DRONE_PORT.get());
        var station = (DroneStationBlockEntity) h.getBlockEntity(stationPos);
        var net = DroneNetwork.get(level);
        var f = net.create(h.absolutePos(stationPos), 0, null, h.absolutePos(stationPos), h.absolutePos(targetPos));
        f.stage = DroneNetwork.DELIVER;
        f.crate = DroneCrateItem.pack(new ItemStack(SinguloItems.PACKING_CRATE.get()), java.util.List.of(new ItemStack(Items.IRON_INGOT, 5)));
        net.virtualize(f, Vec3.atCenterOf(h.absolutePos(targetPos)), level.getGameTime() - 1000);
        station.onBroken(level);
        h.assertTrue(f.station == null, "Station break did not detach flight");
        boolean spawned = CargoDrone.materialize(level, net, f);
        h.assertTrue(spawned, "Could not materialize fixture");
        var d = (CargoDrone) level.getEntity(f.drone);
        d.tick();
        boolean live = d.isAlive(), virtual = f.virtual;
        h.assertTrue(live || virtual, "Cargo stranded: materialized drone discarded and virtual=" + virtual);
        var port = (DronePortBlockEntity) h.getBlockEntity(targetPos);
        h.succeedWhen(() -> {
            int received = 0;
            for (int i = 0; i < DronePortBlockEntity.RECEIVE; i++) {
                var stack = port.items().getStackInSlot(DronePortBlockEntity.SLOT_RECEIVE + i);
                if (stack.is(Items.IRON_INGOT)) received += stack.getCount();
            }
            h.assertTrue(received == 5 && net.flight(f.id) == null, "Orphan delivery did not finish exactly once");
            d.discard();
        });
    }

    @GameTest(template = "empty")
    public static void distantFlightDoesNotLoadDestinationChunk(GameTestHelper h) {
        var level = h.getLevel(); var station = h.absolutePos(new BlockPos(2, 1, 2));
        var far = station.offset(1024, 0, 0);
        h.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Fixture target is loaded");
        var net = DroneNetwork.get(level); net.putPort(far, null, "Far destination");
        var d = CargoDrone.spawn(level, station, 0, DroneKind.ROTOR, Vec3.atBottomCenterOf(station.above()), false);
        var f = net.create(station, 0, null, station, far);
        f.stage = DroneNetwork.DELIVER; f.drone = d.getUUID(); f.phase = true;
        d.tick();
        boolean loaded = level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) != null;
        net.remove(f); net.removePort(far); d.discard();
        h.assertFalse(loaded, "Flight force-loaded the destination chunk 1024 blocks away");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void droneStopsWhenWallIsBuiltAcrossCachedRoute(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var from = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 2)));
        var goal = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 2, 2)));
        var d = CargoDrone.spawn(level, h.absolutePos(new BlockPos(1, 1, 1)), 0, DroneKind.ROTOR, from, false);
        var fly = CargoDrone.class.getDeclaredMethod("flyTo", net.minecraft.server.level.ServerLevel.class, Vec3.class);
        fly.setAccessible(true); fly.invoke(d, level, goal);
        for (int z = 0; z <= 4; z++) for (int y = 1; y <= 4; y++)
            h.setBlock(new BlockPos(3, y, z), net.minecraft.world.level.block.Blocks.STONE);
        boolean crossed = false;
        for (int i = 0; i < 20; i++) {
            fly.invoke(d, level, goal);
            if (!level.noCollision(d.getBoundingBox())) crossed = true;
        }
        d.discard();
        h.assertFalse(crossed, "Non-phase drone crossed a wall built after route planning");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nonOwnerCannotChangeDronePortSidesThroughPayload(GameTestHelper h) throws Exception {
        var pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, SinguloBlocks.DRONE_PORT.get());
        var port = (DronePortBlockEntity) h.getBlockEntity(pos);
        var player = io.github.genichimaruo.singulo.test.harness.TestScope.player(h);
        var at = h.absolutePos(pos); player.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        port.setOwner(new java.util.UUID(100, 200));
        h.assertFalse(port.mayOperate(player), "Fixture player is authorized");
        int before = port.sides().packed();
        var contextType = net.neoforged.neoforge.network.handling.IPayloadContext.class;
        Object context = java.lang.reflect.Proxy.newProxyInstance(contextType.getClassLoader(), new Class<?>[]{contextType}, (proxy, method, args) -> {
            if (method.getName().equals("player")) return player;
            if (method.getName().equals("enqueueWork")) { ((Runnable) args[0]).run(); return java.util.concurrent.CompletableFuture.completedFuture(null); }
            throw new AssertionError("Unexpected context call " + method);
        });
        var payloadType = io.github.genichimaruo.singulo.network.SideConfigPayload.class;
        var handle = payloadType.getDeclaredMethod("handle", payloadType, contextType); handle.setAccessible(true);
        handle.invoke(null, new io.github.genichimaruo.singulo.network.SideConfigPayload(at, 0, 0, 0), context);
        h.assertTrue(port.sides().packed() == before, "Non-owner packet changed the port side configuration");
        h.succeed();
    }
}

