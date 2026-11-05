#version 150

// 重力レンズ。1回の描画で1つのレンズを、影響が及ぶ画面の範囲だけ描く（奥のレンズから順。GravitationalLensing.apply）。
// 2種類のレンズを扱う:
//  - ブラックホール（Sphere.w = k > 0）: シュバルツシルト時空の曲がり方で背景をずらす。光子球に近づくほど偏向角が
//    発散するので、影（地平線の約2.6倍の黒い円）の縁のすぐ外では背景が強く押しつぶされ、周りの景色の平均の色
//    （何周も回った光の重なり）になって細い光の輪（光子リング）として光る。アインシュタインリングは曲がり方から自然に出る。
//  - 歪みだけ（Sphere.w = 0）: 点質量レンズの式 β = θ − θE²/θ で景色を少しずらすだけ（黒い中心も輪もなし）。
// Lens = (中心x, 中心y, 大きさ, 強さ)。座標は画面のUV（縦基準、横は Aspect 倍）。大きさはブラックホールなら影の半径、
// 歪みだけならアインシュタイン半径。
//
// 歪みが及ぶのは、レンズを中心とする球の中だけ:
//  - 画面上で球の外になる所は歪めない（球の縁に向かって歪みを 0 に落とすので、境目は見えない）。
//  - レンズの手前の面より手前に見えているものは歪めない。奥の景色だけを歪める。
//  - 曲がった先の色も奥の景色からだけ取る（手前のブロックを写し込んで二重に見せない）。
//
// 読み方: 歪みの写像のヤコビアン（1画素が元の画面のどれだけの範囲を写すか）を式から求めて、その楕円の範囲を
// ミップマップから読む。画面の微分（dFdx）を使わないので、2×2画素ごとのかたまり（低い解像度で目立つブロック状の
// ノイズ）が出ず、強く縮められる所もちらつかない。
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 Lens;
// (中心までの奥行き, 手前の面までの奥行きの差（ブロック）, 歪みが及ぶ球の半径（UV）, k)
// k は背景までの距離の比 D_ls / D_s（ブラックホール）。0 なら歪みだけのレンズ
uniform vec4 Sphere;
// 光の経路をたどって描く降着円盤（ブラックホールのレンズだけ）。座標はカメラから見たもの（ブロック、視線は −z）。
// Disk = (ブラックホールの中心 xyz, 地平線の半径)。地平線の半径が 0 なら円盤なし（影と円盤はワールドに描いてある）
// DiskN = (円盤の面の向き xyz, 内側の半径)、DiskP = (外側の半径, ドップラーで明るさが変わる幅, 明るさの倍率, 渦の筋の時計（秒、0 で筋なし）)
uniform vec4 Disk;
uniform vec4 DiskN;
uniform vec4 DiskP;
// 投影行列の逆行列。画素ごとの光の出発点と向きを、歩いたときの画面の揺れも含めて正確に求めるのに使う
uniform mat4 InvProj;
// 投影行列の (m22, m32)。深度から距離に戻すのに使う
uniform vec2 DepthParams;
uniform float Aspect;
// 視線から 1 ラジアンずれた方向が、縦の UV でどれだけ離れて写るか（投影行列の m11 ÷ 2）
uniform float Focal;
// 1 なら深度で手前のものを除く。0 なら深度を使わない（Iris 系のシェーダーパック使用時。深度バッファが読めないため、
// 球の範囲の中をすべて奥の景色とみなす）
uniform float UseDepth;

in vec2 texCoord;

out vec4 fragColor;

// 画面の外から背景を引っぱることになる歪みは、この幅（UV）で弱める（歪みだけ）か、周りの平均の色に変える（ブラックホール）
const float EDGE = 0.08;
// 球に入る点の前後で、歪みをなめらかに効かせる幅（ブロック）
const float SOFT = 0.4;
// 影の半径 ÷ 地平線の半径（3√3 / 2。GravitationalLensing.SHADOW_PER_HORIZON と同じ）
const float SHADOW_PER_HORIZON = 2.598;
const vec2 LO = vec2(0.0005);
const vec2 HI = vec2(0.9995);

// 深度バッファの値 → カメラからの奥行き（ブロック）
float dist(vec2 uv) {
    if (UseDepth < 0.5) {
        return 1.0e9;
    }
    float ndc = texture(Sampler1, clamp(uv, LO, HI)).r * 2.0 - 1.0;
    // 透視投影では ndc + m22 は常に負（m32 も負）。0 割りを避けるときも符号を保つ
    return DepthParams.y / min(ndc + DepthParams.x, -1e-6);
}

// シュバルツシルト時空での光の偏向角（ラジアン）。u は衝突パラメータ ÷ 光子球の臨界値（影の半径）。
// 光子球（u → 1）での対数発散と、遠くでの 4M/b + (15π/4)(M/b)² + … の展開をつないだ近似（誤差 0.02 ラジアン以下）。
// 返り値の y は du での微分
vec2 deflection(float u) {
    float iu = 1.0 / u;
    float a = -log(1.0 - iu) - iu * (0.2302 + iu * (0.0637 + iu * 0.10));
    float da = -1.0 / (u * (u - 1.0)) + iu * iu * (0.2302 + iu * (0.1274 + iu * 0.30));
    return vec2(a, da);
}

// 影の内側。強さが 1 未満のレンズ（重力異常点など）は影が半分しか黒くならないので、そのまま見せると歪んでいない窓に
// 見える。反対側の 影の半径² ÷ r の所（上下左右が反転した周りの景色）を読み、中心ほど遠くの景色を押しつぶして写す。
// 影の縁と、遠くて画面や球の外になる所は周りの景色の平均の色にする（外側の光子リングの近くとつながる）。
// 深度が読めないとき（シェーダーパック使用時）は、ワールドに描いた円盤などを歪めないよう、そのまま見せる
vec2 insideShadow(vec2 p, inout mat2 J, vec4 l, vec4 sph, vec2 d, float r, vec2 n, float here, inout float far,
                  inout vec3 farAt) {
    if (UseDepth < 0.5) {
        return p;
    }
    float w = smoothstep(sph.x - sph.y - SOFT, sph.x - sph.y + SOFT, here);
    if (w <= 0.0) {
        return p;
    }
    float rs = l.z;
    float beta = -rs * rs / r;
    vec2 q = p + (n * (beta - r) / vec2(Aspect, 1.0)) * w;
    float outside = smoothstep(0.0, EDGE, max(max(-q.x, q.x - 1.0), max(-q.y, q.y - 1.0)));
    float f = max(max(smoothstep(0.75 * rs, rs, r), smoothstep(0.6, 1.0, -beta / sph.z)), outside) * w;
    if (f > far) {
        far = f;
        farAt = vec3(l.xy, sph.z);
    }
    // ヤコビアン: 半径方向 dβ/dr = rs²/r²、円周方向 β/r = −rs²/r²（縦横の比をそろえた座標で）
    float k = rs * rs / (r * r);
    mat2 local = (1.0 - w) * mat2(1.0) + w * (-k * mat2(1.0) + 2.0 * k * outerProduct(n, n));
    J = mat2(1.0 / Aspect, 0.0, 0.0, 1.0) * local * mat2(Aspect, 0.0, 0.0, 1.0) * J;
    return clamp(q, LO, HI);
}

// レンズを通す。p は「いま見ている方向」（画面のUV）、J はその p を画面の1画素動かしたときの変化（列が x, y）。
// 返り値はレンズで曲がったあとの方向。奥のレンズは先に画面へ描いてあるので、手前のレンズ越しには丸ごと一緒にずれて見える。
// far は「周りの景色の平均の色に置き換える割合」、farAt はその色を取るレンズ（中心x, 中心y, 球の半径）
// clean: ワールドに黒い球や影を描いていない（シェーダーが円盤を描く）。影の真後ろの景色が画面のコピーに残っている
vec2 lens(vec2 p, inout mat2 J, vec4 l, vec4 sph, bool clean, inout float ring, inout float dark, inout float far,
          inout vec3 farAt) {
    if (l.z <= 0.0 || l.w <= 0.0) {
        return p;
    }
    vec2 d = (p - l.xy) * vec2(Aspect, 1.0);
    float r = max(length(d), 1e-6);
    float regionUv = sph.z;
    if (r >= regionUv) {
        return p;
    }
    vec2 n = d / r;
    float here = dist(p);
    bool hole = sph.w > 0.0;
    // 1画素の大きさ（縦の UV）。影の縁と光子リングは、どの解像度でも1〜2画素ぶんなめらかにする
    float pix = 1.0 / float(textureSize(Sampler0, 0).y);
    float rs = l.z;
    // 球の縁に向かって歪みを 0 に落とす（球の外とつながる）。ブラックホールは影の少し外から落とし始める
    float e0 = hole ? min(max(regionUv * 0.55, rs * 1.3), regionUv * 0.9) : regionUv * 0.55;
    float t = clamp((r - e0) / (regionUv - e0), 0.0, 1.0);
    float fade = 1.0 - t * t * (3.0 - 2.0 * t);
    float dFade = -6.0 * t * (1.0 - t) / (regionUv - e0);
    float disp;
    float dDisp;
    float vis = 0.0;
    if (hole) {
        // 影で隠れるのは地平線より奥にあるものだけ。それより手前（降着円盤の手前側や横、描いた黒い球）はそのまま見える
        vis = smoothstep(sph.x + 0.5 * sph.y, sph.x + 1.5 * sph.y, here) * l.w;
        // 深度が読めないときは手前のものと見分けられないので、黒くするのは地平線の大きさまで（その外の影の中は
        // 歪めずにそのまま見せる。降着円盤を黒で塗りつぶさないように）
        float rd = UseDepth < 0.5 ? rs / SHADOW_PER_HORIZON : rs;
        dark = max(dark, (1.0 - smoothstep(rd - pix, rd + pix, r)) * vis);
        // 光子リング: 影の縁のすぐ外の細い輪
        float xr = (r - rs) / max(rs * 0.012, pix);
        float glow = exp(-xr * xr) * vis;
        ring += glow;
        if (glow > 0.01) {
            farAt = vec3(l.xy, regionUv);
        }
        if (r <= rs + 0.5 * pix) {
            return insideShadow(p, J, l, sph, d, r, n, here, far, farAt);
        }
        vec2 a = deflection(r / rs);
        disp = Focal * sph.w * a.x;
        dDisp = Focal * sph.w * a.y / rs;
        // 何周も回ってくる光（偏向角がとても大きい所）は、周りの景色の平均の色にする
        float f = smoothstep(2.0, 3.5, a.x * fade) * vis;
        if (f > far) {
            far = f;
            farAt = vec3(l.xy, regionUv);
        }
    } else {
        float te2 = rs * rs;
        disp = te2 / r;
        dDisp = -te2 / (r * r);
    }
    // 歪めるのはレンズの手前の面より奥の景色だけ
    float back = sph.x - sph.y;
    float w = smoothstep(back - SOFT, back + SOFT, here) * l.w;
    if (w <= 0.0) {
        return p;
    }
    float m = disp * fade * w;
    float dm = (dDisp * fade + disp * dFade) * w;
    if (hole && !clean) {
        // 曲がった先 β（中心からの符号付きの距離。負なら反対側 = 上下左右が反転した二次像）が画面上の影の中に入ると、
        // そこには描いた黒い球や円盤しか写っていない。|β| を √(β² + 影の半径²) にして影のすぐ外へなめらかに押し出し、
        // 影の縁の外に反転した景色が押しつぶされて並ぶようにする（球の縁では元の β に戻る）
        float beta = r - m;
        float g = rs * min(1.0, fade * 3.0) * w;
        float bAbs = sqrt(beta * beta + g * g);
        float betaP = beta >= 0.0 ? bAbs : -bAbs;
        float radialP = abs(beta) / max(bAbs, 1e-6) * (1.0 - dm);
        m = r - betaP;
        dm = 1.0 - radialP;
        // アインシュタインリング（β = 0）では、反転した像と正立した像が影の両側から来て合わさる。ここに写るはずの
        // 真後ろの景色は影に隠れて画面にないので、周りの景色の平均の色でつなぐ（境目の線を出さない）
        float seam = (1.0 - smoothstep(0.0, 0.35 * g, abs(beta))) * w;
        if (seam > far) {
            far = seam;
            farAt = vec3(l.xy, regionUv);
        }
    }
    vec2 s = -n * m / vec2(Aspect, 1.0);
    // 引っぱる先が画面の外なら: 歪みだけのレンズはそのぶん歪みを弱め、ブラックホールは周りの平均の色にする
    vec2 q = p + s;
    float outside = smoothstep(0.0, EDGE, max(max(-q.x, q.x - 1.0), max(-q.y, q.y - 1.0)));
    float scale = 1.0;
    if (hole) {
        if (outside * w > far) {
            far = outside * w;
            farAt = vec3(l.xy, regionUv);
        }
    } else {
        scale = 1.0 - outside;
    }
    // 色を取るのも奥の景色からだけ。曲がった先がレンズより手前のブロックなら、
    // そこに当たらない範囲でいちばん大きく曲げる（二分探索。手前のものを写し込んで二重に見せない）
    if (dist(p + s * scale) < back) {
        float lo = 0.0;
        float hi = scale;
        for (int i = 0; i < 7; i++) {
            float mid = (lo + hi) * 0.5;
            if (dist(p + s * mid) >= back) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        scale = lo;
    }
    m *= scale;
    dm *= scale;
    // この写像のヤコビアン（縦横の比をそろえた座標で）: 半径方向は 1 − m'、円周方向は 1 − m / r
    float radial = 1.0 - dm;
    float tangential = 1.0 - m / r;
    mat2 nn = outerProduct(n, n);
    mat2 local = tangential * mat2(1.0) + (radial - tangential) * nn;
    mat2 toIso = mat2(Aspect, 0.0, 0.0, 1.0);
    mat2 fromIso = mat2(1.0 / Aspect, 0.0, 0.0, 1.0);
    J = fromIso * local * toIso * J;
    return p + s * scale;
}

// 降着円盤を、カメラから光の経路を逆にたどって描く。シュバルツシルト時空の光の軌道（u = 1/r、φ は軌道面内の角度）
//   d²u/dφ² = −u + (3/2) r_s u²
// を蛙跳び法で進め、円盤の面を横切るたびにその点の色を重ねる。手前を横切る円盤、影の上に回り込んで見える奥側の円盤、
// 影の下の細い二次像がこの1つの計算から出る。返り値は (色 × 不透明度, 不透明度)
vec4 traceDisk(vec3 origin, vec3 dir, vec4 da, vec4 dn, vec4 dp, float here) {
    vec4 acc = vec4(0.0);
    float rs = da.w;
    if (rs <= 0.0) {
        return acc;
    }
    // 中心は光の出発点から見た位置にする（揺れで出発点がずれても円盤が景色と一緒に動く）
    vec3 c = da.xyz - origin;
    vec3 n = dn.xyz;
    float rin = dn.w;
    float rout = dp.x;
    vec3 toCam = -c;
    float d0 = length(toCam);
    vec3 e1 = toCam / d0;
    float radial = dot(dir, e1);
    vec3 perp = dir - radial * e1;
    float tang = length(perp);
    // 遠くを素通りする光（衝突パラメータが円盤より十分大きい）はたどらない
    if (tang < 1e-6 || (d0 * tang > rout * 1.25 && d0 > rout * 1.25)) {
        return acc;
    }
    vec3 e2 = perp / tang;
    float u = 1.0 / d0;
    float du = -u * radial / tang;
    float phi = 0.0;
    vec3 prev = toCam;
    float prevS = dot(prev, n);
    for (int i = 0; i < 260; i++) {
        float r = 1.0 / u;
        // 地平線の近くほど細かく進む
        float h = mix(0.015, 0.1, clamp((r - 1.5 * rs) / (6.0 * rs), 0.0, 1.0));
        du += 0.5 * h * (-u + 1.5 * rs * u * u);
        u += h * du;
        du += 0.5 * h * (-u + 1.5 * rs * u * u);
        phi += h;
        if (u <= 0.0) {
            break;
        }
        vec3 q = (cos(phi) * e1 + sin(phi) * e2) / u;
        float sd = dot(q, n);
        if (sd * prevS < 0.0) {
            float t = prevS / (prevS - sd);
            vec3 hit = mix(prev, q, t);
            float rh = length(hit);
            if (rh > rin && rh < rout) {
                float f = (rh - rin) / (rout - rin);
                // ドップラー: 円盤の回る向き（面の向き × 中心からの向き）と、そこからカメラへ向かう光の向き
                vec3 v = normalize(cross(n, hit));
                vec3 toward = normalize(prev - q);
                float br = clamp(0.55 + dp.y * dot(v, toward), 0.15, 1.0);
                vec3 ci = vec3(255.0 * br, 200.0 * br + 40.0, 150.0 * br + 90.0) / 255.0;
                vec3 co = vec3(180.0 * br, 110.0 * br, 90.0 * br + 40.0) / 255.0;
                vec3 col = mix(ci, co, f);
                if (dp.w > 0.0) {
                    // 渦の筋: 内側ほど速く回る対数らせんの濃淡（鍛造炉の円盤だけ）
                    vec3 a1 = normalize(cross(n, abs(n.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
                    vec3 a2 = cross(n, a1);
                    float ang = atan(dot(hit, a2), dot(hit, a1));
                    float streak = sin(ang * 5.0 + log(rh / rin) * 9.0 - dp.w * 3.0 / (0.4 + f));
                    col *= 0.8 + 0.2 * streak;
                }
                if (dp.z != 1.0) {
                    // 明るさ: 明るいほど白く（中心の色に寄せて）、暗いほど赤く沈む
                    float hot = clamp(dp.z - 1.0, 0.0, 2.0);
                    col = mix(col, vec3(1.0, 0.97, 0.92), clamp(hot * 0.35, 0.0, 0.7)) * min(dp.z, 1.0 + hot * 0.25);
                }
                float a = (250.0 / 255.0) * (1.0 - f);
                // 手前のブロックに隠れる。曲がって届く光は、ブラックホールの面より手前のものにだけ隠れる
                float lim = min(-(da.xyz + hit).z, -da.z);
                a *= smoothstep(lim - 0.25, lim + 0.05, here);
                acc.rgb += (1.0 - acc.a) * a * col;
                acc.a += (1.0 - acc.a) * a;
                if (acc.a > 0.98) {
                    break;
                }
            }
        }
        prev = q;
        prevS = sd;
        // 地平線に落ちた / 円盤より外へ出ていく
        if (u * rs > 1.0 || (du < 0.0 && r > rout * 1.3)) {
            break;
        }
    }
    return acc;
}

// 画面の uv の方向へ出る光で円盤をたどる（光の出発点と向きは投影行列の逆行列から。歩いたときの画面の揺れも含む）
vec4 traceDiskAt(vec2 uv, vec4 da, vec4 dn, vec4 dp) {
    if (da.w <= 0.0) {
        return vec4(0.0);
    }
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 a = InvProj * vec4(ndc, -1.0, 1.0);
    vec4 b = InvProj * vec4(ndc, 1.0, 1.0);
    vec3 origin = a.xyz / a.w;
    return traceDisk(origin, normalize(b.xyz / b.w - origin), da, dn, dp, dist(uv));
}

// 1画素が写す範囲（J で決まる楕円）を、長い方向に4点、短い方向はミップマップのぼかしでならして読む
vec3 sampleScreen(vec2 p, mat2 J) {
    vec2 size = vec2(textureSize(Sampler0, 0));
    // テクセル単位の楕円の軸: T Tᵀ の固有ベクトル
    vec2 cx = J[0] * size;
    vec2 cy = J[1] * size;
    float a = dot(vec2(cx.x, cy.x), vec2(cx.x, cy.x));
    float b = dot(vec2(cx.x, cy.x), vec2(cx.y, cy.y));
    float c = dot(vec2(cx.y, cy.y), vec2(cx.y, cy.y));
    float mid = 0.5 * (a + c);
    float rad = sqrt(max(0.25 * (a - c) * (a - c) + b * b, 0.0));
    float major = sqrt(mid + rad);
    float minor = sqrt(max(mid - rad, 0.0));
    float ang = 0.5 * atan(2.0 * b, a - c);
    vec2 axis = vec2(cos(ang), sin(ang));
    // 長い方向は最大でも短い方向の 16 倍まで（それ以上はぼかしに回す）
    minor = max(minor, major / 16.0);
    vec2 gMajor = axis * major / size;
    vec2 gMinor = vec2(-axis.y, axis.x) * minor / size;
    if (major <= 1.5) {
        return textureGrad(Sampler0, clamp(p, LO, HI), gMajor, gMinor).rgb;
    }
    vec2 g = gMajor * 0.25;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        float o = (float(i) - 1.5) * 0.25;
        sum += textureGrad(Sampler0, clamp(p + gMajor * o, LO, HI), g, gMinor).rgb;
    }
    return sum * 0.25;
}

void main() {
    float ring = 0.0;
    float dark = 0.0;
    float far = 0.0;
    vec3 farAt = vec3(0.5, 0.5, 0.0);
    vec2 size = vec2(textureSize(Sampler0, 0));
    mat2 J = mat2(1.0 / size.x, 0.0, 0.0, 1.0 / size.y);
    vec2 p = lens(texCoord, J, Lens, Sphere, Disk.w > 0.0, ring, dark, far, farAt);
    vec4 disk = traceDiskAt(texCoord, Disk, DiskN, DiskP);
    // 何も変えない所は描かない（描く範囲は長方形なので、重なるレンズの結果を四隅で上書きしないように）
    if (p == texCoord && ring <= 0.0 && dark <= 0.0 && far <= 0.0 && disk.a <= 0.0) {
        discard;
    }
    vec3 col;
    if (p == texCoord) {
        // 歪みのない所は元の画素をそのまま読む（画面全体がぼやけないように）
        col = textureLod(Sampler0, p, 0.0).rgb;
    } else {
        col = sampleScreen(p, J);
    }
    if (far > 0.0 || ring > 0.0) {
        // 周りの景色の平均の色: 歪みの球のすぐ外の4か所を、1テクセルが画面の 1/8 ほどを覆う段のミップマップで読む
        // （球の中は黒い影や円盤で占められているので含めない）
        float top = log2(max(size.x, size.y)) - 3.0;
        vec2 o = vec2(farAt.z * 1.15 / Aspect, farAt.z * 1.15);
        vec3 around = 0.25 * (textureLod(Sampler0, clamp(farAt.xy + vec2(o.x, 0.0), LO, HI), top).rgb
                            + textureLod(Sampler0, clamp(farAt.xy - vec2(o.x, 0.0), LO, HI), top).rgb
                            + textureLod(Sampler0, clamp(farAt.xy + vec2(0.0, o.y), LO, HI), top).rgb
                            + textureLod(Sampler0, clamp(farAt.xy - vec2(0.0, o.y), LO, HI), top).rgb);
        col = mix(col, around, clamp(far, 0.0, 1.0));
        col += (around * 0.8 + vec3(1.0, 0.9, 0.78) * 0.3) * min(ring, 1.0);
    }
    col = mix(col, vec3(0.0), clamp(dark, 0.0, 1.0));
    // 降着円盤
    col = col * (1.0 - disk.a) + disk.rgb;
    fragColor = vec4(col, 1.0);
}
