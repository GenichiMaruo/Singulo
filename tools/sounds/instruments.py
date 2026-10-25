# -*- coding: utf-8 -*-
"""音色の部品（dsp の道具を組み合わせた「楽器」）。recipes.py の各音は、これらを重ねて作る。"""
import numpy as np

from dsp import (SR, t_axis, rng, sine, saw, square, tri, fm, partials, white, pink, brown, crackle, bubbles,
                 lowpass, highpass, bandpass, resonator, env, perc, glide, lfo, reverb, echo, drive, crush,
                 mix, fit, fade)


# ---------------------------------------------------------------- 機械の土台

def hum(f, dur, harm=(1, 2, 3), amps=(1, 0.5, 0.25), wobble=0.004, seed=0):
    """電源のうなり（倍音を少し揺らす）。"""
    t = t_axis(dur)
    r = rng(seed)
    out = np.zeros_like(t)
    for k, a in zip(harm, amps):
        drift = 1 + wobble * np.sin(2 * np.pi * r.uniform(0.05, 0.3) * t + r.uniform(0, 6))
        out += a * sine(f * k * drift)
    return out / sum(amps)


def buzz(f, dur, cutoff=900, seed=0):
    """変圧器のジーという電気の音（のこぎり波を丸める）。"""
    return lowpass(saw(f * (1 + 0.002 * lfo(0.13, dur)), n=int(dur * SR)) + 0.3 * saw(f * 2.003, dur), cutoff)


def air(dur, lo=200, hi=4000, kind='pink', seed=0):
    """風・排気・空調の、帯域をしぼった雑音。"""
    n = {'pink': pink, 'white': white, 'brown': brown}[kind](dur, seed)
    return bandpass(n, (lo * hi) ** 0.5, q=(lo * hi) ** 0.5 / max(1, hi - lo))


def pulses(dur, period, make, offset=0.0):
    """period 秒ごとに make() の音を置く（ループの長さが period で割り切れるとき、継ぎ目でもずれない）。"""
    out = np.zeros(int(dur * SR))
    s = offset
    while s < dur:
        p = make(s)
        i = int(s * SR)
        out[i:i + len(p)] += p[:len(out) - i]
        s += period
    return out


def thump(depth=55, length=0.18, click=0.3, seed=0):
    """低いドスン（ピストン・プレス）。"""
    t = t_axis(length)
    body = np.sin(2 * np.pi * np.cumsum(depth * (1 + 1.5 * np.exp(-t / 0.02))) / SR) * np.exp(-t / (length / 4))
    return body + click * highpass(white(length, seed), 2000) * np.exp(-t / 0.006)


def clank(f=600, length=0.25, seed=0):
    """金属がぶつかるカン（共鳴する打撃）。"""
    hit = white(length, seed) * np.exp(-t_axis(length) / 0.003)
    return sum(resonator(hit, f * k, length * d) for k, d in ((1, 0.5), (2.71, 0.3), (5.2, 0.15))) * 8


def tick(f=3000, length=0.02, seed=0):
    return bandpass(white(length, seed), f, 3) * np.exp(-t_axis(length) / 0.004)


def shimmer(dur, rate, base=1200, spread=3.0, decay=0.6, seed=0):
    """ときどき鳴る、澄んだ小さな鐘（量子・結晶の装置のきらめき）。"""
    r = rng(seed)
    out = np.zeros(int(dur * SR))
    for s in r.uniform(0, dur, int(rate * dur)):
        f = base * spread ** r.uniform(0, 1)
        L = decay * r.uniform(0.6, 1.4)
        b = partials(f, (1, 2.76, 5.4), (L, L / 2.5, L / 5), L, seed=int(s * 1000)) * r.uniform(0.2, 1)
        i = int(s * SR)
        out[i:i + len(b)] += b[:len(out) - i]
    return out


def throb(dur, rate, f=40, depth=1.0):
    """ゆっくり脈打つ低いうなり（重力・特異点）。"""
    return sine(f, dur) * (1 - depth * 0.5 * (1 + np.cos(2 * np.pi * rate * t_axis(dur))))


def whine(f, dur, vib=0.004, rate=5.0):
    """高い回転音（タービン・コイル）。"""
    return sine(f * (1 + vib * lfo(rate, dur))) + 0.3 * sine(f * 2 * (1 + vib * lfo(rate, dur)))


# ---------------------------------------------------------------- 一回きりの音

def whoosh(dur, f0=300, f1=3000, q=1.5, seed=0, curve=1.0):
    """風を切る・吸い込む（帯域をすべらせた雑音）。"""
    n = pink(dur, seed) * 0.6 + white(dur, seed + 1) * 0.4
    return bandpass(n, glide(f0, f1, dur, curve), q) * np.sin(np.pi * np.clip(t_axis(dur) / dur, 0, 1)) ** 0.7


def zap(dur, f0=2000, f1=200, seed=0):
    """電気がはじける（下がる鋭い音と、ざらつき）。"""
    f = glide(f0, f1, dur, 0.6)
    tone = square(f) * 0.4 + saw(f * 1.5) * 0.3
    return (lowpass(tone, 5000) + 0.4 * highpass(white(dur, seed), 3000) * perc(dur, 0.001, dur / 3)) * perc(dur, 0.002, dur / 2)


def arc(dur, seed=0, density=60):
    """放電のジリジリ（細かいパチパチを帯域でしぼる）。"""
    c = crackle(dur, density, seed, 0.006)
    return bandpass(c, 2500, 1.2) * 3 + 0.3 * lowpass(saw(120 + 30 * lfo(7, dur)), 1500)


def boom(dur=2.0, depth=40, seed=0, bright=0.4):
    """大きな爆発・衝撃（落ちていく低音と、砕けた雑音）。"""
    t = t_axis(dur)
    low = sine(glide(depth * 3, depth, dur, 0.4)) * np.exp(-t / (dur / 3))
    noise = lowpass(brown(dur, seed), glide(4000, 200, dur, 0.5)) * np.exp(-t / (dur / 2.5))
    crack = highpass(white(dur, seed + 1), 1500) * np.exp(-t / 0.05) * bright
    return low * 1.2 + noise + crack


def impact(dur=0.6, depth=70, seed=0, metal=0.0):
    t = t_axis(dur)
    out = thump(depth, dur, 0.5, seed) + lowpass(brown(dur, seed), 900) * np.exp(-t / 0.08)
    if metal:
        out = out + metal * clank(220, dur, seed)
    return out


def riser(dur, f0=80, f1=900, seed=0, noise=0.4):
    """ためる音（上がっていく音程と、増えていくざわめき）。"""
    t = t_axis(dur)
    g = (t / dur) ** 1.5
    f = glide(f0, f1, dur, 1.6)
    tone = saw(f) * 0.4 + saw(f * 1.007) * 0.4 + sine(f / 2) * 0.5
    return lowpass(tone, 300 + 5000 * g) * g + noise * whoosh(dur, 200, 6000, 1.2, seed, 2.0) * g


def beep(f, dur=0.12, shape='sine'):
    w = {'sine': sine, 'square': square, 'tri': tri}[shape](f, dur)
    return lowpass(w, 4000) * perc(dur, 0.003, dur / 2, 3)


def chime(base, dur=1.5, seed=0, bright=1.0):
    """澄んだ鐘（ガラス・光）。"""
    return partials(base, (1, 2.0, 2.76, 4.07, 5.4), (dur, dur / 2, dur / 3, dur / 5, dur / 8), dur,
                    (1, 0.4 * bright, 0.5 * bright, 0.25 * bright, 0.15 * bright), seed)


def glass(dur=1.2, seed=0, n=40):
    """ガラスや結晶が砕ける（高い共鳴が散らばって鳴る）。"""
    r = rng(seed)
    out = highpass(white(dur, seed), 2500) * np.exp(-t_axis(dur) / 0.08) * 0.6
    for _ in range(n):
        s = r.uniform(0, dur * 0.4)
        L = r.uniform(0.1, 0.5)
        p = partials(r.uniform(2000, 7000), (1, 1.5, 2.3), (L, L / 2, L / 3), L, seed=int(r.integers(1e6))) * r.uniform(0.1, 0.5)
        i = int(s * SR)
        out[i:i + len(p)] += p[:len(out) - i]
    return out


def voices(dur, notes, vowel='a', seed=0, breath=0.15, detune=0.006):
    """合唱のような和音（のこぎり波を声の形の帯域に通す）。notes は Hz の並び。"""
    formants = {'a': (800, 1150, 2900), 'o': (450, 800, 2830), 'u': (325, 700, 2530), 'e': (400, 1700, 2600), 'i': (300, 2200, 3000)}[vowel]
    r = rng(seed)
    src = np.zeros(int(dur * SR))
    for f in notes:
        for d in (-detune, 0, detune):
            vib = 1 + d + 0.004 * lfo(r.uniform(4.5, 6), dur, phase=r.uniform(0, 6))
            src += saw(f * vib)
    src += breath * pink(dur, seed) * len(notes)
    out = sum(bandpass(src, fo, 6) * g for fo, g in zip(formants, (1, 0.6, 0.25)))
    return out


def roar(dur, f0=90, seed=0, grit=0.6, vowel=(500, 900, 2400), contour=None):
    """獣の咆哮のような合成音（低いのこぎり波を声の形に通し、ざらつきと荒い揺れを足す）。"""
    t = t_axis(dur)
    c = contour if contour is not None else env([(0, 0.8), (dur * 0.3, 1.15), (dur, 0.7)], dur)
    f = f0 * c * (1 + 0.03 * lfo(23, dur) + 0.02 * np.sin(2 * np.pi * 7.3 * t))
    src = saw(f) + 0.6 * saw(f * 0.5) + grit * pink(dur, seed) * (1 + 0.5 * lfo(31, dur))
    out = sum(bandpass(src, fo * c, 4) * g for fo, g in zip(vowel, (1, 0.7, 0.3)))
    return drive(out * 1.5, 1.8) * env([(0, 0), (0.08, 1), (dur * 0.7, 0.9), (dur, 0)], dur)


def sub_drop(dur, f0=120, f1=28):
    return sine(glide(f0, f1, dur, 0.5)) * env([(0, 0), (0.02, 1), (dur, 0)], dur)


def servo(dur, f0=180, f1=320, seed=0):
    """モーター・アームが動く（上下するうなりと、歯車のざらつき）。"""
    f = glide(f0, f1, dur, 1.0)
    return (lowpass(saw(f) * 0.6 + square(f * 2.01) * 0.2, 1800) + 0.15 * bandpass(white(dur, seed), f * 8, 4)) \
        * env([(0, 0), (0.04, 1), (dur - 0.05, 1), (dur, 0)], dur)


def alarm(dur, f1=880, f2=660, rate=4.0):
    t = t_axis(dur)
    f = np.where((t * rate) % 1 < 0.5, f1, f2)
    return lowpass(square(f) * 0.5 + sine(f) * 0.5, 3000)


def heartbeat(dur, bpm=60, f=45):
    period = 60 / bpm
    return pulses(dur, period, lambda s: mix(thump(f, 0.25, 0.05), (0.8 * thump(f * 0.9, 0.22, 0.05), 0.22)))


def ring_burst(dur=0.8, f=300, seed=0):
    """光や力場がはじけて広がる（明るい上昇と、きらめく雑音）。"""
    return mix(chime(f, dur, seed) * 0.5, whoosh(dur, 500, 6000, 2, seed, 0.5) * 0.6, zap(min(dur, 0.3), 3000, 800, seed) * 0.3)


# ---------------------------------------------------------------- 重なった機械の音（ループ）

def pad(dur, freqs, cutoff=900, move=0.4, seed=0):
    """やわらかい和音（少しずつずらしたのこぎり波を、ゆっくり開け閉めするフィルタに通す）。"""
    r = rng(seed)
    src = np.zeros(int(dur * SR))
    for f in freqs:
        for d in (-0.004, 0.0, 0.0045):
            src += saw(f * (1 + d) * (1 + 0.002 * lfo(r.uniform(0.1, 0.3), dur, phase=r.uniform(0, 6))))
    c = cutoff * (1 + move * lfo(r.choice([1 / 6, 1 / 3, 0.5]), dur, phase=r.uniform(0, 6)))
    return lowpass(src / (3 * len(freqs)), c)


VOICES = {
    'thump': lambda s, v: thump(60, 0.22, 0.25, s) * v,
    'deep': lambda s, v: thump(42, 0.4, 0.2, s) * v,
    'clank': lambda s, v: clank(300 + 50 * (s % 5), 0.25, s) * v * 0.35,
    'ting': lambda s, v: clank(1400 + 200 * (s % 4), 0.15, s) * v * 0.15,
    'tick': lambda s, v: tick(2500 + 400 * (s % 3), 0.02, s) * v * 0.7,
    'click': lambda s, v: tick(1200, 0.03, s) * v,
    'hiss': lambda s, v: highpass(white(0.18, s), 2500) * perc(0.18, 0.01, 0.08) * v * 0.4,
    'puff': lambda s, v: bandpass(pink(0.3, s), 900, 1.5) * perc(0.3, 0.02, 0.15) * v * 0.7,
    'servo': lambda s, v: servo(0.22, 250 + 40 * (s % 4), 330 + 40 * (s % 3), s) * v * 0.3,
    'blip': lambda s, v: beep(1000 * 2 ** ((s % 7) / 7), 0.05, 'square') * v * 0.25,
    'pump': lambda s, v: lowpass(saw(glide(70, 50, 0.3)), 400) * perc(0.3, 0.03, 0.2) * v * 0.6,
    'zap': lambda s, v: zap(0.06, 4000, 1200, s) * v * 0.2,
    'drop': lambda s, v: bubbles(0.1, 20, s, 600, 1600)[:int(0.1 * SR)] * v * 0.5,
}


def groove(dur, patterns, step=None, seed=0, swing=0.0):
    """機械のリズム: patterns = {声: 16 文字の型}（'X' 強く、'x' ふつう、'.' 休み）。dur を 3 小節として、
    小節ごとに強さを少し変える。step はひとつの刻みの秒（既定は dur / 48）。"""
    r = rng(seed)
    step = step or dur / 48
    out = np.zeros(int(dur * SR))
    for name, pat in patterns.items():
        for k in range(int(dur / step)):
            c = pat[k % len(pat)]
            if c == '.':
                continue
            v = (1.0 if c == 'X' else 0.55) * r.uniform(0.75, 1.0)
            if c == '?' and r.random() < 0.5:
                continue
            s = k * step + (swing * step if k % 2 else 0)
            p = VOICES[name](int(r.integers(1e6)), v)
            i = int(s * SR)
            out[i:i + len(p)] += p[:len(out) - i]
    return out


def scatter(dur, makers, count, seed=0):
    """ときどき鳴る一回きりの音（makers の中から選んで count 個、ばらばらの時刻に）。"""
    r = rng(seed)
    out = np.zeros(int(dur * SR))
    for _ in range(count):
        p = makers[int(r.integers(len(makers)))](int(r.integers(1e6))) * r.uniform(0.5, 1)
        i = int(r.uniform(0, dur) * SR)
        out[i:i + len(p)] += p[:len(out) - i]
    return out


def machine_bed(dur, seed, power=60, power_amp=0.25, air_band=(200, 2500), air_amp=0.25, groove_pat=None, groove_amp=1.0,
                texture=None, texture_amp=0.5, chord=None, chord_amp=0.12, chord_cut=900, events=None, event_count=3, event_amp=0.5,
                room=0.35, step=None):
    """重なった機械の音: 空気の層・電源のうなり・リズム・質感・和音・ときどきの音を足し、部屋の響きでまとめる。"""
    layers = [air(dur, *air_band, seed=seed) * air_amp * (0.85 + 0.15 * lfo(1 / 3, dur)),
              hum(power, dur, (1, 2, 3, 4), (1, 0.6, 0.3, 0.15), seed=seed) * power_amp + sine(power / 2, dur) * power_amp * 0.4]
    if groove_pat:
        layers.append(groove(dur, groove_pat, step, seed) * groove_amp)
    if texture is not None:
        layers.append(texture * texture_amp)
    if chord:
        layers.append(pad(dur, chord, chord_cut, 0.5, seed) * chord_amp * 4)
    if events:
        layers.append(scatter(dur, events, event_count, seed) * event_amp)
    x = sum(fit(l, dur) for l in layers)
    return fit(reverb(x, 0.35, room, 3500, 0.0), dur)


def rich(x, seed=0, air_amp=0.15, room=0.3, voices=3):
    """単調な音に厚みを足す仕上げ: ずらした写しを重ね（コーラス）、音の大きさに沿った空気のざわめきを足し、響きでまとめる。"""
    x = np.asarray(x, dtype=float)
    e = lowpass(np.abs(x), 30)
    e /= max(1e-9, np.max(e))
    a = bandpass(pink(len(x) / SR + 0.01, seed)[:len(x)], 3000, 0.8) * e * air_amp
    from dsp import chorus
    return reverb(chorus(x, voices, 0.004, 0.4, seed) + a, 0.45, room, 4000, 0.6)
