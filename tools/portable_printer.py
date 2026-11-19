# -*- coding: utf-8 -*-
"""0.21.0: 携帯型アンドロイド印刷機の絵。

アイテム（16×16）: 黒鉛色のカバンを立てた正面。黒い取っ手、白い角当て、真ん中の継ぎ目（ここからパカっと割れる）が水色に光る。
アンドロイドキット（白い箱）と見分けるため、外装は黒鉛色にする。輪郭を含めて14×14に収め、上下左右の真ん中に置く。

地面に置いたカバン（64×64、Java の PortablePrinterRenderer の割り付け）: 殻 (0,0) 14×2×10、ふた (0,12) 14×2×10、
取っ手 (48,0) 4×1×1、印刷面 (0,24) 12×1×8。殻の上面とふたの下面は内側（開いたときに見える濃い紺に水色の縁）、
印刷面は白に水色の格子。殻とふたの側面の、継ぎ目の側の段は水色に光る。
"""
from PIL import Image, ImageDraw

GRAPH, GRAPH_HI, GRAPH_LO = (70, 76, 92), (100, 108, 126), (48, 52, 64)
WHITE, SHADE = (238, 240, 244), (200, 206, 216)
CYAN, CYAN_LO = (120, 225, 255), (70, 160, 200)
INNER, INNER_LINE = (32, 36, 50), (60, 110, 150)
HANDLE = (30, 32, 40)
# 継ぎ目と印の色（Mk1 は水色、Mk2 は段階3の紫）
MK1 = (CYAN, CYAN_LO)
MK2 = ((176, 140, 255), (110, 84, 190))


def rgba(c, a=255):
    return tuple(c[:3]) + (a,)


def icon(tone=MK1):
    """アイテムの絵。Mk2 は継ぎ目が紫で、表示灯が3つ（3組を積める）。"""
    glow, glow_lo = tone
    import art16
    from art16 import outlined, SOFT_EDGE

    def f(d):
        d.rectangle([5, 2, 10, 2], fill=rgba(HANDLE))                     # 取っ手
        d.point((5, 3), fill=rgba(HANDLE))
        d.point((10, 3), fill=rgba(HANDLE))
        d.rectangle([2, 4, 13, 13], fill=rgba(GRAPH))                     # カバン
        d.line([(2, 4), (13, 4)], fill=rgba(GRAPH_HI))
        d.line([(2, 13), (13, 13)], fill=rgba(GRAPH_LO))
        d.line([(2, 8), (13, 8)], fill=rgba(glow))                        # 真ん中の継ぎ目
        d.line([(2, 9), (13, 9)], fill=rgba(glow_lo))
        for x, y in ((2, 4), (12, 4), (2, 12), (12, 12)):                # 白い角当て（2×2）
            d.rectangle([x, y, x + 1, y + 1], fill=rgba(WHITE))
        if tone is MK1:
            d.point((7, 6), fill=rgba(glow))                              # 表示灯
            d.point((8, 6), fill=rgba(WHITE))
        else:
            for x in (5, 7, 9):                                           # 3組の表示灯
                d.point((x, 6), fill=rgba(glow))
    im = outlined(f, **SOFT_EDGE)
    for x in range(6, 10):                                                # 取っ手の穴（輪郭で埋まる）を抜く
        im.putpixel((x, 3), (0, 0, 0, 0))
    box = im.getbbox()
    w, h = box[2] - box[0], box[3] - box[1]
    assert w <= 14 and h <= 14, (w, h)
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    out.paste(im.crop(box), ((16 - w) // 2, (16 - h) // 2))
    return out


def _faces(u, v, w, h, d):
    """Minecraft の箱の割り付け: 面の名前 → (u, v, 幅, 高さ)。up は見た目の上（ふたの外・殻の内）。"""
    return {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }


def _rect(d, r, c):
    u, v, w, h = r
    d.rectangle([u, v, u + w - 1, v + h - 1], fill=rgba(c))


def _inner(d, r):
    """内側の面: 濃い紺に水色の縁と、細い格子。"""
    u, v, w, h = r
    _rect(d, r, INNER)
    d.rectangle([u, v, u + w - 1, v + h - 1], outline=rgba(INNER_LINE))
    for x in range(u + 3, u + w - 1, 3):
        d.line([(x, v + 1), (x, v + h - 2)], fill=rgba((44, 54, 76)))


def entity_texture(tone=MK1):
    """地面に置いたカバンの絵（64×64）。Mk2 は継ぎ目とふたの印が紫。"""
    glow = tone[0]
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # 殻（下）: 外は黒鉛色、側面の上の段（継ぎ目）が水色、上面は内側
    base = _faces(0, 0, 14, 2, 10)
    for name in ('east', 'north', 'west', 'south'):
        u, v, w, h = base[name]
        _rect(d, base[name], GRAPH)
        d.line([(u, v), (u + w - 1, v)], fill=rgba(glow))
        d.point((u, v + 1), fill=rgba(WHITE))
        d.point((u + w - 1, v + 1), fill=rgba(WHITE))
    _rect(d, base['down'], GRAPH_LO)
    _inner(d, base['up'])
    # ふた: 外は黒鉛色（縁を明るく、四隅に白い角当て、真ん中に水色の印）、側面の下の段（継ぎ目）が水色、下面は内側
    lid = _faces(0, 12, 14, 2, 10)
    for name in ('east', 'north', 'west', 'south'):
        u, v, w, h = lid[name]
        _rect(d, lid[name], GRAPH)
        d.line([(u, v + h - 1), (u + w - 1, v + h - 1)], fill=rgba(glow))
        d.point((u, v), fill=rgba(WHITE))
        d.point((u + w - 1, v), fill=rgba(WHITE))
    u, v, w, h = lid['up']
    _rect(d, lid['up'], GRAPH)
    d.rectangle([u, v, u + w - 1, v + h - 1], outline=rgba(GRAPH_HI))
    for x, y in ((u, v), (u + w - 2, v), (u, v + h - 2), (u + w - 2, v + h - 2)):
        d.rectangle([x, y, x + 1, y + 1], fill=rgba(WHITE))
    d.rectangle([u + w // 2 - 1, v + h // 2 - 1, u + w // 2, v + h // 2], fill=rgba(glow))
    _inner(d, lid['down'])
    # 取っ手
    for r in _faces(48, 0, 4, 1, 1).values():
        _rect(d, r, HANDLE)
    # 印刷面: 白に水色の格子、側面は陰
    bed = _faces(0, 24, 12, 1, 8)
    u, v, w, h = bed['up']
    _rect(d, bed['up'], WHITE)
    for x in range(u + 1, u + w, 3):
        d.line([(x, v), (x, v + h - 1)], fill=rgba((176, 226, 246)))
    for y in range(v + 1, v + h, 3):
        d.line([(u, y), (u + w - 1, y)], fill=rgba((176, 226, 246)))
    d.rectangle([u, v, u + w - 1, v + h - 1], outline=rgba(CYAN))
    for name in ('east', 'north', 'west', 'south', 'down'):
        _rect(d, bed[name], SHADE)
    return im


BED_FRAMES = 8


def bed_frame(k):
    """印刷面の上面の1コマ（16×16 のうち、真ん中の 12×8 = x 2〜13・y 4〜11 だけを使う。Java の PortablePrinterRenderer が貼る）。
    組立ステーションの上面と同じ動き: 濃い印刷床（印刷面いっぱい）に水色の縁と格子。光の走査線が長い向きに往復し、
    縁の四隅の吐出口が順に光る。"""
    import math
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    x0, y0, x1, y1 = 2, 4, 13, 11
    t = k / BED_FRAMES
    d.rectangle([x0, y0, x1, y1], fill=(30, 36, 46, 255), outline=(120, 225, 255, 255))
    for g in (x0 + 4, x0 + 7):
        d.line([(g, y0 + 1), (g, y1 - 1)], fill=(44, 72, 88, 255))
    for g in (y0 + 3, y0 + 5):
        d.line([(x0 + 1, g), (x1 - 1, g)], fill=(44, 72, 88, 255))
    sweep = x0 + 1 + round((x1 - x0 - 2) * (0.5 - 0.5 * math.cos(2 * math.pi * t)))
    d.line([(sweep, y0 + 1), (sweep, y1 - 1)], fill=(190, 245, 255, 255))
    for x in (sweep - 1, sweep + 1):
        if x0 + 1 <= x <= x1 - 1:
            d.line([(x, y0 + 1), (x, y1 - 1)], fill=(80, 160, 190, 255))
    for i, (x, y) in enumerate(((x0, y0), (x1, y0), (x1, y1), (x0, y1))):
        on = (i + k // 2) % 4 == 0
        d.point((x, y), fill=(220, 250, 255, 255) if on else (70, 140, 170, 255))
    return im


def bed_sheet():
    """印刷面のアニメーション（縦に並べた {BED_FRAMES} コマ）。"""
    sheet = Image.new('RGBA', (16, 16 * BED_FRAMES), (0, 0, 0, 0))
    for k in range(BED_FRAMES):
        sheet.paste(bed_frame(k), (0, 16 * k))
    return sheet
