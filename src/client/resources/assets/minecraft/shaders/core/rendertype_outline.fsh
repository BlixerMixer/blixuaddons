#version 330

// blixuAddons: keeps the outline colour's alpha (vanilla forces it to opaque), so each glowing entity
// can carry its own glow opacity into the glow styles. Alpha 0 means "no alpha given" (plain team
// colours) and stays fully opaque, like vanilla.

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vColor;
in vec2 vUv;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, vUv).a == 0.0) {
        discard;
    }
    float alpha = vColor.a > 0.0 ? vColor.a : 1.0;
    fragColor = vec4(ColorModulator.rgb * vColor.rgb, ColorModulator.a * alpha);
}
