package com.blixu.blixuaddons.client.module.impl.hud;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;

/**
 * One look for every HUD panel (active modules, target panel, labels, the menu window).
 * Drawing lives in hud/HudPanel; the blur itself is rendered once per frame in render/PostFx.
 */
public class HudStyle extends Module {
	public enum Edge { SOFT("Soft"), CRISP("Crisp");
		public final String label;
		Edge(String l) { label = l; }
	}

	public final ColorSetting background = add(new ColorSetting("background", "Background", 0x101218));
	public final NumberSetting backgroundOpacity = add(new NumberSetting("background_opacity", "Background opacity", 0.7, 0, 1, 0.05));
	public final ColorSetting border = add(new ColorSetting("border", "Border colour", 0x2A2E3D));
	public final NumberSetting borderThickness = add(new NumberSetting("border_thickness", "Border thickness", 0.75, 0, 3, 0.25));
	public final NumberSetting radius = add(new NumberSetting("radius", "Corner radius", 4, 0, 12, 0.5));
	public final NumberSetting padding = add(new NumberSetting("padding", "Padding", 5, 2, 12, 0.5));
	public final BoolSetting shadow = add(new BoolSetting("shadow", "Shadow", true));

	public final BoolSetting blur = add(new BoolSetting("blur", "Background blur", true));
	public final NumberSetting blurRadius = add(new NumberSetting("blur_radius", "Blur radius", 8, 1, 24, 1)
		.visibleWhen(blur::get));
	public final BoolSetting frosted = add(new BoolSetting("frosted", "Frosted glass", true).visibleWhen(blur::get));
	public final BoolSetting blurModules = add(new BoolSetting("blur_modules", "Blur: active modules", true).visibleWhen(blur::get));
	public final BoolSetting blurTarget = add(new BoolSetting("blur_target", "Blur: target panel", true).visibleWhen(blur::get));
	public final BoolSetting blurMenu = add(new BoolSetting("blur_menu", "Blur: menu window", true).visibleWhen(blur::get));

	public final ColorSetting glowColor = add(new ColorSetting("glow_color", "Glow colour", 0x8B5CF6));
	public final NumberSetting glowIntensity = add(new NumberSetting("glow_intensity", "Panel glow", 0.35, 0, 1, 0.05));
	public final EnumSetting<Edge> glowEdge = add(new EnumSetting<>("glow_edge", "Glow edge", Edge.SOFT, e -> e.label));
	public final BoolSetting glowAnimated = add(new BoolSetting("glow_animated", "Animated glow", false));
	public final NumberSetting glowSpeed = add(new NumberSetting("glow_speed", "Glow speed", 1, 0.1, 4, 0.1).visibleWhen(glowAnimated::get));
	public final BoolSetting borderGlow = add(new BoolSetting("border_glow", "Border glow", false));
	public final BoolSetting textGlow = add(new BoolSetting("text_glow", "Text glow", false));
	public final NumberSetting textGlowStrength = add(new NumberSetting("text_glow_strength", "Text glow strength", 0.6, 0, 1, 0.05)
		.visibleWhen(textGlow::get));
	public final ColorSetting accent = add(new ColorSetting("accent", "Accent colour", 0x8B5CF6));

	public final NumberSetting animDuration = add(new NumberSetting("anim_duration", "Animation duration", 0.15, 0.02, 0.6, 0.01));
	public final NumberSetting hoverBoost = add(new NumberSetting("hover_boost", "Hover / active highlight", 0.08, 0, 0.3, 0.01));

	public HudStyle() {
		super("hud_style", "HUD Blur & Glow",
			"Frosted blur, glow and panel style shared by every HUD panel and the menu.",
			Category.HUD, "hud", true);
	}

	/** Smoothing speed matching the configured animation duration (≈ 95% done after that time). */
	public float animSpeed() {
		return 3f / animDuration.getF();
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		com.blixu.blixuaddons.client.render.Ui.gradientV(g, x + 1, y + 1, w - 2, h - 2, 6, 0xFF4A7AC0, 0xFF5E9A3C);
		float px = x + 18, py = y + 24, pw = w - 36, ph = 40;
		com.blixu.blixuaddons.client.hud.HudPanel.draw(g, px, py, pw, ph, 0.5f + 0.5f * net.minecraft.util.Mth.sin(time * 1.5f), 1f, true);
		com.blixu.blixuaddons.client.hud.HudPanel.text(g, "Sample panel", px + padding.getF(), py + padding.getF(), 0xFFECEEF5, com.blixu.blixuaddons.client.render.UiFont.SEMIBOLD);
		com.blixu.blixuaddons.client.render.Ui.text(g, "Hover / blur / glow", px + padding.getF(), py + padding.getF() + 12, 0xFF9097AB, com.blixu.blixuaddons.client.render.UiFont.SMALL);
		com.blixu.blixuaddons.client.render.Ui.rect(g, px + padding.getF(), py + ph - padding.getF() - 3, (pw - padding.getF() * 2) * (0.5f + 0.5f * net.minecraft.util.Mth.sin(time)), 3, 1.5f, accent.argb());
	}
}
