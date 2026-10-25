// Colour grading maths shared by color_grade(.fsh) and color_grade_bloom(.fsh).
// Needs <minecraft:globals.glsl> imported first.

layout(std140) uniform GradeConfig {
    vec4 A; // exposure (EV), contrast, saturation, gamma
    vec4 B; // temperature (-1..1), tint (-1..1), shadows (-1..1), highlights (-1..1)
    vec4 C; // vignette, grain, bloom, strength (0..1 mix with the ungraded image)
    vec4 D; // brightness (-1..1), monochrome (0/1), bloom threshold, unused
};

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float gradeHash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 grade(vec3 c, vec2 uv) {
    vec3 src = c;
    c *= exp2(A.x);
    c += D.x;
    // temperature: warm = more red, less blue; tint: + = magenta, - = green
    c *= vec3(1.0 + B.x * 0.18, 1.0 - B.y * 0.12, 1.0 - B.x * 0.18);
    c.rb *= 1.0 + B.y * 0.06;
    float l = luma(c);
    // shadows / highlights lift or crush the ends of the range
    float sh = 1.0 - smoothstep(0.0, 0.5, l);
    float hi = smoothstep(0.5, 1.0, l);
    c += c * B.z * sh * 0.8 + B.w * hi * 0.35;
    c = (c - 0.5) * A.y + 0.5;
    l = luma(c);
    c = mix(vec3(l), c, D.y > 0.5 ? 0.0 : A.z);
    c = pow(max(c, 0.0), vec3(1.0 / max(A.w, 0.05)));
    if (C.x > 0.0) {
        float d = distance(uv, vec2(0.5));
        c *= 1.0 - smoothstep(0.35, 0.85, d) * C.x;
    }
    if (C.y > 0.0) {
        float n = gradeHash(uv * ScreenSize + fract(GameTime * 1200.0) * 913.0) - 0.5;
        c += n * C.y * 0.12;
    }
    return mix(src, clamp(c, 0.0, 1.0), C.w);
}
