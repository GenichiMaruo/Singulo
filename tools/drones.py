# -*- coding: utf-8 -*-
"""ドローン輸送の絵とモデル（ドローン・ステーション、発着ポート、梱包箱、ドローン用のメモリとキット、輸送ドローン）。

アンドロイドの装置と同じ筐体・同じバイザーの系統にそろえ、光はドローンの橙にする（ひと目でドローン用とわかるように）。
顔は丸い単眼。ステーションはバイザーの四隅にローターの印、上面は着陸パッド。ポートは単眼の下に箱の印、上面は昇降口。
"""
import math

from PIL import Image, ImageDraw

import art16
from art16 import S, TIERS, casing, mix, new, rgba, stable_hash

MODID = 'singulo'
ORANGE = (240, 160, 64)
ORANGE_HI = (255, 214, 140)
ORANGE_LO = (120, 74, 36)
CYAN = (120, 225, 255)
ANIM_FRAMES = 8
CRATES = ('packing_crate', 'battery_packing_crate', 'large_battery_packing_crate')


# ---------------------------------------------------------------- 装置の正面（アンドロイドと同じバイザーに、橙の単眼）

def front(iid, stage, on, blink=False):
    """ドローンの装置の正面の1コマ。止まっている間は単眼を閉じて眠っている。"""
    t = TIERS[stage]
    im = casing(stage, 'side', seed=stable_hash(iid) % 97)
    d = ImageDraw.Draw(im)
    d.rectangle([2, 3, 13, 12], fill=rgba(t['trim']))
    glass = (22, 30, 40) if on else (16, 22, 30)
    d.rectangle([3, 4, 12, 11], fill=rgba(glass))
    px = lambda x, y, col: d.point((x, y), fill=rgba(col))
    dim = mix(ORANGE, glass, 0.6)
    if iid == 'drone_station':
        # バイザーの四隅にローターの印（小さな十字）
        for cx, cy in ((4, 5), (11, 5), (4, 10), (11, 10)):
            c = mix(ORANGE, glass, 0.35 if on else 0.7)
            px(cx, cy, c)
            if on:
                px(cx - 1 if cx < 8 else cx + 1, cy, mix(c, glass, 0.5))
    else:
        # 単眼の下に、小さな箱の印
        c = mix(ORANGE, glass, 0.3 if on else 0.7)
        d.rectangle([7, 10, 8, 10], fill=rgba(c))
        px(6, 10, mix(c, glass, 0.5))
        px(9, 10, mix(c, glass, 0.5))
    if not on:
        # 閉じた単眼（下へ弧）
        for x, y in ((6, 7), (7, 8), (8, 8), (9, 7)):
            px(x, y, dim)
        return im
    lit = mix(ORANGE, ORANGE_HI, 0.35)
    if blink:
        for x in (6, 7, 8, 9):
            px(x, 7, lit)
        return im
    # 丸い単眼（4×4 の角を落とした輪）と、左上の光
    for x, y in ((7, 5), (8, 5), (6, 6), (9, 6), (6, 7), (9, 7), (7, 8), (8, 8)):
        px(x, y, lit)
    d.rectangle([7, 6, 8, 7], fill=rgba(mix(ORANGE, (40, 20, 10), 0.35)))
    px(7, 6, (255, 255, 255))
    return im


# ---------------------------------------------------------------- 上面

def station_top(k):
    """ドローン・ステーションの上面: 白い外装の枠の中の濃い着陸パッド。橙の円と十字の印、広がる光の輪、四隅の灯が順に光る。"""
    im, d = new(fill=(228, 232, 236, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=(196, 202, 210, 255))
    d.rectangle([2, 2, S - 3, S - 3], fill=(30, 34, 42, 255), outline=rgba(ORANGE))
    d.ellipse([4, 4, 11, 11], outline=rgba(mix(ORANGE, (30, 34, 42), 0.35)))
    d.line([(7, 6), (7, 9)], fill=rgba(mix(ORANGE, (30, 34, 42), 0.2)))
    d.line([(8, 6), (8, 9)], fill=rgba(mix(ORANGE, (30, 34, 42), 0.2)))
    d.line([(6, 7), (9, 7)], fill=rgba(mix(ORANGE, (30, 34, 42), 0.45)))
    # 広がる輪（中心から外へ、だんだん薄く）
    r = 1 + 5.5 * (k / ANIM_FRAMES)
    a = 1 - k / ANIM_FRAMES
    for i in range(24):
        ang = 2 * math.pi * i / 24
        x, y = round(7.5 + math.cos(ang) * r), round(7.5 + math.sin(ang) * r)
        if 3 <= x <= S - 4 and 3 <= y <= S - 4:
            d.point((x, y), fill=rgba(mix((30, 34, 42), ORANGE_HI, 0.25 + 0.6 * a)))
    for i, (x, y) in enumerate(((1, 1), (S - 2, 1), (S - 2, S - 2), (1, S - 2))):
        on = (i + k // 2) % 4 == 0
        d.point((x, y), fill=rgba(ORANGE) if on else (150, 156, 164, 255))
    return im


def port_top():
    """発着ポートの上面: 開口部（2〜13）のまわりの縁に、橙と黒の斜めの縞。外側は白い外装。"""
    im, d = new(fill=(228, 232, 236, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=(196, 202, 210, 255))
    for y in range(S):
        for x in range(S):
            if 2 <= x <= 13 and 2 <= y <= 13:
                continue
            if x in (0, S - 1) or y in (0, S - 1):
                continue
            stripe = (x + y) % 4 < 2
            d.point((x, y), fill=rgba(ORANGE if stripe else (40, 42, 48)))
    return im


def hatch():
    """昇降口の扉（2枚。上から見た 2〜13 の範囲）: 明るい金属の板に、中央の合わせ目と、外へ開く向きの橙の矢印。"""
    im, d = new(fill=(200, 205, 212, 255))
    d.rectangle([2, 2, 13, 13], fill=(206, 211, 218, 255), outline=(150, 156, 166, 255))
    d.line([(7, 2), (7, 13)], fill=(70, 74, 84, 255))
    d.line([(8, 2), (8, 13)], fill=(110, 116, 126, 255))
    for x0, step in ((5, -1), (10, 1)):
        for k, y in enumerate((6, 7, 8, 9)):
            off = 1 - abs(k - 1.5) // 1
            d.point((x0 + step * int(off), y), fill=rgba(ORANGE))
    for x in (3, 12):
        for y in (3, 12):
            d.point((x, y), fill=(120, 126, 136, 255))
    # 扉の厚み（側面は1段目）
    d.line([(0, 0), (S - 1, 0)], fill=(150, 156, 166, 255))
    return im


def platform():
    """昇降台: 濃い格子の天板に橙の縁。中央の3列は柱と同じ鋼の帯（柱の側面にもこの列を使う）。"""
    im, d = new(fill=(52, 56, 66, 255))
    for y in range(S):
        for x in range(S):
            if (x + y) % 3 == 0:
                d.point((x, y), fill=(40, 44, 52, 255))
    d.rectangle([2, 2, 13, 13], outline=rgba(ORANGE))
    for x in (6, 7, 8, 9):
        for y in range(S):
            c = (196, 202, 210) if x in (7, 8) else (150, 156, 164)
            if y % 4 == 0:
                c = mix(c, (60, 64, 72), 0.3)
            d.point((x, y), fill=rgba(c))
    return im


# ---------------------------------------------------------------- アイテム

# 型ごとのメモリの結晶の色と、角の印の色（ローター型・ジェット型・反重力型）
MEMORY_KINDS = {
    'drone_memory': (ORANGE, CYAN),
    'drone_memory_jet': ((84, 170, 255), (230, 244, 255)),
    'drone_memory_gravity': ((92, 255, 224), (176, 140, 255)),
}


def memory_icon(crystal=ORANGE, mark=CYAN):
    """ドローン用クリスタルメモリ: アンドロイド用より小さい銀のチップに、型の色の結晶と金の端子、角に型の印。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    frame, frame_sh, frame_hi = (196, 202, 212), (140, 146, 160), (236, 240, 246)
    gold = (226, 186, 92)
    for y in range(4, 12):
        for x in range(4, 12):
            px[x, y] = (frame_sh if x == 11 or y == 11 else frame) + (255,)
    for x in range(4, 11):
        px[x, 4] = frame_hi + (255,)
    for y in range(5, 10):
        for x in range(5, 10):
            k = (y - 5) / 4
            c = tuple(int(a * (1 - 0.4 * k) + 255 * 0.22 * (1 - k)) for a in crystal)
            px[x, y] = tuple(min(255, v) for v in c) + (170 if (x + y) % 4 else 205,)
    px[6, 5] = (255, 255, 255, 235)
    for x in (5, 7, 9):
        px[x, 12] = gold + (255,)
        px[x, 13] = mix(gold, (120, 90, 40), 0.3) + (255,)
    # 角の印
    for x, y in ((3, 3), (12, 3)):
        px[x, y] = crystal + (255,)
    px[10, 10] = mark + (255,)
    return im


def kit_icon():
    """ドローンキット: 白い陶磁のケースに、橙の線と黒い留め具。ふたにドローンの印（4つのローターと胴）。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    white, shade, edge = (238, 240, 244), (200, 206, 216), (120, 126, 140)
    dark = (52, 56, 70)
    for y in range(4, 14):
        for x in range(1, 15):
            c = shade if y >= 11 else white
            if x in (1, 14) or y in (4, 13):
                c = edge
            px[x, y] = c + (255,)
    for x in range(2, 14):
        px[x, 10] = ORANGE + (255,)
    for x in (6, 7, 8, 9):
        px[x, 3] = dark + (255,)
    for (x, y) in ((5, 5), (10, 5), (5, 8), (10, 8)):
        px[x, y] = (90, 96, 110, 255)
    for (x, y) in ((6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7)):
        px[x, y] = (110, 120, 136, 255)
    px[7, 6] = ORANGE + (255,)
    return im


def phase_icon():
    """位相透過アップグレード: 濃い板のチップに、灰色の壁の帯と、それを抜ける紫のドローンの印（壁の向こう側は半分透ける）。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    violet, violet_hi = (155, 123, 255), (214, 200, 255)
    d.rounded_rectangle([1, 2, 14, 13], radius=2, fill=(36, 34, 52, 255), outline=(110, 96, 170, 255))
    d.rectangle([7, 3, 8, 12], fill=(120, 124, 136, 255))
    d.line([(7, 3), (7, 12)], fill=(150, 154, 166, 255))
    # 壁の手前は濃く、向こうは透ける
    for (x, y) in ((3, 5), (5, 5), (3, 9), (5, 9)):
        d.point((x, y), fill=rgba(violet))
    d.rectangle([4, 6, 6, 8], fill=rgba(violet))
    d.point((4, 6), fill=rgba(violet_hi))
    for (x, y) in ((10, 5), (12, 5), (10, 9), (12, 9)):
        d.point((x, y), fill=rgba(violet, 120))
    d.rectangle([9, 6, 11, 8], fill=rgba(violet, 110))
    for x in (2, 13):
        d.point((x, 7), fill=rgba(violet_hi, 160))
    return im


def crate_side(tier):
    """梱包箱の側面（モデルの 3〜12 の範囲に描く）: 鋼の枠と板。段階2は電池パック1つ、段階3は2つと光る帯。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    panel = (184, 190, 198)
    d.rectangle([3, 3, 12, 12], fill=rgba(panel), outline=(96, 102, 114, 255))
    d.line([(4, 4), (11, 4)], fill=rgba(mix(panel, (255, 255, 255), 0.4)))
    band = {1: (236, 238, 242), 2: ORANGE, 3: CYAN}[tier]
    d.line([(4, 8), (11, 8)], fill=rgba(band))
    if tier == 1:
        for x, y in ((4, 5), (11, 5), (4, 11), (11, 11)):
            d.point((x, y), fill=(120, 126, 138, 255))
    packs = {2: (6,), 3: (4, 8)}.get(tier, ())
    for x0 in packs:
        d.rectangle([x0, 5, x0 + 3, 11], fill=(46, 50, 60, 255), outline=(30, 32, 40, 255))
        d.point((x0 + 1, 6), fill=rgba(ORANGE_HI if tier == 2 else CYAN))
        d.line([(x0 + 1, 8), (x0 + 2, 8)], fill=rgba(band))
    return im


def crate_top(tier):
    """梱包箱の上面: 鋼の枠に十字の帯、段階の数だけの小さな印。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([3, 3, 12, 12], fill=(176, 182, 192, 255), outline=(96, 102, 114, 255))
    d.line([(7, 4), (7, 11)], fill=(120, 126, 138, 255))
    d.line([(8, 4), (8, 11)], fill=(150, 156, 166, 255))
    d.line([(4, 7), (11, 7)], fill=(120, 126, 138, 255))
    d.line([(4, 8), (11, 8)], fill=(150, 156, 166, 255))
    c = {1: (236, 238, 242), 2: ORANGE, 3: CYAN}[tier]
    for i in range(tier):
        d.point((4 + i * 2, 4), fill=rgba(c))
    return im


# ---------------------------------------------------------------- 輸送ドローン（64×64。CargoDroneRenderer.createLayer と同じ配置）

PARTS = {
    'body': (0, 0, 8, 3, 8, 'shell'),
    'cap': (32, 0, 4, 1, 4, 'shell'),
    'eye': (48, 0, 2, 2, 1, 'lens'),
    'belly': (0, 12, 4, 1, 4, 'dark'),
    'arm': (0, 18, 1, 1, 5, 'dark'),
    'pod': (16, 18, 2, 2, 2, 'pod'),
    'claw': (48, 18, 1, 5, 1, 'claw'),
}


# ジェット型（CargoDroneRenderer.createJetLayer と同じ配置）: (u, v, 幅, 高さ, 奥行き, 材質)
JET_PARTS = {
    'fuse': (0, 0, 4, 3, 12, 'shell'),
    'nose': (32, 0, 3, 2, 2, 'shell'),
    'lens': (44, 0, 1, 1, 1, 'lens'),
    'canopy': (48, 0, 2, 1, 4, 'glass'),
    'wing': (0, 16, 7, 1, 3, 'shell'),
    'nacelle': (20, 16, 3, 3, 6, 'dark'),
    'nozzle': (40, 16, 4, 4, 1, 'nozzle'),
    'fin': (0, 26, 1, 4, 3, 'shell'),
    'bar': (0, 34, 12, 1, 1, 'dark'),
    'claw': (28, 34, 1, 4, 1, 'claw'),
}

# 反重力型（CargoDroneRenderer.createGravityLayer と同じ配置）
GRAVITY_PARTS = {
    'gem': (0, 0, 5, 5, 5, 'crystal'),
    'seg': (24, 0, 4, 1, 1, 'pearl'),
    'emitter': (36, 0, 4, 1, 4, 'dark'),
    'spine': (54, 0, 1, 2, 1, 'pearl'),
    'pod': (0, 12, 2, 5, 2, 'pearl'),
    'tip': (10, 12, 1, 2, 1, 'tip'),
    'plate': (16, 12, 3, 3, 1, 'crystal'),
}
JET_BLUE = (84, 170, 255)
JET_HOT = (220, 240, 255)
TEAL = (92, 255, 224)
TEAL_HI = (210, 255, 246)
TEAL_DIM = (40, 128, 120)


def _faces(u, v, w, h, dd):
    return {
        'up': (u + dd, v, u + dd + w, v + dd),
        'down': (u + dd + w, v, u + dd + 2 * w, v + dd),
        'side_a': (u, v + dd, u + dd, v + dd + h),
        'front': (u + dd, v + dd, u + dd + w, v + dd + h),
        'side_b': (u + dd + w, v + dd, u + 2 * dd + w, v + dd + h),
        'back': (u + 2 * dd + w, v + dd, u + 2 * dd + 2 * w, v + dd + h),
    }


def entity_textures(te, save):
    """輸送ドローン: 白い胴に橙の帯、暗い腕と爪。光る線（単眼・帯・ローターの軸の下）は別の画像。羽根は CargoDroneRenderer.Rotors が色で描く。"""
    import random as _r
    rng = _r.Random(11)
    shell, shell_sh = (236, 238, 242), (204, 208, 216)
    dark = (58, 62, 72)
    base = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    glow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def shade(c, k):
        return tuple(max(0, min(255, int(x + k))) for x in c)

    def fill(rect, col):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = shade(col, rng.randint(-4, 4))
                if x1 - x0 >= 3 and y1 - y0 >= 3:
                    if y == y0 or x == x0:
                        c = shade(c, 12)
                    elif y == y1 - 1 or x == x1 - 1:
                        c = shade(c, -16)
                base.putpixel((x, y), c + (255,))

    def lit(rect, col=ORANGE):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                glow.putpixel((x, y), col + (255,))

    for name, (u, v, w, h, dd, mat) in PARTS.items():
        f = _faces(u, v, w, h, dd)
        col = {'shell': shell, 'lens': (20, 16, 12), 'pod': (70, 74, 84), 'claw': (84, 88, 98)}.get(mat, dark)
        for k, r in f.items():
            fill(r, shell_sh if mat == 'shell' and k in ('side_a', 'side_b', 'back') else col)
        if name == 'body':
            # 側面の真ん中の段に橙の帯（光る）
            for k in ('front', 'back', 'side_a', 'side_b'):
                x0, y0, x1, y1 = f[k]
                for x in range(x0, x1):
                    base.putpixel((x, y0 + 1), ORANGE + (255,))
                lit((x0 + 1, y0 + 1, x1 - 1, y0 + 2), ORANGE)
            # 上面のパネルの継ぎ目
            x0, y0, x1, y1 = f['up']
            for x in range(x0 + 1, x1 - 1):
                base.putpixel((x, y0 + 3), shade(shell, -40) + (255,))
        elif name == 'eye':
            lit(f['front'], ORANGE_HI)
        elif name == 'pod':
            x0, y0, x1, y1 = f['down']
            lit((x0, y0, x1, y1), ORANGE)
        elif name == 'claw':
            for k in ('front', 'back', 'side_a', 'side_b'):
                x0, y0, x1, y1 = f[k]
                base.putpixel((x0, y1 - 1), ORANGE + (255,))
    save(base, te / 'cargo_drone.png')
    save(glow, te / 'cargo_drone_glow.png')
    jet_textures(te, save)
    gravity_textures(te, save)


class _Skin:
    """64×64 のドローンの絵と、光る線の絵（同じ配置）。"""

    def __init__(self, seed):
        import random as _r
        self.rng = _r.Random(seed)
        self.base = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.glow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    @staticmethod
    def shade(c, k):
        return tuple(max(0, min(255, int(x + k))) for x in c)

    def fill(self, rect, col, bevel=True, noise=4):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = self.shade(col, self.rng.randint(-noise, noise))
                if bevel and x1 - x0 >= 3 and y1 - y0 >= 3:
                    if y == y0 or x == x0:
                        c = self.shade(c, 12)
                    elif y == y1 - 1 or x == x1 - 1:
                        c = self.shade(c, -16)
                self.base.putpixel((x, y), c + (255,))

    def px(self, x, y, col, lit=False):
        self.base.putpixel((x, y), col + (255,))
        if lit:
            self.glow.putpixel((x, y), col + (255,))

    def lit(self, rect, col):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px(x, y, col, lit=True)


def jet_textures(te, save):
    """ジェット型: 黒鉛色の胴に青い線（光る）、暗い操縦席の覆いの縁、白い山形の印、噴射口の奥の青白い光。"""
    sk = _Skin(23)
    shell, shell_sh = (76, 82, 94), (56, 60, 70)
    white = (226, 232, 240)
    cols = {'shell': shell, 'lens': (16, 22, 34), 'glass': (22, 32, 48), 'dark': (40, 43, 50), 'nozzle': (30, 32, 38), 'claw': (66, 70, 80)}
    for name, (u, v, w, h, dd, mat) in JET_PARTS.items():
        f = _faces(u, v, w, h, dd)
        for k, r in f.items():
            sk.fill(r, shell_sh if mat == 'shell' and k in ('side_a', 'side_b', 'down') else cols[mat])
        if name == 'fuse':
            # 両わきの青い線、上面の継ぎ目と白い山形の印
            for k in ('side_a', 'side_b'):
                x0, y0, x1, y1 = f[k]
                sk.lit((x0, y0 + 1, x1, y0 + 2), JET_BLUE)
            x0, y0, x1, y1 = f['up']
            for y in range(y0, y1):
                sk.px(x0 + 1, y, sk.shade(shell, -26))
                sk.px(x1 - 2, y, sk.shade(shell, -26))
            for i in range(2):
                sk.px(x0 + 1 + i, y1 - 4 + i, white)
                sk.px(x1 - 2 - i, y1 - 4 + i, white)
        elif name == 'nose':
            x0, y0, x1, y1 = f['front']
            sk.px(x0, y0, white)
            sk.px(x1 - 1, y0, white)
        elif name == 'lens':
            sk.lit(f['front'], JET_HOT)
        elif name == 'canopy':
            for k in ('up', 'front', 'side_a', 'side_b'):
                x0, y0, x1, y1 = f[k]
                for x in range(x0, x1):
                    sk.px(x, y0, (60, 140, 220), lit=True)
        elif name == 'wing':
            # 翼の先の青い帯と、前の縁の明るい線
            x0, y0, x1, y1 = f['up']
            for y in range(y0, y1):
                sk.px(x0, y, JET_BLUE, lit=True)
                sk.px(x1 - 1, y, JET_BLUE, lit=True)
            for x in range(x0, x1):
                sk.px(x, y0, sk.shade(shell, 30))
            sk.fill(f['front'], sk.shade(shell, 24), bevel=False)
        elif name == 'nacelle':
            # 吸気口（前）は暗く、胴の帯は青
            sk.fill(f['front'], (18, 20, 24), bevel=False)
            for k in ('side_a', 'side_b', 'up'):
                x0, y0, x1, y1 = f[k]
                for x in range(x0, x1):
                    sk.px(x, y1 - 2, JET_BLUE, lit=True)
        elif name == 'nozzle':
            x0, y0, x1, y1 = f['back']
            sk.lit((x0, y0, x1, y1), (150, 200, 255))
            sk.lit((x0 + 1, y0 + 1, x1 - 1, y1 - 1), JET_HOT)
        elif name == 'fin':
            for k in ('side_a', 'side_b'):
                x0, y0, x1, y1 = f[k]
                for y in range(y0, y1):
                    sk.px(x1 - 1, y, JET_BLUE, lit=True)
        elif name == 'claw':
            for k in ('front', 'back', 'side_a', 'side_b'):
                x0, y0, x1, y1 = f[k]
                sk.px(x0, y1 - 1, JET_BLUE, lit=True)
    save(sk.base, te / 'cargo_drone_jet.png')
    save(sk.glow, te / 'cargo_drone_jet_glow.png')


def gravity_textures(te, save):
    """反重力型: 暗い菫色の結晶（芯だけ光る）、真珠色の光輪とビット。光るのは芯・光輪の外側・ビットの先・放射板の真ん中だけ。"""
    sk = _Skin(37)
    crystal, pearl, pearl_sh = (44, 30, 70), (226, 230, 238), (190, 196, 210)
    violet = (120, 90, 200)
    cols = {'crystal': crystal, 'pearl': pearl, 'dark': (34, 30, 46), 'tip': TEAL}
    for name, (u, v, w, h, dd, mat) in GRAVITY_PARTS.items():
        f = _faces(u, v, w, h, dd)
        for k, r in f.items():
            sk.fill(r, pearl_sh if mat == 'pearl' and k in ('side_a', 'side_b', 'down') else cols[mat], noise=6 if mat == 'crystal' else 4)
        if name == 'gem':
            # 面ごとに: 縁は少し明るい菫の線（光らない）、真ん中の芯だけが青緑に光る
            for x0, y0, x1, y1 in f.values():
                for x in range(x0, x1):
                    sk.px(x, y0, violet)
                    sk.px(x, y1 - 1, violet)
                for y in range(y0, y1):
                    sk.px(x0, y, violet)
                    sk.px(x1 - 1, y, violet)
                cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
                sk.px(cx, cy, TEAL_HI, lit=True)
        elif name == 'seg':
            # 光輪は外側の面の真ん中だけ光る
            x0, y0, x1, y1 = f['back']
            for x in range(x0 + 1, x1 - 1):
                sk.px(x, y0, TEAL, lit=True)
        elif name == 'emitter':
            # 放射板は下の面の真ん中だけ光り、縁は暗い青緑
            x0, y0, x1, y1 = f['down']
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if x in (x0, x1 - 1) or y in (y0, y1 - 1):
                        sk.px(x, y, TEAL_DIM)
            sk.lit((x0 + 1, y0 + 1, x1 - 1, y1 - 1), TEAL)
        elif name == 'spine':
            sk.lit(f['up'], TEAL)
        elif name == 'pod':
            # 白い殻の胴を回る暗い青緑の帯（光らない）と、下の縁の影
            for k in ('front', 'back', 'side_a', 'side_b'):
                x0, y0, x1, y1 = f[k]
                for x in range(x0, x1):
                    sk.px(x, y0 + 2, TEAL_DIM)
                    sk.px(x, y1 - 1, sk.shade(pearl_sh, -30))
        elif name == 'tip':
            # 先は下の面だけ光る（横は光らない青緑）
            for r in f.values():
                sk.fill(r, TEAL_DIM, bevel=False, noise=2)
            sk.lit(f['down'], TEAL)
        elif name == 'plate':
            for k in ('back', 'front'):
                x0, y0, x1, y1 = f[k]
                for x in range(x0, x1):
                    sk.px(x, y0, violet)
    save(sk.base, te / 'cargo_drone_gravity.png')
    save(sk.glow, te / 'cargo_drone_gravity_glow.png')


# ---------------------------------------------------------------- まとめ

def textures(tb, ti, save, write_json):
    save(art16_sheet([station_top(k) for k in range(ANIM_FRAMES)]), tb / 'drone_station_top.png')
    write_json(tb / 'drone_station_top.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
    save(port_top(), tb / 'drone_port_top.png')
    save(hatch(), tb / 'drone_port_hatch.png')
    save(platform(), tb / 'drone_port_platform.png')
    for iid, (crystal, mark) in MEMORY_KINDS.items():
        save(memory_icon(crystal, mark), ti / f'{iid}.png')
    save(kit_icon(), ti / 'drone_kit.png')
    save(phase_icon(), ti / 'drone_phase_upgrade.png')
    for tier, iid in enumerate(CRATES, 1):
        save(crate_side(tier), tb / f'{iid}_side.png')
        save(crate_top(tier), tb / f'{iid}_top.png')


def art16_sheet(frames):
    sheet = Image.new('RGBA', (S, S * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, S * i))
    return sheet


def models(write_json, assets):
    """梱包箱は 10/16 の立方体（手に持っても、ドローンに吊るしても、台に載せても同じ形）。"""
    for iid in CRATES:
        side = f'{MODID}:block/{iid}_side'
        top = f'{MODID}:block/{iid}_top'
        face = lambda tex: {'texture': tex, 'uv': [3, 3, 13, 13]}
        write_json(assets / 'models' / 'item' / f'{iid}.json', {
            'parent': 'minecraft:block/block',
            'textures': {'side': side, 'top': top, 'particle': side},
            'elements': [{'from': [3, 3, 3], 'to': [13, 13, 13], 'faces': {
                'north': face('#side'), 'south': face('#side'), 'east': face('#side'), 'west': face('#side'),
                'up': face('#top'), 'down': face('#top')}}],
        })
