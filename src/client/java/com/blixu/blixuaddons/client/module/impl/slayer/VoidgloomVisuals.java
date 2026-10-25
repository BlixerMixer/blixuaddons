package com.blixu.blixuaddons.client.module.impl.slayer;

import com.blixu.blixuaddons.client.feature.MobGlow;
import com.blixu.blixuaddons.client.feature.slayer.ServerClock;
import com.blixu.blixuaddons.client.feature.slayer.VoidgloomTracker;
import com.blixu.blixuaddons.client.feature.slayer.VoidgloomTracker.Boss;
import com.blixu.blixuaddons.client.feature.slayer.VoidgloomTracker.Phase;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.ActionSetting;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.module.setting.SectionSetting;
import com.blixu.blixuaddons.client.render.GeoBatch;
import com.blixu.blixuaddons.client.render.PixelFont;
import com.blixu.blixuaddons.client.render.Ui;
import com.blixu.blixuaddons.client.render.UiFont;
import com.blixu.blixuaddons.client.render.WorldCtx;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Voidgloom Seraph visuals (the EmanTracker script, rebuilt on our GLSL rendering): phase-coloured glow
 * for your boss, its mini bosses and Voidlings, enchant glint bands, aura rings, laser walls / show /
 * overhead timer, beacon alarm with landing prediction, Nukekubi heads, an off-screen tracer, six
 * status-window styles, and big TERM NOW / HITS OVER! / remaining-hits screen text.
 */
public class VoidgloomVisuals extends Module {
	public enum HudStyleKind { CARD("Card"), TOOLTIP("Tooltip"), STRIP("Strip"), SCOREBOARD("Scoreboard"), FOCUS("Focus"), PILLS("Pills"), OFF("Off");
		public final String label;
		HudStyleKind(String l) { label = l; }
	}

	public enum Anchor { LEFT("Left"), CENTER("Center"), RIGHT("Right");
		public final String label;
		Anchor(String l) { label = l; }
	}

	/** One glow target's look: on, colour, flash, glint, aura (boss, mini bosses, Voidlings). */
	public final class GlowCfg {
		public final BoolSetting on, flash, glint, aura;
		public final ColorSetting color;

		GlowCfg(String id, String label, int color, boolean onByDefault) {
			on = add(new BoolSetting(id + "_glow", label + " glow", onByDefault));
			this.color = add(new ColorSetting(id + "_glow_color", label + " glow colour", color));
			flash = add(new BoolSetting(id + "_flash", "Flash towards white", true));
			glint = add(new BoolSetting(id + "_glint", "Enchant glint bands", true));
			aura = add(new BoolSetting(id + "_aura", "Aura rings", true));
		}
	}

	// ---- Status window
	public final SectionSetting secHud = add(new SectionSetting("sec_hud", "Status window", "Your boss at a glance: health, shield, laser and beacon"));
	public final EnumSetting<HudStyleKind> hudStyle = add(new EnumSetting<>("hud_style", "Style", HudStyleKind.CARD, s -> s.label));
	public final EnumSetting<Anchor> pillAnchor = add(new EnumSetting<>("pill_anchor", "Pills line up on", Anchor.LEFT, a -> a.label).visibleWhen(() -> hudStyle.get() == HudStyleKind.PILLS));
	public final ActionSetting editHud = add(new ActionSetting("edit_hud", "Move and resize on screen", "Edit HUD layout",
		() -> Minecraft.getInstance().gui.setScreen(new com.blixu.blixuaddons.client.gui.HudEditScreen())));
	public final NumberSetting hudX = add(new NumberSetting("hud_x", "Position X (% of screen)", 2, 0, 100, 0.5));
	public final NumberSetting hudY = add(new NumberSetting("hud_y", "Position Y (% of screen)", 32, 0, 100, 0.5));
	public final NumberSetting hudScale = add(new NumberSetting("hud_scale", "Scale", 1, 0.5, 2.5, 0.05));

	// ---- Screen alerts
	public final SectionSetting secAlerts = add(new SectionSetting("sec_alerts", "Screen alerts", "Big text on your screen (place it in the HUD editor)"));
	public final BoolSetting termAlert = add(new BoolSetting("term_alert", "TERM NOW near the laser's end", true));
	public final NumberSetting termLead = add(new NumberSetting("term_lead", "TERM NOW lead (seconds)", 1, 0, 5, 0.1));
	public final BoolSetting termCountdown = add(new BoolSetting("term_countdown", "Countdown in its place until TERM NOW", true).visibleWhen(termAlert::get));
	public final BoolSetting hitsOverAlert = add(new BoolSetting("hits_over_alert", "HITS OVER! when the shield breaks", true));
	public final BoolSetting beaconSoonAlert = add(new BoolSetting("beacon_soon_alert", "BEACON SOON when it picks up a beacon", true));
	public final BoolSetting beaconDownAlert = add(new BoolSetting("beacon_down_alert", "BEACON DOWN! when a beacon lands", true));
	public final NumberSetting alertScale = add(new NumberSetting("alert_scale", "Alert size", 1, 0.4, 3, 0.05));
	public final NumberSetting alertX = add(new NumberSetting("alert_x", "Alert position X (% of screen)", 50, 0, 100, 0.5));
	public final NumberSetting alertY = add(new NumberSetting("alert_y", "Alert position Y (% of screen)", 42, 0, 100, 0.5));
	public final BoolSetting bigHits = add(new BoolSetting("big_hits", "Big remaining hits counter", true));
	public final NumberSetting bigHitsScale = add(new NumberSetting("big_hits_scale", "Hits counter size", 1, 0.4, 3, 0.05).visibleWhen(bigHits::get));
	public final NumberSetting bigHitsX = add(new NumberSetting("big_hits_x", "Hits counter X (% of screen)", 50, 0, 100, 0.5).visibleWhen(bigHits::get));
	public final NumberSetting bigHitsY = add(new NumberSetting("big_hits_y", "Hits counter Y (% of screen)", 62, 0, 100, 0.5).visibleWhen(bigHits::get));
	public final ActionSetting testAlert = add(new ActionSetting("test_alert", "Preview the alerts", "Test", () -> {
		VoidgloomTracker.alert("TERM NOW", 0xFFFFFFFF, 2500);
		testHitsUntil = System.currentTimeMillis() + 2500;
	}));

	// ---- Boss
	public final SectionSetting secGlowLook = add(new SectionSetting("sec_glow_look", "Glow look", "The glow around every Voidgloom glow; the design here is for beacons and heads"));
	public final com.blixu.blixuaddons.client.feature.GlowStyle.Look glowLook = com.blixu.blixuaddons.client.feature.GlowStyle.addLook(this::add, com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER, () -> true, 0.35, "Fill amount").withMobFill(this::add, () -> true);

	public final SectionSetting secBoss = add(new SectionSetting("sec_boss", "Voidgloom boss", "Your Seraph: glow by phase, glint, aura and a tracer"));
	public final GlowCfg bossGlow = new GlowCfg("boss", "Boss", 0xB84DFF, true);
	public final BoolSetting phaseGlow = add(new BoolSetting("phase_glow", "Glow in the phase colour", true));
	public final ColorSetting damageColor = add(new ColorSetting("damage_color", "Damage phase colour", 0xFF5C8A).visibleWhen(phaseGlow::get));
	public final ColorSetting shieldColor = add(new ColorSetting("shield_color", "Hits shield colour", 0xE58CFF).visibleWhen(phaseGlow::get));
	public final ColorSetting laserColor = add(new ColorSetting("laser_color", "Laser colour (yellow when the server lags)", 0x6BE3FF));
	public final BoolSetting bossTracer = add(new BoolSetting("boss_tracer", "Tracer when it's out of view", true));
	public final com.blixu.blixuaddons.client.feature.GlowStyle.Look bossLook = glowLook.designLook(this::add, com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER_BOSS, () -> true, "boss_");

	// ---- Mini bosses
	public final SectionSetting secMini = add(new SectionSetting("sec_mini", "Mini bosses", "Voidling Devotee, Radical and Voidcrazed Maniac"));
	public final GlowCfg miniGlow = new GlowCfg("mini", "Mini boss", 0xFFD84D, true);
	public final com.blixu.blixuaddons.client.feature.GlowStyle.Look miniLook = glowLook.designLook(this::add, com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER_MINI, () -> true, "mini_");

	// ---- Voidlings
	public final SectionSetting secVoidlings = add(new SectionSetting("sec_voidlings", "Voidlings", "Voidling Fanatics and Extremists"));
	public final GlowCfg fanaticGlow = new GlowCfg("fanatic", "Voidling", 0x4DE8FF, true);
	public final com.blixu.blixuaddons.client.feature.GlowStyle.Look voidlingLook = glowLook.designLook(this::add, com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER_VOIDLING, () -> true, "voidling_");

	// ---- Laser
	public final SectionSetting secLaser = add(new SectionSetting("sec_laser", "Laser phase", "The 8.2 s laser, timed on real server ticks"));
	public final BoolSetting laserOverhead = add(new BoolSetting("laser_overhead", "Seconds left above its head", true));
	public final BoolSetting laserShow = add(new BoolSetting("laser_show", "Spinning rings and start flash", true));
	public final BoolSetting laserWalls = add(new BoolSetting("laser_walls", "Glowing laser walls", true));

	// ---- Hazards
	public final SectionSetting secHazards = add(new SectionSetting("sec_hazards", "Hazards", "Beacons and Nukekubi heads"));
	public final BoolSetting beaconAlarm = add(new BoolSetting("beacon_alarm", "Beacon alarm (path, landing, countdown)", true));
	public final ColorSetting beaconColor = add(new ColorSetting("beacon_color", "Beacon colour", 0xFF2A2A).visibleWhen(beaconAlarm::get));
	public final BoolSetting nukekubiHeads = add(new BoolSetting("nukekubi_heads", "Nukekubi heads (glow, label, line)", true));
	public final ColorSetting headColor = add(new ColorSetting("head_color", "Head colour", 0xFF4FD8).visibleWhen(nukekubiHeads::get));
	public final BoolSetting debug = add(new BoolSetting("debug", "Log detection to the console", false));

	private long testHitsUntil;

	public VoidgloomVisuals() {
		super("voidgloom", "Voidgloom Seraph",
			"Enderman slayer: boss HUD, phase glows, laser walls, beacon and head alarms, TERM NOW and hits alerts.",
			Category.SLAYERS, "damage", false);
		MobGlow.featureGlow = this::glowColorOf;
		MobGlow.featureLook = this::lookOf;
		// positions and sizes are set by dragging / scrolling in the HUD editor, not as sliders (still saved)
		for (NumberSetting s : new NumberSetting[] {hudX, hudY, hudScale, alertScale, alertX, alertY, bigHitsScale, bigHitsX, bigHitsY}) s.visibleWhen(() -> false);
	}

	// ================================================================== glow colours

	private static final long FLASH_MS = 320;

	private int laserCol() {
		return ServerClock.tps() < 19.5 ? 0xFFFFFF55 : laserColor.argb() | 0xFF000000;
	}

	private int phaseColor() {
		return switch (VoidgloomTracker.phase()) {
			case LASER -> laserCol();
			case SHIELD -> shieldColor.argb() | 0xFF000000;
			case DAMAGE -> damageColor.argb() | 0xFF000000;
		};
	}

	private static int flashed(int c, long now) {
		double k = Math.pow((Math.sin(now / (double) FLASH_MS) + 1) / 2, 6) * 0.75;
		return Colors.lerp(c, 0xFFFFFFFF, (float) k);
	}

	private @Nullable GlowCfg cfgOf(int id) {
		if (id == VoidgloomTracker.bodyId) return bossGlow;
		if (VoidgloomTracker.minis.contains(id)) return miniGlow;
		if (VoidgloomTracker.fanatics.contains(id)) return fanaticGlow;
		return null;
	}

	/** Colour of this entity's outline glow right now, 0 = not ours. Read by MobGlow every frame. */
	/** Which look (design) an entity's glow uses: each mob type has its own, beacons and heads the shared one. */
	private int lookOf(int id) {
		if (id == VoidgloomTracker.bodyId) return com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER_BOSS;
		if (VoidgloomTracker.minis.contains(id)) return com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER_MINI;
		if (VoidgloomTracker.fanatics.contains(id)) return com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER_VOIDLING;
		return com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER;
	}

	private int glowColorOf(int id) {
		if (!isEnabled()) return 0;
		long now = System.currentTimeMillis();
		if (beaconAlarm.get() && VoidgloomTracker.flying.containsKey(id)) return beaconColor.argb() | 0xFF000000;
		if (nukekubiHeads.get() && VoidgloomTracker.heads.contains(id)) return headColor.argb() | 0xFF000000;
		GlowCfg cfg = cfgOf(id);
		if (cfg == null) return 0;
		boolean boss = id == VoidgloomTracker.bodyId;
		if (!cfg.on.get() && !(boss && phaseGlow.get())) return 0;
		int base = boss && phaseGlow.get() ? phaseColor() : cfg.color.argb() | 0xFF000000;
		return cfg.flash.get() ? flashed(base, now) : base;
	}

	// ================================================================== tick

	@Override
	public void onTick(Minecraft mc) {
		VoidgloomTracker.debug = debug.get();
		VoidgloomTracker.beaconSoonAlert = beaconSoonAlert.get();
		VoidgloomTracker.beaconDownAlert = beaconDownAlert.get();
		VoidgloomTracker.tick(mc, termAlert.get(), termLead.get(), hitsOverAlert.get(), beaconAlarm.get(), nukekubiHeads.get(), laserWalls.get());
	}

	@Override
	protected void onDisable() {
		VoidgloomTracker.reset();
	}

	@Override
	public void onWorldChange() {
		VoidgloomTracker.reset();
		ServerClock.reset();
	}

	// ================================================================== world

	private static final float BODY_H = 2.9f, BODY_W = 0.6f;

	@Override
	public void onWorldRender(WorldCtx ctx) {
		if (VoidgloomTracker.boss == null || ctx.mc.level == null) return;
		long now = System.currentTimeMillis();
		Entity body = VoidgloomTracker.body();
		float pt = ctx.partialTick;

		if (beaconAlarm.get()) drawBeacons(ctx, now);
		if (nukekubiHeads.get()) drawHeads(ctx, pt);
		if (body != null) {
			double bx = Mth.lerp(pt, body.xo, body.getX()), by = Mth.lerp(pt, body.yo, body.getY()), bz = Mth.lerp(pt, body.zo, body.getZ());
			if (laserOverhead.get() && VoidgloomTracker.laser != null) drawLaserOverhead(ctx, bx, by, bz, now);
			if (laserShow.get() && VoidgloomTracker.laser != null) drawLaserShow(ctx, bx, by, bz, now);
			if (bossTracer.get()) drawTracer(ctx, bx, by, bz, now);
		}
		if (laserWalls.get() && !VoidgloomTracker.beams.isEmpty()) drawWalls(ctx, pt, now);

		// glint + aura on every glowing mob, nearest first
		float sweep = (1 - Mth.cos(now / 2400f * Mth.TWO_PI)) / 2;
		List<Integer> ids = new ArrayList<>();
		if (body != null) ids.add(VoidgloomTracker.bodyId);
		ids.addAll(VoidgloomTracker.minis);
		ids.addAll(VoidgloomTracker.fanatics);
		for (int id : ids) {
			GlowCfg cfg = cfgOf(id);
			Entity e = ctx.mc.level.getEntity(id);
			if (cfg == null || e == null || !cfg.on.get()) continue;
			double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()), ez = Mth.lerp(pt, e.zo, e.getZ());
			if (cfg.glint.get()) drawGlint(ctx, ex, ey, ez, now);
			if (cfg.aura.get()) {
				int c = glowColorOf(id);
				if (c == 0) c = cfg.color.argb() | 0xFF000000;
				ctx.ring(ctx.glow, ex, ey + 0.05, ez, 0.85f, 0.06f, 0, 0, c, Colors.withAlpha(c, 0f), 32);
				ctx.ring(ctx.glow, ex, ey + sweep * BODY_H, ez, 0.6f - sweep * 0.25f, 0.05f, 0, 0,
					Colors.withAlpha(c, 1 - sweep * 0.5f), Colors.withAlpha(c, 0f), 28);
			}
		}
	}

	// ---- glint: diagonal bands scrolling over a camera-facing sheet in front of the body

	private static final float[][] GLINT_LAYERS = {{40, 3000}, {100, 4873}};   // band normal angle, period ms (vanilla's two layers)
	private static final int GLINT_COLOR = 0xA680FF;

	/** Clips the line {p : p.n = d} (n at {@code angleDeg}) to the box [-w/2, w/2] x [0, h]; returns {u1,v1,u2,v2} or null. */
	static float @Nullable [] clipBand(float angleDeg, float d, float w, float h) {
		float t = angleDeg * Mth.DEG_TO_RAD, nx = Mth.cos(t), ny = Mth.sin(t);
		float px = nx * d, py = ny * d, dx = -ny, dy = nx;
		float lo = -Float.MAX_VALUE, hi = Float.MAX_VALUE;
		float[][] axes = {{px, dx, -w / 2, w / 2}, {py, dy, 0, h}};
		for (float[] a : axes) {
			if (Math.abs(a[1]) < 1e-6f) {
				if (a[0] < a[2] || a[0] > a[3]) return null;
				continue;
			}
			float s1 = (a[2] - a[0]) / a[1], s2 = (a[3] - a[0]) / a[1];
			lo = Math.max(lo, Math.min(s1, s2));
			hi = Math.min(hi, Math.max(s1, s2));
		}
		return hi - lo > 1e-5f ? new float[] {px + dx * lo, py + dy * lo, px + dx * hi, py + dy * hi} : null;
	}

	/** One soft band on a sheet: two quads fading from transparent edges to a bright centre line. */
	private static void band(WorldCtx ctx, GeoBatch b, float angle, float centre, float halfW, float w, float h, int peak,
							 Sheet s) {
		float[] mid = clipBand(angle, centre, w, h);
		if (mid == null) return;
		int clear = Colors.withAlpha(peak, 0f);
		for (int side = -1; side <= 1; side += 2) {
			float[] edge = clipBand(angle, centre + side * halfW, w, h);
			if (edge == null) continue;
			s.quad(ctx, b, edge[0], edge[1], edge[2], edge[3], mid[2], mid[3], mid[0], mid[1], clear, clear, peak, peak);
		}
	}

	/** A flat sheet in world space: point(u, v) = origin + U * u + V * v. */
	private record Sheet(double ox, double oy, double oz, double ux, double uy, double uz, double vx, double vy, double vz) {
		void quad(WorldCtx ctx, GeoBatch b, float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4, int c1, int c2, int c3, int c4) {
			b.quad(px(ctx, u1, v1), py(ctx, u1, v1), pz(ctx, u1, v1), px(ctx, u2, v2), py(ctx, u2, v2), pz(ctx, u2, v2),
				px(ctx, u3, v3), py(ctx, u3, v3), pz(ctx, u3, v3), px(ctx, u4, v4), py(ctx, u4, v4), pz(ctx, u4, v4), c1, c2, c3, c4);
		}

		float px(WorldCtx ctx, float u, float v) { return ctx.rx(ox + ux * u + vx * v); }
		float py(WorldCtx ctx, float u, float v) { return ctx.ry(oy + uy * u + vy * v); }
		float pz(WorldCtx ctx, float u, float v) { return ctx.rz(oz + uz * u + vz * v); }
	}

	private void drawGlint(WorldCtx ctx, double ex, double ey, double ez, long now) {
		double tx = ctx.cam.x - ex, tz = ctx.cam.z - ez, len = Math.max(1e-4, Math.hypot(tx, tz));
		tx /= len;
		tz /= len;
		Sheet s = new Sheet(ex + tx * 0.32, ey, ez + tz * 0.32, -tz, 0, tx, 0, 1, 0);
		int peak = Colors.withAlpha(GLINT_COLOR | 0xFF000000, 0.38f);
		for (float[] layer : GLINT_LAYERS) {
			float t = layer[0] * Mth.DEG_TO_RAD;
			float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
			for (float[] c : new float[][] {{-BODY_W / 2, 0}, {BODY_W / 2, 0}, {-BODY_W / 2, BODY_H}, {BODY_W / 2, BODY_H}}) {
				float d = c[0] * Mth.cos(t) + c[1] * Mth.sin(t);
				lo = Math.min(lo, d);
				hi = Math.max(hi, d);
			}
			lo -= 0.5f;
			hi += 0.5f;
			float phase = (now % (long) layer[1]) / layer[1];
			for (int k = 0; k < 2; k++) band(ctx, ctx.glow, layer[0], lo + (hi - lo) * ((phase + k / 2f) % 1f), 0.22f, BODY_W, BODY_H, peak, s);
		}
	}

	// ---- laser

	private void drawLaserOverhead(WorldCtx ctx, double x, double y, double z, long now) {
		double ticks = VoidgloomTracker.laserTicksLeft(now);
		float frac = (float) (ticks / VoidgloomTracker.LASER_TICKS);
		int c = laserCol();
		double top = y + BODY_H + 0.5;
		PixelFont.drawWorld(ctx, ctx.glow, String.format("%.1fs", ticks / ServerClock.tps()), x, top + 0.55, z, 0.055f, c);
		ctx.ring(ctx.glow, x, top, z, 0.25f + 0.75f * frac, 0.05f, 0, 0, c, Colors.withAlpha(c, 0f), 32);
	}

	private void drawLaserShow(WorldCtx ctx, double x, double y, double z, long now) {
		int c = laserCol(), hot = Colors.lerp(c, 0xFFFFFFFF, 0.5f);
		float[][] rings = {{0.6f, 1.1f}, {1.5f, 0.9f}, {2.4f, 0.7f}};
		for (int i = 0; i < rings.length; i++) {
			float breathe = 1 + 0.12f * Mth.sin(now / 260f + i * 2.1f);
			int rc = i % 2 == 1 ? hot : c;
			ctx.ring(ctx.glow, x, y + rings[i][0], z, rings[i][1] * breathe, 0.06f, 0, 0, rc, Colors.withAlpha(rc, 0f), 32);
		}
		long since = now - VoidgloomTracker.laser.startMs;
		if (since < 600) {
			float k = since / 600f;
			ctx.ring(ctx.glow, x, y + 0.1, z, 0.8f + k * 6, 0.12f, 0, 0, Colors.withAlpha(hot, 1 - k), Colors.withAlpha(hot, 0f), 48);
		}
	}

	private void drawWalls(WorldCtx ctx, float pt, long now) {
		int c = laserCol();
		float breathe = 0.85f + 0.15f * Mth.sin(now / 140f);
		int glintC = Colors.lerp(c, 0xFFFFFFFF, 0.6f);
		record Wall(double x, double z, double hx, double hz, double slope, double len, double[] range, double ang) {}
		List<Wall> walls = new ArrayList<>();
		for (VoidgloomTracker.Beam beam : VoidgloomTracker.beams) {
			Entity g = ctx.mc.level.getEntity(beam.guardianId());
			if (g == null) continue;
			double gx = Mth.lerp(pt, g.xo, g.getX()), gy = Mth.lerp(pt, g.yo, g.getY()) + (beam.elder() ? 1.0 : 0.43), gz = Mth.lerp(pt, g.zo, g.getZ());
			Entity t = beam.targetId() >= 0 ? ctx.mc.level.getEntity(beam.targetId()) : null;
			double dx, dy, dz, len;
			if (t != null) {
				dx = Mth.lerp(pt, t.xo, t.getX()) - gx;
				dy = Mth.lerp(pt, t.yo, t.getY()) + 0.5 - gy;
				dz = Mth.lerp(pt, t.zo, t.getZ()) - gz;
				len = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
				dx /= len;
				dy /= len;
				dz /= len;
			} else {
				float yaw = g.getYHeadRot() * Mth.DEG_TO_RAD, pitch = g.getXRot() * Mth.DEG_TO_RAD;
				dx = -Mth.sin(yaw) * Mth.cos(pitch);
				dy = -Mth.sin(pitch);
				dz = Mth.cos(yaw) * Mth.cos(pitch);
				len = 25;
			}
			double flat = Math.hypot(dx, dz);
			if (flat < 0.2) continue;
			double ang = Math.toDegrees(Math.atan2(dz, dx));
			Wall found = null;
			for (Wall w : walls) if (Math.abs(((ang - w.ang) % 360 + 540) % 360 - 180) < 12) { found = w; break; }
			if (found == null) {
				found = new Wall(gx, gz, dx / flat, dz / flat, dy / flat, len * flat, new double[] {gy, gy}, ang);
				walls.add(found);
			}
			found.range[0] = Math.min(found.range[0], gy);
			found.range[1] = Math.max(found.range[1], gy);
		}
		for (Wall w : walls) {
			float lo = (float) w.range[0] - 0.35f, h = (float) (w.range[1] - w.range[0]) + 0.7f, L = (float) w.len;
			// u along the wall (with its slope), v straight up
			Sheet s = new Sheet(w.x, lo, w.z, w.hx, w.slope, w.hz, 0, 1, 0);
			int fill = Colors.withAlpha(c, 0.22f * breathe);
			s.quad(ctx, ctx.glow, 0, 0, L, 0, L, h, 0, h, fill, fill, fill, fill);
			float[][] edges = {{16, 0.2f}, {9, 0.55f}, {3, 1f}};
			for (float[] e : edges) {
				int ec = e[0] == 3 ? 0xFFFFFFFF : Colors.withAlpha(c, Math.min(1f, e[1] * breathe));
				edge(ctx, s, 0, 0, L, 0, ec, e[0]);
				edge(ctx, s, 0, h, L, h, ec, e[0]);
				edge(ctx, s, L, 0, L, h, ec, e[0]);
			}
			// glint bands sweeping along the sheet, in sheet coords centred on its middle
			Sheet centred = new Sheet(w.x + w.hx * L / 2, lo + w.slope * L / 2, w.z + w.hz * L / 2, w.hx, w.slope, w.hz, 0, 1, 0);
			float[][] layers = {{55, 2600}, {125, 4100}};
			for (float[] layer : layers) {
				float t = layer[0] * Mth.DEG_TO_RAD;
				float a1 = -L / 2 * Mth.cos(t), a2 = L / 2 * Mth.cos(t), b1 = h * Mth.sin(t);
				float dlo = Math.min(Math.min(a1, a2), Math.min(a1 + b1, a2 + b1)) - 1, dhi = Math.max(Math.max(a1, a2), Math.max(a1 + b1, a2 + b1)) + 1;
				float phase = ((now + (long) (w.ang * 137)) % (long) layer[1]) / layer[1];
				for (int n = 0; n < 2; n++) band(ctx, ctx.glow, layer[0], dlo + (dhi - dlo) * ((phase + n / 2f) % 1f), 0.25f, L, h,
					Colors.withAlpha(glintC, 0.75f), centred);
			}
		}
	}

	private static void edge(WorldCtx ctx, Sheet s, float u1, float v1, float u2, float v2, int c, float width) {
		ctx.lines.line(s.px(ctx, u1, v1), s.py(ctx, u1, v1), s.pz(ctx, u1, v1), s.px(ctx, u2, v2), s.py(ctx, u2, v2), s.pz(ctx, u2, v2), c, c, width);
	}

	// ---- hazards

	private static final float BEACON_FUSE = 5;

	private void drawBeacons(WorldCtx ctx, long now) {
		int bc = beaconColor.argb() | 0xFF000000;
		for (var e : VoidgloomTracker.beacons.entrySet()) {
			BlockPos p = BlockPos.of(e.getKey());
			float age = (now - e.getValue()) / 1000f;
			float pulse = (Mth.sin(now / Math.max(60f, 260 - age * 40)) + 1) / 2;
			// the block glows (outline buffer = shared glow style), a beam goes up, the fuse counts down
			ctx.boxFill(ctx.outline, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1,
				com.blixu.blixuaddons.client.feature.GlowStyle.encode(bc, 0.45f + 0.55f * pulse, com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER), com.blixu.blixuaddons.client.feature.GlowStyle.encode(bc, 0.45f + 0.55f * pulse, com.blixu.blixuaddons.client.feature.GlowStyle.SLAYER));
			double cx = p.getX() + 0.5, cz = p.getZ() + 0.5, y0 = p.getY() + 1;
			beam(ctx, cx, y0, cz, 40, 0.25f, Colors.withAlpha(bc, 0.55f));
			int tint = Colors.lerp(0xFFFFFF55, bc, Math.min(1f, age / BEACON_FUSE));
			PixelFont.drawWorld(ctx, ctx.glow, String.format("%.1fs", Math.max(0, BEACON_FUSE - age)), cx, p.getY() + 1.7, cz, 0.05f, tint);
		}
		for (VoidgloomTracker.Flying f : VoidgloomTracker.flying.values()) {
			if (f.land == null) continue;
			List<double[]> pts = f.path;
			for (int i = 1; i < pts.size(); i += 2) {
				double[] a = pts.get(i - 1), b = pts.get(i);
				ctx.line(a[0], a[1], a[2], b[0], b[1], b[2], 0xCCFF2A2A, 0xCCFF2A2A, 3);
			}
			int[] l = f.land;
			ctx.boxLines(l[0], l[1], l[2], l[0] + 1, l[1] + 1, l[2] + 1, bc, bc, 3);
			double secs = Math.max(0, (f.landTick - ServerClock.tick(now)) / ServerClock.tps());
			PixelFont.drawWorld(ctx, ctx.glow, String.format("LANDS %.1fs", secs), l[0] + 0.5, l[1] + 1.5, l[2] + 0.5, 0.045f, 0xFFFFFF55);
		}
	}

	/** Two crossed vertical quads fading upwards: a light beam. */
	private static void beam(WorldCtx ctx, double x, double y, double z, float height, float halfW, int c) {
		int top = Colors.withAlpha(c, 0f);
		float bx = ctx.rx(x), by = ctx.ry(y), bz = ctx.rz(z);
		ctx.glow.quad(bx - halfW, by, bz, bx + halfW, by, bz, bx + halfW, by + height, bz, bx - halfW, by + height, bz, c, c, top, top);
		ctx.glow.quad(bx, by, bz - halfW, bx, by, bz + halfW, bx, by + height, bz + halfW, bx, by + height, bz - halfW, c, c, top, top);
	}

	private void drawHeads(WorldCtx ctx, float pt) {
		int hc = headColor.argb() | 0xFF000000;
		double sx = ctx.cam.x + ctx.forward.x, sy = ctx.cam.y + ctx.forward.y, sz = ctx.cam.z + ctx.forward.z;
		for (int id : VoidgloomTracker.heads) {
			Entity e = ctx.mc.level.getEntity(id);
			if (e == null) continue;
			double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()) + 1.7, z = Mth.lerp(pt, e.zo, e.getZ());
			ctx.xrayLine(sx, sy, sz, x, y, z, hc, hc, 2.5f);
			PixelFont.drawWorld(ctx, ctx.xrayGlow, "HEAD", x, y + 0.6, z, 0.05f, hc);
		}
	}

	private void drawTracer(WorldCtx ctx, double x, double y, double z, long now) {
		double dx = x - ctx.cam.x, dy = y + 1.5 - ctx.cam.y, dz = z - ctx.cam.z, len = Math.max(1e-4, Math.sqrt(dx * dx + dy * dy + dz * dz));
		if ((dx * ctx.forward.x + dy * ctx.forward.y + dz * ctx.forward.z) / len >= 0.7) return; // on screen: no tracer
		int c = glowColorOf(VoidgloomTracker.bodyId);
		if (c == 0) c = phaseColor();
		double sx = ctx.cam.x + ctx.forward.x, sy = ctx.cam.y + ctx.forward.y, sz = ctx.cam.z + ctx.forward.z;
		ctx.xrayLine(sx, sy, sz, x, y + 1.5, z, Colors.withAlpha(c, 0.35f), Colors.withAlpha(c, 0.35f), 8);
		ctx.xrayLine(sx, sy, sz, x, y + 1.5, z, c, c, 2.5f);
	}

	// ================================================================== HUD

	private static final int COL_BG = 0xEB0C0E14, COL_BORDER = 0x26FFFFFF, COL_TITLE = 0xFFC77DFF, COL_TEXT = 0xFFFFFFFF, COL_DIM = 0xFF8A8A95,
		COL_TRACK = 0x33FFFFFF, COL_HP = 0xFFFF5577, COL_HITS = 0xFFFF9EE5, COL_LASER = 0xFF7DE3FF, COL_SLOW = 0xFFFFCC44,
		COL_BEACON = 0xFFFF4040, COL_HELD = 0xFFFFAA33, ROSE = 0xFFFF5C8A, ORCHID = 0xFFE58CFF, MUTED = 0xFF9C93AD, GOLD = 0xFFFFAA00,
		PINK = 0xFFFF55FF, GRAY = 0xFFAAAAAA, DARK = 0xFF555555, RED = 0xFFFF5555, AQUA = 0xFF55FFFF, YELLOW = 0xFFFFFF55;

	/** What a status window shows: the live boss, or sample values for the menu preview. */
	public record St(boolean sample, Boss b, double tps, boolean slow, @Nullable Double lTicks, @Nullable Double lSecs, long bSince, double bSecs,
					 int hitsMax, double hpFrac, double @Nullable [] next, Phase phase) {}

	public static St state(long now) {
		boolean sample = VoidgloomTracker.boss == null;
		Boss b = VoidgloomTracker.boss;
		if (sample) {
			b = new Boss();
			b.tier = "IV";
			b.hp = 96.4e6;
			b.maxHp = 210e6;
			b.hits = 37;
		}
		double tps = sample ? 17.6 : ServerClock.tps();
		Double lTicks = sample ? Double.valueOf(90) : VoidgloomTracker.laser != null ? VoidgloomTracker.laserTicksLeft(now) : null;
		long bSince = sample ? now - 1800 : VoidgloomTracker.beacons.values().stream().mapToLong(Long::longValue).min().orElse(0);
		return new St(sample, b, tps, tps < 19.5, lTicks, lTicks != null ? lTicks / tps : null, bSince, bSince > 0 ? (now - bSince) / 1000.0 : 0,
			VoidgloomTracker.TIER_HITS.getOrDefault(b.tier, 100), b.maxHp > 0 ? Math.min(1, b.hp / b.maxHp) : 0, VoidgloomTracker.nextPhase(b),
			sample ? Phase.LASER : VoidgloomTracker.phase());
	}

	/** Size of the chosen status window, in GUI units at scale 1. */
	public float[] statusSize() {
		return switch (hudStyle.get()) {
			case CARD -> new float[] {150, 68};
			case TOOLTIP -> new float[] {140, 66};
			case STRIP -> new float[] {250, 18};
			case SCOREBOARD -> new float[] {110, 58};
			case FOCUS -> new float[] {170, 56};
			case PILLS -> new float[] {190, 56};
			case OFF -> new float[] {0, 0};
		};
	}

	@Override
	public void onHud(GuiGraphicsExtractor g, float dt) {
		long now = System.currentTimeMillis();
		float sw = g.guiWidth(), sh = g.guiHeight();
		if (VoidgloomTracker.boss != null && hudStyle.get() != HudStyleKind.OFF) {
			float s = hudScale.getF();
			g.pose().pushMatrix();
			g.pose().translate(sw * hudX.getF() / 100f, sh * hudY.getF() / 100f);
			g.pose().scale(s, s);
			drawStatus(g, now);
			g.pose().popMatrix();
		}
		drawAlerts(g, sw, sh, now);
	}

	@Override
	public List<com.blixu.blixuaddons.client.hud.HudElement> hudElements() {
		return List.of(
			new com.blixu.blixuaddons.client.hud.HudElement() {
				public String name() { return "Voidgloom status"; }
				public boolean shown() { return isEnabled() && hudStyle.get() != HudStyleKind.OFF; }
				public float[] box(float sw, float sh) {
					float[] s = statusSize();
					float k = hudScale.getF();
					return new float[] {sw * hudX.getF() / 100f, sh * hudY.getF() / 100f, s[0] * k, s[1] * k};
				}
				public void moveTo(float x, float y, float sw, float sh) {
					hudX.set((double) (x / sw * 100));
					hudY.set((double) (y / sh * 100));
				}
				public void resize(float steps) { hudScale.set(hudScale.get() + steps * 0.05); }
				public void drawSample(GuiGraphicsExtractor g, float sw, float sh) {
					if (VoidgloomTracker.boss != null) return; // the live one is already on screen
					float[] b = box(sw, sh);
					g.pose().pushMatrix();
					g.pose().translate(b[0], b[1]);
					g.pose().scale(hudScale.getF(), hudScale.getF());
					drawStatus(g, System.currentTimeMillis());
					g.pose().popMatrix();
				}
			},
			new com.blixu.blixuaddons.client.hud.HudElement() {
				public String name() { return "Remaining hits"; }
				public boolean shown() { return isEnabled() && bigHits.get(); }
				public float[] box(float sw, float sh) { return hitsBox(sw, sh); }
				public void moveTo(float x, float y, float sw, float sh) {
					float[] b = hitsBox(sw, sh);
					bigHitsX.set((double) ((x + b[2] / 2) / sw * 100));
					bigHitsY.set((double) ((y + 4.5f * hitsK()) / sh * 100));
				}
				public void resize(float steps) { bigHitsScale.set(bigHitsScale.get() + steps * 0.05); }
				public void drawSample(GuiGraphicsExtractor g, float sw, float sh) {
					if (VoidgloomTracker.boss != null && VoidgloomTracker.boss.hits != null) return;
					drawHits(g, sw, sh, 37, 100, 1f);
				}
			},
			new com.blixu.blixuaddons.client.hud.HudElement() {
				public String name() { return "Screen alerts (TERM NOW, beacon, hits over)"; }
				public boolean shown() { return isEnabled() && (termAlert.get() || hitsOverAlert.get() || beaconSoonAlert.get() || beaconDownAlert.get()); }
				public float[] box(float sw, float sh) { return alertBox(sw, sh); }
				public void moveTo(float x, float y, float sw, float sh) {
					float[] b = alertBox(sw, sh);
					alertX.set((double) ((x + b[2] / 2) / sw * 100));
					alertY.set((double) ((y + 4.5f * alertK()) / sh * 100));
				}
				public void resize(float steps) { alertScale.set(alertScale.get() + steps * 0.05); }
				public void drawSample(GuiGraphicsExtractor g, float sw, float sh) {
					if (System.currentTimeMillis() < VoidgloomTracker.alertUntil) return;
					drawAlertText(g, sw, sh, "TERM NOW", 0xFFFFFFFF, 1f, 0f);
				}
			});
	}

	// ---- screen text in Minecraft's font, white with a black outline

	private float alertK() { return 3.4f * alertScale.getF(); }
	private float hitsK() { return 3.8f * bigHitsScale.getF(); }

	private float[] alertBox(float sw, float sh) {
		float k = alertK(), w = com.blixu.blixuaddons.client.render.McText.width("HITS OVER!") * k;
		return new float[] {sw * alertX.getF() / 100f - w / 2, sh * alertY.getF() / 100f - 4.5f * k, w, 9.5f * k};
	}

	private float[] hitsBox(float sw, float sh) {
		float k = hitsK(), k2 = k * 0.34f;
		float w = Math.max(com.blixu.blixuaddons.client.render.McText.width("100") * k, com.blixu.blixuaddons.client.render.McText.width("HITS LEFT") * k2);
		return new float[] {sw * bigHitsX.getF() / 100f - w / 2, sh * bigHitsY.getF() / 100f - 4.5f * k, w, 9 * k + 3 + 9 * k2};
	}

	/** {@code pop}: 0..1 extra size right as the alert appears. */
	private void drawAlertText(GuiGraphicsExtractor g, float sw, float sh, String text, int color, float alpha, float pop) {
		float k = alertK() * (1 + 0.25f * pop);
		com.blixu.blixuaddons.client.render.McText.centered(g, text, sw * alertX.getF() / 100f, sh * alertY.getF() / 100f - 4.5f * k, k,
			Colors.withAlpha(color, alpha), Colors.withAlpha(0xFF000000, alpha));
	}

	private void drawHits(GuiGraphicsExtractor g, float sw, float sh, int hits, int max, float alpha) {
		float k = hitsK(), cx = sw * bigHitsX.getF() / 100f, cy = sh * bigHitsY.getF() / 100f;
		int c = Colors.lerp(0xFFFFFFFF, COL_HITS, Mth.clamp(hits / (float) max, 0f, 1f));
		com.blixu.blixuaddons.client.render.McText.centered(g, Integer.toString(hits), cx, cy - 4.5f * k, k, Colors.withAlpha(c, alpha), Colors.withAlpha(0xFF000000, alpha));
		com.blixu.blixuaddons.client.render.McText.centered(g, "HITS LEFT", cx, cy + 4.5f * k + 3, k * 0.34f, Colors.withAlpha(0xFFFFFFFF, 0.85f * alpha), Colors.withAlpha(0xFF000000, alpha));
	}

	/** Big screen text: TERM NOW / HITS OVER!, and the remaining hits while the shield is up. */
	public void drawAlerts(GuiGraphicsExtractor g, float sw, float sh, long now) {
		if (now < VoidgloomTracker.alertUntil) {
			float a = Mth.clamp((VoidgloomTracker.alertUntil - now) / 500f, 0f, 1f);
			// a short punch as it appears (alerts last at least ~1.5 s, so "age" is measured from the start)
			float age = (now - VoidgloomTracker.alertStart) / 1000f;
			float pop = Mth.clamp(1 - age / 0.18f, 0f, 1f);
			drawAlertText(g, sw, sh, VoidgloomTracker.alertText, VoidgloomTracker.alertColor, a, pop);
		} else if (termCountdown.get() && VoidgloomTracker.laser != null && VoidgloomTracker.termAt > now) {
			// the laser is running: count down to TERM NOW in the same spot, warming from white to amber
			float left = (VoidgloomTracker.termAt - now) / 1000f;
			int c = Colors.lerp(0xFFFFFFFF, 0xFFFFB020, Mth.clamp(1f - left / 3f, 0f, 1f));
			drawAlertText(g, sw, sh, String.format(java.util.Locale.ROOT, "%.1fs", left), c, 1f, 0f);
		}
		Boss b = VoidgloomTracker.boss;
		boolean test = now < testHitsUntil;
		if (bigHits.get() && ((b != null && b.hits != null) || test)) {
			int hits = test ? 37 : b.hits;
			int max = test ? 100 : VoidgloomTracker.TIER_HITS.getOrDefault(b.tier, 100);
			drawHits(g, sw, sh, hits, max, 1f);
		}
	}

	/** The chosen status window at (0,0) in the current pose. */
	public void drawStatus(GuiGraphicsExtractor g, long now) {
		St st = state(now);
		switch (hudStyle.get()) {
			case CARD -> drawCard(g, st, now);
			case TOOLTIP -> drawTooltip(g, st);
			case STRIP -> drawStrip(g, st);
			case SCOREBOARD -> drawScoreboard(g, st);
			case FOCUS -> drawFocus(g, st);
			case PILLS -> drawPills(g, st);
			case OFF -> { }
		}
	}

	private static String tier(Boss b) {
		return b.tier.isEmpty() ? "?" : b.tier;
	}

	private static void textRight(GuiGraphicsExtractor g, String s, float right, float y, int c, UiFont f) {
		Ui.text(g, s, right - Ui.width(s, f), y, c, f);
	}

	/** Dark notch at each shield threshold, faint one at each laser start (T4). */
	private static void notches(GuiGraphicsExtractor g, float x, float y, float w, float h, String tier) {
		if (tier.equals("IV")) for (double f : VoidgloomTracker.LASER_FRACS_IV) Ui.rect(g, Math.round(x + w * f), y, 1, h, 0, 0x66000000);
		for (double f : VoidgloomTracker.SHIELD_FRACS) Ui.rect(g, Math.round(x + w * f) - 1, y, 2, h, 0, 0xCC000000);
	}

	private static void bar(GuiGraphicsExtractor g, float x, float y, float w, float h, double frac, int c) {
		Ui.rect(g, x, y, w, h, h / 2, COL_TRACK);
		float fw = w * (float) Mth.clamp(frac, 0, 1);
		if (fw > 1) Ui.rect(g, x, y, fw, h, h / 2, c);
	}

	private void drawCard(GuiGraphicsExtractor g, St st, long now) {
		Boss b = st.b();
		float W = 150, H = 68, P = 7;
		Ui.shadow(g, 0, 2, W, H, 5, 8, 0x80000000);
		Ui.rect(g, 0, 0, W, H, 5, COL_BG);
		Ui.outline(g, 0, 0, W, H, 5, 0.75f, COL_BORDER);
		String phase = st.phase() == Phase.LASER ? "LASER" : st.phase() == Phase.SHIELD ? "HITS" : "DAMAGE";
		int phaseCol = st.phase() == Phase.LASER ? COL_LASER : st.phase() == Phase.SHIELD ? COL_HITS : COL_HP;
		Ui.text(g, "VOIDGLOOM " + tier(b), P, 6, COL_TITLE, UiFont.SEMIBOLD);
		String label = st.sample() ? "PREVIEW" : phase.equals("DAMAGE") && st.next() != null ? VoidgloomTracker.fmt(st.next()[1]) + " TO SHIELD" : phase;
		textRight(g, label, W - P, 7, st.sample() ? COL_DIM : phaseCol, UiFont.SMALL);
		// health bar with the numbers on it
		Ui.rect(g, P, 18, W - 2 * P, 10, 3, COL_TRACK);
		if (st.hpFrac() > 0) Ui.rect(g, P, 18, Math.max(6, (W - 2 * P) * (float) st.hpFrac()), 10, 3, COL_HP);
		notches(g, P, 18, W - 2 * P, 10, b.tier);
		Ui.textCentered(g, VoidgloomTracker.fmt(b.hp) + " / " + (b.maxHp > 0 ? VoidgloomTracker.fmt(b.maxHp) : "?") + "  (" + Math.round(st.hpFrac() * 100) + "%)",
			W / 2, 19.5f, COL_TEXT, UiFont.SMALL);
		row(g, 32, W, P, "Hits", b.hits != null ? b.hits + " / " + st.hitsMax() : "-", b.hits != null ? COL_HITS : COL_DIM,
			b.hits != null ? b.hits / (double) st.hitsMax() : 0, COL_HITS);
		boolean slow = st.slow();
		row(g, 44, W, P, "Laser" + (slow ? "  " + String.format("%.1f", st.tps()) + " TPS" : ""), st.lSecs() != null ? String.format("%.1fs", st.lSecs()) : "-",
			st.lTicks() == null ? COL_DIM : slow ? COL_SLOW : COL_LASER, st.lTicks() != null ? st.lTicks() / VoidgloomTracker.LASER_TICKS : 0, slow ? COL_SLOW : COL_LASER);
		String bText = st.bSince() > 0 ? String.format("PLACED %.1fs", st.bSecs()) : b.held ? "HELD" : "none";
		int bCol = st.bSince() > 0 ? COL_BEACON : b.held ? COL_HELD : COL_DIM;
		Ui.text(g, "Beacon", P, 56, COL_DIM, UiFont.SMALL);
		if (st.bSince() > 0 || b.held) {
			float tw = Ui.width(bText, UiFont.SMALL) + 8;
			float pulse = st.bSince() > 0 ? 0.55f + 0.45f * Math.abs(Mth.sin(now / 180f)) : 1f;
			Ui.rect(g, W - P - tw, 54, tw, 10, 3, Colors.withAlpha((bCol & 0xFFFFFF) | 0x40000000, pulse));
			Ui.textCentered(g, bText, W - P - tw / 2, 55.5f, bCol, UiFont.SMALL);
		} else {
			textRight(g, bText, W - P, 56, bCol, UiFont.SMALL);
		}
	}

	private static void row(GuiGraphicsExtractor g, float y, float W, float P, String name, String value, int valueCol, double frac, int col) {
		Ui.text(g, name, P, y, COL_DIM, UiFont.SMALL);
		textRight(g, value, W - P, y, valueCol, UiFont.SMALL);
		bar(g, P, y + 8, W - 2 * P, 1.5f, frac, col);
	}

	/** Vanilla item tooltip look: near-black purple fill, notched corners, blue-to-purple border. */
	private static void drawTooltip(GuiGraphicsExtractor g, St st) {
		Boss b = st.b();
		float W = 140, H = 66, x = 6;
		Ui.rect(g, 1.5f, 0, W - 3, H, 0, 0xF0100010);
		Ui.rect(g, 0, 1.5f, W, H - 3, 0, 0xF0100010);
		Ui.gradientV(g, 1.5f, 1.5f, 1.2f, H - 3, 0, 0x505000FF, 0x5028007F);
		Ui.gradientV(g, W - 2.7f, 1.5f, 1.2f, H - 3, 0, 0x505000FF, 0x5028007F);
		Ui.rect(g, 1.5f, 1.5f, W - 3, 1.2f, 0, 0x505000FF);
		Ui.rect(g, 1.5f, H - 2.7f, W - 3, 1.2f, 0, 0x5028007F);
		Ui.text(g, "Voidgloom Seraph " + tier(b), x, 5, PINK, UiFont.SEMIBOLD);
		Ui.rect(g, x, 15, W - 12, 1.5f, 0, 0xFF000000);
		Ui.rect(g, x, 15, (W - 12) * (float) st.hpFrac(), 1.5f, 0, durability(st.hpFrac()));
		notches(g, x, 14, W - 12, 3.5f, b.tier);
		float y = 20, line = 9;
		lore(g, x, y, new Object[][] {{"Health: ", GRAY}, {VoidgloomTracker.fmt(b.hp) + "/" + (b.maxHp > 0 ? VoidgloomTracker.fmt(b.maxHp) : "?") + "❤", RED}, {" (" + Math.round(st.hpFrac() * 100) + "%)", DARK}});
		lore(g, x, y += line, st.next() != null ? new Object[][] {{"Next shield: ", GRAY}, {VoidgloomTracker.fmt(st.next()[1]) + " to go", GOLD}} : new Object[][] {{"Next shield: ", GRAY}, {"none left", DARK}});
		lore(g, x, y += line, b.hits != null ? new Object[][] {{"Shield: ", GRAY}, {b.hits + " hits", PINK}, {" of " + st.hitsMax(), DARK}} : new Object[][] {{"Shield: ", GRAY}, {"broken", DARK}});
		lore(g, x, y += line, st.lSecs() != null
			? (st.slow() ? new Object[][] {{"Laser: ", GRAY}, {String.format("%.1fs left", st.lSecs()), AQUA}, {String.format(" %.1f tps", st.tps()), YELLOW}} : new Object[][] {{"Laser: ", GRAY}, {String.format("%.1fs left", st.lSecs()), AQUA}})
			: new Object[][] {{"Laser: ", GRAY}, {"idle", DARK}});
		lore(g, x, y += line, st.bSince() > 0 ? new Object[][] {{"Beacon: ", GRAY}, {String.format("placed %.1fs ago", st.bSecs()), RED}}
			: b.held ? new Object[][] {{"Beacon: ", GRAY}, {"in its hand", GOLD}} : new Object[][] {{"Beacon: ", GRAY}, {"none", DARK}});
	}

	private static void lore(GuiGraphicsExtractor g, float x, float y, Object[][] parts) {
		for (Object[] p : parts) {
			String t = (String) p[0];
			Ui.text(g, t, x, y, (Integer) p[1], UiFont.SMALL);
			x += Ui.width(t, UiFont.SMALL);
		}
	}

	/** Vanilla durability colour: green (full) to red (empty). */
	private static int durability(double frac) {
		float h = (float) Mth.clamp(frac, 0, 1) / 3f;
		return Colors.fromHsv(h, 1f, 1f, 255);
	}

	private static void drawStrip(GuiGraphicsExtractor g, St st) {
		Boss b = st.b();
		float W = 250;
		List<Object[]> parts = new ArrayList<>();
		parts.add(new Object[] {"Voidgloom " + tier(b), 0xFFFFFFFF});
		parts.add(new Object[] {VoidgloomTracker.fmt(b.hp), 0xFFFFFFFF});
		parts.add(new Object[] {Math.round(st.hpFrac() * 100) + "%", 0x99FFFFFF});
		if (st.next() != null && st.phase() == Phase.DAMAGE) parts.add(new Object[] {VoidgloomTracker.fmt(st.next()[1]) + " to shield", GOLD});
		if (b.hits != null) parts.add(new Object[] {b.hits + " hits", COL_HITS});
		if (st.lSecs() != null) parts.add(new Object[] {String.format("laser %.1fs", st.lSecs()), st.slow() ? YELLOW : COL_LASER});
		if (st.bSince() > 0) parts.add(new Object[] {String.format("beacon %.1fs", st.bSecs()), 0xFFFF4D4D});
		else if (b.held) parts.add(new Object[] {"beacon in hand", GOLD});
		float x = 0;
		for (int i = 0; i < parts.size(); i++) {
			UiFont f = i == 0 ? UiFont.SEMIBOLD : UiFont.SMALL;
			String t = (String) parts.get(i)[0];
			Ui.text(g, t, x, i == 0 ? 1 : 2, (Integer) parts.get(i)[1], f);
			x += Ui.width(t, f) + (i == 1 ? 3 : 8);
		}
		Ui.rect(g, 0, 12, W, 1.2f, 0, 0x40FFFFFF);
		Ui.rect(g, 0, 12, W * (float) st.hpFrac(), 1.2f, 0, 0xFFE040FB);
		notches(g, 0, 11, W, 3.5f, b.tier);
		if (st.lTicks() != null) Ui.rect(g, 0, 14.5f, W * (float) (st.lTicks() / VoidgloomTracker.LASER_TICKS), 0.8f, 0, st.slow() ? YELLOW : COL_LASER);
	}

	private static void drawScoreboard(GuiGraphicsExtractor g, St st) {
		Boss b = st.b();
		float W = 110, R = 8;
		String[][] rows = {
			{"Health", VoidgloomTracker.fmt(b.hp) + " " + Math.round(st.hpFrac() * 100) + "%"},
			{"Next shield", st.next() != null ? VoidgloomTracker.fmt(st.next()[1]) : "-"},
			{"Shield", b.hits != null ? "" + b.hits : "-"},
			{"Laser", st.lSecs() != null ? String.format("%.1fs", st.lSecs()) : "-"},
			{"Beacon", st.bSince() > 0 ? String.format("%.1fs", st.bSecs()) : b.held ? "held" : "-"},
		};
		Ui.rect(g, 0, 0, W, R + 1, 0, 0x66000000);
		Ui.rect(g, 0, R + 1, W, R * 6 + 1, 0, 0x4C000000);
		Ui.textCentered(g, "Voidgloom " + tier(b), W / 2, 1.5f, YELLOW, UiFont.SMALL);
		for (int i = 0; i < rows.length; i++) {
			float y = R * (i + 1) + 2.5f;
			boolean live = !rows[i][1].equals("-");
			Ui.text(g, rows[i][0], 2, y, live ? 0xFFFFFFFF : GRAY, UiFont.SMALL);
			textRight(g, rows[i][1], W - 2, y, live ? RED : DARK, UiFont.SMALL);
		}
		Ui.text(g, st.slow() ? String.format("server at %.1f tps", st.tps()) : "server at full speed", 2, R * 6 + 2.5f, st.slow() ? YELLOW : GOLD, UiFont.SMALL);
	}

	private static void drawFocus(GuiGraphicsExtractor g, St st) {
		Boss b = st.b();
		float H = 56;
		Ui.rect(g, 0, 0, 100, H, 0, 0xE00B0812);
		Ui.gradientH(g, 100, 0, 70, H, 0, 0xE00B0812, 0x000B0812);
		String[] shield = b.hits != null ? new String[] {"Shield", b.hits + " hits"} : new String[] {"Shield", "-"};
		int shieldC = b.hits != null ? ORCHID : MUTED;
		String[] laserRow = st.lSecs() != null ? new String[] {"Laser", String.format("%.1fs", st.lSecs()) + (st.slow() ? String.format(" %.1f tps", st.tps()) : "")} : new String[] {"Laser", "-"};
		int laserC = st.lSecs() != null ? st.slow() ? YELLOW : COL_LASER : MUTED;
		String[] health = {"Health", VoidgloomTracker.fmt(b.hp) + "  " + Math.round(st.hpFrac() * 100) + "%"};
		String[] beacon = st.bSince() > 0 ? new String[] {"Beacon", String.format("%.1fs", st.bSecs())} : b.held ? new String[] {"Beacon", "in hand"} : new String[] {"Beacon", "-"};
		int beaconC = st.bSince() > 0 ? 0xFFFF4D4D : b.held ? GOLD : MUTED;
		String big, sub;
		double frac;
		int col;
		Object[][] rows;
		if (st.phase() == Phase.LASER && st.lSecs() != null) {
			big = String.format("%.1f", st.lSecs()); sub = "seconds of laser"; frac = st.lTicks() / VoidgloomTracker.LASER_TICKS; col = st.slow() ? YELLOW : COL_LASER;
			rows = new Object[][] {{health, ROSE}, {shield, shieldC}, {beacon, beaconC}};
		} else if (st.phase() == Phase.SHIELD) {
			big = "" + b.hits; sub = "hits to break"; frac = b.hits / (double) st.hitsMax(); col = ORCHID;
			rows = new Object[][] {{health, ROSE}, {laserRow, laserC}, {beacon, beaconC}};
		} else {
			big = Math.round(st.hpFrac() * 100) + "%"; sub = st.next() != null ? VoidgloomTracker.fmt(st.next()[1]) + " to next shield" : VoidgloomTracker.fmt(b.hp) + " left";
			frac = st.hpFrac(); col = ROSE;
			rows = new Object[][] {{shield, shieldC}, {laserRow, laserC}, {beacon, beaconC}};
		}
		Ui.text(g, "Voidgloom " + tier(b), 7, 5, MUTED, UiFont.SMALL);
		PixelFont.draw(g, big, 7, 14, 2.6f, col);
		Ui.text(g, sub, 7, 36, col, UiFont.SMALL);
		Ui.rect(g, 7, 47, 60, 2, 1, 0x2EFFFFFF);
		if (frac > 0) Ui.rect(g, 7, 47, Math.max(2, 60 * (float) Math.min(1, frac)), 2, 1, col);
		if (frac == st.hpFrac()) notches(g, 7, 46, 60, 4, b.tier);
		Ui.rect(g, 76, 8, 0.75f, 42, 0, 0x24FFFFFF);
		for (int i = 0; i < rows.length; i++) {
			String[] r = (String[]) rows[i][0];
			Ui.text(g, r[0], 84, 12 + i * 12, MUTED, UiFont.SMALL);
			Ui.text(g, r[1], 114, 12 + i * 12, (Integer) rows[i][1], UiFont.SMALL);
		}
	}

	private void drawPills(GuiGraphicsExtractor g, St st) {
		Boss b = st.b();
		int soft = 0xBFFFFFFF;
		float y = 0, PH = 12, gap = 2.5f;
		List<Object[]> hp = new ArrayList<>();
		hp.add(new Object[] {"Voidgloom " + tier(b), 0xFFFFFFFF});
		hp.add(new Object[] {VoidgloomTracker.fmt(b.hp), soft});
		if (st.next() != null && st.phase() == Phase.DAMAGE) hp.add(new Object[] {VoidgloomTracker.fmt(st.next()[1]) + " to shield", GOLD});
		pill(g, y, ROSE, hp, st.hpFrac(), ROSE, b.tier);
		y += PH + gap;
		if (st.lSecs() != null) {
			int c = st.slow() ? YELLOW : COL_LASER;
			pill(g, y, c, List.of(new Object[] {String.format("%.1fs", st.lSecs()), c}, new Object[] {st.slow() ? String.format("of laser at %.1f tps", st.tps()) : "of laser", soft}),
				st.lTicks() / VoidgloomTracker.LASER_TICKS, c, null);
			y += PH + gap;
		}
		if (b.hits != null) {
			pill(g, y, ORCHID, List.of(new Object[] {"" + b.hits, ORCHID}, new Object[] {"hits to break", soft}), -1, 0, null);
			y += PH + gap;
		}
		if (st.bSince() > 0) pill(g, y, 0xFFFF4D4D, List.of(new Object[] {"Beacon down", 0xFFFF4D4D}, new Object[] {String.format("%.1fs", st.bSecs()), soft}), -1, 0, null);
		else if (b.held) pill(g, y, GOLD, List.<Object[]>of(new Object[] {"Beacon in its hand", GOLD}), -1, 0, null);
	}

	private void pill(GuiGraphicsExtractor g, float y, int dot, List<Object[]> parts, double barFrac, int barCol, @Nullable String tier) {
		float PH = 12, BAR = 30, W = 190;
		float w = 11;
		for (Object[] p : parts) w += Ui.width((String) p[0], UiFont.SMALL) + 3;
		if (barFrac >= 0) w += BAR + 2;
		w += 4;
		float x0 = pillAnchor.get() == Anchor.RIGHT ? W - w : pillAnchor.get() == Anchor.CENTER ? (W - w) / 2 : 0;
		Ui.rect(g, x0, y, w, PH, PH / 2, 0xE0120D1C);
		Ui.outline(g, x0, y, w, PH, PH / 2, 0.6f, 0x1AFFFFFF);
		Ui.circle(g, x0 + 6, y + PH / 2, 1.8f, dot);
		float x = x0 + 11;
		for (Object[] p : parts) {
			String t = (String) p[0];
			Ui.text(g, t, x, y + 2.5f, (Integer) p[1], UiFont.SMALL);
			x += Ui.width(t, UiFont.SMALL) + 3;
		}
		if (barFrac >= 0) {
			Ui.rect(g, x + 1, y + 5.2f, BAR, 1.8f, 0.9f, 0x2EFFFFFF);
			if (barFrac > 0) Ui.rect(g, x + 1, y + 5.2f, Math.max(2, BAR * (float) Math.min(1, barFrac)), 1.8f, 0.9f, barCol);
			if (tier != null) notches(g, x + 1, y + 4.2f, BAR, 3.8f, tier);
		}
	}
}
