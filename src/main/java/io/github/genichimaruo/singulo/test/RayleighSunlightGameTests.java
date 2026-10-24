package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("singulo_rayleigh")
@PrefixGameTestTemplate(false)
public final class RayleighSunlightGameTests {
    private static final BlockPos SAMPLE = new BlockPos(2, 1, 2);
    private static void room(GameTestHelper h, int height) {
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) for (int y = 0; y <= height; y++) {
            h.setBlock(new BlockPos(x, y, z), x == 0 || x == 4 || z == 0 || z == 4 || y == 0 || y == height ? Blocks.STONE : Blocks.AIR);
        }
        h.setBlock(new BlockPos(2, height, 2), SinguloBlocks.LAB_RAYLEIGH_PANEL.get().defaultBlockState().setValue(RayleighPanelBlock.LIT, true));
    }
    private static int sunlight(GameTestHelper h, BlockPos pos) { return RayleighSky.light(h.getLevel(), h.absolutePos(pos)); }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void actualSkyAndRawLightEqualClearNoonEvenWithNightDarkening(GameTestHelper h) {
        room(h, 6);
        h.succeedWhen(() -> {
            var level = h.getLevel(); var p = h.absolutePos(SAMPLE);
            h.assertTrue(level.getBrightness(LightLayer.SKY, p) == 15, "Panel emits block light but no actual sky light");
            h.assertTrue(level.getRawBrightness(p, 11) == 15 && level.getRawBrightness(p, 0) == 15, "Night/weather darkens artificial sunlight");
            h.assertTrue(level.canSeeSky(p), "Direct panel sunlight is not recognized as sky exposure");
            h.assertTrue(level.getLightLevelDependentMagicValue(p) == 1.0F, "Gameplay brightness differs from clear-noon outdoor brightness");
            h.assertTrue(level.getBrightness(LightLayer.BLOCK, p) == 0, "Waiting for native block-light work to settle: " + level.getBrightness(LightLayer.BLOCK, p));
        });
    }

    @GameTest(template = "empty")
    public static void skylightUsesVanillaGlassWaterAndOpaqueOcclusion(GameTestHelper h) {
        room(h, 6);
        BlockPos filter = new BlockPos(2, 3, 2);
        h.setBlock(filter, Blocks.GLASS);
        h.assertTrue(sunlight(h, SAMPLE) == 15, "Clear glass blocks sunlight");
        h.setBlock(filter, Blocks.WATER);
        h.assertTrue(sunlight(h, filter) == 14 && sunlight(h, filter.below()) == 13, "Water/downward diffusion differs from vanilla skylight");
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) h.setBlock(new BlockPos(x, 3, z), Blocks.TINTED_GLASS);
        h.assertTrue(sunlight(h, SAMPLE) == 0, "Sunlight passes through an opaque filter");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void partialBlockShapesOccludeSunlightLikeVanilla(GameTestHelper h) {
        room(h, 6);
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) h.setBlock(new BlockPos(x, 3, z), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        h.assertTrue(sunlight(h, SAMPLE) == 0, "A continuous slab ceiling leaks skylight through its full face");
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) h.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
        h.assertTrue(sunlight(h, SAMPLE) == 15, "Removing shaped obstruction does not restore direct sunlight");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lightDiffusesAroundShadowsRatherThanPassingThroughBlocks(GameTestHelper h) {
        room(h, 6);
        h.assertTrue(sunlight(h, SAMPLE.east()) == 14, "Sideways sky diffusion does not attenuate by one");
        h.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        int shadow = sunlight(h, SAMPLE);
        h.assertTrue(shadow > 0 && shadow < 15, "Opaque block does not create a diffused shadow: " + shadow);
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        h.assertTrue(sunlight(h, SAMPLE) == 0, "A sealed floor leaks artificial sunlight");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void panelPowerRemovalAndReloadDiscardOldSunlight(GameTestHelper h) {
        room(h, 6);
        BlockPos panel = new BlockPos(2, 6, 2);
        h.assertTrue(sunlight(h, SAMPLE) == 15, "Missing source");
        BlockPos sample = h.absolutePos(SAMPLE);
        var rendered = RayleighSky.snapshot(h.getLevel());
        h.assertTrue(rendered.near(sample) && rendered.light(sample.getX(), sample.getY(), sample.getZ()) == 15,
                "Renderer did not capture the completed sunlight field");
        h.setBlock(panel, SinguloBlocks.LAB_RAYLEIGH_PANEL.get());
        h.assertTrue(sunlight(h, SAMPLE) == 0, "Power loss leaves stale sunlight");
        h.assertTrue(rendered.light(sample.getX(), sample.getY(), sample.getZ()) == 15,
                "A queued renderer snapshot was mutated after capture");
        h.assertTrue(RayleighSky.snapshot(h.getLevel()).light(sample.getX(), sample.getY(), sample.getZ()) == 0,
                "A new renderer snapshot retained disabled sunlight");
        h.setBlock(panel, SinguloBlocks.LAB_RAYLEIGH_PANEL.get().defaultBlockState().setValue(RayleighPanelBlock.LIT, true));
        var be = (RayleighPanelBlockEntity) h.getBlockEntity(panel);
        be.onChunkUnloaded();
        h.assertTrue(sunlight(h, SAMPLE) == 0, "Unloaded panel retains its sunlight field");
        be.onLoad();
        h.assertTrue(sunlight(h, SAMPLE) == 15, "Loaded powered panel does not restore sunlight");
        h.setBlock(panel, Blocks.STONE);
        h.assertTrue(sunlight(h, SAMPLE) == 0, "Removed panel leaves stale sunlight");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unobstructedSunlightIsNotLimitedToThirtyTwoBlocks(GameTestHelper h) {
        room(h, 40);
        try { h.assertTrue(sunlight(h, SAMPLE) == 15, "Artificial sunlight stops after 32 blocks"); }
        finally {
            // This fixture intentionally extends above its small template; remove those blocks too.
            for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) for (int y = 5; y <= 40; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void detectorUsesAttenuatedNoonLightAndCorrectInversion(GameTestHelper h) {
        room(h, 6);
        h.setBlock(SAMPLE.east(), Blocks.DAYLIGHT_DETECTOR);
        h.setBlock(SAMPLE.west(), Blocks.DAYLIGHT_DETECTOR.defaultBlockState().setValue(DaylightDetectorBlock.INVERTED, true));
        h.runAtTickTime(30, () -> {
            h.assertTrue(h.getBlockState(SAMPLE.east()).getValue(DaylightDetectorBlock.POWER) == 14, "Shadowed detector is forced to full 15");
            h.assertTrue(h.getBlockState(SAMPLE.west()).getValue(DaylightDetectorBlock.POWER) == 1, "Inverted detector ignores attenuated daylight");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void artificialSunBurnsUndeadAtNightWithoutChangingWorldTimeOrWeather(GameTestHelper h) {
        room(h, 6);
        var level = h.getLevel();
        long time = level.getDayTime();
        boolean rain = level.getLevelData().isRaining(), thunder = level.getLevelData().isThundering();
        float rainStrength = level.getRainLevel(1), thunderStrength = level.getThunderLevel(1);
        var zombie = EntityType.ZOMBIE.create(level);
        try {
            level.setDayTime(18000); level.getLevelData().setRaining(true); ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setThundering(true);
            level.setRainLevel(1); level.setThunderLevel(1); level.updateSkyBrightness();
            var p = h.absolutePos(SAMPLE);
            zombie.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, 0, 0);
            var method = Mob.class.getDeclaredMethod("isSunBurnTick"); method.setAccessible(true);
            boolean exposed = false;
            for (int i = 0; i < 4096 && !exposed; i++) exposed = (boolean) method.invoke(zombie);
            h.assertTrue(exposed, "Undead do not recognize artificial noon during a rainy night");
            h.assertFalse(level.isRainingAt(p), "Artificial sun is treated as rain exposure");
            zombie.moveTo(p.getX() + 2.5, p.getY(), p.getZ() + .5, 0, 0); // Solid wall, no source light.
            h.assertFalse((boolean) method.invoke(zombie), "Night outside the lit area is treated as day");
            h.assertTrue(level.getDayTime() == 18000 && !level.isDay() && level.isRaining() && level.isThundering(), "Local noon changes global time/weather");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        finally {
            zombie.discard(); level.setDayTime(time); level.getLevelData().setRaining(rain); ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setThundering(thunder);
            level.setRainLevel(rainStrength); level.setThunderLevel(thunderStrength); level.updateSkyBrightness();
        }
        h.succeed();
    }
}
