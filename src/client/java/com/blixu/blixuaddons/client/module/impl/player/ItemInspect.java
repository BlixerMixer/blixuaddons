package com.blixu.blixuaddons.client.module.impl.player;

import com.blixu.blixuaddons.client.anim.Easing;
import com.blixu.blixuaddons.client.anim.Tween;
import com.blixu.blixuaddons.client.feature.ItemCategory;
import com.blixu.blixuaddons.client.feature.KnifeInspects;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.KeyBindSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Tactical-shooter style item inspection on a key. Purely a first-person pose animation:
 * it never changes the item, its stats, the selected slot or any server action.
 */
public class ItemInspect extends Module {
	public enum Style {
		// Counter-Strike knife inspects (keyframed, see KnifeInspects)
		CS_CLASSIC(KnifeInspects.Anim.CLASSIC), CS_KARAMBIT(KnifeInspects.Anim.KARAMBIT), CS_BUTTERFLY(KnifeInspects.Anim.BUTTERFLY),
		CS_FLIP(KnifeInspects.Anim.FLIP), CS_M9(KnifeInspects.Anim.M9), CS_BAYONET(KnifeInspects.Anim.BAYONET),
		CS_FALCHION(KnifeInspects.Anim.FALCHION), CS_TALON(KnifeInspects.Anim.TALON), CS_BOWIE(KnifeInspects.Anim.BOWIE),
		CS_STILETTO(KnifeInspects.Anim.STILETTO),
		// simple procedural styles
		ROTATE("Rotate"), FLIP("Flip"), SPIN("Spin"), SHOWCASE("Showcase"), TILT("Tilt"), CUSTOM("Custom");
		public final String label;
		public final KnifeInspects.@org.jspecify.annotations.Nullable Anim knife;
		Style(String l) { label = l; knife = null; }
		Style(KnifeInspects.Anim a) { label = "CS: " + a.label; knife = a; }
	}

	public final KeyBindSetting key = add(new KeyBindSetting("key", "Inspect key", GLFW.GLFW_KEY_Y));
	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.CS_KARAMBIT, s -> s.label));
	public final BoolSetting perItem = add(new BoolSetting("per_item", "Per-item styles", false));
	public final EnumSetting<Style> swordStyle = add(new EnumSetting<Style>("sword_style", "Sword style", Style.CS_BUTTERFLY, s -> s.label).visibleWhen(perItem::get));
	public final EnumSetting<Style> toolStyle = add(new EnumSetting<Style>("tool_style", "Tool style", Style.SPIN, s -> s.label).visibleWhen(perItem::get));
	public final EnumSetting<Style> otherStyle = add(new EnumSetting<Style>("other_style", "Other items style", Style.SHOWCASE, s -> s.label).visibleWhen(perItem::get));
	public final BoolSetting emptyHand = add(new BoolSetting("empty_hand", "Allow with empty hand", false));
	public final NumberSetting knifeSpeed = add(new NumberSetting("knife_speed", "CS inspect speed", 1, 0.5, 2, 0.05));
	public final NumberSetting duration = add(new NumberSetting("duration", "Duration (s, simple styles)", 2.2, 0.4, 6, 0.1));
	public final EnumSetting<Easing> easing = add(new EnumSetting<>("easing", "Easing", Easing.EASE_IN_OUT, e -> e.label));
	public final BoolSetting clockwise = add(new BoolSetting("clockwise", "Clockwise", true));
	public final NumberSetting rotations = add(new NumberSetting("rotations", "Rotations", 1, 0.5, 4, 0.5));
	public final NumberSetting offsetX = add(new NumberSetting("offset_x", "Offset X", -0.25, -1, 1, 0.01));
	public final NumberSetting offsetY = add(new NumberSetting("offset_y", "Offset Y", 0.12, -1, 1, 0.01));
	public final NumberSetting offsetZ = add(new NumberSetting("offset_z", "Offset Z", 0.1, -1, 1, 0.01));
	public final NumberSetting scale = add(new NumberSetting("scale", "Scale offset", 0.1, -0.5, 1, 0.01));
	public final BoolSetting metallic = add(new BoolSetting("metallic", "Metallic glint while inspecting", true));
	public final BoolSetting trail = add(new BoolSetting("trail", "Motion trail (uses Swing trail colours)", false));
	public final BoolSetting cancelSwitch = add(new BoolSetting("cancel_switch", "Cancel on item switch", true));
	public final BoolSetting cancelGui = add(new BoolSetting("cancel_gui", "Cancel when a menu opens", true));
	public final BoolSetting cancelAction = add(new BoolSetting("cancel_action", "Cancel on attack / use", true));
	// Custom keyframe (the pose held at the middle of the inspection)
	public final NumberSetting customPitch = add(new NumberSetting("custom_pitch", "Custom pitch", 30, -180, 180, 1).visibleWhen(() -> style.get() == Style.CUSTOM));
	public final NumberSetting customYaw = add(new NumberSetting("custom_yaw", "Custom yaw", -120, -360, 360, 1).visibleWhen(() -> style.get() == Style.CUSTOM));
	public final NumberSetting customRoll = add(new NumberSetting("custom_roll", "Custom roll", 20, -360, 360, 1).visibleWhen(() -> style.get() == Style.CUSTOM));
	public final NumberSetting customHold = add(new NumberSetting("custom_hold", "Custom hold share", 0.4, 0, 0.8, 0.05).visibleWhen(() -> style.get() == Style.CUSTOM));

	private final Tween clock = new Tween();
	private ItemStack inspected = ItemStack.EMPTY;
	private int slot = -1;

	public ItemInspect() {
		super("item_inspect", "Item Inspect",
			"Press a key to inspect your held item, CS-style. Cosmetic only.",
			Category.PLAYER, "knife", false);
	}

	@Override
	public void onTick(Minecraft mc) {
		var player = mc.player;
		if (player == null) return;
		if (key.consumePress()) start(player);
		if (!clock.running()) return;
		boolean switched = player.getInventory().getSelectedSlot() != slot || !ItemStack.isSameItem(player.getMainHandItem(), inspected);
		if (cancelSwitch.get() && switched) clock.cancel();
		if (cancelGui.get() && mc.gui.screen() != null) clock.cancel();
		if (cancelAction.get() && (player.isUsingItem() || player.swinging)) clock.cancel();
	}

	/** Starts an inspection of the held item (the key, or the dev harness). */
	public void start(net.minecraft.world.entity.player.Player player) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty() && !emptyHand.get()) return;
		clock.start(length(styleFor(ItemCategory.of(held))));
		inspected = held;
		slot = player.getInventory().getSelectedSlot();
	}

	public void update(float dt) {
		clock.update(dt);
	}

	public boolean active() {
		if (com.blixu.blixuaddons.client.feature.FirstPersonFx.previewing(this)) return previewProgress() < 1f;
		return clock.running();
	}

	private float previewProgress() {
		float len = length(style.get());
		float cycle = len + 0.6f;
		return (com.blixu.blixuaddons.client.feature.FirstPersonFx.previewTime() % cycle) / len;
	}

	/** Seconds the inspect takes: CS knives keep their own length (scaled by the speed), others use Duration. */
	private float length(Style st) {
		return st.knife != null ? st.knife.seconds / knifeSpeed.getF() : duration.getF();
	}

	private final float[] knifePose = new float[6];

	@Override
	protected void onDisable() {
		clock.cancel();
	}

	@Override
	public void onWorldChange() {
		clock.cancel();
	}

	public Style styleFor(ItemCategory c) {
		if (!perItem.get()) return style.get();
		return switch (c) {
			case SWORD -> swordStyle.get();
			case TOOL -> toolStyle.get();
			default -> otherStyle.get();
		};
	}

	/** Pose for the current inspection progress, applied right before the item is drawn. */
	public void apply(PoseStack pose, ItemCategory cat, int invert) {
		float p = com.blixu.blixuaddons.client.feature.FirstPersonFx.previewing(this) ? Math.min(1f, previewProgress()) : clock.progress();
		Style st = styleFor(cat);
		if (st.knife != null) {
			// keyframed CS inspect: offset in view space, rotations around the grip
			float[] k = knifePose;
			st.knife.sample(p, k);
			pose.translate(invert * k[0], k[1], k[2]);
			float px = invert * st.knife.pivotX, py = st.knife.pivotY;
			pose.translate(px, py, 0);
			pose.mulPose(Axis.YP.rotationDegrees(invert * k[4]));
			pose.mulPose(Axis.XP.rotationDegrees(k[3]));
			pose.mulPose(Axis.ZP.rotationDegrees(invert * k[5]));
			pose.translate(-px, -py, 0);
			return;
		}
		// Blend in over the first 15% and out over the last 15%.
		float env = Easing.SMOOTHSTEP.apply(Math.min(1f, Math.min(p, 1f - p) / 0.15f));
		float e = easing.get().apply(p);
		float dir = clockwise.get() ? -1 : 1;
		float turns = 360f * rotations.getF() * dir;
		float s = 1f + scale.getF() * env;
		pose.translate(invert * offsetX.getF() * env, offsetY.getF() * env, offsetZ.getF() * env);
		switch (st) {
			case ROTATE -> pose.mulPose(Axis.YP.rotationDegrees(invert * turns * e));
			case FLIP -> {
				pose.mulPose(Axis.XP.rotationDegrees(turns * e));
				pose.translate(0, 0.08f * Mth.sin(e * Mth.PI), 0);
			}
			case SPIN -> {
				pose.mulPose(Axis.ZP.rotationDegrees(invert * turns * e));
				pose.mulPose(Axis.YP.rotationDegrees(invert * 25f * Mth.sin(e * Mth.TWO_PI)));
			}
			case SHOWCASE -> {
				// Bring the item towards the centre, turn it to show both faces, then put it back.
				pose.mulPose(Axis.YP.rotationDegrees(invert * (-35f * env + turns * 0.5f * e)));
				pose.mulPose(Axis.ZP.rotationDegrees(invert * 20f * env * Mth.sin(e * Mth.PI)));
				pose.mulPose(Axis.XP.rotationDegrees(-15f * env));
			}
			case TILT -> {
				pose.mulPose(Axis.ZP.rotationDegrees(invert * 35f * Mth.sin(e * Mth.PI) * dir));
				pose.mulPose(Axis.XP.rotationDegrees(-20f * Mth.sin(e * Mth.PI)));
			}
			default -> {
				float hold = customHold.getF();
				float rise = (1f - hold) / 2f;
				float k = p < rise ? Easing.SMOOTHSTEP.apply(p / rise) : p > 1 - rise ? Easing.SMOOTHSTEP.apply((1 - p) / rise) : 1f;
				pose.mulPose(Axis.YP.rotationDegrees(invert * customYaw.getF() * k));
				pose.mulPose(Axis.XP.rotationDegrees(customPitch.getF() * k));
				pose.mulPose(Axis.ZP.rotationDegrees(invert * customRoll.getF() * k));
			}
		}
		pose.scale(s, s, s);
	}

	@Override
	public Preview preview() {
		return Preview.FIRST_PERSON;
	}
}
