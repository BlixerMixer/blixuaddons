package com.blixu.blixuaddons.client.module.impl.player;

import com.blixu.blixuaddons.client.feature.GlowStyle;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.module.setting.SectionSetting;

/**
 * Shader glow around your first-person hand and held item. Drawn by post_effect/hand_glow right
 * after the hand pass: the depth buffer then holds only the hand and item, so their exact silhouette
 * gets the glow band around it and the chosen design on it (see PostFx.handGlow).
 * Same layout as the mob glows: strength sizes the band, Design opacity sets the pattern on the hand.
 */
public class HandAura extends Module {
	public final SectionSetting glowHeader = add(new SectionSetting("glow_header", "Glow around the hand", "Soft band and edge stroke; strength 0 turns the band off"));
	public final ColorSetting color = add(new ColorSetting("glow_color", "Glow colour", 0xD13BFF));
	public final NumberSetting strength = add(new NumberSetting("strength", "Glow strength", 1, 0, 2, 0.05));
	public final BoolSetting ownOuter = add(new BoolSetting("own_outer", "Own outer glow colour", false));
	public final ColorSetting outer = add(new ColorSetting("outer_color", "Outer glow colour", 0x22D3EE).visibleWhen(ownOuter::get));
	public final NumberSetting radius = add(new NumberSetting("radius", "Glow radius (pixels)", 18, 6, 40, 1));

	public final SectionSetting designHeader = add(new SectionSetting("design_header", "Design", "The animated pattern on your hand and item"));
	public final EnumSetting<GlowStyle.Design> style = add(new EnumSetting<>("style", "Design", GlowStyle.Design.NEBULA, s -> s.label));
	public final ColorSetting designA = add(new ColorSetting("design_a", "Design colour", 0xD13BFF));
	public final ColorSetting designB = add(new ColorSetting("design_b", "Design colour 2", 0x22D3EE));
	public final NumberSetting animSpeed = add(new NumberSetting("anim_speed", "Animation speed", 1, 0, 4, 0.05));
	public final NumberSetting designOpacity = add(new NumberSetting("design_opacity", "Design opacity", 0.5, 0, 1, 0.05));

	public HandAura() {
		super("hand_aura", "Hand Aura",
			"A shader glow around your first-person hand and item: Nebula, Plasma, Flame or Galaxy.",
			Category.PLAYER, "aura", false);
	}

	/** HandConfig uniform block (std140, 6 x vec4): Color, Outer, Anim, Params, DesignA, DesignB. */
	public float[] uniforms(boolean lowEnd) {
		int c = color.argb(), o = outer.argb(), a = designA.argb(), b = designB.argb();
		return new float[] {
			r(c), g(c), b(c), 1f,
			r(o), g(o), b(o), ownOuter.get() ? 1f : 0f,
			animSpeed.getF(), 0f, 0f, 0f,
			strength.getF(), designOpacity.getF(), lowEnd ? 1f : 0f, style.get().shader,
			r(a), g(a), b(a), 1f,
			r(b), g(b), b(b), 1f,
		};
	}

	private static float r(int c) { return ((c >> 16) & 255) / 255f; }
	private static float g(int c) { return ((c >> 8) & 255) / 255f; }
	private static float b(int c) { return (c & 255) / 255f; }
}
