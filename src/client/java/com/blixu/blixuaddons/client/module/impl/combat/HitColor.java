package com.blixu.blixuaddons.client.module.impl.combat;

import com.blixu.blixuaddons.client.anim.Easing;
import com.blixu.blixuaddons.client.feature.CombatEvents;
import com.blixu.blixuaddons.client.feature.EntityMaterial;
import com.blixu.blixuaddons.client.feature.EntityVisuals;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the red damage flash with configurable styles. Triggered by the client's own
 * damage event for that entity; damage, invulnerability and hits are untouched.
 */
public class HitColor extends Module {
	public enum Style {
		CLASSIC("Classic red"), WHITE("White flash"), CUSTOM("Custom colour"), GRADIENT("Two-colour gradient"),
		PULSE("Soft pulse"), EDGE("Edge highlight"), GLOW("Brief glow");
		public final String label;
		Style(String l) { label = l; }
	}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.CUSTOM, s -> s.label));
	public final ColorSetting primary = add(new ColorSetting("primary", "Primary colour", 0xFF3B5C));
	public final ColorSetting secondary = add(new ColorSetting("secondary", "Secondary colour", 0xFACC15));
	public final com.blixu.blixuaddons.client.feature.GlowStyle.Look glowLook = com.blixu.blixuaddons.client.feature.GlowStyle.addLook(this::add, com.blixu.blixuaddons.client.feature.GlowStyle.HIT, () -> true).withDesign(this::add, () -> true, 1.0);
	public final NumberSetting duration = add(new NumberSetting("duration", "Duration (s)", 0.45, 0.1, 1.5, 0.05));
	public final EnumSetting<Easing> fade = add(new EnumSetting<>("fade", "Fade curve", Easing.EASE_OUT, e -> e.label));
	public final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 0.85, 0.05, 1, 0.05));
	public final NumberSetting intensity = add(new NumberSetting("intensity", "Intensity", 1, 0.1, 1, 0.05));
	public final NumberSetting glowRadius = add(new NumberSetting("shell_radius", "Shell glow size", 0.06, 0.01, 0.2, 0.01));
	public final BoolSetting players = add(new BoolSetting("players", "Players", true));
	public final BoolSetting hostile = add(new BoolSetting("hostile", "Hostile mobs", true));
	public final BoolSetting passive = add(new BoolSetting("passive", "Passive mobs", true));
	public final BoolSetting other = add(new BoolSetting("other", "Other", true));
	public final BoolSetting self = add(new BoolSetting("self", "Yourself (third person)", true));

	public HitColor() {
		super("hit_color", "Hit Color",
			"Custom damage flash: colours, gradients, pulse, edge highlight or glow.",
			Category.COMBAT, "damage", false);
	}

	private boolean enabledFor(LivingEntity e) {
		if (EntityVisuals.isSelf(e)) return self.get();
		return switch (EntityVisuals.kind(e)) {
			case PLAYER -> players.get();
			case HOSTILE -> hostile.get();
			case PASSIVE -> passive.get();
			case OTHER -> other.get();
		};
	}

	/** 0..1 remaining flash strength for this entity, 0 when not flashing. */
	private float amount(LivingEntity e) {
		float age = CombatEvents.secondsSinceHurt(e.getId());
		float d = duration.getF();
		if (age < 0 || age > d) return 0f;
		float p = age / d;
		float k = 1f - fade.get().apply(p);
		if (style.get() == Style.PULSE) k *= 0.5f + 0.5f * Mth.cos(p * Mth.PI * 4f);
		return Mth.clamp(k * intensity.getF() * opacity.getF(), 0f, 1f);
	}

	private int colorNow(LivingEntity e) {
		float age = CombatEvents.secondsSinceHurt(e.getId());
		float p = Mth.clamp(age / duration.getF(), 0f, 1f);
		return switch (style.get()) {
			case CLASSIC -> 0xFFFF2A2A;
			case WHITE -> 0xFFFFFFFF;
			case GRADIENT -> Colors.lerp(primary.argb(), secondary.argb(), p);
			default -> primary.argb();
		};
	}

	public void apply(LivingEntity e, EntityMaterial m) {
		if (!enabledFor(e)) return;
		// Our flash replaces vanilla's red tint for the whole hurt time.
		if (e.hurtTime > 0 || CombatEvents.secondsSinceHurt(e.getId()) >= 0 && CombatEvents.secondsSinceHurt(e.getId()) < duration.getF()) {
			m.suppressRedOverlay = true;
		}
		float a = amount(e);
		if (a <= 0f) return;
		m.hitColor = colorNow(e);
		switch (style.get()) {
			case EDGE -> m.hit = a * 0.25f; // mostly the outline (see edgeColor), plus a faint tint
			case GLOW -> {
				m.hit = a * 0.5f;
				m.hitShell = true;
				m.shellScale = glowRadius.getF();
			}
			default -> m.hit = a;
		}
	}

	/** Edge style uses the existing outline glow for the flash duration. */
	public int edgeColor(LivingEntity e) {
		if (style.get() != Style.EDGE || !enabledFor(e)) return 0;
		return amount(e) > 0.02f ? colorNow(e) | 0xFF000000 : 0;
	}

	/** Simple 2D stand-in character for menu previews. */
	public static void figure(net.minecraft.client.gui.GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		com.blixu.blixuaddons.client.render.Ui.rect(g, cx - 4 * s, cy - 16 * s, 8 * s, 8 * s, 1.5f * s, color);
		com.blixu.blixuaddons.client.render.Ui.rect(g, cx - 5 * s, cy - 7.5f * s, 10 * s, 12 * s, 1.5f * s, color);
		com.blixu.blixuaddons.client.render.Ui.rect(g, cx - 8.5f * s, cy - 7 * s, 3 * s, 11 * s, 1.2f * s, color);
		com.blixu.blixuaddons.client.render.Ui.rect(g, cx + 5.5f * s, cy - 7 * s, 3 * s, 11 * s, 1.2f * s, color);
		com.blixu.blixuaddons.client.render.Ui.rect(g, cx - 4.5f * s, cy + 5 * s, 4 * s, 12 * s, 1.2f * s, color);
		com.blixu.blixuaddons.client.render.Ui.rect(g, cx + 0.5f * s, cy + 5 * s, 4 * s, 12 * s, 1.2f * s, color);
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		float cycle = duration.getF() + 0.6f;
		float age = time % cycle;
		float d = duration.getF();
		float a = 0f;
		int c = primary.argb();
		if (age < d) {
			float p = age / d;
			a = 1f - fade.get().apply(p);
			if (style.get() == Style.PULSE) a *= 0.5f + 0.5f * net.minecraft.util.Mth.cos(p * net.minecraft.util.Mth.PI * 4f);
			a = net.minecraft.util.Mth.clamp(a * intensity.getF() * opacity.getF(), 0f, 1f);
			c = switch (style.get()) {
				case CLASSIC -> 0xFFFF2A2A;
				case WHITE -> 0xFFFFFFFF;
				case GRADIENT -> com.blixu.blixuaddons.client.style.Colors.lerp(primary.argb(), secondary.argb(), p);
				default -> primary.argb();
			};
		}
		float cx = x + w / 2, cy = y + h / 2 + 6;
		if (style.get() == Style.GLOW && a > 0) com.blixu.blixuaddons.client.render.Ui.shadow(g, cx - 12, cy - 18, 24, 36, 8, 6 + glowRadius.getF() * 80, com.blixu.blixuaddons.client.style.Colors.withAlpha(c, a * 0.7f));
		int body = com.blixu.blixuaddons.client.style.Colors.lerp(0xFF5C6378, c | 0xFF000000, style.get() == Style.EDGE ? a * 0.25f : a);
		figure(g, cx, cy, 1.6f, body);
		if (style.get() == Style.EDGE && a > 0) com.blixu.blixuaddons.client.render.Ui.outline(g, cx - 15, cy - 27, 30, 56, 4, 1.5f, com.blixu.blixuaddons.client.style.Colors.withAlpha(c, a));
	}
}
