package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** Tracks the actual submitted first-person model pose, including swing, bob and equip transforms. */
public final class StaffTipTracker {
    private static final java.util.Map<String, float[]> TIPS = java.util.Map.of(
            "graviton_manipulator", new float[]{13F / 16F, 3F / 16F},
            "micro_black_hole", new float[]{0.5F, 4F / 16F},
            "black_hole_bomb", new float[]{0.5F, 3F / 16F});
    private static final float[] NDC_X = new float[2], NDC_Y = new float[2], RADIUS_UV = new float[2];
    private static final long[] FRAME = {-10, -10};
    private static long currentFrame;
    private static final Matrix4f HAND_PROJECTION = new Matrix4f();
    private static boolean handProjectionValid;
    private static Context context;
    private static final class Context {
        final Context previous;
        final float[] tip;
        final boolean left;
        boolean recorded;
        Context(Context previous, float[] tip, boolean left) { this.previous=previous; this.tip=tip; this.left=left; }
    }
    private StaffTipTracker() {}

    static boolean distorts(ItemStack stack) { return tip(stack) != null; }
    private static float[] tip(ItemStack stack) {
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Singulo.MODID.equals(id.getNamespace()) ? TIPS.get(id.getPath()) : null;
    }
    static void nextFrame() { currentFrame++; }
    static float[] recent(boolean rightArm) {
        int i=rightArm ? 0 : 1;
        return currentFrame-FRAME[i] <= 2 ? new float[]{NDC_X[i],NDC_Y[i],RADIUS_UV[i]} : null;
    }

    /** Called while the real hand projection is current; no GPU-buffer readback is required. */
    public static void beginHands(Matrix4fc projection) {
        HAND_PROJECTION.set(projection);
        handProjectionValid=true;
    }
    public static void endHands() { handProjectionValid=false; context=null; }
    public static void beginItem(ItemStack stack, ItemDisplayContext displayContext) {
        context=new Context(context, displayContext.firstPerson() ? tip(stack) : null, displayContext.leftHand());
    }
    public static void endItem() { if(context != null) context=context.previous; }

    /** Layer pose already includes item display and local transforms, including the -0.5 model origin. */
    public static void recordLayer(PoseStack pose) {
        Context cxt=context;
        if(!handProjectionValid || cxt==null || cxt.tip==null || cxt.recorded) return;
        cxt.recorded=true;
        Matrix4f modelView=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        Vector4f center=modelView.transform(new Vector4f(0.5F,cxt.tip[0],0.5F,1));
        Vector4f edge=modelView.transform(new Vector4f(0.5F+cxt.tip[1],cxt.tip[0],0.5F,1));
        float radius=(float)Math.sqrt((edge.x-center.x)*(edge.x-center.x)+(edge.y-center.y)*(edge.y-center.y)+(edge.z-center.z)*(edge.z-center.z));
        Vector4f clip=HAND_PROJECTION.transform(new Vector4f(center));
        if(clip.w <= 0.01F) return;
        int i=cxt.left ? 1 : 0;
        NDC_X[i]=clip.x/clip.w;
        NDC_Y[i]=clip.y/clip.w;
        RADIUS_UV[i]=HAND_PROJECTION.m11()*radius/clip.w*0.5F;
        FRAME[i]=currentFrame;
    }
}
