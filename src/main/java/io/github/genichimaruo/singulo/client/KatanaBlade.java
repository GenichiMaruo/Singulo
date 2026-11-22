package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.genichimaruo.singulo.android.KatanaAndroid;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Continuous strips along the curved steel. The last cross-section always ends at the scabbard mouth or the tip. */
final class KatanaBlade {
    private static final float COLLAR = 0.04F;
    private static final int[] NORMAL_X = {0, 1, 0, -1};
    private static final int[] NORMAL_Z = {-1, 0, 1, 0};
    private enum Strip { SPINE, FLAT, HAMON, CORE, GLOW }

    private KatanaBlade() {}

    static void render(PoseStack.Pose pose, MultiBufferSource buffers, VertexConsumer steel, int light, boolean crimson,
                       float exposed, float surge, float heat, float wave) {
        float length = KatanaMotion.stroke(crimson) - COLLAR;
        float end = Mth.clamp((exposed - COLLAR) / length, 0, 1);
        draw(pose, steel, light, Strip.SPINE, crimson ? 0x48434E : 0x8A92A0, length, crimson, end, 0, -1);
        draw(pose, steel, light, Strip.FLAT, crimson ? AndroidRenderer.mix(0x6E6874, 0xBD6777, surge) : 0xC9CFD8, length, crimson, end, 0, -1);
        if (!crimson) {
            draw(pose, steel, light, Strip.HAMON, 0xF2F4F8, length, false, end, 0, -1);
        }
        if (heat > 0.01F) {
            draw(pose, buffers.getBuffer(AndroidWeaponLight.CORE), light, Strip.CORE, 0xFFF2DF, length, crimson, end, heat, crimson ? wave : -1);
            draw(pose, buffers.getBuffer(AndroidWeaponLight.GLOW), light, Strip.GLOW, KatanaAndroid.ACCENT, length, crimson, end, heat, crimson ? wave : -1);
        }
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer out, int light, Strip strip, int color,
                             float length, boolean crimson, float end, float heat, float wave) {
        // Steel bands meet edge to edge. Only the outermost bands close the back and cutting edge.
        boolean drawBack = strip != Strip.FLAT && strip != Strip.HAMON;
        boolean drawEdge = strip != Strip.SPINE && (strip != Strip.FLAT || crimson);
        Section a = section(strip, 0, length, crimson, heat, wave);
        for (int i = 1; i <= 12; i++) {
            float s = Math.min(i / 12F, end);
            Section b = section(strip, s, length, crimson, heat, wave);
            // Share the ring between adjacent segments. Internal end caps would make the additive light look striped.
            for (int face = 0; face < 4; face++) {
                if ((face == 0 && !drawBack) || (face == 2 && !drawEdge)) continue;
                int next = (face + 1) % 4;
                a.vertex(pose, out, face, face, light, color);
                b.vertex(pose, out, face, face, light, color);
                b.vertex(pose, out, next, face, light, color);
                a.vertex(pose, out, next, face, light, color);
            }
            if (s >= end) break;
            a = b;
        }
    }

    private static Section section(Strip strip, float s, float length, boolean crimson, float heat, float wave) {
        float width = crimson ? 0.078F : 0.05F;
        float tip = Mth.clamp((1 - s) / 0.16F, 0, 1);
        float half = width * (1 - 0.28F * s) * tip * 0.5F;
        float curve = -KatanaMotion.curveOffset(s);
        float y = COLLAR + length * s;
        float edge = curve + half;
        float ridge = curve - half * 0.48F;
        float hamonStart = edge - 0.009F * tip;
        float crest = Math.max(0, 1 - Math.abs(s - wave) / 0.25F);
        return switch (strip) {
            case SPINE -> new Section(y, 0.009F * tip, curve - half, ridge, 255);
            case FLAT -> new Section(y, 0.009F * tip, (crimson ? 0.008F : 0.0085F) * tip, ridge, crimson ? edge : hamonStart, 255);
            case HAMON -> new Section(y, 0.0085F * tip, 0.008F * tip, hamonStart, edge, 255);
            case CORE -> new Section(y, 0.010F * tip, edge - (0.006F + 0.009F * crest) * tip, edge + 0.002F * tip,
                    (int) Math.min(255, 110 * heat + 95 * crest));
            case GLOW -> {
                float spread = (0.005F + 0.009F * heat + 0.015F * crest) * tip;
                yield new Section(y, 0.011F * tip + spread * 0.55F, edge - spread, edge + spread,
                        (int) Math.min(200, 75 * heat + 70 * crest));
            }
        };
    }

    private record Section(float y, float backThickness, float edgeThickness, float back, float edge, int alpha) {
        Section(float y, float thickness, float back, float edge, int alpha) {
            this(y, thickness, thickness, back, edge, alpha);
        }

        void vertex(PoseStack.Pose pose, VertexConsumer out, int corner, int face, int light, int color) {
            float thickness = corner < 2 ? backThickness : edgeThickness;
            float x = corner == 1 || corner == 2 ? thickness : -thickness;
            float z = corner < 2 ? back : edge;
            out.addVertex(pose, x, y, z).setColor(color >> 16 & 255, color >> 8 & 255, color & 255, alpha)
                    .setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, NORMAL_X[face], 0, NORMAL_Z[face]);
        }
    }
}
