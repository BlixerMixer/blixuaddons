package com.blixu.blixuaddons.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * A key, edited in the menu (click, then press a key; Escape/Backspace clears).
 * Mouse buttons are stored as -100 - button.
 */
public class KeyBindSetting extends Setting<Integer> {
	public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;
	private boolean wasDown;

	public KeyBindSetting(String id, String name, int defaultKey) {
		super(id, name, defaultKey);
	}

	public boolean isDown() {
		int key = value;
		if (key == NONE) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null) return false;
		long window = mc.getWindow().handle();
		if (key <= -100) return GLFW.glfwGetMouseButton(window, -100 - key) == GLFW.GLFW_PRESS;
		return InputConstants.isKeyDown(mc.getWindow(), key);
	}

	/** True once per press. Call every tick/frame from one place only. */
	public boolean consumePress() {
		boolean down = isDown();
		boolean pressed = down && !wasDown;
		wasDown = down;
		return pressed;
	}

	public String keyName() {
		int key = value;
		if (key == NONE) return "None";
		if (key <= -100) return "Mouse " + (-100 - key + 1);
		return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
	}

	@Override public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement json) {
		try {
			value = json.getAsInt();
		} catch (RuntimeException ignored) {
		}
	}
}
