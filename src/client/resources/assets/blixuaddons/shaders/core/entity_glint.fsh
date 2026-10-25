#version 330

// Procedural enchant glint: two layers of soft diagonal bands scrolling at different angles and speeds
// (like vanilla's two glint layers), in a lilac tint, added on top of the model. No texture needed.

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

float bands(vec2 uv, float angle, float scale, float speed, float t) {
    vec2 dir = vec2(cos(angle), sin(angle));
    float d = fract(dot(uv, dir) * scale - t * speed);
    // two soft stripes per repeat
    float a = smoothstep(0.0, 0.08, d) * (1.0 - smoothstep(0.08, 0.22, d));
    float b = smoothstep(0.5, 0.56, d) * (1.0 - smoothstep(0.56, 0.66, d));
    return a + b * 0.6;
}

void main() {
    float t = GameTime * 1200.0;
    float g = bands(vUv, -0.87, 3.0, 0.33, t) + bands(vUv, 0.17, 2.2, 0.21, t) * 0.8;
    vec3 tint = vec3(0.65, 0.5, 1.0) * vColor.rgb;
    vec3 c = tint * g * vColor.a * ColorModulator.a;
    if (dot(c, vec3(1.0)) < 0.003) {
        discard;
    }
    fragColor = vec4(c, 1.0);
}
