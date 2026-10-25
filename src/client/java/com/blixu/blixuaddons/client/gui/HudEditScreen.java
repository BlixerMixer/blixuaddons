package com.blixu.blixuaddons.client.gui;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.hud.HudElement;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.render.Ui;
import com.blixu.blixuaddons.client.render.UiFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD editor: the menu steps aside and every HUD element from our modules shows with sample data.
 * Drag to move (snaps to the screen's centre lines), scroll over one to resize, Esc or Done saves.
 */
public class HudEditScreen extends Screen {
	private static final float SNAP = 4;

	private final List<HudElement> elements = new ArrayList<>();
	private @Nullable HudElement dragging;
	private float grabX, grabY;
	private boolean snapX, snapY;

	public HudEditScreen() {
		super(Component.literal("HUD Editor"));
		for (Module m : ModuleManager.all()) elements.addAll(m.hudElements());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// leave the world and the live HUD visible; only a faint grid
		Ui.rect(g, 0, 0, width, height, 0, 0x30000000);
		for (float x = 0; x < width; x += 20) Ui.rect(g, x, 0, 0.5f, height, 0, 0x0CFFFFFF);
		for (float y = 0; y < height; y += 20) Ui.rect(g, 0, y, width, 0.5f, 0, 0x0CFFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (snapX) Ui.rect(g, width / 2f - 0.5f, 0, 1, height, 0, 0x808B5CF6);
		if (snapY) Ui.rect(g, 0, height / 2f - 0.5f, width, 1, 0, 0x808B5CF6);
		HudElement hover = at(mouseX, mouseY);
		for (HudElement e : elements) {
			if (!e.shown()) continue;
			e.drawSample(g, width, height);
			float[] b = e.box(width, height);
			boolean hot = e == hover || e == dragging;
			Ui.rect(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 3, hot ? 0x228B5CF6 : 0x10FFFFFF);
			Ui.outline(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 3, hot ? 1f : 0.6f, hot ? Theme.ACCENT_GLOW : 0x60FFFFFF);
			float lw = Ui.width(e.name(), UiFont.SMALL) + 8;
			float ly = b[1] - 13 < 0 ? b[1] + b[3] + 3 : b[1] - 13;
			Ui.rect(g, b[0] - 2, ly, lw, 10, 3, hot ? Theme.ACCENT : 0xC0101218);
			Ui.text(g, e.name(), b[0] + 2, ly + 1.5f, Theme.TEXT, UiFont.SMALL);
		}
		// hint bar + Done
		String hint = "HUD Editor  ·  drag to move  ·  scroll to resize  ·  Esc to save";
		float hw = Ui.width(hint, UiFont.BODY) + 80, hx = (width - hw) / 2f;
		Ui.shadow(g, hx, 7, hw, 20, 6, 10, 0x90000000);
		Ui.rect(g, hx, 6, hw, 20, 6, 0xF0101218);
		Ui.outline(g, hx, 6, hw, 20, 6, 0.75f, Theme.BORDER);
		Ui.text(g, hint, hx + 10, 12, Theme.TEXT_SECONDARY, UiFont.BODY);
		float dx = hx + hw - 52;
		boolean overDone = Ui.hovered(mouseX, mouseY, dx, 9, 46, 14);
		Ui.rect(g, dx, 9, 46, 14, 5, overDone ? Theme.ACCENT_GLOW : Theme.ACCENT);
		Ui.textCentered(g, "Done", dx + 23, 12, 0xFFFFFFFF, UiFont.SEMIBOLD);
		if (elements.stream().noneMatch(HudElement::shown)) {
			Ui.textCentered(g, "No HUD elements are on. Switch on Active Modules HUD or Voidgloom Seraph.", width / 2f, height / 2f, Theme.TEXT_SECONDARY, UiFont.BODY);
		}
	}

	private @Nullable HudElement at(double mx, double my) {
		for (int i = elements.size() - 1; i >= 0; i--) {
			HudElement e = elements.get(i);
			if (!e.shown()) continue;
			float[] b = e.box(width, height);
			if (Ui.hovered(mx, my, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4)) return e;
		}
		return null;
	}

	private boolean overDone(double mx, double my) {
		String hint = "HUD Editor  ·  drag to move  ·  scroll to resize  ·  Esc to save";
		float hw = Ui.width(hint, UiFont.BODY) + 80, hx = (width - hw) / 2f;
		return Ui.hovered(mx, my, hx + hw - 52, 9, 46, 14);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (overDone(event.x(), event.y())) {
			onClose();
			return true;
		}
		HudElement e = at(event.x(), event.y());
		if (e != null) {
			float[] b = e.box(width, height);
			dragging = e;
			grabX = (float) event.x() - b[0];
			grabY = (float) event.y() - b[1];
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(event, dx, dy);
		float[] b = dragging.box(width, height);
		float x = (float) event.x() - grabX, y = (float) event.y() - grabY;
		// snap the box's centre to the screen's centre lines
		snapX = Math.abs(x + b[2] / 2 - width / 2f) < SNAP;
		snapY = Math.abs(y + b[3] / 2 - height / 2f) < SNAP;
		if (snapX) x = width / 2f - b[2] / 2;
		if (snapY) y = height / 2f - b[3] / 2;
		x = Math.max(0, Math.min(width - b[2], x));
		y = Math.max(0, Math.min(height - b[3], y));
		dragging.moveTo(x, y, width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		snapX = snapY = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		HudElement e = at(mouseX, mouseY);
		if (e != null) e.resize((float) scrollY);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		ConfigManager.flush();
		minecraft.gui.setScreen(new ClickGuiScreen());
	}
}
