package com.blixu.blixuaddons.client.module.impl;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;

/** Alt-tab without the game pausing or opening the Esc menu (skips vanilla's pause-on-lost-focus). */
public class AlwaysFocused extends Module {
	public AlwaysFocused() {
		super("always_focused", "Always Focused",
			"Alt-tab away without the game pausing or opening the Esc menu.",
			Category.QOL, "qol", true);
	}
}
