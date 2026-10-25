package com.blixu.blixuaddons.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

/**
 * A 5x7 pixel font drawn with our own GLSL: in the GUI as rounded-rect pipeline rects, in the world as
 * camera-facing quads in a world batch. Each row's runs of lit pixels become one rect/quad, so a glyph
 * is a handful of draws, all batched. Used for the big screen alerts and the floating world labels.
 */
public final class PixelFont {
	private PixelFont() {}

	private static final Map<Character, String[]> GLYPHS = new HashMap<>();

	private static void g(char c, String... rows) {
		GLYPHS.put(c, rows);
	}

	static {
		g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
		g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
		g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
		g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
		g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
		g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
		g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####");
		g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
		g('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
		g('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..");
		g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
		g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
		g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
		g('N', "#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#");
		g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
		g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
		g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
		g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
		g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
		g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
		g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
		g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
		g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#.");
		g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
		g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
		g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
		g('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
		g('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
		g('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
		g('3', "####.", "....#", "....#", ".###.", "....#", "....#", "####.");
		g('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
		g('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
		g('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.");
		g('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
		g('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
		g('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..");
		g('.', ".", ".", ".", ".", ".", "#", "#");
		g('!', "#", "#", "#", "#", "#", ".", "#");
		g(':', ".", "#", "#", ".", "#", "#", ".");
		g('/', "....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#....");
		g('%', "##..#", "##.#.", "...#.", "..#..", ".#...", ".#.##", "#..##");
		g('-', "....", "....", "....", "####", "....", "....", "....");
		g('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..");
	}

	/** Width in pixels (glyph cells) of {@code text}: glyph width + 1 gap, 3 for a space. */
	public static int cells(String text) {
		int w = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = Character.toUpperCase(text.charAt(i));
			String[] gl = GLYPHS.get(c);
			w += gl == null ? 3 : gl[0].length() + 1;
		}
		return Math.max(0, w - 1);
	}

	public static float width(String text, float px) {
		return cells(text) * px;
	}

	/** GUI text centred on (cx, cy), {@code px} GUI pixels per font pixel, optional drop shadow (colour / 4 like vanilla). */
	public static void drawCentered(GuiGraphicsExtractor g, String text, float cx, float cy, float px, int color, boolean shadow) {
		float x0 = cx - width(text, px) / 2f, y0 = cy - 3.5f * px;
		if (shadow) {
			int sh = (color & 0xFF000000) | ((color >> 2) & 0x3F3F3F);
			draw(g, text, x0 + px * 0.6f, y0 + px * 0.6f, px, sh);
		}
		draw(g, text, x0, y0, px, color);
	}

	public static void draw(GuiGraphicsExtractor g, String text, float x, float y, float px, int color) {
		for (int i = 0; i < text.length(); i++) {
			char c = Character.toUpperCase(text.charAt(i));
			String[] gl = GLYPHS.get(c);
			if (gl == null) {
				x += 3 * px;
				continue;
			}
			for (int row = 0; row < 7; row++) {
				String r = gl[row];
				int start = -1;
				for (int col = 0; col <= r.length(); col++) {
					boolean on = col < r.length() && r.charAt(col) == '#';
					if (on && start < 0) start = col;
					if (!on && start >= 0) {
						Ui.rect(g, x + start * px, y + row * px, (col - start) * px + 0.02f, px + 0.02f, 0, color);
						start = -1;
					}
				}
			}
			x += (gl[0].length() + 1) * px;
		}
	}

	/** World text centred above (x, y, z), facing the camera; {@code px} = world size of one font pixel. */
	public static void drawWorld(WorldCtx ctx, GeoBatch b, String text, double x, double y, double z, float px, int color) {
		float w = width(text, px);
		float ox = ctx.rx(x), oy = ctx.ry(y), oz = ctx.rz(z);
		float rxv = ctx.right.x, ryv = ctx.right.y, rzv = ctx.right.z, uxv = ctx.up.x, uyv = ctx.up.y, uzv = ctx.up.z;
		float u = -w / 2f;
		for (int i = 0; i < text.length(); i++) {
			char c = Character.toUpperCase(text.charAt(i));
			String[] gl = GLYPHS.get(c);
			if (gl == null) {
				u += 3 * px;
				continue;
			}
			for (int row = 0; row < 7; row++) {
				String r = gl[row];
				float v1 = (3.5f - row) * px, v0 = v1 - px;
				int start = -1;
				for (int col = 0; col <= r.length(); col++) {
					boolean on = col < r.length() && r.charAt(col) == '#';
					if (on && start < 0) start = col;
					if (!on && start >= 0) {
						float u0 = u + start * px, u1 = u + col * px;
						b.quad(ox + rxv * u0 + uxv * v0, oy + ryv * u0 + uyv * v0, oz + rzv * u0 + uzv * v0,
							ox + rxv * u1 + uxv * v0, oy + ryv * u1 + uyv * v0, oz + rzv * u1 + uzv * v0,
							ox + rxv * u1 + uxv * v1, oy + ryv * u1 + uyv * v1, oz + rzv * u1 + uzv * v1,
							ox + rxv * u0 + uxv * v1, oy + ryv * u0 + uyv * v1, oz + rzv * u0 + uzv * v1, color);
						start = -1;
					}
				}
			}
			u += (gl[0].length() + 1) * px;
		}
	}

	/** Self-check: every glyph is 7 rows of equal width. */
	public static void selfCheck() {
		GLYPHS.forEach((c, rows) -> {
			if (rows.length != 7) throw new IllegalStateException("PixelFont glyph " + c + " has " + rows.length + " rows");
			for (String r : rows) if (r.length() != rows[0].length()) throw new IllegalStateException("PixelFont glyph " + c + " is ragged");
		});
		if (cells("HI") != 11 || cells("1.5") != 13) throw new IllegalStateException("PixelFont width check failed: " + cells("HI") + " " + cells("1.5"));
	}
}
