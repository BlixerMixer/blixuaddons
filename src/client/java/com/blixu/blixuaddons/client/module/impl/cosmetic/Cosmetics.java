package com.blixu.blixuaddons.client.module.impl.cosmetic;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.render.GeoBatch;
import com.blixu.blixuaddons.client.render.Mesh;
import com.blixu.blixuaddons.client.render.ParticlePool;
import com.blixu.blixuaddons.client.render.Shape;
import com.blixu.blixuaddons.client.render.WorldCtx;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Original 3D cosmetics drawn on your own character only: conical straw hats, wings, halo,
 * floating rings, shoulder crystals, a back ornament and a crown. All geometry is built
 * here from simple shapes (no copied models/textures) and is purely client-side —
 * hitbox, model and server state are untouched.
 */
public class Cosmetics extends Module {
	public enum Hat {
		NONE("None"), CLASSIC("Classic woven"), WIDE("Wide layered"), MINIMAL("Minimal geometric"), DECORATED("Decorated"), FANTASY("Fantasy rings"),
		KIPPAH("Kippah");
		public final String label;
		Hat(String l) { label = l; }
	}

	public enum Wings {
		NONE("None"), FEATHERED("Feathered"), ENERGY("Energy"), CRYSTAL("Crystal"), MECHANICAL("Mechanical"), MINIMAL("Minimal translucent"),
		DRAGON("Dragon membrane");
		public final String label;
		Wings(String l) { label = l; }
	}

	public enum Material {
		MATTE("Matte"), GLOSSY("Glossy"), GLOW("Glowing"), GLASS("Glass");
		public final String label;
		Material(String l) { label = l; }
	}

	public final EnumSetting<Hat> hat = add(new EnumSetting<>("hat", "Hat", Hat.CLASSIC, h -> h.label));
	public final NumberSetting hatScale = add(new NumberSetting("hat_scale", "Hat scale", 1, 0.5, 2, 0.05));
	public final NumberSetting hatOffset = add(new NumberSetting("hat_offset", "Hat height offset", 0, -0.3, 0.5, 0.01));
	public final NumberSetting hatTilt = add(new NumberSetting("hat_tilt", "Hat tilt", 0, -30, 30, 1));
	public final EnumSetting<Wings> wings = add(new EnumSetting<>("wings", "Wings", Wings.NONE, w -> w.label));
	public final NumberSetting wingScale = add(new NumberSetting("wing_scale", "Wing scale", 1, 0.4, 2, 0.05));
	public final NumberSetting wingOffsetY = add(new NumberSetting("wing_offset_y", "Wing height offset", 0, -0.5, 0.5, 0.01));
	public final NumberSetting wingOffsetZ = add(new NumberSetting("wing_offset_z", "Wing back offset", 0, -0.3, 0.3, 0.01));
	public final NumberSetting wingSpread = add(new NumberSetting("wing_spread", "Wing spread angle", 25, 0, 70, 1));
	public final BoolSetting flap = add(new BoolSetting("flap", "Flap when moving / in air", true));
	public final BoolSetting halo = add(new BoolSetting("halo", "Halo", false));
	public final BoolSetting rings = add(new BoolSetting("rings", "Floating rings", false));
	public final BoolSetting shoulders = add(new BoolSetting("shoulders", "Shoulder ornaments", false));
	public final BoolSetting back = add(new BoolSetting("back", "Back ornament", false));
	public final BoolSetting crown = add(new BoolSetting("crown", "Decorative crown", false));
	public final NumberSetting accessoryScale = add(new NumberSetting("accessory_scale", "Accessory scale", 1, 0.5, 2, 0.05));

	public final ColorSetting primary = add(new ColorSetting("primary", "Primary colour", 0xD9B26B));
	public final ColorSetting secondary = add(new ColorSetting("secondary", "Secondary colour", 0x8A6A3A));
	public final ColorSetting accent = add(new ColorSetting("accent", "Accent colour", 0xD13BFF));
	public final EnumSetting<Material> material = add(new EnumSetting<>("material", "Material", Material.MATTE, m -> m.label));
	public final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 1, 0.1, 1, 0.05));
	public final NumberSetting glow = add(new NumberSetting("glow", "Glow intensity", 0.3, 0, 1, 0.05));
	public final NumberSetting emissive = add(new NumberSetting("emissive", "Emissive brightness", 0.2, 0, 1, 0.05));
	public final BoolSetting particles = add(new BoolSetting("particles", "Sparkle particles", false));
	public final NumberSetting animSpeed = add(new NumberSetting("anim_speed", "Animation speed", 1, 0.1, 4, 0.05));
	public final BoolSetting idle = add(new BoolSetting("idle", "Idle animation", true));
	public final BoolSetting firstPerson = add(new BoolSetting("first_person", "Visible in first person (no headwear)", false));
	public final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Visible in third person", true));

	private final Mesh[] hats = new Mesh[Hat.values().length];
	private final Mesh haloMesh = Mesh.ring(0.27f, 0.035f, 32, Mth.TWO_PI, false);
	private final Mesh ringMesh = Mesh.ring(0.55f, 0.025f, 40, Mth.TWO_PI, false);
	private final Mesh smallRing = Mesh.ring(0.2f, 0.018f, 24, Mth.TWO_PI, false);
	private final Mesh crystal = Mesh.bipyramid(6, 0.05f, 0.14f, 0.06f);
	private final Mesh backOrb = Mesh.bipyramid(8, 0.1f, 0.13f, 0.13f);
	private final Mesh spike = Mesh.bipyramid(4, 0.025f, 0.09f, 0.01f);
	private final Mesh star = Mesh.star(5, 0.06f, 0.025f, 0.02f);
	private final Quaternionf head = new Quaternionf(), body = new Quaternionf(), q = new Quaternionf(), cq = new Quaternionf();
	private final Vector3f v = new Vector3f();
	private final ParticlePool sparkles = new ParticlePool(300, ParticlePool.Kind.COSMETIC);
	private final Random random = new Random();
	private float flapPhase;

	public Cosmetics() {
		super("cosmetics", "3D Cosmetics",
			"Original straw hats, wings, halo, rings and ornaments on your own character.",
			Category.COSMETICS, "hat", false);
		hats[Hat.CLASSIC.ordinal()] = classicHat(24, 0.66f, 0.46f, true);
		hats[Hat.WIDE.ordinal()] = wideHat();
		hats[Hat.MINIMAL.ordinal()] = classicHat(6, 0.56f, 0.38f, false);
		hats[Hat.DECORATED.ordinal()] = decoratedHat();
		hats[Hat.FANTASY.ordinal()] = classicHat(20, 0.56f, 0.44f, true);
		hats[Hat.KIPPAH.ordinal()] = kippah();
	}

	// ------------------------------------------------------------------ hat meshes (original designs)

	/**
	 * Conical straw hat: 16 concentric bands on a slightly bell-shaped profile (about twice as
	 * wide as tall), alternating shades like overlapping leaf layers, 8 radial ribs and a chin
	 * strap. {@code woven = false} gives a plain low-poly cone.
	 */
	private static Mesh classicHat(int segs, float radius, float height, boolean woven) {
		Mesh m = new Mesh();
		int bands = woven ? 16 : 4;
		for (int b = 0; b < bands; b++) {
			float t0 = b / (float) bands, t1 = (b + 1) / (float) bands;
			float r0 = radius * (float) Math.pow(1 - t0, 0.92), r1 = radius * (float) Math.pow(1 - t1, 0.92);
			float y0 = height * t0, y1 = height * t1;
			float shade = woven ? (b % 2 == 0 ? 0.15f : 0.55f) : 0f;
			for (int i = 0; i < segs; i++) {
				float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
				float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
				float tv = woven ? shade + (i % 2) * 0.12f : t0;
				if (r1 < 0.001f) m.tri(0, y1, 0, c0 * r0, y0, s0 * r0, c1 * r0, y0, s1 * r0, 1, tv, tv);
				else m.quad(c0 * r0, y0, s0 * r0, c1 * r0, y0, s1 * r0, c1 * r1, y1, s1 * r1, c0 * r1, y1, s0 * r1, tv, tv, tv, tv);
				// underside of the brim band so it reads solid from below
				if (b == 0) m.tri(0, 0.01f, 0, c1 * r0, 0, s1 * r0, c0 * r0, 0, s0 * r0, 0, 0, 0);
			}
			if (woven && b % 2 == 1) {
				for (int i = 0; i < segs; i++) {
					float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
					m.edge(Mth.cos(a0) * r1, y1 + 0.003f, Mth.sin(a0) * r1, Mth.cos(a1) * r1, y1 + 0.003f, Mth.sin(a1) * r1);
				}
			}
		}
		int ribs = woven ? 8 : segs;
		for (int i = 0; i < ribs; i++) {
			float a = Mth.TWO_PI * i / ribs;
			m.edge(Mth.cos(a) * radius, 0.004f, Mth.sin(a) * radius, 0, height + 0.004f, 0);
		}
		// chin strap from the inner brim down under the chin
		m.edge(-0.24f, 0.02f, 0.02f, -0.05f, -0.52f, 0.2f).edge(0.24f, 0.02f, 0.02f, 0.05f, -0.52f, 0.2f).edge(-0.05f, -0.52f, 0.2f, 0.05f, -0.52f, 0.2f);
		return m;
	}

	/** Wide flat brim with a smaller raised crown on top. */
	private static Mesh wideHat() {
		Mesh m = new Mesh();
		Mesh.addCone(m, 20, 0.82f, 0.34f, 0.1f, 0, true);
		Mesh.addCone(m, 20, 0.34f, 0f, 0.3f, 0.1f, false);
		return m;
	}

	/**
	 * Kippah: a shallow round cap on the crown of the head, sewn from six panels (alternating shade)
	 * with a stitched rim. It sits a little into the top of the head so it reads as worn, not floating.
	 */
	private static Mesh kippah() {
		Mesh m = new Mesh();
		int segs = 24, rings = 5, panels = 6;
		float radius = 0.21f, height = 0.07f, base = -0.025f;
		for (int k = 0; k < rings; k++) {
			float p0 = Mth.HALF_PI * k / rings, p1 = Mth.HALF_PI * (k + 1) / rings;
			float r0 = radius * Mth.cos(p0), r1 = radius * Mth.cos(p1);
			float y0 = base + height * Mth.sin(p0), y1 = base + height * Mth.sin(p1);
			for (int i = 0; i < segs; i++) {
				float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
				float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
				float tv = (i * panels / segs) % 2 == 0 ? 0.25f : 0.6f;
				if (r1 < 0.001f) m.tri(0, y1, 0, c0 * r0, y0, s0 * r0, c1 * r0, y0, s1 * r0, tv, tv, tv);
				else m.quad(c0 * r0, y0, s0 * r0, c1 * r0, y0, s1 * r0, c1 * r1, y1, s1 * r1, c0 * r1, y1, s0 * r1, tv, tv, tv, tv);
			}
		}
		// panel seams from the rim to the top, and the stitched rim
		for (int i = 0; i < panels; i++) {
			float a = Mth.TWO_PI * i / panels;
			float px = Mth.cos(a), pz = Mth.sin(a);
			for (int k = 0; k < rings; k++) {
				float p0 = Mth.HALF_PI * k / rings, p1 = Mth.HALF_PI * (k + 1) / rings;
				m.edge(px * radius * Mth.cos(p0), base + height * Mth.sin(p0) + 0.002f, pz * radius * Mth.cos(p0),
					px * radius * Mth.cos(p1), base + height * Mth.sin(p1) + 0.002f, pz * radius * Mth.cos(p1));
			}
		}
		for (int i = 0; i < segs; i++) {
			float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
			m.edge(Mth.cos(a0) * radius, base + 0.003f, Mth.sin(a0) * radius, Mth.cos(a1) * radius, base + 0.003f, Mth.sin(a1) * radius);
		}
		return m;
	}

	/** Classic cone with a zig-zag geometric band. */
	private static Mesh decoratedHat() {
		Mesh m = classicHat(20, 0.62f, 0.42f, true);
		int teeth = 16;
		float r0 = 0.62f * 0.6f, r1 = 0.62f * 0.46f, y0 = 0.42f * 0.38f, y1 = 0.42f * 0.52f;
		for (int i = 0; i < teeth; i++) {
			float a0 = Mth.TWO_PI * i / teeth, am = Mth.TWO_PI * (i + 0.5f) / teeth, a1 = Mth.TWO_PI * (i + 1) / teeth;
			float lift = 0.006f;
			m.tri(Mth.cos(a0) * r0, y0 + lift, Mth.sin(a0) * r0, Mth.cos(am) * r1, y1 + lift, Mth.sin(am) * r1, Mth.cos(a1) * r0, y0 + lift, Mth.sin(a1) * r0, 1, 1, 1);
		}
		return m;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void onWorldRender(WorldCtx ctx) {
		LocalPlayer p = ctx.mc.player;
		if (p == null) return;
		sparkles.update(ctx.dt);
		boolean fp = ctx.mc.options.getCameraType().isFirstPerson();
		if (fp ? !firstPerson.get() : !thirdPerson.get()) return;
		if (p.isInvisible() || p.isSleeping()) return;

		float pt = ctx.partialTick;
		double px = Mth.lerp(pt, p.xo, p.getX()), py = Mth.lerp(pt, p.yo, p.getY()), pz = Mth.lerp(pt, p.zo, p.getZ());
		float bodyYaw = PlayerSpin.bodyYaw(Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot)) * Mth.DEG_TO_RAD;
		float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) * Mth.DEG_TO_RAD;
		var spin = com.blixu.blixuaddons.client.module.ModuleManager.active(PlayerSpin.class);
		if (spin != null && spin.spinHead.get()) headYaw = bodyYaw;
		float pitch = p.getXRot(pt) * Mth.DEG_TO_RAD;
		boolean crouch = PlayerSpin.crouching(p.getPose() == Pose.CROUCHING);
		float time = ctx.time * animSpeed.getF();

		body.identity().rotateY(-bodyYaw);
		if (crouch) body.rotateX(0.5f);
		head.identity().rotateY(-headYaw).rotateX(pitch);
		double neckY = py + (crouch ? 1.12 : 1.5);

		Material mat = material.get();
		GeoBatch faces = mat == Material.GLOW ? ctx.glow : ctx.fill;
		float op = opacity.getF() * (mat == Material.GLASS ? 0.45f : 1f);
		float bright = 1f + emissive.getF() * 0.6f;
		int ca = Colors.withAlpha(Colors.scale(primary.argb(), bright), op);
		int cb = Colors.withAlpha(Colors.scale(secondary.argb(), bright), op);
		int edge = mat == Material.MATTE ? Colors.withAlpha(Colors.scale(secondary.argb(), 0.7f), op)
			: Colors.withAlpha(Colors.lerp(accent.argb(), 0xFFFFFFFF, mat == Material.GLOSSY ? 0.6f : 0.2f), op);
		float shade = mat == Material.GLOW ? 0f : mat == Material.GLOSSY ? 1f : 0.8f;

		// Headwear (never in first person: it would sit on the camera)
		if (!fp) {
			Hat h = hat.get();
			if (h != Hat.NONE) {
				q.set(head).rotateZ(hatTilt.getF() * Mth.DEG_TO_RAD);
				head.transform(v.set(0, 0.42f + hatOffset.getF(), 0));
				double hx = px + v.x, hy = neckY + v.y, hz = pz + v.z;
				hats[h.ordinal()].draw(ctx, faces, hx, hy, hz, q, hatScale.getF(), cb, ca, shade, edge, 1.5f);
				if (h == Hat.FANTASY) {
					for (int i = 0; i < 2; i++) {
						q.set(head).rotateY(time * (i == 0 ? 1.2f : -0.8f)).rotateX(0.25f * (i == 0 ? 1 : -1));
						head.transform(v.set(0, 0.42f + hatOffset.getF() + (0.48f + i * 0.12f + (idle.get() ? Mth.sin(time * 2 + i) * 0.03f : 0)) * hatScale.getF(), 0));
						smallRing.draw(ctx, ctx.glow, px + v.x, neckY + v.y, pz + v.z, q, hatScale.getF() * (1.1f - i * 0.3f),
							Colors.withAlpha(accent.argb(), op), Colors.withAlpha(0xFFFFFFFF, op * 0.8f), 0, 0, 0);
					}
					head.transform(v.set(0, 0.42f + hatOffset.getF() + 0.43f * hatScale.getF(), 0));
					q.set(head).rotateY(time * 1.5f).rotateX(Mth.HALF_PI);
					star.draw(ctx, ctx.glow, px + v.x, neckY + v.y, pz + v.z, q, hatScale.getF(), Colors.withAlpha(accent.argb(), op), 0xFFFFFFFF, 0, 0, 0);
				}
			}
			if (crown.get()) {
				float s = accessoryScale.getF();
				for (int i = 0; i < 8; i++) {
					float a = Mth.TWO_PI * i / 8;
					head.transform(v.set(Mth.cos(a) * 0.24f * s, 0.5f + hatOffset.getF(), Mth.sin(a) * 0.24f * s));
					q.set(head);
					spike.draw(ctx, faces, px + v.x, neckY + v.y, pz + v.z, q, s, cb, Colors.withAlpha(accent.argb(), op), shade, 0, 0);
				}
			}
			if (halo.get()) {
				float bob = idle.get() ? Mth.sin(time * 2f) * 0.03f : 0;
				q.identity().rotateY(-headYaw + time * 0.5f);
				double hy = neckY + 0.78 + bob + hatOffset.getF() * 0.5;
				haloMesh.draw(ctx, ctx.glow, px, hy, pz, q, accessoryScale.getF(), Colors.withAlpha(accent.argb(), op), Colors.withAlpha(0xFFFFFFFF, op), 0, 0, 0);
				if (glow.getF() > 0) ctx.billboard(ctx.glow, px, hy, pz, 0.45f * accessoryScale.getF(), 0, Colors.withAlpha(accent.argb(), 0.25f * glow.getF()), Shape.SOFT);
			}
		}

		if (rings.get()) {
			for (int i = 0; i < 2; i++) {
				q.identity().rotateY(time * (i == 0 ? 0.8f : -0.6f)).rotateX(0.35f * (i == 0 ? 1 : -1)).rotateZ(0.2f * i);
				double ry = py + 0.9 + i * 0.35 + (idle.get() ? Mth.sin(time * 1.5f + i) * 0.04 : 0);
				ringMesh.draw(ctx, ctx.glow, px, ry, pz, q, accessoryScale.getF() * (1f - i * 0.15f), Colors.withAlpha(accent.argb(), op * 0.85f),
					Colors.withAlpha(primary.argb(), op * 0.85f), 0, 0, 0);
			}
		}
		if (shoulders.get()) {
			for (int side = -1; side <= 1; side += 2) {
				body.transform(v.set(side * 0.42f, crouch ? -0.2f : -0.02f, 0));
				q.set(body).rotateY(time * 1.5f * side);
				double sy = neckY + v.y + (idle.get() ? Mth.sin(time * 2f + side) * 0.02 : 0) + 0.06;
				crystal.draw(ctx, faces, px + v.x, sy, pz + v.z, q, accessoryScale.getF(), cb, Colors.withAlpha(accent.argb(), op), shade, edge, 1.2f);
			}
		}
		if (back.get()) {
			body.transform(v.set(0, -0.35f, -0.48f));
			q.identity().rotateY(time).rotateX(time * 0.6f);
			double by = neckY + v.y + (idle.get() ? Mth.sin(time * 1.8f) * 0.06 : 0);
			backOrb.draw(ctx, faces, px + v.x, by, pz + v.z, q, accessoryScale.getF(), Colors.withAlpha(accent.argb(), op), ca, shade, edge, 1.2f);
			if (glow.getF() > 0) ctx.billboard(ctx.glow, px + v.x, by, pz + v.z, 0.35f * accessoryScale.getF(), 0, Colors.withAlpha(accent.argb(), 0.3f * glow.getF()), Shape.SOFT);
		}

		Wings w = wings.get();
		if (w != Wings.NONE) {
			boolean moving = p.getDeltaMovement().horizontalDistanceSqr() > 0.003 || !p.onGround();
			float speed = flap.get() && moving ? (p.onGround() ? 5f : 9f) : (idle.get() ? 1.6f : 0f);
			flapPhase += ctx.dt * speed * animSpeed.getF();
			float amp = flap.get() && !p.onGround() ? 0.55f : 0.18f;
			float flapAngle = Mth.sin(flapPhase) * amp;
			body.transform(v.set(0, -0.2f + wingOffsetY.getF(), -0.18f - wingOffsetZ.getF()));
			double ax = px + v.x, ay = neckY + v.y, az = pz + v.z;
			for (int side = -1; side <= 1; side += 2) drawWing(ctx, w, ax, ay, az, side, flapAngle, op, time);
		}

		if (particles.get() && random.nextFloat() < ctx.dt * 12f) {
			double sx = px + (random.nextFloat() - 0.5f) * 1.2f, sy = py + random.nextFloat() * 2.2f, sz = pz + (random.nextFloat() - 0.5f) * 1.2f;
			sparkles.spawn(sx, sy, sz, 0, 0.2f, 0, 0.9f, 0.06f, 0.0f, Colors.withAlpha(accent.argb(), 0.9f), 0xFFFFFFFF, Shape.STAR, true, 0, 0.3f,
				random.nextFloat() * 6f, 2f, 300);
		}
		sparkles.draw(ctx, op);
	}

	// Wing skeleton (wing plane: x outward, y up), rebuilt each frame from the spread/flap.
	private final float[] jx = new float[4], jy = new float[4], ja = new float[3];

	/**
	 * Arm (shoulder to elbow), forearm (elbow to wrist) and hand (wrist to tip). {@code spread}
	 * 0 = folded against the back, 1 = fully open; the hand folds most, like a real wing.
	 */
	private void skeleton(float s, float spread, float flapAngle) {
		float a0 = (20f + 25f * spread) * Mth.DEG_TO_RAD + flapAngle * 0.8f;
		float a1 = a0 + (-110f + 115f * spread) * Mth.DEG_TO_RAD;
		float a2 = a1 + (-35f + 30f * spread) * Mth.DEG_TO_RAD + flapAngle * 0.4f;
		float[] len = {0.28f * s, 0.36f * s, 0.30f * s};
		ja[0] = a0; ja[1] = a1; ja[2] = a2;
		jx[0] = 0; jy[0] = 0;
		for (int i = 0; i < 3; i++) {
			jx[i + 1] = jx[i] + Mth.cos(ja[i]) * len[i];
			jy[i + 1] = jy[i] + Mth.sin(ja[i]) * len[i];
		}
	}

	/** Point at fraction t along bone i. */
	private float bx(int i, float t) { return Mth.lerp(t, jx[i], jx[i + 1]); }
	private float by(int i, float t) { return Mth.lerp(t, jy[i], jy[i + 1]); }

	/** One wing in its own plane, rotated by body yaw, spread and flap. Side = -1 left, +1 right. */
	private void drawWing(WorldCtx ctx, Wings style, double ax, double ay, double az, int side, float flapAngle, float op, float time) {
		float s = wingScale.getF();
		float open = wingSpread.getF() * Mth.DEG_TO_RAD;
		// Wing space: x = outward, y = up, z = back. Fold the whole wing back a little by the spread angle.
		q.set(body).rotateY(side * open * 0.35f);
		float spread = Mth.clamp(0.75f + flapAngle * 0.4f, 0f, 1f);
		skeleton(s, spread, flapAngle);
		int ca = Colors.withAlpha(primary.argb(), op), cb = Colors.withAlpha(secondary.argb(), op), cc = Colors.withAlpha(accent.argb(), op);
		float down = -Mth.HALF_PI; // feathers hang "down/back" from the leading edge
		switch (style) {
			case FEATHERED, ENERGY -> {
				boolean energy = style == Wings.ENERGY;
				var batch = energy ? ctx.glow : ctx.fill;
				float pulse = energy ? 0.55f + 0.45f * Mth.sin(time * 4f) : 1f;
				int flightRoot = energy ? Colors.withAlpha(cc, op * 0.8f * pulse) : cb;
				int flightTip = energy ? Colors.withAlpha(ca, 0f) : Colors.scale(cb, 0.8f);
				// 10 primaries on the hand: fan out, longest at the tip
				for (int i = 0; i < 10; i++) {
					float t = i / 9f;
					float ang = ja[2] + down + (0.25f + 0.95f * t);
					float len = (0.42f + 0.26f * t) * s;
					feather(ctx, batch, ax, ay, az, side, bx(2, t), by(2, t), ang, len, 0.085f * s, flightRoot, flightTip, 0.03f - i * 0.002f);
				}
				// 12 secondaries along the forearm, shorter towards the elbow
				for (int i = 0; i < 12; i++) {
					float t = i / 11f;
					float ang = ja[1] + down + 0.15f * t;
					float len = (0.34f + 0.06f * t) * s;
					feather(ctx, batch, ax, ay, az, side, bx(1, t), by(1, t), ang, len, 0.075f * s, flightRoot, flightTip, 0.02f);
				}
				// tertials filling the gap to the body
				for (int i = 0; i < 5; i++) {
					float t = 0.2f + 0.8f * i / 4f;
					feather(ctx, batch, ax, ay, az, side, bx(0, t), by(0, t), ja[0] + down - 0.2f, (0.2f + 0.08f * t) * s, 0.07f * s, flightRoot, flightTip, 0.015f);
				}
				if (!energy) {
					// covert rows on top, like roof tiles: greater, median, lesser (front-most, shortest)
					float[] rowLen = {0.5f, 0.32f, 0.18f};
					int[] rowCol = {Colors.lerp(cb, ca, 0.45f), Colors.lerp(cb, ca, 0.75f), ca};
					for (int row = 0; row < 3; row++) {
						for (int bone = 0; bone < 3; bone++) {
							int n = bone == 2 ? 6 : 8;
							for (int i = 0; i < n; i++) {
								float t = (i + 0.5f) / n;
								float base = bone == 2 ? 0.42f : 0.34f;
								float ang = ja[bone] + down + (bone == 2 ? 0.25f + 0.6f * t : 0.1f);
								feather(ctx, ctx.fill, ax, ay, az, side, bx(bone, t), by(bone, t), ang, base * rowLen[row] * s * 1.6f, 0.07f * s,
									rowCol[row], Colors.scale(rowCol[row], 0.85f), -0.006f - row * 0.006f);
							}
						}
					}
					// leading-edge marginal coverts / bone line
					for (int bone = 0; bone < 3; bone++) wingLine(ctx, ax, ay, az, side, jx[bone], jy[bone], jx[bone + 1], jy[bone + 1], Colors.scale(ca, 1.1f), 3f);
				} else {
					for (int bone = 0; bone < 3; bone++) wingLine(ctx, ax, ay, az, side, jx[bone], jy[bone], jx[bone + 1], jy[bone + 1], Colors.withAlpha(0xFFFFFFFF, op * pulse), 2.5f);
				}
			}
			case CRYSTAL -> {
				// faceted shards growing along the skeleton, longest at the hand
				for (int bone = 0; bone < 3; bone++) {
					int n = 4;
					for (int i = 0; i < n; i++) {
						float t = (i + 0.5f) / n;
						float ang = ja[bone] + down + (bone == 2 ? 0.3f + 0.8f * t : 0.1f);
						float len = (0.25f + bone * 0.1f + 0.08f * t) * s;
						local(side, bx(bone, t) + Mth.cos(ang) * len * 0.5f, by(bone, t) + Mth.sin(ang) * len * 0.5f, 0);
						cq.set(q).rotateZ(side * (ang - Mth.HALF_PI));
						crystal.draw(ctx, ctx.fill, ax + v.x, ay + v.y, az + v.z, cq, len * 3.4f, cb, cc, 1f, Colors.withAlpha(0xFFFFFFFF, op * 0.8f), 1f);
					}
				}
			}
			case MECHANICAL -> {
				// segmented frame along the bones with overlapping armour plates hanging from it
				for (int bone = 0; bone < 3; bone++) {
					wingLine(ctx, ax, ay, az, side, jx[bone], jy[bone], jx[bone + 1], jy[bone + 1], cc, 3.5f);
					int n = 3;
					for (int i = 0; i < n; i++) {
						float t0 = i / (float) n, t1 = (i + 1) / (float) n;
						float drop = (0.32f + bone * 0.08f) * s;
						plate(ctx, ax, ay, az, side, bx(bone, t0), by(bone, t0), bx(bone, t1), by(bone, t1), drop, cb, ca);
					}
				}
			}
			case MINIMAL, DRAGON -> {
				// finger bones from the wrist with a membrane stretched between them (and the body)
				int fingers = style == Wings.DRAGON ? 4 : 2;
				float[] fx = new float[fingers + 1], fy = new float[fingers + 1];
				fx[0] = 0; fy[0] = -0.55f * s; // membrane root at the lower back
				for (int f = 1; f <= fingers; f++) {
					float ang = ja[2] - (f - 1) * (0.55f + 0.15f * spread);
					float len = (0.55f - f * 0.05f) * s;
					fx[f] = jx[3] - Mth.cos(ja[2]) * 0.3f * s + Mth.cos(ang) * len;
					fy[f] = jy[3] - Mth.sin(ja[2]) * 0.3f * s + Mth.sin(ang) * len;
				}
				int m1 = Colors.withAlpha(ca, op * (style == Wings.DRAGON ? 0.75f : 0.35f)), m2 = Colors.withAlpha(cb, op * (style == Wings.DRAGON ? 0.65f : 0.12f));
				float[] wrist = {jx[2], jy[2]};
				float[] prev = {jx[3], jy[3]};
				for (int f = 1; f <= fingers; f++) {
					float[] tip = {fx[f], fy[f]};
					tri(ctx, ax, ay, az, side, wrist, prev, tip, m1, m2, m2);
					prev = tip;
				}
				tri(ctx, ax, ay, az, side, wrist, prev, new float[] {fx[0], fy[0]}, m1, m2, m1);
				tri(ctx, ax, ay, az, side, new float[] {0, 0}, wrist, new float[] {fx[0], fy[0]}, m1, m1, m1);
				int bone = Colors.withAlpha(Colors.scale(cc, 0.9f), op);
				for (int i = 0; i < 3; i++) wingLine(ctx, ax, ay, az, side, jx[i], jy[i], jx[i + 1], jy[i + 1], bone, style == Wings.DRAGON ? 3.5f : 2f);
				for (int f = 1; f <= fingers; f++) wingLine(ctx, ax, ay, az, side, wrist[0], wrist[1], fx[f], fy[f], bone, style == Wings.DRAGON ? 2.5f : 1.5f);
			}
			case NONE -> { }
		}
	}

	private void local(int side, float x, float y, float z) {
		q.transform(v.set(side * x, y, -z));
	}

	/** Tapered feather: quad from root to tip, width shrinking to a point, slight depth offset per layer. */
	private void feather(WorldCtx ctx, GeoBatch b, double ax, double ay, double az, int side, float rx, float ry, float ang, float len, float width,
						 int cRoot, int cTip, float depth) {
		float dx = Mth.cos(ang), dy = Mth.sin(ang);
		float nx = -dy, ny = dx; // in-plane perpendicular
		float tipX = rx + dx * len, tipY = ry + dy * len;
		local(side, rx + nx * width * 0.5f, ry + ny * width * 0.5f, depth);
		float x1 = ctx.rx(ax) + v.x, y1 = ctx.ry(ay) + v.y, z1 = ctx.rz(az) + v.z;
		local(side, rx - nx * width * 0.5f, ry - ny * width * 0.5f, depth);
		float x2 = ctx.rx(ax) + v.x, y2 = ctx.ry(ay) + v.y, z2 = ctx.rz(az) + v.z;
		local(side, tipX - nx * width * 0.15f, tipY - ny * width * 0.15f, depth);
		float x3 = ctx.rx(ax) + v.x, y3 = ctx.ry(ay) + v.y, z3 = ctx.rz(az) + v.z;
		local(side, tipX, tipY, depth);
		b.quad(x1, y1, z1, x2, y2, z2, x3, y3, z3, ctx.rx(ax) + v.x, ctx.ry(ay) + v.y, ctx.rz(az) + v.z, cRoot, cRoot, cTip, cTip);
	}

	private void wingLine(WorldCtx ctx, double ax, double ay, double az, int side, float x1, float y1, float x2, float y2, int c, float w) {
		local(side, x1, y1, 0);
		double sx = ax + v.x, sy = ay + v.y, sz = az + v.z;
		local(side, x2, y2, 0);
		ctx.line(sx, sy, sz, ax + v.x, ay + v.y, az + v.z, c, c, w);
	}

	private void plate(WorldCtx ctx, double ax, double ay, double az, int side, float x1, float y1, float x2, float y2, float drop, int cTop, int cBottom) {
		float[] a = {x1, y1}, b = {x2, y2}, c = {x2 - 0.05f, y2 - drop}, d = {x1, y1 - drop * 0.8f};
		tri(ctx, ax, ay, az, side, a, b, c, cTop, cTop, cBottom);
		tri(ctx, ax, ay, az, side, a, c, d, cTop, cBottom, cBottom);
	}

	private void tri(WorldCtx ctx, double ax, double ay, double az, int side, float[] p1, float[] p2, float[] p3, int c1, int c2, int c3) {
		local(side, p1[0], p1[1], 0);
		float x1 = ctx.rx(ax) + v.x, y1 = ctx.ry(ay) + v.y, z1 = ctx.rz(az) + v.z;
		local(side, p2[0], p2[1], 0);
		float x2 = ctx.rx(ax) + v.x, y2 = ctx.ry(ay) + v.y, z2 = ctx.rz(az) + v.z;
		local(side, p3[0], p3[1], 0);
		ctx.fill.tri(x1, y1, z1, x2, y2, z2, ctx.rx(ax) + v.x, ctx.ry(ay) + v.y, ctx.rz(az) + v.z, c1, c2, c3);
	}

	@Override
	protected void onDisable() {
		sparkles.clear();
	}

	@Override
	public void onWorldChange() {
		sparkles.clear();
	}
}
