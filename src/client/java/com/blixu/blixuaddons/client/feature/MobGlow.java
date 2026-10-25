package com.blixu.blixuaddons.client.feature;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.MobHighlight;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Decides which mobs get the outline, in what colour, and which glow style draws it. */
public final class MobGlow {
	private MobGlow() {}

	private static final Identifier VANILLA_OUTLINE = Identifier.withDefaultNamespace("entity_outline");

	private static final it.unimi.dsi.fastutil.ints.IntOpenHashSet GLOWING = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
	private static String parsed;
	private static final java.util.List<String> NAMES = new java.util.ArrayList<>();

	/**
	 * Glows owned by other features (Slayer visuals: the boss in its phase colour, Voidlings, beacons,
	 * heads): entity id -> ARGB for this frame, 0 = none. Checked before Mob Highlight's own list.
	 */
	public static java.util.function.IntUnaryOperator featureGlow = id -> 0;
	/** Look id for a feature glow (which design it uses). */
	public static java.util.function.IntUnaryOperator featureLook = id -> GlowStyle.SLAYER;

	public static boolean shouldGlow(Entity entity) {
		if (featureGlow.applyAsInt(entity.getId()) != 0) return true;
		MobHighlight m = ModuleManager.find(MobHighlight.class);
		return m != null && m.isEnabled() && GLOWING.contains(entity.getId());
	}

	public static void clear() {
		GLOWING.clear();
	}

	/**
	 * Once per tick: a mob glows if its own name matches the list, or if a name tag (the
	 * invisible armor stands servers like Hypixel float above mobs) right above it matches.
	 */
	public static void refresh(net.minecraft.client.Minecraft mc, MobHighlight m) {
		GLOWING.clear();
		if (mc.level == null || mc.player == null) return;
		if (!m.names.get().equals(parsed)) {
			parsed = m.names.get();
			NAMES.clear();
			for (String raw : parsed.split(",")) {
				String n = raw.trim().toLowerCase(java.util.Locale.ROOT);
				if (!n.isEmpty()) NAMES.add(n);
			}
		}
		if (NAMES.isEmpty()) return;
		double r2 = m.range.get() * m.range.get();
		java.util.List<net.minecraft.world.entity.decoration.ArmorStand> tags = new java.util.ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.distanceToSqr(mc.player) > r2) continue;
			if (e instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
				if (stand.hasCustomName() && matches(stand.getCustomName().getString())) tags.add(stand);
				continue;
			}
			if (!(e instanceof net.minecraft.world.entity.LivingEntity) || e == mc.player) continue;
			if (matches(e.getName().getString())) GLOWING.add(e.getId());
		}
		// Each matching tag marks the closest mob just below it.
		for (var tag : tags) {
			Entity best = null;
			double bestD = 9;
			for (Entity e : mc.level.getEntities(tag, tag.getBoundingBox().inflate(1.5, 4, 1.5))) {
				if (!(e instanceof net.minecraft.world.entity.LivingEntity) || e instanceof net.minecraft.world.entity.decoration.ArmorStand || e == mc.player) continue;
				if (e.getY() > tag.getY() + 0.5) continue;
				double dx = e.getX() - tag.getX(), dz = e.getZ() - tag.getZ(), dy = tag.getY() - (e.getY() + e.getBbHeight());
				double d = dx * dx + dz * dz + dy * dy * 0.25;
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
			if (best != null) GLOWING.add(best.getId());
		}
	}

	private static boolean matches(String raw) {
		// Strip colour codes and compare case-insensitively.
		String s = com.blixu.blixuaddons.client.feature.slayer.Sidebar.strip(raw).toLowerCase(java.util.Locale.ROOT);
		for (String n : NAMES) if (s.contains(n)) return true;
		return false;
	}

	/** Colour as 0xRRGGBB; every glow style builds its palette from it. */
	public static int colorFor(Entity entity) {
		int f = featureGlow.applyAsInt(entity.getId());
		if (f != 0) return GlowStyle.encode(f, 1f, featureLook.applyAsInt(entity.getId()));
		MobHighlight m = ModuleManager.get(MobHighlight.class);
		return GlowStyle.encode(m.color.argb(), 1f, GlowStyle.MOB);
	}

	/** Which post-effect draws outlines this frame: always the shared glow style. Anything that isn't the outline effect passes through. */
	public static Identifier postChainFor(Identifier requested) {
		return VANILLA_OUTLINE.equals(requested) ? GlowStyle.current().postChain : requested;
	}
}
