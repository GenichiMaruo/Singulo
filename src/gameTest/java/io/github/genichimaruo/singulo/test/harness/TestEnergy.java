package io.github.genichimaruo.singulo.test.harness;
public final class TestEnergy {
 public static io.github.genichimaruo.singulo.energy.EnergyResourceView find(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.core.Direction side){return io.github.genichimaruo.singulo.energy.EnergyResourceView.of(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,pos,side));}
}