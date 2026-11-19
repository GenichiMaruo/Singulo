package io.github.genichimaruo.singulo.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

public final class FluidPumpParticles implements IClientBlockExtensions {
    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine engine) {
        if (!(level instanceof ClientLevel client)) return true;
        var boxes = state.getShape(level, pos).toAabbs();
        // Use vanilla's quarter-block spacing once across the whole pump.
        for (double x = 0.125; x < 1; x += 0.25) {
            for (double y = 0.125; y < 1; y += 0.25) {
                for (double z = 0.125; z < 1; z += 0.25) {
                    for (AABB box : boxes) {
                        if (box.contains(x, y, z)) {
                            engine.add(new TerrainParticle(client, pos.getX() + x, pos.getY() + y, pos.getZ() + z,
                                    x - 0.5, y - 0.5, z - 0.5, state, pos).updateSprite(state, pos));
                            break;
                        }
                    }
                }
            }
        }
        return true;
    }
}
