#version 330

// Hand Aura mask: right after the first-person hand pass the depth buffer only holds the hand and the
// held item (the game clears it just before), so "has depth" is exactly their silhouette.

uniform sampler2D DepthSampler;

layout(std140) uniform HandConfig {
    vec4 Color;   // glow colour
    vec4 Outer;   // rgb + w: 1 = own outer colour
    vec4 Anim;    // x = animation speed
    vec4 Params;  // x strength, y design opacity, z low-end, w style
    vec4 DesignA;
    vec4 DesignB;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float d = texture(DepthSampler, texCoord).r;
    fragColor = d > 0.0 ? vec4(Color.rgb, 1.0) : vec4(0.0);
}
