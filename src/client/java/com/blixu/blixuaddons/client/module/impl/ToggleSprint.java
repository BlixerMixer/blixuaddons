package com.blixu.blixuaddons.client.module.impl;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import net.minecraft.client.Minecraft;

/** Keeps the sprint key held for you. Minecraft still decides when you're allowed to sprint. */
public class ToggleSprint extends Module {
	public ToggleSprint() {
		super("toggle_sprint", "Toggle Sprint",
			"Sprint without holding the key.",
			Category.QOL, "sprint", false);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player != null && client.gui.screen() == null) {
			client.options.keySprint.setDown(true);
		}
	}

	@Override
	protected void onDisable() {
		Minecraft.getInstance().options.keySprint.setDown(false);
	}
}
