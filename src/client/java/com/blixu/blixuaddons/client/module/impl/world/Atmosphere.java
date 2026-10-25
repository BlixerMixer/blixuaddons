package com.blixu.blixuaddons.client.module.impl.world;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;

/**
 * Fog colour, density, distances, haze and per-environment looks. Only the fog the client
 * draws changes; the server's view distance still limits what exists (fog never reaches
 * past your render distance).
 */
public class Atmosphere extends Module {
	public enum Preset {
		CUSTOM("Custom"), CLEAR("Clear day"), HAZY("Hazy"), MISTY("Misty morning"), DUSK("Dusk"), DARK("Dark night"), DREAMY("Dreamy");
		public final String label;
		Preset(String l) { label = l; }
	}

	public final EnumSetting<Preset> preset = add(new EnumSetting<>("preset", "Preset", Preset.CUSTOM, p -> p.label));
	public final BoolSetting overrideColor = add(new BoolSetting("override_color", "Override fog colour", false));
	public final ColorSetting fogColor = add(new ColorSetting("fog_color", "Fog colour", 0xB8C6E6).visibleWhen(overrideColor::get));
	public final ColorSetting tint = add(new ColorSetting("tint", "Atmospheric tint", 0x8B5CF6));
	public final NumberSetting tintStrength = add(new NumberSetting("tint_strength", "Tint strength", 0, 0, 1, 0.05));
	public final NumberSetting density = add(new NumberSetting("density", "Fog density", 1, 0, 3, 0.05));
	public final NumberSetting start = add(new NumberSetting("start", "Fog start (% of distance)", 100, 0, 100, 1));
	public final NumberSetting end = add(new NumberSetting("end", "Fog end (% of distance)", 100, 5, 100, 1));
	public final NumberSetting haze = add(new NumberSetting("haze", "Haze intensity", 0, 0, 1, 0.05));
	public final NumberSetting horizonBlend = add(new NumberSetting("horizon_blend", "Horizon blending", 1, 0.2, 4, 0.05));
	public final BoolSetting heightGradient = add(new BoolSetting("height_gradient", "Colour gradient by height", false));
	public final ColorSetting highColor = add(new ColorSetting("high_color", "High-altitude colour", 0x22D3EE).visibleWhen(heightGradient::get));
	public final BoolSetting biomeOverrides = add(new BoolSetting("biome_overrides", "Biome overrides", false));
	public final ColorSetting oceanColor = add(new ColorSetting("ocean_color", "Ocean fog", 0x3A7BD5).visibleWhen(biomeOverrides::get));
	public final ColorSetting desertColor = add(new ColorSetting("desert_color", "Desert / badlands fog", 0xE8B27A).visibleWhen(biomeOverrides::get));
	public final ColorSetting snowColor = add(new ColorSetting("snow_color", "Snowy fog", 0xE6F2FF).visibleWhen(biomeOverrides::get));
	public final ColorSetting jungleColor = add(new ColorSetting("jungle_color", "Jungle / swamp fog", 0x6FAF7A).visibleWhen(biomeOverrides::get));
	public final BoolSetting underwater = add(new BoolSetting("underwater", "Custom underwater", false));
	public final ColorSetting waterColor = add(new ColorSetting("water_color", "Underwater colour", 0x1F6FB5).visibleWhen(underwater::get));
	public final NumberSetting waterDistance = add(new NumberSetting("water_distance", "Underwater visibility", 1, 0.3, 3, 0.05).visibleWhen(underwater::get));
	public final BoolSetting nether = add(new BoolSetting("nether", "Custom Nether", false));
	public final ColorSetting netherColor = add(new ColorSetting("nether_color", "Nether colour", 0x5A1414).visibleWhen(nether::get));
	public final NumberSetting netherDensity = add(new NumberSetting("nether_density", "Nether density", 1, 0, 3, 0.05).visibleWhen(nether::get));
	public final BoolSetting end_ = add(new BoolSetting("end_dim", "Custom End", false));
	public final ColorSetting endColor = add(new ColorSetting("end_color", "End colour", 0x2A1840).visibleWhen(end_::get));
	public final NumberSetting endDensity = add(new NumberSetting("end_density", "End density", 1, 0, 3, 0.05).visibleWhen(end_::get));
	public final NumberSetting transition = add(new NumberSetting("transition", "Transition time (s)", 1.5, 0, 6, 0.1));

	// Smoothed values so preset/biome/dimension changes fade instead of popping.
	private float sStart = -1, sEnd = -1, sR, sG, sB;
	private long lastNanos;

	public Atmosphere() {
		super("atmosphere", "Atmosphere",
			"Fog colour, density, distance, haze, horizon blend, biome and dimension looks.",
			Category.WORLD, "fog", false);
		preset.onChange(this::applyPreset);
	}

	private void applyPreset(Preset p) {
		switch (p) {
			case CLEAR -> set(false, 0xC8DAFF, 0, 0.6, 100, 100, 0, 1);
			case HAZY -> set(true, 0xD8D0C0, 0.1, 1.3, 30, 85, 0.35, 1.6);
			case MISTY -> set(true, 0xD0DCE8, 0.15, 2.2, 5, 55, 0.6, 2.4);
			case DUSK -> set(true, 0xC07A6A, 0.25, 1.2, 40, 90, 0.2, 1.4);
			case DARK -> set(true, 0x0A0C18, 0, 1.6, 20, 75, 0.1, 1.2);
			case DREAMY -> set(true, 0xE0B8FF, 0.35, 1.4, 25, 80, 0.4, 2);
			case CUSTOM -> { }
		}
		ConfigManager.save();
	}

	private void set(boolean override, int color, double tintS, double dens, double st, double en, double hz, double blend) {
		overrideColor.setSilently(override);
		fogColor.setSilently(0xFF000000 | color);
		tintStrength.setSilently(tintS);
		density.setSilently(dens);
		start.setSilently(st);
		end.setSilently(en);
		haze.setSilently(hz);
		horizonBlend.setSilently(blend);
	}

	/** Called at the end of FogRenderer.setupFog. */
	public void apply(FogData fog, Camera camera, float renderDistanceBlocks) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		if (level == null) return;
		FogType fluid = camera.getFluidInCamera();
		float r = fog.color.x, g = fog.color.y, b = fog.color.z;
		float targetStart = fog.environmentalStart, targetEnd = fog.environmentalEnd;
		boolean isNether = level.dimension() == Level.NETHER, isEnd = level.dimension() == Level.END;

		if (fluid == FogType.WATER) {
			if (!underwater.get()) return;
			int c = waterColor.argb();
			r = Colors.r(c) / 255f; g = Colors.g(c) / 255f; b = Colors.b(c) / 255f;
			targetEnd = Math.min(renderDistanceBlocks, fog.environmentalEnd * waterDistance.getF());
			targetStart = Math.min(targetStart, targetEnd * 0.5f);
		} else if (fluid != FogType.NONE) {
			return; // lava / powder snow: leave vanilla (gameplay-relevant visibility)
		} else {
			float dens = density.getF();
			int override = 0;
			if (isNether && nether.get()) {
				override = netherColor.argb();
				dens = netherDensity.getF();
			} else if (isEnd && end_.get()) {
				override = endColor.argb();
				dens = endDensity.getF();
			} else if (overrideColor.get()) {
				override = fogColor.argb();
			}
			if (biomeOverrides.get() && !isNether && !isEnd) {
				int bc = biomeColor(level.getBiome(camera.blockPosition()));
				if (bc != 0) override = override == 0 ? bc : Colors.lerp(override, bc, 0.6f);
			}
			if (override != 0) {
				r = Colors.r(override) / 255f; g = Colors.g(override) / 255f; b = Colors.b(override) / 255f;
			}
			if (heightGradient.get()) {
				float k = Mth.clamp((float) (camera.position().y - 64) / 192f, 0f, 1f);
				int hc = highColor.argb();
				r = Mth.lerp(k, r, Colors.r(hc) / 255f); g = Mth.lerp(k, g, Colors.g(hc) / 255f); b = Mth.lerp(k, b, Colors.b(hc) / 255f);
			}
			// Distances: % of the render distance, divided by density, pulled in by haze.
			float dist = renderDistanceBlocks;
			float d = Math.max(0.05f, dens);
			targetEnd = Math.min(fog.environmentalEnd, dist * end.getF() / 100f / d);
			targetStart = Math.min(targetEnd * 0.98f, dist * start.getF() / 100f / d);
			if (haze.getF() > 0) targetStart = Mth.lerp(haze.getF(), targetStart, 0f);
			// Horizon blending: widen/narrow the fade at the render-distance edge.
			float span = (fog.renderDistanceEnd - fog.renderDistanceStart) * horizonBlend.getF();
			fog.renderDistanceStart = Math.max(0, fog.renderDistanceEnd - span);
		}
		float ts = tintStrength.getF();
		if (ts > 0) {
			int c = tint.argb();
			r = Mth.lerp(ts, r, Colors.r(c) / 255f); g = Mth.lerp(ts, g, Colors.g(c) / 255f); b = Mth.lerp(ts, b, Colors.b(c) / 255f);
		}

		// Smooth transitions
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 1f : Math.min((now - lastNanos) / 1e9f, 0.25f);
		lastNanos = now;
		float k = transition.getF() <= 0.01f || sStart < 0 ? 1f : 1f - (float) Math.exp(-dt * 3f / transition.getF());
		sStart = sStart < 0 ? targetStart : Mth.lerp(k, sStart, targetStart);
		sEnd = sEnd < 0 ? targetEnd : Mth.lerp(k, sEnd, targetEnd);
		sR = Mth.lerp(k, sR, r);
		sG = Mth.lerp(k, sG, g);
		sB = Mth.lerp(k, sB, b);
		fog.environmentalStart = sStart;
		fog.environmentalEnd = Math.min(sEnd, Math.max(fog.renderDistanceEnd, 1f));
		fog.color.set(sR, sG, sB, 1f);
	}

	private int biomeColor(Holder<Biome> biome) {
		String id = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		if (id.contains("ocean") || id.contains("river") || id.contains("beach")) return oceanColor.argb();
		if (id.contains("desert") || id.contains("badlands") || id.contains("savanna")) return desertColor.argb();
		if (id.contains("snow") || id.contains("frozen") || id.contains("ice") || id.contains("peaks")) return snowColor.argb();
		if (id.contains("jungle") || id.contains("swamp") || id.contains("mangrove")) return jungleColor.argb();
		return 0;
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		// Vanilla sky over three rows of hills that fade into the fog colour with distance.
		float horizonY = y + h * 0.66f;
		com.blixu.blixuaddons.client.render.Ui.pushScissor(g, (int) x + 1, (int) y + 1, (int) (x + w) - 1, (int) (y + h) - 1);
		com.blixu.blixuaddons.client.render.Ui.gradientV(g, x, y, w, horizonY - y + 1, 0, 0xFF5A8CFF, 0xFFC4D8FF);
		com.blixu.blixuaddons.client.render.Ui.shadow(g, x + w * 0.72f - 6, y + h * 0.2f - 6, 12, 12, 6, 9, 0x80FFF3D0);
		com.blixu.blixuaddons.client.render.Ui.circle(g, x + w * 0.72f, y + h * 0.2f, 6, 0xFFFFF3D0);
		int fog = 0xFFC4D8FF;
		if (overrideColor.get()) fog = fogColor.argb();
		if (tintStrength.getF() > 0) fog = com.blixu.blixuaddons.client.style.Colors.lerp(fog, tint.argb(), tintStrength.getF());
		float start = this.start.getF() / 100f, end = this.end.getF() / 100f, dens = Math.max(0.05f, density.getF()), hz = haze.getF();
		com.blixu.blixuaddons.client.render.Ui.gradientV(g, x, horizonY - h * 0.12f, w, h * 0.12f + 1, 0, fog & 0x00FFFFFF,
			com.blixu.blixuaddons.client.style.Colors.withAlpha(fog, 0.7f * Math.min(1f, dens)));
		int[] hill = {0xFF2F5A2A, 0xFF3C6E33, 0xFF4E8A3E};
		float[] dist = {0.85f, 0.55f, 0.25f};
		for (int row = 0; row < 3; row++) {
			float fs = start / dens * (1 - hz), fe = Math.max(fs + 0.01f, end / dens);
			float k = Mth.clamp((dist[row] - fs) / (fe - fs), 0f, 1f);
			int col = com.blixu.blixuaddons.client.style.Colors.lerp(hill[row], fog | 0xFF000000, k);
			float base = horizonY + row * h * 0.1f;
			for (int i = -1; i < 5; i++) {
				float hx = x + (i + (row % 2) * 0.5f) * w / 3.2f, hw = w / 2.2f, hh = h * (0.16f + 0.05f * ((i + row + 3) % 3));
				com.blixu.blixuaddons.client.render.Ui.rect(g, hx, base - hh * 0.5f, hw, hh * 2, hh, col);
			}
		}
		com.blixu.blixuaddons.client.render.Ui.popScissor(g);
	}

	@Override
	public void onWorldChange() {
		sStart = sEnd = -1;
	}
}
