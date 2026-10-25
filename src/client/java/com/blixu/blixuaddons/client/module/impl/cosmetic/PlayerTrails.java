package com.blixu.blixuaddons.client.module.impl.cosmetic;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.render.ParticlePool;
import com.blixu.blixuaddons.client.render.Shape;
import com.blixu.blixuaddons.client.render.Shapes;
import com.blixu.blixuaddons.client.render.WorldCtx;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * A fading trail behind your own character. History is a fixed ring of samples; a jump of
 * more than a few blocks between samples (teleport, respawn, world change) restarts it so
 * no trail is drawn across the gap. Movement itself is untouched.
 */
public class PlayerTrails extends Module {
	public enum Style {
		RIBBON("Ribbon"), PARTICLES("Particles"), GHOST("Ghost"), LINE("Line"), ENERGY("Energy"), SPARKS("Sparks"),
		GEOMETRIC("Geometric"), CUSTOM("Custom");
		public final String label;
		Style(String l) { label = l; }
	}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.RIBBON, s -> s.label));
	public final BoolSetting cRibbon = add(new BoolSetting("c_ribbon", "Custom: ribbon", true).visibleWhen(this::custom));
	public final BoolSetting cParticles = add(new BoolSetting("c_particles", "Custom: particles", true).visibleWhen(this::custom));
	public final BoolSetting cLine = add(new BoolSetting("c_line", "Custom: line", false).visibleWhen(this::custom));
	public final BoolSetting cGhost = add(new BoolSetting("c_ghost", "Custom: ghosts", false).visibleWhen(this::custom));
	public final BoolSetting cSparks = add(new BoolSetting("c_sparks", "Custom: sparks", false).visibleWhen(this::custom));
	public final EnumSetting<Shape> geoShape = add(new EnumSetting<Shape>("geo_shape", "Geometric shape", Shape.DIAMOND, s -> s.label)
		.visibleWhen(() -> style.get() == Style.GEOMETRIC || custom()));

	public final ColorSetting color = add(new ColorSetting("color", "Trail colour", 0x8B5CF6));
	public final ColorSetting color2 = add(new ColorSetting("color2", "Gradient colour", 0x22D3EE));
	public final NumberSetting length = add(new NumberSetting("length", "Trail length (samples)", 40, 4, 120, 1));
	public final NumberSetting lifetime = add(new NumberSetting("lifetime", "Lifetime (s)", 1.2, 0.2, 5, 0.05));
	public final NumberSetting width = add(new NumberSetting("width", "Width", 0.25, 0.02, 1, 0.01));
	public final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 0.8, 0.05, 1, 0.05));
	public final NumberSetting heightOffset = add(new NumberSetting("height", "Height on body", 0.9, 0, 2, 0.05));
	public final NumberSetting particleCount = add(new NumberSetting("particle_count", "Particles per sample", 2, 1, 10, 1));
	public final NumberSetting particleSize = add(new NumberSetting("particle_size", "Particle size", 0.08, 0.01, 0.4, 0.01));
	public final NumberSetting fadeIn = add(new NumberSetting("fade_in", "Fade in (s)", 0.08, 0, 1, 0.01));
	public final EnumSetting<ParticlePool.Fade> fadeOut = add(new EnumSetting<>("fade_out", "Fade out", ParticlePool.Fade.SMOOTH, f -> f.label));
	public final NumberSetting interval = add(new NumberSetting("interval", "Emission interval (s)", 0.03, 0.01, 0.3, 0.01));
	public final NumberSetting animSpeed = add(new NumberSetting("anim_speed", "Animation speed", 1, 0.1, 4, 0.05));
	public final NumberSetting maxParticles = add(new NumberSetting("max_particles", "Max particles", 600, 50, 3000, 10));
	public final BoolSetting firstPerson = add(new BoolSetting("first_person", "Visible in first person", false));
	public final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Visible in third person", true));
	public final NumberSetting walking = add(new NumberSetting("walking", "Walking strength", 1, 0, 2, 0.05));
	public final NumberSetting sprinting = add(new NumberSetting("sprinting", "Sprinting strength", 1.4, 0, 2, 0.05));
	public final NumberSetting jumping = add(new NumberSetting("jumping", "Jumping strength", 1.2, 0, 2, 0.05));
	public final NumberSetting falling = add(new NumberSetting("falling", "Falling strength", 0.8, 0, 2, 0.05));
	public final BoolSetting bursts = add(new BoolSetting("bursts", "Bursts on jump / landing", true));

	private static final int MAX = 128;
	private final double[] hx = new double[MAX], hy = new double[MAX], hz = new double[MAX];
	private final float[] hAge = new float[MAX], hScale = new float[MAX], hYaw = new float[MAX];
	private int head;
	private int size;
	private float emitTimer;
	private boolean wasOnGround = true;
	private final ParticlePool pool = new ParticlePool(3000, ParticlePool.Kind.COSMETIC);
	private final Random random = new Random();

	public PlayerTrails() {
		super("player_trails", "Player Trails",
			"Ribbons, particles, ghosts, sparks or shapes trailing behind you.",
			Category.COSMETICS, "trail", false);
	}

	private boolean custom() {
		return style.get() == Style.CUSTOM;
	}

	private boolean has(Style s) {
		if (style.get() != Style.CUSTOM) return style.get() == s;
		return switch (s) {
			case RIBBON -> cRibbon.get();
			case PARTICLES -> cParticles.get();
			case LINE -> cLine.get();
			case GHOST -> cGhost.get();
			case SPARKS -> cSparks.get();
			case GEOMETRIC -> false;
			default -> false;
		};
	}

	private float stateScale(LocalPlayer p) {
		if (!p.onGround()) return p.getDeltaMovement().y > 0 ? jumping.getF() : falling.getF();
		if (p.isSprinting()) return sprinting.getF();
		return walking.getF();
	}

	@Override
	public void onWorldRender(WorldCtx ctx) {
		Minecraft mc = ctx.mc;
		LocalPlayer p = mc.player;
		if (p == null) return;
		float dt = ctx.dt;
		for (int i = 0; i < size; i++) hAge[idx(i)] += dt;
		while (size > 0 && hAge[idx(0)] > lifetime.getF()) size--;

		double px = Mth.lerp(ctx.partialTick, p.xo, p.getX());
		double py = Mth.lerp(ctx.partialTick, p.yo, p.getY()) + heightOffset.get();
		double pz = Mth.lerp(ctx.partialTick, p.zo, p.getZ());
		boolean moving = p.getDeltaMovement().horizontalDistanceSqr() > 1e-4 || !p.onGround();

		emitTimer += dt;
		if (emitTimer >= interval.getF()) {
			emitTimer = 0;
			if (size > 0) {
				int last = idx(size - 1);
				double d2 = (hx[last] - px) * (hx[last] - px) + (hy[last] - py) * (hy[last] - py) + (hz[last] - pz) * (hz[last] - pz);
				if (d2 > 16) size = 0; // teleport / respawn: don't bridge the gap
			}
			if (moving) {
				float scale = stateScale(p);
				push(px, py, pz, scale, PlayerSpin.bodyYaw(Mth.lerp(ctx.partialTick, p.yBodyRotO, p.yBodyRot)));
				if (scale > 0) emitParticles(px, py, pz, scale);
			}
		}
		if (bursts.get()) {
			if (wasOnGround && !p.onGround() && p.getDeltaMovement().y > 0.1) burst(px, py - heightOffset.get(), pz, 10);
			if (!wasOnGround && p.onGround()) burst(px, py - heightOffset.get(), pz, 14);
		}
		wasOnGround = p.onGround();

		pool.fade = fadeOut.get();
		pool.fadeIn = fadeIn.getF();
		pool.update(dt);

		if (com.blixu.blixuaddons.client.feature.PlayerEditorCamera.active()) {
			drawDemo(ctx, ctx.time, px, py + 2.7, pz, 0.45f);
		}
		boolean firstPersonView = mc.options.getCameraType().isFirstPerson();
		if (firstPersonView ? !firstPerson.get() : !thirdPerson.get()) return;
		drawHistory(ctx);
		pool.draw(ctx, opacity.getF());
	}

	private int idx(int i) {
		return Math.floorMod(head - size + i, MAX);
	}

	private void push(double x, double y, double z, float scale, float yaw) {
		hx[head] = x;
		hy[head] = y;
		hz[head] = z;
		hAge[head] = 0;
		hScale[head] = scale;
		hYaw[head] = yaw;
		head = (head + 1) % MAX;
		size = Math.min(size + 1, Math.min(MAX, length.getI()));
	}

	private void emitParticles(double x, double y, double z, float scale) {
		boolean particles = has(Style.PARTICLES) || style.get() == Style.ENERGY;
		boolean sparks = has(Style.SPARKS);
		boolean geo = style.get() == Style.GEOMETRIC;
		int n = Math.max(1, Math.round(particleCount.getF() * scale));
		int limit = maxParticles.getI();
		for (int i = 0; i < n; i++) {
			float jx = (random.nextFloat() - 0.5f) * width.getF(), jy = (random.nextFloat() - 0.5f) * width.getF(), jz = (random.nextFloat() - 0.5f) * width.getF();
			int c = Colors.lerp(color.argb(), color2.argb(), random.nextFloat());
			if (particles) {
				pool.spawn(x + jx, y + jy, z + jz, 0, 0.15f, 0, lifetime.getF() * 0.8f, particleSize.getF() * scale, particleSize.getF() * 0.2f,
					c, Colors.withAlpha(color2.argb(), 0.5f), style.get() == Style.ENERGY ? Shape.SOFT : Shape.CIRCLE, true, 0, 0.5f, 0, 0, limit);
			}
			if (sparks) {
				float a = random.nextFloat() * Mth.TWO_PI;
				float sp = 1.2f + random.nextFloat() * 1.5f;
				pool.spawn(x + jx, y + jy, z + jz, Mth.cos(a) * sp, random.nextFloat() * 1.5f, Mth.sin(a) * sp, 0.4f, particleSize.getF() * 1.2f, 0.01f,
					0xFFFFFFFF, c, Shape.SPARK, true, 6f, 1.5f, 0, 0, limit);
			}
			if (geo) {
				pool.spawn(x + jx, y + jy, z + jz, 0, 0.05f, 0, lifetime.getF(), particleSize.getF() * 1.4f * scale, particleSize.getF() * 0.4f,
					c, color2.argb(), geoShape.get(), false, 0, 0.2f, random.nextFloat() * Mth.TWO_PI, (random.nextFloat() - 0.5f) * 4 * animSpeed.getF(), limit);
			}
		}
	}

	private void burst(double x, double y, double z, int n) {
		for (int i = 0; i < n; i++) {
			float a = Mth.TWO_PI * i / n;
			pool.spawn(x, y + 0.05, z, Mth.cos(a) * 2.2f, 0.4f, Mth.sin(a) * 2.2f, 0.45f, particleSize.getF() * 1.5f, 0.02f,
				color.argb(), color2.argb(), Shape.SOFT, true, 1f, 3f, 0, 0, maxParticles.getI());
		}
	}

	private void drawHistory(WorldCtx ctx) {
		if (size < 2) return;
		float life = lifetime.getF();
		float op = opacity.getF();
		float w = width.getF();
		boolean ribbon = has(Style.RIBBON);
		boolean line = has(Style.LINE);
		boolean energy = style.get() == Style.ENERGY;
		boolean ghost = has(Style.GHOST);
		float t = ctx.time * animSpeed.getF();
		for (int i = 1; i < size; i++) {
			int a = idx(i - 1), b = idx(i);
			float fa = (i - 1) / (float) size, fb = i / (float) size; // 0 = oldest
			float la = 1f - hAge[a] / life, lb = 1f - hAge[b] / life;
			int ca = Colors.withAlpha(Colors.lerp(color2.argb(fa), color.argb(fa), fa), op * fa * la);
			int cb = Colors.withAlpha(Colors.lerp(color2.argb(fb), color.argb(fb), fb), op * fb * lb);
			if (ribbon) {
				ctx.ribbon(ctx.glow, hx[a], hy[a], hz[a], hx[b], hy[b], hz[b], w * 0.5f * fa * hScale[a], w * 0.5f * fb * hScale[b], ca, cb);
			}
			if (line) ctx.line(hx[a], hy[a], hz[a], hx[b], hy[b], hz[b], ca, cb, 2.5f);
			if (energy) {
				// Bright pulses flowing along the trail
				float pulse = 0.5f + 0.5f * Mth.sin(fb * 18f - t * 8f);
				int c = Colors.withAlpha(Colors.lerp(color.argb(), 0xFFFFFFFF, pulse * 0.5f), op * fb * lb * (0.3f + 0.7f * pulse));
				ctx.ribbon(ctx.glow, hx[a], hy[a], hz[a], hx[b], hy[b], hz[b], w * 0.3f * fa, w * 0.3f * fb, c, c);
			}
		}
		if (ghost) {
			// Simple humanoid silhouettes (head, body, legs) at a few past positions.
			int step = Math.max(2, size / 5);
			for (int i = step; i < size; i += step) {
				int k = idx(i);
				float f = i / (float) size * (1f - hAge[k] / life);
				ghost(ctx, hx[k], hy[k] - heightOffset.get(), hz[k], hYaw[k], Colors.withAlpha(color.argb(), op * 0.35f * f));
			}
		}
	}

	private void ghost(WorldCtx ctx, double x, double y, double z, float yawDeg, int c) {
		float yaw = -yawDeg * Mth.DEG_TO_RAD;
		float cs = Mth.cos(yaw), sn = Mth.sin(yaw);
		float px = ctx.rx(x), py = ctx.ry(y), pz = ctx.rz(z);
		// local axes: right (cs, 0, -sn), up, forward (sn, 0, cs)
		Shapes.cube(ctx.glow, px, py + 1.62f, pz, cs * 0.25f, 0, -sn * 0.25f, 0, 0.25f, 0, sn * 0.25f, 0, cs * 0.25f, 1f, c);
		Shapes.cube(ctx.glow, px, py + 1.05f, pz, cs * 0.25f, 0, -sn * 0.25f, 0, 0.36f, 0, sn * 0.13f, 0, cs * 0.13f, 1f, c);
		Shapes.cube(ctx.glow, px, py + 0.35f, pz, cs * 0.24f, 0, -sn * 0.24f, 0, 0.35f, 0, sn * 0.12f, 0, cs * 0.12f, 1f, c);
	}

	@Override
	protected void onDisable() {
		size = 0;
		pool.clear();
	}

	@Override
	public void onWorldChange() {
		size = 0;
		pool.clear();
	}

	@Override
	public Preview preview() {
		return Preview.SCENE;
	}

	/** Figure-eight path the preview emitter follows. */
	private static double[] path(float t) {
		return new double[] {Mth.sin(t * 0.9f) * 1.5, Mth.sin(t * 1.8f) * 0.25 - 0.3, Mth.sin(t * 1.8f) * 0.8};
	}

	@Override
	public void renderPreview(WorldCtx ctx, float time) {
		drawDemo(ctx, time, 0, 0, 0, 1f);
	}

	/** A glowing ball flying a figure-eight and leaving the current trail (menu preview + Player Visual Editor). */
	private void drawDemo(WorldCtx ctx, float time, double ox, double oy, double oz, float scale) {
		int n = Math.min(length.getI(), 60);
		float step = Math.max(0.02f, interval.getF());
		float life = Math.min(lifetime.getF(), n * step);
		double[] prev = null;
		float op = opacity.getF(), w = width.getF();
		for (int i = 0; i < n; i++) {
			float age = i * step;
			if (age > life) break;
			double[] p = path(time - age);
			p[0] = ox + p[0] * scale; p[1] = oy + p[1] * scale; p[2] = oz + p[2] * scale;
			float f = 1f - age / life;
			int c = Colors.withAlpha(Colors.lerp(color2.argb(), color.argb(), f), op * f);
			if (prev != null) {
				if (has(Style.RIBBON) || style.get() == Style.ENERGY) ctx.ribbon(ctx.glow, prev[0], prev[1], prev[2], p[0], p[1], p[2], w * 0.5f * (f + step / life), w * 0.5f * f, c, c);
				if (has(Style.LINE)) ctx.line(prev[0], prev[1], prev[2], p[0], p[1], p[2], c, c, 2.5f);
			}
			if ((has(Style.PARTICLES) || has(Style.SPARKS)) && i % 2 == 0) ctx.billboard(ctx.glow, p[0], p[1] + Math.sin(i) * 0.08, p[2], particleSize.getF() * f + 0.01f, 0, c, has(Style.SPARKS) ? Shape.STAR : Shape.CIRCLE);
			if (style.get() == Style.GEOMETRIC && i % 2 == 0) ctx.billboard(ctx.fill, p[0], p[1], p[2], particleSize.getF() * 1.4f * f + 0.01f, time * animSpeed.getF() + i, c, geoShape.get());
			if (has(Style.GHOST) && i % 8 == 4) ghost(ctx, p[0], p[1] - 0.9, p[2], 0, Colors.withAlpha(color.argb(), op * 0.35f * f));
			prev = p;
		}
		double[] head = path(time);
		head[0] = ox + head[0] * scale; head[1] = oy + head[1] * scale; head[2] = oz + head[2] * scale;
		ctx.billboard(ctx.glow, head[0], head[1], head[2], 0.18f, 0, Colors.withAlpha(color.argb(), 0.9f), Shape.SOFT);
	}
}
