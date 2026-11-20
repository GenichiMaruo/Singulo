package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.genichimaruo.singulo.Singulo;
import java.io.IOException;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Fast/Fancy equivalent of Fabulous' depth-sorted weapon and translucent terrain layers. */
final class WeaponTransparency {
    private static final float[] TRANSPARENT = {0, 0, 0, 0};
    private static ShaderInstance shader;
    private static TextureTarget weapons;
    private static TextureTarget terrain;

    private WeaponTransparency() {}

    static void registerShaders(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Singulo.id("weapon_transparency"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (IOException e) {
            Singulo.LOGGER.error("Could not load the weapon transparency shader", e);
        }
    }

    static boolean available() {
        return shader != null;
    }

    static void clear() {
        if (weapons != null) weapons.destroyBuffers();
        if (terrain != null) terrain.destroyBuffers();
        weapons = null;
        terrain = null;
    }

    static void begin(RenderTarget main, Runnable drawWeapons) {
        var state = new LegacyGlState();
        try {
            if (weapons == null || terrain == null || weapons.width != main.width || weapons.height != main.height
                    || weapons.isStencilEnabled() != main.isStencilEnabled()) {
                clear();
                weapons = newTarget(main);
                terrain = newTarget(main);
            }
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            prepare(terrain, main);
            prepare(weapons, main);
        } finally {
            state.restore();
            main.bindWrite(false);
        }
        weapons.bindWrite(false);
        try {
            drawWeapons.run();
        } finally {
            main.bindWrite(false);
        }
        terrain.bindWrite(false);
    }

    private static TextureTarget newTarget(RenderTarget main) {
        var target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        if (main.isStencilEnabled()) target.enableStencil();
        return target;
    }

    private static void prepare(RenderTarget target, RenderTarget main) {
        target.bindWrite(false);
        // Clearing just the color attachment preserves the world's clear color.
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, TRANSPARENT);
        copyDepth(main, target);
    }

    private static void copyDepth(RenderTarget from, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        int mask = GL11.GL_DEPTH_BUFFER_BIT | (from.isStencilEnabled() ? GL11.GL_STENCIL_BUFFER_BIT : 0);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, mask, GL11.GL_NEAREST);
    }

    static void finish(RenderTarget main) {
        var state = new LegacyGlState();
        try {
            // Subsequent particles, tripwire and outlines use the vanilla terrain depth.
            RenderSystem.disableScissor();
            copyDepth(terrain, main);
            main.bindWrite(false);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            GlStateManager._stencilMask(0);
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            shader.setSampler("WeaponColor", weapons.getColorTextureId());
            shader.setSampler("WeaponDepth", weapons.getDepthTextureId());
            shader.setSampler("TerrainColor", terrain.getColorTextureId());
            shader.setSampler("TerrainDepth", terrain.getDepthTextureId());
            shader.apply();
            try {
                var triangle = Tesselator.getInstance().getBuilder();
                triangle.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
                triangle.vertex(-1, -1, 0).endVertex();
                triangle.vertex(3, -1, 0).endVertex();
                triangle.vertex(-1, 3, 0).endVertex();
                BufferUploader.draw(triangle.end());
            } finally {
                shader.clear();
                VertexBuffer.unbind();
            }
        } finally {
            state.restore();
            main.bindWrite(false);
        }
    }
    /** Preserve only the legacy GL state modified by the private framebuffer/composite pass. */
    private static final class LegacyGlState {
        private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final int stencil = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int[] viewport = new int[4];
        private final boolean[] color = new boolean[4];

        LegacyGlState() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            try (var memory = MemoryStack.stackPush()) {
                var mask = memory.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
                for (int i = 0; i < color.length; i++) color[i] = mask.get(i) != 0;
            }
        }

        void restore() {
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (scissor) GlStateManager._enableScissorTest(); else RenderSystem.disableScissor();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthWrite);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GlStateManager._stencilMask(stencil);
            RenderSystem.colorMask(color[0], color[1], color[2], color[3]);
        }
    }
}
