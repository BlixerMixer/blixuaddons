#version 330

// The icon sheets are 64 px drawn at ~9 px, so a single texture tap shimmers and looks jagged.
// Four taps spread over this pixel's footprint (rotated grid) average it like 4x supersampling.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec2 dx = dFdx(vUv), dy = dFdy(vUv);
    vec4 c = texture(Sampler0, vUv + dx * 0.125 + dy * 0.375)
           + texture(Sampler0, vUv - dx * 0.375 + dy * 0.125)
           + texture(Sampler0, vUv + dx * 0.375 - dy * 0.125)
           + texture(Sampler0, vUv - dx * 0.125 - dy * 0.375);
    vec4 color = c * 0.25 * vColor;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
