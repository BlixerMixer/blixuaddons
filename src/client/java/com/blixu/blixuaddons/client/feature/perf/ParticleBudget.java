package com.blixu.blixuaddons.client.feature.perf;

import com.blixu.blixuaddons.client.render.ParticlePool;
import net.minecraft.util.Mth;

/**
 * One particle budget shared by all our effects (hit particles, trails, relics, cosmetics), plus
 * distance thinning, cosmetic density and auto quality. Only ever drops NEW spawns: live particles
 * finish their animation, and nothing here touches vanilla particles or any game mechanic.
 * Values are plain statics set by the Performance Engine module each tick (defaults = no limits).
 */
public final class ParticleBudget {
	private ParticleBudget() {}

	public static int maxTotal = Integer.MAX_VALUE;
	public static boolean distanceThinning;
	public static float thinFrom = 24f, thinTo = 64f;
	public static float cosmeticDensity = 1f;
	/** 0.3..1, lowered by auto quality while frames are slower than the target. */
	public static float autoScale = 1f;
	public static double camX, camY, camZ;

	private static int seed = 0x2545F491;

	public static void reset() {
		maxTotal = Integer.MAX_VALUE;
		distanceThinning = false;
		cosmeticDensity = 1f;
		autoScale = 1f;
	}

	public static boolean allow(ParticlePool.Kind kind, double x, double y, double z) {
		if (kind == ParticlePool.Kind.PREVIEW) return true;
		if (ParticlePool.liveTotal() >= maxTotal) return false;
		float keep = autoScale;
		if (kind == ParticlePool.Kind.COSMETIC) keep *= cosmeticDensity;
		if (distanceThinning) {
			double dx = x - camX, dy = y - camY, dz = z - camZ;
			float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			float t = Mth.clamp((d - thinFrom) / Math.max(1f, thinTo - thinFrom), 0f, 1f);
			keep *= 1f - 0.85f * t * t * (3 - 2 * t); // far away keeps 15%
		}
		return keep >= 1f || nextFloat() < keep;
	}

	private static float nextFloat() {
		int s = seed; // xorshift: no allocation, render thread only
		s ^= s << 13;
		s ^= s >>> 17;
		s ^= s << 5;
		seed = s;
		return (s >>> 8) / (float) (1 << 24);
	}
}
