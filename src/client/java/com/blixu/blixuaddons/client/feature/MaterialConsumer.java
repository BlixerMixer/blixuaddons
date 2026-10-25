package com.blixu.blixuaddons.client.feature;

import com.blixu.blixuaddons.client.style.Colors;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Wraps the buffer an entity model is written into, so materials can vary per vertex:
 * real vertical/horizontal gradients, holographic scanlines, and wireframe (each face
 * replaced by thin strips along its edges, so it stays depth-tested like the model).
 * Models write whole vertices through the bulk addVertex, which is what we override.
 */
public final class MaterialConsumer implements VertexConsumer {
	private VertexConsumer out;
	private EntityMaterial m;
	private float time;

	private final float[] px = new float[4], py = new float[4], pz = new float[4], pu = new float[4], pv = new float[4];
	private final float[] nx = new float[4], ny = new float[4], nz = new float[4];
	private final int[] pc = new int[4], po = new int[4], pl = new int[4];
	private int n;

	public MaterialConsumer set(VertexConsumer out, EntityMaterial m) {
		this.out = out;
		this.m = m;
		this.time = Colors.seconds();
		this.n = 0;
		return this;
	}

	@Override
	public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nX, float nY, float nZ) {
		int c = color;
		if (m.fill == EntityMaterial.Fill.GRADIENT) {
			float t = switch (m.gradientDir) {
				case VERTICAL -> (y - m.baseY) / Math.max(0.1f, m.height);
				case HORIZONTAL -> 0.5f + ((x - m.centerX) + (z - m.centerZ)) / Math.max(0.2f, m.width * 2f);
				case DIAGONAL -> 0.5f * ((y - m.baseY) / Math.max(0.1f, m.height)) + 0.25f + ((x - m.centerX) + (z - m.centerZ)) / Math.max(0.4f, m.width * 4f);
			};
			c = ARGB.multiply(c, Colors.lerp(m.color1, m.color2, Mth.clamp(t, 0f, 1f)));
		} else if (m.fill == EntityMaterial.Fill.HOLOGRAPHIC) {
			float scan = 0.55f + 0.45f * Mth.sin((y - m.baseY) * 22f - time * m.scanSpeed * 6f);
			c = Colors.withAlpha(c, scan);
		}
		if (!m.wire) {
			out.addVertex(x, y, z, c, u, v, overlay, light, nX, nY, nZ);
			return;
		}
		px[n] = x; py[n] = y; pz[n] = z; pu[n] = u; pv[n] = v;
		nx[n] = nX; ny[n] = nY; nz[n] = nZ; pc[n] = c; po[n] = overlay; pl[n] = light;
		if (++n == 4) {
			n = 0;
			emitWire();
		}
	}

	private void emitWire() {
		float cx = (px[0] + px[1] + px[2] + px[3]) / 4f, cy = (py[0] + py[1] + py[2] + py[3]) / 4f, cz = (pz[0] + pz[1] + pz[2] + pz[3]) / 4f;
		float w = m.wireWidth;
		for (int i = 0; i < 4; i++) {
			int j = (i + 1) & 3;
			// inward = towards the face centre from this edge's midpoint
			float mx = (px[i] + px[j]) / 2f, my = (py[i] + py[j]) / 2f, mz = (pz[i] + pz[j]) / 2f;
			float dx = cx - mx, dy = cy - my, dz = cz - mz;
			float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1e-5f) continue;
			float k = Math.min(w, len) / len;
			dx *= k;
			dy *= k;
			dz *= k;
			out.addVertex(px[i], py[i], pz[i], pc[i], pu[i], pv[i], po[i], pl[i], nx[i], ny[i], nz[i]);
			out.addVertex(px[j], py[j], pz[j], pc[j], pu[j], pv[j], po[j], pl[j], nx[j], ny[j], nz[j]);
			out.addVertex(px[j] + dx, py[j] + dy, pz[j] + dz, pc[j], pu[j], pv[j], po[j], pl[j], nx[j], ny[j], nz[j]);
			out.addVertex(px[i] + dx, py[i] + dy, pz[i] + dz, pc[i], pu[i], pv[i], po[i], pl[i], nx[i], ny[i], nz[i]);
		}
	}

	// Plain per-attribute calls (rare for models) go straight through.
	@Override public VertexConsumer addVertex(float x, float y, float z) { out.addVertex(x, y, z); return this; }
	@Override public VertexConsumer setColor(int r, int g, int b, int a) { out.setColor(r, g, b, a); return this; }
	@Override public VertexConsumer setColor(int color) { out.setColor(color); return this; }
	@Override public VertexConsumer setUv(float u, float v) { out.setUv(u, v); return this; }
	@Override public VertexConsumer setUv1(int u, int v) { out.setUv1(u, v); return this; }
	@Override public VertexConsumer setUv2(int u, int v) { out.setUv2(u, v); return this; }
	@Override public VertexConsumer setNormal(float x, float y, float z) { out.setNormal(x, y, z); return this; }
	@Override public VertexConsumer setLineWidth(float width) { out.setLineWidth(width); return this; }
}
