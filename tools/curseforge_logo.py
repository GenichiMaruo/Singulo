"""Singulo のロゴ（CurseForge 用、正方形）: 光の曲がりを計算して描いたブラックホール。

画素ごとに光線を飛ばし、シュヴァルツシルト時空の光の軌道（x'' = −1.5 h² x / r⁵、シュヴァルツシルト半径 = 1）を数値で追う。
光線が降着円盤の平面を通ればその色を取り（円盤の奥側が地平線の上下に回り込んで見える像も、そのまま出てくる）、
地平線に落ちれば黒、遠くへ抜ければ曲がった先の星空の色になる。円盤の回転による明るさの偏り（ドップラー効果）も入れる。
使い方: uv run --no-project --python 3.12 --with pillow --with numpy tools/curseforge_logo.py docs/curseforge/logo.png
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

OUT = Path(sys.argv[1])
N = 900                       # 計算する大きさ（あとで縮める）
DIST = 26.0                   # カメラまでの距離（シュヴァルツシルト半径 = 1）
ELEV = np.radians(7.0)        # 円盤の面から見上げる角度
FOV = np.radians(44.0)
R_IN, R_OUT = 2.6, 13.0       # 降着円盤の内側と外側の半径
STEPS = 900

# ---------------------------------------------------------------- カメラと光線
cam = np.array([0.0, DIST * np.sin(ELEV), -DIST * np.cos(ELEV)])
forward = -cam / np.linalg.norm(cam)
right = np.cross(np.array([0.0, 1.0, 0.0]), forward)
right /= np.linalg.norm(right)
up = np.cross(forward, right)
s = np.tan(FOV / 2)
ys, xs = np.mgrid[0:N, 0:N]
u = (xs + 0.5) / N * 2 - 1
v = 1 - (ys + 0.5) / N * 2
v = v - 0.08                                          # 少し下を向く（影を真ん中より少し上に）
dirs = forward[None, None, :] + (u[..., None] * s) * right[None, None, :] + (v[..., None] * s) * up[None, None, :]
dirs /= np.linalg.norm(dirs, axis=2, keepdims=True)
dirs = dirs.reshape(-1, 3)
M = dirs.shape[0]
pos = np.repeat(cam[None, :], M, axis=0)
vel = dirs.copy()
h2 = np.sum(np.cross(pos, vel) ** 2, axis=1)          # 角運動量の二乗（光の軌道で一定）

color = np.zeros((M, 3))
alpha = np.zeros(M)                                   # どれだけ色が決まったか（円盤は少し透ける）
alive = np.ones(M, dtype=bool)
fell = np.zeros(M, dtype=bool)


def disk_rgb(r, g):
    """円盤の色: 内側ほど熱く白い。g はドップラー因子（近づく側は青白く明るく、遠ざかる側は赤く暗く）。"""
    t = np.clip((r - R_IN) / (R_OUT - R_IN), 0, 1)
    temp = (1 - t) ** 1.6                              # 0〜1 の温度
    temp = np.clip(temp * (0.55 + 0.55 * g), 0, 1.3)
    hot = np.array([1.0, 0.97, 0.9])
    warm = np.array([1.0, 0.62, 0.26])
    cool = np.array([0.75, 0.18, 0.12])
    c = np.where(temp[:, None] > 0.5,
                 warm + (hot - warm) * np.clip((temp[:, None] - 0.5) / 0.5, 0, 1),
                 cool + (warm - cool) * np.clip(temp[:, None] / 0.5, 0, 1))
    # 明るさ: 内側ほど明るく、ドップラー因子の3乗で偏る。縁はなめらかに消す
    edge = np.clip((r - R_IN) / 0.35, 0, 1) * np.clip((R_OUT - r) / 3.5, 0, 1)
    rings = 0.92 + 0.08 * np.sin(r * 5.3)             # 細かな筋
    bright = (1.4 * (1 - t) ** 1.2 + 0.15) * g ** 3 * rings * edge
    return c * bright[:, None], edge


for step in range(STEPS):
    idx = np.nonzero(alive)[0]
    if idx.size == 0:
        break
    p = pos[idx]
    w = vel[idx]
    r = np.linalg.norm(p, axis=1)
    dt = np.clip(0.06 * r, 0.015, 0.9)                # 穴の近くほど細かく
    # 中点法で一歩進める
    acc = -1.5 * h2[idx, None] * p / r[:, None] ** 5
    p_mid = p + w * (dt[:, None] / 2)
    w_mid = w + acc * (dt[:, None] / 2)
    r_mid = np.linalg.norm(p_mid, axis=1)
    acc_mid = -1.5 * h2[idx, None] * p_mid / r_mid[:, None] ** 5
    p_new = p + w_mid * dt[:, None]
    w_new = w + acc_mid * dt[:, None]
    # 円盤の平面（y = 0）を通ったか
    cross = (p[:, 1] * p_new[:, 1]) < 0
    if cross.any():
        ci = np.nonzero(cross)[0]
        f = p[ci, 1] / (p[ci, 1] - p_new[ci, 1])
        hit = p[ci] + (p_new[ci] - p[ci]) * f[:, None]
        rr = np.hypot(hit[:, 0], hit[:, 2])
        on = (rr > R_IN) & (rr < R_OUT)
        if on.any():
            ci = ci[on]
            hit = hit[on]
            rr = rr[on]
            # 円盤の物質の速さ（ケプラー回転、半径1 = シュヴァルツシルト半径の単位で v = √(0.5 / r)）と向き
            speed = np.sqrt(0.5 / rr)
            tang = np.stack([-hit[:, 2], np.zeros_like(rr), hit[:, 0]], axis=1) / rr[:, None]
            to_cam = -w_new[ci] / np.linalg.norm(w_new[ci], axis=1, keepdims=True)
            gamma = 1 / np.sqrt(1 - speed ** 2)
            g = 1 / (gamma * (1 - speed * np.sum(tang * to_cam, axis=1)))
            # 重力赤方偏移も少し
            g = g * np.sqrt(np.clip(1 - 1 / rr, 0.05, 1))
            rgb, edge = disk_rgb(rr, g)
            gi = idx[ci]
            a = 0.92 * edge * (1 - alpha[gi])
            color[gi] += rgb * a[:, None]
            alpha[gi] += a
    pos[idx] = p_new
    vel[idx] = w_new
    rn = np.linalg.norm(p_new, axis=1)
    gone_in = rn < 1.0
    gone_out = (rn > DIST * 1.6) & (np.sum(p_new * w_new, axis=1) > 0)
    done = gone_in | gone_out | (alpha[idx] > 0.98)
    fell[idx[gone_in]] = True
    alive[idx[done]] = False

# ---------------------------------------------------------------- 遠くへ抜けた光線: 曲がった先の星空
d = vel / np.linalg.norm(vel, axis=1, keepdims=True)
lon = np.arctan2(d[:, 0], d[:, 2])
lat = np.arcsin(np.clip(d[:, 1], -1, 1))
# 星: 方向を立方体の格子に区切り、区切りごとに1つの点として光らせる（どの方向でも同じ大きさの点になる）
q = np.floor(d * 260).astype(np.int64)
cell = q[:, 0] * 73856093 ^ q[:, 1] * 19349663 ^ q[:, 2] * 83492791
rng = (np.sin(cell.astype(np.float64) * 0.001237) * 43758.5453) % 1.0
star = np.where(rng > 0.993, (rng - 0.993) / 0.007, 0.0) ** 2 * 1.3
neb = 0.5 + 0.5 * np.sin(lon * 3.1 + 1.2) * np.cos(lat * 4.3 - 0.6)
sky = np.stack([0.025 + 0.07 * neb, 0.02 + 0.03 * neb, 0.06 + 0.1 * neb], axis=1) + star[:, None] * np.array([0.9, 0.92, 1.0])
escaped = ~fell
color = color + sky * (1 - alpha)[:, None] * escaped[:, None]

img = np.clip(color, 0, None).reshape(N, N, 3)
# トーンマップと、にじむ光
img = img / (1 + img * 0.35)
base = Image.fromarray((np.clip(img, 0, 1) ** (1 / 1.1) * 255).astype(np.uint8))
bright = np.clip(img - 0.55, 0, None)
glow = Image.fromarray((np.clip(bright, 0, 1) * 255).astype(np.uint8))
g1 = np.asarray(glow.filter(ImageFilter.GaussianBlur(N * 0.012)), dtype=np.float32) / 255
g2 = np.asarray(glow.filter(ImageFilter.GaussianBlur(N * 0.045)), dtype=np.float32) / 255
outa = np.asarray(base, dtype=np.float32) / 255 + g1 * 0.6 + g2 * 0.45
# 角を少し暗く
yy, xx = np.mgrid[0:N, 0:N]
dv = np.hypot(xx - N / 2, yy - N / 2) / (N * 0.75)
outa *= np.clip(1.2 - dv * dv, 0, 1)[..., None]
out = Image.fromarray((np.clip(outa, 0, 1) * 255).astype(np.uint8))

OUT.parent.mkdir(parents=True, exist_ok=True)
out.resize((512, 512), Image.LANCZOS).save(OUT)
out.resize((400, 400), Image.LANCZOS).save(OUT.with_name('logo_400.png'))
print('ok', OUT)
