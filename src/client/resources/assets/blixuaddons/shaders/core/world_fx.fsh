#version 330

// World overlay batches. Long soft gradients (glow billboards, auras, fills) band visibly in 8-bit
// colour; a 4x4 ordered dither of +-half a step hides that. The outline mask (NO_DITHER) keeps exact colours.

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vColor;
out vec4 fragColor;

const float BAYER[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

void main() {
    vec4 color = vColor * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }
#ifndef NO_DITHER
    ivec2 p = ivec2(gl_FragCoord.xy) & 3;
    color.rgb += (BAYER[p.x + p.y * 4] / 16.0 - 0.5) / 255.0;
#endif
    fragColor = color;
}
