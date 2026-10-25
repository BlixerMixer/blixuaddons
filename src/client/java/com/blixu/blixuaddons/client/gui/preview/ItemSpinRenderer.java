package com.blixu.blixuaddons.client.gui.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;

/**
 * Draws a real item model (a skull with its skin, a block, a 3D item) turning in 3D, for the rare drop
 * animation. Uses the item-frame view so heads and blocks face you upright, then spins around Y.
 */
public final class ItemSpinRenderer extends PictureInPictureRenderer<ItemSpinState> {
	private final ItemStackRenderState item = new ItemStackRenderState();

	@Override
	public Class<ItemSpinState> getRenderStateClass() {
		return ItemSpinState.class;
	}

	@Override
	protected void renderToTexture(ItemSpinState state, PoseStack poseStack, SubmitNodeCollector out) {
		Minecraft mc = Minecraft.getInstance();
		mc.getItemModelResolver().updateForTopItem(item, state.stack(), ItemDisplayContext.FIXED, mc.level, null, 0);
		mc.gameRenderer.lighting().setupFor(item.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
		poseStack.scale(1f, -1f, -1f);
		poseStack.mulPose(new Quaternionf().rotateX(state.tilt()).rotateY(state.yaw()));
		item.submit(poseStack, out, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected String getTextureLabel() {
		return "blixuaddons item spin";
	}
}
