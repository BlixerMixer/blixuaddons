package com.blixu.blixuaddons.client.anim;

/**
 * A 0..1 progress clock with duration, easing, pause/resume and cancel.
 * Driven by real time (delta seconds), so it looks the same at any frame rate.
 */
public final class Tween {
	private float elapsed;
	private float duration = 1f;
	private boolean running;
	private boolean paused;

	public void start(float durationSeconds) {
		this.duration = Math.max(0.01f, durationSeconds);
		this.elapsed = 0f;
		this.running = true;
		this.paused = false;
	}

	public void update(float dt) {
		if (!running || paused) return;
		elapsed += dt;
		if (elapsed >= duration) {
			elapsed = duration;
			running = false;
		}
	}

	public void pause() { paused = true; }
	public void resume() { paused = false; }

	public void cancel() {
		running = false;
		elapsed = 0f;
	}

	public boolean running() { return running; }

	/** Raw progress 0..1 (1 once finished, 0 when cancelled/never started). */
	public float progress() {
		return elapsed / duration;
	}

	public float eased(Easing e) {
		return e.apply(progress());
	}
}
