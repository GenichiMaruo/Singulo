# -*- coding: utf-8 -*-
"""0.17.0: 液体パイプの段階、液体のドローン発着ポート、液体カプセル。

ID・レシピ・画面の文言・ハンドブックの章・説明文と、パイプとカプセルの絵とモデルを、ここにまとめる
（ids.py・recipes.py・gen_data.py・guide.py・descriptions.py から読む）。

パイプは電線と見分けられるよう、四角い帯の被覆ではなく、継ぎ目ごとの「つば」（フランジ）と、中の液体が見える窓を持つ。
断面と継ぎ手の大きさは Java の PipeProfile と同じ値にしておくこと。中の液体は FluidPipeRenderer が描く。
"""
from pathlib import Path

MODID = 'singulo'

IDS = {
    '真空断熱パイプ': ('vacuum_jacketed_pipe', 'Vacuum-Jacketed Pipe', 'machine'),
    '超流動導管': ('superfluid_conduit', 'Superfluid Conduit', 'machine'),
    'ホライズン・ダクト': ('horizon_duct', 'Horizon Duct', 'machine'),
    '液体ドローン発着ポート': ('fluid_drone_port', 'Fluid Drone Port', 'machine'),
    '液体カプセル': ('fluid_capsule', 'Fluid Capsule', 'tool'),
    'バッテリー付き液体カプセル': ('battery_fluid_capsule', 'Battery Fluid Capsule', 'tool'),
    '大容量バッテリー付き液体カプセル': ('large_battery_fluid_capsule', 'Large Battery Fluid Capsule', 'tool'),
}

# パイプの段階: (ID, 管の太さ, 継ぎ手のつばの太さ, 1 tick に運べる量 mB。None は上限なし)。Java の PipeProfile・SinguloBlocks と同じ
PIPES = {
    'fluid_pipe': (6, 8, 2_000),
    'vacuum_jacketed_pipe': (8, 10, 32_000),
    'superfluid_conduit': (8, 10, 512_000),
    'horizon_duct': (10, 12, None),
}
CAPSULES = ('fluid_capsule', 'battery_fluid_capsule', 'large_battery_fluid_capsule')


def recipes(R, shaped):
    # 液体パイプの列を、鋼の外筒とセラミックの断熱材で包む
    R('真空断熱パイプ', 2, '作業台', {'鋼板': 4, 'ホワイトセラミック複合材': 2, '液体パイプ': 3}, out=3, cat='cable',
      note='1 tick に 32,000 mB。液体パイプの16倍')
    shaped['真空断熱パイプ'] = (('PWP', 'LLL', 'PWP'), {'P': '鋼板', 'W': 'ホワイトセラミック複合材', 'L': '液体パイプ'})
    R('超流動導管', 4, '極低温冷却塔', {'量子もつれ素子': 1, '真空断熱パイプ': 8}, out=8, t=20, fe=4000, cat='cable',
      note='1 tick に 512,000 mB。粘りのない超流動で運ぶ')
    R('ホライズン・ダクト', 5, '縮退圧縮炉', {'エキゾチック物質': 1, '超流動導管': 4, 'ニホニウム板': 1}, out=8, t=20, fe=50000, cat='cable',
      note='上限なし')
    # 物の発着ポートのチェストを、液体を受けるバケツとパイプの口に替えたもの
    R('液体ドローン発着ポート', 2, '作業台', {'鋼板': 2, '鉄のトラップドア': 1, 'バケツ': 1, '基礎フレーム': 1, '液体パイプ': 1,
                                     'セラミック基板': 2, 'ピストン': 1}, cat='machine',
      note='液体を液体カプセルに詰めて、同じ持ち主のほかの液体のポートへドローンで送る')
    shaped['液体ドローン発着ポート'] = (('PTP', 'UFL', 'KXK'), {'P': '鋼板', 'T': '鉄のトラップドア', 'U': 'バケツ', 'F': '基礎フレーム',
                                                         'L': '液体パイプ', 'K': 'セラミック基板', 'X': 'ピストン'})
    # 鋼板の鏡板と枠でガラスの胴を挟んだ、中が空の耐圧容器
    R('液体カプセル', 2, '作業台', {'鋼板': 4, 'ガラス': 4}, out=2, cat='part', note='160ブロックまで送れる。64,000 mB まで詰める')
    shaped['液体カプセル'] = (('PGP', 'G G', 'PGP'), {'P': '鋼板', 'G': 'ガラス'})
    # 梱包箱と同じ電池の付け方
    R('バッテリー付き液体カプセル', 2, '作業台', {'レッドストーン': 2, '超伝導コイル': 1, '銅インゴット': 2, '液体カプセル': 1}, cat='part',
      note='760ブロックまで送れる')
    shaped['バッテリー付き液体カプセル'] = (('ROR', 'CXC'), {'R': 'レッドストーン', 'O': '超伝導コイル', 'C': '銅インゴット', 'X': '液体カプセル'})
    R('大容量バッテリー付き液体カプセル', 3, '作業台', {'超伝導線材': 2, '量子もつれ素子': 1, '超伝導コイル': 2, 'バッテリー付き液体カプセル': 1},
      cat='part', note='3,160ブロックまで送れる')
    shaped['大容量バッテリー付き液体カプセル'] = (('WEW', 'OXO'), {'W': '超伝導線材', 'E': '量子もつれ素子', 'O': '超伝導コイル',
                                                           'X': 'バッテリー付き液体カプセル'})


UI = {
    'gui.singulo.fluid_drone_port.crates': ('空の液体カプセル', 'Empty fluid capsules'),
    'gui.singulo.fluid_drone_port.send_tank': ('送るタンク（発送でカプセルに詰める）', 'Send tank (filled into a capsule when sent)'),
    'gui.singulo.fluid_drone_port.receive_tank': ('受け取りのタンク（届いたカプセルから注ぐ）', 'Receive tank (arrived capsules empty into it)'),
    'gui.singulo.fluid_drone_port.amount': ('%s / %s mB', '%s / %s mB'),
    'gui.singulo.fluid_drone_port.auto.amount': ('自動 %sB', 'Auto: %s B'),
    'gui.singulo.fluid_drone_port.status.6': ('空の液体カプセルがありません', 'No empty fluid capsule'),
    'gui.singulo.fluid_drone_port.status.7': ('射程外です（上の段階の液体カプセルが要る）', 'Out of range (needs a higher-tier capsule)'),
    'gui.singulo.fluid_drone_port.status.9': ('送る液体がありません', 'No fluid to send'),
    'tooltip.singulo.fluid_capsule.empty': ('空（%s mB まで詰められる）', 'Empty (holds up to %s mB)'),
    'tooltip.singulo.fluid_capsule.contents': ('%s %s / %s mB', '%s %s / %s mB'),
    'tooltip.singulo.fluid_capsule.pour': ('タンクなどに使うと、入るだけ注ぐ', 'Use on a tank to pour in what fits'),
    'tooltip.singulo.fluid_pipe.capacity': ('1 tick に %s mB まで運ぶ', 'Carries up to %s mB per tick'),
    'tooltip.singulo.fluid_pipe.unlimited': ('運べる量に上限なし', 'No transfer limit'),
    'tooltip.singulo.fluid_drone_port.send': ('送るタンク: %s %s mB', 'Send tank: %s %s mB'),
    'tooltip.singulo.fluid_drone_port.receive': ('受け取りのタンク: %s %s mB', 'Receive tank: %s %s mB'),
    'gui.singulo.pipe.flow': ('配管: %s mB/t', 'Pipe: %s mB/t'),
}

CHAPTER = ('fluids', 'singulo:vacuum_jacketed_pipe', '液体の輸送', 'Moving Fluids', [
    ('pipes', 'パイプの段階',
     '液体パイプは4段階あり、段階が上がるほど太く、たくさん運べます（1 tick あたり）。\n'
     '- 液体パイプ（段階1）: 2,000 mB。銅の管にガラスの窓。\n'
     '- 真空断熱パイプ（段階2）: 32,000 mB。白い断熱の外筒と霜の青の継ぎ手。\n'
     '- 超流動導管（段階4）: 512,000 mB。透明な管を金の輪が締める。\n'
     '- ホライズン・ダクト（段階5）: 上限なし。黒い太管に金の継ぎ手。\n'
     '段階の違うパイプどうしもつながります。運べる量は、口から口までの道のいちばん細いところで決まり、同じ区間を通る流れはその量を分け合います。'
     '液体が流れている間は、窓や透明な管から中の液体と流れの向きが見えます。\n'
     'パイプは液体を溜めず、どれだけ長くつないでも重くなりにくい作りです（分かれ目のない並びを1つにまとめて扱います）。',
     'Pipe tiers',
     'Fluid pipes come in four tiers. Higher tiers are thicker and carry more per tick.\n'
     '- Fluid Pipe (stage 1): 2,000 mB. A copper pipe with glass windows.\n'
     '- Vacuum-Jacketed Pipe (stage 2): 32,000 mB. A white insulated jacket with frost-blue fittings.\n'
     '- Superfluid Conduit (stage 4): 512,000 mB. A clear tube held by gold rings.\n'
     '- Horizon Duct (stage 5): no limit. A thick black main with gold fittings.\n'
     'Pipes of different tiers connect to each other. The amount moved is set by the narrowest part of the route from one port to another, and flows that share a stretch share its capacity. '
     'While fluid flows, you can see it and its direction through the windows and clear tubes.\n'
     'Pipes hold no fluid, and long networks stay light: unbranched runs are handled as a single stretch.'),
    ('drone', '液体をドローンで送る',
     '液体ドローン発着ポートは、物の発着ポートと同じドローンとステーションで、液体を送ります。行き先に並ぶのは、同じ持ち主の液体のポートだけです。\n'
     '左上に空の液体カプセルを入れ、送るタンク（左）に液体をためて「発送」を押すと、タンクの液体（1種類、64,000 mB まで）をカプセルに詰めて送ります。'
     '届いたカプセルは受け取りのタンク（右、128,000 mB）へ注がれ、空になったカプセルは空き箱の欄へ戻ります。受け取りのタンクがいっぱいなら、カプセルは着荷箱の欄で待ちます。\n'
     'パイプは面の設定に従います。「入力」の面から送るタンクへ入れ、「出力」の面から受け取りのタンクを取り出します。自動排出をオンにすると、受け取った液体を隣のタンクや装置へ押し出します。バケツでも出し入れできます。\n'
     '射程と電力は梱包箱と同じです（液体カプセル160・バッテリー付き760・大容量3,160ブロック）。自動発送のしきい値は、バケツの数（1・16・32・満杯）です。',
     'Sending fluids by drone',
     'The Fluid Drone Port sends fluids with the same drones and stations as the item port. Only fluid ports with the same owner appear as destinations.\n'
     'Put empty fluid capsules top left, fill the send tank (left) and press Send. The port fills a capsule from the tank (one fluid, up to 64,000 mB) and sends it. '
     'Arrived capsules empty into the receive tank (right, 128,000 mB), and empty capsules return to the crate slot. If the receive tank is full, the capsule waits in the arrival slots.\n'
     'Pipes follow the Sides settings: input faces fill the send tank and output faces drain the receive tank. With auto-eject on, received fluid is pushed into the neighbouring tank or machine. Buckets also work.\n'
     'Range and power are the same as packing crates (Fluid Capsule 160, Battery 760, Large Battery 3,160 blocks). Auto-send thresholds count buckets (1, 16, 32 or full).'),
])

DESC = {
    '液体パイプ': ('液体とガスを運ぶ段階1のパイプ。1 tick に 2,000 mB。置くだけでつながり、出口の液体を使う装置へ送る。隣の水源や溶岩源からも汲む。',
               'A stage 1 pipe for fluids and gases: 2,000 mB per tick. Connects on its own, sends outputs to the machines that use them and draws from adjacent water and lava sources.'),
    '真空断熱パイプ': ('真空の断熱層で包んだ段階2のパイプ。1 tick に 32,000 mB。ほかの段階のパイプともつながる。',
                'A stage 2 pipe wrapped in a vacuum insulation jacket: 32,000 mB per tick. Connects to pipes of any tier.'),
    '超流動導管': ('粘りのない超流動で運ぶ段階4の透明な導管。1 tick に 512,000 mB。',
              'A clear stage 4 conduit that moves fluid as a frictionless superfluid: 512,000 mB per tick.'),
    'ホライズン・ダクト': ('段階5の太い本管。運べる量に上限がない。',
                  'A thick stage 5 main with no transfer limit.'),
    '液体ドローン発着ポート': ('送るタンクの液体を液体カプセルに詰め、同じ持ち主のほかの液体のポートへドローンで送る。届いた液体は受け取りのタンクへ注ぐ。',
                     'Fills fluid capsules from its send tank and sends them by drone to other fluid ports with the same owner. Arrived fluid empties into its receive tank.'),
    '液体カプセル': ('液体を1種類、64,000 mB まで詰めてドローンで送る耐圧容器。160ブロックまで届く。',
               'A pressure vessel that carries up to 64,000 mB of one fluid by drone. Reaches 160 blocks.'),
    'バッテリー付き液体カプセル': ('電池を付けた液体カプセル。760ブロックまで届く。', 'A fluid capsule with a battery. Reaches 760 blocks.'),
    '大容量バッテリー付き液体カプセル': ('大きな電池を付けた液体カプセル。3,160ブロックまで届く。', 'A fluid capsule with a large battery. Reaches 3,160 blocks.'),
}

SUMMARY = {
    '液体パイプ': ('液体とガスを運ぶパイプ。', 'A pipe that carries fluids and gases.'),
    '真空断熱パイプ': ('液体パイプの16倍運ぶパイプ。', 'Carries 16 times as much as a Fluid Pipe.'),
    '超流動導管': ('大量の液体を運ぶ透明な導管。', 'A clear conduit for very large flows.'),
    'ホライズン・ダクト': ('上限なしの液体の本管。', 'A fluid main with no limit.'),
    '液体ドローン発着ポート': ('液体をドローンで送る・受け取る。', 'Sends and receives fluids by drone.'),
    '液体カプセル': ('液体をドローンで送る容器。', 'A container for sending fluid by drone.'),
    'バッテリー付き液体カプセル': ('遠くへ送れる液体カプセル。', 'A fluid capsule with longer range.'),
    '大容量バッテリー付き液体カプセル': ('いちばん遠くへ送れる液体カプセル。', 'The longest-range fluid capsule.'),
}


# ---------------------------------------------------------------- 絵

def _pipe_colors(iid):
    """段階ごとの色: 管の地・縁・明るい筋・窓のガラス（RGBA）・継ぎ手・継ぎ手の縁・ボルト。"""
    return {
        'fluid_pipe': dict(body=(184, 110, 70), edge=(118, 66, 40), hi=(228, 160, 112), glass=(176, 222, 240, 96),
                           fit=(150, 86, 52), fit_edge=(96, 52, 32), bolt=(232, 182, 130)),
        'vacuum_jacketed_pipe': dict(body=(232, 236, 240), edge=(178, 186, 196), hi=(250, 252, 253), glass=(190, 230, 255, 110),
                                     fit=(120, 190, 235), fit_edge=(70, 120, 160), bolt=(236, 246, 252)),
        'superfluid_conduit': dict(body=(205, 215, 255), edge=(150, 160, 220), hi=(245, 248, 255), glass=(214, 222, 255, 70),
                                   fit=(220, 188, 108), fit_edge=(150, 118, 52), bolt=(255, 236, 176)),
        'horizon_duct': dict(body=(26, 26, 32), edge=(12, 12, 16), hi=(64, 62, 78), glass=(150, 140, 255, 110),
                             fit=(220, 188, 108), fit_edge=(40, 36, 30), bolt=(255, 230, 160)),
    }[iid]


def pipe_side(iid):
    """管の側面: 行が断面の位置（8 を境に上下対称）、列が軸の向き。窓や透明な管の部分は半透明。"""
    import art16
    from art16 import rgba, mix
    w, _, _ = PIPES[iid]
    c = _pipe_colors(iid)
    im, d = art16.new()
    lo, hi = 8 - w // 2, 8 + w // 2
    for y in range(lo, hi):
        edge = y in (lo, hi - 1)
        for x in range(16):
            col = rgba(c['edge'] if edge else c['body'])
            if iid == 'fluid_pipe':
                # 銅の管。真ん中に2つのガラスの窓
                if not edge and (2 <= x <= 6 or 9 <= x <= 13):
                    col = c['glass']
                elif y == lo + 1:
                    col = rgba(c['hi'])
            elif iid == 'vacuum_jacketed_pipe':
                # 白い外筒。両端の近くに霜の青の帯、真ん中の2段に細い窓
                if x in (2, 13):
                    col = rgba(c['fit'])
                elif y in (7, 8) and 4 <= x <= 11:
                    col = c['glass']
                elif y == lo + 1:
                    col = rgba(c['hi'])
            elif iid == 'superfluid_conduit':
                # 全体が透明な管。金の輪が締める
                if x in (0, 5, 10, 15):
                    col = rgba(c['fit'] if not edge else c['fit_edge'])
                elif edge:
                    col = rgba(c['edge'], 150)
                else:
                    g = c['glass']
                    col = (g[0], g[1], g[2], g[3] + (40 if y == lo + 1 else 0))
            else:
                # 黒い太管。真ん中の2段に紫の窓、その上下に金の細い縁
                if y in (7, 8) and 2 <= x <= 13:
                    col = c['glass']
                elif y in (6, 9) and 2 <= x <= 13:
                    col = rgba(mix(c['fit'], c['body'], 0.35))
                elif y == lo + 1:
                    col = rgba(c['hi'])
            d.point((x, y), fill=col)
    return im


def pipe_flange(iid):
    """継ぎ手のつばの面（輪にボルト）。真ん中の管の口は管の縁の色。"""
    import art16
    from art16 import rgba
    w, f, _ = PIPES[iid]
    c = _pipe_colors(iid)
    im, d = art16.new()
    lo, hi = 8 - f // 2, 8 + f // 2 - 1
    d.rectangle([lo, lo, hi, hi], fill=rgba(c['fit']), outline=rgba(c['fit_edge']))
    tl, th = 8 - w // 2, 8 + w // 2 - 1
    d.rectangle([tl, tl, th, th], fill=rgba(c['edge']), outline=rgba(c['fit_edge']))
    for x, y in ((lo + 1, lo + 1), (hi - 1, lo + 1), (lo + 1, hi - 1), (hi - 1, hi - 1)):
        d.point((x, y), fill=rgba(c['bolt']))
    return im


def pipe_rim(iid):
    """継ぎ手のつばの縁（厚さ1の側面）と、分かれ目の継ぎ手の側面。"""
    import art16
    from art16 import rgba, mix
    c = _pipe_colors(iid)
    im, d = art16.new(fill=rgba(c['fit']))
    for x in range(16):
        d.point((x, 0), fill=rgba(c['fit_edge']))
        d.point((x, 15), fill=rgba(c['fit_edge']))
    for x in range(1, 15, 3):
        d.point((x, 8), fill=rgba(mix(c['fit'], c['bolt'], 0.6)))
    return im


def pipe_joint(iid):
    """分かれ目の継ぎ手（T字・十字の箱）の面: 縁取りの箱に、真ん中の管の口。"""
    import art16
    from art16 import rgba
    w, _, _ = PIPES[iid]
    c = _pipe_colors(iid)
    im, d = art16.new()
    lo, hi = 8 - w // 2 - 1, 8 + w // 2
    d.rectangle([lo, lo, hi, hi], fill=rgba(c['fit']), outline=rgba(c['fit_edge']))
    d.rectangle([lo + 2, lo + 2, hi - 2, hi - 2], fill=rgba(c['body']))
    for x, y in ((lo + 1, lo + 1), (hi - 1, lo + 1), (lo + 1, hi - 1), (hi - 1, hi - 1)):
        d.point((x, y), fill=rgba(c['bolt']))
    return im


def split(im, opaque):
    """不透明な画素だけ（opaque）か、半透明な画素だけの絵。同じ面に重ねても、どの画素も片方にしかないので重ならない。"""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            a = px[x, y][3]
            if (a == 255) != opaque or a == 0:
                px[x, y] = (0, 0, 0, 0)
    return out


# ---------------------------------------------------------------- パイプのモデル

def pipe_models(write_json, assets):
    """パイプ: 断面 w の管、つないだ面ごとの継ぎ手のつば（太さ f、厚さ1）、分かれ目では一回り大きい継ぎ手の箱。
    まっすぐなら中心は管のまま。テクスチャは1ドットを1ドットのまま貼る。窓は半透明（translucent）。"""
    for iid, (w, f, _) in PIPES.items():
        lo, hi = 8 - w // 2, 8 + w // 2
        flo, fhi = 8 - f // 2, 8 + f // 2
        jlo, jhi = lo - 1, hi + 1
        tex = f'{MODID}:block/{iid}'
        # 不透明な本体（'c'、窓は抜いてある）と、窓のガラスだけの絵（'g'）。手に持ったときと粒子は、両方を重ねた絵（_full）
        textures = {'particle': f'{tex}_full', 'c': tex, 'g': f'{tex}_glass', 'f': f'{tex}_flange', 'r': f'{tex}_rim', 'j': f'{tex}_joint'}

        def tube(z0, z1, key='#c'):
            def side(rot=None):
                s = {'uv': [z0, lo, z1, hi], 'texture': key}
                if rot:
                    s['rotation'] = rot
                return s
            return {'from': [lo, lo, z0], 'to': [hi, hi, z1],
                    'faces': {'east': side(), 'west': side(), 'up': side(90), 'down': side(90)}}

        flange = {'from': [flo, flo, 0], 'to': [fhi, fhi, 1], 'faces': {
            'north': {'uv': [flo, flo, fhi, fhi], 'texture': '#f'},
            'south': {'uv': [flo, flo, fhi, fhi], 'texture': '#f'},
            'east': {'uv': [0, flo, 1, fhi], 'texture': '#r'}, 'west': {'uv': [0, flo, 1, fhi], 'texture': '#r'},
            'up': {'uv': [flo, 0, fhi, 1], 'texture': '#r'}, 'down': {'uv': [flo, 0, fhi, 1], 'texture': '#r'}}}
        # 不透明な部分は切り抜き（cutout）で描き、窓のガラスだけを別のモデルで半透明に描く。全体を半透明にすると、
        # 半透明の層が選択の枠より後に重なり、黒い縁が透けたり奥と混ざったりする
        base = {'parent': 'minecraft:block/block', 'textures': textures, 'render_type': 'minecraft:cutout'}
        glass = {'parent': 'minecraft:block/block', 'textures': textures, 'render_type': 'minecraft:translucent'}
        write_json(assets / 'models' / 'block' / f'{iid}_arm_glass.json', {**glass, 'elements': [tube(1, lo, '#g')]})
        write_json(assets / 'models' / 'block' / f'{iid}_arm_short_glass.json', {**glass, 'elements': [tube(1, jlo, '#g')]})
        write_json(assets / 'models' / 'block' / f'{iid}_straight_glass.json', {**glass, 'elements': [tube(lo, hi, '#g')]})
        # 腕: まっすぐのとき（中心の管まで）と、分かれ目のとき（継ぎ手の箱まで）
        write_json(assets / 'models' / 'block' / f'{iid}_arm.json', {**base, 'elements': [flange, tube(1, lo)]})
        write_json(assets / 'models' / 'block' / f'{iid}_arm_short.json', {**base, 'elements': [flange, tube(1, jlo)]})
        write_json(assets / 'models' / 'block' / f'{iid}_straight.json', {**base, 'elements': [tube(lo, hi)]})
        joint = {'from': [jlo, jlo, jlo], 'to': [jhi, jhi, jhi],
                 'faces': {d: {'uv': [jlo, jlo, jhi, jhi], 'texture': '#j'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
        write_json(assets / 'models' / 'block' / f'{iid}_core.json', {**base, 'elements': [joint]})
        rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
        straight_rot = {'z': {}, 'x': {'y': 90}, 'y': {'x': 90}}
        multipart = [{'when': {'straight': 'none'}, 'apply': {'model': f'{MODID}:block/{iid}_core'}}]
        for axis, r in straight_rot.items():
            multipart.append({'when': {'straight': axis}, 'apply': {'model': f'{MODID}:block/{iid}_straight', **r}})
            multipart.append({'when': {'straight': axis}, 'apply': {'model': f'{MODID}:block/{iid}_straight_glass', **r}})
        for d, r in rot.items():
            multipart.append({'when': {d: 'true', 'straight': 'x|y|z'}, 'apply': {'model': f'{MODID}:block/{iid}_arm', **r}})
            multipart.append({'when': {d: 'true', 'straight': 'x|y|z'}, 'apply': {'model': f'{MODID}:block/{iid}_arm_glass', **r}})
            multipart.append({'when': {d: 'true', 'straight': 'none'}, 'apply': {'model': f'{MODID}:block/{iid}_arm_short', **r}})
            multipart.append({'when': {d: 'true', 'straight': 'none'}, 'apply': {'model': f'{MODID}:block/{iid}_arm_short_glass', **r}})
        write_json(assets / 'blockstates' / f'{iid}.json', {'multipart': multipart})
        # 持ったときは、両端に継ぎ手の付いたまっすぐな1本
        band = {'uv': [1, lo, 15, hi], 'texture': '#item'}
        item_tube = {'from': [1, lo, lo], 'to': [15, hi, hi], 'faces': {
            'north': band, 'south': band, 'up': band, 'down': band}}
        ends = []
        for x0, x1 in ((0, 1), (15, 16)):
            ends.append({'from': [x0, flo, flo], 'to': [x1, fhi, fhi], 'faces': {
                'east': {'uv': [flo, flo, fhi, fhi], 'texture': '#f'}, 'west': {'uv': [flo, flo, fhi, fhi], 'texture': '#f'},
                'north': {'uv': [0, flo, 1, fhi], 'texture': '#r'}, 'south': {'uv': [0, flo, 1, fhi], 'texture': '#r'},
                'up': {'uv': [0, flo, 1, fhi], 'texture': '#r', 'rotation': 90},
                'down': {'uv': [0, flo, 1, fhi], 'texture': '#r', 'rotation': 90}}})
        write_json(assets / 'models' / 'item' / f'{iid}.json', {
            'parent': 'minecraft:block/block', 'textures': {**textures, 'item': f'{tex}_full'}, 'render_type': 'minecraft:translucent',
            'elements': [item_tube] + ends,
            'display': {'gui': {'rotation': [30, 45, 0], 'scale': [0.7, 0.7, 0.7]},
                        'fixed': {'rotation': [0, 90, 0], 'scale': [0.8, 0.8, 0.8]}}})


# ---------------------------------------------------------------- 液体カプセル

TIER_BAND = {1: (236, 238, 242), 2: (240, 160, 64), 3: (120, 225, 255)}


def capsule_textures(tier):
    """カプセルの材質の絵（それぞれ 16×16。面には既定の UV で貼るので、どの面も1ドット＝1/16ブロック）。
    鏡板と締め帯の白い金属（継ぎ目と鋲、研磨の筋、縁の擦れ）、枠と脚の暗い鋼、胴のガラス（映り込み）、段階の電池（帯の色の線と端子）。"""
    import surface
    metal = surface.metal((226, 230, 236), 'capsule-metal', grain=0.04, seams=(4, 12),
                          rivets=[(2, 2), (13, 2), (2, 13), (13, 13), (7, 8)], scuffs=5)
    dark = surface.metal((60, 64, 74), 'capsule-dark', grain=0.08, streak='h', scuffs=6, edge=0.12)
    glass = surface.glass((205, 228, 240), 'capsule-glass', alpha=55)
    band = surface.metal((46, 50, 60), f'capsule-band-{tier}', grain=0.06, streak=None, scuffs=3)
    c = TIER_BAND[tier]
    px = band.load()
    for x in range(16):
        for y in (6, 9):
            px[x, y] = tuple(int(v * 0.55) for v in c) + (255,)
        for y in (7, 8):
            px[x, y] = c + (255,)
    for x in range(1, 16, 4):
        px[x, 3] = (200, 205, 214, 255)
        px[x, 12] = (200, 205, 214, 255)
    return {'m': metal, 'd': dark, 'g': glass, 'b': band}


ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def _el(f, t, key, glow=False):
    """要素1つ（全部の面、既定の UV）。"""
    e = {'from': list(f), 'to': list(t), 'faces': {n: {'texture': key} for n in ALL}}
    if glow:
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
    return e


def capsule_elements(tier):
    """立体の耐圧容器（横向き。手に持っても、ドローンに吊るしても、台に載せても同じ形。高さ 3〜13 で、梱包箱と同じ 10/16）。
    ガラスの胴（3〜13 × 5〜11 × 5〜11）・両端の鏡板と丸い頭・四隅の保護の枠・2本の締め帯・下の2本の脚・上の弁。
    段階2は横に電池パック1つ、段階3は前後に2つと光る帯。どの要素も全部の面を描く。"""
    els = [
        _el((3, 5, 5), (13, 11, 11), '#g'),
        _el((2, 5, 5), (3, 11, 11), '#m'), _el((13, 5, 5), (14, 11, 11), '#m'),
        _el((1, 6, 6), (2, 10, 10), '#m'), _el((14, 6, 6), (15, 10, 10), '#m'),
    ]
    for y0, z0 in ((4, 4), (4, 11), (11, 4), (11, 11)):
        els.append(_el((2, y0, z0), (14, y0 + 1, z0 + 1), '#d'))
    for x0 in (5, 10):
        els.append(_el((x0, 11, 5), (x0 + 1, 12, 11), '#m'))
        els.append(_el((x0, 4, 5), (x0 + 1, 5, 11), '#m'))
        els.append(_el((x0, 5, 4), (x0 + 1, 11, 5), '#m'))
        els.append(_el((x0, 5, 11), (x0 + 1, 11, 12), '#m'))
    for z0 in (5, 10):
        els.append(_el((3, 3, z0), (13, 4, z0 + 1), '#d'))
    els.append(_el((7, 11, 7), (9, 12, 9), '#d'))                     # 弁の軸
    els.append(_el((6, 12, 7), (10, 13, 9), '#b'))                    # 弁のハンドル（整数のドット）
    for z0 in {2: (12,), 3: (3, 12)}.get(tier, ()):
        els.append(_el((6, 6, z0), (10, 10, z0 + 1), '#b', glow=tier == 3))
    return els


def capsule_models(write_json, assets):
    for tier, iid in enumerate(CAPSULES, 1):
        tex = f'{MODID}:block/fluid_capsule_{tier}'
        textures = {k: f'{tex}_{k}' for k in ('m', 'd', 'g', 'b')}
        write_json(assets / 'models' / 'item' / f'fluid_capsule_shell_{tier}.json', {
            'parent': 'minecraft:block/block', 'textures': {**textures, 'particle': f'{tex}_m'},
            'render_type': 'minecraft:translucent', 'elements': capsule_elements(tier)})
        # 持ったときの絵は FluidCapsuleRenderer が殻と中の液体を描く
        write_json(assets / 'models' / 'item' / f'{iid}.json', {
            'parent': 'minecraft:builtin/entity', 'gui_light': 'side', 'textures': {'particle': f'{tex}_m'},
            'display': {
                'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.78, 0.78, 0.78]},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.75, 0.75, 0.75]},
                'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.45, 0.45, 0.45]},
                'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.45, 0.45, 0.45]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.45, 0.45, 0.45]},
                'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.45, 0.45, 0.45]}}})


def models(ctx):
    """パイプとカプセルの絵とモデル（gen_data の一般の生成のあとに書く）。"""
    import art16
    write = ctx['write_json']
    assets = Path(ctx['ASSETS'])
    tb = assets / 'textures' / 'block'
    for iid in PIPES:
        full = pipe_side(iid)
        art16.save(full, tb / f'{iid}_full.png')
        art16.save(split(full, opaque=True), tb / f'{iid}.png')
        art16.save(split(full, opaque=False), tb / f'{iid}_glass.png')
        art16.save(pipe_flange(iid), tb / f'{iid}_flange.png')
        art16.save(pipe_rim(iid), tb / f'{iid}_rim.png')
        art16.save(pipe_joint(iid), tb / f'{iid}_joint.png')
    pipe_models(write, assets)
    for tier in (1, 2, 3):
        for key, im in capsule_textures(tier).items():
            art16.save(im, tb / f'fluid_capsule_{tier}_{key}.png')
    capsule_models(write, assets)


# ---------------------------------------------------------------- コラプサー材の使い道（同じ版。collapsar_content）

import collapsar_content as _collapsar  # noqa: E402

IDS.update(_collapsar.IDS)
UI.update(_collapsar.UI)
DESC.update(_collapsar.DESC)
SUMMARY.update(_collapsar.SUMMARY)
# 一般の装置のモデル・正面の絵を作らないブロック（パイプと、ここで書くブロック）
CUSTOM = set(PIPES) | _collapsar.CUSTOM_BLOCKS
CHAPTERS = [CHAPTER, _collapsar.CHAPTER]

_pipe_recipes = recipes
_pipe_models = models


def recipes(R, shaped):  # noqa: F811
    _pipe_recipes(R, shaped)
    _collapsar.recipes(R, shaped)


def models(ctx):  # noqa: F811
    _pipe_models(ctx)
    _collapsar.models(ctx)
