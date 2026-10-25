# -*- coding: utf-8 -*-
"""Singulo の効果音のレシピ（音のファイルのパス → 作り方）。

kind:
- 'loop': 継ぎ目なく繰り返す音（装置の動作音・持続音）。LOOP 秒で作る。
- 'shot': 一回きりの音。
level: 大きさの目安（平均の大きさ dBFS。ピークは -1 dBFS を超えない）。装置の音は控えめ、ボスや大きな出来事は大きめ。
variants: 同じ音の別の版の数（<path>_1.ogg …。生き物の鳴き声などを、鳴るたびに少し変える）。
"""
import numpy as np

from dsp import (SR, t_axis, rng, sine, saw, square, tri, fm, partials, white, pink, brown, crackle, bubbles,
                 lowpass, highpass, bandpass, resonator, env, perc, glide, lfo, reverb, echo, drive, crush,
                 mix, fit, fade, loop, chorus)
from instruments import (hum, buzz, air, pulses, thump, clank, tick, shimmer, throb, whine, whoosh, zap, arc, boom,
                         impact, riser, beep, chime, glass, voices, roar, sub_drop, servo, alarm, heartbeat, ring_burst,
                         pad, groove, scatter, machine_bed, rich)

LOOP = 6.0
RECIPES = {}

MACHINE, SHOT, BOSS, BIG = -27, -18, -15, -13


def recipe(path, kind='shot', level=SHOT, variants=0):
    def wrap(fn):
        RECIPES[path] = dict(fn=fn, kind=kind, level=level, variants=variants)
        return fn
    return wrap


def machine(path, level=MACHINE):
    return recipe(path, 'loop', level)


# =================================================================== ブラックホール

@machine('black_hole/ambient_loop', level=-22)
def black_hole_ambient(d, seed):
    swirl = bandpass(pink(d, seed), 180 + 120 * lfo(1 / 3, d), 2) * (0.6 + 0.4 * lfo(1 / 3, d))
    hawking = highpass(white(d, seed + 1), 6000) * 0.05 * (1 + lfo(0.5, d))
    return throb(d, 1 / 3, 32, 0.6) * 1.2 + sine(21, d) * 0.6 + swirl * 0.7 + hawking


@recipe('black_hole/formation', level=BIG)
def black_hole_formation(d, seed):
    pull = riser(2.6, 60, 900, seed) * 0.8
    implode = whoosh(0.6, 6000, 120, 1.5, seed, 0.5)[::-1] * 1.2
    return reverb(mix(pull, (implode, 2.2), (sub_drop(2.0, 140, 22) * 1.4, 2.7), (boom(1.5, 30, seed, 0.2) * 0.8, 2.75)), 0.9, 0.3, 1800, 1.0)


# =================================================================== 装置（動作音・ループ）
# どれも machine_bed で、空気・電源・リズム・質感・和音・ときどきの音を重ね、部屋の響きでまとめる。
# リズムの型は 16 刻み（'X' 強く、'x' ふつう、'?' ときどき、'.' 休み）。6 秒を 3 小節として繰り返す。

A2, C3, D3, E3, F3, G3, A3, B3, C4, D4, E4, G4, A4 = 110, 130.8, 146.8, 164.8, 174.6, 196, 220, 246.9, 261.6, 293.7, 329.6, 392, 440


@machine('machine/kiln_running')
def kiln(d, seed):
    fire = lowpass(brown(d, seed), 450) * (0.8 + 0.2 * lfo(0.5, d)) + bandpass(crackle(d, 16, seed, 0.008), 2200, 1) * 2.5
    return machine_bed(d, seed, 55, 0.12, (150, 900), 0.3, {'puff': 'x.......x.......', 'tick': '....?.......?...'}, 0.6,
                       fire, 0.9, (A2, E3), 0.06, 500, [lambda s: clank(500, 0.3, s) * 0.3], 2)


@machine('machine/compressor_running')
def compressor(d, seed):
    return machine_bed(d, seed, 60, 0.2, (300, 3000), 0.15,
                       {'deep': 'X.......X.......', 'clank': '.X.......X......', 'hiss': '...x.......x....', 'servo': '......x.......x.', 'tick': 'x.x.x.x.x.x.x.x.'},
                       1.0, None, 0, (D3, A3), 0.05, 600, [lambda s: highpass(white(0.6, s), 1500) * perc(0.6, 0.02, 0.3) * 0.4], 2)


@machine('machine/electrolyzer_running')
def electrolyzer(d, seed):
    fizz = bubbles(d, 30, seed, 350, 1500) * 0.6 + highpass(crackle(d, 40, seed, 0.002), 4000) * 0.8
    return machine_bed(d, seed, 100, 0.08, (200, 1200), 0.25, {'drop': 'x..?..x...?..x..', 'click': '........x.......'}, 0.6,
                       fizz, 1.0, (E3, B3, E4), 0.08, 1200, [lambda s: bubbles(0.6, 40, s, 200, 600)], 2)


@machine('machine/archive_terminal_running')
def archive_terminal(d, seed):
    data = shimmer(d, 2, 2500, 1.5, 0.12, seed) * 0.4
    return machine_bed(d, seed, 120, 0.1, (400, 3000), 0.12,
                       {'blip': 'x.?x..x?.x?..x.?', 'tick': '..x...x...x...x.', 'servo': 'x...............', 'click': '........X.......'},
                       0.9, data, 0.6, (C4, E4, G4), 0.07, 1800, [lambda s: beep(2400, 0.04, 'square') * 0.3, lambda s: tick(900, 0.05, s)], 4)


@machine('machine/precision_assembler_running')
def precision_assembler(d, seed):
    return machine_bed(d, seed, 80, 0.12, (300, 3000), 0.12,
                       {'servo': 'x...x...x.?.x...', 'tick': '..x...x...x...x.', 'ting': '.......x.......?', 'click': 'X.......X.......', 'zap': '?...............'},
                       1.0, None, 0, (G3, D4), 0.05, 1500, [lambda s: clank(1600, 0.2, s) * 0.4, lambda s: servo(0.5, 200, 400, s) * 0.4], 3)


@machine('machine/catalytic_reactor_running')
def catalytic_reactor(d, seed):
    brew = bubbles(d, 14, seed, 200, 800) * 0.5 + highpass(pink(d, seed), 1800) * (0.2 + 0.15 * lfo(0.33, d))
    return machine_bed(d, seed, 80, 0.2, (200, 1500), 0.2, {'pump': 'x.......x.......', 'drop': '..?...?...?...?.'}, 0.8,
                       brew, 0.9, (F3, C4, A4), 0.07, 1000, [lambda s: chime(1800, 0.8, s) * 0.3, lambda s: bandpass(pink(0.8, s), 2500, 2) * perc(0.8, 0.05, 0.4)], 3)


@machine('machine/cooling_tower_running', level=-25)
def cooling_tower(d, seed):
    falls = bandpass(pink(d, seed + 3), 1800, 0.8) * 0.4 + bubbles(d, 10, seed, 900, 2500) * 0.25
    return machine_bed(d, seed, 50, 0.2, (100, 2500), 0.5, {'puff': 'x...x...x...x...'}, 0.4,
                       falls + lowpass(brown(d, seed), 100) * 0.4, 1.0, (D3, A3), 0.04, 500, [lambda s: bubbles(0.8, 25, s, 700, 2000)], 3, room=0.5)


def _orbit_pass(seed, f=1400, length=0.32):
    """粒子の塊がそばを通り過ぎる「シュン」: 近づくと高く、離れると低く（ドップラー）、通る瞬間がいちばん大きい。"""
    t = t_axis(length)
    x = (t - length / 2) / (length / 2)                          # -1（近づく）→ 0（真横）→ 1（離れる）
    doppler = f * (1 - 0.35 * np.tanh(3 * x))
    whoosh_ = bandpass(white(length, seed), doppler, 3) * 0.8 + sine(doppler * 0.5) * 0.25
    return mix(whoosh_ * np.exp(-6 * x ** 2), (zap(0.03, 5000, 2500, seed) * 0.08, length / 2))


def _tube(x):
    """金属の管の中の響き（管の長さで決まる、いくつかの共鳴を重ねる）。"""
    return x * 0.6 + sum(resonator(x, f, 0.08) * g for f, g in ((180, 0.04), (360, 0.03), (540, 0.02), (1270, 0.015)))


def _collision(seed):
    """粒子がぶつかる「パチパチ」: 鋭いはじける音が素早く数回続き、管に小さく響く。"""
    r = rng(seed)
    snaps = mix(*[(highpass(white(0.012, seed + i), 3000) * perc(0.012, 0.0005, 0.003) * r.uniform(0.4, 1), r.uniform(0, 0.12))
                  for i in range(int(r.integers(3, 8)))])
    ping = resonator(snaps, r.uniform(2500, 4200), 0.05) * 0.15
    return mix(snaps, ping, (zap(0.04, 6000, 3000, seed) * 0.15, 0.0))


@machine('machine/particle_accelerator_running', level=-24)
def particle_accelerator(d, seed):
    # 2つの粒子の塊が、別の速さ（毎秒3周と4.5周）で管の中を回り、追い越し合う
    passes = pulses(d, 1 / 3, lambda s: _orbit_pass(seed + int(s * 100), 1300)) + pulses(d, 1 / 4.5, lambda s: _orbit_pass(seed + 7 + int(s * 100), 1900) * 0.7, 0.05)
    # 周回と同じ速さで、管の中の風切りが明るく暗く巡る
    t = t_axis(d)
    swirl = bandpass(pink(d, seed), 900 + 500 * np.sin(2 * np.pi * 3 * t), 2) * (0.5 + 0.5 * np.sin(2 * np.pi * 3 * t)) * 0.4
    magnets = sine(50, d) * (0.6 + 0.4 * np.sin(2 * np.pi * 3 * t)) * 0.5 + sine(100, d) * 0.15
    rf = sine(2850, d) * 0.02 * (1 + 0.5 * lfo(9, d))                # 加速空洞の高い電気の音
    core = _tube(passes + swirl) + magnets + rf
    return machine_bed(d, seed, 50, 0.15, (300, 3000), 0.08, None, 0, core, 1.0, (A2, E3, A3), 0.04, 600,
                       [lambda s: zap(0.15, 6000, 800, s) * 0.4, _collision, _collision, _collision], 6, 0.8, room=0.45)


@machine('machine/thermoelectric_generator_running')
def thermoelectric(d, seed):
    warm = lowpass(brown(d, seed), 300) * 0.3 + bandpass(crackle(d, 5, seed, 0.01), 900, 1) * 1.5
    return machine_bed(d, seed, 60, 0.3, (200, 1500), 0.15, {'click': '...x.......x....', 'tick': '?.......?.......'}, 0.6,
                       warm, 0.8, (C3, G3, E4), 0.07, 800, [lambda s: tick(700, 0.05, s), lambda s: clank(900, 0.2, s) * 0.2], 3)


@machine('machine/cryogenic_turbine_running')
def cryo_turbine(d, seed):
    spin = chorus(whine(1750, d, 0.003, 3) * 0.12, 2, 0.002, 0.5, seed) + highpass(pink(d, seed), 3500) * 0.35
    return machine_bed(d, seed, 42, 0.4, (2000, 8000), 0.25, {'puff': 'x.x.x.x.x.x.x.x.', 'tick': '....x.......x...'}, 0.3,
                       spin, 1.0, (B3, F3 * 2, B3 * 2), 0.05, 2500, [lambda s: highpass(white(0.7, s), 3000) * perc(0.7, 0.05, 0.4) * 0.5], 2, room=0.45)


@machine('machine/smes_cell_running')
def smes(d, seed):
    coil = buzz(120, d, 1400) * 0.4 + sine(2400, d) * 0.03 * (1 + lfo(0.5, d)) + arc(d, seed, 8) * 0.15
    return machine_bed(d, seed, 60, 0.35, (500, 4000), 0.1, {'zap': '?.......?.......', 'click': 'x...............'}, 0.6,
                       coil, 0.9, (A2, D3, A3), 0.06, 900, [lambda s: zap(0.25, 3000, 300, s) * 0.4], 2)


@machine('machine/muon_collector_running')
def muon(d, seed):
    geiger = crackle(d, 11, seed, 0.002) * 2.5
    sky = shimmer(d, 0.8, 3000, 2, 0.3, seed) * 0.3
    return machine_bed(d, seed, 70, 0.2, (1500, 8000), 0.1, {'tick': '?.?.?.?.?.?.?.?.'}, 0.5,
                       geiger + sky, 0.9, (E3, B3, G4), 0.06, 1500, [lambda s: beep(1760, 0.08) * 0.3], 3)


@machine('machine/worldline_anchor_running')
def worldline_anchor(d, seed):
    time_ = voices(d, (A2, E3), 'o', seed, 0.05) * 0.06 * (0.6 + 0.4 * lfo(0.5, d)) + shimmer(d, 0.5, 900, 2, 1.5, seed) * 0.15
    return machine_bed(d, seed, 55, 0.25, (100, 1200), 0.2, {'deep': 'X.......x.......', 'ting': '....?.......?...'}, 0.6,
                       time_ + throb(d, 0.5, 55, 0.7) * 0.5, 1.0, (A2, E3, C4), 0.06, 700, [lambda s: chime(660, 1.5, s) * 0.3], 2, room=0.55)


@machine('machine/advanced_worldline_anchor_running')
def advanced_anchor(d, seed):
    time_ = voices(d, (A2, E3, A3), 'o', seed, 0.05) * 0.07 * (0.6 + 0.4 * lfo(1, d)) + shimmer(d, 1.2, 1100, 2, 1.2, seed) * 0.18
    return machine_bed(d, seed, 55, 0.25, (100, 1500), 0.2, {'deep': 'X...x...X...x...', 'ting': '..?...?...?...?.', 'tick': 'x.x.x.x.x.x.x.x.'}, 0.6,
                       time_ + throb(d, 1, 55, 0.7) * 0.5, 1.0, (A2, E3, A3, C4), 0.07, 900, [lambda s: chime(880, 1.5, s) * 0.3], 3, room=0.6)


@machine('machine/entanglement_synthesizer_running')
def entanglement(d, seed):
    pairs = chorus(sine(440, d) * 0.12 + sine(441.5, d) * 0.12 + sine(660, d) * 0.06 + sine(662.2, d) * 0.06, 2, 0.003, 0.3, seed)
    return machine_bed(d, seed, 60, 0.15, (500, 4000), 0.12, {'blip': '..x...?...x...?.', 'ting': 'x.......?.......'}, 0.6,
                       pairs + shimmer(d, 3, 1500, 3, 0.5, seed) * 0.25, 1.0, (A3, E4), 0.06, 2000, [lambda s: chime(1320, 1.0, s) * 0.3], 3, room=0.5)


@machine('machine/laser_cooler_running')
def laser_cooler(d, seed):
    beam = sine(3200, d) * 0.04 * (1 + 0.3 * lfo(11, d)) + highpass(pink(d, seed), 4500) * 0.2
    return machine_bed(d, seed, 60, 0.2, (200, 1500), 0.3, {'puff': 'x...x...x...x...', 'click': '......x.......x.'}, 0.4,
                       beam, 1.0, (G3, D4, A4), 0.05, 2500, [lambda s: beep(3200, 0.1) * 0.2, lambda s: highpass(white(0.5, s), 4000) * perc(0.5, 0.02, 0.3) * 0.4], 3)


@machine('machine/echo_resonator_running')
def echo_resonator(d, seed):
    rings = fit(echo(shimmer(d, 1.2, 600, 2, 1.0, seed), 0.375, 0.5, 3), d) * 0.4
    return machine_bed(d, seed, 90, 0.2, (300, 2500), 0.12, {'ting': 'x.....x.....x...'}, 0.5,
                       rings, 1.0, (D3, A3, F3 * 2), 0.07, 1200, [lambda s: fit(echo(chime(440, 0.6, s), 0.25, 0.5, 3), 2.0) * 0.4], 2, room=0.65)


@machine('machine/quantum_heat_engine_running')
def quantum_heat(d, seed):
    bells = pulses(d, 1.0, lambda s: fm(900, 1.41, 2, 0.4) * perc(0.4, 0.003, 0.15) * 0.25)
    return machine_bed(d, seed, 75, 0.25, (200, 2000), 0.15, {'thump': 'X.......X.......', 'hiss': '....x.......x...', 'tick': '..x...x...x...x.'}, 0.8,
                       bells, 1.0, (E3, B3, E4), 0.06, 1000, [lambda s: fm(600, 2.0, 3, 0.6) * perc(0.6, 0.005, 0.3) * 0.4], 2)


@machine('machine/inertial_stabilizer_running')
def inertial(d, seed):
    gyro = chorus(whine(900 * (1 + 0.05 * lfo(1 / 3, d)), d, 0.002, 2) * 0.12, 2, 0.002, 0.3, seed)
    return machine_bed(d, seed, 65, 0.35, (300, 2500), 0.15, {'tick': 'x...x...x...x...', 'servo': '........?.......'}, 0.5,
                       gyro, 1.0, (C3, G3, D4), 0.05, 900, [lambda s: whoosh(0.8, 300, 1500, 2, s) * 0.4], 2)


@machine('machine/degenerate_compactor_running', level=-24)
def degenerate_compactor(d, seed):
    groan = lowpass(saw(38 + 4 * lfo(0.5, d), n=int(d * SR)), 300) * 0.4 + lowpass(brown(d, seed), 200) * 0.3
    return machine_bed(d, seed, 40, 0.3, (100, 800), 0.2, {'deep': 'X...............', 'clank': 'x...............', 'hiss': '..x.............', 'pump': '........x...x...'}, 1.1,
                       groan, 1.0, (D3 / 2, A2, D3), 0.06, 400, [lambda s: clank(140, 0.6, s) * 0.5, lambda s: bandpass(saw(glide(60, 45, 1.0)), 300, 3) * perc(1.0, 0.1, 0.6)], 3, room=0.5)


@machine('machine/casimir_cavity_running')
def casimir(d, seed):
    plates = bandpass(white(d, seed), 5000, 8) * (0.5 + 0.5 * lfo(17, d)) * 0.25 + bandpass(white(d, seed + 1), 7000, 10) * 0.1
    return machine_bed(d, seed, 48, 0.4, (2000, 8000), 0.08, {'ting': '?...?...?...?...'}, 0.4,
                       plates, 1.0, (F3, C4, E4), 0.05, 1500, [lambda s: chime(2600, 0.6, s) * 0.2], 3)


@machine('machine/time_crystal_incubator_running')
def time_crystal(d, seed):
    crystal = shimmer(d, 2, 1800, 2, 0.9, seed) * 0.3
    return machine_bed(d, seed, 60, 0.12, (500, 4000), 0.1, {'tick': 'X.......x.......', 'click': '....x.......x...', 'ting': '..?.......?.....'}, 0.8,
                       crystal, 1.0, (C4, E4, G4, B3 * 2), 0.06, 2500, [lambda s: chime(1047, 1.2, s) * 0.4, lambda s: chime(1568, 1.0, s) * 0.3], 3, room=0.55)


@machine('machine/degenerate_furnace_running', level=-24)
def degenerate_furnace(d, seed):
    fire = lowpass(brown(d, seed), 350 + 100 * lfo(1, d)) * (0.8 + 0.2 * lfo(1, d)) + bandpass(crackle(d, 10, seed, 0.01), 1500, 1) * 2
    return machine_bed(d, seed, 36, 0.3, (100, 900), 0.2, {'deep': 'X.......X.......', 'puff': '....x.......x...'}, 0.7,
                       fire + throb(d, 1, 36, 0.5) * 0.5, 1.0, (E3 / 2, B3 / 2, E3), 0.06, 400, [lambda s: clank(200, 0.5, s) * 0.4], 2, room=0.5)


@machine('machine/singularity_encapsulator_running', level=-24)
def encapsulator(d, seed):
    well = throb(d, 0.5, 30, 0.6) + bandpass(pink(d, seed), 300 + 200 * lfo(0.5, d), 3) * 0.5 + buzz(110, d, 900) * 0.1
    return machine_bed(d, seed, 55, 0.2, (100, 1500), 0.15, {'zap': '?.......?.......', 'tick': '....x.......x...'}, 0.5,
                       well, 1.0, (F3 / 2, C3, G3), 0.06, 600, [lambda s: whoosh(1.0, 2000, 200, 2, s) * 0.4], 2, room=0.55)


@machine('machine/shield_tower_running')
def shield_tower(d, seed):
    field = buzz(110, d, 2500) * (0.6 + 0.4 * lfo(0.5, d))
    phased = bandpass(field, 800 + 600 * lfo(1 / 3, d), 2) * 0.6 + bandpass(crackle(d, 5, seed), 3000, 1) * 1.5
    return machine_bed(d, seed, 55, 0.2, (500, 4000), 0.1, {'zap': 'x.......?.......'}, 0.5,
                       phased + field * 0.15, 1.0, (A2, E3, B3), 0.06, 1200, [lambda s: zap(0.3, 2500, 400, s) * 0.4], 2)


@machine('machine/tipler_cylinder_running', level=-24)
def tipler(d, seed):
    spin = bandpass(pink(d, seed), 700, 2) * (0.55 + 0.45 * lfo(8, d)) * 0.8 + whine(1300, d, 0.002, 8) * 0.06
    return machine_bed(d, seed, 40, 0.3, (80, 600), 0.3, {'tick': 'x.x.x.x.x.x.x.x.', 'deep': 'X...............'}, 0.5,
                       spin + lowpass(brown(d, seed), 100) * 0.5, 1.0, (D3 / 2, A2, F3), 0.05, 500, [lambda s: whoosh(0.6, 300, 2000, 2, s) * 0.4], 2, room=0.5)


@machine('machine/wormhole_mouth_ambient', level=-24)
def wormhole_mouth(d, seed):
    wind = bandpass(pink(d, seed), 400 + 300 * lfo(1 / 6, d) + 150 * lfo(0.5, d), 4) * 0.8
    eerie = chorus(sine(220 * (1 + 0.03 * lfo(1 / 3, d)), d) * 0.05 + sine(331 * (1 + 0.02 * lfo(0.5, d)), d) * 0.04, 3, 0.004, 0.2, seed)
    return machine_bed(d, seed, 30, 0.3, (100, 3000), 0.2, None, 0,
                       wind + eerie + shimmer(d, 0.6, 500, 4, 2.0, seed) * 0.2, 1.0, (A2, D3, G3), 0.06, 700,
                       [lambda s: whoosh(1.5, 200, 2500, 3, s) * 0.4, lambda s: chime(330, 2.0, s) * 0.2], 3, room=0.7)


@machine('machine/wormhole_stabilizer_running')
def wormhole_stabilizer(d, seed):
    clamps = bandpass(buzz(165, d, 3000), 1000 + 700 * lfo(0.5, d), 2) * 0.4 + throb(d, 1, 82, 0.4) * 0.3
    return machine_bed(d, seed, 55, 0.3, (200, 2500), 0.15, {'pump': 'x.......x.......', 'tick': '..x...x...x...x.', 'zap': '?...............'}, 0.6,
                       clamps, 1.0, (E3, B3, D4), 0.06, 1000, [lambda s: clank(400, 0.3, s) * 0.3], 2)


@machine('machine/halo_collector_running')
def halo_collector(d, seed):
    dark = highpass(pink(d, seed), 3000) * 0.12 * (1 + 0.3 * lfo(1 / 6, d)) + shimmer(d, 0.9, 700, 2.5, 2.0, seed) * 0.3
    return machine_bed(d, seed, 40, 0.25, (1000, 6000), 0.15, {'ting': '?.......?.......'}, 0.4,
                       dark, 1.0, (D3, A3, E4, F3 * 2), 0.07, 1800, [lambda s: chime(587, 2.0, s) * 0.3], 2, room=0.7)


@machine('machine/containment_tank_running')
def containment_tank(d, seed):
    tank = bubbles(d, 5, seed, 150, 500) * 0.4 + sine(1300, d) * 0.015 * (1 + lfo(0.5, d))
    return machine_bed(d, seed, 50, 0.35, (100, 1200), 0.15, {'pump': 'x...............', 'drop': '......?.......?.'}, 0.6,
                       tank, 1.0, (C3, G3), 0.05, 500, [lambda s: bubbles(0.8, 15, s, 120, 400) * 0.8], 2)


@machine('machine/graviton_manipulator_active')
def graviton_active(d, seed):
    warp = fm(70, 0.5, 2 + lfo(1, d), d) * 0.5 + bandpass(pink(d, seed), 500 + 300 * lfo(2, d), 3) * 0.3
    return machine_bed(d, seed, 70, 0.15, (200, 2000), 0.15, None, 0, warp, 1.0, (D3, A3), 0.06, 800,
                       [lambda s: whoosh(0.6, 400, 1500, 2, s) * 0.3], 2)


@machine('machine/graviton_manipulator_charge')
def graviton_charge(d, seed):
    pulse = fm(110, 1.5, 3, d) * (0.6 + 0.4 * lfo(4, d)) * 0.5 + buzz(220, d, 3000) * 0.15
    return machine_bed(d, seed, 55, 0.3, (500, 4000), 0.12, {'zap': 'x...x...x...x...'}, 0.4, pulse, 1.0, (A2, E3, A3), 0.07, 1500,
                       [lambda s: zap(0.15, 3000, 800, s) * 0.3], 3)


# =================================================================== 装置（一回きり）

@recipe('machine/advanced_worldline_anchor_embed')
def anchor_embed(d, seed):
    return reverb(mix(impact(0.8, 60, seed, 0.4), (chime(330, 2.0, seed) * 0.5, 0.05), (riser(1.2, 60, 200, seed, 0.1)[::-1] * 0.4, 0.1)), 0.6, 0.3)


@recipe('machine/degenerate_furnace_press')
def furnace_press(d, seed):
    hiss = highpass(white(0.8, seed), 1200) * perc(0.8, 0.02, 0.4) * 0.4
    return mix(thump(40, 0.9, 0.5, seed) * 1.2, (clank(160, 0.7, seed) * 0.4, 0.01), (hiss, 0.25))


@recipe('machine/wormhole_generator_charge')
def wormhole_charge(d, seed):
    return reverb(riser(4.0, 50, 700, seed, 0.5) + throb(4.0, 3, 40, 0.5) * env([(0, 0), (4, 1)], 4.0) * 0.6, 0.7, 0.2)


@recipe('machine/wormhole_generator_open', level=BOSS)
def wormhole_open(d, seed):
    swirl = whoosh(3.0, 3000, 200, 3, seed, 0.6) * 0.8
    return reverb(mix(boom(2.0, 35, seed, 0.6), (swirl, 0.1), (shimmer(2.5, 6, 800, 4, 1.0, seed)[::-1] * 0.3, 0.0)), 0.9, 0.35, 2500, 1.5)


@recipe('machine/penrose_reactor_ignition', level=BOSS)
def penrose_ignition(d, seed):
    ignite = mix(riser(2.0, 40, 400, seed), (boom(2.5, 30, seed, 0.5), 1.9), (lowpass(brown(3.0, seed), 600) * env([(0, 0), (0.3, 1), (3, 0.3)], 3.0) * 0.8, 2.0))
    return reverb(ignite, 0.8, 0.3)


@recipe('machine/probe_station_launch')
def probe_launch(d, seed):
    jet = lowpass(brown(3.0, seed), glide(300, 2000, 3.0)) * env([(0, 0), (0.3, 1), (2.2, 0.8), (3, 0)], 3.0)
    return mix(jet, whoosh(3.0, 200, 3000, 1.2, seed, 1.5) * 0.6, impact(0.5, 50, seed) * 0.6)


@recipe('machine/probe_station_return')
def probe_return(d, seed):
    jet = lowpass(brown(2.5, seed), glide(2000, 250, 2.5)) * env([(0, 0), (0.4, 0.8), (2.3, 1), (2.5, 0)], 2.5)
    return mix(jet, whoosh(2.5, 3000, 300, 1.2, seed, 0.7) * 0.5, (impact(0.6, 55, seed, 0.3), 2.3))


@recipe('machine/gravitational_wave_detector_ping')
def gw_ping(d, seed):
    return rich(_gw_ping(seed), seed, 0.1, 0.35)


def _gw_ping(seed):
    # 連星が近づいて合体する「チャープ」: 音程と大きさが加速して上がり、最後に減衰振動（リングダウン）
    t = t_axis(1.6)
    tau = np.maximum(1.62 - t, 0.02)
    f = 35 * tau ** (-3 / 8)
    chirp = sine(np.minimum(f, 900)) * np.minimum(1, (0.25 / tau) ** 0.25)
    chirp *= env([(0, 0), (0.2, 0.4), (1.55, 1), (1.6, 0)], 1.6)
    ring = sine(900, 0.5) * np.exp(-t_axis(0.5) / 0.08)
    return reverb(mix(chirp * 0.7, (ring * 0.6, 1.58), (beep(1760, 0.15) * 0.3, 1.75)), 0.4, 0.2)


@recipe('machine/multiblock_formed')
def multiblock_formed(d, seed):
    notes = mix((chime(523, 1.2, 1) * 0.6, 0), (chime(659, 1.2, 2) * 0.6, 0.12), (chime(784, 1.6, 3) * 0.7, 0.24))
    chord = pad(1.8, (261.6, 392, 523), 1400, 0.3, seed) * env([(0, 0), (0.3, 1), (1.8, 0)], 1.8) * 0.5
    locks = mix(clank(500, 0.4, seed) * 0.4, (thump(80, 0.3, 0.3, seed) * 0.5, 0.0), (servo(0.3, 300, 500, seed) * 0.2, 0.05))
    return rich(mix(locks, notes, (chord, 0.2), (shimmer(1.5, 8, 1500, 2, 0.6, seed) * 0.2, 0.3)), seed, 0.1, 0.35)


@recipe('machine/graviton_manipulator_throw')
def graviton_throw(d, seed):
    return mix(whoosh(0.7, 200, 2500, 1.5, seed, 0.6), thump(60, 0.3, 0.3, seed) * 0.8, fm(80, 0.5, 4, 0.5) * perc(0.5, 0.01, 0.2) * 0.5)


@recipe('machine/keraunos_tower_charge')
def keraunos_charge(d, seed):
    return riser(2.5, 100, 1200, seed, 0.2) * 0.6 + arc(2.5, seed, 40) * env([(0, 0), (2.5, 1)], 2.5) * 0.5


@recipe('machine/keraunos_tower_discharge', level=BOSS)
def keraunos_discharge(d, seed):
    crack = mix(zap(0.25, 6000, 300, seed) * 1.2, highpass(white(0.15, seed), 1000) * perc(0.15, 0.001, 0.05))
    thunder = lowpass(brown(3.0, seed + 1), glide(1500, 150, 3.0)) * env([(0, 0), (0.05, 1), (0.6, 0.6), (3, 0)], 3.0)
    return reverb(mix(crack, (thunder, 0.05)), 0.8, 0.3)


@recipe('machine/keraunos_tower_mode')
def keraunos_mode(d, seed):
    relay = mix(clank(700, 0.2, seed) * 0.4, (thump(120, 0.12, 0.4, seed) * 0.4, 0.0))
    tones = mix((beep(880, 0.08, 'square') * 0.3, 0.05), (beep(1320, 0.1, 'square') * 0.3, 0.13))
    return rich(mix(relay, tones, (arc(0.4, seed, 50) * 0.25, 0.08)), seed, 0.1, 0.25)


@recipe('machine/impact_generator_absorb')
def impact_absorb(d, seed):
    return mix(thump(55, 0.3, 0.2, seed), (zap(0.3, 400, 2500, seed) * 0.4, 0.05), (beep(1500, 0.15) * 0.15, 0.25))


@recipe('machine/deep_sea_collector_collect')
def deep_sea_collect(d, seed):
    creak = bandpass(saw(glide(90, 60, 0.6)) * perc(0.6, 0.05, 0.4), 400, 3) * 0.6
    return reverb(mix(creak, (bubbles(0.5, 20, seed, 200, 600) * 0.4, 0.1), (chime(1800, 1.2, seed) * 0.4, 0.4)), 0.7, 0.4, 1500)


@recipe('machine/void_collector_collect')
def void_collect(d, seed):
    return reverb(mix(whoosh(0.8, 4000, 300, 2, seed, 0.6)[::-1] * 0.5, (shimmer(0.8, 12, 2500, 2, 0.4, seed) * 0.4, 0.5)), 0.8, 0.4)


@recipe('nature/fulgurite_form')
def fulgurite(d, seed):
    sizzle = highpass(crackle(1.0, 60, seed, 0.003), 2000) * perc(1.0, 0.01, 0.5) * 2
    return mix(zap(0.3, 5000, 400, seed), (sizzle, 0.05), (glass(0.8, seed, 12) * 0.4, 0.3))


# =================================================================== 封印コンテナ

@recipe('sealed/denied')
def sealed_denied(d, seed):
    buzzer = mix(beep(220, 0.18, 'square') * 0.5, (beep(165, 0.3, 'square') * 0.5, 0.2))
    return rich(mix(buzzer, thump(70, 0.2, 0.3, seed) * 0.5, (clank(260, 0.25, seed) * 0.25, 0.2), (buzz(110, 0.5, 1200) * perc(0.5, 0.01, 0.3) * 0.2, 0.0)), seed, 0.1, 0.25)


@recipe('sealed/insert')
def sealed_insert(d, seed):
    slide = bandpass(white(0.25, seed), 2500, 2) * env([(0, 0), (0.05, 0.5), (0.25, 0.3)], 0.25)
    return mix(slide, (tick(2200, 0.03, seed) * 0.9, 0.24), (clank(900, 0.2, seed) * 0.3, 0.25))


@recipe('sealed/scan')
def sealed_scan(d, seed):
    sweep = sine(glide(600, 2400, 1.0)) * 0.2 * perc(1.0, 0.05, 1.0, 1)
    laser = bandpass(white(1.0, seed), glide(1500, 4000, 1.0), 6) * 0.3 * env([(0, 0), (0.1, 1), (1, 0)], 1.0)
    return rich(mix(sweep, laser, hum(120, 1.0, seed=seed) * 0.1, *[(beep(1800 + 200 * i, 0.06) * 0.3, 0.15 * i) for i in range(6)]), seed, 0.1, 0.3)


@recipe('sealed/unlock')
def sealed_unlock(d, seed):
    clunks = mix(clank(300, 0.4, seed) * 0.5, (clank(380, 0.4, seed + 1) * 0.5, 0.18), (thump(70, 0.3, 0.3, seed), 0.35))
    return reverb(mix(clunks, (chime(880, 1.2, seed) * 0.4, 0.45), (chime(1320, 1.2, seed + 1) * 0.3, 0.55)), 0.5, 0.3)


@recipe('sealed/vent')
def sealed_vent(d, seed):
    return highpass(pink(1.8, seed), 900) * env([(0, 0), (0.05, 1), (0.4, 0.7), (1.8, 0)], 1.8) + lowpass(brown(1.8, seed), 200) * 0.3 * perc(1.8, 0.02, 0.6)


@recipe('sealed/open')
def sealed_open(d, seed):
    seal = highpass(white(0.5, seed), 1500) * perc(0.5, 0.005, 0.15) * 0.5
    return mix(seal, (servo(1.4, 140, 220, seed) * 0.5, 0.1), (clank(260, 0.4, seed) * 0.4, 1.45))


@recipe('sealed/rise')
def sealed_rise(d, seed):
    return mix(servo(1.6, 120, 260, seed) * 0.6, (thump(80, 0.25, 0.2, seed) * 0.5, 1.55))


@recipe('sealed/lock')
def sealed_lock(d, seed):
    hiss = highpass(white(0.4, seed), 2000) * perc(0.4, 0.01, 0.2) * 0.3
    return rich(mix(servo(0.4, 260, 150, seed) * 0.3, (clank(220, 0.5, seed) * 0.6, 0.35), (thump(60, 0.3, 0.3, seed), 0.4), (hiss, 0.45),
                    (beep(330, 0.2) * 0.25, 0.65)), seed, 0.08, 0.25)


# =================================================================== 道具

@recipe('item/gravity_boots_wall_jump')
def wall_jump(d, seed):
    spring = sine(glide(300, 700, 0.15)) * perc(0.15, 0.003, 0.08) * 0.3
    return mix(thump(110, 0.12, 0.4, seed) * 0.6, spring, whoosh(0.35, 400, 2500, 1.5, seed, 0.6) * 0.5)


@recipe('item/dimensional_pocket_open')
def pocket_open(d, seed):
    return reverb(mix(whoosh(0.5, 300, 4000, 2, seed, 1.5) * 0.5, (shimmer(0.6, 15, 1500, 2.5, 0.35, seed) * 0.3, 0.2)), 0.6, 0.3)


@recipe('item/dimensional_pocket_close')
def pocket_close(d, seed):
    pop = sine(glide(500, 150, 0.08)) * perc(0.08, 0.002, 0.04)
    return rich(mix(whoosh(0.4, 4000, 300, 2, seed, 0.6) * 0.5, (pop * 0.6, 0.35), (shimmer(0.4, 10, 1500, 2, 0.3, seed)[::-1] * 0.2, 0.0)), seed, 0.1, 0.3)


# =================================================================== ボス: 浮かぶ製造ライン「ファウンドリ」（重い工場の機械）

@recipe('boss/foundry_emerge', level=BOSS)
def foundry_emerge(d, seed):
    startup = riser(3.0, 30, 220, seed, 0.2) * 0.8
    clanks = mix(*[(clank(180 + 60 * i, 0.5, seed + i) * 0.4, 0.4 + 0.45 * i) for i in range(5)])
    return reverb(mix(startup, clanks, (alarm(1.2, 660, 440, 3) * 0.2, 1.8), (impact(1.0, 45, seed, 0.6), 3.0)), 0.9, 0.3)


@recipe('boss/foundry_hurt', level=BOSS)
def foundry_hurt(d, seed):
    grind = bandpass(saw(glide(160, 110, 0.5)) + white(0.5, seed) * 0.4, 900, 2) * perc(0.5, 0.005, 0.3)
    return mix(clank(330, 0.5, seed) * 0.7, grind * 0.5)


@recipe('boss/foundry_arm_aim')
def foundry_arm_aim(d, seed):
    gears = groove(0.9, {'tick': 'x.x.x.x.x.x.x.x.'}, 0.9 / 16, seed) * 0.3
    return rich(mix(servo(0.9, 140, 280, seed) * 0.6, gears, highpass(white(0.9, seed), 3000) * 0.05, (clank(600, 0.3, seed) * 0.4, 0.85)), seed, 0.05, 0.3)


@recipe('boss/foundry_arm_grab', level=BOSS)
def foundry_arm_grab(d, seed):
    return mix(servo(0.4, 300, 180, seed) * 0.5, (clank(260, 0.4, seed) * 0.8, 0.35), (whoosh(0.6, 300, 2500, 1.5, seed, 0.5) * 0.6, 0.5))


@recipe('boss/foundry_press_aim')
def foundry_press_aim(d, seed):
    hydraulic = lowpass(saw(glide(50, 75, 1.2)), 400) * env([(0, 0), (0.1, 0.6), (1.2, 1)], 1.2)
    return hydraulic * 0.6 + highpass(pink(1.2, seed), 2000) * env([(0, 0), (1.2, 0.3)], 1.2)


@recipe('boss/foundry_press_slam', level=BIG)
def foundry_press_slam(d, seed):
    return reverb(mix(thump(35, 1.2, 0.8, seed) * 1.4, clank(140, 1.0, seed) * 0.6, boom(1.2, 40, seed, 0.3) * 0.6), 0.8, 0.25)


@recipe('boss/foundry_conveyor_warn')
def foundry_conveyor_warn(d, seed):
    siren = alarm(1.0, 990, 740, 4) * env([(0, 0), (0.02, 1), (0.98, 1), (1.0, 0)], 1.0) * 0.5
    beacon = bandpass(pink(1.0, seed), 1500, 2) * (0.5 + 0.5 * lfo(2, 1.0)) * 0.2
    return rich(mix(siren, beacon, groove(1.0, {'clank': 'x...x...x...x...', 'tick': '..x...x...x...x.'}, 1.0 / 16, seed) * 0.5), seed, 0.08, 0.4)


@recipe('boss/foundry_conveyor_flip')
def foundry_conveyor_flip(d, seed):
    motor = lowpass(saw(glide(120, 40, 0.4)), 800) * 0.5
    return mix(clank(200, 0.4, seed) * 0.6, motor, (lowpass(saw(glide(40, 120, 0.5)), 800) * 0.5, 0.4))


@recipe('boss/foundry_laser_aim')
def foundry_laser_aim(d, seed):
    return whine(glide(600, 2400, 1.5), 1.5, 0.01, 12) * 0.25 * env([(0, 0), (1.5, 1)], 1.5) + arc(1.5, seed, 20) * 0.2


@recipe('boss/foundry_laser_fire', level=BOSS)
def foundry_laser_fire(d, seed):
    beam = bandpass(white(1.8, seed), 2500, 1.5) * 0.6 + saw(180 + 10 * lfo(13, 1.8), n=int(1.8 * SR)) * 0.25
    sizzle = crackle(1.8, 80, seed, 0.003) * 1.5
    return (lowpass(beam, 5000) + sizzle) * env([(0, 0), (0.03, 1), (1.6, 1), (1.8, 0)], 1.8)


@recipe('boss/foundry_print')
def foundry_print(d, seed):
    r = rng(seed)
    zips = mix(*[(servo(0.12, r.uniform(500, 900), r.uniform(500, 900), seed + i) * 0.3, 0.13 * i) for i in range(9)])
    return mix(zips, (tick(3000, 0.02, seed) * 0.6, 1.2))


@recipe('boss/foundry_line_break', level=BOSS)
def foundry_line_break(d, seed):
    wind_down = lowpass(saw(glide(140, 30, 1.6)), 900) * env([(0, 1), (1.6, 0)], 1.6) * 0.6
    sparks = bandpass(crackle(1.0, 30, seed, 0.004), 3000, 1) * 2
    return mix(clank(240, 0.5, seed) * 0.6, wind_down, (sparks, 0.05))


@recipe('boss/foundry_enrage', level=BOSS)
def foundry_enrage(d, seed):
    return reverb(mix(alarm(2.0, 880, 587, 5) * 0.5, riser(2.0, 60, 300, seed, 0.3) * 0.6, (impact(0.8, 50, seed, 0.6), 1.9)), 0.8, 0.3)


@recipe('boss/foundry_death', level=BIG)
def foundry_death(d, seed):
    falls = mix(*[(clank(120 + 40 * i, 0.8, seed + i) * 0.5, 0.3 * i) for i in range(6)])
    down = lowpass(saw(glide(120, 20, 3.5)), 600) * env([(0, 1), (3.5, 0)], 3.5) * 0.5
    return reverb(mix(boom(3.0, 32, seed, 0.6), falls, down, (boom(2.0, 28, seed + 3, 0.3) * 0.7, 1.5)), 0.95, 0.3, 2000, 1.5)


# =================================================================== ボス: 残響の番人（光の像・ガラス・こだま）

def _glitch(x, seed=0):
    return crush(x, 5, 3)


@recipe('boss/echo_sentinel_emerge', level=BOSS)
def echo_emerge(d, seed):
    gather = shimmer(3.0, 18, 900, 4, 0.8, seed)[::-1] * 0.5
    return reverb(mix(gather, (chime(440, 2.5, seed) * 0.6, 2.8), (voices(2.0, (220, 330, 440), 'a', seed) * 0.03, 1.8)), 0.95, 0.4, 3000, 1.5)


@recipe('boss/echo_sentinel_ambient', level=BOSS - 4, variants=3)
def echo_ambient(d, seed):
    whisper = bandpass(pink(1.6, seed), 1200 + 600 * rng(seed).random(), 5) * env([(0, 0), (0.4, 1), (1.6, 0)], 1.6)
    return fit(echo(mix(whisper * 0.5, chime(600 * 1.5 ** rng(seed).integers(0, 3), 1.2, seed) * 0.2), 0.28, 0.5, 4), 2.5)


@recipe('boss/echo_sentinel_hurt', level=BOSS)
def echo_hurt(d, seed):
    return _glitch(mix(chime(700, 0.4, seed) * 0.6, zap(0.25, 3000, 500, seed) * 0.6), seed)


@recipe('boss/echo_sentinel_bolt')
def echo_bolt(d, seed):
    return reverb(mix(zap(0.3, 3500, 700, seed) * 0.7, chime(1400, 0.4, seed) * 0.3), 0.4, 0.25)


@recipe('boss/echo_sentinel_blink')
def echo_blink(d, seed):
    return _glitch(mix(whoosh(0.3, 3000, 600, 2, seed, 0.6) * 0.8, beep(2200, 0.05) * 0.3), seed)


@recipe('boss/echo_sentinel_strike_windup')
def echo_strike_windup(d, seed):
    rise = sum(sine(glide(500 * k, 2000 * k, 0.9)) * a for k, a in ((1, 1), (1.5, 0.4), (2.01, 0.3))) * env([(0, 0), (0.9, 0.5)], 0.9)
    return rich(mix(rise, shimmer(0.9, 20, 1500, 2, 0.3, seed) * 0.3, whoosh(0.9, 800, 5000, 3, seed, 2.0) * 0.3), seed, 0.1, 0.4)


@recipe('boss/echo_sentinel_strike', level=BOSS)
def echo_strike(d, seed):
    return reverb(mix(whoosh(0.35, 1200, 6000, 2.5, seed, 0.5), (chime(1100, 0.8, seed) * 0.5, 0.08)), 0.5, 0.3)


@recipe('boss/echo_sentinel_lance_aim')
def echo_lance_aim(d, seed):
    e = env([(0, 0), (1.2, 0.6)], 1.2)
    tone = (sine(glide(300, 1200, 1.2)) + sine(glide(303, 1212, 1.2)) + 0.4 * saw(glide(150, 600, 1.2))) * e
    return rich(mix(lowpass(tone, 3000), arc(1.2, seed, 25) * e * 0.3, shimmer(1.2, 10, 1200, 2, 0.4, seed) * e * 0.3), seed, 0.1, 0.4)


@recipe('boss/echo_sentinel_lance_fire', level=BOSS)
def echo_lance_fire(d, seed):
    beam = bandpass(white(0.9, seed), 3000, 2) * perc(0.9, 0.005, 0.5)
    return reverb(mix(zap(0.5, 5000, 600, seed), beam * 0.7, chime(900, 1.0, seed) * 0.4), 0.7, 0.3)


@recipe('boss/echo_sentinel_circle_cast')
def echo_circle_cast(d, seed):
    ring = mix(*[(chime(600 * 2 ** (k / 12), 1.2, seed + k) * 0.3, 0.07 * i) for i, k in enumerate((0, 4, 7, 12, 16))])
    return reverb(ring, 0.7, 0.4)


@recipe('boss/echo_sentinel_circle_erupt', level=BOSS)
def echo_circle_erupt(d, seed):
    return reverb(mix(whoosh(0.8, 300, 5000, 2, seed, 0.5), zap(0.4, 4000, 1000, seed) * 0.5, chime(500, 1.0, seed) * 0.3), 0.7, 0.3)


@recipe('boss/echo_sentinel_summon')
def echo_summon(d, seed):
    return reverb(mix(*[(chime(500 * 1.25 ** i, 0.8, seed + i) * 0.3, 0.12 * i) for i in range(4)], whoosh(0.8, 500, 3000, 2, seed) * 0.3), 0.8, 0.4)


@recipe('boss/echo_sentinel_echo_vanish')
def echo_vanish(d, seed):
    return reverb(mix(shimmer(0.8, 15, 1200, 2, 0.5, seed)[::-1] * 0.5, whoosh(0.6, 3000, 600, 2, seed, 0.6) * 0.3), 0.6, 0.3)


@recipe('boss/echo_sentinel_death_unravel', level=BOSS)
def echo_unravel(d, seed):
    falling = sum(sine(glide(800 * 1.5 ** k, 100 * 1.5 ** k, 3.0)) for k in range(3)) * env([(0, 1), (3, 0)], 3.0) * 0.3
    return reverb(_glitch(falling + shimmer(3.0, 10, 800, 3, 0.6, seed) * 0.3, seed), 0.9, 0.4, 3000, 1.0)


@recipe('boss/echo_sentinel_death_shatter', level=BIG)
def echo_shatter(d, seed):
    return reverb(mix(glass(1.8, seed, 70), impact(0.6, 70, seed) * 0.6), 0.9, 0.35, 5000, 1.5)


@recipe('boss/echo_projector_power_down')
def echo_projector_down(d, seed):
    fade_ = lowpass(saw(glide(400, 40, 1.2)) + 0.5 * saw(glide(600, 60, 1.2)), 1500) * env([(0, 0.6), (1.2, 0)], 1.2) * 0.5
    flicker = crush(shimmer(1.2, 10, 1200, 2, 0.3, seed), 5, 3) * env([(0, 1), (1.2, 0)], 1.2) * 0.3
    return rich(mix(fade_, flicker, (clank(500, 0.3, seed) * 0.4, 1.15)), seed, 0.08, 0.3)


# =================================================================== ボス: 重力の澱（暗い質量・岩・水槽）

@recipe('boss/gravity_remnant_emerge', level=BOSS)
def remnant_emerge(d, seed):
    return reverb(mix(heartbeat(3.5, 50, 42) * 1.2, lowpass(brown(3.5, seed), 150) * 0.4, bubbles(3.5, 3, seed, 100, 300) * 0.4), 0.7, 0.3, 1200)


@recipe('boss/containment_tank_crack', level=BOSS)
def tank_crack(d, seed):
    cracks = mix(*[(highpass(white(0.05, seed + i), 2000) * perc(0.05, 0.001, 0.02) * 0.8, 0.09 * i) for i in range(7)])
    return reverb(mix(cracks, chime(2600, 0.8, seed) * 0.2), 0.6, 0.3)


@recipe('boss/containment_tank_shatter', level=BIG)
def tank_shatter(d, seed):
    splash = lowpass(pink(1.5, seed), 1800) * env([(0, 0), (0.05, 1), (1.5, 0)], 1.5)
    return reverb(mix(glass(1.5, seed, 60), splash * 0.7, impact(0.8, 55, seed) * 0.6), 0.8, 0.3)


@recipe('boss/gravity_remnant_ambient', level=BOSS - 3, variants=2)
def remnant_ambient(d, seed):
    groan = lowpass(saw(glide(55, 40 + 10 * rng(seed).random(), 2.5)), 250) * env([(0, 0), (0.6, 1), (2.5, 0)], 2.5)
    return groan * 0.8 + throb(2.5, 1.5, 33, 0.7) * 0.6


@recipe('boss/gravity_remnant_hurt', level=BOSS)
def remnant_hurt(d, seed):
    rock = lowpass(white(0.4, seed), 2500) * perc(0.4, 0.002, 0.1)
    return mix(rock, thump(65, 0.35, 0.2, seed) * 0.7, (rock * 0.5, 0.06))


@recipe('boss/gravity_remnant_debris_launch', level=BOSS)
def debris_launch(d, seed):
    return mix(whoosh(0.8, 150, 1500, 1.2, seed, 0.6) * 0.9, thump(50, 0.3, 0.4, seed) * 0.6)


@recipe('boss/gravity_debris_impact', level=BOSS)
def debris_impact(d, seed):
    shards = mix(*[(lowpass(white(0.2, seed + i), 3000) * perc(0.2, 0.002, 0.05) * 0.5, 0.04 * i) for i in range(5)])
    return mix(impact(0.6, 60, seed), shards)


@recipe('boss/gravity_remnant_shell_regrow', level=BOSS)
def shell_regrow(d, seed):
    gather = mix(*[(lowpass(white(0.15, seed + i), 2000) * perc(0.15, 0.002, 0.05) * 0.4, 0.1 * i) for i in range(14)])
    return reverb(mix(gather[::-1], whoosh(1.6, 2000, 200, 1.5, seed, 0.8)[::-1] * 0.4), 0.6, 0.3)


@recipe('boss/gravity_remnant_lift', level=BOSS)
def remnant_lift(d, seed):
    return reverb(mix(whoosh(1.4, 200, 2500, 1.2, seed, 1.5) * 0.7, sine(glide(40, 120, 1.4)) * env([(0, 0), (1.4, 0.8)], 1.4)), 0.7, 0.3)


@recipe('boss/gravity_remnant_slam', level=BIG)
def remnant_slam(d, seed):
    return reverb(mix(boom(1.8, 30, seed, 0.5) * 1.2, thump(32, 1.0, 0.6, seed)), 0.8, 0.3)


@machine('boss/gravity_remnant_well_loop', level=-18)
def remnant_well(d, seed):
    vortex = bandpass(pink(d, seed), 250 + 150 * lfo(1, d), 3) * (0.6 + 0.4 * lfo(1, d))
    return vortex + sine(32, d) * 0.8 + fm(64, 0.5, 2, d) * 0.2


@recipe('boss/gravity_remnant_death_collapse', level=BIG)
def remnant_collapse(d, seed):
    rumble = lowpass(brown(3.5, seed), glide(800, 80, 3.5)) * env([(0, 0), (0.2, 1), (3.5, 0)], 3.5)
    return reverb(mix(rumble, *[(debris_impact(0, seed + i) * 0.4, 0.5 * i) for i in range(5)]), 0.8, 0.3)


@recipe('boss/gravity_remnant_death_implode', level=BIG)
def remnant_implode(d, seed):
    suck = whoosh(1.2, 6000, 100, 2, seed, 0.4)[::-1] * 0.8
    return mix(suck, (sub_drop(1.5, 160, 20) * 1.4, 1.1), (boom(1.2, 25, seed, 0.2) * 0.6, 1.15))


# =================================================================== ボス: ホライズン・ウォーデン（巨大な宇宙の番兵）

@recipe('boss/horizon_warden_emerge', level=BIG)
def warden_emerge(d, seed):
    dark = voices(4.0, (55, 82.5, 110), 'o', seed, 0.1) * 0.05 * env([(0, 0), (3.5, 1), (4, 0.5)], 4.0)
    return reverb(mix(riser(4.0, 25, 300, seed, 0.3) * 0.7, dark, (boom(2.0, 28, seed, 0.4), 3.8)), 0.95, 0.3, 2000, 1.5)


@recipe('boss/horizon_warden_phase', level=BIG)
def warden_phase(d, seed):
    shards = mix(*[(clank(400 + 200 * i, 0.6, seed + i) * 0.3, 0.05 * i) for i in range(8)])
    return reverb(mix(boom(2.0, 35, seed, 0.6), shards), 0.85, 0.3)


@recipe('boss/horizon_warden_beam_charge', level=BOSS)
def warden_beam_charge(d, seed):
    return reverb(riser(2.2, 60, 1500, seed, 0.5) + whine(glide(300, 1800, 2.2), 2.2, 0.01, 9) * env([(0, 0), (2.2, 0.3)], 2.2), 0.7, 0.25)


@recipe('boss/horizon_warden_beam_fire', level=BIG)
def warden_beam_fire(d, seed):
    blast = boom(2.5, 40, seed, 0.8)
    beam = bandpass(white(2.5, seed), 1800, 1) * env([(0, 0), (0.03, 1), (2, 0.6), (2.5, 0)], 2.5)
    return reverb(mix(blast, beam * 0.8, sub_drop(2.0, 90, 25) * 0.8), 0.9, 0.3)


@machine('boss/horizon_warden_laser_loop', level=-16)
def warden_laser(d, seed):
    sizzle = bandpass(white(d, seed), 2200, 1.2) * (0.8 + 0.2 * lfo(9, d))
    return sizzle * 0.6 + saw(150 + 8 * lfo(13, d), n=int(d * SR)) * 0.2 + crackle(d, 50, seed, 0.004) * 1.5


@recipe('boss/horizon_warden_sweep', level=BOSS)
def warden_sweep(d, seed):
    return mix(whoosh(0.9, 150, 3000, 1.5, seed, 0.7) * 1.1, fm(60, 0.5, 3, 0.9) * perc(0.9, 0.05, 0.4) * 0.4)


@recipe('boss/horizon_warden_grip', level=BOSS)
def warden_grip(d, seed):
    crunch = lowpass(crackle(1.2, 40, seed, 0.01), 1500) * 2
    return reverb(mix(fm(45, 0.5, 5, 1.2) * env([(0, 0), (0.2, 1), (1.2, 0)], 1.2) * 0.8, crunch * 0.5), 0.6, 0.25)


@recipe('boss/horizon_warden_shockwave', level=BIG)
def warden_shockwave(d, seed):
    return reverb(mix(boom(2.0, 38, seed, 0.6), whoosh(1.5, 3000, 200, 1.5, seed, 0.5) * 0.6), 0.9, 0.3)


@recipe('boss/horizon_warden_death', level=BIG)
def warden_death(d, seed):
    fall = voices(5.0, (110, 82.5, 55), 'u', seed, 0.1) * 0.04 * env([(0, 1), (5, 0)], 5.0)
    return reverb(mix(boom(3.0, 30, seed, 0.6), fall, (boom(3.0, 24, seed + 1, 0.3) * 0.8, 1.6), (sub_drop(3.0, 70, 18), 2.0)), 0.95, 0.35, 1800, 2.0)


@recipe('boss/horizon_warden_ambient', level=BOSS - 3, variants=3)
def warden_ambient(d, seed):
    f = 50 + 15 * rng(seed).random()
    return lowpass(saw(glide(f, f * 0.8, 3.0)) + 0.5 * saw(glide(f * 1.5, f * 1.2, 3.0)), 400) * env([(0, 0), (0.8, 1), (3, 0)], 3.0)


@recipe('boss/guardian_core_awaken', level=BIG)
def core_awaken(d, seed):
    spin = lowpass(saw(glide(20, 140, 4.5, 1.5)), glide(200, 2000, 4.5)) * env([(0, 0), (4.5, 1)], 4.5) * 0.6
    rings = pulses(4.5, 0.5, lambda s: clank(200 + s * 60, 0.4, int(s * 10)) * 0.2 * (s / 4.5))
    return reverb(mix(spin, rings, (impact(1.0, 50, seed, 0.5), 4.4)), 0.9, 0.3)


@recipe('boss/vault_unseal')
def vault_unseal(d, seed):
    return reverb(mix(buzz(110, 1.5, 2500) * env([(0, 1), (1.5, 0)], 1.5) * 0.4, shimmer(1.8, 10, 1200, 2, 0.6, seed) * 0.4,
                      (chime(660, 1.5, seed) * 0.4, 1.0)), 0.8, 0.35)


# =================================================================== ボス: エルゴ球の大蛇（鯨の歌のような声・渦・ジェット）

def _song(dur, f0, f1, seed, vowel='u'):
    """鯨の歌のような、長くすべる声。"""
    f = glide(f0, f1, dur, 0.8) * (1 + 0.01 * lfo(5, dur))
    src = saw(f) * 0.5 + sine(f) * 0.5
    fmt = {'u': (350, 700), 'o': (500, 850), 'a': (750, 1150)}[vowel]
    return (bandpass(src, fmt[0], 3) + bandpass(src, fmt[1], 4) * 0.5) * env([(0, 0), (dur * 0.2, 1), (dur * 0.8, 0.9), (dur, 0)], dur)


@recipe('boss/ergosphere_leviathan_emerge', level=BIG)
def lev_emerge(d, seed):
    vortex = whoosh(3.0, 100, 1500, 2, seed, 1.2) * 0.8
    return reverb(mix(vortex, (roar(2.5, 70, seed) * 0.9, 1.6), (sub_drop(2.0, 90, 30) * 0.7, 1.6)), 0.95, 0.35, 2000, 1.5)


@recipe('boss/ergosphere_leviathan_ambient', level=BOSS - 3, variants=3)
def lev_ambient(d, seed):
    r = rng(seed)
    a = _song(2.4, r.uniform(150, 220), r.uniform(90, 140), seed, 'u')
    return reverb(a, 0.95, 0.4, 2000, 1.2)


@recipe('boss/ergosphere_leviathan_hurt', level=BOSS)
def lev_hurt(d, seed):
    screech = bandpass(saw(glide(700, 400, 0.6)) + white(0.6, seed) * 0.5, 1500, 2) * perc(0.6, 0.01, 0.3)
    return mix(screech * 0.8, clank(500, 0.4, seed) * 0.3)


@recipe('boss/ergosphere_leviathan_roar', level=BIG)
def lev_roar(d, seed):
    return reverb(roar(2.6, 80, seed, 0.7) + roar(2.6, 120, seed + 1, 0.4, (650, 1100, 2600)) * 0.5, 0.9, 0.3, 2500, 1.2)


@recipe('boss/ergosphere_leviathan_dive', level=BOSS)
def lev_dive(d, seed):
    return mix(whoosh(1.2, 300, 3000, 1.5, seed, 0.5), roar(1.0, 110, seed, 0.5) * 0.4)


@recipe('boss/ergosphere_leviathan_breath_charge', level=BOSS)
def lev_breath_charge(d, seed):
    inhale = whoosh(1.8, 3000, 300, 2, seed, 1.5) * 0.8
    return reverb(inhale + riser(1.8, 50, 400, seed, 0.2) * 0.5, 0.7, 0.25)


@recipe('boss/ergosphere_leviathan_breath_fire', level=BIG)
def lev_breath_fire(d, seed):
    beam = bandpass(white(2.2, seed), 1200, 1) * env([(0, 0), (0.05, 1), (1.8, 0.7), (2.2, 0)], 2.2)
    return reverb(mix(beam, roar(2.2, 75, seed, 0.6) * 0.7, boom(1.2, 45, seed, 0.3) * 0.5), 0.85, 0.3)


@recipe('boss/ergosphere_leviathan_shed', level=BOSS)
def lev_shed(d, seed):
    return mix(*[(clank(800 + 300 * i, 0.3, seed + i) * 0.35, 0.06 * i) for i in range(6)], whoosh(0.5, 2000, 800, 2, seed) * 0.3)


@recipe('boss/ergosphere_leviathan_death', level=BIG)
def lev_death(d, seed):
    sinking = _song(4.5, 160, 40, seed, 'o') * 1.2
    return reverb(mix(sinking, roar(2.0, 70, seed, 0.5) * 0.6, (sub_drop(3.0, 60, 18), 1.5)), 0.95, 0.4, 1800, 2.0)


@recipe('boss/ergosphere_leviathan_implode', level=BIG)
def lev_implode(d, seed):
    return mix(whoosh(1.0, 5000, 100, 2, seed, 0.4)[::-1], (boom(1.8, 26, seed, 0.4) * 1.1, 0.9))


@recipe('boss/ergosphere_leviathan_hatchling', variants=2)
def lev_hatchling(d, seed):
    r = rng(seed)
    chirps = mix(*[(_song(0.18, r.uniform(700, 1000), r.uniform(1100, 1500), seed + i, 'a') * 0.6, 0.15 * i) for i in range(3)])
    return chirps


@recipe('boss/ergosphere_leviathan_rumble', level=BOSS)
def lev_rumble(d, seed):
    return lowpass(brown(2.5, seed), 180) * env([(0, 0), (0.5, 1), (2.5, 0)], 2.5) + throb(2.5, 3, 35, 0.6) * 0.5


@recipe('boss/ergosphere_leviathan_swallow', level=BOSS)
def lev_swallow(d, seed):
    gulp = sine(glide(200, 60, 0.4)) * perc(0.4, 0.01, 0.2)
    return mix(clank(160, 0.4, seed) * 0.7, thump(70, 0.3, 0.6, seed), (gulp * 0.6, 0.2))


@recipe('boss/ergosphere_leviathan_whip', level=BOSS)
def lev_whip(d, seed):
    return mix(whoosh(0.5, 400, 5000, 2.5, seed, 1.8) * 0.9, (highpass(white(0.08, seed), 2500) * perc(0.08, 0.001, 0.02), 0.45))


@recipe('boss/ergosphere_leviathan_molt', level=BOSS)
def lev_molt(d, seed):
    return mix(impact(0.4, 90, seed) * 0.6, *[(clank(600 + 250 * i, 0.4, seed + i) * 0.3, 0.03 * i) for i in range(10)])


@recipe('boss/ergosphere_leviathan_coil')
def lev_coil(d, seed):
    slither = bandpass(pink(1.8, seed), 1800, 2) * (0.5 + 0.5 * lfo(6, 1.8)) * env([(0, 0), (0.3, 1), (1.8, 0)], 1.8)
    return slither * 0.7 + lowpass(brown(1.8, seed), 150) * 0.4


@recipe('boss/ergosphere_leviathan_ascend', level=BIG)
def lev_ascend(d, seed):
    return reverb(mix(whoosh(2.0, 150, 4000, 1.5, seed, 1.5), roar(2.0, 90, seed, 0.5, contour=glide(0.8, 1.6, 2.0)) * 0.6), 0.85, 0.3)


@recipe('boss/ergosphere_leviathan_jet_charge', level=BOSS)
def lev_jet_charge(d, seed):
    return reverb(riser(2.0, 80, 2000, seed, 0.3) * 0.7 + shimmer(2.0, 12, 1500, 3, 0.4, seed) * env([(0, 0), (2, 1)], 2.0) * 0.4, 0.7, 0.3)


@recipe('boss/ergosphere_leviathan_jet', level=BIG)
def lev_jet(d, seed):
    jet = bandpass(white(2.5, seed), glide(800, 2500, 2.5), 1) * env([(0, 0), (0.05, 1), (2.2, 0.8), (2.5, 0)], 2.5)
    return reverb(mix(jet, sub_drop(2.0, 100, 35) * 0.6, whine(glide(1200, 600, 2.5), 2.5) * 0.1), 0.8, 0.3)


@recipe('boss/ergosphere_leviathan_storm', level=BIG)
def lev_storm(d, seed):
    r = rng(seed)
    falls = mix(*[(mix(whoosh(0.5, 3000, 300, 2, seed + i, 0.5) * 0.5, (impact(0.6, 50, seed + i) * 0.6, 0.45)), r.uniform(0, 2.5)) for i in range(6)])
    return reverb(falls, 0.9, 0.3)


# =================================================================== ボス: 最後の記録者（聖歌・光輪・鐘）

@recipe('boss/last_archivist_emerge', level=BIG)
def arch_emerge(d, seed):
    choir = voices(5.0, (220, 277, 330, 440), 'a', seed) * 0.03 * env([(0, 0), (3.5, 1), (5, 0.6)], 5.0)
    bells = mix(*[(chime(660 * 1.5 ** (i % 3), 2.0, seed + i) * 0.25, 0.5 + 0.6 * i) for i in range(6)])
    return reverb(mix(choir, bells), 0.95, 0.4, 3000, 2.0)


@recipe('boss/last_archivist_ambient', level=BOSS - 4, variants=3)
def arch_ambient(d, seed):
    r = rng(seed)
    root = r.choice([196, 220, 247])
    return reverb(voices(2.8, (root, root * 1.5), 'u', seed, 0.25) * 0.05 * env([(0, 0), (1, 1), (2.8, 0)], 2.8), 0.95, 0.5, 2500, 1.0)


@recipe('boss/last_archivist_hurt', level=BOSS)
def arch_hurt(d, seed):
    wobble = chime(880, 0.8, seed) * (1 + 0.5 * lfo(12, 0.8))
    gasp = voices(0.6, (440, 466), 'e', seed) * 0.03 * perc(0.6, 0.02, 0.3, 2)
    return rich(mix(wobble * 0.5, chime(932, 0.8, seed + 1) * 0.4, gasp, glass(0.5, seed, 8) * 0.3), seed, 0.1, 0.4)


@recipe('boss/last_archivist_phase', level=BIG)
def arch_phase(d, seed):
    chord1 = voices(1.2, (220, 262, 330), 'a', seed) * 0.04
    chord2 = voices(2.0, (233, 294, 349, 466), 'a', seed + 1) * 0.05
    return reverb(mix(chord1, (ring_burst(1.0, 400, seed), 1.0), (chord2, 1.05)), 0.95, 0.4, 3000, 1.5)


@recipe('boss/last_archivist_judgment_aim', level=BOSS)
def arch_judgment_aim(d, seed):
    e = env([(0, 0), (1.5, 0.6)], 1.5)
    choir = voices(1.5, (440, 554, 659), 'a', seed) * 0.03 * e
    return rich(mix(sine(glide(440, 1760, 1.5)) * e * 0.6, chime(1760, 1.5, seed) * e, choir, shimmer(1.5, 12, 1500, 2, 0.5, seed) * e * 0.3), seed, 0.1, 0.5)


@recipe('boss/last_archivist_judgment_fire', level=BIG)
def arch_judgment_fire(d, seed):
    hit = voices(1.5, (330, 415, 494, 659), 'a', seed) * 0.05 * perc(1.5, 0.01, 0.8, 2)
    return reverb(mix(zap(0.6, 6000, 800, seed) * 0.6, hit, whoosh(1.0, 6000, 800, 2, seed, 0.5) * 0.5), 0.9, 0.35)


@recipe('boss/last_archivist_verdict', level=BOSS)
def arch_verdict(d, seed):
    circle = mix(*[(chime(523 * 2 ** (k / 12), 1.5, seed + i) * 0.25, 0.1 * i) for i, k in enumerate((0, 7, 12, 19, 24, 19, 12, 7))])
    return reverb(circle, 0.85, 0.4)


@recipe('boss/last_archivist_collapse', level=BOSS)
def arch_collapse(d, seed):
    return mix(sine(glide(1500, 200, 0.7)) * perc(0.7, 0.01, 0.6, 2) * 0.4, (sub_drop(0.8, 140, 30) * 0.9, 0.6), (impact(0.5, 50, seed) * 0.5, 0.65))


@recipe('boss/last_archivist_recall', level=BOSS)
def arch_recall(d, seed):
    r = rng(seed)
    syllables = mix(*[(bandpass(saw(r.uniform(180, 240), 0.14) + pink(0.14, seed + i) * 0.3,
                                  r.choice([700, 1100, 1800, 2400]), 4) * perc(0.14, 0.01, 0.1, 2) * 0.6, 0.13 * i) for i in range(16)])
    return reverb(syllables, 0.9, 0.45, 3000, 1.0)


@recipe('boss/last_archivist_nova_charge', level=BOSS)
def arch_nova_charge(d, seed):
    return reverb(mix(whoosh(2.0, 6000, 400, 2, seed, 1.5) * 0.7, voices(2.0, (220, 330, 440), 'a', seed) * 0.03 * env([(0, 0), (2, 1)], 2.0)), 0.8, 0.3)


@recipe('boss/last_archivist_nova', level=BIG)
def arch_nova(d, seed):
    choir = voices(2.5, (262, 330, 392, 523), 'a', seed) * 0.05 * perc(2.5, 0.01, 1.5, 2)
    return reverb(mix(boom(2.0, 45, seed, 0.8), ring_burst(1.5, 500, seed) * 0.8, choir), 0.95, 0.35, 3500, 1.5)


@recipe('boss/last_archivist_death', level=BIG)
def arch_death(d, seed):
    falling = voices(4.0, (440, 330, 262), 'u', seed) * 0.04 * env([(0, 1), (4, 0)], 4.0)
    return reverb(mix(glass(1.5, seed, 50), falling), 0.95, 0.4, 3000, 2.0)


@recipe('boss/last_archivist_death_flash', level=BIG)
def arch_death_flash(d, seed):
    e = env([(0, 0), (0.05, 1), (3, 0)], 3.0)
    light = voices(3.0, (440, 659, 880, 1319), 'a', seed) * 0.04 * e
    return rich(mix(sine(1760, 3.0) * e * 0.3, chime(880, 3.0, seed) * 0.6, light, whoosh(3.0, 6000, 800, 1.5, seed, 0.4) * 0.3,
                    shimmer(3.0, 15, 2000, 2, 1.0, seed) * e * 0.3), seed, 0.1, 0.5)


# =================================================================== 創世炉（宇宙を生む炉）

@recipe('genesis/form', level=BOSS)
def genesis_form(d, seed):
    clunks = mix(*[(clank(160 + 50 * i, 0.5, seed + i) * 0.4, 0.35 * i) for i in range(5)])
    chord = voices(2.5, (131, 196, 262), 'o', seed) * 0.05 * env([(0, 0), (2.5, 1)], 2.5)
    return reverb(mix(clunks, (chord, 1.2), (chime(523, 2.0, seed) * 0.4, 3.0)), 0.9, 0.35, 2500, 1.5)


@recipe('genesis/ambient', level=-22, variants=3)
def genesis_ambient(d, seed):
    r = rng(seed)
    f = r.choice([55, 65.4, 73.4])
    pad = voices(4.0, (f * 2, f * 3), 'o', seed, 0.1) * 0.04 * env([(0, 0), (1.5, 1), (4, 0)], 4.0)
    return reverb(throb(4.0, 0.5, f, 0.6) * 0.8 + pad + shimmer(4.0, 1, 800, 3, 1.5, seed) * 0.15, 0.95, 0.4, 2000, 1.0)


@recipe('genesis/seed', level=BOSS)
def genesis_seed(d, seed):
    return reverb(mix(chime(330, 2.0, seed) * 0.5, sine(glide(600, 60, 1.5)) * env([(0, 0), (0.1, 0.6), (1.5, 0)], 1.5)), 0.85, 0.4)


@recipe('genesis/ignite', level=BOSS)
def genesis_ignite(d, seed):
    fire = lowpass(brown(2.5, seed), glide(300, 3000, 2.5, 0.5)) * env([(0, 0), (0.2, 1), (2.5, 0.4)], 2.5)
    return reverb(mix(zap(0.3, 4000, 600, seed) * 0.6, (fire, 0.1), (whoosh(1.0, 300, 4000, 1.5, seed, 0.6) * 0.5, 0.1)), 0.8, 0.3)


@recipe('genesis/surge', level=BOSS)
def genesis_surge(d, seed):
    return reverb(riser(2.5, 40, 300, seed, 0.4) * env([(0, 0), (2, 1), (2.5, 0)], 2.5) + throb(2.5, 2, 45, 0.5) * 0.6, 0.85, 0.3)


@recipe('genesis/alarm', level=BOSS)
def genesis_alarm(d, seed):
    return reverb(alarm(2.0, 740, 554, 2) * env([(0, 0), (0.05, 1), (1.9, 1), (2, 0)], 2.0) * 0.6 + throb(2.0, 4, 50, 0.8) * 0.4, 0.6, 0.25)


@recipe('genesis/collapse', level=BIG)
def genesis_collapse(d, seed):
    crumble = lowpass(brown(3.0, seed), glide(2000, 100, 3.0)) * env([(0, 0), (0.3, 1), (3, 0)], 3.0)
    return reverb(mix(crumble, sub_drop(3.0, 80, 22) * 0.8, crackle(3.0, 20, seed, 0.01) * 0.8), 0.9, 0.3)


@recipe('genesis/lever')
def genesis_lever(d, seed):
    surge = pad(0.8, (196, 294), 1200, 0.3, seed) * env([(0, 0), (0.1, 1), (0.8, 0)], 0.8) * 0.4
    return rich(mix(clank(350, 0.3, seed) * 0.6, (thump(90, 0.2, 0.4, seed) * 0.6, 0.05), (beep(660, 0.1) * 0.2, 0.12), (surge, 0.1)), seed, 0.08, 0.35)


@recipe('genesis/birth_collapse', level=BIG)
def genesis_birth_collapse(d, seed):
    fall = whoosh(2.5, 6000, 80, 2, seed, 0.3)[::-1] * 0.9
    return mix(fall, voices(2.5, (523, 392, 262), 'a', seed) * 0.03 * env([(0, 0), (2.4, 1), (2.5, 0)], 2.5), (sub_drop(1.0, 200, 30), 2.3))


@recipe('genesis/big_bang', level=BIG)
def genesis_big_bang(d, seed):
    blast = boom(5.0, 30, seed, 1.0) * 1.2
    light = voices(5.0, (262, 330, 392, 523, 659), 'a', seed) * 0.05 * env([(0, 0), (0.1, 1), (5, 0)], 5.0)
    sparkle = shimmer(5.0, 25, 1500, 4, 1.0, seed) * env([(0, 1), (5, 0)], 5.0) * 0.4
    return reverb(mix(blast, light, sparkle, whoosh(3.0, 200, 8000, 1.2, seed, 0.3) * 0.6), 0.95, 0.35, 4000, 2.0)


@recipe('genesis/sky', level=BOSS)
def genesis_sky(d, seed):
    pad = voices(5.0, (262, 392, 523, 784), 'o', seed, 0.1) * 0.04 * env([(0, 0), (2, 1), (5, 0)], 5.0)
    return reverb(mix(pad, shimmer(5.0, 8, 1500, 3, 1.5, seed) * 0.35, whoosh(5.0, 200, 3000, 1.2, seed, 1.0) * 0.2), 0.95, 0.4, 3500, 1.5)


@recipe('genesis/synth', variants=3)
def genesis_synth(d, seed):
    r = rng(seed)
    f = r.choice([440, 523, 587, 659])
    return reverb(mix(whoosh(0.5, 3000, 600, 2, seed, 0.6)[::-1] * 0.4, (chime(f, 1.2, seed) * 0.5, 0.45), (chime(f * 1.5, 1.0, seed + 1) * 0.3, 0.5)), 0.7, 0.35)


# =================================================================== アンドロイド（バニラの音の代わり。かわいく、機械らしく）

@recipe('android/power_down')
def android_power_down(d, seed):
    sigh = (sine(glide(1200, 180, 0.7, 0.7)) + 0.3 * sine(glide(1800, 270, 0.7, 0.7))) * env([(0, 0), (0.03, 0.5), (0.7, 0)], 0.7)
    whir = servo(0.7, 400, 120, seed) * env([(0, 0.5), (0.7, 0)], 0.7) * 0.3
    return rich(mix(sigh, whir, lowpass(saw(glide(300, 60, 0.7)), 800) * env([(0, 0.3), (0.7, 0)], 0.7), (thump(100, 0.15, 0.3, seed) * 0.4, 0.66)), seed, 0.08, 0.25)


@recipe('android/charge_start')
def android_charge_start(d, seed):
    rise = riser(0.6, 200, 800, seed, 0.2) * 0.3
    return rich(mix(beep(880, 0.1) * 0.5, (beep(1175, 0.14) * 0.5, 0.1), (hum(120, 0.6, seed=seed) * env([(0, 0), (0.2, 0.3), (0.6, 0)], 0.6), 0.15),
                    (rise, 0.1), (shimmer(0.6, 10, 2000, 2, 0.3, seed) * 0.2, 0.25)), seed, 0.08, 0.25)


@recipe('android/charged')
def android_charged(d, seed):
    bells = mix((chime(1047, 0.6, 1) * 0.4, 0), (chime(1319, 0.6, 2) * 0.4, 0.09), (chime(1568, 0.9, 3) * 0.5, 0.18))
    chord = pad(1.0, (523, 659, 784), 2500, 0.3, seed) * env([(0, 0), (0.2, 1), (1.0, 0)], 1.0) * 0.35
    return rich(mix(bells, (chord, 0.15), (shimmer(0.8, 15, 2500, 2, 0.4, seed) * 0.25, 0.2)), seed, 0.08, 0.3)


@recipe('android/gunner_shot')
def android_gunner_shot(d, seed):
    pew = (sine(glide(2600, 500, 0.12, 0.6)) + 0.4 * square(glide(1300, 250, 0.12, 0.6))) * perc(0.12, 0.001, 0.06) * 0.5
    tail = highpass(white(0.25, seed), 2500) * perc(0.25, 0.002, 0.1) * 0.25
    return rich(mix(pew, zap(0.1, 5000, 1500, seed) * 0.3, thump(160, 0.08, 0.3, seed) * 0.4, (tick(3500, 0.02, seed) * 0.4, 0.0), tail), seed, 0.05, 0.25, 2)


def _metal_hit(seed, base=140, weight=1.0, length=0.7):
    """重い金属の塊どうしがぶつかる「ガン」: 鋭い衝撃で、厚い鉄の低い固有振動をいくつも鳴らし、床に伝わる低い音を足す。"""
    r = rng(seed)
    t = t_axis(length)
    strike = white(length, seed) * np.exp(-t / 0.002)                              # ぶつかった瞬間の衝撃
    modes = sum(resonator(strike, base * k, length * dk) * g
                for k, dk, g in ((1, 0.55, 1.0), (1.59, 0.4, 0.8), (2.14, 0.3, 0.6), (2.95, 0.22, 0.45), (4.3, 0.12, 0.3), (6.1, 0.07, 0.2)))
    return mix(modes * 6 * weight, thump(55 + 10 * r.random(), 0.3, 0.6, seed) * 0.9 * weight, lowpass(strike, 4000) * 0.4 * weight)


def _gachakon(seed, weight=1.0):
    """重い金属がぶつかってはまる「ガ・ガン」: 一度軽く当たり、すぐに重くぶつかって止まる。"""
    r = rng(seed)
    return mix(_metal_hit(seed, 210 + 30 * r.random(), 0.45 * weight, 0.35), (_metal_hit(seed + 1, 130 + 20 * r.random(), weight), 0.05))


@recipe('android/railgun_assemble', level=-19)
def android_railgun_assemble(d, seed):
    # 重い部品が次々とぶつかってはまる: 間隔はばらばら（0.07〜0.16 秒）で、前の音が鳴りやまないうちに次が重なる。
    # 組み上がりの終わり（0.75 秒ほど）で、ひときわ重く固まる
    r = rng(seed)
    release = mix(clank(380, 0.3, seed) * 0.3, lowpass(white(0.15, seed), 2500) * perc(0.15, 0.003, 0.05) * 0.2)
    times, s_ = [], 0.04
    while s_ < 0.68:
        times.append(s_)
        s_ += r.uniform(0.07, 0.16)
    hits = mix(*[(_gachakon(seed + 10 * i, r.uniform(0.55, 0.95)) if r.random() < 0.75 else _metal_hit(seed + 10 * i, r.uniform(200, 260), 0.4, 0.3), st)
                 for i, st in enumerate(times)])
    scrape = bandpass(white(0.7, seed), 600 + 200 * lfo(5, 0.7), 3) * env([(0, 0), (0.1, 0.15), (0.6, 0.1), (0.7, 0)], 0.7)
    final = _gachakon(seed + 99, 1.2)
    power = sine(glide(150, 400, 0.35)) * env([(0, 0), (0.1, 0.2), (0.35, 0)], 0.35)
    return reverb(mix(release, (scrape, 0.03), hits, (final, 0.74), (power, 0.84)), 0.6, 0.25, 3000, 0.6)


@recipe('android/railgun_charge', level=BOSS)
def android_railgun_charge(d, seed):
    # 1.3 秒（ためる 26 tick）に合わせる: コイルが 0.3・0.6・0.95 秒で灯り、0.85 秒で狙いが固まり、最後の一瞬で息をのむ
    D = 1.3
    t = t_axis(D)
    grow = np.clip(t / 1.2, 0, 1) ** 1.5
    sub = sine(glide(35, 70, D, 1.5)) * grow * 0.8
    steps = np.select([t < 0.3, t < 0.6, t < 0.95], [1.0, 1.26, 1.5], 2.0)
    coil = chorus(sine(700 * steps * (1 + 0.15 * t)) * 0.2 + sine(1400 * steps * (1 + 0.15 * t)) * 0.08, 2, 0.002, 6, seed) * grow
    chord = pad(D, (110, 165, 220, 330), 300, 0.0, seed)
    chord = lowpass(chord, 300 + 4000 * grow) * grow * 0.5
    bells = mix(*[(chime(880 * 1.26 ** i, 0.6, seed + i) * 0.4, st) for i, st in enumerate((0.3, 0.6, 0.95))])
    lock = beep(1760, 0.12, 'square') * 0.25 + beep(2637, 0.12) * 0.2
    # 周りのエネルギーが吸い寄せられる: 響きを逆さにした寄せ波、時間差で流れ込む風、密に高くなる光の粒、低い吸い込み
    bloom = reverb(shimmer(0.4, 30, 1200, 3, 0.4, seed), 0.95, 0.9, 5000, 1.0)[::-1]
    gather = fit(bloom[-int(D * SR):], D) * np.clip(t / D, 0, 1) * 0.7      # 寄せ波の山（逆さの響きの終わり）を、ためる終わりに合わせる
    streams = mix(*[(whoosh(0.9 - 0.15 * k, 6000 - 800 * k, 300 + 150 * k, 2.5, seed + k, 0.6)[::-1] * (0.35 + 0.1 * k), 0.15 + 0.12 * k)
                    for k in range(4)])
    grains = shimmer(D, 40, 1500, 3, 0.15, seed + 9) * grow ** 2 * 0.35       # 吸い込まれていく光（なめらかなきらめき）
    suck = lowpass(brown(D, seed + 11), glide(80, 1500, D, 1.8)) * grow * 0.6
    body = mix(sub, coil, chord * 0.8, bells, gather, streams, grains, suck, (lock, 0.85))
    body *= np.concatenate([np.ones(int(1.18 * SR)), np.linspace(1, 0.3, len(body) - int(1.18 * SR))])   # 撃つ直前に、ぐっと息をのむ
    inhale = whoosh(0.35, 5000, 400, 2, seed, 0.5)[::-1] * 0.5
    return reverb(mix(body, (inhale, 0.95)), 0.7, 0.25, 4000, 0.4)


@recipe('android/railgun_fire', level=BIG)
def android_railgun_fire(d, seed):
    crack = mix(*[(zap(0.18, 9000 - 1500 * i, 500, seed + i) * (0.9 - 0.15 * i), 0.004 * i) for i in range(4)],
                highpass(white(0.12, seed), 2500) * perc(0.12, 0.0005, 0.02) * 1.2)
    shock = boom(2.2, 45, seed, 1.0) * 1.1
    sub = sub_drop(1.8, 110, 22) * 1.0
    rails = mix(clank(330, 1.4, seed) * 0.35, clank(495, 1.2, seed + 1) * 0.25)
    thunder = lowpass(brown(3.5, seed + 2), glide(1800, 120, 3.5, 0.6)) * env([(0, 0), (0.08, 1), (0.8, 0.6), (3.5, 0)], 3.5) * 0.8
    sweep = whoosh(1.0, 8000, 300, 1.5, seed, 0.4) * 0.6
    return reverb(mix(crack, shock, sub, rails, sweep, (thunder, 0.05)), 0.95, 0.3, 3000, 1.5)


@recipe('android/ring_inflate')
def android_ring_inflate(d, seed):
    pop = sine(glide(400, 900, 0.06)) * perc(0.06, 0.001, 0.03) * 0.5
    rush = highpass(pink(0.45, seed), 1200) * env([(0, 0), (0.02, 1), (0.45, 0)], 0.45) * 0.5
    squeak = sine(glide(900, 1400, 0.12)) * perc(0.12, 0.01, 0.08) * 0.15
    return mix(pop, (rush, 0.02), (squeak, 0.35))


# =================================================================== 画面

@recipe('ui/click', level=-20)
def ui_click(d, seed):
    # 軽いスイッチ: カチッという接点、小さな筐体の響き、澄んだ短い電子音
    snap = tick(4200, 0.03, seed) * 1.0
    body = resonator(white(0.002, seed) * 1.0, 1150, 0.035) * 3
    blip = sine(glide(1900, 1500, 0.06)) * perc(0.06, 0.001, 0.02) * 0.25
    return mix(snap, body, (blip, 0.004))
