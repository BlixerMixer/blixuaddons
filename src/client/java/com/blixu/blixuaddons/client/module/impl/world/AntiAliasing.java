package com.blixu.blixuaddons.client.module.impl.world;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;

/**
 * FXAA over the finished world image (blocks, entities, hand, and every line, outline, cosmetic and
 * particle we draw), before the GUI. One full-screen GLSL pass: post_effect/fxaa.json.
 */
public class AntiAliasing extends Module {
	public enum Quality {
		LOW("Low (fast)", 0.25f, 4), MEDIUM("Medium", 0.166f, 8), HIGH("High", 0.125f, 12), ULTRA("Ultra", 0.063f, 16);
		public final String label;
		final float threshold;
		final int spread;
		Quality(String l, float threshold, int spread) {
			label = l;
			this.threshold = threshold;
			this.spread = spread;
		}
	}

	public final EnumSetting<Quality> quality = add(new EnumSetting<>("quality", "Quality", Quality.HIGH, q -> q.label));
	public final NumberSetting subpixel = add(new NumberSetting("subpixel", "Sub-pixel smoothing", 0.75, 0, 1, 0.05));

	public AntiAliasing() {
		super("anti_aliasing", "Anti-Aliasing",
			"FXAA smoothing for jagged block edges, lines, outlines and cosmetics (one GLSL pass).",
			Category.OPTIMIZATIONS, "optimizations", false);
	}

	/** FxaaConfig: Subpixel, EdgeThreshold, EdgeMin, Spread. */
	public float[] uniforms() {
		Quality q = quality.get();
		return new float[] {subpixel.getF(), q.threshold, 0.0312f, q.spread};
	}
}
