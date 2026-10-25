package com.blixu.blixuaddons.client.feature;

/**
 * Counter-Strike style knife inspects as keyframed first-person poses. Each key is
 * {t, x, y, z, pitch, yaw, roll}: t in 0..1, offsets in view-space blocks (x towards the screen
 * centre is negative for the right hand), angles in degrees. Rotations pivot around the grip so spins
 * turn around the fingers like in CS. Channels are Catmull-Rom curves, so the motion flows through the
 * keys instead of stopping at each one. Every inspect starts at rest and ends at rest (spins end on a
 * whole number of turns, which looks the same as no turn).
 */
public final class KnifeInspects {
	private KnifeInspects() {}

	public enum Anim {
		// Default CT/T knife: raise, show the blade's face, turn it over to show the other side, put it back.
		CLASSIC("Classic (CT/T knife)", 3.6f, -0.05f, -0.25f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.14f, -0.18f, 0.08f, 0.05f, -10, -25, 15},
			{0.34f, -0.20f, 0.10f, 0.06f, -5, -70, 25},
			{0.46f, -0.20f, 0.11f, 0.06f, -6, -74, 24},
			{0.64f, -0.18f, 0.10f, 0.05f, 4, 110, -20},
			{0.76f, -0.18f, 0.09f, 0.05f, 5, 114, -22},
			{0.90f, -0.04f, 0.02f, 0.01f, 0, 10, -4},
			{1, 0, 0, 0, 0, 0, 0},
		}),
		// Karambit: rolled onto its flat side (the face points up; the held sword's face points sideways,
		// so that is a 90 degree roll), two spins around the ring finger in that flat plane with the tip
		// going forward first (a turn around the vertical axis), then tilted up to show it and put back.
		KARAMBIT("Karambit", 4.2f, -0.28f, -0.30f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.10f, -0.08f, 0.03f, 0.02f, 0, 0, 45},
			{0.20f, -0.10f, 0.04f, 0.00f, 0, 0, 90},
			{0.25f, -0.10f, 0.04f, 0.00f, 0, -8, 90},
			{0.37f, -0.10f, 0.04f, 0.00f, 0, -330, 90},
			{0.49f, -0.10f, 0.04f, 0.00f, 0, -700, 90},
			{0.54f, -0.10f, 0.04f, 0.00f, 0, -720, 90},
			{0.64f, -0.14f, 0.07f, 0.03f, -10, -726, 60},
			{0.76f, -0.14f, 0.07f, 0.03f, -12, -724, 58},
			{0.89f, -0.04f, 0.02f, 0.01f, -2, -719, 10},
			{1, 0, 0, 0, 0, -720, 0},
		}),
		// Butterfly: handle flips (fast turns around the long axis), a toss with an aerial flip, a twirl.
		BUTTERFLY("Butterfly", 4.6f, -0.05f, -0.10f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.08f, -0.15f, 0.05f, 0.03f, 0, 0, 20},
			{0.18f, -0.15f, 0.06f, 0.03f, 0, 360, 20},
			{0.28f, -0.16f, 0.07f, 0.03f, 20, 720, -10},
			{0.38f, -0.17f, 0.08f, 0.04f, 10, 660, 0},
			{0.50f, -0.14f, 0.30f, 0.04f, -360, 700, 0},
			{0.60f, -0.15f, 0.06f, 0.03f, -360, 720, 0},
			{0.72f, -0.15f, 0.06f, 0.03f, -360, 720, -340},
			{0.82f, -0.12f, 0.05f, 0.02f, -360, 720, -362},
			{0.93f, -0.03f, 0.01f, 0.01f, -360, 720, -360},
			{1, 0, 0, 0, -360, 720, -360},
		}),
		// Flip knife: the blade flips forward around the grip, then it is shown and turned back.
		FLIP("Flip knife", 3.6f, -0.28f, -0.30f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.12f, -0.12f, 0.06f, 0.03f, 0, -10, 8},
			{0.30f, -0.13f, 0.07f, 0.03f, -360, -10, 8},
			{0.45f, -0.18f, 0.09f, 0.05f, -365, -62, 18},
			{0.60f, -0.18f, 0.09f, 0.05f, -366, -66, 18},
			{0.76f, -0.12f, 0.06f, 0.03f, -360, 30, -15},
			{0.92f, -0.03f, 0.01f, 0.01f, -360, 4, -2},
			{1, 0, 0, 0, -360, 0, 0},
		}),
		// M9 Bayonet: grip change and twist to show the blade, then a quick horizontal flip.
		M9("M9 Bayonet", 4.0f, -0.10f, -0.15f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.14f, -0.16f, 0.07f, 0.04f, -8, -30, 12},
			{0.32f, -0.19f, 0.09f, 0.05f, -4, -75, 20},
			{0.42f, -0.19f, 0.09f, 0.05f, -4, -78, 20},
			{0.54f, -0.18f, 0.12f, 0.05f, 0, 285, 10},
			{0.70f, -0.18f, 0.10f, 0.05f, 4, 290, -12},
			{0.88f, -0.05f, 0.02f, 0.01f, 0, 352, -2},
			{1, 0, 0, 0, 0, 360, 0},
		}),
		// Bayonet: roll to show the edge, turn it round, finish with a spin in the hand.
		BAYONET("Bayonet", 3.8f, -0.10f, -0.15f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.15f, -0.16f, 0.07f, 0.04f, -6, -35, 22},
			{0.35f, -0.18f, 0.09f, 0.05f, -4, -62, 30},
			{0.55f, -0.17f, 0.09f, 0.05f, 6, 118, -18},
			{0.70f, -0.16f, 0.08f, 0.04f, 4, 120, -20},
			{0.84f, -0.08f, 0.05f, 0.02f, 0, 10, -200},
			{0.94f, -0.02f, 0.01f, 0.00f, 0, 0, -350},
			{1, 0, 0, 0, 0, 0, -360},
		}),
		// Falchion: spins flat in the palm with short pauses between them.
		FALCHION("Falchion", 4.2f, -0.12f, -0.12f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.12f, -0.14f, 0.06f, 0.03f, 0, 0, 10},
			{0.24f, -0.15f, 0.07f, 0.03f, 0, 0, -350},
			{0.30f, -0.15f, 0.07f, 0.03f, 0, 0, -362},
			{0.40f, -0.15f, 0.07f, 0.03f, 0, 0, -715},
			{0.50f, -0.18f, 0.09f, 0.05f, 10, -40, -722},
			{0.62f, -0.15f, 0.07f, 0.03f, 0, 0, -1075},
			{0.76f, -0.16f, 0.08f, 0.04f, -6, 58, -1082},
			{0.92f, -0.03f, 0.01f, 0.01f, 0, 4, -1080},
			{1, 0, 0, 0, 0, 0, -1080},
		}),
		// Talon: a slower reverse-grip spin around the finger with a wrist roll.
		TALON("Talon", 4.4f, -0.28f, -0.30f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.15f, -0.12f, 0.06f, 0.03f, 0, 0, 30},
			{0.36f, -0.13f, 0.07f, 0.03f, 0, -20, -330},
			{0.50f, -0.18f, 0.09f, 0.05f, 20, -70, -360},
			{0.65f, -0.18f, 0.10f, 0.05f, 22, -74, -362},
			{0.80f, -0.12f, 0.06f, 0.03f, -4, 22, -350},
			{0.93f, -0.03f, 0.01f, 0.01f, 0, 2, -360},
			{1, 0, 0, 0, 0, 0, -360},
		}),
		// Bowie: turns the tip towards you to admire it, then rolls it on round.
		BOWIE("Bowie", 3.8f, -0.05f, -0.15f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.15f, -0.14f, 0.06f, 0.04f, -6, 40, 6},
			{0.35f, -0.18f, 0.09f, 0.10f, -20, 160, 10},
			{0.55f, -0.18f, 0.10f, 0.10f, -16, 166, 4},
			{0.70f, -0.16f, 0.08f, 0.06f, 0, 205, 20},
			{0.88f, -0.05f, 0.02f, 0.01f, 0, 340, 4},
			{1, 0, 0, 0, 0, 360, 0},
		}),
		// Stiletto: flick, toss with a spin, catch, show.
		STILETTO("Stiletto", 3.6f, -0.15f, -0.15f, new float[][] {
			{0, 0, 0, 0, 0, 0, 0},
			{0.12f, -0.13f, 0.06f, 0.03f, 0, 0, 8},
			{0.22f, -0.14f, 0.07f, 0.03f, 0, 0, -90},
			{0.38f, -0.14f, 0.28f, 0.03f, 0, 0, -450},
			{0.50f, -0.14f, 0.05f, 0.03f, 0, 0, -722},
			{0.65f, -0.18f, 0.09f, 0.05f, 8, -70, -728},
			{0.80f, -0.17f, 0.09f, 0.05f, 6, -72, -726},
			{0.93f, -0.03f, 0.01f, 0.01f, 0, -4, -720},
			{1, 0, 0, 0, 0, 0, -720},
		});

		public final String label;
		/** How long the inspect takes in CS, seconds (at speed 1). */
		public final float seconds;
		/** Grip position in the item's own space: spins turn around it. */
		public final float pivotX, pivotY;
		final float[][] keys;

		Anim(String label, float seconds, float pivotX, float pivotY, float[][] keys) {
			this.label = label;
			this.seconds = seconds;
			this.pivotX = pivotX;
			this.pivotY = pivotY;
			this.keys = keys;
		}

		/** Pose at progress p (0..1) into out = {x, y, z, pitch, yaw, roll}. */
		public void sample(float p, float[] out) {
			float[][] k = keys;
			p = Math.max(0f, Math.min(1f, p));
			int i = 0;
			while (i < k.length - 2 && p > k[i + 1][0]) i++;
			float[] a = k[Math.max(0, i - 1)], b = k[i], c = k[i + 1], d = k[Math.min(k.length - 1, i + 2)];
			float span = c[0] - b[0];
			float u = span <= 0 ? 0 : (p - b[0]) / span;
			for (int ch = 0; ch < 6; ch++) out[ch] = catmullRom(a[ch + 1], b[ch + 1], c[ch + 1], d[ch + 1], u);
		}
	}

	private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
		float t2 = t * t, t3 = t2 * t;
		return 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}

	/** Startup check: every inspect starts and ends at rest, keys are in time order, and the curve passes through its keys. */
	public static void selfCheck() {
		float[] o = new float[6];
		for (Anim a : Anim.values()) {
			for (int i = 1; i < a.keys.length; i++) check(a.keys[i][0] > a.keys[i - 1][0], a + " keys out of order");
			check(a.keys[0][0] == 0f && a.keys[a.keys.length - 1][0] == 1f, a + " must span 0..1");
			a.sample(0f, o);
			for (float v : o) check(Math.abs(v) < 1e-4f, a + " doesn't start at rest");
			a.sample(1f, o);
			for (int ch = 0; ch < 3; ch++) check(Math.abs(o[ch]) < 1e-4f, a + " doesn't end at rest");
			for (int ch = 3; ch < 6; ch++) check(Math.abs(o[ch] % 360f) < 1e-3f, a + " ends off a whole turn");
			float[] mid = a.keys[a.keys.length / 2];
			a.sample(mid[0], o);
			check(Math.abs(o[5] - mid[6]) < 1e-2f, a + " curve misses its key");
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new IllegalStateException("KnifeInspects self-check failed: " + what);
	}
}
