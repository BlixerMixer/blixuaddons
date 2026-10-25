package com.blixu.blixuaddons.client.module.impl.world;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.module.ModuleManager;

/**
 * Post-process grading of the world image (shaders/post/color_grade*.fsh). Applied before
 * the GUI is drawn, so the HUD, text and menus are never graded.
 */
public class ColorGrading extends Module {
	public enum Preset {
		NATURAL("Natural"), CINEMATIC("Cinematic"), COOL("Cool"), WARM("Warm"), HIGH_CONTRAST("High Contrast"),
		MUTED("Muted"), VIBRANT("Vibrant"), MONOCHROME("Monochrome"), CUSTOM("Custom");
		public final String label;
		Preset(String l) { label = l; }
	}

	public final EnumSetting<Preset> preset = add(new EnumSetting<>("preset", "Preset", Preset.NATURAL, p -> p.label));
	public final NumberSetting strength = add(new NumberSetting("strength", "Effect strength", 1, 0, 1, 0.05));
	public final NumberSetting brightness = add(new NumberSetting("brightness", "Brightness", 0, -0.5, 0.5, 0.01));
	public final NumberSetting contrast = add(new NumberSetting("contrast", "Contrast", 1, 0.5, 2, 0.01));
	public final NumberSetting saturation = add(new NumberSetting("saturation", "Saturation", 1, 0, 2, 0.01));
	public final NumberSetting exposure = add(new NumberSetting("exposure", "Exposure (EV)", 0, -2, 2, 0.05));
	public final NumberSetting gamma = add(new NumberSetting("gamma", "Gamma", 1, 0.4, 2.5, 0.01));
	public final NumberSetting temperature = add(new NumberSetting("temperature", "Temperature", 0, -1, 1, 0.01));
	public final NumberSetting tint = add(new NumberSetting("tint", "Tint (green ↔ magenta)", 0, -1, 1, 0.01));
	public final NumberSetting shadows = add(new NumberSetting("shadows", "Shadows", 0, -1, 1, 0.01));
	public final NumberSetting highlights = add(new NumberSetting("highlights", "Highlights", 0, -1, 1, 0.01));
	public final BoolSetting monochrome = add(new BoolSetting("monochrome", "Monochrome", false));
	public final NumberSetting vignette = add(new NumberSetting("vignette", "Vignette", 0, 0, 1, 0.01));
	public final NumberSetting grain = add(new NumberSetting("grain", "Film grain", 0, 0, 1, 0.01));
	public final BoolSetting bloom = add(new BoolSetting("bloom", "Bloom", false));
	public final NumberSetting bloomStrength = add(new NumberSetting("bloom_strength", "Bloom strength", 0.6, 0, 2, 0.05).visibleWhen(bloom::get));
	public final NumberSetting bloomThreshold = add(new NumberSetting("bloom_threshold", "Bloom threshold", 0.7, 0.2, 1, 0.01).visibleWhen(bloom::get));

	private final float[] uniforms = new float[16];

	public ColorGrading() {
		super("color_grading", "Color Grading",
			"Contrast, saturation, temperature, vignette, grain and bloom for the world (never the HUD).",
			Category.WORLD, "world", false);
		preset.onChange(this::applyPreset);
		// Touching any slider makes it a custom grade.
		for (NumberSetting n : new NumberSetting[] {brightness, contrast, saturation, exposure, gamma, temperature, tint, shadows, highlights, vignette, grain}) {
			n.onChange(v -> preset.setSilently(Preset.CUSTOM));
		}
	}

	private void applyPreset(Preset p) {
		//                    bri  con   sat   exp   gam  temp  tint  shad  high  vig  grain mono
		float[] v = switch (p) {
			case NATURAL ->       new float[] {0, 1.0f, 1.0f, 0, 1.0f, 0, 0, 0, 0, 0, 0, 0};
			case CINEMATIC ->     new float[] {0, 1.12f, 0.88f, -0.1f, 1.0f, 0.12f, 0.03f, -0.15f, 0.1f, 0.35f, 0.12f, 0};
			case COOL ->          new float[] {0, 1.05f, 0.95f, 0, 1.0f, -0.45f, 0, 0, 0.05f, 0.1f, 0, 0};
			case WARM ->          new float[] {0.02f, 1.04f, 1.05f, 0, 1.0f, 0.45f, 0.05f, 0.05f, 0, 0.1f, 0, 0};
			case HIGH_CONTRAST -> new float[] {0, 1.45f, 1.1f, 0, 1.0f, 0, 0, -0.3f, 0.2f, 0.15f, 0, 0};
			case MUTED ->         new float[] {0, 0.9f, 0.6f, 0, 1.05f, 0.05f, 0, 0.1f, -0.1f, 0.1f, 0.05f, 0};
			case VIBRANT ->       new float[] {0.02f, 1.12f, 1.5f, 0.05f, 1.0f, 0.05f, 0, 0, 0.05f, 0, 0, 0};
			case MONOCHROME ->    new float[] {0, 1.2f, 0, 0, 1.0f, 0, 0, -0.1f, 0.1f, 0.3f, 0.15f, 1};
			case CUSTOM -> null;
		};
		if (v == null) return;
		NumberSetting[] targets = {brightness, contrast, saturation, exposure, gamma, temperature, tint, shadows, highlights, vignette, grain};
		for (int i = 0; i < targets.length; i++) targets[i].setSilently((double) v[i]);
		monochrome.setSilently(v[11] > 0.5f);
		ConfigManager.save();
	}

	private static final float[] NEUTRAL = new float[16];

	/** Grade that only applies Fullbright's exposure (used when this module is off). */
	public static float[] exposureOnly(Fullbright fb) {
		float[] u = NEUTRAL;
		java.util.Arrays.fill(u, 0f);
		u[0] = fb.exposureEv();
		u[1] = fb.contrastComp();
		u[2] = fb.saturationComp();
		u[3] = 1f;
		u[11] = 1f;
		u[14] = 1f;
		return u;
	}

	/** The 4 × vec4 GradeConfig block (see shaders/include/grade_common.glsl). */
	public float[] uniforms() {
		Fullbright fb = ModuleManager.active(Fullbright.class);
		float[] u = uniforms;
		u[0] = exposure.getF() + (fb != null ? fb.exposureEv() : 0f);
		u[1] = contrast.getF() * (fb != null ? fb.contrastComp() : 1f);
		u[2] = saturation.getF() * (fb != null ? fb.saturationComp() : 1f);
		u[3] = gamma.getF();
		u[4] = temperature.getF();
		u[5] = tint.getF();
		u[6] = shadows.getF();
		u[7] = highlights.getF();
		u[8] = vignette.getF();
		u[9] = grain.getF();
		u[10] = bloom.get() ? bloomStrength.getF() : 0f;
		u[11] = strength.getF();
		u[12] = brightness.getF();
		u[13] = monochrome.get() ? 1f : 0f;
		u[14] = bloomThreshold.getF();
		u[15] = 0f;
		return u;
	}

	/** CPU copy of the grading shader (grade_common.glsl) for the menu preview. */
	public int gradeColor(int argb) {
		float[] u = uniforms();
		float r = com.blixu.blixuaddons.client.style.Colors.r(argb) / 255f, g = com.blixu.blixuaddons.client.style.Colors.g(argb) / 255f, b = com.blixu.blixuaddons.client.style.Colors.b(argb) / 255f;
		float sr = r, sg = g, sb = b;
		float ex = (float) Math.pow(2, u[0]);
		r = r * ex + u[12]; g = g * ex + u[12]; b = b * ex + u[12];
		r *= 1 + u[4] * 0.18f; g *= 1 - u[5] * 0.12f; b *= 1 - u[4] * 0.18f;
		r *= 1 + u[5] * 0.06f; b *= 1 + u[5] * 0.06f;
		float l = 0.2126f * r + 0.7152f * g + 0.0722f * b;
		float sh = 1 - smooth(0, 0.5f, l), hi = smooth(0.5f, 1, l);
		r += r * u[6] * sh * 0.8f + u[7] * hi * 0.35f;
		g += g * u[6] * sh * 0.8f + u[7] * hi * 0.35f;
		b += b * u[6] * sh * 0.8f + u[7] * hi * 0.35f;
		r = (r - 0.5f) * u[1] + 0.5f; g = (g - 0.5f) * u[1] + 0.5f; b = (b - 0.5f) * u[1] + 0.5f;
		l = 0.2126f * r + 0.7152f * g + 0.0722f * b;
		float sat = u[13] > 0.5f ? 0f : u[2];
		r = l + (r - l) * sat; g = l + (g - l) * sat; b = l + (b - l) * sat;
		float gm = 1f / Math.max(u[3], 0.05f);
		r = (float) Math.pow(Math.max(r, 0), gm); g = (float) Math.pow(Math.max(g, 0), gm); b = (float) Math.pow(Math.max(b, 0), gm);
		float k = u[11];
		r = net.minecraft.util.Mth.lerp(k, sr, net.minecraft.util.Mth.clamp(r, 0, 1)); g = net.minecraft.util.Mth.lerp(k, sg, net.minecraft.util.Mth.clamp(g, 0, 1)); b = net.minecraft.util.Mth.lerp(k, sb, net.minecraft.util.Mth.clamp(b, 0, 1));
		return com.blixu.blixuaddons.client.style.Colors.argb(com.blixu.blixuaddons.client.style.Colors.a(argb), Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
	}

	private static float smooth(float e0, float e1, float x) {
		float t = net.minecraft.util.Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
		return t * t * (3 - 2 * t);
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		int[] samples = {0xFF7FA8FF, 0xFF5E9A3C, 0xFF7C7C7C, 0xFFDBCF8F, 0xFF3157C9, 0xFFE8641A, 0xFF3B6E2A, 0xFFC08A6A};
		float top = y + 16, bh = h - 30;
		float half = w / 2f;
		float split = x + half + net.minecraft.util.Mth.sin(time * 0.6f) * half * 0.6f;
		float cw = (w - 12) / samples.length;
		for (int i = 0; i < samples.length; i++) {
			float cx = x + 6 + i * cw;
			com.blixu.blixuaddons.client.render.Ui.rect(g, cx, top, cw - 1, bh, 2, samples[i]);
			float gx = Math.max(cx, split);
			if (gx < cx + cw - 1) com.blixu.blixuaddons.client.render.Ui.rect(g, gx, top, cx + cw - 1 - gx, bh, 2, gradeColor(samples[i]));
		}
		com.blixu.blixuaddons.client.render.Ui.rect(g, split - 0.5f, top - 2, 1, bh + 4, 0, 0xFFFFFFFF);
		com.blixu.blixuaddons.client.render.Ui.text(g, "Before", x + 6, y + h - 11, 0xFF9097AB, com.blixu.blixuaddons.client.render.UiFont.SMALL);
		com.blixu.blixuaddons.client.render.Ui.text(g, "After", x + w - 6 - com.blixu.blixuaddons.client.render.Ui.width("After", com.blixu.blixuaddons.client.render.UiFont.SMALL), y + h - 11, 0xFFECEEF5, com.blixu.blixuaddons.client.render.UiFont.SMALL);
	}
}
