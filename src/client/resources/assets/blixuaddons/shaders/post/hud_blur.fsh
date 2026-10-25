#version 330

// Separable Gaussian blur for frosted HUD panels. Radius is rewritten every frame from the HUD settings.

uniform sampler2D InSampler;

layout(std140) uniform HudBlurConfig {
    vec4 Dir; // xy = direction, z = radius in pixels
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float radius = max(Dir.z, 0.5);
    float sigma = max(radius * 0.5, 0.5);
    int r = int(ceil(radius));
    vec4 sum = vec4(0.0);
    float total = 0.0;
    // Step 2 texels with bilinear filtering = half the taps for the same reach.
    for (int i = -r; i <= r; i += 2) {
        float x = float(i) + 0.5;
        float w = exp(-(x * x) / (2.0 * sigma * sigma));
        sum += texture(InSampler, texCoord + Dir.xy * texel * x) * w;
        total += w;
    }
    fragColor = vec4((sum / total).rgb, 1.0);
}
