#version 150

// 画面全体を覆う四角形。座標はそのまま正規化デバイス座標として使う
in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
}
