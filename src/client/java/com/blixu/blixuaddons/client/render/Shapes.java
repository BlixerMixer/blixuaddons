package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Geometry builders that work in whatever space the batch is in (camera-relative world
 * space, or an item's local space in first person). {@code right}/{@code up} span the
 * plane a flat shape faces.
 */
public final class Shapes {
	private Shapes() {}

	private static final int FAN = 10;
	private static final float[] COS = new float[FAN + 1];
	private static final float[] SIN = new float[FAN + 1];

	static {
		for (int i = 0; i <= FAN; i++) {
			COS[i] = Mth.cos((float) (i * Math.PI * 2 / FAN));
			SIN[i] = Mth.sin((float) (i * Math.PI * 2 / FAN));
		}
	}

	public static void billboard(GeoBatch b, float x, float y, float z, Vector3f right, Vector3f up, float s, float rot,
								 int c, Shape shape, float dirX, float dirY, float dirZ) {
		float cr = Mth.cos(rot), sr = Mth.sin(rot);
		// rotated in-plane axes
		float rxx = right.x * cr + up.x * sr, rxy = right.y * cr + up.y * sr, rxz = right.z * cr + up.z * sr;
		float uxx = up.x * cr - right.x * sr, uxy = up.y * cr - right.y * sr, uxz = up.z * cr - right.z * sr;
		switch (shape) {
			case SQUARE -> flatQuad(b, x, y, z, rxx, rxy, rxz, uxx, uxy, uxz, s, s, c);
			case DIAMOND -> {
				float w = s * 0.7f, h = s * 1.15f;
				b.quad(x + uxx * h, y + uxy * h, z + uxz * h, x - rxx * w, y - rxy * w, z - rxz * w,
					x - uxx * h, y - uxy * h, z - uxz * h, x + rxx * w, y + rxy * w, z + rxz * w, c);
			}
			case TRIANGLE -> b.tri(x + uxx * s, y + uxy * s, z + uxz * s,
				x - rxx * s * 0.87f - uxx * s * 0.5f, y - rxy * s * 0.87f - uxy * s * 0.5f, z - rxz * s * 0.87f - uxz * s * 0.5f,
				x + rxx * s * 0.87f - uxx * s * 0.5f, y + rxy * s * 0.87f - uxy * s * 0.5f, z + rxz * s * 0.87f - uxz * s * 0.5f, c, c, c);
			case SHARD -> b.tri(x + uxx * s * 1.6f, y + uxy * s * 1.6f, z + uxz * s * 1.6f,
				x - rxx * s * 0.35f, y - rxy * s * 0.35f, z - rxz * s * 0.35f,
				x - uxx * s * 0.6f + rxx * s * 0.3f, y - uxy * s * 0.6f + rxy * s * 0.3f, z - uxz * s * 0.6f + rxz * s * 0.3f,
				c, Colors.scale(c, 0.7f), Colors.scale(c, 0.85f));
			case CIRCLE -> fan(b, x, y, z, rxx, rxy, rxz, uxx, uxy, uxz, s, c, c);
			case SOFT -> fan(b, x, y, z, rxx, rxy, rxz, uxx, uxy, uxz, s, c, c & 0x00FFFFFF);
			case RING -> {
				for (int i = 0; i < FAN; i++) {
					float o = s, in = s * 0.62f;
					b.quad(x + (rxx * COS[i] + uxx * SIN[i]) * in, y + (rxy * COS[i] + uxy * SIN[i]) * in, z + (rxz * COS[i] + uxz * SIN[i]) * in,
						x + (rxx * COS[i] + uxx * SIN[i]) * o, y + (rxy * COS[i] + uxy * SIN[i]) * o, z + (rxz * COS[i] + uxz * SIN[i]) * o,
						x + (rxx * COS[i + 1] + uxx * SIN[i + 1]) * o, y + (rxy * COS[i + 1] + uxy * SIN[i + 1]) * o, z + (rxz * COS[i + 1] + uxz * SIN[i + 1]) * o,
						x + (rxx * COS[i + 1] + uxx * SIN[i + 1]) * in, y + (rxy * COS[i + 1] + uxy * SIN[i + 1]) * in, z + (rxz * COS[i + 1] + uxz * SIN[i + 1]) * in, c);
				}
			}
			case STAR -> {
				float in = s * 0.42f;
				for (int i = 0; i < 5; i++) {
					float a0 = (float) (Math.PI / 2 + i * Math.PI * 2 / 5);
					float am = a0 - (float) (Math.PI / 5), ap = a0 + (float) (Math.PI / 5);
					float ox = Mth.cos(a0) * s, oy = Mth.sin(a0) * s;
					float ix1 = Mth.cos(am) * in, iy1 = Mth.sin(am) * in, ix2 = Mth.cos(ap) * in, iy2 = Mth.sin(ap) * in;
					b.quad(x, y, z,
						x + rxx * ix1 + uxx * iy1, y + rxy * ix1 + uxy * iy1, z + rxz * ix1 + uxz * iy1,
						x + rxx * ox + uxx * oy, y + rxy * ox + uxy * oy, z + rxz * ox + uxz * oy,
						x + rxx * ix2 + uxx * iy2, y + rxy * ix2 + uxy * iy2, z + rxz * ix2 + uxz * iy2, c);
				}
			}
			case HEART -> {
				float r = s * 0.5f;
				fan(b, x - rxx * r * 0.9f + uxx * r * 0.4f, y - rxy * r * 0.9f + uxy * r * 0.4f, z - rxz * r * 0.9f + uxz * r * 0.4f,
					rxx, rxy, rxz, uxx, uxy, uxz, r, c, c);
				fan(b, x + rxx * r * 0.9f + uxx * r * 0.4f, y + rxy * r * 0.9f + uxy * r * 0.4f, z + rxz * r * 0.9f + uxz * r * 0.4f,
					rxx, rxy, rxz, uxx, uxy, uxz, r, c, c);
				b.tri(x - rxx * s * 0.93f + uxx * r * 0.2f, y - rxy * s * 0.93f + uxy * r * 0.2f, z - rxz * s * 0.93f + uxz * r * 0.2f,
					x - uxx * s * 1.05f, y - uxy * s * 1.05f, z - uxz * s * 1.05f,
					x + rxx * s * 0.93f + uxx * r * 0.2f, y + rxy * s * 0.93f + uxy * r * 0.2f, z + rxz * s * 0.93f + uxz * r * 0.2f, c, c, c);
			}
			case CUBE -> {
				// forward = right x up, spun by rot around up
				float fx = rxy * uxz - rxz * uxy, fy = rxz * uxx - rxx * uxz, fz = rxx * uxy - rxy * uxx;
				cube(b, x, y, z, rxx, rxy, rxz, uxx, uxy, uxz, fx, fy, fz, s * 0.6f, c);
			}
			case SPARK -> {
				// stretch along the motion direction, thin across it
				float len = Mth.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
				if (len < 1e-5f) {
					flatQuad(b, x, y, z, rxx, rxy, rxz, uxx, uxy, uxz, s * 0.3f, s * 0.3f, c);
					return;
				}
				float dx = dirX / len * s * 1.8f, dy = dirY / len * s * 1.8f, dz = dirZ / len * s * 1.8f;
				// across = dir x viewNormal(right x up)
				float nx = right.y * up.z - right.z * up.y, ny = right.z * up.x - right.x * up.z, nz = right.x * up.y - right.y * up.x;
				float ax = dy * nz - dz * ny, ay = dz * nx - dx * nz, az = dx * ny - dy * nx;
				float al = Mth.sqrt(ax * ax + ay * ay + az * az);
				if (al < 1e-6f) return;
				float w = s * 0.18f / al;
				ax *= w;
				ay *= w;
				az *= w;
				b.quad(x - dx - ax, y - dy - ay, z - dz - az, x - dx + ax, y - dy + ay, z - dz + az,
					x + dx + ax, y + dy + ay, z + dz + az, x + dx - ax, y + dy - ay, z + dz - az, c & 0x00FFFFFF, c & 0x00FFFFFF, c, c);
			}
		}
	}

	private static void flatQuad(GeoBatch b, float x, float y, float z, float rx, float ry, float rz, float ux, float uy, float uz,
								 float w, float h, int c) {
		b.quad(x - rx * w - ux * h, y - ry * w - uy * h, z - rz * w - uz * h,
			x + rx * w - ux * h, y + ry * w - uy * h, z + rz * w - uz * h,
			x + rx * w + ux * h, y + ry * w + uy * h, z + rz * w + uz * h,
			x - rx * w + ux * h, y - ry * w + uy * h, z - rz * w + uz * h, c);
	}

	/** Filled disc made of triangles; {@code edge} colour lets it fade out (soft glow). */
	public static void fan(GeoBatch b, float x, float y, float z, float rx, float ry, float rz, float ux, float uy, float uz,
						   float r, int center, int edge) {
		for (int i = 0; i < FAN; i++) {
			b.tri(x, y, z,
				x + (rx * COS[i] + ux * SIN[i]) * r, y + (ry * COS[i] + uy * SIN[i]) * r, z + (rz * COS[i] + uz * SIN[i]) * r,
				x + (rx * COS[i + 1] + ux * SIN[i + 1]) * r, y + (ry * COS[i + 1] + uy * SIN[i + 1]) * r, z + (rz * COS[i + 1] + uz * SIN[i + 1]) * r,
				center, edge, edge);
		}
	}

	/** Cube centred at x,y,z with half size h along the three given axes, faces slightly shaded. */
	public static void cube(GeoBatch b, float x, float y, float z, float ax, float ay, float az, float bx, float by, float bz,
							float cx, float cy, float cz, float h, int c) {
		int top = c, side = Colors.scale(c, 0.8f), dark = Colors.scale(c, 0.6f);
		float[][] p = CUBE;
		for (int i = 0; i < 8; i++) {
			float sa = (i & 1) == 0 ? -h : h, sb = (i & 2) == 0 ? -h : h, sc = (i & 4) == 0 ? -h : h;
			p[i][0] = x + ax * sa + bx * sb + cx * sc;
			p[i][1] = y + ay * sa + by * sb + cy * sc;
			p[i][2] = z + az * sa + bz * sb + cz * sc;
		}
		face(b, p, 0, 1, 3, 2, dark);   // -c
		face(b, p, 4, 6, 7, 5, top);    // +c
		face(b, p, 0, 4, 5, 1, side);   // -b
		face(b, p, 2, 3, 7, 6, top);    // +b
		face(b, p, 0, 2, 6, 4, side);   // -a
		face(b, p, 1, 5, 7, 3, dark);   // +a
	}

	private static void face(GeoBatch b, float[][] p, int i, int j, int k, int l, int c) {
		b.quad(p[i][0], p[i][1], p[i][2], p[j][0], p[j][1], p[j][2], p[k][0], p[k][1], p[k][2], p[l][0], p[l][1], p[l][2], c);
	}

	/**
	 * Ring in its own XZ plane rotated by pitch (X), yaw (Y) and roll (Z), radians.
	 * {@code inner}/{@code outer} colour the two edges (fade one to 0 alpha for a soft ring).
	 */
	public static void ring(GeoBatch b, float px, float py, float pz, float radius, float thickness,
							float pitch, float yaw, float roll, int inner, int outer, int segments) {
		float half = thickness / 2f;
		float r0 = radius - half, r1 = radius + half;
		float prevC = 1, prevS = 0;
		float cp = Mth.cos(pitch), sp = Mth.sin(pitch), cy = Mth.cos(yaw), sy = Mth.sin(yaw), cr = Mth.cos(roll), sr = Mth.sin(roll);
		for (int i = 1; i <= segments; i++) {
			float a = (float) (i * Math.PI * 2 / segments);
			float c = Mth.cos(a), s = Mth.sin(a);
			float[] v1 = rot(prevC * r0, prevS * r0, cp, sp, cy, sy, cr, sr, V1);
			float[] v2 = rot(prevC * r1, prevS * r1, cp, sp, cy, sy, cr, sr, V2);
			float[] v3 = rot(c * r1, s * r1, cp, sp, cy, sy, cr, sr, V3);
			float[] v4 = rot(c * r0, s * r0, cp, sp, cy, sy, cr, sr, V4);
			b.quad(px + v1[0], py + v1[1], pz + v1[2], px + v2[0], py + v2[1], pz + v2[2],
				px + v3[0], py + v3[1], pz + v3[2], px + v4[0], py + v4[1], pz + v4[2], inner, outer, outer, inner);
			prevC = c;
			prevS = s;
		}
	}

	// Scratch vectors: rendering is single-threaded, so these avoid per-vertex allocations.
	private static final float[] V1 = new float[3], V2 = new float[3], V3 = new float[3], V4 = new float[3];
	private static final float[][] CUBE = new float[8][3];

	/** Rotate the point (x, 0, z) by pitch, yaw, roll into {@code out}. */
	public static float[] rot(float x, float z, float cp, float sp, float cy, float sy, float cr, float sr, float[] out) {
		float y = -z * sp;
		float z1 = z * cp;
		float x2 = x * cy + z1 * sy;
		float z2 = -x * sy + z1 * cy;
		float x3 = x2 * cr - y * sr;
		float y3 = x2 * sr + y * cr;
		out[0] = x3;
		out[1] = y3;
		out[2] = z2;
		return out;
	}
}
