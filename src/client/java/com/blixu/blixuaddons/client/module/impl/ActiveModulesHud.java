package com.blixu.blixuaddons.client.module.impl;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;

/** The list of active features in a corner of the screen. Drawing is in hud/HudOverlay. */
public class ActiveModulesHud extends Module {
	public final BoolSetting background = add(new BoolSetting("background", "Background", true));
	public final BoolSetting accentBar = add(new BoolSetting("accent_bar", "Accent bar", true));
	public final BoolSetting rightSide = add(new BoolSetting("right_side", "Right side of screen", true));
	/** -1 = hug the screen edge of the chosen side; otherwise the x (% of screen) the list lines up on. */
	public final com.blixu.blixuaddons.client.module.setting.NumberSetting posX = add(new com.blixu.blixuaddons.client.module.setting.NumberSetting(
		"pos_x", "Position X (% of screen, -1 = screen edge)", -1, -1, 100, 0.5));
	public final com.blixu.blixuaddons.client.module.setting.NumberSetting posY = add(new com.blixu.blixuaddons.client.module.setting.NumberSetting(
		"pos_y", "Position Y (% of screen)", 0, 0, 95, 0.5));
	public final com.blixu.blixuaddons.client.module.setting.NumberSetting size = add(new com.blixu.blixuaddons.client.module.setting.NumberSetting(
		"size", "Size", 1, 0.5, 2.5, 0.05));

	/** The x the list lines up on: its right edge on the right side, its left edge on the left. */
	public float edgeX(float sw) {
		if (posX.get() < 0) return rightSide.get() ? sw - 4 : 4;
		return Math.max(4, Math.min(sw - 4, sw * posX.getF() / 100f));
	}

	public ActiveModulesHud() {
		super("active_modules_hud", "Active Modules HUD",
			"Shows what's switched on in a corner of the screen.",
			Category.HUD, "list", true);
		posX.visibleWhen(() -> false);
		posY.visibleWhen(() -> false);
		size.visibleWhen(() -> false);
	}

	@Override
	public java.util.List<com.blixu.blixuaddons.client.hud.HudElement> hudElements() {
		return java.util.List.of(new com.blixu.blixuaddons.client.hud.HudElement() {
			public String name() { return "Active Modules"; }
			public boolean shown() { return isEnabled(); }
			public float[] box(float sw, float sh) {
				int n = 0, wMax = 40;
				for (Module m : com.blixu.blixuaddons.client.module.ModuleManager.all()) {
					if (m == ActiveModulesHud.this || !m.isEnabled()) continue;
					n++;
					wMax = Math.max(wMax, com.blixu.blixuaddons.client.render.Ui.width(m.name(), com.blixu.blixuaddons.client.render.UiFont.BODY));
				}
				float k = size.getF(), w = (wMax + 14) * k, h = Math.max(12, n * 14) * k, edge = edgeX(sw);
				return new float[] {rightSide.get() ? edge - w : edge, 4 + sh * posY.getF() / 100f, w, h};
			}
			public void moveTo(float x, float y, float sw, float sh) {
				float[] b = box(sw, sh);
				boolean right = x + b[2] / 2 > sw / 2;
				if (right != rightSide.get()) rightSide.set(right);
				posX.set((double) ((right ? x + b[2] : x) / sw * 100));
				posY.set((double) Math.max(0, (y - 4) / sh * 100));
			}
			public void resize(float steps) { size.set(size.get() + steps * 0.05); }
		});
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		String[] names = {"Hit Particles", "Atmosphere", "Freelook"};
		float ry = y + 18;
		for (String n : names) {
			float tw = com.blixu.blixuaddons.client.render.Ui.width(n, com.blixu.blixuaddons.client.render.UiFont.BODY) + 10;
			float rx = rightSide.get() ? x + w - 8 - tw : x + 8;
			if (background.get()) com.blixu.blixuaddons.client.hud.HudPanel.draw(g, rx, ry, tw, 12, 0, 1f, false);
			if (accentBar.get()) com.blixu.blixuaddons.client.render.Ui.rect(g, rightSide.get() ? x + w - 6 : x + 5, ry + 1, 2, 10, 1, 0xFF8B5CF6);
			com.blixu.blixuaddons.client.render.Ui.text(g, n, rx + 5, ry + 2, 0xFFECEEF5, com.blixu.blixuaddons.client.render.UiFont.BODY);
			ry += 14;
		}
	}
}
