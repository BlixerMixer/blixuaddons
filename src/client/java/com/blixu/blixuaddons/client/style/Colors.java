package com.blixu.blixuaddons.client.style;

import net.minecraft.util.Mth;

/** Colour maths shared by every visual module. Colours are 0xAARRGGBB ints. */
public final class Colors {
	private Colors() {}

	public static int a(int c) { return c >>> 24; }
	public static int r(int c) { return (c >> 16) & 0xFF; }
	public static int g(int c) { return (c >> 8) & 0xFF; }
	public static int b(int c) { return c & 0xFF; }

	public static int argb(int a, int r, int g, int b) {
		return (Mth.clamp(a, 0, 255) << 24) | (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
	}

	/** Multiplies the existing alpha by {@code alpha} (0..1). */
	public static int withAlpha(int c, float alpha) {
		return (Mth.clamp(Math.round(a(c) * alpha), 0, 255) << 24) | (c & 0xFFFFFF);
	}

	/** Replaces alpha with {@code alpha} (0..1). */
	public static int setAlpha(int c, float alpha) {
		return (Mth.clamp(Math.round(255 * alpha), 0, 255) << 24) | (c & 0xFFFFFF);
	}

	/** Straight per-channel blend, including alpha. Cheap enough for per-vertex use. */
	public static int lerp(int from, int to, float t) {
		t = Mth.clamp(t, 0f, 1f);
		return argb(
			Math.round(a(from) + (a(to) - a(from)) * t),
			Math.round(r(from) + (r(to) - r(from)) * t),
			Math.round(g(from) + (g(to) - g(from)) * t),
			Math.round(b(from) + (b(to) - b(from)) * t));
	}

	/** Sample an evenly spaced multi-stop gradient at {@code t} (0..1). */
	public static int gradient(int[] stops, float t) {
		if (stops.length == 1) return stops[0];
		float f = Mth.clamp(t, 0f, 1f) * (stops.length - 1);
		int i = Math.min((int) f, stops.length - 2);
		return lerp(stops[i], stops[i + 1], f - i);
	}

	/** Brightness multiplier (keeps alpha). */
	public static int scale(int c, float k) {
		return argb(a(c), Math.round(r(c) * k), Math.round(g(c) * k), Math.round(b(c) * k));
	}

	public static float[] toHsv(int c) {
		float rf = r(c) / 255f, gf = g(c) / 255f, bf = b(c) / 255f;
		float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
		float d = max - min;
		float h = 0;
		if (d > 0) {
			if (max == rf) h = ((gf - bf) / d) % 6f;
			else if (max == gf) h = (bf - rf) / d + 2f;
			else h = (rf - gf) / d + 4f;
			h /= 6f;
			if (h < 0) h += 1f;
		}
		return new float[] {h, max == 0 ? 0 : d / max, max};
	}

	public static int fromHsv(float h, float s, float v, int alpha) {
		return (alpha << 24) | (Mth.hsvToRgb(h - (float) Math.floor(h), Mth.clamp(s, 0f, 1f), Mth.clamp(v, 0f, 1f)) & 0xFFFFFF);
	}

	/** Same colour with its hue turned by {@code turns} (1 = full circle); matches the glow shaders' hue shift closely enough for previews. */
	public static int hueShift(int c, float turns) {
		float[] hsv = toHsv(c);
		return fromHsv(hsv[0] + turns, hsv[1], hsv[2], a(c));
	}

	/** Seconds since start, for colour animation that stays smooth at any frame rate. */
	public static float seconds() {
		return (System.nanoTime() / 1_000_000L) / 1000f;
	}

	/** Animated hue that keeps {@code base}'s saturation, brightness and alpha. Grey bases get full saturation. */
	public static int rainbow(int base, float speed, float phase) {
		float[] hsv = toHsv(base);
		float s = hsv[1] < 0.15f ? 0.85f : hsv[1];
		float v = hsv[2] < 0.15f ? 1f : hsv[2];
		return fromHsv(seconds() * speed + phase, s, v, a(base));
	}

	public static String hex(int c) {
		return a(c) == 255 ? String.format("#%06X", c & 0xFFFFFF) : String.format("#%08X", c);
	}

	/** Self-check for the maths above; run from a unit test or a debugger. */
	static void selfCheck() {
		assert lerp(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF808080 : "lerp";
		assert gradient(new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF}, 1f) == 0xFF0000FF : "gradient end";
		float[] hsv = toHsv(0xFFFF0000);
		assert Math.abs(hsv[0]) < 1e-4 && hsv[1] == 1f && hsv[2] == 1f : "hsv red";
		assert (fromHsv(1f / 3f, 1f, 1f, 255) & 0xFFFFFF) == 0x00FF00 : "hsv green";
		assert withAlpha(0x80FFFFFF, 0.5f) >>> 24 == 64 : "alpha";
	}
}
