// Type 95 Addition: the last pass (Rimulib's PostFxRenderer runs it after the Lightning Formula's own passes).
// Puts the effects over the full-resolution scene and applies the screen effects: shake, impact ripple, zoom blur,
// colour split, bloom, flash, vignette, desaturation, contrast and grain.

uniform sampler2D SceneSampler;
uniform sampler2D SceneLowSampler;
uniform float Upscaled;
uniform vec4 Grade;
uniform float Bloom;
uniform vec4 Impact;
uniform float Shake;
uniform float Aberration;
uniform float Desaturate;
uniform float ZoomBlur;
uniform vec4 Flash;
uniform vec4 Vignette;

vec3 fxAt(vec2 uv) {
    uv = clamp(uv, vec2(0.001), vec2(0.999));
    vec3 c = texture(DiffuseSampler, uv).rgb;
    if (Upscaled > 0.0) {
        // the effects ran at a lower resolution: put the scene's own fine detail back
        c += (texture(SceneSampler, uv).rgb - texture(SceneLowSampler, uv).rgb) * Upscaled;
    }
    return c;
}

void main() {
    vec2 uv = texCoord;
    float aspect = OutSize.x / max(OutSize.y, 1.0);

    if (Shake > 0.0) {
        uv += vec2(sin(GTime * 91.0) + sin(GTime * 57.0 + 1.3), cos(GTime * 83.0) + sin(GTime * 39.0 + 0.4)) * 0.0035 * Shake;
    }
    if (Impact.x > 0.0) {
        // a ring of refraction racing out from where the strike hit the screen
        vec2 d = (uv - Impact.zw) * vec2(aspect, 1.0);
        float r = length(d);
        float ring = (1.0 - Impact.x) * 1.4;
        float k = exp(-sqr((r - ring) / 0.07)) * Impact.x * 0.035;
        uv -= normalize(d + 1e-5) / vec2(aspect, 1.0) * k;
    }

    vec3 col;
    if (ZoomBlur > 0.001 || Aberration > 0.001) {
        vec2 dir = uv - 0.5;
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float t = float(i) / 7.0;
            vec2 u = 0.5 + dir * (1.0 - ZoomBlur * 0.09 * t);
            vec2 split = dir * Aberration * 0.014;
            acc += vec3(fxAt(u + split).r, fxAt(u).g, fxAt(u - split).b);
        }
        col = acc / 8.0;
    } else {
        col = fxAt(uv);
    }

    if (Bloom > 0.0) {
        col += texture(AuxSampler, uv).rgb * Bloom;
    }
    col = mix(col, vec3(lum(col)), saturate(Desaturate));
    if (Grade.x != 0.0) {
        col = (col - 0.5) * (1.0 + Grade.x) + 0.5;
    }
    if (Grade.y > 0.0) {
        col += (hash12(texCoord * OutSize + fract(GTime * 7.13) * 100.0) - 0.5) * 0.09 * Grade.y;
    }
    if (Vignette.w > 0.0) {
        vec2 v = (texCoord - 0.5) * vec2(aspect, 1.0);
        float edge = smoothstep(0.3, 1.05, length(v));
        col = mix(col, col * Vignette.rgb * 0.5 + Vignette.rgb * 0.06, edge * saturate(Vignette.w));
    }
    if (Flash.w > 0.0) {
        col = col + Flash.rgb * Flash.w;
    }
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
