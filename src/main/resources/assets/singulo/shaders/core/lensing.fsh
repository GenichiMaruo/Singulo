#version 150

// 重力レンズ: 点質量レンズの式 β = θ − θE²/θ で、背景を中心から外へ押し広げて見せる。
// LensN = (中心x, 中心y, アインシュタイン半径θE, 強さ)。座標は画面のUV（縦基準、横は Aspect 倍）。
// θE の縁は明るい輪（アインシュタインリング）になり、θE × HORIZON より内側は事象の地平線として黒くする。
//
// 歪みが及ぶのは、ブラックホールを中心とする球（SphereN）の中だけ:
//  - 画面上で球の外になる所は歪めない（球の縁に向かって歪みを 0 に落とすので、境目は見えない）。
//  - ブラックホールの手前の面（中心 − 地平線の半径）より手前に見えているものは歪めない。奥の景色だけを歪める。
//  - 曲がった先の色も奥の景色からだけ取る（手前のブロックを写し込んで二重に見せない）。
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 Lens0;
uniform vec4 Lens1;
uniform vec4 Lens2;
uniform vec4 Lens3;
// (中心までの奥行き, アインシュタイン半径, 球の半径) ブロック単位, 黒い中心とリングを出すか（1 / 0）
uniform vec4 Sphere0;
uniform vec4 Sphere1;
uniform vec4 Sphere2;
uniform vec4 Sphere3;
// 投影行列の (m22, m32)。深度から距離に戻すのに使う
uniform vec2 DepthParams;
uniform float Aspect;
// 1 なら深度で手前のものを除く。0 なら深度を使わない（Iris 系のシェーダーパック使用時。深度バッファが読めないため、
// 球の範囲の中をすべて奥の景色とみなす）
uniform float UseDepth;

in vec2 texCoord;

out vec4 fragColor;

// 画面の外から背景を引っぱることになる歪みは、この幅（UV）で弱めて消す（端を引き伸ばしたり鏡写しにしたりしない）
const float EDGE = 0.08;
// 黒い中心の半径 ÷ アインシュタイン半径（GravitationalLensing.EINSTEIN_PER_HORIZON = 2.2 の逆数）
const float HORIZON = 0.4545;
// 球に入る点の前後で、歪みをなめらかに効かせる幅（ブロック）
const float SOFT = 0.4;

// 深度バッファの値 → カメラからの奥行き（ブロック）
float dist(vec2 uv) {
    if (UseDepth < 0.5) {
        return 1.0e9;
    }
    float ndc = texture(Sampler1, uv).r * 2.0 - 1.0;
    // 透視投影では ndc + m22 は常に負（m32 も負）。0 割りを避けるときも符号を保つ
    return DepthParams.y / min(ndc + DepthParams.x, -1e-6);
}

// 1つのレンズを通す。p は「いま見ている方向」（画面のUV）。返り値はレンズで曲がったあとの方向。
// リング・黒い部分も曲がったあとの p で数えるので、手前のレンズ越しに見た奥のレンズは丸ごと一緒にずれて見える。
vec2 lens(vec2 p, vec4 l, vec4 sph, inout float ring, inout float dark) {
    if (l.z <= 0.0 || l.w <= 0.0) {
        return p;
    }
    vec2 d = (p - l.xy) * vec2(Aspect, 1.0);
    float r = max(length(d), 1e-5);
    float te = l.z;
    // 画面の UV → ブロックへの換算（アインシュタイン半径どうしの比）
    float perUv = sph.y / te;
    float regionUv = sph.z / perUv;
    if (r >= regionUv) {
        return p;
    }
    // 歪めるのはブラックホールの手前の面（中心 − 地平線の半径）より奥の景色だけ
    float back = sph.x - sph.y * HORIZON;
    float here = dist(p);
    // リングと黒い中心は、ブラックホールより手前にブロックがあれば隠れる
    float vis = smoothstep(back - 1.2, back - 0.4, here) * l.w * sph.w;
    float xr = (r - te) / (te * 0.07);
    ring += exp(-xr * xr) * vis;
    if (r < te * HORIZON) {
        dark = max(dark, vis);
    }
    float w = smoothstep(back - SOFT, back + SOFT, here) * l.w;
    if (w <= 0.0) {
        return p;
    }
    // 球の縁に向かって歪みを 0 に落とす（球の外とつながる）
    float fade = 1.0 - smoothstep(regionUv * 0.55, regionUv, r);
    vec2 s = -(d / r) * (te * te / r) * fade * w / vec2(Aspect, 1.0);
    // 引っぱる先が画面の外なら、そのぶん歪みを弱める
    vec2 q = p + s;
    float outside = max(max(-q.x, q.x - 1.0), max(-q.y, q.y - 1.0));
    s *= 1.0 - smoothstep(0.0, EDGE, outside);
    // 色を取るのも奥の景色からだけ。曲がった先が手前のもの（ブロックや黒い球）なら、
    // そこに当たらない範囲でいちばん大きく曲げる（二分探索。手前のものを写し込んで二重に見せない）
    if (dist(clamp(p + s, vec2(0.0005), vec2(0.9995))) < back) {
        float lo = 0.0;
        float hi = 1.0;
        for (int i = 0; i < 7; i++) {
            float mid = (lo + hi) * 0.5;
            if (dist(clamp(p + s * mid, vec2(0.0005), vec2(0.9995))) >= back) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        s *= lo;
    }
    return clamp(p + s, vec2(0.0005), vec2(0.9995));
}

void main() {
    float ring = 0.0;
    float dark = 0.0;
    // カメラに近いレンズから順に光をさかのぼる（Lens0 がいちばん近い）
    vec2 p = texCoord;
    p = lens(p, Lens0, Sphere0, ring, dark);
    p = lens(p, Lens1, Sphere1, ring, dark);
    p = lens(p, Lens2, Sphere2, ring, dark);
    p = lens(p, Lens3, Sphere3, ring, dark);
    vec3 c = texture(Sampler0, p).rgb;
    c += vec3(1.0, 0.92, 0.82) * ring * 0.55;
    c = mix(c, vec3(0.0), dark);
    fragColor = vec4(c, 1.0);
}
