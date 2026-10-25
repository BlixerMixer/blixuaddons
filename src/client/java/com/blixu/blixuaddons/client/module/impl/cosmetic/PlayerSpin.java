package com.blixu.blixuaddons.client.module.impl.cosmetic;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.style.Colors;

/**
 * Your character spins around its own vertical axis, optionally bobbing up and down in a crouch.
 * Only how your own model is drawn on your screen changes: your real rotation, sneak state and
 * every packet stay untouched, so nobody else sees it and the server never knows.
 */
public class PlayerSpin extends Module {
	public enum Direction { CLOCKWISE("Clockwise"), COUNTER("Counter-clockwise");
		public final String label;
		Direction(String l) { label = l; }
	}

	public final NumberSetting speed = add(new NumberSetting("speed", "Spin speed (turns/s)", 1, 0.1, 6, 0.1));
	public final EnumSetting<Direction> direction = add(new EnumSetting<>("direction", "Direction", Direction.CLOCKWISE, d -> d.label));
	public final BoolSetting spinHead = add(new BoolSetting("spin_head", "Head spins too", true));
	public final BoolSetting crouchSpam = add(new BoolSetting("crouch_spam", "Crouch spam (visual)", false));
	public final NumberSetting crouchRate = add(new NumberSetting("crouch_rate", "Crouches per second", 4, 1, 10, 0.5).visibleWhen(crouchSpam::get));

	public PlayerSpin() {
		super("player_spin", "Player Spin",
			"Spin your character around its own axis, with an optional crouch spam. Client-side: only you see it.",
			Category.COSMETICS, "player", false);
	}

	/** Body yaw (degrees) to draw the local player with. */
	public float bodyYaw() {
		float turns = Colors.seconds() * speed.getF();
		return (direction.get() == Direction.CLOCKWISE ? 1 : -1) * (turns % 1f) * 360f;
	}

	public boolean crouching() {
		return crouchSpam.get() && (Colors.seconds() * crouchRate.getF()) % 1f < 0.5f;
	}

	/** For cosmetics drawn on the body: the active override, or the real body yaw. */
	public static float bodyYaw(float real) {
		PlayerSpin s = ModuleManager.active(PlayerSpin.class);
		return s == null ? real : s.bodyYaw();
	}

	public static boolean crouching(boolean real) {
		PlayerSpin s = ModuleManager.active(PlayerSpin.class);
		return s == null ? real : real || s.crouching();
	}
}
