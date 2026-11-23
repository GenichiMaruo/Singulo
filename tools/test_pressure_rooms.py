"""隔壁の開閉と圧力容器の継ぎ目を実際の遺構データで検査する。"""
import unittest
from collections import deque

import ruins


class PressureRoomTests(unittest.TestCase):
    def test_only_the_bulkhead_opens_a_path_from_the_arena(self):
        for make, core, gate in ((ruins.final_lab, (52, 9, 26), (52, 6, 45)),
                                 (ruins.sunken_reactor, (31, 9, 30), (31, 9, 53))):
            with self.subTest(make=make.__name__):
                room = make()
                hull = {p for p, (name, props, _) in room.blocks.items()
                        if name in ('singulo:pressure_hull', ruins.PRESSURE_GLASS) and props['sealed'] == 'true'}
                x, y, z = gate
                door = {(x + dx, y + dy, z) for dx in (-1, 0, 1) for dy in range(4)}
                self.assertTrue(all(room.get(*p) == ruins.BULKHEAD for p in door))
                # All rock, trim, floors, lamps, etc. may be removed. A single-cell breach fails the test.
                pending = deque([core])
                seen = {core}
                while pending:
                    px, py, pz = pending.popleft()
                    self.assertTrue(0 < px < room.size[0] - 1 and 0 < py < room.size[1] - 1
                                    and 0 < pz < room.size[2] - 1, (px, py, pz))
                    for dx, dy, dz in ((1,0,0), (-1,0,0), (0,1,0), (0,-1,0), (0,0,1), (0,0,-1)):
                        p = px + dx, py + dy, pz + dz
                        if p not in seen and p not in hull and p not in door:
                            seen.add(p)
                            pending.append(p)
                for px, py, pz in door:
                    self.assertIn((px, py, pz - 1), seen)
                    self.assertNotIn((px, py, pz + 1), seen)
                    self.assertEqual(ruins.AIR, room.get(px, py, pz + 1))
                    self.assertEqual(ruins.AIR, room.get(px, py, pz - 1))

                peers = {p for p, block in room.blocks.items() if block[0] == ruins.RELAY}
                for (px, py, pz) in peers:
                    data = room.blocks[px, py, pz][2]
                    self.assertEqual(peers, {(px + dx, py + dy, pz + dz) for dx, dy, dz in data['stations']})
                    self.assertEqual([x - px, y - py, z - pz], data['door'])

    def test_viewports_ring_the_boss_room(self):
        """ボス部屋を囲む観測窓（耐圧ガラス）が四方に並ぶ。"""
        for make, center, y in ((ruins.final_lab, ruins.FL_DOME, ruins.FL_BASE + 2), (ruins.sunken_reactor, (ruins.SR_CX, ruins.SR_CZ),
                                                                                       ruins.SR_FLOOR + 2)):
            with self.subTest(make=make.__name__):
                room = make()
                cx, cz = center
                glass = [(x, z) for (x, yy, z), (name, _, _) in room.blocks.items() if yy == y and name == ruins.PRESSURE_GLASS]
                quadrants = {(x > cx, z > cz) for x, z in glass}
                self.assertEqual(4, len(quadrants))
                self.assertGreater(len(glass), 150)

    def test_compass_entrances_open_into_the_facility(self):
        """探索コンパスが指す入口は、最終実験施設は連絡坑道の西の端、沈んだ炉心区画は昇降機の立坑の上の端。"""
        lab = ruins.final_lab()
        x, y, z = ruins.FL_ENTRANCE
        self.assertEqual((0, 6, 65), ruins.FL_ENTRANCE)
        self.assertEqual('minecraft:rail', lab.get(x + 1, y, z))            # 坑道の床の人員輸送の軌道
        self.assertEqual(ruins.AIR, lab.get(x + 1, y + 1, z))
        reactor = ruins.sunken_reactor()
        x, y, z = ruins.SR_ENTRANCE
        self.assertEqual((48, 35, 82), ruins.SR_ENTRANCE)
        self.assertEqual('minecraft:ladder', reactor.get(x + 1, y - 1, z))   # 立坑のはしごが上の端まで続く
        self.assertEqual('minecraft:ladder', reactor.get(x + 1, ruins.SR_FLOOR + 1, z))
