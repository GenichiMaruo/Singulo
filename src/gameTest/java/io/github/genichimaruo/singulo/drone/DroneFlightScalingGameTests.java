package io.github.genichimaruo.singulo.drone;

import io.github.genichimaruo.singulo.Singulo;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class DroneFlightScalingGameTests {
    @GameTest(template = "empty")
    public static void remoteWatchFindsEachFlightByItsHomeSlot(GameTestHelper h) {
        int[] sizes = Boolean.getBoolean("singulo.testBenchmarks") ? new int[]{128, 512, 2048, 4096} : new int[]{128};
        for (int size : sizes) {
            var net = new DroneNetwork();
            var flights = new DroneNetwork.Flight[size];
            for (int i = 0; i < size; i++) {
                var pos = new BlockPos(i / DroneStationBlockEntity.MAX_UNITS, 80, 12);
                var f = net.create(pos, i % DroneStationBlockEntity.MAX_UNITS, null, BlockPos.ZERO, pos);
                f.homeDim = Level.NETHER;
                flights[i] = f;
            }
            if (Boolean.getBoolean("singulo.testBenchmarks")) {
                long[] nanos = new long[11];
                for (int sample = -4; sample < nanos.length; sample++) {
                    long start = System.nanoTime();
                    int found = 0;
                    for (int repeat = 0; repeat < 4; repeat++) found += sweep(net, flights);
                    long elapsed = System.nanoTime() - start;
                    h.assertTrue(found == 4 * size, "Incorrect remote flight during fleet lookup");
                    if (sample >= 0) nanos[sample] = elapsed / 4;
                }
                Arrays.sort(nanos);
                Singulo.LOGGER.info("Remote watch: {} flights, median {} ms/sweep", size, nanos[5] / 1e6);
            } else {
                h.assertTrue(sweep(net, flights) == size, "Incorrect remote flight during fleet lookup");
            }
            var original = flights[0];
            var replacement = net.create(original.station, original.unit, null, BlockPos.ZERO, BlockPos.ZERO);
            h.assertTrue(net.flightOf(original.station, original.unit) == replacement, "Local flight hidden by visitor");
            var station = original.station;
            original.station = null;
            net.remove(original);
            h.assertTrue(net.visitor(Level.NETHER, station, original.unit) == null
                    && net.flightOf(station, original.unit) == replacement, "Detached flight displaced a local slot");
        }
        h.succeed();
    }

    private static int sweep(DroneNetwork net, DroneNetwork.Flight[] flights) {
        int found = 0;
        for (var f : flights) {
            if (net.visitor(Level.NETHER, f.station, f.unit) == f) found++;
        }
        return found;
    }
}
