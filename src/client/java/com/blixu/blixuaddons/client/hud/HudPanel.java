package com.blixu.blixuaddons.client.hud;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.hud.HudStyle;
import com.blixu.blixuaddons.client.render.Ui;
import com.blixu.blixuaddons.client.render.UiFont;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws a panel in the shared HUD style (shadow, glow, frosted/plain fill, border, hover/active). */
public final class HudPanel {
	private HudPanel() {}

	/**
	 * @param highlight 0..1 hover/active amount (brightens the fill)
	 * @param fade      0..1 overall opacity (for slide-in animations)
	 * @param blurOk    whether this panel's blur toggle is on
	 */
	public static void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float highlight, float fade, boolean blurOk) {
		HudStyle s = style();
		if (!s.isEnabled()) {
			// Module off: the original simple look.
			Ui.rect(g, x, y, w, h, 3, Colors.withAlpha(com.blixu.blixuaddons.client.gui.Theme.HUD_BG, fade));
			return;
		}
		float r = s.radius.getF();
		if (s.shadow.get()) Ui.shadow(g, x, y + 1.5f, w, h, r, 6, Colors.withAlpha(0x90000000, fade));

		float glow = s.glowIntensity.getF();
		if (s.glowAnimated.get()) glow *= 0.65f + 0.35f * (float) Math.sin(Colors.seconds() * Math.PI * s.glowSpeed.getF());
		if (glow > 0.01f) {
			float soft = s.glowEdge.get() == HudStyle.Edge.SOFT ? 4 + 10 * glow : 1.5f + 2 * glow;
			Ui.shadow(g, x, y, w, h, r, soft, Colors.withAlpha(s.glowColor.argb(), Math.min(1f, glow) * fade));
		}

		int bg = Colors.setAlpha(s.background.argb(), s.backgroundOpacity.getF() * fade);
		if (highlight > 0.001f) bg = Colors.lerp(bg, Colors.setAlpha(0xFFFFFFFF, Colors.a(bg) / 255f), s.hoverBoost.getF() * highlight);
		if (s.blur.get() && blurOk && fade > 0.98f) {
			// Frosted: tint alpha decides how much colour covers the blur.
			int tint = s.frosted.get() ? bg : Colors.setAlpha(bg, 0.25f);
			Ui.frosted(g, x, y, w, h, r, tint);
		} else {
			Ui.rect(g, x, y, w, h, r, bg);
		}

		float bt = s.borderThickness.getF();
		if (bt > 0.01f) {
			int border = Colors.withAlpha(s.borderGlow.get() ? s.glowColor.argb() : s.border.argb(), fade);
			if (s.borderGlow.get()) Ui.shadow(g, x, y, w, h, r, 2.5f, Colors.withAlpha(s.glowColor.argb(), 0.6f * fade));
			Ui.outline(g, x, y, w, h, r, bt, border);
		}
	}

	/** Panel text, with the shared text glow if enabled. */
	public static void text(GuiGraphicsExtractor g, String text, float x, float y, int color, UiFont font) {
		HudStyle s = style();
		if (s.isEnabled() && s.textGlow.get()) Ui.glowText(g, text, x, y, color, s.glowColor.argb(), s.textGlowStrength.getF(), font);
		else Ui.text(g, text, x, y, color, font);
	}

	public static HudStyle style() {
		return ModuleManager.get(HudStyle.class);
	}
}
