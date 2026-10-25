package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * A tiny triangle mesh (built once, drawn every frame without allocating). Vertices are in
 * local units; draw() rotates/scales/places them and shades faces by a fixed light direction
 * so solid cosmetics read as 3D. Edges can be drawn as lines for an outline look.
 */
public final class Mesh {
	private float[] tri = new float[0];   // 9 floats per triangle
	private float[] tv = new float[0];    // per-vertex gradient parameter (0..1), 3 per triangle
	private float[] edges = new float[0]; // 6 floats per edge
	private int triCount;
	private int edgeCount;

	private static final Vector3f A = new Vector3f(), B = new Vector3f(), C = new Vector3f(), N = new Vector3f();
	private static final Vector3f LIGHT = new Vector3f(0.35f, 0.85f, 0.4f).normalize();

	public Mesh tri(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {
		return tri(ax, ay, az, bx, by, bz, cx, cy, cz, 0.5f, 0.5f, 0.5f);
	}

	/** Triangle with a gradient parameter per corner (0 = colour A, 1 = colour B). */
	public Mesh tri(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float ta, float tb, float tc) {
		if (triCount * 9 + 9 > tri.length) {
			tri = Arrays.copyOf(tri, Math.max(36, tri.length * 2));
			tv = Arrays.copyOf(tv, tri.length / 3);
		}
		int i = triCount * 9;
		tri[i] = ax; tri[i + 1] = ay; tri[i + 2] = az;
		tri[i + 3] = bx; tri[i + 4] = by; tri[i + 5] = bz;
		tri[i + 6] = cx; tri[i + 7] = cy; tri[i + 8] = cz;
		int t = triCount * 3;
		tv[t] = ta; tv[t + 1] = tb; tv[t + 2] = tc;
		triCount++;
		return this;
	}

	public Mesh quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
					 float ta, float tb, float tc, float td) {
		tri(ax, ay, az, bx, by, bz, cx, cy, cz, ta, tb, tc);
		return tri(ax, ay, az, cx, cy, cz, dx, dy, dz, ta, tc, td);
	}

	public Mesh edge(float ax, float ay, float az, float bx, float by, float bz) {
		if (edgeCount * 6 + 6 > edges.length) edges = Arrays.copyOf(edges, Math.max(36, edges.length * 2));
		int i = edgeCount * 6;
		edges[i] = ax; edges[i + 1] = ay; edges[i + 2] = az;
		edges[i + 3] = bx; edges[i + 4] = by; edges[i + 5] = bz;
		edgeCount++;
		return this;
	}

	public int triangles() { return triCount; }

	/**
	 * @param shade 0 = flat colour, 1 = full directional shading
	 * @param edgeColor 0 = no outline
	 */
	public void draw(WorldCtx ctx, GeoBatch faces, double x, double y, double z, Quaternionf rot, float scale,
					 int colorA, int colorB, float shade, int edgeColor, float edgeWidth) {
		float ox = ctx.rx(x), oy = ctx.ry(y), oz = ctx.rz(z);
		for (int t = 0; t < triCount; t++) {
			int i = t * 9;
			rot.transform(A.set(tri[i], tri[i + 1], tri[i + 2]).mul(scale));
			rot.transform(B.set(tri[i + 3], tri[i + 4], tri[i + 5]).mul(scale));
			rot.transform(C.set(tri[i + 6], tri[i + 7], tri[i + 8]).mul(scale));
			float k = 1f;
			if (shade > 0f) {
				float ux = B.x - A.x, uy = B.y - A.y, uz = B.z - A.z, vx = C.x - A.x, vy = C.y - A.y, vz = C.z - A.z;
				N.set(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx);
				float len = N.length();
				if (len > 1e-7f) {
					// Two-sided: faces lit from either side look the same, so winding doesn't matter.
					float d = Math.abs(N.dot(LIGHT) / len);
					k = Mth.lerp(shade, 1f, 0.45f + 0.65f * d);
				}
			}
			int ca = Colors.scale(Colors.lerp(colorA, colorB, tv[t * 3]), k);
			int cb = Colors.scale(Colors.lerp(colorA, colorB, tv[t * 3 + 1]), k);
			int cc = Colors.scale(Colors.lerp(colorA, colorB, tv[t * 3 + 2]), k);
			faces.tri(ox + A.x, oy + A.y, oz + A.z, ox + B.x, oy + B.y, oz + B.z, ox + C.x, oy + C.y, oz + C.z, ca, cb, cc);
		}
		if (edgeColor != 0 && edgeCount > 0) {
			for (int e = 0; e < edgeCount; e++) {
				int i = e * 6;
				rot.transform(A.set(edges[i], edges[i + 1], edges[i + 2]).mul(scale));
				rot.transform(B.set(edges[i + 3], edges[i + 4], edges[i + 5]).mul(scale));
				ctx.lines.line(ox + A.x, oy + A.y, oz + A.z, ox + B.x, oy + B.y, oz + B.z, edgeColor, edgeColor, edgeWidth);
			}
		}
	}

	// ------------------------------------------------------------------ builders (original shapes)

	/** Octahedron ("floating diamond"), height 2*h, waist radius r. */
	public static Mesh diamond(float r, float h) {
		return bipyramid(4, r, h, h);
	}

	/** n-sided bipyramid: crystals (n=6, tall top). Gradient runs bottom (0) → top (1). */
	public static Mesh bipyramid(int n, float r, float top, float bottom) {
		Mesh m = new Mesh();
		for (int i = 0; i < n; i++) {
			float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
			float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
			m.tri(0, top, 0, x0, 0, z0, x1, 0, z1, 1, 0.5f, 0.5f);
			m.tri(0, -bottom, 0, x1, 0, z1, x0, 0, z0, 0, 0.5f, 0.5f);
			m.edge(0, top, 0, x0, 0, z0).edge(0, -bottom, 0, x0, 0, z0).edge(x0, 0, z0, x1, 0, z1);
		}
		return m;
	}

	/** Square-section ring in the XZ plane. {@code arc} < 2π gives a partial ring; {@code taper} thins the ends (crescent). */
	public static Mesh ring(float radius, float thick, int segs, float arc, boolean taper) {
		Mesh m = new Mesh();
		for (int i = 0; i < segs; i++) {
			float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
			float a0 = arc * t0 - arc / 2, a1 = arc * t1 - arc / 2;
			float h0 = thick * (taper ? Mth.sin(t0 * Mth.PI) * 0.9f + 0.1f : 1f) / 2f;
			float h1 = thick * (taper ? Mth.sin(t1 * Mth.PI) * 0.9f + 0.1f : 1f) / 2f;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			// inner/outer radius and top/bottom
			float ri0 = radius - h0, ro0 = radius + h0, ri1 = radius - h1, ro1 = radius + h1;
			m.quad(c0 * ro0, h0, s0 * ro0, c1 * ro1, h1, s1 * ro1, c1 * ri1, h1, s1 * ri1, c0 * ri0, h0, s0 * ri0, t0, t1, t1, t0);   // top
			m.quad(c0 * ri0, -h0, s0 * ri0, c1 * ri1, -h1, s1 * ri1, c1 * ro1, -h1, s1 * ro1, c0 * ro0, -h0, s0 * ro0, t0, t1, t1, t0); // bottom
			m.quad(c0 * ro0, -h0, s0 * ro0, c1 * ro1, -h1, s1 * ro1, c1 * ro1, h1, s1 * ro1, c0 * ro0, h0, s0 * ro0, t0, t1, t1, t0);  // outer
			m.quad(c0 * ri0, h0, s0 * ri0, c1 * ri1, h1, s1 * ri1, c1 * ri1, -h1, s1 * ri1, c0 * ri0, -h0, s0 * ri0, t0, t1, t1, t0);  // inner
			m.edge(c0 * ro0, h0, s0 * ro0, c1 * ro1, h1, s1 * ro1).edge(c0 * ri0, h0, s0 * ri0, c1 * ri1, h1, s1 * ri1);
		}
		return m;
	}

	/** Extruded n-point star (thickness along Y). */
	public static Mesh star(int points, float outer, float inner, float thick) {
		Mesh m = new Mesh();
		int n = points * 2;
		float h = thick / 2f;
		for (int i = 0; i < n; i++) {
			float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
			float r0 = i % 2 == 0 ? outer : inner, r1 = i % 2 == 0 ? inner : outer;
			float x0 = Mth.cos(a0) * r0, z0 = Mth.sin(a0) * r0, x1 = Mth.cos(a1) * r1, z1 = Mth.sin(a1) * r1;
			m.tri(0, h, 0, x0, 0, z0, x1, 0, z1, 1, 0.3f, 0.3f);
			m.tri(0, -h, 0, x1, 0, z1, x0, 0, z0, 1, 0.3f, 0.3f);
			m.edge(x0, 0, z0, x1, 0, z1);
		}
		return m;
	}

	/**
	 * Star of David standing upright: two interlocked equilateral triangle frames (one pointing up, one
	 * down) in the XY plane, {@code thick} deep along Z. {@code r} = distance from the centre to a point.
	 */
	public static Mesh starOfDavid(float r, float bar, float thick) {
		Mesh m = new Mesh();
		addTriangleFrame(m, r, Mth.HALF_PI, bar, thick);
		addTriangleFrame(m, r, -Mth.HALF_PI, bar, thick);
		return m;
	}

	/** A triangle-shaped frame in the XY plane: the area between a triangle and a smaller one inside it, with all four faces. */
	private static void addTriangleFrame(Mesh m, float r, float rot, float bar, float thick) {
		float h = thick / 2f;
		float ri = r - bar * 2f; // an equilateral triangle's inradius is half its circumradius
		for (int i = 0; i < 3; i++) {
			float a0 = rot + Mth.TWO_PI * i / 3, a1 = rot + Mth.TWO_PI * (i + 1) / 3;
			float ox0 = Mth.cos(a0) * r, oy0 = Mth.sin(a0) * r, ox1 = Mth.cos(a1) * r, oy1 = Mth.sin(a1) * r;
			float ix0 = Mth.cos(a0) * ri, iy0 = Mth.sin(a0) * ri, ix1 = Mth.cos(a1) * ri, iy1 = Mth.sin(a1) * ri;
			m.quad(ox0, oy0, h, ix0, iy0, h, ix1, iy1, h, ox1, oy1, h, 1, 0.7f, 0.7f, 1);        // front
			m.quad(ox0, oy0, -h, ox1, oy1, -h, ix1, iy1, -h, ix0, iy0, -h, 1, 1, 0.7f, 0.7f);    // back
			m.quad(ox0, oy0, -h, ox0, oy0, h, ox1, oy1, h, ox1, oy1, -h, 0.4f, 0.4f, 0.4f, 0.4f); // outer side
			m.quad(ix0, iy0, h, ix0, iy0, -h, ix1, iy1, -h, ix1, iy1, h, 0.2f, 0.2f, 0.2f, 0.2f); // inner side
			m.edge(ox0, oy0, h, ox1, oy1, h).edge(ix0, iy0, h, ix1, iy1, h);
		}
	}

	/** Irregular tetrahedral shard. */
	public static Mesh shard(float s) {
		Mesh m = new Mesh();
		float[][] p = {{0, 1.6f * s, 0}, {-0.45f * s, -0.4f * s, -0.3f * s}, {0.5f * s, -0.5f * s, -0.2f * s}, {0.05f * s, -0.35f * s, 0.55f * s}};
		int[][] f = {{0, 1, 2}, {0, 2, 3}, {0, 3, 1}, {1, 3, 2}};
		float[] tv = {1, 0, 0, 0};
		for (int[] t : f) m.tri(p[t[0]][0], p[t[0]][1], p[t[0]][2], p[t[1]][0], p[t[1]][1], p[t[1]][2], p[t[2]][0], p[t[2]][1], p[t[2]][2], tv[t[0]], tv[t[1]], tv[t[2]]);
		for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) m.edge(p[i][0], p[i][1], p[i][2], p[j][0], p[j][1], p[j][2]);
		return m;
	}

	/** Cone (apex up) with {@code segs} sides; {@code base} > 0 closes the bottom. */
	public static Mesh cone(int segs, float radius, float height, float yBase, boolean closeBottom) {
		Mesh m = new Mesh();
		addCone(m, segs, radius, 0, height, yBase, closeBottom);
		return m;
	}

	/** Frustum/cone ring from radius r0 at y0 to r1 at y1 (r1 = 0 = point). */
	public static void addCone(Mesh m, int segs, float r0, float r1, float height, float y0, boolean closeBottom) {
		float y1 = y0 + height;
		for (int i = 0; i < segs; i++) {
			float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			if (r1 <= 0) m.tri(0, y1, 0, c0 * r0, y0, s0 * r0, c1 * r0, y0, s1 * r0, 1, 0, 0);
			else m.quad(c0 * r0, y0, s0 * r0, c1 * r0, y0, s1 * r0, c1 * r1, y1, s1 * r1, c0 * r1, y1, s0 * r1, 0, 0, 1, 1);
			if (closeBottom) m.tri(0, y0, 0, c1 * r0, y0, s1 * r0, c0 * r0, y0, s0 * r0, 0, 0, 0);
			m.edge(c0 * r0, y0, s0 * r0, c1 * r0, y0, s1 * r0);
		}
	}

	/** Axis-aligned box centred at (cx, cy, cz). */
	public static void addBox(Mesh m, float cx, float cy, float cz, float hx, float hy, float hz, float tBottom, float tTop) {
		float x0 = cx - hx, x1 = cx + hx, y0 = cy - hy, y1 = cy + hy, z0 = cz - hz, z1 = cz + hz;
		m.quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, tTop, tTop, tTop, tTop);
		m.quad(x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, tBottom, tBottom, tBottom, tBottom);
		m.quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, tBottom, tBottom, tTop, tTop);
		m.quad(x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, tBottom, tTop, tTop, tBottom);
		m.quad(x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, tBottom, tTop, tTop, tBottom);
		m.quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, tBottom, tBottom, tTop, tTop);
	}
}
