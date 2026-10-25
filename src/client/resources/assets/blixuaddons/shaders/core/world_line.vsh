#version 330

// Screen-space thick lines with an anti-aliased edge. Each line endpoint arrives twice (even/odd
// vertex id = the two sides); we push them apart perpendicular to the line on screen, one extra
// pixel wide so the fragment shader can fade that last pixel out smoothly.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vColor;
out float vSide;
flat out float vWidth;

// Pull lines a hair towards the camera so they win the depth test against the faces they outline.
const float SHRINK = 1.0 - 1.0 / 256.0;

void main() {
    mat4 view = mat4(SHRINK, 0.0, 0.0, 0.0, 0.0, SHRINK, 0.0, 0.0, 0.0, 0.0, SHRINK, 0.0, 0.0, 0.0, 0.0, 1.0) * ModelViewMat;
    vec4 a = ProjMat * view * vec4(Position, 1.0);
    vec4 b = ProjMat * view * vec4(Position + Normal, 1.0);
    vec3 ndcA = a.xyz / a.w;
    vec3 ndcB = b.xyz / b.w;
    vec2 dir = normalize((ndcB.xy - ndcA.xy) * ScreenSize);
    float width = LineWidth + 1.0;
    vec2 offset = vec2(-dir.y, dir.x) * width / ScreenSize;
    if (offset.x < 0.0) offset = -offset;
    float side = (gl_VertexID & 1) == 0 ? 1.0 : -1.0;
    gl_Position = vec4((ndcA + vec3(offset * side, 0.0)) * a.w, a.w);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vColor = Color;
    vSide = side;
    vWidth = width;
}
