package com.blixu.blixuaddons.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** World → GUI coordinates for 2D overlays (labels, tracers). Call {@link #begin} once per HUD frame. */
public final class ScreenProjector {
	private ScreenProjector() {}

	private static final Matrix4f VIEW_PROJ = new Matrix4f();
	private static final Vector4f TMP = new Vector4f();
	private static Vec3 cam = Vec3.ZERO;
	private static float guiW = 1, guiH = 1;
	public static float x, y;

	public static void begin(float guiWidth, float guiHeight) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		camera.getViewRotationProjectionMatrix(VIEW_PROJ);
		cam = camera.position();
		guiW = guiWidth;
		guiH = guiHeight;
	}

	/** Projects a world point; on success the result is in {@link #x}/{@link #y}. False if behind the camera. */
	public static boolean project(double wx, double wy, double wz) {
		TMP.set((float) (wx - cam.x), (float) (wy - cam.y), (float) (wz - cam.z), 1f);
		VIEW_PROJ.transform(TMP);
		if (TMP.w <= 0.05f) return false;
		float nx = TMP.x / TMP.w, ny = TMP.y / TMP.w;
		x = (nx * 0.5f + 0.5f) * guiW;
		y = (1f - (ny * 0.5f + 0.5f)) * guiH;
		return true;
	}

	/** Like {@link #project} but points behind you are pushed to the screen edge (for tracers). */
	public static void projectClamped(double wx, double wy, double wz) {
		TMP.set((float) (wx - cam.x), (float) (wy - cam.y), (float) (wz - cam.z), 1f);
		VIEW_PROJ.transform(TMP);
		float w = Math.abs(TMP.w) < 1e-4f ? 1e-4f : TMP.w;
		float nx = TMP.x / w, ny = TMP.y / w;
		if (TMP.w < 0) {
			nx = -nx;
			ny = -ny;
			float m = Math.max(Math.abs(nx), Math.abs(ny));
			if (m < 1e-4f) m = 1e-4f;
			nx = nx / m * 1.5f;
			ny = ny / m * 1.5f;
		}
		x = (nx * 0.5f + 0.5f) * guiW;
		y = (1f - (ny * 0.5f + 0.5f)) * guiH;
	}

	public static Vec3 camera() {
		return cam;
	}
}
