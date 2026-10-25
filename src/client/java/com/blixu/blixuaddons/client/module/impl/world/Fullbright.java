package com.blixu.blixuaddons.client.module.impl.world;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;

/**
 * Brightness done in the lightmap (what the GPU uses to light blocks) and/or as post
 * exposure. World light levels and server lighting are untouched.
 */
public class Fullbright extends Module {
	public enum Mode {
		GAMMA("Gamma"), BOOST("Brightness boost"), NIGHT_VISION("Night vision"), COLOR_PRESERVING("Colour-preserving"), EXPOSURE("Exposure");
		public final String label;
		Mode(String l) { label = l; }
	}

	public enum Preset {
		SUBTLE("Subtle"), BRIGHT("Bright"), MAX("Max"), CUSTOM("Custom");
		public final String label;
		Preset(String l) { label = l; }
	}

	public final EnumSetting<Preset> preset = add(new EnumSetting<>("preset", "Preset", Preset.BRIGHT, p -> p.label));
	public final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", "Mode", Mode.GAMMA, m -> m.label));
	public final NumberSetting intensity = add(new NumberSetting("intensity", "Brightness intensity", 0.8, 0, 1, 0.01));
	public final NumberSetting exposure = add(new NumberSetting("exposure", "Exposure (EV)", 0.6, 0, 2.5, 0.05));
	public final NumberSetting contrastKeep = add(new NumberSetting("contrast_keep", "Contrast preservation", 0.5, 0, 1, 0.05));
	public final NumberSetting saturationKeep = add(new NumberSetting("saturation_keep", "Saturation preservation", 0.5, 0, 1, 0.05));
	public final ColorSetting tint = add(new ColorSetting("tint", "Light tint", 0xFFFFFF));
	public final NumberSetting tintStrength = add(new NumberSetting("tint_strength", "Tint strength", 0, 0, 1, 0.05));
	public final BoolSetting separateWater = add(new BoolSetting("separate_water", "Separate underwater brightness", false));
	public final NumberSetting waterIntensity = add(new NumberSetting("water_intensity", "Underwater intensity", 0.5, 0, 1, 0.01)
		.visibleWhen(separateWater::get));

	private final Vector3f ambient = new Vector3f();
	private final Vector3f nightColor = new Vector3f();

	public Fullbright() {
		super("fullbright", "Fullbright",
			"See in the dark: gamma, night-vision style or exposure. Only changes how light is drawn.",
			Category.WORLD, "world", false);
		preset.onChange(p -> {
			switch (p) {
				case SUBTLE -> { intensity.setSilently(0.35); exposure.setSilently(0.25); }
				case BRIGHT -> { intensity.setSilently(0.8); exposure.setSilently(0.6); }
				case MAX -> { intensity.setSilently(1.0); exposure.setSilently(1.4); }
				case CUSTOM -> { }
			}
			ConfigManager.save();
		});
		intensity.onChange(v -> preset.setSilently(Preset.CUSTOM));
		exposure.onChange(v -> preset.setSilently(Preset.CUSTOM));
	}

	private float strength() {
		var p = Minecraft.getInstance().player;
		boolean water = p != null && p.isUnderWater();
		return separateWater.get() && water ? waterIntensity.getF() : intensity.getF();
	}

	/** Called at the end of lightmap extraction (only when the lightmap is being rebuilt). */
	public void applyLightmap(LightmapRenderState s) {
		float k = strength();
		int t = tint.argb();
		float tr = Colors.r(t) / 255f, tg = Colors.g(t) / 255f, tb = Colors.b(t) / 255f;
		float ts = tintStrength.getF();
		switch (mode.get()) {
			case GAMMA -> s.brightness = Math.max(s.brightness, 1f + k * 9f);
			case BOOST -> {
				float floor = 0.15f + 0.55f * k;
				ambient.set(Math.max(s.ambientColor.x(), floor), Math.max(s.ambientColor.y(), floor), Math.max(s.ambientColor.z(), floor));
				s.ambientColor = tinted(ambient, tr, tg, tb, ts);
				s.brightness = Math.max(s.brightness, 1f);
			}
			case NIGHT_VISION -> {
				s.nightVisionEffectIntensity = Math.max(s.nightVisionEffectIntensity, k);
				nightColor.set(1f, 1f, 1f);
				s.nightVisionColor = tinted(nightColor, tr, tg, tb, Math.max(ts, 0f));
			}
			case COLOR_PRESERVING -> {
				// Lift the ambient floor in the ambient colour's own hue, keep block light tint.
				float floor = 0.1f + 0.5f * k;
				float m = Math.max(0.001f, Math.max(s.ambientColor.x(), Math.max(s.ambientColor.y(), s.ambientColor.z())));
				float scale = Math.max(1f, floor / m);
				ambient.set(s.ambientColor.x() * scale, s.ambientColor.y() * scale, s.ambientColor.z() * scale);
				if (m < 0.01f) ambient.set(floor, floor, floor);
				s.ambientColor = tinted(ambient, tr, tg, tb, ts);
			}
			case EXPOSURE -> { }
		}
	}

	private Vector3f tinted(Vector3f v, float r, float g, float b, float strength) {
		if (strength <= 0f) return v;
		return v.set(v.x * (1 - strength + strength * r), v.y * (1 - strength + strength * g), v.z * (1 - strength + strength * b));
	}

	/** Extra post-process exposure (EXPOSURE mode, or a little help in the other modes). */
	public float exposureEv() {
		return mode.get() == Mode.EXPOSURE ? exposure.getF() * (strength() / Math.max(0.01f, intensity.getF())) : 0f;
	}

	public float contrastComp() {
		return 1f + contrastKeep.getF() * 0.12f * exposureEv();
	}

	public float saturationComp() {
		return 1f + saturationKeep.getF() * 0.15f * exposureEv();
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		// A dark cave wall: blocks fade from torch-lit (left) to pitch black (right).
		int[] blocks = {0xFF7C7C7C, 0xFF5E5E5E, 0xFF8A6A50, 0xFF6E6E6E, 0xFF4F7A3A, 0xFF7C7C7C};
		float k = intensity.getF();
		int t = tint.argb();
		float cell = (w - 12) / 6f;
		for (int row = 0; row < 4; row++) for (int col = 0; col < 6; col++) {
			float lightLevel = 1f - col / 5f; // 1 = lit, 0 = dark
			float vanilla = 0.08f + 0.92f * lightLevel * lightLevel;
			float b = switch (mode.get()) {
				case GAMMA -> Math.min(1f, vanilla + (1 - vanilla) * k * 0.95f);
				case BOOST -> Math.max(vanilla, 0.15f + 0.55f * k);
				case NIGHT_VISION -> net.minecraft.util.Mth.lerp(k, vanilla, 0.95f);
				case COLOR_PRESERVING -> Math.max(vanilla, 0.1f + 0.5f * k);
				case EXPOSURE -> Math.min(1f, vanilla * (float) Math.pow(2, exposure.getF()));
			};
			int base = blocks[(row * 7 + col) % blocks.length];
			int c = com.blixu.blixuaddons.client.style.Colors.scale(base, b);
			if (tintStrength.getF() > 0) c = com.blixu.blixuaddons.client.style.Colors.lerp(c, com.blixu.blixuaddons.client.style.Colors.scale(t | 0xFF000000, b), tintStrength.getF() * 0.5f);
			com.blixu.blixuaddons.client.render.Ui.rect(g, x + 6 + col * cell, y + 16 + row * (h - 30) / 4f, cell - 1, (h - 30) / 4f - 1, 1, c);
		}
		com.blixu.blixuaddons.client.render.Ui.text(g, mode.get().label, x + 6, y + h - 11, 0xFF9097AB, com.blixu.blixuaddons.client.render.UiFont.SMALL);
	}
}
