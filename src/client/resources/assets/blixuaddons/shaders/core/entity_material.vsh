#version 330

// Self Aura / Hit Color body materials (fills, tints, holographic, shells) drawn on entity models.
// Same inputs as Minecraft's entity passes so the model, lightmap and hurt overlay line up.

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vFront;
out vec4 vBack;
out vec4 vLight;
out vec4 vOverlay;
out vec2 vUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    // Shade both faces: translucent fills show their inside, which should be lit from the other side.
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, Normal);
    vFront = minecraft_mix_light_separate(light, Color);
    vBack = minecraft_mix_light_separate(-light, Color);
#ifdef EMISSIVE
    vLight = vec4(1.0);
#else
    vLight = sample_lightmap(Sampler2, UV2);
#endif
    vOverlay = texelFetch(Sampler1, UV1, 0);
    vUv = UV0;
}
