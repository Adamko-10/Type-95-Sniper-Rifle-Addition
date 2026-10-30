"""Makes the Lightning Formula's sounds (run from the project root; needs numpy and ffmpeg on the PATH).

Every sound is synthesised here from noise and oscillators, so the addon ships no one else's audio. Output:
src/main/resources/assets/type95_addition/sounds/*.ogg (mono, 44.1 kHz, Vorbis).
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/type95_addition/sounds"
rng = np.random.default_rng(95)


def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def spectral(x, gain_fn):
    """Filters x by a gain curve over frequency (Hz), via the FFT."""
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1.0 / SR)
    return np.fft.irfft(spec * gain_fn(freqs), len(x))


def lowpass(x, fc, order=2.0):
    return spectral(x, lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order)))


def highpass(x, fc, order=2.0):
    return spectral(x, lambda f: 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1e-3)) ** (2 * order)))


def bandpass(x, lo, hi, order=2.0):
    return highpass(lowpass(x, hi, order), lo, order)


def sweep_bandpass(x, f_start, f_end, width=0.5, steps=24):
    """A band-pass whose centre glides from f_start to f_end (crossfaded blocks)."""
    out = np.zeros_like(x)
    n = len(x)
    edges = np.linspace(0, n, steps + 1).astype(int)
    for i in range(steps):
        fc = f_start * (f_end / f_start) ** (i / max(1, steps - 1))
        lo, hi = edges[i], edges[i + 1]
        pad = (hi - lo) // 2
        a, b = max(0, lo - pad), min(n, hi + pad)
        seg = bandpass(x[a:b], fc * (1 - width), fc * (1 + width))
        win = np.hanning(b - a)
        out[a:b] += seg * win
    return out


def env_exp(t, attack, decay):
    return np.minimum(1.0, t / max(attack, 1e-4)) * np.exp(-np.maximum(t - attack, 0) / decay)


def saw(f, t, harmonics=14):
    out = np.zeros_like(t)
    for k in range(1, harmonics + 1):
        out += np.sin(2 * np.pi * f * k * t) / k
    return out


def crackle(seconds, density, lo=2500, hi=9000, click_len=0.004, seed_shift=0):
    """Electric crackle: random tiny bursts of bright noise."""
    n = int(seconds * SR)
    out = np.zeros(n)
    count = rng.poisson(density * seconds)
    L = int(click_len * SR)
    for _ in range(count):
        at = rng.integers(0, max(1, n - L))
        burst = rng.standard_normal(L) * np.exp(-np.arange(L) / (L / 4)) * rng.uniform(0.3, 1.0)
        out[at:at + L] += burst
    return bandpass(out, lo, hi)


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m < 1e-9 else x / m * peak


def compress(x, drive):
    """Soft-clips a peaky sound so its body comes up under its transient."""
    x = norm(x, 1.0)
    return np.tanh(x * drive) / np.tanh(drive)


def fade(x, fin=0.005, fout=0.05):
    n = len(x)
    a, b = int(fin * SR), int(fout * SR)
    if a > 0:
        x[:a] *= np.linspace(0, 1, a)
    if b > 0:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def loopable(x, overlap=0.4):
    """Crossfades the tail into the head so the sound loops without a click."""
    o = int(overlap * SR)
    head, body, tail = x[:o], x[o:-o], x[-o:]
    ramp = np.linspace(0, 1, o)
    joined = tail * (1 - ramp) + head * ramp
    return np.concatenate([joined, body])


def write(name, x):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5",
                        os.path.join(OUT, name + ".ogg")], check=True)
    print("wrote", name, f"{len(x) / SR:.2f}s")


# ---- The charge -------------------------------------------------------------------------------------------------

def charge_loop():
    secs = 4.4
    t = t_axis(secs)
    hum = saw(55, t, 18) * (0.75 + 0.25 * np.sin(2 * np.pi * 12.5 * t))
    hum = lowpass(hum, 900) * 0.55
    buzz = np.sign(np.sin(2 * np.pi * 110 * t)) * 0.08
    buzz = lowpass(buzz, 2400)
    whine = np.sin(2 * np.pi * (1760 + 9 * np.sin(2 * np.pi * 5 * t)) * t) * 0.05
    sizzle = bandpass(rng.standard_normal(len(t)), 3000, 9000) * 0.08 * (0.6 + 0.4 * np.sin(2 * np.pi * 3.25 * t))
    crack = crackle(secs, 26) * 0.9
    x = loopable(hum + buzz + whine + sizzle + crack)
    return norm(x, 0.8)


def charge_start():
    secs = 1.4
    t = t_axis(secs)
    f = 60 * (12 ** (t / secs))
    phase = 2 * np.pi * np.cumsum(f) / SR
    rise = np.zeros_like(t)
    for k in range(1, 10):
        rise += np.sin(phase * k) / k
    rise = lowpass(rise, 3000) * env_exp(t, 0.6, 0.5)
    whoosh = sweep_bandpass(rng.standard_normal(len(t)), 300, 4000) * env_exp(t, 0.9, 0.3) * 0.6
    crack = crackle(secs, 60) * env_exp(t, 0.8, 0.4) * 1.2
    zap = bandpass(rng.standard_normal(len(t)), 2000, 10000) * env_exp(t - 1.0, 0.002, 0.08) * (t > 1.0) * 1.5
    return fade(norm(rise + whoosh + crack + zap, 0.85))


def stage_up():
    secs = 1.1
    t = t_axis(secs)
    zap = bandpass(rng.standard_normal(len(t)), 1500, 11000) * env_exp(t, 0.001, 0.05) * 1.6
    zap += crackle(secs, 90) * env_exp(t, 0.002, 0.18)
    chime = (np.sin(2 * np.pi * 880 * t) + 0.6 * np.sin(2 * np.pi * 1318.5 * t) + 0.35 * np.sin(2 * np.pi * 1760 * t * 1.003))
    chime *= env_exp(t, 0.004, 0.35) * (1 + 0.3 * np.sin(2 * np.pi * 7 * t)) * 0.45
    thump = np.sin(2 * np.pi * 70 * t) * env_exp(t, 0.002, 0.12) * 0.8
    return fade(norm(compress(zap + chime + thump, 1.8), 0.85))


def full_charge():
    secs = 3.2
    t = t_axis(secs)
    chorus = np.zeros_like(t)
    for d in (-0.6, -0.2, 0.25, 0.7):
        chorus += saw(110 * (1 + d / 100), t, 12)
    chorus = lowpass(chorus, 1800) * env_exp(t, 0.25, 1.4) * 0.35
    boom = np.sin(2 * np.pi * 48 * t) * env_exp(t, 0.003, 0.5) * 1.0
    boom += lowpass(rng.standard_normal(len(t)), 160) * env_exp(t, 0.005, 0.8) * 1.5
    roar = crackle(secs, 160) * env_exp(t, 0.01, 0.9) * 1.6
    snap = bandpass(rng.standard_normal(len(t)), 1000, 12000) * env_exp(t, 0.001, 0.04) * 2.0
    return fade(norm(compress(chorus + boom + roar + snap, 2.0), 0.9))


def full_loop():
    secs = 3.6
    t = t_axis(secs)
    hum = saw(110, t, 14) * (0.7 + 0.3 * np.sin(2 * np.pi * 20 * t))
    hum = lowpass(hum, 1500) * 0.4
    crack = crackle(secs, 70) * 1.2
    arcs = crackle(secs, 8, 600, 3000, 0.03) * 0.8
    x = loopable(hum + crack + arcs)
    return norm(x, 0.8)


def fizzle():
    secs = 0.7
    t = t_axis(secs)
    x = crackle(secs, 50) * env_exp(t, 0.005, 0.25)
    x += sweep_bandpass(rng.standard_normal(len(t)), 3000, 400) * env_exp(t, 0.01, 0.2) * 0.4
    return fade(norm(x, 0.7))


# ---- The shot and the strike -----------------------------------------------------------------------------------

def fire():
    secs = 2.4
    t = t_axis(secs)
    crack = rng.standard_normal(len(t)) * env_exp(t, 0.0005, 0.03) * 2.0
    crack = highpass(crack, 400)
    rip = np.zeros_like(t)
    for i in range(40):
        at = int(rng.uniform(0, 0.12) * SR)
        L = int(0.003 * SR)
        rip[at:at + L] += rng.standard_normal(L) * rng.uniform(0.4, 1.0)
    rip = bandpass(rip, 800, 12000) * 1.2
    whoosh = sweep_bandpass(rng.standard_normal(len(t)), 5000, 200) * env_exp(t, 0.02, 0.35) * 0.9
    thump = np.sin(2 * np.pi * 42 * t) * env_exp(t, 0.003, 0.35) * 1.4
    tail = lowpass(rng.standard_normal(len(t)), 250) * env_exp(t, 0.05, 0.8) * 1.2
    return fade(norm(compress(crack + rip + whoosh + thump + tail, 2.5), 0.88))


def thunder(far=False):
    secs = 7.5 if far else 6.5
    t = t_axis(secs)
    x = np.zeros_like(t)
    if not far:
        # the tearing crack: dozens of sharp clicks in the first fifth of a second
        for i in range(70):
            at = int(abs(rng.normal(0, 0.06)) * SR)
            L = int(rng.uniform(0.002, 0.008) * SR)
            x[at:at + L] += rng.standard_normal(L) * rng.uniform(0.3, 1.0) * np.exp(-at / SR / 0.15)
        x = highpass(x, 300) * 2.2
        x += np.sin(2 * np.pi * 36 * t) * env_exp(t, 0.004, 0.6) * 1.2
    # the roll: brown-ish noise, low-passed, swelling a few times as it dies away
    brown = np.cumsum(rng.standard_normal(len(t)))
    brown = highpass(brown, 20)
    roll = lowpass(brown, 120 if far else 220)
    roll = norm(roll, 1.0)
    swells = np.zeros_like(t)
    for i in range(6):
        c = rng.uniform(0.2, secs * 0.7)
        swells += np.exp(-((t - c) / rng.uniform(0.25, 0.8)) ** 2) * rng.uniform(0.4, 1.0)
    body = env_exp(t, 0.6 if far else 0.08, 2.2 if far else 1.8)
    x += roll * (0.5 + swells) * body * (1.4 if far else 1.6)
    return fade(norm(compress(x, 2.0 if far else 4.0), 0.88), 0.002, 0.8)


def zap(variant):
    secs = 0.45
    t = t_axis(secs)
    x = crackle(secs, 120 + 60 * variant) * env_exp(t, 0.002, 0.12)
    x += bandpass(rng.standard_normal(len(t)), 2500, 11000) * env_exp(t, 0.001, 0.02) * 1.5
    x += np.sin(2 * np.pi * (90 + 30 * variant) * t) * env_exp(t, 0.002, 0.06) * 0.6
    return fade(norm(x, 0.85))


def shockwave():
    secs = 3.0
    t = t_axis(secs)
    rush = sweep_bandpass(rng.standard_normal(len(t)), 1200, 60, 0.6) * env_exp(t, 0.15, 0.9) * 1.6
    rumble = lowpass(np.cumsum(rng.standard_normal(len(t))), 90)
    rumble = norm(highpass(rumble, 18), 1.0) * env_exp(t, 0.1, 1.2)
    crack = crackle(secs, 30) * env_exp(t, 0.05, 0.8) * 0.5
    return fade(norm(rush + rumble + crack, 0.9), 0.01, 0.4)


def dome():
    secs = 3.5
    t = t_axis(secs)
    buzz = np.sign(np.sin(2 * np.pi * 100 * t + 0.4 * np.sin(2 * np.pi * 3 * t)))
    buzz = lowpass(buzz, 2500) * 0.35
    choir = sum(np.sin(2 * np.pi * f * t) for f in (220, 330.5, 441, 662)) * 0.15
    crack = crackle(secs, 80) * 0.8
    x = (buzz + choir + crack) * env_exp(t, 0.2, 1.2)
    return fade(norm(x, 0.8), 0.01, 0.6)


def mark():
    """The lock-on: two quick rising electric chirps and a crackle."""
    secs = 0.6
    t = t_axis(secs)
    x = np.zeros_like(t)
    for start, f0 in ((0.0, 1400), (0.09, 2100)):
        tt = np.maximum(t - start, 0)
        f = f0 * (1 + 0.6 * np.minimum(tt / 0.05, 1))
        x += np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(tt, 0.002, 0.07) * (t >= start)
    x += crackle(secs, 90) * env_exp(t, 0.002, 0.15) * 0.6
    x += np.sin(2 * np.pi * 180 * t) * env_exp(t, 0.002, 0.05) * 0.4
    return fade(norm(compress(x, 1.5), 0.85))


def scope():
    """The scope coming up: a soft whoosh and a mechanical click."""
    secs = 0.45
    t = t_axis(secs)
    whoosh = sweep_bandpass(rng.standard_normal(len(t)), 600, 2500, 0.5, 12) * env_exp(t, 0.08, 0.12) * 0.6
    click = highpass(rng.standard_normal(len(t)), 2000) * env_exp(t - 0.12, 0.0005, 0.008) * (t >= 0.12) * 1.5
    click += np.sin(2 * np.pi * 900 * t) * env_exp(t - 0.12, 0.001, 0.02) * (t >= 0.12) * 0.5
    return fade(norm(whoosh + click, 0.8))


if __name__ == "__main__":
    write("charge_loop", charge_loop())
    write("charge_start", charge_start())
    write("stage_up", stage_up())
    write("full_charge", full_charge())
    write("full_loop", full_loop())
    write("fizzle", fizzle())
    write("fire", fire())
    write("thunder_near", thunder(False))
    write("thunder_far", thunder(True))
    for v in range(3):
        write(f"zap_{v + 1}", zap(v))
    write("shockwave", shockwave())
    write("dome", dome())
    write("mark", mark())
    write("scope", scope())
