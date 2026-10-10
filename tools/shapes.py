"""装置の立体（ブロックモデルの要素）。

基本はふつうの1マスの立方体のまま。装置の働きに意味のある所だけ、浅くくぼませる:
- 燃焼や反応が見えたほうが面白い装置（焼成炉の炎、電解槽の泡、反応室など）は、正面に奥行きのある窓を開けてガラスをはめ、
  奥に中身（正面の絵柄）を見せる。稼働中は中身が光る
- 排気口・吸気口・放熱フィンは、1ドットの溝でさりげなく表す
- 操作盤のある装置は、画面を1ドットだけ奥に下げる

くぼみは「面の手前の層」を、穴の部分を除いた長方形に分けて作る。座標はすべて整数で、1マス（0〜16）からはみ出さない。
同じ向きの面が同じ平面で重ならないように作る（tools/check_models.py で確かめる）。面の UV は書かず、
マインクラフトの既定（要素の座標から決まる）に任せる。正面は北（z=0）。
"""

# くぼみ: (面, (a0, b0, a1, b1), 深さ, 底のテクスチャ, ガラスか, 稼働中に光るか)
#   面ごとの (a, b): north は (x, y)、up は (x, z)、east / west は (z, y)
WINDOW = ('north', (4, 4, 12, 12), 3, '#front', True, True)
SCREEN = ('north', (4, 4, 12, 12), 1, '#front', False, True)


def slots(face, rects):
    """溝（深さ1、底は暗い金属）。"""
    return [(face, r, 1, '#inner', False, False) for r in rects]


TOP_VENT = slots('up', [(4, 5, 12, 6), (4, 8, 12, 9), (4, 11, 12, 12)])
SIDE_FINS = slots('east', [(2, y, 14, y + 1) for y in (3, 6, 9, 12)]) + slots('west', [(2, y, 14, y + 1) for y in (3, 6, 9, 12)])
SIDE_INTAKE = slots('west', [(z, 3, z + 1, 13) for z in (4, 7, 10)]) + slots('east', [(z, 3, z + 1, 13) for z in (4, 7, 10)])

RECESSES = {
    # 炎が見える窓と、天面の排気口
    'kiln': [WINDOW] + TOP_VENT,
    # 電気分解の泡が見える窓
    'electrolyzer': [WINDOW],
    # 反応室・結晶・封入の様子が見える窓
    'catalytic_reactor': [WINDOW],
    'time_crystal_incubator': [WINDOW],
    'singularity_encapsulator': [WINDOW],
    'laser_cooler': [('north', (5, 5, 11, 11), 3, '#front', True, True)],
    # 縮退熱炉の燃焼室の覗き窓
    'degenerate_furnace_controller': [('north', (4, 4, 12, 12), 2, '#front', True, True)],
    # 放熱フィン・吸気口・排気口
    'thermoelectric_generator': SIDE_FINS,
    'cryogenic_turbine': SIDE_INTAKE,
    'quantum_heat_engine': TOP_VENT,
    # 天面の検出窓（宇宙線を受ける）
    'cosmic_muon_collector': [('up', (4, 4, 12, 12), 2, '#inner', True, False)],
    # 操作盤の画面
    'archive_terminal': [SCREEN],
    'gravitational_wave_detector': [SCREEN],
    'probe_station': [SCREEN],
    'core_controller': [SCREEN],
    'cooling_tower_controller': [SCREEN],
    'accelerator_controller': [SCREEN],
    'degenerate_compactor_controller': [SCREEN],
    'casimir_cavity_controller': [SCREEN],
}

# ワールドの面ごとの、ふだんのテクスチャ
NATURAL = {'north': '#front', 'south': '#side', 'east': '#side', 'west': '#side', 'up': '#top', 'down': '#bottom'}
OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'down', 'down': 'up'}


def _decompose(region, holes):
    """長方形 region から穴（長方形）を除いた部分を、重ならない長方形に分ける。"""
    a0, b0, a1, b1 = region
    us = sorted({a0, a1} | {h[0] for h in holes} | {h[2] for h in holes})
    vs = sorted({b0, b1} | {h[1] for h in holes} | {h[3] for h in holes})
    us = [u for u in us if a0 <= u <= a1]
    vs = [v for v in vs if b0 <= v <= b1]
    out = []
    for j in range(len(vs) - 1):
        run = None
        for i in range(len(us) - 1):
            cu, cv = (us[i] + us[i + 1]) / 2, (vs[j] + vs[j + 1]) / 2
            inside = any(h[0] <= cu <= h[2] and h[1] <= cv <= h[3] for h in holes)
            if not inside:
                run = [us[i], vs[j], us[i + 1], vs[j + 1]] if run is None else [run[0], run[1], us[i + 1], run[3]]
            elif run is not None:
                out.append(tuple(run))
                run = None
        if run is not None:
            out.append(tuple(run))
    return out


def _to_box(face, a0, b0, a1, b1, d0, d1):
    """面の座標 (a, b) と深さ d（面から内側へ）を、ブロックの from / to に直す。"""
    if face == 'north':
        return [a0, b0, d0], [a1, b1, d1]
    if face == 'up':
        return [a0, 16 - d1, b0], [a1, 16 - d0, b1]
    if face == 'west':
        return [d0, b0, a0], [d1, b1, a1]
    if face == 'east':
        return [16 - d1, b0, a0], [16 - d0, b1, a1]
    raise ValueError(face)


def _box_faces(f, t, exclude, special=None):
    """要素の面。ブロックの外側に出ている面はふだんのテクスチャ、内側（くぼみの壁）は暗い金属。"""
    faces = {}
    bounds = {'north': f[2] == 0, 'south': t[2] == 16, 'west': f[0] == 0, 'east': t[0] == 16, 'down': f[1] == 0, 'up': t[1] == 16}
    for name in ('north', 'south', 'east', 'west', 'up', 'down'):
        if name in exclude:
            continue
        faces[name] = {'texture': NATURAL[name] if bounds[name] else '#inner'}
    for name, tex in (special or {}).items():
        faces[name] = {'texture': tex}
    return faces


def machine_elements(iid, on=False):
    """装置のモデルの要素と、ガラスがあるか（半透明で描くか）を返す。"""
    recesses = RECESSES.get(iid, [])
    els = []
    # 残りの箱（くぼみのある面の層を取るたびに小さくなる）
    box = [0, 0, 0, 16, 16, 16]
    by_face = {}
    for r in recesses:
        by_face.setdefault(r[0], []).append(r)
    has_glass = any(r[4] for r in recesses)
    for face in ('north', 'up', 'west', 'east'):
        if face not in by_face:
            continue
        holes = by_face[face]
        depth = max(h[2] for h in holes) + 1          # 層の厚さ（くぼみの底の後ろに1ドット残す）
        # この面の層の、面上の範囲（残りの箱から）
        if face == 'north':
            region = (box[0], box[1], box[3], box[4])
        elif face == 'up':
            region = (box[0], box[2], box[3], box[5])
        else:
            region = (box[2], box[1], box[5], box[4])
        # 穴のない部分（面から層の厚さまで）
        for a0, b0, a1, b1 in _decompose(region, [h[1] for h in holes]):
            f, t = _to_box(face, a0, b0, a1, b1, 0, depth)
            els.append({'from': f, 'to': t, 'faces': _box_faces(f, t, {OPPOSITE[face]})})
        # 穴の底（くぼみの深さから層の厚さまで）と、ガラス
        for _, (a0, b0, a1, b1), d, floor, glass, glow in holes:
            f, t = _to_box(face, a0, b0, a1, b1, d, depth)
            e = {'from': f, 'to': t, 'faces': {face: {'texture': floor}}}
            if glow and on:
                e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
            els.append(e)
            if glass:
                gf, gt = _to_box(face, a0, b0, a1, b1, 0.5, 0.5)
                els.append({'from': gf, 'to': gt, 'faces': {face: {'texture': '#glass'}, OPPOSITE[face]: {'texture': '#glass'}}})
        # 残りの箱を縮める
        if face == 'north':
            box[2] += depth
        elif face == 'up':
            box[4] -= depth
        elif face == 'west':
            box[0] += depth
        else:
            box[3] -= depth
    # 残りの箱（本体）。層に接している面は内側なので出さない
    f, t = box[:3], box[3:]
    exclude = {face for face in by_face}
    faces = _box_faces(f, t, exclude)
    # 本体の外側の面は、層を取って奥にずれていてもふだんのテクスチャ
    for name in faces:
        faces[name] = {'texture': NATURAL[name]}
    els.append({'from': f, 'to': t, 'faces': faces})
    return els, has_glass


def frame_elements(t=2):
    """枠だけの筐体（立方体の12本の辺。太さ t ドット）。中が見通せるので、喉を筐体の中に浮かべて見せる。
    柱（縦の4本）は上下いっぱい、梁（上下の各4本）は柱の間だけ。梁の端の面は柱に隠れるので出さない。
    外に出ている面はふだんのテクスチャ、内側を向く面は暗い金属。"""
    lo, hi = t, 16 - t
    els = []

    def add(f, to, exclude=()):
        els.append({'from': f, 'to': to, 'faces': _box_faces(f, to, set(exclude))})

    for x0 in (0, hi):
        for z0 in (0, hi):
            add([x0, 0, z0], [x0 + t, 16, z0 + t])
    for y0 in (0, hi):
        for z0 in (0, hi):
            add([lo, y0, z0], [hi, y0 + t, z0 + t], exclude=('east', 'west'))
        for x0 in (0, hi):
            add([x0, y0, lo], [x0 + t, y0 + t, hi], exclude=('north', 'south'))
    return els
