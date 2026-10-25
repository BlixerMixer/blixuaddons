package com.blixu.blixuaddons.client.module.impl.combat;

import com.blixu.blixuaddons.client.anim.Easing;
import com.blixu.blixuaddons.client.feature.EntityVisuals;
import com.blixu.blixuaddons.client.hud.HudPanel;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.render.ScreenProjector;
import com.blixu.blixuaddons.client.render.Ui;
import com.blixu.blixuaddons.client.render.UiFont;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating damage numbers. Source "Hypixel" replaces the server's own damage tags (invisible armor
 * stands named "1,234" or crits "✧1,234✧"): those stands are hidden and each one becomes one of our
 * tags with its exact value. Source "Synced health" shows the health change the server syncs (before
 * vs after). Either way tags behind walls are culled, and a crowded spot (10+ tags) only gets 1 in 5.
 */
public class DamageTags extends Module {
	public enum Style {
		SIMPLE("Simple"), OUTLINED("Outlined"), CRIT("Bold critical"), GRADIENT("Gradient"), SMALL("Small label"), POPUP("Animated pop-up"), CUSTOM_FONT("Custom font");
		public final String label;
		Style(String l) { label = l; }
	}

	public enum Font { INTER("Inter"), INTER_BOLD("Inter Bold"), INTER_SEMIBOLD("Inter SemiBold"), MINECRAFT("Minecraft");
		public final String label;
		Font(String l) { label = l; }
	}

	public enum Source { HYPIXEL("Replace Hypixel tags"), HEALTH("Synced health"), BOTH("Both");
		public final String label;
		Source(String l) { label = l; }
	}

	public final EnumSetting<Source> source = add(new EnumSetting<>("source", "Numbers from", Source.HYPIXEL, s -> s.label));
	public final ColorSetting critColor = add(new ColorSetting("crit_color", "Critical hit colour", 0xFFB020).visibleWhen(() -> source.get() != Source.HEALTH));
	public final BoolSetting shortNumbers = add(new BoolSetting("short_numbers", "Short numbers (84.9k, 3.2M)", true).visibleWhen(() -> source.get() != Source.HEALTH));
	public final BoolSetting thinCrowds = add(new BoolSetting("thin_crowds", "Crowded spots: show 1 in 5 past 10 tags", true));
	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.POPUP, s -> s.label));
	public final EnumSetting<Font> font = add(new EnumSetting<>("font", "Font", Font.INTER_BOLD, f -> f.label));
	public final ColorSetting textColor = add(new ColorSetting("text_color", "Text colour", 0xFF3B5C));
	public final ColorSetting secondary = add(new ColorSetting("secondary", "Gradient colour", 0xFACC15));
	public final NumberSetting fontSize = add(new NumberSetting("font_size", "Font size", 1.2, 0.5, 3, 0.05));
	public final BoolSetting outline = add(new BoolSetting("outline", "Outline", true));
	public final BoolSetting shadow = add(new BoolSetting("shadow", "Shadow", true));
	public final BoolSetting background = add(new BoolSetting("background", "Background", false));
	public final ColorSetting backgroundColor = add(new ColorSetting("background_color", "Background colour", 0xB0101218).visibleWhen(background::get));
	public final NumberSetting rise = add(new NumberSetting("rise", "Rise distance (blocks)", 0.8, 0, 3, 0.05));
	public final NumberSetting lifetime = add(new NumberSetting("lifetime", "Lifetime (s)", 1.1, 0.3, 4, 0.05));
	public final EnumSetting<Easing> fadeCurve = add(new EnumSetting<>("fade", "Fade curve", Easing.EASE_IN, e -> e.label));
	public final NumberSetting rounding = add(new NumberSetting("rounding", "Decimals", 1, 0, 2, 1));
	public final BoolSetting typeColors = add(new BoolSetting("type_colors", "Damage-type colours (fire, poison, wither)", true));
	public final BoolSetting healing = add(new BoolSetting("healing", "Show healing", true));
	public final ColorSetting healColor = add(new ColorSetting("heal_color", "Healing colour", 0x34F59A).visibleWhen(healing::get));
	public final NumberSetting maxTags = add(new NumberSetting("max", "Max active tags", 40, 5, 150, 1));
	public final BoolSetting merge = add(new BoolSetting("merge", "Merge rapid hits", true));
	public final NumberSetting range = add(new NumberSetting("range", "Range (blocks)", 32, 4, 96, 1));
	public final BoolSetting onlyVisible = add(new BoolSetting("only_visible", "Only entities in line of sight", true));

	private static final class Tag {
		int entityId;
		double x, y, z;
		float value;
		int color;
		float age;
		float jitterX;
		/** Hypixel tags carry their own text; null = format {@link #value}. */
		String text;
		boolean crit;
	}

	/** Read by EntityRenderSkipMixin every frame: hide Hypixel's own damage stands. */
	public static boolean replacing;
	private static final java.util.regex.Pattern HYPIXEL_DAMAGE = java.util.regex.Pattern.compile("^([✧✯]?)(\\d[\\d,.]*)([kKmMbB]?)([✧✯]?)[❤♞☄✷⚔ﬗ]*$");
	private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Object> NAME_SEEN = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
	private static final it.unimi.dsi.fastutil.ints.IntOpenHashSet DAMAGE_STANDS = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
	private int crowdSkip;

	private final List<Tag> tags = new ArrayList<>();
	private final java.util.Random random = new java.util.Random();

	public DamageTags() {
		super("damage_tags", "Damage Tags",
			"Floating damage and healing numbers from the health the server sends you.",
			Category.COMBAT, "damage", false);
	}

	/** From CombatEvents: synced health changed by {@code delta} (negative = damage). */
	public void onHealthChange(LivingEntity e, float delta) {
		if (source.get() == Source.HYPIXEL) return;
		if (delta > 0 && !healing.get()) return;
		if (EntityVisuals.isSelf(e) && Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
		if (onlyVisible.get() && !EntityVisuals.visible(e)) return;
		int color = delta > 0 ? healColor.argb() : damageColor(e);
		if (merge.get()) {
			for (Tag t : tags) {
				if (t.entityId == e.getId() && t.age < 0.35f && Math.signum(t.value) == Math.signum(delta)) {
					t.value += delta;
					t.age = Math.min(t.age, 0.1f);
					return;
				}
			}
		}
		Tag t = newTag(e.getX(), e.getY() + e.getBbHeight() + 0.25, e.getZ());
		if (t == null) return;
		t.entityId = e.getId();
		t.value = delta;
		t.color = color;
	}

	/** A new tag at a spot, or null when that spot is crowded and this one is thinned out. */
	private @org.jspecify.annotations.Nullable Tag newTag(double x, double y, double z) {
		if (thinCrowds.get()) {
			int near = 0;
			for (Tag o : tags) {
				double dx = o.x - x, dy = o.y - y, dz = o.z - z;
				if (dx * dx + dy * dy + dz * dz < 9.0) near++;
			}
			if (near >= 10 && (crowdSkip++ % 5) != 0) return null;
		}
		if (tags.size() >= maxTags.getI()) tags.remove(0);
		Tag t = new Tag();
		t.x = x;
		t.y = y;
		t.z = z;
		t.entityId = -1;
		t.jitterX = (random.nextFloat() - 0.5f) * 14f;
		tags.add(t);
		return t;
	}

	/**
	 * Whether the stand is one of Hypixel's damage numbers; the first time one is seen it becomes our
	 * tag. Cached per stand by its name component, so a stand costs one regex per name change.
	 */
	public static boolean isDamageStand(net.minecraft.world.entity.decoration.ArmorStand s) {
		if (!s.isInvisible()) return false;
		net.minecraft.network.chat.Component name = s.getCustomName();
		if (name == null) return false;
		int id = s.getId();
		if (NAME_SEEN.get(id) == name) return DAMAGE_STANDS.contains(id);
		NAME_SEEN.put(id, name);
		String text = net.minecraft.ChatFormatting.stripFormatting(name.getString());
		java.util.regex.Matcher m = text == null ? null : HYPIXEL_DAMAGE.matcher(text.replace(" ", ""));
		if (m == null || !m.matches()) {
			DAMAGE_STANDS.remove(id);
			return false;
		}
		if (DAMAGE_STANDS.add(id)) {
			DamageTags self = com.blixu.blixuaddons.client.module.ModuleManager.active(DamageTags.class);
			if (self != null) self.fromStand(s, m.group(2), m.group(3), !m.group(1).isEmpty() || !m.group(4).isEmpty());
		}
		return true;
	}

	private void fromStand(net.minecraft.world.entity.decoration.ArmorStand s, String digits, String suffix, boolean crit) {
		if (onlyVisible.get() && !EntityVisuals.visible(s)) return; // culled: behind a wall
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.distanceToSqr(s) > range.get() * range.get()) return;
		Tag t = newTag(s.getX(), s.getY() + s.getBbHeight() + 0.5, s.getZ());
		if (t == null) return;
		t.crit = crit;
		t.color = crit ? critColor.argb() : textColor.argb();
		t.text = shortNumbers.get() ? shortNumber(digits, suffix) : digits + suffix;
	}

	/** "84,919" -> "84.9k"; an existing suffix ("3.2M") is kept as it is. */
	static String shortNumber(String digits, String suffix) {
		if (!suffix.isEmpty()) return digits + suffix;
		double v;
		try {
			v = Double.parseDouble(digits.replace(",", ""));
		} catch (NumberFormatException e) {
			return digits;
		}
		String[] units = {"", "k", "M", "B", "T"};
		int u = 0;
		while (v >= 1000 && u < units.length - 1) {
			v /= 1000;
			u++;
		}
		if (u == 0) return digits;
		String n = v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
		if (n.endsWith(".0")) n = n.substring(0, n.length() - 2);
		return n + units[u];
	}

	public static void selfCheck() {
		check(shortNumber("84,919", "").equals("84.9k"), "84,919");
		check(shortNumber("999", "").equals("999"), "999");
		check(shortNumber("3,215,000", "").equals("3.2M"), "3,215,000");
		check(shortNumber("150,000", "").equals("150k"), "150,000");
		check(shortNumber("3.2", "M").equals("3.2M"), "3.2M");
		check(HYPIXEL_DAMAGE.matcher("✧1,234,567✧").matches(), "crit");
		check(HYPIXEL_DAMAGE.matcher("84,919").matches(), "plain");
		check(!HYPIXEL_DAMAGE.matcher("Voidling Fanatic").matches(), "mob name");
		check(!HYPIXEL_DAMAGE.matcher("[Lv183] Voidling Fanatic 581.5k/750k❤").matches(), "health tag");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new IllegalStateException("DamageTags self-check failed: " + what);
	}

	private int damageColor(LivingEntity e) {
		if (typeColors.get()) {
			if (e.isOnFire()) return 0xFFFF7A1A;
			if (e.hasEffect(MobEffects.POISON)) return 0xFF34F59A;
			if (e.hasEffect(MobEffects.WITHER)) return 0xFF5C6378;
		}
		return textColor.argb();
	}

	@Override
	public void onHud(GuiGraphicsExtractor g, float dt) {
		if (tags.isEmpty()) return;
		ScreenProjector.begin(g.guiWidth(), g.guiHeight());
		float life = lifetime.getF();
		UiFont uiFont = switch (font.get()) {
			case INTER -> UiFont.BODY;
			case INTER_BOLD, MINECRAFT -> UiFont.TITLE;
			case INTER_SEMIBOLD -> UiFont.SEMIBOLD;
		};
		if (style.get() == Style.SMALL) uiFont = UiFont.SMALL;
		for (int i = tags.size() - 1; i >= 0; i--) {
			Tag t = tags.get(i);
			t.age += dt;
			if (t.age >= life) {
				tags.remove(i);
				continue;
			}
			float p = t.age / life;
			float up = Easing.CUBIC_OUT.apply(p) * rise.getF();
			if (!ScreenProjector.project(t.x, t.y + up, t.z)) continue;
			float alpha = alphaAt(p);
			float scale = fontSize.getF() * (uiFont == UiFont.TITLE ? 0.75f : 1f);
			if (style.get() == Style.POPUP) scale *= p < 0.15f ? Easing.BACK_OUT.apply(p / 0.15f) * 1.15f : 1.15f - 0.15f * Math.min(1f, (p - 0.15f) * 3f);
			if (style.get() == Style.CRIT || t.crit) scale *= 1.3f;
			String text;
			if (t.text != null) {
				text = t.crit ? "✧" + t.text + "✧" : t.text;
			} else {
				text = format(t.value);
				if (t.value > 0) text = "+" + text;
				else if (style.get() == Style.CRIT) text = text + "!";
			}
			drawTag(g, text, ScreenProjector.x + t.jitterX, ScreenProjector.y, scale, t.color, alpha, uiFont, t.crit);
		}
	}

	private void drawTag(GuiGraphicsExtractor g, String text, float x, float y, float scale, int color, float alpha, UiFont uiFont) {
		drawTag(g, text, x, y, scale, color, alpha, uiFont, false);
	}

	/** {@code crit}: gradient from the crit colour to the gradient colour, whatever the style. */
	private void drawTag(GuiGraphicsExtractor g, String text, float x, float y, float scale, int color, float alpha, UiFont uiFont, boolean crit) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		float w = font.get() == Font.MINECRAFT ? Minecraft.getInstance().font.width(text) : Ui.width(text, uiFont);
		float tx = -w / 2f, ty = -4.5f;
		if (background.get()) Ui.rect(g, tx - 3, ty - 2, w + 6, 13, 3, Colors.withAlpha(backgroundColor.argb(), alpha));
		int c = Colors.withAlpha(color, alpha);
		if (shadow.get()) drawText(g, text, tx + 0.8f, ty + 0.8f, Colors.withAlpha(0xFF000000, alpha * 0.6f), uiFont);
		if (outline.get() || style.get() == Style.OUTLINED) {
			int o = Colors.withAlpha(0xFF101218, alpha);
			drawText(g, text, tx - 0.6f, ty, o, uiFont);
			drawText(g, text, tx + 0.6f, ty, o, uiFont);
			drawText(g, text, tx, ty - 0.6f, o, uiFont);
			drawText(g, text, tx, ty + 0.6f, o, uiFont);
		}
		if (style.get() == Style.GRADIENT || crit) {
			// Per-character gradient from text colour to the secondary colour.
			float cx = tx;
			for (int i = 0; i < text.length(); i++) {
				String ch = text.substring(i, i + 1);
				int cc = Colors.withAlpha(Colors.lerp(color, secondary.argb(), text.length() <= 1 ? 0 : i / (float) (text.length() - 1)), alpha);
				drawText(g, ch, cx, ty, cc, uiFont);
				cx += font.get() == Font.MINECRAFT ? Minecraft.getInstance().font.width(ch) : Ui.width(ch, uiFont);
			}
		} else {
			if (HudPanel.style().isEnabled() && HudPanel.style().textGlow.get()) Ui.shadow(g, tx - 1, ty - 0.5f, w + 2, 9, 4, 5, Colors.withAlpha(color, 0.35f * alpha));
			drawText(g, text, tx, ty, c, uiFont);
		}
		g.pose().popMatrix();
	}

	private void drawText(GuiGraphicsExtractor g, String text, float x, float y, int color, UiFont uiFont) {
		if (font.get() == Font.MINECRAFT) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.text(Minecraft.getInstance().font, text, 0, 0, color, false);
			g.pose().popMatrix();
		} else {
			Ui.text(g, text, x, y, color, uiFont);
		}
	}

	/**
	 * Fully visible for the first half of the life, then faded by the chosen curve. Clamped, because
	 * overshooting curves (Spring, Back) pass 1 early and would otherwise hide the tag almost at once.
	 */
	private float alphaAt(float p) {
		float f = Mth.clamp((p - 0.5f) / 0.5f, 0f, 1f);
		return Mth.clamp(1f - fadeCurve.get().apply(f), 0f, 1f);
	}

	private String format(float v) {
		float a = Math.abs(v);
		return switch (rounding.getI()) {
			case 0 -> String.valueOf(Math.round(a));
			case 2 -> String.format("%.2f", a);
			default -> String.format("%.1f", a);
		};
	}

	private final List<Tag> previewTags = new ArrayList<>();
	private float lastPreviewSpawn = -10, lastPreviewTime;

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		float dt = Math.max(0, Math.min(0.1f, time - lastPreviewTime));
		lastPreviewTime = time;
		float cx = x + w / 2, cy = y + h * 0.68f;
		com.blixu.blixuaddons.client.module.impl.combat.HitColor.figure(g, cx, cy, 1.4f, 0xFF5C6378);
		if (time - lastPreviewSpawn > 0.7f || time < lastPreviewSpawn) {
			lastPreviewSpawn = time;
			Tag t = new Tag();
			boolean heal = healing.get() && random.nextFloat() < 0.25f;
			t.value = heal ? 1 + random.nextFloat() * 4 : -(2 + random.nextFloat() * 18);
			t.color = heal ? healColor.argb() : textColor.argb();
			t.jitterX = (random.nextFloat() - 0.5f) * 30f;
			previewTags.add(t);
			if (previewTags.size() > 8) previewTags.remove(0);
		}
		UiFont uiFont = switch (font.get()) {
			case INTER -> UiFont.BODY;
			case INTER_BOLD, MINECRAFT -> UiFont.TITLE;
			case INTER_SEMIBOLD -> UiFont.SEMIBOLD;
		};
		if (style.get() == Style.SMALL) uiFont = UiFont.SMALL;
		float life = lifetime.getF();
		for (int i = previewTags.size() - 1; i >= 0; i--) {
			Tag t = previewTags.get(i);
			t.age += dt;
			if (t.age >= life) {
				previewTags.remove(i);
				continue;
			}
			float p = t.age / life;
			float up = com.blixu.blixuaddons.client.anim.Easing.CUBIC_OUT.apply(p) * rise.getF() * 40f;
			float scale = fontSize.getF() * (uiFont == UiFont.TITLE ? 0.75f : 1f);
			if (style.get() == Style.POPUP) scale *= p < 0.15f ? com.blixu.blixuaddons.client.anim.Easing.BACK_OUT.apply(p / 0.15f) * 1.15f : 1.15f - 0.15f * Math.min(1f, (p - 0.15f) * 3f);
			if (style.get() == Style.CRIT) scale *= 1.35f;
			String text = format(t.value);
			if (t.value > 0) text = "+" + text;
			else if (style.get() == Style.CRIT) text = text + "!";
			drawTag(g, text, cx + t.jitterX, cy - 32 - up, scale, t.color, alphaAt(p), uiFont);
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		replacing = source.get() != Source.HEALTH;
		if (mc.level == null) return;
		if (replacing) {
			// catch stands that never reach the renderer (out of view) so their ids are known
			for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.decoration.ArmorStand st) isDamageStand(st);
			}
			if (mc.level.getGameTime() % 100 == 0) {
				NAME_SEEN.keySet().removeIf((int id) -> mc.level.getEntity(id) == null);
				DAMAGE_STANDS.removeIf((int id) -> mc.level.getEntity(id) == null);
			}
		}
		// Keep tags attached to their entity while it moves.
		for (Tag t : tags) {
			var e = mc.level.getEntity(t.entityId);
			if (t.entityId >= 0 && e != null && t.age < 0.5f) {
				t.x = Mth.lerp(0.5, t.x, e.getX());
				t.z = Mth.lerp(0.5, t.z, e.getZ());
			}
		}
	}

	@Override
	protected void onDisable() {
		tags.clear();
		replacing = false;
	}

	@Override
	public void onWorldChange() {
		tags.clear();
		NAME_SEEN.clear();
		DAMAGE_STANDS.clear();
	}
}
