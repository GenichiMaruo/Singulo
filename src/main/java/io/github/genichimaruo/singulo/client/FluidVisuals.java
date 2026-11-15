package io.github.genichimaruo.singulo.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.neoforge.fluids.FluidStack;
final class FluidVisuals {
    private FluidVisuals(){}
    static net.minecraft.client.renderer.block.FluidModel model(FluidStack fluid){return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());}
    static TextureAtlasSprite sprite(FluidStack fluid){return model(fluid).stillMaterial().sprite();}
    static int tint(FluidStack fluid){var tint=model(fluid).fluidTintSource();return tint==null?-1:tint.colorAsStack(fluid);}
}