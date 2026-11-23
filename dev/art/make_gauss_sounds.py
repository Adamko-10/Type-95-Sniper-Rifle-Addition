"""Makes the Gauss Formula's sounds (run from the project root, like make_sounds.py, whose helpers it uses)."""
import numpy as np

from make_sounds import (SR, bandpass, compress, crackle, env_exp, fade, highpass, loopable, lowpass, norm, rng,
                         sweep_bandpass, t_axis, write)


def gauss_whine():
    """The capacitors charging: a thin rising-sounding whine (the game raises its pitch with the charge)."""
    secs = 2.0
    t = t_axis(secs)
    f = 2200 + 14 * np.sin(2 * np.pi * 3 * t)
    phase = 2 * np.pi * np.cumsum(f) / SR
    whine = (np.sin(phase) + 0.35 * np.sin(phase * 2.003) + 0.12 * np.sin(phase * 3.01)) * 0.35
    beat = 0.8 + 0.2 * np.sin(2 * np.pi * 16 * t)
    hum = lowpass(np.sign(np.sin(2 * np.pi * 120 * t)), 1400) * 0.12
    hiss = bandpass(rng.standard_normal(len(t)), 5000, 12000) * 0.05
    tick = crackle(secs, 10, 3000, 9000) * 0.4
    return norm(loopable(whine * beat + hum + hiss + tick, 0.3), 0.75)


def gauss_stage():
    """A coil bank coming up: a clack and a bright two-note ping."""
    secs = 0.6
    t = t_axis(secs)
    clack = bandpass(rng.standard_normal(len(t)), 1500, 9000) * env_exp(t, 0.0005, 0.012) * 1.5
    ping = np.sin(2 * np.pi * 1975 * t) * env_exp(t, 0.002, 0.12)
    ping += np.sin(2 * np.pi * 2637 * t) * env_exp(t - 0.07, 0.002, 0.18) * (t > 0.07)
    return fade(norm(clack + ping * 0.5, 0.8))


def gauss_crack():
    """The slug leaving the rails: a hard supersonic crack with a short metallic ring."""
    secs = 0.9
    t = t_axis(secs)
    snap = highpass(rng.standard_normal(len(t)), 1200) * env_exp(t, 0.0002, 0.006) * 2.5
    body = bandpass(rng.standard_normal(len(t)), 300, 6000) * env_exp(t, 0.0005, 0.04) * 1.2
    ring = np.zeros_like(t)
    for f, d in ((3150, 0.09), (4720, 0.07), (6900, 0.05)):
        ring += np.sin(2 * np.pi * f * t) * env_exp(t, 0.001, d)
    zap = crackle(secs, 200) * env_exp(t, 0.001, 0.05)
    return fade(norm(compress(snap + body + ring * 0.25 + zap, 3.0), 0.9))


def gauss_boom():
    """The deep boom after it (the shock of the air), starting a moment after the crack."""
    secs = 3.2
    t = t_axis(secs)
    s = np.maximum(t - 0.07, 0.0)
    on = t > 0.07
    f = 55 * np.exp(-s / 0.4) + 28
    phase = 2 * np.pi * np.cumsum(f * on) / SR
    thump = np.sin(phase) * env_exp(s, 0.004, 0.5) * on * 1.4
    brown = norm(highpass(np.cumsum(rng.standard_normal(len(t))), 18), 1.0)
    rumble = lowpass(brown, 140) * env_exp(s, 0.02, 1.0) * on * 1.3
    rush = sweep_bandpass(rng.standard_normal(len(t)), 900, 80, 0.6) * env_exp(s, 0.01, 0.4) * on * 0.6
    return fade(norm(compress(thump + rumble + rush, 2.5), 0.9), 0.002, 0.6)


if __name__ == "__main__":
    write("gauss_whine", gauss_whine())
    write("gauss_stage", gauss_stage())
    write("gauss_crack", gauss_crack())
    write("gauss_boom", gauss_boom())
