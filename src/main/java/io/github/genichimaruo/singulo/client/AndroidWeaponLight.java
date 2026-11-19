package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Translucent weapon surfaces, drawn after opaque entities and block entities. */
public final class AndroidWeaponLight extends RenderType {
    private AndroidWeaponLight(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                               boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    static final RenderType BLADE = material("blade", true, false);
    static final RenderType CORE = material("core", false, false);
    static final RenderType GLOW = material("glow", false, false);
    private static final RenderType LAYER_BLADE = material("blade", true, true);
    private static final RenderType LAYER_CORE = material("core", false, true);
    private static final RenderType LAYER_GLOW = material("glow", false, true);
    private static final MultiBufferSource.BufferSource BATCH = weaponBuffers();
    private static boolean queued;
    private static boolean drawingTerrain;

    private static MultiBufferSource.BufferSource weaponBuffers() {
        var layers = new LinkedHashMap<RenderType, ByteBufferBuilder>();
        // Finish the textured surface and white core before blending the wider colored glow.
        for (RenderType type : java.util.List.of(LAYER_BLADE, LAYER_CORE, LAYER_GLOW))
            layers.put(type, new ByteBufferBuilder(1536));
        return MultiBufferSource.immediateWithBuffers(layers, new ByteBufferBuilder(256));
    }

    private static RenderType material(String name, boolean textured, boolean layered) {
        // ITEM_ENTITY_TARGET binds Minecraft's separate layer in Fabulous. In Fast/Fancy it leaves
        // the bound target alone, so the same batches can draw to our private weapon layer.
        var state = CompositeState.builder()
                .setShaderState(textured ? RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER : RENDERTYPE_LIGHTNING_SHADER)
                .setTransparencyState(textured ? TRANSLUCENT_TRANSPARENCY : LIGHTNING_TRANSPARENCY)
                .setOutputState(layered ? ITEM_ENTITY_TARGET : MAIN_TARGET)
                .setWriteMaskState(layered ? COLOR_DEPTH_WRITE : COLOR_WRITE);
        if (textured) {
            state.setTextureState(new TextureStateShard(AndroidRenderer.Variant.BLADE.texture, false, false))
                    .setOverlayState(OVERLAY).setCullState(NO_CULL);
        }
        return create("singulo_weapon_" + name + (layered ? "_layered" : ""),
                textured ? DefaultVertexFormat.NEW_ENTITY : DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1536, false, true, state.createCompositeState(false));
    }

    static MultiBufferSource worldBuffers(MultiBufferSource original) {
        if (!GlowDepth.inWorld() || AndroidRenderer.inGui || GravitationalLensing.shaderPackInUse()
                || !Minecraft.useShaderTransparency() && !WeaponTransparency.available()) return original;
        return type -> {
            RenderType layer;
            if (type == BLADE) layer = LAYER_BLADE;
            else if (type == CORE) layer = LAYER_CORE;
            else if (type == GLOW) layer = LAYER_GLOW;
            else return original.getBuffer(type);
            queued = true;
            return BATCH.getBuffer(layer);
        };
    }

    static void afterBlockEntities() {
        if (queued && Minecraft.useShaderTransparency()) {
            queued = false;
            BATCH.endBatch();
        }
    }

    public static boolean beginTerrain() {
        if (!queued || Minecraft.useShaderTransparency()) return false;
        queued = false;
        WeaponTransparency.begin(Minecraft.getInstance().getMainRenderTarget(), BATCH::endBatch);
        drawingTerrain = true;
        return true;
    }

    public static void finishTerrain() {
        if (drawingTerrain) {
            drawingTerrain = false;
            WeaponTransparency.finish(Minecraft.getInstance().getMainRenderTarget());
        }
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) finishTerrain();
    }
}
