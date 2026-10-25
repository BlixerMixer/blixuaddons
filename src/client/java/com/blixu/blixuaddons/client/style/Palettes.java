package com.blixu.blixuaddons.client.style;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.hud.PaletteEditor;

/** Shared named palettes (primary / secondary / accent). Edited in the Palettes module. */
public final class Palettes {
	private Palettes() {}

	public static final int COUNT = 6;
	public static final String[] SLOT_NAMES = {"Primary", "Secondary", "Accent"};

	public static int color(int index, int slot, float phase) {
		PaletteEditor p = editor();
		return p == null ? 0xFFFFFFFF : p.colors(index)[slot].argb(phase);
	}

	public static int[] stops(int index) {
		PaletteEditor p = editor();
		if (p == null) return new int[] {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
		var c = p.colors(index);
		return new int[] {c[0].argb(), c[1].argb(), c[2].argb()};
	}

	public static String name(int index) {
		PaletteEditor p = editor();
		return p == null ? "Palette " + (index + 1) : p.name(index);
	}

	public static int accent(float phase) {
		PaletteEditor p = editor();
		return p == null ? 0xFF8B5CF6 : p.themeAccent.argb(phase);
	}

	private static PaletteEditor editor() {
		return ModuleManager.find(PaletteEditor.class);
	}
}
