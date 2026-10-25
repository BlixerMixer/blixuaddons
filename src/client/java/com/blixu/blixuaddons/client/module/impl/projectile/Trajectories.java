package com.blixu.blixuaddons.client.module.impl.projectile;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.render.Shape;
import com.blixu.blixuaddons.client.render.WorldCtx;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Predicted flight path for what you're holding, using the game's own launch speed,
 * gravity and drag. It is an estimate: wind, server lag, moving targets and other
 * server-side factors can change the real landing spot. Nothing about the throw changes.
 */
public class Trajectories extends Module {
	public enum Style { THIN("Thin line"), DOTTED("Dotted line"), GRADIENT("Gradient line"), PARTICLES("Particle trail"), RIBBON("Ribbon"), ENERGY("Animated energy"), CUSTOM("Custom");
		public final String label;
		Style(String l) { label = l; }
	}

	public enum Endpoint { RING("Ring"), CROSS("Cross"), BOX("Box"), DIAMOND("Diamond"), NONE("None");
		public final String label;
		Endpoint(String l) { label = l; }
	}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.GRADIENT, s -> s.label));
	public final BoolSetting cLine = add(new BoolSetting("c_line", "Custom: line", true).visibleWhen(() -> style.get() == Style.CUSTOM));
	public final BoolSetting cRibbon = add(new BoolSetting("c_ribbon", "Custom: ribbon", true).visibleWhen(() -> style.get() == Style.CUSTOM));
	public final BoolSetting cParticles = add(new BoolSetting("c_particles", "Custom: particles", false).visibleWhen(() -> style.get() == Style.CUSTOM));
	public final ColorSetting primary = add(new ColorSetting("primary", "Primary colour", 0x22D3EE));
	public final ColorSetting secondary = add(new ColorSetting("secondary", "Secondary colour", 0xD13BFF));
	public final BoolSetting perType = add(new BoolSetting("per_type", "Per-projectile colours", false));
	public final ColorSetting arrowColor = add(new ColorSetting("arrow_color", "Arrows", 0xFFFFFF).visibleWhen(perType::get));
	public final ColorSetting tridentColor = add(new ColorSetting("trident_color", "Trident", 0x22D3EE).visibleWhen(perType::get));
	public final ColorSetting pearlColor = add(new ColorSetting("pearl_color", "Ender pearl", 0x34F59A).visibleWhen(perType::get));
	public final ColorSetting throwableColor = add(new ColorSetting("throwable_color", "Snowball / egg", 0xE6F2FF).visibleWhen(perType::get));
	public final ColorSetting potionColor = add(new ColorSetting("potion_color", "Potions / XP bottles", 0xD13BFF).visibleWhen(perType::get));
	public final NumberSetting thickness = add(new NumberSetting("thickness", "Line thickness", 2.5, 1, 8, 0.25));
	public final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 0.9, 0.1, 1, 0.05));
	public final NumberSetting segments = add(new NumberSetting("segments", "Max simulation steps", 180, 20, 400, 10));
	public final NumberSetting maxDistance = add(new NumberSetting("max_distance", "Max prediction distance", 120, 10, 300, 5));
	public final EnumSetting<Endpoint> endpoint = add(new EnumSetting<>("endpoint", "Endpoint style", Endpoint.RING, e -> e.label));
	public final NumberSetting markerSize = add(new NumberSetting("marker_size", "Marker size", 0.35, 0.1, 1.5, 0.05));
	public final BoolSetting impactRing = add(new BoolSetting("impact_ring", "Animated impact ring", true));
	public final BoolSetting landingMarker = add(new BoolSetting("landing_marker", "Landing marker", true));
	public final BoolSetting alwaysDrawn = add(new BoolSetting("always_drawn", "Treat bows as fully drawn (Skyblock shortbows)", true));
	public final BoolSetting entityHits = add(new BoolSetting("entity_hits", "Stop at entities in the path", true));
	public final BoolSetting fromHand = add(new BoolSetting("from_hand", "Start the line at your hand (not the crosshair)", true));
	public final BoolSetting terminator = add(new BoolSetting("terminator", "Terminator: show all 3 arrows", true));
	public final NumberSetting terminatorSpread = add(new NumberSetting("terminator_spread", "Terminator spread (degrees)", 5, 1, 15, 0.5).visibleWhen(terminator::get));

	/** Sub-points per tick in the first ticks, so the curve is smooth where it leaves your hand. */
	private static final int NEAR_TICKS = 2, NEAR_SUB = 8;
	private final double[] px = new double[401 + NEAR_TICKS * NEAR_SUB], py = new double[px.length], pz = new double[px.length];

	public Trajectories() {
		super("trajectories", "Projectile Trajectories",
			"Predicted path and landing point for bows, crossbows, tridents, pearls, snowballs, eggs, potions and XP bottles.",
			Category.PROJECTILES, "arrow", false);
	}

	/** Launch parameters for an item: speed, gravity, pitch offset, colour. Speed 0 = not a projectile item. */
	private float[] launch(LocalPlayer p, ItemStack s) {
		if (s.is(Items.BOW)) {
			if (alwaysDrawn.get()) return new float[] {3f, 0.05f, 0f, 0};
			if (!p.isUsingItem()) return null;
			float pow = BowItem.getPowerForTime(p.getTicksUsingItem());
			return pow < 0.1f ? null : new float[] {pow * 3f, 0.05f, 0f, 0};
		}
		if (s.is(Items.CROSSBOW)) return CrossbowItem.isCharged(s) ? new float[] {3.15f, 0.05f, 0f, 0} : null;
		if (s.is(Items.TRIDENT)) return p.isUsingItem() && p.getTicksUsingItem() >= 10 ? new float[] {2.5f, 0.05f, 0f, 1} : null;
		if (s.is(Items.ENDER_PEARL)) return new float[] {1.5f, 0.03f, 0f, 2};
		if (s.is(Items.SNOWBALL) || s.is(Items.EGG) || s.is(Items.BLUE_EGG) || s.is(Items.BROWN_EGG)) return new float[] {1.5f, 0.03f, 0f, 3};
		if (s.is(Items.EXPERIENCE_BOTTLE)) return new float[] {0.7f, 0.07f, -20f, 4};
		if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return new float[] {0.5f, 0.05f, -20f, 4};
		return null;
	}

	private int typeColor(int type) {
		if (!perType.get()) return primary.argb();
		return switch (type) {
			case 1 -> tridentColor.argb();
			case 2 -> pearlColor.argb();
			case 3 -> throwableColor.argb();
			case 4 -> potionColor.argb();
			default -> arrowColor.argb();
		};
	}

	/** True for SkyBlock's Terminator: its name or SkyBlock id contains "terminator" (it fires 3 arrows at once). */
	static boolean isTerminator(ItemStack s) {
		if (s.isEmpty() || !s.is(Items.BOW)) return false;
		String name = net.minecraft.ChatFormatting.stripFormatting(s.getHoverName().getString());
		if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains("terminator")) return true;
		var data = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data == null) return false;
		var tag = data.copyTag();
		String id = tag.getStringOr("id", "");
		if (id.isEmpty()) id = tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", "");
		return id.toLowerCase(java.util.Locale.ROOT).contains("terminator");
	}

	@Override
	public void onWorldRender(WorldCtx ctx) {
		LocalPlayer p = ctx.mc.player;
		if (p == null || ctx.mc.level == null) return;
		ItemStack held = p.getMainHandItem();
		boolean offhand = false;
		float[] l = launch(p, held);
		if (l == null) {
			held = p.getOffhandItem();
			l = launch(p, held);
			offhand = true;
		}
		if (l == null) return;
		int color = typeColor((int) l[3]);
		if (terminator.get() && isTerminator(held)) {
			// the Terminator fires three arrows side by side: straight ahead and one to each side
			float spread = terminatorSpread.getF();
			for (float off : new float[] {-spread, 0, spread}) fly(ctx, p, l, off, offhand, color);
		} else {
			fly(ctx, p, l, 0, offhand, color);
		}
	}

	/** Simulates one projectile ({@code yawOffset} degrees off your aim) and draws its path. */
	private void fly(WorldCtx ctx, LocalPlayer p, float[] l, float yawOffset, boolean offhand, int color) {
		float yaw = p.getYRot(ctx.partialTick) + yawOffset, pitch = p.getXRot(ctx.partialTick);
		double x = Mth.lerp(ctx.partialTick, p.xo, p.getX()), y = Mth.lerp(ctx.partialTick, p.yo, p.getY()) + p.getEyeHeight() - 0.1, z = Mth.lerp(ctx.partialTick, p.zo, p.getZ());
		double vx = -Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
		double vy = -Mth.sin((pitch + l[2]) * Mth.DEG_TO_RAD);
		double vz = Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
		double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
		vx = vx / len * l[0];
		vy = vy / len * l[0];
		vz = vz / len * l[0];
		Vec3 move = p.getKnownMovement();
		vx += move.x;
		vz += move.z;
		if (!p.onGround()) vy += move.y;

		int steps = segments.getI();
		double maxD2 = maxDistance.get() * maxDistance.get();
		int n = 0;
		px[0] = x; py[0] = y; pz[0] = z;
		double sx = x, sy = y, sz = z;
		HitResult hit = null;
		for (int i = 0; i < steps && n < px.length - 1; i++) {
			Vec3 from = new Vec3(x, y, z), to = new Vec3(x + vx, y + vy, z + vz);
			BlockHitResult bh = ctx.mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (bh.getType() != HitResult.Type.MISS) to = bh.getLocation();
			if (entityHits.get() && i > 0) {
				AABB sweep = new AABB(from, to).inflate(1.0);
				EntityHitResult eh = ProjectileUtil.getEntityHitResult(ctx.mc.level, p, from, to, sweep,
					e -> !e.isSpectator() && e.isPickable() && e != p, 0.3f);
				if (eh != null) {
					hit = eh;
					to = eh.getLocation();
				}
			}
			if (hit == null && bh.getType() != HitResult.Type.MISS) hit = bh;
			// the first ticks get extra points (motion is straight within a tick), for a smooth start near you
			if (i < NEAR_TICKS) {
				for (int k = 1; k < NEAR_SUB && n < px.length - 2; k++) {
					double f = k / (double) NEAR_SUB;
					n++;
					px[n] = x + (to.x - x) * f; py[n] = y + (to.y - y) * f; pz[n] = z + (to.z - z) * f;
				}
			}
			x = to.x; y = to.y; z = to.z;
			n++;
			px[n] = x; py[n] = y; pz[n] = z;
			if (hit != null) break;
			boolean water = ctx.mc.level.getFluidState(net.minecraft.core.BlockPos.containing(x, y, z)).isSource();
			double drag = water ? 0.6 : 0.99;
			vx *= drag; vy *= drag; vz *= drag;
			vy -= l[1];
			double dx = x - sx, dy = y - sy, dz = z - sz;
			if (dx * dx + dy * dy + dz * dz > maxD2 || y < ctx.mc.level.getMinY() - 16) break;
		}
		if (fromHand.get()) startAtHand(ctx, p, n, offhand);
		draw(ctx, n, color, hit, fromHand.get() ? 1 : 2);
	}

	/**
	 * Visual only: moves the start of the drawn line from your eyes to where the item is held (a little
	 * right of and below the view, or left for the off hand), easing back onto the real path over the
	 * first blocks. The simulated flight and the landing point are untouched.
	 */
	private void startAtHand(WorldCtx ctx, LocalPlayer p, int n, boolean offhand) {
		float yaw = p.getYRot(ctx.partialTick) * Mth.DEG_TO_RAD, pitch = p.getXRot(ctx.partialTick) * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw) * Mth.cos(pitch), fy = -Mth.sin(pitch), fz = Mth.cos(yaw) * Mth.cos(pitch);
		double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw); // right of the view, level
		// up of the view = right x forward
		double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
		boolean rightHand = (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) != offhand;
		double side = rightHand ? 0.24 : -0.24;
		double ox = rx * side - ux * 0.16 + fx * 0.35, oy = -uy * 0.16 + fy * 0.35, oz = rz * side - uz * 0.16 + fz * 0.35;
		double blend = 4.0; // blocks over which the line eases from the hand onto the true path
		for (int i = 0; i <= n; i++) {
			double dx = px[i] - px[0], dy = py[i] - py[0], dz = pz[i] - pz[0];
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d >= blend) break;
			double k = 1 - d / blend;
			k = k * k * (3 - 2 * k);
			px[i] += ox * k;
			py[i] += oy * k;
			pz[i] += oz * k;
		}
	}

	@Override
	public Preview preview() {
		return Preview.SCENE;
	}

	@Override
	public float previewScale() {
		return 24f;
	}

	@Override
	public void renderPreview(WorldCtx ctx, float time) {
		// ground grid
		for (int i = -3; i <= 3; i++) {
			ctx.line(i, -1.2, -3, i, -1.2, 3, 0x30FFFFFF, 0x30FFFFFF, 1f);
			ctx.line(-3, -1.2, i, 3, -1.2, i, 0x30FFFFFF, 0x30FFFFFF, 1f);
		}
		// a simple arc launched from the left, landing on the grid
		double x = -2.6, y = 0.2, z = 0, vx = 0.28, vy = 0.16, vz = 0;
		int n = 0;
		px[0] = x; py[0] = y; pz[0] = z;
		while (n < 60 && y > -1.2) {
			x += vx; y += vy; z += vz;
			vy -= 0.02;
			n++;
			px[n] = x; py[n] = Math.max(y, -1.2); pz[n] = z;
		}
		HitResult fake = new BlockHitResult(new Vec3(px[n], py[n], pz[n]), Direction.UP, net.minecraft.core.BlockPos.ZERO, false);
		draw(ctx, n, typeColor(0), fake, 1);
	}

	private float nearTaper(int i) {
		double dx = px[i] - px[0], dy = py[i] - py[0], dz = pz[i] - pz[0];
		return (float) Mth.clamp(Math.sqrt(dx * dx + dy * dy + dz * dz) / 3.0, 0.15, 1.0);
	}

	/** {@code first}: index of the first segment to draw (2 skips the one inside your view). */
	private void draw(WorldCtx ctx, int n, int base, HitResult hit, int first) {
		float op = opacity.getF();
		float w = thickness.getF();
		int c1 = Colors.withAlpha(base, op), c2 = Colors.withAlpha(secondary.argb(), op);
		Style s = style.get();
		boolean line = s == Style.THIN || s == Style.GRADIENT || s == Style.DOTTED || (s == Style.CUSTOM && cLine.get());
		boolean ribbon = s == Style.RIBBON || s == Style.ENERGY || (s == Style.CUSTOM && cRibbon.get());
		boolean particles = s == Style.PARTICLES || (s == Style.CUSTOM && cParticles.get());
		for (int i = Math.max(1, first); i <= n; i++) {
			float t0 = (i - 1) / (float) n, t1 = i / (float) n;
			int a = s == Style.THIN ? c1 : Colors.lerp(c1, c2, t0), b = s == Style.THIN ? c1 : Colors.lerp(c1, c2, t1);
			if (line && (s != Style.DOTTED || i % 2 == 0)) ctx.line(px[i - 1], py[i - 1], pz[i - 1], px[i], py[i], pz[i], a, b, w);
			if (ribbon) {
				float k = s == Style.ENERGY ? 0.5f + 0.5f * Mth.sin(t1 * 30f - ctx.time * 10f) : 1f;
				// thin where it leaves your hand (it is right in front of the camera there), full width after ~3 blocks
				float w0 = 0.04f * w * 0.4f * nearTaper(i - 1), w1 = 0.04f * w * 0.4f * nearTaper(i);
				ctx.ribbon(ctx.glow, px[i - 1], py[i - 1], pz[i - 1], px[i], py[i], pz[i], w0, w1,
					Colors.withAlpha(a, 0.6f * k), Colors.withAlpha(b, 0.6f * k));
			}
			if (particles && i % 2 == 0) ctx.billboard(ctx.glow, px[i], py[i], pz[i], 0.06f + 0.02f * w, ctx.time * 2 + i, Colors.withAlpha(b, 0.85f), Shape.STAR);
		}
		if (hit == null || n < 1) return;
		double hx = px[n], hy = py[n], hz = pz[n];
		float m = markerSize.getF();
		int end = Colors.withAlpha(c2, op);
		float pitch = 0, roll = 0;
		if (hit instanceof BlockHitResult bh) {
			Direction d = bh.getDirection();
			if (d.getAxis() == Direction.Axis.X) roll = Mth.HALF_PI;
			else if (d.getAxis() == Direction.Axis.Z) pitch = Mth.HALF_PI;
		}
		if (landingMarker.get()) {
			switch (endpoint.get()) {
				case RING -> ctx.ring(ctx.glow, hx, hy, hz, m * 0.6f, m * 0.15f, pitch, roll, end, Colors.withAlpha(end, 0.2f), 32);
				case CROSS -> {
					ctx.line(hx - m * 0.5, hy, hz, hx + m * 0.5, hy, hz, end, end, w);
					ctx.line(hx, hy - m * 0.5, hz, hx, hy + m * 0.5, hz, end, end, w);
					ctx.line(hx, hy, hz - m * 0.5, hx, hy, hz + m * 0.5, end, end, w);
				}
				case BOX -> ctx.boxLines(hx - m * 0.3, hy - m * 0.3, hz - m * 0.3, hx + m * 0.3, hy + m * 0.3, hz + m * 0.3, end, end, w);
				case DIAMOND -> ctx.billboard(ctx.glow, hx, hy, hz, m * 0.4f, ctx.time, end, Shape.DIAMOND);
				case NONE -> { }
			}
		}
		if (impactRing.get()) {
			float pulse = (ctx.time * 1.2f) % 1f;
			ctx.ring(ctx.glow, hx, hy, hz, m * (0.3f + pulse * 1.2f), m * 0.08f, pitch, roll,
				Colors.withAlpha(c1, (1 - pulse) * op), Colors.withAlpha(c1, 0f), 32);
		}
		if (hit instanceof EntityHitResult eh) {
			Entity e = eh.getEntity();
			AABB b = e.getBoundingBox();
			ctx.boxCorners(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, end, w, 0.25f);
		}
	}
}
