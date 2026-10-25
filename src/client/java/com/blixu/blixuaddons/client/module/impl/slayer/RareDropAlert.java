package com.blixu.blixuaddons.client.module.impl.slayer;

import com.blixu.blixuaddons.client.feature.slayer.PriceService;
import com.blixu.blixuaddons.client.feature.slayer.Sidebar;
import com.blixu.blixuaddons.client.feature.slayer.SlayerDrops;
import com.blixu.blixuaddons.client.feature.slayer.SlayerDrops.Drop;
import com.blixu.blixuaddons.client.feature.slayer.SlayerDrops.Rarity;
import com.blixu.blixuaddons.client.feature.slayer.VoidgloomTracker;
import com.blixu.blixuaddons.client.gui.Theme;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.ActionSetting;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.module.setting.SectionSetting;
import com.blixu.blixuaddons.client.render.McText;
import com.blixu.blixuaddons.client.render.PixelFont;
import com.blixu.blixuaddons.client.render.Ui;
import com.blixu.blixuaddons.client.render.UiFont;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Rare slayer drop animation: when chat announces a rare drop, the item flies in and spins slowly with
 * its name, price and drop chance at your current Magic Find (read from the same chat line). Two looks:
 * the whole screen, or a smaller notification above the hotbar (movable in the HUD editor).
 */
public class RareDropAlert extends Module {
	public enum Style { FULL("Whole screen"), HOTBAR("Above the hotbar"), BOTH("Both");
		public final String label;
		Style(String l) { label = l; }
	}

	public enum MinRarity { ALL("Every rare drop"), EXTRAORDINARY("Extraordinary and up"), RNGESUS("Pray RNGesus and up");
		public final String label;
		MinRarity(String l) { label = l; }
	}

	public final SectionSetting secLook = add(new SectionSetting("sec_look", "Rare drop animation", "When a slayer drops something rare"));
	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Look", Style.FULL, s -> s.label));
	public final EnumSetting<MinRarity> minRarity = add(new EnumSetting<>("min_rarity", "Show for", MinRarity.ALL, r -> r.label));
	public final NumberSetting duration = add(new NumberSetting("duration", "Duration (seconds)", 5, 2, 12, 0.5));
	public final BoolSetting showPrice = add(new BoolSetting("show_price", "Show price (SkyCofl: auction house / bazaar)", true));
	public final BoolSetting showChance = add(new BoolSetting("show_chance", "Show drop chance at your Magic Find", true));
	public final BoolSetting sound = add(new BoolSetting("sound", "Play a sound", true));
	public final ActionSetting test = add(new ActionSetting("test", "Preview the animation (or /blixutestraredrop)", "Test", () -> test(null)));
	public final ActionSetting editHud = add(new ActionSetting("edit_hud", "Move the hotbar notification", "Edit HUD layout",
		() -> Minecraft.getInstance().gui.setScreen(new com.blixu.blixuaddons.client.gui.HudEditScreen())));
	public final NumberSetting toastX = add(new NumberSetting("toast_x", "Notification X", 50, 0, 100, 0.5));
	public final NumberSetting toastY = add(new NumberSetting("toast_y", "Notification Y", 78, 0, 100, 0.5));
	public final NumberSetting toastScale = add(new NumberSetting("toast_scale", "Notification size", 1, 0.5, 2.5, 0.05));

	/** {@code look}: forced variant for this one (test command), or null for the Look setting. */
	private record Shown(String label, String name, @Nullable Drop drop, double mf, int color, long startMs, int tier, @Nullable Style look) {}

	private @Nullable Shown shown;
	private ItemStack stack = ItemStack.EMPTY;
	private @Nullable String itemId;
	/** True once the real item from the inventory replaced the placeholder (better than the NEU repo look). */
	private boolean fromInventory;

	public RareDropAlert() {
		super("rare_drop_alert", "Rare Drop Animation",
			"Rare slayer drops fly onto your screen with their name, price and chance at your Magic Find.",
			Category.HUD, "item", true);
		for (NumberSetting s : new NumberSetting[] {toastX, toastY, toastScale}) s.visibleWhen(() -> false);
	}

	// ================================================================== detection

	/** Every chat/system line (main thread). */
	public void onChat(String line) {
		if (!isEnabled()) return;
		SlayerDrops.Parsed p = SlayerDrops.parse(line);
		if (p == null) return;
		Drop d = SlayerDrops.find(p.name());
		// unknown items only count during a slayer quest (other mobs have "RARE DROP!" lines too)
		if (d == null && !(Sidebar.contains("Slay the boss!") || Sidebar.contains("Boss slain!") || Sidebar.contains("Slayer Quest"))) return;
		Rarity r = d != null ? d.rarity() : Rarity.RARE;
		if (minRarity.get() == MinRarity.EXTRAORDINARY && r.ordinal() < Rarity.EXTRAORDINARY.ordinal()) return;
		if (minRarity.get() == MinRarity.RNGESUS && r.ordinal() < Rarity.PRAY_RNGESUS.ordinal()) return;
		show(p.label(), d != null ? d.name() : p.name(), p.magicFind());
	}

	/** Test from the menu or /blixutestraredrop: a named drop, or a random one from the table, at a random Magic Find. */
	public void test(@Nullable String name) {
		test(name, null);
	}

	/** {@code look}: FULL or HOTBAR to force that variant, null for the Look setting. */
	public void test(@Nullable String name, @Nullable Style look) {
		java.util.Random r = new java.util.Random();
		Drop d = name != null ? SlayerDrops.find(name) : SlayerDrops.DROPS.get(r.nextInt(SlayerDrops.DROPS.size()));
		String label = d == null ? "RARE" : switch (d.rarity()) {
			case RARE -> "RARE";
			case EXTRAORDINARY -> "VERY RARE";
			case PRAY_RNGESUS -> "CRAZY RARE";
			case RNGESUS_INCARNATE -> "INSANE";
		};
		show(label, d != null ? d.name() : name, 100 + r.nextInt(300), look);
	}

	private void show(String label, String name, double mf) {
		show(label, name, mf, null);
	}

	private void show(String label, String name, double mf, @Nullable Style look) {
		Drop d = SlayerDrops.find(name);
		int tier = 0;
		if (d != null && d.slayer().equals("Voidgloom Seraph") && VoidgloomTracker.boss != null) {
			tier = switch (VoidgloomTracker.boss.tier) { case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; default -> 0; };
		}
		int color = d != null ? d.rarity().color : 0xFFFFAA33;
		shown = new Shown(label.endsWith("DROP!") ? label : label + " DROP!", name, d, mf, color, System.currentTimeMillis(), tier, look);
		stack = d != null ? new ItemStack(d.icon()) : new ItemStack(net.minecraft.world.item.Items.NETHER_STAR);
		itemId = d != null ? d.id() : name.toUpperCase(Locale.ROOT).replace("'", "").replace(' ', '_');
		fromInventory = false;
		PriceService.prefetch(itemId);
		com.blixu.blixuaddons.client.feature.slayer.NeuItems.stack(itemId); // start loading its real look
		if (sound.get()) {
			Minecraft mc = Minecraft.getInstance();
			mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.7f));
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		// The real item (with its skull texture and SkyBlock id) lands in the inventory a moment later.
		if (shown == null || mc.player == null || System.currentTimeMillis() - shown.startMs > 3000) return;
		String want = shown.name.toLowerCase(Locale.ROOT);
		for (ItemStack s : mc.player.getInventory().getNonEquipmentItems()) {
			if (s.isEmpty() || !Sidebar.strip(s.getHoverName().getString()).toLowerCase(Locale.ROOT).contains(want)) continue;
			stack = s.copy();
			fromInventory = true;
			var data = s.get(DataComponents.CUSTOM_DATA);
			if (data != null && data.copyTag().contains("id")) itemId = data.copyTag().getStringOr("id", itemId);
			return;
		}
	}

	private static final int WHITE = 0xFFFFFFFF, MF_BLUE = 0xFF55D7FF, GOLD = 0xFFFFD166;

	private @Nullable String priceText() {
		if (!showPrice.get() || itemId == null) return null;
		Double p = PriceService.price(itemId);
		return p == null ? "price loading…" : PriceService.coins(p) + " coins";
	}

	/** "1 in 236  ·  0.42%  ·  " in white, "251% MF" in light blue. */
	private @Nullable List<McText.Part> chanceParts(Shown s, float alpha) {
		if (!showChance.get()) return null;
		int w = Colors.withAlpha(WHITE, alpha), mf = Colors.withAlpha(MF_BLUE, alpha);
		String mfText = Math.round(s.mf) + "% Magic Find";
		if (s.drop == null) return List.of(new McText.Part("chance unknown  ·  ", w), new McText.Part(mfText, mf));
		double eff = SlayerDrops.withMagicFind(s.drop.baseChance(s.tier), s.mf);
		return List.of(new McText.Part(SlayerDrops.oneIn(eff) + "  ·  " + pct(eff) + "  ·  ", w), new McText.Part(mfText, mf));
	}

	private static String pct(double p) {
		return p >= 1 ? String.format(Locale.ROOT, "%.2f%%", p) : p >= 0.01 ? String.format(Locale.ROOT, "%.3f%%", p) : String.format(Locale.ROOT, "%.5f%%", p);
	}

	// ================================================================== drawing

	@Override
	public void onHud(GuiGraphicsExtractor g, float dt) {
		Shown s = shown;
		if (s == null) return;
		float t = (System.currentTimeMillis() - s.startMs) / 1000f, dur = duration.getF();
		if (t > dur) {
			shown = null;
			return;
		}
		if (!fromInventory && itemId != null) {
			ItemStack neu = com.blixu.blixuaddons.client.feature.slayer.NeuItems.stack(itemId);
			if (neu != null) stack = neu;
		}
		float sw = g.guiWidth(), sh = g.guiHeight();
		Style look = s.look != null ? s.look : style.get();
		if (look != Style.HOTBAR) drawFull(g, s, t, dur, sw, sh);
		if (look != Style.FULL) {
			float[] b = toastBox(sw, sh);
			drawToast(g, s, t, dur, b[0], b[1]);
		}
	}

	private static float easeOut(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return 1 - (1 - x) * (1 - x) * (1 - x);
	}

	/** Overshoots to ~1.1 and settles: a springy "pop". */
	private static float backOut(float x) {
		x = Mth.clamp(x, 0f, 1f);
		float c1 = 1.9f, c3 = c1 + 1;
		return 1 + c3 * (x - 1) * (x - 1) * (x - 1) + c1 * (x - 1) * (x - 1);
	}

	/** The real 3D item model centred on (x, y), {@code k} x 16px, turned {@code angle} radians around its vertical axis. */
	private void item(GuiGraphicsExtractor g, float x, float y, float k, float angle) {
		if (stack.isEmpty() || k <= 0.01f) return;
		((com.blixu.blixuaddons.client.mixin.GuiGraphicsAccessor) g).blixuaddons$guiRenderState().addPicturesInPictureState(
			com.blixu.blixuaddons.client.gui.preview.ItemSpinState.of(stack, angle, 0.18f, x, y, 16 * k, g.pose(), Ui.currentScissor()));
	}

	private static final float FLY = 0.85f, LAND_SPIN = 1.1f;

	private void drawFull(GuiGraphicsExtractor g, Shown s, float t, float dur, float sw, float sh) {
		float alpha = Math.min(Mth.clamp(t / 0.2f, 0f, 1f), Mth.clamp((dur - t) / 0.6f, 0f, 1f));
		float cx = sw / 2f, cy = sh * 0.42f;
		int c = s.color;
		boolean landed = t >= FLY;
		float since = t - FLY;
		Ui.rect(g, 0, 0, sw, sh, 0, Colors.withAlpha(0xFF04030A, 0.62f * alpha));
		// edge vignette in the rarity colour
		Ui.shadow(g, -40, -40, sw + 80, sh + 80, 0, 80, Colors.withAlpha(c, 0.18f * alpha));

		// light: a breathing glow and turning rays, bigger once it lands
		float grow = landed ? backOut(since / 0.5f) : easeOut(t / FLY) * 0.6f;
		float glowR = (52 + 8 * Mth.sin(t * 2.4f)) * grow;
		Ui.shadow(g, cx - glowR, cy - glowR, glowR * 2, glowR * 2, glowR, glowR * 1.2f, Colors.withAlpha(c, 0.6f * alpha));
		for (int i = 0; i < 18; i++) {
			float a = i / 18f * Mth.TWO_PI + t * 0.3f, len = (110 + 40 * Mth.sin(t * 1.8f + i * 1.7f)) * grow;
			float w = i % 2 == 0 ? 3.2f : 1.8f;
			Ui.line(g, cx, cy, cx + Mth.cos(a) * len, cy + Mth.sin(a) * len, w, Colors.withAlpha(c, 0.38f * alpha), Colors.withAlpha(c, 0f));
		}

		// landing: white flash, two shockwave rings and a burst of sparks
		if (landed && since < 0.9f) {
			float f = since / 0.9f;
			if (since < 0.15f) Ui.rect(g, 0, 0, sw, sh, 0, Colors.withAlpha(0xFFFFFFFF, 0.35f * (1 - since / 0.15f) * alpha));
			for (int ring = 0; ring < 2; ring++) {
				float rf = Mth.clamp(f * 1.4f - ring * 0.25f, 0f, 1f), r = 16 + easeOut(rf) * (90 + ring * 50);
				Ui.outline(g, cx - r, cy - r, r * 2, r * 2, r, 2.5f - ring, Colors.withAlpha(ring == 0 ? 0xFFFFFFFF : c, (1 - rf) * alpha));
			}
			for (int i = 0; i < 26; i++) {
				float a = i * 2.39996f, sp = 60 + (i * 37 % 50);
				float d = easeOut(f) * sp, size = 2.4f * (1 - f) + 0.6f;
				Ui.rect(g, cx + Mth.cos(a) * d - size / 2, cy + Mth.sin(a) * d - size / 2, size, size, size / 2,
					Colors.withAlpha(i % 3 == 0 ? 0xFFFFFFFF : c, (1 - f) * alpha));
			}
		}

		// flight: a curve in from the lower left over a high arc (quadratic bezier), growing as it comes,
		// then a full turn to show itself, then a slow spin with a gentle bob
		float x, y, k, angle;
		if (!landed) {
			float u = easeOut(t / FLY);
			float p0x = -40, p0y = sh * 0.95f, p1x = sw * 0.18f, p1y = -sh * 0.15f;
			x = (1 - u) * (1 - u) * p0x + 2 * (1 - u) * u * p1x + u * u * cx;
			y = (1 - u) * (1 - u) * p0y + 2 * (1 - u) * u * p1y + u * u * cy;
			k = Mth.lerp(u, 1.2f, 4.6f);
			angle = u * Mth.PI * 3; // tumbling on the way in
		} else {
			x = cx;
			y = cy + Mth.sin(since * 2.2f) * 2.5f;
			k = 4.6f * (1 + 0.18f * (1 - backOut(since / 0.35f))) ; // squash-and-settle as it lands
			float turn = easeOut(since / LAND_SPIN);
			angle = turn * Mth.TWO_PI + Math.max(0, since - LAND_SPIN) * 0.9f;
		}
		item(g, x, y, k, angle);

		// text pops in after landing: label, name, price, chance (Minecraft font, white with black outline)
		float text = landed ? Mth.clamp(since / 0.25f, 0f, 1f) * alpha : 0f;
		if (text <= 0.01f) return;
		float pop = backOut(Mth.clamp(since / 0.35f, 0f, 1f));
		int outline = Colors.withAlpha(0xFF000000, text);
		McText.centered(g, s.label, cx, cy - 66, 2.2f * pop, Colors.withAlpha(c, text), outline);
		McText.centered(g, s.name, cx, cy + 44, 2.4f * pop, Colors.withAlpha(WHITE, text), outline);
		float ly = cy + 70;
		String price = priceText();
		if (price != null) {
			McText.centered(g, price, cx, ly, 1.5f, Colors.withAlpha(GOLD, text), outline);
			ly += 16;
		}
		List<McText.Part> chance = chanceParts(s, text);
		if (chance != null) McText.centered(g, chance, cx, ly, 1.2f, outline);
	}

	private static final float TOAST_W = 220, TOAST_H = 40;

	private float[] toastBox(float sw, float sh) {
		float k = toastScale.getF();
		return new float[] {sw * toastX.getF() / 100f - TOAST_W * k / 2, sh * toastY.getF() / 100f, TOAST_W * k, TOAST_H * k};
	}

	private void drawToast(GuiGraphicsExtractor g, Shown s, float t, float dur, float x, float y) {
		float in = Mth.clamp(t / 0.45f, 0f, 1f), alpha = Math.min(Mth.clamp(t / 0.12f, 0f, 1f), Mth.clamp((dur - t) / 0.5f, 0f, 1f));
		float pop = backOut(in), k = toastScale.getF();
		int c = s.color;
		g.pose().pushMatrix();
		// springs up from below and overshoots, scaled around its centre
		g.pose().translate(x + TOAST_W * k / 2, y + TOAST_H * k / 2 + (1 - easeOut(in)) * 24);
		g.pose().scale(k * (0.7f + 0.3f * pop), k * (0.7f + 0.3f * pop));
		g.pose().translate(-TOAST_W / 2, -TOAST_H / 2);
		float pulse = 0.5f + 0.5f * Mth.sin(t * 5f);
		Ui.shadow(g, 0, 3, TOAST_W, TOAST_H, 9, 12, Colors.withAlpha(0xFF000000, 0.55f * alpha));
		Ui.shadow(g, 0, 0, TOAST_W, TOAST_H, 9, 8 + 4 * pulse, Colors.withAlpha(c, (0.35f + 0.25f * pulse) * alpha));
		Ui.rect(g, 0, 0, TOAST_W, TOAST_H, 9, Colors.withAlpha(0xFF0B0B12, 0.95f * alpha));
		Ui.gradientH(g, 0, 0, TOAST_W * 0.65f, TOAST_H, 9, Colors.withAlpha(c, 0.28f * alpha), Colors.withAlpha(c, 0f));
		// a shine sweeping across once, right after it pops in
		float sweep = (t - 0.25f) / 0.6f;
		if (sweep > 0 && sweep < 1) {
			float sx = -30 + sweep * (TOAST_W + 60);
			Ui.gradientH(g, Math.max(0, sx - 30), 0, Math.min(30, sx), TOAST_H, 0, 0x00FFFFFF, Colors.withAlpha(0xFFFFFFFF, 0.28f * alpha));
		}
		Ui.outline(g, 0, 0, TOAST_W, TOAST_H, 9, 1f, Colors.withAlpha(c, 0.85f * alpha));
		// icon on a glowing disc, bouncing in, then a slow turn
		float ib = backOut(Mth.clamp((t - 0.1f) / 0.4f, 0f, 1f));
		Ui.shadow(g, 6, 6, 28, 28, 14, 9, Colors.withAlpha(c, 0.55f * alpha));
		item(g, 20, 20, 1.55f * ib, t * 1.3f);
		int outline = Colors.withAlpha(0xFF000000, alpha);
		McText.left(g, List.of(new McText.Part(Ui.ellipsize(s.name, (TOAST_W - 48) / 1.0f, UiFont.SEMIBOLD), Colors.withAlpha(c, alpha))), 40, 5, 1f, outline);
		String price = priceText();
		McText.left(g, List.of(new McText.Part(s.label + (price != null ? "  " : ""), Colors.withAlpha(WHITE, alpha)),
			new McText.Part(price != null ? price : "", Colors.withAlpha(GOLD, alpha))), 40, 16, 0.75f, outline);
		List<McText.Part> chance = chanceParts(s, alpha);
		if (chance != null) McText.left(g, chance, 40, 26, 0.75f, outline);
		g.pose().popMatrix();
	}

	/** Small sample for the Slayers page preview. */
	public void drawPreviewToast(GuiGraphicsExtractor g, float x, float y) {
		Shown keep = shown;
		ItemStack keepStack = stack;
		String keepId = itemId;
		if (keep == null) {
			Drop d = SlayerDrops.find("Judgement Core");
			shown = new Shown("CRAZY RARE DROP!", "Judgement Core", d, 251, d.rarity().color, System.currentTimeMillis() - 1500, 4, null);
			ItemStack neu = com.blixu.blixuaddons.client.feature.slayer.NeuItems.stack(d.id());
			stack = neu != null ? neu : new ItemStack(d.icon());
			itemId = d.id();
		}
		drawToast(g, shown, 1.5f, 99, x, y);
		shown = keep;
		stack = keepStack;
		itemId = keepId;
	}

	@Override
	public List<com.blixu.blixuaddons.client.hud.HudElement> hudElements() {
		return List.of(new com.blixu.blixuaddons.client.hud.HudElement() {
			public String name() { return "Rare drop notification"; }
			public boolean shown() { return isEnabled() && style.get() != Style.FULL; }
			public float[] box(float sw, float sh) { return toastBox(sw, sh); }
			public void moveTo(float x, float y, float sw, float sh) {
				float[] b = toastBox(sw, sh);
				toastX.set((double) ((x + b[2] / 2) / sw * 100));
				toastY.set((double) (y / sh * 100));
			}
			public void resize(float steps) { toastScale.set(toastScale.get() + steps * 0.05); }
			public void drawSample(GuiGraphicsExtractor g, float sw, float sh) {
				if (shown != null) return;
				float[] b = toastBox(sw, sh);
				drawPreviewToast(g, b[0], b[1]); // drawToast applies the size itself
			}
		});
	}
}
