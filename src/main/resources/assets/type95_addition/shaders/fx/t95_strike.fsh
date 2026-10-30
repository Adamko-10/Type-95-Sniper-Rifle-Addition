// Type 95 Addition: one Lightning Formula strike.
//   Target  where it hit          Origin  the muzzle it was fired from      (camera-relative)
//   P0      seconds since the main bolt (negative before it), radius, crater radius, charge
//   P1      glow colour, storm cover 0-1
//   P2      core colour, scale (the strike's size)
//   P3      seed, shock front (blocks), shock reach (blocks), beam 0-1
//   Pts[i]  the latest bolts: xyz where each hit (camera-relative), w how bright it still is (0 = unused)
// The storm over the target and its lit clouds, the bolts as columns of light (lighting up the ground round them),
// the flash of the main bolt, the plasma dome, the shock wave on the ground and as a wall of light, the crater's
// glowing cracks, and the beam from the rifle.

#include "t95.glsl"

// Sigil: seconds since the shot, the sky circle's radius, its brightness, the ground circle's brightness
uniform vec4 Sigil;

void main() {
    initPixel(texCoord);
    vec3 rd = viewDir(texCoord);
    bool sky = skyAt(texCoord);
    vec3 surf = scenePosAt(texCoord);
    float sceneT = sky ? FAR : length(surf);

    float age = P0.x;
    float R = P0.y;
    float C = P0.z;
    float charge = P0.w;
    vec3 glow = P1.rgb;
    float cover = P1.w;
    vec3 core = P2.rgb;
    float scale = P2.w;
    float seed = P3.x;
    float front = P3.y;
    float reach = P3.z;
    float beam = P3.w;
    float deckH = Target.y + min(90.0 + R * 0.12, 320.0);

    // ---- The shock wave bends the light passing through it ---------------------------------------------------
    vec2 uv = texCoord;
    float shockLive = (front > 0.0 && front < reach) ? 1.0 - front / reach : 0.0;
    if (!sky && shockLive > 0.0) {
        float dxz = length(surf.xz - Target.xz);
        float w = 2.0 + front * 0.025;
        float k = exp(-sqr((dxz - front) / w)) * shockLive;
        if (k > 0.002) {
            vec3 c = project(Target);
            vec2 away = normalize(texCoord - c.xy + 1e-5);
            uv += away * k * 0.012;
        }
    }
    vec3 base = sampleScene(uv);
    vec3 add = vec3(0.0);

    // ---- The storm: clouds over the target, the land beneath it darkened -------------------------------------
    // Minecraft's own clouds count as sky here (so the storm swallows them too)
    bool cloudPix = !sky && surf.y > Target.y + 70.0 && length(surf.xz) > 40.0;
    if (cover > 0.001) {
        float near = exp(-length(surf.xz - Target.xz) / max(R * 3.0, 200.0));
        if (cloudPix) {
            base = mix(base, base * vec3(0.42, 0.45, 0.56), cover * 0.75);
        } else if (!sky) {
            base = mix(base, base * vec3(0.5, 0.53, 0.65), cover * (0.25 + 0.4 * near));
        } else {
            vec2 deck = stormDeck(rd, Target.xz, deckH, max(R * 1.6, 160.0), age + seed, seed);
            if (deck.y > 0.0) {
                base = stormShade(base, deck.x, cover);
                // the clouds lit from inside by the bolts under them
                vec3 q = rd * deck.y;
                for (int i = 0; i < 16; i++) {
                    vec4 b = Pts[i];
                    if (b.w <= 0.0) continue;
                    float d = length(q.xz - b.xz);
                    add += mix(glow, core, 0.3) * b.w * exp(-d / 45.0) * (0.4 + deck.x) * 1.3;
                }
                add += glow * deck.x * cover * 0.05;
            } else {
                base = mix(base, base * vec3(0.45, 0.48, 0.6), cover * 0.6);
            }
        }
    }

    // ---- The Lightning Formula's circles: one flashing in the sky over the target as the bolt comes down, one burned
    //      into the ground where it hit, fading over a few seconds -----------------------------------------------
    if (Sigil.z > 0.001) {
        float hitT;
        vec2 c = circleOnPlane(vec3(0.0), rd, vec3(Target.x, deckH - 8.0, Target.z), vec3(0.0, -1.0, 0.0), vec3(1.0, 0.0, 0.0),
            Sigil.y, Sigil.x * 0.9 + seed, seed, sceneT, 1.0, hitT);
        add += (core * c.x * 1.8 + glow * c.y * 1.4) * Sigil.z;
    }
    if (Sigil.w > 0.001 && !sky) {
        float Rg = min(max(C, R * 0.2), 160.0) + 6.0;
        vec2 c = circleOnTerrain(surf, rd, Target, Rg, Sigil.x * 0.6 + seed, seed + 1.3, 1.0);
        add += (core * c.x * 1.3 + glow * c.y) * Sigil.w;
    }

    // ---- The bolts: jagged columns of light from the clouds down to the ground --------------------------------
    for (int i = 0; i < 16; i++) {
        vec4 b = Pts[i];
        if (b.w <= 0.0) continue;
        float bs = float(i) * 7.31 + seed;
        float roll = floor(age * 14.0);
        vec3 prev = b.xyz;
        float top = max(deckH, b.y + 40.0);
        float width = (0.18 + 0.12 * scale) * (0.6 + 0.4 * b.w);
        for (int s = 1; s <= 6; s++) {
            float f = float(s) / 6.0;
            vec3 next = vec3(b.x, mix(b.y, top, f), b.z);
            if (s < 6) {
                float j = (top - b.y) * 0.06;
                next.x += (hash11(bs + float(s) * 1.7 + roll) - 0.5) * j;
                next.z += (hash11(bs + float(s) * 2.9 + roll * 1.3) - 0.5) * j;
            }
            float tRay;
            float d = raySegmentDist(vec3(0.0), rd, prev, next, tRay);
            if (tRay < sceneT + 1.0) {
                add += glowLine(d, tRay, width, core * 2.2, glow * 1.3) * b.w;
            }
            prev = next;
        }
        // its light on the ground and everything near it
        if (!sky) {
            float d = length(surf - (b.xyz + vec3(0.0, 2.0, 0.0)));
            float l = sqr(lightFalloff(d, 10.0 + 8.0 * sqrt(scale))) * b.w;
            add += base * glow * l * 1.8 + glow * l * 0.05;
        }
    }

    // ---- The main bolt's flash: the whole radius lit up for an instant ----------------------------------------
    if (age >= 0.0) {
        float flash = exp(-age * 2.2);
        if (!sky) {
            float d = length(surf - (Target + vec3(0.0, 4.0, 0.0)));
            add += base * mix(glow, core, 0.5) * sqr(lightFalloff(d, R * 0.3 + 6.0)) * flash * 1.6;
        }
        float tP;
        float dP = rayPointDist(vec3(0.0), rd, Target + vec3(0.0, 2.0, 0.0), tP);
        if (tP < sceneT + 4.0) {
            add += core * halo(dP, (2.0 + 4.0 * sqrt(scale)) + gPix * tP) * flash * 2.0;
            add += glow * halo(dP, (10.0 + 18.0 * sqrt(scale)) + gPix * tP * 3.0) * flash * 0.6;
        }
    }

    // ---- The plasma dome: a crackling shell of electricity over the radius -------------------------------------
    if (age >= 0.0) {
        float grow = 1.0 - exp(-age * 5.0);
        float fade = exp(-age * 0.55) * smoothstep(0.0, 0.08, age);
        float Rd = min(R * 0.55, 700.0) * (0.25 + 0.75 * grow);
        if (fade > 0.01) {
            vec2 hit = raySphere(vec3(0.0), rd, Target, Rd);
            for (int side = 0; side < 2; side++) {
                float th = side == 0 ? hit.x : hit.y;
                if (th <= 0.0 || th > sceneT) continue;
                vec3 p = rd * th - Target;
                if (p.y < -1.0) continue;
                vec3 n = p / Rd;
                float fres = pow(1.0 - abs(dot(rd, n)), 2.5);
                float web = web3(n * 7.0 + vec3(0.0, -age * 0.8, age * 0.5) + seed, 9.0);
                float edgeFade = smoothstep(-1.0, Rd * 0.08, p.y);
                float k = (fres * 0.55 + web * (0.5 + fres)) * fade * edgeFade * (side == 0 ? 1.0 : 0.45);
                add += mix(glow, core, web * 0.6) * k * 0.9;
            }
        }
    }

    // ---- The shock wave: a ring racing over the ground and a wall of light rising from it ----------------------
    if (shockLive > 0.0) {
        float w = 2.0 + front * 0.025;
        if (!sky) {
            float dxz = length(surf.xz - Target.xz);
            float ground = 1.0 - smoothstep(10.0, 40.0 + R * 0.1, abs(surf.y - Target.y));
            float ring = exp(-sqr((dxz - front) / w)) * ground;
            add += (glow * 1.4 + core * ring * 0.8) * ring * shockLive * 1.3;
            // the ground it has passed over still glows faintly for a moment
            add += glow * 0.12 * smoothstep(front, front - w * 6.0, dxz) * smoothstep(front * 0.5, front, dxz) * shockLive * ground;
        }
        vec2 cyl = rayCylinderY(vec3(0.0), rd, Target, front);
        float wallH = 6.0 + R * 0.06;
        for (int side = 0; side < 2; side++) {
            float tc = side == 0 ? cyl.x : cyl.y;
            if (tc <= 0.0 || tc > sceneT) continue;
            vec3 p = rd * tc - Target;
            float hgt = p.y / wallH;
            if (hgt < -0.2 || hgt > 1.0) continue;
            float streak = 0.6 + 0.4 * vnoise2(vec2(atan(p.z, p.x) * front * 0.15, age * 3.0));
            float k = (1.0 - hgt) * smoothstep(-0.2, 0.05, hgt) * streak * shockLive * (side == 0 ? 0.5 : 0.25);
            add += glow * k;
        }
    }

    // ---- The crater: cracks in its floor glowing hot, cooling over half a minute ---------------------------------
    if (C > 0.0 && !sky && age > 0.0) {
        float dxz = length(surf.xz - Target.xz);
        if (dxz < C * 1.15 && surf.y < Target.y + 4.0) {
            float heat = exp(-age / 14.0) * smoothstep(C * 1.15, C * 0.7, dxz);
            // (in coordinates fixed to the crater, not the camera: the pattern must stay put as you move)
            vec2 q = (surf.xz - Target.xz) * (0.35 / (1.0 + C * 0.01));
            float cracks = pow(ridge2(q + seed), 14.0) + pow(ridge2(q * 2.3 - seed), 18.0) * 0.6;
            vec3 hot = mix(glow, core, 0.4 + 0.4 * cracks);
            add += hot * cracks * heat * 2.4 * (0.6 + 0.4 * facingUp(surf));
        }
    }

    // ---- The beam from the rifle to the target -----------------------------------------------------------------
    if (beam > 0.001) {
        vec3 prev = Origin;
        float L = length(Target - Origin);
        float roll = floor(age * 30.0);
        for (int s = 1; s <= 8; s++) {
            float f = float(s) / 8.0;
            vec3 next = mix(Origin, Target, f);
            if (s < 8) {
                float j = L * 0.025;
                next += vec3(hash11(seed + float(s) * 3.1 + roll) - 0.5, hash11(seed + float(s) * 5.3 + roll) - 0.5,
                             hash11(seed + float(s) * 7.7 + roll) - 0.5) * j;
            }
            float tRay;
            float d = raySegmentDist(vec3(0.0), rd, prev, next, tRay);
            if (tRay < sceneT + 1.0) {
                add += glowLine(d, tRay, 0.06 + 0.1 * charge, core * 2.0, glow * 1.2) * beam;
            }
            prev = next;
        }
    }

    fragColor = vec4(tonemap(base + add), 1.0);
}
