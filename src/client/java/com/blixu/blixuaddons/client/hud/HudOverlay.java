package com.blixu.blixuaddons.client.hud;

import com.blixu.blixuaddons.client.gui.Theme;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.ActiveModulesHud;
import com.blixu.blixuaddons.client.module.impl.hud.HudStyle;
import com.blixu.blixuaddons.client.render.Ui;
import com.blixu.blixuaddons.client.render.UiFont;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Our in-game HUD. Modules draw their own HUD parts through {@link Module#onHud}. */
public final class HudOverlay {
	private HudOverlay() {}

	/** Slide-in amount per module, so the list animates when things turn on/off. */
	private static final Map<Module, Float> SLIDE = new HashMap<>();
	private static final List<Module> SORTED = new ArrayList<>();
	private static long lastFrame = System.nanoTime();

	public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;

		Minecraft mc = Minecraft.getInstance();
		if (mc.getDebugOverlay().showDebugScreen()) return;

		long t0 = System.nanoTime();
		ModuleManager.forEnabled(m -> m.onHud(g, dt));
		com.blixu.blixuaddons.client.feature.perf.FrameStats.hudNanos = System.nanoTime() - t0;

		ActiveModulesHud hud = ModuleManager.get(ActiveModulesHud.class);
		if (!hud.isEnabled()) return;
		drawActiveModules(g, hud, dt);
	}

	private static void drawActiveModules(GuiGraphicsExtractor g, ActiveModulesHud hud, float dt) {
		if (SORTED.isEmpty()) {
			for (Module m : ModuleManager.all()) if (m != hud) SORTED.add(m);
			SORTED.sort(Comparator.comparingInt((Module m) -> Ui.width(m.name(), UiFont.BODY)).reversed());
		}
		HudStyle style = HudPanel.style();
		boolean styled = style.isEnabled();
		float pad = styled ? style.padding.getF() : 5;

		boolean rightSide = hud.rightSide.get();
		float edge = hud.edgeX(g.guiWidth());
		float y = 4 + g.guiHeight() * hud.posY.getF() / 100f;
		// size: scale around the corner the list hangs from
		float k = hud.size.getF();
		g.pose().pushMatrix();
		g.pose().translate(edge, y);
		g.pose().scale(k, k);
		g.pose().translate(-edge, -y);
		float rowH = 12;
		float speed = styled ? style.animSpeed() : 14f;
		for (Module m : SORTED) {
			float slide = Ui.approach(SLIDE.getOrDefault(m, m.isEnabled() ? 1f : 0f), m.isEnabled() ? 1f : 0f, dt, speed);
			SLIDE.put(m, slide);
			if (slide < 0.01f) continue;

			int textW = Ui.width(m.name(), UiFont.BODY);
			float w = textW + pad * 2;
			// Slides in from the screen edge.
			float x = rightSide ? edge - w * slide : edge - w * (1 - slide);
			float h = rowH * slide;

			if (hud.background.get()) {
				if (styled) HudPanel.draw(g, rightSide ? x : x - 4, y, w + 4, h, 0, slide, style.blurModules.get());
				else Ui.rect(g, rightSide ? x : x - 4, y, w + 4, h, 3, Ui.alpha(Theme.HUD_BG, slide));
			}
			if (hud.accentBar.get()) {
				float barX = rightSide ? edge + 1 : edge - 3;
				int accent = styled ? style.accent.argb() : Theme.ACCENT;
				int top = styled ? Colors.scale(accent, 1.25f) : Theme.ACCENT_GLOW;
				Ui.gradientV(g, barX, y + 1, 2, h - 2, 1, Ui.alpha(top, slide), Ui.alpha(accent, slide));
			}
			HudPanel.text(g, m.name(), x + pad, y + 2, Ui.alpha(Theme.TEXT, slide), UiFont.BODY);
			y += h + 2 * slide;
		}
		g.pose().popMatrix();
	}
}
