#version 330

// blixuAddons: entity silhouettes into the outline buffer (the source of every glow style).
// Same inputs as vanilla; see the .fsh for what changed.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec4 vColor;
out vec2 vUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vColor = Color;
    vUv = UV0;
}
