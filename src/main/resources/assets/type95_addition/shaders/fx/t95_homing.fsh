// Type 95 Addition: a Homing Formula crush (the Phantom Ruby's red).
//   Target  the crushed one's feet (camera-relative)
//   P0      seconds since the hit, its width, its height now (it shrinks as it's pressed), fade 0-1
//   P1      the slam's flash (hitting the ground, going flat), 1 for a boss, 1 to turn the world red (the nearest), seed
// The world turns red round it (all of it, up close), the crushed one stands in a magenta halo with dark streaks
// raining down over it, and the picture glitches: bands of it slip sideways with the colours split.

#include "t95.glsl"

void main() {
    initPixel(texCoord);
    float t = P0.x;
    float w = P0.y;
    float h = max(P0.z, 0.15);
    float fade = P0.w;
    float slam = P1.x;
    float boss = P1.y;
    float tint = P1.z;
    float seed = P1.w;
    float dist = length(Target + vec3(0.0, h * 0.5, 0.0));
    float near = fade * (1.0 - smoothstep(24.0 + 48.0 * boss, 96.0 + 96.0 * boss, dist));

    // the glitch: in bursts (always on a slam) bands of the picture slip sideways, the colours pulled apart
    vec2 uv = texCoord;
    float frame = floor(t * 14.0);
    float burst = step(0.8 - 0.6 * saturate(slam), hash11(frame + seed));
    vec2 cell = floor(uv * vec2(5.0, 26.0));
    float pick = step(0.84, hash12(cell + frame * 1.7 + seed));
    float shift = (hash12(cell * 1.3 + frame) - 0.5) * 0.07 * burst * pick * near;
    vec2 guv = uv + vec2(shift, 0.0);
    float split = (0.0015 + 0.012 * burst * pick) * near;
    vec3 base = vec3(sampleScene(guv + vec2(split, 0.0)).r, sampleScene(guv).g, sampleScene(guv - vec2(split, 0.0)).b);

    // red over everything near it
    float l = lum(base);
    vec3 red = vec3(l * 1.4 + 0.05, l * 0.22, l * 0.36 + 0.02);
    base = mix(base, red, near * tint * 0.55);

    vec3 add = vec3(0.0);
    vec3 a = project(Target);
    vec3 b = project(Target + vec3(0.0, h, 0.0));
    if (a.z > 0.0 && b.z > 0.0) {
        vec2 aspect = vec2(OutSize.x / OutSize.y, 1.0);
        vec2 p = uv * aspect;
        vec2 pa = a.xy * aspect;
        vec2 pb = b.xy * aspect;
        float side = w * 0.6 + 0.3;
        float rx = max(length(project(Target + vec3(side, 0.0, 0.0)).xy * aspect - pa),
                       length(project(Target + vec3(0.0, 0.0, side)).xy * aspect - pa));
        rx = max(rx, 0.004);
        // the body: a capsule from its feet to its head on the screen
        float d = sdSegment2(p, pa, pb);
        float inside = 1.0 - smoothstep(rx * 0.7, rx * 1.05, d);
        float halo = exp(-max(d - rx * 0.6, 0.0) / (rx * 0.5));
        float pulse = 0.75 + 0.25 * sin(t * 9.0);
        add += vec3(1.0, 0.12, 0.42) * halo * (1.0 - inside * 0.7) * 0.45 * pulse * fade;

        // the streaks: thin dark lines raining down over it, from well above its head
        float up = max(length(pb - pa), rx) * 2.4;
        float col = sdSegment2(p, pa, pa + vec2(0.0, up));
        float region = 1.0 - smoothstep(rx * 0.9, rx * 1.4, col);
        float px = uv.x * OutSize.x / 3.0;
        float rate = hash11(floor(px) + seed);
        float drop = fract(uv.y * 2.5 + t * (1.5 + 3.0 * rate) + rate * 7.0);
        float streak = step(0.55, rate) * smoothstep(0.6, 0.0, drop) * step(fract(px), 0.5);
        base = mix(base, base * vec3(0.3, 0.02, 0.06), streak * region * fade * 0.85);
        add += vec3(1.0, 0.2, 0.5) * saturate(slam) * halo * 0.8;
    }
    // and the slam's flash
    add += vec3(1.0, 0.1, 0.3) * saturate(slam) * 0.25 * near * max(tint, 0.3);

    fragColor = vec4(tonemap(base + add), 1.0);
}
