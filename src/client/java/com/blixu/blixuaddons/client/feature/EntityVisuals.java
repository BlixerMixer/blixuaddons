package com.blixu.blixuaddons.client.feature;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.combat.HitColor;
import com.blixu.blixuaddons.client.module.impl.player.SelfAura;
import com.blixu.blixuaddons.client.style.Colors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity material pipeline shared by Self Aura and Hit Color. Only changes how the
 * entity's normal model pass is drawn (plus optional extra passes of the same model), so
 * everything stays depth-tested: no entity is ever drawn through walls by this.
 */
public final class EntityVisuals {
	private EntityVisuals() {}

	public static final RenderStateDataKey<EntityMaterial> KEY = RenderStateDataKey.create(() -> "blixuaddons:material");
	public static final Identifier WHITE = BlixuAddons.id("textures/misc/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	public enum Kind {
		PLAYER("Players"), HOSTILE("Hostile mobs"), PASSIVE("Passive mobs"), OTHER("Other");
		public final String label;
		Kind(String l) { label = l; }
	}

	public static Kind kind(Entity e) {
		if (e instanceof Player) return Kind.PLAYER;
		if (e instanceof Enemy) return Kind.HOSTILE;
		if (e instanceof Animal || e instanceof net.minecraft.world.entity.AgeableMob) return Kind.PASSIVE;
		return Kind.OTHER;
	}

	public static boolean isSelf(Entity e) {
		return e == Minecraft.getInstance().player;
	}

	// ------------------------------------------------------------------ visibility

	private static final Map<Integer, Long> LOS = new HashMap<>();

	/** Line of sight from your eyes, cached per entity per tick. Used to gate any outline effect. */
	public static boolean visible(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return false;
		if (e == mc.player) return true;
		long tick = mc.level.getGameTime();
		Long cached = LOS.get(e.getId());
		if (cached != null && (cached >> 1) == tick) return (cached & 1) == 1;
		boolean vis = mc.player.hasLineOfSight(e);
		LOS.put(e.getId(), (tick << 1) | (vis ? 1 : 0));
		if (LOS.size() > 4096) LOS.clear();
		return vis;
	}

	public static void clearCaches() {
		LOS.clear();
	}

	// ------------------------------------------------------------------ outline (existing glow styles)

	/** Asked from Minecraft.shouldEntityAppearGlowing. */
	public static boolean wantsOutline(Entity e) {
		return outlineColor(e) != 0;
	}

	/** 0 = no outline from us. Self: always allowed. Others: only with line of sight. */
	public static int outlineColor(Entity e) {
		if (!(e instanceof LivingEntity living)) return 0;
		SelfAura self = ModuleManager.active(SelfAura.class);
		if (self != null && isSelf(e)) {
			int c = self.outlineColor(living);
			if (c != 0) return com.blixu.blixuaddons.client.feature.GlowStyle.encode(c, ((c >>> 24) & 255) / 255f, com.blixu.blixuaddons.client.feature.GlowStyle.SELF);
		}
		HitColor hit = ModuleManager.active(HitColor.class);
		if (hit != null && !isSelf(e)) {
			int c = hit.edgeColor(living);
			if (c != 0 && visible(e)) return com.blixu.blixuaddons.client.feature.GlowStyle.encode(c, 1f, com.blixu.blixuaddons.client.feature.GlowStyle.HIT);
		}
		return 0;
	}

	// ------------------------------------------------------------------ extract

	public static void extract(Entity e, EntityRenderState state) {
		if (!(e instanceof LivingEntity living)) return;
		EntityMaterial m = state.getData(KEY);
		if (m == null) {
			m = new EntityMaterial();
			state.setData(KEY, m);
		}
		m.clear();
		Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
		m.baseY = (float) (state.y - cam.y);
		m.height = Math.max(0.1f, state.boundingBoxHeight);
		m.centerX = (float) (state.x - cam.x);
		m.centerZ = (float) (state.z - cam.z);
		m.width = Math.max(0.1f, state.boundingBoxWidth);

		HitColor hit = ModuleManager.active(HitColor.class);
		if (hit != null) hit.apply(living, m);
	}

	// ------------------------------------------------------------------ submit

	public interface ModelSubmit {
		void submit(RenderType type, int light, int overlay, int tint, PoseStack pose);
	}

	/** Replaces the body submitModel call of LivingEntityRenderer.submit. */
	public static void submitBody(EntityRenderState state, Identifier texture, RenderType vanillaType, int light, int overlay, int tint,
								  PoseStack pose, ModelSubmit out) {
		EntityMaterial m = state.getData(KEY);
		if (m == null || !m.active()) {
			out.submit(vanillaType, light, overlay, tint, pose);
			return;
		}
		RenderType type = vanillaType;
		int t = tint;
		int l = m.emissive ? FULL_BRIGHT : light;
		int ov = m.suppressRedOverlay || m.hit > 0 ? (overlay & 0xFFFF) | (10 << 16) : overlay;

		switch (m.fill) {
			case NONE -> { }
			case TEXTURED_TINT -> {
				t = ARGB.multiply(tint, Colors.scale(Colors.setAlpha(m.color1, m.alpha), m.brightness));
				if (m.alpha < 0.999f) type = m.emissive ? com.blixu.blixuaddons.client.render.WorldPipelines.entityTranslucentEmissive(texture) : com.blixu.blixuaddons.client.render.WorldPipelines.entityTranslucent(texture);
			}
			case FLAT, GRADIENT -> {
				type = m.emissive ? com.blixu.blixuaddons.client.render.WorldPipelines.entityTranslucentEmissive(WHITE) : com.blixu.blixuaddons.client.render.WorldPipelines.entityTranslucent(WHITE);
				t = m.fill == EntityMaterial.Fill.FLAT ? Colors.scale(Colors.setAlpha(m.color1, m.alpha), m.brightness)
					: Colors.setAlpha(Colors.scale(0xFFFFFFFF, m.brightness), m.alpha);
			}
			case SOLID -> {
				type = com.blixu.blixuaddons.client.render.WorldPipelines.entitySolid(WHITE);
				t = Colors.scale(m.color1 | 0xFF000000, m.brightness);
			}
			case HOLOGRAPHIC -> {
				type = com.blixu.blixuaddons.client.render.WorldPipelines.entityTranslucentEmissive(texture);
				t = Colors.setAlpha(Colors.scale(m.color1, m.brightness), m.alpha);
				l = FULL_BRIGHT;
			}
		}
		if (m.wire && m.fill == EntityMaterial.Fill.NONE) {
			type = com.blixu.blixuaddons.client.render.WorldPipelines.entityTranslucent(texture);
		}
		if (m.hit > 0f) {
			t = Colors.lerp(t, (t & 0xFF000000) | (m.hitColor & 0xFFFFFF), m.hit);
		}
		VERTEX_PASS.put(state, type);
		out.submit(type, l, ov, t, pose);

		if (m.glint) out.submit(com.blixu.blixuaddons.client.render.WorldPipelines.entityGlint(WHITE), l, ov, -1, pose);
		if (m.shell || (m.hitShell && m.hit > 0f)) {
			int c = m.hitShell && m.hit > 0f ? Colors.withAlpha(m.hitColor, m.hit * 0.6f) : m.shellColor;
			float s = 1f + (m.shell ? m.shellScale : 0.05f);
			pose.pushPose();
			// Model space: y points down from ~1.5 blocks above the feet; scale around the body centre.
			pose.translate(0f, 0.75f, 0f);
			pose.scale(s, s, s);
			pose.translate(0f, -0.75f, 0f);
			out.submit(com.blixu.blixuaddons.client.render.WorldPipelines.entityTranslucentEmissive(texture), FULL_BRIGHT, ov, c, pose);
			pose.popPose();
		}
	}

	/** Which pass of this state gets per-vertex effects (so glint/shell passes don't). Identity map, cleared each frame. */
	private static final Map<Object, RenderType> VERTEX_PASS = new java.util.IdentityHashMap<>();
	private static final MaterialConsumer CONSUMER = new MaterialConsumer();

	public static void endFrame() {
		VERTEX_PASS.clear();
	}

	/** Called while the model is written to its buffer (ModelFeatureRenderer). */
	public static VertexConsumer wrap(@Nullable Object state, RenderType type, VertexConsumer buffer) {
		if (!(state instanceof EntityRenderState s)) return buffer;
		EntityMaterial m = s.getData(KEY);
		if (m == null || !m.perVertex() || VERTEX_PASS.get(state) != type) return buffer;
		return CONSUMER.set(buffer, m);
	}
}
