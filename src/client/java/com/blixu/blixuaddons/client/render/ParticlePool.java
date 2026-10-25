package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.util.Mth;

/**
 * Fixed-capacity particle storage (struct-of-arrays, no per-particle objects). When full,
 * new particles are dropped, so a burst can never grow memory or frame cost unbounded.
 * Purely client-side visuals.
 */
public final class ParticlePool {
	public enum Fade {
		LINEAR("Linear"), SMOOTH("Smooth"), LATE("Fade late"), EARLY("Fade early");
		public final String label;
		Fade(String l) { label = l; }

		float alpha(float t) {
			return switch (this) {
				case LINEAR -> 1 - t;
				case SMOOTH -> 1 - t * t * (3 - 2 * t);
				case LATE -> 1 - t * t * t;
				case EARLY -> (1 - t) * (1 - t);
			};
		}
	}

	/** What a pool is for: the shared budget thins COSMETIC more, and never touches menu PREVIEW pools. */
	public enum Kind { EFFECT, COSMETIC, PREVIEW }

	private static final java.util.List<ParticlePool> WORLD_POOLS = new java.util.ArrayList<>();

	/** Live particles across all in-world pools (a handful of pools, so summing is cheap). */
	public static int liveTotal() {
		int n = 0;
		for (int i = 0; i < WORLD_POOLS.size(); i++) n += WORLD_POOLS.get(i).count;
		return n;
	}

	private final Kind kind;
	private final int capacity;
	private int count;
	private final double[] x, y, z;
	private final float[] vx, vy, vz, age, life, size, rot, spin, gravity, drag, endSize;
	private final int[] c1, c2;
	private final byte[] shape;
	private final boolean[] glow;
	public Fade fade = Fade.SMOOTH;
	/** Seconds of fade-in at the start of each particle's life. */
	public float fadeIn = 0.05f;

	public ParticlePool(int capacity) {
		this(capacity, Kind.EFFECT);
	}

	public ParticlePool(int capacity, Kind kind) {
		this.capacity = capacity;
		this.kind = kind;
		if (kind != Kind.PREVIEW) WORLD_POOLS.add(this);
		x = new double[capacity];
		y = new double[capacity];
		z = new double[capacity];
		vx = new float[capacity];
		vy = new float[capacity];
		vz = new float[capacity];
		age = new float[capacity];
		life = new float[capacity];
		size = new float[capacity];
		endSize = new float[capacity];
		rot = new float[capacity];
		spin = new float[capacity];
		gravity = new float[capacity];
		drag = new float[capacity];
		c1 = new int[capacity];
		c2 = new int[capacity];
		shape = new byte[capacity];
		glow = new boolean[capacity];
	}

	public int count() { return count; }
	public void clear() { count = 0; }

	/** @return false if the pool (or {@code limit}) is full and the particle was dropped */
	public boolean spawn(double px, double py, double pz, float velX, float velY, float velZ, float lifetime, float startSize, float finalSize,
						 int colorStart, int colorEnd, Shape s, boolean additive, float grav, float dragPerSecond, float rotation, float spinSpeed, int limit) {
		if (count >= Math.min(capacity, limit) * com.blixu.blixuaddons.client.module.impl.LowEndMode.particleScale() || lifetime <= 0) return false;
		if (!com.blixu.blixuaddons.client.feature.perf.ParticleBudget.allow(kind, px, py, pz)) return false;
		int i = count++;
		x[i] = px; y[i] = py; z[i] = pz;
		vx[i] = velX; vy[i] = velY; vz[i] = velZ;
		age[i] = 0; life[i] = lifetime;
		size[i] = startSize; endSize[i] = finalSize;
		c1[i] = colorStart; c2[i] = colorEnd;
		shape[i] = (byte) s.ordinal();
		glow[i] = additive;
		gravity[i] = grav;
		drag[i] = dragPerSecond;
		rot[i] = rotation;
		spin[i] = spinSpeed;
		return true;
	}

	public void update(float dt) {
		int i = 0;
		while (i < count) {
			age[i] += dt;
			if (age[i] >= life[i]) {
				removeAt(i);
				continue;
			}
			float k = Math.max(0f, 1f - drag[i] * dt);
			vx[i] *= k;
			vy[i] = (vy[i] - gravity[i] * dt) * k;
			vz[i] *= k;
			x[i] += vx[i] * dt;
			y[i] += vy[i] * dt;
			z[i] += vz[i] * dt;
			rot[i] += spin[i] * dt;
			i++;
		}
	}

	private void removeAt(int i) {
		int last = --count;
		if (i == last) return;
		x[i] = x[last]; y[i] = y[last]; z[i] = z[last];
		vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last];
		age[i] = age[last]; life[i] = life[last];
		size[i] = size[last]; endSize[i] = endSize[last];
		rot[i] = rot[last]; spin[i] = spin[last];
		gravity[i] = gravity[last]; drag[i] = drag[last];
		c1[i] = c1[last]; c2[i] = c2[last];
		shape[i] = shape[last]; glow[i] = glow[last];
	}

	public void draw(WorldCtx ctx, float alphaMul) {
		Shape[] shapes = Shape.values();
		for (int i = 0; i < count; i++) {
			if (!ctx.inFront(x[i], y[i], z[i])) continue;
			float t = age[i] / life[i];
			float a = fade.alpha(t) * alphaMul;
			if (fadeIn > 0) a *= Mth.clamp(age[i] / fadeIn, 0f, 1f);
			int c = Colors.withAlpha(Colors.lerp(c1[i], c2[i], t), a);
			if ((c >>> 24) < 2) continue;
			float s = Mth.lerp(t, size[i], endSize[i]);
			ctx.billboard(glow[i] ? ctx.glow : ctx.fill, x[i], y[i], z[i], s, rot[i], c, shapes[shape[i]], vx[i], vy[i], vz[i]);
		}
	}
}
