package com.blixu.blixuaddons.client.feature;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * While the Player Visual Editor is open the camera orbits your character at 5 degrees per
 * second so cosmetics can be seen from every side. Camera only: your rotation stays put.
 */
public final class PlayerEditorCamera {
	private PlayerEditorCamera() {}

	public static final float DEGREES_PER_SECOND = 5f;
	private static boolean active;
	private static long startNanos;
	private static float startYaw;
	private static @Nullable CameraType previous;

	private static boolean worldMode;
	private static boolean hideGuiBefore;

	public static void begin() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		lookForward(mc);
		active = true;
		worldMode = false;
		startNanos = System.nanoTime();
		startYaw = mc.player.getYRot() + 180f; // start in front of the player
		previous = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
	}

	/** World editor: first person, level view straight ahead, HUD hidden like F1. */
	public static void beginWorld() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		lookForward(mc);
		worldMode = true;
		hideGuiBefore = mc.gui.hud.isHidden();
		if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
		previous = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.FIRST_PERSON);
	}

	/** Level the view (pitch 0) and line the body up with it, so you face straight ahead. */
	private static void lookForward(Minecraft mc) {
		var p = mc.player;
		p.setXRot(0f);
		p.xRotO = 0f;
		p.setYHeadRot(p.getYRot());
		p.setYBodyRot(p.getYRot());
	}

	public static void end() {
		Minecraft mc = Minecraft.getInstance();
		if (worldMode) {
			worldMode = false;
			if (mc.gui.hud.isHidden() != hideGuiBefore) mc.gui.hud.toggle();
		}
		active = false;
		if (previous != null) mc.options.setCameraType(previous);
		previous = null;
	}

	public static boolean active() {
		return active && Minecraft.getInstance().player != null;
	}

	public static float yaw() {
		return startYaw + (System.nanoTime() - startNanos) / 1e9f * DEGREES_PER_SECOND;
	}
}
