package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.energy.SinguloEnergyStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class EnergyStorageGameTests {
    @GameTest(template = "empty")
    public static void highGenerationSaturatesWithoutOverflowOrExtraNotifications(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        int[] changes = {0};
        // Degenerate furnace capacity; high configured output is clamped to Integer.MAX_VALUE.
        var energy = new SinguloEnergyStorage(200_000_000, 0, 0, () -> changes[0]++);
        energy.setEnergy(100_000_000);
        energy.generate(Integer.MAX_VALUE);
        h.assertTrue(energy.getEnergyStored() == 200_000_000 && changes[0] == 1,
                "Generation overflowed instead of filling the buffer");
        energy.generate(Integer.MAX_VALUE);
        h.assertTrue(energy.getEnergyStored() == 200_000_000 && changes[0] == 1,
                "A full buffer changed or sent redundant notifications");
        energy.consume(100);
        energy.generate(40);
        h.assertTrue(energy.getEnergyStored() == 199_999_940 && changes[0] == 3,
                "Ordinary generation or change notifications changed");
        h.succeed();
    }
}
