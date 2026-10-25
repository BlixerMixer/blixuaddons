package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.render.PostFx;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hand Aura: runs the hand glow right after the first-person hand is drawn (depth = hand only). */
@Mixin(GameRenderer.class)
public abstract class GameRendererHandGlowMixin {
	@Shadow @Final private RenderTarget mainRenderTarget;
	@Shadow @Final private CrossFrameResourcePool resourcePool;

	@Inject(method = "renderItemInHand", at = @At("TAIL"))
	private void blixuaddons$handGlow(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
		if (!cameraState.isPanoramicMode) PostFx.handGlow(mainRenderTarget, resourcePool);
	}
}
