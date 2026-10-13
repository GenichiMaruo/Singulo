"""遺構のアンドロイドのテクスチャ（128×64）。

部品の配置は src/main/java/.../client/AndroidModel.createLayer と同じ。3体で形は共通で、髪・光の色・武器だけが違う。
共通の目印: 白い陶磁の外装、黒いインナー、右のこめかみのセンサーの羽、胸の発光ライン、スカートの裾の発光ライン。
"""
import random

# 名前: (u, v, 幅, 高さ, 奥行き)
ANDROID_PARTS = {
    'head': (0, 0, 8, 8, 7),
    'hair': (30, 0, 8, 8, 7),
    'back_hair': (60, 0, 9, 9, 2),
    'side_lock_l': (82, 0, 1, 8, 2),
    'side_lock_r': (88, 0, 1, 4, 2),
    'ponytail': (94, 0, 2, 7, 2),
    'ahoge': (10, 48, 1, 3, 2),
    'fin': (106, 0, 1, 3, 3),
    'neck': (114, 0, 2, 2, 2),
    'torso': (0, 16, 6, 8, 3),
    'chest': (18, 16, 6, 3, 1),
    'pelvis': (32, 16, 7, 3, 4),
    'skirt_front': (54, 16, 8, 5, 1),
    'skirt_back': (72, 16, 8, 6, 1),
    'skirt_side': (90, 16, 1, 5, 5),
    'skirt_tail': (102, 16, 1, 8, 3),
    'thigh_l': (0, 32, 3, 6, 3),
    'thigh_r': (12, 32, 3, 6, 3),
    'shin_l': (24, 32, 3, 6, 3),
    'shin_r': (36, 32, 3, 6, 3),
    'arm_l': (48, 32, 2, 5, 2),
    'arm_r': (56, 32, 2, 5, 2),
    'forearm_l': (64, 32, 2, 5, 2),
    'gauntlet': (72, 32, 3, 5, 3),
    'pad': (84, 32, 3, 2, 4),
    'blade': (98, 32, 1, 13, 2),
    'blade_tip': (104, 32, 1, 2, 1),
    'thruster': (0, 48, 2, 3, 2),
    'whip_handle': (110, 32, 1, 4, 1),
}

# 型ごとの色。hair: 髪（明・影・深い影）、streak: ひと房（またはインナー）の色、accent: 光の色、iris: 瞳（深・明）
# mirror: 長い房・サイドテール・前髪のひと房が体の右側（鞭型）。inner: 髪の内側（後ろ髪の裏・房の内側）を streak の色に。
VARIANTS = {
    'blade_android': {
        'hair': ((238, 241, 247), (208, 215, 229), (178, 188, 208)),
        'streak': ((126, 216, 244), (84, 182, 222)),
        'accent': (120, 225, 255),
        'iris': ((40, 128, 182), (110, 214, 250)),
        'seed': 31,
    },
    'whip_android': {
        'hair': ((44, 42, 56), (32, 30, 42), (20, 19, 28)),
        'streak': ((236, 92, 178), (184, 58, 140)),
        'accent': (255, 106, 196),
        'iris': ((150, 40, 120), (250, 122, 210)),
        'seed': 37,
        'mirror': True,
        'inner': True,
    },
}

SKIN = (255, 236, 228)
SKIN_SH = (238, 214, 206)
BLUSH = (246, 186, 194)
CERAMIC = (240, 242, 246)
CERAMIC_SH = (208, 213, 223)
CERAMIC_DEEP = (176, 182, 196)
SUIT = (38, 42, 56)
SUIT_HI = (60, 66, 84)
WHITE = (252, 253, 255)


def faces(u, v, w, h, d):
    """立方体の展開図の各面（x0, y0, x1, y1。右と下は含まない）。side_a は体の右（-X）、side_b は左（+X）、front は正面（-Z）。"""
    return {
        'up': (u + d, v, u + d + w, v + d),
        'down': (u + d + w, v, u + d + 2 * w, v + d),
        'side_a': (u, v + d, u + d, v + d + h),
        'front': (u + d, v + d, u + d + w, v + d + h),
        'side_b': (u + d + w, v + d, u + 2 * d + w, v + d + h),
        'back': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def textures(te, save):
    from PIL import Image
    for name, pal in VARIANTS.items():
        base, glow = paint(pal, Image)
        save(base, te / f'{name}.png')
        save(glow, te / f'{name}_glow.png')


def paint(pal, Image):
    rng = random.Random(pal['seed'])
    base = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    glow = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    hair, hair_sh, hair_deep = pal['hair']
    streak, streak_sh = pal['streak']
    mirror = pal.get('mirror', False)
    inner = pal.get('inner', False)
    accent = pal['accent']
    iris_deep, iris = pal['iris']

    def mix(c, k):
        return tuple(max(0, min(255, int(x + k))) for x in c)

    def put(x, y, c, a=255, lit=False):
        base.putpixel((x, y), tuple(c) + (a,))
        if lit:
            glow.putpixel((x, y), tuple(c) + (255,))

    def fill(rect, c, noise=3, a=255):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                put(x, y, mix(c, rng.randint(-noise, noise)), a)

    def part(name):
        return faces(*ANDROID_PARTS[name])

    def hair_face(rect, strands=True):
        """髪の面: 縦の筋（2ドットおきに影）。"""
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = hair_sh if strands and (x - x0) % 3 == 2 else hair
                if y == y1 - 1 and strands:
                    c = hair_sh
                put(x, y, mix(c, rng.randint(-2, 2)))

    def line(rect, c, lit=True):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                put(x, y, c, lit=lit)

    # ---------------------------------------------------------------- 頭（顔）
    f = part('head')
    for k in ('up', 'back'):
        hair_face(f[k])
    fill(f['down'], SKIN_SH, 2)
    # 頭の横は髪（外側の髪の層の角を抜いたところから、1ドット奥の髪として見える）。あごの角だけ肌
    for k, front_left in (('side_a', False), ('side_b', True)):
        x0, y0, x1, y1 = f[k]
        for y in range(y0, y1):
            for x in range(x0, x1):
                col = x - x0
                jaw = y >= y0 + 6 and ((front_left and col <= 2) or (not front_left and col >= 4))
                put(x, y, SKIN_SH if jaw else mix(hair_sh, rng.randint(-2, 2)))
    # 顔（8×8）: 0〜3段目は前髪の下、4段目は眉（髪の色）、すぐ下の5〜6段目が目、7段目の両端に頬。まつげ・口・鼻は描かない
    fx, fy, _, _ = f['front']
    fill(f['front'], SKIN, 1)
    for y in range(3):
        for x in range(8):
            put(fx + x, fy + y, hair if y < 2 else hair_sh)
    for bx in (1, 5):
        put(fx + bx, fy + 4, hair_deep)
        put(fx + bx + 1, fy + 4, hair_deep)
    for ex in (1, 5):
        put(fx + ex, fy + 5, WHITE, lit=True)
        put(fx + ex + 1, fy + 5, iris_deep, lit=True)
        put(fx + ex, fy + 6, iris, lit=True)
        put(fx + ex + 1, fy + 6, iris, lit=True)
    put(fx + 0, fy + 7, BLUSH)
    put(fx + 7, fy + 7, BLUSH)
    # 外側の髪の層で抜いた角の奥（影を濃くして、凹んで見えるように）
    x0, y0, x1, y1 = f['up']
    for cx, cy in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
        put(cx, cy, hair_deep)
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        put(x0, y0, hair_deep)
        put(x1 - 1, y0, hair_deep)

    # ---------------------------------------------------------------- 髪の殻（前髪・横・後ろ・上）
    f = part('hair')
    # 前髪: 眉のすぐ上まで。いちばん下の段は毛先がぎざぎざ
    hx, hy, _, _ = f['front']
    for y in range(4):
        for x in range(8):
            put(hx + x, hy + y, hair if (x + y) % 3 else hair_sh)
    for x in (0, 3, 4):
        put(hx + x, hy + 4, hair_sh)
    # ひと房（光刃型は体の左側、鞭型は右側の前髪）
    sc, tc = (1, 0) if mirror else (6, 7)
    for y in range(4):
        put(hx + sc, hy + y, streak)
    put(hx + tc, hy + 4, streak_sh)
    # 顔の両脇に垂れる毛
    for y in range(5, 7):
        put(hx, hy + y, hair_sh)
        put(hx + 7, hy + y, hair_sh if y < 6 else hair_deep)
    for k, near_front_left in (('side_a', False), ('side_b', True)):
        x0, y0, x1, y1 = f[k]
        hair_face(f[k])
        # 顔に近い下の角は抜く（あごの線と耳）
        for y in range(y0 + 6, y1):
            for x in range(x0, x1):
                col = x - x0
                if (near_front_left and col <= 2) or (not near_front_left and col >= 4):
                    put(x, y, (0, 0, 0), 0)
    hair_face(f['back'])
    x0, y0, x1, y1 = f['up']
    hair_face(f['up'], strands=False)
    for i in range(7):
        put(x0 + min(7, 2 + i // 2), y0 + i, hair_sh)  # 分け目
    put(x0 + (1 if mirror else 6), y0 + 0, streak)
    put(x0 + (1 if mirror else 6), y0 + 1, streak)
    # 上の角を1ドットずつ抜いて、頭の丸みを出す
    clear = (0, 0, 0)
    x0, y0, x1, y1 = f['up']
    for cx, cy in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
        put(cx, cy, clear, 0)
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        put(x0, y0, clear, 0)
        put(x1 - 1, y0, clear, 0)

    # ---------------------------------------------------------------- 後ろ髪・横の房・サイドテール・アホ毛
    f = part('back_hair')
    for k in ('back', 'side_a', 'side_b', 'up'):
        hair_face(f[k])
    # 後ろ髪の裏（頭側）。インナーカラーの型は、ここが差し色になる
    fill(f['front'], streak_sh if inner else hair_deep, 2)
    fill(f['down'], streak if inner else hair_sh, 2)
    if inner:
        for k in ('side_a', 'side_b'):
            x0, y0, x1, y1 = f[k]
            for y in range(y0 + 2, y1):
                put(x0 if k == 'side_b' else x1 - 1, y, streak)
    x0, y0, x1, y1 = f['back']
    for x in range(x0, x1):
        if (x - x0) % 2 == 1:
            put(x, y1 - 1, (0, 0, 0), 0)
    x0, y0, x1, y1 = f['front']
    for x in range(x0, x1):
        if (x - x0) % 2 == 1:
            put(x, y1 - 1, (0, 0, 0), 0)

    for name, tip in (('side_lock_l', True), ('side_lock_r', False)):
        f = part(name)
        for k in f:
            hair_face(f[k], strands=False)
        if inner:
            # 房の内側（顔の側）を差し色に。長い房は左にあれば -X 面、右にあれば +X 面が顔の側
            on_left = (name == 'side_lock_l') != mirror
            fill(f['side_a' if on_left else 'side_b'], streak, 2)
        if tip:
            for k in ('front', 'side_a', 'side_b', 'back'):
                x0, y0, x1, y1 = f[k]
                for x in range(x0, x1):
                    put(x, y1 - 2, streak)
                    put(x, y1 - 1, streak_sh)

    f = part('ponytail')
    for k in f:
        hair_face(f[k])
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y0, accent, lit=True)  # 髪留め
            put(x, y1 - 2, streak)
            put(x, y1 - 1, streak_sh)
    x0, y0, x1, y1 = f['up']
    line((x0, y0, x1, y1), accent)

    # アホ毛: 横から見て縦3・横2のコの字（下の棒が頭に乗り、前で折り返して、上の棒が後ろへ）。縁の細い面は描かない
    f = part('ahoge')
    for k, front in (('side_a', 1), ('side_b', 0)):
        x0, y0, x1, y1 = f[k]
        for cx in range(2):
            put(x0 + cx, y0, hair)
            put(x0 + cx, y0 + 2, hair_sh)
        put(x0 + front, y0 + 1, hair)

    # ---------------------------------------------------------------- センサーの羽（共通の目印）・首
    f = part('fin')
    for k in f:
        fill(f[k], CERAMIC, 2)
    x0, y0, x1, y1 = f['side_a']
    for x in range(x0, x1):
        put(x, y0 + 1, accent, lit=True)
    put(x0, y0, CERAMIC_SH)
    f = part('neck')
    for k in f:
        fill(f[k], SKIN_SH, 2)
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y1 - 1, SUIT)
    x0, y0, x1, y1 = f['front']
    put(x0, y1 - 1, accent, lit=True)

    # ---------------------------------------------------------------- 胴（黒いインナー、陶磁の襟と脇、胸の発光ライン）
    f = part('torso')
    for k in f:
        fill(f[k], SUIT, 3)
    x0, y0, x1, y1 = f['front']
    for x in range(x0, x1):
        put(x, y0, CERAMIC)
    for y in range(y0 + 4, y0 + 7):
        put(x0 + 2 if y % 2 == 0 else x0 + 3, y, accent, lit=True)
    for y in range(y0 + 1, y0 + 4):
        for x in range(x0, x1):
            put(x, y, CERAMIC_SH)
    for k in ('side_a', 'side_b'):
        x0, y0, x1, y1 = f[k]
        for y in range(y0 + 3, y1):
            put(x0 + 1, y, CERAMIC_SH)
    x0, y0, x1, y1 = f['back']
    for y in range(y0 + 1, y0 + 4):
        for x in range(x0 + 1, x1 - 1):
            put(x, y, CERAMIC_DEEP)
    f = part('chest')
    for k in f:
        fill(f[k], CERAMIC, 2)
    x0, y0, x1, y1 = f['front']
    for x in range(x0, x1):
        put(x, y1 - 1, CERAMIC_SH)
    put(x0 + 2, y1 - 1, accent, lit=True)
    put(x0 + 3, y1 - 1, accent, lit=True)
    put(x0 + 4, y0, accent, lit=True)  # 左胸の小さな紋（非対称）

    f = part('pelvis')
    for k in f:
        fill(f[k], SUIT, 3)
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y0, CERAMIC)
    x0, y0, x1, y1 = f['front']
    put(x0 + 3, y0, accent, lit=True)

    # ---------------------------------------------------------------- スカート（ひだ、裾の発光ライン）
    def skirt(name, faces_out):
        f = part(name)
        for k in f:
            fill(f[k], SUIT_HI, 2)
        for k in faces_out:
            x0, y0, x1, y1 = f[k]
            for y in range(y0, y1):
                for x in range(x0, x1):
                    c = CERAMIC_SH if (x - x0) % 2 == 1 else CERAMIC
                    if y >= y1 - 2:
                        c = mix(c, -8)
                    put(x, y, mix(c, rng.randint(-2, 2)))
            for x in range(x0, x1):
                put(x, y1 - 1, accent, lit=True)
        for k in ('up',):
            fill(f[k], CERAMIC, 2)

    skirt('skirt_front', ('front',))
    skirt('skirt_back', ('back',))
    skirt('skirt_side', ('side_a', 'side_b'))
    f = part('skirt_tail')
    for k in f:
        fill(f[k], CERAMIC_SH, 2)
    for k in ('side_a', 'side_b'):
        x0, y0, x1, y1 = f[k]
        for y in range(y0, y1):
            put(x0 + 1, y, accent if y > y0 + 1 else CERAMIC, lit=y > y0 + 1)
        for x in range(x0, x1):
            put(x, y1 - 1, CERAMIC_DEEP)

    # ---------------------------------------------------------------- 脚（左は黒いニーハイ、右は素肌にガーター。ブーツ）
    f = part('thigh_l')
    for k in f:
        x0, y0, x1, y1 = f[k]
        for y in range(y0, y1):
            row = y - y0
            for x in range(x0, x1):
                if k in ('up', 'down'):
                    c = SUIT
                elif row < 2:
                    c = SKIN if k == 'front' else SKIN_SH
                elif row == 2:
                    c = accent
                else:
                    c = SUIT if (x - x0) != 1 or k != 'front' else SUIT_HI
                put(x, y, c, lit=(row == 2 and k not in ('up', 'down')))
    f = part('thigh_r')
    for k in f:
        x0, y0, x1, y1 = f[k]
        for y in range(y0, y1):
            row = y - y0
            for x in range(x0, x1):
                c = SKIN if k == 'front' else SKIN_SH
                lit = False
                if row == 1 and k in ('front', 'side_a', 'side_b', 'back'):
                    c, lit = accent, True
                put(x, y, c, lit=lit)
    for name, outer in (('shin_l', 'side_b'), ('shin_r', 'side_a')):
        f = part(name)
        for k in f:
            fill(f[k], CERAMIC, 2)
        for k in ('front', 'side_a', 'side_b', 'back'):
            x0, y0, x1, y1 = f[k]
            for x in range(x0, x1):
                put(x, y0, accent, lit=True)
                put(x, y1 - 1, SUIT)
            if k == 'front':
                put(x0 + 1, y0 + 1, CERAMIC_SH)
                put(x0 + 1, y0 + 2, CERAMIC_SH)
        fill(f['down'], SUIT, 2)
        if name == 'shin_l':
            x0, y0, x1, y1 = f[outer]
            for y in range(y0 + 1, y1 - 1):
                put(x0 + 1, y, accent, lit=True)

    # ---------------------------------------------------------------- 腕（左は素肌と長い手袋、右は黒い袖と籠手）
    f = part('arm_l')
    for k in f:
        fill(f[k], SKIN_SH if k != 'front' else SKIN, 2)
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y0 + 2, accent, lit=True)
    f = part('arm_r')
    for k in f:
        fill(f[k], SUIT, 3)
    for k in ('side_a',):
        x0, y0, x1, y1 = f[k]
        for y in range(y0, y1):
            put(x0, y, CERAMIC_SH)
    f = part('forearm_l')
    for k in f:
        fill(f[k], CERAMIC, 2)
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y0, accent, lit=True)
            put(x, y1 - 2, CERAMIC_SH)
            put(x, y1 - 1, CERAMIC_SH)
    f = part('gauntlet')
    for k in f:
        fill(f[k], CERAMIC, 2)
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y0, SUIT)
            put(x, y1 - 1, SUIT_HI)
    x0, y0, x1, y1 = f['side_a']
    for y in range(y0 + 1, y1 - 1):
        put(x0 + 1, y, accent, lit=True)
        put(x0 + 2, y, CERAMIC_DEEP)
    x0, y0, x1, y1 = f['front']
    put(x0 + 1, y0 + 2, CERAMIC_DEEP)
    fill(f['down'], SUIT_HI, 2)
    f = part('pad')
    for k in f:
        fill(f[k], CERAMIC, 2)
    for k in ('side_a', 'front', 'back'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y1 - 1, accent if k == 'side_a' else CERAMIC_SH, lit=k == 'side_a')

    # ---------------------------------------------------------------- 光の刃（半透明。縁ほど明るい）
    for name in ('blade', 'blade_tip'):
        f = part(name)
        for k, (x0, y0, x1, y1) in f.items():
            for y in range(y0, y1):
                along = (y - y0) / max(1, (y1 - y0 - 1))
                for x in range(x0, x1):
                    edge = (x == x0) if k in ('side_a', 'side_b') else True
                    if k in ('front', 'back', 'up', 'down') or edge:
                        c, a = mix(WHITE, -10), 235
                    else:
                        c, a = mix(accent, 30 * along), int(120 + 60 * along)
                    put(x, y, c, a)

    # ---------------------------------------------------------------- 背中のスラスター
    f = part('thruster')
    for k in f:
        fill(f[k], SUIT_HI, 3)
    x0, y0, x1, y1 = f['back']
    for y in range(y0, y1):
        put(x0, y, CERAMIC)
    line(f['down'], accent)
    # ---------------------------------------------------------------- 鞭の柄（黒い握りに、光る輪）
    f = part('whip_handle')
    for k in f:
        fill(f[k], SUIT_HI, 3)
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y0, CERAMIC)
            put(x, y0 + 2, accent, lit=True)
            put(x, y1 - 1, accent, lit=True)
    line(f['down'], accent)
    return base, glow
