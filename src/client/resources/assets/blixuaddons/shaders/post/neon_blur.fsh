#version 330

// One direction of a Gaussian blur. Run twice (horizontal + vertical).
// Colour is blurred premultiplied by alpha so the glow keeps its colour
// as it fades out instead of turning grey.

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float sigma = max(Radius * 0.5, 0.5);
    int r = int(ceil(Radius));

    vec4 sum = vec4(0.0);
    float total = 0.0;
    // centre tap, then pairs of texels folded into one bilinear sample each: half the taps
    vec4 c0 = texture(InSampler, texCoord);
    sum += vec4(c0.rgb * c0.a, c0.a);
    total += 1.0;
    for (int i = 1; i <= r; i += 2) {
        float w1 = exp(-float(i * i) / (2.0 * sigma * sigma));
        float w2 = exp(-float((i + 1) * (i + 1)) / (2.0 * sigma * sigma));
        float w = w1 + w2;
        float o = (float(i) * w1 + float(i + 1) * w2) / w;
        vec4 a = texture(InSampler, texCoord + BlurDir * texel * o);
        vec4 b = texture(InSampler, texCoord - BlurDir * texel * o);
        sum += (vec4(a.rgb * a.a, a.a) + vec4(b.rgb * b.a, b.a)) * w;
        total += 2.0 * w;
    }
    sum /= total;

    vec3 rgb = sum.a > 0.0001 ? sum.rgb / sum.a : vec3(0.0);
    fragColor = vec4(rgb, sum.a);
}
