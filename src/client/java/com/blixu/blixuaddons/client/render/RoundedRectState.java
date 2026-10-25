package com.blixu.blixuaddons.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * One rounded shape queued for this frame. Minecraft's GUI renderer collects these
 * and later calls {@link #buildVertices} to turn them into a quad for our shader.
 *
 * @param mode   0 = filled, > 0 = soft edge width (shadows/glows), < 0 = outline thickness
 * @param colors corner colours: top-left, top-right, bottom-right, bottom-left (ARGB)
 */
public record RoundedRectState(
	Matrix3x2fc pose,
	float x, float y, float width, float height,
	float radius, float mode,
	int colorTL, int colorTR, int colorBR, int colorBL,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds,
	@Nullable GpuTextureView blur
) implements GuiElementRenderState {

	public static RoundedRectState of(Matrix3x2fc pose, float x, float y, float w, float h, float radius, float mode,
									  int tl, int tr, int br, int bl, @Nullable ScreenRectangle scissor) {
		return of(pose, x, y, w, h, radius, mode, tl, tr, br, bl, scissor, null);
	}

	public static RoundedRectState of(Matrix3x2fc pose, float x, float y, float w, float h, float radius, float mode,
									  int tl, int tr, int br, int bl, @Nullable ScreenRectangle scissor, @Nullable GpuTextureView blur) {
		Matrix3x2f frozen = new Matrix3x2f(pose);
		float pad = Math.max(mode, 0f) + 1f;
		ScreenRectangle area = new ScreenRectangle(
			(int) Math.floor(x - pad), (int) Math.floor(y - pad),
			(int) Math.ceil(w + pad * 2), (int) Math.ceil(h + pad * 2)
		).transformMaxBounds(frozen);
		if (scissor != null) {
			area = area.intersection(scissor);
		}
		return new RoundedRectState(frozen, x, y, w, h, radius, mode, tl, tr, br, bl, scissor, area, blur);
	}

	@Override
	public void buildVertices(VertexConsumer consumer) {
		// Soft shapes need room outside the box for the fade.
		float pad = Math.max(mode, 0f) + 1f;
		float halfW = width / 2f;
		float halfH = height / 2f;
		float cx = x + halfW;
		float cy = y + halfH;

		int hx = Math.round(halfW * 16f);
		int hy = Math.round(halfH * 16f);
		int r = Math.round(radius * 16f);
		int m = Math.round(mode * 16f);

		float lx = halfW + pad;
		float ly = halfH + pad;
		vertex(consumer, cx - lx, cy - ly, -lx, -ly, colorTL, hx, hy, r, m);
		vertex(consumer, cx - lx, cy + ly, -lx, ly, colorBL, hx, hy, r, m);
		vertex(consumer, cx + lx, cy + ly, lx, ly, colorBR, hx, hy, r, m);
		vertex(consumer, cx + lx, cy - ly, lx, -ly, colorTR, hx, hy, r, m);
	}

	private void vertex(VertexConsumer c, float px, float py, float localX, float localY,
						int color, int hx, int hy, int r, int m) {
		c.addVertexWith2DPose(pose, px, py)
			.setColor(color)
			.setUv(localX, localY)
			.setUv1(hx, hy)
			.setUv2(r, m)
			.setNormal(0f, 0f, 1f);
	}

	@Override
	public RenderPipeline pipeline() {
		return blur != null ? UiPipelines.ROUNDED_BLUR : UiPipelines.ROUNDED_RECT;
	}

	@Override
	public TextureSetup textureSetup() {
		return blur != null ? TextureSetup.singleTexture(blur, PostFx.linearSampler()) : TextureSetup.noTexture();
	}
}
