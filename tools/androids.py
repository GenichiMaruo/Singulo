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
    'armor_chest': (110, 40, 6, 3, 1),
    'armor_pad': (110, 46, 3, 1, 4),
    'hilt': (114, 32, 1, 4, 1),
    'guard': (118, 32, 3, 1, 1),
    'backpack': (16, 44, 6, 6, 3),
    'pocket': (36, 44, 4, 3, 1),
    'strap': (48, 44, 1, 1, 4),
    'battery': (16, 54, 2, 3, 2),
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
    'saber_android': {
        'hair': ((226, 230, 238), (192, 198, 212), (160, 166, 182)),
        'streak': ((242, 202, 98), (204, 160, 58)),
        'accent': (255, 196, 90),
        'iris': ((168, 108, 28), (255, 212, 112)),
        'seed': 41,
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
    # ---------------------------------------------------------------- 装甲板（積んだときだけ見える。銀の板に型の色の縁）
    silver, silver_sh = (214, 220, 230), (168, 176, 190)
    f = part('armor_chest')
    for k in f:
        fill(f[k], silver_sh, 2)
    x0, y0, x1, y1 = f['front']
    fill(f['front'], silver, 2)
    for x in range(x0, x1):
        put(x, y1 - 1, accent, lit=True)
    put(x0 + 2, y0 + 1, WHITE)
    put(x0 + 3, y0 + 1, mix(accent, 40), lit=True)
    f = part('armor_pad')
    for k in f:
        fill(f[k], silver_sh if k != 'up' else silver, 2)
    x0, y0, x1, y1 = f['side_b']
    for x in range(x0, x1):
        put(x, y0, accent, lit=True)

    # ---------------------------------------------------------------- バックパック（積んだときだけ見える）
    # 白い陶磁の箱に黒い縁取り。下の面に型の色の光るライン（背中のスラスターの代わり）
    f = part('backpack')
    for k in f:
        fill(f[k], CERAMIC if k in ('back', 'up') else CERAMIC_SH, 2)
    for k in ('back', 'side_a', 'side_b'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y0, SUIT_HI)
            put(x, y1 - 1, SUIT)
        for y in range(y0, y1):
            put(x0, y, SUIT_HI)
            put(x1 - 1, y, SUIT_HI)
    x0, y0, x1, y1 = f['back']
    for x in range(x0 + 1, x1 - 1):
        put(x, y1 - 2, accent, lit=True)
    line(f['down'], accent)
    f = part('pocket')
    for k in f:
        fill(f[k], SUIT, 3)
    x0, y0, x1, y1 = f['back']
    for x in range(x0, x1):
        put(x, y0, SUIT_HI)
    put(x0 + 1, y0 + 1, accent, lit=True)
    put(x0 + 2, y0 + 1, accent, lit=True)
    f = part('strap')
    for k in f:
        fill(f[k], SUIT, 3)
    # ---------------------------------------------------------------- バッテリーパック（積んだ数だけ）
    # 銀の筒に黒い端子のふた。後ろの面に、型の色の充電ランプ（2つ）
    f = part('battery')
    for k in f:
        fill(f[k], CERAMIC_SH if k != 'up' else SUIT_HI, 2)
    for k in ('back', 'front', 'side_a', 'side_b'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y0, SUIT_HI)
    x0, y0, x1, y1 = f['back']
    put(x0, y0 + 1, accent, lit=True)
    put(x0 + 1, y0 + 1, accent, lit=True)
    put(x0, y0 + 2, mix(accent, -40), lit=True)

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

    # ---------------------------------------------------------------- 光剣の柄（白い握りに金の帯）と鍔
    f = part('hilt')
    for k in f:
        fill(f[k], CERAMIC, 2)
    for k in ('front', 'side_a', 'side_b', 'back'):
        x0, y0, x1, y1 = f[k]
        for x in range(x0, x1):
            put(x, y0 + 1, SUIT)
            put(x, y0 + 2, accent, lit=True)
    f = part('guard')
    for k in f:
        fill(f[k], CERAMIC_SH, 2)
    line(f['up'], accent)
    return base, glow


# ---------------------------------------------------------------- アイテムの絵（16×16）

def item_textures(ti, save):
    """小型クリスタルメモリ（3つの型の色）、アンドロイドキット、無線充電器。"""
    from PIL import Image
    for name, pal in VARIANTS.items():
        kind = name.replace('_android', '')
        save(memory_icon(pal['accent'], Image), ti / f'android_memory_{kind}.png')
    save(kit_icon(Image), ti / 'android_kit.png')
    save(charger_icon(Image), ti / 'android_charger.png')
    save(repair_icon(Image), ti / 'android_repair_kit.png')
    save(plate_icon(Image), ti / 'android_armor_plate.png')
    save(wear(upgrade_icon(Image), 11), ti / 'android_transfer_upgrade.png')
    save(wear(production_icon(Image), 13), ti / 'android_production_upgrade.png')
    save(wear(battery_icon(Image), 17), ti / 'android_battery_upgrade.png')
    save(wear(backpack_icon(Image), 19), ti / 'android_backpack_upgrade.png')
    save(wear(tank_icon(Image), 31), ti / 'android_tank_upgrade.png')
    save(droplet_icon(Image), ti / 'fluid_sample.png')
    save(wear(mining_icon(Image), 23), ti / 'android_mining_upgrade.png')
    save(wear(felling_icon(Image), 29), ti / 'android_felling_upgrade.png')
    save(card_icon(Image, (176, 182, 194), recorded=False), ti / 'work_location_card.png')
    save(card_icon(Image, (90, 190, 120), recorded=True), ti / 'work_location_card_recorded.png')
    save(card_icon(Image, (120, 200, 236)), ti / 'recipe_card.png')
    save(card_icon(Image, (236, 178, 92)), ti / 'procedure_card.png')


def memory_icon(accent, Image):
    """読み取り端子つきの小型クリスタルメモリ: 銀の枠に型の色の結晶、下に金の端子、横に小さな表示灯。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    frame, frame_sh, frame_hi = (196, 202, 212), (140, 146, 160), (236, 240, 246)
    gold = (226, 186, 92)

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = tuple(c) + (255,) if len(c) == 3 else c

    rect(3, 1, 12, 12, frame_sh)
    rect(4, 1, 12, 11, frame)
    rect(4, 1, 11, 1, frame_hi)
    # 使い込んだ枠の汚し（細かな擦れ・角の摩耗・斜めの小傷。汚れではなく使用感）
    rng = random.Random(sum(accent))
    for y in range(1, 13):
        for x in range(3, 13):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            k = rng.choice((0, 0, 0, -6, -10, 6))
            if (x, y) in ((3, 1), (12, 1), (3, 12), (12, 12), (4, 1), (12, 2)):
                k -= 18
            px[x, y] = (max(0, min(255, r + k)), max(0, min(255, g + k)), max(0, min(255, b + k)), a)
    for (x, y) in ((10, 11), (11, 10), (4, 7), (4, 8)):
        r, g, b, a = px[x, y]
        px[x, y] = (min(255, r + 26), min(255, g + 26), min(255, b + 26), a)
    # 結晶（上ほど明るい、斜めの光。奥の枠が少し透けて見える）
    for y in range(2, 10):
        for x in range(5, 11):
            k = (y - 2) / 7
            c = tuple(int(a * (1 - 0.45 * k) + 255 * 0.25 * (1 - k)) for a in accent)
            alpha = 150
            if (x + y) % 5 == 0:
                c = tuple(min(255, v + 50) for v in c)
                alpha = 190
            px[x, y] = tuple(min(255, v) for v in c) + (alpha,)
    px[6, 3] = (255, 255, 255, 235)
    px[7, 3] = (240, 250, 255, 210)
    px[6, 4] = (240, 250, 255, 210)
    # 読み取り端子
    rect(4, 12, 11, 12, frame_sh)
    for x in range(4, 12, 2):
        rect(x, 13, x, 14, gold)
    # 表示灯
    px[11, 11] = tuple(accent) + (255,)
    return im


def kit_icon(Image):
    """アンドロイドキット: 白い陶磁のケースに、黒い留め具と水色の線。ふたに人型の印。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    white, shade, edge = (238, 240, 244), (200, 206, 216), (120, 126, 140)
    dark = (52, 56, 70)
    glow = (120, 225, 255)
    for y in range(3, 14):
        for x in range(1, 15):
            c = shade if y >= 11 else white
            if x in (1, 14) or y in (3, 13):
                c = edge
            px[x, y] = c + (255,)
    for x in range(2, 14):
        px[x, 10] = glow + (255,)
    for x in (6, 7, 8, 9):
        px[x, 2] = dark + (255,)
    px[6, 3] = dark + (255,)
    px[9, 3] = dark + (255,)
    # 人型の印（頭と体）
    for (x, y) in ((7, 5), (8, 5), (7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8)):
        px[x, y] = (90, 150, 190, 255)
    px[3, 5] = glow + (255,)
    px[12, 5] = glow + (255,)
    return im


def charger_icon(Image):
    """無線充電器: 黒い本体に白い外装の輪、水色に光る送電の輪と稲妻の印。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    import math
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.0:
                c = (236, 238, 242) if d > 5.6 else (120, 225, 255) if d > 4.6 else (44, 48, 60)
                if 5.6 < d <= 7.0 and y > 9:
                    c = (200, 206, 216)
                px[x, y] = c + (255,)
    for (x, y) in ((8, 4), (7, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (8, 9), (7, 10)):
        px[x, y] = (255, 236, 140, 255)
    return im


def repair_icon(Image):
    """自己修理キット: 白い小箱に、ピンクの十字と小さなハート。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(4, 14):
        for x in range(2, 14):
            c = (238, 240, 244) if y < 11 else (206, 212, 222)
            if x in (2, 13) or y in (4, 13):
                c = (130, 136, 150)
            px[x, y] = c + (255,)
    for x in range(5, 11):
        px[x, 3] = (130, 136, 150, 255)
    px[5, 2] = px[10, 2] = (130, 136, 150, 255)
    for x in range(6, 10):
        px[x, 2] = (130, 136, 150, 255)
    for (x, y) in ((7, 6), (8, 6), (7, 7), (8, 7), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (7, 9), (8, 9), (7, 10), (8, 10)):
        px[x, y] = (240, 90, 130, 255)
    px[11, 5] = (255, 160, 190, 255)
    return im


def plate_icon(Image):
    """アンドロイド用装甲板: 白い陶磁の胸当て。縁は銀、中央に水色の発光ライン。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    rows = [(4, 11), (2, 13), (2, 13), (2, 13), (3, 12), (3, 12), (3, 12), (4, 11), (4, 11), (5, 10), (6, 9)]
    for i, (a, b) in enumerate(rows):
        y = 3 + i
        for x in range(a, b + 1):
            edge = x in (a, b) or i in (0, len(rows) - 1)
            c = (150, 156, 170) if edge else (236, 238, 242) if x < 8 else (210, 214, 224)
            px[x, y] = c + (255,)
    for x in (6, 9):
        px[x, 3] = (0, 0, 0, 0)
    for y in range(6, 12):
        px[7, y] = (120, 225, 255, 255)
        px[8, y] = (90, 190, 230, 255)
    return im


def upgrade_icon(Image):
    """転送アップグレード: 白い基板に、紫の転送の渦と、矢印。端に金の端子。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(2, 14):
        for x in range(2, 14):
            c = (236, 238, 242) if (x, y) not in ((2, 2), (13, 2), (2, 13), (13, 13)) else (0, 0, 0)
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                continue
            if x in (2, 13) or y in (2, 13):
                c = (150, 156, 170)
            px[x, y] = c + (255,)
    import math
    for y in range(4, 12):
        for x in range(4, 12):
            d = math.hypot(x - 7.5, y - 7.5)
            if 1.5 < d < 3.6:
                ang = math.atan2(y - 7.5, x - 7.5)
                v = 0.5 + 0.5 * math.sin(ang * 2 + d)
                px[x, y] = (int(120 + 80 * v), int(60 + 40 * v), int(200 + 50 * v), 255)
    px[7, 7] = px[8, 8] = (240, 220, 255, 255)
    px[7, 8] = px[8, 7] = (200, 160, 255, 255)
    for x in (5, 7, 9):
        px[x, 14] = (226, 186, 92, 255)
    return im


def upgrade_board(Image):
    """アップグレードの共通の台: 白い基板と灰色の縁、下に金の端子。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(2, 14):
        for x in range(2, 14):
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                continue
            px[x, y] = ((150, 156, 170) if x in (2, 13) or y in (2, 13) else (236, 238, 242)) + (255,)
    for x in (5, 7, 9):
        px[x, 14] = (226, 186, 92, 255)
    return im, px


def wear(im, seed):
    """使い込んだ汚し（汚れではなく使用感）: 面の細かな擦れ、縁の摩耗（角ほど強く）、小さな光る傷。"""
    px = im.load()
    w, h = im.size
    rng = random.Random(seed)

    def opaque(x, y):
        return 0 <= x < w and 0 <= y < h and px[x, y][3] > 0

    for y in range(h):
        for x in range(w):
            if not opaque(x, y):
                continue
            r, g, b, a = px[x, y]
            k = rng.choice((0, 0, 0, 0, -5, -8, 5))
            open_sides = sum(not opaque(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if open_sides >= 2:
                k -= 16                                   # 角の摩耗
            elif open_sides == 1 and rng.random() < 0.3:
                k -= 10                                   # 縁の擦れ
            px[x, y] = (max(0, min(255, r + k)), max(0, min(255, g + k)), max(0, min(255, b + k)), a)
    inner = [(x, y) for y in range(1, h - 1) for x in range(1, w - 1)
             if all(opaque(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))]
    for _ in range(3):
        if not inner:
            break
        x, y = rng.choice(inner)
        for (sx, sy, d) in ((x, y, 26), (x + 1, y - 1, 14)):     # 斜めの小傷
            if opaque(sx, sy):
                r, g, b, a = px[sx, sy]
                px[sx, sy] = (min(255, r + d), min(255, g + d), min(255, b + d), a)
    return im


def card_icon(Image, stripe, recorded=None):
    """作業のカード: 白いカードに色の帯と、金の端子、小さな光る点（作業位置は緑、レシピは水色）。
    作業位置カードは、未記録（recorded=False）なら帯が灰色で点が消え、記録済みなら帯が緑で、右上に位置の印（ピン）が付く。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(3, 13):
        for x in range(2, 14):
            if (x, y) in ((2, 3), (13, 3), (2, 12), (13, 12)):
                continue
            px[x, y] = ((150, 156, 170) if x in (2, 13) or y in (3, 12) else (238, 240, 244)) + (255,)
    for y in range(4, 7):
        for x in range(3, 13):
            px[x, y] = stripe + (255,)
    for x in (4, 6, 8):
        px[x, 10] = (226, 186, 92, 255)
        px[x, 11] = (200, 160, 70, 255)
    if recorded is not False:
        px[11, 9] = tuple(min(255, c + 70) for c in stripe) + (255,)
        px[10, 9] = stripe + (255,)
    else:
        px[11, 9] = (196, 200, 210, 255)
        px[10, 9] = (196, 200, 210, 255)
    if recorded:
        # 位置の印（ピン）: 帯の右上に、濃い緑の頭と白い光
        for (x, y) in ((11, 1), (12, 1), (11, 2), (12, 2)):
            px[x, y] = (40, 120, 70, 255)
        px[11, 1] = (200, 255, 210, 255)
        px[12, 3] = (40, 120, 70, 255)
    return im


def production_icon(Image):
    """生産アップグレード: 基板の上に、重なった2体の人影（水色と白）。"""
    im, px = upgrade_board(Image)
    back, front = (120, 200, 236), (60, 120, 170)
    for (cx, c) in ((9, back), (6, front)):
        for y in range(4, 7):
            for x in range(cx - 1, cx + 1):
                px[x, y] = c + (255,)
        for y in range(7, 12):
            for x in range(cx - 2, cx + 2):
                px[x, y] = c + (255,)
    px[11, 4] = (255, 255, 255, 255)
    return im


def battery_icon(Image):
    """バッテリーアップグレード: 縦長の電池に、緑の残量の目盛りと稲妻。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(3, 15):
        for x in range(4, 12):
            px[x, y] = ((90, 96, 110) if x in (4, 11) or y in (3, 14) else (214, 220, 230)) + (255,)
    for x in range(6, 10):
        px[x, 2] = (60, 64, 76, 255)
    for y in range(5, 13):
        for x in range(5, 11):
            if y >= 7:
                px[x, y] = ((90, 214, 140) if y % 2 else (70, 190, 120)) + (255,)
    for (x, y) in ((8, 6), (7, 7), (6, 8), (7, 8), (8, 8), (9, 8), (8, 9), (7, 10)):
        px[x, y] = (255, 236, 120, 255)
    return im


def backpack_icon(Image):
    """バックパックアップグレード: 白い箱のバックパックに、黒いふたと肩ひも、光るライン。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(4, 14):
        for x in range(3, 13):
            if (x, y) in ((3, 4), (12, 4), (3, 13), (12, 13)):
                continue
            px[x, y] = ((150, 156, 170) if x in (3, 12) or y in (4, 13) else (236, 238, 242)) + (255,)
    for y in range(4, 8):
        for x in range(4, 12):
            px[x, y] = (54, 58, 74, 255)
    for x in range(5, 11):
        px[x, 2] = (54, 58, 74, 255)
    px[5, 3] = px[10, 3] = (54, 58, 74, 255)
    for y in range(9, 12):
        for x in range(6, 10):
            px[x, y] = (200, 206, 216, 255)
    for x in range(4, 12):
        px[x, 12] = (120, 225, 255, 255)
    return im


def mining_icon(Image):
    """採掘アップグレード: 基板の上に、光るツルハシ（持ち手だけ実体の色、頭は水色に光る）。"""
    im, px = upgrade_board(Image)
    for i in range(7):
        px[5 + i, 11 - i] = (120, 88, 60, 255)        # 持ち手（左下から右上へ）
    head = [(4, 5), (5, 4), (6, 3), (7, 3), (8, 3), (9, 4), (10, 5), (11, 6), (12, 7)]
    for (x, y) in head:
        px[x, y] = (150, 236, 255, 255)
    for (x, y) in ((6, 4), (7, 4), (8, 4), (9, 5), (10, 6)):
        px[x, y] = (210, 248, 255, 255)
    return im


def droplet_icon(Image):
    """流体の見本: 白いしずく（流体の色に染めて使う）。左上に光、右下に影。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    # しずくの形（行ごとの左右の端）
    rows = {2: (7, 8), 3: (7, 8), 4: (6, 9), 5: (6, 9), 6: (5, 10), 7: (4, 11), 8: (4, 11), 9: (3, 12), 10: (3, 12),
            11: (3, 12), 12: (4, 11), 13: (5, 10), 14: (6, 9)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (2, 14)
            shade = 150 if edge else 255 - max(0, (x - 7) + (y - 9)) * 10
            px[x, y] = (shade, shade, shade, 255)
    for (x, y) in ((5, 9), (5, 10), (6, 8)):
        px[x, y] = (255, 255, 255, 255)
    return im


def tank_icon(Image):
    """タンクアップグレード: 基板の上に、ガラスの筒が2本（左は水色、右は黄緑の流体が入っている）。"""
    im, px = upgrade_board(Image)
    for x0, fluid in ((4, (90, 170, 240, 255)), (9, (170, 230, 110, 255))):
        for y in range(3, 13):
            for x in range(x0, x0 + 4):
                edge = x in (x0, x0 + 3) or y in (3, 12)
                if edge:
                    px[x, y] = (200, 210, 222, 255)          # ガラスの縁
                elif y >= 6:
                    px[x, y] = fluid                          # 流体
                else:
                    px[x, y] = (226, 238, 246, 255)          # 空いている所
        px[x0 + 1, 7] = (255, 255, 255, 255)                  # 光の反射
        for x in range(x0 + 1, x0 + 3):
            px[x, 2] = (120, 126, 136, 255)                   # ふた
    return im


def felling_icon(Image):
    """伐採アップグレード: 基板の上に、光る斧（持ち手だけ実体の色、刃は水色に光る）。"""
    im, px = upgrade_board(Image)
    for i in range(7):
        px[5 + i, 11 - i] = (120, 88, 60, 255)        # 持ち手（左下から右上へ）
    blade = [(9, 3), (10, 3), (11, 3), (8, 4), (9, 4), (10, 4), (11, 4), (12, 4), (9, 5), (10, 5), (11, 5), (12, 5), (12, 6), (11, 6)]
    for (x, y) in blade:
        px[x, y] = (150, 236, 255, 255)
    for (x, y) in ((12, 4), (12, 5), (12, 6)):
        px[x, y] = (210, 248, 255, 255)
    return im


# ---------------------------------------------------------------- アンドロイド用のガラス

# id: (縁の色, 光の色, ガラスの色。RGBA)
GLASSES = {
    'android_barrier_glass': ((190, 140, 80, 170), (255, 190, 100, 220), (255, 206, 150, 46)),
    'android_gate_glass': ((90, 156, 194, 170), (130, 228, 255, 220), (160, 222, 255, 46)),
}


def edge_color(frame, glow, i):
    """縁の1ドット（i は縁に沿った位置）: 明るい所と暗い所の細かな濃淡と、ところどころ光る点。"""
    if i % 6 == 3:
        return glow
    k = (8, -10, 0, 12, -6, 4)[i % 6]
    r, g, b, a = frame
    return (max(0, min(255, r + k)), max(0, min(255, g + k)), max(0, min(255, b + k)), a)
DIRS = {'north': ('z', -1), 'south': ('z', 1), 'west': ('x', -1), 'east': ('x', 1), 'down': ('y', -1), 'up': ('y', 1)}


def glass_textures(tb, save):
    """無地（中身・縁・持ったときの絵）と、模様入り（シンメトリー）のガラス。"""
    from PIL import Image
    for iid, (frame, glow, fill) in GLASSES.items():
        save(glass_fill(fill, Image), tb / f'{iid}.png')
        save(glass_edge(frame, glow, Image), tb / f'{iid}_edge.png')
        item = glass_fill(fill, Image)
        border(item, frame, glow)
        save(item, tb / f'{iid}_item.png')
        save(glass_pattern(iid, frame, glow, fill, Image), tb / f'{iid}_patterned.png')


def glass_fill(fill, Image):
    """ガラスの中身（縁なし）: うすい色と、斜めの光の筋2本。"""
    im = Image.new('RGBA', (16, 16), fill)
    px = im.load()
    for i in range(16):
        for x, y in ((i, 15 - i), (i + 3, 15 - i)):
            if 0 <= x < 16 and 2 <= i <= 12:
                px[x, y] = (255, 255, 255, 70)
    return im


def border(im, frame, glow):
    """外周1ドットの控えめな縁（細かな濃淡と光る点）。"""
    px = im.load()
    for i in range(16):
        c = edge_color(frame, glow, i)
        for a, b in ((i, 0), (i, 15), (0, i), (15, i)):
            px[a, b] = c


def glass_edge(frame, glow, Image):
    """つながっていない向きに描く縁: 上の1行（横の縁）と左の1列（縦の縁）。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for i in range(16):
        c = edge_color(frame, glow, i)
        px[i, 0] = c
        px[0, i] = c
    return im


def glass_pattern(iid, frame, glow, fill, Image):
    """模様入り: 縁と、上下左右対称の模様（遮断はひし形と横の帯、透過はひし形と中央の輪と内向きの山形）。"""
    im = glass_fill(fill, Image)
    border(im, frame, glow)
    px = im.load()
    for i in range(8):
        # ひし形（中心 7.5、半径 6）
        for x, y in ((7 - i, 2 + i), (8 + i, 2 + i), (7 - i, 13 - i), (8 + i, 13 - i)):
            if 2 <= x <= 13 and 2 <= y <= 13 and abs(x - 7.5) + abs(y - 7.5) >= 5:
                px[x, y] = glow
    if 'barrier' in iid:
        for x in range(4, 12):
            px[x, 7] = frame
            px[x, 8] = frame
        for x in (5, 10):
            px[x, 7] = px[x, 8] = glow
    else:
        for x, y in ((6, 6), (7, 6), (8, 6), (9, 6), (6, 9), (7, 9), (8, 9), (9, 9), (6, 7), (6, 8), (9, 7), (9, 8)):
            px[x, y] = glow
        px[7, 7] = px[8, 8] = px[7, 8] = px[8, 7] = (255, 255, 255, 120)
        for x, y in ((3, 4), (4, 3), (12, 4), (11, 3), (3, 11), (4, 12), (12, 11), (11, 12)):
            px[x, y] = frame
    return im


def glass_models(iid, write_json, assets):
    """無地のガラス: 中身の箱と、つながっていない向き（6方向）ごとの縁のモデル。blockstate は multipart。"""
    modid = 'singulo'
    tex = f'{modid}:block/{iid}'
    edge = f'{modid}:block/{iid}_edge'
    faces = ('north', 'south', 'east', 'west', 'up', 'down')
    write_json(assets / 'models' / 'block' / f'{iid}.json', {
        'render_type': 'minecraft:translucent',
        'textures': {'all': tex, 'particle': tex},
        'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16],
                      'faces': {f: {'texture': '#all', 'cullface': f} for f in faces}}],
    })
    vaxis = {'north': 'y', 'south': 'y', 'east': 'y', 'west': 'y', 'up': 'z', 'down': 'z'}
    parts = [{'apply': {'model': f'{modid}:block/{iid}'}}]
    for d, (dax, dsg) in DIRS.items():
        els = []
        for f, (fax, fsg) in DIRS.items():
            if fax == dax:
                continue
            lo, hi = [0, 0, 0], [16, 16, 16]
            i = 'xyz'.index(fax)
            # 横の縁と縦の縁は四隅で重なるので、縦の縁を少しだけ外へずらして、同じ面に重ならないようにする（ちらつき防止）
            horizontal = vaxis[f] == dax
            off = 0.005 if horizontal else 0.012
            plane = -off if fsg < 0 else 16 + off
            lo[i] = hi[i] = plane
            j = 'xyz'.index(dax)
            lo[j], hi[j] = (15, 16) if dsg > 0 else (0, 1)
            uv = [0, 0, 16, 1] if horizontal else [0, 0, 1, 16]
            els.append({'from': lo, 'to': hi, 'faces': {f: {'texture': '#edge', 'uv': uv, 'cullface': f}}})
        write_json(assets / 'models' / 'block' / f'{iid}_edge_{d}.json', {
            'render_type': 'minecraft:translucent', 'textures': {'edge': edge, 'particle': tex}, 'elements': els})
        parts.append({'when': {d: 'false'}, 'apply': {'model': f'{modid}:block/{iid}_edge_{d}'}})
    write_json(assets / 'blockstates' / f'{iid}.json', {'multipart': parts})
    write_json(assets / 'models' / 'item' / f'{iid}.json', {
        'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:translucent',
        'textures': {'all': f'{modid}:block/{iid}_item'}})
