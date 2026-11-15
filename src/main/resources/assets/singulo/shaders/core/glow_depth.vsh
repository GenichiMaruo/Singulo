#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// 光と同じ形を深度へ書く（GlowDepth）
in vec3 Position;
in vec4 Color;


out float alpha;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    alpha = Color.a;
}
