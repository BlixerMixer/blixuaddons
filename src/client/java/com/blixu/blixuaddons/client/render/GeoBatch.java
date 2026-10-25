package com.blixu.blixuaddons.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Arrays;

/**
 * Reusable vertex list for one frame. Modules append quads or lines (camera-relative
 * coordinates), then the batch is handed to Minecraft once via submitCustomGeometry.
 * Arrays grow once and are reused, so drawing doesn't allocate per frame.
 */
public final class GeoBatch {
	private final boolean lines;
	private final int maxVertices;
	private float[] pos = new float[3 * 1024];
	private int[] col = new int[1024];
	private float[] width;
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer replay, replayOpaque, replayTranslucent;

	/** @param maxVertices hard cap so a runaway module can't build a giant mesh */
	public GeoBatch(boolean lines, int maxVertices) {
		this.lines = lines;
		this.maxVertices = maxVertices;
		if (lines) width = new float[1024];
		this.replay = (pose, vc) -> emit(pose, vc, 0);
		this.replayOpaque = (pose, vc) -> emit(pose, vc, 1);
		this.replayTranslucent = (pose, vc) -> emit(pose, vc, 2);
	}

	public void clear() { count = 0; }
	public boolean isEmpty() { return count == 0; }
	public int vertexCount() { return count; }
	public boolean full(int extra) { return count + extra > maxVertices; }

	private void ensure(int extra) {
		int need = count + extra;
		if (need <= col.length) return;
		int cap = Math.max(need, col.length * 2);
		pos = Arrays.copyOf(pos, cap * 3);
		col = Arrays.copyOf(col, cap);
		if (width != null) width = Arrays.copyOf(width, cap);
	}

	private void v(float x, float y, float z, int c) {
		int i = count * 3;
		pos[i] = x;
		pos[i + 1] = y;
		pos[i + 2] = z;
		col[count] = c;
		count++;
	}

	public void quad(float x1, float y1, float z1, float x2, float y2, float z2,
					 float x3, float y3, float z3, float x4, float y4, float z4,
					 int c1, int c2, int c3, int c4) {
		if (lines || full(4)) return;
		ensure(4);
		v(x1, y1, z1, c1);
		v(x2, y2, z2, c2);
		v(x3, y3, z3, c3);
		v(x4, y4, z4, c4);
	}

	public void quad(float x1, float y1, float z1, float x2, float y2, float z2,
					 float x3, float y3, float z3, float x4, float y4, float z4, int c) {
		quad(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, c, c, c, c);
	}

	/** Triangle as a degenerate quad (the pipelines are quad-based). */
	public void tri(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int c1, int c2, int c3) {
		quad(x1, y1, z1, x2, y2, z2, x3, y3, z3, x3, y3, z3, c1, c2, c3, c3);
	}

	public void line(float x1, float y1, float z1, float x2, float y2, float z2, int c1, int c2, float w) {
		if (!lines || full(2)) return;
		ensure(2);
		width[count] = w;
		v(x1, y1, z1, c1);
		width[count] = w;
		v(x2, y2, z2, c2);
	}

	/** Hands the batch to Minecraft. The lambda reads the arrays later this frame, so don't clear until next frame. */
	public void submit(SubmitNodeCollector out, PoseStack poseStack, RenderType type) {
		if (count > 0) out.submitCustomGeometry(poseStack, type, replay);
	}

	/** Quads whose four corners are fully opaque go to {@code solid} (depth-writing), the rest to {@code translucent}. */
	public void submitSplit(SubmitNodeCollector out, PoseStack poseStack, RenderType solid, RenderType translucent) {
		if (count == 0) return;
		out.submitCustomGeometry(poseStack, solid, replayOpaque);
		out.submitCustomGeometry(poseStack, translucent, replayTranslucent);
	}

	private boolean opaque(int quad) {
		int i = quad * 4;
		return (col[i] & col[i + 1] & col[i + 2] & col[i + 3]) >>> 24 == 0xFF;
	}

	/** @param mode 0 all, 1 opaque quads only, 2 translucent quads only */
	private void emit(PoseStack.Pose pose, VertexConsumer vc, int mode) {
		if (lines) {
			for (int i = 0; i + 1 < count; i += 2) {
				int a = i * 3, b = a + 3;
				float nx = pos[b] - pos[a], ny = pos[b + 1] - pos[a + 1], nz = pos[b + 2] - pos[a + 2];
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (len < 1e-6f) continue;
				nx /= len;
				ny /= len;
				nz /= len;
				vc.addVertex(pose, pos[a], pos[a + 1], pos[a + 2]).setColor(col[i]).setNormal(pose, nx, ny, nz).setLineWidth(width[i]);
				vc.addVertex(pose, pos[b], pos[b + 1], pos[b + 2]).setColor(col[i + 1]).setNormal(pose, nx, ny, nz).setLineWidth(width[i + 1]);
			}
		} else {
			for (int q = 0; q < count / 4; q++) {
				if (mode != 0 && opaque(q) != (mode == 1)) continue;
				for (int i = q * 4; i < q * 4 + 4; i++) {
					int p = i * 3;
					vc.addVertex(pose, pos[p], pos[p + 1], pos[p + 2]).setColor(col[i]);
				}
			}
		}
	}
}
