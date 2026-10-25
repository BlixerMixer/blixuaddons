package com.blixu.blixuaddons.client.module.impl.player;

import com.blixu.blixuaddons.client.anim.Easing;
import com.blixu.blixuaddons.client.anim.Tween;
import com.blixu.blixuaddons.client.feature.ItemCategory;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * First-person swing styles. Runs on its own cosmetic clock started when you swing,
 * so the look can be longer/shorter than vanilla — attack timing, cooldown, packets
 * and hit registration are never touched.
 */
public class SwingAnimations extends Module {
	public enum Style {
		CLASSIC("Classic"), SMOOTH_SLASH("Smooth Slash"), FAST_SLASH("Fast Slash"), SPIN("Spin"), REVERSE_SLASH("Reverse Slash"),
		HOOK("Hook"), PUSH("Push"), DOUBLE("Double Motion"), MINIMAL("Minimal"), CUSTOM("Custom");
		public final String label;
		Style(String l) { label = l; }
	}

	public enum RotAxis { X("Pitch (X)"), Y("Yaw (Y)"), Z("Roll (Z)");
		public final String label;
		RotAxis(String l) { label = l; }
	}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.SMOOTH_SLASH, s -> s.label));
	public final BoolSetting perItem = add(new BoolSetting("per_item", "Per-item styles", false));
	public final EnumSetting<Style> swordStyle = add(new EnumSetting<Style>("sword_style", "Sword style", Style.SMOOTH_SLASH, s -> s.label).visibleWhen(perItem::get));
	public final EnumSetting<Style> toolStyle = add(new EnumSetting<Style>("tool_style", "Tool style", Style.CLASSIC, s -> s.label).visibleWhen(perItem::get));
	public final EnumSetting<Style> otherStyle = add(new EnumSetting<Style>("other_style", "Other items style", Style.MINIMAL, s -> s.label).visibleWhen(perItem::get));
	public final NumberSetting duration = add(new NumberSetting("duration", "Duration (s)", 0.3, 0.08, 1.2, 0.01));
	public final EnumSetting<Easing> easing = add(new EnumSetting<>("easing", "Easing", Easing.CUBIC_OUT, e -> e.label));
	public final NumberSetting angle = add(new NumberSetting("angle", "Swing angle", 80, 0, 180, 1));
	public final EnumSetting<RotAxis> axis = add(new EnumSetting<>("axis", "Spin axis", RotAxis.X, a -> a.label));
	public final NumberSetting horizontal = add(new NumberSetting("horizontal", "Horizontal movement", 1, -2, 2, 0.05));
	public final NumberSetting vertical = add(new NumberSetting("vertical", "Vertical movement", 1, -2, 2, 0.05));
	public final NumberSetting forward = add(new NumberSetting("forward", "Forward displacement", 1, -2, 2, 0.05));
	public final NumberSetting scalePulse = add(new NumberSetting("scale_pulse", "Scale during swing", 0, -0.5, 0.5, 0.01));
	public final BoolSetting useRotation = add(new BoolSetting("use_rotation", "Component: rotation", true));
	public final BoolSetting useTranslation = add(new BoolSetting("use_translation", "Component: movement", true));
	public final BoolSetting useScale = add(new BoolSetting("use_scale", "Component: scale", true));
	public final BoolSetting restartOnSpam = add(new BoolSetting("restart", "Restart on every click", false));

	public final BoolSetting afterimage = add(new BoolSetting("afterimage", "Afterimage", false));
	public final NumberSetting afterimageCount = add(new NumberSetting("afterimage_count", "Afterimage copies", 2, 1, 4, 1).visibleWhen(afterimage::get));
	public final NumberSetting afterimageLag = add(new NumberSetting("afterimage_lag", "Afterimage spacing", 0.08, 0.02, 0.25, 0.01).visibleWhen(afterimage::get));
	public final BoolSetting trail = add(new BoolSetting("trail", "Motion trail", true));
	public final ColorSetting trailColor = add(new ColorSetting("trail_color", "Trail colour", 0xA78BFA).visibleWhen(trail::get));
	public final ColorSetting trailColor2 = add(new ColorSetting("trail_color2", "Trail end colour", 0x22D3EE).visibleWhen(trail::get));
	public final NumberSetting trailLength = add(new NumberSetting("trail_length", "Trail length", 14, 4, 32, 1).visibleWhen(trail::get));
	public final NumberSetting trailWidth = add(new NumberSetting("trail_width", "Trail width", 0.06, 0.01, 0.2, 0.005).visibleWhen(trail::get));
	public final ColorSetting glowColor = add(new ColorSetting("glow_color", "Swing glow colour", 0xD13BFF));
	public final NumberSetting glow = add(new NumberSetting("glow", "Swing glow", 0, 0, 1, 0.05));

	// Custom keyframes: rest → K1 → K2 → rest
	public final NumberSetting k1Time = add(new NumberSetting("k1_time", "Key 1 time", 0.3, 0.05, 0.9, 0.01).visibleWhen(this::custom));
	public final NumberSetting k1Pitch = add(new NumberSetting("k1_pitch", "Key 1 pitch", -70, -180, 180, 1).visibleWhen(this::custom));
	public final NumberSetting k1Yaw = add(new NumberSetting("k1_yaw", "Key 1 yaw", -20, -180, 180, 1).visibleWhen(this::custom));
	public final NumberSetting k1Roll = add(new NumberSetting("k1_roll", "Key 1 roll", -30, -180, 180, 1).visibleWhen(this::custom));
	public final NumberSetting k1X = add(new NumberSetting("k1_x", "Key 1 X", -0.2, -1, 1, 0.01).visibleWhen(this::custom));
	public final NumberSetting k1Y = add(new NumberSetting("k1_y", "Key 1 Y", 0.1, -1, 1, 0.01).visibleWhen(this::custom));
	public final NumberSetting k1Z = add(new NumberSetting("k1_z", "Key 1 Z", -0.2, -1, 1, 0.01).visibleWhen(this::custom));
	public final NumberSetting k2Time = add(new NumberSetting("k2_time", "Key 2 time", 0.65, 0.1, 0.95, 0.01).visibleWhen(this::custom));
	public final NumberSetting k2Pitch = add(new NumberSetting("k2_pitch", "Key 2 pitch", -20, -180, 180, 1).visibleWhen(this::custom));
	public final NumberSetting k2Yaw = add(new NumberSetting("k2_yaw", "Key 2 yaw", 30, -180, 180, 1).visibleWhen(this::custom));
	public final NumberSetting k2Roll = add(new NumberSetting("k2_roll", "Key 2 roll", 10, -180, 180, 1).visibleWhen(this::custom));
	public final NumberSetting k2X = add(new NumberSetting("k2_x", "Key 2 X", 0.05, -1, 1, 0.01).visibleWhen(this::custom));
	public final NumberSetting k2Y = add(new NumberSetting("k2_y", "Key 2 Y", 0, -1, 1, 0.01).visibleWhen(this::custom));
	public final NumberSetting k2Z = add(new NumberSetting("k2_z", "Key 2 Z", -0.1, -1, 1, 0.01).visibleWhen(this::custom));

	private final Tween clock = new Tween();
	private final float[] out = new float[7];

	public SwingAnimations() {
		super("swing_animations", "Swing Animations",
			"Ten first-person swing styles with timing, easing, trail and afterimage. Visual only.",
			Category.PLAYER, "swing", false);
	}

	private boolean custom() {
		return style.get() == Style.CUSTOM || perItem.get() && (swordStyle.get() == Style.CUSTOM || toolStyle.get() == Style.CUSTOM || otherStyle.get() == Style.CUSTOM);
	}

	/** The local player started a swing (client-side hook on LivingEntity.swing). */
	public void onSwing() {
		if (!clock.running() || restartOnSpam.get() || clock.progress() > 0.45f) clock.start(duration.getF());
	}

	public void update(float dt) {
		clock.update(dt);
	}

	public boolean swinging() {
		if (com.blixu.blixuaddons.client.feature.FirstPersonFx.previewing(this)) return previewProgress() < 1f;
		return clock.running();
	}

	public float progress() {
		if (com.blixu.blixuaddons.client.feature.FirstPersonFx.previewing(this)) return Math.min(1f, previewProgress());
		return clock.running() ? clock.progress() : 0f;
	}

	/** Menu preview: one swing, a short pause, repeat. */
	private float previewProgress() {
		float cycle = duration.getF() + 0.45f;
		return (com.blixu.blixuaddons.client.feature.FirstPersonFx.previewTime() % cycle) / duration.getF();
	}

	@Override
	protected void onDisable() {
		clock.cancel();
	}

	public Style styleFor(ItemCategory cat) {
		if (!perItem.get()) return style.get();
		return switch (cat) {
			case SWORD -> swordStyle.get();
			case TOOL -> toolStyle.get();
			default -> otherStyle.get();
		};
	}

	/**
	 * Applies the swing at raw progress {@code p} (0..1) to {@code pose}. Returns false for
	 * CLASSIC so the caller runs vanilla's swing with remapped timing instead.
	 */
	public boolean apply(PoseStack pose, Style s, float p, int invert) {
		if (s == Style.CLASSIC) return false;
		float[] o = evaluate(s, p);
		if (useTranslation.get()) pose.translate(invert * o[0] * horizontal.getF(), o[1] * vertical.getF(), o[2] * forward.getF());
		if (useRotation.get()) {
			// Pivot like vanilla: rotate in a frame turned 45° towards the screen centre.
			pose.mulPose(Axis.YP.rotationDegrees(invert * 45f));
			pose.mulPose(Axis.YP.rotationDegrees(invert * o[4]));
			pose.mulPose(Axis.ZP.rotationDegrees(invert * o[5]));
			pose.mulPose(Axis.XP.rotationDegrees(o[3]));
			pose.mulPose(Axis.YP.rotationDegrees(invert * -45f));
		}
		if (useScale.get() && o[6] != 1f) pose.scale(o[6], o[6], o[6]);
		return true;
	}

	/** Eased classic timing (fed to vanilla's swing for the CLASSIC style). */
	public float classicAttack(float p) {
		return easing.get().apply(p);
	}

	/** tx, ty, tz, pitch, yaw, roll, scale — all zero/one at p = 0 and p = 1. */
	private float[] evaluate(Style s, float p) {
		float e = easing.get().apply(p);
		float a = angle.getF();
		float b = Mth.sin(e * Mth.PI);
		float tx = 0, ty = 0, tz = 0, rx = 0, ry = 0, rz = 0;
		switch (s) {
			case SMOOTH_SLASH -> {
				tx = -0.28f * b; ty = 0.12f * Mth.sin(e * Mth.TWO_PI); tz = -0.16f * b;
				rx = -a * 0.85f * b; ry = -a * 0.25f * b; rz = -a * 0.45f * b;
			}
			case FAST_SLASH -> {
				float q = Mth.sqrt(e);
				float fb = Mth.sin(q * Mth.PI);
				tx = -0.16f * fb; tz = -0.1f * fb;
				rx = -a * 0.65f * fb; rz = -a * 0.2f * fb;
			}
			case SPIN -> {
				float turn = -360f * e * Math.max(0.25f, a / 80f);
				switch (axis.get()) {
					case X -> rx = turn;
					case Y -> ry = turn;
					case Z -> rz = turn;
				}
				tz = -0.08f * b;
			}
			case REVERSE_SLASH -> {
				tx = 0.22f * b; ty = 0.1f * Mth.sin(e * Mth.TWO_PI); tz = -0.16f * b;
				rx = -a * 0.75f * b; ry = a * 0.25f * b; rz = a * 0.5f * b;
			}
			case HOOK -> {
				tx = -0.32f * b; tz = -0.1f * b; ty = 0.04f * b;
				ry = -a * 0.9f * b; rz = -a * 0.15f * b;
			}
			case PUSH -> {
				tz = -0.38f * b; ty = 0.03f * b; rx = -a * 0.12f * b;
			}
			case DOUBLE -> {
				float half = e < 0.5f ? e * 2 : (e - 0.5f) * 2;
				float db = Mth.sin(half * Mth.PI);
				float side = e < 0.5f ? 1 : -1;
				tx = -0.2f * db * side; tz = -0.14f * db;
				rx = -a * 0.7f * db; rz = -a * 0.4f * db * side;
			}
			case MINIMAL -> {
				rx = -a * 0.25f * b; tz = -0.05f * b; ty = 0.02f * b;
			}
			case CUSTOM -> {
				float t1 = k1Time.getF(), t2 = Math.max(t1 + 0.01f, k2Time.getF());
				float[] k1 = {k1X.getF(), k1Y.getF(), k1Z.getF(), k1Pitch.getF(), k1Yaw.getF(), k1Roll.getF()};
				float[] k2 = {k2X.getF(), k2Y.getF(), k2Z.getF(), k2Pitch.getF(), k2Yaw.getF(), k2Roll.getF()};
				float[] v = new float[6];
				for (int i = 0; i < 6; i++) {
					if (e < t1) v[i] = Mth.lerp(Easing.SMOOTHSTEP.apply(e / t1), 0, k1[i]);
					else if (e < t2) v[i] = Mth.lerp(Easing.SMOOTHSTEP.apply((e - t1) / (t2 - t1)), k1[i], k2[i]);
					else v[i] = Mth.lerp(Easing.SMOOTHSTEP.apply((e - t2) / (1 - t2)), k2[i], 0);
				}
				tx = v[0]; ty = v[1]; tz = v[2]; rx = v[3]; ry = v[4]; rz = v[5];
			}
			case CLASSIC -> { }
		}
		out[0] = tx; out[1] = ty; out[2] = tz; out[3] = rx; out[4] = ry; out[5] = rz;
		out[6] = 1f + scalePulse.getF() * b;
		return out;
	}

	// Created on first use: item components aren't ready while modules are constructed.
	private static net.minecraft.world.item.ItemStack previewItem;

	/** Draws the preview sword (16 px icon) centred at (cx, cy) with rotation (degrees) and scale. */
	public static void previewItem(net.minecraft.client.gui.GuiGraphicsExtractor g, float cx, float cy, float rotDeg, float sx, float sy) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(rotDeg * net.minecraft.util.Mth.DEG_TO_RAD);
		g.pose().scale(sx, sy);
		if (previewItem == null) previewItem = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
		g.item(previewItem, -8, -8);
		g.pose().popMatrix();
	}

	@Override
	public Preview preview() {
		return Preview.FIRST_PERSON;
	}
}
