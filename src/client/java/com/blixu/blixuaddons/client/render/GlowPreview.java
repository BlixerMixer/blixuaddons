package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.client.feature.GlowStyle;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * 2D stand-in for the glow post effects, for menu previews. Each style mirrors its shader:
 * same layers (halo / stars / nebula / strokes / edge) and the same colours, drawn around a
 * silhouette made of rounded boxes.
 */
public final class GlowPreview {
	private GlowPreview() {}

	/** {@code parts}: {x, y, w, h, radius} per box of the silhouette. {@code body} = fill colour (0 = none). */
	public static void paint(GuiGraphicsExtractor g, GlowStyle.@org.jspecify.annotations.Nullable Look look, int color, float time, float[][] parts, int body) {
		float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f;
		for (float[] p : parts) {
			minX = Math.min(minX, p[0]);
			minY = Math.min(minY, p[1]);
			maxX = Math.max(maxX, p[0] + p[2]);
			maxY = Math.max(maxY, p[1] + p[3]);
		}
		// the aura glow: soft band, outer colour, edge stroke
		int c = color | 0xFF000000;
		float k = look != null ? look.strength.getF() : 1f;
		int outer = look != null && look.ownOuter.get() ? look.outer.argb() | 0xFF000000 : Colors.scale(c, 0.4f);
		float rk = look != null && look.radius != null ? look.radius.getF() : 1f; // the module's own band size
		for (float[] p : parts) Ui.shadow(g, p[0], p[1], p[2], p[3], p[4], 11 * rk, Colors.withAlpha(c, Math.min(1f, 0.8f * k)));
		for (float[] p : parts) Ui.shadow(g, p[0], p[1], p[2], p[3], p[4], 7 * rk, Colors.withAlpha(outer, Math.min(1f, 0.75f * k)));
		int edge = Colors.lerp(c, 0xFFFFFFFF, 0.65f);
		if (body != 0) for (float[] p : parts) Ui.rect(g, p[0], p[1], p[2], p[3], p[4], body);
		for (float[] p : parts) Ui.outline(g, p[0], p[1], p[2], p[3], p[4], 1.2f, edge);
	}

	private static float hash(float n) {
		float s = Mth.sin(n) * 43758.5453f;
		return s - (float) Math.floor(s);
	}

	/** A tall mob silhouette (enderman-ish proportions) centred at cx, with feet at bottom. */
	public static float[][] mob(float cx, float bottom, float s) {
		return new float[][] {
			{cx - 4 * s, bottom - 44 * s, 8 * s, 8 * s, 1.5f * s},
			{cx - 4.5f * s, bottom - 35 * s, 9 * s, 13 * s, 1.5f * s},
			{cx - 8 * s, bottom - 35 * s, 2.5f * s, 22 * s, 1.2f * s},
			{cx + 5.5f * s, bottom - 35 * s, 2.5f * s, 22 * s, 1.2f * s},
			{cx - 3.8f * s, bottom - 21 * s, 3 * s, 21 * s, 1.2f * s},
			{cx + 0.8f * s, bottom - 21 * s, 3 * s, 21 * s, 1.2f * s},
		};
	}
}
