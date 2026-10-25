package com.blixu.blixuaddons.client.render;

/** Small flat/3D shapes used by particles, trails, relics and markers. */
public enum Shape {
	SQUARE("Square"),
	CIRCLE("Circle"),
	SOFT("Soft Glow"),
	RING("Ring"),
	STAR("Star"),
	HEART("Heart"),
	DIAMOND("Diamond"),
	TRIANGLE("Triangle"),
	CUBE("Cube"),
	SHARD("Shard"),
	SPARK("Spark");

	public final String label;

	Shape(String label) {
		this.label = label;
	}
}
