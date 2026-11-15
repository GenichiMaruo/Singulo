package io.github.genichimaruo.singulo.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
abstract class ExtractedLayer<T extends LivingEntity,M extends ExtractedEntityModel<T>> {
    private final ExtractedLayerParent<T,M> parent;
    protected ExtractedLayer(ExtractedLayerParent<T,M> parent) { this.parent=parent; }
    protected M getParentModel() { return parent.getModel(); }
    public abstract void render(PoseStack pose,MultiBufferSource buffers,int light,T entity,float walkPosition,float walkSpeed,float partialTick,float age,float headYaw,float headPitch);
}