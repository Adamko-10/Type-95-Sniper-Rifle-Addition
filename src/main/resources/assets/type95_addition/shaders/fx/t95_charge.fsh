// Type 95 Addition: one player charging the Lightning Formula.
//   Origin  the muzzle            Caster  the player's eyes        (camera-relative)
//   P0      charge 0-1, stage 1-4, full 0/1, seconds charging
//   P1      glow colour, how much the world darkens
//   P2      core colour, storm clouds overhead 0-1
//   P3      orb size (blocks), first person 0/1, seed, light reach (blocks)
// The orb of plasma at the muzzle, its light on the ground round the player, the world going dark and a storm
// gathering overhead as the charge grows.

#include "t95.glsl"

void main() {
    initPixel(texCoord);
    vec3 rd = viewDir(texCoord);
    bool sky = skyAt(texCoord);
    vec3 surf = scenePosAt(texCoord);
    float sceneT = sky ? FAR : length(surf);

    float charge = P0.x;
    float full = P0.z;
    float secs = P0.w;
    vec3 glow = P1.rgb;
    vec3 core = P2.rgb;
    float orbSize = P3.x;
    float seed = P3.z;
    float reach = P3.w;

    // flicker: a few quick random dips and spikes, faster as the charge grows
    float beat = floor(secs * (10.0 + 14.0 * charge));
    float flick = 0.75 + 0.5 * hash11(beat + seed);

    vec3 base = sampleScene(texCoord);

    // The storm gathering overhead (stage 3 on)
    float clouds = P2.w;
    if (clouds > 0.001 && sky) {
        vec2 deck = stormDeck(rd, Caster.xz, Caster.y + 70.0, 160.0, secs + seed, seed);
        if (deck.y > 0.0) {
            float inside = hash11(floor(secs * 6.0) + seed * 3.0);
            float lit = inside > 0.82 ? (inside - 0.82) * 5.5 * full : 0.0;
            base = stormShade(base, deck.x, clouds * 0.9);
            base += glow * deck.x * clouds * lit * 0.9;
        }
    }

    // The world darkens (sky included) and takes the formula's tint
    float dim = P1.w;
    if (dim > 0.001) {
        vec3 tint = mix(vec3(0.42, 0.46, 0.6), glow * 0.6 + 0.2, 0.25);
        base = mix(base, base * tint, dim);
    }

    vec3 add = vec3(0.0);

    // The muzzle's light on everything near the player
    if (!sky) {
        float d = length(surf - Origin);
        float light = sqr(lightFalloff(d, reach)) * flick * (0.25 + 0.55 * charge);
        add += base * glow * light * 1.1;
        add += glow * light * 0.03;
    }

    // The orb: a hot core and a wide soft glow, flaring at full charge
    float tOrb;
    float dOrb = rayPointDist(vec3(0.0), rd, Origin, tOrb);
    if (tOrb < sceneT + orbSize * 2.0) {
        float r = orbSize * (0.55 + 0.45 * charge) * (0.9 + 0.2 * flick);
        float pix = gPix * tOrb;
        add += core * halo(dOrb, r * 0.3 + pix) * (0.7 + 0.4 * full);
        add += glow * halo(dOrb, r * 1.1 + pix * 2.0) * (0.15 + 0.25 * charge);
        add += glow * halo(dOrb, r * 3.5 + pix * 4.0) * 0.04 * charge;
        // spikes of light from the orb (a lens star), turning slowly
        vec3 toPix = rd * tOrb - Origin;
        float spikes = 0.0;
        vec2 sp = vec2(dot(toPix, normalize(cross(rd, vec3(0.0, 1.0, 0.0)))), toPix.y);
        sp = rot(secs * 0.7) * sp;
        float a = atan(sp.y, sp.x);
        spikes = pow(abs(cos(a * 3.0)), 60.0) * exp(-length(sp) / (r * 3.0));
        add += core * spikes * charge * 0.5;
    }

    fragColor = vec4(tonemap(base + add), 1.0);
}
