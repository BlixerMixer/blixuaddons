#version 330

// Self Aura enchant glint: same entity vertex inputs, only position and UVs are needed.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 vUv;
out vec4 vColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUv = UV0;
    vColor = Color;
}
