#version 330

// Hand Aura: a glow around your first-person hand and item, composited over the scene.
// Styles (Params.w) are the shared designs in glow_common: 1 Nebula, 2 Plasma, 3 Flame, 4 Galaxy.

#moj_import <minecraft:globals.glsl>
#moj_import <blixuaddons:glow_common.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D EdgeSampler;
uniform sampler2D AuraSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform HandConfig {
    vec4 Color;   // rgb, w = opacity
    vec4 Outer;
    vec4 Anim;    // x = animation speed
    vec4 Params;  // x strength (the band), y design opacity (on the hand), z low-end, w style
    vec4 DesignA; // the design's two colours (on the hand and item)
    vec4 DesignB;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec2 size = vec2(textureSize(MaskSampler, 0));
    vec2 px = texCoord * size;
    int style = int(Params.w + 0.5);
    bool low = Params.z > 0.5;
    float t = glowSeconds() * Anim.x;
    float k = Params.x;
    float body = texture(MaskSampler, texCoord).a;
    float edge = texture(EdgeSampler, texCoord).a;
    float aura = texture(AuraSampler, texCoord).a;

    float flameN = 0.0;
    if (style == 3) {
        flameN = flameNoise(px, t, low);
        aura = max(aura * 0.6, texture(AuraSampler, texCoord - vec2(0.0, flameN * 30.0 * k / size.y)).a * (0.3 + flameN));
    }
    if (aura < 0.002 && edge < 0.002 && body < 0.002) {
        fragColor = vec4(scene.rgb, 1.0);
        return;
    }

    vec3 base = Color.rgb;
    vec3 outerTint = Outer.w > 0.5 ? Outer.rgb : base * 0.4;
    // the band in the glow colours, the hand and item in the design colours
    bool onHand = body > 0.5;
    vec4 result = paintDesign(style, px, t, clamp(aura, 0.0, 1.0), aura, onHand ? 1.0 : 0.0,
        onHand ? DesignA.rgb : base, onHand ? DesignB.rgb : outerTint, k, Params.y, low, flameN);
    result = over(result, vec4(mix(base, vec3(1.0), 0.65), clamp(edge * 1.3, 0.0, 1.0)));
    fragColor = vec4(mix(scene.rgb, result.rgb, result.a), 1.0);
}
