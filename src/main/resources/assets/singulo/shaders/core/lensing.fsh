#version 150

// 重力レンズ: 点質量レンズの式 β = θ − θE²/θ で、背景を中心から外へ押し広げて見せる。
// LensN = (中心x, 中心y, アインシュタイン半径θE, 強さ)。座標は画面のUV（縦基準、横は Aspect 倍）。
// θE の縁は明るい輪（アインシュタインリング）になり、θE の 2/3 より内側は事象の地平線として黒くする。
uniform sampler2D Sampler0;
uniform vec4 Lens0;
uniform vec4 Lens1;
uniform vec4 Lens2;
uniform vec4 Lens3;
uniform float Aspect;

in vec2 texCoord;

out vec4 fragColor;

void lens(vec2 uv, vec4 l, inout vec2 shift, inout float ring, inout float dark) {
    if (l.z <= 0.0 || l.w <= 0.0) {
        return;
    }
    vec2 d = (uv - l.xy) * vec2(Aspect, 1.0);
    float r = max(length(d), 1e-5);
    float te = l.z;
    // 遠くでは歪みを0に落とす（画面全体がゆがまないように）
    float fade = 1.0 - smoothstep(te * 3.0, te * 7.0, r);
    vec2 s = -(d / r) * (te * te / r) * fade * l.w;
    shift += s / vec2(Aspect, 1.0);
    float x = (r - te) / (te * 0.07);
    ring += exp(-x * x) * l.w;
    if (r < te * 0.66) {
        dark = max(dark, l.w);
    }
}

void main() {
    vec2 shift = vec2(0.0);
    float ring = 0.0;
    float dark = 0.0;
    lens(texCoord, Lens0, shift, ring, dark);
    lens(texCoord, Lens1, shift, ring, dark);
    lens(texCoord, Lens2, shift, ring, dark);
    lens(texCoord, Lens3, shift, ring, dark);
    vec3 c = texture(Sampler0, clamp(texCoord + shift, vec2(0.001), vec2(0.999))).rgb;
    c += vec3(1.0, 0.92, 0.82) * ring * 0.55;
    c = mix(c, vec3(0.0), dark);
    fragColor = vec4(c, 1.0);
}
