# -*- coding: utf-8 -*-
"""材質の絵（16×16）を描く補助。のっぺりしないよう、模様と「汚し」（使い込んだ金属の色むら・継ぎ目・鋲・縁の擦れ・研磨の筋）を入れる。
汚れ（しみ・泥）は描かない。乱数は位置と種から決める（毎回同じ絵）。

モデルの面は UV を指定せず、ブロックの座標から決まる既定の UV で貼る前提（1ドット＝1/16ブロック。どの面も同じ細かさ）。
そのため模様は絵のどこを切り取っても破綻しないよう、全面に散らすか、決まった格子（8ドットごとの継ぎ目など）に置く。
"""
import zlib

from PIL import Image


def _h(x, y, seed, salt=0):
    """位置と種から 0〜1 の決まった値。"""
    return (zlib.crc32(f'{seed}:{salt}:{x}:{y}'.encode()) & 0xFFFF) / 0xFFFF


def _mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _shade(c, k):
    """k > 0 で明るく、k < 0 で暗く。"""
    return _mix(c, (255, 255, 255), k) if k > 0 else _mix(c, (0, 0, 0), -k)


def metal(base, seed, alpha=255, grain=0.05, streak='h', seams=(), seam_axis='h', rivets=(), scuffs=4, edge=0.08):
    """使い込んだ金属の板。
    grain: 色むらの強さ。streak: 研磨の筋の向き（'h' 横、'v' 縦、None なし）。seams: 継ぎ目の行（または列）。
    rivets: 鋲の位置。scuffs: 縁や面の小さな擦れ（明るい短い線）の数。edge: 絵の縁（0 と 15）をわずかに明るく（擦れ）。"""
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            k = (_h(x, y, seed) - 0.5) * 2 * grain
            if streak == 'h':
                k += (_h(0, y, seed, 1) - 0.5) * grain
            elif streak == 'v':
                k += (_h(x, 0, seed, 1) - 0.5) * grain
            # ゆるい色むら（4ドットの塊）
            k += (_h(x // 4, y // 4, seed, 2) - 0.5) * grain * 0.8
            if x in (0, 15) or y in (0, 15):
                k += edge * (0.4 + 0.6 * _h(x, y, seed, 3))
            px[x, y] = _shade(base, k) + (alpha,)
    for s in seams:
        for i in range(16):
            x, y = (i, s) if seam_axis == 'h' else (s, i)
            c = px[x, y]
            px[x, y] = _shade(c[:3], -0.28) + (c[3],)
            nx, ny = (x, (y + 1) % 16) if seam_axis == 'h' else ((x + 1) % 16, y)
            c = px[nx, ny]
            px[nx, ny] = _shade(c[:3], 0.12) + (c[3],)
    for i in range(scuffs):
        x, y = int(_h(i, 0, seed, 4) * 14) + 1, int(_h(i, 1, seed, 4) * 14) + 1
        length = 1 + int(_h(i, 2, seed, 4) * 3)
        horizontal = _h(i, 3, seed, 4) < 0.6
        for j in range(length):
            xx, yy = (min(15, x + j), y) if horizontal else (x, min(15, y + j))
            c = px[xx, yy]
            px[xx, yy] = _shade(c[:3], 0.14) + (c[3],)
    for x, y in rivets:
        c = px[x, y]
        px[x, y] = _shade(c[:3], 0.35) + (c[3],)
        if x + 1 < 16 and y + 1 < 16:
            c = px[x + 1, y + 1]
            px[x + 1, y + 1] = _shade(c[:3], -0.3) + (c[3],)
    return im


def glass(tint, seed, alpha=60, streaks=2):
    """うすいガラス: 色むらはほとんどなく、斜めの映り込みの筋と、まれな小さな傷。"""
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            a = alpha + int((_h(x, y, seed) - 0.5) * 10)
            px[x, y] = tint + (max(0, a),)
    for s in range(streaks):
        off = int(_h(s, 0, seed, 5) * 16)
        for i in range(16):
            x, y = (i + off) % 16, (i * 1) % 16
            if (x + y) % 2 == 0 or s == 0:
                px[x, (15 - y)] = (255, 255, 255, min(255, alpha + 70 - s * 30))
    for i in range(3):
        x, y = int(_h(i, 0, seed, 6) * 16), int(_h(i, 1, seed, 6) * 16)
        px[x, y] = (240, 246, 255, alpha + 40)
    return im


def glow(color, seed, core=(255, 255, 255)):
    """光る面（灯・レンズ）: 中ほどが明るく、細かいまだら。"""
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 / 10.6
            k = max(0.0, 0.55 - d) + (_h(x, y, seed) - 0.5) * 0.12
            px[x, y] = _mix(color, core, max(0.0, min(1.0, k))) + (255,)
    return im


def panels(base, seed, size=8, rivets=True, grain=0.06):
    """四角い板を並べた金属（研磨の筋なし。木目に見えないように）: 板ごとにわずかに色が違い、面取りの明るい上辺・左辺と
    暗い下辺・右辺、板の角の近くに鋲。色むらと小さな擦れも入れる。"""
    im = metal(base, seed, grain=grain, streak=None, scuffs=4, edge=0)
    px = im.load()
    for y in range(16):
        for x in range(16):
            u, v = x % size, y % size
            c = px[x, y][:3]
            k = (_h(x // size, y // size, seed, 7) - 0.5) * 0.08          # 板ごとの色の違い
            if v == 0 or u == 0:
                k += 0.16
            elif v == size - 1 or u == size - 1:
                k -= 0.22
            px[x, y] = _shade(c, k) + (255,)
    if rivets:
        for py in range(0, 16, size):
            for px0 in range(0, 16, size):
                for dx, dy in ((2, 2), (size - 3, size - 3)):
                    x, y = px0 + dx, py + dy
                    c = px[x, y][:3]
                    px[x, y] = _shade(c, 0.32) + (255,)
                    c = px[x + 1, y + 1][:3] if x + 1 < 16 and y + 1 < 16 else None
                    if c is not None:
                        px[x + 1, y + 1] = _shade(c, -0.28) + (255,)
    return im
