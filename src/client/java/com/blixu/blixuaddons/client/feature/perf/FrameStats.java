package com.blixu.blixuaddons.client.feature.perf;

import java.util.Arrays;

/**
 * Low-overhead frame measurements for the diagnostics panel and auto quality: a ring of the last
 * 240 frame times plus per-frame counters. Recording is a few stores per frame; the sorted
 * percentiles are only computed when someone asks (the panel, 4 times a second).
 */
public final class FrameStats {
	private FrameStats() {}

	private static final int N = 240;
	private static final float[] RING = new float[N];
	private static final float[] SORT = new float[N];
	private static int head, filled;
	private static long lastFrame;

	// per-frame counters, rolled into the "last" values at the start of each frame
	private static int considered, culled, rays;
	public static int lastConsidered, lastCulled, lastRays;
	/** Nanoseconds our own world visuals took last frame (module updates + batch submission). */
	public static long visualsNanos, hudNanos;
	/** Non-empty world batches we submitted last frame. */
	public static int lastBatches;

	/** Called once per rendered world frame. */
	public static void frame() {
		long now = System.nanoTime();
		if (lastFrame != 0) {
			float ms = (now - lastFrame) / 1e6f;
			if (ms < 1000f) { // skip pauses (menus, loading)
				RING[head] = ms;
				head = (head + 1) % N;
				filled = Math.min(N, filled + 1);
			}
		}
		lastFrame = now;
		lastConsidered = considered;
		lastCulled = culled;
		lastRays = rays;
		considered = culled = rays = 0;
	}

	public static void entity(boolean wasCulled) {
		considered++;
		if (wasCulled) culled++;
	}

	public static void rays(int n) { rays += n; }

	public static int samples() { return filled; }

	/** Mean frame time (ms) over the last {@code frames} frames. */
	public static float averageMs(int frames) {
		int n = Math.min(frames, filled);
		if (n == 0) return 0f;
		float sum = 0f;
		for (int i = 1; i <= n; i++) sum += RING[Math.floorMod(head - i, N)];
		return sum / n;
	}

	/** Frame time (ms) that {@code p} (0..1) of the recent frames were faster than. */
	public static float percentileMs(float p) {
		if (filled == 0) return 0f;
		System.arraycopy(RING, 0, SORT, 0, filled);
		Arrays.sort(SORT, 0, filled);
		return SORT[Math.min(filled - 1, (int) (p * (filled - 1) + 0.5f))];
	}

	public static void reset() {
		head = filled = 0;
		lastFrame = 0;
	}
}
