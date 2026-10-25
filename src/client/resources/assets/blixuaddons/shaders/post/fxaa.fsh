#version 330

// FXAA (luma-based, in the spirit of Timothy Lottes' FXAA 3.11 "quality" preset, simplified):
// finds high-contrast edges, works out their direction, and blends along them, smoothing jagged
// block edges, lines, outlines and cosmetics in one full-screen pass.

uniform sampler2D InSampler;

layout(std140) uniform FxaaConfig {
    float Subpixel;       // 0..1 how much sub-pixel aliasing (single-pixel sparkle) to remove
    float EdgeThreshold;  // minimum local contrast to treat as an edge (0.063 .. 0.333)
    float EdgeMin;        // ignore very dark regions below this contrast
    float Spread;         // how far (in pixels) to search along an edge
};

in vec2 texCoord;
out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 px = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 rgbM = texture(InSampler, texCoord).rgb;
    float lM = luma(rgbM);
    float lN = luma(texture(InSampler, texCoord + vec2(0.0, -px.y)).rgb);
    float lS = luma(texture(InSampler, texCoord + vec2(0.0, px.y)).rgb);
    float lE = luma(texture(InSampler, texCoord + vec2(px.x, 0.0)).rgb);
    float lW = luma(texture(InSampler, texCoord + vec2(-px.x, 0.0)).rgb);

    float lMax = max(lM, max(max(lN, lS), max(lE, lW)));
    float lMin = min(lM, min(min(lN, lS), min(lE, lW)));
    float range = lMax - lMin;
    if (range < max(EdgeMin, lMax * EdgeThreshold)) {
        fragColor = vec4(rgbM, 1.0);
        return;
    }

    float lNW = luma(texture(InSampler, texCoord + vec2(-px.x, -px.y)).rgb);
    float lNE = luma(texture(InSampler, texCoord + vec2(px.x, -px.y)).rgb);
    float lSW = luma(texture(InSampler, texCoord + vec2(-px.x, px.y)).rgb);
    float lSE = luma(texture(InSampler, texCoord + vec2(px.x, px.y)).rgb);

    // Sub-pixel blend amount from the 3x3 neighbourhood average.
    float avg = (2.0 * (lN + lS + lE + lW) + lNW + lNE + lSW + lSE) / 12.0;
    float sub = clamp(abs(avg - lM) / range, 0.0, 1.0);
    sub = smoothstep(0.0, 1.0, sub);
    float subBlend = sub * sub * Subpixel;

    // Edge orientation.
    float edgeH = abs(lNW + lNE - 2.0 * lN) + 2.0 * abs(lW + lE - 2.0 * lM) + abs(lSW + lSE - 2.0 * lS);
    float edgeV = abs(lNW + lSW - 2.0 * lW) + 2.0 * abs(lN + lS - 2.0 * lM) + abs(lNE + lSE - 2.0 * lE);
    bool horizontal = edgeH >= edgeV;

    float l1 = horizontal ? lN : lW;
    float l2 = horizontal ? lS : lE;
    float g1 = abs(l1 - lM), g2 = abs(l2 - lM);
    bool steep1 = g1 >= g2;
    float gradScaled = 0.25 * max(g1, g2);
    float stepLen = horizontal ? px.y : px.x;
    float lLocal = steep1 ? 0.5 * (l1 + lM) : 0.5 * (l2 + lM);
    if (steep1) stepLen = -stepLen;

    vec2 uv = texCoord;
    if (horizontal) uv.y += stepLen * 0.5; else uv.x += stepLen * 0.5;
    vec2 off = horizontal ? vec2(px.x, 0.0) : vec2(0.0, px.y);

    // Walk both ways along the edge until the contrast ends.
    vec2 uv1 = uv - off, uv2 = uv + off;
    float e1 = luma(texture(InSampler, uv1).rgb) - lLocal;
    float e2 = luma(texture(InSampler, uv2).rgb) - lLocal;
    bool done1 = abs(e1) >= gradScaled, done2 = abs(e2) >= gradScaled;
    int steps = int(clamp(Spread, 2.0, 16.0));
    for (int i = 0; i < 16; i++) {
        if (i >= steps || (done1 && done2)) break;
        float q = i < 3 ? 1.0 : 1.5;
        if (!done1) { uv1 -= off * q; e1 = luma(texture(InSampler, uv1).rgb) - lLocal; done1 = abs(e1) >= gradScaled; }
        if (!done2) { uv2 += off * q; e2 = luma(texture(InSampler, uv2).rgb) - lLocal; done2 = abs(e2) >= gradScaled; }
    }

    float d1 = horizontal ? texCoord.x - uv1.x : texCoord.y - uv1.y;
    float d2 = horizontal ? uv2.x - texCoord.x : uv2.y - texCoord.y;
    bool closer1 = d1 < d2;
    float dMin = min(d1, d2);
    float span = d1 + d2;
    bool correctVariation = ((closer1 ? e1 : e2) < 0.0) != (lM - lLocal < 0.0);
    float edgeBlend = correctVariation ? 0.5 - dMin / span : 0.0;

    float blend = max(edgeBlend, subBlend);
    vec2 finalUv = texCoord;
    if (horizontal) finalUv.y += blend * stepLen; else finalUv.x += blend * stepLen;
    fragColor = vec4(texture(InSampler, finalUv).rgb, 1.0);
}
