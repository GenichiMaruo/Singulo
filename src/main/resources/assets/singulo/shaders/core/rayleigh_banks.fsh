#version 330
uniform sampler2D NaturalLight;
uniform sampler2D NoonLight;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    int block = clamp(p.x, 0, 15);
    int row = clamp(p.y, 0, 511);
    if (row >= 256 && row < 272) {
        fragColor = texelFetch(NoonLight, ivec2(block, row - 256), 0);
    } else if (row < 256) {
        int artificial = row / 16;
        vec4 natural = texelFetch(NaturalLight, ivec2(block, row % 16), 0);
        fragColor = artificial == 0 ? natural : max(natural, texelFetch(NoonLight, ivec2(block, artificial), 0));
    } else { fragColor = vec4(1.0); }
}
