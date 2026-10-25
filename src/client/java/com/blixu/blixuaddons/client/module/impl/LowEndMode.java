package com.blixu.blixuaddons.client.module.impl;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;

/**
 * Cheaper versions of the heavy GPU work for low-end machines. The flags are plain statics refreshed
 * every tick, so the hot paths (particle spawns, per-frame uniforms) never look the module up.
 */
public class LowEndMode extends Module {
	public final BoolSetting lightGlow = add(new BoolSetting("light_glow", "Lighter glow shaders (no star fields, fewer taps)", true));
	public final BoolSetting lightSky = add(new BoolSetting("light_sky", "Lighter Cosmic Sky (fewer noise layers)", true));
	public final BoolSetting skipAa = add(new BoolSetting("skip_aa", "Skip anti-aliasing", true));
	public final NumberSetting particles = add(new NumberSetting("particles", "Particle amount", 0.5, 0.1, 1, 0.05));

	private static boolean glow, sky, aa;
	private static float particleScale = 1f;

	public LowEndMode() {
		super("low_end_mode", "Low-End Mode",
			"Lighter shaders, sky and particles so everything runs smooth on weaker PCs and laptops.",
			Category.OPTIMIZATIONS, "optimizations", false);
	}

	@Override
	public void onTick(net.minecraft.client.Minecraft client) {
		glow = lightGlow.get();
		sky = lightSky.get();
		aa = skipAa.get();
		particleScale = particles.getF();
	}

	@Override
	protected void onDisable() {
		glow = sky = aa = false;
		particleScale = 1f;
	}

	public static boolean lightGlow() { return glow; }
	public static boolean lightSky() { return sky; }
	public static boolean skipAa() { return aa; }
	public static float particleScale() { return particleScale; }
}
