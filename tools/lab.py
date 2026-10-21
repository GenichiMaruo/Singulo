# -*- coding: utf-8 -*-
"""地下アンドロイド研究所の部屋の型紙（.nbt）と、構造物の設定・中身（gen_data.py から呼ぶ）。

研究所は、大きさのちがう部屋を、狭い廊下（断面 3×3）でつないだ施設（並べ方は Java の LabLayout が毎回ランダムに決める）。
ここで作るのは部屋ごとの型紙だけ。部屋は自分の床・壁・天井を持ち、横幅・奥行きはどれも奇数（各辺の真ん中が出入口）。
- ふつうの部屋: 4つの辺の真ん中（幅3・高さ3）が、廊下のつながる出入口の候補。そこから2マス内側までは物を置かない（door_zone）
- 小部屋（annex_*）: 大きな部屋の壁に直接くっつく。出入口は北の辺の真ん中だけ（Java が北を大きな部屋へ向けて置く）

部屋は崩れていない。人がいなくなった今もアンドロイドが直し続けていて、白いまま保たれている。
施設灯は電力が戻るまで消えていて、非常灯だけが赤く灯る（Java が電力で切り替える）。
"""
import math
import random

import nbt
from ruins import Builder, AIR, CACHE, CHAIN_Y, DOCK, FABRICATOR, CORE, LINE, CONVEYOR, MODID, DATA_VERSION

PANEL = f'{MODID}:lab_panel'
LINED = f'{MODID}:lab_panel_lined'
TILES = f'{MODID}:lab_tiles'
FRAME = f'{MODID}:lab_frame'
GLASS = f'{MODID}:lab_glass'
HULL = (f'{MODID}:pressure_hull', {'sealed': 'true'})
LIGHT = (f'{MODID}:facility_light', {'powered': 'false'})
RAYLEIGH = (f'{MODID}:lab_rayleigh_panel', {'lit': 'false'})
EMERGENCY = (f'{MODID}:emergency_light', {'powered': 'false'})
REACTOR_CORE = (f'{MODID}:reactor_core', {'powered': 'false'})
CONDUIT = (f'{MODID}:power_conduit', {'powered': 'false'})
DOOR = f'{MODID}:lab_door'
DARK = 'minecraft:polished_deepslate'
SLAB_TOP = ('minecraft:smooth_quartz_slab', {'type': 'top', 'waterlogged': 'false'})
CATWALK = ('minecraft:polished_deepslate_slab', {'type': 'top', 'waterlogged': 'false'})
STRIPE_Y, STRIPE_K = 'minecraft:yellow_concrete', 'minecraft:black_concrete'

# 記録端末の番号（lab.singulo.log.N の記録）
LOG_RECEPTION, LOG_OFFICE, LOG_MEETING, LOG_ARCHIVE, LOG_LAB, LOG_SERVER, LOG_CONTROL, LOG_DORM, LOG_MEDICAL, LOG_REACTOR, \
    LOG_CAFETERIA, LOG_DOCK = range(12)
LOGS = 12

# ボス部屋をほかの部屋より深く置く分（LabStructure.BOSS_DEPTH と同じ）
BOSS_DEPTH = 4

# 収納の中身（loot table）。部屋に合ったもの: 物資・研究・事務・個室・ロッカー（樽）・厨房・医務
SUPPLIES = f'{MODID}:chests/lab_supplies'
RESEARCH = f'{MODID}:chests/lab_research'
OFFICE = f'{MODID}:chests/lab_office'
QUARTERS = f'{MODID}:chests/lab_quarters'
LOCKER = f'{MODID}:chests/lab_locker'
KITCHEN = f'{MODID}:chests/lab_kitchen'
MEDICAL = f'{MODID}:chests/lab_medical'


def stairs(facing):
    return ('minecraft:quartz_stairs', {'facing': facing, 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})


class Room(Builder):
    """w×h×d の部屋。annex なら出入口は北の辺だけ。"""

    def __init__(self, w, h, d, seed, annex=False):
        assert w % 2 == 1 and d % 2 == 1, (w, d)
        super().__init__(w, h, d, seed)
        self.annex = annex

    @property
    def w(self):
        return self.size[0]

    @property
    def h(self):
        return self.size[1]

    @property
    def d(self):
        return self.size[2]

    def door_zone(self, x, z):
        cx, cz = self.w // 2, self.d // 2
        if z <= 2 and abs(x - cx) <= 2:
            return True
        if self.annex:
            return False
        return (z >= self.d - 3 and abs(x - cx) <= 2) or (x <= 2 and abs(z - cz) <= 2) or (x >= self.w - 3 and abs(z - cz) <= 2)

    def put(self, x, y, z, name, props=None, data=None):
        """家具を置く（出入口の前には置かない）。"""
        if not self.door_zone(x, z):
            self.set(x, y, z, name, props, data)

    def shell(self, wall=PANEL):
        w, h, d = self.size
        self.fill(1, 1, 1, w - 2, h - 2, d - 2, AIR)            # 中は空気（地下に置いたとき岩を消す）
        self.fill(0, 0, 0, w - 1, 0, d - 1, TILES)
        for y in range(1, h - 1):
            for x in range(w):
                for z in range(d):
                    if x in (0, w - 1) or z in (0, d - 1):
                        self.set(x, y, z, LINED if y == 1 else wall)
        self.fill(0, h - 1, 0, w - 1, h - 1, d - 1, PANEL)
        for x in range(2, w - 2, 4):
            for z in range(2, d - 2, 4):
                self.set(x, h - 1, z, *LIGHT)
        # 非常灯: 天井の2つの隅
        self.set(1, h - 1, 1, *EMERGENCY)
        self.set(w - 2, h - 1, d - 2, *EMERGENCY)

    def skylight(self, x0, z0, x1, z1):
        """天井の一部をレイリー散乱パネルにする（電力が戻ると、天窓のように青空が映る）。"""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.set(x, self.h - 1, z, *RAYLEIGH)

    def terminal(self, x, y, z, facing, log):
        self.put(x, y, z, f'{MODID}:lab_terminal', {'facing': facing, 'log': str(log)})

    def chest(self, x, y, z, facing, table=SUPPLIES):
        self.put(x, y, z, 'minecraft:chest', {'facing': facing, 'type': 'single', 'waterlogged': 'false'}, {'LootTable': table})

    def barrel(self, x, y, z, facing='up', table=None):
        self.put(x, y, z, 'minecraft:barrel', {'facing': facing, 'open': 'false'}, {'LootTable': table} if table else None)

    def capsule(self, x, z, facing):
        if not self.door_zone(x, z):
            self.set(x, 1, z, f'{MODID}:android_capsule', {'facing': facing, 'half': 'lower'})
            self.set(x, 2, z, f'{MODID}:android_capsule', {'facing': facing, 'half': 'upper'})

    def desk(self, x0, x1, z, chair=None, dz=0):
        """机（天板は白い石英の上付きハーフ）と、椅子（階段）。"""
        for x in range(x0, x1 + 1):
            self.put(x, 1, z, *SLAB_TOP)
            if chair:
                self.put(x, 1, z + dz, *stairs(chair))

    def tank(self, x, z, glass, top=None):
        self.put(x, 1, z, FRAME)
        self.put(x, 2, z, glass)
        self.put(x, 3, z, glass)
        self.put(x, 4, z, *(top or LIGHT))

    def rack(self, x, z, facing, height=2):
        for y in range(1, height + 1):
            self.put(x, y, z, f'{MODID}:server_rack', {'facing': facing, 'powered': 'false'})

    def plant(self, x, z):
        self.put(x, 1, z, 'minecraft:potted_azalea_bush')

    # ---- 小物（ゴチャゴチャ感）。facing は前（画面・注ぎ口）の向き
    def monitor(self, x, y, z, facing):
        self.put(x, y, z, f'{MODID}:desk_monitor', {'facing': facing})

    def coffee(self, x, y, z, facing):
        """コーヒーメーカー（研究所のものは、はじめから3杯分入っている）。"""
        self.put(x, y, z, f'{MODID}:coffee_maker', {'facing': facing, 'cups': '3', 'brewing': 'false'})

    def papers(self, x, y, z, facing='north'):
        self.put(x, y, z, f'{MODID}:paper_stack', {'facing': facing})

    def trash(self, x, z):
        self.put(x, 1, z, f'{MODID}:trash_bin', {'facing': 'north'})

    def vending(self, x, z, facing):
        if not self.door_zone(x, z):
            # 施設の電力が戻るまで止まっている
            self.set(x, 1, z, f'{MODID}:vending_machine', {'facing': facing, 'half': 'lower', 'powered': 'false'})
            self.set(x, 2, z, f'{MODID}:vending_machine', {'facing': facing, 'half': 'upper', 'powered': 'false'})

    def tray(self, x, y, z):
        self.put(x, y, z, 'minecraft:light_weighted_pressure_plate', {'power': '0'})

    def cup(self, x, y, z, color='white'):
        self.put(x, y, z, f'minecraft:{color}_candle', {'candles': '1', 'lit': 'false', 'waterlogged': 'false'})

    def door(self, x, z, facing, open_=False):
        """研究所のスライドドア（2ブロックの高さ。facing は扉の面の向き。壁の中に置く）。"""
        for y, half in ((1, 'lower'), (2, 'upper')):
            self.set(x, y, z, DOOR, {'facing': facing, 'half': half, 'open': str(open_).lower(), 'powered': 'false'})

    def partition(self, x0, z0, x1, z1):
        """部屋の中の仕切りの壁（床から天井の下まで。腰の高さは帯入りパネル）。"""
        for y in range(1, self.h - 1):
            self.fill(x0, y, z0, x1, y, z1, LINED if y == 1 else PANEL)

    def screens(self, z, x0, x1, ys):
        """壁の大きな監視画面（黒いガラスと施設灯の格子）。"""
        for x in range(x0, x1 + 1):
            for y in ys:
                if (x + y) % 3:
                    self.set(x, y, z, 'minecraft:tinted_glass')
                else:
                    self.set(x, y, z, *LIGHT)


# ---------------------------------------------------------------- 必ずある部屋

def entrance():
    """エントランス（13×13）: 受付のカウンターと端末、待合のベンチ、観葉植物。真ん中の天井は主立坑（Java が開ける）なので空けておく。"""
    b = Room(13, 9, 13, 701)
    b.shell()
    for x in range(1, 4):
        b.put(x, 1, 10, *SLAB_TOP)
    b.terminal(2, 2, 10, 'north', LOG_RECEPTION)
    b.monitor(1, 2, 10, 'south')
    b.papers(3, 2, 10)
    for x in (9, 10, 11):
        b.put(x, 1, 11, *stairs('south'))
    b.vending(11, 9, 'west')
    b.trash(1, 9)
    for x, z in ((1, 1), (11, 1), (1, 11)):
        b.plant(x, z)
    for x in (1, 2, 10, 11):
        b.set(x, 4, 0, 'minecraft:light_blue_concrete')
    return b


def control():
    """メインコントロールルーム（15×11）: 北の壁いっぱいの監視画面、2列の操作卓と端末。"""
    b = Room(15, 9, 11, 703)
    b.shell()
    b.screens(0, 1, 13, (5, 6))
    for z in (4, 7):
        b.desk(2, 4, z, 'south', 1)
        b.desk(10, 12, z, 'south', 1)
        b.terminal(3, 2, z, 'south', LOG_CONTROL)
        b.terminal(11, 2, z, 'south', LOG_CONTROL)
        b.monitor(2, 2, z, 'south')
        b.monitor(12, 2, z, 'south')
        b.papers(4, 2, z)
        b.papers(10, 2, z, 'east')
    b.chest(13, 1, 9, 'west', RESEARCH)
    b.vending(1, 9, 'east')
    b.trash(1, 1)
    b.put(13, 1, 8, *SLAB_TOP)
    b.coffee(13, 2, 8, 'west')
    b.set(13, 1, 1, FABRICATOR)
    return b


def loading_dock():
    """搬入搬出口（17×13、天井が高い）: 止まったコンベア、積まれた木箱、天井のクレーンの鉤、トロッコの線路、溶接して閉じた搬入口。"""
    b = Room(17, 11, 13, 704)
    b.shell()
    for x in range(2, 15):
        b.put(x, 0, 9, CONVEYOR, {'facing': 'east', 'running': 'false', 'warn': 'false'})
    for x, z, n in ((1, 1, 3), (2, 1, 2), (1, 2, 1), (14, 1, 2), (15, 1, 3), (15, 2, 1)):
        for y in range(1, n + 1):
            b.barrel(x, y, z)
    for x in list(range(1, 6)) + list(range(11, 16)):
        for y in range(1, 7):
            b.set(x, y, 12, STRIPE_Y if (x + y) % 2 == 0 else STRIPE_K)
    for x in (5, 11):
        b.fill(x, 7, 6, x, 9, 6, *CHAIN_Y)
        b.set(x, 6, 6, 'minecraft:lightning_rod', {'facing': 'down', 'powered': 'false', 'waterlogged': 'false'})
    for x in range(3, 14):
        b.put(x, 1, 7, 'minecraft:rail', {'shape': 'east_west', 'waterlogged': 'false'})
    b.terminal(15, 1, 3, 'west', LOG_DOCK)
    b.chest(1, 1, 10, 'east')
    b.set(15, 1, 10, FABRICATOR)
    b.dock(8, 1, 4, 3)
    return b


def freight_lift():
    """Dedicated cargo shaft room, with a clear central 3×3 lift and handling equipment."""
    b = Room(17, 11, 17, 727)
    b.shell()
    # Lift landing: reinforced platform and safety border, open up to the shaft ceiling.
    b.fill(6, 0, 6, 10, 0, 10, FRAME)
    b.fill(7, 0, 7, 9, 0, 9, 'minecraft:iron_block')
    for x in range(5, 12):
        for z in range(5, 12):
            if x in (5, 11) or z in (5, 11):
                b.set(x, 0, z, STRIPE_Y if (x+z)%2 else STRIPE_K)
    # Overhead hoist rails flank the central opening; chains stop above walking height.
    for x in (5, 11):
        b.fill(x, 7, 4, x, 7, 12, FRAME)
        for z in (4, 12):
            b.fill(x, 1, z, x, 6, z, FRAME)
        b.fill(x, 5, 8, x, 6, 8, *CHAIN_Y)
        b.set(x, 4, 8, 'minecraft:lightning_rod', {'facing':'down','powered':'false','waterlogged':'false'})
    # Rail trolley lane and conveyor staging bay are separate from all four door approaches.
    for x in range(3, 14):
        b.put(x, 1, 3, 'minecraft:rail', {'shape':'east_west','waterlogged':'false'})
        b.put(x, 0, 13, CONVEYOR, {'facing':'west','running':'false','warn':'false'})
    for x,z,height in ((2,5,2),(2,11,3),(14,5,3),(14,11,2)):
        for y in range(1,height+1): b.barrel(x,y,z)
    b.chest(2,1,13,'east')
    b.terminal(14,1,3,'west',LOG_DOCK)
    for x in (6,10):
        b.set(x,1,5,'minecraft:iron_bars')
        b.set(x,1,11,'minecraft:iron_bars')
    return b


def reactor():
    """
    中央動力炉（19×19、天井が高い）: ボス部屋の直前の部屋。真ん中に天井まで届く炉心の柱（四隅は構造材、面はガラス、中の 3×3 が炉心）。
    床には警告の縞の環と、炉心から4つの隅へ伸びる導管。天井にも導管が十字に走る。炉のまわりの高さ6にキャットウォーク（はしごで上る）。
    南に制御盤のある操作卓、壁には監視画面とサーバーラック。電力が戻ると炉心と導管が水色に光る。
    """
    b = Room(19, 13, 19, 702)
    b.shell()
    c = 9
    for y in range(1, 12):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                x, z = c + dx, c + dz
                if abs(dx) == 2 and abs(dz) == 2:
                    b.set(x, y, z, FRAME)
                elif abs(dx) == 2 or abs(dz) == 2:
                    b.set(x, y, z, FRAME if y in (1, 6, 11) else GLASS)
                else:
                    b.set(x, y, z, *REACTOR_CORE)
    for x in range(c - 3, c + 4):
        for z in range(c - 3, c + 4):
            if max(abs(x - c), abs(z - c)) == 3:
                b.set(x, 0, z, STRIPE_Y if (x + z) % 2 == 0 else STRIPE_K)
    # 床の導管（炉心から4つの隅へ斜めに）と、天井の導管（十字）
    for k in range(4, 9):
        for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            b.set(c + sx * k, 0, c + sz * k, *CONDUIT)
    for k in range(3, 9):
        for x, z in ((c + k, c), (c - k, c), (c, c + k), (c, c - k)):
            b.set(x, 11, z, *CONDUIT)
    # キャットウォーク（高さ6の上、炉のまわり）と、天井から吊る鎖、上るはしご（炉の北の面）
    for x in range(c - 4, c + 5):
        for z in range(c - 4, c + 5):
            if max(abs(x - c), abs(z - c)) == 4:
                b.set(x, 6, z, *CATWALK)
    for x, z in ((c - 4, c - 4), (c + 4, c - 4), (c - 4, c + 4), (c + 4, c + 4)):
        b.fill(x, 7, z, x, 11, z, *CHAIN_Y)
    for y in range(1, 7):
        b.set(c, y, c - 3, 'minecraft:ladder', {'facing': 'north', 'waterlogged': 'false'})
    b.set(c, 6, c - 4, AIR)                                    # はしごの上は、キャットウォークに開けておく
    # 操作卓（南）: 制御盤と、両わきの計器
    b.put(c, 1, 14, f'{MODID}:reactor_console', {'facing': 'north', 'powered': 'false'})
    for dx in (-2, -1, 1, 2):
        b.put(c + dx, 1, 14, *SLAB_TOP)
    b.put(c - 1, 2, 14, f'{MODID}:lab_terminal', {'facing': 'north', 'log': str(LOG_REACTOR)})
    b.put(c + 1, 2, 14, f'{MODID}:server_rack', {'facing': 'north', 'powered': 'false'})
    # 壁: 監視画面とサーバーラック
    b.screens(0, 3, 15, (7, 8))
    for z in list(range(1, 5)) + list(range(14, 18)):
        b.rack(1, z, 'east')
        b.rack(17, z, 'west')
    for x, z in ((3, 3), (15, 3), (3, 15), (15, 15)):
        b.set(x, 11, z, *EMERGENCY)
    # 炉を守る: 両わきの印刷台と、キャットウォークの上の警備機
    b.set(3, 1, 9, FABRICATOR)
    b.set(15, 1, 9, FABRICATOR)
    b.dock(c, 7, c + 4, 3)
    return b


def boss_hall():
    """
    AI中枢・第一量産ホール（ボス部屋、31×19×31）: 壁と天井は耐圧殻。ほかの部屋より4ブロック深く掘り下げて置く（BOSS_DEPTH）ので、
    台地（ボスのステージ）は出入口と同じ高さ、溶岩の溝はその3ブロック下。
    - 真ん中の台地（床の中心に番人の封印核、縁石の環と警告の縞、横切る2本のコンベア、縁に製造ライン）。
      縁には鉄格子の手すり。製造ラインは北と南の縁に2つずつ（上下・左右とも対称）。
      台地の四隅は、さらに3ブロック高い見張り台（階段で上る）。南東の見張り台に保管庫
    - 台地のまわりは幅3の溝で、底に溶岩が流れる。壁の何か所かから溶岩が溝へ流れ落ちる
    - 溝の上の壁には、色ガラスの管がずらっと並ぶ（水色・黄緑・白、上下は構造材の留め具）
    - 4辺の真ん中の出入口（廊下がつながり、隔壁でふさぐ）からは、溝をまたぐ平らな橋で台地へ渡る
    """
    w, hh = 31, 19
    b = Room(w, hh, w, 730)
    top = hh - 1
    c = 15
    lo, hi = 4, 26                        # 台地の範囲（x・z とも）
    up = BOSS_DEPTH + 1                   # 台地の上に立つ高さ（出入口の床と同じ。y=4 が台地の上面）
    fl = up - 1
    b.fill(1, 1, 1, w - 2, top - 1, w - 2, AIR)
    for y in range(0, top + 1):
        for x in range(w):
            for z in range(w):
                if x in (0, w - 1) or z in (0, w - 1) or y in (0, top):
                    b.set(x, y, z, *HULL)
    # 溝: 底に溶岩（y=1）。台地: y=1〜3 を埋め、上面に模様
    for x in range(1, w - 1):
        for z in range(1, w - 1):
            if lo <= x <= hi and lo <= z <= hi:
                b.fill(x, 1, z, x, fl - 1, z, DARK)
                d = math.hypot(x - c, z - c)
                if 8.5 <= d <= 9.5:
                    t = f'{MODID}:striped_ruin_panel'
                elif d <= 3.6:
                    t = FRAME
                elif (x - c) % 6 == 0 and (z - c) % 6 == 0:
                    t = LIGHT
                else:
                    t = DARK
                if isinstance(t, tuple):
                    b.set(x, fl, z, *t)
                else:
                    b.set(x, fl, z, t)
            else:
                b.set(x, 1, z, 'minecraft:lava', {'level': '0'})
    b.set(c, fl, c, CORE)
    for x in range(c - 4, c + 5):
        for z in range(c - 4, c + 5):
            if 2.6 <= math.hypot(x - c, z - c) <= 3.5:
                b.set(x, up, z, 'minecraft:polished_deepslate_slab', {'type': 'bottom', 'waterlogged': 'false'})
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        b.fill(c + dx, 12, c + dz, c + dx, top - 1, c + dz, *CHAIN_Y)
    # 台地を横切るコンベア（見張り台の間）
    for lz in (c - 6, c - 5, c + 5, c + 6):
        for x in range(lo + 1, hi):
            b.set(x, fl, lz, CONVEYOR, {'facing': 'east', 'running': 'false', 'warn': 'false'})
    # 製造ライン: 北と南の縁に2つずつ（真ん中から左右に4）。前を中へ向け、まわりに枠と灯
    for (lx, lz, facing, ax, az) in ((c - 4, lo, 'south', 1, 0), (c + 4, lo, 'south', 1, 0), (c - 4, hi, 'north', 1, 0),
                                     (c + 4, hi, 'north', 1, 0)):
        b.set(lx, up, lz, LINE, {'facing': facing})
        for sgn in (-1, 1):
            b.fill(lx + ax * sgn, up, lz + az * sgn, lx + ax * sgn, up + 3, lz + az * sgn, FRAME)
        b.fill(lx - ax, up + 4, lz - az, lx + ax, up + 4, lz + az, FRAME)
        b.set(lx, up + 3, lz, *EMERGENCY)
    # 台地の縁の手すり（出入口の橋とラインの所は空ける）
    bars_x = ('minecraft:iron_bars', {'east': 'true', 'west': 'true', 'north': 'false', 'south': 'false', 'waterlogged': 'false'})
    bars_z = ('minecraft:iron_bars', {'east': 'false', 'west': 'false', 'north': 'true', 'south': 'true', 'waterlogged': 'false'})
    for k in range(lo, hi + 1):
        if abs(k - c) <= 1:
            continue
        for x, z, bars in ((k, lo, bars_x), (k, hi, bars_x), (lo, k, bars_z), (hi, k, bars_z)):
            if b.get(x, up, z) in (None, AIR):
                b.set(x, up, z, *bars)
    # 出入口の橋: 溝をまたぐ平らな橋（4辺の真ん中、幅3。台地と同じ高さ）
    for t in (-1, 0, 1):
        for k in (1, 2, 3):
            for x, z in ((c + t, k), (c + t, w - 1 - k), (k, c + t), (w - 1 - k, c + t)):
                b.fill(x, 1, z, x, fl - 1, z, FRAME)
                b.set(x, fl, z, TILES)
    # 見張り台: 台地の四隅を3ブロック高く（4×4）。台地の内側へ向いた階段で上る
    for (x0, z0, sx, sz) in ((lo, lo, 1, 1), (hi - 3, lo, -1, 1), (lo, hi - 3, 1, -1), (hi - 3, hi - 3, -1, -1)):
        for x in range(x0, x0 + 4):
            for z in range(z0, z0 + 4):
                b.fill(x, up, z, x, up + 2, z, PANEL)
                b.set(x, up + 2, z, TILES)
        # 階段（台地の内側の辺の真ん中から、内向きに3段）
        ex = x0 + (4 if sx > 0 else -1)
        for k in range(3):
            xs = ex + sx * k
            for zz in (z0 + 1, z0 + 2):
                b.set(xs, up + 2 - k, zz, 'minecraft:quartz_stairs',
                      {'facing': 'west' if sx > 0 else 'east', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
                for yy in range(up, up + 2 - k):
                    b.set(xs, yy, zz, PANEL)
        # 見張り台の縁の灯と柱
        b.fill(x0 + (0 if sx > 0 else 3), up + 3, z0 + (0 if sz > 0 else 3), x0 + (0 if sx > 0 else 3), up + 5,
               z0 + (0 if sz > 0 else 3), FRAME)
        b.set(x0 + (0 if sx > 0 else 3), up + 6, z0 + (0 if sz > 0 else 3), *EMERGENCY)
    # 南東の見張り台の保管庫（番人を倒すまで力場で封鎖）
    b.cache(hi - 1, up + 3, hi - 1, 'android_lab', sealed=True)
    b.set(hi, up + 3, hi, f'{MODID}:lab_terminal', {'facing': 'north', 'log': str(LOG_CONTROL)})
    # 南西の見張り台の封印コンテナ（段階3の鍵で開く。封鎖培養施設と同じ格）
    b.sealed(lo + 1, up + 3, hi - 1, 3)
    # 壁のガラス管: 溝の上の壁際に、色ガラスの縦の管をずらっと並べる（出入口の橋と、溶岩の滝の所は空ける）
    colors = ('minecraft:light_blue_stained_glass', 'minecraft:lime_stained_glass', 'minecraft:white_stained_glass')
    falls = (8, 22)
    for k in range(2, w - 2, 2):
        if abs(k - c) <= 2:
            continue
        glass = colors[(k // 2) % len(colors)]
        for x, z in ((k, 1), (k, w - 2), (1, k), (w - 2, k)):
            if k in falls:
                continue
            b.set(x, up + 1, z, FRAME)
            b.fill(x, up + 2, z, x, top - 3, z, glass)
            b.set(x, top - 2, z, FRAME)
            b.set(x, top - 1, z, *CONDUIT)
    # 溶岩の滝: 壁際の高い所の溶岩が、溝へ流れ落ちる
    for k in falls:
        for x, z in ((k, 1), (k, w - 2), (1, k), (w - 2, k)):
            b.set(x, top - 3, z, 'minecraft:lava', {'level': '0'})
    # 天井: ロボットアームのレールと、灯の環
    for rz in (c - 6, c + 6):
        b.fill(lo, top - 1, rz, hi, top - 1, rz, FRAME)
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            d = math.hypot(x - c, z - c)
            if 7.5 <= d <= 8.3 and (x + z) % 2 == 0 and b.get(x, top - 1, z) in (None, AIR):
                b.set(x, top - 1, z, *LIGHT)
    return b


# ---------------------------------------------------------------- 選ばれる部屋

def research_lab():
    """研究室（15×11）: 壁際の実験台（醸造台・大釜）、標本の槽、端末。警備機が1体。"""
    b = Room(15, 9, 11, 711)
    b.shell()
    b.skylight(5, 4, 9, 6)                                        # 真ん中の天井に 5×3 の空
    for x in range(1, 14):
        b.put(x, 1, 1, *SLAB_TOP)
        b.put(x, 1, 9, *SLAB_TOP)
    for x in (2, 11):
        b.put(x, 2, 1, 'minecraft:brewing_stand', {'has_bottle_0': 'false', 'has_bottle_1': 'false', 'has_bottle_2': 'false'})
    b.put(12, 2, 9, 'minecraft:cauldron')
    for x in (4, 10):
        b.tank(x, 5, 'minecraft:light_blue_stained_glass')
    b.terminal(2, 2, 9, 'north', LOG_LAB)
    b.monitor(10, 2, 1, 'south')
    b.monitor(4, 2, 9, 'north')
    b.papers(4, 2, 1)
    b.papers(10, 2, 9, 'east')
    b.trash(1, 8)
    b.chest(12, 2, 1, 'south', RESEARCH)
    b.dock(7, 1, 5, 3)
    b.set(13, 1, 8, FABRICATOR)
    return b


def meeting():
    """会議室（11×9、小さい）: 机と椅子、奥の画面。"""
    b = Room(11, 7, 9, 712)
    b.shell()
    for x in range(3, 8):
        b.put(x, 1, 4, *SLAB_TOP)
        b.put(x, 1, 3, *stairs('north'))
        b.put(x, 1, 5, *stairs('south'))
    for z in range(2, 7):
        b.set(10, 5, z, 'minecraft:tinted_glass')
    b.terminal(7, 2, 4, 'west', LOG_MEETING)
    b.papers(4, 2, 4)
    b.papers(6, 2, 4, 'east')
    b.cup(5, 2, 4)
    b.plant(1, 1)
    b.put(1, 1, 7, *SLAB_TOP)
    b.coffee(1, 2, 7, 'east')
    b.trash(9, 7)
    return b


def archive():
    """資料室（11×13）: 本棚の列（間を通れる）、書見台、資料の端末と箱。"""
    b = Room(11, 9, 13, 713)
    b.shell()
    for z in (3, 9):
        for x in range(2, 9):
            if x == 5:
                continue
            for y in (1, 2, 3):
                b.put(x, y, z, 'minecraft:bookshelf')
    b.put(7, 1, 6, 'minecraft:lectern', {'facing': 'west', 'has_book': 'false', 'powered': 'false'})
    b.terminal(3, 1, 6, 'east', LOG_ARCHIVE)
    for x, z in ((2, 3), (6, 9), (8, 3)):
        b.papers(x, 4, z)
    b.trash(9, 1)
    b.chest(9, 1, 11, 'west', RESEARCH)
    b.dock(5, 1, 6, 3)
    return b


def office():
    """事務室（13×11、天井が低い）: ガラスの仕切りの机、書類の棚（樽）。"""
    b = Room(13, 7, 11, 714)
    b.shell()
    for x0 in (2, 8):
        for z in (3, 7):
            b.desk(x0, x0 + 2, z, 'south', 1)
            b.monitor(x0, 2, z, 'south')
            b.papers(x0 + 2, 2, z)
    for z in (3, 7):
        b.put(5, 1, z, GLASS)
        b.put(5, 2, z, GLASS)
    b.terminal(3, 2, 3, 'south', LOG_OFFICE)
    b.terminal(9, 2, 7, 'south', LOG_OFFICE)
    for z in (1, 2):
        b.barrel(11, 1, z, 'west')
    b.coffee(11, 2, 1, 'west')
    b.papers(11, 2, 2)
    b.chest(1, 1, 9, 'east', OFFICE)
    b.vending(1, 1, 'south')
    b.trash(11, 9)
    b.trash(4, 9)
    b.dock(6, 1, 5, 3)
    return b


def server_room():
    """サーバールーム（13×11）: 向かい合うラックの列、真ん中の通路。警備機が1体。"""
    b = Room(13, 9, 11, 715)
    b.shell()
    for z in (1, 2, 8, 9):
        b.rack(3, z, 'east', 3)
        b.rack(5, z, 'west', 3)
        b.rack(7, z, 'east', 3)
        b.rack(9, z, 'west', 3)
    for x in (4, 8):
        b.fill(x, 7, 1, x, 7, 9, FRAME)
    b.terminal(11, 1, 1, 'west', LOG_SERVER)
    b.put(11, 1, 8, *SLAB_TOP)
    b.monitor(11, 2, 8, 'west')
    b.dock(6, 1, 5, 3)
    b.set(11, 1, 9, FABRICATOR)
    return b


def clean_room():
    """クリーンルーム（15×15）: ガラスの間仕切りの中の、白い作業台と槽。間仕切りは各辺の真ん中が入口。"""
    b = Room(15, 9, 15, 716)
    b.shell()
    for x in range(3, 12):
        for z in range(3, 12):
            if (x in (3, 11) or z in (3, 11)) and not (6 <= x <= 8 or 6 <= z <= 8):
                for y in (1, 2, 3):
                    b.set(x, y, z, GLASS)
    for x in (5, 9):
        b.put(x, 1, 5, *SLAB_TOP)
    b.tank(5, 9, 'minecraft:white_stained_glass')
    b.tank(9, 9, 'minecraft:white_stained_glass')
    b.dock(7, 1, 7, 3)
    return b


def prototype():
    """試作室（13×13）: 縞の台の上の遺構の印刷台（試作の個体が出てくる）、素体のカプセル、工具。"""
    b = Room(13, 9, 13, 717)
    b.shell()
    for x in range(9, 12):
        for z in range(9, 12):
            b.put(x, 0, z, STRIPE_Y if (x + z) % 2 else STRIPE_K)
    b.set(10, 1, 10, FABRICATOR)
    b.capsule(1, 1, 'east')
    b.capsule(1, 11, 'east')
    b.put(4, 1, 1, 'minecraft:anvil', {'facing': 'east'})
    b.put(3, 1, 1, 'minecraft:smithing_table')
    b.chest(11, 1, 1, 'west', RESEARCH)
    return b


def mass_factory():
    """アンドロイド量産工場（21×15、大きい・天井が高い）: 2本の組立のコンベア、ライン上の作りかけの素体、溶接のアーム。印刷台が1つ。"""
    b = Room(21, 11, 15, 718)
    b.shell()
    for z in (4, 10):
        for x in range(2, 19):
            b.put(x, 1, z, CONVEYOR, {'facing': 'east', 'running': 'false', 'warn': 'false'})
        for x in (5, 15):
            b.put(x, 2, z, 'minecraft:quartz_pillar', {'axis': 'y'})
            b.fill(x, 6, z, x, 9, z, *CHAIN_Y)
            b.set(x, 5, z, 'minecraft:end_rod', {'facing': 'down'})
    b.set(19, 1, 13, FABRICATOR)
    b.capsule(1, 1, 'east')
    b.capsule(2, 1, 'south')
    b.set(1, 1, 13, FABRICATOR)
    b.dock(10, 1, 7, 3)
    return b


def dorm():
    """社員寮（15×9、天井が低い）: 壁際の寝台、ロッカー（樽）、枕もとの灯、個人の記録の端末。"""
    b = Room(15, 7, 9, 719)
    b.shell()
    for x in (2, 4, 10, 12):
        b.put(x, 1, 1, 'minecraft:white_bed', {'facing': 'north', 'occupied': 'false', 'part': 'head'})
        b.put(x, 1, 2, 'minecraft:white_bed', {'facing': 'north', 'occupied': 'false', 'part': 'foot'})
        b.put(x, 1, 7, 'minecraft:white_bed', {'facing': 'south', 'occupied': 'false', 'part': 'head'})
        b.put(x, 1, 6, 'minecraft:white_bed', {'facing': 'south', 'occupied': 'false', 'part': 'foot'})
    for x in (3, 11):
        b.barrel(x, 1, 1, 'south', LOCKER)
        b.put(x, 2, 1, 'minecraft:lantern', {'hanging': 'false', 'waterlogged': 'false'})
    b.terminal(3, 1, 4, 'east', LOG_DORM)
    for x in (2, 4, 10, 12):
        b.put(x, 1, 3, 'minecraft:light_gray_carpet')
        b.put(x, 1, 5, 'minecraft:light_gray_carpet')
    b.trash(13, 7)
    b.papers(11, 2, 1)
    b.chest(11, 1, 7, 'north', QUARTERS)
    b.dock(7, 1, 4, 3)
    return b


def private_rooms():
    """個室区画（21×15、天井が低い）: 真ん中の廊下の両側に、スライドドアのついた研究員の個室が4つずつ並ぶ。
    個室（中は3×3）には寝台・机と椅子・モニターか書類・物入れの樽。開いたままのドアもある。北の出入口から廊下へは、個室の間の通路で抜ける。"""
    b = Room(21, 7, 15, 726)
    b.shell()
    cells = (1, 5, 13, 17)
    for north in (True, False):
        front = 4 if north else 10
        z0, z1 = (1, 3) if north else (11, 13)
        b.partition(1, front, 8, front)
        b.partition(12, front, 19, front)
        for x in (4, 8, 12, 16):
            b.partition(x, z0, x, z1)
        back, mid = (z0, z0 + 1) if north else (z1, z1 - 1)
        for k, a in enumerate(cells):
            n = k + (0 if north else 4)
            b.door(a + 1, front, 'south' if north else 'north', open_=n in (2, 5))
            b.put(a, 1, back, 'minecraft:white_bed', {'facing': 'north' if north else 'south', 'occupied': 'false', 'part': 'head'})
            b.put(a, 1, mid, 'minecraft:white_bed', {'facing': 'north' if north else 'south', 'occupied': 'false', 'part': 'foot'})
            b.put(a + 2, 1, back, *SLAB_TOP)
            b.put(a + 2, 1, mid, *stairs('south' if north else 'north'))
            if n == 1:
                b.terminal(a + 2, 2, back, 'south', LOG_DORM)          # 個人の記録の端末
            elif n % 3 == 0:
                b.papers(a + 2, 2, back)
            else:
                b.monitor(a + 2, 2, back, 'south' if north else 'north')
            near = z1 if north else z0                            # ドアの側の列（真ん中はドアの前なので空ける）
            if n in (3, 6):
                b.chest(a, 1, near, 'east', QUARTERS)
            else:
                b.barrel(a, 1, near, 'east', LOCKER)
            if n % 2:
                b.trash(a + 2, near)
            elif n != 0:
                b.plant(a + 2, near)
    b.plant(8, 5)                                                 # 廊下の小物は、ドアの前を避けて仕切りの角に
    b.plant(12, 9)
    b.trash(16, 9)
    b.dock(10, 1, 7, 2)
    return b


def body_storage():
    """素体保管庫（11×11）: 壁際に並ぶ素体のカプセル（中に白い素体）。真ん中に印刷台。"""
    b = Room(11, 9, 11, 720)
    b.shell()
    for x in (1, 2, 8, 9):
        b.capsule(x, 1, 'south')
        b.capsule(x, 9, 'north')
    for z in (1, 2, 8, 9):
        b.capsule(1, z, 'east')
        b.capsule(9, z, 'west')
    b.set(5, 1, 5, FABRICATOR)
    return b


def test_ground():
    """耐久試験場（17×17、大きい・天井が高い）: 焦げた床の試験の場、的、観察窓のある小部屋（ガラス）。警備機が1体。"""
    b = Room(17, 11, 17, 721)
    b.shell()
    rng = random.Random(721)
    for _ in range(24):
        x, z = rng.randint(3, 13), rng.randint(3, 13)
        b.set(x, 0, z, rng.choice(('minecraft:blackstone', 'minecraft:polished_blackstone', 'minecraft:basalt')))
    for x, z in ((5, 5), (11, 11), (11, 5)):
        b.put(x, 1, z, 'minecraft:target', {'power': '0'})
    for x in range(1, 5):
        b.set(x, 1, 12, FRAME)
        for y in (2, 3):
            b.set(x, y, 12, GLASS)
    for z in (13, 14, 15):
        b.set(4, 1, z, FRAME)
        b.set(4, 2, z, GLASS)
        b.set(4, 3, z, GLASS)
    b.dock(8, 1, 5, 3)
    b.dock(12, 1, 11, 3)
    b.set(15, 1, 15, FABRICATOR)
    return b


def bio_parts():
    """バイオパーツ育成室（13×13）: 緑の培養槽、苔の床の育成棚、光るカエルの灯、大釜。"""
    b = Room(13, 9, 13, 722)
    b.shell()
    for x, z in ((3, 3), (9, 3), (3, 9), (9, 9), (6, 6)):
        b.tank(x, z, 'minecraft:lime_stained_glass', top=('minecraft:verdant_froglight', {'axis': 'y'}))
    for x in range(1, 12):
        if not b.door_zone(x, 11):
            b.set(x, 0, 11, 'minecraft:moss_block')
            b.put(x, 1, 11, 'minecraft:moss_carpet')
    for x in (1, 11):
        b.put(x, 1, 1, 'minecraft:cauldron')
    b.chest(11, 1, 10, 'west', RESEARCH)
    b.dock(6, 1, 9, 3)
    return b


def waste():
    """産業廃棄物処理場（13×11）: 隅の格子のふたの下の処理槽（マグマ）、圧縮機（ピストン）、ホッパーと堆肥箱、廃材の山。"""
    b = Room(13, 9, 11, 723)
    b.shell()
    for x in range(1, 4):
        for z in range(7, 10):
            b.set(x, 0, z, 'minecraft:magma_block')
            b.set(x, 1, z, 'minecraft:iron_trapdoor', {'facing': 'north', 'half': 'bottom', 'open': 'false', 'powered': 'false',
                                                       'waterlogged': 'false'})
    for x in (9, 10, 11):
        b.put(x, 1, 1, 'minecraft:piston', {'facing': 'down', 'extended': 'false'})
        b.put(x, 1, 2, 'minecraft:hopper', {'enabled': 'true', 'facing': 'down'})
    for x, z in ((10, 8), (11, 8), (11, 9)):
        b.put(x, 1, z, 'minecraft:composter', {'level': '3'})
    rng = random.Random(723)
    for _ in range(8):
        x, z = rng.randint(7, 11), rng.randint(6, 9)
        b.put(x, 1, z, rng.choice((FRAME, 'minecraft:iron_block', 'minecraft:barrel')))
    b.set(6, 1, 5, FABRICATOR)
    return b


def medical():
    """医務室（11×9、小さい・天井が低い）: 白いカーテン（羊毛）で仕切った寝台、薬の棚、醸造台。"""
    b = Room(11, 7, 9, 724)
    b.shell()
    for x in (1, 9):
        b.put(x, 1, 7, 'minecraft:white_bed', {'facing': 'south', 'occupied': 'false', 'part': 'head'})
        b.put(x, 1, 6, 'minecraft:white_bed', {'facing': 'south', 'occupied': 'false', 'part': 'foot'})
    for y in (1, 2):
        b.put(2, y, 7, 'minecraft:white_wool')
        b.put(8, y, 7, 'minecraft:white_wool')
    for x in (8, 9):
        b.put(x, 1, 1, *SLAB_TOP)
    b.put(8, 2, 1, 'minecraft:brewing_stand', {'has_bottle_0': 'false', 'has_bottle_1': 'false', 'has_bottle_2': 'false'})
    b.terminal(1, 1, 1, 'east', LOG_MEDICAL)
    b.trash(2, 1)
    b.chest(9, 2, 1, 'south', MEDICAL)
    return b


def cafeteria():
    """食堂（15×13、天井が低い）: 4人がけの机と椅子。南西の隅は、スライドドアと配膳の窓のある厨房（燻製器・流し・食料の樽）。"""
    b = Room(15, 7, 13, 725)
    b.shell()
    b.skylight(6, 5, 8, 7)                                        # 机の間の天井に 3×3 の空
    # 厨房（中は x 1〜4、z 9〜11）: 仕切りの壁、ドア、配膳の窓（腰の高さのカウンターの上が開いている）
    b.partition(1, 8, 5, 8)
    b.partition(5, 8, 5, 11)
    b.door(4, 8, 'north')                                         # 扉がしまわれる両脇（x 3・5）は壁のまま
    for x in (1, 2):
        b.set(x, 2, 8, AIR)
    for x0, z0 in ((3, 3), (9, 3), (9, 9)):
        b.put(x0, 1, z0, *SLAB_TOP)
        b.put(x0 + 1, 1, z0, *SLAB_TOP)
        for x, z, f in ((x0, z0 - 1, 'north'), (x0 + 1, z0 - 1, 'north'), (x0, z0 + 1, 'south'), (x0 + 1, z0 + 1, 'south')):
            b.put(x, 1, z, *stairs(f))
        b.tray(x0, 2, z0)
        b.cup(x0 + 1, 2, z0, 'light_gray' if x0 == 3 else 'white')
    b.put(10, 2, 9, 'minecraft:cake', {'bites': '2'})
    b.put(1, 1, 11, 'minecraft:smoker', {'facing': 'north', 'lit': 'false'})
    b.put(2, 1, 11, 'minecraft:water_cauldron', {'level': '3'})
    b.put(3, 1, 11, 'minecraft:smoker', {'facing': 'north', 'lit': 'false'})
    b.put(4, 1, 11, *SLAB_TOP)
    b.coffee(4, 2, 11, 'north')
    b.barrel(1, 1, 9, 'east', KITCHEN)                            # 配膳の窓の内側の台（食料の樽）
    b.barrel(1, 1, 10, 'east', KITCHEN)
    b.tray(1, 2, 9)
    for x in (1, 2):
        b.vending(x, 1, 'south')
    b.vending(12, 1, 'south')
    b.trash(13, 9)
    b.trash(4, 1)
    b.plant(1, 3)
    b.terminal(13, 1, 2, 'west', LOG_CAFETERIA)
    b.chest(13, 1, 11, 'north', KITCHEN)
    b.dock(7, 1, 6, 3)
    return b


# ---------------------------------------------------------------- 小部屋（大きな部屋の壁に直接くっつく。出入口は北だけ）

def annex_storage():
    """倉庫（7×7）: 棚（樽）と物資の箱。"""
    b = Room(7, 7, 7, 741, annex=True)
    b.shell()
    for x in (1, 5):
        for z in (3, 4, 5):
            for y in (1, 2):
                b.barrel(x, y, z, 'east' if x == 1 else 'west')
    b.chest(3, 1, 5, 'north')
    return b


def annex_locker():
    """ロッカー室（7×9）: 両側のロッカー（樽）と、真ん中のベンチ。"""
    b = Room(7, 7, 9, 742, annex=True)
    b.shell()
    for z in range(3, 8):
        for y in (1, 2):
            b.barrel(1, y, z, 'east')
            b.barrel(5, y, z, 'west')
    for z in (5, 6):
        b.put(3, 1, z, *SLAB_TOP)
    b.papers(3, 2, 5)
    b.trash(3, 7)
    return b


def annex_observation():
    """観察室（7×7）: 奥の壁の観察窓、机と端末。"""
    b = Room(7, 7, 7, 743, annex=True)
    b.shell()
    for x in range(1, 6):
        for y in (2, 3):
            b.set(x, y, 6, GLASS)
    b.desk(2, 4, 5)
    b.terminal(3, 2, 5, 'north', LOG_LAB)
    return b


def annex_substation():
    """変電室（9×7、動力炉の小部屋）: 縞の床に並ぶ変圧器（構造材と導管）、配電盤（ラック）。電力が戻ると導管が光る。"""
    b = Room(9, 7, 7, 744, annex=True)
    b.shell()
    for x in range(1, 8):
        for z in range(3, 6):
            b.set(x, 0, z, STRIPE_Y if (x + z) % 2 else STRIPE_K)
    for x in (1, 7):
        for z in (3, 5):
            b.set(x, 1, z, FRAME)
            b.set(x, 2, z, *CONDUIT)
            b.set(x, 3, z, FRAME)
    for x in range(2, 7):
        b.set(x, 5, 6, *CONDUIT)
    for x in (3, 5):
        b.rack(x, 5, 'north')
    return b


REQUIRED = {'entrance': entrance, 'reactor': reactor, 'control': control, 'loading_dock': loading_dock, 'freight_lift': freight_lift}
OPTIONAL = {'research_lab': research_lab, 'meeting': meeting, 'archive': archive, 'office': office, 'server_room': server_room,
            'clean_room': clean_room, 'prototype': prototype, 'mass_factory': mass_factory, 'dorm': dorm,
            'body_storage': body_storage, 'test_ground': test_ground, 'bio_parts': bio_parts, 'waste': waste,
            'medical': medical, 'cafeteria': cafeteria, 'private_rooms': private_rooms}
ANNEX = {'annex_storage': annex_storage, 'annex_locker': annex_locker, 'annex_observation': annex_observation,
         'annex_substation': annex_substation}


def to_nbt(b):
    out = b.to_nbt()
    out['DataVersion'] = DATA_VERSION
    return out


def write_all(data, write_json, placement, depth):
    """型紙・構造物・置き方・バイオームを書く。placement=(間隔, 最小間隔, salt, バイオーム)、depth=(床の最小, 最大)。"""
    folder = data / 'structure' / 'lab'
    folder.mkdir(parents=True, exist_ok=True)
    for old in folder.glob('*.nbt'):
        old.unlink()
    for name, make in {**REQUIRED, **OPTIONAL, **ANNEX, 'boss_hall': boss_hall}.items():
        nbt.write_gzip(folder / f'{name}.nbt', to_nbt(make()))
    spacing, separation, salt, biomes = placement
    lo, hi = depth
    write_json(data / 'tags' / 'worldgen' / 'biome' / 'has_structure' / 'android_lab.json', {'values': biomes})
    write_json(data / 'worldgen' / 'structure' / 'android_lab.json', {
        'type': f'{MODID}:android_lab',
        'biomes': f'#{MODID}:has_structure/android_lab',
        # 地下の飾り（ディープダークのスカルクの塊・スカルクヴェイン、鍾乳石など）のあとに置く。先に置くと、部屋の空洞に
        # スカルクの塊の起点が来て、床や壁にスカルクヴェインが這う。木や草（vegetal_decoration）よりは前なので、地表の建物は避けて生える
        'step': 'fluid_springs',
        # 暗くても、ふつうの敵は湧かない（施設を守るのは、警備機と遺構の印刷台のアンドロイドだけ）
        'spawn_overrides': {'monster': {'bounding_box': 'full', 'spawns': []}},
        'terrain_adaptation': 'none',
        'floor_min': lo,
        'floor_max': hi,
    })
    write_json(data / 'worldgen' / 'structure_set' / 'android_lab.json', {
        'structures': [{'structure': f'{MODID}:android_lab', 'weight': 1}],
        # 探す位置はチャンクの真ん中（主立坑はそこに立つ。探索コンパスは主立坑を指す）
        'placement': {'type': 'minecraft:random_spread', 'spacing': spacing, 'separation': separation, 'salt': salt,
                      'locate_offset': [8, 0, 8]},
    })


def chest_loot(write_json, data, mid):
    """収納の中身。どれも、研究所の部品（Singulo の品）の組と、その部屋にあったバニラの品の組の2つを引く。
    バニラの品を多めに引いて、開けたときに中がにぎやかに見えるようにする（ばらばらの枠に散らばる）。"""
    def count(e, lo, hi):
        if hi > 1:
            e['functions'] = [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
        return e

    def entry(name, lo, hi, weight):
        return count({'type': 'minecraft:item', 'name': mid(name), 'weight': weight}, lo, hi)

    def v(name, lo, hi, weight, *functions):
        e = count({'type': 'minecraft:item', 'name': f'minecraft:{name}', 'weight': weight}, lo, hi)
        if functions:
            e['functions'] = e.get('functions', []) + list(functions)
        return e

    def table(name, mod_rolls, mod, vanilla_rolls, vanilla):
        pools = [{'rolls': {'type': 'minecraft:uniform', 'min': vanilla_rolls[0], 'max': vanilla_rolls[1]}, 'entries': vanilla}]
        if mod:
            pools.insert(0, {'rolls': {'type': 'minecraft:uniform', 'min': mod_rolls[0], 'max': mod_rolls[1]}, 'entries': mod})
        write_json(data / 'loot_table' / 'chests' / f'{name}.json', {'type': 'minecraft:chest', 'pools': pools})

    healing = {'function': 'minecraft:set_potion', 'id': 'minecraft:healing'}
    regeneration = {'function': 'minecraft:set_potion', 'id': 'minecraft:regeneration'}
    # 物資棚（搬入口・倉庫）: 部品と、資材
    table('lab_supplies', (2, 4), [
        entry('鋼鉄インゴット', 2, 6, 6), entry('基礎回路', 1, 3, 5), entry('鋼板', 2, 4, 4), entry('超伝導コイル', 1, 2, 3),
        entry('自己修理キット', 1, 2, 3), entry('アンドロイドキット', 1, 1, 1)], (4, 7), [
        v('iron_ingot', 2, 6, 6), v('copper_ingot', 2, 8, 6), v('iron_nugget', 3, 9, 5), v('redstone', 2, 8, 5), v('glass', 2, 6, 4),
        v('chain', 1, 3, 3), v('string', 1, 4, 3), v('quartz', 1, 4, 3), v('gunpowder', 1, 3, 2), v('paper', 2, 6, 3),
        v('lever', 1, 2, 2), v('rail', 2, 6, 2), v('bucket', 1, 1, 1)])
    # 研究の資料（研究室・資料室・試作室・生体部品室・制御室）
    table('lab_research', (1, 3), [
        entry('クリスタルメモリ', 1, 2, 5), entry('基礎回路', 2, 4, 4), entry('精密アクチュエーター', 1, 1, 2),
        entry('量子もつれ素子', 1, 2, 3)], (4, 7), [
        v('paper', 3, 10, 6), v('book', 1, 3, 4), v('writable_book', 1, 1, 2), v('glass_bottle', 1, 4, 4), v('redstone', 1, 5, 4),
        v('glowstone_dust', 1, 4, 3), v('amethyst_shard', 1, 3, 3), v('quartz', 1, 4, 3), v('comparator', 1, 1, 2),
        v('repeater', 1, 2, 2), v('ender_pearl', 1, 1, 1), v('experience_bottle', 1, 2, 1)])
    # 事務室の机の引き出し
    table('lab_office', (0, 1), [entry('基礎回路', 1, 2, 3), entry('クリスタルメモリ', 1, 1, 1)], (5, 8), [
        v('paper', 4, 12, 8), v('book', 1, 3, 4), v('writable_book', 1, 1, 3), v('ink_sac', 1, 3, 3), v('feather', 1, 3, 3),
        v('map', 1, 1, 2), v('clock', 1, 1, 1), v('compass', 1, 1, 1), v('cookie', 1, 4, 3), v('string', 1, 3, 2),
        v('name_tag', 1, 1, 1), v('white_candle', 1, 2, 2)])
    # 個室・社員寮の私物
    table('lab_quarters', (0, 1), [entry('自己修理キット', 1, 1, 2), entry('クリスタルメモリ', 1, 1, 1)], (5, 8), [
        v('bread', 1, 4, 5), v('cookie', 2, 6, 4), v('apple', 1, 3, 4), v('book', 1, 2, 4), v('writable_book', 1, 1, 2),
        v('paper', 2, 6, 3), v('white_candle', 1, 3, 3), v('white_wool', 1, 3, 3), v('leather_boots', 1, 1, 1), v('clock', 1, 1, 1),
        v('spyglass', 1, 1, 1), v('gold_nugget', 2, 6, 3), v('emerald', 1, 2, 2), v('item_frame', 1, 2, 2), v('painting', 1, 1, 2),
        v('flower_pot', 1, 1, 2), v('music_disc_13', 1, 1, 1), v('music_disc_cat', 1, 1, 1)])
    # 個室・社員寮のロッカー（樽）: 少しの私物
    table('lab_locker', None, None, (2, 4), [
        v('leather_boots', 1, 1, 2), v('leather_chestplate', 1, 1, 1), v('white_wool', 1, 2, 3), v('string', 1, 3, 3),
        v('paper', 1, 4, 3), v('book', 1, 1, 2), v('cookie', 1, 3, 3), v('white_candle', 1, 2, 2), v('stick', 1, 3, 2),
        v('gold_nugget', 1, 3, 2), v('name_tag', 1, 1, 1)])
    # 厨房・食堂: 食料と調理の道具
    table('lab_kitchen', None, None, (6, 9), [
        v('bread', 2, 6, 6), v('wheat', 3, 8, 4), v('sugar', 2, 6, 4), v('egg', 1, 4, 4), v('apple', 2, 5, 4), v('carrot', 2, 5, 4),
        v('potato', 2, 6, 4), v('baked_potato', 1, 4, 3), v('beetroot', 2, 5, 3), v('cookie', 2, 8, 4), v('pumpkin_pie', 1, 2, 2),
        v('honey_bottle', 1, 2, 2), v('bowl', 1, 4, 3), v('mushroom_stew', 1, 1, 1), v('glass_bottle', 1, 3, 2), v('milk_bucket', 1, 1, 1),
        v('cocoa_beans', 1, 4, 2), v('cake', 1, 1, 1)])
    # 医務室: 手当ての品
    table('lab_medical', (1, 2), [entry('自己修理キット', 1, 2, 4)], (4, 7), [
        v('potion', 1, 1, 3, healing), v('potion', 1, 1, 2, regeneration), v('glass_bottle', 2, 5, 4), v('paper', 2, 5, 3),
        v('honey_bottle', 1, 3, 3), v('sugar', 1, 4, 3), v('white_wool', 1, 3, 3), v('golden_carrot', 1, 2, 2), v('glistering_melon_slice', 1, 2, 2),
        v('golden_apple', 1, 1, 1), v('string', 1, 3, 2)])
