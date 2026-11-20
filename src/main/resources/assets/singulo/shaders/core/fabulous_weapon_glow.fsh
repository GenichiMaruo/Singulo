#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float visibility = 1.0 - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    vec4 glow = vertexColor * ColorModulator * visibility;
    vec3 emitted = glow.rgb * glow.a;
    if (max(max(emitted.r, emitted.g), emitted.b) < 0.5 / 255.0) discard;
    // Vanilla Fabulous ignores a zero-alpha layer. A glow emits light without
    // blocking the rear terrain; record just enough coverage to preserve the layer.
    fragColor = vec4(emitted, 1.0 / 255.0);
}
