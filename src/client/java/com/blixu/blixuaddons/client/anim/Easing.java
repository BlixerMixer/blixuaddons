package com.blixu.blixuaddons.client.anim;

/** Easing curves, t in 0..1 → 0..1 (Back/Elastic/Spring overshoot a little on purpose). */
public enum Easing {
	LINEAR("Linear"),
	EASE_IN("Ease In"),
	EASE_OUT("Ease Out"),
	EASE_IN_OUT("Ease In-Out"),
	SMOOTHSTEP("Smoothstep"),
	CUBIC_IN("Cubic In"),
	CUBIC_OUT("Cubic Out"),
	CUBIC_IN_OUT("Cubic In-Out"),
	BACK_OUT("Back Out"),
	ELASTIC_OUT("Elastic Out"),
	SPRING("Spring");

	public final String label;

	Easing(String label) {
		this.label = label;
	}

	public float apply(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return switch (this) {
			case LINEAR -> t;
			case EASE_IN -> t * t;
			case EASE_OUT -> 1 - (1 - t) * (1 - t);
			case EASE_IN_OUT -> t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
			case SMOOTHSTEP -> t * t * (3 - 2 * t);
			case CUBIC_IN -> t * t * t;
			case CUBIC_OUT -> 1 - (float) Math.pow(1 - t, 3);
			case CUBIC_IN_OUT -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
			case BACK_OUT -> {
				float c1 = 1.70158f, c3 = c1 + 1;
				yield 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
			}
			case ELASTIC_OUT -> t == 0 || t == 1 ? t
				: (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
			case SPRING -> 1 - (float) (Math.cos(t * Math.PI * 2.5) * Math.exp(-6 * t));
		};
	}

	/** 0 → 1 → 0 bell, eased. Handy for "out and back" motions (swings, inspections). */
	public float pingPong(float t) {
		return t < 0.5f ? apply(t * 2) : apply((1 - t) * 2);
	}
}
