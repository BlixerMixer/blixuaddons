package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.EntityVisuals;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Applies Self Aura / Hit Color materials to the entity body pass (still depth-tested). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<S extends LivingEntityRenderState> {
	@Shadow
	public abstract Identifier getTextureLocation(S state);

	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void blixuaddons$material(SubmitNodeCollector out, Model<?> model, Object state, PoseStack pose, RenderType type, int light, int overlay,
									  int tint, TextureAtlasSprite sprite, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {
		@SuppressWarnings("unchecked") S s = (S) state;
		EntityVisuals.submitBody(s, getTextureLocation(s), type, light, overlay, tint, pose,
			(t, l, o, c, p) -> original.call(out, model, state, p, t, l, o, c, sprite, outline, crumbling));
	}
}
