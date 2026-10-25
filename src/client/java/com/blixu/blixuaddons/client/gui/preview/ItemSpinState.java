package com.blixu.blixuaddons.client.gui.preview;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** One item model drawn in 3D, turned {@code yaw} radians around its vertical axis (rare drop animation). */
public record ItemSpinState(
	ItemStack stack, float yaw, float tilt,
	int x0, int y0, int x1, int y1, float scale,
	Matrix3x2fc pose,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	/** Centred on (cx, cy) in the current pose, {@code size} GUI pixels across. */
	public static ItemSpinState of(ItemStack stack, float yaw, float tilt, float cx, float cy, float size, Matrix3x2fc pose, @Nullable ScreenRectangle scissor) {
		Matrix3x2f frozen = new Matrix3x2f(pose);
		float half = size * 0.75f; // room for the model to turn without clipping its corners
		int x0 = Math.round(cx - half), y0 = Math.round(cy - half), x1 = Math.round(cx + half), y1 = Math.round(cy + half);
		ScreenRectangle area = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(frozen);
		if (scissor != null) area = area.intersection(scissor);
		// the texture is sized from the transformed bounds, so the model scale follows the pose's scale too
		float poseScale = (float) Math.hypot(frozen.m00(), frozen.m01());
		return new ItemSpinState(stack, yaw, tilt, x0, y0, x1, y1, size * poseScale, frozen, scissor, area);
	}
}
