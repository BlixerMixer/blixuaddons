package com.blixu.blixuaddons.client.module.impl.combat;

import com.blixu.blixuaddons.client.feature.EntityVisuals;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen-space lines to every wanted entity in range, walls or not.
 */
public class Tracers extends Module {
	public enum Style { STRAIGHT("Straight"), DOTTED("Dotted"), GRADIENT("Gradient"), ANIMATED("Animated"), MARKER("Endpoint marker only");
		public final String label;
		Style(String l) { label = l; }
	}

	public enum Anchor { CROSSHAIR("Crosshair"), CENTER("Screen centre"), BOTTOM("Bottom centre");
		public final String label;
		Anchor(String l) { label = l; }
	}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.GRADIENT, s -> s.label));
	public final EnumSetting<Anchor> anchor = add(new EnumSetting<>("anchor", "Start anchor", Anchor.BOTTOM, a -> a.label));
	public final BoolSetting players = add(new BoolSetting("players", "Players", true));
	public final BoolSetting mobs = add(new BoolSetting("mobs", "Mobs", false));
	public final BoolSetting items = add(new BoolSetting("items", "Dropped items", false));
	public final BoolSetting projectiles = add(new BoolSetting("projectiles", "Projectiles", false));
	public final ColorSetting playerColor = add(new ColorSetting("player_color", "Player colour", 0x22D3EE));
	public final ColorSetting mobColor = add(new ColorSetting("mob_color", "Mob colour", 0xFF3B5C));
	public final ColorSetting itemColor = add(new ColorSetting("item_color", "Item colour", 0xFACC15));
	public final ColorSetting projectileColor = add(new ColorSetting("projectile_color", "Projectile colour", 0xFFFFFF));
	public final BoolSetting teamColors = add(new BoolSetting("team_colors", "Use team / friend colours", true));
	public final com.blixu.blixuaddons.client.module.setting.StringSetting friends = add(new com.blixu.blixuaddons.client.module.setting.StringSetting("friends", "Friends (comma separated)", "", 256));
	public final ColorSetting friendColor = add(new ColorSetting("friend_color", "Friend colour", 0x34F59A));
	public final NumberSetting thickness = add(new NumberSetting("thickness", "Line thickness", 1, 0.5, 4, 0.25));
	public final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 0.75, 0.05, 1, 0.05));
	public final BoolSetting endpoint = add(new BoolSetting("endpoint", "Endpoint marker", true));
	public final NumberSetting range = add(new NumberSetting("range", "Max distance", 64, 8, 256, 1));
	public final NumberSetting maxLines = add(new NumberSetting("max_lines", "Max lines", 20, 1, 100, 1));
	public final BoolSetting distanceLabel = add(new BoolSetting("distance_label", "Distance label", true));

	private record Target(Entity entity, int color, float dist) {}

	private final List<Target> targets = new ArrayList<>();

	public Tracers() {
		super("tracers", "Tracers",
			"Lines from your screen to players, mobs, items or projectiles, through walls.",
			Category.COMBAT, "tracer", false);
	}

	private int colorFor(Entity e) {
		if (e instanceof Player p) {
			if (teamColors.get()) {
				if (!friends.get().isBlank()
					&& ("," + friends.get().toLowerCase() + ",").replace(" ", "").contains("," + p.getGameProfile().name().toLowerCase() + ",")) {
					return friendColor.argb();
				}
				if (p.getTeam() != null) return 0xFF000000 | p.getTeamColor();
			}
			return playerColor.argb();
		}
		if (e instanceof ItemEntity) return itemColor.argb();
		if (e instanceof Projectile) return projectileColor.argb();
		return mobColor.argb();
	}

	private boolean wanted(Entity e) {
		if (e instanceof Player) return players.get();
		if (e instanceof ItemEntity) return items.get();
		if (e instanceof Projectile) return projectiles.get();
		return e instanceof LivingEntity && mobs.get();
	}

	@Override
	public void onTick(Minecraft mc) {
		targets.clear();
		if (mc.level == null || mc.player == null) return;
		double r2 = range.get() * range.get();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || e.isRemoved() || e.isInvisible() || !wanted(e)) continue;
			double d2 = e.distanceToSqr(mc.player);
			if (d2 > r2) continue;
			targets.add(new Target(e, colorFor(e), (float) Math.sqrt(d2)));
		}
		targets.sort((a, b) -> Float.compare(a.dist, b.dist));
	}

	@Override
	public void onHud(GuiGraphicsExtractor g, float dt) {
		if (targets.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		ScreenProjector.begin(g.guiWidth(), g.guiHeight());
		float ax = g.guiWidth() / 2f;
		float ay = switch (anchor.get()) {
			case CROSSHAIR, CENTER -> g.guiHeight() / 2f;
			case BOTTOM -> g.guiHeight();
		};
		float op = opacity.getF();
		float w = thickness.getF();
		float time = (System.nanoTime() / 1_000_000L) / 1000f;
		int n = Math.min(maxLines.getI(), targets.size());
		for (int i = 0; i < n; i++) {
			Target t = targets.get(i);
			Entity e = t.entity;
			double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.5, z = Mth.lerp(pt, e.zo, e.getZ());
			boolean onScreen = ScreenProjector.project(x, y, z);
			if (!onScreen) ScreenProjector.projectClamped(x, y, z);
			float ex = ScreenProjector.x, ey = ScreenProjector.y;
			int c = Colors.withAlpha(t.color, op);
			int faded = Colors.withAlpha(t.color, op * 0.15f);
			switch (style.get()) {
				case STRAIGHT -> Ui.line(g, ax, ay, ex, ey, w, c, c);
				case GRADIENT -> Ui.line(g, ax, ay, ex, ey, w, faded, c);
				case DOTTED, ANIMATED -> {
					float len = (float) Math.hypot(ex - ax, ey - ay);
					float dash = 5f, gap = 4f;
					float offset = style.get() == Style.ANIMATED ? (time * 30f) % (dash + gap) : 0f;
					for (float s = -offset; s < len; s += dash + gap) {
						float s0 = Math.max(0, s), s1 = Math.min(len, s + dash);
						if (s1 <= s0) continue;
						float k0 = s0 / len, k1 = s1 / len;
						Ui.line(g, ax + (ex - ax) * k0, ay + (ey - ay) * k0, ax + (ex - ax) * k1, ay + (ey - ay) * k1, w, c, c);
					}
				}
				case MARKER -> { }
			}
			if (onScreen && (endpoint.get() || style.get() == Style.MARKER)) {
				Ui.circle(g, ex, ey, 2.5f + w * 0.5f, c);
				Ui.shadow(g, ex - 3, ey - 3, 6, 6, 3, 4, Colors.withAlpha(t.color, op * 0.5f));
			}
			if (onScreen && distanceLabel.get()) {
				Ui.textCentered(g, String.format("%.0fm", t.dist), ex, ey + 5, Colors.withAlpha(0xFFECEEF5, op), UiFont.SMALL);
			}
		}
	}

	@Override
	public void onWorldChange() {
		targets.clear();
	}

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		float ax = x + w / 2f;
		float ay = switch (anchor.get()) {
			case CROSSHAIR, CENTER -> y + h / 2f;
			case BOTTOM -> y + h - 2;
		};
		int[] cols = {playerColor.argb(), mobColor.argb(), itemColor.argb(), projectileColor.argb()};
		boolean[] on = {players.get(), mobs.get(), items.get(), projectiles.get()};
		float op = opacity.getF(), lw = thickness.getF();
		for (int i = 0; i < 4; i++) {
			if (!on[i]) continue;
			float ang = time * 0.5f + i * 1.57f;
			float ex = x + w / 2f + net.minecraft.util.Mth.cos(ang) * w * 0.38f, ey = y + h * 0.45f + net.minecraft.util.Mth.sin(ang * 1.3f) * h * 0.28f;
			int c = com.blixu.blixuaddons.client.style.Colors.withAlpha(cols[i], op);
			switch (style.get()) {
				case STRAIGHT -> com.blixu.blixuaddons.client.render.Ui.line(g, ax, ay, ex, ey, lw, c, c);
				case GRADIENT -> com.blixu.blixuaddons.client.render.Ui.line(g, ax, ay, ex, ey, lw, com.blixu.blixuaddons.client.style.Colors.withAlpha(cols[i], op * 0.15f), c);
				case DOTTED, ANIMATED -> {
					float len = (float) Math.hypot(ex - ax, ey - ay);
					float off = style.get() == Style.ANIMATED ? (time * 30f) % 9f : 0f;
					for (float s = -off; s < len; s += 9f) {
						float s0 = Math.max(0, s), s1 = Math.min(len, s + 5f);
						if (s1 <= s0) continue;
						com.blixu.blixuaddons.client.render.Ui.line(g, ax + (ex - ax) * s0 / len, ay + (ey - ay) * s0 / len, ax + (ex - ax) * s1 / len, ay + (ey - ay) * s1 / len, lw, c, c);
					}
				}
				case MARKER -> { }
			}
			if (endpoint.get() || style.get() == Style.MARKER) com.blixu.blixuaddons.client.render.Ui.circle(g, ex, ey, 2.5f + lw * 0.5f, c);
			if (distanceLabel.get()) com.blixu.blixuaddons.client.render.Ui.textCentered(g, (12 + i * 9) + "m", ex, ey + 4, com.blixu.blixuaddons.client.style.Colors.withAlpha(0xFFECEEF5, op), com.blixu.blixuaddons.client.render.UiFont.SMALL);
		}
	}
}
