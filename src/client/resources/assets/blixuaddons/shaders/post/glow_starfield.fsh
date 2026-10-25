#version 330

// The aura glow for everything in the outline buffer. Each object's look comes from its module: the
// outline alpha holds opacity (high 4 bits) and look id (low 4 bits). Per module: strength, how much
// lies over the object (fill), outer colour, radius, a design for the band around the object and one
// for the fill on it (glow_common paintDesign), the fill's own two colours and its animation speed.
// A thin pale edge stroke goes on top.

#moj_import <minecraft:globals.glsl>
#moj_import <blixuaddons:glow_common.glsl>

uniform sampler2D EdgeSampler;
uniform sampler2D AuraSampler;
uniform sampler2D MaskSampler;
uniform sampler2D DistSampler;

layout(std140) uniform DistConfig {
    float Radius;
    float PxPerBlock;
    float Near;
    float Far;
};
layout(std140) uniform GlowConfig {
    vec4 FillA[16];  // rgb = fill main colour, w = animation speed
    vec4 Outer[16];  // rgb, w = use it
    vec4 Look[16];   // x = strength, y = fill kept on the object, z = radius scale, w = band design + fill design * 8
    vec4 Global;    // y = Low-End Mode
    vec4 FillB[16];  // rgb = fill second colour, w = 1 when the module has its own fill colours
};

in vec2 texCoord;
out vec4 fragColor;

/** The object at uv, or 1px around it (offsets are stored at 8 bits, so thin limbs can be missed by a pixel). */
vec4 objectNear(vec2 uv, vec2 texel) {
    vec4 o = texture(MaskSampler, uv);
    if (o.a > 0.0) return o;
    for (int i = 0; i < 4; i++) {
        vec2 d = i == 0 ? vec2(1, 0) : i == 1 ? vec2(-1, 0) : i == 2 ? vec2(0, 1) : vec2(0, -1);
        o = texture(MaskSampler, uv + d * texel);
        if (o.a > 0.0) return o;
    }
    return vec4(0.0);
}

void main() {
    vec2 size = vec2(textureSize(MaskSampler, 0));
    vec2 texel = 1.0 / size;
    vec2 px = texCoord * size;
    vec4 edgeS = texture(EdgeSampler, texCoord);
    float aura = texture(AuraSampler, texCoord).a;
    vec4 here = texture(MaskSampler, texCoord);
    float edge = edgeS.a;
    float body = here.a > 0.0 ? 1.0 : 0.0;
    if (aura < 0.002 && edge < 0.002 && body < 0.5) {
        fragColor = vec4(0.0);
        return;
    }
    // Which object this pixel belongs to. Read from the UN-blurred distance pass: a blurred offset
    // averages two limbs into empty space between them (that cut the band into dark patches).
    // In the blur's soft tail just past the band, borrow the answer of a neighbour inside it.
    vec4 obj = here;
    if (body < 0.5) {
        vec4 d = texture(DistSampler, texCoord);
        obj = d.a > 0.0 ? objectNear(texCoord + (d.rg - 0.5) * 2.0 * Radius * texel, texel) : vec4(0.0);
        for (int i = 0; i < 8 && obj.a <= 0.0; i++) {
            float ang = 0.7853982 * float(i);
            vec2 nuv = texCoord + vec2(cos(ang), sin(ang)) * 6.0 * texel;
            vec4 nd = texture(DistSampler, nuv);
            if (nd.a > 0.0) obj = objectNear(nuv + (nd.rg - 0.5) * 2.0 * Radius * texel, texel);
        }
        if (obj.a <= 0.0) {
            fragColor = vec4(0.0);
            return;
        }
    }
    int a = int(obj.a * 255.0 + 0.5);
    int id = a & 15;
    float opacity = float(a >> 4) / 15.0;
    vec3 base = obj.rgb;
    float k = Look[id].x;
    float tint = Look[id].y;
    int designs = int(Look[id].w + 0.5);
    int style = body > 0.5 ? (designs >> 3) & 7 : designs & 7;
    bool low = Global.y > 0.5;
    float t = glowSeconds() * FillA[id].w;
    vec3 outerTint = Outer[id].w > 0.5 ? Outer[id].rgb : base * 0.4;
    // the fill uses its own two colours when the module has them; the band uses the glow colours
    bool ownFill = body > 0.5 && FillB[id].w > 0.5;
    vec3 colA = ownFill ? FillA[id].rgb : base;
    vec3 colB = ownFill ? FillB[id].rgb : outerTint;
    float flameN = style == 3 ? flameNoise(px, t, low) : 0.0;
    float halo = body > 0.5 ? 1.0 : clamp(aura, 0.0, 1.0);

    vec4 result = paintDesign(style, px, t, halo, aura, body, colA, colB, k, tint, low, flameN);
    result.a *= opacity;
    result = over(result, vec4(mix(base, vec3(1.0), 0.65), clamp(edge * 1.3, 0.0, 1.0) * opacity));
    fragColor = result;
}
