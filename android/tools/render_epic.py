"""Renders the Pairo epic theme (epic.json) to OGG.

Mirrors the WebAudio engine in the web version, instrument for instrument:
  ostinato  2 detuned saws (±7 cents) -> low-pass 1800 Hz, short staccato envelope
  pad       3 detuned saws (0, ±9 cents) -> low-pass 1100 Hz, 0.35 s attack, 0.6 s release
  brass     2 detuned saws -> low-pass 2400 Hz, 60 ms attack, plus the same line an octave down
  bass      sine + saw at 30 %, held for the bar
  taiko     sine 95 -> 48 Hz with low-passed noise thump, 0.55 s decay
  tom       sine 160 -> 85 Hz, 0.3 s
  crash     high-passed noise, 2.2 s decay
Everything except the ostinato & bass is sent to a 2.8 s noise-tail reverb.
"""
import json, os, sys, subprocess
import numpy as np
from scipy.signal import butter, lfilter, fftconvolve
from scipy.io import wavfile

SR = 44100
rng = np.random.default_rng(11)
hz = lambda m: 440.0 * 2 ** ((m - 69) / 12)


def saw(f, n, max_freq):
    t = np.arange(n) / SR
    out = np.zeros(n)
    for k in range(1, int(min(max_freq, SR / 2) / f) + 1):
        out += np.sin(2 * np.pi * k * f * t) / k
    return out * (2 / np.pi)


def lowpass(x, cutoff, order=2):
    b, a = butter(order, cutoff / (SR / 2))
    return lfilter(b, a, x)


def highpass(x, cutoff, order=2):
    b, a = butter(order, cutoff / (SR / 2), btype="high")
    return lfilter(b, a, x)


def adsr(n_hold, attack, release, v, sustain=0.8, decay=0.12):
    a, d, r = int(attack * SR), int(decay * SR), int(release * SR)
    n = n_hold + r
    e = np.full(n, v * sustain)
    a = min(a, n_hold)
    e[:a] = np.linspace(0, v, a)
    d = min(d, max(0, n_hold - a))
    e[a:a + d] = np.linspace(v, v * sustain, d)
    e[n_hold:] = np.linspace(v * sustain, 0, r)
    return e


def voice(m, dur, vol, detunes, cutoff, attack, release, sustain=0.8, decay=0.12, max_freq=8000):
    n_hold, n_rel = int(dur * SR), int(release * SR)
    n = n_hold + n_rel
    x = np.zeros(n)
    for c in detunes:
        x += saw(hz(m) * 2 ** (c / 1200), n, max_freq)
    x = lowpass(x / len(detunes), cutoff)
    return x * adsr(n_hold, attack, release, vol, sustain, decay)


def drum(f0, f1, dur, vol, noise=0.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = np.log(f1 / f0) / dur
    phase = 2 * np.pi * f0 * (np.exp(k * t) - 1) / k
    env = np.exp(np.log(0.001) * t / dur)
    x = np.sin(phase)
    if noise:
        x += noise * lowpass(rng.uniform(-1, 1, n), 600) * np.exp(np.log(0.001) * t / (dur * 0.3))
    return x * env * vol


def crash(vol):
    n = int(2.2 * SR)
    t = np.arange(n) / SR
    return highpass(rng.uniform(-1, 1, n), 5000) * vol * np.exp(np.log(0.001) * t / 2.2)


def impulse(seconds=2.8):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = rng.uniform(-1, 1, n) * np.power(1 - t / seconds, 3)
    return ir / np.sqrt(np.sum(ir ** 2))  # unit energy, like WebAudio's normalised ConvolverNode


def add(buf, start, x):
    end = min(len(buf), start + len(x))
    if end > start:
        buf[start:end] += x[: end - start]


def render(song):
    step = 60 / song["bpm"] / 4
    total = int(song["steps"] * step * SR)
    dry = np.zeros(total + 4 * SR)
    wet = np.zeros(total + 4 * SR)
    mx = song["mix"]
    at = lambda s: int(round(s * step * SR))

    for bar in range(song["steps"] // 16):
        root, chord = song["roots"][bar], song["chords"][bar]
        b0 = bar * 16
        # Bass: whole bar.
        n = int(16 * step * SR)
        tt = np.arange(n + int(0.3 * SR)) / SR
        bass = (np.sin(2 * np.pi * hz(root) * tt) + 0.3 * saw(hz(root), len(tt), 1500))
        add(dry, at(b0), lowpass(bass, 400) * adsr(n, 0.02, 0.3, mx["bass"], 0.9, 0.2))
        # Pad (choir-like) on the chord.
        for m in chord:
            add(wet, at(b0), voice(m, 16 * step, mx["pad"], (0, -9, 9), 1100, 0.35, 0.6, 0.9, 0.3, 4000))
        # String ostinato on 16ths.
        for i, off in enumerate(song["ostinato"]):
            add(dry, at(b0 + i), voice(root + 12 + off, step * 0.55, mx["ostinato"], (-7, 7), 1800,
                                        0.005, 0.06, 0.5, 0.05))
        # Drums.
        for s in song["taiko"]:
            add(wet, at(b0 + s), drum(95, 48, 0.55, mx["taiko"], noise=0.5))
        for s in (song["fillToms"] if bar in song["fillBars"] else song["toms"]):
            add(wet, at(b0 + s), drum(160, 85, 0.3, mx["tom"]))
        if bar in song["crashBars"]:
            add(wet, at(b0), crash(mx["crash"]))

    for s, m, ln in song["melody"]:
        dur = ln * step * 0.95
        add(wet, at(s), voice(m, dur, mx["brass"], (-5, 5), 2400, 0.06, 0.2, 0.85, 0.15))
        add(wet, at(s), voice(m - 12, dur, mx["brassLow"], (-5, 5), 1600, 0.06, 0.2, 0.85, 0.15))

    rev = fftconvolve(wet, impulse())[: len(wet)]
    out = dry + wet + rev * mx["reverb"]
    # Fold the tail back onto the start so the loop is seamless.
    loop = out[:total].copy()
    tail = out[total:]
    loop[: len(tail)] += tail[: total]
    return loop * mx["master"]


if __name__ == "__main__":
    outdir = sys.argv[1]
    os.makedirs(outdir, exist_ok=True)
    song = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "epic.json")))
    x = render(song)
    peak = np.abs(x).max()
    x = x / max(1.0, peak / 0.95)
    rms = np.sqrt(np.mean(x ** 2))
    wav = os.path.join(outdir, "music.wav")
    wavfile.write(wav, SR, (x * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5",
                    os.path.join(outdir, "music.ogg")], check=True)
    os.remove(wav)  # a leftover music.wav in res/raw would clash with music.ogg
    print(f"music.ogg {len(x)/SR:.2f}s peak(before norm) {peak:.2f} rms {rms:.3f}")
