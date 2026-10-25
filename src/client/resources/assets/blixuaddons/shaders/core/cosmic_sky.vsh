#version 330

// Cosmic Sky: a sphere around the camera; the fragment shader paints space from the view direction.
// Settings ride on the vertex attributes (same for every vertex):
//   Color = nebula colour A (rgb), overall opacity (a)
//   UV0   = (animation time in seconds, style * 4 + intensity)
//   UV1   = nebula colour B red, green (0..255)
//   UV2   = (colour B blue, star density * 100 + star brightness * 100 * 128)

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
in vec3 Normal;

out vec3 vDir;
flat out vec4 vColorA;
flat out vec3 vColorB;
flat out vec4 vParams; // time, intensity, star density, star brightness
flat out float vLow;    // 1 = Low-End Mode
flat out int vStyle;    // 0 nebula, 1 aurora, 2 clouds, 3 sunset, 4 galaxy core

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vDir = Position;
    vColorA = Color;
    vColorB = vec3(float(UV1.x), float(UV1.y), float(UV2.x)) / 255.0;
    float packed = float(UV2.y);
    vLow = Normal.x > 0.5 ? 1.0 : 0.0;
    vStyle = int(floor(UV0.y / 4.0));
    vParams = vec4(UV0.x, mod(UV0.y, 4.0), mod(packed, 128.0) / 100.0, floor(packed / 128.0) / 100.0);
}
