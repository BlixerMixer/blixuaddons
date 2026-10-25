#version 330

// Copies one target into another (tinted by ColorModulate); used to move results between post targets.

uniform sampler2D InSampler;

layout(std140) uniform BlitConfig {
    vec4 ColorModulate;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(InSampler, texCoord) * ColorModulate;
}
