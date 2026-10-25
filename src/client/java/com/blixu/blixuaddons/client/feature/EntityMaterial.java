package com.blixu.blixuaddons.client.feature;

/**
 * How an entity should be drawn this frame. Filled by Self Aura / Hit Color and
 * applied in EntityVisuals (render type + tint) and MaterialConsumer (per-vertex effects).
 * One instance lives on each render state and is reused every frame.
 */
public final class EntityMaterial {
	public enum Fill { NONE, TEXTURED_TINT, FLAT, SOLID, GRADIENT, HOLOGRAPHIC }

	public enum GradientDir { VERTICAL, HORIZONTAL, DIAGONAL }

	public Fill fill = Fill.NONE;
	public int color1 = -1;
	public int color2 = -1;
	public GradientDir gradientDir = GradientDir.VERTICAL;
	/** 0..1 alpha for translucent fills. */
	public float alpha = 1f;
	public float brightness = 1f;
	public boolean emissive;
	/** Draw the model as edge strips (wireframe) instead of filled faces. */
	public boolean wire;
	public float wireWidth = 0.06f;
	/** Additive shell slightly larger than the model (soft glow halo). */
	public boolean shell;
	public float shellScale = 0.06f;
	public int shellColor = 0x60FFFFFF;
	/** Enchant-style glint over the model (metallic look). */
	public boolean glint;
	/** Animated scanlines for holographic looks (moving speed). */
	public float scanSpeed;
	/** Post outline (the existing glow styles), gated by line of sight by the caller. */
	public boolean outline;
	public int outlineColor = 0xFFFFFFFF;

	// Hit flash, layered on top of whatever else is set.
	public float hit;
	public int hitColor = 0xFFFF0000;
	public boolean hitShell;
	public boolean suppressRedOverlay;

	// Geometry info for per-vertex effects (camera-relative, filled at extract time).
	public float baseY;
	public float height = 1.8f;
	public float centerX;
	public float centerZ;
	public float width = 0.6f;

	public void clear() {
		fill = Fill.NONE;
		color1 = color2 = -1;
		gradientDir = GradientDir.VERTICAL;
		alpha = 1f;
		brightness = 1f;
		emissive = false;
		wire = false;
		shell = false;
		glint = false;
		scanSpeed = 0f;
		outline = false;
		hit = 0f;
		hitShell = false;
		suppressRedOverlay = false;
	}

	public boolean active() {
		return fill != Fill.NONE || wire || shell || glint || hit > 0f || suppressRedOverlay;
	}

	/** True when MaterialConsumer must touch vertices. */
	public boolean perVertex() {
		return wire || fill == Fill.GRADIENT || fill == Fill.HOLOGRAPHIC;
	}
}
