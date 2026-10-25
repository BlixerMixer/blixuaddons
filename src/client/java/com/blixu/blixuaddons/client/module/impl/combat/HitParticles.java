package com.blixu.blixuaddons.client.module.impl.combat;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.render.ParticlePool;
import com.blixu.blixuaddons.client.render.Shape;
import com.blixu.blixuaddons.client.render.WorldCtx;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

/** Local hit feedback particles. Spawned from your attack / the client's damage event; attacks are untouched. */
public class HitParticles extends Module {
	public enum Type {
		STARS("Stars", Shape.STAR), SPARKS("Sparks", Shape.SPARK), CIRCLES("Circles", Shape.CIRCLE), HEARTS("Hearts", Shape.HEART),
		DIAMONDS("Diamonds", Shape.DIAMOND), CUBES("Small cubes", Shape.CUBE), SHARDS("Geometric shards", Shape.SHARD),
		ENERGY("Energy fragments", Shape.SOFT), SMOKE("Smoke puffs", Shape.SOFT), CUSTOM("Custom mix", Shape.STAR);
		public final String label;
		public final Shape shape;
		Type(String l, Shape s) { label = l; shape = s; }
	}

	public enum Trigger { ATTACK("When you attack"), HURT("When it takes damage"), BOTH("Both");
		public final String label;
		Trigger(String l) { label = l; }
	}

	public enum Preset { SUBTLE("Subtle"), MINIMAL("Minimal"), COLORFUL("Colourful"), HIGH_ENERGY("High energy"), CUSTOM("Custom");
		public final String label;
		Preset(String l) { label = l; }
	}

	public final EnumSetting<Preset> preset = add(new EnumSetting<>("preset", "Preset", Preset.COLORFUL, p -> p.label));
	public final EnumSetting<Type> type = add(new EnumSetting<>("type", "Particle type", Type.STARS, t -> t.label));
	public final EnumSetting<Shape> mixA = add(new EnumSetting<Shape>("mix_a", "Mix shape 1", Shape.STAR, s -> s.label).visibleWhen(() -> type.get() == Type.CUSTOM));
	public final EnumSetting<Shape> mixB = add(new EnumSetting<Shape>("mix_b", "Mix shape 2", Shape.SPARK, s -> s.label).visibleWhen(() -> type.get() == Type.CUSTOM));
	public final EnumSetting<Shape> mixC = add(new EnumSetting<Shape>("mix_c", "Mix shape 3", Shape.DIAMOND, s -> s.label).visibleWhen(() -> type.get() == Type.CUSTOM));
	public final EnumSetting<Trigger> trigger = add(new EnumSetting<>("trigger", "Trigger", Trigger.HURT, t -> t.label));
	public final BoolSetting onlyMine = add(new BoolSetting("only_mine", "Only my hits", true));
	public final ColorSetting primary = add(new ColorSetting("primary", "Primary colour", 0xFACC15));
	public final ColorSetting secondary = add(new ColorSetting("secondary", "Secondary colour", 0xFF7A1A));
	public final ColorSetting accent = add(new ColorSetting("accent", "Accent colour", 0xFFFFFF));
	public final BoolSetting gradient = add(new BoolSetting("gradient", "Gradient over lifetime", true));
	public final NumberSetting count = add(new NumberSetting("count", "Particle count", 12, 1, 60, 1));
	public final NumberSetting size = add(new NumberSetting("size", "Particle size", 0.12, 0.02, 0.5, 0.01));
	public final NumberSetting lifetime = add(new NumberSetting("lifetime", "Lifetime (s)", 0.7, 0.1, 3, 0.05));
	public final NumberSetting spread = add(new NumberSetting("spread", "Spread radius", 0.4, 0, 2, 0.05));
	public final NumberSetting horizontal = add(new NumberSetting("horizontal", "Horizontal speed", 2.2, 0, 8, 0.1));
	public final NumberSetting vertical = add(new NumberSetting("vertical", "Vertical speed", 2.5, -4, 8, 0.1));
	public final NumberSetting gravity = add(new NumberSetting("gravity", "Gravity", 6, -5, 20, 0.5));
	public final EnumSetting<ParticlePool.Fade> fade = add(new EnumSetting<>("fade", "Fade curve", ParticlePool.Fade.SMOOTH, f -> f.label));
	public final NumberSetting rotation = add(new NumberSetting("rotation", "Rotation speed", 4, 0, 20, 0.5));
	public final NumberSetting glow = add(new NumberSetting("glow", "Glow intensity", 0.8, 0, 1, 0.05));
	public final NumberSetting cooldown = add(new NumberSetting("cooldown", "Min time between bursts (s)", 0.05, 0, 1, 0.01));
	public final NumberSetting maxParticles = add(new NumberSetting("max", "Max active particles", 400, 20, 2000, 10));

	private final ParticlePool pool = new ParticlePool(2000);
	private final Random random = new Random();
	private long lastBurst;

	public HitParticles() {
		super("hit_particles", "Hit Particles",
			"Stars, sparks, hearts, shards and more when you land hits. Visual only.",
			Category.COMBAT, "particles", false);
		preset.onChange(this::applyPreset);
	}

	private void applyPreset(Preset p) {
		switch (p) {
			case SUBTLE -> { count.setSilently(5.0); size.setSilently(0.07); lifetime.setSilently(0.45); horizontal.setSilently(1.2); vertical.setSilently(1.5); glow.setSilently(0.4); type.setSilently(Type.CIRCLES); }
			case MINIMAL -> { count.setSilently(3.0); size.setSilently(0.05); lifetime.setSilently(0.35); horizontal.setSilently(0.8); vertical.setSilently(1.0); glow.setSilently(0.2); type.setSilently(Type.DIAMONDS); }
			case COLORFUL -> { count.setSilently(12.0); size.setSilently(0.12); lifetime.setSilently(0.7); horizontal.setSilently(2.2); vertical.setSilently(2.5); glow.setSilently(0.8); type.setSilently(Type.STARS); }
			case HIGH_ENERGY -> { count.setSilently(30.0); size.setSilently(0.1); lifetime.setSilently(0.9); horizontal.setSilently(4.5); vertical.setSilently(4.0); glow.setSilently(1.0); type.setSilently(Type.SPARKS); }
			case CUSTOM -> { }
		}
		ConfigManager.save();
	}

	public void onAttack(Entity target) {
		if (trigger.get() != Trigger.HURT) burst(target);
	}

	public void onHurt(LivingEntity e, boolean mine) {
		if (trigger.get() == Trigger.ATTACK) return;
		if (onlyMine.get() && !mine) return;
		burst(e);
	}

	private Shape shapeFor(int i) {
		if (type.get() != Type.CUSTOM) return type.get().shape;
		return switch (i % 3) { case 0 -> mixA.get(); case 1 -> mixB.get(); default -> mixC.get(); };
	}

	private void burst(Entity e) {
		long now = System.nanoTime();
		if (now - lastBurst < cooldown.get() * 1e9) return;
		lastBurst = now;
		burstAt(pool, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), e.getBbHeight());
	}

	private void burstAt(ParticlePool pool, double cx, double cy, double cz, float height) {
		int n = count.getI();
		int limit = maxParticles.getI();
		boolean smoke = type.get() == Type.SMOKE;
		for (int i = 0; i < n; i++) {
			float ang = random.nextFloat() * Mth.TWO_PI;
			float r = random.nextFloat() * spread.getF();
			float hs = horizontal.getF() * (0.4f + random.nextFloat() * 0.6f);
			float vs = vertical.getF() * (0.3f + random.nextFloat() * 0.7f);
			int start = pick(i, n);
			int end = gradient.get() ? Colors.withAlpha(accent.argb(), 0.9f) : start;
			if (smoke) {
				start = Colors.lerp(start, 0xFF9097AB, 0.6f);
				end = Colors.withAlpha(0xFF5C6378, 0.6f);
			}
			float s = size.getF() * (0.6f + random.nextFloat() * 0.8f);
			pool.spawn(cx + Mth.cos(ang) * r, cy + (random.nextFloat() - 0.5f) * height * 0.5f, cz + Mth.sin(ang) * r,
				Mth.cos(ang) * hs, smoke ? vs * 0.3f : vs, Mth.sin(ang) * hs,
				lifetime.getF() * (0.7f + random.nextFloat() * 0.6f), s, smoke ? s * 2.2f : s * 0.4f,
				start, end, shapeFor(i), glow.getF() > 0.3f, smoke ? -0.5f : gravity.getF(), smoke ? 2.5f : 1.2f,
				random.nextFloat() * Mth.TWO_PI, (random.nextFloat() - 0.5f) * 2 * rotation.getF(), limit);
		}
	}

	private int pick(int i, int n) {
		if (!gradient.get()) return primary.argb();
		return Colors.lerp(primary.argb(i / (float) n), secondary.argb(), random.nextFloat());
	}

	@Override
	public void onWorldRender(WorldCtx ctx) {
		pool.fade = fade.get();
		pool.update(ctx.dt);
		pool.draw(ctx, 0.25f + 0.75f * Math.max(glow.getF(), 0.35f));
	}

	// Menu preview: a target dummy that gets "hit" every 1.2 s with the current settings.
	private final ParticlePool previewPool = new ParticlePool(800, ParticlePool.Kind.PREVIEW);
	private float lastPreviewBurst = -10f, lastPreviewTime;

	@Override
	public Preview preview() {
		return Preview.SCENE;
	}

	@Override
	public void renderPreview(WorldCtx ctx, float time) {
		float dt = Math.max(0f, Math.min(0.1f, time - lastPreviewTime));
		lastPreviewTime = time;
		if (time - lastPreviewBurst > 1.2f || time < lastPreviewBurst) {
			lastPreviewBurst = time;
			burstAt(previewPool, 0, 0.2, 0, 1.6f);
		}
		previewPool.fade = fade.get();
		previewPool.update(dt);
		// dummy: two stacked boxes
		float flash = Math.max(0f, 1f - (time - lastPreviewBurst) * 4f);
		int body = com.blixu.blixuaddons.client.style.Colors.lerp(0xFF3A3F52, 0xFFFFFFFF, flash * 0.5f);
		ctx.boxFill(ctx.fill, -0.3, -0.8, -0.3, 0.3, 0.4, 0.3, body, com.blixu.blixuaddons.client.style.Colors.scale(body, 1.2f));
		ctx.boxFill(ctx.fill, -0.22, 0.4, -0.22, 0.22, 0.84, 0.22, body, body);
		ctx.boxLines(-0.3, -0.8, -0.3, 0.3, 0.4, 0.3, 0xFF5C6378, 0xFF5C6378, 1f);
		previewPool.draw(ctx, 0.25f + 0.75f * Math.max(glow.getF(), 0.35f));
	}

	@Override
	protected void onDisable() {
		pool.clear();
	}

	@Override
	public void onWorldChange() {
		pool.clear();
	}
}
