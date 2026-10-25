package com.blixu.blixuaddons.client.module.impl;

import com.blixu.blixuaddons.client.feature.GlowStyle;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;

/** Outline through walls. Drawing lives in feature/MobGlow + the post_effect shaders. */
public class MobHighlight extends Module {

	public final com.blixu.blixuaddons.client.module.setting.StringSetting names = add(new com.blixu.blixuaddons.client.module.setting.StringSetting(
		"names", "Mob names (comma separated)", "Enderman", 512));
	public final com.blixu.blixuaddons.client.module.setting.NumberSetting range = add(new com.blixu.blixuaddons.client.module.setting.NumberSetting(
		"range", "Max distance", 48, 8, 128, 1));
	public final com.blixu.blixuaddons.client.module.setting.SectionSetting glowHeader = add(new com.blixu.blixuaddons.client.module.setting.SectionSetting(
		"glow_header", "Glow around the mob", "Soft band and edge stroke; strength 0 turns the band off"));
	public final ColorSetting color = add(new ColorSetting("color", "Glow colour", 0xD13BFF));
	public final GlowStyle.Look look = GlowStyle.addLook(this::add, GlowStyle.MOB, () -> true, 0.35, "Fill amount").withMobFill(this::add, () -> true);

	public MobHighlight() {
		super("mob_highlight", "Mob Highlight",
			"Glow mobs whose name or the name tag above them matches your list. Visible through walls.",
			Category.VISUALS, "target", true);
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		float cx = x + w / 2, bottom = y + h - 16;
		String first = names.get().split(",")[0].trim();
		if (!first.isEmpty()) {
			String tag = "[Lv12] " + first;
			float tw = com.blixu.blixuaddons.client.render.Ui.width(tag, com.blixu.blixuaddons.client.render.UiFont.SMALL) + 6;
			com.blixu.blixuaddons.client.render.Ui.rect(g, cx - tw / 2, y + 14, tw, 10, 2, 0x80000000);
			com.blixu.blixuaddons.client.render.Ui.textCentered(g, tag, cx, y + 15.5f, 0xFFECEEF5, com.blixu.blixuaddons.client.render.UiFont.SMALL);
		}
		com.blixu.blixuaddons.client.render.GlowPreview.paint(g, look, color.argb(), time,
			com.blixu.blixuaddons.client.render.GlowPreview.mob(cx, bottom, 1.65f), 0xFF141414);
		com.blixu.blixuaddons.client.render.Ui.text(g, look.fillDesign.label() + " fill", x + 6, y + h - 11, 0xFF9097AB, com.blixu.blixuaddons.client.render.UiFont.SMALL);
	}

	@Override
	public void onTick(net.minecraft.client.Minecraft mc) {
		com.blixu.blixuaddons.client.feature.MobGlow.refresh(mc, this);
	}

	@Override
	public void onWorldChange() {
		com.blixu.blixuaddons.client.feature.MobGlow.clear();
	}
}
