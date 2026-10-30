// Type 95 Addition: the local player's mark (left click while charging).
//   Target  the mark (camera-relative)
//   P0      seconds since it was placed, the sigil's radius, the strike's radius at the charge so far, charge 0-1
//   P1      glow colour, 1 when the mark is on a mob
//   P2      core colour, seed
// A circle drawn on the ground round the mark, a ring showing how far the strike will reach (growing with the
// charge), a faint tint over that ground, and a thin beacon of light standing up from the mark.

#include "t95.glsl"

void main() {
    initPixel(texCoord);
    vec3 rd = viewDir(texCoord);
    bool sky = skyAt(texCoord);
    vec3 surf = scenePosAt(texCoord);
    float sceneT = sky ? FAR : length(surf);

    float age = P0.x;
    float sigilR = P0.y;
    float reach = P0.z;
    float charge = P0.w;
    vec3 glow = P1.rgb;
    vec3 core = P2.rgb;
    float seed = P2.w;
    float pop = smoothstep(0.0, 0.25, age);
    float pulse = 0.75 + 0.25 * sin(age * 6.0);

    vec3 base = sampleScene(texCoord);
    vec3 add = vec3(0.0);

    if (!sky) {
        // the sigil, spinning, drawn in as the mark lands
        vec2 c = circleOnTerrain(surf, rd, Target, sigilR * (0.6 + 0.4 * pop), age * 1.5 + seed, seed, 1.0);
        add += (core * c.x * 1.4 + glow * c.y * 1.2) * pop;

        // the strike's reach: a ring on the ground, the land inside it tinted
        vec3 q = surf - Target;
        float dxz = length(q.xz);
        float near = 1.0 - smoothstep(reach * 0.25 + 8.0, reach * 0.5 + 30.0, abs(q.y));
        float w = 0.35 + gPix * length(surf) * 2.0 + reach * 0.004;
        float ring = exp(-sqr((dxz - reach) / w)) * near;
        float dash = step(0.35, fract(atan(q.z, q.x) / TAU * 48.0 - age * 0.4));
        add += glow * ring * (0.5 + 0.5 * dash) * pulse * 1.4 * pop;
        base = mix(base, base * (0.85 + glow * 0.3), smoothstep(reach, reach - 2.0, dxz) * near * 0.35 * charge * pop);
    }

    // the beacon: a thin column of light standing up from the mark, flickering
    float tRay;
    float d = raySegmentDist(vec3(0.0), rd, Target, Target + vec3(0.0, 90.0, 0.0), tRay);
    if (tRay < sceneT + 1.0) {
        float up = clamp(dot(rd * tRay - Target, vec3(0.0, 1.0, 0.0)) / 90.0, 0.0, 1.0);
        float flick = 0.7 + 0.3 * hash11(floor(age * 18.0) + seed);
        add += glowLine(d, tRay, 0.05, core * 1.2, glow) * (1.0 - up) * flick * pop * (0.6 + 0.6 * charge);
    }

    fragColor = vec4(tonemap(base + add), 1.0);
}
