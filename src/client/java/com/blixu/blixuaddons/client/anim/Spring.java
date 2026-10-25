package com.blixu.blixuaddons.client.anim;

/** Critically-damped-ish spring for values that should chase a target with a little life (camera, relics). */
public final class Spring {
	public float value;
	public float velocity;

	public Spring(float initial) {
		this.value = initial;
	}

	/** {@code stiffness} ~ 40..300, {@code damping} ~ 2*sqrt(stiffness) for no overshoot. */
	public float update(float target, float dt, float stiffness, float damping) {
		// Sub-step so big frame hitches don't explode the spring.
		int steps = Math.max(1, (int) Math.ceil(dt / (1f / 120f)));
		float h = dt / steps;
		for (int i = 0; i < steps; i++) {
			float accel = (target - value) * stiffness - velocity * damping;
			velocity += accel * h;
			value += velocity * h;
		}
		return value;
	}

	public void snap(float v) {
		value = v;
		velocity = 0f;
	}
}
