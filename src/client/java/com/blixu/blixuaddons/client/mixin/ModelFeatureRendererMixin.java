package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.EntityVisuals;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Per-vertex material effects (gradient, hologram scanlines, wireframe) while a model is written out. */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
	@WrapOperation(method = "prepareModel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
	private void blixuaddons$wrap(Model<?> model, PoseStack pose, VertexConsumer buffer, int light, int overlay, int tint, Operation<Void> original,
								  @Local(argsOnly = true) ModelFeatureRenderer.Submit<?> submit) {
		original.call(model, pose, EntityVisuals.wrap(submit.state(), submit.renderType(), buffer), light, overlay, tint);
	}
}
