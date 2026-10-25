#version 330

// blixuAddons UI shapes.
// Each quad carries, besides position and colour:
//   UV0 = this corner's position relative to the box centre (GUI pixels)
//   UV1 = half the box size * 16          (whole numbers, so ivec2)
//   UV2 = (corner radius * 16, mode * 16) mode > 0: soft edge (shadow/glow) width
//                                         mode < 0: outline thickness
//                                         mode = 0: normal filled shape
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

out vec4 vColor;
out vec2 vLocal;
flat out vec2 vHalf;
flat out vec2 vParams;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vColor = Color;
    vLocal = UV0;
    vHalf = vec2(UV1) / 16.0;
    vParams = vec2(UV2) / 16.0;
}
