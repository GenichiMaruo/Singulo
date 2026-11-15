package io.github.genichimaruo.singulo.compat;
public final class Particles {
    private Particles() {}
    public static net.minecraft.core.particles.DustParticleOptions dust(org.joml.Vector3fc color,float scale) {
        return new net.minecraft.core.particles.DustParticleOptions(net.minecraft.util.ARGB.colorFromFloat(1, color.x(), color.y(), color.z()), scale);
    }
    public static net.minecraft.core.particles.DustParticleOptions dust(int color,float scale) {
        return new net.minecraft.core.particles.DustParticleOptions(color, scale);
    }
}
