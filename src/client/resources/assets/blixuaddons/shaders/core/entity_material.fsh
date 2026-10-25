#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vFront;
in vec4 vBack;
in vec4 vLight;
in vec4 vOverlay;
in vec2 vUv;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, vUv);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    color *= (gl_FrontFacing ? vFront : vBack) * ColorModulator;
    color.rgb = mix(vOverlay.rgb, color.rgb, vOverlay.a);
    color *= vLight;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
