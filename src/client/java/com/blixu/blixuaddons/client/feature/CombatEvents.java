package com.blixu.blixuaddons.client.feature;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.combat.DamageTags;
import com.blixu.blixuaddons.client.module.impl.combat.HitParticles;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Local combat feedback events. Purely observational: nothing here changes attacks,
 * cooldowns or packets — it only tells visual modules that something happened.
 */
public final class CombatEvents {
	private CombatEvents() {}

	private static final Int2LongOpenHashMap HURT_AT = new Int2LongOpenHashMap();
	private static final Int2FloatOpenHashMap HEALTH = new Int2FloatOpenHashMap();
	private static long lastAttackNanos;
	private static int lastAttackId = -1;

	/** The local player swung at {@code target} (fires client-side before the attack packet, unchanged). */
	public static void onLocalAttack(Entity target) {
		lastAttackNanos = System.nanoTime();
		lastAttackId = target.getId();
		HitParticles particles = ModuleManager.active(HitParticles.class);
		if (particles != null) particles.onAttack(target);
	}

	/** The server told this client that {@code e} took damage (vanilla damage event). */
	public static void onHurt(LivingEntity e) {
		HURT_AT.put(e.getId(), System.nanoTime());
		HitParticles particles = ModuleManager.active(HitParticles.class);
		if (particles != null) particles.onHurt(e, wasMyHit(e));
	}

	/** True if you attacked this entity in the last ~0.6 s (so effects can be limited to your hits). */
	public static boolean wasMyHit(Entity e) {
		return e.getId() == lastAttackId && System.nanoTime() - lastAttackNanos < 600_000_000L;
	}

	/** Seconds since the last damage event for this entity, or -1 if none recorded. */
	public static float secondsSinceHurt(int entityId) {
		long t = HURT_AT.getOrDefault(entityId, 0L);
		return t == 0 ? -1f : (System.nanoTime() - t) / 1e9f;
	}

	/** Each tick: compare synced health to spot damage/healing amounts for damage numbers. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		DamageTags tags = ModuleManager.active(DamageTags.class);
		double range = tags != null ? tags.range.get() : 0;
		if (tags == null) {
			if (!HEALTH.isEmpty()) HEALTH.clear();
			return;
		}
		double r2 = range * range;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || living.isRemoved()) continue;
			if (living.distanceToSqr(mc.player) > r2) continue;
			float hp = living.getHealth();
			int id = living.getId();
			if (HEALTH.containsKey(id)) {
				float before = HEALTH.get(id);
				float delta = hp - before;
				if (Math.abs(delta) > 0.01f) tags.onHealthChange(living, delta);
			}
			HEALTH.put(id, hp);
		}
		if (HEALTH.size() > 2048) HEALTH.clear();
		if (HURT_AT.size() > 2048) HURT_AT.clear();
	}

	public static void clear() {
		HURT_AT.clear();
		HEALTH.clear();
		lastAttackId = -1;
	}
}
