package com.blixu.blixuaddons.client.anim;

/** One shared per-frame delta time (seconds), clamped so pauses/hitches don't make effects jump. */
public final class FrameClock {
	private FrameClock() {}

	private static long last = System.nanoTime();
	private static float dt = 1f / 60f;
	private static long frame;

	/** Called once at the start of each rendered world frame. */
	public static void tick() {
		long now = System.nanoTime();
		dt = Math.min((now - last) / 1_000_000_000f, 0.1f);
		last = now;
		frame++;
	}

	public static float dt() { return dt; }
	public static long frame() { return frame; }

	/** Exponential smoothing step factor, frame-rate independent. */
	public static float smoothing(float speed) {
		return 1f - (float) Math.exp(-speed * dt);
	}
}
