# -*- coding: utf-8 -*-
"""効果音を合成するための小さな道具箱（NumPy / SciPy だけ。録音素材は使わない）。

すべての音は 22050 Hz のモノラルの float 配列（-1〜1）で扱う。時間は秒、周波数は Hz。
- 発振器: sine / saw / square / tri（周波数は数か、時間ごとの配列）、FM、倍音を足した鐘や金属の音
- 雑音: white / pink / brown、パチパチ（crackle）、泡（bubbles）
- フィルタ: lowpass / highpass / bandpass（時間とともに動かせる）、共鳴（resonator）
- 包絡: env（点をつないだ形）、perc（立ち上がりと減衰）
- 効果: reverb（簡単な残響）、echo、drive（歪み）、crush（ビットを荒く）
- ループ: loop（終わりと始まりを重ねて、継ぎ目なく繰り返せる長さにする）
"""
import numpy as np
from scipy import signal

SR = 22050


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def rng(seed):
    return np.random.default_rng(seed)


# ---------------------------------------------------------------- 発振器

def _phase(freq, n):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,)) if np.ndim(freq) else np.full(n, float(freq))
    return 2 * np.pi * np.cumsum(f) / SR


def sine(freq, dur=None, n=None, phase=0.0):
    n = n or (len(freq) if np.ndim(freq) else int(dur * SR))
    return np.sin(_phase(freq, n) + phase)


def saw(freq, dur=None, n=None):
    n = n or (len(freq) if np.ndim(freq) else int(dur * SR))
    p = _phase(freq, n) / (2 * np.pi)
    return 2 * (p - np.floor(p + 0.5))


def square(freq, dur=None, n=None, duty=0.5):
    n = n or (len(freq) if np.ndim(freq) else int(dur * SR))
    p = (_phase(freq, n) / (2 * np.pi)) % 1
    return np.where(p < duty, 1.0, -1.0)


def tri(freq, dur=None, n=None):
    return 2 * np.abs(saw(freq, dur, n)) - 1


def fm(carrier, ratio, index, dur=None, n=None):
    """周波数変調: 搬送波 carrier、変調波は carrier×ratio、深さ index（配列でもよい）。"""
    n = n or (len(carrier) if np.ndim(carrier) else int(dur * SR))
    c = np.broadcast_to(np.asarray(carrier, dtype=float), (n,)) if np.ndim(carrier) else np.full(n, float(carrier))
    mod = np.sin(_phase(c * ratio, n))
    idx = np.broadcast_to(np.asarray(index, dtype=float), (n,)) if np.ndim(index) else index
    return np.sin(_phase(c, n) + idx * mod)


def partials(base, ratios, decays, dur, amps=None, seed=0):
    """倍音（ratios 倍の周波数）を、それぞれの速さ（decays 秒で 1/e）で減衰させて足す。鐘・金属・ガラス。"""
    t = t_axis(dur)
    out = np.zeros_like(t)
    r = rng(seed)
    for i, (k, d) in enumerate(zip(ratios, decays)):
        a = 1.0 if amps is None else amps[i]
        out += a * np.sin(2 * np.pi * base * k * t + r.uniform(0, 2 * np.pi)) * np.exp(-t / d)
    return out / max(1e-9, np.max(np.abs(out)))


# ---------------------------------------------------------------- 雑音

def white(dur, seed=0):
    return rng(seed).uniform(-1, 1, int(dur * SR))


def pink(dur, seed=0):
    w = rng(seed).normal(0, 1, int(dur * SR))
    b, a = [0.049922035, -0.095993537, 0.050612699, -0.004408786], [1, -2.494956002, 2.017265875, -0.522189400]
    p = signal.lfilter(b, a, w)
    return p / max(1e-9, np.max(np.abs(p)))


def brown(dur, seed=0):
    w = rng(seed).normal(0, 1, int(dur * SR))
    b = np.cumsum(w)
    b = signal.lfilter([1, -1], [1, -0.995], b)
    return b / max(1e-9, np.max(np.abs(b)))


def crackle(dur, rate, seed=0, length=0.004):
    """まばらなパチパチ（rate 回/秒。ひとつは length 秒の短い雑音）。"""
    r = rng(seed)
    out = np.zeros(int(dur * SR))
    k = int(length * SR)
    for s in r.uniform(0, dur, int(rate * dur)):
        i = int(s * SR)
        burst = r.uniform(-1, 1, k) * np.exp(-np.arange(k) / (k / 4)) * r.uniform(0.3, 1)
        out[i:i + k] += burst[:len(out) - i]
    return out


def bubbles(dur, rate, seed=0, lo=300, hi=1200):
    """泡がはじける音（上へすべる短いサイン）を rate 個/秒。"""
    r = rng(seed)
    out = np.zeros(int(dur * SR))
    for s in r.uniform(0, dur, int(rate * dur)):
        L = r.uniform(0.02, 0.07)
        tt = t_axis(L)
        f0 = r.uniform(lo, hi)
        b = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 2.5 * tt / L)) / SR) * np.exp(-tt / (L / 3)) * r.uniform(0.3, 1)
        i = int(s * SR)
        out[i:i + len(b)] += b[:len(out) - i]
    return out


# ---------------------------------------------------------------- フィルタ

def _sos(kind, f, order=2, q=None):
    f = np.clip(f, 20, SR / 2 - 200)
    if kind == 'band':
        bw = f / (q or 2)
        return signal.butter(order, [max(20, f - bw / 2), min(SR / 2 - 100, f + bw / 2)], 'bandpass', fs=SR, output='sos')
    return signal.butter(order, f, 'lowpass' if kind == 'low' else 'highpass', fs=SR, output='sos')


def _filt(kind, x, f, order=2, q=None):
    if not np.ndim(f):
        return signal.sosfilt(_sos(kind, f, order, q), x)
    # 時間とともに動くフィルタ: 256 サンプルずつ、状態を引き継いで係数を替える
    out = np.zeros_like(x)
    zi = None
    for i in range(0, len(x), 256):
        sos = _sos(kind, float(f[min(i, len(f) - 1)]), order, q)
        if zi is None or zi.shape != (sos.shape[0], 2):
            zi = np.zeros((sos.shape[0], 2))
        out[i:i + 256], zi = signal.sosfilt(sos, x[i:i + 256], zi=zi)
    return out


def lowpass(x, f, order=2):
    return _filt('low', x, f, order)


def highpass(x, f, order=2):
    return _filt('high', x, f, order)


def bandpass(x, f, q=2.0, order=2):
    return _filt('band', x, f, order, q)


def resonator(x, f, decay):
    """共鳴（f Hz で decay 秒ほど鳴り続ける 2 次の IIR）。打撃を金属や木の響きにする。"""
    r = np.exp(-1 / (decay * SR))
    w = 2 * np.pi * f / SR
    return signal.lfilter([1 - r], [1, -2 * r * np.cos(w), r * r], x)


# ---------------------------------------------------------------- 包絡

def env(points, dur):
    """(時刻, 値) の点を直線でつないだ包絡（時刻は秒）。"""
    t = t_axis(dur)
    ts, vs = zip(*points)
    return np.interp(t, ts, vs)


def perc(dur, attack=0.005, decay=None, curve=4.0):
    t = t_axis(dur)
    d = decay or dur
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-curve * np.maximum(0, t - attack) / d)


def glide(f0, f1, dur, curve=1.0):
    """f0 から f1 へすべる周波数（curve > 1 で後半に急ぐ、< 1 で前半に急ぐ）。指数的に動く。"""
    k = (t_axis(dur) / dur) ** curve
    return f0 * (f1 / f0) ** k


def lfo(rate, dur, depth=1.0, phase=0.0):
    return depth * np.sin(2 * np.pi * rate * t_axis(dur) + phase)


# ---------------------------------------------------------------- 効果

def reverb(x, size=0.5, mix=0.3, damp=3000, tail=1.0):
    """Schroeder 型の簡単な残響。size 0〜1 で部屋の大きさ、tail 秒ぶん後ろへのばす。"""
    x = np.concatenate([x, np.zeros(int(tail * SR))])
    wet = np.zeros_like(x)
    for d, g in ((1557, 0.84), (1617, 0.83), (1491, 0.82), (1422, 0.81)):
        D = int(d * (0.4 + size) * SR / 44100)
        a = np.zeros(D + 1)
        a[0], a[D] = 1, -g * (0.85 + 0.12 * size)
        wet += signal.lfilter([1], a, x)
    for d in (225, 556):
        D = int(d * SR / 44100)
        b = np.zeros(D + 1)
        b[0], b[D] = -0.5, 1
        a = np.zeros(D + 1)
        a[0], a[D] = 1, -0.5
        wet = signal.lfilter(b, a, wet)
    wet = lowpass(wet, damp)
    wet /= max(1e-9, np.max(np.abs(wet)))
    return (1 - mix) * x / max(1e-9, np.max(np.abs(x))) + mix * wet


def echo(x, delay, feedback=0.4, taps=4, tail=None):
    D = int(delay * SR)
    x = np.concatenate([x, np.zeros(D * taps if tail is None else int(tail * SR))])
    out = x.copy()
    for k in range(1, taps + 1):
        out[D * k:] += x[:len(x) - D * k] * feedback ** k
    return out


def drive(x, amount=2.0):
    return np.tanh(amount * x) / np.tanh(amount)


def crush(x, bits=6, hold=2):
    """ビットを荒くし、サンプルを hold 個ずつ保つ（古い機械・デジタルの声）。"""
    q = 2 ** (bits - 1)
    y = np.round(x * q) / q
    if hold > 1:
        y = np.repeat(y[::hold], hold)[:len(x)]
    return y


def pan_none(x):
    return x


# ---------------------------------------------------------------- 組み立て

def mix(*parts):
    """長さの違う音を、頭をそろえて足す。(音, 開始秒) の組も渡せる。"""
    items = [(p, 0.0) if not isinstance(p, tuple) else p for p in parts]
    n = max(int(s * SR) + len(p) for p, s in items)
    out = np.zeros(n)
    for p, s in items:
        i = int(s * SR)
        out[i:i + len(p)] += p
    return out


def fit(x, dur):
    n = int(dur * SR)
    return x[:n] if len(x) >= n else np.concatenate([x, np.zeros(n - len(x))])


def fade(x, fin=0.005, fout=0.02):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def loop(make, dur, overlap=0.5):
    """継ぎ目なく繰り返せる音を作る: make(長さ) で dur + overlap 秒を作り、終わりの overlap 秒を始まりへ重ねる。"""
    x = make(dur + overlap)
    n, o = int(dur * SR), int(overlap * SR)
    x = x[:n + o].copy()
    w = np.linspace(0, 1, o)
    head = x[:o] * w + x[n:n + o] * (1 - w)
    return np.concatenate([head, x[o:n]])


def normalize(x, peak=-1.0, rms=None):
    """ピーク（dBFS）にそろえる。rms を渡せば、その平均の大きさに寄せて、ピークは peak を超えない。"""
    x = x - np.mean(x)
    p = np.max(np.abs(x)) or 1.0
    if rms is not None:
        r = np.sqrt(np.mean(x ** 2)) or 1e-9
        g = 10 ** (rms / 20) / r
        g = min(g, 10 ** (peak / 20) / p)
    else:
        g = 10 ** (peak / 20) / p
    return x * g


def chorus(x, voices=3, depth=0.004, rate=0.35, seed=0):
    """少しずつ揺れる遅れを重ねて、音に厚みを出す（ひとつの音を、何人かで鳴らしたように）。"""
    r = rng(seed)
    n = len(x)
    idx = np.arange(n)
    out = x.copy()
    for v in range(voices):
        delay = (0.008 + depth * (1 + np.sin(2 * np.pi * rate * (0.7 + 0.6 * r.random()) * idx / SR + r.uniform(0, 6)))) * SR
        out += np.interp(idx - delay, idx, x, left=0) * 0.7
    return out / (1 + 0.7 * voices)
