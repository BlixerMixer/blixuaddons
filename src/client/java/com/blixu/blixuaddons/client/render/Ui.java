package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.mixin.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * blixuAddons drawing toolkit. Everything here goes through our own GLSL shader
 * (rounded shapes) or Inter TrueType fonts, so it doesn't look like vanilla Minecraft.
 *
 * Coordinates are GUI units (the same ones Screens use). Colours are 0xAARRGGBB.
 */
public final class Ui {
	private Ui() {}

	private static final Deque<ScreenRectangle> SCISSORS = new ArrayDeque<>();

	// ---------------------------------------------------------------- shapes

	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		shape(g, x, y, w, h, radius, 0f, color, color, color, color);
	}

	/** Top-to-bottom gradient. */
	public static void gradientV(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int top, int bottom) {
		shape(g, x, y, w, h, radius, 0f, top, top, bottom, bottom);
	}

	/** Left-to-right gradient. */
	public static void gradientH(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int left, int right) {
		shape(g, x, y, w, h, radius, 0f, left, right, right, left);
	}

	/** Rounded outline drawn just inside the box. */
	public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float thickness, int color) {
		shape(g, x, y, w, h, radius, -Math.max(thickness, 0.25f), color, color, color, color);
	}

	/** Soft shadow / glow around a box. {@code softness} = how far it fades out, in GUI pixels. */
	public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float softness, int color) {
		shape(g, x, y, w, h, radius, Math.max(softness, 0.5f), color, color, color, color);
	}

	/**
	 * Frosted-glass box: the shared blurred world behind it, tinted by {@code tint}
	 * (its alpha = tint strength). Falls back to a plain translucent box if blur is unavailable.
	 */
	public static void frosted(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int tint) {
		if (w <= 0 || h <= 0) return;
		var blur = com.blixu.blixuaddons.client.render.PostFx.requestBlur();
		if (blur == null) {
			rect(g, x, y, w, h, radius, (Math.max(tint >>> 24, 0xB0) << 24) | (tint & 0xFFFFFF));
			return;
		}
		((GuiGraphicsAccessor) g).blixuaddons$guiRenderState().addGuiElement(
			RoundedRectState.of(g.pose(), x, y, w, h, radius, 0f, tint, tint, tint, tint, SCISSORS.peek(), blur));
	}

	/** Text with a soft coloured glow behind it (a blurred capsule sized to the text). */
	public static void glowText(GuiGraphicsExtractor g, String text, float x, float y, int color, int glow, float strength, UiFont font) {
		if (strength > 0.01f && (glow >>> 24) > 0) {
			int w = width(text, font);
			shadow(g, x - 1, y - 0.5f, w + 2, 9, 4, 3 + 4 * strength, alpha(glow, Math.min(1f, 0.55f * strength)));
		}
		text(g, text, x, y, color, font);
	}

	/** 2D line (a rotated thin box) with a colour gradient from start to end. */
	public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float width, int c1, int c2) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.01f) return;
		g.pose().pushMatrix();
		g.pose().translate(x1, y1);
		g.pose().rotate((float) Math.atan2(dy, dx));
		gradientH(g, 0, -width / 2f, len, width, width / 2f, c1, c2);
		g.pose().popMatrix();
	}

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
		rect(g, cx - r, cy - r, r * 2, r * 2, r, color);
	}

	public static void shape(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float mode,
							 int tl, int tr, int br, int bl) {
		if (w <= 0 || h <= 0) return;
		((GuiGraphicsAccessor) g).blixuaddons$guiRenderState().addGuiElement(
			RoundedRectState.of(g.pose(), x, y, w, h, radius, mode, tl, tr, br, bl, SCISSORS.peek())
		);
	}

	// ---------------------------------------------------------------- text

	public static Component styled(String text, UiFont font) {
		return Component.literal(text).withStyle(s -> s.withFont(font.description));
	}

	public static void text(GuiGraphicsExtractor g, String text, float x, float y, int color, UiFont font) {
		if ((color >>> 24) == 0) return;
		g.pose().pushMatrix();
		g.pose().translate(x - (int) x, y - (int) y);
		g.text(Minecraft.getInstance().font, styled(text, font), (int) x, (int) y, color, false);
		g.pose().popMatrix();
	}

	public static void textCentered(GuiGraphicsExtractor g, String text, float cx, float y, int color, UiFont font) {
		text(g, text, cx - width(text, font) / 2f, y, color, font);
	}

	private static final java.util.Map<UiFont, java.util.HashMap<String, Integer>> WIDTHS = new java.util.EnumMap<>(UiFont.class);

	/** Text width, cached: labels are measured every frame, the font only needs to do it once. */
	public static int width(String text, UiFont font) {
		java.util.HashMap<String, Integer> map = WIDTHS.computeIfAbsent(font, f -> new java.util.HashMap<>());
		Integer w = map.get(text);
		if (w == null) {
			if (map.size() > 4096) map.clear(); // changing numbers (timers, HP) would grow it forever
			w = Minecraft.getInstance().font.width(styled(text, font));
			map.put(text, w);
		}
		return w;
	}

	/** Forget cached widths (fonts reloaded). */
	public static void clearWidthCache() {
		WIDTHS.clear();
	}

	/** Cuts text to fit {@code maxWidth}, adding "…" when shortened. */
	public static String ellipsize(String text, float maxWidth, UiFont font) {
		if (width(text, font) <= maxWidth) return text;
		String dots = "…";
		int end = text.length();
		while (end > 0 && width(text.substring(0, end) + dots, font) > maxWidth) end--;
		return text.substring(0, end).stripTrailing() + dots;
	}

	// ---------------------------------------------------------------- icons

	/** Draws one of our white icons (textures/gui/icon/NAME.png) tinted with {@code color}. */
	public static void icon(GuiGraphicsExtractor g, String name, float x, float y, float size, int color) {
		Identifier tex = BlixuAddons.id("textures/gui/icon/" + name + ".png");
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 64f, size / 64f);
		g.blit(UiPipelines.GUI_ICON, tex, 0, 0, 0f, 0f, 64, 64, 64, 64, 64, 64, color);
		g.pose().popMatrix();
	}

	// ---------------------------------------------------------------- clipping

	/** Clip everything (our shapes, text, icons) to a rectangle. Always pair with {@link #popScissor}. */
	public static void pushScissor(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.enableScissor(x0, y0, x1, y1);
		ScreenRectangle rect = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(g.pose());
		ScreenRectangle parent = SCISSORS.peek();
		SCISSORS.push(parent != null ? orEmpty(rect.intersection(parent)) : rect);
	}

	/** The innermost clip rectangle (for custom render states), or null. */
	public static @Nullable ScreenRectangle currentScissor() {
		return SCISSORS.peek();
	}

	public static void popScissor(GuiGraphicsExtractor g) {
		g.disableScissor();
		SCISSORS.poll();
	}

	private static ScreenRectangle orEmpty(@Nullable ScreenRectangle r) {
		return r != null ? r : ScreenRectangle.empty();
	}

	// ---------------------------------------------------------------- colour + animation helpers

	public static int alpha(int color, float alpha) {
		int a = Mth.clamp(Math.round(((color >>> 24) & 0xFF) * alpha), 0, 255);
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		return ARGB.srgbLerp(Mth.clamp(t, 0f, 1f), a, b);
	}

	/** Frame-rate independent smoothing towards a target (0..1 style values). */
	public static float approach(float current, float target, float deltaSeconds, float speed) {
		return current + (target - current) * (1f - (float) Math.exp(-speed * deltaSeconds));
	}

	public static boolean hovered(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
