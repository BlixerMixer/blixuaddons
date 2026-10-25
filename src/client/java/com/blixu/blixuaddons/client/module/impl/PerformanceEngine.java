package com.blixu.blixuaddons.client.module.impl;

import com.blixu.blixuaddons.client.feature.perf.FrameStats;
import com.blixu.blixuaddons.client.feature.perf.OcclusionCuller;
import com.blixu.blixuaddons.client.feature.perf.ParticleBudget;
import com.blixu.blixuaddons.client.feature.perf.PerfCompat;
import com.blixu.blixuaddons.client.hud.HudElement;
import com.blixu.blixuaddons.client.hud.HudPanel;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.module.setting.SectionSetting;
import com.blixu.blixuaddons.client.render.ParticlePool;
import com.blixu.blixuaddons.client.render.Ui;
import com.blixu.blixuaddons.client.render.UiFont;
import com.blixu.blixuaddons.client.render.WorldCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The optimization hub. It works WITH the performance mods you have installed instead of copying
 * them: Sodium keeps terrain, ImmediatelyFast vanilla batching, FerriteCore memory, BadOptimizations
 * lightmap caching, EntityCulling occlusion. What only we can do lives here: a shared budget for our
 * own particle effects, a fallback entity culler (when EntityCulling isn't installed), weather hiding
 * and a measured (never estimated) diagnostics panel.
 */
public class PerformanceEngine extends Module {
	private static final boolean EXT_CULLING = PerfCompat.ENTITY_CULLING;

	public final SectionSetting cullingHeader = add(new SectionSetting("culling_header", "Entity culling",
		EXT_CULLING ? "EntityCulling is installed and does this, so ours stays off." : "Skips entities fully hidden behind solid blocks."));
	public final BoolSetting entityCulling = add(new BoolSetting("entity_culling", "Occlusion culling", true).visibleWhen(() -> !EXT_CULLING));
	public final BoolSetting conservative = add(new BoolSetting("conservative", "Compatibility mode (never cull players or named entities)", true).visibleWhen(() -> !EXT_CULLING));
	public final NumberSetting rayBudget = add(new NumberSetting("ray_budget", "Visibility checks per frame", 600, 100, 2000, 50).visibleWhen(() -> !EXT_CULLING));
	public final BoolSetting debugCulling = add(new BoolSetting("debug_culling", "Show culled entities (debug boxes)", false).visibleWhen(() -> !EXT_CULLING));

	public final SectionSetting particleHeader = add(new SectionSetting("particle_header", "Our particle effects",
		"Hit particles, trails, relics and cosmetics share one budget. Only new spawns are thinned."));
	public final NumberSetting maxParticles = add(new NumberSetting("max_particles", "Max particles (all effects)", 6000, 500, 12000, 250));
	public final BoolSetting distanceThinning = add(new BoolSetting("distance_thinning", "Fewer particles far away", true));
	public final NumberSetting thinFrom = add(new NumberSetting("thin_from", "Thinning starts at (blocks)", 24, 8, 64, 1).visibleWhen(distanceThinning::get));
	public final NumberSetting thinTo = add(new NumberSetting("thin_to", "Fully thinned at (blocks)", 64, 16, 128, 1).visibleWhen(distanceThinning::get));
	public final NumberSetting cosmeticDensity = add(new NumberSetting("cosmetic_density", "Cosmetic effect density", 1, 0.1, 1, 0.05));
	public final BoolSetting autoQuality = add(new BoolSetting("auto_quality", "Auto quality (thin effects while FPS is low)", true));
	public final NumberSetting targetFps = add(new NumberSetting("target_fps", "Auto quality target FPS", 60, 30, 240, 5).visibleWhen(autoQuality::get));

	public final SectionSetting worldHeader = add(new SectionSetting("world_header", "World", "Visual only; sounds and gameplay are untouched."));
	public final BoolSetting hideWeather = add(new BoolSetting("hide_weather", "Hide rain and snow", false));

	public final SectionSetting diagHeader = add(new SectionSetting("diag_header", "Diagnostics",
		"Measured numbers only. Installed: " + String.join(", ", PerfCompat.installed())));
	public final BoolSetting diagnostics = add(new BoolSetting("diagnostics", "Show performance panel", false));
	public final NumberSetting posX = add(new NumberSetting("pos_x", "Panel X (%)", 1, 0, 100, 0.5).visibleWhen(() -> false));
	public final NumberSetting posY = add(new NumberSetting("pos_y", "Panel Y (%)", 20, 0, 100, 0.5).visibleWhen(() -> false));
	public final NumberSetting size = add(new NumberSetting("size", "Panel size", 1, 0.5, 2.5, 0.05).visibleWhen(() -> false));

	/** Read by the mixins every frame; plain statics so the hot paths never look the module up. */
	public static boolean cullingOn, conservativeOn, weatherHidden;

	private String[] lines = new String[0];
	private long nextRefresh;

	public PerformanceEngine() {
		super("performance_engine", "Performance Engine",
			"Shared particle budget, auto quality, entity culling fallback, weather toggle and a diagnostics panel.",
			Category.OPTIMIZATIONS, "optimizations", true);
	}

	@Override
	public void onTick(Minecraft client) {
		cullingOn = !EXT_CULLING && entityCulling.get();
		conservativeOn = conservative.get();
		weatherHidden = hideWeather.get();
		OcclusionCuller.debug = cullingOn && debugCulling.get();
		ParticleBudget.maxTotal = maxParticles.getI();
		ParticleBudget.distanceThinning = distanceThinning.get();
		ParticleBudget.thinFrom = thinFrom.getF();
		ParticleBudget.thinTo = Math.max(thinFrom.getF() + 1, thinTo.getF());
		ParticleBudget.cosmeticDensity = cosmeticDensity.getF();
		if (autoQuality.get() && FrameStats.samples() >= 60) {
			float avg = FrameStats.averageMs(60), target = 1000f / targetFps.getF();
			if (avg > target * 1.1f) ParticleBudget.autoScale = Math.max(0.3f, ParticleBudget.autoScale - 0.05f);
			else if (avg < target * 0.9f) ParticleBudget.autoScale = Math.min(1f, ParticleBudget.autoScale + 0.02f);
		} else {
			ParticleBudget.autoScale = 1f;
		}
	}

	@Override
	protected void onDisable() {
		cullingOn = weatherHidden = false;
		OcclusionCuller.debug = false;
		OcclusionCuller.clear();
		ParticleBudget.reset();
	}

	@Override
	public void onWorldChange() {
		OcclusionCuller.clear();
		FrameStats.reset();
	}

	@Override
	public void onWorldRender(WorldCtx ctx) {
		if (!OcclusionCuller.debug) return;
		for (int i = 0; i < OcclusionCuller.debugCount; i++) {
			AABB b = OcclusionCuller.DEBUG[i];
			edges(ctx, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, 0xFFFF4D5E);
		}
	}

	private static void edges(WorldCtx ctx, double x0, double y0, double z0, double x1, double y1, double z1, int c) {
		double[] xs = {x0, x1}, ys = {y0, y1}, zs = {z0, z1};
		for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
			ctx.xrayLine(x0, ys[a], zs[b], x1, ys[a], zs[b], c, c, 1.5f);
			ctx.xrayLine(xs[a], y0, zs[b], xs[a], y1, zs[b], c, c, 1.5f);
			ctx.xrayLine(xs[a], ys[b], z0, xs[a], ys[b], z1, c, c, 1.5f);
		}
	}

	// ---- diagnostics panel ----

	private void refresh(Minecraft mc) {
		Runtime rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
		float avg = FrameStats.averageMs(240), p95 = FrameStats.percentileMs(0.95f), p99 = FrameStats.percentileMs(0.99f);
		int entities = mc.level == null ? 0 : mc.level.getEntityCount();
		String culling = EXT_CULLING ? "by EntityCulling"
			: cullingOn ? FrameStats.lastCulled + " culled / " + FrameStats.lastConsidered + " checked, " + FrameStats.lastRays + " rays"
			: "off";
		lines = new String[] {
			"FPS " + mc.getFps() + "   frame " + f1(avg) + " ms",
			"p95 " + f1(p95) + " ms   p99 " + f1(p99) + " ms (1% low " + (p99 > 0 ? Math.round(1000f / p99) : 0) + " fps)",
			"Memory " + used + " / " + max + " MB",
			"Entities " + entities + "   culling: " + culling,
			"Our particles " + ParticlePool.liveTotal() + " / " + maxParticles.getI() + "   quality " + Math.round(ParticleBudget.autoScale * 100) + "%",
			"Our visuals CPU " + f2(FrameStats.visualsNanos / 1e6f) + " ms world, " + f2(FrameStats.hudNanos / 1e6f) + " ms HUD, " + FrameStats.lastBatches + " batches",
			"Backend " + PerfCompat.backend(),
		};
	}

	private static String f1(float v) { return String.valueOf(Math.round(v * 10) / 10f); }
	private static String f2(float v) { return String.valueOf(Math.round(v * 100) / 100f); }

	private float[] panelBox(float sw, float sh) {
		float k = size.getF();
		float w = 40;
		for (String l : lines) w = Math.max(w, Ui.width(l, UiFont.SMALL));
		return new float[] {sw * posX.getF() / 100f, sh * posY.getF() / 100f, (w + 12) * k, (Math.max(1, lines.length) * 10 + 8) * k};
	}

	@Override
	public void onHud(GuiGraphicsExtractor g, float dt) {
		if (!diagnostics.get()) return;
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (now >= nextRefresh) {
			nextRefresh = now + 250;
			refresh(mc);
		}
		float[] b = panelBox(g.guiWidth(), g.guiHeight());
		float k = size.getF();
		HudPanel.draw(g, b[0], b[1], b[2], b[3], 0, 1f, false);
		g.pose().pushMatrix();
		g.pose().translate(b[0] + 6 * k, b[1] + 4 * k);
		g.pose().scale(k, k);
		for (int i = 0; i < lines.length; i++) Ui.text(g, lines[i], 0, i * 10, i == 0 ? 0xFFECEEF5 : 0xFFB8BDCC, UiFont.SMALL);
		g.pose().popMatrix();
	}

	@Override
	public List<HudElement> hudElements() {
		return List.of(new HudElement() {
			public String name() { return "Performance panel"; }
			public boolean shown() { return isEnabled() && diagnostics.get(); }
			public float[] box(float sw, float sh) {
				if (lines.length == 0) refresh(Minecraft.getInstance());
				return panelBox(sw, sh);
			}
			public void moveTo(float x, float y, float sw, float sh) {
				posX.set((double) (x / sw * 100));
				posY.set((double) (y / sh * 100));
			}
			public void resize(float steps) { size.set(size.get() + steps * 0.05); }
		});
	}
}
