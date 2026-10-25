#version 330

// The aura band around every silhouette. Its width is a fixed size IN THE WORLD (PxPerBlock / distance
// pixels, from the depth buffer), so it shrinks with distance exactly like the object does. Alpha falls
// from 1 at the object's edge to 0 at its radius, measured to the NEAREST object, so the band never
// grows when objects cluster.
// Output rg = offset to that nearest object pixel (/ Radius, 0.5 = none), so the final pass can read
// the object's own colour, opacity and module id from the mask. PxPerBlock = 0: fixed Radius (the hand).

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform DistConfig {
    float Radius;      // largest radius in pixels (also the search reach)
    float PxPerBlock;  // aura size in blocks * focal length in pixels; 0 = fixed Radius
    float Near;        // camera planes, to turn reverse-Z depth into distance
    float Far;
};
layout(std140) uniform GlowConfig {
    vec4 FillA[16];
    vec4 Outer[16];
    vec4 Look[16];  // z = this module's radius scale
    vec4 Global;   // y = Low-End Mode
    vec4 FillB[16];
};

in vec2 texCoord;
out vec4 fragColor;

/** Radius in pixels for the object drawn at uv. */
float radiusAt(vec2 uv) {
    if (PxPerBlock <= 0.0) return Radius;
    float d = texture(DepthSampler, uv).r;
    if (d <= 0.0) return Radius * 0.5; // nothing in the depth buffer there: a middle size
    float z = Near * Far / (Near + d * (Far - Near)); // reverse-Z depth -> distance
    return clamp(PxPerBlock / max(z, 0.5), 2.5, Radius);
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    if (texture(InSampler, texCoord).a > 0.0) {
        fragColor = vec4(0.5, 0.5, 0.0, 1.0);
        return;
    }
    float best = 0.0;
    vec2 bestOff = vec2(0.0);
    bool low = Global.y > 0.5;
    int rings = low ? 4 : 8;
    int dirs = low ? 10 : 16;
    // per-pixel jitter turns the ring steps into fine noise, which the blur after this smooths away
    float jitter = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    for (int ring = 1; ring <= 8; ring++) {
        if (ring > rings) break;
        // quadratic spacing: fine steps near the silhouette (small, far objects), long reach for near ones
        float f = (float(ring) - 0.5 + jitter) / float(rings);
        float r = Radius * f * f;
        for (int i = 0; i < 16; i++) {
            if (i >= dirs) break;
            float ang = 6.2831853 * (float(i) + jitter + 0.5 * float(ring & 1)) / float(dirs);
            vec2 off = vec2(cos(ang), sin(ang)) * r;
            vec2 uv = texCoord + off * texel;
            float ma = texture(InSampler, uv).a;
            if (ma <= 0.0) continue;
            // each module sizes its own aura (the id sits in the low 4 bits of the outline alpha)
            // (the hand glow, PxPerBlock = 0, has a single look and a fixed radius)
            float zs = PxPerBlock <= 0.0 ? 1.0 : Look[int(ma * 255.0 + 0.5) & 15].z;
            float v = 1.0 - r / (radiusAt(uv) * max(zs, 0.05));
            if (v > best) {
                best = v;
                bestOff = off;
            }
        }
    }
    fragColor = vec4(bestOff / Radius * 0.5 + 0.5, 0.0, best);
}
