#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vColor;
in vec2 vLocal;
flat in vec2 vHalf;
flat in vec2 vParams;

out vec4 fragColor;

// Signed distance to a rounded box: < 0 inside, 0 on the edge, > 0 outside.
float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + vec2(radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    float radius = min(vParams.x, min(vHalf.x, vHalf.y));
    float mode = vParams.y;
    float d = roundedBox(vLocal, vHalf, radius);

    // Width of one screen pixel in GUI units, so edges stay crisp at any GUI scale.
    float px = max(fwidth(d), 0.0001);

    float alpha;
    if (mode > 0.0) {
        // Soft shadow / glow: fades out over `mode` pixels.
        float t = clamp(1.0 - (d + mode * 0.25) / mode, 0.0, 1.0);
        alpha = t * t * (3.0 - 2.0 * t);
    } else if (mode < 0.0) {
        // Outline: a ring of thickness -mode just inside the edge.
        float thickness = -mode;
        float ring = abs(d + thickness * 0.5) - thickness * 0.5;
        alpha = clamp(0.5 - ring / px, 0.0, 1.0);
    } else {
        // Filled shape with a 1px anti-aliased edge.
        alpha = clamp(0.5 - d / px, 0.0, 1.0);
    }

    vec4 color = vColor * ColorModulator;
    color.a *= alpha;
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color;
}
