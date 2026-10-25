package com.blixu.blixuaddons.client.module.setting;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.style.Colors;
import com.blixu.blixuaddons.client.style.Palettes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * A colour. Stored as 0xAARRGGBB plus a mode: a fixed colour, an animated rainbow
 * (keeping the picked colour's saturation/brightness), a slot of a shared palette
 * (edit the palette once and every linked setting follows), or the theme accent.
 * Old configs that saved "#RRGGBB" still load.
 */
public class ColorSetting extends Setting<Integer> {
	public static final int[] DEFAULT_SWATCHES = {
		0xD13BFF, // neon magenta
		0x8B5CF6, // violet
		0x22D3EE, // cyan
		0x34F59A, // mint
		0xFACC15, // yellow
		0xFF7A1A, // orange
		0xFF3B5C, // red
		0xFFFFFF  // white
	};

	public enum Mode { STATIC, RAINBOW, PALETTE, THEME }

	private final int[] swatches;
	private final boolean allowLinks;
	private Mode mode = Mode.STATIC;
	private int paletteIndex;
	private int paletteSlot;
	private float rainbowSpeed = 0.25f;

	public ColorSetting(String id, String name, int defaultRgb) {
		this(id, name, defaultRgb, DEFAULT_SWATCHES);
	}

	public ColorSetting(String id, String name, int defaultRgb, int[] swatches) {
		this(id, name, defaultRgb, swatches, true);
	}

	/** {@code allowLinks = false} for the palette colours themselves (no palette → palette loops). */
	public ColorSetting(String id, String name, int defaultColor, int[] swatches, boolean allowLinks) {
		super(id, name, (defaultColor >>> 24) == 0 ? 0xFF000000 | defaultColor : defaultColor);
		this.swatches = swatches;
		this.allowLinks = allowLinks;
	}

	public int[] swatches() { return swatches; }
	public boolean allowLinks() { return allowLinks; }
	public Mode mode() { return mode; }
	public int paletteIndex() { return paletteIndex; }
	public int paletteSlot() { return paletteSlot; }
	public float rainbowSpeed() { return rainbowSpeed; }

	public void setMode(Mode mode) {
		this.mode = allowLinks || mode == Mode.STATIC || mode == Mode.RAINBOW ? mode : Mode.STATIC;
		ConfigManager.save();
	}

	public void linkPalette(int index, int slot) {
		this.paletteIndex = Math.floorMod(index, Palettes.COUNT);
		this.paletteSlot = Math.floorMod(slot, 3);
		setMode(Mode.PALETTE);
	}

	public void setRainbowSpeed(float speed) {
		this.rainbowSpeed = Math.max(0.01f, Math.min(speed, 3f));
		ConfigManager.save();
	}

	/** The colour to draw with right now (ARGB). */
	public int argb() {
		return argb(0f);
	}

	/** Like {@link #argb()}, with {@code phase} (0..1) shifting rainbow hue — for gradients along trails. */
	public int argb(float phase) {
		return switch (mode) {
			case STATIC -> value;
			case RAINBOW -> Colors.rainbow(value, rainbowSpeed, phase);
			case PALETTE -> Colors.withAlpha(Palettes.color(paletteIndex, paletteSlot, phase), (value >>> 24) / 255f);
			case THEME -> Colors.withAlpha(Palettes.accent(phase), (value >>> 24) / 255f);
		};
	}

	@Override
	public void reset() {
		mode = Mode.STATIC;
		super.reset();
	}

	@Override
	public JsonElement toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("color", String.format("#%08X", value));
		o.addProperty("mode", mode.name());
		o.addProperty("palette", paletteIndex);
		o.addProperty("slot", paletteSlot);
		o.addProperty("speed", rainbowSpeed);
		return o;
	}

	@Override
	public void fromJson(JsonElement json) {
		try {
			if (json instanceof JsonPrimitive) {
				value = parse(json.getAsString());
				return;
			}
			JsonObject o = json.getAsJsonObject();
			if (o.has("color")) value = parse(o.get("color").getAsString());
			if (o.has("mode")) {
				for (Mode m : Mode.values()) if (m.name().equals(o.get("mode").getAsString())) mode = m;
				if (!allowLinks && (mode == Mode.PALETTE || mode == Mode.THEME)) mode = Mode.STATIC;
			}
			if (o.has("palette")) paletteIndex = Math.floorMod(o.get("palette").getAsInt(), Palettes.COUNT);
			if (o.has("slot")) paletteSlot = Math.floorMod(o.get("slot").getAsInt(), 3);
			if (o.has("speed")) rainbowSpeed = Math.max(0.01f, Math.min(o.get("speed").getAsFloat(), 3f));
		} catch (RuntimeException ignored) {
		}
	}

	private static int parse(String s) {
		String hex = s.replace("#", "").trim();
		long v = Long.parseLong(hex, 16);
		return hex.length() <= 6 ? 0xFF000000 | (int) v : (int) v;
	}
}
