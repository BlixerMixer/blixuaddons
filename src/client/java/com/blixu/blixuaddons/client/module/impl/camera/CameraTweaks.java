package com.blixu.blixuaddons.client.module.impl.camera;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.KeyBindSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** FOV, zoom, view bobbing, camera lean and third-person distance. Only the local camera changes. */
public class CameraTweaks extends Module {
	public final NumberSetting fovOffset = add(new NumberSetting("fov_offset", "FOV adjustment", 0, -30, 30, 1));
	public final KeyBindSetting zoomKey = add(new KeyBindSetting("zoom_key", "Zoom key", GLFW.GLFW_KEY_C));
	public final NumberSetting zoomLevel = add(new NumberSetting("zoom_level", "Zoom level", 4, 1.5, 12, 0.5));
	public final NumberSetting zoomSpeed = add(new NumberSetting("zoom_speed", "Zoom transition speed", 12, 2, 40, 1));
	public final BoolSetting zoomSensitivity = add(new BoolSetting("zoom_sensitivity", "Lower mouse sensitivity while zoomed", true));
	public final BoolSetting bobbing = add(new BoolSetting("bobbing", "Camera bobbing", true));
	public final NumberSetting bobIntensity = add(new NumberSetting("bob_intensity", "View bobbing intensity", 1, 0, 2, 0.05).visibleWhen(bobbing::get));
	public final NumberSetting lean = add(new NumberSetting("lean", "Camera roll when strafing", 0, 0, 8, 0.5));
	public final NumberSetting distance = add(new NumberSetting("distance", "Third-person distance", 4, 1, 8, 0.25));

	private float zoom = 1f;
	private float leanNow;
	private long lastNanos;

	public CameraTweaks() {
		super("camera_tweaks", "Camera Tweaks",
			"FOV, smooth zoom, bobbing intensity, strafe lean and third-person distance.",
			Category.CAMERA, "zoom", false);
	}

	/** Applied to the world FOV every frame. */
	public float fov(float vanilla) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min((now - lastNanos) / 1e9f, 0.1f);
		lastNanos = now;
		boolean zooming = zoomKey.isDown();
		float target = zooming ? zoomLevel.getF() : 1f;
		zoom += (target - zoom) * (1f - (float) Math.exp(-dt * zoomSpeed.getF()));
		var p = Minecraft.getInstance().player;
		if (p != null) {
			float strafe = p.input.getMoveVector().x;
			leanNow += (strafe * lean.getF() - leanNow) * (1f - (float) Math.exp(-dt * 6f));
		}
		return Mth.clamp((vanilla + fovOffset.getF()) / zoom, 1f, 170f);
	}

	public float zoomFactor() {
		return zoom;
	}

	/** Multiplier for mouse turn speed (lower when zoomed in). */
	public float sensitivity() {
		return zoomSensitivity.get() && zoom > 1.01f ? 1f / zoom : 1f;
	}

	public float bob() {
		return bobbing.get() ? bobIntensity.getF() : 0f;
	}

	public float leanDegrees() {
		return leanNow;
	}
}
