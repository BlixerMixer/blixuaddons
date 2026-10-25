package com.blixu.blixuaddons.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Mth;

/** A number shown as a slider. */
public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;

	public NumberSetting(String id, String name, double defaultValue, double min, double max, double step) {
		super(id, name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	@Override
	public void set(Double v) {
		double snapped = step > 0 ? Math.round(v / step) * step : v;
		super.set(Mth.clamp(snapped, min, max));
	}

	public double min() { return min; }
	public double max() { return max; }
	public double step() { return step; }

	public double fraction() { return (value - min) / (max - min); }

	public String display() {
		if (step >= 1) return String.valueOf(Math.round(value));
		return step >= 0.1 ? String.format("%.1f", value) : String.format("%.2f", value);
	}

	public float getF() { return value.floatValue(); }
	public int getI() { return (int) Math.round(value); }

	@Override public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement json) {
		try {
			value = Mth.clamp(json.getAsDouble(), min, max);
		} catch (RuntimeException ignored) {
		}
	}
}
