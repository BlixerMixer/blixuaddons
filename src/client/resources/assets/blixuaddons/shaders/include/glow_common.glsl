// Shared helpers for blixuAddons glow styles.
// Needs <minecraft:globals.glsl> imported first (for GameTime).

// Seconds since the day started, for animation. GameTime is 0..1 over one Minecraft day (1200 s).
float glowSeconds() {
    return GameTime * 1200.0;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// Smooth value noise, 0..1.
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), u.x),
               mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), u.x), u.y);
}

// A field of twinkling stars on a grid of `cell` pixels.
// density 0..1 = share of cells that hold a star; size = core radius in px;
// sparkle 0..1 = how much 4-point "cross" flare each star gets.
float starField(vec2 px, float cell, float density, float size, float sparkle, float seed) {
    vec2 c = floor(px / cell);
    float total = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 id = c + vec2(x, y);
            float h = hash12(id + seed);
            if (h > density) continue;
            vec2 pos = (id + 0.15 + 0.7 * hash22(id + seed * 1.7)) * cell;
            vec2 d = px - pos;
            float bright = 0.45 + 0.55 * hash12(id + seed * 3.1);
            float twinkle = 0.55 + 0.45 * sin(glowSeconds() * (1.5 + 2.5 * hash12(id + 7.0)) + h * 40.0);
            float r = size * (0.6 + 0.8 * hash12(id + seed * 5.3));
            float core = exp(-dot(d, d) / (r * r));
            float cross = exp(-abs(d.x) / (r * 0.35)) * exp(-abs(d.y) / (r * 3.0))
                        + exp(-abs(d.y) / (r * 0.35)) * exp(-abs(d.x) / (r * 3.0));
            total += (core + sparkle * cross * 0.6) * bright * twinkle;
        }
    }
    return clamp(total, 0.0, 1.0);
}

// Rotate a colour's hue by `angle` radians around the grey axis (keeps brightness roughly).
vec3 hueShift(vec3 c, float angle) {
    const vec3 k = vec3(0.57735);
    float ca = cos(angle);
    return clamp(c * ca + cross(k, c) * sin(angle) + k * dot(k, c) * (1.0 - ca), 0.0, 1.0);
}

// Layer `top` (straight alpha) over `base` (straight alpha).
vec4 over(vec4 base, vec4 top) {
    float a = top.a + base.a * (1.0 - top.a);
    vec3 rgb = a > 0.0001 ? (top.rgb * top.a + base.rgb * base.a * (1.0 - top.a)) / a : vec3(0.0);
    return vec4(rgb, a);
}

// ---------------------------------------------------------------- aura designs
// Shared by Hand Aura (post/hand_starfield), Self Aura, Mob Highlight and the slayer glows (post/glow_starfield).
// 0 Starfield (plain soft band), 1 Nebula, 2 Plasma, 3 Flame, 4 Galaxy. Every design is painted only in
// the two colours it's given (colA main, colB second), so it follows the colours chosen in the menu.

float fbm2(vec2 p, int octaves) {
    float sum = 0.0, amp = 0.5;
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) break;
        sum += valueNoise(p) * amp;
        p = p * 2.02 + vec2(5.3, 1.7);
        amp *= 0.5;
    }
    return sum / (1.0 - amp * 2.0); // 0..1 whatever the octave count
}

// Rising noise for the Flame design; callers lift the aura by it so tongues lick past the band.
float flameNoise(vec2 px, float t, bool low) {
    return fbm2(vec2(px.x * 0.045, px.y * 0.03 - t * 2.2), low ? 2 : 4);
}

// One design at a pixel. halo 0..1 = how deep in the aura (1 on the object), aura = raw band value,
// body 0/1 = on the object, k = strength, tint = share kept on the object (0 = nothing on it),
// t = animation time (already scaled by the module's speed). Returns straight-alpha colour.
vec4 paintDesign(int style, vec2 px, float t, float halo, float aura, float body, vec3 colA, vec3 colB,
                 float k, float tint, bool low, float flameN) {
    float cover = mix(1.0, tint, body);
    if (body > 0.5) k = 1.0; // strength sizes the band; on the object only the design opacity (tint) counts
    // the band thins out softly towards its edge instead of ending in a flat slab
    float h = body > 0.5 ? 1.0 : pow(halo, 1.7);
    if (style == 1 || style == 4) {
        // Nebula / Galaxy: drifting domain-warped clouds in the two colours, bright knots
        vec2 q = px * 0.012 + vec2(t * 0.05, t * 0.03);
        float w = fbm2(q + 3.1, low ? 2 : 4);
        float n = fbm2(q + w * 1.8, low ? 3 : 5);
        float m = smoothstep(0.3, 0.7, n);
        if (style == 4) m = 0.5 + 0.5 * sin(t * 0.6 + w * 6.2831 + n * 3.0); // Galaxy: the colours swirl into each other
        vec3 col = mix(colB, colA, m);
        col += mix(colA, vec3(1.0), 0.6) * pow(n, 4.0) * 1.2;
        return vec4(col, clamp(h * (0.3 + smoothstep(0.25, 0.7, n)) * k, 0.0, 1.0) * cover);
    }
    if (style == 2) {
        // Plasma: crackling arcs (ridged noise) over a soft electric haze
        vec2 q = px * 0.02;
        float n1 = fbm2(q + vec2(t * 0.8, -t * 0.5), low ? 3 : 4);
        float arcs = pow(1.0 - abs(2.0 * n1 - 1.0), 14.0);
        if (!low) arcs += 0.6 * pow(1.0 - abs(2.0 * fbm2(q * 1.7 - vec2(t * 0.6, t * 0.9) + 4.0, 4) - 1.0), 18.0);
        vec4 r = vec4(mix(colB, colA, halo), clamp(h * 0.55 * k, 0.0, 1.0) * cover);
        float onBody = tint > 0.0 ? min(1.0, tint + 0.3) : 0.0;
        r = over(r, vec4(mix(colA, vec3(1.0), 0.6), clamp(arcs * h * 1.4 * k, 0.0, 1.0) * mix(1.0, onBody, body)));
        return r;
    }
    if (style == 3) {
        // Flame: heat from the lifted band, main colour in the body, white-hot at the core
        float heat = clamp(halo * (0.5 + flameN), 0.0, 1.0);
        vec3 col = mix(colB, colA, smoothstep(0.1, 0.55, heat));
        col = mix(col, mix(colA, vec3(1.0), 0.75), smoothstep(0.7, 1.0, heat));
        return vec4(col, clamp(smoothstep(0.04, 0.6, heat) * heat * 1.3 * k, 0.0, 1.0) * cover);
    }
    // Starfield: a plain soft band in the glow colour
    return vec4(mix(colB, colA, halo), clamp(h * 0.9 * k, 0.0, 1.0) * cover);
}
