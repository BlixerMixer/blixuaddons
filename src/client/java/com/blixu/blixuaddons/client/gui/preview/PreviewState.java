package com.blixu.blixuaddons.client.gui.preview;

import com.blixu.blixuaddons.client.module.Module;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** One live module preview queued into the GUI frame (drawn by {@link PreviewRenderer}). */
public record PreviewState(
	Module module, float time, float yaw, float pitch,
	int x0, int y0, int x1, int y1, float scale,
	Matrix3x2fc pose,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	public static PreviewState of(Module module, float time, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
								  Matrix3x2fc pose, @Nullable ScreenRectangle scissor) {
		Matrix3x2f frozen = new Matrix3x2f(pose);
		ScreenRectangle area = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(frozen);
		if (scissor != null) area = area.intersection(scissor);
		return new PreviewState(module, time, yaw, pitch, x0, y0, x1, y1, scale, frozen, scissor, area);
	}
}
