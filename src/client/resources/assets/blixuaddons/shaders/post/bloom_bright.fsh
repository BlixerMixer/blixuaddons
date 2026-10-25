#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <blixuaddons:grade_common.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

// Keeps only the bright parts of the image, softly, for bloom.
void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float l = luma(c);
    float k = smoothstep(D.z, D.z + 0.25, l);
    fragColor = vec4(c * k, 1.0);
}
