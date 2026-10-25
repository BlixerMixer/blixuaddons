#version 330

// Frosted-glass panel: the shared blurred copy of the world, clipped to a rounded box
// and tinted with the vertex colour (alpha = how much tint covers the blur).

uniform sampler2D Sampler0;

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

float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + vec2(radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    float radius = min(vParams.x, min(vHalf.x, vHalf.y));
    float d = roundedBox(vLocal, vHalf, radius);
    float px = max(fwidth(d), 0.0001);
    float alpha = clamp(0.5 - d / px, 0.0, 1.0);
    if (alpha <= 0.002) {
        discard;
    }
    vec3 blurred = texture(Sampler0, gl_FragCoord.xy / vec2(textureSize(Sampler0, 0))).rgb;
    vec4 tint = vColor * ColorModulator;
    // a touch of frost: lift the blurred image slightly so text on it stays readable
    vec3 frosted = mix(blurred, vec3(1.0), 0.04);
    fragColor = vec4(mix(frosted, tint.rgb, tint.a), alpha);
}
