// Type 95 Addition: helpers shared by the Lightning Formula's passes (included after Rimulib's common.glsl).

// A glowing line at distance d from the ray, tRay along it: a hot core of width w and a soft glow around it,
// never thinner than a pixel (so far-away bolts stay visible instead of flickering away).
vec3 glowLine(float d, float tRay, float w, vec3 core, vec3 glow) {
    float pix = gPix * tRay * 1.2;
    float we = max(w, pix);
    float keep = w / we;
    float wh = max(min(w * 10.0, tRay * 0.01 + w * 4.0), pix * 3.0);
    return core * exp(-d / we) * keep * 1.5 + glow * exp(-d / wh) * (0.35 + 0.65 * keep) * 0.6;
}

// Ridged noise: thin bright lines where the noise crosses its middle (an electric web, cracks).
float ridge2(vec2 p) {
    return 1.0 - abs(vnoise2(p) * 2.0 - 1.0);
}

float ridge3(vec3 p) {
    return 1.0 - abs(vnoise3(p) * 2.0 - 1.0);
}

float web3(vec3 p, float sharp) {
    float a = ridge3(p);
    float b = ridge3(p * 2.13 + vec3(5.2, 1.3, 7.7));
    return pow(a, sharp) * 0.7 + pow(b, sharp * 1.4) * 0.45;
}

// How much a surface faces up (1 = floor, 0 = wall), from the screen-space slope of its position.
float facingUp(vec3 surf) {
    vec3 n = cross(dFdx(surf), dFdy(surf));
    float len = length(n);
    return len < 1e-8 ? 1.0 : smoothstep(0.5, 0.85, abs(n.y) / len);
}

// A storm's cloud deck at height h (camera-relative), swirling round centre (xz), as seen along rd from the eye:
// x = cover 0-1, y = distance along the ray (or -1 when the ray doesn't reach the deck).
vec2 stormDeck(vec3 rd, vec2 centre, float h, float reach, float t, float seed) {
    if (rd.y < 0.015 || h <= 1.0) {
        return vec2(0.0, -1.0);
    }
    float tp = h / rd.y;
    vec2 q = rd.xz * tp - centre;
    float r = length(q);
    float fall = exp(-r / reach);
    if (fall < 0.01) {
        return vec2(0.0, tp);
    }
    float swirl = 2.4 / (1.0 + r / (reach * 0.35)) + t * 0.06;
    vec2 s = rot(swirl) * q / max(reach * 0.22, 8.0);
    float n = fbm2(s + vec2(seed, seed * 0.37) + vec2(t * 0.05, -t * 0.03));
    float n2 = fbm2(s * 2.7 - vec2(t * 0.11, t * 0.07) + seed);
    float cover = smoothstep(0.32, 0.72, n * 0.75 + n2 * 0.35) * fall;
    // the eye of the storm: a darker, faster swirl right over the centre
    cover = max(cover, smoothstep(reach * 0.25, 0.0, r) * 0.85);
    return vec2(saturate(cover), tp);
}

vec3 stormShade(vec3 base, float cover, float dark) {
    vec3 cloud = vec3(0.10, 0.11, 0.15);
    return mix(base, cloud, saturate(cover * dark));
}
