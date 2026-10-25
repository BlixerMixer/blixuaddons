package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.client.anim.FrameClock;
import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.feature.CombatEvents;
import com.blixu.blixuaddons.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import com.blixu.blixuaddons.client.feature.perf.FrameStats;
import com.blixu.blixuaddons.client.feature.perf.OcclusionCuller;
import com.blixu.blixuaddons.client.feature.perf.ParticleBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.InteractionResult;

/** Wires modules into Minecraft's frame: world drawing, attack feedback, world changes, shutdown. */
public final class VisualsHooks {
	/** Batches that actually had geometry this frame (submit of an empty batch is a no-op). */
	private static int batches(WorldCtx c) {
		int n = 0;
		for (GeoBatch b : BATCHES) if (!b.isEmpty()) n++;
		return n;
	}

	private VisualsHooks() {}

	private static final WorldCtx CTX = new WorldCtx();
	private static final GeoBatch[] BATCHES = {CTX.fill, CTX.glow, CTX.lines, CTX.outline, CTX.xrayGlow, CTX.xrayLines};

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null) return;
			long t0 = System.nanoTime();
			FrameClock.tick();
			PostFx.markWorldRendered();
			CameraRenderState cam = context.levelState().cameraRenderState;
			CTX.begin(cam.pos, cam.orientation, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), FrameClock.dt(), cam.depthFar);
			FrameStats.frame();
			ParticleBudget.camX = cam.pos.x;
			ParticleBudget.camY = cam.pos.y;
			ParticleBudget.camZ = cam.pos.z;
			ModuleManager.forEnabled(m -> m.onWorldRender(CTX));
			var out = context.submitNodeCollector();
			var pose = context.poseStack();
			var cosmic = ModuleManager.active(com.blixu.blixuaddons.client.module.impl.world.CosmicSky.class);
			if (cosmic != null) cosmic.submit(out.order(-100), pose, cam.depthFar);
			CTX.fill.submitSplit(out, pose, WorldPipelines.SOLID, WorldPipelines.fill());
			CTX.glow.submit(out, pose, WorldPipelines.GLOW);
			CTX.lines.submit(out, pose, WorldPipelines.lines());
			CTX.outline.submit(out, pose, WorldPipelines.OUTLINE);
			CTX.xrayGlow.submit(out, pose, WorldPipelines.XRAY_GLOW);
			CTX.xrayLines.submit(out, pose, WorldPipelines.XRAY_LINES);
			FrameStats.lastBatches = batches(CTX);
			FrameStats.visualsNanos = System.nanoTime() - t0;
			// after the debug boxes were drawn: fresh culling budget for the next frame's entities
			OcclusionCuller.beginFrame(ModuleManager.get(com.blixu.blixuaddons.client.module.impl.PerformanceEngine.class).rayBudget.getI());
		});

		// Per-vertex material passes are matched per frame; clear once the frame's models are built.
		LevelRenderEvents.START_MAIN.register(context -> com.blixu.blixuaddons.client.feature.EntityVisuals.endFrame());

		// Block Overlay draws its own outline in COLLECT_SUBMITS; skip vanilla's then.
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
			var overlay = ModuleManager.active(com.blixu.blixuaddons.client.module.impl.world.BlockOverlay.class);
			return overlay == null || overlay.allowVanilla();
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) CombatEvents.onLocalAttack(entity);
			return InteractionResult.PASS;
		});

		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> ModuleManager.worldChange());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ModuleManager.worldChange());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ConfigManager.flush();
			PostFx.close();
		});
	}
}
