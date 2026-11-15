package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/** Animation runs during extraction; only emitted mesh vertices cross into submission. */
abstract class ExtractedEntityModel<T extends LivingEntity> {
    public float attackTime;
    public abstract ModelPart root();
    public abstract void setupAnim(T entity, float walkPosition, float walkSpeed, float age, float headYaw, float headPitch);
    public void renderToBuffer(PoseStack pose, VertexConsumer out, int light, int overlay) { root().render(pose,out,light,overlay); }
    public void renderToBuffer(PoseStack pose, VertexConsumer out, int light, int overlay, int color) { root().render(pose,out,light,overlay,color); }
}