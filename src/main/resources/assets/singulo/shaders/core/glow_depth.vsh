#version 150

// 光と同じ形を深度へ書く（GlowDepth）
in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float alpha;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    alpha = Color.a;
}
