package com.blixu.blixuaddons.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Minecraft's own font, scaled, with a crisp black outline (a black copy in 8 directions under the text). */
public final class McText {
	private McText() {}

	private static final int[][] OUTLINE = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

	public record Part(String text, int color) {}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Width at scale 1 (GUI pixels). */
	public static int width(String s) {
		return font().width(s);
	}

	public static float width(List<Part> parts) {
		float w = 0;
		for (Part p : parts) w += width(p.text());
		return w;
	}

	/** One line centred on cx, top at y, {@code scale} x the normal size. */
	public static void centered(GuiGraphicsExtractor g, String text, float cx, float y, float scale, int color, int outline) {
		centered(g, List.of(new Part(text, color)), cx, y, scale, outline);
	}

	/** Several coloured pieces on one line, centred on cx. The outline takes each piece's alpha. */
	public static void centered(GuiGraphicsExtractor g, List<Part> parts, float cx, float y, float scale, int outline) {
		g.pose().pushMatrix();
		g.pose().translate(cx - width(parts) * scale / 2f, y);
		g.pose().scale(scale, scale);
		int x = 0;
		for (Part p : parts) {
			if ((p.color() >>> 24) != 0) {
				Component c = Component.literal(p.text());
				int oc = ((Math.round((p.color() >>> 24) * ((outline >>> 24) / 255f))) << 24) | (outline & 0xFFFFFF);
				for (int[] o : OUTLINE) g.text(font(), c, x + o[0], o[1], oc, false);
				g.text(font(), c, x, 0, p.color(), false);
			}
			x += width(p.text());
		}
		g.pose().popMatrix();
	}

	/** Left-aligned version. */
	public static void left(GuiGraphicsExtractor g, List<Part> parts, float x, float y, float scale, int outline) {
		centered(g, parts, x + width(parts) * scale / 2f, y, scale, outline);
	}
}
