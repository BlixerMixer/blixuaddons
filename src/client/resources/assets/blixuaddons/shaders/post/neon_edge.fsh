#version 330

// Finds the silhouette edge of glowing mobs and keeps their outline colour.
// Thickness = how many pixels out from the body we look for the edge.

uniform sampler2D InSampler;

layout(std140) uniform NeonEdge {
    float Thickness;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    vec4 center = texture(InSampler, texCoord);

    float edge = 0.0;
    vec3 color = center.rgb * center.a;
    float weight = center.a;

    for (int i = 0; i < 12; i++) {
        float angle = 6.2831853 * float(i) / 12.0;
        vec2 offset = vec2(cos(angle), sin(angle)) * texel * Thickness;
        vec4 n = texture(InSampler, texCoord + offset);
        edge = max(edge, abs(center.a - n.a));
        color += n.rgb * n.a;
        weight += n.a;
    }

    vec3 outColor = weight > 0.0 ? color / weight : vec3(0.0);
    fragColor = vec4(outColor, edge);
}
