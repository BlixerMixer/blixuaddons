package com.blixu.blixuaddons.client.gui.preview;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.render.WorldCtx;
import com.blixu.blixuaddons.client.render.WorldPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Renders a module's preview scene into a small texture shown in the menu. The scene uses
 * the same world-drawing helpers as in-game effects, viewed from a slowly turning camera.
 */
public final class PreviewRenderer extends PictureInPictureRenderer<PreviewState> {
	private final WorldCtx ctx = new WorldCtx();
	private final Quaternionf view = new Quaternionf();
	private final Quaternionf inverse = new Quaternionf();

	@Override
	public Class<PreviewState> getRenderStateClass() {
		return PreviewState.class;
	}

	private final net.minecraft.client.renderer.Projection handProjection = new net.minecraft.client.renderer.Projection();
	private final net.minecraft.client.renderer.ProjectionMatrixBuffer handProjectionBuffer = new net.minecraft.client.renderer.ProjectionMatrixBuffer("blixuaddons preview hand");

	@Override
	protected void renderToTexture(PreviewState state, PoseStack poseStack, SubmitNodeCollector out) {
		if (state.module().preview() == com.blixu.blixuaddons.client.module.Module.Preview.FIRST_PERSON) {
			renderFirstPerson(state, poseStack, out);
			return;
		}
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		// Viewer sits on +Z looking at the origin; y is flipped because GUI pixels grow downwards.
		view.identity().rotateX(state.pitch() * Mth.DEG_TO_RAD).rotateY(state.yaw() * Mth.DEG_TO_RAD);
		view.conjugate(inverse);
		Vec3 back = new Vec3(inverse.transform(new org.joml.Vector3f(0, 0, 12)));
		ctx.begin(back, inverse, 1f, 1f / 60f, 200f);
		ctx.preview = true;
		try {
			state.module().renderPreview(ctx, state.time());
		} catch (RuntimeException e) {
			BlixuAddons.LOGGER.warn("Preview for {} failed", state.module().name(), e);
		}
		poseStack.scale(1f, -1f, 1f);
		poseStack.mulPose(view);
		poseStack.translate(back.x, back.y, back.z);
		ctx.fill.submitSplit(out, poseStack, WorldPipelines.SOLID, WorldPipelines.fill());
		ctx.glow.submit(out, poseStack, WorldPipelines.GLOW);
		ctx.lines.submit(out, poseStack, WorldPipelines.lines());
	}

	/**
	 * Your real first-person hand and held item, exactly as the in-game hand pass draws it
	 * (pose, swing, inspection and aura included), animated on the preview clock.
	 */
	private void renderFirstPerson(PreviewState state, PoseStack poseStack, SubmitNodeCollector out) {
		Minecraft mc = Minecraft.getInstance();
		var player = mc.player;
		if (player == null) return;
		int scale = (int) mc.getWindow().getGuiScale();
		float w = (state.x1() - state.x0()) * scale, h = (state.y1() - state.y0()) * scale;
		// widen the vertical FOV so the hand sits where it does on a 16:9 screen instead of off the narrow edge
		float fov = (float) Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(35)) * (16f / 9f) / (w / h)));
		handProjection.setupPerspective(0.05f, 100f, Math.max(70f, fov), w, h);
		com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(handProjectionBuffer.getBuffer(handProjection), com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
		mc.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
		poseStack.setIdentity();
		com.blixu.blixuaddons.client.feature.FirstPersonFx.setPreview(state.module(), state.time());
		try {
			mc.gameRenderer.itemInHandRenderer.submitHandsWithItems(1f, poseStack, out, player,
				mc.getEntityRenderDispatcher().getPackedLightCoords(player, 1f));
		} finally {
			com.blixu.blixuaddons.client.feature.FirstPersonFx.setPreview(null, 0f);
		}
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected String getTextureLabel() {
		return "blixuaddons preview";
	}
}
