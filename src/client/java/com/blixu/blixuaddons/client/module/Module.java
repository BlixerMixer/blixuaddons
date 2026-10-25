package com.blixu.blixuaddons.client.module;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.module.setting.KeyBindSetting;
import com.blixu.blixuaddons.client.module.setting.Setting;
import com.blixu.blixuaddons.client.render.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One feature that can be switched on and off from the menu.
 * To add a feature: extend this, then register it in {@link ModuleManager#init()}.
 */
public abstract class Module {
	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final String icon;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;

	/** Optional key that toggles this module (set in its settings panel). */
	public final KeyBindSetting toggleKey;

	/** Used by the menu for the toggle animation (0 = off, 1 = on). */
	public float toggleAnim;

	protected Module(String id, String name, String description, Category category, String icon, boolean enabledByDefault) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.icon = icon;
		this.enabled = enabledByDefault;
		this.toggleAnim = enabledByDefault ? 1f : 0f;
		this.toggleKey = new KeyBindSetting("toggle_key", "Toggle key", KeyBindSetting.NONE);
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	/** Called once by the manager after construction (the toggle key lives on the card, not in the list). */
	void finishSettings() {
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) onEnable(); else onDisable();
		ConfigManager.save();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** Called when loading the config — sets the state without side effects or saving. */
	public void loadEnabled(boolean enabled) {
		this.enabled = enabled;
		this.toggleAnim = enabled ? 1f : 0f;
	}

	/** Puts every setting back to its default. */
	public void resetSettings() {
		for (Setting<?> s : settings) {
			s.reset();
		}
	}

	protected void onEnable() {}

	/** Also the place to drop particles/trails/caches so nothing stale is drawn on re-enable. */
	protected void onDisable() {}

	/** Runs every client tick (20 times per second) while enabled. */
	/** Movable HUD parts of this module, for the HUD editor. */
	public java.util.List<com.blixu.blixuaddons.client.hud.HudElement> hudElements() {
		return java.util.List.of();
	}

	public void onTick(Minecraft client) {}

	/** Draw world-space visuals. Runs every frame while enabled and a world is loaded. */
	public void onWorldRender(WorldCtx ctx) {}

	/** Draw 2D HUD elements. Runs every frame while enabled (not with F1 / F3). */
	public void onHud(GuiGraphicsExtractor g, float dt) {}

	/** Live preview shown next to the settings in the menu. */
	public enum Preview { NONE, SCENE, FLAT, FIRST_PERSON }

	public Preview preview() { return Preview.NONE; }

	/**
	 * SCENE previews: build a small 3D scene around the origin (1 unit = 1 block, about 4 units
	 * fit the window). {@code time} is seconds since the menu opened.
	 */
	public void renderPreview(WorldCtx ctx, float time) {}

	/** GUI pixels per block in the SCENE preview. */
	public float previewScale() { return 36f; }

	/** FLAT previews (and overlays on top of a scene): plain 2D drawing in the given box. */
	public void drawPreview(GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {}

	/** World changed or disconnected: clear anything tied to old entities/positions. Runs even when disabled. */
	public void onWorldChange() {}

	public boolean isEnabled() { return enabled; }
	public String id() { return id; }
	public String name() { return name; }
	public String description() { return description; }
	public Category category() { return category; }
	public String icon() { return icon; }
	public List<Setting<?>> settings() { return Collections.unmodifiableList(settings); }
}
