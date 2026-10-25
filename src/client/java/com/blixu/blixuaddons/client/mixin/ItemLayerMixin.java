package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.FirstPersonFx;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Lets the first-person effects see where the held item model is drawn, and adds the inspect glint. */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class ItemLayerMixin {
	@WrapOperation(method = "submit", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
	private void blixuaddons$capture(SubmitNodeCollector out, PoseStack pose, ItemDisplayContext ctx, int light, int overlay, int outline,
									 int[] tints, List<BakedQuad> quads, ItemStackRenderState.FoilType foil, Operation<Void> original) {
		if (FirstPersonFx.capturingItem()) {
			FirstPersonFx.captureItem(pose.last(), quads);
			if (foil == ItemStackRenderState.FoilType.NONE && FirstPersonFx.inspecting()) foil = ItemStackRenderState.FoilType.STANDARD;
		}
		original.call(out, pose, ctx, light, overlay, outline, tints, quads, foil);
	}
}
