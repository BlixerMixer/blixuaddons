#version 330

// Cosmic Sky styles: 0 Nebula, 1 Aurora, 2 Realistic clouds, 3 Sunset, 4 Galaxy core.
// Nebula: deep space, a domain-warped two-colour nebula with dark dust lanes concentrated in a
// tilted galactic band, bright emission knots, and two layers of twinkling stars. All per pixel from
// the view direction, so it's seamless in every direction and costs no textures.

in vec3 vDir;
flat in vec4 vColorA;
flat in vec3 vColorB;
flat in vec4 vParams;
flat in float vLow;
flat in int vStyle;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i), n100 = hash13(i + vec3(1, 0, 0));
    float n010 = hash13(i + vec3(0, 1, 0)), n110 = hash13(i + vec3(1, 1, 0));
    float n001 = hash13(i + vec3(0, 0, 1)), n101 = hash13(i + vec3(1, 0, 1));
    float n011 = hash13(i + vec3(0, 1, 1)), n111 = hash13(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float fbm(vec3 p, int octaves) {
    float sum = 0.0, amp = 0.5;
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) break;
        sum += noise3(p) * amp;
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        amp *= 0.5;
    }
    return sum;
}

// Stars on a 3D grid around the unit sphere: one candidate per cell, round core, slow twinkle.
float stars(vec3 d, float scale, float density, float t) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    float result = 0.0;
    for (int z = -1; z <= 1; z++)
    for (int y = -1; y <= 1; y++)
    for (int x = -1; x <= 1; x++) {
        vec3 c = cell + vec3(x, y, z);
        float h = hash13(c);
        if (h > density) continue;
        vec3 pos = c + 0.2 + 0.6 * hash33(c);
        float dist = length(p - pos);
        float size = 0.06 + 0.1 * hash13(c + 7.0);
        float twinkle = 0.65 + 0.35 * sin(t * (1.0 + 3.0 * hash13(c + 3.0)) + h * 50.0);
        result += smoothstep(size, 0.0, dist) * twinkle * (0.4 + 0.6 * hash13(c + 11.0));
    }
    return result;
}

vec3 nebulaSky(vec3 d, float t, float intensity, bool low) {
    vec3 colA = vColorA.rgb;
    vec3 colB = vColorB;

    // Deep space: almost black, a touch of the nebula colour towards the zenith.
    vec3 color = mix(vec3(0.008, 0.008, 0.02), colA * 0.06 + vec3(0.01, 0.008, 0.03), smoothstep(-0.3, 0.9, d.y));

    // Galactic band: a tilted great circle where the nebula and stars gather.
    vec3 bandAxis = normalize(vec3(0.35, 0.82, 0.45));
    float band = exp(-pow(dot(d, bandAxis), 2.0) * 7.0);

    // Domain-warped nebula, drifting slowly.
    int oct = low ? 3 : 5;
    vec3 q = d * 2.1 + vec3(t * 0.004, 0.0, t * 0.0025);
    // Low-End Mode: one warp channel reused for all three axes, fewer octaves everywhere
    float w0 = fbm(q + 3.3, oct);
    vec3 warp = low ? vec3(w0, w0 * 0.8, 1.0 - w0) : vec3(w0, fbm(q + 7.7, oct), fbm(q + 1.1, oct));
    float n = fbm(q + warp * 1.6, oct);
    float cloud = smoothstep(0.42, 0.85, n) * mix(0.25, 1.0, band);
    float tint = low ? smoothstep(0.3, 0.75, w0) : smoothstep(0.3, 0.75, fbm(q * 1.7 - warp, oct));
    vec3 nebula = mix(colA, colB, tint);

    // Dark dust lanes cut through the brightest parts.
    float dust = low ? 0.0 : smoothstep(0.5, 0.72, fbm(d * 6.0 + warp * 0.8 + 5.0, 4)) * band;
    float neb = cloud * (1.0 - dust * 0.75) * intensity;
    color += nebula * neb * 0.9;
    // Hot emission knots: whiter where the cloud is densest.
    color += mix(nebula, vec3(1.0), 0.55) * pow(cloud, 3.5) * 0.7 * intensity;

    // Stars: a fine dense layer (more in the band) and a sparse bright layer.
    float starDensity = vParams.z;
    float starBright = vParams.w;
    float s = stars(d, 60.0, 0.12 * starDensity, t * 0.7) * 1.4;
    if (!low) s += stars(d, 160.0, 0.25 * starDensity * mix(0.5, 1.6, band), t) * 0.8;
    vec3 starTint = mix(vec3(1.0), mix(colA, colB, 0.5), 0.15);
    color += starTint * s * starBright * (1.0 - dust * 0.6);

    return color;
}

// fbm rescaled to 0..1 whatever the octave count (value noise centres it on 0.5)
float fbmN(vec3 p, int octaves) {
    return fbm(p, octaves) / (1.0 - pow(0.5, float(octaves)));
}

// ---- Aurora: thin folding sheets of light, marched up through their height so the rays hang
// vertically; a running average between steps smooths the slices into soft curtains ----

float auroraField(vec2 p, float t, bool low) {
    // the curtains' centreline wanders and folds with slow noise
    float warp = noise3(vec3(p.x * 0.12, 1.3, t * 0.03)) * 3.0;
    if (!low) warp += noise3(vec3(p.x * 0.45, 7.1, t * 0.06)) * 1.1;
    float sheets = 0.0;
    for (int k = 0; k < 2; k++) {
        float u = p.y + (k == 0 ? 4.0 : 7.5) + warp + 1.2 * sin(p.x * 0.15 + t * 0.03 + float(k) * 2.0);
        sheets += exp(-u * u * 2.5) * (k == 0 ? 1.0 : 0.55);
    }
    // vertical rays along the curtain, shimmering
    float rays = noise3(vec3(p.x * 3.0, 2.0, t * 0.35));
    if (!low) rays = rays * 0.7 + 0.3 * noise3(vec3(p.x * 9.0, 5.0, t * 0.8));
    return sheets * (0.15 + 0.85 * rays * rays);
}

vec3 auroraSky(vec3 d, float t, vec3 colA, vec3 colB, float intensity, bool low) {
    vec3 color = mix(vec3(0.02, 0.035, 0.07), vec3(0.003, 0.005, 0.016), smoothstep(0.0, 0.8, d.y));
    float s = stars(d, 60.0, 0.12 * vParams.z, t * 0.7) * 1.3;
    if (!low) s += stars(d, 150.0, 0.2 * vParams.z, t) * 0.6;
    color += vec3(0.9, 0.95, 1.0) * s * vParams.w;
    if (d.y > 0.0) {
        int steps = low ? 14 : 32;
        vec3 avg = vec3(0.0), acc = vec3(0.0);
        for (int i = 0; i < 32; i++) {
            if (i >= steps) break;
            float f = (float(i) + 0.5) / float(steps);
            float h = 1.0 + f * f * 4.0; // denser steps near the sharp bottom edge
            vec2 p = d.xz * (h / max(d.y, 0.03)) * 0.5;
            vec3 c = mix(colA, colB, smoothstep(0.1, 0.8, f)) * auroraField(p, t, low);
            avg = mix(avg, c, 0.5);
            acc += avg * exp(-f * 2.5);
        }
        vec3 glow = acc / float(steps) * 4.5 * intensity * smoothstep(0.0, 0.1, d.y);
        // faint green-ish airglow along the horizon under the curtains
        glow += colA * 0.05 * intensity * exp(-d.y * 9.0) * smoothstep(-0.05, 0.0, d.y);
        color += glow;
    }
    return color;
}

// ---- Clouds: a raymarched cloud layer between heights 1 and 1.6. Each sample is shadowed by
// the cloud toward the sun, scatters forward around the sun, and the far layer fades into haze ----

float cloudDensity(vec3 p, float t, float coverage, int oct) {
    vec3 q = p * 1.5 + vec3(t * 0.02, t * 0.004, t * 0.01);
    float shape = fbmN(q, oct);
    float h = (p.y - 1.0) / 0.6;
    float profile = smoothstep(0.0, 0.2, h) * smoothstep(1.0, 0.4, h); // flat bases, rounded tops
    return clamp((shape - coverage) * 3.5 - (1.0 - profile), 0.0, 1.0);
}

/** rgb = in-scattered cloud light, a = sky transmittance left behind the clouds. */
vec4 marchClouds(vec3 d, vec3 sun, vec3 sunCol, vec3 ambient, vec3 haze, float t, float coverage, bool low) {
    if (d.y < 0.015) return vec4(0.0, 0.0, 0.0, 1.0);
    float t0 = 1.0 / d.y;
    if (t0 > 18.0) return vec4(0.0, 0.0, 0.0, 1.0);
    float t1 = min(1.6 / d.y, t0 + 2.5);
    int steps = low ? 7 : 14;
    float dt = (t1 - t0) / float(steps);
    int oct = low ? 3 : (t0 > 7.0 ? 3 : 4); // fewer octaves far away: no shimmering grain near the horizon
    float mu = dot(d, sun);
    float phase = 0.6 + 1.6 * pow(max(mu, 0.0), 6.0) + 0.25 * (1.0 - mu); // forward scattering + a bit of back
    float trans = 1.0;
    vec3 light = vec3(0.0);
    for (int i = 0; i < 14; i++) {
        if (i >= steps) break;
        vec3 p = d * (t0 + (float(i) + 0.5) * dt);
        float dens = cloudDensity(p, t, coverage, oct);
        if (dens < 0.01) continue;
        float ld = cloudDensity(p + sun * 0.1, t, coverage, oct - 1);
        if (!low) ld += cloudDensity(p + sun * 0.28, t, coverage, oct - 1);
        float sunT = exp(-ld * (low ? 4.0 : 2.6));
        float powder = 1.0 - exp(-dens * 5.0); // dark crevices on the sunlit side
        float hgt = (p.y - 1.0) / 0.6;
        vec3 S = sunCol * sunT * phase * mix(0.55, 1.0, powder) + ambient * (0.45 + 0.55 * hgt);
        float a = 1.0 - exp(-dens * dt * 10.0);
        light += trans * a * S;
        trans *= 1.0 - a;
        if (trans < 0.02) break;
    }
    // aerial perspective: far clouds blend into the horizon haze and thin out
    float far = smoothstep(3.0, 16.0, t0);
    light = mix(light, haze * (1.0 - trans), far * 0.7);
    float fade = smoothstep(18.0, 9.0, t0);
    return vec4(light * fade, mix(1.0, trans, fade));
}

vec3 daySky(vec3 d, float t, float intensity, bool low) {
    vec3 sun = normalize(vec3(0.45, 0.42, -0.6));
    float up = max(d.y, 0.0);
    vec3 horizon = vec3(0.68, 0.8, 0.93);
    vec3 color = mix(horizon, vec3(0.13, 0.33, 0.76), pow(up, 0.5));
    float sd = max(dot(d, sun), 0.0);
    color += vec3(1.0, 0.9, 0.7) * pow(sd, 10.0) * 0.3;
    color += vec3(1.0, 0.97, 0.9) * smoothstep(0.9993, 0.9997, sd) * 4.0;
    vec4 c = marchClouds(d, sun, vec3(1.25, 1.2, 1.1), vec3(0.42, 0.52, 0.68), horizon, t, 0.62 - 0.1 * intensity, low);
    color = color * c.a + c.rgb;
    return mix(color, horizon * 0.8, smoothstep(0.0, -0.3, d.y));
}

vec3 sunsetSky(vec3 d, float t, float intensity, bool low) {
    vec3 sun = normalize(vec3(0.0, 0.035, -1.0));
    float up = max(d.y, 0.0);
    float sd = max(dot(d, sun), 0.0);
    vec3 horizon = mix(vec3(0.95, 0.42, 0.25), vec3(1.0, 0.72, 0.38), pow(sd, 4.0));
    vec3 color = mix(horizon, vec3(0.72, 0.36, 0.45), smoothstep(0.0, 0.25, up));
    color = mix(color, vec3(0.12, 0.12, 0.32), smoothstep(0.2, 0.85, up));
    color += vec3(1.0, 0.6, 0.3) * pow(sd, 12.0) * 0.8;
    color += vec3(1.0, 0.85, 0.6) * smoothstep(0.999, 0.9995, sd) * 3.0;
    color += vec3(1.0) * stars(d, 60.0, 0.1 * vParams.z, t * 0.7) * smoothstep(0.45, 0.95, up) * 0.5 * vParams.w;
    // the low sun lights the undersides; shadowed parts take the purple of the upper sky
    vec4 c = marchClouds(d, normalize(vec3(0.0, 0.25, -1.0)), vec3(1.5, 0.75, 0.4), vec3(0.3, 0.2, 0.32), horizon, t, 0.62 - 0.1 * intensity, low);
    color = color * c.a + c.rgb;
    return mix(color, vec3(0.25, 0.12, 0.12), smoothstep(0.0, -0.3, d.y));
}

// ---- Galaxy core: the milky way's elongated warm bulge, a mottled band of star clouds split by
// dark dust filaments (the "great rift"), pink emission knots and dense star fields ----

vec3 galaxySky(vec3 d, float t, vec3 colA, vec3 colB, float intensity, bool low) {
    vec3 axis = normalize(vec3(0.3, 0.55, 0.78));            // galactic pole
    vec3 core = normalize(cross(vec3(0.0, 0.0, 1.0), axis)); // in the plane, ~30 degrees up
    vec3 side = cross(axis, core);
    float lat = dot(d, axis);
    vec3 inPlane = normalize(d - axis * lat + 1e-5);
    float lon = atan(dot(inPlane, side), dot(inPlane, core)); // -pi..pi, 0 at the core
    int oct = low ? 3 : 5;
    vec3 drift = vec3(t * 0.0015, 0.0, 0.0);

    float width = 0.07 + 0.08 * exp(-lon * lon * 1.5);        // the band swells toward the core
    float band = exp(-pow(lat / width, 2.0));
    float bulge = exp(-(lon * lon / 0.1 + lat * lat / 0.012));
    float clouds = fbmN(d * 9.0 + drift, oct);                // mottled star clouds
    float lum = band * (0.25 + 1.1 * clouds * clouds) + bulge * (1.6 + clouds);
    if (!low) lum *= 0.8 + 0.4 * noise3(d * 120.0);          // grain of unresolved stars

    // dust filaments along the plane, wandering a little off it
    float dn = fbmN(d * 5.0 + 11.0, oct) * 0.65 + fbmN(d * 15.0 + 3.0, low ? 2 : 4) * 0.35;
    float lane = exp(-pow((lat - 0.018 * sin(lon * 3.0) - 0.01) / (width * 0.5), 2.0));
    float dust = smoothstep(0.42, 0.7, dn) * lane;

    vec3 diskCol = mix(colA, vec3(0.8, 0.85, 1.0), 0.6);
    vec3 coreCol = vec3(1.0, 0.78, 0.5);
    vec3 color = vec3(0.005, 0.005, 0.012) + colA * 0.025 * exp(-lat * lat * 5.0); // faint diffuse glow
    color += mix(diskCol, coreCol, smoothstep(0.0, 0.6, bulge + band * exp(-abs(lon)) * 0.4)) * lum * 0.55 * intensity;
    color += colB * pow(clouds, 5.0) * band * 1.6 * intensity * (1.0 - dust);
    color *= 1.0 - dust * 0.9;
    color *= mix(vec3(1.0), vec3(1.0, 0.75, 0.55), dust * 0.6); // dust reddens what it lets through

    float s = stars(d, 60.0, 0.12 * vParams.z, t * 0.7) * 1.3;
    if (!low) s += stars(d, 170.0, 0.3 * vParams.z * mix(0.3, 2.4, band), t) * 0.8;
    color += vec3(1.0, 0.95, 0.9) * s * vParams.w * (1.0 - dust * 0.75);
    return color;
}

void main() {
    vec3 d = normalize(vDir);
    float t = vParams.x;
    float intensity = vParams.y;
    bool low = vLow > 0.5;
    vec3 color;
    if (vStyle == 1) color = auroraSky(d, t, vColorA.rgb, vColorB, intensity, low);
    else if (vStyle == 2) color = daySky(d, t, intensity, low);
    else if (vStyle == 3) color = sunsetSky(d, t, intensity, low);
    else if (vStyle == 4) color = galaxySky(d, t, vColorA.rgb, vColorB, intensity, low);
    else color = nebulaSky(d, t, intensity, low);
    // space skies: slightly darker below the horizon so the world's edge sits on deep space
    if (vStyle == 0 || vStyle == 1 || vStyle == 4) color *= mix(0.55, 1.0, smoothstep(-0.25, 0.05, d.y));
    fragColor = vec4(color, vColorA.a);
}
