package com.blixu.blixuaddons.client.gui;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.hud.HudPanel;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.hud.HudStyle;
import com.blixu.blixuaddons.client.module.setting.ActionSetting;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.KeyBindSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.module.setting.SectionSetting;
import com.blixu.blixuaddons.client.module.setting.Setting;
import com.blixu.blixuaddons.client.module.setting.StringSetting;
import com.blixu.blixuaddons.client.render.Ui;
import com.blixu.blixuaddons.client.render.UiFont;
import com.blixu.blixuaddons.client.style.Colors;
import com.blixu.blixuaddons.client.style.Palettes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * The blixuAddons menu (Right Shift). Sidebar with categories on the left, module cards on the right.
 *
 * Everything is drawn every frame; while drawing we record clickable areas ({@link Hit}) so mouse
 * handling always matches what's on screen. Right-click a setting row to reset it to its default.
 */
public class ClickGuiScreen extends Screen {
	private static final float SIDEBAR = 118;
	private static final float CARD_H = 44;
	private static final float ROW_H = 18;
	private static final float GAP = 8;

	private static Category lastCategory = Category.VISUALS;

	private Category category = lastCategory;
	private String search = "";
	private boolean searchFocused;
	private @Nullable Module expanded;

	/** Text field being typed into (a StringSetting, the hex box, or a preset name). */
	private @Nullable TextTarget typing;
	private @Nullable KeyBindSetting capturing;
	/** Waiting for the new menu key (footer, bottom left). */
	private boolean capturingMenuKey;
	/** Full text of a shortened label under the cursor this frame, drawn on top at the end. */
	private @Nullable String tooltip;
	private float tipX, tipY;
	private @Nullable ColorSetting picking;
	private String hexDraft = "";
	private final Map<Module, String> presetNames = new HashMap<>();
	private final Map<Module, String> presetStatus = new HashMap<>();

	private float scroll;
	private float scrollTarget;
	private float openAnim;
	private long lastFrame = System.nanoTime();

	private final Map<Object, Float> hoverAnim = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private @Nullable Hit dragging;

	// Window transform for the open animation, used to map the mouse back.
	private float scale = 1f;
	private float pivotX;
	private float pivotY;

	/** Player Visual Editor mode: compact panel top-left, world visible, camera orbits you. */
	private final boolean editor;
	/** World Visuals editor: same compact panel, first-person view straight ahead, HUD hidden (like F1). */
	private final boolean worldEditor;

	public ClickGuiScreen() {
		this(false, false);
	}

	public ClickGuiScreen(boolean editor) {
		this(editor, false);
	}

	public ClickGuiScreen(boolean editor, boolean world) {
		super(Component.literal(world ? "World Visual Editor" : editor ? "Player Visual Editor" : "blixuAddons"));
		this.editor = editor || world;
		this.worldEditor = world;
	}

	@Override
	protected void init() {
		if (worldEditor) com.blixu.blixuaddons.client.feature.PlayerEditorCamera.beginWorld();
		else if (editor) com.blixu.blixuaddons.client.feature.PlayerEditorCamera.begin();
	}

	/** Everything that changes how your own character looks. */
	private List<Module> editorModules() {
		return ModuleManager.in(worldEditor ? Category.WORLD : Category.COSMETICS);
	}

	@Override
	public void onClose() {
		if (editor) {
			lastCategory = Category.PLAYER;
			minecraft.gui.setScreen(new ClickGuiScreen());
			return;
		}
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		if (editor) com.blixu.blixuaddons.client.feature.PlayerEditorCamera.end();
		else lastCategory = category;
		ConfigManager.save();
		ConfigManager.flush();
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (editor) return; // keep the world (and you) fully visible
		extractBlurredBackground(g);
		Ui.rect(g, 0, 0, width, height, 0, Ui.alpha(0x99000000, openAnim));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int rawMouseX, int rawMouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		openAnim = Ui.approach(openAnim, 1f, dt, 16f);
		scroll = Ui.approach(scroll, scrollTarget, dt, 18f);
		hits.clear();
		tooltip = null;

		boolean slayers = slayerPage();
		float w = editor ? Math.min(290, width * 0.45f) : Math.min(width - 24, 560);
		float h = editor ? height - 16 : Math.min(height - 24, 340);
		float x0 = editor ? 8 : (width - w) / 2f;
		float y0 = editor ? 8 : (height - h) / 2f;

		scale = 0.94f + 0.06f * openAnim;
		pivotX = editor ? x0 : width / 2f;
		pivotY = editor ? y0 : height / 2f;
		double mx = toLocalX(rawMouseX);
		double my = toLocalY(rawMouseY);

		g.pose().pushMatrix();
		g.pose().translate(pivotX, pivotY);
		g.pose().scale(scale, scale);
		g.pose().translate(-pivotX, -pivotY);

		// Window: deep drop shadow, faint accent ambience, top highlight hairline
		Ui.shadow(g, x0, y0 + 6, w, h, 12, 30, Theme.SHADOW);
		Ui.shadow(g, x0, y0, w, h, 12, 46, Ui.alpha(Theme.ACCENT, 0.10f * openAnim));
		HudStyle style = HudPanel.style();
		if (style.isEnabled() && style.blur.get() && style.blurMenu.get()) {
			Ui.frosted(g, x0, y0, w, h, 10, Colors.setAlpha(Theme.BG_BASE, 0.82f));
		} else {
			Ui.rect(g, x0, y0, w, h, 10, Theme.BG_BASE);
		}

		if (editor) {
			drawContent(g, x0, y0, w, h, mx, my, dt);
		} else {
			drawSidebar(g, x0, y0, h, mx, my, dt);
			if (slayers) drawSlayers(g, x0 + SIDEBAR, y0, w - SIDEBAR, h, mx, my, dt);
			else drawContent(g, x0 + SIDEBAR, y0, w - SIDEBAR, h, mx, my, dt);
		}

		Ui.outline(g, x0, y0, w, h, 10, 0.75f, Theme.BORDER);
		Ui.gradientH(g, x0 + 14, y0, w / 2f - 14, 0.75f, 0, 0x00FFFFFF, 0x24FFFFFF);
		Ui.gradientH(g, x0 + w / 2f, y0, w / 2f - 14, 0.75f, 0, 0x24FFFFFF, 0x00FFFFFF);
		if (picking != null) drawPicker(g, picking, x0 + w / 2f - 110, y0 + h / 2f - 92, mx, my, dt);
		else if (tooltip != null) drawTooltip(g, tooltip, tipX, tipY);
		g.pose().popMatrix();
	}

	/** Text cut to maxW; while the cursor is over a shortened one, its full text shows as a tooltip. */
	private void textFit(GuiGraphicsExtractor g, String text, float x, float y, float maxW, int color, UiFont font,
						 double mx, double my, boolean canHover) {
		String shown = Ui.ellipsize(text, maxW, font);
		Ui.text(g, shown, x, y, color, font);
		if (canHover && !shown.equals(text) && Ui.hovered(mx, my, x, y - 1, Ui.width(shown, font), 10)) {
			tooltip = text;
			tipX = (float) mx;
			tipY = (float) my;
		}
	}

	/** Wrapped full text in a small card next to the cursor, kept on screen. */
	private void drawTooltip(GuiGraphicsExtractor g, String text, float mx, float my) {
		float maxW = 220;
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && Ui.width(next, UiFont.SMALL) > maxW) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		float w = 0;
		for (String l : lines) w = Math.max(w, Ui.width(l, UiFont.SMALL));
		float bw = w + 12, bh = lines.size() * 9 + 9;
		float bx = Math.min(mx + 10, width - bw - 4), by = Math.min(my + 12, height - bh - 4);
		Ui.shadow(g, bx, by + 2, bw, bh, 5, 10, Theme.SHADOW);
		Ui.rect(g, bx, by, bw, bh, 5, Theme.BG_CARD);
		Ui.outline(g, bx, by, bw, bh, 5, 0.75f, Theme.BORDER);
		for (int i = 0; i < lines.size(); i++) Ui.text(g, lines.get(i), bx + 6, by + 5 + i * 9, Theme.TEXT, UiFont.SMALL);
	}

	private void drawSidebar(GuiGraphicsExtractor g, float x, float y, float h, double mx, double my, float dt) {
		Ui.rect(g, x, y, SIDEBAR, h, 10, Ui.alpha(Theme.BG_PANEL, 0.92f));
		Ui.rect(g, x + SIDEBAR - 12, y, 12, h, 0, Ui.alpha(Theme.BG_PANEL, 0.92f)); // square off the right edge
		Ui.rect(g, x + SIDEBAR - 0.75f, y, 0.75f, h, 0, Theme.BORDER);

		// Brand
		float bx = x + 12;
		float by = y + 11;
		Ui.shadow(g, bx, by, 17, 17, 5, 6, Ui.alpha(Theme.ACCENT, 0.45f));
		Ui.shape(g, bx, by, 17, 17, 5, 0, Theme.ACCENT_GLOW, Theme.ACCENT, 0xFF6D3BE0, Theme.ACCENT);
		Ui.textCentered(g, "B", bx + 8.5f, by + 4.5f, Theme.TEXT, UiFont.SEMIBOLD);
		Ui.text(g, "blixuAddons", bx + 23, by + 1, Theme.TEXT, UiFont.SEMIBOLD);
		Ui.text(g, "v1.0.0 · 26.2", bx + 23, by + 10.5f, Theme.TEXT_MUTED, UiFont.SMALL);

		Ui.text(g, "CATEGORIES", x + 12, y + 37, Theme.TEXT_MUTED, UiFont.SMALL);

		float ty = y + 47;
		int cats = Category.values().length;
		boolean footer = h - 47 - cats * 19 >= 36 + 22;
		float th = footer ? 17 : Math.max(10, Math.min(17, (h - 52 - 24) / cats - 2));
		for (Category c : Category.values()) {
			float tx = x + 7;
			float tw = SIDEBAR - 14;
			boolean active = c == category && search.isEmpty();
			float hover = animateHover(c, Ui.hovered(mx, my, tx, ty, tw, th), dt);
			if (active) {
				Ui.gradientH(g, tx, ty, tw, th, 6, Ui.alpha(Theme.ACCENT, 0.30f), Ui.alpha(Theme.ACCENT, 0.05f));
				Ui.shadow(g, tx, ty + 3, 2.5f, th - 6, 1.25f, 4, Ui.alpha(Theme.ACCENT_GLOW, 0.7f));
				Ui.rect(g, tx, ty + 3, 2.5f, th - 6, 1.25f, Theme.ACCENT_GLOW);
			} else if (hover > 0.01f) {
				Ui.rect(g, tx, ty, tw, th, 6, Ui.alpha(Theme.HOVER, hover));
			}
			long enabledHere = ModuleManager.in(c).stream().filter(Module::isEnabled).count();
			if (enabledHere > 0) {
				String n = Long.toString(enabledHere);
				float bw = Math.max(11, Ui.width(n, UiFont.SMALL) + 6);
				float badgeX = tx + tw - bw - 4, badgeY = ty + (th - 9) / 2f;
				Ui.rect(g, badgeX, badgeY, bw, 9, 4.5f, active ? Ui.alpha(Theme.ACCENT, 0.45f) : Theme.BG_CARD);
				Ui.textCentered(g, n, badgeX + bw / 2f, badgeY + 1f, active ? Theme.TEXT : Theme.TEXT_MUTED, UiFont.SMALL);
			}
			int fg = active ? Theme.TEXT : Ui.mix(Theme.TEXT_SECONDARY, Theme.TEXT, hover);
			Ui.icon(g, c.icon, tx + 7, ty + (th - 9) / 2f, 9, active ? Theme.ACCENT_GLOW : fg);
			Ui.text(g, c.title, tx + 21, ty + (th - 8) / 2f, fg, active ? UiFont.SEMIBOLD : UiFont.BODY);
			hits.add(Hit.click(tx, ty, tw, th, button -> selectCategory(c)));
			ty += th + 2;
		}

		// HUD editor: the menu steps aside so our HUD parts can be dragged around
		float ey = footer ? y + h - 30 - 22 : y + h - 24;
		float eh = 18, ex = x + 7, ew = SIDEBAR - 14;
		float editHover = animateHover("edit_hud", Ui.hovered(mx, my, ex, ey, ew, eh), dt);
		Ui.gradientH(g, ex, ey, ew, eh, 6, Ui.mix(Theme.ACCENT_SOFT, Theme.ACCENT, 0.35f + 0.4f * editHover), Theme.ACCENT_SOFT);
		Ui.outline(g, ex, ey, ew, eh, 6, 0.75f, Ui.mix(Theme.BORDER, Theme.ACCENT_GLOW, editHover));
		Ui.icon(g, "hud", ex + 7, ey + 4.5f, 9, Theme.ACCENT_GLOW);
		Ui.text(g, "Edit HUD layout", ex + 21, ey + 5, Theme.TEXT, UiFont.SEMIBOLD);
		hits.add(Hit.click(ex, ey, ew, eh, button -> minecraft.gui.setScreen(new HudEditScreen())));

		// Footer
		if (!footer) return;
		float fy = y + h - 30;
		// click to rebind the menu key
		float keyHover = animateHover("menu_key", Ui.hovered(mx, my, x + 7, fy, SIDEBAR - 14, 23), dt);
		Ui.rect(g, x + 7, fy, SIDEBAR - 14, 23, 6, capturingMenuKey ? Theme.ACCENT_SOFT : Ui.mix(Theme.BG_CARD, Theme.BG_CARD_HOVER, keyHover));
		if (capturingMenuKey) Ui.outline(g, x + 7, fy, SIDEBAR - 14, 23, 6, 0.75f, Theme.ACCENT);
		Ui.text(g, "Right-click row = reset", x + 13, fy + 4, Theme.TEXT_MUTED, UiFont.SMALL);
		String menuKey = capturingMenuKey ? "Press a key… (Esc cancels)"
			: "Menu key: " + com.blixu.blixuaddons.client.BlixuAddonsClient.openMenuKey().getTranslatedKeyMessage().getString();
		Ui.text(g, menuKey, x + 13, fy + 13, capturingMenuKey ? Theme.ACCENT_GLOW : Ui.mix(Theme.TEXT_SECONDARY, Theme.TEXT, keyHover), UiFont.SMALL);
		hits.add(Hit.click(x + 7, fy, SIDEBAR - 14, 23, button -> capturingMenuKey = true));
	}

	private void drawContent(GuiGraphicsExtractor g, float x, float y, float w, float h, double mx, double my, float dt) {
		float cx = x + 16;
		float cw = w - 32;
		boolean searching = !search.isEmpty();

		List<Module> modules = editor && !searching ? editorModules() : searching
			? ModuleManager.all().stream().filter(m -> matches(m, search)).toList()
			: ModuleManager.in(category);

		// Header: gradient icon badge + title
		String headerIcon = searching ? "search" : worldEditor ? Category.WORLD.icon : editor ? Category.COSMETICS.icon : category.icon;
		Ui.shadow(g, cx, y + 13, 20, 20, 6, 8, Ui.alpha(Theme.ACCENT, 0.4f));
		Ui.shape(g, cx, y + 13, 20, 20, 6, 0, Theme.ACCENT_GLOW, Theme.ACCENT, 0xFF5B3BD0, Theme.ACCENT);
		Ui.icon(g, headerIcon, cx + 5.5f, y + 18.5f, 9, 0xFFFFFFFF);
		Ui.text(g, searching ? "Search" : worldEditor ? "World Visuals" : editor ? "Player Visuals" : category.title, cx + 27, y + 14, Theme.TEXT, UiFont.TITLE);
		String subtitle = searching
			? modules.size() + (modules.size() == 1 ? " result" : " results") + " for \"" + search + "\""
			: worldEditor ? "Looking ahead, HUD hidden — changes show live" : editor ? "Camera orbits you — every change shows live" : category.subtitle;
		textFit(g, subtitle, cx + 27, y + 31, editor ? cw - 120 : cw - 167, Theme.TEXT_SECONDARY, UiFont.SMALL, mx, my, true);

		// Close button
		float bx = x + w - 16 - 18;
		float by = y + 13;
		float closeHover = animateHover("close", Ui.hovered(mx, my, bx, by, 18, 18), dt);
		Ui.rect(g, bx, by, 18, 18, 5, Ui.mix(Theme.BG_CARD, Theme.BG_CARD_HOVER, closeHover));
		Ui.icon(g, "close", bx + 5, by + 5, 8, Ui.mix(Theme.TEXT_SECONDARY, Theme.TEXT, closeHover));
		hits.add(Hit.click(bx, by, 18, 18, button -> onClose()));

		// Search box
		float sw = 124;
		float sx = bx - 6 - sw;
		if (!editor) {
		Ui.rect(g, sx, by, sw, 18, 5, Theme.BG_INPUT);
		Ui.outline(g, sx, by, sw, 18, 5, searchFocused ? 1f : 0.75f, searchFocused ? Theme.ACCENT : Theme.BORDER);
		Ui.icon(g, "search", sx + 6, by + 5, 8, Theme.TEXT_MUTED);
		if (search.isEmpty() && !searchFocused) {
			Ui.text(g, "Search modules…", sx + 18, by + 5.5f, Theme.TEXT_MUTED, UiFont.BODY);
		} else {
			drawFieldText(g, search, sx + 18, by + 5.5f, sw - 26, searchFocused);
		}
		hits.add(Hit.click(sx, by, sw, 18, button -> {
			searchFocused = true;
			typing = null;
		}));
		}

		// Enabled count
		long on = modules.stream().filter(Module::isEnabled).count();
		String count = on + " of " + modules.size() + " enabled";
		Ui.text(g, count, x + w - 16 - Ui.width(count, UiFont.SMALL), y + 38, Theme.TEXT_MUTED, UiFont.SMALL);

		// Module grid (scrollable)
		float gy = y + 50;
		float gh = y + h - 10 - gy;
		Ui.pushScissor(g, (int) (cx - 8), (int) gy, (int) (cx + cw + 8), (int) (gy + gh));

		if (modules.isEmpty()) {
			drawEmpty(g, cx, gy, cw, gh, searching);
		}

		boolean single = cw < 330;
		float colW = single ? cw : (cw - GAP) / 2f;
		float rowY = gy + 2 - scroll;
		int col = 0;
		float rowHeight = 0;
		boolean mouseInGrid = my >= gy && my < gy + gh && picking == null;
		for (Module m : modules) {
			boolean open = m == expanded;
			if (open && col == 1) {
				rowY += rowHeight + GAP;
				col = 0;
				rowHeight = 0;
			}
			float cardW = open ? cw : colW;
			float cardH = open ? CARD_H + (visibleSettings(m) + 1) * ROW_H + 10 : CARD_H;
			if (open && m.preview() != Module.Preview.NONE) cardH = Math.max(cardH, CARD_H + PREVIEW_H + 16);
			float cardX = cx + (open ? 0 : col * (colW + GAP));
			boolean visible = rowY + cardH > gy && rowY < gy + gh;
			if (visible) drawCard(g, m, cardX, rowY, cardW, cardH, mx, my, dt, mouseInGrid, gy, gy + gh);

			rowHeight = Math.max(rowHeight, cardH);
			if (open || col == 1 || single) {
				rowY += rowHeight + GAP;
				col = 0;
				rowHeight = 0;
			} else {
				col = 1;
			}
		}
		if (col == 1) rowY += rowHeight + GAP;

		float contentH = rowY + scroll - gy;
		float maxScroll = Math.max(0, contentH - gh);
		scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);

		Ui.popScissor(g);

		// Scrollbar
		if (maxScroll > 0) {
			float barH = Math.max(20, gh * gh / contentH);
			float barY = gy + (gh - barH) * (scroll / maxScroll);
			Ui.rect(g, x + w - 7, barY, 2.5f, barH, 1.25f, Theme.BORDER);
		}
	}

	// ================================================================== Slayers (full screen)

	private static final String[][] SLAYER_TABS = {
		{"Voidgloom", "Enderman", "", "Voidgloom Seraph"},
		{"Revenant", "Zombie", "Health HUD, Pestilence and Thrasher alerts, boss glow", "Revenant Horror"},
		{"Sven", "Wolf", "Pup glows, true-damage phase timer, boss HUD", "Sven Packmaster"},
		{"Tarantula", "Spider", "Egg and Tarantula Vermin glows, jump warnings", "Tarantula Broodfather"},
		{"Blaze", "Blaze", "Attunement colours, demon swap timer, fire pillars", "Inferno Demonlord"},
	};
	private static int slayerTab;

	private boolean slayerPage() {
		return !editor && search.isEmpty() && category == Category.SLAYERS;
	}

	/** The Slayers tab: the slayer module fills the whole content area (tabs per slayer, live preview, settings sections). */
	private void drawSlayers(GuiGraphicsExtractor g, float x, float y, float w, float h, double mx, double my, float dt) {
		float cx = x + 16, cw = w - 32;
		// header
		Ui.shadow(g, cx, y + 13, 20, 20, 6, 8, Ui.alpha(Theme.ACCENT, 0.4f));
		Ui.shape(g, cx, y + 13, 20, 20, 6, 0, Theme.ACCENT_GLOW, Theme.ACCENT, 0xFF5B3BD0, Theme.ACCENT);
		Ui.icon(g, Category.SLAYERS.icon, cx + 5.5f, y + 18.5f, 9, 0xFFFFFFFF);
		Ui.text(g, "Slayers", cx + 27, y + 14, Theme.TEXT, UiFont.TITLE);
		Ui.text(g, Category.SLAYERS.subtitle, cx + 27, y + 31, Theme.TEXT_SECONDARY, UiFont.SMALL);
		float bx = x + w - 16 - 18, by = y + 13;
		float closeHover = animateHover("close", Ui.hovered(mx, my, bx, by, 18, 18), dt);
		Ui.rect(g, bx, by, 18, 18, 5, Ui.mix(Theme.BG_CARD, Theme.BG_CARD_HOVER, closeHover));
		Ui.icon(g, "close", bx + 5, by + 5, 8, Ui.mix(Theme.TEXT_SECONDARY, Theme.TEXT, closeHover));
		hits.add(Hit.click(bx, by, 18, 18, button -> onClose()));

		// slayer tabs
		float tx = cx, ty = y + 46;
		for (int i = 0; i < SLAYER_TABS.length; i++) {
			String name = SLAYER_TABS[i][0];
			boolean soon = i > 0, active = i == slayerTab;
			float tw = Ui.width(name, UiFont.SEMIBOLD) + 14 + (soon ? Ui.width("SOON", UiFont.SMALL) + 9 : 0);
			float hover = animateHover("slayer_tab" + i, Ui.hovered(mx, my, tx, ty, tw, 18), dt);
			if (active) {
				Ui.gradientH(g, tx, ty, tw, 18, 9, Ui.alpha(Theme.ACCENT, 0.55f), Ui.alpha(Theme.ACCENT, 0.25f));
				Ui.outline(g, tx, ty, tw, 18, 9, 0.75f, Theme.ACCENT_GLOW);
			} else {
				Ui.rect(g, tx, ty, tw, 18, 9, Ui.mix(Theme.BG_CARD, Theme.BG_CARD_HOVER, hover));
				Ui.outline(g, tx, ty, tw, 18, 9, 0.75f, Theme.BORDER);
			}
			Ui.text(g, name, tx + 7, ty + 5, active ? Theme.TEXT : soon ? Theme.TEXT_MUTED : Theme.TEXT_SECONDARY, UiFont.SEMIBOLD);
			if (soon) {
				float sx = tx + tw - Ui.width("SOON", UiFont.SMALL) - 10;
				Ui.rect(g, sx, ty + 4, Ui.width("SOON", UiFont.SMALL) + 6, 10, 5, Ui.alpha(0xFFFFAA33, 0.25f));
				Ui.text(g, "SOON", sx + 3, ty + 5.5f, 0xFFFFC266, UiFont.SMALL);
			}
			final int tab = i;
			hits.add(Hit.click(tx, ty, tw, 18, button -> {
				slayerTab = tab;
				resetScroll();
			}));
			tx += tw + 5;
		}

		float gy = y + 72, gh = y + h - 10 - gy;
		boolean interactive = my >= gy && my < gy + gh && picking == null;
		Ui.pushScissor(g, (int) (cx - 8), (int) gy, (int) (cx + cw + 8), (int) (gy + gh));
		float top = gy + 2 - scroll;
		float contentH;
		if (slayerTab == 0) {
			var vg = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.slayer.VoidgloomVisuals.class);
			contentH = drawSlayerModulePage(g, vg, "Enderman slayer  ·  boss, mini bosses and Voidlings",
				new String[] {"Phase-coloured glows with enchant glint and aura rings.", "Laser walls, laser show and a timer on real server ticks.",
					"Beacon path, landing spot, fuse and BEACON SOON / DOWN.", "TERM NOW, HITS OVER! and a big remaining-hits counter."},
				(pg, px, py, pw, ph) -> {
					float[] size = vg.statusSize();
					if (size[0] <= 0) {
						Ui.textCentered(pg, "Status window off", px + pw / 2, py + ph / 2 - 4, Theme.TEXT_MUTED, UiFont.SMALL);
						return;
					}
					float k = Math.min(1.4f, Math.min((pw - 16) / size[0], (ph - 22) / size[1]));
					pg.pose().pushMatrix();
					pg.pose().translate(px + (pw - size[0] * k) / 2, py + 14 + (ph - 18 - size[1] * k) / 2);
					pg.pose().scale(k, k);
					vg.drawStatus(pg, System.currentTimeMillis());
					pg.pose().popMatrix();
				}, cx, top, cw, mx, my, dt, interactive, gy, gy + gh);
		}
		else contentH = drawSlayerPlaceholder(g, cx, top, cw, SLAYER_TABS[slayerTab]);
		Ui.popScissor(g);

		float maxScroll = Math.max(0, contentH - gh);
		scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
		if (maxScroll > 0) {
			float barH = Math.max(20, gh * gh / contentH);
			Ui.rect(g, x + w - 7, gy + (gh - barH) * (scroll / maxScroll), 2.5f, barH, 1.25f, Theme.BORDER);
		}
	}

	private interface PagePreview {
		void draw(GuiGraphicsExtractor g, float x, float y, float w, float h);
	}

	/** A slayer tab's page: hero card (switch, blurb, live preview) and the module's sections in cards. */
	private float drawSlayerModulePage(GuiGraphicsExtractor g, Module m, String subtitle, String[] lines, PagePreview preview,
									   float x, float top, float w, double mx, double my, float dt, boolean interactive, float clipTop, float clipBottom) {
		// hero card: switch, name, description, live status-window preview
		float heroH = 96;
		m.toggleAnim = Ui.approach(m.toggleAnim, m.isEnabled() ? 1f : 0f, dt, 16f);
		float t = m.toggleAnim;
		Ui.rect(g, x, top, w, heroH, 8, Theme.BG_CARD);
		if (t > 0.01f) Ui.gradientH(g, x, top, Math.min(w, 360), heroH, 8, Ui.alpha(Theme.ACCENT, 0.14f * t), Ui.alpha(Theme.ACCENT, 0f));
		Ui.outline(g, x, top, w, heroH, 8, 0.75f, Ui.mix(Theme.BORDER, 0xFF5A45A0, t));
		float descW = w * 0.52f;
		Ui.text(g, m.name(), x + 12, top + 12, Theme.TEXT, UiFont.TITLE);
		textFit(g, subtitle, x + 12, top + 29, descW, Theme.TEXT_SECONDARY, UiFont.SMALL, mx, my, interactive);
		for (int i = 0; i < lines.length; i++) textFit(g, "•  " + lines[i], x + 12, top + 41 + i * 9, descW, Theme.TEXT_MUTED, UiFont.SMALL, mx, my, interactive);
		// switch
		float sw = 24, sh = 12, sx = x + 12, sy = top + heroH - 18;
		Ui.rect(g, sx, sy, sw, sh, 7, Ui.mix(Theme.STATE_OFF, Theme.ACCENT, t));
		Ui.circle(g, sx + 6 + t * 12, sy + 6, 4.2f, 0xFFFFFFFF);
		Ui.text(g, m.isEnabled() ? "Enabled" : "Disabled", sx + sw + 7, sy + 2, m.isEnabled() ? Theme.TEXT : Theme.TEXT_MUTED, UiFont.SEMIBOLD);
		if (interactive) hits.add(Hit.click(sx, sy, sw + 60, sh, button -> m.toggle()));
		// live preview (sample data until there's something real to show)
		float pw = Math.min(240, w * 0.42f), ph = heroH - 12, px = x + w - pw - 6, py = top + 6;
		Ui.rect(g, px, py, pw, ph, 6, 0xFF07080C);
		Ui.gradientV(g, px, py, pw, ph, 6, 0xFF151027, 0xFF07080C);
		preview.draw(g, px, py, pw, ph);
		Ui.outline(g, px, py, pw, ph, 6, 0.75f, Theme.BORDER);
		float lw = Ui.width("LIVE", UiFont.SMALL) + 16;
		Ui.rect(g, px + 5, py + 5, lw, 10, 5, 0xB0000000);
		Ui.circle(g, px + 10, py + 10, 2, 0xFFFF4D6A);
		Ui.text(g, "LIVE", px + 14, py + 6, Theme.TEXT, UiFont.SMALL);

		// sections in a masonry of columns
		List<SectionSetting> heads = new ArrayList<>();
		List<List<Setting<?>>> bodies = new ArrayList<>();
		for (Setting<?> s : m.settings()) {
			if (s instanceof SectionSetting sec) {
				heads.add(sec);
				bodies.add(new ArrayList<>());
			} else if (s.isVisible() && !bodies.isEmpty()) {
				bodies.get(bodies.size() - 1).add(s);
			}
		}
		int cols = w >= 520 ? 2 : 1;
		float colW = (w - GAP * (cols - 1)) / cols;
		float[] colY = new float[cols];
		float startY = top + heroH + GAP;
		java.util.Arrays.fill(colY, startY);
		for (int i = 0; i < heads.size(); i++) {
			int c = 0;
			for (int k = 1; k < cols; k++) if (colY[k] < colY[c]) c = k;
			float sx0 = x + c * (colW + GAP), sy0 = colY[c];
			float cardH = 34 + bodies.get(i).size() * ROW_H + 6;
			if (sy0 + cardH > clipTop && sy0 < clipBottom) {
				Ui.rect(g, sx0, sy0, colW, cardH, 7, Theme.BG_CARD);
				Ui.outline(g, sx0, sy0, colW, cardH, 7, 0.75f, Theme.BORDER);
				Ui.rect(g, sx0 + 10, sy0 + 11, 3, 12, 1.5f, Theme.ACCENT_GLOW);
				Ui.text(g, heads.get(i).name(), sx0 + 18, sy0 + 9, Theme.TEXT, UiFont.SEMIBOLD);
				textFit(g, heads.get(i).subtitle(), sx0 + 18, sy0 + 19, colW - 28, Theme.TEXT_MUTED, UiFont.SMALL, mx, my, interactive);
				float ry = sy0 + 32;
				for (Setting<?> s : bodies.get(i)) {
					if (ry + ROW_H > clipTop && ry < clipBottom) drawSetting(g, s, sx0 + 10, ry, colW - 20, mx, my, dt, interactive);
					ry += ROW_H;
				}
			}
			colY[c] += cardH + GAP;
		}
		float bottom = startY;
		for (float cy : colY) bottom = Math.max(bottom, cy);
		return bottom - top + 4;
	}

	private float drawSlayerPlaceholder(GuiGraphicsExtractor g, float x, float top, float w, String[] tab) {
		float cw = Math.min(380, w), cx = x + (w - cw) / 2, cy = top + 16, ch = 140;
		Ui.rect(g, cx, cy, cw, ch, 10, Theme.BG_CARD);
		Ui.outline(g, cx, cy, cw, ch, 10, 0.75f, Theme.BORDER);
		Ui.rect(g, cx + cw / 2 - 24, cy + 16, 48, 14, 7, Ui.alpha(0xFFFFAA33, 0.25f));
		Ui.textCentered(g, "SOON", cx + cw / 2, cy + 19, 0xFFFFC266, UiFont.SEMIBOLD);
		Ui.textCentered(g, tab[3], cx + cw / 2, cy + 42, Theme.TEXT, UiFont.TITLE);
		Ui.textCentered(g, tab[1] + " slayer visuals are on the way", cx + cw / 2, cy + 62, Theme.TEXT_SECONDARY, UiFont.BODY);
		Ui.textCentered(g, Ui.ellipsize("Planned: " + tab[2], cw - 20, UiFont.SMALL), cx + cw / 2, cy + 84, Theme.TEXT_MUTED, UiFont.SMALL);
		Ui.textCentered(g, Ui.ellipsize("Same layout as Voidgloom: status window, glows, alerts.", cw - 20, UiFont.SMALL), cx + cw / 2, cy + 100, Theme.TEXT_MUTED, UiFont.SMALL);
		return ch + 60;
	}

	private static int visibleSettings(Module m) {
		int n = 0;
		for (Setting<?> s : m.settings()) if (s.isVisible()) n++;
		return n;
	}

	private void drawCard(GuiGraphicsExtractor g, Module m, float x, float y, float w, float h,
						  double mx, double my, float dt, boolean mouseInGrid, float clipTop, float clipBottom) {
		m.toggleAnim = Ui.approach(m.toggleAnim, m.isEnabled() ? 1f : 0f, dt, 16f);
		float t = m.toggleAnim;
		boolean overCard = mouseInGrid && Ui.hovered(mx, my, x, y, w, CARD_H);
		float hover = animateHover(m, overCard, dt);

		if (hover > 0.01f) Ui.shadow(g, x, y + 3, w, h, 7, 12, Ui.alpha(0xFF000000, 0.45f * hover));
		Ui.rect(g, x, y, w, h, 7, Ui.mix(Theme.BG_CARD, Theme.BG_CARD_HOVER, hover * 0.8f));
		if (t > 0.01f) Ui.gradientH(g, x, y, Math.min(w, 220), CARD_H, 7, Ui.alpha(Theme.ACCENT, 0.12f * t), Ui.alpha(Theme.ACCENT, 0f));
		Ui.outline(g, x, y, w, h, 7, 0.75f, Ui.mix(Ui.mix(Theme.BORDER, 0xFF3A3F55, hover), 0xFF5A45A0, t));

		// Icon tile: dark when off, violet gradient when on
		Ui.rect(g, x + 9, y + 9, 18, 18, 5, Theme.BG_INPUT);
		if (t > 0.01f) {
			Ui.shadow(g, x + 9, y + 9, 18, 18, 5, 7, Ui.alpha(Theme.ACCENT, 0.45f * t));
			Ui.shape(g, x + 9, y + 9, 18, 18, 5, 0, Ui.alpha(Theme.ACCENT_GLOW, t), Ui.alpha(Theme.ACCENT, t), Ui.alpha(0xFF5B3BD0, t), Ui.alpha(Theme.ACCENT, t));
		}
		Ui.icon(g, m.icon(), x + 13, y + 13, 10, Ui.mix(Theme.TEXT_SECONDARY, 0xFFFFFFFF, t));

		// Toggle
		float tw = 18;
		float th = 10;
		float tx = x + w - 9 - tw;
		float ty = y + 13;
		Ui.rect(g, tx, ty, tw, th, 5, Ui.mix(Theme.STATE_OFF, Theme.ACCENT, t));
		float knobX = tx + 5 + t * 8;
		if (t > 0.01f) Ui.shadow(g, knobX - 3.5f, ty + 1.5f, 7, 7, 3.5f, 5, Ui.alpha(Theme.ACCENT_GLOW, 0.6f * t));
		Ui.circle(g, knobX, ty + 5, 3.5f, 0xFFFFFFFF);

		// Gear (settings)
		boolean hasSettings = !m.settings().isEmpty();
		float gx = tx - 15;
		float gyy = y + 13.5f;
		boolean overGear = mouseInGrid && hasSettings && Ui.hovered(mx, my, gx - 2, gyy - 2, 13, 13);
		if (hasSettings) {
			int gearColor = m == expanded ? Theme.ACCENT_GLOW : overGear ? Theme.TEXT : Theme.TEXT_MUTED;
			Ui.icon(g, "gear", gx, gyy, 9, gearColor);
		}

		// Toggle-key chip, left of the gear
		boolean listening = capturing == m.toggleKey;
		String keyLabel = listening ? "…" : m.toggleKey.get() == KeyBindSetting.NONE ? "Key" : m.toggleKey.keyName();
		float chipW = Ui.width(keyLabel, UiFont.SMALL) + 10;
		float chipX = (hasSettings ? gx - 5 : tx - 5) - chipW;
		float chipY = y + 12.5f;
		boolean overChip = mouseInGrid && Ui.hovered(mx, my, chipX, chipY, chipW, 12);
		Ui.rect(g, chipX, chipY, chipW, 12, 4, listening ? Theme.ACCENT_SOFT : overChip ? Theme.BG_CARD_HOVER : Theme.BG_INPUT);
		Ui.outline(g, chipX, chipY, chipW, 12, 4, 0.75f, listening ? Theme.ACCENT : Theme.BORDER);
		Ui.textCentered(g, keyLabel, chipX + chipW / 2f, chipY + 2f, m.toggleKey.get() == KeyBindSetting.NONE && !listening ? Theme.TEXT_MUTED : Theme.TEXT, UiFont.SMALL);

		// Text
		float textRight = chipX - 4;
		Ui.text(g, Ui.ellipsize(m.name(), textRight - (x + 33), UiFont.SEMIBOLD), x + 33, y + 14, Theme.TEXT, UiFont.SEMIBOLD);
		textFit(g, m.description(), x + 9, y + 32, w - 18, Theme.TEXT_SECONDARY, UiFont.SMALL, mx, my, mouseInGrid && my >= clipTop && my <= clipBottom);

		if (mouseInGrid && y + CARD_H > clipTop && y < clipBottom) {
			// Later hits win, so the gear goes after the card.
			hits.add(Hit.click(x, y, w, CARD_H, button -> {
				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hasSettings) toggleExpanded(m);
				else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) m.toggle();
			}));
			if (hasSettings) hits.add(Hit.click(gx - 2, gyy - 2, 13, 13, button -> toggleExpanded(m)));
			hits.add(Hit.click(chipX, chipY, chipW, 12, button -> {
				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					m.toggleKey.set(KeyBindSetting.NONE);
					capturing = null;
				} else {
					capturing = m.toggleKey;
					typing = null;
				}
			}));
		}

		if (m == expanded) {
			Ui.rect(g, x + 9, y + CARD_H, w - 18, 0.75f, 0, Theme.BORDER);
			boolean hasPreview = m.preview() != Module.Preview.NONE && w > 300;
			float settingsW = w - 18 - (hasPreview ? PREVIEW_W + 10 : 0);
			float sy = y + CARD_H + 5;
			for (Setting<?> s : m.settings()) {
				if (!s.isVisible()) continue;
				boolean rowVisible = sy + ROW_H > clipTop && sy < clipBottom;
				if (rowVisible) drawSetting(g, s, x + 9, sy, settingsW, mx, my, dt, mouseInGrid);
				sy += ROW_H;
			}
			if (sy + ROW_H > clipTop && sy < clipBottom) drawPresetRow(g, m, x + 9, sy, settingsW, mx, my, mouseInGrid);
			if (hasPreview) drawPreview(g, m, x + w - 9 - PREVIEW_W, y + CARD_H + 7, clipTop, clipBottom);
		}
	}

	private static final float PREVIEW_W = 150;
	private static final float PREVIEW_H = 118;
	private final long openedAt = System.nanoTime();

	/** Live preview window to the right of the settings (3D scene and/or 2D drawing from the module). */
	private void drawPreview(GuiGraphicsExtractor g, Module m, float px, float py, float clipTop, float clipBottom) {
		// Keep the window in view while scrolling through long settings lists.
		float top = Math.max(py, clipTop + 4);
		if (top + PREVIEW_H > clipBottom - 4) top = Math.max(py, clipBottom - 4 - PREVIEW_H);
		if (top + 12 > clipBottom || top + PREVIEW_H < clipTop) return;
		float time = (System.nanoTime() - openedAt) / 1e9f;
		Ui.rect(g, px, top, PREVIEW_W, PREVIEW_H, 6, 0xFF07080C);
		boolean firstPerson = m.preview() == Module.Preview.FIRST_PERSON && minecraft != null && minecraft.player != null;
		if (firstPerson) {
			Ui.gradientV(g, px, top, PREVIEW_W, PREVIEW_H * 0.62f, 6, 0xFF6F9CE8, 0xFFB9D2F5);
			Ui.gradientV(g, px, top + PREVIEW_H * 0.62f - 0.5f, PREVIEW_W, PREVIEW_H * 0.38f + 0.5f, 6, 0xFF6E9A4C, 0xFF3F6B2C);
			Ui.rect(g, px + PREVIEW_W / 2 - 3, top + PREVIEW_H / 2 - 0.5f, 6, 1, 0, 0xC0FFFFFF);
			Ui.rect(g, px + PREVIEW_W / 2 - 0.5f, top + PREVIEW_H / 2 - 3, 1, 6, 0, 0xC0FFFFFF);
		}
		if (m.preview() == Module.Preview.SCENE || firstPerson) {
			((com.blixu.blixuaddons.client.mixin.GuiGraphicsAccessor) g).blixuaddons$guiRenderState().addPicturesInPictureState(
				com.blixu.blixuaddons.client.gui.preview.PreviewState.of(m, time, time * 15f, 18f,
					(int) px + 1, (int) top + 1, (int) (px + PREVIEW_W) - 1, (int) (top + PREVIEW_H) - 1, m.previewScale(), g.pose(), Ui.currentScissor()));
		}
		m.drawPreview(g, px, top, PREVIEW_W, PREVIEW_H, time);
		Ui.outline(g, px, top, PREVIEW_W, PREVIEW_H, 6, 0.75f, Theme.BORDER);
		float lw = Ui.width("LIVE", UiFont.SMALL) + 16;
		Ui.rect(g, px + 5, top + 5, lw, 10, 5, 0xB0000000);
		Ui.circle(g, px + 10, top + 10, 2, 0xFFFF4D6A);
		Ui.text(g, "LIVE", px + 14, top + 6, Theme.TEXT, UiFont.SMALL);
	}

	private void drawSetting(GuiGraphicsExtractor g, Setting<?> s, float x, float y, float w,
							 double mx, double my, float dt, boolean interactive) {
		if (s instanceof SectionSetting sec) {
			Ui.text(g, sec.name().toUpperCase(Locale.ROOT), x + 2, y + 7, Theme.ACCENT_GLOW, UiFont.SMALL);
			return;
		}
		float right = x + w;
		boolean overRow = interactive && Ui.hovered(mx, my, x, y, w, ROW_H);
		if (overRow) Ui.rect(g, x - 3, y, w + 6, ROW_H, 4, 0x08FFFFFF);
		float nameMax = s instanceof BoolSetting ? w - 30 : s instanceof ColorSetting ? w - 150 : w - 158;
		textFit(g, s.name(), x + 2, y + 5, Math.max(40, nameMax), Theme.TEXT_SECONDARY, UiFont.BODY, mx, my, interactive);
		int firstHit = hits.size();
		// Right-click anywhere on the row resets (added first so the controls below win for left-click).
		hits.add(Hit.click(x, y, w, ROW_H, button -> {
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) s.reset();
		}));

		if (s instanceof BoolSetting b) {
			float t = animateHover(b, b.get(), dt);
			float tx = right - 16;
			float ty = y + 4;
			Ui.rect(g, tx, ty, 16, 9, 4.5f, Ui.mix(Theme.STATE_OFF, Theme.ACCENT, t));
			Ui.circle(g, tx + 4.5f + t * 7, ty + 4.5f, 3, 0xFFFFFFFF);
			hits.add(Hit.click(x, y, w, ROW_H, button -> {
				if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) b.toggle(); else s.reset();
			}));
		} else if (s instanceof ColorSetting c) {
			drawColorRow(g, c, right, y, mx, my);
		} else if (s instanceof EnumSetting<?> e) {
			// Pill:  ‹  Option  ›   left half = previous, right half = next.
			String text = e.label();
			float pillW = Math.max(110, Ui.width(text, UiFont.SEMIBOLD) + 44);
			float px0 = right - pillW;
			float py0 = y + 2;
			boolean overLeft = Ui.hovered(mx, my, px0, py0, pillW / 2f, 14);
			boolean overRight = Ui.hovered(mx, my, px0 + pillW / 2f, py0, pillW / 2f, 14);
			Ui.rect(g, px0, py0, pillW, 14, 7, Theme.BG_INPUT);
			Ui.outline(g, px0, py0, pillW, 14, 7, 0.75f, Theme.BORDER);
			Ui.text(g, "‹", px0 + 7, py0 + 3, overLeft ? Theme.ACCENT_GLOW : Theme.TEXT_MUTED, UiFont.SEMIBOLD);
			Ui.text(g, "›", px0 + pillW - 11, py0 + 3, overRight ? Theme.ACCENT_GLOW : Theme.TEXT_MUTED, UiFont.SEMIBOLD);
			Ui.textCentered(g, Ui.ellipsize(text, pillW - 30, UiFont.SEMIBOLD), px0 + pillW / 2f, py0 + 3, Theme.TEXT, UiFont.SEMIBOLD);
			String count = (e.index() + 1) + "/" + e.count();
			Ui.text(g, count, px0 - 6 - Ui.width(count, UiFont.SMALL), y + 5.5f, Theme.TEXT_MUTED, UiFont.SMALL);
			hits.add(Hit.click(px0, py0, pillW / 2f, 14, button -> {
				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) s.reset(); else e.cycle(-1);
			}));
			hits.add(Hit.click(px0 + pillW / 2f, py0, pillW / 2f, 14, button -> {
				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) s.reset(); else e.cycle(1);
			}));
		} else if (s instanceof NumberSetting n) {
			float trackW = 110;
			float tx = right - trackW - 4;
			float ty = y + 8;
			float f = (float) n.fraction();
			String value = n.display();
			Ui.text(g, value, tx - 8 - Ui.width(value, UiFont.SMALL), y + 5.5f, Theme.TEXT, UiFont.SMALL);
			Ui.rect(g, tx, ty, trackW, 3, 1.5f, Theme.STATE_OFF);
			Ui.gradientH(g, tx, ty, trackW * f, 3, 1.5f, Theme.ACCENT, Ui.mix(Theme.ACCENT_GLOW, Theme.ACCENT_2, f * 0.6f));
			if (overRow) Ui.shadow(g, tx + trackW * f - 4, ty - 2.5f, 8, 8, 4, 6, Ui.alpha(Theme.ACCENT_GLOW, 0.6f));
			Ui.circle(g, tx + trackW * f, ty + 1.5f, 4, 0xFFFFFFFF);
			Ui.circle(g, tx + trackW * f, ty + 1.5f, 1.6f, Theme.ACCENT);
			DoubleConsumer setFromMouse = mouseX -> n.set(n.min() + Mth.clamp((mouseX - tx) / trackW, 0, 1) * (n.max() - n.min()));
			hits.add(Hit.drag(tx - 4, y, trackW + 8, ROW_H, setFromMouse));
		} else if (s instanceof StringSetting str) {
			float fw = Math.min(150, w * 0.52f);
			float fx = right - fw;
			boolean focused = typing != null && typing.target == str;
			Ui.rect(g, fx, y + 2, fw, 14, 4, Theme.BG_INPUT);
			Ui.outline(g, fx, y + 2, fw, 14, 4, focused ? 1f : 0.75f, focused ? Theme.ACCENT : Theme.BORDER);
			drawFieldText(g, focused ? typing.text : str.get(), fx + 5, y + 5, fw - 10, focused);
			hits.add(Hit.click(fx, y + 2, fw, 14, button -> {
				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					str.reset();
					return;
				}
				focusText(new TextTarget(str, str.get(), str.maxLength(), str::set));
			}));
		} else if (s instanceof KeyBindSetting k) {
			boolean listening = capturing == k;
			String label = listening ? "Press a key…" : k.keyName();
			float bw = Math.max(70, Ui.width(label, UiFont.SEMIBOLD) + 16);
			float bx = right - bw;
			Ui.rect(g, bx, y + 2, bw, 14, 4, listening ? Theme.ACCENT_SOFT : Theme.BG_INPUT);
			Ui.outline(g, bx, y + 2, bw, 14, 4, 0.75f, listening ? Theme.ACCENT : Theme.BORDER);
			Ui.textCentered(g, label, bx + bw / 2f, y + 5, listening ? Theme.ACCENT_GLOW : Theme.TEXT, UiFont.SEMIBOLD);
			hits.add(Hit.click(bx, y + 2, bw, 14, button -> {
				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					k.set(KeyBindSetting.NONE);
					capturing = null;
				} else if (capturing == k && button >= 2) {
					k.set(-100 - button);
					capturing = null;
				} else {
					capturing = k;
					typing = null;
				}
			}));
		} else if (s instanceof ActionSetting a) {
			String label = a.buttonLabel();
			float bw = Math.max(64, Ui.width(label, UiFont.SEMIBOLD) + 18);
			float bx = right - bw;
			boolean over = Ui.hovered(mx, my, bx, y + 2, bw, 14);
			Ui.rect(g, bx, y + 2, bw, 14, 4, over ? Theme.ACCENT : Theme.ACCENT_SOFT);
			Ui.textCentered(g, label, bx + bw / 2f, y + 5, Theme.TEXT, UiFont.SEMIBOLD);
			hits.add(Hit.click(bx, y + 2, bw, 14, button -> a.run()));
		}
			if (!interactive) while (hits.size() > firstHit) hits.remove(hits.size() - 1);
	}

	private void drawColorRow(GuiGraphicsExtractor g, ColorSetting c, float right, float y, double mx, double my) {
		// [picker button] then the quick swatches
		float pbW = 26;
		float pbX = right - pbW;
		int now = c.argb();
		Ui.rect(g, pbX, y + 3, pbW, 12, 4, 0xFF000000 | (now & 0xFFFFFF));
		Ui.outline(g, pbX, y + 3, pbW, 12, 4, picking == c ? 1.2f : 0.75f, picking == c ? Theme.ACCENT_GLOW : Theme.BORDER);
		String badge = switch (c.mode()) {
			case STATIC -> "";
			case RAINBOW -> "RGB";
			case PALETTE -> "P" + (c.paletteIndex() + 1);
			case THEME -> "THM";
		};
		if (!badge.isEmpty()) Ui.textCentered(g, badge, pbX + pbW / 2f, y + 5.5f, Colors.toHsv(now)[2] > 0.6f ? 0xFF101218 : 0xFFFFFFFF, UiFont.SMALL);
		hits.add(Hit.click(pbX, y + 3, pbW, 12, button -> {
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				c.reset();
				return;
			}
			picking = picking == c ? null : c;
			hexDraft = Colors.hex(c.get());
			typing = null;
		}));

		int[] swatches = c.swatches();
		float r = 4.5f;
		float step = 13;
		float sx = pbX - 8 - r - (swatches.length - 1) * step;
		for (int rgb : swatches) {
			float cx = sx;
			float cy = y + 9;
			boolean selected = c.mode() == ColorSetting.Mode.STATIC && (c.get() & 0xFFFFFF) == rgb;
			boolean over = Ui.hovered(mx, my, cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2);
			if (selected) {
				Ui.shadow(g, cx - r, cy - r, r * 2, r * 2, r, 5, Ui.alpha(0xFF000000 | rgb, 0.7f));
				Ui.circle(g, cx, cy, r + 1.5f, 0xFFFFFFFF);
			} else if (over) {
				Ui.circle(g, cx, cy, r + 1.2f, 0x60FFFFFF);
			}
			Ui.circle(g, cx, cy, r, 0xFF000000 | rgb);
			hits.add(Hit.click(cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2, button -> {
				c.setMode(ColorSetting.Mode.STATIC);
				c.set((c.get() & 0xFF000000) | rgb);
			}));
			sx += step;
		}
	}

	/** Per-module preset row: name field + Save / Load / Reset to defaults. */
	private void drawPresetRow(GuiGraphicsExtractor g, Module m, float x, float y, float w, double mx, double my, boolean interactive) {
		Ui.text(g, "Preset", x + 2, y + 5, Theme.TEXT_MUTED, UiFont.BODY);
		String name = presetNames.getOrDefault(m, "preset1");
		float right = x + w;
		float[] widths = {40, 40, 58};
		String[] labels = {"Save", "Load", "Defaults"};
		float bx = right - (widths[0] + widths[1] + widths[2] + 8);
		float fw = Math.min(110, bx - (x + 50) - 6);
		float fx = bx - fw - 6;
		boolean focused = typing != null && typing.target == m;
		Ui.rect(g, fx, y + 2, fw, 14, 4, Theme.BG_INPUT);
		Ui.outline(g, fx, y + 2, fw, 14, 4, focused ? 1f : 0.75f, focused ? Theme.ACCENT : Theme.BORDER);
		drawFieldText(g, focused ? typing.text : name, fx + 5, y + 5, fw - 10, focused);
		String status = presetStatus.get(m);
		if (status != null) Ui.text(g, status, x + 40, y + 5.5f, Theme.ACCENT_GLOW, UiFont.SMALL);
		if (interactive) {
			hits.add(Hit.click(fx, y + 2, fw, 14, button ->
				focusText(new TextTarget(m, name, 24, v -> presetNames.put(m, ConfigManager.cleanName(v))))));
		}
		for (int i = 0; i < 3; i++) {
			boolean over = Ui.hovered(mx, my, bx, y + 2, widths[i], 14);
			Ui.rect(g, bx, y + 2, widths[i], 14, 4, over ? Theme.BG_CARD_HOVER : Theme.BG_INPUT);
			Ui.outline(g, bx, y + 2, widths[i], 14, 4, 0.75f, Theme.BORDER);
			Ui.textCentered(g, labels[i], bx + widths[i] / 2f, y + 5, Theme.TEXT, UiFont.SEMIBOLD);
			final int idx = i;
			if (interactive) hits.add(Hit.click(bx, y + 2, widths[i], 14, button -> {
				switch (idx) {
					case 0 -> {
						ConfigManager.saveModulePreset(m, name);
						presetStatus.put(m, "saved");
					}
					case 1 -> presetStatus.put(m, ConfigManager.loadModulePreset(m, name) ? "loaded" : "not found");
					default -> {
						m.resetSettings();
						presetStatus.put(m, "reset");
					}
				}
			}));
			bx += widths[i] + 4;
		}
	}

	// ------------------------------------------------------------------ colour picker

	private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

	private void drawPicker(GuiGraphicsExtractor g, ColorSetting c, float x, float y, double mx, double my, float dt) {
		float w = 220, h = 184;
		Ui.shadow(g, x, y + 3, w, h, 8, 18, 0xC0000000);
		Ui.rect(g, x, y, w, h, 8, Theme.BG_PANEL);
		Ui.outline(g, x, y, w, h, 8, 0.75f, Theme.BORDER);
		// Swallow clicks on the panel background so they don't fall through.
		hits.add(Hit.click(x, y, w, h, button -> { }));
		Ui.text(g, Ui.ellipsize(c.name(), 150, UiFont.SEMIBOLD), x + 9, y + 8, Theme.TEXT, UiFont.SEMIBOLD);
		float closeX = x + w - 18;
		Ui.icon(g, "close", closeX + 3, y + 7, 7, Theme.TEXT_SECONDARY);
		hits.add(Hit.click(closeX, y + 4, 14, 14, button -> picking = null));

		int value = c.get();
		float[] hsv = Colors.toHsv(value);
		float alpha = Colors.a(value) / 255f;

		// Saturation / value square
		float sqX = x + 9, sqY = y + 22, sq = 96;
		int hueColor = Colors.fromHsv(hsv[0], 1, 1, 255);
		Ui.gradientH(g, sqX, sqY, sq, sq, 3, 0xFFFFFFFF, hueColor);
		Ui.gradientV(g, sqX, sqY, sq, sq, 3, 0x00000000, 0xFF000000);
		Ui.circle(g, sqX + hsv[1] * sq, sqY + (1 - hsv[2]) * sq, 3.5f, 0xFFFFFFFF);
		Ui.circle(g, sqX + hsv[1] * sq, sqY + (1 - hsv[2]) * sq, 2.2f, 0xFF000000 | (value & 0xFFFFFF));
		hits.add(Hit.drag2(sqX, sqY, sq, sq, (px, py) -> {
			float s = Mth.clamp((float) ((px - sqX) / sq), 0, 1);
			float v = 1 - Mth.clamp((float) ((py - sqY) / sq), 0, 1);
			float[] cur = Colors.toHsv(c.get());
			float hue = cur[1] < 0.001f || cur[2] < 0.001f ? lastHue : cur[0];
			setPicked(c, Colors.fromHsv(hue, s, v, Colors.a(c.get())));
		}));

		// Hue bar
		float hbX = sqX + sq + 8, hbW = 10;
		for (int i = 0; i < 6; i++) {
			Ui.gradientV(g, hbX, sqY + sq * i / 6f, hbW, sq / 6f + 0.5f, 0, HUES[i], HUES[i + 1]);
		}
		if (hsv[1] > 0.001f && hsv[2] > 0.001f) lastHue = hsv[0];
		Ui.rect(g, hbX - 1.5f, sqY + lastHue * sq - 1, hbW + 3, 2, 1, 0xFFFFFFFF);
		hits.add(Hit.drag2(hbX - 2, sqY, hbW + 4, sq, (px, py) -> {
			lastHue = Mth.clamp((float) ((py - sqY) / sq), 0, 0.999f);
			float[] cur = Colors.toHsv(c.get());
			setPicked(c, Colors.fromHsv(lastHue, Math.max(cur[1], 0.01f), Math.max(cur[2], 0.01f), Colors.a(c.get())));
		}));

		// Alpha bar
		float abX = hbX + hbW + 8;
		for (int i = 0; i < 8; i++) Ui.rect(g, abX + (i % 2) * 5, sqY + i * sq / 8f, 5, sq / 8f, 0, i % 2 == 0 ? 0xFF888888 : 0xFFCCCCCC);
		for (int i = 0; i < 8; i++) Ui.rect(g, abX + ((i + 1) % 2) * 5, sqY + i * sq / 8f, 5, sq / 8f, 0, i % 2 == 0 ? 0xFFCCCCCC : 0xFF888888);
		Ui.gradientV(g, abX, sqY, hbW, sq, 0, 0xFF000000 | (value & 0xFFFFFF), value & 0xFFFFFF);
		Ui.rect(g, abX - 1.5f, sqY + (1 - alpha) * sq - 1, hbW + 3, 2, 1, 0xFFFFFFFF);
		hits.add(Hit.drag2(abX - 2, sqY, hbW + 4, sq, (px, py) -> {
			float a = 1 - Mth.clamp((float) ((py - sqY) / sq), 0, 1);
			setPicked(c, Colors.setAlpha(c.get(), a));
		}));

		// Right column: preview, hex, RGBA readout
		float rx = abX + hbW + 10;
		float rw = x + w - 9 - rx;
		Ui.rect(g, rx, sqY, rw, 22, 4, c.argb() | 0xFF000000);
		Ui.rect(g, rx, sqY + 22, rw, 6, 0, c.argb());
		Ui.text(g, "HEX", rx, sqY + 33, Theme.TEXT_MUTED, UiFont.SMALL);
		boolean hexFocused = typing != null && typing.target == HEX_KEY;
		Ui.rect(g, rx, sqY + 42, rw, 14, 4, Theme.BG_INPUT);
		Ui.outline(g, rx, sqY + 42, rw, 14, 4, hexFocused ? 1f : 0.75f, hexFocused ? Theme.ACCENT : Theme.BORDER);
		drawFieldText(g, hexFocused ? typing.text : Colors.hex(value), rx + 4, sqY + 45, rw - 8, hexFocused);
		hits.add(Hit.click(rx, sqY + 42, rw, 14, button -> focusText(new TextTarget(HEX_KEY, Colors.hex(c.get()), 9, txt -> {
			try {
				String hx = txt.replace("#", "").trim();
				long v = Long.parseLong(hx, 16);
				setPicked(c, hx.length() <= 6 ? 0xFF000000 | (int) v : (int) v);
			} catch (NumberFormatException ignored) {
			}
		}))));
		Ui.text(g, "R " + Colors.r(value) + "  G " + Colors.g(value), rx, sqY + 61, Theme.TEXT_SECONDARY, UiFont.SMALL);
		Ui.text(g, "B " + Colors.b(value) + "  A " + Colors.a(value), rx, sqY + 70, Theme.TEXT_SECONDARY, UiFont.SMALL);

		// Mode buttons
		float my0 = sqY + sq + 7;
		ColorSetting.Mode[] modes = c.allowLinks() ? ColorSetting.Mode.values() : new ColorSetting.Mode[] {ColorSetting.Mode.STATIC, ColorSetting.Mode.RAINBOW};
		String[] names = {"Solid", "Rainbow", "Palette", "Theme"};
		float bw = (w - 18 - (modes.length - 1) * 4) / modes.length;
		float bx = x + 9;
		for (ColorSetting.Mode mode : modes) {
			boolean on = c.mode() == mode;
			Ui.rect(g, bx, my0, bw, 13, 4, on ? Theme.ACCENT : Theme.BG_INPUT);
			Ui.outline(g, bx, my0, bw, 13, 4, 0.75f, on ? Theme.ACCENT_GLOW : Theme.BORDER);
			Ui.textCentered(g, names[mode.ordinal()], bx + bw / 2f, my0 + 3, Theme.TEXT, UiFont.SEMIBOLD);
			hits.add(Hit.click(bx, my0, bw, 13, button -> c.setMode(mode)));
			bx += bw + 4;
		}

		float oy = my0 + 18;
		if (c.mode() == ColorSetting.Mode.RAINBOW) {
			float f = (c.rainbowSpeed() - 0.01f) / 2.99f;
			Ui.text(g, "Speed", x + 9, oy + 3, Theme.TEXT_SECONDARY, UiFont.SMALL);
			float tx = x + 45, tw = w - 54;
			Ui.rect(g, tx, oy + 5, tw, 3, 1.5f, Theme.STATE_OFF);
			Ui.gradientH(g, tx, oy + 5, tw * f, 3, 1.5f, Theme.ACCENT, Theme.ACCENT_GLOW);
			Ui.circle(g, tx + tw * f, oy + 6.5f, 4, 0xFFFFFFFF);
			hits.add(Hit.drag(tx - 4, oy, tw + 8, 13, mxx -> c.setRainbowSpeed(0.01f + Mth.clamp((float) ((mxx - tx) / tw), 0, 1) * 2.99f)));
		} else if (c.mode() == ColorSetting.Mode.PALETTE) {
			// 6 palettes × 3 slots; click one to link
			float cellW = (w - 18) / Palettes.COUNT;
			for (int p = 0; p < Palettes.COUNT; p++) {
				int[] stops = Palettes.stops(p);
				for (int s = 0; s < 3; s++) {
					float sx0 = x + 9 + p * cellW + s * (cellW - 4) / 3f;
					boolean sel = c.paletteIndex() == p && c.paletteSlot() == s;
					Ui.rect(g, sx0, oy + 1, (cellW - 4) / 3f - 1, 10, 2, stops[s]);
					if (sel) Ui.outline(g, sx0 - 1, oy, (cellW - 4) / 3f + 1, 12, 2, 1f, 0xFFFFFFFF);
					final int fp = p, fs = s;
					hits.add(Hit.click(sx0, oy, (cellW - 4) / 3f, 12, button -> c.linkPalette(fp, fs)));
				}
			}
			Ui.text(g, Ui.ellipsize(Palettes.name(c.paletteIndex()) + " · " + Palettes.SLOT_NAMES[c.paletteSlot()], w - 18, UiFont.SMALL),
				x + 9, oy + 14, Theme.TEXT_MUTED, UiFont.SMALL);
		} else if (c.mode() == ColorSetting.Mode.THEME) {
			Ui.text(g, "Follows the theme accent (Color Palettes module).", x + 9, oy + 3, Theme.TEXT_MUTED, UiFont.SMALL);
		} else {
			Ui.text(g, "Drag in the square, or type a hex code.", x + 9, oy + 3, Theme.TEXT_MUTED, UiFont.SMALL);
		}
	}

	private float lastHue;
	private static final Object HEX_KEY = new Object();

	private void setPicked(ColorSetting c, int argb) {
		if (c.mode() == ColorSetting.Mode.PALETTE || c.mode() == ColorSetting.Mode.THEME) {
			// Linked colours keep their own alpha only.
			c.set((argb & 0xFF000000) | (c.get() & 0xFFFFFF));
			return;
		}
		c.set(argb);
	}

	private void drawEmpty(GuiGraphicsExtractor g, float x, float y, float w, float h, boolean searching) {
		float cy = y + h / 2f - 22;
		Ui.icon(g, "box", x + w / 2f - 9, cy, 18, Theme.TEXT_MUTED);
		Ui.textCentered(g, searching ? "No matches" : "Nothing here yet", x + w / 2f, cy + 26, Theme.TEXT_SECONDARY, UiFont.SEMIBOLD);
		Ui.textCentered(g, searching ? "Try a different word." : "Features you add to this category show up here.",
			x + w / 2f, cy + 38, Theme.TEXT_MUTED, UiFont.SMALL);
	}

	private void drawFieldText(GuiGraphicsExtractor g, String text, float x, float y, float maxW, boolean caret) {
		String shown = text;
		while (Ui.width(shown, UiFont.BODY) > maxW - 2 && !shown.isEmpty()) shown = shown.substring(1);
		Ui.text(g, shown, x, y, Theme.TEXT, UiFont.BODY);
		if (caret && (System.currentTimeMillis() / 530) % 2 == 0) {
			Ui.rect(g, x + 0.5f + Ui.width(shown, UiFont.BODY), y - 1, 0.75f, 9, 0, Theme.ACCENT_GLOW);
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = toLocalX(event.x());
		double my = toLocalY(event.y());
		if (capturing != null && event.button() >= 2) {
			capturing.set(-100 - event.button());
			capturing = null;
			return true;
		}
		capturing = null;
		searchFocused = false;
		TextTarget before = typing;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(mx, my)) {
				if (typing == before) commitTyping();
				if (hit.drag != null) {
					dragging = hit;
					hit.drag.accept(mx, my);
				} else if (hit.click != null) {
					hit.click.accept(event.button());
				}
				return true;
			}
		}
		commitTyping();
		if (picking != null) {
			picking = null;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null && dragging.drag != null) {
			dragging.drag.accept(toLocalX(event.x()), toLocalY(event.y()));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (picking == null) scrollTarget -= (float) scrollY * 24f;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturingMenuKey) {
			capturingMenuKey = false;
			if (event.key() != GLFW.GLFW_KEY_ESCAPE) {
				var mapping = com.blixu.blixuaddons.client.BlixuAddonsClient.openMenuKey();
				mapping.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(event.key()));
				net.minecraft.client.KeyMapping.resetMapping();
				minecraft.options.save();
			}
			return true;
		}
		if (capturing != null) {
			int key = event.key();
			capturing.set(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE ? KeyBindSetting.NONE : key);
			capturing = null;
			return true;
		}
		if (typing != null) {
			if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
				if (!typing.text.isEmpty()) typing.text = typing.text.substring(0, typing.text.length() - 1);
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER || event.key() == GLFW.GLFW_KEY_TAB) {
				commitTyping();
				return true;
			}
			if (event.isEscape()) {
				typing = null;
				return true;
			}
		}
		if (event.isEscape()) {
			if (picking != null) {
				picking = null;
			} else if (!search.isEmpty() || searchFocused) {
				search = "";
				searchFocused = false;
			} else if (expanded != null) {
				expanded = null;
			} else {
				onClose();
			}
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
			search = search.substring(0, search.length() - 1);
			resetScroll();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!event.isAllowedChatCharacter()) return super.charTyped(event);
		if (typing != null) {
			if (typing.text.length() < typing.maxLength) typing.text += event.codepointAsString();
			return true;
		}
		if (search.length() < 32) {
			// Typing anywhere searches.
			search += event.codepointAsString();
			searchFocused = true;
			resetScroll();
			return true;
		}
		return super.charTyped(event);
	}

	// ================================================================== helpers

	private void focusText(TextTarget target) {
		commitTyping();
		typing = target;
		searchFocused = false;
		capturing = null;
	}

	private void commitTyping() {
		if (typing != null) {
			TextTarget t = typing;
			typing = null;
			t.commit.accept(t.text);
		}
	}

	private void selectCategory(Category c) {
		if ((c == Category.COSMETICS || c == Category.WORLD) && minecraft != null && minecraft.player != null) {
			minecraft.gui.setScreen(new ClickGuiScreen(c == Category.COSMETICS, c == Category.WORLD));
			return;
		}
		category = c;
		search = "";
		expanded = null;
		picking = null;
		resetScroll();
	}

	private void toggleExpanded(Module m) {
		expanded = expanded == m ? null : m;
		picking = null;
	}

	private void resetScroll() {
		scroll = 0;
		scrollTarget = 0;
	}

	/** Matches module name/description and any setting name, so "glow radius" finds the right module. */
	private static boolean matches(Module m, String query) {
		String q = query.toLowerCase(Locale.ROOT).trim();
		if (m.name().toLowerCase(Locale.ROOT).contains(q) || m.description().toLowerCase(Locale.ROOT).contains(q)) return true;
		if (m.category().title.toLowerCase(Locale.ROOT).contains(q)) return true;
		for (Setting<?> s : m.settings()) {
			if (s.name().toLowerCase(Locale.ROOT).contains(q)) return true;
		}
		return false;
	}

	private float animateHover(Object key, boolean on, float dt) {
		float v = Ui.approach(hoverAnim.getOrDefault(key, on ? 1f : 0f), on ? 1f : 0f, dt, 18f);
		hoverAnim.put(key, v);
		return v;
	}

	private double toLocalX(double x) {
		return pivotX + (x - pivotX) / scale;
	}

	private double toLocalY(double y) {
		return pivotY + (y - pivotY) / scale;
	}

	/** A text field being edited; {@code commit} runs on Enter or when focus leaves. */
	private static final class TextTarget {
		final Object target;
		String text;
		final int maxLength;
		final Consumer<String> commit;

		TextTarget(Object target, String text, int maxLength, Consumer<String> commit) {
			this.target = target;
			this.text = text;
			this.maxLength = maxLength;
			this.commit = commit;
		}
	}

	private interface Drag2 {
		void accept(double x, double y);
	}

	/** A clickable (or draggable) area recorded while drawing. */
	private record Hit(float x, float y, float w, float h, @Nullable IntConsumer click, @Nullable Drag2 drag) {
		static Hit click(float x, float y, float w, float h, IntConsumer onClick) {
			return new Hit(x, y, w, h, onClick, null);
		}

		static Hit drag(float x, float y, float w, float h, DoubleConsumer onDrag) {
			return new Hit(x, y, w, h, null, (px, py) -> onDrag.accept(px));
		}

		static Hit drag2(float x, float y, float w, float h, Drag2 onDrag) {
			return new Hit(x, y, w, h, null, onDrag);
		}

		boolean contains(double mx, double my) {
			return Ui.hovered(mx, my, x, y, w, h);
		}
	}
}
