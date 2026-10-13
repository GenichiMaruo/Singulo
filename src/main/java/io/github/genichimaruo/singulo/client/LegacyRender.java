package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/** ARGB model colors on the Java 17 / 1.20.1 rendering API. */
final class LegacyRender {
    private LegacyRender() {}
    static void model(Model model, PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        model.renderToBuffer(pose, vertices, light, overlay, ((color >>> 16) & 255) / 255F,
                ((color >>> 8) & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
    }
    static void part(ModelPart part, PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        part.render(pose, vertices, light, overlay, ((color >>> 16) & 255) / 255F,
                ((color >>> 8) & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
    }
}
