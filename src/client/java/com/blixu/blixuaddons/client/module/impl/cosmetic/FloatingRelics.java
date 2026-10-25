package com.blixu.blixuaddons.client.module.impl.cosmetic;

import com.blixu.blixuaddons.client.anim.Spring;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.render.Mesh;
import com.blixu.blixuaddons.client.render.ParticlePool;
import com.blixu.blixuaddons.client.render.Shape;
import com.blixu.blixuaddons.client.render.WorldCtx;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Original geometric relics orbiting your own character (only you see them; never attached
 * to other players). The orbit centre follows you on springs so it never snaps.
 */
public class FloatingRelics extends Module {
	public enum Model {
		DIAMOND("Floating diamond"), CRYSTAL("Orbiting crystal"), RING("Rotating ring"), CRESCENT("Crescent ornament"),
		RUNE("Stylized rune"), STAR("Floating star"), SHARD("Geometric shard"), ORNAMENT("Ornament (custom)"), STAR_OF_DAVID("Star of David");
		public final String label;
		Model(String l) { label = l; }
	}

	public enum SlotModel {
		SAME("Same as main"), DIAMOND("Diamond"), CRYSTAL("Crystal"), RING("Ring"), CRESCENT("Crescent"), RUNE("Rune"), STAR("Star"), SHARD("Shard"), ORNAMENT("Ornament"),
		STAR_OF_DAVID("Star of David");
		public final String label;
		SlotModel(String l) { label = l; }
	}

	public enum Formation { CIRCLE("Circle"), SPIRAL("Spiral"), HALO("Tilted halo"), WAVE("Wave");
		public final String label;
		Formation(String l) { label = l; }
	}

	public enum Animation { SPIN("Spin"), BOB("Bob"), PULSE("Pulse"), TUMBLE("Tumble");
		public final String label;
		Animation(String l) { label = l; }
	}

	public final EnumSetting<Model> model = add(new EnumSetting<>("model", "Relic model", Model.CRYSTAL, m -> m.label));
	public final EnumSetting<SlotModel> slot1 = add(new EnumSetting<>("slot1", "Relic 1 model", SlotModel.SAME, m -> m.label));
	public final EnumSetting<SlotModel> slot2 = add(new EnumSetting<>("slot2", "Relic 2 model", SlotModel.SAME, m -> m.label));
	public final EnumSetting<SlotModel> slot3 = add(new EnumSetting<>("slot3", "Relic 3 model", SlotModel.SAME, m -> m.label));
	public final EnumSetting<SlotModel> slot4 = add(new EnumSetting<>("slot4", "Relic 4+ model", SlotModel.SAME, m -> m.label));
	public final NumberSetting count = add(new NumberSetting("count", "Number of relics", 3, 1, 12, 1));
	public final NumberSetting radius = add(new NumberSetting("radius", "Orbit radius", 0.9, 0.3, 3, 0.05));
	public final NumberSetting height = add(new NumberSetting("height", "Orbit height", 1.3, 0, 3, 0.05));
	public final NumberSetting speed = add(new NumberSetting("speed", "Rotation speed", 0.6, 0, 4, 0.05));
	public final BoolSetting clockwise = add(new BoolSetting("clockwise", "Clockwise", false));
	public final EnumSetting<Formation> formation = add(new EnumSetting<>("formation", "Formation", Formation.CIRCLE, f -> f.label));
	public final NumberSetting spacing = add(new NumberSetting("spacing", "Spacing (share of circle)", 1, 0.2, 1, 0.05));
	public final NumberSetting scale = add(new NumberSetting("scale", "Model scale", 1, 0.3, 3, 0.05));
	public final ColorSetting primary = add(new ColorSetting("primary", "Primary colour", 0x8B5CF6));
	public final ColorSetting secondary = add(new ColorSetting("secondary", "Secondary colour", 0x22D3EE));
	public final ColorSetting accent = add(new ColorSetting("accent", "Accent colour", 0xFFFFFF));
	public final NumberSetting glow = add(new NumberSetting("glow", "Glow intensity", 0.6, 0, 1, 0.05));
	public final BoolSetting outline = add(new BoolSetting("outline", "Outline edges", true));
	public final NumberSetting emissive = add(new NumberSetting("emissive", "Emissive brightness", 0.6, 0, 1, 0.05));
	public final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 0.9, 0.1, 1, 0.05));
	public final BoolSetting particleTrail = add(new BoolSetting("particle_trail", "Particle trail", true));
	public final BoolSetting innerCore = add(new BoolSetting("inner_core", "Glowing inner core", true));
	public final BoolSetting orbitRings = add(new BoolSetting("orbit_rings", "Small ring around each relic", true));
	public final BoolSetting constellation = add(new BoolSetting("constellation", "Connect relics (constellation)", false));
	public final EnumSetting<Animation> animation = add(new EnumSetting<>("animation", "Animation style", Animation.SPIN, a -> a.label));
	public final BoolSetting firstPerson = add(new BoolSetting("first_person", "Visible in first person", true));
	public final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Visible in third person", true));

	private final Mesh[] meshes = new Mesh[Model.values().length];
	private final Spring sx = new Spring(0), sy = new Spring(0), sz = new Spring(0);
	private boolean snapped;
	private double lastX, lastY, lastZ;
	private final Quaternionf rot = new Quaternionf();
	private final ParticlePool trail = new ParticlePool(600, ParticlePool.Kind.COSMETIC);
	private float trailTimer;

	public FloatingRelics() {
		super("floating_relics", "Floating Relics",
			"Original crystals, diamonds, rings, runes and stars orbiting you.",
			Category.COSMETICS, "relic", false);
		meshes[Model.DIAMOND.ordinal()] = Mesh.diamond(0.11f, 0.16f);
		meshes[Model.CRYSTAL.ordinal()] = Mesh.bipyramid(6, 0.07f, 0.24f, 0.1f);
		meshes[Model.RING.ordinal()] = Mesh.ring(0.13f, 0.035f, 24, Mth.TWO_PI, false);
		meshes[Model.CRESCENT.ordinal()] = Mesh.ring(0.14f, 0.07f, 18, Mth.PI * 1.3f, true);
		meshes[Model.RUNE.ordinal()] = rune();
		meshes[Model.STAR.ordinal()] = Mesh.star(5, 0.15f, 0.065f, 0.05f);
		meshes[Model.SHARD.ordinal()] = Mesh.shard(0.1f);
		meshes[Model.ORNAMENT.ordinal()] = ornament();
		meshes[Model.STAR_OF_DAVID.ordinal()] = Mesh.starOfDavid(0.16f, 0.022f, 0.04f);
	}

	/** Flat tablet with an engraved-looking (raised) angular glyph. */
	private static Mesh rune() {
		Mesh m = new Mesh();
		Mesh.addBox(m, 0, 0, 0, 0.1f, 0.14f, 0.015f, 0.2f, 0.4f);
		float d = 0.022f;
		Mesh.addBox(m, 0, 0.02f, d, 0.012f, 0.1f, 0.008f, 1, 1);
		Mesh.addBox(m, 0.035f, 0.06f, d, 0.04f, 0.012f, 0.008f, 1, 1);
		Mesh.addBox(m, -0.03f, -0.04f, d, 0.035f, 0.012f, 0.008f, 1, 1);
		m.edge(-0.1f, -0.14f, 0.016f, 0.1f, -0.14f, 0.016f).edge(0.1f, -0.14f, 0.016f, 0.1f, 0.14f, 0.016f)
			.edge(0.1f, 0.14f, 0.016f, -0.1f, 0.14f, 0.016f).edge(-0.1f, 0.14f, 0.016f, -0.1f, -0.14f, 0.016f);
		return m;
	}

	/** Small diamond inside a thin ring. */
	private static Mesh ornament() {
		Mesh m = Mesh.bipyramid(4, 0.06f, 0.12f, 0.12f);
		int segs = 20;
		float radius = 0.12f, half = 0.01f;
		for (int i = 0; i < segs; i++) {
			float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			m.quad(c0 * (radius + half), 0, s0 * (radius + half), c1 * (radius + half), 0, s1 * (radius + half),
				c1 * (radius - half), 0, s1 * (radius - half), c0 * (radius - half), 0, s0 * (radius - half), 1, 1, 1, 1);
			m.quad(c0 * radius, -half, s0 * radius, c1 * radius, -half, s1 * radius, c1 * radius, half, s1 * radius, c0 * radius, half, s0 * radius, 1, 1, 1, 1);
			m.edge(c0 * radius, 0, s0 * radius, c1 * radius, 0, s1 * radius);
		}
		return m;
	}

	private Model modelFor(int i) {
		SlotModel s = switch (i) {
			case 0 -> slot1.get();
			case 1 -> slot2.get();
			case 2 -> slot3.get();
			default -> slot4.get();
		};
		return s == SlotModel.SAME ? model.get() : Model.values()[s.ordinal() - 1];
	}

	@Override
	public void onWorldRender(WorldCtx ctx) {
		LocalPlayer p = ctx.mc.player;
		if (p == null) return;
		double px = Mth.lerp(ctx.partialTick, p.xo, p.getX());
		double py = Mth.lerp(ctx.partialTick, p.yo, p.getY());
		double pz = Mth.lerp(ctx.partialTick, p.zo, p.getZ());
		// The orbit centre lags behind you on a spring: we spring the offset (not the absolute
		// position) so it stays precise far from the world origin.
		if (!snapped || Math.abs(px - lastX) > 8 || Math.abs(py - lastY) > 8 || Math.abs(pz - lastZ) > 8) {
			sx.snap(0);
			sy.snap(0);
			sz.snap(0);
			snapped = true;
		} else {
			sx.value += (float) (lastX - px);
			sy.value += (float) (lastY - py);
			sz.value += (float) (lastZ - pz);
		}
		lastX = px;
		lastY = py;
		lastZ = pz;
		double cx = px + sx.update(0, ctx.dt, 120f, 22f);
		double cy = py + sy.update(0, ctx.dt, 120f, 22f);
		double cz = pz + sz.update(0, ctx.dt, 120f, 22f);
		trail.update(ctx.dt);

		boolean fp = ctx.mc.options.getCameraType().isFirstPerson();
		if (fp ? !firstPerson.get() : !thirdPerson.get()) return;

		drawRelics(ctx, cx, cy, cz, trail);
	}

	private void drawRelics(WorldCtx ctx, double cx, double cy, double cz, ParticlePool trail) {
		int n = count.getI();
		float t = ctx.time * speed.getF() * (clockwise.get() ? -1 : 1);
		float span = Mth.TWO_PI * spacing.getF();
		float op = opacity.getF();
		int edge = outline.get() ? Colors.withAlpha(accent.argb(), op) : 0;
		float bright = 0.75f + 0.5f * emissive.getF();
		trailTimer += ctx.dt;
		boolean emit = particleTrail.get() && trailTimer > 0.04f;
		if (emit) trailTimer = 0;

		for (int i = 0; i < n; i++) {
			float a = t + (n == 1 ? 0 : span * i / (spacing.getF() >= 0.999f ? n : n - 1));
			float r = radius.getF();
			float h = height.getF();
			double ox = Mth.cos(a) * r, oz = Mth.sin(a) * r, oy = h;
			switch (formation.get()) {
				case SPIRAL -> oy = h - 0.4 + 0.8 * i / Math.max(1, n - 1);
				case HALO -> oy = h + Mth.sin(a) * r * 0.35f;
				case WAVE -> oy = h + Mth.sin(a * 2 + ctx.time * 2) * 0.15f;
				case CIRCLE -> { }
			}
			float s = scale.getF();
			rot.identity();
			switch (animation.get()) {
				case SPIN -> rot.rotateY(ctx.time * 2f + i);
				case BOB -> {
					oy += Mth.sin(ctx.time * 2.5f + i * 1.3f) * 0.08f;
					rot.rotateY(-a);
				}
				case PULSE -> {
					s *= 1f + 0.15f * Mth.sin(ctx.time * 4f + i);
					rot.rotateY(ctx.time + i);
				}
				case TUMBLE -> rot.rotateXYZ(ctx.time * 1.3f + i, ctx.time * 0.9f, ctx.time * 0.6f + i * 0.5f);
			}
			Model mdl = modelFor(i);
			if (mdl == Model.RUNE) rot.rotateY(-a + Mth.HALF_PI - ctx.time * 2f - i);
			double wx = cx + ox, wy = cy + oy, wz = cz + oz;
			int ca = Colors.withAlpha(Colors.scale(primary.argb(i / (float) n), bright), op);
			int cb = Colors.withAlpha(Colors.scale(secondary.argb(i / (float) n), bright), op);
			meshes[mdl.ordinal()].draw(ctx, ctx.fill, wx, wy, wz, rot, s, cb, ca, 0.8f, edge, 2f);
			if (innerCore.get()) {
				float beat = 0.7f + 0.3f * Mth.sin(ctx.time * 3f + i);
				ctx.billboard(ctx.glow, wx, wy, wz, 0.07f * s * beat, 0, Colors.withAlpha(0xFFFFFFFF, 0.9f * op), Shape.SOFT);
			}
			if (orbitRings.get()) {
				ctx.ring(ctx.glow, wx, wy, wz, 0.2f * s, 0.012f * s, 0.9f + Mth.sin(ctx.time + i) * 0.3f, ctx.time * 1.5f + i,
					Colors.withAlpha(accent.argb(), 0.8f * op), Colors.withAlpha(accent.argb(), 0.1f * op), 28);
			}
			if (i < 16) {
				relX[i] = wx;
				relY[i] = wy;
				relZ[i] = wz;
			}
			if (glow.getF() > 0) {
				ctx.billboard(ctx.glow, wx, wy, wz, 0.32f * s * (0.6f + glow.getF()), 0, Colors.withAlpha(primary.argb(), 0.35f * glow.getF() * op), Shape.SOFT);
			}
			if (emit) {
				trail.spawn(wx, wy, wz, 0, -0.1f, 0, 0.6f, 0.05f * s, 0.005f, Colors.withAlpha(secondary.argb(), 0.8f), accent.argb(), Shape.SOFT, true, 0, 0.5f, 0, 0, 600);
			}
		}
		if (constellation.get() && n > 1) {
			int lineC = Colors.withAlpha(accent.argb(), 0.55f * op);
			for (int i = 0; i < Math.min(n, 16); i++) {
				int j = (i + 1) % Math.min(n, 16);
				ctx.line(relX[i], relY[i], relZ[i], relX[j], relY[j], relZ[j], lineC, lineC, 1.5f);
			}
		}
		trail.draw(ctx, op);
	}

	private final ParticlePool previewTrail = new ParticlePool(300, ParticlePool.Kind.PREVIEW);
	private final double[] relX = new double[16], relY = new double[16], relZ = new double[16];

	@Override
	public Preview preview() {
		return Preview.SCENE;
	}

	@Override
	public void renderPreview(WorldCtx ctx, float time) {
		previewTrail.update(ctx.dt);
		// stand-in character (feet at y = -1)
		ctx.boxFill(ctx.fill, -0.3, -1, -0.15, 0.3, 0.5, 0.15, 0xFF3A3F52, 0xFF4A5068);
		ctx.boxFill(ctx.fill, -0.25, 0.5, -0.25, 0.25, 1.0, 0.25, 0xFF4A5068, 0xFF5C6378);
		drawRelics(ctx, 0, -1, 0, previewTrail);
	}

	@Override
	protected void onDisable() {
		snapped = false;
		trail.clear();
	}

	@Override
	public void onWorldChange() {
		snapped = false;
		trail.clear();
	}
}
