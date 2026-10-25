package com.blixu.blixuaddons.client.module.impl.player;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.feature.ItemCategory;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.ActionSetting;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;

/**
 * First-person hand/item pose editor: one pose (position, rotation, scale) for every item, per hand.
 * Only the first-person hand pass is transformed: third person, inventory and the item's real
 * behaviour are untouched.
 */
public class HandPose extends Module {
	public enum Side { RIGHT("Right hand"), LEFT("Left hand");
		public final String label;
		Side(String l) { label = l; }
	}

	public enum Preset {
		CLASSIC("Classic"), COMPACT("Compact"), LOWERED("Lowered"), CENTERED("Centered"), ANGLED("Angled"), CUSTOM("Custom");
		public final String label;
		Preset(String l) { label = l; }
	}

	/** One editable pose, shared by all items in that hand. */
	public final class Profile {
		final NumberSetting x, y, z, pitch, yaw, roll, scale, sx, sy, sz;

		Profile(Side side) {
			String p = "all_" + side.name().toLowerCase() + "_";
			java.util.function.BooleanSupplier vis = () -> editSide.get() == side && !(side == Side.LEFT && mirrorLeft.get());
			x = add(new NumberSetting(p + "x", "Offset X", 0, -1, 1, 0.01).visibleWhen(vis));
			y = add(new NumberSetting(p + "y", "Offset Y", 0, -1, 1, 0.01).visibleWhen(vis));
			z = add(new NumberSetting(p + "z", "Offset Z", 0, -1, 1, 0.01).visibleWhen(vis));
			pitch = add(new NumberSetting(p + "pitch", "Pitch", 0, -180, 180, 1).visibleWhen(vis));
			yaw = add(new NumberSetting(p + "yaw", "Yaw", 0, -180, 180, 1).visibleWhen(vis));
			roll = add(new NumberSetting(p + "roll", "Roll", 0, -180, 180, 1).visibleWhen(vis));
			scale = add(new NumberSetting(p + "scale", "Scale (all items)", 1, 0.1, 2.5, 0.01).visibleWhen(vis));
			sx = add(new NumberSetting(p + "sx", "Width", 1, 0.1, 2.5, 0.01).visibleWhen(vis));
			sy = add(new NumberSetting(p + "sy", "Height", 1, 0.1, 2.5, 0.01).visibleWhen(vis));
			sz = add(new NumberSetting(p + "sz", "Depth", 1, 0.1, 2.5, 0.01).visibleWhen(vis));
		}

		void set(double... v) {
			NumberSetting[] all = {x, y, z, pitch, yaw, roll, scale, sx, sy, sz};
			for (int i = 0; i < all.length; i++) all[i].setSilently(i < v.length ? v[i] : all[i].defaultValue());
		}

		void reset() {
			for (NumberSetting n : new NumberSetting[] {x, y, z, pitch, yaw, roll, scale, sx, sy, sz}) n.setSilently(n.defaultValue());
		}
	}

	public final EnumSetting<Preset> preset = add(new EnumSetting<>("preset", "Preset", Preset.CLASSIC, p -> p.label));
	public final BoolSetting mirrorLeft = add(new BoolSetting("mirror_left", "Left hand mirrors right", true));
	public final EnumSetting<Side> editSide = add(new EnumSetting<Side>("edit_side", "Editing hand", Side.RIGHT, s -> s.label)
		.visibleWhen(() -> !mirrorLeft.get()));
	private final Profile[] profiles = new Profile[2];
	public final BoolSetting emissive = add(new BoolSetting("emissive", "Emissive (full-bright) hand & item", false));
	public final ColorSetting armTint = add(new ColorSetting("arm_tint", "Hand tint", 0xFFFFFF));
	public final NumberSetting armTintStrength = add(new NumberSetting("arm_tint_strength", "Hand tint strength", 0, 0, 1, 0.05));

	public HandPose() {
		super("hand_pose", "Hand Pose Editor",
			"Move, rotate and scale your first-person hand and items: one pose for every item.",
			Category.PLAYER, "hand", false);
		for (Side s : Side.values()) profiles[s.ordinal()] = new Profile(s);
		add(new ActionSetting("reset_profile", "Reset pose", "Reset", () -> {
			editing().reset();
			preset.setSilently(Preset.CUSTOM);
			ConfigManager.save();
		}));
		preset.onChange(this::applyPreset);
	}

	private Profile editing() {
		return profiles[(mirrorLeft.get() ? Side.RIGHT : editSide.get()).ordinal()];
	}

	private void applyPreset(Preset p) {
		Profile e = editing();
		switch (p) {
			case CLASSIC -> e.reset();
			case COMPACT -> e.set(0.06, 0.04, 0.05, 0, 0, 0, 0.75);
			case LOWERED -> e.set(0, -0.18, 0, 6, 0, 0, 1);
			case CENTERED -> e.set(-0.32, 0.02, -0.05, 0, 8, 0, 0.9);
			case ANGLED -> e.set(0.02, 0, 0, -12, 18, 14, 1);
			case CUSTOM -> { }
		}
		ConfigManager.save();
	}

	private Profile profile(HumanoidArm arm) {
		return profiles[(arm == HumanoidArm.LEFT && !mirrorLeft.get() ? Side.LEFT : Side.RIGHT).ordinal()];
	}

	/** Position offset, applied where vanilla starts placing the arm (view space). {@code cat} is unused: one pose for all items. */
	public void applyOffset(PoseStack pose, ItemCategory cat, HumanoidArm arm) {
		Profile p = profile(arm);
		float inv = arm == HumanoidArm.RIGHT ? 1 : -1;
		pose.translate(p.x.getF() * inv, p.y.getF(), p.z.getF());
	}

	/** Rotation + scale around the item/hand anchor, applied right before it is drawn. */
	public void applyRotationScale(PoseStack pose, ItemCategory cat, HumanoidArm arm) {
		Profile p = profile(arm);
		float inv = arm == HumanoidArm.RIGHT ? 1 : -1;
		if (p.pitch.getF() != 0) pose.mulPose(Axis.XP.rotationDegrees(p.pitch.getF()));
		if (p.yaw.getF() != 0) pose.mulPose(Axis.YP.rotationDegrees(p.yaw.getF() * inv));
		if (p.roll.getF() != 0) pose.mulPose(Axis.ZP.rotationDegrees(p.roll.getF() * inv));
		float s = p.scale.getF();
		pose.scale(s * p.sx.getF(), s * p.sy.getF(), s * p.sz.getF());
	}

	@Override
	public Preview preview() {
		return Preview.FIRST_PERSON;
	}
}
