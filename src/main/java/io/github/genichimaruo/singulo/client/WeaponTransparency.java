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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.GlStateBackup;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
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
        var state = new GlStateBackup();
        GlStateManager._backupGlState(state);
        int x = GlStateManager.Viewport.x(), y = GlStateManager.Viewport.y();
        int width = GlStateManager.Viewport.width(), height = GlStateManager.Viewport.height();
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
            RenderSystem.viewport(x, y, width, height);
            GlStateManager._restoreGlState(state);
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
        var state = new GlStateBackup();
        GlStateManager._backupGlState(state);
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
                var triangle = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
                triangle.addVertex(-1, -1, 0);
                triangle.addVertex(3, -1, 0);
                triangle.addVertex(-1, 3, 0);
                BufferUploader.draw(triangle.buildOrThrow());
            } finally {
                shader.clear();
                VertexBuffer.unbind();
            }
        } finally {
            GlStateManager._restoreGlState(state);
            main.bindWrite(false);
        }
    }
}
