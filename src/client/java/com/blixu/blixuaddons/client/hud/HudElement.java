package com.blixu.blixuaddons.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A movable piece of our HUD, for the HUD editor. Boxes are in GUI pixels. */
public interface HudElement {
	String name();

	/** Whether it's in use (its module is on); hidden ones aren't shown in the editor. */
	boolean shown();

	/** {x, y, w, h} on a screen of sw x sh. */
	float[] box(float sw, float sh);

	/** Move so the box's top-left lands on (x, y). */
	void moveTo(float x, float y, float sw, float sh);

	/** Mouse wheel over it: grow (+) or shrink (-). */
	default void resize(float steps) {}

	/** Draw sample content in the box (elements that are already on screen live can skip this). */
	default void drawSample(GuiGraphicsExtractor g, float sw, float sh) {}
}
