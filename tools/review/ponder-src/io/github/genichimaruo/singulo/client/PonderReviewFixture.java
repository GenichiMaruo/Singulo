package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlock;
import io.github.genichimaruo.singulo.ruin.lab.RayleighSky;
import io.github.genichimaruo.singulo.ruin.lab.RayleighLightListener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

/** Place real sunlight at the coordinates borrowed by Ponder, beneath an opaque roof. */
final class PonderReviewFixture {
    private static boolean requested;
    private static volatile boolean ready;
    static final BlockPos SAMPLE = new BlockPos(3, 2, 3);
    static boolean prepare(Minecraft mc) {
        if (ready) {
            var listener = mc.level.getLightEngine().getLayerListener(LightLayer.SKY);
            return RayleighSky.light(mc.level, SAMPLE) == 15
                    && listener instanceof RayleighLightListener light && light.nativeLight().getLightValue(SAMPLE) == 0;
        }
        if (!requested) {
            requested = true;
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
                level.setDayTime(6000); level.setWeatherParameters(10000, 0, false, false);
                level.setRainLevel(0); level.setThunderLevel(0); level.updateSkyBrightness();
                for (int x = -1; x <= 9; x++) for (int z = -1; z <= 9; z++) for (int y = -1; y <= 9; y++) {
                    boolean wall = x == -1 || x == 9 || z == -1 || z == 9;
                    var state = y == -1 || y == 9 || wall ? Blocks.STONE.defaultBlockState()
                            : y == 8 ? SinguloBlocks.LAB_RAYLEIGH_PANEL.get().defaultBlockState().setValue(RayleighPanelBlock.LIT, true)
                            : Blocks.AIR.defaultBlockState();
                    level.setBlockAndUpdate(new BlockPos(x, y, z), state);
                }
                var player = level.getServer().getPlayerList().getPlayers().get(0);
                player.teleportTo(3.5, 11, 6.3); player.setNoGravity(true);
                ready = true;
            });
        }
        return false;
    }
    static void verify(Minecraft mc) {
        if (RayleighSky.light(mc.level, SAMPLE) != 15) throw new AssertionError("Real-world sunlight fixture is not active");
        var listener = mc.level.getLightEngine().getLayerListener(LightLayer.SKY);
        if (!(listener instanceof RayleighLightListener light) || light.nativeLight().getLightValue(SAMPLE) != 0)
            throw new AssertionError("Sunlight fixture is not isolated from natural sky");
    }
}
