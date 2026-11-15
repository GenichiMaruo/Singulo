package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.drone.FluidCapsuleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.function.Consumer;

/** Liquid interior. The shell is a regular model in the composite item definition. */
public final class FluidCapsuleRenderer implements SpecialModelRenderer<FluidCapsuleRenderer.Frame> {
    private static final float X0=3.2F/16, X1=12.8F/16, Y0=5.2F/16, Y1=10.8F/16, Z0=5.2F/16, Z1=10.8F/16;
    public record Frame(FluidLook.Look look, float top) {}
    static void registerModels(RegisterSpecialModelRendererEvent event) {
        event.register(Singulo.id("fluid_capsule"), Unbaked.CODEC);
    }
    @Override public Frame extractArgument(ItemStack stack) {
        var fluid=FluidCapsuleItem.fluid(stack);
        if(fluid.isEmpty()) return null;
        var mc=Minecraft.getInstance();
        float fill=Mth.clamp(fluid.getAmount()/(float)FluidCapsuleItem.CAPACITY,.08F,1);
        float time=mc.level==null?0:(mc.level.getGameTime()%24000+mc.getDeltaTracker().getGameTimeDeltaPartialTick(false))/20F;
        float wobble=fill<.98F?Mth.sin(time*2.4F)*.12F/16:0;
        float top=fluid.getFluidType().isLighterThanAir()?Y1:Math.min(Y1,Y0+(Y1-Y0)*fill+wobble);
        return new Frame(FluidLook.look(fluid),top);
    }
    @Override public void submit(Frame frame,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline) {
        if(frame==null)return;
        var capture=new SnapshotBlockRenderer.Capture();
        var look=frame.look();
        FluidLook.box(new PoseStack(),FluidLook.insideBuffer(capture),look,look.rgb(),look.alpha(),light,0x3F,X0,Y0,Z0,X1,frame.top(),Z1);
        SnapshotBlockRenderer.submitGeometry(capture.finish(),pose,collector);
    }
    @Override public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(X0,Y0,Z0));output.accept(new Vector3f(X1,Y1,Z1));
    }
    public record Unbaked() implements SpecialModelRenderer.Unbaked<Frame> {
        public static final MapCodec<Unbaked> CODEC=MapCodec.unit(new Unbaked());
        @Override public SpecialModelRenderer<Frame> bake(SpecialModelRenderer.BakingContext context){return new FluidCapsuleRenderer();}
        @Override public MapCodec<Unbaked> type(){return CODEC;}
    }
}
