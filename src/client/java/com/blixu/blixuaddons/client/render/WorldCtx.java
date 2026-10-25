package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What a module gets each frame to draw in the world. All public draw helpers take
 * WORLD coordinates and convert to camera-relative themselves. Everything is depth
 * tested, so nothing here shows through walls.
 */
public final class WorldCtx {
	public final Minecraft mc = Minecraft.getInstance();
	/** Translucent fill (alpha blended). */
	public final GeoBatch fill = new GeoBatch(false, 120_000);
	/** Additive glow (bright where it overlaps). */
	public final GeoBatch glow = new GeoBatch(false, 120_000);
	public final GeoBatch lines = new GeoBatch(true, 60_000);
	/** Solid shapes drawn into the entity-outline buffer, so they get the Mob Highlight glow style. Ignores walls: callers must check visibility. */
	public final GeoBatch outline = new GeoBatch(false, 40_000);
	/** Through-wall lines and additive glow (boss tracer, slayer head lines/labels): no depth test. */
	public final GeoBatch xrayLines = new GeoBatch(true, 8_000);
	public final GeoBatch xrayGlow = new GeoBatch(false, 20_000);

	public Vec3 cam = Vec3.ZERO;
	public final Vector3f right = new Vector3f(1, 0, 0);
	public final Vector3f up = new Vector3f(0, 1, 0);
	public final Vector3f forward = new Vector3f(0, 0, -1);
	public float partialTick;
	public float dt;
	public float depthFar = 512f;
	public float time;
	/** True while drawing a menu preview (no world: modules must not read the level/player). */
	public boolean preview;

	public void begin(Vec3 cameraPos, Quaternionf orientation, float partialTick, float dt, float depthFar) {
		fill.clear();
		glow.clear();
		lines.clear();
		outline.clear();
		xrayLines.clear();
		xrayGlow.clear();
		this.cam = cameraPos;
		orientation.transform(1, 0, 0, right);
		orientation.transform(0, 1, 0, up);
		orientation.transform(0, 0, -1, forward);
		this.partialTick = partialTick;
		this.dt = dt;
		this.depthFar = depthFar;
		this.time = Colors.seconds();
	}

	public float rx(double x) { return (float) (x - cam.x); }
	public float ry(double y) { return (float) (y - cam.y); }
	public float rz(double z) { return (float) (z - cam.z); }

	/** True if the point is in front of the camera (cheap cull for markers/labels). */
	public boolean inFront(double x, double y, double z) {
		return rx(x) * forward.x + ry(y) * forward.y + rz(z) * forward.z > 0;
	}

	// ------------------------------------------------------------------ lines

	public void line(double x1, double y1, double z1, double x2, double y2, double z2, int c1, int c2, float width) {
		lines.line(rx(x1), ry(y1), rz(z1), rx(x2), ry(y2), rz(z2), c1, c2, width);
	}

	/** A line visible through walls. */
	public void xrayLine(double x1, double y1, double z1, double x2, double y2, double z2, int c1, int c2, float width) {
		xrayLines.line(rx(x1), ry(y1), rz(z1), rx(x2), ry(y2), rz(z2), c1, c2, width);
	}

	public void boxLines(AABB b, int c, float w) {
		boxLines(b, c, c, w);
	}

	/** Box edges; bottom edges get {@code bottom}, top get {@code top}, verticals blend. */
	public void boxLines(AABB b, int bottom, int top, float w) {
		boxLines(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, bottom, top, w);
	}

	public void boxLines(double x0, double y0, double z0, double x1, double y1, double z1, int bottom, int top, float w) {
		line(x0, y0, z0, x1, y0, z0, bottom, bottom, w);
		line(x1, y0, z0, x1, y0, z1, bottom, bottom, w);
		line(x1, y0, z1, x0, y0, z1, bottom, bottom, w);
		line(x0, y0, z1, x0, y0, z0, bottom, bottom, w);
		line(x0, y1, z0, x1, y1, z0, top, top, w);
		line(x1, y1, z0, x1, y1, z1, top, top, w);
		line(x1, y1, z1, x0, y1, z1, top, top, w);
		line(x0, y1, z1, x0, y1, z0, top, top, w);
		line(x0, y0, z0, x0, y1, z0, bottom, top, w);
		line(x1, y0, z0, x1, y1, z0, bottom, top, w);
		line(x1, y0, z1, x1, y1, z1, bottom, top, w);
		line(x0, y0, z1, x0, y1, z1, bottom, top, w);
	}

	/** Short L-shaped brackets on each corner; {@code frac} = bracket length as share of the edge. */
	public void boxCorners(AABB b, int c, float w, float frac) {
		boxCorners(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, c, w, frac);
	}

	public void boxCorners(double x0, double y0, double z0, double x1, double y1, double z1, int c, float w, float frac) {
		double lx = (x1 - x0) * frac, ly = (y1 - y0) * frac, lz = (z1 - z0) * frac;
		for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) for (int k = 0; k < 2; k++) {
			double x = i == 0 ? x0 : x1, y = j == 0 ? y0 : y1, z = k == 0 ? z0 : z1;
			double sx = i == 0 ? lx : -lx, sy = j == 0 ? ly : -ly, sz = k == 0 ? lz : -lz;
			line(x, y, z, x + sx, y, z, c, c, w);
			line(x, y, z, x, y + sy, z, c, c, w);
			line(x, y, z, x, y, z + sz, c, c, w);
		}
	}

	// ------------------------------------------------------------------ fills

	/** Six faces. {@code bottom}/{@code top} give a vertical gradient. */
	public void boxFill(GeoBatch b, AABB box, int bottom, int top) {
		boxFill(b, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, bottom, top);
	}

	public void boxFill(GeoBatch b, double ax, double ay, double az, double bx, double by, double bz, int bottom, int top) {
		float x0 = rx(ax), y0 = ry(ay), z0 = rz(az), x1 = rx(bx), y1 = ry(by), z1 = rz(bz);
		b.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bottom);
		b.quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, top);
		b.quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, bottom, top, top, bottom);
		b.quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, bottom, bottom, top, top);
		b.quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, bottom, bottom, top, top);
		b.quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, bottom, top, top, bottom);
	}

	/** Ring lying in the XZ plane, tilted by pitch (around X) then roll (around Z), radians. */
	public void ring(GeoBatch b, double cx, double cy, double cz, float radius, float thickness, float pitch, float roll,
					 int inner, int outer, int segments) {
		Shapes.ring(b, rx(cx), ry(cy), rz(cz), radius, thickness, pitch, 0f, roll, inner, outer, segments);
	}

	// ------------------------------------------------------------------ billboards

	/** Camera-facing shape centred on a world point. {@code dirX/Y/Z} only matter for SPARK. */
	public void billboard(GeoBatch b, double x, double y, double z, float size, float rotation, int color, Shape shape,
						  float dirX, float dirY, float dirZ) {
		Shapes.billboard(b, rx(x), ry(y), rz(z), right, up, size, rotation, color, shape, dirX, dirY, dirZ);
	}

	public void billboard(GeoBatch b, double x, double y, double z, float size, float rotation, int color, Shape shape) {
		billboard(b, x, y, z, size, rotation, color, shape, 0, 1, 0);
	}

	/** A ribbon segment between two world points, facing the camera, with per-end width/colour. */
	public void ribbon(GeoBatch b, double x1, double y1, double z1, double x2, double y2, double z2,
					   float w1, float w2, int c1, int c2) {
		float ax = rx(x1), ay = ry(y1), az = rz(z1), bx = rx(x2), by = ry(y2), bz = rz(z2);
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		// side = dir x (point - camera), so the strip turns its face to the camera
		float vx = (ax + bx) / 2, vy = (ay + by) / 2, vz = (az + bz) / 2;
		float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
		float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1e-6f) return;
		sx /= len;
		sy /= len;
		sz /= len;
		b.quad(ax - sx * w1, ay - sy * w1, az - sz * w1, ax + sx * w1, ay + sy * w1, az + sz * w1,
			bx + sx * w2, by + sy * w2, bz + sz * w2, bx - sx * w2, by - sy * w2, bz - sz * w2, c1, c1, c2, c2);
	}
}
