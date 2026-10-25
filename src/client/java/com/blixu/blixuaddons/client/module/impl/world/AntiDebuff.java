package com.blixu.blixuaddons.client.module.impl.world;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.ActionSetting;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;

/**
 * Tones down disruptive screen effects. Only the local presentation changes: status
 * effects, their durations and every server mechanic stay exactly as they are.
 * Each slider is "how much to remove" (1 = fully hidden, 0 = vanilla).
 */
public class AntiDebuff extends Module {
	public enum Preset { MINIMAL("Minimal distraction"), VANILLA("Vanilla look"), CUSTOM("Custom");
		public final String label;
		Preset(String l) { label = l; }
	}

	public final EnumSetting<Preset> preset = add(new EnumSetting<>("preset", "Preset", Preset.MINIMAL, p -> p.label));
	public final NumberSetting nausea = add(new NumberSetting("nausea", "Nausea distortion", 1, 0, 1, 0.05));
	public final NumberSetting darkness = add(new NumberSetting("darkness", "Darkness effect", 1, 0, 1, 0.05));
	public final NumberSetting blindness = add(new NumberSetting("blindness", "Blindness fog", 0.85, 0, 1, 0.05));
	public final NumberSetting fire = add(new NumberSetting("fire", "Fire overlay", 0.6, 0, 1, 0.05));
	public final NumberSetting fireLower = add(new NumberSetting("fire_lower", "Fire overlay: lower by", 0.25, 0, 0.6, 0.01));
	public final BoolSetting fireTint = add(new BoolSetting("fire_tint", "Custom fire colour", false));
	public final ColorSetting fireColor = add(new ColorSetting("fire_color", "Fire colour", 0x22D3EE).visibleWhen(fireTint::get));
	public final NumberSetting water = add(new NumberSetting("water", "Water overlay", 1, 0, 1, 0.05));
	public final NumberSetting portal = add(new NumberSetting("portal", "Portal distortion", 1, 0, 1, 0.05));
	public final NumberSetting pumpkin = add(new NumberSetting("pumpkin", "Pumpkin / helmet overlay", 0.7, 0, 1, 0.05));
	public final NumberSetting freeze = add(new NumberSetting("freeze", "Powder-snow frost overlay", 0.5, 0, 1, 0.05));
	public final NumberSetting inWall = add(new NumberSetting("in_wall", "In-block (suffocation) overlay", 0.5, 0, 1, 0.05));
	public final NumberSetting potionParticles = add(new NumberSetting("potion_particles", "Potion effect particles", 0.8, 0, 1, 0.05));
	public final NumberSetting hurtShake = add(new NumberSetting("hurt_shake", "Hurt camera tilt", 0, 0, 1, 0.05));
	public final NumberSetting bobbing = add(new NumberSetting("bobbing", "Camera bobbing", 0, 0, 1, 0.05));
	public final ActionSetting test = add(new ActionSetting("test", "Test (5 s of debuffs, client-side)",
		() -> this.testTicks > 0 ? "Testing… " + (this.testTicks / 20 + 1) + "s" : "Test", this::startTest));

	private static final java.util.List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> TEST_EFFECTS = java.util.List.of(
		net.minecraft.world.effect.MobEffects.NAUSEA, net.minecraft.world.effect.MobEffects.DARKNESS, net.minecraft.world.effect.MobEffects.BLINDNESS);
	private final java.util.List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> added = new java.util.ArrayList<>();
	private int testTicks;

	public AntiDebuff() {
		super("anti_debuff", "Anti-Debuff Visuals",
			"Reduce nausea, darkness, blindness, fire, water, portal and other screen clutter. Visual only.",
			Category.WORLD, "shield", false);
		preset.onChange(this::applyPreset);
	}

	private void applyPreset(Preset p) {
		double[] v = switch (p) {
			case MINIMAL -> new double[] {1, 1, 0.85, 0.6, 0.25, 1, 1, 0.7, 0.5, 0.5, 0.8, 0.5, 0.3};
			case VANILLA -> new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
			case CUSTOM -> null;
		};
		if (v == null) return;
		NumberSetting[] s = {nausea, darkness, blindness, fire, fireLower, water, portal, pumpkin, freeze, inWall, potionParticles, hurtShake, bobbing};
		for (int i = 0; i < s.length; i++) s[i].setSilently(v[i]);
		ConfigManager.save();
	}

	/** Puts nausea, darkness, blindness and fire on your own client for 5 s so you can judge the sliders. The server never sees it. */
	private void startTest() {
		var p = net.minecraft.client.Minecraft.getInstance().player;
		if (p == null || testTicks > 0 || !isEnabled()) return; // onTick (which ends the test) only runs while enabled
		for (var e : TEST_EFFECTS) {
			if (p.hasEffect(e)) continue; // a real effect stays untouched
			p.forceAddEffect(new net.minecraft.world.effect.MobEffectInstance(e, 120, 0), null);
			added.add(e);
		}
		testTicks = 100;
	}

	private void stopTest(net.minecraft.client.player.LocalPlayer p) {
		testTicks = 0;
		if (p != null) {
			for (var e : added) p.removeEffectNoUpdate(e);
			p.setSharedFlagOnFire(false);
		}
		added.clear();
	}

	@Override
	public void onTick(net.minecraft.client.Minecraft client) {
		if (testTicks <= 0 || client.player == null) return;
		client.player.setSharedFlagOnFire(true);
		if (--testTicks == 0) stopTest(client.player);
	}

	@Override
	protected void onDisable() {
		if (testTicks > 0) stopTest(net.minecraft.client.Minecraft.getInstance().player);
	}

	@Override
	public void onWorldChange() {
		added.clear();
		testTicks = 0;
	}

	/** Multiplier to apply to an effect's strength: 1 - suppression. */
	public static float keep(NumberSetting s) {
		return 1f - s.getF();
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		// Left: vanilla, right: with your settings. Fire at the bottom, water / portal tint over it.
		float half = (w - 14) / 2f;
		for (int side = 0; side < 2; side++) {
			float bx = x + 6 + side * (half + 2), by = y + 16, bh = h - 30;
			com.blixu.blixuaddons.client.render.Ui.rect(g, bx, by, half, bh, 3, 0xFF5E9A3C);
			com.blixu.blixuaddons.client.render.Ui.gradientV(g, bx, by, half, bh * 0.45f, 3, 0xFF7FA8FF, 0xFFB8D0FF);
			float fire = side == 0 ? 1f : keep(this.fire);
			float lower = side == 0 ? 0f : fireLower.getF();
			int fc = side == 1 && fireTint.get() ? fireColor.argb() : 0xFFFF8A1A;
			if (fire > 0.01f) {
				float fh = bh * (0.55f - lower * 0.6f);
				for (int i = 0; i < 6; i++) {
					float fw = half / 6f;
					float flick = 0.8f + 0.2f * net.minecraft.util.Mth.sin(time * 9 + i * 2);
					com.blixu.blixuaddons.client.render.Ui.gradientV(g, bx + i * fw, by + bh - fh * flick, fw + 0.5f, fh * flick, 2, com.blixu.blixuaddons.client.style.Colors.withAlpha(fc, 0f), com.blixu.blixuaddons.client.style.Colors.withAlpha(fc, 0.85f * fire));
				}
			}
			float water = side == 0 ? 1f : keep(this.water);
			com.blixu.blixuaddons.client.render.Ui.rect(g, bx, by, half, bh, 3, com.blixu.blixuaddons.client.style.Colors.withAlpha(0xFF1F4FB5, 0.25f * water));
			float portal = side == 0 ? 1f : keep(this.portal);
			com.blixu.blixuaddons.client.render.Ui.rect(g, bx, by, half, bh, 3, com.blixu.blixuaddons.client.style.Colors.withAlpha(0xFF8B2BD6, 0.18f * portal * (0.6f + 0.4f * net.minecraft.util.Mth.sin(time * 2))));
		}
		com.blixu.blixuaddons.client.render.Ui.text(g, "Vanilla", x + 6, y + h - 11, 0xFF9097AB, com.blixu.blixuaddons.client.render.UiFont.SMALL);
		com.blixu.blixuaddons.client.render.Ui.text(g, "Yours", x + w - 6 - com.blixu.blixuaddons.client.render.Ui.width("Yours", com.blixu.blixuaddons.client.render.UiFont.SMALL), y + h - 11, 0xFFECEEF5, com.blixu.blixuaddons.client.render.UiFont.SMALL);
	}
}
