package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** Immutable block-model extraction for moving rubble and miniature previews. */
final class BlockDraw {
    private static final BlockDraw INSTANCE=new BlockDraw();
    private static final BlockDisplayContext CONTEXT=BlockDisplayContext.create();
    static BlockDraw get(){return INSTANCE;}
    void renderSingleBlock(BlockState block,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var state=new BlockModelRenderState();
        Minecraft.getInstance().getBlockModelResolver().update(state,block,CONTEXT);
        var transform=new Matrix4f(pose.last().pose());
        ModelSubmissions.add((outer,collector)->{
            outer.pushPose();outer.mulPose(transform);
            state.submitMultiLayer(outer,collector,light,overlay,0);
            outer.popPose();
        });
    }
}
