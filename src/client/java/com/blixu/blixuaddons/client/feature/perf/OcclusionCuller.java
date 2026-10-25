package com.blixu.blixuaddons.client.feature.perf;

import com.blixu.blixuaddons.client.feature.EntityVisuals;
import com.blixu.blixuaddons.client.feature.MobGlow;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Fallback entity occlusion culling, only used when the EntityCulling mod isn't installed.
 * Conservative by design: an entity is hidden only when EVERY line from the camera to its box
 * (centre, then the 8 corners, box grown by its motion) runs into a full opaque block. Anything
 * unusual (glowing, outlined by our modules, ridden, leashed, displays, the dragon, huge boxes) is
 * never culled, and with no answer yet (new entity, ray budget spent) the entity is drawn.
 * Results are cached per entity id for a few frames; occluded ones are re-checked every 2 frames so
 * something stepping out from behind a wall reappears almost at once.
 */
public final class OcclusionCuller {
	private OcclusionCuller() {}

	/** entity id -> (frame tested << 1) | visible */
	private static final Int2IntOpenHashMap CACHE = new Int2IntOpenHashMap();
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	private static final int VISIBLE_RECHECK = 6, OCCLUDED_RECHECK = 2;

	private static int frame;
	private static int raysLeft;
	private static int blockedFrame = -1;
	private static boolean cameraBlocked;

	// culled boxes this frame, for the debug view (bounded)
	public static final int DEBUG_MAX = 256;
	public static final AABB[] DEBUG = new AABB[DEBUG_MAX];
	public static int debugCount;
	public static boolean debug;

	static {
		CACHE.defaultReturnValue(-1);
	}

	public static void beginFrame(int rayBudget) {
		frame++;
		raysLeft = rayBudget;
		debugCount = 0;
		if ((frame & 255) == 0) {
			// drop entries for entities we haven't seen in a while (keeps the map bounded)
			CACHE.int2IntEntrySet().removeIf(e -> frame - (e.getIntValue() >>> 1) > 600);
		}
	}

	public static void clear() {
		CACHE.clear();
		debugCount = 0;
	}

	public static int cached() { return CACHE.size(); }

	/** Entities we never cull (special rendering, see-through outlines, the things you interact with). */
	public static boolean eligible(Entity e, boolean conservative) {
		Minecraft mc = Minecraft.getInstance();
		if (e == mc.getCameraEntity() || e == mc.player) return false;
		if (mc.player != null && (e.hasIndirectPassenger(mc.player) || mc.player.getVehicle() == e)) return false;
		if (e.isCurrentlyGlowing() || MobGlow.shouldGlow(e) || EntityVisuals.wantsOutline(e)) return false;
		if (e instanceof Display || e instanceof EnderDragon) return false;
		if (e instanceof Leashable l && l.getLeashHolder() != null) return false;
		if (conservative) {
			// name tags show through walls, and players are what you most want to see appear on time
			if (e instanceof Player || e.isCustomNameVisible()) return false;
			if (e instanceof ArmorStand) return false;
		}
		return true;
	}

	/** @return true when the entity is confidently hidden behind opaque blocks */
	public static boolean occluded(Entity e, AABB cullBox, double cx, double cy, double cz) {
		Level level = e.level();
		if (blockedFrame != frame) {
			blockedFrame = frame;
			// camera inside a solid block (spectator, freecam clipping): every ray would fail; don't cull
			cameraBlocked = level.getBlockState(POS.set(Mth.floor(cx), Mth.floor(cy), Mth.floor(cz))).isSolidRender();
		}
		if (cameraBlocked) return false;
		if (cullBox.getSize() > 6.0 || cullBox.hasNaN()) return false;
		if (cullBox.distanceToSqr(new net.minecraft.world.phys.Vec3(cx, cy, cz)) < 9.0) return false;

		int id = e.getId();
		int v = CACHE.get(id);
		boolean known = v != -1;
		boolean wasVisible = known && (v & 1) == 1;
		if (known && frame - (v >>> 1) < (wasVisible ? VISIBLE_RECHECK : OCCLUDED_RECHECK)) return !wasVisible;
		if (raysLeft <= 0) return known && !wasVisible;

		var m = e.getDeltaMovement();
		AABB box = cullBox.inflate(0.25).expandTowards(m.x * 3, m.y * 3, m.z * 3);
		boolean visible = anyRayReaches(level, box, cx, cy, cz);
		CACHE.put(id, (frame << 1) | (visible ? 1 : 0));
		if (!visible && debug && debugCount < DEBUG_MAX) DEBUG[debugCount++] = box;
		return !visible;
	}

	private static boolean anyRayReaches(Level level, AABB b, double cx, double cy, double cz) {
		Opaque o = (x, y, z) -> level.getBlockState(POS.set(x, y, z)).isSolidRender();
		if (ray(o, cx, cy, cz, (b.minX + b.maxX) * 0.5, (b.minY + b.maxY) * 0.5, (b.minZ + b.maxZ) * 0.5)) return true;
		for (int i = 0; i < 8; i++) {
			if (raysLeft <= 0) return true; // out of budget mid-test: assume visible
			double x = (i & 1) == 0 ? b.minX : b.maxX;
			double y = (i & 2) == 0 ? b.minY : b.maxY;
			double z = (i & 4) == 0 ? b.minZ : b.maxZ;
			if (ray(o, cx, cy, cz, x, y, z)) return true;
		}
		return false;
	}

	/** Voxel walk (Amanatides-Woo) from the camera to the point; false if a full opaque block is in between. */
	interface Opaque {
		boolean at(int x, int y, int z);
	}

	static boolean ray(Opaque opaque, double x0, double y0, double z0, double x1, double y1, double z1) {
		raysLeft--;
		FrameStats.rays(1);
		int x = Mth.floor(x0), y = Mth.floor(y0), z = Mth.floor(z0);
		int tx = Mth.floor(x1), ty = Mth.floor(y1), tz = Mth.floor(z1);
		double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
		int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
		double ddx = dx != 0 ? Math.abs(1 / dx) : Double.MAX_VALUE;
		double ddy = dy != 0 ? Math.abs(1 / dy) : Double.MAX_VALUE;
		double ddz = dz != 0 ? Math.abs(1 / dz) : Double.MAX_VALUE;
		double mx = dx != 0 ? (dx > 0 ? x + 1 - x0 : x0 - x) * ddx : Double.MAX_VALUE;
		double my = dy != 0 ? (dy > 0 ? y + 1 - y0 : y0 - y) * ddy : Double.MAX_VALUE;
		double mz = dz != 0 ? (dz > 0 ? z + 1 - z0 : z0 - z) * ddz : Double.MAX_VALUE;
		int steps = Math.abs(tx - x) + Math.abs(ty - y) + Math.abs(tz - z);
		for (int s = 0; s < steps; s++) {
			if (mx < my && mx < mz) { x += sx; mx += ddx; }
			else if (my < mz) { y += sy; my += ddy; }
			else { z += sz; mz += ddz; }
			if (x == tx && y == ty && z == tz) return true; // the target's own block never hides it
			if (opaque.at(x, y, z)) return false;
		}
		return true;
	}

	/** Startup check of the voxel walk against a one-block wall at (5, 0, 0). */
	public static void selfCheck() {
		Opaque wall = (x, y, z) -> x == 5 && y == 0 && z == 0;
		int saved = raysLeft;
		check(!ray(wall, 0.5, 0.5, 0.5, 10.5, 0.5, 0.5), "straight through the wall");
		check(ray(wall, 0.5, 0.5, 0.5, 10.5, 6.5, 0.5), "passes above the wall");
		check(ray(wall, 0.5, 0.5, 0.5, 5.5, 0.5, 0.5), "target inside the wall block");
		check(ray(wall, 10.5, 0.5, 3.5, 0.5, 0.5, 3.5), "parallel row beside the wall");
		check(!ray(wall, 10.5, 0.5, 0.5, 0.5, 0.5, 0.5), "reverse direction");
		raysLeft = saved;
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new IllegalStateException("OcclusionCuller ray self-check failed: " + what);
	}
}
