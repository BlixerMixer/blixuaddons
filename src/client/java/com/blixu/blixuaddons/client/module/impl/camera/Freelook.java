package com.blixu.blixuaddons.client.module.impl.camera;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.KeyBindSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Look around without turning your character. While active, mouse input moves only a
 * camera offset; the player's real rotation (used for movement, attacks and what the
 * server sees) never changes, and no packets are sent or spoofed.
 */
public class Freelook extends Module {
	public final KeyBindSetting key = add(new KeyBindSetting("key", "Freelook key", GLFW.GLFW_KEY_LEFT_ALT));
	public final BoolSetting toggleMode = add(new BoolSetting("toggle_mode", "Toggle instead of hold", false));
	public final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Switch to third person while active", true));
	public final NumberSetting sensH = add(new NumberSetting("sens_h", "Horizontal sensitivity", 1, 0.1, 3, 0.05));
	public final NumberSetting sensV = add(new NumberSetting("sens_v", "Vertical sensitivity", 1, 0.1, 3, 0.05));
	public final NumberSetting maxYaw = add(new NumberSetting("max_yaw", "Max yaw offset", 180, 10, 180, 1));
	public final NumberSetting maxPitch = add(new NumberSetting("max_pitch", "Max pitch offset", 90, 10, 180, 1));
	public final NumberSetting smoothness = add(new NumberSetting("smoothness", "Smoothness", 0.3, 0, 0.95, 0.05));
	public final NumberSetting transitionIn = add(new NumberSetting("transition_in", "Transition in (s)", 0.12, 0, 1, 0.01));
	public final NumberSetting transitionOut = add(new NumberSetting("transition_out", "Transition out (s)", 0.25, 0, 1.5, 0.01));
	public final BoolSetting inertia = add(new BoolSetting("inertia", "Camera inertia", false));
	public final NumberSetting roll = add(new NumberSetting("roll", "Roll when turning", 0, 0, 15, 0.5));

	private boolean active;
	private boolean keyWasDown;
	private CameraType previous;
	// target = where the mouse put the camera; current = what is shown (smoothed)
	private float targetYaw, targetPitch, yaw, pitch, velYaw, velPitch, rollNow;
	private float strength; // 0..1 blend for transitions in/out
	private long lastNanos;

	public Freelook() {
		super("freelook", "Freelook",
			"Hold a key to look around independently of where you are facing.",
			Category.CAMERA, "eye", false);
	}

	@Override
	public void onTick(Minecraft mc) {
		boolean down = key.isDown();
		boolean want = toggleMode.get() ? (down && !keyWasDown ? !active : active) : down;
		keyWasDown = down;
		if (mc.gui.screen() != null && !toggleMode.get()) want = false;
		if (want != active) {
			active = want;
			if (active) {
				targetYaw = targetPitch = 0;
				if (thirdPerson.get() && mc.options.getCameraType().isFirstPerson()) {
					previous = mc.options.getCameraType();
					mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				}
			} else if (previous != null) {
				mc.options.setCameraType(previous);
				previous = null;
			}
		}
	}

	@Override
	protected void onDisable() {
		if (previous != null) Minecraft.getInstance().options.setCameraType(previous);
		previous = null;
		active = false;
		yaw = pitch = targetYaw = targetPitch = strength = 0;
	}

	/** True while mouse input should go to the camera instead of the player. */
	public boolean capturing() {
		return isEnabled() && active;
	}

	/** Mouse delta (same units as Entity.turn). */
	public void turn(double dx, double dy) {
		targetYaw = Mth.clamp(targetYaw + (float) dx * 0.15f * sensH.getF(), -maxYaw.getF(), maxYaw.getF());
		targetPitch = Mth.clamp(targetPitch + (float) dy * 0.15f * sensV.getF(), -maxPitch.getF(), maxPitch.getF());
	}

	/** Advances smoothing once per frame (called from the camera hook). */
	public void update() {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min((now - lastNanos) / 1e9f, 0.1f);
		lastNanos = now;
		float dur = active ? transitionIn.getF() : transitionOut.getF();
		float goal = active ? 1f : 0f;
		strength = dur <= 0.001f ? goal : strength + (goal - strength) * (1f - (float) Math.exp(-dt * 3f / dur));
		if (!active && strength < 0.002f) {
			targetYaw = targetPitch = 0;
			strength = 0;
		}
		float smooth = smoothness.getF();
		float follow = smooth <= 0.001f ? 1f : 1f - (float) Math.exp(-dt * (1f - smooth) * 30f);
		if (inertia.get()) {
			// critically-damped chase gives a little weight to the camera
			float k = 60f * (1f - smooth) + 10f, d = 2f * Mth.sqrt(k);
			velYaw += ((targetYaw - yaw) * k - velYaw * d) * dt;
			velPitch += ((targetPitch - pitch) * k - velPitch * d) * dt;
			yaw += velYaw * dt;
			pitch += velPitch * dt;
		} else {
			float before = yaw;
			yaw += (targetYaw - yaw) * follow;
			pitch += (targetPitch - pitch) * follow;
			velYaw = dt > 0 ? (yaw - before) / dt : 0;
		}
		float rollTarget = Mth.clamp(-velYaw * 0.02f, -1f, 1f) * roll.getF();
		rollNow += (rollTarget - rollNow) * (1f - (float) Math.exp(-dt * 8f));
	}

	public float yawOffset() { return yaw * strength; }
	public float pitchOffset() { return pitch * strength; }
	public float rollDegrees() { return rollNow * strength; }
	public boolean showing() { return isEnabled() && strength > 0.001f; }
}
