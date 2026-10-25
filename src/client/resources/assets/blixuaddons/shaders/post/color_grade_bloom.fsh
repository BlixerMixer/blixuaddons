#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <blixuaddons:grade_common.glsl>

uniform sampler2D InSampler;
uniform sampler2D BloomSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb + texture(BloomSampler, texCoord).rgb * C.z;
    fragColor = vec4(grade(c, texCoord), 1.0);
}
