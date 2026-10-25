package com.blixu.blixuaddons.client.module.impl.player;

import com.blixu.blixuaddons.client.feature.GlowStyle;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * Your own character in the Hand Aura designs (Nebula, Plasma, Flame, Galaxy): a band
 * around you and a fill on you, drawn by the shared outline glow pass (post/glow_starfield) under
 * the SELF look. In first person the body isn't drawn, so the camera and held items are never covered.
 */
public class SelfAura extends Module {
	public final BoolSetting thirdPersonOnly = add(new BoolSetting("third_person_only", "Only in third person", true));
	public final com.blixu.blixuaddons.client.module.setting.SectionSetting glowHeader = add(new com.blixu.blixuaddons.client.module.setting.SectionSetting(
		"glow_header", "Glow around you", "Soft band and edge stroke; strength 0 turns the band off"));
	public final ColorSetting outlineColor = add(new ColorSetting("outline_color", "Glow colour", 0xD13BFF));
	public final GlowStyle.Look glowLook = GlowStyle.addLook(this::add, GlowStyle.SELF, () -> true, 0.5, "Fill on your character").withDesign(this::add, () -> true, 1.0, "aura_glow_radius");

	public SelfAura() {
		super("self_aura", "Self Aura & Fill",
			"The Hand Aura designs on your own character: Nebula, Plasma, Flame or Galaxy, in your colours.",
			Category.COSMETICS, "player", false);
	}

	private boolean shown() {
		return !thirdPersonOnly.get() || !Minecraft.getInstance().options.getCameraType().isFirstPerson();
	}

	/** Post outline colour (0 = none); EntityVisuals tags it with the SELF look id. */
	public int outlineColor(@org.jspecify.annotations.Nullable LivingEntity self) {
		if (!shown()) return 0;
		return Colors.setAlpha(outlineColor.argb(), 1f);
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		float cx = x + w / 2, bottom = y + h - 16;
		com.blixu.blixuaddons.client.render.GlowPreview.paint(g, glowLook, outlineColor.argb(), time,
			com.blixu.blixuaddons.client.render.GlowPreview.mob(cx, bottom, 1.65f), 0xFF8A6A50);
		com.blixu.blixuaddons.client.render.Ui.text(g, glowLook.fillDesign.get().label, x + 6, y + h - 11, 0xFF9097AB, com.blixu.blixuaddons.client.render.UiFont.SMALL);
	}
}
