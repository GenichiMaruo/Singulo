#version 150

// 降着ジェット鍛造炉のジェットとビーム（加算で重ねる）。
// texCoord0.x は軸に沿った位置（ブロック）、texCoord0.y は横方向の位置 v（-1〜1）に 4 × 種類 を足したもの。
// 種類: 0 プラズマ（太く荒れたジェット）・1 芯（白く細いビーム。速い波が流れる）・2 光暈（やわらかな外側の光）・
//       3 力場（照射室の安全の膜。u・v は面の上の位置）・4 流れ込む物質（降着円盤へ落ちる流れ）
// 頂点の色の a は明るさ。
uniform vec4 ColorModulator;
uniform float GameTime;
// 0 より大きければ深度だけを書く描き方で、光がこの強さに届かない画素を捨てる（GlowDepth）。
// 光っている所に深度を入れて、あとから描かれるガラスや水が手前に割り込まないようにする
uniform float DepthCut;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return s;
}

void main() {
    // GameTime は1日（24000 tick）で 1 になる。秒にする（1日を何周もすると桁が落ちるので、繰り返す長さで割った余りを使う）
    float t = mod(GameTime * 1200.0, 600.0);
    float style = floor((texCoord0.y + 2.0) / 4.0);
    float v = texCoord0.y - 4.0 * style;
    float u = texCoord0.x;
    float av = abs(v);
    float a;
    vec3 tint = vec3(1.0);
    if (style < 0.5) {
        // プラズマ: 外へ流れる大きな渦と、細かくちぎれる縁
        float n1 = fbm(vec2(u * 0.8 - t * 6.0, v * 1.5 + t * 0.6));
        float n2 = fbm(vec2(u * 2.2 - t * 10.0, v * 3.1 - t * 1.1));
        float edge = 1.0 - smoothstep(0.55 + 0.4 * n2, 1.0, av);
        float prof = pow(max(0.0, 1.0 - av * av), 1.2);
        a = prof * edge * (0.25 + 1.5 * n1 * n2);
        tint = mix(vec3(0.85, 0.9, 1.15), vec3(1.15, 0.95, 0.8), n1);
    } else if (style < 1.5) {
        // 芯: 中心が白くとがり、軸に沿って速い波（脈動）が流れる
        float prof = exp(-av * av * 5.0);
        float ripple = 0.7 + 0.3 * sin(u * 2.6 - t * 38.0) * sin(u * 0.7 - t * 9.0);
        float n = noise(vec2(u * 5.0 - t * 70.0, v * 3.0));
        a = prof * ripple * (0.75 + 0.5 * n) + exp(-av * av * 60.0) * 0.6;
    } else if (style < 2.5) {
        // 光暈: なだらかに消える外側の光と、ゆっくりした揺らぎ
        float prof = exp(-av * av * 3.2) * (1.0 - av);
        float n = fbm(vec2(u * 0.45 - t * 1.6, v * 1.2 + t * 0.25));
        a = max(0.0, prof) * (0.55 + 0.7 * n);
    } else if (style > 3.5) {
        // 流れ込む物質: 細い芯と、流れに乗って速く進む明るい塊
        float prof = exp(-av * av * 4.0);
        float knots = pow(0.5 + 0.5 * sin(u * 3.1 - t * 14.0), 6.0);
        float n = fbm(vec2(u * 1.7 - t * 9.0, v * 2.0));
        a = prof * (0.35 + 0.9 * knots + 0.5 * n);
        tint = mix(vec3(1.0), vec3(1.25, 1.1, 0.95), knots);
    } else {
        // 力場: 細かな格子と、上から下へ走る走査線（縁ほど明るい）。面そのものはほとんど透明（重なっても白い板にならない）
        vec2 g = abs(fract(vec2(u * 2.0, v * 5.0)) - 0.5);
        float grid = smoothstep(0.42, 0.5, max(g.x, g.y));
        float scan = smoothstep(0.85, 1.0, sin(v * 3.0 + t * 4.0) * 0.5 + 0.5);
        float rim = smoothstep(0.75, 1.0, av);
        float n = noise(vec2(u * 3.0 + t * 0.5, v * 4.0));
        a = 0.06 + 0.85 * grid + 0.35 * scan + 0.3 * rim;
        a *= 0.75 + 0.5 * n;
    }
    vec4 c = vertexColor * ColorModulator;
    fragColor = vec4(c.rgb * tint, clamp(a * c.a, 0.0, 1.0));
    if (DepthCut > 0.0 && fragColor.a < DepthCut) {
        discard;
    }
}
