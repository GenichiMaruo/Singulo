# -*- coding: utf-8 -*-
"""地下アンドロイド研究所のブロックの絵・モデル・落とす物（gen_data.py から呼ぶ。ids.py の種類 'lab'）。

研究所は崩れた廃墟ではなく、アンドロイドが今も直し続けている白い施設。どの絵も、汚れのない新品の工業製品らしくする
（朽ちた遺構パネルの汚しは入れない）。電力で見た目が変わるものは、止まっている形（off）と動いている形（on）の2枚を持つ。

- 白いパネル・帯入りパネル・床タイル・構造材（暗い鋼）・ガラス
- 施設灯（電力で白く光る）・非常灯（電力がない間だけ赤く光る）・炉心（電力で水色に強く光る）
- 耐圧殻（暗い鋼の厚板。ボスを倒すまで壊せない）・隔壁（黄と黒の警告の縞。電力が戻ると開く）
- 動力炉の制御盤（正面の画面: 止まっている間は赤い OFFLINE、動くと緑）・サーバーラック（動くと灯が瞬く）
- 記録端末（緑の文字の画面）・素体カプセル（ガラスの筒の中に、白いアンドロイドの素体）
"""
import json

import zlib

from blocks_extra import MODID, S, grime, mix, new, rgba

WHITE, SEAM, SHADE = (244, 246, 249), (206, 212, 220), (226, 230, 236)
BAND = (92, 132, 168)
STEEL, STEEL_DARK, STEEL_HI = (70, 76, 88), (40, 44, 52), (120, 128, 142)
RED, AMBER, GREEN, CYAN = (255, 64, 52), (255, 176, 64), (96, 236, 140), (120, 230, 255)
RACK_FRAMES = 8


# ---------------------------------------------------------------- 絵

def _bevel(d, a=1):
    b = S - 1 - a
    d.line([(a, a), (b, a)], fill=(255, 255, 255, 255))
    d.line([(a, a), (a, b)], fill=(255, 255, 255, 255))
    d.line([(a, b), (b, b)], fill=rgba(SHADE))
    d.line([(b, a), (b, b)], fill=rgba(SHADE))


def lab_panel():
    """白いパネル: 面取りの縁と、四隅の小さな留め具。"""
    im, d = new(rgba(WHITE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(SEAM))
    _bevel(d)
    d.point([(2, 2), (13, 2), (2, 13), (13, 13)], fill=rgba(SEAM))
    return im


def lab_panel_lined():
    """帯入りのパネル: 白いパネルの真ん中に、青灰色の帯と細い白線。"""
    im = lab_panel()
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    d.rectangle([0, 6, S - 1, 9], fill=rgba(BAND))
    d.line([(0, 7), (S - 1, 7)], fill=rgba(mix(BAND, (255, 255, 255), 0.35)))
    return im


def lab_tiles():
    """床タイル: 4枚の淡い灰色のタイルと、目地。"""
    im, d = new(rgba((232, 235, 240)))
    for ox in (0, 8):
        for oy in (0, 8):
            d.rectangle([ox, oy, ox + 7, oy + 7], outline=rgba((196, 202, 212)))
            d.line([(ox + 1, oy + 1), (ox + 6, oy + 1)], fill=(250, 251, 253, 255))
    return im


def power_conduit(on):
    """動力導管: 暗い鋼の管に、縦の筋。止まっていると鈍い青、動いていると明るい水色に光る。"""
    im, d = new(rgba(STEEL_DARK))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL))
    glow = CYAN if on else (44, 66, 86)
    d.rectangle([5, 0, 10, S - 1], fill=rgba(glow))
    d.line([(7, 0), (7, S - 1)], fill=(235, 252, 255, 255) if on else rgba((60, 84, 104)))
    for y in (3, 12):
        d.line([(3, y), (12, y)], fill=rgba(STEEL_HI))
    return im


def lab_frame():
    """構造材: 暗い鋼に、縁の明るい筋とボルト。"""
    im, d = new(rgba(STEEL))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL_DARK))
    d.line([(1, 1), (14, 1)], fill=rgba(STEEL_HI))
    d.line([(1, 1), (1, 14)], fill=rgba(STEEL_HI))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point((x, y), fill=rgba(STEEL_HI))
    return im


def lab_glass():
    """ガラス: ほとんど透明で、縁だけ白い枠。左上に映り込みの筋。"""
    im, d = new((214, 234, 246, 52))
    d.rectangle([0, 0, S - 1, S - 1], outline=(236, 240, 246, 230))
    d.line([(3, 6), (6, 3)], fill=(255, 255, 255, 140))
    d.line([(3, 9), (9, 3)], fill=(255, 255, 255, 90))
    return im


def facility_light(on):
    """施設灯: 白い枠の中の、細長い拡散板。止まっていると灰色、動いていると白く光る。"""
    im, d = new(rgba(WHITE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(SEAM))
    c = (255, 255, 250) if on else (150, 156, 166)
    d.rectangle([2, 2, 13, 13], fill=rgba(c))
    if on:
        d.rectangle([4, 4, 11, 11], fill=(255, 255, 255, 255))
    else:
        d.line([(2, 7), (13, 7)], fill=rgba((132, 138, 148)))
    return im


def emergency_light(on):
    """非常灯: 鋼の枠に赤い灯（電力がない間は光る）。"""
    im, d = new(rgba(STEEL))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL_DARK))
    c = RED if on else (96, 40, 40)
    d.rectangle([3, 3, 12, 12], fill=rgba(c))
    if on:
        d.rectangle([5, 5, 10, 10], fill=rgba((255, 160, 140)))
    return im


def pressure_hull():
    """耐圧殻: 暗い鋼の厚板。斜めの補強の筋と、大きなリベット。"""
    im, d = new(rgba((52, 56, 64)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba((28, 30, 36)))
    for k in range(-S, S, 6):
        d.line([(k, 0), (k + S, S)], fill=rgba((58, 62, 72)))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (7, 7)):
        d.rectangle([x, y, x + 1, y + 1], fill=rgba((92, 98, 110)))
    return im


def bulkhead():
    """隔壁: 黄と黒の斜めの警告の縞と、真ん中の継ぎ目。"""
    im, d = new(rgba((240, 196, 40)))
    for k in range(-S, 2 * S, 6):
        d.polygon([(k, 0), (k + 3, 0), (k + 3 - S, S), (k - S, S)], fill=rgba((30, 30, 34)))
    d.line([(7, 0), (7, S - 1)], fill=rgba(STEEL_DARK))
    d.line([(8, 0), (8, S - 1)], fill=rgba(STEEL_HI))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL_DARK))
    return im


def machine_side():
    """機械の側面（制御盤・ラック・端末）: 白い外装と通気口。"""
    im, d = new(rgba(WHITE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(SEAM))
    for y in (4, 6, 8, 10):
        d.line([(4, y), (11, y)], fill=rgba(SHADE))
    return im


def machine_top():
    im, d = new(rgba(WHITE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(SEAM))
    d.rectangle([3, 3, 12, 12], outline=rgba(SHADE))
    return im


def console_front(on):
    """制御盤の正面: 黒い画面に、止まっていれば赤い横線（OFFLINE）、動いていれば緑の波形。下に操作の並び。"""
    im, d = new(rgba(WHITE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(SEAM))
    d.rectangle([2, 2, 13, 8], fill=rgba((16, 20, 24)))
    if on:
        pts = [(3, 6), (5, 4), (7, 6), (9, 3), (11, 6), (12, 5)]
        d.line(pts, fill=rgba(GREEN))
    else:
        d.line([(4, 5), (11, 5)], fill=rgba(RED))
        d.point([(3, 3), (12, 3)], fill=rgba(RED))
    for x, c in ((3, AMBER), (6, GREEN if on else (60, 70, 60)), (9, RED if not on else (70, 50, 50)), (12, CYAN)):
        d.rectangle([x - 1, 11, x, 12], fill=rgba(c))
    return im


def reactor_core(on):
    """炉心: 鋼の格子の奥に光る芯。止まっていると暗い青、動いていると明るい水色。"""
    im, d = new(rgba(STEEL_DARK))
    glow = CYAN if on else (40, 70, 90)
    d.rectangle([2, 0, 13, S - 1], fill=rgba(glow))
    if on:
        d.rectangle([5, 0, 10, S - 1], fill=(230, 252, 255, 255))
    for y in range(1, S, 4):
        d.line([(1, y), (14, y)], fill=rgba(STEEL))
    d.line([(1, 0), (1, S - 1)], fill=rgba(STEEL))
    d.line([(14, 0), (14, S - 1)], fill=rgba(STEEL))
    return im


def rack_front(on, k=0):
    """サーバーラックの正面: 暗い引き出しが並び、それぞれに小さな灯。動いている間はコマごとに瞬く。"""
    im, d = new(rgba((34, 38, 46)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL))
    import random
    rng = random.Random(1000 + k)
    for i, y in enumerate(range(2, 14, 3)):
        d.rectangle([2, y, 13, y + 1], fill=rgba((52, 58, 68)))
        for j, x in enumerate((10, 12)):
            if on:
                lit = rng.random() < 0.6
                c = (GREEN if j == 0 else AMBER) if lit else (40, 60, 44)
            else:
                c = (40, 46, 50)
            d.point((x, y), fill=rgba(c))
    return im


def terminal_front():
    """記録端末の画面: 黒い画面に緑の文字の行。"""
    im, d = new(rgba(WHITE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(SEAM))
    d.rectangle([2, 2, 13, 10], fill=rgba((10, 18, 14)))
    for y, w in ((3, 9), (5, 7), (7, 10), (9, 5)):
        d.line([(3, y), (3 + w, y)], fill=rgba(GREEN))
    d.rectangle([5, 12, 10, 13], fill=rgba(SHADE))
    return im


def monitor_screen():
    """机のモニターの画面: 暗い画面に、水色のグラフと文字の行。"""
    im, d = new(rgba((12, 20, 26)))
    d.line([(2, 10), (5, 7), (8, 9), (13, 4)], fill=rgba(CYAN))
    for y, w in ((2, 6), (4, 4)):
        d.line([(2, y), (2 + w, y)], fill=rgba(GREEN))
    return im


def plastic(base=(232, 235, 240)):
    """白い樹脂の外装（小物の地）。"""
    im, d = new(rgba(base))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(SEAM))
    return im


def coffee_front():
    """コーヒーメーカーの正面: 黒い注ぎ口と、赤い灯、ボタン。"""
    im, d = new(rgba((52, 56, 64)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL_DARK))
    d.rectangle([5, 2, 10, 4], fill=rgba((24, 26, 30)))
    d.point([(4, 7), (11, 7)], fill=rgba(RED))
    d.point([(6, 7), (8, 7)], fill=rgba((200, 204, 210)))
    return im


def coffee_pot():
    """コーヒーのポット（ガラスの器）。テクスチャを 1:1 で割り付ける（モデルの UV と合わせる）。

    [0,0]-[6,6] 正面・背面（6x6）、[6,0]-[10,6] 側面（4x6）、[0,6]-[6,10] 上（蓋）、[6,6]-[12,10] 底、
    [12,0]-[14,4] 取っ手。上1段は黒い襟、その下は空のガラス、下半分はコーヒー。左寄りに縦の光。
    """
    im, d = new(rgba((40, 40, 46)))
    glass, coffee, coffee_hi = (176, 196, 208), (82, 48, 30), (112, 70, 44)
    for x0, w in ((0, 6), (6, 4)):
        d.rectangle([x0, 0, x0 + w - 1, 0], fill=rgba((34, 34, 40)))          # 黒い襟
        d.rectangle([x0, 1, x0 + w - 1, 2], fill=rgba(glass))               # 空のガラス
        d.rectangle([x0, 3, x0 + w - 1, 5], fill=rgba(coffee))              # コーヒー
        d.line([(x0, 3), (x0 + w - 1, 3)], fill=rgba(coffee_hi))            # 液面
        d.line([(x0 + 1, 1), (x0 + 1, 5)], fill=(236, 244, 250, 255))       # ガラスの光
        d.point([(x0, 1), (x0, 2), (x0 + w - 1, 1), (x0 + w - 1, 2)], fill=rgba(mix(glass, (90, 100, 110), 0.5)))
    d.rectangle([0, 6, 5, 9], fill=rgba((34, 34, 40)))                      # 蓋
    d.rectangle([2, 7, 3, 8], fill=rgba((70, 72, 80)))                      # 蓋のつまみ
    d.rectangle([6, 6, 11, 9], fill=rgba(coffee))                           # 底（ガラス越しのコーヒー）
    d.rectangle([12, 0, 13, 3], fill=rgba((34, 34, 40)))                    # 取っ手
    return im


def vending_front(upper):
    """自販機の正面。上半分: 光る見本の棚（色とりどりの缶）。下半分: 押しボタンと取り出し口。"""
    im, d = new(rgba((236, 76, 70)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba((150, 40, 36)))
    if upper:
        d.rectangle([2, 2, 13, 14], fill=rgba((236, 244, 250)))
        cans = [(255, 80, 70), (80, 160, 255), (250, 210, 70), (90, 210, 120), (240, 240, 240), (180, 110, 220)]
        for row, y in enumerate((3, 7, 11)):
            for k, x in enumerate((3, 6, 9, 12)):
                c = cans[(row * 4 + k) % len(cans)]
                d.rectangle([x, y, x, y + 2], fill=rgba(c))
    else:
        for k, x in enumerate((3, 6, 9, 12)):
            d.point((x, 2), fill=rgba((255, 230, 120)))
        d.rectangle([2, 4, 9, 6], fill=rgba((30, 30, 34)))           # お金の口・表示
        d.rectangle([2, 10, 13, 13], fill=rgba((24, 24, 28)))         # 取り出し口
        d.rectangle([11, 4, 13, 7], fill=rgba((210, 210, 214)))
    return im


def vending_glow(upper):
    """自販機の光る所（見本の棚と、ボタンの灯）。"""
    im = vending_front(upper)
    rects = [(2, 2, 13, 14)] if upper else [(3, 2, 12, 2)]
    return glow_layer(im, rects)


def paper():
    """書類: 白い紙の重なりと、文字の行。"""
    im, d = new(rgba((246, 246, 242)))
    for y in range(2, S, 3):
        d.line([(2, y), (11, y)], fill=rgba((176, 180, 190)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba((214, 214, 208)))
    return im


def paper_side():
    im, d = new(rgba((240, 240, 234)))
    for y in range(0, S, 2):
        d.line([(0, y), (S - 1, y)], fill=rgba((208, 208, 202)))
    return im


def bin_side():
    """ごみ箱の側面: 灰色の樹脂に縦の溝、上の縁（上3段。モデルは上端を縁に合わせる）。"""
    im, d = new(rgba((150, 156, 166)))
    for x in range(1, S, 3):
        d.line([(x, 3), (x, S - 1)], fill=rgba((130, 136, 146)))
    d.rectangle([0, 0, S - 1, 2], fill=rgba((110, 116, 126)))
    d.line([(0, 0), (S - 1, 0)], fill=rgba((168, 174, 184)))
    return im


def bin_inner():
    """ごみ箱の内側の壁: 上ほど明るく、底へ向かって暗くなる（深さが見えるように）。上端は縁の色。"""
    im, d = new()
    top, bottom = (104, 110, 120), (34, 37, 43)
    for y in range(S):
        d.line([(0, y), (S - 1, y)], fill=rgba(mix(top, bottom, min(1.0, y / 9))))
    d.line([(0, 0), (S - 1, 0)], fill=rgba((110, 116, 126)))
    return im


def bin_bottom():
    """ごみ箱の底: 暗い樹脂に、丸めた紙くずが少し。"""
    im, d = new(rgba((30, 33, 38)))
    d.rectangle([1, 1, 3, 2], fill=rgba((214, 216, 220)))
    d.point([(2, 3), (1, 2)], fill=rgba((180, 184, 190)))
    d.rectangle([4, 4, 5, 5], fill=rgba((196, 200, 206)))
    d.point([(5, 4)], fill=rgba((232, 234, 238)))
    return im


def capsule_glass():
    im, d = new((206, 232, 250, 78))
    d.line([(2, 0), (2, S - 1)], fill=(255, 255, 255, 150))
    d.line([(4, 0), (4, S - 1)], fill=(255, 255, 255, 80))
    return im


def capsule_body():
    """素体: つるりとした白と、関節の淡い灰色の継ぎ目。"""
    im, d = new(rgba((250, 250, 252)))
    for y in (5, 10):
        d.line([(0, y), (S - 1, y)], fill=rgba((214, 220, 228)))
    d.point([(7, 3), (8, 3)], fill=rgba(CYAN))
    return im


# 画面のある絵で、光らせる所（x0, y0, x1, y1。両端を含む）
CONSOLE_SCREEN = [(2, 2, 13, 8), (2, 11, 12, 12)]
TERMINAL_SCREEN = [(2, 2, 13, 10)]
RACK_LEDS = [(x, y, x, y) for x in (10, 12) for y in (2, 5, 8, 11)]


def glow_layer(im, rects):
    """im のうち rects の中の画素だけを残した絵（ほかは透明）。前の面に重ねて、画面だけを光らせる。"""
    out = im.copy()
    px = out.load()
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            if not any(x0 <= x <= x1 and y0 <= y % S <= y1 for x0, y0, x1, y1 in rects):
                px[x, y] = (0, 0, 0, 0)
    return out


def textures(tb, save_plain):
    from PIL import Image

    def seed(name):
        return zlib.crc32(name.encode())

    def save(im, path):
        """どの絵にも使用感の汚しを入れる（アニメーションの絵は、コマごとに同じ汚しを入れてから並べる）。"""
        if im.size[1] == im.size[0]:
            grime(im, seed(path.name))
        save_plain(im, path)

    save(lab_panel(), tb / 'lab_panel.png')
    save(lab_panel_lined(), tb / 'lab_panel_lined.png')
    save(lab_tiles(), tb / 'lab_tiles.png')
    save(lab_frame(), tb / 'lab_frame.png')
    save(lab_glass(), tb / 'lab_glass.png')
    for on in (False, True):
        tag = 'on' if on else 'off'
        save(facility_light(on), tb / f'facility_light_{tag}.png')
        save(emergency_light(on), tb / f'emergency_light_{tag}.png')
        front = console_front(on)
        save(front, tb / f'reactor_console_front_{tag}.png')
        save_plain(glow_layer(front, CONSOLE_SCREEN), tb / f'reactor_console_front_{tag}_glow.png')
        save(reactor_core(on), tb / f'reactor_core_{tag}.png')
        save(power_conduit(on), tb / f'power_conduit_{tag}.png')
    save(pressure_hull(), tb / 'pressure_hull.png')
    save(bulkhead(), tb / 'bulkhead.png')
    save(machine_side(), tb / 'lab_machine_side.png')
    save(machine_top(), tb / 'lab_machine_top.png')
    save(rack_front(False), tb / 'server_rack_front_off.png')
    sheet = Image.new('RGBA', (S, S * RACK_FRAMES))
    for k in range(RACK_FRAMES):
        sheet.paste(grime(rack_front(True, k), seed('server_rack_front_on.png')), (0, S * k))
    save(sheet, tb / 'server_rack_front_on.png')
    (tb / 'server_rack_front_on.png.mcmeta').write_text(json.dumps({'animation': {'frametime': 6}}), encoding='utf-8')
    save_plain(glow_layer(sheet, RACK_LEDS), tb / 'server_rack_front_on_glow.png')
    (tb / 'server_rack_front_on_glow.png.mcmeta').write_text(json.dumps({'animation': {'frametime': 6}}), encoding='utf-8')
    front = terminal_front()
    save(front, tb / 'lab_terminal_front.png')
    save_plain(glow_layer(front, TERMINAL_SCREEN), tb / 'lab_terminal_front_glow.png')
    save(capsule_glass(), tb / 'android_capsule_glass.png')
    screen = monitor_screen()
    save(screen, tb / 'desk_monitor_screen.png')
    save_plain(glow_layer(screen, [(0, 0, S - 1, S - 1)]), tb / 'desk_monitor_screen_glow.png')
    save(plastic(), tb / 'lab_plastic.png')
    save(plastic((60, 64, 72)), tb / 'lab_plastic_dark.png')
    save(coffee_front(), tb / 'coffee_maker_front.png')
    save(coffee_pot(), tb / 'coffee_maker_pot.png')
    for upper in (False, True):
        half = 'upper' if upper else 'lower'
        save(vending_front(upper), tb / f'vending_machine_front_{half}.png')
        save_plain(vending_glow(upper), tb / f'vending_machine_front_{half}_glow.png')
    save(paper(), tb / 'paper_stack_top.png')
    save(paper_side(), tb / 'paper_stack_side.png')
    save(bin_side(), tb / 'trash_bin_side.png')
    save(bin_inner(), tb / 'trash_bin_inner.png')
    save(bin_bottom(), tb / 'trash_bin_bottom.png')
    save(capsule_body(), tb / 'android_capsule_body.png')


# ---------------------------------------------------------------- モデル

GLOW = {'block_light': 15, 'sky_light': 15}
FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
SIMPLE = ('lab_panel', 'lab_panel_lined', 'lab_tiles', 'lab_frame', 'pressure_hull', 'bulkhead')


def _cube(texture, emissive=False, translucent=False):
    faces = {d: {'texture': '#all', 'cullface': d} for d in ('north', 'south', 'east', 'west', 'up', 'down')}
    el = {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}
    if emissive:
        el['neoforge_data'] = GLOW
    m = {'parent': 'minecraft:block/block', 'textures': {'all': f'{MODID}:block/{texture}', 'particle': f'{MODID}:block/{texture}'},
         'elements': [el]}
    if translucent:
        m['render_type'] = 'minecraft:translucent'
    return m


def _orientable(front, side='lab_machine_side', top='lab_machine_top', glow=None):
    """前（north）が front の箱。glow を与えると、画面の画素だけの層をブロック境界に重ねて、そこだけ光らせる。"""
    t = {'front': f'{MODID}:block/{front}', 'side': f'{MODID}:block/{side}', 'top': f'{MODID}:block/{top}',
         'particle': f'{MODID}:block/{side}'}
    body = {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
        'south': {'texture': '#side', 'cullface': 'south'}, 'east': {'texture': '#side', 'cullface': 'east'},
        'west': {'texture': '#side', 'cullface': 'west'}, 'up': {'texture': '#top', 'cullface': 'up'},
        'down': {'texture': '#top', 'cullface': 'down'}}}
    face = {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {'north': {'texture': '#front', 'cullface': 'north'}}}
    model = {'parent': 'minecraft:block/block', 'textures': t, 'elements': [body, face]}
    if glow:
        face['from'][2] = 0.01  # 発光面と同一平面にしない。両方とも1マス内。
        t['glow'] = f'{MODID}:block/{glow}'
        model['elements'].append({'from': [0, 0, 0], 'to': [16, 16, 0], 'neoforge_data': GLOW,
                                  'faces': {'north': {'texture': '#glow', 'cullface': 'north'}}})
        model['render_type'] = 'minecraft:cutout'
    return model


def _rot(model, y):
    v = {'model': f'{MODID}:block/{model}'}
    if y:
        v['y'] = y
    return v


def models(write_json, assets):
    mb = assets / 'models' / 'block'
    mi = assets / 'models' / 'item'
    bs = assets / 'blockstates'
    for iid in SIMPLE:
        write_json(mb / f'{iid}.json', _cube(iid))
        write_json(mi / f'{iid}.json', {'parent': f'{MODID}:block/{iid}'})
    write_json(bs / 'lab_panel.json', {'variants': {'': {'model': f'{MODID}:block/lab_panel'}}})
    write_json(bs / 'lab_panel_lined.json', {'variants': {'': {'model': f'{MODID}:block/lab_panel_lined'}}})
    write_json(bs / 'lab_tiles.json', {'variants': {'': {'model': f'{MODID}:block/lab_tiles'}}})
    write_json(bs / 'lab_frame.json', {'variants': {'': {'model': f'{MODID}:block/lab_frame'}}})
    write_json(bs / 'pressure_hull.json', {'variants': {'sealed=true': {'model': f'{MODID}:block/pressure_hull'},
                                                        'sealed=false': {'model': f'{MODID}:block/pressure_hull'}}})
    # 隔壁: 開いていると描かない（Java で見えなくする）。形は同じものを当てておく
    write_json(bs / 'bulkhead.json', {'variants': {'open=false': {'model': f'{MODID}:block/bulkhead'},
                                                   'open=true': {'model': f'{MODID}:block/bulkhead'}}})
    write_json(mb / 'lab_glass.json', _cube('lab_glass', translucent=True))
    write_json(mi / 'lab_glass.json', {'parent': f'{MODID}:block/lab_glass'})
    write_json(bs / 'lab_glass.json', {'variants': {'': {'model': f'{MODID}:block/lab_glass'}}})
    # 電力で変わるもの（施設灯・非常灯・炉心）: powered=true / false
    for iid, lit_when in (('facility_light', True), ('emergency_light', False), ('reactor_core', True), ('power_conduit', True)):
        for on in (False, True):
            tag = 'on' if on else 'off'
            write_json(mb / f'{iid}_{tag}.json', _cube(f'{iid}_{tag}', emissive=on))
        write_json(bs / f'{iid}.json', {'variants': {
            'powered=true': {'model': f'{MODID}:block/{iid}_{"on" if lit_when else "off"}'},
            'powered=false': {'model': f'{MODID}:block/{iid}_{"off" if lit_when else "on"}'}}})
        write_json(mi / f'{iid}.json', {'parent': f'{MODID}:block/{iid}_on'})
    # 向きのあるもの
    for on in (False, True):
        tag = 'on' if on else 'off'
        write_json(mb / f'reactor_console_{tag}.json', _orientable(f'reactor_console_front_{tag}', glow=f'reactor_console_front_{tag}_glow'))
        write_json(mb / f'server_rack_{tag}.json', _orientable(f'server_rack_front_{tag}', side='lab_frame', top='lab_frame',
                                                               glow='server_rack_front_on_glow' if on else None))
    write_json(bs / 'reactor_console.json', {'variants': {
        f'facing={f},powered={p}': _rot(f'reactor_console_{"on" if p == "true" else "off"}', y)
        for f, y in FACING_Y.items() for p in ('false', 'true')}})
    write_json(bs / 'server_rack.json', {'variants': {
        f'facing={f},powered={p}': _rot(f'server_rack_{"on" if p == "true" else "off"}', y)
        for f, y in FACING_Y.items() for p in ('false', 'true')}})
    write_json(mi / 'reactor_console.json', {'parent': f'{MODID}:block/reactor_console_off'})
    write_json(mi / 'server_rack.json', {'parent': f'{MODID}:block/server_rack_on'})
    write_json(mb / 'lab_terminal.json', _orientable('lab_terminal_front', glow='lab_terminal_front_glow'))
    write_json(bs / 'lab_terminal.json', {'variants': {f'facing={f}': _rot('lab_terminal', y) for f, y in FACING_Y.items()}})
    write_json(mi / 'lab_terminal.json', {'parent': f'{MODID}:block/lab_terminal'})
    # 素体カプセル: 鋼の台座と天蓋、ガラスの筒、中の白い素体（頭・胴・脚）。2ブロックの高さ（下半分・上半分）
    t = {'base': f'{MODID}:block/lab_frame', 'glass': f'{MODID}:block/android_capsule_glass', 'body': f'{MODID}:block/android_capsule_body',
         'particle': f'{MODID}:block/lab_frame'}
    def box(a, b, tex, cull=()):
        return {'from': a, 'to': b, 'faces': {d: ({'texture': tex, 'cullface': d} if d in cull else {'texture': tex})
                                              for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    lower = [box([1, 0, 1], [15, 2, 15], '#base', ('down',)),
             box([6, 2, 7], [7.5, 12, 9], '#body'), box([8.5, 2, 7], [10, 12, 9], '#body'),
             box([2, 2, 2], [14, 16, 14], '#glass')]
    upper = [box([5.5, 0, 6.5], [10.5, 7, 9.5], '#body'), box([4.5, 1, 7], [5.5, 6, 9], '#body'), box([10.5, 1, 7], [11.5, 6, 9], '#body'),
             box([6.5, 7.5, 6.5], [9.5, 11, 9.5], '#body'),
             box([2, 0, 2], [14, 13, 14], '#glass'), box([1, 13, 1], [15, 16, 15], '#base', ('up',))]
    # ガラスの上下の蓋は台座・天蓋で隠れ、中央の継ぎ目にも不要。素体の足・胴と同じ面を重ねない。
    for glass in (lower[-1], upper[-2]):
        glass['faces'].pop('up')
        glass['faces'].pop('down')
    # 台座・素体は solid、ガラスだけ translucent に分ける。全部を translucent にすると、半透明の並べ替えが面の中心の距離で
    # 決まるため、見る角度によってガラスが先に描かれて奥行きを書き込み、中の素体が消える。
    for half, els in (('lower', lower), ('upper', upper)):
        glass = [e for e in els if e['faces'].get('north', {}).get('texture') == '#glass']
        solid = [e for e in els if e not in glass]
        write_json(mb / f'android_capsule_{half}.json', {'parent': 'minecraft:block/block', 'loader': 'neoforge:composite',
            'textures': {'particle': t['particle']},
            'children': {'solid': {'parent': 'minecraft:block/block', 'render_type': 'minecraft:solid', 'textures': t, 'elements': solid},
                         'glass': {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'textures': t,
                                   'elements': glass}},
            'item_render_order': ['solid', 'glass']})
    write_json(bs / 'android_capsule.json', {'variants': {
        f'facing={f},half={h}': _rot(f'android_capsule_{h}', y) for f, y in FACING_Y.items() for h in ('lower', 'upper')}})
    write_json(mi / 'android_capsule.json', {'parent': f'{MODID}:block/android_capsule_lower', 'display': held_display(lower)})
    decor_models(write_json, mb, mi, bs)


def _box(a, b, faces, glow=False):
    """要素（faces は 向き → テクスチャ名。省いた向きは描かない）。"""
    el = {'from': a, 'to': b, 'faces': {d: {'texture': t} for d, t in faces.items()}}
    if glow:
        el['neoforge_data'] = GLOW
    return el


def _uvbox(a, b, faces):
    """要素（faces は 向き → (テクスチャ名, UV) か (テクスチャ名, UV, 回転)。省いた向きは描かない）。"""
    def face(f):
        out = {'uv': f[1], 'texture': f[0]}
        if len(f) > 2 and f[2]:
            out['rotation'] = f[2]
        return out
    return {'from': a, 'to': b, 'faces': {d: face(f) for d, f in faces.items()}}


def _all(tex, skip=()):
    return {d: tex for d in ('north', 'south', 'east', 'west', 'up', 'down') if d not in skip}


def held_display(elements):
    """小さく偏った模型を、手・額・地面・持ち物欄で真ん中に見せる（バニラの block/block の向きのまま）。

    バニラの変形は模型の箱（16x16x16）の中心を基準にするので、下に寄った小物は一人称で画面の外へ沈む。
    模型の実際の範囲の中心が基準に来るよう平行移動を足し、小さいものは少し大きく見せる。
    """
    import math
    lo = [min(e['from'][i] for e in elements) for i in range(3)]
    hi = [max(e['to'][i] for e in elements) for i in range(3)]
    c = [(lo[i] + hi[i]) / 2 - 8 for i in range(3)]
    grow = min(1.5, 14 / max(hi[i] - lo[i] for i in range(3)))

    def rot(v, r):
        x, y, z = v
        for axis, deg in ((2, r[2]), (1, r[1]), (0, r[0])):       # rotationXYZ: Rx・Ry・Rz の順に掛かる
            a = math.radians(deg)
            co, si = math.cos(a), math.sin(a)
            if axis == 0:
                y, z = y * co - z * si, y * si + z * co
            elif axis == 1:
                x, z = x * co + z * si, -x * si + z * co
            else:
                x, y = x * co - y * si, x * si + y * co
        return x, y, z

    def view(r, scale, t=(0, 0, 0), k=grow):
        s = scale * k
        off = rot([-ci * s for ci in c], r)
        return {'rotation': r, 'translation': [round(t[i] + off[i], 3) for i in range(3)], 'scale': [round(s, 4)] * 3}
    tp = view([75, 45, 0], 0.375, (0, 2.5, 0))
    fp = view([0, 45, 0], 0.40)
    return {'gui': view([30, 225, 0], 0.625, k=min(grow, 1.25)), 'ground': view([0, 0, 0], 0.25, (0, 3, 0)),
            'fixed': view([0, 0, 0], 0.5), 'thirdperson_righthand': tp, 'thirdperson_lefthand': tp,
            'firstperson_righthand': fp, 'firstperson_lefthand': view([0, 225, 0], 0.40)}


def decor_models(write_json, mb, mi, bs):
    """研究所の小物: 机のモニター・コーヒーメーカー・自販機（2ブロック）・書類の山・ごみ箱。前は north。"""
    t = lambda n: f'{MODID}:block/{n}'
    models = {}

    def wm(path, data):
        models[path.stem] = data
        write_json(path, data)
    # 机のモニター: 台・首・画面の枠（暗い樹脂）と、前の画面（光るのは画面だけ）
    wm(mb / 'desk_monitor.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': {'dark': t('lab_plastic_dark'), 'screen': t('desk_monitor_screen'), 'glow': t('desk_monitor_screen_glow'),
                     'particle': t('lab_plastic_dark')},
        'elements': [_box([5, 0, 7], [11, 1, 11], _all('#dark')),
                     _box([7.5, 1, 8.5], [8.5, 4, 9.5], _all('#dark', ('up', 'down'))),
                     _box([2, 4, 8], [14, 12, 9.5], _all('#dark')),
                     _box([3, 5, 7.98], [13, 11, 8], {'north': '#screen'}),
                     _box([3, 5, 7.97], [13, 11, 7.98], {'north': '#glow'}, glow=True)]})
    # コーヒーメーカー: 白い本体、黒い正面、上の張り出し、ポット
    wm(mb / 'coffee_maker.json', {'parent': 'minecraft:block/block',
        'textures': {'body': t('lab_plastic'), 'front': t('coffee_maker_front'), 'pot': t('coffee_maker_pot'), 'particle': t('lab_plastic')},
        'elements': [_box([4, 0, 4], [12, 1, 12], _all('#body')),
                     _box([4, 1, 9], [12, 11, 13], {**_all('#body', ('north',)), 'north': '#front'}),
                     _box([4, 11, 4], [12, 13, 13], {**_all('#body', ('north',)), 'north': '#front'}),
                     _uvbox([5, 1, 5], [11, 7, 9], {'north': ('#pot', [0, 0, 6, 6]), 'south': ('#pot', [0, 0, 6, 6]),
                                                    'east': ('#pot', [6, 0, 10, 6]), 'west': ('#pot', [6, 0, 10, 6]),
                                                    'up': ('#pot', [0, 6, 6, 10]), 'down': ('#pot', [6, 6, 12, 10])}),
                     _uvbox([11, 2, 6.5], [12, 6, 7.5], {d: ('#pot', [12, 0, 13, 4]) for d in ('north', 'south', 'east', 'west')}
                            | {'up': ('#pot', [12, 0, 13, 1]), 'down': ('#pot', [12, 3, 13, 4])})]})
    # 自販機: 2ブロック。外装は赤、前の上半分は光る見本の棚
    for half in ('lower', 'upper'):
        wm(mb / f'vending_machine_{half}.json', {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
            'textures': {'side': t('vending_machine_front_lower'), 'front': t(f'vending_machine_front_{half}'),
                         'glow': t(f'vending_machine_front_{half}_glow'), 'body': t('lab_plastic'), 'particle': t('lab_plastic')},
            'elements': [_box([0, 0, 1], [16, 16, 16], {'north': '#front', 'south': '#body', 'east': '#body', 'west': '#body',
                                                         **({'up': '#body'} if half == 'upper' else {'down': '#body'})}),
                         _box([0, 0, 0.98], [16, 16, 0.99], {'north': '#glow'}, glow=True)]})
    write_json(bs / 'vending_machine.json', {'variants': {
        f'facing={f},half={h}': _rot(f'vending_machine_{h}', y) for f, y in FACING_Y.items() for h in ('lower', 'upper')}})
    write_json(mi / 'vending_machine.json', {'parent': f'{MODID}:block/vending_machine_lower',
                                             'display': held_display(models['vending_machine_lower']['elements'])})
    # 書類の山: 少しずれた2つの束
    wm(mb / 'paper_stack.json', {'parent': 'minecraft:block/block',
        'textures': {'top': t('paper_stack_top'), 'side': t('paper_stack_side'), 'particle': t('paper_stack_top')},
        'elements': [_box([3, 0, 3], [11, 3, 13], {**_all('#side', ('up',)), 'up': '#top'}),
                     _box([6, 3, 4], [13, 5, 12], {**_all('#side', ('up',)), 'up': '#top'})]})
    # ごみ箱: 灰色の筒。厚さ1の壁4枚と底で中を空け、内側の壁は底へ向かって暗くする。外側の上端に縁の帯が来るよう UV を合わせる
    def wall(a, b):
        (x0, y0, z0), (x1, y1, z1) = a, b
        v = [10 - y1, 10 - y0]
        side = {'north': [16 - x1, v[0], 16 - x0, v[1]], 'south': [x0, v[0], x1, v[1]],
                'east': [16 - z1, v[0], 16 - z0, v[1]], 'west': [z0, v[0], z1, v[1]]}
        inward = {'north': z0 > 8, 'south': z1 < 8, 'east': x1 < 8, 'west': x0 > 8}
        faces = {d: ('#inner' if inward[d] else '#side', uv) for d, uv in side.items()}
        # 上面（縁）・下面: 横に長い壁はそのまま、奥へ長い壁は絵を90度回して、長さに合ったピクセル数で貼る
        long_x = x1 - x0 > z1 - z0
        faces['up'] = ('#side', [x0, 1, x1, 2]) if long_x else ('#side', [16 - z1, 1, 16 - z0, 2], 90)
        faces['down'] = ('#side', [x0, 15 - (z1 - z0), x1, 15]) if long_x else ('#side', [16 - z1, 15 - (x1 - x0), 16 - z0, 15], 90)
        return _uvbox(a, b, faces)
    wm(mb / 'trash_bin.json', {'parent': 'minecraft:block/block',
        'textures': {'side': t('trash_bin_side'), 'inner': t('trash_bin_inner'), 'bottom': t('trash_bin_bottom'),
                     'particle': t('trash_bin_side')},
        'elements': [wall([4, 0, 4], [12, 10, 5]), wall([4, 0, 11], [12, 10, 12]),
                     wall([4, 0, 5], [5, 10, 11]), wall([11, 0, 5], [12, 10, 11]),
                     _uvbox([5, 0, 5], [11, 1, 11], {'up': ('#bottom', [0, 0, 6, 6]), 'down': ('#side', [5, 5, 11, 11])})]})
    for iid in ('desk_monitor', 'coffee_maker', 'paper_stack', 'trash_bin'):
        write_json(bs / f'{iid}.json', {'variants': {f'facing={f}': _rot(iid, y) for f, y in FACING_Y.items()}})
        write_json(mi / f'{iid}.json', {'parent': f'{MODID}:block/{iid}', 'display': held_display(models[iid]['elements'])})


# ---------------------------------------------------------------- 落とす物

def loot(write_json, data):
    # 隔壁・制御盤は何も落とさない（研究所の仕掛け）。素体カプセルは下半分だけが落とす
    # 自販機は下半分だけが落とす
    write_json(data / 'loot_table' / 'blocks' / 'vending_machine.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{MODID}:vending_machine'}],
        'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'{MODID}:vending_machine',
                        'properties': {'half': 'lower'}}, {'condition': 'minecraft:survives_explosion'}]}]})
    for iid in ('bulkhead', 'reactor_console'):
        write_json(data / 'loot_table' / 'blocks' / f'{iid}.json', {'type': 'minecraft:block', 'pools': []})
    write_json(data / 'loot_table' / 'blocks' / 'android_capsule.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{MODID}:android_capsule'}],
        'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'{MODID}:android_capsule',
                        'properties': {'half': 'lower'}}, {'condition': 'minecraft:survives_explosion'}]}]})
