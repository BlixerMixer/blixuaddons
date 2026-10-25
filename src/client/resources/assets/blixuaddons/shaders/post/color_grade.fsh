#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <blixuaddons:grade_common.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 c = texture(InSampler, texCoord);
    fragColor = vec4(grade(c.rgb, texCoord), 1.0);
}
