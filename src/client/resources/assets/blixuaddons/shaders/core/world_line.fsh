#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vColor;
in float vSide;
flat in float vWidth;

out vec4 fragColor;

void main() {
    // Distance from the line's centre in pixels; full coverage inside, fading over the outer pixel.
    float d = abs(vSide) * vWidth * 0.5;
    float coverage = clamp(vWidth * 0.5 - d, 0.0, 1.0);
    vec4 color = vColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
