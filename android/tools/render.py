"""Renders the Pairo sound effects (sfx_*.ogg) to OGG.

The game music comes from render_epic.py. Pass --chiptune to also render the
earlier chiptune theme (song-chiptune.json) as chiptune.ogg.

The synthesis mirrors the WebAudio engine in the web version:
square melody & arpeggio, triangle bass, sine kick, filtered-noise snare/hats,
attack 5 ms, decay to 50 % over the note, 50 ms release, then a 3.2 kHz low-pass on the music bus.
"""
import json, sys, numpy as np, subprocess, os
from scipy.signal import butter, lfilter
from scipy.io import wavfile

SR = 44100
rng = np.random.default_rng(7)
mid = lambda m: 440.0 * 2 ** ((m - 69) / 12)

def osc(kind, f, n, f_end=None):
    t = np.arange(n) / SR
    if f_end is None:
        phase = 2 * np.pi * f * t
    else:  # exponential glide
        k = np.log(f_end / f) / (n / SR)
        phase = 2 * np.pi * f * (np.exp(k * t) - 1) / k
    if kind == "sine":
        return np.sin(phase)
    harm = int((SR / 2) / f)
    out = np.zeros(n)
    if kind == "square":
        for h in range(1, harm + 1, 2):
            out += np.sin(h * phase) / h
        return out * 4 / np.pi
    if kind == "triangle":
        for i, h in enumerate(range(1, harm + 1, 2)):
            out += ((-1) ** i) * np.sin(h * phase) / (h * h)
        return out * 8 / np.pi ** 2
    raise ValueError(kind)

def env(n_note, n_rel, v):
    a = int(0.005 * SR)
    e = np.empty(n_note + n_rel)
    e[:a] = np.linspace(0, v, a)
    e[a:n_note] = v * np.exp(np.log(0.5) * np.linspace(0, 1, n_note - a))
    e[n_note:] = np.linspace(v * 0.5, 0, n_rel)
    return e

def note(kind, m, dur, v):
    n, r = int(dur * SR), int(0.05 * SR)
    return osc(kind, mid(m), n + r) * env(n, r, v)

def noise_hit(dur, v, cutoff):
    n = int(dur * SR)
    b, a = butter(2, cutoff / (SR / 2), btype="high")
    x = lfilter(b, a, rng.uniform(-1, 1, n))
    return x * v * np.exp(np.log(0.001) * np.linspace(0, 1, n))

def kick(v):
    n = int(0.14 * SR)
    return osc("sine", 150, n, 45) * v * np.exp(np.log(0.001) * np.linspace(0, 1, n))

def add(buf, start, x):
    end = min(len(buf), start + len(x))
    buf[start:end] += x[: end - start]

def render_song(song):
    step = 60 / song["bpm"] / 4
    total = int(song["steps"] * step * SR)
    buf = np.zeros(total + SR)  # 1 s tail, folded back for a seamless loop
    mx = song["mix"]
    at = lambda s: int(round(s * step * SR))
    for s, m, ln in song["melody"]:
        add(buf, at(s), note("square", m, ln * step * 0.92, mx["melody"]))
    for bar in range(song["steps"] // 16):
        chord, root = song["chords"][bar], song["bassRoots"][bar]
        for i, off in enumerate(song["bassPattern"]):
            add(buf, at(bar * 16 + i * 2), note("triangle", root + off, step * 1.8, mx["bass"]))
        for i, idx in enumerate(song["arpPattern"]):
            add(buf, at(bar * 16 + i), note("square", chord[idx] + 12, step * 0.6, mx["arp"]))
        for s in song["kick"]:
            add(buf, at(bar * 16 + s), kick(mx["kick"]))
        for s in song["snare"]:
            add(buf, at(bar * 16 + s), noise_hit(0.12, mx["snare"], 1500))
        for s in song["hat"]:
            add(buf, at(bar * 16 + s), noise_hit(0.04, mx["hat"], 7000))
    buf[: len(buf) - total] += buf[total:]
    buf = buf[:total]
    b, a = butter(2, 3200 / (SR / 2))
    return lfilter(b, a, buf) * mx["master"]

def seq(parts):
    """parts: list of (kind, midi, start, dur, vol)."""
    end = max(s + d for _, _, s, d, _ in parts) + 0.06
    buf = np.zeros(int(end * SR))
    for kind, m, s, d, v in parts:
        add(buf, int(s * SR), note(kind, m, d, v))
    return buf

def sfx():
    out = {}
    out["match"] = seq([("triangle", 88, 0, 0.06, 0.22), ("triangle", 93, 0.06, 0.12, 0.22), ("sine", 100, 0.06, 0.14, 0.12)])
    out["miss"] = seq([("triangle", 55, 0, 0.09, 0.2), ("triangle", 50, 0.09, 0.16, 0.2)])
    out["tick"] = seq([("sine", 81, 0, 0.07, 0.35)])
    out["go"] = seq([("triangle", 84, 0, 0.18, 0.2), ("sine", 96, 0, 0.18, 0.15)])
    n = int(0.32 * SR)
    out["freeze"] = osc("triangle", 1400, n, 300) * 0.3 * np.exp(np.log(0.01) * np.linspace(0, 1, n))
    fan = [("triangle", m, i * 0.09, 0.08, 0.2) for i, m in enumerate([72, 76, 79, 84])]
    fan += [("triangle", m, 0.36, 0.45, 0.13) for m in (72, 76, 79, 84)]
    out["win"] = seq(fan)
    out["timeup"] = seq([("triangle", 67, 0, 0.12, 0.2), ("triangle", 64, 0.13, 0.12, 0.2), ("triangle", 60, 0.26, 0.3, 0.2)])
    out["click"] = seq([("sine", 76, 0, 0.03, 0.25)])
    out["star"] = seq([("triangle", 84, 0, 0.08, 0.2), ("sine", 91, 0.05, 0.16, 0.14)])
    return out

def write(path, x):
    x = np.clip(x / max(1.0, np.abs(x).max() / 0.95), -1, 1)
    wavfile.write(path, SR, (x * 32767).astype(np.int16))

if __name__ == "__main__":
    outdir = sys.argv[1]
    os.makedirs(outdir, exist_ok=True)
    tracks = {"sfx_" + k: v for k, v in sfx().items()}
    if "--chiptune" in sys.argv:
        song = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "song-chiptune.json")))
        tracks["chiptune"] = render_song(song)
    for name, x in tracks.items():
        wav = os.path.join(outdir, name + ".wav")
        write(wav, x)
        q = "4" if name == "chiptune" else "6"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis",
                        "-q:a", q, os.path.join(outdir, name + ".ogg")], check=True)
        os.remove(wav)
        print(f"{name}.ogg  {len(x)/SR:.2f}s  peak {np.abs(x).max():.2f}")
