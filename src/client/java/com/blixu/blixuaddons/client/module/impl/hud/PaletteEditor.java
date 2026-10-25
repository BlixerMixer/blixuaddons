package com.blixu.blixuaddons.client.module.impl.hud;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.ActionSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.StringSetting;
import com.blixu.blixuaddons.client.style.Palettes;

/**
 * The central palette editor. Any colour setting can be linked to a palette slot
 * (in its colour picker); editing the palette here updates every linked module.
 */
public class PaletteEditor extends Module {
	public enum Slot {
		P1, P2, P3, P4, P5, P6
	}

	private static final String[] NAMES = {"Neon", "Ocean", "Sunset", "Toxic", "Frost", "Mono"};
	private static final int[][] DEFAULTS = {
		{0xD13BFF, 0x8B5CF6, 0x22D3EE},
		{0x0EA5E9, 0x22D3EE, 0x34F59A},
		{0xFF7A1A, 0xFF3B5C, 0xFACC15},
		{0x34F59A, 0xA3E635, 0xFACC15},
		{0xE0F2FE, 0x7DD3FC, 0xA78BFA},
		{0xFFFFFF, 0x9097AB, 0x3A3F52},
	};

	public final EnumSetting<Slot> editing = add(new EnumSetting<>("editing", "Editing", Slot.P1, s -> Palettes.name(s.ordinal())));
	private final StringSetting[] names = new StringSetting[Palettes.COUNT];
	private final ColorSetting[][] colors = new ColorSetting[Palettes.COUNT][3];
	public final ColorSetting themeAccent;

	public PaletteEditor() {
		super("palettes", "Color Palettes",
			"Named palettes every colour setting can link to, plus the theme accent.",
			Category.HUD, "palette", true);
		for (int i = 0; i < Palettes.COUNT; i++) {
			final int idx = i;
			names[i] = add(new StringSetting("name_" + i, "Palette name", NAMES[i], 16)
				.visibleWhen(() -> editing.get().ordinal() == idx));
			for (int s = 0; s < 3; s++) {
				colors[i][s] = add(new ColorSetting("color_" + i + "_" + s, Palettes.SLOT_NAMES[s], DEFAULTS[i][s],
					ColorSetting.DEFAULT_SWATCHES, false).visibleWhen(() -> editing.get().ordinal() == idx));
			}
			add(new ActionSetting("reset_" + i, "Reset this palette", "Reset", () -> {
				names[idx].reset();
				for (ColorSetting c : colors[idx]) c.reset();
			}).visibleWhen(() -> editing.get().ordinal() == idx));
		}
		themeAccent = add(new ColorSetting("theme_accent", "Theme accent", 0x8B5CF6, ColorSetting.DEFAULT_SWATCHES, false));
	}

	public ColorSetting[] colors(int index) {
		return colors[Math.floorMod(index, Palettes.COUNT)];
	}

	public String name(int index) {
		return names[Math.floorMod(index, Palettes.COUNT)].get();
	}
}
